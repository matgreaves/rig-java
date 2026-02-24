package io.github.matgreaves.rig.client.internal;

import io.github.matgreaves.rig.client.RigException;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;

/**
 * Wrapper around {@link java.net.http.HttpClient} configured with virtual threads
 * and proxy support for communicating with rigd.
 */
public final class RigHttpClient {
    private final HttpClient client;

    public RigHttpClient() {
        this.client = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .proxy(ProxyConfig.fromEnv())
                .build();
    }

    /** POST JSON to the given URL. Returns the response body as a string. */
    public HttpResponse<String> postJson(String url, String json) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RigException("HTTP POST " + url + " failed", e);
        }
    }

    /** DELETE the given URL. Returns the response body as a string. */
    public HttpResponse<String> delete(String url) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .DELETE()
                .build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RigException("HTTP DELETE " + url + " failed", e);
        }
    }

    /** GET the given URL, returning the response with an InputStream body (for SSE streaming). */
    public HttpResponse<InputStream> getStream(String url) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (Exception e) {
            throw new RigException("HTTP GET " + url + " failed", e);
        }
    }

    /** GET the given URL, returning the response body as a string. */
    public HttpResponse<String> get(String url) {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RigException("HTTP GET " + url + " failed", e);
        }
    }

    /** Returns the underlying HttpClient for direct use. */
    public HttpClient underlying() {
        return client;
    }
}
