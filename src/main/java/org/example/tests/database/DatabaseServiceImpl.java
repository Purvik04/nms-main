package org.example.tests.database;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;
import org.example.Main;
import org.example.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class DatabaseServiceImpl implements DatabaseService
{
    private static final Logger logger = LoggerFactory.getLogger(DatabaseServiceImpl.class);

    private static final Pool client = DatabaseClient.getInstance(Main.getVertx());

    @Override
    public Future<JsonObject> executeQuery(JsonObject query)
    {
        var sql = query.getString(Constants.QUERY);

        var params = query.getJsonArray(Constants.PARAMS , new JsonArray());

        if (!params.isEmpty() && params.getValue(0) instanceof JsonArray)
        {
            return executeBatch(sql, params);
        }
        else
        {
            return executeSingle(sql, params);
        }
    }

    private Future<JsonObject> executeSingle(String query, JsonArray params)
    {
        var promise = Promise.<JsonObject>promise();

        var tuple = Tuple.tuple();

        try
        {
            // Handle single param which is a List (e.g., List<Integer> for ANY($1))
            if (params.size() == 1 && params.getValue(0) instanceof List)
            {
                tuple.addArrayOfInteger((Integer[]) params.getValue(0)); // directly add the List
            }
            else
            {
                for (int i = 0; i < params.size(); i++)
                {
                    tuple.addValue(params.getValue(i));
                }
            }

            client.preparedQuery(query).execute(tuple, asyncResult ->
            {
                if (asyncResult.succeeded())
                {
                    logger.info("Query executed successfully");

                    var rows = asyncResult.result();

                    var jsonRows = new JsonArray();

                    var obj = new JsonObject();

                    rows.forEach(row ->
                    {
                       obj.clear();

                        for (int i = 0; i < row.size(); i++)
                        {
                            try
                            {
                                obj.put(row.getColumnName(i), row.getValue(i));
                            }
                            catch (Exception exception)
                            {
                                logger.error("Failed to parse column value: {}", exception.getMessage());
                            }
                        }

                        jsonRows.add(obj);
                    });

                    promise.complete(new JsonObject()
                            .put(Constants.SUCCESS, true)
                            .put(Constants.DATA, jsonRows));
                }
                else
                {
                    logger.error("Query execution failed: {}", asyncResult.cause().getMessage());

                    promise.fail(new JsonObject()
                            .put(Constants.SUCCESS, false)
                            .put(Constants.ERROR, asyncResult.cause().getMessage()).toString());
                }
            });
        }
        catch (Exception exception)
        {
            logger.error("Unexpected error during query execution: {}", exception.getMessage());

            promise.fail(new JsonObject()
                    .put(Constants.SUCCESS, false)
                    .put(Constants.ERROR, exception.getMessage()).toString());
        }

        return promise.future();
    }

    private Future<JsonObject> executeBatch(String query, JsonArray params)
    {
        var promise = Promise.<JsonObject>promise();

        var batchParams = new ArrayList<Tuple>();

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

        logger.info("Executing Batch Query: {} with {} parameter sets", query, batchParams.size());

        client.preparedQuery(query).executeBatch(batchParams, asyncResult ->
        {
            if (asyncResult.succeeded())
            {
                logger.info("Batch query executed successfully");

                promise.complete(new JsonObject()
                        .put(Constants.SUCCESS, true)
                        .put(Constants.DATA, "Batch update successful"));
            }
            else
            {
                logger.error("Batch query execution failed: {}", asyncResult.cause().getMessage());

                promise.fail(new JsonObject()
                        .put(Constants.SUCCESS, false)
                        .put(Constants.ERROR, asyncResult.cause().getMessage()).toString());
            }
        });

        return promise.future();
    }

}
