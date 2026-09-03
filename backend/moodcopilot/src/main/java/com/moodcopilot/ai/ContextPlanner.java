package com.moodcopilot.ai;

import com.moodcopilot.entity.UserProfileMemoryEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** 统一控制聊天上下文的优先级、长度预算和来源边界。 */
@Component
public class ContextPlanner {
    private static final int CORE_BUDGET = 6000;
    private static final int SHORT_TERM_BUDGET = 1800;
    private static final int REFERENCE_BUDGET = 6000;
    private static final int RAG_BUDGET = 7000;

    private final MemoryOrchestrator memoryOrchestrator;

    public ContextPlanner(MemoryOrchestrator memoryOrchestrator) {
        this.memoryOrchestrator = memoryOrchestrator;
    }

    public ContextPlan plan(long userId, String coreMemory, List<String> references, String ragContext) {
        StringBuilder context = new StringBuilder();
        append(context, "<core_memory>", limit(coreMemory, CORE_BUDGET), "</core_memory>");

        String shortTerm = memoryOrchestrator.current(userId).stream()
                .filter(memory -> "short_term_state".equals(memory.getMemoryType()))
                .map(this::formatMemory)
                .collect(Collectors.joining("\n"));
        append(context, "<short_term_state>", limit(shortTerm, SHORT_TERM_BUDGET), "</short_term_state>");

        if (references != null && !references.isEmpty()) {
            String referenceText = references.stream()
                    .filter(Objects::nonNull)
                    .map(value -> limit(value, 2800))
                    .filter(value -> !value.isBlank())
                    .limit(2)
                    .collect(Collectors.joining("\n---\n"));
            append(context, "<user_diary>", limit(referenceText, REFERENCE_BUDGET), "</user_diary>");
        }

        append(context, "<retrieved_experiences>", limit(ragContext, RAG_BUDGET), "</retrieved_experiences>");
        return new ContextPlan(context.toString(), CORE_BUDGET, SHORT_TERM_BUDGET, REFERENCE_BUDGET, RAG_BUDGET);
    }

    private String formatMemory(UserProfileMemoryEntity memory) {
        return "- " + limit(memory.getAttributeKey(), 64) + "：" + limit(memory.getAttributeValue(), 500);
    }

    private void append(StringBuilder target, String open, String value, String close) {
        if (value == null || value.isBlank()) return;
        target.append(open).append('\n').append(value).append('\n').append(close).append('\n');
    }

    private String limit(String value, int maxLength) {
        if (value == null) return "";
        String normalized = value.replaceAll("[\\p{Cntrl}&&[^\\n]]", "").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }

    public record ContextPlan(String context, int coreBudget, int shortTermBudget,
                              int referenceBudget, int ragBudget) {
    }
}
