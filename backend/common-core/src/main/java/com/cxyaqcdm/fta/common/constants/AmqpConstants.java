package com.cxyaqcdm.fta.common.constants;

public class AmqpConstants {
    // Exchanges
    public static final String EXCHANGE_DOCUMENT = "exchange.document";
    public static final String EXCHANGE_RAG = "exchange.rag";
    public static final String EXCHANGE_FAULT_TREE = "exchange.fault-tree";
    public static final String EXCHANGE_VALIDATION = "exchange.validation";
    public static final String EXCHANGE_FEEDBACK = "exchange.feedback";
    public static final String EXCHANGE_KNOWLEDGE_GRAPH = "exchange.knowledge-graph";
    
    // Queues
    public static final String QUEUE_DOCUMENT_PARSE = "queue.document.parse";
    public static final String QUEUE_RAG_GENERATE = "queue.rag.generate";
    public static final String QUEUE_VALIDATE_FAULT_TREE = "queue.validation.fault.tree";
    public static final String QUEUE_FEEDBACK_PROCESS = "queue.feedback.process";
    
    // Routing Keys
    public static final String ROUTING_KEY_DOCUMENT_PARSED = "document.parsed";
    public static final String ROUTING_KEY_RAG_GENERATED = "rag.generated";
    public static final String ROUTING_KEY_TREE_FINALIZED = "tree.finalized";
    public static final String ROUTING_KEY_VALIDATE_FAULT_TREE = "validation.fault.tree";
    public static final String ROUTING_KEY_VALIDATION_RESULT = "validation.result";
    public static final String ROUTING_KEY_FEEDBACK_CREATED = "feedback.created";
    public static final String ROUTING_KEY_FEEDBACK_ANALYZED = "feedback.analyzed";
    public static final String ROUTING_KEY_MODELS_OPTIMIZED = "models.optimized";
    public static final String ROUTING_KEY_KNOWLEDGE_OPTIMIZE = "knowledge.optimize";
    public static final String ROUTING_KEY_RAG_OPTIMIZE = "rag.optimize";
}
