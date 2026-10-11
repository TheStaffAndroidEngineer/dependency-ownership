#!/usr/bin/env python3
"""Compile an old Java client once, then run those exact bytes against three Kotlin libraries."""
from pathlib import Path
import hashlib,json,os,re,shutil,subprocess
ROOT=Path(__file__).resolve().parents[1]
WORK=ROOT/'build/compatibility-work'; OUT=ROOT/'evidence/compatibility'
WORK.mkdir(parents=True,exist_ok=True);OUT.mkdir(parents=True,exist_ok=True)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
for name in ['gradlew','gradlew.bat','gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.properties','gradle/libs.versions.toml','gradle.properties']:
 target=WORK/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(ROOT/name,target)
(WORK/'settings.gradle.kts').write_text('''pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { mavenCentral() } }
rootProject.name = "compatibility-witness"
include(":v1", ":compatible", ":broken", ":client")
''')
(WORK/'build.gradle.kts').write_text('plugins { alias(libs.plugins.kotlinJvm) apply false }\n')
inputs={}
for mod,source in [('v1',ROOT/'compatibility/v1/CartBadge.kt'),('compatible',ROOT/'legacy-bridge/src/main/kotlin/staff/android/ownership/legacy/CartBadge.kt'),('broken',ROOT/'legacy-bridge/src/main/kotlin/staff/android/ownership/legacy/CartBadge.kt')]:
 p=WORK/mod/'src/main/kotlin/CartBadge.kt';p.parent.mkdir(parents=True,exist_ok=True)
 text=source.read_text()
 if mod=='broken':
  assert '@JvmOverloads' in text
  text=re.sub(r'@JvmOverloads\s*', '', text)
 p.write_text(text);inputs[mod]={'source':str(source.relative_to(ROOT)),'source_sha256':sha(source),'tested_sha256':sha(p),'mutation':'remove @JvmOverloads' if mod=='broken' else None}
 (WORK/mod/'build.gradle.kts').write_text('''plugins { alias(libs.plugins.kotlinJvm); `java-library` }
kotlin { jvmToolchain(27); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26) } }
java { sourceCompatibility = JavaVersion.VERSION_26; targetCompatibility = JavaVersion.VERSION_26 }
''')
p=WORK/'client/src/main/java/OldConsumer.java';p.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(ROOT/'compatibility/client/OldConsumer.java',p)
(WORK/'client/build.gradle.kts').write_text('''plugins { java }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(27)) } }
tasks.withType<JavaCompile>().configureEach { options.release.set(26) }
dependencies { implementation(project(":v1")) }
''')
env=os.environ.copy();assert env.get('JAVA_HOME'),'Set JAVA_HOME to the edition JDK'
result=subprocess.run([str(WORK/'gradlew'),':v1:jar',':client:jar',':compatible:jar',':broken:jar','--console=plain'],cwd=WORK,env=env,capture_output=True,text=True)
(OUT/'build.log').write_text(result.stdout+result.stderr);assert result.returncode==0,result.stdout+result.stderr
jhome=Path(env['JAVA_HOME']); stdlibs=list((Path.home()/'.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.4.20').rglob('kotlin-stdlib-2.4.20.jar'));assert len(stdlibs)==1
client=WORK/'client/build/libs/client.jar';shutil.copy2(client,OUT/'old-consumer.jar');oldhash=sha(client)
results=[]
for mod in ['v1','compatible','broken']:
 lib=WORK/mod/'build/libs'/f'{mod}.jar';shutil.copy2(lib,OUT/f'{mod}.jar')
 cp=os.pathsep.join(map(str,[client,lib,stdlibs[0]]))
 run=subprocess.run([str(jhome/'bin/java'),'-cp',cp,'OldConsumer'],capture_output=True,text=True)
 (OUT/f'{mod}-run.log').write_text(run.stdout+run.stderr)
 expected=(run.returncode==0 and 'OLD CONSUMER PASS: Tea in Cart: 4' in run.stdout) if mod!='broken' else (run.returncode!=0 and 'NoSuchMethodError' in run.stderr and '<init>(int)' in run.stderr)
 assert expected,(mod,run.stdout,run.stderr)
 signatures=subprocess.run([str(jhome/'bin/javap'),'-s','-classpath',str(lib),'staff.android.ownership.legacy.CartBadge'],capture_output=True,text=True,check=True).stdout
 (OUT/f'{mod}-descriptors.txt').write_text(signatures)
 # A freshly compiled Java consumer is a separate source compatibility observation.
 classes=WORK/mod/'fresh-java';classes.mkdir(exist_ok=True)
 compile_new=subprocess.run([str(jhome/'bin/javac'),'--release','26','-cp',os.pathsep.join(map(str,[lib,stdlibs[0]])),'-d',str(classes),str(p)],capture_output=True,text=True)
 (OUT/f'{mod}-fresh-javac.log').write_text(compile_new.stdout+compile_new.stderr)
 assert (compile_new.returncode==0)==(mod!='broken')
 assert sha(client)==oldhash
 results.append({'library':mod,'exit_code':run.returncode,'expected':True,'new_java_compiles':compile_new.returncode==0,'library_sha256':sha(lib),'unchanged_client_sha256':oldhash})
report={'status':'PASS','inputs':inputs,'consumer_source_sha256':sha(p),'results':results,'scope':'One unchanged Java consumer, constructor (I)V, getters and label behavior; not blanket Kotlin or SDK compatibility.'}
(OUT/'result.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
