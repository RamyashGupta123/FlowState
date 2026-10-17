package app.flowstate.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class ActivityEventRequest {
    @NotNull private UUID eventId;
    @NotNull private UUID deviceId;
    @NotNull private String platform;
    @NotNull private OffsetDateTime timestamp;
    @NotNull private String timezone;
    @NotNull private OffsetDateTime sessionStart;
    @NotNull private OffsetDateTime sessionEnd;
    @NotNull private Integer durationSeconds;
    @NotNull private String schemaVersion;
    
    private String appIdentifier;
    private String appName;
    private String windowTitleSummary;
    private String browserDomain;
    private String browserTabTitleCategory;
    private Integer idleSeconds;
    private Integer appSwitchCount;
    private UUID declaredGoalId;
    private UUID focusSessionId;
    private String syncStatus;
    private String agentVersion;
}
