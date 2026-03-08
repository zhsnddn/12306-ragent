package com.ming.agent12306.common.constant;

public final class AssistantErrorMessagesConstant {

    public static final String EMPTY_MESSAGE = "message 不能为空";
    public static final String MISSING_API_KEY = "未配置 assistant.api-key 或环境变量 DASHSCOPE_API_KEY";
    public static final String MISSING_TRAVEL_INFO = "请补充完整的出发地、到达地和出行日期。";
    public static final String MISSING_TRAVEL_DATE = "请提供明确的出行日期，例如 2026-03-10、明天、下周一。";
    public static final String INVALID_TRAVEL_DATE = "日期格式无法识别，请提供明确日期，例如 2026-03-10。";
    public static final String EMPTY_MODEL_RESPONSE = "未获取到模型响应";

    private AssistantErrorMessagesConstant() {
    }
}
