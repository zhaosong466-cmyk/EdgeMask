#!/usr/bin/env python3
"""Run production CompositorBridge in a JVM with fake Android interfaces.

Only the absolute dumpsys executable is substituted in a temporary source copy.
No Android device, root writes, panel behavior, or real Binder compatibility is tested.
"""
import os
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
    'android/graphics/Rect.java': '''package android.graphics;
public class Rect { public int left,top,right,bottom;
 public Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
 public String toString(){return left+","+top+","+right+","+bottom;}}
''',
    'android/os/Process.java': 'package android.os; public class Process {public static int myUid(){return 0;}}',
    'android/os/IBinder.java': '''package android.os; public interface IBinder {
 String getInterfaceDescriptor(); boolean transact(int code,Parcel data,Parcel reply,int flags); }
''',
    'android/os/Parcel.java': '''package android.os; import java.util.*;
public class Parcel {private final List<Integer> values=new ArrayList<>();private int cursor;
 public static Parcel obtain(){return new Parcel();} public void recycle(){}
 public void writeInterfaceToken(String ignored){} public void writeInt(int n){values.add(n);}
 public int readInt(){return values.get(cursor++);} public int dataSize(){return values.size()*4;}}
''',
    'android/os/ServiceManager.java': '''package android.os; import fake.State;
public class ServiceManager {public static IBinder getService(String name){return new IBinder(){
 public String getInterfaceDescriptor(){return "android.ui.ISurfaceComposer";}
 public boolean transact(int code,Parcel data,Parcel reply,int flags){
  State s=new State();
  if(code==1010){for(int i=0;i<4;i++)reply.writeInt(0);reply.writeInt(s.n("gpu"));return true;}
  if(code==1008){int value=data.readInt();s.backup(false);s.put("gpu",value);s.log("gpu "+value);s.save();return true;}
  return false;}};}}
''',
    'fake/State.java': '''package fake;
import java.nio.file.*;import java.io.*;import java.util.*;
public class State {public final Properties p=new Properties();final Path file;
 public State(){try {file=Paths.get(System.getenv("EDGE_STATE"));try(InputStream in=Files.newInputStream(file)){p.load(in);}}
 catch(Exception e){throw new RuntimeException(e);}}
 public int n(String key){return Integer.parseInt(p.getProperty(key));}
 public String get(String key){return p.getProperty(key,"");}
 public void put(String key,Object val){p.setProperty(key,""+val);}
 public void save(){try(OutputStream out=Files.newOutputStream(file)){p.store(out,"");}catch(Exception e){throw new RuntimeException(e);}}
 public void log(String line){try {Files.write(file.resolveSibling("writes"),(line+"\\n").getBytes("UTF-8"),StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}}
 public void backup(boolean size){try {Properties b=new Properties();try(InputStream in=Files.newInputStream(file.resolveSibling("compositor.properties"))){b.load(in);}
  if(!"1440".equals(b.getProperty("width"))||!"3200".equals(b.getProperty("height"))||!get("expectedGpu").equals(b.getProperty("gpu")))throw new Exception("original backup lost");
  if(size&&!"true".equals(b.getProperty("outputSizeRequested")))throw new Exception("size marker missing before mutation");
 }catch(Exception e){throw new RuntimeException(e);}}
}
''',
    'android/view/SurfaceControl.java': '''package android.view;
import android.os.*;import android.graphics.Rect;import fake.State;
public class SurfaceControl {
 public static long[] getPhysicalDisplayIds(){return new long[]{42};}
 public static IBinder getPhysicalDisplayToken(long id){return ServiceManager.getService("SurfaceFlinger");}
 public static Info getStaticDisplayInfo(long id){return new Info();}
 public static class Info {public boolean isInternal=true;public int installOrientation=0;}
 public static class Transaction {
  private int w,h,r;private Rect source,dest;
  public Transaction(){}
  public Transaction setDisplaySize(IBinder token,int a,int b){w=a;h=b;return this;}
  public Transaction setDisplayProjection(IBinder token,int a,Rect b,Rect c){r=a;source=b;dest=c;return this;}
  public void close(){}
  public void apply(boolean sync){State s=new State();
   if(w!=0){s.backup(true);s.log("size "+w+"x"+h);
    if("1".equals(s.get("restoreSizeFailure")))throw new IllegalStateException("simulated restore-size failure");
    if(!"ignored".equals(s.get("mode"))&&!"qti_override".equals(s.get("mode"))){s.put("nw",w);s.put("nh",h);s.put("fw",w);s.put("fh",h);s.save();}
    if("size_failure".equals(s.get("mode"))&&s.n("lw")!=1440)throw new IllegalStateException("simulated partial size failure");
   }
   if(source!=null){s.backup(false);s.log("project "+dest);s.put("rotation",r);s.put("out",dest);s.put("layer",source);
    // Model the verified QTI extension: output rectangle is overwritten by source.
    if("qti_override".equals(s.get("mode")))s.put("out",source);
    s.save();
    if("project_failure".equals(s.get("mode"))&&s.n("lw")!=1440)throw new IllegalStateException("simulated partial projection failure");}
  }
 }
}
'''
}
DUMPSYS = r'''#!/usr/bin/env python3
import os,sys
from pathlib import Path
p={}
for line in Path(os.environ['EDGE_STATE']).read_text().splitlines():
 if '=' in line and not line.startswith('#'):
  k,v=line.split('=',1);p[k]=v
n=lambda k:int(p[k])
r=n('rotation');nw=n('nw');nh=n('nh');fw=n('fw');fh=n('fh')
ow,oh=(nw,nh) if r%2==0 else (nh,nw)
lw,lh=(n('lw'),n('lh')) if r%2==0 else (n('lh'),n('lw'))
if sys.argv[1]=='input':
 print(f'DisplayViewport{{type=INTERNAL, displayId=0, orientation={r}, deviceWidth=1440, deviceHeight=3200, logicalFrame=[0, 0, {lw}, {lh}], physicalFrame=[0, 0, 1440, 3200]}}')
 sys.exit(0)
assert sys.argv[1:] == ['SurfaceFlinger','--comp-displays']
out=[int(x) for x in p['out'].split(',')];l,t,rr,b=out
native=out if r==0 else ([nw-b,l,nw-t,rr] if r==1 else ([nw-rr,nh-b,nw-l,nh-t] if r==2 else [t,nh-rr,b,nh-l]))
frame=[int(v*(fw/nw if i%2==0 else fh/nh)+.5) for i,v in enumerate(native)]
def space(name,bounds,content,rotation):
 rect=lambda a:'Rect('+', '.join(map(str,a))+')'
 print(name+' = ProjectionSpace{bounds='+rect(bounds)+', content='+rect(content)+', orientation=ROTATION_'+str(rotation*90)+'} ')
print('Display 42 (physical, "Test")')
logical=[int(x) for x in p['layer'].split(',')]
space('layerStackSpace',logical,logical,0)
space('orientedDisplaySpace',[0,0,ow,oh],out,0)
space('displaySpace',[0,0,nw,nh],native,r)
space('framebufferSpace',[0,0,fw,fh],frame,r)
'''


def read_props(file):
    return dict(line.split('=', 1) for line in file.read_text().splitlines()
                if '=' in line and not line.startswith('#'))


def write_props(file, p):
    file.write_text(''.join(f'{k}={v}\n' for k, v in p.items()))


def run_case(classes, folder, name):
    folder.mkdir()
    state = folder / 'state'
    original_gpu = 1 if name == 'gpu_on' else 0
    mode = name if name in ('ignored', 'size_failure', 'project_failure', 'qti_override') else 'honored'
    force_gpu = name != 'qti_disabled_gpu_off'
    qti_disabled = name in ('qti_disabled', 'qti_disabled_gpu_off')
    p = dict(lw=1440, lh=3200, nw=1440, nh=3200, fw=1440, fh=3200,
             rotation=0, gpu=original_gpu, originalGpu=original_gpu,
             expectedGpu=original_gpu if force_gpu else -1, mode=mode,
             out='0,0,1440,3200', layer='0,0,1440,3200')
    write_props(state, p)
    env = dict(os.environ, EDGE_STATE=str(state))

    def bridge(action, *args, succeeds=True):
        result = subprocess.run(['java', '-cp', str(classes), 'com.edges.mask.CompositorBridge',
                                 action, str(folder), *map(str, args)], env=env, text=True, capture_output=True)
        assert (result.returncode == 0) == succeeds, (name, action, result.stdout, result.stderr)
        return result

    bridge('prepare', 1440, 3200, 0, int(force_gpu))
    backup = read_props(folder / 'compositor.properties')
    assert backup['frameWidth'] == '1440' and backup['frameHeight'] == '3200'
    assert not (folder / 'writes').exists(), 'prepare must remain read-only'
    p.update(lw=1354, layer='0,0,1354,3200')
    if qti_disabled:
        # WM projection retains the original output raster when the QTI feature is off.
        p.update(out='43,0,1397,3200')
    else:
        p.update(nw=1354, fw=1354, out='0,0,1354,3200')
    write_props(state, p)
    failure = name in ('size_failure', 'project_failure', 'qti_override')
    bridge('apply', 1354, 3200, succeeds=not failure)
    report = (folder / 'compositor-report.txt').read_text()
    assert 'after-wm 1354x3200:' in report
    if qti_disabled:
        assert 'restore-buffer-request' not in report
        assert 'outputSizeRequested' not in read_props(folder / 'compositor.properties')
    else:
        assert 'restore-buffer-request: 1440x3200' in report
        assert read_props(folder / 'compositor.properties')['outputSizeRequested'] == 'true'
    assert read_props(folder / 'compositor.properties')['gpu'] == str(p['expectedGpu'])
    if not failure:
        bridge('check', 1354, 3200)
        out = read_props(state)['out']
        assert out == ('40,0,1314,3200' if name == 'ignored' else '43,0,1397,3200'), out
        assert ('compensated=true' in report) == (name == 'ignored')
    # Simulate the watchdog restoring WM first; SurfaceFlinger projection may still be stale.
    p = read_props(state)
    p.update(lw=1440, nw=1440, fw=1440)
    if name == 'rotation':
        p.update(rotation=1, layer='0,0,3200,1440', out='0,0,3200,1440')
    if name == 'restore_size_failure':
        p['restoreSizeFailure'] = 1
    write_props(state, p)
    bridge('restore', succeeds=name != 'restore_size_failure')
    final = read_props(state)
    assert final['gpu'] == str(original_gpu)
    assert final['out'] == ('0,0,3200,1440' if name == 'rotation' else '0,0,1440,3200')
    assert final['nw'] == '1440' and final['fw'] == '1440'
    writes = (folder / 'writes').read_text().splitlines()
    assert writes[-1].startswith('project '), writes
    if qti_disabled:
        assert not any(line.startswith('size ') for line in writes), writes
        if not force_gpu:
            assert not any(line.startswith('gpu ') for line in writes), writes
    else:
        assert writes[-3].startswith('gpu ') and writes[-2] == 'size 1440x3200', writes
    assert (folder / 'compositor.properties').exists(), 'retain original backup for watchdog verification'
    if name == 'restore_size_failure':
        p = read_props(state); p['restoreSizeFailure'] = 0; write_props(state, p)
        bridge('restore')  # Recovery remains retryable after a partial failure.
    print('PASS: production bridge', name)


if __name__ == '__main__':
    with tempfile.TemporaryDirectory(prefix='edge-bridge-') as tmp:
        temp = Path(tmp); src = temp / 'src'; classes = temp / 'classes'
        mock = temp / 'mock-dumpsys'; mock.write_text(DUMPSYS); mock.chmod(0o700)
        for name, content in STUBS.items():
            path = src / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text(content)
        app = ROOT / 'app/src/main/java/com/edges/mask'
        for name in ('CompositorBridge', 'CompositorGeometry', 'CompositorPlan', 'DisplayProjection', 'DisplayProfile', 'DisplayScaling'):
            content = (app / (name + '.java')).read_text()
            if name == 'CompositorBridge':
                assert content.count('"/system/bin/dumpsys"') == 4
                content = content.replace('"/system/bin/dumpsys"', '"' + str(mock) + '"')
            path = src / 'com/edges/mask' / (name + '.java'); path.parent.mkdir(parents=True, exist_ok=True); path.write_text(content)
        compiler = ['java', '-jar', str(Path(sys.argv[1]).resolve()), '-source', '8', '-target', '8'] if len(sys.argv)>1 else ['javac']
        subprocess.run([*compiler, '-d', str(classes), *map(str, src.rglob('*.java'))], check=True)
        for case in ('honored', 'ignored', 'size_failure', 'project_failure', 'gpu_on', 'rotation',
                     'restore_size_failure', 'qti_override', 'qti_disabled', 'qti_disabled_gpu_off'):
            run_case(classes, temp / case, case)
