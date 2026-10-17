package app.flowstate.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class DailySummary {
    private String date;
    private int total_active_seconds;
    private Map<String, Integer> category_breakdown;
    private int focus_sessions_count;
    private int focus_seconds_total;
}
