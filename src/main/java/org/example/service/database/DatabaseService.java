package org.example.service.database;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.example.BootStrap;

/**
 * A Vert.x Service Proxy interface for interacting with the database asynchronously.
 * Enables execution of single or batch queries via event bus-based RPC.
 */
@ProxyGen
public interface DatabaseService
{
    /**
     * Factory method to create a local instance of the {@link DatabaseService}.
     *
     * @return a new instance of {@link DatabaseServiceImpl}
     */
    static DatabaseService create()
    {
        return new DatabaseServiceImpl();
    }

    /**
     * Factory method to create a service proxy client that communicates over the Vert.x event bus.
     *
     * @param address the event bus address where the service is registered
     * @return a proxy instance of {@link DatabaseService}
     */
    static DatabaseService createProxy(String address)
    {
        return new DatabaseServiceVertxEBProxy(BootStrap.getVertx(), address);
    }

    /**
     * Executes a query or batch of queries against the database.
     *
     * @param query a {@link JsonObject} representing the query type and parameters
     * @return a {@link Future} containing the result as a {@link JsonObject}, or an error if execution fails
     */
    Future<JsonObject> executeQuery(JsonObject query);
}
