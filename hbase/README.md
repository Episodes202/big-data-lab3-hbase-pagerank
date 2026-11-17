# HBase 操作实验

姓名：赵君豪；学号：202400390041；班级：1班。

使用 HBase 2.5.11 Java Client，源代码兼容 Java 8 及以上；建议与实验环境统一使用 JDK 11、Maven 3.9.9。程序依赖真实 HBase 服务，不使用内存模拟数据库。

## 数据与建表

原题 Student 表将 Mary、Lisi 的学号都写为 `2015003`，但 SC 表中有 `2015002` 的两条选课记录。`data/Student_original.tsv` 完整保留题面。实验工作数据采用 **Mary 学号为 2015002** 的一致性假设；这一修正只影响学生身份关联，不改变课程、成绩、性别、年龄等值。该假设尚未得到出题教师确认，若要求严格按原表存储，可把 Java `loadData` 和 shell 文件中 Mary 的 `Info:S_No` 改回 `2015003`，但 SC 的 `2015002` 将无法关联到学生。这里不对原始文件作覆盖。

| HBase 表 | 行键 | 列族及限定符 | 行数 |
|---|---|---|---:|
| Student | S_Name | Info:S_No、Info:S_Name、Info:S_Sex、Info:S_Age；Score:课程名 | 3 |
| Course | C_No | Info:C_No、Info:C_Name、Info:C_Credit | 3 |
| SC | SC_Sno_SC_Cno | Info:SC_Sno、Info:SC_Cno、Info:SC_Score | 6 |

Student 的 Score 列族按姓名反规范化存储成绩，满足题目示例；Course 和 SC 保留独立记录，便于核对原表。实际应用中姓名可能重名，宜采用学号等稳定唯一键，本实验依题意使用姓名。

## 运行

先按提交包 `environment/README.md` 启动 Linux 实验环境，设置 `JAVA_HOME`、`HADOOP_HOME`、`HBASE_HOME`，并确保 `hbase-site.xml` 指向当前服务。本次 HBase 实测采用 standalone 模式、本地文件系统存储、内嵌 ZooKeeper（端口 12181）；Hadoop 和 Spark 的伪分布式服务独立运行。下列两个入口会重建实验表 **Student、Course、SC、Lab3Scratch**，请使用专用实验环境。

```bash
cd hbase
source ../environment/env.sh
bash scripts/run_java.sh
bash scripts/run_shell.sh
```

Java 的 `HBaseConfiguration.create()` 自动读取 HBase 配置目录中的 `hbase-site.xml`。必要时可以设置环境变量 `HBASE_ZOOKEEPER_QUORUM=localhost`。`run_java.sh` 先编译普通 JAR，再借助 HBase 自带的类路径启动；无须额外复制依赖 JAR。完整控制台输出分别保存在 `results/build.log`、`results/java-demo.log`、`results/shell-demo.log`。

保留题目要求的三参数修改方法，在标准输入中读取新值；演示程序使用四参数重载，避免脚本停留在输入提示处。交互示例：

```bash
export HBASE_CLASSPATH="$PWD/target/hbase-lab-1.0.0.jar"
"$HBASE_HOME/bin/hbase" cn.edu.lab.HBaseLab modify Student Zhangsan Score:Math
# 按提示输入 90，再回车。
```

## Java API 与 Shell 对照

| 功能 | Java 方法 | HBase Shell |
|---|---|---|
| 列出表及列族 | listTables | list；describe 'Student' |
| 扫描全部记录 | scanTable | scan 'Student' |
| 新增列族 | addFamily | alter 'Student', {NAME => 'Temp'} |
| 新增单元格 | addRecord | put 'Student', 'Zhangsan', 'Temp:note', 'demo' |
| 删除单元格全部版本 | deleteColumn | deleteall 'Student', 'Zhangsan', 'Temp:note' |
| 删除列族 | deleteFamily | alter 'Student', {NAME => 'Temp', METHOD => 'delete'} |
| 清空表并保留模式 | clearTable | truncate 'Lab3Scratch' |
| 统计行数 | countRows | count 'Student' |
| 重建表 | createTable | disable；drop；create |
| 浏览列族或具体列 | scanColumn | scan，使用 COLUMNS 选项 |
| 修改值 | modifyData | put 同一行键和列名 |
| 删除整行 | deleteRow | deleteall 'Student', 'Temporary' |

`createTable` 接受列族名或 `family:qualifier`，提取并去重列族；HBase 限定符随写入动态创建。`addRecord` 检查 fields/values 长度；若字段仅给列族，则写入空限定符。`scanColumn` 返回每个现有行键的结果，缺失目标列/列族时返回 Java `null`。它先扫描全行再筛选，避免投影扫描直接省略缺列行。例如 `Score:Math` 应得到 Lisi=98、Mary=null、Zhangsan=86。HBase Shell 的 `scan ... COLUMNS` 只显示存在值的行；对 Mary 执行 `get` 会返回 0 个单元格，语义上对应缺失值。

## 自动核验

演示程序通过真实服务执行以下检查，断言失败会以非零退出码终止：三张表行数 3/3/6；缺失列返回 null；初始成绩；修改并恢复成绩；添加/删除列限定符；添加/删除列族；删除临时行；清空表后行数为 0；对已有表再次 createTable 后旧数据消失。全部成功时输出 `ALL HBASE CHECKS PASSED`。正式样本成绩最终保持题面值，破坏性清空操作在 Lab3Scratch 上演示。

2026-11-17 在 WSL Ubuntu、JDK 11.0.29、HBase 2.5.11 中实际执行：Java 12 条断言全部通过，Shell 批处理退出码为 0，Student/Course/SC 行数分别为 3/3/6，清空后的 Lab3Scratch 为 0 行。原始输出保存在本目录 `results/java-demo.log`、`results/shell-demo.log`，编译日志为 `results/build.log`。

## 参考

- Apache HBase 2.5 Admin API：https://hbase.apache.org/2.5/apidocs/org/apache/hadoop/hbase/client/Admin.html
- Apache HBase 2.5 Table API：https://hbase.apache.org/2.5/apidocs/org/apache/hadoop/hbase/client/Table.html
- Apache HBase Reference Guide：https://hbase.apache.org/book.html
