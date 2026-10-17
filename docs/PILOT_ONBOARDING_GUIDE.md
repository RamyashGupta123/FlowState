# FlowState Digital Wellbeing Platform — Participant Onboarding Guide

Welcome to the FlowState private pilot! FlowState is an open, privacy-conscious digital wellbeing platform designed to help you understand your focus, screen habits, and deep work patterns.

---

## 1. Privacy Guarantees & Tracking Boundaries

Your privacy and data ownership are the foundational principles of FlowState:

### What FlowState Collects
* **Active Window / Application Name**: Process identifier of the foreground window (e.g., `code.exe`, `slack.exe`).
* **Browser Domains**: Only the high-level domain you visit (e.g., `github.com`, `stackoverflow.com`).
* **Active Duration & Idle Time**: Number of seconds spent active in an app, and idle seconds when you step away.

### What FlowState NEVER Collects
* **NO Keystrokes or Form Input**: No keyboard logging of any kind.
* **NO Passwords, Tokens, or Credentials**: Form fields and secrets are never captured or stored.
* **NO Screenshots or Screen Recording**: No visual screen captures are ever taken.
* **NO Full URLs with Query Strings**: URLs are stripped to top-level domains to prevent accidental leakage of sensitive tokens.
* **NO Background Tab or Inactive Window Snooping**: Only the single active foreground window is tracked.

---

## 2. Step 1: Create Your Account

1. Open your browser and navigate to the FlowState Web Dashboard:
   ```
   http://<server-host>:8081
   ```
   *(For local testing on the same machine, visit `http://localhost:8081`)*
2. If your server is hosted on a remote IP (e.g. `http://192.168.1.50:8080/api`), click **Server: ...** at the bottom of the login card to set the API address.
3. Click **Need an account? Register**.
4. Enter your email and password, then click **Register**.

---

## 3. Step 2: Install the Chrome Browser Extension

The Chrome extension tracks domain focus times in your browser and syncs them periodically.

### Installation via Developer Mode
1. Ensure you have the `flowState/extensions/browser` directory downloaded or unzipped on your machine.
2. Open Google Chrome and navigate to:
   ```
   chrome://extensions
   ```
3. In the top right corner, toggle **Developer mode** to **ON**.
4. Click the **Load unpacked** button in the top left corner.
5. In the file picker, select the `extensions/browser` folder.
6. The **FlowState** extension icon will appear in your Chrome toolbar. Click the puzzle icon to pin FlowState for easy access.

### Connecting the Extension
1. Click the FlowState extension icon.
2. Read the **Privacy Notice** and click **I Consent**.
3. In the login screen:
   * **Server URL**: Enter your server address (default: `http://localhost:8080`).
   * **Email**: Enter your registered FlowState email.
   * **Password**: Enter your account password.
4. Click **Connect & Login**.
5. Once connected, the status dot turns green. Your device is registered, and domain metrics will sync automatically every 5 minutes (or immediately via **Sync Now**).

### How to Pause or Stop Browser Tracking
* **To Pause**: Click the FlowState extension icon and click **Pause**. The status dot turns yellow, and all recording stops.
* **To Resume**: Click **Pause** again (which now displays as unpaused).
* **To Disconnect**: Click **Logout**. All local tokens and device identifiers are wiped immediately.

---

## 4. Step 3: Run the Windows Activity Tracker (Optional)

The Windows tracker tracks desktop application time (e.g., VS Code, Word, Terminal).

### Launching the Tracker
1. Open the folder:
   ```
   flowState\agents\windows\
   ```
2. Double-click **`run_tracker.bat`**.
3. The launcher will check Python and automatically install `psutil` and `requests` if needed.
4. Select option **1** (Normal launch) or option **2** (Custom server URL).
5. Enter your email and password securely when prompted (passwords are hidden).
6. Read the on-screen privacy guarantee and type **`y`** to grant tracking consent.
7. The tracker will confirm device registration and begin tracking.

### How to Pause or Stop Desktop Tracking
* **To Pause/Exit**: Press **`Ctrl + C`** in the terminal window. Any pending session duration will be safely flushed to the backend before exit.
* **To Revoke Consent**: Run `run_tracker.bat` and select Option **3** (`--revoke-consent`).
* **To Log Out**: Run `run_tracker.bat` and select Option **4** (`--logout`). Local credentials and offline queues are deleted.

---

## 5. Step 4: Using Your Dashboard & Managing Data

### Timeline & Feedback
* Open `http://<server-host>:8081` and select the **Timeline** tab.
* Inspect your daily activities and predicted categories (`development`, `learning_research`, `social`, etc.).
* If a category is incorrect, tap the event to provide feedback. Your feedback overrides the classification and improves future predictions.

### Focus Sessions
* On the **Dashboard**, use the **Focus Session** card.
* Select a target duration (15, 25, 45, or 60 minutes) and enter an optional goal.
* Tap **Start Focus Session**. A live timer will tick down. You can **Pause**, **Resume**, or **Complete** the session at any time.

### Data Export (GDPR / CCPA)
* Navigate to the **Settings** tab.
* Click **Data Export (JSON)**.
* An inspection dialog will show your complete data summary (registered devices, focus sessions, and chronological events). You can verify that passwords and secrets are strictly excluded.
