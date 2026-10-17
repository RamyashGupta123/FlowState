# FlowState — Setup Guide

## Prerequisites

| Tool | Required by | Minimum Version |
|---|---|---|
| Java JDK | Backend | 17 |
| Docker Desktop | PostgreSQL, ML service | Latest |
| Python | ML service, Linux agent | 3.11 |
| Node.js | Browser extension | 18 |
| Flutter SDK | Flutter dashboard | 3.x (see below) |

---

## 1. Clone and Configure

```bash
git clone <repo-url>
cd flowstate
cp .env.example .env
```

Edit `.env` and set at minimum:
- `POSTGRES_PASSWORD` — a strong random password
- `JWT_SECRET` — a base64-encoded 256-bit random string (e.g., `openssl rand -base64 32`)

---

## 2. Start PostgreSQL

```bash
# Start Docker Desktop first
cd infrastructure/docker
docker compose up -d postgres

# Verify it's healthy
docker compose ps
```

---

## 3. Backend (Spring Boot)

```bash
cd backend/spring_boot
# First run — Maven Wrapper downloads Maven automatically
./mvnw spring-boot:run

# Or on Windows:
mvnw.cmd spring-boot:run
```

Flyway migrations run automatically on startup. The API is available at `http://localhost:8080`.

**API documentation:** `http://localhost:8080/swagger-ui.html`

### Run backend tests

```bash
cd backend/spring_boot
./mvnw test
```

---

## 4. ML Classifier Service

```bash
cd ml
pip install -r requirements.txt

# Train the classifier on synthetic development data
python -m classifier.train

# Start the FastAPI service
python -m classifier.server
# Service at http://localhost:8001
# API docs at http://localhost:8001/docs
```

### Run ML tests

```bash
cd ml
pytest tests/ -v
```

---

## 5. Linux Agent

> **Platform requirement:** Linux with X11 or Wayland + `xdotool` / `wmctrl`.
> On Windows or macOS this agent does not run — use the browser extension instead.

```bash
cd agents/linux
pip install -r requirements.txt
cp config.yaml.example config.yaml
```

Edit `config.yaml`:
```yaml
api_url: http://localhost:8080
poll_interval_seconds: 30
consent_given: false  # Set to true after reading the privacy notice
exclusions:
  apps: []
  browser_domains: []
```

First run shows the consent notice:
```bash
python agent.py
```

---

## 6. Browser Extension

```bash
cd extensions/browser
npm install
npm test
```

Load in Chrome:
1. Open `chrome://extensions`
2. Enable **Developer mode** (top right)
3. Click **Load unpacked** → select `extensions/browser/`
4. Click the FlowState icon → accept the consent flow
5. Set your backend URL in the extension settings (default: `http://localhost:8080`)

---

## 7. Flutter Dashboard (Pending Flutter Installation)

Flutter is not yet installed in this environment. To install Flutter:

### Windows Installation

1. Download Flutter SDK: https://docs.flutter.dev/get-started/install/windows
2. Extract to `C:\flutter`
3. Add `C:\flutter\bin` to your PATH
4. Run `flutter doctor` to verify

### Run the Flutter app (after installation)

```bash
cd apps/flutter_app
flutter pub get
flutter run -d windows  # or -d linux, -d chrome
```

The Flutter app connects to the backend at the URL configured in `apps/flutter_app/lib/config/app_config.dart`.

---

## Known Limitations

### Platform
- **Linux agent:** Wayland restricts window title access. Process name is always available; title may be empty on Wayland.
- **Android agent:** Not implemented in MVP. Android activity tracking requires Usage Access permission granted by the user in system settings.
- **macOS agent:** Not implemented in MVP.

### Classification
- The ML classifier is trained on **synthetic development data**. Classification accuracy will improve as real user feedback is collected.
- The rule-based fallback is used when ML confidence is below 0.6.
- Ambiguous apps and domains (e.g., YouTube, Chrome) frequently return `unknown` category pending user confirmation.

### Data
- Events require a registered device. The device token must be configured in each agent.
- The browser extension and Linux agent use independent device registrations.

---

## Troubleshooting

| Problem | Solution |
|---|---|
| `Connection refused` on backend | Ensure Docker is running and PostgreSQL is healthy: `docker compose ps` |
| `Flyway migration failed` | Check PostgreSQL credentials in `.env` match `docker-compose.yml` |
| `JWT_SECRET not set` | Set `JWT_SECRET` in `.env` — the backend will refuse to start without it |
| Linux agent: no window detected | Install `xdotool`: `sudo apt install xdotool` |
| Browser extension CORS error | Ensure backend URL in extension settings matches `SERVER_PORT` in `.env` |
