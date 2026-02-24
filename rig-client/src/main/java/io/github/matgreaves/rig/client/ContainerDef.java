package io.github.matgreaves.rig.client;

import io.github.matgreaves.rig.client.internal.EgressRef;
import io.github.matgreaves.rig.client.internal.HookDef;
import java.util.*;

/**
 * Defines a service backed by a Docker container.
 * <pre>{@code
 * Rig.container("nginx:alpine").port(80)
 * Rig.container("redis:7").port(6379).exec("redis-cli", "SET", "key", "value")
 * }</pre>
 */
public final class ContainerDef implements ServiceDef {
    final String image;
    final List<String> cmd = new ArrayList<>();
    final Map<String, String> env = new LinkedHashMap<>();
    final Map<String, IngressDef> ingresses = new LinkedHashMap<>();
    final Map<String, EgressRef> egresses = new LinkedHashMap<>();
    final List<HookDef> prestartHooks = new ArrayList<>();
    final List<HookDef> initHooks = new ArrayList<>();

    ContainerDef(String image) {
        this.image = image;
        this.ingresses.put("default", IngressDef.http());
    }

    /** Sets the container port for the default ingress. */
    public ContainerDef port(int containerPort) {
        IngressDef existing = ingresses.getOrDefault("default", IngressDef.http());
        ingresses.put("default", new IngressDef(
                existing.protocol(),
                containerPort,
                existing.ready(),
                existing.attributes()
        ));
        return this;
    }

    /** Overrides the container's default command. */
    public ContainerDef cmd(String... args) { Collections.addAll(this.cmd, args); return this; }

    /** Sets an environment variable on the container. */
    public ContainerDef env(String key, String value) { this.env.put(key, value); return this; }

    public ContainerDef noIngress() { ingresses.clear(); return this; }
    public ContainerDef ingress(String name, IngressDef def) { ingresses.put(name, def); return this; }

    public ContainerDef egress(String service, String... ingress) {
        return egressAs(service, service, ingress);
    }

    public ContainerDef egressAs(String name, String service, String... ingress) {
        egresses.put(name, new EgressRef(service, ingress.length > 0 ? ingress[0] : ""));
        return this;
    }

    /** Registers an exec init hook that runs a command inside the container. */
    public ContainerDef exec(String... cmd) {
        initHooks.add(new HookDef.Exec(List.of(cmd)));
        return this;
    }

    public ContainerDef initHook(HookFunction fn) { initHooks.add(new HookDef.ClientFunc(fn)); return this; }
    public ContainerDef prestartHook(HookFunction fn) { prestartHooks.add(new HookDef.ClientFunc(fn)); return this; }
}
