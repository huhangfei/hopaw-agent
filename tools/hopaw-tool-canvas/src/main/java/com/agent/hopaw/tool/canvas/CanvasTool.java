package com.agent.hopaw.tool.canvas;

import com.agent.hopaw.infra.service.IPluginResultStore;
import com.agent.hopaw.infra.service.IWebSocketBridgeService;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 画布工具插件后端门面。
 *
 * <p>核心思路：前端交互型工具不在后端真正"画"，而是通过 WS 下发指令到浏览器前端，
 * 由前端插件 JS 在 canvas 上实时绘制；当需要把结果返回给 LLM 时，后端阻塞等待前端
 * 通过暂存服务回传的 string（如 canvas.toDataURL() 的图片 base64）。</p>
 *
 * <p>四个 @Tool：</p>
 * <ul>
 *   <li>{@code drawCanvas} —— 开始绘制，前端收缩会话区并新建并排画布；</li>
 *   <li>{@code appendDraw} —— 下发实时绘制命令，前端在画布上绘制；</li>
 *   <li>{@code getCanvasResult} —— 获取当前画布结果图（不关闭插件），后端阻塞等待后返回给 LLM；</li>
 *   <li>{@code closeCanvas} —— 结束会话，前端还原布局并关闭插件。</li>
 * </ul>
 */
public class CanvasTool implements AgentTool {

    private static final Logger logger = LoggerFactory.getLogger(CanvasTool.class);

    private static final String TOOL_NAME = "canvas";

    /**
     * 画布逻辑尺寸（坐标系范围），需与前端 canvas.html 中
     * .plugin-canvas-board 的 width/height 保持一致。
     * 告知 LLM 该范围，避免绘制坐标越界或挤在角落。
     */
    private static final int CANVAS_WIDTH = 800;
    private static final int CANVAS_HEIGHT = 800;

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
    @Tool(value = {"开始画布绘制", "在浏览器前端收缩会话区并新建一个并排的实时画布，返回确认信息（含画布尺寸与坐标系说明）"})
    public String drawCanvas(
            @P("绘制说明，例如要绘制的内容主题") String description) {
        boolean ok = sendCommand("start", description == null ? "" : description, null);
        if (!ok) {
            return "错误: 画布启动指令下发失败（WebSocket 桥接异常），请稍后重试或告知用户刷新页面。";
        }
        return "画布已就绪。画布尺寸 " + CANVAS_WIDTH + "x" + CANVAS_HEIGHT + " 像素"
                + "（坐标系原点在左上角，x 向右、y 向下），请把 appendDraw 的所有坐标控制在该范围内。"
                + "注意：你无法直接查看画布图像（图片不支持作为工具结果回传），画布上可能已有用户手绘内容，"
                + "布局不确定时可询问用户或优先从空白位置开始绘制。";
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"追加画布绘制命令", "向浏览器前端实时画布下发绘制命令，支持 JSON 描述图形"})
    public String appendCanvasDraw(
            @P("绘制命令，JSON 字符串，可以是单个命令对象，也可以是多个命令对象组成的数组（按顺序执行）。" +
              "画布尺寸 " + CANVAS_WIDTH + "x" + CANVAS_HEIGHT +
              " 像素，坐标系原点在左上角（x 向右、y 向下），所有坐标请控制在该范围内。" +
              "支持的 type 及参数：" +
              "clear(清空，无参数)；" +
              "rect(矩形，x,y,w,h,color)；" +
              "circle(圆形，x,y,r,color)；" +
              "line(线段，x1,y1,x2,y2,w,color)；" +
              "polygon(多边形，points 为 [[x,y],[x,y],...] 坐标数组，color 填充色，可选 stroke/strokeColor/lineWidth 描边，可选 fill=false 仅描边)；" +
              "text(文字，text,x,y,size,color)。" +
              "示例（单命令）：{\"type\":\"polygon\",\"points\":[[10,10],[60,10],[35,60]],\"color\":\"#4f66d8\"}；" +
              "示例（数组，一次画多个图形）：[{\"type\":\"circle\",\"x\":400,\"y\":280,\"r\":100,\"color\":\"#2f9e44\"},{\"type\":\"text\",\"text\":\"你好\",\"x\":400,\"y\":280,\"size\":24,\"color\":\"#000\"}]") String command) {
        boolean ok = sendCommand("draw", command, null);
        if (!ok) {
            return "错误: 绘制命令下发失败（WebSocket 桥接异常），本次命令未生效，请稍后重试。";
        }
        return "绘制命令已下发。";
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"获取当前画布结果", "获取当前画布内容为图片返回给大模型，不关闭画布插件，可继续追加绘制"})
    public List<Content> getCanvasResult() {
        String requestId = UUID.randomUUID().toString();
        // 先注册待回传槽位，再下发快照指令（前端回传 dataURL，不关闭插件）
        pluginResultStore.register(requestId, null);
        sendCommand("snapshot", null, requestId);

        // 阻塞等待前端回传（纯 string 透传，期望前端回传 canvas.toDataURL() 的 data URL）
        String result = pluginResultStore.await(requestId, IPluginResultStore.DEFAULT_TIMEOUT_SECONDS);
        if (result == null) {
            return List.of(new TextContent("错误: 获取画布结果超时，可能前端画布未就绪或未在浏览器打开会话页面。"));
        }
        logger.info("CanvasTool: received canvas snapshot ({} chars)", result.length());

        // 解析 data URL（形如 data:image/png;base64,xxxxx），提取 MIME 与纯 base64
        DataUrl dataUrl = parseDataUrl(result);
        if (dataUrl == null) {
            return List.of(new TextContent("错误: 前端回传的画布结果不是有效的图片 data URL。"));
        }
        return List.of(
                new TextContent("画布当前结果已作为图片内容提供（格式 " + dataUrl.mimeType + "），画布保持打开可继续绘制。"),
                ImageContent.from(dataUrl.base64, dataUrl.mimeType));
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"结束画布会话并关闭插件", "结束画布会话，前端还原布局并关闭画布插件"})
    public String closeCanvas() {
        sendCommand("close", null, null);
        return "画布会话已结束，插件已关闭。";
    }

    /**
     * 解析 data URL：返回 MIME 类型与纯 base64（不含 data:...;base64, 前缀）。
     * 无法解析时返回 null。
     */
    private DataUrl parseDataUrl(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith("data:")) {
            return null;
        }
        int comma = dataUrl.indexOf(',');
        if (comma < 0) {
            return null;
        }
        String header = dataUrl.substring(0, comma);
        String data = dataUrl.substring(comma + 1);
        // header 形如 data:image/png;base64
        int colon = header.indexOf(':');
        String meta = colon >= 0 ? header.substring(colon + 1) : header;
        int semicolon = meta.indexOf(';');
        String mimeType = semicolon >= 0 ? meta.substring(0, semicolon) : meta;
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = "image/png";
        }
        String base64 = data;
        // 若 header 声明 base64，但部分实现可能仍带前缀，做一层兜底剥离
        if (base64.indexOf("base64,") >= 0) {
            base64 = base64.substring(base64.indexOf("base64,") + "base64,".length());
        }
        if (base64.isEmpty()) {
            return null;
        }
        return new DataUrl(mimeType, base64);
    }

    /** data URL 解析结果：MIME 类型 + 纯 base64 */
    private static final class DataUrl {
        final String mimeType;
        final String base64;

        DataUrl(String mimeType, String base64) {
            this.mimeType = mimeType;
            this.base64 = base64;
        }
    }

    /**
     * 下发指令到前端插件（通过 /ws/plugin 下行通道，userId 为空时广播）。
     *
     * @return true=已成功投递到消息队列；false=投递失败（前端不会收到）
     */
    private boolean sendCommand(String action, String payload, String requestId) {
        Map<String, Object> cmd = new HashMap<>();
        cmd.put("toolName", TOOL_NAME);
        cmd.put("action", action);
        cmd.put("payload", payload);
        cmd.put("requestId", requestId);
        try {
            boolean ok = webSocketBridgeService.sendPluginCommand(null, JSON.toJSONString(cmd));
            logger.info("CanvasTool: send command action={} payloadLen={} delivered={}", action,
                    payload == null ? 0 : payload.length(), ok);
            return ok;
        } catch (Exception e) {
            logger.error("CanvasTool: failed to send plugin command action={}", action, e);
            return false;
        }
    }
}
