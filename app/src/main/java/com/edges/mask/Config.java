package com.edges.mask;
import android.content.*;
final class Config {
 static SharedPreferences prefs(Context c){return c.getSharedPreferences("mask",0);}
 static boolean enabled(Context c){return prefs(c).getBoolean("enabled",false);}
 static boolean access(Context c){return prefs(c).getBoolean("access",true);}
 static void refresh(Context c){if(MaskAccessibility.instance!=null)MaskAccessibility.instance.refresh(); if(OverlayService.instance!=null)OverlayService.instance.refresh();}
 static void stop(Context c){prefs(c).edit().putBoolean("enabled",false).apply();refresh(c);c.stopService(new Intent(c,OverlayService.class));}
}