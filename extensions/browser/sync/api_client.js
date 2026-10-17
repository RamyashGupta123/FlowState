export function parseJwt(token) {
    if (!token || typeof token !== 'string') return null;
    try {
        const parts = token.split('.');
        if (parts.length !== 3) return null;
        const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
        const pad = base64.length % 4;
        const padded = pad ? base64 + '='.repeat(4 - pad) : base64;
        const decoded = (typeof atob === 'function')
            ? atob(padded)
            : Buffer.from(padded, 'base64').toString('binary');
        return JSON.parse(decoded);
    } catch {
        return null;
    }
}

export function isTokenExpired(token) {
    const payload = parseJwt(token);
    if (!payload || !payload.exp) return true;
    return Date.now() >= payload.exp * 1000;
}

export class ApiClient {
    static async login(baseUrl, email, password) {
        const cleanUrl = baseUrl.replace(/\/$/, '');
        const res = await fetch(`${cleanUrl}/api/auth/login`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ email, password })
        });
        if (res.ok) {
            return await res.json();
        }
        const errText = await res.text();
        throw new Error(`Login failed (HTTP ${res.status}): ${errText}`);
    }

    constructor(baseUrl, token) {
        this.baseUrl = baseUrl.replace(/\/$/, '');
        this.token = token;
    }
    
    async registerDevice(platform, name, agentVersion) {
        const res = await fetch(`${this.baseUrl}/api/devices`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`
            },
            body: JSON.stringify({ platform, name, agentVersion })
        });
        if (res.ok) {
            return await res.json();
        }
        const errText = await res.text();
        throw new Error(`Device registration failed: HTTP ${res.status} - ${errText}`);
    }
    
    async syncEvents(events) {
        try {
            // Map any lingering snake_case events from storage to camelCase
            const mappedEvents = events.map(e => ({
                eventId: e.eventId || e.event_id,
                deviceId: e.deviceId || e.device_id,
                platform: e.platform,
                timestamp: e.timestamp,
                timezone: e.timezone,
                browserDomain: e.browserDomain || e.browser_domain,
                browserTabTitleCategory: e.browserTabTitleCategory || e.browser_tab_title_category,
                sessionStart: e.sessionStart || e.session_start,
                sessionEnd: e.sessionEnd || e.session_end,
                durationSeconds: e.durationSeconds || e.duration_seconds,
                appSwitchCount: e.appSwitchCount || e.app_switch_count || 0,
                syncStatus: e.syncStatus || e.sync_status,
                agentVersion: e.agentVersion || e.agent_version,
                schemaVersion: e.schemaVersion || e.schema_version
            }));
            
            const res = await fetch(`${this.baseUrl}/api/events`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${this.token}`
                },
                body: JSON.stringify({ events: mappedEvents })
            });
            if (res.ok) {
                const data = await res.json();
                return {
                    success: true,
                    accepted: data.accepted,
                    syncedEventIds: mappedEvents.map(e => e.eventId)
                };
            }
            const errText = await res.text();
            return { success: false, status: res.status, error: `HTTP ${res.status}: ${errText}` };
        } catch (e) {
            return { success: false, status: null, error: e.message };
        }
    }
}
