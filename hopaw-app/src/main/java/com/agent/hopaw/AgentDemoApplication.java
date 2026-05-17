package com.agent.hopaw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AgentDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentDemoApplication.class, args);
    }
}
