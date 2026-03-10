package com.ming.agent12306.plan.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 票务查询步骤结果模型 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TicketQueryStepResult {
    private Boolean success;
    private Boolean hasTicket;
    private String ticketSummary;
    private List<String> trainOptions;
}
