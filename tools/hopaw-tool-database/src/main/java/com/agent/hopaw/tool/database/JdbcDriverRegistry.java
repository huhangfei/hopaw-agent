package com.agent.hopaw.tool.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC 驱动注册表：<b>插件级共享资源</b>。
 *
 * <p>本类是「插件即工具工厂」范式的示范：插件在 {@code asyncInit()} 里构建本实例，
 * 再注入给 {@link DatabaseQueryTool} 与 {@link DatabaseSchemaTool} 两个工具集共用；
 * 插件卸载时 {@code destroy()} 调用 {@link #close()} 统一释放驱动 ClassLoader。
 * 插件级配置变更时调用 {@link #reload(String, boolean)} 就地重建，两个工具集持有的是同一实例，无需感知。</p>
 *
 * <p>驱动 JAR 通过独立 {@link URLClassLoader} 加载并注册到 {@link DriverManager}，
 * 因此不依赖应用 ClassLoader，也不需要在插件 JAR 里内置任何 JDBC 驱动。</p>
 */
public class JdbcDriverRegistry {

    private static final Logger log = LoggerFactory.getLogger(JdbcDriverRegistry.class);

    /** 默认驱动目录名（相对项目根目录） */
    public static final String DEFAULT_DRIVER_DIR = "jdbc-drivers";

    /** JDBC URL 前缀 → 驱动类名；驱动加载后由 ServiceLoader 扫描写入 */
    private final Map<String, String> driverMap = new LinkedHashMap<>();

    /** 已加载的驱动 JAR 路径 → 其 ClassLoader（持有引用防止 GC 回收） */
    private final Map<String, URLClassLoader> loadedDrivers = new ConcurrentHashMap<>();

    /** 当前驱动目录（绝对路径） */
    private volatile Path driverDir;

    public JdbcDriverRegistry(String driverDirPath) {
        this.driverDir = resolveDriverDir(driverDirPath);
    }

    /**
     * 重载：释放已加载的驱动 ClassLoader，切换到新的驱动目录，并按需自动加载目录内全部 JAR。
     *
     * <p>就地变更，不换实例——插件级配置变更时两个工具集持有的引用自动生效。</p>
     *
     * @param driverDirPath 新的驱动目录（可为空，回退默认目录名）
     * @param autoLoad      是否自动加载目录下的全部 JAR
     * @return 自动加载成功的驱动 JAR 数量
     */
    public synchronized int reload(String driverDirPath, boolean autoLoad) {
        close();
        this.driverDir = resolveDriverDir(driverDirPath);
        if (!autoLoad) {
            log.info("JDBC driver auto-load disabled, driver dir={}", driverDir);
            return 0;
        }
        return autoLoadAll();
    }

    /**
     * 扫描当前驱动目录并加载全部 JAR。
     *
     * @return 成功加载的 JAR 数量
     */
    public int autoLoadAll() {
        File dir = driverDir.toFile();
        if (!dir.exists() || !dir.isDirectory()) {
            log.info("JDBC driver directory not found: {}", driverDir);
            return 0;
        }
        File[] jars = dir.listFiles((f) -> f.isFile() && f.getName().toLowerCase().endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            log.info("No JDBC driver JARs found in {}", driverDir);
            return 0;
        }
        Arrays.sort(jars, Comparator.comparing(File::getName));
        log.info("Auto-loading {} JDBC driver JAR(s) from {}", jars.length, driverDir);
        int ok = 0;
        for (File jar : jars) {
            try {
                loadFromJar(jar.getAbsolutePath());
                ok++;
            } catch (Exception e) {
                log.warn("Failed to auto-load driver {}: {}", jar.getName(), e.getMessage());
            }
        }
        return ok;
    }

    /**
     * 从本地 JAR 文件加载 JDBC 驱动并注册到 DriverManager。
     *
     * @return 面向用户/LLM 的可读结果文本
     */
    public String loadFromJar(String jarPath) {
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
            loadedDrivers.put(jarPath, loader);

            // 通过 ServiceLoader 触发 java.sql.Driver 自动注册
            int count = 0;
            StringBuilder drivers = new StringBuilder();
            java.util.ServiceLoader<Driver> sl = java.util.ServiceLoader.load(Driver.class, loader);
            for (Driver d : sl) {
                try {
                    DriverManager.registerDriver(new DriverWrapper(d));
                    if (drivers.length() > 0) {
                        drivers.append(", ");
                    }
                    drivers.append(d.getClass().getName());
                    count++;
                    inferAndRegisterUrlPrefix(d.getClass().getName());
                } catch (Exception ignored) {
                    // 可能已注册，忽略
                }
            }

            if (count == 0) {
                return "警告：未能从 JAR 中自动发现 JDBC Driver。\n"
                        + "已加载 JAR 到 classpath，请确认该 JAR 包含 META-INF/services/java.sql.Driver 文件。";
            }
            // 加载成功后复制到驱动目录，便于下次启动自动加载
            String copyMsg = copyJarToDriverDir(jar);
            return "成功加载 " + count + " 个 JDBC 驱动：" + drivers
                    + (copyMsg.isEmpty() ? "" : "\n" + copyMsg);
        } catch (Exception e) {
            log.error("加载驱动 JAR 失败 path={}", jarPath, e);
            return "加载失败：" + e.getMessage();
        }
    }

    /**
     * 按 JDBC URL 前缀解析驱动类名。
     *
     * @return 驱动类名；无法识别时返回 {@code null}
     */
    public String resolveDriverClass(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : driverMap.entrySet()) {
            if (jdbcUrl.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 建立连接。不调用 {@code Class.forName}——驱动已通过 ServiceLoader 加载并注册到 DriverManager，
     * 而 {@code Class.forName} 走应用 ClassLoader 反而看不到 URLClassLoader 中的驱动类。
     */
    public Connection open(String jdbcUrl, String username, String password) throws SQLException {
        if (resolveDriverClass(jdbcUrl) == null) {
            throw new SQLException(unsupportedUrlMessage(jdbcUrl));
        }
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    /**
     * 拼接「URL 前缀不支持」的引导信息：列出已支持前缀 + 扫描驱动目录。
     */
    public String unsupportedUrlMessage(String jdbcUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("无法识别 JDBC URL 的数据库类型：").append(jdbcUrl).append("\n");
        sb.append("当前已注册的 URL 前缀：").append(driverMap.isEmpty() ? "(无)" : driverMap.keySet()).append("\n");
        sb.append("请先调用 loadJdbcDriverFromJar 加载对应数据库的驱动 JAR。");

        File dir = driverDir.toFile();
        if (dir.exists() && dir.isDirectory()) {
            File[] jars = dir.listFiles((f) -> f.isFile() && f.getName().toLowerCase().endsWith(".jar"));
            if (jars != null && jars.length > 0) {
                Arrays.sort(jars, Comparator.comparing(File::getName));
                sb.append("\n\n提示：").append(driverDir).append(" 目录下的 JAR：\n");
                for (File jar : jars) {
                    sb.append("  - ").append(jar.getName()).append("\n");
                }
                sb.append("如以上 JAR 包含您需要的驱动，请调用：\n");
                sb.append("  loadJdbcDriverFromJar(\"")
                        .append(jars[0].getParentFile().getAbsolutePath().replace('\\', '/'))
                        .append("/<jarName>\")");
            }
        }
        return sb.toString();
    }

    /**
     * 生成「驱动目录 + 已加载 JAR + 已注册 URL 前缀 + DriverManager 现状」的可读报告。
     */
    public String describe() {
        StringBuilder sb = new StringBuilder();

        sb.append("=== 驱动文件目录 ===\n");
        sb.append("  目录：").append(driverDir).append("\n");
        File dir = driverDir.toFile();
        if (dir.exists() && dir.isDirectory()) {
            File[] jars = dir.listFiles((f) -> f.isFile() && f.getName().toLowerCase().endsWith(".jar"));
            if (jars == null || jars.length == 0) {
                sb.append("  状态：目录存在，但无 .jar 文件\n");
            } else {
                sb.append("  状态：找到 ").append(jars.length).append(" 个驱动 JAR\n");
                Arrays.sort(jars, Comparator.comparing(File::getName));
                for (File jar : jars) {
                    boolean loaded = loadedDrivers.values().stream().anyMatch(loader -> {
                        URL[] urls = loader.getURLs();
                        if (urls.length == 0) {
                            return false;
                        }
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
            sb.append("  提示：请在项目根目录下创建 ").append(DEFAULT_DRIVER_DIR)
                    .append("/ 文件夹，将驱动 JAR 放入其中\n");
        }

        sb.append("\n=== 已加载的 JDBC 驱动 JAR ===\n");
        if (loadedDrivers.isEmpty()) {
            sb.append("  (无)\n");
        } else {
            int idx = 1;
            for (Map.Entry<String, URLClassLoader> e : loadedDrivers.entrySet()) {
                URL[] urls = e.getValue().getURLs();
                String jarName = urls.length > 0 ? new File(urls[0].getFile()).getName() : "(unknown)";
                sb.append("  ").append(idx++).append(". ").append(jarName).append("\n")
                        .append("     路径: ").append(e.getKey()).append("\n");
            }
        }

        sb.append("\n=== 已注册的 URL 前缀 → 驱动类 ===\n");
        if (driverMap.isEmpty()) {
            sb.append("  (无)\n");
        } else {
            List<String> prefixes = new ArrayList<>(driverMap.keySet());
            Collections.sort(prefixes);
            for (String prefix : prefixes) {
                sb.append("  ").append(String.format("%-22s", prefix))
                        .append(" → ").append(driverMap.get(prefix)).append("\n");
            }
        }

        sb.append("\n=== DriverManager 中已注册的驱动 ===\n");
        Enumeration<Driver> registered = DriverManager.getDrivers();
        int count = 0;
        while (registered.hasMoreElements()) {
            sb.append("  ").append(registered.nextElement().getClass().getName()).append("\n");
            count++;
        }
        sb.append(count == 0 ? "  (无)\n" : "共 " + count + " 个驱动\n");

        return sb.toString();
    }

    /**
     * 释放全部驱动 ClassLoader（插件卸载时由插件调用）。
     */
    public synchronized void close() {
        for (Map.Entry<String, URLClassLoader> e : loadedDrivers.entrySet()) {
            try {
                e.getValue().close();
            } catch (Exception ex) {
                log.warn("Failed to close driver classloader: {}", e.getKey(), ex);
            }
        }
        loadedDrivers.clear();
        driverMap.clear();
    }

    private static Path resolveDriverDir(String driverDirPath) {
        String path = (driverDirPath == null || driverDirPath.isBlank()) ? DEFAULT_DRIVER_DIR : driverDirPath.trim();
        File dir = new File(path);
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir"), path);
        }
        return dir.toPath().toAbsolutePath().normalize();
    }

    /**
     * 将外部驱动 JAR 复制到驱动目录，以便下次启动自动加载。
     * 已在目录中（或目标已存在且大小一致）则跳过。
     *
     * @return 复制结果信息；无需复制时返回空字符串
     */
    private String copyJarToDriverDir(File sourceJar) {
        File dir = driverDir.toFile();
        File destJar = new File(dir, sourceJar.getName());
        try {
            if (sourceJar.getCanonicalPath().equals(destJar.getCanonicalPath())) {
                return "";
            }
        } catch (Exception ignored) {
            // 忽略：无法规范化时继续走复制流程
        }
        if (!dir.exists() && !dir.mkdirs()) {
            return "无法创建驱动目录: " + driverDir;
        }
        if (destJar.exists() && destJar.length() == sourceJar.length()) {
            return "驱动已存在于默认目录，跳过复制";
        }
        try (InputStream in = new FileInputStream(sourceJar);
             OutputStream out = new FileOutputStream(destJar)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            log.info("已复制驱动 JAR 到驱动目录: {}", destJar.getAbsolutePath());
            return "驱动已复制到默认目录: " + destJar.getAbsolutePath();
        } catch (Exception e) {
            return "复制驱动失败: " + e.getMessage();
        }
    }

    /**
     * 根据已加载的驱动类名推断并注册其支持的 JDBC URL 前缀。
     */
    private void inferAndRegisterUrlPrefix(String driverClassName) {
        if (driverClassName == null) {
            return;
        }
        String prefix = null;
        switch (driverClassName) {
            case "com.mysql.cj.jdbc.Driver":
            case "com.mysql.jdbc.Driver":
                prefix = "jdbc:mysql:";
                break;
            case "org.mariadb.jdbc.Driver":
                prefix = "jdbc:mariadb:";
                break;
            case "org.postgresql.Driver":
                prefix = "jdbc:postgresql:";
                break;
            case "com.microsoft.sqlserver.jdbc.SQLServerDriver":
                prefix = "jdbc:sqlserver:";
                break;
            case "net.sourceforge.jtds.jdbc.Driver":
                prefix = "jdbc:jtds:sqlserver:";
                break;
            case "org.h2.Driver":
                prefix = "jdbc:h2:";
                break;
            case "org.sqlite.JDBC":
                prefix = "jdbc:sqlite:";
                break;
            case "oracle.jdbc.OracleDriver":
            case "oracle.jdbc.driver.OracleDriver":
                prefix = "jdbc:oracle:";
                break;
            case "com.ibm.db2.jcc.DB2Driver":
                prefix = "jdbc:db2:";
                break;
            case "com.dameng.DmDriver":
            case "dm.jdbc.driver.DmDriver":
                prefix = "jdbc:dm:";
                break;
            case "com.kingbase8.Driver":
                prefix = "jdbc:kingbase8:";
                break;
            default:
                if (driverClassName.startsWith("com.microsoft.sqlserver")) {
                    prefix = "jdbc:sqlserver:";
                }
        }
        if (prefix != null) {
            synchronized (driverMap) {
                driverMap.put(prefix, driverClassName);
            }
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }

    /** 包装 Driver，避免同一驱动被重复注册时抛异常。 */
    private static class DriverWrapper implements Driver {
        private final Driver delegate;

        DriverWrapper(Driver delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            return delegate.connect(url, info);
        }

        @Override
        public boolean acceptsURL(String url) throws SQLException {
            return delegate.acceptsURL(url);
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
            return delegate.getPropertyInfo(url, info);
        }

        @Override
        public int getMajorVersion() {
            return delegate.getMajorVersion();
        }

        @Override
        public int getMinorVersion() {
            return delegate.getMinorVersion();
        }

        @Override
        public boolean jdbcCompliant() {
            return delegate.jdbcCompliant();
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }
    }
}
