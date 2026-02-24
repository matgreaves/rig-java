package io.github.matgreaves.rig.client.internal;

import java.net.*;
import java.util.*;

/**
 * Parses HTTP_PROXY / HTTPS_PROXY / NO_PROXY environment variables and
 * returns a {@link ProxySelector} for use with {@link java.net.http.HttpClient}.
 */
public final class ProxyConfig {
    private ProxyConfig() {}

    /**
     * Creates a ProxySelector from environment variables.
     * Returns {@link ProxySelector#getDefault()} if no proxy env vars are set.
     */
    public static ProxySelector fromEnv() {
        String httpProxy = envOrNull("HTTP_PROXY", "http_proxy");
        String httpsProxy = envOrNull("HTTPS_PROXY", "https_proxy");

        if (httpProxy == null && httpsProxy == null) {
            return ProxySelector.getDefault();
        }

        String noProxy = envOrNull("NO_PROXY", "no_proxy");
        Set<String> noProxyHosts = parseNoProxy(noProxy);

        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                String host = uri.getHost();

                // Always bypass proxy for localhost and loopback
                if (ProxyConfig.isLocalhost(host)) {
                    return List.of(Proxy.NO_PROXY);
                }

                // Check NO_PROXY patterns
                if (ProxyConfig.matchesNoProxy(host, noProxyHosts)) {
                    return List.of(Proxy.NO_PROXY);
                }

                String proxyUrl = "https".equals(uri.getScheme()) ? httpsProxy : httpProxy;
                if (proxyUrl == null) proxyUrl = httpProxy;
                if (proxyUrl == null) {
                    return List.of(Proxy.NO_PROXY);
                }

                try {
                    URI parsed = URI.create(proxyUrl);
                    int port = parsed.getPort();
                    if (port == -1) port = 8080;
                    return List.of(new Proxy(Proxy.Type.HTTP,
                            new InetSocketAddress(parsed.getHost(), port)));
                } catch (Exception e) {
                    return List.of(Proxy.NO_PROXY);
                }
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, java.io.IOException ioe) {
                // no-op
            }
        };
    }

    private static String envOrNull(String... names) {
        for (String name : names) {
            String val = System.getenv(name);
            if (val != null && !val.isEmpty()) return val;
        }
        return null;
    }

    private static Set<String> parseNoProxy(String noProxy) {
        if (noProxy == null || noProxy.isEmpty()) return Set.of();
        var hosts = new HashSet<String>();
        for (String part : noProxy.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) hosts.add(trimmed);
        }
        return hosts;
    }

    /**
     * Returns true if the host is localhost or a loopback address.
     */
    private static boolean isLocalhost(String host) {
        if (host == null) return false;
        return host.equals("localhost")
            || host.equals("127.0.0.1")
            || host.equals("::1")
            || host.equals("0.0.0.0")
            || host.startsWith("127.")
            || host.equals("[::1]");
    }

    /**
     * Checks if a host matches any NO_PROXY pattern.
     * Supports:
     * - Exact match: "example.com"
     * - Suffix match with leading dot: ".example.com" matches "foo.example.com"
     * - IP addresses
     * - Wildcards: "*" or "*.example.com"
     */
    private static boolean matchesNoProxy(String host, Set<String> noProxyPatterns) {
        if (host == null || noProxyPatterns.isEmpty()) return false;

        for (String pattern : noProxyPatterns) {
            // Wildcard
            if (pattern.equals("*")) return true;

            // Exact match
            if (host.equalsIgnoreCase(pattern)) return true;

            // Suffix match with leading dot
            if (pattern.startsWith(".") && host.toLowerCase().endsWith(pattern.toLowerCase())) {
                return true;
            }

            // Wildcard suffix: "*.example.com"
            if (pattern.startsWith("*.")) {
                String suffix = pattern.substring(1); // ".example.com"
                if (host.equalsIgnoreCase(pattern.substring(2))) return true; // exact: "example.com"
                if (host.toLowerCase().endsWith(suffix.toLowerCase())) return true; // suffix
            }
        }

        return false;
    }
}
