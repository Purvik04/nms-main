CREATE TABLE IF NOT EXISTS availability_polling_results (
    id SERIAL PRIMARY KEY,
    provision_id INT,
    packets_send INT NOT NULL,
    packets_received INT NOT NULL,
    packet_loss_percentage INT NOT NULL CHECK (packet_loss_percentage BETWEEN 0 AND 100),
    timestamp TEXT NOT NULL,
    FOREIGN KEY (provision_id) REFERENCES provision(id) ON DELETE RESTRICT
);