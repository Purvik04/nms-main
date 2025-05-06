package org.example.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.cache.AvailabilityCacheEngine;
import org.example.service.database.Database;
import org.example.service.database.DatabaseService;
import org.example.utils.Constants;
import org.example.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verticle for polling device availability. It fetches device information from the database,
 * runs a ping test on the devices, and updates their availability status in a cache.
 */
public class AvailabilityPollingEngine extends AbstractVerticle
{
    // Logger for logging messages
    private static final Logger LOGGER = LoggerFactory.getLogger(AvailabilityPollingEngine.class);

    // Database service to interact with the database
    private static final DatabaseService DATABASE_SERVICE = DatabaseService.createProxy(Database.DB_SERVICE_ADDRESS);

    // SQL query to fetch all devices from the provisioning_jobs table
    private static final String FETCH_ALL_DEVICES_ID_QUERY = "SELECT id FROM provision;";

    private static final String FETCH_DEVICE_IP_QUERY = "SELECT ip, id from provision WHERE id IN ($1)";
    /**
     * Start method for the verticle. Initializes the polling engine by fetching device data from the database
     * and setting the initial status for each device.
     *
     * @param startPromise A promise to indicate when the verticle is started.
     */
    @Override
    public void start(Promise<Void> startPromise)
    {
        // Execute query to fetch all devices from the provisioning_jobs table
        DATABASE_SERVICE.executeQuery(new JsonObject().put(Constants.QUERY ,FETCH_ALL_DEVICES_ID_QUERY))
                .onSuccess(result ->
                {
                    // If the query was successful, fetch the devices data
                    if(Boolean.TRUE.equals(result.getBoolean(Constants.SUCCESS)))
                    {
                        var deviceData = result.getJsonArray(Constants.RESPONSE);

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
        vertx.eventBus().localConsumer(Constants.AVAILABILITY_POLLING_ADDRESS,this::handleAvailabilityPolling);
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
            // todo change flow for db
            DATABASE_SERVICE.executeQuery(new JsonObject()
                    .put(Constants.QUERY, Utils.buildJoinQuery(FETCH_DEVICE_IP_QUERY, deviceIds.size()))
                    .put(Constants.PARAMS, deviceIds)).onSuccess(result ->
                    {
                        // If the query was successful
                        if(Boolean.TRUE.equals(result.getBoolean(Constants.SUCCESS)))
                        {
                            var devicesData = result.getJsonArray(Constants.RESPONSE);

                            // If devices are found, execute the fping utility for availability check
                            if (!devicesData.isEmpty())
                            {
                                // todo harsh ho many pools defined why explain why with numbers
                                vertx.executeBlocking(() -> Utils.ping(devicesData), false, asyncResult ->
                                {
                                    // Handle the result of the fping utility
                                    if (asyncResult.succeeded())
                                    {
                                        LOGGER.info("fping results: {}", asyncResult.result());

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
                                                    LOGGER.error("Error in setting device status: {}", exception.getMessage());
                                                }
                                            }
                                        }
                                        else
                                        {
                                            LOGGER.error("Ping process failed");
                                        }
                                    }
                                });
                            }
                        }
                    }).onFailure(error -> LOGGER.error("Error in fetching devices for availability polling: {}", error.getMessage()));
        }
        catch (Exception exception)
        {
            // Log any exceptions that occur during the availability polling process
            LOGGER.error("Error in availability polling: {}", exception.getMessage());
        }
    }
}
