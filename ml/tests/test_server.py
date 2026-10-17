from fastapi.testclient import TestClient
from classifier.server import app

client = TestClient(app)

def test_health_check():
    response = client.get("/health")
    assert response.status_code == 200
    assert "status" in response.json()

def test_classify_valid():
    req = {
        "event_id": "123",
        "platform": "linux",
        "duration_seconds": 300,
        "is_focus_session_active": True
    }
    response = client.post("/classify", json=req)
    assert response.status_code == 200
    assert "predicted_category" in response.json()

def test_classify_invalid():
    req = {
        "event_id": "123"
        # missing required fields
    }
    response = client.post("/classify", json=req)
    assert response.status_code == 422
