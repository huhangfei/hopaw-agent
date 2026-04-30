package com.agent.hopaw.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service("webSearch")
public class WebSearchTool implements AgentTool {

    private static final int TIMEOUT_MS = 10000;
    private static final int MAX_RESULTS = 10;

    @Tool("搜索互联网网页信息，返回相关的网页标题和摘要内容。搜索源为百度或必应。")
    public String webSearch(@P(description = "搜索关键词") String query, @P(description = "搜索源（可选值有 baidu，bing）默认baidu",required = false) String source, @P(description = "最大结果数，默认10",required = false) Integer maxResults, @P(description = "超时时间（毫秒），默认10000毫秒",required = false) Integer timeout) {
        if (query == null || query.trim().isEmpty()) {
            return "错误: 搜索关键词不能为空";
        }
        source=source==null?"baidu":source;
        if(maxResults==null){
            maxResults=MAX_RESULTS;
        }
        if(timeout==null){
            timeout=TIMEOUT_MS;
        }
        try {
            String result = source.equalsIgnoreCase("bing") ?
                    searchBing(query, timeout, maxResults) :
                    searchBaidu(query, timeout, maxResults);
            return result==null?"未找到相关内容" : result.trim();
        } catch (Exception e) {
            return "搜索失败: " + e.getMessage();
        }
    }

    private String searchBing(String query, Integer timeout, Integer maxResults) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
            String url = "https://www.bing.com/search?q=" + encodedQuery + "&count="+maxResults;

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(timeout)
                    .get();
            String text = Jsoup.clean(doc.html(), Safelist.none());
            return text;

        } catch (IOException e) {
        }
        return null;
    }

    private String searchBaidu(String query, Integer timeout, Integer maxResults) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
            String url = "https://www.baidu.com/s?wd=" + encodedQuery + "&rn="+maxResults;

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(timeout)
                    .get();
            String text = Jsoup.clean(doc.html(), Safelist.none());
            return text;
        } catch (IOException e) {
        }
        return null;
    }

    @Override
    public String getName() {
        return "webSearch";
    }

    @Override
    public String getDescription() {
        return "搜索网页信息，返回相关的网页标题和摘要内容";
    }
}
