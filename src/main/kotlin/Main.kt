package com.moneydance.modules.features.receiptgrabber

import com.moneydance.apps.md.controller.FeatureModule
import java.awt.Image
import java.awt.Toolkit
import java.io.ByteArrayOutputStream
import javax.swing.JOptionPane

private const val URI = "receipt_grabber:hello"

/**
 * A "Hello, World" sample Moneydance extension written in Kotlin.
 */
class Main : FeatureModule() {
    /**
     * Called by Moneydance when the extension is loaded. This is where we register
     * the feature to be invoked, for example, from a menu item or toolbar button.
     */
    override fun init() {
        try {
            context!!.registerFeature(
                this,
                URI,
                loadIcon("icon.gif"),
                "Receipt Grabber",
            )
        } catch (e: Exception) {
            e.printStackTrace(System.err)
        }
    }

    /**
     * Called by Moneydance when the user selects our feature. The 'uri' is the
     * command we registered in the init() method.
     */
    override fun invoke(uri: String) {
        if (uri != URI) return
        showHelloWorldDialog()
    }

    /**
     * Returns the name of the extension.
     */
    override fun getName(): String = "Receipt Grabber"

    private fun showHelloWorldDialog() {
        JOptionPane.showMessageDialog(
            null,
            "Hello from the Receipt Grabber extension!",
            "Receipt Grabber",
            JOptionPane.INFORMATION_MESSAGE,
        )
    }

    /**
     * Loads an image resource from the extension's JAR file.
     * The icon should be placed in the 'src/main/resources' directory.
     */
    private fun loadIcon(resourceName: String): Image? =
        try {
            val cl = javaClass.classLoader
            val resourceStream = cl.getResourceAsStream(resourceName)
            if (resourceStream != null) {
                val bout = ByteArrayOutputStream(1000)
                resourceStream.use { it.copyTo(bout) }
                Toolkit.getDefaultToolkit().createImage(bout.toByteArray())
            } else {
                null
            }
        } catch (e: Throwable) {
            e.printStackTrace(System.err)
            null
        }
}
