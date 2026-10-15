# -*- coding: utf-8 -*-
"""本地模拟两段式 MapReduce，验证 InvertedIndex.java 的逻辑并生成报告用结果。
分词/计数/排序规则与 Java 版完全一致。"""
import io, re, sys, glob, os
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

def tokenize(s):
    return [t for t in re.split("[^a-z]+", s.lower()) if t]

# ---------- Job1: map ----------
stage = {}  # word -> doc -> count
for path in sorted(glob.glob("data/document*.txt")):
    doc = os.path.basename(path)
    with io.open(path, encoding="utf-8") as f:
        for line in f:
            for w in tokenize(line):          # 逐行(=每个 map 输入记录)
                stage.setdefault(w, {}).setdefault(doc, 0)
                stage[w][doc] += 1

# ---------- Job1: reduce -> 中间输出 ----------
inter_lines = []
for w in sorted(stage):
    post = ";".join(f"{d}:{c}" for d, c in sorted(stage[w].items()))
    inter_lines.append(w + "\t" + post)

# ---------- Job2: map(停词过滤) + reduce(格式化/total) ----------
stop = set()
with io.open("stopwords.txt", encoding="utf-8") as f:
    for line in f:
        stop.update(tokenize(line))

final_lines = []
for line in inter_lines:
    w, post = line.split("\t")
    if w in stop:
        continue
    docs = {}
    for entry in post.split(";"):
        d, c = entry.rsplit(":", 1)
        docs[d] = int(c)
    total = sum(docs.values())
    s = w + " " + "".join(f"<{d},{c}>;" for d, c in sorted(docs.items())) + f"<total,{total}>."
    final_lines.append(s)

os.makedirs("sim_output", exist_ok=True)
io.open("sim_output/stage1_output.txt", "w", encoding="utf-8").write("\n".join(inter_lines) + "\n")
io.open("sim_output/final_output.txt", "w", encoding="utf-8").write("\n".join(final_lines) + "\n")

# ---------- 统计信息(供报告使用) ----------
n_words_raw = sum(sum(v.values()) for v in stage.values())
n_types_raw = len(stage)
n_types_final = len(final_lines)
n_tokens_final = 0
stop_hits = {}
for line in inter_lines:
    w, post = line.split("\t")
    cnt = sum(int(e.rsplit(":", 1)[1]) for e in post.split(";"))
    if w in stop:
        stop_hits[w] = cnt
    else:
        n_tokens_final += cnt
print("语料总词数:", n_words_raw)
print("原始不重复词数:", n_types_raw)
print("过滤停词后索引词条数:", n_types_final)
print("被过滤停词的词次合计:", n_words_raw - n_tokens_final)
print("停词命中最多的10个词:", sorted(stop_hits.items(), key=lambda x: -x[1])[:10])
print()
print("===== 最终倒排索引前 25 行 =====")
print("\n".join(final_lines[:25]))
