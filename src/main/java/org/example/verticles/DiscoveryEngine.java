package org.example.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.service.database.DatabaseService;
import org.example.service.database.Database;
import org.example.utils.Constants;
import org.example.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

public class DiscoveryEngine extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryEngine.class);

    private static final String STEP = "step";

    private static final String FAILURE_STEP_PING  = "ping";

    private static final String UPDATE_DISCOVERY_RESULT_QUERY = "UPDATE discovery_profiles SET status = $1 WHERE id = $2";

    private static final DatabaseService DATABASE_SERVICE = DatabaseService.createProxy(Database.DB_SERVICE_ADDRESS);

    @Override
    public void start(Promise<Void> startPromise)
    {
        // Initialize the discovery service
        vertx.eventBus().localConsumer(Constants.EVENTBUS_DISCOVERY_ADDRESS, this::handleDiscoveryRequest);

        startPromise.complete();
    }

    private void handleDiscoveryRequest(Message<JsonArray> discoveryRequest)
    {
        try
        {
            // Step 1: Fetch device data from database
            String fetchQuery = "SELECT dp.id, dp.ip, dp.port, cp.credentials FROM discovery_profiles dp " +
                    "JOIN credential_profiles cp ON dp.credential_profile_id = cp.id " +
                    "WHERE dp.id IN (" + Utils.buildPlaceholders(discoveryRequest.body().size()) + ")";

            JsonObject request = new JsonObject()
                    .put(Constants.QUERY, fetchQuery)
                    .put(Constants.PARAMS, discoveryRequest.body());

            DATABASE_SERVICE
                    .executeQuery(request)
                    .onSuccess(asyncResult ->
                    {
                        LOGGER.info(asyncResult.toString());

                        vertx.executeBlocking(() ->
                        {
                            var responseArray = new JsonArray();

                            if (asyncResult.getJsonArray(Constants.DATA).isEmpty())
                            {
                                LOGGER.info("No discovery profiles found for IDs: {}",discoveryRequest.body());

                                return new DiscoveryResult(responseArray, "No discovery profiles found");
                            }

                            var deviceData = asyncResult.getJsonArray(Constants.DATA);

                            var idToDeviceMap = new HashMap<Integer, JsonObject>();

                            LOGGER.info("Processing fping of : {}", deviceData);

                            for (var i = 0; i < deviceData.size(); i++)
                            {
                                idToDeviceMap.put(deviceData.getJsonObject(i).getInteger(Constants.ID),
                                        deviceData.getJsonObject(i));
                            }

                            var pingResult = Utils.runFping(deviceData);

                            if (pingResult.isEmpty())
                            {
                                LOGGER.error("ping processing failed");

                                return new DiscoveryResult(responseArray, "ping processing failed");
                            }

                            var sshFilteredDevices = new JsonArray();

                            for(var i = 0; i < pingResult.size(); i++)
                            {
                                var pingOutput = pingResult.getJsonObject(i);

                                if (pingOutput.getString(Constants.STATUS, Constants.DOWN).equalsIgnoreCase(Constants.UP))
                                {
                                    sshFilteredDevices.add(idToDeviceMap.get(pingOutput.getInteger(Constants.ID)));
                                }
                                else
                                {
                                    responseArray.add(new JsonObject()
                                            .put(Constants.ID, pingOutput.getInteger(Constants.ID))
                                            .put(Constants.STATUS, pingOutput.getString(Constants.STATUS))
                                            .put(STEP, FAILURE_STEP_PING));
                                }
                            }

                            if(sshFilteredDevices.isEmpty())
                            {
                                return new DiscoveryResult(responseArray, null);
                            }

                            LOGGER.info("Processing SSH discovery of : {}", sshFilteredDevices);

                            var pluginOutput = Utils.runGoPluginSecure(sshFilteredDevices, Constants.DISCOVERY_MODE);

                            if (pluginOutput.isEmpty())
                            {
                                LOGGER.error("Go plugin execution failed");

                                return new DiscoveryResult(responseArray.clear(), "Go plugin execution failed");
                            }

                            LOGGER.info("Discovery Process Completed");

                            for (var i = 0; i < pluginOutput.size(); i++)
                            {
                                var pluginResult = pluginOutput.getJsonObject(i);

                                responseArray.add(new JsonObject()
                                        .put(Constants.ID, pluginResult.getInteger(Constants.ID))
                                        .put(Constants.STATUS, pluginResult.getString(Constants.STATUS))
                                                .put(STEP, pluginResult.getString(STEP)));
                            }

                            var batchParams = new JsonArray();

                            // Update the response array in-place
                            for (var i = 0; i < responseArray.size(); i++)
                            {
                                batchParams.add(new JsonArray()
                                        .add(responseArray.getJsonObject(i).getBoolean(Constants.SUCCESS))
                                        .add(responseArray.getJsonObject(i).getInteger(Constants.ID)));
                            }

                            // Prepare batch update
                            var updateRequest = new JsonObject()
                                    .put(Constants.QUERY,UPDATE_DISCOVERY_RESULT_QUERY)
                                    .put(Constants.PARAMS, batchParams);

                            // Create a Future to hold the database update result
                            var dbPromise = Promise.<DiscoveryResult>promise();

                            DATABASE_SERVICE.executeQuery(updateRequest)
                                    .onSuccess(updateResult ->
                                    {
                                        LOGGER.info("Database update successful");

                                        dbPromise.complete(new DiscoveryResult(responseArray, null));
                                    })
                                    .onFailure(updateError ->
                                    {
                                        LOGGER.error("Database update failed: {}", updateError.getMessage());

                                        dbPromise.complete(new DiscoveryResult(responseArray.clear(), "Database update failed: " + updateError.getMessage()));
                                    });

                            try
                            {
                                return dbPromise.future().toCompletionStage().toCompletableFuture().get();
                            }
                            catch (Exception exception)
                            {
                                LOGGER.error("Error in waiting for database response: {}",exception.getMessage());

                                return new DiscoveryResult(responseArray.clear(), "Error in waiting for database response: " + exception.getMessage());
                            }

                        }, false, result ->
                        {
                            if (result.failed())
                            {
                                LOGGER.error("Discovery processing failed: {}", result.cause().getMessage());

                                discoveryRequest.fail(500, result.cause().getMessage());
                            }
                            else
                            {
                                if (result.result().isSuccess())
                                {
                                    discoveryRequest.reply(result.result().responseArray());
                                }
                                else
                                {
                                    LOGGER.error("Discovery failed: {}", result.result().errorMessage());

                                    discoveryRequest.fail(500, result.result().errorMessage());
                                }
                            }
                        });

                    }).onFailure(error ->
                    {
                        LOGGER.error("Database query failed: {}", error.getMessage());

                        discoveryRequest.fail(500, error.getMessage());
                    });
        }
        catch (Exception exception)
        {
            LOGGER.error("Unexpected error in discovery request: {}", exception.getMessage());

            discoveryRequest.fail(500, "Internal server error");
        }
    }

    // A simple wrapper class to handle both success and failure cases
    private record DiscoveryResult(JsonArray responseArray, String errorMessage)
    {
        public boolean isSuccess()
        {
            return errorMessage == null;
        }
    }
}

