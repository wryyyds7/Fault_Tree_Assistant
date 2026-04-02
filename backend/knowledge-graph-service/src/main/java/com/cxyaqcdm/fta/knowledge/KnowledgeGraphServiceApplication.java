package com.cxyaqcdm.fta.knowledge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class KnowledgeGraphServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(KnowledgeGraphServiceApplication.class, args);
    }
}
