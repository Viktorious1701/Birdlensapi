-- Create posts table
CREATE TABLE IF NOT EXISTS posts (
                                     id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id),
    content             TEXT,
    location_name       VARCHAR(255),
    location_point      GEOGRAPHY(Point, 4326),
    privacy_level       VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    type                VARCHAR(20) NOT NULL,
    sighting_date       DATE,
    tagged_species_code VARCHAR(20) REFERENCES bird_taxonomy(species_code),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

-- Create indexes for performance on posts
CREATE INDEX IF NOT EXISTS idx_posts_user_id ON posts(user_id);
CREATE INDEX IF NOT EXISTS idx_posts_created_at ON posts(created_at DESC);

-- Create post_media table
CREATE TABLE IF NOT EXISTS post_media (
                                          id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id             UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    original_url        TEXT NOT NULL,
    thumbnail_url       TEXT,
    compressed_url      TEXT,
    processing_status   VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

-- Create post_comments table
CREATE TABLE IF NOT EXISTS post_comments (
                                             id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id             UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content             TEXT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
CREATE INDEX IF NOT EXISTS idx_post_comments_post_id ON post_comments(post_id);

-- Create post_reactions table
CREATE TABLE IF NOT EXISTS post_reactions (
                                              id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id             UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reaction_type       VARCHAR(20) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(post_id, user_id, reaction_type)
    );
CREATE INDEX IF NOT EXISTS idx_post_reactions_post_id ON post_reactions(post_id);