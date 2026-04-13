package com.cxyaqcdm.fta.common.util;

import com.cxyaqcdm.fta.common.constants.AmqpConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PersistentMessageSender {

    private final RabbitTemplate rabbitTemplate;

    private static final MessagePostProcessor PERSISTENT_MESSAGE_POST_PROCESSOR = message -> {
        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
        return message;
    };

    public void sendDocumentParseMessage(Object message) {
        sendMessage(AmqpConstants.EXCHANGE_DOCUMENT, AmqpConstants.ROUTING_KEY_DOCUMENT_PARSED, message);
    }

    public void sendDocumentParsedMessage(Object message) {
        sendMessage(AmqpConstants.EXCHANGE_DOCUMENT, AmqpConstants.ROUTING_KEY_DOCUMENT_PARSED, message);
    }

    public void sendRagGenerateMessage(Object message) {
        sendMessage(AmqpConstants.EXCHANGE_RAG, AmqpConstants.ROUTING_KEY_RAG_GENERATED, message);
    }

    public void sendRagGeneratedMessage(Object message) {
        sendMessage(AmqpConstants.EXCHANGE_RAG, AmqpConstants.ROUTING_KEY_RAG_GENERATED, message);
    }

    public void sendValidationRequest(Object message) {
        sendMessage(AmqpConstants.EXCHANGE_VALIDATION, AmqpConstants.ROUTING_KEY_VALIDATE_FAULT_TREE, message);
    }

    public void sendValidationResult(Object message) {
        sendMessage(AmqpConstants.EXCHANGE_VALIDATION, AmqpConstants.ROUTING_KEY_VALIDATION_RESULT, message);
    }

    public void sendFeedbackCreatedMessage(Object message) {
        sendMessage(AmqpConstants.EXCHANGE_FEEDBACK, AmqpConstants.ROUTING_KEY_FEEDBACK_CREATED, message);
    }

    public void sendFeedbackAnalyzedMessage(Object message) {
        sendMessage(AmqpConstants.EXCHANGE_FEEDBACK, AmqpConstants.ROUTING_KEY_FEEDBACK_ANALYZED, message);
    }

    public void sendModelsOptimizedMessage(Object message) {
        sendMessage(AmqpConstants.EXCHANGE_FEEDBACK, AmqpConstants.ROUTING_KEY_MODELS_OPTIMIZED, message);
    }

    public void sendKnowledgeOptimizeMessage(Object message) {
        sendMessage(AmqpConstants.EXCHANGE_KNOWLEDGE_GRAPH, AmqpConstants.ROUTING_KEY_KNOWLEDGE_OPTIMIZE, message);
    }

    public void sendRagOptimizeMessage(Object message) {
        sendMessage(AmqpConstants.EXCHANGE_RAG, AmqpConstants.ROUTING_KEY_RAG_OPTIMIZE, message);
    }

    public void sendFaultTreeGeneratedMessage(Object message) {
        sendMessage(AmqpConstants.EXCHANGE_FAULT_TREE, AmqpConstants.ROUTING_KEY_TREE_GENERATED, message);
    }

    public void sendFaultTreeFinalizedMessage(Object message) {
        sendMessage(AmqpConstants.EXCHANGE_FAULT_TREE, AmqpConstants.ROUTING_KEY_TREE_FINALIZED, message);
    }

    public void sendMessage(String exchange, String routingKey, Object message) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message, PERSISTENT_MESSAGE_POST_PROCESSOR);
            log.info("Message sent successfully to exchange: {}, routingKey: {}", exchange, routingKey);
        } catch (Exception e) {
            log.error("Failed to send message to exchange: {}, routingKey: {}, error: {}",
                    exchange, routingKey, e.getMessage(), e);
            throw new RuntimeException("Failed to send message", e);
        }
    }

    public void sendMessageToQueue(String queueName, Object message) {
        try {
            rabbitTemplate.convertAndSend(queueName, message, PERSISTENT_MESSAGE_POST_PROCESSOR);
            log.info("Message sent successfully to queue: {}", queueName);
        } catch (Exception e) {
            log.error("Failed to send message to queue: {}, error: {}", queueName, e.getMessage(), e);
            throw new RuntimeException("Failed to send message", e);
        }
    }
}
