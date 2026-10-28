from pathlib import Path
import subprocess,socket,time,tempfile,os,json,sys
r=Path(__file__).resolve().parents[1].parent
import shutil
jdk=Path(os.environ['JAVA_HOME'])/'bin' if 'JAVA_HOME' in os.environ else Path(shutil.which('javac')).resolve().parent
java=str(jdk/('java.exe' if os.name=='nt' else 'java'))
game=sys.argv[1];repo=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='multiplayer-process-') as tmp:
    with socket.socket() as s:s.bind(('127.0.0.1',0));port=s.getsockname()[1]
    server_args=([java,'-Dveil.bind=127.0.0.1','-cp','build/classes','veil.LanServer',str(port),'92233',tmp+'/world'] if game=='veil' else [java,'-cp',os.pathsep.join(['build/classes','core/src/main/resources']),'diplomacy.server.OpenLanBattleServer','127.0.0.1',str(port),'16'])
    server_log=Path(tmp)/'server.log'
    with server_log.open('w') as f:
        host=subprocess.Popen(server_args,cwd=repo,stdin=subprocess.PIPE,stdout=f,stderr=subprocess.STDOUT,text=True)
        try:
            # Read the explicit startup receipt, never probe a public endpoint or allocate a phantom player.
            deadline=time.monotonic()+12
            while time.monotonic()<deadline:
                text=server_log.read_text(errors='replace')
                if ('private dedicated host ready' if game=='veil' else 'OPEN_LAN:') in text:break
                if host.poll() is not None:raise RuntimeError('Host startup failed')
                time.sleep(.05)
            else:raise RuntimeError('Host startup deadline')
            clients=[]
            for i in range(2):
                cp='build/classes' if game=='veil' else os.pathsep.join(['build/classes','core/src/main/resources'])
                name='veil.LanSocketProbe' if game=='veil' else 'diplomacy.server.BattleSocketProbe'
                clients.append(subprocess.Popen([java,'-Duser.home='+tmp+'/player'+str(i),'-cp',cp,name,str(port)],cwd=repo,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True))
            results=[]
            for client in clients:
                out,_=client.communicate(timeout=20);results.append({'exit':client.returncode,'output':out.strip()})
            host.stdin.write('shutdown\n' if game=='veil' else 'stop\n');host.stdin.flush();host.wait(timeout=12)
            if any(x['exit'] for x in results) or host.returncode:raise RuntimeError(json.dumps(results))
            with socket.socket() as verify:verify.bind(('127.0.0.1',port))
            receipt={'game':game,'mode':'explicit trusted loopback, no TLS claim','clients':results,'server_exit':host.returncode,'port_released':True}
            output=repo/'build/network-checks/processes.json';output.write_text(json.dumps(receipt,indent=2));print(json.dumps(receipt))
        finally:
            if host.poll() is None:host.kill();host.wait()
