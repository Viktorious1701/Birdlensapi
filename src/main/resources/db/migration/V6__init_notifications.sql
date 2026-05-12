-- Create user_notifications table
CREATE TABLE IF NOT EXISTS user_notifications (
                                                  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message     TEXT NOT NULL,
    type        VARCHAR(50) NOT NULL,
    is_read     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

-- Create index for fast retrieval of unread notifications for a specific user
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON user_notifications(user_id, is_read);