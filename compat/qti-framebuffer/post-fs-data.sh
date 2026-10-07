#!/system/bin/sh
# QTI caches this feature at SurfaceFlinger initialization. Never restart that service.
# No persistent properties, system mounts, display commands, Binder guesses or panel writes.
MODDIR=${0%/*}
umask 077
record() {
    printf '%s\n' "$1" > "$MODDIR/boot-result"
    cat /proc/sys/kernel/random/boot_id > "$MODDIR/applied-boot"
}
record checking
LIB=/system_ext/lib64/libsurfaceflinger.so
SUM=$(timeout 3 sha256sum "$LIB" 2>/dev/null) || { record library-unreadable; exit 0; }
SUM=${SUM%% *}
grep -Fxq "$SUM" "$MODDIR/supported-libraries.txt" || { record library-changed; exit 0; }
# Boot scripts run before Android starts. If this ROM starts SF earlier, leave it alone.
if pidof surfaceflinger >/dev/null 2>&1; then
    record display-already-started
    exit 0
fi
command -v resetprop >/dev/null 2>&1 || { record resetprop-missing; exit 0; }
# -n bypasses init property notifications; setprop here can deadlock boot.
timeout 3 resetprop -n vendor.display.enable_fb_scaling false || { record property-failed; exit 0; }
[ "$(getprop vendor.display.enable_fb_scaling)" = false ] || { record property-unverified; exit 0; }
record applied
