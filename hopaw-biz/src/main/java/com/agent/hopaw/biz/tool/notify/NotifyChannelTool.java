package com.agent.hopaw.biz.tool.notify;

import com.agent.hopaw.infra.model.entity.NotifyChannel;
import com.agent.hopaw.infra.service.INotificationService;
import com.agent.hopaw.infra.service.INotifyChannelService;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import com.agent.hopaw.infra.util.InvocationParametersWrapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 消息通知渠道工具：查询当前用户的通知渠道，向一个或多个渠道发送通知消息。
 */
@Component("notifyChannelTool")
public class NotifyChannelTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(NotifyChannelTool.class);

    private final INotifyChannelService notifyChannelService;
    private final INotificationService notificationService;

    public NotifyChannelTool(INotifyChannelService notifyChannelService,
                             INotificationService notificationService) {
        this.notifyChannelService = notifyChannelService;
        this.notificationService = notificationService;
    }

    @Override
    public String getName() {
        return "notifyChannelTool";
    }

    @Override
    public String getDescription() {
        return "查询通知渠道并向一个或多个渠道发送消息通知（钉钉群/飞书/邮件/Webhook）";
    }

    @Override
    public String getIcon() {
        return "notify-tool.svg";
    }

    @Override
    public String getKeyword() {
        return "通知";
    }

    /**
     * 查询当前用户全部通知渠道（编号、名称、类型、启用状态）。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"查询通知渠道", "查询当前用户全部通知渠道（编号、名称、类型、启用状态）"})
    public String queryNotifyChannels(InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        List<NotifyChannel> channels = notifyChannelService.listByUser(wrapper.getUserId());
        if (channels == null || channels.isEmpty()) {
            return "成功：当前用户暂无通知渠道，可先在系统设置-通知渠道中新增";
        }
        StringBuilder sb = new StringBuilder("共 " + channels.size() + " 个通知渠道：\n");
        for (NotifyChannel ch : channels) {
            sb.append("渠道ID：").append(ch.getId())
                    .append("，名称：").append(ch.getName())
                    .append("，类型：").append(channelTypeText(ch.getType()))
                    .append("，状态：").append(Boolean.FALSE.equals(ch.getEnabled()) ? "已停用" : "启用")
                    .append("\n");
        }
        return "成功：\n" + sb;
    }

    /**
     * 向一个或多个通知渠道发送消息。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.ALL_REQUIRE_APPROVAL)
    @Tool(value = {"发送通知消息", "向一个或多个通知渠道发送消息通知（标题+内容），渠道编号可通过「查询通知渠道」获取"})
    public String sendNotifyMessage(@P(value = "渠道编号列表，多个用逗号分隔，如 1,2", required = true) String channelIds,
                                    @P(value = "通知标题") String title,
                                    @P(value = "通知内容") String content,
                                    InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        // 解析渠道编号
        List<Long> ids;
        try {
            ids = java.util.Arrays.stream((channelIds == null ? "" : channelIds).split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());
        } catch (NumberFormatException e) {
            return "失败：渠道编号格式错误（应为数字，多个用逗号分隔）";
        }
        if (ids.isEmpty()) {
            return "失败：请至少提供一个渠道编号";
        }
        if (content == null || content.isBlank()) {
            return "失败：通知内容不能为空";
        }

        String finalTitle = (title == null || title.isBlank()) ? content.split("\n")[0] : title;
        StringBuilder result = new StringBuilder();
        int ok = 0;
        for (Long id : ids) {
            // 校验渠道归属，防止跨用户发送
            NotifyChannel channel = notifyChannelService.getChannel(id, wrapper.getUserId());
            if (channel == null) {
                result.append("渠道 ").append(id).append("：失败（渠道不存在或无权访问）\n");
                continue;
            }
            try {
                String err = notificationService.sendByChannelId(id, finalTitle, content);
                if (err == null) {
                    ok++;
                    result.append("渠道 ").append(id).append("（").append(channel.getName()).append("）：发送成功\n");
                    log.info("工具调用: 通知消息发送成功 userId={}, channelId={}", wrapper.getUserId(), id);
                } else {
                    result.append("渠道 ").append(id).append("（").append(channel.getName()).append("）：失败（").append(err).append("）\n");
                }
            } catch (Exception e) {
                log.warn("工具调用: 通知消息发送异常 channelId={}", id, e);
                result.append("渠道 ").append(id).append("：发送异常（").append(e.getMessage()).append("）\n");
            }
        }
        return "通知发送完成：成功 " + ok + "/" + ids.size() + " 个渠道\n" + result;
    }

    /** 渠道类型码转中文 */
    private String channelTypeText(String type) {
        if (type == null) return "未知";
        switch (type) {
            case "dingtalk": return "钉钉群";
            case "feishu": return "飞书";
            case "email": return "邮件";
            case "webhook": return "Webhook";
            default: return type;
        }
    }
}
