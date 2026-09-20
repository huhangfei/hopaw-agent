package com.agent.hopaw.infra.tool.datetime;

import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import dev.langchain4j.agent.tool.Tool;
import com.agent.hopaw.infra.tool.AgentTool;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Component("currentDateTime")
public class CurrentDateTimeTool implements AgentTool {

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(name = "currentDateTime_getCurrentTime", value = {"获取当前时间", "获取当前日期和时间"})
    public String getCurrentTime() {
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
        return "current-datetime-tool.svg";
    }

    @Override
    public String getKeyword() {
        return "时间";
    }
}
