package app.flowstate.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class EventBatchRequest {
    @NotEmpty
    @Valid
    private List<ActivityEventRequest> events;
}
