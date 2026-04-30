package com.agent.hopaw.config;

import com.agent.hopaw.constant.ModelCapabilityEnum;
import com.agent.hopaw.constant.ModelProviderEnum;
import com.agent.hopaw.mapper.AgentMapper;
import com.agent.hopaw.mapper.AiModelMapper;
import com.agent.hopaw.mapper.AiModelProviderMapper;
import com.agent.hopaw.mapper.ChatHistoryMapper;
import com.agent.hopaw.model.Agent;
import com.agent.hopaw.model.AiModel;
import com.agent.hopaw.model.AiModelProvider;
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
    public DataInitializer(AgentMapper agentMapper, AiModelProviderMapper aiModelProviderMapper, AiModelMapper aiModelMapper, ChatHistoryMapper chatHistoryMapper, DataSource dataSource, List<AgentTool> allTools) {
        this.agentMapper = agentMapper;
        this.aiModelProviderMapper = aiModelProviderMapper;
        this.aiModelMapper = aiModelMapper;
        this.chatHistoryMapper = chatHistoryMapper;
        this.dataSource = dataSource;
        this.allTools = allTools;
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
                    "ai_model_id INTEGER, " +
                    "model_name TEXT, " +
                    "enable_thinking INTEGER DEFAULT 1" +
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
                    "thinking_content TEXT, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS chat_memory (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "agent_id INTEGER NOT NULL, " +
                    "message_id TEXT NOT NULL, " +
                    "message_json TEXT NOT NULL, " +
                    "cleaned INTEGER DEFAULT 0, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_memory_agent ON chat_memory(agent_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chat_memory_message_id ON chat_memory(message_id)");

            stmt.execute("CREATE TABLE IF NOT EXISTS long_term_memory (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "identity TEXT NOT NULL, " +
                    "memory TEXT, " +
                    "memory_hash TEXT, " +
                    "parent_id INTEGER, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_long_term_memory_identity ON long_term_memory(identity)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_long_term_memory_parent ON long_term_memory(parent_id)");

            stmt.execute("CREATE TABLE IF NOT EXISTS ai_model_providers (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL, " +
                    "provider TEXT NOT NULL, " +
                    "type TEXT NOT NULL DEFAULT 'custom', " +
                    "url TEXT, " +
                    "api_key TEXT, " +
                    "icon TEXT, " +
                    "ext_params TEXT, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS ai_models (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "provider_id INTEGER NOT NULL, " +
                    "model_name TEXT NOT NULL, " +
                    "capabilities TEXT, " +
                    "verified INTEGER DEFAULT 0, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ai_models_provider ON ai_models(provider_id)");
        }

        List<AiModelProvider> aiModelProviders = aiModelProviderMapper.findAll();
        if (aiModelProviders.isEmpty()) {
            for (ModelProviderEnum providerEnum : ModelProviderEnum.values()) {
                AiModelProvider provider = new AiModelProvider(providerEnum.getName(), providerEnum.getCode(), "builtin", providerEnum.getDefaultUrl(), null);
                provider.setIcon(providerEnum.getIcon());
                aiModelProviderMapper.insert(provider);
            }
            aiModelProviders = aiModelProviderMapper.findAll();
        }

        List<AiModel> aiModels = aiModelMapper.findAll();
        if (aiModels.isEmpty()) {
            for (AiModelProvider provider : aiModelProviders) {
                switch (provider.getProvider()) {
                    case "openai":
                        aiModelMapper.insert(new AiModel(provider.getId(), "gpt-4o", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.IMAGE), true));
                        aiModelMapper.insert(new AiModel(provider.getId(), "gpt-4o-mini", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.IMAGE), true));
                        aiModelMapper.insert(new AiModel(provider.getId(), "gpt-4-turbo", ModelCapabilityEnum.TEXT.getCode(), true));
                        aiModelMapper.insert(new AiModel(provider.getId(), "gpt-3.5-turbo", ModelCapabilityEnum.TEXT.getCode(), true));
                        break;
                    case "anthropic":
                        aiModelMapper.insert(new AiModel(provider.getId(), "claude-sonnet-4-20250514", ModelCapabilityEnum.TEXT.getCode(), true));
                        aiModelMapper.insert(new AiModel(provider.getId(), "claude-opus-4-20250514", ModelCapabilityEnum.TEXT.getCode(), true));
                        aiModelMapper.insert(new AiModel(provider.getId(), "claude-3-5-sonnet-20241022", ModelCapabilityEnum.TEXT.getCode(), true));
                        aiModelMapper.insert(new AiModel(provider.getId(), "claude-3-haiku-20240307", ModelCapabilityEnum.TEXT.getCode(), true));
                        break;
                    case "google":
                        aiModelMapper.insert(new AiModel(provider.getId(), "gemini-2.5-pro", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.IMAGE, ModelCapabilityEnum.AUDIO, ModelCapabilityEnum.VIDEO), true));
                        aiModelMapper.insert(new AiModel(provider.getId(), "gemini-2.5-flash", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.IMAGE, ModelCapabilityEnum.AUDIO, ModelCapabilityEnum.VIDEO), true));
                        aiModelMapper.insert(new AiModel(provider.getId(), "gemini-2.0-flash", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.IMAGE), true));
                        break;
                    case "deepseek":
                        aiModelMapper.insert(new AiModel(provider.getId(), "deepseek-chat", ModelCapabilityEnum.TEXT.getCode(), true));
                        aiModelMapper.insert(new AiModel(provider.getId(), "deepseek-reasoner", ModelCapabilityEnum.TEXT.getCode(), true));
                        break;
                    case "qwen":
                        aiModelMapper.insert(new AiModel(provider.getId(), "qwen-max", ModelCapabilityEnum.TEXT.getCode(), true));
                        aiModelMapper.insert(new AiModel(provider.getId(), "qwen-plus", ModelCapabilityEnum.TEXT.getCode(), true));
                        aiModelMapper.insert(new AiModel(provider.getId(), "qwen-turbo", ModelCapabilityEnum.TEXT.getCode(), true));
                        aiModelMapper.insert(new AiModel(provider.getId(), "qwen-vl-max", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.IMAGE), true));
                        aiModelMapper.insert(new AiModel(provider.getId(), "qwen-long", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.DOCUMENT), true));
                        break;
                    case "zhipu":
                        aiModelMapper.insert(new AiModel(provider.getId(), "glm-4-plus", ModelCapabilityEnum.TEXT.getCode(), true));
                        aiModelMapper.insert(new AiModel(provider.getId(), "glm-4", ModelCapabilityEnum.TEXT.getCode(), true));
                        aiModelMapper.insert(new AiModel(provider.getId(), "glm-4v", joinCapabilities(ModelCapabilityEnum.TEXT, ModelCapabilityEnum.IMAGE), true));
                        aiModelMapper.insert(new AiModel(provider.getId(), "glm-4-flash", ModelCapabilityEnum.TEXT.getCode(), true));
                        break;
                    case "moonshot":
                        aiModelMapper.insert(new AiModel(provider.getId(), "moonshot-v1-8k", ModelCapabilityEnum.TEXT.getCode(), true));
                        aiModelMapper.insert(new AiModel(provider.getId(), "moonshot-v1-32k", ModelCapabilityEnum.TEXT.getCode(), true));
                        aiModelMapper.insert(new AiModel(provider.getId(), "moonshot-v1-128k", ModelCapabilityEnum.TEXT.getCode(), true));
                        break;
                    case "minimax":
                        aiModelMapper.insert(new AiModel(provider.getId(), "MiniMax-M1", ModelCapabilityEnum.TEXT.getCode(), true));
                        aiModelMapper.insert(new AiModel(provider.getId(), "MiniMax-Text-01", ModelCapabilityEnum.TEXT.getCode(), true));
                        break;
                }
            }
        }

        List<Agent> agents = agentMapper.findAll();
        if (agents.isEmpty()) {
            String tools = allTools.stream().map(x -> x.getName()).collect(Collectors.joining(","));
            agentMapper.insert(new Agent("通用助手", "可以回答各种问题，使用多种工具", tools, 20, 20));

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
