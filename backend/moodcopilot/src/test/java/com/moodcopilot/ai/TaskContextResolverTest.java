package com.moodcopilot.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TaskContextResolverTest {
    private final TaskContextResolver resolver = new TaskContextResolver();

    @Test
    void recognizesCodingWithoutChangingModelSelectionOrPersona() {
        TaskContext context = resolver.resolve("帮我看看这个 Redis Lua 脚本为什么报错");
        assertEquals("CODING", context.taskType());
    }

    @Test
    void currentTurnStyleDoesNotChangeTaskClassification() {
        TaskContext context = resolver.resolve("这一轮直接一点，按步骤审查我的代码");
        assertEquals("CODING", context.taskType());
    }

    @Test
    void unknownRequestIsFirstClassGeneralTask() {
        assertEquals("GENERAL", resolver.resolve("帮我比较两个方案").taskType());
    }

    @Test
    void explicitGeneralTaskIsNotOverriddenByKeywordRules() {
        assertEquals("GENERAL", resolver.resolve("任务类型：通用，请直接回答这个 Redis 问题").taskType());
    }

    @Test
    void configuredRulesOverrideOneRuleButKeepOtherDefaults() {
        TaskContextProperties properties = new TaskContextProperties();
        TaskContextProperties.Rule rule = new TaskContextProperties.Rule();
        rule.setTaskType("WRITING");
        rule.setPriority(200);
        rule.setInstruction("按指定模板写作");
        rule.setTriggerPatterns(List.of("模板"));
        properties.setRules(List.of(rule));

        TaskContext context = new TaskContextResolver(properties).resolve("请按模板整理这段内容");

        assertEquals("WRITING", context.taskType());
        assertEquals("按指定模板写作", context.instruction());
        assertEquals("CODING", new TaskContextResolver(properties)
                .resolve("这个 Redis 脚本报错了").taskType());
    }
}
