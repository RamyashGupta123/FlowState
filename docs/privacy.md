# FlowState — Privacy Model

## Core Principles

1. **Informed consent first.** No data is collected before the user has read and accepted a clear explanation of what is collected and why.
2. **Data minimization.** Only signals required for classification and insights are collected.
3. **Local-first processing.** Sensitive analysis happens on-device where feasible. Only derived, minimized events are sent to the backend.
4. **User control.** Users can pause monitoring, exclude applications or domains, export their data, and delete everything at any time.
5. **No sensitive content.** The platform never collects keystrokes, screenshots, clipboard contents, passwords, private messages, or full page contents.

---

## What Is and Is Not Collected

### Collected (with explicit consent)

| Signal | Purpose | Where processed |
|---|---|---|
| Foreground application name and process ID | Activity classification | Agent (local) |
| Active browser domain (no path/params) | Activity classification | Browser extension (local) |
| Session start and end times | Duration and pattern analysis | Agent → Backend |
| Idle time per session | Engagement quality | Agent (local) |
| Application switch count | Context-switching analysis | Agent (local) |
| Time of day and day of week | Pattern analysis | Backend |

### Never collected

- Full window titles (only scrubbed summaries, if at all)
- Full URLs, URL paths, or query parameters
- Page contents, article text, or DOM data
- Keystrokes or keyboard input of any kind
- Clipboard contents
- Screenshots or screen recordings
- Private messages or email contents
- Passwords or authentication tokens
- Microphone or camera data

---

## Consent and Permissions

### Linux Agent

The agent requires no special OS permissions to read the active window name.  
The user is shown a clear consent screen on first run, explaining exactly what will be collected.  
A `consent_given = true` flag is written to `config.yaml` and must be present for collection to begin.

### Browser Extension

The extension requests only:
- `tabs` — to detect active tab domain changes
- `storage` — to persist consent, session state, and exclusion list locally
- `alarms` — to trigger periodic sync

It does **not** request `webRequest`, `history`, `bookmarks`, or broad host permissions.  
A consent popup appears on first install.

### Android (future)

The Android agent will request `PACKAGE_USAGE_STATS` (Usage Access) via a user-facing settings screen.  
The user will be directed to the system settings page to grant this permission explicitly.

---

## Exclusions

Users can exclude specific applications, process names, or browser domains from collection.

```yaml
# config.yaml example
exclusions:
  apps:
    - "keepassxc"
    - "gnome-keyring"
    - "1password"
  browser_domains:
    - "banking.example.com"
    - "health.example.com"
```

Excluded events receive `sync_status: excluded` and are never sent to the backend.

---

## Data Storage

| Layer | Storage | Retention |
|---|---|---|
| Agent local buffer | SQLite (agent device) | Configurable; default 7 days |
| Backend database | PostgreSQL (self-hosted or managed) | Configurable; default 90 days |
| ML model preferences | PostgreSQL | Until user deletes account |

---

## Data Export and Deletion

- `GET /api/user/export` — returns all stored events, goals, and preferences as JSON
- `DELETE /api/user/data` — permanently deletes all events, goals, feedback, and the user account

---

## Network Security

- All backend endpoints served over HTTPS in production
- JWT tokens expire after 24 hours (configurable)
- Tokens are never logged
- Secrets are stored in environment variables, never in source control

---

## Known Limitations

- The Linux agent reads the active window title if available. Title scrubbing is applied but may not catch all sensitive content. Users can disable title collection entirely in `config.yaml`.
- On Wayland desktops, window title detection may be restricted. The agent falls back to process name only.
- The browser extension collects the registered domain only. It cannot distinguish between different pages on the same domain (e.g., different YouTube videos).
