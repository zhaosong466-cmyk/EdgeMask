package com.edges.mask;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Only numeric display geometry is exportable; never export window/layer names or raw dumps. */
final class DiagnosticGeometry {
    static final String KEYS = "mCurrentLayerStack|mCurrentOrientation|mCurrentLayerStackRect|mCurrentDisplayRect"
        + "|mDisplayScalingDisabled|mDisplayOffset|mDisplayRect|mLayerStackRect"
        + "|layerStackSpaceRect|orientedDisplaySpaceRect|layerStackSpace|orientedDisplaySpace"
        + "|framebufferSpace|displaySpace|framebufferSize|displaySize|bufferSize";
    static String command(String service) {
        if (!service.equals("display") && !service.equals("SurfaceFlinger"))
            throw new IllegalArgumentException("诊断服务不受支持");
        return "/system/bin/dumpsys " + service + " | /system/bin/grep -E '^[[:space:]]*("
            + KEYS + ")[[:space:]]*[=:]'";
    }
    static String filter(String input) {
        Pattern field = Pattern.compile("^[\\t ]*(" + KEYS + ")[\\t ]*[=:][\\t ]*(.*)$");
        StringBuilder result = new StringBuilder(); int count = 0;
        for (String line : input.split("\\r?\\n")) {
            Matcher m = field.matcher(line);
            if (!m.matches() || m.group(2).length() > 400) continue;
            String value = m.group(2);
            String numeric = value.replaceAll("\\b(?:Rect|ProjectionSpace|bounds|content|orientation|width|height|size|true|false)\\b", "")
                .replaceAll("(?:ROTATION|ROT)_?(?=[0-9])", "");
            if (value.isEmpty() || !numeric.matches("[0-9\\t .,x\\[\\](){}=:+_-]*")) continue;
            result.append(m.group(1)).append(" = ").append(value).append('\n');
            if (++count >= 64) break;
        }
        return result.length() == 0 ? "未提供可导出的几何字段\n" : result.toString();
    }
}
