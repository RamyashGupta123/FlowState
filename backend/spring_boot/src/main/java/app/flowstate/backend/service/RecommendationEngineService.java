package app.flowstate.backend.service;

import app.flowstate.backend.dto.RecommendationDTOs.*;
import app.flowstate.backend.entity.*;
import app.flowstate.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class RecommendationEngineService {

    public static final String TYPE_BREATHING = "BREATHING_MINDFULNESS";
    public static final String TYPE_WALK = "WALK_STRETCH";
    public static final String TYPE_SCREEN = "SCREEN_REST";
    public static final String TYPE_GENERAL = "GENERAL_BREAK";

    private final RecommendationRepository recommendationRepository;
    private final RecommendationFeedbackRepository feedbackRepository;
    private final InterventionPreferenceRepository preferenceRepository;
    private final FocusSessionRepository focusSessionRepository;
    private final ActivityEventRepository activityEventRepository;

    public RecommendationEngineService(
            RecommendationRepository recommendationRepository,
            RecommendationFeedbackRepository feedbackRepository,
            InterventionPreferenceRepository preferenceRepository,
            FocusSessionRepository focusSessionRepository,
            ActivityEventRepository activityEventRepository) {
        this.recommendationRepository = recommendationRepository;
        this.feedbackRepository = feedbackRepository;
        this.preferenceRepository = preferenceRepository;
        this.focusSessionRepository = focusSessionRepository;
        this.activityEventRepository = activityEventRepository;
    }

    @Transactional
    public RecommendationEvaluationResult evaluateCurrentRecommendation(UUID userId, String demoScenario) {
        OffsetDateTime now = OffsetDateTime.now();
        InterventionPreference prefs = getOrCreatePreferences(userId);

        // 0. Handle Demo Reset Scenario
        if ("reset_demo".equalsIgnoreCase(demoScenario)) {
            prefs.setSnoozeUntil(null);
            preferenceRepository.save(prefs);
            List<Recommendation> recs = recommendationRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
                    userId, now.minusHours(2));
            for (Recommendation r : recs) {
                if ("PENDING".equals(r.getStatus())) {
                    r.setStatus("EXPIRED");
                    recommendationRepository.save(r);
                }
            }
            List<FocusSession> active = focusSessionRepository.findByUserIdAndStatus(userId, "active");
            for (FocusSession fs : active) {
                fs.setStatus("completed");
                fs.setEndTime(now.minusHours(1));
                focusSessionRepository.save(fs);
            }
            List<FocusSession> paused = focusSessionRepository.findByUserIdAndStatus(userId, "paused");
            for (FocusSession fs : paused) {
                fs.setStatus("completed");
                fs.setEndTime(now.minusHours(1));
                focusSessionRepository.save(fs);
            }
            return RecommendationEvaluationResult.builder()
                    .eligible(false)
                    .suppressionReason("DEMO_RESET")
                    .explanation("Demo state reset. Cooldown and pending prompts cleared.")
                    .continuousWorkMinutes(0)
                    .build();
        }

        // 1. User Opt-Out Check
        if (Boolean.FALSE.equals(prefs.getBreakRemindersEnabled())) {
            return RecommendationEvaluationResult.builder()
                    .eligible(false)
                    .suppressionReason("OPTED_OUT")
                    .explanation("Break reminders are disabled in user settings.")
                    .build();
        }

        // 2. Active Snooze Check
        if (prefs.getSnoozeUntil() != null && prefs.getSnoozeUntil().isAfter(now)) {
            return RecommendationEvaluationResult.builder()
                    .eligible(false)
                    .suppressionReason("SNOOZED")
                    .explanation("Reminders snoozed until " + prefs.getSnoozeUntil().format(DateTimeFormatter.ofPattern("HH:mm")))
                    .build();
        }

        // 3. Quiet Hours Check
        if (Boolean.TRUE.equals(prefs.getQuietHoursEnabled()) && isQuietHours(prefs, now)) {
            return RecommendationEvaluationResult.builder()
                    .eligible(false)
                    .suppressionReason("QUIET_HOURS")
                    .explanation("Quiet hours are active (" + prefs.getQuietHoursStart() + " - " + prefs.getQuietHoursEnd() + ").")
                    .build();
        }

        // 4. Strong Suppression: Active or Paused Focus Session
        boolean simFocus = "focus_session_active".equalsIgnoreCase(demoScenario);
        List<FocusSession> activeSessions = focusSessionRepository.findByUserIdAndStatus(userId, "active");
        List<FocusSession> pausedSessions = focusSessionRepository.findByUserIdAndStatus(userId, "paused");

        // Filter to sessions within last 4 hours
        activeSessions = activeSessions.stream()
                .filter(fs -> fs.getStartTime() != null && fs.getStartTime().isAfter(now.minusHours(4)))
                .toList();
        pausedSessions = pausedSessions.stream()
                .filter(fs -> fs.getStartTime() != null && fs.getStartTime().isAfter(now.minusHours(4)))
                .toList();

        if (simFocus || !activeSessions.isEmpty()) {
            return RecommendationEvaluationResult.builder()
                    .eligible(false)
                    .suppressionReason("FOCUS_SESSION_ACTIVE")
                    .explanation("FlowState Protected: An active Focus Session is in progress. All interruptions are deferred.")
                    .build();
        }
        if (!pausedSessions.isEmpty()) {
            return RecommendationEvaluationResult.builder()
                    .eligible(false)
                    .suppressionReason("DECLARED_BREAK")
                    .explanation("FlowState Protected: User is in a declared focus break/pause.")
                    .build();
        }

        // 5. Post-Focus / Post-Break Buffer Check
        int bufferMinutes = prefs.getBufferAfterBreakMinutes() != null ? prefs.getBufferAfterBreakMinutes() : 15;
        boolean simRecentBreak = "recent_break".equalsIgnoreCase(demoScenario);
        if (simRecentBreak || hasRecentFocusCompletion(userId, now, bufferMinutes) || hasRecentDeclaredBreak(userId, now, bufferMinutes)) {
            return RecommendationEvaluationResult.builder()
                    .eligible(false)
                    .suppressionReason("RECENT_BREAK")
                    .explanation("FlowState Protected: A break or focus session concluded within the last " + bufferMinutes + " minutes. Allowing recovery time before prompts.")
                    .build();
        }

        // 6. Cooldown Check (e.g. 30 min between prompts)
        int cooldownMins = prefs.getCooldownMinutes() != null ? prefs.getCooldownMinutes() : 30;
        boolean simCooldown = "cooldown".equalsIgnoreCase(demoScenario);
        boolean isDemoWork = "continuous_work".equalsIgnoreCase(demoScenario);
        if (simCooldown || (!isDemoWork && isCooldownActive(userId, now, cooldownMins))) {
            return RecommendationEvaluationResult.builder()
                    .eligible(false)
                    .suppressionReason("COOLDOWN")
                    .explanation("Intervention cooldown active (" + cooldownMins + " minutes between recommendations).")
                    .build();
        }

        // 7. Check if an active PENDING recommendation already exists (Prevent Duplicates)
        Optional<Recommendation> existingPending = recommendationRepository.findTopByUserIdAndStatusOrderByCreatedAtDesc(userId, "PENDING");
        if (existingPending.isPresent()) {
            Recommendation rec = existingPending.get();
            // Expire if older than 20 minutes
            if (Duration.between(rec.getCreatedAt(), now).toMinutes() > 20) {
                rec.setStatus("EXPIRED");
                recommendationRepository.save(rec);
            } else {
                return RecommendationEvaluationResult.builder()
                        .eligible(true)
                        .explanation("Active recommendation awaiting response.")
                        .continuousWorkMinutes(calculateContinuousWorkMinutes(userId, now, demoScenario))
                        .recommendation(toResponse(rec))
                        .build();
            }
        }

        // 8. Continuous Work Duration Evaluation
        int workMinutes = calculateContinuousWorkMinutes(userId, now, demoScenario);
        int threshold = prefs.getContinuousWorkThresholdMinutes() != null ? prefs.getContinuousWorkThresholdMinutes() : 45;

        if (workMinutes < threshold) {
            return RecommendationEvaluationResult.builder()
                    .eligible(false)
                    .suppressionReason("THRESHOLD_NOT_MET")
                    .explanation("Continuous productive work (" + workMinutes + " min) is below recommendation threshold (" + threshold + " min).")
                    .continuousWorkMinutes(workMinutes)
                    .build();
        }

        // 9. Eligible: Candidate Selection using Explainable Bounded Weighting
        InterventionCandidate selected = selectIntervention(userId, prefs, workMinutes, now);

        Recommendation newRec = Recommendation.builder()
                .userId(userId)
                .interventionType(selected.type)
                .title(selected.title)
                .message(selected.message)
                .reason(selected.reason)
                .durationSeconds(selected.durationSeconds)
                .status("PENDING")
                .score(selected.score)
                .build();
        newRec = recommendationRepository.save(newRec);

        return RecommendationEvaluationResult.builder()
                .eligible(true)
                .explanation("Continuous work threshold reached (" + workMinutes + "m >= " + threshold + "m). " + selected.explanation)
                .continuousWorkMinutes(workMinutes)
                .recommendation(toResponse(newRec))
                .build();
    }

    private boolean isQuietHours(InterventionPreference prefs, OffsetDateTime now) {
        try {
            LocalTime current = now.toLocalTime();
            LocalTime start = LocalTime.parse(prefs.getQuietHoursStart() != null ? prefs.getQuietHoursStart() : "22:00");
            LocalTime end = LocalTime.parse(prefs.getQuietHoursEnd() != null ? prefs.getQuietHoursEnd() : "08:00");
            if (start.isAfter(end)) {
                return current.isAfter(start) || current.isBefore(end);
            } else {
                return current.isAfter(start) && current.isBefore(end);
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasRecentFocusCompletion(UUID userId, OffsetDateTime now, int bufferMinutes) {
        OffsetDateTime windowStart = now.minusMinutes(bufferMinutes);
        List<FocusSession> completed = focusSessionRepository.findByUserIdAndStatusAndStartTimeBetween(
                userId, "completed", windowStart.minusHours(4), now);
        for (FocusSession fs : completed) {
            if (fs.getEndTime() != null && fs.getEndTime().isAfter(windowStart)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRecentDeclaredBreak(UUID userId, OffsetDateTime now, int bufferMinutes) {
        OffsetDateTime windowStart = now.minusMinutes(bufferMinutes);
        // Conservative: check for events categorized as break or idle with duration >= 8 minutes (480s)
        List<ActivityEvent> events = activityEventRepository.findByUserIdAndTimestampBetween(userId, windowStart, now);
        for (ActivityEvent e : events) {
            if (e.getDurationSeconds() != null && e.getDurationSeconds() >= 480) {
                String cat = e.getActivityCategory() != null ? e.getActivityCategory().toLowerCase() : "";
                if (cat.contains("break") || cat.contains("rest") || cat.contains("idle")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isCooldownActive(UUID userId, OffsetDateTime now, int cooldownMinutes) {
        OffsetDateTime cooldownStart = now.minusMinutes(cooldownMinutes);
        List<Recommendation> recent = recommendationRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, cooldownStart);
        for (Recommendation r : recent) {
            // If accepted or dismissed within cooldown window, suppress
            if ("ACCEPTED".equalsIgnoreCase(r.getStatus()) || "DISMISSED".equalsIgnoreCase(r.getStatus()) || "SNOOZED".equalsIgnoreCase(r.getStatus())) {
                return true;
            }
        }
        return false;
    }

    private int calculateContinuousWorkMinutes(UUID userId, OffsetDateTime now, String demoScenario) {
        if ("continuous_work".equalsIgnoreCase(demoScenario)) {
            return 55;
        }
        // Calculate from real activity events within last 3 hours
        OffsetDateTime windowStart = now.minusHours(3);
        List<ActivityEvent> events = activityEventRepository.findByUserIdAndTimestampBetween(userId, windowStart, now);
        if (events == null || events.isEmpty()) {
            return 0;
        }

        // Sort chronologically ascending
        events.sort(Comparator.comparing(ActivityEvent::getTimestamp));

        int totalProductiveSeconds = 0;
        for (ActivityEvent e : events) {
            int duration = e.getDurationSeconds() != null ? e.getDurationSeconds() : 0;
            String cat = e.getActivityCategory() != null ? e.getActivityCategory().toLowerCase() : "";
            // Conservative break reset: if an idle/break gap >= 480 seconds (8 mins) occurs, reset streak
            if (duration >= 480 && (cat.contains("break") || cat.contains("idle"))) {
                totalProductiveSeconds = 0;
            } else if (cat.contains("dev") || cat.contains("work") || cat.contains("productiv") || cat.contains("learn") || cat.contains("research") || cat.contains("office")) {
                totalProductiveSeconds += duration;
            } else if (duration < 480) {
                // Short micro-pauses do not reset continuous work
                totalProductiveSeconds += duration;
            }
        }

        if (totalProductiveSeconds <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(totalProductiveSeconds / 60.0));
    }

    private InterventionCandidate selectIntervention(UUID userId, InterventionPreference prefs, int workMinutes, OffsetDateTime now) {
        // Base candidate definitions
        Map<String, InterventionCandidate> candidates = new LinkedHashMap<>();

        candidates.put(TYPE_BREATHING, new InterventionCandidate(
                TYPE_BREATHING,
                "1-Minute Breathing Exercise",
                "Take a deliberate 60-second breathing break. Inhale for 4 seconds, hold for 4 seconds, and exhale for 6 seconds.",
                "Continuous cognitive focus detected. Lowers sympathetic stress and restores clarity.",
                60,
                1.0,
                "Promotes cognitive balance."
        ));

        candidates.put(TYPE_WALK, new InterventionCandidate(
                TYPE_WALK,
                "Movement & Posture Stretch",
                "Stand up, roll your shoulders, and take a quick 3-minute walk around your workspace.",
                "Prolonged sedentary posture detected. Restores spinal alignment and circulation.",
                180,
                1.0,
                "Addresses physical immobility."
        ));

        candidates.put(TYPE_SCREEN, new InterventionCandidate(
                TYPE_SCREEN,
                "20-20-20 Screen Rest",
                "Shift your focus away from your monitor. Look at an object at least 20 feet away for 20 seconds.",
                "Sustained visual screen strain detected. Relaxes ciliary ocular muscles.",
                60,
                1.0,
                "Reduces ocular strain."
        ));

        candidates.put(TYPE_GENERAL, new InterventionCandidate(
                TYPE_GENERAL,
                "Hydration & Energy Recharge",
                "Step away from your workstation, hydrate with a glass of fresh water, and take a brief pause.",
                "High cumulative work output today. Sustains cognitive stamina for subsequent tasks.",
                300,
                1.0,
                "Maintains long-term energy."
        ));

        // Contextual heuristics based on work duration
        if (workMinutes >= 60) {
            candidates.get(TYPE_WALK).score += 0.8;
            candidates.get(TYPE_SCREEN).score += 0.6;
        }
        if (workMinutes >= 90) {
            candidates.get(TYPE_GENERAL).score += 1.2;
        }

        // Preference heuristics (bounded boost)
        String prefType = prefs.getPreferredInterventionType();
        if (prefType != null && candidates.containsKey(prefType)) {
            candidates.get(prefType).score += 2.5;
            candidates.get(prefType).explanation += " (matches your preferred activity preference)";
        }

        // Bounded feedback history adjustment (past 7 days)
        OffsetDateTime sevenDaysAgo = now.minusDays(7);
        List<RecommendationFeedback> feedbackHistory = feedbackRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, sevenDaysAgo);

        for (RecommendationFeedback fb : feedbackHistory) {
            String type = fb.getFeedbackType();
            if ("PREFER_WALKING".equalsIgnoreCase(type)) {
                candidates.get(TYPE_WALK).score += 1.0;
            } else if ("PREFER_BREATHING".equalsIgnoreCase(type)) {
                candidates.get(TYPE_BREATHING).score += 1.0;
            }
        }

        // Recency penalty: penalize the most recent recommendation intervention type to ensure variety
        List<Recommendation> recentRecs = recommendationRepository.findTop5ByUserIdOrderByCreatedAtDesc(userId);
        if (!recentRecs.isEmpty()) {
            String lastType = recentRecs.get(0).getInterventionType();
            if (candidates.containsKey(lastType)) {
                candidates.get(lastType).score -= 1.0;
            }
        }

        // Clamp scores within explainable bounds [0.1, 5.0]
        for (InterventionCandidate c : candidates.values()) {
            c.score = Math.max(0.1, Math.min(5.0, c.score));
        }

        // Select maximum scoring candidate
        return candidates.values().stream()
                .max(Comparator.comparingDouble(c -> c.score))
                .orElse(candidates.get(TYPE_BREATHING));
    }

    public InterventionPreference getOrCreatePreferences(UUID userId) {
        return preferenceRepository.findByUserId(userId).orElseGet(() -> {
            InterventionPreference newPref = InterventionPreference.builder()
                    .userId(userId)
                    .focusRemindersEnabled(true)
                    .breakRemindersEnabled(true)
                    .reflectionPromptsEnabled(true)
                    .continuousWorkThresholdMinutes(45)
                    .cooldownMinutes(30)
                    .bufferAfterBreakMinutes(15)
                    .quietHoursEnabled(false)
                    .quietHoursStart("22:00")
                    .quietHoursEnd("08:00")
                    .build();
            return preferenceRepository.save(newPref);
        });
    }

    private RecommendationResponse toResponse(Recommendation rec) {
        return RecommendationResponse.builder()
                .id(rec.getId())
                .interventionType(rec.getInterventionType())
                .title(rec.getTitle())
                .message(rec.getMessage())
                .reason(rec.getReason())
                .durationSeconds(rec.getDurationSeconds())
                .status(rec.getStatus())
                .createdAt(rec.getCreatedAt())
                .build();
    }

    private static class InterventionCandidate {
        String type;
        String title;
        String message;
        String reason;
        int durationSeconds;
        double score;
        String explanation;

        InterventionCandidate(String type, String title, String message, String reason, int durationSeconds, double score, String explanation) {
            this.type = type;
            this.title = title;
            this.message = message;
            this.reason = reason;
            this.durationSeconds = durationSeconds;
            this.score = score;
            this.explanation = explanation;
        }
    }
}
