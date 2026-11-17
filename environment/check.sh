#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$HERE/env.sh"
date --iso-8601=seconds
uname -a
cat /etc/os-release
printf '\nAvailable CPU cores: '
nproc
free -m
java -version
mvn -version
hadoop version
"$HBASE_HOME/bin/hbase" version
spark-submit --version
jps
hdfs dfsadmin -report
yarn node -list
curl --fail -s http://127.0.0.1:18080/json/
