package com.cxyaqcdm.fta.validation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@SpringBootApplication
@EnableRabbit
public class RuleValidationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RuleValidationServiceApplication.class, args);
    }
}
