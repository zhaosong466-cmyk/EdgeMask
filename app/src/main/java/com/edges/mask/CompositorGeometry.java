package com.edges.mask;

import java.util.*;
import java.util.regex.*;

/** SurfaceFlinger output geometry, separate from the input service's cached viewport. */
final class CompositorGeometry {
    final int rotation;
    final int[] layer, frameBounds, frame, outputBounds, output, nativeBounds, nativeOutput;
    CompositorGeometry(int r, int[] l, int[] fb, int[] f, int[] ob, int[] o, int[] nb, int[] no) {
        rotation=r; layer=l; frameBounds=fb; frame=f; outputBounds=ob; output=o; nativeBounds=nb; nativeOutput=no;
    }
    static int[] rect(String value) {
        Matcher m=Pattern.compile("-?\\d+").matcher(value); int[] v=new int[4]; int n=0;
        while(m.find()) { if(n==4) throw new IllegalArgumentException("合成层矩形格式不明确"); v[n++]=Integer.parseInt(m.group()); }
        if(n!=4 || v[0]<0 || v[1]<0 || v[2]<=v[0] || v[3]<=v[1] || v[2]>20000 || v[3]>20000)
            throw new IllegalArgumentException("合成层矩形无效");
        return v;
    }
    static CompositorGeometry parse(String dump, String id) {
        boolean selected=false; int count=0; Map<String,String> fields=new HashMap<>();
        Pattern header=Pattern.compile("^Display ([0-9]+) \\(physical,.*$");
        Pattern field=Pattern.compile("^\\s*(layerStackSpace|framebufferSpace|orientedDisplaySpace|displaySpace)\\s*=\\s*(.*)$");
        for(String line:dump.split("\\r?\\n")) {
            if(line.startsWith("Display ")) {
                Matcher h=header.matcher(line); selected=h.matches() && h.group(1).equals(id);
                if(selected && ++count>1) throw new IllegalArgumentException("合成层主屏幕记录重复");
            }
            if(!selected) continue;
            Matcher f=field.matcher(line);
            if(f.matches() && fields.put(f.group(1),f.group(2))!=null)
                throw new IllegalArgumentException("合成层坐标字段重复");
        }
        int[][] layer=space(fields.get("layerStackSpace")), frame=space(fields.get("framebufferSpace"));
        int[][] oriented=space(fields.get("orientedDisplaySpace")), display=space(fields.get("displaySpace"));
        if(oriented[2][0]!=0 || layer[2][0]!=0 || frame[2][0]!=display[2][0])
            throw new IllegalArgumentException("合成层坐标方向不一致");
        return new CompositorGeometry(display[2][0],layer[1],frame[0],frame[1],oriented[0],oriented[1],display[0],display[1]);
    }
    private static int[][] space(String s) {
        if(s==null) throw new IllegalArgumentException("ROM 未提供合成层坐标，请复制显示诊断");
        // dumpsys may append spaces after '}'. They are formatting, not a different ROM
        // protocol. Normalize only whitespace; still require every field and valid value.
        Matcher m=Pattern.compile("ProjectionSpace\\s*\\{\\s*bounds\\s*=\\s*(.*?),\\s*content\\s*=\\s*(.*?),\\s*orientation\\s*=\\s*(?:ROTATION_|ROT_)?(0|90|180|270)\\s*\\}").matcher(s.trim());
        if(!m.matches()) throw new IllegalArgumentException("无法解析合成层坐标，请复制显示诊断");
        return new int[][]{rect(m.group(1)),rect(m.group(2)),new int[]{Integer.parseInt(m.group(3))/90}};
    }
    static int[] logical(int w,int h,int r) { return r%2==0?new int[]{0,0,w,h}:new int[]{0,0,h,w}; }
    static int[] centered(int originalW,int originalH,int targetW,int targetH,int rotation,int outputW,int outputH) {
        if(rotation<0 || rotation>3 || originalW<200 || originalH<200 || targetW<200 || targetH<200
                || targetW>originalW || targetH>originalH || (targetW!=originalW && targetH!=originalH)
                || targetW*100L<originalW*70L || targetH*100L<originalH*70L)
            throw new IllegalArgumentException("目标投影超出范围");
        int ow=rotation%2==0?originalW:originalH, oh=rotation%2==0?originalH:originalW;
        if(outputW<200 || outputH<200 || outputW>10000 || outputH>10000
                || Math.abs((double)outputW*oh/(outputH*(double)ow)-1)>.002)
            throw new IllegalArgumentException("当前屏幕输出比例与原始显示不一致");
        int tw=rotation%2==0?targetW:targetH, th=rotation%2==0?targetH:targetW;
        int w=(int)Math.round((double)tw*outputW/ow),h=(int)Math.round((double)th*outputH/oh);
        int x=(outputW-w)/2,y=(outputH-h)/2; return new int[]{x,y,x+w,y+h};
    }
    void require(int[] wantedLayer,int[] wantedOutput,int wantedRotation) {
        if(rotation!=wantedRotation || !Arrays.equals(layer,wantedLayer) || !Arrays.equals(output,wantedOutput))
            throw new IllegalArgumentException("合成层投影尚未居中或被系统改写");
        // The GPU target must keep the full display aspect, including the side borders.
        int fw=rotation%2==0?frameBounds[2]:frameBounds[3],fh=rotation%2==0?frameBounds[3]:frameBounds[2];
        if(frameBounds[0]!=0 || frameBounds[1]!=0 || outputBounds[0]!=0 || outputBounds[1]!=0
                || Math.abs((double)fw*outputBounds[3]/(fh*(double)outputBounds[2])-1)>.002)
            throw new IllegalArgumentException("帧缓冲比例被改变，不能确认防拉伸");
    }
    String numericSummary() {
        return "rotation="+rotation+"; logical="+Arrays.toString(layer)+"; output="+Arrays.toString(output)
            +"; outputBounds="+Arrays.toString(outputBounds)+"; framebuffer="+Arrays.toString(frame)
            +"; framebufferBounds="+Arrays.toString(frameBounds)+"; nativeBounds="+Arrays.toString(nativeBounds)
            +"; nativeOutput="+Arrays.toString(nativeOutput);
    }
}
