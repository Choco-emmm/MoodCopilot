package com.moodcopilot.diary;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.AiAnalysisService;
import com.moodcopilot.ai.MemoryExtractionService;
import com.moodcopilot.entity.DiaryAnalysisEntity;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.DiaryRecommendationExposureEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.follow.FollowService;
import com.moodcopilot.mapper.DiaryAnalysisMapper;
import com.moodcopilot.mapper.DiaryCommentMapper;
import com.moodcopilot.mapper.DiaryHideMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.DiaryRecommendationExposureMapper;
import com.moodcopilot.mapper.DiaryResonanceMapper;
import com.moodcopilot.notification.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryRecommendationServiceTest {

    @Mock private DiaryMapper diaryMapper;
    @Mock private DiaryAnalysisMapper diaryAnalysisMapper;
    @Mock private DiaryCommentMapper diaryCommentMapper;
    @Mock private DiaryResonanceMapper diaryResonanceMapper;
    @Mock private DiaryHideMapper diaryHideMapper;
    @Mock private DiaryRecommendationExposureMapper exposureMapper;
    @Mock private AiAnalysisService aiAnalysisService;
    @Mock private MemoryExtractionService memoryExtractionService;
    @Mock private NotificationService notificationService;
    @Mock private FollowService followService;
    @Mock private StringRedisTemplate redisTemplate;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void todayMatchSkipsRecentExposureAndRecordsResult() {
        loginAs(1L);
        DiaryService service = service();
        when(diaryMapper.selectList(any()))
                .thenReturn(List.of(diary(10L, 1L, "自己", "PRIVATE", 3)))
                .thenReturn(List.of(
                        diary(20L, 2L, "同频 A", "PUBLIC", 2),
                        diary(21L, 3L, "同频 B", "PUBLIC", 1),
                        diary(22L, 4L, "不同情绪", "PUBLIC", 0)
                ));
        when(diaryAnalysisMapper.selectBatchIds(any()))
                .thenReturn(List.of(analysis(10L, "疲惫", "工作")))
                .thenReturn(List.of(
                        analysis(20L, "疲惫", "工作"),
                        analysis(21L, "疲惫", "工作"),
                        analysis(22L, "开心", "生活")
                ));
        when(diaryHideMapper.selectList(any())).thenReturn(List.of());
        when(exposureMapper.selectList(any())).thenReturn(List.of(exposure(20L, "TODAY_MATCH")));

        DiaryView match = service.todayMatch();

        assertThat(match.id()).isEqualTo(21L);
        ArgumentCaptor<DiaryRecommendationExposureEntity> captor =
                ArgumentCaptor.forClass(DiaryRecommendationExposureEntity.class);
        verify(exposureMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getDiaryId()).isEqualTo(21L);
        assertThat(captor.getValue().getScene()).isEqualTo("TODAY_MATCH");
    }

    @Test
    void similarSkipsOwnDiaryDeduplicatesAuthorAndRecordsExposures() {
        loginAs(1L);
        DiaryService service = service();
        when(diaryMapper.selectById(10L)).thenReturn(diary(10L, 1L, "自己", "PUBLIC", 4));
        when(diaryAnalysisMapper.selectById(10L)).thenReturn(analysis(10L, "疲惫", "工作"));
        Page<DiaryEntity> candidates = Page.of(1, 200, 4);
        candidates.setRecords(List.of(
                diary(11L, 1L, "自己旧日记", "PUBLIC", 3),
                diary(12L, 2L, "作者 A", "PUBLIC", 2),
                diary(13L, 2L, "作者 A 另一篇", "PUBLIC", 1),
                diary(14L, 3L, "作者 B", "PUBLIC", 0)
        ));
        when(diaryMapper.selectPage(any(), any())).thenReturn(candidates);
        when(diaryHideMapper.selectList(any())).thenReturn(List.of());
        when(diaryAnalysisMapper.selectBatchIds(any())).thenReturn(List.of(
                analysis(12L, "疲惫", "工作"),
                analysis(13L, "疲惫", "生活"),
                analysis(14L, "疲惫", "生活")
        ));

        List<DiaryView> result = service.similar(10L, 3);

        assertThat(result).extracting(DiaryView::id).containsExactly(12L, 14L);
        ArgumentCaptor<DiaryRecommendationExposureEntity> captor =
                ArgumentCaptor.forClass(DiaryRecommendationExposureEntity.class);
        verify(exposureMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(DiaryRecommendationExposureEntity::getDiaryId)
                .containsExactly(12L, 14L);
        assertThat(captor.getAllValues()).allMatch(e -> "SIMILAR_DIARIES".equals(e.getScene()));
    }

    private DiaryService service() {
        return new DiaryService(
                diaryMapper,
                diaryAnalysisMapper,
                diaryCommentMapper,
                diaryResonanceMapper,
                diaryHideMapper,
                exposureMapper,
                aiAnalysisService,
                memoryExtractionService,
                notificationService,
                followService,
                redisTemplate,
                new ObjectMapper()
        );
    }

    private void loginAs(long userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setDisplayName("测试用户");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null)
        );
    }

    private DiaryEntity diary(long id, long authorUserId, String authorName, String visibility, int daysAgo) {
        DiaryEntity diary = new DiaryEntity();
        diary.setId(id);
        diary.setAuthorUserId(authorUserId);
        diary.setAuthorName(authorName);
        diary.setContent("今天很累");
        diary.setVisibility(visibility);
        diary.setResonanceCount(0);
        diary.setIsDeleted(false);
        diary.setCreatedAt(LocalDateTime.now().minusDays(daysAgo));
        diary.setUpdatedAt(diary.getCreatedAt());
        return diary;
    }

    private DiaryAnalysisEntity analysis(long diaryId, String mood, String topic) {
        DiaryAnalysisEntity analysis = new DiaryAnalysisEntity();
        analysis.setDiaryId(diaryId);
        analysis.setMoodLabel(mood);
        analysis.setMoodIntensity(3);
        analysis.setTopicLabelsJson(List.of(topic));
        return analysis;
    }

    private DiaryRecommendationExposureEntity exposure(long diaryId, String scene) {
        DiaryRecommendationExposureEntity exposure = new DiaryRecommendationExposureEntity();
        exposure.setUserId(1L);
        exposure.setDiaryId(diaryId);
        exposure.setScene(scene);
        exposure.setCreatedAt(LocalDateTime.now().minusDays(1));
        return exposure;
    }
}
