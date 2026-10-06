package com.edges.mask;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks the display/touch viewport actually published by Android's input service. */
final class DisplayProjection {
    final int rotation, deviceWidth, deviceHeight;
    final int[] logical, physical;
    DisplayProjection(int r, int w, int h, int[] l, int[] p) {
        rotation = r; deviceWidth = w; deviceHeight = h; logical = l; physical = p;
    }
    static int number(String line, String name) {
        Matcher m = Pattern.compile("\\b" + name + "\\s*=\\s*(\\d+)").matcher(line);
        if (!m.find()) throw new IllegalArgumentException("无法读取实际屏幕映射");
        return Integer.parseInt(m.group(1));
    }
    static int[] rectangle(String line, String name) {
        Matcher m = Pattern.compile(name + "\\s*=\\s*(?:\\[\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\]"
            + "|Rect\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*-\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\))").matcher(line);
        if (!m.find()) throw new IllegalArgumentException("无法读取实际屏幕映射范围");
        int start = m.group(1) == null ? 5 : 1;
        int[] result = new int[4];
        for (int i = 0; i < 4; i++) result[i] = Integer.parseInt(m.group(start + i));
        return result;
    }
    static DisplayProjection parse(String output) {
        Matcher rows = Pattern.compile("DisplayViewport\\{[^}\\r\\n]*\\}|Viewport[\\t ]+(?:INTERNAL|DEFAULT)[^\\r\\n]*").matcher(output);
        DisplayProjection result = null;
        while (rows.find()) {
            String row = rows.group();
            if (!Pattern.compile("\\bdisplayId\\s*=\\s*0(?!\\d)").matcher(row).find()
                    || !row.contains("INTERNAL")) continue;
            if (row.contains("valid=false") || row.contains("isActive=false"))
                throw new IllegalArgumentException("主屏幕映射无效");
            int w, h;
            if (row.contains("deviceWidth=")) { w = number(row, "deviceWidth"); h = number(row, "deviceHeight"); }
            else {
                Matcher size = Pattern.compile("deviceSize=\\[\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\]").matcher(row);
                if (!size.find()) throw new IllegalArgumentException("无法读取物理屏幕尺寸");
                w = Integer.parseInt(size.group(1)); h = Integer.parseInt(size.group(2));
            }
            DisplayProjection next = new DisplayProjection(number(row, "orientation"), w, h,
                    rectangle(row, "logicalFrame"), rectangle(row, "physicalFrame"));
            if (result != null && (result.rotation != next.rotation || result.deviceWidth != w
                    || result.deviceHeight != h || !Arrays.equals(result.logical, next.logical)
                    || !Arrays.equals(result.physical, next.physical)))
                throw new IllegalArgumentException("屏幕映射仍在变化，请使用普通遮罩");
            result = next;
        }
        if (result == null) throw new IllegalArgumentException("ROM 未提供可验证的屏幕映射");
        return result;
    }
    void requireCentered(DisplayProfile original, int[] target, int expectedRotation) {
        int w = expectedRotation % 2 == 0 ? target[0] : target[1];
        int h = expectedRotation % 2 == 0 ? target[1] : target[0];
        int pw = expectedRotation % 2 == 0 ? original.physicalWidth : original.physicalHeight;
        int ph = expectedRotation % 2 == 0 ? original.physicalHeight : original.physicalWidth;
        int left = (pw - w) / 2, top = (ph - h) / 2;
        int[] desired = {left, top, left + w, top + h};
        if (rotation != expectedRotation || deviceWidth != pw || deviceHeight != ph
                || !Arrays.equals(logical, new int[]{0, 0, w, h}))
            throw new IllegalArgumentException("实际屏幕映射与目标尺寸不同");
        for (int i = 0; i < 4; i++) if (physical[i] < 0 || physical[i] > (i % 2 == 0 ? pw : ph)
                || Math.abs(physical[i] - desired[i]) > 1)
            throw new IllegalArgumentException("系统仍在拉伸或偏移画面，不能保留 Root 修改");
    }
}
