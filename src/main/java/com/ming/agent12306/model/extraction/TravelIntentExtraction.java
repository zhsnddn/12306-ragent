package com.ming.agent12306.model.extraction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TravelIntentExtraction {
    private String queryType;
    private String trainCode;
    private String fromStation;
    private String toStation;
    private String travelDateRaw;
    private String travelDateNormalized;
    private String departureTimePreference;
    private String seatPreference;
    private Boolean needKnowledgeRetrieve;
    private Boolean fallbackToRuleWhenNoTicket;
    private Boolean needClarification;
    private String clarificationQuestion;
}
