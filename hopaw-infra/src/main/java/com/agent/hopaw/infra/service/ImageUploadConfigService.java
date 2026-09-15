package com.agent.hopaw.infra.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 图片上传压缩配置：是否开启压缩 + 按文件大小分档的压缩质量设置，
 * 从系统配置读取（设置页"图片上传"可调），每次上传时实时读取，保存后立即生效。
 */
@Service
public class ImageUploadConfigService {
    private static final Logger logger = LoggerFactory.getLogger(ImageUploadConfigService.class);

    /** 配置项：是否开启图片压缩（1=开启，0=关闭，默认关闭） */
    public static final String CONFIG_COMPRESS_ENABLED = "image_upload_compress_enabled";
    /** 配置项：压缩分档设置，JSON 数组 [{"minSize":100,"quality":80}]，minSize 单位 KB，quality 为 1~100 */
    public static final String CONFIG_COMPRESS_TIERS = "image_compress_tiers";

    /** 默认分档（KB → 压缩质量%）：100KB 起压 80%，512KB 起压 60%，1MB 起压 40% */
    private static final String DEFAULT_TIERS_JSON = "[{\"minSize\":100,\"quality\":80},{\"minSize\":512,\"quality\":60},{\"minSize\":1024,\"quality\":40}]";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ISysConfigService sysConfigService;

    public ImageUploadConfigService(ISysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    /** 是否开启图片压缩（配置缺失或非法时默认关闭） */
    public boolean isCompressEnabled() {
        try {
            return "1".equals(sysConfigService.getValueByKey(CONFIG_COMPRESS_ENABLED, "0").trim());
        } catch (Exception e) {
            logger.warn("读取图片压缩开关失败，默认关闭: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取压缩分档（按 minSize 升序排序）。
     * 配置缺失或非法时回退默认分档；过滤非法档位（minSize <= 0 或 quality 超范围）。
     */
    public List<CompressTier> getCompressTiers() {
        String raw;
        try {
            raw = sysConfigService.getValueByKey(CONFIG_COMPRESS_TIERS, DEFAULT_TIERS_JSON);
        } catch (Exception e) {
            logger.warn("读取图片压缩分档配置失败，使用默认分档: {}", e.getMessage());
            return parseTiers(DEFAULT_TIERS_JSON);
        }
        List<CompressTier> tiers = parseTiers(raw);
        return tiers.isEmpty() ? parseTiers(DEFAULT_TIERS_JSON) : tiers;
    }

    /**
     * 按文件大小匹配压缩质量：取满足 fileSizeKB >= minSize 的最大档（文件越大质量越低）。
     *
     * @return 匹配的压缩质量（0~1）；无匹配档位（文件过小）返回 null 表示不压缩
     */
    public Float matchQuality(long fileSizeBytes) {
        long fileSizeKB = fileSizeBytes / 1024;
        Float quality = null;
        for (CompressTier tier : getCompressTiers()) {
            if (fileSizeKB >= tier.getMinSize()) {
                quality = tier.getQuality() / 100f;
            }
        }
        return quality;
    }

    private List<CompressTier> parseTiers(String json) {
        List<CompressTier> result = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return result;
        }
        try {
            List<?> items = objectMapper.readValue(json, List.class);
            for (Object item : items) {
                if (!(item instanceof java.util.Map)) {
                    continue;
                }
                Object minSize = ((java.util.Map<?, ?>) item).get("minSize");
                Object quality = ((java.util.Map<?, ?>) item).get("quality");
                if (minSize == null || quality == null) {
                    continue;
                }
                long minSizeVal = Long.parseLong(String.valueOf(minSize));
                int qualityVal = Integer.parseInt(String.valueOf(quality));
                if (minSizeVal > 0 && qualityVal >= 1 && qualityVal <= 100) {
                    result.add(new CompressTier(minSizeVal, qualityVal));
                }
            }
        } catch (Exception e) {
            logger.warn("图片压缩分档配置解析失败，使用默认分档: {}", e.getMessage());
            return new ArrayList<>();
        }
        result.sort(Comparator.comparingLong(CompressTier::getMinSize));
        return result;
    }

    /** 压缩分档：文件最小大小（KB）与对应压缩质量（1~100） */
    public static class CompressTier {
        private final long minSize;
        private final int quality;

        public CompressTier(long minSize, int quality) {
            this.minSize = minSize;
            this.quality = quality;
        }

        public long getMinSize() {
            return minSize;
        }

        public int getQuality() {
            return quality;
        }
    }
}
