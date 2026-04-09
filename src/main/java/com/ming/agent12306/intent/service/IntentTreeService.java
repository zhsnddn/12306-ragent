package com.ming.agent12306.intent.service;

import com.ming.agent12306.intent.config.IntentProperties;
import com.ming.agent12306.intent.model.IntentKind;
import com.ming.agent12306.intent.model.IntentNode;
import com.ming.agent12306.intent.model.IntentTreeData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 意图树服务：负责加载和缓存意图树数据
 */
@Service
public class IntentTreeService {

    private static final Logger log = LoggerFactory.getLogger(IntentTreeService.class);

    private final IntentProperties intentProperties;
    private final StringRedisTemplate redisTemplate;

    public IntentTreeService(IntentProperties intentProperties, StringRedisTemplate redisTemplate) {
        this.intentProperties = intentProperties;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取意图树数据
     * 加载顺序：Redis优先 → 构建内存索引
     */
    public IntentTreeData getIntentTree() {
        String cacheKey = intentProperties.getCache().getIntentTreeKey();

        // 尝试从 Redis 获取
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                log.debug("[intent-tree] cache hit, key={}", cacheKey);
                // TODO: 反序列化（可使用 JSON 库如 Jackson）
            }
        } catch (Exception e) {
            log.warn("[intent-tree] redis get failed, key={}, error={}", cacheKey, e.getMessage());
        }

        // 构建意图树（硬编码方式，后续可改为数据库加载）
        IntentTreeData treeData = buildIntentTree();

        // 写入 Redis 缓存
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    serializeTreeData(treeData),
                    Duration.ofDays(intentProperties.getCache().getTtlDays())
            );
            log.info("[intent-tree] cache set, key={}, nodes={}", cacheKey, treeData.getAllNodes().size());
        } catch (Exception e) {
            log.warn("[intent-tree] redis set failed, key={}, error={}", cacheKey, e.getMessage());
        }

        return treeData;
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        String cacheKey = intentProperties.getCache().getIntentTreeKey();
        try {
            redisTemplate.delete(cacheKey);
            log.info("[intent-tree] cache cleared, key={}", cacheKey);
        } catch (Exception e) {
            log.warn("[intent-tree] redis delete failed, key={}, error={}", cacheKey, e.getMessage());
        }
    }

    /**
     * 构建意图树（贴合12306业务场景）
     * 后续可改为从数据库加载
     */
    private IntentTreeData buildIntentTree() {
        List<IntentNode> allNodes = new ArrayList<>();
        Map<String, IntentNode> id2Node = new HashMap<>();

        // ========== DOMAIN 层 ==========
        IntentNode domainNode = IntentNode.builder()
                .id("domain_12306")
                .name("12306票务系统")
                .description("铁路12306官方票务服务")
                .level(0)
                .kind(IntentKind.SYSTEM)
                .children(new ArrayList<>())
                .build();
        allNodes.add(domainNode);
        id2Node.put(domainNode.getId(), domainNode);

        // ========== CATEGORY 层 ==========
        // 票务查询
        IntentNode catTicket = IntentNode.builder()
                .id("cat_ticket")
                .name("票务查询")
                .description("余票、车次、经停站等查询")
                .level(1)
                .kind(IntentKind.MCP)
                .parentId("domain_12306")
                .children(new ArrayList<>())
                .build();

        // 规则咨询
        IntentNode catRule = IntentNode.builder()
                .id("cat_rule")
                .name("规则咨询")
                .description("退改签、候补、购票限制等规则")
                .level(1)
                .kind(IntentKind.KB)
                .parentId("domain_12306")
                .children(new ArrayList<>())
                .build();

        // 业务办理
        IntentNode catOrder = IntentNode.builder()
                .id("cat_order")
                .name("业务办理")
                .description("订单查询、改签、退票等")
                .level(1)
                .kind(IntentKind.MCP)
                .parentId("domain_12306")
                .children(new ArrayList<>())
                .build();

        // 账户服务
        IntentNode catAccount = IntentNode.builder()
                .id("cat_account")
                .name("账户服务")
                .description("会员、积分、乘车人管理")
                .level(1)
                .kind(IntentKind.MCP)
                .parentId("domain_12306")
                .children(new ArrayList<>())
                .build();

        // 投诉建议
        IntentNode catFeedback = IntentNode.builder()
                .id("cat_feedback")
                .name("投诉建议")
                .description("投诉渠道、建议反馈")
                .level(1)
                .kind(IntentKind.SYSTEM)
                .parentId("domain_12306")
                .children(new ArrayList<>())
                .build();

        allNodes.add(catTicket);
        allNodes.add(catRule);
        allNodes.add(catOrder);
        allNodes.add(catAccount);
        allNodes.add(catFeedback);
        id2Node.put(catTicket.getId(), catTicket);
        id2Node.put(catRule.getId(), catRule);
        id2Node.put(catOrder.getId(), catOrder);
        id2Node.put(catAccount.getId(), catAccount);
        id2Node.put(catFeedback.getId(), catFeedback);

        // ========== TOPIC 层（叶子节点） ==========
        List<IntentNode> leafNodes = new ArrayList<>();

        // --- 票务查询 Category ---
        leafNodes.add(buildTopicNode("cat_ticket/topic_route_ticket", "站点余票查询",
                "查询两个车站之间的余票情况，包括高铁、动车、普通列车等", IntentKind.MCP,
                List.of("查一下明天北京到上海的高铁票", "我想从杭州去南京，明天有几趟车", "北京到上海余票查询"),
                catTicket));
        leafNodes.add(buildTopicNode("cat_ticket/topic_train_stops", "车次经停站查询",
                "查询指定车次的经停站信息、到发时间", IntentKind.MCP,
                List.of("G1234次列车经过哪些站", "D202次在南京停吗", "这趟车在武汉几点到"),
                catTicket));
        leafNodes.add(buildTopicNode("cat_ticket/topic_transfer", "换乘方案查询",
                "查询两个车站之间的换乘方案", IntentKind.MCP,
                List.of("北京到深圳怎么换乘", "没有直达车怎么办", "需要转车吗"),
                catTicket));

        // --- 规则咨询 Category ---
        leafNodes.add(buildTopicNode("cat_rule/topic_refund", "退票规则",
                "退票手续费计算规则、退票时间限制", IntentKind.KB,
                List.of("退票怎么收费", "开车前退票扣多少钱", "不小心买错了能退吗"),
                catRule));
        leafNodes.add(buildTopicNode("cat_rule/topic_resign", "改签规则",
                "改签手续费、开车前改签时间限制", IntentKind.KB,
                List.of("改签手续费多少", "可以改到后天吗", "改签怎么办理"),
                catRule));
        leafNodes.add(buildTopicNode("cat_rule/topic_waitlist", "候补购票规则",
                "候补购票的规则、兑现率、操作方法", IntentKind.KB,
                List.of("候补购票是什么意思", "候补能成功吗", "怎么排队候补"),
                catRule));
        leafNodes.add(buildTopicNode("cat_rule/topic_buy_limit", "购票限制说明",
                "儿童票、军人票等重点旅客购票限制", IntentKind.KB,
                List.of("儿童怎么买票", "军人买票有优惠吗", "一个成人能带几个小孩"),
                catRule));
        leafNodes.add(buildTopicNode("cat_rule/topic_student", "学生票规则",
                "学生票购买条件、优惠次数、资质核验", IntentKind.KB,
                List.of("学生票怎么购买", "学生票一年可以用几次", "学生票需要核验吗"),
                catRule));
        leafNodes.add(buildTopicNode("cat_rule/topic_points", "积分规则",
                "积分获取方式、兑换规则、会员等级权益", IntentKind.KB,
                List.of("积分怎么兑换", "买票有积分吗", "积分能换什么"),
                catRule));

        // --- 业务办理 Category ---
        leafNodes.add(buildTopicNode("cat_order/topic_order_query", "订单查询",
                "查询已购订单信息", IntentKind.MCP,
                List.of("我的订单在哪里查", "我买了哪趟车的票", "怎么看订单详情"),
                catOrder));
        leafNodes.add(buildTopicNode("cat_order/topic_order_cancel", "取消订单",
                "取消未支付或已购买的订单", IntentKind.MCP,
                List.of("如何取消订单", "订单不想用了", "买重了能取消吗"),
                catOrder));

        // --- 账户服务 Category ---
        leafNodes.add(buildTopicNode("cat_account/topic_vip_level", "会员等级权益",
                "12306会员等级、积分累积比例、权益兑换", IntentKind.KB,
                List.of("会员有哪些等级", "普通会员和会员有什么区别", "怎么升级"),
                catAccount));
        leafNodes.add(buildTopicNode("cat_account/topic_passenger", "乘车人管理",
                "常用联系人管理、乘车人添加删除", IntentKind.MCP,
                List.of("如何添加乘车人", "怎么删除联系人", "可以帮别人买吗"),
                catAccount));

        // --- 投诉建议 Category ---
        leafNodes.add(buildTopicNode("cat_feedback/topic_complaint", "投诉建议",
                "投诉渠道、建议反馈方式", IntentKind.SYSTEM,
                List.of("怎么投诉列车员", "有建议想提", "服务态度不好怎么反馈"),
                catFeedback));

        // 建立父子关系
        for (IntentNode leaf : leafNodes) {
            allNodes.add(leaf);
            id2Node.put(leaf.getId(), leaf);
            String parentId = leaf.getParentId();
            if (parentId != null && id2Node.containsKey(parentId)) {
                IntentNode parent = id2Node.get(parentId);
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(leaf);
            }
        }

        return IntentTreeData.builder()
                .allNodes(allNodes)
                .leafNodes(leafNodes)
                .id2Node(id2Node)
                .build();
    }

    private IntentNode buildTopicNode(String id, String name, String description, IntentKind kind,
                                      List<String> examples, IntentNode parent) {
        return IntentNode.builder()
                .id(id)
                .name(name)
                .description(description)
                .level(2)
                .kind(kind)
                .examples(examples)
                .parentId(parent.getId())
                .build();
    }

    /**
     * 序列化意图树（简单实现，可改用 JSON 库）
     */
    private String serializeTreeData(IntentTreeData data) {
        // TODO: 使用 Jackson 或 FastJSON 序列化
        // 目前返回空字符串占位，后续实现
        return "{}";
    }
}
