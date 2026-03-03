package io.github.matgreaves.rig.client;

import io.github.matgreaves.rig.client.internal.WireEvent;
import io.github.matgreaves.rig.connect.Protocol;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EventDispatcherTest {

    @Test
    void wireToEndpointParsesIpv4() {
        var wire = new WireEvent.WireEndpoint();
        wire.hostport = "127.0.0.1:5432";
        wire.protocol = "tcp";

        var ep = EventDispatcher.wireToEndpoint(wire);
        assertEquals("127.0.0.1", ep.host());
        assertEquals(5432, ep.port());
        assertEquals(Protocol.TCP, ep.protocol());
    }

    @Test
    void wireToEndpointParsesWithAttributes() {
        var wire = new WireEvent.WireEndpoint();
        wire.hostport = "10.0.0.1:6379";
        wire.protocol = "tcp";
        wire.attributes = Map.of("REDIS_URL", "redis://10.0.0.1:6379");

        var ep = EventDispatcher.wireToEndpoint(wire);
        assertEquals("10.0.0.1", ep.host());
        assertEquals(6379, ep.port());
        assertEquals("redis://10.0.0.1:6379", ep.attr("REDIS_URL"));
    }

    @Test
    void wireToEndpointHandlesEmptyHostport() {
        var wire = new WireEvent.WireEndpoint();
        wire.hostport = "";
        wire.protocol = "http";

        var ep = EventDispatcher.wireToEndpoint(wire);
        assertEquals("", ep.host());
        assertEquals(0, ep.port());
    }

    @Test
    void wireToEndpointHandlesNullHostport() {
        var wire = new WireEvent.WireEndpoint();
        wire.protocol = "http";

        var ep = EventDispatcher.wireToEndpoint(wire);
        assertEquals("", ep.host());
        assertEquals(0, ep.port());
        assertEquals(Protocol.HTTP, ep.protocol());
    }

    @Test
    void wireToEndpointHandlesNullProtocol() {
        var wire = new WireEvent.WireEndpoint();
        wire.hostport = "127.0.0.1:8080";

        var ep = EventDispatcher.wireToEndpoint(wire);
        assertEquals("127.0.0.1", ep.host());
        assertEquals(8080, ep.port());
        assertNull(ep.protocol());
    }
}
