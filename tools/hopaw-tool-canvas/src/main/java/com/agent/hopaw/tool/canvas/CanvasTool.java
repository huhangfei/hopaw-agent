package com.agent.hopaw.tool.canvas;

import com.agent.hopaw.infra.service.IPluginResultStore;
import com.agent.hopaw.infra.service.IWebSocketBridgeService;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 画布工具插件后端门面。
 *
 * <p>核心思路：前端交互型工具不在后端真正"画"，而是通过 WS 下发指令到浏览器前端，
 * 由前端插件 JS 在 canvas 上实时绘制；当需要把结果返回给 LLM 时，后端阻塞等待前端
 * 通过暂存服务回传的 string（如 canvas.toDataURL() 的图片 base64）。</p>
 *
 * <p>三个 @Tool：</p>
 * <ul>
 *   <li>{@code drawCanvas} —— 开始绘制，前端收缩会话区并新建并排画布；</li>
 *   <li>{@code appendDraw} —— 下发实时绘制命令，前端在画布上绘制；</li>
 *   <li>{@code finishCanvas} —— 结束绘制，前端还原布局并回传画布结果图，后端阻塞等待后返回给 LLM。</li>
 * </ul>
 */
public class CanvasTool implements AgentTool {

    private static final Logger logger = LoggerFactory.getLogger(CanvasTool.class);

    private static final String TOOL_NAME = "canvas";

    @Autowired
    private IWebSocketBridgeService webSocketBridgeService;

    @Autowired
    private IPluginResultStore pluginResultStore;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "画布绘图工具：在浏览器前端并排展示实时画布并绘制图形，结束时获取画布结果图";
    }

    @Override
    public String getKeyword() {
        return "画布,绘图,canvas,绘制";
    }

    @Override
    public String getIcon() {
        return "agent-tool.svg";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"开始画布绘制", "在浏览器前端收缩会话区并新建一个并排的实时画布，返回确认信息"})
    public String drawCanvas(
            @P("绘制说明，例如要绘制的内容主题") String description) {
        sendCommand("start", description == null ? "" : description, null);
        return "画布已就绪，可通过 appendDraw 下发绘制命令。";
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"追加画布绘制命令", "向浏览器前端实时画布下发绘制命令，支持 JSON 描述图形"})
    public String appendDraw(
            @P("绘制命令，JSON 字符串，如 {\"type\":\"rect\",\"x\":10,\"y\":10,\"w\":100,\"h\":80,\"color\":\"#4f66d8\"}") String command) {
        sendCommand("draw", command, null);
        return "绘制命令已下发。";
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"结束画布绘制并获取结果", "结束画布绘制，前端还原布局并回传画布结果图，返回图片内容"})
    public String finishCanvas() {
        String requestId = UUID.randomUUID().toString();
        // 先注册待回传槽位，再下发结束指令
        pluginResultStore.register(requestId, null);
        sendCommand("finish", null, requestId);

        // 阻塞等待前端回传（纯 string 透传，此处期望前端回传图片 base64 或 URL）
        String result = pluginResultStore.await(requestId, IPluginResultStore.DEFAULT_TIMEOUT_SECONDS);
        if (result == null) {
            return "获取画布结果超时，可能前端画布未就绪或未在浏览器打开会话页面。";
        }
        logger.info("CanvasTool: received canvas result ({} chars)", result.length());
        return result;
    }

    /**
     * 下发指令到前端插件（通过 /ws/plugin 下行通道，userId 为空时广播）。
     */
    private void sendCommand(String action, String payload, String requestId) {
        Map<String, Object> cmd = new HashMap<>();
        cmd.put("toolName", TOOL_NAME);
        cmd.put("action", action);
        cmd.put("payload", payload);
        cmd.put("requestId", requestId);
        try {
            webSocketBridgeService.sendPluginCommand(null, JSON.toJSONString(cmd));
        } catch (Exception e) {
            logger.error("CanvasTool: failed to send plugin command action={}", action, e);
        }
    }
}
