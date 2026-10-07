package com.edges.mask;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class RomEvidenceChecks {
    static int checks;
    static void check(boolean condition,String message) { checks++;if(!condition)throw new AssertionError(message); }
    interface Operation { void run() throws Exception; }
    static void rejects(Operation operation,String message) throws Exception {
        boolean rejected=false;try {operation.run();} catch(IOException expected){rejected=true;}check(rejected,message);
    }
    static byte[] ELF={127,'E','L','F',0,-1,3,0,9,1,0,-128};
    static class Fake implements RomEvidence.Source {
        String inventory;final List<String> reads=new ArrayList<>();
        Fake(String s) {inventory=s;}
        public String inventory() {return inventory;}
        public void copy(RomEvidence.Component c,File destination) throws Exception {
            reads.add(c.path);
            if(c.path.contains("broken")) {Files.write(destination.toPath(),ELF);throw new IOException("partial read");}
            if(c.path.contains("corrupt")) {Files.write(destination.toPath(),new byte[ELF.length]);return;}
            try(OutputStream out=new FileOutputStream(destination)) {
                out.write(ELF);byte[] zero=new byte[8192];long left=c.size-ELF.length;
                while(left>0){int n=(int)Math.min(left,zero.length);out.write(zero,0,n);left-=n;}
            }
        }
    }
    static String row(String path,long size) {return path+"\t"+path+"\t"+size+"\n";}
    static void delete(File file) {if(file.isDirectory())for(File child:file.listFiles())delete(child);file.delete();}
    public static void main(String[] args) throws Exception {
        for(String path:new String[]{"/system/lib64/libgui.so","/system/lib64/libandroid_runtime.so",
                "/system/lib64/libsurfaceflinger.so","/system_ext/lib64/libmisurfaceflinger.so",
                "/vendor/lib64/libMiSurfaceFlinger.so","/system/bin/surfaceflinger","/system/framework/framework.jar"})
            check(RomEvidence.allowedPath(path),"accept selected system display components");
        for(String path:new String[]{"/data/user/0/private","/sdcard/photos.jpg","/system/lib64/libaccounts.so",
                "/system/lib64/../../data/private","/system/framework/services.jar","/vendor/etc/build.prop"})
            check(!RomEvidence.allowedPath(path),"reject unrelated or private requested path");
        for(String path:new String[]{"/apex/com.android.example@36/lib64/libgui.so","/system_ext/lib64/libgui.so"})
            check(RomEvidence.allowedReal(path),"permit canonical system/APEX target");
        for(String path:new String[]{"/data/adb/module/libgui.so","/sdcard/file","/system/../data/private",
                "/system//../data/private","/system/lib64/x;cat"})
            check(!RomEvidence.allowedReal(path),"reject user-data symlinks and shell syntax");
        String good=row("/system/lib64/libgui.so",ELF.length);
        check(RomEvidence.parse(good+good).size()==1,"deduplicate glob results");
        rejects(()->RomEvidence.parse("/system/lib64/libgui.so\t/data/user/0/private\t12\n"),"reject symlink to private data");
        rejects(()->RomEvidence.parse(row("/data/private",12)),"reject private inventory row");
        rejects(()->RomEvidence.parse("/system/lib64/libgui.so\t/system/lib64/libgui.so\t-1\n"),"reject invalid size");
        rejects(()->RomEvidence.parse(good.replace("\t12","\t0")),"reject empty component");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        RomEvidence.copyBounded(new ByteArrayInputStream(ELF),bytes,ELF.length);
        check(Arrays.equals(bytes.toByteArray(),ELF),"preserve NUL and non-UTF8 binary bytes");
        rejects(()->RomEvidence.copyBounded(new ByteArrayInputStream(ELF),new ByteArrayOutputStream(),ELF.length-1),"reject overflow");
        rejects(()->RomEvidence.copyBounded(new ByteArrayInputStream(ELF),new ByteArrayOutputStream(),ELF.length+1),"reject incomplete read");
        rejects(()->RomEvidence.copyBounded(new ByteArrayInputStream(ELF),new ByteArrayOutputStream(),RomEvidence.FILE_LIMIT+1),"reject excessive expected size");
        File cache=Files.createTempDirectory("rom-export-check-").toFile();
        try {
            Fake source=new Fake(good+row("/system/lib64/libbrokensurfaceflinger.so",ELF.length)
                +row("/system/lib64/libcorruptsurfaceflinger.so",ELF.length)
                +row("/system/lib64/libhugesurfaceflinger.so",RomEvidence.FILE_LIMIT+1));
            File archive=RomEvidence.create(cache,"numeric diagnostic fixture",source);
            try(ZipFile zip=new ZipFile(archive)) {
                check(zip.size()==3,"include one valid component, manifest and diagnostic only");
                check(Arrays.equals(read(zip,"components/system/lib64/libgui.so"),ELF),"retain exact binary content in ZIP");
                String manifest=new String(read(zip,"manifest.txt"),"UTF-8");
                check(manifest.contains("SKIPPED unreadable-or-invalid /system/lib64/libbrokensurfaceflinger.so"),"record partial failure");
                check(manifest.contains("SKIPPED unreadable-or-invalid /system/lib64/libcorruptsurfaceflinger.so"),"record wrong binary format");
                check(manifest.contains("SKIPPED size-limit /system/lib64/libhugesurfaceflinger.so"),"record oversized component");
                File exact=new File(cache,"exact");Files.write(exact.toPath(),ELF);
                check(manifest.contains("sha256="+RomEvidence.hash(exact)),"provide hash of exported bytes");
                check(new String(read(zip,"display-diagnostic.txt"),"UTF-8").equals("numeric diagnostic fixture"),"include supplied numeric evidence");
            }
            check(!source.reads.contains("/system/lib64/libhugesurfaceflinger.so"),"skip oversized file before reading");
            Fake budget=new Fake(row("/system/lib64/libgui.so",RomEvidence.FILE_LIMIT)
                +row("/system/lib64/libsurfaceflinger.so",15L*1024*1024));
            File big=RomEvidence.create(cache,"fixture",budget);
            check(budget.reads.size()==1,"enforce total budget before next read");
            try(ZipFile zip=new ZipFile(big)) {
                check(zip.getEntry("components/system/lib64/libgui.so").getSize()==RomEvidence.FILE_LIMIT,"accept exact per-file upper limit");
                check(new String(read(zip,"manifest.txt"),"UTF-8").contains("SKIPPED size-limit"),"record total budget omission");
            }
            rejects(()->RomEvidence.create(cache,"fixture",new Fake(row("/system/lib64/libcorruptsurfaceflinger.so",ELF.length))),"do not report success when all files invalid");
            rejects(()->RomEvidence.create(cache,"fixture",new Fake("")),"do not create empty evidence export");
        } finally {delete(cache);}
        System.out.println("PASS: "+checks+" read-only ROM export boundary and binary integrity checks");
    }
    static byte[] read(ZipFile zip,String name) throws IOException {
        try(InputStream in=zip.getInputStream(zip.getEntry(name));ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();
        }
    }
}
