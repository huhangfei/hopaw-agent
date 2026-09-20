package com.agent.hopaw.tool.svg;

import com.agent.hopaw.infra.service.IWebSocketBridgeService;
import com.agent.hopaw.infra.tool.AbstractAgentTool;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import com.agent.hopaw.infra.util.InvocationParametersWrapper;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.apache.batik.transcoder.image.JPEGTranscoder;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.OutputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * SVG 工具插件后端门面。
 *
 * <p>两类能力：</p>
 * <ul>
 *   <li><b>前端插槽交互</b>：{@code showSvg} 通过 WS 把 SVG 代码下发到浏览器，前端在会话
 *       侧边插槽中并排渲染 SVG 图片，并提供「下载」「切换代码」按钮（代码可编辑、实时重渲染）；
 *       {@code closeSvg} 结束会话、关闭插槽。</li>
 *   <li><b>服务端光栅化</b>：{@code saveSvg} / {@code saveSvgFile} 用 Batik 把 SVG 渲染为
 *       PNG/JPEG 位图保存到指定路径（自带插槽预览不可用时由模型直接出图）。</li>
 * </ul>
 */
public class SvgTool extends AbstractAgentTool {

    private static final Logger log = LoggerFactory.getLogger(SvgTool.class);

    private static final String TOOL_NAME = "svg";

    /** SVG 转图片时的默认放大倍数，保证位图清晰度 */
    private static final float DEFAULT_SCALE = 2.0f;

    @Autowired
    private IWebSocketBridgeService webSocketBridgeService;

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        return "SVG 工具集：在会话侧边插槽中预览/编辑/下载 SVG，并将 SVG 代码或 SVG 文件渲染保存为图片";
    }

    @Override
    public String getIcon() {
        return "svg-tool.svg";
    }

    @Override
    public String getKeyword() {
        return "svg,矢量图,矢量,绘图,图标";
    }

    // ==================== 前端插槽交互 ====================

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(name = "svg_show", value = {"展示SVG", "把 SVG 代码下发到会话前端插槽并排渲染为图片展示，用户可在插槽中预览、切换查看/编辑源码、下载", "预览SVG"})
    public String showSvg(
            @P(description = "SVG 代码，根元素需包含 xmlns=\"http://www.w3.org/2000/svg\"，建议指定 width 和 height 或 viewBox") String svgCode,
            @P(description = "插槽标题，用于说明这张 SVG 是什么，如 \"登录页图标\"", required = false) String title,
            InvocationParameters invocationParameters) {
        if (svgCode == null || svgCode.isBlank()) {
            return "错误: SVG 代码不能为空";
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("svg", svgCode);
        payload.put("title", title == null ? "" : title);
        boolean ok = sendCommand("show", JSON.toJSONString(payload), null, invocationParameters);
        if (!ok) {
            return "错误: SVG 展示指令下发失败（WebSocket 桥接异常），请稍后重试或告知用户刷新页面。";
        }
        return "SVG 已在会话侧边插槽中展示。用户可在插槽内切换查看/编辑源码、下载图片；"
                + "如需继续修改，请用新的 svg_show 覆盖展示。";
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(name = "svg_close", value = {"关闭SVG插槽", "结束 SVG 插槽会话，前端还原布局并关闭插槽"})
    public String closeSvg(InvocationParameters invocationParameters) {
        sendCommand("close", null, null, invocationParameters);
        return "SVG 插槽已关闭。";
    }

    // ==================== SVG 光栅化保存 ====================

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(name = "svg_save", value = {"保存SVG图片", "将SVG代码渲染为位图并保存到指定路径，输出格式由文件扩展名决定(.png/.jpg/.jpeg)，SVG代码需包含xmlns命名空间；保存成功后会把 SVG 同步展示到会话侧边插槽", "SVG转图片"})
    public String saveSvgImage(
            @P(description = "SVG代码，根元素需包含xmlns=\"http://www.w3.org/2000/svg\"，建议指定width和height属性") String svgCode,
            @P(description = "保存图片的完整文件路径，扩展名 .png 或 .jpg/.jpeg 决定输出格式，如 D:/images/logo.png") String filePath,
            InvocationParameters invocationParameters) {
        try {
            if (svgCode == null || svgCode.isBlank()) {
                return "错误: SVG代码不能为空";
            }
            if (filePath == null || filePath.isBlank()) {
                return "错误: 文件路径不能为空";
            }
            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            ImageTranscoder transcoder = createTranscoder(path);
            if (transcoder == null) {
                return "错误: 不支持的输出格式，请使用 .png 或 .jpg/.jpeg 扩展名: " + filePath;
            }

            // 创建父目录
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            long start = System.currentTimeMillis();
            TranscoderInput input = new TranscoderInput(new StringReader(svgCode));
            try (OutputStream os = Files.newOutputStream(path)) {
                TranscoderOutput output = new TranscoderOutput(os);
                transcoder.transcode(input, output);
            }

            // 顺带把 SVG 推到前端插槽展示（失败不影响保存结果）
            pushToSlot(svgCode, path.getFileName().toString(), invocationParameters);

            StringBuilder sb = new StringBuilder();
            sb.append("SVG图片保存成功\n");
            sb.append("格式: ").append(path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png") ? "image/png" : "image/jpeg").append("\n");
            sb.append("路径: ").append(path).append("\n");
            sb.append("大小: ").append(formatFileSize(Files.size(path))).append("\n");
            sb.append("耗时: ").append(System.currentTimeMillis() - start).append("ms");
            return sb.toString();
        } catch (Exception e) {
            log.error("保存SVG图片失败: {}", filePath, e);
            return "错误: 保存SVG图片失败 - " + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(name = "svg_saveFile", value = {"SVG文件转图片", "读取指定路径的SVG文件并渲染为位图保存到指定路径，输出格式由文件扩展名决定(.png/.jpg/.jpeg)；转换成功后会把 SVG 同步展示到会话侧边插槽", "SVG文件转图片"})
    public String saveSvgFileToImage(
            @P(description = "SVG文件的完整路径") String svgFilePath,
            @P(description = "保存图片的完整文件路径，扩展名 .png 或 .jpg/.jpeg 决定输出格式，如 D:/images/logo.png") String filePath,
            InvocationParameters invocationParameters) {
        try {
            if (svgFilePath == null || svgFilePath.isBlank()) {
                return "错误: SVG文件路径不能为空";
            }
            if (filePath == null || filePath.isBlank()) {
                return "错误: 输出文件路径不能为空";
            }

            Path svgPath = Paths.get(svgFilePath).toAbsolutePath().normalize();
            if (!Files.exists(svgPath)) {
                return "错误: SVG文件不存在: " + svgFilePath;
            }
            if (!Files.isRegularFile(svgPath)) {
                return "错误: 路径不是文件: " + svgFilePath;
            }
            String svgFileName = svgPath.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!svgFileName.endsWith(".svg")) {
                return "错误: 文件不是SVG格式(扩展名需为.svg): " + svgFilePath;
            }

            String svgCode = Files.readString(svgPath);
            if (svgCode == null || svgCode.isBlank()) {
                return "错误: SVG文件内容为空: " + svgFilePath;
            }

            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            ImageTranscoder transcoder = createTranscoder(path);
            if (transcoder == null) {
                return "错误: 不支持的输出格式，请使用 .png 或 .jpg/.jpeg 扩展名: " + filePath;
            }

            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            // 解析SVG原始尺寸，按倍数放大输出高清位图
            float svgWidth = parseSvgWidth(svgCode);
            float svgHeight = parseSvgHeight(svgCode);
            if (svgWidth > 0 && svgHeight > 0) {
                transcoder.addTranscodingHint(ImageTranscoder.KEY_WIDTH, svgWidth * DEFAULT_SCALE);
                transcoder.addTranscodingHint(ImageTranscoder.KEY_HEIGHT, svgHeight * DEFAULT_SCALE);
            }

            long start = System.currentTimeMillis();
            TranscoderInput input = new TranscoderInput(new StringReader(svgCode));
            try (OutputStream os = Files.newOutputStream(path)) {
                TranscoderOutput output = new TranscoderOutput(os);
                transcoder.transcode(input, output);
            }

            // 顺带把 SVG 推到前端插槽展示（失败不影响转换结果）
            pushToSlot(svgCode, svgPath.getFileName().toString(), invocationParameters);

            StringBuilder sb = new StringBuilder();
            sb.append("SVG文件转图片成功\n");
            sb.append("源文件: ").append(svgPath).append("\n");
            sb.append("格式: ").append(path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png") ? "image/png" : "image/jpeg").append("\n");
            sb.append("路径: ").append(path).append("\n");
            sb.append("大小: ").append(formatFileSize(Files.size(path))).append("\n");
            sb.append("耗时: ").append(System.currentTimeMillis() - start).append("ms");
            return sb.toString();
        } catch (Exception e) {
            log.error("SVG文件转图片失败: {} -> {}", svgFilePath, filePath, e);
            return "错误: SVG文件转图片失败 - " + e.getMessage();
        }
    }

    /**
     * 把 SVG 源码推到前端插槽展示（best-effort：下发失败只记日志，不影响工具主流程）
     */
    private void pushToSlot(String svgCode, String title, InvocationParameters invocationParameters) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("svg", svgCode);
            payload.put("title", title == null ? "" : title);
            sendCommand("show", JSON.toJSONString(payload), null, invocationParameters);
        } catch (Exception e) {
            log.warn("SvgTool: push svg to slot failed: {}", e.getMessage());
        }
    }

    /**
     * 按输出路径扩展名创建转码器，不支持的格式返回 null
     */
    private ImageTranscoder createTranscoder(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            return new PNGTranscoder();
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            JPEGTranscoder jpeg = new JPEGTranscoder();
            // 指定默认压缩质量，避免 Batik 未设置质量时的告警日志
            jpeg.addTranscodingHint(JPEGTranscoder.KEY_QUALITY, 0.9f);
            return jpeg;
        }
        return null;
    }

    /**
     * 解析SVG宽度（支持width属性和viewBox）
     */
    private float parseSvgWidth(String svgCode) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("width=[\"']([\\d.]+)(?:px)?[\"']", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(svgCode);
        if (m.find()) {
            try {
                return Float.parseFloat(m.group(1));
            } catch (Exception ignored) {
            }
        }
        m = java.util.regex.Pattern.compile("viewBox=[\"'][\\d.]+\\s+[\\d.]+\\s+([\\d.]+)\\s+([\\d.]+)[\"']").matcher(svgCode);
        if (m.find()) {
            try {
                return Float.parseFloat(m.group(1));
            } catch (Exception ignored) {
            }
        }
        return -1;
    }

    /**
     * 解析SVG高度（支持height属性和viewBox）
     */
    private float parseSvgHeight(String svgCode) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("height=[\"']([\\d.]+)(?:px)?[\"']", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(svgCode);
        if (m.find()) {
            try {
                return Float.parseFloat(m.group(1));
            } catch (Exception ignored) {
            }
        }
        m = java.util.regex.Pattern.compile("viewBox=[\"'][\\d.]+\\s+[\\d.]+\\s+([\\d.]+)\\s+([\\d.]+)[\"']").matcher(svgCode);
        if (m.find()) {
            try {
                return Float.parseFloat(m.group(2));
            } catch (Exception ignored) {
            }
        }
        return -1;
    }

    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        }
        if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        }
        return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * 下发指令到前端插件（通过 /ws/plugin 下行通道）。
     * 按会话隔离：sessionId 非空时指令仅推给注册了该会话的前端连接；
     * sessionId 为空（如直接 API 调用）时退化为广播。
     *
     * @return true=已成功投递到消息队列；false=投递失败（前端不会收到）
     */
    private boolean sendCommand(String action, String payload, String requestId, InvocationParameters invocationParameters) {
        String sessionId = userIdOrSession(invocationParameters, false);
        String userId = userIdOrSession(invocationParameters, true);
        Map<String, Object> cmd = new HashMap<>();
        cmd.put("toolName", TOOL_NAME);
        cmd.put("action", action);
        cmd.put("payload", payload);
        cmd.put("requestId", requestId);
        try {
            boolean ok = webSocketBridgeService.sendPluginCommand(userId, sessionId, JSON.toJSONString(cmd));
            log.info("SvgTool: send command action={} sessionId={} payloadLen={} delivered={}", action,
                    sessionId, payload == null ? 0 : payload.length(), ok);
            return ok;
        } catch (Exception e) {
            log.error("SvgTool: failed to send plugin command action={}", action, e);
            return false;
        }
    }

    /** 从 InvocationParameters 提取 userId / sessionId，可能为 null。 */
    private String userIdOrSession(InvocationParameters invocationParameters, boolean wantUserId) {
        if (invocationParameters == null) {
            return null;
        }
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        return wantUserId ? wrapper.getUserId() : wrapper.getSessionId();
    }
}
