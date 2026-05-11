package com.agent.hopaw.tools;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Service;

@Service("currentDateTime")
public class CurrentDateTimeTool implements AgentTool {

    @Tool("获取当前日期和时间")
    public String currentDateTime() {
        return java.time.LocalDateTime.now().toString();
    }

    @Override
    public String getName() {
        return "currentDateTime";
    }

    @Override
    public String getDescription() {
        return "获取当前日期和时间";
    }

    @Override
    public String getIcon() {
        return "current-datetime-tool";
    }

    @Override
    public String getKeyword() {
        return "时间";
    }
}
