package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.Prompt;
import com.agent.hopaw.infra.service.IPromptService;
import com.agent.hopaw.util.CurrentUser;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/prompts")
public class PromptController {

    private final IPromptService promptService;

    public PromptController(IPromptService promptService) {
        this.promptService = promptService;
    }

    @GetMapping("/page")
    public ResponseBean page(HttpServletRequest request,
                             @RequestParam(required = false, defaultValue = "") String keyword,
                             @RequestParam(required = false, defaultValue = "") String tag,
                             @RequestParam(required = false, defaultValue = "heat") String sortBy,
                             @RequestParam(required = false, defaultValue = "1") int page,
                             @RequestParam(required = false, defaultValue = "20") int size) {
        String userId = CurrentUser.require(request);
        Map<String, Object> data = promptService.list(userId, keyword, tag, sortBy, page, size);
        return ResponseBean.success(data);
    }

    @GetMapping("/{id}")
    public ResponseBean getById(@PathVariable Long id) {
        Prompt prompt = promptService.getById(id);
        if (prompt == null) return ResponseBean.fail("提示词不存在");
        return ResponseBean.success(prompt);
    }

    @PostMapping
    public ResponseBean create(HttpServletRequest request, @RequestBody Prompt prompt) {
        String userId = CurrentUser.require(request);
        prompt.setUserId(userId);
        promptService.create(prompt);
        return ResponseBean.success(prompt);
    }

    @PutMapping("/{id}")
    public ResponseBean update(HttpServletRequest request, @PathVariable Long id, @RequestBody Prompt prompt) {
        String userId = CurrentUser.require(request);
        prompt.setId(id);
        prompt.setUserId(userId);
        promptService.update(prompt);
        return ResponseBean.success(prompt);
    }

    @DeleteMapping("/{id}")
    public ResponseBean delete(@PathVariable Long id) {
        promptService.delete(id);
        return ResponseBean.success();
    }

    @PostMapping("/{id}/copy")
    public ResponseBean copy(@PathVariable Long id) {
        promptService.incrementHeat(id);
        return ResponseBean.success();
    }

    @GetMapping("/tags")
    public ResponseBean getTags(HttpServletRequest request) {
        String userId = CurrentUser.require(request);
        return ResponseBean.success(promptService.getAllTags(userId));
    }
}
