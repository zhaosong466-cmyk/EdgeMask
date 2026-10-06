#!/usr/bin/env python3
"""Black-box tests for the generated watchdog using fake wm and dumpsys binaries.
Usage: python3 tests/watchdog_checks.py /absolute/path/to/compiled-pure-classes
No Android or root commands are executed by this test.
"""
import concurrent.futures
import json
import os
from pathlib import Path
import shlex
import subprocess
import sys
import tempfile
import time

MOCK = '''#!/usr/bin/env python3
import json,sys,time
from pathlib import Path
p=Path(__file__).resolve().parent
s=json.loads((p/'display.json').read_text())
def save():
 (p/'display.tmp').write_text(json.dumps(s));(p/'display.tmp').replace(p/'display.json')
if len(sys.argv)==2 and sys.argv[1]=='size':
 print('Physical size: '+s['physical'])
 if s['override']:print('Override size: '+s['override'])
 sys.exit(0)
if len(sys.argv)==3 and sys.argv[1]=='size':
 with (p/'commands').open('a') as f:f.write(sys.argv[2]+'\\n')
 if s.get('delay') and sys.argv[2]!='reset':time.sleep(s['delay'])
 if s['fail']:
  s['fail']-=1;save();sys.exit(1)
 s['override']=None if sys.argv[2]=='reset' else sys.argv[2]
 save();sys.exit(0)
if len(sys.argv)==3 and sys.argv[1]=='scaling' and sys.argv[2] in {'auto','off'}:
 mode=sys.argv[2]
 with (p/'scaling_commands').open('a') as f:f.write(mode+'\\n')
 if s.get(mode+'_failure'):sys.exit(1)
 if not s.get(mode+'_ignored'):s['scaling']=mode;save()
 sys.exit(0)
sys.exit(2)
'''

MOCK_DUMPSYS = '''#!/usr/bin/env python3
import json,sys
from pathlib import Path
s=json.loads((Path(__file__).resolve().parent/'display.json').read_text())
if sys.argv[1:]!=['window','displays']:sys.exit(2)
if s.get('unreadable'):print('OEM format unavailable');sys.exit(0)
# Other displays must not influence the default display's scaling state.
print('  Display: mDisplayId=2')
print('    init=800x600 160dpi noscale')
print('  Display: mDisplayId=0')
print('    init='+s['physical']+' 420dpi'+(' noscale' if s['scaling']=='off' else ''))
if s.get('duplicate'):print('    init='+s['physical']+' 420dpi')
'''

def await_value(path, values, seconds=6):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        if path.exists() and path.read_text().strip() in values:return path.read_text().strip()
        time.sleep(.05)
    raise AssertionError(f'Timed out waiting for {path.name}: {path.read_text() if path.exists() else "missing"}')

def run_case(name, classes):
    with tempfile.TemporaryDirectory(prefix="edge guard ' ") as tmp:
        d=Path(tmp);override=name=='prior_override';original_scaling='off' if name=='original_scaling_off' else 'auto'
        state={'physical':'1440x3200' if override else '1080x2400','override':'990x2400','scaling':'off',
               'fail':2 if name=='retry' else 4 if name=='failure' else 1 if name=='apply_failure' else 0,
               'delay':1.5 if name=='delayed_apply' else 0}
        if name=='scaling_off_failure':state.update(scaling='auto',off_failure=True)
        if name=='scaling_off_ignored':state.update(scaling='auto',off_ignored=True)
        if name=='scaling_restore_failure':state['auto_failure']=True
        if name=='scaling_restore_ignored':state['auto_ignored']=True
        if name=='unreadable_scaling':state['unreadable']=True
        if name=='duplicate_scaling':state['duplicate']=True
        (d/'display.json').write_text(json.dumps(state))
        (d/'mock-wm').write_text(MOCK);(d/'mock-wm').chmod(0o700)
        (d/'mock-dumpsys').write_text(MOCK_DUMPSYS);(d/'mock-dumpsys').chmod(0o700)
        for key,value in {'clock':'100.00 0.00\n','deadline':'102','backup.properties':'original snapshot','state':'','ready':'','pid':'','target':'','applied':'','bootid':''}.items():(d/key).write_text(value)
        subprocess.run(['java','-cp',classes,'com.edges.mask.SafetyChecks',str(d),str(d/'mock-wm'),
                        'override' if override else 'off' if original_scaling=='off' else 'default'],check=True)
        # Match app launch semantics. The launcher exits; watchdog must continue independently.
        subprocess.run(['/bin/sh','-c','nohup /bin/sh '+shlex.quote(str(d/'watchdog.sh'))+' </dev/null >/dev/null 2>&1 &'],check=True)
        await_value(d/'ready',{'ready'})
        targets=[]
        if name in {'target_apply','target_updates','delayed_apply','apply_failure','scaling_off_failure',
                    'scaling_off_ignored','unreadable_scaling','duplicate_scaling'}:
            targets=['994x2400']
            (d/'target').write_text(targets[0])
            if name=='delayed_apply':
                await_value(d/'commands',{targets[0]})
                (d/'clock').write_text('103.00 0.00\n')
            elif name in {'apply_failure','scaling_off_failure','scaling_off_ignored','unreadable_scaling','duplicate_scaling'}:
                pass
            else:
                await_value(d/'applied',{targets[0]})
                assert json.loads((d/'display.json').read_text())['override']==targets[0]
                assert json.loads((d/'display.json').read_text())['scaling']=='off'
                if name=='target_updates':
                    targets.append('972x2400');(d/'target').write_text(targets[-1])
                    await_value(d/'applied',{targets[-1]})
                    assert json.loads((d/'display.json').read_text())['override']==targets[-1]
                (d/'stop').write_text('1')
        elif name.startswith('reject_'):
            bad={'reject_shrink':'700x2400','reject_dp':'800x2400','reject_both_axes':'994x2300','reject_growth':'1200x2400','reject_malformed':'bad; touch unsafe','reject_leading_zero':'0994x2400'}[name]
            (d/'target').write_text(bad)
        elif name=='heartbeat':
            (d/'deadline').write_text('110');(d/'clock').write_text('103.00 0.00\n')
            time.sleep(1.15)
            assert (d/'state').read_text()=='' and not (d/'commands').exists(),'valid lease must remain active'
            (d/'clock').write_text('111.00 0.00\n')
        elif name=='manual_stop':(d/'stop').write_text('1')
        elif name=='corrupt_lease':(d/'deadline').write_text('broken')
        else:(d/'clock').write_text('103.00 0.00\n')
        expected='failed' if name in {'failure','scaling_restore_failure','scaling_restore_ignored',
                                     'unreadable_scaling','duplicate_scaling'} else 'restored'
        await_value(d/'state',{expected})
        final=json.loads((d/'display.json').read_text())
        if expected=='restored':assert final['override']==('1080x2400' if override else None)
        if name not in {'scaling_restore_failure','scaling_restore_ignored'}:
            assert final['scaling']==original_scaling,'attempt scaling restore even when size recovery fails'
        else:assert final['scaling']=='off','unverified restore must fail and retain backup'
        assert (d/'backup.properties').read_text()=='original snapshot','backup retained until app verification'
        writes=(d/'commands').read_text().splitlines()
        recovery='1080x2400' if override else 'reset'
        retries=3 if name in {'retry','failure','scaling_restore_failure','scaling_restore_ignored',
                             'unreadable_scaling','duplicate_scaling'} else 1
        applied_targets=[] if name in {'unreadable_scaling','duplicate_scaling'} else targets
        assert writes==applied_targets+[recovery]*retries,writes
        scaling_writes=(d/'scaling_commands').read_text().splitlines()
        target_scaling=[] if name in {'apply_failure','unreadable_scaling','duplicate_scaling'} else ['off']*len(targets)
        assert scaling_writes==target_scaling+[original_scaling]*retries,scaling_writes
        if name in {'apply_failure','scaling_off_failure','scaling_off_ignored','unreadable_scaling','duplicate_scaling'}:
            assert (d/'applied').read_text()=='','reject target acknowledgement if scaling is not verified'
        if targets and name not in {'apply_failure','scaling_off_failure','scaling_off_ignored','unreadable_scaling','duplicate_scaling'}:
            time.sleep(.5)
            assert (d/'commands').read_text().splitlines()==writes,'no late size change after rollback'
            assert json.loads((d/'display.json').read_text())['override'] is None
        if name=='retry':assert len(writes)==3
        if name=='failure':assert len(writes)==3 and final['override']=='990x2400'
        return name+' passed'

if __name__=='__main__':
    classes=str(Path(sys.argv[1]).resolve())
    cases=['app_death_timeout','prior_override','manual_stop','heartbeat','corrupt_lease','retry','failure',
           'target_apply','target_updates','delayed_apply','apply_failure','reject_shrink','reject_dp',
           'reject_both_axes','reject_growth','reject_malformed','reject_leading_zero','original_scaling_off',
           'scaling_off_failure','scaling_off_ignored','scaling_restore_failure','scaling_restore_ignored',
           'unreadable_scaling','duplicate_scaling']
    with concurrent.futures.ThreadPoolExecutor(max_workers=7) as executor:
        for result in executor.map(lambda c:run_case(c,classes),cases):print('PASS:',result)
