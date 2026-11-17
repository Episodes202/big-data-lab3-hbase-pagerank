#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
: "${HBASE_HOME:?Set HBASE_HOME to the HBase 2.5 installation directory}"
mkdir -p "$PROJECT_DIR/results"
cd "$PROJECT_DIR"
mvn -B -DskipTests package 2>&1 | tee results/build.log
export HBASE_CLASSPATH="$PROJECT_DIR/target/hbase-lab-1.0.0.jar${HBASE_CLASSPATH:+:$HBASE_CLASSPATH}"
"$HBASE_HOME/bin/hbase" cn.edu.lab.HBaseLab demo 2>&1 | tee results/java-demo.log
grep -q '^ALL HBASE CHECKS PASSED$' results/java-demo.log
echo 'HBase Java demo completed and all checks passed.'
