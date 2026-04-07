package com.cxyaqcdm.fta.document;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableRabbit
@EnableFeignClients(basePackages = "com.cxyaqcdm.fta.document.client")
@ComponentScan(basePackages = {"com.cxyaqcdm.fta.document", "com.cxyaqcdm.fta.common"})
public class DocumentIngestServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocumentIngestServiceApplication.class, args);
    }
}
