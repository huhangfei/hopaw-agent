package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.service.ITtsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TTS 服务工厂，根据厂商编号获取对应的 TTS 实现。
 */
@Component
public class TtsServiceFactory {

    private static final Logger logger = LoggerFactory.getLogger(TtsServiceFactory.class);

    private final Map<String, ITtsService> serviceMap = new ConcurrentHashMap<>();

    public TtsServiceFactory(List<ITtsService> ttsServices) {
        for (ITtsService service : ttsServices) {
            serviceMap.put(service.getVendorCode(), service);
            logger.info("已注册 TTS 厂商: {} ({})", service.getVendorCode(), service.getVendorName());
        }
    }

    public ITtsService getService(String vendorCode) {
        ITtsService service = serviceMap.get(vendorCode);
        if (service == null) {
            logger.warn("未找到 TTS 厂商: {}", vendorCode);
        }
        return service;
    }

    public Map<String, String> listVendorNames() {
        Map<String, String> names = new ConcurrentHashMap<>();
        for (Map.Entry<String, ITtsService> entry : serviceMap.entrySet()) {
            names.put(entry.getKey(), entry.getValue().getVendorName());
        }
        return names;
    }
}