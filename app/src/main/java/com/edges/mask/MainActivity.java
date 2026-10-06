package com.edges.mask;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    LinearLayout root;
    TextView status, rootStatus;
    Preview preview;
    SeekBar width, radius;
    Switch rootSwitch;
    Button keepButton;
    boolean updatingSwitch, busy;
    final Handler ui = new Handler(Looper.getMainLooper());
    final ExecutorService tasks = Executors.newSingleThreadExecutor();
    final Runnable refreshStatus = new Runnable() {
        public void run() { update(); ui.postDelayed(this, 500); }
    };
    int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    TextView text(String s, int size) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size);
        t.setTextColor(Color.rgb(24, 32, 48)); t.setPadding(0, dp(8), 0, dp(8)); root.addView(t); return t;
    }
    Button button(String s, Runnable fn) {
        Button b = new Button(this); b.setText(s); root.addView(b, new LinearLayout.LayoutParams(-1, dp(54)));
        b.setOnClickListener(v -> fn.run()); return b;
    }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.rgb(240, 244, 250));
        getWindow().setNavigationBarColor(Color.rgb(240, 244, 250));
        ScrollView scroll = new ScrollView(this); root = new LinearLayout(this); root.setOrientation(1);
        root.setBackgroundColor(Color.rgb(240, 244, 250)); scroll.addView(root); setContentView(scroll);
        text("对称黑边 1.1", 28); text("黑边 · 圆角 · 可选 Root 显示同步", 15);
        button("关闭遮罩并恢复原始显示", this::restoreDisplay);
        status = text("", 13); rootStatus = text("", 14);
        keepButton = button("确认并保留当前分辨率", () -> {
            RootSyncService s = RootSyncService.instance; if (s != null) s.confirm(); update();
        });
        preview = new Preview(this); root.addView(preview, new LinearLayout.LayoutParams(-1, dp(160)));
        text("显示方式", 18);
        RadioGroup modes = new RadioGroup(this);
        RadioButton a = new RadioButton(this); a.setId(101); a.setText("无障碍遮罩（推荐，触摸穿透）");
        RadioButton o = new RadioButton(this); o.setId(102); o.setText("普通悬浮窗");
        modes.addView(a); modes.addView(o); root.addView(modes); modes.check(Config.access(this) ? 101 : 102);
        modes.setOnCheckedChangeListener((g, id) -> {
            Config.stop(this); Config.prefs(this).edit().putBoolean("access", id == 101).apply(); update();
        });
        rootSwitch = new Switch(this); rootSwitch.setText("Root 同步系统显示（可选，测试功能）");
        root.addView(rootSwitch); rootSwitch.setChecked(Config.rootWanted(this));
        rootSwitch.setOnCheckedChangeListener((view, checked) -> {
            if (updatingSwitch) return;
            if (checked && (!RootController.available || RootController.recovering || busy)) {
                updatingSwitch = true; view.setChecked(false); updatingSwitch = false;
                toast("请先点击检测并申请 Root 权限"); return;
            }
            Config.stop(this); Config.prefs(this).edit().putBoolean("rootWanted", checked).apply(); update();
        });
        button("检测并申请 Root 权限", this::requestRoot);
        text("Root：系统按黑条宽度居中收窄，应用重新排版；圆角仍由遮罩绘制。每侧最多 15%，可见短边至少 320 dp。DPI 保持不变。每次改动需在 15 秒内确认，否则恢复。此功能尚未经真机验证，无法保证所有 ROM 稳定。", 13);
        width = slider("每侧黑条宽度", 25, "%", "width", 4);
        radius = slider("内边框圆角半径", 160, "dp", "radius", 32);
        button("启用 / 应用当前设置", this::startMask);
        button("授予无障碍权限", () -> {
            Config.stop(this);
            new AlertDialog.Builder(this).setTitle("无障碍权限用途")
                .setMessage("仅绘制黑边与圆角，不读取屏幕文字、不执行点击。继续后找到“对称黑边 · 无障碍遮罩”并手动开启，然后返回应用点击启用。")
                .setPositiveButton("打开设置", (d, which) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton("取消", null).show();
        });
        button("授予在其他应用上层显示权限", () -> {
            Config.stop(this);
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        });
        button("恢复默认遮罩参数", () -> { width.setProgress(4); radius.setProgress(32); });
        button("查看备用恢复方法", () -> new AlertDialog.Builder(this).setTitle("显示异常时恢复")
            .setMessage("不要卸载应用或撤销 Root 后再恢复。优先等待自动回退，或点击页面顶部恢复按钮。\n\n若自动回退不可用，可从已授权的电脑执行：\nadb shell su -c 'wm size reset'\n\n这会恢复默认尺寸；若原来有自定义尺寸，请使用原来的 WxH。Root 模式不修改 DPI，不需要重置密度。\n\n重启/更新后应用会尝试恢复，但 ROM 或 Root 管理器可能阻止。恢复成功前不要开启新会话。")
            .setPositiveButton("知道了", null).show());
        text("非 Root 模式仅遮住内容，不改变应用布局。普通悬浮窗拦截黑条及圆角方块内的触摸。Root 模式会在旋转、移除应用任务或监控异常时恢复，并且不会开机自动启用。AI 编写；建议首次只用每侧 1% 测试。", 13);
        update();
    }
    SeekBar slider(String title, int max, String unit, String key, int initial) {
        TextView label = text("", 16); SeekBar s = new SeekBar(this); s.setMax(max);
        s.setProgress(Config.prefs(this).getInt(key, initial)); label.setText(title + "：" + s.getProgress() + " " + unit);
        root.addView(s);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar v, int value, boolean user) {
                label.setText(title + "：" + value + " " + unit);
                Config.prefs(MainActivity.this).edit().putInt(key, value).apply();
                Config.refresh(MainActivity.this); preview.invalidate();
                if (key.equals("width") && RootSyncService.instance != null) RootSyncService.instance.schedule();
                update();
            }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        LinearLayout row = new LinearLayout(this);
        for (int delta : new int[]{-1, 1}) {
            Button step = new Button(this); step.setText(delta < 0 ? "−1" : "+1");
            row.addView(step, new LinearLayout.LayoutParams(0, dp(44), 1));
            step.setOnClickListener(v -> s.setProgress(Math.max(0, Math.min(max, s.getProgress() + delta))));
        }
        root.addView(row); return s;
    }
    void requestRoot() {
        if (busy || RootController.recovering) { toast("正在处理，请稍后"); return; }
        if (RootSyncService.instance != null) { toast("请先关闭当前 Root 显示会话"); return; }
        busy = true; RootController.message = "等待 Root 管理器授权…"; update();
        tasks.execute(() -> {
            try { RootController.probe(this); }
            catch (Exception e) { RootController.available = false; RootController.message = "Root 不可用：" + e.getMessage(); }
            ui.post(() -> { busy = false; if (!isDestroyed()) update(); });
        });
    }
    void startMask() {
        RootSyncService session = RootSyncService.instance;
        if (busy || RootController.recovering || (session != null && session.stopping)
                || (session == null && RootController.hasBackup(this))) { toast("原始显示正在恢复，确认恢复后再启用"); return; }
        if (Config.access(this) && MaskAccessibility.instance == null) { toast("请先开启无障碍服务"); return; }
        if (!Config.access(this) && !Settings.canDrawOverlays(this)) { toast("请先授予悬浮窗权限"); return; }
        if (Config.rootWanted(this) && !RootController.available) { toast("请先授予 Root 权限"); return; }
        if (getWindowManager().getDefaultDisplay().getDisplayId() != 0 && Config.rootWanted(this)) { toast("Root 暂只支持主显示器"); return; }
        if ((Config.rootWanted(this) || !Config.access(this)) && Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        Config.prefs(this).edit().putBoolean("enabled", true).apply();
        try {
            if (!Config.access(this)) startForegroundService(new Intent(this, OverlayService.class));
            else Config.refresh(this);
            if (Config.rootWanted(this)) startForegroundService(new Intent(this, RootSyncService.class));
        } catch (RuntimeException e) { Config.stop(this); toast("启动失败：" + e.getClass().getSimpleName()); }
        update();
    }
    void restoreDisplay() {
        Config.stop(this); Config.prefs(this).edit().putBoolean("rootWanted", false).apply();
        if (RootSyncService.instance == null && RootController.hasBackup(this) && !busy && !RootController.recovering) {
            busy = true; RootController.message = "正在恢复原始显示…";
            tasks.execute(() -> {
                try { RootController.restore(this); RootController.message = "原始显示已恢复"; }
                catch (Exception e) { RootController.message = "恢复未确认：" + e.getMessage(); }
                ui.post(() -> { busy = false; if (!isDestroyed()) update(); });
            });
        }
        update();
    }
    void update() {
        if (root == null) return;
        int side = dp(24);
        if (Config.enabled(this) && !Config.rootApplied()) side = Math.max(side,
            getResources().getDisplayMetrics().widthPixels * Config.prefs(this).getInt("width", 4) / 100 + dp(10));
        root.setPadding(side, dp(42), side, dp(32));
        if (status != null) status.setText("遮罩：" + (Config.enabled(this) ? "已启用" : "已关闭")
            + "  |  无障碍：" + (MaskAccessibility.instance != null ? "已连接" : "未连接")
            + "  |  悬浮窗：" + (Settings.canDrawOverlays(this) ? "已授权" : "未授权"));
        RootSyncService session = RootSyncService.instance;
        String extra = "";
        if (session != null && session.active) extra = session.pending
            ? "\n请确认显示及触摸正常；" + Math.max(0, session.previewDeadline - SystemClock.elapsedRealtime() / 1000) + " 秒后自动恢复"
            : "\n已确认；服务停止或心跳丢失时恢复原始显示";
        if (rootStatus != null) rootStatus.setText(RootController.message + extra);
        if (keepButton != null) { boolean show = session != null && session.canConfirm();
            keepButton.setVisibility(show ? View.VISIBLE : View.GONE); keepButton.setEnabled(show); }
        if (rootSwitch != null && rootSwitch.isChecked() != Config.rootWanted(this)) {
            updatingSwitch = true; rootSwitch.setChecked(Config.rootWanted(this)); updatingSwitch = false;
        }
    }
    @Override public void onConfigurationChanged(Configuration c) { super.onConfigurationChanged(c); update(); preview.invalidate(); }
    @Override protected void onResume() { super.onResume(); ui.removeCallbacks(refreshStatus); ui.post(refreshStatus); }
    @Override protected void onPause() { ui.removeCallbacks(refreshStatus); super.onPause(); }
    @Override protected void onDestroy() { ui.removeCallbacks(refreshStatus); tasks.shutdown(); super.onDestroy(); }
    final class Preview extends View {
        final Paint paint = new Paint(3); Preview(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), b = w * Config.prefs(MainActivity.this).getInt("width", 4) / 100f;
            float r = Math.min(dp(Config.prefs(MainActivity.this).getInt("radius", 32)), Math.min((w - 2 * b) / 2, h / 2));
            paint.setColor(Color.BLACK); c.drawRoundRect(0, 0, w, h, dp(12), dp(12), paint);
            paint.setColor(Color.rgb(133, 182, 240)); c.drawRoundRect(b, 0, w - b, h, r, r, paint);
            paint.setColor(Color.WHITE); paint.setTextSize(dp(17)); paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("可见区域预览", w / 2, h / 2, paint);
        }
    }
}
