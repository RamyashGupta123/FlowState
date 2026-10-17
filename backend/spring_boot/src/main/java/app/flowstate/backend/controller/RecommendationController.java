package app.flowstate.backend.controller;

import app.flowstate.backend.dto.RecommendationDTOs.*;
import app.flowstate.backend.service.RecommendationEngineService;
import app.flowstate.backend.service.RecommendationFeedbackService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationEngineService engineService;
    private final RecommendationFeedbackService feedbackService;

    public RecommendationController(
            RecommendationEngineService engineService,
            RecommendationFeedbackService feedbackService) {
        this.engineService = engineService;
        this.feedbackService = feedbackService;
    }

    @GetMapping("/current")
    public RecommendationEvaluationResult getCurrentRecommendation(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(name = "demo_scenario", required = false) String demoScenario) {
        return engineService.evaluateCurrentRecommendation(userId, demoScenario);
    }

    @PostMapping("/{id}/feedback")
    public RecommendationResponse recordFeedback(
            @AuthenticationPrincipal UUID userId,
            @PathVariable("id") UUID recommendationId,
            @Valid @RequestBody FeedbackRequest request) {
        return feedbackService.recordFeedback(userId, recommendationId, request);
    }

    @GetMapping("/preferences")
    public InterventionPreferenceDTO getPreferences(@AuthenticationPrincipal UUID userId) {
        return feedbackService.getPreferences(userId);
    }

    @PutMapping("/preferences")
    public InterventionPreferenceDTO updatePreferences(
            @AuthenticationPrincipal UUID userId,
            @RequestBody InterventionPreferenceDTO dto) {
        return feedbackService.updatePreferences(userId, dto);
    }
}
