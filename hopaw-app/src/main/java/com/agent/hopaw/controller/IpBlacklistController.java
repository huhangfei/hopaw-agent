package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.IpBlacklist;
import com.agent.hopaw.infra.service.IIpBlacklistService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ip-blacklist")
public class IpBlacklistController {

    private final IIpBlacklistService ipBlacklistService;

    public IpBlacklistController(IIpBlacklistService ipBlacklistService) {
        this.ipBlacklistService = ipBlacklistService;
    }

    @GetMapping
    public ResponseBean list() {
        List<IpBlacklist> list = ipBlacklistService.listAll();
        return ResponseBean.success(list);
    }

    @PostMapping
    public ResponseBean add(@RequestBody Map<String, String> body) {
        String ip = body.get("ip");
        String remark = body.get("remark");
        if (ip == null || ip.isBlank()) {
            return ResponseBean.fail("IP地址不能为空");
        }
        IpBlacklist entry = ipBlacklistService.add(ip.trim(), remark);
        if (entry == null) {
            return ResponseBean.fail("该IP已在黑名单中");
        }
        return ResponseBean.success(entry);
    }

    @DeleteMapping("/{id}")
    public ResponseBean delete(@PathVariable Long id) {
        ipBlacklistService.remove(id);
        return ResponseBean.success();
    }
}
