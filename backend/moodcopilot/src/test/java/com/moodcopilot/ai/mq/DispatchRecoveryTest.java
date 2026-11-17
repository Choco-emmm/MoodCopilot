package com.moodcopilot.ai.mq;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class DispatchRecoveryTest {
    @Test void expiredAnalysisCannotReplaceTheUsersOptOutWithAFailure() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), AiTaskEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), com.moodcopilot.entity.DiaryEntity.class);
        var tasks = mock(AiTaskMapper.class);
        var diaries = mock(com.moodcopilot.mapper.DiaryMapper.class);
        var task = new AiTaskEntity();
        task.setTaskId("expired-analysis"); task.setLeaseOwner("old-worker");
        task.setUserId(7L); task.setAggregateId("31"); task.setTaskType(AiTaskMessage.TYPE_DIARY_ANALYSIS);
        task.setStatus("RUNNING"); task.setStartedAt(LocalDateTime.now().minusMinutes(30));
        task.setLeaseUntil(LocalDateTime.now().minusMinutes(1)); task.setAttempts(2); task.setMaxAttempts(3);
        when(tasks.selectList(any())).thenReturn(List.of(task));
        when(tasks.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        new AiTaskService(tasks, diaries, mock(RabbitTemplate.class), new ObjectMapper()).recoverExpiredTasks();
        @SuppressWarnings("unchecked") var guard = org.mockito.ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(diaries).update(any(com.moodcopilot.entity.DiaryEntity.class), guard.capture());
        assertTrue(guard.getValue().getSqlSegment().contains("analysis_status <>"));
        assertTrue(guard.getValue().getParamNameValuePairs().containsValue("skipped_user"));
        assertTrue(guard.getValue().getParamNameValuePairs().containsValue(7L));
        verify(diaries, never()).updateById(any(com.moodcopilot.entity.DiaryEntity.class));
    }

    @Test void expiredForeignDispatchUsesObservedOwnerAndExpirationGuard() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), AiTaskEntity.class);
        var mapper = mock(AiTaskMapper.class);
        var task = new AiTaskEntity();
        task.setTaskId("old-dispatch"); task.setLeaseOwner("previous-process");
        task.setStatus("RUNNING"); task.setLeaseUntil(LocalDateTime.now().minusMinutes(1));
        task.setAttempts(0); task.setMaxAttempts(3);
        when(mapper.selectList(any())).thenReturn(List.of(task));
        when(mapper.selectById("old-dispatch")).thenReturn(task);
        List<LambdaUpdateWrapper<AiTaskEntity>> updates = new ArrayList<>();
        when(mapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenAnswer(call -> {
            updates.add(call.getArgument(1)); return 1;
        });
        new AiTaskService(mapper, null, mock(RabbitTemplate.class), new ObjectMapper()).recoverExpiredTasks();
        assertEquals(1, updates.size());
        var update = updates.get(0);
        String sql = update.getSqlSegment();
        assertTrue(update.getParamNameValuePairs().containsValue("previous-process"));
        assertTrue(sql.contains("lease_until") && sql.contains("<="), sql);
        assertTrue(sql.contains("started_at IS NULL"), sql);
    }
}
