import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

/**
 * 实验三 第二部分：PageRank 算法 —— MapReduce 实现
 *
 * 数据集格式（每行）: 网页 \t 该网页链接到的网页集合（英文逗号分隔）
 * 迭代中间格式       : 网页 \t PR值 \t 链接集合
 * 迭代公式           : PR(u) = (1-d) + d * Σ_{v→u} PR(v)/L(v)，d=0.85
 * 初始 PR=1.0，连续迭代 10 次；最终按 PR 降序输出 (网页,PR值)，PR 保留 10 位小数。
 *
 * 编译打包:
 *   javac -encoding UTF-8 -cp "$(hadoop classpath)" -d classes PageRankMR.java
 *   jar cf PageRankMR.jar -C classes .
 * 运行（伪分布式/YARN；jar 清单无 Main-Class，必须显式给出主类名 PageRankMR）:
 *   hadoop jar PageRankMR.jar PageRankMR /pagerank/input /pagerank/output_mr
 */
public class PageRankMR {

    public static final double DAMPING = 0.85;   // 阻尼系数 d
    public static final int ITERATIONS = 10;     // 迭代次数

    /**
     * Mapper：
     *  1) 把本行记录原样传给下一轮：(本页, "P\tPR\t链接表")
     *  2) 把本页 PR 均分给出链目标：(目标页, "C\tPR/出链数")
     */
    public static class PRMapper extends Mapper<LongWritable, Text, Text, Text> {
        private Text outKey = new Text();
        private Text outValue = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context)
                throws IOException, InterruptedException {
            String line = value.toString().trim();
            if (line.isEmpty()) return;
            String[] parts = line.split("\t");
            String page = parts[0].trim();
            double pr;
            String links;
            if (parts.length >= 3) {              // 第2轮起: 网页 \t PR \t 链接表
                pr = Double.parseDouble(parts[1].trim());
                links = parts[2].trim();
            } else {                              // 第1轮:   网页 \t 链接表，PR 初始为 1.0
                pr = 1.0;
                links = parts.length >= 2 ? parts[1].trim() : "";
            }
            // 1) 自身信息（PR + 链接表）传给下一轮
            outKey.set(page);
            outValue.set("P\t" + pr + "\t" + links);
            context.write(outKey, outValue);
            // 2) PR 均分分发给每个出链目标页
            if (!links.isEmpty()) {
                String[] targets = links.split(",");
                double contrib = pr / targets.length;
                for (String t : targets) {
                    t = t.trim();
                    if (t.isEmpty()) continue;
                    outKey.set(t);
                    outValue.set("C\t" + contrib);
                    context.write(outKey, outValue);
                }
            }
        }
    }

    /**
     * Reducer：汇总某页收到的全部贡献值 C，按公式计算新 PR：
     *   newPR = (1-d) + d * ΣC
     * 输出 网页 \t newPR \t 链接表 作为下一轮输入。
     */
    public static class PRReducer extends Reducer<Text, Text, Text, Text> {
        private Text outValue = new Text();

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            String links = "";
            double sum = 0.0;
            for (Text v : values) {
                String s = v.toString();
                if (s.startsWith("P\t")) {
                    String[] p = s.split("\t", 3);
                    links = p.length >= 3 ? p[2] : "";
                } else if (s.startsWith("C\t")) {
                    sum += Double.parseDouble(s.substring(2));
                }
            }
            double newPr = (1 - DAMPING) + DAMPING * sum;
            outValue.set(newPr + "\t" + links);
            context.write(key, outValue);
        }
    }

    public static void main(String[] args) throws Exception {
        String inputPath = args.length > 0 ? args[0] : "/pagerank/input";
        String outputBase = args.length > 1 ? args[1] : "/pagerank/output_mr";
        Configuration conf = new Configuration();
        FileSystem fs = FileSystem.get(conf);

        // 连续迭代 10 次：第 i 轮的输入是第 i-1 轮的输出（都在 HDFS 上）
        for (int i = 1; i <= ITERATIONS; i++) {
            Path in = new Path(i == 1 ? inputPath : outputBase + "/iter" + (i - 1));
            Path out = new Path(outputBase + "/iter" + i);
            if (fs.exists(out)) fs.delete(out, true);
            Job job = Job.getInstance(conf, "PageRank iteration " + i + "/" + ITERATIONS);
            job.setJarByClass(PageRankMR.class);
            job.setMapperClass(PRMapper.class);
            job.setReducerClass(PRReducer.class);
            job.setOutputKeyClass(Text.class);
            job.setOutputValueClass(Text.class);
            FileInputFormat.addInputPath(job, in);
            FileOutputFormat.setOutputPath(job, out);
            if (!job.waitForCompletion(true)) {
                throw new RuntimeException("PageRank 第 " + i + " 次迭代失败");
            }
        }

        // 读取第 10 轮结果，按 PR 降序输出 (网页,PR值)，PR 保留 10 位小数
        Path finalDir = new Path(outputBase + "/iter" + ITERATIONS);
        List<String[]> results = new ArrayList<>();
        FileStatus[] files = fs.listStatus(finalDir, new PathFilter() {
            @Override
            public boolean accept(Path p) {
                String n = p.getName();
                return !n.startsWith("_") && !n.startsWith(".");
            }
        });
        for (FileStatus f : files) {
            BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(f.getPath())));
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split("\t");
                if (p.length >= 2) results.add(new String[] { p[0], p[1] });
            }
            br.close();
        }
        results.sort((a, b) -> Double.compare(Double.parseDouble(b[1]), Double.parseDouble(a[1])));

        StringBuilder sb = new StringBuilder();
        System.out.println("===== MapReduce PageRank 结果（d=0.85，迭代 " + ITERATIONS + " 次）=====");
        for (String[] r : results) {
            String one = "(" + r[0] + "," + String.format("%.10f", Double.parseDouble(r[1])) + ")";
            System.out.println(one);
            sb.append(one).append("\n");
        }

        // 结果另存到 HDFS，便于查看/写报告
        Path resultFile = new Path(outputBase + "/result.txt");
        if (fs.exists(resultFile)) fs.delete(resultFile, false);
        FSDataOutputStream os = fs.create(resultFile);
        os.write(sb.toString().getBytes("UTF-8"));
        os.close();
        System.out.println("结果已另存到: hdfs://" + resultFile);
    }
}
