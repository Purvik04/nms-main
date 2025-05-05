package org.example.service.database;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.serviceproxy.ServiceBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Database extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Database.class);

    public static final String DB_SERVICE_ADDRESS = "database-service";

    @Override
    public void start(Promise<Void> startPromise)
    {
        try
        {
            if(DatabaseClient.getInstance() !=  null)
            {
                new ServiceBinder(vertx)
                        .setAddress(DB_SERVICE_ADDRESS)
                        .register(DatabaseService.class, DatabaseService.create())
                        .completionHandler(ar ->
                        {
                            if (ar.failed())
                            {
                                LOGGER.error("Failed to register DatabaseService: {}", ar.cause().getMessage());

                                startPromise.fail(ar.cause());
                            }
                            else
                            {
                                LOGGER.info("Services registered at {}", DB_SERVICE_ADDRESS);

                                startPromise.complete();
                            }
                        });
            }
            else
            {
                startPromise.fail("Database client not can't be instantiated");
            }

        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to start DatabaseVerticle: {}", exception.getMessage());

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