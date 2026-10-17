package app.flowstate.backend.repository;

import app.flowstate.backend.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {
    Optional<Recommendation> findByIdAndUserId(UUID id, UUID userId);
    Optional<Recommendation> findTopByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status);
    List<Recommendation> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID userId, OffsetDateTime after);
    List<Recommendation> findTop5ByUserIdOrderByCreatedAtDesc(UUID userId);
}
