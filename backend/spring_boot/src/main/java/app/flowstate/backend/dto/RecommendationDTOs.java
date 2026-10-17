package app.flowstate.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

public class RecommendationDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RecommendationResponse {
        private UUID id;
        private String interventionType;
        private String title;
        private String message;
        private String reason;
        private Integer durationSeconds;
        private String status;
        private OffsetDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RecommendationEvaluationResult {
        private boolean eligible;
        private String suppressionReason; // null if eligible, otherwise FOCUS_SESSION_ACTIVE, RECENT_BREAK, COOLDOWN, SNOOZED, OPTED_OUT, THRESHOLD_NOT_MET, QUIET_HOURS
        private String explanation;
        private Integer continuousWorkMinutes;
        private RecommendationResponse recommendation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackRequest {
        @NotBlank(message = "feedbackType is required")
        private String feedbackType; // HELPFUL, NOT_RELEVANT, PREFER_WALKING, PREFER_BREATHING, REMIND_ME_LATER, DISMISS_FOR_TODAY
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InterventionPreferenceDTO {
        private Boolean focusRemindersEnabled;
        private Boolean breakRemindersEnabled;
        private Boolean reflectionPromptsEnabled;
        private Integer continuousWorkThresholdMinutes;
        private Integer cooldownMinutes;
        private Integer bufferAfterBreakMinutes;
        private String preferredInterventionType;
        private Boolean quietHoursEnabled;
        private String quietHoursStart;
        private String quietHoursEnd;
        private OffsetDateTime snoozeUntil;
    }
}
