package app.flowstate.backend.controller;

import app.flowstate.backend.dto.DeviceRegistrationRequest;
import app.flowstate.backend.entity.Device;
import app.flowstate.backend.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Device register(@AuthenticationPrincipal UUID userId, @Valid @RequestBody DeviceRegistrationRequest request) {
        return deviceService.registerDevice(userId, request);
    }

    @GetMapping
    public List<Device> listDevices(@AuthenticationPrincipal UUID userId) {
        return deviceService.listDevices(userId);
    }

    @DeleteMapping("/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateDevice(@AuthenticationPrincipal UUID userId, @PathVariable UUID deviceId) {
        deviceService.deactivateDevice(userId, deviceId);
    }
}
