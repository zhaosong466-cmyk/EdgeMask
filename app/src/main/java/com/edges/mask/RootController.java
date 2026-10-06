package com.edges.mask;

import android.content.Context;
import android.os.SystemClock;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

final class RootController {
    static volatile boolean available, recovering;
    static volatile DisplayProfile grantedProfile;
    static volatile String message = "Root 未申请；默认仅显示遮罩";
    static final String QUERY = "/system/bin/wm size && /system/bin/wm density";

    static File directory(Context c) throws IOException {
        File d = new File(c.createDeviceProtectedStorageContext().getFilesDir(), "display-recovery");
        if (!d.isDirectory() && !d.mkdirs()) throw new IOException("无法创建恢复目录");
        return d;
    }
    static File file(Context c, String name) throws IOException { return new File(directory(c), name); }
    static String read(File f) throws IOException {
        return f.exists() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim() : "";
    }
    static void write(File f, String s) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(s.getBytes(StandardCharsets.UTF_8)); out.getFD().sync();
        }
    }
    static synchronized void lease(Context c, long expirySeconds) throws IOException {
        File temp = file(c, "deadline.tmp"), dest = file(c, "deadline");
        write(temp, Long.toString(expirySeconds));
        Files.move(temp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }
    static boolean hasBackup(Context c) {
        try { return file(c, "backup.properties").isFile(); } catch (IOException e) { return false; }
    }
    static DisplayProfile query() throws Exception {
        String scaling = DisplayScaling.queryCommand(RootShell.quote("/system/bin/dumpsys"));
        DisplayProfile p = DisplayProfile.parse(RootShell.run(QUERY + " && " + scaling, 8).checked());
        if (p.scalingMode == DisplayScaling.UNKNOWN) throw new IOException("无法确认 ROM 的缩放状态，拒绝 Root 修改");
        return p;
    }
    static void verifyProjection(DisplayProfile original, int[] target, int rotation) throws Exception {
        String data = RootShell.run("/system/bin/dumpsys input | grep -E '(Viewport|logicalFrame|physicalFrame)'", 4).checked();
        DisplayProjection.parse(data).requireCentered(original, target, rotation);
    }

    static synchronized DisplayProfile probe(Context c) throws Exception {
        available = false; grantedProfile = null;
        if (!"0".equals(RootShell.run("id -u", 45).checked().trim())) throw new IOException("未获得 Root 权限");
        if (hasBackup(c)) restore(c);
        if (android.os.Process.myUid() / 100000 != 0) throw new IOException("暂只支持手机主用户");
        RootShell.run("command -v nohup >/dev/null", 5).checked();
        DisplayProfile p = query(); p.validateNoScaleBaseline();
        grantedProfile = p;
        available = true; message = "Root 已授权；原始 " + p.width + "×" + p.height + "，DPI " + p.density;
        return p;
    }

    static void save(Context c, DisplayProfile p) throws Exception {
        if (hasBackup(c)) throw new IOException("仍有待恢复的显示参数，拒绝覆盖备份");
        Properties values = new Properties();
        values.setProperty("physicalWidth", "" + p.physicalWidth);
        values.setProperty("physicalHeight", "" + p.physicalHeight);
        values.setProperty("width", "" + p.width); values.setProperty("height", "" + p.height);
        values.setProperty("density", "" + p.density); values.setProperty("override", "" + p.overridden);
        values.setProperty("scaling", "" + p.scalingMode);
        File temp = file(c, "backup.tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) { values.store(out, "Original display size"); out.getFD().sync(); }
        Files.move(temp.toPath(), file(c, "backup.properties").toPath(), StandardCopyOption.ATOMIC_MOVE);
    }
    static DisplayProfile load(Context c) throws Exception {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(file(c, "backup.properties"))) { p.load(in); }
        return new DisplayProfile(Integer.parseInt(p.getProperty("physicalWidth")),
            Integer.parseInt(p.getProperty("physicalHeight")), Integer.parseInt(p.getProperty("width")),
            Integer.parseInt(p.getProperty("height")), Integer.parseInt(p.getProperty("density")),
            Boolean.parseBoolean(p.getProperty("override")), Integer.parseInt(p.getProperty("scaling", "-1")));
    }

    static synchronized void arm(Context c, DisplayProfile p, long expirySeconds) throws Exception {
        DisplayScaling.argument(p.scalingMode);
        if (hasBackup(c)) throw new IOException("旧会话尚未恢复，拒绝启动新的回退进程");
        for (String s : new String[]{"stop", "ready", "state", "pid"}) {
            File f = file(c, s);
            if (f.exists() && !f.delete()) throw new IOException("无法清理旧回退状态");
        }
        // Precreate files as the app UID: root writes must not make them unreadable to the app.
        for (String s : new String[]{"ready", "state", "pid", "rollback.log", "target", "applied", "bootid"}) write(file(c, s), "");
        save(c, p); lease(c, expirySeconds);
        File script = file(c, "watchdog.sh");
        write(script, GuardScript.build(directory(c).getAbsolutePath(), p, "/system/bin/wm", "/proc/uptime", "/system/bin/dumpsys"));
        RootShell.run("nohup /system/bin/sh " + RootShell.quote(script.getAbsolutePath())
            + " </dev/null >/dev/null 2>&1 &", 5).checked();
        long end = SystemClock.elapsedRealtime() + 3000;
        while (!read(file(c, "ready")).equals("ready") && SystemClock.elapsedRealtime() < end) Thread.sleep(50);
        String pid = read(file(c, "pid"));
        if (!read(file(c, "ready")).equals("ready") || !pid.matches("[1-9][0-9]{0,8}"))
            throw new IOException("独立回退进程未启动，未修改显示");
        RootShell.run("kill -0 " + pid, 3).checked();
        if (SystemClock.elapsedRealtime() / 1000 >= expirySeconds) throw new IOException("安全预览已超时，未修改显示");
    }

    static void requestTarget(Context c, int w, int h, long expiry) throws Exception {
        String target = w + "x" + h;
        File temp = file(c, "target.tmp"); write(temp, target);
        Files.move(temp.toPath(), file(c, "target").toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        long end = Math.min(SystemClock.elapsedRealtime() + 5000, expiry * 1000);
        while (SystemClock.elapsedRealtime() < end) {
            if (!read(file(c, "state")).isEmpty()) throw new IOException("回退已开始");
            if (read(file(c, "applied")).equals(target)) return;
            Thread.sleep(50);
        }
        throw new IOException("系统尺寸应用未及时确认，触发恢复");
    }

    static synchronized void restore(Context c) throws Exception {
        if (!hasBackup(c)) return;
        DisplayProfile original = load(c);
        write(file(c, "stop"), "1");
        String savedBoot = read(file(c, "bootid"));
        String currentBoot = RootShell.run("cat /proc/sys/kernel/random/boot_id", 5).checked().trim();
        boolean sameBoot = !savedBoot.isEmpty() && savedBoot.equals(currentBoot);
        long end = SystemClock.elapsedRealtime() + 8000;
        String state = read(file(c, "state")), pid = read(file(c, "pid"));
        while (sameBoot && !state.equals("restored") && !state.equals("failed") && !pid.isEmpty()
                && SystemClock.elapsedRealtime() < end) {
            Thread.sleep(100); state = read(file(c, "state"));
        }
        if (sameBoot && !state.equals("restored") && !state.equals("failed") && pid.matches("[1-9][0-9]{0,8}")
                && RootShell.run("kill -0 " + pid, 3).code == 0)
            throw new IOException("回退进程仍在处理中，请稍后再次恢复；禁止再次修改");
        String restore = "/system/bin/wm size " + original.restoreArgument();
        if (original.scalingMode != DisplayScaling.UNKNOWN) {
            // Try both restores even if the first command fails. Never modify density.
            restore += "; sized=$?; /system/bin/wm scaling " + DisplayScaling.argument(original.scalingMode)
                + "; scaled=$?; [ \"$sized\" -eq 0 ] && [ \"$scaled\" -eq 0 ]";
        }
        RootShell.run(restore, 8).checked();
        DisplayProfile current = original.scalingMode == DisplayScaling.UNKNOWN
            ? DisplayProfile.parse(RootShell.run(QUERY, 8).checked()) : query();
        if (!original.sameSize(current) || (original.scalingMode != DisplayScaling.UNKNOWN
                && original.scalingMode != current.scalingMode))
            throw new IOException("尺寸或缩放恢复校验失败，请使用备用恢复命令");
        if (hasBackup(c) && !file(c, "backup.properties").delete()) throw new IOException("显示已恢复，但备份清理失败");
    }
}
