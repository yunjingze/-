#!/bin/bash
# PageRank MapReduce 一键：上传数据 → 编译打包 → YARN 上跑 10 次迭代 → 输出结果
set -eo pipefail
cd "$(dirname "$0")"
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export HADOOP_HOME=/usr/local/hadoop
export PATH=$JAVA_HOME/bin:$HADOOP_HOME/bin:$HADOOP_HOME/sbin:$PATH

echo ">>> [1/3] 上传数据集到 HDFS /pagerank/input"
hdfs dfs -mkdir -p /pagerank/input
hdfs dfs -put -f ../data/pagerank_input.txt /pagerank/input/
echo "数据集前3行："
hdfs dfs -cat /pagerank/input/pagerank_input.txt | head -3

echo ">>> [2/3] 编译打包 PageRankMR.jar"
rm -rf classes && mkdir classes
javac -encoding UTF-8 -cp "$(hadoop classpath)" -d classes PageRankMR.java
jar cf PageRankMR.jar -C classes .

echo ">>> [3/3] 运行 PageRank：YARN 伪分布式，连续迭代 10 次"
hdfs dfs -rm -r -f /pagerank/output_mr >/dev/null 2>&1 || true
hadoop jar PageRankMR.jar PageRankMR /pagerank/input /pagerank/output_mr 2>&1 | tee mr_run.log

echo
echo ">>> 完成。最终结果（也保存在 HDFS /pagerank/output_mr/result.txt）："
hdfs dfs -cat /pagerank/output_mr/result.txt
