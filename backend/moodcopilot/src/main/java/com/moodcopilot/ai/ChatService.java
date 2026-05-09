package com.moodcopilot.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.entity.*;
import com.moodcopilot.mapper.DiaryAnalysisMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.DiarySummaryMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class ChatService {

    private final DeepSeekClient deepSeekClient;
    private final DiaryMapper diaryMapper;
    private final DiaryAnalysisMapper diaryAnalysisMapper;
    private final DiarySummaryMapper summaryMapper;

    private static final String CHAT_SYSTEM_PROMPT = """
            你是 MoodCopilot 的情绪陪伴伙伴，名叫「小情绪」。你温暖、善解人意，像一位了解你的朋友。
            你会根据用户最近的日记和情绪总结来回应用户，你的回复自然、温柔，不机械不模板。
            可以适度追问、关心细节，也可以给一些温柔的提醒。
            回复控制在 300 字以内，用口语化的中文。""";

    public ChatService(DeepSeekClient deepSeekClient,
                       DiaryMapper diaryMapper,
                       DiaryAnalysisMapper diaryAnalysisMapper,
                       DiarySummaryMapper summaryMapper) {
        this.deepSeekClient = deepSeekClient;
        this.diaryMapper = diaryMapper;
        this.diaryAnalysisMapper = diaryAnalysisMapper;
        this.summaryMapper = summaryMapper;
    }

    public String chat(String message) {
        UserEntity user = currentUser();
        String context = buildContext(user.getId());
        String systemPrompt = CHAT_SYSTEM_PROMPT + "\n\n" + context;
        return deepSeekClient.chat(systemPrompt, message);
    }

    private String buildContext(long userId) {
        StringBuilder sb = new StringBuilder();

        List<DiaryEntity> recentDiaries = diaryMapper.selectList(
                new LambdaQueryWrapper<DiaryEntity>()
                        .eq(DiaryEntity::getAuthorUserId, userId)
                        .orderByDesc(DiaryEntity::getCreatedAt)
                        .last("LIMIT 10")
        );

        if (!recentDiaries.isEmpty()) {
            sb.append("用户最近的日记：\n");
            var sorted = recentDiaries.stream()
                    .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                    .toList();
            for (DiaryEntity diary : sorted) {
                DiaryAnalysisEntity analysis = diaryAnalysisMapper.selectById(diary.getId());
                if (analysis != null) {
                    sb.append("- ").append(diary.getCreatedAt().toLocalDate())
                            .append(" 情绪：").append(analysis.getMoodLabel())
                            .append("，主题：").append(String.join("、", analysis.getTopicLabelsJson()))
                            .append("，摘要：").append(analysis.getSummary())
                            .append("\n");
                }
            }
            sb.append("\n");
        }

        List<DiarySummaryEntity> summaries = summaryMapper.selectList(
                new LambdaQueryWrapper<DiarySummaryEntity>()
                        .eq(DiarySummaryEntity::getUserId, userId)
                        .orderByDesc(DiarySummaryEntity::getCreatedAt)
                        .last("LIMIT 5")
        );

        if (!summaries.isEmpty()) {
            sb.append("用户的情绪总结：\n");
            for (DiarySummaryEntity s : summaries) {
                String snippet = s.getAiSummary().length() > 100
                        ? s.getAiSummary().substring(0, 100) + "..."
                        : s.getAiSummary();
                sb.append("- ").append(s.getTitle()).append("：").append(snippet).append("\n");
            }
        }

        return sb.toString();
    }

    private UserEntity currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserEntity user) {
            return user;
        }
        throw new ResponseStatusException(BAD_REQUEST, "用户未登录");
    }
}
