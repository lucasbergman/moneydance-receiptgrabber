{
  pkgs,
  jdk,
  ...
}:
{
  default = pkgs.mkShell {
    buildInputs = [
      jdk

      pkgs.gradle
    ];
  };
}
