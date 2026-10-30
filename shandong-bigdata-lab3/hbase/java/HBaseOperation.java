import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.util.Bytes;

/**
 * 实验三 第一部分(二)：HBase 数据库操作
 * 把关系型数据库中的 Student/Course/SC 表转换为适合 HBase 存储的表并插入数据，
 * 实现 createTable / addRecord / scanColumn / modifyData / deleteRow 五个函数。
 *
 * 表设计：
 *   Student：RowKey = 学生姓名 S_Name（Zhangsan/Mary/Lisi），列族 S_No、S_Sex、S_Age（每族一列，空限定符）
 *   Course ：RowKey = 课程号 C_No，列族 C_Name、C_Credit
 *   SC     ：RowKey = 学生姓名 S_Name，列族 Score，列限定符为课程名（Math/Computer Science/English）
 *
 * 说明：题目学生表中 Mary 与 Lisi 学号均为 2015003（疑为笔误），此处按教材标准数据取 Mary=2015002，
 *       保证选课表 SC 中的学号都能对应到学生。
 *
 * 编译：javac -encoding UTF-8 -cp "$(hbase classpath)" HBaseOperation.java
 * 运行：java -cp "$(hbase classpath):." HBaseOperation          （完整演示）
 *       java -cp "$(hbase classpath):." HBaseOperation restore  （只重建表恢复数据）
 */
public class HBaseOperation {
    public static Configuration conf;
    public static Connection connection;
    public static Admin admin;

    public static void init() throws IOException {
        conf = HBaseConfiguration.create();
        connection = ConnectionFactory.createConnection(conf);
        admin = connection.getAdmin();
    }

    public static void close() {
        try {
            if (admin != null) admin.close();
            if (connection != null) connection.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 创建表：fields 中每个元素为一个列族名；
     * 若 HBase 中已存在同名表，先删除原表再创建新表。
     */
    public static void createTable(String tableName, String[] fields) throws IOException {
        TableName tn = TableName.valueOf(tableName);
        if (admin.tableExists(tn)) {
            System.out.println("[createTable] 表 " + tableName + " 已存在，先删除原表 ...");
            admin.disableTable(tn);
            admin.deleteTable(tn);
        }
        TableDescriptorBuilder builder = TableDescriptorBuilder.newBuilder(tn);
        for (String field : fields) {
            builder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(field));
        }
        admin.createTable(builder.build());
        System.out.println("[createTable] 创建表 " + tableName + " 成功，列族: " + String.join(", ", fields));
    }

    /**
     * 添加记录：fields[i] 形如 "columnFamily:column"；
     * 若不含冒号，则表示该列族下的空限定符列。values[i] 为对应写入的值。
     */
    public static void addRecord(String tableName, String row, String[] fields, String[] values) throws IOException {
        Table table = connection.getTable(TableName.valueOf(tableName));
        for (int i = 0; i < fields.length; i++) {
            Put put = new Put(Bytes.toBytes(row));
            String[] col = fields[i].split(":", 2);
            if (col.length == 2) {
                put.addColumn(Bytes.toBytes(col[0]), Bytes.toBytes(col[1]), Bytes.toBytes(values[i]));
            } else {
                put.addColumn(Bytes.toBytes(col[0]), Bytes.toBytes(""), Bytes.toBytes(values[i]));
            }
            table.put(put);
        }
        table.close();
        System.out.println("[addRecord] 表 " + tableName + " 行 " + row + " 写入 "
                + String.join(", ", fields) + " = " + String.join(", ", values));
    }

    /**
     * 浏览表某一列的数据：
     *   column 为列族名（如 "Score"）时，列出该族下所有列限定符的数据；
     *   column 为 "列族:列"（如 "Score:Math"）时，只列出该列数据；
     *   某行该列数据不存在时输出 null。
     */
    public static void scanColumn(String tableName, String column) throws IOException {
        Table table = connection.getTable(TableName.valueOf(tableName));
        String[] col = column.split(":", 2);
        System.out.println("[scanColumn] 浏览表 " + tableName + " 的列 " + column + "：");
        Scan scanAll = new Scan();
        ResultScanner allScanner = table.getScanner(scanAll);
        for (Result r : allScanner) {
            String row = Bytes.toString(r.getRow());
            Get get = new Get(Bytes.toBytes(row));
            if (col.length == 2) {
                get.addColumn(Bytes.toBytes(col[0]), Bytes.toBytes(col[1]));
            } else {
                get.addFamily(Bytes.toBytes(col[0]));
            }
            Result res = table.get(get);
            if (col.length == 2) {
                byte[] v = res.getValue(Bytes.toBytes(col[0]), Bytes.toBytes(col[1]));
                System.out.println("  " + row + " => " + (v == null ? "null" : Bytes.toString(v)));
            } else {
                if (res.isEmpty()) {
                    System.out.println("  " + row + " => null");
                } else {
                    for (Cell cell : res.rawCells()) {
                        System.out.println("  " + row + " => "
                                + Bytes.toString(CellUtil.cloneFamily(cell)) + ":"
                                + Bytes.toString(CellUtil.cloneQualifier(cell)) + " = "
                                + Bytes.toString(CellUtil.cloneValue(cell)));
                    }
                }
            }
        }
        allScanner.close();
        table.close();
    }

    /** 修改单元格数据（按题目签名，演示用新值为 "24"，即把 Zhangsan 年龄 23 改为 24） */
    public static void modifyData(String tableName, String row, String column) throws IOException {
        modifyData(tableName, row, column, "24");
    }

    /** 修改单元格数据：把 row 行 column 列的值改为 newValue，并打印修改前后的值 */
    public static void modifyData(String tableName, String row, String column, String newValue) throws IOException {
        Table table = connection.getTable(TableName.valueOf(tableName));
        String[] col = column.split(":", 2);
        byte[] fam = Bytes.toBytes(col[0]);
        byte[] qual = col.length == 2 ? Bytes.toBytes(col[1]) : Bytes.toBytes("");
        Get get = new Get(Bytes.toBytes(row));
        get.addColumn(fam, qual);
        Result before = table.get(get);
        byte[] oldV = before.getValue(fam, qual);
        Put put = new Put(Bytes.toBytes(row));
        put.addColumn(fam, qual, Bytes.toBytes(newValue));
        table.put(put);
        System.out.println("[modifyData] 表 " + tableName + " 行 " + row + " 列 " + column + "："
                + (oldV == null ? "null" : Bytes.toString(oldV)) + " -> " + newValue);
        table.close();
    }

    /** 删除表 tableName 中 row 指定行的整行记录 */
    public static void deleteRow(String tableName, String row) throws IOException {
        Table table = connection.getTable(TableName.valueOf(tableName));
        Delete delete = new Delete(Bytes.toBytes(row));
        table.delete(delete);
        table.close();
        System.out.println("[deleteRow] 删除表 " + tableName + " 中行 " + row + " 的记录");
    }

    /** 把 Student/Course/SC 三张关系表转换为 HBase 表并插入全部数据 */
    public static void buildStudentDB() throws IOException {
        createTable("Student", new String[] { "S_No", "S_Sex", "S_Age" });
        createTable("Course", new String[] { "C_Name", "C_Credit" });
        createTable("SC", new String[] { "Score" });

        addRecord("Student", "Zhangsan", new String[] { "S_No", "S_Sex", "S_Age" }, new String[] { "2015001", "male", "23" });
        addRecord("Student", "Mary", new String[] { "S_No", "S_Sex", "S_Age" }, new String[] { "2015002", "female", "22" });
        addRecord("Student", "Lisi", new String[] { "S_No", "S_Sex", "S_Age" }, new String[] { "2015003", "male", "24" });

        addRecord("Course", "123001", new String[] { "C_Name", "C_Credit" }, new String[] { "Math", "2.0" });
        addRecord("Course", "123002", new String[] { "C_Name", "C_Credit" }, new String[] { "Computer Science", "5.0" });
        addRecord("Course", "123003", new String[] { "C_Name", "C_Credit" }, new String[] { "English", "3.0" });

        addRecord("SC", "Zhangsan", new String[] { "Score:Math", "Score:English" }, new String[] { "86", "69" });
        addRecord("SC", "Mary", new String[] { "Score:Computer Science", "Score:English" }, new String[] { "77", "99" });
        addRecord("SC", "Lisi", new String[] { "Score:Math", "Score:Computer Science" }, new String[] { "98", "95" });
    }

    public static void main(String[] args) throws IOException {
        init();
        System.out.println("========== (1) 创建 Student/Course/SC 表并导入关系数据 ==========");
        buildStudentDB();

        if (args.length > 0 && args[0].equals("restore")) {
            System.out.println("[restore] 恢复数据模式：只重建表并导入数据，跳过修改/删除演示");
            close();
            return;
        }

        System.out.println();
        System.out.println("========== (2) scanColumn 浏览列数据 ==========");
        scanColumn("Student", "S_No");     // 列族名（族下为空限定符列）：列出所有学号
        scanColumn("SC", "Score");         // 列族名：列出 Score 族下每个列限定符的数据
        scanColumn("SC", "Score:Math");    // 具体列：只列数学成绩；Mary 无数学成绩，输出 null

        System.out.println();
        System.out.println("========== (3) modifyData 修改单元格 ==========");
        modifyData("Student", "Zhangsan", "S_Age");   // Zhangsan 年龄 23 -> 24
        scanColumn("Student", "S_Age");

        System.out.println();
        System.out.println("========== (4) deleteRow 删除行 ==========");
        deleteRow("SC", "Lisi");
        scanColumn("SC", "Score");

        close();
        System.out.println();
        System.out.println("HBaseOperation 演示全部完成。");
    }
}
