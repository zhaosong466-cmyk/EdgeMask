#!/system/bin/sh
# Invoke the installed Magisk CLI, not the manager APK's assets/extraction screen.
# This script installs only the APK-bundled module. No reboot or property/display writes here.
set -eu
fail() { printf '%s\n' "$1" >&2; exit 1; }
[ "$(id -u)" = 0 ] || fail "Root authorization is required."
[ "$#" = 1 ] && [ -f "$1" ] || fail "Bundled module ZIP is missing."
MAGISK=$(command -v magisk) || fail "Magisk command is unavailable. Save the ZIP and install with your root manager."
VERSION=$("$MAGISK" -V) || fail "Cannot reach the installed Magisk daemon."
case "$VERSION" in ''|*[!0-9]*) fail "Cannot verify the installed Magisk version.";; esac
[ "$VERSION" -gt 0 ] || fail "Magisk is not running."
"$MAGISK" --help 2>&1 | grep -q -- '--install-module' || fail "This Magisk version has no module CLI installer."
printf 'Magisk version code: %s\n' "$VERSION"
"$MAGISK" --install-module "$1" || fail "Magisk module installation failed."
STAGED=/data/adb/modules_update/edgemask_qti_framebuffer
grep -qx 'id=edgemask_qti_framebuffer' "$STAGED/module.prop" || fail "Module installation was not verified."
grep -qx 'versionCode=2' "$STAGED/module.prop" || fail "The new module version was not staged."
[ -f "$STAGED/skip_mount" ] && [ -f "$STAGED/post-fs-data.sh" ] || fail "Installed module files are incomplete."
printf '%s\n' 'EDGEMASK_MODULE_STAGED_2'
