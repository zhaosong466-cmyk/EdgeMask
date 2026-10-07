package com.edges.mask;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.*;

/** User-requested, bounded copies of system display components. Never modifies display state. */
final class RomEvidence {
    static final long FILE_LIMIT=16L*1024*1024, TOTAL_LIMIT=28L*1024*1024;
    static final String CANDIDATES="/system/lib64/libgui.so /system/lib64/libandroid_runtime.so "
        +"/system/lib64/*surfaceflinger*.so /system/lib64/*SurfaceFlinger*.so "
        +"/system_ext/lib64/*surfaceflinger*.so /system_ext/lib64/*SurfaceFlinger*.so "
        +"/vendor/lib64/*surfaceflinger*.so /vendor/lib64/*SurfaceFlinger*.so "
        +"/system/bin/surfaceflinger /system/framework/framework.jar";
    interface Source {
        String inventory() throws Exception;
        void copy(Component component, File destination) throws Exception;
    }
    static final class Component {
        final String path,real; final long size;
        Component(String p,String r,long n) { path=p; real=r; size=n; }
    }
    static boolean allowedPath(String path) {
        return "/system/bin/surfaceflinger".equals(path) || "/system/framework/framework.jar".equals(path)
            || "/system/lib64/libgui.so".equals(path) || "/system/lib64/libandroid_runtime.so".equals(path)
            || path.matches("/(?:system|system_ext|vendor)/lib64/[A-Za-z0-9_.-]*(?:surfaceflinger|SurfaceFlinger)[A-Za-z0-9_.-]*\\.so");
    }
    static boolean allowedReal(String path) {
        // Allow canonical system/APEX locations, never links resolving to /data or /sdcard.
        if(path.contains("..") || !path.matches("/[A-Za-z0-9_./@+-]+")) return false;
        return path.startsWith("/system/") || path.startsWith("/system_ext/")
            || path.startsWith("/vendor/") || path.startsWith("/apex/");
    }
    static List<Component> parse(String inventory) throws IOException {
        List<Component> result=new ArrayList<>(); Set<String> seen=new HashSet<>();
        for(String line:inventory.split("\\r?\\n")) {
            if(line.isEmpty()) continue;
            String[] fields=line.split("\\t",-1);
            if(fields.length!=3 || !allowedPath(fields[0]) || !allowedReal(fields[1])
                    || !fields[2].matches("[1-9][0-9]{0,11}"))
                throw new IOException("系统组件清单包含范围外路径或无效尺寸");
            if(seen.add(fields[0])) result.add(new Component(fields[0],fields[1],Long.parseLong(fields[2])));
            if(result.size()>40) throw new IOException("显示组件数量异常");
        }
        return result;
    }
    static Source rootSource() {
        return new Source() {
            public String inventory() throws Exception {
                if(!"0".equals(RootShell.run("id -u",45).checked().trim())) throw new IOException("未取得 Root 权限");
                return RootShell.run("for p in "+CANDIDATES+"; do [ -f \"$p\" ] || continue; "
                    +"r=$(/system/bin/readlink -f \"$p\") || continue; "
                    +"case \"$r\" in /system/*|/system_ext/*|/vendor/*|/apex/*) ;; *) continue;; esac; "
                    +"n=$(/system/bin/stat -c %s \"$p\") || continue; "
                    +"printf '%s\\t%s\\t%s\\n' \"$p\" \"$r\" \"$n\"; done",15).checked();
            }
            public void copy(Component c,File destination) throws Exception { rootCopy(c,destination); }
        };
    }
    private static void rootCopy(Component c,File destination) throws Exception {
        if(!allowedPath(c.path) || !allowedReal(c.real) || c.size>FILE_LIMIT) throw new IOException("读取范围无效");
        String p=RootShell.quote(c.path),r=RootShell.quote(c.real);
        // Recheck the canonical target immediately before reading. All writes are app-private.
        String command="[ \"$(/system/bin/readlink -f "+p+")\" = "+r+" ] && "
            +"[ \"$(/system/bin/stat -c %s "+r+")\" = "+c.size+" ] && exec /system/bin/timeout 12 /system/bin/cat "+r;
        Process process=new ProcessBuilder("su","-c",command).start();
        AtomicReference<Exception> error=new AtomicReference<>();
        Thread reader=new Thread(() -> {
            try(InputStream in=process.getInputStream();OutputStream out=new FileOutputStream(destination)) {
                copyBounded(in,out,c.size);
            } catch(Exception e) { error.set(e); }
        },"display-component-read");
        Thread stderr=new Thread(() -> {
            // Drain errors to avoid blocking; do not export root-manager messages or logcat.
            try(InputStream in=process.getErrorStream()) { byte[] b=new byte[1024]; while(in.read(b)!=-1){} }
            catch(IOException ignored){}
        },"display-component-errors");
        reader.setDaemon(true); stderr.setDaemon(true); reader.start(); stderr.start();
        try {
            if(!process.waitFor(16,TimeUnit.SECONDS)) throw new IOException("系统组件读取超时");
            reader.join(1000);
            if(reader.isAlive() || process.exitValue()!=0 || error.get()!=null)
                throw new IOException("系统组件读取失败或不完整");
        } finally {
            process.destroyForcibly(); process.getInputStream().close(); process.getErrorStream().close();
            process.getOutputStream().close(); reader.join(1000);
        }
    }
    static void copyBounded(InputStream in,OutputStream out,long expected) throws IOException {
        if(expected<1 || expected>FILE_LIMIT) throw new IOException("组件尺寸超出限制");
        byte[] b=new byte[8192]; long size=0; int n;
        while((n=in.read(b))!=-1) {
            size+=n; if(size>expected) throw new IOException("组件尺寸在读取中改变");
            out.write(b,0,n);
        }
        if(size!=expected) throw new IOException("系统组件内容不完整");
    }
    static String hash(File f) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(f)) { byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)digest.update(b,0,n); }
        StringBuilder s=new StringBuilder();for(byte b:digest.digest())s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();
    }
    static void validateBinary(File f,Component c) throws IOException {
        if(f.length()!=c.size) throw new IOException("复制后的尺寸不符");
        byte[] magic=new byte[4];try(DataInputStream in=new DataInputStream(new FileInputStream(f))) { in.readFully(magic); }
        boolean valid=c.path.endsWith(".jar")
            ? magic[0]=='P' && magic[1]=='K' && magic[2]==3 && magic[3]==4
            : magic[0]==127 && magic[1]=='E' && magic[2]=='L' && magic[3]=='F';
        if(!valid) throw new IOException("组件格式不符，未加入导出包");
    }
    static File create(File cache,String diagnostic,Source source) throws Exception {
        File folder=Files.createTempDirectory(cache.toPath(),"rom-evidence-").toFile();
        File archive=new File(folder,"EdgeMask-ROM-evidence.zip");
        List<Component> components;
        try { components=parse(source.inventory()); }
        catch(Exception e) { folder.delete();throw e; }
        StringBuilder manifest=new StringBuilder("EdgeMask ROM evidence (user-requested, read-only)\n"
            +"Contains selected system display binaries, file hashes and display diagnostics.\n"
            +"No screenshots, logcat, app-private data, accounts or automatic upload.\n"
            +"A missing/oversized component is recorded; this is not a full ROM dump.\n\n");
        long total=0;int included=0;
        try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(archive))) {
            for(Component c:components) {
                if(c.size>FILE_LIMIT || c.size>TOTAL_LIMIT-total) {
                    manifest.append("SKIPPED size-limit ").append(c.path).append(" bytes=").append(c.size).append('\n');continue;
                }
                File temp=new File(folder,"component.tmp");
                try {
                    source.copy(c,temp);validateBinary(temp,c);
                } catch(Exception e) {
                    temp.delete();manifest.append("SKIPPED unreadable-or-invalid ").append(c.path).append('\n');continue;
                }
                try {
                    String sha=hash(temp);
                    zip.putNextEntry(new ZipEntry("components"+c.path));
                    try(InputStream in=new FileInputStream(temp)) { copyBounded(in,zip,c.size); }
                    zip.closeEntry();total+=c.size;included++;
                    manifest.append("INCLUDED ").append(c.path).append(" bytes=").append(c.size).append(" sha256=").append(sha).append('\n');
                } finally { temp.delete(); }
            }
            if(included==0) throw new IOException("没有取得可分析的显示组件，请检查 Root 授权");
            manifest.append("\nIncluded: ").append(included).append("; uncompressed bytes: ").append(total).append('\n');
            entry(zip,"manifest.txt",manifest.toString());entry(zip,"display-diagnostic.txt",diagnostic);
        } catch(Exception e) { archive.delete();folder.delete();throw e; }
        if(archive.length()>30L*1024*1024) { archive.delete();folder.delete();throw new IOException("导出包超出大小限制"); }
        return archive;
    }
    private static void entry(ZipOutputStream zip,String name,String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));zip.write(content.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }
}
