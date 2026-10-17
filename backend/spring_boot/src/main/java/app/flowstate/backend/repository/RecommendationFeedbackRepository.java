package app.flowstate.backend.repository;

import app.flowstate.backend.entity.RecommendationFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface RecommendationFeedbackRepository extends JpaRepository<RecommendationFeedback, UUID> {
    List<RecommendationFeedback> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<RecommendationFeedback> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID userId, OffsetDateTime after);
    long countByUserIdAndFeedbackTypeAndCreatedAtAfter(UUID userId, String feedbackType, OffsetDateTime after);
}
