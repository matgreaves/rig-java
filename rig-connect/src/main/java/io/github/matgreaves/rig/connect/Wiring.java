package io.github.matgreaves.rig.connect;

import java.util.Map;
import java.util.TreeMap;

/**
 * Provides resolved endpoint information to services and hook functions.
 *
 * @param ingresses  named ingress endpoints (own endpoints)
 * @param egresses   named egress endpoints (dependency endpoints)
 * @param attributes service-level string attributes
 * @param tempDir    scratch directory (cleaned after environment teardown)
 * @param envDir     persistent environment directory
 */
public record Wiring(
        Map<String, Endpoint> ingresses,
        Map<String, Endpoint> egresses,
        Map<String, String> attributes,
        String tempDir,
        String envDir
) {
    public Wiring {
        ingresses = ingresses == null ? Map.of() : Map.copyOf(ingresses);
        egresses = egresses == null ? Map.of() : Map.copyOf(egresses);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        tempDir = tempDir == null ? "" : tempDir;
        envDir = envDir == null ? "" : envDir;
    }

    /**
     * Returns the named ingress endpoint. If no name is provided,
     * "default" is used.
     *
     * @throws IllegalStateException if the ingress is not found
     */
    public Endpoint ingress(String... name) {
        String n = name.length > 0 ? name[0] : "default";
        Endpoint ep = ingresses.get(n);
        if (ep == null) {
            throw new IllegalStateException(
                    "rig: ingress \"%s\" not found in wiring (available: %s)"
                            .formatted(n, sortedKeys(ingresses)));
        }
        return ep;
    }

    /**
     * Returns the named egress endpoint.
     *
     * @throws IllegalStateException if the egress is not found
     */
    public Endpoint egress(String name) {
        Endpoint ep = egresses.get(name);
        if (ep == null) {
            throw new IllegalStateException(
                    "rig: egress \"%s\" not found in wiring (available: %s)"
                            .formatted(name, sortedKeys(egresses)));
        }
        return ep;
    }

    private static String sortedKeys(Map<String, ?> m) {
        return new TreeMap<>(m).keySet().toString();
    }
}
