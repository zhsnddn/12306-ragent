package com.ming.agent12306.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

/** 智能助手对话请求模型 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssistantChatRequest(
        String sessionId,
        @NotBlank(message = "message 不能为空")
        String message
) {
}
