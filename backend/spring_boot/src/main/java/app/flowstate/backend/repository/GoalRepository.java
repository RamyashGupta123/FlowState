package app.flowstate.backend.repository;

import app.flowstate.backend.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoalRepository extends JpaRepository<Goal, UUID> {
    List<Goal> findByUserIdAndStatus(UUID userId, String status);
    List<Goal> findByUserId(UUID userId);
    Optional<Goal> findByIdAndUserId(UUID id, UUID userId);
}
