
package org.example.routes;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.example.Main;
import org.example.cache.AvailabilityCacheEngine;
import org.example.utils.Constants;
import org.example.utils.Utils;

public class ProvisioningRouter extends AbstractRouter
{
    private final Router router;

    public ProvisioningRouter()
    {
        this.router = Router.router(Main.getVertx());

        initRoutes();
    }

    @Override
    public void initRoutes()
    {
        router.post("/startProvision/:id").handler(this::handleStartProvision);

        router.get("/getAll").handler(this::handleGetAll);

        router.get("/:id").handler(this::handleGetById);

        router.delete("/:id").handler(this::handleDelete);
    }

    private void handleStartProvision(RoutingContext context)
    {
        var id = context.pathParam(Constants.ID);

        if (id == null || id.isEmpty())
        {
            context.response().setStatusCode(Constants.SC_400).end(Constants.MESSAGE_ID_INVALID);
        }
        else
        {
            setReusableObjects();

            reusableQueryObject.put(Constants.OPERATION, Constants.DB_SELECT)
                    .put(Constants.TABLE_NAME, Constants.DISCOVERY_PROFILES_TABLE_NAME)
                    .put(Constants.CONDITIONS , new JsonObject().put(Constants.ID, Integer.parseInt(id)));

            var query = Utils.buildQuery(reusableQueryObject, reusableStringQuery, reusableQueryParams);

            DATABASE_SERVICE
                    .executeQuery(query)
                    .onSuccess(result ->
                    {
                        if (!Boolean.TRUE.equals(result.getBoolean(Constants.SUCCESS)))
                        {
                            context.response()
                                    .setStatusCode(Constants.SC_500)
                                    .end(new JsonObject().put(Constants.ERROR, result.getString(Constants.ERROR)).encodePrettily());

                            return;
                        }

                        var data = result.getJsonArray(Constants.DATA);

                        if (data == null || data.isEmpty())
                        {
                            context.response()
                                    .setStatusCode(Constants.SC_404)
                                    .end(new JsonObject().put(Constants.ERROR, "Discovery profile not found").encodePrettily());

                            return;
                        }

                        var discoveryProfile = data.getJsonObject(0);

                        if (!Boolean.TRUE.equals(discoveryProfile.getBoolean(Constants.STATUS)))
                        {
                            context.response()
                                    .setStatusCode(Constants.SC_404)
                                    .end(new JsonObject().put(Constants.ERROR, "Device is not discovered yet").encodePrettily());

                            return;
                        }

                        setReusableObjects();

                        reusableQueryObject.put(Constants.OPERATION, Constants.DB_INSERT)
                                .put(Constants.TABLE_NAME, Utils.getTableNameFromContext(context))
                                .put(Constants.DATA, new JsonObject()
                                        .put(Constants.IP, discoveryProfile.getString(Constants.IP))
                                        .put(Constants.PORT, discoveryProfile.getInteger(Constants.PORT))
                                        .put(Constants.CREDENTIAL_PROFILE_ID, discoveryProfile.getInteger(Constants.CREDENTIAL_PROFILE_ID)));

                        DATABASE_SERVICE
                                .executeQuery(Utils.buildQuery(reusableQueryObject, reusableStringQuery, reusableQueryParams))
                                .onSuccess(dbReply ->
                                        {
                                            AvailabilityCacheEngine.setDeviceStatus(dbReply.getJsonArray(Constants.DATA).getJsonObject(0).getInteger(Constants.ID), Constants.UP);

                                            LOGGER.info("after provisionnig {}", AvailabilityCacheEngine.getAllDeviceIds());

                                            context.response().setStatusCode(Constants.SC_201).end(dbReply.encode());
                                        })
                                .onFailure(error ->
                                        context.response()
                                            .setStatusCode(Constants.SC_500)
                                            .end(new JsonObject().put(Constants.ERROR, "DBService failed: " + error.getMessage()).encodePrettily())
                                );
                    })
                    .onFailure(error -> dbServiceFailed(context, error.getMessage()));
        }
    }

    @Override
    protected void handleGetById(RoutingContext context)
    {
        var id = context.pathParam(Constants.ID);

        if (id == null || id.isEmpty())
        {
            context.response().setStatusCode(Constants.SC_400).end(Constants.MESSAGE_ID_INVALID);
        }
        else
        {
            setReusableObjects();

            reusableQueryObject.put(Constants.OPERATION, Constants.DB_SELECT)
                .put(Constants.TABLE_NAME, Utils.getTableNameFromContext(context))
                    .put(Constants.CONDITIONS , new JsonObject().put(Constants.JOB_ID, Integer.parseInt(id)));

            var query = Utils.buildQuery(reusableQueryObject, reusableStringQuery, reusableQueryParams);

            DATABASE_SERVICE
                .executeQuery(query)
                .onSuccess(reply ->
                    context.response()
                        .setStatusCode(Constants.SC_200)
                        .end(reply.toString())
                )
                .onFailure(error -> context.response().setStatusCode(Constants.SC_500).end(error.getMessage()));
        }
    }

    @Override
    public Router getRouter() {
        return router;
    }
}
