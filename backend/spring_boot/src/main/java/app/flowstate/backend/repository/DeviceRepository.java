package app.flowstate.backend.repository;

import app.flowstate.backend.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {
    List<Device> findByUserIdAndIsActiveTrue(UUID userId);
    Optional<Device> findByIdAndUserId(UUID id, UUID userId);
}
