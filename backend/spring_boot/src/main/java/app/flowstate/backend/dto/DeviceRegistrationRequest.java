package app.flowstate.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeviceRegistrationRequest {
    @NotBlank
    private String platform;
    
    @NotBlank
    private String name;
    
    private String agentVersion;
}
