package com.edges.mask;

import java.util.Locale;

/** Conservative restriction based on an actual 1.2 device report, not a ROM-wide claim. */
final class RootCompatibility {
    static String restriction(String device, String model, int sdk) {
        String d = normalize(device), m = normalize(model);
        boolean phone = d.equals("ishtar") || m.equals("xiaomi13ultra") || m.equals("mi13ultra")
            || m.equals("2304fpn6dc") || m.equals("2304fpn6dg");
        return sdk >= 36 && phone
            ? "小米 13 Ultra / Android 16 及以上已有实际拉伸反馈，暂不启用 Root 收窄；请使用普通黑边"
            : "";
    }
    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
