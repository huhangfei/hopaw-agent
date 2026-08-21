package com.agent.hopaw.controller;

import com.agent.hopaw.infra.constant.TaskCommenterTypeEnum;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.service.ITaskCommentService;
import com.agent.hopaw.util.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@Controller
public class TaskCommentController {
    private final ITaskCommentService commentService;

    public TaskCommentController(ITaskCommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/api/workflow/tasks/{taskId}/comments")
    @ResponseBody
    public ResponseBean getComments(@PathVariable Long taskId) {
        return ResponseBean.success(commentService.getCommentsByTaskId(taskId));
    }

    @PostMapping("/api/workflow/tasks/{taskId}/comments")
    @ResponseBody
    public ResponseBean addComment(HttpServletRequest request, @PathVariable Long taskId, @RequestBody Map<String, String> body) {
        String userId = CurrentUser.require(request);
        String content = body.get("content");
        String commentType = body.get("commentType");
        return ResponseBean.success(commentService.addComment(taskId, content, userId,
                TaskCommenterTypeEnum.USER.getCode(), userId, commentType));
    }

    @DeleteMapping("/api/workflow/comments/{id}")
    @ResponseBody
    public ResponseBean deleteComment(HttpServletRequest request, @PathVariable Long id) {
        String userId = CurrentUser.require(request);
        try {
            commentService.deleteComment(id, userId);
            return ResponseBean.success();
        } catch (Exception e) {
            return ResponseBean.fail(e.getMessage());
        }
    }
}
