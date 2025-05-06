package org.example.utils;

import io.vertx.core.json.JsonObject;
import io.vertx.json.schema.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Validator for JSON requests using latest Vert.x JSON Schema API.
 */
public class RequestValidator
{
    private static final Map<String, JsonSchema> SCHEMA_CACHE = new HashMap<>();

    private static final String BASE_URI = "http://localhost:8080/";

    private static final String SCHEMA_NOT_FOUND = "Schema not found";

    private static final String VALIDATION_FAILED = "Validation failed";

    private static final String EXCEPTION_FORMATION = "Validation failed: %s %s";

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestValidator.class);

    private static JsonSchemaOptions options;

    /**
     * Private constructor to prevent instantiation
     */
    private RequestValidator() {}

    /**
     * Initialize the validator and load schemas from files into the SCHEMA_CACHE
     */
    public static void initialize() throws IOException,NullPointerException
    {
        if(SCHEMA_CACHE.isEmpty())
        {
            options = new JsonSchemaOptions()
                    .setDraft(Draft.DRAFT7)
                    .setBaseUri(BASE_URI)
                    .setOutputFormat(OutputFormat.Basic);

            // Load schemas during initialization
            loadSchema(Constants.CREDENTIAL_PROFILES_TABLE, Constants.CREDENTIAL_PROFILES_SCHEMA_PATH);

            loadSchema(Constants.DISCOVERY_PROFILES_TABLE, Constants.DISCOVERY_PROFILES_SCHEMA_PATH);
        }
    }

    /**
     * Load a schema from a file and cache it
     * @param schemaName Name to reference the schema
     * @param filePath Path to the schema file
     */
    public static void loadSchema(String schemaName, String filePath) throws IOException,NullPointerException
    {
        try(var inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(filePath))
        {
            if (inputStream == null)
            {
                throw new NullPointerException("Schema not found in classpath: " + filePath);
            }

            SCHEMA_CACHE.put(schemaName, JsonSchema.of(new JsonObject(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8))));
        }

    }

    /**
     * Validates a request body against a named schema
     * @param schema The name of the schema to validate against
     * @param request The JSON request body to validate
     * @return String containing validation errors or an empty string if valid
     */
    public static String validate(String schema, JsonObject request)
    {
        if (SCHEMA_CACHE.get(schema) == null)
        {
            return SCHEMA_NOT_FOUND;
        }

        try
        {
            var result = Validator.create(SCHEMA_CACHE.get(schema), options).validate(request);

            if (Boolean.TRUE.equals(result.getValid()))
            {
                return Constants.EMPTY_STRING;
            }
            else
            {
                var errorMessage = new StringBuilder(1000);

                if(!result.getErrors().isEmpty())
                {
                    result.getErrors().forEach(error -> errorMessage.append(error.getInstanceLocation())
                            .append(Constants.COLON_SEPARATOR)
                            .append(error.getError())
                            .append(Constants.SEMICOLON_SEPARATOR)
                            .append(Constants.LINE_SEPARATOR));
                }
                else
                {
                    errorMessage.append(VALIDATION_FAILED);
                }
                return errorMessage.toString();
            }
        }
        catch (Exception exception)
        {
            LOGGER.error("Unexpected exception while validating: {}", exception.getMessage());

            return String.format(EXCEPTION_FORMATION, VALIDATION_FAILED, exception.getMessage());
        }
    }
}