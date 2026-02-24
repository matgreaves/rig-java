package io.github.matgreaves.rig.client.internal;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

/**
 * Async log writer that buffers lines and posts them as service.log events to rigd.
 * Safe for concurrent use.
 */
public final class LogShipper extends OutputStream {
    private final RigHttpClient httpClient;
    private final String serverUrl;
    private final String envId;
    private final String service;

    private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(256);
    private final CountDownLatch done = new CountDownLatch(1);
    private final Object bufLock = new Object();
    private final StringBuilder buf = new StringBuilder();
    private volatile boolean closed = false;

    public LogShipper(RigHttpClient httpClient, String serverUrl, String envId, String service) {
        this.httpClient = httpClient;
        this.serverUrl = serverUrl;
        this.envId = envId;
        this.service = service;

        Thread.ofVirtual().name("rig-log-" + service).start(this::drain);
    }

    private void drain() {
        try {
            while (!closed || !queue.isEmpty()) {
                String first;
                try {
                    first = queue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (first == null) continue;

                var batch = new ArrayList<String>();
                batch.add(first);
                queue.drainTo(batch);

                postLog(String.join("\n", batch));
            }
        } finally {
            done.countDown();
        }
    }

    @Override
    public void write(int b) {
        write(new byte[]{(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) {
        synchronized (bufLock) {
            buf.append(new String(b, off, len));
            flushCompleteLines();
        }
    }

    private void flushCompleteLines() {
        int nlIndex;
        while ((nlIndex = buf.indexOf("\n")) >= 0) {
            String line = buf.substring(0, nlIndex);
            buf.delete(0, nlIndex + 1);
            enqueue(line);
        }
    }

    /** Sends any remaining buffered data and waits for all lines to be posted. */
    public void flush() {
        synchronized (bufLock) {
            if (!buf.isEmpty()) {
                enqueue(buf.toString());
                buf.setLength(0);
            }
        }
        closed = true;
        try { done.await(); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void enqueue(String line) {
        if (line == null || line.isEmpty()) return;
        queue.offer(line); // drop if full rather than block
    }

    private void postLog(String logData) {
        try {
            String json = """
                    {"type":"service.log","service":"%s","stream":"stdout","log_data":"%s"}"""
                    .formatted(escape(service), escape(logData));
            String url = "%s/environments/%s/events".formatted(serverUrl, envId);
            httpClient.postJson(url, json);
        } catch (Exception ignored) {
            // never block the caller on HTTP failures
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
