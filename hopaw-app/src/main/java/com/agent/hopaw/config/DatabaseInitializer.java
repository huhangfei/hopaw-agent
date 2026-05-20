package com.agent.hopaw.config;

import com.agent.hopaw.constant.DefaultUser;
import com.agent.hopaw.infra.constant.ModelCapabilityEnum;
import com.agent.hopaw.infra.constant.ModelProviderEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Component
@Order(1)
public class DatabaseInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final DataSource dataSource;

    public DatabaseInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("=== Start database initialization ===");
        createDatabaseSchema();
        initializeDefaultData();
        log.info("=== Database initialization completed ===");
    }

    private void createDatabaseSchema() throws Exception {
        log.info("Creating database tables...");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE IF NOT EXISTS agents (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL, " +
                    "description TEXT, " +
                    "tools TEXT, " +
                    "max_memory_records INTEGER DEFAULT 20, " +
                    "max_tool_invocations INTEGER DEFAULT 20, " +
                    "vector_tool_search INTEGER DEFAULT 1, " +
                    "vector_tool_search_max_results INTEGER DEFAULT 5, " +
                    "ai_model_id INTEGER, " +
                    "model_name TEXT, " +
                    "enable_thinking INTEGER DEFAULT 1," +
                    "ext_params TEXT," +
                    "user_id TEXT DEFAULT 'user1'" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_agents_user ON agents(user_id)");

            stmt.execute("CREATE TABLE IF NOT EXISTS chat_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "agent_id INTEGER NOT NULL, " +
                    "session_id TEXT, " +
                    "role TEXT NOT NULL, " +
                    "message_type TEXT NOT NULL DEFAULT 'text', " +
                    "content TEXT, " +
                    "tool_call_id TEXT, " +
                    "tool_name TEXT, " +
                    "tool_arguments TEXT, " +
                    "tool_call_status TEXT, " +
                    "tool_execution_time INTEGER, " +
                    "user_id TEXT, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_history_agent ON chat_history(agent_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_history_session ON chat_history(session_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_history_user ON chat_history(user_id)");

            stmt.execute("CREATE TABLE IF NOT EXISTS chat_memory (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "agent_id INTEGER NOT NULL, " +
                    "user_id TEXT DEFAULT 'user1', " +
                    "message_id TEXT NOT NULL, " +
                    "message_json TEXT NOT NULL, " +
                    "status INTEGER DEFAULT 0, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_memory_agent ON chat_memory(agent_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_memory_agent_user ON chat_memory(agent_id, user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_memory_message_id ON chat_memory(message_id)");

            stmt.execute("CREATE TABLE IF NOT EXISTS long_term_memory (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "agent_id TEXT NOT NULL, " +
                    "memory TEXT, " +
                    "memory_hash TEXT, " +
                    "parent_id INTEGER, " +
                    "user_id TEXT, " +
                    "memory_type TEXT, " +
                    "summary TEXT, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_long_term_memory_agent ON long_term_memory(agent_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_long_term_memory_parent ON long_term_memory(parent_id)");

            stmt.execute("CREATE TABLE IF NOT EXISTS ai_model_providers (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL, " +
                    "provider TEXT NOT NULL, " +
                    "type TEXT NOT NULL DEFAULT 'custom', " +
                    "url TEXT, " +
                    "api_key TEXT, " +
                    "icon TEXT, " +
                    "sdk_name TEXT, " +
                    "ext_params TEXT, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS ai_models (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "provider_id INTEGER NOT NULL, " +
                    "model_name TEXT NOT NULL, " +
                    "capabilities TEXT, " +
                    "verified INTEGER DEFAULT 0, " +
                    "ext_params TEXT, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ai_models_provider ON ai_models(provider_id)");

            stmt.execute("CREATE TABLE IF NOT EXISTS token_usage (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "agent_id INTEGER, " +
                    "model_name TEXT, " +
                    "input_tokens INTEGER DEFAULT 0, " +
                    "output_tokens INTEGER DEFAULT 0, " +
                    "total_tokens INTEGER DEFAULT 0, " +
                    "user_id TEXT, " +
                    "source TEXT, " +
                    "create_time DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS sys_config (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "config_key TEXT NOT NULL UNIQUE, " +
                    "config_value TEXT, " +
                    "description TEXT, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sys_config_key ON sys_config(config_key)");

            stmt.execute("CREATE TABLE IF NOT EXISTS scheduled_tasks (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "task_name TEXT NOT NULL, " +
                    "task_type TEXT NOT NULL, " +
                    "cron_expression TEXT NOT NULL, " +
                    "enabled INTEGER DEFAULT 1, " +
                    "description TEXT, " +
                    "ext_params TEXT, " +
                    "user_id TEXT, " +
                    "agent_id TEXT, " +
                    "builtin INTEGER DEFAULT 0, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_scheduled_tasks_type ON scheduled_tasks(task_type)");

            stmt.execute("CREATE TABLE IF NOT EXISTS chat_sessions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "session_id TEXT NOT NULL UNIQUE, " +
                    "agent_id INTEGER NOT NULL, " +
                    "user_id TEXT NOT NULL, " +
                    "title TEXT, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "last_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_sessions_user ON chat_sessions(user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_sessions_agent ON chat_sessions(agent_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_sessions_user_agent ON chat_sessions(user_id, agent_id)");

            log.info("Database tables created");
        }
    }

    private void initializeDefaultData() throws Exception {
        log.info("Initializing default data...");

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            long providerCount = countTableRows(stmt, "ai_model_providers");
            if (providerCount == 0) {
                log.info("Initializing model providers...");
                for (ModelProviderEnum providerEnum : ModelProviderEnum.values()) {
                    stmt.execute(String.format(
                            "INSERT INTO ai_model_providers (name, provider, type, url, icon, sdk_name) VALUES ('%s', '%s', 'builtin', '%s', '%s', '%s')",
                            escapeSQL(providerEnum.getName()),
                            escapeSQL(providerEnum.getCode()),
                            escapeSQL(providerEnum.getDefaultUrl()),
                            escapeSQL(providerEnum.getIcon()),
                            escapeSQL(providerEnum.getSdkName())
                    ));
                }
            }

            long modelCount = countTableRows(stmt, "ai_models");
            if (modelCount == 0) {
                log.info("Initializing models...");
                long providerId = 1;
                for (ModelProviderEnum providerEnum : ModelProviderEnum.values()) {
                    switch (providerEnum.getCode()) {
                        case "openai":
                            insertModel(stmt, providerId, "gpt-4o", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.IMAGE));
                            insertModel(stmt, providerId, "gpt-4o-mini", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.IMAGE));
                            insertModel(stmt, providerId, "gpt-4-turbo", ModelCapabilityEnum.TEXT.getCode());
                            insertModel(stmt, providerId, "gpt-3.5-turbo", ModelCapabilityEnum.TEXT.getCode());
                            break;
                        case "anthropic":
                            insertModel(stmt, providerId, "claude-sonnet-4-20250514", ModelCapabilityEnum.TEXT.getCode());
                            insertModel(stmt, providerId, "claude-opus-4-20250514", ModelCapabilityEnum.TEXT.getCode());
                            insertModel(stmt, providerId, "claude-3-5-sonnet-20241022", ModelCapabilityEnum.TEXT.getCode());
                            insertModel(stmt, providerId, "claude-3-haiku-20240307", ModelCapabilityEnum.TEXT.getCode());
                            break;
                        case "google":
                            insertModel(stmt, providerId, "gemini-2.5-pro", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.IMAGE, ModelCapabilityEnum.AUDIO, ModelCapabilityEnum.VIDEO));
                            insertModel(stmt, providerId, "gemini-2.5-flash", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.IMAGE, ModelCapabilityEnum.AUDIO, ModelCapabilityEnum.VIDEO));
                            insertModel(stmt, providerId, "gemini-2.0-flash", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.IMAGE));
                            break;
                        case "deepseek":
                            insertModel(stmt, providerId, "deepseek-chat", ModelCapabilityEnum.TEXT.getCode());
                            insertModel(stmt, providerId, "deepseek-reasoner", ModelCapabilityEnum.TEXT.getCode());
                            insertModel(stmt, providerId, "deepseek-v4-flash", ModelCapabilityEnum.TEXT.getCode());
                            insertModel(stmt, providerId, "deepseek-v4-pro", ModelCapabilityEnum.TEXT.getCode());
                            break;
                        case "qwen":
                            insertModel(stmt, providerId, "qwen-max", ModelCapabilityEnum.TEXT.getCode());
                            insertModel(stmt, providerId, "qwen-plus", ModelCapabilityEnum.TEXT.getCode());
                            insertModel(stmt, providerId, "qwen-turbo", ModelCapabilityEnum.TEXT.getCode());
                            insertModel(stmt, providerId, "qwen-vl-max", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.IMAGE));
                            insertModel(stmt, providerId, "qwen-long", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.DOCUMENT));
                            break;
                        case "zhipu":
                            insertModel(stmt, providerId, "glm-4-plus", ModelCapabilityEnum.TEXT.getCode());
                            insertModel(stmt, providerId, "glm-4", ModelCapabilityEnum.TEXT.getCode());
                            insertModel(stmt, providerId, "glm-4v", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.IMAGE));
                            insertModel(stmt, providerId, "glm-4-flash", ModelCapabilityEnum.TEXT.getCode());
                            break;
                        case "moonshot":
                            insertModel(stmt, providerId, "moonshot-v1-8k", ModelCapabilityEnum.TEXT.getCode());
                            insertModel(stmt, providerId, "moonshot-v1-32k", ModelCapabilityEnum.TEXT.getCode());
                            insertModel(stmt, providerId, "moonshot-v1-128k", ModelCapabilityEnum.TEXT.getCode());
                            break;
                        case "minimax":
                            insertModel(stmt, providerId, "MiniMax-M1", ModelCapabilityEnum.TEXT.getCode());
                            insertModel(stmt, providerId, "MiniMax-Text-01", ModelCapabilityEnum.TEXT.getCode());
                            break;
                    }
                    providerId++;
                }
            }

            long agentCount = countTableRows(stmt, "agents");
            if (agentCount == 0) {
                log.info("Initializing default agent...");
                String tools = "agentTaskTool,commandExecutor,getCurrentTime,mailTool,memoryTool,sysConfigTool,baiduSearch";
                stmt.execute(String.format(
                        "INSERT INTO agents (name, description, tools, max_memory_records, max_tool_invocations, vector_tool_search, vector_tool_search_max_results, user_id, enable_thinking) VALUES ('%s', '%s', '%s', %d, %d, %d, %d, '%s', %d)",
                        escapeSQL("大虾"),
                        escapeSQL("Answer various questions using multiple tools"),
                        escapeSQL(tools),
                        20, 20, 1, 15,
                        DefaultUser.USER,
                        1
                ));
            }

            if (!configExists(stmt, "memory_prompt")) {
                log.info("Initializing memory prompt...");
                String customPrompt = "You are a memory organizer...";
                stmt.execute(String.format(
                        "INSERT INTO sys_config (config_key, config_value, description) VALUES ('%s', '%s', '%s')",
                        "memory_prompt",
                        escapeSQL(customPrompt),
                        "Memory prompt"
                ));
            }

            if (!configExists(stmt, "memory_ai_model_id")) {
                stmt.execute(String.format(
                        "INSERT INTO sys_config (config_key, config_value, description) VALUES ('%s', '%s', '%s')",
                        "memory_ai_model_id",
                        "",
                        "Memory model ID"
                ));
            }

            long taskCount = countTableRows(stmt, "scheduled_tasks");
            if (taskCount == 0) {
                log.info("Initializing default scheduled tasks...");
                stmt.execute("INSERT INTO scheduled_tasks (task_name, task_type, cron_expression, enabled, description, builtin) " +
                        "SELECT 'Log Test', 'testLog', '0 */5 * * * *', 0, 'Print log every 5 minutes for testing', 1 " +
                        "WHERE NOT EXISTS (SELECT 1 FROM scheduled_tasks WHERE task_type = 'testLog')");
                stmt.execute("INSERT INTO scheduled_tasks (task_name, task_type, cron_expression, enabled, description, builtin) " +
                        "SELECT 'Long Term Memory', 'longTermMemory', '0/50 * * * * *', 0, 'Execute memory cleanup every 50 seconds', 1 " +
                        "WHERE NOT EXISTS (SELECT 1 FROM scheduled_tasks WHERE task_type = 'longTermMemory')");
            }

            log.info("Default data initialization completed");
        }
    }

    private long countTableRows(Statement stmt, String table) throws Exception {
        try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return 0;
    }

    private boolean configExists(Statement stmt, String key) throws Exception {
        try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM sys_config WHERE config_key = '" + escapeSQL(key) + "'")) {
            if (rs.next()) {
                return rs.getLong(1) > 0;
            }
        }
        return false;
    }

    private void insertModel(Statement stmt, long providerId, String modelName, String capabilities) throws Exception {
        stmt.execute(String.format(
                "INSERT INTO ai_models (provider_id, model_name, capabilities, verified) VALUES (%d, '%s', '%s', %d)",
                providerId,
                escapeSQL(modelName),
                escapeSQL(capabilities),
                0
        ));
    }

    private String escapeSQL(String value) {
        if (value == null) return "";
        return value.replace("'", "''");
    }

    private String joinCapabilities(ModelCapabilityEnum... capabilities) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < capabilities.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(capabilities[i].getCode());
        }
        return sb.toString();
    }
}
