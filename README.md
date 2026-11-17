# HBase 操作与 PageRank 算法实验

本项目包含 HBase Java API 与等价 Shell 操作，以及 Hadoop MapReduce、Spark RDD 两套 PageRank 实现。提供输入数据、独立矩阵参考算法、10轮实测结果、自动核验和独立 Linux 环境脚本。

## 实测情况

- HBase 2.5.11 独立服务：Java 12项检查通过，Shell等价操作执行成功。
- Hadoop 3.3.6：真实 HDFS 与 YARN 单机伪分布式运行，规范化、10轮迭代、最终格式化共12个作业全部成功。
- Spark 3.5.7：真实独立 Master/Worker 运行10轮，输入与输出均存储于 HDFS。
- 两套 PageRank 最终结果与独立参考计算在10位小数上逐项一致；10轮 MapReduce 中间状态也逐项核对通过。
- 系统为 Ubuntu 24.04.3 LTS（WSL2），JDK 11.0.29，Maven 3.9.9，Python 3.12.3。完整模式说明见 [environment/README.md](environment/README.md)。

## 目录

|目录|内容|
|---|---|
|[hbase](hbase/README.md)|Java API、等价Shell、原始及工作数据、验证摘要|
|[pagerank](pagerank/README.md)|MapReduce、Spark、独立参考实现、测试及结果|
|[environment](environment/README.md)|隔离的Linux运行环境安装、配置、启动、检查和停止脚本|
|[results](results/verification_summary.json)|机器可读验证摘要、环境版本、发布包校验值|

仓库保留源码与紧凑结果；完整服务和任务日志随实验提交包保存。运行脚本会重新生成相应日志。运行时发行包与编译产物由脚本获取或构建。

## 运行

在仓库根目录执行，Linux需要 bash、curl、tar、Python3 和可访问软件仓库的网络：

```bash
bash environment/install.sh
source environment/env.sh
bash environment/start.sh
bash environment/check.sh
bash hbase/scripts/run_java.sh
bash hbase/scripts/run_shell.sh
SPARK_MASTER=spark://127.0.0.1:17077 bash pagerank/scripts/run_pseudodistributed.sh
bash environment/stop.sh
```

默认运行时目录为 `/tmp/codex-pagerank-runtime`。可在安装前设置 `LAB_RUNTIME` 为专用持久目录，后续命令使用相同值。HBase演示会按实验要求重建 Student、Course、SC、Lab3Scratch；请在专用实验环境执行。PageRank每次创建新的HDFS结果目录，拒绝覆盖已有输出。

不启动分布式服务也可核对参考结果：

```bash
cd pagerank
python3 reference.py data/graph.tsv
python3 -m unittest discover -s tests -v
python3 scripts/compare_results.py results/reference_result.txt results/mapreduce_result.txt results/spark_result.txt
```

## 算法与数据约定

输入每行是 `网页<TAB>出链1,出链2`。初值为 `1/N`，阻尼系数0.85，固定迭代10轮；悬挂节点的PR质量均匀分配给全部节点。补全仅作为链接目标出现的节点，保留零入度节点，重复边去重，最终输出 `(网页,PR值)` 并保留10位小数。

示例包含8个节点，原文件7行，H仅作为目标出现。第10轮结果为：

```text
(A,0.3084571706)
(B,0.1590249346)
(C,0.3150293797)
(D,0.0495579625)
(E,0.0495579625)
(F,0.0495579625)
(G,0.0283624204)
(H,0.0404522074)
```

未舍入PR总和约为1，展示值由于舍入合计1.0000000002。第10轮是规定的截断点，不表示完全收敛。

原题Student数据存在重复学号；仓库保留 `Student_original.tsv`，工作副本对Mary采用学号2015002的一致性假设。该假设与转换逻辑详见HBase说明。
