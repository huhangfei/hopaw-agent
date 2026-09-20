package com.agent.hopaw.tool.database;

import com.agent.hopaw.infra.service.IPluginConfigService;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 两个工具集（{@link DatabaseQueryTool} / {@link DatabaseSchemaTool}）共用的 SQL 辅助方法。
 *
 * <p>插件级配置项 key 在此集中定义，避免两个工具集各写一份字符串常量。</p>
 */
final class JdbcSupport {

    /** 插件标识（配置前缀 plugin.database.） */
    static final String PLUGIN_ID = "database";

    /** 插件级配置项 key：默认 JDBC 地址 */
    static final String CFG_JDBC_URL = "defaultJdbcUrl";
    /** 插件级配置项 key：默认用户名 */
    static final String CFG_USERNAME = "defaultUsername";
    /** 插件级配置项 key：默认密码 */
    static final String CFG_PASSWORD = "defaultPassword";
    /** 插件级配置项 key：驱动目录 */
    static final String CFG_DRIVER_DIR = "driverDir";
    /** 插件级配置项 key：是否启动时自动加载驱动 */
    static final String CFG_AUTO_LOAD = "autoLoadDrivers";

    private JdbcSupport() {
    }

    /** 连接参数：JDBC 地址/用户名/密码（已套用插件级默认值）。 */
    static final class ConnParams {
        final String jdbcUrl;
        final String username;
        final String password;

        ConnParams(String jdbcUrl, String username, String password) {
            this.jdbcUrl = jdbcUrl;
            this.username = username;
            this.password = password;
        }
    }

    /**
     * 把工具参数与插件级默认配置合并：参数优先，缺失时回退 plugin.database.*，并给出可读的缺失提示。
     *
     * @return 合并后的连接参数；缺少必要项时返回 {@code null}，错误原因写入 {@code errorHolder[0]}
     */
    static ConnParams resolveParams(IPluginConfigService config,
                                    String jdbcUrl, String username, String password,
                                    String[] errorHolder) {
        String url = blankToNull(jdbcUrl);
        if (url == null && config != null) {
            url = blankToNull(config.get(PLUGIN_ID, CFG_JDBC_URL, null));
        }
        if (url == null) {
            errorHolder[0] = "错误：未提供 JDBC 连接地址，且插件未配置默认地址。\n"
                    + "请传入 jdbcUrl 参数，或到「插件管理 → 数据库插件 → 插件配置」中设置默认连接信息。";
            return null;
        }
        String user = blankToNull(username);
        if (user == null && config != null) {
            user = blankToNull(config.get(PLUGIN_ID, CFG_USERNAME, null));
        }
        String pwd = blankToNull(password);
        if (pwd == null && config != null) {
            pwd = config.get(PLUGIN_ID, CFG_PASSWORD, null);
        }
        return new ConnParams(url, user, pwd);
    }

    static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    /**
     * 把结果集格式化为等宽文本表格，最多 {@code maxRows} 行。
     */
    static String formatResultSet(ResultSet rs, int maxRows) throws SQLException {
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

    /**
     * CSV 字段转义：含逗号、双引号或换行符时用双引号包裹并转义内部双引号。
     */
    static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        return needsQuote ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }

    static Connection open(JdbcDriverRegistry registry, ConnParams params) throws SQLException {
        return registry.open(params.jdbcUrl, params.username, params.password);
    }

    private static String formatRow(String[] values, int[] widths) {
        StringBuilder sb = new StringBuilder("|");
        for (int i = 0; i < values.length; i++) {
            String v = values[i].length() > widths[i]
                    ? values[i].substring(0, widths[i] - 3) + "..."
                    : values[i];
            sb.append(" ").append(rpad(v, widths[i])).append(" |");
        }
        return sb.toString();
    }

    private static String formatSeparator(int[] widths) {
        StringBuilder sb = new StringBuilder("|");
        for (int w : widths) {
            sb.append(" ").append(String.join("", Collections.nCopies(w, "-"))).append(" |");
        }
        return sb.toString();
    }

    private static String rpad(String s, int n) {
        if (s == null) {
            s = "";
        }
        return s.length() >= n ? s : s + String.join("", Collections.nCopies(n - s.length(), " "));
    }
}
