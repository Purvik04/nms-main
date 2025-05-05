CREATE TABLE IF NOT EXISTS provisioned_data (
    id SERIAL PRIMARY KEY,
    job_id INT NOT NULL REFERENCES provisioning_jobs(id) ON DELETE CASCADE,
    data JSONB NOT NULL,
    polled_at TEXT NOT NULL
);
