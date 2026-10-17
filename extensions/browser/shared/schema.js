export function extractDomain(url) {
    if (!url) return null;
    try {
        return new URL(url).hostname;
    } catch {
        return null;
    }
}

export function inferTitleCategory(title) {
    if (!title) return 'other';
    title = title.toLowerCase();
    if (title.includes('youtube') || title.includes('video')) return 'video';
    if (title.includes('docs') || title.includes('documentation')) return 'documentation';
    return 'other';
}

export function createActivityEvent({
    deviceId, domain, tabTitleCategory, sessionStart, sessionEnd, durationSeconds, switchCount, agentVersion
}) {
    return {
        eventId: crypto.randomUUID(),
        deviceId: deviceId,
        platform: 'browser',
        timestamp: new Date().toISOString(),
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        browserDomain: domain,
        browserTabTitleCategory: tabTitleCategory,
        sessionStart: sessionStart,
        sessionEnd: sessionEnd,
        durationSeconds: durationSeconds,
        appSwitchCount: switchCount,
        syncStatus: 'pending',
        agentVersion: agentVersion,
        schemaVersion: '1.0'
    };
}
