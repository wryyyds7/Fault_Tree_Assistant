package com.cxyaqcdm.fta.document;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableRabbit
@EnableFeignClients(basePackages = "com.cxyaqcdm.fta.document.client")
public class DocumentIngestServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocumentIngestServiceApplication.class, args);
    }
}
