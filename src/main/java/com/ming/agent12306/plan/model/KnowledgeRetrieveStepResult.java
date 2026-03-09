package com.ming.agent12306.plan.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnowledgeRetrieveStepResult {
    private Boolean success;
    private String summary;
    private List<String> references;
}
