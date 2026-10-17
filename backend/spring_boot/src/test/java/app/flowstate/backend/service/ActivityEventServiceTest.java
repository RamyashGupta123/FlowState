package app.flowstate.backend.service;

import app.flowstate.backend.dto.DailySummary;
import app.flowstate.backend.entity.ActivityEvent;
import app.flowstate.backend.repository.ActivityEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class ActivityEventServiceTest {

    @Mock
    private ActivityEventRepository repository;

    @Mock
    private app.flowstate.backend.repository.DeviceRepository deviceRepository;

    @Mock
    private app.flowstate.backend.repository.FocusSessionRepository focusSessionRepository;

    @Mock
    private org.springframework.web.client.RestTemplate restTemplate;

    @InjectMocks
    private ActivityEventService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetDailySummary() {
        UUID userId = UUID.randomUUID();
        
        ActivityEvent productive = ActivityEvent.builder()
                .durationSeconds(1800)
                .activityCategory("productive_work")
                .build();
                
        ActivityEvent distracting = ActivityEvent.builder()
                .durationSeconds(900)
                .activityCategory("potential_distraction")
                .build();
                
        when(repository.findByUserIdAndTimestampBetween(eq(userId), any(), any()))
                .thenReturn(Arrays.asList(productive, distracting));

        DailySummary summary = service.getDailySummary(userId, "2026-10-10", "+00:00");
        
        // 1800 + 900 = 2700 seconds (45 minutes active)
        assertEquals(2700, summary.getTotal_active_seconds());
        
        // 1800 seconds (30 minutes productive)
        assertEquals(1800, summary.getCategory_breakdown().get("productive_work"));
        
        // 900 seconds (15 minutes distracting)
        assertEquals(900, summary.getCategory_breakdown().get("potential_distraction"));
        assertEquals(0, summary.getFocus_sessions_count());
        assertEquals(0, summary.getFocus_seconds_total());
    }

    @Test
    void testGetDailySummary_WithCompletedFocusSessions() {
        UUID userId = UUID.randomUUID();
        
        app.flowstate.backend.entity.FocusSession session = app.flowstate.backend.entity.FocusSession.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status("completed")
                .durationSeconds(1500)
                .build();

        when(repository.findByUserIdAndTimestampBetween(eq(userId), any(), any()))
                .thenReturn(java.util.Collections.emptyList());
        when(focusSessionRepository.findByUserIdAndStatusAndStartTimeBetween(eq(userId), eq("completed"), any(), any()))
                .thenReturn(Arrays.asList(session));

        DailySummary summary = service.getDailySummary(userId, "2026-10-10", "+00:00");
        assertEquals(1, summary.getFocus_sessions_count());
        assertEquals(1500, summary.getFocus_seconds_total());
    }

    @Test
    void testGetDailySummary_BrowserOverlapReconciliation() {
        UUID userId = UUID.randomUUID();
        OffsetDateTime baseTime = OffsetDateTime.parse("2026-10-10T10:00:00+00:00");

        // Browser extension event: 10:00 - 10:20 (1200s, productive_work)
        ActivityEvent extensionEvent = ActivityEvent.builder()
                .platform("browser")
                .browserDomain("github.com")
                .timestamp(baseTime)
                .sessionStart(baseTime)
                .sessionEnd(baseTime.plusSeconds(1200))
                .durationSeconds(1200)
                .activityCategory("productive_work")
                .build();

        // Desktop Windows chrome container: 10:00 - 10:30 (1800s, unknown)
        ActivityEvent desktopEvent = ActivityEvent.builder()
                .platform("windows")
                .appIdentifier("chrome.exe")
                .timestamp(baseTime)
                .sessionStart(baseTime)
                .sessionEnd(baseTime.plusSeconds(1800))
                .durationSeconds(1800)
                .activityCategory("unknown")
                .build();

        when(repository.findByUserIdAndTimestampBetween(eq(userId), any(), any()))
                .thenReturn(Arrays.asList(extensionEvent, desktopEvent));

        DailySummary summary = service.getDailySummary(userId, "2026-10-10", "+00:00");

        // Overlapping 1200s should be subtracted from desktop event (1800 - 1200 = 600s remaining)
        // Total active: 1200 (github) + 600 (remaining chrome container) = 1800s
        assertEquals(1800, summary.getTotal_active_seconds());
        assertEquals(1200, summary.getCategory_breakdown().get("productive_work"));
        assertEquals(600, summary.getCategory_breakdown().get("unknown"));
    }

    @Test
    void testClassifyPendingEvents_Success() {
        ActivityEvent event = ActivityEvent.builder()
                .id(UUID.randomUUID())
                .classificationSource("pending")
                .build();
                
        when(repository.findByClassificationSource("pending"))
                .thenReturn(Arrays.asList(event));
                
        java.util.Map<String, Object> mockResponse = new java.util.HashMap<>();
        mockResponse.put("predicted_category", "productive_work");
        mockResponse.put("classification_source", "ml_model");
        mockResponse.put("confidence", 0.95);
        
        when(restTemplate.postForEntity(any(String.class), any(), eq(java.util.Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockResponse));
                
        service.classifyPendingEvents();
        
        assertEquals("productive_work", event.getActivityCategory());
        assertEquals("ml_model", event.getClassificationSource());
        assertEquals(0.95, event.getClassificationConfidence());
    }

    @Test
    void testClassifyPendingEvents_Unclassified() {
        ActivityEvent event = ActivityEvent.builder()
                .id(UUID.randomUUID())
                .classificationSource("pending")
                .build();
                
        when(repository.findByClassificationSource("pending"))
                .thenReturn(Arrays.asList(event));
                
        java.util.Map<String, Object> mockResponse = new java.util.HashMap<>();
        mockResponse.put("predicted_category", "unknown");
        
        when(restTemplate.postForEntity(any(String.class), any(), eq(java.util.Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockResponse));
                
        service.classifyPendingEvents();
        
        org.junit.jupiter.api.Assertions.assertNull(event.getActivityCategory());
        assertEquals("unclassified", event.getClassificationSource());
    }

    @Test
    void testIngestEvents_ValidDevice_Success() {
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        app.flowstate.backend.dto.ActivityEventRequest req = new app.flowstate.backend.dto.ActivityEventRequest();
        req.setEventId(eventId);
        req.setDeviceId(deviceId);
        req.setPlatform("browser");
        req.setTimestamp(OffsetDateTime.now());
        req.setTimezone("UTC");
        req.setSessionStart(OffsetDateTime.now().minusMinutes(5));
        req.setSessionEnd(OffsetDateTime.now());
        req.setDurationSeconds(300);
        req.setSchemaVersion("1.0");

        app.flowstate.backend.dto.EventBatchRequest batch = new app.flowstate.backend.dto.EventBatchRequest();
        batch.setEvents(java.util.List.of(req));

        when(deviceRepository.findByIdAndUserId(deviceId, userId))
                .thenReturn(java.util.Optional.of(app.flowstate.backend.entity.Device.builder().id(deviceId).userId(userId).build()));
        when(repository.findByIdIn(any())).thenReturn(java.util.List.of());

        app.flowstate.backend.dto.EventBatchResponse response = service.ingestEvents(userId, batch);
        assertEquals(1, response.getAccepted());
    }

    @Test
    void testIngestEvents_NonExistentDevice_ThrowsBadRequest() {
        UUID userId = UUID.randomUUID();
        UUID fakeDeviceId = UUID.randomUUID();

        app.flowstate.backend.dto.ActivityEventRequest req = new app.flowstate.backend.dto.ActivityEventRequest();
        req.setEventId(UUID.randomUUID());
        req.setDeviceId(fakeDeviceId);

        app.flowstate.backend.dto.EventBatchRequest batch = new app.flowstate.backend.dto.EventBatchRequest();
        batch.setEvents(java.util.List.of(req));

        when(deviceRepository.findByIdAndUserId(fakeDeviceId, userId)).thenReturn(java.util.Optional.empty());
        when(deviceRepository.existsById(fakeDeviceId)).thenReturn(false);

        org.springframework.web.server.ResponseStatusException ex = org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.ingestEvents(userId, batch)
        );
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void testIngestEvents_ForeignUserDevice_ThrowsForbidden() {
        UUID userId = UUID.randomUUID();
        UUID foreignDeviceId = UUID.randomUUID();

        app.flowstate.backend.dto.ActivityEventRequest req = new app.flowstate.backend.dto.ActivityEventRequest();
        req.setEventId(UUID.randomUUID());
        req.setDeviceId(foreignDeviceId);

        app.flowstate.backend.dto.EventBatchRequest batch = new app.flowstate.backend.dto.EventBatchRequest();
        batch.setEvents(java.util.List.of(req));

        when(deviceRepository.findByIdAndUserId(foreignDeviceId, userId)).thenReturn(java.util.Optional.empty());
        when(deviceRepository.existsById(foreignDeviceId)).thenReturn(true);

        org.springframework.web.server.ResponseStatusException ex = org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> service.ingestEvents(userId, batch)
        );
        assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, ex.getStatusCode());
    }
}
