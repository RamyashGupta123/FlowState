package app.flowstate.backend.repository;

import app.flowstate.backend.entity.InterventionPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InterventionPreferenceRepository extends JpaRepository<InterventionPreference, UUID> {
    Optional<InterventionPreference> findByUserId(UUID userId);
}
