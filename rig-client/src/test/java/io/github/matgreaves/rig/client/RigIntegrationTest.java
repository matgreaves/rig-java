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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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

	@Test
	void redisService() throws Exception {
		try (
			var env = Rig.up(
				"redis-test",
				Map.of("cache", Rig.redis()),
				Rig.withTimeout(TIMEOUT)
			)
		) {
			var ep = env.endpoint("cache");

			assertFalse(
				ep.attr("REDIS_URL").isEmpty(),
				"REDIS_URL should be set"
			);
			assertTrue(
				ep.attr("REDIS_URL").startsWith("redis://"),
				"REDIS_URL should start with redis://"
			);

			// PING via raw Redis protocol.
			try (var sock = new java.net.Socket(ep.host(), ep.port())) {
				sock.setSoTimeout(5000);
				var out = sock.getOutputStream();
				var in = new java.io.BufferedReader(
					new java.io.InputStreamReader(sock.getInputStream())
				);
				out.write("PING\r\n".getBytes());
				out.flush();
				String reply = in.readLine();
				assertEquals("+PONG", reply, "Redis should respond to PING");
			}
		}
	}

	@Test
	void s3Service() throws Exception {
		try (
			var env = Rig.up(
				"s3-test",
				Map.of("store", Rig.s3()),
				Rig.withTimeout(TIMEOUT)
			)
		) {
			var ep = env.endpoint("store");

			String endpoint = ep.attr("S3_ENDPOINT");
			String bucket = ep.attr("S3_BUCKET");
			assertFalse(endpoint.isEmpty(), "S3_ENDPOINT should be set");
			assertFalse(bucket.isEmpty(), "S3_BUCKET should be set");
			assertEquals("rigadmin", ep.attr("AWS_ACCESS_KEY_ID"));
			assertEquals("rigadmin", ep.attr("AWS_SECRET_ACCESS_KEY"));

			// PUT an object then GET it back via the S3 HTTP API (SigV4 auth).
			var client = HttpClient.newHttpClient();
			String accessKey = ep.attr("AWS_ACCESS_KEY_ID");
			String secretKey = ep.attr("AWS_SECRET_ACCESS_KEY");

			var putUri = URI.create("%s/%s/test.txt".formatted(endpoint, bucket));
			byte[] body = "hello s3".getBytes(StandardCharsets.UTF_8);
			var putResp = s3Request(client, "PUT", putUri, body, accessKey, secretKey);
			assertEquals(200, putResp.statusCode(),
				"PUT should succeed, got: " + putResp.body());

			var getUri = URI.create("%s/%s/test.txt".formatted(endpoint, bucket));
			var getResp = s3Request(client, "GET", getUri, new byte[0], accessKey, secretKey);
			assertEquals(200, getResp.statusCode());
			assertEquals("hello s3", getResp.body());
		}
	}

	@Test
	void sqsService() throws Exception {
		try (
			var env = Rig.up(
				"sqs-test",
				Map.of("queue", Rig.sqs()),
				Rig.withTimeout(TIMEOUT)
			)
		) {
			var ep = env.endpoint("queue");

			String endpoint = ep.attr("SQS_ENDPOINT");
			String queueUrl = ep.attr("SQS_QUEUE_URL");
			assertFalse(endpoint.isEmpty(), "SQS_ENDPOINT should be set");
			assertFalse(queueUrl.isEmpty(), "SQS_QUEUE_URL should be set");
			assertEquals("rig", ep.attr("AWS_ACCESS_KEY_ID"));
			assertEquals("rig", ep.attr("AWS_SECRET_ACCESS_KEY"));

			// SQS_QUEUE_URL points to the internal container address;
			// rewrite it to go through the proxy endpoint instead.
			String queuePath = URI.create(queueUrl).getPath();
			String proxyQueueUrl = endpoint + queuePath;

			// Send a message then receive it via the SQS HTTP query API.
			var client = HttpClient.newHttpClient();

			var send = HttpRequest.newBuilder()
				.uri(URI.create(proxyQueueUrl +
					"?Action=SendMessage&MessageBody=hello+sqs"))
				.timeout(Duration.ofSeconds(10))
				.GET()
				.build();
			var sendResp = client.send(send, HttpResponse.BodyHandlers.ofString());
			assertEquals(200, sendResp.statusCode(),
				"SendMessage should succeed, got: " + sendResp.body());

			var recv = HttpRequest.newBuilder()
				.uri(URI.create(proxyQueueUrl +
					"?Action=ReceiveMessage&MaxNumberOfMessages=1&WaitTimeSeconds=5"))
				.timeout(Duration.ofSeconds(10))
				.GET()
				.build();
			var recvResp = client.send(recv, HttpResponse.BodyHandlers.ofString());
			assertEquals(200, recvResp.statusCode());
			assertTrue(recvResp.body().contains("hello sqs"),
				"Should receive the sent message");
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

	/** Sends an S3 request with AWS SigV4 authentication. */
	private static HttpResponse<String> s3Request(
		HttpClient client, String method, URI uri, byte[] body,
		String accessKey, String secretKey
	) throws Exception {
		var now = ZonedDateTime.now(ZoneOffset.UTC);
		String amzDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
		String dateStamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		String region = "us-east-1";
		String service = "s3";

		String payloadHash = sha256Hex(body);
		String host = uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
		String canonicalHeaders = "host:" + host + "\n"
			+ "x-amz-content-sha256:" + payloadHash + "\n"
			+ "x-amz-date:" + amzDate + "\n";
		String signedHeaders = "host;x-amz-content-sha256;x-amz-date";

		String canonicalRequest = method + "\n"
			+ uri.getPath() + "\n"
			+ "\n"
			+ canonicalHeaders + "\n"
			+ signedHeaders + "\n"
			+ payloadHash;

		String scope = dateStamp + "/" + region + "/" + service + "/aws4_request";
		String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n"
			+ sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

		byte[] signingKey = hmacSha256(
			hmacSha256(
				hmacSha256(
					hmacSha256(
						("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8),
						dateStamp
					), region
				), service
			), "aws4_request"
		);
		String signature = HexFormat.of().formatHex(
			hmacSha256(signingKey, stringToSign)
		);

		String authorization = "AWS4-HMAC-SHA256 Credential=%s/%s, SignedHeaders=%s, Signature=%s"
			.formatted(accessKey, scope, signedHeaders, signature);

		var builder = HttpRequest.newBuilder()
			.uri(uri)
			.timeout(Duration.ofSeconds(10))
			.header("Authorization", authorization)
			.header("x-amz-date", amzDate)
			.header("x-amz-content-sha256", payloadHash);

		if ("PUT".equals(method)) {
			builder.PUT(HttpRequest.BodyPublishers.ofByteArray(body));
		} else {
			builder.GET();
		}

		return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static String sha256Hex(byte[] data) throws Exception {
		return HexFormat.of().formatHex(
			MessageDigest.getInstance("SHA-256").digest(data)
		);
	}

	private static byte[] hmacSha256(byte[] key, String data) throws Exception {
		var mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(key, "HmacSHA256"));
		return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
	}
}
