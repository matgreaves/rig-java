# rig-java

Java SDK for the [rig](https://github.com/matgreaves/rig) test environment orchestrator.

> **Beta** -- this SDK is distributed as source. There is no Maven Central or GitHub Packages artifact yet. See [Installation](#installation) for how to use it today.

## Overview

rig-java lets you declare multi-service test environments in Java and run them via [rigd](https://github.com/matgreaves/rig). Services start in parallel, wiring is resolved automatically, and environments tear down cleanly after tests.

```java
@RegisterExtension
static RigExtension rig = RigExtension.create("my-test")
    .service("db", Rig.postgres().initSql("CREATE TABLE users (id INT)"))
    .service("api", Rig.process("java -jar api.jar").egress("db"))
    .timeout(Duration.ofSeconds(60))
    .build();

@Test
void createUser() {
    var ep = rig.endpoint("api");
    var resp = httpPost("http://" + ep.addr() + "/users", """{"name": "alice"}""");
    assertEquals(201, resp.statusCode());
}
```

## Modules

| Module | Description | Scope |
|--------|-------------|-------|
| `rig-connect` | Pure types library (Endpoint, Wiring, Protocol, Attrs). Zero dependencies. | Compile (only if your app uses `RigWiring.parseWiring()`) |
| `rig-client` | Full SDK: `Rig.up()`, service builders, SSE, server management. Depends on Gson. | Test |
| `rig-junit5` | JUnit 5 extension: lifecycle, test result reporting, parameter injection. | Test |

## Requirements

- Java 25+
- Maven
- Docker (for container, postgres, and temporal services)

## Installation

There is no published Maven artifact yet. Clone this repo and install to your local Maven repository:

```bash
git clone https://github.com/matgreaves/rig-java.git
cd rig-java
mvn install -DskipTests
```

Then add the dependency to your project:

```xml
<dependency>
    <groupId>io.github.matgreaves</groupId>
    <artifactId>rig-junit5</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>test</scope>
</dependency>
```

This transitively pulls in `rig-client` and `rig-connect`.

### Vendoring source

Alternatively, copy the source directly into your project:

```
rig-connect/src/main/java/**  -->  src/main/java/   (only if using RigWiring in production code)
rig-client/src/main/java/**   -->  src/test/java/
rig-junit5/src/main/java/**   -->  src/test/java/
```

Add Gson as a test dependency:

```xml
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.11.0</version>
    <scope>test</scope>
</dependency>
```

## Usage

### JUnit 5 (recommended)

```java
class OrderApiTest {
    @RegisterExtension
    static RigExtension rig = RigExtension.create("orders")
        .service("db", Rig.postgres())
        .service("api", Rig.process("java -jar api.jar").egress("db"))
        .timeout(Duration.ofSeconds(60))
        .build();

    @Test
    void listOrders() {
        var ep = rig.endpoint("api");
        // make HTTP requests against ep.addr()
    }
}
```

The environment starts once before all tests in the class and tears down after. Test results are posted to rigd's event timeline. Classes run in parallel with JUnit 5's parallel execution:

```properties
# junit-platform.properties
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.classes.default=concurrent
junit.jupiter.execution.parallel.mode.default=concurrent
```

### Manual (try-with-resources)

```java
try (var env = Rig.up("test", Map.of(
        "db", Rig.postgres(),
        "api", Rig.go_("./cmd/api").egress("db")
), Rig.withTimeout(Duration.ofSeconds(60)))) {
    var ep = env.endpoint("db");
    String dsn = Attrs.postgresDsn(ep);
    // ...
}
```

## Service types

| Factory | Type | Description |
|---------|------|-------------|
| `Rig.postgres()` | Managed | PostgreSQL container with init hooks |
| `Rig.temporal()` | Managed | Temporal server with gRPC + UI ingresses |
| `Rig.container(image)` | Container | Any Docker image |
| `Rig.process(command)` | Process | Pre-built binary |
| `Rig.go_(module)` | Go | Go module, built and run by rigd |
| `Rig.func(fn)` | In-process | Java function running in a virtual thread |
| `Rig.custom(type, config)` | Custom | Server-registered service type |

All service types support `.egress()`, `.egressAs()`, `.initHook()`, and `.prestartHook()`.

## In-process functions

`Rig.func()` runs a Java function as a service inside the test JVM. The function runs in a virtual thread and is interrupted on shutdown:

```java
Rig.func(() -> {
    var wiring = RigWiring.parseWiring();
    var ep = wiring.ingress();
    // start server on ep.host():ep.port(), block until interrupted
})
```

For rig-unaware apps, use a prestart hook to write configuration:

```java
Rig.func(() -> MyApp.main(args))
    .prestartHook(w -> {
        var ep = w.ingress();
        Files.writeString(Path.of(w.tempDir(), "config.properties"),
            "host=%s\nport=%d\n".formatted(ep.host(), ep.port()));
        args[0] = "-c";
        args[1] = Path.of(w.tempDir(), "config.properties").toString();
    })
```

## Environment variables

| Variable | Description |
|----------|-------------|
| `RIG_SERVER_ADDR` | rigd server URL (auto-detected if unset) |
| `RIG_PRESERVE` | Set to `true` to keep environment artifacts after teardown |
| `RIG_DIR` | rig directory (default: `~/.rig`) |
| `RIG_BINARY` | Path to rigd binary override |
| `HTTP_PROXY` / `HTTPS_PROXY` / `NO_PROXY` | Proxy configuration for rigd communication and binary downloads |

## Agent guide

If you're an AI coding agent helping a user write tests with rig-java, see [agents-guide.md](agents-guide.md) for a complete API reference with copy-pasteable patterns.

## Building

```bash
mvn compile    # compile all modules
mvn test       # run all tests (requires Docker)
```
