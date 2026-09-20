package com.agent.hopaw.tool.database;

import com.agent.hopaw.infra.model.dto.OptionItem;
import com.agent.hopaw.infra.model.dto.ToolConfigItem;
import com.agent.hopaw.infra.plugin.AbstractAgentPlugin;
import com.agent.hopaw.infra.service.IPluginConfigService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * 数据库插件（<b>多工具集 + 插件级共享资源示范</b>）。
 *
 * <p>本插件示范三件事：</p>
 * <ol>
 *   <li><b>一个插件提供多个工具集</b>：{@link DatabaseQueryTool}（数据查询与执行）与
 *       {@link DatabaseSchemaTool}（驱动与结构管理）；</li>
 *   <li><b>共享资源上移插件级</b>：JDBC 驱动注册表 {@link JdbcDriverRegistry} 由插件在
 *       {@code asyncInit()} 中构建并注入给两个工具集，卸载时由插件 {@code destroy()} 统一释放
 *       （驱动 ClassLoader 不再随工具类的静态字段泄漏）；</li>
 *   <li><b>插件级配置</b>：默认连接信息与驱动目录声明在 {@code plugin.database.*} 段，
 *       两个工具集共享读取，工具参数留空时自动回退，避免每次调用都向 LLM 索要连接信息。</li>
 * </ol>
 */
public class DatabasePlugin extends AbstractAgentPlugin {

    /** 插件标识（同时是插件级配置前缀 plugin.database. 的中间段） */
    public static final String PLUGIN_ID = "database";

    /** 插件级共享资源：JDBC 驱动注册表（构造时先占位，asyncInit 按配置重载） */
    private final JdbcDriverRegistry driverRegistry = new JdbcDriverRegistry(null);

    /** 插件级配置读取 */
    @Autowired
    private IPluginConfigService pluginConfigService;

    @Override
    public String getId() {
        return PLUGIN_ID;
    }

    @Override
    public String getName() {
        return "数据库";
    }

    @Override
    public String getDescription() {
        return "提供数据库查询、SQL 执行、结果导出与结构管理能力；驱动从本地 JAR 动态加载，"
                + "支持 MySQL / PostgreSQL / MariaDB / SQL Server / Oracle / H2 / SQLite 等";
    }

    @Override
    public String getVersion() {
        return "2.0.0";
    }

    @Override
    public String getKeyword() {
        return "数据库,查询,SQL,JDBC,表结构";
    }

    @Override
    public String getIcon() {
        return "database.svg";
    }

    @Override
    public List<ToolConfigItem> getConfigItems() {
        ToolConfigItem driverDir = item(JdbcSupport.CFG_DRIVER_DIR, "驱动目录",
                "JDBC 驱动 JAR 的存放目录（相对项目根目录或绝对路径）；插件启动时自动加载其中全部 JAR",
                ToolConfigItem.ConfigType.TEXT_SINGLE);
        driverDir.setDefaultValue(JdbcDriverRegistry.DEFAULT_DRIVER_DIR);

        ToolConfigItem autoLoad = item(JdbcSupport.CFG_AUTO_LOAD, "启动自动加载驱动",
                "是否在插件初始化时自动扫描并加载驱动目录下的全部 JAR",
                ToolConfigItem.ConfigType.SELECT,
                new OptionItem("true", "开启"), new OptionItem("false", "关闭"));
        autoLoad.setDefaultValue("true");

        return List.of(
                driverDir,
                autoLoad,
                item(JdbcSupport.CFG_JDBC_URL, "默认 JDBC 地址",
                        "工具调用未传 jdbcUrl 时使用的默认连接地址，例如 jdbc:mysql://127.0.0.1:3306/demo",
                        ToolConfigItem.ConfigType.TEXT_SINGLE),
                item(JdbcSupport.CFG_USERNAME, "默认用户名",
                        "工具调用未传 username 时使用的默认数据库用户名",
                        ToolConfigItem.ConfigType.TEXT_SINGLE),
                new ToolConfigItem(JdbcSupport.CFG_PASSWORD, "默认密码",
                        "工具调用未传 password 时使用的默认数据库密码（加密存储）",
                        ToolConfigItem.ConfigType.TEXT_PASSWORD)
        );
    }

    /** 插件级配置项工厂：插件级配置多为连接地址/开关，默认非加密存储。 */
    private static ToolConfigItem item(String key, String label, String desc,
                                       ToolConfigItem.ConfigType type, OptionItem... options) {
        return new ToolConfigItem(key, label, desc, type, options).sensitive(false);
    }

    @Override
    public void asyncInit() {
        // 1) 先按插件配置构建共享资源，保证两个工具集拿到的是同一份已就绪的驱动注册表
        reloadRegistry();

        // 2) 再把共享资源注入给各工具集（插件即工具工厂）
        tools(
                new DatabaseQueryTool(driverRegistry),
                new DatabaseSchemaTool(driverRegistry)
        );
    }

    @Override
    public void onConfigChanged() {
        // 驱动目录 / 自动加载开关变更后就地重载：工具集持有的是同一实例，无需重新注册
        reloadRegistry();
    }

    @Override
    public void destroy() {
        driverRegistry.close();
    }

    private void reloadRegistry() {
        String dir = pluginConfigService == null
                ? null : pluginConfigService.get(PLUGIN_ID, JdbcSupport.CFG_DRIVER_DIR, null);
        boolean autoLoad = pluginConfigService == null
                || !"false".equalsIgnoreCase(
                        pluginConfigService.get(PLUGIN_ID, JdbcSupport.CFG_AUTO_LOAD, "true"));
        driverRegistry.reload(dir, autoLoad);
    }
}
