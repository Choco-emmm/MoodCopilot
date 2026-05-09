package com.moodcopilot.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.entity.*;
import com.moodcopilot.mapper.DiaryAnalysisMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.DiarySummaryMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class ChatService {

    private final ChatClient chatChatClient;
    private final DiaryMapper diaryMapper;
    private final DiaryAnalysisMapper diaryAnalysisMapper;
    private final DiarySummaryMapper summaryMapper;

    public ChatService(ChatClient chatChatClient,
                       DiaryMapper diaryMapper,
                       DiaryAnalysisMapper diaryAnalysisMapper,
                       DiarySummaryMapper summaryMapper) {
        this.chatChatClient = chatChatClient;
        this.diaryMapper = diaryMapper;
        this.diaryAnalysisMapper = diaryAnalysisMapper;
        this.summaryMapper = summaryMapper;
    }

    public String chat(String message) {
        UserEntity user = currentUser();
        String context = buildContext(user.getId());
        return chatChatClient.prompt()
                .user(message)
                .system(s -> s.text(context))
                .call()
                .content();
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
