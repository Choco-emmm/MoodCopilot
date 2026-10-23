package com.moodcopilot.ai;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.*;
import com.moodcopilot.mapper.*;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real SQL, real MyBatis and Spring transactions; only the external vector service is mocked. */
class MemoryApprovalTransactionTest {
    DriverManagerDataSource database;
    JdbcTemplate jdbc;
    SqlSessionTemplate session;
    MemoryOrchestrator service;
    MemoryChangeCommandMapper commands;
    RagMemoryService rag;

    @BeforeEach void setup() throws Exception {
        database = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO users VALUES(7),(8)");
        // Existing model schema is only fixture setup; the new receipt DDL runs from the actual migration.
        table(UserProfileMemoryEntity.class, "user_profile_memory");
        table(UserMemoryCandidateEntity.class, "user_memory_candidates");
        table(UserMemoryEvidenceEntity.class, "user_memory_evidence");
        table(UserMemoryRejectionEntity.class, "user_memory_rejections");
        jdbc.execute("ALTER TABLE user_profile_memory ADD active_key VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN status='active' THEN attribute_key ELSE NULL END)");
        jdbc.execute("CREATE UNIQUE INDEX one_active ON user_profile_memory(user_id,active_key)");
        jdbc.execute("ALTER TABLE user_memory_evidence ADD FOREIGN KEY(memory_id) REFERENCES user_profile_memory(id) ON DELETE CASCADE");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1_63__add_memory_change_commands.sql")).execute(database);
        var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        for (var type : List.of(UserProfileMemoryMapper.class, UserMemoryCandidateMapper.class, UserMemoryEvidenceMapper.class,
                UserMemoryRejectionMapper.class, MemoryChangeCommandMapper.class)) configuration.addMapper(type);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(database); factory.setConfiguration(configuration);
        session = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        commands = session.getMapper(MemoryChangeCommandMapper.class);
        rag = mock(RagMemoryService.class);
        service = transactional(commands);
    }

    @AfterEach void cleanup() { jdbc.execute("DROP ALL OBJECTS"); }

    MemoryOrchestrator transactional(MemoryChangeCommandMapper mapper) {
        var target = new MemoryOrchestrator(session.getMapper(UserProfileMemoryMapper.class), session.getMapper(UserMemoryCandidateMapper.class),
                session.getMapper(UserMemoryEvidenceMapper.class), session.getMapper(UserMemoryRejectionMapper.class), rag, new ObjectMapper());
        target.setCommandMapper(mapper);
        var advice = new TransactionInterceptor(); advice.setTransactionManager(new DataSourceTransactionManager(database));
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true); proxy.addAdvice(advice);
        return (MemoryOrchestrator) proxy.getProxy();
    }

    long original() {
        return service.createConfirmed(7L, "饮食偏好", "喜欢苹果", "preference", "我喜欢苹果", "我喜欢苹果", 32L).memoryId();
    }

    void prepare(String run, String call, long id) {
        service.prepareUpdate(7L, run, call, id, "喜欢苹果", "喜欢香蕉", "我现在喜欢香蕉", "我现在喜欢香蕉");
    }

    long pendingCandidate() {
        service.processExtractedMemories(7L, List.of(MemoryWriteContractTest.attribute("运动偏好", "喜欢跑步", "inferred", "喜欢跑步")),
                "chat_candidate", null, 32L, "我喜欢跑步", null);
        return service.listCandidates(7L, "PENDING").get(0).getId();
    }

    @Test void aStaleRejectionCannotReverseAnApprovedCandidateOrWriteARejection() {
        long id = pendingCandidate();
        service.approveCandidate(7L, id);
        var conflict = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.rejectCandidate(7L, id, "stale dialog"));
        assertEquals(409, conflict.getStatusCode().value());
        assertEquals("APPROVED", service.listCandidates(7L, "APPROVED").get(0).getStatus());
        assertEquals("喜欢跑步", service.current(7L).get(0).getAttributeValue());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_memory_rejections", Integer.class));
    }

    @Test void aRepeatedRejectionIsIdempotentAndCannotBeApprovedLater() {
        long id = pendingCandidate();
        service.rejectCandidate(7L, id);
        service.rejectCandidate(7L, id, "duplicate delivery");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_memory_rejections", Integer.class));
        var conflict = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.approveCandidate(7L, id));
        assertEquals(409, conflict.getStatusCode().value());
        assertTrue(service.current(7L).isEmpty());
    }

    @Test void competingCandidateDecisionsHaveOnlyOneWinningOutcome() throws Exception {
        long id = pendingCandidate();
        var barrier = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var approval = workers.submit(() -> {
                barrier.await();
                try { service.approveCandidate(7L, id); return "APPROVED"; }
                catch (org.springframework.web.server.ResponseStatusException e) { assertEquals(409, e.getStatusCode().value()); return "CONFLICT"; }
            });
            var rejection = workers.submit(() -> {
                barrier.await();
                try { service.rejectCandidate(7L, id); return "REJECTED"; }
                catch (org.springframework.web.server.ResponseStatusException e) { assertEquals(409, e.getStatusCode().value()); return "CONFLICT"; }
            });
            var outcomes = List.of(approval.get(10, TimeUnit.SECONDS), rejection.get(10, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("CONFLICT"::equals).count());
            boolean approved = outcomes.contains("APPROVED");
            assertEquals(approved ? 1 : 0, service.current(7L).size());
            assertEquals(approved ? 0 : 1, jdbc.queryForObject("SELECT COUNT(*) FROM user_memory_rejections", Integer.class));
        }
    }

    @Test void editedApprovalCreatesOneVersionAndReplaysItsDurableReceipt() {
        long old = original(); prepare("run-a", "call-a", old);
        var first = service.applyUpdate(7L, "run-a", "call-a", old, "喜欢梨", 32L);
        assertEquals("APPLIED", first.status());
        assertEquals("喜欢梨", service.detail(7L, first.memoryId()).getAttributeValue());
        assertEquals(old, service.detail(7L, first.memoryId()).getPreviousMemoryId());
        assertEquals(first, transactional(commands).applyUpdate(7L, "run-a", "call-a", old, "喜欢梨", 32L));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM user_profile_memory", Integer.class));
        assertEquals("CALL_CONTENT_CHANGED", service.applyUpdate(7L, "run-a", "call-a", old, "喜欢葡萄", 32L).reasonCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_memory_evidence WHERE source_type='USER_ACTION'", Integer.class));
        assertFalse(jdbc.queryForMap("SELECT * FROM memory_change_commands").toString().contains("喜欢梨"));
    }

    @Test void mergePurgesOtherSourceKeysAndTheirApprovalHashesButKeepsTargetHistory() {
        long target = original();
        long source = service.createConfirmed(7L, "休闲偏好", "喜欢散步", "habit", "喜欢散步", "我喜欢散步", 32L).memoryId();
        service.prepareUpdate(7L, "source-run", "source-call", source, "喜欢散步", "喜欢游泳", "喜欢游泳", "我喜欢游泳");
        var merged = service.mergeMemoriesByIds(7L, List.of(target, source), "饮食偏好", "喜欢苹果，也喜欢散步", "pattern", true, "确认合并", 32L);
        assertEquals("preference", merged.getMemoryType());
        assertEquals(target, merged.getPreviousMemoryId());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_profile_memory WHERE attribute_key='休闲偏好'", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM user_profile_memory", Integer.class));
        var receipt = jdbc.queryForMap("SELECT * FROM memory_change_commands");
        assertNull(receipt.get("PROPOSED_VALUE_HASH"));
        assertNull(receipt.get("TARGET_MEMORY_ID"));
        assertEquals("REJECTED", receipt.get("STATUS"));
    }

    @Test void aReceiptWriteFailureRollsBackMemoryAndEvidenceTogether() {
        long old = original(); prepare("run-a", "call-a", old);
        var failing = spy(commands);
        doThrow(new IllegalStateException("synthetic receipt failure")).when(failing).updateById(any(MemoryChangeCommandEntity.class));
        assertThrows(IllegalStateException.class, () -> transactional(failing).applyUpdate(7L, "run-a", "call-a", old, "喜欢香蕉", 32L));
        assertEquals("喜欢苹果", service.detail(7L, old).getAttributeValue());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_profile_memory", Integer.class));
        assertEquals("PREPARED", jdbc.queryForObject("SELECT status FROM memory_change_commands", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_memory_evidence WHERE source_type='USER_ACTION'", Integer.class));
    }

    @Test void competingApprovalsCannotOverwriteTheFirstCommittedVersion() throws Exception {
        long old = original(); prepare("run-a", "call-a", old); prepare("run-b", "call-b", old);
        var barrier = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> { barrier.await(); return service.applyUpdate(7L, "run-a", "call-a", old, "喜欢香蕉", 32L); });
            var b = workers.submit(() -> { barrier.await(); return service.applyUpdate(7L, "run-b", "call-b", old, "喜欢梨", 32L); });
            assertEquals(Set.of("APPLIED", "STALE"), Set.of(a.get(10,TimeUnit.SECONDS).status(), b.get(10,TimeUnit.SECONDS).status()));
        }
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM user_profile_memory", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_profile_memory WHERE status='active'", Integer.class));
    }

    @Test void purgeRemovesHashesAndOldApprovalsCannotRecreateTheMemory() {
        long old = original(); prepare("run-a", "call-a", old);
        service.applyUpdate(7L, "run-a", "call-a", old, "喜欢香蕉", 32L);
        service.purgeByKey(7L, "饮食偏好");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_profile_memory", Integer.class));
        var row = jdbc.queryForMap("SELECT * FROM memory_change_commands");
        assertNull(row.get("APPROVED_VALUE_HASH")); assertNull(row.get("ATTRIBUTE_KEY"));
        assertFalse(service.applyUpdate(7L, "run-a", "call-a", old, "喜欢香蕉", 32L).success());
    }

    @Test void aDeclinedCommandRemainsTerminalAfterRestart() {
        long old = original(); prepare("run-a", "call-a", old);
        service.rejectUpdate(7L, "run-a", "call-a", "USER_DECLINED");
        assertFalse(transactional(commands).applyUpdate(7L, "run-a", "call-a", old, "喜欢香蕉", 32L).success());
        assertEquals("USER_DECLINED", jdbc.queryForObject("SELECT reason_code FROM memory_change_commands", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_profile_memory", Integer.class));
    }

    @Test void noOpDoesNotCreateAVersionOrAppendSyntheticEvidence() {
        long old = original();
        service.prepareUpdate(7L, "run-a", "call-a", old, "喜欢苹果", "喜欢苹果", "仍喜欢苹果", "我仍喜欢苹果");
        var result = service.applyUpdate(7L, "run-a", "call-a", old, "喜欢苹果", 32L);
        assertEquals("UNCHANGED", result.status()); assertFalse(result.executed());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_profile_memory", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_memory_evidence WHERE source_type='USER_ACTION'", Integer.class));
    }

    @Test void aPostCommitIndexFailureKeepsTheSavedReceiptAndRetriesTheIndex() {
        long old = original(); prepare("run-a", "call-a", old);
        doThrow(new IllegalStateException("fixture index offline")).when(rag).indexUserProfileAsync(anyLong(), anyList(), anyLong());
        var result = service.applyUpdate(7L, "run-a", "call-a", old, "喜欢香蕉", 32L);
        assertEquals("APPLIED", result.status());
        assertEquals("喜欢香蕉", service.detail(7L, result.memoryId()).getAttributeValue());
        doThrow(new IllegalStateException("fixture index offline")).when(rag).indexUserProfile(anyLong(), anyList(), anyLong());
        service.retryPendingChangeIndexes();
        assertTrue(jdbc.queryForObject("SELECT index_pending FROM memory_change_commands", Boolean.class));
        reset(rag); service.retryPendingChangeIndexes();
        assertFalse(jdbc.queryForObject("SELECT index_pending FROM memory_change_commands", Boolean.class));
    }

    @Test void confirmedReplacementKeepsLegacyIdentityAndItsVersionChain() {
        long old = original();
        jdbc.update("UPDATE user_profile_memory SET attribute_key='legacy_key',memory_type='other' WHERE id=?", old);
        service.replaceWithUserAction(7L, List.of(new MemoryExtractionService.MemoryAttribute(
                "legacy_key", "喜欢梨", false, "other", "explicit", 1D, null, null, null)));
        var active = service.current(7L).get(0);
        assertEquals("legacy_key", active.getAttributeKey()); assertEquals("other", active.getMemoryType());
        assertEquals(old, active.getPreviousMemoryId()); assertEquals("喜欢梨", active.getAttributeValue());
    }

    @Test void automaticCandidatesKeepSymbolBearingValuesAndHonorRejectionFingerprints() {
        service.processExtractedMemories(7L, List.of(
                MemoryWriteContractTest.attribute("编程偏好", "C++", "inferred", "C++"),
                MemoryWriteContractTest.attribute("编程偏好", "C#", "inferred", "C#")),
                "chat_candidate", null, 32L, "C++和C#都挺有意思", null);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM user_memory_candidates", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(DISTINCT normalized_value) FROM user_memory_candidates", Integer.class));
        assertTrue(jdbc.queryForList("SELECT normalized_value FROM user_memory_candidates", String.class).stream().allMatch(value -> value.startsWith("v2:")));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_profile_memory", Integer.class));
        long candidate = jdbc.queryForObject("SELECT id FROM user_memory_candidates WHERE attribute_value='C++'", Long.class);
        service.rejectCandidate(7L, candidate, "fixture reason");
        service.processExtractedMemories(7L, List.of(MemoryWriteContractTest.attribute("编程偏好", "C ++", "explicit", "C ++")),
                "chat_candidate", null, 32L, "我偏好C ++", null);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM user_memory_candidates", Integer.class));
        assertTrue(jdbc.queryForObject("SELECT normalized_value FROM user_memory_rejections", String.class).startsWith("v2:"));
    }

    @Test void opaqueToolCallIdsKeepTheirCaseSensitiveIdentity() {
        long old = original();
        prepare("run-a", "call-a", old); prepare("run-a", "call-A", old);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM memory_change_commands", Integer.class));
    }

    @Test void invalidEditsAndForeignTargetsNeverConsumeTheProposal() {
        long old = original(); prepare("run-a", "call-a", old);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.validateUpdateDecision(7L, "run-a", "call-a", "someone@example.com"));
        assertEquals("PREPARED", jdbc.queryForObject("SELECT status FROM memory_change_commands", String.class));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.prepareUpdate(8L, "run-x", "call-x", old, "喜欢苹果", "喜欢梨", "喜欢梨", "喜欢梨"));
    }

    void table(Class<?> entity, String name) {
        var columns = new ArrayList<String>();
        for (var field : entity.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            String column = field.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
            Class<?> type = field.getType();
            columns.add(column + " " + (column.equals("id") ? "BIGINT AUTO_INCREMENT PRIMARY KEY" : type == Long.class ? "BIGINT"
                    : type == Boolean.class ? "BOOLEAN" : type == Double.class ? "DOUBLE" : type == LocalDate.class ? "DATE"
                    : type == LocalDateTime.class ? "TIMESTAMP" : "VARCHAR(2000)"));
        }
        jdbc.execute("CREATE TABLE " + name + " (" + String.join(",", columns) + ")");
    }
}
