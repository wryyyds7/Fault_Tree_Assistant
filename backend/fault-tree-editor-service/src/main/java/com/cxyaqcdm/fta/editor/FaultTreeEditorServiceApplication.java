package com.cxyaqcdm.fta.editor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.cxyaqcdm.fta.editor", "com.cxyaqcdm.fta.common"})
public class FaultTreeEditorServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FaultTreeEditorServiceApplication.class, args);
    }
}
