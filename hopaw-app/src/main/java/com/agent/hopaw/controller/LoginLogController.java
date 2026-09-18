package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.SysConfig;
import com.agent.hopaw.infra.service.ILoginLogService;
import com.agent.hopaw.infra.service.ISysConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/login-log")
public class LoginLogController {

    private final ILoginLogService loginLogService;
    private final ISysConfigService sysConfigService;

    public LoginLogController(ILoginLogService loginLogService, ISysConfigService sysConfigService) {
        this.loginLogService = loginLogService;
        this.sysConfigService = sysConfigService;
    }

    @GetMapping("/page")
    public ResponseBean page(@RequestParam(required = false, defaultValue = "") String userId,
                             @RequestParam(required = false, defaultValue = "") String ip,
                             @RequestParam(required = false, defaultValue = "") String result,
                             @RequestParam(required = false, defaultValue = "1") int page,
                             @RequestParam(required = false, defaultValue = "20") int size) {
        Map<String, Object> data = loginLogService.queryPage(userId, ip, result, page, size);
        return ResponseBean.success(data);
    }

    @DeleteMapping("/all")
    public ResponseBean clearAll() {
        loginLogService.clearAll();
        return ResponseBean.success();
    }

    @GetMapping("/exception-config")
    public ResponseBean getExceptionConfig() {
        Map<String, Object> config = new HashMap<>();
        String maxUserFailures = sysConfigService.getValueByKey("login_max_user_failures", "10");
        String maxIpFailures = sysConfigService.getValueByKey("login_max_ip_failures", "20");
        String notifyChannels = sysConfigService.getValueByKey("login_exception_notify_channels", "");
        config.put("maxUserFailures", Integer.parseInt(maxUserFailures));
        config.put("maxIpFailures", Integer.parseInt(maxIpFailures));
        config.put("notifyChannels", notifyChannels);
        return ResponseBean.success(config);
    }

    @PutMapping("/exception-config")
    public ResponseBean saveExceptionConfig(@RequestBody Map<String, Object> body) {
        Object maxUser = body.get("maxUserFailures");
        Object maxIp = body.get("maxIpFailures");
        Object channels = body.get("notifyChannels");

        if (maxUser != null) {
            saveOrUpdateConfig("login_max_user_failures", String.valueOf(maxUser), "登录异常-用户最大失败次数");
        }
        if (maxIp != null) {
            saveOrUpdateConfig("login_max_ip_failures", String.valueOf(maxIp), "登录异常-IP最大失败次数");
        }
        if (channels != null) {
            saveOrUpdateConfig("login_exception_notify_channels", String.valueOf(channels), "登录异常-通知渠道ID列表");
        }
        return ResponseBean.success();
    }

    private void saveOrUpdateConfig(String key, String value, String description) {
        SysConfig existing = sysConfigService.getByKey(key);
        if (existing != null) {
            existing.setConfigValue(value);
            sysConfigService.update(existing);
        } else {
            sysConfigService.insert(new SysConfig(key, value, description));
        }
    }
}
