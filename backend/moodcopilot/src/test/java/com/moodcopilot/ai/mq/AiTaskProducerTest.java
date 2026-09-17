package com.moodcopilot.ai.mq;

import com.moodcopilot.mapper.DiaryMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AiTaskProducerTest {

    private static final String SNAPSHOT = "snap-1";

    private final AiTaskService taskService = mock(AiTaskService.class);
    private final AiTaskProducer producer = new AiTaskProducer(taskService, mock(DiaryMapper.class));

    private List<String> capturedOperationKeys(int expectedCalls) {
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(taskService, times(expectedCalls)).enqueue(
                anyLong(), anyString(), anyString(), anyString(), any(), keys.capture(), any(), any());
        return keys.getAllValues();
    }

    @Test
    void automaticRefreshKeepsOneOperationKeyPerSnapshotSoRepeatedRecomputesCoalesce() {
        producer.submitLifeChapterRefreshTask(9L, 7L, SNAPSHOT);
        producer.submitLifeChapterRefreshTask(9L, 7L, SNAPSHOT);

        assertEquals(1, capturedOperationKeys(2).stream().distinct().count());
    }

    @Test
    void forcedRefreshAlwaysGetsAFreshOperationKey() {
        producer.submitLifeChapterRefreshTask(9L, 7L, SNAPSHOT, true);
        producer.submitLifeChapterRefreshTask(9L, 7L, SNAPSHOT, true);

        List<String> keys = capturedOperationKeys(2);
        assertNotEquals(keys.get(0), keys.get(1),
                "快照没变时用户主动重整也必须换新键，否则会被 ai_tasks 的幂等键当成重复任务丢掉");
        assertTrue(keys.get(0).contains(":retry:"));
    }
}
