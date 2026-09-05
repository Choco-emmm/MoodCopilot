package com.moodcopilot.ai;

import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;

/** Small timing helper for AI/network calls. It deliberately carries no prompt text. */
public final class AiCallTiming {
    private AiCallTiming() {}

    public static long start() {
        return System.nanoTime();
    }

    public static long elapsedMs(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    public static void completed(Logger log, String type, String model, long startedAt,
            String status, int inputLength, int outputLength) {
        log.info("AI调用完成 type={} model={} elapsedMs={} status={} inputLength={} outputLength={}",
                type, safe(model), elapsedMs(startedAt), safe(status), inputLength, outputLength);
    }

    public static void failed(Logger log, String type, String model, long startedAt,
            Throwable error, int inputLength) {
        log.warn("AI调用失败 type={} model={} elapsedMs={} errorType={} inputLength={}",
                type, safe(model), elapsedMs(startedAt),
                error == null ? "unknown" : error.getClass().getSimpleName(), inputLength);
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
