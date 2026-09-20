package com.agent.hopaw.tool.qrcode;

import com.agent.hopaw.infra.plugin.AbstractAgentPlugin;

/**
 * 二维码插件主体。
 */
public class QrCodePlugin extends AbstractAgentPlugin {

    public QrCodePlugin() {
        // 插件作为工具工厂：分发本插件提供的工具实例
        tools(new QrCodeTool());
    }

    @Override
    public String getId() {
        return "qrCode";
    }

    @Override
    public String getDescription() {
        return "二维码工具集，支持生成二维码图片和解析二维码图片内容";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getKeyword() {
        return "二维码,qrcode,QR,扫码";
    }

    @Override
    public String getIcon() {
        return "qrcode-tool.svg";
    }
}
