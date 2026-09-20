package com.agent.hopaw.infra.plugin;

import com.agent.hopaw.infra.tool.AgentTool;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link AgentPlugin} 便捷基类：收敛单工具/少工具插件的迁移样板。
 *
 * <p>典型用法（原"1 JAR 1 AgentTool"插件迁移后的形态）：</p>
 * <pre>{@code
 * public class SshPlugin extends AbstractAgentPlugin {
 *     private SshConnectionPool pool;
 *
 *     {@literal @Override} public String getId() { return "ssh"; }
 *     {@literal @Override} public String getDescription() { return "服务器远程操作插件"; }
 *
 *     {@literal @Override} public void asyncInit() {
 *         pool = new SshConnectionPool(...);   // 共享资源在插件级构建
 *     }
 *
 *     {@literal @Override} public List<AgentTool> getTools() {
 *         return tools(new SshTool(pool));     // 注入共享资源后分发
 *     }
 *
 *     {@literal @Override} public void destroy() {
 *         pool.close();
 *     }
 * }
 * }</pre>
 *
 * <p>纯前端组件插件只需继承本类并实现 id/description，{@link #getTools()} 返回空列表（默认行为）。</p>
 */
public abstract class AbstractAgentPlugin implements AgentPlugin {

    /**
     * 工具实例持有者：asyncInit 后由子类填充。
     */
    private List<AgentTool> tools = List.of();

    /**
     * 设置插件提供的工具实例（可多次调用追加）。
     */
    protected void setTools(List<AgentTool> tools) {
        this.tools = List.copyOf(tools);
    }

    /**
     * 设置插件提供的工具实例（varargs 便捷方法）。
     */
    protected void tools(AgentTool... toolInstances) {
        List<AgentTool> list = new ArrayList<>(toolInstances.length);
        for (AgentTool tool : toolInstances) {
            list.add(tool);
        }
        this.tools = List.copyOf(list);
    }

    @Override
    public List<AgentTool> getTools() {
        return tools;
    }
}
