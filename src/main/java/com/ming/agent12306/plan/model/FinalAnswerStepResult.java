package com.ming.agent12306.plan.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 最终回答步骤结果模型 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FinalAnswerStepResult {
    private String summary;
    private List<String> ticketHighlights;
    private List<String> recommendations;
    private List<String> ruleTips;
}
