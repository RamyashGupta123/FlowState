package app.flowstate.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "recommendation_feedback")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationFeedback {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "recommendation_id")
    private UUID recommendationId;

    @Column(name = "feedback_type", nullable = false, length = 50)
    private String feedbackType; // HELPFUL, NOT_RELEVANT, PREFER_WALKING, PREFER_BREATHING, REMIND_ME_LATER, DISMISS_FOR_TODAY

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
