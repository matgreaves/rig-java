package io.github.matgreaves.rig.client.internal;

import java.util.Map;

/**
 * SSE event payload DTOs for events received from rigd.
 */
public final class WireEvent {
    private WireEvent() {}

    public static final class Event {
        public String type;
        public String service;
        public String ingress;
        public String artifact;
        public String error;
        public String message;
        public CallbackRequest callback;
        public RequestInfo request;
        public GRPCCallInfo grpc_call;
        public ConnectionInfo connection;
        public WireEndpoint endpoint;
        public LogEntry log;
        public String env_dir;
        public Map<String, Map<String, WireEndpoint>> ingresses;
    }

    public static final class RequestInfo {
        public String source;
        public String target;
        public String ingress;
        public String method;
        public String path;
        public int status_code;
        public double latency_ms;
        public long request_size;
        public long response_size;
    }

    public static final class ConnectionInfo {
        public String source;
        public String target;
        public String ingress;
        public long bytes_in;
        public long bytes_out;
        public double duration_ms;
    }

    public static final class GRPCCallInfo {
        public String source;
        public String target;
        public String ingress;
        public String service;
        public String method;
        public String grpc_status;
        public String grpc_message;
        public double latency_ms;
        public long request_size;
        public long response_size;
    }

    public static final class LogEntry {
        public String stream;
        public String data;
    }

    public static final class CallbackRequest {
        public String request_id;
        public String name;
        public String type;
        public WiringContext wiring;
    }

    public static final class WiringContext {
        public Map<String, WireEndpoint> ingresses;
        public Map<String, WireEndpoint> egresses;
        public String temp_dir;
        public String env_dir;
        public Map<String, String> attributes;
    }

    public static final class WireEndpoint {
        public String hostport;
        public String protocol;
        public Map<String, Object> attributes;
    }
}
