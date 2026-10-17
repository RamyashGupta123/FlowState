import requests
import uuid
from datetime import datetime, timezone

BACKEND_URL = "http://localhost:8080"

def seed_data():
    email = "demo@flowstate.com"
    password = "password123"
    
    print(f"1. Authenticating as {email}...")
    res = requests.post(f"{BACKEND_URL}/api/auth/register", json={"email": email, "password": password})
    if res.status_code == 409: # Already exists
        res = requests.post(f"{BACKEND_URL}/api/auth/login", json={"email": email, "password": password})
    
    if res.status_code not in (200, 201):
        print(f"Failed to auth: {res.text}")
        return
        
    token = res.json()['token']
    headers = {"Authorization": f"Bearer {token}"}
    
    print("2. Registering demo device...")
    dev_res = requests.post(f"{BACKEND_URL}/api/devices", json={
        "platform": "windows",
        "name": "Demo PC",
        "agentVersion": "1.0"
    }, headers=headers)
    device_id = dev_res.json()['id']
    
    now = datetime.now(timezone.utc).isoformat()
    
    events = [
        {
            "eventId": str(uuid.uuid4()),
            "deviceId": device_id,
            "platform": "windows",
            "timestamp": now,
            "timezone": "UTC",
            "sessionStart": now,
            "sessionEnd": now,
            "durationSeconds": 1800,
            "schemaVersion": "1.0",
            "appIdentifier": "code",
            "appName": "VS Code",
            "browserDomain": "github.com",
            "browserTabTitleCategory": "development",
            "idleSeconds": 30,
            "appSwitchCount": 5,
            "syncStatus": "synced",
            "agentVersion": "1.0"
        },
        {
            "eventId": str(uuid.uuid4()),
            "deviceId": device_id,
            "platform": "windows",
            "timestamp": now,
            "timezone": "UTC",
            "sessionStart": now,
            "sessionEnd": now,
            "durationSeconds": 900,
            "schemaVersion": "1.0",
            "appIdentifier": "chrome",
            "appName": "Google Chrome",
            "browserDomain": "youtube.com",
            "browserTabTitleCategory": "video",
            "idleSeconds": 10,
            "appSwitchCount": 2,
            "syncStatus": "synced",
            "agentVersion": "1.0"
        },
        {
            "eventId": str(uuid.uuid4()),
            "deviceId": device_id,
            "platform": "windows",
            "timestamp": now,
            "timezone": "UTC",
            "sessionStart": now,
            "sessionEnd": now,
            "durationSeconds": 600,
            "schemaVersion": "1.0",
            "appIdentifier": "SyntheticTest",
            "appName": "Synthetic Test App",
            "browserDomain": "synthetic.com",
            "browserTabTitleCategory": "development",
            "idleSeconds": 0,
            "appSwitchCount": 0,
            "syncStatus": "synced", # We send it as synced so it gets picked up by classifyPendingEvents!
            "agentVersion": "1.0"
        }
    ]
    
    print("3. Ingesting synthetic events...")
    ingest_res = requests.post(f"{BACKEND_URL}/api/events", json={"events": events}, headers=headers)
    print(f"Ingest Result: {ingest_res.status_code}")
    
    print("4. Verifying GET /api/events...")
    get_res = requests.get(f"{BACKEND_URL}/api/events?page=0&size=10", headers=headers)
    if get_res.status_code == 200:
        data = get_res.json()
        print(f"Success! Found {data['total']} events in timeline.")
    
    print(f"\n--- Demo Data Ready ---")
    print(f"Login to the Flutter dashboard with:")
    print(f"Email: {email}")
    print(f"Password: {password}")

if __name__ == "__main__":
    seed_data()
