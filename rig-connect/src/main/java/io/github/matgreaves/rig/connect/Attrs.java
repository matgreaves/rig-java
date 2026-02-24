package io.github.matgreaves.rig.connect;

/**
 * Well-known attribute constants for standard service types.
 */
public final class Attrs {
    private Attrs() {}

    // Postgres
    public static final Attr<String> PG_HOST = Attr.ofString("PGHOST");
    public static final Attr<String> PG_PORT = Attr.ofString("PGPORT");
    public static final Attr<String> PG_USER = Attr.ofString("PGUSER");
    public static final Attr<String> PG_PASSWORD = Attr.ofString("PGPASSWORD");
    public static final Attr<String> PG_DATABASE = Attr.ofString("PGDATABASE");

    // Temporal
    public static final Attr<String> TEMPORAL_ADDRESS = Attr.ofString("TEMPORAL_ADDRESS");
    public static final Attr<String> TEMPORAL_NAMESPACE = Attr.ofString("TEMPORAL_NAMESPACE");

    // Redis
    public static final Attr<String> REDIS_URL = Attr.ofString("REDIS_URL");

    // Cross-cutting
    public static final Attr<Boolean> SECURE = Attr.ofBoolean("SECURE");

    /**
     * Builds a Postgres connection string from endpoint attributes.
     * Uses PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE with sslmode=disable.
     */
    public static String postgresDsn(Endpoint ep) {
        String host = valueOr(PG_HOST.get(ep), "");
        String port = valueOr(PG_PORT.get(ep), "");
        String user = valueOr(PG_USER.get(ep), "");
        String pass = valueOr(PG_PASSWORD.get(ep), "");
        String db = valueOr(PG_DATABASE.get(ep), "");
        return "postgres://%s:%s@%s:%s/%s?sslmode=disable".formatted(user, pass, host, port, db);
    }

    private static String valueOr(String v, String fallback) {
        return v == null ? fallback : v;
    }
}
