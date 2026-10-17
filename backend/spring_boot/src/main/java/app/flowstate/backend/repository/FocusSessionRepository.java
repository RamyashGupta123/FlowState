package app.flowstate.backend.repository;

import app.flowstate.backend.entity.FocusSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FocusSessionRepository extends JpaRepository<FocusSession, UUID> {
    List<FocusSession> findByUserIdAndStatus(UUID userId, String status);
    List<FocusSession> findByUserId(UUID userId);
    Optional<FocusSession> findByIdAndUserId(UUID id, UUID userId);
    List<FocusSession> findByUserIdAndStartTimeBetween(UUID userId, java.time.OffsetDateTime start, java.time.OffsetDateTime end);
    List<FocusSession> findByUserIdAndStatusAndStartTimeBetween(UUID userId, String status, java.time.OffsetDateTime start, java.time.OffsetDateTime end);
}
