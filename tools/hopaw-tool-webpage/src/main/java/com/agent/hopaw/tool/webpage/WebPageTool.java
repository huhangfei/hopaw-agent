package com.agent.hopaw.tool.webpage;

import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import dev.langchain4j.agent.tool.P;
import com.agent.hopaw.infra.tool.AgentTool;
import dev.langchain4j.agent.tool.Tool;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.htmlunit.BrowserVersion;
import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlPage;

/**
 * 网页内容获取工具插件
 * 注意：作为插件使用时，不要加 @Component 注解，由插件加载器实例化
 * 基于 HtmlUnit（GUI-Less 浏览器）实现，无需下载浏览器，支持 JS 渲染，
 * 相比 Playwright 轻量得多，启动即用。
 */
public class WebPageTool implements AgentTool {

    private final Logger logger = LoggerFactory.getLogger(WebPageTool.class);

    /** 默认用户代理，模拟 Chrome 桌面浏览器 */
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    /** 等待后台 JS 渲染的最长时间（毫秒） */
    private static final int WAIT_JS_TIMEOUT = 3000;

    /** 页面加载超时（毫秒） */
    private static final int PAGE_TIMEOUT = 30000;

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
     * 插件实例化后调用。HtmlUnit 无需下载浏览器、初始化极快，这里不做异步预初始化，
     * 每次调用时创建独立的 WebClient。
     */
    @Override
    public void asyncInit() {
        // 无需异步初始化
    }

    /**
     * 插件卸载时清理资源（每次抓取使用独立 WebClient 并在 finally 中关闭，无共享资源需要清理）
     */
    @Override
    public void destroy() {
        // 无共享资源
    }

    /**
     * 创建 WebClient 实例。
     * 注意：HtmlUnit 的 WebClient 不是线程安全的（JS 引擎、任务队列、窗口集均绑定实例），
     * 且并发调用会互相干扰，因此每次抓取创建独立实例，用完即关（client.close 会停掉所有 JS 任务并释放窗口）。
     *
     * @param javaScriptEnabled 是否启用 JS 执行
     */
    private WebClient createWebClient(boolean javaScriptEnabled) {
        WebClient client = new WebClient(BrowserVersion.CHROME);
        client.getOptions().setJavaScriptEnabled(javaScriptEnabled);
        client.getOptions().setCssEnabled(false);
        client.getOptions().setThrowExceptionOnScriptError(false);
        client.getOptions().setThrowExceptionOnFailingStatusCode(false);
        client.getOptions().setTimeout(PAGE_TIMEOUT);
        client.getOptions().setDownloadImages(false);
        client.getOptions().setGeolocationEnabled(false);
        client.getOptions().setDoNotTrackEnabled(true);
        // 容忍企业网关（Zscaler/McAfee 等）对 TLS 的透明拦截重写导致的主机名校验失败
        client.getOptions().setUseInsecureSSL(true);
        client.addRequestHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        client.addRequestHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        client.addRequestHeader("Upgrade-Insecure-Requests", "1");
        return client;
    }

    /**
     * 静默关闭 WebClient（清理失败仅记录日志，不影响调用方）
     */
    private void closeClientQuietly(WebClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (Throwable e) {
                logger.warn("关闭 WebClient 失败（忽略）: {}", e.getMessage());
            }
        }
    }

    /**
     * 判断异常链中是否包含 Rhino JS 字节码编译超限异常（ClassSizeException/ClassFileFormatException）
     * 或因类加载失败产生的 NoClassDefFoundError（消息中含异常类名）
     */
    private static boolean isClassSizeError(Throwable t) {
        while (t != null) {
            String name = t.getClass().getName();
            if (name.contains("ClassSizeException") || name.contains("ClassFileFormatException")) {
                return true;
            }
            // NoClassDefFoundError 是 Error 类型，且异常名在消息里（斜杠格式）
            if (t instanceof NoClassDefFoundError && t.getMessage() != null
                    && (t.getMessage().contains("ClassSizeException") || t.getMessage().contains("ClassFileFormatException"))) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    /**
     * 提取页面内容
     * @param format text=纯文本, html=源文件
     */
    private String extractContent(HtmlPage page, int maxLength, String format) {
        String html = page.asXml();
        if ("html".equalsIgnoreCase(format)) {
            return html.length() > maxLength ? html.substring(0, maxLength) + "..." : html;
        }
        // 纯文本：Jsoup.clean(Safelist.none()) 输出仍是 HTML 实体，需再 parse().text() 解码
        String text = Jsoup.parse(html).text();
        if (text.isEmpty()) {
            return "获取网页失败: 页面没有可提取的文本内容";
        }
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(name = "webPage_fetch", value = {"获取网页", "获取网页内容，输入URL地址，返回网页的纯文本或HTML源文件"})
    public String fetchWebPage(@P(description = "URL地址") String url,
                               @P(description = "返回文本最大长度，超出截断，默认5000", required = false) Integer maxLength,
                               @P(description = "返回格式: text=纯文本(默认), html=HTML源文件", required = false) String format) {
        if (url == null || url.trim().isEmpty()) {
            return "获取网页失败: URL不能为空";
        }
        int maxLen = (maxLength != null && maxLength > 0) ? maxLength : 5000;
        String fmt = (format != null && !format.trim().isEmpty()) ? format.trim() : "text";
        String target = url.trim();

        // 首选启用 JS 渲染抓取（独立 WebClient，支持并发）
        WebClient client = createWebClient(true);
        try {
            HtmlPage page = client.getPage(target);
            // 等待页面中的异步 JS 渲染完成（如前端框架动态加载内容）
            client.waitForBackgroundJavaScript(WAIT_JS_TIMEOUT);
            return extractContent(page, maxLen, fmt);
        } catch (Throwable e) {
            // JS 引擎编译超限（如页面内存在超大 JS 方法）或相关类加载失败时，降级为无 JS 模式重新抓取；
            // 注意 NoClassDefFoundError 等 Error 不是 Exception，必须 catch Throwable 才能兜住
            if (isClassSizeError(e)) {
                logger.warn("JS引擎编译异常，降级为无JS模式抓取: url={}, cause={}", target, e.getMessage());
                return fetchWithoutJs(target, maxLen, fmt);
            }
            logger.error("获取网页失败:url=" + target, e);
            return "获取网页失败: " + e.getMessage();
        } finally {
            // close 会停掉页面遗留的 JS 定时任务并释放全部窗口资源
            closeClientQuietly(client);
        }
    }

    /**
     * 无 JS 模式抓取（兜底：至少能提取静态 HTML 文本；独立 WebClient，支持并发）
     */
    private String fetchWithoutJs(String url, int maxLength, String format) {
        WebClient client = createWebClient(false);
        try {
            HtmlPage page = client.getPage(url);
            return extractContent(page, maxLength, format);
        } catch (Throwable e) {
            logger.error("无JS模式获取网页失败:url=" + url, e);
            return "获取网页失败: " + e.getMessage();
        } finally {
            closeClientQuietly(client);
        }
    }
}
