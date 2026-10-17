# FlowState

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green.svg)](https://spring.io/projects/spring-boot)
[![Python](https://img.shields.io/badge/Python-3.11-blue.svg)](https://www.python.org/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.100+-teal.svg)](https://fastapi.tiangolo.com/)
[![Flutter](https://img.shields.io/badge/Flutter-3.x-blue.svg)](https://flutter.dev/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Scikit-Learn](https://img.shields.io/badge/Scikit--Learn-1.3+-orange.svg)](https://scikit-learn.org/)
[![Manifest V3](https://img.shields.io/badge/Chrome%20Extension-MV3-yellow.svg)](https://developer.chrome.com/docs/extensions/mv3/intro/)

**FlowState** is a privacy-first, context-aware digital wellbeing and focus optimization platform. Unlike conventional screen-time trackers that only measure raw application duration, FlowState understands *what* you are doing and *why*—distinguishing between focused work, learning/research, communication, breaks, and distractions using machine learning and contextual evidence.

The platform pairs cross-platform activity capture with an **Adaptive Wellbeing Recommendation Engine** that delivers bounded, explainable micro-interventions (mindfulness breathing, physical stretches, screen rest, and hydration breaks) while respecting user focus sessions, quiet hours, and fatigue thresholds.

---

## Key Features

- **Context-Aware Classification**: Combines rule-based heuristics with a Random Forest ML classifier to categorize active windows and browser domains based on duration, idle ratio, switch rates, time of day, and declared goals.
- **Adaptive Wellbeing Recommendations**:
  - Delivers 4 evidence-backed interventions: *Breathing exercise (1 min)*, *Desk stretch (2 min)*, *20-20-20 screen rest (30s)*, and *Movement break (5 min)*.
  - Contextual suppression: Automatically defers prompts during active Focus Sessions, declared breaks, and quiet hours.
  - Fatigue tracking: Measures continuous productive minutes and triggers reminders based on user preferences.
  - Adaptive feedback loop: Integrates user feedback (helpful, not helpful, snooze) to dynamically tune future recommendation timing.
  - Development Demo Controls: Interactive scenario testing directly in the UI without altering underlying database integrity.
- **Structured Focus Sessions**:
  - Goal-oriented sessions with customizable durations (15m, 25m Pomodoro, 45m, 60m).
  - Live session timers, state management (active, paused, completed, abandoned), and interruption tracking.
- **Multi-Device Activity Capture**:
  - **Chrome Extension (Manifest V3)**: Tracks active tab domains and title categories with client-side domain exclusion lists and JWT-authenticated batch syncing.
  - **Windows Activity Tracker**: Native desktop daemon with Win32 `GetLastInputInfo` idle detection, process name mapping, and local event buffering.
  - **Linux Agent**: X11-based window and idle monitoring with SQLite local buffering.
- **Strict Multi-User Privacy & Isolation**:
  - Device ownership validation: Every event ingestion request cryptographically validates that the device belongs to the authenticated user.
  - Local-first filtering: No keystrokes, clipboard data, window screenshots, or private browser URLs (`chrome://`, incognito) are ever recorded.
  - Full data export and account deletion endpoints under GDPR-aligned principles.

---

## Architecture

```
[ Chrome Extension (MV3) ]   [ Windows Native Tracker ]   [ Linux Agent (X11) ]
         │                               │                         │
         └─── Local Buffer & Privacy ────┴───────────┬─────────────┘
                                                     │ HTTP / REST (JWT Auth)
                                                     ▼
                                      ┌──────────────────────────────┐
                                      │   Spring Boot Backend API    │
                                      │   (Port 8080)                │
                                      └───────┬──────────────┬───────┘
                                              │              │
                     Flyway SQL Migrations   │              │ Classification Request
                                              ▼              ▼
                                     ┌────────────────┐  ┌───────────────────────┐
                                     │   PostgreSQL   │  │   FastAPI ML Service  │
                                     │   (Port 5432)  │  │   (Port 8001)         │
                                     └────────────────┘  │   • Feature Pipeline  │
                                              ▲          │   • Random Forest     │
                                              │          └───────────────────────┘
                                              │
                                     ┌────────────────┐
                                     │  Flutter Web   │
                                     │  Dashboard     │
                                     │  (Port 8081)   │
                                     └────────────────┘
```

---

## Technology Stack

| Layer | Technologies | Key Responsibilities |
| :--- | :--- | :--- |
| **Backend API** | Java 17, Spring Boot 3.2, Spring Security, Spring Data JPA, Flyway | REST API, JWT authentication, device ownership validation, activity ingestion, focus sessions, recommendation engine |
| **Database** | PostgreSQL 16 (Docker) | Relational schema with 7 versioned Flyway migrations, indexing on user timestamps and activity categories |
| **ML Classifier** | Python 3.11, FastAPI, Scikit-Learn, Pandas, NumPy, Joblib | Context feature extraction, Random Forest classification, rule-based fallback, confidence scoring |
| **Frontend UI** | Flutter 3.x, Dart | Web dashboard, live timeline, focus timer, adaptive recommendation cards, settings, and auth management |
| **Browser Extension** | JavaScript (ES modules), Chrome Manifest V3 | Tab active time monitoring, domain extraction, privacy filters, batch syncing |
| **Desktop Trackers** | Python 3.10+, Win32 ctypes (`user32.dll`), `psutil`, SQLite | Foreground window detection, user idle timing, offline event buffering, auto-registration |

---

## Repository Structure

```
flowstate/
├── agents/
│   ├── linux/               # Linux Python monitoring agent (X11, SQLite buffer)
│   └── windows/             # Windows Python daemon (Win32 API idle tracking)
├── apps/
│   └── flutter_app/         # Flutter web dashboard and multi-screen UI
├── backend/
│   └── spring_boot/         # Spring Boot 3.2 backend (REST, Security, JPA, Flyway)
├── docs/                    # Architecture, API contract, setup, and pilot onboarding guides
├── extensions/
│   └── browser/             # Chrome Manifest V3 extension
├── infrastructure/
│   └── docker/              # Docker Compose definitions for PostgreSQL and ML service
├── ml/                      # Python FastAPI ML classification service
├── shared/
│   ├── api_contracts/       # OpenAPI 3.0 specification
│   └── schemas/             # Unified Activity Event JSON Schema (v1.0)
├── .env.example             # Environment variable template
└── .gitignore
```

---

## Getting Started

### Prerequisites

- **Java Development Kit (JDK) 17+**
- **Python 3.10 or 3.11**
- **Node.js 18+** (for browser extension tests)
- **Docker Desktop** (for PostgreSQL)
- **Flutter SDK 3.x** (for the web dashboard)

---

### Step 1: Environment Configuration

Copy the template environment file:
```bash
cp .env.example .env
```
The application comes preconfigured with local development defaults (`application.yml` and `docker-compose.yml`), so you can run the services immediately without editing `.env`.

---

### Step 2: Start PostgreSQL Database

From the repository root, start the containerized PostgreSQL instance:
```bash
cd infrastructure/docker
docker compose up -d postgres
```
Verify the container is healthy:
```bash
docker ps --filter "name=flowstate-postgres"
```

---

### Step 3: Run the Spring Boot Backend

The backend includes a self-contained Maven Wrapper (`mvnw` / `mvnw.cmd`):

```bash
cd backend/spring_boot

# On Windows:
.\mvnw.cmd spring-boot:run

# On Linux / macOS:
./mvnw spring-boot:run
```
- Flyway migrations (`V1` through `V7`) execute automatically on startup.
- **REST API**: `http://localhost:8080`
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **Health Check**: `http://localhost:8080/actuator/health`

---

### Step 4: Run the FastAPI ML Service

In a new terminal:
```bash
cd ml
pip install -r requirements.txt
python -m classifier.server
```
- **ML Service**: `http://localhost:8001`
- **Health Check**: `http://localhost:8001/health`

---

### Step 5: Run the Flutter Web Dashboard

In a new terminal:
```bash
cd apps/flutter_app
flutter pub get
flutter run -d web-server --web-port 8081 --web-hostname localhost
```
Open `http://localhost:8081` in your browser. Register an account or log in with demo credentials.

---

### Step 6: Install the Chrome Browser Extension

1. Open Google Chrome and navigate to `chrome://extensions`.
2. Enable **Developer mode** (toggle in the top-right corner).
3. Click **Load unpacked** and select the `extensions/browser/` folder.
4. Click the **FlowState** puzzle icon in Chrome:
   - Configure Backend URL (default: `http://localhost:8080`).
   - Log in with your FlowState account credentials.
   - Accept the explicit privacy consent notice to begin syncing browser tabs.

---

### Step 7: Run Desktop Tracking (Windows)

To track native desktop applications on Windows:
```bash
cd agents/windows
run_tracker.bat
```
The tracker prompts for your account email and password on first launch, registers your device securely, and runs in the background.

---

## Automated Verification & Test Coverage

All core services have comprehensive automated test suites verifying functional accuracy, security constraints, and data integrity:

| Subsystem | Test Suite | Pass Count | Scope & Coverage |
| :--- | :--- | :---: | :--- |
| **Spring Boot Backend** | `.\mvnw.cmd test` | **19 / 19** | Device ownership validation, JWT auth, focus session state machine, continuous work calculation, recommendation eligibility and cooldowns |
| **FastAPI ML Service** | `pytest tests/ -v` | **8 / 8** | Feature extraction pipeline, duration bucketing, Random Forest classification, rule-based fallback, REST endpoints |
| **Flutter Dashboard** | `flutter analyze lib`<br>`flutter test` | **0 Warnings**<br>**1 / 1** | Static analyzer verification across all screens, models, and API clients; widget authentication tests |
| **Browser Extension** | `npm test` | **6 / 6** | API client batch sync, domain parser, URL protection filter, privacy exclusion lists |
| **Windows Tracker** | `pytest test_tracker.py -v` | **8 / 8** | Win32 idle calculation, jitter tolerance, 32-bit tick wraparound, device ID session reuse, event sync |
| **Linux Agent** | `pytest tests/ -v` | **5 / 7** | Domain filtering, title privacy scrub, API sync (2 SQLite buffer cleanup tests exhibit Windows-specific `WinError 32` file locks during `os.unlink`, verified on POSIX systems) |

---

## Privacy & Security Guarantees

FlowState was engineered around privacy-by-design principles:

1. **Explicit Informed Consent**: No activity tracking occurs until the user explicitly accepts the consent dialogue in the client agent or extension.
2. **Device Ownership Verification**: The backend rejects any event submission where the supplied `deviceId` does not belong to the authenticated user (HTTP 403 Forbidden).
3. **Local-First Privacy Filtering**:
   - Internal URLs (`chrome://`, `about:`, `file://`) and incognito tabs are ignored before reaching network buffers.
   - Raw window titles are scrubbed of URLs, emails, and file paths.
   - Configurable exclusion lists allow users to permanently exclude sensitive domains or apps.
4. **No Invasive Telemetry**: FlowState does not capture keystrokes, clipboard text, audio, webcam streams, or screen pixels.
5. **Data Sovereignty**: Complete user data export (`GET /api/user/export`) and irreversible account deletion (`DELETE /api/user/data`) are fully implemented.

---

## License

This project is licensed under the MIT License - see the LICENSE file for details.
