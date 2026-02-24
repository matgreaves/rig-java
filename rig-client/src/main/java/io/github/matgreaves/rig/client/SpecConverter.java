package io.github.matgreaves.rig.client;

import io.github.matgreaves.rig.client.internal.EgressRef;
import io.github.matgreaves.rig.client.internal.HookDef;
import io.github.matgreaves.rig.client.internal.WireTypes;
import io.github.matgreaves.rig.client.internal.WireTypes.*;

import java.nio.file.Path;
import java.util.*;

/**
 * Converts SDK service definitions to the wire format sent to rigd.
 */
final class SpecConverter {
    private SpecConverter() {}

    /** Converts SDK Services map to the specEnvironment wire format. */
    public static SpecEnvironment toSpec(
            String name,
            Map<String, ServiceDef> services,
            HookRegistry registry,
            boolean observe
    ) {
        var spec = new SpecEnvironment();
        spec.name = name;
        spec.observe = observe;
        spec.services = new LinkedHashMap<>();
        for (var entry : services.entrySet()) {
            spec.services.put(entry.getKey(), serviceToSpec(entry.getValue(), registry));
        }
        return spec;
    }

    private static SpecService serviceToSpec(ServiceDef def, HookRegistry registry) {
        return switch (def) {
            case GoDef d -> goToSpec(d, registry);
            case ProcessDef d -> processToSpec(d, registry);
            case FuncDef d -> funcToSpec(d, registry);
            case ContainerDef d -> containerToSpec(d, registry);
            case PostgresDef d -> postgresToSpec(d, registry);
            case TemporalDef d -> temporalToSpec(d, registry);
            case CustomDef d -> customToSpec(d, registry);
        };
    }

    private static SpecService goToSpec(GoDef d, HookRegistry registry) {
        String module = d.module;
        if (!Path.of(module).isAbsolute()) {
            module = Path.of(System.getProperty("user.dir"), module).toString();
        }

        var svc = new SpecService();
        svc.type = "go";
        svc.config = Map.of("module", module);
        svc.args = d.args.isEmpty() ? null : List.copyOf(d.args);
        svc.ingresses = ingressesToSpec(d.ingresses);
        svc.egresses = egressesToSpec(d.egresses);
        svc.hooks = hooksToSpec(d.prestartHooks, d.initHooks, registry);
        return svc;
    }

    private static SpecService processToSpec(ProcessDef d, HookRegistry registry) {
        var config = new LinkedHashMap<String, String>();
        config.put("command", d.command);
        if (!d.dir.isEmpty()) config.put("dir", d.dir);

        var svc = new SpecService();
        svc.type = "process";
        svc.config = config;
        svc.args = d.args.isEmpty() ? null : List.copyOf(d.args);
        svc.ingresses = ingressesToSpec(d.ingresses);
        svc.egresses = egressesToSpec(d.egresses);
        svc.hooks = hooksToSpec(d.prestartHooks, d.initHooks, registry);
        return svc;
    }

    private static SpecService funcToSpec(FuncDef d, HookRegistry registry) {
        String startName = registry.registerStart(d.fn);

        var svc = new SpecService();
        svc.type = "client";
        svc.config = Map.of("start_handler", startName);
        svc.ingresses = ingressesToSpec(d.ingresses);
        svc.egresses = egressesToSpec(d.egresses);
        svc.hooks = hooksToSpec(d.prestartHooks, d.initHooks, registry);
        return svc;
    }

    private static SpecService containerToSpec(ContainerDef d, HookRegistry registry) {
        var config = new LinkedHashMap<String, Object>();
        config.put("image", d.image);
        if (!d.cmd.isEmpty()) config.put("cmd", List.copyOf(d.cmd));
        if (!d.env.isEmpty()) config.put("env", Map.copyOf(d.env));

        var svc = new SpecService();
        svc.type = "container";
        svc.config = config;
        svc.ingresses = ingressesToSpec(d.ingresses);
        svc.egresses = egressesToSpec(d.egresses);
        svc.hooks = hooksToSpec(d.prestartHooks, d.initHooks, registry);
        return svc;
    }

    private static SpecService postgresToSpec(PostgresDef d, HookRegistry registry) {
        Map<String, String> config = null;
        if (!d.image.isEmpty()) {
            config = Map.of("image", d.image);
        }

        // Postgres has an implicit TCP ingress on port 5432.
        var ingress = new SpecIngressSpec();
        ingress.protocol = "tcp";
        ingress.container_port = 5432;

        var svc = new SpecService();
        svc.type = "postgres";
        svc.config = config;
        svc.ingresses = Map.of("default", ingress);
        svc.egresses = egressesToSpec(d.egresses);
        svc.hooks = hooksToSpec(d.prestartHooks, d.initHooks, registry);
        return svc;
    }

    private static SpecService temporalToSpec(TemporalDef d, HookRegistry registry) {
        Map<String, String> config = null;
        if (!d.version.isEmpty() || !d.namespace.isEmpty()) {
            var cfgMap = new LinkedHashMap<String, String>();
            if (!d.version.isEmpty()) cfgMap.put("version", d.version);
            if (!d.namespace.isEmpty()) cfgMap.put("namespace", d.namespace);
            config = cfgMap;
        }

        // Temporal auto-creates GRPC + HTTP ingresses.
        var grpcIngress = new SpecIngressSpec();
        grpcIngress.protocol = "grpc";
        var httpIngress = new SpecIngressSpec();
        httpIngress.protocol = "http";

        var svc = new SpecService();
        svc.type = "temporal";
        svc.config = config;
        svc.ingresses = Map.of("default", grpcIngress, "ui", httpIngress);
        svc.egresses = egressesToSpec(d.egresses);
        svc.hooks = hooksToSpec(d.prestartHooks, d.initHooks, registry);
        return svc;
    }

    private static SpecService customToSpec(CustomDef d, HookRegistry registry) {
        var svc = new SpecService();
        svc.type = d.svcType;
        svc.config = d.config.isEmpty() ? null : Map.copyOf(d.config);
        svc.args = d.args.isEmpty() ? null : List.copyOf(d.args);
        svc.ingresses = ingressesToSpec(d.ingresses);
        svc.egresses = egressesToSpec(d.egresses);
        svc.hooks = hooksToSpec(d.prestartHooks, d.initHooks, registry);
        return svc;
    }

    private static Map<String, SpecIngressSpec> ingressesToSpec(Map<String, IngressDef> ingresses) {
        if (ingresses == null || ingresses.isEmpty()) return null;
        var out = new LinkedHashMap<String, SpecIngressSpec>();
        for (var entry : ingresses.entrySet()) {
            var ing = entry.getValue();
            var s = new SpecIngressSpec();
            s.protocol = ing.protocol().wire();
            s.container_port = ing.containerPort();
            s.attributes = ing.attributes();
            if (ing.ready() != null) {
                var r = new SpecReadySpec();
                r.type = ing.ready().type_();
                r.path = ing.ready().path();
                r.interval = WireTypes.durationToWire(ing.ready().interval());
                r.timeout = WireTypes.durationToWire(ing.ready().timeout());
                s.ready = r;
            }
            out.put(entry.getKey(), s);
        }
        return out;
    }

    private static Map<String, SpecEgressSpec> egressesToSpec(Map<String, EgressRef> egresses) {
        if (egresses == null || egresses.isEmpty()) return null;
        var out = new LinkedHashMap<String, SpecEgressSpec>();
        for (var entry : egresses.entrySet()) {
            var ref = entry.getValue();
            var s = new SpecEgressSpec();
            s.service = ref.service();
            s.ingress = ref.ingress().isEmpty() ? null : ref.ingress();
            out.put(entry.getKey(), s);
        }
        return out;
    }

    private static SpecHooks hooksToSpec(
            List<HookDef> prestartHooks,
            List<HookDef> initHooks,
            HookRegistry registry
    ) {
        if (prestartHooks.isEmpty() && initHooks.isEmpty()) return null;
        var hooks = new SpecHooks();
        if (!prestartHooks.isEmpty()) {
            hooks.prestart = new ArrayList<>();
            for (var h : prestartHooks) hooks.prestart.add(hookToSpec(h, registry));
        }
        if (!initHooks.isEmpty()) {
            hooks.init = new ArrayList<>();
            for (var h : initHooks) hooks.init.add(hookToSpec(h, registry));
        }
        return hooks;
    }

    private static SpecHookSpec hookToSpec(HookDef h, HookRegistry registry) {
        var spec = new SpecHookSpec();
        switch (h) {
            case HookDef.ClientFunc cf -> {
                String name = registry.registerHook(cf.fn());
                spec.type = "client_func";
                spec.client_func = new SpecClientFuncSpec(name);
            }
            case HookDef.Sql sql -> {
                spec.type = "sql";
                spec.config = Map.of("statements", sql.statements());
            }
            case HookDef.Exec exec -> {
                spec.type = "exec";
                spec.config = Map.of("command", exec.command());
            }
        }
        return spec;
    }
}
