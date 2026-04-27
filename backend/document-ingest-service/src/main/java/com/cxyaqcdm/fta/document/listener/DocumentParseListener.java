package com.cxyaqcdm.fta.document.listener;

import com.cxyaqcdm.fta.common.constants.AmqpConstants;
import com.cxyaqcdm.fta.document.service.DocumentService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentParseListener {

    private final DocumentService documentService;
    private final RabbitTemplate rabbitTemplate;

    @PostConstruct
    public void init() {
        log.info("========== DocumentParseListener 初始化 ==========");
        log.info("监听的队列: {}", AmqpConstants.QUEUE_DOCUMENT_PARSE);
        log.info("================================================");
    }

    @RabbitListener(queues = AmqpConstants.QUEUE_DOCUMENT_PARSE)
    public void handleDocumentParse(Map<String, Object> message) {
        log.info("★★☆ DocumentParseListener 收到消息 ☆★★");
        log.info("消息内容 keys: {}", message.keySet());
        log.info("完整消息: {}", message);

        String docId = (String) message.get("docId");
        String filePath = (String) message.get("filePath");
        String userId = (String) message.get("userId");
        String status = (String) message.get("status");

        log.info("收到文档解析请求 - docId: {}, filePath: {}, userId: {}", docId, filePath, userId);

        if (docId == null || docId.isEmpty() || "unknown".equals(docId)) {
            log.error("✗ 无效的 docId，拒绝处理! docId={}", docId);
            return;
        }

        if ("error".equals(status)) {
            log.warn("✗ 这是一条错误报告消息，不是解析请求，跳过处理! docId={}, message={}", 
                    docId, message.get("message"));
            return;
        }

        if (message.get("message") != null && message.get("message").toString().contains("error")) {
            log.warn("✗ 这是一条错误报告消息（包含error标识），跳过处理! docId={}", docId);
            return;
        }

        try {
            log.info("开始调用 documentService.processDocument...");
            long startTime = System.currentTimeMillis();
            documentService.processDocument(message);
            long duration = System.currentTimeMillis() - startTime;
            log.info("✓ documentService.processDocument 执行完成，耗时: {}ms", duration);
        } catch (Exception e) {
            log.error("✗ 处理文档 {} 时发生错误: {}", docId, e.getMessage(), e);
            log.warn("✗ 处理失败，但不移除消息！请检查文件是否存在或消息是否已过期");
        }
    }
}
