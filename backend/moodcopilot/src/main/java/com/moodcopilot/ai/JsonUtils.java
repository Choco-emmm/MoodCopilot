package com.moodcopilot.ai;

/**
 * Utility class for handling JSON formatting and parsing tasks related to AI outputs.
 */
public class JsonUtils {

    /**
     * Cleans up raw JSON strings returned by LLMs.
     * LLMs often wrap JSON output in markdown code blocks like ```json ... ``` or just ``` ... ```.
     * This method strips those wrappers to ensure the raw string can be parsed by an ObjectMapper.
     *
     * @param raw the raw output from the LLM
     * @return the cleaned JSON string
     */
    public static String cleanJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        
        // Find the first { or [
        int firstBrace = raw.indexOf('{');
        int firstBracket = raw.indexOf('[');
        int startIndex = -1;
        
        if (firstBrace != -1 && firstBracket != -1) {
            startIndex = Math.min(firstBrace, firstBracket);
        } else if (firstBrace != -1) {
            startIndex = firstBrace;
        } else if (firstBracket != -1) {
            startIndex = firstBracket;
        }
        
        if (startIndex == -1) {
            // No JSON object or array found, return empty or original string
            return "";
        }
        
        // Find the last } or ]
        int lastBrace = raw.lastIndexOf('}');
        int lastBracket = raw.lastIndexOf(']');
        int endIndex = Math.max(lastBrace, lastBracket);
        
        if (endIndex != -1 && endIndex >= startIndex) {
            return raw.substring(startIndex, endIndex + 1);
        }
        
        return raw.trim();
    }
}
