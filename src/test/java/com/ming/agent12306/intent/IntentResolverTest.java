package com.ming.agent12306.intent;

import com.ming.agent12306.intent.fallback.FallbackResponseService;
import com.ming.agent12306.intent.model.GuidanceDecision;
import com.ming.agent12306.intent.model.IntentKind;
import com.ming.agent12306.intent.model.IntentNode;
import com.ming.agent12306.intent.model.IntentTreeData;
import com.ming.agent12306.intent.resolver.IntentResolver;
import com.ming.agent12306.intent.service.IntentClassifier;
import com.ming.agent12306.intent.service.IntentGuidanceService;
import com.ming.agent12306.intent.service.IntentTreeService;
import com.ming.agent12306.intent.config.IntentProperties;
import com.ming.agent12306.common.util.StructuredOutputJsonParser;
import io.agentscope.core.model.DashScopeChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 意图识别系统测试
 */
@ExtendWith(MockitoExtension.class)
public class IntentResolverTest {

    @Mock
    private DashScopeChatModel dashScopeChatModel;

    @Test
    void testFallbackResponseService() {
        // 测试 SYSTEM 兜底
        FallbackResponseService fallbackService = new FallbackResponseService(new IntentProperties());

        String greetingResponse = fallbackService.getFallbackResponse("你好");
        assertTrue(greetingResponse.contains("您好"));

        String orderResponse = fallbackService.getFallbackResponse("我的订单在哪里");
        assertTrue(orderResponse.contains("出发地") || orderResponse.contains("目的地"));

        String emptyResponse = fallbackService.getFallbackResponse("");
        assertNotNull(emptyResponse);
    }

    @Test
    void testIntentNodeModel() {
        IntentNode node = IntentNode.builder()
                .id("cat_ticket/topic_route_ticket")
                .name("站点余票查询")
                .description("查询余票")
                .level(2)
                .kind(IntentKind.MCP)
                .examples(List.of("查票", "买票"))
                .build();

        assertTrue(node.isLeaf());
        assertEquals(IntentKind.MCP, node.getKind());
        assertEquals("站点余票查询", node.getName());
    }

    @Test
    void testIntentTreeData() {
        List<IntentNode> leafNodes = new ArrayList<>();
        List<IntentNode> allNodes = new ArrayList<>();
        Map<String, IntentNode> id2Node = new HashMap<>();

        IntentNode topicNode = IntentNode.builder()
                .id("cat_ticket/topic_route_ticket")
                .name("站点余票查询")
                .level(2)
                .kind(IntentKind.MCP)
                .build();
        leafNodes.add(topicNode);
        allNodes.add(topicNode);
        id2Node.put(topicNode.getId(), topicNode);

        IntentNode catNode = IntentNode.builder()
                .id("cat_ticket")
                .name("票务查询")
                .level(1)
                .build();
        allNodes.add(catNode);
        id2Node.put(catNode.getId(), catNode);

        IntentTreeData treeData = IntentTreeData.builder()
                .allNodes(allNodes)
                .leafNodes(leafNodes)
                .id2Node(id2Node)
                .build();

        assertEquals(1, treeData.getLeafNodes().size());
        assertEquals(2, treeData.getAllNodes().size());
        assertNotNull(treeData.getId2Node().get("cat_ticket/topic_route_ticket"));
    }

    @Test
    void testGuidanceDecision() {
        GuidanceDecision none = GuidanceDecision.none();
        assertFalse(none.shouldGuide());
        assertNull(none.prompt());

        GuidanceDecision prompt = GuidanceDecision.prompt("请选择:");
        assertTrue(prompt.shouldGuide());
        assertEquals("请选择:", prompt.prompt());
    }

    @Test
    void testIntentKind() {
        assertEquals(0, IntentKind.KB.getValue());
        assertEquals(1, IntentKind.SYSTEM.getValue());
        assertEquals(2, IntentKind.MCP.getValue());
    }

    @Test
    void testIntentProperties() {
        IntentProperties props = new IntentProperties();
        assertEquals(0.35, props.getMinScore());
        assertEquals(3, props.getMaxIntentCount());
        assertTrue(props.getGuidance().isEnabled());
        assertEquals(0.8, props.getGuidance().getAmbiguityScoreRatio());

        // 测试配置覆盖
        props.setMinScore(0.5);
        assertEquals(0.5, props.getMinScore());
    }
}
