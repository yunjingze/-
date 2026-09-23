import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

/**
 * 实验一 第三部分 任务2：对输入文件进行排序
 * 输入：多个文件，每行一个整数
 * 输出：每行两个整数，第一个数字为排序位次，第二个为原整数
 *
 * 原理：Map 阶段解析出整数值作为 key 输出（IntWritable 按数值升序比较），
 *       Reduce 阶段自动收到有序的 key 序列；只设置 1 个 Reduce 任务保证全局有序，
 *       Reducer 按出现次序累计位次并输出 "位次 原整数"。
 *
 * 运行：hadoop jar sort.jar Sort <输入目录> <输出目录>
 */
public class Sort {

    public static class Map extends Mapper<Object, Text, IntWritable, NullWritable> {
        public void map(Object key, Text value, Context context)
                throws IOException, InterruptedException {
            String line = value.toString().trim();
            if (line.length() == 0) {       // 跳过空行
                return;
            }
            context.write(new IntWritable(Integer.parseInt(line)), NullWritable.get());
        }
    }

    public static class Reduce extends Reducer<IntWritable, NullWritable, Text, NullWritable> {
        private static int rank = 0;        // 位次计数器，跨所有 key 累计

        public void reduce(IntWritable key, Iterable<NullWritable> values, Context context)
                throws IOException, InterruptedException {
            rank++;
            context.write(new Text(rank + " " + key.get()), NullWritable.get());
        }
    }

    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "Sort");
        job.setJarByClass(Sort.class);
        job.setMapperClass(Map.class);
        job.setReducerClass(Reduce.class);
        job.setMapOutputKeyClass(IntWritable.class);
        job.setMapOutputValueClass(NullWritable.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(NullWritable.class);
        job.setNumReduceTasks(1);           // 只用一个 Reduce 任务，保证全局有序
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}