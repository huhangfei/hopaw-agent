package com.agent.hopaw.tool.wechat;

import com.agent.hopaw.infra.model.dto.ToolConfigItem;
import com.agent.hopaw.infra.model.dto.ToolMapConfigItem;
import com.agent.hopaw.infra.model.dto.ValidationRule;
import com.agent.hopaw.infra.model.entity.SysConfig;
import com.agent.hopaw.infra.service.ISysConfigService;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import com.agent.hopaw.infra.tool.AgentTool;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 微信公众号文章管理工具。
 * <p>支持多公众号管理：在系统配置中添加多组公众号账号（账号名称 + AppID + AppSecret），
 * 各方法只需传入公众号账号名称即可操作对应公众号。能力覆盖：
 * 草稿箱（新增/查询/修改/删除）、素材（封面图片、正文图片、素材列表）、发布（发布草稿/状态/列表/删除）。</p>
 * <p>使用前提：已认证的非个人主体公众号；服务器 IP 需加入公众号 IP 白名单。</p>
 */
public class WechatOfficialAccountTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(WechatOfficialAccountTool.class);

    /** 映射组配置键：账号名称 → {appId, appSecret} */
    private static final String CONFIG_KEY_ACCOUNTS = "accounts";

    @Autowired
    private ISysConfigService sysConfigService;

    /** 公众号账号配置缓存：账号名称 → 凭证 */
    private volatile Map<String, WxAccount> cachedAccounts = Collections.emptyMap();

    /** 公众号凭证 */
    private record WxAccount(String appId, String appSecret) {}

    private final WeChatMpApiClient client = new WeChatMpApiClient();

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /** 常见微信错误码中文提示 */
    private static final Map<Integer, String> ERR_TIPS = new HashMap<>();

    static {
        ERR_TIPS.put(40001, "appSecret 错误或不属于该公众号，请检查开发者密码");
        ERR_TIPS.put(40013, "appId 不合法");
        ERR_TIPS.put(40164, "服务器 IP 不在公众号 IP 白名单内");
        ERR_TIPS.put(45009, "接口调用次数超过每日限额");
        ERR_TIPS.put(48001, "api 功能未授权，发布/草稿接口需要已认证的非个人主体公众号");
        ERR_TIPS.put(53401, "该草稿已发布或正在发布中");
        ERR_TIPS.put(53404, "article_id 或草稿不存在");
    }

    @Override
    public String getName() {
        return "wechatOfficialAccountTool";
    }

    @Override
    public String getDescription() {
        return "微信公众号文章管理工具，支持多公众号（公众号账号在系统配置中维护，各方法传入公众号账号名称即可），"
                + "提供文章草稿的新增/查询/修改/删除、封面与正文图片素材上传、文章发布与发布状态查询。"
                + "要求：已认证的非个人主体公众号，服务器 IP 需加入公众号后台 IP 白名单。";
    }

    @Override
    public String getKeyword() {
        return "微信公众号,公众号,文章,草稿,发布,图文,素材,封面,weixin,wechat,official account";
    }

    @Override
    public String getIcon() {
        return "wechat-official-account-tool.svg";
    }

    // ========== 配置定义与账号解析 ==========

    /**
     * 映射组结构配置：主体 key = 组名（公众号账号名称），values = 每组内的字段。
     * 存储为一条 JSON：{"账号名称1":{"appId":"...","appSecret":"..."},"账号名称2":{...}}
     */
    @Override
    public List<ToolConfigItem> getConfigItems() {
        ToolMapConfigItem accounts = new ToolMapConfigItem(CONFIG_KEY_ACCOUNTS, "公众号账号",
                "配置多个公众号：每组填写公众号账号名称，组内配置该公众号的 AppID 与 AppSecret，工具方法通过账号名称引用",
                ToolConfigItem.ConfigType.TEXT_SINGLE);
        accounts.setValues(List.of(
                new ToolConfigItem("appId", "AppID", "开发者ID", ToolConfigItem.ConfigType.TEXT_SINGLE)
                        .validation(new ValidationRule().required()),
                new ToolConfigItem("appSecret", "AppSecret", "开发者密码", ToolConfigItem.ConfigType.TEXT_PASSWORD)
                        .validation(new ValidationRule().required())
        ));
        return List.of(accounts);
    }

    @Override
    public void asyncInit() {
        reloadAccounts();
    }

    @Override
    public void onConfigChanged() {
        reloadAccounts();
    }

    /**
     * 从 sys_config 加载 accounts 映射组配置，解析为 账号名称 → 公众号凭证。
     */
    private void reloadAccounts() {
        Map<String, WxAccount> accounts = new LinkedHashMap<>();
        if (sysConfigService != null) {
            SysConfig config = sysConfigService.getByKey(getConfigPrefix() + CONFIG_KEY_ACCOUNTS);
            String json = config != null ? config.getConfigValue() : null;
            if (notBlank(json)) {
                try {
                    LinkedHashMap<String, LinkedHashMap<String, String>> groups = JSON.parseObject(json,
                            new TypeReference<LinkedHashMap<String, LinkedHashMap<String, String>>>() {});
                    for (Map.Entry<String, LinkedHashMap<String, String>> e : groups.entrySet()) {
                        String appId = e.getValue() != null ? e.getValue().get("appId") : null;
                        String appSecret = e.getValue() != null ? e.getValue().get("appSecret") : null;
                        if (notBlank(appId) && notBlank(appSecret)) {
                            accounts.put(e.getKey(), new WxAccount(appId, appSecret));
                        } else {
                            log.warn("公众号账号[{}]配置不完整（缺少 appId 或 appSecret），已跳过", e.getKey());
                        }
                    }
                } catch (Exception ex) {
                    log.error("公众号账号配置解析失败：{}", ex.getMessage());
                }
            }
        }
        this.cachedAccounts = accounts;
        log.info("公众号账号配置已加载，共{}个：{}", accounts.size(), accounts.keySet());
    }

    /**
     * 按公众号账号名称解析凭证；缓存为空时先尝试加载一次。
     */
    private WxAccount resolveAccount(String account) {
        if (isBlank(account)) {
            return null;
        }
        if (cachedAccounts.isEmpty()) {
            reloadAccounts();
        }
        return cachedAccounts.get(account.trim());
    }

    /**
     * 生成账号解析失败的提示信息（含可用账号列表，便于模型自我修正）。
     */
    private String accountError(String account) {
        if (isBlank(account)) {
            return "失败：公众号账号不能为空，请传入系统配置中添加的公众号账号名称";
        }
        if (cachedAccounts.isEmpty()) {
            return "失败：尚未配置任何公众号账号，请先在系统配置的公众号工具中添加（每组包含账号名称、AppID、AppSecret）";
        }
        return "失败：未找到公众号账号 [" + account + "]，当前已配置的账号：" + String.join("、", cachedAccounts.keySet());
    }

    // ========== 连接测试 ==========

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {
            "测试公众号连接",
            "验证公众号账号配置（AppID/AppSecret）是否正确并检查接口连通性。操作公众号前可先调用本方法确认配置。",
            "公众号,测试,连接,验证"
    })
    public String testConnection(
            @P(description = "公众号账号名称（需先在系统配置中添加）") String account) {
        WxAccount wx = resolveAccount(account);
        if (wx == null) return accountError(account);
        try {
            JSONObject resp = client.post(wx.appId(), wx.appSecret(), "draft/count", new JSONObject());
            return "连接成功，公众号[" + account + "]配置有效。\n当前草稿总数：" + resp.getIntValue("total_count");
        } catch (WeChatMpApiClient.WxApiException e) {
            return formatError("连接测试", e);
        } catch (Exception e) {
            return "失败：" + e.getMessage();
        }
    }

    // ========== 草稿管理 ==========

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {
            "新增公众号文章草稿",
            "在公众号草稿箱新增一篇文章（图文消息）。标题最长32字，正文支持HTML。封面图需先通过上传封面图片素材接口获得 mediaId。保存后可在公众号后台草稿箱中查看。",
            "公众号,文章,草稿,新增,创建,写作"
    })
    public String addDraft(
            @P(description = "公众号账号名称（需先在系统配置中添加）") String account,
            @P(description = "文章标题，不超过32个字") String title,
            @P(description = "文章正文内容，支持HTML标签，图片 src 必须使用上传文章内图片接口返回的微信 URL（外链图片会被过滤）") String content,
            @P(description = "封面图片的素材 mediaId（永久素材），需先调用上传封面图片素材接口获取") String thumbMediaId,
            @P(description = "作者，不超过16个字", required = false) String author,
            @P(description = "文章摘要，不超过120个字，不填则默认取正文前54个字", required = false) String digest,
            @P(description = "原文链接，即点击阅读原文后的URL", required = false) String contentSourceUrl,
            @P(description = "是否打开评论：1打开，0不打开（默认）", required = false) Integer needOpenComment) {

        WxAccount wx = resolveAccount(account);
        if (wx == null) return accountError(account);
        if (isBlank(title)) return "失败：文章标题不能为空";
        if (isBlank(content)) return "失败：文章正文不能为空";
        if (isBlank(thumbMediaId)) return "失败：封面图片素材 mediaId 不能为空，请先调用上传封面图片素材接口";

        JSONObject article = new JSONObject();
        article.put("title", title);
        article.put("content", content);
        article.put("thumb_media_id", thumbMediaId);
        article.put("need_open_comment", needOpenComment != null ? needOpenComment : 0);
        if (notBlank(author)) article.put("author", author);
        if (notBlank(digest)) article.put("digest", digest);
        if (notBlank(contentSourceUrl)) article.put("content_source_url", contentSourceUrl);

        JSONObject body = new JSONObject();
        JSONArray articles = new JSONArray();
        articles.add(article);
        body.put("articles", articles);

        try {
            JSONObject resp = client.post(wx.appId(), wx.appSecret(), "draft/add", body);
            return "草稿新增成功。\nmediaId: " + resp.getString("media_id")
                    + "\n后续可用该 mediaId 修改、删除草稿或发布文章。";
        } catch (WeChatMpApiClient.WxApiException e) {
            return formatError("新增草稿", e);
        } catch (Exception e) {
            return "失败：" + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {
            "查询公众号草稿列表",
            "分页查询公众号草稿箱中的文章列表，返回草稿 mediaId、标题、作者、摘要、更新时间（不含正文内容）。",
            "公众号,草稿,列表,查询,文章"
    })
    public String listDrafts(
            @P(description = "公众号账号名称（需先在系统配置中添加）") String account,
            @P(description = "分页起始位置，从0开始，默认0", required = false) Integer offset,
            @P(description = "每页数量，默认20，最大20", required = false) Integer count) {

        WxAccount wx = resolveAccount(account);
        if (wx == null) return accountError(account);
        int off = offset != null && offset >= 0 ? offset : 0;
        int cnt = count != null && count > 0 ? Math.min(count, 20) : 20;

        try {
            JSONObject body = new JSONObject();
            body.put("offset", off);
            body.put("count", cnt);
            body.put("no_content", 1); // 不返回正文，避免超长
            JSONObject resp = client.post(wx.appId(), wx.appSecret(), "draft/batchget", body);

            StringBuilder sb = new StringBuilder();
            sb.append("草稿总数：").append(resp.getIntValue("total_count"))
                    .append("，本页返回：").append(resp.getIntValue("item_count")).append("\n");
            JSONArray items = WeChatMpApiClient.getArray(resp, "item");
            for (int i = 0; i < items.size(); i++) {
                JSONObject item = items.getJSONObject(i);
                JSONObject first = WeChatMpApiClient.getArray(item, "content").getJSONObject(0);
                sb.append("\n[").append(off + i + 1).append("] mediaId: ").append(item.getString("media_id"));
                if (first != null) {
                    sb.append("\n    标题: ").append(safe(first.getString("title")));
                    if (notBlank(first.getString("author"))) sb.append("  作者: ").append(first.getString("author"));
                    if (notBlank(first.getString("digest"))) sb.append("\n    摘要: ").append(first.getString("digest"));
                }
                Long updateTime = item.getLong("update_time");
                if (updateTime != null) sb.append("\n    更新时间: ").append(formatTime(updateTime));
                sb.append("\n");
            }
            if (items.isEmpty()) sb.append("\n（草稿箱为空或该页无数据）");
            return sb.toString();
        } catch (WeChatMpApiClient.WxApiException e) {
            return formatError("查询草稿列表", e);
        } catch (Exception e) {
            return "失败：" + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {
            "查询公众号草稿详情",
            "查询单篇草稿的完整信息，包括标题、作者、摘要、正文HTML内容、封面素材等，可用于修改前的内容确认。",
            "公众号,草稿,详情,内容,正文"
    })
    public String getDraft(
            @P(description = "公众号账号名称（需先在系统配置中添加）") String account,
            @P(description = "草稿的 mediaId，可从查询草稿列表接口获取") String mediaId,
            @P(description = "正文内容最大返回长度（字符），超长截断，默认8000", required = false) Integer maxContentLength) {

        WxAccount wx = resolveAccount(account);
        if (wx == null) return accountError(account);
        if (isBlank(mediaId)) return "失败：mediaId 不能为空";
        int maxLen = maxContentLength != null && maxContentLength > 0 ? maxContentLength : 8000;

        try {
            JSONObject resp = client.post(wx.appId(), wx.appSecret(), "draft/get",
                    new JSONObject(Map.of("media_id", mediaId)));

            JSONArray news = WeChatMpApiClient.getArray(resp, "news_item");
            StringBuilder sb = new StringBuilder("草稿详情 mediaId: ").append(mediaId).append("\n");
            for (int i = 0; i < news.size(); i++) {
                JSONObject a = news.getJSONObject(i);
                sb.append("\n===== 第").append(i + 1).append("篇 =====\n");
                sb.append("标题: ").append(safe(a.getString("title"))).append("\n");
                sb.append("作者: ").append(safe(a.getString("author"))).append("\n");
                sb.append("摘要: ").append(safe(a.getString("digest"))).append("\n");
                sb.append("封面素材ID: ").append(safe(a.getString("thumb_media_id"))).append("\n");
                if (notBlank(a.getString("content_source_url"))) sb.append("原文链接: ").append(a.getString("content_source_url")).append("\n");
                sb.append("是否开启评论: ").append(a.getIntValue("need_open_comment") == 1 ? "是" : "否").append("\n");
                String content = a.getString("content");
                if (content != null && content.length() > maxLen) {
                    sb.append("正文(").append(content.length()).append("字符，已截断):\n")
                            .append(WeChatMpApiClient.truncate(content, maxLen)).append("\n");
                } else {
                    sb.append("正文:\n").append(safe(content)).append("\n");
                }
            }
            Long updateTime = resp.getLong("update_time");
            if (updateTime != null) sb.append("\n更新时间: ").append(formatTime(updateTime));
            return sb.toString();
        } catch (WeChatMpApiClient.WxApiException e) {
            return formatError("查询草稿详情", e);
        } catch (Exception e) {
            return "失败：" + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {
            "修改公众号文章草稿",
            "修改草稿箱中的文章。只需传入要修改的字段，未传字段保持原样。多图文草稿通过 index 指定要修改的篇目（从0开始）。",
            "公众号,草稿,修改,编辑,更新"
    })
    public String updateDraft(
            @P(description = "公众号账号名称（需先在系统配置中添加）") String account,
            @P(description = "草稿的 mediaId") String mediaId,
            @P(description = "要修改的篇目序号（多图文从0开始，单图文传0）") Integer index,
            @P(description = "新标题，不超过32个字", required = false) String title,
            @P(description = "新正文内容，支持HTML", required = false) String content,
            @P(description = "新封面图片素材 mediaId", required = false) String thumbMediaId,
            @P(description = "新作者", required = false) String author,
            @P(description = "新摘要，不超过120个字", required = false) String digest) {

        WxAccount wx = resolveAccount(account);
        if (wx == null) return accountError(account);
        if (isBlank(mediaId)) return "失败：mediaId 不能为空";
        if (index == null || index < 0) return "失败：index 不能为空（多图文从0开始）";
        if (isBlank(title) && isBlank(content) && isBlank(thumbMediaId) && isBlank(author) && isBlank(digest)) {
            return "失败：至少传入一个要修改的字段（标题/正文/封面/作者/摘要）";
        }

        JSONObject article = new JSONObject();
        if (notBlank(title)) article.put("title", title);
        if (notBlank(content)) article.put("content", content);
        if (notBlank(thumbMediaId)) article.put("thumb_media_id", thumbMediaId);
        if (notBlank(author)) article.put("author", author);
        if (notBlank(digest)) article.put("digest", digest);

        JSONObject body = new JSONObject();
        body.put("media_id", mediaId);
        body.put("index", index);
        body.put("articles", article);

        try {
            client.post(wx.appId(), wx.appSecret(), "draft/update", body);
            return "草稿修改成功。mediaId: " + mediaId + "，篇目: " + index;
        } catch (WeChatMpApiClient.WxApiException e) {
            return formatError("修改草稿", e);
        } catch (Exception e) {
            return "失败：" + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.ALL_REQUIRE_APPROVAL)
    @Tool(value = {
            "删除公众号文章草稿",
            "删除草稿箱中的指定草稿，删除后不可恢复，请谨慎操作。",
            "公众号,草稿,删除"
    })
    public String deleteDraft(
            @P(description = "公众号账号名称（需先在系统配置中添加）") String account,
            @P(description = "要删除的草稿 mediaId") String mediaId) {

        WxAccount wx = resolveAccount(account);
        if (wx == null) return accountError(account);
        if (isBlank(mediaId)) return "失败：mediaId 不能为空";
        try {
            client.post(wx.appId(), wx.appSecret(), "draft/delete", new JSONObject(Map.of("media_id", mediaId)));
            return "草稿删除成功。mediaId: " + mediaId;
        } catch (WeChatMpApiClient.WxApiException e) {
            return formatError("删除草稿", e);
        } catch (Exception e) {
            return "失败：" + e.getMessage();
        }
    }

    // ========== 素材管理 ==========

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {
            "上传公众号封面图片素材",
            "上传本地图片为公众号永久图片素材，返回 mediaId，用作文章封面（新增/修改草稿的 thumbMediaId 参数）。支持 jpg/png/gif/bmp，大小不超过10M。",
            "公众号,封面,素材,上传,图片"
    })
    public String uploadCoverImage(
            @P(description = "公众号账号名称（需先在系统配置中添加）") String account,
            @P(description = "本地图片文件的绝对路径（jpg/png/gif/bmp）") String filePath) {

        WxAccount wx = resolveAccount(account);
        if (wx == null) return accountError(account);
        Path file = validateImageFile(filePath);
        if (file == null) return "失败：图片文件不存在或格式不支持（支持 jpg/png/gif/bmp）: " + filePath;
        try {
            JSONObject resp = client.uploadMedia(wx.appId(), wx.appSecret(), "material/add_material", "type=image", file);
            return "封面上传成功。\nmediaId: " + resp.getString("media_id")
                    + "\n图片URL: " + safe(resp.getString("url"))
                    + "\n请将 mediaId 作为新增/修改草稿的封面素材ID（thumbMediaId）使用。";
        } catch (WeChatMpApiClient.WxApiException e) {
            return formatError("上传封面图片", e);
        } catch (Exception e) {
            return "失败：上传封面图片异常 - " + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {
            "上传公众号文章内图片",
            "上传本地图片到公众号并返回可用于文章正文的微信图片URL。公众号正文中的图片必须使用本接口返回的URL，外链图片会被微信过滤。",
            "公众号,正文,图片,上传,素材"
    })
    public String uploadContentImage(
            @P(description = "公众号账号名称（需先在系统配置中添加）") String account,
            @P(description = "本地图片文件的绝对路径（jpg/png/gif/bmp）") String filePath) {

        WxAccount wx = resolveAccount(account);
        if (wx == null) return accountError(account);
        Path file = validateImageFile(filePath);
        if (file == null) return "失败：图片文件不存在或格式不支持（支持 jpg/png/gif/bmp）: " + filePath;
        try {
            JSONObject resp = client.uploadMedia(wx.appId(), wx.appSecret(), "media/uploadimg", null, file);
            return "正文图片上传成功。\n微信图片URL: " + safe(resp.getString("url"))
                    + "\n请在文章正文HTML中使用该URL作为 img 标签的 src。";
        } catch (WeChatMpApiClient.WxApiException e) {
            return formatError("上传正文图片", e);
        } catch (Exception e) {
            return "失败：上传正文图片异常 - " + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {
            "查询公众号素材列表",
            "分页查询公众号永久素材列表（图片/视频/图文），返回素材 mediaId、名称、URL、更新时间。",
            "公众号,素材,列表,查询,图片"
    })
    public String listMaterials(
            @P(description = "公众号账号名称（需先在系统配置中添加）") String account,
            @P(description = "素材类型：image（默认）/ video / news", required = false) String type,
            @P(description = "分页起始位置，从0开始，默认0", required = false) Integer offset,
            @P(description = "每页数量，默认20，最大20", required = false) Integer count) {

        WxAccount wx = resolveAccount(account);
        if (wx == null) return accountError(account);
        String t = notBlank(type) ? type : "image";
        int off = offset != null && offset >= 0 ? offset : 0;
        int cnt = count != null && count > 0 ? Math.min(count, 20) : 20;

        try {
            JSONObject body = new JSONObject();
            body.put("type", t);
            body.put("offset", off);
            body.put("count", cnt);
            JSONObject resp = client.post(wx.appId(), wx.appSecret(), "material/batchget", body);

            StringBuilder sb = new StringBuilder();
            sb.append("素材类型: ").append(t)
                    .append("，总数: ").append(resp.getIntValue("total_count"))
                    .append("，本页返回: ").append(resp.getIntValue("item_count")).append("\n");
            JSONArray items = WeChatMpApiClient.getArray(resp, "item");
            for (int i = 0; i < items.size(); i++) {
                JSONObject item = items.getJSONObject(i);
                sb.append("\n[").append(off + i + 1).append("] mediaId: ").append(item.getString("media_id"));
                sb.append("\n    名称: ").append(safe(item.getString("name")));
                if (notBlank(item.getString("url"))) sb.append("\n    URL: ").append(item.getString("url"));
                Long updateTime = item.getLong("update_time");
                if (updateTime != null) sb.append("\n    更新时间: ").append(formatTime(updateTime));
                sb.append("\n");
            }
            if (items.isEmpty()) sb.append("\n（无素材或该页无数据）");
            return sb.toString();
        } catch (WeChatMpApiClient.WxApiException e) {
            return formatError("查询素材列表", e);
        } catch (Exception e) {
            return "失败：" + e.getMessage();
        }
    }

    // ========== 发布管理 ==========

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {
            "发布公众号文章",
            "将草稿箱中的文章提交发布（异步任务）。提交成功后返回 publishId，需调用查询发布状态接口轮询发布结果，成功后文章会展示在公众号主页历史消息中。注意：发布不会给粉丝推送群发消息。",
            "公众号,发布,发表,群发,上线"
    })
    public String publishDraft(
            @P(description = "公众号账号名称（需先在系统配置中添加）") String account,
            @P(description = "要发布的草稿 mediaId") String mediaId) {

        WxAccount wx = resolveAccount(account);
        if (wx == null) return accountError(account);
        if (isBlank(mediaId)) return "失败：mediaId 不能为空";
        try {
            JSONObject resp = client.post(wx.appId(), wx.appSecret(), "freepublish/submit",
                    new JSONObject(Map.of("media_id", mediaId)));
            return "发布任务提交成功。\npublishId: " + resp.getString("publish_id")
                    + "\n发布为异步任务，请稍后调用查询发布状态接口（传入 publishId）确认发布结果。";
        } catch (WeChatMpApiClient.WxApiException e) {
            return formatError("发布文章", e);
        } catch (Exception e) {
            return "失败：" + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {
            "查询公众号发布状态",
            "通过 publishId 查询文章发布任务的执行状态与结果，返回发布状态、articleId 与文章链接。发布状态：0成功 / 1发布中 / 2原创失败 / 3常用错误 / 4平台审核不通过。",
            "公众号,发布,状态,查询,进度"
    })
    public String getPublishStatus(
            @P(description = "公众号账号名称（需先在系统配置中添加）") String account,
            @P(description = "发布任务ID（publishId），来自发布文章接口的返回") String publishId) {

        WxAccount wx = resolveAccount(account);
        if (wx == null) return accountError(account);
        if (isBlank(publishId)) return "失败：publishId 不能为空";
        try {
            JSONObject resp = client.post(wx.appId(), wx.appSecret(), "freepublish/get",
                    new JSONObject(Map.of("publish_id", publishId)));
            return formatPublishResult(publishId, resp);
        } catch (WeChatMpApiClient.WxApiException e) {
            return formatError("查询发布状态", e);
        } catch (Exception e) {
            return "失败：" + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {
            "查询公众号已发布文章列表",
            "分页查询公众号已成功发布的文章列表，返回 articleId、标题、文章链接、发布时间。",
            "公众号,发布,文章,列表,历史消息"
    })
    public String listPublishedArticles(
            @P(description = "公众号账号名称（需先在系统配置中添加）") String account,
            @P(description = "分页起始位置，从0开始，默认0", required = false) Integer offset,
            @P(description = "每页数量，默认20，最大20", required = false) Integer count) {

        WxAccount wx = resolveAccount(account);
        if (wx == null) return accountError(account);
        int off = offset != null && offset >= 0 ? offset : 0;
        int cnt = count != null && count > 0 ? Math.min(count, 20) : 20;

        try {
            JSONObject body = new JSONObject();
            body.put("offset", off);
            body.put("count", cnt);
            body.put("no_content", 1); // 不返回正文，避免超长
            JSONObject resp = client.post(wx.appId(), wx.appSecret(), "freepublish/batchget", body);

            StringBuilder sb = new StringBuilder();
            sb.append("已发布总数：").append(resp.getIntValue("total_count"))
                    .append("，本页返回：").append(resp.getIntValue("item_count")).append("\n");
            JSONArray items = WeChatMpApiClient.getArray(resp, "item");
            for (int i = 0; i < items.size(); i++) {
                JSONObject item = items.getJSONObject(i);
                sb.append("\n[").append(off + i + 1).append("] articleId: ").append(item.getString("article_id"));
                JSONArray news = WeChatMpApiClient.getArray(item.getJSONObject("content"), "news_item");
                for (int j = 0; j < news.size(); j++) {
                    JSONObject a = news.getJSONObject(j);
                    sb.append("\n    第").append(j + 1).append("篇 标题: ").append(safe(a.getString("title")));
                    if (notBlank(a.getString("link"))) sb.append("\n    链接: ").append(a.getString("link"));
                }
                Long updateTime = item.getLong("update_time");
                if (updateTime != null) sb.append("\n    发布时间: ").append(formatTime(updateTime));
                sb.append("\n");
            }
            if (items.isEmpty()) sb.append("\n（暂无已发布文章或该页无数据）");
            return sb.toString();
        } catch (WeChatMpApiClient.WxApiException e) {
            return formatError("查询已发布文章列表", e);
        } catch (Exception e) {
            return "失败：" + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.ALL_REQUIRE_APPROVAL)
    @Tool(value = {
            "删除公众号已发布文章",
            "删除已发布成功的文章（从公众号主页历史消息中移除）。删除后不可恢复，若文章被自定义菜单或自动回复引用将失效，请谨慎操作。",
            "公众号,删除,下架,已发布"
    })
    public String deletePublishedArticle(
            @P(description = "公众号账号名称（需先在系统配置中添加）") String account,
            @P(description = "要删除的已发布文章 articleId（可从查询已发布文章列表接口获取）") String articleId,
            @P(description = "要删除的篇目序号（多图文从0开始，单图文传0）") Integer index) {

        WxAccount wx = resolveAccount(account);
        if (wx == null) return accountError(account);
        if (isBlank(articleId)) return "失败：articleId 不能为空";
        if (index == null || index < 0) return "失败：index 不能为空（多图文从0开始）";
        try {
            JSONObject body = new JSONObject();
            body.put("article_id", articleId);
            body.put("index", index);
            client.post(wx.appId(), wx.appSecret(), "freepublish/delete", body);
            return "已发布文章删除成功。articleId: " + articleId + "，篇目: " + index;
        } catch (WeChatMpApiClient.WxApiException e) {
            return formatError("删除已发布文章", e);
        } catch (Exception e) {
            return "失败：" + e.getMessage();
        }
    }

    // ========== 私有辅助 ==========

    private Path validateImageFile(String filePath) {
        if (isBlank(filePath)) return null;
        Path file = Path.of(filePath.trim());
        if (!Files.isRegularFile(file)) return null;
        String name = file.getFileName().toString().toLowerCase();
        if (!(name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                || name.endsWith(".gif") || name.endsWith(".bmp"))) return null;
        return file;
    }

    private String formatPublishResult(String publishId, JSONObject resp) {
        JSONObject articleDetail = resp.getJSONObject("article_detail");
        StringBuilder sb = new StringBuilder("发布任务查询结果 publishId: ").append(publishId).append("\n");
        Integer status = resp.getInteger("publish_status");
        sb.append("发布状态: ").append(publishStatusText(status)).append("\n");
        String failId = resp.getString("fail_idx");
        if (notBlank(failId)) sb.append("失败篇目序号: ").append(failId).append("\n");
        if (notBlank(resp.getString("article_id"))) {
            sb.append("articleId: ").append(resp.getString("article_id")).append("\n");
        }
        if (articleDetail != null) {
            JSONArray items = WeChatMpApiClient.getArray(articleDetail, "item");
            for (int i = 0; i < items.size(); i++) {
                JSONObject item = items.getJSONObject(i);
                sb.append("\n第").append(item.getIntValue("idx")).append("篇 标题: ").append(safe(item.getString("title")));
                if (notBlank(item.getString("article_url"))) sb.append("\n链接: ").append(item.getString("article_url"));
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String publishStatusText(Integer status) {
        if (status == null) return "未知（发布中）";
        switch (status) {
            case 0: return "发布成功";
            case 1: return "发布中，请稍后再次查询";
            case 2: return "发布失败（原创声明失败）";
            case 3: return "发布失败（常用错误）";
            case 4: return "发布失败（平台审核不通过）";
            default: return "未知状态码 " + status;
        }
    }

    private String formatError(String action, WeChatMpApiClient.WxApiException e) {
        String tip = ERR_TIPS.get(e.getErrcode());
        return "失败：" + action + "接口返回错误 - " + e.getMessage()
                + (tip != null ? "\n提示：" + tip : "");
    }

    private static String formatTime(long epochSeconds) {
        return TIME_FMT.format(Instant.ofEpochSecond(epochSeconds));
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean notBlank(String s) {
        return !isBlank(s);
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }
}
