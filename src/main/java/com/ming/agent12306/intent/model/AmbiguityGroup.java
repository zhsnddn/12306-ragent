package com.ming.agent12306.intent.model;

import java.util.List;

/**
 * 歧义组：同名但不同类别的意图
 */
public record AmbiguityGroup(
        String topicName,
        List<String> optionIds
) {
    public AmbiguityGroup {
        if (topicName == null) topicName = "";
        if (optionIds == null) optionIds = List.of();
    }
}
