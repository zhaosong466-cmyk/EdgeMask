package com.edges.mask;
import android.accessibilityservice.AccessibilityService;import android.view.accessibility.AccessibilityEvent;import android.content.res.Configuration;
public class MaskAccessibility extends AccessibilityService {
 static MaskAccessibility instance;MaskWindows mask;
 protected void onServiceConnected(){instance=this;mask=new MaskWindows(this,true);refresh();}
 void refresh(){if(mask==null)return;if(Config.enabled(this)&&Config.access(this))mask.show();else mask.clear();}
 public void onAccessibilityEvent(AccessibilityEvent e){}public void onInterrupt(){}
 public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);refresh();}
 public void onDestroy(){if(mask!=null)mask.clear();instance=null;super.onDestroy();}
}