# -*- coding: utf-8 -*-
"""
实验三 第二部分：PageRank 算法 —— Spark(PySpark) 实现

迭代公式: PR(u) = (1-d) + d * Σ_{v→u} PR(v)/L(v)，d=0.85，初始 PR=1.0，连续迭代 10 次
输入格式（每行）: 网页 \t 该网页链接到的网页集合（英文逗号分隔）
输出格式: (网页,PR值)，PR 值保留 10 位小数，按 PR 降序

用法: spark-submit --master yarn pagerank_spark.py [HDFS输入路径]
"""
import sys

from pyspark import SparkConf, SparkContext

DAMPING = 0.85     # 阻尼系数 d
ITERATIONS = 10    # 迭代次数


def main():
    input_path = sys.argv[1] if len(sys.argv) > 1 else "hdfs://localhost:9000/pagerank/input"
    conf = SparkConf().setAppName("PageRankSpark")
    sc = SparkContext(conf=conf)
    sc.setLogLevel("WARN")

    # 解析数据集: links = (网页, [出链网页列表])，persist() 缓存内存，迭代过程不重复读盘
    links = (sc.textFile(input_path)
             .map(lambda line: line.strip())
             .filter(lambda line: len(line) > 0)
             .map(lambda line: line.split("\t"))
             .map(lambda parts: (parts[0].strip(),
                                 [x.strip() for x in parts[1].split(",") if x.strip()]))
             .persist())

    # 初始 PR 值 = 1.0
    ranks = links.mapValues(lambda _: 1.0)

    for i in range(ITERATIONS):
        # 每个页面把自己的 PR 均分给它链接到的页面: (目标页, PR/出链数)
        contributions = (links.join(ranks)
                         .flatMap(lambda kv: [(target, kv[1][1] / len(kv[1][0]))
                                              for target in kv[1][0]]))
        # 汇总每个页面收到的贡献并套用迭代公式；
        # union 一个全 0 的 (页面,0.0) 保证没有入链的页面也不会从结果中丢失
        ranks = (links.mapValues(lambda _: 0.0)
                 .union(contributions)
                 .reduceByKey(lambda a, b: a + b)
                 .mapValues(lambda c: (1.0 - DAMPING) + DAMPING * c))

    # 按 PR 降序输出 (网页,PR值)，保留 10 位小数
    results = ranks.sortBy(lambda kv: -kv[1]).collect()
    print("===== Spark PageRank 结果（d=0.85，迭代 %d 次）=====" % ITERATIONS)
    lines = []
    for page, pr in results:
        line = "(%s,%.10f)" % (page, pr)
        lines.append(line)
        print(line)

    # 结果保存到 HDFS，便于查看/写报告
    out = "hdfs://localhost:9000/pagerank/output_spark"
    sc.parallelize(lines).coalesce(1).saveAsTextFile(out)
    print("结果已另存到: %s" % out)
    sc.stop()


if __name__ == "__main__":
    main()
