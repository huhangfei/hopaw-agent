package com.agent.hopaw.tool.ssh;

import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import com.agent.hopaw.infra.service.IAgentExecutorService;
import com.agent.hopaw.infra.service.ISysConfigService;
import com.agent.hopaw.infra.util.InvocationParametersWrapper;
import com.agent.hopaw.infra.model.dto.ToolConfigItem;
import com.agent.hopaw.infra.model.dto.ToolMapConfigItem;
import com.agent.hopaw.infra.model.dto.ValidationRule;
import com.agent.hopaw.infra.model.entity.SysConfig;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.jcraft.jsch.*;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.agent.hopaw.infra.tool.AbstractAgentTool;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SSH远程连接工具插件
 * 支持多服务器配置：在系统配置中添加多组服务器（配置名称 + IP + 端口 + 账号 + 密码），
 * 密码加密存储，sshConnectFromConfig 传入配置名称即可建立连接。
 * 注意：作为插件使用时，不要加 @Component 注解，由插件加载器实例化并通过 @Autowired 注入依赖
 * @author hhf
 */
public class SshTool extends AbstractAgentTool {
    private static final Logger logger = LoggerFactory.getLogger(SshTool.class);
    private static final Map<String, Session> SESSION_CACHE = new ConcurrentHashMap<>();

    /** 映射组配置键：服务器配置名称 → {host, port, username, password} */
    private static final String CONFIG_KEY_SERVERS = "servers";

    @Autowired
    private IAgentExecutorService agentExecutorService;

    @Autowired
    private ISysConfigService sysConfigService;

    /** 服务器配置缓存：配置名称 → 服务器连接信息 */
    private volatile Map<String, SshServer> cachedServers = Collections.emptyMap();

    /** 服务器连接配置 */
    private record SshServer(String host, int port, String username, String password) {}

    /**
     * 无参构造函数 - 插件加载器使用
     */
    public SshTool() {
    }

    /**
     * 有参构造函数 - 如果作为 Spring Bean 直接使用
     */
    public SshTool(IAgentExecutorService agentExecutorService) {
        this.agentExecutorService = agentExecutorService;
    }

    @Override
    public String getName() {
        return "ssh";
    }

    @Override
    public String getDescription() {
        return "SSH远程连接、命令执行与文件传输工具。";
    }

    @Override
    public String getIcon() {
        return "ssh-tool.svg";
    }

    @Override
    public String getKeyword() {
        return "SSH, SFTP";
    }

    // ========== 多服务器配置定义与解析 ==========

    /**
     * 映射组结构配置：主体 key = 配置名称（服务器配置主键），values = 每组内的字段。
     * 存储为一条 JSON：{"服务器1":{"host":"...","port":"22","username":"...","password":"..."},...}
     * 密码为敏感项，整条 JSON 加密存储。
     */
    @Override
    public List<ToolConfigItem> getConfigItems() {
        ToolMapConfigItem servers = new ToolMapConfigItem(CONFIG_KEY_SERVERS, "服务器配置",
                "配置多组服务器：每组填写服务器配置名称（作为配置主键，sshConnectFromConfig 通过该名称建立连接），组内配置服务器IP、端口、账号与密码",
                ToolConfigItem.ConfigType.TEXT_SINGLE);
        servers.setValues(List.of(
                new ToolConfigItem("host", "服务器IP", "服务器IP地址或域名", ToolConfigItem.ConfigType.TEXT_SINGLE)
                        .validation(new ValidationRule().required()),
                new ToolConfigItem("port", "SSH端口", "SSH端口号，默认22", ToolConfigItem.ConfigType.TEXT_SINGLE)
                        .validation(new ValidationRule().value(1L, 65535L)),
                new ToolConfigItem("username", "登录账号", "SSH登录用户名", ToolConfigItem.ConfigType.TEXT_SINGLE)
                        .validation(new ValidationRule().required()),
                new ToolConfigItem("password", "登录密码", "SSH登录密码（加密存储）", ToolConfigItem.ConfigType.TEXT_PASSWORD)
                        .validation(new ValidationRule().required())
        ));
        return List.of(servers);
    }

    @Override
    public void asyncInit() {
        reloadServers();
    }

    @Override
    public void onConfigChanged() {
        reloadServers();
    }

    /**
     * 从 sys_config 加载 servers 映射组配置，解析为 配置名称 → 服务器连接信息。
     */
    private void reloadServers() {
        Map<String, SshServer> servers = new LinkedHashMap<>();
        if (sysConfigService != null) {
            SysConfig config = sysConfigService.getByKey(getConfigPrefix() + CONFIG_KEY_SERVERS);
            String json = config != null ? config.getConfigValue() : null;
            if (json != null && !json.trim().isEmpty()) {
                try {
                    LinkedHashMap<String, LinkedHashMap<String, String>> groups = JSON.parseObject(json,
                            new TypeReference<LinkedHashMap<String, LinkedHashMap<String, String>>>() {});
                    for (Map.Entry<String, LinkedHashMap<String, String>> e : groups.entrySet()) {
                        String host = e.getValue() != null ? e.getValue().get("host") : null;
                        String port = e.getValue() != null ? e.getValue().get("port") : null;
                        String username = e.getValue() != null ? e.getValue().get("username") : null;
                        String password = e.getValue() != null ? e.getValue().get("password") : null;
                        int portVal;
                        try {
                            portVal = (port == null || port.trim().isEmpty()) ? 22 : Integer.parseInt(port.trim());
                        } catch (NumberFormatException ex) {
                            logger.warn("服务器配置[{}]端口不合法（{}），已跳过", e.getKey(), port);
                            continue;
                        }
                        if (isNotBlank(host) && isNotBlank(username) && isNotBlank(password)) {
                            servers.put(e.getKey(), new SshServer(host.trim(), portVal, username.trim(), password));
                        } else {
                            logger.warn("服务器配置[{}]不完整（缺少 host、username 或 password），已跳过", e.getKey());
                        }
                    }
                } catch (Exception ex) {
                    logger.error("服务器配置解析失败：{}", ex.getMessage());
                }
            }
        }
        this.cachedServers = servers;
        logger.info("SSH服务器配置已加载，共{}个：{}", servers.size(), servers.keySet());
    }

    /**
     * 按服务器配置主键解析连接信息；缓存为空时先尝试加载一次。
     */
    private SshServer resolveServer(String serverKey) {
        if (serverKey == null || serverKey.trim().isEmpty()) {
            return null;
        }
        if (cachedServers.isEmpty()) {
            reloadServers();
        }
        return cachedServers.get(serverKey.trim());
    }

    /**
     * 生成服务器配置解析失败的提示信息（含可用配置列表，便于模型自我修正）。
     */
    private String serverError(String serverKey) {
        if (serverKey == null || serverKey.trim().isEmpty()) {
            return "错误：服务器配置主键不能为空，请传入系统配置中添加的服务器配置名称";
        }
        if (cachedServers.isEmpty()) {
            return "错误：尚未配置任何服务器，请先在系统配置的SSH工具中添加（每组包含配置名称、服务器IP、端口、账号、密码）";
        }
        return "错误：未找到服务器配置 [" + serverKey + "]，当前已配置的服务器：" + String.join("、", cachedServers.keySet());
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.ALL_REQUIRE_APPROVAL)
    @Tool(name = "ssh_connect", value = {"SSH连接", "SSH远程连接服务器，建立SSH会话。密码属于敏感信息，如果账号密码错误不要自行猜测，请搜索记忆或询问用户。连接成功后会返回sessionKey，后续操作需要使用此sessionKey。"})
    public String sshConnect(
            @P(description = "服务器IP地址或域名") String host,
            @P(description = "SSH端口号，默认22", required = false) Integer port,
            @P(description = "登录用户名") String username,
            @P(description = "登录密码") String password) {
        if (host == null || host.trim().isEmpty() || username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return "错误：host、username、password 不能为空";
        }
        int portVal = (port != null && port > 0) ? port : 22;
        String sessionKey = host + ":" + portVal;

        try {

            sshDisconnect(sessionKey);

            Session session = jschConnect(sessionKey, username, host, portVal, password);
            return "成功：连接已建立，sessionKey=" + sessionKey;
        } catch (JSchException e) {
            logger.error("SSH connection failed", e);
            return "错误：连接失败 - " + e.getMessage();
        }
    }

    /**
     * 建立连接并注册到会话缓存（并发安全）。
     * 使用 putIfAbsent/replace 保证：并发连接同一目标时只有一个连接被保留，竞争失败的连接立即关闭，不会泄漏。
     */
    private Session jschConnect(String sessionKey, String username, String host, int port, String password) throws JSchException {
        Session existing = SESSION_CACHE.get(sessionKey);
        if (existing != null && existing.isConnected()) {
            if (isSessionAlive(existing)) {
                return existing;
            }
            // 老连接不可用（isConnected 无法发现服务器重启/网络中断后的半死连接）：断开老连接并移除缓存，重新连接
            logger.info("缓存的SSH连接已失效，断开并重新连接: sessionKey={}", sessionKey);
            if (SESSION_CACHE.remove(sessionKey, existing)) {
                disconnectQuietly(existing);
            } else {
                // 已被其他线程处理，复用其新连接
                Session current = SESSION_CACHE.get(sessionKey);
                if (current != null && current.isConnected()) {
                    return current;
                }
            }
        }
        JSch jsch = new JSch();
        Session session = jsch.getSession(username, host, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(30000);

        Session prev = SESSION_CACHE.putIfAbsent(sessionKey, session);
        if (prev == null) {
            return session;
        }
        // 并发窗口内已有其他线程抢先注册：优先复用，关闭本次新建连接避免泄漏
        disconnectQuietly(session);
        if (prev.isConnected()) {
            return prev;
        }
        // 抢先注册的连接已断开：原子替换
        if (SESSION_CACHE.replace(sessionKey, prev, session)) {
            return session;
        }
        // replace 失败说明又被其他线程更新，复用当前缓存中的连接
        disconnectQuietly(session);
        Session current = SESSION_CACHE.get(sessionKey);
        return current != null ? current : session;
    }

    /**
     * 获取可用的SSH会话；会话不存在或已断开时自动重连。
     * 仅 sessionKey 为服务器配置主键的连接支持自动重连（可从系统配置中取得连接凭证）；
     * host:port 形式的连接（sshConnect 明文建立，密码不缓存）无法自动重连，返回 null 由调用方提示。
     */
    private Session acquireSession(String sessionKey) {
        String key = sessionKey.trim();
        SshServer server = resolveServer(key);
        if (server != null) {
            // jschConnect 内部会探测半死连接并自动重建
            try {
                return jschConnect(key, server.username(), server.host(), server.port(), server.password());
            } catch (JSchException e) {
                logger.error("SSH会话自动重连失败: sessionKey={}", key, e);
                return null;
            }
        }
        // host:port 形式：仅复用缓存中的现有连接
        Session session = SESSION_CACHE.get(key);
        if (session != null && session.isConnected()) {
            return session;
        }
        return null;
    }

    /**
     * 真实探测会话是否可用。
     * isConnected() 只反映本地标志位，无法发现服务器重启/网络中断后的半死连接，
     * 这里通过打开 exec channel 执行空命令（true）做一次真实往返，connect 成功即连接可用。
     */
    private boolean isSessionAlive(Session session) {
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("true");
            channel.setInputStream(null);
            channel.connect(5000);
            return true;
        } catch (Exception e) {
            logger.warn("SSH连接探测失败（{}），将重建连接", e.getMessage());
            return false;
        } finally {
            if (channel != null) {
                try {
                    channel.disconnect();
                } catch (Throwable ignore) {
                    // 清理失败不影响主流程
                }
            }
        }
    }

    private static void disconnectQuietly(Session session) {
        if (session != null) {
            try {
                session.disconnect();
            } catch (Throwable ignore) {
                // 关闭失败不影响主流程
            }
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.ALL_REQUIRE_APPROVAL)
    @Tool(name = "ssh_connectFromConfig", value = {"SSH按配置连接", "通过系统配置中预置的服务器配置主键建立SSH会话，无需传入明文的IP、账号与密码。服务器配置在系统配置的SSH工具中维护（包含服务器IP、端口、账号、密码，密码加密存储）。连接成功后返回该配置主键作为sessionKey，后续操作使用此sessionKey。"})
    public String sshConnectFromConfig(
            @P(description = "服务器配置主键，即系统配置中的服务器配置名称") String serverKey) {
        SshServer server = resolveServer(serverKey);
        if (server == null) {
            return serverError(serverKey);
        }
        // sessionKey 即服务器配置主键（配置名称约束不含冒号，不会与 host:port 形式的 sessionKey 冲突）
        String sessionKey = serverKey.trim();

        try {
            jschConnect(sessionKey, server.username(), server.host(), server.port(), server.password());
            return "成功：连接已建立，sessionKey=" + sessionKey;
        } catch (JSchException e) {
            logger.error("SSH connection from config [{}] failed", sessionKey, e);
            return "错误：连接失败（服务器配置 [" + sessionKey + "]，目标 " + server.host() + ":" + server.port() + "） - " + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(name = "ssh_getConnectConfigs", value = {"SSH获取服务器配置列表", "获取系统配置中所有已配置的SSH服务器列表（配置名称、服务器IP、端口、登录账号），不包含密码。可用于查询可用配置或确认某个配置主键是否存在，供 ssh_connectFromConfig 使用。", "SSH,配置,列表,服务器"})
    public String getSshConnectConfigs() {
        if (cachedServers.isEmpty()) {
            reloadServers();
        }
        if (cachedServers.isEmpty()) {
            return "当前未配置任何SSH服务器，请先在系统配置的SSH工具中添加（每组包含配置名称、服务器IP、端口、账号、密码）";
        }
        StringBuilder sb = new StringBuilder("已配置 " + cachedServers.size() + " 台服务器（密码已隐藏）：\n");
        int index = 1;
        for (Map.Entry<String, SshServer> e : cachedServers.entrySet()) {
            SshServer s = e.getValue();
            sb.append(index++).append(". 配置名称：").append(e.getKey())
                    .append("，服务器IP：").append(s.host())
                    .append("，端口：").append(s.port())
                    .append("，账号：").append(s.username())
                    .append("\n");
        }
        return sb.toString().trim();
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.ALL_REQUIRE_APPROVAL)
    @Tool(name = "ssh_exec", value = {"SSH执行命令", "在已连接的SSH会话上执行远程命令，需要先通过 ssh_connect 建立连接获取 sessionKey"})
    public String sshExec(
            @P(description = "会话标识，由sshConnect返回的sessionKey") String sessionKey,
            @P(description = "要执行的远程命令") String command,
            @P(description = "命令最大执行时间（秒），默认60秒", required = false) Integer timeout, InvocationParameters invocationParameters) {
        if (sessionKey == null || sessionKey.trim().isEmpty()) {
            return "错误：sessionKey 不能为空";
        }
        if (command == null || command.trim().isEmpty()) {
            return "错误：command 不能为空";
        }

        Session session = acquireSession(sessionKey);
        if (session == null) {
            return "错误：会话未连接或不存在，且自动重连失败（host:port 形式的连接不支持自动重连，请重新执行sshConnect），sessionKey=" + sessionKey;
        }
        InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create(invocationParameters);
        String toolCallId = invocationParametersWrapper.getToolCallId();
        String userId = invocationParametersWrapper.getUserId();
        String sessionId = invocationParametersWrapper.getSessionId();
        int timeoutSec = (timeout != null && timeout > 0) ? timeout : 60;
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        AtomicReference<Boolean> userCancelled = new AtomicReference<>(false);

        try {
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);

            java.io.InputStream in = channel.getInputStream();
            int connectTimeout = Math.min(timeoutSec * 1000, 30000);
            channel.connect(connectTimeout);

            agentExecutorService.addToolStopHook(sessionId, toolCallId, (callId) -> { userCancelled.set(true); channel.disconnect();  });

            StringBuilder output = new StringBuilder();
            byte[] tmp = new byte[1024];
            boolean timedOut = false;
            while (true) {
                while (in.available() > 0) {
                    if (agentExecutorService.toolIsCancelled(sessionId, toolCallId)) {
                        output.append("退出: 用户取消执行");
                        break;
                    }

                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) {
                        break;
                    }
                    String msg = new String(tmp, 0, i);
                    output.append(msg);
                    agentExecutorService.sendToolRunningContent(sessionId, toolCallId, msg);
                }
                if (agentExecutorService.toolIsCancelled(sessionId, toolCallId)) {
                    output.append("退出: 用户取消执行");
                    break;
                }
                if (channel.isClosed()) {
                    break;
                }
                if (System.currentTimeMillis() >= deadline) {
                    timedOut = true;
                    break;
                }
                Thread.sleep(100);
            }

            int exitCode = channel.getExitStatus();
            channel.disconnect();
            if (timedOut) {
                return "退出码: -1 (超时)\n" + output + "\n[命令已超时，已断开连接]";
            }
            return "退出码: " + exitCode + "\n" + output;
        } catch (Exception e) {
            logger.error("SSH exec failed", e);
            if(e instanceof ExecutionException && userCancelled.get()){
                return "退出: 用户取消执行";
            }
            return "错误：命令执行失败 - " + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.ALL_REQUIRE_APPROVAL)
    @Tool(name = "ssh_upload", value = {"SSH上传文件", "通过SFTP上传本地文件到远程服务器，需要先建立SSH连接"})
    public String sshUpload(
            @P(description = "会话标识，由sshConnect返回的sessionKey") String sessionKey,
            @P(description = "本地文件路径") String localPath,
            @P(description = "远程服务器目标路径") String remotePath,
            @P(description = "最大执行时间（秒），默认300秒", required = false) Integer timeout,
            InvocationParameters invocationParameters) {
        if (sessionKey == null || localPath == null || remotePath == null) {
            return "错误：sessionKey、localPath、remotePath 不能为空";
        }

        Session session = acquireSession(sessionKey);
        if (session == null) {
            return "错误：会话未连接或不存在，且自动重连失败（host:port 形式的连接不支持自动重连，请重新执行sshConnect），sessionKey=" + sessionKey;
        }

        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        String sessionId = wrapper.getSessionId();
        String toolCallId = wrapper.getToolCallId();

        int timeoutSec = (timeout != null && timeout > 0) ? timeout : 300;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Boolean> userCancelled = new AtomicReference<>(false);

        try {
            SftpProgressMonitor monitor = new SftpProgressReporter(agentExecutorService, sessionId, toolCallId, "上传");
            java.util.concurrent.Future<String> future = executor.submit(() -> {
            ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(Math.min(timeoutSec * 1000, 30000));
            agentExecutorService.addToolStopHook(sessionId, toolCallId, (callId) -> {userCancelled.set(true);sftp.disconnect(); });

            sftp.put(localPath, remotePath, monitor);
            sftp.disconnect();

            return "成功：文件已上传至 " + remotePath;
            });
            return future.get(timeoutSec, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            return "错误：上传超时（" + timeoutSec + "秒）";
        }catch (Exception e) {
            logger.error("SSH upload failed", e);
            if(e instanceof ExecutionException && userCancelled.get()){
                return "退出: 用户取消上传";
            }
            return "错误：上传失败 - " + e.getMessage();
        } finally {
            executor.shutdownNow();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.ALL_REQUIRE_APPROVAL)
    @Tool(name = "ssh_download", value = {"SSH下载文件", "通过SFTP从远程服务器下载文件到本地，需要先建立SSH连接"})
    public String sshDownload(
            @P(description = "会话标识，由sshConnect返回的sessionKey") String sessionKey,
            @P(description = "远程服务器文件路径") String remotePath,
            @P(description = "本地保存路径") String localPath,
            @P(description = "最大执行时间（秒），默认300秒", required = false) Integer timeout,
            InvocationParameters invocationParameters) {
        if (sessionKey == null || remotePath == null || localPath == null) {
            return "错误：sessionKey、remotePath、localPath 不能为空";
        }

        Session session = acquireSession(sessionKey);
        if (session == null) {
            return "错误：会话未连接或不存在，且自动重连失败（host:port 形式的连接不支持自动重连，请重新执行sshConnect），sessionKey=" + sessionKey;
        }

        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        String sessionId = wrapper.getSessionId();
        String toolCallId = wrapper.getToolCallId();
        AtomicReference<Boolean> userCancelled = new AtomicReference<>(false);

        int timeoutSec = (timeout != null && timeout > 0) ? timeout : 300;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            SftpProgressMonitor monitor = new SftpProgressReporter(
                    agentExecutorService, sessionId, toolCallId, "下载");
            java.util.concurrent.Future<String> future = executor.submit(() -> {
            ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
                sftp.connect(Math.min(timeoutSec * 1000, 30000));
            agentExecutorService.addToolStopHook(sessionId, toolCallId, (callId) -> {userCancelled.set(true);sftp.disconnect(); });
            sftp.get(remotePath, localPath, monitor);
            sftp.disconnect();
            return "成功：文件已下载至 " + localPath;
            });
            return future.get(timeoutSec, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            return "错误：下载超时（" + timeoutSec + "秒）";
        } catch (Exception e) {
            logger.error("SSH download failed", e);
            if(e instanceof ExecutionException && userCancelled.get()){
                return "退出: 用户取消下载";
            }
            return "错误：下载失败 - " + e.getMessage();
        } finally {
            executor.shutdownNow();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(name = "ssh_disconnect", value = {"SSH断开连接", "断开SSH远程连接，释放会话资源"})
    public String sshDisconnect(
            @P(description = "会话标识，由sshConnect返回的sessionKey") String sessionKey) {
        if (sessionKey == null || sessionKey.trim().isEmpty()) {
            return "错误：sessionKey 不能为空";
        }

        Session session = SESSION_CACHE.remove(sessionKey);
        if (session != null && session.isConnected()) {
            session.disconnect();
            return "成功：连接已断开，sessionKey=" + sessionKey;
        } else {
            return "成功：会话不存在或已断开，sessionKey=" + sessionKey;
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(name = "ssh_disconnectAll", value = {"断开所有SSH连接", "断开所有SSH远程连接，清理所有会话资源"})
    public String sshDisconnectAll() {
        int count = 0;
        for (Map.Entry<String, Session> entry : SESSION_CACHE.entrySet()) {
            Session session = entry.getValue();
            if (session != null && session.isConnected()) {
                session.disconnect();
                count++;
            }
        }
        SESSION_CACHE.clear();
        return "成功：已断开全部 " + count + " 个连接";
    }

    @Override
    public void destroy(){
        sshDisconnectAll();
    }

    private static class SftpProgressReporter implements SftpProgressMonitor {
        private final IAgentExecutorService executorService;
        private final String sessionId;
        private final String toolCallId;
        private final String direction;
        private long max;
        private long transferred;
        private int lastPercent = -1;

        SftpProgressReporter(IAgentExecutorService executorService, String sessionId,
                             String toolCallId, String direction) {
            this.executorService = executorService;
            this.sessionId = sessionId;
            this.toolCallId = toolCallId;
            this.direction = direction;
        }

        @Override
        public void init(int op, String src, String dest, long max) {
            this.max = max;
            if (max > 0) {
                String msg = "[" + direction + "] 开始传输，文件大小: " + formatSize(max);
                executorService.sendToolRunningContent(sessionId, toolCallId, msg);
            }
        }

        @Override
        public boolean count(long delta) {
            transferred += delta;
            if (max > 0) {
                int percent = (int) (transferred * 100 / max);
                if (percent != lastPercent) {
                    lastPercent = percent;
                    String msg = "\n[" + direction + "进度] " + percent + "% ("
                            + formatSize(transferred) + " / " + formatSize(max) + ")";
                    executorService.sendToolRunningContent(sessionId, toolCallId, msg);
                }
            }
            return true;
        }

        @Override
        public void end() {
            String msg = "\n[" + direction + "] 传输完成";
            executorService.sendToolRunningContent(sessionId, toolCallId, msg);
        }

        private String formatSize(long bytes) {
            if (bytes < 1024) {
                return bytes + "B";
            }
            if (bytes < 1024 * 1024) {
                return String.format("%.1fKB", bytes / 1024.0);
            }
            if (bytes < 1024 * 1024 * 1024) {
                return String.format("%.1fMB", bytes / (1024.0 * 1024));
            }
            return String.format("%.1fGB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}