package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.AccountMapper;
import com.agent.hopaw.infra.model.entity.Account;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountService implements IAccountService {

    private final AccountMapper accountMapper;

    public AccountService(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    @Override
    public List<Account> listAccounts() {
        return accountMapper.findAll();
    }

    @Override
    public Account getById(Long id) {
        return accountMapper.findById(id);
    }

    @Override
    public Account getByUserId(String userId) {
        return accountMapper.findByUserId(userId);
    }

    @Override
    @Transactional
    public int create(Account account) {
        if (account.getStatus() == null) {
            account.setStatus(1);
        }
        return accountMapper.insert(account);
    }

    @Override
    @Transactional
    public int update(Account account) {
        return accountMapper.update(account);
    }

    @Override
    @Transactional
    public int deleteById(Long id) {
        return accountMapper.deleteById(id);
    }

    public boolean existsByUserId(String userId) {
        return accountMapper.countByUserId(userId) > 0;
    }
}
