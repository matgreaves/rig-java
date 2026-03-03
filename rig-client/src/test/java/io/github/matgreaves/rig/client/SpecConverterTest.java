package io.github.matgreaves.rig.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.matgreaves.rig.client.internal.WireTypes.SpecEnvironment;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpecConverterTest {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Test
    void postgresConversion() {
        var registry = new HookRegistry();
        var spec = SpecConverter.toSpec("test",
                Map.of("db", Rig.postgres()),
                registry, true);

        assertEquals("test", spec.name);
        assertTrue(spec.observe);
        assertNotNull(spec.services.get("db"));

        var db = spec.services.get("db");
        assertEquals("postgres", db.type);
        assertNotNull(db.ingresses);
        assertNotNull(db.ingresses.get("default"));
        assertEquals("tcp", db.ingresses.get("default").protocol);
        assertEquals(5432, db.ingresses.get("default").container_port);
    }

    @Test
    void postgresWithImage() {
        var registry = new HookRegistry();
        var spec = SpecConverter.toSpec("test",
                Map.of("db", Rig.postgres().image("postgres:15")),
                registry, true);

        var db = spec.services.get("db");
        String json = gson.toJson(db.config);
        assertTrue(json.contains("postgres:15"));
    }

    @Test
    void goServiceConversion() {
        var registry = new HookRegistry();
        var spec = SpecConverter.toSpec("test",
                Map.of("api", Rig.go_("/abs/path/cmd/api").egress("db").args("--verbose")),
                registry, true);

        var api = spec.services.get("api");
        assertEquals("go", api.type);
        assertNotNull(api.args);
        assertTrue(api.args.contains("--verbose"));
        assertNotNull(api.ingresses.get("default"));
        assertEquals("http", api.ingresses.get("default").protocol);
        assertNotNull(api.egresses.get("db"));
        assertEquals("db", api.egresses.get("db").service);
    }

    @Test
    void containerConversion() {
        var registry = new HookRegistry();
        var spec = SpecConverter.toSpec("test",
                Map.of("nginx", Rig.container("nginx:alpine").port(80).env("FOO", "bar")),
                registry, true);

        var svc = spec.services.get("nginx");
        assertEquals("container", svc.type);
        assertNotNull(svc.ingresses.get("default"));
        assertEquals(80, svc.ingresses.get("default").container_port);
    }

    @Test
    void temporalConversion() {
        var registry = new HookRegistry();
        var spec = SpecConverter.toSpec("test",
                Map.of("temporal", Rig.temporal().namespace("my-ns")),
                registry, true);

        var svc = spec.services.get("temporal");
        assertEquals("temporal", svc.type);
        assertNotNull(svc.ingresses.get("default"));
        assertEquals("grpc", svc.ingresses.get("default").protocol);
        assertNotNull(svc.ingresses.get("ui"));
        assertEquals("http", svc.ingresses.get("ui").protocol);
    }

    @Test
    void funcConversion() {
        var registry = new HookRegistry();
        var spec = SpecConverter.toSpec("test",
                Map.of("svc", Rig.func(() -> Thread.sleep(Long.MAX_VALUE))),
                registry, true);

        var svc = spec.services.get("svc");
        assertEquals("client", svc.type);
        String json = gson.toJson(svc.config);
        assertTrue(json.contains("start_handler"));
    }

    @Test
    void hookConversion() {
        var registry = new HookRegistry();
        var spec = SpecConverter.toSpec("test",
                Map.of("db", Rig.postgres().initSql("CREATE TABLE t (id INT)")),
                registry, true);

        var db = spec.services.get("db");
        assertNotNull(db.hooks);
        assertNotNull(db.hooks.init);
        assertEquals(1, db.hooks.init.size());
        assertEquals("sql", db.hooks.init.getFirst().type);
    }

    @Test
    void clientFuncHookConversion() {
        var registry = new HookRegistry();
        var spec = SpecConverter.toSpec("test",
                Map.of("api", Rig.go_("/abs/path").prestartHook(w -> {})),
                registry, true);

        var api = spec.services.get("api");
        assertNotNull(api.hooks);
        assertNotNull(api.hooks.prestart);
        assertEquals(1, api.hooks.prestart.size());
        assertEquals("client_func", api.hooks.prestart.getFirst().type);
        assertNotNull(api.hooks.prestart.getFirst().client_func);
    }

    @Test
    void observeDisabled() {
        var registry = new HookRegistry();
        var spec = SpecConverter.toSpec("test",
                Map.of("db", Rig.postgres()),
                registry, false);

        assertFalse(spec.observe);
    }

    @Test
    void customServiceConversion() {
        var registry = new HookRegistry();
        var spec = SpecConverter.toSpec("test",
                Map.of("svc", Rig.custom("my-type", Map.of("key", "value")).args("--flag")),
                registry, true);

        var svc = spec.services.get("svc");
        assertEquals("my-type", svc.type);
        assertNotNull(svc.args);
        assertTrue(svc.args.contains("--flag"));
    }

    @Test
    void redisConversion() {
        var registry = new HookRegistry();
        var spec = SpecConverter.toSpec("test",
                Map.of("cache", Rig.redis()),
                registry, true);

        var svc = spec.services.get("cache");
        assertEquals("redis", svc.type);
        assertNull(svc.config);
        assertNotNull(svc.ingresses.get("default"));
        assertEquals("tcp", svc.ingresses.get("default").protocol);
        assertEquals(6379, svc.ingresses.get("default").container_port);
    }

    @Test
    void redisWithImage() {
        var registry = new HookRegistry();
        var spec = SpecConverter.toSpec("test",
                Map.of("cache", Rig.redis().image("redis:7-alpine")),
                registry, true);

        var svc = spec.services.get("cache");
        String json = gson.toJson(svc.config);
        assertTrue(json.contains("redis:7-alpine"));
    }

    @Test
    void s3Conversion() {
        var registry = new HookRegistry();
        var spec = SpecConverter.toSpec("test",
                Map.of("store", Rig.s3()),
                registry, true);

        var svc = spec.services.get("store");
        assertEquals("s3", svc.type);
        assertNull(svc.config);
        assertNotNull(svc.ingresses.get("default"));
        assertEquals("tcp", svc.ingresses.get("default").protocol);
        assertEquals(8333, svc.ingresses.get("default").container_port);
    }

    @Test
    void sqsConversion() {
        var registry = new HookRegistry();
        var spec = SpecConverter.toSpec("test",
                Map.of("queue", Rig.sqs()),
                registry, true);

        var svc = spec.services.get("queue");
        assertEquals("sqs", svc.type);
        assertNull(svc.config);
        assertNotNull(svc.ingresses.get("default"));
        assertEquals("tcp", svc.ingresses.get("default").protocol);
        assertEquals(9324, svc.ingresses.get("default").container_port);
    }

    @Test
    void processConversion() {
        var registry = new HookRegistry();
        var spec = SpecConverter.toSpec("test",
                Map.of("svc", Rig.process("/usr/bin/myapp").dir("/work")),
                registry, true);

        var svc = spec.services.get("svc");
        assertEquals("process", svc.type);
        String json = gson.toJson(svc.config);
        assertTrue(json.contains("/usr/bin/myapp"));
        assertTrue(json.contains("/work"));
    }
}
