package app.flowstate.backend.controller;

import app.flowstate.backend.entity.*;
import app.flowstate.backend.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final ActivityEventRepository eventRepository;
    private final FocusSessionRepository sessionRepository;
    private final RecommendationRepository recommendationRepository;
    private final RecommendationFeedbackRepository feedbackRepository;

    public UserController(
            UserRepository userRepository,
            DeviceRepository deviceRepository,
            ActivityEventRepository eventRepository,
            FocusSessionRepository sessionRepository,
            RecommendationRepository recommendationRepository,
            RecommendationFeedbackRepository feedbackRepository) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
        this.recommendationRepository = recommendationRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @GetMapping("/export")
    public Map<String, Object> exportUserData(@AuthenticationPrincipal UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exported_at", OffsetDateTime.now().toString());

        Map<String, Object> userProfile = new LinkedHashMap<>();
        userProfile.put("id", user.getId());
        userProfile.put("email", user.getEmail());
        userProfile.put("created_at", user.getCreatedAt());
        userProfile.put("updated_at", user.getUpdatedAt());
        userProfile.put("preferences", user.getPreferences());
        export.put("user_profile", userProfile);

        List<Device> devices = deviceRepository.findByUserIdAndIsActiveTrue(userId);
        export.put("devices", devices);

        List<FocusSession> sessions = sessionRepository.findByUserId(userId);
        export.put("focus_sessions", sessions);

        List<ActivityEvent> events = eventRepository.findByUserIdOrderByTimestampDesc(userId);
        export.put("activity_events", events);

        List<Recommendation> recommendations = recommendationRepository.findTop5ByUserIdOrderByCreatedAtDesc(userId);
        export.put("recommendations", recommendations);

        List<RecommendationFeedback> feedback = feedbackRepository.findByUserIdOrderByCreatedAtDesc(userId);
        export.put("recommendation_feedback", feedback);

        return export;
    }
}
