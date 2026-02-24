package io.github.matgreaves.rig.client;

import io.github.matgreaves.rig.connect.Endpoint;
import java.util.Map;

/**
 * Holds the resolved endpoints for a single service.
 *
 * @param ingresses named ingress endpoints
 */
public record ResolvedService(Map<String, Endpoint> ingresses) {
    public ResolvedService {
        ingresses = ingresses == null ? Map.of() : Map.copyOf(ingresses);
    }
}
