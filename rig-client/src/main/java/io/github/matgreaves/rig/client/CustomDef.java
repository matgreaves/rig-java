package io.github.matgreaves.rig.client;

import io.github.matgreaves.rig.client.internal.EgressRef;
import io.github.matgreaves.rig.client.internal.HookDef;
import java.util.*;

/**
 * Defines a service using any server-registered type.
 * <pre>{@code
 * Rig.custom("my-type", Map.of("key", "value"))
 * }</pre>
 */
public final class CustomDef implements ServiceDef {
    final String svcType;
    final Map<String, Object> config;
    final List<String> args = new ArrayList<>();
    final Map<String, IngressDef> ingresses = new LinkedHashMap<>();
    final Map<String, EgressRef> egresses = new LinkedHashMap<>();
    final List<HookDef> prestartHooks = new ArrayList<>();
    final List<HookDef> initHooks = new ArrayList<>();

    CustomDef(String svcType, Map<String, Object> config) {
        this.svcType = svcType;
        this.config = config == null ? Map.of() : new LinkedHashMap<>(config);
        this.ingresses.put("default", IngressDef.http());
    }

    public CustomDef noIngress() { ingresses.clear(); return this; }
    public CustomDef ingress(String name, IngressDef def) { ingresses.put(name, def); return this; }

    public CustomDef egress(String service, String... ingress) {
        return egressAs(service, service, ingress);
    }

    public CustomDef egressAs(String name, String service, String... ingress) {
        egresses.put(name, new EgressRef(service, ingress.length > 0 ? ingress[0] : ""));
        return this;
    }

    public CustomDef args(String... args) { Collections.addAll(this.args, args); return this; }
    public CustomDef initHook(HookFunction fn) { initHooks.add(new HookDef.ClientFunc(fn)); return this; }
    public CustomDef prestartHook(HookFunction fn) { prestartHooks.add(new HookDef.ClientFunc(fn)); return this; }
}
