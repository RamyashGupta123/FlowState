package app.flowstate.backend.controller;

import app.flowstate.backend.dto.EventBatchRequest;
import app.flowstate.backend.dto.EventBatchResponse;
import app.flowstate.backend.service.ActivityEventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {
    private final ActivityEventService eventService;

    public EventController(ActivityEventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EventBatchResponse ingestEvents(@AuthenticationPrincipal UUID userId, @Valid @RequestBody EventBatchRequest request) {
        return eventService.ingestEvents(userId, request);
    }

    @GetMapping
    public app.flowstate.backend.dto.EventTimelineResponse getEvents(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return eventService.getTimeline(userId, page, size);
    }

    @GetMapping("/summary")
    public app.flowstate.backend.dto.DailySummary getSummary(
            @AuthenticationPrincipal UUID userId,
            @RequestParam String date,
            @RequestParam(defaultValue = "Z") String timezone) {
        return eventService.getDailySummary(userId, date, timezone);
    }

    @PostMapping("/{eventId}/feedback")
    public app.flowstate.backend.entity.ActivityEvent submitFeedback(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID eventId,
            @RequestBody app.flowstate.backend.dto.UserFeedbackRequest request) {
        return eventService.submitFeedback(eventId, userId, request);
    }
}
