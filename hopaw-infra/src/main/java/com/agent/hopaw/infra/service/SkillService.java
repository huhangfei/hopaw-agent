package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.SkillInfo;
import com.github.promeg.pinyinhelper.Pinyin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillService.class);
    private static final String SKILL_MD = "SKILL.md";
    private static final String CONFIG_KEY_SKILL_DIR = "skill.dir";

    private final ISysConfigService sysConfigService;

    public SkillService(ISysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    private Path getSkillDir() {
        String dir = sysConfigService.getValueByKey(CONFIG_KEY_SKILL_DIR, "skills");
        return Paths.get(dir).toAbsolutePath().normalize();
    }

    public List<SkillInfo> listSkills() {
        Path skillDir = getSkillDir();
        if (!Files.isDirectory(skillDir)) {
            return Collections.emptyList();
        }
        try (Stream<Path> dirs = Files.list(skillDir)) {
            return dirs
                    .filter(Files::isDirectory)
                    .map(this::readSkillFromDir)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(s -> s.getName() != null ? s.getName() : ""))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to list skills", e);
            return Collections.emptyList();
        }
    }

    public SkillInfo getSkill(String folderName) {
        Path skillDir = getSkillDir().resolve(folderName);
        if (!Files.isDirectory(skillDir)) {
            return null;
        }
        return readSkillFromDir(skillDir);
    }

    public SkillInfo createSkill(SkillInfo skillInfo) {
        if (skillInfo.getFolderName() == null || skillInfo.getFolderName().isBlank()) {
            if (skillInfo.getSlug() != null && !skillInfo.getSlug().isBlank()) {
                skillInfo.setFolderName(skillInfo.getSlug());
            } else {
                skillInfo.setFolderName(toFolderName(skillInfo.getName()));
            }
        }
        Path skillDir = getSkillDir().resolve(skillInfo.getFolderName());
        try {
            Files.createDirectories(skillDir);
            writeSkillMd(skillDir, skillInfo);
            return readSkillFromDir(skillDir);
        } catch (IOException e) {
            log.error("Failed to create skill: {}", skillInfo.getFolderName(), e);
            throw new RuntimeException("创建技能失败: " + e.getMessage());
        }
    }

    public SkillInfo updateSkill(String folderName, SkillInfo skillInfo) {
        Path skillDir = getSkillDir().resolve(folderName);
        if (!Files.isDirectory(skillDir)) {
            throw new RuntimeException("技能不存在: " + folderName);
        }
        try {
            writeSkillMd(skillDir, skillInfo);
            return readSkillFromDir(skillDir);
        } catch (IOException e) {
            log.error("Failed to update skill: {}", folderName, e);
            throw new RuntimeException("更新技能失败: " + e.getMessage());
        }
    }

    public void deleteSkill(String folderName) {
        Path skillDir = getSkillDir().resolve(folderName);
        if (!Files.isDirectory(skillDir)) {
            throw new RuntimeException("技能不存在: " + folderName);
        }
        try {
            try (Stream<Path> files = Files.walk(skillDir)) {
                files.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.warn("Failed to delete: {}", path, e);
                            }
                        });
            }
        } catch (IOException e) {
            log.error("Failed to delete skill: {}", folderName, e);
            throw new RuntimeException("删除技能失败: " + e.getMessage());
        }
    }

    private SkillInfo readSkillFromDir(Path skillDir) {
        Path skillMd = skillDir.resolve(SKILL_MD);
        if (!Files.isRegularFile(skillMd)) {
            return null;
        }
        try {
            String content = Files.readString(skillMd);
            SkillInfo info = parseSkillMd(content);
            info.setFolderName(skillDir.getFileName().toString());
            info.setContent(content);
            return info;
        } catch (IOException e) {
            log.warn("Failed to read SKILL.md from: {}", skillDir, e);
            return null;
        }
    }

    SkillInfo parseSkillMd(String content) {
        SkillInfo info = new SkillInfo();
        info.setContent(content);

        String trimmed = content.stripLeading();
        if (!trimmed.startsWith("---")) {
            String firstLine = content.lines().filter(l -> !l.isBlank()).findFirst().orElse("").trim();
            if (firstLine.startsWith("#")) {
                firstLine = firstLine.replaceAll("^#+\\s*", "");
            }
            info.setName(firstLine);
            info.setDescription("");
            return info;
        }

        int firstDelim = content.indexOf("---");
        int secondDelim = content.indexOf("---", firstDelim + 3);

        if (secondDelim == -1) {
            info.setName(content.substring(firstDelim + 3).trim());
            info.setDescription("");
            return info;
        }

        String yamlBlock = content.substring(firstDelim + 3, secondDelim).trim();
        Map<String, String> frontmatter = parseSimpleYaml(yamlBlock);

        info.setName(frontmatter.getOrDefault("name", ""));
        info.setDescription(frontmatter.getOrDefault("description", ""));
        info.setLicense(frontmatter.get("license"));
        info.setCompatibility(frontmatter.get("compatibility"));
        info.setVersion(frontmatter.get("version"));
        info.setHomepage(frontmatter.get("homepage"));
        info.setChangelog(frontmatter.get("changelog"));
        info.setSlug(frontmatter.get("slug"));

        Map<String, String> metadata = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : frontmatter.entrySet()) {
            String key = entry.getKey();
            if (!key.equals("name") && !key.equals("description")
                    && !key.equals("license") && !key.equals("compatibility")
                    && !key.equals("version") && !key.equals("homepage") && !key.equals("changelog")
                    && !key.equals("slug")) {
                metadata.put(key, entry.getValue());
            }
        }
        if (!metadata.isEmpty()) {
            info.setMetadata(metadata);
        }

        return info;
    }

    private Map<String, String> parseSimpleYaml(String yaml) {
        Map<String, String> result = new LinkedHashMap<>();
        String currentKey = null;
        StringBuilder currentValue = new StringBuilder();
        int baseIndent = -1;

        for (String line : yaml.split("\n")) {
            if (line.isBlank()) {
                if (currentKey != null) {
                    currentValue.append("\n");
                }
                continue;
            }

            int indent = countLeadingSpaces(line);
            String trimmedLine = line.strip();

            if (currentKey != null && indent > baseIndent) {
                if (currentValue.length() > 0) {
                    currentValue.append("\n");
                }
                currentValue.append(trimmedLine);
                continue;
            }

            if (currentKey != null) {
                result.put(currentKey, currentValue.toString().trim());
                currentKey = null;
                currentValue = new StringBuilder();
            }

            if (trimmedLine.startsWith("#")) {
                continue;
            }

            int colonIdx = trimmedLine.indexOf(':');
            if (colonIdx > 0) {
                baseIndent = indent;
                currentKey = trimmedLine.substring(0, colonIdx).trim();
                String valuePart = trimmedLine.substring(colonIdx + 1).trim();
                if (valuePart.isEmpty() || valuePart.equals("|") || valuePart.equals(">")) {
                    currentValue = new StringBuilder();
                } else {
                    currentValue = new StringBuilder(valuePart);
                }
            }
        }

        if (currentKey != null) {
            result.put(currentKey, currentValue.toString().trim());
        }

        return result;
    }

    private int countLeadingSpaces(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') count++;
            else break;
        }
        return count;
    }

    private void writeSkillMd(Path skillDir, SkillInfo skillInfo) throws IOException {
        String name = skillInfo.getName() != null ? skillInfo.getName() : skillInfo.getFolderName();
        String description = skillInfo.getDescription() != null ? skillInfo.getDescription() : "";

        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("name: ").append(name).append("\n");
        sb.append("description: ").append(description).append("\n");
        if (skillInfo.getSlug() != null && !skillInfo.getSlug().isBlank()) {
            sb.append("slug: ").append(skillInfo.getSlug()).append("\n");
        }
        if (skillInfo.getLicense() != null && !skillInfo.getLicense().isBlank()) {
            sb.append("license: ").append(skillInfo.getLicense()).append("\n");
        }
        if (skillInfo.getCompatibility() != null && !skillInfo.getCompatibility().isBlank()) {
            sb.append("compatibility: ").append(skillInfo.getCompatibility()).append("\n");
        }
        if (skillInfo.getVersion() != null && !skillInfo.getVersion().isBlank()) {
            sb.append("version: ").append(skillInfo.getVersion()).append("\n");
        }
        if (skillInfo.getHomepage() != null && !skillInfo.getHomepage().isBlank()) {
            sb.append("homepage: ").append(skillInfo.getHomepage()).append("\n");
        }
        if (skillInfo.getChangelog() != null && !skillInfo.getChangelog().isBlank()) {
            sb.append("changelog: ").append(skillInfo.getChangelog()).append("\n");
        }
        if (skillInfo.getMetadata() != null && !skillInfo.getMetadata().isEmpty()) {
            sb.append("metadata:\n");
            for (Map.Entry<String, String> entry : skillInfo.getMetadata().entrySet()) {
                sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }
        sb.append("---\n");

        String body = skillInfo.getContent();
        if (body != null && !body.isBlank()) {
            int secondDelim = body.indexOf("---", body.indexOf("---") + 3);
            if (body.stripLeading().startsWith("---") && secondDelim != -1) {
                sb.append(body.substring(secondDelim + 3).stripLeading());
            } else {
                sb.append("\n").append(body.strip());
            }
            sb.append("\n");
        }

        Files.writeString(skillDir.resolve(SKILL_MD), sb.toString());
    }

    private String toFolderName(String name) {
        if (name == null || name.isBlank()) {
            return "skill-" + System.currentTimeMillis() % 100000;
        }
        String slug = name.trim().toLowerCase()
                .replaceAll("[^a-z0-9\\s\\-\\u4e00-\\u9fa5]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        if (slug.length() > 50) {
            slug = slug.substring(0, 50).replaceAll("-$", "");
        }
        if (containsChinese(slug)) {
            slug = toPinyinSlug(slug);
        }
        if (slug.isBlank()) {
            slug = "skill-" + System.currentTimeMillis() % 100000;
        }
        return slug;
    }

    private boolean containsChinese(String s) {
        for (char c : s.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fa5') {
                return true;
            }
        }
        return false;
    }

    private String toPinyinSlug(String name) {
        StringBuilder result = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fa5') {
                try {
                    String py = Pinyin.toPinyin(c);
                    if (result.length() > 0 && result.charAt(result.length() - 1) != '-') {
                        result.append('-');
                    }
                    result.append(py);
                } catch (Exception e) {
                    // skip character if pinyin conversion fails
                }
            } else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                result.append(c);
            } else if (c == '-' || c == ' ') {
                if (result.length() > 0 && result.charAt(result.length() - 1) != '-') {
                    result.append('-');
                }
            }
        }
        String slug = result.toString().toLowerCase()
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        if (slug.length() > 50) {
            slug = slug.substring(0, 50).replaceAll("-$", "");
        }
        return slug;
    }
}