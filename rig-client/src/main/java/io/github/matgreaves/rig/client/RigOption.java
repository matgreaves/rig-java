package io.github.matgreaves.rig.client;

import java.time.Duration;

/**
 * Options for configuring {@link Rig#up}.
 */
public sealed interface RigOption {

    /** Sets the rigd server base URL. */
    record WithServer(String url) implements RigOption {}

    /** Sets the maximum time to wait for the environment to become ready. */
    record WithTimeout(Duration duration) implements RigOption {}

    /** Disables transparent traffic proxying. */
    record WithoutObserve() implements RigOption {}

    /** Sets the environment TTL (time-to-live). When set, the server-side timer handles teardown. */
    record WithTTL(Duration duration) implements RigOption {}
}
