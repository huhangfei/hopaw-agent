package com.agent.hopaw.tools;

import com.jcraft.jsch.*;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SshTool implements AgentTool {

    private static final Logger logger = LoggerFactory.getLogger(SshTool.class);
    private static final Map<String, Session> sessionCache = new ConcurrentHashMap<>();

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
        return "ssh-tool";
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

        if (sessionCache.containsKey(sessionKey)) {
            Session existing = sessionCache.get(sessionKey);
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

            sessionCache.put(sessionKey, session);
            return "成功：连接已建立，sessionKey=" + sessionKey;
        } catch (JSchException e) {
            logger.error("SSH connection failed", e);
            return "错误：连接失败 - " + e.getMessage();
        }
    }

    @Tool("在已连接的SSH会话上执行远程命令，需要先通过sshConnect建立连接获取sessionKey")
    public String sshExec(
            @P(description = "会话标识，由sshConnect返回的sessionKey") String sessionKey,
            @P(description = "要执行的远程命令") String command) {
        if (sessionKey == null || sessionKey.trim().isEmpty()) {
            return "错误：sessionKey 不能为空";
        }
        if (command == null || command.trim().isEmpty()) {
            return "错误：command 不能为空";
        }

        Session session = sessionCache.get(sessionKey);
        if (session == null || !session.isConnected()) {
            return "错误：会话未连接或不存在，sessionKey=" + sessionKey;
        }

        try {
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);

            java.io.InputStream in = channel.getInputStream();
            channel.connect(30000);

            StringBuilder output = new StringBuilder();
            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) break;
                    output.append(new String(tmp, 0, i));
                }
                if (channel.isClosed()) break;
                Thread.sleep(100);
            }

            int exitCode = channel.getExitStatus();
            channel.disconnect();
            return "退出码: " + exitCode + "\n" + output;
        } catch (Exception e) {
            logger.error("SSH exec failed", e);
            return "错误：命令执行失败 - " + e.getMessage();
        }
    }

    @Tool("通过SFTP上传本地文件到远程服务器，需要先建立SSH连接")
    public String sshUpload(
            @P(description = "会话标识，由sshConnect返回的sessionKey") String sessionKey,
            @P(description = "本地文件路径") String localPath,
            @P(description = "远程服务器目标路径") String remotePath) {
        if (sessionKey == null || localPath == null || remotePath == null) {
            return "错误：sessionKey、localPath、remotePath 不能为空";
        }

        Session session = sessionCache.get(sessionKey);
        if (session == null || !session.isConnected()) {
            return "错误：会话未连接或不存在，sessionKey=" + sessionKey;
        }

        try {
            ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(30000);
            sftp.put(localPath, remotePath);
            sftp.disconnect();
            return "成功：文件已上传至 " + remotePath;
        } catch (Exception e) {
            logger.error("SSH upload failed", e);
            return "错误：上传失败 - " + e.getMessage();
        }
    }

    @Tool("通过SFTP从远程服务器下载文件到本地，需要先建立SSH连接")
    public String sshDownload(
            @P(description = "会话标识，由sshConnect返回的sessionKey") String sessionKey,
            @P(description = "远程服务器文件路径") String remotePath,
            @P(description = "本地保存路径") String localPath) {
        if (sessionKey == null || remotePath == null || localPath == null) {
            return "错误：sessionKey、remotePath、localPath 不能为空";
        }

        Session session = sessionCache.get(sessionKey);
        if (session == null || !session.isConnected()) {
            return "错误：会话未连接或不存在，sessionKey=" + sessionKey;
        }

        try {
            ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(30000);
            sftp.get(remotePath, localPath);
            sftp.disconnect();
            return "成功：文件已下载至 " + localPath;
        } catch (Exception e) {
            logger.error("SSH download failed", e);
            return "错误：下载失败 - " + e.getMessage();
        }
    }

    @Tool("断开SSH远程连接，释放会话资源")
    public String sshDisconnect(
            @P(description = "会话标识，由sshConnect返回的sessionKey") String sessionKey) {
        if (sessionKey == null || sessionKey.trim().isEmpty()) {
            return "错误：sessionKey 不能为空";
        }

        Session session = sessionCache.remove(sessionKey);
        if (session != null && session.isConnected()) {
            session.disconnect();
            return "成功：连接已断开，sessionKey=" + sessionKey;
        } else {
            return "成功：会话不存在或已断开，sessionKey=" + sessionKey;
        }
    }
}
