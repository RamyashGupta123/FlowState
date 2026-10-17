-- V7__create_recommendations_and_feedback.sql
-- Additive migration for Adaptive Wellbeing Recommendation Engine

CREATE TABLE IF NOT EXISTS recommendations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    intervention_type VARCHAR(50) NOT NULL, -- 'BREATHING_MINDFULNESS', 'WALK_STRETCH', 'SCREEN_REST', 'GENERAL_BREAK'
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    reason TEXT NOT NULL,
    duration_seconds INT NOT NULL DEFAULT 60,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'ACCEPTED', 'DISMISSED', 'SNOOZED', 'EXPIRED'
    score DOUBLE PRECISION DEFAULT 1.0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS recommendation_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recommendation_id UUID REFERENCES recommendations(id) ON DELETE SET NULL,
    feedback_type VARCHAR(50) NOT NULL, -- 'HELPFUL', 'NOT_RELEVANT', 'PREFER_WALKING', 'PREFER_BREATHING', 'REMIND_ME_LATER', 'DISMISS_FOR_TODAY'
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_recommendations_user_created ON recommendations(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_recommendations_user_status ON recommendations(user_id, status);
CREATE INDEX IF NOT EXISTS idx_rec_feedback_user_created ON recommendation_feedback(user_id, created_at DESC);

-- Extend intervention_preferences with recommendation thresholds if columns do not exist
ALTER TABLE intervention_preferences 
ADD COLUMN IF NOT EXISTS continuous_work_threshold_minutes INT DEFAULT 45,
ADD COLUMN IF NOT EXISTS cooldown_minutes INT DEFAULT 30,
ADD COLUMN IF NOT EXISTS buffer_after_break_minutes INT DEFAULT 15,
ADD COLUMN IF NOT EXISTS preferred_intervention_type VARCHAR(50),
ADD COLUMN IF NOT EXISTS quiet_hours_enabled BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS quiet_hours_start VARCHAR(10) DEFAULT '22:00',
ADD COLUMN IF NOT EXISTS quiet_hours_end VARCHAR(10) DEFAULT '08:00';
