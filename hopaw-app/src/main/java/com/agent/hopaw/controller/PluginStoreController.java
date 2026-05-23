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

@Controller
@RequestMapping("/tools/plugin-store")
public class PluginStoreController {

    private final IPluginStoreService pluginStoreService;

    public PluginStoreController(IPluginStoreService pluginStoreService) {
        this.pluginStoreService = pluginStoreService;
    }

    @GetMapping({"", "/"})
    public String storePage(Model model) {
        return "plugin-store";
    }

    @GetMapping("/api/plugins")
    @ResponseBody
    public ResponseBean apiStorePlugins() {
        List<PluginRepoResult> pluginRepoResults = pluginStoreService.fetchStorePlugins();
        return ResponseBean.success(pluginRepoResults);
    }
}
