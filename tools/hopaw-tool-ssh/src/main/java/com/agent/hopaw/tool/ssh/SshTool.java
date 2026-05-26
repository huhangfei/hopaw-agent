package com.agent.hopaw.tool.ssh;

import com.agent.hopaw.infra.service.IAgentExecutorService;
import com.agent.hopaw.infra.util.InvocationParametersWrapper;
import com.jcraft.jsch.*;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.agent.hopaw.infra.tool.AgentTool;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SSH远程连接工具插件
 * 注意：作为插件使用时，不要加 @Component 注解，由插件加载器实例化并通过 @Autowired 注入依赖
 * @author hhf
 */
public class SshTool implements AgentTool {
    private static final Logger logger = LoggerFactory.getLogger(SshTool.class);
    private static final Map<String, Session> SESSION_CACHE = new ConcurrentHashMap<>();

    @Autowired
    private IAgentExecutorService agentExecutorService;

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
        return "sshTool";
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

    @Tool("SSH远程连接服务器，建立SSH会话。密码属于敏感信息，如果账号密码错误不要自行猜测，请搜索记忆或询问用户。连接成功后会返回sessionKey，后续操作需要使用此sessionKey。")
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

        if (SESSION_CACHE.containsKey(sessionKey)) {
            Session existing = SESSION_CACHE.get(sessionKey);
            if (existing.isConnected()) {
                return "成功：已存在连接，sessionKey=" + sessionKey;
            }
        }

        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(username, host, portVal);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(30000);

            SESSION_CACHE.put(sessionKey, session);
            return "成功：连接已建立，sessionKey=" + sessionKey;
        } catch (JSchException e) {
            logger.error("SSH connection failed", e);
            return "错误：连接失败 - " + e.getMessage();
        }
    }

    @Tool("在已连接的SSH会话上执行远程命令，需要先通过sshConnect建立连接获取sessionKey")
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

        Session session = SESSION_CACHE.get(sessionKey);
        if (session == null || !session.isConnected()) {
            return "错误：会话未连接或不存在，sessionKey=" + sessionKey;
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

    @Tool("通过SFTP上传本地文件到远程服务器，需要先建立SSH连接")
    public String sshUpload(
            @P(description = "会话标识，由sshConnect返回的sessionKey") String sessionKey,
            @P(description = "本地文件路径") String localPath,
            @P(description = "远程服务器目标路径") String remotePath,
            @P(description = "最大执行时间（秒），默认300秒", required = false) Integer timeout,
            InvocationParameters invocationParameters) {
        if (sessionKey == null || localPath == null || remotePath == null) {
            return "错误：sessionKey、localPath、remotePath 不能为空";
        }

        Session session = SESSION_CACHE.get(sessionKey);
        if (session == null || !session.isConnected()) {
            return "错误：会话未连接或不存在，sessionKey=" + sessionKey;
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

    @Tool("通过SFTP从远程服务器下载文件到本地，需要先建立SSH连接")
    public String sshDownload(
            @P(description = "会话标识，由sshConnect返回的sessionKey") String sessionKey,
            @P(description = "远程服务器文件路径") String remotePath,
            @P(description = "本地保存路径") String localPath,
            @P(description = "最大执行时间（秒），默认300秒", required = false) Integer timeout,
            InvocationParameters invocationParameters) {
        if (sessionKey == null || remotePath == null || localPath == null) {
            return "错误：sessionKey、remotePath、localPath 不能为空";
        }

        Session session = SESSION_CACHE.get(sessionKey);
        if (session == null || !session.isConnected()) {
            return "错误：会话未连接或不存在，sessionKey=" + sessionKey;
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

    @Tool("断开SSH远程连接，释放会话资源")
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

    @Tool("断开所有SSH远程连接，清理所有会话资源")
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