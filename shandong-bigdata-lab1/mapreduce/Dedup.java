import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

/**
 * 实验一 第三部分 任务1：文件的合并和去重
 * 输入：两个文件 A、B（每行一条记录）
 * 输出：合并后剔除重复内容的新文件 C
 *
 * 原理：Map 阶段把每一行内容作为 key 输出（row 相同的记录自然被分组到一起），
 *       Reduce 阶段每个 key 只输出一次，即天然完成去重。
 *
 * 运行：hadoop jar dedup.jar Dedup <输入目录> <输出目录>
 */
public class Dedup {

    public static class Map extends Mapper<Object, Text, Text, NullWritable> {
        public void map(Object key, Text value, Context context)
                throws IOException, InterruptedException {
            String line = value.toString().trim();
            if (line.length() == 0) {       // 跳过空行
                return;
            }
            context.write(new Text(line), NullWritable.get());
        }
    }

    public static class Reduce extends Reducer<Text, NullWritable, Text, NullWritable> {
        public void reduce(Text key, Iterable<NullWritable> values, Context context)
                throws IOException, InterruptedException {
            context.write(key, NullWritable.get());   // key 唯一，输出一次即去重
        }
    }

    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "Dedup");
        job.setJarByClass(Dedup.class);
        job.setMapperClass(Map.class);
        job.setCombinerClass(Reduce.class);   // 可在 Map 端先做一次合并，减少网络传输
        job.setReducerClass(Reduce.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(NullWritable.class);
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}