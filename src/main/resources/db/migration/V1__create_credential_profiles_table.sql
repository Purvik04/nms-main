CREATE TABLE IF NOT EXISTS credential_profiles (
    id SERIAL PRIMARY KEY,
    credential_profile_name TEXT UNIQUE NOT NULL,
    system_type TEXT NOT NULL,
    credentials JSONB NOT NULL
);