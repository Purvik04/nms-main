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


public class AvailabilityPollingEngine extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AvailabilityPollingEngine.class);

    private static final DatabaseService DATABASE_SERVICE = DatabaseService.createProxy(Database.DB_SERVICE_ADDRESS);

    private static final String QUERY_SELECT_ALL_DEVICES = "SELECT id FROM provisioning_jobs;";

    @Override
    public void start(Promise<Void> startPromise)
    {
        DATABASE_SERVICE.executeQuery(new JsonObject().put(Constants.QUERY ,QUERY_SELECT_ALL_DEVICES))
                        .onSuccess(result ->
                        {
                            if(Boolean.TRUE.equals(result.getBoolean(Constants.SUCCESS)))
                            {
                                var data = result.getJsonArray(Constants.DATA);

                                if (!data.isEmpty())
                                {
                                    for(var i = 0 ; i < data.size(); i++)
                                    {
                                        try
                                        {
                                            AvailabilityCacheEngine.setDeviceStatus(data
                                                    .getJsonObject(i).getInteger(Constants.ID),Constants.DOWN);
                                        }
                                        catch (Exception exception)
                                        {
                                            LOGGER.error("Error in setting device status: {}", exception.getMessage());

                                            startPromise.fail(exception.getMessage());
                                        }
                                    }
                                }
                                startPromise.complete();
                            }
                        })
                        .onFailure( error ->
                        {
                            LOGGER.error("Error in fetching devices: {}", error.getMessage());

                            startPromise.fail("Error in fetching devices: " + error.getMessage());
                        });

        vertx.eventBus().localConsumer(Constants.EVENTBUS_AVAILABILITY_POLLING_ADDRESS ,this::handleAvailabilityPolling);
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

            DATABASE_SERVICE.executeQuery(query).onSuccess(result ->
                            {
                                if(Boolean.TRUE.equals(result.getBoolean(Constants.SUCCESS)))
                                {
                                    var data = result.getJsonArray(Constants.DATA);

                                    if (!data.isEmpty())
                                    {
                                        vertx.executeBlocking(() -> Utils.runFping(data), false, asyncResult ->
                                        {
                                            if (asyncResult.succeeded())
                                            {
                                                LOGGER.info("fping results: {}", asyncResult.result());

                                                if (!asyncResult.result().isEmpty())
                                                {
                                                    for (var i = 0; i < asyncResult.result().size(); i++)
                                                    {
                                                        try
                                                        {
                                                            AvailabilityCacheEngine.setDeviceStatus(asyncResult.result()
                                                                            .getJsonObject(i).getInteger(Constants.ID),
                                                                            asyncResult.result().getJsonObject(i)
                                                                                    .getString(Constants.STATUS));
                                                        }
                                                        catch (Exception exception)
                                                        {
                                                            LOGGER.error("Error in setting device status: {}", exception.getMessage());
                                                        }
                                                    }
                                                }
                                            }
                                        });
                                    }
                                }
                            })
                            .onFailure(error -> LOGGER.error("Error in fetching devices for availability polling: {}", error.getMessage()));
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in availability polling: {}", exception.getMessage());
        }
    }
}


