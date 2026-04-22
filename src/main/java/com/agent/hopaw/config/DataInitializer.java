package com.agent.hopaw.config;

import com.agent.hopaw.mapper.AgentMapper;
import com.agent.hopaw.mapper.ChatHistoryMapper;
import com.agent.hopaw.model.Agent;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final AgentMapper agentMapper;
    private final ChatHistoryMapper chatHistoryMapper;
    private final DataSource dataSource;

    public DataInitializer(AgentMapper agentMapper, ChatHistoryMapper chatHistoryMapper, DataSource dataSource) {
        this.agentMapper = agentMapper;
        this.chatHistoryMapper = chatHistoryMapper;
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS agents (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL, " +
                    "description TEXT, " +
                    "tools TEXT" +
                    ")");
            
            stmt.execute("CREATE TABLE IF NOT EXISTS chat_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "agent_id INTEGER NOT NULL, " +
                    "role TEXT NOT NULL, " +
                    "content TEXT NOT NULL, " +
                    "create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");
        }

        List<Agent> agents = agentMapper.findAll();
        if (agents.isEmpty()) {
            agentMapper.insert(new Agent("通用助手", "可以回答各种问题，使用多种工具", "calculator,weather,search"));
            agentMapper.insert(new Agent("数学助手", "专门解决数学问题", "calculator"));
            agentMapper.insert(new Agent("天气助手", "专门提供天气信息", "weather"));
            agentMapper.insert(new Agent("搜索助手", "专门进行网页搜索", "search"));
        }
    }
}
