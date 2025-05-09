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

import java.util.ArrayList;
import java.util.Arrays;

/**
 * BootStrap entry point for the Lite NMS application.
 * Handles configuration loading, database migration, and sequential Verticle deployment.
 */
public class BootStrap
{
    private static final Logger LOGGER = LoggerFactory.getLogger(BootStrap.class);

    // Load configuration statically at application startup
    static
    {
        try
        {
            MotaDataConfigUtil.loadConfig(Constants.CONFIG_FILE_PATH);
        }
        catch (Exception exception)
        {
            LOGGER.error("Failed to load configuration: {}", exception.getMessage());

            System.exit(1);
        }
    }

    private static final String DATABASE_USER = MotaDataConfigUtil.getConfig()
            .getString("database.user" , "postgres");
    private static final String DATABASE_PASSWORD = MotaDataConfigUtil.getConfig()
            .getString("database.password" , "postgres");
    private static final int DATABASE_VERTICLE_INSTANCES = MotaDataConfigUtil.getConfig()
            .getInteger("database.verticle.instances", 1);
    private static final int SERVER_VERTICLE_INSTANCES = MotaDataConfigUtil.getConfig()
            .getInteger("server.verticle.instances", 1);
    private static final int POLLER_ENGINE_INSTANCES = MotaDataConfigUtil.getConfig()
            .getInteger("poller.engine.instances", 1);
    private static final int METRIC_POLLING_ENGINE_INSTANCES = MotaDataConfigUtil.getConfig()
            .getInteger("metric.polling.engine.instances", 1);
    private static final int POLLING_PROCESSOR_INSTANCES = MotaDataConfigUtil.getConfig()
            .getInteger("poller.processor.engine.instances", 1);
    private static final int AVAILABILITY_POLLING_ENGINE_INSTANCES = MotaDataConfigUtil.getConfig()
            .getInteger("availability.polling.engine.instances", 1);
    private static final int DISCOVERY_ENGINE_INSTANCES = MotaDataConfigUtil.getConfig()
            .getInteger("discovery.engine.instances", 1);
    private static final int EVENTLOOP_POOL_SIZE = MotaDataConfigUtil.getConfig()
            .getInteger("eventloop.pool.size", 7);
    private static final int WORKER_POOL_SIZE = MotaDataConfigUtil.getConfig()
            .getInteger("worker.pool.size", 5);

    private static final Vertx VERTX = Vertx.vertx(new VertxOptions()
            .setWorkerPoolSize(WORKER_POOL_SIZE)
            .setEventLoopPoolSize(EVENTLOOP_POOL_SIZE));

    /**
     * Returns the shared Vertx instance for global use.
     *
     * @return Vertx instance
     */
    public static Vertx getVertx()
    {
        return VERTX;
    }

    /**
     * BootStrap method to start the application.
     * Executes Flyway DB migration and then deploys Vert.x verticles in sequence.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args)
    {
        runFlywayMigrations()
                .onComplete(asyncResult ->
                {
                    if (asyncResult.succeeded())
                    {
                        LOGGER.info("Database migration complete.");

                        deployVerticles();
                    }
                    else
                    {
                        LOGGER.error("Database migration failed: {}", asyncResult.cause().getMessage());
                    }
                });
    }

    /**
     * Runs Flyway database migrations using hardcoded PostgreSQL credentials.
     *
     * @return a Future indicating success or failure of migration
     */
    private static Future<Void> runFlywayMigrations()
    {
        var promise = Promise.<Void>promise();

        try
        {
            Flyway flyway = Flyway.configure()
                    .dataSource("jdbc:postgresql://localhost:5432/nms_lite"
                            ,DATABASE_USER
                            ,DATABASE_PASSWORD)
                    .baselineOnMigrate(Constants.TRUE)
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

            promise.fail(exception);
        }

        return promise.future();
    }

    /**
     * Deploys application verticles sequentially.
     * Logs deployment success or failure for each verticle.
     */
    private static void deployVerticles()
    {
        var deploymentList = Arrays.asList(
                new VerticleConfig(Database.class, new DeploymentOptions()
                        .setInstances(DATABASE_VERTICLE_INSTANCES)),
                new VerticleConfig(Server.class, new DeploymentOptions()
                        .setInstances(SERVER_VERTICLE_INSTANCES)),
                new VerticleConfig(AvailabilityPollingEngine.class, new DeploymentOptions()
                        .setInstances(AVAILABILITY_POLLING_ENGINE_INSTANCES)),
                new VerticleConfig(PollingProcessorEngine.class, new DeploymentOptions()
                        .setInstances(POLLING_PROCESSOR_INSTANCES)),
                new VerticleConfig(MetricPollingVerticle.class, new DeploymentOptions()
                        .setInstances(METRIC_POLLING_ENGINE_INSTANCES)),
                new VerticleConfig(PollerEngine.class, new DeploymentOptions()
                        .setInstances(POLLER_ENGINE_INSTANCES)),
                new VerticleConfig(DiscoveryEngine.class, new DeploymentOptions()
                        .setInstances(DISCOVERY_ENGINE_INSTANCES))
        );

        var deployedVerticlesIds = new ArrayList<String>(deploymentList.size());

        var deploymentSequence = Future.<Void>succeededFuture();

        for (var verticle : deploymentList)
        {
            deploymentSequence = deploymentSequence.compose(result -> VERTX.deployVerticle(verticle.verticleClass().getName(), verticle.options())
                    .onSuccess(id -> {
                        deployedVerticlesIds.add(id);

                        LOGGER.info("{} started successfully",
                                verticle.verticleClass().getSimpleName());
                    }).mapEmpty());
        }

        deploymentSequence
                .onSuccess(res -> LOGGER.info("All verticles started successfully in sequence"))
                .onFailure(err ->
                {
                    LOGGER.error("Deployment failed at {}", err.getMessage());

                    // Undeploy already-deployed verticles
                    var undeployChain = Future.<Void>succeededFuture();

                    for (var i = deployedVerticlesIds.size() - 1; i >= 0; i--)
                    {
                        var id = deployedVerticlesIds.get(i);

                        undeployChain = undeployChain.compose(v ->
                                VERTX.undeploy(id)
                                        .onSuccess(x -> LOGGER.info("Undeployed verticle: {}", id))
                                        .onFailure(e -> LOGGER.warn("Failed to undeploy verticle {}: {}", id, e.getMessage()))
                                        .mapEmpty()
                        );
                    }

                    undeployChain
                            .onComplete(done -> {
                                LOGGER.info("All verticles undeployed. Now shutting down Vert.x...");
                                VERTX.close()
                                        .onSuccess(v -> LOGGER.info("Vert.x closed successfully."))
                                        .onFailure(e -> LOGGER.error("Error while closing Vert.x: {}", e.getMessage()));
                            });

                });
    }
}
