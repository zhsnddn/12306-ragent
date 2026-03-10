package com.ming.agent12306.plan.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 知识检索步骤结果模型 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeRetrieveStepResult {
    private Boolean success;
    private String summary;
    private List<String> references;
}
