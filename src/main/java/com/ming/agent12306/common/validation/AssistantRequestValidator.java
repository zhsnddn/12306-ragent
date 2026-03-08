package com.ming.agent12306.common.validation;

import com.ming.agent12306.common.constant.AssistantErrorMessagesConstant;
import com.ming.agent12306.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AssistantRequestValidator {

    public void validateMessage(String message) {
        if (!StringUtils.hasText(message)) {
            throw new BusinessException(AssistantErrorMessagesConstant.EMPTY_MESSAGE);
        }
    }

    public void validateApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(AssistantErrorMessagesConstant.MISSING_API_KEY);
        }
    }
}
