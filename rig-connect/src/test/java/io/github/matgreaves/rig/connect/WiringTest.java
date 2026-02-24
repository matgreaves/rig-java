package io.github.matgreaves.rig.connect;

import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WiringTest {

    @Test
    void ingressReturnsDefaultWhenNoNameProvided() {
        var ep = new Endpoint("127.0.0.1", 8080, Protocol.HTTP, null);
        var w = new Wiring(Map.of("default", ep), null, null, null, null);
        assertEquals(ep, w.ingress());
    }

    @Test
    void ingressReturnsNamedEndpoint() {
        var ep = new Endpoint("127.0.0.1", 9090, Protocol.GRPC, null);
        var w = new Wiring(Map.of("grpc", ep), null, null, null, null);
        assertEquals(ep, w.ingress("grpc"));
    }

    @Test
    void ingressThrowsOnMissing() {
        var w = new Wiring(Map.of("default",
                new Endpoint("localhost", 80, Protocol.HTTP, null)),
                null, null, null, null);
        var ex = assertThrows(IllegalStateException.class, () -> w.ingress("missing"));
        assertTrue(ex.getMessage().contains("missing"));
        assertTrue(ex.getMessage().contains("default"));
    }

    @Test
    void egressReturnsNamedEndpoint() {
        var ep = new Endpoint("db", 5432, Protocol.TCP, null);
        var w = new Wiring(null, Map.of("postgres", ep), null, null, null);
        assertEquals(ep, w.egress("postgres"));
    }

    @Test
    void egressThrowsOnMissing() {
        var w = new Wiring(null, Map.of("redis",
                new Endpoint("redis", 6379, Protocol.TCP, null)),
                null, null, null);
        var ex = assertThrows(IllegalStateException.class, () -> w.egress("missing"));
        assertTrue(ex.getMessage().contains("missing"));
        assertTrue(ex.getMessage().contains("redis"));
    }
}
