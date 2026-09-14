package com.moodcopilot.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatModelProfilesTest {

    private final ChatModelProfiles profiles = new ChatModelProfiles(
            "deepseek-v4-flash", 32768, "deepseek-v4-pro", 32768);

    @Test
    void flashOmitsTemperatureAndReasoningEffortSoTheEndpointDefaultApplies() {
        AgentLoopOptions flash = profiles.flash();

        // 这两个为 null 是「保持改造前行为」的关键：DeepSeekClient 会据此不下发对应字段
        assertNull(flash.temperature(), "flash must not send a temperature");
        assertNull(flash.reasoningEffort(), "flash must not send reasoning_effort");
        assertEquals("deepseek-v4-flash", flash.model());
        assertEquals(32768, flash.maxTokens());
        assertFalse(flash.exposeReasoning(), "flash previously had no thinking panel");
        assertEquals("CHAT_STREAM", flash.logType());
        assertEquals("FLASH", flash.modelLabel());
    }

    @Test
    void proKeepsHighReasoningEffortAndExposesTheChainOfThought() {
        AgentLoopOptions pro = profiles.pro();

        assertNull(pro.temperature());
        assertEquals("high", pro.reasoningEffort());
        assertEquals("deepseek-v4-pro", pro.model());
        assertTrue(pro.exposeReasoning());
        assertEquals("CHAT_AGENT_STREAM", pro.logType());
        assertEquals("PRO", pro.modelLabel());
    }

    @Test
    void bothProfilesShareTheSameDepthLimit() {
        assertEquals(5, profiles.flash().maxDepth());
        assertEquals(5, profiles.pro().maxDepth());
    }

    @Test
    void blankModelFallsBackToTheDocumentedDefault() {
        ChatModelProfiles blank = new ChatModelProfiles("  ", 32768, null, 32768);

        assertEquals("deepseek-v4-flash", blank.flash().model());
        assertEquals("deepseek-v4-pro", blank.pro().model());
    }

    @Test
    void tinyMaxTokensIsRaisedToTheFloor() {
        ChatModelProfiles tiny = new ChatModelProfiles("m", 8, "m", 8);

        assertEquals(1024, tiny.flash().maxTokens());
        assertEquals(1024, tiny.pro().maxTokens());
    }
}
