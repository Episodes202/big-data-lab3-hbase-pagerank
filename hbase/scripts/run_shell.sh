#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
: "${HBASE_HOME:?Set HBASE_HOME to the HBase 2.5 installation directory}"
mkdir -p "$PROJECT_DIR/results"
"$HBASE_HOME/bin/hbase" shell -n "$SCRIPT_DIR/load_and_operations.hbase" 2>&1 | tee "$PROJECT_DIR/results/shell-demo.log"
