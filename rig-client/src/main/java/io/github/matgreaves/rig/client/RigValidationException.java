package io.github.matgreaves.rig.client;

import java.util.List;

/**
 * Thrown when rigd returns a 422 spec validation error.
 */
public class RigValidationException extends RigException {
    private final List<String> validationErrors;

    public RigValidationException(List<String> validationErrors) {
        super("rig: spec validation failed:\n  " + String.join("\n  ", validationErrors));
        this.validationErrors = List.copyOf(validationErrors);
    }

    public List<String> validationErrors() {
        return validationErrors;
    }
}
