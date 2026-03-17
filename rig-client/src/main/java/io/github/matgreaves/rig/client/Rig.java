package io.github.matgreaves.rig.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.matgreaves.rig.client.internal.FuncThreads;
import io.github.matgreaves.rig.client.internal.RigHttpClient;
import io.github.matgreaves.rig.client.internal.ServerManager;
import io.github.matgreaves.rig.client.internal.WireTypes;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Static entry point for the rig Java SDK.
 *
 * <pre>{@code
 * try (var env = Rig.up("test", Map.of(
 *         "db", Rig.postgres(),
 *         "api", Rig.go_("./cmd/api").egress("db")
 * ))) {
 *     var ep = env.endpoint("db");
 *     // ...
 * }
 * }</pre>
 */
public final class Rig {
    private Rig() {}

    private static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .create();

    /**
     * Creates an environment, blocks until all services are ready, and returns
     * the resolved Environment. Use try-with-resources to ensure cleanup.
     *
     * @param name     environment name
     * @param services map of service name to definition
     * @param opts     options (server URL, timeout, observe)
     * @return the resolved environment
     * @throws RigException            on failure
     * @throws RigValidationException  on spec validation errors
     * @throws RigTimeoutException     on startup timeout
     */
    public static Environment up(String name, Map<String, ServiceDef> services, RigOption... opts) {
        // Parse options.
        String serverUrl = System.getenv("RIG_SERVER_ADDR");
        Duration startupTimeout = Duration.ofMinutes(2);
        boolean observe = true;
        Duration ttl = null;

        // Default TTL from environment variable.
        String rigTtlEnv = System.getenv("RIG_TTL");
        if (rigTtlEnv != null && !rigTtlEnv.isEmpty()) {
            ttl = WireTypes.parseGoDuration(rigTtlEnv);
        }

        for (RigOption opt : opts) {
            switch (opt) {
                case RigOption.WithServer ws -> serverUrl = ws.url();
                case RigOption.WithTimeout wt -> startupTimeout = wt.duration();
                case RigOption.WithoutObserve ignored -> observe = false;
                case RigOption.WithTTL wt -> ttl = wt.duration();
            }
        }

        // Validate TTL.
        if (ttl != null && (ttl.isZero() || ttl.isNegative())) {
            throw new RigException("rig: TTL must be positive, got: " + ttl);
        }

        String ttlWire = ttl != null ? WireTypes.durationToWire(ttl) : null;
        boolean skipDelete = ttl != null;

        // Ensure server is running.
        if (serverUrl == null || serverUrl.isEmpty()) {
            serverUrl = ServerManager.ensureServer("");
        }
        serverUrl = serverUrl.replaceAll("/+$", "");

        // Convert spec.
        var registry = new HookRegistry();
        var specEnv = SpecConverter.toSpec(name, services, registry, observe, ttlWire);

        // POST /environments
        var httpClient = new RigHttpClient();
        String json = GSON.toJson(specEnv);
        var response = httpClient.postJson(serverUrl + "/environments", json);

        if (response.statusCode() == 422) {
            // Parse validation errors.
            try {
                var result = GSON.fromJson(response.body(),
                        ValidationErrorResponse.class);
                if (result != null && result.validation_errors != null) {
                    throw new RigValidationException(result.validation_errors);
                }
            } catch (RigValidationException e) {
                throw e;
            } catch (Exception ignored) {}
            throw new RigException("rig: spec validation failed: " + response.body());
        }

        if (response.statusCode() != 201) {
            throw new RigException("rig: create environment: HTTP %d: %s"
                    .formatted(response.statusCode(), response.body()));
        }

        // Parse environment ID.
        var created = GSON.fromJson(response.body(), CreateResponse.class);
        String envId = created.id;

        // Track func threads for shutdown.
        var funcThreads = new FuncThreads();

        // Stream SSE events until environment.up.
        var dispatcher = new EventDispatcher(httpClient, serverUrl, envId, registry, funcThreads, skipDelete);
        try {
            return dispatcher.streamUntilReady();
        } catch (Exception e) {
            // Environment was created but streaming failed — send DELETE so rigd
            // writes event logs and cleans up resources.
            funcThreads.interruptAll();
            try {
                httpClient.delete("%s/environments/%s?log=true&reason=test_failed".formatted(serverUrl, envId));
            } catch (Exception ignored) {}
            throw e;
        }
    }

    // --- Service factories ---

    /** Creates a Go service definition with a default HTTP ingress. */
    public static GoDef go_(String module) {
        return new GoDef(module);
    }

    /** Creates a service backed by an in-process function. */
    public static FuncDef func(RigFunction fn) {
        return new FuncDef(fn);
    }

    /** Creates a container service definition with a default HTTP ingress. */
    public static ContainerDef container(String image) {
        return new ContainerDef(image);
    }

    /** Creates a Postgres service definition. */
    public static PostgresDef postgres() {
        return new PostgresDef();
    }

    /** Creates a Redis service definition. */
    public static RedisDef redis() {
        return new RedisDef();
    }

    /** Creates an S3 service definition. */
    public static S3Def s3() {
        return new S3Def();
    }

    /** Creates an SQS service definition. */
    public static SqsDef sqs() {
        return new SqsDef();
    }

    /** Creates a Kafka service definition using Redpanda. */
    public static KafkaDef kafka() {
        return new KafkaDef();
    }

    /** Creates a Temporal service definition. */
    public static TemporalDef temporal() {
        return new TemporalDef();
    }

    /** Creates a process service definition with a default HTTP ingress. */
    public static ProcessDef process(String command) {
        return new ProcessDef(command);
    }

    /** Creates a custom service definition for any server-registered type. */
    public static CustomDef custom(String type, Map<String, Object> config) {
        return new CustomDef(type, config);
    }

    // --- Option factories ---

    /** Sets the rigd server base URL. */
    public static RigOption withServer(String url) {
        return new RigOption.WithServer(url);
    }

    /** Sets the startup timeout. */
    public static RigOption withTimeout(Duration duration) {
        return new RigOption.WithTimeout(duration);
    }

    /** Disables transparent traffic proxying. */
    public static RigOption withoutObserve() {
        return new RigOption.WithoutObserve();
    }

    /** Sets the environment TTL. When set, the server-side timer handles teardown. */
    public static RigOption withTTL(Duration duration) {
        return new RigOption.WithTTL(duration);
    }

    // --- Internal DTOs ---

    private static class CreateResponse {
        String id;
    }

    private static class ValidationErrorResponse {
        List<String> validation_errors;
    }
}
