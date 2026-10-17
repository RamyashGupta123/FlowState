package app.flowstate.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "focus_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FocusSession {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name="user_id", nullable = false)
    private UUID userId;

    private UUID goalId;
    private String status;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private Integer durationSeconds;
    private String notes;
    
    @Builder.Default
    private Integer interruptions = 0;

    @CreationTimestamp
    private OffsetDateTime createdAt;
}
