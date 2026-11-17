# PageRank 实现与复现

本目录包含 Hadoop MapReduce Java 实现、Spark RDD Python 实现和独立矩阵乘法参考实现。两种分布式程序均固定执行 10 轮，输出每个网页的 PR 值并保留 10 位小数。

## 数据与算法口径

`data/graph.tsv` 每行是 `网页<TAB>链接1,链接2`。TAB 是真正的制表符；没有出链的 F 行仍保留 TAB。图包含 8 个节点、10 条不同的有向边：

| 网页 | 出链 |
|---|---|
| A | B,C |
| B | C |
| C | A |
| D | C,E |
| E | D,F |
| F | 空 |
| G | A,H |
| H | 空；仅出现在目标集合，由程序自动补全 |

重复源行合并，重复边去重。网页名称不得为空，不得包含空白、逗号或括号；这些字符用于输入、输出分隔。本实现将所有网页（包括只出现为目标的 H）纳入节点集合。

采用标准概率归一化：初值 `PR₀(v)=1/N`，阻尼系数 `d=0.85`。每轮更新为：

`PRₜ₊₁(v)=(1-d)/N+d×(Σ[u→v] PRₜ(u)/outdegree(u)+Sₜ/N)`

其中 `Sₜ` 是全部悬挂节点的 PR 之和。均匀再分配该质量，使每轮未舍入的 PR 总和保持 1。报告示例只规定输出形式；若使用初值 1 和随机跳转项 0.15，得到的结果恰为本实验结果的 N 倍，不能混用两种口径。程序始终使用双精度数迭代，只在最终输出时格式化为 10 位小数，不逐轮舍入。

## 文件说明

- `mapreduce/src/main/java/edu/sdu/lab3/PageRank.java`：Java MapReduce 主程序。
- `mapreduce/pom.xml`：Maven 构建定义，Hadoop 3.3.6，Java 8 字节码（可用 JDK 11 构建运行）。
- `spark/pagerank_spark.py`：Spark 3.5.7 RDD 程序；使用 Python 3.8 或以上，具体实测版本见环境记录。
- `reference.py`：仅用 Python 标准库的独立转移矩阵验证器，不代表分布式运行。
- `scripts/run_pseudodistributed.sh`：在已启动的伪分布式环境内完成构建、上传、运行、比对。
- `scripts/compare_results.py`：校验节点集合、10 位小数格式、数值误差与 PR 总和。
- `tests/test_reference.py`：环图、全悬挂节点、质量守恒、仅目标节点、重复边、空图检查。
- `results/`：真实运行结果和日志。`reference_*` 明确是本地参考值；MapReduce/Spark 日志由相应引擎生成。

## Linux 伪分布式运行

先依照提交包环境说明启动 NameNode、DataNode、ResourceManager、NodeManager，以及 Spark Master 和至少一个 Worker。Hadoop 的 `mapreduce.framework.name` 必须设为 `yarn`；`core-site.xml` 中配置相同的 HDFS 地址。Hadoop 和 Spark 应使用同一 JDK 11，并确保 `HADOOP_CONF_DIR` 可供 Spark 读取。

```bash
export JAVA_HOME=/path/to/jdk11
export HADOOP_HOME=/path/to/hadoop-3.3.6
export HADOOP_CONF_DIR="$HADOOP_HOME/etc/hadoop"
export SPARK_HOME=/path/to/spark-3.5.7-bin-hadoop3
export SPARK_MASTER=spark://localhost:7077
export PYSPARK_PYTHON=python3
bash scripts/run_pseudodistributed.sh
```

脚本仅创建新的 HDFS 目录，不清空已有数据。重复运行时设置新的 `RUN_ID`。无需预先 `pip install pyspark`，通过发行包的 `spark-submit` 启动即可。结果目录必须尚不存在，这是 Hadoop 和 Spark 输出接口的要求。

单独运行命令：

```bash
mvn -f mapreduce/pom.xml -B package
hadoop jar mapreduce/target/pagerank-mapreduce-1.0.0.jar /your/hdfs/input /your/hdfs/new-mr-output
hdfs dfs -cat /your/hdfs/new-mr-output/result/part-* | sort
spark-submit --master spark://localhost:7077 spark/pagerank_spark.py /your/hdfs/input /your/hdfs/new-spark-output
hdfs dfs -cat /your/hdfs/new-spark-output/part-* | sort
```

MapReduce 先运行图归一化作业，再连续运行 10 个排名迭代作业，最后的格式化作业只输出数值，不再迭代。每轮 Mapper 保留邻接表并发出出链贡献，Reducer 汇总后加入随机跳转项与上一轮悬挂质量；当新 PR 属于悬挂节点时，通过 `MultipleOutputs` 另存，由 Driver 求和供下一轮使用。`iteration-01` 至 `iteration-10` 保留全精度中间状态。没有入链的 G 也有结构记录，不会丢失。Driver 仅收集悬挂节点标量，不收集完整图；本实验数据较小，适用此教学实现。

Spark 将邻接表缓存，利用 `join` 关联旧 PR、`flatMap` 发出贡献、`reduceByKey` 聚合入链贡献，最后使用 `leftOuterJoin` 保留零入度节点。每轮先计算悬挂质量，再物化新 PR，之后释放旧缓存；闭包固定当前轮的跳转项，以避免延迟计算引用错误的循环变量。

## 第十轮参考结果

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

以上是 `reference.py` 实际执行所得的参考结果；分布式结果须以各引擎输出和 `comparison.txt` 为准。排序采用网页名，题目没有要求按 PR 降序排列。第 10 轮只是规定的截断点，不能据此声称已完全收敛。

无需分布式平台的本地数值校验：

```bash
python3 reference.py data/graph.tsv
python3 -m unittest discover -s tests -v
python3 scripts/compare_results.py results/reference_result.txt results/mapreduce_result.txt results/spark_result.txt
```

浮点聚合次序可能使末位出现极小差异，校验允许 `1.01e-10` 的绝对误差；对总质量的容差仅用于最终十位小数舍入累积误差。

## 参考文档

- [Apache Hadoop 3.3.6 MapReduce Tutorial](https://hadoop.apache.org/docs/r3.3.6/hadoop-mapreduce-client/hadoop-mapreduce-client-core/MapReduceTutorial.html)
- [Apache Spark 3.5.7 Documentation](https://spark.apache.org/docs/3.5.7/)
- [Spark RDD Programming Guide](https://spark.apache.org/docs/3.5.7/rdd-programming-guide.html)
