package com.edges.mask;

import java.util.Arrays;
import static com.edges.mask.SafetyChecks.*;

public final class CompositorChecks {
    static String space(String name,int[] bounds,int[] content,int rotation) {
        return "   "+name+"=ProjectionSpace{bounds="+Arrays.toString(bounds)+", content="+Arrays.toString(content)
            +", orientation=ROTATION_"+(rotation*90)+"}\n";
    }
    static String fixture(String id,int rotation,int[] layer,int[] output,int[] outputBounds,int[] frameBounds,int[] frame) {
        return "Display "+id+" (physical, \"Built-in Screen\")\n   Composition Display State:\n"
            +space("layerStackSpace",new int[]{0,0,layer[2],layer[3]},layer,0)
            +space("framebufferSpace",frameBounds,frame,rotation)
            +space("orientedDisplaySpace",outputBounds,output,0)
            +space("displaySpace",frameBounds,frame,rotation);
    }
    public static void main(String[] args) {
        int[] nativeSize={0,0,1440,3200}, narrow={0,0,1354,3200}, centered={43,0,1397,3200};
        check(Arrays.equals(CompositorGeometry.centered(1440,3200,1354,3200,0,1440,3200),centered),
            "reported Xiaomi example has exactly 43 pixels on both sides");
        check(Arrays.equals(CompositorGeometry.centered(1440,3200,1296,3200,0,1440,3200),new int[]{72,0,1368,3200}),
            "five percent is centered, with no horizontal expansion");
        int[] fhd=CompositorGeometry.centered(1440,3200,1354,3200,0,1080,2400);
        check(fhd[0]==32 && fhd[2]==1048,"adapt to actual 1080 output rather than assume wm physical size");
        check(Math.abs((fhd[2]-fhd[0])/1354.0-2400.0/3200)<.001,"rounding preserves isotropic scale");
        for(int r=0;r<4;r++) {
            int tw=r%2==0?1354:1440,th=r%2==0?3200:3008;
            int[] out=CompositorGeometry.centered(1440,3200,tw,th,r,r%2==0?1440:3200,r%2==0?3200:1440);
            check(out[0]==(r%2==0?43:96) && out[1]==0,"all rotations shrink the user's horizontal axis");
        }
        rejects(()->CompositorGeometry.centered(1440,3200,1600,3200,0,1440,3200),"refuse enlarged logical size");
        rejects(()->CompositorGeometry.centered(1440,3200,1000,3200,0,1440,3200),"refuse excessive shrink");
        rejects(()->CompositorGeometry.centered(1440,3200,1354,3100,0,1440,3200),"do not shrink both axes");
        rejects(()->CompositorGeometry.centered(1440,3200,1354,3200,0,1354,3200),"catch a narrowed output being stretched by the panel");
        rejects(()->CompositorGeometry.centered(1440,3200,1354,3200,4,1440,3200),"refuse unknown rotation");
        String good=fixture("4619827259835644672",0,narrow,centered,nativeSize,nativeSize,centered);
        CompositorGeometry g=CompositorGeometry.parse(good,"4619827259835644672");
        g.require(narrow,centered,0);check(true,"parse compositor output independent from input viewport");
        String full=fixture("4619827259835644672",0,narrow,nativeSize,nativeSize,nativeSize,nativeSize);
        rejects(()->CompositorGeometry.parse(full,"4619827259835644672").require(narrow,centered,0),
            "reject SF full-screen expansion even if WM/input claimed centered");
        String wrongFb=fixture("4619827259835644672",0,narrow,centered,nativeSize,narrow,narrow);
        rejects(()->CompositorGeometry.parse(wrongFb,"4619827259835644672").require(narrow,centered,0),
            "reject narrowed framebuffer even when oriented output claims centered");
        String second=fixture("99",0,narrow,nativeSize,nativeSize,nativeSize,nativeSize);
        CompositorGeometry.parse(second+good,"4619827259835644672").require(narrow,centered,0);
        check(true,"select exact native display id, never another display");
        rejects(()->CompositorGeometry.parse(good+good,"4619827259835644672"),"reject duplicate display blocks");
        rejects(()->CompositorGeometry.parse(good.replace("physical,","virtual,"),"4619827259835644672"),"refuse virtual display");
        rejects(()->CompositorGeometry.parse(good.replace("framebufferSpace=","unknownSpace="),"4619827259835644672"),"refuse incomplete readback");
        String androidRect=good.replace("[0, 0, 1354, 3200]","Rect(0, 0 - 1354, 3200)");
        CompositorGeometry.parse(androidRect,"4619827259835644672").require(narrow,centered,0);check(true,"support Rect rendering");
        String landscape=fixture("42",1,new int[]{0,0,3008,1440},new int[]{96,0,3104,1440},
            new int[]{0,0,3200,1440},nativeSize,new int[]{0,96,1440,3104});
        CompositorGeometry.parse(landscape,"42").require(new int[]{0,0,3008,1440},new int[]{96,0,3104,1440},1);
        check(true,"handle rotated full framebuffer separately from oriented content");
        // Coordinate rows supplied by the Xiaomi / Android 16 diagnostic. The display ID
        // and header below are synthetic; no device identifiers or build fingerprint are stored.
        // Preserve the real trailing spaces: stripping them from a fixture hides the 1.4 bug.
        String reported="Display 42 (physical, \"Built-in Screen\")\n"
            +"   layerStackSpace=ProjectionSpace{bounds=Rect(0, 0, 1440, 3200), content=Rect(0, 0, 1440, 3200), orientation=ROTATION_0} \n"
            +"   framebufferSpace=ProjectionSpace{bounds=Rect(0, 0, 1440, 3200), content=Rect(0, 0, 1440, 3200), orientation=ROTATION_0} \n"
            +"   orientedDisplaySpace=ProjectionSpace{bounds=Rect(0, 0, 1440, 3200), content=Rect(0, 0, 1440, 3200), orientation=ROTATION_0} \n"
            +"   displaySpace=ProjectionSpace{bounds=Rect(0, 0, 1440, 3200), content=Rect(0, 0, 1440, 3200), orientation=ROTATION_0} \n";
        CompositorGeometry.parse(reported,"42").require(nativeSize,nativeSize,0);
        check(true,"accept actual comma-separated Rect values with trailing spaces");
        String whitespace=reported.replace("="," \t=\t ").replace("{","{\t ").replace("}"," \t}")
            .replace("\n","\t \r\n");
        CompositorGeometry.parse(whitespace,"42").require(nativeSize,nativeSize,0);
        check(true,"tolerate field whitespace, tabs and CRLF without changing coordinates");
        String narrowReported=reported.replace("layerStackSpace=ProjectionSpace{bounds=Rect(0, 0, 1440, 3200), content=Rect(0, 0, 1440, 3200)",
            "layerStackSpace=ProjectionSpace{bounds=Rect(0, 0, 1354, 3200), content=Rect(0, 0, 1354, 3200)");
        rejects(()->CompositorGeometry.parse(narrowReported,"42").require(narrow,centered,0),
            "whitespace tolerance must not accept actual full-screen stretching");
        String centeredReported=narrowReported.replace("content=Rect(0, 0, 1440, 3200)","content=Rect(43, 0, 1397, 3200)");
        CompositorGeometry.parse(centeredReported,"42").require(narrow,centered,0);
        check(true,"accept centered post-apply readback in the same reported format");
        rejects(()->CompositorGeometry.parse(reported.replace("ROTATION_0","ROTATION_45"),"42"),
            "continue rejecting unsupported orientation values");
        rejects(()->CompositorGeometry.parse(reported.replace("} ","} unexpected "),"42"),
            "only trailing whitespace may be ignored, not unknown content");
        rejects(()->CompositorGeometry.parse(whitespace+whitespace,"42"),
            "whitespace must not hide ambiguous display records");
        rejects(()->CompositorGeometry.parse(reported.replace("1440, 3200","-1440, 3200"),"42"),
            "continue rejecting invalid rectangles in reported format");
        System.out.println("PASS: "+checks+" compositor geometry regression checks");
    }
}
