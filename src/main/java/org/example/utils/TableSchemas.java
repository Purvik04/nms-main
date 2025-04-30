package org.example.utils;

import java.util.List;

public class TableSchemas
{
    public static List<String> getAllSchemas() {
        return List.of(
                """
                CREATE TABLE IF NOT EXISTS credential_profiles (
                    id SERIAL PRIMARY KEY,
                    credential_profile_name TEXT UNIQUE NOT NULL,
                    system_type TEXT NOT NULL,
                    credentials JSONB NOT NULL
                );
                """,
                """
                CREATE TABLE IF NOT EXISTS discovery_profiles (
                    id SERIAL PRIMARY KEY,
                    discovery_profile_name TEXT UNIQUE NOT NULL,
                    credential_profile_id INT,
                    ip TEXT NOT NULL,
                    port INT NOT NULL DEFAULT 22,
                    status BOOLEAN DEFAULT FALSE,
                    FOREIGN KEY (credential_profile_id) REFERENCES credential_profiles(id) ON DELETE RESTRICT
                );
                """,
                """
                CREATE TABLE IF NOT EXISTS provisioning_jobs (
                    id SERIAL PRIMARY KEY,
                    credential_profile_id INT,
                    ip TEXT NOT NULL UNIQUE,
                    port INT NOT NULL,
                    FOREIGN KEY (credential_profile_id) REFERENCES credential_profiles(id) ON DELETE RESTRICT
                );
                """,
                """
                CREATE TABLE IF NOT EXISTS provisioned_data (
                    id SERIAL PRIMARY KEY,
                    job_id INT NOT NULL REFERENCES provisioning_jobs(id) ON DELETE CASCADE,
                    data JSONB NOT NULL,
                    polled_at TEXT NOT NULL
                );
                """,
                """
                CREATE TABLE IF NOT EXISTS users (
                    id SERIAL PRIMARY KEY,
                    username TEXT UNIQUE NOT NULL,
                    password TEXT NOT NULL
                );
                """
        );
    }
}
