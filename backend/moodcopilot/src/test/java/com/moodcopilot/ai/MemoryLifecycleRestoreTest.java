package com.moodcopilot.ai;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.UserMemoryEvidenceEntity;
import com.moodcopilot.entity.UserProfileMemoryEntity;
import com.moodcopilot.entity.UserMemoryCandidateEntity;
import com.moodcopilot.mapper.UserMemoryCandidateMapper;
import com.moodcopilot.mapper.UserMemoryEvidenceMapper;
import com.moodcopilot.mapper.UserMemoryRejectionMapper;
import com.moodcopilot.mapper.UserProfileMemoryMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 恢复已归档记忆的关键约束：必须同时重启衰减时钟，否则下一次生命周期任务
 * 会用旧的 {@code last_evidence_at} 立刻把它重新归档，用户会看到「点了恢复又变回去」。
 */
class MemoryLifecycleRestoreTest {

    @BeforeAll
    static void initializeLambdaMetadataForMockedMapperPaths() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "memory-lifecycle-restore-test");
        TableInfoHelper.initTableInfo(assistant, UserProfileMemoryEntity.class);
        TableInfoHelper.initTableInfo(assistant, UserMemoryEvidenceEntity.class);
        TableInfoHelper.initTableInfo(assistant, UserMemoryCandidateEntity.class);
    }

    private static UserProfileMemoryEntity memory(String lifecycleState) {
        UserProfileMemoryEntity memory = new UserProfileMemoryEntity();
        memory.setId(11L);
        memory.setUserId(7L);
        memory.setAttributeKey("喜欢咖啡");
        memory.setAttributeValue("喜欢咖啡");
        memory.setMemoryType("preference");
        memory.setSourceType("diary_inferred");
        memory.setStatus("active");
        memory.setConfidence(0.5);
        memory.setLifecycleState(lifecycleState);
        memory.setFreshness(0.16);
        return memory;
    }

    private static MemoryOrchestrator orchestrator(UserProfileMemoryMapper memoryMapper,
            UserMemoryEvidenceMapper evidenceMapper) {
        return new MemoryOrchestrator(memoryMapper, mock(UserMemoryCandidateMapper.class), evidenceMapper,
                mock(UserMemoryRejectionMapper.class), mock(RagMemoryService.class), new ObjectMapper());
    }

    @Test
    void restoreResetsDecayClockAndRecordsUserAction() {
        UserProfileMemoryMapper memoryMapper = mock(UserProfileMemoryMapper.class);
        UserMemoryEvidenceMapper evidenceMapper = mock(UserMemoryEvidenceMapper.class);
        when(memoryMapper.selectById(11L)).thenReturn(memory("archived"));
        when(memoryMapper.update(isNull(), any())).thenReturn(1);
        when(evidenceMapper.selectCount(any())).thenReturn(0L);

        orchestrator(memoryMapper, evidenceMapper).restoreLifecycle(7L, 11L);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Wrapper<UserProfileMemoryEntity>> captor =
                org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        verify(memoryMapper).update(isNull(), captor.capture());
        @SuppressWarnings("unchecked")
        LambdaUpdateWrapper<UserProfileMemoryEntity> wrapper =
                (LambdaUpdateWrapper<UserProfileMemoryEntity>) captor.getValue();
        String sqlSet = wrapper.getSqlSet();
        assertTrue(sqlSet.contains("lifecycle_state"), sqlSet);
        assertTrue(sqlSet.contains("freshness"), sqlSet);
        assertTrue(sqlSet.contains("last_evidence_at"), "必须重启衰减时钟: " + sqlSet);
        verify(evidenceMapper).insert(any(UserMemoryEvidenceEntity.class));
    }

    @Test
    void restoringAnActiveMemoryIsANoOp() {
        UserProfileMemoryMapper memoryMapper = mock(UserProfileMemoryMapper.class);
        UserMemoryEvidenceMapper evidenceMapper = mock(UserMemoryEvidenceMapper.class);
        when(memoryMapper.selectById(11L)).thenReturn(memory("active"));

        orchestrator(memoryMapper, evidenceMapper).restoreLifecycle(7L, 11L);

        verify(memoryMapper, never()).update(any(), any());
        verifyNoInteractions(evidenceMapper);
    }
}
