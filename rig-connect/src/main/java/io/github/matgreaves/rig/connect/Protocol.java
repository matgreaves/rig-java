package io.github.matgreaves.rig.connect;

/**
 * Identifies the application-layer protocol an endpoint speaks.
 */
public enum Protocol {
    TCP("tcp"),
    HTTP("http"),
    GRPC("grpc"),
    KAFKA("kafka");

    private final String wire;

    Protocol(String wire) {
        this.wire = wire;
    }

    /** Returns the wire-format string (e.g. "tcp", "http", "grpc"). */
    public String wire() {
        return wire;
    }

    /** Parses a wire-format string into a Protocol. */
    public static Protocol fromWire(String s) {
        for (Protocol p : values()) {
            if (p.wire.equals(s)) {
                return p;
            }
        }
        throw new IllegalArgumentException("unknown protocol: " + s);
    }
}
