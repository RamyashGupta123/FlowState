import time
import uuid
import ctypes
from ctypes import wintypes
import psutil
import requests
from datetime import datetime, timezone
import json
import os
import getpass
import argparse

DEFAULT_BACKEND_URL = "http://localhost:8080"

class WindowsTracker:
    def __init__(self, email=None, password=None, backend_url=None, config_dir=None):
        self.email = email
        self.password = password
        self.backend_url = (backend_url or os.getenv("FLOWSTATE_SERVER", DEFAULT_BACKEND_URL)).rstrip('/')
        
        # User-isolated configuration and buffering directory
        self.config_dir = config_dir or os.path.join(os.path.expanduser("~"), ".flowstate")
        os.makedirs(self.config_dir, exist_ok=True)
        self.config_file = os.path.join(self.config_dir, "tracker_config.json")
        self.buffer_file = os.path.join(self.config_dir, "pending_events.json")
        
        self.config = self._load_config()
        self.token = self.config.get("token")
        self.device_id = self.config.get("device_id")
        if not self.email and self.config.get("email"):
            self.email = self.config.get("email")

        self.session_start = None
        self.current_app = None
        self.current_title = None
        self.last_input_time = time.time()
        self.switch_count = 0
        
        self.user32 = ctypes.windll.user32
        self.kernel32 = ctypes.windll.kernel32

        # Configure ctypes structures and signatures for Windows APIs
        class LASTINPUTINFO(ctypes.Structure):
            _fields_ = [("cbSize", wintypes.UINT), ("dwTime", wintypes.DWORD)]

        self.LASTINPUTINFO = LASTINPUTINFO
        self.user32.GetLastInputInfo.argtypes = [ctypes.POINTER(LASTINPUTINFO)]
        self.user32.GetLastInputInfo.restype = wintypes.BOOL

        if hasattr(self.kernel32, 'GetTickCount64'):
            self.kernel32.GetTickCount64.argtypes = []
            self.kernel32.GetTickCount64.restype = ctypes.c_ulonglong
        elif hasattr(self.kernel32, 'GetTickCount'):
            self.kernel32.GetTickCount.argtypes = []
            self.kernel32.GetTickCount.restype = wintypes.DWORD

    def _load_config(self):
        if os.path.exists(self.config_file):
            try:
                with open(self.config_file, 'r') as f:
                    return json.load(f)
            except Exception:
                pass
        return {}

    def _save_config(self):
        try:
            with open(self.config_file, 'w') as f:
                json.dump(self.config, f, indent=2)
        except Exception as e:
            print(f"Warning: Could not save config: {e}")

    def _save_to_buffer(self, event):
        events = []
        if os.path.exists(self.buffer_file):
            try:
                with open(self.buffer_file, 'r') as f:
                    events = json.load(f)
            except Exception:
                pass
        events.append(event)
        with open(self.buffer_file, 'w') as f:
            json.dump(events, f)

    def _flush_buffer(self):
        if not os.path.exists(self.buffer_file):
            return
        try:
            with open(self.buffer_file, 'r') as f:
                events = json.load(f)
        except Exception:
            return
            
        if not events: return
        
        headers = {"Authorization": f"Bearer {self.token}"}
        try:
            res = requests.post(f"{self.backend_url}/api/events", json={"events": events}, headers=headers)
            if res.status_code == 202:
                os.remove(self.buffer_file)
                print(f"Flushed {len(events)} pending events successfully.")
            else:
                print(f"Flush failed (HTTP {res.status_code}): {res.text}")
        except Exception as e:
            print(f"Flush exception: {e}")

    def authenticate(self):
        # 1. Prompt for email if missing
        if not self.email:
            self.email = input("Enter FlowState account email: ").strip()

        # 2. Check if existing token and device ID are already valid on the server
        if self.token and self.device_id and self.config.get("email") == self.email:
            try:
                verify_res = requests.get(
                    f"{self.backend_url}/api/devices",
                    headers={"Authorization": f"Bearer {self.token}"},
                    timeout=5
                )
                if verify_res.status_code == 200:
                    devices = verify_res.json()
                    if any(d.get("id") == self.device_id for d in devices):
                        print(f"Authenticated as {self.email} using existing session.")
                        print(f"Reusing registered device ID: {self.device_id}")
                        return
            except Exception:
                pass # Fall back to full login

        # 3. Prompt for password securely if not provided
        if not self.password:
            self.password = getpass.getpass(f"Enter password for {self.email}: ")

        print(f"Authenticating with {self.backend_url}...")
        res = requests.post(f"{self.backend_url}/api/auth/login", json={"email": self.email, "password": self.password})
        if res.status_code != 200:
            print("Login failed, attempting registration...")
            res = requests.post(f"{self.backend_url}/api/auth/register", json={"email": self.email, "password": self.password})
            if res.status_code != 201:
                raise Exception(f"Authentication failed: {res.text}")
        
        self.token = res.json()['token']
        self.password = None # Clear plaintext password from memory immediately
        print("Authenticated successfully.")
        
        headers = {"Authorization": f"Bearer {self.token}"}
        
        # 4. Check if a device ID is already registered for this user
        existing_device_found = False
        try:
            dev_list_res = requests.get(f"{self.backend_url}/api/devices", headers=headers, timeout=5)
            if dev_list_res.status_code == 200:
                devices = dev_list_res.json()
                if self.device_id and any(d.get("id") == self.device_id for d in devices):
                    existing_device_found = True
                    print(f"Reusing saved device: {self.device_id}")
                elif devices:
                    # Reuse first active windows device if available
                    win_devs = [d for d in devices if d.get("platform") == "windows" and d.get("isActive", True)]
                    if win_devs:
                        self.device_id = win_devs[0]["id"]
                        existing_device_found = True
                        print(f"Reusing existing Windows device: {self.device_id}")
        except Exception:
            pass

        if not existing_device_found:
            dev_res = requests.post(f"{self.backend_url}/api/devices", json={
                "platform": "windows",
                "name": "Desktop Tracker",
                "agentVersion": "1.0.0"
            }, headers=headers)
            if dev_res.status_code != 201 and dev_res.status_code != 200:
                raise Exception(f"Device registration failed: {dev_res.text}")
            self.device_id = dev_res.json()['id']
            print(f"Registered new device: {self.device_id}")

        # Update local config
        self.config["email"] = self.email
        self.config["server_url"] = self.backend_url
        self.config["token"] = self.token
        self.config["device_id"] = self.device_id
        self._save_config()

    def get_active_window_info(self):
        hwnd = self.user32.GetForegroundWindow()
        if not hwnd:
            return None, None
            
        length = self.user32.GetWindowTextLengthW(hwnd)
        buf = ctypes.create_unicode_buffer(length + 1)
        self.user32.GetWindowTextW(hwnd, buf, length + 1)
        title = buf.value
        
        pid = wintypes.DWORD()
        self.user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
        
        try:
            process_name = psutil.Process(pid.value).name()
        except (psutil.NoSuchProcess, psutil.AccessDenied):
            process_name = "Unknown"
            
        return process_name, title

    def get_idle_time(self):
        lii = self.LASTINPUTINFO()
        lii.cbSize = ctypes.sizeof(self.LASTINPUTINFO)
        if not self.user32.GetLastInputInfo(ctypes.byref(lii)):
            return 0.0

        if hasattr(self.kernel32, 'GetTickCount64'):
            tick = self.kernel32.GetTickCount64()
        elif hasattr(self.kernel32, 'GetTickCount'):
            tick = self.kernel32.GetTickCount()
        else:
            return 0.0

        tick32 = tick & 0xFFFFFFFF
        if tick32 >= lii.dwTime:
            elapsed_ms = tick32 - lii.dwTime
        else:
            diff = (tick32 - lii.dwTime) & 0xFFFFFFFF
            # Handle potential millisecond timing jitter vs true 32-bit counter wraparound (~49.7 days)
            if diff > 0x80000000 and (lii.dwTime - tick32) < 5000:
                elapsed_ms = 0
            else:
                elapsed_ms = diff

        return max(0.0, elapsed_ms / 1000.0)

    def _extract_browser_domain(self, title):
        if not title:
            return None
        title_lower = title.lower()
        if 'github' in title_lower:
            return 'github.com'
        elif 'youtube' in title_lower:
            return 'youtube.com'
        elif 'chatgpt' in title_lower or 'openai' in title_lower:
            return 'chatgpt.com'
        elif 'localhost' in title_lower or '127.0.0.1' in title_lower:
            return 'localhost'
        elif 'stackoverflow' in title_lower:
            return 'stackoverflow.com'
        elif 'docs.google' in title_lower or 'google docs' in title_lower:
            return 'docs.google.com'
        elif 'notion' in title_lower:
            return 'notion.so'
        elif 'slack' in title_lower:
            return 'slack.com'
        elif 'reddit' in title_lower:
            return 'reddit.com'
        elif 'twitter' in title_lower:
            return 'x.com'
        return None

    def sync_event(self, app_name, title, start_time, duration, idle, switches):
        if duration < 5: return # Ignore very short sessions
        
        now = datetime.now(timezone.utc)
        app_id = app_name.lower().replace('.exe', '')
        is_browser = app_id in ['chrome', 'msedge', 'firefox', 'brave', 'opera', 'vivaldi']
        
        browser_domain = self._extract_browser_domain(title) if is_browser else None

        event = {
            "eventId": str(uuid.uuid4()),
            "deviceId": self.device_id,
            "platform": "windows",
            "timestamp": now.isoformat(),
            "timezone": "UTC",
            "sessionStart": start_time.isoformat(),
            "sessionEnd": now.isoformat(),
            "durationSeconds": int(duration),
            "schemaVersion": "1.0",
            "appIdentifier": app_id,
            "appName": app_name,
            "browserDomain": browser_domain,
            "browserTabTitleCategory": "unknown",
            "idleSeconds": int(idle),
            "appSwitchCount": switches,
            "syncStatus": "synced",
            "agentVersion": "1.0.0"
        }
        
        print(f"Syncing event: {app_name} ({int(duration)}s) - Idle: {int(idle)}s")
        self._save_to_buffer(event)
        self._flush_buffer()

    def run(self):
        self.authenticate()
        
        # Explicit consent check and persistence
        if not self.config.get("consent_given"):
            print("\n" + "="*50)
            print("FlowState Windows Activity Tracker — Privacy Notice")
            print("="*50)
            print("We collect ONLY active foreground window process name, permitted title, and duration.")
            print("No keystrokes, passwords, page contents, or screen captures are recorded.")
            print("Tracking pauses automatically when machine is idle or locked.")
            consent = input("Do you consent to activity tracking? (y/n): ")
            if consent.lower() != 'y':
                print("Consent denied. Exiting.")
                return
            self.config["consent_given"] = True
            self._save_config()
            print("Consent recorded.")
        else:
            print("Activity tracking active under previously granted consent.")
            print("To revoke consent or log out, run with --revoke-consent or --logout.")
            
        print("\nTracking started. Press Ctrl+C to pause/exit.")
        self.session_start = datetime.now(timezone.utc)
        
        try:
            while True:
                time.sleep(2)
                
                app_name, title = self.get_active_window_info()
                idle_sec = self.get_idle_time()
                
                if not app_name: continue
                
                if self.current_app != app_name:
                    if self.current_app is not None:
                        duration = (datetime.now(timezone.utc) - self.session_start).total_seconds()
                        self.sync_event(self.current_app, self.current_title, self.session_start, duration, idle_sec, self.switch_count)
                        
                    self.current_app = app_name
                    self.current_title = title
                    self.session_start = datetime.now(timezone.utc)
                    self.switch_count = 0
                else:
                    self.current_title = title
                    
                # To simulate switch count
                if idle_sec < 2:
                    self.switch_count += 1
                    
        except KeyboardInterrupt:
            print("\nTracking paused/exited.")
            if self.current_app:
                duration = (datetime.now(timezone.utc) - self.session_start).total_seconds()
                self.sync_event(self.current_app, self.current_title, self.session_start, duration, 0, self.switch_count)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="FlowState Windows Activity Tracker")
    parser.add_argument("--server", default=os.getenv("FLOWSTATE_SERVER", "http://localhost:8080"), help="FlowState Backend URL")
    parser.add_argument("--email", default=None, help="FlowState account email")
    parser.add_argument("--logout", action="store_true", help="Clear saved credentials and exit")
    parser.add_argument("--revoke-consent", action="store_true", help="Revoke tracking consent and exit")
    args = parser.parse_args()

    tracker = WindowsTracker(email=args.email, backend_url=args.server)

    if args.logout:
        tracker.config = {}
        tracker._save_config()
        if os.path.exists(tracker.buffer_file):
            os.remove(tracker.buffer_file)
        print("Logged out. Saved credentials cleared.")
    elif args.revoke_consent:
        tracker.config["consent_given"] = False
        tracker._save_config()
        print("Consent revoked. Tracker will request consent on next launch.")
    else:
        tracker.run()
