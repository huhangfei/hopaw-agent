package com.agent.hopaw.tool.chatbg;

import com.agent.hopaw.infra.plugin.AbstractAgentPlugin;

/**
 * 会话背景色插件（<b>纯前端插件示范</b>）。
 *
 * <p>本插件示范「零 AgentTool 的纯前端插件」这一合法形态：</p>
 * <ul>
 *   <li>{@link #getTools()} 返回空列表——不给 LLM 提供任何工具；</li>
 *   <li>能力全部来自 {@code plugin-assets.json}：css / html / js 三个资产，
 *       页面标识 {@code index}（会话页）；</li>
 *   <li>三个资产都声明 {@code sandbox: true}：宿主把它们整体装进一个
 *       {@code <iframe sandbox="allow-scripts">} 容器，脚本进不了宿主全局作用域，
 *       只能通过 {@code postMessage} 调用宿主白名单能力
 *       （{@code ui.resize} / {@code theme.setBackground} / {@code theme.getBackground}）。</li>
 * </ul>
 *
 * <p>因此本插件在「插件管理」页表现为：1 个插件、0 个工具集、3 个前端资产、不支持 invoke。
 * 没有 {@code @Tool} 方法，也不需要任何后端依赖。</p>
 */
public class ChatBackgroundPlugin extends AbstractAgentPlugin {

    /** 插件标识 */
    public static final String PLUGIN_ID = "chatBackground";

    @Override
    public String getId() {
        return PLUGIN_ID;
    }

    @Override
    public String getName() {
        return "会话背景色";
    }

    @Override
    public String getDescription() {
        return "纯前端插件示范：在会话页以沙箱面板提供背景色预设与自定义，设置结果由宿主持久化；"
                + "不提供任何工具集，仅注入前端资产";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getAuthor() {
        return "HoPaw 官方插件";
    }

    @Override
    public String getKeyword() {
        return "背景,背景色,主题,外观,配色,chat,background";
    }

    @Override
    public String getIcon() {
        return "chat-background.svg";
    }
}
