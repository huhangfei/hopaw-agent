package com.agent.hopaw.infra.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 插件 / 工具集图标解析器：把 JAR 内声明的图标文件名解析为**内联 SVG 代码**。
 *
 * <p>为什么必须内联：插件 JAR 不在应用静态资源目录下，浏览器 {@code /icons/tools/xxx.svg}
 * 只能拿到 hopaw-app 自带的图标，插件图标走 URL 必然 404。因此图标一律由后端从 JAR 读出
 * 内容后内联下发给前端（前端约定：{@code icon} 以 {@code <svg} 开头则直接 uthy 渲染）。</p>
 *
 * <p>目录约定：</p>
 * <ul>
 *   <li>插件图标：{@code static/icons/plugins/<文件名>}（回退 {@code static/icons/tools/}，兼容早期放在 tools 下的插件图标）</li>
 *   <li>工具集图标：{@code static/icons/tools/<文件名>}（回退 {@code static/icons/plugins/}，兼容"工具未声明图标 → 回退插件图标"的情形）</li>
 * </ul>
 */
public final class PluginIconResolver {

    private static final Logger log = LoggerFactory.getLogger(PluginIconResolver.class);

    /** 插件图标目录 */
    public static final String PLUGIN_ICON_DIR = "static/icons/plugins/";
    /** 工具集图标目录 */
    public static final String TOOL_ICON_DIR = "static/icons/tools/";

    private PluginIconResolver() {
    }

    /**
     * 解析插件图标：优先 {@code static/icons/plugins/}，回退 {@code static/icons/tools/}。
     *
     * @return 内联 SVG；图标未声明或 JAR 内找不到时返回 {@code ""}（前端据此渲染兜底占位图标）
     */
    public static String resolvePluginIcon(PluginRegistry.PluginEntry entry, String icon) {
        return resolve(entry, icon, true, PLUGIN_ICON_DIR, TOOL_ICON_DIR);
    }

    /**
     * 解析工具集图标：优先 {@code static/icons/tools/}，回退 {@code static/icons/plugins/}。
     *
     * @return 内联 SVG；找不到时原样返回传入值（保持"前端按文件名请求"的既有行为，不引入回归）
     */
    public static String resolveToolIcon(PluginRegistry.PluginEntry entry, String icon) {
        return resolve(entry, icon, false, TOOL_ICON_DIR, PLUGIN_ICON_DIR);
    }

    /**
     * @param emptyWhenMissing true 表示解析失败返回空串（触发前端兜底图标），
     *                         false 表示解析失败原样返回文件名
     */
    private static String resolve(PluginRegistry.PluginEntry entry, String icon,
                                  boolean emptyWhenMissing, String... dirs) {
        if (icon == null || icon.isBlank()) {
            return emptyWhenMissing ? "" : icon;
        }
        // 已经是 svg 代码片段，无需解析
        if (icon.startsWith("<svg")) {
            return icon;
        }
        if (entry == null) {
            return emptyWhenMissing ? "" : icon;
        }
        for (String dir : dirs) {
            try {
                String content = entry.getCachedResource(dir + icon);
                if (content != null && !content.isEmpty()) {
                    return content;
                }
            } catch (Exception e) {
                log.warn("Failed to resolve icon [{}] from {} of plugin [{}]: {}",
                        icon, dir, entry.getPluginId(), e.getMessage());
            }
        }
        log.debug("Icon [{}] not found in plugin [{}], dirs={}", icon, entry.getPluginId(), dirs);
        return emptyWhenMissing ? "" : icon;
    }
}
