package app.flowstate.backend.dto;

import lombok.Data;

@Data
public class UserFeedbackRequest {
    private String corrected_category;
    private Boolean is_intentional;
    private String notes;
}
