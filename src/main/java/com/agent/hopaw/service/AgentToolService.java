package com.agent.hopaw.service;

import com.agent.hopaw.model.ToolInfo;
import com.agent.hopaw.model.ToolParamInfo;
import com.agent.hopaw.model.ToolSetInfo;
import com.agent.hopaw.tools.AgentTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class AgentToolService {

    private final ApplicationContext applicationContext;

    public AgentToolService(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public List<AgentTool> getAgentTools() {
        Map<String, AgentTool> beans = applicationContext.getBeansOfType(AgentTool.class);
        return new ArrayList<>(beans.values());
    }

    public List<ToolSetInfo> getToolSets() {
        List<ToolSetInfo> result = new ArrayList<>();
        for (AgentTool agentTool : getAgentTools()) {
            result.add(scanToolSet(agentTool));
        }
        return result;
    }

    private ToolSetInfo scanToolSet(AgentTool agentTool) {
        List<ToolInfo> tools = new ArrayList<>();
        for (Method method : agentTool.getClass().getMethods()) {
            Tool toolAnn = method.getAnnotation(Tool.class);
            if (toolAnn == null) continue;

            String toolName = toolAnn.name();
            if (toolName.isEmpty()) {
                toolName = method.getName();
            }

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

            tools.add(new ToolInfo(toolName, toolAnn.value()[0], params));
        }
        return new ToolSetInfo(agentTool.getName(), agentTool.getDescription(), agentTool.getIcon(), tools);
    }
}
