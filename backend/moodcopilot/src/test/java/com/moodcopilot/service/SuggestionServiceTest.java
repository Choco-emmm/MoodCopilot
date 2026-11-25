package com.moodcopilot.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moodcopilot.dto.CreateSuggestionReplyRequest;
import com.moodcopilot.dto.SuggestionDTO;
import com.moodcopilot.dto.SuggestionReplyDTO;
import com.moodcopilot.dto.SuggestionRequest;
import com.moodcopilot.entity.SuggestionEntity;
import com.moodcopilot.entity.SuggestionReplyEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.SuggestionMapper;
import com.moodcopilot.mapper.SuggestionReplyMapper;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.notification.NotificationService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SuggestionServiceTest {

    private SuggestionMapper suggestionMapper;
    private SuggestionReplyMapper suggestionReplyMapper;
    private UserMapper userMapper;
    private JavaMailSender javaMailSender;
    private NotificationService notificationService;
    private SuggestionService suggestionService;

    private UserEntity userA;
    private UserEntity userB;
    private UserEntity admin;

    @BeforeEach
    void setUp() {
        suggestionMapper = mock(SuggestionMapper.class);
        suggestionReplyMapper = mock(SuggestionReplyMapper.class);
        userMapper = mock(UserMapper.class);
        javaMailSender = mock(JavaMailSender.class);
        notificationService = mock(NotificationService.class);

        suggestionService = new SuggestionService(
                suggestionMapper,
                suggestionReplyMapper,
                userMapper,
                javaMailSender,
                notificationService
        );

        userA = buildUser(101L, "Alice", "USER");
        userB = buildUser(102L, "Bob", "USER");
        admin = buildUser(1L, "AdminBoss", "ADMIN");
        admin.setEmail("admin@moodcopilot.top");
        ReflectionTestUtils.setField(suggestionService, "mailFrom", "noreply@moodcopilot.top");
        when(javaMailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));

        when(userMapper.selectBatchIds(any())).thenAnswer(inv -> {
            Collection<?> ids = inv.getArgument(0);
            List<UserEntity> result = new ArrayList<>();
            for (Object id : ids) {
                if (Long.valueOf(101L).equals(id)) result.add(userA);
                if (Long.valueOf(102L).equals(id)) result.add(userB);
                if (Long.valueOf(1L).equals(id)) result.add(admin);
            }
            return result;
        });
        when(userMapper.selectList(any())).thenReturn(List.of(admin));
    }

    private UserEntity buildUser(Long id, String name, String role) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setDisplayName(name);
        user.setNickname(name);
        user.setRole(role);
        return user;
    }

    private SuggestionEntity buildSuggestion(Long id, Long ownerId, String content, String status) {
        SuggestionEntity entity = new SuggestionEntity();
        entity.setId(id);
        entity.setUserId(ownerId);
        entity.setContent(content);
        entity.setStatus(status);
        entity.setCreatedAt(LocalDateTime.of(2026, 11, 25, 10, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 11, 25, 10, 0));
        return entity;
    }

    @Test
    void regularUserCannotAccessAdminListOrUpdateStatus() {
        ResponseStatusException listEx = assertThrows(
                ResponseStatusException.class,
                () -> suggestionService.listAllSuggestions(userA, 1, 20, null)
        );
        assertEquals(HttpStatus.FORBIDDEN, listEx.getStatusCode());

        ResponseStatusException statusEx = assertThrows(
                ResponseStatusException.class,
                () -> suggestionService.updateStatus(userA, 10L, "RESOLVED")
        );
        assertEquals(HttpStatus.FORBIDDEN, statusEx.getStatusCode());
    }

    @Test
    void regularUserCannotViewOrReplyToOtherUsersSuggestion() {
        SuggestionEntity userBSuggestion = buildSuggestion(20L, userB.getId(), "Bob 的建议", "PENDING");
        when(suggestionMapper.selectById(20L)).thenReturn(userBSuggestion);

        ResponseStatusException getEx = assertThrows(
                ResponseStatusException.class,
                () -> suggestionService.getSuggestion(userA, 20L)
        );
        assertEquals(HttpStatus.FORBIDDEN, getEx.getStatusCode());

        ResponseStatusException replyEx = assertThrows(
                ResponseStatusException.class,
                () -> suggestionService.addReply(userA, 20L, new CreateSuggestionReplyRequest("偷看回复", null))
        );
        assertEquals(HttpStatus.FORBIDDEN, replyEx.getStatusCode());
    }

    @Test
    void adminReplyAndUserFollowUpFormTwoLevelTreeWithNotifications() {
        SuggestionEntity suggestion = buildSuggestion(10L, userA.getId(), "希望增加夜间字体大小调节", "PENDING");
        List<SuggestionReplyEntity> storedReplies = new ArrayList<>();
        AtomicLong idGen = new AtomicLong(500L);

        when(suggestionMapper.selectById(10L)).thenReturn(suggestion);
        when(suggestionReplyMapper.insert(any(SuggestionReplyEntity.class))).thenAnswer(inv -> {
            SuggestionReplyEntity r = inv.getArgument(0);
            r.setId(idGen.incrementAndGet());
            storedReplies.add(r);
            return 1;
        });
        when(suggestionReplyMapper.selectById(any())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return storedReplies.stream().filter(r -> id.equals(r.getId())).findFirst().orElse(null);
        });
        when(suggestionReplyMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(storedReplies));

        // 1. Admin replies to User A's suggestion
        SuggestionDTO afterAdminReply = suggestionService.addReply(
                admin, 10L, new CreateSuggestionReplyRequest("收到，下个版本安排上！", null)
        );

        assertEquals("READ", afterAdminReply.status());
        assertEquals(1, afterAdminReply.replyCount());
        assertEquals(1, afterAdminReply.replies().size());
        SuggestionReplyDTO adminReplyDto = afterAdminReply.replies().get(0);
        assertEquals("ADMIN", adminReplyDto.authorRole());
        assertNull(adminReplyDto.parentReplyId());
        assertNull(adminReplyDto.rootReplyId());
        verify(notificationService).notifyFeedbackReply(eq(admin), eq(10L), eq(userA.getId()), contains("管理员 AdminBoss"));
        verify(javaMailSender, never()).send(any(MimeMessage.class));

        // Mark resolved by admin
        suggestion.setStatus("RESOLVED");

        // 2. User A replies to Admin's reply
        SuggestionDTO afterUserReply = suggestionService.addReply(
                userA, 10L, new CreateSuggestionReplyRequest("太棒了，谢谢管理员！顺便问下大概什么时候发版？", adminReplyDto.id())
        );

        assertEquals("PENDING", afterUserReply.status());
        assertEquals(2, afterUserReply.replyCount());
        assertEquals(1, afterUserReply.replies().size());
        List<SuggestionReplyDTO> nestedReplies = afterUserReply.replies().get(0).replies();
        assertEquals(1, nestedReplies.size());
        SuggestionReplyDTO userReplyDto = nestedReplies.get(0);
        assertEquals("USER", userReplyDto.authorRole());
        assertEquals(adminReplyDto.id(), userReplyDto.parentReplyId());
        assertEquals(adminReplyDto.id(), userReplyDto.rootReplyId());
        assertEquals("AdminBoss", userReplyDto.replyToUserName());
        verify(notificationService).notifyFeedbackReply(eq(userA), eq(10L), eq(admin.getId()), contains("Alice 回复了意见反馈"));
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));

        // 3. Admin replies to User A's nested reply -> stays flattened under rootReplyId = adminReplyDto.id()
        SuggestionDTO afterThirdReply = suggestionService.addReply(
                admin, 10L, new CreateSuggestionReplyRequest("预计下周五更新~", userReplyDto.id())
        );

        assertEquals(3, afterThirdReply.replyCount());
        List<SuggestionReplyDTO> flattened = afterThirdReply.replies().get(0).replies();
        assertEquals(2, flattened.size());
        SuggestionReplyDTO thirdReplyDto = flattened.get(1);
        assertEquals(userReplyDto.id(), thirdReplyDto.parentReplyId());
        assertEquals(adminReplyDto.id(), thirdReplyDto.rootReplyId());
        assertEquals("Alice", thirdReplyDto.replyToUserName());
    }

    @Test
    void crossSuggestionParentReplyIsRejected() {
        SuggestionEntity suggestion10 = buildSuggestion(10L, userA.getId(), "A 的反馈", "PENDING");
        SuggestionReplyEntity replyOnOtherSuggestion = new SuggestionReplyEntity();
        replyOnOtherSuggestion.setId(999L);
        replyOnOtherSuggestion.setSuggestionId(99L);

        when(suggestionMapper.selectById(10L)).thenReturn(suggestion10);
        when(suggestionReplyMapper.selectById(999L)).thenReturn(replyOnOtherSuggestion);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> suggestionService.addReply(userA, 10L, new CreateSuggestionReplyRequest("跨贴回复", 999L))
        );
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void deleteReplyPermissionsAndOrphanPromotion() {
        SuggestionEntity suggestion = buildSuggestion(10L, userA.getId(), "反馈", "READ");
        SuggestionReplyEntity adminReply = new SuggestionReplyEntity();
        adminReply.setId(501L);
        adminReply.setSuggestionId(10L);
        adminReply.setAuthorUserId(admin.getId());
        adminReply.setAuthorName("AdminBoss");
        adminReply.setAuthorRole("ADMIN");
        adminReply.setContent("管理员回复");
        adminReply.setCreatedAt(LocalDateTime.of(2026, 11, 25, 10, 5));

        SuggestionReplyEntity userChild = new SuggestionReplyEntity();
        userChild.setId(502L);
        userChild.setSuggestionId(10L);
        userChild.setParentReplyId(501L);
        userChild.setRootReplyId(501L);
        userChild.setAuthorUserId(userA.getId());
        userChild.setAuthorName("Alice");
        userChild.setAuthorRole("USER");
        userChild.setContent("用户追问");
        userChild.setCreatedAt(LocalDateTime.of(2026, 11, 25, 10, 10));

        List<SuggestionReplyEntity> activeReplies = new ArrayList<>(List.of(adminReply, userChild));

        when(suggestionMapper.selectById(10L)).thenReturn(suggestion);
        when(suggestionReplyMapper.selectById(501L)).thenReturn(adminReply);
        when(suggestionReplyMapper.selectById(502L)).thenReturn(userChild);
        when(suggestionReplyMapper.deleteById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            activeReplies.removeIf(r -> id.equals(r.getId()));
            return 1;
        });
        when(suggestionReplyMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(activeReplies));

        // User A cannot delete Admin's reply
        ResponseStatusException forbidden = assertThrows(
                ResponseStatusException.class,
                () -> suggestionService.deleteReply(userA, 10L, 501L)
        );
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());

        // Admin deletes root reply -> User A's child reply is still visible (promoted, not hidden)
        SuggestionDTO afterAdminDelete = suggestionService.deleteReply(admin, 10L, 501L);
        assertEquals(1, afterAdminDelete.replyCount());
        assertEquals(1, afterAdminDelete.replies().size());
        assertEquals(502L, afterAdminDelete.replies().get(0).id());
        assertNull(afterAdminDelete.replies().get(0).replyToUserName());

        // User A can delete their own reply
        SuggestionDTO afterUserDelete = suggestionService.deleteReply(userA, 10L, 502L);
        assertEquals(0, afterUserDelete.replyCount());
        assertTrue(afterUserDelete.replies().isEmpty());
    }

    @Test
    void submitSuggestionAndListMySuggestionsWork() {
        when(suggestionMapper.insert(any(SuggestionEntity.class))).thenAnswer(inv -> {
            SuggestionEntity e = inv.getArgument(0);
            e.setId(77L);
            return 1;
        });

        SuggestionDTO created = suggestionService.submitSuggestion(userA, new SuggestionRequest("  希望支持导出 PDF  "));
        assertEquals(77L, created.id());
        assertEquals("PENDING", created.status());
        assertEquals("希望支持导出 PDF", created.content());
        assertEquals(0, created.replyCount());
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));

        SuggestionEntity saved = buildSuggestion(77L, userA.getId(), "希望支持导出 PDF", "PENDING");
        when(suggestionMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(inv -> {
            Page<SuggestionEntity> page = inv.getArgument(0);
            page.setRecords(List.of(saved));
            page.setTotal(1L);
            return page;
        });
        when(suggestionReplyMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> mine = suggestionService.listMySuggestions(userA, 1, 20);
        assertEquals(1L, mine.get("total"));
        @SuppressWarnings("unchecked")
        List<SuggestionDTO> items = (List<SuggestionDTO>) mine.get("items");
        assertEquals(1, items.size());
        assertEquals(77L, items.get(0).id());
    }
}
