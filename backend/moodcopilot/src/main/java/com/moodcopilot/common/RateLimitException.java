package com.moodcopilot.common;

public class RateLimitException extends RuntimeException {

    private final String type;

    public RateLimitException(String type, String message) {
        super(message);
        this.type = type;
    }

    public String getType() { return type; }
}
