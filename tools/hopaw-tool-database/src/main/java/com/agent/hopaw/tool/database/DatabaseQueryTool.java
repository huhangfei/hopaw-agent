package com.agent.hopaw.tool.database;

import com.agent.hopaw.infra.service.IPluginConfigService;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;

/**
 * 工具集「数据查询与执行」：连接测试、SELECT 查询、写操作 SQL、查询结果导出 CSV。
 *
 * <p>属于 database 插件（{@link DatabasePlugin}）的第一个工具集，与
 * {@link DatabaseSchemaTool} 共用插件级共享资源 {@link JdbcDriverRegistry}
 * 与插件级配置（默认连接信息）。</p>
 */
public class DatabaseQueryTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(DatabaseQueryTool.class);

    private static final String TOOL_SET_NAME = "databaseQuery";

    /** 插件级共享资源：JDBC 驱动注册表（由 DatabasePlugin 构建并注入） */
    private final JdbcDriverRegistry driverRegistry;

    /** 插件级配置读取（默认连接信息） */
    @Autowired
    private IPluginConfigService pluginConfigService;

    public DatabaseQueryTool(JdbcDriverRegistry driverRegistry) {
        this.driverRegistry = driverRegistry;
    }

    @Override
    public String getName() {
        return TOOL_SET_NAME;
    }

    @Override
    public String getDescription() {
        return "数据库数据操作工具集：连接测试、SELECT 查询、INSERT/UPDATE/DELETE/DDL 执行、查询结果导出 CSV";
    }

    @Override
    public String getKeyword() {
        return "数据库,查询,SQL,DML";
    }

    @Override
    public String getIcon() {
        return "database-query-tool.svg";
    }

    // ========== @Tool 方法 ==========

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(name = "database_testConnection", value = {
            "测试数据库连接",
            "使用给定的 JDBC URL、用户名和密码测试数据库连通性；参数留空时使用插件配置的默认连接信息。"
    })
    public String testDatabaseConnection(
            @P(description = "JDBC 连接地址，例如 jdbc:mysql://localhost:3306/mydb；留空则用插件配置的默认地址", required = false) String jdbcUrl,
            @P(description = "数据库用户名；留空则用插件配置的默认用户名", required = false) String username,
            @P(description = "数据库密码；留空则用插件配置的默认密码", required = false) String password) {
        String[] err = new String[1];
        JdbcSupport.ConnParams params = JdbcSupport.resolveParams(pluginConfigService, jdbcUrl, username, password, err);
        if (params == null) {
            return err[0];
        }
        try (Connection conn = JdbcSupport.open(driverRegistry, params)) {
            java.sql.DatabaseMetaData meta = conn.getMetaData();
            return String.format("连接成功！\n"
                            + "数据库产品：%s %s\n"
                            + "JDBC 驱动：%s %s\n"
                            + "连接 URL：%s",
                    meta.getDatabaseProductName(), meta.getDatabaseProductVersion(),
                    meta.getDriverName(), meta.getDriverVersion(),
                    params.jdbcUrl);
        } catch (Exception e) {
            log.error("数据库连接测试失败", e);
            return "连接失败：" + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(name = "database_executeSelectQuery", value = {
            "查询数据库",
            "执行 SELECT 查询并返回结果集。结果以表格形式展示，最多返回指定行数。"
    })
    public String executeSelectQuery(
            @P(description = "JDBC 连接地址；留空则用插件配置的默认地址", required = false) String jdbcUrl,
            @P(description = "数据库用户名；留空则用插件配置的默认用户名", required = false) String username,
            @P(description = "数据库密码；留空则用插件配置的默认密码", required = false) String password,
            @P(description = "SELECT 查询语句") String sql,
            @P(description = "最大返回行数，默认100，最大1000", required = false) Integer maxRows) {
        if (sql == null || sql.isBlank()) {
            return "错误：SQL 语句不能为空";
        }
        if (maxRows == null || maxRows <= 0) {
            maxRows = 100;
        }
        if (maxRows > 1000) {
            maxRows = 1000;
        }
        String[] err = new String[1];
        JdbcSupport.ConnParams params = JdbcSupport.resolveParams(pluginConfigService, jdbcUrl, username, password, err);
        if (params == null) {
            return err[0];
        }

        try (Connection conn = JdbcSupport.open(driverRegistry, params);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setMaxRows(maxRows);
            try (ResultSet rs = stmt.executeQuery()) {
                return JdbcSupport.formatResultSet(rs, maxRows);
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
    @Tool(name = "database_executeUpdateSql", value = {
            "执行SQL",
            "执行 INSERT / UPDATE / DELETE 或 DDL 语句。返回影响行数或执行结果。"
    })
    public String executeUpdateSql(
            @P(description = "JDBC 连接地址；留空则用插件配置的默认地址", required = false) String jdbcUrl,
            @P(description = "数据库用户名；留空则用插件配置的默认用户名", required = false) String username,
            @P(description = "数据库密码；留空则用插件配置的默认密码", required = false) String password,
            @P(description = "要执行的 SQL 语句（INSERT / UPDATE / DELETE / DDL）") String sql) {
        if (sql == null || sql.isBlank()) {
            return "错误：SQL 语句不能为空";
        }
        String[] err = new String[1];
        JdbcSupport.ConnParams params = JdbcSupport.resolveParams(pluginConfigService, jdbcUrl, username, password, err);
        if (params == null) {
            return err[0];
        }

        try (Connection conn = JdbcSupport.open(driverRegistry, params);
             Statement stmt = conn.createStatement()) {
            boolean isResultSet = stmt.execute(sql);
            if (isResultSet) {
                try (ResultSet rs = stmt.getResultSet()) {
                    return "执行成功，返回结果集：\n" + JdbcSupport.formatResultSet(rs, 100);
                }
            }
            int updateCount = stmt.getUpdateCount();
            return updateCount >= 0 ? "执行成功，影响行数：" + updateCount : "执行成功";
        } catch (SQLException e) {
            log.error("执行失败 sql={}", sql, e);
            return "执行失败：" + e.getMessage();
        } catch (Exception e) {
            log.error("执行失败 sql={}", sql, e);
            return "执行失败：" + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(name = "database_exportQueryToCsv", value = {
            "导出查询结果到CSV",
            "将大查询结果导出为 CSV 文件，保存到项目 exports/ 目录下。返回文件路径及下载链接，适合数据量较大的场景。"
    })
    public String exportQueryToCsv(
            @P(description = "JDBC 连接地址；留空则用插件配置的默认地址", required = false) String jdbcUrl,
            @P(description = "数据库用户名；留空则用插件配置的默认用户名", required = false) String username,
            @P(description = "数据库密码；留空则用插件配置的默认密码", required = false) String password,
            @P(description = "SELECT 查询语句") String sql,
            @P(description = "最大导出行数，默认100000，最大1000000", required = false) Integer maxRows) {
        if (sql == null || sql.isBlank()) {
            return "错误：SQL 语句不能为空";
        }
        if (maxRows == null || maxRows <= 0) {
            maxRows = 100_000;
        }
        if (maxRows > 1_000_000) {
            maxRows = 1_000_000;
        }
        String[] err = new String[1];
        JdbcSupport.ConnParams params = JdbcSupport.resolveParams(pluginConfigService, jdbcUrl, username, password, err);
        if (params == null) {
            return err[0];
        }

        long startTime = System.currentTimeMillis();

        File exportDir = new File(System.getProperty("user.dir"), "exports");
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            return "导出失败：无法创建导出目录 " + exportDir.getAbsolutePath();
        }

        String fileName = "export_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv";
        File csvFile = new File(exportDir, fileName);

        try (Connection conn = JdbcSupport.open(driverRegistry, params);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // 尝试设置游标读取，避免一次性加载全部数据到内存
            try {
                stmt.setFetchSize(1000);
            } catch (SQLException ignored) {
                // 部分驱动不支持 fetchSize，忽略
            }
            stmt.setMaxRows(maxRows);

            try (ResultSet rs = stmt.executeQuery();
                 java.io.BufferedWriter writer = new java.io.BufferedWriter(
                         new java.io.OutputStreamWriter(new java.io.FileOutputStream(csvFile), "UTF-8"))) {

                // UTF-8 BOM，保证 Excel 正确识别中文
                writer.write('\uFEFF');

                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();

                for (int i = 1; i <= colCount; i++) {
                    if (i > 1) {
                        writer.write(',');
                    }
                    writer.write(JdbcSupport.csvEscape(meta.getColumnLabel(i)));
                }
                writer.newLine();

                int rowCount = 0;
                while (rs.next() && rowCount < maxRows) {
                    for (int i = 1; i <= colCount; i++) {
                        if (i > 1) {
                            writer.write(',');
                        }
                        String val = rs.getString(i);
                        writer.write(val != null ? JdbcSupport.csvEscape(val) : "");
                    }
                    writer.newLine();
                    rowCount++;
                }
                writer.flush();

                long elapsed = System.currentTimeMillis() - startTime;
                return String.format(
                        "导出成功！\n文件路径：%s\n下载链接：%s\n文件大小：%s\n导出行数：%,d\n列数：%d\n耗时：%,d ms",
                        csvFile.getAbsolutePath(),
                        "/exports/" + fileName,
                        JdbcSupport.formatSize(csvFile.length()),
                        rowCount,
                        colCount,
                        elapsed);
            }
        } catch (SQLException e) {
            log.error("导出CSV失败 sql={}", sql, e);
            if (csvFile.exists()) {
                csvFile.delete();
            }
            return "导出失败：" + e.getMessage();
        } catch (java.io.IOException e) {
            log.error("导出CSV写入失败", e);
            if (csvFile.exists()) {
                csvFile.delete();
            }
            return "导出失败（文件写入错误）：" + e.getMessage();
        } catch (Exception e) {
            log.error("导出CSV失败 sql={}", sql, e);
            if (csvFile.exists()) {
                csvFile.delete();
            }
            return "导出失败：" + e.getMessage();
        }
    }
}
