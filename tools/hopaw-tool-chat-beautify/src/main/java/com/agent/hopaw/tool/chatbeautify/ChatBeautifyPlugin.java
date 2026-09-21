package com.agent.hopaw.tool.chatbeautify;

import com.agent.hopaw.infra.plugin.AbstractAgentPlugin;

/**
 * 会话美化插件（<b>纯前端插件</b>）。
 *
 * <p>本插件示范「零 AgentTool 的纯前端插件」这一合法形态：</p>
 * <ul>
 *   <li>{@link #getTools()} 返回空列表——不给 LLM 提供任何工具；</li>
 *   <li>能力全部来自 {@code plugin-assets.json}：css / html / js 三个资产，
 *       页面标识 {@code index}（会话页）；</li>
 *   <li>三个资产为<b>非沙箱</b>资产，直接注入宿主页面（不做 iframe），
 *       脚本运行在宿主全局作用域，可直接操作会话页 DOM 与 localStorage。</li>
 * </ul>
 *
 * <p>能力：在会话页头部「更多」按钮前插入一个「会话美化」按钮，点击向下弹出设置面板，
 * 支持设置会话区背景色 / 背景图（背景图可调透明度），气泡背景透明度（agent 回合大盒子与用户气泡，
 * 文字深浅随透明度自适应），
 * 以及拖拽调节会话消息字体大小（思考 / 普通消息 / 工具按钮），
 * 全部设置由本插件自行持久化到 localStorage 并在页面加载时还原。</p>
 */
public class ChatBeautifyPlugin extends AbstractAgentPlugin {

    /** 插件标识 */
    public static final String PLUGIN_ID = "chatBeautify";

    @Override
    public String getId() {
        return PLUGIN_ID;
    }

    @Override
    public String getName() {
        return "会话美化";
    }

    @Override
    public String getDescription() {
        return "纯前端插件：在会话页头部提供「会话美化」按钮，可设置会话区背景色、背景图（含透明度），"
                + "调节气泡背景透明度（agent 回合与用户气泡），并拖拽调节会话消息字体大小（思考 / 普通 / 工具按钮）；"
                + "设置结果本地持久化，不提供任何工具集，仅注入前端资产";
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
        return "美化,背景,背景色,背景图,透明度,透明,字体,字号,外观,主题,chat,background,opacity";
    }

    @Override
    public String getIcon() {
        return "chat-beautify.svg";
    }
}
