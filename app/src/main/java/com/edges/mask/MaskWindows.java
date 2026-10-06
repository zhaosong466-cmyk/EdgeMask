package com.edges.mask;
import android.content.*;import android.graphics.*;import android.os.Build;import android.view.*;import java.util.*;
final class MaskWindows {
 final Context c; final WindowManager wm; final boolean accessibility; final ArrayList<View> views=new ArrayList<>(); String error="";
 MaskWindows(Context c,boolean a){this.c=c;accessibility=a;wm=(WindowManager)c.getSystemService(Context.WINDOW_SERVICE);}
 void clear(){for(View v:views){try{wm.removeViewImmediate(v);}catch(RuntimeException ignored){}}views.clear();}
 void show(){clear();error="";android.util.DisplayMetrics m=new android.util.DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(m);
 int w=m.widthPixels,h=m.heightPixels; int b=Math.round(w*Config.prefs(c).getInt("width",4)/100f);
 int r=Math.min(Math.round(Config.prefs(c).getInt("radius",32)*m.density),Math.min((w-2*b)/2,h/2));
 try{if(b>0){add(new Part(c,-1),b,h,Gravity.TOP|Gravity.LEFT,0);add(new Part(c,-1),b,h,Gravity.TOP|Gravity.RIGHT,0);}
 if(r>0){add(new Part(c,0),r,r,Gravity.TOP|Gravity.LEFT,b);add(new Part(c,1),r,r,Gravity.TOP|Gravity.RIGHT,b);add(new Part(c,2),r,r,Gravity.BOTTOM|Gravity.LEFT,b);add(new Part(c,3),r,r,Gravity.BOTTOM|Gravity.RIGHT,b);}}
 catch(RuntimeException e){error=e.getClass().getSimpleName();Config.prefs(c).edit().putBoolean("enabled",false).apply();clear();android.widget.Toast.makeText(c,"遮罩启动失败，请检查权限："+error,android.widget.Toast.LENGTH_LONG).show();}}
 void add(View v,int w,int h,int gravity,int x){int flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
 if(accessibility)flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
 WindowManager.LayoutParams lp=new WindowManager.LayoutParams(w,h,accessibility?WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY:WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,flags,PixelFormat.TRANSLUCENT);
 lp.gravity=gravity;lp.x=x;lp.y=0;if(Build.VERSION.SDK_INT>=28)lp.layoutInDisplayCutoutMode=Build.VERSION.SDK_INT>=30?WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS:WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
 if(Build.VERSION.SDK_INT>=30)lp.setFitInsetsTypes(0);v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);wm.addView(v,lp);views.add(v);}
 static class Part extends View {final int corner;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);Part(Context c,int k){super(c);corner=k;paint.setColor(Color.BLACK);}
 protected void onDraw(Canvas canvas){if(corner<0){canvas.drawColor(Color.BLACK);return;}float r=getWidth();Path mask=new Path();mask.setFillType(Path.FillType.EVEN_ODD);mask.addRect(0,0,getWidth(),getHeight(),Path.Direction.CW);float cx=(corner==0||corner==2)?r:0,cy=(corner==0||corner==1)?r:0;mask.addCircle(cx,cy,r,Path.Direction.CW);canvas.drawPath(mask,paint);}
 public boolean onTouchEvent(android.view.MotionEvent e){return true;}}
}