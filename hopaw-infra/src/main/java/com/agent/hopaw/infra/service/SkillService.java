package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.SkillInfo;
import com.github.promeg.pinyinhelper.Pinyin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;

@Service
public class SkillService implements ISkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillService.class);
    private static final String SKILL_MD = "SKILL.md";
    private static final String CONFIG_KEY_SKILL_DIR = "skill.dir";

    private final ISysConfigService sysConfigService;

    public SkillService(ISysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    @Override
    public Path getSkillDir() {
        String dir = sysConfigService.getValueByKey(SkillService.CONFIG_KEY_SKILL_DIR, "skills");
        return Paths.get(dir).toAbsolutePath().normalize();
    }

    @Override
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

    @Override
    public SkillInfo getSkill(String folderName) {
        Path skillDir = getSkillDir().resolve(folderName);
        if (!Files.isDirectory(skillDir)) {
            return null;
        }
        return readSkillFromDir(skillDir);
    }

    @Override
    public SkillInfo createSkill(SkillInfo skillInfo) {
        if (skillInfo.getVersion() == null || skillInfo.getVersion().isBlank()) {
            skillInfo.setVersion("1.0.0");
        }
        String slug = (skillInfo.getSlug() != null && !skillInfo.getSlug().isBlank())
                ? skillInfo.getSlug()
                : toFolderName(skillInfo.getName());
        skillInfo.setFolderName(buildFolderName(slug, skillInfo.getVersion()));
        Path skillDir = getSkillDir().resolve(skillInfo.getFolderName());
        if (Files.exists(skillDir)) {
            throw new RuntimeException("技能目录已存在: " + skillInfo.getFolderName());
        }
        try {
            Files.createDirectories(skillDir);
            writeSkillMd(skillDir, skillInfo);
            return readSkillFromDir(skillDir);
        } catch (IOException e) {
            log.error("Failed to create skill: {}", skillInfo.getFolderName(), e);
            throw new RuntimeException("创建技能失败: " + e.getMessage());
        }
    }

    @Override
    public SkillInfo updateSkill(String folderName, SkillInfo skillInfo) {
        if (skillInfo.getVersion() == null || skillInfo.getVersion().isBlank()) {
            skillInfo.setVersion("1.0.0");
        }
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

    @Override
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

    @Override
    public SkillInfo readSkillFromDir(Path skillDir) {
        Path skillMd = skillDir.resolve(SkillService.SKILL_MD);
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
            SkillService.log.warn("Failed to read SKILL.md from: {}", skillDir, e);
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
        info.setRawFrontmatter(yamlBlock);

        Map<String, String> frontmatter = parseSimpleYaml(yamlBlock);

        info.setName(frontmatter.getOrDefault("name", ""));
        info.setDescription(frontmatter.getOrDefault("description", ""));
        info.setLicense(frontmatter.get("license"));
        info.setCompatibility(frontmatter.get("compatibility"));
        info.setVersion(frontmatter.get("version"));
        info.setHomepage(frontmatter.get("homepage"));
        info.setChangelog(frontmatter.get("changelog"));
        info.setSlug(frontmatter.get("slug"));

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
            if (c == ' ') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private void writeSkillMd(Path skillDir, SkillInfo skillInfo) throws IOException {
        String name = skillInfo.getName() != null ? skillInfo.getName() : skillInfo.getFolderName();
        String description = skillInfo.getDescription() != null ? skillInfo.getDescription() : "";

        String rawFrontmatter = extractRawFrontmatter(skillInfo.getContent());
        String body = extractBody(skillInfo.getContent());

        StringBuilder sb = new StringBuilder();
        sb.append("---\n");

        if (rawFrontmatter != null && !rawFrontmatter.isBlank()) {
            sb.append(applyFrontmatterFields(rawFrontmatter, skillInfo));
        } else {
            sb.append("name: ").append(name).append("\n");
            sb.append("description: ").append(description).append("\n");
            appendFrontmatterField(sb, "slug", skillInfo.getSlug());
            appendFrontmatterField(sb, "license", skillInfo.getLicense());
            appendFrontmatterField(sb, "compatibility", skillInfo.getCompatibility());
            appendFrontmatterField(sb, "version", skillInfo.getVersion());
            appendFrontmatterField(sb, "homepage", skillInfo.getHomepage());
            appendFrontmatterField(sb, "changelog", skillInfo.getChangelog());
        }

        sb.append("---\n");
        if (body != null && !body.isBlank()) {
            sb.append(body).append("\n");
        } else {
            sb.append("\n");
        }

        Files.writeString(skillDir.resolve(SKILL_MD), sb.toString());
    }

    private String extractRawFrontmatter(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String trimmed = content.stripLeading();
        if (!trimmed.startsWith("---")) {
            return null;
        }
        int firstDelim = content.indexOf("---");
        int secondDelim = content.indexOf("---", firstDelim + 3);
        if (secondDelim == -1) {
            return null;
        }
        return content.substring(firstDelim + 3, secondDelim).trim();
    }

    private String extractBody(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String trimmed = content.stripLeading();
        if (!trimmed.startsWith("---")) {
            return content;
        }
        int firstDelim = content.indexOf("---");
        int secondDelim = content.indexOf("---", firstDelim + 3);
        if (secondDelim == -1) {
            return content.substring(firstDelim + 3).trim();
        }
        return content.substring(secondDelim + 3).stripLeading();
    }

    private String applyFrontmatterFields(String raw, SkillInfo skillInfo) {
        List<String> knownKeys = Arrays.asList(
                "name", "description", "slug", "license",
                "compatibility", "version", "homepage", "changelog"
        );
        Set<String> knownKeySet = new HashSet<>(knownKeys);

        Map<String, String> newValues = new LinkedHashMap<>();
        newValues.put("name", skillInfo.getName());
        newValues.put("description", skillInfo.getDescription());
        newValues.put("slug", skillInfo.getSlug());
        newValues.put("license", skillInfo.getLicense());
        newValues.put("compatibility", skillInfo.getCompatibility());
        newValues.put("version", skillInfo.getVersion());
        newValues.put("homepage", skillInfo.getHomepage());
        newValues.put("changelog", skillInfo.getChangelog());

        StringBuilder sb = new StringBuilder();
        List<String> lines = raw.lines().collect(Collectors.toList());
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (line.isBlank()) {
                sb.append(line).append("\n");
                i++;
                continue;
            }

            int indent = countLeadingSpaces(line);
            if (indent > 0) {
                i++;
                continue;
            }

            String trimmedLine = line.stripLeading();
            if (trimmedLine.startsWith("#")) {
                sb.append(line).append("\n");
                i++;
                continue;
            }

            int colonIdx = trimmedLine.indexOf(':');
            if (colonIdx <= 0) {
                sb.append(line).append("\n");
                i++;
                continue;
            }

            String key = trimmedLine.substring(0, colonIdx).trim();
            if (knownKeySet.contains(key)) {
                i = skipFieldLines(lines, i);
                String newValue = newValues.get(key);
                if (newValue != null && !newValue.isBlank()) {
                    if (newValue.contains("\n")) {
                        sb.append(key).append(": |\n");
                        for (String vl : newValue.split("\n")) {
                            sb.append("  ").append(vl).append("\n");
                        }
                    } else {
                        sb.append(key).append(": ").append(newValue).append("\n");
                    }
                }
            } else {
                i = copyFieldLines(lines, i, sb);
            }
        }

        return sb.toString().stripTrailing();
    }

    private int skipFieldLines(List<String> lines, int startIdx) {
        int i = startIdx + 1;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (line.isBlank() || countLeadingSpaces(line) > 0) {
                i++;
            } else {
                String tl = line.stripLeading();
                if (tl.startsWith("#")) {
                    i++;
                } else {
                    break;
                }
            }
        }
        return i;
    }

    private int copyFieldLines(List<String> lines, int startIdx, StringBuilder sb) {
        int i = startIdx;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (i > startIdx && !line.isBlank() && countLeadingSpaces(line) == 0) {
                String tl = line.stripLeading();
                if (!tl.startsWith("#") && tl.indexOf(':') > 0) {
                    break;
                }
            }
            sb.append(line).append("\n");
            i++;
        }
        return i;
    }

    private void appendFrontmatterField(StringBuilder sb, String key, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(key).append(": ").append(value).append("\n");
        }
    }

    public SkillInfo importSkill(byte[] fileBytes, String originalFilename) throws IOException {
        if (originalFilename == null) {
            throw new RuntimeException("文件名无效");
        }
        String lowerName = originalFilename.toLowerCase();

        if (lowerName.endsWith(".zip")) {
            return importFromZip(fileBytes);
        } else if (lowerName.endsWith(".md")) {
            return importFromMd(fileBytes, originalFilename);
        } else {
            throw new RuntimeException("不支持的文件格式: " + originalFilename);
        }
    }

    private SkillInfo importFromZip(byte[] fileBytes) throws IOException {
        Path tempDir = Files.createTempDirectory("skill-import-");
        try {
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(fileBytes))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String entryName = entry.getName();
                    int slashIdx = entryName.indexOf('/');
                    if (slashIdx > 0) {
                        entryName = entryName.substring(slashIdx + 1);
                    }
                    Path targetPath = tempDir.resolve(entryName);
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    zis.closeEntry();
                }
            }

            Path skillMd = findSkillMdInDir(tempDir);
            if (skillMd == null) {
                throw new RuntimeException("ZIP 文件中未找到 SKILL.md");
            }

            Path skillDir = createSkillDirFromMd(skillMd);
            copyDir(tempDir, skillDir);

            return readSkillFromDir(skillDir);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private SkillInfo importFromMd(byte[] fileBytes, String originalFilename) throws IOException {
        String content = new String(fileBytes);
        SkillInfo info = parseSkillMd(content);
        String slug;
        if (info.getSlug() != null && !info.getSlug().isBlank()) {
            slug = info.getSlug();
        } else if (info.getName() != null && !info.getName().isBlank()) {
            slug = toFolderName(info.getName());
        } else {
            slug = originalFilename.replaceFirst("(?i)\\.md$", "");
        }
        String version = (info.getVersion() != null && !info.getVersion().isBlank())
                ? info.getVersion() : "1.0.0";
        String folderName = buildFolderName(slug, version);

        Path skillDir = getSkillDir().resolve(folderName);
        if (Files.exists(skillDir)) {
            throw new RuntimeException("技能目录已存在: " + folderName);
        }
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve(SKILL_MD), content);

        return readSkillFromDir(skillDir);
    }

    private Path findSkillMdInDir(Path dir) throws IOException {
        Path direct = dir.resolve(SKILL_MD);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            List<Path> subDirs = entries.filter(Files::isDirectory).collect(Collectors.toList());
            for (Path subDir : subDirs) {
                Path nested = subDir.resolve(SKILL_MD);
                if (Files.isRegularFile(nested)) {
                    return nested;
                }
            }
        }
        return null;
    }

    private Path createSkillDirFromMd(Path skillMd) throws IOException {
        String content = Files.readString(skillMd);
        SkillInfo info = parseSkillMd(content);
        String slug;
        if (info.getSlug() != null && !info.getSlug().isBlank()) {
            slug = info.getSlug();
        } else if (info.getName() != null && !info.getName().isBlank()) {
            slug = toFolderName(info.getName());
        } else {
            slug = toFolderName(skillMd.getParent().getFileName().toString());
        }
        String version = (info.getVersion() != null && !info.getVersion().isBlank())
                ? info.getVersion() : "1.0.0";
        String folderName = buildFolderName(slug, version);

        Path skillDir = getSkillDir().resolve(folderName);
        if (Files.exists(skillDir)) {
            throw new RuntimeException("技能目录已存在: " + folderName);
        }
        Files.createDirectories(skillDir);
        return skillDir;
    }

    private void copyDir(Path source, Path target) throws IOException {
        try (Stream<Path> files = Files.walk(source)) {
            files.forEach(sourcePath -> {
                try {
                    Path targetPath = target.resolve(source.relativize(sourcePath));
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private String buildFolderName(String slug, String version) {
        String ver = (version != null && !version.isBlank()) ? version : "1.0.0";
        return slug + "-" + ver;
    }

    private void deleteRecursively(Path dir) {
        try {
            try (Stream<Path> files = Files.walk(dir)) {
                files.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.warn("Failed to delete temp file: {}", path, e);
                            }
                        });
            }
        } catch (IOException e) {
            log.warn("Failed to clean up temp dir: {}", dir, e);
        }
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