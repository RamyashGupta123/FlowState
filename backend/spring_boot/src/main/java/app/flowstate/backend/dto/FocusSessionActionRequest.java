package app.flowstate.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FocusSessionActionRequest {
    private String action; // pause, resume, complete, abandon
    private String notes;
}
