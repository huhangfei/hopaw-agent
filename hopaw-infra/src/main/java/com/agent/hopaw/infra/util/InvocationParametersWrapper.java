package com.agent.hopaw.infra.util;

import dev.langchain4j.invocation.InvocationParameters;

public class InvocationParametersWrapper {
    private InvocationParameters invocationParameters;

    public InvocationParametersWrapper(InvocationParameters invocationParameters) {
        this.invocationParameters = invocationParameters;
    }

    public InvocationParametersWrapper() {
        invocationParameters=new InvocationParameters();
    }

    public  String getUserId(){
        return this.invocationParameters.get("userId");
    }
    public  String getToolCallId(){
        return this.invocationParameters.get("toolCallId");
    }
    public  Long getAgentId(){
        return Long.valueOf(invocationParameters.get("agentId").toString());
    }
    public  String getRequestId(){
        return invocationParameters.get("requestId");
    }
    public  InvocationParametersWrapper setUserId(String userId){
         invocationParameters.put("userId",userId);
         return this;
    }
    public  InvocationParametersWrapper setToolCallId(String toolCallId){
         invocationParameters.put("toolCallId",toolCallId);
         return this;
    }
    public  InvocationParametersWrapper setAgentId(Long agentId){
        invocationParameters.put("agentId",agentId);
        return this;
    }

    public  InvocationParametersWrapper setRequestId(String requestId){
        invocationParameters.put("requestId",requestId);
        return this;
    }
    public  InvocationParametersWrapper setSessionId(String sessionId){
        invocationParameters.put("sessionId",sessionId);
        return this;
    }

    public InvocationParameters getParameters() {
        return invocationParameters;
    }

    public static InvocationParametersWrapper create(InvocationParameters invocationParameters) {
        return new InvocationParametersWrapper(invocationParameters);
    }
    public static InvocationParametersWrapper create() {
        return new InvocationParametersWrapper();
    }
}
