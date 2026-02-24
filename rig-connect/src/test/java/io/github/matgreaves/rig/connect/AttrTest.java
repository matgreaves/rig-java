package io.github.matgreaves.rig.connect;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AttrTest {

    @Test
    void getReturnsTypedValue() {
        var attr = Attr.ofString("key");
        var ep = new Endpoint("localhost", 80, Protocol.HTTP,
                Map.of("key", "value"));
        assertEquals("value", attr.get(ep));
    }

    @Test
    void getReturnsNullWhenMissing() {
        var attr = Attr.ofString("missing");
        var ep = new Endpoint("localhost", 80, Protocol.HTTP, Map.of());
        assertNull(attr.get(ep));
    }

    @Test
    void mustGetReturnsValue() {
        var attr = Attr.ofString("key");
        var ep = new Endpoint("localhost", 80, Protocol.HTTP,
                Map.of("key", "value"));
        assertEquals("value", attr.mustGet(ep));
    }

    @Test
    void mustGetThrowsWhenMissing() {
        var attr = Attr.ofString("missing");
        var ep = new Endpoint("localhost", 80, Protocol.HTTP, Map.of());
        assertThrows(IllegalStateException.class, () -> attr.mustGet(ep));
    }

    @Test
    void getThrowsOnTypeMismatch() {
        var attr = Attr.ofBoolean("flag");
        var ep = new Endpoint("localhost", 80, Protocol.HTTP,
                Map.of("flag", "not-a-boolean"));
        assertThrows(ClassCastException.class, () -> attr.get(ep));
    }

    @Test
    void setWritesToMap() {
        var attr = Attr.ofString("key");
        var map = new HashMap<String, Object>();
        attr.set(map, "value");
        assertEquals("value", map.get("key"));
    }

    @Test
    void booleanAttrWorks() {
        var attr = Attr.ofBoolean("secure");
        var ep = new Endpoint("localhost", 443, Protocol.HTTP,
                Map.of("secure", true));
        assertEquals(true, attr.get(ep));
        assertEquals(true, attr.mustGet(ep));
    }
}
