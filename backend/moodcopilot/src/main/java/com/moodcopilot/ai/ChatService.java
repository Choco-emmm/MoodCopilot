package com.moodcopilot.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.entity.*;
import com.moodcopilot.mapper.DiaryAnalysisMapper;
import com.moodcopilot.mapper.DiaryMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class ChatService {

    private final ChatClient chatChatClient;
    private final DiaryMapper diaryMapper;
    private final DiaryAnalysisMapper diaryAnalysisMapper;
    private final Map<Long, ChatMemory> userChatMemories;

    public ChatService(ChatClient chatChatClient,
                       DiaryMapper diaryMapper,
                       DiaryAnalysisMapper diaryAnalysisMapper,
                       Map<Long, ChatMemory> userChatMemories) {
        this.chatChatClient = chatChatClient;
        this.diaryMapper = diaryMapper;
        this.diaryAnalysisMapper = diaryAnalysisMapper;
        this.userChatMemories = userChatMemories;
    }

    public Flux<String> chat(String message) {
        UserEntity user = currentUser();
        String context = buildContext(user.getId());

        ChatMemory memory = userChatMemories.computeIfAbsent(user.getId(), k -> new InMemoryChatMemory());

        return chatChatClient.prompt()
                .user(message)
                .system(s -> s.text(context))
                .advisors(new MessageChatMemoryAdvisor(memory))
                .stream()
                .content();
    }

    public void clearMemory(Long userId) {
        userChatMemories.remove(userId);
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
            sb.append("以下是你最近日记的内容（你可以引用它们来回复用户）：\n");
            var sorted = recentDiaries.stream()
                    .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                    .toList();
            for (int i = 0; i < sorted.size(); i++) {
                DiaryEntity diary = sorted.get(i);
                DiaryAnalysisEntity analysis = diaryAnalysisMapper.selectById(diary.getId());
                sb.append("[日记 #").append(i + 1).append(" · ").append(diary.getCreatedAt().toLocalDate()).append("] ");
                if (analysis != null) {
                    sb.append("情绪：").append(analysis.getMoodLabel())
                            .append("，主题：").append(String.join("、", analysis.getTopicLabelsJson()))
                            .append("\n内容：").append(diary.getContent()).append("\n");
                } else {
                    sb.append("内容：").append(diary.getContent()).append("\n");
                }
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
