package com.edges.mask;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public final class SafetyChecks {
    static int checks;
    static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    static void rejects(Runnable fn, String message) {
        boolean thrown = false;
        try { fn.run(); } catch (IllegalArgumentException expected) { thrown = true; }
        check(thrown, message);
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 3) {
            DisplayProfile p = args[2].equals("override")
                ? new DisplayProfile(1440, 3200, 1080, 2400, 420, true)
                : new DisplayProfile(1080, 2400, 1080, 2400, 420, false);
            Files.write(Paths.get(args[0], "watchdog.sh"),
                GuardScript.build(args[0], p, args[1], Paths.get(args[0], "clock").toString()).getBytes(StandardCharsets.UTF_8));
            return;
        }
        DisplayProfile p = DisplayProfile.parse("Physical size: 1080x2400\nPhysical density: 420\n");
        p.validateBaseline();
        check(!p.overridden && p.restoreArgument().equals("reset"), "reset only when no previous override");
        DisplayProfile custom = DisplayProfile.parse("Physical size: 1440x3200\nOverride size: 1080x2400\nPhysical density: 560\nOverride density: 420\n");
        custom.validateBaseline();
        check(custom.restoreArgument().equals("1080x2400"), "preserve existing override");
        for (int rotation = 0; rotation < 4; rotation++) {
            int[] t = p.target(4, rotation);
            check(t[rotation % 2] == (rotation % 2 == 0 ? 994 : 2208), "correct width axis");
            check(t[1 - rotation % 2] == (rotation % 2 == 0 ? 2400 : 1080), "other axis unchanged");
            check(Math.min(t[0], t[1]) * 160.0 / p.density >= 320, "minimum usable dp");
        }
        rejects(() -> p.target(16, 0), "reject excessive shrink");
        rejects(() -> p.target(15, 0), "reject too small usable width");
        rejects(() -> p.target(-1, 0), "reject negative percentages");
        rejects(() -> p.target(1, 4), "reject invalid rotation");
        rejects(() -> DisplayProfile.parse("Override size: 900x2400\nPhysical density: 420\n"), "require physical size");
        rejects(() -> DisplayProfile.parse("Physical size: 1080x2400\nPhysical size: 1200x2400\nPhysical density: 420\n"), "reject ambiguous display");
        rejects(() -> DisplayProfile.parse("Physical size: 1080x2400\nPhysical density: 0\n"), "reject invalid dpi");
        rejects(() -> new DisplayProfile(1080, 2400, 900, 2400, 420, true).validateBaseline(), "reject custom aspect ratio");
        check(RootShell.quote("a'b").equals("'a'\\''b'"), "quote apostrophe without shell execution");
        String script = GuardScript.build("/data/user_de/0/com.edges.mask/files/display-recovery", custom, "/system/bin/wm", "/proc/uptime");
        check(script.contains("size '1080x2400'"), "watchdog restores original override");
        check(!script.contains("wm density") && !script.contains("scaling auto") && !script.contains("settings put"), "no density/scaling mutations");
        check(!script.contains("rm -f"), "independent watchdog retains recovery record");
        System.out.println("PASS: " + checks + " profile and rollback-policy checks");
    }
}
