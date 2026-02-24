package io.github.matgreaves.rig.client;

import com.google.gson.Gson;
import io.github.matgreaves.rig.client.internal.FuncThreads;
import io.github.matgreaves.rig.client.internal.LogShipper;
import io.github.matgreaves.rig.client.internal.RigHttpClient;
import io.github.matgreaves.rig.client.internal.SseClient;
import io.github.matgreaves.rig.client.internal.WireEvent;
import io.github.matgreaves.rig.connect.*;

import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * Main event loop: reads SSE events from rigd, dispatches callbacks,
 * tracks state, returns Environment on environment.up.
 */
public final class EventDispatcher {
    private final RigHttpClient httpClient;
    private final String serverUrl;
    private final String envId;
    private final HookRegistry registry;
    private final FuncThreads funcThreads;
    private final Gson gson = new Gson();
    private String lastStallMessage = "";

    public EventDispatcher(
            RigHttpClient httpClient,
            String serverUrl,
            String envId,
            HookRegistry registry,
            FuncThreads funcThreads
    ) {
        this.httpClient = httpClient;
        this.serverUrl = serverUrl;
        this.envId = envId;
        this.registry = registry;
        this.funcThreads = funcThreads;
    }

    /**
     * Connects to the SSE stream and processes events until environment.up or failure.
     *
     * @return the resolved Environment
     * @throws RigException on failure
     * @throws RigTimeoutException if the stream ends due to timeout
     */
    public Environment streamUntilReady() {
        String url = "%s/environments/%s/events".formatted(serverUrl, envId);
        HttpResponse<InputStream> response = httpClient.getStream(url);

        if (response.statusCode() != 200) {
            throw new RigException("event stream: HTTP " + response.statusCode());
        }

        final Environment[] result = {null};
        final RigException[] error = {null};

        try {
            SseClient.stream(response, (eventType, data) -> {
                if (result[0] != null || error[0] != null) return false;

                WireEvent.Event ev = gson.fromJson(data, WireEvent.Event.class);
                if (ev == null) return true;

                switch (ev.type) {
                    case "callback.request" -> {
                        if (ev.callback == null) return true;
                        if ("start".equals(ev.callback.type)) {
                            dispatchStartCallback(ev.service, ev.callback);
                        } else {
                            dispatchHookCallback(ev.service, ev.callback);
                        }
                    }
                    case "environment.up" -> {
                        result[0] = buildEnvironment(ev);
                        return false;
                    }
                    case "environment.down" -> {
                        String msg = ev.message != null && !ev.message.isEmpty()
                                ? ev.message
                                : "environment shut down unexpectedly";
                        error[0] = new RigException(msg);
                        return false;
                    }
                    case "progress.stall" -> {
                        if (ev.message != null && !ev.message.isEmpty()) {
                            lastStallMessage = ev.message;
                        }
                    }
                    default -> {} // ignore other event types
                }
                return true;
            });
        } catch (Exception e) {
            if (result[0] != null) return result[0];
            if (error[0] != null) throw error[0];
            throw new RigException("event stream read: " + e.getMessage(), e);
        }

        if (result[0] != null) return result[0];
        if (error[0] != null) throw error[0];

        throw new RigTimeoutException(lastStallMessage);
    }

    private void dispatchHookCallback(String serviceName, WireEvent.CallbackRequest cb) {
        HookFunction handler = registry.getHook(cb.name);
        if (handler == null) {
            postCallbackResult(serviceName, cb.request_id,
                    "no handler registered for callback \"%s\"".formatted(cb.name));
            throw new RigException("no handler registered for callback \"%s\"".formatted(cb.name));
        }

        Wiring wiring = convertWiring(cb.wiring);
        String handlerError = null;
        try {
            handler.execute(wiring);
        } catch (Exception e) {
            handlerError = e.getMessage();
        }

        postCallbackResult(serviceName, cb.request_id, handlerError);
        if (handlerError != null) {
            throw new RigException("callback \"%s\": %s".formatted(cb.name, handlerError));
        }
    }

    private void dispatchStartCallback(String serviceName, WireEvent.CallbackRequest cb) {
        RigFunction handler = registry.getStart(cb.name);
        if (handler == null) {
            postCallbackResult(serviceName, cb.request_id,
                    "no start handler registered for callback \"%s\"".formatted(cb.name));
            throw new RigException("no start handler registered for callback \"%s\"".formatted(cb.name));
        }

        Wiring wiring = convertWiring(cb.wiring);

        // Create a log shipper for this service.
        var logShipper = new LogShipper(httpClient, serverUrl, envId, serviceName);

        // Launch the function in a virtual thread.
        var thread = Thread.ofVirtual().name("rig-func-" + serviceName).unstarted(() -> {
            try {
                ScopedValue.where(RigWiring.WIRING, wiring)
                        .where(RigWiring.LOG_WRITER, logShipper)
                        .run(() -> {
                            try {
                                handler.run();
                            } catch (InterruptedException ignored) {
                                // expected shutdown
                            } catch (Exception e) {
                                if (!Thread.currentThread().isInterrupted()) {
                                    postServiceError(serviceName, e.getMessage());
                                }
                            }
                        });
            } finally {
                logShipper.flush();
            }
        });
        funcThreads.register(thread);
        thread.start();

        // Respond immediately — the function is running.
        postCallbackResult(serviceName, cb.request_id, null);
    }

    private Wiring convertWiring(WireEvent.WiringContext w) {
        if (w == null) return new Wiring(null, null, null, null, null);
        return new Wiring(
                convertEndpoints(w.ingresses),
                convertEndpoints(w.egresses),
                w.attributes,
                w.temp_dir,
                w.env_dir
        );
    }

    private Map<String, Endpoint> convertEndpoints(Map<String, WireEvent.WireEndpoint> eps) {
        if (eps == null) return null;
        var out = new LinkedHashMap<String, Endpoint>();
        for (var entry : eps.entrySet()) {
            var ep = entry.getValue();
            Protocol protocol = null;
            if (ep.protocol != null && !ep.protocol.isEmpty()) {
                try { protocol = Protocol.fromWire(ep.protocol); }
                catch (IllegalArgumentException ignored) {}
            }
            out.put(entry.getKey(), new Endpoint(ep.host, ep.port, protocol, ep.attributes));
        }
        return out;
    }

    private Environment buildEnvironment(WireEvent.Event ev) {
        var services = new LinkedHashMap<String, ResolvedService>();
        if (ev.ingresses != null) {
            for (var svcEntry : ev.ingresses.entrySet()) {
                var ingresses = new LinkedHashMap<String, Endpoint>();
                for (var ingEntry : svcEntry.getValue().entrySet()) {
                    var ep = ingEntry.getValue();
                    Protocol protocol = null;
                    if (ep.protocol != null && !ep.protocol.isEmpty()) {
                        try { protocol = Protocol.fromWire(ep.protocol); }
                        catch (IllegalArgumentException ignored) {}
                    }
                    ingresses.put(ingEntry.getKey(),
                            new Endpoint(ep.host, ep.port, protocol, ep.attributes));
                }
                services.put(svcEntry.getKey(), new ResolvedService(ingresses));
            }
        }
        return new Environment(envId, services, ev.env_dir, httpClient, serverUrl, funcThreads);
    }

    private void postCallbackResult(String serviceName, String requestId, String errorMsg) {
        try {
            String error = errorMsg != null ? ",\"error\":\"%s\"".formatted(escape(errorMsg)) : "";
            String json = """
                    {"type":"callback.response","service":"%s","request_id":"%s"%s}"""
                    .formatted(escape(serviceName), escape(requestId), error);
            String url = "%s/environments/%s/events".formatted(serverUrl, envId);
            httpClient.postJson(url, json);
        } catch (Exception ignored) {}
    }

    private void postServiceError(String serviceName, String errorMsg) {
        try {
            String json = """
                    {"type":"service.error","service":"%s","error":"%s"}"""
                    .formatted(escape(serviceName), escape(errorMsg));
            String url = "%s/environments/%s/events".formatted(serverUrl, envId);
            httpClient.postJson(url, json);
        } catch (Exception ignored) {}
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
