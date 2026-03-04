package io.github.matgreaves.rig.client.internal;

import io.github.matgreaves.rig.client.RigException;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

/**
 * Finds or starts a rigd server instance. Mirrors the Go SDK's EnsureServer logic.
 */
public final class ServerManager {
    private ServerManager() {}

    public static final String RIGD_VERSION = "0.7.0";

    /**
     * Finds or starts a rigd instance and returns its base URL.
     *
     * @param rigDir the rig directory (empty string for default)
     * @return the server base URL (e.g. "http://127.0.0.1:12345")
     */
    public static String ensureServer(String rigDir) {
        if (rigDir == null || rigDir.isEmpty()) {
            rigDir = defaultRigDir();
        }

        BinaryInfo binary = findBinary();

        // When RIG_BINARY is set (override), use unversioned file names.
        Path addrFile;
        Path lockFile;
        if (binary.override) {
            addrFile = Path.of(rigDir, "rigd.addr");
            lockFile = Path.of(rigDir, "rigd.lock");
        } else {
            addrFile = Path.of(rigDir, "rigd-v" + RIGD_VERSION + ".addr");
            lockFile = Path.of(rigDir, "rigd-v" + RIGD_VERSION + ".lock");
        }

        // Fast path: existing instance.
        String addr = readAddrFile(addrFile);
        if (addr != null && probeHealth(addr)) {
            return "http://" + addr;
        }

        // Acquire lock to prevent concurrent starts.
        try {
            Files.createDirectories(Path.of(rigDir));
        } catch (IOException e) {
            throw new RigException("create rig dir: " + e.getMessage(), e);
        }

        try (var lockChannel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = lockChannel.lock()) {

            // Double-check after acquiring lock.
            addr = readAddrFile(addrFile);
            if (addr != null && probeHealth(addr)) {
                return "http://" + addr;
            }

            String binPath = binary.path;

            // If no binary found, download.
            if (binPath == null) {
                Path destPath = Path.of(rigDir, "bin", "v" + RIGD_VERSION, "rigd");
                String url = BinaryDownloader.downloadUrl(RIGD_VERSION);
                BinaryDownloader.download(url, destPath);
                binPath = destPath.toString();
            }

            // Start rigd as a detached subprocess.
            var pb = new ProcessBuilder(binPath, "--idle", "5m", "--rig-dir", rigDir);
            if (!binary.override) {
                pb.command().addAll(java.util.List.of("--addr-file", addrFile.toString()));
            }
            pb.redirectErrorStream(false);

            // Append stderr to log file.
            Path logPath = Path.of(rigDir, "rigd.log");
            pb.redirectError(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);

            try {
                pb.start();
            } catch (IOException e) {
                throw new RigException("start rigd: " + e.getMessage(), e);
            }

            // Poll for addr file.
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline) {
                addr = readAddrFile(addrFile);
                if (addr != null && !addr.isEmpty() && probeHealth(addr)) {
                    return "http://" + addr;
                }
                try { Thread.sleep(100); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RigException("interrupted while waiting for rigd", e);
                }
            }

            throw new RigException("rigd did not become healthy within 10s (log: " + logPath + ")");

        } catch (IOException e) {
            throw new RigException("acquire lock: " + e.getMessage(), e);
        }
    }

    record BinaryInfo(String path, boolean override) {}

    /**
     * Locates the rigd binary. Search order:
     * 1. RIG_BINARY env var
     * 2. ~/.rig/bin/v{version}/rigd
     * 3. ~/.rig/bin/rigd (legacy)
     * 4. PATH
     * 5. Not found (null path)
     */
    static BinaryInfo findBinary() {
        String rigBinary = System.getenv("RIG_BINARY");
        if (rigBinary != null && !rigBinary.isEmpty()) {
            if (Files.exists(Path.of(rigBinary))) {
                return new BinaryInfo(rigBinary, true);
            }
            throw new RigException("RIG_BINARY=\"%s\": file not found".formatted(rigBinary));
        }

        String home = System.getProperty("user.home");
        if (home != null) {
            // Versioned path.
            Path versioned = Path.of(home, ".rig", "bin", "v" + RIGD_VERSION, "rigd");
            if (Files.exists(versioned)) return new BinaryInfo(versioned.toString(), false);

            // Legacy unversioned.
            Path legacy = Path.of(home, ".rig", "bin", "rigd");
            if (Files.exists(legacy)) return new BinaryInfo(legacy.toString(), false);
        }

        // PATH lookup.
        try {
            var pb = new ProcessBuilder("which", "rigd");
            pb.redirectErrorStream(true);
            var proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes()).trim();
            if (proc.waitFor() == 0 && !output.isEmpty()) {
                return new BinaryInfo(output, false);
            }
        } catch (Exception ignored) {
        }

        return new BinaryInfo(null, false);
    }

    static boolean probeHealth(String addr) {
        try {
            var client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(1))
                    .proxy(ProxyConfig.fromEnv())
                    .build();
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + addr + "/health"))
                    .timeout(Duration.ofSeconds(1))
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static String readAddrFile(Path path) {
        try {
            String content = Files.readString(path).trim();
            return content.isEmpty() ? null : content;
        } catch (IOException e) {
            return null;
        }
    }

    static String defaultRigDir() {
        String dir = System.getenv("RIG_DIR");
        if (dir != null && !dir.isEmpty()) return dir;
        String home = System.getProperty("user.home");
        if (home != null) return Path.of(home, ".rig").toString();
        return Path.of(System.getProperty("java.io.tmpdir"), "rig").toString();
    }
}
