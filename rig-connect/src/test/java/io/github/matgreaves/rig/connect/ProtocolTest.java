package io.github.matgreaves.rig.connect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {

    @Test
    void wireRoundTrips() {
        for (Protocol p : Protocol.values()) {
            assertEquals(p, Protocol.fromWire(p.wire()));
        }
    }

    @Test
    void fromWireThrowsOnUnknown() {
        assertThrows(IllegalArgumentException.class, () -> Protocol.fromWire("unknown"));
    }

    @Test
    void wireValues() {
        assertEquals("tcp", Protocol.TCP.wire());
        assertEquals("http", Protocol.HTTP.wire());
        assertEquals("grpc", Protocol.GRPC.wire());
    }
}
