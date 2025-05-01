package org.example.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
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

    private static final DeliveryOptions deliveryOptions = new DeliveryOptions().setSendTimeout(5000);

    @Override
    public void start(Promise<Void> startPromise)
    {
        var query = new JsonObject().put(Constants.QUERY, "SELECT id FROM provisioning_jobs;");

        vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, query, deliveryOptions,reply -> handleDBResponse(reply, startPromise));

        vertx.eventBus().localConsumer(Constants.EVENTBUS_AVAILABILITY_POLLING_ADDRESS, this::handleAvailabilityPolling);
    }

    private void handleAvailabilityPolling(Message<JsonArray> message)
    {
        try
        {
            var deviceIds = message.body();

            var placeholders = Utils.buildPlaceholders(deviceIds.size());

            var fetchQuery = "SELECT ip, id from provisioning_jobs "+
                    "WHERE id IN (" + placeholders + ")";

            var query = new JsonObject()
                    .put(Constants.QUERY, fetchQuery)
                    .put(Constants.PARAMS, deviceIds);

            vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, query,deliveryOptions, reply ->
            {
                if (reply.succeeded())
                {
                    var response = reply.result().body();

                    if(Boolean.TRUE.equals(response.getBoolean(Constants.SUCCESS)))
                    {
                        var data = response.getJsonArray(Constants.DATA);

                        if (!data.isEmpty())
                        {
                            vertx.executeBlocking(() -> Utils.runFping(data), false, asyncResult ->
                            {
                                if (asyncResult.succeeded())
                                {
                                    var fpingResults = asyncResult.result();

                                    logger.info("fping results: {}", fpingResults);

                                    if(!fpingResults.isEmpty())
                                    {
                                        for (var i = 0; i<fpingResults.size(); i++)
                                        {
                                            AvailabilityCacheEngine.setDeviceStatus(fpingResults.getJsonObject(i).getInteger(Constants.ID), fpingResults.getJsonObject(i).getString(Constants.STATUS));
                                        }
                                    }
                                }
                            });
                        }
                    }
                }
            });
        }
        catch (Exception exception)
        {
            logger.error("Error in availability polling: {}", exception.getMessage());
        }
    }

    private void handleDBResponse(AsyncResult<Message<JsonObject>> reply, Promise<Void> startPromise)
    {
        try
        {
            if (reply.succeeded())
            {
                var response = reply.result().body();

                if(Boolean.TRUE.equals(response.getBoolean(Constants.SUCCESS)))
                {
                    var data = response.getJsonArray(Constants.DATA);

                    if (!data.isEmpty())
                    {
                        for(var i = 0 ; i < data.size(); i++)
                        {
                            AvailabilityCacheEngine.setDeviceStatus(data.getJsonObject(i).getInteger(Constants.ID),Constants.DOWN);
                        }
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
        catch (Exception exception)
        {
            logger.error("Error in handleDBResponse: {}", exception.getMessage());

            startPromise.fail("Error in handleDBResponse: " + exception.getMessage());
        }
    }
}


