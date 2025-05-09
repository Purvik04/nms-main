CREATE TABLE IF NOT EXISTS discovery_profiles (
    id SERIAL PRIMARY KEY,
    discovery_profile_name TEXT UNIQUE NOT NULL,
    credential_profile_id INT NOT NULL,
    ip TEXT NOT NULL,
    port INT NOT NULL DEFAULT 22,
    status BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (credential_profile_id) REFERENCES credential_profiles(id) ON DELETE RESTRICT
);