import requests
import json
import uuid
import time
from datetime import datetime, timezone

BACKEND_URL = "http://localhost:8080"
ML_URL = "http://localhost:8001"

def run_integration_test():
    print("Starting Integration Test...")
    
    # 1. Register User
    email = f"test_{uuid.uuid4().hex[:8]}@example.com"
    print(f"Registering user {email}...")
    auth_res = requests.post(f"{BACKEND_URL}/api/auth/register", json={
        "email": email,
        "password": "password123"
    })
    
    if auth_res.status_code != 201:
        print(f"Failed to register user: {auth_res.text}")
        return
        
    token = auth_res.json()['token']
    headers = {"Authorization": f"Bearer {token}"}
    
    # 2. Register Device
    print("Registering device...")
    dev_res = requests.post(f"{BACKEND_URL}/api/devices", json={
        "platform": "linux",
        "name": "Integration Test PC",
        "agentVersion": "1.0"
    }, headers=headers)
    
    device_id = dev_res.json()['id']
    
    # 3. Create Event
    print("Creating event...")
    event_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc).isoformat()
    
    event = {
        "eventId": event_id,
        "deviceId": device_id,
        "platform": "linux",
        "timestamp": now,
        "timezone": "UTC",
        "sessionStart": now,
        "sessionEnd": now,
        "durationSeconds": 300,
        "schemaVersion": "1.0",
        "appIdentifier": "code",
        "appName": "VS Code",
        "browserDomain": "github.com",
        "browserTabTitleCategory": "development",
        "idleSeconds": 5,
        "appSwitchCount": 2,
        "syncStatus": "pending",
        "agentVersion": "1.0"
    }
    
    # 4. Ingest Event to Backend
    print("Ingesting event to backend...")
    ingest_res = requests.post(f"{BACKEND_URL}/api/events", json={
        "events": [event]
    }, headers=headers)
    
    print(f"Ingest Result: {ingest_res.status_code}")
    if ingest_res.status_code != 202:
        print(f"Ingest Response: {ingest_res.text}")
    
    # 5. Classify Event via ML Service
    print("Classifying event via ML Service...")
    ml_req = {
        "event_id": event_id,
        "platform": "linux",
        "app_identifier": "code",
        "browser_domain": "github.com",
        "duration_seconds": 300,
        "idle_seconds": 5,
        "app_switch_count": 2,
        "is_focus_session_active": True
    }
    ml_res = requests.post(f"{ML_URL}/classify", json=ml_req)
    if ml_res.status_code == 200:
        ml_data = ml_res.json()
        print(f"ML Classification Result: {ml_data['predicted_category']} (Confidence: {ml_data['confidence']})")
    else:
        print(f"ML Classification Failed: {ml_res.text}")

    # 6. User Feedback correction
    print("Sending user feedback...")
    feedback_req = {
        "event_id": event_id,
        "corrected_category": "productive_work",
        "previous_category": ml_data.get('predicted_category') if ml_res.status_code == 200 else "unknown"
    }
    fb_res = requests.post(f"{ML_URL}/feedback", json=feedback_req)
    print(f"Feedback Result: {fb_res.status_code}")

    print("Integration test completed.")

if __name__ == "__main__":
    run_integration_test()
