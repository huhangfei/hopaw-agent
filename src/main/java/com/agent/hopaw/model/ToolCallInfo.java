package com.agent.hopaw.model;

public class ToolCallInfo {
    public static final String TYPE_TOOL_CALL = "tool_call";
    public static final String STATUS_PREPARING = "preparing";
    public static final String STATUS_STARTING = "starting";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_EXECUTED = "executed";
    public static final String STATUS_STOPPABLE = "stoppable";
    public static final String STATUS_STOPPING = "stopping";

    private String type;
    private String status;
    private String toolCallId;
    private String toolName;
    private Object arguments;
    private Object argumentsPartial;
    private Object result;
    private Object resultPartial;
    private String responseId;


    private int index;

    public ToolCallInfo() {
    }

    public static ToolCallInfo preparing(String toolCallId, String toolName, Object argumentsPartial) {
        ToolCallInfo info = new ToolCallInfo();
        info.type = TYPE_TOOL_CALL;
        info.status = STATUS_PREPARING;
        info.toolCallId = toolCallId;
        info.toolName = toolName;
        info.argumentsPartial = argumentsPartial;
        return info;
    }
    public static ToolCallInfo starting(String toolCallId, String toolName, Object arguments) {
        ToolCallInfo info = new ToolCallInfo();
        info.type = TYPE_TOOL_CALL;
        info.status = STATUS_STARTING;
        info.toolCallId = toolCallId;
        info.toolName = toolName;
        info.arguments = arguments;
        return info;
    }

    public static ToolCallInfo stoppable(String toolCallId) {
        ToolCallInfo info = new ToolCallInfo();
        info.type = TYPE_TOOL_CALL;
        info.status = STATUS_STOPPABLE;
        info.toolCallId = toolCallId;
        return info;
    }


    public static ToolCallInfo stopping(String toolCallId) {
        ToolCallInfo info = new ToolCallInfo();
        info.type = TYPE_TOOL_CALL;
        info.status = STATUS_STOPPING;
        info.toolCallId = toolCallId;
        return info;
    }

    public static ToolCallInfo running(String toolCallId, Object resultPartial) {
        ToolCallInfo info = new ToolCallInfo();
        info.type = TYPE_TOOL_CALL;
        info.status = STATUS_RUNNING;
        info.toolCallId = toolCallId;
        info.resultPartial = resultPartial;
        return info;
    }

    public static ToolCallInfo executed(String toolCallId, String toolName, Object arguments, Object result) {
        ToolCallInfo info = new ToolCallInfo();
        info.type = TYPE_TOOL_CALL;
        info.status = STATUS_EXECUTED;
        info.toolCallId = toolCallId;
        info.toolName = toolName;
        info.arguments = arguments;
        info.result = result;
        return info;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public String getResponseId() {
        return responseId;
    }

    public void setResponseId(String responseId) {
        this.responseId = responseId;
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

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }
}