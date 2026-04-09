package com.ming.agent12306.intent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 意图节点模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentNode {
    /**
     * 节点唯一标识，如 "cat_ticket/topic_route_ticket"
     */
    private String id;

    /**
     * 节点名称，如 "站点余票查询"
     */
    private String name;

    /**
     * 节点描述，用于 LLM 理解
     */
    private String description;

    /**
     * 层级：0=DOMAIN, 1=CATEGORY, 2=TOPIC
     */
    private Integer level;

    /**
     * 意图类型：KB/SYSTEM/MCP
     */
    private IntentKind kind;

    /**
     * 示例问题，帮助 LLM 理解
     */
    private List<String> examples;

    /**
     * 子节点
     */
    private List<IntentNode> children;

    /**
     * 获取父节点 ID（如有）
     */
    private String parentId;

    /**
     * 判断是否为叶子节点（Topic层）
     */
    public boolean isLeaf() {
        return level != null && level == 2;
    }
}
