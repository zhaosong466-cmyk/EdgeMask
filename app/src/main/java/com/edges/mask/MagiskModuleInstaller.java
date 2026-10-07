package com.edges.mask;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-invoked module installation through Magisk's documented CLI. Never reboots. */
final class MagiskModuleInstaller {
    private static final AtomicBoolean installing = new AtomicBoolean();
    static boolean running() { return installing.get(); }
    static String install(File launcher, File archive) throws Exception {
        if (!installing.compareAndSet(false, true)) throw new IOException("模块正在安装，请稍后");
        try {
            if (!launcher.isFile() || !archive.isFile()) throw new IOException("内置安装文件不完整");
            RootShell.Result result = RootShell.run("sh " + RootShell.quote(launcher.getAbsolutePath())
                + " " + RootShell.quote(archive.getAbsolutePath()), 90);
            if (result.code != 0 || !result.output.contains("EDGEMASK_MODULE_STAGED_2"))
                throw new IOException("面具命令安装未确认（" + result.code + "）\n" + result.output);
            return result.output.replace("EDGEMASK_MODULE_STAGED_2", "模块文件已校验，可手动重启");
        } finally { installing.set(false); }
    }
    private MagiskModuleInstaller() { }
}
