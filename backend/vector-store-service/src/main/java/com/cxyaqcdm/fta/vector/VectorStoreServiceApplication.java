package com.cxyaqcdm.fta.vector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.cxyaqcdm.fta.vector.client")
public class VectorStoreServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(VectorStoreServiceApplication.class, args);
    }
}