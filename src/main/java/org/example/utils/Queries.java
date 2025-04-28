package org.example.utils;

public enum Queries
{
    CREDENTIAL_PROFILES_INSERT("INSERT INTO credential_profiles (credential_profile_name, system_type, credentials) VALUES ($1, $2, $3)"),
    CREDENTIAL_PROFILES_SELECT_BY_ID("SELECT * FROM credential_profiles WHERE id = $1"),
    CREDENTIAL_PROFILES_UPDATE_BY_ID("UPDATE credential_profiles SET credential_profile_name = $1, system_type = $2, credentials = $3 WHERE id = $4"),
    CREDENTIAL_PROFILES_DELETE_BY_ID("DELETE FROM credential_profiles WHERE id = $1"),

    DISCOVERY_PROFILES_INSERT("INSERT INTO discovery_profiles (discovery_profile_name, credential_profile_id, ip, port) VALUES ($1, $2, $3, $4)"),
    DISCOVERY_PROFILES_SELECT_BY_ID("SELECT * FROM discovery_profiles WHERE id = $1"),
    DISCOVERY_PROFILES_UPDATE_STATUS("UPDATE discovery_profiles SET status = $1 WHERE id = $2"),
    DISCOVERY_PROFILES_DELETE_BY_ID("DELETE FROM discovery_profiles WHERE id = $1"),

    PROVISIONING_JOBS_INSERT("INSERT INTO provisioning_jobs (credential_profile_id, ip, port) VALUES ($1, $2, $3)"),
    PROVISIONING_JOBS_DELETE_BY_ID("DELETE FROM provisioning_jobs WHERE id = $1");

    private final String query;

    Queries(String query) {
        this.query = query;
    }

    public String getQuery() {
        return query;
    }
}


