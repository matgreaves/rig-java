package io.github.matgreaves.rig.client;

import io.github.matgreaves.rig.client.internal.EgressRef;
import io.github.matgreaves.rig.client.internal.HookDef;
import java.util.*;

/**
 * Defines a service built from a Go module.
 * <pre>{@code
 * Rig.go_("./cmd/api")
 *     .egress("postgres")
 *     .initHook(w -> { ... })
 * }</pre>
 */
public final class GoDef implements ServiceDef {
    final String module;
    final List<String> args = new ArrayList<>();
    final Map<String, IngressDef> ingresses = new LinkedHashMap<>();
    final Map<String, EgressRef> egresses = new LinkedHashMap<>();
    final List<HookDef> prestartHooks = new ArrayList<>();
    final List<HookDef> initHooks = new ArrayList<>();

    GoDef(String module) {
        this.module = module;
        this.ingresses.put("default", IngressDef.http());
    }

    /** Removes all ingresses, for pure worker services. */
    public GoDef noIngress() { ingresses.clear(); return this; }

    /** Adds or overrides an ingress. */
    public GoDef ingress(String name, IngressDef def) { ingresses.put(name, def); return this; }

    /** Adds a dependency named after the target service. */
    public GoDef egress(String service, String... ingress) {
        return egressAs(service, service, ingress);
    }

    /** Adds a dependency with a custom local name. */
    public GoDef egressAs(String name, String service, String... ingress) {
        egresses.put(name, new EgressRef(service, ingress.length > 0 ? ingress[0] : ""));
        return this;
    }

    /** Sets command-line arguments. */
    public GoDef args(String... args) { Collections.addAll(this.args, args); return this; }

    /** Registers a client-side init hook (after health checks, before marked ready). */
    public GoDef initHook(HookFunction fn) { initHooks.add(new HookDef.ClientFunc(fn)); return this; }

    /** Registers a client-side prestart hook (after egresses resolved, before process starts). */
    public GoDef prestartHook(HookFunction fn) { prestartHooks.add(new HookDef.ClientFunc(fn)); return this; }
}
