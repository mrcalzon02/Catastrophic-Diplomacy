import argparse, pathlib, subprocess, time, json, hashlib, os, re, shutil, sys
REPO=pathlib.Path(__file__).resolve().parents[1]
ROOT=REPO.parent
JDK=pathlib.Path(os.environ['JAVA_HOME'])/'bin' if 'JAVA_HOME' in os.environ else pathlib.Path(shutil.which('javac')).resolve().parent
p=argparse.ArgumentParser();p.add_argument('game',choices=['beckenburg','veil','diplomacy']);p.add_argument('--label',default='baseline');p.add_argument('--full',action='store_true');a=p.parse_args()
names={'beckenburg':'The-Beckenburg-Project','veil':'The-Malefic-Veil','diplomacy':'Catastrophic-Diplomacy'}
repo=REPO; output=repo/'build'/'network-checks';output.mkdir(parents=True,exist_ok=True)
log=output/(a.label+'.log'); results=[]
def run(cmd,timeout=180):
    start=time.monotonic()
    with log.open('a',encoding='utf-8') as f:
        f.write('COMMAND '+str(cmd)+'\n');f.flush()
        try:r=subprocess.run(cmd,cwd=repo,stdout=f,stderr=subprocess.STDOUT,timeout=timeout);code=r.returncode
        except subprocess.TimeoutExpired:code=124
    row={'command':cmd,'exit':code,'seconds':round(time.monotonic()-start,3)};results.append(row);print(json.dumps(row),flush=True);return code
java=str(JDK/('java.exe' if os.name=='nt' else 'java')); javac=str(JDK/('javac.exe' if os.name=='nt' else 'javac'))
if a.game=='beckenburg':
    env=os.environ.copy();env['PATH']=str(JDK)+os.pathsep+'C:/Program Files/Blender Foundation/Blender 5.0'+os.pathsep+env['PATH'];os.environ.update(env)
    code=run(['cmd','/c','build.cmd','package'] if os.name=='nt' else ['sh','build.sh','package'],300)
    if code==0:code=run([javac,'--release','17','-encoding','UTF-8','-cp','build/classes','-d','build/test','@build/sources-tests.txt'])
    cp=os.pathsep.join(['build/classes','build/test']);prefix='org.beckenburg.'
    tests=['LanWorldIntegrationTest','LanSessionResumeIntegrationTest','LanGhostLobbySecrecyTest','LanOutboundQueueTest','CampaignCoopLobbyTest','GameplaySmokeTest']
    if a.full:tests=[x.strip() for x in (repo/'regression-tests.txt').read_text().splitlines() if x.strip() and not x.startswith('#')]
elif a.game=='veil':
    sources=list((repo/'src/main/java').rglob('*.java'))+list((repo/'src/test/java').rglob('*.java'))
    argfile=output/'sources.txt';argfile.write_text('\n'.join('"'+str(x.relative_to(repo)).replace('\\','/')+'"' for x in sources))
    code=run([javac,'--release','17','-encoding','UTF-8','-d','build/classes','@'+str(argfile)],180)
    cp='build/classes';prefix='veil.'
    tests=['WorldSelfTest','ArenaSystemsTest','LanSmokeTest','DedicatedHostTest','HostCheckpointTest','HostPersistenceTest','HostAiRealmRestartTest','ContinentSectorNetworkTest','WorldCreationEndToEndTest','HardcoreHostingLanTest']
    if a.full:
        workflow=(repo/'.github/workflows/release.yml')
        texts='\n'.join(x.read_text(encoding='utf-8') for x in (repo/'.github/workflows').glob('*.yml'))
        matches=re.findall(r'for test in (.*?); do',texts,re.S)
        tests=list(dict.fromkeys(t for match in matches for t in re.findall(r'\b[A-Za-z][A-Za-z0-9]*(?:Test|SelfTest)\b',match))) or tests
else:
    sources=list((repo/'core/src/main/java').rglob('*.java'))+list((repo/'core/src/test/java').rglob('*.java'))+list((repo/'server/src').rglob('*.java'))
    argfile=output/'sources.txt';argfile.write_text('\n'.join('"'+str(x.relative_to(repo)).replace('\\','/')+'"' for x in sources))
    code=run([javac,'--release','17','-encoding','UTF-8','-d','build/classes','@'+str(argfile)],180)
    cp=os.pathsep.join(['build/classes','core/src/main/resources']);prefix='diplomacy.sim.'
    tests=['BattleSmokeTest','BattleOrdersTest','ShipyardTest','PlatformPersistenceTest','CampaignLedgerTest','FleetScaleTest']
    if a.full:tests=[x.stem for x in (repo/'core/src/test/java/diplomacy/sim').glob('*Test.java')]
    game_sources=list((repo/'game/src/main/java').rglob('*.java'))
    game_args=output/'game-sources.txt';game_args.write_text('\n'.join('"'+str(x.relative_to(repo)).replace('\\','/')+'"' for x in game_sources))
    gdx=pathlib.Path.home()/'.m2/repository/com/badlogicgames/gdx/gdx/1.14.2/gdx-1.14.2.jar'
    if code==0:code=run([javac,'--release','17','-encoding','UTF-8','-cp',cp+os.pathsep+str(gdx),'-d','build/game','@'+str(game_args)])
if code==0:
    for test in tests:run([java,'-Djava.awt.headless=true','-ea','-cp',cp,prefix+test],180)
    run([java,'-Djava.awt.headless=true','-ea','-cp',cp,'hbnet.HbnetTest','local'])
    for test in ({'beckenburg':['hbnet.PrivacyUiTest','org.beckenburg.DedicatedLifecycleTest','hbnet.HbnetTest','org.beckenburg.SecureLanIntegrationTest'],'veil':['hbnet.PrivacyUiTest','hbnet.HbnetTest','veil.SecureLanIntegrationTest'],'diplomacy':['hbnet.HbnetTest','diplomacy.server.BattleServerTest','diplomacy.server.BattleLanTest']}[a.game]):
        if (repo/'build/classes'/pathlib.Path(test.replace('.','/')+'.class')).exists() or (repo/'build/test'/pathlib.Path(test.replace('.','/')+'.class')).exists():run([java,'-Djava.awt.headless=true','-ea','-cp',cp,test],180)
receipt={'base':subprocess.check_output(['git','rev-parse','HEAD'],cwd=repo,text=True).strip(),'platform':os.name,'results':results,'log_sha256':hashlib.sha256(log.read_bytes()).hexdigest()}
(output/(a.label+'.json')).write_text(json.dumps(receipt,indent=2))
print('SUMMARY',a.game,'passed',sum(x['exit']==0 for x in results),'failed',sum(x['exit']!=0 for x in results),flush=True)

sys.exit(1 if any(x['exit']!=0 for x in results) else 0)
