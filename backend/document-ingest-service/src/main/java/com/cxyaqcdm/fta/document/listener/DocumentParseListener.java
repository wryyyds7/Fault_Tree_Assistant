package com.cxyaqcdm.fta.document.listener;

import com.cxyaqcdm.fta.common.constants.AmqpConstants;
import com.cxyaqcdm.fta.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentParseListener {

    private final DocumentService documentService;

    @RabbitListener(queues = AmqpConstants.QUEUE_DOCUMENT_PARSE)
    public void handleDocumentParse(Map<String, Object> message) {
        String docId = (String) message.get("docId");
        log.info("Received document parse request for: {}", docId);
        documentService.processDocument(message);
    }
}
