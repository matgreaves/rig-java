package io.github.matgreaves.rig.client;

/**
 * Thrown when the startup timeout is exceeded.
 */
public class RigTimeoutException extends RigException {
    private final String lastStallMessage;

    public RigTimeoutException(String lastStallMessage) {
        super(lastStallMessage != null && !lastStallMessage.isEmpty()
                ? "startup timeout exceeded:\n" + lastStallMessage
                : "startup timeout exceeded");
        this.lastStallMessage = lastStallMessage;
    }

    public String lastStallMessage() {
        return lastStallMessage;
    }
}
