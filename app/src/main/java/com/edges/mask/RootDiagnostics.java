package com.edges.mask;

import android.content.Context;
import android.os.Build;
import java.util.Arrays;

/** Explicit, read-only collection. No mutation, screenshot, network, or automatic upload. */
final class RootDiagnostics {
    static String collect(Context c) {
        StringBuilder report = new StringBuilder("EdgeMask 1.4 显示诊断（只读）\n");
        report.append("制造商: ").append(Build.MANUFACTURER)
            .append("\n机型: ").append(Build.MODEL).append("\n设备代号: ").append(Build.DEVICE)
            .append("\nAndroid: ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT)
            .append("\n系统版本: ").append(Build.DISPLAY).append("\n构建版本: ").append(Build.FINGERPRINT)
            .append("\nGPU 合成兼容模式: ").append(Config.gpuCompatibility(c))
            .append("\n遮罩启用: ").append(Config.enabled(c))
            .append("\nRoot 会话: ").append(RootSyncService.instance != null).append('\n');
        try {
            if (!"0".equals(RootShell.run("id -u", 45).checked().trim())) {
                report.append("未获得 Root，仅有上述系统信息\n"); return report.toString();
            }
        } catch (Exception e) {
            report.append("Root 授权未完成: ").append(e.getMessage()).append('\n'); return report.toString();
        }
        report.append("\n[尺寸与缩放]\n");
        try {
            DisplayProfile p = RootController.query();
            report.append("物理尺寸: ").append(p.physicalWidth).append('x').append(p.physicalHeight)
                .append("\n逻辑尺寸: ").append(p.width).append('x').append(p.height)
                .append("\nDPI: ").append(p.density).append("\n尺寸覆盖: ").append(p.overridden)
                .append("\n缩放: ").append(DisplayScaling.argument(p.scalingMode)).append('\n');
        } catch (Exception e) { report.append("无法读取: ").append(e.getMessage()).append('\n'); }
        report.append("\n[输入服务报告的映射]\n");
        try {
            DisplayProjection p = DisplayProjection.parse(RootShell.run(
                "/system/bin/dumpsys input | /system/bin/grep -E '(Viewport|logicalFrame|physicalFrame)'", 8).checked());
            report.append("方向: ").append(p.rotation).append("\n设备尺寸: ").append(p.deviceWidth).append('x').append(p.deviceHeight)
                .append("\n逻辑范围: ").append(Arrays.toString(p.logical))
                .append("\n物理范围: ").append(Arrays.toString(p.physical)).append('\n');
        } catch (Exception e) { report.append("无法读取: ").append(e.getMessage()).append('\n'); }
        for (String service : new String[]{"display", "SurfaceFlinger"}) {
            report.append("\n[").append(service).append(" 几何参数]\n");
            try {
                RootShell.Result data = RootShell.run(DiagnosticGeometry.command(service), 8);
                report.append(DiagnosticGeometry.filter(data.output));
                if (data.code != 0) report.append("此 ROM 未提供匹配字段或读取失败\n");
            } catch (Exception e) { report.append("读取未完成: ").append(e.getMessage()).append('\n'); }
        }
        report.append("\n[最近一次合成层修正记录，超时后也保留]\n");
        try { report.append(RootController.read(RootController.file(c,"compositor-report.txt"))).append('\n'); }
        catch(Exception e) { report.append("暂无修正记录\n"); }
        try { report.append("修正错误: ").append(RootController.read(RootController.file(c,"error"))).append('\n'); }
        catch(Exception ignored) { }
        report.append("\n以上系统报告不能证明实际面板无拉伸。本次诊断未修改显示参数，未截图，未自动发送。\n");
        return report.toString();
    }
}
