package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.PluginRepoResult;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.service.IPluginStoreService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 插件商店端点（{@code /plugins/store}）：浏览远端插件仓库，安装 / 升级插件。
 *
 * <p>归属「运维管理 → 插件管理」分支 —— 与 {@link PluginController} 的 {@code /plugins} 同组，
 * 页面复用同一条 {@code op-tabs} 导航条并把「插件管理」置为激活态（不新增菜单项），
 * 由页面上的返回按钮回到插件列表 {@code /plugins}。</p>
 */
@Controller
@RequestMapping("/plugins/store")
public class PluginStoreController {

    private final IPluginStoreService pluginStoreService;

    public PluginStoreController(IPluginStoreService pluginStoreService) {
        this.pluginStoreService = pluginStoreService;
    }

    @GetMapping({"", "/"})
    public String storePage(Model model) {
        // 商店属于插件管理分支：激活「插件管理」Tab，同时让侧边栏「系统运维」与 body[data-page] 口径一致
        model.addAttribute("activePage", "plugins");
        model.addAttribute("activeTab", "plugins");
        return "plugin-store";
    }

    @GetMapping("/api/plugins")
    @ResponseBody
    public ResponseBean apiStorePlugins() {
        List<PluginRepoResult> pluginRepoResults = pluginStoreService.fetchStorePlugins();
        return ResponseBean.success(pluginRepoResults);
    }
}
