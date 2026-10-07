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
    volatile boolean active, armed, verified, pending, stopping, projectionVerified;
    private volatile boolean checkingProjection;
    private long lastProjectionCheck;
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
                    long now = SystemClock.elapsedRealtime() / 1000;
                    if (!state.isEmpty()) {
                        if (pending && now >= previewDeadline) revert("预览超时，已请求恢复原始显示");
                        else {
                            String detail=RootController.read(RootController.file(RootSyncService.this,"error"));
                            fallback(detail.isEmpty()?"独立回退进程已触发恢复":detail);
                        }
                        return;
                    }
                    if (pending && now >= previewDeadline) { revert("预览超时，已请求恢复原始分辨率"); return; }
                    if (!pending) RootController.lease(RootSyncService.this, now + 20);
                    if (rotation() != originalRotation) { revert("屏幕旋转，已请求恢复原始分辨率"); return; }
                    if (!pending && projectionVerified && !checkingProjection && now - lastProjectionCheck >= 5) {
                        checkingProjection = true; lastProjectionCheck = now;
                        final int request = generation;
                        worker.execute(() -> {
                            try {
                                DisplayProfile current = RootController.query();
                                if (current.scalingMode != DisplayScaling.OFF)
                                    throw new Exception("系统重新开启了自动缩放");
                                RootController.verifyProjection(original, original.target(lastPercent, originalRotation), originalRotation);
                            } catch (Exception e) {
                                ui.post(() -> { if (request == generation && wanted()) fallback("屏幕映射改变或再次拉伸"); });
                            } finally { checkingProjection = false; }
                        });
                    }
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
        generation++;
        ui.removeCallbacks(applyTask); ui.postDelayed(applyTask, 450);
    }
    private void apply(int request) {
        if (!wanted() || request != generation) return;
        try {
            int percent = Config.prefs(this).getInt("width", 4);
            if (original == null) {
                if (!RootController.available || RootController.recovering) throw new Exception("请先检测并授予 Root 权限");
                original = RootController.query(); original.validateNoScaleBaseline(); originalRotation = rotation();
                // Unsupported viewport formats must fail before changing any display setting.
                RootController.verifyProjection(original, new int[]{original.width, original.height}, originalRotation);
            }
            if (rotation() != originalRotation) throw new Exception("旋转后请重新开启 Root 会话");
            int[] target = original.target(percent, originalRotation);
            if (percent == lastPercent) {
                RootController.verifyProjection(original, target, originalRotation);
                verifiedGeneration = request; return;
            }
            if (percent == 0) { ui.post(() -> revert("宽度为零，恢复原始显示")); return; }
            if (!armed) {
                previewDeadline = SystemClock.elapsedRealtime() / 1000 + 30;
                pending = true;
                RootController.arm(this, original, previewDeadline, originalRotation);
                armed = true;
            } else {
                if (!pending) { previewDeadline = SystemClock.elapsedRealtime() / 1000 + 25; pending = true; }
                RootController.lease(this, previewDeadline);
            }
            if (!wanted() || request != generation) return;
            if (SystemClock.elapsedRealtime() / 1000 >= previewDeadline) throw new Exception("预览已超时");
            if (!RootController.read(RootController.file(this, "state")).isEmpty()) throw new Exception("回退已开始，拒绝修改");
            // Configuration callbacks may run while wm is updating the logical display.
            active = true; verified = false; projectionVerified = false;
            ui.post(() -> { if (!stopping) Config.refresh(RootSyncService.this); });
            // The watchdog is the only size writer during a session. A delayed su request
            // can never apply a new size after the watchdog has already rolled back.
            RootController.requestTarget(this, target[0], target[1], previewDeadline);
            DisplayProfile result = RootController.query();
            if (result.width != target[0] || result.height != target[1] || result.density != original.density
                    || result.physicalWidth != original.physicalWidth || result.physicalHeight != original.physicalHeight
                    || result.scalingMode != DisplayScaling.OFF)
                throw new Exception("系统未按预期修改显示，触发恢复");
            // WM accepting a resolution is not proof that the ROM stopped stretching it.
            Exception projectionError = null;
            for (int attempt = 0; attempt < 3; attempt++) {
                if (!wanted() || SystemClock.elapsedRealtime() / 1000 >= previewDeadline) return;
                try { RootController.verifyProjection(original, target, originalRotation); projectionError = null; break; }
                catch (Exception e) { projectionError = e; if (attempt < 2) Thread.sleep(150); }
            }
            if (projectionError != null) throw projectionError;
            if (!wanted() || request != generation) return;
            lastPercent = percent;
            projectionVerified = true; verifiedGeneration = request; verified = true;
            RootController.message = "已提交合成层居中 " + target[0] + "×" + target[1]
                + (Config.gpuCompatibility(this)?" + GPU 合成":"") + "；请检查手机实际画面";
            ui.post(() -> { if (!stopping) Config.refresh(RootSyncService.this); });
        } catch (Exception e) {
            ui.post(() -> fallback(e.getMessage() == null ? "Root 显示修改失败" : e.getMessage()));
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
            RootController.message = "已保留合成层居中；持续监控输出投影与触摸映射";
            Config.refresh(this);
        } catch (Exception e) { revert("无法确认，触发恢复"); }
    }
    boolean canConfirm() { return active && verified && pending && !stopping && verifiedGeneration == generation; }
    void revert(String reason) {
        finish(reason, false);
    }
    void fallback(String reason) {
        if (!wanted()) { revert(reason); return; }
        finish(reason, true);
    }
    private void finish(String reason, boolean keepMask) {
        if (stopping) return;
        stopping = true; active = false; verified = false; pending = false; projectionVerified = false; generation++;
        ui.removeCallbacksAndMessages(null);
        boolean preserve = keepMask && ((Config.access(this) && MaskAccessibility.instance != null)
            || (!Config.access(this) && android.provider.Settings.canDrawOverlays(this)));
        Config.prefs(this).edit().putBoolean("enabled", preserve).putBoolean("rootWanted", false).apply();
        Config.refresh(this);
        RootController.message = reason + "；正在恢复…";
        worker.execute(() -> {
            try { RootController.restore(this); RootController.message = reason
                + (preserve ? "；已恢复尺寸和缩放，继续普通黑边" : "；原始显示已恢复"); }
            catch (Exception e) { RootController.message = "恢复未确认：" + e.getMessage(); }
            ui.post(() -> { Config.refresh(this); stopSelf(); });
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
