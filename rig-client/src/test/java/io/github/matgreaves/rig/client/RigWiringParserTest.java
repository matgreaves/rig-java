package io.github.matgreaves.rig.client;

import io.github.matgreaves.rig.connect.Protocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RigWiringParserTest {

    @Test
    void parsesJsonWiring() {
        String json = """
                {
                  "ingresses": {
                    "default": {
                      "host": "127.0.0.1",
                      "port": 8080,
                      "protocol": "http",
                      "attributes": {"key": "value"}
                    }
                  },
                  "egresses": {
                    "db": {
                      "host": "127.0.0.1",
                      "port": 5432,
                      "protocol": "tcp"
                    }
                  },
                  "temp_dir": "/tmp/rig",
                  "env_dir": "/tmp/env",
                  "attributes": {"env": "test"}
                }
                """;

        var wiring = RigWiringParser.parseJson(json);

        assertNotNull(wiring.ingresses());
        var defaultIngress = wiring.ingress();
        assertEquals("127.0.0.1", defaultIngress.host());
        assertEquals(8080, defaultIngress.port());
        assertEquals(Protocol.HTTP, defaultIngress.protocol());
        assertEquals("value", defaultIngress.attr("key"));

        var db = wiring.egress("db");
        assertEquals("127.0.0.1", db.host());
        assertEquals(5432, db.port());
        assertEquals(Protocol.TCP, db.protocol());

        assertEquals("/tmp/rig", wiring.tempDir());
        assertEquals("/tmp/env", wiring.envDir());
        assertEquals("test", wiring.attributes().get("env"));
    }

    @Test
    void parsesMinimalWiring() {
        String json = """
                {
                  "ingresses": {
                    "default": {"host": "localhost", "port": 3000, "protocol": "http"}
                  }
                }
                """;

        var wiring = RigWiringParser.parseJson(json);
        assertEquals("localhost", wiring.ingress().host());
        assertEquals(3000, wiring.ingress().port());
    }
}
