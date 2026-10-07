package com.edges.mask;
import android.content.*;
final class Config {
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("mask",0);}
 static boolean enabled(Context c){return prefs(c).getBoolean("enabled",false);}
 static boolean access(Context c){return prefs(c).getBoolean("access",true);}
 static boolean rootWanted(Context c){return prefs(c).getBoolean("rootWanted",false);}
 static boolean gpuCompatibility(Context c){return prefs(c).getBoolean("gpuCompatibility",true);}
 static boolean rootApplied(){RootSyncService s=RootSyncService.instance;return s!=null&&s.active&&s.projectionVerified;}
 static void refresh(Context c){if(MaskAccessibility.instance!=null)MaskAccessibility.instance.refresh(); if(OverlayService.instance!=null)OverlayService.instance.refresh();}
 static void stop(Context c){prefs(c).edit().putBoolean("enabled",false).apply();if(RootSyncService.instance!=null)RootSyncService.instance.revert("手动关闭遮罩");refresh(c);c.stopService(new Intent(c,OverlayService.class));}
}
