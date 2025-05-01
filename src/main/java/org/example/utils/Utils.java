package org.example.utils;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Utils
{
    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

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
            String path  = context.normalizedPath().split("/")[2];

            var method = context.request().method().name();

            return switch (path)
            {
                case Constants.CREDENTIALS -> Constants.CREDENTIAL_PROFILES_TABLE_NAME;

                case Constants.DISCOVERY -> Constants.DISCOVERY_PROFILES_TABLE_NAME;

                case Constants.PROVISION -> (method.equals("POST") || method.equals("DELETE"))
                        ? Constants.PROVISIONING_JOBS_TABLE_NAME
                        : Constants.PROVISIONED_DATA_TABLE_NAME;

                default -> "";
            };
        }
        catch (Exception exception)
        {
            logger.error("Error getting table name from context: {}", exception.getMessage());

            return "";
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
                var device = devices.getJsonObject(i);

                ipList.add(device.getString(Constants.IP));

                ipToIdMap.put(device.getString(Constants.IP), device.getInteger(Constants.ID));
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
                    var ip = line.split(":")[0].trim();

                    var isDown = line.contains("100%");

                    devices.add(new JsonObject()
                            .put(Constants.ID, ipToIdMap.get(ip))
                            .put(Constants.STATUS, isDown ? Constants.DOWN : Constants.UP));
                }
            }

            if (!process.waitFor(1, TimeUnit.SECONDS))
            {
                logger.error("fping timeout! Process killed.");

                process.destroyForcibly();

                return new JsonArray(); // Empty response on timeout
            }

            var exitCode = process.exitValue();

            if (exitCode != 0 && exitCode != 1)
            {
                logger.error("fping exited abnormally with code {}", exitCode);

                return new JsonArray(); // Empty response on abnormal exit
            }

            return devices;
        }
        catch (Exception exception)
        {
            Thread.currentThread().interrupt();

            logger.error("Error during fping execution: {}", exception.getMessage());

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

    //todo :- change String to JsonArray after integrating new plugin
    public static String runGoPlugin(JsonArray devices , String mode)
    {
        var output = new StringBuilder();

        var result = new JsonArray();

        try
        {
            var process = new ProcessBuilder("go/ssh-plugin", mode).start();

            try (var writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream())))
            {
                writer.write(devices.toString());

                writer.flush();
            }

            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
            {
                var line = "";

                while ((line = reader.readLine()) != null)
                {
                    output.append(line);
                }
            }

            var exitCode = process.waitFor();

            if (exitCode != 0)
            {
                logger.warn("Plugin exited with non-zero code: {}",exitCode);

            }
        }
        catch (Exception exception)
        {
            logger.error("Error during SSH discovery {}", exception.getMessage());

            return "";
        }

        return output.toString();
    }

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
            logger.error("Error building query {}", exception.getMessage());

            return new JsonObject()
                    .put(Constants.SUCCESS, false)
                    .put(Constants.ERROR, exception.getMessage());
        }
    }
}
