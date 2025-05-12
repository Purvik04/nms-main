package org.example.utils;

public class Constants
{
    private Constants() {}

    public static final int HTTPS_PORT = 8080;

    //DATABASE CONFIGURATION
    public static final String DB_HOST = "localhost";

    public static final int DB_PORT = 5432;

    public static final String DB_NAME = "nms_lite";

    public static final String DB_USER = "purvik";

    public static final String DB_CONNECTION_CHECK_QUERY = "SELECT 1";

    //QUERY BUILDER CONSTANTS
    public static final String OPERATION = "operation";

    public static final String DB_INSERT = "INSERT";

    public static final String DB_SELECT = "SELECT";

    public static final String DB_UPDATE = "UPDATE";

    public static final String DB_DELETE = "DELETE";

    public static final String TABLE_NAME = "table.name";

    public static final String CONDITIONS = "conditions";

    public static final String COLUMNS = "columns";

    public static final String  QUERY = "query";

    public static final String  PARAMS = "params";

    public static final String IDS = "ids";

    public static final String PLACEHOLDERS = "placeholders";

    //DATABASE DATA PREPARE CONSTANTS
    public static final String DATA = "data";

    public static final String SUCCESS = "success";

    public static final String ERROR = "error";


    //EVENTBUS ADDRESS CONSTANTS
    public static final String POLLING_PROCESSOR_ADDRESS = "polling.processor.engine";

    public static final String METRIC_POLLING_ADDRESS = "metric.polling.engine";

    public static final String AVAILABILITY_POLLING_ADDRESS = "availability.polling.engine";

    public static final String DISCOVERY_ADDRESS = "discovery.engine";


    //SERVER DATA CONSTANTS
    public static final String MESSAGE_BODY_REQUIRED = "Body is empty";

    public static final String MESSAGE_ID_INVALID = "Id is invalid";

    public static final String MESSAGE = "message";


    //TABLE COLUMNS CONSTANTS
    public static final String CREDENTIAL_PROFILE_ID = "credential_profile_id";

    public static final String CREDENTIALS = "credentials";

    public static final String  ID = "id";

    public static final String  POLLED_AT = "polled_at";

    public static final String IP = "ip";

    public static final String PORT = "port";

    public static final String PROVISION_ID = "provision_id";

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

    public static final String SSL_KEYSTORE_PATH = "ssl.keyStore.path";

    public static final String SSL_KEYSTORE_PASSWORD = "ssl.keyStore.password";

    public static final String JWT_SECRET_KEY = "default-secret-key";

    public static final String JWT_ENCRYPTION_ALGORITHM = "HS256";

    public static final int JWT_EXPIRY_TIME_IN_SECONDS = 3600;

    //TABLE NAMES
    public static final String CREDENTIAL_PROFILES_TABLE = "credential_profiles";

    public static final String DISCOVERY_PROFILES_TABLE = "discovery_profiles";

    public static final String PROVISION_TABLE = "provision";

    public static final String POLLED_RESULTS_TABLE = "polled_results";

    //GO PLUGIN SPAWN EVENTS
    public static final String METRICS = "metrics";

    public static final String DISCOVERY = "discovery";


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

    public static final String PACKETS_SEND = "packets_send";

    public static final String PACKETS_RECEIVED = "packets_received";

    public static final String PACKET_LOSS_PERCENTAGE= "packet_loss_percentage";

    public static final String PING_PROCESS_TIMEOUT = "ping.process.timeout";

    public static final String PING_PACKET_COUNT = "ping.packet.count";

    public static final String PING_PACKET_TIMEOUT_IN_MILLISECONDS = "ping.packet.timeout.in.milliseconds";

    public static final String PLUGIN_PROCESS_TIMEOUT = "plugin.process.timeout";

    public static final int DEFAULT_PING_PROCESS_TIMEOUT = 1;

    public static final int DEFAULT_PLUGIN_PROCESS_TIMEOUT = 5;

    public static final int DEFAULT_PING_PACKET_COUNT = 3;

    public static final int DEFAULT_PING_PACKET_TIMEOUT_IN_MILLISECONDS = 500;


    //SCHEMA PATHS
    public static final String CREDENTIAL_PROFILES_SCHEMA_PATH = "schemas/credential_profiles.json";

    public static final String DISCOVERY_PROFILES_SCHEMA_PATH = "schemas/discovery_profiles.json";


    //BOOLEAN CONSTNATS
    public static final boolean TRUE = true;

    public static final boolean FALSE = false;


    public static final String FETCH_DISCOVERY_PROFILES_QUERY = "SELECT dp.id, dp.ip, dp.port, cp.credentials, cp.system_type FROM discovery_profiles dp " +
            "JOIN credential_profiles cp ON dp.credential_profile_id = cp.id WHERE dp.id IN ($1)";
}

