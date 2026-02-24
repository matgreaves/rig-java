package io.github.matgreaves.rig.client;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import io.github.matgreaves.rig.connect.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * End-to-end integration tests that spin up real environments via rigd.
 * Requires Docker to be running.
 */
class RigIntegrationTest {

	private static final Duration TIMEOUT = Duration.ofSeconds(60);

	@Test
	void funcService() throws Exception {
		RigFunction fn = () -> {
			var wiring = RigWiring.parseWiring();
			var ep = wiring.ingress();

			var server = HttpServer.create(
				new InetSocketAddress(ep.host(), ep.port()),
				0
			);
			server.createContext("/", exchange -> {
				byte[] body = "ok".getBytes();
				exchange.sendResponseHeaders(200, body.length);
				try (var os = exchange.getResponseBody()) {
					os.write(body);
				}
			});
			server.start();
			try {
				Thread.sleep(Long.MAX_VALUE);
			} finally {
				server.stop(0);
			}
		};

		try (
			var env = Rig.up(
				"func-test",
				Map.of("echo", Rig.func(fn)),
				Rig.withTimeout(TIMEOUT)
			)
		) {
			var ep = env.endpoint("echo");
			var resp = httpGet("http://" + ep.addr() + "/");

			assertEquals(200, resp.statusCode());
			assertEquals("ok", resp.body());
		}
	}

	@Test
	void postgresService() {
		try (
			var env = Rig.up(
				"pg-test",
				Map.of("db", Rig.postgres()),
				Rig.withTimeout(TIMEOUT)
			)
		) {
			var ep = env.endpoint("db");

			assertFalse(ep.attr("PGHOST").isEmpty(), "PGHOST should be set");
			assertFalse(ep.attr("PGPORT").isEmpty(), "PGPORT should be set");
			assertFalse(ep.attr("PGUSER").isEmpty(), "PGUSER should be set");
			assertFalse(
				ep.attr("PGPASSWORD").isEmpty(),
				"PGPASSWORD should be set"
			);
			assertFalse(
				ep.attr("PGDATABASE").isEmpty(),
				"PGDATABASE should be set"
			);

			String dsn = Attrs.postgresDsn(ep);
			assertTrue(
				dsn.startsWith("postgres://"),
				"DSN should start with postgres://"
			);
			assertTrue(
				dsn.contains(ep.attr("PGHOST")),
				"DSN should contain host"
			);
			assertTrue(
				dsn.contains(ep.attr("PGPORT")),
				"DSN should contain port"
			);
			assertTrue(
				dsn.contains(ep.attr("PGUSER")),
				"DSN should contain user"
			);
			assertTrue(
				dsn.contains(ep.attr("PGPASSWORD")),
				"DSN should contain password"
			);
			assertTrue(
				dsn.contains(ep.attr("PGDATABASE")),
				"DSN should contain database"
			);
			assertTrue(
				dsn.contains("sslmode=disable"),
				"DSN should contain sslmode=disable"
			);
		}
	}

	@Test
	void funcServiceWithInitHook() throws Exception {
		var capturedWiring = new AtomicReference<Wiring>();

		RigFunction fn = () -> {
			var wiring = RigWiring.parseWiring();
			var ep = wiring.ingress();

			var server = HttpServer.create(
				new InetSocketAddress(ep.host(), ep.port()),
				0
			);
			server.createContext("/", exchange -> {
				byte[] body = "ok".getBytes();
				exchange.sendResponseHeaders(200, body.length);
				try (var os = exchange.getResponseBody()) {
					os.write(body);
				}
			});
			server.start();
			try {
				Thread.sleep(Long.MAX_VALUE);
			} finally {
				server.stop(0);
			}
		};

		try (
			var env = Rig.up(
				"hook-test",
				Map.of(
					"svc",
					Rig.func(fn).initHook(w -> capturedWiring.set(w))
				),
				Rig.withTimeout(TIMEOUT)
			)
		) {
			assertNotNull(
				capturedWiring.get(),
				"init hook should have been called"
			);
			assertFalse(
				capturedWiring.get().ingresses().isEmpty(),
				"wiring should have ingresses"
			);
		}
	}

	/**
	 * A "rig unaware" HTTP server. Parses {@code -c <path>} to find its
	 * config file. This is the app — it knows nothing about rig.
	 */
	static void echoApp(String[] args) throws Exception {
		String configFile = null;
		for (int i = 0; i < args.length - 1; i++) {
			if ("-c".equals(args[i])) {
				configFile = args[i + 1];
				break;
			}
		}
		if (configFile == null) {
			throw new IllegalArgumentException("usage: echoApp -c <config>");
		}

		var props = new Properties();
		try (var in = Files.newInputStream(Path.of(configFile))) {
			props.load(in);
		}

		var server = HttpServer.create(
			new InetSocketAddress(
				props.getProperty("host"),
				Integer.parseInt(props.getProperty("port"))
			),
			0
		);
		server.createContext("/", exchange -> {
			byte[] body = "ok".getBytes();
			exchange.sendResponseHeaders(200, body.length);
			try (var os = exchange.getResponseBody()) {
				os.write(body);
			}
		});
		server.start();
		try {
			Thread.sleep(Long.MAX_VALUE);
		} finally {
			server.stop(0);
		}
	}

	/**
	 * Adapts a main-like method to a rig func service in two phases:
	 *
	 * <ol>
	 *   <li><b>prestart</b> ({@link #writeConfigFile}): writes wiring to
	 *       {@code $rig_temp/server.properties}</li>
	 *   <li><b>run</b> ({@link #runWithConfigFile}): invokes the app with
	 *       {@code -c $rig_temp/server.properties}</li>
	 * </ol>
	 *
	 * The two phases match by convention (the well-known config path)
	 * rather than shared state.
	 */
	@FunctionalInterface
	interface MainLike {
		void main(String[] args) throws Exception;
	}

	private static final String CONFIG_FILE = "server.properties";

	static HookFunction writeConfigFile() {
		return w -> {
			var ep = w.ingress();
			Files.writeString(
				Path.of(w.tempDir(), CONFIG_FILE),
				"host=%s\nport=%d\n".formatted(ep.host(), ep.port())
			);
		};
	}

	static RigFunction runWithConfigFile(MainLike app) {
		return () -> {
			var w = RigWiring.parseWiring();
			var configPath = Path.of(w.tempDir(), CONFIG_FILE);
			app.main(new String[]{"-c", configPath.toString()});
		};
	}

	@Test
	void rigUnawareService() throws Exception {
		try (
			var env = Rig.up(
				"unaware-test",
				Map.of("svc", Rig.func(
					runWithConfigFile(RigIntegrationTest::echoApp)
				).prestartHook(
					writeConfigFile()
				)),
				Rig.withTimeout(TIMEOUT)
			)
		) {
			var ep = env.endpoint("svc");
			var resp = httpGet("http://" + ep.addr() + "/");

			assertEquals(200, resp.statusCode());
			assertEquals("ok", resp.body());
		}
	}

	@Test
	void containerService() throws Exception {
		try (
			var env = Rig.up(
				"container-test",
				Map.of("web", Rig.container("nginx:alpine").port(80)),
				Rig.withTimeout(TIMEOUT)
			)
		) {
			var ep = env.endpoint("web");
			var resp = httpGet("http://" + ep.addr() + "/");

			assertEquals(200, resp.statusCode());
			assertFalse(
				resp.body().isEmpty(),
				"response body should not be empty"
			);
		}
	}

	private static HttpResponse<String> httpGet(String url)
		throws IOException, InterruptedException {
		var client = HttpClient.newHttpClient();
		var request = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.timeout(Duration.ofSeconds(10))
			.GET()
			.build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
