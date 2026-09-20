package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.constant.AgentToolSourceEnum;
import com.agent.hopaw.infra.event.ConfigChangeEvent;
import com.agent.hopaw.infra.model.dto.ToolInfo;
import com.agent.hopaw.infra.model.dto.ToolParamInfo;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.plugin.AgentPlugin;
import com.agent.hopaw.infra.plugin.PluginRegistry;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工具集管理（二级）服务实现：扫描工具集元数据、按插件分组组装、分发工具级配置变更。
 *
 * <p>内置工具（Spring Bean）与插件工具都实现 {@link AgentTool}；内置工具没有插件父节点，
 * 版本/作者/来源使用默认值，图标与关键字取自工具自身。禁用插件提供的工具集不进入结果集。</p>
 */
@Service
public class ToolSetService implements IToolSetService {

    private static final Logger log = LoggerFactory.getLogger(ToolSetService.class);

    private final ApplicationContext applicationContext;
    private final PluginRegistry pluginRegistry;
    private final PluginStateService pluginStateService;

    public ToolSetService(ApplicationContext applicationContext,
                          PluginRegistry pluginRegistry,
                          PluginStateService pluginStateService) {
        this.applicationContext = applicationContext;
        this.pluginRegistry = pluginRegistry;
        this.pluginStateService = pluginStateService;
    }

    @Override
    public List<ToolSetInfo> getToolSets() {
        List<ToolSetInfo> result = new ArrayList<>(getToolSetsByPlugin(null));
        for (PluginRegistry.PluginEntry entry : pluginRegistry.getAllPluginEntries()) {
            if (!pluginStateService.isEnabled(entry.getPluginId())) {
                log.debug("Skip tool sets of disabled plugin [{}]", entry.getPluginId());
                continue;
            }
            result.addAll(scanEntry(entry));
        }
        return result;
    }

    @Override
    public List<ToolSetInfo> getToolSetsByPlugin(String pluginId) {
        if (pluginId == null) {
            List<ToolSetInfo> builtIns = new ArrayList<>();
            for (AgentTool tool : builtInTools()) {
                builtIns.add(scanToolSet(tool, AgentToolSourceEnum.BUILT_IN, null));
            }
            builtIns.sort(Comparator.comparing(ToolSetInfo::getName));
            return builtIns;
        }
        PluginRegistry.PluginEntry entry = pluginRegistry.getPlugin(pluginId);
        return entry == null ? List.of() : scanEntry(entry);
    }

    @Override
    public ToolSetInfo getToolSet(String toolSetName) {
        if (toolSetName == null || toolSetName.isEmpty()) {
            return null;
        }
        return getToolSets().stream()
                .filter(ts -> toolSetName.equals(ts.getName()))
                .findFirst().orElse(null);
    }

    @Override
    public List<AgentTool> getAgentTools() {
        List<AgentTool> tools = new ArrayList<>(builtInTools());
        for (PluginRegistry.PluginEntry entry : pluginRegistry.getAllPluginEntries()) {
            if (pluginStateService.isEnabled(entry.getPluginId())) {
                tools.addAll(entry.getTools());
            }
        }
        tools.sort(Comparator.comparing(AgentTool::getName));
        return tools;
    }

    @Override
    public AgentTool getAgentTool(String toolSetName) {
        if (toolSetName == null || toolSetName.isEmpty()) {
            return null;
        }
        for (AgentTool tool : getAgentTools()) {
            if (toolSetName.equals(tool.getName())) {
                return tool;
            }
        }
        return null;
    }

    @Override
    public Map<String, String> getToolNameAndDescriptionMap() {
        Map<String, String> toolNameMap = new HashMap<>();
        getToolSets().forEach(toolSet -> toolSet.getTools().forEach(
                tool -> toolNameMap.put(tool.getName(),
                        tool.getDescriptions().size() > 0 ? tool.getDescriptions().get(0) : tool.getName())
        ));
        toolNameMap.put(AgentTool.TOOL_SEARCH_TOOL_NAME, AgentTool.TOOL_SEARCH_TOOL_DESCRIPTION);
        return toolNameMap;
    }

    /**
     * 工具级配置变更分发：{@code tool.<工具集名>.} 前缀命中则触发对应工具的 {@code onConfigChanged()}。
     */
    @EventListener
    public void onConfigChange(ConfigChangeEvent event) {
        for (AgentTool tool : getAgentTools()) {
            String prefix = tool.getConfigPrefix();
            for (String key : event.getChangedKeys()) {
                if (key.startsWith(prefix)) {
                    try {
                        tool.onConfigChanged();
                        log.info("Config changed for tool [{}], onConfigChanged called", tool.getName());
                    } catch (Exception e) {
                        log.error("Error calling onConfigChanged for tool [{}]", tool.getName(), e);
                    }
                    break;
                }
            }
        }
    }

    private List<AgentTool> builtInTools() {
        Map<String, AgentTool> beans = applicationContext.getBeansOfType(AgentTool.class);
        return new ArrayList<>(beans.values());
    }

    private List<ToolSetInfo> scanEntry(PluginRegistry.PluginEntry entry) {
        List<ToolSetInfo> result = new ArrayList<>();
        for (AgentTool tool : entry.getTools()) {
            ToolSetInfo toolSetInfo = scanToolSet(tool, AgentToolSourceEnum.PLUGIN, entry);
            toolSetInfo.setJarFileName(entry.getJarFileName());
            toolSetInfo.setPluginId(entry.getPluginId());
            toolSetInfo.setPluginName(entry.getPluginName());
            if (!AgentTool.DEFAULT_ICON.equals(toolSetInfo.getIcon())) {
                String iconContent = entry.getCachedResource("static/icons/tools/" + toolSetInfo.getIcon());
                if (iconContent != null && !iconContent.isEmpty()) {
                    toolSetInfo.setIcon(iconContent);
                }
            }
            result.add(toolSetInfo);
        }
        result.sort(Comparator.comparing(ToolSetInfo::getName));
        return result;
    }

    /**
     * 扫描工具集元数据。
     *
     * @param agentTool 工具实例
     * @param source    来源（内置 / 插件）
     * @param entry     所属插件条目；内置工具传 null
     */
    private ToolSetInfo scanToolSet(AgentTool agentTool, AgentToolSourceEnum source, PluginRegistry.PluginEntry entry) {
        List<ToolInfo> tools = new ArrayList<>();
        for (Method method : agentTool.getClass().getMethods()) {
            Tool toolAnn = method.getAnnotation(Tool.class);
            if (toolAnn == null) continue;

            String toolName = toolAnn.name();
            if (toolName.isEmpty()) {
                toolName = method.getName();
            }
            String description = Arrays.stream(toolAnn.value()).collect(Collectors.joining(" "));
            List<ToolParamInfo> params = new ArrayList<>();
            for (Parameter param : method.getParameters()) {
                if (param.getType() == InvocationParameters.class) continue;

                P pAnn = param.getAnnotation(P.class);
                String paramName = param.getName();
                String paramDesc = "";
                boolean required = true;

                if (pAnn != null) {
                    paramDesc = pAnn.description();
                    if (paramDesc.isEmpty()) {
                        paramDesc = pAnn.value();
                    }
                    required = pAnn.required();
                }

                params.add(new ToolParamInfo(paramName, paramDesc, required, param.getType().getSimpleName()));
            }
            params.sort(Comparator.comparing(p -> p.isRequired() ? 0 : 1));

            ToolInfo toolInfo = new ToolInfo(toolName, description, params);
            toolInfo.setDescriptions(Arrays.asList(toolAnn.value()));
            ToolSecurityLevel methodSecurityAnn = method.getAnnotation(ToolSecurityLevel.class);
            if (methodSecurityAnn != null) {
                toolInfo.setSecurityLevel(methodSecurityAnn.value());
            }
            tools.add(toolInfo);
        }
        AgentPlugin plugin = entry == null ? null : entry.getPlugin();
        ToolSetInfo toolSetInfo = new ToolSetInfo(agentTool.getName(), agentTool.getDescription(),
                resolveIcon(agentTool, plugin), tools, source);
        // 插件身份元数据（版本/作者/来源）取自插件主体，工具不再承载；内置工具用默认值
        if (plugin != null) {
            toolSetInfo.setVersion(plugin.getVersion());
            toolSetInfo.setAuthor(plugin.getAuthor());
            toolSetInfo.setUrl(plugin.getUrl());
            toolSetInfo.setKeyword(agentTool.getKeyword().isEmpty() ? plugin.getKeyword() : agentTool.getKeyword());
        } else {
            toolSetInfo.setVersion("1.0.0");
            toolSetInfo.setAuthor("Agent Tool");
            toolSetInfo.setUrl("https://gitee.com/hgflydream/hopaw-agent");
            toolSetInfo.setKeyword(agentTool.getKeyword());
        }
        toolSetInfo.setHasConfigItems(!agentTool.getConfigItems().isEmpty());
        toolSetInfo.setAgentTool(agentTool);

        return toolSetInfo;
    }

    /**
     * 解析工具集图标：优先工具自身声明，工具未声明（默认图标）时回退到所属插件声明的图标。
     */
    private String resolveIcon(AgentTool agentTool, AgentPlugin plugin) {
        String icon = agentTool.getIcon();
        if (AgentTool.DEFAULT_ICON.equals(icon) && plugin != null && plugin.getIcon() != null) {
            return plugin.getIcon();
        }
        return icon;
    }
}
