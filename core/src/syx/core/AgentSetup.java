package syx.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * How a player connects their own agent. The agent, its account and its costs are the player's, so
 * nothing here holds a key or calls an AI service. It only produces text to copy, and never edits
 * another app's settings. Formats were checked against each client's documentation on 2026-11-03.
 */
public final class AgentSetup {

	public static final String SERVER_NAME = "syx-toolkit";
	public static final String LAUNCHER = "syx.exe";

	/**
	 * Setup for one client.
	 *
	 * @param where where the text goes, or what to do with it
	 * @param text what to copy
	 */
	public record Client(String name, String where, String text) {
	}

	private AgentSetup() {
	}

	/** The packaged command line beside the running app, or null when the app isn't packaged. */
	public static Path launcher() {
		String app = System.getProperty("jpackage.app-path");
		if (app == null) {
			return null;
		}
		Path launcher = Path.of(app).resolveSibling(LAUNCHER);
		return Files.isRegularFile(launcher) ? launcher : null;
	}

	public static List<Client> clients(Path launcher, boolean readOnly) {
		String path = launcher.toString();
		List<String> args = readOnly ? List.of("mcp", "--read-only") : List.of("mcp");
		String shellArgs = String.join(" ", args);
		List<Client> clients = new ArrayList<>();
		clients.add(new Client("Claude Code", "Run this in a terminal.", "claude mcp add --transport stdio --scope user " + SERVER_NAME + " -- \"" + path + "\" " + shellArgs));
		clients.add(new Client("Claude Desktop", "Add this to %APPDATA%\\Claude\\claude_desktop_config.json, under any servers already there, then restart the app.", json("mcpServers", path, args, false)));
		clients.add(new Client("Cursor", "Add this to ~/.cursor/mcp.json, or .cursor/mcp.json in a project.", json("mcpServers", path, args, true)));
		clients.add(new Client("Codex", "Run this in a terminal.", "codex mcp add " + SERVER_NAME + " -- \"" + path + "\" " + shellArgs));
		clients.add(new Client("VS Code", "Add this to .vscode/mcp.json in a workspace, or mcp.json in your user profile.", json("servers", path, args, true)));
		clients.add(new Client("Any other MCP client", "This is a local stdio server. Give your client this command.", "\"" + path + "\" " + shellArgs));
		clients.add(new Client("Agents without MCP", "Your agent can run the command line instead. Have it start with this, which explains how to work with the toolkit.", "\"" + path + "\" agent_guide"));
		return clients;
	}

	private static String json(String root, String path, List<String> args, boolean typed) {
		Map<String, Object> server = new LinkedHashMap<>();
		if (typed) {
			server.put("type", "stdio");
		}
		server.put("command", path);
		server.put("args", args);
		Map<String, Object> servers = new LinkedHashMap<>();
		servers.put(SERVER_NAME, server);
		Map<String, Object> config = new LinkedHashMap<>();
		config.put(root, servers);
		return JsonText.writePretty(config);
	}

	/** Runs the command line once, and says what came of it. */
	public static String test(Path launcher) {
		try {
			Process process = new ProcessBuilder(launcher.toString(), "status").redirectErrorStream(false).redirectError(ProcessBuilder.Redirect.DISCARD).start();
			process.getOutputStream().close();
			byte[] output = process.getInputStream().readAllBytes();
			if (!process.waitFor(60, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return "It didn't finish within a minute.";
			}
			String text = new String(output, StandardCharsets.UTF_8);
			return process.exitValue() == 0 ? "It works. It reports:\n" + text : "It ran, but reported a problem:\n" + text;
		} catch (IOException e) {
			return "It couldn't be started: " + e.getMessage();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return "The test was interrupted.";
		}
	}
}
