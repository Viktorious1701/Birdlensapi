-- The subscriptions table was created in V1 to satisfy the users FK,
-- but we need to ensure the standard audit columns exist.
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Create events table
CREATE TABLE IF NOT EXISTS events (
                                      id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    cover_photo_url     TEXT,
    start_date          TIMESTAMPTZ,
    end_date            TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

-- Create tours table
CREATE TABLE IF NOT EXISTS tours (
                                     id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id            UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    price               NUMERIC(10, 2),
    capacity            INT,
    thumbnail_url       TEXT,
    duration_hours      INT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

-- Create index for faster relational lookups
CREATE INDEX IF NOT EXISTS idx_tours_event_id ON tours(event_id);