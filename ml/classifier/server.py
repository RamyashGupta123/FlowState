from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import os
from classifier.classifier import FlowStateClassifier
from classifier.feature_pipeline import FeaturePipeline

app = FastAPI(title="FlowState ML Classifier")

classifier = FlowStateClassifier()
pipeline = FeaturePipeline()

model_path = os.getenv("ML_MODEL_PATH", "models/classifier.joblib")
classifier.load_model(model_path)
if not classifier.is_model_loaded():
    print("WARNING: Model not loaded, falling back to rule-based classification only.")

class ClassificationRequest(BaseModel):
    event_id: str
    platform: str
    app_identifier: str | None = None
    browser_domain: str | None = None
    browser_tab_title_category: str | None = None
    duration_seconds: int
    idle_seconds: int | None = None
    app_switch_count: int | None = None
    declared_goal_category: str | None = None
    is_focus_session_active: bool
    time_of_day_slot: str | None = None
    day_of_week: str | None = None
    user_id: str | None = None

class ClassificationResponse(BaseModel):
    event_id: str
    predicted_category: str
    confidence: float
    goal_alignment: str
    evidence: list[str]
    requires_user_confirmation: bool
    classification_source: str

class FeedbackRequest(BaseModel):
    event_id: str
    corrected_category: str
    previous_category: str | None = None

@app.get("/health")
def health_check():
    return {"status": "ok", "model_loaded": classifier.is_model_loaded()}

@app.post("/classify", response_model=ClassificationResponse)
def classify_event(req: ClassificationRequest):
    raw_request = req.dict()
    features = pipeline.process(raw_request)
    result = classifier.classify(features, raw_request)
    
    return ClassificationResponse(
        event_id=req.event_id,
        predicted_category=result.predicted_category,
        confidence=result.confidence,
        goal_alignment=result.goal_alignment,
        evidence=result.evidence,
        requires_user_confirmation=result.requires_user_confirmation,
        classification_source=result.classification_source
    )

@app.post("/feedback")
def receive_feedback(req: FeedbackRequest):
    print(f"Feedback received for event {req.event_id}: {req.corrected_category} (was {req.previous_category})")
    return {"status": "recorded"}

if __name__ == "__main__":
    import uvicorn
    host = os.getenv("ML_SERVICE_HOST", "0.0.0.0")
    port = int(os.getenv("ML_SERVICE_PORT", 8001))
    uvicorn.run(app, host=host, port=port)
