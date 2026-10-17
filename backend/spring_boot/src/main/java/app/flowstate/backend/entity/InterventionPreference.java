package app.flowstate.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "intervention_preferences")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterventionPreference {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", unique = true, nullable = false)
    private UUID userId;

    @Column(name = "focus_reminders_enabled")
    @Builder.Default
    private Boolean focusRemindersEnabled = true;

    @Column(name = "break_reminders_enabled")
    @Builder.Default
    private Boolean breakRemindersEnabled = true;

    @Column(name = "reflection_prompts_enabled")
    @Builder.Default
    private Boolean reflectionPromptsEnabled = true;

    @Column(name = "snooze_until")
    private OffsetDateTime snoozeUntil;

    @Column(name = "continuous_work_threshold_minutes")
    @Builder.Default
    private Integer continuousWorkThresholdMinutes = 45;

    @Column(name = "cooldown_minutes")
    @Builder.Default
    private Integer cooldownMinutes = 30;

    @Column(name = "buffer_after_break_minutes")
    @Builder.Default
    private Integer bufferAfterBreakMinutes = 15;

    @Column(name = "preferred_intervention_type", length = 50)
    private String preferredInterventionType;

    @Column(name = "quiet_hours_enabled")
    @Builder.Default
    private Boolean quietHoursEnabled = false;

    @Column(name = "quiet_hours_start", length = 10)
    @Builder.Default
    private String quietHoursStart = "22:00";

    @Column(name = "quiet_hours_end", length = 10)
    @Builder.Default
    private String quietHoursEnd = "08:00";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
