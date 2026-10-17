package app.flowstate.backend.dto;

import app.flowstate.backend.entity.ActivityEvent;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class EventTimelineResponse {
    private List<ActivityEvent> events;
    private int total;
    private int page;
    private int size;
}
