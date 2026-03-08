package com.ming.agent12306.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AssistantChatRequest(
        @NotBlank(message = "message 不能为空")
        String message
) {
}
