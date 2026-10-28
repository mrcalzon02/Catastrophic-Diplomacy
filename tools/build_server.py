import pathlib,subprocess,os,shutil
root=pathlib.Path(__file__).resolve().parents[1]
jdk=pathlib.Path(os.environ['JAVA_HOME'])/'bin' if 'JAVA_HOME' in os.environ else pathlib.Path(shutil.which('javac')).resolve().parent
suffix='.exe' if os.name=='nt' else ''
out=root/'build/server-classes';out.mkdir(parents=True,exist_ok=True)
sources=list((root/'core/src/main/java').rglob('*.java'))+list((root/'server/src/main/java').rglob('*.java'))
argfile=root/'build/server-sources.txt';argfile.write_text('\n'.join('"'+str(p.relative_to(root)).replace('\\','/')+'"' for p in sources))
subprocess.run([str(jdk/('javac'+suffix)),'--release','17','-encoding','UTF-8','-d',str(out),'@'+str(argfile)],cwd=root,check=True)
subprocess.run(['python',str(root/'tools/verify_hbnet.py')],cwd=root,check=True)
command=[str(jdk/('jar'+suffix)),'--create','--file',str(root/'build/dedicated-server.jar'),'--main-class','diplomacy.server.BattleServer','-C',str(out),'.']
command+=['-C',str(root/'core/src/main/resources'),'.']
subprocess.run(command,cwd=root,check=True)
print('PASS Java 17 dedicated-server.jar built without GPU, model export or a display')
