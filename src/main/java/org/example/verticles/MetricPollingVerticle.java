package org.example.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.cache.AvailabilityCacheEngine;
import org.example.utils.Constants;
import org.example.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricPollingVerticle extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(MetricPollingVerticle.class);

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

            logger.info("Received device ids: {}", deviceIds);

            var filteredIds = filterUpDevices(deviceIds);

            if (filteredIds.isEmpty())
            {
                logger.warn("No devices are UP for IDs: {}", deviceIds);

                return;
            }

            var placeholders = Utils.buildPlaceholders(filteredIds.size());

            var fetchJobs = "SELECT pj.id AS id, pj.ip, pj.port, cp.credentials, cp.system_type FROM provisioning_jobs pj " +
                    "JOIN credential_profiles cp ON pj.credential_profile_id = cp.id " +
                    "WHERE pj.id IN (" + placeholders + ") ORDER BY pj.id";

            var query = new JsonObject()
                    .put(Constants.QUERY, fetchJobs)
                    .put(Constants.PARAMS, filteredIds);

            vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, query, asyncResult ->
            {
                if (asyncResult.succeeded())
                {
                    var result = asyncResult.result().body();

                    if (Boolean.TRUE.equals(result.getBoolean(Constants.SUCCESS)))
                    {
                        var data = result.getJsonArray(Constants.DATA);

                        if (!data.isEmpty())
                        {
                            logger.info("Sending batch of size {} to PollingProcessor", data.size());

                            logger.info(data.toString());

                            vertx.eventBus().send(Constants.EVENTBUS_POLLING_PROCESSOR_ADDRESS, data);
                        }
                        else
                        {
                            logger.warn("No data found for device ids: {}", filteredIds);
                        }
                    }
                    else
                    {
                        logger.error("Failed to fetch devices from database: {}", result.getString(Constants.ERROR));
                    }
                }
                else
                {
                    logger.error("Database request failed: {}", asyncResult.cause().getMessage());
                }
            });
        }
        catch (Exception exception)
        {
            logger.error("Error in metric polling: {}", exception.getMessage());
        }

    }

    private JsonArray filterUpDevices(JsonArray deviceIds)
    {
        var filteredIds = new JsonArray();

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
            logger.error("Error filtering UP devices: {}", exception.getMessage());

            return filteredIds.clear();
        }

        return filteredIds;
    }

    @Override
    public void stop() {
        // Cleanup resources if needed
    }
}
