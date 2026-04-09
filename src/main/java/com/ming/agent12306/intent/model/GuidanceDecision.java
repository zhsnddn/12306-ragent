package com.ming.agent12306.intent.model;

/**
 * 歧义引导决策
 */
public record GuidanceDecision(
        /**
         * 是否需要引导
         */
        boolean shouldGuide,

        /**
         * 引导提示语
         */
        String prompt
) {
    public static GuidanceDecision none() {
        return new GuidanceDecision(false, null);
    }

    public static GuidanceDecision prompt(String prompt) {
        return new GuidanceDecision(true, prompt);
    }
}
