#!/usr/bin/env python3
"""Run the exact production watchdog with fake WM + compositor executables, without root."""
import concurrent.futures
import json
from pathlib import Path
import shlex
import subprocess
import sys
import tempfile
import time
from watchdog_checks import MOCK, MOCK_DUMPSYS, await_value

HELPER = r'''#!/bin/sh
exec python3 "$(dirname "$0")/mock_compositor.py" "$@"
'''
COMPOSITOR = r'''
import json,sys,time
from pathlib import Path
d=Path(__file__).parent
args=sys.argv[1:];action=args[0]
s=json.loads((d/'compositor.json').read_text())
with (d/'project_commands').open('a') as f:f.write(' '.join(args)+'\n')
def save():
 (d/'compositor.tmp').write_text(json.dumps(s));(d/'compositor.tmp').replace(d/'compositor.json')
if action=='prepare':
 assert json.loads((d/'display.json').read_text())['override'] is None
 if s['case']=='prepare_failure':print('preflight unsupported');sys.exit(1)
 (d/'compositor.properties').write_text(str(s['gpu']))
elif action=='apply':
 assert (d/'compositor.properties').exists(),'backup before any GPU/projection write'
 assert json.loads((d/'display.json').read_text())['override']=='x'.join(args[1:])
 s['gpu']=1;s['projection']='x'.join(args[1:]);save()
 if s['case']=='delayed_compositor':time.sleep(1.5)
 if s['case']=='partial_apply_failure':print('projection failed after GPU changed');sys.exit(1)
elif action=='check':
 if s['case']=='projection_drift':print('projection was overwritten');sys.exit(1)
 if s['projection']!='x'.join(args[1:]) or s['gpu']!=1:sys.exit(1)
elif action=='restore':
 assert json.loads((d/'display.json').read_text())['override'] is None,'restore WM before output mapping'
 if s['case']=='restore_failure':sys.exit(1)
 if s.get('restore_retries',0):s['restore_retries']-=1;save();sys.exit(1)
 if (d/'compositor.properties').exists():s['gpu']=int((d/'compositor.properties').read_text())
 s['projection']='1080x2400';save()
else:sys.exit(2)
'''

def case(name, classes):
    with tempfile.TemporaryDirectory(prefix="edge output ' ") as temp:
        d=Path(temp)
        (d/'display.json').write_text(json.dumps({'physical':'1080x2400','override':None,'scaling':'auto','fail':0}))
        original_gpu=1 if name=='preserve_gpu_on' else 0
        (d/'compositor.json').write_text(json.dumps({'case':name,'gpu':original_gpu,'projection':'1080x2400',
                                                    'restore_retries':2 if name=='restore_retry' else 0}))
        for filename,content in [('mock-wm',MOCK),('mock-dumpsys',MOCK_DUMPSYS),('compositor.sh',HELPER),('mock_compositor.py',COMPOSITOR)]:
            (d/filename).write_text(content);(d/filename).chmod(0o700)
        for filename,value in {'clock':'100.00 0.00\n','deadline':'120','backup.properties':'original WM backup',
                               'state':'','ready':'','pid':'','bootid':'','applied':'','target':'','error':''}.items():
            (d/filename).write_text(value)
        subprocess.run(['java','-cp',classes,'com.edges.mask.SafetyChecks',str(d),str(d/'mock-wm'),'default','compositor'],check=True)
        subprocess.run(['/bin/sh','-c','nohup /bin/sh '+shlex.quote(str(d/'watchdog.sh'))+' </dev/null >/dev/null 2>&1 &'],check=True)
        if name=='prepare_failure':
            await_value(d/'state',{'restored'})
            assert (d/'applied').read_text()==''
            assert (d/'commands').read_text().splitlines()==['reset']
        else:
            await_value(d/'ready',{'ready'});(d/'target').write_text('994x2400')
            if name=='partial_apply_failure':await_value(d/'state',{'restored'})
            elif name=='delayed_compositor':
                end=time.monotonic()+4
                while json.loads((d/'compositor.json').read_text())['gpu']!=1:
                    assert time.monotonic()<end;time.sleep(.05)
                (d/'clock').write_text('121.00 0.00\n')
            else:
                await_value(d/'applied',{'994x2400'})
                s=json.loads((d/'compositor.json').read_text());assert s['gpu']==1 and s['projection']=='994x2400'
                if name=='target_updates':
                    (d/'target').write_text('972x2400');await_value(d/'applied',{'972x2400'})
                if name=='projection_drift':(d/'clock').write_text('106.00 0.00\n')
                elif name=='app_death':(d/'clock').write_text('121.00 0.00\n')
                else:(d/'stop').write_text('1')
            expected='failed' if name=='restore_failure' else 'restored'
            await_value(d/'state',{expected},seconds=9)
        s=json.loads((d/'compositor.json').read_text())
        if name!='restore_failure':
            assert s['gpu']==original_gpu,'restore original GPU flag, including pre-existing ON'
            assert s['projection']=='1080x2400','restore full original output'
        else:assert (d/'backup.properties').exists() and (d/'compositor.properties').exists()
        assert json.loads((d/'display.json').read_text())['override'] is None
        assert json.loads((d/'display.json').read_text())['scaling']=='auto'
        commands=(d/'project_commands').read_text().splitlines()
        assert commands[0]=='prepare 1080 2400 0 1'
        if name=='partial_apply_failure':assert (d/'applied').read_text()==''
        if name=='projection_drift':assert 'check 994 2400' in commands and 'overwritten' in (d/'error').read_text()
        if name in ('restore_failure','restore_retry'):assert commands.count('restore')==3
        before=(d/'project_commands').read_text();time.sleep(.35)
        assert (d/'project_commands').read_text()==before,'no delayed output mutation after rollback'
        return name

if __name__=='__main__':
    classes=str(Path(sys.argv[1]).resolve())
    cases=['normal_stop','preserve_gpu_on','prepare_failure','partial_apply_failure','delayed_compositor',
           'app_death','projection_drift','target_updates','restore_retry','restore_failure']
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
        for result in pool.map(lambda name:case(name,classes),cases):print('PASS: compositor',result)
