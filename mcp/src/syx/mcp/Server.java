package syx.mcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import syx.core.JsonText;
import syx.core.Operations;

/**
 * An MCP server over stdio: one JSON-RPC message to a line, and nothing else on stdout. It only passes
 * calls to {@link Operations}. It serves both kinds of client: those on the 2026-07-28 revision, whose
 * every request carries its version, and older ones that open with an initialize handshake.
 */
public final class Server {

	private static final String MODERN = "2026-07-28";
	/** Newest first. */
	private static final List<String> LEGACY = List.of("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05");
	private static final String VERSION_KEY = "io.modelcontextprotocol/protocolVersion";
	private static final String CAPABILITIES_KEY = "io.modelcontextprotocol/clientCapabilities";
	private static final String SERVER_INFO_KEY = "io.modelcontextprotocol/serverInfo";
	private static final int PARSE_ERROR = -32700;
	private static final int INVALID_REQUEST = -32600;
	private static final int METHOD_NOT_FOUND = -32601;
	private static final int INVALID_PARAMS = -32602;
	private static final int UNSUPPORTED_VERSION = -32022;

	private final Operations operations;
	private final InputStream in;
	private final Writer out;
	private final Set<String> cancelled = ConcurrentHashMap.newKeySet();
	/** Only for an initialize handshake. The newer revision has no connection state. */
	private volatile boolean initialized;

	public Server(Operations operations, InputStream in, OutputStream out) {
		this.operations = operations;
		this.in = in;
		this.out = new OutputStreamWriter(out, StandardCharsets.UTF_8);
	}

	/** Runs until the input ends, which is how a client says to stop. Calls run one at a time, in order. */
	public void run() throws IOException, InterruptedException {
		ExecutorService worker = Executors.newSingleThreadExecutor();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (!line.isBlank()) {
					String message = line;
					// Read here so a cancellation is seen while the call it cancels is running.
					Object parsed = parse(message);
					if (parsed instanceof Map<?, ?> map && "notifications/cancelled".equals(map.get("method"))) {
						if (map.get("params") instanceof Map<?, ?> params && params.get("requestId") != null) {
							cancelled.add(String.valueOf(params.get("requestId")));
						}
					} else {
						worker.execute(() -> handle(parsed));
					}
				}
			}
		} finally {
			worker.shutdown();
			// A save in progress finishes, so nothing is left half done.
			worker.awaitTermination(30, TimeUnit.SECONDS);
		}
	}

	private static final Object UNREADABLE = new Object();

	private static Object parse(String line) {
		try {
			return JsonText.parse(line);
		} catch (RuntimeException e) {
			return UNREADABLE;
		}
	}

	private void handle(Object parsed) {
		try {
			Map<String, Object> reply = reply(parsed);
			if (reply != null) {
				send(reply);
			}
		} catch (RuntimeException e) {
			// The server must keep going for whatever comes next.
			System.err.println("Unexpected failure: " + e);
		}
	}

	private Map<String, Object> reply(Object parsed) {
		if (parsed == UNREADABLE) {
			return error(null, PARSE_ERROR, "That wasn't valid JSON.", null);
		}
		if (!(parsed instanceof Map<?, ?> message) || !"2.0".equals(message.get("jsonrpc")) || !(message.get("method") instanceof String method)) {
			return error(parsed instanceof Map<?, ?> map ? map.get("id") : null, INVALID_REQUEST, "That wasn't a JSON-RPC 2.0 request.", null);
		}
		Object id = message.get("id");
		// A notification gets no reply, whatever it is.
		if (!message.containsKey("id")) {
			if (method.equals("notifications/initialized")) {
				initialized = true;
			}
			return null;
		}
		if (id == null) {
			return error(null, INVALID_REQUEST, "A request's id can't be null.", null);
		}
		Map<?, ?> params = message.get("params") instanceof Map<?, ?> map ? map : Map.of();
		Map<?, ?> meta = params.get("_meta") instanceof Map<?, ?> map ? map : Map.of();
		boolean modern = meta.containsKey(VERSION_KEY);
		if (method.equals("initialize")) {
			return initialize(id, params);
		}
		if (method.equals("ping")) {
			return result(id, new LinkedHashMap<>(), modern);
		}
		if (modern || method.equals("server/discover")) {
			Map<String, Object> rejected = checkModern(id, meta);
			if (rejected != null) {
				return rejected;
			}
			modern = true;
		} else if (!initialized) {
			return error(id, INVALID_PARAMS, "This request has no " + VERSION_KEY + " in its _meta, and no initialize handshake came first. Supported versions: " + supported(), null);
		}
		return switch (method) {
		case "server/discover" -> discover(id);
		case "tools/list" -> toolsList(id, modern);
		case "tools/call" -> toolsCall(id, params, modern);
		default -> error(id, METHOD_NOT_FOUND, "Unknown method: " + method, null);
		};
	}

	private static List<String> supported() {
		List<String> versions = new ArrayList<>();
		versions.add(MODERN);
		versions.addAll(LEGACY);
		return versions;
	}

	private static Map<String, Object> checkModern(Object id, Map<?, ?> meta) {
		if (!(meta.get(VERSION_KEY) instanceof String version) || !(meta.get(CAPABILITIES_KEY) instanceof Map<?, ?>)) {
			return error(id, INVALID_PARAMS, "A request needs " + VERSION_KEY + " and " + CAPABILITIES_KEY + " in its _meta.", null);
		}
		if (!version.equals(MODERN)) {
			Map<String, Object> data = new LinkedHashMap<>();
			data.put("supported", List.of(MODERN));
			data.put("requested", version);
			return error(id, UNSUPPORTED_VERSION, "Unsupported protocol version", data);
		}
		return null;
	}

	private static Map<String, Object> serverInfo() {
		Map<String, Object> info = new LinkedHashMap<>();
		info.put("name", "syx-toolkit");
		info.put("title", "Songs of Syx Modding Toolkit");
		info.put("version", Operations.VERSION);
		return info;
	}

	private static Map<String, Object> capabilities() {
		Map<String, Object> capabilities = new LinkedHashMap<>();
		capabilities.put("tools", new LinkedHashMap<>());
		return capabilities;
	}

	private Map<String, Object> initialize(Object id, Map<?, ?> params) {
		Object requested = params.get("protocolVersion");
		Map<String, Object> out = new LinkedHashMap<>();
		// The handshake's own rule: the same version if it's supported, or else the newest that is.
		out.put("protocolVersion", LEGACY.contains(requested) ? requested : LEGACY.get(0));
		out.put("capabilities", capabilities());
		out.put("serverInfo", serverInfo());
		out.put("instructions", Operations.GUIDE);
		return result(id, out, false);
	}

	private Map<String, Object> discover(Object id) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("supportedVersions", List.of(MODERN));
		out.put("capabilities", capabilities());
		out.put("instructions", Operations.GUIDE);
		return result(id, out, true);
	}

	private Map<String, Object> toolsList(Object id, boolean modern) {
		List<Object> tools = new ArrayList<>();
		for (Operations.Operation operation : operations.all()) {
			Map<String, Object> tool = new LinkedHashMap<>();
			tool.put("name", operation.name());
			tool.put("title", operation.title());
			tool.put("description", operation.description());
			tool.put("inputSchema", operation.inputSchema());
			Map<String, Object> annotations = new LinkedHashMap<>();
			annotations.put("title", operation.title());
			annotations.put("readOnlyHint", !operation.writes());
			if (operation.writes()) {
				annotations.put("destructiveHint", operation.overwrites());
			}
			annotations.put("openWorldHint", operation.network());
			tool.put("annotations", annotations);
			tools.add(tool);
		}
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("tools", tools);
		return result(id, out, modern);
	}

	private Map<String, Object> toolsCall(Object id, Map<?, ?> params, boolean modern) {
		if (!(params.get("name") instanceof String name)) {
			return error(id, INVALID_PARAMS, "tools/call needs a name.", null);
		}
		if (operations.all().stream().noneMatch(operation -> operation.name().equals(name)) && operations.find(name) == null) {
			return error(id, INVALID_PARAMS, "Unknown tool: " + name, null);
		}
		Map<String, Object> arguments = new LinkedHashMap<>();
		if (params.get("arguments") instanceof Map<?, ?> given) {
			given.forEach((key, value) -> arguments.put(String.valueOf(key), value));
		} else if (params.get("arguments") != null) {
			return error(id, INVALID_PARAMS, "A tool's arguments must be an object.", null);
		}
		String key = String.valueOf(id);
		Operations.Result result = operations.call(name, arguments, () -> cancelled.contains(key));
		// A cancelled request gets no further messages.
		if (cancelled.remove(key)) {
			return null;
		}
		Map<String, Object> payload = new LinkedHashMap<>(result.payload());
		Object png = payload.remove("png_base64");
		Map<String, Object> text = new LinkedHashMap<>();
		text.put("type", "text");
		text.put("text", JsonText.writePretty(payload));
		List<Object> content = new ArrayList<>();
		content.add(text);
		if (png != null) {
			Map<String, Object> image = new LinkedHashMap<>();
			image.put("type", "image");
			image.put("data", png);
			image.put("mimeType", "image/png");
			content.add(image);
		}
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("content", content);
		out.put("isError", result.outcome() != Operations.Outcome.OK);
		return result(id, out, modern);
	}

	private static Map<String, Object> result(Object id, Map<String, Object> result, boolean modern) {
		if (modern) {
			result.put("resultType", "complete");
			Map<String, Object> meta = new LinkedHashMap<>();
			meta.put(SERVER_INFO_KEY, serverInfo());
			result.put("_meta", meta);
		}
		Map<String, Object> reply = new LinkedHashMap<>();
		reply.put("jsonrpc", "2.0");
		reply.put("id", id);
		reply.put("result", result);
		return reply;
	}

	private static Map<String, Object> error(Object id, int code, String message, Object data) {
		Map<String, Object> error = new LinkedHashMap<>();
		error.put("code", code);
		error.put("message", message);
		if (data != null) {
			error.put("data", data);
		}
		Map<String, Object> reply = new LinkedHashMap<>();
		reply.put("jsonrpc", "2.0");
		reply.put("id", id);
		reply.put("error", error);
		return reply;
	}

	private synchronized void send(Map<String, Object> reply) {
		try {
			out.write(JsonText.write(reply));
			out.write('\n');
			out.flush();
		} catch (IOException e) {
			System.err.println("The client has gone: " + e);
		}
	}
}
