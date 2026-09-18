package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.Account;
import com.agent.hopaw.infra.service.AccountService;
import com.agent.hopaw.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ResponseBean list() {
        List<Account> list = accountService.listAccounts();
        return ResponseBean.success(list);
    }

    @GetMapping("/{id}")
    public ResponseBean getById(@PathVariable Long id) {
        Account account = accountService.getById(id);
        if (account == null) {
            return ResponseBean.fail("账户不存在");
        }
        return ResponseBean.success(account);
    }

    @PostMapping
    public ResponseBean create(@RequestBody Account account) {
        if (account.getUserId() == null || account.getUserId().isBlank()) {
            return ResponseBean.fail("用户编号不能为空");
        }
        if (account.getUsername() == null || account.getUsername().isBlank()) {
            return ResponseBean.fail("用户名称不能为空");
        }
        if (accountService.existsByUserId(account.getUserId())) {
            return ResponseBean.fail("用户编号已存在: " + account.getUserId());
        }
        accountService.create(account);
        return ResponseBean.success(account);
    }

    @PutMapping("/{id}")
    public ResponseBean update(@PathVariable Long id, @RequestBody Account account) {
        Account existing = accountService.getById(id);
        if (existing == null) {
            return ResponseBean.fail("账户不存在");
        }
        if (account.getUserId() == null || account.getUserId().isBlank()) {
            return ResponseBean.fail("用户编号不能为空");
        }
        if (account.getUsername() == null || account.getUsername().isBlank()) {
            return ResponseBean.fail("用户名称不能为空");
        }
        // 用户编号修改后需要再次校验唯一
        if (!existing.getUserId().equals(account.getUserId())
                && accountService.existsByUserId(account.getUserId())) {
            return ResponseBean.fail("用户编号已存在: " + account.getUserId());
        }
        account.setId(id);
        accountService.update(account);
        return ResponseBean.success(account);
    }

    @DeleteMapping("/{id}")
    public ResponseBean delete(@PathVariable Long id) {
        Account existing = accountService.getById(id);
        if (existing == null) {
            return ResponseBean.fail("账户不存在");
        }
        if ("user1".equals(existing.getUserId())) {
            return ResponseBean.fail("默认账户不可删除");
        }
        accountService.deleteById(id);
        return ResponseBean.success();
    }

    /**
     * 设置账户密码（启用密码时必须提供密码，禁用密码时清除密码）
     */
    @PutMapping("/{id}/password")
    public ResponseBean setPassword(@PathVariable Long id, @RequestBody java.util.Map<String, Object> body) {
        Account existing = accountService.getById(id);
        if (existing == null) {
            return ResponseBean.fail("账户不存在");
        }
        Integer passwordEnabled = body.get("passwordEnabled") != null
                ? Integer.parseInt(body.get("passwordEnabled").toString()) : null;
        String password = body.get("password") != null ? body.get("password").toString() : null;

        if (passwordEnabled != null && passwordEnabled == 1) {
            if (password == null || password.isBlank()) {
                return ResponseBean.fail("启用密码时必须设置密码");
            }
            existing.setPasswordEnabled(1);
            existing.setPassword(PasswordUtil.hash(password));
        } else {
            existing.setPasswordEnabled(0);
            existing.setPassword(null);
        }
        accountService.update(existing);
        return ResponseBean.success();
    }
}
