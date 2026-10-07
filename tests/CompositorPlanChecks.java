package com.edges.mask;

import java.util.Arrays;
import static com.edges.mask.SafetyChecks.*;

public final class CompositorPlanChecks {
    // Test an independent end-to-end model: GPU frame fills the original panel, as in the
    // reported physical stretch. Test snapshots are hypotheses, not unseen device captures.
    static CompositorGeometry rendered(CompositorPlan p,int frameW,int frameH) {
        boolean landscape=p.rotation%2!=0;
        int nw=landscape?p.rasterHeight:p.rasterWidth,nh=landscape?p.rasterWidth:p.rasterHeight;
        int[] out=p.destination;
        int[] nativeRect;
        switch(p.rotation) {
            case 0:nativeRect=out.clone();break;
            case 1:nativeRect=new int[]{nw-out[3],out[0],nw-out[1],out[2]};break;
            case 2:nativeRect=new int[]{nw-out[2],nh-out[3],nw-out[0],nh-out[1]};break;
            default:nativeRect=new int[]{out[1],nh-out[2],out[3],nh-out[0]};break;
        }
        int[] frame=new int[4];
        for(int i=0;i<4;i++) frame[i]=(int)Math.round(nativeRect[i]*(i%2==0?(double)frameW/nw:(double)frameH/nh));
        String dump="Display 42 (physical, \"Test\")\n"
            +CompositorChecks.space("layerStackSpace",p.source,p.source,0)
            +CompositorChecks.space("orientedDisplaySpace",new int[]{0,0,p.rasterWidth,p.rasterHeight},p.destination,0)
            +CompositorChecks.space("displaySpace",new int[]{0,0,nw,nh},nativeRect,p.rotation)
            +CompositorChecks.space("framebufferSpace",new int[]{0,0,frameW,frameH},frame,p.rotation);
        return CompositorGeometry.parse(dump,"42");
    }
    public static void main(String[] args) {
        CompositorPlan normal=new CompositorPlan(1440,3200,1354,3200,0,1440,3200,1440,3200);
        check(!normal.compensated && Arrays.equals(normal.destination,new int[]{43,0,1397,3200}),"preserve normal centered output");
        normal.require(rendered(normal,1440,3200));
        CompositorPlan narrowed=new CompositorPlan(1440,3200,1354,3200,0,1440,3200,1354,3200);
        check(narrowed.compensated && Arrays.equals(narrowed.destination,new int[]{40,0,1314,3200}),
            "compress the 1354-wide content to 1274 pixels inside a 1354-wide raster");
        double afterPanelWidth=1274.0*1440/1354;
        check(Math.abs(afterPanelWidth-1354)<1,"final panel width returns to target within one pixel after expansion");
        narrowed.require(rendered(narrowed,1354,3200));
        narrowed.require(rendered(narrowed,1440,3200));
        check(true,"verify both resized and full-sized GPU buffers through the entire mapping");
        int scenarios=0;
        for(int rotation=0;rotation<4;rotation++) for(int percent=1;percent<=11;percent++) {
            int ow=1440,oh=3200,tw=rotation%2==0?ow-2*Math.round(ow*percent/100f):ow;
            int th=rotation%2==0?oh:oh-2*Math.round(oh*percent/100f);
            int sw=rotation%2==0?tw:th,sh=rotation%2==0?th:tw;
            int pw=rotation%2==0?ow:oh,ph=rotation%2==0?oh:ow;
            CompositorPlan p=new CompositorPlan(ow,oh,tw,th,rotation,pw,ph,sw,sh);
            CompositorGeometry g=rendered(p,tw,th);p.require(g);
            // Independent physical output dimensions after the GPU buffer fills the panel.
            double nativeVisibleW=(g.frame[2]-g.frame[0])*1440.0/tw;
            double nativeVisibleH=(g.frame[3]-g.frame[1])*3200.0/th;
            check(Math.abs(nativeVisibleW-tw)<=2 && Math.abs(nativeVisibleH-th)<=2,
                "logical rectangle keeps physical dimensions after the final raster expansion");
            check(p.destination[0]==p.rasterWidth-p.destination[2]
                && p.destination[1]==p.rasterHeight-p.destination[3],"opposite borders remain equal");
            scenarios++;
        }
        CompositorPlan fhd=new CompositorPlan(1440,3200,1354,3200,0,1080,2400,1016,2400);
        fhd.require(rendered(fhd,1016,2400));check(fhd.compensated,"support uniformly downscaled native output with pixel rounding");
        rejects(()->new CompositorPlan(1440,3200,1354,3200,0,1440,3200,1000,2500),"refuse unexplained output aspect changes");
        rejects(()->new CompositorPlan(1440,3200,1600,3200,0,1440,3200,1600,3200),"do not enlarge logical target");
        rejects(()->new CompositorPlan(1440,3200,1354,3000,0,1440,3200,1354,3000),"do not change both logical axes");
        rejects(()->new CompositorPlan(1440,3200,1354,3200,0,1354,3200,1354,3200),"never treat an already distorted baseline as normal");
        CompositorGeometry valid=rendered(narrowed,1354,3200);
        CompositorGeometry frameFillsAll=new CompositorGeometry(0,valid.layer,valid.frameBounds,new int[]{0,0,1354,3200},
            valid.outputBounds,valid.output,valid.nativeBounds,valid.nativeOutput);
        rejects(()->narrowed.require(frameFillsAll),"reject GPU buffer losing black borders despite correct projection metadata");
        CompositorGeometry staleRotation=new CompositorGeometry(1,valid.layer,valid.frameBounds,valid.frame,
            valid.outputBounds,valid.output,valid.nativeBounds,valid.nativeOutput);
        rejects(()->narrowed.require(staleRotation),"reject stale rotation during application");
        CompositorGeometry wrongNative=new CompositorGeometry(0,valid.layer,valid.frameBounds,valid.frame,
            valid.outputBounds,valid.output,valid.nativeBounds,new int[]{0,0,1354,3200});
        rejects(()->narrowed.require(wrongNative),"reject native mapping stretching to full output");
        System.out.println("PASS: "+checks+" output-compensation checks, including "+scenarios+" rotation/width scenarios");
    }
}
