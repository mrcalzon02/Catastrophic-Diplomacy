#!/usr/bin/env python3
"""Run real LWJGL3 recovery framebuffer captures, with TCP Xvfb in the same process.

No repository writes, package publication, fake application/UI, or real user saves.
The fixture does not claim a naturally played combat result or Android device run.
"""
from pathlib import Path
import argparse,datetime,hashlib,json,os,shutil,socket,subprocess,tempfile,time

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--repo',type=Path,default=Path('/workspace/scratch/2c6cab9e5294/Catastrophic-Diplomacy'))
    parser.add_argument('--audit',type=Path,default=Path('/workspace/scratch/2c6cab9e5294/audit-work'))
    parser.add_argument('--sizes',nargs='+',default=['1280x720','1560x720'])
    parser.add_argument('--proof',action='store_true')
    args=parser.parse_args()
    out=Path(__file__).resolve().parent
    manifest={'started_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'game_type':'Desktop LWJGL3; software OpenGL in TCP Xvfb',
              'fixture':'Four actual commissioned vessels, controlled finished battle, real temporary save write fault',
              'source_head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=args.repo,text=True).strip(),
              'android_device_claim':False,'mock_panels':False,'sources':{},'classes':{},'runs':[]}
    source_paths=['game/src/main/java/diplomacy/client/CatastrophicGame.java',
                  'game/src/main/java/diplomacy/client/GameMenuPanel.java',
                  'core/src/main/java/diplomacy/sim/CampaignLedger.java']
    for rel in source_paths:
        source=args.repo/rel
        compiled=args.repo/rel.replace('/src/main/java/','/build/classes/java/main/').replace('.java','.class')
        if not args.proof and source.stat().st_mtime>compiled.stat().st_mtime:
            raise SystemExit('Wait for root to compile current source: '+rel)
        manifest['sources'][rel]=digest(source)
        manifest['classes'][str(compiled.relative_to(args.repo))]=digest(compiled)
    snapshot=Path(tempfile.mkdtemp(prefix='compiled-',dir=out))
    cp=[]
    for module in ['core','game','desktop']:
        src=args.repo/module/'build/classes/java/main'
        target=snapshot/module
        shutil.copytree(src,target)
        cp.append(str(target))
    resource=args.repo/'core/build/resources/main'
    shutil.copytree(resource,snapshot/'resources')
    cp += [str(snapshot/'resources'),str(args.audit/'release-resources')]
    cp.append((args.audit/'external-classpath.txt').read_text().strip())
    capture_classes=snapshot/'capture';capture_classes.mkdir()
    cp.insert(0,str(capture_classes))
    classpath=':'.join(cp)
    jdk=args.audit/'deps/jdk-17.0.21+9/bin'
    source=out/'RecoveryVisualCapture.java'
    manifest['harness_sha256']=digest(source)
    compiled=subprocess.run([str(jdk/'javac'),'--release','17','-cp',classpath,'-d',str(capture_classes),str(source)],capture_output=True,text=True)
    manifest['harness_compile_exit']=compiled.returncode
    (out/'harness-compile.log').write_text(compiled.stdout+compiled.stderr)
    if compiled.returncode:raise SystemExit(compiled.stderr)
    env=os.environ.copy();env.update(DISPLAY='127.0.0.1:94',LIBGL_ALWAYS_SOFTWARE='1',ALSOFT_DRIVERS='null')
    max_width=max(int(s.split('x')[0]) for s in args.sizes)
    max_height=max(int(s.split('x')[1]) for s in args.sizes)
    with (out/'xvfb-capture.log').open('w') as xlog:
        xserver=subprocess.Popen([str(args.audit/'deps/xserver/usr/bin/Xvfb'),':94','-screen','0',f'{max_width}x{max_height}x24','-nolisten','unix','-listen','tcp','-ac','-noreset'],stdout=xlog,stderr=subprocess.STDOUT,env=env)
        try:
            ready=False
            for _ in range(40):
                if xserver.poll() is not None:break
                try:
                    with socket.create_connection(('127.0.0.1',6094),.1):ready=True;break
                except OSError:time.sleep(.1)
            if not ready:raise RuntimeError('Xvfb TCP display did not become ready')
            for size in args.sizes:
                width,height=size.split('x')
                runtime=Path(tempfile.mkdtemp(prefix='runtime-'+size+'-',dir=out))
                log=out/('capture-'+size+'.log')
                command=[str(jdk/'java'),'-Xmx1g','-cp',classpath,'diplomacy.client.RecoveryVisualCapture',str(out),width,height]
                if args.proof:command.append('proof')
                with log.open('w') as stream:
                    game=subprocess.run(command,cwd=runtime,env=env,stdout=stream,stderr=subprocess.STDOUT,timeout=90)
                run={'size':size,'game_exit':game.returncode,'runtime':str(runtime),'log':str(log),'screenshots':{}}
                names=['gl-game-proof'] if args.proof else ['settlement-pending','settlement-retry-saved','corrupt-save-confirmation']
                for name in names:
                    image=out/(name+'-'+size+'.png')
                    if image.exists():run['screenshots'][image.name]={'sha256':digest(image),'bytes':image.stat().st_size}
                manifest['runs'].append(run)
                (out/'capture-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
                print(json.dumps(run,indent=2),flush=True)
                if game.returncode or len(run['screenshots'])!=len(names):
                    raise RuntimeError('Capture failed; see '+str(log))
        finally:
            xserver.terminate()
            try:xserver.wait(timeout=5)
            except subprocess.TimeoutExpired:xserver.kill();xserver.wait()
    manifest['finished_utc']=datetime.datetime.now(datetime.timezone.utc).isoformat()
    (out/'capture-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    return 0

if __name__=='__main__':
    raise SystemExit(main())
