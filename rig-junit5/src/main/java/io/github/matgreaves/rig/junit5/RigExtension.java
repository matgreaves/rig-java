package io.github.matgreaves.rig.junit5;

import io.github.matgreaves.rig.client.*;
import io.github.matgreaves.rig.connect.Endpoint;

import org.junit.jupiter.api.extension.*;

import java.time.Duration;
import java.util.*;

/**
 * JUnit 5 extension that manages a rig environment for the test class.
 * The environment starts once before all tests and tears down after all
 * tests complete. Test results (pass/fail) are posted to rigd.
 *
 * <pre>{@code
 * class MyTest {
 *     @RegisterExtension
 *     static RigExtension rig = RigExtension.create("my-test")
 *         .service("db", Rig.postgres())
 *         .service("api", Rig.process("java -jar api.jar").egress("db"))
 *         .timeout(Duration.ofSeconds(60));
 *
 *     @Test
 *     void healthCheck() {
 *         var ep = rig.endpoint("api");
 *         // ...
 *     }
 * }
 * }</pre>
 */
public final class RigExtension
        implements BeforeAllCallback, AfterAllCallback, TestWatcher, ParameterResolver {

    private final String name;
    private final Map<String, ServiceDef> services;
    private final List<RigOption> options;
    private Environment env;

    private RigExtension(String name, Map<String, ServiceDef> services, List<RigOption> options) {
        this.name = name;
        this.services = services;
        this.options = options;
    }

    /** Creates a new builder for the given environment name. */
    public static Builder create(String name) {
        return new Builder(name);
    }

    // --- Lifecycle ---

    @Override
    public void beforeAll(ExtensionContext context) {
        env = Rig.up(name, services, options.toArray(RigOption[]::new));
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (env != null) {
            env.close();
            env = null;
        }
    }

    // --- TestWatcher: post results to rigd ---

    @Override
    public void testSuccessful(ExtensionContext context) {
        if (env != null) {
            env.postNote("PASS: " + context.getDisplayName());
        }
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        if (env != null) {
            env.postNote("FAIL: " + context.getDisplayName() + " - " + cause.getMessage());
        }
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        if (env != null) {
            env.postNote("ABORT: " + context.getDisplayName() + " - " + cause.getMessage());
        }
    }

    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        if (env != null) {
            env.postNote("SKIP: " + context.getDisplayName()
                    + reason.map(r -> " - " + r).orElse(""));
        }
    }

    // --- ParameterResolver: inject Environment or Endpoint ---

    @Override
    public boolean supportsParameter(ParameterContext paramCtx,
                                     ExtensionContext extCtx) {
        var type = paramCtx.getParameter().getType();
        return type == Environment.class || type == Endpoint.class;
    }

    @Override
    public Object resolveParameter(ParameterContext paramCtx,
                                   ExtensionContext extCtx) {
        var type = paramCtx.getParameter().getType();
        if (type == Environment.class) {
            return env;
        }
        if (type == Endpoint.class) {
            // Use the parameter name as the service name.
            var name = paramCtx.getParameter().getName();
            return env.endpoint(name);
        }
        throw new ParameterResolutionException(
                "Unsupported parameter type: " + type);
    }

    // --- Public accessors ---

    /** Returns the resolved environment. Only valid after setup. */
    public Environment environment() {
        if (env == null) {
            throw new IllegalStateException("rig environment not started");
        }
        return env;
    }

    /** Shorthand for {@code environment().endpoint(service)}. */
    public Endpoint endpoint(String service, String... ingress) {
        return environment().endpoint(service, ingress);
    }

    /** Shorthand for {@code environment().services()}. */
    public Map<String, ResolvedService> services() {
        return environment().services();
    }

    // --- Builder ---

    public static final class Builder {
        private final String name;
        private final Map<String, ServiceDef> services = new LinkedHashMap<>();
        private final List<RigOption> options = new ArrayList<>();

        Builder(String name) {
            this.name = name;
        }

        /** Adds a service to the environment. */
        public Builder service(String name, ServiceDef def) {
            services.put(name, def);
            return this;
        }

        /** Sets the startup timeout. */
        public Builder timeout(Duration duration) {
            options.add(new RigOption.WithTimeout(duration));
            return this;
        }

        /** Sets the rigd server URL. */
        public Builder server(String url) {
            options.add(new RigOption.WithServer(url));
            return this;
        }

        /** Disables transparent traffic proxying. */
        public Builder withoutObserve() {
            options.add(new RigOption.WithoutObserve());
            return this;
        }

        /** Builds the extension. Also usable directly — the builder IS the extension. */
        public RigExtension build() {
            return new RigExtension(name, Map.copyOf(services), List.copyOf(options));
        }
    }
}
