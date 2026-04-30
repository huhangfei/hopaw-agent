package com.agent.hopaw.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.jcraft.jsch.*;
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
        return "SSH远程连接工具。";
    }

    @Tool(name = "ssh", value = "SSH remote connection tool. Passwords are highly sensitive. If the account password is incorrect, don't make guesses on your own. Either search your memory or ask the user. " +
            "Connect: {\"action\":\"connect\",\"host\":\"IP\",\"port\":22,\"username\":\"user\",\"password\":\"pass\"} " +
            "Exec: {\"action\":\"exec\",\"sessionKey\":\"host:port\",\"command\":\"ls -la\"} " +
            "Upload: {\"action\":\"upload\",\"sessionKey\":\"host:port\",\"localPath\":\"/local/file\",\"remotePath\":\"/remote/file\"} " +
            "Download: {\"action\":\"download\",\"sessionKey\":\"host:port\",\"remotePath\":\"/remote/file\",\"localPath\":\"/local/file\"} " +
            "Disconnect: {\"action\":\"disconnect\",\"sessionKey\":\"host:port\"}")
    public String executeRemoteCmd(String paramsJson) {
        try {
            JSONObject params = JSON.parseObject(paramsJson);
            String action = params.getString("action");

            if ("connect".equals(action)) {
                return connect(params);
            } else if ("exec".equals(action)) {
                return exec(params);
            } else if ("upload".equals(action)) {
                return upload(params);
            } else if ("download".equals(action)) {
                return download(params);
            } else if ("disconnect".equals(action)) {
                return disconnect(params);
            } else {
                return "{\"error\":\"Unknown action: " + action + "\"}";
            }
        } catch (Exception e) {
            logger.error("SSH tool error", e);
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private String connect(JSONObject params) {
        String host = params.getString("host");
        int port = params.getInteger("port");
        String username = params.getString("username");
        String password = params.getString("password");

        if (host == null || port <=0 || username == null || password == null) {
            return "{\"error\":\"Missing required parameters: host, port, username, password\"}";
        }

        String sessionKey = host + ":" + port;

        if (sessionCache.containsKey(sessionKey)) {
            Session existing = sessionCache.get(sessionKey);
            if (existing.isConnected()) {
                return "{\"success\":\"Already connected\",\"sessionKey\":\"" + sessionKey + "\"}";
            }
        }

        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(username, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(30000);

            sessionCache.put(sessionKey, session);
            return "{\"success\":\"Connected\",\"sessionKey\":\"" + sessionKey + "\"}";
        } catch (JSchException e) {
            logger.error("SSH connection failed", e);
            return "{\"error\":\"Connection failed: " + e.getMessage() + "\"}";
        }
    }

    private String exec(JSONObject params) {
        String sessionKey = params.getString("sessionKey");
        String command = params.getString("command");

        if (sessionKey == null || command == null) {
            return "{\"error\":\"Missing required parameters: sessionKey, command\"}";
        }

        Session session = sessionCache.get(sessionKey);
        if (session == null || !session.isConnected()) {
            return "{\"error\":\"Session not found or not connected: " + sessionKey + "\"}";
        }

        try {
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            channel.setErrStream(System.err);

            java.io.InputStream in = channel.getInputStream();
            channel.connect(30000);

            StringBuilder output = new StringBuilder();
            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(tmp, 0, 1024);
                    if (i < 0) {break;}
                    output.append(new String(tmp, 0, i));
                }
                if (channel.isClosed()) {
                    break;
                }
                Thread.sleep(100);
            }

            channel.disconnect();
            return "{\"success\":true,\"output\":\"" + escapeJson(output.toString()) + "\",\"exitCode\":" + channel.getExitStatus() + "}";
        } catch (Exception e) {
            logger.error("SSH exec failed", e);
            return "{\"error\":\"Exec failed: " + e.getMessage() + "\"}";
        }
    }

    private String upload(JSONObject params) {
        String sessionKey = params.getString("sessionKey");
        String localPath = params.getString("localPath");
        String remotePath = params.getString("remotePath");

        if (sessionKey == null || localPath == null || remotePath == null) {
            return "{\"error\":\"Missing required parameters: sessionKey, localPath, remotePath\"}";
        }

        Session session = sessionCache.get(sessionKey);
        if (session == null || !session.isConnected()) {
            return "{\"error\":\"Session not found or not connected: " + sessionKey + "\"}";
        }

        try {
            ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(30000);

            sftp.put(localPath, remotePath);
            sftp.disconnect();

            return "{\"success\":\"Uploaded\",\"localPath\":\"" + localPath + "\",\"remotePath\":\"" + remotePath + "\"}";
        } catch (Exception e) {
            logger.error("SSH upload failed", e);
            return "{\"error\":\"Upload failed: " + e.getMessage() + "\"}";
        }
    }

    private String download(JSONObject params) {
        String sessionKey = params.getString("sessionKey");
        String remotePath = params.getString("remotePath");
        String localPath = params.getString("localPath");

        if (sessionKey == null || remotePath == null || localPath == null) {
            return "{\"error\":\"Missing required parameters: sessionKey, remotePath, localPath\"}";
        }

        Session session = sessionCache.get(sessionKey);
        if (session == null || !session.isConnected()) {
            return "{\"error\":\"Session not found or not connected: " + sessionKey + "\"}";
        }

        try {
            ChannelSftp sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(30000);

            sftp.get(remotePath, localPath);
            sftp.disconnect();

            return "{\"success\":\"Downloaded\",\"remotePath\":\"" + remotePath + "\",\"localPath\":\"" + localPath + "\"}";
        } catch (Exception e) {
            logger.error("SSH download failed", e);
            return "{\"error\":\"Download failed: " + e.getMessage() + "\"}";
        }
    }

    private String disconnect(JSONObject params) {
        String sessionKey = params.getString("sessionKey");

        if (sessionKey == null) {
            return "{\"error\":\"Missing required parameter: sessionKey\"}";
        }

        Session session = sessionCache.remove(sessionKey);
        if (session != null && session.isConnected()) {
            session.disconnect();
            return "{\"success\":\"Disconnected\",\"sessionKey\":\"" + sessionKey + "\"}";
        } else {
            return "{\"success\":\"Session not found or already disconnected\",\"sessionKey\":\"" + sessionKey + "\"}";
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}