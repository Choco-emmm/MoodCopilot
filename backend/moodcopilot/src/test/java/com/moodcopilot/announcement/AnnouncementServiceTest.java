package com.moodcopilot.announcement;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.moodcopilot.entity.SystemAnnouncementEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.SystemAnnouncementMapper;
import com.moodcopilot.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    private final SystemAnnouncementMapper announcementMapper = mock(SystemAnnouncementMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final AnnouncementService service = new AnnouncementService(announcementMapper, userMapper);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeReturnsNullWhenNothingHasBeenPublished() {
        when(announcementMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertNull(service.active());
    }

    @Test
    void nonAdminCannotReadAdminCurrentOrPublish() {
        authenticate("USER");

        assertThrows(ResponseStatusException.class, service::currentForAdmin);
        assertThrows(ResponseStatusException.class,
                () -> service.publish(new PublishAnnouncementRequest("标题", "正文")));
    }

    @Test
    void publishCreatesNextVersionAndMakesItActive() {
        authenticate("ADMIN");
        SystemAnnouncementEntity current = new SystemAnnouncementEntity();
        current.setVersion(4L);
        when(announcementMapper.selectOne(any(Wrapper.class))).thenReturn(current);

        AnnouncementView published = service.publish(new PublishAnnouncementRequest("新公告", "第一行\n第二行"));

        ArgumentCaptor<SystemAnnouncementEntity> captor = ArgumentCaptor.forClass(SystemAnnouncementEntity.class);
        verify(announcementMapper).insert(captor.capture());
        assertEquals(5L, captor.getValue().getVersion());
        assertEquals("新公告", captor.getValue().getTitle());
        assertEquals("第一行\n第二行", captor.getValue().getContent());
        assertEquals(5L, published.version());
    }

    @Test
    void publishRejectsBlankOrOverlongContent() {
        authenticate("ADMIN");

        assertThrows(ResponseStatusException.class,
                () -> service.publish(new PublishAnnouncementRequest(" ", "正文")));
        assertThrows(ResponseStatusException.class,
                () -> service.publish(new PublishAnnouncementRequest("标题", "x".repeat(2001))));
    }

    private void authenticate(String role) {
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setDisplayName("管理员");
        user.setRole(role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
    }
}
