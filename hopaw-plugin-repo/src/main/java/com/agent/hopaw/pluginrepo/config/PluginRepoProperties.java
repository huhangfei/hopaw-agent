package com.agent.hopaw.pluginrepo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "plugin-repo")
public class PluginRepoProperties {

    private String packagesDir = "plugin-packages";
    private String baseUrl;

    public String getPackagesDir() {
        return packagesDir;
    }

    public void setPackagesDir(String packagesDir) {
        this.packagesDir = packagesDir;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}