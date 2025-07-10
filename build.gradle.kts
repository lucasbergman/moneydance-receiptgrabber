import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.nio.file.Files
import java.security.MessageDigest

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.21"
}

group = "house.bergman"
version = "1.0-SNAPSHOT"

// Moneydance extension properties
val extensionId = "receiptgrabber"
val extensionName = "$extensionId.mxt"
val keyFileId = "99" // From the Moneydance developer examples

val libsDir = layout.projectDirectory.dir("lib")
val tmpDirProvider = layout.buildDirectory.dir("tmp")
val distDirProvider = layout.buildDirectory.dir("dist")

repositories {
    mavenCentral()
}

// JDK 21 is the latest LTS version, but for now Moneydance supports targeting Java 11
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
}

private data class SourceArchive(
    val url: String,
    val sha256: String,
    val archiveFileName: String,
    val filesToExtract: Map<String, String>,
)

// Configuration for downloading source archives
private val sourceArchives =
    listOf(
        SourceArchive(
            url = "https://infinitekind.com/dev/moneydance-devkit-5.1.tar.gz",
            sha256 = "0314f04863ee924a2dd12b038374c90342533e4e787607e300000a76acd32382",
            archiveFileName = "moneydance-devkit-5.1.tar.gz",
            filesToExtract = mapOf("extadmin.jar" to "moneydance-devkit-5.1/lib/extadmin.jar"),
        ),
        SourceArchive(
            url = "https://infinitekind.com/stabledl/2024.4.5253/moneydance-linux.tar.gz",
            sha256 = "31eaab8f655c07f929c346af727a5332b815f55108ffd6b787d865b1bce46cb9",
            archiveFileName = "moneydance-linux.tar.gz",
            filesToExtract = mapOf("moneydance-dev.jar" to "moneydance/lib/moneydance.jar"),
        ),
    )

// Custom task to download, verify, and extract the Moneydance JARs
tasks.register("setUpMoneydanceLibs") {
    group = "Moneydance"
    description = "Downloads, verifies, and extracts Moneydance library JARs."

    inputs.property("sourceArchivesConfig", sourceArchives.map { it.url to it.sha256 }.toString())
    val outputFiles = sourceArchives.flatMap { it.filesToExtract.keys }.map { libsDir.file(it) }
    outputs.files(outputFiles)
    outputs.dir(libsDir)

    doLast {
        libsDir.asFile.mkdirs()
        val tmpDir = tmpDirProvider.get().asFile
        tmpDir.mkdirs()

        sourceArchives.forEach { config ->
            val archiveFile = tmpDir.resolve(config.archiveFileName)
            if (!archiveFile.exists()) {
                println("Downloading ${archiveFile.name} from ${config.url}...")
                URI(config.url).toURL().openStream().use { input ->
                    Files.copy(input, archiveFile.toPath())
                }
            }

            val actualHash = archiveFile.readBytes().toSha256()
            if (!actualHash.equals(config.sha256, ignoreCase = true)) {
                throw GradleException(
                    """
                    Checksum verification failed for ${archiveFile.name}!
                      Expected: ${config.sha256}
                      Actual:   $actualHash
                    Manually delete '$archiveFile' to retry.
                    """.trimMargin(),
                )
            }

            config.filesToExtract.forEach extract@{ (destFileName, pathInArchive) ->
                val destFile = libsDir.asFile.resolve(destFileName)
                if (destFile.exists()) {
                    return@extract
                }
                println("Extracting $pathInArchive from ${archiveFile.name} to $destFile...")
                copy {
                    from(tarTree(archiveFile)) { include(pathInArchive) }
                    into(libsDir)
                    eachFile { path = destFileName }
                    includeEmptyDirs = false
                }
            }
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))

    // TODO: moneydance.jar belongs in both sets, but extadmin.jar is runtime only
    compileOnly(files(tasks.named("setUpMoneydanceLibs")))
    runtimeOnly(files(tasks.named("setUpMoneydanceLibs")))
}

private fun ByteArray.toSha256(): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

tasks.withType<Jar> {
    archiveBaseName.set(extensionId)
    archiveVersion.set("")
    destinationDirectory.set(distDirProvider)
}

// TODO: Goodness, what a hack
tasks.register<JavaExec>("genKeys") {
    group = "Moneydance"
    description = "Generates private and public keys for signing the extension."
    dependsOn("setUpMoneydanceLibs")

    mainClass.set("com.moneydance.admin.KeyAdmin")
    classpath = sourceSets.main.get().runtimeClasspath

    doFirst { args = listOf("genkey", "priv_key", "pub_key") }
    standardInput = System.`in`
}

// Custom task to sign the packaged extension JAR
tasks.register<JavaExec>("signExtension") {
    group = "Moneydance"
    description = "Creates a signed .mxt extension from the compiled JAR."

    mainClass.set("com.moneydance.admin.KeyAdmin")
    classpath = sourceSets.main.get().runtimeClasspath

    // The extension signing CLI outputs to the project root :(
    val signedMxtFile = layout.projectDirectory.file("s-$extensionName").asFile

    val unsignedJarProvider = tasks.jar.flatMap { it.archiveFile }
    val finalMxtProvider = distDirProvider.map { it.file(extensionName) }

    inputs.file(unsignedJarProvider)
    outputs.file(finalMxtProvider)

    doFirst {
        args =
            listOf(
                "signextjar",
                "priv_key",
                keyFileId,
                extensionId,
                unsignedJarProvider.get().asFile.absolutePath,
            )
    }
    standardInput = System.`in`

    doLast {
        val finalMxtFile = finalMxtProvider.get().asFile

        if (!signedMxtFile.exists()) {
            error("Signing failed; no signed file at: ${signedMxtFile.path}")
        }
        finalMxtFile.parentFile.mkdirs()
        signedMxtFile.renameTo(finalMxtFile)
        println("\n✅ Signed extension created at: ${finalMxtFile.absolutePath}")
    }
}
