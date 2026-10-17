package app.flowstate.backend.service;

import app.flowstate.backend.dto.ActivityEventRequest;
import app.flowstate.backend.dto.EventBatchRequest;
import app.flowstate.backend.dto.EventBatchResponse;
import app.flowstate.backend.entity.ActivityEvent;
import app.flowstate.backend.repository.ActivityEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ActivityEventService {
    private final ActivityEventRepository repository;
    private final app.flowstate.backend.repository.DeviceRepository deviceRepository;
    private final app.flowstate.backend.repository.FocusSessionRepository focusSessionRepository;
    private final RestTemplate restTemplate;
    
    @Value("${ML_SERVICE_URL:http://localhost:8001}")
    private String mlServiceUrl;

    public ActivityEventService(
            ActivityEventRepository repository,
            app.flowstate.backend.repository.DeviceRepository deviceRepository,
            app.flowstate.backend.repository.FocusSessionRepository focusSessionRepository,
            RestTemplate restTemplate) {
        this.repository = repository;
        this.deviceRepository = deviceRepository;
        this.focusSessionRepository = focusSessionRepository;
        this.restTemplate = restTemplate;
    }

    @Transactional
    public EventBatchResponse ingestEvents(UUID userId, EventBatchRequest request) {
        // Validate device ownership: every device in the batch must belong to the authenticated user
        Set<UUID> deviceIds = request.getEvents().stream()
                .map(ActivityEventRequest::getDeviceId)
                .collect(Collectors.toSet());

        for (UUID devId : deviceIds) {
            if (devId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device ID cannot be null");
            }
            if (deviceRepository.findByIdAndUserId(devId, userId).isEmpty()) {
                if (deviceRepository.existsById(devId)) {
                    throw new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "Unauthorized: Device " + devId + " does not belong to the authenticated user."
                    );
                } else {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Device " + devId + " is not a registered device."
                    );
                }
            }
        }

        List<UUID> incomingIds = request.getEvents().stream().map(ActivityEventRequest::getEventId).collect(Collectors.toList());
        List<UUID> existingIds = repository.findByIdIn(incomingIds).stream().map(ActivityEvent::getId).collect(Collectors.toList());

        List<ActivityEvent> toSave = new ArrayList<>();
        int duplicates = 0;

        for (ActivityEventRequest req : request.getEvents()) {
            if (existingIds.contains(req.getEventId())) {
                duplicates++;
                continue;
            }
            ActivityEvent event = ActivityEvent.builder()
                    .id(req.getEventId())
                    .userId(userId)
                    .deviceId(req.getDeviceId())
                    .platform(req.getPlatform())
                    .timestamp(req.getTimestamp())
                    .timezone(req.getTimezone())
                    .appIdentifier(req.getAppIdentifier())
                    .appName(req.getAppName())
                    .browserDomain(req.getBrowserDomain())
                    .browserTabTitleCategory(req.getBrowserTabTitleCategory())
                    .sessionStart(req.getSessionStart())
                    .sessionEnd(req.getSessionEnd())
                    .durationSeconds(req.getDurationSeconds())
                    .idleSeconds(req.getIdleSeconds())
                    .appSwitchCount(req.getAppSwitchCount())
                    .declaredGoalId(req.getDeclaredGoalId())
                    .focusSessionId(req.getFocusSessionId())
                    .syncStatus("synced") // Successfully reached the backend
                    .agentVersion(req.getAgentVersion())
                    .schemaVersion(req.getSchemaVersion())
                    .classificationSource("pending")
                    .build();
            toSave.add(event);
        }

        repository.saveAll(toSave);

        return EventBatchResponse.builder()
                .accepted(toSave.size())
                .duplicatesSkipped(duplicates)
                .errors(List.of())
                .build();
    }

    public app.flowstate.backend.dto.EventTimelineResponse getTimeline(UUID userId, int page, int size) {
        org.springframework.data.domain.Page<ActivityEvent> pageResult = repository.findAllByUserIdOrderByTimestampDesc(
            userId, org.springframework.data.domain.PageRequest.of(page, size)
        );
        return app.flowstate.backend.dto.EventTimelineResponse.builder()
                .events(pageResult.getContent())
                .total((int)pageResult.getTotalElements())
                .page(page)
                .size(size)
                .build();
    }

    public app.flowstate.backend.dto.DailySummary getDailySummary(UUID userId, String date, String timezone) {
        if (timezone == null || timezone.equals("Z") || timezone.isEmpty()) {
            timezone = "+00:00";
        }
        java.time.OffsetDateTime start = java.time.OffsetDateTime.parse(date + "T00:00:00" + timezone);
        java.time.OffsetDateTime end = start.plusDays(1);
        List<ActivityEvent> events = repository.findByUserIdAndTimestampBetween(userId, start, end);

        // Collect browser extension intervals to reconcile overlapping desktop browser containers
        List<long[]> extensionIntervals = new ArrayList<>();
        for (ActivityEvent e : events) {
            if ("browser".equalsIgnoreCase(e.getPlatform())) {
                long s = getStartEpoch(e);
                long endE = getEndEpoch(e);
                if (endE > s) {
                    extensionIntervals.add(new long[]{s, endE});
                }
            }
        }
        List<long[]> mergedExtensionIntervals = mergeIntervals(extensionIntervals);

        int totalActive = 0;
        Map<String, Integer> breakdown = new HashMap<>();
        for (ActivityEvent e : events) {
            int duration = e.getDurationSeconds() != null ? e.getDurationSeconds() : 0;
            if (duration <= 0) continue;

            int effectiveDuration = duration;
            // If desktop browser container overlaps with browser extension events, subtract overlapping seconds
            if (!"browser".equalsIgnoreCase(e.getPlatform()) && isBrowserApp(e.getAppIdentifier())) {
                long s = getStartEpoch(e);
                long endE = getEndEpoch(e);
                long overlap = 0;
                for (long[] interval : mergedExtensionIntervals) {
                    long oStart = Math.max(s, interval[0]);
                    long oEnd = Math.min(endE, interval[1]);
                    if (oEnd > oStart) {
                        overlap += (oEnd - oStart);
                    }
                }
                effectiveDuration = (int) Math.max(0, duration - overlap);
            }

            if (effectiveDuration > 0) {
                totalActive += effectiveDuration;
                String cat = e.getActivityCategory() != null ? e.getActivityCategory() : "unknown";
                breakdown.put(cat, breakdown.getOrDefault(cat, 0) + effectiveDuration);
            }
        }

        List<app.flowstate.backend.entity.FocusSession> completedSessions = focusSessionRepository != null
                ? focusSessionRepository.findByUserIdAndStatusAndStartTimeBetween(userId, "completed", start, end)
                : List.of();
        int focusSessionsCount = completedSessions.size();
        int focusSecondsTotal = completedSessions.stream()
                .mapToInt(s -> s.getDurationSeconds() != null ? s.getDurationSeconds() : 0)
                .sum();

        return app.flowstate.backend.dto.DailySummary.builder()
                .date(date)
                .total_active_seconds(totalActive)
                .category_breakdown(breakdown)
                .focus_sessions_count(focusSessionsCount)
                .focus_seconds_total(focusSecondsTotal)
                .build();
    }

    private long getStartEpoch(ActivityEvent event) {
        if (event.getSessionStart() != null) {
            return event.getSessionStart().toEpochSecond();
        }
        if (event.getTimestamp() != null) {
            return event.getTimestamp().toEpochSecond();
        }
        return 0;
    }

    private long getEndEpoch(ActivityEvent event) {
        if (event.getSessionEnd() != null) {
            return event.getSessionEnd().toEpochSecond();
        }
        long start = getStartEpoch(event);
        int dur = event.getDurationSeconds() != null ? event.getDurationSeconds() : 0;
        return start + dur;
    }

    private boolean isBrowserApp(String appIdentifier) {
        if (appIdentifier == null) return false;
        String lower = appIdentifier.toLowerCase();
        return lower.contains("chrome") || lower.contains("msedge") || lower.contains("firefox")
                || lower.contains("brave") || lower.contains("opera") || lower.contains("edge");
    }

    private List<long[]> mergeIntervals(List<long[]> intervals) {
        if (intervals.isEmpty()) return intervals;
        List<long[]> sorted = new ArrayList<>(intervals);
        sorted.sort((a, b) -> Long.compare(a[0], b[0]));
        List<long[]> merged = new ArrayList<>();
        long[] current = sorted.get(0);
        for (int i = 1; i < sorted.size(); i++) {
            long[] next = sorted.get(i);
            if (next[0] <= current[1]) {
                current[1] = Math.max(current[1], next[1]);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    @Transactional
    public ActivityEvent submitFeedback(UUID eventId, UUID userId, app.flowstate.backend.dto.UserFeedbackRequest request) {
        ActivityEvent event = repository.findById(eventId).orElseThrow(() -> new RuntimeException("Event not found"));
        if (!event.getUserId().equals(userId)) {
            throw new RuntimeException("Access Denied");
        }
        event.setActivityCategory(request.getCorrected_category());
        event.setClassificationSource("user_override");
        return repository.save(event);
    }

    @Scheduled(fixedDelay = 10000) // Poll every 10 seconds
    @Transactional
    public void classifyPendingEvents() {
        List<ActivityEvent> pending = repository.findByClassificationSource("pending");
        if (pending.isEmpty()) return;
        
        for (ActivityEvent event : pending) {
            try {
                Map<String, Object> req = new HashMap<>();
                req.put("event_id", event.getId().toString());
                req.put("platform", event.getPlatform());
                req.put("app_identifier", event.getAppIdentifier());
                req.put("browser_domain", event.getBrowserDomain());
                req.put("duration_seconds", event.getDurationSeconds());
                req.put("idle_seconds", event.getIdleSeconds());
                req.put("app_switch_count", event.getAppSwitchCount());
                req.put("is_focus_session_active", event.getFocusSessionId() != null);
                
                ResponseEntity<Map> res = restTemplate.postForEntity(mlServiceUrl + "/classify", req, Map.class);
                if (res.getStatusCode().is2xxSuccessful() && res.getBody() != null) {
                    Map<String, Object> body = res.getBody();
                    String predicted = (String) body.get("predicted_category");
                    
                    if (predicted != null && !predicted.equals("unknown")) {
                        event.setActivityCategory(predicted);
                        event.setClassificationSource((String) body.get("classification_source"));
                        Double confidence = (Double) body.get("confidence");
                        event.setClassificationConfidence(confidence != null ? confidence : 0.0);
                    } else {
                        event.setClassificationSource("unclassified");
                    }
                    repository.save(event);
                } else {
                    event.setClassificationSource("failed");
                    repository.save(event);
                }
            } catch (Exception e) {
                System.err.println("Failed to classify event " + event.getId() + ": " + e.getMessage());
                // We leave it as pending to retry, or we could mark as failed.
                // To avoid infinite loops on bad events, maybe mark as failed:
                event.setClassificationSource("failed");
                repository.save(event);
            }
        }
    }
}
