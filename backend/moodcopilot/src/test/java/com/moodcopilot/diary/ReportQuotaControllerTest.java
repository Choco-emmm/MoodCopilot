package com.moodcopilot.diary;

import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.security.RateLimitService;
import com.moodcopilot.common.RateLimitException;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ReportQuotaControllerTest {
    DiaryService diaries = mock(DiaryService.class);
    RateLimitService quotas = mock(RateLimitService.class);
    UserEntity user = new UserEntity();
    @BeforeEach void login() {
        user.setId(7L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void deniedQuotaNeverStartsEitherReport() {
        doThrow(new RateLimitException("REPORT", "额度不足")).when(quotas).tryAcquire(user, RateLimitService.AiApiType.REPORT);
        var controller = new DiaryController(diaries, quotas);
        assertThrows(RateLimitException.class, () -> controller.generateWeeklyReport(0));
        assertThrows(RateLimitException.class, () -> controller.generateMonthlyReport(-1));
        verifyNoInteractions(diaries);
    }
    @Test void bothReportsAcquireBeforeSchedulingAndKeepOffsets() {
        var controller = new DiaryController(diaries, quotas);
        controller.generateWeeklyReport(-2);
        controller.generateMonthlyReport(-1);
        var order = inOrder(quotas, diaries);
        order.verify(quotas).tryAcquire(user, RateLimitService.AiApiType.REPORT);
        order.verify(diaries).generateWeeklyAiSummaryAsync(7L, -2);
        order.verify(diaries).loadWeeklyReportForUser(7L, -2);
        order.verify(quotas).tryAcquire(user, RateLimitService.AiApiType.REPORT);
        order.verify(diaries).generateMonthlyAiSummaryAsync(7L, -1);
    }
}
