package org.example.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.cache.AvailabilityCacheEngine;
import org.example.utils.Constants;
import org.example.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AvailabilityPollingVerticle extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(AvailabilityPollingVerticle.class);

    @Override
    public void start(Promise<Void> startPromise)
    {
        var query = new JsonObject().put(Constants.QUERY, "SELECT id FROM provisioning_jobs;");

        vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, query, reply -> handleDBResponse(reply, startPromise));

        vertx.eventBus().localConsumer(Constants.EVENTBUS_AVAILABILITY_POLLING_ADDRESS, this::handleAvailabilityPolling);
    }

    private void handleAvailabilityPolling(Message<JsonArray> message)
    {
        var deviceIds = message.body();

        var placeholders = Utils.buildPlaceholders(deviceIds.size());

        var fetchQuery = "SELECT ip, id from provisioning_jobs "+
                "WHERE id IN (" + placeholders + ")";

        var query = new JsonObject()
                .put(Constants.QUERY, fetchQuery)
                .put(Constants.PARAMS, deviceIds);

        vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, query, reply ->
        {
            if (reply.succeeded())
            {
                var response = reply.result().body();

                if(response.getBoolean(Constants.SUCCESS))
                {
                    var data = response.getJsonArray(Constants.DATA);

                    if (!data.isEmpty())
                    {
                        vertx.executeBlocking(() -> Utils.runFping(data), false, ar ->
                        {
                            if (ar.succeeded())
                            {
                                var fpingResults = ar.result();

                                logger.info("fping results: {}", fpingResults);

                                if(!fpingResults.isEmpty())
                                {
                                    fpingResults.forEach(result ->
                                    {
                                        var deviceResult = (JsonObject) result;

                                        AvailabilityCacheEngine.setDeviceStatus(deviceResult.getInteger(Constants.ID), deviceResult.getString(Constants.STATUS));
                                    });
                                }
                            }
                        });
                    }
                }
            }
        });
    }

    private void handleDBResponse(AsyncResult<Message<JsonObject>> reply, Promise<Void> startPromise)
    {
        if (reply.succeeded())
        {
            var response = reply.result().body();

            if(response.getBoolean(Constants.SUCCESS))
            {
                var data = response.getJsonArray(Constants.DATA);

                if (!data.isEmpty())
                {
                    data.forEach(item ->
                    {
                        var device = (JsonObject) item;

                        AvailabilityCacheEngine.setDeviceStatus(device.getInteger(Constants.ID),Constants.DOWN);
                    });
                }

                startPromise.complete();
            }
            else
            {
                startPromise.fail("Failed to fetch devices from database");
            }
        }
        else
        {
            startPromise.fail("Database request failed: " + reply.cause().getMessage());
        }
    }
}


