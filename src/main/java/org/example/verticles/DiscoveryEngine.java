package org.example.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.service.database.DatabaseService;
import org.example.service.database.Database;
import org.example.utils.Constants;
import org.example.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

/**
 * Verticle responsible for handling discovery of devices using a multi-step process.
 * It fetches device data, runs ping checks using fping, performs SSH-based checks via a Go plugin,
 * and updates the discovery status in the database accordingly.
 */
public class DiscoveryEngine extends AbstractVerticle
{
    // Logger for this class
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryEngine.class);

    // Key used in response to indicate discovery step
    private static final String STEP = "step";

    // Step name used when ping fails
    private static final String FAILURE_STEP_PING  = "ping";

    // SQL query to update discovery status
    private static final String UPDATE_DISCOVERY_RESULT_QUERY = "UPDATE discovery_profiles SET status = $1 WHERE id = $2";

private static final String FETCH_DISCOVERY_PROFILES_QUERY = "SELECT dp.id, dp.ip, dp.port, cp.credentials, cp.system_type FROM discovery_profiles dp " +
        "JOIN credential_profiles cp ON dp.credential_profile_id = cp.id " +
        "WHERE dp.id IN ($1)";

    // Service proxy for interacting with the database
    private static final DatabaseService DATABASE_SERVICE = DatabaseService.createProxy(Database.DB_SERVICE_ADDRESS);

    private MessageConsumer<JsonArray> localConsumer;
    /**
     * Called when the verticle is deployed.
     * Registers the consumer to handle discovery events on the event bus.
     *
     * @param startPromise the promise to be completed when verticle starts
     */
    @Override
    public void start(Promise<Void> startPromise)
    {
        // Initialize the discovery service by consuming the event bus address
        localConsumer = vertx.eventBus().localConsumer(Constants.DISCOVERY_ADDRESS, this::handleDiscoveryRequest);

        startPromise.complete();
    }

    /**
     * Handles the discovery request by processing the device IDs,
     * running fping and SSH checks, and updating database with results.
     *
     * @param discoveryRequest the message containing an array of discovery profile IDs
     */
    private void handleDiscoveryRequest(Message<JsonArray> discoveryRequest)
    {
        try
        {
            var deviceIds = discoveryRequest.body();

            // Execute database query to fetch device data
            DATABASE_SERVICE
                    .executeQuery(new JsonObject()
                            .put(Constants.QUERY, Utils.buildJoinQuery(FETCH_DISCOVERY_PROFILES_QUERY,deviceIds.size()))
                            .put(Constants.PARAMS, deviceIds))
                    .onSuccess(asyncResult ->
                    {
                        LOGGER.info(asyncResult.toString());

                        // Run discovery process in a blocking thread
                        vertx.executeBlocking(() ->
                        {
                            var discoveryResponse = new JsonArray();

                            // If no data is returned, return early
                            if (asyncResult.getJsonArray(Constants.RESPONSE).isEmpty())
                            {
                                LOGGER.info("No discovery profiles found for IDs: {}", discoveryRequest.body());

                                return new DiscoveryResult(discoveryResponse, "No discovery profiles found");
                            }

                            var devicesData = asyncResult.getJsonArray(Constants.RESPONSE);

                            var idToDeviceDataMap = new HashMap<Integer, JsonObject>();

                            // Map device ID to its full data for quick lookup
                            for (var index = 0; index < devicesData.size(); index++)
                            {
                                idToDeviceDataMap.put(devicesData.getJsonObject(index).getInteger(Constants.ID),
                                        devicesData.getJsonObject(index));
                            }

                            LOGGER.info("Processing fping of : {}", devicesData);

                            // Run fping to check which devices are reachable
                            var pingResult = Utils.ping(devicesData);

                            if (pingResult.isEmpty())
                            {
                                LOGGER.error("ping processing failed");

                                return new DiscoveryResult(discoveryResponse, "ping processing failed");
                            }

                            //data of devices that are eligible for port and ssh connection check
                            var sshFilteredDevicesData = new JsonArray();

                            // Filter ping result to select devices that are UP
                            for (var index = 0; index < pingResult.size(); index++)
                            {
                                var pingOutput = pingResult.getJsonObject(index);

                                if (pingOutput.getString(Constants.STATUS).equals(Constants.UP))
                                {
                                    sshFilteredDevicesData.add(idToDeviceDataMap.get(pingOutput.getInteger(Constants.ID)));
                                }
                                else
                                {
                                    discoveryResponse.add(new JsonObject()
                                            .put(Constants.ID, pingOutput.getInteger(Constants.ID))
                                            .put(Constants.SUCCESS, Constants.FALSE)
                                            .put(STEP, FAILURE_STEP_PING));
                                }
                            }

                            // If no devices passed ping, return early
                            if (sshFilteredDevicesData.isEmpty())
                            {
                                return new DiscoveryResult(discoveryResponse, null);
                            }

                            LOGGER.info("Processing SSH discovery of : {}", sshFilteredDevicesData);

                            // Run SSH discovery using Go plugin
                            var pluginOutput = Utils.spawnGoPlugin(sshFilteredDevicesData, Constants.DISCOVERY);

                            if (pluginOutput.isEmpty())
                            {
                                LOGGER.error("Go plugin execution failed");

                                return new DiscoveryResult(discoveryResponse.clear(), "Go plugin execution failed");
                            }

                            LOGGER.info("Discovery Process Completed");

                            // Add plugin output to the response

                            for (var index = 0; index < pluginOutput.size(); index++)
                            {
                                var pluginResult = pluginOutput.getJsonObject(index);

                                discoveryResponse.add(new JsonObject()
                                        .put(Constants.ID, pluginResult.getInteger(Constants.ID))
                                        .put(Constants.SUCCESS, pluginResult.getBoolean(Constants.SUCCESS))
                                        .put(STEP, pluginResult.getString(STEP)));
                            }

                            LOGGER.info("Discovery response prepared");

                            var batchParams = new JsonArray();

                            // Prepare parameters for batch update to DB
                            for (var index = 0; index < discoveryResponse.size(); index++)
                            {
                                var responseObject = discoveryResponse.getJsonObject(index);

                                batchParams.add(new JsonArray()
                                        .add(responseObject.getBoolean(Constants.SUCCESS))
                                        .add(responseObject.getInteger(Constants.ID)));
                            }

                            LOGGER.info("Batch parmams prepared Completed");

                            var dbPromise = Promise.<DiscoveryResult>promise();

                            // Send batch update request to database
                            DATABASE_SERVICE.executeQuery(new JsonObject()
                                            .put(Constants.QUERY, UPDATE_DISCOVERY_RESULT_QUERY)
                                            .put(Constants.PARAMS, batchParams))
                                    .onSuccess(updateResult ->
                                    {
                                        LOGGER.info("Database update successful");

                                        dbPromise.complete(new DiscoveryResult(discoveryResponse, null));
                                    })
                                    .onFailure(updateError ->
                                    {
                                        LOGGER.error("Database update failed: {}", updateError.getMessage());

                                        dbPromise.complete(new DiscoveryResult(discoveryResponse.clear(),
                                                "Database update failed: " + updateError.getMessage()));
                                    });

                            try
                            {
                                // Wait for DB update to complete and return result
                                return dbPromise.future().toCompletionStage().toCompletableFuture().get();
                            }
                            catch (Exception exception)
                            {
                                LOGGER.error("Error in waiting for database response: {}", exception.getMessage());

                                return new DiscoveryResult(discoveryResponse.clear(),
                                        "Error in waiting for database response: " + exception.getMessage());
                            }

                        }, false, result ->
                        {
                            // Final result processing after blocking call
                            if (result.failed())
                            {
                                LOGGER.error("Discovery processing failed: {}", result.cause().getMessage());

                                discoveryRequest.fail(500, result.cause().getMessage());
                            }
                            else
                            {
                                if (result.result().isSuccess())
                                {
                                    discoveryRequest.reply(result.result().discoveryResponse());
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
                        // If initial query to fetch devices failed
                        LOGGER.error("Database query failed: {}", error.getMessage());

                        discoveryRequest.fail(500, error.getMessage());
                    });
        }
        catch (Exception exception)
        {
            // Catch any unexpected exceptions
            LOGGER.error("Unexpected error in discovery request: {}", exception.getMessage());

            discoveryRequest.fail(500, "Internal server error");
        }
    }

    /**
     * Wrapper class used to store discovery results including
     * the response array and any error message.
     *
     * @param discoveryResponse the array of result objects (per device)
     * @param errorMessage error message string, null if successful
     */
    private record DiscoveryResult(JsonArray discoveryResponse, String errorMessage)
    {
        /**
         * Indicates whether discovery was successful.
         *
         * @return true if no error occurred, false otherwise
         */
        public boolean isSuccess()
        {
            return errorMessage == null;
        }
    }

    /**
     * Stops the verticle, unregistering the event bus consumer and cleaning up.
     */
    @Override
    public void stop(Promise<Void> stopPromise) throws Exception
    {
        if (localConsumer != null)
        {
            localConsumer.unregister()
                    .onSuccess(v -> LOGGER.info("DiscoveryEngine event bus consumer unregistered."))
                    .onFailure(err -> LOGGER.error("Failed to unregister event bus consumer: {}", err.getMessage()));
        }

        LOGGER.info("PollingProcessorEngine stopped.");

        stopPromise.complete();
    }
}
