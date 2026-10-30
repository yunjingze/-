#!/bin/bash
# 第一部分 Java 程序一键编译运行：
#   HBaseOperation —— 建 Student/Course/SC 表 + 导数 + createTable/addRecord/scanColumn/modifyData/deleteRow 演示
#   HBaseTask      —— 5 个任务（list/scan/加减列族列/truncate/count）的 Java API 实现
#   最后恢复数据
set -eo pipefail
cd "$(dirname "$0")"
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export HBASE_HOME=/usr/local/hbase
export PATH=$JAVA_HOME/bin:$HBASE_HOME/bin:$PATH

CP=$(hbase classpath)
echo ">>> 编译 HBaseOperation.java / HBaseTask.java ..."
javac -encoding UTF-8 -cp "$CP" HBaseOperation.java HBaseTask.java

echo
echo ">>> [1/3] HBaseOperation：建表、导入关系数据、演示5个函数"
java -cp "$CP:." HBaseOperation 2>&1 | tee java_operation.log

echo
echo ">>> [2/3] HBaseTask：5个任务（列表/浏览/加减列族列/统计/清空）"
java -cp "$CP:." HBaseTask 2>&1 | tee java_task.log

echo
echo ">>> [3/3] 恢复数据（重建三表）"
java -cp "$CP:." HBaseOperation restore 2>&1 | tee java_restore.log

echo
echo ">>> 全部完成。日志: java_operation.log / java_task.log / java_restore.log"
