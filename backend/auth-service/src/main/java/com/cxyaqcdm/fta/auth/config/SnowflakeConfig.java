package com.cxyaqcdm.fta.auth.config;

import com.cxyaqcdm.fta.auth.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class SnowflakeConfig {

    private final SnowflakeIdProperties snowflakeIdProperties;

    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator() {
        return new SnowflakeIdGenerator(
                snowflakeIdProperties.getWorkerId(),
                snowflakeIdProperties.getDatacenterId()
        );
    }
}