package io.github.matgreaves.rig.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Maps generated callback names to their handler functions.
 * Used during spec conversion to register handlers, and during event dispatching
 * to look them up.
 */
final class HookRegistry {
    private static final AtomicLong SEQ = new AtomicLong();

    private final Map<String, HookFunction> hookHandlers = new ConcurrentHashMap<>();
    private final Map<String, RigFunction> startHandlers = new ConcurrentHashMap<>();

    /** Generates a unique hook name and registers the handler. */
    public String registerHook(HookFunction fn) {
        String name = "_hook_" + SEQ.incrementAndGet();
        hookHandlers.put(name, fn);
        return name;
    }

    /** Generates a unique start handler name and registers the function. */
    public String registerStart(RigFunction fn) {
        String name = "_start_" + SEQ.incrementAndGet();
        startHandlers.put(name, fn);
        return name;
    }

    public HookFunction getHook(String name) {
        return hookHandlers.get(name);
    }

    public RigFunction getStart(String name) {
        return startHandlers.get(name);
    }
}
