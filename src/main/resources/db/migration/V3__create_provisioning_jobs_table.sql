CREATE TABLE IF NOT EXISTS provisioning_jobs (
    id SERIAL PRIMARY KEY,
    credential_profile_id INT,
    ip TEXT NOT NULL UNIQUE,
    port INT NOT NULL,
    FOREIGN KEY (credential_profile_id) REFERENCES credential_profiles(id) ON DELETE RESTRICT
);
