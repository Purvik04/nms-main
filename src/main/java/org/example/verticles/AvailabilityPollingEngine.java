package org.example.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.cache.AvailabilityCacheEngine;
import org.example.service.database.Database;
import org.example.service.database.DatabaseService;
import org.example.utils.Constants;
import org.example.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Verticle for polling device availability. It fetches device information from the database,
 * runs a ping test on the devices, and updates their availability status in a cache.
 */
public class AvailabilityPollingEngine extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AvailabilityPollingEngine.class);

    private static final DatabaseService DATABASE_SERVICE = DatabaseService.createProxy(Database.DB_SERVICE_ADDRESS);

    // SQL queries
    private static final String FETCH_ALL_DEVICES_ID_QUERY = "SELECT id FROM provision WHERE status = true;";

    private static final String FETCH_DEVICE_IP_QUERY = "SELECT ip, id from provision WHERE id IN ($1)";

    private static final String INSERT_PING_RESULTS_QUERY = """
            INSERT INTO availability_polling_results (provision_id, packets_send, packets_received, packet_loss_percentage,timestamp)
            VALUES ($1, $2, $3, $4,$5)
            """;

    private MessageConsumer<JsonArray> localConsumer;
    /**
     * Start method for the verticle. Initializes the polling engine by fetching device data from the database
     * and setting the initial status for each device.
     *
     * @param startPromise A promise to indicate when the verticle is started.
     */
    @Override
    public void start(Promise<Void> startPromise)
    {
        try
        {
            // Execute query to fetch all devices from the provisioning_jobs table
            DATABASE_SERVICE.executeQuery(new JsonObject().put(Constants.QUERY ,FETCH_ALL_DEVICES_ID_QUERY))
                    .onSuccess(result ->
                    {
                        // If the query was successful, fetch the devices data
                        if(Boolean.TRUE.equals(result.getBoolean(Constants.SUCCESS)))
                        {
                            var deviceData = result.getJsonArray(Constants.DATA);

                            // If devices are found, initialize their status to "DOWN"
                            if (!deviceData.isEmpty())
                            {
                                for(var index = 0; index < deviceData.size(); index++)
                                {
                                    try
                                    {
                                        // Set the device status to "DOWN" initially in the cache
                                        AvailabilityCacheEngine.setDeviceStatus(deviceData
                                                .getJsonObject(index).getInteger(Constants.ID),Constants.DOWN);
                                    }
                                    catch (Exception exception)
                                    {
                                        // Log any exception during device status setting
                                        LOGGER.error("Error in setting device status: {}", exception.getMessage());

                                        startPromise.fail(exception.getMessage());
                                    }
                                }
                            }
                            // Complete the promise once initialization is done
                            startPromise.complete();
                        }
                    })
                    .onFailure( error ->
                    {
                        // Log any failure in fetching devices and fail the promise
                        LOGGER.error("Error in fetching devices: {}", error.getMessage());

                        startPromise.fail("Error in fetching devices: " + error.getMessage());
                    });

            // Set up an event bus consumer to handle availability polling requests
            localConsumer = vertx.eventBus().localConsumer(Constants.AVAILABILITY_POLLING_ADDRESS,this::handleAvailabilityPolling);
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in deploying availability polling verticle: {}", exception.getMessage());

            startPromise.fail(exception);
        }
    }

    /**
     * Handle the availability polling request. This method processes the device IDs provided in the message,
     * fetches their IPs from the database, runs the fping utility to check their availability, and updates
     * the device status in the cache.
     *
     * @param message The message containing the device IDs for polling.
     */
    private void handleAvailabilityPolling(Message<JsonArray> message)
    {
        try
        {
            // Get the device IDs from the message body
            var deviceIds = message.body();

            // Execute the query to fetch device information
            DATABASE_SERVICE.executeQuery(new JsonObject()
                    .put(Constants.QUERY, Utils.buildJoinQuery(FETCH_DEVICE_IP_QUERY, deviceIds.size()))
                    .put(Constants.PARAMS, deviceIds)).onSuccess(result ->
                    {
                        // If the query was successful
                        if(Boolean.TRUE.equals(result.getBoolean(Constants.SUCCESS)))
                        {
                            var devicesData = result.getJsonArray(Constants.DATA);

                            var timeStamp = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z";

                            // If devices are found, execute the fping utility for availability check
                            if (!devicesData.isEmpty())
                            {
                                vertx.executeBlocking(() -> Utils.ping(devicesData), false, asyncResult ->
                                {
                                    // Handle the result of the fping utility
                                    if (asyncResult.succeeded())
                                    {
                                        var pingOutput = asyncResult.result();

                                        // If fping result is not empty, update the device status in the cache
                                        if (!pingOutput.isEmpty())
                                        {
                                            for (var index = 0; index < pingOutput.size(); index++)
                                            {
                                                try
                                                {
                                                    var deviceResult = pingOutput.getJsonObject(index);

                                                    // Set device status based on fping result (UP/DOWN)
                                                    AvailabilityCacheEngine.setDeviceStatus(deviceResult.getInteger(Constants.ID),
                                                            deviceResult.getString(Constants.STATUS));
                                                }
                                                catch (Exception exception)
                                                {
                                                    // Log any exception during status update
                                                    LOGGER.error("Error in set up device status: {}", exception.getMessage());
                                                }
                                            }

                                            updatePingResultsInDb(pingOutput, timeStamp);
                                        }
                                        else
                                        {
                                            LOGGER.error("Ping process failed");
                                        }
                                    }
                                });
                            }
                        }
                    }).onFailure(error -> LOGGER.error("Error in fetching devices for availability polling: {}"
                    , error.getMessage()));
        }
        catch (Exception exception)
        {
            // Log any exceptions that occur during the availability polling process
            LOGGER.error("Error in availability polling: {}", exception.getMessage());
        }
    }

    private static void updatePingResultsInDb(JsonArray pingOutput, String timeStamp)
    {
        try
        {
            var batchParams = new JsonArray();

            for (var index = 0; index < pingOutput.size(); index++)
            {
                var pingResult = pingOutput.getJsonObject(index);

                // Create array for each row
                var paramArray = new JsonArray()
                        .add(pingResult.getInteger(Constants.ID))
                        .add(pingResult.getInteger(Constants.PACKETS_SEND))
                        .add(pingResult.getInteger(Constants.PACKETS_RECEIVED))
                        .add(pingResult.getInteger(Constants.PACKET_LOSS_PERCENTAGE))
                        .add(timeStamp);

                batchParams.add(paramArray);
            }

            DATABASE_SERVICE.executeQuery(new JsonObject().put(Constants.QUERY, INSERT_PING_RESULTS_QUERY)
                            .put(Constants.PARAMS, batchParams)).onFailure(error ->
                                    LOGGER.error("Error in inserting ping results: {}", error.getMessage()));
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in creating batch update for ping results: {}", exception.getMessage());
        }
    }

    /**
     * Stops the verticle, unregistering the event bus consumer and cleaning up.
     */
    @Override
    public void stop(Promise<Void> stopPromise)
    {
        if (localConsumer != null)
        {
            localConsumer.unregister()
                    .onSuccess(v -> LOGGER.info("AvailabilityPollingEngine event bus consumer unregistered."))
                    .onFailure(err -> LOGGER.error("Failed to unregister event bus consumer: {}"
                            , err.getMessage()));
        }

        stopPromise.complete();
    }

}
