
package org.example.routes;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.example.BootStrap;
import org.example.cache.AvailabilityCacheEngine;
import org.example.utils.Constants;
import org.example.utils.Utils;

public class ProvisioningRouter extends AbstractRouter
{
    private final Router router;

    private static final String MESSAGE_DEVICE_NOT_DISCOVERED = "Device is not discovered yet";

    private static final String MESSAGE_DISCOVERY_PROFILE_NOT_FOUND = "Discovery profile not found";

    private static final String MESSAGE_DEVICE_IS_ALREADY_PROVISIONING = "Device is already provisioning";

    public ProvisioningRouter()
    {
        this.router = Router.router(BootStrap.getVertx());

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

    //todo:- simplify logic of provision
    void handleStartProvision(RoutingContext context)
    {
        var id = context.pathParam(Constants.ID);

        try
        {
            if(isInvalidId(context.pathParam(Constants.ID),context)) return;

            setReusableObjects();

            reusableQueryObject.put(Constants.OPERATION, Constants.DB_SELECT)
                    .put(Constants.TABLE_NAME, Constants.DISCOVERY_PROFILES_TABLE)
                    .put(Constants.CONDITIONS , new JsonObject().put(Constants.ID, Integer.parseInt(id)));

            DATABASE_SERVICE
                    .executeQuery(Utils.buildQuery(reusableQueryObject, reusableStringQuery, reusableQueryParams))
                    .onSuccess(result ->
                    {
                        if (!Boolean.TRUE.equals(result.getBoolean(Constants.SUCCESS)))
                        {
                            context.response()
                                    .setStatusCode(Constants.SC_500)
                                    .end(new JsonObject().put(Constants.ERROR, result.getString(Constants.ERROR)).encodePrettily());

                            return;
                        }

                        var response = result.getJsonArray(Constants.DATA);

                        if (response == null || response.isEmpty())
                        {
                            context.response()
                                    .setStatusCode(Constants.SC_404)
                                    .end(new JsonObject()
                                            .put(Constants.SUCCESS, Constants.FALSE)
                                            .put(Constants.ERROR,MESSAGE_DISCOVERY_PROFILE_NOT_FOUND).encodePrettily());

                            return;
                        }

                        var discoveryProfile = response.getJsonObject(0);

                        if (!Boolean.TRUE.equals(discoveryProfile.getBoolean(Constants.STATUS)))
                        {
                            context.response()
                                    .setStatusCode(Constants.SC_404)
                                    .end(new JsonObject()
                                            .put(Constants.SUCCESS, Constants.FALSE)
                                            .put(Constants.ERROR, MESSAGE_DEVICE_NOT_DISCOVERED).encodePrettily());

                            return;
                        }

                        setReusableObjects();

                        reusableQueryObject.put(Constants.OPERATION, Constants.DB_SELECT)
                                .put(Constants.TABLE_NAME, Constants.PROVISION_TABLE)
                                .put(Constants.CONDITIONS , new JsonObject()
                                        .put(Constants.IP, discoveryProfile.getString(Constants.IP))
                                        .put(Constants.CREDENTIAL_PROFILE_ID
                                                ,discoveryProfile.getInteger(Constants.CREDENTIAL_PROFILE_ID)));

                        DATABASE_SERVICE.executeQuery(Utils.buildQuery(reusableQueryObject, reusableStringQuery, reusableQueryParams))
                                .onSuccess(asyncResult ->
                                {
                                    var databaseResponse = asyncResult.getJsonArray(Constants.DATA);

                                    setReusableObjects();

                                    if(databaseResponse.isEmpty())
                                    {
                                        reusableQueryObject.put(Constants.OPERATION, Constants.DB_INSERT)
                                                .put(Constants.TABLE_NAME, Constants.PROVISION_TABLE)
                                                .put(Constants.DATA, new JsonObject()
                                                        .put(Constants.IP, discoveryProfile.getString(Constants.IP))
                                                        .put(Constants.PORT, discoveryProfile.getInteger(Constants.PORT))
                                                        .put(Constants.CREDENTIAL_PROFILE_ID, discoveryProfile.getInteger(Constants.CREDENTIAL_PROFILE_ID)));

                                        DATABASE_SERVICE
                                                .executeQuery(Utils.buildQuery(reusableQueryObject, reusableStringQuery, reusableQueryParams))
                                                .onSuccess(dbReply ->
                                                {
                                                    AvailabilityCacheEngine.setDeviceStatus(dbReply.getJsonArray(Constants.DATA).getJsonObject(0).getInteger(Constants.ID), Constants.UP);

                                                    context.response().setStatusCode(Constants.SC_201).end(dbReply.encode());
                                                })
                                                .onFailure(error -> dbServiceFailed(context, error.getMessage()));
                                    }
                                    else if(!Boolean.TRUE.equals(databaseResponse.getJsonObject(0).getBoolean(Constants.STATUS)))
                                    {
                                        reusableQueryObject.put(Constants.OPERATION, Constants.DB_UPDATE)
                                                .put(Constants.CONDITIONS ,new JsonObject().put(Constants.ID,databaseResponse.getJsonObject(0).getInteger(Constants.ID)))
                                                .put(Constants.TABLE_NAME,Constants.PROVISION_TABLE)
                                                .put(Constants.DATA, new JsonObject().put(Constants.STATUS,Constants.TRUE));

                                        DATABASE_SERVICE
                                                .executeQuery(Utils.buildQuery(reusableQueryObject, reusableStringQuery, reusableQueryParams))
                                                .onSuccess(dbReply ->
                                                {
                                                    AvailabilityCacheEngine.setDeviceStatus(databaseResponse.getJsonObject(0).getInteger(Constants.ID), Constants.UP);

                                                    context.response().setStatusCode(Constants.SC_201)
                                                            .end(new JsonObject()
                                                                    .put(Constants.SUCCESS, Constants.TRUE)
                                                                    .put(Constants.ID,databaseResponse.getJsonObject(0).getInteger(Constants.ID)).encode());

                                                })
                                                .onFailure(error -> dbServiceFailed(context, error.getMessage()));
                                    }
                                    else
                                    {
                                        context.response().setStatusCode(Constants.SC_400)
                                                .end(new JsonObject().put(Constants.SUCCESS, Constants.FALSE)
                                                        .put(Constants.ERROR,MESSAGE_DEVICE_IS_ALREADY_PROVISIONING)
                                                        .encodePrettily());
                                    }
                                });


                    })
                    .onFailure(error -> dbServiceFailed(context, error.getMessage()));
        }
        catch (Exception exception)
        {
            LOGGER.error(ERROR_MESSAGE, exception);

            context.response().setStatusCode(Constants.SC_500).end(exception.getMessage());
        }
    }

    @Override
    void handleGetById(RoutingContext context)
    {
        try
        {
            if(isInvalidId(context.pathParam(Constants.ID),context)) return;

            setReusableObjects();

            reusableQueryObject.put(Constants.OPERATION, Constants.DB_SELECT)
                    .put(Constants.TABLE_NAME, Utils.getTableNameFromContext(context))
                    .put(Constants.CONDITIONS , new JsonObject().put(Constants.PROVISION_ID,
                            Integer.parseInt(context.pathParam(Constants.ID))));

            DATABASE_SERVICE
                    .executeQuery(Utils.buildQuery(reusableQueryObject, reusableStringQuery, reusableQueryParams))
                    .onSuccess(reply -> context.response().setStatusCode(Constants.SC_200).end(reply.toString()))
                    .onFailure(error -> context.response().setStatusCode(Constants.SC_500).end(error.getMessage()));

        }
        catch (Exception exception)
        {
            LOGGER.error(ERROR_MESSAGE, exception);

            context.response().setStatusCode(Constants.SC_500).end(exception.getMessage());
        }
    }

    @Override
    void handleDelete(RoutingContext context)
    {
        try
        {
            if (isInvalidId(context.pathParam(Constants.ID), context)) return;

            var id = Integer.parseInt(context.pathParam(Constants.ID));

            setReusableObjects();

            reusableQueryObject
                    .put(Constants.OPERATION, Constants.DB_UPDATE)
                    .put(Constants.CONDITIONS , new JsonObject().put(Constants.ID,id))
                    .put(Constants.TABLE_NAME, Utils.getTableNameFromContext(context))
                    .put(Constants.DATA, new JsonObject()
                            .put(Constants.STATUS, Constants.FALSE));

            DATABASE_SERVICE.executeQuery(Utils.buildQuery(reusableQueryObject, reusableStringQuery, reusableQueryParams)).onSuccess(reply ->
                    {
                        try
                        {
                            AvailabilityCacheEngine.removeDevice(id);

                            context.response().setStatusCode(Constants.SC_200).end(reply.encode());
                        }
                        catch (Exception exception)
                        {
                            context.response().setStatusCode(Constants.SC_500).end(exception.getMessage());
                        }
                    }).onFailure(error -> dbServiceFailed(context, error.getMessage()));
        }
        catch (Exception exception)
        {
            LOGGER.error(ERROR_MESSAGE, exception);

            context.response().setStatusCode(Constants.SC_500).end(exception.getMessage());
        }
    }

    @Override
    public Router getRouter()
    {
        return router;
    }
}
