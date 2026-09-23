# 实验一 大数据系统基本实验 —— 完整操作指南与代码包

适用环境：VMware Workstation Pro + Ubuntu 22.04（用户 `lixinze`）+ Hadoop 3.3.6 伪分布式 + JDK 11（安装在 `/usr/local/hadoop`）。

> 教程里的命令需要**自己逐条敲一遍**，并把关键结果截图，截图要放进实验报告。
> 凡是涉及创建/修改 `/usr`、/（根目录）下文件的命令，普通用户权限不够，请加 `sudo`。

**目录结构**
```
shandong-bigdata-lab1/
├── README.md                # 本文件
├── data/                    # 三个 MapReduce 任务的输入数据与预期输出
│   ├── A.txt  B.txt         # 任务1：合并去重输入
│   ├── file1..3.txt         # 任务2：排序输入
│   ├── child-parent.txt     # 任务3：祖孙关系输入
│   └── expected_*.txt       # 三个任务的预期输出（用于对照验证）
├── hdfs/
│   └── HdfsOperator.java    # 第二部分 HDFS 10 个功能的 Java API 实现
└── mapreduce/
    ├── Dedup.java           # 第三部分任务1：合并去重
    ├── Sort.java            # 第三部分任务2：排序
    ├── GrandParents.java    # 第三部分任务3：祖孙关系挖掘
    ├── build.sh             # 一键编译三个程序生成 jar
    └── run_all.sh           # 一键上传输入并运行三个任务
```

---

## 第 0 步：把代码包放进虚拟机

1. 在 Windows 上解压本压缩包得到文件夹 `shandong-bigdata-lab1`。
2. 启动 VMware 虚拟机，登录系统（用户 `lixinze`）。
3. 把这个文件夹拖进虚拟机桌面（VMware 默认支持拖拽；不行就开启"共享文件夹"或通过 U 盘拷贝），把文件夹放到主目录：`mv ~/Desktop/shandong-bigdata-lab1 ~/`（路径按实际位置改）。
4. 进入目录：`cd ~/shandong-bigdata-lab1`

---

## 第一部分：熟悉常用的 Linux 操作和 Hadoop 操作

打开终端（Ctrl+Alt+T），逐条执行并截图。下面的命令都给了执行结果说明。

### 1. cd 切换目录
```bash
cd /usr/local          # (1) 切换到 /usr/local
pwd                    # 查看当前目录，确认切换成功
cd ..                  # (2) 切换到上一级目录（现在在 /usr）
pwd
cd ~                   # (3) 切换回当前用户主文件夹（/home/lixinze）
pwd
```

### 2. ls 查看文件与目录
```bash
ls /usr                # 查看 /usr 下所有文件和目录
ls -l /usr             # 带详细信息（权限、属主、大小、时间）查看
```

### 3. mkdir 新建目录
```bash
cd /tmp                # 进入 /tmp
mkdir a                # (1) 创建目录 a
ls /tmp                # 查看 /tmp 下已有哪些目录（应能看到 a）
mkdir -p a1/a2/a3/a4   # (2) 用 -p 一次创建多级目录 a1/a2/a3/a4
ls -R a1               # 递归查看 a1 下的层级结构
```

### 4. rmdir 删除空目录
```bash
rmdir /tmp/a           # (1) 删除目录 a（a 是空目录）
rmdir -p /tmp/a1/a2/a3/a4   # (2) 用 -p 从最内层开始逐层删除整条链
ls /tmp                # 查看 /tmp 下还剩哪些目录
```
> `rmdir` 只能删除**空目录**；如果目录里还有文件会报 `Directory not empty`。非空目录用 `rm -r`。

### 5. cp 复制文件或目录
```bash
sudo cp ~/.bashrc /usr/bashrc1   # (1) 复制 ~/.bashrc 到 /usr 下并改名为 bashrc1（/usr 需要 root 权限）
mkdir /tmp/test                   # (2) 先在 /tmp 下新建 test 目录
sudo cp -r /tmp/test /usr/test    #     用 -r 复制整个目录到 /usr
ls -l /usr | grep -E "bashrc1|test"   # 确认复制结果
```

### 6. mv 移动/重命名
```bash
sudo mv /usr/bashrc1 /usr/test/   # (1) 把 /usr 下的 bashrc1 移动到 /usr/test 目录中
sudo mv /usr/test /usr/test2      # (2) 把目录 test 重命名为 test2
ls -l /usr
```

### 7. rm 移除文件或目录
```bash
sudo rm /usr/test2/bashrc1        # (1) 删除 /usr/test2 下的 bashrc1 文件
sudo rm -r /usr/test2             # (2) 用 -r 删除整个 test2 目录
ls -l /usr                        # 确认已删除
```

### 8. cat 查看文件内容
```bash
cat ~/.bashrc
```

### 9. tac 反向查看文件内容
```bash
tac ~/.bashrc                     # 从最后一行开始向前显示
```

### 10. more 一页一页翻动查看
```bash
more ~/.bashrc                    # 空格翻下一页，q 退出
```

### 11. head 取出前面几行
```bash
head -20 ~/.bashrc                # (1) 显示前 20 行
head -n -50 ~/.bashrc             # (2) 显示除最后 50 行以外的部分（只显示前面几行）
```

### 12. tail 取出后面几行
```bash
tail -20 ~/.bashrc                # (1) 显示最后 20 行
tail -n +51 ~/.bashrc             # (2) 只列出第 51 行以后的数据（-n +N 表示从第 N 行开始）
```

### 13. touch 修改时间或创建文件
```bash
touch /tmp/hello                  # (1) 创建空文件 hello
ls -l /tmp/hello                  #     查看文件时间
touch -d "5 days ago" /tmp/hello  # (2) 把文件时间修改成 5 天前
ls -l /tmp/hello                  #     对比修改前后的时间
```

### 14. chown 修改文件所有者
```bash
sudo chown root /tmp/hello        # 把所有者改为 root
ls -l /tmp/hello                  # 查看属性，确认所有者变成 root
```

### 15. find 文件查找
```bash
find ~ -name ".bashrc"            # 在主文件夹下找文件名为 .bashrc 的文件
```

### 16. tar 压缩与解压
```bash
sudo mkdir /test                  # (1) 在根目录 / 下新建文件夹 test
sudo tar -czf /test.tar.gz /test  #     在根目录 / 下打包压缩成 test.tar.gz（-c 打包 -z gzip -f 指定文件名）
ls -l /test.tar.gz
sudo tar -xzf /test.tar.gz -C /tmp # (2) 解压到 /tmp 目录（-x 解压 -C 指定目录）
ls -R /tmp/test                   # 确认解压结果
```

### 17. grep 查找字符串
```bash
grep "examples" ~/.bashrc         # 从 ~/.bashrc 中查找字符串 examples
echo "当前行号计数："; grep -c "alias" ~/.bashrc   # 若上面无匹配，可用 alias 验证命令有效
```
> 标准 Ubuntu 22.04 的 `~/.bashrc` 里多半没有 `examples` 这一串，此时 grep 会无输出（这是正常现象）。建议**两条都跑**：第一条如实记录"无匹配"，第二条 `grep -n "alias" ~/.bashrc` 演示有结果的情况。

### 18–21. 启动 Hadoop 并做 HDFS 基本操作

```bash
start-dfs.sh                 # 启动 HDFS（NameNode/DataNode/SecondaryNameNode）
start-yarn.sh                # 启动 YARN（ResourceManager/NodeManager）
jps                          # 验证：应看到 5 个进程
```
> 若未配置 SSH 免密会反复输入密码，实验零已配好则不用管。
> 若已启动过，先 `stop-all.sh` 再重新 `start-all.sh`。

```bash
hdfs dfs -mkdir -p /user/lixinze     # (18) 为当前用户（lixinze）在 HDFS 中创建用户目录
hdfs dfs -ls /user/lixinze           # 查看
# 注：实验书上是"为 hadoop 用户创建 /user/hadoop"，你的机器用户名是 lixinze，改用对应目录即可

hdfs dfs -mkdir -p /user/lixinze/test   # (19) 在 /user/lixinze 下创建 test 文件夹
hdfs dfs -ls /user/lixinze              # 查看文件列表

hdfs dfs -put ~/.bashrc /user/lixinze/test/   # (20) 把本地 ~/.bashrc 上传到 HDFS 的 test 目录
hdfs dfs -ls /user/lixinze/test              # 查看 test 目录，确认 .bashrc 已上传

hdfs dfs -get /user/lixinze/test/.bashrc /usr/local/hadoop/   # (21) 把 HDFS 中的文件复制回本地文件系统
ls -l /usr/local/hadoop/.bashrc    # 确认复制成功（/usr/local/hadoop 当前用户可写则无需 sudo）
```

---

## 第二部分：熟悉常用的 HDFS 操作（10 个功能，Shell 命令 + Java API 双实现）

实验要求：**编程实现以下 10 个功能，并用 Hadoop Shell 命令完成相同任务**。
Java 程序已经写好（`hdfs/HdfsOperator.java`），按菜单运行即可；每个功能下面的 Shell 命令是"相同任务的 Shell 版"，两个都要做、都要截图。

### 编译并运行 Java 程序
```bash
cd ~/shandong-bigdata-lab1/hdfs
export CLASSPATH=$(hadoop classpath)
javac HdfsOperator.java
java HdfsOperator
```
运行后按菜单输入编号。提示输入 HDFS 路径时，统一用 `/user/lixinze/...` 开头。

### 10 个功能：Shell 命令对照（截图时用你自己的路径）

| # | 功能 | Shell 命令（示例） |
|---|------|-------------------|
| 1 | 上传文件（存在时追加或覆盖） | `hdfs dfs -put -f 本地文件 /user/lixinze/test/文件`（覆盖）；`hdfs dfs -appendToFile 本地文件 HDFS文件`（追加） |
| 2 | 下载文件（重名自动改名） | 先 `hdfs dfs -get HDFS文件 ~/文件`，再跑一次：本地已有同名文件，Shell 会提示已存在——手动 `mv` 改名（Java 版会自动改名，作对照） |
| 3 | 输出文件内容到终端 | `hdfs dfs -cat /user/lixinze/test/.bashrc` |
| 4 | 显示指定文件信息 | `hdfs dfs -ls -h /user/lixinze/test/.bashrc` 或 `hdfs dfs -stat "%b %o %r %y" 文件` |
| 5 | 递归输出目录下所有文件信息 | `hdfs dfs -ls -R /user/lixinze/test` |
| 6 | 创建/删除文件（父目录自动创建） | `hdfs dfs -mkdir -p /user/lixinze/a/b` 再 `hdfs dfs -touchz /user/lixinze/a/b/f1`（创建）；`hdfs dfs -rm /user/lixinze/a/b/f1`（删除） |
| 7 | 创建/删除目录（空才删，非空不删） | `hdfs dfs -mkdir -p /user/lixinze/d`（创建，父目录自动建）；`hdfs dfs -rmdir /user/lixinze/d`（只删空目录；非空会报错不删） |
| 8 | 追加内容到文件开头或结尾 | 结尾：`hdfs dfs -appendToFile 本地新内容 HDFS文件`；开头：`hdfs dfs -cat 文件 > 本地` 拼好后 `hdfs dfs -put -f` 覆盖（Java 版直接选 h/t） |
| 9 | 删除指定文件 | `hdfs dfs -rm /user/lixinze/test/文件` |
| 10 | 移动文件 | `hdfs dfs -mv /user/lixinze/test/文件 /user/lixinze/test/新名字` |

**报告中怎么写**：每个功能同时截图"Shell 命令执行结果"和"Java 程序菜单运行结果"，并写一句"该功能用 Shell 命令 `xxx` 完成，用 Java API（FileSystem 类的 `copyFromLocalFile/cat/open/listFiles/delete/rename` 等方法）完成"。

---

## 第三部分：MapReduce 初级编程（3 个程序）

### 编译
```bash
cd ~/shandong-bigdata-lab1/mapreduce
bash build.sh          # 依次编译 Dedup、Sort、GrandParents 并打成 jar
```

### 运行（一键跑全部三个）
```bash
bash run_all.sh
```
脚本会自动：上传 `data/` 中的输入文件到 HDFS → 分别运行三个任务 → 打印每个任务的输出。

### 三个任务的原理与对照验证

**任务1：合并去重（Dedup）**——Map 阶段把每一行内容作为 key 输出，Reduce 阶段同一 key 只输出一次，即去掉重复行。
将输出与 `data/expected_dedup.txt` 对比：
```bash
hdfs dfs -cat /user/lixinze/lab1/out_dedup/part-r-00000
diff <(hdfs dfs -cat /user/lixinze/lab1/out_dedup/part-r-00000) data/expected_dedup.txt
```

**任务2：排序（Sort）**——Map 阶段把整数解析为 `IntWritable` 作为 key（数值升序），只设 1 个 Reduce 保证全局有序，Reducer 累计位次输出"位次 整数"。
```bash
hdfs dfs -cat /user/lixinze/lab1/out_sort/part-r-00000
diff <(hdfs dfs -cat /user/lixinze/lab1/out_sort/part-r-00000) data/expected_sort.txt
```

**任务3：祖孙关系挖掘（GrandParents）**——对每条 (child, parent) 记录发两条倒排信息：`parent -> (0:child)`、`child -> (1:parent)`；Reduce 下同一 key 汇集其"孩子列表"与"父辈列表"，两者笛卡尔积即得 (孙子, 爷爷) 对（join 思想）。
```bash
hdfs dfs -cat /user/lixinze/lab1/out_family/part-r-00000
```
> 输出首行是表头 `grandchild grandparent`。组内人名顺序可能和样例略有不同（MapReduce 不保证组内 value 顺序），内容一致即可；也可以执行 `hdfs dfs -cat ... | sort | diff - data/expected_grand.txt` 做排序后对比。

### 常用调试命令
```bash
hdfs dfs -rm -r -f /user/lixinze/lab1/out_xxx    # 输出目录已存在会报错，先删掉
hadoop jar xxx.jar MainClass 输入 输出          # 手动运行单个任务
tail -50 $HADOOP_HOME/logs/userlogs/*/*/syslog  # 看任务日志
```

---

## 常见问题（FAQ）

1. **`jps` 少进程 / NameNode 没起来**：多半是格式化过多次或目录冲突。`stop-all.sh` 后，清掉 namenode/datanode 数据目录并重新 `hdfs namenode -format`，再 `start-all.sh`。
2. **运行 MapReduce 报 `Unsupported class file major version` / 找不到 YARN 类**：确认 JDK 与 Hadoop 匹配；实验零已配过 `yarn-site.xml` 的 `yarn.application.classpath`，正常不会有此问题。
3. **`Permission denied`（HDFS 或本地）**：HDFS 上先 `hdfs dfs -mkdir -p /user/lixinze` 并全部用 `/user/lixinze/...` 路径；本地 `/usr`、`/` 下操作统一加 `sudo`。
4. **输出目录已存在报错**：`FileAlreadyExistsException`——`hdfs dfs -rm -r -f 输出目录` 后重跑。
5. **外网不可用**：本实验所有数据都是本地创建、本地上传，不依赖外网；只有最后上传 GitHub 需要外网（可在 Windows 上操作）。
6. **拖不进文件**：VMware 菜单"虚拟机 → 设置 → 选项 → 共享文件夹"添加 Windows 文件夹，在虚拟机里访问 `/mnt/hgfs/`。

---

## 报告与提交（课程要求）

- 命名：`202400390048-李昕泽-实验一.pdf`（先做 docx，再导出 PDF）
- 篇幅不超过 **8 页**，宋体小四、1.25 倍行距
- 实验代码整理后上传 **GitHub**（仓库设为 Public），仓库链接放进报告"实验代码"一节
- 提交到课程作业系统 https://h.sdu-ai.media/ ，选"大数据管理与分析"