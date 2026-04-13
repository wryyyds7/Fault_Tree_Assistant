package com.cxyaqcdm.fta.feedback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableRabbit
@ComponentScan(basePackages = {"com.cxyaqcdm.fta.feedback", "com.cxyaqcdm.fta.common"})
public classdevFeedbackLearningServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FeedbackLearningServiceApplication.class, args);
    }
}
