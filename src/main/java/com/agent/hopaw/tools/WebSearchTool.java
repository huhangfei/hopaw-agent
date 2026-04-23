package com.agent.hopaw.tools;

import dev.langchain4j.agent.tool.Tool;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
    public String search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "错误: 搜索关键词不能为空";
        }

        try {
            List<SearchResult> results = searchBing(query);

            if (results.isEmpty()) {
                results = searchBaidu(query);
            }

            if (results.isEmpty()) {
                return "未找到关于\"" + query + "\"的搜索结果";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("搜索结果 (").append(results.size()).append(" 条):\n\n");

            int count = 0;
            for (SearchResult result : results) {
                if (count >= MAX_RESULTS) {
                    break;
                }
                sb.append("[").append(count + 1).append("] ").append(result.title).append("\n");
                sb.append("链接: ").append(result.url).append("\n");
                if (result.snippet != null && !result.snippet.isEmpty()) {
                    sb.append("摘要: ").append(result.snippet).append("\n");
                }
                sb.append("\n");
                count++;
            }

            return sb.toString().trim();

        } catch (Exception e) {
            return "搜索失败: " + e.getMessage();
        }
    }

    private List<SearchResult> searchBing(String query) {
        List<SearchResult> results = new ArrayList<>();
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
            String url = "https://www.bing.com/search?q=" + encodedQuery + "&count=10";

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(TIMEOUT_MS)
                    .get();

            Elements searchResults = doc.select("#b_results .b_algo");

            for (Element result : searchResults) {
                Element titleElement = result.selectFirst("h2 a");
                Element snippetElement = result.selectFirst(".b_caption p, .b_algoSlug");

                if (titleElement != null) {
                    SearchResult sr = new SearchResult();
                    sr.title = titleElement.text();
                    sr.url = titleElement.absUrl("href");
                    if (snippetElement != null) {
                        sr.snippet = Jsoup.parse(snippetElement.text()).text();
                    }
                    results.add(sr);
                }
            }
        } catch (IOException e) {
        }
        return results;
    }

    private List<SearchResult> searchBaidu(String query) {
        List<SearchResult> results = new ArrayList<>();
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
            String url = "https://www.baidu.com/s?wd=" + encodedQuery + "&rn=10";

            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(TIMEOUT_MS)
                    .get();

            Elements searchResults = doc.select(".result.c-container");

            for (Element result : searchResults) {
                Element titleElement = result.selectFirst("h3 a, .t a");
                Element snippetElement = result.selectFirst(".c-abstract, .content-right_8Zs40");

                if (titleElement != null) {
                    SearchResult sr = new SearchResult();
                    sr.title = titleElement.text();
                    sr.url = titleElement.absUrl("href");
                    if (snippetElement != null) {
                        sr.snippet = Jsoup.parse(snippetElement.text()).text();
                    }
                    results.add(sr);
                }
            }
        } catch (IOException e) {
        }
        return results;
    }

    private static class SearchResult {
        String title;
        String url;
        String snippet;
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
