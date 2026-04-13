package com.cxyaqcdm.fta.common.listener;

import com.cxyaqcdm.fta.common.constants.AmqpConstants;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeadLetterQueueHandler {

    @RabbitListener(queues = AmqpConstants.QUEUE_DOCUMENT_PARSE_DLQ)
    public void handleDocumentParseDLQ(Message message, Channel channel,
                                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handleDeadLetterMessage("Document Parse", message, channel, deliveryTag);
    }

    @RabbitListener(queues = AmqpConstants.QUEUE_RAG_GENERATE_DLQ)
    public void handleRagGenerateDLQ(Message message, Channel channel,
                                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handleDeadLetterMessage("RAG Generate", message, channel, deliveryTag);
    }

    @RabbitListener(queues = AmqpConstants.QUEUE_VALIDATE_FAULT_TREE_DLQ)
    public void handleValidateFaultTreeDLQ(Message message, Channel channel,
                                            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handleDeadLetterMessage("Validate Fault Tree", message, channel, deliveryTag);
    }

    @RabbitListener(queues = AmqpConstants.QUEUE_FEEDBACK_PROCESS_DLQ)
    public void handleFeedbackProcessDLQ(Message message, Channel channel,
                                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handleDeadLetterMessage("Feedback Process", message, channel, deliveryTag);
    }

    @RabbitListener(queues = AmqpConstants.QUEUE_FAULT_TREE_GENERATE_DLQ)
    public void handleFaultTreeGenerateDLQ(Message message, Channel channel,
                                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handleDeadLetterMessage("Fault Tree Generate", message, channel, deliveryTag);
    }

    private void handleDeadLetterMessage(String queueType, Message message, Channel channel, long deliveryTag) throws IOException {
        try {
            String body = new String(message.getBody());
            Map<String, Object> headers = message.getMessageProperties().getHeaders();

            log.error("DLQ Message received from {} DLQ - Body: {}, Headers: {}",
                    queueType, body, headers);

            long deathTime = 0;
            if (headers.containsKey("x-death")) {
                Object xDeath = headers.get("x-death");
                log.error("x-death header: {}", xDeath);
            }

            String originalExchange = (String) headers.get("x-first-death-exchange");
            String originalRoutingKey = (String) headers.get("x-first-death-queue");
            Long deathCount = (Long) headers.get("x-death-count");

            log.error("Original Exchange: {}, Original Routing Key: {}, Death Count: {}",
                    originalExchange, originalRoutingKey, deathCount);

            logFailedMessage(queueType, body, headers);

            channel.basicAck(deliveryTag, false);
            log.info("DLQ message acknowledged: {}", queueType);

        } catch (Exception e) {
            log.error("Error processing DLQ message for {}: {}", queueType, e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private void logFailedMessage(String queueType, String body, Map<String, Object> headers) {
        log.error("========== FAILED MESSAGE DETAILS ==========");
        log.error("Queue Type: {}", queueType);
        log.error("Message Body: {}", body);
        log.error("Content Type: {}", headers.get("contentType"));
        log.error("Delivery Mode: {}", headers.get("deliveryMode"));
        log.error("Timestamp: {}", headers.get("timestamp"));
        log.error("==============================================");
    }
}
