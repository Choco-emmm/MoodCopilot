package com.moodcopilot.ai.mq;

import com.moodcopilot.entity.*;
import com.moodcopilot.mapper.DiaryMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class DiaryTaskInputTest {
    @Test void attachmentsAndRepeatedUserRequestsNeverReuseCompletedOperations() {
        var tasks = mock(AiTaskService.class);
        var mapper = mock(DiaryMapper.class);
        var diary = new DiaryEntity();
        diary.setContent("相同正文"); diary.setImages(List.of("image-a"));
        when(mapper.selectOne(any())).thenReturn(diary);
        var producer = new AiTaskProducer(tasks, mapper);
        producer.submitDiaryAnalysisTask(9, 7, false);
        diary.setImages(List.of("image-b"));
        producer.submitDiaryAnalysisTask(9, 7, false);
        var music = new MusicMeta(); music.setUserLyric("新歌词"); diary.setMusicMeta(music);
        producer.submitDiaryAnalysisTask(9, 7, false);
        producer.submitDiaryAnalysisTask(9, 7, false);
        var versions = ArgumentCaptor.forClass(String.class);
        var keys = ArgumentCaptor.forClass(String.class);
        verify(tasks, times(4)).enqueue(eq(7L), eq(AiTaskMessage.TYPE_DIARY_ANALYSIS), eq("9"), versions.capture(), anyString(), keys.capture(), any(), isNull());
        assertNotEquals(versions.getAllValues().get(0), versions.getAllValues().get(1));
        assertNotEquals(versions.getAllValues().get(1), versions.getAllValues().get(2));
        assertEquals(versions.getAllValues().get(2), versions.getAllValues().get(3));
        assertEquals(4, keys.getAllValues().stream().distinct().count());
    }
}
