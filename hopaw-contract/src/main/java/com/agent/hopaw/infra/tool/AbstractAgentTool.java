package com.agent.hopaw.infra.tool;

/**
 * {@link AgentTool} 便捷基类：承载「框架注入的宿主身份」（pluginId）。
 *
 * <p>{@link AgentTool#getConfigPrefix()} 需要知道工具属于哪个插件才能算出
 * {@code plugin.<pluginId>.tool.<工具集名>.} 前缀，而插件实例是由插件自己的
 * {@code getTools()} 创建的——因此由框架在工具 {@code asyncInit()} 之前回填 pluginId。
 * 存储位置就是本类，插件工具继承本类即可，无需自己持有该字段。</p>
 *
 * <p>典型用法（相对于直接实现接口，只差一行类声明）：</p>
 * <pre>{@code
 * public class SshTool extends AbstractAgentTool {
 *     @Override public String getName() { return "ssh"; }
 *     ...
 * }
 * }</pre>
 *
 * <p>内置工具（Spring Bean，无插件父节点）可直接实现 {@link AgentTool}，
 * {@link #getPluginId()} 保持 {@code null}，配置键退化为 {@code tool.<工具集名>.}。</p>
 */
public abstract class AbstractAgentTool implements AgentTool {

    /** 所属插件标识；由框架注入，未注入时为 null（内置工具）。 */
    private volatile String pluginId;

    @Override
    public String getPluginId() {
        return pluginId;
    }

    @Override
    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }
}
