package org.example.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.cache.AvailabilityCacheEngine;
import org.example.utils.Constants;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PollingSchedulerVerticle extends AbstractVerticle
{
    private final Map<Integer, Long> deviceTimerMetricMap = new ConcurrentHashMap<>();

    private final Map<Integer, Long> deviceTimerAvailabilityMap = new ConcurrentHashMap<>();

    private static final long METRIC_POLLING_INTERVAL_MILLIS = 5* 60 * 1000L; //5 minutes

    private static final long AVL_POLLING_INTERVAL_MILLIS =  30 * 1000L; //2 minutes

    private static final long CHECK_INTERVAL = 10_000; // 10 seconds

    private static final JsonArray reUsableForAvailability = new JsonArray();

    private static final JsonArray reUsableForMetric = new JsonArray();

    @Override
    public void start(Promise<Void> startPromise)
    {
        vertx.setPeriodic(CHECK_INTERVAL, timerId ->
        {
            var now = System.currentTimeMillis();

            reUsableForAvailability.clear();

            reUsableForMetric.clear();

            var devicesIds = AvailabilityCacheEngine.getAllDeviceIds();

            devicesIds.forEach(deviceId ->
            {
                if(now - deviceTimerMetricMap.getOrDefault(deviceId, 0L) >= METRIC_POLLING_INTERVAL_MILLIS)
                {
                    deviceTimerMetricMap.put(deviceId, now);

                    reUsableForMetric.add(deviceId);
                }

                if(now - deviceTimerAvailabilityMap.getOrDefault(deviceId, 0L) >= AVL_POLLING_INTERVAL_MILLIS)
                {
                    deviceTimerAvailabilityMap.put(deviceId, now);

                    reUsableForAvailability.add(deviceId);
                }
            });

            if (!reUsableForAvailability.isEmpty())
            {
                vertx.eventBus().send(Constants.EVENTBUS_AVAILABILITY_POLLING_ADDRESS, reUsableForAvailability);
            }
            if(!reUsableForMetric.isEmpty())
            {
                vertx.eventBus().send(Constants.EVENTBUS_METRIC_POLLING_ADDRESS, reUsableForMetric);
            }
        });

        startPromise.complete();
    }
}

