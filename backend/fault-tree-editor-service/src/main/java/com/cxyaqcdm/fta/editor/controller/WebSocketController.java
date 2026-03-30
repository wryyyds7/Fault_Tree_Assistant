package com.cxyaqcdm.fta.editor.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
@Slf4j
public class WebSocketController {

    @MessageMapping("/edit/fault-tree")
    @SendTo("/topic/fault-tree-updates")
    public Map<String, Object> handleFaultTreeEdit(@Payload Map<String, Object> message, SimpMessageHeaderAccessor headerAccessor) {
        try {
            // 提取编辑信息
            String treeId = (String) message.get("treeId");
            Map<String, Object> editData = (Map<String, Object>) message.get("editData");
            String userId = (String) message.get("userId");
            
            log.info("Received edit for fault tree {} from user {}", treeId, userId);
            
            // 可以在这里添加编辑验证逻辑
            
            // 广播编辑消息给所有订阅该故障树的客户端
            return message;
        } catch (Exception e) {
            log.error("Error handling fault tree edit: {}", e.getMessage());
            return Map.of(
                "error", true,
                "message", "Failed to process edit"
            );
        }
    }

    @MessageMapping("/join/fault-tree")
    @SendTo("/topic/fault-tree-presence")
    public Map<String, Object> handleJoinFaultTree(@Payload Map<String, Object> message) {
        try {
            String treeId = (String) message.get("treeId");
            String userId = (String) message.get("userId");
            
            log.info("User {} joined fault tree {}", userId, treeId);
            
            // 广播用户加入消息
            return Map.of(
                "treeId", treeId,
                "userId", userId,
                "action", "join"
            );
        } catch (Exception e) {
            log.error("Error handling join fault tree: {}", e.getMessage());
            return Map.of(
                "error", true,
                "message", "Failed to process join"
            );
        }
    }

    @MessageMapping("/leave/fault-tree")
    @SendTo("/topic/fault-tree-presence")
    public Map<String, Object> handleLeaveFaultTree(@Payload Map<String, Object> message) {
        try {
            String treeId = (String) message.get("treeId");
            String userId = (String) message.get("userId");
            
            log.info("User {} left fault tree {}", userId, treeId);
            
            // 广播用户离开消息
            return Map.of(
                "treeId", treeId,
                "userId", userId,
                "action", "leave"
            );
        } catch (Exception e) {
            log.error("Error handling leave fault tree: {}", e.getMessage());
            return Map.of(
                "error", true,
                "message", "Failed to process leave"
            );
        }
    }
}
