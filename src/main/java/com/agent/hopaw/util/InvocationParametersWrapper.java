package com.agent.hopaw.util;

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
    public  String getAgentId(){
        return invocationParameters.get("agentId");
    }
    public  String getRequestId(){
        return invocationParameters.get("requestId");
    }
    public  InvocationParametersWrapper setUserId(String userId){
         invocationParameters.put("userId",userId);
         return this;
    }
    public  InvocationParametersWrapper setAgentId(String agentId){
        invocationParameters.put("agentId",agentId);
        return this;
    }

    public  InvocationParametersWrapper setRequestId(String requestId){
        invocationParameters.put("requestId",requestId);
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
