package syx.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import syx.core.Diagnostic;
import syx.core.GameInstall;
import syx.core.SyntaxChecker;
import syx.core.ToolkitSettings;

/** Plain test runner. Every fixture is generated here, so no vanilla content is involved. */
public final class SyntaxCheckTest {

	private static int passed;
	private static int failed;

	private SyntaxCheckTest() {
	}

	public static void main(String[] args) throws IOException {
		Path temp = Files.createTempDirectory("syx-test");
		testSettings(temp);
		testMissingGame(temp);

		if (args.length == 0) {
			System.out.println("SKIPPED: parser tests, because no game folder was given.");
		} else {
			Path game = Paths.get(args[0]);
			testCleanMod(temp, game);
			testSyntaxErrors(temp, game);
			testLineNumbers(temp, game);
			testParserAccepts(temp, game);
			testNothingToCheck(temp, game);
			testEverythingParsed(temp, game);
			testVersionFolders(temp, game);
			testIgnoreVanillaSkipped(temp, game);
			testCancel(temp, game);
			testBadJar(temp);
		}
		System.out.println("passed=" + passed + " failed=" + failed);
		System.exit(failed == 0 ? 0 : 1);
	}

	private static void testSettings(Path temp) throws IOException {
		Path file = temp.resolve("settings/settings.properties");
		ToolkitSettings first = new ToolkitSettings(file);
		expect("no game folder at first", first.gameFolder().isEmpty());
		first.setGameFolder(temp);
		expect("game folder reloads", new ToolkitSettings(file).gameFolder().orElseThrow().equals(temp.toAbsolutePath()));
	}

	private static void testMissingGame(Path temp) throws IOException {
		Path noJar = Files.createDirectories(temp.resolve("not-game"));
		expect("folder without jar is invalid", !GameInstall.isValid(noJar));
		SyntaxChecker.Result r = run(temp, noJar);
		expect("missing jar stops the check", r.status() == SyntaxChecker.Status.GAME_UNAVAILABLE);
		expect("missing jar is reported", hasCode(r, Diagnostic.GAME_JAR_MISSING));
		expect("missing jar isn't a mod error", r.errors() == 0);
		SyntaxChecker.Result none = run(temp, null);
		expect("no game folder is reported", hasCode(none, Diagnostic.GAME_JAR_MISSING));
	}

	private static void testCleanMod(Path temp, Path game) throws IOException {
		Path mod = temp.resolve("clean");
		write(mod.resolve("V71/assets/init/race/A.txt"), "A: 1,\nBLOCK: {\n\tX: 2,\n},\n");
		SyntaxChecker.Result r = run(mod, game);
		expect("clean mod completes", r.status() == SyntaxChecker.Status.COMPLETED);
		expect("clean mod has one file", r.filesChecked() == 1);
		expect("clean mod has no diagnostics: " + r.diagnostics(), r.diagnostics().isEmpty());
	}

	private static void testSyntaxErrors(Path temp, Path game) throws IOException {
		Path mod = temp.resolve("broken");
		write(mod.resolve("V71/assets/init/race/GOOD.txt"), "A: 1,\n");
		write(mod.resolve("V71/assets/init/race/COMMA.txt"), "A: 1,\nB: 2,\nC: 3\nD: 4,\nE: 5,\n");
		write(mod.resolve("V71/assets/init/race/BLOCK.txt"), "A: 1,\nB: 2,\nBLOCK: {\n\tX: 1,\n");
		write(mod.resolve("V71/assets/init/race/DUPLICATE.txt"), "A: 1,\nB: 2,\nA: 3,\n");
		SyntaxChecker.Result r = run(mod, game);
		expect("all four files checked", r.filesChecked() == 4);
		expect("three errors: " + r.diagnostics(), r.errors() == 3);
		expect("good file isn't flagged", r.diagnostics().stream().noneMatch(d -> d.file().endsWith("GOOD.txt")));
		for (Diagnostic d : r.diagnostics()) {
			System.out.println("OBSERVED " + d.file() + " line=" + d.line() + " :: " + d.message());
			expect("syntax code on " + d.file(), d.code().equals(Diagnostic.SYNTAX_ERROR));
			expect("no absolute path in message: " + d.message(), !d.message().contains(mod.toString()));
		}
	}

	/** Each expected line was worked out from the game's source (JsonValue.java). */
	private static void testLineNumbers(Path temp, Path game) throws IOException {
		Path mod = temp.resolve("lines");
		Path init = mod.resolve("assets/init");
		String[][] cases = {
				{ "1", "COMMA_FIRST.txt", "A: 1\nB: 2,\n" },
				{ "3", "COMMA_MIDDLE.txt", "A: 1,\nB: 2,\nC: 3\nD: 4,\n" },
				{ "2", "COMMA_GAP.txt", "A: 1,\nB: 2\n\n\nC: 3,\n" },
				{ "2", "COMMA_END.txt", "A: 1,\nB: 2" },
				{ "2", "COMMA_CRLF.txt", "A: 1,\r\nB: 2\r\nC: 3,\r\n" },
				{ "2", "DUP_SECOND.txt", "A: 1,\nA: 2,\n" },
				{ "4", "DUP_LATER.txt", "A: 1,\nB: 2,\nC: 3,\nA: 4,\n" },
				{ "3", "DUP_CRLF.txt", "A: 1,\r\nB: 2,\r\nA: 3,\r\n" },
				{ "4", "DUP_BLOCK.txt", "A: 1,\nB: {\n\tX: 1,\n\tX: 2,\n},\n" },
				{ "5", "NOKEY_GAP.txt", "A: 1,\n\n\n\nB 2,\n" },
				{ "3", "NOKEY_BLOCK.txt", "A: 1,\nB: {\n\tX 1,\n},\n" },
				{ "3", "NOKEY_CRLF.txt", "A: 1,\r\nB: 2,\r\nC 3,\r\n" },
				{ "2", "UNCLOSED.txt", "A: 1,\nB: {\n\tX: 1,\n" },
				{ "2", "UNCLOSED_NO_COMMA.txt", "A: 1,\nB: {\n\tX: 1,\n}\nC: 2,\n" },
		};
		for (String[] c : cases) {
			write(init.resolve(c[1]), c[2]);
		}
		SyntaxChecker.Result r = run(mod, game);
		expect("every line case is rejected: " + r.diagnostics(), r.errors() == cases.length);
		for (String[] c : cases) {
			Integer line = lineOf(r, c[1]);
			expect(c[1] + " should point at line " + c[0] + ", got " + line, line != null && line == Integer.parseInt(c[0]));
		}
		for (Diagnostic d : r.diagnostics()) {
			expect("message is rewritten and has no file contents: " + d.message(),
					!d.message().contains("\n") && !d.message().contains("Error parsing"));
		}
	}

	/** The game's parser accepts these, so this check must too. Later checks can flag them. */
	private static void testParserAccepts(Path temp, Path game) throws IOException {
		Path mod = temp.resolve("accepted");
		Path init = mod.resolve("assets/init");
		write(init.resolve("KEY_SPANS_LINES.txt"), "A: 1,\nB 2,\nC: 3,\n");
		write(init.resolve("LIST_CONTENTS.txt"), "A: [\n{ X 1 },\n],\n");
		write(init.resolve("COMMENT.txt"), "** comment\nA: 1, ** another\n");
		SyntaxChecker.Result r = run(mod, game);
		expect("parser-accepted files aren't flagged: " + r.diagnostics(), r.diagnostics().isEmpty() && r.filesChecked() == 3);
	}

	private static void testNothingToCheck(Path temp, Path game) throws IOException {
		Path empty = temp.resolve("nothing");
		// The game doesn't parse the notes in assets/sprite (vanilla's sprite/game/_Note.txt isn't valid data).
		write(empty.resolve("V71/assets/sprite/game/_Note.txt"), "this is not parsed");
		SyntaxChecker.Result r = run(empty, game);
		expect("only files the game doesn't parse isn't clean", r.status() == SyntaxChecker.Status.NOTHING_TO_CHECK);
		expect("summary says nothing to check", r.summary().startsWith("Nothing to check"));
	}

	/** Text, sound config and _Info.txt are parsed by the game too, so a broken one stops it as surely as a data file. */
	private static void testEverythingParsed(Path temp, Path game) throws IOException {
		Path mod = temp.resolve("everything");
		// The game's parser accepts a missing comma after quoted text, so these are broken another way: a block left open.
		write(mod.resolve("_Info.txt"), "NAME: \"Everything\",\nINFO: {\n\tX: 1,\n");
		write(mod.resolve("V71/assets/init/race/A.txt"), "A: 1,\n");
		write(mod.resolve("V71/assets/text/race/A.txt"), "NAME: \"A\",\nINFO: {\n\tDESC: \"B\",\n");
		write(mod.resolve("V71/assets/audio/config/mono/A.txt"), "SOUND_A: x->y,\nSOUND_B: x->y\n");
		write(mod.resolve("V71/assets/sprite/game/_Note.txt"), "a note, not data");
		SyntaxChecker.Result r = run(mod, game);
		expect("init, text, audio config and _Info.txt are checked, and the sprite note isn't: " + r.filesChecked(), r.filesChecked() == 4);
		expect("a broken text file is an error: " + r.diagnostics(), r.diagnostics().stream().anyMatch(d -> d.file().endsWith("text/race/A.txt") && d.code().equals(Diagnostic.SYNTAX_ERROR)));
		expect("so is a broken sound config", r.diagnostics().stream().anyMatch(d -> d.file().endsWith("audio/config/mono/A.txt") && d.code().equals(Diagnostic.SYNTAX_ERROR)));
		expect("and a broken _Info.txt", r.diagnostics().stream().anyMatch(d -> d.file().endsWith("_Info.txt") && d.code().equals(Diagnostic.SYNTAX_ERROR)));
		expect("the good data file isn't flagged", r.diagnostics().stream().noneMatch(d -> d.file().endsWith("init/race/A.txt")));
		SyntaxChecker.Result inside = run(mod.resolve("V71/assets/text"), game);
		expect("a text folder can be checked on its own", inside.filesChecked() == 1 && inside.errors() == 1);
	}

	private static void testVersionFolders(Path temp, Path game) throws IOException {
		Path mod = temp.resolve("versions");
		write(mod.resolve("V64/assets/init/A.txt"), "A: 1,\n");
		write(mod.resolve("V71/assets/init/A.txt"), "A: 1,\n");
		SyntaxChecker.Result r = run(mod, game);
		expect("both version folders scanned", r.foldersScanned().size() == 2 && r.filesChecked() == 2);
	}

	private static void testIgnoreVanillaSkipped(Path temp, Path game) throws IOException {
		Path mod = temp.resolve("ignore");
		write(mod.resolve("V71/assets/init/race/_IgnoreVanilla.txt"), "not parser data");
		write(mod.resolve("V71/assets/init/race/A.txt"), "A: 1,\n");
		SyntaxChecker.Result r = run(mod, game);
		expect("_IgnoreVanilla.txt is skipped", r.filesSkipped() == 1 && r.filesChecked() == 1 && r.diagnostics().isEmpty());
	}

	private static void testCancel(Path temp, Path game) throws IOException {
		Path mod = temp.resolve("cancel");
		write(mod.resolve("V71/assets/init/A.txt"), "A: 1,\n");
		SyntaxChecker.Result r = SyntaxChecker.check(mod, game, (d, t, f) -> {
		}, () -> true);
		expect("cancel is reported as cancelled", r.status() == SyntaxChecker.Status.CANCELLED);
	}

	private static void testBadJar(Path temp) throws IOException {
		Path game = temp.resolve("bad-game");
		write(game.resolve(GameInstall.JAR_NAME), "not a jar");
		Path mod = temp.resolve("bad-jar-mod");
		write(mod.resolve("V71/assets/init/A.txt"), "A: 1,\n");
		SyntaxChecker.Result r = run(mod, game);
		expect("bad jar gives parser unavailable: " + r.diagnostics(), hasCode(r, Diagnostic.GAME_PARSER_UNAVAILABLE));
		expect("bad jar isn't a mod error", r.errors() == 0);
	}

	private static SyntaxChecker.Result run(Path folder, Path game) {
		return SyntaxChecker.check(folder, game, (d, t, f) -> {
		}, () -> false);
	}

	private static boolean hasCode(SyntaxChecker.Result r, String code) {
		return r.diagnostics().stream().anyMatch(d -> d.code().equals(code));
	}

	private static Integer lineOf(SyntaxChecker.Result r, String fileEnd) {
		List<Diagnostic> matches = r.diagnostics().stream().filter(d -> d.file().endsWith(fileEnd)).toList();
		return matches.isEmpty() ? null : matches.get(0).line();
	}

	private static void write(Path file, String text) throws IOException {
		Files.createDirectories(file.getParent());
		Files.write(file, text.getBytes(StandardCharsets.UTF_8));
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
