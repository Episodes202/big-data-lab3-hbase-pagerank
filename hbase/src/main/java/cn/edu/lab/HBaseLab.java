package cn.edu.lab;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptor;
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
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.filter.FirstKeyOnlyFilter;
import org.apache.hadoop.hbase.util.Bytes;

/** 实验三：HBase 2.5 Java API。姓名：赵君豪，学号：202400390041，班级：1班。 */
public final class HBaseLab implements Closeable {
    private final Connection connection;

    public HBaseLab(Configuration configuration) throws IOException {
        connection = ConnectionFactory.createConnection(configuration);
    }

    /** 列限定符不是预定义模式；fields 同时接受 Info 或 Info:S_No。 */
    public void createTable(String tableName, String[] fields) throws IOException {
        if (fields == null || fields.length == 0) {
            throw new IllegalArgumentException("至少指定一个列族");
        }
        Set<String> families = new TreeSet<>();
        for (String field : fields) families.add(splitColumn(field)[0]);
        TableName name = TableName.valueOf(tableName);
        TableDescriptorBuilder builder = TableDescriptorBuilder.newBuilder(name);
        for (String family : families) {
            builder.setColumnFamily(ColumnFamilyDescriptorBuilder.of(family));
        }
        try (Admin admin = connection.getAdmin()) {
            if (admin.tableExists(name)) {
                if (admin.isTableEnabled(name)) admin.disableTable(name);
                admin.deleteTable(name);
            }
            admin.createTable(builder.build());
        }
    }

    /** family 无限定符时采用空限定符，符合 HBase 的 cell 模型。 */
    public void addRecord(String tableName, String row, String[] fields, String[] values)
            throws IOException {
        if (fields == null || values == null || fields.length != values.length || fields.length == 0) {
            throw new IllegalArgumentException("fields 与 values 必须非空且长度相等");
        }
        if (row == null || row.isEmpty()) throw new IllegalArgumentException("row 不能为空");
        Put put = new Put(Bytes.toBytes(row));
        for (int i = 0; i < fields.length; i++) {
            String[] column = splitColumn(fields[i]);
            if (values[i] == null) throw new IllegalArgumentException("value 不能为 null");
            put.addColumn(Bytes.toBytes(column[0]), Bytes.toBytes(column[1]), Bytes.toBytes(values[i]));
        }
        try (Table table = connection.getTable(TableName.valueOf(tableName))) { table.put(put); }
    }

    /**
     * 返回 row -> (完整列名 -> 值)。某行缺少目标列/列族时，该行映射值为 null。
     * 必须扫描全行；若对 Scan.addColumn 直接投影，会漏掉只有其他列的行。
     */
    public Map<String, Map<String, String>> scanColumn(String tableName, String column)
            throws IOException {
        String[] parts = splitColumn(column);
        boolean exactColumn = column.indexOf(':') >= 0;
        byte[] family = Bytes.toBytes(parts[0]);
        byte[] qualifier = Bytes.toBytes(parts[1]);
        Map<String, Map<String, String>> rows = new LinkedHashMap<>();
        try (Table table = connection.getTable(TableName.valueOf(tableName));
             ResultScanner scanner = table.getScanner(new Scan())) {
            for (Result result : scanner) {
                Map<String, String> columns = new TreeMap<>();
                for (Cell cell : result.rawCells()) {
                    if (CellUtil.matchingFamily(cell, family)
                            && (!exactColumn || CellUtil.matchingQualifier(cell, qualifier))) {
                        columns.put(parts[0] + ":" + Bytes.toString(CellUtil.cloneQualifier(cell)),
                                Bytes.toString(CellUtil.cloneValue(cell)));
                    }
                }
                rows.put(Bytes.toString(result.getRow()), columns.isEmpty() ? null : columns);
            }
        }
        return rows;
    }

    /** 保留题目要求的三参数签名，缺少的新值从标准输入读取。 */
    public void modifyData(String tableName, String row, String column) throws IOException {
        System.out.print("请输入 " + tableName + "/" + row + "/" + column + " 的新值: ");
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String newValue = input.readLine();
        if (newValue == null) throw new IOException("标准输入结束，未提供新值");
        modifyData(tableName, row, column, newValue);
    }

    /** 非交互重载，便于可复现脚本与自动验证调用。 */
    public void modifyData(String tableName, String row, String column, String newValue)
            throws IOException {
        addRecord(tableName, row, new String[]{column}, new String[]{newValue});
    }

    public void deleteRow(String tableName, String row) throws IOException {
        try (Table table = connection.getTable(TableName.valueOf(tableName))) {
            table.delete(new Delete(Bytes.toBytes(row)));
        }
    }

    public List<String> listTables() throws IOException {
        List<String> descriptions = new ArrayList<>();
        try (Admin admin = connection.getAdmin()) {
            for (TableDescriptor descriptor : admin.listTableDescriptors()) {
                List<String> families = new ArrayList<>();
                for (ColumnFamilyDescriptor family : descriptor.getColumnFamilies()) {
                    families.add(family.getNameAsString());
                }
                descriptions.add(descriptor.getTableName().getNameAsString() + " " + families);
            }
        }
        return descriptions;
    }

    public void scanTable(String tableName) throws IOException {
        try (Table table = connection.getTable(TableName.valueOf(tableName));
             ResultScanner scanner = table.getScanner(new Scan())) {
            for (Result result : scanner) {
                for (Cell cell : result.rawCells()) {
                    System.out.println(Bytes.toString(result.getRow()) + "\t"
                            + Bytes.toString(CellUtil.cloneFamily(cell)) + ":"
                            + Bytes.toString(CellUtil.cloneQualifier(cell)) + "\t"
                            + Bytes.toString(CellUtil.cloneValue(cell)));
                }
            }
        }
    }

    public void addFamily(String tableName, String family) throws IOException {
        try (Admin admin = connection.getAdmin()) {
            admin.addColumnFamily(TableName.valueOf(tableName), ColumnFamilyDescriptorBuilder.of(family));
        }
    }

    public void deleteFamily(String tableName, String family) throws IOException {
        try (Admin admin = connection.getAdmin()) {
            admin.deleteColumnFamily(TableName.valueOf(tableName), Bytes.toBytes(family));
        }
    }

    /** 删除指定行列的全部历史版本，避免旧值重新可见。 */
    public void deleteColumn(String tableName, String row, String column) throws IOException {
        if (column.indexOf(':') < 0) throw new IllegalArgumentException("列名格式须为 family:qualifier");
        String[] parts = splitColumn(column);
        Delete delete = new Delete(Bytes.toBytes(row));
        delete.addColumns(Bytes.toBytes(parts[0]), Bytes.toBytes(parts[1]));
        try (Table table = connection.getTable(TableName.valueOf(tableName))) { table.delete(delete); }
    }

    public void clearTable(String tableName) throws IOException {
        TableName name = TableName.valueOf(tableName);
        try (Admin admin = connection.getAdmin()) {
            if (admin.isTableEnabled(name)) admin.disableTable(name);
            admin.truncateTable(name, true);
        }
    }

    public long countRows(String tableName) throws IOException {
        long count = 0;
        Scan scan = new Scan().setFilter(new FirstKeyOnlyFilter());
        try (Table table = connection.getTable(TableName.valueOf(tableName));
             ResultScanner scanner = table.getScanner(scan)) {
            for (Result ignored : scanner) count++;
        }
        return count;
    }

    public String getValue(String tableName, String row, String column) throws IOException {
        String[] parts = splitColumn(column);
        try (Table table = connection.getTable(TableName.valueOf(tableName))) {
            byte[] value = table.get(new Get(Bytes.toBytes(row))).getValue(
                    Bytes.toBytes(parts[0]), Bytes.toBytes(parts[1]));
            return value == null ? null : Bytes.toString(value);
        }
    }

    private static String[] splitColumn(String column) {
        if (column == null || column.isEmpty()) throw new IllegalArgumentException("列名不能为空");
        String[] parts = column.split(":", 2);
        if (parts[0].isEmpty()) throw new IllegalArgumentException("列族不能为空");
        return new String[]{parts[0], parts.length == 2 ? parts[1] : ""};
    }

    @Override public void close() throws IOException { connection.close(); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("CHECK FAILED: " + message);
        System.out.println("PASS: " + message);
    }

    /** 主数据集。原题重复学号已在 data/Student_original.tsv 保留并在 README 说明。 */
    private void loadData() throws IOException {
        createTable("Student", new String[]{"Info", "Score"});
        createTable("Course", new String[]{"Info"});
        createTable("SC", new String[]{"Info"});
        String[][] students = {{"2015001", "Zhangsan", "male", "23"},
                {"2015002", "Mary", "female", "22"}, {"2015003", "Lisi", "male", "24"}};
        for (String[] student : students) {
            addRecord("Student", student[1], new String[]{"Info:S_No", "Info:S_Name", "Info:S_Sex", "Info:S_Age"}, student);
        }
        String[][] courses = {{"123001", "Math", "2.0"}, {"123002", "Computer Science", "5.0"},
                {"123003", "English", "3.0"}};
        for (String[] course : courses) {
            addRecord("Course", course[0], new String[]{"Info:C_No", "Info:C_Name", "Info:C_Credit"}, course);
        }
        String[][] selections = {{"2015001", "123001", "86"}, {"2015001", "123003", "69"},
                {"2015002", "123002", "77"}, {"2015002", "123003", "99"},
                {"2015003", "123001", "98"}, {"2015003", "123002", "95"}};
        for (String[] selection : selections) {
            addRecord("SC", selection[0] + "_" + selection[1],
                    new String[]{"Info:SC_Sno", "Info:SC_Cno", "Info:SC_Score"}, selection);
        }
        addRecord("Student", "Zhangsan", new String[]{"Score:Math", "Score:English"}, new String[]{"86", "69"});
        addRecord("Student", "Mary", new String[]{"Score:Computer Science", "Score:English"}, new String[]{"77", "99"});
        addRecord("Student", "Lisi", new String[]{"Score:Math", "Score:Computer Science"}, new String[]{"98", "95"});
    }

    private void demo() throws IOException {
        loadData();
        System.out.println("TABLES " + listTables());
        for (String table : Arrays.asList("Student", "Course", "SC")) {
            System.out.println("=== " + table + " ==="); scanTable(table);
        }
        require(countRows("Student") == 3, "Student rows = 3");
        require(countRows("Course") == 3, "Course rows = 3");
        require(countRows("SC") == 6, "SC rows = 6");
        System.out.println("Score family = " + scanColumn("Student", "Score"));
        Map<String, Map<String, String>> math = scanColumn("Student", "Score:Math");
        System.out.println("Score:Math = " + math);
        require(math.containsKey("Mary") && math.get("Mary") == null, "missing Mary Math returns null");
        require("86".equals(math.get("Zhangsan").get("Score:Math")), "Zhangsan Math = 86");
        modifyData("Student", "Zhangsan", "Score:Math", "90");
        require("90".equals(getValue("Student", "Zhangsan", "Score:Math")), "modify Math to 90");
        modifyData("Student", "Zhangsan", "Score:Math", "86");
        addFamily("Student", "Temp");
        addRecord("Student", "Zhangsan", new String[]{"Temp:note"}, new String[]{"demo"});
        require("demo".equals(getValue("Student", "Zhangsan", "Temp:note")), "add family and qualifier");
        deleteColumn("Student", "Zhangsan", "Temp:note");
        require(getValue("Student", "Zhangsan", "Temp:note") == null, "delete all versions of a column");
        deleteFamily("Student", "Temp");
        try (Admin admin = connection.getAdmin()) {
            require(!admin.getDescriptor(TableName.valueOf("Student")).hasColumnFamily(Bytes.toBytes("Temp")),
                    "delete family Temp");
        }
        addRecord("Student", "Temporary", new String[]{"Info:S_Name"}, new String[]{"Temporary"});
        deleteRow("Student", "Temporary");
        require(countRows("Student") == 3, "delete temporary row");
        createTable("Lab3Scratch", new String[]{"Info:value"});
        addRecord("Lab3Scratch", "r1", new String[]{"Info:value"}, new String[]{"1"});
        clearTable("Lab3Scratch");
        require(countRows("Lab3Scratch") == 0, "truncate preserves table and removes rows");
        addRecord("Lab3Scratch", "r2", new String[]{"Info:value"}, new String[]{"2"});
        createTable("Lab3Scratch", new String[]{"Info"});
        require(countRows("Lab3Scratch") == 0, "createTable replaces existing table");
        System.out.println("ALL HBASE CHECKS PASSED");
    }

    public static void main(String[] args) throws IOException {
        Configuration configuration = HBaseConfiguration.create();
        String quorum = System.getenv("HBASE_ZOOKEEPER_QUORUM");
        if (quorum != null && !quorum.isEmpty()) configuration.set("hbase.zookeeper.quorum", quorum);
        try (HBaseLab lab = new HBaseLab(configuration)) {
            if (args.length == 0 || "demo".equals(args[0])) lab.demo();
            else if (args.length == 4 && "modify".equals(args[0])) lab.modifyData(args[1], args[2], args[3]);
            else throw new IllegalArgumentException("用法: HBaseLab [demo | modify table row family:qualifier]");
        }
    }
}
