package io.github.matgreaves.rig.client.internal;

import io.github.matgreaves.rig.client.HookFunction;
import java.util.List;

/**
 * Internal sealed hierarchy for hook definitions.
 */
public sealed interface HookDef {

    /** A client-side callback function. */
    record ClientFunc(HookFunction fn) implements HookDef {}

    /** SQL statements executed server-side via docker exec. */
    record Sql(List<String> statements) implements HookDef {}

    /** A command executed server-side via docker exec. */
    record Exec(List<String> command) implements HookDef {}

    /** A schema registration hook for Kafka schema registry. */
    record Schema(String subject, String schemaType, String schema) implements HookDef {}
}
