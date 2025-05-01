package org.example.tests.database;

import io.vertx.core.Vertx;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import org.example.utils.Constants;

public class DatabaseClient
{
    private static Pool instance;

    private DatabaseClient() {} // Private constructor

    public static synchronized Pool getInstance(Vertx vertx)
    {
        if (instance == null)
        {
            var connectOptions = new PgConnectOptions()
                    .setHost(Constants.DB_HOST)
                    .setPort(Integer.parseInt(Constants.DB_PORT))
                    .setDatabase(Constants.DB_NAME)
                    .setUser(Constants.DB_USER)
                    .setPassword(Constants.DB_PASSWORD)
                    .setReconnectAttempts(3)
                    .setReconnectInterval(2000L);

            var poolOptions = new PoolOptions()
                    .setMaxSize(10);

            instance = PgBuilder.pool()
                    .with(poolOptions)
                    .connectingTo(connectOptions)
                    .using(vertx)
                    .build();
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
