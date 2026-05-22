package com.agent.hopaw.pluginrepo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class HopawPluginRepoApplication {

    public static void main(String[] args) {
        SpringApplication.run(HopawPluginRepoApplication.class, args);
    }
}