package com.agent.hopaw.config;

import com.agent.hopaw.model.ChatModelFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class ChatModelFactoryConfig {

    private final Map<String, ChatModelFactory> factories = new HashMap<>();

    @Value("${api.provider:openai}")
    private String activeProvider;

    public ChatModelFactoryConfig(List<ChatModelFactory> factoryList) {
        for (ChatModelFactory factory : factoryList) {
            factories.put(factory.getProviderName().toLowerCase(), factory);
        }
    }

    @PostConstruct
    public void init() {
        if (!factories.containsKey(activeProvider.toLowerCase())) {
            throw new IllegalStateException("Unknown API provider: " + activeProvider +
                    ". Available providers: " + factories.keySet());
        }
    }

    public ChatModelFactory getFactory() {
        return factories.get(activeProvider.toLowerCase());
    }

    public ChatModelFactory getFactory(String provider) {
        return factories.get(provider.toLowerCase());
    }

    public Map<String, ChatModelFactory> getAllFactories() {
        return new HashMap<>(factories);
    }

    public String getActiveProvider() {
        return activeProvider;
    }
}