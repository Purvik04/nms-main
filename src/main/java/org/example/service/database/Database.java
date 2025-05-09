package org.example.service.database;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.serviceproxy.ServiceBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verticle responsible for registering the {@link DatabaseService} on the Vert.x Event Bus.
 * It ensures that the {@link DatabaseClient} is instantiated before proceeding with service registration.
 */

//todo:- implement databse execute query logic here
public class Database extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Database.class);

    public static final String DB_SERVICE_ADDRESS = "database-service";

    /**
     * Starts the verticle by registering the {@link DatabaseService} on the Event Bus using Service Proxy.
     *
     * @param startPromise a promise that should be completed when the verticle is fully started
     */
    @Override
    public void start(Promise<Void> startPromise)
    {
        try
        {
            // Ensure DatabaseClient singleton is initialized before registering the service
            if (DatabaseClient.getInstance() != null)
            {
                // Register the service implementation at the defined address
                new ServiceBinder(vertx)
                        .setAddress(DB_SERVICE_ADDRESS)
                        .register(DatabaseService.class, DatabaseService.create())
                        .completionHandler(asyncResult ->
                        {
                            // Handle result of registration
                            if (asyncResult.failed())
                            {
                                LOGGER.error("Failed to register DatabaseService: {}", asyncResult.cause().getMessage());

                                startPromise.fail(asyncResult.cause());
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

    /**
     * Stops the verticle by closing the shared {@link DatabaseClient} instance.
     *
     * @param stopFuture a promise that should be completed when the verticle is fully stopped
     */
    @Override
    public void stop(Promise<Void> stopFuture)
    {
        // Close database client and release any resources
        DatabaseClient.close();

        LOGGER.info("Database verticle undeployed successfully");

        stopFuture.complete();
    }
}
