package io.github.matgreaves.rig.client;

import io.github.matgreaves.rig.client.internal.EgressRef;
import io.github.matgreaves.rig.client.internal.HookDef;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Defines a service backed by the builtin Postgres type.
 * <pre>{@code
 * Rig.postgres()
 * Rig.postgres().image("postgres:15").initSql("CREATE TABLE users (id SERIAL)")
 * }</pre>
 */
public final class PostgresDef implements ServiceDef {
    String image = "";
    final Map<String, EgressRef> egresses = new LinkedHashMap<>();
    final List<HookDef> prestartHooks = new ArrayList<>();
    final List<HookDef> initHooks = new ArrayList<>();

    PostgresDef() {}

    /** Overrides the default Postgres Docker image (postgres:16-alpine). */
    public PostgresDef image(String image) { this.image = image; return this; }

    public PostgresDef egress(String service, String... ingress) {
        return egressAs(service, service, ingress);
    }

    public PostgresDef egressAs(String name, String service, String... ingress) {
        egresses.put(name, new EgressRef(service, ingress.length > 0 ? ingress[0] : ""));
        return this;
    }

    /** Registers SQL statements to run via psql after the database is healthy. */
    public PostgresDef initSql(String... statements) {
        initHooks.add(new HookDef.Sql(List.of(statements)));
        return this;
    }

    /**
     * Reads all .sql files from a directory, sorts by filename, and registers them
     * as SQL init hooks.
     */
    public PostgresDef initSqlDir(String dir) {
        Path dirPath = Path.of(dir);
        if (!dirPath.isAbsolute()) {
            dirPath = Path.of(System.getProperty("user.dir")).resolve(dirPath);
        }
        try (Stream<Path> paths = Files.list(dirPath)) {
            List<String> stmts = paths
                    .filter(p -> !Files.isDirectory(p) && p.toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .map(p -> {
                        try { return Files.readString(p); }
                        catch (IOException e) { throw new UncheckedIOException(e); }
                    })
                    .toList();
            if (!stmts.isEmpty()) {
                initHooks.add(new HookDef.Sql(stmts));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("rig: initSqlDir: " + dir, e);
        }
        return this;
    }

    /** Registers an exec init hook that runs a command inside the container. */
    public PostgresDef exec(String... cmd) {
        initHooks.add(new HookDef.Exec(List.of(cmd)));
        return this;
    }

    public PostgresDef initHook(HookFunction fn) { initHooks.add(new HookDef.ClientFunc(fn)); return this; }
    public PostgresDef prestartHook(HookFunction fn) { prestartHooks.add(new HookDef.ClientFunc(fn)); return this; }
}
