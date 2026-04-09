package com.ming.agent12306.intent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 意图树内存数据结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentTreeData {
    /**
     * 所有节点（扁平化）
     */
    private List<IntentNode> allNodes;

    /**
     * 所有叶子节点（Topic 层）
     */
    private List<IntentNode> leafNodes;

    /**
     * id → 节点映射
     */
    private Map<String, IntentNode> id2Node;
}
