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

    public static String buildPlaceholders(int count)
    {
        var sb = new StringBuilder();

        for (int i = 1; i <= count; i++)
        {
            sb.append("$").append(i);

            if (i < count) sb.append(", ");
        }

        return sb.toString();
    }

    public static String getTableNameFromContext(RoutingContext context)
    {
        try
        {
            String path  = context.normalizedPath().split(Constants.PATH_SEPARATOR)[2];

            var method = context.request().method().name();

            return switch (path)
            {
                case Constants.CREDENTIALS_PATH -> Constants.CREDENTIAL_PROFILES_TABLE_NAME;

                case Constants.DISCOVERY_PATH -> Constants.DISCOVERY_PROFILES_TABLE_NAME;

                case Constants.PROVISION_PATH -> (method.equals(HTTP_METHOD_POST) || method.equals(HTTP_METHOD_DELETE))
                        ? Constants.PROVISIONING_JOBS_TABLE_NAME
                        : Constants.PROVISIONED_DATA_TABLE_NAME;

                default -> Constants.EMPTY_STRING;
            };
        }
        catch (Exception exception)
        {
            LOGGER.error("Error getting table name from context: {}", exception.getMessage());

            return Constants.EMPTY_STRING;
        }
    }

    public static JsonArray runFping(JsonArray devices)
{
    Process process = null;

    try
    {
        var ipToIdMap = new HashMap<String, Integer>();

        var ipList = new ArrayList<String>(devices.size());

        for (int i = 0; i < devices.size(); i++)
        {
            ipList.add(devices.getJsonObject(i).getString(Constants.IP));

            ipToIdMap.put(devices.getJsonObject(i).getString(Constants.IP),
                    devices.getJsonObject(i).getInteger(Constants.ID));
        }

        devices.clear();

        var command = new ArrayList<String>();

        command.add("fping");
        command.add("-c");
        command.add("3");
        command.add("-q");
        command.add("-t");
        command.add("500");
        command.add("-p");
        command.add("0");
        command.addAll(ipList);

        process = new ProcessBuilder(command).start();

        try (var reader = new BufferedReader(new InputStreamReader(process.getErrorStream())))
        {
            String line;

            while ((line = reader.readLine()) != null)
            {
                var ip = line.split(Constants.COLON_SEPARATOR)[0].trim();

                var isDown = line.contains("100%");

                devices.add(new JsonObject()
                        .put(Constants.ID, ipToIdMap.get(ip))
                        .put(Constants.STATUS, isDown ? Constants.DOWN : Constants.UP));
            }
        }

        if (!process.waitFor(1, TimeUnit.SECONDS))
        {
            LOGGER.error("Ping Process timeout! Process killed.");

            process.destroyForcibly();

            return devices; // Empty response on timeout
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
        Thread.currentThread().interrupt();

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


    private static void parseFpingResult(String summaryLine, JsonObject result, int id)
    {
        try
        {
            // Example: 192.168.1.1 : xmt/rcv/%loss = 3/3/0%, min/avg/max = ...
            var parts = summaryLine.split(Constants.EQUALS_SEPARATOR);

            if (parts.length >= 2)
            {
                var stats = parts[1].split(Constants.COMMA_SEPARATOR)[0].trim().split(Constants.PATH_SEPARATOR);

                var loss = Integer.parseInt(stats[2].replace(Constants.PERCENTAGE_SEPARATOR, Constants.EMPTY_STRING));

                result.put(Constants.ID, id).put(Constants.PACKET_SEND, Integer.parseInt(stats[0]))
                        .put(Constants.PACKET_RECEIVE, Integer.parseInt(stats[1]))
                        .put(Constants.PACKET_LOSS_PERCENTAGE,loss)
                        .put(Constants.STATUS, loss == LOSS_PERCENTAGE_100 ? Constants.DOWN : Constants.UP);
            }
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in parsing ping result: {}", exception.getMessage());

            result.put(Constants.ID, id).put(Constants.PACKET_SEND,DEFAULT_PACKET_SEND)
                    .put(Constants.PACKET_RECEIVE,DEFAULT_PACKET_RECEIVE)
                    .put(Constants.PACKET_LOSS_PERCENTAGE,LOSS_PERCENTAGE_100).put(Constants.STATUS, Constants.DOWN);
        }
    }

    public static JsonArray runGoPluginSecure(JsonArray devices , String mode)
    {
        var result = new JsonArray();

        var file = new File(SECURE_COMPRESSED_FILE_PATH);

        var filePath = file.getAbsolutePath();

        Process process = null;

        try
        {
            SecureCompressor.writeEncryptedSnappyFile(devices,filePath);

            process = new ProcessBuilder("go/ssh-plugin", mode, filePath).start();

            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
            {
                var line = "";

                while ((line = reader.readLine()) != null)
                {
                    if(mode.equals(Constants.DISCOVERY_MODE))
                    {
                        result.add(line.trim());
                    }
                    else
                    {
                        processPollingResult(result,line);
                    }
                }
            }

            boolean finished = process.waitFor(5, TimeUnit.SECONDS);

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
            if (process != null && process.isAlive())
            {
                process.destroyForcibly();
            }

            // Cleanup temporary file
            try
            {
                Files.deleteIfExists(file.toPath());
            }
            catch (Exception exception)
            {
                LOGGER.error("Error deleting temp file {}: {}", filePath, exception.getMessage());
            }
        }

        return result;
    }

    public static void processPollingResult(JsonArray devices , String device)
    {
        try
        {
            devices.add(SecureCompressor.decryptLine(device.trim()));
        }
        catch (Exception exception)
        {
            LOGGER.error("Decryption error for line: {}, error: {}", device, exception.getMessage());
        }
    }

    public static JsonObject buildQuery(JsonObject input, StringBuilder query, JsonArray params)
    {
        try
        {
            var table = input.getString(Constants.TABLE_NAME);

            var data = input.getJsonObject(Constants.DATA, new JsonObject());

            var conditions = input.getJsonObject(Constants.CONDITIONS, new JsonObject());

            var columns = input.getJsonArray(Constants.COLUMNS, new JsonArray());

            switch (input.getString(Constants.OPERATION).toLowerCase())
            {
                case Constants.DB_INSERT:

                    var keys = data.fieldNames();

                    var columnsStr = String.join(", ", keys);

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

                    var columnStr = (columns != null && !columns.isEmpty())
                            ? String.join(", ", columns.stream().map(Object::toString).toList())
                            : "*";

                    query.append("SELECT ").append(columnStr).append(" FROM ").append(table);

                    if (!conditions.isEmpty())
                    {
                        var whereClause = Utils.buildWhereClause(conditions, params, 1);

                        query.append(" ").append(whereClause);
                    }
                    break;

                case Constants.DB_UPDATE:

                    query.append("UPDATE ").append(table).append(" SET ");

                    var index = 1;

                    for (var key : data.fieldNames())
                    {
                        query.append(key).append(" = $").append(index++);

                        if (index <= data.size()) query.append(", ");

                        params.add(data.getValue(key));
                    }

                    if (!conditions.isEmpty())
                    {
                        var whereClause = Utils.buildWhereClause(conditions, params, index);

                        query.append(" ").append(whereClause);
                    }
                    break;

                case Constants.DB_DELETE:

                    query.append("DELETE FROM ").append(table);

                    if (!conditions.isEmpty())
                    {
                        var whereClause = Utils.buildWhereClause(conditions, params, 1);

                        query.append(" ").append(whereClause);
                    }
                    break;

                default:

                    return new JsonObject()
                            .put(Constants.SUCCESS, false)
                            .put(Constants.ERROR, "Invalid operation: " + input.getString(Constants.OPERATION));
            }

            return new JsonObject()
                    .put(Constants.SUCCESS, true)
                    .put(Constants.QUERY, query.toString())
                    .put(Constants.PARAMS, params);
        }
        catch (Exception exception)
        {
            LOGGER.error("Error building query {}", exception.getMessage());

            return new JsonObject()
                    .put(Constants.SUCCESS, false)
                    .put(Constants.ERROR, exception.getMessage());
        }
    }
}
