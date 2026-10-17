package app.flowstate.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FocusSessionRequest {
    private UUID goal_id;
    private String notes;
    private Integer target_duration_minutes;
}
