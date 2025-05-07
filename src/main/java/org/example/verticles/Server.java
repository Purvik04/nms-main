package org.example.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import org.example.routes.CredentialRouter;
import org.example.routes.DiscoveryRouter;
import org.example.routes.ProvisioningRouter;
import org.example.service.auth.AuthHandler;
import org.example.utils.Constants;
import org.example.utils.MotaDataConfigUtil;
import org.example.utils.RequestValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This verticle sets up the main HTTPS server with routing and JWT-based authentication.
 * It wires all feature routers (credentials, discovery, provisioning) and starts the server.
 */
public class Server extends AbstractVerticle
{
    private static final String SSL_KEYSTORE_PATH = "ssl.keyStore.path";
    private static final String SSL_KEYSTORE_PASSWORD = "ssl.keyStore.password";
    private static final String HTTPS_PORT = "https.port";

    private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);

    /**
     * BootStrap entry point for the verticle. Initializes HTTP server, sets SSL options,
     * sets up all routes, and binds the server to the configured port.
     */
    @Override
    public void start(Promise<Void> startPromise)
    {
        try
        {
            // Initializes JSON schema validators globally
            RequestValidator.initialize();

            var mainRouter = Router.router(vertx);

            var authHandler = new AuthHandler();

            // Registers all the application routes
            setUpRoutes(mainRouter, authHandler, startPromise);

            // Creates HTTPS server with SSL configuration loaded from config
            vertx.createHttpServer(new HttpServerOptions().setSsl(Constants.TRUE).setKeyCertOptions(new JksOptions()
                            .setPath(MotaDataConfigUtil.getConfig()
                                    .getString(SSL_KEYSTORE_PATH, Constants.SSL_KEYSTORE_PATH))
                            .setPassword(MotaDataConfigUtil.getConfig()
                                    .getString(SSL_KEYSTORE_PASSWORD, Constants.SSL_KEYSTORE_PASSWORD))))
                    .requestHandler(mainRouter)
                    .exceptionHandler(error -> LOGGER.error("Error in main router: {}", error.getMessage()))
                    .listen(MotaDataConfigUtil.getConfig()
                            .getInteger(HTTPS_PORT, Constants.HTTPS_PORT), asyncResult ->
                    {
                        if (asyncResult.succeeded())
                        {
                            startPromise.complete();
                        }
                        else
                        {
                            startPromise.fail(asyncResult.cause());
                        }
                    });
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in starting NMS server: {}", exception.getMessage());

            startPromise.fail(exception);
        }
    }

    /**
     * Registers HTTP routes for login, refresh, and secure API access under JWTAuth.
     *
     * @param mainRouter     The root router for the application.
     * @param authHandler    Authentication handler that provides JWT logic.
     * @param startPromise   Verticle start lifecycle promise.
     */
    private static void setUpRoutes(Router mainRouter, AuthHandler authHandler, Promise<Void> startPromise)
    {
        try
        {
            // Attach body handler for all requests
            mainRouter.route("/").handler(BodyHandler.create());

            // Public login and token refresh endpoints
            mainRouter.post("/login").handler(authHandler::handleLogin);

            mainRouter.post("/refresh").handler(authHandler::handleRefresh);

            // Secure routes under /api/* require JWT auth
            mainRouter.route("/api/*")
                    .handler(JWTAuthHandler.create(authHandler.getProvider()));

            // Mount sub-routers for each feature module
            mainRouter.route("/api/credentials/*").subRouter(new CredentialRouter().getRouter());

            mainRouter.route("/api/discovery/*").subRouter(new DiscoveryRouter().getRouter());

            mainRouter.route("/api/provision/*").subRouter(new ProvisioningRouter().getRouter());
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in setting up routes: {}", exception.getMessage());

            startPromise.fail(exception);
        }
    }
}
