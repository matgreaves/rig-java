package io.github.matgreaves.rig.client;

/**
 * Base unchecked exception for rig SDK errors.
 */
public class RigException extends RuntimeException {
    public RigException(String message) {
        super(message);
    }

    public RigException(String message, Throwable cause) {
        super(message, cause);
    }
}
