package com.moodcopilot.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DiaryMemorySignalContractTest {
    @Test void primaryAnalysisDropsOversizedMemoryInsteadOfTruncatingIt() throws Exception {
        var service = new AiAnalysisService(null, null, new ObjectMapper(), null, null, null, null, null, null);
        var parse = AiAnalysisService.class.getDeclaredMethod("parseMemorySignals", Object.class);
        parse.setAccessible(true);
        var signal = new HashMap<String,Object>(); signal.put("attributeKey", "生活偏好");
        signal.put("attributeValue", "字".repeat(501)); signal.put("memoryType", "preference");
        signal.put("assertionType", "explicit"); signal.put("evidence", "原文");
        assertTrue(((List<?>)parse.invoke(service, List.of(signal))).isEmpty());
        signal.put("attributeValue", "字".repeat(500));
        assertEquals(1, ((List<?>)parse.invoke(service, List.of(signal))).size());
    }
}
