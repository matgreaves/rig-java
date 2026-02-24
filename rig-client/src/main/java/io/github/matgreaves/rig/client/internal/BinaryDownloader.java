package io.github.matgreaves.rig.client.internal;

import io.github.matgreaves.rig.client.RigException;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.zip.GZIPInputStream;

/**
 * Downloads rigd binary from GitHub releases.
 * Extracts from tar.gz archive using minimal tar header parsing.
 */
public final class BinaryDownloader {
    private BinaryDownloader() {}

    /** Returns the GitHub releases download URL for the given rigd version. */
    public static String downloadUrl(String version) {
        String os = detectOs();
        String arch = detectArch();
        return "https://github.com/matgreaves/rig/releases/download/rigd/v%s/rigd-%s-%s.tar.gz"
                .formatted(version, os, arch);
    }

    /**
     * Downloads a tar.gz archive from the URL, extracts the "rigd" binary,
     * and writes it to destPath. Uses a temp file + rename for atomicity.
     */
    public static void download(String url, Path destPath) {
        try {
            Files.createDirectories(destPath.getParent());
        } catch (IOException e) {
            throw new RigException("create directory: " + e.getMessage(), e);
        }

        try {
            var client = HttpClient.newBuilder()
                    .proxy(ProxyConfig.fromEnv())
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new RigException("download %s: HTTP %d".formatted(url, response.statusCode()));
            }

            try (var gzip = new GZIPInputStream(response.body())) {
                extractRigdFromTar(gzip, destPath);
            }
        } catch (RigException e) {
            throw e;
        } catch (Exception e) {
            throw new RigException("download %s: %s".formatted(url, e.getMessage()), e);
        }
    }

    /**
     * Minimal tar extraction — reads 512-byte headers looking for the "rigd" file.
     * Tar format: 512-byte header followed by file data padded to 512 bytes.
     */
    private static void extractRigdFromTar(InputStream tarStream, Path destPath) throws IOException {
        byte[] header = new byte[512];
        while (true) {
            int bytesRead = readFully(tarStream, header);
            if (bytesRead < 512) break;

            // Check for zero block (end of archive).
            boolean allZero = true;
            for (byte b : header) { if (b != 0) { allZero = false; break; } }
            if (allZero) break;

            // Parse filename from bytes 0-99 (null-terminated).
            String fileName = parseString(header, 0, 100);

            // Parse size from bytes 124-135 (octal, null/space-terminated).
            long fileSize = parseOctal(header, 124, 12);

            // Parse type flag at byte 156.
            byte typeFlag = header[156];

            // Strip path prefix to get basename.
            String baseName = fileName;
            int lastSlash = fileName.lastIndexOf('/');
            if (lastSlash >= 0) baseName = fileName.substring(lastSlash + 1);

            if ("rigd".equals(baseName) && (typeFlag == '0' || typeFlag == 0)) {
                // Found the binary — extract it.
                Path tmpFile = Files.createTempFile(destPath.getParent(), "rigd-download-", "");
                try (var out = new FileOutputStream(tmpFile.toFile())) {
                    long remaining = fileSize;
                    byte[] buf = new byte[8192];
                    while (remaining > 0) {
                        int toRead = (int) Math.min(buf.length, remaining);
                        int n = tarStream.read(buf, 0, toRead);
                        if (n <= 0) throw new IOException("unexpected end of tar data");
                        out.write(buf, 0, n);
                        remaining -= n;
                    }
                }
                Files.setPosixFilePermissions(tmpFile, PosixFilePermissions.fromString("rwxr-xr-x"));
                Files.move(tmpFile, destPath, StandardCopyOption.REPLACE_EXISTING);
                return;
            }

            // Skip file data (padded to 512-byte boundary).
            long skipBytes = ((fileSize + 511) / 512) * 512;
            long skipped = 0;
            while (skipped < skipBytes) {
                long n = tarStream.skip(skipBytes - skipped);
                if (n <= 0) break;
                skipped += n;
            }
        }

        throw new RigException("rigd binary not found in archive");
    }

    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int n = in.read(buf, total, buf.length - total);
            if (n < 0) break;
            total += n;
        }
        return total;
    }

    private static String parseString(byte[] buf, int offset, int length) {
        int end = offset;
        while (end < offset + length && buf[end] != 0) end++;
        return new String(buf, offset, end - offset);
    }

    private static long parseOctal(byte[] buf, int offset, int length) {
        int end = offset;
        while (end < offset + length && buf[end] != 0 && buf[end] != ' ') end++;
        String s = new String(buf, offset, end - offset).trim();
        if (s.isEmpty()) return 0;
        return Long.parseLong(s, 8);
    }

    private static String detectOs() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) return "darwin";
        if (os.contains("linux")) return "linux";
        throw new RigException("unsupported OS: " + os);
    }

    private static String detectArch() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (arch.equals("aarch64") || arch.equals("arm64")) return "arm64";
        if (arch.equals("amd64") || arch.equals("x86_64")) return "amd64";
        throw new RigException("unsupported architecture: " + arch);
    }
}
