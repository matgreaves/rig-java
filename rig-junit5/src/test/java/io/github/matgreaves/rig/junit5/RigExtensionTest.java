package io.github.matgreaves.rig.junit5;

import io.github.matgreaves.rig.client.*;
import io.github.matgreaves.rig.connect.Endpoint;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RigExtensionTest {

    @RegisterExtension
    static RigExtension rig = RigExtension.create("junit5-test")
            .service("db", Rig.postgres())
            .timeout(Duration.ofSeconds(60))
            .build();

    @Test
    void environmentIsAvailable() {
        assertNotNull(rig.environment());
        assertNotNull(rig.environment().id());
    }

    @Test
    void endpointShorthand() {
        var ep = rig.endpoint("db");
        assertNotNull(ep);
        assertFalse(ep.attr("PGHOST").isEmpty());
        assertFalse(ep.attr("PGPORT").isEmpty());
    }

    @Test
    void servicesAccessible() {
        assertFalse(rig.services().isEmpty());
        assertTrue(rig.services().containsKey("db"));
    }
}
