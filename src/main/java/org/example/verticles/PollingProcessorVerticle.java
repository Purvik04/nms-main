package org.example.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.utils.Constants;
import org.example.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PollingProcessorVerticle extends AbstractVerticle
{
    private static final String QUERY_INSERT_PROVISIONED_DATA = """
        INSERT INTO provisioned_data (job_id, data, polled_at)
        VALUES ($1, $2, $3)
    """;

    private static final Logger logger = LoggerFactory.getLogger(PollingProcessorVerticle.class);

    @Override
    public void start(Promise<Void> startPromise) throws Exception
    {
        vertx.eventBus().localConsumer(Constants.EVENTBUS_POLLING_PROCESSOR_ADDRESS, this::setUpConsumer);

        startPromise.complete();
    }

    private void setUpConsumer(Message<JsonArray> message)
    {
        try
        {
            var deviceBatch = message.body();

            vertx.executeBlocking(()-> Utils.runGoPlugin(deviceBatch, Constants.METRICS), false, res ->
            {
                if (res.succeeded())
                {
                    var pluginOutput = res.result();

                    if (pluginOutput.isEmpty())
                    {
                        logger.error("Go plugin execution failed");

                        return;
                    }

                    var processedData = new JsonArray(pluginOutput);

                    logger.info("Plugin processed batch, sending {} entries to DB", processedData.size());

                    sendToDatabase(processedData);
                }
                else
                {
                    logger.error(res.cause().getMessage());
                }
            });
        }
        catch (Exception exception)
        {
            logger.error("Error in Polling Processing: {}", exception.getMessage());
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

            var request = new JsonObject()
                    .put(Constants.QUERY, QUERY_INSERT_PROVISIONED_DATA)
                    .put(Constants.PARAMS, batchParams);

            vertx.eventBus().<JsonObject>request(Constants.EVENTBUS_DATABASE_ADDRESS, request, reply ->
            {
                if (reply.succeeded())
                {
                    var response = reply.result().body();

                    if (Boolean.TRUE.equals(response.getBoolean(Constants.SUCCESS)))
                    {
                        logger.info("Batch update of provisioned data successful");
                    }
                    else
                    {
                        logger.error("Batch update of provision failed from db: {}", response.getString(Constants.ERROR));
                    }
                }
                else
                {
                    logger.error("Batch update of provision failed: {}", reply.cause().getMessage());
                }
            });
        }
        catch (Exception exception)
        {
            logger.error("Error in sending to database: {}", exception.getMessage());
        }
    }

    @Override
    public void stop() throws Exception
    {
        super.stop();
        // Cleanup resources if needed
    }
}
