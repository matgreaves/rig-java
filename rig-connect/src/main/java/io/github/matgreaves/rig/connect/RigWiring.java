package io.github.matgreaves.rig.connect;

import java.io.OutputStream;

/**
 * Provides {@link ScopedValue}-based dependency injection for rig service wiring
 * and log output. This is the Java equivalent of Go's context-based wiring injection.
 *
 * <p>In-process (Func) services receive their wiring via scoped values set by the
 * rig client SDK. External services read from the {@code RIG_WIRING} environment variable.
 */
public final class RigWiring {
    private RigWiring() {}

    /** Scoped wiring context, set by the rig client SDK for Func services. */
    public static final ScopedValue<Wiring> WIRING = ScopedValue.newInstance();

    /** Scoped log writer, set by the rig client SDK for Func services. */
    public static final ScopedValue<OutputStream> LOG_WRITER = ScopedValue.newInstance();

    /**
     * Reads the service wiring. Checks the scoped value first (set for in-process
     * services), then falls back to the {@code RIG_WIRING} environment variable,
     * then {@code HOST}/{@code PORT}.
     *
     * <p>The RIG_WIRING JSON parsing requires Gson (rig-client module). When called
     * from rig-connect only, this method supports scoped value and HOST/PORT fallback.
     *
     * @return the resolved wiring
     * @throws IllegalStateException if no wiring source is available
     */
    public static Wiring parseWiring() {
        if (WIRING.isBound()) {
            return WIRING.get();
        }

        String raw = System.getenv("RIG_WIRING");
        if (raw != null && !raw.isEmpty()) {
            // Delegate to the rig-client module's parser if available.
            // This method will be supplemented by RigWiringParser in rig-client.
            throw new IllegalStateException(
                    "RIG_WIRING env var set but JSON parsing requires rig-client module");
        }

        // Fallback: construct minimal wiring from HOST/PORT.
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
                java.util.Map.of("default", new Endpoint(host, port, null, null)),
                null, null, null, null
        );
    }

    /**
     * Returns the log writer for service output. When running as a rig Func service,
     * writes are shipped to the rigd event timeline. Otherwise returns {@code System.out}.
     */
    public static OutputStream logWriter() {
        if (LOG_WRITER.isBound()) {
            return LOG_WRITER.get();
        }
        return System.out;
    }
}
