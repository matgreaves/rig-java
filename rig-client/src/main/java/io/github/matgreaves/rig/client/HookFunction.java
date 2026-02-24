package io.github.matgreaves.rig.client;

import io.github.matgreaves.rig.connect.Wiring;

/**
 * A client-side hook function that receives the service wiring.
 * Used for init hooks (after health checks) and prestart hooks (before service starts).
 */
@FunctionalInterface
public interface HookFunction {
    void execute(Wiring wiring) throws Exception;
}
