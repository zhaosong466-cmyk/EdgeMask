package com.edges.mask;
import android.app.*;import android.content.*;import android.content.res.Configuration;import android.os.*;import android.provider.Settings;
public class OverlayService extends Service {
 static OverlayService instance;MaskWindows mask;
 public void onCreate(){super.onCreate();instance=this;mask=new MaskWindows(this,false);NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("mask","黑边遮罩",NotificationManager.IMPORTANCE_LOW));
 PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
 PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,OverlayService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
 Notification n=new Notification.Builder(this,"mask").setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("对称黑边正在显示").setContentText("点此调整；或点击关闭遮罩").setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"关闭遮罩",stop).build()).build();
 if(Build.VERSION.SDK_INT>=34)startForeground(1,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(1,n);}
 public int onStartCommand(Intent i,int f,int id){if(i!=null&&"STOP".equals(i.getAction())){Config.stop(this);return START_NOT_STICKY;}refresh();return START_NOT_STICKY;}
 void refresh(){if(Config.enabled(this)&&!Config.access(this)&&Settings.canDrawOverlays(this))mask.show();else{mask.clear();stopSelf();}}
 public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);refresh();}
 public IBinder onBind(Intent i){return null;}
 public void onDestroy(){if(mask!=null)mask.clear();instance=null;super.onDestroy();}
}