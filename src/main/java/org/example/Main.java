package org.example;

import io.vertx.core.Vertx;
import org.example.verticles.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main
{
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args)
    {
        Vertx vertx = Vertx.vertx();

        vertx.deployVerticle(new NmsServerVerticle())
                .compose(res ->
                {

                    logger.info("NMS Server Verticle started successfully");

                    return vertx.deployVerticle(new DBVerticle());
                })
                .compose(res->
                {

                    logger.info("DB Verticle started successfully");

                    return vertx.deployVerticle(new QueryBuilderVerticle());
                })
                .compose(res ->
                {
                    logger.info("QueryBuilderVerticle started successfully");

                    return vertx.deployVerticle(new AvailabilityPollingVerticle());
                })
                .compose(res ->
                {

                    logger.info("AvailabilityPollingVerticle started successfully");

                    return vertx.deployVerticle(new PollingProcessorVerticle());
                })
                .compose(res->
                {
                    logger.info("PollingProcessorVerticle started successfully");

                    return vertx.deployVerticle(new MetricPollingVerticle());
                })
                .compose(res->
                {
                    logger.info("MetricPollingVerticle started successfully");

                    return vertx.deployVerticle(new PollingSchedulerVerticle());
                })
                .onSuccess(res->
                {
                    logger.info("PollingSchedulerVerticle started successfully");

                    logger.info("All Verticles started successfully");
                })
                .onFailure(err -> {

                    logger.error(err.getMessage());

                    vertx.close();
                });
    }
}