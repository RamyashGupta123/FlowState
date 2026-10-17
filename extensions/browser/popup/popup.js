import { ApiClient, isTokenExpired, parseJwt } from '../sync/api_client.js';

document.addEventListener('DOMContentLoaded', async () => {
    const data = await chrome.storage.local.get([
        'consent_given', 'monitoring_paused', 'pending_events', 'api_token', 'api_url'
    ]);
    
    const serverUrl = data.api_url || 'http://localhost:8080';
    const isExpired = isTokenExpired(data.api_token);

    if (!data.consent_given) {
        document.getElementById('consent-view').classList.remove('hidden');
    } else if (!data.api_token || isExpired) {
        document.getElementById('login-view').classList.remove('hidden');
        document.getElementById('login-server').value = serverUrl;
        if (isExpired && data.api_token) {
            const errorEl = document.getElementById('login-error');
            if (errorEl) {
                errorEl.style.color = '#d97706';
                errorEl.innerText = 'Session expired (HTTP 401). Please log in to resume syncing.';
            }
        }
    } else {
        document.getElementById('main-view').classList.remove('hidden');
        document.getElementById('status-dot').className = data.monitoring_paused ? 'dot yellow' : 'dot green';
        document.getElementById('status-text').innerText = data.monitoring_paused ? 'Paused' : 'Active';
        document.getElementById('pending-count').innerText = (data.pending_events || []).length;
        document.getElementById('connected-server').innerText = serverUrl;
        const claims = parseJwt(data.api_token);
        const userEl = document.getElementById('connected-user');
        if (userEl) {
            userEl.innerText = claims?.email || claims?.sub || 'Authenticated';
        }
        
        // Show current domain if active
        chrome.tabs.query({active: true, currentWindow: true}, tabs => {
            if (tabs.length > 0) {
                try {
                    const url = new URL(tabs[0].url);
                    document.getElementById('current-domain').innerText = url.hostname;
                } catch(e) {}
            }
        });
    }
    
    // Consent Handler
    document.getElementById('btn-consent')?.addEventListener('click', async () => {
        await chrome.storage.local.set({ consent_given: true });
        location.reload();
    });
    
    // Login Handler
    document.getElementById('btn-login')?.addEventListener('click', async () => {
        const errorEl = document.getElementById('login-error');
        errorEl.innerText = '';
        const server = document.getElementById('login-server').value.trim() || 'http://localhost:8080';
        const email = document.getElementById('login-email').value.trim();
        const password = document.getElementById('login-password').value;

        if (!email || !password) {
            errorEl.innerText = 'Please enter email and password.';
            return;
        }

        const loginBtn = document.getElementById('btn-login');
        loginBtn.disabled = true;
        loginBtn.innerText = 'Connecting...';

        try {
            const authRes = await ApiClient.login(server, email, password);
            if (!authRes.token) {
                throw new Error('Authentication succeeded but no token returned.');
            }

            // Immediately clear password from input
            document.getElementById('login-password').value = '';

            // Register device with backend for this user
            const client = new ApiClient(server, authRes.token);
            const dev = await client.registerDevice('browser', 'Chrome Extension', '1.0.0');

            await chrome.storage.local.set({
                api_url: server,
                api_token: authRes.token,
                device_id: dev.id,
                device_registered: true
            });

            chrome.runtime.reload(); // Reload extension to instantiate client in service worker
        } catch (err) {
            errorEl.innerText = err.message || 'Login failed. Check server URL and credentials.';
            loginBtn.disabled = false;
            loginBtn.innerText = 'Connect & Login';
        }
    });

    // Logout Handler
    document.getElementById('btn-logout')?.addEventListener('click', async () => {
        await chrome.storage.local.remove(['api_token', 'device_id', 'device_registered']);
        location.reload();
    });
    
    // Sync Handler
    document.getElementById('btn-sync')?.addEventListener('click', () => {
        const fb = document.getElementById('sync-feedback');
        fb.style.color = '#3b82f6';
        fb.innerText = 'Syncing...';
        
        chrome.runtime.sendMessage({ action: 'sync_now' }, response => {
            if (chrome.runtime.lastError) {
                fb.style.color = '#ef4444';
                fb.innerText = "Error contacting background script.";
                return;
            }
            if (response && response.success) {
                fb.style.color = '#22c55e';
                fb.innerText = response.message;
                chrome.storage.local.get('pending_events', d => {
                    document.getElementById('pending-count').innerText = (d.pending_events || []).length;
                });
            } else {
                fb.style.color = '#ef4444';
                const isAuthErr = response?.status === 401 || response?.error?.includes('401') || response?.error?.includes('Unauthorized');
                if (isAuthErr) {
                    fb.innerHTML = 'Session expired (HTTP 401). <a href="#" id="link-relogin" style="color: #3b82f6; text-decoration: underline; cursor: pointer;">Log in again</a>';
                    document.getElementById('link-relogin')?.addEventListener('click', (e) => {
                        e.preventDefault();
                        document.getElementById('main-view').classList.add('hidden');
                        document.getElementById('login-view').classList.remove('hidden');
                        document.getElementById('login-server').value = serverUrl;
                        const errorEl = document.getElementById('login-error');
                        if (errorEl) {
                            errorEl.style.color = '#d97706';
                            errorEl.innerText = 'Please log in to re-authenticate and sync pending activity.';
                        }
                    });
                } else {
                    fb.innerText = response?.error || 'Unknown sync error';
                }
            }
        });
    });
    
    // Pause/Resume Handler
    document.getElementById('btn-pause')?.addEventListener('click', async () => {
        const current = (await chrome.storage.local.get('monitoring_paused')).monitoring_paused;
        await chrome.storage.local.set({ monitoring_paused: !current });
        location.reload();
    });
});
