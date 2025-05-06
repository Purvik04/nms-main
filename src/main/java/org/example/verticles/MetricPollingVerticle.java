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
 * MetricPollingVerticle handles incoming requests (via EventBus) for metric polling.
 * It filters devices that are marked "UP", fetches their provisioned job & credential data,
 * and passes the batch to the PollingProcessor.
 */
public class MetricPollingVerticle extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricPollingVerticle.class);

    // Service proxy for asynchronous database interactions
    private static final DatabaseService DATABASE_SERVICE = DatabaseService.createProxy(Database.DB_SERVICE_ADDRESS);

    private static final String FETCH_DEVICES_DATA_QUERY = "SELECT provision.id AS id, provision.ip, provision.port, cp.credentials, cp.system_type " +
            "FROM provision JOIN credential_profiles cp ON p.credential_profile_id = cp.id WHERE p.id IN ($1) ORDER BY p.id";

    /**
     * Registers a local EventBus consumer for metric polling events.
     */
    @Override
    public void start(Promise<Void> startPromise)
    {
        vertx.eventBus().localConsumer(Constants.METRIC_POLLING_ADDRESS, this::handleMetricPolling);

        startPromise.complete();
    }

    /**
     * Core logic to handle metric polling requests.
     * Filters device IDs based on UP status, queries DB for details, and forwards valid data to polling processor.
     */
    private void handleMetricPolling(Message<JsonArray> message)
    {
        try
        {
            var deviceIds = message.body();

            LOGGER.info("Received device ids: {}", deviceIds);

            // Step 1: Filter out devices not marked UP in cache
            var filteredDeviceIds = filterDevicesForPolling(deviceIds);

            if (filteredDeviceIds.isEmpty())
            {
                LOGGER.warn("No devices are UP for IDs: {}", deviceIds);

                return;
            }

            // Step 2: Query the database asynchronously
            // todo do not add here create db
            DATABASE_SERVICE.executeQuery(new JsonObject()
                            .put(Constants.QUERY, Utils.buildJoinQuery(FETCH_DEVICES_DATA_QUERY,filteredDeviceIds.size()))
                            .put(Constants.PARAMS, filteredDeviceIds))
                    .onSuccess(result ->
                    {
                        if (Boolean.TRUE.equals(result.getBoolean(Constants.SUCCESS)))
                        {
                            var devicesData = result.getJsonArray(Constants.RESPONSE);

                            if (!devicesData.isEmpty())
                            {
                                LOGGER.info("Sending batch of size {} to PollingProcessor", devicesData.size());

                                LOGGER.info(devicesData.toString());

                                // Step 4: Send data to PollingProcessor via EventBus
                                vertx.eventBus().send(Constants.POLLING_PROCESSOR_ADDRESS, devicesData);
                            }
                            else
                            {
                                LOGGER.warn("No data found for device ids: {}", filteredDeviceIds);
                            }
                        }
                    }).onFailure(error -> LOGGER.error("Database request failed: {}", error.getMessage()));
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in metric polling: {}", exception.getMessage());
        }
    }

    /**
     * Filters out devices not marked as "UP" in the availability cache.
     */
    private JsonArray filterDevicesForPolling(JsonArray deviceIds)
    {
        var filteredIds = new JsonArray();

        for (var index = 0; index < deviceIds.size(); index++)
        {
            try
            {
                var deviceId = deviceIds.getInteger(index);

                if (AvailabilityCacheEngine.getDeviceStatus(deviceId).equals(Constants.UP))
                {
                    filteredIds.add(deviceId);
                }
            }
            catch (Exception exception)
            {
                LOGGER.error("Error in filtering devices: {}", exception.getMessage());
            }

        }
        return filteredIds;
    }
}
