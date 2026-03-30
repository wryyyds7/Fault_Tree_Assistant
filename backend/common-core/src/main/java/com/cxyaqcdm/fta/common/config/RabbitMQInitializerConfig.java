package com.cxyaqcdm.fta.common.config;

import com.cxyaqcdm.fta.common.constants.AmqpConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public RabbitTemplate rabbitTemplate() {
        return new RabbitTemplate(connectionFactory);
    }

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

    @Bean
    public Queue documentParseQueue() {
        return QueueBuilder.durable(AmqpConstants.QUEUE_DOCUMENT_PARSE).build();
    }

    @Bean
    public Queue ragGenerateQueue() {
        return QueueBuilder.durable(AmqpConstants.QUEUE_RAG_GENERATE).build();
    }

    @Bean
    public Queue validateFaultTreeQueue() {
        return QueueBuilder.durable(AmqpConstants.QUEUE_VALIDATE_FAULT_TREE).build();
    }

    @Bean
    public Queue feedbackProcessQueue() {
        return QueueBuilder.durable(AmqpConstants.QUEUE_FEEDBACK_PROCESS).build();
    }

    @Bean
    public Binding documentParsedBinding() {
        return BindingBuilder.bind(documentParseQueue())
                .to(documentExchange())
                .with(AmqpConstants.ROUTING_KEY_DOCUMENT_PARSED)
                .noargs();
    }

    @Bean
    public Binding ragGeneratedBinding() {
        return BindingBuilder.bind(ragGenerateQueue())
                .to(ragExchange())
                .with(AmqpConstants.ROUTING_KEY_RAG_GENERATED)
                .noargs();
    }

    @Bean
    public Binding validateFaultTreeBinding() {
        return BindingBuilder.bind(validateFaultTreeQueue())
                .to(validationExchange())
                .with(AmqpConstants.ROUTING_KEY_VALIDATE_FAULT_TREE)
                .noargs();
    }

    @Bean
    public Binding feedbackCreatedBinding() {
        return BindingBuilder.bind(feedbackProcessQueue())
                .to(feedbackExchange())
                .with(AmqpConstants.ROUTING_KEY_FEEDBACK_CREATED)
                .noargs();
    }
}