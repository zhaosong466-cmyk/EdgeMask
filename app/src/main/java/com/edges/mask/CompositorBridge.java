package com.edges.mask;

import android.graphics.Rect;
import android.os.IBinder;
import android.os.Parcel;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Invoked only by the root watchdog through app_process. No system files or display modes change. */
public final class CompositorBridge {
    private final Class<?> surface=Class.forName("android.view.SurfaceControl");
    private final Class<?> transaction=Class.forName("android.view.SurfaceControl$Transaction");
    private final Class<?> displays;
    private final IBinder compositor;
    private final File dir;

    private CompositorBridge(File folder) throws Exception {
        dir=folder;
        if(android.os.Process.myUid()!=0) throw new IOException("合成层修正需要 Root");
        Class<?> dc;
        try { surface.getMethod("getPhysicalDisplayIds"); dc=surface; }
        catch(NoSuchMethodException newerAndroid) {
            String cp=System.getenv("SYSTEMSERVERCLASSPATH");
            if(cp==null || cp.isEmpty()) cp="/system/framework/services.jar";
            Class<?> factory=Class.forName("com.android.internal.os.ClassLoaderFactory");
            Method create=factory.getDeclaredMethod("createClassLoader",String.class,String.class,String.class,
                ClassLoader.class,int.class,boolean.class,String.class);
            ClassLoader loader=(ClassLoader)create.invoke(null,cp,null,null,ClassLoader.getSystemClassLoader(),0,true,null);
            dc=Class.forName("com.android.server.display.DisplayControl",true,loader);
            Method lib=Runtime.class.getDeclaredMethod("loadLibrary0",Class.class,String.class);
            lib.setAccessible(true); lib.invoke(Runtime.getRuntime(),dc,"android_servers");
        }
        displays=dc;
        compositor=(IBinder)Class.forName("android.os.ServiceManager").getDeclaredMethod("getService",String.class)
            .invoke(null,"SurfaceFlinger");
        if(compositor==null || !"android.ui.ISurfaceComposer".equals(compositor.getInterfaceDescriptor()))
            throw new IOException("SurfaceFlinger 接口无法识别");
        // Resolve every mutation API before any display modification.
        transaction.getMethod("setDisplayProjection",IBinder.class,int.class,Rect.class,Rect.class);
        transaction.getMethod("apply",boolean.class);
        transaction.getMethod("close");
    }
    public static void main(String[] args) {
        try {
            if(args.length<2) throw new IOException("缺少参数");
            CompositorBridge bridge=new CompositorBridge(new File(args[1]));
            if("prepare".equals(args[0]) && args.length==6)
                bridge.prepare(Integer.parseInt(args[2]),Integer.parseInt(args[3]),Integer.parseInt(args[4]),"1".equals(args[5]));
            else if(("apply".equals(args[0]) || "check".equals(args[0])) && args.length==4)
                bridge.target(Integer.parseInt(args[2]),Integer.parseInt(args[3]),"apply".equals(args[0]));
            else if("restore".equals(args[0]) && args.length==2) bridge.restore();
            else throw new IOException("合成层命令无效");
            System.exit(0);
        } catch(Throwable error) {
            Throwable e=error;
            while(e instanceof InvocationTargetException && ((InvocationTargetException)e).getCause()!=null)
                e=((InvocationTargetException)e).getCause();
            // No raw dumps, layer names, or content in logs.
            System.err.println("合成层修正失败："+e.getClass().getSimpleName()+": "+e.getMessage());
            System.exit(1);
        }
    }
    private IBinder token(long id) throws Exception {
        IBinder result=(IBinder)displays.getMethod("getPhysicalDisplayToken",long.class).invoke(null,id);
        if(result==null) throw new IOException("原始屏幕已断开"); return result;
    }
    private long internalDisplay() throws Exception {
        long[] ids=(long[])displays.getMethod("getPhysicalDisplayIds").invoke(null);
        Long selected=null;
        for(long id:ids) {
            Object info=surface.getMethod("getStaticDisplayInfo",long.class).invoke(null,id);
            if(info!=null && info.getClass().getField("isInternal").getBoolean(info)) {
                if(selected!=null) throw new IOException("当前有多个内置屏幕，无法唯一选择");
                if(info.getClass().getField("installOrientation").getInt(info)!=0)
                    throw new IOException("屏幕安装方向需要额外适配");
                selected=id;
            }
        }
        if(selected==null) throw new IOException("未找到内置屏幕"); return selected;
    }
    private CompositorGeometry geometry(long id) throws Exception {
        return CompositorGeometry.parse(run("/system/bin/dumpsys","SurfaceFlinger","--comp-displays"),Long.toUnsignedString(id));
    }
    private void project(long id,int rotation,int[] logical,int[] output) throws Exception {
        Object t=transaction.getConstructor().newInstance();
        try {
            transaction.getMethod("setDisplayProjection",IBinder.class,int.class,Rect.class,Rect.class)
                .invoke(t,token(id),rotation,rect(logical),rect(output));
            transaction.getMethod("apply",boolean.class).invoke(t,true);
        } finally { transaction.getMethod("close").invoke(t); }
    }
    private static Rect rect(int[] r) { return new Rect(r[0],r[1],r[2],r[3]); }
    private int gpu() throws Exception {
        // AOSP Android 16 developer-option protocol: 1010 returns exactly five int32s.
        // Check the descriptor AND reply layout; never guess a vendor transaction or default state.
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try {
            data.writeInterfaceToken("android.ui.ISurfaceComposer");
            if(!compositor.transact(1010,data,reply,0) || reply.dataSize()!=20)
                throw new IOException("无法备份 GPU 合成状态");
            int a=reply.readInt(),b=reply.readInt(),flash=reply.readInt(),d=reply.readInt(),value=reply.readInt();
            if(a!=0 || b!=0 || d!=0 || flash<0 || value<0 || value>1)
                throw new IOException("GPU 合成查询格式不兼容");
            return value;
        } finally { data.recycle(); reply.recycle(); }
    }
    private void gpu(int value) throws Exception {
        if(value!=0 && value!=1) throw new IOException("GPU 状态无效");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try {
            data.writeInterfaceToken("android.ui.ISurfaceComposer"); data.writeInt(value);
            if(!compositor.transact(1008,data,reply,0)) throw new IOException("ROM 拒绝 GPU 合成设置");
        } finally { data.recycle(); reply.recycle(); }
        for(int i=0;i<5;i++) { if(gpu()==value) return; Thread.sleep(60); }
        throw new IOException("GPU 合成设置未生效");
    }
    private void prepare(int w,int h,int rotation,boolean forceGpu) throws Exception {
        if(new File(dir,"compositor.properties").exists()) throw new IOException("合成层原始备份已存在");
        long id=internalDisplay(); token(id);
        CompositorGeometry g=geometry(id);
        int[] logical=CompositorGeometry.logical(w,h,rotation);
        int[] full=CompositorGeometry.centered(w,h,w,h,rotation,g.outputBounds[2],g.outputBounds[3]);
        g.require(logical,full,rotation);
        Properties p=new Properties();
        p.setProperty("id",Long.toString(id)); p.setProperty("width",""+w); p.setProperty("height",""+h);
        p.setProperty("rotation",""+rotation); p.setProperty("gpu",forceGpu?""+gpu():"-1");
        p.setProperty("output",Arrays.toString(g.output)); p.setProperty("boot",boot());
        writeProperties(p);
        note("before",g,forceGpu?gpu():-1);
        System.out.println("合成层原始状态已备份");
    }
    private void target(int w,int h,boolean apply) throws Exception {
        Properties p=load(); long id=Long.parseLong(p.getProperty("id"));
        if(!boot().equals(p.getProperty("boot"))) throw new IOException("系统已重启，旧会话不能继续");
        int ow=number(p,"width"),oh=number(p,"height"),r=number(p,"rotation");
        DisplayProjection input=DisplayProjection.parse(run("/system/bin/dumpsys","input"));
        for(int i=0;apply && i<5 && input.rotation==r && !Arrays.equals(input.logical,CompositorGeometry.logical(w,h,r));i++) {
            Thread.sleep(100); input=DisplayProjection.parse(run("/system/bin/dumpsys","input"));
        }
        if(input.rotation!=r || !Arrays.equals(input.logical,CompositorGeometry.logical(w,h,r)))
            throw new IOException("输入坐标正在变化");
        CompositorGeometry before=geometry(id);
        if(before.rotation!=r) throw new IOException("屏幕方向已改变");
        int[] dest=CompositorGeometry.centered(ow,oh,w,h,r,before.outputBounds[2],before.outputBounds[3]);
        int[] source=CompositorGeometry.logical(w,h,r);
        int savedGpu=number(p,"gpu");
        if(apply) {
            note("requested-before",before,savedGpu>=0?gpu():-1);
            if(savedGpu>=0) gpu(1);
            project(id,r,source,dest);
        }
        Exception last=null;
        for(int i=0;i<(apply?5:1);i++) {
            try {
                CompositorGeometry after=geometry(id); after.require(source,dest,r);
                if(savedGpu>=0 && gpu()!=1) throw new IOException("GPU 合成被系统关闭");
                if(apply) note("applied",after,savedGpu>=0?1:-1); return;
            } catch(Exception e) { last=e; if(apply) Thread.sleep(100); }
        }
        throw last;
    }
    private void restore() throws Exception {
        File saved=new File(dir,"compositor.properties"); if(!saved.exists()) return;
        Properties p=load();
        // SurfaceFlinger's projection and debug flags are transient. Never replay an old
        // boot's GPU flag or token onto a newly booted compositor.
        if(!boot().equals(p.getProperty("boot"))) return;
        Exception error=null;
        int oldGpu=number(p,"gpu");
        try { if(oldGpu>=0) gpu(oldGpu); } catch(Exception e) { error=e; }
        try {
            int w=number(p,"width"),h=number(p,"height"); long id=Long.parseLong(p.getProperty("id"));
            DisplayProjection input=DisplayProjection.parse(run("/system/bin/dumpsys","input"));
            int r=input.rotation;
            if(!Arrays.equals(input.logical,CompositorGeometry.logical(w,h,r)))
                throw new IOException("系统尺寸尚未恢复，保留合成层恢复记录");
            CompositorGeometry current=geometry(id);
            // Rotation may have triggered recovery. Use CURRENT orientation and output size,
            // not the old portrait rectangle, when restoring a now-landscape screen.
            int[] full=CompositorGeometry.centered(w,h,w,h,r,
                r%2==current.rotation%2?current.outputBounds[2]:current.outputBounds[3],
                r%2==current.rotation%2?current.outputBounds[3]:current.outputBounds[2]);
            project(id,r,CompositorGeometry.logical(w,h,r),full);
            geometry(id).require(CompositorGeometry.logical(w,h,r),full,r);
        } catch(Exception e) { if(error==null) error=e; }
        if(error!=null) throw error;
        System.out.println("原始合成层投影与 GPU 设置已恢复");
    }
    private static int number(Properties p,String name) { return Integer.parseInt(p.getProperty(name)); }
    private static String boot() throws IOException { return new String(Files.readAllBytes(Paths.get("/proc/sys/kernel/random/boot_id")),StandardCharsets.UTF_8).trim(); }
    private Properties load() throws IOException {
        Properties p=new Properties(); try(InputStream in=new FileInputStream(new File(dir,"compositor.properties"))) { p.load(in); } return p;
    }
    private void writeProperties(Properties p) throws IOException {
        File tmp=new File(dir,"compositor.tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)) { p.store(out,"Original compositor state"); out.getFD().sync(); }
        if(!tmp.setReadable(true,false)) throw new IOException("无法设置恢复记录读取权限");
        Files.move(tmp.toPath(),new File(dir,"compositor.properties").toPath(),StandardCopyOption.ATOMIC_MOVE);
    }
    private void note(String stage,CompositorGeometry g,int gpu) throws IOException {
        // Numeric evidence survives timeout recovery. App-private file; never uploaded automatically.
        File report=new File(dir,"compositor-report.txt");
        try(FileOutputStream out=new FileOutputStream(report,report.length()<32768)) {
            out.write((stage+": "+g.numericSummary()+"; GPU="+gpu+"\n").getBytes(StandardCharsets.UTF_8));
        }
    }
    private static String run(String... command) throws Exception {
        Process process=new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        Thread reader=new Thread(() -> {
            try(InputStream in=process.getInputStream()) { byte[] b=new byte[4096]; int n;
                while((n=in.read(b))!=-1) synchronized(bytes) { if(bytes.size()+n<=1048576) bytes.write(b,0,n); }
            } catch(IOException ignored) { }
        });
        reader.setDaemon(true); reader.start();
        if(!process.waitFor(4,TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IOException("合成层读取超时"); }
        reader.join(500);
        if(process.exitValue()!=0) throw new IOException("系统显示接口读取失败");
        synchronized(bytes) { return bytes.toString("UTF-8"); }
    }
}
