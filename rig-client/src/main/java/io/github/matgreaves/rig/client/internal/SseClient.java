package io.github.matgreaves.rig.client.internal;

import java.io.*;
import java.net.http.HttpResponse;
import java.util.function.BiPredicate;

/**
 * Simple SSE (Server-Sent Events) parser. Reads from an InputStream line-by-line
 * and dispatches event type + data on blank lines.
 */
public final class SseClient {
    private SseClient() {}

    /**
     * Parses SSE events from an HTTP response body stream.
     * Calls {@code handler} with (eventType, data) for each complete event.
     * The handler returns {@code true} to continue or {@code false} to stop.
     * Returns when the handler stops, the stream is closed, or an error occurs.
     *
     * @param response the HTTP response with an InputStream body
     * @param handler  receives (eventType, data); return false to stop
     * @throws IOException if reading fails
     */
    public static void stream(
            HttpResponse<InputStream> response,
            BiPredicate<String, String> handler
    ) throws IOException {
        stream(response.body(), handler);
    }

    /**
     * Parses SSE events from an InputStream.
     * The handler returns {@code true} to continue or {@code false} to stop.
     */
    public static void stream(
            InputStream inputStream,
            BiPredicate<String, String> handler
    ) throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String eventType = "";
            String data = "";
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("event: ")) {
                    eventType = line.substring(7);
                } else if (line.startsWith("data: ")) {
                    data = line.substring(6);
                } else if (line.isEmpty()) {
                    if (!eventType.isEmpty() && !data.isEmpty()) {
                        if (!handler.test(eventType, data)) {
                            return;
                        }
                    }
                    eventType = "";
                    data = "";
                }
            }
        }
    }
}
