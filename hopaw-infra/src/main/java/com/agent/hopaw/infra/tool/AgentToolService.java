package com.agent.hopaw.infra.tool;

import com.agent.hopaw.infra.constant.AgentToolSourceEnum;
import com.agent.hopaw.infra.model.dto.ToolInfo;
import com.agent.hopaw.infra.model.dto.ToolParamInfo;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import com.agent.hopaw.infra.plugin.DynamicToolRegistry;
import com.agent.hopaw.infra.plugin.JarPluginLoader;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AgentToolService implements IAgentToolService {

    private final ApplicationContext applicationContext;
    private final DynamicToolRegistry dynamicToolRegistry;
    private final JarPluginLoader jarPluginLoader;

    public AgentToolService(ApplicationContext applicationContext, DynamicToolRegistry dynamicToolRegistry, JarPluginLoader jarPluginLoader) {
        this.applicationContext = applicationContext;
        this.dynamicToolRegistry = dynamicToolRegistry;
        this.jarPluginLoader = jarPluginLoader;
    }

    @Override
    public List<AgentTool> getAgentTools() {
        Map<String, AgentTool> beans = applicationContext.getBeansOfType(AgentTool.class);
        List<AgentTool> tools = new ArrayList<>(beans.values());
        tools.addAll(dynamicToolRegistry.getAllDynamicTools());
        tools.sort(Comparator.comparing(AgentTool::getName));
        return tools;
    }

    @Override
    public List<ToolSetInfo> getToolSets() {
        List<ToolSetInfo> result = new ArrayList<>();
        Map<String, AgentTool> beans = applicationContext.getBeansOfType(AgentTool.class);
        for (AgentTool agentTool : beans.values()) {
            result.add(scanToolSet(agentTool, AgentToolSourceEnum.BUILT_IN));
        }
        result.addAll(getAllToolSets());
        return result;
    }
    public List<ToolSetInfo> getAllToolSets() {
        List<ToolSetInfo> result = new ArrayList<>();
        List<DynamicToolRegistry.PluginEntry> allPluginEntries = dynamicToolRegistry.getAllPluginEntries();
        for (DynamicToolRegistry.PluginEntry entry : allPluginEntries) {
            List<AgentTool> tools = entry.tools;
            for (AgentTool tool : tools) {
                ToolSetInfo toolSetInfo = scanToolSet(tool, AgentToolSourceEnum.PLUGIN);
                toolSetInfo.setJarFileName(entry.jarFileName);
                if(!AgentTool.DEFAULT_ICON.equals(toolSetInfo.getIcon())){
                    toolSetInfo.setIcon(entry.getCachedResource("static/icons/tools/"+tool.getIcon()));
                }
                result.add(toolSetInfo);
            }
        }
        return result;
    }
    private ToolSetInfo scanToolSet(AgentTool agentTool, AgentToolSourceEnum source) {
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

            tools.add(new ToolInfo(toolName, description, params));
        }
        ToolSetInfo toolSetInfo = new ToolSetInfo(agentTool.getName(), agentTool.getDescription(), agentTool.getIcon(), tools, source);
        toolSetInfo.setVersion(agentTool.getVersion());
        toolSetInfo.setAuthor(agentTool.getAuthor());
        toolSetInfo.setUrl(agentTool.getUrl());
        toolSetInfo.setKeyword(agentTool.getKeyword());
        toolSetInfo.setHasConfigItems(!agentTool.getConfigItems().isEmpty());
        return toolSetInfo;
    }

    @Override
    public boolean unloadPlugin(String jarFileName) {
        return jarPluginLoader.unloadAndDeletePlugin(jarFileName);
    }
}
