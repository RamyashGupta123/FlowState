CREATE TABLE intervention_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    focus_reminders_enabled BOOLEAN DEFAULT TRUE,
    break_reminders_enabled BOOLEAN DEFAULT TRUE,
    reflection_prompts_enabled BOOLEAN DEFAULT TRUE,
    snooze_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
