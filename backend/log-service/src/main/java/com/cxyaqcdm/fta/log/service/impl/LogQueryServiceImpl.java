package com.cxyaqcdm.fta.log.service.impl;

import com.cxyaqcdm.fta.log.entity.OperationLog;
import com.cxyaqcdm.fta.log.repository.OperationLogRepository;
import com.cxyaqcdm.fta.log.service.LogQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogQueryServiceImpl implements LogQueryService {

    private final OperationLogRepository operationLogRepository;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public List<OperationLog> queryLogs(String userId, String serviceName, String logLevel,
                                          String operationType, String startTime, String endTime,
                                          int pageNum, int pageSize) {
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));

        Specification<OperationLog> spec = buildSpecification(
                userId, serviceName, logLevel, operationType, startTime, endTime);

        Page<OperationLog> page = operationLogRepository.findAll(spec, pageable);
        return page.getContent();
    }

    @Override
    public long countLogs(String userId, String serviceName, String logLevel,
                           String operationType, String startTime, String endTime) {
        Specification<OperationLog> spec = buildSpecification(
                userId, serviceName, logLevel, operationType, startTime, endTime);
        return operationLogRepository.count(spec);
    }

    @Override
    public List<OperationLog> getLogsForExport(String userId, String serviceName, String logLevel,
                                                 String operationType, String startTime, String endTime) {
        Specification<OperationLog> spec = buildSpecification(
                userId, serviceName, logLevel, operationType, startTime, endTime);
        return operationLogRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createTime"));
    }

    private Specification<OperationLog> buildSpecification(String userId, String serviceName,
                                                            String logLevel, String operationType,
                                                            String startTime, String endTime) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userId != null && !userId.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("userId"), userId));
            }
            if (serviceName != null && !serviceName.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("serviceName"), serviceName));
            }
            if (logLevel != null && !logLevel.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("logLevel"), logLevel));
            }
            if (operationType != null && !operationType.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("operationType"), operationType));
            }
            if (startTime != null && !startTime.isEmpty()) {
                LocalDateTime start = LocalDateTime.parse(startTime, FORMATTER);
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createTime"), start));
            }
            if (endTime != null && !endTime.isEmpty()) {
                LocalDateTime end = LocalDateTime.parse(endTime, FORMATTER);
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createTime"), end));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
