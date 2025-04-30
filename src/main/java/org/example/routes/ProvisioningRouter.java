
package org.example.routes;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.example.utils.Constants;
import org.example.utils.Utils;

public class ProvisioningRouter extends AbstractRouter
{
    public ProvisioningRouter(Vertx vertx)
    {
        super(vertx);
    }

    @Override
    public void initRoutes()
    {
        router.post("/startProvision/:id").handler(this::handleStartProvision);

        router.get("/getAll").handler(dbService::getAll);

        router.get("/:id").handler(this::handleGetById);

        router.delete("/:id").handler(this::handleDelete);
    }

    private void handleStartProvision(RoutingContext context) {
        var id = context.pathParam(Constants.ID);

        if (id == null || id.isEmpty())
        {
            context.response().setStatusCode(400).end(Constants.MESSAGE_ID_REQUIRED);
        }
        else
        {
            dbService.addForProvision(id, context);
        }
    }

    @Override
    protected void handleGetById(RoutingContext context)
    {
        var id = context.pathParam(Constants.ID);

        if (id == null || id.isEmpty())
        {
            context.response().setStatusCode(400).end(Constants.MESSAGE_ID_REQUIRED);
        }
        else
        {
            var formattedReuqest = new JsonObject()
                    .put(Constants.OPERATION, Constants.DB_SELECT)
                    .put(Constants.TABLE_NAME, Utils.getTableNameFromContext(context))
                    .put(Constants.CONDITIONS , new JsonObject().put("job_id" ,Integer.parseInt(id)));

            dbService.sendToQueryBuilder(formattedReuqest,context);
        }
    }
}
