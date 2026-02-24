package io.github.matgreaves.rig.connect;

import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EndpointTest {

    @Test
    void addrReturnsHostPort() {
        var ep = new Endpoint("127.0.0.1", 5432, Protocol.TCP, null);
        assertEquals("127.0.0.1:5432", ep.addr());
    }

    @Test
    void attrReturnsStringValue() {
        var ep = new Endpoint("localhost", 80, Protocol.HTTP,
                Map.of("key", "value"));
        assertEquals("value", ep.attr("key"));
    }

    @Test
    void attrReturnsEmptyForMissing() {
        var ep = new Endpoint("localhost", 80, Protocol.HTTP, null);
        assertEquals("", ep.attr("missing"));
    }

    @Test
    void attrConvertsNonStringToString() {
        var ep = new Endpoint("localhost", 80, Protocol.HTTP,
                Map.of("port", 5432));
        assertEquals("5432", ep.attr("port"));
    }

    @Test
    void nullAttributesBecomeEmptyMap() {
        var ep = new Endpoint("localhost", 80, Protocol.HTTP, null);
        assertNotNull(ep.attributes());
        assertTrue(ep.attributes().isEmpty());
    }
}
