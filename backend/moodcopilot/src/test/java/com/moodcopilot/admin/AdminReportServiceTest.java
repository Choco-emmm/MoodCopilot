package com.moodcopilot.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.moodcopilot.entity.DiaryCommentEntity;
import com.moodcopilot.entity.DiaryEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.entity.UserReportEntity;
import com.moodcopilot.mapper.DiaryCommentMapper;
import com.moodcopilot.mapper.DiaryMapper;
import com.moodcopilot.mapper.UserReportMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReportServiceTest {

    @Mock
    private UserReportMapper userReportMapper;
    @Mock
    private DiaryMapper diaryMapper;
    @Mock
    private DiaryCommentMapper diaryCommentMapper;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ordinaryUserCannotListReports() {
        loginAs("USER");
        AdminReportService service = new AdminReportService(userReportMapper, diaryMapper, diaryCommentMapper);

        assertThatThrownBy(() -> service.list("PENDING", 1, 20))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void adminCanRejectReport() {
        loginAs("ADMIN");
        UserReportEntity report = report("DIARY", 7L);
        when(userReportMapper.selectById(1L)).thenReturn(report);
        AdminReportService service = new AdminReportService(userReportMapper, diaryMapper, diaryCommentMapper);

        service.reject(1L, "不是违规内容");

        ArgumentCaptor<UserReportEntity> captor = ArgumentCaptor.forClass(UserReportEntity.class);
        verify(userReportMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("REJECTED");
        assertThat(captor.getValue().getHandledByUserId()).isEqualTo(99L);
        assertThat(captor.getValue().getHandleNote()).isEqualTo("不是违规内容");
    }

    @Test
    void adminHideTargetDeletesDiaryAndResolvesReport() {
        loginAs("ADMIN");
        UserReportEntity report = report("DIARY", 7L);
        when(userReportMapper.selectById(1L)).thenReturn(report);
        AdminReportService service = new AdminReportService(userReportMapper, diaryMapper, diaryCommentMapper);

        service.hideTarget(1L, "公开内容违规");

        verify(diaryMapper).deleteById(7L);
        ArgumentCaptor<UserReportEntity> captor = ArgumentCaptor.forClass(UserReportEntity.class);
        verify(userReportMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("RESOLVED");
        assertThat(captor.getValue().getHandleNote()).isEqualTo("公开内容违规");
    }

    @Test
    void adminHideTargetDeletesCommentAndResolvesReport() {
        loginAs("ADMIN");
        UserReportEntity report = report("COMMENT", 8L);
        when(userReportMapper.selectById(1L)).thenReturn(report);
        AdminReportService service = new AdminReportService(userReportMapper, diaryMapper, diaryCommentMapper);

        service.hideTarget(1L, "评论违规");

        verify(diaryCommentMapper).deleteById(8L);
        ArgumentCaptor<UserReportEntity> captor = ArgumentCaptor.forClass(UserReportEntity.class);
        verify(userReportMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("RESOLVED");
    }

    private void loginAs(String role) {
        UserEntity user = new UserEntity();
        user.setId(99L);
        user.setEmail("admin@moodcopilot.local");
        user.setDisplayName("管理员");
        user.setRole(role);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null)
        );
    }

    private UserReportEntity report(String targetType, long targetId) {
        UserReportEntity report = new UserReportEntity();
        report.setId(1L);
        report.setReporterUserId(2L);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReason("测试举报");
        report.setStatus("PENDING");
        return report;
    }
}
