package app.flowstate.backend.repository;

import app.flowstate.backend.entity.ActivityEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, UUID> {
    Page<ActivityEvent> findByUserIdAndTimestampBetweenOrderByTimestampDesc(UUID userId, OffsetDateTime start, OffsetDateTime end, Pageable pageable);
    Page<ActivityEvent> findAllByUserIdOrderByTimestampDesc(UUID userId, Pageable pageable);
    List<ActivityEvent> findByIdIn(List<UUID> ids);
    List<ActivityEvent> findByUserIdAndTimestampBetween(UUID userId, OffsetDateTime start, OffsetDateTime end);
    List<ActivityEvent> findByClassificationSourceAndSyncStatus(String classificationSource, String syncStatus);
    List<ActivityEvent> findByClassificationSource(String classificationSource);
    List<ActivityEvent> findByUserIdOrderByTimestampDesc(UUID userId);
}
