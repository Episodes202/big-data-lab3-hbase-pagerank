#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$HERE/env.sh"
python3 "$HERE/configure.py"
if [[ ! -f "$LAB_RUNTIME/data/hadoop/name/current/VERSION" ]]; then
  hdfs namenode -format -nonInteractive >"$HADOOP_LOG_DIR/format.log" 2>&1
fi
setsid hdfs --daemon start namenode
setsid hdfs --daemon start datanode
setsid yarn --daemon start resourcemanager
setsid yarn --daemon start nodemanager
setsid mapred --daemon start historyserver
setsid "$HBASE_HOME/bin/hbase-daemon.sh" start master
"$SPARK_HOME/sbin/start-master.sh" --host 127.0.0.1 --port 17077 --webui-port 18080
"$SPARK_HOME/sbin/start-worker.sh" --cores 2 --memory 1g --webui-port 18081 spark://127.0.0.1:17077
echo 'Services launched. Check hdfs dfsadmin -report, yarn node -list, jps.'
