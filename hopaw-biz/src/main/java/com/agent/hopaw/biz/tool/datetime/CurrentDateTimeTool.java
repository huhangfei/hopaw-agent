package com.agent.hopaw.biz.tool.datetime;

import dev.langchain4j.agent.tool.Tool;
import com.agent.hopaw.infra.tool.AgentTool;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Component("currentDateTime")
public class CurrentDateTimeTool implements AgentTool {

    @Tool("获取当前日期和时间")
    public String getCurrentTime() {
        return java.time.LocalDateTime.now().toString();
    }

    @Override
    public String getName() {
        return "getCurrentTime";
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
