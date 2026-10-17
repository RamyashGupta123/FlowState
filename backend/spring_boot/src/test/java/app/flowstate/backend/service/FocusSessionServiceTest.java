package app.flowstate.backend.service;

import app.flowstate.backend.dto.FocusSessionActionRequest;
import app.flowstate.backend.dto.FocusSessionRequest;
import app.flowstate.backend.entity.FocusSession;
import app.flowstate.backend.repository.FocusSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class FocusSessionServiceTest {

    @Mock
    private FocusSessionRepository repository;

    @InjectMocks
    private FocusSessionService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testStartSession() {
        UUID userId = UUID.randomUUID();
        FocusSessionRequest req = FocusSessionRequest.builder()
                .notes("Deep work")
                .target_duration_minutes(25)
                .build();

        when(repository.save(any(FocusSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FocusSession created = service.startSession(userId, req);
        assertEquals(userId, created.getUserId());
        assertEquals("active", created.getStatus());
        assertEquals("Deep work", created.getNotes());
        assertEquals(0, created.getInterruptions());
    }

    @Test
    void testPauseAndResumeSession() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        FocusSession session = FocusSession.builder()
                .id(sessionId)
                .userId(userId)
                .status("active")
                .interruptions(0)
                .startTime(OffsetDateTime.now().minusMinutes(10))
                .build();

        when(repository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(repository.save(any(FocusSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Pause
        FocusSession paused = service.updateSession(sessionId, userId, FocusSessionActionRequest.builder().action("pause").build());
        assertEquals("paused", paused.getStatus());
        assertEquals(1, paused.getInterruptions());

        // Resume
        FocusSession resumed = service.updateSession(sessionId, userId, FocusSessionActionRequest.builder().action("resume").build());
        assertEquals("active", resumed.getStatus());
        assertEquals(1, resumed.getInterruptions());
    }

    @Test
    void testCompleteSession() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.now().minusMinutes(25);
        FocusSession session = FocusSession.builder()
                .id(sessionId)
                .userId(userId)
                .status("active")
                .interruptions(0)
                .startTime(start)
                .build();

        when(repository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(repository.save(any(FocusSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FocusSession completed = service.updateSession(sessionId, userId, FocusSessionActionRequest.builder()
                .action("complete")
                .notes("Finished coding tasks")
                .build());

        assertEquals("completed", completed.getStatus());
        assertNotNull(completed.getEndTime());
        assertTrue(completed.getDurationSeconds() >= 1490);
        assertEquals("Finished coding tasks", completed.getNotes());
    }
}
