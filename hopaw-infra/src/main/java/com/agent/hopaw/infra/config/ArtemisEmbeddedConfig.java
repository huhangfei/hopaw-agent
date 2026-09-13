package com.agent.hopaw.infra.config;

import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;

import javax.jms.ConnectionFactory;

/**
 * 嵌入式 ActiveMQ Artemis 配置：零外部依赖，内存队列
 */
@org.springframework.context.annotation.Configuration
public class ArtemisEmbeddedConfig {

    private static final Logger log = LoggerFactory.getLogger(ArtemisEmbeddedConfig.class);

    @Bean(initMethod = "start", destroyMethod = "stop")
    public EmbeddedActiveMQ embeddedActiveMQ() throws Exception {
        org.apache.activemq.artemis.core.config.Configuration config = new ConfigurationImpl();

        config.addAcceptorConfiguration("invm", "vm://0");
        config.setPersistenceEnabled(false);
        config.setSecurityEnabled(false);

        AddressSettings addrSettings = new AddressSettings();
        addrSettings.setDeadLetterAddress(new org.apache.activemq.artemis.api.core.SimpleString("DLQ"));
        addrSettings.setExpiryAddress(new org.apache.activemq.artemis.api.core.SimpleString("ExpiryQueue"));
        addrSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
        addrSettings.setAutoCreateQueues(true);
        addrSettings.setAutoCreateAddresses(true);
        config.getAddressSettings().put("#", addrSettings);

        log.info("启动嵌入式 ActiveMQ Artemis（内存模式）");
        EmbeddedActiveMQ server = new EmbeddedActiveMQ();
        server.setConfiguration(config);
        return server;
    }

    @Bean
    @DependsOn("embeddedActiveMQ")
    public ConnectionFactory jmsConnectionFactory() {
        // JmsTemplate 默认每次发送都新建并关闭 Connection，InVM 传输对正常关闭也会回调
        // connectionDestroyed 并打印 failover DEBUG 日志（AMQ219006），造成周期性噪音；
        // 用 CachingConnectionFactory 复用单条共享连接 + 缓存 Session，消除连接反复创建/销毁
        ActiveMQConnectionFactory target = new ActiveMQConnectionFactory("vm://0");
        CachingConnectionFactory caching = new CachingConnectionFactory(target);
        caching.setSessionCacheSize(16);
        return caching;
    }

    @Bean
    @DependsOn("jmsConnectionFactory")
    public JmsTemplate jmsTemplate(ConnectionFactory jmsConnectionFactory) {
        return new JmsTemplate(jmsConnectionFactory);
    }
}
