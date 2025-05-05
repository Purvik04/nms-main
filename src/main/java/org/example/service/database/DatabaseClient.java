package org.example.service.database;

import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import org.example.Main;
import org.example.utils.Constants;
import org.example.utils.MotaDataConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseClient
{
    private static final String HOST = "host";

    private static final String PORT = "port";

    private static final String DATABASE = "database";

    private static final String USER = "user";

    private static final String PASSWORD = "password";

    private static final JsonObject DBCONFIG = MotaDataConfigUtil.getConfig().getJsonObject("db" , new JsonObject());

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseClient.class);

    private static Pool instance;

    private DatabaseClient() {} // Private constructor

    public static synchronized Pool getInstance()
    {
        if (instance == null)
        {
            try
            {
                var connectOptions = new PgConnectOptions()
                        .setHost(DBCONFIG.getString(HOST , Constants.DB_HOST))
                        .setPort(DBCONFIG.getInteger(PORT , Constants.DB_PORT))
                        .setDatabase(DBCONFIG.getString(DATABASE , Constants.DB_NAME))
                        .setUser(DBCONFIG.getString(USER , Constants.DB_USER))
                        .setPassword(DBCONFIG.getString(PASSWORD))
                        .setReconnectAttempts(3)
                        .setReconnectInterval(2000L);

                var poolOptions = new PoolOptions()
                        .setMaxSize(10);

                instance = PgBuilder.pool()
                        .with(poolOptions)
                        .connectingTo(connectOptions)
                        .using(Main.getVertx())
                        .build();

                instance.getConnection(conn ->
                {
                    if (conn.failed())
                    {
                        LOGGER.error("DB connection test failed: {}", conn.cause().getMessage());

                        instance = null; // Ensure instance remains null on failure
                    }
                });
            }
            catch (Exception exception)
            {
               LOGGER.error("Failed to create database client: {}", exception.getMessage());

               instance = null;
            }
        }
        return instance;
    }

    public static synchronized void close()
    {
        if (instance != null)
        {
            instance.close();

            instance = null;
        }
    }
}
