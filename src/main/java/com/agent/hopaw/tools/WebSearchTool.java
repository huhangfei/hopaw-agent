package com.agent.hopaw.tools;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Service;

@Service("webSearch")
public class WebSearchTool implements AgentTool {

    @Tool("搜索网页信息")
    public String search(String query) {
        return "搜索结果: \n1. 关于\"" + query + "\"的信息1\n2. 关于\"" + query + "\"的信息2\n3. 关于\"" + query + "\"的信息3";
    }

    @Override
    public String getName() {
        return "webSearch";
    }

    @Override
    public String getDescription() {
        return "搜索网页信息";
    }
}
