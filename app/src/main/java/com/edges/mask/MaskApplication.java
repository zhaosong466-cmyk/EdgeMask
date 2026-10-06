package com.edges.mask;

import android.app.Application;
import android.os.UserManager;

public class MaskApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        if (getSystemService(UserManager.class).isUserUnlocked()) {
            boolean oldRoot = Config.rootWanted(this);
            Config.prefs(this).edit().putBoolean("rootWanted", false)
                .putBoolean("enabled", oldRoot ? false : Config.enabled(this)).apply();
        }
        if (RootController.hasBackup(this)) {
            RootController.recovering = true; RootController.message = "检测到未结束的会话，正在恢复原始显示…";
            new Thread(() -> {
                try { RootController.restore(this); RootController.message = "上次会话的原始显示已恢复；Root 需重新手动启用"; }
                catch (Exception e) { RootController.message = "恢复未确认，请点击恢复：" + e.getMessage(); }
                finally { RootController.recovering = false; }
            }, "display-startup-recovery").start();
        }
    }
}
