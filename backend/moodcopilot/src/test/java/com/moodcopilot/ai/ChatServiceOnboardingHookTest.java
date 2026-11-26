package com.moodcopilot.ai;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.moodcopilot.ai.tool.ChatToolRegistry;
import com.moodcopilot.config.AiPromptProperties;
import com.moodcopilot.diary.DiaryService;
import com.moodcopilot.entity.ChatConversationEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.entity.dto.CustomChatMessage;
import com.moodcopilot.event.LifeChapterService;
import com.moodcopilot.event.LifeEventService;
import com.moodcopilot.growth.UserGrowthService;
import com.moodcopilot.mapper.ChatConversationMapper;
import com.moodcopilot.mapper.DiaryKnowledgeGraphMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.onboarding.GuideKey;
import com.moodcopilot.onboarding.OnboardingService;
import com.moodcopilot.security.RateLimitService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceOnboardingHookTest {

    private static final Long USER_ID = 7L;
    private static final Long CONVERSATION_ID = 42L;

    private ChatConversationMapper conversationMapper;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ChatAgentLoop agentLoop;
    private OnboardingService onboardingService;
    private ChatService chatService;

    @BeforeAll
    static void initMybatisLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ChatConversationEntity.class);
    }

    @BeforeEach
    void setup() {
        conversationMapper = mock(ChatConversationMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        agentLoop = mock(ChatAgentLoop.class);
        onboardingService = mock(OnboardingService.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any())).thenReturn(1L);

        ChatConversationEntity conv = new ChatConversationEntity();
        conv.setId(CONVERSATION_ID);
        conv.setUserId(USER_ID);
        when(conversationMapper.selectById(CONVERSATION_ID)).thenReturn(conv);

        ContextPlanner contextPlanner = mock(ContextPlanner.class);
        ContextEnvelope emptyEnvelope = new ContextEnvelope(
                "ctx-1", CONVERSATION_ID, USER_ID, ContextPurpose.CHAT, Instant.now(), "v1",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(contextPlanner.planEnvelopeWithReferencePurpose(
                anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ContextPlanner.ContextPlan("", emptyEnvelope));

        PersonaService personaService = mock(PersonaService.class);
        EffectivePersona persona = new EffectivePersona(
                "personal_assistant", List.of(), List.of(), List.of(), 1, null, false, "hash-1");
        when(personaService.compileForChat(USER_ID, CONVERSATION_ID)).thenReturn(persona);

        TaskContextResolver taskContextResolver = mock(TaskContextResolver.class);
        when(taskContextResolver.resolve(anyString()))
                .thenReturn(new TaskContext("GENERAL", "general chat", List.of(), null));

        PromptComposer promptComposer = mock(PromptComposer.class);
        when(promptComposer.compose(any(), any(EffectivePersona.class), any(), any(), any(ContextEnvelope.class)))
                .thenReturn("system prompt");

        ChatImageCaptionService imageCaptionService = mock(ChatImageCaptionService.class);
        when(imageCaptionService.describeForChat(any(), any(), any())).thenReturn("");

        AiPromptProperties promptProperties = new AiPromptProperties();
        promptProperties.setAgentToolsPrompt("agent prompt");

        Cache<String, List<CustomChatMessage>> memoryCache = Caffeine.newBuilder().build();

        chatService = new ChatService(
                mock(ChatClient.class),
                conversationMapper,
                memoryCache,
                redisTemplate,
                new ObjectMapper(),
                mock(RateLimitService.class),
                mock(UserGrowthService.class),
                mock(DiaryService.class),
                mock(MemoryExtractionService.class),
                contextPlanner,
                mock(RagMemoryService.class),
                mock(AiAnalysisService.class),
                mock(DeepSeekClient.class),
                mock(DiaryKnowledgeGraphMapper.class),
                mock(DiaryMapper.class),
                mock(VisionService.class),
                mock(ChatToolRegistry.class),
                agentLoop,
                new ChatModelProfiles("deepseek-v4-flash", 32768, "deepseek-v4-pro", 32768),
                imageCaptionService,
                promptProperties,
                mock(LifeEventService.class),
                mock(LifeChapterService.class),
                mock(ChatTitleService.class),
                personaService,
                taskContextResolver,
                mock(ContextMetadataRecorder.class),
                mock(PersonaPromptSupport.class),
                promptComposer,
                onboardingService,
                "Asia/Shanghai"
        );

        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void chatStreamRecordsFirstChatReplyOnlyAfterPersistedNonBlankCompletion() {
        AgentLoopOutcome outcome = outcomeWith("你好，我在听。", Flux.just("你好，", "我在听。"));
        when(agentLoop.run(any(), any(), any(), any(), any(), anyString(), anyBoolean())).thenReturn(outcome);

        ChatService.ChatStreamContext ctx = chatService.chat(
                CONVERSATION_ID, "你好", List.of(), "", false,
                ReferencePurpose.DISCUSS, List.of(), null, List.of(), "run-1", true);

        // Before stream completes, hook has not fired
        verify(onboardingService, never()).recordBusinessTaskCompleted(anyLong(), any());

        ctx.stream().collectList().block();
        verify(onboardingService).recordBusinessTaskCompleted(USER_ID, GuideKey.FIRST_CHAT_REPLY);
    }

    @Test
    void approvalPauseDoesNotRecordFirstChatReplyUntilResumedCompletion() {
        AgentLoopOutcome paused = outcomeWith("我先准备修改记忆", Flux.just("我先准备修改记忆"));
        paused.pause(Map.of(ChatAgentLoop.APPROVAL_KEY, Map.of("items", List.of())));
        when(agentLoop.run(any(), any(), any(), any(), any(), anyString(), anyBoolean())).thenReturn(paused);

        ChatService.ChatStreamContext initial = chatService.chat(
                CONVERSATION_ID, "帮我改下记忆", List.of(), "", false,
                ReferencePurpose.DISCUSS, List.of(), null, List.of(), "run-pause", true);
        initial.stream().collectList().block();

        verify(onboardingService, never()).recordBusinessTaskCompleted(anyLong(), any());

        // Resume completes cleanly -> now records FIRST_CHAT_REPLY
        AgentLoopOutcome resumed = outcomeWith("已经按你的确认更新好了。", Flux.just("已经按你的确认更新好了。"));
        when(agentLoop.resume(any(), any(), any(), any(), anyString(), any())).thenReturn(resumed);

        ChatTurn turn = new ChatTurn(List.of(), "帮我改下记忆", CONVERSATION_ID, List.of());
        ChatService.ChatStreamContext resumedCtx = chatService.resumeChat(
                USER_ID, turn, false, Map.of(), "run-pause");
        resumedCtx.stream().collectList().block();

        verify(onboardingService).recordBusinessTaskCompleted(USER_ID, GuideKey.FIRST_CHAT_REPLY);
    }

    @Test
    void streamErrorAndCancelDoNotRecordFirstChatReply() {
        AgentLoopOutcome errored = outcomeWith("半截内容", Flux.concat(Flux.just("半截内容"), Flux.error(new RuntimeException("upstream failed"))));
        when(agentLoop.run(any(), any(), any(), any(), any(), anyString(), anyBoolean())).thenReturn(errored);

        ChatService.ChatStreamContext errCtx = chatService.chat(
                CONVERSATION_ID, "hi", List.of(), "", false,
                ReferencePurpose.DISCUSS, List.of(), null, List.of(), "run-err", false);
        assertThrows(RuntimeException.class, () -> errCtx.stream().collectList().block());
        verify(onboardingService, never()).recordBusinessTaskCompleted(anyLong(), any());

        AgentLoopOutcome cancelled = outcomeWith("第一段", Flux.just("第一段", "第二段"));
        when(agentLoop.run(any(), any(), any(), any(), any(), anyString(), anyBoolean())).thenReturn(cancelled);

        ChatService.ChatStreamContext cancelCtx = chatService.chat(
                CONVERSATION_ID, "hi", List.of(), "", false,
                ReferencePurpose.DISCUSS, List.of(), null, List.of(), "run-cancel", false);
        cancelCtx.stream().take(1).collectList().block();
        verify(onboardingService, never()).recordBusinessTaskCompleted(anyLong(), any());
    }

    @Test
    void blankReplyOrReasoningOnlyDoesNotRecordFirstChatReply() {
        AgentLoopOutcome blank = outcomeWith("   ", Flux.just("   "));
        chatService.addAssistantTurn(USER_ID, CONVERSATION_ID, blank, false);
        verify(onboardingService, never()).recordBusinessTaskCompleted(anyLong(), any());

        AgentLoopOutcome reasoningOnly = new AgentLoopOutcome();
        reasoningOnly.appendText(AgentLoopOutcome.REASONING_MARKER + "thinking without visible reply");
        chatService.addAssistantTurn(USER_ID, CONVERSATION_ID, reasoningOnly, true);
        verify(onboardingService, never()).recordBusinessTaskCompleted(anyLong(), any());
    }

    @Test
    void historyPersistenceFailureDoesNotRecordFirstChatReply() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("redis unavailable"));

        AgentLoopOutcome outcome = outcomeWith("完整回复", Flux.just("完整回复"));
        assertThrows(IllegalStateException.class,
                () -> chatService.addAssistantTurn(USER_ID, CONVERSATION_ID, outcome, false));
        verify(onboardingService, never()).recordBusinessTaskCompleted(anyLong(), any());
    }

    @Test
    void nonStreamingReplyRecordsFirstChatReplyAndSurvivesOnboardingException() {
        AgentLoopOutcome outcome = outcomeWith("非流式完整回复", Flux.just("非流式完整回复"));
        when(agentLoop.run(any(), any(), any(), any(), any(), anyString(), anyBoolean())).thenReturn(outcome);
        doThrow(new RuntimeException("onboarding failure"))
                .when(onboardingService).recordBusinessTaskCompleted(USER_ID, GuideKey.FIRST_CHAT_REPLY);

        String reply = chatService.reply(
                CONVERSATION_ID, "帮我整理一下计划", List.of(), "", false,
                ReferencePurpose.DISCUSS, List.of(), null, List.of());

        assertEquals("非流式完整回复", reply);
        verify(onboardingService).recordBusinessTaskCompleted(USER_ID, GuideKey.FIRST_CHAT_REPLY);
    }

    private static AgentLoopOutcome outcomeWith(String reply, Flux<String> chunks) {
        AgentLoopOutcome outcome = new AgentLoopOutcome();
        outcome.appendText(reply);
        outcome.attachChunks(chunks);
        return outcome;
    }
}
