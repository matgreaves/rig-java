package io.github.matgreaves.rig.client;

import java.time.Duration;

/**
 * Overrides the health check for an ingress.
 *
 * @param type_    check type: "tcp", "http", "grpc"
 * @param path     HTTP check path
 * @param interval poll interval
 * @param timeout  max wait
 */
public record ReadyDef(String type_, String path, Duration interval, Duration timeout) {
}
