package org.example.routes;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.example.service.DBService;
import org.example.utils.Constants;
//import org.example.utils.RequestValidator;
//import org.example.utils.RequestValidator2;
//import org.example.utils.Utils;

public abstract class AbstractRouter implements RouterHandler
{
    protected final Router router;
    protected final DBService dbService;

    protected AbstractRouter(Vertx vertx)
    {
        this.router = Router.router(vertx);

        this.dbService = new DBService(vertx);

        initRoutes();
    }

    protected void handleCreate(RoutingContext context)
    {
        context.request().bodyHandler(body ->
        {
            if (isInvalidBody(body.toJsonObject(), context)) return;

            dbService.create(body.toJsonObject(), context);
        });
    }

    protected void handleUpdate(RoutingContext context)
    {
        context.request().bodyHandler(body ->
        {
            if (isInvalidId(context.pathParam(Constants.ID), context) || isInvalidBody(body.toJsonObject(), context)) return;

            dbService.update(context.pathParam(Constants.ID), body.toJsonObject(), context);
        });
    }

    protected void handleGetById(RoutingContext context)
    {
        if (isInvalidId(context.pathParam(Constants.ID), context)) return;

        dbService.getById(context.pathParam(Constants.ID), context);
    }

    protected void handleDelete(RoutingContext context)
    {
        if (isInvalidId(context.pathParam(Constants.ID), context)) return;

        dbService.delete(context.pathParam(Constants.ID), context);
    }

    private boolean isInvalidId(String id, RoutingContext context)
    {
        if (id == null || id.isEmpty())
        {
            context.response().setStatusCode(400).end(Constants.MESSAGE_ID_REQUIRED);

            return true;
        }

        return false;
    }

    private boolean isInvalidBody(JsonObject body, RoutingContext context)
    {
        if (body == null || body.isEmpty())
        {
            context.response().setStatusCode(400).end(Constants.MESSAGE_BODY_REQUIRED);

            return true;
        }

//        var errorMessage = RequestValidator.validate(Utils.getTableNameFromContext(context), body);

//        if(errorMessage != null)
//        {
//            context.response().setStatusCode(400).end(errorMessage);
//
//            return true;
//        }

//        return false;
        return true;
    }

    @Override
    public Router getRouter() {
        return router;
    }
}
