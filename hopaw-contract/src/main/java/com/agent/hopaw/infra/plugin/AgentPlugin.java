package com.agent.hopaw.infra.plugin;

import com.agent.hopaw.infra.model.dto.ToolConfigItem;
import com.agent.hopaw.infra.tool.AgentTool;

import java.util.List;
import java.util.Map;

/**
 * 插件主体接口：一个插件 JAR 对应一个 AgentPlugin 实例。
 *
 * <p>插件是工具的工厂与宿主：插件在 {@link #asyncInit()} 中构建共享资源（连接池、客户端等），
 * 再通过 {@link #getTools()} 向框架分发工具实例（可在构造时注入共享资源）。</p>
 *
 * <p>加载顺序：newInstance → autowireBean(插件) → 插件.asyncInit() → 插件.getTools()
 * → 逐工具 autowireBean → 注册；销毁顺序：插件.destroy()（插件负责释放自己创建并分发给工具的全部资源）→ 关闭 classloader。</p>
 *
 * <p>一个插件可提供 0..N 个 {@link AgentTool}：纯前端组件插件、纯 invoke 服务插件都是合法形态。</p>
 *
 * @see AbstractAgentPlugin
 */
public interface AgentPlugin {

    /**
     * 插件唯一标识（pluginId），全局唯一，作为注册表主键。
     * 建议使用小写字母与中划线，如 {@code database}、{@code ssh}。
     */
    String getId();

    /**
     * 插件显示名称，默认同 id。
     */
    default String getName() {
        return getId();
    }

    /**
     * 插件描述：用途、提供的能力说明。
     */
    String getDescription();

    default String getVersion() {
        return "1.0.0";
    }

    default String getAuthor() {
        return "Agent Plugin";
    }

    default String getUrl() {
        return "https://gitee.com/hgflydream/hopaw-agent";
    }

    /**
     * 插件图标：jar 内 static/icons/plugins/ 下的文件名，或 svg 代码片段。
     */
    default String getIcon() {
        return "plugin.svg";
    }

    /**
     * 声明关键字，便于匹配检索。
     */
    default String getKeyword() {
        return "";
    }

    /**
     * 异步初始化：构建插件级共享资源（连接池、HttpClient 等）。
     * 在 {@link #getTools()} 之前调用，工具实例可在此之后创建并持有共享资源。
     */
    default void asyncInit() {
    }

    /**
     * 插件卸载时清理资源：插件负责释放自己创建并分发给各工具的全部资源。
     */
    default void destroy() {
    }

    /**
     * 声明插件级配置项（前缀由框架统一计算为 plugin.&lt;id&gt;.），用于多工具共享的公共配置。
     *
     * <p>本插件各工具集的配置项也挂在该前缀之下（{@code plugin.&lt;id&gt;.tool.&lt;工具集名&gt;.&lt;key&gt;}），
     * 因此卸载插件时可按 {@code plugin.&lt;id&gt;.} 一个前缀扫清全部配置。</p>
     */
    default List<ToolConfigItem> getConfigItems() {
        return List.of();
    }

    /**
     * 插件级配置变更通知：插件配置保存后触发，方便重建内部对象。
     */
    default void onConfigChanged() {
    }

    /**
     * 提供的工具实例（0..N）。在 {@link #asyncInit()} 之后调用。
     * 工具实例由插件自行创建，可注入插件持有的共享资源；框架会对其补充 autowireBean。
     */
    List<AgentTool> getTools();

    /**
     * 插件通用调用入口：客户端可通过统一 API（POST /api/plugins/{pluginId}/invoke）调用插件，
     * 向前端插件发送数据或从插件获取数据。
     *
     * @param toolRef 多工具插件的路由键（工具集名），可为空，由插件自行决定默认行为
     * @param params 客户端传入的参数（JSON 对象反序列化为 Map，可能为空）
     * @return 返回给客户端的结果（序列化为 JSON 对象返回）
     */
    default Map<String, Object> invoke(String toolRef, Map<String, Object> params) {
        throw new UnsupportedOperationException("插件 " + getId() + " 不支持 invoke 调用");
    }
}
