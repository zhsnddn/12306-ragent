package com.ming.agent12306.config;

import io.agentscope.core.plan.PlanNotebook;
import io.agentscope.core.plan.storage.InMemoryPlanStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** PlanNotebook 计划编排配置 */
@Configuration
public class PlanNotebookConfig {

    @Bean
    public PlanNotebook planNotebook() {
        return PlanNotebook.builder()
                .storage(new InMemoryPlanStorage())
                .maxSubtasks(8)
                .build();
    }
}
