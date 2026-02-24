package io.github.matgreaves.rig.client;

import io.github.matgreaves.rig.connect.Protocol;
import java.util.Map;

/**
 * Defines an endpoint a service exposes.
 *
 * @param protocol      the application-layer protocol
 * @param containerPort for container types: the container-internal port
 * @param ready         optional health check override
 * @param attributes    static attributes published with this ingress
 */
public record IngressDef(
        Protocol protocol,
        int containerPort,
        ReadyDef ready,
        Map<String, Object> attributes
) {
    /** Returns an IngressDef for an HTTP endpoint. */
    public static IngressDef http() {
        return new IngressDef(Protocol.HTTP, 0, null, null);
    }

    /** Returns an IngressDef for a TCP endpoint. */
    public static IngressDef tcp() {
        return new IngressDef(Protocol.TCP, 0, null, null);
    }

    /** Returns an IngressDef for a gRPC endpoint. */
    public static IngressDef grpc() {
        return new IngressDef(Protocol.GRPC, 0, null, null);
    }
}
