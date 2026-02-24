package io.github.matgreaves.rig.client;

import io.github.matgreaves.rig.client.internal.EgressRef;
import io.github.matgreaves.rig.client.internal.HookDef;
import java.util.*;

/**
 * Defines a service backed by an in-process function.
 * <pre>{@code
 * Rig.func(signal -> {
 *     var wiring = RigWiring.parseWiring();
 *     // start serving...
 *     signal.await();
 * }).egress("db")
 * }</pre>
 */
public final class FuncDef implements ServiceDef {
    final RigFunction fn;
    final Map<String, IngressDef> ingresses = new LinkedHashMap<>();
    final Map<String, EgressRef> egresses = new LinkedHashMap<>();
    final List<HookDef> prestartHooks = new ArrayList<>();
    final List<HookDef> initHooks = new ArrayList<>();

    FuncDef(RigFunction fn) {
        this.fn = fn;
        this.ingresses.put("default", IngressDef.http());
    }

    public FuncDef noIngress() { ingresses.clear(); return this; }
    public FuncDef ingress(String name, IngressDef def) { ingresses.put(name, def); return this; }

    public FuncDef egress(String service, String... ingress) {
        return egressAs(service, service, ingress);
    }

    public FuncDef egressAs(String name, String service, String... ingress) {
        egresses.put(name, new EgressRef(service, ingress.length > 0 ? ingress[0] : ""));
        return this;
    }

    public FuncDef initHook(HookFunction fn) { initHooks.add(new HookDef.ClientFunc(fn)); return this; }
    public FuncDef prestartHook(HookFunction fn) { prestartHooks.add(new HookDef.ClientFunc(fn)); return this; }
}
