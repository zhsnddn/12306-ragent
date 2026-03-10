package com.ming.agent12306.properties;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 知识库模块配置属性 */
@Data
@ConfigurationProperties(prefix = "assistant.knowledge")
public class AssistantKnowledgeProperties {

    private boolean enabled = true;
    private int chunkSize = 800;
    private int chunkOverlap = 120;
    private int recallTopK = 4;
    private double recallScoreThreshold = 0.45D;
    private Storage storage = new Storage();
    private Milieus milvus = new Milieus();

    @Setter
    @Getter
    public static class Storage {
        private String endpoint = "http://127.0.0.1:9000";
        private String accessKey = "rustfsadmin";
        private String secretKey = "rustfsadmin";
        private String bucketName = "agent12306-knowledge";
        private String region = "us-east-1";

    }

    @Setter
    @Getter
    public static class Milieus {
        private boolean enabled = true;
        private String uri = "http://127.0.0.1:19530";
        private String collectionName = "assistant_knowledge";
        private int dimensions = 1024;
        private String token;

    }
}
