package syx.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import syx.core.GameInstall;
import syx.core.GameParser;
import syx.core.ModAssets;
import syx.core.ModEntry;
import syx.core.ModInfoField;
import syx.core.ModInfoFile;
import syx.core.ModLibrary;
import syx.core.ModsFolder;

/** Tests the _Info.txt editor and the asset tree. Real files are only read, and copied before any edit. */
public final class ModInfoTest {

	private static int passed;
	private static int failed;
	private static int counter;

	private ModInfoTest() {
	}

	public static void main(String[] args) throws Exception {
		Path temp = Files.createTempDirectory("syx-info");
		testValidation();
		testAssets(temp);
		if (args.length == 0) {
			System.out.println("SKIPPED: _Info.txt editing tests, because no game folder was given.");
		} else {
			Path game = Paths.get(args[0]);
			try (GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
				testPreservation(temp, parser);
				testAdding(temp, parser);
				testAwkwardFiles(temp, parser);
				testRemoving(temp, parser);
				testRefusals(temp, parser);
				testTextValues(temp, parser);
				testRealFiles(temp, parser, game);
				testListingAfterSave(temp, parser, game);
			}
		}
		System.out.println("passed=" + passed + " failed=" + failed);
		System.exit(failed == 0 ? 0 : 1);
	}

	private static void testValidation() {
		String[][] good = {
				{ "NAME", "My Mod" }, { "NAME", "A \"quoted\" word" }, { "NAME", "Modé" }, { "AUTHOR", "" }, { "VERSION", "1.2-beta" },
				{ "DESC", "" }, { "DESC", "Line one\nLine two" }, { "DESC", "ends with a quote\"" }, { "INFO", "https://example.com/a,b" },
				{ "TEXTURE_CACHE_SIZE", "4096" }, { "TEXTURE_CACHE_SIZE", "8192" }, { "TEXTURE_CACHE_SIZE", "16384" },
				{ "GAME_VERSION_MAJOR", "71" }, { "GAME_VERSION_MINOR", "0" }, { "GAME_VERSION_MINOR", "65535" } };
		for (String[] c : good) {
			String problem = ModInfoField.valueOf(c[0]).problemWith(c[1]);
			expect(c[0] + " accepts [" + c[1] + "]: " + problem, problem == null);
		}
		String[][] bad = {
				{ "NAME", "" }, { "NAME", "   " }, { "NAME", "a\nb" }, { "NAME", "a\",b" }, { "NAME", "a\tb" }, { "NAME", "a%r%b" },
				{ "AUTHOR", "a\nb" }, { "VERSION", "1\",2" }, { "DESC", "a\n\nb" }, { "DESC", "a\",b" }, { "DESC", "a\tb" }, { "INFO", "a\u0001b" },
				{ "TEXTURE_CACHE_SIZE", "" }, { "TEXTURE_CACHE_SIZE", "2048" }, { "TEXTURE_CACHE_SIZE", "32768" }, { "TEXTURE_CACHE_SIZE", "5000" },
				{ "TEXTURE_CACHE_SIZE", "4096 " }, { "GAME_VERSION_MAJOR", "" }, { "GAME_VERSION_MAJOR", "-1" }, { "GAME_VERSION_MAJOR", "+1" },
				{ "GAME_VERSION_MAJOR", "1.5" }, { "GAME_VERSION_MINOR", "65536" }, { "GAME_VERSION_MINOR", "abc" }, { "GAME_VERSION_MINOR", "999999" } };
		for (String[] c : bad) {
			expect(c[0] + " refuses [" + c[1] + "]", ModInfoField.valueOf(c[0]).problemWith(c[1]) != null);
		}
	}

	private static void testPreservation(Path temp, GameParser parser) throws Exception {
		String original = "** My comment at the top\r\n"
				+ "VERSION :   \"1\" ,\r\n".replace("\" ,", "\",")
				+ "NAME: \"Old\", ** trailing comment\r\n\r\n"
				+ "EXTRA_TAG: 5,\r\n"
				+ "SOME_BLOCK: {\r\n\tX: 1,\r\n\tNAME: \"inner\",\r\n},\r\n"
				+ "SOME_LIST: [A, B,],\r\n"
				+ "DESC:\t\"Keep me\",\r\n"
				+ "GAME_VERSION_MAJOR: 71,\r\n";
		Path mod = modWith(temp, original);
		ModInfoFile file = ModInfoFile.load(mod, parser);
		expect("file order is kept", keys(file).equals(List.of("VERSION", "NAME", "EXTRA_TAG", "SOME_BLOCK", "SOME_LIST", "DESC", "GAME_VERSION_MAJOR")));

		Map<ModInfoField, String> values = current(file);
		expect("no change is no change", !file.hasChanges(values));
		expect("saving no changes writes nothing", !file.save(values, parser));
		expect("the file is untouched", read(mod).equals(original));

		values.put(ModInfoField.NAME, "New Name");
		expect("a change is noticed", file.hasChanges(values));
		expect("saving a change writes", file.save(values, parser));
		expect("only that value changed: " + read(mod), read(mod).equals(original.replace("\"Old\"", "\"New Name\"")));
		expectNoTempFiles(mod);

		ModInfoFile second = ModInfoFile.load(mod, parser);
		Map<ModInfoField, String> number = current(second);
		number.put(ModInfoField.GAME_VERSION_MAJOR, "72");
		second.save(number, parser);
		expect("a plain value changes in place", read(mod).equals(original.replace("\"Old\"", "\"New Name\"").replace("MAJOR: 71", "MAJOR: 72")));

		ModInfoFile third = ModInfoFile.load(mod, parser);
		Map<ModInfoField, String> lines = current(third);
		lines.put(ModInfoField.DESC, "One\nTwo");
		third.save(lines, parser);
		expect("line breaks use the file's style", read(mod).contains("DESC:\t\"One\r\nTwo\","));
		ModInfoFile fourth = ModInfoFile.load(mod, parser);
		expect("line breaks read back", valueOf(fourth, "DESC").equals("One\nTwo"));
	}

	private static void testAdding(Path temp, GameParser parser) throws Exception {
		String original = "NAME: \"A\",\nDESC: \"\",\n";
		Path mod = modWith(temp, original);
		ModInfoFile file = ModInfoFile.load(mod, parser);
		expect("missing fields are offered: " + file.missingFields(), file.missingFields().size() == ModInfoField.values().length - 2);
		expect("present fields aren't offered", !file.missingFields().contains(ModInfoField.NAME));

		Map<ModInfoField, String> values = current(file);
		values.put(ModInfoField.AUTHOR, "Me");
		values.put(ModInfoField.TEXTURE_CACHE_SIZE, "8192");
		file.save(values, parser);
		String after = read(mod);
		expect("added fields go at the end: " + after, after.startsWith(original) && after.contains("AUTHOR: \"Me\",\n") && after.contains("TEXTURE_CACHE_SIZE: 8192,\n"));
		ModInfoFile reloaded = ModInfoFile.load(mod, parser);
		expect("added fields read back", valueOf(reloaded, "AUTHOR").equals("Me") && valueOf(reloaded, "TEXTURE_CACHE_SIZE").equals("8192"));

		Path crlf = modWith(temp, "NAME: \"A\",\r\n");
		ModInfoFile crlfFile = ModInfoFile.load(crlf, parser);
		Map<ModInfoField, String> crlfValues = current(crlfFile);
		crlfValues.put(ModInfoField.AUTHOR, "Me");
		crlfFile.save(crlfValues, parser);
		expect("added fields use the file's line endings", read(crlf).equals("NAME: \"A\",\r\nAUTHOR: \"Me\",\r\n"));

		GameParser.GameVersion version = parser.version();
		expect("new version fields use the jar's version", ModInfoField.GAME_VERSION_MAJOR.defaultValue("x", version).equals(String.valueOf(version.major())));
		expect("a new name defaults to the folder", ModInfoField.NAME.defaultValue("Folder", version).equals("Folder"));
		expect("a new texture size is valid", ModInfoField.TEXTURE_CACHE_SIZE.problemWith(ModInfoField.TEXTURE_CACHE_SIZE.defaultValue("x", version)) == null);
	}

	private static void testAwkwardFiles(Path temp, GameParser parser) throws Exception {
		// No comma after the last value, at the very end of the file.
		Path noComma = modWith(temp, "NAME: \"A\",\nDESC: \"Last\"");
		ModInfoFile file = ModInfoFile.load(noComma, parser);
		Map<ModInfoField, String> values = current(file);
		values.put(ModInfoField.AUTHOR, "Me");
		file.save(values, parser);
		ModInfoFile reloaded = ModInfoFile.load(noComma, parser);
		expect("a missing last comma is fixed when adding", valueOf(reloaded, "DESC").equals("Last") && valueOf(reloaded, "AUTHOR").equals("Me"));

		// A last line with a comment and no line break.
		Path comment = modWith(temp, "NAME: \"A\", ** note");
		ModInfoFile commentFile = ModInfoFile.load(comment, parser);
		Map<ModInfoField, String> commentValues = current(commentFile);
		commentValues.put(ModInfoField.AUTHOR, "Me");
		commentFile.save(commentValues, parser);
		expect("adding after a final comment works", valueOf(ModInfoFile.load(comment, parser), "AUTHOR").equals("Me") && read(comment).contains("** note"));

		// No file at all.
		Path missing = temp.resolve("missing-" + counter++);
		Files.createDirectories(missing);
		ModInfoFile none = ModInfoFile.load(missing, parser);
		expect("a missing file has no fields, and offers them all", none.fields().isEmpty() && none.missingFields().size() == ModInfoField.values().length);
		Map<ModInfoField, String> created = new LinkedHashMap<>();
		created.put(ModInfoField.NAME, "Brand New");
		expect("saving creates a missing file", none.save(created, parser));
		expect("the created file reads back", valueOf(ModInfoFile.load(missing, parser), "NAME").equals("Brand New"));

		// Forms the game would reject are shown, and fixed on save.
		Path unquoted = modWith(temp, "NAME: Bare,\nTEXTURE_CACHE_SIZE: \"8192\",\nGAME_VERSION_MAJOR: {X: 1,},\n");
		ModInfoFile wrong = ModInfoFile.load(unquoted, parser);
		expect("wrong forms are noticed: " + wrong.fields(), wrong.fields().stream().allMatch(ModInfoFile.Field::wrongForm));
		Map<ModInfoField, String> fixes = current(wrong);
		expect("a wrong form counts as a change", wrong.hasChanges(fixes));
		expect("a block can't pass as a number", wrong.problemWith(ModInfoField.GAME_VERSION_MAJOR, "") != null);
		fixes.put(ModInfoField.GAME_VERSION_MAJOR, "71");
		wrong.save(fixes, parser);
		expect("saving fixes the forms: " + read(unquoted), read(unquoted).equals("NAME: \"Bare\",\nTEXTURE_CACHE_SIZE: 8192,\nGAME_VERSION_MAJOR: 71,\n"));

		// An invalid existing value in the right form doesn't block other edits, but can't be saved if changed.
		Path badChoice = modWith(temp, "NAME: \"A\",\nTEXTURE_CACHE_SIZE: 5000,\n");
		ModInfoFile choice = ModInfoFile.load(badChoice, parser);
		expect("an untouched value isn't a problem", choice.problemWith(ModInfoField.TEXTURE_CACHE_SIZE, "5000") == null);
		expect("changing to another bad value is", choice.problemWith(ModInfoField.TEXTURE_CACHE_SIZE, "6000") != null);
	}

	/** Removes tags, then checks the exact bytes left behind. */
	private static void expectRemoval(Path temp, GameParser parser, String before, Set<String> removals, String after) throws Exception {
		Path mod = modWith(temp, before);
		ModInfoFile file = ModInfoFile.load(mod, parser);
		Map<ModInfoField, String> values = current(file);
		expect("removal counts as a change: " + removals, file.hasChanges(values, removals));
		expect("removal saves: " + removals, file.save(values, removals, parser));
		expect("removing " + removals + " leaves [" + after + "], got [" + read(mod) + "]", read(mod).equals(after));
		expectNoTempFiles(mod);
		ModInfoFile.load(mod, parser);
	}

	private static void testRemoving(Path temp, GameParser parser) throws Exception {
		expect("only NAME can't be removed", Arrays.stream(ModInfoField.values()).filter(f -> !f.removable()).toList().equals(List.of(ModInfoField.NAME)));

		// Defaults from ModInfo.java, and none for the fields the game doesn't read.
		for (ModInfoField text : List.of(ModInfoField.DESC, ModInfoField.AUTHOR, ModInfoField.VERSION, ModInfoField.INFO)) {
			expect(text + " defaults to ???", "???".equals(text.gameDefault()));
			GameParser.TextValue read = parser.text(modWith(temp, "OTHER: 1,\n").resolve("_Info.txt"), text.name(), text.gameDefault());
			expect(text + " absent reads as its default", "???".equals(read.value()));
		}
		expect("TEXTURE_CACHE_SIZE defaults to 4096", "4096".equals(ModInfoField.TEXTURE_CACHE_SIZE.gameDefault()));
		expect("unread fields have no default", ModInfoField.GAME_VERSION_MAJOR.gameDefault() == null && ModInfoField.GAME_VERSION_MINOR.gameDefault() == null);

		expectRemoval(temp, parser, "NAME: \"A\",\nDESC: \"B\",\nAUTHOR: \"C\",\n", Set.of("DESC"), "NAME: \"A\",\nAUTHOR: \"C\",\n");
		expectRemoval(temp, parser, "NAME: \"A\",\r\nDESC: \"B\",\r\nAUTHOR: \"C\",\r\n", Set.of("DESC"), "NAME: \"A\",\r\nAUTHOR: \"C\",\r\n");
		expectRemoval(temp, parser, "DESC: \"B\",\nNAME: \"A\",\n", Set.of("DESC"), "NAME: \"A\",\n");
		expectRemoval(temp, parser, "NAME: \"A\",\nDESC: \"B\",\n", Set.of("DESC"), "NAME: \"A\",\n");
		expectRemoval(temp, parser, "NAME: \"A\",\nDESC: \"B\"", Set.of("DESC"), "NAME: \"A\",\n");
		expectRemoval(temp, parser, "NAME: \"A\",\n\tDESC: \"B\",\n", Set.of("DESC"), "NAME: \"A\",\n");
		expectRemoval(temp, parser, "NAME: \"A\",\nGAME_VERSION_MAJOR: 71,\n", Set.of("GAME_VERSION_MAJOR"), "NAME: \"A\",\n");
		expectRemoval(temp, parser, "NAME: \"A\",\nDESC: \"One\nTwo\",\nAUTHOR: \"C\",\n", Set.of("DESC"), "NAME: \"A\",\nAUTHOR: \"C\",\n");
		// Several tags at once, and everything but NAME.
		expectRemoval(temp, parser, "A1: 1,\nNAME: \"A\",\nDESC: \"B\",\nAUTHOR: \"C\",\n", Set.of("A1", "DESC", "AUTHOR"), "NAME: \"A\",\n");
		// Tags sharing a line, comments and neighbours are kept.
		expectRemoval(temp, parser, "NAME: \"A\", DESC: \"B\",\n", Set.of("DESC"), "NAME: \"A\", \n");
		expectRemoval(temp, parser, "DESC: \"B\", NAME: \"A\",\n", Set.of("DESC"), "NAME: \"A\",\n");
		expectRemoval(temp, parser, "NAME: \"A\",\nDESC: \"B\", ** my note\nAUTHOR: \"C\",\n", Set.of("DESC"), "NAME: \"A\",\n** my note\nAUTHOR: \"C\",\n");
		expectRemoval(temp, parser, "** top\nNAME: \"A\",\n** about desc\nDESC: \"B\",\n", Set.of("DESC"), "** top\nNAME: \"A\",\n** about desc\n");
		// Unknown tags and blocks can go too.
		expectRemoval(temp, parser, "NAME: \"A\",\nEXTRA: 5,\nBLOCK: {\n\tX: 1,\n},\nLIST: [A, B,],\nDESC: \"B\",\n", Set.of("EXTRA", "BLOCK", "LIST"), "NAME: \"A\",\nDESC: \"B\",\n");
		// Removing and adding together, including when the removed tag had no comma.
		Path both = modWith(temp, "NAME: \"A\",\nDESC: \"B\"");
		ModInfoFile bothFile = ModInfoFile.load(both, parser);
		Map<ModInfoField, String> bothValues = current(bothFile);
		bothValues.put(ModInfoField.AUTHOR, "C");
		bothFile.save(bothValues, Set.of("DESC"), parser);
		expect("removing and adding together: " + read(both), read(both).equals("NAME: \"A\",\nAUTHOR: \"C\",\n"));
		// A tag being removed wins over a value for it.
		Path wins = modWith(temp, "NAME: \"A\",\nDESC: \"B\",\n");
		ModInfoFile winsFile = ModInfoFile.load(wins, parser);
		Map<ModInfoField, String> winsValues = current(winsFile);
		winsValues.put(ModInfoField.DESC, "bad\",value");
		winsFile.save(winsValues, Set.of("DESC"), parser);
		expect("removal wins, and an invalid value for it doesn't matter", read(wins).equals("NAME: \"A\",\n"));
		// A removed field is offered again.
		expect("a removed field can be added back", ModInfoFile.load(wins, parser).missingFields().contains(ModInfoField.DESC));
		// NAME is refused, and the file left alone.
		String named = "NAME: \"A\",\nDESC: \"B\",\n";
		Path keep = modWith(temp, named);
		ModInfoFile keepFile = ModInfoFile.load(keep, parser);
		try {
			keepFile.save(current(keepFile), Set.of("NAME"), parser);
			expect("removing NAME should be refused", false);
		} catch (ModInfoFile.InfoFileException e) {
			expect("removing NAME is refused", read(keep).equals(named));
		}
		expectNoTempFiles(keep);
		// A file left with no tags is still one the game reads.
		Path emptied = modWith(temp, "DESC: \"B\",\n");
		ModInfoFile emptiedFile = ModInfoFile.load(emptied, parser);
		emptiedFile.save(current(emptiedFile), Set.of("DESC"), parser);
		expect("removing the last tag leaves an empty file the game reads", read(emptied).isEmpty() && ModInfoFile.load(emptied, parser).fields().isEmpty());
	}

	private static void testRefusals(Path temp, GameParser parser) throws Exception {
		for (String[] c : new String[][] {
				{ "a duplicate tag", "NAME: \"A\",\nNAME: \"B\",\n" },
				{ "an unclosed value", "NAME: \"A\nDESC: \"B\n" },
				{ "a missing comma", "NAME: \"A\",\nGAME_VERSION_MAJOR: 71\nDESC: \"B\",\n" } }) {
			Path mod = modWith(temp, c[1]);
			expectLoadRefused(c[0], mod, parser);
			expect(c[0] + " leaves the file alone", read(mod).equals(c[1]));
		}

		Path latin = temp.resolve("latin-" + counter++);
		Files.createDirectories(latin);
		byte[] notUtf8 = "NAME: \"Café\",\n".getBytes(StandardCharsets.ISO_8859_1);
		Files.write(latin.resolve("_Info.txt"), notUtf8);
		expectLoadRefused("a file that isn't UTF-8", latin, parser);
		expect("a non-UTF-8 file is untouched", Arrays.equals(notUtf8, Files.readAllBytes(latin.resolve("_Info.txt"))));

		// An invalid value is refused by save itself, not only by the screen.
		String original = "NAME: \"A\",\n";
		Path mod = modWith(temp, original);
		ModInfoFile file = ModInfoFile.load(mod, parser);
		Map<ModInfoField, String> bad = current(file);
		bad.put(ModInfoField.NAME, "a\",b");
		expectSaveRefused("an invalid value", file, bad, parser);
		expect("a refused save leaves the file byte-identical", read(mod).equals(original));
		expectNoTempFiles(mod);

		// The file changed on disk after it was opened.
		Path changed = modWith(temp, original);
		ModInfoFile stale = ModInfoFile.load(changed, parser);
		String edited = "NAME: \"Edited elsewhere\",\n";
		Files.write(changed.resolve("_Info.txt"), edited.getBytes(StandardCharsets.UTF_8));
		Map<ModInfoField, String> values = current(stale);
		values.put(ModInfoField.NAME, "Mine");
		expectSaveRefused("a file changed elsewhere", stale, values, parser);
		expect("the other edit is kept", read(changed).equals(edited));
		expectNoTempFiles(changed);

		// A missing file that appears after opening isn't overwritten.
		Path appears = temp.resolve("appears-" + counter++);
		Files.createDirectories(appears);
		ModInfoFile noneYet = ModInfoFile.load(appears, parser);
		Files.write(appears.resolve("_Info.txt"), edited.getBytes(StandardCharsets.UTF_8));
		Map<ModInfoField, String> create = new LinkedHashMap<>();
		create.put(ModInfoField.NAME, "Mine");
		expectSaveRefused("a file that appeared", noneYet, create, parser);
		expect("the file that appeared is kept", read(appears).equals(edited));
	}

	/** Every value the validator allows must come back from the game's parser exactly. */
	private static void testTextValues(Path temp, GameParser parser) throws Exception {
		for (String value : List.of("Plain", "", "A \"quoted\" word", "ends with a quote\"", "\"starts with a quote", "\"", "\"\"",
				"a, b, c", "{ block? }", "[ list? ]", "key: value", "** not a comment", "Modé ñ 日本語", "One\nTwo\nThree", "\nleading line", "trailing line\n")) {
			Path mod = modWith(temp, "NAME: \"A\",\nDESC: \"Old\",\nAUTHOR: \"Keep\",\n");
			ModInfoFile file = ModInfoFile.load(mod, parser);
			Map<ModInfoField, String> values = current(file);
			values.put(ModInfoField.DESC, value);
			String problem = file.problemWith(ModInfoField.DESC, value);
			expect("allowed: [" + value + "] " + problem, problem == null);
			try {
				file.save(values, parser);
				ModInfoFile reloaded = ModInfoFile.load(mod, parser);
				expect("reads back exactly: [" + value + "] got [" + valueOf(reloaded, "DESC") + "]", valueOf(reloaded, "DESC").equals(value));
				expect("next field is safe: [" + value + "]", valueOf(reloaded, "AUTHOR").equals("Keep"));
			} catch (ModInfoFile.InfoFileException e) {
				expect("save worked for [" + value + "]: " + e.getMessage(), false);
			}
		}
	}

	private static void testRealFiles(Path temp, GameParser parser, Path game) throws Exception {
		List<Path> infos = new ArrayList<>();
		collectInfoFiles(ModsFolder.standard(), infos);
		Path steamapps = game.getParent() == null || game.getParent().getParent() == null ? null : game.getParent().getParent();
		if (steamapps != null) {
			collectInfoFiles(steamapps.resolve("workshop").resolve("content").resolve("1162750"), infos);
		}
		Path example = Paths.get("Source", "Game Source", "Mod Source", "_Info.txt");
		if (Files.isRegularFile(example)) {
			infos.add(example);
		}
		System.out.println("real _Info.txt files tried: " + infos.size());
		if (infos.isEmpty()) {
			System.out.println("SKIPPED: real files, because none were found.");
		}
		for (Path info : infos) {
			String original = Files.readString(info);
			Path mod = modWith(temp, original);
			ModInfoFile file = ModInfoFile.load(mod, parser);
			expect("real file has fields: " + info, !file.fields().isEmpty());
			Map<ModInfoField, String> values = current(file);
			values.put(ModInfoField.AUTHOR, "Edited Author");
			file.save(values, parser);
			ModInfoFile reloaded = ModInfoFile.load(mod, parser);
			expect("real file edit reads back: " + info, valueOf(reloaded, "AUTHOR").equals("Edited Author"));
			for (ModInfoFile.Field before : file.fields()) {
				if (!before.key().equals("AUTHOR")) {
					expect(info + " keeps " + before.key(), valueOf(reloaded, before.key()).equals(before.value()));
				}
			}
			expect("only the author's bytes changed in " + info, read(mod).replace("Edited Author", "").length()
					== original.length() - valueLength(file, "AUTHOR"));
		}
	}

	private static void testListingAfterSave(Path temp, GameParser parser, Path game) throws Exception {
		Path mods = Files.createDirectories(temp.resolve("mods-" + counter++));
		Path mod = Files.createDirectories(mods.resolve("Folder"));
		Files.createDirectories(mod.resolve("V" + parser.version().major()));
		Files.write(mod.resolve("_Info.txt"), "NAME: \"Before\",\n".getBytes(StandardCharsets.UTF_8));
		ModInfoFile file = ModInfoFile.load(mod, parser);
		Map<ModInfoField, String> values = current(file);
		values.put(ModInfoField.NAME, "After");
		file.save(values, parser);
		ModEntry entry = ModLibrary.find(mods, game, "Folder").orElseThrow();
		expect("the library sees the new name", entry.displayName().equals("After"));
		expect("the folder name is unchanged", entry.folderName().equals("Folder") && entry.problem() == null);
	}

	private static void testAssets(Path temp) throws IOException {
		Path mod = temp.resolve("assets-mod");
		write(mod.resolve("_Info.txt"), "x");
		write(mod.resolve("icon.ico"), "x");
		write(mod.resolve("V71/assets/init/race/ZED.txt"), "x");
		write(mod.resolve("V71/assets/init/race/alpha.txt"), "x");
		Files.createDirectories(mod.resolve("V71/assets/init/race/sprite"));
		Files.createDirectories(mod.resolve("V71/assets/audio"));
		write(mod.resolve("V64/assets/init/OLD.txt"), "x");
		Files.createDirectories(mod.resolve("Vnot-a-version"));
		Files.createDirectories(mod.resolve("assets"));

		ModAssets.Listing listing = ModAssets.read(mod, 71);
		expect("only version folders are read: " + listing.versions(), listing.versions().size() == 2);
		expect("the current version comes first", listing.versions().get(0).folderName().equals("V71") && listing.versions().get(0).current());
		expect("labels are right", listing.versions().get(0).label().equals("V71 (current version)") && listing.versions().get(1).label().equals("V64 (outdated)"));
		expect("files are counted without stray ones: " + listing.files(), listing.files() == 3);
		ModAssets.Node assets = listing.versions().get(0).root().children().get(0);
		expect("empty folders are kept", assets.children().get(0).name().equals("audio") && assets.children().get(0).folder() && assets.children().get(0).children().isEmpty());
		ModAssets.Node race = assets.children().get(1).children().get(0);
		expect("folders come first, then files ignoring case", race.children().stream().map(ModAssets.Node::name).toList().equals(List.of("sprite", "alpha.txt", "ZED.txt")));
		expect("nothing was unreadable", listing.unreadable().isEmpty());
		expect("an unknown game version marks nothing current", ModAssets.read(mod, -1).versions().stream().noneMatch(ModAssets.VersionTree::current));

		Path empty = Files.createDirectories(temp.resolve("assets-empty"));
		ModAssets.Listing none = ModAssets.read(empty, 71);
		expect("a mod with no version folder has no trees", none.versions().isEmpty() && none.files() == 0);

		Path example = Paths.get("Source", "Game Source", "Mod Source");
		if (Files.isDirectory(example)) {
			ModAssets.Listing real = ModAssets.read(example, 71);
			System.out.println("example mod: versions=" + real.versions().size() + " files=" + real.files());
			expect("the example mod's real tree is read", real.versions().size() == 1 && real.files() == 479 && real.unreadable().isEmpty());
		}
	}

	private static int valueLength(ModInfoFile file, String key) {
		return file.fields().stream().filter(f -> f.key().equals(key)).findFirst().map(f -> f.value().length()).orElse(0);
	}

	private static void collectInfoFiles(Path folder, List<Path> infos) throws IOException {
		if (!Files.isDirectory(folder)) {
			return;
		}
		try (Stream<Path> mods = Files.list(folder)) {
			for (Path mod : mods.toList()) {
				Path info = mod.resolve("_Info.txt");
				if (Files.isRegularFile(info)) {
					infos.add(info);
				}
			}
		}
	}

	private static Map<ModInfoField, String> current(ModInfoFile file) {
		Map<ModInfoField, String> values = new LinkedHashMap<>();
		for (ModInfoFile.Field field : file.fields()) {
			if (field.known() != null) {
				values.put(field.known(), field.value());
			}
		}
		return values;
	}

	private static List<String> keys(ModInfoFile file) {
		return file.fields().stream().map(ModInfoFile.Field::key).toList();
	}

	private static String valueOf(ModInfoFile file, String key) {
		return file.fields().stream().filter(f -> f.key().equals(key)).findFirst().map(ModInfoFile.Field::value).orElse("<missing>");
	}

	private static Path modWith(Path temp, String info) throws IOException {
		Path mod = temp.resolve("mod-" + counter++);
		Files.createDirectories(mod);
		Files.write(mod.resolve("_Info.txt"), info.getBytes(StandardCharsets.UTF_8));
		return mod;
	}

	private static String read(Path mod) throws IOException {
		return Files.readString(mod.resolve("_Info.txt"));
	}

	private static void write(Path file, String text) throws IOException {
		Files.createDirectories(file.getParent());
		Files.write(file, text.getBytes(StandardCharsets.UTF_8));
	}

	private static void expectLoadRefused(String name, Path mod, GameParser parser) {
		try {
			ModInfoFile.load(mod, parser);
			expect(name + " should be refused", false);
		} catch (ModInfoFile.InfoFileException e) {
			System.out.println("OBSERVED " + name + ": " + e.getMessage());
			expect(name + " is refused", true);
		}
	}

	private static void expectSaveRefused(String name, ModInfoFile file, Map<ModInfoField, String> values, GameParser parser) {
		try {
			file.save(values, parser);
			expect(name + " should be refused", false);
		} catch (ModInfoFile.InfoFileException e) {
			System.out.println("OBSERVED " + name + ": " + e.getMessage());
			expect(name + " is refused", true);
		}
	}

	private static void expectNoTempFiles(Path mod) throws IOException {
		try (Stream<Path> files = Files.list(mod)) {
			expect("no temp files are left", files.count() == 1);
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
