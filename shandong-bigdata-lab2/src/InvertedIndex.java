import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

/**
 * 实验二：文档倒排索引算法实现（MapReduce 两段式作业）
 *
 * Job1 索引构建：  输入原始英文文档，输出  word -> doc1:n;doc2:m
 * Job2 清洗合并：  过滤停词、按词聚合、统计 total，输出
 *                 word <doc1,n>;<doc2,m>;<total,k>.
 *
 * 提交方式：
 *   hadoop jar iindex.jar InvertedIndex <输入目录> <停词表HDFS路径> <中间输出目录> <最终输出目录>
 */
public class InvertedIndex extends Configured implements Tool {

  /** Job1 Mapper：对文档每一行分词，逐词发出 (word, 文档名:行内词频) */
  public static class IndexMapper extends Mapper<Object, Text, Text, Text> {
    private final Text outKey = new Text();
    private final Text outVal = new Text();

    @Override
    protected void map(Object key, Text value, Context context)
        throws java.io.IOException, InterruptedException {
      // 当前切片所属文件名，如 document01.txt
      String docId = ((FileSplit) context.getInputSplit()).getPath().getName();
      // 分词：统一转小写，用所有非字母字符切分
      String[] tokens = value.toString().toLowerCase().split("[^a-z]+");
      // map 端局部计数（相当于 Combiner 思想），减少 shuffle 数据量
      Map<String, Integer> freq = new TreeMap<String, Integer>();
      for (String t : tokens) {
        if (!t.isEmpty()) {
          Integer old = freq.get(t);
          freq.put(t, old == null ? 1 : old + 1);
        }
      }
      for (Map.Entry<String, Integer> e : freq.entrySet()) {
        outKey.set(e.getKey());
        outVal.set(docId + ":" + e.getValue());
        context.write(outKey, outVal);
      }
    }
  }

  /** Job1 Reducer：把同一单词在多个文档、多行中的出现次数累加 */
  public static class IndexReducer extends Reducer<Text, Text, Text, Text> {
    @Override
    protected void reduce(Text key, Iterable<Text> values, Context context)
        throws java.io.IOException, InterruptedException {
      TreeMap<String, Integer> postings = new TreeMap<String, Integer>();
      for (Text v : values) {
        String s = v.toString();
        int i = s.lastIndexOf(':');
        String doc = s.substring(0, i);
        int cnt = Integer.parseInt(s.substring(i + 1));
        Integer old = postings.get(doc);
        postings.put(doc, old == null ? cnt : old + cnt);
      }
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<String, Integer> e : postings.entrySet()) {
        if (sb.length() > 0) sb.append(";");
        sb.append(e.getKey()).append(":").append(e.getValue());
      }
      context.write(key, new Text(sb.toString()));
    }
  }

  /** Job2 Mapper：setup 阶段从 HDFS 加载停词表，过滤停词，逐条转发倒排项 */
  public static class CleanupMapper extends Mapper<Object, Text, Text, Text> {
    private final Set<String> stopWords = new HashSet<String>();

    @Override
    protected void setup(Context context) throws java.io.IOException {
      Configuration conf = context.getConfiguration();
      FileSystem fs = FileSystem.get(conf);
      BufferedReader br = new BufferedReader(new InputStreamReader(
          fs.open(new Path(conf.get("index.stopwords.path")))));
      String line;
      while ((line = br.readLine()) != null) {
        for (String w : line.toLowerCase().split("[^a-z]+")) {
          if (!w.isEmpty()) stopWords.add(w);
        }
      }
      br.close();
    }

    @Override
    protected void map(Object key, Text value, Context context)
        throws java.io.IOException, InterruptedException {
      String line = value.toString();
      int tab = line.indexOf('\t');
      if (tab < 0) return;
      String word = line.substring(0, tab).trim();
      String postings = line.substring(tab + 1).trim();
      if (word.isEmpty() || postings.isEmpty()) return;
      if (stopWords.contains(word)) return;   // 停词不进索引
      for (String entry : postings.split(";")) {
        if (!entry.isEmpty()) context.write(new Text(word), new Text(entry));
      }
    }
  }

  /** Job2 Reducer：合并同一单词全部倒排项，统计 total 并格式化输出 */
  public static class CleanupReducer extends Reducer<Text, Text, Text, NullWritable> {
    @Override
    protected void reduce(Text key, Iterable<Text> values, Context context)
        throws java.io.IOException, InterruptedException {
      TreeMap<String, Integer> postings = new TreeMap<String, Integer>();
      int total = 0;
      for (Text v : values) {
        String s = v.toString();
        int i = s.lastIndexOf(':');
        int cnt = Integer.parseInt(s.substring(i + 1));
        String doc = s.substring(0, i);
        Integer old = postings.get(doc);
        postings.put(doc, old == null ? cnt : old + cnt);
        total += cnt;
      }
      StringBuilder sb = new StringBuilder();
      sb.append(key).append(" ");
      for (Map.Entry<String, Integer> e : postings.entrySet()) {
        sb.append("<").append(e.getKey()).append(",").append(e.getValue()).append(">;");
      }
      sb.append("<total,").append(total).append(">.");
      context.write(new Text(sb.toString()), NullWritable.get());
    }
  }

  /** 驱动类：依次提交两个作业 */
  @Override
  public int run(String[] args) throws Exception {
    if (args.length != 4) {
      System.err.println("用法: InvertedIndex <输入目录> <停词表> <中间输出目录> <最终输出目录>");
      return -1;
    }
    Path input = new Path(args[0]);
    Path stopwords = new Path(args[1]);
    Path interOut = new Path(args[2]);
    Path finalOut = new Path(args[3]);

    // ---- Job1：构建 word -> doc:count ----
    Configuration conf1 = new Configuration();
    Job job1 = Job.getInstance(conf1, "InvertedIndex-Build");
    job1.setJarByClass(InvertedIndex.class);
    job1.setMapperClass(IndexMapper.class);
    job1.setReducerClass(IndexReducer.class);
    job1.setOutputKeyClass(Text.class);
    job1.setOutputValueClass(Text.class);
    FileInputFormat.addInputPath(job1, input);
    FileOutputFormat.setOutputPath(job1, interOut);
    if (!job1.waitForCompletion(true)) return 1;

    // ---- Job2：停词过滤 + total 统计 + 格式化 ----
    Configuration conf2 = new Configuration();
    conf2.set("index.stopwords.path", stopwords.toString());
    Job job2 = Job.getInstance(conf2, "InvertedIndex-Cleanup");
    job2.setJarByClass(InvertedIndex.class);
    job2.setMapperClass(CleanupMapper.class);
    job2.setReducerClass(CleanupReducer.class);
    job2.setMapOutputKeyClass(Text.class);
    job2.setMapOutputValueClass(Text.class);
    job2.setOutputKeyClass(Text.class);
    job2.setOutputValueClass(NullWritable.class);
    FileInputFormat.addInputPath(job2, interOut);
    FileOutputFormat.setOutputPath(job2, finalOut);
    return job2.waitForCompletion(true) ? 0 : 1;
  }

  public static void main(String[] args) throws Exception {
    System.exit(ToolRunner.run(new Configuration(), new InvertedIndex(), args));
  }
}
