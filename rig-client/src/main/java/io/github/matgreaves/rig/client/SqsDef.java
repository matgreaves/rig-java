package io.github.matgreaves.rig.client;

import io.github.matgreaves.rig.client.internal.EgressRef;
import io.github.matgreaves.rig.client.internal.HookDef;
import java.util.*;

/**
 * Defines a service backed by the builtin SQS type.
 * <pre>{@code
 * Rig.sqs()
 * }</pre>
 */
public final class SqsDef implements ServiceDef {
    final Map<String, EgressRef> egresses = new LinkedHashMap<>();
    final List<HookDef> prestartHooks = new ArrayList<>();
    final List<HookDef> initHooks = new ArrayList<>();

    SqsDef() {}

    public SqsDef egress(String service, String... ingress) {
        return egressAs(service, service, ingress);
    }

    public SqsDef egressAs(String name, String service, String... ingress) {
        egresses.put(name, new EgressRef(service, ingress.length > 0 ? ingress[0] : ""));
        return this;
    }

    public SqsDef initHook(HookFunction fn) { initHooks.add(new HookDef.ClientFunc(fn)); return this; }
    public SqsDef prestartHook(HookFunction fn) { prestartHooks.add(new HookDef.ClientFunc(fn)); return this; }
}
