package com.edges.mask;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

/** Executes production RootShell + installer using a fake su/Magisk provided by the Python runner. */
public final class ModuleInstallerChecks {
    public static void main(String[] args) throws Exception {
        File launcher = new File(args[0]), archive = new File(args[1]);
        boolean succeeds = "success".equals(args[2]);
        if ("concurrent".equals(args[2])) {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread first = new Thread(() -> {
                try { MagiskModuleInstaller.install(launcher, archive); }
                catch (Throwable e) { failure.set(e); }
            });
            first.start();
            long deadline = System.currentTimeMillis() + 3000;
            while (!MagiskModuleInstaller.running() && first.isAlive() && System.currentTimeMillis() < deadline)
                Thread.yield();
            if (!MagiskModuleInstaller.running()) throw new AssertionError("installer did not start");
            try {
                MagiskModuleInstaller.install(launcher, archive);
                throw new AssertionError("duplicate installation was accepted");
            } catch (java.io.IOException expected) { }
            first.join(10000);
            if (first.isAlive() || failure.get() != null) throw new AssertionError("first installation failed", failure.get());
        } else {
            try {
                String output = MagiskModuleInstaller.install(launcher, archive);
                if (!succeeds || !output.contains("模块文件已校验")) throw new AssertionError(output);
            } catch (Exception e) {
                if (succeeds) throw e;
                if (args.length > 3 && !e.getMessage().contains(args[3])) throw new AssertionError(e);
            }
        }
        if (MagiskModuleInstaller.running()) throw new AssertionError("installation flag was not released");
        System.out.println("PASS: production module installer " + args[2]);
    }
}
