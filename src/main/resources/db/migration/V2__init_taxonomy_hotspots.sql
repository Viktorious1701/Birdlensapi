-- Create bird_taxonomy table
CREATE TABLE IF NOT EXISTS bird_taxonomy (
    species_code          VARCHAR(20) PRIMARY KEY,
    common_name           VARCHAR(255) NOT NULL,
    scientific_name       VARCHAR(255) NOT NULL,
    category              VARCHAR(50),
    taxon_order           NUMERIC,
    bird_order            VARCHAR(100),
    family_common_name    VARCHAR(100),
    family_scientific_name VARCHAR(100)
);

-- Create indexes for fast case-insensitive/prefix searching on taxonomy
CREATE INDEX IF NOT EXISTS idx_taxonomy_common_name ON bird_taxonomy(lower(common_name) text_pattern_ops);
CREATE INDEX IF NOT EXISTS idx_taxonomy_scientific_name ON bird_taxonomy(lower(scientific_name) text_pattern_ops);

-- Create ebird_hotspots table
CREATE TABLE IF NOT EXISTS ebird_hotspots (
    loc_id                VARCHAR(50) PRIMARY KEY,
    loc_name              VARCHAR(255) NOT NULL,
    country_code          VARCHAR(5),
    subnational1_code     VARCHAR(20),
    subnational2_code     VARCHAR(20),
    latitude              DOUBLE PRECISION NOT NULL,
    longitude             DOUBLE PRECISION NOT NULL,
    location              GEOGRAPHY(Point, 4326) NOT NULL,
    latest_obs_dt         DATE,
    num_species_all_time  INT DEFAULT 0,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create GiST index for fast spatial queries (bounding box / radius)
CREATE INDEX IF NOT EXISTS idx_hotspots_location ON ebird_hotspots USING GIST(location);