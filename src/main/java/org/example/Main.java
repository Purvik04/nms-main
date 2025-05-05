package org.example;

import io.vertx.core.*;
import org.example.service.database.Database;
import org.example.utils.Constants;
import org.example.utils.MotaDataConfigUtil;
import org.example.utils.VerticleConfig;
import org.example.verticles.*;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class Main
{
    private static final Vertx VERTX = Vertx.vertx(new VertxOptions().setWorkerPoolSize(20)
            .setEventLoopPoolSize(Runtime.getRuntime().availableProcessors()));

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private static final int DISCOVERY_VERTICLE_INSTANCES = 1;

    private static final int SERVER_VERTICLE_INSTANCES = 1;

    private static final int POLLING_PROCESSOR_VERTICLE_INSTANCES = 2;

    private static final int DATABASE_VERTICLE_INSTANCES = 2;

    private static final String CONFIG_FILE_PATH = "config.json";

    static
    {
        try
        {
            MotaDataConfigUtil.loadConfig(CONFIG_FILE_PATH);
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to load configuration: {}", e.getMessage());

            System.exit(1);
        }
    }

    public static Vertx getVertx()
    {
        return VERTX;
    }

    public static void main(String[] args)
    {
        runFlywayMigrations()
                .onComplete(res ->
                {
                    if (res.succeeded())
                    {
                        LOGGER.info("Database migration complete.");

                        deployVerticles();
                    }
                    else
                    {
                        LOGGER.error("Database migration failed: {}", res.cause().getMessage());
                    }
                });
    }

    private static Future<Void> runFlywayMigrations()
    {
        var promise = Promise.<Void>promise();

        try
        {
            Flyway flyway = Flyway.configure()
                    .dataSource("jdbc:postgresql://localhost:5432/nms_lite", "purvik", "admin")
                    .baselineOnMigrate(true)
                    .load();

            if (flyway.migrate().success)
            {
                promise.complete();
            }
            else
            {
                promise.fail("No migrations were executed.");
            }
        }
        catch (Exception exception)
        {
            LOGGER.error("Flyway migration failed: {}", exception.getMessage());

            promise.fail(exception.getMessage());
        }

        return promise.future();
    }

    private static void deployVerticles()
    {
        var deploymentSequence = Arrays.asList(
                new VerticleConfig(Database.class, new DeploymentOptions().setInstances(DATABASE_VERTICLE_INSTANCES)),

                new VerticleConfig(NmsServerVerticle.class, new DeploymentOptions()
                        .setInstances(SERVER_VERTICLE_INSTANCES)),

                new VerticleConfig(AvailabilityPollingEngine.class, new DeploymentOptions()
                        .setInstances(1)),

                new VerticleConfig(PollingProcessorEngine.class, new DeploymentOptions()
                        .setInstances(POLLING_PROCESSOR_VERTICLE_INSTANCES)),

                new VerticleConfig(MetricPollingVerticle.class, new DeploymentOptions()),

                new VerticleConfig(PollerEngine.class, new DeploymentOptions()),

                new VerticleConfig(DiscoveryEngine.class, new DeploymentOptions()
                        .setInstances(DISCOVERY_VERTICLE_INSTANCES).setWorkerPoolSize(5)
                        .setWorkerPoolName(Constants.EVENTBUS_DISCOVERY_ADDRESS))
        );

        var deploymentChain = Future.<Void>succeededFuture();

        for (var verticle : deploymentSequence)
        {
            deploymentChain = deploymentChain.compose(res -> VERTX.deployVerticle(verticle.verticleClass.getName(), verticle.options)
                    .onSuccess(id -> LOGGER.info("{} started successfully",
                            verticle.verticleClass.getSimpleName()))
                    .mapEmpty());
        }

        deploymentChain
                .onSuccess(res -> LOGGER.info("All verticles started successfully in sequence"))
                .onFailure(err ->
                {
                    LOGGER.error("Deployment failed at {}: {}", err.getStackTrace()[0].getClassName(), err.getMessage());
                    VERTX.close();
                });
    }
}

