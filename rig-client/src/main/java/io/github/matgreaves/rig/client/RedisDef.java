package io.github.matgreaves.rig.client;

import io.github.matgreaves.rig.client.internal.EgressRef;
import io.github.matgreaves.rig.client.internal.HookDef;
import java.util.*;

/**
 * Defines a service backed by the builtin Redis type.
 * <pre>{@code
 * Rig.redis()
 * Rig.redis().image("redis:7-alpine")
 * }</pre>
 */
public final class RedisDef implements ServiceDef {
    String image = "";
    final Map<String, EgressRef> egresses = new LinkedHashMap<>();
    final List<HookDef> prestartHooks = new ArrayList<>();
    final List<HookDef> initHooks = new ArrayList<>();

    RedisDef() {}

    /** Overrides the default Redis Docker image. */
    public RedisDef image(String image) { this.image = image; return this; }

    public RedisDef egress(String service, String... ingress) {
        return egressAs(service, service, ingress);
    }

    public RedisDef egressAs(String name, String service, String... ingress) {
        egresses.put(name, new EgressRef(service, ingress.length > 0 ? ingress[0] : ""));
        return this;
    }

    public RedisDef initHook(HookFunction fn) { initHooks.add(new HookDef.ClientFunc(fn)); return this; }
    public RedisDef prestartHook(HookFunction fn) { prestartHooks.add(new HookDef.ClientFunc(fn)); return this; }
}
