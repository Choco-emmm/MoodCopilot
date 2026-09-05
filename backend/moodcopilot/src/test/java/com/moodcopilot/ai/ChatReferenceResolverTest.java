package com.moodcopilot.ai;

import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.UserLifeEventMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatReferenceResolverTest {

    @Test
    void resolvesOwnedDiaryWithoutTrustingClientContent() {
        DiaryMapper diaryMapper = mock(DiaryMapper.class);
        UserLifeEventMapper eventMapper = mock(UserLifeEventMapper.class);
        DiaryEntity diary = new DiaryEntity();
        diary.setId(2014L);
        diary.setAuthorUserId(7L);
        diary.setIsDeleted(false);
        diary.setContent("服务端保存的日记正文");
        diary.setCreatedAt(LocalDateTime.of(2026, 9, 4, 12, 30));
        when(diaryMapper.selectOne(any())).thenReturn(diary);

        ChatReferenceResolver resolver = new ChatReferenceResolver(diaryMapper, eventMapper, "Asia/Shanghai");
        List<UserReference> result = resolver.resolve(7L,
                List.of(new ChatReferenceRequest("diary", 2014L, "RECALL")), ReferencePurpose.DISCUSS);

        assertEquals(1, result.size());
        assertEquals("服务端保存的日记正文", result.get(0).content());
        assertEquals("USER_DIARY", result.get(0).source().sourceType());
        assertEquals("2014", result.get(0).source().sourceId());
        assertEquals(ReferencePurpose.RECALL, result.get(0).referencePurpose());
    }

    @Test
    void ignoresUnknownSourceTypes() {
        ChatReferenceResolver resolver = new ChatReferenceResolver(mock(DiaryMapper.class),
                mock(UserLifeEventMapper.class), "Asia/Shanghai");

        assertTrue(resolver.resolve(7L,
                List.of(new ChatReferenceRequest("formal_memory", 1L, null)), ReferencePurpose.DISCUSS).isEmpty());
    }
}
