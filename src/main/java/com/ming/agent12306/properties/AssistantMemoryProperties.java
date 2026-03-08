package com.ming.agent12306.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assistant.memory")
public class AssistantMemoryProperties {

    private boolean enabled = true;
    private int windowSize = 6;
    private int summaryBatchSize = 4;
    private int recallTopK = 3;
    private long ttlHours = 24;
    private double recallScoreThreshold = 0.6D;
    private Milvus milvus = new Milvus();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(int windowSize) {
        this.windowSize = windowSize;
    }

    public int getSummaryBatchSize() {
        return summaryBatchSize;
    }

    public void setSummaryBatchSize(int summaryBatchSize) {
        this.summaryBatchSize = summaryBatchSize;
    }

    public long getTtlHours() {
        return ttlHours;
    }

    public void setTtlHours(long ttlHours) {
        this.ttlHours = ttlHours;
    }

    public int getRecallTopK() {
        return recallTopK;
    }

    public void setRecallTopK(int recallTopK) {
        this.recallTopK = recallTopK;
    }

    public double getRecallScoreThreshold() {
        return recallScoreThreshold;
    }

    public void setRecallScoreThreshold(double recallScoreThreshold) {
        this.recallScoreThreshold = recallScoreThreshold;
    }

    public Milvus getMilvus() {
        return milvus;
    }

    public void setMilvus(Milvus milvus) {
        this.milvus = milvus;
    }

    public static class Milvus {
        private boolean enabled = false;
        private String uri = "http://127.0.0.1:19530";
        private String collectionName = "assistant_memory";
        private int dimensions = 1024;
        private String token;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getCollectionName() {
            return collectionName;
        }

        public void setCollectionName(String collectionName) {
            this.collectionName = collectionName;
        }

        public int getDimensions() {
            return dimensions;
        }

        public void setDimensions(int dimensions) {
            this.dimensions = dimensions;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }
}
