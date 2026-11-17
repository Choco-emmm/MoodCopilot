package com.moodcopilot.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.ai.MemoryExtractionService;
import com.moodcopilot.ai.MemoryOrchestrator;
import com.moodcopilot.ai.RagMemoryService;
import com.moodcopilot.ai.VisionService;
import com.moodcopilot.ai.tool.impl.CreateEventTool;
import com.moodcopilot.ai.tool.impl.DeleteMemoryTool;
import com.moodcopilot.ai.tool.impl.DiaryImageAnalysisTool;
import com.moodcopilot.ai.tool.impl.DiarySemanticSearchTool;
import com.moodcopilot.ai.tool.impl.DiaryKeywordSearchTool;
import com.moodcopilot.ai.tool.impl.GraphSearchTool;
import com.moodcopilot.ai.tool.impl.ListEventsTool;
import com.moodcopilot.ai.tool.impl.ListRecentDiariesTool;
import com.moodcopilot.ai.tool.impl.MemoryQueryTool;
import com.moodcopilot.ai.tool.impl.MergeMemoryTool;
import com.moodcopilot.ai.tool.impl.ReadDiaryTool;
import com.moodcopilot.ai.tool.impl.ReadImageTextTool;
import com.moodcopilot.ai.tool.impl.ReportSnapshotTool;
import com.moodcopilot.ai.tool.impl.SaveMemoryTool;
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
            @Lazy MemoryOrchestrator memoryOrchestrator,
            @Lazy DiaryKnowledgeGraphMapper diaryKnowledgeGraphMapper,
            DiaryMapper diaryMapper,
            VisionService visionService,
            RateLimitService rateLimitService,
            @Lazy LifeEventService lifeEventService) {
        List<ChatTool<?>> tools = List.of(
                // 概览类问题先给列表：没有具体检索词的「我最近都写了什么」
                // 之前只能靠两个检索工具反复试探，容易把 Agent Loop 的递归深度耗光
                new ListRecentDiariesTool(diaryService),
                new DiarySemanticSearchTool(ragMemoryService),
                new DiaryKeywordSearchTool(diaryService),
                new ReadDiaryTool(diaryMapper),
                new UserStatsTool(diaryService),
                new ReportSnapshotTool(diaryService),
                new MemoryQueryTool(memoryExtractionService, ragMemoryService),
                new GraphSearchTool(diaryKnowledgeGraphMapper, ragMemoryService),
                new DiaryImageAnalysisTool(diaryMapper, visionService, rateLimitService),
                new ReadImageTextTool(visionService),
                new ListEventsTool(lifeEventService),
                new UpdateEventStatusTool(lifeEventService),
                new CreateEventTool(lifeEventService),
                // 写类工具排在读类之后：模型先查清楚再决定改什么
                new SaveMemoryTool(memoryOrchestrator, memoryExtractionService),
                new com.moodcopilot.ai.tool.impl.UpdateMemoryTool(memoryOrchestrator),
                new DeleteMemoryTool(memoryOrchestrator),
                new MergeMemoryTool(memoryOrchestrator, memoryExtractionService));
        return new ChatToolRegistry(objectMapper, tools);
    }
}
