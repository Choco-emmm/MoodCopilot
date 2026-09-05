package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.ConversationPersonaOverrideEntity;
import com.moodcopilot.entity.UserPersonaEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates and compiles resolved, untrusted preferences into a prompt-independent object. */
@Component
public class PersonaCompiler {
    private final ObjectMapper objectMapper;
    private final PersonaMergePolicy mergePolicy;
    private final PersonaResolver resolver;

    public PersonaCompiler(ObjectMapper objectMapper) {
        this(objectMapper, new PersonaMergePolicy(), new PersonaResolver(objectMapper));
    }

    public PersonaCompiler(ObjectMapper objectMapper, PersonaMergePolicy mergePolicy) {
        this(objectMapper, mergePolicy, new PersonaResolver(objectMapper));
    }

    @Autowired
    public PersonaCompiler(ObjectMapper objectMapper, PersonaMergePolicy mergePolicy, PersonaResolver resolver) {
        this.objectMapper = objectMapper;
        this.mergePolicy = mergePolicy == null ? new PersonaMergePolicy() : mergePolicy;
        this.resolver = resolver == null ? new PersonaResolver(objectMapper) : resolver;
    }

    public EffectivePersona compile(UserPersonaEntity global, ConversationPersonaOverrideEntity conversation) {
        return compile(global, conversation, (CurrentTurnPreference) null);
    }

    public EffectivePersona compile(UserPersonaEntity global, ConversationPersonaOverrideEntity conversation,
            CurrentTurnPreference turn) {
        PersonaResolver.ResolvedPersona resolved = resolver.resolve(global, conversation, turn);
        return compileResolved(resolved, List.of());
    }

    /** Legacy bridge. New request paths use CurrentTurnPreference and never infer from text. */
    @Deprecated(forRemoval = false)
    public EffectivePersona compile(UserPersonaEntity global, ConversationPersonaOverrideEntity conversation,
            PersonaUpdateRequestLike turn) {
        List<String> globalTone = tones(global == null ? null : global.getToneJson());
        List<String> conversationTone = tones(conversation == null ? null : conversation.getToneJson());
        List<String> turnTone = turn == null ? List.of() : PersonaPolicy.normalizeValues(turn.tone(), PersonaPolicy.TONES);
        String role = mergePolicy.mergeRole(turn == null ? null : turn.role(),
                conversation == null ? null : conversation.getRole(), global == null ? null : global.getRole());
        List<String> tone = mergePolicy.mergeTone(PersonaPolicy.DEFAULT_TONE, globalTone, conversationTone, turnTone);
        List<String> behavior = mergePolicy.mergeBehaviors(PersonaPolicy.DEFAULT_BEHAVIORS,
                behaviors(global == null ? null : global.getBehaviorFlagsJson()),
                behaviors(conversation == null ? null : conversation.getBehaviorFlagsJson()),
                turn == null ? List.of() : turn.behaviorFlags());
        List<String> styles = mergePolicy.extractStylePreferences(firstNonBlank(
                turn == null ? null : turn.customDescription(),
                conversation == null ? null : conversation.getCustomDescription(),
                global == null ? null : global.getCustomDescription()));
        String customTone = firstSafeCustomTone(turn == null ? null : turn.customTone(),
                conversation == null ? null : conversation.getCustomTone(),
                global == null ? null : global.getCustomTone());
        String responseStyle = firstResponseStyle(turn == null ? null : turn.customResponseStyle(),
                conversation == null ? null : conversation.getCustomResponseStyle(),
                global == null ? null : global.getCustomResponseStyle());
        String hash = hash(role + "|" + tone + "|" + behavior + "|" + customTone + "|" + responseStyle + "|"
                + (global == null ? null : global.getVersion()) + "|"
                + (conversation == null ? null : conversation.getVersion()));
        return new EffectivePersona(role, tone, behavior, styles, customTone, responseStyle, "",
                global == null ? null : global.getVersion(), conversation == null ? null : conversation.getVersion(),
                turn != null, hash, Map.of());
    }

    private EffectivePersona compileResolved(PersonaResolver.ResolvedPersona resolved, List<String> legacyStyles) {
        String role = PersonaPolicy.ROLES.contains(resolved.role()) ? resolved.role() : PersonaPolicy.DEFAULT_ROLE;
        List<String> tone = PersonaPolicy.normalizeValues(resolved.tone(), PersonaPolicy.TONES);
        if (tone.isEmpty()) tone = PersonaPolicy.DEFAULT_TONE;
        List<String> behavior = PersonaPolicy.normalizeValues(resolved.behaviorFlags(), PersonaPolicy.BEHAVIORS);
        String customTone = PersonaPolicy.normalizeCustomTone(resolved.customTone());
        String responseStyle = PersonaPolicy.normalizeCustomResponseStyle(resolved.customResponseStyle());
        String hash = hash(role + "|" + tone + "|" + behavior + "|" + customTone + "|" + responseStyle + "|"
                + resolved.globalVersion() + "|" + resolved.conversationVersion());
        return new EffectivePersona(role, tone, behavior, legacyStyles, customTone, responseStyle,
                resolved.outputRequirement(), resolved.globalVersion(), resolved.conversationVersion(),
                resolved.turnOverridePresent(), hash,
                resolved.resolutionTrace());
    }

    private List<String> tones(String json) { return readValues(json, PersonaPolicy.TONES); }
    private List<String> behaviors(String json) { return readValues(json, PersonaPolicy.BEHAVIORS); }
    private List<String> readValues(String json, Set<String> allowed) {
        if (json == null || json.isBlank()) return List.of();
        try { return PersonaPolicy.normalizeValues(objectMapper.readValue(json, List.class), allowed); }
        catch (Exception ignored) { return List.of(); }
    }
    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return PersonaPolicy.normalize(value);
        return "";
    }
    private String firstSafeCustomTone(String... values) {
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
    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : digest) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception e) { return Integer.toHexString(value.hashCode()); }
    }

    public record PersonaUpdateRequestLike(String role, List<String> tone, List<String> behaviorFlags,
            String customDescription, String customTone, List<String> disabledBehaviorFlags,
            String customResponseStyle) {
        public PersonaUpdateRequestLike(String role, List<String> tone, List<String> behaviorFlags,
                String customDescription) {
            this(role, tone, behaviorFlags, customDescription, null, List.of(), null);
        }
        public PersonaUpdateRequestLike(String role, List<String> tone, List<String> behaviorFlags,
                String customDescription, String customTone) {
            this(role, tone, behaviorFlags, customDescription, customTone, List.of(), null);
        }
    }
}
