package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.Account;

import java.util.List;

public interface IAccountService {

    List<Account> listAccounts();

    Account getById(Long id);

    Account getByUserId(String userId);

    int create(Account account);

    int update(Account account);

    int deleteById(Long id);
}
