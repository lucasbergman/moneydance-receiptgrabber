{ pkgs, ... }:
{
  projectRootFile = "flake.nix";
  programs.ktlint.enable = true;
  programs.nixfmt.enable = true;
}
