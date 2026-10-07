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
                ? new DisplayProfile(1440, 3200, 1080, 2400, 420, true, DisplayScaling.AUTO)
                : new DisplayProfile(1080, 2400, 1080, 2400, 420, false,
                    args[2].equals("off") ? DisplayScaling.OFF : DisplayScaling.AUTO);
            Files.write(Paths.get(args[0], "watchdog.sh"),
                GuardScript.build(args[0], p, args[1], Paths.get(args[0], "clock").toString(),
                    Paths.get(args[0], "mock-dumpsys").toString()).getBytes(StandardCharsets.UTF_8));
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
        check(p.maximumPercent(0) == 11, "portrait slider stops before minimum usable width");
        check(p.maximumPercent(1) == 15, "landscape slider uses rotated axis");
        DisplayProfile recorded = new DisplayProfile(1440, 3200, 1440, 3200, 560, false);
        check(recorded.maximumPercent(0) == 11, "recorded device limits shrinking before 320 dp");
        check(new DisplayProfile(1000, 2400, 1000, 2400, 500, false).maximumPercent(0) == 0,
                "refuse shrinking a screen already at minimum width");
        rejects(() -> p.maximumPercent(4), "reject invalid slider rotation");
        rejects(() -> DisplayProfile.parse("Override size: 900x2400\nPhysical density: 420\n"), "require physical size");
        rejects(() -> DisplayProfile.parse("Physical size: 1080x2400\nPhysical size: 1200x2400\nPhysical density: 420\n"), "reject ambiguous display");
        rejects(() -> DisplayProfile.parse("Physical size: 1080x2400\nPhysical density: 0\n"), "reject invalid dpi");
        rejects(() -> new DisplayProfile(1080, 2400, 900, 2400, 420, true).validateBaseline(), "reject custom aspect ratio");
        check(RootShell.quote("a'b").equals("'a'\\''b'"), "quote apostrophe without shell execution");
        custom = new DisplayProfile(1440, 3200, 1080, 2400, 420, true, DisplayScaling.AUTO);
        String script = GuardScript.build("/data/user_de/0/com.edges.mask/files/display-recovery", custom,
                "/system/bin/wm", "/proc/uptime", "/system/bin/dumpsys");
        check(script.contains("size '1080x2400'"), "watchdog restores original override");
        check(!script.contains("wm density") && !script.contains("settings put"), "no density/global-setting mutations");
        check(!script.contains("rm -f"), "independent watchdog retains recovery record");
        check(script.contains("scaling 'auto'"), "restore original auto scaling");
        check(GuardScript.build("/tmp/guard", new DisplayProfile(1080, 2400, 1080, 2400, 420, false, DisplayScaling.OFF),
            "/mock/wm", "/mock/clock", "/mock/dumpsys").contains("scaling 'off'"), "restore original off scaling");
        rejects(() -> GuardScript.build("/tmp/guard", p, "/mock/wm", "/mock/clock", "/mock/dumpsys"),
                "refuse arming without a known scaling backup");
        check(DisplayScaling.parse("EdgeMask scaling: off\n") == DisplayScaling.OFF, "read verified off mode");
        check(DisplayScaling.parse("EdgeMask scaling: auto\n") == DisplayScaling.AUTO, "read verified auto mode");
        check(DisplayScaling.parse("unsupported ROM") == DisplayScaling.UNKNOWN, "unknown scaling is never assumed auto");
        rejects(() -> DisplayScaling.parse("EdgeMask scaling: auto\nEdgeMask scaling: off\n"), "reject contradictory modes");
        rejects(() -> DisplayScaling.argument(DisplayScaling.UNKNOWN), "unknown restore mode cannot become a command");
        rejects(() -> new DisplayProfile(1440, 3200, 1080, 2400, 420, true, DisplayScaling.AUTO).validateNoScaleBaseline(),
                "refuse scaled baselines that would also add top/bottom borders");
        DisplayProfile nativeDisplay = new DisplayProfile(1440, 3200, 1440, 3200, 560, false, DisplayScaling.AUTO);
        nativeDisplay.validateNoScaleBaseline();
        String nativeRow = "Viewport INTERNAL: displayId=0, orientation=0, logicalFrame=[0,0,1296,3200], "
            + "physicalFrame=[72,0,1368,3200], deviceSize=[1440,3200]";
        DisplayProjection centered = DisplayProjection.parse(nativeRow);
        centered.requireCentered(nativeDisplay, new int[]{1296,3200}, 0);
        check(centered.physical[0] == 72, "verify real centered 5 percent side borders");
        rejects(() -> DisplayProjection.parse(nativeRow.replace("[72,0,1368,3200]", "[0,0,1440,3200]"))
            .requireCentered(nativeDisplay, new int[]{1296,3200}, 0), "reject ROM stretching to full panel");
        rejects(() -> DisplayProjection.parse(nativeRow.replace("[72,0,1368,3200]", "[0,0,1296,3200]"))
            .requireCentered(nativeDisplay, new int[]{1296,3200}, 0), "reject viewport shifted to one side");
        rejects(() -> DisplayProjection.parse(nativeRow).requireCentered(nativeDisplay, new int[]{1296,3200}, 1), "reject stale rotation");
        String javaRow = "DisplayViewport{type=INTERNAL, valid=true, isActive=true, displayId=0, orientation=0, "
            + "logicalFrame=Rect(0, 0 - 1296, 3200), physicalFrame=Rect(72, 0 - 1368, 3200), deviceWidth=1440, deviceHeight=3200}";
        DisplayProjection.parse(javaRow + "\n" + nativeRow).requireCentered(nativeDisplay, new int[]{1296,3200}, 0);
        check(true, "accept equivalent Java and native viewport reports");
        rejects(() -> DisplayProjection.parse(javaRow.replace("valid=true", "valid=false")), "reject inactive viewport");
        rejects(() -> DisplayProjection.parse(nativeRow.replace("displayId=0", "displayId=2")), "do not use another display as proof");
        rejects(() -> DisplayProjection.parse(javaRow + "\n" + nativeRow.replace("[72,0,1368,3200]", "[0,0,1440,3200]")),
                "reject inconsistent mapping snapshots");
        DisplayProjection.parse(nativeRow.replace("orientation=0", "orientation=1")
            .replace("[0,0,1296,3200]", "[0,0,2880,1440]")
            .replace("[72,0,1368,3200]", "[160,0,3040,1440]")
            .replace("[1440,3200]", "[3200,1440]")).requireCentered(nativeDisplay, new int[]{1440,2880}, 1);
        check(true, "validate landscape borders using rotated physical coordinates");
        check(!RootCompatibility.restriction("ishtar", "2304FPN6DC", 36).isEmpty(),
                "block the reported Xiaomi 13 Ultra Android 16 combination");
        check(!RootCompatibility.restriction("", "Xiaomi 13 Ultra", 36).isEmpty(), "match human readable model names");
        check(!RootCompatibility.restriction("", "2304FPN6DG", 36).isEmpty(), "match global model variant");
        check(!RootCompatibility.restriction("ishtar", "unknown", 37).isEmpty(), "do not assume an untested newer OS fixed output");
        check(RootCompatibility.restriction("ishtar", "2304FPN6DC", 35).isEmpty(), "do not generalize the report to earlier Android versions");
        check(RootCompatibility.restriction("other", "other", 36).isEmpty(), "do not block unrelated devices");
        check(RootCompatibility.restriction(null, null, 36).isEmpty(), "missing names do not crash compatibility check");
        String diagnostic = DiagnosticGeometry.filter("  mCurrentDisplayRect=Rect(43, 0 - 1397, 3200)\n"
            + "  layerStackSpace=ProjectionSpace{bounds=Rect(0, 0 - 1354, 3200), content=Rect(0, 0 - 1354, 3200), orientation=ROTATION_0}\n"
            + "  mDisplayScalingDisabled=true\n"
            + "Layer com.private.bank secret-message\n"
            + "  layerStackSpace=com.private.bank-secret\n"
            + "Window title: layerStackSpace=Rect(0, 0 - 1, 2)\n");
        check(diagnostic.contains("mCurrentDisplayRect") && diagnostic.contains("ProjectionSpace")
            && diagnostic.contains("mDisplayScalingDisabled = true"), "retain numeric display geometry");
        check(!diagnostic.contains("private") && !diagnostic.contains("secret") && !diagnostic.contains("Window"),
                "never export names, screen text, or unknown geometry values");
        rejects(() -> DiagnosticGeometry.command("SurfaceFlinger; wm size reset"), "diagnostic services cannot inject shell commands");
        check(!DiagnosticGeometry.command("display").contains("wm ")
            && !DiagnosticGeometry.command("SurfaceFlinger").contains("service call"), "diagnostics are read only");
        System.out.println("PASS: " + checks + " profile and rollback-policy checks");
    }
}
