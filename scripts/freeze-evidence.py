#!/usr/bin/env python3
"""Freeze Chapter 17 verified sources, reports and exact installed artifacts once."""
from pathlib import Path
import hashlib,json,shutil,subprocess,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]; REPO=ROOT.parents[1]; OUT=ROOT/'evidence/2026-10-06'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
rel=lambda p:str(p.relative_to(REPO))
assert not (OUT/'verification.json').exists(), 'Preserve a completed freeze before replacing it'
def copy(p,q):
 q.parent.mkdir(parents=True,exist_ok=True)
 result=subprocess.run(['cp','-c',str(p),str(q)],capture_output=True)
 if result.returncode:shutil.copy2(p,q)
 assert sha(p)==sha(q)
def tree(p,q):
 for f in sorted(p.rglob('*')):
  if f.is_file():copy(f,q/f.relative_to(p))
reports=[('session-tests',ROOT/'session-impl/build/test-results/test',16,0),
 ('bridge-tests',ROOT/'legacy-bridge/build/test-results/test',8,0),
 ('lease-tests',ROOT/'lease-lab/build/test-results/test',19,0),
 ('learner-tests',ROOT/'lease-lab/build/test-results/exerciseTest',19,10)]
for mod,count in [('app',2),('hilt-app',1),('broken',1)]:reports.append((mod+'-android-tests',ROOT/mod/'build/outputs/androidTest-results/connected/debug',count,0))
counts={}
for name,source,expected,failures in reports:
 files=list(source.rglob('TEST-*.xml'));suites=[s for f in files for s in ET.parse(f).getroot().iter('testsuite')]
 actual={k:sum(int(s.get(k,'0')) for s in suites) for k in ['tests','failures','errors','skipped']}
 assert actual==dict(tests=expected,failures=failures,errors=0,skipped=0),(name,actual)
 if failures:
  assert all('AssertionFailedError' in n.get('type','') for f in files for n in ET.parse(f).getroot().iter('failure'))
 counts[name]=actual
 tree(source,OUT/'reference'/name)
lint={}
for mod in ['app','hilt-app','broken']:
 p=ROOT/mod/'build/reports/lint-results-debug.xml';issues=list(ET.parse(p).getroot())
 assert all(i.get('severity')=='Warning' and i.get('id')=='MissingApplicationIcon' for i in issues),(mod,issues)
 lint[mod]=[i.get('id') for i in issues];copy(p,OUT/'reference'/f'{mod}-lint.xml')
hosts=sorted((ROOT/'evidence/host-journeys').iterdir());host=hosts[-1]
host_result=json.loads((host/'result.json').read_text())
assert host_result['status']=='PASS' and len(list(host.glob('*.png')))==17
for mod,item in host_result['installed_artifacts'].items():
 apk=ROOT/mod/'build/outputs/apk/debug'/f'{mod}-debug.apk'
 assert sha(apk)==item['sha256']==sha(host/item['file'])
assert sha(ROOT/'scripts/host-journey.py')==host_result['host_script_sha256']
tree(host,OUT/'final-host-journey')
for previous in hosts[:-1]:tree(previous,OUT/'earlier-host-journeys'/previous.name)
probes=sorted((ROOT/'evidence/hilt-probes').iterdir());probe=probes[-1]
hilt=json.loads((probe/'results.json').read_text());assert hilt['status']=='PASS' and len(hilt['witnesses'])==3
for path,expected in hilt['canonical_inputs'].items():assert sha(ROOT/path)==expected,path
tree(probe,OUT/'final-hilt-probes')
for previous in probes[:-1]:tree(previous,OUT/'earlier-hilt-probes'/previous.name)
compat=json.loads((ROOT/'evidence/compatibility/result.json').read_text());assert compat['status']=='PASS' and len(compat['results'])==3
for item in compat['inputs'].values():assert sha(ROOT/item['source'])==item['source_sha256']
tree(ROOT/'evidence/compatibility',OUT/'compatibility')
if (ROOT/'evidence/initial-hilt-test-failure').exists():tree(ROOT/'evidence/initial-hilt-test-failure',OUT/'earlier-hilt-instrumentation')
for p in sorted(ROOT.glob('evidence-*.log')):copy(p,OUT/'logs'/p.name)
for p in sorted((ROOT/'evidence').glob('*.log')):copy(p,OUT/'logs'/p.name)
artifacts=[]
files=[]
for mod in ['app','hilt-app','broken']:
 files += [ROOT/mod/'build/outputs/apk/debug'/f'{mod}-debug.apk',ROOT/mod/'build/outputs/apk/androidTest/debug'/f'{mod}-debug-androidTest.apk']
files += [ROOT/mod/'build/libs'/f'{mod}.jar' for mod in ['cart-api','cart-impl','session-api','session-impl','legacy-bridge']]
for p in files:
 q=OUT/'artifacts'/p.name;copy(p,q);artifacts.append({'path':rel(q),'sha256':sha(q),'built_from':rel(p)})
sources=[]
for p in sorted(ROOT.rglob('*')):
 local=p.relative_to(ROOT)
 if not p.is_file() or set(local.parts)&{'evidence','build','.gradle','.kotlin','__pycache__'}:continue
 if p.name=='local.properties':continue
 if p.name not in {'.gitignore','README.md','verification.md','gradlew','gradlew.bat'} and p.suffix not in {'.kt','.java','.kts','.toml','.properties','.xml','.json','.py','.jar'}:continue
 q=OUT/'source-snapshot'/local;copy(p,q);sources.append({'path':rel(p),'snapshot':rel(q),'sha256':sha(p)})
evidence=[{'path':rel(p),'sha256':sha(p)} for p in sorted(OUT.rglob('*')) if p.is_file() and 'source-snapshot' not in p.relative_to(OUT).parts and p.name!='verification.json']
manifest={'status':'PASS','date':'2026-10-06','edition_cutoff':'2026-10-04',
 'runtime':'JDK27 / Gradle9.8.0 / Kotlin2.4.20 / JVM26 / AGP9.4.1 / Hilt2.60.1 / KSP2.3.12 / debug API36 ARM64 emulator',
 'reference_checks':{'session_jvm':16,'bridge_jvm':8,'lease_jvm':19,'manual_android':2,'hilt_android':1,'total':46},
 'defect_witnesses':{'broken_android':1},'learner':{'cases':19,'intended_assertion_failures':10,'passes':9,'errors':0,'skipped':0},
 'test_counts':counts,'hilt_probes':hilt,'compatibility_probes':compat,
 'final_build':{'tasks':296,'executed':252,'from_cache':35,'up_to_date':9,'fresh_all_tasks_run':False},
 'lint':lint,'final_host':{'path':rel(OUT/'final-host-journey'),'result':host_result},
 'sources':sources,'artifacts':artifacts,'evidence_files':evidence,
 'limits':['In-memory fixture; process recreation reseeds A/1 quantity3; no authentication or durable account data',
 'One unchanged Java consumer and selected descriptors; not blanket Kotlin/SDK compatibility',
 'Generated graph probes are not runtime authority or a build performance measurement',
 'No release shrinking, physical-device timing, API26/API37 device or real network check',
 'SIGKILL is a controlled debug-process interruption, not an observed low-memory kill']}
(OUT/'verification.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps({'status':'PASS','sources':len(sources),'artifacts':len(artifacts),'evidence_files':len(evidence)}))
