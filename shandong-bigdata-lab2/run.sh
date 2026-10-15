#!/bin/bash
# 实验二：倒排索引 MapReduce 作业运行脚本（在 Hadoop 伪分布式节点终端执行）
set -e
APP=iindex.jar
HDFS_IN=/iindex/input
HDFS_STOP=/iindex/stopwords.txt
HDFS_INTER=/iindex/inter
HDFS_OUT=/iindex/output

# 1) 准备 HDFS 目录，上传 10 个英文文档与停词表
hadoop fs -rm -r -f $HDFS_INTER $HDFS_OUT || true
hadoop fs -rm -r -f $HDFS_IN || true
hadoop fs -mkdir -p $HDFS_IN
hadoop fs -put -f data/*.txt $HDFS_IN
hadoop fs -put -f stopwords.txt $HDFS_STOP

# 2) 编译打包
mkdir -p build
javac -cp "$(hadoop classpath)" -d build src/InvertedIndex.java
jar -cf $APP -C build .

# 3) 提交两段式作业：Job1 构建倒排，Job2 停词过滤并统计 total
hadoop jar $APP InvertedIndex $HDFS_IN $HDFS_STOP $HDFS_INTER $HDFS_OUT

# 4) 查看结果
echo "===== 倒排索引结果（前 20 行）====="
hadoop fs -cat $HDFS_OUT/part-r-00000 | head -20
echo "===== 索引词条总数 ====="
hadoop fs -cat $HDFS_OUT/part-r-00000 | wc -l
