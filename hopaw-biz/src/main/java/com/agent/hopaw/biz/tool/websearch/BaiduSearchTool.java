package com.agent.hopaw.biz.tool.websearch;

import com.agent.hopaw.biz.util.QianfanWebSearchUtil;
import com.agent.hopaw.infra.model.dto.ToolConfigItem;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import com.agent.hopaw.infra.tool.AgentTool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("baiduSearch")
public class BaiduSearchTool implements AgentTool {

    private static final int TIMEOUT_MS = 10000;
    private static final int MAX_RESULTS = 5;

    private final QianfanWebSearchUtil qianfanWebSearchUtil;

    public BaiduSearchTool(QianfanWebSearchUtil qianfanWebSearchUtil) {
        this.qianfanWebSearchUtil = qianfanWebSearchUtil;
    }

    @Tool(value={"搜索查询互联网最新网络信息，返回相关的网页标题和摘要内容。","新闻、军事、财经、时事、天气、资料"})
    public String baiduSearch(@P(description = "搜索关键词") String query, @P(description = "最大结果数，默认5", required = false) Integer maxResults, @P(description = "超时时间（毫秒），默认10000毫秒", required = false) Integer timeout) {
        if (query == null || query.trim().isEmpty()) {
            return "错误: 搜索关键词不能为空";
        }

        int mr = maxResults != null ? maxResults : MAX_RESULTS;
        int to = timeout != null ? timeout : TIMEOUT_MS;

        try {
            String result = qianfanWebSearchUtil.search(query, mr, to);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            return "搜索失败: " + e.getMessage();
        }
        return "未找到相关结果";
    }


    @Override
    public String getName() {
        return "baiduSearch";
    }

    @Override
    public String getDescription() {
        return "百度搜索网页信息，返回相关的网页标题和摘要内容";
    }

    @Override
    public String getIcon() {
        return "web-search-tool.svg";
    }

    @Override
    public String getKeyword() {
        return "搜索";
    }

    @Override
    public List<ToolConfigItem> getConfigItems() {
        return List.of(
                new ToolConfigItem("apiKeys", "API 密钥", "百度千帆 API 密钥，多个密钥用英文逗号分隔，将自动轮询使用", ToolConfigItem.ConfigType.TEXT_MULTI),
                new ToolConfigItem("edition", "搜索参数", "搜索版本：standard 完整版或 lite 轻量版", ToolConfigItem.ConfigType.SELECT, List.of("standard", "lite"))
        );
    }
}
