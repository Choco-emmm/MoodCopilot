package com.moodcopilot.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodcopilot.entity.NotificationEntity;
import com.moodcopilot.entity.UserEntity;
import com.moodcopilot.mapper.UserMapper;
import com.moodcopilot.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.EOFException;
import java.net.URI;
import java.net.SocketException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketHandler.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    private final Map<Long, Set<WebSocketSession>> sessionsByUserId = new ConcurrentHashMap<>();
    private final Map<String, Long> userIdBySessionId = new ConcurrentHashMap<>();

    public NotificationWebSocketHandler(JwtTokenProvider jwtTokenProvider,
            UserMapper userMapper,
            ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = resolveToken(session);
        if (!StringUtils.hasText(token) || !jwtTokenProvider.validateToken(token)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid token"));
            return;
        }

        Long userId = jwtTokenProvider.getUserId(token);
        UserEntity user = userMapper.selectById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() != 1)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid user"));
            return;
        }

        sessionsByUserId.computeIfAbsent(userId, ignored -> new CopyOnWriteArraySet<>()).add(session);
        userIdBySessionId.put(session.getId(), userId);

        sendSafely(session, buildEnvelope("CONNECTED", Map.of(
                "serverTime", LocalDateTime.now().toString(),
                "userId", userId)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        if ("ping".equalsIgnoreCase(payload)) {
            sendSafely(session, buildEnvelope("PONG", Map.of("time", LocalDateTime.now().toString())));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String sessionId = session == null ? "unknown" : session.getId();
        if (isClientDisconnect(exception)) {
            log.debug("Notification websocket disconnected by client/network, sessionId={}, reason={}",
                    sessionId, rootMessage(exception));
        } else {
            log.warn("Notification websocket transport error, sessionId={}", sessionId, exception);
        }
        removeSession(session);
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException ignored) {
            // ignored
        }
    }

    public void pushNotification(Long userId, NotificationEntity notification) {
        Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
        if (sessions == null || sessions.isEmpty())
            return;

        String message = buildEnvelope("NOTIFICATION", notification);
        sessions.forEach(session -> sendSafely(session, message));
    }

    private String resolveToken(WebSocketSession session) {
        String header = session.getHandshakeHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }

        URI uri = session.getUri();
        if (uri == null)
            return null;
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("token");
    }

    private String buildEnvelope(String type, Object data) {
        try {
            return objectMapper.writeValueAsString(Map.of("type", type, "data", data));
        } catch (Exception e) {
            log.warn("Failed to serialize websocket payload", e);
            return "{\"type\":\"" + type + "\"}";
        }
    }

    private void sendSafely(WebSocketSession session, String payload) {
        if (session == null || !session.isOpen()) {
            removeSession(session);
            return;
        }
        try {
            session.sendMessage(new TextMessage(payload));
        } catch (IOException e) {
            if (isClientDisconnect(e)) {
                log.debug("Skip send to closed websocket sessionId={}, reason={}", session.getId(), rootMessage(e));
            } else {
                log.warn("Failed to send websocket message, sessionId={}", session.getId(), e);
            }
            removeSession(session);
        }
    }

    private boolean isClientDisconnect(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof EOFException) {
                return true;
            }
            if (current instanceof SocketException socketException) {
                String msg = socketException.getMessage();
                if (msg != null) {
                    String normalized = msg.toLowerCase();
                    if (normalized.contains("connection reset")
                            || normalized.contains("broken pipe")
                            || normalized.contains("forcibly closed")) {
                        return true;
                    }
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private String rootMessage(Throwable error) {
        if (error == null) {
            return "unknown";
        }
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String msg = current.getMessage();
        return (msg == null || msg.isBlank()) ? current.getClass().getSimpleName() : msg;
    }

    private void removeSession(WebSocketSession session) {
        if (session == null)
            return;

        Long userId = userIdBySessionId.remove(session.getId());
        if (userId == null)
            return;

        Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
        if (sessions == null)
            return;

        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByUserId.remove(userId);
        }
    }
}
