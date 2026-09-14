package com.moodcopilot.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.MemoryExtractionService;
import com.moodcopilot.ai.RagMemoryService;
import com.moodcopilot.ai.VisionService;
import com.moodcopilot.ai.tool.impl.DiaryImageAnalysisTool;
import com.moodcopilot.ai.tool.impl.DiarySearchTool;
import com.moodcopilot.ai.tool.impl.GraphSearchTool;
import com.moodcopilot.ai.tool.impl.ListEventsTool;
import com.moodcopilot.ai.tool.impl.MemoryQueryTool;
import com.moodcopilot.ai.tool.impl.ReadImageTextTool;
import com.moodcopilot.ai.tool.impl.ReportSnapshotTool;
import com.moodcopilot.ai.tool.impl.UpdateEventStatusTool;
import com.moodcopilot.ai.tool.impl.UserStatsTool;
import com.moodcopilot.diary.DiaryService;
import com.moodcopilot.event.LifeEventService;
import com.moodcopilot.mapper.DiaryKnowledgeGraphMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.security.RateLimitService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.List;

/**
 * 装配聊天工具注册表。
 * <p>
 * 工具在此按显式顺序构造，而不是靠 Spring 的集合注入 —— 声明顺序就是发给模型的顺序，
 * 不能依赖容器的不确定排序。
 * <p>
 * 这些 {@code @Lazy} 是从原 AIConfiguration 的工具 Bean 参数上原样保留的：
 * 打破了 DiaryService/RagMemoryService 与 ChatService 之间的循环依赖，去掉会导致启动失败。
 */
@Configuration
public class ChatToolConfiguration {

    @Bean
    public ChatToolRegistry chatToolRegistry(
            ObjectMapper objectMapper,
            @Lazy DiaryService diaryService,
            @Lazy RagMemoryService ragMemoryService,
            @Lazy MemoryExtractionService memoryExtractionService,
            @Lazy DiaryKnowledgeGraphMapper diaryKnowledgeGraphMapper,
            DiaryMapper diaryMapper,
            VisionService visionService,
            RateLimitService rateLimitService,
            @Lazy LifeEventService lifeEventService) {
        List<ChatTool<?>> tools = List.of(
                new DiarySearchTool(diaryService, ragMemoryService),
                new UserStatsTool(diaryService),
                new ReportSnapshotTool(diaryService),
                new MemoryQueryTool(memoryExtractionService, ragMemoryService),
                new GraphSearchTool(diaryKnowledgeGraphMapper, ragMemoryService),
                new DiaryImageAnalysisTool(diaryMapper, visionService, rateLimitService),
                new ReadImageTextTool(visionService),
                new ListEventsTool(lifeEventService),
                new UpdateEventStatusTool(lifeEventService));
        return new ChatToolRegistry(objectMapper, tools);
    }
}
