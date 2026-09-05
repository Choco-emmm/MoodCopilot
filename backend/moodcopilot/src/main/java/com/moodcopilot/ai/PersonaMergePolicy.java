package com.moodcopilot.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Field-level merge rules for the global, conversation and turn Persona scopes.
 * This class contains no prompt text and does not grant any runtime capability.
 */
@Component
public class PersonaMergePolicy {
    private static final Set<String> NATURALNESS_AXIS = Set.of("natural", "formal");
    private static final Set<String> ENERGY_AXIS = Set.of("concise", "playful", "humorous");

    public String mergeRole(String turnRole, String conversationRole, String globalRole) {
        String role = firstNonBlank(turnRole, conversationRole, globalRole, PersonaPolicy.DEFAULT_ROLE);
        return PersonaPolicy.ROLES.contains(role) ? role : PersonaPolicy.DEFAULT_ROLE;
    }

    public List<String> mergeTone(List<String> defaults, List<String> global, List<String> conversation,
            List<String> turn) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        addTone(result, defaults);
        addTone(result, global);
        addTone(result, conversation);
        addTone(result, turn);
        return List.copyOf(result);
    }

    public List<String> mergeBehaviors(List<String> defaults, List<String> global, List<String> conversation,
            List<String> turn) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        addValues(result, defaults, false);
        addValues(result, global, false);
        addValues(result, conversation, false);
        addValues(result, turn, true);
        return List.copyOf(result);
    }

    /** Extract only supported style preferences from untrusted free-form text. */
    public List<String> extractStylePreferences(String input) {
        String value = PersonaPolicy.normalize(input);
        if (value.isBlank()) return List.of();
        String lower = value.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        if (lower.contains("简洁") || lower.contains("少废话") || lower.contains("结论")) {
            result.add("conclusion_first");
        }
        if (lower.contains("直接") || lower.contains("坦诚") || lower.contains("别安慰")) {
            result.add("direct_feedback");
        }
        if (lower.contains("步骤") || lower.contains("一步一步")) {
            result.add("step_by_step");
        }
        if (lower.contains("代码优先") || lower.contains("先给代码")) {
            result.add("code_first");
        }
        return result.stream().distinct().toList();
    }

    private void addTone(Set<String> target, List<String> values) {
        if (values == null) return;
        for (String value : values) {
            if (value == null) continue;
            String normalized = PersonaPolicy.normalize(value).toLowerCase(Locale.ROOT);
            if (!PersonaPolicy.TONES.contains(normalized)) continue;
            target.removeIf(existing -> conflictsOnSameAxis(existing, normalized));
            target.add(normalized);
        }
    }

    private void addValues(Set<String> target, List<String> values, boolean allowRemoval) {
        if (values == null) return;
        for (String value : values) {
            if (value == null) continue;
            String normalized = PersonaPolicy.normalize(value).toUpperCase(Locale.ROOT);
            boolean remove = allowRemoval && normalized.startsWith("-");
            String candidate = remove ? normalized.substring(1) : normalized;
            if (!PersonaPolicy.BEHAVIORS.contains(candidate)) continue;
            if (remove) target.remove(candidate);
            else target.add(candidate);
        }
    }

    private boolean conflictsOnSameAxis(String left, String right) {
        return (NATURALNESS_AXIS.contains(left) && NATURALNESS_AXIS.contains(right))
                || (ENERGY_AXIS.contains(left) && ENERGY_AXIS.contains(right));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = PersonaPolicy.normalize(value).toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) return normalized;
        }
        return "";
    }
}
