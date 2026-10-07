package com.edges.mask;

import java.util.Arrays;

/** Maps saved full-screen coordinates through an output raster that may have been resized by WM.
 * In compensation mode the final full raster is assumed to fill the original panel. This is a
 * reversible correction model for the reported stretch, not proof of downstream panel behavior. */
final class CompositorPlan {
    final int rotation, panelWidth, panelHeight, rasterWidth, rasterHeight;
    final int[] source, destination, panelTarget;
    final boolean compensated;

    CompositorPlan(int originalW,int originalH,int targetW,int targetH,int r,
                   int savedOutputW,int savedOutputH,int currentOutputW,int currentOutputH) {
        rotation=r; panelWidth=savedOutputW; panelHeight=savedOutputH;
        rasterWidth=currentOutputW; rasterHeight=currentOutputH;
        // Validate the unchanged original full-screen reference and target bounds first.
        panelTarget=CompositorGeometry.centered(originalW,originalH,targetW,targetH,r,panelWidth,panelHeight);
        source=CompositorGeometry.logical(targetW,targetH,r);
        if(rasterWidth<200 || rasterHeight<200 || rasterWidth>10000 || rasterHeight>10000)
            throw new IllegalArgumentException("输出区域尺寸无效："+rasterWidth+"x"+rasterHeight);
        boolean normal=aspect(rasterWidth,rasterHeight,panelWidth,panelHeight);
        // Permit this specific candidate cause of the reported stretch: output follows the new logical
        // aspect. Do not accept arbitrary anisotropic geometry or just remove a safety check.
        compensated=!normal;
        if(compensated && !aspect(rasterWidth,rasterHeight,source[2],source[3]))
            throw new IllegalArgumentException("输出比例变化不符合目标：original="+panelWidth+"x"+panelHeight
                +", logical="+source[2]+"x"+source[3]+", output="+rasterWidth+"x"+rasterHeight);
        if(!compensated) {
            destination=CompositorGeometry.centered(originalW,originalH,targetW,targetH,r,rasterWidth,rasterHeight);
        } else {
            // Compress in the current raster BEFORE its subsequent expansion to the panel.
            // Equal integer margins ensure symmetric borders after the final scaling too.
            int x=(int)Math.round((double)panelTarget[0]*rasterWidth/panelWidth);
            int y=(int)Math.round((double)panelTarget[1]*rasterHeight/panelHeight);
            destination=new int[]{x,y,rasterWidth-x,rasterHeight-y};
            if(x<0 || y<0 || destination[2]<=x || destination[3]<=y)
                throw new IllegalArgumentException("反向补偿区域无效");
        }
    }
    private static boolean aspect(int w,int h,int refW,int refH) {
        return Math.abs((double)w*refH/(h*(double)refW)-1)<=.002;
    }
    static int[] rotate(int[] rect,int orientedW,int orientedH,int r) {
        switch(r) {
            case 0:return rect.clone();
            case 1:return new int[]{orientedH-rect[3],rect[0],orientedH-rect[1],rect[2]};
            case 2:return new int[]{orientedW-rect[2],orientedH-rect[3],orientedW-rect[0],orientedH-rect[1]};
            case 3:return new int[]{rect[1],orientedW-rect[2],rect[3],orientedW-rect[0]};
            default:throw new IllegalArgumentException("投影方向无效");
        }
    }
    void require(CompositorGeometry g) {
        if(g.rotation!=rotation || !Arrays.equals(g.layer,source) || !Arrays.equals(g.output,destination)
                || !Arrays.equals(g.outputBounds,new int[]{0,0,rasterWidth,rasterHeight}))
            throw new IllegalArgumentException("输出投影未按计算结果生效");
        int nw=rotation%2==0?rasterWidth:rasterHeight,nh=rotation%2==0?rasterHeight:rasterWidth;
        int pw=rotation%2==0?panelWidth:panelHeight,ph=rotation%2==0?panelHeight:panelWidth;
        if(!Arrays.equals(g.nativeBounds,new int[]{0,0,nw,nh})
                || !Arrays.equals(g.nativeOutput,rotate(destination,rasterWidth,rasterHeight,rotation)))
            throw new IllegalArgumentException("屏幕方向与原生输出坐标不一致");
        int fw=g.frameBounds[2],fh=g.frameBounds[3];
        if(g.frameBounds[0]!=0 || g.frameBounds[1]!=0 || fw<200 || fh<200 || fw>10000 || fh>10000)
            throw new IllegalArgumentException("帧缓冲范围无效");
        int[] expectedPanel=rotate(panelTarget,panelWidth,panelHeight,rotation);
        for(int i=0;i<4;i++) {
            double frameExtent=i%2==0?fw:fh,outputExtent=i%2==0?nw:nh,panelExtent=i%2==0?pw:ph;
            double frameCoordinate=g.nativeOutput[i]*frameExtent/outputExtent;
            if(Math.abs(g.frame[i]-frameCoordinate)>1.01)
                throw new IllegalArgumentException("GPU 帧缓冲没有保留计算出的边距");
            // Verify the whole logical -> output -> framebuffer -> panel mapping, allowing
            // only pixel-rounding error. Checking aspect equality of two buffers is insufficient.
            double finalCoordinate=g.frame[i]*panelExtent/frameExtent;
            double tolerance=1+2*panelExtent/Math.min(frameExtent,outputExtent);
            if(Math.abs(finalCoordinate-expectedPanel[i])>tolerance)
                throw new IllegalArgumentException("补偿后的屏幕区域比例不符");
        }
    }
    String numericSummary() {
        return "compensated="+compensated+"; reference="+panelWidth+"x"+panelHeight
            +"; raster="+rasterWidth+"x"+rasterHeight+"; source="+Arrays.toString(source)
            +"; destination="+Arrays.toString(destination)+"; panelTarget="+Arrays.toString(panelTarget);
    }
}
