package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.PromptMapper;
import com.agent.hopaw.infra.model.entity.Prompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PromptService implements IPromptService {

    private static final Logger log = LoggerFactory.getLogger(PromptService.class);

    private final PromptMapper promptMapper;

    public PromptService(PromptMapper promptMapper) {
        this.promptMapper = promptMapper;
    }

    @Override
    public Prompt create(Prompt prompt) {
        promptMapper.insert(prompt);
        return prompt;
    }

    @Override
    public Prompt update(Prompt prompt) {
        promptMapper.update(prompt);
        return prompt;
    }

    @Override
    public void delete(Long id) {
        promptMapper.deleteById(id);
    }

    @Override
    public Prompt getById(Long id) {
        return promptMapper.findById(id);
    }

    @Override
    public Map<String, Object> list(String userId, String keyword, String tag, String sortBy, int page, int size) {
        int offset = (page - 1) * size;
        List<Prompt> list = promptMapper.findByUserId(userId, keyword, tag, sortBy, offset, size);
        int total = promptMapper.countByUserId(userId, keyword, tag);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Override
    public void incrementHeat(Long id) {
        promptMapper.incrementHeat(id);
    }

    @Override
    public List<String> getAllTags(String userId) {
        return promptMapper.findAllTags(userId);
    }
}
