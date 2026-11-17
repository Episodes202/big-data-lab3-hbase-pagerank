#!/usr/bin/env bash
# Source this file after unpacking the runtime archives listed in README.md.
export LAB_RUNTIME="${LAB_RUNTIME:-/tmp/codex-pagerank-runtime}"
export JAVA_HOME="$LAB_RUNTIME/zulu11.84.17-ca-jdk11.0.29-linux_x64"
export HADOOP_HOME="$LAB_RUNTIME/hadoop-3.3.6"
export HADOOP_CONF_DIR="$HADOOP_HOME/etc/hadoop"
export HBASE_HOME="$LAB_RUNTIME/hbase-2.5.11"
export SPARK_HOME="$LAB_RUNTIME/spark-3.5.7-bin-hadoop3"
export PATH="$JAVA_HOME/bin:$LAB_RUNTIME/apache-maven-3.9.9/bin:$HADOOP_HOME/bin:$HBASE_HOME/bin:$SPARK_HOME/bin:$PATH"
export JAVA_TOOL_OPTIONS="-XX:ActiveProcessorCount=4"
export HADOOP_LOG_DIR="$LAB_RUNTIME/logs/hadoop"
export HADOOP_PID_DIR="$LAB_RUNTIME/pids/hadoop"
export HBASE_LOG_DIR="$LAB_RUNTIME/logs/hbase"
export HBASE_PID_DIR="$LAB_RUNTIME/pids/hbase"
export SPARK_LOG_DIR="$LAB_RUNTIME/logs/spark"
export SPARK_PID_DIR="$LAB_RUNTIME/pids/spark"
export SPARK_LOCAL_IP=127.0.0.1
export PYSPARK_PYTHON=python3
