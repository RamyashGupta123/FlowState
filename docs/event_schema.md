# FlowState — Event Schema Reference

## Overview

Every platform agent (Linux, Browser, Android, Windows) emits events using the **Unified ActivityEvent Schema**. Events are normalized locally by each agent before being buffered to SQLite and synchronized to the backend.

Schema version: **1.0**  
Schema file: [`shared/schemas/activity_event.schema.json`](../shared/schemas/activity_event.schema.json)

---

## Design Principles

1. **Minimization first.** Only fields required for classification and insights are collected.
2. **Derived over raw.** Store derived, privacy-safe summaries (e.g., `idle_ratio`) instead of raw input streams.
3. **Separation of concerns.** Raw agent signals are processed locally; only minimized events reach the backend.
4. **No sensitive content.** Window titles, if included, are scrubbed. Full URLs, keystrokes, clipboard contents, message bodies, and page contents are never collected.

---

## Field Reference

| Field | Type | Required | Description |
|---|---|---|---|
| `event_id` | UUID | ✅ | Globally unique (UUID v4). Used for deduplication. |
| `user_id` | UUID | ✅ | Set by backend on ingestion. |
| `device_id` | UUID | ✅ | Identifies the agent that recorded this event. |
| `platform` | enum | ✅ | `linux`, `android`, `browser`, `windows`, `macos` |
| `timestamp` | ISO-8601 UTC | ✅ | When the event was recorded. |
| `timezone` | IANA string | ✅ | Local timezone at recording time. |
| `app_identifier` | string | — | Platform app ID (`code`, `org.mozilla.firefox`). Null for browser events. |
| `app_name` | string | — | Display name, max 256 chars. |
| `window_title_summary` | string | — | Privacy-scrubbed summary only. **Omit if scrubbing cannot be guaranteed.** |
| `browser_domain` | hostname | — | Registered domain only (e.g., `github.com`). No path or params. |
| `browser_tab_title_category` | enum | — | Coarse category from tab title — never the raw title. |
| `session_start` | ISO-8601 UTC | ✅ | When this activity interval began. |
| `session_end` | ISO-8601 UTC | ✅ | When this activity interval ended. |
| `duration_seconds` | integer | ✅ | 1–86400. |
| `idle_seconds` | integer | — | Idle time within this session. |
| `app_switch_count` | integer | — | Context-switching indicator. |
| `declared_goal_id` | UUID | — | Active goal at recording time. |
| `focus_session_id` | UUID | — | Active focus session at recording time. |
| `activity_category` | enum | — | Set by classifier. Null until classified. |
| `goal_alignment` | enum | — | `aligned`, `neutral`, `misaligned`, `unknown`. |
| `classification_confidence` | float [0,1] | — | Classifier confidence. |
| `classification_source` | enum | — | `rules`, `ml_model`, `user_override`. |
| `classification_evidence` | string[] | — | Human-readable reasons for the classification. |
| `requires_user_confirmation` | bool | — | True when confidence < threshold. |
| `user_feedback` | object | — | User correction of the classification. |
| `context_features` | object | — | Derived features used for classification. |
| `sync_status` | enum | ✅ | `pending`, `synced`, `failed`, `excluded`. |
| `agent_version` | string | — | Agent version for debugging. |
| `schema_version` | string | ✅ | Always `"1.0"`. |

---

## Activity Categories

| Category | Description |
|---|---|
| `productive_work` | Active work in development tools, document editors, data tools |
| `learning_research` | Educational content, documentation, research |
| `communication_admin` | Email, calendar, messaging (work-related) |
| `intentional_leisure` | Entertainment, social media during planned downtime |
| `break_idle` | System idle, explicit break periods |
| `potential_distraction` | Off-goal activity during an active focus session |
| `unknown` | Insufficient evidence — user confirmation recommended |

> [!IMPORTANT]
> No application is permanently classified as productive or distracting. Classification depends on the user's active goal, focus session state, time of day, and historical preferences.

---

## Example Events

### Linux — Productive coding session

```json
{
  "event_id": "550e8400-e29b-41d4-a716-446655440000",
  "user_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "device_id": "d1e2f3a4-b5c6-7890-dcba-fedcba987654",
  "platform": "linux",
  "timestamp": "2026-10-10T16:30:00Z",
  "timezone": "Asia/Kolkata",
  "app_identifier": "code",
  "app_name": "Visual Studio Code",
  "window_title_summary": null,
  "browser_domain": null,
  "session_start": "2026-10-10T16:00:00Z",
  "session_end": "2026-10-10T16:30:00Z",
  "duration_seconds": 1800,
  "idle_seconds": 120,
  "app_switch_count": 3,
  "declared_goal_id": "goal-uuid",
  "focus_session_id": "session-uuid",
  "activity_category": "productive_work",
  "goal_alignment": "aligned",
  "classification_confidence": 0.91,
  "classification_source": "ml_model",
  "classification_evidence": [
    "App 'code' is in the 'development' category",
    "Focus session active",
    "Idle ratio 6.7% — sustained engagement"
  ],
  "requires_user_confirmation": false,
  "sync_status": "synced",
  "schema_version": "1.0"
}
```

### Browser — Ambiguous YouTube session

```json
{
  "event_id": "660e9500-f30c-52e5-b827-557766551111",
  "platform": "browser",
  "browser_domain": "youtube.com",
  "browser_tab_title_category": "video",
  "duration_seconds": 300,
  "activity_category": "unknown",
  "classification_confidence": 0.42,
  "classification_evidence": [
    "Domain 'youtube.com' is ambiguous — could be tutorial or entertainment",
    "No focus session active",
    "Short session — insufficient evidence for confident classification"
  ],
  "requires_user_confirmation": true,
  "schema_version": "1.0"
}
```

### Linux — Idle / break period

```json
{
  "event_id": "770fa600-a41d-63f6-c938-668877662222",
  "platform": "linux",
  "app_identifier": null,
  "app_name": null,
  "duration_seconds": 600,
  "idle_seconds": 590,
  "activity_category": "break_idle",
  "classification_confidence": 0.95,
  "classification_source": "rules",
  "classification_evidence": [
    "Idle ratio 98.3% — system effectively idle"
  ],
  "requires_user_confirmation": false,
  "schema_version": "1.0"
}
```
