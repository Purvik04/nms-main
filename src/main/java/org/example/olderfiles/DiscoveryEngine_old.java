package org.example.olderfiles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.service.database.DatabaseService;
import org.example.service.database.Database;
import org.example.utils.Constants;
import org.example.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

public class DiscoveryEngine_old extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DiscoveryEngine_old.class);

    private static final String STEP = "step";

    private static final String FAILURE_REASON = "failure_reason";

    private static final DatabaseService DATABASE_SERVICE = DatabaseService.createProxy(Database.DB_SERVICE_ADDRESS);

    @Override
    public void start(Promise<Void> startPromise)
    {
        // Initialize the discovery service
        vertx.eventBus().localConsumer(Constants.DISCOVERY_ADDRESS, this::handleDiscoveryRequest);

        startPromise.complete();
    }

    private void handleDiscoveryRequest(Message<JsonArray> discoveryRequest)
    {
        var fetchQuery = "SELECT dp.id, dp.ip, dp.port, cp.credentials, FROM discovery_profiles dp " +
                "JOIN credential_profiles cp ON dp.credential_profile_id = cp.id " +
                "WHERE dp.id IN (" + Utils.buildPlaceholders(discoveryRequest.body().size()) + ")";

        var request = new JsonObject()
                .put(Constants.QUERY, fetchQuery)
                .put(Constants.PARAMS, discoveryRequest.body());

        DATABASE_SERVICE
                .executeQuery(request)
                .onSuccess(asyncResult ->
                {
                    LOGGER.info(asyncResult.toString());

                    vertx.executeBlocking(() ->
                    {
                        var responseArray = new JsonArray();

                        if (asyncResult.getJsonArray(Constants.RESPONSE).isEmpty())
                        {
                            LOGGER.info("No discovery profiles found for IDs: {}",discoveryRequest.body());

                            return responseArray;
                        }

                        var deviceData = asyncResult.getJsonArray(Constants.RESPONSE);

                        var idToDeviceMap = new HashMap<Integer, JsonObject>();

                        LOGGER.info("Processing fping of : {}", deviceData);

                        for (var i = 0; i < deviceData.size(); i++)
                        {
                            responseArray.add(new JsonObject()
                                    .put(Constants.ID, deviceData.getJsonObject(i).getInteger(Constants.ID))
                                    .put(Constants.SUCCESS, Constants.FALSE)
                                    .put(FAILURE_REASON, "ping failed"));

                            idToDeviceMap.put(deviceData.getJsonObject(i).getInteger(Constants.ID),
                                    deviceData.getJsonObject(i));
                        }

                        var pingResult = Utils.ping(deviceData);

                        if (pingResult.isEmpty())
                        {
                            LOGGER.error("ping processing failed");

                            return responseArray;
                        }

                        var sshFilteredDevices = new JsonArray();

                        for(var i = 0; i < pingResult.size(); i++)
                        {
                            if (pingResult.getJsonObject(i).getString(Constants.STATUS, Constants.DOWN)
                                    .equalsIgnoreCase(Constants.UP))
                            {
                                sshFilteredDevices.add(idToDeviceMap.get(pingResult.getJsonObject(i)
                                        .getInteger(Constants.ID)));
                            }
                        }

                        LOGGER.info("Processing SSH discovery of : {}", sshFilteredDevices);

                        var pluginOutput = Utils.spawnGoPlugin(sshFilteredDevices, Constants.DISCOVERY);

                        if (pluginOutput.isEmpty())
                        {
                            LOGGER.error("Go plugin execution failed");

                            return responseArray;
                        }

                        LOGGER.info("Discovery Process Completed");

                        var pluginOutPutMap = new HashMap<Integer, JsonObject>();

                        for (var i = 0; i < pluginOutput.size(); i++)
                        {
                            pluginOutPutMap.put(pluginOutput.getJsonObject(i).getInteger(Constants.ID),
                                    pluginOutput.getJsonObject(i));
                        }

                        var batchParams = new JsonArray();

                        // Update the response array in-place
                        for (var i = 0; i < responseArray.size(); i++)
                        {
                            if (pluginOutPutMap.containsKey(responseArray.getJsonObject(i).getInteger(Constants.ID)))
                            {
                                var pluginResult = pluginOutPutMap.get(responseArray.getJsonObject(i)
                                        .getInteger(Constants.ID));

                                responseArray.getJsonObject(i).put(Constants.SUCCESS,
                                        pluginResult.getBoolean(Constants.SUCCESS));
                                responseArray.getJsonObject(i).put(FAILURE_REASON, pluginResult.getString(STEP));
                            }

                            batchParams.add(new JsonArray()
                                    .add(responseArray.getJsonObject(i).getBoolean(Constants.SUCCESS))
                                    .add(responseArray.getJsonObject(i).getInteger(Constants.ID)));
                        }

                        // Prepare batch update
                        var updateRequest = new JsonObject()
                                .put(Constants.QUERY, "UPDATE discovery_profiles SET status = $1 WHERE id = $2")
                                .put(Constants.PARAMS, batchParams);

                        DATABASE_SERVICE.executeQuery(updateRequest);

                        return responseArray;
                    }, false, result ->
                    {
                        if (result.failed())
                        {
                            discoveryRequest.fail(500 , result.cause().getMessage());
                        }
                        else
                        {
                            discoveryRequest.reply(result.result());
                        }
                    });

                }).onFailure(error ->
                {
                    LOGGER.error("Database query failed: {}", error.getMessage());

                    discoveryRequest.fail(500, error.getMessage());
                });
    }
}

