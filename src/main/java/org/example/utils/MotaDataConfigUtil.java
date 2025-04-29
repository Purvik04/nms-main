package org.example.utils;

import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class MotaDataConfigUtil
{
    private static JsonObject config = null;

    public static synchronized void loadConfig(String path)
    {
        if (config != null)
        {
            return; // already loaded
        }

        try(var inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path))
        {
            if (inputStream == null)
            {
                throw new RuntimeException("Config file not found in classpath: " + path);
            }

            var content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            config = new JsonObject(content);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to load config file: " + path, e);
        }
    }

    public static JsonObject getConfig()
    {
        return config;
    }
}

