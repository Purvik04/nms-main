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

public class MetricPollingVerticle extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricPollingVerticle.class);

    private static final DatabaseService DATABASE_SERVICE = DatabaseService.createProxy(Database.DB_SERVICE_ADDRESS);

    @Override
    public void start(Promise<Void> startPromise)
    {
        vertx.eventBus().localConsumer(Constants.EVENTBUS_METRIC_POLLING_ADDRESS,this::handleMetricPolling);

        startPromise.complete();
    }

    private void handleMetricPolling(Message<JsonArray> message)
    {
        try
        {
            var deviceIds = message.body();

            LOGGER.info("Received device ids: {}", deviceIds);

            var filteredIds = filterUpDevices(deviceIds);

            if (filteredIds.isEmpty())
            {
                LOGGER.warn("No devices are UP for IDs: {}", deviceIds);

                return;
            }

            var placeholders = Utils.buildPlaceholders(filteredIds.size());

            var fetchJobs = "SELECT pj.id AS id, pj.ip, pj.port, cp.credentials, cp.system_type FROM provisioning_jobs pj " +
                    "JOIN credential_profiles cp ON pj.credential_profile_id = cp.id " +
                    "WHERE pj.id IN (" + placeholders + ") ORDER BY pj.id";

            var query = new JsonObject()
                    .put(Constants.QUERY, fetchJobs)
                    .put(Constants.PARAMS, filteredIds);

            DATABASE_SERVICE.executeQuery(query)
                    .onSuccess(result ->
                    {
                        if (Boolean.TRUE.equals(result.getBoolean(Constants.SUCCESS)))
                        {
                            var data = result.getJsonArray(Constants.DATA);

                            if (!data.isEmpty())
                            {
                                LOGGER.info("Sending batch of size {} to PollingProcessor", data.size());

                                LOGGER.info(data.toString());

                                vertx.eventBus().send(Constants.EVENTBUS_POLLING_PROCESSOR_ADDRESS, data);
                            }
                            else
                            {
                                LOGGER.warn("No data found for device ids: {}", filteredIds);
                            }
                        }
                    })
                    .onFailure(error -> LOGGER.error("Database request failed: {}", error.getMessage()));
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in metric polling: {}", exception.getMessage());
        }

    }

    private JsonArray filterUpDevices(JsonArray deviceIds)
    {
        var filteredIds = new JsonArray();

        LOGGER.info("ALL Devices: {}", AvailabilityCacheEngine.getAllDeviceStatus());

        try
        {
            for (int i = 0; i < deviceIds.size(); i++)
            {
                if (AvailabilityCacheEngine.getDeviceStatus(deviceIds.getInteger(i)).equalsIgnoreCase(Constants.UP))
                {
                    filteredIds.add(deviceIds.getInteger(i));
                }
            }
        }
        catch (Exception exception)
        {
            LOGGER.error("Error filtering UP devices: {}", exception.getMessage());

            return filteredIds.clear();
        }

        return filteredIds;
    }

    @Override
    public void stop() {
        // Cleanup resources if needed
    }
}
