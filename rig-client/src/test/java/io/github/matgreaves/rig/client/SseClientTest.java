package io.github.matgreaves.rig.client;

import io.github.matgreaves.rig.client.internal.SseClient;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SseClientTest {

    @Test
    void parsesSingleEvent() throws Exception {
        String sseData = """
                event: environment.up
                data: {"type":"environment.up","env_dir":"/tmp/env"}

                """;

        var events = parseEvents(sseData);
        assertEquals(1, events.size());
        assertEquals("environment.up", events.getFirst().type);
        assertTrue(events.getFirst().data.contains("environment.up"));
    }

    @Test
    void parsesMultipleEvents() throws Exception {
        String sseData = """
                event: progress.stall
                data: {"type":"progress.stall","message":"waiting"}

                event: environment.up
                data: {"type":"environment.up","env_dir":"/tmp"}

                """;

        var events = parseEvents(sseData);
        assertEquals(2, events.size());
        assertEquals("progress.stall", events.get(0).type);
        assertEquals("environment.up", events.get(1).type);
    }

    @Test
    void ignoresEmptyEvents() throws Exception {
        String sseData = """
                event: test
                data:\s

                event: real
                data: {"type":"real"}

                """;

        var events = parseEvents(sseData);
        // "data: " with nothing after prefix results in empty data, so first event is skipped
        assertEquals(1, events.size());
        assertEquals("real", events.getFirst().type);
    }

    @Test
    void ignoresLinesWithoutPrefix() throws Exception {
        String sseData = """
                : comment line
                event: test
                id: 123
                data: {"type":"test"}

                """;

        var events = parseEvents(sseData);
        assertEquals(1, events.size());
        assertEquals("test", events.getFirst().type);
    }

    @Test
    void skipsEventsWithMissingFields() throws Exception {
        String sseData = """
                event: only-type

                data: only-data

                event: complete
                data: {"complete":true}

                """;

        var events = parseEvents(sseData);
        // First event has no data, second has no event type — both skipped
        assertEquals(1, events.size());
        assertEquals("complete", events.getFirst().type);
    }

    record SseEvent(String type, String data) {}

    private List<SseEvent> parseEvents(String sseData) throws Exception {
        var stream = new ByteArrayInputStream(sseData.getBytes(StandardCharsets.UTF_8));
        var events = new ArrayList<SseEvent>();
        SseClient.stream(stream, (type, data) -> { events.add(new SseEvent(type, data)); return true; });
        return events;
    }
}
