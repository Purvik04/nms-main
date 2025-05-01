package org.example.tests.database;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.serviceproxy.ServiceBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseVerticle extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(DatabaseVerticle.class);
    public static final String DB_SERVICE_ADDRESS = "database-service";

    @Override
    public void start(Promise<Void> startPromise)
    {
        try
        {
            new ServiceBinder(vertx)
                    .setAddress(DB_SERVICE_ADDRESS)
                    .register(DatabaseService.class, DatabaseService.create())
                    .completionHandler(ar ->
                    {
                        if (ar.failed())
                        {
                            logger.error("Failed to register DatabaseService: {}", ar.cause().getMessage());

                            startPromise.fail(ar.cause());
                        }
                        else
                        {
                            logger.info("Services registered at {}", DB_SERVICE_ADDRESS);

                            startPromise.complete();
                        }
                    });
        }
        catch (Exception exception)
        {
            logger.error("Failed to start DatabaseVerticle: {}", exception.getMessage());

            startPromise.fail(exception.getMessage());
        }
    }

    @Override
    public void stop(Promise<Void> stopFuture)
    {
        DatabaseClient.close();

        stopFuture.complete();
    }
}