package org.example.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import org.example.cache.AvailabilityCacheEngine;
import org.example.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class PollerEngine extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PollerEngine.class);

    private static final long METRIC_POLLING_INTERVAL_MILLIS = 5 * 60 * 1000L; //5 minutes

    private static final long AVL_POLLING_INTERVAL_MILLIS = 2 * 60 * 1000L; //2 minutes

    private static final long SCHEDULER_INTERVAL = 10_000; // 10 seconds

    private static final JsonArray AVAILABILITY_POLLING_DEVICE_IDS = new JsonArray();

    private static final JsonArray METRIC_POLLING_DEVICE_IDS = new JsonArray();

    private final Map<Integer, Long> deviceTimerMetricMap = new HashMap<>();

    private final Map<Integer, Long> deviceTimerAvailabilityMap = new HashMap<>();

    @Override
    public void start(Promise<Void> startPromise)
    {
        vertx.setPeriodic(SCHEDULER_INTERVAL, timerId ->
        {
            try
            {
                var now = System.currentTimeMillis();

                AVAILABILITY_POLLING_DEVICE_IDS.clear();

                METRIC_POLLING_DEVICE_IDS.clear();

                var devicesIds = AvailabilityCacheEngine.getAllDeviceIds();

                LOGGER.info("Found {} devices", devicesIds);

                if(!devicesIds.isEmpty())
                {
                    devicesIds.forEach(deviceId ->
                    {
                        if(now - deviceTimerMetricMap.getOrDefault(deviceId, 0L) >= METRIC_POLLING_INTERVAL_MILLIS)
                        {
                            deviceTimerMetricMap.put(deviceId, now);

                            METRIC_POLLING_DEVICE_IDS.add(deviceId);
                        }

                        if(now - deviceTimerAvailabilityMap.getOrDefault(deviceId, 0L) >= AVL_POLLING_INTERVAL_MILLIS)
                        {
                            deviceTimerAvailabilityMap.put(deviceId, now);

                            AVAILABILITY_POLLING_DEVICE_IDS.add(deviceId);
                        }
                    });
                }

                if (!AVAILABILITY_POLLING_DEVICE_IDS.isEmpty())
                {
                    vertx.eventBus().send(Constants.EVENTBUS_AVAILABILITY_POLLING_ADDRESS, AVAILABILITY_POLLING_DEVICE_IDS.copy());
                }
                if(!METRIC_POLLING_DEVICE_IDS.isEmpty())
                {
                    vertx.eventBus().send(Constants.EVENTBUS_METRIC_POLLING_ADDRESS, METRIC_POLLING_DEVICE_IDS.copy());
                }
            }
            catch (Exception exception)
            {
                LOGGER.error("Error in Polling Scheduler: {}", exception.getMessage());
            }
        });

        startPromise.complete();
    }
}

