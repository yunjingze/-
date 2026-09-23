#!/bin/bash
# 编译三个 MapReduce 程序并打包成 jar
# 用法：bash build.sh   （需要在虚拟机 Linux 中执行，且 Hadoop 环境变量正确）
set -e

export HADOOP_HOME=${HADOOP_HOME:-/usr/local/hadoop}
export PATH=$PATH:$HADOOP_HOME/bin:$HADOOP_HOME/sbin

if ! command -v hadoop >/dev/null 2>&1; then
    echo "[错误] 找不到 hadoop 命令，请检查 HADOOP_HOME 与 PATH 设置"
    exit 1
fi
if ! command -v javac >/dev/null 2>&1; then
    echo "[错误] 找不到 javac，请确认已安装 JDK 并配置 PATH"
    exit 1
fi

export CLASSPATH=$(hadoop classpath)
echo "== 编译 Dedup =="
javac Dedup.java && jar cf dedup.jar Dedup*.class
echo "== 编译 Sort =="
javac Sort.java && jar cf sort.jar Sort*.class
echo "== 编译 GrandParents =="
javac GrandParents.java && jar cf grandparents.jar GrandParents*.class

echo "构建完成，已生成：dedup.jar  sort.jar  grandparents.jar"
ls -l *.jar