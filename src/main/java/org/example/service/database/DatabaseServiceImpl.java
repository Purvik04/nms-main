package org.example.service.database;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;
import org.example.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

/**
 * Implementation of the {@link DatabaseService} interface.
 * Handles execution of single and batch SQL queries using a shared PostgreSQL client.
 */
public class DatabaseServiceImpl implements DatabaseService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseServiceImpl.class);

    // Singleton database client
    private static final Pool CLIENT = DatabaseClient.getInstance();

    // Used to append RETURNING id to insert/update queries
    private static final String RETURNING_ID = " RETURNING id";

    /**
     * Executes either a single or batch SQL query based on the query payload.
     *
     * @param query a JsonObject containing the SQL string and parameters
     * @return a Future of JsonObject with query results or error
     */
    @Override
    public Future<JsonObject> executeQuery(JsonObject query)
    {
        var sql = query.getString(Constants.QUERY);

        var params = query.getJsonArray(Constants.PARAMS, new JsonArray());

        try
        {
            // Check if the params are a batch (JsonArray of JsonArrays)
            if (!params.isEmpty() && params.getValue(0) instanceof JsonArray)
            {
                return executeBatchQuery(sql, params);
            }
            else
            {
                return executeSingleQuery(sql, params);
            }
        }
        catch (Exception exception)
        {
            LOGGER.error(exception.getMessage());

            return Future.failedFuture(exception);
        }
    }

    /**
     * Executes a single SQL query with parameters.
     *
     * @param query the SQL string to execute
     * @param params a JsonArray of parameters to bind to the query
     * @return a Future containing a JsonObject with results or failure
     */
    private Future<JsonObject> executeSingleQuery(String query, JsonArray params)
    {
        var promise = Promise.<JsonObject>promise();

        var tuple = Tuple.tuple();

        // Append RETURNING id to insert/update if not present
        if ((query.trim().startsWith(Constants.DB_INSERT) ||
                query.trim().startsWith(Constants.DB_UPDATE)) &&
                !query.trim().toLowerCase().contains("returning"))
        {
            query += RETURNING_ID;
        }

        try
        {
            // Add parameters to the tuple
            for (var index = 0; index< params.size(); index++)
            {
                tuple.addValue(params.getValue(index));
            }

            // Execute prepared query
            CLIENT.preparedQuery(query).execute(tuple, asyncResult ->
            {
                if (asyncResult.succeeded())
                {
                    try
                    {
                        var response = new JsonArray();

                        if (asyncResult.result().size() > 0)
                        {
                            asyncResult.result().forEach(row ->
                            {
                                var responseObject = new JsonObject();

                                // Convert row to JsonObject
                                for (var index = 0; index < row.size(); index++)
                                {
                                    try
                                    {
                                        responseObject.put(row.getColumnName(index),row.getValue(index));
                                    }
                                    catch (Exception exception)
                                    {
                                        LOGGER.error(exception.getMessage());
                                    }
                                }

                                response.add(responseObject);
                            });
                        }

                        // Complete promise with results
                        promise.complete(new JsonObject()
                                .put(Constants.SUCCESS, Constants.TRUE)
                                .put(Constants.DATA, response));
                    }
                    catch (Exception exception)
                    {
                        LOGGER.error("Failed to parse query result: {}", exception.getMessage());

                        promise.fail(exception);
                    }
                }
                else
                {
                    LOGGER.error("Query execution failed: {}", asyncResult.cause().getMessage());

                    promise.fail(asyncResult.cause());
                }
            });
        }
        catch (Exception exception)
        {
            LOGGER.error("Unexpected error during query execution: {}", exception.getMessage());

            promise.fail(exception);
        }

        return promise.future();
    }

    /**
     * Executes a batch SQL query with multiple parameter sets.
     *
     * @param query the SQL string to execute
     * @param params a JsonArray of JsonArrays, each representing one parameter set
     * @return a Future containing a JsonObject with batch result or failure
     */
    private Future<JsonObject> executeBatchQuery(String query, JsonArray params)
    {
        var promise = Promise.<JsonObject>promise();

        try
        {
            var batchParams = new ArrayList<Tuple>();

            // Convert each JsonArray to a Tuple
            for (var index = 0; index < params.size(); index++)
            {
                var inner = params.getJsonArray(index);

                var tuple = Tuple.tuple();

                for (var innerIndex = 0; innerIndex < inner.size(); innerIndex++)
                {
                    tuple.addValue(inner.getValue(innerIndex));
                }

                batchParams.add(tuple);
            }

            CLIENT.preparedQuery(query).executeBatch(batchParams, asyncResult ->
            {
                if (asyncResult.succeeded())
                {
                    promise.complete(new JsonObject()
                            .put(Constants.SUCCESS, Constants.TRUE));
                }
                else
                {
                    LOGGER.error("Batch query execution failed: {}", asyncResult.cause().getMessage());

                    promise.fail(asyncResult.cause());
                }
            });
        }
        catch (Exception exception)
        {
            LOGGER.error("Unexpected error during batch query execution: {}", exception.getMessage());

            promise.fail(exception);
        }

        return promise.future();
    }
}
