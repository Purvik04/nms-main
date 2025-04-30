package org.example.utils;

public class Constants
{
    public static final String DB_HOST = "localhost";

    public static final String DB_PORT = "5432";

    public static final String DB_NAME = "nms_lite";

    public static final String DB_USER = "purvik";

    public static final String DB_PASSWORD = "admin";

    public static final String OPERATION = "operation";

    public static final String DB_INSERT = "insert";

    public static final String DB_SELECT = "select";

    public static final String DB_UPDATE = "update";

    public static final String DB_DELETE = "delete";

    public static final String TABLE_NAME = "tableName";

    public static final String DATA = "data";

    public static final String SUCCESS = "success";

    public static final String ERROR = "error";

    public static final String CONDITIONS = "conditions";

    public static final String COLUMNS = "columns";

    public static final String DB_CONNECTION_CHECK_QUERY = "SELECT 1";

    public static final String EVENTBUS_DATABASE_ADDRESS = "database.query.execute";

    public static final String EVENTBUS_POLLING_PROCESSOR_ADDRESS = "polling.processor";

    public static final String EVENTBUS_METRIC_POLLING_ADDRESS = "polling.metric";

    public static final String EVENTBUS_AVAILABILITY_POLLING_ADDRESS = "polling.availability";

    public static final String MESSAGE_BODY_REQUIRED = "Body is empty";

    public static final String MESSAGE_ID_REQUIRED = "ID is required";

    public static final String MESSAGE_INCORRECT_BODY = "Incorrect body";

    public static final String MESSAGE_WRONG_IPV4_ADDRESS = "Wrong IPv4 address";

    public static final String  QUERY = "query";

    public static final String  PARAMS = "params";

    public static final String  ID = "id";

    public static final String  POLLED_AT = "polled_at";

    public static final String IP = "ip";

    public static final String PORT = "port";

    public static final String CREDENTIAL_PROFILE_ID = "credential_profile_id";

    public static final String CREDENTIALS = "credentials";

    public static final String PROVISION = "provision";

    public static final String METRICS = "metrics";

    public static final String DISCOVERY = "discovery";

    public static final String EVENTBUS_QUERYBUILDER_ADDRESS = "query.builder";

    public static final String UP = "UP";

    public static final String DOWN = "DOWN";

    public static final String STATUS = "status";



    public static final String CONFIG_FILE_PATH = "config.json";

    public static final String SSL_KEYSTORE_PATH = "ssl_keyStore_path";

    public static final String SSL_KEYSTORE_PASSWORD = "ssl_keyStore_password";

    public static final String JWT_SECRET_KEY = "jwt-secret-key";

    public static final String JWT_ENCRYPTION_ALGORITHM = "jwt-encryption-algorithm";

    public static final String JWT_EXPIRY_TIME_IN_SECONDS = "jwtExpiryTimeInSeconds";

    public static final String EVENT_LOOP_POOL_SIZE = "eventLoopPoolSize";

    public static final String WORKER_POOL_SIZE = "workerPoolSize";


    public static final String CREDENTIAL_PROFILES_TABLE_NAME = "credential_profiles";

    public static final String DISCOVERY_PROFILES_TABLE_NAME = "discovery_profiles";

    public static final String PROVISIONING_JOBS_TABLE_NAME = "provisioning_jobs";

    public static final String CREDENTIAL_PROFILES_SCHEMA_PATH = "src/main/java/org/example/schemas/credential_profiles.json";

    public static final String DISCOVERY_PROFILES_SCHEMA_PATH = "src/main/java/org/example/schemas/discovery_profiles.json";
}

