package org.example.utils;

public class Constants
{
    private Constants() {}

    public static final int HTTP_PORT = 8080;

    //DATABASE CONFIGURATION
    public static final String DB_HOST = "localhost";

    public static final int DB_PORT = 5432;

    public static final String DB_NAME = "nms_lite";

    public static final String DB_USER = "purvik";

    public static final String DB_CONNECTION_CHECK_QUERY = "SELECT 1";


    //QUERY BUILDER CONSTANTS
    public static final String OPERATION = "operation";

    public static final String DB_INSERT = "insert";

    public static final String DB_SELECT = "select";

    public static final String DB_UPDATE = "update";

    public static final String DB_DELETE = "delete";

    public static final String TABLE_NAME = "tableName";

    public static final String CONDITIONS = "conditions";

    public static final String COLUMNS = "columns";

    public static final String  QUERY = "query";

    public static final String  PARAMS = "params";

    public static final String PLACEHOLDERS = "placeholders";

    //DATABASE RESPONSE PREPARE CONSTANTS
    public static final String DATA = "data";

    public static final String SUCCESS = "success";

    public static final String ERROR = "error";


    //EVENTBUS ADDRESS CONSTANTS
    public static final String EVENTBUS_POLLING_PROCESSOR_ADDRESS = "polling.processor.engine";

    public static final String EVENTBUS_METRIC_POLLING_ADDRESS = "metric.polling.engine";

    public static final String EVENTBUS_AVAILABILITY_POLLING_ADDRESS = "availability.polling.engine";

    public static final String EVENTBUS_DISCOVERY_ADDRESS = "discovery.engine";


    //SERVER RESPONSE CONSTANTS
    public static final String MESSAGE_BODY_REQUIRED = "Body is empty";

    public static final String MESSAGE_ID_INVALID = "Id is invalid";

    public static final String MESSAGE = "message";


    //TABLE COLUMNS CONSTANTS
    public static final String CREDENTIAL_PROFILE_ID = "credential_profile_id";

    public static final String USERNAME = "username";

    public static final String PASSWORD = "password";

    public static final String SYSTEM_TYPE = "system_type";

    public static final String CREDENTIALS = "credentials";

    public static final String  ID = "id";

    public static final String  POLLED_AT = "polled_at";

    public static final String IP = "ip";

    public static final String PORT = "port";

    public static final String JOB_ID = "job_id";



    //API PATH START CONSTANTS
    public static final String PROVISION_PATH = "provision";

    public static final String DISCOVERY_PATH = "discovery";

    public static final String CREDENTIALS_PATH = "credentials";



    //DISCOVERY_MODE RESULT PREPARATION CONSTANTS
    public static final String UP = "UP";

    public static final String DOWN = "DOWN";

    public static final String STATUS = "status";


    //CONFIG FILE RELATED CONSTANTS
    public static final String CONFIG_FILE_PATH = "config.json";

    public static final String SSL_KEYSTORE_PATH = "ssl_keyStore_path";

    public static final String SSL_KEYSTORE_PASSWORD = "ssl_keyStore_password";

    public static final String JWT_SECRET_KEY = "default-secret-key";

    public static final String JWT_ENCRYPTION_ALGORITHM = "HS256";

    public static final int JWT_EXPIRY_TIME_IN_SECONDS = 3600;

    //TABLE NAMES
    public static final String CREDENTIAL_PROFILES_TABLE_NAME = "credential_profiles";

    public static final String DISCOVERY_PROFILES_TABLE_NAME = "discovery_profiles";

    public static final String PROVISIONING_JOBS_TABLE_NAME = "provisioning_jobs";

    public static final String PROVISIONED_DATA_TABLE_NAME = "provisioned_data";


    //GO PLUGIN SPAWN MODES
    public static final String METRICS_MODE = "metrics";

    public static final String DISCOVERY_MODE = "discovery";


    // HTTP Status Codes
    public static final int SC_200 = 200; // OK

    public static final int SC_201  = 201; // Created

    public static final int SC_400 = 400; // Bad Request

    public static final int SC_401 = 401; // Unauthorized

    public static final int SC_404 = 404; // Not Found

    public static final int SC_500 = 500; // Internal Server Error

    //SEPARATORS
    public static final String EQUALS_SEPARATOR = "=";

    public static final String COMMA_SEPARATOR = ",";

    public static final String PERCENTAGE_SEPARATOR = "%";

    public static final String SPACE_SEPARATOR = " ";

    public static final String SEMICOLON_SEPARATOR = ";";

    public static final String COLON_SEPARATOR = ":";

    public static final String LINE_SEPARATOR = "\n";

    public static final String PATH_SEPARATOR = "/";

    public static final String EMPTY_STRING = "";

    public static final String PACKET_SEND = "packet.send";

    public static final String PACKET_RECEIVE = "packet.receive";

    public static final String PACKET_LOSS_PERCENTAGE= "packet.loss.percentage";


    //SCHEMA PATHS
    public static final String CREDENTIAL_PROFILES_SCHEMA_PATH = "schemas/credential_profiles.json";

    public static final String DISCOVERY_PROFILES_SCHEMA_PATH = "schemas/discovery_profiles.json";


    //BOOLEAN CONSTNATS
    public static final boolean TRUE = true;

    public static final boolean FALSE = false;
}

