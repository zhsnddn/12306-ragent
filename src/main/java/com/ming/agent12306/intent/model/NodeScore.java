package com.ming.agent12306.intent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 节点打分结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeScore {
    /**
     * 意图节点
     */
    private IntentNode node;

    /**
     * 得分 0.0-1.0
     */
    private Double score;

    /**
     * 判断理由
     */
    private String reason;
}
