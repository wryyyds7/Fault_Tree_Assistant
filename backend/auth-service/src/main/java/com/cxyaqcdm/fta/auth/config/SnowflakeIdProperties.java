package com.cxyaqcdm.fta.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "snowflake")
public class SnowflakeIdProperties {
    private long workerId = 1;
    private long datacenterId = 1;
}