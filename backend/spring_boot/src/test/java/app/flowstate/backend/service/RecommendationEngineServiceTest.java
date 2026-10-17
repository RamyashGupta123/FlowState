package app.flowstate.backend.service;

import app.flowstate.backend.dto.RecommendationDTOs.*;
import app.flowstate.backend.entity.*;
import app.flowstate.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendationEngineServiceTest {

    @Mock
    private RecommendationRepository recommendationRepository;

    @Mock
    private RecommendationFeedbackRepository feedbackRepository;

    @Mock
    private InterventionPreferenceRepository preferenceRepository;

    @Mock
    private FocusSessionRepository focusSessionRepository;

    @Mock
    private ActivityEventRepository activityEventRepository;

    @InjectMocks
    private RecommendationEngineService engineService;

    private RecommendationFeedbackService feedbackService;

    private UUID userId;
    private InterventionPreference defaultPrefs;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        feedbackService = new RecommendationFeedbackService(
                recommendationRepository,
                feedbackRepository,
                preferenceRepository,
                engineService
        );

        defaultPrefs = InterventionPreference.builder()
                .userId(userId)
                .breakRemindersEnabled(true)
                .focusRemindersEnabled(true)
                .continuousWorkThresholdMinutes(45)
                .cooldownMinutes(30)
                .bufferAfterBreakMinutes(15)
                .quietHoursEnabled(false)
                .build();

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(defaultPrefs));
    }

    @Test
    void testEvaluate_FocusSessionActive_StrongSuppression() {
        // Active focus session exists
        FocusSession activeSession = FocusSession.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status("active")
                .startTime(OffsetDateTime.now().minusMinutes(20))
                .build();
        when(focusSessionRepository.findByUserIdAndStatus(userId, "active"))
                .thenReturn(List.of(activeSession));

        RecommendationEvaluationResult result = engineService.evaluateCurrentRecommendation(userId, null);

        assertFalse(result.isEligible());
        assertEquals("FOCUS_SESSION_ACTIVE", result.getSuppressionReason());
        assertTrue(result.getExplanation().contains("Focus Session is in progress"));
        assertNull(result.getRecommendation());
    }

    @Test
    void testEvaluate_RecentBreakBuffer_Suppression() {
        // Focus session concluded 5 minutes ago (within 15 min buffer)
        FocusSession completedSession = FocusSession.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .status("completed")
                .startTime(OffsetDateTime.now().minusMinutes(30))
                .endTime(OffsetDateTime.now().minusMinutes(5))
                .build();
        when(focusSessionRepository.findByUserIdAndStatus(userId, "active")).thenReturn(Collections.emptyList());
        when(focusSessionRepository.findByUserIdAndStatus(userId, "paused")).thenReturn(Collections.emptyList());
        when(focusSessionRepository.findByUserIdAndStatusAndStartTimeBetween(eq(userId), eq("completed"), any(), any()))
                .thenReturn(List.of(completedSession));

        RecommendationEvaluationResult result = engineService.evaluateCurrentRecommendation(userId, null);

        assertFalse(result.isEligible());
        assertEquals("RECENT_BREAK", result.getSuppressionReason());
        assertTrue(result.getExplanation().contains("Allowing recovery time"));
    }

    @Test
    void testEvaluate_ContinuousWorkThresholdMet_GeneratesRecommendation() {
        when(focusSessionRepository.findByUserIdAndStatus(userId, "active")).thenReturn(Collections.emptyList());
        when(focusSessionRepository.findByUserIdAndStatus(userId, "paused")).thenReturn(Collections.emptyList());
        when(focusSessionRepository.findByUserIdAndStatusAndStartTimeBetween(eq(userId), eq("completed"), any(), any()))
                .thenReturn(Collections.emptyList());
        when(activityEventRepository.findByUserIdAndTimestampBetween(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());
        when(recommendationRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(Collections.emptyList());
        when(recommendationRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(userId, "PENDING"))
                .thenReturn(Optional.empty());
        when(feedbackRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(Collections.emptyList());
        when(recommendationRepository.findTop5ByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(Collections.emptyList());

        when(recommendationRepository.save(any(Recommendation.class))).thenAnswer(invocation -> {
            Recommendation r = invocation.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(OffsetDateTime.now());
            return r;
        });

        // Use continuous_work demo scenario (55 min)
        RecommendationEvaluationResult result = engineService.evaluateCurrentRecommendation(userId, "continuous_work");

        assertTrue(result.isEligible());
        assertNotNull(result.getRecommendation());
        assertEquals("PENDING", result.getRecommendation().getStatus());
        assertEquals(55, result.getContinuousWorkMinutes());
    }

    @Test
    void testEvaluate_PreferenceAdaptation_BoostsWalking() {
        defaultPrefs.setPreferredInterventionType(RecommendationEngineService.TYPE_WALK);

        when(focusSessionRepository.findByUserIdAndStatus(userId, "active")).thenReturn(Collections.emptyList());
        when(focusSessionRepository.findByUserIdAndStatus(userId, "paused")).thenReturn(Collections.emptyList());
        when(focusSessionRepository.findByUserIdAndStatusAndStartTimeBetween(eq(userId), eq("completed"), any(), any()))
                .thenReturn(Collections.emptyList());
        when(activityEventRepository.findByUserIdAndTimestampBetween(eq(userId), any(), any()))
                .thenReturn(Collections.emptyList());
        when(recommendationRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(Collections.emptyList());
        when(recommendationRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(userId, "PENDING"))
                .thenReturn(Optional.empty());
        when(feedbackRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(eq(userId), any()))
                .thenReturn(Collections.emptyList());
        when(recommendationRepository.findTop5ByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(Collections.emptyList());

        when(recommendationRepository.save(any(Recommendation.class))).thenAnswer(invocation -> {
            Recommendation r = invocation.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(OffsetDateTime.now());
            return r;
        });

        RecommendationEvaluationResult result = engineService.evaluateCurrentRecommendation(userId, "continuous_work");

        assertTrue(result.isEligible());
        assertEquals(RecommendationEngineService.TYPE_WALK, result.getRecommendation().getInterventionType());
    }

    @Test
    void testRecordFeedback_RemindMeLater_SnoozesAndPersists() {
        UUID recId = UUID.randomUUID();
        Recommendation existing = Recommendation.builder()
                .id(recId)
                .userId(userId)
                .interventionType(RecommendationEngineService.TYPE_BREATHING)
                .title("1-Minute Breathing")
                .message("Breathe")
                .reason("Cognitive reset")
                .status("PENDING")
                .createdAt(OffsetDateTime.now())
                .build();

        when(recommendationRepository.findByIdAndUserId(recId, userId)).thenReturn(Optional.of(existing));

        FeedbackRequest req = FeedbackRequest.builder()
                .feedbackType("REMIND_ME_LATER")
                .notes("In a call")
                .build();

        RecommendationResponse response = feedbackService.recordFeedback(userId, recId, req);

        assertEquals("SNOOZED", response.getStatus());
        assertNotNull(defaultPrefs.getSnoozeUntil());
        assertTrue(defaultPrefs.getSnoozeUntil().isAfter(OffsetDateTime.now()));
        verify(feedbackRepository).save(any(RecommendationFeedback.class));
        verify(preferenceRepository).save(defaultPrefs);
    }

    @Test
    void testRecordFeedback_CrossUserIsolation_ThrowsNotFound() {
        UUID recId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        // When userId tries to access other user's recommendation, repo returns empty
        when(recommendationRepository.findByIdAndUserId(recId, otherUserId)).thenReturn(Optional.empty());

        FeedbackRequest req = FeedbackRequest.builder().feedbackType("HELPFUL").build();

        assertThrows(ResponseStatusException.class, () ->
                feedbackService.recordFeedback(otherUserId, recId, req)
        );
    }

    @Test
    void testEvaluate_UserOptOut_Suppressed() {
        defaultPrefs.setBreakRemindersEnabled(false);

        RecommendationEvaluationResult result = engineService.evaluateCurrentRecommendation(userId, null);

        assertFalse(result.isEligible());
        assertEquals("OPTED_OUT", result.getSuppressionReason());
    }

    @Test
    void testEvaluate_ActiveSnooze_Suppressed() {
        defaultPrefs.setSnoozeUntil(OffsetDateTime.now().plusMinutes(20));

        RecommendationEvaluationResult result = engineService.evaluateCurrentRecommendation(userId, null);

        assertFalse(result.isEligible());
        assertEquals("SNOOZED", result.getSuppressionReason());
    }
}
