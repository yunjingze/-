# 实验二：文档倒排索引算法实现（shandong-bigdata-lab2）

课程：大数据管理与分析 ｜ 平台：Ubuntu 22.04 + Hadoop 3.2.2（伪分布式）+ OpenJDK 1.8

用 MapReduce 在 Hadoop 集群上为 10 篇英文文档构建倒排索引，输出形如：

    data <document01.txt,5>;<document02.txt,2>;...;<total,28>.

## 目录结构

    src/InvertedIndex.java    MapReduce 程序（两段式作业）
    data/document01..10.txt   英文语料（整理自维基百科等公开网页的大数据主题文章）
    stopwords.txt             停词表（约100个常见英文虚词）
    scripts/download_docs.sh  备选：从公开网页实时抓取语料（需外网）
    run.sh                    上传数据 -> 编译打包 -> 提交作业 -> 查看结果
    simulate_mr.py            本地单线程模拟同一逻辑，用于结果校验
    sim_output/               校验用输出（stage1 中间结果 + 最终 451 词条索引）

## 算法说明

- Job1（构建）：Mapper 逐行分词（转小写、按非字母切分），map 端局部计数后发出
  (word, 文档名:词频)；Reducer 累加同一单词各文档词频，输出 word -> doc1:n;doc2:m。
- Job2（清洗）：Mapper 在 setup 阶段从 HDFS 载入停词表并过滤停词，把倒排项拆开逐条
  发出，借 Shuffle 按词聚合；Reducer 合并倒排链、统计 <total,k> 并格式化输出。

## 运行

    cd shandong-bigdata-lab2
    start-dfs.sh; start-yarn.sh     # 若集群未启动
    bash run.sh                     # 一键运行
    hadoop fs -cat /iindex/output/part-r-00000 | head -20

手工方式：

    hadoop fs -mkdir -p /iindex/input
    hadoop fs -put data/*.txt /iindex/input
    hadoop fs -put stopwords.txt /iindex/stopwords.txt
    mkdir build
    javac -cp "$(hadoop classpath)" -d build src/InvertedIndex.java
    jar -cf iindex.jar -C build .
    hadoop jar iindex.jar InvertedIndex /iindex/input /iindex/stopwords.txt /iindex/inter /iindex/output

## 结果

- 语料 1175 个词次、503 个不重复词；过滤 52 个停词（405 词次）后索引 **451 个词条**；
- 与 simulate_mr.py 本地重算结果逐行一致（sim_output/final_output.txt）。