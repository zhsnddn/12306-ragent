package com.ming.agent12306.properties;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 会话记忆模块配置属性 */
@Data
@ConfigurationProperties(prefix = "assistant.memory")
public class AssistantMemoryProperties {

    private boolean enabled = true;
    private int windowSize = 6;
    private int summaryBatchSize = 4;
    private int recallTopK = 3;
    private long ttlHours = 24;
    private double recallScoreThreshold = 0.6D;
    private Milvus milvus = new Milvus();

    @Setter
    @Getter
    public static class Milvus {
        private boolean enabled = false;
        private String uri = "http://127.0.0.1:19530";
        private String collectionName = "assistant_memory";
        private int dimensions = 1024;
        private String token;
    }
}
