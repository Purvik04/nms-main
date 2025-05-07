package org.example.service.database;

import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import org.example.BootStrap;
import org.example.utils.Constants;
import org.example.utils.MotaDataConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeUnit;

/**
 * Singleton class responsible for creating and managing the shared PostgreSQL connection pool (`Pool`) using Vert.x.
 * Reads database configuration from the application config and lazily initializes the pool on demand.
 */
public class DatabaseClient
{
    // Configuration keys
    private static final String HOST = "database.host";
    private static final String PORT = "database.port";
    private static final String DATABASE = "database.name";
    private static final String USER = "database.user";
    private static final String PASSWORD = "database.password";

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseClient.class);

    // Shared Pool instance
    private static Pool instance;

    // Private constructor to prevent instantiation
    private DatabaseClient() {}

    /**
     * Lazily initializes and returns the shared instance of the PostgreSQL connection pool.
     *
     * @return shared {@link Pool} instance for database operations
     */
    public static synchronized Pool getInstance()
    {
        // Return existing instance if already created
        if (instance == null)
        {
            try
            {
                // Configure connection options using app-level config or default constants
                var connectOptions = new PgConnectOptions()
                        .setHost(MotaDataConfigUtil.getConfig().getString(HOST, Constants.DB_HOST))
                        .setPort(MotaDataConfigUtil.getConfig().getInteger(PORT, Constants.DB_PORT))
                        .setDatabase(MotaDataConfigUtil.getConfig().getString(DATABASE, Constants.DB_NAME))
                        .setUser(MotaDataConfigUtil.getConfig().getString(USER, Constants.DB_USER))
                        .setPassword(MotaDataConfigUtil.getConfig().getString(PASSWORD))
                        .setReconnectAttempts(2)        // Retry attempts on failure
                        .setReconnectInterval(2000L)
                        .setIdleTimeout(10)
                        .setIdleTimeoutUnit(TimeUnit.SECONDS);   // Retry interval in milliseconds

                // Configure pool options

                //todo harsh why 5
                var poolOptions = new PoolOptions()
                        .setMaxSize(5); // Max number of connections in pool

                // Build the pool using Vert.x and provided options
                instance = PgBuilder.pool()
                        .with(poolOptions)
                        .connectingTo(connectOptions)
                        .using(BootStrap.getVertx())
                        .build();


                for(var index = 0 ; index < 10; index++)
                {
                    instance.getConnection(handler ->{
                        if(handler.failed())
                        {
                            LOGGER.error(handler.cause().getMessage(), handler.cause());
                        }
                    });
                }

//                instance.query(Constants.DB_CONNECTION_CHECK_QUERY).execute(asyncResult ->
//                {
//                    if(asyncResult.succeeded())
//                    {
//                        LOGGER.info("Connected to database");
//                    }
//                    else
//                    {
//                        instance = null;
//                    }
//                });
            }
            catch (Exception exception)
            {
                // Log and nullify instance on failure
                LOGGER.error("Failed to create database client: {}", exception.getMessage());

                instance = null;
            }
        }
        return instance;
    }

    /**
     * Closes and resets the shared {@link Pool} instance, releasing all resources.
     */
    public static synchronized void close()
    {
        // Close only if pool is initialized
        if (instance != null)
        {
            try
            {
                instance.close();
            }
            catch (Exception exception)
            {
                LOGGER.error("Failed to close database client: {}", exception.getMessage());
            }
            finally
            {
                instance = null;
            }
        }
    }
}
