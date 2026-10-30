# 实验三 PageRank 算法实现

《大数据管理与分析》实验代码：第一部分为 HBase 常用操作（Shell 与 Java API），第二部分分别用 MapReduce 和 PySpark 实现 PageRank 算法。

## 目录结构

```
shandong-bigdata-lab3/
├── hbase/
│   ├── java/
│   │   ├── HBaseOperation.java   # 五个功能: createTable/addRecord/scanColumn/modifyData/deleteRow；建表导数；restore 恢复模式
│   │   ├── HBaseTask.java        # Java API 完成5任务: 列表/扫描/增删列族列/计数/清空表
│   │   └── build_and_run.sh      # 一键编译运行（结束后自动恢复被清空的数据）
│   └── shell/
│       ├── hbase_shell_demo.hbs  # 与5任务等价的 HBase Shell 脚本
│       └── run_shell_demo.sh     # 管道方式执行脚本并存日志，随后恢复数据
└── pagerank/
    ├── data/pagerank_input.txt   # 自制数据集: 12网页, 每行 "网页\t出链集合(逗号分隔)"
    ├── mapreduce/
    │   ├── PageRankMR.java       # MapReduce 实现, 驱动连续迭代10次并按 PR 降序输出
    │   └── build_and_run.sh      # 上传数据→编译打包→YARN 运行→打印结果
    └── spark/
        ├── pagerank_spark.py     # PySpark 实现 (links 缓存内存迭代10轮)
        └── run_spark.sh          # spark-submit (yarn client, 失败自动回退 local)
```

## 运行环境

Ubuntu 22.04 + JDK 11 + Hadoop 3.3.6（伪分布式 HDFS/YARN）+ HBase 2.5.16 + Spark 3.5.9 + Python 3。
脚本默认 JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64、HADOOP_HOME=/usr/local/hadoop、HBASE_HOME=/usr/local/hbase、SPARK_HOME=/usr/local/spark，如安装位置不同请自行修改脚本头部变量。
运行前先启动 Hadoop（`start-dfs.sh && start-yarn.sh`）与 HBase（`start-hbase.sh`）。

## 第一部分：HBase 常用操作

表设计：

| 表 | RowKey | 列族 |
| --- | --- | --- |
| Student | S_Name | S_No、S_Sex、S_Age |
| Course | C_No | C_Name、C_Credit |
| SC | S_Name | Score（列限定符为课程名） |

```bash
cd hbase/java && ./build_and_run.sh     # Java API: 建表导数、5功能、5任务、恢复数据
cd hbase/shell && ./run_shell_demo.sh   # HBase Shell 完成相同5任务
```

输出要点：`scanColumn` 浏览 `Score:Math` 时 Mary 为 null（未选该课）；`modifyData` 将 Zhangsan 的 S_Age 由 23 改为 24；任务4 truncate 后 count 由 3 变 0，脚本随后自动恢复数据。

## 第二部分：PageRank

迭代公式 PR(u)=(1-d)+d·Σ PR(v)/L(v)，d=0.85，初始 PR=1.0，连续迭代 10 次，按 (网页,PR值) 输出、PR 保留 10 位小数。

```bash
cd pagerank/mapreduce && ./build_and_run.sh   # MapReduce 版（YARN 上 10 个 Job）
cd pagerank/spark && ./run_spark.sh           # Spark 版
```

两程序最终输出逐行一致：

```
(Home,2.9855738333)     (Support,1.4636553560)    (Contact,1.1377735642)
(Blog,1.0654947917)     (About,0.9597028556)      (News,0.9597028556)
(Products,0.8447532018) (Login,0.7513378531)      (FAQ,0.6185237448)
(Docs,0.5549935519)     (Download,0.3292441961)   (Shop,0.3292441961)
```

## 运行提示

- 若 HBase Master 初始化卡住（status 报 ServerNotRunningYet），可在 hbase-site.xml 增加 `hbase.wal.provider=filesystem` 绕过 AsyncFSWAL 的兼容性问题。
- PageRankMR.jar 清单无 Main-Class，手动运行时需显式给出主类名：`hadoop jar PageRankMR.jar PageRankMR <输入> <输出>`。
