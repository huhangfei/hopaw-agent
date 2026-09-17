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
import com.microsoft.playwright.options.ScreenshotType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 网页内容获取工具插件
 * 注意：作为插件使用时，不要加 @Component 注解，由插件加载器实例化
 * Playwright 采用异步初始化策略，在插件加载后立即后台初始化，避免首次调用时等待时间过长
 */
public class WebPagePlaywrightTool implements AgentTool {

    private final Logger logger = LoggerFactory.getLogger(WebPagePlaywrightTool.class);
    
    private volatile Playwright playwright;
    private volatile Browser browser;

    private volatile boolean initialized = false;
    private volatile boolean initializing = false;
    private volatile boolean initializationFailed = false;
    private volatile CompletableFuture<Void> initializationFuture=null;
    
    // 单线程池用于异步初始化
    private static final ExecutorService initExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "webpage-tool-playwright-init");
        t.setDaemon(true);
        return t;
    });

    /** 等待页面网络空闲的最长时间（毫秒），超时后按当前已渲染内容继续 */
    private static final double NETWORK_IDLE_TIMEOUT = 15000;

    @Override
    public String getName() {
        return "webPagePlaywright";
    }

    @Override
    public String getDescription() {
        return "使用Playwright获取网页内容，输入URL地址，返回网页的纯文本内容。适用于获取网页文章、文档等文本内容。";
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
                    // 跳过浏览器自动下载，避免Docker环境OOM被Kill
                    // 浏览器需预装：mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
                    java.util.Map<String, String> env = new java.util.HashMap<>();
                    env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
                    Playwright.CreateOptions createOptions = new Playwright.CreateOptions().setEnv(env);
                    playwright = Playwright.create(createOptions);
                    BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                            .setHeadless(true)
                            .setArgs(java.util.Arrays.asList(
                                    "--no-sandbox",
                                    "--disable-setuid-sandbox",
                                    "--disable-dev-shm-usage",
                                    "--disable-gpu",
                                    "--disable-extensions",
                                    "--disable-background-networking"
                            ));
                    browser = playwright.chromium().launch(launchOptions);
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
     * 确保 Playwright 已初始化（支持异步和同步两种模式）。
     * 初始化失败/被中断时抛出明确异常，避免后续 browser 为 null 产生难定位的 NPE
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
                Thread.currentThread().interrupt();
                throw new RuntimeException("Playwright initialization interrupted", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Playwright initialization failed", e.getCause());
            }
        }
        // 等待结束后仍未初始化成功（失败或未开始），统一抛出，防止 browser 为 null 时 NPE
        if (!initialized) {
            throw new RuntimeException("Playwright not initialized" + (initializationFailed ? " (initialization failed)" : ""));
        }
    }

    /**
     * 等待页面 JS 渲染完成：等待网络空闲（无网络请求持续 500ms）。
     * SPA 页面的内容通常在 load 事件之后由 XHR/fetch 异步加载，仅等 load 会拿到未渲染的骨架页。
     * 部分页面存在持续轮询/长连接请求，网络永远不会空闲，此时超时后按当前已渲染内容继续。
     */
    private void waitForJsRendering(Page page, String url) {
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(NETWORK_IDLE_TIMEOUT));
        } catch (PlaywrightException e) {
            logger.warn("等待网络空闲超时（页面可能存在持续轮询），按当前已渲染内容继续: url={}", url);
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"获取网页用Playwright", "获取网页内容，输入URL地址，返回网页的纯文本或HTML源文件"})
    public String fetchWebPageByPlaywright(@P(description = "URL地址") String url,
                                           @P(description = "返回文本最大长度，超出截断，默认5000", required = false) Integer maxLength,
                                           @P(description = "返回格式: text=纯文本(默认), html=HTML源文件", required = false) String format) {
        // 确保 Playwright 已初始化
        ensureInitialized();
        int maxLen = (maxLength != null && maxLength > 0) ? maxLength : 5000;
        String fmt = (format != null && !format.trim().isEmpty()) ? format.trim() : "text";

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

            waitForJsRendering(page, url);

            String html = page.content();

            if ("html".equalsIgnoreCase(fmt)) {
                return html.length() > maxLen ? html.substring(0, maxLen) + "..." : html;
            }
            // 纯文本：Jsoup.clean(Safelist.none()) 输出仍是 HTML 实体，需再 parse().text() 解码
            String text = Jsoup.parse(html).text();
            return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;

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

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"网页截图", "使用Playwright截取指定URL网页的整页图片并保存到本地文件。支持png/jpg/jpeg格式，按保存路径扩展名自动选择格式，自动创建父目录。", "网页,截图,截图保存,页面截图"})
    public String captureWebPageScreenshot(
            @P(description = "URL地址") String url,
            @P(description = "图片保存路径（本地文件路径，扩展名支持png/jpg/jpeg，自动创建父目录）") String savePath) {
        if (url == null || url.trim().isEmpty()) {
            return "错误：URL不能为空";
        }
        if (savePath == null || savePath.trim().isEmpty()) {
            return "错误：保存路径不能为空";
        }

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

            Response response = page.navigate(url.trim());
            if (response == null) {
                return "截图失败: 无法加载页面";
            }
            waitForJsRendering(page, url.trim());

            // 按保存路径扩展名选择格式
            String lower = savePath.trim().toLowerCase();
            boolean jpeg = lower.endsWith(".jpg") || lower.endsWith(".jpeg");
            Page.ScreenshotOptions options = new Page.ScreenshotOptions()
                    .setType(jpeg ? ScreenshotType.JPEG : ScreenshotType.PNG)
                    .setFullPage(true);
            if (jpeg) {
                options.setQuality(90);
            }
            byte[] imageBytes = page.screenshot(options);

            // 写入文件，自动创建父目录
            Path target = Paths.get(savePath.trim());
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(target, imageBytes);

            return "成功：网页截图已保存至 " + target.toAbsolutePath()
                    + "（" + (jpeg ? "JPEG" : "PNG") + "，"
                    + String.format("%.1fKB", imageBytes.length / 1024.0) + "）";
        } catch (Exception e) {
            logger.error("网页截图失败:url="+url+",savePath="+savePath, e);
            return "截图失败: " + e.getMessage();
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