package com.example.tool;

import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.SearchBehavior;
import dev.langchain4j.agent.tool.Tool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.Objects;

/**
 * 示例插件工具。
 * <p>
 * 注意：插件工具不需要 {@code @Component} 注解，
 * 由 JarPluginLoader 通过反射发现并注册。
 * </p>
 */
public class MyPluginTool implements AgentTool {

    private final OkHttpClient httpClient = new OkHttpClient();

    // ========== 元数据 ==========

    @Override
    public String getName() {
        return "myPluginTool";
    }

    @Override
    public String getDescription() {
        return "我的示例插件工具，用于演示 AgentTool 插件开发流程";
    }

    @Override
    public String getKeyword() {
        return "示例 插件";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getAuthor() {
        return "Example Author";
    }

    @Override
    public String getIcon() {
        return "my-plugin-tool.svg";
    }

    // ========== Tool 方法 ==========

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = "调用示例 API 获取数据", searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String callExampleApi(
            @P(description = "API 路径，例如 /users/1") String path) {
        try {
            Request request = new Request.Builder()
                    .url("https://api.example.com" + (path.startsWith("/") ? path : "/" + path))
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                return Objects.requireNonNullElse(response.body()).string();
            }
        } catch (Exception e) {
            return "调用失败: " + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = "回显输入内容（用于调试）", searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String echo(
            @P(description = "需要回显的内容") String content) {
        return "Echo: " + content;
    }
}
