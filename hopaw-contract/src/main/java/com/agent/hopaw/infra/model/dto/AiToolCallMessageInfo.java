package com.agent.hopaw.infra.model.dto;

public class AiToolCallMessageInfo extends AiMessageBaseInfo{
    public static final String TYPE_TOOL_CALL = "tool_call";
    public static final String STATUS_PREPARING = "preparing";
    public static final String STATUS_STARTING = "starting";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_EXECUTED = "executed";
    public static final String STATUS_STOPPABLE = "stoppable";
    public static final String STATUS_STOPPING = "stopping";

    private String status;
    private String toolCallId;
    private String toolName;
    private Object arguments;
    private Object argumentsPartial;
    private Object result;
    private Object resultPartial;

    private Integer index;

    public AiToolCallMessageInfo() {
        super(TYPE_TOOL_CALL);
    }

    public static AiToolCallMessageInfo preparing(String sessionId, String requestId, String toolCallId, String toolName, Object argumentsPartial,Integer index) {
        AiToolCallMessageInfo info = new AiToolCallMessageInfo();
        info.setSessionId(sessionId);
        info.setRequestId(requestId);
        info.setStatus(STATUS_PREPARING);
        info.setToolCallId(toolCallId);
        info.setToolName(toolName);
        info.setArgumentsPartial(argumentsPartial);
        info.setIndex(index);
        return info;
    }
    public static AiToolCallMessageInfo starting(String sessionId, String requestId, String toolCallId, String toolName, Object arguments) {
        AiToolCallMessageInfo info = new AiToolCallMessageInfo();
        info.setSessionId(sessionId);
        info.setRequestId(requestId);
        info.setStatus(STATUS_STARTING);
        info.setToolCallId(toolCallId);
        info.setToolName(toolName);
        info.setArguments(arguments);
        return info;
    }

    public static AiToolCallMessageInfo stoppable(String sessionId, String requestId, String toolCallId) {
        AiToolCallMessageInfo info = new AiToolCallMessageInfo();
        info.setSessionId(sessionId);
        info.setRequestId(requestId);
        info.setStatus(STATUS_STOPPABLE);
        info.setToolCallId(toolCallId);
        return info;
    }


    public static AiToolCallMessageInfo stopping(String sessionId, String requestId, String toolCallId) {
        AiToolCallMessageInfo info = new AiToolCallMessageInfo();
        info.setSessionId(sessionId);
        info.setRequestId(requestId);
        info.setStatus(STATUS_STOPPING);
        info.setToolCallId(toolCallId);
        return info;
    }

    public static AiToolCallMessageInfo running(String sessionId, String requestId, String toolCallId, Object resultPartial) {
        AiToolCallMessageInfo info = new AiToolCallMessageInfo();
        info.setSessionId(sessionId);
        info.setRequestId(requestId);
        info.setStatus(STATUS_RUNNING);
        info.setToolCallId(toolCallId);
        info.setResultPartial(resultPartial);
        return info;
    }

    public static AiToolCallMessageInfo executed(String sessionId, String requestId, String toolCallId, String toolName, Object arguments, Object result) {
        AiToolCallMessageInfo info = new AiToolCallMessageInfo();
        info.setSessionId(sessionId);
        info.setRequestId(requestId);
        info.setStatus(STATUS_EXECUTED);
        info.setToolCallId(toolCallId);
        info.setToolName(toolName);
        info.setArguments(arguments);
        info.setResult(result);
        return info;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public Object getArguments() {
        return arguments;
    }

    public void setArguments(Object arguments) {
        this.arguments = arguments;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }


    public Object getResultPartial() {
        return resultPartial;
    }

    public void setResultPartial(Object resultPartial) {
        this.resultPartial = resultPartial;
    }

    public Object getArgumentsPartial() {
        return argumentsPartial;
    }

    public void setArgumentsPartial(Object argumentsPartial) {
        this.argumentsPartial = argumentsPartial;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }
}