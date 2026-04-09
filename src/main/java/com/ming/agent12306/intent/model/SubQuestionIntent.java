package com.ming.agent12306.intent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 子问题意图
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubQuestionIntent {
    /**
     * 子问题文本
     */
    private String subQuestion;

    /**
     * 该子问题的意图候选（已排序）
     */
    private List<NodeScore> nodeScores;
}
