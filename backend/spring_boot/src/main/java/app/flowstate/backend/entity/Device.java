package app.flowstate.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "devices")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Device {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name="user_id", nullable = false)
    private UUID userId;

    private String platform;
    private String name;
    private String agentVersion;
    private OffsetDateTime lastSeen;
    
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    private OffsetDateTime createdAt;
}
