package io.github.matgreaves.rig.connect;

import java.util.Map;

/**
 * A typed attribute key for use with {@link Endpoint#attributes()}.
 * The type parameter {@code T} indicates the expected value type.
 *
 * @param <T> the attribute value type
 */
public final class Attr<T> {
    private final String name;
    private final Class<T> type;

    private Attr(String name, Class<T> type) {
        this.name = name;
        this.type = type;
    }

    /** Creates a string-typed attribute key. */
    public static Attr<String> ofString(String name) {
        return new Attr<>(name, String.class);
    }

    /** Creates a boolean-typed attribute key. */
    public static Attr<Boolean> ofBoolean(String name) {
        return new Attr<>(name, Boolean.class);
    }

    /** Returns the raw key name. */
    public String name() {
        return name;
    }

    /**
     * Retrieves the attribute value from the endpoint.
     * Returns {@code null} if the key is not present.
     *
     * @throws ClassCastException if the value has the wrong type
     */
    public T get(Endpoint ep) {
        Object v = ep.attributes().get(name);
        if (v == null) {
            return null;
        }
        if (!type.isInstance(v)) {
            throw new ClassCastException(
                    "rig: attribute \"%s\" has type %s, want %s"
                            .formatted(name, v.getClass().getSimpleName(), type.getSimpleName()));
        }
        return type.cast(v);
    }

    /**
     * Retrieves the attribute value, throwing if missing.
     *
     * @throws IllegalStateException if the attribute is not found
     * @throws ClassCastException    if the value has the wrong type
     */
    public T mustGet(Endpoint ep) {
        T v = get(ep);
        if (v == null) {
            throw new IllegalStateException("rig: attribute \"%s\" not found".formatted(name));
        }
        return v;
    }

    /**
     * Writes the attribute value into a mutable map
     * (typically used when building endpoint attributes).
     */
    public void set(Map<String, Object> m, T v) {
        m.put(name, v);
    }
}
