package org.example.routes;

import io.vertx.core.Vertx;
import org.example.service.DBService;

public class CredentialRouter extends AbstractRouter
{
    public CredentialRouter(Vertx vertx)
    {
        super(vertx);
    }
    @Override
    public void initRoutes()
    {
        System.out.println("Initializing CredentialRouter...");

        router.post("/createCredential").handler(this::handleCreate);

        router.get("/getCredentials").handler(dbService::getAll);

        router.get("/:id").handler(this::handleGetById);

        router.put("/:id").handler(this::handleUpdate);

        router.delete("/:id").handler(this::handleDelete);
    }
}
