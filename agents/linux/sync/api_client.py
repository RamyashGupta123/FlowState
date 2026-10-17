from dataclasses import dataclass
import requests

@dataclass
class SyncResult:
    accepted: int
    duplicates_skipped: int
    errors: list
    success: bool

class FlowStateApiClient:
    def __init__(self, api_url: str, token: str, device_id: str):
        self.api_url = api_url.rstrip('/')
        self.token = token
        self.device_id = device_id
        self.headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

    def register_device(self, platform: str, name: str, agent_version: str) -> str | None:
        try:
            res = requests.post(
                f"{self.api_url}/api/devices",
                json={"platform": platform, "name": name, "agentVersion": agent_version},
                headers=self.headers,
                timeout=10
            )
            if res.status_code == 201:
                return res.json().get('device_id')
        except Exception:
            pass
        return None

    def sync_events(self, events: list[dict]) -> SyncResult:
        if not events:
            return SyncResult(0, 0, [], True)
            
        try:
            res = requests.post(
                f"{self.api_url}/api/events",
                json={"events": events},
                headers=self.headers,
                timeout=15
            )
            if res.status_code == 202:
                data = res.json()
                return SyncResult(data.get('accepted', 0), data.get('duplicates_skipped', 0), data.get('errors', []), True)
            else:
                return SyncResult(0, 0, [], False)
        except Exception as e:
            return SyncResult(0, 0, [str(e)], False)

    def is_backend_reachable(self) -> bool:
        try:
            res = requests.get(f"{self.api_url}/actuator/health", timeout=5)
            return res.status_code == 200
        except Exception:
            return False
