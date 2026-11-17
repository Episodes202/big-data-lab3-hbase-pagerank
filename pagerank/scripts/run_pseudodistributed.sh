#!/usr/bin/env bash
# Run from Linux with Hadoop YARN/HDFS and a Spark standalone worker already started.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${HADOOP_HOME:?Set HADOOP_HOME}"
: "${SPARK_HOME:?Set SPARK_HOME}"
export PATH="$HADOOP_HOME/bin:$SPARK_HOME/bin:$PATH"
SPARK_MASTER="${SPARK_MASTER:-spark://localhost:7077}"
RUN_ID="${RUN_ID:-$(date +%Y%m%d-%H%M%S)}"
BASE="/user/$(id -un)/lab3-pagerank-$RUN_ID"
mkdir -p results

# Each run uses new output paths; existing experiment data are never removed.
if hdfs dfs -test -e "$BASE"; then
  echo "Output already exists: $BASE (choose a different RUN_ID)" >&2
  exit 1
fi
mvn -f mapreduce/pom.xml -B -DskipTests package
hdfs dfs -mkdir -p "$BASE/input"
hdfs dfs -put data/graph.tsv "$BASE/input/graph.tsv"
printf 'HDFS input: %s\nSpark master: %s\n' "$BASE/input" "$SPARK_MASTER" | tee results/run_environment.txt
hadoop jar mapreduce/target/pagerank-mapreduce-1.0.0.jar \
  "$BASE/input" "$BASE/mapreduce" 2>&1 | tee results/mapreduce_run.log
hdfs dfs -cat "$BASE/mapreduce/result/part-*" | LC_ALL=C sort > results/mapreduce_result.txt

spark-submit --master "$SPARK_MASTER" --deploy-mode client \
  --conf spark.executor.cores=1 --conf spark.cores.max=2 \
  spark/pagerank_spark.py "$BASE/input" "$BASE/spark" \
  2>&1 | tee results/spark_run.log
hdfs dfs -cat "$BASE/spark/part-*" | LC_ALL=C sort > results/spark_result.txt
python3 reference.py data/graph.tsv | tee results/reference_run.log
python3 scripts/compare_results.py results/reference_result.txt \
  results/mapreduce_result.txt results/spark_result.txt | tee results/comparison.txt
python3 -m unittest discover -s tests -v 2>&1 | tee results/unit_tests.txt
printf 'Completed exactly 10 iterations per engine. HDFS directory: %s\n' "$BASE"
