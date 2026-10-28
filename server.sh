#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
exec java -Djava.awt.headless=true -Xmx1g -jar build/dedicated-server.jar "$@"
