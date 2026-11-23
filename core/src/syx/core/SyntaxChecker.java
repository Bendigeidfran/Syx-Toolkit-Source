package syx.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs every file in a folder that the game reads with its own parser through that parser. Read-only.
 * That's each .txt under assets/init, assets/text and assets/audio/config (PATHS.java:183,189,403), and a
 * mod's _Info.txt (PATHS.java:145). The .txt files under assets/sprite are notes the game doesn't parse.
 */
public final class SyntaxChecker {

	public static final String SCOPE_NOTE =
			"Syntax only. Every file the game parses is checked: the .txt files under assets/init, assets/text and assets/audio/config, and _Info.txt. "
					+ "The parser stops at the first error in each file, so fix and re-check. "
					+ "Errors inside [ ] lists aren't found yet.";

	/** The folders under assets whose .txt files the game parses (PATHS.java:183,189,403). */
	static final List<String> PARSED_FOLDERS = List.of("init", "text", "audio/config");

	private static final String INFO_FILE = "_Info.txt";

	/** A marker file for the layering system, so it's left out of this check. */
	private static final String IGNORE_VANILLA_FILE = "_ignorevanilla.txt";

	private static final Pattern PARSER_ERROR = Pattern.compile("(?s)^Error parsing line: (\\d+)\\. (.*)$");
	private static final Pattern UNCLOSED = Pattern.compile("^Expecting a close : (.) followed by a ',' after line (\\d+)$");
	private static final Pattern DUPLICATE = Pattern.compile("(?s)^Duplicate entry: (.*) at line \\d+$");

	/** A parser error, with its message rewritten and its line counted from 1. */
	record SyntaxProblem(Integer line, String message) {
	}

	public enum Status {
		COMPLETED, NOTHING_TO_CHECK, CANCELLED, GAME_UNAVAILABLE
	}

	public interface Listener {
		void onProgress(int done, int total, String file);
	}

	public record Result(
			Status status,
			Path selected,
			List<Path> foldersScanned,
			int filesFound,
			int filesChecked,
			int filesSkipped,
			List<Diagnostic> diagnostics) {

		public long errors() {
			return count(Diagnostic.Severity.ERROR);
		}

		public long toolFailures() {
			return count(Diagnostic.Severity.TOOL_FAILURE);
		}

		private long count(Diagnostic.Severity severity) {
			return diagnostics.stream().filter(d -> d.severity() == severity).count();
		}

		public String summary() {
			return switch (status) {
			case NOTHING_TO_CHECK -> "Nothing to check: no files the game parses were found in " + selected + ".";
			case GAME_UNAVAILABLE -> "The check didn't run, because the game's parser isn't available.";
			case CANCELLED -> "Cancelled after " + filesChecked + " of " + filesFound + " files the game parses. "
					+ errors() + " syntax errors found so far.";
			case COMPLETED -> {
				String text = "Checked " + filesChecked + " of " + filesFound + " files the game parses. "
						+ (errors() == 0 && toolFailures() == 0 ? "No syntax errors found." : errors() + " syntax errors found.");
				yield toolFailures() == 0 ? text
						: text + " " + toolFailures() + " problems stopped the tool checking everything.";
			}
			};
		}
	}

	private SyntaxChecker() {
	}

	public static Result check(Path selected, Path gameFolder, Listener listener, BooleanSupplier cancelled) {
		List<Diagnostic> diagnostics = new ArrayList<>();

		String installProblem = GameInstall.problemWith(gameFolder);
		if (installProblem != null) {
			diagnostics.add(Diagnostic.toolFailure(Diagnostic.GAME_JAR_MISSING, null, installProblem));
			return new Result(Status.GAME_UNAVAILABLE, selected, List.of(), 0, 0, 0, diagnostics);
		}
		if (selected == null || !Files.isDirectory(selected)) {
			return new Result(Status.NOTHING_TO_CHECK, selected, List.of(), 0, 0, 0, diagnostics);
		}

		List<Path> files = new ArrayList<>();
		List<Path> roots = findParsedFolders(selected, files, diagnostics);
		int skipped = 0;
		for (Path root : roots) {
			skipped += collectDataFiles(root, selected, files, diagnostics);
		}
		files.sort(Comparator.naturalOrder());
		if (files.isEmpty() && diagnostics.isEmpty()) {
			return new Result(Status.NOTHING_TO_CHECK, selected, roots, 0, 0, skipped, diagnostics);
		}

		int checked = 0;
		try (GameParser parser = GameParser.open(GameInstall.jarIn(gameFolder))) {
			for (int i = 0; i < files.size(); i++) {
				if (cancelled.getAsBoolean()) {
					return new Result(Status.CANCELLED, selected, roots, files.size(), checked, skipped, diagnostics);
				}
				Path file = files.get(i);
				String shown = display(selected, file);
				listener.onProgress(i, files.size(), shown);
				if (checkFile(parser, file, shown, diagnostics)) {
					checked++;
				}
			}
		} catch (GameParser.UnavailableException e) {
			diagnostics.add(Diagnostic.toolFailure(Diagnostic.GAME_PARSER_UNAVAILABLE, null, e.getMessage()));
			return new Result(Status.GAME_UNAVAILABLE, selected, roots, files.size(), 0, skipped, diagnostics);
		}
		listener.onProgress(files.size(), files.size(), "");
		return new Result(Status.COMPLETED, selected, roots, files.size(), checked, skipped, diagnostics);
	}

	/** Returns true if the parser gave a verdict on the file. */
	private static boolean checkFile(GameParser parser, Path file, String shown, List<Diagnostic> diagnostics) {
		try (InputStream in = Files.newInputStream(file)) {
			in.read();
		} catch (IOException e) {
			diagnostics.add(Diagnostic.toolFailure(Diagnostic.FILE_READ_FAILED, shown, "Couldn't read this file: " + e.getMessage()));
			return false;
		}
		GameParser.Outcome outcome = parser.parse(file);
		if (outcome.otherFailure() != null) {
			Throwable failure = outcome.otherFailure();
			diagnostics.add(Diagnostic.toolFailure(Diagnostic.PARSER_FAILED, shown,
					"The game's parser failed unexpectedly, which doesn't prove the file is wrong: "
							+ failure.getClass().getName() + ": " + failure.getMessage()));
			return false;
		}
		if (outcome.dataError() != null) {
			SyntaxProblem problem = translate(outcome.dataError());
			diagnostics.add(Diagnostic.syntaxError(shown, problem.line(), problem.message()));
		}
		return true;
	}

	/**
	 * Rewrites a parser error for modders. The game's line counter starts at 0
	 * (JsonValue.Reader.lineCurrent), so most lines need 1 added. An unrecognised message keeps
	 * no line, because its numbering isn't known.
	 */
	static SyntaxProblem translate(String error) {
		Matcher parsed = PARSER_ERROR.matcher(error);
		if (!parsed.matches()) {
			return new SyntaxProblem(null, "The game's parser rejected this file: " + error);
		}
		int counted = Integer.parseInt(parsed.group(1));
		String detail = parsed.group(2);

		// Thrown just after the counter passes the newline, so it already names the value's line.
		if (detail.equals("Expecting: ','")) {
			return new SyntaxProblem(counted, "Expected a ',' at the end of this line.");
		}
		// The game adds the whole file to this message, so none of it is passed on.
		if (detail.startsWith("Expecting: ',' after:")) {
			return new SyntaxProblem(counted + 1, "Expected a ',' after the last value in the file.");
		}
		if (detail.startsWith("Expecting a keyword followed by a ':'")) {
			return new SyntaxProblem(counted + 1, "Expected a tag name followed by ':', but its block or file ended first.");
		}
		if (detail.startsWith("unexpected end of file")) {
			return new SyntaxProblem(counted + 1, "The file ends unexpectedly.");
		}
		Matcher unclosed = UNCLOSED.matcher(detail);
		if (unclosed.matches()) {
			return new SyntaxProblem(Integer.parseInt(unclosed.group(2)) + 1,
					"The value opened on this line needs a closing " + unclosed.group(1) + " followed by a ','.");
		}
		Matcher duplicate = DUPLICATE.matcher(detail);
		if (duplicate.matches()) {
			return new SyntaxProblem(counted + 1,
					"Tag " + duplicate.group(1) + " is used twice in this block. The second one ends on this line.");
		}
		return new SyntaxProblem(null, "The game's parser rejected this file: " + detail);
	}

	/**
	 * Finds each folder the game parses: init, text and audio/config inside every assets folder, or the selected
	 * folder itself if it's inside one of those. A mod's _Info.txt, outside assets, is added to the files directly.
	 */
	private static List<Path> findParsedFolders(Path selected, List<Path> infoFiles, List<Diagnostic> diagnostics) {
		List<Path> roots = new ArrayList<>();
		try {
			Files.walkFileTree(selected, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
					if (dir.getFileName() != null && dir.getFileName().toString().equals("assets")) {
						for (String folder : PARSED_FOLDERS) {
							Path parsed = dir.resolve(folder);
							if (Files.isDirectory(parsed)) {
								roots.add(parsed);
							}
						}
						return FileVisitResult.SKIP_SUBTREE;
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					// One inside a parsed folder is collected with that folder's files.
					if (file.getFileName().toString().equals(INFO_FILE) && !insideParsedFolder(file.getParent())) {
						infoFiles.add(file);
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException e) {
					diagnostics.add(unreadable(selected, file, e));
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			diagnostics.add(unreadable(selected, selected, e));
		}
		if (roots.isEmpty() && insideParsedFolder(selected)) {
			roots.add(selected);
		}
		roots.sort(Comparator.naturalOrder());
		return roots;
	}

	private static int collectDataFiles(Path root, Path selected, List<Path> files, List<Diagnostic> diagnostics) {
		int[] skipped = { 0 };
		try {
			Files.walkFileTree(root, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
					if (name.equals(IGNORE_VANILLA_FILE)) {
						skipped[0]++;
					} else if (name.endsWith(".txt")) {
						files.add(file);
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException e) {
					diagnostics.add(unreadable(selected, file, e));
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			diagnostics.add(unreadable(selected, root, e));
		}
		return skipped[0];
	}

	private static Diagnostic unreadable(Path selected, Path path, IOException e) {
		return Diagnostic.toolFailure(Diagnostic.FILE_READ_FAILED, display(selected, path), "Couldn't read this: " + e.getMessage());
	}

	/** Whether a folder is inside assets/init, assets/text or assets/audio/config. */
	private static boolean insideParsedFolder(Path dir) {
		for (Path p = dir.toAbsolutePath(); p != null; p = p.getParent()) {
			Path parent = p.getParent();
			if (parent == null || parent.getFileName() == null) {
				continue;
			}
			String name = p.getFileName().toString();
			String above = parent.getFileName().toString();
			if (above.equals("assets") && (name.equals("init") || name.equals("text"))) {
				return true;
			}
			if (name.equals("config") && above.equals("audio") && parent.getParent() != null && parent.getParent().getFileName() != null
					&& parent.getParent().getFileName().toString().equals("assets")) {
				return true;
			}
		}
		return false;
	}

	private static String display(Path selected, Path file) {
		String relative = selected.relativize(file).toString().replace('\\', '/');
		return relative.isEmpty() ? file.toString() : relative;
	}
}
