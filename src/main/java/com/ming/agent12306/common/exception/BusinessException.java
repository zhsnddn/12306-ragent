package com.ming.agent12306.common.exception;

/**
 * 统一异常处理
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
