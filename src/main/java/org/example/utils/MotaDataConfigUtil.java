package org.example.utils;

import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class MotaDataConfigUtil
{
    private MotaDataConfigUtil() {}

    private static JsonObject config = null;

    public static synchronized void loadConfig(String path) throws IOException,NullPointerException
    {
        if (config != null)
        {
            return; // already loaded
        }

        try(var inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path))
        {
            if (inputStream == null)
            {
                throw new NullPointerException("Config file not found in classpath: " + path);
            }

            config = new JsonObject(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    public static JsonObject getConfig()
    {
        return config;
    }
}

