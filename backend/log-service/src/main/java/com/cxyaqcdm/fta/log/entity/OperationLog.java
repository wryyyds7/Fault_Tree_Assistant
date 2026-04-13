package com.cxyaqcdm.fta.log.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "operation_log")
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "operation_log_seq")
    @SequenceGenerator(name = "operation_log_seq", sequenceName = "operation_log_seq", allocationSize = 1)
    private Long id;

    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "username", length = 128)
    private String username;

    @Column(name = "service_name", length = 64, nullable = false)
    private String serviceName;

    @Column(name = "log_level", length = 16, nullable = false)
    private String logLevel;

    @Column(name = "operation_type", length = 64, nullable = false)
    private String operationType;

    @Column(name = "operation_detail", columnDefinition = "CLOB")
    private String operationDetail;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "request_method", length = 10)
    private String requestMethod;

    @Column(name = "request_path", length = 512)
    private String requestPath;

    @Column(name = "request_params", columnDefinition = "CLOB")
    private String requestParams;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "execution_time")
    private Long executionTime;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}
