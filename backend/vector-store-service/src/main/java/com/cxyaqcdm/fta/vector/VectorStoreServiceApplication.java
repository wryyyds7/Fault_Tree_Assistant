package com.cxyaqcdm.fta.vector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.cxyaqcdm.fta.vector.client")
@ComponentScan(basePackages = {"com.cxyaqcdm.fta.vector", "com.cxyaqcdm.fta.common"})
public class    VectorStoreServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(VectorStoreServiceApplication.class, args);
    }
}