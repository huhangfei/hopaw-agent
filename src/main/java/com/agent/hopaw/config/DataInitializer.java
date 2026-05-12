package com.agent.hopaw.config;

import com.agent.hopaw.constant.DefaultUser;
import com.agent.hopaw.constant.ModelCapabilityEnum;
import com.agent.hopaw.constant.ModelProviderEnum;
import com.agent.hopaw.mapper.AgentMapper;
import com.agent.hopaw.mapper.AiModelMapper;
import com.agent.hopaw.mapper.AiModelProviderMapper;
import com.agent.hopaw.mapper.ChatHistoryMapper;
import com.agent.hopaw.model.Agent;
import com.agent.hopaw.model.AiModel;
import com.agent.hopaw.model.AiModelProvider;
import com.agent.hopaw.model.SysConfig;
import com.agent.hopaw.mapper.SysConfigMapper;
import com.agent.hopaw.tools.AgentTool;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DataInitializer implements CommandLineRunner {

    private final AgentMapper agentMapper;
    private final AiModelProviderMapper aiModelProviderMapper;
    private final AiModelMapper aiModelMapper;
    private final ChatHistoryMapper chatHistoryMapper;
    private final DataSource dataSource;
    private final List<AgentTool> allTools;
    private final SysConfigMapper sysConfigMapper;
    public DataInitializer(AgentMapper agentMapper, AiModelProviderMapper aiModelProviderMapper, AiModelMapper aiModelMapper, ChatHistoryMapper chatHistoryMapper, DataSource dataSource, List<AgentTool> allTools, SysConfigMapper sysConfigMapper) {
        this.agentMapper = agentMapper;
        this.aiModelProviderMapper = aiModelProviderMapper;
        this.aiModelMapper = aiModelMapper;
        this.chatHistoryMapper = chatHistoryMapper;
        this.dataSource = dataSource;
        this.allTools = allTools;
        this.sysConfigMapper = sysConfigMapper;

    }

    @Override
    public void run(String... args) throws Exception {
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
            
            stmt.execute("CREATE TABLE IF NOT EXISTS chat_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "agent_id INTEGER NOT NULL, " +
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

            // 兼容旧表：添加 user_id 列（如果不存在）
            try {
                stmt.execute("ALTER TABLE chat_history ADD COLUMN user_id TEXT");
            } catch (Exception ignored) {
            }

            // 兼容旧表：添加 tool_execution_time 列（如果不存在）
            try {
                stmt.execute("ALTER TABLE chat_history ADD COLUMN tool_execution_time INTEGER");
            } catch (Exception ignored) {
            }

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

            // 兼容旧表：添加 user_id 列（如果不存在）
            try {
                stmt.execute("ALTER TABLE chat_memory ADD COLUMN user_id TEXT DEFAULT 'user1'");
            } catch (Exception ignored) {
            }

            // 兼容旧表：将 cleaned 列重命名为 status（如果 cleaned 列存在）
            try {
                stmt.execute("ALTER TABLE chat_memory RENAME COLUMN cleaned TO status");
            } catch (Exception ignored) {
            }

            stmt.execute("CREATE TABLE IF NOT EXISTS long_term_memory (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "agent_id TEXT NOT NULL, " +
                    "memory TEXT, " +
                    "memory_hash TEXT, " +
                    "parent_id INTEGER, " +
                    "user_id TEXT, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_long_term_memory_agent ON long_term_memory(agent_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_long_term_memory_parent ON long_term_memory(parent_id)");

            // 兼容旧表：将 identity 重命名为 agent_id
            try {
                stmt.execute("ALTER TABLE long_term_memory RENAME COLUMN identity TO agent_id");
            } catch (Exception ignored) {
            }
            // 兼容旧表：添加 user_id 列（如果不存在）
            try {
                stmt.execute("ALTER TABLE long_term_memory ADD COLUMN user_id TEXT");
            } catch (Exception ignored) {
            }
            // 兼容旧表：添加 memory_type 列（如果不存在）
            try {
                stmt.execute("ALTER TABLE long_term_memory ADD COLUMN memory_type TEXT");
            } catch (Exception ignored) {
            }
            // 兼容旧表：添加 summary 列（如果不存在）
            try {
                stmt.execute("ALTER TABLE long_term_memory ADD COLUMN summary TEXT");
            } catch (Exception ignored) {
            }

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

            // 兼容旧表：添加 sdk_name 列（如果不存在）
            try {
                stmt.execute("ALTER TABLE ai_model_providers ADD COLUMN sdk_name TEXT");
            } catch (Exception ignored) {
                // 列已存在，忽略
            }

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

            // 兼容旧表：添加 update_time 列（如果不存在）
            try {
                stmt.execute("ALTER TABLE sys_config ADD COLUMN update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
            } catch (Exception ignored) {
            }

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

            // 兼容旧表：添加 user_id 列（如果不存在）
            try {
                stmt.execute("ALTER TABLE agents ADD COLUMN user_id TEXT DEFAULT 'user1'");
            } catch (Exception ignored) {
            }

            // 兼容旧表：添加 vector_tool_search 和 vector_tool_search_max_results 列
            try {
                stmt.execute("ALTER TABLE agents ADD COLUMN vector_tool_search INTEGER DEFAULT 1");
            } catch (Exception ignored) {
            }
            try {
                stmt.execute("ALTER TABLE agents ADD COLUMN vector_tool_search_max_results INTEGER DEFAULT 5");
            } catch (Exception ignored) {
            }

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_scheduled_tasks_type ON scheduled_tasks(task_type)");

            // 兼容旧表：将 identity 重命名为 user_id，添加 agent_id 列（如果不存在）
            try {
                stmt.execute("ALTER TABLE scheduled_tasks RENAME COLUMN identity TO user_id");
            } catch (Exception ignored) {
            }
            try {
                stmt.execute("ALTER TABLE scheduled_tasks ADD COLUMN agent_id TEXT");
            } catch (Exception ignored) {
            }
            try {
                stmt.execute("ALTER TABLE scheduled_tasks ADD COLUMN builtin INTEGER DEFAULT 0");
            } catch (Exception ignored) {
            }

            // 兼容旧表：添加 user_id 列（如果不存在）
            try {
                stmt.execute("ALTER TABLE token_usage ADD COLUMN user_id TEXT");
            } catch (Exception ignored) {
            }

            // 兼容旧表：添加 source 列（如果不存在）
            try {
                stmt.execute("ALTER TABLE token_usage ADD COLUMN source TEXT");
            } catch (Exception ignored) {
            }

            // 插入默认定时任务（如果表为空）
            try {
                stmt.execute("INSERT INTO scheduled_tasks (task_name, task_type, cron_expression, enabled, description, builtin) " +
                        "SELECT '日志测试', 'testLog', '0 */5 * * * *', 0, '每5分钟打印一条日志，用于测试定时任务', 1 " +
                        "WHERE NOT EXISTS (SELECT 1 FROM scheduled_tasks where task_type = 'testLog')");
            } catch (Exception ignored) {
            }try {
                stmt.execute("INSERT INTO scheduled_tasks (task_name, task_type, cron_expression, enabled, description, builtin) " +
                        "SELECT '长时记忆整理', 'longTermMemory', '0/50 * * * * *', 0, '每50秒钟执行一次长时记忆整理', 1 " +
                        "WHERE NOT EXISTS (SELECT 1 FROM scheduled_tasks where task_type = 'longTermMemory')");
            } catch (Exception ignored) {
            }
        }

        List<AiModelProvider> aiModelProviders = aiModelProviderMapper.findAll();
        if (aiModelProviders.isEmpty()) {
            for (ModelProviderEnum providerEnum : ModelProviderEnum.values()) {
                AiModelProvider provider = new AiModelProvider(providerEnum.getName(), providerEnum.getCode(), "builtin", providerEnum.getDefaultUrl(), null);
                provider.setIcon(providerEnum.getIcon());
                provider.setSdkName(providerEnum.getSdkName());
                aiModelProviderMapper.insert(provider);
            }
            aiModelProviders = aiModelProviderMapper.findAll();
        }

        List<AiModel> aiModels = aiModelMapper.findAll();
        if (aiModels.isEmpty()) {
            for (AiModelProvider provider : aiModelProviders) {
                switch (provider.getProvider()) {
                    case "openai":
                        aiModelMapper.insert(new AiModel(provider.getId(), "gpt-4o", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.IMAGE), false));
                        aiModelMapper.insert(new AiModel(provider.getId(), "gpt-4o-mini", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.IMAGE), false));
                        aiModelMapper.insert(new AiModel(provider.getId(), "gpt-4-turbo", ModelCapabilityEnum.TEXT.getCode(), false));
                        aiModelMapper.insert(new AiModel(provider.getId(), "gpt-3.5-turbo", ModelCapabilityEnum.TEXT.getCode(), false));
                        break;
                    case "anthropic":
                        aiModelMapper.insert(new AiModel(provider.getId(), "claude-sonnet-4-20250514", ModelCapabilityEnum.TEXT.getCode(), false));
                        aiModelMapper.insert(new AiModel(provider.getId(), "claude-opus-4-20250514", ModelCapabilityEnum.TEXT.getCode(), false));
                        aiModelMapper.insert(new AiModel(provider.getId(), "claude-3-5-sonnet-20241022", ModelCapabilityEnum.TEXT.getCode(), false));
                        aiModelMapper.insert(new AiModel(provider.getId(), "claude-3-haiku-20240307", ModelCapabilityEnum.TEXT.getCode(), false));
                        break;
                    case "google":
                        aiModelMapper.insert(new AiModel(provider.getId(), "gemini-2.5-pro", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.IMAGE, ModelCapabilityEnum.AUDIO, ModelCapabilityEnum.VIDEO), false));
                        aiModelMapper.insert(new AiModel(provider.getId(), "gemini-2.5-flash", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.IMAGE, ModelCapabilityEnum.AUDIO, ModelCapabilityEnum.VIDEO), false));
                        aiModelMapper.insert(new AiModel(provider.getId(), "gemini-2.0-flash", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.IMAGE), false));
                        break;
                    case "deepseek":
                        aiModelMapper.insert(new AiModel(provider.getId(), "deepseek-chat", ModelCapabilityEnum.TEXT.getCode(), false));
                        aiModelMapper.insert(new AiModel(provider.getId(), "deepseek-reasoner", ModelCapabilityEnum.TEXT.getCode(), false));
                        aiModelMapper.insert(new AiModel(provider.getId(), "deepseek-v4-flash", ModelCapabilityEnum.TEXT.getCode(), false));
                        aiModelMapper.insert(new AiModel(provider.getId(), "deepseek-v4-pro", ModelCapabilityEnum.TEXT.getCode(), false));
                        break;
                    case "qwen":
                        aiModelMapper.insert(new AiModel(provider.getId(), "qwen-max", ModelCapabilityEnum.TEXT.getCode(), false));
                        aiModelMapper.insert(new AiModel(provider.getId(), "qwen-plus", ModelCapabilityEnum.TEXT.getCode(), false));
                        aiModelMapper.insert(new AiModel(provider.getId(), "qwen-turbo", ModelCapabilityEnum.TEXT.getCode(), false));
                        aiModelMapper.insert(new AiModel(provider.getId(), "qwen-vl-max", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.IMAGE), false));
                        aiModelMapper.insert(new AiModel(provider.getId(), "qwen-long", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.DOCUMENT), false));
                        break;
                    case "zhipu":
                        aiModelMapper.insert(new AiModel(provider.getId(), "glm-4-plus", ModelCapabilityEnum.TEXT.getCode(), false));
                        aiModelMapper.insert(new AiModel(provider.getId(), "glm-4", ModelCapabilityEnum.TEXT.getCode(), false));
                        aiModelMapper.insert(new AiModel(provider.getId(), "glm-4v", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.IMAGE), false));
                        aiModelMapper.insert(new AiModel(provider.getId(), "glm-4-flash", ModelCapabilityEnum.TEXT.getCode(), false));
                        break;
                    case "moonshot":
                        aiModelMapper.insert(new AiModel(provider.getId(), "moonshot-v1-8k", ModelCapabilityEnum.TEXT.getCode(), false));
                        aiModelMapper.insert(new AiModel(provider.getId(), "moonshot-v1-32k", ModelCapabilityEnum.TEXT.getCode(), false));
                        aiModelMapper.insert(new AiModel(provider.getId(), "moonshot-v1-128k", ModelCapabilityEnum.TEXT.getCode(), false));
                        break;
                    case "minimax":
                        aiModelMapper.insert(new AiModel(provider.getId(), "MiniMax-M1", ModelCapabilityEnum.TEXT.getCode(), false));
                        aiModelMapper.insert(new AiModel(provider.getId(), "MiniMax-Text-01", ModelCapabilityEnum.TEXT.getCode(), false));
                        break;
                }
            }
        }

        List<Agent> agents = agentMapper.findAll();
        if (agents.isEmpty()) {
            String tools = allTools.stream().map(x -> x.getName()).collect(Collectors.joining(","));
            Agent agent = new Agent("大虾🦐", "回答各种问题，使用多种工具", tools, 20, 20, true);
            agent.setUserId(DefaultUser.USER);
            agentMapper.insert(agent);

        }

        if (sysConfigMapper.findByKey("memory_prompt")==null) {
            String customPrompt="你是一个记忆整理助手。善于根据聊天记录提取关键的用户记忆信息。" +
                    "请根据已提供的现有记忆和新会话总结出用户的关键记忆信息（拓展知识仅提供了概要，如果发现有新总结出的扩展知识需要校验时，再对应的去查询某一条详情即可），要严格按以下要求进行分类整理：\n" +
                    "========\n" +
                    "分类1，用户画像\n" +
                    "内容包含：姓名、昵称、年龄、地域、职业、收入、常用设备、喜好、交流风格、偏好与厌恶、经常提的要求规则等，只记录简短的用户各种标签。\n" +
                    "整理限制: 请给出一个简短的标签作为概要(比如：姓名及昵称、爱好及延误、服务器清单、联系方式等)，具体事实作为画像内容，内容要精简；每条画像不宜过细，相同分类的共用一条即可。\n" +
                    "分类2，任务记录\n" +
                    "内容包含：正在做的什么事情（开始时间、任务说明、任务过程主要节点、结果、结束时间）。\n" +
                    "整理限制: 每次可以汇总出一条或多条不同任务记录，要根据具体的对话场景和已有的任务记录做判断，那些是旧任务的延续，哪些是新任务的开始；旧任务就更新内容新任务就新增内容；" +
                    "每条任务都要汇总出一段简短的任务概要；内容要抓住终点，涵盖完整任务内容但不要啰嗦。\n" +
                    "分类3，扩展知识\n" +
                    "内容包含：解决问题的经验、操作指导说明、明确的操作步骤。\n" +
                    "整理限制: 请给出一个简短的问题对象描述作为概要(比如，安装git经验、docker镜像源超时等);内容：要从对话中总结解决问题的正确经验，汇总出操作指导说明，梳理出明确的操作步骤。过于简单的问题（通过搜索可以快速找到答案的、经过3步以内尝试解决的），请勿记录。\n" +
                    "========\n" +
                    "请认真总结记忆得到清单后进行检查，不要有重复的记忆，发现已有重复记忆请删除,记忆内容不能胡编乱造信息，要完全从内容中来，冲突的记忆以最新的为准。\n" +
                    "在完成记忆总结后，有新增或修改记忆时必须调用记忆操作相关工具，保存完再进行查询检查一下。\n"+
                    "你是一个后台助手，做好任务即可不需要回复我任何信息。\n";
            sysConfigMapper.insert(new SysConfig("memory_prompt", customPrompt, "记忆整理提示词"));
        }
        if (sysConfigMapper.findByKey("memory_ai_model_id")==null) {
            sysConfigMapper.insert(new SysConfig("memory_ai_model_id", "", "记忆整理使用模型"));
        }
    }

    private String joinCapabilities(ModelCapabilityEnum... capabilities) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < capabilities.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(capabilities[i].getCode());
        }
        return sb.toString();
    }
}
