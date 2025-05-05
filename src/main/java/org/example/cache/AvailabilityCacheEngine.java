package org.example.cache;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class AvailabilityCacheEngine {

    private static final Map<Integer, String> deviceStatusMap = new ConcurrentHashMap<>();

    private AvailabilityCacheEngine() {}

    public static String getDeviceStatus(Integer deviceId) {
        return deviceStatusMap.get(deviceId);
    }

    public static void setDeviceStatus(Integer deviceId, String status) {
        deviceStatusMap.put(deviceId, status);
    }

    public static Map<Integer, String> getAllDeviceStatus() {
        return deviceStatusMap;
    }

    public static Set<Integer> getAllDeviceIds() {
        return deviceStatusMap.keySet();
    }

    public static void removeDevice(Integer deviceId) {
        deviceStatusMap.remove(deviceId);
    }
}
