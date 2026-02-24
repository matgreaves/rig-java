package io.github.matgreaves.rig.client;

import io.github.matgreaves.rig.client.internal.EgressRef;
import io.github.matgreaves.rig.client.internal.HookDef;
import java.util.*;

/**
 * Defines a service that runs a pre-built binary.
 * <pre>{@code
 * Rig.process("/path/to/binary")
 *     .dir("/work")
 *     .egress("postgres")
 * }</pre>
 */
public final class ProcessDef implements ServiceDef {
    final String command;
    String dir = "";
    final List<String> args = new ArrayList<>();
    final Map<String, IngressDef> ingresses = new LinkedHashMap<>();
    final Map<String, EgressRef> egresses = new LinkedHashMap<>();
    final List<HookDef> prestartHooks = new ArrayList<>();
    final List<HookDef> initHooks = new ArrayList<>();

    ProcessDef(String command) {
        this.command = command;
        this.ingresses.put("default", IngressDef.http());
    }

    public ProcessDef noIngress() { ingresses.clear(); return this; }
    public ProcessDef dir(String dir) { this.dir = dir; return this; }
    public ProcessDef ingress(String name, IngressDef def) { ingresses.put(name, def); return this; }

    public ProcessDef egress(String service, String... ingress) {
        return egressAs(service, service, ingress);
    }

    public ProcessDef egressAs(String name, String service, String... ingress) {
        egresses.put(name, new EgressRef(service, ingress.length > 0 ? ingress[0] : ""));
        return this;
    }

    public ProcessDef args(String... args) { Collections.addAll(this.args, args); return this; }
    public ProcessDef initHook(HookFunction fn) { initHooks.add(new HookDef.ClientFunc(fn)); return this; }
    public ProcessDef prestartHook(HookFunction fn) { prestartHooks.add(new HookDef.ClientFunc(fn)); return this; }
}
