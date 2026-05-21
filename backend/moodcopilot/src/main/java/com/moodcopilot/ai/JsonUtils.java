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
        String cleaned = raw.trim();
        // Remove markdown json block wrappers
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring("```json".length());
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring("```".length());
        }
        
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }
}
