#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$HERE/env.sh"
"$SPARK_HOME/sbin/stop-worker.sh"
"$SPARK_HOME/sbin/stop-master.sh"
"$HBASE_HOME/bin/hbase-daemon.sh" stop master
mapred --daemon stop historyserver
yarn --daemon stop nodemanager
yarn --daemon stop resourcemanager
hdfs --daemon stop datanode
hdfs --daemon stop namenode
echo 'Lab processes stopped; files and logs preserved.'
