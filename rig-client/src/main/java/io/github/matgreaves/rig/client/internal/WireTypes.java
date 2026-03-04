package io.github.matgreaves.rig.client.internal;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Wire format DTOs matching the JSON payloads sent to rigd.
 * All fields use Gson-compatible naming via {@code @SerializedName} in the
 * Gson adapter, but here we use camelCase and convert via FieldNamingPolicy.
 */
public final class WireTypes {
    private WireTypes() {}

    public static final class SpecEnvironment {
        public String name;
        public Map<String, SpecService> services;
        public boolean observe;
        public Map<String, String> host_env;
        public String dir;
    }

    public static final class SpecService {
        public String type;
        public Object config;
        public List<String> args;
        public Map<String, SpecIngressSpec> ingresses;
        public Map<String, SpecEgressSpec> egresses;
        public SpecHooks hooks;
    }

    public static final class SpecHooks {
        public List<SpecHookSpec> prestart;
        public List<SpecHookSpec> init;
    }

    public static final class SpecHookSpec {
        public String type;
        public SpecClientFuncSpec client_func;
        public Object config;
    }

    public static final class SpecClientFuncSpec {
        public String name;

        public SpecClientFuncSpec() {}

        public SpecClientFuncSpec(String name) {
            this.name = name;
        }
    }

    public static final class SpecIngressSpec {
        public int container_port;
        public String protocol;
        public SpecReadySpec ready;
        public Map<String, Object> attributes;
    }

    public static final class SpecEgressSpec {
        public String service;
        public String ingress;
    }

    public static final class SpecReadySpec {
        public String type;
        public String path;
        public String interval;
        public String timeout;
    }

    /** Converts a Duration to a wire-format string like "5s" or "100ms". */
    public static String durationToWire(Duration d) {
        if (d == null || d.isZero()) return "";
        long millis = d.toMillis();
        if (millis % 1000 == 0) return (millis / 1000) + "s";
        return millis + "ms";
    }
}
