package com.agent.hopaw.infra.plugin;

import java.util.List;

/**
 * 插件前端资源声明（对应 JAR 根目录 plugin-assets.json 中 assets 数组的一项）。
 *
 * <p>一个资源可以是 js / css / html，声明其注入到哪些页面（{@link #matches}），
 * 由 PluginAssetController 暴露、前端 plugin-loader.js 按序注入。</p>
 */
public class PluginAsset {

    /** 资源唯一 id，前端用于去重/卸载标记。 */
    private final String id;

    /** 所属插件（JAR 文件名）。 */
    private final String plugin;

    /** 资源类型：js / css / html。 */
    private final String type;

    /** JAR 内路径，必须 static/ 前缀。 */
    private final String path;

    /** 匹配的页面标识（复用 activePage），支持 "*" 通配与 "page/" 前缀匹配。 */
    private final List<String> pages;

    /** js 注入位置：head / body-start / body-end（默认 body-end）。 */
    private final String position;

    /** js 是否 defer。 */
    private final boolean defer;

    /** 加载优先级，数值小者先加载，默认 1000。 */
    private final int priority;

    /** html 挂载选择器。 */
    private final String mount;

    /** html 挂载模式：append / replace / prepend（默认 append）。 */
    private final String mode;

    /** 缓存失效版本号（取 JAR lastModified 的 hex）。 */
    private final String version;

    public PluginAsset(String id, String plugin, String type, String path, List<String> pages,
                       String position, boolean defer, int priority, String mount, String mode, String version) {
        this.id = id;
        this.plugin = plugin;
        this.type = type;
        this.path = path;
        this.pages = pages;
        this.position = position;
        this.defer = defer;
        this.priority = priority;
        this.mount = mount;
        this.mode = mode;
        this.version = version;
    }

    /**
     * 判断该资源是否匹配给定页面标识。
     * <ul>
     *   <li>"*" 命中所有页面；</li>
     *   <li>精确相等命中；</li>
     *   <li>前缀 "page/" 命中子页面（如 "chat" 命中 "chat/xxx"）。</li>
     * </ul>
     */
    public boolean matches(String page) {
        if (pages == null || pages.isEmpty()) {
            return false;
        }
        if (page == null) {
            page = "";
        }
        for (String p : pages) {
            if (p == null) {
                continue;
            }
            if ("*".equals(p) || p.equals(page) || page.startsWith(p + "/")) {
                return true;
            }
        }
        return false;
    }

    public String getId() {
        return id;
    }

    public String getPlugin() {
        return plugin;
    }

    public String getType() {
        return type;
    }

    public String getPath() {
        return path;
    }

    public List<String> getPages() {
        return pages;
    }

    public String getPosition() {
        return position;
    }

    public boolean isDefer() {
        return defer;
    }

    public int getPriority() {
        return priority;
    }

    public String getMount() {
        return mount;
    }

    public String getMode() {
        return mode;
    }

    public String getVersion() {
        return version;
    }
}
