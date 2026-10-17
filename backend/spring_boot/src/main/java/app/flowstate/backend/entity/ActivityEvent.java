package app.flowstate.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "activity_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityEvent {
    @Id
    private UUID id;

    @Column(name="user_id", nullable = false)
    private UUID userId;

    @Column(name="device_id", nullable = false)
    private UUID deviceId;

    private String platform;
    private OffsetDateTime timestamp;
    private String timezone;
    private String appIdentifier;
    private String appName;
    private String browserDomain;
    private String browserTabTitleCategory;
    private OffsetDateTime sessionStart;
    private OffsetDateTime sessionEnd;
    private Integer durationSeconds;
    private Integer idleSeconds;
    private Integer appSwitchCount;
    private UUID declaredGoalId;
    private UUID focusSessionId;
    private String activityCategory;
    private String goalAlignment;
    private Double classificationConfidence;
    private String classificationSource;
    
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> classificationEvidence;

    @Builder.Default
    private Boolean requiresUserConfirmation = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String userFeedback;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String contextFeatures;

    private String syncStatus;
    private String agentVersion;
    private String schemaVersion;

    @CreationTimestamp
    private OffsetDateTime createdAt;
}
