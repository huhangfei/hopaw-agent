package com.agent.hopaw.tool.database;

import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.SearchBehavior;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用数据库操作工具。
 * <p>
 * 不内置任何 JDBC 驱动，运行前需通过 {@link #loadJdbcDriverFromJar(String)} 加载驱动 JAR。
 * 根据 JDBC URL 前缀自动适配驱动类名。所有连接参数由调用者每次必传。
 * </p>
 *
 * <h3>驱动放置规范</h3>
 * JDBC 驱动 JAR 应放置在项目根目录下的 {@code jdbc-drivers/} 文件夹内，例如：
 * <pre>
 *   ./jdbc-drivers/mysql-connector-j-8.0.33.jar
 *   ./jdbc-drivers/postgresql-42.7.1.jar
 *   ./jdbc-drivers/ojdbc11.jar
 * </pre>
 * 工具会扫描该目录并按需动态加载；如未在 jdbc-drivers/ 目录中，可直接传绝对路径给 loadJdbcDriverFromJar。
 *
 * <h3>使用流程</h3>
 * <ol>
 *   <li>先调用 loadJdbcDriverFromJar 加载驱动 JAR（如有需要）</li>
 *   <li>再调用 executeSelectQuery / executeUpdateSql / listDatabaseTables / describeDatabaseTable</li>
 * </ol>
 *
 * <h3>支持的 URL 前缀 → 驱动类映射</h3>
 * <ul>
 *   <li>jdbc:mysql:// → com.mysql.cj.jdbc.Driver</li>
 *   <li>jdbc:mariadb:// → org.mariadb.jdbc.Driver</li>
 *   <li>jdbc:postgresql:// → org.postgresql.Driver</li>
 *   <li>jdbc:sqlserver:// → com.microsoft.sqlserver.jdbc.SQLServerDriver</li>
 *   <li>jdbc:h2: → org.h2.Driver</li>
 *   <li>jdbc:sqlite: → org.sqlite.JDBC</li>
 * </ul>
 */
public class DatabaseTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(DatabaseTool.class);

    /** 项目根目录下的 JDBC 驱动默认目录 */
    private static final String DRIVER_DIR_NAME = "jdbc-drivers";

    /** JDBC URL 前缀 → 驱动类名（驱动加载后由 ServiceLoader 扫描写入） */
    private static final Map<String, String> DRIVER_MAP = new LinkedHashMap<>();

    /** 已加载的驱动 JAR 对应的 ClassLoader，防止 GC 回收 */
    private static final Map<String, URLClassLoader> LOADED_DRIVERS = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "databaseTool";
    }

    @Override
    public String getDescription() {
        return "通用数据库操作工具，驱动从本地 JAR 动态加载，支持 MySQL / PostgreSQL / MariaDB / SQL Server / H2 / SQLite 等";
    }

    @Override
    public String getKeyword() {
        return "数据库 查询 SQL JDBC";
    }

    @Override
    public String getIcon() {
        return "database-tool.svg";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    // ========== Tool 方法 ==========

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {
            "加载JDBC驱动JAR",
            "从本地 .jar 文件路径动态加载 JDBC 驱动。调用 query / execute 前若驱动未加载可先调用本方法。"
    })
    public String loadJdbcDriverFromJar(
            @P(description = "JDBC 驱动 JAR 文件的绝对路径，例如 D:/drivers/mysql-connector-j-8.0.33.jar") String jarPath) {
        if (jarPath == null || jarPath.isBlank()) {
            return "加载失败：JAR 路径不能为空";
        }
        File jar = new File(jarPath);
        if (!jar.exists() || !jar.isFile()) {
            return "加载失败：文件不存在 - " + jarPath;
        }
        if (!jar.getName().toLowerCase().endsWith(".jar")) {
            return "加载失败：文件扩展名必须是 .jar";
        }

        try {
            URL url = jar.toURI().toURL();
            URLClassLoader loader = new URLClassLoader(
                    new URL[]{url},
                    Thread.currentThread().getContextClassLoader()
            );
            LOADED_DRIVERS.put(jarPath, loader);

            // 通过 ServiceLoader 触发 java.sql.Driver 自动注册
            int count = 0;
            StringBuilder drivers = new StringBuilder();
            ServiceLoader<Driver> sl = ServiceLoader.load(Driver.class, loader);
            for (Driver d : sl) {
                try {
                    DriverManager.registerDriver(new DriverWrapper(d));
                    if (drivers.length() > 0) drivers.append(", ");
                    drivers.append(d.getClass().getName());
                    count++;

                    // 反向推断：扫描驱动类源码注释/注解太重，
                    // 直接通过 ServiceLoader 拿到的 Driver 类名映射常见 JDBC URL 前缀。
                    String driverClassName = d.getClass().getName();
                    inferAndRegisterUrlPrefix(driverClassName);
                } catch (Exception ignored) {
                    // 可能已注册
                }
            }

            if (count == 0) {
                return "警告：未能从 JAR 中自动发现 JDBC Driver。\n"
                        + "已加载 JAR 到 classpath，请确认该 JAR 包含 META-INF/services/java.sql.Driver 文件。";
            }
            return "成功加载 " + count + " 个 JDBC 驱动：" + drivers;
        } catch (Exception e) {
            log.error("加载驱动 JAR 失败 path={}", jarPath, e);
            return "加载失败：" + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {
            "列出已加载的JDBC驱动",
            "查看当前进程中已加载的 JDBC 驱动 JAR、对应驱动类与支持的 URL 前缀。"
    })
    public String listLoadedJdbcDrivers() {
        StringBuilder sb = new StringBuilder();

        // 顶部：驱动文件放置目录
        File driverDir = resolveDriverDir();
        sb.append("=== 驱动文件目录 ===\n");
        sb.append("  目录：").append(driverDir.getAbsolutePath()).append("\n");
        if (driverDir.exists() && driverDir.isDirectory()) {
            File[] jars = driverDir.listFiles((f) -> f.isFile() && f.getName().toLowerCase().endsWith(".jar"));
            if (jars == null || jars.length == 0) {
                sb.append("  状态：目录存在，但无 .jar 文件\n");
            } else {
                sb.append("  状态：找到 ").append(jars.length).append(" 个驱动 JAR\n");
                Arrays.sort(jars, Comparator.comparing(File::getName));
                for (File jar : jars) {
                    boolean loaded = LOADED_DRIVERS.values().stream().anyMatch(loader -> {
                        URL[] urls = loader.getURLs();
                        if (urls.length == 0) return false;
                        try {
                            return new File(urls[0].toURI()).equals(jar);
                        } catch (Exception e) {
                            return false;
                        }
                    });
                    sb.append("    ")
                            .append(loaded ? "[已加载] " : "[未加载] ")
                            .append(jar.getName())
                            .append("  (").append(formatSize(jar.length())).append(")")
                            .append("\n");
                }
            }
        } else {
            sb.append("  状态：目录不存在\n");
            sb.append("  提示：请在项目根目录下创建 ").append(DRIVER_DIR_NAME).append("/ 文件夹，将驱动 JAR 放入其中\n");
        }

        // 已加载的 ClassLoader
        sb.append("\n=== 已加载的 JDBC 驱动 JAR ===\n");
        if (LOADED_DRIVERS.isEmpty()) {
            sb.append("  (无)\n");
        } else {
            int idx = 1;
            for (Map.Entry<String, URLClassLoader> e : LOADED_DRIVERS.entrySet()) {
                URL[] urls = e.getValue().getURLs();
                String jarName = urls.length > 0 ? new File(urls[0].getFile()).getName() : "(unknown)";
                sb.append("  ").append(idx++).append(". ")
                        .append(jarName).append("\n")
                        .append("     路径: ").append(e.getKey()).append("\n")
                        .append("     ClassLoader: ").append(e.getValue().getClass().getName()).append("\n");
            }
        }

        // URL 前缀 → 驱动类
        sb.append("\n=== 已注册的 URL 前缀 → 驱动类 ===\n");
        if (DRIVER_MAP.isEmpty()) {
            sb.append("  (无)\n");
        } else {
            List<String> prefixes = new ArrayList<>(DRIVER_MAP.keySet());
            Collections.sort(prefixes);
            for (String prefix : prefixes) {
                sb.append("  ").append(String.format("%-22s", prefix))
                        .append(" → ").append(DRIVER_MAP.get(prefix)).append("\n");
            }
        }

        // DriverManager 中已注册的驱动
        sb.append("\n=== DriverManager 中已注册的驱动 ===\n");
        Enumeration<Driver> registered = DriverManager.getDrivers();
        int count = 0;
        while (registered.hasMoreElements()) {
            Driver d = registered.nextElement();
            sb.append("  ").append(d.getClass().getName()).append("\n");
            count++;
        }
        if (count == 0) {
            sb.append("  (无)\n");
        } else {
            sb.append("共 ").append(count).append(" 个驱动\n");
        }

        return sb.toString();
    }

    /**
     * 解析 JDBC 驱动目录。优先取项目根目录下的 jdbc-drivers/，找不到时回退到 user.dir。
     */
    private static File resolveDriverDir() {
        // user.dir 通常是应用启动目录 = 项目根目录
        File dir = new File(System.getProperty("user.dir"), DRIVER_DIR_NAME);
        if (dir.exists() && dir.isDirectory()) {
            return dir;
        }
        // 备选：向上寻找 1~3 级父目录中的 jdbc-drivers
        File parent = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 3 && parent != null; i++) {
            File candidate = new File(parent, DRIVER_DIR_NAME);
            if (candidate.exists() && candidate.isDirectory()) {
                return candidate;
            }
            parent = parent.getParentFile();
        }
        return dir; // 不存在也返回原路径，让上层做提示
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }

    /**
     * CSV 字段转义：若字段包含逗号、双引号或换行符，用双引号包裹并转义内部双引号。
     */
    private static String csvEscape(String value) {
        if (value == null) return "";
        boolean needsQuote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (needsQuote) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {
            "测试数据库连接",
            "使用给定的 JDBC URL、用户名和密码测试数据库连通性。"
    })
    public String testDatabaseConnection(
            @P(description = "JDBC 连接地址，例如 jdbc:mysql://localhost:3306/mydb") String jdbcUrl,
            @P(description = "数据库用户名") String username,
            @P(description = "数据库密码") String password) {
        try (Connection conn = createConnection(jdbcUrl, username, password)) {
            DatabaseMetaData meta = conn.getMetaData();
            return String.format("连接成功！\n"
                            + "数据库产品：%s %s\n"
                            + "JDBC 驱动：%s %s\n"
                            + "连接 URL：%s",
                    meta.getDatabaseProductName(), meta.getDatabaseProductVersion(),
                    meta.getDriverName(), meta.getDriverVersion(),
                    jdbcUrl);
        } catch (Exception e) {
            log.error("数据库连接测试失败", e);
            return "连接失败：" + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {
            "查询数据库",
            "执行 SELECT 查询并返回结果集。结果以表格形式展示，最多返回指定行数。"
    })
    public String executeSelectQuery(
            @P(description = "JDBC 连接地址") String jdbcUrl,
            @P(description = "数据库用户名") String username,
            @P(description = "数据库密码") String password,
            @P(description = "SELECT 查询语句") String sql,
            @P(description = "最大返回行数，默认100，最大1000", required = false) Integer maxRows) {
        if (sql == null || sql.isBlank()) {
            return "错误：SQL 语句不能为空";
        }
        if (maxRows == null || maxRows <= 0) maxRows = 100;
        if (maxRows > 1000) maxRows = 1000;

        try (Connection conn = createConnection(jdbcUrl, username, password);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setMaxRows(maxRows);
            try (ResultSet rs = stmt.executeQuery()) {
                return formatResultSet(rs, maxRows);
            }
        } catch (SQLException e) {
            log.error("查询失败 sql={}", sql, e);
            return "查询失败：" + e.getMessage();
        } catch (Exception e) {
            log.error("查询失败 sql={}", sql, e);
            return "查询失败：" + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {
            "执行SQL",
            "执行 INSERT / UPDATE / DELETE 或 DDL 语句。返回影响行数或执行结果。"
    })
    public String executeUpdateSql(
            @P(description = "JDBC 连接地址") String jdbcUrl,
            @P(description = "数据库用户名") String username,
            @P(description = "数据库密码") String password,
            @P(description = "要执行的 SQL 语句（INSERT / UPDATE / DELETE / DDL）") String sql) {
        if (sql == null || sql.isBlank()) {
            return "错误：SQL 语句不能为空";
        }

        try (Connection conn = createConnection(jdbcUrl, username, password);
             Statement stmt = conn.createStatement()) {
            boolean isResultSet = stmt.execute(sql);
            if (isResultSet) {
                try (ResultSet rs = stmt.getResultSet()) {
                    return "执行成功，返回结果集：\n" + formatResultSet(rs, 100);
                }
            } else {
                int updateCount = stmt.getUpdateCount();
                return updateCount >= 0
                        ? "执行成功，影响行数：" + updateCount
                        : "执行成功";
            }
        } catch (SQLException e) {
            log.error("执行失败 sql={}", sql, e);
            return "执行失败：" + e.getMessage();
        } catch (Exception e) {
            log.error("执行失败 sql={}", sql, e);
            return "执行失败：" + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {
            "列出数据库表",
            "列出当前数据库中的所有表。"
    })
    public String listDatabaseTables(
            @P(description = "JDBC 连接地址") String jdbcUrl,
            @P(description = "数据库用户名") String username,
            @P(description = "数据库密码") String password,
            @P(description = "按表名过滤，支持 % 通配符，不传则列出全部", required = false) String pattern) {
        if (pattern == null || pattern.isBlank()) pattern = "%";

        try (Connection conn = createConnection(jdbcUrl, username, password)) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();

            List<String> tables = new ArrayList<>();
            try (ResultSet rs = meta.getTables(catalog, schema, pattern, new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String tableType = rs.getString("TABLE_TYPE");
                    String remarks = rs.getString("REMARKS");
                    String line = String.format("%-40s %-10s %s",
                            tableName, tableType, remarks != null ? remarks : "");
                    tables.add(line);
                }
            }

            if (tables.isEmpty()) {
                return "未找到匹配的表（pattern=" + pattern + "）";
            }

            String header = String.format("%-40s %-10s %s", "TABLE_NAME", "TYPE", "REMARKS");
            return "数据库：" + meta.getDatabaseProductName()
                    + " | Schema：" + (schema != null ? schema : "N/A")
                    + "\n" + header + "\n" + String.join("", Collections.nCopies(header.length(), "-")) + "\n"
                    + String.join("\n", tables)
                    + "\n\n共 " + tables.size() + " 个表/视图";
        } catch (SQLException e) {
            log.error("列出表失败", e);
            return "操作失败：" + e.getMessage();
        } catch (Exception e) {
            log.error("列出表失败", e);
            return "操作失败：" + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {
            "查看表结构",
            "查看指定表的列定义、主键、索引信息。"
    })
    public String describeDatabaseTable(
            @P(description = "JDBC 连接地址") String jdbcUrl,
            @P(description = "数据库用户名") String username,
            @P(description = "数据库密码") String password,
            @P(description = "表名") String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return "错误：表名不能为空";
        }

        try (Connection conn = createConnection(jdbcUrl, username, password)) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();

            StringBuilder sb = new StringBuilder();
            sb.append("表：").append(tableName).append("\n");

            List<String> columns = new ArrayList<>();
            Map<String, List<String>> primaryKeys = new LinkedHashMap<>();
            try (ResultSet rs = meta.getColumns(catalog, schema, tableName, "%")) {
                while (rs.next()) {
                    String colName = rs.getString("COLUMN_NAME");
                    String typeName = rs.getString("TYPE_NAME");
                    int colSize = rs.getInt("COLUMN_SIZE");
                    String nullable = "YES".equals(rs.getString("IS_NULLABLE")) ? "NULL" : "NOT NULL";
                    String defaultValue = rs.getString("COLUMN_DEF");
                    String remarks = rs.getString("REMARKS");
                    String autoIncrement = "";
                    if ("YES".equals(rs.getString("IS_AUTOINCREMENT"))) {
                        autoIncrement = " AUTO_INCREMENT";
                    }

                    columns.add(String.format("  %-30s %-20s %-8s %s%s%s",
                            colName, typeName + (colSize > 0 ? "(" + colSize + ")" : ""),
                            nullable,
                            defaultValue != null ? "DEFAULT " + defaultValue + " " : "",
                            autoIncrement,
                            remarks != null && !remarks.isEmpty() ? " -- " + remarks : ""));
                }
            }

            try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, tableName)) {
                while (rs.next()) {
                    String pkName = rs.getString("PK_NAME");
                    String colName = rs.getString("COLUMN_NAME");
                    primaryKeys.computeIfAbsent(pkName != null ? pkName : "PRIMARY", k -> new ArrayList<>()).add(colName);
                }
            }

            sb.append("\n列定义：\n");
            if (columns.isEmpty()) {
                sb.append("  (无列信息，表可能不存在)\n");
            } else {
                columns.forEach(c -> sb.append(c).append("\n"));
            }

            if (!primaryKeys.isEmpty()) {
                sb.append("\n主键：\n");
                primaryKeys.forEach((name, cols) ->
                        sb.append("  ").append(name).append(": ").append(String.join(", ", cols)).append("\n"));
            }

            List<String> indexes = new ArrayList<>();
            try (ResultSet rs = meta.getIndexInfo(catalog, schema, tableName, false, false)) {
                while (rs.next()) {
                    String idxName = rs.getString("INDEX_NAME");
                    String colName = rs.getString("COLUMN_NAME");
                    boolean nonUnique = rs.getBoolean("NON_UNIQUE");
                    if (idxName != null && !primaryKeys.containsKey(idxName)) {
                        indexes.add(String.format("  %-30s %-20s %s",
                                idxName, colName, nonUnique ? "" : "UNIQUE"));
                    }
                }
            }
            if (!indexes.isEmpty()) {
                sb.append("\n索引：\n");
                indexes.stream().distinct().forEach(i -> sb.append(i).append("\n"));
            }

            return sb.toString();
        } catch (SQLException e) {
            log.error("查看表结构失败 table={}", tableName, e);
            return "操作失败：" + e.getMessage();
        } catch (Exception e) {
            log.error("查看表结构失败 table={}", tableName, e);
            return "操作失败：" + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {
            "导出查询结果到CSV",
            "将大查询结果导出为 CSV 文件，保存到项目 exports/ 目录下。" +
            "返回文件路径及下载链接，适合数据量较大的场景。"
    })
    public String exportQueryToCsv(
            @P(description = "JDBC 连接地址") String jdbcUrl,
            @P(description = "数据库用户名") String username,
            @P(description = "数据库密码") String password,
            @P(description = "SELECT 查询语句") String sql,
            @P(description = "最大导出行数，默认100000，最大1000000", required = false) Integer maxRows) {
        if (sql == null || sql.isBlank()) {
            return "错误：SQL 语句不能为空";
        }
        if (maxRows == null || maxRows <= 0) maxRows = 100_000;
        if (maxRows > 1_000_000) maxRows = 1_000_000;

        long startTime = System.currentTimeMillis();

        File exportDir = new File(System.getProperty("user.dir"), "exports");
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            return "导出失败：无法创建导出目录 " + exportDir.getAbsolutePath();
        }

        String fileName = "export_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv";
        File csvFile = new File(exportDir, fileName);

        try (Connection conn = createConnection(jdbcUrl, username, password);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // 尝试设置游标读取，避免一次性加载全部数据到内存
            try { stmt.setFetchSize(1000); } catch (SQLException ignored) {}

            stmt.setMaxRows(maxRows);

            try (ResultSet rs = stmt.executeQuery();
                 java.io.BufferedWriter writer = new java.io.BufferedWriter(
                         new java.io.OutputStreamWriter(new java.io.FileOutputStream(csvFile), "UTF-8"))) {

                // UTF-8 BOM，保证 Excel 正确识别中文
                writer.write('\uFEFF');

                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();

                // 写 CSV 表头
                for (int i = 1; i <= colCount; i++) {
                    if (i > 1) writer.write(',');
                    writer.write(csvEscape(meta.getColumnLabel(i)));
                }
                writer.newLine();

                // 写数据行
                int rowCount = 0;
                while (rs.next() && rowCount < maxRows) {
                    for (int i = 1; i <= colCount; i++) {
                        if (i > 1) writer.write(',');
                        String val = rs.getString(i);
                        writer.write(val != null ? csvEscape(val) : "");
                    }
                    writer.newLine();
                    rowCount++;
                }

                writer.flush();

                long elapsed = System.currentTimeMillis() - startTime;
                String downloadUrl = "/exports/" + fileName;

                return String.format(
                        "导出成功！\n" +
                        "文件路径：%s\n" +
                        "下载链接（可以拼接为a标签的href，点击后下载）：%s\n" +
                        "文件大小：%s\n" +
                        "导出行数：%,d\n" +
                        "列数：%d\n" +
                        "耗时：%,d ms",
                        csvFile.getAbsolutePath(),
                        downloadUrl,
                        formatSize(csvFile.length()),
                        rowCount,
                        colCount,
                        elapsed
                );
            }
        } catch (SQLException e) {
            log.error("导出CSV失败 sql={}", sql, e);
            // 删除不完整的文件
            if (csvFile.exists()) csvFile.delete();
            return "导出失败：" + e.getMessage();
        } catch (java.io.IOException e) {
            log.error("导出CSV写入失败", e);
            if (csvFile.exists()) csvFile.delete();
            return "导出失败（文件写入错误）：" + e.getMessage();
        } catch (Exception e) {
            log.error("导出CSV失败 sql={}", sql, e);
            if (csvFile.exists()) csvFile.delete();
            return "导出失败：" + e.getMessage();
        }
    }

    // ========== 内部方法 ==========

    private Connection createConnection(String jdbcUrl, String username, String password) throws Exception {
        String driverClass = resolveDriver(jdbcUrl);
        if (driverClass == null) {
            throw new Exception(buildUrlUnsupportedMessage(jdbcUrl));
        }
        // 不调用 Class.forName —— 驱动已通过 ServiceLoader 加载并由 DriverManager.registerDriver 注册。
        // Class.forName 使用应用程序 ClassLoader，看不到 URLClassLoader 中的驱动类，反而会抛 ClassNotFoundException。
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    /**
     * 拼接 "URL 前缀不支持" 的引导信息：列出已支持前缀 + 扫描 jdbc-drivers/ 目录。
     */
    private String buildUrlUnsupportedMessage(String jdbcUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("无法识别 JDBC URL 的数据库类型：").append(jdbcUrl).append("\n");
        sb.append("当前已注册的 URL 前缀：").append(DRIVER_MAP.isEmpty() ? "(无)" : DRIVER_MAP.keySet()).append("\n");
        sb.append("请先调用 loadJdbcDriverFromJar 加载对应数据库的驱动 JAR。");

        File driverDir = resolveDriverDir();
        if (driverDir.exists() && driverDir.isDirectory()) {
            File[] jars = driverDir.listFiles((f) -> f.isFile() && f.getName().toLowerCase().endsWith(".jar"));
            if (jars != null && jars.length > 0) {
                sb.append("\n\n提示：").append(driverDir.getAbsolutePath()).append(" 目录下的 JAR：\n");
                Arrays.sort(jars, Comparator.comparing(File::getName));
                for (File jar : jars) {
                    sb.append("  - ").append(jar.getName()).append("\n");
                }
                sb.append("如以上 JAR 包含您需要的驱动，请调用：\n");
                sb.append("  loadJdbcDriverFromJar(\"").append(jars[0].getParentFile().getAbsolutePath().replace('\\', '/'))
                        .append("/<jarName>\")");
            }
        }
        return sb.toString();
    }

    private String resolveDriver(String jdbcUrl) {
        for (Map.Entry<String, String> entry : DRIVER_MAP.entrySet()) {
            if (jdbcUrl.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 根据已加载的驱动类名，推断并注册其支持的 JDBC URL 前缀。
     * 覆盖主流数据库；若驱动类不在内置映射表中，调用方需在 query 时传 driverClass 参数。
     */
    private void inferAndRegisterUrlPrefix(String driverClassName) {
        if (driverClassName == null) return;

        if (driverClassName.equals("com.mysql.cj.jdbc.Driver")
                || driverClassName.equals("com.mysql.jdbc.Driver")) {
            synchronized (DRIVER_MAP) { DRIVER_MAP.put("jdbc:mysql:", driverClassName); }
        } else if (driverClassName.equals("org.mariadb.jdbc.Driver")) {
            synchronized (DRIVER_MAP) { DRIVER_MAP.put("jdbc:mariadb:", driverClassName); }
        } else if (driverClassName.equals("org.postgresql.Driver")) {
            synchronized (DRIVER_MAP) { DRIVER_MAP.put("jdbc:postgresql:", driverClassName); }
        } else if (driverClassName.equals("com.microsoft.sqlserver.jdbc.SQLServerDriver")) {
            synchronized (DRIVER_MAP) { DRIVER_MAP.put("jdbc:sqlserver:", driverClassName); }
        } else if (driverClassName.equals("net.sourceforge.jtds.jdbc.Driver")) {
            synchronized (DRIVER_MAP) { DRIVER_MAP.put("jdbc:jtds:sqlserver:", driverClassName); }
        } else if (driverClassName.equals("org.h2.Driver")) {
            synchronized (DRIVER_MAP) { DRIVER_MAP.put("jdbc:h2:", driverClassName); }
        } else if (driverClassName.equals("org.sqlite.JDBC")) {
            synchronized (DRIVER_MAP) { DRIVER_MAP.put("jdbc:sqlite:", driverClassName); }
        } else if (driverClassName.equals("oracle.jdbc.OracleDriver")
                || driverClassName.equals("oracle.jdbc.driver.OracleDriver")) {
            synchronized (DRIVER_MAP) { DRIVER_MAP.put("jdbc:oracle:", driverClassName); }
        } else if (driverClassName.equals("com.ibm.db2.jcc.DB2Driver")) {
            synchronized (DRIVER_MAP) { DRIVER_MAP.put("jdbc:db2:", driverClassName); }
        } else if (driverClassName.equals("com.dameng.DmDriver")
                || driverClassName.equals("dm.jdbc.driver.DmDriver")) {
            synchronized (DRIVER_MAP) { DRIVER_MAP.put("jdbc:dm:", driverClassName); }
        } else if (driverClassName.equals("com.kingbase8.Driver")) {
            synchronized (DRIVER_MAP) { DRIVER_MAP.put("jdbc:kingbase8:", driverClassName); }
        } else if (driverClassName.startsWith("com.microsoft.sqlserver")) {
            // 兼容旧版 SQL Server 驱动
            synchronized (DRIVER_MAP) { DRIVER_MAP.put("jdbc:sqlserver:", driverClassName); }
        }
    }

    private String formatResultSet(ResultSet rs, int maxRows) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();

        String[] colNames = new String[colCount];
        int[] colWidths = new int[colCount];
        for (int i = 0; i < colCount; i++) {
            colNames[i] = meta.getColumnLabel(i + 1);
            colWidths[i] = Math.max(colNames[i].length(), 8);
        }

        List<String[]> rows = new ArrayList<>();
        int rowCount = 0;
        while (rs.next() && rowCount < maxRows) {
            String[] row = new String[colCount];
            for (int i = 0; i < colCount; i++) {
                String val = rs.getString(i + 1);
                row[i] = val != null ? val : "NULL";
                colWidths[i] = Math.max(colWidths[i], Math.min(row[i].length(), 40));
            }
            rows.add(row);
            rowCount++;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(formatRow(colNames, colWidths)).append("\n");
        sb.append(formatSeparator(colWidths)).append("\n");
        for (String[] row : rows) {
            sb.append(formatRow(row, colWidths)).append("\n");
        }
        sb.append("\n共 ").append(rowCount).append(" 行");
        if (rowCount >= maxRows) {
            sb.append("（已达到上限 ").append(maxRows).append("，可能还有更多数据）");
        }
        return sb.toString();
    }

    private String formatRow(String[] values, int[] widths) {
        StringBuilder sb = new StringBuilder("|");
        for (int i = 0; i < values.length; i++) {
            String v = values[i].length() > widths[i]
                    ? values[i].substring(0, widths[i] - 3) + "..."
                    : values[i];
            sb.append(" ").append(rpad(v, widths[i])).append(" |");
        }
        return sb.toString();
    }

    private String formatSeparator(int[] widths) {
        StringBuilder sb = new StringBuilder("|");
        for (int w : widths) {
            sb.append(" ").append(String.join("", Collections.nCopies(w, "-"))).append(" |");
        }
        return sb.toString();
    }

    private String rpad(String s, int n) {
        if (s == null) s = "";
        return s.length() >= n ? s : s + String.join("", Collections.nCopies(n - s.length(), " "));
    }

    /** 包装 Driver 避免重复注册。 */
    private static class DriverWrapper implements Driver {
        private final Driver delegate;
        DriverWrapper(Driver delegate) { this.delegate = delegate; }
        @Override public Connection connect(String url, Properties info) throws SQLException { return delegate.connect(url, info); }
        @Override public boolean acceptsURL(String url) throws SQLException { return delegate.acceptsURL(url); }
        @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException { return delegate.getPropertyInfo(url, info); }
        @Override public int getMajorVersion() { return delegate.getMajorVersion(); }
        @Override public int getMinorVersion() { return delegate.getMinorVersion(); }
        @Override public boolean jdbcCompliant() { return delegate.jdbcCompliant(); }
        @Override public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException { return delegate.getParentLogger(); }
    }
}