package com.taskboard.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskboard.config.WebSocketEventHandler;
import com.taskboard.dto.TaskResponse;
import com.taskboard.event.TaskEvent;
import com.taskboard.event.TaskEventType;
import com.taskboard.model.TaskPriority;
import com.taskboard.model.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebSocketIntegrationTest {

    @Mock
    private WebSocketSession session;

    @Captor
    private ArgumentCaptor<TextMessage> messageCaptor;

    private WebSocketEventHandler handler;
    private ObjectMapper objectMapper;
    private UUID boardId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        handler = new WebSocketEventHandler(objectMapper);
        boardId = UUID.randomUUID();
    }

    @Test
    @DisplayName("should establish WebSocket connection with boardId")
    void shouldEstablishConnectionWithBoardId() throws Exception {
        // Given
        when(session.getUri()).thenReturn(new URI("ws://localhost:8080/ws/taskboard?boardId=" + boardId));
        when(session.getId()).thenReturn("session-1");
        when(session.isOpen()).thenReturn(true);

        // When
        handler.afterConnectionEstablished(session);

        // Then
        assertThat(handler.getActiveSessionCount(boardId)).isEqualTo(1);
    }

    @Test
    @DisplayName("should broadcast task created event to connected clients")
    void shouldBroadcastTaskCreatedEvent() throws Exception {
        // Given
        when(session.getUri()).thenReturn(new URI("ws://localhost:8080/ws/taskboard?boardId=" + boardId));
        when(session.getId()).thenReturn("session-1");
        when(session.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(session);

        TaskResponse taskResponse = TaskResponse.builder()
                .id(UUID.randomUUID())
                .boardId(boardId)
                .title("Test Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        TaskEvent event = TaskEvent.created(taskResponse);

        // When
        handler.handleTaskEvent(event);

        // Then
        // BUG #2: This test verifies that the message is properly serialized as JSON
        // The current implementation uses toString() which produces non-JSON output
        verify(session).sendMessage(messageCaptor.capture());
        String messageContent = messageCaptor.getValue().getPayload();

        // Should be valid JSON
        assertThat(messageContent).startsWith("{");
        assertThat(messageContent).contains("\"type\":\"TASK_CREATED\"");
        assertThat(messageContent).contains("\"boardId\":\"" + boardId + "\"");

        // Parse as JSON to verify structure
        TaskEvent parsedEvent = objectMapper.readValue(messageContent, TaskEvent.class);
        assertThat(parsedEvent.getType()).isEqualTo(TaskEventType.TASK_CREATED);
        assertThat(parsedEvent.getPayload().getTitle()).isEqualTo("Test Task");
    }

    @Test
    @DisplayName("should only send events to sessions subscribed to the board")
    void shouldOnlySendEventsToSubscribedSessions() throws Exception {
        // Given
        UUID otherBoardId = UUID.randomUUID();

        WebSocketSession session1 = mock(WebSocketSession.class);
        WebSocketSession session2 = mock(WebSocketSession.class);

        when(session1.getUri()).thenReturn(new URI("ws://localhost:8080/ws/taskboard?boardId=" + boardId));
        when(session1.getId()).thenReturn("session-1");
        when(session1.isOpen()).thenReturn(true);

        when(session2.getUri()).thenReturn(new URI("ws://localhost:8080/ws/taskboard?boardId=" + otherBoardId));
        when(session2.getId()).thenReturn("session-2");
        when(session2.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session1);
        handler.afterConnectionEstablished(session2);

        TaskResponse taskResponse = TaskResponse.builder()
                .id(UUID.randomUUID())
                .boardId(boardId)
                .title("Test Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        TaskEvent event = TaskEvent.created(taskResponse);

        // When
        handler.handleTaskEvent(event);

        // Then
        // session1 should receive the message (same board)
        verify(session1).sendMessage(any(TextMessage.class));
        // session2 should NOT receive the message (different board)
        verify(session2, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("should remove session on connection close")
    void shouldRemoveSessionOnConnectionClose() throws Exception {
        // Given
        when(session.getUri()).thenReturn(new URI("ws://localhost:8080/ws/taskboard?boardId=" + boardId));
        when(session.getId()).thenReturn("session-1");
        when(session.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(session);

        assertThat(handler.getActiveSessionCount(boardId)).isEqualTo(1);

        // When
        handler.afterConnectionClosed(session, null);

        // Then
        assertThat(handler.getActiveSessionCount(boardId)).isEqualTo(0);
    }
}
