package com.edges.mask;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class RecoveryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction())
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        if (!RootController.hasBackup(context)) return;
        PendingResult pending = goAsync();
        new Thread(() -> {
            try { RootController.restore(context); RootController.message = "重启/更新后已恢复原始显示"; }
            catch (Exception e) { RootController.message = "自动恢复未确认，请打开应用点击恢复：" + e.getMessage(); }
            finally { pending.finish(); }
        }, "display-boot-recovery").start();
    }
}
