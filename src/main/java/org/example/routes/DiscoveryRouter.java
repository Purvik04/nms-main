package org.example.routes;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.Router;
import org.example.Main;
import org.example.utils.Constants;
import org.example.utils.Utils;

public class DiscoveryRouter extends AbstractRouter
{
    private final Router router;

    public DiscoveryRouter()
    {
        this.router = Router.router(Main.getVertx());

        initRoutes();
    }

    @Override
    public void initRoutes()
    {
        router.post("/createDiscoveryProfile").handler(context ->
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
                            .onSuccess(reply ->
                                context.vertx().eventBus().<JsonArray>request(Constants.EVENTBUS_DISCOVERY_ADDRESS,
                                        new JsonArray().add(reply.getJsonArray(Constants.DATA).getJsonObject(0)
                                                .getInteger(Constants.ID)), asyncResult ->
                                        {
                                            if (asyncResult.succeeded())
                                            {
                                                context.response()
                                                        .setStatusCode(Constants.SC_201)
                                                        .end(asyncResult.result().body().encode());
                                            }
                                            else
                                            {
                                                context.response().setStatusCode(Constants.SC_500).end(asyncResult.cause().getMessage());
                                            }
                                        }))
                            .onFailure(error -> dbServiceFailed(context, error.getMessage()));
                }
                catch (Exception exception)
                {
                    LOGGER.error(ERROR_MESSAGE, exception);

                    context.response().setStatusCode(Constants.SC_400).end(exception.getMessage());
                }
            })
        );

        router.post("/run").handler(context ->

            context.request().bodyHandler(body ->
            {
                if (body == null || body.length() == 0)
                {
                    context.response().setStatusCode(Constants.SC_400).end(Constants.MESSAGE_BODY_REQUIRED);
                }
                else
                {
                    context.vertx().eventBus().<JsonArray>request(Constants.EVENTBUS_DISCOVERY_ADDRESS, body.toJsonObject().getJsonArray("ids"), asyncResult ->
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
        );

        router.get("/getDiscoveryProfiles").handler(this::handleGetAll);

        router.get("/:id").handler(this::handleGetById);

        router.put("/:id").handler(this::handleUpdate);

        router.delete("/:id").handler(this::handleDelete);
    }

    @Override
    public Router getRouter() {
        return router;
    }
}
