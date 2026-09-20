package com.agent.hopaw.tool.database;

import com.agent.hopaw.infra.plugin.AbstractAgentPlugin;

/**
 * 数据库插件主体。
 */
public class DatabasePlugin extends AbstractAgentPlugin {

    public DatabasePlugin() {
        // 插件作为工具工厂：分发本插件提供的工具实例
        tools(new DatabaseTool());
    }

    @Override
    public String getId() {
        return "databaseTool";
    }

    @Override
    public String getDescription() {
        return "通用数据库操作工具，驱动从本地 JAR 动态加载，支持 MySQL / PostgreSQL / MariaDB / SQL Server / H2 / SQLite 等";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getKeyword() {
        return "数据库 查询 SQL JDBC";
    }

    @Override
    public String getIcon() {
        return "database-tool.svg";
    }
}
