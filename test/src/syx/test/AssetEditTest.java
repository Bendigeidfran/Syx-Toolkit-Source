package syx.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import javax.swing.SwingUtilities;

import syx.core.AssetCatalog;
import syx.core.AssetChecker;
import syx.core.AssetCreator;
import syx.core.AssetEditing;
import syx.core.AssetForm;
import syx.core.GameAssetIndex;
import syx.core.GameInstall;
import syx.core.GameParser;
import syx.core.GameTree;
import syx.core.HandbookStore;
import syx.core.ModCreator;
import syx.core.ModEntry;
import syx.core.SafeWrite;
import syx.core.SoundEntries;
import syx.core.SpriteCopy;
import syx.core.ToolkitSettings;
import syx.gui.AddAssetScreen;
import syx.gui.AssetEditorScreen;
import syx.gui.Theme;

/** Editing existing assets: nothing the form can't show is lost, and missing files are only ever added. */
public final class AssetEditTest {

	private static int passed;
	private static int failed;

	private static Path game;
	private static GameParser parser;
	private static GameParser.GameVersion version;
	private static List<AssetCatalog.Entry> entries;
	private static Path mods;

	private AssetEditTest() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			System.out.println("SKIPPED: no game folder given.");
			System.out.println("passed=0 failed=0");
			return;
		}
		game = Paths.get(args[0]);
		Path temp = Files.createTempDirectory("syx-edit-test");
		mods = temp.resolve("mods");
		SwingUtilities.invokeAndWait(() -> Theme.install(ToolkitSettings.ThemeChoice.DARK));
		try (GameParser opened = GameParser.open(GameInstall.jarIn(game))) {
			parser = opened;
			version = parser.version();
			entries = AssetCatalog.load(GameInstall.jarIn(game));
			gameTreeRoundTrip(temp);
			vanillaThroughTheForm();
			valuesNotOffered();
			preservation();
			edits();
			droppedTags();
			safety();
			generatingText();
			generatingSounds();
			cropRules();
			refusals();
			comments();
			mines();
			copiedRaces();
			overrides();
			vanillaInitialise();
			copyVanillaSprites();
		} finally {
			deleteFolder(temp);
		}
		System.out.println("passed=" + passed + " failed=" + failed);
		System.exit(failed == 0 ? 0 : 1);
	}

	/**
	 * The form's Initialise: a vanilla asset's values, read from the install when asked, fill the real form, for a
	 * new asset and an existing one, and what's saved from that reads back as vanilla's.
	 */
	private static void vanillaInitialise() throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Initialise");
		AssetCatalog.Entry farm = entry("Farm");
		GameAssetIndex index = indexOf(mod);
		Map<String, String> grain = syx.core.AssetForm.vanillaStart(farm, game, index, parser, "GRAIN", "INITFARM", true).values();

		// A new farm.
		AssetEditorScreen[] screen = new AssetEditorScreen[1];
		boolean[] answer = { false };
		SwingUtilities.invokeAndWait(() -> {
			screen[0] = new AssetEditorScreen(() -> game, url -> { }, () -> { });
			screen[0].setDiscardPrompt(question -> answer[0]);
			screen[0].open(mod, new AddAssetScreen.Choice(farm, "INITFARM", index, version, HandbookStore.Loaded.NONE, null));
		});
		expect("the vanilla picker and Initialise are at the top of the form", screen[0].vanillaPickerShown());
		expect("a farm has no sprites, so Copy Vanilla Sprites stays off", !screen[0].copySpritesShown());
		expect("the form offers the game's farms: " + screen[0].vanillaChoices(),screen[0].vanillaChoices().contains("GRAIN") && screen[0].vanillaChoices().contains("COTTON"));
		Map<String, String> before = screen[0].currentValues();
		SwingUtilities.invokeAndWait(() -> run(() -> screen[0].initialiseNow("GRAIN")));
		expect("declining the question changes nothing", screen[0].currentValues().equals(before));
		answer[0] = true;
		SwingUtilities.invokeAndWait(() -> run(() -> screen[0].initialiseNow("GRAIN")));
		Map<String, String> filled = screen[0].currentValues();
		expect("Initialise fills the form with every vanilla value: " + difference(withoutBlanks(grain), withoutBlanks(filled)), withoutBlanks(filled).equals(withoutBlanks(grain)));
		expect("including the text and sounds", "Soil".equals(filled.get("STATS.0.NAME")) && filled.keySet().stream().anyMatch(key -> key.startsWith("SOUND.")));
		AssetCreator.create(mod.root(), farm.type(), farm.schema(), "INITFARM", filled, null, parser, index);
		GameAssetIndex after = indexOf(mod);
		AssetEditing.Opened made = open(mod, assetsOf(mod).resolve("init/room/FARM_INITFARM.txt"), after);
		expect("what's saved from it reads back as vanilla's: " + difference(withoutBlanks(grain), withoutBlanks(made.values())), withoutBlanks(made.values()).equals(withoutBlanks(grain)));

		// An existing farm, made from cotton, initialised from grain, then saved.
		Map<String, String> cotton = syx.core.AssetForm.vanillaStart(farm, game, after, parser, "COTTON", "WASCOTTON", true).values();
		AssetCreator.create(mod.root(), farm.type(), farm.schema(), "WASCOTTON", cotton, null, parser, after);
		GameAssetIndex withBoth = indexOf(mod);
		AssetEditing.Opened wasCotton = open(mod, assetsOf(mod).resolve("init/room/FARM_WASCOTTON.txt"), withBoth);
		GameAssetIndex editing = indexFor(wasCotton, withBoth);
		Map<String, String>[] form = form(screen[0], mod, wasCotton, editing);
		SwingUtilities.invokeAndWait(() -> run(() -> screen[0].initialiseNow("GRAIN")));
		Map<String, String> regrown = screen[0].currentValues();
		expect("an existing asset's form is initialised too: " + difference(withoutBlanks(grain), withoutBlanks(regrown)), withoutBlanks(regrown).equals(withoutBlanks(grain)));
		AssetCreator.save(wasCotton, form[0], regrown, false, parser, editing);
		AssetEditing.Opened saved = open(mod, assetsOf(mod).resolve("init/room/FARM_WASCOTTON.txt"), indexOf(mod));
		expect("and saving it gives vanilla grain's values: " + difference(withoutBlanks(grain), withoutBlanks(saved.values())), withoutBlanks(saved.values()).equals(withoutBlanks(grain)));

		// A race starts from the chosen look, as a new race does, so its files are its own and copied from that look.
		AssetCatalog.Entry race = entry("Race");
		SwingUtilities.invokeAndWait(() -> screen[0].open(mod, new AddAssetScreen.Choice(race, "INITFOLK", withBoth, version, HandbookStore.Loaded.NONE, null, "ARGONOSH")));
		expect("a race offers the looks: " + screen[0].vanillaChoices(), screen[0].vanillaChoices().contains("HUMAN"));
		SwingUtilities.invokeAndWait(() -> run(() -> screen[0].initialiseNow("HUMAN")));
		Map<String, String> folk = screen[0].currentValues();
		expect("the race now has Human's lines: " + folk.get("POSSESSIVE"), "Human".equals(folk.get("POSSESSIVE")));
		expect("its files are named after it", "INITFOLK".equals(folk.get("BIO_FILE")) && "INITFOLK".equals(folk.get("SPRITE_FILE")));
		expect("and they'll be copied from Human: " + screen[0].currentAppearance(), "HUMAN".equals(screen[0].currentAppearance()));
		Map<String, String> human = syx.core.AssetForm.startingValues(race.schema(), withBoth, "HUMAN", "INITFOLK");
		expect("which is what a new race from Human starts with: " + difference(withoutBlanks(human), withoutBlanks(folk)), withoutBlanks(folk).equals(withoutBlanks(human)));
		expect("a race offers Copy Vanilla Sprites", screen[0].copySpritesShown());
		SwingUtilities.invokeAndWait(() -> run(() -> screen[0].copySpritesNow("HUMAN")));
		expect("the form names the copied sheets", screen[0].currentValues().values().stream().anyMatch(value -> value.contains("Initfolk")));
		expect("and says cancelling leaves the images: " + screen[0].messageText(), screen[0].messageText().contains("Cancelling leaves the images"));
	}

	/** A vanilla asset's own sheets, renamed for the new asset. Shared sheets stay as the game's. */
	private static void copyVanillaSprites() throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Sprites");
		AssetCatalog.Entry race = entry("Race");
		SpriteCopy.Result folk = SpriteCopy.copy(race, game, mod, version, indexOf(mod), parser, "HUMAN", "FOLK");
		Path assets = assetsOf(mod);
		expect("Folk.png is the race sheet", Files.exists(assets.resolve("sprite/race/Folk.png")));
		expect("CrownFolk.png is the crown", Files.exists(assets.resolve("sprite/race/face/addon/CrownFolk.png")));
		expect("Folk_Village.png is the village wall", Files.exists(assets.resolve("sprite/world/centre/wall/Folk_Village.png")));
		expect("the infant sheet comes too", Files.exists(assets.resolve("sprite/race/infant/Folk.png")));
		expect("shared sheets stay put", !Files.exists(assets.resolve("sprite/race/Blanket.png")) && !named(assets, "Humanoid.png") && !named(assets, "Blanket.png"));
		expect("icons stay put", !Files.exists(assets.resolve("sprite/icon/24/race/Folk.png")));
		expect("the bytes are the vanilla sheet", Arrays.equals(Files.readAllBytes(assets.resolve("sprite/race/Folk.png")), vanilla("sprite/race/Human.png")));
		expect("fields use the new stems: " + folk.values(), folk.values().containsValue("CrownFolk") && folk.values().containsValue("Folk_Village") && folk.values().containsValue("Folk"));
		expect("text is left alone", !folk.values().containsKey("POSSESSIVE"));

		SpriteCopy.Result newt = SpriteCopy.copy(race, game, mod, version, indexOf(mod), parser, "Q_AMEVIA", "NEWT");
		expect("Q_AMEVIA resolves to Amevia: " + newt.files(), newt.files().stream().anyMatch(file -> file.endsWith("/Newt.png")) && newt.files().stream().anyMatch(file -> file.endsWith("CrownNewt.png")));
		expect("the leading segment is not used", newt.files().stream().noneMatch(file -> file.toLowerCase().contains("q_newt")));

		SpriteCopy.Result beast = SpriteCopy.copy(entry("Animal"), game, mod, version, indexOf(mod), parser, "AUROCH", "TEST_BEAST");
		expect("an animal's sheet is written", Files.exists(assets.resolve("sprite/animal/Test_beast.png")));
		expect("and SPRITE names it", "Test_beast".equals(beast.values().get("SPRITE")));
		expect("with the vanilla bytes", Arrays.equals(Files.readAllBytes(assets.resolve("sprite/animal/Test_beast.png")), vanilla("sprite/animal/Auroch.png")));

		ModEntry clash = ModCreator.create(mods, game, "SpriteClash");
		Path blocking = assetsOf(clash).resolve("sprite/race/Folk.png");
		Files.createDirectories(blocking.getParent());
		Files.write(blocking, new byte[] { 1, 2, 3 });
		boolean refused = false;
		String why = "";
		try {
			SpriteCopy.copy(race, game, clash, version, indexOf(clash), parser, "HUMAN", "FOLK");
		} catch (SpriteCopy.Rejected e) {
			refused = e.why() == SpriteCopy.Why.CLASH;
			why = e.getMessage();
		}
		expect("an existing file refuses the copy: " + why, refused);
		expect("and nothing else is written", Arrays.equals(Files.readAllBytes(blocking), new byte[] { 1, 2, 3 }) && !Files.exists(assetsOf(clash).resolve("sprite/race/face/addon/CrownFolk.png")));

		ModEntry mineMod = ModCreator.create(mods, game, "SpriteMine");
		boolean none = false;
		why = "";
		try {
			SpriteCopy.copy(entry("Mine"), game, mineMod, version, indexOf(mineMod), parser, "STONE", "NEWSTONE");
		} catch (SpriteCopy.Rejected e) {
			none = e.why() == SpriteCopy.Why.NONE && e.getMessage().contains("no sprites named");
			why = e.getMessage();
		}
		expect("a mine copies nothing: " + why, none);
		expect("and writes no images", pngs(assetsOf(mineMod)).isEmpty());
	}

	private static boolean named(Path folder, String fileName) throws IOException {
		return pngs(folder).stream().anyMatch(file -> file.getFileName().toString().equals(fileName));
	}

	private static List<Path> pngs(Path folder) throws IOException {
		if (!Files.isDirectory(folder)) {
			return List.of();
		}
		try (Stream<Path> files = Files.walk(folder)) {
			return files.filter(file -> file.getFileName().toString().endsWith(".png")).toList();
		}
	}

	private interface Step {
		void run() throws Exception;
	}

	private static void run(Step step) {
		try {
			step.run();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static AssetCatalog.Entry entry(String label) {
		return entries.stream().filter(e -> e.type().label().equals(label)).findFirst().orElseThrow();
	}

	private static Path assetsOf(ModEntry mod) {
		return AssetCreator.versionFolder(mod.root(), version).resolve("assets");
	}

	private static byte[] vanilla(String relative) throws IOException {
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile()); InputStream in = zip.getInputStream(zip.getEntry("data/assets/" + relative))) {
			return in.readAllBytes();
		}
	}

	private static Path write(Path file, String content) throws IOException {
		Files.createDirectories(file.getParent());
		Files.write(file, content.getBytes(StandardCharsets.UTF_8));
		return file;
	}

	private static GameAssetIndex indexOf(ModEntry mod) throws IOException {
		return GameAssetIndex.load(game, AssetCreator.versionFolder(mod.root(), version), parser);
	}

	private static AssetEditing.Opened open(ModEntry mod, Path file, GameAssetIndex index) throws AssetEditing.OpenException {
		AssetEditing.Location location = AssetEditing.locate(mod.root(), version, file, entries, index);
		if (location.problem() != null) {
			throw new IllegalStateException(location.problem());
		}
		return AssetEditing.load(location, parser, index);
	}

	private static GameAssetIndex indexFor(AssetEditing.Opened opened, GameAssetIndex index) {
		return index.editing(opened.entry().type(), opened.fileName());
	}

	private static GameTree.Block tree(Path file) throws GameParser.ReadException {
		return GameTree.read(parser.root(file));
	}

	/** Whatever the parser can read, the tree must write back so it reads the same. */
	private static void gameTreeRoundTrip(Path temp) throws Exception {
		String everything = """
				PLAIN: 1,
				TEXT: "Some, text",
				EMPTY: [],
				VALUES: [A, B,],
				TEXTS: [
					"One",
					"Two",
				],
				BLOCKS: [
					{
						X: 1,
					},
					{
						Y: "two",
					},
				],
				LISTS: [
					[1, 2,],
					[3,],
				],
				BLOCK: {
					INNER: {
						DEEP: true,
					},
					NONE: {
					},
				},
				""";
		GameTree.Block read = tree(write(temp.resolve("tree.txt"), everything));
		expect("a tree holds every kind: " + read, read.get("VALUES") instanceof GameTree.Plains && read.get("TEXTS") instanceof GameTree.Texts
				&& read.get("BLOCKS") instanceof GameTree.Blocks && read.get("LISTS") instanceof GameTree.Lists && read.get("TEXT") instanceof GameTree.Text
				&& read.get("PLAIN") instanceof GameTree.Plain && read.get("BLOCK") instanceof GameTree.Block);
		GameTree.Block again = tree(write(temp.resolve("tree2.txt"), GameTree.write(read)));
		expect("a written tree reads the same: " + GameTree.write(read), again.equals(read));
	}

	private record Copy(AssetCatalog.Entry entry, Path data) {
	}

	/** Copies a vanilla file under a new name, because one with vanilla's name would merge with it. */
	private static void copyAll(ModEntry mod, String label, String folder, String prefix, List<Copy> copies, Map<String, String> crops) throws IOException {
		AssetCatalog.Entry entry = entry(label);
		Path assets = assetsOf(mod);
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile())) {
			String start = "data/assets/init/" + folder + "/";
			List<String> names = zip.stream().map(java.util.zip.ZipEntry::getName)
					.filter(n -> n.startsWith(start) && n.endsWith(".txt") && n.indexOf('/', start.length()) < 0 && n.substring(start.length()).startsWith(prefix)).toList();
			for (String name : names) {
				String stem = name.substring(start.length(), name.length() - 4);
				String copied = prefix + "COPY" + stem.substring(prefix.length());
				String data = new String(vanilla("init/" + folder + "/" + stem + ".txt"), StandardCharsets.UTF_8);
				if (crops != null) {
					// A crop can't yield what another crop yields, so each copy gets a resource of its own.
					String resource = "COPYRES" + crops.size();
					crops.put(copied, resource);
					write(assets.resolve("init/resource/" + resource + ".txt"), "");
					data = data.replaceFirst("RESOURCE:\\s*[A-Za-z0-9_]+,", "RESOURCE: " + resource + ",");
				}
				Path file = write(assets.resolve("init/" + folder + "/" + copied + ".txt"), data);
				copies.add(new Copy(entry, file));
				if (entry.type().textFolder() != null && zip.getEntry("data/assets/text/" + entry.type().textFolder() + "/" + stem + ".txt") != null) {
					Files.createDirectories(assets.resolve("text/" + entry.type().textFolder()));
					Files.write(assets.resolve("text/" + entry.type().textFolder() + "/" + copied + ".txt"), vanilla("text/" + entry.type().textFolder() + "/" + stem + ".txt"));
				}
			}
		}
	}

	private static Map<String, String> withoutBlanks(Map<String, String> values) {
		Map<String, String> kept = new TreeMap<>(values);
		kept.values().removeIf(String::isEmpty);
		return kept;
	}

	/** Every vanilla asset goes through the real form. If it's saved unchanged, not a byte may differ. */
	private static void vanillaThroughTheForm() throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Vanilla Copies");
		List<Copy> copies = new ArrayList<>();
		copyAll(mod, "Animal", "animal", "", copies, null);
		copyAll(mod, "Resource", "resource", "", copies, null);
		copyAll(mod, "Crop", "resource/growable", "", copies, new HashMap<>());
		copyAll(mod, "Farm", "room", "FARM_", copies, null);
		copyAll(mod, "Mine", "room", "MINE_", copies, null);
		copyAll(mod, "Refiner", "room", "REFINER_", copies, null);
		copyAll(mod, "Workshop", "room", "WORKSHOP_", copies, null);
		copyAll(mod, "Pasture", "room", "PASTURE_", copies, null);
		copyAll(mod, "Fishery", "room", "FISHERY_", copies, null);
		copyAll(mod, "Orchard", "room", "ORCHARD_", copies, null);
		copyAll(mod, "Hunter", "room", "HUNTER_", copies, null);
		copyAll(mod, "Temple", "room", "TEMPLE_", copies, null);
		copyAll(mod, "Shrine", "room", "SHRINE_", copies, null);
		copyAll(mod, "Tavern", "room", "TAVERN_", copies, null);
		copyAll(mod, "Eatery", "room", "EATERY_", copies, null);
		copyAll(mod, "Canteen", "room", "CANTEEN_", copies, null);
		copyAll(mod, "Well", "room", "WELL_", copies, null);
		copyAll(mod, "Bath", "room", "BATH_", copies, null);
		copyAll(mod, "Lavatory", "room", "LAVATORY_", copies, null);
		copyAll(mod, "Barber", "room", "BARBER_", copies, null);
		copyAll(mod, "Physician", "room", "PHYSICIAN_", copies, null);
		copyAll(mod, "Pleasure", "room", "PLEASURE_", copies, null);
		copyAll(mod, "Market", "room", "MARKET_", copies, null);
		copyAll(mod, "Stage", "room", "STAGE_", copies, null);
		copyAll(mod, "Speaker", "room", "SPEAKER_", copies, null);
		copyAll(mod, "Fight pit", "room", "FIGHTPIT_", copies, null);
		copyAll(mod, "Grand arena", "room", "ARENAG_", copies, null);
		copyAll(mod, "Minable resource", "resource/minable", "", copies, null);
		GameAssetIndex index = indexOf(mod);
		AssetEditorScreen[] screen = new AssetEditorScreen[1];
		SwingUtilities.invokeAndWait(() -> screen[0] = new AssetEditorScreen(() -> game, url -> { }, () -> { }));
		Map<String, Integer> counts = new TreeMap<>();
		Map<String, String[]> edits = new HashMap<>(Map.of("Animal", new String[] { "MASS", "123" }, "Resource", new String[] { "DEGRADE_RATE", "0.25" },
				"Crop", new String[] { "MINIMAP_COLOR", "1_2_3" }, "Farm", new String[] { "MINI_COLOR", "1_2_3" },
				"Mine", new String[] { "MINI_COLOR", "1_2_3" }, "Refiner", new String[] { "MINI_COLOR", "1_2_3" }, "Workshop", new String[] { "MINI_COLOR", "1_2_3" },
				"Pasture", new String[] { "MINI_COLOR", "1_2_3" }, "Minable resource", new String[] { "MINIMAP_COLOR", "1_2_3" }));
		for (String room : List.of("Fishery", "Orchard", "Hunter", "Temple", "Shrine", "Tavern", "Eatery", "Canteen", "Well", "Bath", "Lavatory", "Barber", "Physician", "Pleasure", "Market", "Stage", "Speaker", "Fight pit", "Grand arena")) {
			edits.put(room, new String[] { "MINI_COLOR", "1_2_3" });
		}
		for (Copy copy : copies) {
			String label = copy.entry().type().label();
			counts.merge(label, 1, Integer::sum);
			String file = copy.data().getFileName().toString();
			AssetEditing.Opened opened = open(mod, copy.data(), index);
			GameAssetIndex editing = indexFor(opened, index);
			byte[] dataBefore = Files.readAllBytes(copy.data());
			byte[] textBefore = opened.textFile() != null && Files.exists(opened.textFile()) ? Files.readAllBytes(opened.textFile()) : null;
			GameTree.Block treeBefore = tree(copy.data());

			Map<String, String>[] form = form(screen[0], mod, opened, editing);
			expect(file + " fills the form exactly: " + difference(withoutBlanks(opened.values()), withoutBlanks(form[0])), withoutBlanks(opened.values()).equals(withoutBlanks(form[0])));
			List<AssetChecker.Finding> blocking = AssetChecker.check(opened.schema(), editing, form[0], opened.scope(form[0], opened.kept())).stream().filter(AssetChecker.Finding::blocksSaving).toList();
			expect(file + " can be saved as it is: " + blocking, blocking.isEmpty());

			AssetCreator.Saved saved = AssetCreator.save(opened, form[0], form[0], false, parser, editing);
			expect(file + " saved unchanged writes nothing: " + saved.written(), saved.written().isEmpty());
			expect(file + " saved unchanged is identical", Arrays.equals(dataBefore, Files.readAllBytes(copy.data())));

			// A change moves only what was changed.
			String[] edit = edits.get(label);
			Map<String, String> changed = new LinkedHashMap<>(form[0]);
			changed.put(edit[0], edit[1]);
			AssetCreator.save(opened, form[0], changed, false, parser, editing);
			GameTree.Block expected = treeBefore.copy();
			expected.put(edit[0], new GameTree.Plain(edit[1]));
			expect(file + " changes only " + edit[0] + ": " + tree(copy.data()), tree(copy.data()).equals(expected));
			if (textBefore != null) {
				expect(file + "'s text file isn't touched by a change to the data file", Arrays.equals(textBefore, Files.readAllBytes(opened.textFile())));
			}
			AssetEditing.Opened reopened = open(mod, copy.data(), index);
			expect(file + " reads back with the new value", edit[1].equals(reopened.values().get(edit[0])));

			if (textBefore != null) {
				GameTree.Block textTree = tree(reopened.textFile());
				Map<String, String> renamed = new LinkedHashMap<>(reopened.values());
				renamed.put("NAME", "Edited");
				AssetCreator.save(reopened, reopened.values(), renamed, false, parser, indexFor(reopened, index));
				GameTree.Block newText = tree(reopened.textFile());
				GameTree.Block info = textTree.get("INFO") instanceof GameTree.Block block ? block.copy() : null;
				if (info != null && !textTree.has("NAME")) {
					info.put("NAME", new GameTree.Text("Edited"));
					textTree.put("INFO", info);
				} else {
					textTree.put("NAME", new GameTree.Text("Edited"));
				}
				expect(file + "'s text changes only NAME: " + newText, newText.equals(textTree));
			}
			Path leftovers = copy.data().getParent();
			try (Stream<Path> walk = Files.list(leftovers)) {
				expect(file + " leaves no temp files", walk.noneMatch(p -> p.toString().endsWith(".syxtmp")));
			}
		}
		expect("every kind of asset was checked: " + counts, counts.keySet().equals(Set.of("Animal", "Resource", "Crop", "Farm", "Mine", "Minable resource", "Refiner", "Workshop", "Pasture", "Fishery", "Orchard", "Hunter", "Temple", "Shrine",
				"Tavern", "Eatery", "Canteen", "Well", "Bath", "Lavatory", "Barber", "Physician", "Pleasure", "Market", "Stage", "Speaker", "Fight pit", "Grand arena")));
		expect("all 10 workshops were checked: " + counts, counts.get("Workshop") == 10);
		expect("all 6 pastures were checked: " + counts, counts.get("Pasture") == 6);
		expect("all 6 mines were checked: " + counts, counts.get("Mine") == 6);
		expect("all 6 deposits were checked: " + counts, counts.get("Minable resource") == 6);
		expect("all 7 farms were checked: " + counts, counts.get("Farm") == 7);
		expect("all 7 crops were checked: " + counts, counts.get("Crop") == 7);
	}

	/** A value the lists don't offer must reach the form as it is, not be quietly changed or lost. */
	private static void valuesNotOffered() throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Not Offered");
		String animal = new String(vanilla("init/animal/" + firstAnimal() + ".txt"), StandardCharsets.UTF_8)
				.replaceFirst("SPRITE:\\s*[^,]+,", "SPRITE: NOT_THERE,");
		animal = animal.replaceFirst("(?s)CLIMATE:\\s*\\{.*?\\},", "CLIMATE: {\n\tNOT_A_CLIMATE: 0.5,\n},");
		animal = animal.replaceFirst("RESOURCES:\\s*\\[[^\\]]*\\],", "RESOURCES: [NOT_A_RESOURCE,],").replaceFirst("RESOURCE_AMOUNT:\\s*\\[[^\\]]*\\],", "RESOURCE_AMOUNT: [1,],");
		Path data = write(assetsOf(mod).resolve("init/animal/ODD.txt"), animal);
		write(assetsOf(mod).resolve("text/animal/ODD.txt"), "INFO: {\n\tNAME: \"Odd\",\n\tDESC: \"Odd.\",\n},\n");
		GameAssetIndex index = indexOf(mod);
		AssetEditing.Opened opened = open(mod, data, index);
		AssetEditorScreen[] screen = new AssetEditorScreen[1];
		SwingUtilities.invokeAndWait(() -> screen[0] = new AssetEditorScreen(() -> game, url -> { }, () -> { }));
		Map<String, String> onForm = form(screen[0], mod, opened, index)[0];
		expect("an image that isn't there stays: " + onForm.get("SPRITE"), "NOT_THERE".equals(onForm.get("SPRITE")));
		expect("an entry the game doesn't have stays: " + onForm.get("RESOURCES"), "NOT_A_RESOURCE".equals(onForm.get("RESOURCES")));
		expect("a map's unknown entry stays: " + onForm, "0.5".equals(onForm.get("CLIMATE.NOT_A_CLIMATE")));
		boolean[] saveOn = new boolean[1];
		String[] problem = new String[1];
		SwingUtilities.invokeAndWait(() -> {
			saveOn[0] = screen[0].isSaveEnabled();
			problem[0] = screen[0].problemFor("SPRITE");
		});
		expect("and the checker says what's wrong with it: " + problem[0], !saveOn[0] && problem[0] != null && problem[0].contains("NOT_THERE"));
		byte[] before = Files.readAllBytes(data);
		Map<String, String> sameAgain = new LinkedHashMap<>(onForm);
		refused("and it can't be saved until fixed", () -> AssetCreator.save(opened, onForm, sameAgain, false, parser, index), "NOT_A_RESOURCE");
		expect("and the file is untouched", Arrays.equals(before, Files.readAllBytes(data)));
	}

	private static String difference(Map<String, String> one, Map<String, String> two) {
		Map<String, String> found = new TreeMap<>();
		one.forEach((key, value) -> {
			if (!value.equals(two.get(key))) {
				found.put("file " + key, value + " / form " + two.get(key));
			}
		});
		two.keySet().stream().filter(key -> !one.containsKey(key)).forEach(key -> found.put("only in form " + key, two.get(key)));
		return found.toString();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, String>[] form(AssetEditorScreen screen, ModEntry mod, AssetEditing.Opened opened, GameAssetIndex editing) throws Exception {
		@SuppressWarnings("rawtypes")
		Map<String, String>[] result = new Map[1];
		SwingUtilities.invokeAndWait(() -> {
			screen.openExisting(mod, new AddAssetScreen.Choice(opened.entry(), opened.name(), editing, version, HandbookStore.Loaded.NONE, null), opened);
			result[0] = screen.currentValues();
		});
		return result;
	}

	private static Path farmFile(ModEntry mod, String name, String extra) throws IOException {
		String data = new String(vanilla("init/room/FARM_GRAIN.txt"), StandardCharsets.UTF_8);
		return write(assetsOf(mod).resolve("init/room/FARM_" + name + ".txt"), data + extra);
	}

	private static Path farmText(ModEntry mod, String name) throws IOException {
		Path text = assetsOf(mod).resolve("text/room/FARM_" + name + ".txt");
		Files.createDirectories(text.getParent());
		Files.write(text, vanilla("text/room/FARM_GRAIN.txt"));
		return text;
	}

	/** An icon can be one path or a block, and what's in a block that the form can't show is looked after. */
	private static void iconForms(ModEntry mod) throws Exception {
		String block = "ICON: {\n\tBG: 32->BG->4,\n\tFG: 24->resource->Clay->0,\n\tSHADOW: 2,\n\tSOMETHING_ELSE: 7,\n},\n";
		Path data = farmFile(mod, "ICONS", "");
		farmText(mod, "ICONS");
		String vanillaText = Files.readString(data);
		Files.writeString(data, vanillaText.replaceFirst("ICON\\s*:\\s*\\{[^}]*\\},?", java.util.regex.Matcher.quoteReplacement(block)));
		GameAssetIndex index = indexOf(mod);
		AssetEditing.Opened opened = open(mod, data, index);
		expect("every tag in a block is read: " + opened.values(), "32->BG->4".equals(opened.values().get("ICON.BG")) && "24->resource->Clay->0".equals(opened.values().get("ICON.FG")) && "2".equals(opened.values().get("ICON.SHADOW")));
		expect("nothing about it is reported", AssetChecker.check(opened.schema(), index.editing(opened.entry().type(), opened.fileName()), opened.values(), opened.scope(opened.values(), opened.kept())).stream().noneMatch(f -> f.tag() != null && f.tag().startsWith("ICON")));

		Map<String, String> changed = new LinkedHashMap<>(opened.values());
		changed.put("ICON.SHADOW", "0");
		changed.put("ICON.OFFX", "-2");
		expect("changing one tag in it loses nothing", AssetCreator.droppedBy(opened, opened.values(), changed).isEmpty());
		AssetCreator.save(opened, opened.values(), changed, false, parser, index);
		GameTree.Block icon = (GameTree.Block) tree(data).get("ICON");
		expect("the change is made, and what's beside it stays: " + icon, new GameTree.Plain("0").equals(icon.get("SHADOW")) && new GameTree.Plain("-2").equals(icon.get("OFFX")) && new GameTree.Plain("32->BG->4").equals(icon.get("BG")) && new GameTree.Plain("7").equals(icon.get("SOMETHING_ELSE")));

		AssetEditing.Opened again = open(mod, data, indexOf(mod));
		Map<String, String> single = new LinkedHashMap<>(again.values());
		single.keySet().removeIf(key -> key.startsWith("ICON."));
		single.put("ICON", "24->resource->Clay->0");
		expect("one icon would lose what the form couldn't show: " + AssetCreator.droppedBy(again, again.values(), single), AssetCreator.droppedBy(again, again.values(), single).equals(List.of("ICON.SOMETHING_ELSE")));
		try {
			AssetCreator.save(again, again.values(), single, false, parser, index);
			expect("which has to be accepted", false);
		} catch (AssetCreator.CreationException e) {
			expect("which has to be accepted: " + e.getMessage(), e.getMessage().contains("ICON.SOMETHING_ELSE"));
		}
		AssetCreator.save(again, again.values(), single, true, parser, index);
		expect("then it becomes one icon", new GameTree.Plain("24->resource->Clay->0").equals(tree(data).get("ICON")));

		AssetEditing.Opened third = open(mod, data, indexOf(mod));
		Map<String, String> combined = new LinkedHashMap<>(third.values());
		combined.remove("ICON");
		combined.put("ICON.BG", "32->BG->4");
		combined.put("ICON.FG", "24->resource->Clay->0");
		AssetCreator.save(third, third.values(), combined, false, parser, index);
		expect("one icon can become a block", tree(data).get("ICON") instanceof GameTree.Block made && new GameTree.Plain("32->BG->4").equals(made.get("BG")));

		// A list of blocks isn't offered, so it stays as it's written and isn't called missing.
		Files.writeString(data, Files.readString(data).replaceFirst("ICON\\s*:\\s*\\{[^}]*\\},?", "ICON: [\n\t{\n\t\tBG: 32->BG->4,\n\t},\n],\n"));
		AssetEditing.Opened listed = open(mod, data, indexOf(mod));
		expect("a list of icons is kept: " + listed.kept(), listed.kept().contains("ICON"));
		expect("and isn't called missing", AssetChecker.check(listed.schema(), indexOf(mod).editing(listed.entry().type(), listed.fileName()), listed.values(), listed.scope(listed.values(), listed.kept())).stream().noneMatch(f -> "ICON".equals(f.tag())));
		Map<String, String> other = new LinkedHashMap<>(listed.values());
		other.put("WORK.SHIFT_OFFSET", "0.25");
		AssetCreator.save(listed, listed.values(), other, false, parser, indexOf(mod));
		expect("and it stays through a save: " + tree(data).get("ICON"), tree(data).get("ICON") instanceof GameTree.Blocks);

		// A block with nothing the form can show stays whole, rather than asking for BG.
		Files.writeString(data, Files.readString(data).replaceFirst("ICON\\s*:\\s*\\[[^\\]]*\\],?", "ICON: {\n\tBG: {\n\t\tODD: 1,\n\t},\n},\n"));
		AssetEditing.Opened odd = open(mod, data, indexOf(mod));
		expect("a block it can't show is kept: " + odd.kept(), odd.kept().contains("ICON"));
		expect("and isn't called missing", AssetChecker.check(odd.schema(), indexOf(mod).editing(odd.entry().type(), odd.fileName()), odd.values(), odd.scope(odd.values(), odd.kept())).stream().noneMatch(f -> f.tag() != null && f.tag().startsWith("ICON")));
	}

	/** What the form doesn't know about stays, however close the change is. */
	private static void preservation() throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Keeps Things");
		Path data = farmFile(mod, "KEEP", "UNKNOWN_ROOT: 5,\n_ARRAY_ADD: true,\n");
		Path text = farmText(mod, "KEEP");
		GameAssetIndex index = indexOf(mod);
		AssetEditing.Opened opened = open(mod, data, index);
		expect("a block ICON can be edited: " + opened.kept(), !opened.kept().contains("ICON") && opened.values().containsKey("ICON.BG") && opened.values().containsKey("ICON.FG"));
		expect("comments are noticed", opened.comments());
		Map<String, String> changed = new LinkedHashMap<>(opened.values());
		changed.put("WORK.SHIFT_OFFSET", "0.5");
		AssetCreator.save(opened, opened.values(), changed, false, parser, index);
		GameTree.Block after = tree(data);
		GameTree.Block work = (GameTree.Block) after.get("WORK");
		expect("the change is made: " + work, work.get("SHIFT_OFFSET").equals(new GameTree.Plain("0.5")));
		expect("an unread tag beside it stays: " + work, new GameTree.Plain("impact->Dig*").equals(work.get("SOUND")) && new GameTree.Plain("true").equals(work.get("USES_TOOL")));
		expect("a block ICON stays: " + after.get("ICON"), after.get("ICON") instanceof GameTree.Block icon && icon.has("BG") && icon.has("FG"));
		expect("an unknown tag stays", new GameTree.Plain("5").equals(after.get("UNKNOWN_ROOT")));
		expect("_ARRAY_ADD stays", new GameTree.Plain("true").equals(after.get("_ARRAY_ADD")));
		expect("a text file that isn't changed keeps its bytes", Arrays.equals(Files.readAllBytes(text), vanilla("text/room/FARM_GRAIN.txt")));

		// An unread text tag stays when the text changes, and a block ICON can become one icon.
		AssetEditing.Opened second = open(mod, data, indexOf(mod));
		Map<String, String> renamed = new LinkedHashMap<>(second.values());
		renamed.put("NAME", "Renamed");
		renamed.keySet().removeIf(key -> key.startsWith("ICON."));
		renamed.put("ICON", "24->resource->Grain->0");
		AssetCreator.save(second, second.values(), renamed, false, parser, index);
		GameTree.Block textAfter = tree(text);
		expect("an unread text tag stays: " + textAfter, textAfter.get("BONUS") instanceof GameTree.Block);
		expect("INFO stays a block: " + textAfter, textAfter.get("INFO") instanceof GameTree.Block info && new GameTree.Text("Renamed").equals(info.get("NAME")) && !textAfter.has("NAME"));
		expect("a block ICON can become one icon", new GameTree.Plain("24->resource->Grain->0").equals(tree(data).get("ICON")));
		iconForms(mod);

		// INFO written at the root stays at the root.
		ModEntry root = ModCreator.create(mods, game, "Root Info");
		Path animal = write(assetsOf(root).resolve("init/animal/ROOTY.txt"), new String(vanilla("init/animal/AUROCHS.txt".replace("AUROCHS", firstAnimal())), StandardCharsets.UTF_8));
		Path animalText = write(assetsOf(root).resolve("text/animal/ROOTY.txt"), "NAME: \"Rooty\",\nDESC: \"Roots.\",\n");
		AssetEditing.Opened rooted = open(root, animal, indexOf(root));
		expect("INFO at the root is read: " + rooted.values().get("NAME"), "Rooty".equals(rooted.values().get("NAME")));
		Map<String, String> rootChange = new LinkedHashMap<>(rooted.values());
		rootChange.put("DESC", "Changed.");
		AssetCreator.save(rooted, rooted.values(), rootChange, false, parser, indexOf(root));
		GameTree.Block rootText = tree(animalText);
		expect("INFO at the root stays there: " + rootText, new GameTree.Text("Changed.").equals(rootText.get("DESC")) && !rootText.has("INFO"));

		// A row written as one number stays that way unless it's edited.
		ModEntry plain = ModCreator.create(mods, game, "Plain Rows");
		String grain = new String(vanilla("init/room/FARM_GRAIN.txt"), StandardCharsets.UTF_8);
		String plainRow = grain.replaceFirst("GRAIN: \\{\\s*PLAYER: 4,\\s*AI_RATE: 3.64,\\s*\\},", "GRAIN: 4,");
		expect("the fixture has a plain row", !plainRow.equals(grain));
		Path plainFarm = write(assetsOf(plain).resolve("init/room/FARM_PLAIN.txt"), plainRow);
		farmText(plain, "PLAIN");
		AssetEditing.Opened plainOpened = open(plain, plainFarm, indexOf(plain));
		expect("a plain rate stands for the first column: " + plainOpened.values(), "4".equals(plainOpened.values().get("INDUSTRY.OUT.GRAIN.PLAYER")));
		Map<String, String> other = new LinkedHashMap<>(plainOpened.values());
		other.put("INDOORS", "true");
		AssetCreator.save(plainOpened, plainOpened.values(), other, false, parser, indexOf(plain));
		expect("a plain row stays plain", new GameTree.Plain("4").equals(((GameTree.Block) ((GameTree.Block) tree(plainFarm).get("INDUSTRY")).get("OUT")).get("GRAIN")));
		AssetEditing.Opened plainAgain = open(plain, plainFarm, indexOf(plain));
		Map<String, String> rate = new LinkedHashMap<>(plainAgain.values());
		rate.put("INDUSTRY.OUT.GRAIN.PLAYER", "5");
		AssetCreator.save(plainAgain, plainAgain.values(), rate, false, parser, indexOf(plain));
		AssetEditing.Opened plainLast = open(plain, plainFarm, indexOf(plain));
		expect("an edited row has the new rate: " + plainLast.values(), "5".equals(plainLast.values().get("INDUSTRY.OUT.GRAIN.PLAYER")));
	}

	private static String firstAnimal() throws IOException {
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile())) {
			return zip.stream().map(java.util.zip.ZipEntry::getName).filter(n -> n.matches("data/assets/init/animal/[^/]+\\.txt")).sorted().findFirst().orElseThrow()
					.replaceAll(".*/", "").replace(".txt", "");
		}
	}

	private static void edits() throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Edits");
		Path data = farmFile(mod, "EDIT", "");
		Path text = farmText(mod, "EDIT");
		GameAssetIndex index = indexOf(mod);
		AssetEditing.Opened opened = open(mod, data, index);
		expect("the name has its prefix removed: " + opened.name(), opened.name().equals("EDIT"));
		Map<String, String> changed = new LinkedHashMap<>(opened.values());
		changed.remove("NAMES");
		changed.put("INDUSTRY.OUT.GRAIN.PLAYER", "9");
		changed.remove("INDUSTRY.OUT.GRAIN.AI_RATE");
		AssetCreator.save(opened, opened.values(), changed, false, parser, index);
		expect("removing an optional tag removes it: " + tree(text), !((GameTree.Block) tree(text).get("INFO")).has("NAMES"));
		GameTree.Block row = (GameTree.Block) ((GameTree.Block) ((GameTree.Block) tree(data).get("INDUSTRY")).get("OUT")).get("GRAIN");
		expect("a changed column is changed: " + row, new GameTree.Plain("9").equals(row.get("PLAYER")));
		expect("a removed column is removed: " + row, !row.has("AI_RATE"));

		AssetEditing.Opened again = open(mod, data, indexOf(mod));
		Map<String, String> bad = new LinkedHashMap<>(again.values());
		bad.put("MINI_COLOR", "not a colour");
		byte[] before = Files.readAllBytes(data);
		refused("a bad value blocks saving", () -> AssetCreator.save(again, again.values(), bad, false, parser, index), "MINI_COLOR");
		expect("and leaves the file as it was", Arrays.equals(before, Files.readAllBytes(data)));
	}

	/**
	 * A room the game builds from one file of its own, such as the Court. The game reads a mod's file of that name merged
	 * over its own (Json.java:29-42), so the form shows the merged values and a save keeps the mod's file to its own tags.
	 */
	private static void overrides() throws Exception {
		AssetCatalog.Entry court = entry("Court");
		expect("the Court is an override named COURT: " + court.type(), court.type().isOverride() && court.type().fileName(court.type().fixedName()).equals("_COURT"));
		ModEntry mod = ModCreator.create(mods, game, "Overrides");
		GameAssetIndex index = indexOf(mod);
		String other = AssetCreator.problemWithName(court.type(), court.schema(), "OTHER", index);
		expect("an override can't take another name: " + other, other != null && other.contains("always called COURT"));
		String fine = AssetCreator.problemWithName(court.type(), court.schema(), "COURT", index);
		expect("the fixed name is fine: " + fine, fine == null);

		// A new one is a whole copy of the game's files.
		Map<String, String> starting = AssetForm.startingValues(court.schema(), index, null, "COURT");
		expect("a new override starts with the game's values: " + starting.get("MINI_COLOR") + " " + starting.get("NAME"), "70_70_70".equals(starting.get("MINI_COLOR")) && "Court".equals(starting.get("NAME")));
		AssetCreator.Created created = AssetCreator.create(mod.root(), court.type(), court.schema(), "COURT", starting, null, parser, index);
		Path data = assetsOf(mod).resolve("init/room/_COURT.txt");
		Path text = assetsOf(mod).resolve("text/room/_COURT.txt");
		expect("both files are created: " + created, data.equals(created.dataFile()) && text.equals(created.textFile()) && Files.exists(data) && Files.exists(text));
		Path temp = Files.createTempDirectory("syx-override");
		GameTree.Block gameData = tree(write(temp.resolve("data.txt"), new String(vanilla("init/room/_COURT.txt"), StandardCharsets.UTF_8)));
		GameTree.Block gameText = tree(write(temp.resolve("text.txt"), new String(vanilla("text/room/_COURT.txt"), StandardCharsets.UTF_8)));
		expect("the new data file reads as the game's", tree(data).equals(gameData));
		expect("the new text file reads as the game's", tree(text).equals(gameText));
		String again = AssetCreator.problemWithName(court.type(), court.schema(), "COURT", indexOf(mod));
		expect("a mod has at most one: " + again, again != null && again.contains("already overrides"));

		// A file with one tag: the rest is the game's, and a save writes only what's the mod's own.
		Files.delete(text);
		Files.writeString(data, "MINI_COLOR: 1_2_3,\n");
		index = indexOf(mod);
		AssetEditing.Opened opened = open(mod, data, index);
		expect("an override opens with the game's values: " + opened.values().get("RESOURCES") + " " + opened.values().get("NAME"),
				starting.get("RESOURCES") != null && starting.get("RESOURCES").equals(opened.values().get("RESOURCES")) && "Court".equals(opened.values().get("NAME")) && "1_2_3".equals(opened.values().get("MINI_COLOR")));
		expect("and with no text file of its own", !opened.hasTextFile());
		List<AssetChecker.Finding> findings = AssetChecker.check(court.schema(), index.editing(court.type(), opened.fileName()), opened.values(), opened.scope(opened.values(), opened.kept()));
		expect("a partial override has no errors: " + findings, findings.stream().noneMatch(AssetChecker.Finding::blocksSaving));
		AssetCreator.Saved unchanged = AssetCreator.save(opened, opened.values(), opened.values(), false, parser, index.editing(court.type(), opened.fileName()));
		expect("saved unchanged, nothing is written: " + unchanged.written(), unchanged.written().isEmpty());
		Map<String, String> changed = new LinkedHashMap<>(opened.values());
		changed.put("DEGRADE_RATE", "0.5");
		AssetCreator.save(opened, opened.values(), changed, false, parser, index.editing(court.type(), opened.fileName()));
		expect("only the mod's own tag and the changed one are written: " + tree(data).entries().keySet(), tree(data).entries().keySet().equals(Set.of("MINI_COLOR", "DEGRADE_RATE")));
		expect("and no text file", !Files.exists(text));

		AssetEditing.Opened renaming = open(mod, data, indexOf(mod));
		Map<String, String> renamed = new LinkedHashMap<>(renaming.values());
		renamed.put("NAME", "Tribunal");
		AssetCreator.save(renaming, renaming.values(), renamed, false, parser, indexOf(mod).editing(court.type(), renaming.fileName()));
		GameTree.Block words = tree(text);
		expect("a changed name writes a text file with only INFO: " + words.entries().keySet(), words.entries().keySet().equals(Set.of("INFO")));
		expect("whose other words are the game's", new GameTree.Text("Tribunal").equals(((GameTree.Block) words.get("INFO")).get("NAME"))
				&& ((GameTree.Block) words.get("INFO")).get("DESC").equals(((GameTree.Block) gameText.get("INFO")).get("DESC")));

		// What can't be done.
		AssetEditing.Opened last = open(mod, data, indexOf(mod));
		Map<String, String> removed = new LinkedHashMap<>(last.values());
		removed.keySet().removeIf(key -> key.equals("RESOURCES"));
		List<AssetChecker.Finding> removing = AssetChecker.check(court.schema(), indexOf(mod), removed, last.scope(removed, last.kept()));
		expect("removing a tag the game's file has is an error: " + removing, removing.stream().anyMatch(f -> "RESOURCES".equals(f.tag()) && f.blocksSaving()));
		byte[] before = Files.readAllBytes(data);
		refused("and saving it is refused", () -> AssetCreator.save(last, last.values(), removed, false, parser, indexOf(mod).editing(court.type(), last.fileName())), "RESOURCES");
		expect("leaving the file as it was", Arrays.equals(before, Files.readAllBytes(data)));
		Files.writeString(data, "_JSON_ADD: true,\nMINI_COLOR: 1_2_3,\n");
		AssetEditing.Location merging = AssetEditing.locate(mod.root(), version, data, entries, indexOf(mod));
		try {
			AssetEditing.load(merging, parser, indexOf(mod));
			expect("a file with _JSON_ADD is refused", false);
		} catch (AssetEditing.OpenException e) {
			expect("a file with _JSON_ADD is refused, saying why: " + e.getMessage(), e.getMessage().contains("_JSON_ADD"));
		}
		deleteFolder(temp);
	}

	private interface Saving {
		void run() throws Exception;
	}

	private static void refused(String name, Saving saving, String mentions) throws Exception {
		try {
			saving.run();
			expect(name + " was refused", false);
		} catch (AssetCreator.CreationException e) {
			expect(name + ": " + e.getMessage(), e.getMessage().contains(mentions));
		}
	}

	/** Blocks the form doesn't fully know can't follow their place when a list changes length, so that's asked about first. */
	private static void droppedTags() throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Dropped");
		String upgrades = "UPGRADES: [\n\t{\n\t\tBOOST: 1,\n\t\tRESOURCE_MASK: [1,],\n\t\tSECRET: 9,\n\t},\n\t{\n\t\tBOOST: 2,\n\t\tRESOURCE_MASK: [1,],\n\t},\n],\n";
		Path data = farmFile(mod, "LEVELS", upgrades);
		farmText(mod, "LEVELS");
		GameAssetIndex index = indexOf(mod);
		AssetEditing.Opened opened = open(mod, data, index);
		expect("the levels are read: " + opened.values(), "2".equals(opened.values().get("UPGRADES.1.BOOST")));

		Map<String, String> sameCount = new LinkedHashMap<>(opened.values());
		sameCount.put("UPGRADES.0.BOOST", "3");
		expect("nothing is lost if the count stays the same", AssetCreator.droppedBy(opened, opened.values(), sameCount).isEmpty());
		AssetCreator.save(opened, opened.values(), sameCount, false, parser, index);
		GameTree.Block level = ((GameTree.Blocks) tree(data).get("UPGRADES")).items().get(0);
		expect("an unread tag in a list stays: " + level, new GameTree.Plain("9").equals(level.get("SECRET")) && new GameTree.Plain("3").equals(level.get("BOOST")));

		AssetEditing.Opened again = open(mod, data, indexOf(mod));
		Map<String, String> fewer = new LinkedHashMap<>(again.values());
		fewer.keySet().removeIf(key -> key.startsWith("UPGRADES.1."));
		expect("a shorter list reports what it would lose: " + AssetCreator.droppedBy(again, again.values(), fewer), AssetCreator.droppedBy(again, again.values(), fewer).equals(List.of("UPGRADES.0.SECRET")));
		byte[] before = Files.readAllBytes(data);
		refused("that isn't saved without agreement", () -> AssetCreator.save(again, again.values(), fewer, false, parser, index), "UPGRADES.0.SECRET");
		expect("and the file is untouched", Arrays.equals(before, Files.readAllBytes(data)));
		AssetCreator.save(again, again.values(), fewer, true, parser, index);
		expect("with agreement, the level is gone", ((GameTree.Blocks) tree(data).get("UPGRADES")).items().size() == 1);
	}

	private static void safety() throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Safety");
		Path data = farmFile(mod, "SAFE", "");
		Path text = farmText(mod, "SAFE");
		GameAssetIndex index = indexOf(mod);
		AssetEditing.Opened opened = open(mod, data, index);
		Map<String, String> changed = new LinkedHashMap<>(opened.values());
		changed.put("INDOORS", "true");

		String elsewhere = Files.readString(data) + "\nADDED_BY_HAND: 1,\n";
		Files.writeString(data, elsewhere);
		refused("a data file changed since opening is refused", () -> AssetCreator.save(opened, opened.values(), changed, false, parser, index), "changed since");
		expect("and what was written elsewhere survives", Files.readString(data).equals(elsewhere));
		try (Stream<Path> walk = Files.walk(mod.root())) {
			expect("and no temp files are left", walk.noneMatch(p -> p.toString().endsWith(".syxtmp")));
		}

		AssetEditing.Opened fresh = open(mod, data, indexOf(mod));
		Files.writeString(text, Files.readString(text) + "\n");
		refused("a text file changed since opening is refused", () -> AssetCreator.save(fresh, fresh.values(), changed, false, parser, index), "changed since");

		// A file that must be new is never put over one that has just appeared.
		Path raceFolder = Files.createTempDirectory(mod.root(), "race");
		Path taken = write(raceFolder.resolve("taken.txt"), "someone else's");
		Path takenTemp = write(raceFolder.resolve("taken.tmp"), "mine");
		try {
			SafeWrite.commit(List.of(new SafeWrite.Change(taken, takenTemp, null)));
			expect("a new file refuses to overwrite one that appeared", false);
		} catch (IOException e) {
			expect("a file that appeared is untouched", Files.readString(taken).equals("someone else's"));
		}

		// One that fails part way puts back what had already moved.
		Path folder = Files.createTempDirectory(mod.root(), "commit");
		Path first = write(folder.resolve("first.txt"), "original");
		Path brandNew = folder.resolve("new.txt");
		Path firstTemp = write(folder.resolve("first.tmp"), "changed");
		Path newTemp = write(folder.resolve("new.tmp"), "added");
		Path inTheWay = folder.resolve("full");
		write(inTheWay.resolve("inside.txt"), "x");
		Path wayTemp = write(folder.resolve("way.tmp"), "never");
		try {
			SafeWrite.commit(List.of(new SafeWrite.Change(first, firstTemp, "original".getBytes(StandardCharsets.UTF_8)), new SafeWrite.Change(brandNew, newTemp, null),
					new SafeWrite.Change(inTheWay, wayTemp, "x".getBytes(StandardCharsets.UTF_8))));
			expect("a move onto a full folder fails", false);
		} catch (IOException e) {
			expect("a failed save puts the original back", Files.readString(first).equals("original"));
			expect("a failed save removes what it added", !Files.exists(brandNew));
			expect("what was in the way is untouched", Files.exists(inTheWay.resolve("inside.txt")));
		}
	}

	private static void generatingText() throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Generate Text");
		Path data = write(assetsOf(mod).resolve("init/animal/NOTEXT.txt"), new String(vanilla("init/animal/" + firstAnimal() + ".txt"), StandardCharsets.UTF_8));
		Path text = assetsOf(mod).resolve("text/animal/NOTEXT.txt");
		GameAssetIndex index = indexOf(mod);
		AssetEditing.Opened opened = open(mod, data, index);
		expect("a missing text file is noticed", !opened.hasTextFile() && opened.needsTextFile());
		List<AssetChecker.Finding> findings = AssetChecker.check(opened.schema(), index, opened.values(), opened.scope(opened.values(), opened.kept())).stream().filter(AssetChecker.Finding::blocksSaving).toList();
		expect("its tags aren't asked for until it's generated: " + findings, findings.isEmpty());
		long files = countFiles(mod.root());
		AssetCreator.Saved nothing = AssetCreator.save(opened, opened.values(), opened.values(), false, parser, index);
		expect("saving without generating writes no text file", !Files.exists(text) && nothing.written().isEmpty() && countFiles(mod.root()) == files);

		Map<String, String> partial = new LinkedHashMap<>(opened.values());
		partial.put("NAME", "Named");
		refused("a generated file must be complete", () -> AssetCreator.save(opened, opened.values(), partial, false, parser, index), "DESC");
		expect("and nothing is left behind by that", !Files.exists(text) && countFiles(mod.root()) == files);

		Map<String, String> full = new LinkedHashMap<>(partial);
		full.put("DESC", "Described.");
		AssetCreator.Saved written = AssetCreator.save(opened, opened.values(), full, false, parser, index);
		expect("generating writes the text file: " + written.written(), written.written().equals(List.of(text)));
		expect("in the form a new asset's has: " + Files.readString(text), Files.readString(text).equals("INFO: {\n\tNAME: \"Named\",\n\tDESC: \"Described.\",\n},\n"));

		// A file that has appeared in the meantime is never overwritten.
		ModEntry raced = ModCreator.create(mods, game, "Generate Raced");
		Path racedData = write(assetsOf(raced).resolve("init/animal/RACED.txt"), new String(vanilla("init/animal/" + firstAnimal() + ".txt"), StandardCharsets.UTF_8));
		AssetEditing.Opened racing = open(raced, racedData, indexOf(raced));
		Path appeared = write(assetsOf(raced).resolve("text/animal/RACED.txt"), "INFO: {\n\tNAME: \"Mine\",\n\tDESC: \"Mine.\",\n},\n");
		Map<String, String> racedValues = new LinkedHashMap<>(racing.values());
		racedValues.put("NAME", "Yours");
		racedValues.put("DESC", "Yours.");
		refused("a text file that appeared isn't overwritten", () -> AssetCreator.save(racing, racing.values(), racedValues, false, parser, indexOf(raced)), "changed since");
		expect("and it's left alone", Files.readString(appeared).contains("Mine"));
	}

	private static void generatingSounds() throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Generate Sounds");
		String animalData = new String(vanilla("init/animal/" + firstAnimal() + ".txt"), StandardCharsets.UTF_8);
		Path data = write(assetsOf(mod).resolve("init/animal/CALLER.txt"), animalData);
		write(assetsOf(mod).resolve("text/animal/CALLER.txt"), "INFO: {\n\tNAME: \"Caller\",\n\tDESC: \"Calls.\",\n},\n");
		SoundEntries.Slot call = SoundEntries.ANIMAL_CALL;
		Path own = assetsOf(mod).resolve("audio/config/mono/CALLER.txt");
		GameAssetIndex index = indexOf(mod);
		AssetEditing.Opened opened = open(mod, data, index);
		expect("a missing sound is offered: " + opened.missingSounds(), opened.missingSounds().equals(List.of(call)));
		List<AssetChecker.Finding> findings = AssetChecker.check(opened.schema(), index, opened.values(), opened.scope(opened.values(), opened.kept())).stream().filter(AssetChecker.Finding::blocksSaving).toList();
		expect("it isn't asked for until it's generated: " + findings, findings.isEmpty());
		AssetCreator.save(opened, opened.values(), opened.values(), false, parser, index);
		expect("saving without generating writes no sound file", !Files.exists(own));

		Map<String, String> chosen = new LinkedHashMap<>(opened.values());
		chosen.put(call.valueKey(), SoundEntries.NO_SOUND);
		AssetCreator.save(opened, opened.values(), chosen, false, parser, index);
		expect("generating writes the entry: " + Files.readString(own), Files.readString(own).equals("ANIMAL_CALL_CALLER: DUMMY,\n"));

		GameAssetIndex after = indexOf(mod);
		AssetEditing.Opened reopened = open(mod, data, after);
		expect("it's then the asset's own: " + reopened.sounds().get(call), reopened.sounds().get(call).state() == SoundEntries.State.OWN_FILE && SoundEntries.NO_SOUND.equals(reopened.values().get(call.valueKey())));
		Map<String, String> edited = new LinkedHashMap<>(reopened.values());
		String another = after.sounds().options(call).get(1);
		edited.put(call.valueKey(), another);
		AssetCreator.save(reopened, reopened.values(), edited, false, parser, after);
		expect("an own entry can be edited: " + Files.readString(own), new GameTree.Plain(another).equals(tree(own).get(call.key("CALLER"))));

		// An entry is added after what's there, comments included.
		ModEntry shared = ModCreator.create(mods, game, "Sound Additions");
		Path sharedData = write(assetsOf(shared).resolve("init/animal/ADDED.txt"), animalData);
		write(assetsOf(shared).resolve("text/animal/ADDED.txt"), "INFO: {\n\tNAME: \"Added\",\n\tDESC: \"Added.\",\n},\n");
		String existing = "** a note\nOTHER_ENTRY: DUMMY,";
		Path ownFile = write(assetsOf(shared).resolve("audio/config/mono/ADDED.txt"), existing);
		GameAssetIndex sharedIndex = indexOf(shared);
		AssetEditing.Opened adding = open(shared, sharedData, sharedIndex);
		Map<String, String> add = new LinkedHashMap<>(adding.values());
		add.put(call.valueKey(), SoundEntries.NO_SOUND);
		AssetCreator.save(adding, adding.values(), add, false, parser, sharedIndex);
		expect("adding keeps the file as it was, and adds after it: " + Files.readString(ownFile), Files.readString(ownFile).equals(existing + "\nANIMAL_CALL_ADDED: DUMMY,\n"));

		// An entry in a file that isn't the asset's own is only shown.
		ModEntry other = ModCreator.create(mods, game, "Sound Elsewhere");
		Path otherData = write(assetsOf(other).resolve("init/animal/ELSEWHERE.txt"), animalData);
		write(assetsOf(other).resolve("text/animal/ELSEWHERE.txt"), "INFO: {\n\tNAME: \"E\",\n\tDESC: \"E.\",\n},\n");
		Path sharedFile = write(assetsOf(other).resolve("audio/config/mono/AllMySounds.txt"), "ANIMAL_CALL_ELSEWHERE: DUMMY,\n");
		GameAssetIndex otherIndex = indexOf(other);
		AssetEditing.Opened elsewhere = open(other, otherData, otherIndex);
		SoundEntries.Info info = elsewhere.sounds().get(call);
		expect("an entry in another file names it: " + info, info.state() == SoundEntries.State.OTHER_MOD_FILE && info.source().equals("audio/config/mono/AllMySounds.txt"));
		expect("and isn't on the form: " + elsewhere.values(), !elsewhere.values().containsKey(call.valueKey()));
		AssetCreator.save(elsewhere, elsewhere.values(), elsewhere.values(), false, parser, otherIndex);
		expect("and isn't touched", Files.readString(sharedFile).equals("ANIMAL_CALL_ELSEWHERE: DUMMY,\n"));
		expect("nor copied to a file of the asset's own", !Files.exists(assetsOf(other).resolve("audio/config/mono/ELSEWHERE.txt")));
		SoundEntries.Info game = otherIndex.sounds().info(SoundEntries.ROOM_WORK, "FARM_GRAIN", assetsOf(other));
		expect("the game's own entry is told apart: " + game, game.state() == SoundEntries.State.GAME && game.value() != null);
	}

	/** Editing a crop mustn't compare it with itself, but must still compare it with the others. */
	private static void cropRules() throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Crop Rules");
		String crop = new String(vanilla("init/resource/growable/GRAIN.txt"), StandardCharsets.UTF_8).replaceFirst("RESOURCE:\\s*[A-Za-z0-9_]+,", "RESOURCE: MYRES,");
		write(assetsOf(mod).resolve("init/resource/MYRES.txt"), "");
		Path data = write(assetsOf(mod).resolve("init/resource/growable/MYCROP.txt"), crop);
		GameAssetIndex index = indexOf(mod);
		AssetEditing.Opened opened = open(mod, data, index);
		GameAssetIndex editing = indexFor(opened, index);
		expect("the plain index sees the clash that editing must not", AssetChecker.check(opened.schema(), index, opened.values()).stream().anyMatch(f -> f.message().contains("already yields")));
		expect("a crop doesn't clash with itself", AssetChecker.check(opened.schema(), editing, opened.values(), opened.scope(opened.values(), opened.kept())).stream().noneMatch(AssetChecker.Finding::blocksSaving));
		Map<String, String> clash = new LinkedHashMap<>(opened.values());
		clash.put("RESOURCE", "GRAIN");
		refused("it still clashes with another crop", () -> AssetCreator.save(opened, opened.values(), clash, false, parser, editing), "already yields");
	}

	private static void refusals() throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Refusals");
		Path assets = assetsOf(mod);
		Path grain = farmFile(mod, "OK", "");
		GameAssetIndex none = indexOf(mod);
		expect("a farm opens", AssetEditing.locate(mod.root(), version, grain, entries, none).problem() == null);
		expect("a farm's text file opens the farm", "OK".equals(AssetEditing.locate(mod.root(), version, farmText(mod, "OK"), entries, none).name()));
		expect("and it's the farm's type", "Farm".equals(AssetEditing.locate(mod.root(), version, grain, entries, none).entry().type().label()));

		expectProblem(mod, mod.root().resolve("V1/assets/init/animal/OLD.txt"), "another game version", none);
		expectProblem(mod, write(assets.resolve("init/room/SCHOOL_MINE.txt"), ""), "can't be edited yet", none);
		expectProblem(mod, write(assets.resolve("init/disease/FLU.txt"), ""), "can't be edited yet", none);
		expectProblem(mod, write(assets.resolve("text/animal/ORPHAN.txt"), "INFO: {\n},\n"), "no data file", none);
		expectProblem(mod, write(assets.resolve("sprite/animal/IMAGE.png"), ""), "isn't an asset", none);
		expectProblem(mod, write(assets.resolve("audio/config/mono/SOME.txt"), ""), "isn't an asset", none);
		expectProblem(mod, AssetCreator.versionFolder(mod.root(), version).resolve("notes.txt"), "isn't an asset", none);

		// A file with one of vanilla's names merges with it, so its absent tags aren't the defaults.
		Path merged = write(assets.resolve("init/room/FARM_GRAIN.txt"), "INDOORS: true,\n");
		expectProblem(mod, merged, "merges the two files", indexOf(mod));

		Path broken = write(assets.resolve("init/room/FARM_BROKEN.txt"), "THIS IS NOT");
		AssetEditing.Location location = AssetEditing.locate(mod.root(), version, broken, entries, none);
		try {
			AssetEditing.load(location, parser, none);
			expect("a file the game can't read isn't opened", false);
		} catch (AssetEditing.OpenException e) {
			expect("a file the game can't read isn't opened: " + e.getMessage(), e.getMessage().contains("refused"));
		}
	}

	private static void expectProblem(ModEntry mod, Path file, String mentions, GameAssetIndex index) {
		String problem = AssetEditing.locate(mod.root(), version, file, entries, index).problem();
		expect(file.getFileName() + " is refused saying " + mentions + ": " + problem, problem != null && problem.contains(mentions));
	}

	/** A sprite holds a list inside a block, which nothing else does, so it's checked at each depth. */
	private static void mines() throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Mine Edits");
		Path assets = assetsOf(mod);
		String stone = new String(vanilla("init/room/MINE_STONE.txt"), StandardCharsets.UTF_8);
		Path data = write(assets.resolve("init/room/MINE_EDIT.txt"), stone);
		write(assets.resolve("text/room/MINE_EDIT.txt"), new String(vanilla("text/room/MINE_STONE.txt"), StandardCharsets.UTF_8));
		GameAssetIndex index = indexOf(mod);
		AssetEditing.Opened opened = open(mod, data, index);
		expect("the name has its prefix removed", opened.name().equals("EDIT"));
		expect("a sprite's levels are read: " + opened.values().keySet().stream().filter(k -> k.startsWith("SPRITES.WORK_1X1")).toList(), "STORAGE: 5".equals(opened.values().get("SPRITES.WORK_1X1.0.FRAMES")));
		expect("several frames are a line each", opened.values().get("SPRITES.AUX_1X1.0.FRAMES").split("\\n").length == 6);

		GameTree.Block before = tree(data);
		Map<String, String> changed = new LinkedHashMap<>(opened.values());
		changed.put("SPRITES.WORK_1X1.1.FRAMES", "MECH_WORK: 3");
		changed.put("SPRITES.WORK_1X1.1.FPS", "9");
		AssetCreator.save(opened, opened.values(), changed, false, parser, indexFor(opened, index));
		GameTree.Block after = tree(data);
		GameTree.Block sprites = (GameTree.Block) after.get("SPRITES");
		GameTree.Block level = ((GameTree.Blocks) sprites.get("WORK_1X1")).items().get(1);
		expect("a frame's change reaches the file: " + level, new GameTree.Plains(List.of("MECH_WORK: 3")).equals(level.get("FRAMES")) && new GameTree.Plain("9").equals(level.get("FPS")));
		expect("the level's other tags stay: " + level, level.has("SHADOW_LENGTH") && level.has("CIRCULAR"));
		GameTree.Block expected = before.copy();
		GameTree.Block expectedSprites = ((GameTree.Block) expected.get("SPRITES")).copy();
		expectedSprites.put("WORK_1X1", sprites.get("WORK_1X1"));
		expected.put("SPRITES", expectedSprites);
		expect("nothing else moved", after.equals(expected));

		// The forms the form can't show stay as they are.
		String single = "SPRITES: {\n\tSOLO_1X1: 1,\n\tAUX_1X1: {\n\t\tFRAMES: [\n\t\t\tSTORAGE: 0,\n\t\t],\n\t\tFPS: 3,\n\t},\n";
		Path solo = write(assets.resolve("init/room/MINE_SOLO.txt"), stone.substring(0, stone.indexOf("SPRITES:")) + single + stone.substring(stone.indexOf("SPRITES:") + "SPRITES: {".length() + 1).replaceFirst("(?s)\\tAUX_1X1: \\[.*?\\n\\t\\],\\r?\\n", ""));
		write(assets.resolve("text/room/MINE_SOLO.txt"), new String(vanilla("text/room/MINE_STONE.txt"), StandardCharsets.UTF_8));
		AssetEditing.Opened soloOpened = open(mod, solo, indexOf(mod));
		expect("a sprite written as one block is its first level: " + soloOpened.values().get("SPRITES.AUX_1X1.0.FRAMES"), "STORAGE: 0".equals(soloOpened.values().get("SPRITES.AUX_1X1.0.FRAMES")) && !soloOpened.kept().contains("SPRITES.AUX_1X1"));
		expect("and it isn't called missing: ", AssetChecker.check(soloOpened.schema(), indexOf(mod), soloOpened.values(), soloOpened.scope(soloOpened.values(), soloOpened.kept())).stream().noneMatch(f -> f.tag() != null && f.tag().contains("AUX_1X1")));
		GameTree.Block soloBefore = tree(solo);
		Map<String, String> soloChanged = new LinkedHashMap<>(soloOpened.values());
		soloChanged.put("STORAGE", "99");
		AssetCreator.save(soloOpened, soloOpened.values(), soloChanged, false, parser, indexFor(soloOpened, indexOf(mod)));
		GameTree.Block soloAfter = tree(solo);
		expect("a block and an unknown tag survive another tag's change", soloAfter.get("SPRITES").equals(soloBefore.get("SPRITES")));

		// A tag the form doesn't offer, in a level that goes away.
		String overwrite = stone.replaceFirst("WORK_1X1: \\[\\s*\\{", "WORK_1X1: [\n{\nOVERWRITE: [ { FPS: 1, }, ],");
		Path levels = write(assets.resolve("init/room/MINE_LEVELS.txt"), overwrite);
		write(assets.resolve("text/room/MINE_LEVELS.txt"), new String(vanilla("text/room/MINE_STONE.txt"), StandardCharsets.UTF_8));
		GameAssetIndex levelIndex = indexOf(mod);
		AssetEditing.Opened levelsOpened = open(mod, levels, levelIndex);
		Map<String, String> fewer = new LinkedHashMap<>(levelsOpened.values());
		fewer.keySet().removeIf(key -> key.startsWith("SPRITES.WORK_1X1.1."));
		expect("a shorter sprite list reports what it would lose: " + AssetCreator.droppedBy(levelsOpened, levelsOpened.values(), fewer), AssetCreator.droppedBy(levelsOpened, levelsOpened.values(), fewer).equals(List.of("SPRITES.WORK_1X1.0.OVERWRITE")));
		refused("that isn't saved without agreement", () -> AssetCreator.save(levelsOpened, levelsOpened.values(), fewer, false, parser, indexFor(levelsOpened, levelIndex)), "OVERWRITE");

		// An empty list reaches the form, so it isn't called missing, and stays as it is.
		String free = stone.replaceFirst("RESOURCES: \\[[^\\]]*\\],", "RESOURCES: [],").replaceFirst("AREA_COSTS: \\[[^\\]]*\\],", "AREA_COSTS: [],").replaceAll("COSTS: \\[[^\\]]*\\],", "COSTS: [],");
		Path freeFile = write(assets.resolve("init/room/MINE_FREE.txt"), free);
		write(assets.resolve("text/room/MINE_FREE.txt"), new String(vanilla("text/room/MINE_STONE.txt"), StandardCharsets.UTF_8));
		GameAssetIndex freeIndex = indexOf(mod);
		AssetEditing.Opened freeOpened = open(mod, freeFile, freeIndex);
		AssetEditorScreen[] freeScreen = new AssetEditorScreen[1];
		SwingUtilities.invokeAndWait(() -> freeScreen[0] = new AssetEditorScreen(() -> game, url -> { }, () -> { }));
		Map<String, String>[] freeForm = form(freeScreen[0], mod, freeOpened, indexFor(freeOpened, freeIndex));
		List<AssetChecker.Finding> freeBlocking = AssetChecker.check(freeOpened.schema(), indexFor(freeOpened, freeIndex), freeForm[0], freeOpened.scope(freeForm[0], freeOpened.kept())).stream().filter(AssetChecker.Finding::blocksSaving).toList();
		expect("a mine with no resources can be saved: " + freeBlocking, freeBlocking.isEmpty());
		byte[] freeBefore = Files.readAllBytes(freeFile);
		expect("and unchanged writes nothing", AssetCreator.save(freeOpened, freeForm[0], freeForm[0], false, parser, indexFor(freeOpened, freeIndex)).written().isEmpty() && Arrays.equals(freeBefore, Files.readAllBytes(freeFile)));

		// The real form: a new mine starts from the game's sprites, folded, and leaving it asks nothing.
		AssetCatalog.Entry mine = entry("Mine");
		GameAssetIndex newIndex = indexOf(mod);
		AssetEditorScreen[] screen = new AssetEditorScreen[1];
		boolean[] asked = new boolean[1];
		SwingUtilities.invokeAndWait(() -> {
			screen[0] = new AssetEditorScreen(() -> game, url -> { }, () -> { });
			screen[0].setDiscardPrompt(question -> {
				asked[0] = true;
				return true;
			});
			screen[0].open(mod, new AddAssetScreen.Choice(mine, "FRESH", newIndex, version, HandbookStore.Loaded.NONE, null));
		});
		Map<String, String> fresh = screen[0].currentValues();
		expect("the form starts with the game's sprites: " + fresh.get("SPRITES.WORK_1X1.1.FRAMES"), fresh.get("SPRITES.WORK_1X1.1.FRAMES") != null && fresh.get("SPRITES.WORK_1X1.1.FRAMES").startsWith("MECH_WORK: 0"));
		expect("a sprite starts folded", !screen[0].isUnfolded("SPRITES.WORK_1X1"));
		expect("the sounds start from a mine's: " + screen[0].valueOf("SOUND.ROOM_WORK_"), "impact->Mine*".equals(screen[0].valueOf("SOUND.ROOM_WORK_")));
		SwingUtilities.invokeAndWait(() -> screen[0].confirmLeave());
		expect("starting values alone don't count as work to lose", !asked[0]);
		SwingUtilities.invokeAndWait(() -> screen[0].clickRowAction("SPRITES.WORK_1X1"));
		expect("Show unfolds it", screen[0].isUnfolded("SPRITES.WORK_1X1"));
		SwingUtilities.invokeAndWait(() -> screen[0].clickRowAction("SPRITES.WORK_1X1"));
		expect("and Hide folds it, more than once", !screen[0].isUnfolded("SPRITES.WORK_1X1"));
		// Inside a level, as anywhere, what has a default is ghosted with it until it's added.
		expect("an absent tag in a level shows its default: " + screen[0].nestedGhostValue("SPRITES.WORK_1X1.0.TINT"), "true".equals(screen[0].nestedGhostValue("SPRITES.WORK_1X1.0.TINT")));
		expect("and isn't in what's saved", !screen[0].currentValues().containsKey("SPRITES.WORK_1X1.0.TINT"));
		expect("a colour's default is the game's behaviour", "None, so TINT decides".equals(screen[0].nestedGhostValue("SPRITES.WORK_1X1.0.COLOR")));
		expect("a tag the game's file has isn't ghosted", screen[0].nestedGhostValue("SPRITES.WORK_1X1.0.SHADOW_LENGTH") == null && "2".equals(screen[0].currentValues().get("SPRITES.WORK_1X1.0.SHADOW_LENGTH")));
		SwingUtilities.invokeAndWait(() -> screen[0].clickRowAction("SPRITES.WORK_1X1.0.FPS"));
		expect("Add takes it out of ghosting", screen[0].nestedGhostValue("SPRITES.WORK_1X1.0.FPS") == null);
		SwingUtilities.invokeAndWait(() -> screen[0].setValue("SPRITES.WORK_1X1.0.FPS", "7"));
		expect("and it's saved", "7".equals(screen[0].currentValues().get("SPRITES.WORK_1X1.0.FPS")));
		SwingUtilities.invokeAndWait(() -> screen[0].clickRowAction("SPRITES.WORK_1X1.0.FPS"));
		expect("Remove puts the ghost back", "0".equals(screen[0].nestedGhostValue("SPRITES.WORK_1X1.0.FPS")) && !screen[0].currentValues().containsKey("SPRITES.WORK_1X1.0.FPS"));
		SwingUtilities.invokeAndWait(() -> screen[0].setValue("SPRITES.AUX_1X1.0.FRAMES", "not a frame"));
		expect("a problem unfolds its sprite", screen[0].isUnfolded("SPRITES.AUX_1X1"));
		expect("and shows on its own row: " + screen[0].problemFor("SPRITES.AUX_1X1.0.FRAMES"), screen[0].problemFor("SPRITES.AUX_1X1.0.FRAMES").contains("isn't a frame"));
		expect("the other sprites stay folded", !screen[0].isUnfolded("SPRITES.WORK_1X1"));
		SwingUtilities.invokeAndWait(() -> screen[0].confirmLeave());
		expect("a changed value does count", asked[0]);
	}

	private static void comments() throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Comments");
		Path noted = write(assetsOf(mod).resolve("init/animal/NOTED.txt"), "** a note\n" + new String(vanilla("init/animal/" + firstAnimal() + ".txt"), StandardCharsets.UTF_8).replace("**", ""));
		Path plain = write(assetsOf(mod).resolve("init/animal/PLAIN.txt"), new String(vanilla("init/animal/" + firstAnimal() + ".txt"), StandardCharsets.UTF_8).replace("**", ""));
		write(assetsOf(mod).resolve("text/animal/PLAIN.txt"), "INFO: {\n\tNAME: \"Stars ** in text\",\n\tDESC: \"None.\",\n},\n");
		GameAssetIndex index = indexOf(mod);
		expect("a comment is noticed", open(mod, noted, index).comments());
		expect("** inside text isn't a comment", !open(mod, plain, index).comments());
	}

	private static long countFiles(Path folder) throws IOException {
		try (Stream<Path> walk = Files.walk(folder)) {
			return walk.count();
		}
	}

	private static void deleteFolder(Path folder) throws IOException {
		List<Path> paths = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(folder)) {
			walk.sorted(Comparator.reverseOrder()).forEach(paths::add);
		}
		for (Path path : paths) {
			Files.deleteIfExists(path);
		}
	}

	/** A copied race keeps the game's file names. Editing one field writes no companion file. */
	private static void copiedRaces() throws Exception {
		AssetCatalog.Entry race = entry("Race");
		expect("Race can be edited: " + race.unavailableReason(), race.available());
		if (!race.available()) {
			return;
		}
		ModEntry mod = ModCreator.create(mods, game, "Copied Races");
		GameAssetIndex index = indexOf(mod);
		for (String stem : List.of("ARGONOSH", "CANTOR", "CRETONIAN", "DONDORIAN", "GARTHIMI", "HUMAN", "Q_AMEVIA", "TILAPI")) {
			Path data = write(assetsOf(mod).resolve("init/race/" + stem + "COPY.txt"), new String(vanilla("init/race/" + stem + ".txt"), StandardCharsets.UTF_8));
			List<Path> before = filesUnder(assetsOf(mod));
			AssetEditing.Opened opened = open(mod, data, index);
			String appearance = opened.values().get("SPRITE_FILE");
			expect(stem + " still names the game's appearance: " + appearance, appearance != null && !appearance.endsWith("COPY"));
			Map<String, String> changed = new LinkedHashMap<>(opened.values());
			changed.put("PLAYABLE", "true".equals(changed.get("PLAYABLE")) ? "false" : "true");
			AssetCreator.save(opened, opened.values(), changed, false, parser, index);
			expect(stem + " was edited", Files.readString(data).contains("PLAYABLE: " + changed.get("PLAYABLE")));
			List<Path> after = new ArrayList<>(filesUnder(assetsOf(mod)));
			after.removeAll(before);
			expect(stem + " created no other file: " + after, after.isEmpty());
		}
	}

	private static List<Path> filesUnder(Path folder) throws IOException {
		if (!Files.isDirectory(folder)) {
			return List.of();
		}
		try (Stream<Path> walk = Files.walk(folder)) {
			return walk.filter(Files::isRegularFile).toList();
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
