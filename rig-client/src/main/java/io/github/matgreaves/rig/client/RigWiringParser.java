package io.github.matgreaves.rig.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.matgreaves.rig.connect.*;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses the {@code RIG_WIRING} environment variable JSON using Gson.
 * Supplements {@link RigWiring#parseWiring()} with JSON parsing capability.
 */
final class RigWiringParser {
    private RigWiringParser() {}

    private static final Gson GSON = new Gson();

    /**
     * Reads the service wiring. Checks the scoped value first, then falls back
     * to the {@code RIG_WIRING} environment variable (parsed with Gson), then
     * {@code HOST}/{@code PORT}.
     *
     * @return the resolved wiring
     * @throws IllegalStateException if no wiring source is available
     */
    public static Wiring parseWiring() {
        if (RigWiring.WIRING.isBound()) {
            return RigWiring.WIRING.get();
        }

        String raw = System.getenv("RIG_WIRING");
        if (raw != null && !raw.isEmpty()) {
            return parseJson(raw);
        }

        // Fallback: HOST/PORT
        String host = System.getenv("HOST");
        String portStr = System.getenv("PORT");
        if (host == null || host.isEmpty() || portStr == null || portStr.isEmpty()) {
            throw new IllegalStateException("HOST and PORT must be set (or RIG_WIRING)");
        }
        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("invalid PORT \"%s\"".formatted(portStr), e);
        }
        return new Wiring(
                Map.of("default", new Endpoint(host, port, null, null)),
                null, null, null, null
        );
    }

    static Wiring parseJson(String json) {
        WireWiring w = GSON.fromJson(json, WireWiring.class);
        return new Wiring(
                convertEndpoints(w.ingresses),
                convertEndpoints(w.egresses),
                w.attributes,
                w.temp_dir,
                w.env_dir
        );
    }

    private static Map<String, Endpoint> convertEndpoints(Map<String, WireEndpoint> eps) {
        if (eps == null) return null;
        var out = new LinkedHashMap<String, Endpoint>();
        for (var entry : eps.entrySet()) {
            var ep = entry.getValue();
            Protocol protocol = null;
            if (ep.protocol != null && !ep.protocol.isEmpty()) {
                try { protocol = Protocol.fromWire(ep.protocol); }
                catch (IllegalArgumentException ignored) {}
            }
            out.put(entry.getKey(), new Endpoint(ep.host, ep.port, protocol, ep.attributes));
        }
        return out;
    }

    // DTOs for Gson deserialization
    private static class WireWiring {
        Map<String, WireEndpoint> ingresses;
        Map<String, WireEndpoint> egresses;
        Map<String, String> attributes;
        String temp_dir;
        String env_dir;
    }

    private static class WireEndpoint {
        String host;
        int port;
        String protocol;
        Map<String, Object> attributes;
    }
}
