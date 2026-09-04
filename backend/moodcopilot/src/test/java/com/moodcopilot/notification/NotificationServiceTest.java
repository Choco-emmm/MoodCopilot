package com.moodcopilot.notification;

import com.moodcopilot.entity.NotificationEntity;
import com.moodcopilot.mapper.NotificationMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationServiceTest {

    @Test
    void websocketFailureDoesNotTurnPersistedNotificationIntoRetry() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        NotificationWebSocketHandler websocket = mock(NotificationWebSocketHandler.class);
        doThrow(new IllegalStateException("socket unavailable"))
                .when(websocket).pushNotification(any(), any(NotificationEntity.class));

        NotificationService service = new NotificationService(mapper, websocket);

        assertTrue(service.notifyDailyFollowUp(7L, "回访"));
        verify(mapper).insert(any(NotificationEntity.class));
    }

    @Test
    void persistenceFailureIsReportedToScheduler() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        NotificationWebSocketHandler websocket = mock(NotificationWebSocketHandler.class);
        doThrow(new IllegalStateException("database unavailable"))
                .when(mapper).insert(any(NotificationEntity.class));

        NotificationService service = new NotificationService(mapper, websocket);

        assertFalse(service.notifyDailyFollowUp(7L, "回访"));
    }
}
