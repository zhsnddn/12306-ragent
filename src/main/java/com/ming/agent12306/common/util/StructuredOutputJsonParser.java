package com.ming.agent12306.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 *  结构化输出 JSON 解析工具
 */
@Component
public class StructuredOutputJsonParser {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

    public <T> T parse(String content, Class<T> type) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(stripCodeFence(content), type);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    public JsonNode parseTree(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(stripCodeFence(content));
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    public String readText(JsonNode root, String fieldName) {
        if (root == null || fieldName == null) {
            return null;
        }
        JsonNode node = root.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        return flattenNode(node);
    }

    public List<String> readTextList(JsonNode root, String fieldName) {
        if (root == null || fieldName == null) {
            return List.of();
        }
        JsonNode node = root.get(fieldName);
        if (node == null || node.isNull()) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> collectText(items, item));
            return items;
        }
        collectText(items, node);
        return items;
    }

    public String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineBreak = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineBreak > -1 && lastFence > firstLineBreak) {
                return trimmed.substring(firstLineBreak + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private void collectText(List<String> items, JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectText(items, item));
            return;
        }
        String flattened = flattenNode(node);
        if (flattened != null && !flattened.isBlank()) {
            items.add(flattened);
        }
    }

    private String flattenNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(item -> {
                String flattened = flattenNode(item);
                if (flattened != null && !flattened.isBlank()) {
                    values.add(flattened);
                }
            });
            return String.join("；", values);
        }
        if (node.isObject()) {
            List<String> fields = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                String value = flattenNode(entry.getValue());
                if (value != null && !value.isBlank()) {
                    fields.add(entry.getKey() + "：" + value);
                }
            }
            return String.join("；", fields);
        }
        return node.toString();
    }
}
