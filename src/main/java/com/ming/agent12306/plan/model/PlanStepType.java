package com.ming.agent12306.plan.model;

public enum PlanStepType {
    EXTRACT_INTENT(0, "EXTRACT_INTENT", "提取出行参数"),
    QUERY_TICKET(1, "QUERY_TICKET", "调用票务工具"),
    RETRIEVE_KNOWLEDGE(2, "RETRIEVE_KNOWLEDGE", "检索规则知识"),
    GENERATE_ANSWER(3, "GENERATE_ANSWER", "生成最终答案");

    private final int index;
    private final String code;
    private final String label;

    PlanStepType(int index, String code, String label) {
        this.index = index;
        this.code = code;
        this.label = label;
    }

    public int index() {
        return index;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }
}
