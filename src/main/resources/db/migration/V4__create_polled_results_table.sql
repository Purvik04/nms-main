CREATE TABLE IF NOT EXISTS polled_results (
    id SERIAL PRIMARY KEY,
    provision_id INT NOT NULL REFERENCES provision(id) ON DELETE CASCADE,
    metrics JSONB NOT NULL,
    polled_at TEXT NOT NULL
);
