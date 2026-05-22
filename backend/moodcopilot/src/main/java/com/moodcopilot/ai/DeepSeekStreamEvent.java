package com.moodcopilot.ai;

public interface DeepSeekStreamEvent {
    record TextChunk(String text) implements DeepSeekStreamEvent {}
    record ToolCallReady(String toolCallId, String functionName, String argumentsJson) implements DeepSeekStreamEvent {}
}
