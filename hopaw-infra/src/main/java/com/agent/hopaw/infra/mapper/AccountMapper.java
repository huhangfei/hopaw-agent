package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AccountMapper {

    List<Account> findAll();

    Account findById(@Param("id") Long id);

    Account findByUserId(@Param("userId") String userId);

    int insert(Account account);

    int update(Account account);

    int deleteById(@Param("id") Long id);

    int countByUserId(@Param("userId") String userId);
}
