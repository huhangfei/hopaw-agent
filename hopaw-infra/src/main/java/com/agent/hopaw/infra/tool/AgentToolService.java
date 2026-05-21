package com.agent.hopaw.infra.tool;

import com.agent.hopaw.infra.constant.AgentToolSourceEnum;
import com.agent.hopaw.infra.model.dto.PluginUpdateInfo;
import com.agent.hopaw.infra.model.dto.ToolInfo;
import com.agent.hopaw.infra.model.dto.ToolParamInfo;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import com.agent.hopaw.infra.plugin.DynamicToolRegistry;
import com.agent.hopaw.infra.plugin.JarPluginLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AgentToolService implements IAgentToolService {

    private static final Logger log = LoggerFactory.getLogger(AgentToolService.class);
    private static final int BUFFER_SIZE = 8192;

    private final ApplicationContext applicationContext;
    private final DynamicToolRegistry dynamicToolRegistry;
    private final JarPluginLoader jarPluginLoader;
    private final ObjectMapper objectMapper;

    public AgentToolService(ApplicationContext applicationContext, DynamicToolRegistry dynamicToolRegistry, JarPluginLoader jarPluginLoader) {
        this.applicationContext = applicationContext;
        this.dynamicToolRegistry = dynamicToolRegistry;
        this.jarPluginLoader = jarPluginLoader;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<AgentTool> getAgentTools() {
        Map<String, AgentTool> beans = applicationContext.getBeansOfType(AgentTool.class);
        List<AgentTool> tools = new ArrayList<>(beans.values());
        tools.addAll(dynamicToolRegistry.getAllDynamicTools());
        tools.sort(Comparator.comparing(AgentTool::getName));
        return tools;
    }

    @Override
    public List<ToolSetInfo> getToolSets() {
        List<ToolSetInfo> result = new ArrayList<>();
        Map<String, AgentTool> beans = applicationContext.getBeansOfType(AgentTool.class);
        for (AgentTool agentTool : beans.values()) {
            result.add(scanToolSet(agentTool, AgentToolSourceEnum.BUILT_IN));
        }
        result.addAll(getAllToolSets());
        return result;
    }
    public List<ToolSetInfo> getAllToolSets() {
        List<ToolSetInfo> result = new ArrayList<>();
        List<DynamicToolRegistry.PluginEntry> allPluginEntries = dynamicToolRegistry.getAllPluginEntries();
        for (DynamicToolRegistry.PluginEntry entry : allPluginEntries) {
            List<AgentTool> tools = entry.tools;
            for (AgentTool tool : tools) {
                ToolSetInfo toolSetInfo = scanToolSet(tool, AgentToolSourceEnum.PLUGIN);
                toolSetInfo.setJarFileName(entry.jarFileName);
                if(!AgentTool.DEFAULT_ICON.equals(toolSetInfo.getIcon())){
                    toolSetInfo.setIcon(entry.getCachedResource("static/icons/tools/"+tool.getIcon()));
                }
                result.add(toolSetInfo);
            }
        }
        return result;
    }
    private ToolSetInfo scanToolSet(AgentTool agentTool, AgentToolSourceEnum source) {
        List<ToolInfo> tools = new ArrayList<>();
        for (Method method : agentTool.getClass().getMethods()) {
            Tool toolAnn = method.getAnnotation(Tool.class);
            if (toolAnn == null) continue;

            String toolName = toolAnn.name();
            if (toolName.isEmpty()) {
                toolName = method.getName();
            }
            String description = Arrays.stream(toolAnn.value()).collect(Collectors.joining(" "));
            List<ToolParamInfo> params = new ArrayList<>();
            for (Parameter param : method.getParameters()) {
                if (param.getType() == InvocationParameters.class) continue;

                P pAnn = param.getAnnotation(P.class);
                String paramName = param.getName();
                String paramDesc = "";
                boolean required = true;

                if (pAnn != null) {
                    paramDesc = pAnn.description();
                    if (paramDesc.isEmpty()) {
                        paramDesc = pAnn.value();
                    }
                    required = pAnn.required();
                }

                params.add(new ToolParamInfo(paramName, paramDesc, required, param.getType().getSimpleName()));
            }
            params.sort(Comparator.comparing(p -> p.isRequired() ? 0 : 1));

            tools.add(new ToolInfo(toolName, description, params));
        }
        ToolSetInfo toolSetInfo = new ToolSetInfo(agentTool.getName(), agentTool.getDescription(), agentTool.getIcon(), tools, source);
        toolSetInfo.setVersion(agentTool.getVersion());
        toolSetInfo.setAuthor(agentTool.getAuthor());
        toolSetInfo.setUrl(agentTool.getUrl());
        toolSetInfo.setKeyword(agentTool.getKeyword());
        toolSetInfo.setHasConfigItems(!agentTool.getConfigItems().isEmpty());
        toolSetInfo.setDefaultUpdateUrl(agentTool.getDefaultUpdateUrl());
        return toolSetInfo;
    }

    @Override
    public boolean unloadPlugin(String jarFileName) {
        return jarPluginLoader.unloadAndDeletePlugin(jarFileName);
    }

    @Override
    public PluginUpdateInfo checkPluginUpdate(String checkUrl, String toolName, String currentVersion, String jarFileName) {
        PluginUpdateInfo updateInfo = new PluginUpdateInfo();
        updateInfo.setToolName(toolName);
        updateInfo.setCurrentVersion(currentVersion);
        updateInfo.setInstalled(dynamicToolRegistry.getAllDynamicTools().stream()
                .anyMatch(tool -> tool.getName().equals(toolName)));

        try {
            URL url = new URI(checkUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    JsonNode root = objectMapper.readTree(response.toString());

                    updateInfo.setVersion(getJsonText(root, "version"));
                    updateInfo.setFileName(getJsonText(root, "fileName"));
                    updateInfo.setFileSize(root.has("fileSize") ? root.get("fileSize").asLong() : 0);
                    updateInfo.setDownloadUrl(getJsonText(root, "downloadUrl"));
                    updateInfo.setSha256Hash(getJsonText(root, "sha256Hash"));
                }
            } else {
                log.warn("Failed to check plugin update, HTTP response code: {}", responseCode);
            }
            connection.disconnect();
        } catch (Exception e) {
            log.error("Error checking plugin update for {}", toolName, e);
        }

        if (updateInfo.getVersion() != null && !updateInfo.getVersion().isEmpty()) {
            updateInfo.setNeedUpgrade(!updateInfo.getVersion().equals(currentVersion));
        }

        return updateInfo;
    }

    private String getJsonText(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : "";
    }

    @Override
    public boolean installOrUpgradePlugin(PluginUpdateInfo updateInfo) {
        if (updateInfo.getDownloadUrl() == null || updateInfo.getDownloadUrl().isEmpty()) {
            log.error("Download URL is empty for plugin: {}", updateInfo.getToolName());
            return false;
        }

        if (updateInfo.getSha256Hash() == null || updateInfo.getSha256Hash().isEmpty()) {
            log.error("SHA256 hash is empty for plugin: {}", updateInfo.getToolName());
            return false;
        }

        String jarFileName = updateInfo.getFileName();
        if (jarFileName == null || !jarFileName.toLowerCase().endsWith(".jar")) {
            jarFileName = updateInfo.getToolName() + ".jar";
        }

        Path pluginDir = jarPluginLoader.getPluginDir();
        Path targetPath = pluginDir.resolve(jarFileName);

        try {
            if (updateInfo.isInstalled()) {
                log.info("Plugin {} is installed, uninstalling before upgrade", updateInfo.getToolName());
                DynamicToolRegistry.PluginEntry removed = dynamicToolRegistry.unregister(jarFileName);
                if (removed != null) {
                    for (AgentTool tool : removed.tools) {
                        try {
                            tool.destroy();
                        } catch (Exception e) {
                            log.error("Error calling destroy() on tool: {}", tool.getClass().getSimpleName(), e);
                        }
                    }
                }
                File existingFile = targetPath.toFile();
                if (existingFile.exists() && !existingFile.delete()) {
                    log.warn("Failed to delete existing plugin file: {}", targetPath);
                }
            }

            log.info("Downloading plugin from: {}", updateInfo.getDownloadUrl());
            URL downloadUrl = new URI(updateInfo.getDownloadUrl()).toURL();
            HttpURLConnection connection = (HttpURLConnection) downloadUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(60000);
            connection.setReadTimeout(60000);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                log.error("Failed to download plugin, HTTP response code: {}", responseCode);
                connection.disconnect();
                return false;
            }

            Path tempFile = Files.createTempFile("plugin_download_", ".jar");
            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            connection.disconnect();

            String downloadedHash = calculateSHA256(tempFile);
            if (!updateInfo.getSha256Hash().equalsIgnoreCase(downloadedHash)) {
                log.error("SHA256 hash mismatch for plugin {}. Expected: {}, Got: {}",
                        updateInfo.getToolName(), updateInfo.getSha256Hash(), downloadedHash);
                Files.deleteIfExists(tempFile);
                return false;
            }

            Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Plugin {} installed successfully to {}", updateInfo.getToolName(), targetPath);

            return true;
        } catch (Exception e) {
            log.error("Error installing/upgrading plugin: {}", updateInfo.getToolName(), e);
            return false;
        }
    }

    private String calculateSHA256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        byte[] hashBytes = digest.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
