package com.edges.mask;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.view.WindowManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A session is deliberately not restarted by Android. Root settings are never reapplied at boot. */
public class RootSyncService extends Service implements DisplayManager.DisplayListener {
    static volatile RootSyncService instance;
    volatile boolean active, armed, verified, pending, stopping;
    volatile long previewDeadline;
    private DisplayProfile original;
    private int originalRotation, lastPercent = -1;
    private volatile int generation;
    private volatile int verifiedGeneration = -1;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    int rotation() { return ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRotation(); }
    boolean wanted() { return !stopping && Config.enabled(this) && Config.rootWanted(this); }
    final Runnable applyTask = () -> {
        final int request = generation;
        worker.execute(() -> apply(request));
    };
    final Runnable heartbeat = new Runnable() {
        public void run() {
            if (stopping) return;
            try {
                if (armed) {
                    if (!wanted()) { revert("遮罩已关闭"); return; }
                    if ((Config.access(RootSyncService.this) && MaskAccessibility.instance == null)
                            || (!Config.access(RootSyncService.this) && !android.provider.Settings.canDrawOverlays(RootSyncService.this))) {
                        revert("遮罩权限已失效"); return;
                    }
                    String state = RootController.read(RootController.file(RootSyncService.this, "state"));
                    if (!state.isEmpty()) {
                        revert("独立回退进程已触发恢复"); return;
                    }
                    long now = SystemClock.elapsedRealtime() / 1000;
                    if (pending && now >= previewDeadline) { revert("预览超时，已请求恢复原始分辨率"); return; }
                    if (!pending) RootController.lease(RootSyncService.this, now + 20);
                    if (rotation() != originalRotation) { revert("屏幕旋转，已请求恢复原始分辨率"); return; }
                }
            } catch (Exception e) { revert("回退监控异常，已请求恢复"); return; }
            ui.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate() {
        super.onCreate(); instance = this;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("root-display", "Root 显示安全会话", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 20, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent restore = PendingIntent.getService(this, 21, new Intent(this, RootSyncService.class).setAction("RESTORE"), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "root-display")
            .setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("Root 显示同步会话")
            .setContentText("点击调整；随时可恢复原始分辨率").setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "恢复原始显示", restore).build()).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(2, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(2, n);
        getSystemService(DisplayManager.class).registerDisplayListener(this, ui);
        ui.post(heartbeat);
    }
    @Override public int onStartCommand(Intent i, int flags, int id) {
        if (i != null && "RESTORE".equals(i.getAction())) revert("手动恢复");
        else schedule();
        return START_NOT_STICKY;
    }
    void schedule() {
        if (!wanted()) return;
        generation++; ui.removeCallbacks(applyTask); ui.postDelayed(applyTask, 450);
    }
    private void apply(int request) {
        if (!wanted() || request != generation) return;
        try {
            int percent = Config.prefs(this).getInt("width", 4);
            if (original == null) {
                if (!RootController.available || RootController.recovering) throw new Exception("请先检测并授予 Root 权限");
                original = RootController.query(); original.validateBaseline(); originalRotation = rotation();
            }
            if (rotation() != originalRotation) throw new Exception("旋转后请重新开启 Root 会话");
            int[] target = original.target(percent, originalRotation);
            if (percent == lastPercent) { verifiedGeneration = request; return; }
            if (percent == 0) { ui.post(() -> revert("宽度为零，恢复原始显示")); return; }
            if (!armed) {
                previewDeadline = SystemClock.elapsedRealtime() / 1000 + 15;
                pending = true;
                RootController.arm(this, original, previewDeadline);
                armed = true;
            } else {
                if (!pending) { previewDeadline = SystemClock.elapsedRealtime() / 1000 + 15; pending = true; }
                RootController.lease(this, previewDeadline);
            }
            if (!wanted() || request != generation) return;
            if (SystemClock.elapsedRealtime() / 1000 >= previewDeadline) throw new Exception("预览已超时");
            if (!RootController.read(RootController.file(this, "state")).isEmpty()) throw new Exception("回退已开始，拒绝修改");
            // Configuration callbacks may run while wm is updating the logical display.
            active = true; verified = false;
            // The watchdog is the only size writer during a session. A delayed su request
            // can never apply a new size after the watchdog has already rolled back.
            RootController.requestTarget(this, target[0], target[1], previewDeadline);
            DisplayProfile result = RootController.query();
            if (result.width != target[0] || result.height != target[1] || result.density != original.density
                    || result.physicalWidth != original.physicalWidth || result.physicalHeight != original.physicalHeight)
                throw new Exception("系统未按预期修改显示，触发恢复");
            lastPercent = percent;
            verifiedGeneration = request; verified = true;
            RootController.message = "当前 " + target[0] + "×" + target[1] + "，DPI 保持 " + original.density;
            ui.post(() -> { if (!stopping) Config.refresh(RootSyncService.this); });
        } catch (Exception e) {
            ui.post(() -> revert(e.getMessage() == null ? "Root 显示修改失败" : e.getMessage()));
        }
    }
    void confirm() {
        if (!canConfirm()) return;
        long now = SystemClock.elapsedRealtime() / 1000;
        if (now >= previewDeadline) { revert("确认超时"); return; }
        try {
            // A lease may never be renewed after the watchdog has started to restore.
            String state = RootController.read(RootController.file(this, "state"));
            if (!state.isEmpty()) { revert("回退已触发，请重新启用"); return; }
            RootController.lease(this, now + 20); pending = false;
        } catch (Exception e) { revert("无法确认，触发恢复"); }
    }
    boolean canConfirm() { return active && verified && pending && !stopping && verifiedGeneration == generation; }
    void revert(String reason) {
        if (stopping) return;
        stopping = true; active = false; verified = false; pending = false; generation++;
        ui.removeCallbacksAndMessages(null);
        Config.prefs(this).edit().putBoolean("enabled", false).putBoolean("rootWanted", false).apply();
        Config.refresh(this);
        RootController.message = reason + "；正在恢复…";
        worker.execute(() -> {
            try { RootController.restore(this); RootController.message = reason + "；原始显示已恢复"; }
            catch (Exception e) { RootController.message = "恢复未确认：" + e.getMessage(); }
            ui.post(() -> stopSelf());
        });
    }
    @Override public void onDisplayChanged(int id) {
        if (id == 0 && active && !stopping && rotation() != originalRotation) revert("屏幕旋转");
    }
    @Override public void onDisplayAdded(int id) { }
    @Override public void onDisplayRemoved(int id) { if (id == 0) revert("主显示器改变"); }
    @Override public void onConfigurationChanged(Configuration c) { super.onConfigurationChanged(c); onDisplayChanged(0); }
    @Override public void onTaskRemoved(Intent i) { revert("应用任务已移除"); super.onTaskRemoved(i); }
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onDestroy() {
        if (!stopping) revert("显示服务已停止");
        getSystemService(DisplayManager.class).unregisterDisplayListener(this);
        ui.removeCallbacksAndMessages(null); instance = null; worker.shutdown(); super.onDestroy();
    }
}
