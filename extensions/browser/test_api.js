import { ApiClient } from './sync/api_client.js';

async function run() {
    // We need a real token. Let's get one from the backend.
    const tokenRes = await fetch('http://localhost:8080/api/auth/login', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({email: 'demo@flowstate.com', password: 'password123'})
    });
    const tokenData = await tokenRes.json();
    const token = tokenData.token;
    
    console.log("Got token:", token.substring(0, 20) + "...");
    
    const client = new ApiClient('http://localhost:8080', token);
    
    console.log("Registering device...");
    const dev = await client.registerDevice("browser", "Chrome Test Extension", "1.0.0");
    console.log("Registered device ID:", dev.id);
    
    const testEvents = [
        {
            event_id: crypto.randomUUID(),
            device_id: dev.id, // Use valid device ID
            platform: 'browser',
            timestamp: new Date().toISOString(),
            timezone: 'UTC',
            browser_domain: 'example.com',
            browser_tab_title_category: 'development',
            session_start: new Date().toISOString(),
            session_end: new Date().toISOString(),
            duration_seconds: 300,
            app_switch_count: 0,
            sync_status: 'pending',
            agent_version: '1.0',
            schema_version: '1.0'
        }
    ];
    
    console.log("Sending events (testing camelCase mapping)...");
    const result = await client.syncEvents(testEvents);
    console.log("Result:", result);
}

run().catch(console.error);
