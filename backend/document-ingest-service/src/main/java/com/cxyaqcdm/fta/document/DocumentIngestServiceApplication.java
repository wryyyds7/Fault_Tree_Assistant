package com.cxyaqcdm.fta.document;

import com.cxyaqcdm.fta.common.config.RabbitMQInitializerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableRabbit
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.cxyaqcdm.fta.document.client")
@ComponentScan(basePackages = {"com.cxyaqcdm.fta.document", "com.cxyaqcdm.fta.common"})
@Import(RabbitMQInitializerConfig.class)
public class DocumentIngestServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocumentIngestServiceApplication.class, args);
    }
}
