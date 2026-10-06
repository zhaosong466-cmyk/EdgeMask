package com.edges.mask;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java parsing and bounds checks. No command may contain user supplied text. */
final class DisplayProfile {
    final int physicalWidth, physicalHeight, width, height, density;
    final boolean overridden;

    DisplayProfile(int pw, int ph, int w, int h, int dpi, boolean override) {
        if (pw < 200 || ph < 200 || pw > 10000 || ph > 10000
                || w < 200 || h < 200 || w > 10000 || h > 10000
                || dpi < 72 || dpi > 1200) throw new IllegalArgumentException("显示参数不受支持");
        physicalWidth = pw; physicalHeight = ph; width = w; height = h;
        density = dpi; overridden = override;
    }

    static int[] size(String output, String kind, boolean required) {
        Matcher m = Pattern.compile("(?m)^" + kind + " size: (\\d+)x(\\d+)\\s*$").matcher(output);
        if (!m.find()) {
            if (required) throw new IllegalArgumentException("无法读取系统分辨率");
            return null;
        }
        int[] value = {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
        if (m.find()) throw new IllegalArgumentException("多个显示器，暂不支持");
        return value;
    }

    static DisplayProfile parse(String output) {
        int[] physical = size(output, "Physical", true), override = size(output, "Override", false);
        Matcher m = Pattern.compile("(?m)^(Physical|Override) density: (\\d+)\\s*$").matcher(output);
        int dpi = 0;
        while (m.find()) dpi = Integer.parseInt(m.group(2));
        int[] effective = override == null ? physical : override;
        return new DisplayProfile(physical[0], physical[1], effective[0], effective[1], dpi,
                override != null);
    }

    void validateBaseline() {
        double ratio = ((double) width * physicalHeight) / ((double) height * physicalWidth);
        if (Math.abs(ratio - 1) > .01) throw new IllegalArgumentException("原始显示比例已有自定义修改，请先恢复后再使用 Root 模式");
        if (Math.min(width, height) * 160.0 / density < 320)
            throw new IllegalArgumentException("原始显示区域小于 320 dp，拒绝进一步收窄");
    }

    int[] target(int percent, int rotation) {
        if (percent < 0 || percent > 15) throw new IllegalArgumentException("Root 模式每侧宽度上限为 15%");
        if (rotation < 0 || rotation > 3) throw new IllegalArgumentException("旋转参数无效");
        int[] result = {width, height};
        int axis = (rotation % 2 == 0) ? 0 : 1;
        result[axis] -= 2 * Math.round(result[axis] * percent / 100f);
        if (result[axis] < (axis == 0 ? width : height) * .70
                || Math.min(result[0], result[1]) * 160.0 / density < 320)
            throw new IllegalArgumentException("超出安全范围：可见短边必须至少 320 dp，请减小黑条宽度");
        return result;
    }

    String restoreArgument() { return overridden ? width + "x" + height : "reset"; }
    boolean sameSize(DisplayProfile other) {
        return width == other.width && height == other.height
                && physicalWidth == other.physicalWidth && physicalHeight == other.physicalHeight
                && overridden == other.overridden;
    }
}
