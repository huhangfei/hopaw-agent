package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.PluginRepoResult;

import java.util.List;

public interface IPluginStoreService {

    List<PluginRepoResult> fetchStorePlugins();
}
