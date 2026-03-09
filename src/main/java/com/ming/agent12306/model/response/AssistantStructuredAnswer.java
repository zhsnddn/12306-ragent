package com.ming.agent12306.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AssistantStructuredAnswer(
        String summary,
        List<String> ticketHighlights,
        List<String> recommendations,
        List<String> ruleTips
) {
}
