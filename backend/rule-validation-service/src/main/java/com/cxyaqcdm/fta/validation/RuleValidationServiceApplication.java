package com.cxyaqcdm.fta.validation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableRabbit
@ComponentScan(basePackages = {"com.cxyaqcdm.fta.validation", "com.cxyaqcdm.fta.common"})
public class RuleValidationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RuleValidationServiceApplication.class, args);
    }
}
