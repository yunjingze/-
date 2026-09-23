#!/bin/bash
# 运行三个 MapReduce 程序（需先执行 build.sh 完成编译，并已启动 Hadoop）
# 用法：bash run_all.sh
# 说明：
#   1) 脚本会把 data/ 下的输入文件上传到 HDFS 的用户目录并依序运行三个任务；
#   2) 若你的 Linux 用户名不是 lixinze，请修改下面的 MY_USER；
#   3) 输出目录会自动清理旧结果，可反复运行。
set -e

export HADOOP_HOME=${HADOOP_HOME:-/usr/local/hadoop}
export PATH=$PATH:$HADOOP_HOME/bin:$HADOOP_HOME/sbin

MY_USER=${MY_USER:-lixinze}        # 改成你的 Linux 用户名
LOCAL_DATA="$(cd "$(dirname "$0")" && pwd)/../data"   # 本机输入文件目录

HDFS_ROOT="/user/$MY_USER/lab1"
RUN() {
    echo; echo "############################################################"; echo "# $1"; echo "############################################################"; echo
    shift
    "$@"
}

echo "检查 Hadoop 是否已启动（应能看到 5 个进程）..."
if ! jps | grep -q NameNode; then
    echo "[提示] NameNode 未运行，请先执行： start-dfs.sh && start-yarn.sh"
    exit 1
fi

# 1) 合并去重 Dedup
hdfs dfs -rm -r -f "$HDFS_ROOT/input_dedup" "$HDFS_ROOT/out_dedup" >/dev/null 2>&1 || true
hdfs dfs -mkdir -p "$HDFS_ROOT/input_dedup"
hdfs dfs -put "$LOCAL_DATA/A.txt" "$LOCAL_DATA/B.txt" "$HDFS_ROOT/input_dedup/"
RUN "任务1：合并去重 Dedup" hadoop jar dedup.jar Dedup "$HDFS_ROOT/input_dedup" "$HDFS_ROOT/out_dedup"
echo "----- 输出文件 C 内容 -----"
hdfs dfs -cat "$HDFS_ROOT/out_dedup/part-r-00000"

# 2) 排序 Sort
hdfs dfs -rm -r -f "$HDFS_ROOT/input_sort" "$HDFS_ROOT/out_sort" >/dev/null 2>&1 || true
hdfs dfs -mkdir -p "$HDFS_ROOT/input_sort"
hdfs dfs -put "$LOCAL_DATA/file1.txt" "$LOCAL_DATA/file2.txt" "$LOCAL_DATA/file3.txt" "$HDFS_ROOT/input_sort/"
RUN "任务2：排序 Sort" hadoop jar sort.jar Sort "$HDFS_ROOT/input_sort" "$HDFS_ROOT/out_sort"
echo "----- 排序输出内容 -----"
hdfs dfs -cat "$HDFS_ROOT/out_sort/part-r-00000"

# 3) 祖孙关系挖掘 GrandParents
hdfs dfs -rm -r -f "$HDFS_ROOT/input_family" "$HDFS_ROOT/out_family" >/dev/null 2>&1 || true
hdfs dfs -mkdir -p "$HDFS_ROOT/input_family"
hdfs dfs -put "$LOCAL_DATA/child-parent.txt" "$HDFS_ROOT/input_family/"
RUN "任务3：祖孙关系 GrandParents" hadoop jar grandparents.jar GrandParents "$HDFS_ROOT/input_family" "$HDFS_ROOT/out_family"
echo "----- 祖孙关系输出内容 -----"
hdfs dfs -cat "$HDFS_ROOT/out_family/part-r-00000"

echo
echo "全部完成！三个任务的输出都保存在 $HDFS_ROOT 下。"
echo "可用命令对比预期结果："
echo "  hdfs dfs -cat $HDFS_ROOT/out_dedup/part-r-00000     对比 data/expected_dedup.txt"
echo "  hdfs dfs -cat $HDFS_ROOT/out_sort/part-r-00000      对比 data/expected_sort.txt"
echo "  hdfs dfs -cat $HDFS_ROOT/out_family/part-r-00000    对比 data/expected_grand.txt"
echo "（先下载到本地：hdfs dfs -get ... 再用 diff 对比）"