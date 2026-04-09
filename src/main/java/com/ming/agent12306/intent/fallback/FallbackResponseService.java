package com.ming.agent12306.intent.fallback;

import com.ming.agent12306.intent.config.IntentProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 兜底响应服务
 */
@Service
public class FallbackResponseService {

    private final IntentProperties intentProperties;

    // SYSTEM 兜底关键词
    private static final List<Pattern> SYSTEM_PATTERNS = List.of(
            Pattern.compile("你好|您好|hi|hello", Pattern.CASE_INSENSITIVE),
            Pattern.compile("帮忙|帮助|怎么用|使用方法", Pattern.CASE_INSENSITIVE),
            Pattern.compile("你是谁|能做什么|有什么功能", Pattern.CASE_INSENSITIVE)
    );

    // MCP 兜底关键词
    private static final List<Pattern> MCP_PATTERNS = List.of(
            Pattern.compile("订单|购票|买票", Pattern.CASE_INSENSITIVE),
            Pattern.compile("查询|搜索|找一下", Pattern.CASE_INSENSITIVE),
            Pattern.compile("数据|报表|统计", Pattern.CASE_INSENSITIVE)
    );

    // 默认兜底响应模板
    private static final String DEFAULT_FALLBACK = "您好！我是12306智能助手，我可以帮您：\n\n" +
            "• 查询车票余票（如「明天北京到上海的高铁票」）\n" +
            "• 查询车次经停站（如「G1234次经过哪些站」）\n" +
            "• 解答退票改签规则（如「退票怎么收费」）\n" +
            "• 查询候补购票规则（如「候补购票是什么意思」）\n" +
            "• 查询订单信息\n\n" +
            "请具体描述您的需求，比如「帮我查一下明天杭州东到南京南的票」或「学生票怎么买」。";

    // SYSTEM 兜底响应
    private static final String SYSTEM_FALLBACK = "您好！我是12306智能助手，很高兴为您服务！\n\n" +
            "我可以帮您：\n" +
            "• 查询实时车票余票\n" +
            "• 查询车次经停站信息\n" +
            "• 解答退票、改签、候补等规则问题\n" +
            "• 查询和管理您的订单\n\n" +
            "请问有什么可以帮到您？";

    // MCP 兜底响应
    private static final String MCP_FALLBACK = "您好！针对您的需求，我来帮您查询。\n\n" +
            "为了更准确地为您服务，请告诉我：\n" +
            "• 出发地和目的地（如「北京到上海」）\n" +
            "• 出行日期（如「4月15日」）\n" +
            "• 偏好车次或席别（如「高铁」或「二等座」）\n\n" +
            "示例：「帮我查一下4月15日北京到上海的高铁票」";

    public FallbackResponseService(IntentProperties intentProperties) {
        this.intentProperties = intentProperties;
    }

    /**
     * 获取兜底响应
     *
     * @param question 用户问题
     * @return 兜底响应话术
     */
    public String getFallbackResponse(String question) {
        if (!StringUtils.hasText(question)) {
            return DEFAULT_FALLBACK;
        }

        // SYSTEM 兜底
        if (matchesAnyPattern(question, SYSTEM_PATTERNS)) {
            return SYSTEM_FALLBACK;
        }

        // MCP 兜底
        if (matchesAnyPattern(question, MCP_PATTERNS)) {
            return MCP_FALLBACK;
        }

        // 默认兜底
        return DEFAULT_FALLBACK;
    }

    /**
     * 检查是否匹配任何模式
     */
    private boolean matchesAnyPattern(String text, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}
