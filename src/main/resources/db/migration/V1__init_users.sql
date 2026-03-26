-- Create subscriptions table first to satisfy the foreign key constraint in the users table
CREATE TABLE IF NOT EXISTS subscriptions (
                                             id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    price           NUMERIC,
    duration_days   INT,
    product_id      VARCHAR(100)
    );

-- Create core users table
CREATE TABLE IF NOT EXISTS users (
                                     id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                   VARCHAR(255) NOT NULL UNIQUE,
    username                VARCHAR(100) NOT NULL UNIQUE,
    password_hash           VARCHAR(255) NOT NULL,
    first_name              VARCHAR(100),
    last_name               VARCHAR(100),
    avatar_url              TEXT,
    subscription_id         UUID REFERENCES subscriptions(id),
    subscription_expires_at TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

-- Create indexes for frequent lookups
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);