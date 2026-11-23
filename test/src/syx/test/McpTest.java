package syx.test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import syx.core.JsonText;

/**
 * The real packaged syx.exe, as a separate process, the way an agent's client runs it. It's not in
 * test.ps1, because it needs a packaged app: McpTest GAME_FOLDER PATH_TO_SYX_EXE.
 */
public final class McpTest {

	private static int passed;
	private static int failed;
	private static long id;
	private static Path exe;
	private static Path game;
	private static Path temp;

	private McpTest() {
	}

	private static final class Client implements AutoCloseable {
		final Process process;
		final BufferedWriter in;
		final BufferedReader out;
		final List<String> errors = new ArrayList<>();

		Client(String... extra) throws IOException {
			List<String> command = new ArrayList<>(List.of(exe.toString(), "--game", game.toString(), "--mods", temp.resolve("mods").toString(), "--settings", temp.resolve("settings.properties").toString(), "mcp"));
			command.addAll(List.of(extra));
			process = new ProcessBuilder(command).start();
			in = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
			out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
			Thread errorReader = new Thread(() -> {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
					reader.lines().forEach(errors::add);
				} catch (IOException e) {
					errors.add(e.toString());
				}
			});
			errorReader.setDaemon(true);
			errorReader.start();
		}

		@SuppressWarnings("unchecked")
		Map<String, Object> ask(String method, Map<String, Object> params) throws IOException {
			Map<String, Object> request = new LinkedHashMap<>();
			request.put("jsonrpc", "2.0");
			request.put("id", ++id);
			request.put("method", method);
			if (params != null) {
				request.put("params", params);
			}
			in.write(JsonText.write(request));
			in.write('\n');
			in.flush();
			String line = out.readLine();
			if (line == null) {
				throw new IOException("The server ended. " + errors);
			}
			Object parsed;
			try {
				parsed = JsonText.parse(line);
			} catch (RuntimeException e) {
				// Anything on stdout that isn't a message breaks a real client, which is the point of this test.
				expect("stdout holds only protocol messages, but had: " + line, false);
				throw e;
			}
			return (Map<String, Object>) parsed;
		}

		@SuppressWarnings("unchecked")
		Map<String, Object> tool(String name, Object... pairs) throws IOException {
			Map<String, Object> arguments = new LinkedHashMap<>();
			for (int i = 0; i < pairs.length; i += 2) {
				arguments.put((String) pairs[i], pairs[i + 1]);
			}
			Map<String, Object> params = new LinkedHashMap<>();
			params.put("name", name);
			params.put("arguments", arguments);
			Map<String, Object> reply = ask("tools/call", params);
			Map<String, Object> result = (Map<String, Object>) reply.get("result");
			Map<String, Object> payload = (Map<String, Object>) JsonText.parse((String) ((List<Map<String, Object>>) result.get("content")).get(0).get("text"));
			payload.put("__isError", result.get("isError"));
			return payload;
		}

		void handshake() throws IOException {
			Map<String, Object> params = new LinkedHashMap<>();
			params.put("protocolVersion", "2025-11-25");
			params.put("capabilities", new LinkedHashMap<>());
			params.put("clientInfo", Map.of("name", "McpTest", "version", "1"));
			expect("the handshake works", ask("initialize", params).get("result") != null);
			in.write("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n");
			in.flush();
		}

		@Override
		public void close() {
			process.destroyForcibly();
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.out.println("SKIPPED: this needs the game folder and the packaged syx.exe.");
			System.out.println("passed=0 failed=0");
			return;
		}
		game = Paths.get(args[0]);
		exe = Paths.get(args[1]);
		temp = Files.createTempDirectory("syx-mcp");
		Files.createDirectories(temp.resolve("mods"));
		try {
			conversation();
			shutdown();
			readOnly();
			commandLine();
		} finally {
			deleteFolder(temp);
		}
		System.out.println("passed=" + passed + " failed=" + failed);
		System.exit(failed == 0 ? 0 : 1);
	}

	@SuppressWarnings("unchecked")
	private static void conversation() throws Exception {
		try (Client client = new Client()) {
			client.handshake();
			List<Map<String, Object>> tools = (List<Map<String, Object>>) ((Map<String, Object>) client.ask("tools/list", null).get("result")).get("tools");
			expect("the tools are listed: " + tools.size(), tools.size() == 30);
			// Reading files loads game code, whose logging must not reach stdout.
			Map<String, Object> status = client.tool("status");
			expect("it finds the game: " + status, status.get("game_version") != null && status.get("game_problem") == null);
			expect("a mod can be created", Boolean.FALSE.equals(client.tool("create_mod", "name", "Through Exe").get("__isError")));
			expect("in the folder it was told to use", Files.isRegularFile(temp.resolve("mods/Through Exe/_Info.txt")));
			Map<String, Object> types = client.tool("list_asset_types");
			expect("the types come through", String.valueOf(types.get("types")).contains("Mine"));
			Map<String, Object> form = client.tool("new_asset_form", "mod", "Through Exe", "type", "Mine", "name", "DEEP", "detail", "full");
			expect("a whole form with non-ASCII safe text comes through intact: " + form.keySet(), form.get("tags") != null && form.get("starting_values") != null);
			Map<String, Object> deposit = new LinkedHashMap<>();
			deposit.put("RESOURCE", "STONE");
			deposit.put("ON_EVERY_MAP", "true");
			deposit.put("COLOR", "255_255_255");
			deposit.put("MINIMAP_COLOR", "10_20_30");
			deposit.put("FERTILITY_INCREASE", "0");
			deposit.put("SPRITE", "Clay");
			deposit.put("TERRAIN.MOUNTAIN", "0.5");
			Map<String, Object> created = client.tool("create_asset", "mod", "Through Exe", "type", "Minable resource", "name", "VIA_EXE", "values", deposit);
			expect("an asset is created: " + created, Boolean.FALSE.equals(created.get("__isError")));
			Map<String, Object> bad = client.tool("create_asset", "mod", "Through Exe", "type", "Minable resource", "name", "VIA_EXE", "values", deposit);
			expect("the same again is a refusal the agent can read: " + bad.get("refused"), Boolean.TRUE.equals(bad.get("__isError")) && bad.get("refused") != null);
			for (int i = 0; i < 3; i++) {
				expect("the server keeps answering", client.tool("status").get("game_version") != null);
			}
		}
	}

	private static void shutdown() throws Exception {
		Client client = new Client();
		client.handshake();
		long pid = client.process.pid();
		client.in.close();
		expect("it exits when its input closes, as the specification asks", client.process.waitFor(10, TimeUnit.SECONDS));
		expect("cleanly: " + client.process.exitValue() + " " + client.errors, client.process.exitValue() == 0);
		expect("and leaves nothing running", !ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));

		Client killed = new Client();
		killed.handshake();
		List<ProcessHandle> children = killed.process.descendants().toList();
		killed.process.destroyForcibly();
		killed.process.waitFor(10, TimeUnit.SECONDS);
		Thread.sleep(500);
		expect("a killed server leaves nothing running: " + children, children.stream().noneMatch(ProcessHandle::isAlive));
	}

	@SuppressWarnings("unchecked")
	private static void readOnly() throws Exception {
		try (Client client = new Client("--read-only")) {
			client.handshake();
			List<Map<String, Object>> tools = (List<Map<String, Object>>) ((Map<String, Object>) client.ask("tools/list", null).get("result")).get("tools");
			expect("read-only has no write tools: " + tools.size(), tools.size() == 22);
			expect("and a write is refused", Boolean.TRUE.equals(client.tool("create_mod", "name", "Blocked").get("__isError")) && !Files.exists(temp.resolve("mods/Blocked")));
		}
	}

	private static String run(int expectedExit, String... args) throws Exception {
		List<String> command = new ArrayList<>(List.of(exe.toString(), "--game", game.toString(), "--mods", temp.resolve("mods").toString(), "--settings", temp.resolve("settings.properties").toString()));
		command.addAll(List.of(args));
		Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
		process.getOutputStream().close();
		String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		expect(String.join(" ", args) + " exits with " + expectedExit + ": " + process.waitFor() + " " + (process.exitValue() == expectedExit ? "" : out), process.exitValue() == expectedExit);
		return out;
	}

	/** Runs it with this on standard input, and returns what it said on standard error. */
	private static String runError(int expectedExit, String input, String... args) throws Exception {
		List<String> command = new ArrayList<>(List.of(exe.toString(), "--game", game.toString(), "--mods", temp.resolve("mods").toString(), "--settings", temp.resolve("settings.properties").toString()));
		command.addAll(List.of(args));
		Process process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
		if (input != null) {
			process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
		}
		process.getOutputStream().close();
		String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
		expect(String.join(" ", args) + " exits with " + expectedExit + ": " + process.waitFor() + " " + error, process.exitValue() == expectedExit);
		return error;
	}

	private static void commandLine() throws Exception {
		expect("help lists the commands", run(0, "help").contains("create_asset"));
		expect("help describes one", run(0, "help", "save_asset").contains("--revision"));
		expect("text is readable", run(0, "status").contains("game_version:"));
		expect("--json is JSON", JsonText.parse(run(0, "--json", "status")) instanceof Map);
		expect("a refusal exits with 1", run(1, "--json", "get_mod_info", "--mod", "Nothing").contains("refused"));
		run(1, "no_such_command");
		run(1, "get_mod_info", "--nope", "1");
		Path values = temp.resolve("values.json");
		Files.writeString(values, "{\"RESOURCE\":\"STONE\",\"ON_EVERY_MAP\":\"true\",\"COLOR\":\"255_255_255\",\"MINIMAP_COLOR\":\"10_20_30\",\"FERTILITY_INCREASE\":\"0\",\"SPRITE\":\"Clay\",\"TERRAIN.MOUNTAIN\":\"0.5\"}");
		Map<?, ?> check = (Map<?, ?>) JsonText.parse(run(0, "--json", "check_asset", "--mod", "Through Exe", "--type", "Minable resource", "--name", "FROM_FILE", "--values", "@" + values));
		expect("values can come from a file: " + check, Boolean.TRUE.equals(check.get("can_save")));
		Files.writeString(values, "{\"RESOURCE\":\"NOT_REAL\"}");
		Map<?, ?> failing = (Map<?, ?>) JsonText.parse(run(1, "--json", "check_asset", "--mod", "Through Exe", "--type", "Minable resource", "--name", "FROM_FILE", "--values", "@" + values));
		expect("a blocking finding exits with 1: " + failing.get("summary"), Boolean.FALSE.equals(failing.get("can_save")));
		String deposit = "{\"RESOURCE\":\"STONE\",\"ON_EVERY_MAP\":\"true\",\"COLOR\":\"255_255_255\",\"MINIMAP_COLOR\":\"10_20_30\",\"FERTILITY_INCREASE\":\"0\",\"SPRITE\":\"Clay\",\"TERRAIN.MOUNTAIN\":\"0.5\"}";
		// The launcher strips quotes from its arguments, so typed JSON has to say what to do instead.
		expect("typed JSON that lost its quotes says to use a file", runError(1, null, "create_asset", "--mod", "Through Exe", "--type", "Minable resource", "--name", "TYPED", "--values", deposit).contains("@-"));
		runError(0, deposit, "--json", "create_asset", "--mod", "Through Exe", "--type", "Minable resource", "--name", "FROM_CLI", "--values", "@-");
		expect("the command line's write arrived", Files.exists(temp.resolve("mods/Through Exe")) && Files.walk(temp.resolve("mods/Through Exe")).anyMatch(path -> path.endsWith("FROM_CLI.txt")));
		run(1, "--read-only", "create_mod", "--name", "BlockedToo");
		expect("--read-only stops it", !Files.exists(temp.resolve("mods/BlockedToo")));
	}

	private static void deleteFolder(Path folder) throws IOException {
		if (!Files.exists(folder)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(folder)) {
			for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}

	private static void expect(String name, boolean ok) {
		if (ok) {
			passed++;
		} else {
			failed++;
			System.out.println("FAILED: " + name);
		}
	}
}
