package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.service.ToolResultStore;
import com.agent.hopaw.util.CurrentUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 插件前端结果上报接口。
 *
 * <p>前端插件 JS 完成操作后调用本接口，把 string 结果上报给 {@link ToolResultStore}，
 * 解除后端 @Tool 的阻塞等待。value 为纯 string 透传，语义由具体 @Tool 决定。</p>
 */
@RestController
@RequestMapping("/api/plugins")
public class PluginReportController {

    private final ToolResultStore toolResultStore;

    public PluginReportController(ToolResultStore toolResultStore) {
        this.toolResultStore = toolResultStore;
    }

    @PostMapping("/report")
    public ResponseBean report(@RequestBody Map<String, String> body, HttpServletRequest request) {
        if (body == null) {
            return ResponseBean.fail("请求体为空");
        }
        String requestId = body.get("requestId");
        String value = body.get("value");
        if (requestId == null || requestId.isEmpty()) {
            return ResponseBean.fail("缺少 requestId");
        }
        String userId = CurrentUser.require(request);
        boolean ok = toolResultStore.complete(requestId, userId, value == null ? "" : value);
        return ok ? ResponseBean.success() : ResponseBean.fail("槽位不存在或已超时");
    }
}
