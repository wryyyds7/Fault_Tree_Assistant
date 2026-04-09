package com.cxyaqcdm.fta.common.config;

import com.cxyaqcdm.fta.common.constants.AmqpConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class RabbitMQInitializerConfig {

    private final ConnectionFactory connectionFactory;

    @Bean
    public RabbitAdmin rabbitAdmin() {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate() {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        template.setMandatory(true);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("Message not confirmed, correlationData: {}, cause: {}", correlationData, cause);
            } else {
                log.debug("Message confirmed: {}", correlationData);
            }
        });
        template.setReturnsCallback(returned -> {
            log.error("Message returned: {}, replyCode: {}, replyText: {}",
                    returned.getMessage(), returned.getReplyCode(), returned.getReplyText());
        });
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory() {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(10);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    // ==================== Dead Letter Exchange & Queues ====================

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(AmqpConstants.EXCHANGE_DEAD_LETTER).durable(true).build();
    }

    @Bean
    public Queue documentParseDLQ() {
        return QueueBuilder.durable(AmqpConstants.QUEUE_DOCUMENT_PARSE_DLQ)
                .withArgument("x-dead-letter-exchange", AmqpConstants.EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", AmqpConstants.ROUTING_KEY_DEAD_LETTER)
                .build();
    }

    @Bean
    public Queue ragGenerateDLQ() {
        return QueueBuilder.durable(AmqpConstants.QUEUE_RAG_GENERATE_DLQ)
                .withArgument("x-dead-letter-exchange", AmqpConstants.EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", AmqpConstants.ROUTING_KEY_DEAD_LETTER)
                .build();
    }

    @Bean
    public Queue validateFaultTreeDLQ() {
        return QueueBuilder.durable(AmqpConstants.QUEUE_VALIDATE_FAULT_TREE_DLQ)
                .withArgument("x-dead-letter-exchange", AmqpConstants.EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", AmqpConstants.ROUTING_KEY_DEAD_LETTER)
                .build();
    }

    @Bean
    public Queue feedbackProcessDLQ() {
        return QueueBuilder.durable(AmqpConstants.QUEUE_FEEDBACK_PROCESS_DLQ)
                .withArgument("x-dead-letter-exchange", AmqpConstants.EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", AmqpConstants.ROUTING_KEY_DEAD_LETTER)
                .build();
    }

    @Bean
    public Queue faultTreeGenerateDLQ() {
        return QueueBuilder.durable(AmqpConstants.QUEUE_FAULT_TREE_GENERATE_DLQ)
                .withArgument("x-dead-letter-exchange", AmqpConstants.EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", AmqpConstants.ROUTING_KEY_DEAD_LETTER)
                .build();
    }

    @Bean
    public Binding documentParseDLQBinding() {
        return new Binding(AmqpConstants.QUEUE_DOCUMENT_PARSE_DLQ, Binding.DestinationType.QUEUE,
                AmqpConstants.EXCHANGE_DEAD_LETTER, AmqpConstants.QUEUE_DOCUMENT_PARSE_DLQ, null);
    }

    @Bean
    public Binding ragGenerateDLQBinding() {
        return new Binding(AmqpConstants.QUEUE_RAG_GENERATE_DLQ, Binding.DestinationType.QUEUE,
                AmqpConstants.EXCHANGE_DEAD_LETTER, AmqpConstants.QUEUE_RAG_GENERATE_DLQ, null);
    }

    @Bean
    public Binding validateFaultTreeDLQBinding() {
        return new Binding(AmqpConstants.QUEUE_VALIDATE_FAULT_TREE_DLQ, Binding.DestinationType.QUEUE,
                AmqpConstants.EXCHANGE_DEAD_LETTER, AmqpConstants.QUEUE_VALIDATE_FAULT_TREE_DLQ, null);
    }

    @Bean
    public Binding feedbackProcessDLQBinding() {
        return new Binding(AmqpConstants.QUEUE_FEEDBACK_PROCESS_DLQ, Binding.DestinationType.QUEUE,
                AmqpConstants.EXCHANGE_DEAD_LETTER, AmqpConstants.QUEUE_FEEDBACK_PROCESS_DLQ, null);
    }

    @Bean
    public Binding faultTreeGenerateDLQBinding() {
        return new Binding(AmqpConstants.QUEUE_FAULT_TREE_GENERATE_DLQ, Binding.DestinationType.QUEUE,
                AmqpConstants.EXCHANGE_DEAD_LETTER, AmqpConstants.QUEUE_FAULT_TREE_GENERATE_DLQ, null);
    }

    // ==================== Main Exchanges ====================

    @Bean
    public Exchange documentExchange() {
        return ExchangeBuilder.directExchange(AmqpConstants.EXCHANGE_DOCUMENT).durable(true).build();
    }

    @Bean
    public Exchange ragExchange() {
        return ExchangeBuilder.directExchange(AmqpConstants.EXCHANGE_RAG).durable(true).build();
    }

    @Bean
    public Exchange validationExchange() {
        return ExchangeBuilder.directExchange(AmqpConstants.EXCHANGE_VALIDATION).durable(true).build();
    }

    @Bean
    public Exchange feedbackExchange() {
        return ExchangeBuilder.directExchange(AmqpConstants.EXCHANGE_FEEDBACK).durable(true).build();
    }

    @Bean
    public Exchange faultTreeExchange() {
        return ExchangeBuilder.directExchange(AmqpConstants.EXCHANGE_FAULT_TREE).durable(true).build();
    }

    @Bean
    public Exchange knowledgeGraphExchange() {
        return ExchangeBuilder.directExchange(AmqpConstants.EXCHANGE_KNOWLEDGE_GRAPH).durable(true).build();
    }

    // ==================== Main Queues with DLQ ====================

    @Bean
    public Queue documentParseQueue() {
        return QueueBuilder.durable(AmqpConstants.QUEUE_DOCUMENT_PARSE)
                .withArgument("x-dead-letter-exchange", AmqpConstants.EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", AmqpConstants.QUEUE_DOCUMENT_PARSE_DLQ)
                .build();
    }

    @Bean
    public Queue ragGenerateQueue() {
        return QueueBuilder.durable(AmqpConstants.QUEUE_RAG_GENERATE)
                .withArgument("x-dead-letter-exchange", AmqpConstants.EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", AmqpConstants.QUEUE_RAG_GENERATE_DLQ)
                .build();
    }

    @Bean
    public Queue validateFaultTreeQueue() {
        return QueueBuilder.durable(AmqpConstants.QUEUE_VALIDATE_FAULT_TREE)
                .withArgument("x-dead-letter-exchange", AmqpConstants.EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", AmqpConstants.QUEUE_VALIDATE_FAULT_TREE_DLQ)
                .build();
    }

    @Bean
    public Queue feedbackProcessQueue() {
        return QueueBuilder.durable(AmqpConstants.QUEUE_FEEDBACK_PROCESS)
                .withArgument("x-dead-letter-exchange", AmqpConstants.EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", AmqpConstants.QUEUE_FEEDBACK_PROCESS_DLQ)
                .build();
    }

    @Bean
    public Queue faultTreeGenerateQueue() {
        return QueueBuilder.durable(AmqpConstants.QUEUE_FAULT_TREE_GENERATE)
                .withArgument("x-dead-letter-exchange", AmqpConstants.EXCHANGE_DEAD_LETTER)
                .withArgument("x-dead-letter-routing-key", AmqpConstants.QUEUE_FAULT_TREE_GENERATE_DLQ)
                .build();
    }

    // ==================== Main Bindings ====================

    @Bean
    public Binding documentParseBinding() {
        return new Binding(AmqpConstants.QUEUE_DOCUMENT_PARSE, Binding.DestinationType.QUEUE,
                AmqpConstants.EXCHANGE_DOCUMENT, AmqpConstants.ROUTING_KEY_DOCUMENT_PARSE_REQUEST, null);
    }

    @Bean
    public Binding ragGeneratedBinding() {
        return new Binding(AmqpConstants.QUEUE_RAG_GENERATE, Binding.DestinationType.QUEUE,
                AmqpConstants.EXCHANGE_RAG, AmqpConstants.ROUTING_KEY_RAG_GENERATED, null);
    }

    @Bean
    public Binding validateFaultTreeBinding() {
        return new Binding(AmqpConstants.QUEUE_VALIDATE_FAULT_TREE, Binding.DestinationType.QUEUE,
                AmqpConstants.EXCHANGE_VALIDATION, AmqpConstants.ROUTING_KEY_VALIDATE_FAULT_TREE, null);
    }

    @Bean
    public Binding feedbackCreatedBinding() {
        return new Binding(AmqpConstants.QUEUE_FEEDBACK_PROCESS, Binding.DestinationType.QUEUE,
                AmqpConstants.EXCHANGE_FEEDBACK, AmqpConstants.ROUTING_KEY_FEEDBACK_CREATED, null);
    }

    @Bean
    public Binding faultTreeGeneratedBinding() {
        return new Binding(AmqpConstants.QUEUE_FAULT_TREE_GENERATE, Binding.DestinationType.QUEUE,
                AmqpConstants.EXCHANGE_FAULT_TREE, AmqpConstants.ROUTING_KEY_TREE_GENERATED, null);
    }
}
