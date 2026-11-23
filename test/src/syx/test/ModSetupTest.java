package syx.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import syx.core.GameFolderFinder;
import syx.core.GameInstall;
import syx.core.GameParser;
import syx.core.ModCreator;
import syx.core.ModEntry;
import syx.core.ModLibrary;
import syx.core.ModsFolder;
import syx.core.ToolkitSettings;

/** Tests finding the game, and creating and listing mods. Everything is in temp folders. */
public final class ModSetupTest {

	private static int passed;
	private static int failed;

	private ModSetupTest() {
	}

	public static void main(String[] args) throws Exception {
		Path temp = Files.createTempDirectory("syx-setup");
		testFinder(temp);
		testModsFolder();
		testSettings(temp);
		testNames();
		if (args.length == 0) {
			System.out.println("SKIPPED: mod creation and listing tests, because no game folder was given.");
		} else {
			Path game = Paths.get(args[0]);
			testCreate(temp, game);
			testExistingFolderIsUntouched(temp, game);
			testUnusualNames(temp, game);
			testMissingModsFolder(temp, game);
			testBadGame(temp);
			testLibrary(temp, game);
		}
		System.out.println("passed=" + passed + " failed=" + failed);
		System.exit(failed == 0 ? 0 : 1);
	}

	private static void testFinder(Path temp) throws IOException {
		List<Path> libraries = GameFolderFinder.parseLibraryFolders("""
				"libraryfolders"
				{
					"0"
					{
						"path"		"C:\\\\Program Files (x86)\\\\Steam"
						"apps" { "228980" "1" }
					}
					"1"
					{
						"path"		"E:\\\\Some Library"
						"apps" { "1162750" "500350479" }
					}
				}
				""");
		expect("two libraries parsed: " + libraries, libraries.size() == 2);
		expect("escaped path is read: " + libraries, libraries.size() == 2 && libraries.get(1).equals(Paths.get("E:\\Some Library")));

		Path root = temp.resolve("finder");
		Path defaultSteam = root.resolve("DefaultSteam");
		Path otherLibrary = root.resolve("Other Library");
		Path otherGame = otherLibrary.resolve("steamapps/common/Songs of Syx");
		write(defaultSteam.resolve("steamapps/libraryfolders.vdf"),
				"\"path\"\t\t\"" + otherLibrary.toString().replace("\\", "\\\\") + "\"\n");
		write(otherGame.resolve(GameInstall.JAR_NAME), "x");

		GameFolderFinder.Result viaLibrary = GameFolderFinder.search(GameFolderFinder.windowsCandidates(defaultSteam, Optional.empty(), List.of()));
		expect("game found through a second library: " + viaLibrary.found(), viaLibrary.found().equals(Optional.of(otherGame)));

		Path defaultGame = defaultSteam.resolve("steamapps/common/Songs of Syx");
		write(defaultGame.resolve(GameInstall.JAR_NAME), "x");
		GameFolderFinder.Result preferred = GameFolderFinder.search(GameFolderFinder.windowsCandidates(defaultSteam, Optional.empty(), List.of()));
		expect("default Steam folder is preferred: " + preferred.found(), preferred.found().equals(Optional.of(defaultGame)));
		expect("default Steam folder is checked first", preferred.checked().size() == 1);

		Path drive = root.resolve("Drive");
		Path driveGame = drive.resolve("SteamLibrary/steamapps/common/Songs of Syx");
		write(driveGame.resolve(GameInstall.JAR_NAME), "x");
		GameFolderFinder.Result viaDrive = GameFolderFinder.search(
				GameFolderFinder.windowsCandidates(root.resolve("NoSteam"), Optional.empty(), List.of(drive)));
		expect("game found in a drive's SteamLibrary: " + viaDrive.found(), viaDrive.found().equals(Optional.of(driveGame)));

		GameFolderFinder.Result none = GameFolderFinder.search(
				GameFolderFinder.windowsCandidates(root.resolve("NoSteam"), Optional.empty(), List.of(root.resolve("NoDrive"))));
		expect("nothing found is reported", none.found().isEmpty());
		expect("places checked are listed: " + none.checked().size(), none.checked().size() >= 5);

		Path notGame = root.resolve("NoJar/steamapps/common/Songs of Syx");
		Files.createDirectories(notGame);
		GameFolderFinder.Result noJar = GameFolderFinder.search(
				GameFolderFinder.windowsCandidates(root.resolve("NoJar"), Optional.empty(), List.of()));
		expect("a folder without the jar doesn't count", noJar.found().isEmpty());
	}

	private static void testModsFolder() {
		String appData = System.getenv("AppData");
		if (appData != null) {
			expect("standard mods folder: " + ModsFolder.standard(), ModsFolder.standard().equals(Paths.get(appData, "songsofsyx", "mods")));
		}
	}

	private static void testSettings(Path temp) throws IOException {
		Path file = temp.resolve("old-settings/settings.properties");
		write(file, "gameFolder=C\\:\\\\Game\n");
		ToolkitSettings old = new ToolkitSettings(file);
		expect("an older settings file still loads", old.gameFolder().equals(Optional.of(Paths.get("C:\\Game"))));
		expect("an older settings file has no current mod", old.currentMod().isEmpty());
		old.setCurrentMod("My Mod");
		ToolkitSettings reloaded = new ToolkitSettings(file);
		expect("current mod reloads", reloaded.currentMod().equals(Optional.of("My Mod")));
		expect("game folder survives", reloaded.gameFolder().equals(Optional.of(Paths.get("C:\\Game"))));
		reloaded.clearCurrentMod();
		expect("current mod clears", new ToolkitSettings(file).currentMod().isEmpty());
		expect("the theme defaults to dark", new ToolkitSettings(file).theme() == ToolkitSettings.ThemeChoice.DARK);
		new ToolkitSettings(file).setTheme(ToolkitSettings.ThemeChoice.LIGHT);
		expect("the theme is saved", new ToolkitSettings(file).theme() == ToolkitSettings.ThemeChoice.LIGHT);
		Files.writeString(file, "theme=purple\n");
		expect("an unknown theme reads as dark", new ToolkitSettings(file).theme() == ToolkitSettings.ThemeChoice.DARK);
		Files.delete(file);
	}

	private static void testNames() {
		for (String bad : new String[] { "", "_Hidden", " Leading", "Trailing ", "Dot.", "CON", "con", "nul.txt", "COM1", "a/b", "a\\b", "a:b",
				"a*b", "a?b", "a\"b", "a<b", "a>b", "a|b", "a\tb", "a\nb", "x".repeat(ModCreator.MAX_NAME_LENGTH + 1) }) {
			expect("name is refused: [" + bad + "]", ModCreator.problemWithName(bad) != null);
		}
		for (String good : new String[] { "My Mod", "Deep Mines", "Mod-2_v1.5", "Modé", "A, B's {mod} [1]", "CONVERT", "x".repeat(ModCreator.MAX_NAME_LENGTH) }) {
			expect("name is allowed: [" + good + "]", ModCreator.problemWithName(good) == null);
		}
	}

	private static void testCreate(Path temp, Path game) throws Exception {
		Path mods = Files.createDirectories(temp.resolve("mods-create"));
		ModEntry mod = ModCreator.create(mods, game, "My Mod");
		int major;
		try (GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
			major = parser.version().major();
			expect("game version comes from the jar: " + parser.version(), major > 0);
			GameParser.TextValue name = parser.text(mods.resolve("My Mod/_Info.txt"), "NAME", null);
			expect("the game's parser reads NAME", "My Mod".equals(name.value()));
		}
		Path root = mods.resolve("My Mod");
		expect("entry points at the mod", mod.root().equals(root) && mod.folderName().equals("My Mod"));
		expect("new mod isn't outdated or broken: " + mod, !mod.outdated() && mod.problem() == null);
		expect("_Info.txt exists", Files.isRegularFile(root.resolve("_Info.txt")));
		for (String folder : List.of("init", "text", "sprite", "audio")) {
			expect("assets folder exists: " + folder, Files.isDirectory(root.resolve("V" + major + "/assets/" + folder)));
		}
		try (Stream<Path> all = Files.walk(root)) {
			expect("nothing extra is created", all.count() == 8);
		}
		String info = Files.readString(root.resolve("_Info.txt"));
		expect("_Info.txt records the version: " + info, info.contains("GAME_VERSION_MAJOR: " + major + ","));
		for (String key : List.of("NAME: \"My Mod\",", "DESC: \"\",", "AUTHOR: \"\",", "VERSION: \"1.0\",", "INFO: \"\",", "GAME_VERSION_MINOR: ")) {
			expect("_Info.txt has " + key, info.contains(key));
		}
	}

	private static void testExistingFolderIsUntouched(Path temp, Path game) throws Exception {
		Path mods = Files.createDirectories(temp.resolve("mods-existing"));
		Path existing = mods.resolve("Taken");
		byte[] original = "NAME: \"Mine\",\n".getBytes(StandardCharsets.UTF_8);
		Files.createDirectories(existing);
		Files.write(existing.resolve("_Info.txt"), original);
		expectFailure("an existing folder is refused", () -> ModCreator.create(mods, game, "Taken"));
		expect("existing file is byte-identical", java.util.Arrays.equals(original, Files.readAllBytes(existing.resolve("_Info.txt"))));
		try (Stream<Path> all = Files.list(existing)) {
			expect("existing folder has nothing added", all.count() == 1);
		}
		expectFailure("an existing folder is refused whatever its case", () -> ModCreator.create(mods, game, "TAKEN"));
		try (Stream<Path> all = Files.list(mods)) {
			expect("no new folder appears", all.count() == 1);
		}
	}

	private static void testUnusualNames(Path temp, Path game) throws Exception {
		Path mods = Files.createDirectories(temp.resolve("mods-unusual"));
		for (String name : List.of("A, B's {mod} [1]", "Modé ñ")) {
			ModEntry mod = ModCreator.create(mods, game, name);
			expect("unusual name reads back: " + mod.displayName(), mod.displayName().equals(name));
		}
	}

	private static void testMissingModsFolder(Path temp, Path game) throws Exception {
		Path mods = temp.resolve("not-yet/songsofsyx/mods");
		ModLibrary.Listing empty = ModLibrary.list(mods, game);
		expect("a missing mods folder lists no mods", empty.mods().isEmpty() && empty.problem() == null);
		expect("listing doesn't create the folder", !Files.exists(mods));
		ModCreator.create(mods, game, "First");
		expect("creating a mod creates the mods folder", Files.isRegularFile(mods.resolve("First/_Info.txt")));
	}

	private static void testBadGame(Path temp) throws Exception {
		Path badGame = temp.resolve("bad-game");
		write(badGame.resolve(GameInstall.JAR_NAME), "not a jar");
		Path mods = temp.resolve("mods-bad");
		expectFailure("a bad game jar stops creation", () -> ModCreator.create(mods, badGame, "Mod"));
		expect("a failure leaves nothing behind", !Files.exists(mods));
		expectFailure("no game folder stops creation", () -> ModCreator.create(mods, null, "Mod"));
		expect("still nothing left behind", !Files.exists(mods));

		Path fileInTheWay = temp.resolve("file-in-the-way");
		write(fileInTheWay, "x");
		expectFailure("an unusable mods folder fails", () -> ModCreator.create(fileInTheWay.resolve("mods"), badGame, "Mod"));
	}

	private static void testLibrary(Path temp, Path game) throws Exception {
		Path mods = Files.createDirectories(temp.resolve("mods-library"));
		int major;
		try (GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
			major = parser.version().major();
		}
		mod(mods, "Current", "NAME: \"Current Mod\",\n", "V" + major);
		mod(mods, "Old", "NAME: \"Old Mod\",\n", "V" + (major - 5));
		mod(mods, "Both", "NAME: \"Both Mod\",\n", "V" + (major - 5), "V" + major);
		mod(mods, "Broken", "NAME: \"Broken\n", "V" + major);
		mod(mods, "NoName", "DESC: \"x\",\n", "V" + major);
		mod(mods, "NoVersion", "NAME: \"No Version\",\n");
		mod(mods, "_Skipped", "NAME: \"Skipped\",\n", "V" + major);
		Files.createDirectories(mods.resolve("NoInfo/V" + major));
		write(mods.resolve("a-file.txt"), "not a mod");

		ModLibrary.Listing listing = ModLibrary.list(mods, game);
		expect("listing has no problem: " + listing.problem(), listing.problem() == null);
		expect("folders and _ names are filtered: " + listing.mods().size(), listing.mods().size() == 6 + 1);
		expect("_Skipped isn't listed", entry(listing, "_Skipped") == null);
		ModEntry current = entry(listing, "Current");
		expect("current mod is fine: " + current, !current.outdated() && current.problem() == null && current.displayName().equals("Current Mod"));
		expect("old mod is outdated", entry(listing, "Old").outdated() && entry(listing, "Old").describe().contains("Outdated"));
		expect("a mod with the current folder too isn't outdated", !entry(listing, "Both").outdated());
		expect("broken _Info.txt is still listed", entry(listing, "Broken").problem() != null);
		expect("broken mod falls back on its folder name", entry(listing, "Broken").displayName().equals("Broken"));
		expect("no NAME falls back on the folder name", entry(listing, "NoName").displayName().equals("NoName"));
		expect("no version folder is reported", entry(listing, "NoVersion").problem() != null);
		expect("no _Info.txt is reported", entry(listing, "NoInfo").problem() != null);

		expect("find returns a saved mod", ModLibrary.find(mods, game, "Current").isPresent());
		expect("find returns nothing for a gone mod", ModLibrary.find(mods, game, "Gone").isEmpty());
		expect("find can't leave the mods folder", ModLibrary.find(mods, game, "../mods-create").isEmpty());
	}

	private static ModEntry entry(ModLibrary.Listing listing, String folder) {
		return listing.mods().stream().filter(m -> m.folderName().equals(folder)).findFirst().orElse(null);
	}

	private static void mod(Path mods, String name, String info, String... versions) throws IOException {
		write(mods.resolve(name).resolve("_Info.txt"), info);
		for (String version : versions) {
			Files.createDirectories(mods.resolve(name).resolve(version));
		}
	}

	private static void write(Path file, String text) throws IOException {
		Files.createDirectories(file.getParent());
		Files.write(file, text.getBytes(StandardCharsets.UTF_8));
	}

	private interface Action {
		void run() throws Exception;
	}

	private static void expectFailure(String name, Action action) {
		try {
			action.run();
			expect(name + " (nothing was refused)", false);
		} catch (ModCreator.CreationException e) {
			System.out.println("OBSERVED " + name + ": " + e.getMessage());
			expect(name, true);
		} catch (Exception e) {
			expect(name + " (wrong failure: " + e + ")", false);
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
