package org.example.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.service.database.Database;
import org.example.service.database.DatabaseService;
import org.example.utils.Constants;
import org.example.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PollingProcessorEngine extends AbstractVerticle
{
    private static final String QUERY_INSERT_PROVISIONED_DATA = """
        INSERT INTO provisioned_data (job_id, data, polled_at)
        VALUES ($1, $2, $3)
    """;

    private static final DatabaseService DATABASE_SERVICE = DatabaseService.createProxy(Database.DB_SERVICE_ADDRESS);

    private static final Logger LOGGER = LoggerFactory.getLogger(PollingProcessorEngine.class);

    @Override
    public void start(Promise<Void> startPromise) throws Exception
    {
        vertx.eventBus().localConsumer(Constants.EVENTBUS_POLLING_PROCESSOR_ADDRESS, this::handlePolling);

        startPromise.complete();
    }

    private void handlePolling(Message<JsonArray> message)
    {
        try
        {
            vertx.executeBlocking(()-> Utils.runGoPluginSecure(message.body(), Constants.METRICS_MODE), false, asyncResult ->
            {
                if (asyncResult.succeeded())
                {
                    var pluginOutput = asyncResult.result();

                    if (pluginOutput.isEmpty())
                    {
                        LOGGER.error("Go plugin execution failed");

                        return;
                    }

                    LOGGER.info("Plugin processed batch, sending {} entries to DB", pluginOutput.size());

                    sendToDatabase(pluginOutput);
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

    private void sendToDatabase(JsonArray polledResults)
    {
        try
        {
            var batchParams = new JsonArray();

            for (int i = 0; i < polledResults.size(); i++){

                var deviceResult = polledResults.getJsonObject(i);

                batchParams.add(new JsonArray()
                        .add(deviceResult.getInteger(Constants.ID))
                        .add(deviceResult.getJsonObject(Constants.DATA))
                        .add(deviceResult.getString(Constants.POLLED_AT)));
            }

            var query = new JsonObject()
                    .put(Constants.QUERY, QUERY_INSERT_PROVISIONED_DATA)
                    .put(Constants.PARAMS, batchParams);

            DATABASE_SERVICE.executeQuery(query)
                            .onSuccess(result ->
                                    LOGGER.info("Successfully processed {} entries to DB", polledResults.size()))
                            .onFailure(error ->
                                    LOGGER.error("Database service failed: {}", error.getMessage()));
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in sending to database: {}", exception.getMessage());
        }
    }
}
