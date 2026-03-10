package com.ming.agent12306.memory.service;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.DashScopeChatModel;
import org.springframework.stereotype.Component;

import java.util.List;

/** 会话摘要生成服务 */
@Component
public class ConversationSummaryGenerator {

    private final DashScopeChatModel dashScopeChatModel;

    public ConversationSummaryGenerator(DashScopeChatModel dashScopeChatModel) {
        this.dashScopeChatModel = dashScopeChatModel;
    }

    public String summarize(String previousSummary, List<String> lines) {
        StringBuilder prompt = new StringBuilder("请总结以下12306对话，保留用户偏好、已确认行程、关键约束和待解决问题。\n");
        if (previousSummary != null && !previousSummary.isBlank()) {
            prompt.append("已有摘要：").append(previousSummary).append("\n");
        }
        prompt.append("新增对话：\n");
        lines.forEach(line -> prompt.append("- ").append(line).append("\n"));

        ReActAgent agent = ReActAgent.builder()
                .name("会话摘要器")
                .description("总结会话上下文")
                .sysPrompt("你是对话摘要助手，请输出可复用的中文摘要，避免冗余。")
                .model(dashScopeChatModel)
                .maxIters(1)
                .build();

        Msg response = agent.call(List.of(Msg.builder()
                .name("user")
                .role(MsgRole.USER)
                .textContent(prompt.toString())
                .build())).block();

        return response == null || response.getTextContent() == null ? previousSummary : response.getTextContent();
    }
}
