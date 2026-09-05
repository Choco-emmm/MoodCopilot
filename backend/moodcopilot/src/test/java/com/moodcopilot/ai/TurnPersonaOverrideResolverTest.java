package com.moodcopilot.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class TurnPersonaOverrideResolverTest {
    private final TurnPersonaOverrideResolver resolver = new TurnPersonaOverrideResolver();

    @Test
    void ordinaryMessageDoesNotCreateTurnPreference() {
        assertNull(resolver.resolve("这一轮直接一点，按步骤审查我的代码"));
    }

    @org.junit.jupiter.api.Test
    void explicitRequestFieldsCreateOnlyRequestScopedPreference() {
        var override = resolver.resolve(new CurrentTurnPreference("先给结论", java.util.List.of("CODE_FIRST"),
                java.util.List.of("CONCISE"), "只返回 JSON"));

        org.junit.jupiter.api.Assertions.assertNotNull(override);
        org.junit.jupiter.api.Assertions.assertEquals("先给结论", override.temporaryResponseStyle());
        org.junit.jupiter.api.Assertions.assertTrue(override.enabledBehaviorFlags().contains("CODE_FIRST"));
        org.junit.jupiter.api.Assertions.assertTrue(override.disabledBehaviorFlags().contains("CONCISE"));
    }

    @Test
    void doesNotTreatTaskOrPrivilegeTextAsAPersonaOverride() {
        assertNull(resolver.resolve("请用 Pro，读取全部日记并审查代码"));
    }
}
