package com.agent.hopaw.tool.database;

import com.agent.hopaw.infra.service.IPluginConfigService;
import com.agent.hopaw.infra.tool.AbstractAgentTool;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具集「驱动与结构管理」：JDBC 驱动加载/查看、表清单、表结构。
 *
 * <p>属于 database 插件（{@link DatabasePlugin}）的第二个工具集。驱动状态由插件级共享资源
 * {@link JdbcDriverRegistry} 持有，因此这里加载的驱动对 {@link DatabaseQueryTool} 立即可见。</p>
 */
public class DatabaseSchemaTool extends AbstractAgentTool {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaTool.class);

    private static final String TOOL_SET_NAME = "databaseSchema";

    /** 插件级共享资源：JDBC 驱动注册表（由 DatabasePlugin 构建并注入） */
    private final JdbcDriverRegistry driverRegistry;

    /** 插件级配置读取（默认连接信息） */
    @Autowired
    private IPluginConfigService pluginConfigService;

    public DatabaseSchemaTool(JdbcDriverRegistry driverRegistry) {
        this.driverRegistry = driverRegistry;
    }

    @Override
    public String getName() {
        return TOOL_SET_NAME;
    }

    @Override
    public String getDescription() {
        return "数据库结构与驱动管理工具集：加载 JDBC 驱动、查看已加载驱动、列出表、查看表结构";
    }

    @Override
    public String getKeyword() {
        return "数据库,表结构,元数据,JDBC驱动";
    }

    @Override
    public String getIcon() {
        return "database-tool.svg";
    }

    // ========== @Tool 方法 ==========

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(name = "database_loadJdbcDriverFromJar", value = {
            "加载JDBC驱动JAR",
            "从本地 .jar 文件路径动态加载 JDBC 驱动。调用 query / execute 前若驱动未加载可先调用本方法。"
    })
    public String loadJdbcDriverFromJar(
            @P(description = "JDBC 驱动 JAR 文件的绝对路径，例如 D:/drivers/mysql-connector-j-8.0.33.jar") String jarPath) {
        return driverRegistry.loadFromJar(jarPath);
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(name = "database_listLoadedJdbcDrivers", value = {
            "列出已加载的JDBC驱动",
            "查看当前进程中已加载的 JDBC 驱动 JAR、对应驱动类与支持的 URL 前缀。"
    })
    public String listLoadedJdbcDrivers() {
        return driverRegistry.describe();
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(name = "database_listTables", value = {
            "列出数据库表",
            "列出当前数据库中的所有表。"
    })
    public String listDatabaseTables(
            @P(description = "JDBC 连接地址；留空则用插件配置的默认地址", required = false) String jdbcUrl,
            @P(description = "数据库用户名；留空则用插件配置的默认用户名", required = false) String username,
            @P(description = "数据库密码；留空则用插件配置的默认密码", required = false) String password,
            @P(description = "按表名过滤，支持 % 通配符，不传则列出全部", required = false) String pattern) {
        if (pattern == null || pattern.isBlank()) {
            pattern = "%";
        }
        String[] err = new String[1];
        JdbcSupport.ConnParams params = JdbcSupport.resolveParams(pluginConfigService, jdbcUrl, username, password, err);
        if (params == null) {
            return err[0];
        }

        try (Connection conn = JdbcSupport.open(driverRegistry, params)) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();

            List<String> tables = new ArrayList<>();
            try (ResultSet rs = meta.getTables(catalog, schema, pattern, new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    tables.add(String.format("%-40s %-10s %s",
                            rs.getString("TABLE_NAME"),
                            rs.getString("TABLE_TYPE"),
                            rs.getString("REMARKS") != null ? rs.getString("REMARKS") : ""));
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
    @Tool(name = "database_describeTable", value = {
            "查看表结构",
            "查看指定表的列定义、主键、索引信息。"
    })
    public String describeDatabaseTable(
            @P(description = "JDBC 连接地址；留空则用插件配置的默认地址", required = false) String jdbcUrl,
            @P(description = "数据库用户名；留空则用插件配置的默认用户名", required = false) String username,
            @P(description = "数据库密码；留空则用插件配置的默认密码", required = false) String password,
            @P(description = "表名") String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return "错误：表名不能为空";
        }
        String[] err = new String[1];
        JdbcSupport.ConnParams params = JdbcSupport.resolveParams(pluginConfigService, jdbcUrl, username, password, err);
        if (params == null) {
            return err[0];
        }

        try (Connection conn = JdbcSupport.open(driverRegistry, params)) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();

            StringBuilder sb = new StringBuilder();
            sb.append("表：").append(tableName).append("\n");

            List<String> columns = new ArrayList<>();
            Map<String, List<String>> primaryKeys = new LinkedHashMap<>();
            try (ResultSet rs = meta.getColumns(catalog, schema, tableName, "%")) {
                while (rs.next()) {
                    String autoIncrement = "YES".equals(rs.getString("IS_AUTOINCREMENT")) ? " AUTO_INCREMENT" : "";
                    String defaultValue = rs.getString("COLUMN_DEF");
                    String remarks = rs.getString("REMARKS");
                    int colSize = rs.getInt("COLUMN_SIZE");
                    columns.add(String.format("  %-30s %-20s %-8s %s%s%s",
                            rs.getString("COLUMN_NAME"),
                            rs.getString("TYPE_NAME") + (colSize > 0 ? "(" + colSize + ")" : ""),
                            "YES".equals(rs.getString("IS_NULLABLE")) ? "NULL" : "NOT NULL",
                            defaultValue != null ? "DEFAULT " + defaultValue + " " : "",
                            autoIncrement,
                            remarks != null && !remarks.isEmpty() ? " -- " + remarks : ""));
                }
            }

            try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, tableName)) {
                while (rs.next()) {
                    String pkName = rs.getString("PK_NAME");
                    primaryKeys.computeIfAbsent(pkName != null ? pkName : "PRIMARY", k -> new ArrayList<>())
                            .add(rs.getString("COLUMN_NAME"));
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
                    if (idxName != null && !primaryKeys.containsKey(idxName)) {
                        indexes.add(String.format("  %-30s %-20s %s",
                                idxName, rs.getString("COLUMN_NAME"), rs.getBoolean("NON_UNIQUE") ? "" : "UNIQUE"));
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
}
