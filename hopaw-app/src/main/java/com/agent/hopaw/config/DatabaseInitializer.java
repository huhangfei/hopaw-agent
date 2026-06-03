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
import java.util.HashMap;
import java.util.Map;

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
                    "user_id TEXT DEFAULT 'admin'" +
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
                    "user_id TEXT DEFAULT 'admin', " +
                    "message_id TEXT NOT NULL, " +
                    "message_json TEXT NOT NULL, " +
                    "status INTEGER DEFAULT 0, " +
                    "session_id TEXT, " +
                    "request_id TEXT, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_memory_session ON chat_memory(session_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_memory_request ON chat_memory(request_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_memory_user ON chat_memory(user_id)");

            stmt.execute("CREATE TABLE IF NOT EXISTS chat_memory_obsolete (" +
                    "id INTEGER PRIMARY KEY, " +
                    "agent_id INTEGER NOT NULL, " +
                    "user_id TEXT DEFAULT 'admin', " +
                    "message_id TEXT NOT NULL, " +
                    "message_json TEXT NOT NULL, " +
                    "status INTEGER DEFAULT 0, " +
                    "session_id TEXT, " +
                    "request_id TEXT, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_memory_session ON chat_memory_obsolete(session_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_memory_request ON chat_memory_obsolete(request_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_memory_user ON chat_memory_obsolete(user_id)");

            // 记忆整理进度游标：记录每个 (session_id, user_id) 已处理到的 chat_memory / chat_memory_obsolete 最大 id
            stmt.execute("CREATE TABLE IF NOT EXISTS chat_memory_processed_cursor (" +
                    "session_id TEXT NOT NULL, " +
                    "user_id TEXT NOT NULL, " +
                    "last_chat_memory_id INTEGER DEFAULT 0, " +
                    "last_obsolete_memory_id INTEGER DEFAULT 0, " +
                    "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "PRIMARY KEY (session_id, user_id)" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_memory_cursor_user ON chat_memory_processed_cursor(user_id)");

            stmt.execute("CREATE TABLE IF NOT EXISTS long_term_memory (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "memory TEXT, " +
                    "memory_hash TEXT, " +
                    "parent_id INTEGER, " +
                    "user_id TEXT, " +
                    "memory_type TEXT, " +
                    "summary TEXT, " +
                    "session_id TEXT, " +
                    "embedding_id TEXT, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_long_term_memory_session ON long_term_memory(session_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_long_term_memory_embedding ON long_term_memory(embedding_id)");

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
                    "session_id TEXT, " +
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

            stmt.execute("CREATE TABLE IF NOT EXISTS user_config (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id TEXT NOT NULL, " +
                    "config_key TEXT NOT NULL, " +
                    "config_value TEXT, " +
                    "description TEXT, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_user_config_user_key ON user_config(user_id, config_key)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_config_user ON user_config(user_id)");


            stmt.execute("CREATE TABLE IF NOT EXISTS agent_avatar_config (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id TEXT NOT NULL, " +
                    "agent_id INTEGER NOT NULL, " +
                    "disabled INTEGER DEFAULT 0, " +
                    "model_setting TEXT, " +
                    "model_group TEXT, " +
                    "persona_setting TEXT, " +
                    "avatar_ai_prompt TEXT, " +
                    "total_tokens INTEGER DEFAULT 0, " +
                    "last_processed_chat_id INTEGER DEFAULT 0, " +
                    "sound_enabled INTEGER DEFAULT 1, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_agent_avatar_config_user_agent ON agent_avatar_config(user_id, agent_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_agent_avatar_config_user ON agent_avatar_config(user_id)");

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
                    "session_id TEXT, " +
                    "builtin INTEGER DEFAULT 0, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_scheduled_tasks_type ON scheduled_tasks(task_type)");

            stmt.execute("CREATE TABLE IF NOT EXISTS chat_sessions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "session_id TEXT NOT NULL UNIQUE, " +
                    "user_id TEXT NOT NULL, " +
                    "agent_id INTEGER, " +
                    "title TEXT, " +
                    "enable_thinking INTEGER DEFAULT 1, " +
                    "skill_names TEXT, " +
                    "ai_model_id INTEGER, " +
                    "tool_call_permission TEXT, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "last_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_sessions_user ON chat_sessions(user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_sessions_session ON chat_sessions(session_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_sessions_user_session ON chat_sessions(user_id, session_id)");

            try {
                stmt.execute("ALTER TABLE chat_sessions ADD COLUMN enable_thinking INTEGER DEFAULT 1");
            } catch (Exception ignored) {}
            try {
                stmt.execute("ALTER TABLE chat_sessions ADD COLUMN skill_names TEXT");
            } catch (Exception ignored) {}
            try {
                stmt.execute("ALTER TABLE chat_sessions ADD COLUMN ai_model_id INTEGER");
            } catch (Exception ignored) {}
            try {
                stmt.execute("ALTER TABLE chat_sessions ADD COLUMN tool_call_permission TEXT");
            } catch (Exception ignored) {}

            stmt.execute("CREATE TABLE IF NOT EXISTS accounts (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "user_id TEXT NOT NULL UNIQUE, " +
                    "username TEXT NOT NULL, " +
                    "nickname TEXT, " +
                    "status INTEGER DEFAULT 1, " +
                    "remark TEXT, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_accounts_user_id ON accounts(user_id)");

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
                String tools = "agentTaskTool,getCurrentTime,memoryTool,sysConfigTool,skillTool,mailTool,commandExecutor,baiduSearch,webPage,sshTool,fileOperation,dingtalkNotify";
                stmt.execute(String.format(
                        "INSERT INTO agents (name, description, tools, max_memory_records, max_tool_invocations, vector_tool_search, vector_tool_search_max_results, user_id, enable_thinking) VALUES ('%s', '%s', '%s', %d, %d, %d, %d, '%s', %d)",
                        escapeSQL("大虾\uD83E\uDD90"),
                        escapeSQL("善于使用多种工具解决用户问题"),
                        escapeSQL(tools),
                        20, 20, 1, 15,
                        DefaultUser.USER,
                        1
                ));
            }

            if (!configExists(stmt, "memory_prompt")) {
                log.info("Initializing memory prompt...");
                String customPrompt="你是一个记忆整理助手。善于根据聊天记录提取关键的用户记忆信息。" +
                        "请根据已提供的现有记忆和新会话总结出用户的关键记忆信息（拓展知识仅提供了概要，如果发现有新总结出的扩展知识需要校验时，再对应的去查询某一条详情即可），要严格按以下要求进行分类整理：\n" +
                        "========\n" +
                        "分类1，用户画像\n" +
                        "内容包含：姓名、昵称、年龄、地域、职业、收入、常用设备、喜好、交流风格、偏好与厌恶、经常提的要求规则等，只记录简短的用户各种标签。\n" +
                        "整理限制: 请给出一个简短的标签作为概要(比如：姓名及昵称、爱好及延误、服务器清单、联系方式等)，具体事实作为画像内容，内容要精简；每条画像不宜过细，相同分类的共用一条即可。\n" +
                        "分类2，任务记录\n" +
                        "内容包含：正在做的什么事情（开始时间、任务说明、任务过程主要节点、结果、结束时间）。\n" +
                        "整理限制: 每次可以汇总出一条或多条不同任务记录，要根据具体的对话场景和已有的任务记录做判断，那些是旧任务的延续，哪些是新任务的开始；旧任务就更新内容新任务就新增内容；" +
                        "每条任务都要汇总出一段简短的任务概要；内容要抓住重点，涵盖完整任务内容但不要啰嗦。\n" +
                        "分类3，经验知识\n" +
                        "内容包含：解决问题的经验、操作指导说明、明确的操作步骤。\n" +
                        "整理限制: 请给出一个简短的问题对象描述作为概要(比如，安装git经验、docker镜像源超时等);内容：要从对话中总结解决问题的正确经验，汇总出操作指导说明，梳理出明确的操作步骤。过于简单的问题（通过搜索可以快速找到答案的、经过3步以内尝试解决的），请勿记录，发现多个知识属于同一类型或者同一技术方向的请合并。\n" +
                        "========\n" +
                        "请认真总结记忆得到清单后进行检查，不要有重复的记忆，发现已有重复记忆请删除,记忆内容不能胡编乱造信息，要完全从内容中来，冲突的记忆以最新的为准。\n" +
                        "在完成记忆总结后，有新增或修改记忆时必须调用记忆操作相关工具，保存完再进行查询检查一下。\n"+
                        "你是一个后台助手，做好任务即可不需要回复我任何信息。\n";

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

            if (!configExists(stmt, "skill.dir")) {
                stmt.execute(String.format(
                        "INSERT INTO sys_config (config_key, config_value, description) VALUES ('%s', '%s', '%s')",
                        "skill.dir",
                        "skills",
                        "技能文件存放目录"
                ));
            }

            long taskCount = countTableRows(stmt, "scheduled_tasks");
            if (taskCount == 0) {
                log.info("Initializing default scheduled tasks...");
                stmt.execute("INSERT INTO scheduled_tasks (task_name, task_type, cron_expression, enabled, description, builtin) " +
                        "SELECT 'Log Test', 'testLog', '0 */5 * * * *', 0, 'Print log every 5 minutes for testing', 1 " +
                        "WHERE NOT EXISTS (SELECT 1 FROM scheduled_tasks WHERE task_type = 'testLog')");
                stmt.execute("INSERT INTO scheduled_tasks (task_name, task_type, cron_expression, enabled, description, builtin) " +
                        "SELECT 'Long Term Memory', 'longTermMemory', '0/50 * * * * *', 1, 'Execute memory cleanup every 50 seconds', 1 " +
                        "WHERE NOT EXISTS (SELECT 1 FROM scheduled_tasks WHERE task_type = 'longTermMemory')");
                stmt.execute("INSERT INTO scheduled_tasks (task_name, task_type, cron_expression, enabled, description, builtin) " +
                        "SELECT 'Avatar Task', 'avatar', '0 0/2 * * * *', 1, 'Execute avatar module task every 30 seconds', 1 " +
                        "WHERE NOT EXISTS (SELECT 1 FROM scheduled_tasks WHERE task_type = 'avatar')");
            }

            long accountCount = countTableRows(stmt, "accounts");
            if (accountCount == 0) {
                log.info("Initializing default account...");
                stmt.execute(String.format(
                        "INSERT INTO accounts (user_id, username, nickname, status, remark) VALUES ('%s', '%s', '%s', %d, '%s')",
                        escapeSQL(DefaultUser.USER),
                        escapeSQL(DefaultUser.USER),
                        escapeSQL("默认用户"),
                        1,
                        escapeSQL("系统初始化默认账户")
                ));
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
