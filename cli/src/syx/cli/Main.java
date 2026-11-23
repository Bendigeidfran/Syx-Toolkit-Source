package syx.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import syx.core.GameFolderFinder;
import syx.core.HandbookStore;
import syx.core.JsonText;
import syx.core.ManifestStore;
import syx.core.ModsFolder;
import syx.core.Operations;
import syx.core.ToolkitSettings;
import syx.mcp.Server;

/**
 * The syx command. It only turns arguments into calls to {@link Operations}, so it can't give different
 * results from anything else. Exit codes: 0 clean or warnings only, 1 something blocking or refused,
 * 2 the tool itself failed.
 */
public final class Main {

	private static final int CLEAN = 0;
	private static final int ERRORS = 1;
	private static final int TOOL_FAILED = 2;

	private Main() {
	}

	public static void main(String[] args) {
		// The game's code logs to stdout, which belongs to our output, so it's kept aside before any of that loads.
		PrintStream output = new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, StandardCharsets.UTF_8);
		System.setOut(System.err);
		int code;
		try {
			code = run(args, output);
		} catch (RuntimeException | IOException e) {
			System.err.println("The tool failed: " + e);
			code = TOOL_FAILED;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			code = TOOL_FAILED;
		}
		output.flush();
		System.exit(code);
	}

	private static final class Global {
		Path game;
		Path mods;
		boolean readOnly;
		boolean json;
	}

	public static int run(String[] args, PrintStream out) throws IOException, InterruptedException {
		Global global = new Global();
		int at = 0;
		while (at < args.length && args[at].startsWith("--")) {
			switch (args[at]) {
			case "--game" -> global.game = Path.of(args[++at]);
			case "--mods" -> global.mods = Path.of(args[++at]);
			case "--settings" -> System.setProperty(ToolkitSettings.FILE_OVERRIDE_PROPERTY, args[++at]);
			case "--read-only" -> global.readOnly = true;
			case "--json" -> global.json = true;
			case "--help" -> {
				at = args.length;
				global = null;
			}
			default -> {
				System.err.println("Unknown option: " + args[at]);
				return ERRORS;
			}
			}
			if (global == null) {
				break;
			}
			at++;
		}
		Operations operations = operations(global == null ? new Global() : global);
		if (global == null || at >= args.length || args[at].equals("help")) {
			String topic = global != null && at + 1 < args.length ? args[at + 1] : null;
			return help(operations, topic, out);
		}
		String command = args[at];
		if (command.equals("mcp")) {
			// Anything after the command is also accepted, because that's where people expect a switch to go.
			Global after = readTrailing(args, at + 1, global);
			new Server(after.readOnly ? operations(after) : operations, System.in, out).run();
			return CLEAN;
		}
		Operations.Operation operation = operations.find(command);
		if (operation == null) {
			System.err.println("There's no command called " + command + ". syx help lists them.");
			return ERRORS;
		}
		Map<String, Object> input;
		try {
			input = input(operation, args, at + 1, global);
		} catch (IllegalArgumentException e) {
			System.err.println(e.getMessage());
			return ERRORS;
		}
		Operations.Result result = operations(global).call(command, input);
		if (global.json) {
			out.println(JsonText.writePretty(result.payload()));
		} else {
			text(result.payload(), "", out);
		}
		return switch (result.outcome()) {
		case FAILED -> TOOL_FAILED;
		case REFUSED -> ERRORS;
		case OK -> result.hasErrors() ? ERRORS : CLEAN;
		};
	}

	private static Global readTrailing(String[] args, int from, Global global) {
		for (int i = from; i < args.length; i++) {
			if (args[i].equals("--read-only")) {
				global.readOnly = true;
			} else {
				throw new IllegalArgumentException("Unknown option: " + args[i]);
			}
		}
		return global;
	}

	private static Operations operations(Global global) {
		ToolkitSettings settings = ToolkitSettings.openDefault();
		Path fixed = global.game;
		Supplier<Path> game = () -> {
			if (fixed != null) {
				return fixed;
			}
			Optional<Path> saved = settings.gameFolder();
			return saved.isPresent() ? saved.get() : GameFolderFinder.find().found().orElse(null);
		};
		return new Operations(game, global.mods != null ? global.mods : ModsFolder.standard(), settings, HandbookStore.openDefault(), ManifestStore.openDefault(), global.readOnly);
	}

	/** Each input is --name value. An object or list is JSON, as @file or @- for standard input. A switch on its own is true. */
	private static Map<String, Object> input(Operations.Operation operation, String[] args, int from, Global global) throws IOException {
		Map<String, Object> input = new LinkedHashMap<>();
		for (int i = from; i < args.length; i++) {
			String arg = args[i];
			if (arg.equals("--json")) {
				global.json = true;
				continue;
			}
			if (arg.equals("--read-only")) {
				global.readOnly = true;
				continue;
			}
			if (!arg.startsWith("--")) {
				throw new IllegalArgumentException("Expected an option, not: " + arg);
			}
			String name = arg.substring(2).replace('-', '_');
			Operations.Param param = operation.params().stream().filter(candidate -> candidate.name().equals(name)).findFirst()
					.orElseThrow(() -> new IllegalArgumentException(operation.name() + " has no option --" + name + ". syx help " + operation.name() + " lists them."));
			switch (param.type()) {
			case BOOLEAN -> input.put(name, !(i + 1 < args.length && args[i + 1].equals("false") && ++i > 0));
			case STRING -> input.put(name, next(args, ++i, arg));
			case OBJECT, ARRAY -> input.put(name, jsonValue(next(args, ++i, arg), arg));
			}
		}
		return input;
	}

	private static String next(String[] args, int at, String option) {
		if (at >= args.length) {
			throw new IllegalArgumentException(option + " needs a value.");
		}
		return args[at];
	}

	private static Object jsonValue(String value, String option) throws IOException {
		String text;
		if (value.equals("@-")) {
			text = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
		} else {
			text = value.startsWith("@") ? Files.readString(Path.of(value.substring(1)), StandardCharsets.UTF_8) : value;
		}
		try {
			return JsonText.parse(text);
		} catch (RuntimeException e) {
			// The packaged launcher strips quotes from its arguments, so JSON typed directly doesn't reach us whole.
			throw new IllegalArgumentException(option + " isn't valid JSON: " + e.getMessage() + ". syx.exe loses quotes from what's typed on its command line, so give JSON as @file, or as @- to read it from standard input.");
		}
	}

	private static int help(Operations operations, String topic, PrintStream out) {
		if (topic == null) {
			out.println("syx [--game FOLDER] [--mods FOLDER] [--read-only] [--json] COMMAND [--option value ...]");
			out.println("syx mcp [--read-only]    Runs the MCP server, for connecting an agent.");
			out.println();
			out.println("An object or list option takes JSON, as @file, or as @- to read standard input. Exit codes: 0 clean or warnings, 1 errors, 2 the tool failed.");
			out.println();
			for (Operations.Operation operation : operations.all()) {
				out.println(String.format("%-20s %s", operation.name(), operation.title() + (operation.writes() ? " (writes)" : "")));
			}
			return CLEAN;
		}
		Operations.Operation operation = operations.find(topic);
		if (operation == null) {
			System.err.println("There's no command called " + topic + ".");
			return ERRORS;
		}
		out.println(operation.name() + ": " + operation.description());
		for (Operations.Param param : operation.params()) {
			out.println("  --" + param.name() + (param.required() ? " (required)" : "") + "  " + param.description());
		}
		return CLEAN;
	}

	/** For people reading. Empty values are left out, and agents should use --json. */
	private static void text(Object value, String indent, PrintStream out) {
		if (value instanceof Map<?, ?> map) {
			map.forEach((key, item) -> {
				if (item instanceof Map<?, ?> || item instanceof List<?>) {
					if (!isEmpty(item)) {
						out.println(indent + key + ":");
						text(item, indent + "  ", out);
					}
				} else if (item != null) {
					out.println(indent + key + ": " + item);
				}
			});
		} else if (value instanceof List<?> list) {
			List<Object> items = new ArrayList<>(list);
			for (Object item : items) {
				if (item instanceof Map<?, ?> || item instanceof List<?>) {
					text(item, indent, out);
					out.println();
				} else {
					out.println(indent + item);
				}
			}
		}
	}

	private static boolean isEmpty(Object value) {
		return value instanceof Map<?, ?> map ? map.isEmpty() : ((List<?>) value).isEmpty();
	}
}
