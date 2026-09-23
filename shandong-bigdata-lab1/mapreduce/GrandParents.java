import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

/**
 * 实验一 第三部分 任务3：对指定的 child-parent 表格进行信息挖掘（求祖孙关系）
 * 输入：每行 "child parent"（第一行为表头，会作为普通记录被跳过）
 * 输出：每行 "grandchild grandparent"
 *
 * 原理（连接 join 思想，1 个 MapReduce 搞定两跳关系）：
 *   对每条父子记录 (child, parent) 发出两条“倒排索引”：
 *     以 parent 为 key，发 (0:child) —— 表示 child 是 key 的孩子；
 *     以 child  为 key，发 (1:parent) —— 表示 parent 是 key 的父辈。
 *   Reduce 阶段同一个 key（中间人）会收集两类信息：
 *     一类是它的孩子列表，一类是它的父辈列表，
 *   两者做笛卡尔积，即得到“孙子 —— 爷爷”的关系对。
 *   例：key=Lucy 收到孩子{Steven,Jone}、父辈{Mary,Frank}，输出 (Steven,Mary) 等四组。
 *
 * 运行：hadoop jar grandparents.jar GrandParents <输入目录> <输出目录>
 */
public class GrandParents {

    public static class Map extends Mapper<Object, Text, Text, Text> {
        private static final Text CHILD_FLAG = new Text("0:");   // 表示 value 是 key 的孩子
        private static final Text PARENT_FLAG = new Text("1:");  // 表示 value 是 key 的父辈

        public void map(Object key, Text value, Context context)
                throws IOException, InterruptedException {
            String line = value.toString().trim();
            if (line.length() == 0 || line.startsWith("child")) {   // 跳过空行和表头
                return;
            }
            String[] parts = line.split("\\s+");
            if (parts.length < 2) {
                return;
            }
            String child = parts[0];
            String parent = parts[1];
            // 信息1：parent 的孩子中有 child
            context.write(new Text(parent), new Text(CHILD_FLAG.toString() + child));
            // 信息2：child 的父辈中有 parent
            context.write(new Text(child), new Text(PARENT_FLAG.toString() + parent));
        }
    }

    public static class Reduce extends Reducer<Text, Text, Text, Text> {
        private static boolean first = true;   // 只输出一次表头

        public void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            List<String> children = new ArrayList<String>();
            List<String> parents = new ArrayList<String>();
            for (Text v : values) {
                String s = v.toString();
                if (s.startsWith("0:")) {
                    children.add(s.substring(2));
                } else if (s.startsWith("1:")) {
                    parents.add(s.substring(2));
                }
            }
            if (children.isEmpty() || parents.isEmpty()) {
                return;   // 只当孩子和父辈都存在时才有祖孙关系
            }
            for (String c : children) {
                for (String p : parents) {
                    if (first) {
                        context.write(new Text("grandchild"), new Text("grandparent"));
                        first = false;
                    }
                    context.write(new Text(c), new Text(p));
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "GrandParents");
        job.setJarByClass(GrandParents.class);
        job.setMapperClass(Map.class);
        job.setReducerClass(Reduce.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        job.setNumReduceTasks(1);   // 只用一个 Reduce，保证表头只出现一次
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}