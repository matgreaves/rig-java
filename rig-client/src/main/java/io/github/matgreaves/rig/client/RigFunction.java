package io.github.matgreaves.rig.client;

/**
 * A function that runs as a service in the test process.
 * Should behave like a service main: call {@code RigWiring.parseWiring()} to
 * get wiring, start serving, and block until interrupted.
 */
@FunctionalInterface
public interface RigFunction {
    void run() throws Exception;
}
