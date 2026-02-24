package io.github.matgreaves.rig.connect;

import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AttrsTest {

    @Test
    void postgresDsnBuildsCorrectUrl() {
        var ep = new Endpoint("127.0.0.1", 5432, Protocol.TCP, Map.of(
                "PGHOST", "127.0.0.1",
                "PGPORT", "5432",
                "PGUSER", "postgres",
                "PGPASSWORD", "secret",
                "PGDATABASE", "testdb"
        ));
        assertEquals(
                "postgres://postgres:secret@127.0.0.1:5432/testdb?sslmode=disable",
                Attrs.postgresDsn(ep)
        );
    }

    @Test
    void postgresDsnHandlesMissingAttributes() {
        var ep = new Endpoint("localhost", 5432, Protocol.TCP, Map.of());
        String dsn = Attrs.postgresDsn(ep);
        assertEquals("postgres://:@:/?sslmode=disable", dsn);
    }

    @Test
    void wellKnownConstantsExist() {
        assertNotNull(Attrs.PG_HOST);
        assertNotNull(Attrs.PG_PORT);
        assertNotNull(Attrs.PG_USER);
        assertNotNull(Attrs.PG_PASSWORD);
        assertNotNull(Attrs.PG_DATABASE);
        assertNotNull(Attrs.TEMPORAL_ADDRESS);
        assertNotNull(Attrs.TEMPORAL_NAMESPACE);
        assertNotNull(Attrs.REDIS_URL);
        assertNotNull(Attrs.SECURE);
    }
}
