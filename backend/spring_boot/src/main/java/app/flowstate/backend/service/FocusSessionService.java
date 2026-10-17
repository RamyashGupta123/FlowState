package app.flowstate.backend.service;

import app.flowstate.backend.dto.FocusSessionActionRequest;
import app.flowstate.backend.dto.FocusSessionRequest;
import app.flowstate.backend.entity.FocusSession;
import app.flowstate.backend.repository.FocusSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class FocusSessionService {
    private final FocusSessionRepository repository;

    public FocusSessionService(FocusSessionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public FocusSession startSession(UUID userId, FocusSessionRequest request) {
        FocusSession session = FocusSession.builder()
                .userId(userId)
                .goalId(request != null ? request.getGoal_id() : null)
                .status("active")
                .startTime(OffsetDateTime.now())
                .durationSeconds(0)
                .interruptions(0)
                .notes(request != null ? request.getNotes() : null)
                .build();
        return repository.save(session);
    }

    @Transactional(readOnly = true)
    public List<FocusSession> listSessions(UUID userId, String status) {
        if (status != null && !status.isBlank()) {
            return repository.findByUserIdAndStatus(userId, status);
        }
        return repository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public FocusSession getSession(UUID sessionId, UUID userId) {
        return repository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Focus session not found"));
    }

    @Transactional
    public FocusSession updateSession(UUID sessionId, UUID userId, FocusSessionActionRequest request) {
        FocusSession session = getSession(sessionId, userId);
        String action = request != null && request.getAction() != null ? request.getAction().toLowerCase() : "";

        OffsetDateTime now = OffsetDateTime.now();

        switch (action) {
            case "pause":
                if ("active".equalsIgnoreCase(session.getStatus())) {
                    session.setStatus("paused");
                    session.setInterruptions((session.getInterruptions() != null ? session.getInterruptions() : 0) + 1);
                }
                break;
            case "resume":
                if ("paused".equalsIgnoreCase(session.getStatus())) {
                    session.setStatus("active");
                }
                break;
            case "complete":
                session.setStatus("completed");
                session.setEndTime(now);
                if (session.getStartTime() != null) {
                    int seconds = (int) Duration.between(session.getStartTime(), now).getSeconds();
                    session.setDurationSeconds(Math.max(0, seconds));
                }
                if (request != null && request.getNotes() != null) {
                    session.setNotes(request.getNotes());
                }
                break;
            case "abandon":
                session.setStatus("abandoned");
                session.setEndTime(now);
                if (session.getStartTime() != null) {
                    int seconds = (int) Duration.between(session.getStartTime(), now).getSeconds();
                    session.setDurationSeconds(Math.max(0, seconds));
                }
                if (request != null && request.getNotes() != null) {
                    session.setNotes(request.getNotes());
                }
                break;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown action: " + action);
        }

        return repository.save(session);
    }
}
