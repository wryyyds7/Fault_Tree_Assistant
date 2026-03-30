package com.cxyaqcdm.fta.editor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${websocket.endpoint}")
    private String websocketEndpoint;

    @Value("${websocket.broker.prefix}")
    private String brokerPrefix;

    @Value("${websocket.application.prefix}")
    private String applicationPrefix;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用消息代理，设置前缀
        config.enableSimpleBroker(brokerPrefix);
        // 设置应用前缀，用于客户端发送消息
        config.setApplicationDestinationPrefixes(applicationPrefix);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册WebSocket端点，允许跨域
        registry.addEndpoint(websocketEndpoint)
                .setAllowedOrigins("*")
                .withSockJS();
    }
}
