package org.example.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.service.database.Database;
import org.example.service.database.DatabaseService;
import org.example.utils.Constants;
import org.example.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PollingProcessorEngine is responsible for handling batches of device polling data,
 * executing the Go plugin for metric collection, and storing results in the database.
 */
public class PollingProcessorEngine extends AbstractVerticle
{
    // SQL query to insert polled metrics into provisioned_data table
    private static final String QUERY_INSERT_POLLED_RESULTS = """
        INSERT INTO polled_results (provision_id, metrics, polled_at)
        VALUES ($1, $2, $3)
    """;

    // Proxy to interact with the shared DatabaseService
    private static final DatabaseService DATABASE_SERVICE = DatabaseService.createProxy(Database.DB_SERVICE_ADDRESS);

    private static final Logger LOGGER = LoggerFactory.getLogger(PollingProcessorEngine.class);

    // Keep reference to the event bus consumer to unregister it on stop
    private MessageConsumer<JsonArray> localConsumer;
    /**
     * Starts the verticle by registering a local event bus consumer to listen for polling tasks.
     */
    @Override
    public void start(Promise<Void> startPromise) throws Exception
    {
        try
        {
            localConsumer = vertx.eventBus().localConsumer(Constants.POLLING_PROCESSOR_ADDRESS, this::handlePolling);

            startPromise.complete();
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in deploying polling processor engine:{}", exception.getMessage());

            startPromise.fail(exception);
        }
    }

    /**
     * Handles incoming polling batch messages, runs the Go plugin securely in a worker thread,
     * and sends results to the database if successful.
     */
    private void handlePolling(Message<JsonArray> message)
    {
        try
        {
            vertx.executeBlocking(() ->
                    Utils.spawnGoPlugin(message.body(), Constants.METRICS)
                    , false, asyncResult ->
            {
                if (asyncResult.succeeded())
                {
                    var pluginOutput = asyncResult.result();

                    if (pluginOutput.isEmpty())
                    {
                        LOGGER.error("Go plugin execution failed");

                        return;
                    }

                    try
                    {
                        var batchParams = new JsonArray();

                        for (var index = 0; index < pluginOutput.size(); index++)
                        {
                            var deviceResult = pluginOutput.getJsonObject(index);

                            batchParams.add(new JsonArray()
                                    .add(deviceResult.getInteger(Constants.ID))
                                    .add(deviceResult.getJsonObject(Constants.METRICS))
                                    .add(deviceResult.getString(Constants.POLLED_AT)));
                        }

                        DATABASE_SERVICE.executeQuery(new JsonObject()
                                        .put(Constants.QUERY, QUERY_INSERT_POLLED_RESULTS)
                                        .put(Constants.PARAMS, batchParams))
                                        .onFailure(error -> LOGGER.error("Database service failed: {}",
                                                error.getMessage()));
                    }
                    catch (Exception exception)
                    {
                        LOGGER.error("Error in sending to database: {}", exception.getMessage());
                    }
                }
                else
                {
                    LOGGER.error(asyncResult.cause().getMessage());
                }
            });
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in Polling Processing: {}", exception.getMessage());
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
                    .onSuccess(v -> LOGGER.info("PollingProcessorEngine event bus consumer unregistered."))
                    .onFailure(err -> LOGGER.error("Failed to unregister event bus consumer: {}", err.getMessage()));
        }

        stopPromise.complete();
    }

}
