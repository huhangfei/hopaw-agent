package com.agent.hopaw.tool.file;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import com.agent.hopaw.infra.model.dto.ToolConfigItem;
import com.agent.hopaw.infra.model.dto.ValidationRule;
import com.agent.hopaw.infra.model.entity.SysConfig;
import com.agent.hopaw.infra.service.ISysConfigService;
import com.agent.hopaw.infra.tool.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

public class FileOperationTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(FileOperationTool.class);
    private static final String CONFIG_KEY_MAX_THREADS = "maxThreads";
    private static final String CONFIG_KEY_MAX_RESULTS = "maxResults";
    private static final int DEFAULT_MAX_THREADS = 4;
    private static final int DEFAULT_MAX_RESULTS = 10;

    @Autowired
    private ISysConfigService sysConfigService;

    private volatile int maxThreads = DEFAULT_MAX_THREADS;
    private volatile int maxResults = DEFAULT_MAX_RESULTS;
    private volatile boolean configLoaded = false;

    private void ensureConfigLoaded() {
        if (!configLoaded) {
            synchronized (this) {
                if (!configLoaded) {
                    loadConfig();
                    configLoaded = true;
                }
            }
        }
    }

    private void loadConfig() {
        if (sysConfigService == null) {
            return;
        }
        SysConfig threadsConfig = sysConfigService.getByKey(getConfigPrefix()+ CONFIG_KEY_MAX_THREADS);
        if (threadsConfig != null && threadsConfig.getConfigValue() != null && !threadsConfig.getConfigValue().isBlank()) {
            try {
                int val = Integer.parseInt(threadsConfig.getConfigValue().trim());
                maxThreads = Math.max(1, Math.min(val, 32));
            } catch (NumberFormatException e) {
                log.warn("无效的maxThreads配置: {}, 使用默认值 {}", threadsConfig.getConfigValue(), DEFAULT_MAX_THREADS);
                maxThreads = DEFAULT_MAX_THREADS;
            }
        }
        SysConfig resultsConfig = sysConfigService.getByKey(getConfigPrefix()+CONFIG_KEY_MAX_RESULTS);
        if (resultsConfig != null && resultsConfig.getConfigValue() != null && !resultsConfig.getConfigValue().isBlank()) {
            try {
                int val = Integer.parseInt(resultsConfig.getConfigValue().trim());
                maxResults = Math.max(0, Math.min(val, 1000));
            } catch (NumberFormatException e) {
                log.warn("无效的maxResults配置: {}, 使用默认值 {}", resultsConfig.getConfigValue(), DEFAULT_MAX_RESULTS);
                maxResults = DEFAULT_MAX_RESULTS;
            }
        }
    }

    @Tool(value = {"读取文本文件内容", "文件读取"})
    public String readFile(@P(description = "文件路径") String filePath) {
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                return "错误: 文件不存在: " + filePath;
            }
            if (!Files.isRegularFile(path)) {
                return "错误: 路径不是文件: " + filePath;
            }

            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }

            String result = content.toString();
            return result.isEmpty() ? "(空文件)" : result;
        } catch (IOException e) {
            log.error("读取文件失败: {}", filePath, e);
            return "错误: 读取文件失败 - " + e.getMessage();
        }
    }

    @Tool(value = {"按行读取文本文件内容，返回带行号的结果", "文件读取"})
    public String readFileByLine(
            @P(description = "文件路径") String filePath,
            @P(description = "起始行号(从1开始)，为空表示从头开始", required = false) Integer startLine,
            @P(description = "结束行号，为空表示读到最后", required = false) Integer endLine) {
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                return "错误: 文件不存在: " + filePath;
            }
            if (!Files.isRegularFile(path)) {
                return "错误: 路径不是文件: " + filePath;
            }

            int start = startLine != null && startLine > 0 ? startLine : 1;
            int end = endLine != null && endLine > 0 ? endLine : Integer.MAX_VALUE;

            if (start > end) {
                return "错误: 起始行号不能大于结束行号";
            }

            StringBuilder result = new StringBuilder();
            int currentLine = 0;

            try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    currentLine++;
                    if (currentLine >= start && currentLine <= end) {
                        result.append(String.format("%6d: %s%n", currentLine, line));
                    }
                    if (currentLine > end) {
                        break;
                    }
                }
            }

            if (result.length() == 0) {
                return "文件为空或指定行范围无内容";
            }

            return result.toString();
        } catch (IOException e) {
            log.error("按行读取文件失败: {}", filePath, e);
            return "错误: 读取文件失败 - " + e.getMessage();
        }
    }

    @Tool(value = {"写入内容到文本文件，会覆盖原文件", "文件写入"})
    public String writeFile(
            @P(description = "文件路径") String filePath,
            @P(description = "要写入的内容") String content) {
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();

            File parentDir = path.getParent().toFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    return "错误: 无法创建目录: " + parentDir.getAbsolutePath();
                }
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
                writer.write(content);
            }

            return "成功写入文件: " + filePath;
        } catch (IOException e) {
            log.error("写入文件失败: {}", filePath, e);
            return "错误: 写入文件失败 - " + e.getMessage();
        }
    }

    @Tool(value = {"追加内容到文本文件末尾", "文件写入"})
    public String appendFile(
            @P(description = "文件路径") String filePath,
            @P(description = "要追加的内容") String content) {
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();

            File parentDir = path.getParent().toFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    return "错误: 无法创建目录: " + parentDir.getAbsolutePath();
                }
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile(), true))) {
                writer.write(content);
                writer.newLine();
            }

            return "成功追加内容到文件: " + filePath;
        } catch (IOException e) {
            log.error("追加文件内容失败: {}", filePath, e);
            return "错误: 追加文件内容失败 - " + e.getMessage();
        }
    }

    @Tool(value = {"按行写入内容到文本文件，会覆盖原文件", "文件写入"})
    public String writeFileByLine(
            @P(description = "文件路径") String filePath,
            @P(description = "要写入的行内容列表，每行一个元素，用逗号分隔") String lines) {
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();

            File parentDir = path.getParent().toFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    return "错误: 无法创建目录: " + parentDir.getAbsolutePath();
                }
            }

            String[] lineArray = lines.split(",");

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
                for (String line : lineArray) {
                    writer.write(line.trim());
                    writer.newLine();
                }
            }

            return "成功写入 " + lineArray.length + " 行到文件: " + filePath;
        } catch (IOException e) {
            log.error("按行写入文件失败: {}", filePath, e);
            return "错误: 写入文件失败 - " + e.getMessage();
        }
    }

    @Tool(value = {"在指定位置插入行到文本文件", "文件写入"})
    public String insertLine(
            @P(description = "文件路径") String filePath,
            @P(description = "要插入的内容") String content,
            @P(description = "插入位置行号(从1开始)，0表示在文件开头插入") Integer lineNumber) {
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                return "错误: 文件不存在: " + filePath;
            }

            List<String> allLines = Files.readAllLines(path);

            int insertPos = lineNumber != null && lineNumber > 0 ? lineNumber - 1 : 0;
            if (insertPos > allLines.size()) {
                insertPos = allLines.size();
            }

            allLines.add(insertPos, content);

            Files.write(path, allLines);

            return "成功在第 " + (insertPos + 1) + " 行插入内容";
        } catch (IOException e) {
            log.error("插入行失败: {}", filePath, e);
            return "错误: 插入行失败 - " + e.getMessage();
        }
    }

    @Tool(value = {"删除指定文件", "文件删除"})
    public String deleteFile(@P(description = "文件路径") String filePath) {
        try {
            Path path = Paths.get(filePath).toAbsolutePath().normalize();

            if (!Files.exists(path)) {
                return "错误: 文件不存在: " + filePath;
            }

            boolean deleted = Files.deleteIfExists(path);

            if (deleted) {
                return "成功删除文件: " + filePath;
            } else {
                return "删除失败: " + filePath;
            }
        } catch (IOException e) {
            log.error("删除文件失败: {}", filePath, e);
            return "错误: 删除文件失败 - " + e.getMessage();
        }
    }

    @Tool(value = {"删除指定目录", "文件删除"})
    public String deleteDirectory(
            @P(description = "目录路径") String dirPath,
            @P(description = "是否递归删除子目录和文件", required = false) Boolean recursive) {
        try {
            Path path = Paths.get(dirPath).toAbsolutePath().normalize();

            if (!Files.exists(path)) {
                return "错误: 目录不存在: " + dirPath;
            }

            if (!Files.isDirectory(path)) {
                return "错误: 路径不是目录: " + dirPath;
            }

            boolean rec = recursive != null && recursive;

            if (rec) {
                Files.walk(path)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                log.error("删除失败: {}", p, e);
                            }
                        });
                return "成功递归删除目录: " + dirPath;
            } else {
                if (isDirectoryEmpty(path)) {
                    Files.delete(path);
                    return "成功删除空目录: " + dirPath;
                } else {
                    return "错误: 目录不为空，请使用 recursive=true 参数";
                }
            }
        } catch (IOException e) {
            log.error("删除目录失败: {}", dirPath, e);
            return "错误: 删除目录失败 - " + e.getMessage();
        }
    }

    @Tool(value = {"移动或重命名文件", "文件移动"})
    public String moveFile(
            @P(description = "源文件路径") String sourcePath,
            @P(description = "目标路径或新文件名") String destinationPath) {
        try {
            Path source = Paths.get(sourcePath).toAbsolutePath().normalize();
            Path destination = Paths.get(destinationPath).toAbsolutePath().normalize();

            if (!Files.exists(source)) {
                return "错误: 源文件不存在: " + sourcePath;
            }

            File destParent = destination.getParent().toFile();
            if (destParent != null && !destParent.exists()) {
                if (!destParent.mkdirs()) {
                    return "错误: 无法创建目标目录: " + destParent.getAbsolutePath();
                }
            }

            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);

            return "成功移动文件: " + sourcePath + " -> " + destinationPath;
        } catch (IOException e) {
            log.error("移动文件失败: {} -> {}", sourcePath, destinationPath, e);
            return "错误: 移动文件失败 - " + e.getMessage();
        }
    }

    @Tool(value = {"复制文件", "文件复制"})
    public String copyFile(
            @P(description = "源文件路径") String sourcePath,
            @P(description = "目标路径") String destinationPath) {
        try {
            Path source = Paths.get(sourcePath).toAbsolutePath().normalize();
            Path destination = Paths.get(destinationPath).toAbsolutePath().normalize();

            if (!Files.exists(source)) {
                return "错误: 源文件不存在: " + sourcePath;
            }

            File destParent = destination.getParent().toFile();
            if (destParent != null && !destParent.exists()) {
                if (!destParent.mkdirs()) {
                    return "错误: 无法创建目标目录: " + destParent.getAbsolutePath();
                }
            }

            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);

            return "成功复制文件: " + sourcePath + " -> " + destinationPath;
        } catch (IOException e) {
            log.error("复制文件失败: {} -> {}", sourcePath, destinationPath, e);
            return "错误: 复制文件失败 - " + e.getMessage();
        }
    }

    @Tool(value = {"复制目录", "文件复制"})
    public String copyDirectory(
            @P(description = "源目录路径") String sourcePath,
            @P(description = "目标目录路径") String destinationPath) {
        try {
            Path source = Paths.get(sourcePath).toAbsolutePath().normalize();
            Path destination = Paths.get(destinationPath).toAbsolutePath().normalize();

            if (!Files.exists(source)) {
                return "错误: 源目录不存在: " + sourcePath;
            }

            if (!Files.isDirectory(source)) {
                return "错误: 源路径不是目录: " + sourcePath;
            }

            if (!Files.exists(destination)) {
                Files.createDirectories(destination);
            }

            Files.walk(source).forEach(sourceFile -> {
                try {
                    Path targetFile = destination.resolve(source.relativize(sourceFile));
                    if (Files.isDirectory(sourceFile)) {
                        Files.createDirectories(targetFile);
                    } else {
                        Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    log.error("复制目录中的文件失败: {}", sourceFile, e);
                }
            });

            return "成功复制目录: " + sourcePath + " -> " + destinationPath;
        } catch (IOException e) {
            log.error("复制目录失败: {} -> {}", sourcePath, destinationPath, e);
            return "错误: 复制目录失败 - " + e.getMessage();
        }
    }

    @Tool(value = {"获取文件或目录信息", "文件信息"})
    public String getFileInfo(@P(description = "文件或目录路径") String pathStr) {
        try {
            Path path = Paths.get(pathStr).toAbsolutePath().normalize();

            if (!Files.exists(path)) {
                return "错误: 路径不存在: " + pathStr;
            }

            File file = path.toFile();
            StringBuilder info = new StringBuilder();

            info.append("路径: ").append(path).append("\n");
            info.append("类型: ").append(Files.isDirectory(path) ? "目录" : "文件").append("\n");
            info.append("大小: ").append(formatFileSize(Files.size(path))).append("\n");
            info.append("创建时间: ").append(Files.getAttribute(path, "creationTime")).append("\n");
            info.append("最后修改: ").append(Files.getLastModifiedTime(path)).append("\n");
            info.append("可读: ").append(file.canRead()).append("\n");
            info.append("可写: ").append(file.canWrite()).append("\n");
            info.append("可执行: ").append(file.canExecute()).append("\n");

            if (Files.isDirectory(path)) {
                String[] children = file.list();
                info.append("子项数量: ").append(children != null ? children.length : 0).append("\n");
            }

            return info.toString();
        } catch (IOException e) {
            log.error("获取文件信息失败: {}", pathStr, e);
            return "错误: 获取文件信息失败 - " + e.getMessage();
        }
    }

    @Tool(value = {"列出目录内容", "文件列表"})
    public String listDirectory(
            @P(description = "目录路径") String dirPath,
            @P(description = "是否递归列出子目录", required = false) Boolean recursive) {
        try {
            Path path = Paths.get(dirPath).toAbsolutePath().normalize();

            if (!Files.exists(path)) {
                return "错误: 目录不存在: " + dirPath;
            }

            if (!Files.isDirectory(path)) {
                return "错误: 路径不是目录: " + dirPath;
            }

            StringBuilder result = new StringBuilder();
            boolean rec = recursive != null && recursive;

            if (rec) {
                Files.walk(path).forEach(p -> {
                    try {
                        String prefix = "";
                        int depth = path.relativize(p).getNameCount() - 1;
                        for (int i = 0; i < depth; i++) {
                            prefix += "  ";
                        }

                        String name = p.getFileName().toString();
                        if (Files.isDirectory(p)) {
                            result.append(prefix).append("[DIR] ").append(name).append("\n");
                        } else {
                            result.append(prefix).append("[FILE] ").append(name);
                            result.append(" (").append(formatFileSize(Files.size(p))).append(")\n");
                        }
                    } catch (IOException e) {
                        log.error("遍历目录失败: {}", p, e);
                    }
                });
            } else {
                File dir = path.toFile();
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isDirectory()) {
                            result.append("[DIR] ").append(f.getName()).append("\n");
                        } else {
                            result.append("[FILE] ").append(f.getName());
                            result.append(" (").append(formatFileSize(f.length())).append(")\n");
                        }
                    }
                }
            }

            return result.length() == 0 ? "目录为空" : result.toString();
        } catch (IOException e) {
            log.error("列出目录失败: {}", dirPath, e);
            return "错误: 列出目录失败 - " + e.getMessage();
        }
    }

    @Tool(value = {"创建目录", "文件操作"})
    public String createDirectory(
            @P(description = "目录路径") String dirPath,
            @P(description = "是否创建父目录", required = false) Boolean createParent) {
        try {
            Path path = Paths.get(dirPath).toAbsolutePath().normalize();

            if (Files.exists(path)) {
                return "错误: 目录已存在: " + dirPath;
            }

            boolean parent = createParent == null || createParent;

            if (parent) {
                Files.createDirectories(path);
                return "成功创建目录及父目录: " + dirPath;
            } else {
                Files.createDirectory(path);
                return "成功创建目录: " + dirPath;
            }
        } catch (IOException e) {
            log.error("创建目录失败: {}", dirPath, e);
            return "错误: 创建目录失败 - " + e.getMessage();
        }
    }

    @Tool(value = {"在文件或目录中高性能搜索关键词，支持多关键词、正则、多线程并行处理", "文件搜索"})
    public String searchInFiles(
            @P(description = "搜索关键词，多个关键词用逗号、空格或分号分隔") String keywords,
            @P(description = "文件或目录路径") String targetPath,
            @P(description = "是否使用正则表达式搜索，默认否", required = false) Boolean isRegex,
            @P(description = "最大线程数，控制并行搜索的文件数，不传则使用配置值", required = false) Integer maxThreadsParam,
            @P(description = "最大搜索条数，传0不限制，不传则使用配置值（默认10）", required = false) Integer maxResultsParam) {
        ensureConfigLoaded();
        try {
            Path path = Paths.get(targetPath).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                return "错误: 路径不存在: " + targetPath;
            }

            String[] kwArray = keywords.split("[,;，；\\s]+");
            if (kwArray.length == 0) {
                return "错误: 关键词不能为空";
            }

            boolean useRegex = isRegex != null && isRegex;
            int threads = maxThreadsParam != null && maxThreadsParam > 0
                    ? Math.min(maxThreadsParam, 32)
                    : this.maxThreads;
            int limit = maxResultsParam != null ? maxResultsParam : this.maxResults;

            List<Path> files = new ArrayList<>();
            if (Files.isDirectory(path)) {
                Files.walk(path).filter(Files::isRegularFile).forEach(files::add);
            } else {
                files.add(path);
            }

            if (files.isEmpty()) {
                return "未找到任何文件: " + targetPath;
            }

            ExecutorService executor = Executors.newFixedThreadPool(threads);
            MatchStats stats = new MatchStats();
            Map<String, List<LineMatch>> resultsByFile = new LinkedHashMap<>();

            try {
                @SuppressWarnings("unchecked")
                Future<List<LineMatch>>[] futures = new Future[files.size()];
                for (int i = 0; i < files.size(); i++) {
                    Path f = files.get(i);
                    SearchPattern searchPattern = useRegex
                            ? new RegexSearchPattern(kwArray)
                            : new BmSearchPattern(kwArray);
                    futures[i] = executor.submit(new FileSearchTask(f, searchPattern, stats));
                }

                long totalCollected = 0;
                int fileIdx = 0;
                for (fileIdx = 0; fileIdx < futures.length; fileIdx++) {
                    List<LineMatch> matches = futures[fileIdx].get();
                    if (!matches.isEmpty()) {
                        if (limit > 0 && totalCollected + matches.size() > limit) {
                            int remaining = (int) (limit - totalCollected);
                            if (remaining > 0) {
                                resultsByFile.put(files.get(fileIdx).toString(), matches.subList(0, remaining));
                                totalCollected += remaining;
                            }
                            break;
                        }
                        resultsByFile.put(files.get(fileIdx).toString(), matches);
                        totalCollected += matches.size();
                        if (limit > 0 && totalCollected >= limit) {
                            break;
                        }
                    }
                }

                if (limit > 0 && totalCollected >= limit) {
                    for (int j = futures.length - 1; j > fileIdx; j--) {
                        futures[j].cancel(true);
                    }
                }
            } finally {
                executor.shutdown();
            }

            if (resultsByFile.isEmpty()) {
                return "未找到匹配内容，共扫描 " + files.size() + " 个文件，命中 " + stats.totalLines + " 行，耗时 " + stats.elapsedMs + "ms";
            }

            StringBuilder result = new StringBuilder();
            result.append("搜索完成: 扫描 ").append(files.size()).append(" 个文件, 命中 ")
                    .append(stats.totalLines).append(" 行, 耗时 ").append(stats.elapsedMs).append("ms\n\n");

            for (Map.Entry<String, List<LineMatch>> entry : resultsByFile.entrySet()) {
                result.append(entry.getKey()).append(" (").append(entry.getValue().size()).append("处匹配):\n");
                for (LineMatch m : entry.getValue()) {
                    result.append(String.format("%6d: %s%n", m.lineNumber, m.line));
                }
                result.append("\n");
            }

            return result.toString();
        } catch (Exception e) {
            log.error("搜索文件失败: {}", targetPath, e);
            return "错误: 搜索失败 - " + e.getMessage();
        }
    }

    @Override
    public List<ToolConfigItem> getConfigItems() {
        return List.of(
                new ToolConfigItem("maxThreads", "最大线程数", "文件搜索时的最大并行线程数（1-32），控制同时搜索多个文件的并发度", ToolConfigItem.ConfigType.TEXT_SINGLE)
                        .validation(new ValidationRule().required().value(1L, 32L)),
                new ToolConfigItem("maxResults", "最大搜索条数", "单次搜索返回的最大匹配行数，0表示不限制（0-1000）", ToolConfigItem.ConfigType.TEXT_SINGLE)
                        .validation(new ValidationRule().required().value(0L, 1000L))
        );
    }

    @Override
    public void onConfigChanged() {
        configLoaded = false;
        loadConfig();
        configLoaded = true;
        log.info("文件操作工具配置已重载, maxThreads={}, maxResults={}", maxThreads, maxResults);
    }

    @Override
    public String getName() {
        return "fileOperation";
    }

    @Override
    public String getDescription() {
        return "文件操作工具集，支持读取、写入、删除、移动、复制、高性能搜索等文件操作";
    }

    @Override
    public String getIcon() {
        return "file-operation-tool.svg";
    }

    @Override
    public String getKeyword() {
        return "文件";
    }

    private static class MatchStats {
        long totalLines;
        long elapsedMs;
    }

    private static class LineMatch {
        final long lineNumber;
        final String line;

        LineMatch(long lineNumber, String line) {
            this.lineNumber = lineNumber;
            this.line = line;
        }
    }

    private interface SearchPattern {
        boolean matches(byte[] array, int offset, int length);
    }

    private static class RegexSearchPattern implements SearchPattern {
        private final Pattern[] patterns;

        RegexSearchPattern(String[] keywords) {
            this.patterns = new Pattern[keywords.length];
            for (int i = 0; i < keywords.length; i++) {
                this.patterns[i] = Pattern.compile(keywords[i]);
            }
        }

        @Override
        public boolean matches(byte[] array, int offset, int length) {
            String line = new String(array, offset, length, StandardCharsets.UTF_8);
            for (Pattern p : patterns) {
                if (p.matcher(line).find()) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class BmSearchPattern implements SearchPattern {
        private final byte[][] patternBytes;
        private final int[][] badCharSkip;
        private final int[] patternLengths;

        BmSearchPattern(String[] keywords) {
            int n = keywords.length;
            this.patternBytes = new byte[n][];
            this.badCharSkip = new int[n][];
            this.patternLengths = new int[n];

            for (int k = 0; k < n; k++) {
                byte[] p = keywords[k].getBytes(StandardCharsets.UTF_8);
                this.patternBytes[k] = p;
                int m = p.length;
                this.patternLengths[k] = m;
                this.badCharSkip[k] = new int[256];
                Arrays.fill(this.badCharSkip[k], m);
                for (int i = 0; i < m - 1; i++) {
                    this.badCharSkip[k][p[i] & 0xFF] = m - 1 - i;
                }
            }
        }

        @Override
        public boolean matches(byte[] array, int offset, int length) {
            for (int k = 0; k < patternBytes.length; k++) {
                byte[] p = patternBytes[k];
                int m = patternLengths[k];
                if (m == 0) continue;
                int[] skip = badCharSkip[k];
                int n = length;
                int i = 0;
                while (i <= n - m) {
                    int j = m - 1;
                    while (j >= 0 && p[j] == array[offset + i + j]) j--;
                    if (j < 0) return true;
                    i += Math.max(1, skip[array[offset + i + m - 1] & 0xFF]);
                }
            }
            return false;
        }
    }

    private static class FileSearchTask implements Callable<List<LineMatch>> {
        private final Path file;
        private final SearchPattern pattern;
        private final MatchStats stats;

        FileSearchTask(Path file, SearchPattern pattern, MatchStats stats) {
            this.file = file;
            this.pattern = pattern;
            this.stats = stats;
        }

        @Override
        public List<LineMatch> call() throws Exception {
            List<LineMatch> results = new ArrayList<>();
            long start = System.currentTimeMillis();
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
                long size = channel.size();
                if (size == 0) return results;
                if (size > Integer.MAX_VALUE - 8) {
                    size = Integer.MAX_VALUE - 8;
                }
                ByteBuffer buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, size);
                byte[] lineBuffer = new byte[8192];
                int lineLen = 0;
                long lineNumber = 1;

                for (int i = 0; i < size; i++) {
                    byte b = buf.get(i);
                    if (b == '\n') {
                        if (pattern.matches(lineBuffer, 0, lineLen)) {
                            results.add(new LineMatch(lineNumber,
                                    new String(lineBuffer, 0, lineLen, StandardCharsets.UTF_8)));
                        }
                        lineLen = 0;
                        lineNumber++;
                    } else if (b == '\r') {
                        if (i + 1 < size && buf.get(i + 1) == '\n') {
                            if (pattern.matches(lineBuffer, 0, lineLen)) {
                                results.add(new LineMatch(lineNumber,
                                        new String(lineBuffer, 0, lineLen, StandardCharsets.UTF_8)));
                            }
                            i++;
                        } else {
                            if (pattern.matches(lineBuffer, 0, lineLen)) {
                                results.add(new LineMatch(lineNumber,
                                        new String(lineBuffer, 0, lineLen, StandardCharsets.UTF_8)));
                            }
                        }
                        lineLen = 0;
                        lineNumber++;
                    } else {
                        if (lineLen == lineBuffer.length) {
                            lineBuffer = Arrays.copyOf(lineBuffer, lineBuffer.length * 2);
                        }
                        lineBuffer[lineLen++] = b;
                    }
                }
                if (lineLen > 0 && pattern.matches(lineBuffer, 0, lineLen)) {
                    results.add(new LineMatch(lineNumber,
                            new String(lineBuffer, 0, lineLen, StandardCharsets.UTF_8)));
                }
            }
            long elapsed = System.currentTimeMillis() - start;
            synchronized (stats) {
                stats.totalLines += results.size();
                if (elapsed > stats.elapsedMs) {
                    stats.elapsedMs = elapsed;
                }
            }
            return results;
        }
    }

    private boolean isDirectoryEmpty(Path path) throws IOException {
        try (var entries = Files.list(path)) {
            return entries.findFirst().isEmpty();
        }
    }

    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }
}