CREATE TABLE activity_events (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    platform VARCHAR(50) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    timezone VARCHAR(100),
    app_identifier VARCHAR(255),
    app_name VARCHAR(256),
    browser_domain VARCHAR(255),
    browser_tab_title_category VARCHAR(50),
    session_start TIMESTAMPTZ NOT NULL,
    session_end TIMESTAMPTZ NOT NULL,
    duration_seconds INT NOT NULL,
    idle_seconds INT,
    app_switch_count INT,
    declared_goal_id UUID, -- We'll add FK constraint later or leave without strict FK if goals can be deleted
    focus_session_id UUID,
    activity_category VARCHAR(50),
    goal_alignment VARCHAR(50),
    classification_confidence DOUBLE PRECISION,
    classification_source VARCHAR(50),
    classification_evidence TEXT[],
    requires_user_confirmation BOOLEAN DEFAULT FALSE,
    user_feedback JSONB,
    context_features JSONB,
    sync_status VARCHAR(50),
    agent_version VARCHAR(50),
    schema_version VARCHAR(10),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_activity_events_user_timestamp ON activity_events(user_id, timestamp DESC);
CREATE INDEX idx_activity_events_user_category ON activity_events(user_id, activity_category);
CREATE INDEX idx_activity_events_device_id ON activity_events(device_id);
