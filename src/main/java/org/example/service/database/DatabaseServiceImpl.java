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

public class DatabaseServiceImpl implements DatabaseService
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseServiceImpl.class);

    private static final Pool CLIENT = DatabaseClient.getInstance();

    private static final String RETURNING_ID = " RETURNING id";

    @Override
    public Future<JsonObject> executeQuery(JsonObject query)
    {
        var sql = query.getString(Constants.QUERY);

        var params = query.getJsonArray(Constants.PARAMS , new JsonArray());

        try
        {
            if (!params.isEmpty() && params.getValue(0) instanceof JsonArray)
            {
                return executeBatch(sql, params);
            }
            else
            {
                return executeSingle(sql, params);
            }
        }
        catch (Exception exception)
        {
            LOGGER.error(exception.getMessage());

            return Future.failedFuture(exception);
        }
    }

    private Future<JsonObject> executeSingle(String query, JsonArray params)
    {
        var promise = Promise.<JsonObject>promise();

        var tuple = Tuple.tuple();

        if ((query.trim().toLowerCase().startsWith(Constants.DB_INSERT) || query.trim().toLowerCase().startsWith(Constants.DB_UPDATE)) && !query.toLowerCase().contains("returning"))
        {
            query += RETURNING_ID;
        }

        try
        {
            for (int i = 0; i < params.size(); i++)
            {
                tuple.addValue(params.getValue(i));
            }

            assert CLIENT != null;
            CLIENT.preparedQuery(query).execute(tuple, asyncResult ->
            {
                if (asyncResult.succeeded())
                {
                    LOGGER.info("Query executed successfully");

                    try
                    {
                        var jsonRows = new JsonArray();

                        if(asyncResult.result().size() > 0)
                        {
                            asyncResult.result().forEach(row ->
                            {
                                var obj = new JsonObject();

                                for (int i = 0; i < row.size(); i++)
                                {
                                   try
                                   {
                                       obj.put(row.getColumnName(i), row.getValue(i));
                                   }
                                   catch (Exception exception)
                                   {
                                       LOGGER.error(exception.getMessage());
                                   }
                                }

                                jsonRows.add(obj);
                            });
                        }

                        promise.complete(new JsonObject()
                                .put(Constants.SUCCESS, Constants.TRUE)
                                .put(Constants.DATA, jsonRows));
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

    private Future<JsonObject> executeBatch(String query, JsonArray params)
    {
        var promise = Promise.<JsonObject>promise();

        try
        {
            var batchParams = new ArrayList<Tuple>();

            try
            {
                for (int i = 0; i < params.size(); i++)
                {
                    var inner = params.getJsonArray(i);

                    var tuple = Tuple.tuple();

                    for (int j = 0; j < inner.size(); j++)
                    {
                        tuple.addValue(inner.getValue(j));
                    }
                    batchParams.add(tuple);
                }
            }
            catch (Exception exception)
            {
                promise.fail(exception);

                return promise.future();
            }

            LOGGER.info("Executing Batch Query: {} with {} parameter sets", query, batchParams.size());

            assert CLIENT != null;
            CLIENT.preparedQuery(query).executeBatch(batchParams, asyncResult ->
            {
                if (asyncResult.succeeded())
                {
                    LOGGER.info("Batch query executed successfully");

                    promise.complete(new JsonObject()
                            .put(Constants.SUCCESS, Constants.TRUE)
                            .put(Constants.DATA, "Batch update successful"));
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
