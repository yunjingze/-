#!/bin/bash
# 一键运行 HBase Shell 5任务演示：输出保存到 shell_output.log，结束后自动恢复 Student 数据
set -eo pipefail
cd "$(dirname "$0")"
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export HBASE_HOME=/usr/local/hbase
export PATH=$JAVA_HOME/bin:$HBASE_HOME/bin:$PATH

echo ">>> 以管道方式运行 hbase_shell_demo.hbs （日志: shell_output.log）"
cat hbase_shell_demo.hbs | hbase shell 2>&1 | tee shell_output.log

echo ">>> truncate 后 Student 表已清空，现在恢复数据 ..."
cd ../java
CP=$(hbase classpath)
[ -f HBaseOperation.class ] || javac -encoding UTF-8 -cp "$CP" HBaseOperation.java
java -cp "$CP:." HBaseOperation restore 2>&1 | grep -vE "WARN|INFO" || true
echo ">>> 完成。shell 演示日志: ../shell/shell_output.log"
