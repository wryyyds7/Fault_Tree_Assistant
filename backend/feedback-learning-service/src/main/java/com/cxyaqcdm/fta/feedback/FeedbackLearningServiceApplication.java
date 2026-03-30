package com.cxyaqcdm.fta.feedback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@SpringBootApplication
@EnableRabbit
public class FeedbackLearningServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FeedbackLearningServiceApplication.class, args);
    }
}
