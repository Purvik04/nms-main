package org.example.utils;

import io.vertx.core.json.JsonObject;
import io.vertx.json.schema.Draft;
import io.vertx.json.schema.JsonSchema;
import io.vertx.json.schema.JsonSchemaOptions;
import io.vertx.json.schema.Validator;
import org.example.Main;

import java.util.HashMap;
import java.util.Map;

/**
 * Validator for JSON requests using latest Vert.x JSON Schema API.
 */
public class RequestValidator
{
    private static final Map<String, JsonSchema> schemaCache = new HashMap<>();
    private static JsonSchemaOptions options;
    private static final String ERROR_FORMATTER_SEMICOLON = "; ";
    private static final String ERROR_FORMATTER_COLON = ": ";

    /**
     * Initialize the validator and load schemas from files into the schemaCache
     */
    public static void initialize()
    {
        options = new JsonSchemaOptions()
                .setDraft(Draft.DRAFT7)
                .setBaseUri("http://localhost:8080");

        // Load schemas during initialization
        loadSchema(Constants.CREDENTIAL_PROFILES_TABLE_NAME, Constants.CREDENTIAL_PROFILES_SCHEMA_PATH);
        loadSchema(Constants.DISCOVERY_PROFILES_TABLE_NAME, Constants.DISCOVERY_PROFILES_SCHEMA_PATH);
    }

    /**
     * Load a schema from a file and cache it
     * @param schemaName Name to reference the schema
     * @param filePath Path to the schema file
     */
    public static void loadSchema(String schemaName, String filePath)
    {
        try
        {
            var schemaContent = Main.getVertx().fileSystem().readFileBlocking(filePath).toString();

            // Parse the schema content and cache it
            schemaCache.put(schemaName, JsonSchema.of(new JsonObject(schemaContent)));
        }
        catch (Exception exception)
        {
            throw new RuntimeException("Failed to load schema from " + filePath, exception);
        }
    }

    /**
     * Validates a request body against a named schema
     * @param schemaName The name of the schema to validate against
     * @param requestBody The JSON request body to validate
     * @return ValidationResult containing success status and error messages if any
     */
    public static String validate(String schemaName, JsonObject requestBody)
    {
        if (schemaCache.get(schemaName) == null)
        {
            return "Schema not found";
        }

        try
        {
            var result = Validator.create(schemaCache.get(schemaName), options).validate(requestBody);

            if (Boolean.TRUE.equals(result.getValid()))
            {
                return null;
            }
            else
            {
                var errorMessage = new StringBuilder();

                result.getErrors().forEach(error ->
                {
                    if (!errorMessage.isEmpty())
                    {
                        errorMessage.append(ERROR_FORMATTER_SEMICOLON);
                    }

                    errorMessage.append(error.getInstanceLocation()).append(ERROR_FORMATTER_COLON).append(error.getError());
                });

                return errorMessage.toString();
            }
        }
        catch (Exception exception)
        {
            return "Validation failed: " + exception.getMessage();
        }
    }
}