package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.ConversationPersonaOverrideEntity;
import com.moodcopilot.entity.UserPersonaEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves field ownership. It does not render prompt text or grant capabilities. */
@Component
public class PersonaResolver {
    private final ObjectMapper objectMapper;

    public PersonaResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ResolvedPersona resolve(UserPersonaEntity global, ConversationPersonaOverrideEntity conversation,
            CurrentTurnPreference turn) {
        Map<String, EffectivePersona.ResolutionTrace> trace = new LinkedHashMap<>();
        String role = firstRole(conversation, global, trace);
        List<String> tone = firstConfiguredTone(conversation, global, trace);

        LinkedHashSet<String> behaviors = new LinkedHashSet<>(PersonaPolicy.DEFAULT_BEHAVIORS);
        applyBehaviorScope(behaviors, global == null ? null : values(global.getBehaviorFlagsJson()),
                global == null ? null : values(global.getDisabledBehaviorFlagsJson()));
        applyBehaviorScope(behaviors, conversation == null ? null : values(conversation.getBehaviorFlagsJson()),
                conversation == null ? null : values(conversation.getDisabledBehaviorFlagsJson()));
        if (turn != null) {
            applyBehaviorScope(behaviors, turn.enabledBehaviorFlags(), turn.disabledBehaviorFlags());
            trace.put("behaviorFlags", new EffectivePersona.ResolutionTrace("TURN", null));
        } else if (conversation != null && (conversation.getBehaviorFlagsJson() != null
                || conversation.getDisabledBehaviorFlagsJson() != null)) {
            trace.put("behaviorFlags", traceFor("CONVERSATION", conversation.getVersion()));
        } else if (global != null) {
            trace.put("behaviorFlags", traceFor("GLOBAL", global.getVersion()));
        }

        String customTone = firstValidTone(conversation == null ? null : conversation.getCustomTone(),
                global == null ? null : global.getCustomTone());
        if (conversation != null && !PersonaPolicy.normalizeCustomTone(conversation.getCustomTone()).isBlank()) {
            trace.put("customTone", traceFor("CONVERSATION", conversation.getVersion()));
        } else if (global != null && !PersonaPolicy.normalizeCustomTone(global.getCustomTone()).isBlank()) {
            trace.put("customTone", traceFor("GLOBAL", global.getVersion()));
        }

        String responseStyle = firstResponseStyle(turn == null ? null : turn.temporaryResponseStyle(),
                conversation == null ? null : conversation.getCustomResponseStyle(),
                global == null ? null : global.getCustomResponseStyle());
        if (!PersonaPolicy.normalizeCustomResponseStyle(turn == null ? null : turn.temporaryResponseStyle()).isBlank()) {
            trace.put("customResponseStyle", new EffectivePersona.ResolutionTrace("TURN", null));
        } else if (!PersonaPolicy.normalizeCustomResponseStyle(conversation == null ? null : conversation.getCustomResponseStyle()).isBlank()) {
            trace.put("customResponseStyle", traceFor("CONVERSATION", conversation.getVersion()));
        } else if (global != null && !PersonaPolicy.normalizeCustomResponseStyle(global.getCustomResponseStyle()).isBlank()) {
            trace.put("customResponseStyle", traceFor("GLOBAL", global.getVersion()));
        }
        return new ResolvedPersona(role, tone, List.copyOf(behaviors), customTone, responseStyle,
                turn == null ? "" : PersonaPolicy.normalizeOutputRequirement(turn.outputRequirement()),
                global == null ? null : global.getVersion(), conversation == null ? null : conversation.getVersion(),
                turn != null && turn.isPresent(), trace);
    }

    private String firstRole(ConversationPersonaOverrideEntity conversation, UserPersonaEntity global,
            Map<String, EffectivePersona.ResolutionTrace> trace) {
        if (conversation != null && !PersonaPolicy.normalize(conversation.getRole()).isBlank()
                && PersonaPolicy.ROLES.contains(PersonaPolicy.normalize(conversation.getRole()).toLowerCase())) {
            trace.put("role", traceFor("CONVERSATION", conversation.getVersion()));
            return PersonaPolicy.normalize(conversation.getRole()).toLowerCase();
        }
        if (global != null && PersonaPolicy.ROLES.contains(PersonaPolicy.normalize(global.getRole()).toLowerCase())) {
            trace.put("role", traceFor("GLOBAL", global.getVersion()));
            return PersonaPolicy.normalize(global.getRole()).toLowerCase();
        }
        return PersonaPolicy.DEFAULT_ROLE;
    }

    private List<String> firstConfiguredTone(ConversationPersonaOverrideEntity conversation,
            UserPersonaEntity global, Map<String, EffectivePersona.ResolutionTrace> trace) {
        if (conversation != null && conversation.getToneJson() != null) {
            trace.put("tone", traceFor("CONVERSATION", conversation.getVersion()));
            List<String> values = toneValues(conversation.getToneJson());
            return values.isEmpty() ? PersonaPolicy.DEFAULT_TONE : values;
        }
        if (global != null && global.getToneJson() != null) {
            trace.put("tone", traceFor("GLOBAL", global.getVersion()));
            List<String> values = toneValues(global.getToneJson());
            return values.isEmpty() ? PersonaPolicy.DEFAULT_TONE : values;
        }
        return PersonaPolicy.DEFAULT_TONE;
    }

    private String firstValidTone(String... values) {
        for (String value : values) {
            String normalized = PersonaPolicy.normalizeCustomTone(value);
            if (!normalized.isBlank()) return normalized;
        }
        return "";
    }

    private String firstResponseStyle(String... values) {
        for (String value : values) {
            String normalized = PersonaPolicy.normalizeCustomResponseStyle(value);
            if (!normalized.isBlank()) return normalized;
        }
        return "";
    }

    private void applyBehaviorScope(Set<String> target, List<String> enabled, List<String> disabled) {
        if (enabled != null) target.addAll(enabled);
        if (disabled != null) target.removeAll(disabled);
    }

    private List<String> values(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return PersonaPolicy.normalizeValues(objectMapper.readValue(json, List.class), PersonaPolicy.BEHAVIORS);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<String> toneValues(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return PersonaPolicy.normalizeValues(objectMapper.readValue(json, List.class), PersonaPolicy.TONES);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private EffectivePersona.ResolutionTrace traceFor(String scope, Integer version) {
        return new EffectivePersona.ResolutionTrace(scope, version);
    }

    public record ResolvedPersona(String role, List<String> tone, List<String> behaviorFlags,
            String customTone, String customResponseStyle, String outputRequirement, Integer globalVersion,
            Integer conversationVersion, boolean turnOverridePresent,
            Map<String, EffectivePersona.ResolutionTrace> resolutionTrace) {}
}
