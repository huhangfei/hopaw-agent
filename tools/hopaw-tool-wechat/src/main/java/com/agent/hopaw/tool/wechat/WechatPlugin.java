package com.agent.hopaw.tool.wechat;

import com.agent.hopaw.infra.plugin.AbstractAgentPlugin;

/**
 * 微信公众号文章管理插件主体。
 */
public class WechatPlugin extends AbstractAgentPlugin {

    public WechatPlugin() {
        // 插件作为工具工厂：分发本插件提供的工具实例
        tools(new WechatOfficialAccountTool());
    }

    @Override
    public String getId() {
        return "wechatOfficialAccountTool";
    }

    @Override
    public String getDescription() {
        return "微信公众号文章管理工具，支持多公众号（公众号账号在系统配置中维护，各方法传入公众号账号名称即可），"
                + "提供文章草稿的新增/查询/修改/删除、封面与正文图片素材上传、文章发布与发布状态查询。"
                + "要求：已认证的非个人主体公众号，服务器 IP 需加入公众号后台 IP 白名单。";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getKeyword() {
        return "微信公众号,公众号,文章,草稿,发布,图文,素材,封面,weixin,wechat,official account";
    }

    @Override
    public String getIcon() {
        return "wechat-official-account-tool.svg";
    }
}
