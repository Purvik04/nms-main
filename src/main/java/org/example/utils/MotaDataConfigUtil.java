package org.example.utils;

import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for loading and caching a JSON configuration file from the classpath.
 * The configuration is lazily loaded once and then reused.
 */
public class MotaDataConfigUtil
{
    // Private constructor to prevent instantiation
    private MotaDataConfigUtil() {}

    // Cached configuration object
    private static JsonObject config = null;

    /**
     * Loads the configuration file from the classpath and parses it into a JsonObject.
     *
     * @param path the relative path to the configuration file in the classpath
     * @throws IOException if an I/O error occurs while reading the file
     */
    public static void loadConfig(String path) throws IOException
    {
        // Avoid reloading if already loaded
        if (config != null)
        {
            return;
        }

        // Load resource from classpath
        try (var inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path))
        {
            // Throw exception if file is missing
            if (inputStream == null)
            {
                throw new NullPointerException("Config file not found in classpath: " + path);
            }

            // Read entire file content as UTF-8 string and parse it as JSON
            config = new JsonObject(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /**
     * Returns the loaded configuration.
     *
     * @return the cached JsonObject configuration, or null if not yet loaded
     */
    public static JsonObject getConfig()
    {
        return config;
    }
}
