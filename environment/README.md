# Linux 实验环境

本包在 Ubuntu 24.04.3（Windows WSL2，Linux 6.6.87.2）实际运行。系统可见 32 个逻辑 CPU、7840 MiB 内存；Java 的 ActiveProcessorCount 限为 4，Spark Worker 配置 2 核、1 GiB。作业数据保存在真正的 HDFS 中，MapReduce 使用 YARN 提交，Spark 使用独立 Master/Worker，均为单机伪分布式环境。HBase 第一部分使用真实 HBase standalone 服务，内嵌 ZooKeeper/RegionServer，数据位于独立本地文件系统目录，并非 HBase 分布式集群。

## 软件版本

|软件|版本|
|---|---|
|JDK|Azul Zulu OpenJDK 11.0.29，zulu11.84.17|
|Hadoop|3.3.6|
|HBase|2.5.11|
|Spark|3.5.7，bin-hadoop3|
|Maven|3.9.9|
|Python|Ubuntu 自带 Python 3.12|

## 安装

Linux 需要 bash、curl、tar、Python3，以及可访问软件仓库的网络。以下下载均为官方发布文件；Apache 存档可用华为镜像加速，脚本中的 Apache SHA512 已与官方公布值核对，JDK SHA512 固定为本次从 Azul CDN 取得文件的值。运行时二进制合计较大，不放入提交压缩包。

```bash
bash environment/install.sh
source environment/env.sh
bash environment/start.sh
bash environment/check.sh
```

默认安装目录为 `/tmp/codex-pagerank-runtime`，可事先 `export LAB_RUNTIME="$PWD/.runtime"` 使用持久目录。所有脚本在同一个 `LAB_RUNTIME` 值下执行；配置文件会写入此目录内的专用安装。不要将 LAB_RUNTIME 指向正在使用的共享集群安装。脚本仅在未发现 NameNode VERSION 时首次格式化自己的 NameNode；重复执行不会清空已有 HDFS。

若系统原有这几项软件，可按 `env.sh` 修改各 HOME 变量并使用自己的已启动服务；此时应沿用已有配置，勿运行 `configure.py`。新环境启动后通常需要数秒使服务就绪，`hdfs dfsadmin -report` 应显示 1 个 Live datanode，`yarn node -list` 应显示 1 个 RUNNING 节点，Spark JSON 应显示 ALIVE Worker。

## 实际运行命令

在提交包根目录执行：

```bash
source environment/env.sh
bash hbase/scripts/run_java.sh
bash hbase/scripts/run_shell.sh
SPARK_MASTER=spark://127.0.0.1:17077 bash pagerank/scripts/run_pseudodistributed.sh
```

HBase 演示按题意重新创建专用教学表 Student、Course、SC 和 Lab3Scratch；请在本包独立实验环境中运行。PageRank 每次使用时间戳创建新的 HDFS 输入输出目录，不覆盖已有实验结果。

`results/environment_check.log` 记录实际 OS、版本、进程、HDFS 和 YARN 状态。`pagerank/results/mapreduce_run.log` 中的 job/application 编号证明使用 YARN；Spark 日志中的 `Connected to Spark cluster` 和 `spark://127.0.0.1:17077` 对应真实 Master/Worker。日志中的 SLF4J 多绑定及 native-hadoop 警告不影响本次运行，是否成功以退出码、任务状态与结果比较为准。

## 端口和模式

|用途|本机端口|
|---|---|
|HDFS NameNode RPC / Web|19000 / 19870|
|DataNode data / Web / IPC|19866 / 19864 / 19867|
|YARN ResourceManager RPC / Web|18032 / 18088|
|NodeManager / Web|18040 / 18042|
|MapReduce JobHistory Web|19888|
|HBase ZooKeeper / Master Web|12181 / 16010|
|Spark Master RPC / Web|17077 / 18080|
|Spark Worker Web|18081|

这些端口应在运行前空闲。服务以 `setsid` 启动，避免 WSL 调用会话结束时的 SIGHUP 让后台 JVM 退出。实验结束运行 `bash environment/stop.sh`；只停止本目录记录的实验进程，保留数据和日志，不关闭 WSL，也不修改系统级配置。
