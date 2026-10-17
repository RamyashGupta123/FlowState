package app.flowstate.backend.service;

import app.flowstate.backend.dto.RecommendationDTOs.*;
import app.flowstate.backend.entity.*;
import app.flowstate.backend.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class RecommendationFeedbackService {

    private final RecommendationRepository recommendationRepository;
    private final RecommendationFeedbackRepository feedbackRepository;
    private final InterventionPreferenceRepository preferenceRepository;
    private final RecommendationEngineService engineService;

    public RecommendationFeedbackService(
            RecommendationRepository recommendationRepository,
            RecommendationFeedbackRepository feedbackRepository,
            InterventionPreferenceRepository preferenceRepository,
            RecommendationEngineService engineService) {
        this.recommendationRepository = recommendationRepository;
        this.feedbackRepository = feedbackRepository;
        this.preferenceRepository = preferenceRepository;
        this.engineService = engineService;
    }

    @Transactional
    public RecommendationResponse recordFeedback(UUID userId, UUID recommendationId, FeedbackRequest request) {
        Recommendation rec = recommendationRepository.findByIdAndUserId(recommendationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation not found"));

        OffsetDateTime now = OffsetDateTime.now();
        String feedbackType = request.getFeedbackType() != null ? request.getFeedbackType().toUpperCase() : "HELPFUL";
        InterventionPreference prefs = engineService.getOrCreatePreferences(userId);

        switch (feedbackType) {
            case "REMIND_ME_LATER":
                rec.setStatus("SNOOZED");
                prefs.setSnoozeUntil(now.plusMinutes(15));
                break;
            case "DISMISS_FOR_TODAY":
                rec.setStatus("DISMISSED");
                prefs.setSnoozeUntil(now.plusHours(12));
                break;
            case "PREFER_WALKING":
                rec.setStatus("ACCEPTED");
                prefs.setPreferredInterventionType(RecommendationEngineService.TYPE_WALK);
                break;
            case "PREFER_BREATHING":
                rec.setStatus("ACCEPTED");
                prefs.setPreferredInterventionType(RecommendationEngineService.TYPE_BREATHING);
                break;
            case "HELPFUL":
                rec.setStatus("ACCEPTED");
                break;
            case "NOT_RELEVANT":
                rec.setStatus("DISMISSED");
                break;
            default:
                rec.setStatus("ACCEPTED");
                break;
        }

        rec.setRespondedAt(now);
        recommendationRepository.save(rec);
        preferenceRepository.save(prefs);

        RecommendationFeedback feedback = RecommendationFeedback.builder()
                .userId(userId)
                .recommendationId(rec.getId())
                .feedbackType(feedbackType)
                .notes(request.getNotes())
                .build();
        feedbackRepository.save(feedback);

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

    @Transactional(readOnly = true)
    public InterventionPreferenceDTO getPreferences(UUID userId) {
        InterventionPreference p = engineService.getOrCreatePreferences(userId);
        return toDTO(p);
    }

    @Transactional
    public InterventionPreferenceDTO updatePreferences(UUID userId, InterventionPreferenceDTO dto) {
        InterventionPreference p = engineService.getOrCreatePreferences(userId);
        if (dto.getFocusRemindersEnabled() != null) p.setFocusRemindersEnabled(dto.getFocusRemindersEnabled());
        if (dto.getBreakRemindersEnabled() != null) p.setBreakRemindersEnabled(dto.getBreakRemindersEnabled());
        if (dto.getReflectionPromptsEnabled() != null) p.setReflectionPromptsEnabled(dto.getReflectionPromptsEnabled());
        if (dto.getContinuousWorkThresholdMinutes() != null) p.setContinuousWorkThresholdMinutes(dto.getContinuousWorkThresholdMinutes());
        if (dto.getCooldownMinutes() != null) p.setCooldownMinutes(dto.getCooldownMinutes());
        if (dto.getBufferAfterBreakMinutes() != null) p.setBufferAfterBreakMinutes(dto.getBufferAfterBreakMinutes());
        if (dto.getPreferredInterventionType() != null) p.setPreferredInterventionType(dto.getPreferredInterventionType());
        if (dto.getQuietHoursEnabled() != null) p.setQuietHoursEnabled(dto.getQuietHoursEnabled());
        if (dto.getQuietHoursStart() != null) p.setQuietHoursStart(dto.getQuietHoursStart());
        if (dto.getQuietHoursEnd() != null) p.setQuietHoursEnd(dto.getQuietHoursEnd());
        if (dto.getSnoozeUntil() != null) p.setSnoozeUntil(dto.getSnoozeUntil());

        p = preferenceRepository.save(p);
        return toDTO(p);
    }

    private InterventionPreferenceDTO toDTO(InterventionPreference p) {
        return InterventionPreferenceDTO.builder()
                .focusRemindersEnabled(p.getFocusRemindersEnabled())
                .breakRemindersEnabled(p.getBreakRemindersEnabled())
                .reflectionPromptsEnabled(p.getReflectionPromptsEnabled())
                .continuousWorkThresholdMinutes(p.getContinuousWorkThresholdMinutes())
                .cooldownMinutes(p.getCooldownMinutes())
                .bufferAfterBreakMinutes(p.getBufferAfterBreakMinutes())
                .preferredInterventionType(p.getPreferredInterventionType())
                .quietHoursEnabled(p.getQuietHoursEnabled())
                .quietHoursStart(p.getQuietHoursStart())
                .quietHoursEnd(p.getQuietHoursEnd())
                .snoozeUntil(p.getSnoozeUntil())
                .build();
    }
}
