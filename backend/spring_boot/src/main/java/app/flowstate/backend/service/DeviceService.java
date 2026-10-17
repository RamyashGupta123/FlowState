package app.flowstate.backend.service;

import app.flowstate.backend.dto.DeviceRegistrationRequest;
import app.flowstate.backend.entity.Device;
import app.flowstate.backend.repository.DeviceRepository;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DeviceService {
    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public Device registerDevice(UUID userId, DeviceRegistrationRequest request) {
        Device device = Device.builder()
                .userId(userId)
                .platform(request.getPlatform())
                .name(request.getName())
                .agentVersion(request.getAgentVersion())
                .lastSeen(OffsetDateTime.now())
                .build();
        return deviceRepository.save(device);
    }

    public List<Device> listDevices(UUID userId) {
        return deviceRepository.findByUserIdAndIsActiveTrue(userId);
    }

    public void deactivateDevice(UUID userId, UUID deviceId) {
        Device device = deviceRepository.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new RuntimeException("Device not found"));
        device.setIsActive(false);
        deviceRepository.save(device);
    }
}
