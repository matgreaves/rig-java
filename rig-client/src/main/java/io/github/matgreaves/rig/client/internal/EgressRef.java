package io.github.matgreaves.rig.client.internal;

/**
 * Internal reference to a target service's ingress.
 *
 * @param service the target service name
 * @param ingress the target ingress name (empty string for default)
 */
public record EgressRef(String service, String ingress) {
}
