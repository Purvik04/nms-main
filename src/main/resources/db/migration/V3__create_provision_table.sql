CREATE TABLE IF NOT EXISTS provision (
    id SERIAL PRIMARY KEY,
    credential_profile_id INT,
    ip TEXT NOT NULL UNIQUE,
    port INT NOT NULL,
    status BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (credential_profile_id) REFERENCES credential_profiles(id) ON DELETE RESTRICT
);
