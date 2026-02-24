package io.github.matgreaves.rig.client.internal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks virtual threads running func services and interrupts them on shutdown.
 */
public final class FuncThreads {
    private final List<Thread> threads = new CopyOnWriteArrayList<>();

    public void register(Thread t) {
        threads.add(t);
    }

    public void interruptAll() {
        for (var t : threads) {
            t.interrupt();
        }
    }
}
