package com.agent.hopaw.biz.tool.websearch;

import com.agent.hopaw.biz.util.QianfanWebSearchUtil;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import com.agent.hopaw.infra.tool.AgentTool;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Component("webSearch")
public class WebSearchTool implements AgentTool {

    private static final int TIMEOUT_MS = 10000;
    private static final int MAX_RESULTS = 5;

    private final QianfanWebSearchUtil qianfanWebSearchUtil;

    public WebSearchTool(QianfanWebSearchUtil qianfanWebSearchUtil) {
        this.qianfanWebSearchUtil = qianfanWebSearchUtil;
    }

    @Tool(value={"搜索查询互联网最新网络信息，返回相关的网页标题和摘要内容。",""})
    public String webSearch(@P(description = "搜索关键词") String query, @P(description = "搜索源（可选值有 baiduqianfan）默认 baiduqianfan", required = false) String source, @P(description = "最大结果数，默认5", required = false) Integer maxResults, @P(description = "超时时间（毫秒），默认10000毫秒", required = false) Integer timeout) {
        if (query == null || query.trim().isEmpty()) {
            return "错误: 搜索关键词不能为空";
        }

        source = source == null ? "baiduqianfan" : source;
        int mr = maxResults != null ? maxResults : MAX_RESULTS;
        int to = timeout != null ? timeout : TIMEOUT_MS;

        String result = null;
        try {
            if (source.equalsIgnoreCase("baiduqianfan")) {

                result = qianfanWebSearchUtil.search(query, mr, to);
                if (result != null) {
                    return result;
                }
            } else {
                return "错误: 不支持的搜索源 " + source;
            }

        } catch (Exception e) {
            return "搜索失败: 搜索源 " + source + " ，错误" + e.getMessage();
        }
        return "未找到相关结果，搜索源 " + source;
    }


    @Override
    public String getName() {
        return "webSearch";
    }

    @Override
    public String getDescription() {
        return "搜索网页信息，返回相关的网页标题和摘要内容";
    }

    @Override
    public String getIcon() {
        return "web-search-tool";
    }

    @Override
    public String getKeyword() {
        return "搜索";
    }
}
