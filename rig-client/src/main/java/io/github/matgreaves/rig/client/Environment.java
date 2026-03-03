package io.github.matgreaves.rig.client;

import io.github.matgreaves.rig.client.internal.FuncThreads;
import io.github.matgreaves.rig.client.internal.RigHttpClient;
import io.github.matgreaves.rig.connect.Endpoint;

import java.util.*;

/**
 * A resolved, running environment. Implements {@link AutoCloseable} for
 * use with try-with-resources.
 *
 * <pre>{@code
 * try (var env = Rig.up("test", services)) {
 *     var ep = env.endpoint("postgres");
 *     // use endpoint...
 * } // environment is torn down automatically
 * }</pre>
 */
public final class Environment implements AutoCloseable {
    private final String id;
    private final Map<String, ResolvedService> services;
    private final String envDir;
    private final RigHttpClient httpClient;
    private final String serverUrl;
    private final FuncThreads funcThreads;
    private volatile boolean testFailed;

    Environment(
            String id,
            Map<String, ResolvedService> services,
            String envDir,
            RigHttpClient httpClient,
            String serverUrl,
            FuncThreads funcThreads
    ) {
        this.id = id;
        this.services = Map.copyOf(services);
        this.envDir = envDir;
        this.httpClient = httpClient;
        this.serverUrl = serverUrl;
        this.funcThreads = funcThreads;
    }

    /**
     * Returns the ingress endpoint for the named service. If ingress is omitted,
     * the default ingress is returned. If the service has a single ingress,
     * it is returned regardless of its name.
     *
     * @throws IllegalStateException if the service or ingress is not found
     */
    public Endpoint endpoint(String service, String... ingress) {
        ResolvedService svc = services.get(service);
        if (svc == null) {
            throw new IllegalStateException(
                    "rig: service \"%s\" not found in environment (available: %s)"
                            .formatted(service, sortedKeys(services)));
        }

        String ingressName = ingress.length > 0 ? ingress[0] : "default";

        // Single ingress shorthand.
        if ("default".equals(ingressName) && svc.ingresses().size() == 1) {
            return svc.ingresses().values().iterator().next();
        }

        Endpoint ep = svc.ingresses().get(ingressName);
        if (ep == null) {
            throw new IllegalStateException(
                    "rig: ingress \"%s\" not found on service \"%s\" (available: %s)"
                            .formatted(ingressName, service, sortedKeys(svc.ingresses())));
        }
        return ep;
    }

    /** Returns all resolved services. */
    public Map<String, ResolvedService> services() {
        return services;
    }

    /** Returns the server-side environment directory. */
    public String envDir() {
        return envDir;
    }

    /** Returns the environment ID. */
    public String id() {
        return id;
    }

    /**
     * Marks the environment as having a failed test. When closed,
     * this causes {@code reason=test_failed} to be sent to rigd.
     */
    public void markFailed() {
        this.testFailed = true;
    }

    /**
     * Posts a note to the environment's event timeline on rigd.
     * Used by extensions (e.g. JUnit 5) to report test results.
     */
    public void postNote(String message) {
        try {
            String json = "{\"type\":\"test.note\",\"error\":\"%s\"}"
                    .formatted(escape(message));
            String url = "%s/environments/%s/events".formatted(serverUrl, id);
            httpClient.postJson(url, json);
        } catch (Exception ignored) {
            // best-effort — don't fail tests because of event posting
        }
    }

    /**
     * Tears down the environment: signals shutdown to func services,
     * then DELETEs the environment on rigd.
     */
    @Override
    public void close() {
        funcThreads.interruptAll();
        try {
            String url = "%s/environments/%s?log=true".formatted(serverUrl, id);
            if (shouldPreserve()) url += "&preserve=true";
            if (testFailed) url += "&reason=test_failed";
            httpClient.delete(url);
        } catch (Exception ignored) {
            // cleanup must not throw
        }
    }

    private boolean shouldPreserve() {
        if ("true".equals(System.getenv("RIG_PRESERVE"))) return true;
        return testFailed && "true".equals(System.getenv("RIG_PRESERVE_ON_FAILURE"));
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String sortedKeys(Map<String, ?> m) {
        return new TreeMap<>(m).keySet().toString();
    }
}
