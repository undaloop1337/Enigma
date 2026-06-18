package cuchaz.enigma.command.mcp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class McpHttpServer {
	private static final long SESSION_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(30);

	private final InetSocketAddress address;
	private final McpJsonRpcHandler handler;
	private final Map<String, SseSession> sessions = new ConcurrentHashMap<>();
	private HttpServer server;
	private ScheduledExecutorService sessionCleaner;

	public McpHttpServer(String host, int port, List<McpTool> tools) {
		this.address = new InetSocketAddress(host, port);
		this.handler = new McpJsonRpcHandler(tools);
	}

	public synchronized void start() throws IOException {
		if (server != null) {
			return;
		}

		server = HttpServer.create(address, 0);
		server.createContext("/health", this::handleHealth);
		server.createContext("/mcp", this::handleMcp);
		server.createContext("/sse", this::handleSse);
		server.createContext("/message", this::handleMessage);
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();

		sessionCleaner = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "MCP-SSE-Cleaner");
			t.setDaemon(true);
			return t;
		});
		sessionCleaner.scheduleAtFixedRate(this::cleanStaleSessions, 5, 5, TimeUnit.MINUTES);
	}

	public synchronized void stop() {
		if (server == null) {
			return;
		}

		if (sessionCleaner != null) {
			sessionCleaner.shutdownNow();
			sessionCleaner = null;
		}

		for (SseSession session : sessions.values()) {
			session.close();
		}

		server.stop(0);
		server = null;
		sessions.clear();
	}

	private void cleanStaleSessions() {
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<String, SseSession>> it = sessions.entrySet().iterator();

		while (it.hasNext()) {
			SseSession session = it.next().getValue();

			if (session.isClosed() || now - session.getLastActivityTime() > SESSION_TIMEOUT_MS) {
				session.close();
				it.remove();
			}
		}
	}

	public int getPort() {
		return address.getPort();
	}

	public String getStreamableHttpUrl() {
		return "http://" + address.getHostString() + ":" + address.getPort() + "/mcp";
	}

	public String getSseUrl() {
		return "http://" + address.getHostString() + ":" + address.getPort() + "/sse";
	}

	private void handleHealth(HttpExchange exchange) throws IOException {
		JsonObject health = new JsonObject();
		health.addProperty("status", "ok");
		health.addProperty("tools", handler.getToolCount());
		health.addProperty("mcp", getStreamableHttpUrl());
		health.addProperty("sse", getSseUrl());
		writeJson(exchange, 200, health.toString());
	}

	private void handleMcp(HttpExchange exchange) throws IOException {
		if (!exchange.getRequestMethod().equals("POST")) {
			writeText(exchange, 405, "Method Not Allowed");
			return;
		}

		String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		JsonObject response = handler.handle(request);
		writeJson(exchange, 200, response == null ? "{}" : handler.toJson(response));
	}

	private void handleSse(HttpExchange exchange) throws IOException {
		if (!exchange.getRequestMethod().equals("GET")) {
			writeText(exchange, 405, "Method Not Allowed");
			return;
		}

		String session = UUID.randomUUID().toString();
		exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
		exchange.getResponseHeaders().add("Cache-Control", "no-cache");
		exchange.getResponseHeaders().add("Connection", "keep-alive");
		exchange.sendResponseHeaders(200, 0);

		OutputStream body = exchange.getResponseBody();
		SseSession sse = new SseSession(body);
		sessions.put(session, sse);
		sse.write("endpoint", "/message?session=" + session);
	}

	private void handleMessage(HttpExchange exchange) throws IOException {
		if (!exchange.getRequestMethod().equals("POST")) {
			writeText(exchange, 405, "Method Not Allowed");
			return;
		}

		String session = getQuery(exchange, "session");
		SseSession sse = sessions.get(session);

		if (sse == null) {
			writeText(exchange, 404, "Unknown session");
			return;
		}

		String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		JsonObject response = handler.handle(request);

		if (response != null) {
			try {
				sse.write("message", handler.toJson(response));
			} catch (IOException e) {
				sessions.remove(session);
				sse.close();
			}
		}

		writeText(exchange, 202, "Accepted");
	}

	private static String getQuery(HttpExchange exchange, String name) {
		String query = exchange.getRequestURI().getRawQuery();

		if (query == null) {
			return null;
		}

		for (String part : query.split("&")) {
			String[] split = part.split("=", 2);

			if (split.length == 2 && split[0].equals(name)) {
				return split[1];
			}
		}

		return null;
	}

	private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
		exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
		write(exchange, status, body);
	}

	private static void writeText(HttpExchange exchange, int status, String body) throws IOException {
		exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
		write(exchange, status, body);
	}

	private static void write(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.sendResponseHeaders(status, bytes.length);

		try (OutputStream stream = exchange.getResponseBody()) {
			stream.write(bytes);
		}
	}

	private static class SseSession {
		private final OutputStream stream;
		private volatile long lastActivityTime;
		private volatile boolean closed;

		SseSession(OutputStream stream) {
			this.stream = stream;
			this.lastActivityTime = System.currentTimeMillis();
		}

		synchronized void write(String event, String data) throws IOException {
			if (closed) {
				throw new IOException("Session is closed");
			}

			stream.write(("event: " + event + "\n").getBytes(StandardCharsets.UTF_8));
			stream.write(("data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
			stream.flush();
			lastActivityTime = System.currentTimeMillis();
		}

		long getLastActivityTime() {
			return lastActivityTime;
		}

		boolean isClosed() {
			return closed;
		}

		synchronized void close() {
			if (closed) {
				return;
			}

			closed = true;

			try {
				stream.close();
			} catch (IOException ignored) {
			}
		}
	}
}
