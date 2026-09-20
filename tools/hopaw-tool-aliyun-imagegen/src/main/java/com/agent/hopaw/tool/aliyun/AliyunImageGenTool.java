package com.agent.hopaw.tool.aliyun;

import com.agent.hopaw.infra.model.dto.ToolConfigItem;
import com.agent.hopaw.infra.model.dto.ValidationRule;
import com.agent.hopaw.infra.service.ISysConfigService;
import com.agent.hopaw.infra.tool.AbstractAgentTool;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 阿里云百炼文生图工具
 * 基于通义万相 qwen-image 系列模型，通过文字描述生成图片。
 * 调用多模态生成接口（multimodal-generation/generation），下载生成的 PNG 图片并作为图片内容返回给大模型。
 */
public class AliyunImageGenTool extends AbstractAgentTool {

    private static final Logger log = LoggerFactory.getLogger(AliyunImageGenTool.class);

    private static final String ENV_API_KEY = "DASHSCOPE_API_KEY";
    private static final String CONFIG_KEY_API_KEY = "apiKey";
    private static final String CONFIG_KEY_WORKSPACE_ID = "workspaceId";
    private static final String CONFIG_KEY_MODEL = "model";
    private static final String DEFAULT_MODEL = "qwen-image-3.0-pro";

    private static final String GENERATION_ENDPOINT_FORMAT =
            "https://%s.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";

    private final HttpClient httpClient;
    private volatile String apiKey;
    private volatile String workspaceId;
    private volatile String model = DEFAULT_MODEL;

    @Autowired
    private ISysConfigService sysConfigService;

    public AliyunImageGenTool() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public String getName() {
        return "aliyunImageGen";
    }

    @Override
    public String getDescription() {
        return "阿里云百炼文生图工具，基于通义万相 qwen-image 模型，根据文字描述生成图片";
    }

    @Override
    public String getIcon() {
        return "aliyun-image-gen.svg";
    }

    @Override
    public String getKeyword() {
        return "文生图,生成图片,画图,AI绘画,通义万相,阿里云,aliyun,qwen-image";
    }

    @Override
    public List<ToolConfigItem> getConfigItems() {
        return List.of(
                new ToolConfigItem(CONFIG_KEY_API_KEY, "阿里云百炼API Key",
                        "阿里云百炼（DashScope）API Key，用于 Bearer 鉴权（sk-xxx），不填则读取环境变量 " + ENV_API_KEY,
                        ToolConfigItem.ConfigType.TEXT_PASSWORD),
                new ToolConfigItem(CONFIG_KEY_WORKSPACE_ID, "业务空间ID（WorkspaceId）",
                        "请求地址中的业务空间ID，用于拼接生成接口地址",
                        ToolConfigItem.ConfigType.TEXT_SINGLE)
                        .validation(new ValidationRule().required())
                        .sensitive(false),
                new ToolConfigItem(CONFIG_KEY_MODEL, "模型名称",
                        "生成模型，可选 qwen-image-3.0-pro 或 qwen-image-3.0，默认 " + DEFAULT_MODEL,
                        ToolConfigItem.ConfigType.TEXT_SINGLE)
                        .sensitive(false)
        );
    }

    @Override
    public void asyncInit() {
        loadConfig();
    }

    @Override
    public void onConfigChanged() {
        log.info("收到配置变更通知，重新加载阿里云文生图工具配置");
        loadConfig();
    }

    private void loadConfig() {
        String prefix = getConfigPrefix();

        if (sysConfigService != null) {
            String dbApiKey = sysConfigService.getValueByKey(prefix + CONFIG_KEY_API_KEY, null);
            if (dbApiKey != null && !dbApiKey.isBlank()) {
                apiKey = dbApiKey.trim();
                log.info("Loaded Aliyun API key from database config");
            }
            String dbWorkspaceId = sysConfigService.getValueByKey(prefix + CONFIG_KEY_WORKSPACE_ID, null);
            if (dbWorkspaceId != null && !dbWorkspaceId.isBlank()) {
                workspaceId = dbWorkspaceId.trim();
            }
            String dbModel = sysConfigService.getValueByKey(prefix + CONFIG_KEY_MODEL, null);
            if (dbModel != null && !dbModel.isBlank()) {
                model = dbModel.trim();
            }
        }

        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv(ENV_API_KEY);
        }
        log.info("阿里云文生图工具配置已重载: workspaceId={}, model={}, apiKeyConfigured={}",
                workspaceId, model, apiKey != null && !apiKey.isBlank());
    }

    /**
     * 文生图：根据文字描述调用阿里云百炼生成图片，并将图片作为图片内容返回给大模型。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(name = "aliyunImageGen_generateImage", value = {"文生图", "根据文字描述生成图片，调用阿里云百炼通义万相模型，返回生成的图片给用户", "AI画图", "生成图片", "画图"})
    public List<Content> generateImage(
            @P(value = "正向提示词，描述期望生成的图片内容、风格和构图，支持中英文", required = false) String prompt,
            @P(value = "输出图像分辨率，格式为宽*高，如 1024*1024；不传由模型自动推荐", required = false) String size,
            @P(value = "输出图片数量，支持 1-6，默认 1", required = false) Integer n,
            @P(value = "反向提示词，描述不希望出现在画面中的内容", required = false) String negativePrompt,
            @P(value = "随机数种子，范围 [0, 2147483647]，固定种子结果相对稳定", required = false) Integer seed,
            @P(value = "是否开启提示词智能改写，默认 true", required = false) Boolean promptExtend) {
        try {
            if (prompt == null || prompt.isBlank()) {
                return errorResult("提示词不能为空");
            }
            String key = resolveApiKey();
            if (key == null || key.isBlank()) {
                return errorResult("未配置阿里云百炼API Key，请到 工具配置 页面设置 apiKey 或设置环境变量 " + ENV_API_KEY);
            }
            if (workspaceId == null || workspaceId.isBlank()) {
                return errorResult("未配置业务空间ID（WorkspaceId），请到 工具配置 页面设置 workspaceId");
            }

            String jsonBody = buildRequestBody(prompt, size, n, negativePrompt, seed, promptExtend);
            String endpoint = String.format(GENERATION_ENDPOINT_FORMAT, workspaceId.trim());

            long start = System.currentTimeMillis();
            String respBody = doPost(endpoint, key, jsonBody);
            if (respBody == null) {
                return errorResult("调用阿里云文生图接口失败");
            }

            JSONObject json = JSON.parseObject(respBody);
            if (json == null) {
                return errorResult("接口返回解析失败");
            }
            // 请求失败时返回 code / message
            if (json.containsKey("code")) {
                log.warn("阿里云文生图接口返回错误: {}", respBody);
                return errorResult("阿里云文生图接口错误: " + json.getString("message"));
            }

            JSONObject output = json.getJSONObject("output");
            if (output == null) {
                return errorResult("接口响应缺少 output 字段");
            }

            List<String> imageUrls = extractImageUrls(output);
            if (imageUrls.isEmpty()) {
                return errorResult("接口未返回生成的图片");
            }

            // 下载所有生成图片
            List<Content> result = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            sb.append("文生图成功，共生成 ").append(imageUrls.size()).append(" 张图片，图片已提供\n");
            sb.append("模型: ").append(model).append("\n");
            for (int i = 0; i < imageUrls.size(); i++) {
                String url = imageUrls.get(i);
                byte[] bytes = downloadImage(url);
                if (bytes == null || bytes.length == 0) {
                    result.add(new TextContent("错误: 生成第 " + (i + 1) + " 张图片后下载图片失败"));
                    break;
                }
                String base64 = Base64.getEncoder().encodeToString(bytes);
                result.add(new TextContent("图片 " + (i + 1) + ": " + url));
                result.add(ImageContent.from(base64, "image/png"));
            }
            sb.append("耗时: ").append(System.currentTimeMillis() - start).append("ms");
            result.add(0, new TextContent(sb.toString()));

            return result;
        } catch (Exception e) {
            log.error("阿里云文生图失败", e);
            return errorResult("阿里云文生图失败 - " + e.getMessage());
        }
    }

    private String buildRequestBody(String prompt, String size, Integer n,
                                    String negativePrompt, Integer seed, Boolean promptExtend) {
        JSONObject textContent = new JSONObject();
        textContent.put("text", prompt);

        JSONArray contentArray = new JSONArray();
        contentArray.add(textContent);

        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", contentArray);

        JSONArray messages = new JSONArray();
        messages.add(message);

        JSONObject input = new JSONObject();
        input.put("messages", messages);

        JSONObject parameters = new JSONObject();
        if (size != null && !size.isBlank()) {
            parameters.put("size", size.trim());
        }
        if (n != null) {
            parameters.put("n", n);
        }
        if (negativePrompt != null && !negativePrompt.isBlank()) {
            parameters.put("negative_prompt", negativePrompt.trim());
        }
        if (seed != null) {
            parameters.put("seed", seed);
        }
        if (promptExtend != null) {
            parameters.put("prompt_extend", promptExtend);
        }

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("input", input);
        body.put("parameters", parameters);
        return body.toJSONString();
    }

    private List<String> extractImageUrls(JSONObject output) {
        List<String> urls = new ArrayList<>();
        JSONArray choices = output.getJSONArray("choices");
        if (choices == null) {
            return urls;
        }
        for (int i = 0; i < choices.size(); i++) {
            JSONObject choice = choices.getJSONObject(i);
            if (choice == null) {
                continue;
            }
            JSONObject msg = choice.getJSONObject("message");
            if (msg == null) {
                continue;
            }
            JSONArray content = msg.getJSONArray("content");
            if (content == null) {
                continue;
            }
            for (int j = 0; j < content.size(); j++) {
                JSONObject item = content.getJSONObject(j);
                if (item != null) {
                    String image = item.getString("image");
                    if (image != null && !image.isBlank()) {
                        urls.add(image);
                    }
                }
            }
        }
        return urls;
    }

    private String doPost(String endpoint, String apiKey, String jsonBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(120))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            String body = response.body();
            if (status < 200 || status >= 300) {
                log.warn("阿里云文生图接口 HTTP {}: {}", status, body);
                return null;
            }
            return body;
        } catch (IOException e) {
            log.error("调用阿里云文生图接口 IO 异常", e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("调用阿里云文生图接口被中断", e);
            return null;
        }
    }

    private byte[] downloadImage(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(60))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            log.warn("下载生成图片失败 HTTP {}: {}", response.statusCode(), url);
            return null;
        } catch (IOException e) {
            log.error("下载生成图片 IO 异常: {}", url, e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("下载生成图片被中断: {}", url, e);
            return null;
        }
    }

    private String resolveApiKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }
        return System.getenv(ENV_API_KEY);
    }

    private List<Content> errorResult(String message) {
        return List.of(new TextContent("错误: " + message));
    }
}