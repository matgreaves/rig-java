package io.github.matgreaves.rig.connect;

import java.util.Map;

/**
 * A resolved service endpoint with connection helpers.
 *
 * @param host       the hostname or IP address
 * @param port       the port number
 * @param protocol   the application-layer protocol
 * @param attributes arbitrary typed attributes (e.g. PG credentials)
 */
public record Endpoint(
        String host,
        int port,
        Protocol protocol,
        Map<String, Object> attributes
) {
    public Endpoint {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** Returns "host:port" suitable for connecting. */
    public String addr() {
        return host + ":" + port;
    }

    /**
     * Returns the value of a named attribute as a string.
     * Returns "" if the attribute is not found.
     */
    public String attr(String name) {
        Object v = attributes.get(name);
        return v == null ? "" : String.valueOf(v);
    }
}
