@echo off
setlocal
cd /d "%~dp0"
java -Djava.awt.headless=true -Xmx1g -jar build\dedicated-server.jar %*
