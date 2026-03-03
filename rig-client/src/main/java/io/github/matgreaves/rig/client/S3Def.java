package io.github.matgreaves.rig.client;

import io.github.matgreaves.rig.client.internal.EgressRef;
import io.github.matgreaves.rig.client.internal.HookDef;
import java.util.*;

/**
 * Defines a service backed by the builtin S3 type.
 * <pre>{@code
 * Rig.s3()
 * }</pre>
 */
public final class S3Def implements ServiceDef {
    final Map<String, EgressRef> egresses = new LinkedHashMap<>();
    final List<HookDef> prestartHooks = new ArrayList<>();
    final List<HookDef> initHooks = new ArrayList<>();

    S3Def() {}

    public S3Def egress(String service, String... ingress) {
        return egressAs(service, service, ingress);
    }

    public S3Def egressAs(String name, String service, String... ingress) {
        egresses.put(name, new EgressRef(service, ingress.length > 0 ? ingress[0] : ""));
        return this;
    }

    public S3Def initHook(HookFunction fn) { initHooks.add(new HookDef.ClientFunc(fn)); return this; }
    public S3Def prestartHook(HookFunction fn) { prestartHooks.add(new HookDef.ClientFunc(fn)); return this; }
}
