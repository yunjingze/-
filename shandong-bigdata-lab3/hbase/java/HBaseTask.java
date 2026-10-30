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
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.util.Bytes;

/**
 * 实验三 第一部分(一)：用 Java API 实现与 HBase Shell 等价的 5 个任务
 *   任务1 listTables()     —— 列出 HBase 所有表的相关信息（表名、列族） 等价 shell: list
 *   任务2 scanTable(t)     —— 终端显示指定表的所有记录数据             等价 shell: scan '表名'
 *   任务3 添加/删除列族或列 —— alter / put / delete                    等价 shell: alter/put/delete
 *   任务4 truncateTable(t) —— 清空指定表的所有记录数据                 等价 shell: truncate '表名'
 *   任务5 countRows(t)     —— 统计表的行数                            等价 shell: count '表名'
 *
 * 运行前提：先运行 HBaseOperation 建好并填充 Student/Course/SC 三表。
 * 注意：main 末尾会 truncate Student 表；运行完请再执行
 *       java -cp "$(hbase classpath):." HBaseOperation restore 恢复数据。
 */
public class HBaseTask {
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

    /** 任务1：列出 HBase 所有表的相关信息（表名、列族） */
    public static void listTables() throws IOException {
        System.out.println("[任务1] HBase 所有表的相关信息（等价 shell 命令: list）：");
        TableName[] tableNames = admin.listTableNames();
        if (tableNames.length == 0) {
            System.out.println("  （数据库中没有表）");
            return;
        }
        for (TableName tn : tableNames) {
            TableDescriptor desc = admin.getDescriptor(tn);
            StringBuilder sb = new StringBuilder();
            for (byte[] cf : desc.getColumnFamilyNames()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(Bytes.toString(cf));
            }
            System.out.println("  表名: " + tn.getNameAsString() + "    列族: [" + sb + "]");
        }
    }

    /** 任务2：在终端显示出指定表的所有记录数据 */
    public static void scanTable(String tableName) throws IOException {
        System.out.println("[任务2] 表 " + tableName + " 的所有记录数据（等价 shell 命令: scan '" + tableName + "'）：");
        Table table = connection.getTable(TableName.valueOf(tableName));
        Scan scan = new Scan();
        ResultScanner scanner = table.getScanner(scan);
        int rows = 0;
        for (Result r : scanner) {
            rows++;
            for (Cell cell : r.rawCells()) {
                System.out.println("  row=" + Bytes.toString(CellUtil.cloneRow(cell))
                        + ", 列=" + Bytes.toString(CellUtil.cloneFamily(cell)) + ":" + Bytes.toString(CellUtil.cloneQualifier(cell))
                        + ", value=" + Bytes.toString(CellUtil.cloneValue(cell))
                        + ", timestamp=" + cell.getTimestamp());
            }
        }
        if (rows == 0) System.out.println("  （表为空，没有记录）");
        scanner.close();
        table.close();
    }

    /** 任务3a：给表添加列族（等价 shell: alter '表',{NAME=>'列族'}） */
    public static void addColumnFamily(String tableName, String family) throws IOException {
        admin.addColumnFamily(TableName.valueOf(tableName), ColumnFamilyDescriptorBuilder.of(family));
        System.out.println("[任务3] 表 " + tableName + " 添加列族 " + family
                + "（等价 shell: alter '" + tableName + "',{NAME=>'" + family + "'}）");
    }

    /** 任务3b：删除表的列族（等价 shell: alter '表','delete'=>'列族'） */
    public static void deleteColumnFamily(String tableName, String family) throws IOException {
        admin.deleteColumnFamily(TableName.valueOf(tableName), Bytes.toBytes(family));
        System.out.println("[任务3] 表 " + tableName + " 删除列族 " + family
                + "（等价 shell: alter '" + tableName + "','delete'=>'" + family + "'）");
    }

    /** 任务3c：向指定行添加一列（写入单元格，等价 shell: put） */
    public static void addColumn(String tableName, String row, String column, String value) throws IOException {
        Table table = connection.getTable(TableName.valueOf(tableName));
        String[] col = column.split(":", 2);
        Put put = new Put(Bytes.toBytes(row));
        put.addColumn(Bytes.toBytes(col[0]),
                col.length == 2 ? Bytes.toBytes(col[1]) : Bytes.toBytes(""),
                Bytes.toBytes(value));
        table.put(put);
        table.close();
        System.out.println("[任务3] 表 " + tableName + " 行 " + row + " 添加列 " + column + " = " + value
                + "（等价 shell: put '" + tableName + "','" + row + "','" + column + "','" + value + "'）");
    }

    /** 任务3d：删除指定行的指定列（等价 shell: delete '表','行','列'） */
    public static void deleteColumn(String tableName, String row, String column) throws IOException {
        Table table = connection.getTable(TableName.valueOf(tableName));
        String[] col = column.split(":", 2);
        Delete delete = new Delete(Bytes.toBytes(row));
        if (col.length == 2) {
            delete.addColumn(Bytes.toBytes(col[0]), Bytes.toBytes(col[1]));
        } else {
            delete.addFamily(Bytes.toBytes(col[0]));
        }
        table.delete(delete);
        table.close();
        System.out.println("[任务3] 表 " + tableName + " 行 " + row + " 删除列 " + column
                + "（等价 shell: delete '" + tableName + "','" + row + "','" + column + "'）");
    }

    /** 任务4：清空指定表的所有记录数据（等价 shell: truncate '表'） */
    public static void truncateTable(String tableName) throws IOException {
        TableName tn = TableName.valueOf(tableName);
        if (admin.isTableEnabled(tn)) {
            admin.disableTable(tn);
        }
        admin.truncateTable(tn, true);
        System.out.println("[任务4] 清空表 " + tableName + "（等价 shell 命令: truncate '" + tableName + "'）");
    }

    /** 任务5：统计表的行数（等价 shell: count '表'） */
    public static long countRows(String tableName) throws IOException {
        Table table = connection.getTable(TableName.valueOf(tableName));
        ResultScanner scanner = table.getScanner(new Scan());
        long count = 0;
        for (Result r : scanner) {
            count++;
        }
        scanner.close();
        table.close();
        return count;
    }

    public static void main(String[] args) throws IOException {
        init();
        System.out.println("========== 第一部分(一)：Java API 完成 5 个任务 ==========");
        System.out.println();

        // 任务1：列出所有表的相关信息
        listTables();
        System.out.println();

        // 任务2：终端显示指定表的所有记录数据
        scanTable("Student");
        System.out.println();

        // 任务3：向已创建好的表添加和删除指定的列族或列
        addColumnFamily("Student", "score");                    // 添加列族 score
        addColumn("Student", "Zhangsan", "score:Math", "90");   // 添加列 score:Math
        scanTable("Student");                                   // 查看添加后的效果
        deleteColumn("Student", "Zhangsan", "score:Math");      // 删除指定列
        deleteColumnFamily("Student", "score");                 // 删除列族
        System.out.println();

        // 任务5：统计表行数（先统计，再演示清空）
        System.out.println("[任务5] Student 表行数 = " + countRows("Student") + "（等价 shell: count 'Student'）");
        System.out.println("[任务5] SC 表行数 = " + countRows("SC") + "（等价 shell: count 'SC'）");
        System.out.println();

        // 任务4：清空指定表的所有记录数据
        truncateTable("Student");
        System.out.println("[任务5] truncate 之后 Student 表行数 = " + countRows("Student"));
        scanTable("Student");

        close();
        System.out.println();
        System.out.println("5 个任务全部完成。注意：Student 表已被清空，请运行 HBaseOperation restore 恢复数据。");
    }
}
