package io.github.matgreaves.rig.client;

import io.github.matgreaves.rig.client.internal.EgressRef;
import io.github.matgreaves.rig.client.internal.HookDef;
import java.util.*;

/**
 * Defines a service backed by the builtin Temporal type.
 * <pre>{@code
 * Rig.temporal()
 * Rig.temporal().version("1.5.1").namespace("my-ns")
 * }</pre>
 */
public final class TemporalDef implements ServiceDef {
    String version = "";
    String namespace = "";
    final Map<String, EgressRef> egresses = new LinkedHashMap<>();
    final List<HookDef> prestartHooks = new ArrayList<>();
    final List<HookDef> initHooks = new ArrayList<>();

    TemporalDef() {}

    /** Overrides the Temporal CLI version (default: 1.5.1). */
    public TemporalDef version(String v) { this.version = v; return this; }

    /** Overrides the default namespace name (default: "default"). */
    public TemporalDef namespace(String ns) { this.namespace = ns; return this; }

    public TemporalDef egress(String service, String... ingress) {
        return egressAs(service, service, ingress);
    }

    public TemporalDef egressAs(String name, String service, String... ingress) {
        egresses.put(name, new EgressRef(service, ingress.length > 0 ? ingress[0] : ""));
        return this;
    }

    public TemporalDef initHook(HookFunction fn) { initHooks.add(new HookDef.ClientFunc(fn)); return this; }
    public TemporalDef prestartHook(HookFunction fn) { prestartHooks.add(new HookDef.ClientFunc(fn)); return this; }
}
