package com.ming.agent12306.common.handler;

import com.ming.agent12306.common.exception.BusinessException;
import com.ming.agent12306.model.response.AssistantChatResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public AssistantChatResponse handleBusinessException(BusinessException ex) {
        return new AssistantChatResponse(false, null, ex.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public AssistantChatResponse handleException(Exception ex) {
        return new AssistantChatResponse(false, null, "系统繁忙，请稍后重试", null);
    }
}
