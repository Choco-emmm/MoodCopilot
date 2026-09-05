package com.moodcopilot.ai;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Deterministic task framing only; it does not route models or grant permissions. */
@Component
public class TaskContextResolver {
    private static final List<TaskRule> DEFAULT_RULES = List.of(
            new TaskRule("CODING", 100, "代码编程开发", List.of("代码", "编程", "bug", "报错", "java", "redis", "sql", "typescript", "python", "接口"), Set.of("歌词翻译"), List.of("给出可运行的技术解释和修改建议")),
            new TaskRule("TRANSLATION", 90, "翻译用户提供的内容", List.of("翻译", "translate", "译成"), Set.of(), List.of("保留原意，按用户指定的目标语言输出")),
            new TaskRule("WRITING", 80, "协助用户完成写作", List.of("写一篇", "润色", "改写", "文案", "作文", "起草"), Set.of(), List.of("先确认体裁和用途，保持内容清晰")),
            new TaskRule("LEARNING", 70, "帮助用户理解和学习", List.of("讲解", "怎么学", "学习", "原理", "为什么"), Set.of(), List.of("从概念到例子，按需逐步解释")),
            new TaskRule("PLANNING", 60, "协助用户制定计划", List.of("计划", "规划", "安排", "路线", "拆解"), Set.of(), List.of("明确目标、约束和下一步行动")),
            new TaskRule("EMOTIONAL_SUPPORT", 50, "提供支持性、陪伴性的回应", List.of("难过", "焦虑", "压力", "内耗", "崩溃", "陪我聊"), Set.of(), List.of("先回应用户真实需要，不进行诊断")));
    private final List<TaskRule> rules;

    /** Keeps the resolver convenient to use in pure unit tests. */
    public TaskContextResolver() {
        this.rules = DEFAULT_RULES;
    }

    @Autowired
    public TaskContextResolver(TaskContextProperties properties) {
        this.rules = configuredRules(properties);
    }

    private List<TaskRule> configuredRules(TaskContextProperties properties) {
        if (properties == null || properties.getRules() == null || properties.getRules().isEmpty()) {
            return DEFAULT_RULES;
        }
        java.util.Map<String, TaskRule> merged = new java.util.LinkedHashMap<>();
        DEFAULT_RULES.forEach(rule -> merged.put(rule.taskType(), rule));
        properties.getRules().stream()
                .filter(rule -> rule != null && rule.getTaskType() != null && !rule.getTaskType().isBlank())
                .map(rule -> new TaskRule(rule.getTaskType().toUpperCase(Locale.ROOT), rule.getPriority(),
                        rule.getInstruction(), normalizePatterns(rule.getTriggerPatterns()),
                        Set.copyOf(normalizePatterns(rule.getExclusions())),
                        normalizeValues(rule.getOutputHints())))
                .filter(rule -> Set.of("GENERAL", "CODING", "LEARNING", "WRITING", "TRANSLATION", "PLANNING",
                        "EMOTIONAL_SUPPORT").contains(rule.taskType()))
                .forEach(rule -> merged.put(rule.taskType(), rule));
        return List.copyOf(merged.values());
    }

    private List<String> normalizePatterns(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> PersonaPolicy.normalize(value).toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private List<String> normalizeValues(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(PersonaPolicy::normalize)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    public TaskContext resolve(String message) {
        String normalized = PersonaPolicy.normalize(message).toLowerCase(Locale.ROOT);
        boolean explicitGeneral = hasExplicitGeneral(normalized);
        TaskRule matched = explicitGeneral ? null : explicitRule(normalized);
        if (matched == null && !explicitGeneral) {
            matched = rules.stream().filter(rule -> rule.matches(normalized))
                    .max(java.util.Comparator.comparingInt(TaskRule::priority)).orElse(null);
        }
        return matched == null
                ? new TaskContext("GENERAL", "按用户当前请求直接完成任务", List.of(), null)
                : new TaskContext(matched.taskType(), matched.instruction(), matched.outputHints(), null);
    }

    private boolean hasExplicitGeneral(String message) {
        return message.contains("任务类型:通用") || message.contains("任务:通用")
                || message.contains("task: general");
    }

    private TaskRule explicitRule(String message) {
        if (message.contains("任务类型:编程") || message.contains("任务:编程") || message.contains("task: coding")) {
            return rules.stream().filter(rule -> "CODING".equals(rule.taskType())).findFirst().orElse(null);
        }
        if (message.contains("任务类型:翻译") || message.contains("任务:翻译") || message.contains("task: translation")) {
            return rules.stream().filter(rule -> "TRANSLATION".equals(rule.taskType())).findFirst().orElse(null);
        }
        if (message.contains("任务类型:写作") || message.contains("任务:写作") || message.contains("task: writing")) {
            return rules.stream().filter(rule -> "WRITING".equals(rule.taskType())).findFirst().orElse(null);
        }
        if (message.contains("任务类型:学习") || message.contains("任务:学习") || message.contains("task: learning")) {
            return rules.stream().filter(rule -> "LEARNING".equals(rule.taskType())).findFirst().orElse(null);
        }
        if (message.contains("任务类型:规划") || message.contains("任务:规划") || message.contains("task: planning")) {
            return rules.stream().filter(rule -> "PLANNING".equals(rule.taskType())).findFirst().orElse(null);
        }
        if (message.contains("任务类型:情绪") || message.contains("任务:情绪") || message.contains("task: emotional_support")) {
            return rules.stream().filter(rule -> "EMOTIONAL_SUPPORT".equals(rule.taskType())).findFirst().orElse(null);
        }
        return null;
    }

    private record TaskRule(String taskType, int priority, String instruction, List<String> triggers,
            Set<String> exclusions, List<String> outputHints) {
        boolean matches(String value) {
            return triggers.stream().anyMatch(value::contains) && exclusions.stream().noneMatch(value::contains);
        }
    }
}
