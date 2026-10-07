package com.edges.mask;

/** Read-only module status. The APK does not install modules or change boot properties. */
final class FramebufferCompat {
    static final String ARCHIVE="EdgeMask-QTI-Compat-1.0.zip";
    static String status() throws Exception {
        String dir="/data/adb/modules/edgemask_qti_framebuffer";
        String command="printf '缩放属性（不是进程内缓存值）: '; getprop vendor.display.enable_fb_scaling; "
            +"D="+RootShell.quote(dir)+"; "
            +"if [ ! -d \"$D\" ]; then echo '模块状态: 未安装'; "
            +"elif [ -f \"$D/disable\" ] || [ -f \"$D/remove\" ]; then echo '模块状态: 已停用，重启后恢复原开关'; "
            +"elif [ ! -f \"$D/applied-boot\" ] || [ \"$(cat \"$D/applied-boot\")\" != \"$(cat /proc/sys/kernel/random/boot_id)\" ]; then echo '模块状态: 本次启动未执行，请重启'; "
            +"else case \"$(cat \"$D/boot-result\" 2>/dev/null)\" in "
            +"applied) echo '模块状态: 本次启动已关闭 QTI 缩放，仍需验证实际投影';; "
            +"library-changed|library-unreadable) echo '模块状态: 显示库已变化或无法读取，未改开关';; "
            +"display-already-started) echo '模块状态: 显示服务启动较早，未改开关';; "
            +"resetprop-missing) echo '模块状态: Root 管理器缺少 resetprop，未改开关';; "
            +"*) echo '模块状态: 开机设置未通过校验';; esac; fi";
        return RootShell.run(command,8).checked();
    }
    private FramebufferCompat() { }
}
