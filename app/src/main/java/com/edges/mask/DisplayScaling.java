package com.edges.mask;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads WindowManager's active state, not an obsolete global settings key. */
final class DisplayScaling {
    static final int UNKNOWN = -1, AUTO = 0, OFF = 1;
    static String queryCommand(String executable) {
        // Android 8+ provides sh and sed; do not depend on an optional awk/busybox install.
        return executable + " window displays | sed -n"
            + " -e '/^[[:space:]]*Display: mDisplayId=[0-9]/p'"
            + " -e '/^[[:space:]]*init=[0-9]/p' | {"
            + " selected=0; n=0; mode=''; while IFS= read -r row; do"
            + " case \"$row\" in *'Display: mDisplayId='*)"
            + " id=${row#*'Display: mDisplayId='}; id=${id%%[!0-9]*};"
            + " if [ \"$id\" = 0 ]; then selected=1; else selected=0; fi;;"
            + " *) if [ \"$selected\" -eq 1 ]; then n=$((n+1));"
            + " case \"$row\" in *' noscale'*) mode=off;; *) mode=auto;; esac; fi;; esac; done;"
            + " if [ \"$n\" -eq 1 ]; then printf 'EdgeMask scaling: %s\\n' \"$mode\"; else false; fi; }";
    }
    static int parse(String output) {
        Matcher m = Pattern.compile("(?m)^EdgeMask scaling: (auto|off)[\\t ]*$").matcher(output);
        if (!m.find()) return UNKNOWN;
        int mode = m.group(1).equals("off") ? OFF : AUTO;
        if (m.find()) throw new IllegalArgumentException("缩放状态有多个结果，拒绝修改");
        return mode;
    }
    static String argument(int mode) {
        if (mode == AUTO) return "auto";
        if (mode == OFF) return "off";
        throw new IllegalArgumentException("原始缩放状态未知，拒绝修改");
    }
}
