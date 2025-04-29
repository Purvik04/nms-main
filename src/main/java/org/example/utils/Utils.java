package org.example.utils;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class Utils
{
    private static final String REGEX_IPV4 = "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)(\\.|$)){4}$";

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

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
        String path  = context.normalizedPath().split("/")[2];

        var method = context.request().method().name();

        return switch (path)
        {
            case Constants.CREDENTIALS -> "credential_profiles";

            case Constants.DISCOVERY -> "discovery_profiles";

            case Constants.PROVISION -> (method.equals("POST") || method.equals("DELETE"))
                    ? "provisioning_jobs"
                    : "provisioned_data";

            default -> "";
        };
    }

    public static boolean isValidIPv4(String ip)
    {
        return ip != null && ip.matches(REGEX_IPV4);
    }

    public static boolean validateRequest(JsonObject requestBody,RoutingContext context) {

        var tableName = getTableNameFromContext(context);

        return switch (tableName)
        {
            case "credential_profiles" -> validateCredentialProfiles(requestBody);

            case "discovery_profiles" -> validateDiscoveryProfiles(requestBody);

            default -> false;
        };
    }

    private static boolean validateCredentialProfiles(JsonObject requestBody)
    {
        return requestBody.containsKey("credential_profile_name")
                && requestBody.getValue("credential_profile_name") instanceof String
                && requestBody.containsKey("system_type")
                && requestBody.getValue("system_type") instanceof String
                && requestBody.containsKey(Constants.CREDENTIALS)
                && requestBody.getValue(Constants.CREDENTIALS) instanceof JsonObject;
    }

    private static boolean validateDiscoveryProfiles(JsonObject requestBody)
    {
        return requestBody.containsKey("discovery_profile_name")
                && requestBody.getValue("discovery_profile_name") instanceof String
                && requestBody.containsKey("credential_profile_id")
                && requestBody.getValue("credential_profile_id") instanceof Integer
                && requestBody.containsKey(Constants.IP)
                && requestBody.getValue(Constants.IP) instanceof String
                && isValidIPv4(requestBody.getValue(Constants.IP).toString())
                && requestBody.containsKey(Constants.PORT)
                && requestBody.getValue(Constants.PORT) instanceof Integer;
    }

    public static JsonArray runFping(JsonArray devices)
    {
        var ipToIdMap = new HashMap<String, Integer>();

        var ipList = new ArrayList<String>(devices.size());

        for (int i = 0; i < devices.size(); i++)
        {
            var device = devices.getJsonObject(i);

            ipList.add(device.getString(Constants.IP));

            ipToIdMap.put(device.getString(Constants.IP), device.getInteger(Constants.ID));
        }

        var resultArray = new JsonArray();

        Process process = null;

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

        try
        {
            process = new ProcessBuilder(command).start();

            try (var reader = new BufferedReader(new InputStreamReader(process.getErrorStream())))
            {
                String line;

                while ((line = reader.readLine()) != null)
                {
                    var ip = line.split(":")[0].trim();

                    var isDown = line.contains("100%");

                    resultArray.add(new JsonObject()
                            .put(Constants.ID, ipToIdMap.get(ip))
                            .put("status", isDown ? "DOWN" : "UP"));
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

            return resultArray;
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

    public static String runGoPlugin(JsonArray devices , String mode)
    {
        var output = new StringBuilder();

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
                String line;

                while ((line = reader.readLine()) != null)
                {
                    output.append(line);
                }
            }

            var exitCode = process.waitFor();

            if (exitCode != 0)
            {
                logger.warn("Plugin exited with non-zero code: {}",exitCode);

                return "";
            }
        }
        catch (Exception exception)
        {
            logger.error("Error during SSH discovery {}", exception.getMessage());

            return "";
        }

        return output.toString();
    }
}
