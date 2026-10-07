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
    LinearLayout root, screen, header, footer;
    TextView status, rootStatus;
    Preview preview;
    SeekBar width, radius;
    Switch rootSwitch;
    Button keepButton;
    Button stretchButton;
    boolean updatingSwitch, busy;
    final Handler ui = new Handler(Looper.getMainLooper());
    final ExecutorService tasks = Executors.newSingleThreadExecutor();
    final Runnable refreshStatus = new Runnable() {
        public void run() { update(); ui.postDelayed(this, 500); }
    };
    int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    TextView text(String s, int size) {
        return text(root, s, size);
    }
    TextView text(LinearLayout host, String s, int size) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size);
        t.setTextColor(Color.rgb(24, 32, 48)); t.setPadding(0, dp(4), 0, dp(4)); host.addView(t); return t;
    }
    Button button(String s, Runnable fn) {
        return button(root, s, fn);
    }
    Button button(LinearLayout host, String s, Runnable fn) {
        Button b = new Button(this); b.setText(s); host.addView(b, new LinearLayout.LayoutParams(-1, dp(54)));
        b.setOnClickListener(v -> fn.run()); return b;
    }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        screen = new LinearLayout(this); screen.setOrientation(1);
        screen.setBackgroundColor(Color.rgb(240, 244, 250));
        header = new LinearLayout(this); header.setOrientation(1); screen.addView(header);
        text(header, "对称黑边 1.3", 24);
        status = text(header, "", 12); status.setMaxLines(2);
        rootStatus = text(header, "", 13); rootStatus.setMaxLines(3);
        ScrollView scroll = new ScrollView(this); root = new LinearLayout(this); root.setOrientation(1);
        scroll.setClipToPadding(true); scroll.addView(root);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        footer = new LinearLayout(this); footer.setOrientation(1); screen.addView(footer);
        keepButton = button(footer, "检查手机画面并确认", this::confirmPhysicalDisplay);
        stretchButton = button(footer, "画面被拉伸：恢复并保留黑边", () -> {
            Config.reportStretch(this);
            RootSyncService s = RootSyncService.instance;
            if (s != null) s.fallback("你反馈实际画面被拉伸");
            update();
        });
        button(footer, "恢复原始显示并关闭遮罩", this::restoreDisplay);
        screen.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                screen.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                darkSystemIcons();
                return WindowInsets.CONSUMED;
            }
            int left = insets.getSystemWindowInsetLeft(), top = insets.getSystemWindowInsetTop();
            int right = insets.getSystemWindowInsetRight(), bottom = insets.getSystemWindowInsetBottom();
            if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                DisplayCutout cutout = insets.getDisplayCutout();
                left = Math.max(left, cutout.getSafeInsetLeft()); top = Math.max(top, cutout.getSafeInsetTop());
                right = Math.max(right, cutout.getSafeInsetRight()); bottom = Math.max(bottom, cutout.getSafeInsetBottom());
            }
            screen.setPadding(left, top, right, bottom);
            return insets.consumeSystemWindowInsets();
        });
        setContentView(screen);
        screen.post(() -> { darkSystemIcons(); screen.requestApplyInsets(); });
        text("黑边 · 圆角 · 可选 Root 显示同步", 15);
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
            if (checked && !Config.rootRestriction(this).isEmpty()) {
                updatingSwitch = true; view.setChecked(false); updatingSwitch = false;
                toast(Config.rootRestriction(this)); return;
            }
            if (checked && (!RootController.available || RootController.recovering || busy)) {
                updatingSwitch = true; view.setChecked(false); updatingSwitch = false;
                toast("请先点击检测并申请 Root 权限"); return;
            }
            if (checked && rootWidthLimit() == 0) {
                updatingSwitch = true; view.setChecked(false); updatingSwitch = false;
                toast("当前显示宽度已到安全下限，无法进一步收窄"); return;
            }
            Config.stop(this); Config.prefs(this).edit().putBoolean("rootWanted", checked).apply(); update();
        });
        button("检测并申请 Root 权限", this::requestRoot);
        text("Root：系统报告的映射不能证明实际面板没有拉伸。小米 13 Ultra / Android 16 及以上暂时停用 Root 收窄，普通黑边与圆角照常可调。其他机型每次改动需直接检查手机画面，在 15 秒内确认；失败时恢复尺寸与缩放。每侧最多 15%，短边至少 320 dp；DPI 不变。", 13);
        button("复制显示诊断（只读，需要 Root）", this::copyDisplayDiagnostics);
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
        button("查看备用恢复方法", this::showRecoveryHelp);
        text("非 Root 模式仅遮住内容，不改变应用布局。普通悬浮窗拦截黑条及圆角方块内的触摸。Root 模式会在旋转、移除应用任务或监控异常时恢复，并且不会开机自动启用。AI 编写；建议首次只用每侧 1% 测试。", 13);
        update();
    }
    void darkSystemIcons() {
        if (Build.VERSION.SDK_INT >= 30 && getWindow().getInsetsController() != null) {
            int flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            getWindow().getInsetsController().setSystemBarsAppearance(flags, flags);
        }
    }
    void confirmPhysicalDisplay() {
        RootSyncService session = RootSyncService.instance;
        if (session == null || !session.canConfirm()) return;
        LinearLayout body = new LinearLayout(this); body.setOrientation(1); body.setPadding(dp(20), dp(8), dp(20), dp(8));
        text(body, "请直接看手机屏幕：圆形是否是正圆，方框是否是正方形，触摸是否准确？截图比例正常不能证明实际显示正常。", 15);
        body.addView(new RatioPattern(this), new LinearLayout.LayoutParams(-1, dp(150)));
        new AlertDialog.Builder(this).setTitle("检查真实画面比例")
            .setView(body).setPositiveButton("画面与触摸正常", (d, which) -> {
                if (RootSyncService.instance == session && session.canConfirm()) session.confirm();
                else toast("预览已结束，请重新启用");
                update();
            }).setNegativeButton("被拉伸 / 恢复", (d, which) -> {
                Config.reportStretch(this);
                if (RootSyncService.instance == session) session.fallback("你反馈实际画面被拉伸");
                update();
            }).show();
    }
    void copyDisplayDiagnostics() {
        if (busy || RootController.recovering) { toast("正在处理，请稍后"); return; }
        busy = true; update(); toast("正在读取显示参数；可能出现 Root 授权窗口");
        tasks.execute(() -> {
            String report = RootDiagnostics.collect(this);
            ui.post(() -> {
                busy = false;
                if (isDestroyed()) return;
                new AlertDialog.Builder(this).setTitle("只读显示诊断")
                    .setMessage("仅包含机型、系统版本、显示尺寸及映射。未修改系统设置，也不会自动发送。复制后可以粘贴给我分析。")
                    .setPositiveButton("复制", (d, which) -> {
                        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("EdgeMask 显示诊断", report));
                        toast("显示诊断已复制");
                    }).setNegativeButton("取消", null).show();
                update();
            });
        });
    }
    void showRecoveryHelp() {
        String command = "wm size reset; wm scaling auto";
        String note = "以下命令适用于原先使用默认尺寸与自动缩放的情况。原来已有修改时，应改用原始 WxH 和 auto / off。";
        if (RootController.hasBackup(this)) {
            try {
                DisplayProfile original = RootController.load(this);
                command = "wm size " + original.restoreArgument();
                if (original.scalingMode != DisplayScaling.UNKNOWN)
                    command += "; wm scaling " + DisplayScaling.argument(original.scalingMode);
                note = original.scalingMode == DisplayScaling.UNKNOWN
                    ? "以下命令根据旧版尺寸备份生成。旧版未修改缩放，不额外重置缩放。"
                    : "以下命令根据本次会话的原始尺寸与缩放备份生成。";
            } catch (Exception e) { note = "原始备份无法读取，请核对原始设置后使用命令。"; }
        }
        new AlertDialog.Builder(this).setTitle("显示异常时恢复")
            .setMessage("优先等待自动回退，或点击页面底部恢复按钮。\n\n" + note
                + "\n从此前已授权的电脑执行：\nadb shell su -c '" + command + "'"
                + "\n\nDPI 未修改，无需重置密度。恢复成功前不要卸载应用、清除数据或撤销 Root。重启/更新后会尝试恢复，但 ROM 或 Root 管理器可能阻止。")
            .setPositiveButton("知道了", null).show();
    }
    int rootWidthLimit() {
        DisplayProfile p = RootController.grantedProfile;
        return p == null ? 0 : p.maximumPercent(getWindowManager().getDefaultDisplay().getRotation());
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
        if (Config.rootWanted(this) && !Config.rootRestriction(this).isEmpty())
            Config.prefs(this).edit().putBoolean("rootWanted", false).apply();
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
        root.setPadding(side, dp(8), side, dp(12));
        header.setPadding(side, dp(6), side, dp(6)); footer.setPadding(side, dp(6), side, dp(8));
        if (width != null) {
            int max = Config.rootWanted(this) ? rootWidthLimit() : 25;
            if (width.getMax() != max) width.setMax(max);
        }
        if (status != null) status.setText("遮罩：" + (Config.enabled(this) ? "已启用" : "已关闭")
            + "  |  无障碍：" + (MaskAccessibility.instance != null ? "已连接" : "未连接")
            + "  |  悬浮窗：" + (Settings.canDrawOverlays(this) ? "已授权" : "未授权"));
        RootSyncService session = RootSyncService.instance;
        String extra = "";
        if (session != null && session.active) extra = session.pending
            ? "\n请确认显示及触摸正常；" + Math.max(0, session.previewDeadline - SystemClock.elapsedRealtime() / 1000) + " 秒后自动恢复"
            : "\n已确认；服务停止或心跳丢失时恢复原始显示";
        String restriction = Config.rootRestriction(this);
        if (rootStatus != null) rootStatus.setText((restriction.isEmpty() ? RootController.message
            : restriction + (RootController.recovering || RootController.hasBackup(this) ? "\n" + RootController.message : "")) + extra);
        if (keepButton != null) {
            boolean show = session != null && session.pending && !session.stopping;
            boolean canConfirm = session != null && session.canConfirm();
            keepButton.setVisibility(show ? View.VISIBLE : View.GONE); keepButton.setEnabled(canConfirm);
            keepButton.setText(canConfirm ? "检查手机画面并确认 · "
                + Math.max(0, session.previewDeadline - SystemClock.elapsedRealtime() / 1000) + " 秒" : "正在应用分辨率…");
        }
        if (stretchButton != null) stretchButton.setVisibility(session != null && session.active && !session.stopping ? View.VISIBLE : View.GONE);
        if (rootSwitch != null) rootSwitch.setEnabled(restriction.isEmpty());
        if (rootSwitch != null && rootSwitch.isChecked() != Config.rootWanted(this)) {
            updatingSwitch = true; rootSwitch.setChecked(Config.rootWanted(this)); updatingSwitch = false;
        }
    }
    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c); update(); preview.invalidate(); screen.requestApplyInsets();
    }
    @Override protected void onResume() {
        super.onResume(); darkSystemIcons(); ui.removeCallbacks(refreshStatus); ui.post(refreshStatus);
        if (screen != null) screen.requestApplyInsets();
    }
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
    final class RatioPattern extends View {
        final Paint paint = new Paint(3);
        RatioPattern(Context c) { super(c); }
        @Override protected void onDraw(Canvas canvas) {
            float size = Math.min(getHeight() - dp(20), getWidth() / 2f - dp(20));
            if (size <= 0) return;
            float x = getWidth() / 4f, y = getHeight() / 2f, r = size / 2;
            paint.setColor(Color.rgb(0, 137, 123)); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(3));
            canvas.drawCircle(x, y, r, paint);
            x = getWidth() * 3 / 4f;
            canvas.drawRect(x - r, y - r, x + r, y + r, paint);
        }
    }
}
