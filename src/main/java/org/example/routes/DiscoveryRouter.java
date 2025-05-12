package org.example.routes;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.example.BootStrap;
import org.example.utils.Constants;
import org.example.utils.Utils;
import org.example.verticles.DiscoveryEngine;

public class DiscoveryRouter extends AbstractRouter
{
    private final Router router;

    public DiscoveryRouter()
    {
        this.router = Router.router(BootStrap.getVertx());

        initRoutes();
    }

    @Override
    public void initRoutes()
    {
        router.post("/createDiscoveryProfile").handler(this::handleCreate);

        router.post("/run").handler(this::handleDiscovery);

        router.get("/getDiscoveryProfiles").handler(this::handleGetAll);

        router.get("/:id").handler(this::handleGetById);

        router.put("/:id").handler(this::handleUpdate);

        router.delete("/:id").handler(this::handleDelete);
    }

    @Override
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

                DATABASE_SERVICE
                        .executeQuery(Utils.buildQuery(reusableQueryObject, reusableStringQuery, reusableQueryParams))
                        .onSuccess(reply ->

                                DATABASE_SERVICE.executeQuery(new JsonObject()
                                        .put(Constants.QUERY,  Utils.buildJoinQuery(Constants.FETCH_DISCOVERY_PROFILES_QUERY,reply.getJsonArray(Constants.DATA).size()))
                                        .put(Constants.PARAMS, new JsonArray().add(reply.getJsonArray(Constants.DATA)
                                                .getJsonObject(0).getInteger(Constants.ID))))
                                        .onSuccess(databaseReply ->

                                            context.vertx().eventBus().<JsonArray>request(Constants.DISCOVERY_ADDRESS,
                                                    databaseReply.getJsonArray(Constants.DATA), asyncResult ->
                                                    {
                                                        if (asyncResult.succeeded())
                                                        {
                                                            context.response().setStatusCode(Constants.SC_201)
                                                                    .end(asyncResult.result().body().encode());
                                                        }
                                                        else
                                                        {
                                                            context.response().setStatusCode(Constants.SC_500)
                                                                    .end(asyncResult.cause().getMessage());
                                                        }
                                                    })
                                        ).onFailure(error -> dbServiceFailed(context, error.getMessage())))
                        .onFailure(error -> dbServiceFailed(context, error.getMessage()));
            }
            catch (Exception exception)
            {
                LOGGER.error(ERROR_MESSAGE, exception);

                context.response().setStatusCode(Constants.SC_400).end(exception.getMessage());
            }
        });
    }

    void handleDiscovery(RoutingContext context)
    {
        context.request().bodyHandler(body ->
        {
            try
            {
                if (body == null || body.length() == 0)
                {
                    context.response().setStatusCode(Constants.SC_400).end(Constants.MESSAGE_BODY_REQUIRED);
                }
                else
                {
                    setReusableObjects();

                    var deviceIDs = body.toJsonObject().getJsonArray(Constants.IDS);

                    DATABASE_SERVICE.executeQuery(new JsonObject()
                                    .put(Constants.QUERY, Utils.buildJoinQuery(Constants.FETCH_DISCOVERY_PROFILES_QUERY,deviceIDs.size()))
                                    .put(Constants.PARAMS,deviceIDs))
                                    .onSuccess(response->
                                    {
                                        if (response.getJsonArray(Constants.DATA).isEmpty())
                                        {
                                            LOGGER.error("No discovery profiles found for IDs: {}", deviceIDs);

                                            context.response().end(new JsonObject()
                                                    .put(Constants.SUCCESS, Constants.FALSE)
                                                    .put(Constants.ERROR, "No discovery profiles found").encode());
                                        }
                                        else
                                        {
                                            context.vertx().eventBus().<JsonArray>request(Constants.DISCOVERY_ADDRESS,response.getJsonArray(Constants.DATA), asyncResult ->
                                            {
                                                if (asyncResult.succeeded())
                                                {
                                                    context.response()
                                                            .setStatusCode(Constants.SC_200)
                                                            .end(asyncResult.result().body().encode());
                                                }
                                                else
                                                {
                                                    context.response().setStatusCode(Constants.SC_500).end(asyncResult.cause().getMessage());
                                                }
                                            });
                                        }
                                    })
                                    .onFailure(error -> dbServiceFailed(context, error.getMessage()));
                }
            }
            catch (Exception exception)
            {
                LOGGER.error(ERROR_MESSAGE, exception);

                context.response().setStatusCode(Constants.SC_500).end(exception.getMessage());
            }
        });
    }

    @Override
    public Router getRouter()
    {
        return router;
    }
}
