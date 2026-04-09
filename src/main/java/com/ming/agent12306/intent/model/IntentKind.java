package com.ming.agent12306.intent.model;

/**
 * 意图类型枚举
 */
public enum IntentKind {
    KB(0),      // 知识库检索
    SYSTEM(1),  // 系统预定义交互
    MCP(2);     // 外部工具调用

    private final int value;

    IntentKind(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
