package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.Prompt;

import java.util.List;
import java.util.Map;

/**
 * 提示词管理服务接口
 */
public interface IPromptService {

    Prompt create(Prompt prompt);

    Prompt update(Prompt prompt);

    void delete(Long id);

    Prompt getById(Long id);

    Map<String, Object> list(String userId, String keyword, String tag, String sortBy, int page, int size);

    void incrementHeat(Long id);

    List<String> getAllTags(String userId);
}
