package org.example.utils;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for various operations including:
 * - Table name extraction from route
 * - Ping operations using fping
 * - Secure communication with Go plugin
 * - SQL query building
 */
public class Utils
{
    private Utils(){}

    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

    private static final String HTTP_METHOD_POST = "POST";

    private static final String HTTP_METHOD_DELETE = "DELETE";

    private static final int LOSS_PERCENTAGE_100 = 100;

    private static final int DEFAULT_PACKET_SEND = 3;

    private static final int DEFAULT_PACKET_RECEIVE = 0;

    private static final String SECURE_COMPRESSED_FILE_PATH = "devices.snappy.aes.b64.txt";

    private static final String PING_PACKET_COUNT = String.valueOf(MotaDataConfigUtil.getConfig().getInteger(Constants.PING_PACKET_COUNT
            ,Constants.DEFAULT_PING_PACKET_COUNT));

    private static final String PING_PACKET_TIMEOUT = String.valueOf(MotaDataConfigUtil.getConfig().getInteger(Constants.PING_PACKET_TIMEOUT_IN_MILLISECONDS
            ,Constants.DEFAULT_PING_PACKET_TIMEOUT_IN_MILLISECONDS));

    /**
     * Extracts the table name from the request's routing context based on the path and HTTP method.
     *
     * @param context The RoutingContext containing the current HTTP request details.
     * @return The table name as a string, or an empty string if an error occurs.
     */
    public static String getTableNameFromContext(RoutingContext context)
    {
        try
        {
            String path  = context.normalizedPath().split(Constants.PATH_SEPARATOR)[2];

            var method = context.request().method().name();

            return switch (path)
            {
                case Constants.CREDENTIALS_PATH -> Constants.CREDENTIAL_PROFILES_TABLE;

                case Constants.DISCOVERY_PATH -> Constants.DISCOVERY_PROFILES_TABLE;

                case Constants.PROVISION_PATH -> (method.equals(HTTP_METHOD_POST) || method.equals(HTTP_METHOD_DELETE))
                        ? Constants.PROVISION_TABLE
                        : Constants.POLLED_RESULTS_TABLE;

                default -> Constants.EMPTY_STRING;
            };
        }
        catch (Exception exception)
        {
            LOGGER.error("Error getting table name from context: {}", exception.getMessage());

            return Constants.EMPTY_STRING;
        }
    }

    /**
     * Runs the fping command to check the availability of devices.
     *
     * @param devices A JsonArray containing the devices with their IPs.
     * @return A JsonArray containing the devices id and their status (UP/DOWN).
     */
    public static JsonArray ping(JsonArray devices)
    {
        Process process = null;

        try
        {
            var ipToDeviceIdMap = new HashMap<String, Integer>();

            var ipList = new ArrayList<String>(devices.size());

            for (var index = 0; index < devices.size(); index++)
            {
                ipList.add(devices.getJsonObject(index).getString(Constants.IP));

                ipToDeviceIdMap.put(devices.getJsonObject(index).getString(Constants.IP),
                        devices.getJsonObject(index).getInteger(Constants.ID));
            }

            devices.clear();

            var command = new ArrayList<String>();

            command.add("fping");
            command.add("-c");
            command.add(PING_PACKET_COUNT);
            command.add("-q");
            command.add("-t");
            command.add(PING_PACKET_TIMEOUT);
            command.add("-p");
            command.add("0");
            command.addAll(ipList);

            process = new ProcessBuilder(command).start();

            try (var reader = new BufferedReader(new InputStreamReader(process.getErrorStream())))
            {
                var line = "";

                while ((line = reader.readLine()) != null)
                {
                    var ip = line.split(Constants.COLON_SEPARATOR)[0].trim();

                    devices.add(parsePingResult(line.trim(),ipToDeviceIdMap.get(ip)));
                }
            }

            if (!process.waitFor(MotaDataConfigUtil.getConfig()
                    .getInteger(Constants.PING_PROCESS_TIMEOUT,Constants.DEFAULT_PING_PROCESS_TIMEOUT), TimeUnit.SECONDS))
            {
                LOGGER.error("Ping Process timeout! Process killed.");

                process.destroyForcibly();

                return devices;
            }

            var exitCode = process.exitValue();

            if (exitCode != 0 && exitCode != 1)
            {
                LOGGER.error("fping exited abnormally with code {}", exitCode);

                return new JsonArray(); // Empty response on abnormal exit
            }

            return devices;
        }
        catch (Exception exception)
        {
            LOGGER.error("Error during fping execution: {}", exception.getMessage());

            return new JsonArray();
        }
        finally
        {
            if (process != null && process.isAlive())
            {
                process.destroyForcibly();
            }
        }
    }


    private static JsonObject parsePingResult(String summaryLine, int id)
    {
        try
        {
            // Example: 192.168.1.1 : xmt/rcv/%loss = 3/3/0%, min/avg/max = ...
            var parts = summaryLine.split(Constants.EQUALS_SEPARATOR);

            if (parts.length >= 2)
            {
                var stats = parts[1].split(Constants.COMMA_SEPARATOR)[0].trim().split(Constants.PATH_SEPARATOR);

                var loss = Integer.parseInt(stats[2].replace(Constants.PERCENTAGE_SEPARATOR, Constants.EMPTY_STRING));

                return new JsonObject().put(Constants.ID, id).put(Constants.PACKETS_SEND, Integer.parseInt(stats[0]))
                        .put(Constants.PACKETS_RECEIVED, Integer.parseInt(stats[1]))
                        .put(Constants.PACKET_LOSS_PERCENTAGE,loss)
                        .put(Constants.STATUS, loss == LOSS_PERCENTAGE_100 ? Constants.DOWN : Constants.UP);
            }
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in parsing ping result: {}", exception.getMessage());
        }

        return new JsonObject().put(Constants.ID, id).put(Constants.PACKETS_SEND,DEFAULT_PACKET_SEND)
                .put(Constants.PACKETS_RECEIVED,DEFAULT_PACKET_RECEIVE)
                .put(Constants.PACKET_LOSS_PERCENTAGE,LOSS_PERCENTAGE_100).put(Constants.STATUS, Constants.DOWN);
    }

    /**
     * Executes the Go plugin with secure file processing, including encryption and compression.
     *
     * @param devices The input JSON array of devices to be processed.
     * @param event The event in which the Go plugin should run (e.g., discovery or metric).
     * @return A JSON array containing the results of the Go plugin execution.
     */
    public static JsonArray spawnGoPlugin(JsonArray devices , String event)
    {
        var result = new JsonArray();

        var file = new File(SECURE_COMPRESSED_FILE_PATH);

        var filePath = file.getAbsolutePath();

        Process process = null;

        try
        {
            // Encrypt and compress the devices' JSON data, and write it to the file
            SecureCompressor.writeIntoFile(devices,filePath);

            process = new ProcessBuilder("go/ssh-plugin", event, filePath).start();

            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
            {
                var line = "";

                while ((line = reader.readLine()) != null)
                {
                    if(event.equals(Constants.DISCOVERY))
                    {
                        result.add(new JsonObject(line.trim()));
                    }
                    else
                    {
                        processPollingResult(result,line);
                    }
                }
            }

            var finished = process.waitFor(MotaDataConfigUtil.getConfig().getInteger(Constants.PLUGIN_PROCESS_TIMEOUT,
                            Constants.DEFAULT_PLUGIN_PROCESS_TIMEOUT), TimeUnit.SECONDS);

            if (!finished)
            {
                LOGGER.warn("Go plugin process timeout exceeded, forcibly terminating.");

                process.destroyForcibly();
            }

        }
        catch (Exception exception)
        {
            LOGGER.error("Unexpected error in secure plugin execution: {}", exception.getMessage());
        }
        finally
        {
            try
            {
                if (process != null && process.isAlive())
                {
                    process.destroyForcibly();
                }

                Files.deleteIfExists(file.toPath());
            }
            catch (Exception exception)
            {
                LOGGER.error("Error: {}",exception.getMessage());
            }
        }

        return result;
    }

    /**
     * Processes a single encrypted, compressed polling result line from the Go plugin.
     * Decrypts and decompresses the line and appends the resulting JSON object to the devices array.
     *
     * @param devices The JSON array to which the decrypted result will be added.
     * @param device A single line of output from the Go plugin, which is Base64-encoded, AES-GCM encrypted, and Snappy-compressed.
     */
    public static void processPollingResult(JsonArray devices , String device)
    {
        try
        {
            devices.add(SecureCompressor.decryptPluginOutput(device.trim()));
        }
        catch (Exception exception)
        {
            LOGGER.error("Decryption error for line: {}, error: {}", device, exception.getMessage());
        }
    }

    /**
     * Builds an SQL query dynamically based on the input JsonObject.
     * Supports INSERT, SELECT, UPDATE, DELETE operations with condition and parameter binding support.
     *
     * @param input  The JSON input containing operation type, table name, data, conditions, and optional columns.
     * @param query  A StringBuilder object to hold the generated SQL query string.
     * @param params A JsonArray to which the query parameters will be added in positional order.
     * @return JsonObject containing "success", "query" (String), and "params" (JsonArray), or error if invalid input.
     */
    public static JsonObject buildQuery(JsonObject input, StringBuilder query, JsonArray params)
    {
        try
        {
            // Extract target table name
            var table = input.getString(Constants.TABLE_NAME);

            // JSON object with column-value pairs for insert/update
            var data = input.getJsonObject(Constants.DATA, new JsonObject());

            // JSON object with condition column-value pairs for WHERE clause
            var conditions = input.getJsonObject(Constants.CONDITIONS, new JsonObject());

            // Optional array of columns for SELECT
            var columns = input.getJsonArray(Constants.COLUMNS, new JsonArray());

            // Switch based on the requested SQL operation
            switch (input.getString(Constants.OPERATION).toLowerCase())
            {
                case Constants.DB_INSERT:
                    // INSERT INTO table (col1, col2) VALUES ($1, $2)
                    var keys = data.fieldNames();

                    var columnsStr = String.join(Constants.COMMA_SEPARATOR, keys);

                    var placeholders = Utils.buildPlaceholders(keys.size());

                    for (var key : keys)
                    {
                        params.add(data.getValue(key));
                    }

                    query.append("INSERT INTO ").append(table)
                            .append(" (").append(columnsStr).append(")")
                            .append(" VALUES (").append(placeholders).append(")");

                    break;

                case Constants.DB_SELECT:
                    // SELECT col1, col2 FROM table WHERE condition1 = $1 AND ...
                    var columnStr = (columns != null && !columns.isEmpty())
                            ? String.join(", ", columns.stream().map(Object::toString).toList())
                            : "*";

                    query.append("SELECT ").append(columnStr).append(" FROM ").append(table);

                    if (!conditions.isEmpty())
                    {
                        var whereClause = Utils.buildWhereClause(conditions, params, 1);

                        query.append(Constants.SPACE_SEPARATOR).append(whereClause);
                    }
                    break;

                case Constants.DB_UPDATE:
                    // UPDATE table SET col1 = $1, col2 = $2 WHERE condition1 = $3 AND ...
                    query.append("UPDATE ").append(table).append(" SET ");

                    var index = 1;

                    for (var key : data.fieldNames())
                    {
                        query.append(key).append(" = $").append(index++);

                        if (index <= data.size()) query.append(Constants.COMMA_SEPARATOR);

                        params.add(data.getValue(key));
                    }

                    if (!conditions.isEmpty())
                    {
                        var whereClause = Utils.buildWhereClause(conditions, params, index);

                        query.append(Constants.SPACE_SEPARATOR).append(whereClause);
                    }
                    break;

                case Constants.DB_DELETE:
                    // DELETE FROM table WHERE condition1 = $1 AND ...
                    query.append("DELETE FROM ").append(table);

                    if (!conditions.isEmpty())
                    {
                        var whereClause = Utils.buildWhereClause(conditions, params, 1);

                        query.append(Constants.SPACE_SEPARATOR).append(whereClause);
                    }
                    break;

                default:
                    // Invalid operation provided
                    return new JsonObject()
                            .put(Constants.SUCCESS, Constants.FALSE)
                            .put(Constants.ERROR, "Invalid operation: " + input.getString(Constants.OPERATION));
            }

            // Return success with the generated SQL query and bound parameters
            return new JsonObject()
                    .put(Constants.SUCCESS, Constants.TRUE)
                    .put(Constants.QUERY, query.toString())
                    .put(Constants.PARAMS, params);
        }
        catch (Exception exception)
        {
            LOGGER.error("Error building query {}", exception.getMessage());

            // Return error if query building fails
            return new JsonObject()
                .put(Constants.SUCCESS, Constants.FALSE)
                    .put(Constants.ERROR, exception.getMessage());
        }
    }

    /**
     * Builds a WHERE clause with positional parameter binding.
     *
     * @param conditions       JSON object containing column-value pairs for the WHERE clause.
     * @param params           JSON array to collect parameter values in positional order.
     * @param paramStartIndex  Index at which to start positional parameters (e.g., $1, $2).
     * @return WHERE clause string (e.g., "WHERE col1 = $1 AND col2 = $2")
     */
    public static String buildWhereClause(JsonObject conditions, JsonArray params, int paramStartIndex)
    {
        var clause = new StringBuilder("WHERE ");

        var i = 0;

        for (var key : conditions.fieldNames())
        {
            if (i > 0) clause.append(" AND ");

            clause.append(key).append(" = $").append(paramStartIndex + i);

            params.add(conditions.getValue(key));

            i++;
        }

        return clause.toString();
    }

    /**
     * Builds a comma-separated list of positional placeholders like "$1, $2, $3".
     *
     * @param count Number of placeholders to generate.
     * @return String of placeholders for use in INSERT/UPDATE operations.
     */
    public static String buildPlaceholders(int count)
    {
        var placeHolders = new StringBuilder();

        for (var index = 1; index <= count; index++)
        {
            placeHolders.append("$").append(index);

            if (index < count) placeHolders.append(Constants.COMMA_SEPARATOR);
        }

        return placeHolders.toString();
    }

    public static String buildJoinQuery(String baseQuery, int placeHolders)
    {
        return baseQuery.replace("$1" , Utils.buildPlaceholders(placeHolders));
    }
}