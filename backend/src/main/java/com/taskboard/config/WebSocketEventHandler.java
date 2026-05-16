package com.taskboard.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskboard.event.BoardEvent;
import com.taskboard.event.TaskEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@Slf4j
public class WebSocketEventHandler extends TextWebSocketHandler {

    private final Map<UUID, Set<WebSocketSession>> boardSessions = new ConcurrentHashMap<>();
    private final Map<String, UUID> sessionBoardMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public WebSocketEventHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        UUID boardId = extractBoardId(session);
        if (boardId != null) {
            boardSessions.computeIfAbsent(boardId, k -> new CopyOnWriteArraySet<>()).add(session);
            sessionBoardMap.put(session.getId(), boardId);
            log.info("WebSocket connected: session={}, board={}", session.getId(), boardId);
        } else {
            log.warn("WebSocket connection without boardId, session={}", session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        UUID boardId = sessionBoardMap.remove(session.getId());
        if (boardId != null) {
            Set<WebSocketSession> sessions = boardSessions.get(boardId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    boardSessions.remove(boardId);
                }
            }
        }
        log.info("WebSocket disconnected: session={}, status={}", session.getId(), status);
    }

    @EventListener
    public void handleTaskEvent(TaskEvent event) {
        UUID boardId = event.getBoardId();
        Set<WebSocketSession> sessions = boardSessions.get(boardId);

        if (sessions == null || sessions.isEmpty()) {
            log.debug("No active sessions for board: {}", boardId);
            return;
        }

        try {
            String message = objectMapper.writeValueAsString(event);
            broadcastToSessions(sessions, message);
        } catch (Exception e) {
            log.error("Failed to serialize task event", e);
        }
    }

    @EventListener
    public void handleBoardEvent(BoardEvent event) {
        // For board events, broadcast to all sessions
        // This is intentionally broadcasting to all - board list updates affect everyone
        try {
            String message = objectMapper.writeValueAsString(event);
            boardSessions.values().stream()
                    .flatMap(Set::stream)
                    .forEach(session -> sendMessage(session, message));
        } catch (Exception e) {
            log.error("Failed to serialize board event", e);
        }
    }

    private void broadcastToSessions(Set<WebSocketSession> sessions, String message) {
        sessions.forEach(session -> sendMessage(session, message));
    }

    private void sendMessage(WebSocketSession session, String message) {
        if (session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                log.error("Failed to send WebSocket message to session: {}", session.getId(), e);
            }
        }
    }

    private UUID extractBoardId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri != null && uri.getQuery() != null) {
            String query = uri.getQuery();
            for (String param : query.split("&")) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2 && "boardId".equals(keyValue[0])) {
                    try {
                        return UUID.fromString(keyValue[1]);
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid boardId in WebSocket query: {}", keyValue[1]);
                    }
                }
            }
        }
        return null;
    }

    // For testing purposes
    public int getActiveSessionCount(UUID boardId) {
        Set<WebSocketSession> sessions = boardSessions.get(boardId);
        return sessions != null ? sessions.size() : 0;
    }
}
