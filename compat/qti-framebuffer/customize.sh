#!/system/bin/sh
# Installation checks only. No display changes or property changes during installation.
[ "$BOOTMODE" = true ] || abort "Install from the root manager while Android is running."
LIB=/system_ext/lib64/libsurfaceflinger.so
SUM=$(timeout 3 sha256sum "$LIB" 2>/dev/null) || abort "Cannot verify the display library."
SUM=${SUM%% *}
grep -Fxq "$SUM" "$MODPATH/supported-libraries.txt" || abort "This display library has not been analyzed. No change was made."
ui_print "AI-written EdgeMask compatibility module."
ui_print "At boot: vendor.display.enable_fb_scaling=false (RAM only)."
ui_print "Restore the display in EdgeMask before rebooting."
ui_print "After reboot, EdgeMask can change the width live."
ui_print "To revert: restore in EdgeMask, disable/remove this module, then reboot."
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
