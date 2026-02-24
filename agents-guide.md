# Rig Java SDK -- Agent Guide

This guide helps AI coding agents understand and use the rig-java SDK when assisting users with integration tests.

## Quick reference

### Imports

```java
// JUnit 5 extension (recommended)
import io.github.matgreaves.rig.junit5.RigExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

// Core SDK
import io.github.matgreaves.rig.client.*;
import io.github.matgreaves.rig.connect.*;
```

### JUnit 5 pattern

```java
class MyTest {
    @RegisterExtension
    static RigExtension rig = RigExtension.create("env-name")
        .service("name", /* service def */)
        .timeout(Duration.ofSeconds(60))
        .build();

    @Test
    void test() {
        var ep = rig.endpoint("name");
        // ep.host(), ep.port(), ep.addr(), ep.attr("KEY")
    }
}
```

### Manual pattern

```java
try (var env = Rig.up("env-name", Map.of(
    "name", /* service def */
), Rig.withTimeout(Duration.ofSeconds(60)))) {
    var ep = env.endpoint("name");
}
```

## Service definitions

### Postgres

```java
Rig.postgres()                              // postgres:16-alpine
Rig.postgres().image("postgres:15")         // custom image
Rig.postgres().initSql("CREATE TABLE ...")  // init SQL statements
Rig.postgres().initSqlDir("sql/")           // load *.sql files sorted by name
Rig.postgres().exec("psql", "-c", "...")    // exec command in container
```

Endpoint attributes: `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, `PGDATABASE`.

```java
var ep = rig.endpoint("db");
String dsn = Attrs.postgresDsn(ep);  // postgres://user:pass@host:port/db?sslmode=disable
String host = ep.attr("PGHOST");
```

### Temporal

```java
Rig.temporal()                          // default namespace
Rig.temporal().namespace("my-ns")       // custom namespace
Rig.temporal().version("1.5.1")         // specific CLI version
```

Two ingresses: `default` (gRPC) and `ui` (HTTP).

```java
var grpc = rig.endpoint("temporal");            // gRPC endpoint
var ui = rig.endpoint("temporal", "ui");        // web UI
String addr = grpc.attr("TEMPORAL_ADDRESS");
String ns = grpc.attr("TEMPORAL_NAMESPACE");
```

### Container

```java
Rig.container("nginx:alpine").port(80)              // HTTP on port 80
Rig.container("redis:7").port(6379)                 // TCP on port 6379
Rig.container("image").cmd("arg1", "arg2")          // override command
Rig.container("image").env("KEY", "value").port(80) // environment variable
Rig.container("image").exec("redis-cli", "PING")    // init exec hook
```

### Process (pre-built binary)

```java
Rig.process("java -jar app.jar")                   // command to run
Rig.process("/path/to/binary").dir("/work")         // working directory
Rig.process("./app").args("--port", "${PORT}")      // args with var expansion
```

### Go (built by rigd)

```java
Rig.go_("./cmd/api")                               // relative module path
Rig.go_("/abs/path/to/module")                      // absolute path
Rig.go_("./cmd/api").args("--verbose")              // build args
```

### Func (in-process Java function)

```java
Rig.func(() -> {
    var wiring = RigWiring.parseWiring();
    var ep = wiring.ingress();
    // start HTTP server on ep.host():ep.port()
    // block until interrupted (Thread.sleep(Long.MAX_VALUE) or server.join())
})
```

Func services run in virtual threads. Shutdown is via thread interruption. The function should block until interrupted and clean up in a finally block.

### Custom

```java
Rig.custom("my-type", Map.of("key", "value"))      // server-registered type
```

## Wiring services together

Use `.egress()` to declare dependencies between services:

```java
.service("db", Rig.postgres())
.service("cache", Rig.container("redis:7").port(6379))
.service("api", Rig.process("java -jar api.jar")
    .egress("db")          // api can reach db
    .egress("cache"))      // api can reach cache
.service("gateway", Rig.process("java -jar gw.jar")
    .egress("api"))        // gateway can reach api
```

Rig resolves the dependency graph, starts services in the right order, and injects connection details automatically.

### Named ingresses

```java
// Service with multiple ingresses
.service("temporal", Rig.temporal())  // has "default" (gRPC) and "ui" (HTTP)

// Reference a specific ingress
.service("worker", Rig.process("./worker")
    .egress("temporal"))                      // default ingress
    .egressAs("temporal-ui", "temporal", "ui")) // named ingress
```

### Custom ingresses

```java
Rig.process("./app")
    .noIngress()                                        // remove default
    .ingress("grpc", IngressDef.grpc())                 // gRPC ingress
    .ingress("metrics", IngressDef.http())              // HTTP metrics
```

## Hooks

Hooks run at specific points in the service lifecycle.

### Prestart hooks

Run after egresses are resolved, before the service starts. Receive full wiring (ingresses + egresses).

```java
Rig.process("java -jar app.jar")
    .prestartHook(w -> {
        var dbEp = w.egress("db");
        Files.writeString(
            Path.of(w.tempDir(), "config.properties"),
            "db.host=%s\ndb.port=%d\n".formatted(dbEp.host(), dbEp.port())
        );
    })
```

### Init hooks

Run after health checks pass, before the service is marked ready. Receive own ingresses only.

```java
Rig.postgres()
    .initHook(w -> {
        var ep = w.ingress();
        // run migrations, seed data, etc.
    })
```

### SQL and exec hooks

Server-side hooks that don't require client code:

```java
Rig.postgres().initSql("CREATE TABLE users (id INT)", "INSERT INTO users VALUES (1)")
Rig.container("redis:7").port(6379).exec("redis-cli", "SET", "key", "value")
```

## Rig-unaware services

For services that don't use `RigWiring.parseWiring()`, use a prestart hook to write configuration in the service's native format:

```java
// The app -- completely rig-unaware, reads -c <config>
static void myApp(String[] args) throws Exception {
    // parse args, read config file, start server
}

// Prestart hook writes config, run adapter invokes the app
static HookFunction writeConfig() {
    return w -> {
        var ep = w.ingress();
        Files.writeString(Path.of(w.tempDir(), "app.properties"),
            "host=%s\nport=%d\n".formatted(ep.host(), ep.port()));
    };
}

static RigFunction runApp(/* app main reference */) {
    return () -> {
        var w = RigWiring.parseWiring();
        myApp(new String[]{"-c", Path.of(w.tempDir(), "app.properties").toString()});
    };
}

// Wire it up
.service("svc", Rig.func(runApp()).prestartHook(writeConfig()))
```

The prestart hook and the run adapter match by convention (the config file path) rather than shared state.

## Endpoint access

```java
var ep = rig.endpoint("service");           // default ingress
var ep = rig.endpoint("service", "grpc");   // named ingress

ep.host()                    // "127.0.0.1"
ep.port()                    // 8080
ep.addr()                    // "127.0.0.1:8080"
ep.protocol()                // Protocol.HTTP
ep.attr("PGHOST")            // attribute as string, "" if missing
ep.attributes()              // raw Map<String, Object>
```

## Typed attributes

```java
// Well-known attributes
Attrs.PG_HOST.get(ep)                    // String, null if missing
Attrs.PG_HOST.mustGet(ep)                // String, throws if missing
Attrs.SECURE.get(ep)                     // Boolean
Attrs.postgresDsn(ep)                    // full connection string
Attrs.TEMPORAL_ADDRESS.get(ep)           // temporal host:port
Attrs.TEMPORAL_NAMESPACE.get(ep)         // temporal namespace
Attrs.REDIS_URL.get(ep)                  // redis URL
```

## Options

```java
Rig.withTimeout(Duration.ofSeconds(90))  // startup timeout (default: 2 minutes)
Rig.withServer("http://localhost:12345") // rigd URL (default: auto-detect)
Rig.withoutObserve()                     // disable traffic proxying
```

## Common patterns

### Shared base environment

```java
class Env {
    static RigExtension.Builder base() {
        return RigExtension.create("test")
            .service("db", Rig.postgres().initSql("schema.sql"))
            .service("api", Rig.process("java -jar api.jar").egress("db"))
            .timeout(Duration.ofSeconds(60));
    }
}

class OrderTest {
    @RegisterExtension
    static RigExtension rig = Env.base().build();
}

class PaymentTest {
    @RegisterExtension
    static RigExtension rig = Env.base()
        .service("stripe", Rig.container("stripe-mock").port(12111))
        .build();
}
```

### Parallel test classes

Each class gets its own isolated environment. Enable JUnit 5 parallel execution:

```properties
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.classes.default=concurrent
junit.jupiter.execution.parallel.mode.default=concurrent
```

### Func service with HTTP server

```java
Rig.func(() -> {
    var w = RigWiring.parseWiring();
    var ep = w.ingress();
    var server = HttpServer.create(new InetSocketAddress(ep.host(), ep.port()), 0);
    server.createContext("/", exchange -> {
        byte[] body = "ok".getBytes();
        exchange.sendResponseHeaders(200, body.length);
        try (var os = exchange.getResponseBody()) { os.write(body); }
    });
    server.start();
    try {
        Thread.sleep(Long.MAX_VALUE);  // block until interrupted
    } finally {
        server.stop(0);
    }
})
```

## Important details

- **Java 25 required** -- uses ScopedValue (standard in 25), virtual threads, records, sealed interfaces.
- **Shutdown is via thread interruption** -- func services should block on an interruptible call and clean up in finally blocks. Do not swallow `InterruptedException`.
- **rigd is auto-downloaded** -- if not found locally, the SDK downloads the binary from GitHub releases to `~/.rig/bin/`.
- **Proxy-aware** -- all HTTP communication (including binary downloads) respects `HTTP_PROXY`, `HTTPS_PROXY`, and `NO_PROXY` environment variables.
- **`RIG_PRESERVE=true`** -- set this to keep environment artifacts after teardown for debugging.
- **Gson is the only external dependency** -- rig-connect has zero dependencies. rig-client depends on Gson 2.11.
