import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Date;
import java.util.Scanner;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;

/**
 * 实验一 第二部分：熟悉常用的 HDFS 操作（Java API 实现）
 *
 * 本程序用 HDFS 官方 Java API（org.apache.hadoop.fs.FileSystem）实现实验要求的
 * 10 个功能，与 hadoop fs 系列 Shell 命令一一对应，运行后按菜单选择即可。
 *
 * 编译运行（需先启动 HDFS）：
 *   export CLASSPATH=$(hadoop classpath)
 *   javac HdfsOperator.java
 *   java HdfsOperator
 *
 * 注意：如果程序报 "Permission denied"，说明当前用户对 / 下目录没有写权限，
 *       请先执行  hdfs dfs -mkdir -p /user/<你的用户名>  并把操作路径换到该目录下。
 */
public class HdfsOperator {

    private static FileSystem fs;
    private static Scanner sc = new Scanner(System.in);

    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        fs = FileSystem.get(conf);   // conf 自动读取 core-site.xml，连到 hdfs://localhost:9000
        System.out.println("========== HDFS 常用操作（Java API 版） ==========");
        System.out.println("1. 上传文件（存在则追加或覆盖）  " +
                           "2. 下载文件（重名自动改名）");
        System.out.println("3. 输出文件内容到终端           " +
                           "4. 显示指定文件的信息");
        System.out.println("5. 递归输出目录下所有文件信息   " +
                           "6. 创建/删除文件（父目录自动建）");
        System.out.println("7. 创建/删除目录（空目录才删）  " +
                           "8. 向文件追加内容（开头/结尾）");
        System.out.println("9. 删除指定文件                 " +
                           "10. 移动文件（源->目的）");
        System.out.println("0. 退出");
        while (true) {
            System.out.print("\n请输入功能编号：");
            String choice = sc.nextLine().trim();
            try {
                switch (choice) {
                    case "1":  upload();        break;
                    case "2":  download();      break;
                    case "3":  catFile();       break;
                    case "4":  showFileInfo();  break;
                    case "5":  listRecursive(); break;
                    case "6":  createDeleteFile(); break;
                    case "7":  createDeleteDir();  break;
                    case "8":  appendContent(); break;
                    case "9":  deleteFile();    break;
                    case "10": moveFile();      break;
                    case "0":
                        fs.close();
                        System.out.println("再见！");
                        return;
                    default:
                        System.out.println("无效编号，请重新输入。");
                }
            } catch (Exception e) {
                System.out.println("操作出错：" + e.getMessage());
            }
        }
    }

    /* 1. 向 HDFS 上传文件；若已存在，由用户选择追加到末尾或覆盖 */
    private static void upload() throws IOException {
        System.out.print("输入本地文件路径（如 /home/lixinze/.bashrc）：");
        String local = sc.nextLine().trim();
        System.out.print("输入 HDFS 目标路径（如 /user/lixinze/test/.bashrc）：");
        String remote = sc.nextLine().trim();
        Path localPath = new Path(local);
        Path remotePath = new Path(remote);
        if (!fs.exists(remotePath)) {
            fs.copyFromLocalFile(false, true, localPath, remotePath);
            System.out.println("上传完成：" + remote);
        } else {
            System.out.print("目标文件已存在，追加到末尾请输入 a，覆盖请输入 c：");
            String op = sc.nextLine().trim();
            if ("c".equalsIgnoreCase(op)) {
                fs.copyFromLocalFile(false, true, localPath, remotePath);
                System.out.println("已覆盖：" + remote);
            } else {
                appendLocalToRemote(localPath, remotePath);
                System.out.println("已追加到：" + remote);
            }
        }
    }

    /* 2. 从 HDFS 下载文件；本地重名时自动改名 */
    private static void download() throws IOException {
        System.out.print("输入 HDFS 源文件路径：");
        String remote = sc.nextLine().trim();
        System.out.print("输入本地保存路径：");
        String local = sc.nextLine().trim();
        File f = new File(local);
        if (f.exists()) {
            int i = 1;
            String base = local, ext = "";
            int dot = local.lastIndexOf('.');
            if (dot > local.lastIndexOf('/')) {   // 带扩展名时拆开
                base = local.substring(0, dot);
                ext = local.substring(dot);
            }
            while (f.exists()) {
                f = new File(base + "(" + i + ")" + ext);
                i++;
            }
            System.out.println("本地已存在同名文件，自动重命名为：" + f.getPath());
        }
        fs.copyToLocalFile(false, new Path(remote), new Path(f.getPath()), false);
        System.out.println("下载完成：" + f.getPath());
    }

    /* 3. 把 HDFS 中文件内容输出到终端 */
    private static void catFile() throws IOException {
        System.out.print("输入 HDFS 文件路径：");
        String remote = sc.nextLine().trim();
        FSDataInputStream in = fs.open(new Path(remote));
        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        String line;
        while ((line = br.readLine()) != null) {
            System.out.println(line);
        }
        br.close();
    }

    /* 4. 显示指定文件的权限、大小、创建时间、路径等信息 */
    private static void showFileInfo() throws IOException {
        System.out.print("输入 HDFS 文件路径：");
        String remote = sc.nextLine().trim();
        FileStatus st = fs.getFileStatus(new Path(remote));
        System.out.println("路径：" + st.getPath());
        System.out.println("权限：" + st.getPermission());
        System.out.println("所有者：" + st.getOwner() + ":" + st.getGroup());
        System.out.println("大小：" + st.getLen() + " 字节");
        System.out.println("修改时间：" + new Date(st.getModificationTime()));
        System.out.println("块大小：" + st.getBlockSize() + " 字节");
        System.out.println("副本数：" + st.getReplication());
    }

    /* 5. 递归输出目录下所有文件的信息 */
    private static void listRecursive() throws IOException {
        System.out.print("输入 HDFS 目录路径：");
        String remote = sc.nextLine().trim();
        RemoteIterator<LocatedFileStatus> it = fs.listFiles(new Path(remote), true);
        while (it.hasNext()) {
            LocatedFileStatus st = it.next();
            System.out.println(st.getPath() + "  权限=" + st.getPermission()
                    + "  大小=" + st.getLen() + "B  时间="
                    + new Date(st.getModificationTime()));
        }
    }

    /* 6. 创建或删除文件；父目录不存在时自动创建 */
    private static void createDeleteFile() throws IOException {
        System.out.print("创建文件输入 c，删除文件输入 d：");
        String op = sc.nextLine().trim();
        System.out.print("输入 HDFS 文件路径（父目录不存在会自动创建）：");
        String remote = sc.nextLine().trim();
        Path p = new Path(remote);
        if ("c".equalsIgnoreCase(op)) {
            fs.mkdirs(p.getParent());          // 自动创建父目录
            if (!fs.exists(p)) {
                FSDataOutputStream out = fs.create(p, true);
                out.close();
            }
            System.out.println("文件已创建：" + remote);
        } else {
            System.out.println(fs.delete(p, false) ? "文件已删除：" + remote
                                                 : "删除失败（文件可能不存在）");
        }
    }

    /* 7. 创建或删除目录；删除时目录为空才删，非空不删 */
    private static void createDeleteDir() throws IOException {
        System.out.print("创建目录输入 c，删除目录输入 d：");
        String op = sc.nextLine().trim();
        System.out.print("输入 HDFS 目录路径：");
        String remote = sc.nextLine().trim();
        Path p = new Path(remote);
        if ("c".equalsIgnoreCase(op)) {
            fs.mkdirs(p);                      // 父目录不存在时自动创建
            System.out.println("目录已创建：" + remote);
        } else {
            if (!fs.exists(p)) {
                System.out.println("目录不存在：" + remote);
                return;
            }
            if (!fs.isDirectory(p)) {
                System.out.println("该路径不是目录：" + remote);
                return;
            }
            if (fs.listStatus(p).length > 0) {
                System.out.println("目录非空，按规则不删除：" + remote);
            } else {
                fs.delete(p, false);
                System.out.println("空目录已删除：" + remote);
            }
        }
    }

    /* 8. 向 HDFS 中文件追加内容；开头或结尾由用户指定 */
    private static void appendContent() throws IOException {
        System.out.print("输入 HDFS 文件路径：");
        String remote = sc.nextLine().trim();
        System.out.print("输入要追加的内容：");
        String content = sc.nextLine();
        System.out.print("追加到开头输入 h，追加到结尾输入 t：");
        String op = sc.nextLine().trim();
        Path p = new Path(remote);
        if ("h".equalsIgnoreCase(op)) {          // 开头：读出原文，新内容写前面
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(fs.open(p), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            br.close();
            FSDataOutputStream out = fs.create(p, true);
            out.write(content.getBytes("UTF-8"));
            out.write("\n".getBytes("UTF-8"));
            out.write(sb.toString().getBytes("UTF-8"));
            out.close();
        } else {                                  // 结尾：HDFS 原生 append
            FSDataOutputStream out = fs.append(p);
            out.write(content.getBytes("UTF-8"));
            out.write("\n".getBytes("UTF-8"));
            out.close();
        }
        System.out.println("追加完成：" + remote);
    }

    /* 9. 删除 HDFS 中指定文件 */
    private static void deleteFile() throws IOException {
        System.out.print("输入 HDFS 文件路径：");
        String remote = sc.nextLine().trim();
        System.out.println(fs.delete(new Path(remote), false) ? "文件已删除：" + remote
                                                              : "删除失败（文件可能不存在）");
    }

    /* 10. 将 HDFS 文件从源路径移动到目的路径 */
    private static void moveFile() throws IOException {
        System.out.print("输入源路径：");
        String src = sc.nextLine().trim();
        System.out.print("输入目的路径：");
        String dst = sc.nextLine().trim();
        Path sp = new Path(src), dp = new Path(dst);
        if (!fs.exists(sp)) {
            System.out.println("源文件不存在：" + src);
            return;
        }
        fs.rename(sp, dp);
        System.out.println("移动完成：" + src + " -> " + dst);
    }

    /* 辅助：把本地文件内容追加到 HDFS 文件末尾 */
    private static void appendLocalToRemote(Path local, Path remote) throws IOException {
        FSDataOutputStream out = fs.append(remote);
        FileInputStream in = new FileInputStream(new File(local.toString()));
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        out.close();
    }
}