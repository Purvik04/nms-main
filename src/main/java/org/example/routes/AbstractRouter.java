package org.example.routes;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.example.service.database.DatabaseService;
import org.example.service.database.Database;
import org.example.utils.Constants;
import org.example.utils.RequestValidator;
import org.example.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractRouter implements RouterHandler
{
    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractRouter.class);

    protected static final DatabaseService DATABASE_SERVICE = DatabaseService.createProxy(Database.DB_SERVICE_ADDRESS);

    protected static final String ERROR_MESSAGE = "Error while processing request";

    protected final StringBuilder reusableStringQuery = new StringBuilder();

    protected final JsonArray reusableQueryParams = new JsonArray();

    protected final JsonObject reusableQueryObject = new JsonObject();

    void handleCreate(RoutingContext context)
    {
        context.request().bodyHandler(body ->
        {
            try
            {
                if (isInvalidBody(body.toJsonObject(), context)) return;

                setReusableObjects();

                reusableQueryObject.put(Constants.OPERATION, Constants.DB_INSERT)
                        .put(Constants.TABLE_NAME, Utils.getTableNameFromContext(context))
                        .put(Constants.DATA, body.toJsonObject());

                var query = Utils.buildQuery(reusableQueryObject, reusableStringQuery, reusableQueryParams);

                DATABASE_SERVICE
                        .executeQuery(query)
                        .onSuccess(reply -> context.response().setStatusCode(Constants.SC_201).end(reply.encode()))
                        .onFailure(error -> dbServiceFailed(context, error.getMessage()));

            }
            catch (Exception exception)
            {
                LOGGER.error(ERROR_MESSAGE, exception);

                context.response().setStatusCode(Constants.SC_400).end(exception.getMessage());
            }
        });
    }

    void handleUpdate(RoutingContext context)
    {
        context.request().bodyHandler(body ->
        {
            try
            {
                if (isInvalidId(context.pathParam(Constants.ID), context) || isInvalidBody(body.toJsonObject(), context)) return;

                setReusableObjects();

                reusableQueryObject
                        .put(Constants.OPERATION, Constants.DB_UPDATE)
                        .put(Constants.TABLE_NAME, Utils.getTableNameFromContext(context))
                        .put(Constants.DATA, body.toJsonObject())
                        .put(Constants.CONDITIONS , new JsonObject().put(Constants.ID, Integer.parseInt(context.pathParam(Constants.ID))));

                var query = Utils.buildQuery(reusableQueryObject, reusableStringQuery, reusableQueryParams);

                DATABASE_SERVICE
                        .executeQuery(query)
                        .onSuccess(reply ->
                                context.response()
                                        .setStatusCode(Constants.SC_201)
                                        .end(reply.toString())
                        )
                        .onFailure(error -> dbServiceFailed(context, error.getMessage()));
            }
            catch (Exception exception)
            {
                LOGGER.error(ERROR_MESSAGE, exception);

                context.response().setStatusCode(Constants.SC_400).end(exception.getMessage());
            }
        });
    }

    void handleGetById(RoutingContext context)
    {
        try
        {
            if (isInvalidId(context.pathParam(Constants.ID), context)) return;

            setReusableObjects();

            reusableQueryObject
                    .put(Constants.OPERATION, Constants.DB_SELECT)
                    .put(Constants.TABLE_NAME, Utils.getTableNameFromContext(context))
                    .put(Constants.CONDITIONS , new JsonObject().put(Constants.ID, Integer.parseInt(context.pathParam(Constants.ID))));

            var query = Utils.buildQuery(reusableQueryObject, reusableStringQuery, reusableQueryParams);

            DATABASE_SERVICE
                    .executeQuery(query)
                    .onSuccess(reply ->
                            context.response()
                                    .setStatusCode(Constants.SC_200)
                                    .end(reply.toString())
                    )
                    .onFailure(error -> dbServiceFailed(context, error.getMessage()));
        }
        catch (Exception exception)
        {
            LOGGER.error(ERROR_MESSAGE, exception);

            context.response().setStatusCode(Constants.SC_400).end(exception.getMessage());
        }

    }

    void handleDelete(RoutingContext context)
    {
        try
        {
            if (isInvalidId(context.pathParam(Constants.ID), context)) return;

            setReusableObjects();

            reusableQueryObject
                    .put(Constants.OPERATION, Constants.DB_DELETE)
                    .put(Constants.TABLE_NAME, Utils.getTableNameFromContext(context))
                    .put(Constants.CONDITIONS, new JsonObject().put(Constants.ID, Integer.parseInt(context.pathParam(Constants.ID))));

            var query = Utils.buildQuery(reusableQueryObject, reusableStringQuery, reusableQueryParams);

            DATABASE_SERVICE
                    .executeQuery(query)
                    .onSuccess(reply -> context.response().setStatusCode(Constants.SC_200).end(reply.encode()))
                    .onFailure(error -> dbServiceFailed(context, error.getMessage()));
        }
        catch (Exception exception)
        {
            LOGGER.error(ERROR_MESSAGE, exception);

            context.response().setStatusCode(Constants.SC_400).end(exception.getMessage());
        }
    }

    void handleGetAll(RoutingContext context)
    {
        try
        {
            setReusableObjects();

            reusableQueryObject.put(Constants.OPERATION, Constants.DB_SELECT)
                    .put(Constants.TABLE_NAME, Utils.getTableNameFromContext(context));

            var query = Utils.buildQuery(reusableQueryObject, reusableStringQuery, reusableQueryParams);

            DATABASE_SERVICE.executeQuery(query)
                    .onSuccess(reply -> context.response().setStatusCode(Constants.SC_200)
                            .end(reply.toString()))
                    .onFailure(error -> dbServiceFailed(context, error.getMessage()));
        }
        catch (Exception exception)
        {
            LOGGER.error(ERROR_MESSAGE, exception);

            context.response().setStatusCode(Constants.SC_400).end(exception.getMessage());
        }

    }

    boolean isInvalidId(String id, RoutingContext context)
    {
        if (id == null || id.isEmpty() || Integer.parseInt(id) < 1)
        {
            context.response().setStatusCode(Constants.SC_400).end(Constants.MESSAGE_ID_INVALID);

            return true;
        }

        return false;
    }

    boolean isInvalidBody(JsonObject body, RoutingContext context)
    {
        try
        {
            if (body == null || body.isEmpty())
            {
                context.response().setStatusCode(Constants.SC_400).end(Constants.MESSAGE_BODY_REQUIRED);

                return true;
            }

            var errorMessage = RequestValidator.validate(Utils.getTableNameFromContext(context), body);

            if(!errorMessage.isEmpty())
            {
                context.response().setStatusCode(Constants.SC_400).end(new JsonObject()
                        .put(Constants.SUCCESS,Constants.FALSE).put(Constants.ERROR,errorMessage).encode());

                return true;
            }
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in validating body:", exception);

            context.response().setStatusCode(Constants.SC_400).end(exception.getMessage());

            return true;
        }

        return false;
    }

    protected void dbServiceFailed(RoutingContext context, String errorMessage)
    {
        context.response()
                .setStatusCode(Constants.SC_500)
                .end(new JsonObject().put(Constants.ERROR, errorMessage).encodePrettily());
    }

    protected void setReusableObjects()
    {
        reusableStringQuery.setLength(0);

        reusableQueryParams.clear();

        reusableQueryObject.clear();
    }
}
