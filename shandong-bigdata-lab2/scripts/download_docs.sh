#!/bin/bash
# 备选方案：从公开网页（维基百科 REST API）实时抓取 10 篇英文文档。
# 主题与 data/ 目录中整理好的文档一致；需虚拟机可访问外网。
# 用法: bash scripts/download_docs.sh
set -e
mkdir -p data
curl_doc() {
  # $1: 输出文件名  $2: 维基百科条目名
  curl -s "https://en.wikipedia.org/api/rest_v1/page/summary/$2" \
    | python3 -c "import json,sys; print(json.load(sys.stdin)['extract'])" > "$1"
}
curl_doc data/document01.txt Apache_hadoop
curl_doc data/document02.txt MapReduce
curl_doc data/document03.txt Apache_Spark
curl_doc data/document04.txt Inverted_index
curl_doc data/document05.txt Search_engine
curl_doc data/document06.txt Big_data
curl_doc data/document07.txt Cloud_computing
curl_doc data/document08.txt Apache_Kafka
curl_doc data/document09.txt Linux
curl_doc data/document10.txt Apache_Flink
wc -w data/*.txt
