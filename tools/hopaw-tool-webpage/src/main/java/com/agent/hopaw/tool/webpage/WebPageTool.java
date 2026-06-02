package com.agent.hopaw.tool.webpage;

import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import dev.langchain4j.agent.tool.P;
import com.agent.hopaw.infra.tool.AgentTool;
import dev.langchain4j.agent.tool.Tool;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;

import javax.annotation.PostConstruct;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 网页内容获取工具插件
 * 注意：作为插件使用时，不要加 @Component 注解，由插件加载器实例化
 * Playwright 采用异步初始化策略，在插件加载后立即后台初始化，避免首次调用时等待时间过长
 */
public class WebPageTool implements AgentTool {

    private final Logger logger = LoggerFactory.getLogger(WebPageTool.class);
    
    private volatile Playwright playwright;
    private volatile Browser browser;

    private volatile boolean initialized = false;
    private volatile boolean initializing = false;
    private volatile boolean initializationFailed = false;
    private volatile CompletableFuture<Void> initializationFuture=null;
    
    // 单线程池用于异步初始化
    private static final ExecutorService initExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "webpage-tool-init");
        t.setDaemon(true);
        return t;
    });

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
        return "web-page-tool.svg";
    }

    @Override
    public String getKeyword() {
        return "网页,url";
    }

    /**
     * 插件实例化后立即启动异步初始化
     */
    @Override
    public void asyncInit() {
        if (!initializing && !initialized) {
            initializing = true;
            initializationFuture= CompletableFuture.runAsync(() -> {
                ClassLoader originalCl = Thread.currentThread().getContextClassLoader();
                try {
                    Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
                    logger.info("Initializing Playwright...");
                    playwright = Playwright.create();
                    browser = playwright.chromium().launch();
                    initialized = true;
                    logger.info("WebPageTool Playwright initialized successfully");
                } catch (Exception e) {
                    logger.error("Failed to initialize Playwright", e);
                    initializationFailed = true;
                    throw new RuntimeException("Playwright initialization failed", e);
                } finally {
                    initializing = false;
                    Thread.currentThread().setContextClassLoader(originalCl);
                }
            }, initExecutor);
            logger.info("WebPageTool async initialization started");
        }
    }

    /**
     * 插件卸载时清理资源
     */
    @Override
    public void destroy() {
        try {
            if (browser != null) {
                browser.close();
                logger.info("WebPageTool browser closed");
            }
            if (playwright != null) {
                playwright.close();
                logger.info("WebPageTool playwright closed");
            }
        } catch (Exception e) {
            logger.error("Error closing WebPageTool resources", e);
        }
    }


    /**
     * 确保 Playwright 已初始化（支持异步和同步两种模式）
     */
    private void ensureInitialized() {
        // 如果已经初始化完成，直接返回
        if (initialized) {
            return;
        }
        // 如果初始化失败，抛出异常
        if (initializationFailed) {
            throw new RuntimeException("Playwright initialization failed");
        }
        // 如果正在异步初始化中，等待完成
        if (initializing) {
            logger.debug("Playwright is initializing, waiting...");
            try {
                if (initializationFuture != null){ initializationFuture.get();}
            } catch (InterruptedException e) {
                logger.error("Playwright initialization interrupted", e);
            } catch (ExecutionException e) {
                logger.error("Playwright initialization failed", e);
            }
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool("获取网页内容，输入URL地址，返回网页的纯文本内容")
    public String fetchWebPage(@P(description = "URL地址") String url) {
        // 确保 Playwright 已初始化
        ensureInitialized();
        
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

            page.waitForLoadState(LoadState.LOAD);

            String html = page.content();

            String text = Jsoup.clean(html, Safelist.none());
            text = Jsoup.parse(text).text();

            return text.length() > 50000 ? text.substring(0, 50000) + "..." : text;

        } catch (Exception e) {
            logger.error("获取网页失败:url="+url, e);
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