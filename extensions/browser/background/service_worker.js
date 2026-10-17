import { createActivityEvent, extractDomain, inferTitleCategory } from '../shared/schema.js';
import { PrivacyFilter } from '../privacy/filter.js';
import { ApiClient, isTokenExpired } from '../sync/api_client.js';

let filter = new PrivacyFilter();
let apiClient = null;
let currentSession = null;

async function initConfig() {
    const data = await chrome.storage.local.get([
        'consent_given', 'monitoring_paused', 'api_url', 'api_token', 'device_id', 'excluded_domains', 'device_registered'
    ]);
    if (data.excluded_domains) {
        filter = new PrivacyFilter(data.excluded_domains);
    }
    if (data.api_url && data.api_token) {
        apiClient = new ApiClient(data.api_url, data.api_token);
    }
    return data;
}

chrome.runtime.onInstalled.addListener(async () => {
    const data = await chrome.storage.local.get(['consent_given']);
    if (!data.consent_given) {
        chrome.storage.local.set({
            consent_given: false,
            monitoring_paused: false,
            api_url: 'http://localhost:8080',
            pending_events: []
        });
        chrome.action.openPopup();
    }
});

async function closeCurrentSession() {
    if (!currentSession) return;
    const now = new Date();
    const duration = Math.floor((now - currentSession.startTime) / 1000);
    
    if (duration >= 10) {
        const data = await chrome.storage.local.get(['device_id', 'pending_events']);
        const event = createActivityEvent({
            deviceId: data.device_id,
            domain: currentSession.domain,
            tabTitleCategory: currentSession.titleCategory,
            sessionStart: currentSession.startTime.toISOString(),
            sessionEnd: now.toISOString(),
            durationSeconds: duration,
            switchCount: currentSession.switchCount,
            agentVersion: '1.0.0'
        });
        
        let pending = data.pending_events || [];
        pending.push(event);
        if (pending.length > 500) pending = pending.slice(pending.length - 500);
        await chrome.storage.local.set({ pending_events: pending });
    }
    currentSession = null;
}

chrome.tabs.onActivated.addListener(async (activeInfo) => {
    const data = await initConfig();
    if (!data.consent_given || data.monitoring_paused) return;
    
    const tab = await chrome.tabs.get(activeInfo.tabId);
    if (!filter.shouldTrackTab(tab)) {
        await closeCurrentSession();
        return;
    }
    
    const domain = extractDomain(tab.url);
    if (currentSession && currentSession.domain === domain) {
        currentSession.switchCount++;
        return;
    }
    
    await closeCurrentSession();
    currentSession = {
        domain: domain,
        titleCategory: inferTitleCategory(tab.title),
        startTime: new Date(),
        switchCount: 0
    };
});

async function triggerSync() {
    let data = await initConfig();
    if (!data.consent_given || data.monitoring_paused) {
        console.warn("Sync aborted: consent missing or paused.");
        return { success: false, error: "Monitoring is paused or consent missing." };
    }
    if (!data.api_token || isTokenExpired(data.api_token)) {
        console.warn("Sync aborted: API token missing or expired.");
        return { success: false, status: 401, error: "Session expired (HTTP 401). Please log in again to resume syncing." };
    }
    if (!apiClient) {
        console.error("Sync aborted: API client not initialized.");
        return { success: false, status: 401, error: "Authentication token is missing. Please log in." };
    }
    
    let isNewlyRegistered = false;
    if (!data.device_registered) {
        try {
            console.log("Registering device with backend...");
            const dev = await apiClient.registerDevice("browser", "Chrome Extension", "1.0.0");
            await chrome.storage.local.set({ device_id: dev.id, device_registered: true });
            data.device_id = dev.id;
            isNewlyRegistered = true;
        } catch (e) {
            console.error("Failed to register device:", e);
            return { success: false, error: e.message };
        }
    }
    
    let pending = (await chrome.storage.local.get(['pending_events'])).pending_events || [];
    if (pending.length === 0) {
        console.log("Sync skipped: No pending events.");
        return { success: true, message: "No pending events." };
    }
    
    // Rewrite all pending events to use the valid registered device ID!
    pending = pending.map(e => ({
        ...e,
        device_id: data.device_id,
        deviceId: data.device_id
    }));
    // Save them back immediately so we don't lose the updated IDs if sync fails
    if (isNewlyRegistered) {
        await chrome.storage.local.set({ pending_events: pending });
    }
    
    console.log(`Attempting to sync ${pending.length} events...`);
    const result = await apiClient.syncEvents(pending);
    if (result.success) {
        console.log(`Successfully synced ${result.accepted} events.`);
        // Remove ONLY the successfully uploaded events from the pending queue (Point 9)
        const syncedIds = new Set(result.syncedEventIds || pending.map(e => e.eventId || e.event_id));
        const currentData = await chrome.storage.local.get(['pending_events']);
        const currentPending = currentData.pending_events || [];
        const remaining = currentPending.filter(e => !syncedIds.has(e.eventId || e.event_id));
        await chrome.storage.local.set({ pending_events: remaining });
        return { success: true, message: `Synced ${result.accepted} events.` };
    } else {
        console.error("Sync failed: Backend rejected or unreachable. Retaining pending events.");
        return { success: false, status: result.status, error: result.error || "Backend sync failed." };
    }
}

chrome.alarms.create('sync', { periodInMinutes: 5 });
chrome.alarms.onAlarm.addListener(async (alarm) => {
    if (alarm.name === 'sync') {
        await triggerSync();
    }
});

chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
    if (request.action === 'sync_now') {
        triggerSync().then(sendResponse).catch(err => {
            console.error("Sync error:", err);
            sendResponse({ success: false, error: err.message });
        });
        return true; // keep channel open for async response
    }
    if (request.action === 'close_session') {
        closeCurrentSession().then(() => sendResponse({ success: true }));
        return true;
    }
});
