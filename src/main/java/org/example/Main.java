package org.example;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import org.example.tests.database.DatabaseVerticle;
import org.example.utils.Constants;
import org.example.utils.MotaDataConfigUtil;
import org.example.verticles.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main
{
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    static
    {
        MotaDataConfigUtil.loadConfig(Constants.CONFIG_FILE_PATH);
    }

    private static final JsonObject config = MotaDataConfigUtil.getConfig();

    private static final Vertx VERTX = Vertx.vertx(new VertxOptions()
                .setEventLoopPoolSize(config.getInteger(Constants.EVENT_LOOP_POOL_SIZE, Runtime.getRuntime().availableProcessors()))
        .setWorkerPoolSize(config.getInteger(Constants.WORKER_POOL_SIZE, 20)));

    public static Vertx getVertx()
    {
        return VERTX;
    }

    public static void main(String[] args)
    {
        var config = MotaDataConfigUtil.getConfig();

        VERTX.deployVerticle(new NmsServerVerticle())
                .compose(res ->
                {
                    logger.info("NMS Server Verticle started successfully");

//                    var dbOptions = new DeploymentOptions().setInstances(config.getInteger(DBVerticle.class.getSimpleName(), 1));

                    return VERTX.deployVerticle(DatabaseVerticle.class.getName());
                })
                .compose(res->
                {
                    logger.info("DB Verticle started successfully");

                    return VERTX.deployVerticle(QueryBuilderVerticle.class.getName());
                })
//                .compose(res ->
//                {
//                    logger.info("QueryBuilderVerticle started successfully");
//
//                    var availabilityOptions = new DeploymentOptions()
//                            .setInstances(config.getInteger(AvailabilityPollingVerticle.class.getSimpleName(), 1));
//
//                    return VERTX.deployVerticle(AvailabilityPollingVerticle.class.getName(),availabilityOptions);
//                })
//                .compose(res ->
//                {
//                    logger.info("AvailabilityPollingVerticle started successfully");
//
//                    var pollingOptions = new DeploymentOptions()
//                            .setInstances(config.getInteger(PollingProcessorVerticle.class.getSimpleName(), 1));
//
//                    return VERTX.deployVerticle(PollingProcessorVerticle.class.getName(),pollingOptions);
//                })
//                .compose(res->
//                {
//                    logger.info("PollingProcessorVerticle started successfully");
//
//                    return VERTX.deployVerticle(new MetricPollingVerticle());
//                })
//                .compose(res->
//                {
//                    logger.info("MetricPollingVerticle started successfully");
//
//                    return VERTX.deployVerticle(new PollingSchedulerVerticle());
//                })
                .onSuccess(res->
                {
                    logger.info("PollingSchedulerVerticle started successfully");

                    logger.info("All Verticles started successfully");
                })
                .onFailure(err ->
                {
                    logger.error(err.getMessage());

                    VERTX.close();
                });
    }
}