package app.flowstate.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class EventBatchResponse {
    private int accepted;
    private int duplicatesSkipped;
    private List<Map<String, String>> errors;
}
