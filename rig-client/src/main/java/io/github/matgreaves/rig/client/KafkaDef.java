package io.github.matgreaves.rig.client;

import io.github.matgreaves.rig.client.internal.EgressRef;
import io.github.matgreaves.rig.client.internal.HookDef;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;

/**
 * Defines a service backed by the builtin Kafka type (Redpanda).
 * Each test gets a fresh container — no pool, no topic collision.
 *
 * <p>The service exposes two ingresses:
 * <ul>
 *   <li>{@code "default"} (Kafka protocol on port 9092) — use {@code ep.hostPort()} as bootstrap servers</li>
 *   <li>{@code "schema-registry"} (HTTP on port 8081) — Confluent-compatible schema registry</li>
 * </ul>
 *
 * <pre>{@code
 * Rig.kafka()
 * Rig.kafka().image("redpandadata/redpanda:v24.1.1")
 * Rig.kafka().avroSchema("schemas/user-value.avsc")
 * }</pre>
 */
public final class KafkaDef implements ServiceDef {
    String image = "";
    final Map<String, EgressRef> egresses = new LinkedHashMap<>();
    final List<HookDef> prestartHooks = new ArrayList<>();
    final List<HookDef> initHooks = new ArrayList<>();

    KafkaDef() {}

    /** Overrides the default Redpanda Docker image. */
    public KafkaDef image(String image) { this.image = image; return this; }

    public KafkaDef egress(String service, String... ingress) {
        return egressAs(service, service, ingress);
    }

    public KafkaDef egressAs(String name, String service, String... ingress) {
        egresses.put(name, new EgressRef(service, ingress.length > 0 ? ingress[0] : ""));
        return this;
    }

    /**
     * Registers an Avro schema file to be posted to the schema registry during init.
     * The subject name is derived from the filename (sans extension):
     * "user-value.avsc" → subject "user-value".
     *
     * <p>The file is read at call time.
     */
    public KafkaDef avroSchema(String path) {
        return addSchema(path, "AVRO");
    }

    /**
     * Registers a Protobuf schema file to be posted to the schema registry during init.
     * The subject name is derived from the filename (sans extension):
     * "order-key.proto" → subject "order-key".
     *
     * <p>The file is read at call time.
     */
    public KafkaDef protoSchema(String path) {
        return addSchema(path, "PROTOBUF");
    }

    private KafkaDef addSchema(String path, String schemaType) {
        Path filePath = Path.of(path);
        if (!filePath.isAbsolute()) {
            filePath = Path.of(System.getProperty("user.dir")).resolve(filePath);
        }
        try {
            String data = Files.readString(filePath);
            String filename = filePath.getFileName().toString();
            String subject = filename.contains(".")
                    ? filename.substring(0, filename.lastIndexOf('.'))
                    : filename;
            initHooks.add(new HookDef.Schema(subject, schemaType, data));
        } catch (IOException e) {
            throw new UncheckedIOException("rig: schema: " + path, e);
        }
        return this;
    }

    public KafkaDef initHook(HookFunction fn) { initHooks.add(new HookDef.ClientFunc(fn)); return this; }
    public KafkaDef prestartHook(HookFunction fn) { prestartHooks.add(new HookDef.ClientFunc(fn)); return this; }
}
