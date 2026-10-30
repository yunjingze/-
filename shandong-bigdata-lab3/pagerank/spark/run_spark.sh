#!/bin/bash
# Spark PageRank 一键提交：yarn-client 模式；若失败自动回退 local[*]（结果一致）
set -eo pipefail
cd "$(dirname "$0")"
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export HADOOP_HOME=/usr/local/hadoop
export HADOOP_CONF_DIR=$HADOOP_HOME/etc/hadoop
export SPARK_HOME=/usr/local/spark
export PATH=$JAVA_HOME/bin:$HADOOP_HOME/bin:$SPARK_HOME/bin:$PATH
export PYSPARK_PYTHON=python3
export PYSPARK_DRIVER_PYTHON=python3

echo ">>> 确保数据集已上传到 HDFS /pagerank/input"
hdfs dfs -mkdir -p /pagerank/input
hdfs dfs -put -f ../data/pagerank_input.txt /pagerank/input/
hdfs dfs -rm -r -f /pagerank/output_spark >/dev/null 2>&1 || true

echo ">>> 提交 Spark PageRank（yarn-client 模式，迭代 10 次）"
if spark-submit --master yarn --deploy-mode client \
    --conf spark.executorEnv.PYSPARK_PYTHON=python3 \
    --conf spark.yarn.appMasterEnv.PYSPARK_PYTHON=python3 \
    pagerank_spark.py hdfs://localhost:9000/pagerank/input 2>&1 | tee spark_run.log; then
    echo ">>> 完成（yarn 模式）。日志: spark_run.log"
else
    echo ">>> yarn 模式失败，自动回退 local[*] 模式 ..."
    hdfs dfs -rm -r -f /pagerank/output_spark >/dev/null 2>&1 || true
    spark-submit --master "local[*]" pagerank_spark.py hdfs://localhost:9000/pagerank/input 2>&1 | tee spark_run_local.log
    echo ">>> 完成（local 模式）。日志: spark_run_local.log"
fi
