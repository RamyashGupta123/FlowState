package app.flowstate.backend.controller;

import app.flowstate.backend.dto.FocusSessionActionRequest;
import app.flowstate.backend.dto.FocusSessionRequest;
import app.flowstate.backend.entity.FocusSession;
import app.flowstate.backend.service.FocusSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
public class FocusSessionController {
    private final FocusSessionService sessionService;

    public FocusSessionController(FocusSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FocusSession startSession(@AuthenticationPrincipal UUID userId, @RequestBody(required = false) FocusSessionRequest request) {
        return sessionService.startSession(userId, request);
    }

    @GetMapping
    public List<FocusSession> listSessions(@AuthenticationPrincipal UUID userId, @RequestParam(required = false) String status) {
        return sessionService.listSessions(userId, status);
    }

    @GetMapping("/{sessionId}")
    public FocusSession getSession(@AuthenticationPrincipal UUID userId, @PathVariable UUID sessionId) {
        return sessionService.getSession(sessionId, userId);
    }

    @PutMapping("/{sessionId}")
    public FocusSession updateSession(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID sessionId,
            @RequestBody FocusSessionActionRequest request) {
        return sessionService.updateSession(sessionId, userId, request);
    }
}
