package com.moodcopilot.ai;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.UserMemoryCandidateEntity;
import com.moodcopilot.entity.UserMemoryEvidenceEntity;
import com.moodcopilot.entity.UserMemoryRejectionEntity;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import com.moodcopilot.mapper.UserMemoryCandidateMapper;
import com.moodcopilot.mapper.UserMemoryEvidenceMapper;
import com.moodcopilot.mapper.UserMemoryRejectionMapper;
import com.moodcopilot.mapper.UserProfileMemoryMapper;
import com.moodcopilot.notification.NotificationService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「按 key 彻底删除」的三条不变量。
 * <p>
 * 这是产品决策里最容易在重构中被磨平的地方：删除的语义是**连历史一起清掉、且不让它自己长回来**，
 * 但同时又必须给「用户亲口说要记」留门。三条各自钉一个断言，谁被改坏了都会红。
 */
class MemoryOrchestratorPurgeTest {

    private UserProfileMemoryMapper memoryMapper;
    private UserMemoryCandidateMapper candidateMapper;
    private UserMemoryEvidenceMapper evidenceMapper;
    private UserMemoryRejectionMapper rejectionMapper;
    private MemoryOrchestrator orchestrator;

    @BeforeAll
    static void initializeLambdaMetadataForMockedMapperPaths() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "memory-purge-test");
        TableInfoHelper.initTableInfo(assistant, UserMemoryCandidateEntity.class);
        TableInfoHelper.initTableInfo(assistant, UserMemoryEvidenceEntity.class);
        TableInfoHelper.initTableInfo(assistant, UserProfileMemoryEntity.class);
        TableInfoHelper.initTableInfo(assistant, UserMemoryRejectionEntity.class);
    }

    @BeforeEach
    void setUp() {
        memoryMapper = mock(UserProfileMemoryMapper.class);
        candidateMapper = mock(UserMemoryCandidateMapper.class);
        evidenceMapper = mock(UserMemoryEvidenceMapper.class);
        rejectionMapper = mock(UserMemoryRejectionMapper.class);
        orchestrator = new MemoryOrchestrator(memoryMapper, candidateMapper, evidenceMapper, rejectionMapper,
                mock(RagMemoryService.class), new ObjectMapper(), mock(NotificationService.class));
    }

    private static UserProfileMemoryEntity memory(String key, String value, String status) {
        UserProfileMemoryEntity memory = new UserProfileMemoryEntity();
        memory.setUserId(7L);
        memory.setAttributeKey(key);
        memory.setAttributeValue(value);
        memory.setMemoryType("preference");
        memory.setStatus(status);
        return memory;
    }

    /**
     * 让「按键封印」那条查询返回命中，普通墓碑（{@code isRejected}）返回未命中。
     * <p>
     * 两者都走 {@code selectCount}，靠 {@code any()} + 固定返回值区分不开 —— 只能看 SQL：
     * 只有按键封印带 {@code rejection_type} 条件。
     */
    private void sealOnlyTheKeySealQuery() {
        when(rejectionMapper.selectCount(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            LambdaQueryWrapper<UserMemoryRejectionEntity> wrapper = invocation.getArgument(0);
            return wrapper.getSqlSegment().contains("rejection_type") ? 1L : 0L;
        });
    }

    @Test
    void purgeRemovesEveryVersionOfTheKeyNotJustTheActiveOne() {
        // 同一个键在库里有两行：9/3 那条被顶掉的，和当前生效的。
        // 「删除」要的是整条记忆不存在，不是只把当前值停用。
        when(memoryMapper.selectList(any())).thenReturn(List.of(
                memory("心理状态", "情绪低落，有自伤念头", "superseded"),
                memory("心理状态", "自述已好转，状态稳定", "active")));

        int removed = orchestrator.purgeByKey(7L, "心理状态");

        assertEquals(2, removed);
        verify(memoryMapper).delete(any());
    }

    @Test
    void deletingByIdPurgesEveryVersionOfThatKey() {
        // 记忆中心那个「删除」按钮传的是 id，但清的是整个键 —— 两个入口必须是同一个意思，
        // 否则用户在一个地方删完以为干净了、在另一个地方删完才是真干净。
        when(memoryMapper.selectById(42L)).thenReturn(memory("心理状态", "自述已好转，状态稳定", "active"));
        when(memoryMapper.selectList(any())).thenReturn(List.of(
                memory("心理状态", "情绪低落，有自伤念头", "superseded"),
                memory("心理状态", "自述已好转，状态稳定", "active")));

        int removed = orchestrator.purgeMemory(7L, 42L);

        assertEquals(2, removed);
        verify(memoryMapper).delete(any());
    }

    @Test
    void deletingSomebodyElsesMemoryIsRefused() {
        // 物理删除没有回头路，归属校验必须在这之前拦住
        UserProfileMemoryEntity other = memory("心理状态", "别人的记忆", "active");
        other.setUserId(99L);
        when(memoryMapper.selectById(42L)).thenReturn(other);

        assertThrows(ResponseStatusException.class, () -> orchestrator.purgeMemory(7L, 42L));
        verify(memoryMapper, never()).delete(any());
    }

    @Test
    void purgeOfAKeyThatIsNotThereTouchesNothing() {
        when(memoryMapper.selectList(any())).thenReturn(List.of());

        int removed = orchestrator.purgeByKey(7L, "心理状态");

        assertEquals(0, removed);
        verify(memoryMapper, never()).delete(any());
        verify(rejectionMapper, never()).insert(any(UserMemoryRejectionEntity.class));
    }

    @Test
    void theSealRecordsTheKeyButNotTheContent() {
        // 用户要的是「彻底删掉」。墓碑表里再留一份被删的值（普通 addRejection 就是这么干的）
        // 等于删了个寂寞 —— 封印只该回答「这个键还要不要再自动推导」。
        when(memoryMapper.selectList(any())).thenReturn(List.of(
                memory("心理状态", "情绪低落，有自伤念头", "active")));

        orchestrator.purgeByKey(7L, "心理状态");

        ArgumentCaptor<UserMemoryRejectionEntity> seal = ArgumentCaptor.forClass(UserMemoryRejectionEntity.class);
        verify(rejectionMapper).insert(seal.capture());
        assertEquals("USER_PURGED_KEY", seal.getValue().getRejectionType());
        assertEquals("心理状态", seal.getValue().getNormalizedKey());
        String stored = seal.getValue().getNormalizedValue();
        assertTrue(!stored.contains("情绪低落") && !stored.contains("自伤"),
                "封印里不该出现被删的内容，实际存了：" + stored);
    }

    @Test
    void aSealedKeyIsNotRebuiltByAutomaticExtraction() {
        sealOnlyTheKeySealQuery();
        when(candidateMapper.selectOne(any())).thenReturn(null);
        when(evidenceMapper.selectCount(any())).thenReturn(0L);
        when(evidenceMapper.selectList(any())).thenReturn(List.of());
        when(memoryMapper.selectList(any())).thenReturn(List.of());

        orchestrator.processExtractedMemories(7L,
                List.of(new MemoryExtractionService.MemoryAttribute("讨厌的食物", "番茄", false,
                        "preference", "inferred", .95, "我还是受不了番茄", null, null)),
                "diary_inferred", 12L, null, "今天又吃到番茄，我还是受不了番茄那个味道", null);

        verify(candidateMapper, never()).insert(any(UserMemoryCandidateEntity.class));
    }

    @Test
    void aSealedKeyIsStillWrittenWhenTheUserAsksForIt() {
        // 封印只拦自动抽取。用户亲口说「记一下」走的是 explicit 分支，在封印判断之前，
        // 所以必须照样写进去 —— 这正是产品文案承诺的「你之后明确表达新的事实时，仍可重新建立」。
        sealOnlyTheKeySealQuery();
        when(memoryMapper.selectOne(any())).thenReturn(null);
        when(memoryMapper.selectList(any())).thenReturn(List.of());

        orchestrator.processExtractedMemories(7L,
                List.of(new MemoryExtractionService.MemoryAttribute("讨厌的食物", "番茄", false,
                        "preference", "explicit", 1.0, "我讨厌吃番茄", null, null)),
                "explicit", null, 33L, "我讨厌吃番茄", null);

        verify(memoryMapper).insert(any(UserProfileMemoryEntity.class));
    }
}
