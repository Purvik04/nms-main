package org.example.routes;

import io.vertx.ext.web.Router;
import org.example.BootStrap;

public class CredentialRouter extends AbstractRouter
{
    private final Router router;

    public CredentialRouter()
    {
        this.router = Router.router(BootStrap.getVertx());

        initRoutes();
    }

    @Override
    public void initRoutes()
    {
        router.post("/createCredential").handler(this::handleCreate);

        router.get("/getCredentials").handler(this::handleGetAll);

        router.get("/:id").handler(this::handleGetById);

        router.put("/:id").handler(this::handleUpdate);

        router.delete("/:id").handler(this::handleDelete);
    }

    @Override
    public Router getRouter() {
        return router;
    }
}
