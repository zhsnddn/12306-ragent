package com.ming.agent12306.intent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 意图识别配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "assistant.intent")
public class IntentProperties {

    /**
     * 最低分数阈值，低于此分数不参与后续流程
     */
    private double minScore = 0.35;

    /**
     * 单次查询最多参与的意图数量
     */
    private int maxIntentCount = 3;

    /**
     * 歧义引导配置
     */
    private Guidance guidance = new Guidance();

    /**
     * Redis 缓存配置
     */
    private Cache cache = new Cache();

    @Data
    public static class Guidance {
        /**
         * 是否启用歧义引导
         */
        private boolean enabled = true;

        /**
         * 触发引导的分数比例（次高分/最高分）
         */
        private double ambiguityScoreRatio = 0.8;

        /**
         * 引导选项的最大数量
         */
        private int maxOptions = 6;
    }

    @Data
    public static class Cache {
        /**
         * 意图树缓存 Key
         */
        private String intentTreeKey = "12306:intent:tree";

        /**
         * 缓存过期时间（天）
         */
        private int ttlDays = 7;
    }
}
