#!/usr/bin/env bash
# Usage: bash environment/export_iterations.sh <HDFS MapReduce output directory>
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$HERE/env.sh"
BASE="${1:?Pass the MapReduce HDFS output directory}"
DEST="$HERE/../pagerank/results/mapreduce_iterations"
mkdir -p "$DEST"
for i in $(seq 1 10); do
  number="$(printf '%02d' "$i")"
  hdfs dfs -cat "$BASE/iteration-$number/part-r-*" | LC_ALL=C sort > "$DEST/iteration-$number.tsv"
done
printf '%s\n' "$BASE" > "$DEST/hdfs_source.txt"
