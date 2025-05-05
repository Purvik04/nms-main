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

public class NmsServerVerticle extends AbstractVerticle
{
    private static final String SSL_KEYSTORE_PATH = "ssl_keyStore_path";

    private static final String SSL_KEYSTORE_PASSWORD = "ssl_keyStore_password";

    private static final String HTTP_PORT = "http_port";

    private static final Logger LOGGER = LoggerFactory.getLogger(NmsServerVerticle.class);

    @Override
    public void start(Promise<Void> startPromise)
    {
        try
        {
            RequestValidator.initialize();

            var mainRouter = Router.router(vertx);

            var authHandler = new AuthHandler();

            setUpRoutes(mainRouter, authHandler, startPromise);

            vertx.createHttpServer(new HttpServerOptions().setSsl(Constants.TRUE).setKeyCertOptions(new JksOptions()
                                            .setPath(MotaDataConfigUtil.getConfig()
                                                    .getString(SSL_KEYSTORE_PATH, Constants.SSL_KEYSTORE_PATH))
                                            .setPassword(MotaDataConfigUtil.getConfig()
                                                    .getString(SSL_KEYSTORE_PASSWORD, Constants.SSL_KEYSTORE_PASSWORD))))
                    .requestHandler(mainRouter)
                    .listen(MotaDataConfigUtil.getConfig()
                            .getInteger(HTTP_PORT, Constants.HTTP_PORT), asyncResult ->
                    {
                        if (asyncResult.succeeded())
                        {
                            startPromise.complete();
                        }
                        else
                        {
                            startPromise.fail(asyncResult.cause().getMessage());
                        }
                    });
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in starting NMS server: {}", exception.getMessage());

            startPromise.fail(exception.getMessage());
        }
    }

    private static void setUpRoutes(Router mainRouter, AuthHandler authHandler, Promise<Void> startPromise)
    {
        try
        {
            mainRouter.route("/").handler(BodyHandler.create());

            // Public login route
            mainRouter.post("/login").handler(authHandler::handleLogin);

            mainRouter.post("/refresh").handler(authHandler::handleRefresh);

            mainRouter.route("/api/*")
                    .handler(JWTAuthHandler.create(authHandler.getProvider()));


            mainRouter.route("/api/credentials/*").subRouter(new CredentialRouter().getRouter());

            mainRouter.route("/api/discovery/*").subRouter(new DiscoveryRouter().getRouter());

            mainRouter.route("/api/provision/*").subRouter(new ProvisioningRouter().getRouter());
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in setting up routes: {}", exception.getMessage());

            startPromise.fail(exception.getMessage());
        }
    }
}

