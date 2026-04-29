package com.agent.hopaw.config;

import com.agent.hopaw.mapper.AgentMapper;
import com.agent.hopaw.mapper.ChatHistoryMapper;
import com.agent.hopaw.model.Agent;
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
    private final ChatHistoryMapper chatHistoryMapper;
    private final DataSource dataSource;
    private final List<AgentTool> allTools;
    public DataInitializer(AgentMapper agentMapper, ChatHistoryMapper chatHistoryMapper, DataSource dataSource, List<AgentTool> allTools) {
        this.agentMapper = agentMapper;
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
                    "max_memory_records INTEGER DEFAULT 20" +
                    ")");
            
            try {
                stmt.execute("ALTER TABLE agents ADD COLUMN max_memory_records INTEGER DEFAULT 20");
            } catch (Exception e) {
            }

            try {
                stmt.execute("ALTER TABLE agents ADD COLUMN max_tool_invocations INTEGER DEFAULT 20");
            } catch (Exception e) {
            }
            
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
            
            try {
                stmt.execute("ALTER TABLE chat_history ADD COLUMN message_type TEXT NOT NULL DEFAULT 'text'");
            } catch (Exception e) {
            }
            try {
                stmt.execute("ALTER TABLE chat_history ADD COLUMN tool_call_id TEXT");
            } catch (Exception e) {
            }
            try {
                stmt.execute("ALTER TABLE chat_history ADD COLUMN tool_arguments TEXT");
            } catch (Exception e) {
            }
            try {
                stmt.execute("ALTER TABLE chat_history ADD COLUMN tool_call_status TEXT");
            } catch (Exception e) {
            }
            try {
                stmt.execute("ALTER TABLE chat_history ADD COLUMN thinking_content TEXT");
            } catch (Exception e) {
            }

            
            stmt.execute("CREATE TABLE IF NOT EXISTS chat_memory (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "agent_id INTEGER NOT NULL, " +
                    "message_id TEXT NOT NULL, " +
                    "message_json TEXT NOT NULL, " +
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

            try {
                stmt.execute("ALTER TABLE long_term_memory ADD COLUMN memory_hash TEXT");
            } catch (Exception e) {
            }

            stmt.execute("CREATE TABLE IF NOT EXISTS memory_process_log (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "identity TEXT NOT NULL UNIQUE, " +
                    "last_processed_chat_id INTEGER DEFAULT 0, " +
                    "last_processed_time TIMESTAMP" +
                    ")");

            try {
                stmt.execute("ALTER TABLE memory_process_log ADD COLUMN last_processed_chat_id INTEGER DEFAULT 0");
            } catch (Exception e) {
            }
            try {
                stmt.execute("ALTER TABLE memory_process_log ADD COLUMN last_processed_time TIMESTAMP");
            } catch (Exception e) {
            }
        }

        List<Agent> agents = agentMapper.findAll();
        if (agents.isEmpty()) {
            String tools = allTools.stream().map(x -> x.getName()).collect(Collectors.joining(","));
            agentMapper.insert(new Agent("通用助手", "可以回答各种问题，使用多种工具", tools, 20, 20));

        }
    }
}
