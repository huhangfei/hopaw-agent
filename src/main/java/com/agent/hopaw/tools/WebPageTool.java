package com.agent.hopaw.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;

@Component
public class WebPageTool implements AgentTool {

    private static final Playwright playwright = Playwright.create();
    private static final Browser browser;

    static {
        browser = playwright.chromium().launch();
    }

    @Override
    public String getName() {
        return "webPage";
    }

    @Override
    public String getDescription() {
        return "获取网页内容，输入URL地址，返回网页的纯文本内容。适用于获取网页文章、文档等文本内容。";
    }

    @Override
    public String getIcon() {
        return "web-page-tool";
    }

    @Tool("获取网页内容，输入URL地址，返回网页的纯文本内容")
    public String fetchWebPage(@P(description = "URL地址") String url) {
        BrowserContext context = null;
        Page page = null;
        try {
            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .setViewportSize(1920, 1080)
                    .setLocale("zh-CN")
                    .setTimezoneId("Asia/Shanghai")
                    .setExtraHTTPHeaders(new java.util.HashMap<String, String>() {{
                        put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
                        put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
                        put("Accept-Encoding", "gzip, deflate, br");
                        put("Connection", "keep-alive");
                        put("Upgrade-Insecure-Requests", "1");
                    }});

            context = browser.newContext(contextOptions);
            page = context.newPage();

            Response response = page.navigate(url);

            if (response == null) {
                return "获取网页失败: 无法加载页面";
            }

            page.waitForLoadState(LoadState.NETWORKIDLE);

            String html = page.content();

            String text = Jsoup.clean(html, Safelist.none());
            text = Jsoup.parse(text).text();

            return text.length() > 50000 ? text.substring(0, 50000) + "..." : text;

        } catch (Exception e) {
            return "获取网页失败: " + e.getMessage();
        } finally {
            if (page != null) {
                page.close();
            }
            if (context != null) {
                context.close();
            }
        }
    }
}