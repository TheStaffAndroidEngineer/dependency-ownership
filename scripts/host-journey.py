#!/usr/bin/env python3
"""Reset only the three chapter apps on the named lab emulator; record exact installed APKs."""
from pathlib import Path
from fresh_android_xml import read_ui_xml
from common_goods_ui import CommonGoodsUi
import datetime, hashlib, json, os, re, shutil, subprocess, time, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT/'evidence/host-journeys'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
OUT.mkdir(parents=True,exist_ok=False)
for helper_name in ('common_goods_ui.py', 'fresh_android_xml.py'):
    helper = Path(__file__).with_name(helper_name)
    if helper.is_file():
        (OUT / helper_name).write_bytes(helper.read_bytes())
ADB = Path(os.environ['ANDROID_HOME'])/'platform-tools/adb'
APPS = {'broken':'staff.android.ownership.broken',
        'app':'staff.android.ownership','hilt-app':'staff.android.ownership.hiltapp'}
events=[]; frozen={}
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()

def run(*args):
    return subprocess.run([str(ADB),'-s','emulator-5554',*args],capture_output=True,check=True).stdout

def xml():
    return read_ui_xml(run, '/sdcard/chapter17-window.xml', set(APPS.values()))

ui = CommonGoodsUi(xml, run, OUT)
def capture(name,contains):
    frames=ui.observe(name)
    content='\n'.join(value for _,value in frames)
    for expected in contains: assert expected in content,(name,'missing',expected)
    events.append({'screen':name,'viewports':[label for label,_ in frames],
                   'asserted':list(contains),'scope':'Retained scrolling viewports, not one simultaneous screenshot'})
def top():ui.top()
def tap(text):ui.tap(text)

def edit(text):
    node=ui.find(lambda n: n.get('class')=='android.widget.EditText')
    assert node is not None, 'No visible editor'
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
    run('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.3)
    run('shell','input','keycombination','113','29');run('shell','input','keyevent','67')
    run('shell','input','text',text);run('shell','input','keyevent','4');time.sleep(.3)

def launch(package):
    run('shell','am','start','-n',package+'/.MainActivity');time.sleep(1)

try:
    avd=run('emu','avd','name').decode()
    assert 'StaffAndroid_API_36' in avd, 'Use the named disposable chapter emulator'
    for module,package in APPS.items():
        apk=ROOT/module/'build/outputs/apk/debug'/f'{module}-debug.apk'
        target=OUT/f'tested-{module}-debug.apk'
        shutil.copy2(apk,target)
        frozen[module]={'file':target.name,'sha256':sha(target),'package':package}
    shutil.copy2(Path(__file__),OUT/'tested-host-journey.py')
    for module,entry in frozen.items():
        run('install','-r',str(OUT/entry['file']))
        run('shell','pm','clear',entry['package'])
    (OUT/'device.txt').write_bytes(run('shell','getprop'))
    launch(APPS['broken'])
    capture('01-broken-start',('Cart Tea: 3','Saved sees: 3'))
    tap('Add 1 Tea from Saved');top()
    capture('02-broken-two-owners',('Cart Tea: 3','Saved sees: 4','Saved applied: 4'))
    for base,module in [(3,'app'),(10,'hilt-app')]:
        launch(APPS[module])
        capture(f'{base:02}-{module}-start',('Account A; generation 1','Cart Tea: 3','Saved sees: 3'))
        tap('Add 1 Tea from Saved');top()
        capture(f'{base+1:02}-{module}-shared-four',('Cart Tea: 4','Saved sees: 4','Saved applied: 4'))
        tap('Open legacy Cart');tap('Legacy add 1 Tea');top()
        capture(f'{base+2:02}-{module}-legacy-policy',('Cart Tea: 4','Tea in Cart: 4','Legacy rejected; no cart changed.'))
        tap('Switch to B');top()
        capture(f'{base+3:02}-{module}-new-b',('Account B; generation 2','Cart Tea: 1','Saved sees: 1','No legacy edit yet.'))
        tap('Legacy add 1 Tea');top()
        capture(f'{base+4:02}-{module}-java-b-add',('Cart Tea: 2','Saved sees: 2','Legacy applied: 2'))
        tap('Switch to A');top()
        capture(f'{base+5:02}-{module}-new-a',('Account A; generation 3','Cart Tea: 3','No legacy edit yet.'))
        tap('Try held A handle');top()
        capture(f'{base+6:02}-{module}-expired-old-a',('Cart Tea: 3','Held A expired; no cart changed.'))
    package=APPS['hilt-app']
    old_pid=run('shell','pidof',package).decode().strip()
    run('shell','input','keyevent','3')
    run('shell','run-as',package,'kill','-9',old_pid)
    time.sleep(.5)
    stopped=run('shell','dumpsys','package',package).decode()
    assert 'stopped=false' in stopped
    (OUT/'package-after-abrupt-death.txt').write_text(stopped)
    launch(package)
    new_pid=run('shell','pidof',package).decode().strip()
    assert old_pid and new_pid and old_pid!=new_pid
    capture('17-new-hilt-process',('Account A; generation 1','Cart Tea: 3','Saved sees: 3'))
    events.append({'abruptDebugProcessDeath':{'oldPid':old_pid,'newPid':new_pid,'reseedsQuantity':3,
                   'scope':'SIGKILL simulation, not an observed operating-system low-memory kill'}})
    (OUT/'logcat.txt').write_bytes(run('logcat','-d','-t','1000'))
    result={'status':'PASS','events':events,'installed_artifacts':frozen,
            'host_script_sha256':sha(OUT/'tested-host-journey.py'),
            'limits':['API36 emulator debug correctness only; no physical-device timing claim',
                      'Activity recreation and Hilt scope identity covered by instrumentation',
                      'No persistence/authentication/backend; account labels are controlled fixtures']}
    (OUT/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    print(OUT)
except Exception as error:
    (OUT/'result.json').write_text(json.dumps({'status':'FAIL','events':events,'installed_artifacts':frozen,'error':str(error)},indent=2)+'\n')
    raise
