package com.agent.hopaw.util;

import dev.langchain4j.invocation.InvocationParameters;

public class InvocationParametersUtil {
    public static String getUserId(InvocationParameters invocationParameters){
        return invocationParameters.get("userId");
    }
    public static String getAgentId(InvocationParameters invocationParameters){
        return invocationParameters.get("agentId");
    }
    public static String getMemoryId(InvocationParameters invocationParameters){
        return invocationParameters.get("memoryId");
    }
    public static InvocationParameters setUserId(InvocationParameters invocationParameters,String userId){
         invocationParameters.put("userId",userId);
         return invocationParameters;
    }
    public static InvocationParameters setAgentId(InvocationParameters invocationParameters,String agentId){
        invocationParameters.put("agentId",agentId);
        return invocationParameters;
    }
    public static InvocationParameters setMemoryId(InvocationParameters invocationParameters,String memoryId){
        invocationParameters.put("memoryId",memoryId);
        return invocationParameters;
    }
}
