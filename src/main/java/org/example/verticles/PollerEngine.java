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

/**
 * PollerEngine is responsible for deciding *when* each device should be polled for:
 * 1. Availability (every 2 minutes)
 * 2. Metrics (every 5 minutes)
 * It maintains per-device last-poll timestamps to ensure appropriate scheduling.
 */
public class PollerEngine extends AbstractVerticle
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PollerEngine.class);

    // Time interval constants (in milliseconds)
    private static final long METRIC_POLLING_INTERVAL_MILLIS = 5 * 60 * 1000L; // 5 minutes
    private static final long AVL_POLLING_INTERVAL_MILLIS = 2 * 60 * 1000L; // 2 minutes
    private static final long SCHEDULER_INTERVAL = 10_000; // 10 seconds
    private static final long DEFAULT_TIMESTAMP = 0;

    // Reusable JsonArrays for each polling type (cleared on each cycle)
    private static final JsonArray AVAILABILITY_POLLING_DEVICE_IDS = new JsonArray();
    private static final JsonArray METRIC_POLLING_DEVICE_IDS = new JsonArray();

    // Maps to track the last time each device was polled
    private final Map<Integer, Long> deviceTimerMetricMap = new HashMap<>();
    private final Map<Integer, Long> deviceTimerAvailabilityMap = new HashMap<>();

    // ID of the periodic timer for cancellation
    private long schedulerTimerId = -1;

    /**
     * Starts the polling scheduler loop, which runs every 10 seconds.
     * It evaluates which devices are due for availability or metric polling.
     */
    @Override
    public void start(Promise<Void> startPromise)
    {
        try
        {
            schedulerTimerId = vertx.setPeriodic(SCHEDULER_INTERVAL, timerId ->
            {
                try
                {
                    var currentTime = System.currentTimeMillis();

                    // Clear reusable JSON arrays before building fresh polling sets
                    AVAILABILITY_POLLING_DEVICE_IDS.clear();

                    METRIC_POLLING_DEVICE_IDS.clear();

                    // Retrieve all registered device IDs from cache
                    var devicesIds = AvailabilityCacheEngine.getAllDeviceIds();

                    if (!devicesIds.isEmpty())
                    {
                        devicesIds.forEach(deviceId ->
                        {
                            // Check if it's time to perform metric polling for this device
                            if (currentTime - deviceTimerMetricMap.getOrDefault(deviceId, DEFAULT_TIMESTAMP)
                                    >= METRIC_POLLING_INTERVAL_MILLIS)
                            {
                                deviceTimerMetricMap.put(deviceId, currentTime);

                                METRIC_POLLING_DEVICE_IDS.add(deviceId);
                            }

                            // Check if it's time to perform availability polling for this device
                            if (currentTime - deviceTimerAvailabilityMap.getOrDefault(deviceId, DEFAULT_TIMESTAMP)
                                    >= AVL_POLLING_INTERVAL_MILLIS)
                            {
                                deviceTimerAvailabilityMap.put(deviceId, currentTime);

                                AVAILABILITY_POLLING_DEVICE_IDS.add(deviceId);
                            }
                        });
                    }

                    // Dispatch polling requests via EventBus if any devices are ready
                    if (!AVAILABILITY_POLLING_DEVICE_IDS.isEmpty())
                    {
                        vertx.eventBus().send(Constants.AVAILABILITY_POLLING_ADDRESS,
                                AVAILABILITY_POLLING_DEVICE_IDS.copy());
                    }

                    if (!METRIC_POLLING_DEVICE_IDS.isEmpty())
                    {
                        vertx.eventBus().send(Constants.METRIC_POLLING_ADDRESS,
                                METRIC_POLLING_DEVICE_IDS.copy());
                    }
                }
                catch (Exception exception)
                {
                    LOGGER.error("Error in Polling Scheduler: {}", exception.getMessage());
                }
            });

            startPromise.complete();
        }
        catch (Exception exception)
        {
            LOGGER.error("Error in deploying poller engine: {}", exception.getMessage());

            startPromise.fail(exception);
        }
    }

    /**
     * Cleans up the timer and state when the verticle is undeployed.
     */
    @Override
    public void stop()
    {
        if (schedulerTimerId != -1)
        {
            vertx.cancelTimer(schedulerTimerId);

            LOGGER.info("Cancelled PollerEngine timer");
        }

        deviceTimerMetricMap.clear();
        deviceTimerAvailabilityMap.clear();
        AVAILABILITY_POLLING_DEVICE_IDS.clear();
        METRIC_POLLING_DEVICE_IDS.clear();
    }
}
