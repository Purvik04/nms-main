//package org.example.utils;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.networknt.schema.JsonSchema;
//import com.networknt.schema.JsonSchemaFactory;
//import com.networknt.schema.SpecVersion;
//import com.networknt.schema.ValidationMessage;
//import org.example.Main;
//
//import java.util.*;
//
//public class RequestValidator2
//{
//    private static final Map<String, JsonSchema> schemaCache = new HashMap<>();
//
//    private static final ObjectMapper objectMapper = new ObjectMapper();
//
//    /**
//     * Load schema at startup and cache them
//     */
//    public static void initialize()
//    {
//        loadSchema(Constants.CREDENTIAL_PROFILES_TABLE_NAME, Constants.CREDENTIAL_PROFILES_SCHEMA_PATH);
//
//        loadSchema(Constants.DISCOVERY_PROFILES_TABLE_NAME, Constants.DISCOVERY_PROFILES_SCHEMA_PATH);
//    }
//
//    private static void loadSchema(String schemaName, String schemaFilePath)
//    {
//        try
//        {
//            var schemaContent = Main.getVertx().fileSystem().readFileBlocking(schemaFilePath).toString();
//
//            var schemaNode = objectMapper.readTree(schemaContent);
//
//            var factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
//
//            var schema = factory.getSchema(schemaNode);
//
//            schemaCache.put(schemaName, schema);
//        }
//        catch (Exception e)
//        {
//            throw new RuntimeException("Failed to load schema from " + schemaFilePath, e);
//        }
//    }
//
//    /**
//     * Validate the given request body against the named schema
//     *
//     * @param schemaName the schema name used in cache
//     * @param requestBody Vert.x JsonObject (will be converted)
//     * @return null if valid, or error message string
//     */
//    public static String validate(String schemaName, io.vertx.core.json.JsonObject requestBody)
//    {
//        var schema = schemaCache.get(schemaName);
//
//        if (schema == null)
//        {
//            return "Schema not found for " + schemaName;
//        }
//
//        try
//        {
//            var jsonNode = objectMapper.readTree(requestBody.encode());
//
//            var validationMessages = schema.validate(jsonNode);
//
//            if (validationMessages.isEmpty())
//            {
//                return null;
//            }
//
//            var errors = new StringBuilder();
//
//            for (ValidationMessage message : validationMessages)
//            {
//                if (!errors.isEmpty()) errors.append("; \n");
//
//                errors.append(message.getMessage());
//            }
//
//            return errors.toString();
//        }
//        catch (Exception e)
//        {
//            return "Validation failed: " + e.getMessage();
//        }
//    }
//}
//
