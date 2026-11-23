package syx.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import syx.core.AssetEditing;
import syx.core.AssetForm;
import syx.core.AssetCatalog;
import syx.core.AssetChecker;
import syx.core.AssetCreator;
import syx.core.AssetSchema;
import syx.core.AssetType;
import syx.core.AssetVanillaCheck;
import syx.core.ClassFileScanner;
import syx.core.GameAssetIndex;
import syx.core.GameInstall;
import syx.core.GameParser;
import syx.core.ModCreator;
import syx.core.ModEntry;
import syx.core.ResourceProducers;
import syx.core.SoundEntries;
import syx.core.TagSpec;

/**
 * Tests the jar scan, tag rules and asset creation. Everything needs the game, because the rules
 * come from the user's own jar, so it all skips without a game folder. Vanilla files are read from
 * data.zip at runtime and copied only to a temp folder.
 * Usage: AssetSchemaTest [game folder]
 */
public final class AssetSchemaTest {

	private static int passed;
	private static int failed;

	private AssetSchemaTest() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			System.out.println("SKIPPED: no game folder given.");
			System.out.println("passed=0 failed=0");
			return;
		}
		Path game = Paths.get(args[0]);
		Path temp = Files.createTempDirectory("syx-asset-test");
		try {
			List<AssetCatalog.Entry> entries = AssetCatalog.load(GameInstall.jarIn(game));
			AssetCatalog.Entry animal = entries.stream().filter(e -> e.type().label().equals("Animal")).findFirst().orElseThrow();
			expect("Animal is available: " + animal.unavailableReason(), animal.available());
			if (animal.available()) {
				scannerMatchesSource(animal.schema());
				catalogue(entries);
				gateHolds(game);
				try (GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
					Path mods = temp.resolve("mods");
					ModEntry mod = ModCreator.create(mods, game, "Asset Test");
					GameParser.GameVersion version = parser.version();
					GameAssetIndex index = GameAssetIndex.load(game, AssetCreator.versionFolder(mod.root(), version));
					references(index);
					vanillaAnimals(game, animal.schema(), index, temp);
					vanillaText(game, animal.schema(), temp);
					changedLoaders(game, temp);
					scannerFollowsCode(temp);
					gameParserAgrees(parser, temp);
					valueRules(animal.schema(), index);
					names(animal, index);
					creation(animal, mod, parser, version, index, game);
					versionFolderWarning(mods, version, game);
					modSprites(animal.schema(), mods, version, game);
					AssetCatalog.Entry resource = entries.stream().filter(e -> e.type().label().equals("Resource")).findFirst().orElseThrow();
					expect("Resource is available: " + resource.unavailableReason(), resource.available());
					if (resource.available()) {
						resourceMatchesSource(resource.schema());
						resourceGate(game, temp);
						vanillaResources(game, resource.schema(), index, temp);
						resourceText(game, resource.schema(), temp);
						resourceRules(resource.schema(), index);
						resourceCreation(resource, mods, parser, version, game);
						resourceSprites(resource.schema(), mods, version, game);
						producers(mods, parser, version, game);
						pinnedOutput(entries, mods, parser, version, game);
						rooms(entries, game, mods, parser, version, index, temp);
					}
					AssetCatalog.Entry race = entries.stream().filter(e -> e.type().label().equals("Race")).findFirst().orElseThrow();
					expect("Race is available: " + race.unavailableReason(), race.available());
					if (race.available()) {
						raceTable(race.schema());
						raceGate(game, temp);
						vanillaRaces(game, race.schema(), index, temp, parser);
						raceRules(race.schema(), index);
						raceCreation(race, mod, parser, game);
						raceWildcardOrder(race, mod, parser, game);
					}
					AssetCatalog.Entry playerMode = entryNamed(entries, "Player mode");
					expect("Player mode is available: " + playerMode.unavailableReason(), playerMode.available());
					if (playerMode.available()) {
						modeTable(playerMode);
						modeGate(game, temp);
						vanillaModes(game, playerMode.schema(), index, temp, parser);
						modeRules(playerMode.schema(), index);
						modeCreation(playerMode, mod, parser, game);
					}
					monument(entries, game, mods, parser, version, index, temp);
				}
			}
		} finally {
			deleteFolder(temp);
		}
		System.out.println("passed=" + passed + " failed=" + failed);
		System.exit(failed == 0 ? 0 : 1);
	}

	/** The expected values below were checked by hand against AnimalSpecies.java:71-108. */
	private static void scannerMatchesSource(AssetSchema schema) {
		expectTag(schema, "CARAVAN", TagSpec.Kind.BOOL, true, null, null, null, true);
		expectTag(schema, "MASS", TagSpec.Kind.INT, true, null, 1.0, 500.0, false);
		expectTag(schema, "SPEED", TagSpec.Kind.INT, true, null, 1.0, 31.0, false);
		expectTag(schema, "HEIGHT", TagSpec.Kind.INT, true, null, 0.0, 50.0, false);
		expectTag(schema, "PACK", TagSpec.Kind.BOOL, true, null, null, null, true);
		expectTag(schema, "GRAZES", TagSpec.Kind.BOOL, true, null, null, null, true);
		expectTag(schema, "DANGER", TagSpec.Kind.DECIMAL, true, null, 0.0, 1.0, true);
		expectTag(schema, "LIVES_IN_CAVES", TagSpec.Kind.DECIMAL, true, null, 0.0, 1.0, true);
		expectTag(schema, "SPRITE", TagSpec.Kind.SPRITE, true, null, null, null, true);
		expectTag(schema, "ICON", TagSpec.Kind.ICON, true, null, null, null, true);
		expectTag(schema, "COLOR", TagSpec.Kind.COLOR, true, null, 0.0, 255.0, true);
		expectTag(schema, "RESOURCES", TagSpec.Kind.KEY_LIST, false, "Empty", null, null, true);
		expectTag(schema, "RESOURCE_AMOUNT", TagSpec.Kind.LINKED_DECIMALS, true, null, null, null, true);
		expectTag(schema, "CLIMATE", TagSpec.Kind.DECIMAL_MAP, false, "0 for every entry", 0.0, 1.0, true);
		expectTag(schema, "TERRAIN", TagSpec.Kind.DECIMAL_MAP, false, "0 for every entry", 0.0, 1.0, true);
		expectTag(schema, "DAMAGE", TagSpec.Kind.DECIMAL_MAP, false, "0 for every entry", 0.0, 10000.0, true);
		expect("exactly the tags in the source: " + schema.dataTags().stream().map(TagSpec::key).toList(), schema.dataTags().size() == 16);
		expect("the sprite's required size was scanned", schema.tag("SPRITE").sprite().equals(new TagSpec.Sprite("sprite/animal/", 164, 462)));
		for (TagSpec tag : schema.dataTags()) {
			expect(tag.key() + " records evidence: " + tag.evidence(), tag.evidence().startsWith("AnimalSpecies.java:"));
			expect(tag.key() + " records confidence", tag.confidence() == TagSpec.Confidence.CODE_VERIFIED);
		}
		expect("line numbers come through", schema.tag("MASS").evidence().equals("AnimalSpecies.java:73"));
		expect("the text tags come from INFO", schema.textTags().stream().map(TagSpec::key).toList().equals(List.of("NAME", "NAMES", "DESC", "WIKI")));
		expect("a tag with a default is ghosted", schema.tag("CLIMATE").ghosted());
		expect("NAMES' default is the name", schema.tag("NAMES").ghosted());
		expect("a tag with no default isn't ghosted", !schema.tag("WIKI").ghosted() && schema.tag("WIKI").addOnly());
		expect("a required tag isn't ghosted", !schema.tag("MASS").ghosted() && !schema.tag("MASS").addOnly());
	}

	private static void expectTag(AssetSchema schema, String key, TagSpec.Kind kind, boolean required, String gameDefault, Double min, Double max, boolean maxEnforced) {
		TagSpec tag = schema.tag(key);
		expect(key + " was found", tag != null);
		if (tag != null) {
			expect(key + " kind: " + tag.kind(), tag.kind() == kind);
			expect(key + " required: " + tag.required(), tag.required() == required);
			expect(key + " default: " + tag.gameDefault(), java.util.Objects.equals(tag.gameDefault(), gameDefault));
			expect(key + " min: " + tag.min(), java.util.Objects.equals(tag.min(), min));
			expect(key + " max: " + tag.max(), java.util.Objects.equals(tag.max(), max));
			expect(key + " enforcement: " + tag.maxEnforced(), tag.maxEnforced() == maxEnforced);
		}
	}

	private static void catalogue(List<AssetCatalog.Entry> entries) {
		expect("every type is listed: " + entries.size(), entries.size() == 67);
		List<String> enabled = entries.stream().filter(AssetCatalog.Entry::available).map(e -> e.type().label()).toList();
		expect("only the reviewed types are enabled: " + enabled, enabled.equals(List.of("Animal", "Player mode", "Race", "Resource", "Crop", "Minable resource", "Farm", "Mine", "Refiner", "Workshop", "Pasture", "Fishery", "Orchard", "Hunter", "Temple", "Shrine",
				"Tavern", "Eatery", "Canteen", "Well", "Bath", "Lavatory", "Barber", "Physician", "Pleasure", "Market", "Stage", "Speaker", "Fight pit", "Grand arena", "Court", "Prison", "Stockade", "Guard", "Police", "Stocks", "Execution", "Monument")));
		List<String> grouped = entries.stream().filter(e -> "Rooms".equals(e.type().group())).map(e -> e.type().label()).toList();
		expect("the room types are the Rooms group: " + grouped, grouped.equals(List.of("Farm", "Mine", "Refiner", "Workshop", "Pasture", "Fishery", "Orchard", "Hunter", "Temple", "Shrine", "Tavern", "Eatery", "Canteen",
				"Well", "Bath", "Lavatory", "Barber", "Physician", "Pleasure", "Market", "Stage", "Speaker", "Fight pit", "Grand arena", "Court", "Prison", "Stockade", "Guard", "Police", "Stocks", "Execution", "Monument")));
		expect("the generic Room isn't in it", entryNamed(entries, "Room").type().group() == null);
		for (AssetCatalog.Entry entry : entries) {
			if (!entry.available()) {
				expect(entry.type().label() + " says why it's off", entry.unavailableReason().equals(AssetCatalog.NOT_VERIFIED));
			}
		}
		Set<String> labels = new HashSet<>();
		for (AssetCatalog.Entry entry : entries) {
			expect(entry.type().label() + " is listed once", labels.add(entry.type().label()));
			expect(entry.type().label() + " isn't a fixed-name folder", !entry.type().initFolder().equals("config") && !entry.type().initFolder().equals("settlement/environment"));
		}
	}

	private static void gateHolds(Path game) throws IOException {
		for (String[] rule : new String[][] {
				{ "init/sprite/UI/Icons", "get" },
				{ "snake2d/util/color/ColorImp", "<init>" },
				{ "util/keymap/RMAPS", "readMany" },
				{ "util/keymap/RMAP", "readFill" },
				{ "util/info/INFO", "<init>" },
				{ "KJson", "" } }) {
			AssetCatalog.Entry entry = AssetCatalog.loadWithoutHelper(GameInstall.jarIn(game), rule[0], rule[1]).stream()
					.filter(e -> e.type().label().equals("Animal")).findFirst().orElseThrow();
			expect("without the " + rule[0] + " rule, Animal is disabled", !entry.available());
			expect("and it says why: " + entry.unavailableReason(), entry.unavailableReason() != null && entry.unavailableReason().contains("reviewed"));
		}
	}

	/** Vanilla is the test: zero errors, and every warning must be justified. */
	private static void vanillaAnimals(Path game, AssetSchema schema, GameAssetIndex index, Path temp) throws IOException, GameParser.UnavailableException {
		Path folder = temp.resolve("vanilla");
		Files.createDirectories(folder);
		int count = 0;
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile());
				GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (entry.getName().matches("data/assets/init/animal/[^/]+\\.txt")) {
					Path copy = folder.resolve(entry.getName().substring(entry.getName().lastIndexOf('/') + 1));
					try (InputStream in = zip.getInputStream(entry)) {
						Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
					}
					count++;
					List<AssetChecker.Finding> findings = AssetVanillaCheck.check(schema, index, copy, parser);
					expect(copy.getFileName() + " has no errors or warnings: " + findings, findings.isEmpty());
				}
			}
		}
		expect("all 9 vanilla animals were checked: " + count, count == 9);
	}

	/** Every vanilla text file uses only the tags in the schema. */
	private static void vanillaText(Path game, AssetSchema schema, Path temp) throws IOException, GameParser.UnavailableException {
		Path folder = temp.resolve("vanilla-text");
		Files.createDirectories(folder);
		int count = 0;
		List<String> known = schema.textTags().stream().map(TagSpec::key).toList();
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile());
				GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (entry.getName().matches("data/assets/text/animal/[^/]+\\.txt")) {
					Path copy = folder.resolve(entry.getName().substring(entry.getName().lastIndexOf('/') + 1));
					try (InputStream in = zip.getInputStream(entry)) {
						Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
					}
					count++;
					List<String> keys = parser.blockKeys(copy, "INFO");
					expect(copy.getFileName() + " has an INFO block with known tags: " + keys, keys != null && known.containsAll(keys));
					expect(copy.getFileName() + " has its required tags", keys != null && keys.contains("NAME") && keys.contains("DESC"));
				}
			}
		}
		expect("vanilla animal text was checked: " + count, count == 9);
	}

	/** If a game update changes the loader, the type turns off rather than offering a wrong form. */
	private static void changedLoaders(Path game, Path temp) throws IOException {
		Path missing = temp.resolve("no-loader.jar");
		writeJar(game, missing, null);
		AssetCatalog.Entry gone = animalIn(missing);
		expect("a jar without the loader disables Animal: " + gone.unavailableReason(), !gone.available() && gone.unavailableReason().contains("missing"));

		// A class with a different constructor stands in for a loader that has changed shape.
		Path changed = temp.resolve("changed-loader.jar");
		writeJar(game, changed, "init/resources/RESOURCE.class");
		AssetCatalog.Entry different = animalIn(changed);
		expect("a changed loader disables Animal: " + different.unavailableReason(), !different.available() && different.unavailableReason().contains("constructor"));

		Path notAJar = temp.resolve("not-a-jar.jar");
		Files.writeString(notAJar, "This isn't a jar.");
		AssetCatalog.Entry broken = animalIn(notAJar);
		expect("an unreadable jar disables Animal: " + broken.unavailableReason(), !broken.available());
	}

	private static AssetCatalog.Entry animalIn(Path jar) {
		return AssetCatalog.load(jar).stream().filter(e -> e.type().label().equals("Animal")).findFirst().orElseThrow();
	}

	/** Makes a tiny temp jar where the loader's entry holds another class, or nothing at all. */
	private static void writeJar(Path game, Path target, String standIn) throws IOException {
		try (ZipFile jar = new ZipFile(GameInstall.jarIn(game).toFile());
				ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(target))) {
			out.putNextEntry(new ZipEntry(standIn == null ? "placeholder.txt" : "settlement/entity/animal/AnimalSpecies.class"));
			if (standIn != null) {
				try (InputStream in = jar.getInputStream(jar.getEntry(standIn))) {
					in.transferTo(out);
				}
			}
			out.closeEntry();
		}
	}

	/** Code the scan must follow correctly. Only its compiled form is read, and it's never run. */
	static final class Fixture {
		private Fixture() {
		}

		static int always(long wide, double other) {
			return take("KEY", 7, 1.5, wide, other);
		}

		static int inBranch(boolean flag) {
			if (flag) {
				return take("BRANCH", 1, 2.0, 0L, 0.0);
			}
			return take("AFTER", 2, 3.0, 0L, 0.0);
		}

		static int inElse(boolean flag) {
			int result;
			if (flag) {
				result = 1;
			} else {
				result = take("ELSE", 1, 2.0, 0L, 0.0);
			}
			return result;
		}

		static int inLoop(int times) {
			int total = 0;
			for (int i = 0; i < times; i++) {
				total += take("LOOP", i, 2.0, 0L, 0.0);
			}
			return total;
		}

		static int ternary(boolean flag) {
			return take(flag ? "ONE" : "TWO", 1, 2.0, 0L, 0.0);
		}

		static int builtKey(String part) {
			return take("A" + part, 1, 2.0, 0L, 0.0);
		}

		static int inTry() {
			try {
				return take("TRY", 1, 2.0, 0L, 0.0);
			} catch (RuntimeException e) {
				return take("CATCH", 1, 2.0, 0L, 0.0);
			}
		}

		static StringBuilder kept;

		static int twoNew(String first, String second) {
			StringBuilder one = new StringBuilder(first.trim());
			StringBuilder two = new StringBuilder(second.trim());
			kept = two;
			return take(one.toString(), 1, 2.0, 0L, 0.0);
		}

		static int take(String key, int number, double decimal, long wide, double other) {
			return key.length() + number + (int) decimal + (int) wide + (int) other;
		}
	}

	private static void scannerFollowsCode(Path temp) throws Exception {
		String name = Fixture.class.getName().replace('.', '/') + ".class";
		Path jar = temp.resolve("fixture.jar");
		try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar));
				InputStream in = Fixture.class.getClassLoader().getResourceAsStream(name)) {
			out.putNextEntry(new ZipEntry(name));
			in.transferTo(out);
			out.closeEntry();
		}
		try (ClassFileScanner scanner = ClassFileScanner.open(jar)) {
			ClassFileScanner.ScannedClass scanned = scanner.scan(Fixture.class.getName());
			expect("a missing class gives null", scanner.scan("no.such.Thing") == null);

			ClassFileScanner.Call always = onlyTake(scanned, "always");
			expect("a string constant is read", always.args().get(0).equals(new ClassFileScanner.Const("KEY")));
			expect("an int constant is read", always.args().get(1).equals(new ClassFileScanner.Const(7)));
			expect("a double constant is read", always.args().get(2).equals(new ClassFileScanner.Const(1.5)));
			expect("a wide parameter's slot is right: " + always.args().get(3), always.args().get(3).equals(new ClassFileScanner.Local(0)));
			expect("the parameter after it is right: " + always.args().get(4), always.args().get(4).equals(new ClassFileScanner.Local(2)));
			expect("a plain call isn't conditional", !always.conditional());
			expect("it records the source file", "AssetSchemaTest.java".equals(scanned.sourceFile()));
			expect("it records the line", always.line() > 0);

			List<ClassFileScanner.Call> branch = takes(scanned, "inBranch");
			expect("a call in a branch is conditional", branch.get(0).args().get(0).equals(new ClassFileScanner.Const("BRANCH")) && branch.get(0).conditional());
			expect("a call after a branch that returns is followed", branch.get(1).args().get(0).equals(new ClassFileScanner.Const("AFTER")));
			expect("a call in an else is conditional", onlyTake(scanned, "inElse").conditional());
			expect("a call in a loop is conditional", onlyTake(scanned, "inLoop").conditional());
			expect("a loop counter isn't a constant", !(onlyTake(scanned, "inLoop").args().get(1) instanceof ClassFileScanner.Const));

			// Where two paths give different values, it says it doesn't know, rather than picking one.
			expect("a ternary's value is unknown: " + onlyTake(scanned, "ternary").args().get(0), onlyTake(scanned, "ternary").args().get(0) instanceof ClassFileScanner.Unknown);
			expect("a built key isn't a constant: " + onlyTake(scanned, "builtKey").args().get(0), !(onlyTake(scanned, "builtKey").args().get(0) instanceof ClassFileScanner.Const));

			List<ClassFileScanner.Call> attempt = takes(scanned, "inTry");
			expect("a catch block is conditional", attempt.size() == 2 && attempt.get(1).conditional());

			// Two new objects of one class are told apart, and so are the things they were built from.
			ClassFileScanner.ScannedMethod twoNew = scanned.methods().stream().filter(m -> m.name().equals("twoNew")).findFirst().orElseThrow();
			List<ClassFileScanner.Call> built = twoNew.calls().stream().filter(c -> c.owner().equals("java/lang/StringBuilder") && c.name().equals("<init>")).toList();
			expect("two new objects are both found: " + built.size(), built.size() == 2);
			int firstSite = ((ClassFileScanner.New) built.get(0).receiver()).site();
			int secondSite = ((ClassFileScanner.New) built.get(1).receiver()).site();
			expect("two new objects are told apart", firstSite != secondSite);
			expect("a returned value knows what it was called on: " + built.get(0).args().get(0), built.get(0).args().get(0) instanceof ClassFileScanner.Returned r && r.receiver().equals(new ClassFileScanner.Local(0)));
			expect("the second is built from the second parameter", built.get(1).args().get(0) instanceof ClassFileScanner.Returned r && r.receiver().equals(new ClassFileScanner.Local(1)));
			expect("a stored new object is reported: " + twoNew.escapes(), twoNew.escapes().stream().anyMatch(e -> e.site() == secondSite));
			expect("one that isn't stored isn't", twoNew.escapes().stream().noneMatch(e -> e.site() == firstSite));
		}
	}

	private static List<ClassFileScanner.Call> takes(ClassFileScanner.ScannedClass scanned, String method) {
		return scanned.methods().stream().filter(m -> m.name().equals(method)).findFirst().orElseThrow()
				.calls().stream().filter(c -> c.name().equals("take")).toList();
	}

	private static ClassFileScanner.Call onlyTake(ClassFileScanner.ScannedClass scanned, String method) {
		return takes(scanned, method).get(0);
	}

	/**
	 * The rules claim things about the game's own parser, so they're tested against it. The file holds
	 * deliberately wrong values, and each read goes through the real Json class.
	 */
	private static void gameParserAgrees(GameParser parser, Path temp) throws IOException {
		Path file = temp.resolve("wrong.txt");
		Files.writeString(file, "MASS_LOW: 0,\nMASS_HIGH: 501,\nDANGER_HIGH: 5,\nBOOL: yes,\nWHOLE: 1.5,\nLIST: [1, 2,],\nBAD_LIST: [1, x,],\nBLOCK: {\n\tA: 2,\n},\n");
		expect("Json.i refuses below its minimum", !read(parser, file, List.of(), "i", "MASS_LOW", 1, 500).outcome().ok());
		GameParser.ReadResult high = read(parser, file, List.of(), "i", "MASS_HIGH", 1, 500);
		expect("Json.i accepts above its maximum, as the warning says: " + high.outcome(), high.outcome().ok() && Integer.valueOf(501).equals(high.value()));
		expect("Json.d refuses above its maximum", !read(parser, file, List.of(), "d", "DANGER_HIGH", 0.0, 1.0).outcome().ok());
		expect("Json.d's refusal is a data error", read(parser, file, List.of(), "d", "DANGER_HIGH", 0.0, 1.0).outcome().dataError() != null);
		expect("Json.bool refuses anything but true or false", !read(parser, file, List.of(), "bool", "BOOL").outcome().ok());
		expect("Json.i refuses a decimal", !read(parser, file, List.of(), "i", "WHOLE", 0, 5).outcome().ok());
		expect("Json.ds refuses the wrong length", !read(parser, file, List.of(), "ds", "LIST", 3).outcome().ok());
		expect("Json.ds accepts the right length", read(parser, file, List.of(), "ds", "LIST", 2).outcome().ok());
		expect("Json.ds refuses a non-number, which a plain parse misses", !read(parser, file, List.of(), "ds", "BAD_LIST").outcome().ok() && parser.parse(file).ok());
		expect("a block's values are range checked", !read(parser, file, List.of("BLOCK"), "d", "A", 0.0, 1.0).outcome().ok());
		expect("a missing tag is refused", !read(parser, file, List.of(), "bool", "NOT_THERE").outcome().ok());
	}

	private static GameParser.ReadResult read(GameParser parser, Path file, List<String> block, String method, Object... args) {
		return parser.read(file, List.of(new GameParser.Read(block, method, List.of(args)))).get(0);
	}

	private static void references(GameAssetIndex index) {
		List<String> resources = index.options(TagSpec.Reference.RESOURCES);
		expect("resources include MEAT: " + resources, resources.contains("MEAT"));
		expect("a leading _ works without it", resources.contains("STONE") && !resources.contains("_STONE"));
		expect("subfolders aren't resources", !resources.contains("edible"));
		expect("damage types are read: " + index.options(TagSpec.Reference.DAMAGES), index.options(TagSpec.Reference.DAMAGES).containsAll(List.of("PIERCE", "SLASH")));
		List<String> sprites = index.spriteOptions("sprite/animal/");
		expect("sprites include Auroch: " + sprites, sprites.contains("Auroch"));
		expect("the shared texture isn't offered", !sprites.contains("_Texture"));
		for (String sprite : sprites) {
			GameAssetIndex.ImageSize size = index.spriteSize("sprite/animal/", sprite);
			expect(sprite + " has the size the game requires: " + size, size.width() == 164 && size.height() >= 462);
		}
		List<String> icons = index.options(TagSpec.Reference.ICONS);
		expect("icons are written as the tag wants: " + icons.size(), icons.contains("32->animal->Auroch"));
		expect("a vanilla sheet has icons: " + index.iconCount("32->animal->Auroch"), index.iconCount("32->animal->Auroch") >= 1);
		expect("a missing sheet has none", index.iconCount("32->animal->Nothing") == -1);
	}

	private static Map<String, String> validAnimal() {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("ICON", "32->animal->Auroch->0");
		values.put("CARAVAN", "true");
		values.put("MASS", "100");
		values.put("SPEED", "4");
		values.put("HEIGHT", "5");
		values.put("COLOR", "200_100_50");
		values.put("RESOURCES", "MEAT,LEATHER");
		values.put("RESOURCE_AMOUNT", "0.8,0.5");
		values.put("CLIMATE.COLD", "0.8");
		values.put("CLIMATE.HOT", "0");
		values.put("TERRAIN.FOREST", "1");
		values.put("DAMAGE.PIERCE", "0.4");
		values.put("PACK", "true");
		values.put("GRAZES", "false");
		values.put("DANGER", "0.5");
		values.put("LIVES_IN_CAVES", "0");
		values.put("SPRITE", "Auroch");
		values.put("SOUND.ANIMAL_CALL_", "animal->Bull*");
		values.put("NAME", "Test Beast");
		values.put("DESC", "It's for testing.");
		return values;
	}

	private static List<AssetChecker.Finding> errorsWith(AssetSchema schema, GameAssetIndex index, String key, String value) {
		Map<String, String> values = validAnimal();
		if (value == null) {
			values.remove(key);
		} else {
			values.put(key, value);
		}
		return AssetChecker.check(schema, index, values).stream().filter(AssetChecker.Finding::blocksSaving).toList();
	}

	private static void expectBlocks(AssetSchema schema, GameAssetIndex index, String key, String value) {
		List<AssetChecker.Finding> errors = errorsWith(schema, index, key, value);
		expect(key + "=" + value + " is refused", !errors.isEmpty());
	}

	private static void expectAllowed(AssetSchema schema, GameAssetIndex index, String key, String value) {
		List<AssetChecker.Finding> errors = errorsWith(schema, index, key, value);
		expect(key + "=" + value + " is accepted: " + errors, errors.isEmpty());
	}

	private static void valueRules(AssetSchema schema, GameAssetIndex index) {
		List<AssetChecker.Finding> all = AssetChecker.check(schema, index, validAnimal());
		expect("a good animal has no problems: " + all, all.isEmpty());

		// Json.i only enforces the minimum, so above the maximum warns and doesn't block.
		expectAllowed(schema, index, "MASS", "1");
		expectBlocks(schema, index, "MASS", "0");
		expectAllowed(schema, index, "MASS", "500");
		expectAllowed(schema, index, "MASS", "501");
		Map<String, String> tooHeavy = validAnimal();
		tooHeavy.put("MASS", "501");
		expect("above an unenforced maximum warns", AssetChecker.check(schema, index, tooHeavy).stream()
				.anyMatch(f -> f.severity() == AssetChecker.Severity.WARNING && "MASS".equals(f.tag())));
		expectBlocks(schema, index, "MASS", "1.5");
		expectBlocks(schema, index, "MASS", "abc");
		expectBlocks(schema, index, "MASS", "99999999999");
		expectBlocks(schema, index, "MASS", "");
		expectBlocks(schema, index, "MASS", null);

		// Json.d enforces both ends.
		expectAllowed(schema, index, "DANGER", "0");
		expectAllowed(schema, index, "DANGER", "1");
		expectAllowed(schema, index, "DANGER", ".5");
		expectBlocks(schema, index, "DANGER", "1.01");
		expectBlocks(schema, index, "DANGER", "-0.01");
		expectBlocks(schema, index, "DANGER", "1e0");

		expectBlocks(schema, index, "PACK", "yes");
		expectBlocks(schema, index, "PACK", "TRUE");
		expectBlocks(schema, index, "PACK", null);

		expectAllowed(schema, index, "COLOR", "0_0_0");
		expectAllowed(schema, index, "COLOR", "255_255_255");
		expectBlocks(schema, index, "COLOR", "256_0_0");
		expectBlocks(schema, index, "COLOR", "1_2");
		expectBlocks(schema, index, "COLOR", "a_b_c");

		expectBlocks(schema, index, "SPRITE", "NoSuchSprite");
		expectBlocks(schema, index, "ICON", "32->animal->NoSuchSheet->0");
		expectBlocks(schema, index, "ICON", "32->animal->Auroch");

		expectBlocks(schema, index, "RESOURCES", "NO_SUCH_RESOURCE,MEAT");
		expectBlocks(schema, index, "RESOURCE_AMOUNT", "0.8");
		expectBlocks(schema, index, "RESOURCE_AMOUNT", "0.8,x");
		expectAllowed(schema, index, "RESOURCES", "STONE,MEAT");

		expectBlocks(schema, index, "CLIMATE.COLD", "1.5");
		expectBlocks(schema, index, "CLIMATE.COLD", "abc");
		expectAllowed(schema, index, "DAMAGE.PIERCE", "10000");
		expectBlocks(schema, index, "DAMAGE.PIERCE", "10001");

		expectBlocks(schema, index, "NAME", "");
		expectBlocks(schema, index, "NAME", null);
		expectBlocks(schema, index, "NAME", "two\nlines");
		expectBlocks(schema, index, "DESC", "bad\",value");
		expectAllowed(schema, index, "DESC", "");
		expectBlocks(schema, index, "DESC", null);
		expectAllowed(schema, index, "NAMES", "Test Beasts");

		expectBlocks(schema, index, "WIKI.NAME", "Only a name");

		// It would load, but never appear, so the user is told and not stopped.
		Map<String, String> neverSeen = validAnimal();
		neverSeen.remove("TERRAIN.FOREST");
		List<AssetChecker.Finding> findings = AssetChecker.check(schema, index, neverSeen);
		expect("no terrain warns that it won't appear: " + findings, findings.size() == 1
				&& findings.get(0).severity() == AssetChecker.Severity.WARNING && findings.get(0).message().startsWith("TERRAIN"));
	}

	private static void names(AssetCatalog.Entry animal, GameAssetIndex index) {
		expect("a good name works", AssetCreator.problemWithName(animal.type(), "NEW_BEAST_2", index) == null);
		for (String bad : List.of("", "_HIDDEN", "BAD NAME", "BAD*", "BAD,NAME", "BAD.NAME", "CON", "AUROCH", "auroch")) {
			expect("the name '" + bad + "' is refused", AssetCreator.problemWithName(animal.type(), bad, index) != null);
		}
	}

	private static long countFiles(Path folder) throws IOException {
		try (Stream<Path> walk = Files.walk(folder)) {
			return walk.count();
		}
	}

	private static void creation(AssetCatalog.Entry animal, ModEntry mod, GameParser parser, GameParser.GameVersion version, GameAssetIndex index, Path game) throws Exception {
		AssetSchema schema = animal.schema();
		Path root = mod.root();

		// A bad value writes nothing at all.
		long before = countFiles(root);
		Map<String, String> bad = validAnimal();
		bad.put("DANGER", "5");
		expectRefused(root, animal, "BAD_BEAST", bad, parser, index, "DANGER");
		expect("a refused asset leaves nothing behind", countFiles(root) == before);

		// A good one goes in the right places.
		Map<String, String> values = validAnimal();
		values.put("NAMES", "Test Beasts");
		values.put("WIKI.NAME", "Test Beast");
		values.put("WIKI.CATEGORY", "Animals");
		values.put("WIKI.TEXT", "Line one.\nLine two.");
		values.put("DESC", "It's for testing.\nOn two lines.");
		AssetCreator.Created created = AssetCreator.create(root, animal.type(), schema, "TEST_BEAST", values, parser, index);
		Path assets = root.resolve("V" + version.major()).resolve("assets");
		expect("the data file is in place", created.dataFile().equals(assets.resolve("init/animal/TEST_BEAST.txt")) && Files.isRegularFile(created.dataFile()));
		expect("the text file is in place", created.textFile().equals(assets.resolve("text/animal/TEST_BEAST.txt")) && Files.isRegularFile(created.textFile()));
		try (Stream<Path> walk = Files.walk(root)) {
			expect("no temp files are left", walk.noneMatch(p -> p.toString().endsWith(".syxtmp")));
		}

		// The real files pass the same check as vanilla, and the game reads back what was entered.
		List<AssetChecker.Finding> findings = AssetVanillaCheck.check(schema, index, created.dataFile(), parser);
		expect("the new file passes the vanilla check: " + findings, findings.isEmpty());
		List<GameParser.ReadResult> reads = parser.read(created.dataFile(), List.of(
				new GameParser.Read(List.of(), "i", List.of("MASS", 1, 500)),
				new GameParser.Read(List.of(), "bool", List.of("CARAVAN")),
				new GameParser.Read(List.of(), "value", List.of("SPRITE")),
				new GameParser.Read(List.of(), "values", List.of("RESOURCES")),
				new GameParser.Read(List.of(), "ds", List.of("RESOURCE_AMOUNT", 2)),
				new GameParser.Read(List.of("CLIMATE"), "d", List.of("COLD", 0.0, 1.0))));
		expect("MASS reads back: " + reads.get(0), Integer.valueOf(100).equals(reads.get(0).value()));
		expect("CARAVAN reads back", Boolean.TRUE.equals(reads.get(1).value()));
		expect("SPRITE reads back", "Auroch".equals(reads.get(2).value()));
		expect("RESOURCES reads back", java.util.Arrays.equals((String[]) reads.get(3).value(), new String[] { "MEAT", "LEATHER" }));
		expect("RESOURCE_AMOUNT reads back", java.util.Arrays.equals((double[]) reads.get(4).value(), new double[] { 0.8, 0.5 }));
		expect("CLIMATE reads back", Double.valueOf(0.8).equals(reads.get(5).value()));
		String dataFile = Files.readString(created.dataFile());
		expect("a ghosted tag that wasn't added isn't written: " + dataFile, !dataFile.contains("OCEAN"));
		String text = Files.readString(created.textFile());
		expect("the text is in an INFO block like vanilla: " + text, text.startsWith("INFO: {") && text.contains("NAME: \"Test Beast\""));
		List<GameParser.ReadResult> textReads = parser.read(created.textFile(), List.of(
				new GameParser.Read(List.of("INFO"), "text", List.of("NAME")),
				new GameParser.Read(List.of("INFO"), "text", List.of("DESC")),
				new GameParser.Read(List.of("INFO", "WIKI"), "text", List.of("TEXT"))));
		expect("NAME reads back", "Test Beast".equals(textReads.get(0).value()));
		expect("a two-line DESC reads back: " + textReads.get(1), "It's for testing.\nOn two lines.".equals(textReads.get(1).value()));
		expect("the WIKI text reads back: " + textReads.get(2), "Line one.\nLine two.".equals(textReads.get(2).value()));

		// It can't overwrite, even when the index is out of date.
		String beforeOverwrite = Files.readString(created.dataFile());
		expectRefused(root, animal, "TEST_BEAST", validAnimal(), parser, index, "");
		expect("the existing file is untouched", Files.readString(created.dataFile()).equals(beforeOverwrite));
		GameAssetIndex refreshed = GameAssetIndex.load(game, AssetCreator.versionFolder(root, version));
		expect("a refreshed index refuses the name", AssetCreator.problemWithName(animal.type(), "TEST_BEAST", refreshed) != null);

		// No resources still writes the empty list the game requires.
		Map<String, String> noResources = validAnimal();
		noResources.remove("RESOURCES");
		noResources.remove("RESOURCE_AMOUNT");
		AssetCreator.Created plain = AssetCreator.create(root, animal.type(), schema, "PLAIN_BEAST", noResources, parser, index);
		String plainFile = Files.readString(plain.dataFile());
		expect("the empty amount list is written: " + plainFile, plainFile.contains("RESOURCE_AMOUNT: [],"));
		expect("the absent list isn't written", !plainFile.contains("RESOURCES:"));
		expect("the game reads the empty list", parser.read(plain.dataFile(), List.of(new GameParser.Read(List.of(), "ds", List.of("RESOURCE_AMOUNT", 0)))).get(0).outcome().ok());

		// If the second folder can't be made, the first file and folder don't stay behind.
		ModEntry second = ModCreator.create(root.getParent(), game, "Rollback Test");
		Path secondAssets = AssetCreator.versionFolder(second.root(), version).resolve("assets");
		Path blocker = secondAssets.resolve("text").resolve("animal");
		Files.createDirectories(blocker.getParent());
		Files.writeString(blocker, "A file where a folder is needed.");
		long beforeRollback = countFiles(second.root());
		expectRefused(second.root(), animal, "ROLLBACK_BEAST", validAnimal(), parser, index, "");
		expect("a failed create leaves nothing behind", countFiles(second.root()) == beforeRollback);
		expect("and the blocker is untouched", Files.readString(blocker).startsWith("A file"));
	}

	private static void expectRefused(Path root, AssetCatalog.Entry animal, String name, Map<String, String> values, GameParser parser, GameAssetIndex index, String mentions) {
		try {
			AssetCreator.create(root, animal.type(), animal.schema(), name, values, parser, index);
			expect(name + " should have been refused", false);
		} catch (AssetCreator.CreationException e) {
			expect(name + " was refused: " + e.getMessage(), e.getMessage().contains(mentions));
		}
	}

	private static void versionFolderWarning(Path mods, GameParser.GameVersion version, Path game) throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Version Test");
		expect("no warning when the folder exists", AssetCreator.versionFolderWarning(mod.root(), version) == null);
		Files.move(mod.root().resolve("V" + version.major()), mod.root().resolve("V" + (version.major() - 1)));
		String warning = AssetCreator.versionFolderWarning(mod.root(), version);
		expect("a warning when only an older folder exists: " + warning, warning != null && warning.contains("V" + (version.major() - 1)));
	}

	/** A mod's own images count too, and a wrong size is caught because it would stop the game loading. */
	private static void modSprites(AssetSchema schema, Path mods, GameParser.GameVersion version, Path game) throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Sprite Test");
		Path assets = AssetCreator.versionFolder(mod.root(), version).resolve("assets");
		Path sprites = assets.resolve("sprite/animal");
		Files.createDirectories(sprites);
		Files.write(sprites.resolve("RightSize.png"), pngHeader(164, 462));
		Files.write(sprites.resolve("Taller.png"), pngHeader(164, 500));
		Files.write(sprites.resolve("TooWide.png"), pngHeader(165, 462));
		Files.write(sprites.resolve("TooShort.png"), pngHeader(164, 461));
		Files.writeString(sprites.resolve("NotAnImage.png"), "This isn't a PNG, and it's long enough to read a header from.");
		Path icons = assets.resolve("sprite/icon/32/mine");
		Files.createDirectories(icons);
		// One row of three icons: 3 x (32 + 6) + 6 = 120 wide, doubled for the normal map, and 44 tall.
		Files.write(icons.resolve("Good.png"), pngHeader(240, 44));
		Files.write(icons.resolve("Bad.png"), pngHeader(241, 50));
		GameAssetIndex index = GameAssetIndex.load(game, AssetCreator.versionFolder(mod.root(), version));

		expect("a mod's sprite is offered", index.spriteOptions("sprite/animal/").contains("RightSize"));
		expect("vanilla sprites are still offered", index.spriteOptions("sprite/animal/").contains("Auroch"));
		expectAllowed(schema, index, "SPRITE", "RightSize");
		expectAllowed(schema, index, "SPRITE", "Taller");
		expectBlocks(schema, index, "SPRITE", "TooWide");
		expectBlocks(schema, index, "SPRITE", "TooShort");
		expectBlocks(schema, index, "SPRITE", "NotAnImage");

		expect("a mod's icon sheet is offered", index.options(TagSpec.Reference.ICONS).contains("32->mine->Good"));
		expect("a sheet's icons are counted: " + index.iconCount("32->mine->Good"), index.iconCount("32->mine->Good") == 3);
		expect("a badly sized sheet has no count", index.iconCount("32->mine->Bad") == -1);
		expectAllowed(schema, index, "ICON", "32->mine->Good->2");
		expectBlocks(schema, index, "ICON", "32->mine->Bad->0");
		Map<String, String> pastTheEnd = validAnimal();
		pastTheEnd.put("ICON", "32->mine->Good->3");
		List<AssetChecker.Finding> findings = AssetChecker.check(schema, index, pastTheEnd);
		expect("a position past the end warns, and doesn't block: " + findings, findings.size() == 1 && findings.get(0).severity() == AssetChecker.Severity.WARNING);
	}

	private static byte[] pngHeader(int width, int height) {
		ByteBuffer header = ByteBuffer.allocate(24);
		header.putLong(0x89504E470D0A1A0AL).putInt(13).putInt(0x49484452).putInt(width).putInt(height);
		return header.array();
	}

	/** The expected values below were checked by hand against RESOURCE.java:90-104 and RESOURCES.java:73,80-81. */
	private static void resourceMatchesSource(AssetSchema schema) {
		expectTag(schema, "DEGRADE_RATE", TagSpec.Kind.DECIMAL, true, null, 0.0, 1.0, true);
		expectTag(schema, "COLOR", TagSpec.Kind.COLOR, true, null, 0.0, 255.0, true);
		expectTag(schema, "MINIMAP_COLOR", TagSpec.Kind.COLOR, true, null, 0.0, 255.0, true);
		expectTag(schema, "CATEGORY_DEFAULT", TagSpec.Kind.INT, true, null, 0.0, 9.0, true);
		expectTag(schema, "ICON", TagSpec.Kind.ICON, true, null, null, null, true);
		expectTag(schema, "PRICE_CAP", TagSpec.Kind.DECIMAL, false, "1.0", 0.0, 1.0, true);
		expectTag(schema, "PRICE_MUL", TagSpec.Kind.DECIMAL, false, "1.0", 0.0, 100.0, true);
		expectTag(schema, "SPRITE", TagSpec.Kind.SPRITE, true, null, null, null, true);
		expectTag(schema, "SPRITE_DEBRIS", TagSpec.Kind.SPRITE, true, null, null, null, true);
		expect("exactly the resource tags in the source: " + schema.dataTags().stream().map(TagSpec::key).toList(), schema.dataTags().size() == 9);
		expect("the sprite's size was scanned: " + schema.tag("SPRITE").sprite(), schema.tag("SPRITE").sprite().equals(new TagSpec.Sprite("sprite/resource/", 244, 94)));
		expect("the debris size was scanned: " + schema.tag("SPRITE_DEBRIS").sprite(), schema.tag("SPRITE_DEBRIS").sprite().equals(new TagSpec.Sprite("sprite/resource/debris/", 716, 28)));
		for (TagSpec tag : schema.dataTags()) {
			expect(tag.key() + " records evidence: " + tag.evidence(), tag.evidence().startsWith("RESOURCE.java:"));
			expect(tag.key() + " records confidence", tag.confidence() == TagSpec.Confidence.CODE_VERIFIED);
		}
		expect("the category's evidence names both reads: " + schema.tag("CATEGORY_DEFAULT").evidence(), schema.tag("CATEGORY_DEFAULT").evidence().contains("RESOURCES.java"));
		expect("the resource's text tags come from INFO", schema.textTags().stream().map(TagSpec::key).toList().equals(List.of("NAME", "NAMES", "DESC", "WIKI")));
		expect("a resource has nothing that decides if it spawns", schema.spawnBlocks().isEmpty());
		expect("prices are ghosted", schema.tag("PRICE_CAP").ghosted() && schema.tag("PRICE_MUL").ghosted());
		expect("the category range reads plainly: " + schema.tag("CATEGORY_DEFAULT").rangeText(), "0 to 9".equals(schema.tag("CATEGORY_DEFAULT").rangeText()));
	}

	/** Resource must turn off, and say why, if what its rules rest on has gone. */
	private static void resourceGate(Path game, Path temp) throws IOException {
		for (String[] rule : new String[][] {
				{ "init/sprite/UI/Icons", "get" },
				{ "snake2d/util/color/ColorImp", "<init>" },
				{ "util/info/INFO", "<init>" } }) {
			AssetCatalog.Entry entry = resourceFrom(AssetCatalog.loadWithoutHelper(GameInstall.jarIn(game), rule[0], rule[1]));
			expect("without the " + rule[0] + " rule, Resource is disabled", !entry.available());
			expect("and it says why: " + entry.unavailableReason(), entry.unavailableReason() != null && entry.unavailableReason().contains("reviewed"));
		}
		List<String> needed = List.of("init/resources/RESOURCE", "init/resources/RESOURCE$1", "init/resources/Sprite", "init/resources/Sprite$1", "init/resources/RESOURCES$Data", "init/sprite/UI/Icons");
		AssetCatalog.Entry control = resourceFrom(AssetCatalog.load(jarWith(game, temp.resolve("resource-all.jar"), needed)));
		expect("these classes are enough for Resource: " + control.unavailableReason(), control.available());
		for (String gone : needed) {
			String file = "resource-without-" + gone.substring(gone.lastIndexOf('/') + 1).replace('$', '_') + ".jar";
			AssetCatalog.Entry entry = resourceFrom(AssetCatalog.load(jarWith(game, temp.resolve(file), needed.stream().filter(n -> !n.equals(gone)).toList())));
			expect("without " + gone + ", Resource is disabled: " + entry.unavailableReason(), !entry.available());
		}
	}

	/** Copied from what the writer produced before it learned nesting, so Animal's and Resource's files can't change by accident. */
	private static void pinnedOutput(List<AssetCatalog.Entry> entries, Path mods, GameParser parser, GameParser.GameVersion version, Path game) throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Pinned Output");
		GameAssetIndex index = GameAssetIndex.load(game, AssetCreator.versionFolder(mod.root(), version));
		Map<String, String> good = new LinkedHashMap<>();
		good.put("DEGRADE_RATE", "0.5");
		good.put("COLOR", "255_255_255");
		good.put("MINIMAP_COLOR", "10_10_10");
		good.put("CATEGORY_DEFAULT", "4");
		good.put("ICON", "24->resource->Gem->0");
		good.put("SPRITE", "Gem");
		good.put("SPRITE_DEBRIS", "Wood");
		good.put("PRICE_MUL", "2.5");
		good.put("NAME", "Test Good");
		good.put("NAMES", "Test Goods");
		good.put("DESC", "It's for\ntesting.");
		AssetCatalog.Entry resource = entryNamed(entries, "Resource");
		AssetCreator.Created made = AssetCreator.create(mod.root(), resource.type(), resource.schema(), "PINNED_GOOD", good, parser, index);
		expect("a resource's data file is unchanged: " + Files.readString(made.dataFile()), Files.readString(made.dataFile()).equals(
				"DEGRADE_RATE: 0.5,\nCOLOR: 255_255_255,\nMINIMAP_COLOR: 10_10_10,\nCATEGORY_DEFAULT: 4,\nICON: 24->resource->Gem->0,\nPRICE_MUL: 2.5,\nSPRITE: Gem,\nSPRITE_DEBRIS: Wood,\n"));
		expect("a resource's text file is unchanged: " + Files.readString(made.textFile()), Files.readString(made.textFile()).equals(
				"INFO: {\n\tNAME: \"Test Good\",\n\tNAMES: \"Test Goods\",\n\tDESC: \"It's for\ntesting.\",\n},\n"));

		Map<String, String> animal = new LinkedHashMap<>();
		animal.put("CARAVAN", "false");
		animal.put("MASS", "100");
		animal.put("SPEED", "10");
		animal.put("HEIGHT", "2");
		animal.put("PACK", "true");
		animal.put("GRAZES", "true");
		animal.put("DANGER", "0.5");
		animal.put("LIVES_IN_CAVES", "0");
		animal.put("SPRITE", "Auroch");
		animal.put("ICON", "24->resource->Gem->0");
		animal.put("COLOR", "1_2_3");
		animal.put("RESOURCES", "WOOD,STONE");
		animal.put("RESOURCE_AMOUNT", "1,2.5");
		animal.put("CLIMATE.TEMPERATE", "0.5");
		animal.put("NAME", "Dumpling");
		animal.put("DESC", "A test.");
		animal.put("SOUND.ANIMAL_CALL_", "animal->Bull*");
		AssetCatalog.Entry animalEntry = entryNamed(entries, "Animal");
		AssetCreator.Created madeAnimal = AssetCreator.create(mod.root(), animalEntry.type(), animalEntry.schema(), "PINNED_ANIMAL", animal, parser, index);
		expect("an animal's data file is unchanged: " + Files.readString(madeAnimal.dataFile()), Files.readString(madeAnimal.dataFile()).equals(
				"ICON: 24->resource->Gem->0,\nCARAVAN: false,\nMASS: 100,\nSPEED: 10,\nHEIGHT: 2,\nCOLOR: 1_2_3,\nRESOURCES: [WOOD, STONE,],\nRESOURCE_AMOUNT: [1, 2.5,],\nCLIMATE: {\n\tTEMPERATE: 0.5,\n},\nPACK: true,\nGRAZES: true,\nDANGER: 0.5,\nLIVES_IN_CAVES: 0,\nSPRITE: Auroch,\n"));
		expect("an animal's text file is unchanged: " + Files.readString(madeAnimal.textFile()), Files.readString(madeAnimal.textFile()).equals(
				"INFO: {\n\tNAME: \"Dumpling\",\n\tDESC: \"A test.\",\n},\n"));
	}

	private static AssetCatalog.Entry entryNamed(List<AssetCatalog.Entry> entries, String label) {
		return entries.stream().filter(e -> e.type().label().equals(label)).findFirst().orElseThrow();
	}

	private static final List<String> FARM_CLASSES = List.of("settlement/room/food/farm/ROOM_FARM", "settlement/room/food/farm/Constructor", "settlement/room/main/furnisher/Furnisher",
			"settlement/room/main/RoomBlueprintImp", "settlement/room/main/RoomBlueprintIns", "settlement/room/main/RoomUpgrades", "settlement/room/main/employment/RoomEmploymentSimple",
			"settlement/room/main/furnisher/FurnisherMinimapColor", "settlement/room/industry/module/Industry", "init/type/CLIMATES", "settlement/room/main/furnisher/FurnisherStat",
			"settlement/room/main/BonusExperience$RoomExperienceBonus", "init/value/Lockable", "util/info/INFO", "init/sprite/UI/Icons");

	private static final String MONUMENT_PACK = "settlement/room/infra/monument/";

	/** Every class the Monument's gate reviews: a farm's room classes without its employment, recipes or stats, plus the monument's own. */
	private static List<String> monumentClasses() {
		List<String> classes = new java.util.ArrayList<>(FARM_CLASSES);
		classes.removeAll(List.of("settlement/room/food/farm/ROOM_FARM", "settlement/room/food/farm/Constructor", "settlement/room/industry/module/Industry", "init/type/CLIMATES",
				"settlement/room/main/BonusExperience$RoomExperienceBonus", "settlement/room/main/RoomBlueprintIns", "settlement/room/main/employment/RoomEmploymentSimple",
				"settlement/room/main/furnisher/FurnisherStat"));
		for (String own : List.of("ROOM_MONUMENTS", "ROOM_MONUMENTS$1", "ROOM_MONUMENT", "ROOM_MONUMENT$Instance", "MConstructor", "Imp", "Imp$Constructor", "Imp$Constructor$1",
				"Imp$Constructor$2", "Imp$Constructor$3", "Imp$Constructor$4")) {
			classes.add(MONUMENT_PACK + own);
		}
		classes.addAll(List.of("init/race/bio/Opinion", "game/boosting/BoostSpecs", "settlement/stats/standing/StatStanding$StandingDef", "settlement/stats/standing/StatStanding$StandingDef$StandingData",
				"settlement/room/sprite/RoomSpriteCombo", "settlement/room/sprite/RoomSpriteXxX", "settlement/room/sprite/RoomSpriteImp", "settlement/room/sprite/RoomSprite1x1",
				"settlement/room/sprite/RoomSprite1xN", "init/sprite/game/Sheets", "init/sprite/game/GameSheets", "init/sprite/game/SheetData"));
		return classes;
	}

	/** A good monument with one 2x2 item and no resources, as Eldermind's first building is planned. */
	private static Map<String, String> validMonument(AssetSchema schema) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("ICON", "32->MONUMENT->6");
		values.put("RESOURCES", "");
		values.put("AREA_COSTS", "");
		values.put("MINI_COLOR", "48_139_204");
		values.put("ITEMS.0.COSTS", "");
		values.put("ITEMS.0.STATS", "");
		values.put("MAX_VALUE", "8");
		values.put("SPRITES.0.2x2.0.FRAMES", "MONUMENT: 1");
		values.put("SPRITES.0.FLOOR_COMBO.0.FRAMES", "COMBO_PODEUM: 1");
		values.put("REQUIRES.GREATERE.PLAY_MODE_TEST", "1");
		values.put("NAME", "Test Monument");
		values.put("DESC", "It's for testing.");
		values.put("ITEMS.0.NAME", "Statue");
		values.put("ITEMS.0.DESC", "");
		for (SoundEntries.Slot slot : schema.sounds()) {
			values.put("SOUND." + slot.prefix(), "DUMMY");
		}
		return values;
	}

	/**
	 * A Monument, checked by hand against ROOM_MONUMENTS, ROOM_MONUMENT, MConstructor, Imp, Furnisher, StandingDef,
	 * BoostSpecs, Opinion and Lockable: the table, the gate, vanilla's four non-torch monuments, the rules, a new one,
	 * and a torch refused.
	 */
	private static void monument(List<AssetCatalog.Entry> entries, Path game, Path mods, GameParser parser, GameParser.GameVersion version, GameAssetIndex index, Path temp) throws Exception {
		AssetCatalog.Entry entry = entryNamed(entries, "Monument");
		expect("Monument is available: " + entry.unavailableReason(), entry.available());
		if (!entry.available()) {
			return;
		}
		AssetSchema schema = entry.schema();
		expect("a monument's files start with MONUMENT_", entry.type().fileName("X").equals("MONUMENT_X"));
		expect("Monument is in the Rooms group", "Rooms".equals(entry.type().group()));
		List<String> tags = schema.dataTags().stream().map(TagSpec::key).toList();
		expect("Monument's tags: " + tags, tags.equals(List.of("ICON", "RESOURCES", "AREA_COSTS", "FLOOR", "MINI_COLOR", "MINI_COLOR_PATTERN", "SOLID", "ITEMS", "STANDING", "FULFILLMENT_BONUS",
				"MAX_VALUE", "SPRITES", "UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE", "REQUIRES")));
		List<String> textTags = schema.textTags().stream().map(TagSpec::key).toList();
		expect("Monument's text tags: " + textTags, textTags.equals(List.of("NAME", "NAMES", "DESC", "WIKI", "ITEMS", "MORE", "LESS", "UPGRADES")));
		for (String unread : List.of("TYPE", "STANDING_UPGRADE", "WORK", "BONUS", "EXPERIENCE_BONUS", "STATS", "SERVICE", "INDUSTRY")) {
			expect("Monument doesn't offer " + unread, schema.find(unread) == null);
		}
		expectNested(schema, "SOLID", TagSpec.Kind.BOOL, false, "true", null, null);
		expectNested(schema, "MAX_VALUE", TagSpec.Kind.INT, true, null, null, null);
		expectNested(schema, "STANDING", TagSpec.Kind.BLOCK, false, "0 for every class", null, null);
		expectNested(schema, "STANDING.CITIZEN", TagSpec.Kind.DECIMAL, false, "0", null, null);
		expectNested(schema, "STANDING.MULTIPLIER", TagSpec.Kind.DECIMAL, false, "1", 0.0, 100000.0);
		expectNested(schema, "FULFILLMENT_BONUS", TagSpec.Kind.DECIMAL_MAP, false, null, null, null);
		expectNested(schema, "REQUIRES", TagSpec.Kind.BLOCK, false, "Nothing required", null, null);
		expect("REQUIRES holds the five comparators", schema.find("REQUIRES").shape().children().stream().map(TagSpec::key).toList().equals(List.of("LESS", "GREATER", "GREATERE", "EQUAL", "NEQUAL")));
		expectNested(schema, "REQUIRES.GREATERE", TagSpec.Kind.DECIMAL_MAP, false, null, null, null);
		expect("a requirement is a data key", !schema.isTextKey("REQUIRES.GREATERE.PLAY_MODE_TEST"));
		expect("ITEMS has no fixed count in either file", schema.tag("ITEMS").shape().fixedCount() == 0
				&& schema.textTags().stream().filter(t -> t.key().equals("ITEMS")).findFirst().orElseThrow().shape().fixedCount() == 0);
		expect("an item's text is a text key and its costs aren't", schema.isTextKey("ITEMS.0.NAME") && !schema.isTextKey("ITEMS.0.COSTS"));
		expect("an item's DESC can be blank, as vanilla's BLOB writes it", schema.find("ITEMS.DESC") != null && schema.find("ITEMS.DESC").blankAllowed());
		expect("SPRITES' sizes and floor", schema.tag("SPRITES").shape().children().stream().map(TagSpec::key).toList().equals(List.of("1x1", "2x2", "3x3", "FLOOR_COMBO")));
		for (String[] sprite : new String[][] { { "1x1", "sprite/game/1x1/" }, { "2x2", "sprite/game/2x2/" }, { "3x3", "sprite/game/3x3/" }, { "FLOOR_COMBO", "sprite/game/combo/" } }) {
			expect(sprite[0] + " looks in " + sprite[1], schema.find("SPRITES." + sprite[0] + ".FRAMES").sprite().folder().equals(sprite[1]));
			expect(sprite[0] + " is required only for the floor", schema.find("SPRITES." + sprite[0]).required() == sprite[0].equals("FLOOR_COMBO"));
		}
		expect("a monument starts from the sculpture", schema.starter().file().equals("init/room/MONUMENT_SCULPTURE.txt")
				&& schema.starter().tags().equals(List.of("RESOURCES", "AREA_COSTS", "ITEMS", "SPRITES")));
		List<String> sounds = schema.sounds().stream().map(slot -> slot.prefix() + "=" + slot.likeKey()).toList();
		expect("a monument's sounds: " + sounds, sounds.equals(List.of("ROOM_CLICK_=ROOM_CLICK_MONUMENT_SCULPTURE", "ROOM_=ROOM_MONUMENT_SCULPTURE")));

		// The gate: every class matters, and so does a changed key.
		List<String> classes = monumentClasses();
		AssetCatalog.Entry control = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve("monument-all.jar"), classes)), "Monument");
		expect("these classes are enough for Monument: " + control.unavailableReason(), control.available());
		int at = 0;
		for (String gone : classes) {
			AssetCatalog.Entry without = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve("monument-without-" + at++ + ".jar"), classes.stream().filter(n -> !n.equals(gone)).toList())), "Monument");
			expect("without " + gone + ", Monument is disabled: " + without.unavailableReason(), !without.available() && without.unavailableReason() != null);
		}
		for (String[] change : new String[][] { { MONUMENT_PACK + "ROOM_MONUMENTS$1", "TYPE", "TYPX" }, { MONUMENT_PACK + "ROOM_MONUMENT", "MAX_VALUE", "MAX_VALUX" },
				{ MONUMENT_PACK + "ROOM_MONUMENT", "FULFILLMENT_BONUS", "FULFILLMENT_BONUX" }, { MONUMENT_PACK + "Imp$Constructor", "FLOOR_COMBO", "FLOOR_COMBX" },
				{ MONUMENT_PACK + "MConstructor", "ITEMS", "ITEMX" }, { "init/race/bio/Opinion", "LESS", "LESX" } }) {
			AssetCatalog.Entry changed = entryNamed(AssetCatalog.load(patchedJar(game, temp.resolve("monument-changed-" + change[2] + ".jar"), classes, change[0], change[1], change[2])), "Monument");
			expect("a changed " + change[1] + " turns Monument off: " + changed.unavailableReason(), !changed.available() && changed.unavailableReason() != null);
		}

		// Vanilla's monuments: the four Imp ones with zero errors or warnings, and the torch out of scope.
		Path folder = temp.resolve("vanilla-monument");
		Files.createDirectories(folder);
		List<String> known = new java.util.ArrayList<>(textTags);
		known.add("INFO");
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile())) {
			for (String name : List.of("SCULPTURE", "BLOB", "NATURE", "DEATH", "TORCH")) {
				String file = "MONUMENT_" + name + ".txt";
				Path data = folder.resolve("data-" + file);
				Path words = folder.resolve("text-" + file);
				try (InputStream in = zip.getInputStream(zip.getEntry("data/assets/init/room/" + file))) {
					Files.copy(in, data, StandardCopyOption.REPLACE_EXISTING);
				}
				try (InputStream in = zip.getInputStream(zip.getEntry("data/assets/text/room/" + file))) {
					Files.copy(in, words, StandardCopyOption.REPLACE_EXISTING);
				}
				String outOfScope = schema.outOfScopeReason(syx.core.GameTree.read(parser.root(data)));
				if (name.equals("TORCH")) {
					expect(file + " is a torch, which this type refuses: " + outOfScope, outOfScope != null && outOfScope.contains("TYPE: TORCH"));
					continue;
				}
				expect(file + " is a monument this type covers: " + outOfScope, outOfScope == null);
				List<AssetChecker.Finding> findings = AssetVanillaCheck.check(schema, index, data, parser);
				expect(file + " has no errors or warnings: " + findings, findings.isEmpty());
				List<String> keys = parser.tags(words).tags().stream().map(GameParser.TagValue::key).toList();
				expect(file + " text uses only known tags: " + keys, known.containsAll(keys));
				List<AssetChecker.Finding> textFindings = AssetVanillaCheck.checkText(schema, words, parser);
				expect(file + " text reads as the schema says: " + textFindings, textFindings.isEmpty());
			}
		}

		// The rules.
		Map<String, String> good = validMonument(schema);
		expect("a good monument has no problems: " + AssetChecker.check(schema, index, good), AssetChecker.check(schema, index, good).isEmpty());
		for (String key : List.of("ICON", "MINI_COLOR", "MAX_VALUE", "NAME", "DESC", "ITEMS.0.NAME", "SPRITES.0.FLOOR_COMBO.0.FRAMES")) {
			refused(schema, index, good, key, null);
		}
		refused(schema, index, good, "ITEMS.0.STATS", "0");
		refused(schema, index, good, "ITEMS.0.COSTS", "1");
		Map<String, String> paid = new LinkedHashMap<>(good);
		paid.put("RESOURCES", "WOOD");
		paid.put("AREA_COSTS", "0");
		paid.put("ITEMS.0.COSTS", "2");
		expect("an item that pays for its resource is fine: " + AssetChecker.check(schema, index, paid), AssetChecker.check(schema, index, paid).isEmpty());
		refused(schema, index, good, "SPRITES.1.1x1.0.FRAMES", "MONUMENT: 0");
		Map<String, String> twoItems = new LinkedHashMap<>(good);
		twoItems.put("ITEMS.1.COSTS", "");
		twoItems.put("ITEMS.1.STATS", "");
		twoItems.put("ITEMS.1.NAME", "Pillar");
		twoItems.put("SPRITES.1.1x1.0.FRAMES", "MONUMENT: 0");
		twoItems.put("SPRITES.1.FLOOR_COMBO.0.FRAMES", "COMBO_PODEUM: 1");
		expect("two items, each with a block, are fine: " + AssetChecker.check(schema, index, twoItems), AssetChecker.check(schema, index, twoItems).isEmpty());
		Map<String, String> unnamed = new LinkedHashMap<>(twoItems);
		unnamed.remove("ITEMS.1.NAME");
		expect("an item without words in the text file is refused: " + AssetChecker.check(schema, index, unnamed),
				AssetChecker.check(schema, index, unnamed).stream().anyMatch(f -> f.blocksSaving() && "ITEMS".equals(f.tag()) && f.message().contains("text file has 1 item")));
		Map<String, String> spare = new LinkedHashMap<>(good);
		spare.put("ITEMS.1.COSTS", "");
		spare.put("ITEMS.1.STATS", "");
		spare.put("ITEMS.1.NAME", "Spare");
		expect("an item with no sprite block is unused, not a problem: " + AssetChecker.check(schema, index, spare), AssetChecker.check(schema, index, spare).isEmpty());
		Map<String, String> sizeless = new LinkedHashMap<>(good);
		sizeless.remove("SPRITES.0.2x2.0.FRAMES");
		expect("a sprite block with no size is refused: " + AssetChecker.check(schema, index, sizeless),
				AssetChecker.check(schema, index, sizeless).stream().anyMatch(f -> f.blocksSaving() && "SPRITES.0".equals(f.tag())));
		refused(schema, index, good, "FULFILLMENT_BONUS.CIVIC_LAW", "0.1");
		clean(schema, index, good, "FULFILLMENT_BONUS.CIVIC_LAW>ADD", "0.1");
		refused(schema, index, good, "STANDING.CITIZEN", "1000001");
		clean(schema, index, good, "STANDING.CITIZEN", "0");
		refused(schema, index, good, "STANDING.MULTIPLIER", "100001");
		warned(schema, index, good, "MAX_VALUE", "0");
		warned(schema, index, good, "MAX_VALUE", "16");
		clean(schema, index, good, "MAX_VALUE", "15");
		clean(schema, index, good, "MAX_VALUE", "1");
		refused(schema, index, good, "SOLID", "maybe");
		clean(schema, index, good, "REQUIRES.EQUAL.PLAY_MODE_OTHER", "0");
		refused(schema, index, good, "REQUIRES.GREATERE.PLAY_MODE_TEST", "yes");
		clean(schema, index, good, "MORE", "We want more.\nMuch more.");

		// A new one, read back with the game's own reads, then opened again with nothing kept.
		ModEntry mod = ModCreator.create(mods, game, "Monument Creation");
		Path modVersion = AssetCreator.versionFolder(mod.root(), version);
		Path assets = modVersion.resolve("assets");
		GameAssetIndex modIndex = GameAssetIndex.load(game, modVersion, parser);
		Map<String, String> starting = AssetForm.startingValues(schema, modIndex);
		expect("a new monument starts with the sculpture's statue and pillar", starting.containsKey("SPRITES.0.1x1.0.FRAMES") && starting.containsKey("SPRITES.1.3x3.0.FRAMES")
				&& starting.containsKey("ITEMS.1.COSTS") && "STONE_CUT".equals(starting.get("RESOURCES")));
		AssetCreator.Created made = AssetCreator.create(mod.root(), entry.type(), schema, "TEST_PRECIPITATOR", good, parser, modIndex);
		expect("its file has the prefix the game needs", made.dataFile().equals(assets.resolve("init/room/MONUMENT_TEST_PRECIPITATOR.txt")));
		List<AssetChecker.Finding> findings = AssetVanillaCheck.check(schema, modIndex, made.dataFile(), parser);
		expect("the new monument passes the vanilla check: " + findings, findings.isEmpty());
		List<AssetChecker.Finding> textFindings = AssetVanillaCheck.checkText(schema, made.textFile(), parser);
		expect("its text passes too: " + textFindings, textFindings.isEmpty());
		GameParser.Node node = parser.root(made.dataFile());
		expect("the game reads the requirement", node.block("REQUIRES").block("GREATERE").decimal("PLAY_MODE_TEST") == 1);
		expect("and an item with no costs or stats", node.blocks("ITEMS").size() == 1 && node.blocks("ITEMS").get(0).decimals("COSTS").length == 0
				&& node.blocks("ITEMS").get(0).decimals("STATS").length == 0);
		expect("and the 2x2 statue", node.blocks("SPRITES").size() == 1 && node.blocks("SPRITES").get(0).has("2x2") && !node.blocks("SPRITES").get(0).has("1x1"));
		expect("no STANDING is written, so every class is 0", !node.has("STANDING"));
		GameParser.Node words = parser.root(made.textFile());
		expect("the text has the item's words", "Statue".equals(words.blocks("ITEMS").get(0).text("NAME")) && "".equals(words.blocks("ITEMS").get(0).text("DESC")));
		GameAssetIndex after = GameAssetIndex.load(game, modVersion, parser);
		AssetEditing.Location location = AssetEditing.locate(mod.root(), version, made.dataFile(), entries, after, parser);
		expect("the new monument can be opened: " + location.problem(), location.problem() == null);
		if (location.problem() == null) {
			AssetEditing.Opened opened = AssetEditing.load(location, parser, after);
			expect("it opens with nothing kept as written: " + opened.kept(), opened.kept().isEmpty());
			expect("and its values come back: " + opened.values(), "1".equals(opened.values().get("REQUIRES.GREATERE.PLAY_MODE_TEST"))
					&& "MONUMENT: 1".equals(opened.values().get("SPRITES.0.2x2.0.FRAMES")) && "Statue".equals(opened.values().get("ITEMS.0.NAME")));
		}

		// A torch in a mod is refused, both where it's listed and where it's opened.
		Path torch = assets.resolve("init/room/MONUMENT_OWN_TORCH.txt");
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile()); InputStream in = zip.getInputStream(zip.getEntry("data/assets/init/room/MONUMENT_TORCH.txt"))) {
			Files.copy(in, torch);
		}
		GameAssetIndex withTorch = GameAssetIndex.load(game, modVersion, parser);
		String listed = AssetEditing.locate(mod.root(), version, torch, entries, withTorch, parser).problem();
		expect("a torch is refused where it's listed, saying why: " + listed, listed != null && listed.contains("TYPE: TORCH"));
		AssetEditing.Location unparsed = AssetEditing.locate(mod.root(), version, torch, entries, withTorch);
		try {
			AssetEditing.load(unparsed, parser, withTorch);
			expect("a torch isn't opened", false);
		} catch (AssetEditing.OpenException e) {
			expect("a torch is refused when it's opened, saying why: " + e.getMessage(), e.getMessage().contains("TYPE: TORCH"));
		}
	}

	private static void rooms(List<AssetCatalog.Entry> entries, Path game, Path mods, GameParser parser, GameParser.GameVersion version, GameAssetIndex index, Path temp) throws Exception {
		AssetCatalog.Entry crop = entryNamed(entries, "Crop");
		AssetCatalog.Entry farm = entryNamed(entries, "Farm");
		expect("Crop is available: " + crop.unavailableReason(), crop.available());
		expect("Farm is available: " + farm.unavailableReason(), farm.available());
		if (!crop.available() || !farm.available()) {
			return;
		}
		ModEntry mod = ModCreator.create(mods, game, "Room Rules");
		GameAssetIndex cropIndex = GameAssetIndex.load(game, AssetCreator.versionFolder(mod.root(), version), parser);
		cropTable(crop);
		farmTable(farm);
		roomGate(game, temp);
		vanillaCrops(game, crop.schema(), index, temp, parser);
		vanillaFarms(game, farm.schema(), index, cropIndex, temp, parser);
		cropRules(crop.schema(), cropIndex);
		farmRules(farm.schema(), cropIndex);
		cropSprites(crop.schema(), mods, version, game);
		roomNames(farm.type(), crop.type(), cropIndex, version);
		roomCreation(entries, crop, farm, mods, parser, version, game);
		sounds(entries, farm, mods, parser, version, game, temp);
		mines(entries, game, mods, parser, version, index, temp);
	}

	private static final List<String> MINE_CLASSES = minesClasses();

	private static List<String> minesClasses() {
		List<String> classes = new java.util.ArrayList<>(FARM_CLASSES);
		classes.remove("settlement/room/food/farm/ROOM_FARM");
		classes.remove("settlement/room/food/farm/Constructor");
		classes.remove("settlement/room/industry/module/Industry");
		classes.addAll(List.of("settlement/room/industry/mine/ROOM_MINE", "settlement/room/industry/mine/Constructor", "settlement/room/sprite/RoomSpriteImp", "settlement/room/sprite/RoomSprite1x1",
				"settlement/room/sprite/RoomSprite1xN", "settlement/room/sprite/RoomSpriteXxX", "init/sprite/game/Sheets", "init/sprite/game/GameSheets", "init/sprite/game/SheetData"));
		for (int i : new int[] { 5, 6, 7, 8, 9, 11, 12, 13, 14, 15 }) {
			classes.add("settlement/room/industry/mine/Constructor$" + i);
		}
		return classes;
	}

	private static final List<String> MINABLE_CLASSES = List.of("init/resources/Minable", "init/resources/Minable$Util", "init/resources/Minable$Util$1");
	private static final List<String> MINE_SPRITES = List.of("CONVEYOR_1X1", "STORAGE_1X1_TOP", "STORAGE_1X1_MID", "STORAGE_1X1_END", "SLAB_1X1", "AUX_1X1", "AUX_MEDIUM_A_1X1", "AUX_MEDIUM_B_1X1", "AUX_BIG_2X2", "WORK_1X1");

	private static void mines(List<AssetCatalog.Entry> entries, Path game, Path mods, GameParser parser, GameParser.GameVersion version, GameAssetIndex index, Path temp) throws Exception {
		AssetCatalog.Entry mine = entryNamed(entries, "Mine");
		AssetCatalog.Entry minable = entryNamed(entries, "Minable resource");
		expect("Mine is available: " + mine.unavailableReason(), mine.available());
		expect("Minable resource is available: " + minable.unavailableReason(), minable.available());
		if (!mine.available() || !minable.available()) {
			return;
		}
		mineTable(mine);
		minableTable(minable);
		mineGate(game, temp);
		vanillaMines(game, mine.schema(), minable.schema(), index, temp, parser);
		mineRules(mine, index);
		minableRules(minable, game, mods, version, parser);
		frameRules(mine.schema(), game, mods, version);
		mineCreation(entries, mine, minable, mods, game, parser, version);
		refiners(entries, game, index, temp, parser);
		workshops(entries, game, mods, parser, version, index, temp);
		pastures(entries, game, mods, parser, version, index, temp);
		for (FoodRoom room : FOOD_ROOMS) {
			foodRoom(room, entries, game, mods, parser, version, index, temp);
		}
		orchardAndHunterLimits(entries, index);
		for (SpiritRoom room : SPIRIT_ROOMS) {
			spiritRoom(room, entries, game, mods, parser, version, index, temp);
		}
		for (AccessRoom room : ACCESS_ROOMS) {
			accessRoom(room, entries, game, mods, parser, version, index, temp);
		}
		for (NeedRoom room : NEED_ROOMS) {
			needRoom(room, entries, game, mods, parser, version, index, temp);
		}
		for (LawRoom room : LAW_ROOMS) {
			lawRoom(room, entries, game, parser, index, temp);
		}
	}

	/**
	 * A Well, a Bath, a Lavatory or a Barber, each checked by hand against its ROOM_ class, its Constructor, RoomService,
	 * RoomServiceAccess, RoomServiceNeed, StandingDef and BoostSpecs.
	 *
	 * @param namedSlots how many STATS blocks are shown with their words required, from the first
	 * @param namedStats the STATS blocks whose words the game reads
	 * @param inner each class after the package that handles the file, besides the ROOM_ class
	 * @param others more classes the gate reviews
	 * @param work true if WORK is required, false if it's optional and warns, null if it isn't offered
	 * @param sounds each sound's prefix and the vanilla entry a new one starts from, or null
	 * @param changes a full class name, a key in it, and what to rename the key to
	 * @param warned data tags vanilla writes and nothing reads
	 */
	private record NeedRoom(String label, String prefix, String pack, String roomClass, List<String> tags, List<String> textTags, List<String> sprites, int items, int stats,
			int namedSlots, List<Integer> namedStats, List<String> inner, List<String> others, Boolean work, List<String[]> sounds, String[][] changes, List<String> warned) {
	}

	private static final String SERVICE_NEED = "settlement/room/service/module/RoomServiceNeed";
	private static final List<String> NEED_TEXT = List.of("NAME", "NAMES", "DESC", "WIKI", "WORK", "STATS", "ITEMS", "SERVICE", "UPGRADES");
	/** What a Stage, Speaker, Fight pit and Grand arena share: the same data tags, and the crowd and box sprite classes. */
	private static final List<String> SPECTATOR_TAGS = List.of("ICON", "RESOURCES", "AREA_COSTS", "FLOOR", "MINI_COLOR", "MINI_COLOR_PATTERN", "WORK", "ITEMS", "SPRITES", "SERVICE", "UPGRADES",
			"ENVIRONMENT_EMIT", "DEGRADE_RATE");
	private static final List<String> SPECTATOR_CLASSES = List.of("settlement/room/service/module/ROOM_SPECTATOR", "settlement/room/sprite/RoomSpriteBoxN");
	private static final List<NeedRoom> NEED_ROOMS = List.of(
			new NeedRoom("Well", "WELL_", "settlement/room/service/hygine/well/", "ROOM_WELL",
					List.of("ICON", "RESOURCES", "AREA_COSTS", "FLOOR", "MINI_COLOR", "MINI_COLOR_PATTERN", "ITEMS", "SPRITES", "SERVICE", "UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE"),
					NEED_TEXT.stream().filter(t -> !t.equals("WORK")).toList(),
					List.of("STONE_RING_STENCIL_COMBO", "ROOF_EDGE_1X1", "ROOF_MID_1X1", "FOUNTAIN_1X1", "STONE_RING_COMBO", "BUCKET_1X1"),
					1, 1, 1, List.of(0), List.of("Constructor", "Constructor$1", "Constructor$2", "Constructor$3", "Constructor$4", "Constructor$5"), List.of(), null,
					List.of(new String[] { "ROOM_CLICK_", "ROOM_CLICK_WELL_NORMAL" }, new String[] { "ROOM_", "ROOM_WELL_NORMAL" }, new String[] { "ROOM_SERVICE_", "ROOM_SERVICE_WELL_NORMAL" }),
					new String[][] { { "settlement/room/service/hygine/well/Constructor", "FOUNTAIN_1X1", "FOUNTAIN_1X2" }, { SERVICE_NEED, "SERVICE", "SERVICX" } }, List.of()),
			new NeedRoom("Bath", "BATH_", "settlement/room/service/hygine/bath/", "ROOM_BATH",
					List.of("ICON", "RESOURCES", "AREA_COSTS", "FLOOR", "MINI_COLOR", "MINI_COLOR_PATTERN", "WORK", "ITEMS", "SPRITES", "WATER_COLOR", "WATER_OPACITY", "SERVICE", "INDUSTRY",
							"UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE"),
					List.of("NAME", "NAMES", "DESC", "WIKI", "HEATING", "HEATING_DESC", "HEATING_PROBLEM", "WATER_PROBLEM", "WORK", "STATS", "ITEMS", "SERVICE", "UPGRADES"),
					List.of("FRAME_COMBO", "POOL_FLOOR_TEXTURE", "WORK_1X1", "OVEN_1X1", "ENTRANCE_1X1", "PIPE_1X1", "BENCH_1X1", "TORCH_1X1"),
					2, 2, 2, List.of(0, 1), List.of("ROOM_BATH$2", "Constructor", "Constructor$2", "Constructor$3", "Constructor$4", "Constructor$5", "Constructor$6"),
					List.of("settlement/room/industry/module/Industry", "settlement/room/sprite/RoomSpriteTex"), true,
					List.of(new String[] { "ROOM_WORK_", "ROOM_WORK_BATH_NORMAL" }, new String[] { "ROOM_CLICK_", "ROOM_CLICK_BATH_NORMAL" }, new String[] { "ROOM_", "ROOM_BATH_NORMAL" },
							new String[] { "ROOM_SERVICE_", null }),
					new String[][] { { "settlement/room/service/hygine/bath/Constructor$2", "WATER_OPACITY", "WATER_OPACITX" }, { "settlement/room/service/hygine/bath/ROOM_BATH", "HEATING_PROBLEM", "HEATING_PROBLEX" } },
					List.of()),
			new NeedRoom("Lavatory", "LAVATORY_", "settlement/room/service/lavatory/", "ROOM_LAVATORY",
					List.of("ICON", "RESOURCES", "AREA_COSTS", "FLOOR", "MINI_COLOR", "MINI_COLOR_PATTERN", "WORK", "ITEMS", "SPRITES", "SERVICE", "UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE"),
					NEED_TEXT,
					List.of("NICKNACK_1X1", "SIT_COMBO", "SHITHOLE_1X1", "SHIT_LID_1X1", "SHIT_1X1", "SIT_ONTOP_COMBO", "TABLE_COMBO", "BASIN_WATER_1X1", "BASIN_1X1"),
					2, 3, 3, List.of(2), List.of("Constructor", "Constructor$1", "Constructor$1$1", "Constructor$2", "Constructor$2$1", "Constructor$4", "Constructor$4$1"), List.of(), false,
					List.of(new String[] { "ROOM_WORK_", "ROOM_WORK_LAVATORY_NORMAL" }, new String[] { "ROOM_CLICK_", "ROOM_CLICK_LAVATORY_NORMAL" }, new String[] { "ROOM_", "ROOM_LAVATORY_NORMAL" },
							new String[] { "ROOM_SERVICE_", "ROOM_SERVICE_LAVATORY_NORMAL" }),
					new String[][] { { "settlement/room/service/lavatory/Constructor$2", "SIT_ONTOP_COMBO", "SIT_ONTOP_COMBX" } }, List.of("DEFAULT_STANDING")),
			new NeedRoom("Barber", "BARBER_", "settlement/room/service/barber/", "ROOM_BARBER",
					List.of("ICON", "RESOURCES", "AREA_COSTS", "FLOOR", "MINI_COLOR", "MINI_COLOR_PATTERN", "WORK_TIME_IN_DAYS", "WORK", "ITEMS", "SPRITES", "SERVICE", "UPGRADES", "ENVIRONMENT_EMIT",
							"DEGRADE_RATE"),
					NEED_TEXT,
					List.of("NICKNACK_1X1", "TABLE_CENTRE_1X1", "TABLE_CENTRE_TOP_1X1", "TABLE_COMBO", "CHAIR_1X1", "SEPARATOR_1X1", "CARPET_COMBO"),
					2, 3, 3, List.of(2), List.of("Constructor", "Constructor$1", "Constructor$2", "Constructor$3", "Constructor$5", "Constructor$6"),
					List.of("settlement/room/main/furnisher/FurnisherItemTools", "settlement/room/main/furnisher/FurnisherItemTools$2", "settlement/room/main/furnisher/FurnisherItemTools$3"), false,
					List.of(new String[] { "ROOM_WORK_", "ROOM_WORK_BARBER_NORMAL" }, new String[] { "ROOM_CLICK_", "ROOM_CLICK_BARBER_NORMAL" }, new String[] { "ROOM_", "ROOM_BARBER_NORMAL" },
							new String[] { "ROOM_SERVICE_", "ROOM_SERVICE_BARBER_NORMAL" }),
					new String[][] { { "settlement/room/service/barber/ROOM_BARBER", "WORK_TIME_IN_DAYS", "WORK_TIME_IN_DAYX" } }, List.of()),
			new NeedRoom("Physician", "PHYSICIAN_", "settlement/room/health/physician/", "ROOM_PHYSICIAN",
					List.of("ICON", "RESOURCES", "AREA_COSTS", "FLOOR", "MINI_COLOR", "MINI_COLOR_PATTERN", "WORK", "ITEMS", "SPRITES", "SERVICE", "UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE"),
					NEED_TEXT,
					List.of("SHELF_1X1", "SHELF_ONTOP_1X1", "BUNK_1X1_TOP", "BUNK_1X1_BOTTOM", "TABLE_ONTOP_1X1", "TABLE_COMBO", "STORAGE_1X1"),
					2, 3, 3, List.of(0, 2), List.of("Constructor", "Constructor$1", "Constructor$2", "Constructor$3", "Constructor$4"), List.of(), false,
					List.of(new String[] { "ROOM_WORK_", "ROOM_WORK_PHYSICIAN_NORMAL" }, new String[] { "ROOM_CLICK_", "ROOM_CLICK_PHYSICIAN_NORMAL" }, new String[] { "ROOM_", "ROOM_PHYSICIAN_NORMAL" },
							new String[] { "ROOM_SERVICE_", null }),
					new String[][] { { "settlement/room/health/physician/Constructor$1", "SHELF_ONTOP_1X1", "SHELF_ONTOP_1X2" }, { "settlement/room/health/physician/Constructor", "BUNK_1X1_BOTTOM", "BUNK_1X2_BOTTOM" } },
					List.of()),
			new NeedRoom("Pleasure", "PLEASURE_", "settlement/room/service/pleasure/", "ROOM_PLEASURE",
					List.of("ICON", "RESOURCES", "AREA_COSTS", "FLOOR", "FLOOR2", "MINI_COLOR", "MINI_COLOR_PATTERN", "COLOR_PIXEL_BASE", "WORK", "ITEMS", "SPRITES", "SERVICE", "UPGRADES",
							"ENVIRONMENT_EMIT", "DEGRADE_RATE"),
					NEED_TEXT,
					List.of("BED_HEAD_1X1", "BED_TAIL_1X1", "TABLE_TOP_1X1", "TABLE_COMBO", "SHELF_1X1", "SHELF_TOP_1X1", "NICKNACK_1X1", "WALLS_COMBO", "CARPET_COMBO"),
					3, 3, 3, List.of(0, 1, 2), List.of("Constructor", "Constructor$1", "Constructor$3", "Constructor$4"),
					List.of("settlement/room/main/furnisher/FurnisherItemTools", "settlement/room/main/furnisher/FurnisherItemTools$2", "settlement/room/main/furnisher/FurnisherItemTools$3"), false,
					List.of(new String[] { "ROOM_WORK_", "ROOM_WORK_PLEASURE_NORMAL" }, new String[] { "ROOM_CLICK_", "ROOM_CLICK_PLEASURE_NORMAL" }, new String[] { "ROOM_", "ROOM_PLEASURE_NORMAL" },
							new String[] { "ROOM_SERVICE_", null }),
					new String[][] { { "settlement/room/service/pleasure/Constructor", "FLOOR2", "FLOOR3" }, { "settlement/room/service/pleasure/Constructor", "COLOR_PIXEL_BASE", "COLOR_PIXEL_BASX" },
							{ "settlement/room/service/pleasure/Constructor$3", "SHELF_TOP_1X1", "SHELF_TOP_1X2" } },
					List.of()),
			new NeedRoom("Stage", "STAGE_", "settlement/room/service/stage/", "ROOM_STAGE", SPECTATOR_TAGS, NEED_TEXT, List.of("A_BOX", "B_BOX", "C_BOX"),
					1, 2, 1, List.of(0), List.of("StageConstructor", "StageConstructor$1", "StageConstructor$2", "StageConstructor$3"), SPECTATOR_CLASSES, true,
					spectatorSounds("STAGE_NORMAL"), new String[][] { { "settlement/room/service/stage/StageConstructor", "C_BOX", "C_BOY" } }, List.of()),
			new NeedRoom("Speaker", "SPEAKER_", "settlement/room/service/speaker/", "ROOM_SPEAKER", SPECTATOR_TAGS, NEED_TEXT, List.of("CENTER_COMBO", "FRAME_COMBO"),
					1, 2, 0, List.of(), List.of("SpeakerConstructor", "SpeakerConstructor$1"), SPECTATOR_CLASSES, true,
					spectatorSounds("SPEAKER_NORMAL"), new String[][] { { "settlement/room/service/speaker/SpeakerConstructor", "FRAME_COMBO", "FRAME_COMBX" } }, List.of()),
			new NeedRoom("Fight pit", "FIGHTPIT_", "settlement/room/service/arena/pit/", "ROOM_FIGHTPIT", SPECTATOR_TAGS, NEED_TEXT,
					List.of("WALL_1X1", "TOWER_1X1", "TORCH_1X1", "TORCH_TOP_1X1", "STAIRS_LEFT_1X1", "STAIRS_CENTRE_1X1", "STAIRS_RIGHT_1X1", "SEAT_BOX", "RIM_BOX"),
					1, 2, 1, List.of(0), List.of("ArenaConstructor", "ArenaConstructor$1", "ArenaConstructor$2", "ArenaConstructor$3", "ArenaConstructor$4", "ArenaConstructor$5", "ArenaConstructor$5$1",
							"ArenaConstructor$SStairs"), SPECTATOR_CLASSES, true,
					spectatorSounds("FIGHTPIT_NORMAL"), new String[][] { { "settlement/room/service/arena/pit/ArenaConstructor$5", "TORCH_TOP_1X1", "TORCH_TOP_1X2" } }, List.of("TYPE")),
			new NeedRoom("Grand arena", "ARENAG_", "settlement/room/service/arena/grand/", "ROOM_ARENA", SPECTATOR_TAGS, NEED_TEXT,
					List.of("CONSTRUCT_1X1", "WALL_1X1", "TOWER_1X1", "TORCH_1X1", "TORCH_TOP_1X1", "STAIRS_SINGLE_1X1", "STAIRS_LEFT_1X1", "STAIRS_CENTRE_1X1", "STAIRS_RIGHT_1X1", "SEAT_BOX", "RIM_BOX"),
					1, 2, 2, List.of(0, 1), List.of("ArenaConstructor", "CUtil", "CUtil$1", "CUtil$2", "CUtil$3", "CUtil$4", "CUtil$5", "CUtil$5$1", "CUtil$SStairs"), SPECTATOR_CLASSES, true,
					spectatorSounds("ARENAG_NORMAL"), new String[][] { { "settlement/room/service/arena/grand/CUtil", "STAIRS_SINGLE_1X1", "STAIRS_SINGLE_1X2" },
							{ "settlement/room/service/arena/grand/ArenaConstructor", "CONSTRUCT_1X1", "CONSTRUCT_1X2" } }, List.of("TYPE")),
			new NeedRoom("Court", "_", "settlement/room/law/court/", "ROOM_COURT", SPECTATOR_TAGS, NEED_TEXT,
					List.of("TABLE_COMBO", "CARPET_COMBO", "TORCH_1X1", "GEM_1X1", "STAND_COMBO", "CHAIR_1X1", "DECOR_A_1X1", "DECOR_B_1X1", "BENCH_A_1X1", "BENCH_B_1X1", "BENCH_C_1X1"),
					2, 3, 3, List.of(0, 1, 2), List.of("Constructor", "Constructor$1", "Constructor$2", "Constructor$3", "Constructor$4", "Constructor$5", "Constructor$6", "Constructor$7"), List.of(), false,
					List.of(), new String[][] { { "settlement/room/law/court/ROOM_COURT", "_COURT", "_COURX" }, { "settlement/room/law/court/Constructor", "BENCH_C_1X1", "BENCH_C_1X2" } },
					List.of()),
			new NeedRoom("Stocks", "_", "settlement/room/law/stocks/", "ROOM_STOCKS",
					List.of("ICON", "RESOURCES", "AREA_COSTS", "FLOOR", "MINI_COLOR", "MINI_COLOR_PATTERN", "ITEMS", "SPRITES", "SERVICE", "UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE"),
					List.of("NAME", "NAMES", "DESC", "WIKI", "STATS", "ITEMS", "SERVICE", "UPGRADES"), List.of("BOX", "STOCK_BELOW_1X1", "STOCK_TOP_1X1"),
					1, 1, 0, List.of(), List.of("MConstructor", "MConstructor$1", "MConstructor$2", "MConstructor$2$1"), List.of("settlement/room/sprite/RoomSpriteBoxN"), null,
					List.of(), new String[][] { { "settlement/room/law/stocks/ROOM_STOCKS", "_STOCKS", "_STOCKX" }, { "settlement/room/law/stocks/MConstructor$2", "STOCK_TOP_1X1", "STOCK_TOP_1X2" } },
					List.of("TYPE")));

	/**
	 * A law room without a service, such as the Prison, which the game builds from one file of its own, so a mod can only
	 * override it. Each is checked by hand against its ROOM_ class and Constructor.
	 *
	 * @param file the game's file, such as _PRISON
	 * @param inner each class after the package that handles the file, besides the ROOM_ class
	 * @param others more classes the gate reviews
	 * @param extra values a good one needs besides the shared ones
	 * @param changes a full class name, a key in it, and what to rename the key to
	 * @param warned data tags the game's own file has that nothing reads, which warn
	 */
	private record LawRoom(String label, String file, String pack, String roomClass, List<String> tags, List<String> textTags, List<String> sprites, int items, int stats,
			List<String> inner, List<String> others, Map<String, String> extra, String[][] changes, List<String> warned) {
	}

	private static final List<String> LAW_TEXT = List.of("NAME", "NAMES", "DESC", "WIKI", "WORK", "STATS", "ITEMS", "UPGRADES");
	private static final List<LawRoom> LAW_ROOMS = List.of(
			new LawRoom("Prison", "_PRISON", "settlement/room/law/prison/", "ROOM_PRISON",
					List.of("ICON", "RESOURCES", "AREA_COSTS", "FLOOR", "FLOOR2", "MINI_COLOR", "MINI_COLOR_PATTERN", "WORK", "ITEMS", "SPRITES", "UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE"), LAW_TEXT,
					List.of("WALLS_COMBO", "BARS_1X1", "LATRINE_EMPTY_1X1", "LATRINE_FULL_1X1", "FOOD_1X1", "MISC_1X1", "OPENING_1X1", "CANDLE_SHELF_1X1"), 1, 2,
					List.of("Constructor", "Constructor$1", "Constructor$3", "Constructor$5"), List.of(), Map.of("FLOOR2", "STONE1"),
					new String[][] { { "settlement/room/law/prison/ROOM_PRISON", "_PRISON", "_PRISOX" }, { "settlement/room/law/prison/Constructor", "FLOOR2", "FLOOR3" },
							{ "settlement/room/law/prison/Constructor$1", "LATRINE_FULL_1X1", "LATRINE_FULL_1X2" } },
					List.of()),
			new LawRoom("Stockade", "_STOCKADE", "settlement/room/law/stockade/", "ROOM_STOCKADE",
					List.of("ICON", "RESOURCES", "AREA_COSTS", "FLOOR", "MINI_COLOR", "MINI_COLOR_PATTERN", "WORK", "ITEMS", "SPRITES", "UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE"), LAW_TEXT,
					List.of("WALL_1X1", "WALL_CORNER_1X1", "OPENING_1X1", "FOOD_1X1", "LATRINE_EMPTY_1X1", "LATRINE_FULL_1X1", "MISC_1X1"), 1, 2,
					List.of("Constructor", "Constructor$3", "Constructor$5", "Constructor$6"), List.of(), Map.of(),
					new String[][] { { "settlement/room/law/stockade/ROOM_STOCKADE", "_STOCKADE", "_STOCKADX" }, { "settlement/room/law/stockade/Constructor", "WALL_CORNER_1X1", "WALL_CORNER_1X2" } },
					List.of()),
			new LawRoom("Guard", "_GUARD", "settlement/room/law/guard/", "ROOM_GUARD",
					List.of("ICON", "RESOURCES", "AREA_COSTS", "FLOOR", "MINI_COLOR", "MINI_COLOR_PATTERN", "EMPLOYMENT", "ITEMS", "SPRITES", "UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE"),
					List.of("NAME", "NAMES", "DESC", "WIKI", "EMPLOYMENT", "STATS", "ITEMS", "UPGRADES"),
					List.of("FLOOR_BOX", "CARPET_COMBO", "TORCH_BOTTOM_COMBO", "TORCH_COMBO", "FENCE_COMBO"), 2, 1,
					List.of("Constructor", "Constructor$2", "Constructor$3", "Constructor$6", "Constructor$6$1", "Constructor$6$2", "Constructor$7", "Constructor$7$1"),
					List.of("settlement/room/sprite/RoomSpriteBoxN"), Map.of("EMPLOYMENT.SHIFT_OFFSET", "0.5", "EMPLOYMENT.TITLE", "Guard", "EMPLOYMENT.VERB", "guarding"),
					new String[][] { { "settlement/room/law/guard/ROOM_GUARD", "_GUARD", "_GUARX" }, { "settlement/room/law/guard/Constructor$7", "FENCE_COMBO", "FENCE_COMBX" } },
					List.of()),
			new LawRoom("Police", "_POLICE", "settlement/room/law/police/", "ROOM_POLICE",
					List.of("ICON", "RESOURCES", "AREA_COSTS", "FLOOR", "MINI_COLOR", "MINI_COLOR_PATTERN", "WORK", "ITEMS", "SPRITES", "UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE", "BOOST"), LAW_TEXT,
					List.of("BED_STRAP_1X1", "BED_1X1A", "CHAIR_1X1", "CAGE_A_1X1", "CAGE_A_TOP_1X1", "CAGE_B_1X1", "CAGE_LATCH_1X1", "TABLE_COMBO", "NICKNACK_1X1"), 5, 2,
					List.of("PoliceConstructor", "PoliceConstructor$1", "PoliceConstructor$2", "PoliceConstructor$3", "PoliceConstructor$4", "PoliceConstructor$5", "PoliceConstructor$6",
							"PoliceConstructor$7", "PoliceConstructor$8"),
					List.of("game/boosting/BoostSpecs"), Map.of(),
					new String[][] { { "settlement/room/law/police/ROOM_POLICE", "_POLICE", "_POLICX" }, { "settlement/room/law/police/PoliceConstructor$5", "CAGE_A_TOP_1X1", "CAGE_A_TOP_1X2" } },
					List.of()),
			new LawRoom("Execution", "_EXECUTION", "settlement/room/law/execution/", "ROOM_EXECTUTION",
					List.of("ICON", "RESOURCES", "AREA_COSTS", "FLOOR", "MINI_COLOR", "MINI_COLOR_PATTERN", "ITEMS", "SPRITES", "UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE"),
					List.of("NAME", "NAMES", "DESC", "WIKI", "STATS", "ITEMS", "UPGRADES"),
					List.of("GALLOW_BEAM_LEFT_1X1", "GALLOW_BEAM_CENTRE_1X1", "GALLOW_BOTTOM_1X1", "GALLOW_PILLAR_1X1", "GALLOW_BOX_1X1", "GALLOW_NOOSE_1X1", "CHOP_TABLE_1X1", "CHOP_MAT_1X1",
							"GIBBET_CAGE_1X1", "GIBBET_DOOR_1X1", "CROSS_R_1X1", "CROSS_L_1X1", "CROSS_MID_1X1", "CROSS_POLE_1X1", "CROSS_C_1X1", "PODIUM_BOX"), 4, 1,
					List.of("Constructor", "Constructor$1", "Constructor$2", "Constructor$3", "Constructor$4", "Constructor$5", "Constructor$6", "Constructor$PSprite", "Constructor$RSprite",
							"Constructor$Pedistal", "Constructor$Pedistall"),
					List.of("settlement/room/sprite/RoomSpriteBoxN"), Map.of(),
					new String[][] { { "settlement/room/law/execution/ROOM_EXECTUTION", "_EXECUTION", "_EXECUTIOX" }, { "settlement/room/law/execution/Constructor$Pedistal", "PODIUM_BOX", "PODIUM_BOY" } },
					List.of("SERVICE")));

	/** The classes behind any room with instances: a need room's, without the service. */
	private static List<String> lawRoomClasses(LawRoom room) {
		List<String> classes = new java.util.ArrayList<>(FARM_CLASSES);
		for (String gone : List.of("settlement/room/food/farm/ROOM_FARM", "settlement/room/food/farm/Constructor", "settlement/room/industry/module/Industry", "init/type/CLIMATES", "settlement/room/main/BonusExperience$RoomExperienceBonus")) {
			classes.remove(gone);
		}
		if (room.roomClass().equals("ROOM_EXECTUTION")) {
			// A RoomBlueprintImp, so it has neither (ROOM_EXECTUTION.java:24).
			classes.remove("settlement/room/main/RoomBlueprintIns");
			classes.remove("settlement/room/main/employment/RoomEmploymentSimple");
		}
		classes.add(room.pack() + room.roomClass());
		room.inner().forEach(inner -> classes.add(room.pack() + inner));
		classes.addAll(room.others());
		classes.addAll(List.of("settlement/room/sprite/RoomSpriteImp", "settlement/room/sprite/RoomSprite1x1", "settlement/room/sprite/RoomSprite1xN",
				"settlement/room/sprite/RoomSpriteCombo", "init/sprite/game/Sheets", "init/sprite/game/GameSheets", "init/sprite/game/SheetData"));
		return classes;
	}

	/** The tables, the gate, the game's own file and the rules, for a law room without a service. */
	private static void lawRoom(LawRoom room, List<AssetCatalog.Entry> entries, Path game, GameParser parser, GameAssetIndex index, Path temp) throws Exception {
		String label = room.label();
		AssetCatalog.Entry entry = entryNamed(entries, label);
		expect(label + " is available: " + entry.unavailableReason(), entry.available());
		if (!entry.available()) {
			return;
		}
		AssetSchema schema = entry.schema();
		expect(label + " overrides the game's " + room.file(), entry.type().isOverride() && entry.type().fileName(entry.type().fixedName()).equals(room.file())
				&& schema.overrides().dataFile().equals("init/room/" + room.file() + ".txt") && schema.overrides().textFile().equals("text/room/" + room.file() + ".txt"));
		expect(label + " is in the Rooms group", "Rooms".equals(entry.type().group()));
		expect(label + " offers no sounds and no starter", schema.sounds().isEmpty() && schema.starter() == null);
		expect(label + "'s tags: " + schema.dataTags().stream().map(TagSpec::key).toList(), schema.dataTags().stream().map(TagSpec::key).toList().equals(room.tags()));
		expect(label + "'s text tags: " + schema.textTags().stream().map(TagSpec::key).toList(), schema.textTags().stream().map(TagSpec::key).toList().equals(room.textTags()));
		expect(label + " has " + room.items() + " items", schema.tag("ITEMS").shape().fixedCount() == room.items());
		TagSpec stats = schema.textTags().stream().filter(t -> t.key().equals("STATS")).findFirst().orElseThrow();
		expect(label + "'s stat blocks: " + stats.shape().fixedCount(), stats.shape().fixedCount() == room.stats());
		expect(label + "'s sprites", schema.tag("SPRITES").shape().children().stream().map(TagSpec::key).toList().equals(room.sprites()));
		for (String sprite : room.sprites()) {
			String folder = sprite.endsWith("COMBO") ? "sprite/game/combo/" : sprite.endsWith("BOX") ? "sprite/game/box/" : "sprite/game/1x1/";
			expect(sprite + " looks in the right folder", schema.find("SPRITES." + sprite + ".FRAMES").sprite().folder().equals(folder));
		}
		if (schema.find("WORK") != null) {
			expect(label + "'s WORK only warns when it's left out", !schema.find("WORK").required());
		}

		// The gate: every class matters, and so does a changed key, including the file's own name.
		List<String> classes = lawRoomClasses(room);
		String lower = label.toLowerCase(java.util.Locale.ROOT);
		AssetCatalog.Entry control = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve(lower + "-all.jar"), classes)), label);
		expect("these classes are enough for " + label + ": " + control.unavailableReason(), control.available());
		int at = 0;
		for (String gone : classes) {
			AssetCatalog.Entry without = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve(lower + "-without-" + at++ + ".jar"), classes.stream().filter(n -> !n.equals(gone)).toList())), label);
			expect("without " + gone + ", " + label + " is disabled: " + without.unavailableReason(), !without.available() && without.unavailableReason() != null);
		}
		for (String[] change : room.changes()) {
			AssetCatalog.Entry changed = entryNamed(AssetCatalog.load(patchedJar(game, temp.resolve(lower + "-changed-" + change[1] + ".jar"), classes, change[0], change[1], change[2])), label);
			expect("a changed " + change[1] + " turns " + label + " off: " + changed.unavailableReason(), !changed.available() && changed.unavailableReason() != null);
		}

		// The game's own files, with zero errors and only justified warnings.
		Path folder = temp.resolve("vanilla-" + lower);
		Files.createDirectories(folder);
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile())) {
			Path data = folder.resolve("data.txt");
			Path words = folder.resolve("text.txt");
			try (InputStream in = zip.getInputStream(zip.getEntry("data/assets/init/room/" + room.file() + ".txt"))) {
				Files.copy(in, data, StandardCopyOption.REPLACE_EXISTING);
			}
			try (InputStream in = zip.getInputStream(zip.getEntry("data/assets/text/room/" + room.file() + ".txt"))) {
				Files.copy(in, words, StandardCopyOption.REPLACE_EXISTING);
			}
			List<AssetChecker.Finding> findings = new java.util.ArrayList<>(AssetVanillaCheck.check(schema, index, data, parser));
			for (String warned : room.warned()) {
				expect(room.file() + " warns that " + warned + " is unread", findings.removeIf(f -> warned.equals(f.tag()) && f.severity() == AssetChecker.Severity.WARNING));
			}
			expect(room.file() + " has no other errors or warnings: " + findings, findings.isEmpty());
			List<String> known = new java.util.ArrayList<>(room.textTags());
			known.add("INFO");
			known.addAll(room.warned());
			List<String> keys = parser.tags(words).tags().stream().map(GameParser.TagValue::key).toList();
			expect(room.file() + " text uses only known tags: " + keys, known.containsAll(keys));
			List<AssetChecker.Finding> textFindings = AssetVanillaCheck.checkText(schema, words, parser);
			expect(room.file() + " text reads as the schema says: " + textFindings, textFindings.isEmpty());
		}

		// The rules.
		Map<String, String> good = new LinkedHashMap<>();
		good.put("ICON", "24->resource->Gem->0");
		good.put("RESOURCES", "WOOD");
		good.put("AREA_COSTS", "0");
		good.put("MINI_COLOR", "10_20_30");
		// An override can't leave out a tag the game's file has, and some of the game's files have FLOOR as a list.
		good.put("FLOOR", "DIRT");
		if (schema.find("WORK") != null) {
			good.put("WORK.SHIFT_OFFSET", "0.5");
			good.put("WORK.TITLE", "Keeper");
			good.put("WORK.VERB", "keeping");
		}
		String numbers = String.join(",", java.util.Collections.nCopies(room.stats(), "0"));
		for (int i = 0; i < room.items(); i++) {
			good.put("ITEMS." + i + ".COSTS", "1");
			good.put("ITEMS." + i + ".STATS", numbers);
			good.put("ITEMS." + i + ".NAME", "Item " + i);
			good.put("ITEMS." + i + ".DESC", "About item " + i);
		}
		for (String sprite : room.sprites()) {
			good.put("SPRITES." + sprite + ".0.FRAMES", sprite.endsWith("COMBO") ? "COMBO_TABLES: 6" : sprite.endsWith("BOX") ? "PODIUM: 0" : "STORAGE: 6");
		}
		for (int block = 0; block < room.stats(); block++) {
			good.put("STATS." + block + ".NAME", "Stat " + block);
			good.put("STATS." + block + ".DESC", "About stat " + block);
		}
		good.putAll(room.extra());
		good.put("NAME", "Test " + label);
		good.put("DESC", "It's for testing.");
		expect("a good " + label + " has no problems: " + AssetChecker.check(schema, index, good), AssetChecker.check(schema, index, good).isEmpty());
		for (String key : List.of("ICON", "MINI_COLOR", "ITEMS.0.COSTS", "NAME", "DESC", "ITEMS.0.NAME", "SPRITES." + room.sprites().get(0) + ".0.FRAMES", "STATS.0.NAME")) {
			refused(schema, index, good, key, null);
		}
		refused(schema, index, good, "ITEMS.0.STATS", "0,0,0,0,0,0");
		if (room.extra().containsKey("FLOOR2")) {
			refused(schema, index, good, "FLOOR2", "NOPE");
		}
		if (room.extra().containsKey("EMPLOYMENT.TITLE")) {
			refused(schema, index, good, "EMPLOYMENT.TITLE", null);
			Map<String, String> noEmployment = new LinkedHashMap<>(good);
			noEmployment.keySet().removeIf(key -> key.startsWith("EMPLOYMENT."));
			expect("a " + lower + " without EMPLOYMENT warns about it", AssetChecker.check(schema, index, noEmployment).stream().anyMatch(f -> f.severity() == AssetChecker.Severity.WARNING && "EMPLOYMENT".equals(f.tag())));
		}
		if (schema.find("BOOST") != null) {
			refused(schema, index, good, "BOOST.LAWFULNESS", "1");
			clean(schema, index, good, "BOOST.BEHAVIOUR_LAWFULNESS>ADD", "1");
		}
		if (schema.find("WORK") != null) {
			Map<String, String> noWork = new LinkedHashMap<>(good);
			noWork.keySet().removeIf(key -> key.startsWith("WORK."));
			List<AssetChecker.Finding> withoutWork = AssetChecker.check(schema, index, noWork);
			// The game's own file has WORK, which an override can't take away, so that's an error as well as the warning.
			expect("a " + lower + " without WORK warns about it: " + withoutWork, withoutWork.stream().anyMatch(f -> f.severity() == AssetChecker.Severity.WARNING && f.message().contains("WORK")));
		}
	}

	private static List<String[]> spectatorSounds(String vanilla) {
		return List.of(new String[] { "ROOM_WORK_", "ROOM_WORK_" + vanilla }, new String[] { "ROOM_CLICK_", "ROOM_CLICK_" + vanilla }, new String[] { "ROOM_", "ROOM_" + vanilla },
				new String[] { "ROOM_SERVICE_", "ROOM_SERVICE_" + vanilla });
	}

	private static List<String> needRoomClasses(NeedRoom room) {
		List<String> classes = new java.util.ArrayList<>(FARM_CLASSES);
		for (String gone : List.of("settlement/room/food/farm/ROOM_FARM", "settlement/room/food/farm/Constructor", "settlement/room/industry/module/Industry", "init/type/CLIMATES", "settlement/room/main/BonusExperience$RoomExperienceBonus")) {
			classes.remove(gone);
		}
		classes.add(room.pack() + room.roomClass());
		room.inner().forEach(inner -> classes.add(room.pack() + inner));
		classes.addAll(room.others());
		classes.addAll(List.of("settlement/room/service/module/RoomService", SERVICE_ACCESS, SERVICE_NEED, "game/boosting/BoostSpecs", "settlement/stats/standing/StatStanding$StandingDef",
				"settlement/stats/standing/StatStanding$StandingDef$StandingData", "settlement/room/sprite/RoomSpriteImp", "settlement/room/sprite/RoomSprite1x1", "settlement/room/sprite/RoomSprite1xN",
				"settlement/room/sprite/RoomSpriteCombo", "init/sprite/game/Sheets", "init/sprite/game/GameSheets", "init/sprite/game/SheetData"));
		return classes;
	}

	private static Map<String, String> validNeedRoom(NeedRoom room, AssetSchema schema) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("ICON", "24->resource->Gem->0");
		values.put("RESOURCES", "WOOD");
		values.put("AREA_COSTS", "0");
		values.put("MINI_COLOR", "10_20_30");
		if (schema.overrides() != null) {
			// An override can't leave out a tag the game's file has, and some of the game's files have FLOOR as a list.
			values.put("FLOOR", "DIRT");
		}
		if (room.work() != null) {
			values.put("WORK.SHIFT_OFFSET", "0.5");
			values.put("WORK.TITLE", "Keeper");
			values.put("WORK.VERB", "keeping");
		}
		String stats = String.join(",", java.util.Collections.nCopies(room.stats(), "0"));
		for (int i = 0; i < room.items(); i++) {
			values.put("ITEMS." + i + ".COSTS", "1");
			values.put("ITEMS." + i + ".STATS", stats);
			values.put("ITEMS." + i + ".NAME", "Item " + i);
			values.put("ITEMS." + i + ".DESC", "About item " + i);
		}
		for (String sprite : room.sprites()) {
			values.put("SPRITES." + sprite + ".0.FRAMES", sprite.endsWith("COMBO") ? "COMBO_TABLES: 6" : sprite.endsWith("TEXTURE") ? "STONE: 7" : sprite.endsWith("BOX") ? "PODIUM: 0" : "STORAGE: 6");
		}
		for (int block : room.namedStats()) {
			values.put("STATS." + block + ".NAME", "Stat " + block);
			values.put("STATS." + block + ".DESC", "About stat " + block);
		}
		values.put("SERVICE.NEED", "GROOMING");
		values.put("SERVICE.STANDING.CITIZEN", "0.75");
		values.put("SERVICE.STANDING.PRIO", "3");
		values.put("SERVICE.VERB", "Freshening up");
		values.put("SERVICE.MORE", "We want more.\nMuch more.");
		switch (room.label()) {
		case "Bath" -> {
			values.put("INDUSTRY.IN.WOOD", "1");
			values.put("WATER_COLOR", "20_40_100");
			values.put("WATER_OPACITY", "0.5");
			values.put("HEATING", "Heat");
			values.put("HEATING_DESC", "How warm it is.");
			values.put("HEATING_PROBLEM", "Too cold.");
			values.put("WATER_PROBLEM", "No water.");
		}
		case "Barber" -> values.put("WORK_TIME_IN_DAYS", "0.25");
		case "Pleasure" -> {
			values.put("FLOOR2", "WOOD");
			values.put("COLOR_PIXEL_BASE", "100_80_60");
		}
		default -> {
		}
		}
		for (SoundEntries.Slot slot : schema.sounds()) {
			values.put("SOUND." + slot.prefix(), "DUMMY");
		}
		values.put("NAME", "Test " + room.label());
		values.put("DESC", "It's for testing.");
		return values;
	}

	/** The tables, the gate, the vanilla file, the rules and a new asset read back, for a Well, a Bath, a Lavatory or a Barber. */
	private static void needRoom(NeedRoom room, List<AssetCatalog.Entry> entries, Path game, Path mods, GameParser parser, GameParser.GameVersion version, GameAssetIndex index, Path temp) throws Exception {
		String label = room.label();
		AssetCatalog.Entry entry = entryNamed(entries, label);
		expect(label + " is available: " + entry.unavailableReason(), entry.available());
		if (!entry.available()) {
			return;
		}
		AssetSchema schema = entry.schema();
		expect(label + "'s files start with its prefix", entry.type().fileName("X").equals(room.prefix() + "X"));
		expect(label + " is in the Rooms group", "Rooms".equals(entry.type().group()));
		expect(label + "'s tags: " + schema.dataTags().stream().map(TagSpec::key).toList(), schema.dataTags().stream().map(TagSpec::key).toList().equals(room.tags()));
		expect(label + "'s text tags: " + schema.textTags().stream().map(TagSpec::key).toList(), schema.textTags().stream().map(TagSpec::key).toList().equals(room.textTags()));
		for (String unread : List.of("BONUS", "EXPERIENCE_BONUS", "STORAGE", "EMPLOYMENT", "REQUIRES", "USAGE", "DEFAULT_STANDING", "SERVICE.SOUND", "SERVICE.DEFAULT_ACCESS", "SERVICE.DEFAULT_VALUE", "WORK.SOUND")) {
			expect(label + " doesn't offer " + unread, schema.find(unread) == null);
		}
		if (room.work() == null) {
			expect(label + " doesn't offer WORK", schema.find("WORK") == null);
		} else {
			expect(label + "'s WORK is required only where the code proves it: " + schema.find("WORK").required(), schema.find("WORK").required() == room.work());
		}
		expectNested(schema, "SERVICE", TagSpec.Kind.BLOCK, true, null, null, null);
		expectNested(schema, "SERVICE.NEED", TagSpec.Kind.KEY, true, null, null, null);
		expect(label + "'s NEED names a need file", schema.find("SERVICE.NEED").reference() == TagSpec.Reference.NEEDS);
		expectNested(schema, "SERVICE.RADIUS", TagSpec.Kind.INT, false, "150", 0.0, 50000.0);
		expectNested(schema, "SERVICE.STANDING", TagSpec.Kind.BLOCK, true, null, null, null);
		expectNested(schema, "SERVICE.STANDING.EXPONENT", TagSpec.Kind.DECIMAL, false, "1", 0.01, 10.0);
		expectNested(schema, "SERVICE.STANDING.NOBLE", TagSpec.Kind.DECIMAL, false, "0", null, null);
		expectNested(schema, "SERVICE.BOOST", TagSpec.Kind.DECIMAL_MAP, false, null, null, null);
		expectNested(schema, "SERVICE.USAGE", TagSpec.Kind.DECIMAL, false, "1", 0.0, 1.0);
		expectNested(schema, "SERVICE.VERB", TagSpec.Kind.TEXT_LINE, true, null, null, null);
		expectNested(schema, "SERVICE.MORE", TagSpec.Kind.TEXT_ROWS, true, null, null, null);
		expect(label + " has " + room.items() + " items", schema.tag("ITEMS").shape().fixedCount() == room.items());
		TagSpec stats = schema.textTags().stream().filter(t -> t.key().equals("STATS")).findFirst().orElseThrow();
		expect(label + "'s stat blocks: " + stats.shape().fixedCount() + ", " + stats.shape().namedSlots(), stats.shape().fixedCount() == room.stats() && stats.shape().namedSlots() == room.namedSlots());
		expect(label + "'s sprites", schema.tag("SPRITES").shape().children().stream().map(TagSpec::key).toList().equals(room.sprites()));
		for (String sprite : room.sprites()) {
			String folder = sprite.endsWith("COMBO") ? "sprite/game/combo/" : sprite.endsWith("TEXTURE") ? "sprite/game/texture/" : sprite.endsWith("BOX") ? "sprite/game/box/" : "sprite/game/1x1/";
			expect(sprite + " looks in the right folder", schema.find("SPRITES." + sprite + ".FRAMES").sprite().folder().equals(folder));
		}
		String vanilla = entry.type().isOverride() ? entry.type().fileName(entry.type().fixedName()) : room.prefix() + "NORMAL";
		if (entry.type().isOverride()) {
			// A room the game builds from one file of its own: a new one is a copy of that file, and its sounds aren't offered.
			expect(label + " overrides the game's " + vanilla, schema.overrides() != null && schema.overrides().dataFile().equals("init/room/" + vanilla + ".txt")
					&& schema.overrides().textFile().equals("text/room/" + vanilla + ".txt") && schema.starter() == null);
		} else {
			expect(label + " starts its sprites from the game's only one", schema.starter().file().equals("init/room/" + vanilla + ".txt"));
		}
		List<String> soundPrefixes = schema.sounds().stream().map(SoundEntries.Slot::prefix).toList();
		List<String> soundLikes = schema.sounds().stream().map(SoundEntries.Slot::likeKey).toList();
		expect(label + "'s sounds: " + soundPrefixes + " " + soundLikes, soundPrefixes.equals(room.sounds().stream().map(s -> s[0]).toList())
				&& soundLikes.equals(room.sounds().stream().map(s -> s[1]).toList()));
		if (label.equals("Bath")) {
			expectNested(schema, "INDUSTRY.IN", TagSpec.Kind.KEYED_ROWS, true, null, null, null);
			expectNested(schema, "INDUSTRY.OUT", TagSpec.Kind.KEYED_ROWS, false, "Nothing produced", null, null);
			expectNested(schema, "WATER_COLOR", TagSpec.Kind.COLOR, true, null, 0.0, 255.0);
			expectNested(schema, "WATER_OPACITY", TagSpec.Kind.DECIMAL, true, null, 0.0, 1.0);
		}
		if (label.equals("Barber")) {
			expectNested(schema, "WORK_TIME_IN_DAYS", TagSpec.Kind.DECIMAL, true, null, null, null);
		}

		// The gate: every class matters, and so does a changed key.
		List<String> classes = needRoomClasses(room);
		String lower = label.toLowerCase(java.util.Locale.ROOT);
		AssetCatalog.Entry control = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve(lower + "-all.jar"), classes)), label);
		expect("these classes are enough for " + label + ": " + control.unavailableReason(), control.available());
		int at = 0;
		for (String gone : classes) {
			AssetCatalog.Entry without = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve(lower + "-without-" + at++ + ".jar"), classes.stream().filter(n -> !n.equals(gone)).toList())), label);
			expect("without " + gone + ", " + label + " is disabled: " + without.unavailableReason(), !without.available() && without.unavailableReason() != null);
		}
		for (String[] change : room.changes()) {
			AssetCatalog.Entry changed = entryNamed(AssetCatalog.load(patchedJar(game, temp.resolve(lower + "-changed-" + change[1] + ".jar"), classes, change[0], change[1], change[2])), label);
			expect("a changed " + change[1] + " turns " + label + " off: " + changed.unavailableReason(), !changed.available() && changed.unavailableReason() != null);
		}

		// The one vanilla file, with zero errors and only justified warnings.
		Path folder = temp.resolve("vanilla-" + lower);
		Files.createDirectories(folder.resolve("data"));
		Files.createDirectories(folder.resolve("text"));
		String file = vanilla + ".txt";
		List<String> known = new java.util.ArrayList<>(schema.textTags().stream().map(TagSpec::key).toList());
		known.add("INFO");
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile())) {
			Path data = folder.resolve("data").resolve(file);
			Path words = folder.resolve("text").resolve(file);
			try (InputStream in = zip.getInputStream(zip.getEntry("data/assets/init/room/" + file))) {
				Files.copy(in, data, StandardCopyOption.REPLACE_EXISTING);
			}
			try (InputStream in = zip.getInputStream(zip.getEntry("data/assets/text/room/" + file))) {
				Files.copy(in, words, StandardCopyOption.REPLACE_EXISTING);
			}
			List<AssetChecker.Finding> findings = new java.util.ArrayList<>(AssetVanillaCheck.check(schema, index, data, parser));
			for (String warned : room.warned()) {
				// LAVATORY_NORMAL writes DEFAULT_STANDING at the top, and nothing reads it.
				expect(file + " warns that " + warned + " is unread", findings.removeIf(f -> warned.equals(f.tag()) && f.severity() == AssetChecker.Severity.WARNING));
			}
			expect(file + " has no other errors or warnings: " + findings, findings.isEmpty());
			List<String> keys = parser.tags(words).tags().stream().map(GameParser.TagValue::key).toList();
			expect(file + " text uses only known tags: " + keys, known.containsAll(keys));
			List<AssetChecker.Finding> textFindings = AssetVanillaCheck.checkText(schema, words, parser);
			expect(file + " text reads as the schema says: " + textFindings, textFindings.isEmpty());
		}

		// The rules.
		Map<String, String> good = validNeedRoom(room, schema);
		expect("a good " + label + " has no problems: " + AssetChecker.check(schema, index, good), AssetChecker.check(schema, index, good).isEmpty());
		for (String key : List.of("ICON", "MINI_COLOR", "ITEMS.0.COSTS", "NAME", "DESC", "ITEMS.0.NAME", "SPRITES." + room.sprites().get(0) + ".0.FRAMES", "SERVICE.NEED", "SERVICE.VERB", "SERVICE.MORE")) {
			refused(schema, index, good, key, null);
		}
		if (!schema.sounds().isEmpty()) {
			refused(schema, index, good, "SOUND.ROOM_SERVICE_", null);
		}
		refused(schema, index, good, "SERVICE.NEED", "NOPE");
		clean(schema, index, good, "SERVICE.NEED", "BATH");
		for (int block : room.namedStats()) {
			refused(schema, index, good, "STATS." + block + ".NAME", null);
			refused(schema, index, good, "STATS." + block + ".DESC", null);
		}
		if (!room.namedStats().contains(0)) {
			clean(schema, index, good, "STATS.0.NAME", null);
		}
		refused(schema, index, good, "ITEMS.0.STATS", "0,0,0,0,0,0");
		refused(schema, index, good, "SERVICE.STANDING.CITIZEN", "1000001");
		clean(schema, index, good, "SERVICE.STANDING.CITIZEN", "1000000");
		refused(schema, index, good, "SERVICE.STANDING.CHILD", "yes");
		refused(schema, index, good, "SERVICE.USAGE", "1.5");
		refused(schema, index, good, "SERVICE.BOOST.HAPPINESS", "1");
		clean(schema, index, good, "SERVICE.BOOST.PHYSICS_HEALTH>MUL", "1.5");
		refused(schema, index, good, "SERVICE.RADIUS", "-1");
		warned(schema, index, good, "SERVICE.RADIUS", "50001");
		if (room.work() != null) {
			refused(schema, index, good, "WORK.TITLE", null);
			Map<String, String> noWork = new LinkedHashMap<>(good);
			noWork.remove("WORK.SHIFT_OFFSET");
			noWork.remove("WORK.TITLE");
			noWork.remove("WORK.VERB");
			List<AssetChecker.Finding> withoutWork = AssetChecker.check(schema, index, noWork);
			if (room.work()) {
				expect("a " + lower + " without WORK is refused: " + withoutWork, withoutWork.stream().anyMatch(AssetChecker.Finding::blocksSaving));
			} else {
				expect("a " + lower + " without WORK only warns: " + withoutWork, !withoutWork.isEmpty() && withoutWork.stream().noneMatch(AssetChecker.Finding::blocksSaving));
			}
		}
		if (label.equals("Bath")) {
			refused(schema, index, good, "INDUSTRY.IN.WOOD", null);
			refused(schema, index, good, "INDUSTRY.IN.WOOD", "10001");
			refused(schema, index, good, "INDUSTRY.OUT.MEAT.PLAYER", "1");
			refused(schema, index, good, "WATER_COLOR", null);
			refused(schema, index, good, "WATER_OPACITY", null);
			refused(schema, index, good, "WATER_OPACITY", "1.5");
			clean(schema, index, good, "WATER_OPACITY", "1");
			for (String words : List.of("HEATING", "HEATING_DESC", "HEATING_PROBLEM", "WATER_PROBLEM")) {
				refused(schema, index, good, words, null);
			}
		}
		if (label.equals("Barber")) {
			refused(schema, index, good, "WORK_TIME_IN_DAYS", null);
			clean(schema, index, good, "WORK_TIME_IN_DAYS", "2");
		}
		if (label.equals("Pleasure")) {
			refused(schema, index, good, "FLOOR2", null);
			refused(schema, index, good, "FLOOR2", "NOPE");
			refused(schema, index, good, "COLOR_PIXEL_BASE", null);
			refused(schema, index, good, "COLOR_PIXEL_BASE", "300_0_0");
		}

		// A new one, read back with the game's own reads at the paths the loader uses.
		ModEntry mod = ModCreator.create(mods, game, label + " Creation");
		Path root = mod.root();
		Path modVersion = AssetCreator.versionFolder(root, version);
		Path assets = modVersion.resolve("assets");
		GameAssetIndex modIndex = GameAssetIndex.load(game, modVersion, parser);
		expect("a new " + label + " starts with the game's sprites", AssetForm.startingValues(schema, modIndex).containsKey("SPRITES." + room.sprites().get(0) + ".0.FRAMES"));
		Map<String, String> made0 = new LinkedHashMap<>(good);
		// SERVICE is in both files, and a boost is an entry of the data file's map, not a text tag.
		expect(label + "'s boost belongs in the data file", !schema.isTextKey("SERVICE.BOOST.PHYSICS_HEALTH>MUL") && schema.isTextKey("SERVICE.VERB") && !schema.isTextKey("SERVICE.STANDING.CITIZEN"));
		made0.put("SERVICE.BOOST.PHYSICS_HEALTH>MUL", "1.5");
		boolean blankDesc = schema.find("ITEMS.DESC") != null && schema.find("ITEMS.DESC").blankAllowed();
		if (blankDesc) {
			made0.remove("ITEMS.0.DESC");
		}
		// An override's name is the game's file's.
		String newName = entry.type().isOverride() ? entry.type().fixedName() : "TEST_ROOM";
		AssetCreator.Created made = AssetCreator.create(root, entry.type(), schema, newName, made0, parser, modIndex);
		expect(label + "'s file has the prefix the game needs", made.dataFile().equals(assets.resolve("init/room/" + room.prefix() + newName + ".txt")));
		if (!schema.sounds().isEmpty()) {
			String mono = Files.readString(assets.resolve("audio/config/mono/" + room.prefix() + newName + ".txt"));
			expect("its service sound is written: " + mono, mono.contains("ROOM_SERVICE_" + room.prefix() + newName + ": DUMMY"));
		}
		List<AssetChecker.Finding> findings = new java.util.ArrayList<>(AssetVanillaCheck.check(schema, modIndex, made.dataFile(), parser));
		if (entry.type().isOverride()) {
			// A new override is a copy of the game's file, so it keeps what that file has and nothing reads.
			findings.removeIf(f -> room.warned().contains(f.tag()) && f.severity() == AssetChecker.Severity.WARNING);
		}
		expect("the new " + label + " passes the vanilla check: " + findings, findings.isEmpty());
		List<AssetChecker.Finding> textFindings = AssetVanillaCheck.checkText(schema, made.textFile(), parser);
		expect("its text passes too: " + textFindings, textFindings.isEmpty());
		GameParser.Node node = parser.root(made.dataFile());
		GameParser.Node service = node.block("SERVICE");
		expect("the game reads the need", "GROOMING".equals(service.value("NEED")));
		expect("and the standing", service.block("STANDING").decimal("CITIZEN", 0, 1000000) == 0.75);
		expect("and the boost, from the data file", service.block("BOOST").decimal("PHYSICS_HEALTH>MUL") == 1.5);
		expect("which isn't in the text file", !parser.root(made.textFile()).block("SERVICE").has("BOOST"));
		if (label.equals("Bath")) {
			expect("the game reads the fuel", node.block("INDUSTRY").block("IN").decimal("WOOD", 0, 10000) == 1);
			expect("and the water's opacity", node.decimal("WATER_OPACITY", 0, 1) == 0.5);
		}
		if (label.equals("Barber")) {
			expect("the game reads the work time", node.decimal("WORK_TIME_IN_DAYS") == 0.25);
		}
		if (label.equals("Pleasure")) {
			expect("the game reads the booths' floor", "WOOD".equals(node.value("FLOOR2")));
		}
		GameParser.Node text = parser.root(made.textFile());
		expect("the text has the service verb", "Freshening up".equals(text.block("SERVICE").text("VERB")));
		expect("and what subjects say", List.of("We want more.", "Much more.").equals(List.of(text.block("SERVICE").texts("MORE"))));
		expect("and every stat block", text.blocks("STATS").size() == room.stats());
		for (int block : room.namedStats()) {
			expect("and the game can read block " + block, ("Stat " + block).equals(text.blocks("STATS").get(block).text("NAME")));
		}
		if (blankDesc) {
			expect("an item description left empty is written blank, which the game reads", "".equals(text.blocks("ITEMS").get(0).text("DESC")));
		}
		if (label.equals("Bath")) {
			expect("the game reads the heating words at the top", "Heat".equals(text.text("HEATING")) && "No water.".equals(text.text("WATER_PROBLEM")));
		}
		expect("a vanilla name is refused", AssetCreator.problemWithName(entry.type(), "NORMAL", modIndex) != null);
		SoundEntries sounds = SoundEntries.load(game, modVersion, parser);
		expect("the mod's sound check finds nothing missing: " + sounds.missingIn(modVersion, parser), sounds.missingIn(modVersion, parser).isEmpty());
		if (!schema.sounds().isEmpty()) {
			expect("and would report a missing service sound", sounds.missingForRoom(room.prefix() + "NOPE", room.work() != null).contains("ROOM_SERVICE_" + room.prefix() + "NOPE"));
		}
	}

	/**
	 * A Tavern, an Eatery or a Canteen, each checked by hand against its ROOM_ class, its Constructor, RoomService,
	 * RoomServiceAccess, StandingDef and BoostSpecs.
	 *
	 * @param namedStats the STATS blocks whose words the game reads
	 * @param inner each class after the package that handles the file, besides the ROOM_ class
	 * @param others more classes the gate reviews
	 * @param changes a full class name, a key in it, and what to rename the key to
	 * @param warned a data tag vanilla writes and nothing reads
	 */
	private record AccessRoom(String label, String prefix, String folder, String roomClass, List<String> tags, List<String> sprites, int items, int stats,
			List<Integer> namedStats, List<String> inner, List<String> others, boolean workRequired, String[][] changes, String warned) {
	}

	private static final String SERVICE_ACCESS = "settlement/room/service/module/RoomServiceAccess";
	private static final String DISTRIBUTION = "settlement/room/service/food/eatery/RoomDistribution";
	private static final List<AccessRoom> ACCESS_ROOMS = List.of(
			new AccessRoom("Tavern", "TAVERN_", "tavern", "ROOM_TAVERN",
					List.of("ICON", "RESOURCES", "AREA_COSTS", "FLOOR", "MINI_COLOR", "MINI_COLOR_PATTERN", "WORK", "ITEMS", "SPRITES", "SERVICE", "UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE"),
					List.of("JUG_1X1", "JUG_FILL_1X1", "TABLE_COMBO", "STORAGE_1X1", "CHAIR_1X1", "TABLE_DECOR_1X1", "MISC_1X1", "MISC_DECOR_1X1", "CARPET_COMBO"),
					3, 2, List.of(1), List.of("Constructor", "Constructor$1", "Constructor$2", "Constructor$3", "Constructor$5"),
					List.of(DISTRIBUTION, "settlement/room/main/furnisher/FurnisherItemTools", "settlement/room/main/furnisher/FurnisherItemTools$2", "settlement/room/main/furnisher/FurnisherItemTools$3"), false,
					new String[][] { { "settlement/room/service/food/tavern/Constructor", "JUG_FILL_1X1", "JUG_FILL_1X2" }, { "settlement/room/service/food/tavern/Constructor$5", "MISC_DECOR_1X1", "MISC_DECOR_1X2" },
							{ SERVICE_ACCESS, "USAGE", "USAGX" } }, null),
			new AccessRoom("Eatery", "EATERY_", "eatery", "ROOM_EATERY",
					List.of("ICON", "RESOURCES", "AREA_COSTS", "FLOOR", "MINI_COLOR", "MINI_COLOR_PATTERN", "WORK", "ITEMS", "SPRITES", "SERVICE", "UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE"),
					List.of("CRATE_BOTTOM_A_1X1", "CRATE_TOP_1X1", "STALL_BOTTOM_1X1", "STALL_TOP_1X1", "MISC_BOTTOM_1X1", "MISC_1X1", "CARPET_COMBO"),
					1, 2, List.of(), List.of("Constructor", "Constructor$1", "Constructor$2", "Constructor$2$1", "Constructor$3", "Constructor$4"), List.of(DISTRIBUTION), false,
					new String[][] { { "settlement/room/service/food/eatery/Constructor$2", "STALL_TOP_1X1", "STALL_TOP_1X2" }, { SERVICE_ACCESS, "MORE", "MORX" } }, "USAGE"),
			new AccessRoom("Canteen", "CANTEEN_", "canteen", "ROOM_CANTEEN",
					List.of("ICON", "RESOURCES", "AREA_COSTS", "FLOOR", "MINI_COLOR", "MINI_COLOR_PATTERN", "WORK", "ITEMS", "SPRITES", "SERVICE", "INDUSTRY", "UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE"),
					List.of("PLATE_1X1", "TABLE_COMBO", "OVEN_BENEATH_1X1", "OVEN_BENEATH_USED_1X1", "OVEN_1X1", "TOOLS_1X1", "STOOL_1X1", "DECOR_1X1"),
					2, 3, List.of(2), List.of("Constructor", "Constructor$1", "Constructor$1$1", "Constructor$2", "Constructor$3", "Constructor$4", "Constructor$5"),
					List.of("settlement/room/industry/module/Industry"), true,
					new String[][] { { "settlement/room/service/food/canteen/Constructor$1", "OVEN_BENEATH_USED_1X1", "OVEN_BENEATH_USED_1X2" },
							{ "settlement/stats/standing/StatStanding$StandingDef", "EXPONENT", "EXPONENX" } }, null),
			new AccessRoom("Market", "MARKET_", "settlement/room/service/market/", "ROOM_MARKET",
					List.of("ICON", "RESOURCES", "AREA_COSTS", "FLOOR", "MINI_COLOR", "MINI_COLOR_PATTERN", "WORK", "ITEMS", "SPRITES", "SERVICE", "UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE"),
					List.of("CRATE_1X1", "STALL_BOTTOM_COMBO", "STALL_TOP_1X1", "MISC_BOTTOM_1X1", "MISC_1X1", "CARPET_COMBO"),
					1, 2, List.of(), List.of("Constructor", "Constructor$1", "Constructor$2", "Constructor$2$1", "Constructor$3", "Constructor$4"), List.of(DISTRIBUTION), false,
					new String[][] { { "settlement/room/service/market/Constructor$3", "MISC_1X1", "MISC_1X2" }, { "settlement/room/service/market/Constructor", "CRATE_1X1", "CRATE_1X2" } }, null));

	private static List<String> accessRoomClasses(AccessRoom room) {
		List<String> classes = new java.util.ArrayList<>(FARM_CLASSES);
		for (String gone : List.of("settlement/room/food/farm/ROOM_FARM", "settlement/room/food/farm/Constructor", "settlement/room/industry/module/Industry", "init/type/CLIMATES", "settlement/room/main/BonusExperience$RoomExperienceBonus")) {
			classes.remove(gone);
		}
		// The food rooms give their folder, and a room elsewhere its whole package.
		String pack = room.folder().contains("/") ? room.folder() : "settlement/room/service/food/" + room.folder() + "/";
		classes.add(pack + room.roomClass());
		room.inner().forEach(inner -> classes.add(pack + inner));
		classes.addAll(room.others());
		classes.addAll(List.of("settlement/room/service/module/RoomService", SERVICE_ACCESS, "game/boosting/BoostSpecs", "settlement/stats/standing/StatStanding$StandingDef",
				"settlement/stats/standing/StatStanding$StandingDef$StandingData", "settlement/room/sprite/RoomSpriteImp", "settlement/room/sprite/RoomSprite1x1", "settlement/room/sprite/RoomSprite1xN",
				"settlement/room/sprite/RoomSpriteCombo", "init/sprite/game/Sheets", "init/sprite/game/GameSheets", "init/sprite/game/SheetData"));
		return classes;
	}

	private static Map<String, String> validAccessRoom(AccessRoom room) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("ICON", "24->resource->Gem->0");
		values.put("RESOURCES", "WOOD");
		values.put("AREA_COSTS", "0");
		values.put("MINI_COLOR", "10_20_30");
		values.put("WORK.SHIFT_OFFSET", "0.5");
		values.put("WORK.TITLE", "Keeper");
		values.put("WORK.VERB", "keeping");
		String stats = String.join(",", java.util.Collections.nCopies(room.stats(), "0"));
		for (int i = 0; i < room.items(); i++) {
			values.put("ITEMS." + i + ".COSTS", "1");
			values.put("ITEMS." + i + ".STATS", stats);
			values.put("ITEMS." + i + ".NAME", "Item " + i);
			values.put("ITEMS." + i + ".DESC", "About item " + i);
		}
		for (String sprite : room.sprites()) {
			values.put("SPRITES." + sprite + ".0.FRAMES", sprite.endsWith("COMBO") ? "COMBO_TABLES: 6" : "STORAGE: 6");
		}
		for (int block : room.namedStats()) {
			values.put("STATS." + block + ".NAME", "Stat " + block);
			values.put("STATS." + block + ".DESC", "About stat " + block);
		}
		values.put("SERVICE.STANDING.CITIZEN", "0.75");
		values.put("SERVICE.STANDING.PRIO", "3");
		values.put("SERVICE.VERB", "Having a bite");
		values.put("SERVICE.MORE", "We want more.\nMuch more.");
		if (room.label().equals("Canteen")) {
			values.put("INDUSTRY.IN.WOOD", "1");
		}
		values.put("SOUND.ROOM_WORK_", "DUMMY");
		values.put("SOUND.ROOM_CLICK_", "DUMMY");
		values.put("SOUND.ROOM_", "DUMMY");
		values.put("SOUND.ROOM_SERVICE_", "DUMMY");
		values.put("NAME", "Test " + room.label());
		values.put("DESC", "It's for testing.");
		return values;
	}

	/** The tables, the gate, the vanilla file, the rules and a new asset read back, for a Tavern, an Eatery or a Canteen. */
	private static void accessRoom(AccessRoom room, List<AssetCatalog.Entry> entries, Path game, Path mods, GameParser parser, GameParser.GameVersion version, GameAssetIndex index, Path temp) throws Exception {
		String label = room.label();
		AssetCatalog.Entry entry = entryNamed(entries, label);
		expect(label + " is available: " + entry.unavailableReason(), entry.available());
		if (!entry.available()) {
			return;
		}
		AssetSchema schema = entry.schema();
		expect(label + "'s files start with its prefix", entry.type().fileName("X").equals(room.prefix() + "X"));
		expect(label + "'s tags: " + schema.dataTags().stream().map(TagSpec::key).toList(), schema.dataTags().stream().map(TagSpec::key).toList().equals(room.tags()));
		for (String unread : List.of("BONUS", "EXPERIENCE_BONUS", "STORAGE", "EMPLOYMENT", "REQUIRES", "USAGE", "SERVICE.SOUND", "SERVICE.NEED", "SERVICE.DEFAULT_ACCESS", "SERVICE.DEFAULT_VALUE", "WORK.SOUND")) {
			expect(label + " doesn't offer " + unread, schema.find(unread) == null);
		}
		expect(label + "'s WORK is required only where the code proves it: " + schema.find("WORK").required(), schema.find("WORK").required() == room.workRequired());
		expectNested(schema, "SERVICE", TagSpec.Kind.BLOCK, true, null, null, null);
		expectNested(schema, "SERVICE.RADIUS", TagSpec.Kind.INT, false, "150", 0.0, 50000.0);
		expectNested(schema, "SERVICE.STANDING", TagSpec.Kind.BLOCK, true, null, null, null);
		expectNested(schema, "SERVICE.STANDING.MULTIPLIER", TagSpec.Kind.DECIMAL, false, "1", 0.0, 100000.0);
		expectNested(schema, "SERVICE.STANDING.EXPONENT", TagSpec.Kind.DECIMAL, false, "1", 0.01, 10.0);
		expectNested(schema, "SERVICE.STANDING.PRIO", TagSpec.Kind.DECIMAL, false, "1", 0.0, 100000.0);
		expectNested(schema, "SERVICE.STANDING.CHILD", TagSpec.Kind.BOOL, false, "false", null, null);
		expectNested(schema, "SERVICE.STANDING.NOBLE", TagSpec.Kind.DECIMAL, false, "0", null, null);
		expectNested(schema, "SERVICE.BOOST", TagSpec.Kind.DECIMAL_MAP, false, null, null, null);
		expectNested(schema, "SERVICE.USAGE", TagSpec.Kind.DECIMAL, false, "1", 0.0, 1.0);
		expectNested(schema, "SERVICE.VERB", TagSpec.Kind.TEXT_LINE, true, null, null, null);
		expectNested(schema, "SERVICE.MORE", TagSpec.Kind.TEXT_ROWS, true, null, null, null);
		expect(label + " has " + room.items() + " items", schema.tag("ITEMS").shape().fixedCount() == room.items());
		TagSpec stats = schema.textTags().stream().filter(t -> t.key().equals("STATS")).findFirst().orElseThrow();
		// A block read by position isn't the first, so every block is shown and a rule asks for the words (as for a Hunter).
		int shown = room.namedStats().isEmpty() ? 0 : room.stats();
		expect(label + "'s stat blocks: " + stats.shape().fixedCount() + ", " + stats.shape().namedSlots(), stats.shape().fixedCount() == room.stats() && stats.shape().namedSlots() == shown);
		expect(label + "'s sprites", schema.tag("SPRITES").shape().children().stream().map(TagSpec::key).toList().equals(room.sprites()));
		for (String sprite : room.sprites()) {
			expect(sprite + " looks in the right folder", schema.find("SPRITES." + sprite + ".FRAMES").sprite().folder().equals(sprite.endsWith("COMBO") ? "sprite/game/combo/" : "sprite/game/1x1/"));
		}
		String vanilla = room.prefix() + "NORMAL";
		expect(label + " starts its sprites from the game's only one", schema.starter().file().equals("init/room/" + vanilla + ".txt"));
		expect(label + "'s sounds: " + schema.sounds().stream().map(SoundEntries.Slot::likeKey).toList(), java.util.Arrays.asList("ROOM_WORK_" + vanilla, "ROOM_CLICK_" + vanilla, "ROOM_" + vanilla, null)
				.equals(schema.sounds().stream().map(SoundEntries.Slot::likeKey).toList()) && schema.sounds().get(3).prefix().equals("ROOM_SERVICE_"));
		if (label.equals("Canteen")) {
			expectNested(schema, "INDUSTRY.IN", TagSpec.Kind.KEYED_ROWS, true, null, null, null);
			expectNested(schema, "INDUSTRY.OUT", TagSpec.Kind.KEYED_ROWS, false, "Nothing produced", null, null);
		}

		// The gate: every class matters, and so does a changed key.
		List<String> classes = accessRoomClasses(room);
		String lower = label.toLowerCase(java.util.Locale.ROOT);
		AssetCatalog.Entry control = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve(lower + "-all.jar"), classes)), label);
		expect("these classes are enough for " + label + ": " + control.unavailableReason(), control.available());
		int at = 0;
		for (String gone : classes) {
			AssetCatalog.Entry without = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve(lower + "-without-" + at++ + ".jar"), classes.stream().filter(n -> !n.equals(gone)).toList())), label);
			expect("without " + gone + ", " + label + " is disabled: " + without.unavailableReason(), !without.available() && without.unavailableReason() != null);
		}
		for (String[] change : room.changes()) {
			AssetCatalog.Entry changed = entryNamed(AssetCatalog.load(patchedJar(game, temp.resolve(lower + "-changed-" + change[1] + ".jar"), classes, change[0], change[1], change[2])), label);
			expect("a changed " + change[1] + " turns " + label + " off: " + changed.unavailableReason(), !changed.available() && changed.unavailableReason() != null);
		}

		// The one vanilla file, with zero errors and only justified warnings.
		Path folder = temp.resolve("vanilla-" + lower);
		Files.createDirectories(folder.resolve("data"));
		Files.createDirectories(folder.resolve("text"));
		String file = vanilla + ".txt";
		List<String> known = new java.util.ArrayList<>(schema.textTags().stream().map(TagSpec::key).toList());
		known.add("INFO");
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile())) {
			Path data = folder.resolve("data").resolve(file);
			Path words = folder.resolve("text").resolve(file);
			try (InputStream in = zip.getInputStream(zip.getEntry("data/assets/init/room/" + file))) {
				Files.copy(in, data, StandardCopyOption.REPLACE_EXISTING);
			}
			try (InputStream in = zip.getInputStream(zip.getEntry("data/assets/text/room/" + file))) {
				Files.copy(in, words, StandardCopyOption.REPLACE_EXISTING);
			}
			List<AssetChecker.Finding> findings = new java.util.ArrayList<>(AssetVanillaCheck.check(schema, index, data, parser));
			if (room.warned() != null) {
				// EATERY_NORMAL writes USAGE at the top, and only SERVICE.USAGE is read (RoomServiceAccess.java:52).
				expect(file + " warns that " + room.warned() + " is unread", findings.removeIf(f -> room.warned().equals(f.tag()) && f.severity() == AssetChecker.Severity.WARNING));
			}
			expect(file + " has no other errors or warnings: " + findings, findings.isEmpty());
			List<String> keys = parser.tags(words).tags().stream().map(GameParser.TagValue::key).toList();
			expect(file + " text uses only known tags: " + keys, known.containsAll(keys));
			List<AssetChecker.Finding> textFindings = AssetVanillaCheck.checkText(schema, words, parser);
			expect(file + " text reads as the schema says: " + textFindings, textFindings.isEmpty());
		}

		// The rules.
		Map<String, String> good = validAccessRoom(room);
		expect("a good " + label + " has no problems: " + AssetChecker.check(schema, index, good), AssetChecker.check(schema, index, good).isEmpty());
		for (String key : List.of("ICON", "MINI_COLOR", "ITEMS.0.COSTS", "NAME", "DESC", "ITEMS.0.NAME", "SPRITES." + room.sprites().get(0) + ".0.FRAMES", "SERVICE.VERB", "SERVICE.MORE", "SOUND.ROOM_SERVICE_")) {
			refused(schema, index, good, key, null);
		}
		for (int block : room.namedStats()) {
			refused(schema, index, good, "STATS." + block + ".NAME", null);
			refused(schema, index, good, "STATS." + block + ".DESC", null);
		}
		clean(schema, index, good, "STATS.0.NAME", null);
		refused(schema, index, good, "ITEMS.0.STATS", "0,0,0,0,0,0");
		refused(schema, index, good, "SERVICE.STANDING.CITIZEN", "1000001");
		clean(schema, index, good, "SERVICE.STANDING.CITIZEN", "1000000");
		refused(schema, index, good, "SERVICE.STANDING.CITIZEN", "-1");
		refused(schema, index, good, "SERVICE.STANDING.EXPONENT", "0");
		clean(schema, index, good, "SERVICE.STANDING.EXPONENT", "0.01");
		refused(schema, index, good, "SERVICE.STANDING.CHILD", "yes");
		clean(schema, index, good, "SERVICE.STANDING.CHILD", "true");
		refused(schema, index, good, "SERVICE.USAGE", "1.5");
		clean(schema, index, good, "SERVICE.USAGE", "0.125");
		refused(schema, index, good, "SERVICE.BOOST.HAPPINESS", "1");
		clean(schema, index, good, "SERVICE.BOOST.HAPPINESS>ADD", "1");
		refused(schema, index, good, "SERVICE.RADIUS", "-1");
		warned(schema, index, good, "SERVICE.RADIUS", "50001");
		clean(schema, index, good, "SERVICE.MORE", "Just one line.");
		refused(schema, index, good, "WORK.TITLE", null);
		Map<String, String> noWork = new LinkedHashMap<>(good);
		noWork.remove("WORK.SHIFT_OFFSET");
		noWork.remove("WORK.TITLE");
		noWork.remove("WORK.VERB");
		List<AssetChecker.Finding> withoutWork = AssetChecker.check(schema, index, noWork);
		if (room.workRequired()) {
			expect("a " + lower + " without WORK is refused: " + withoutWork, withoutWork.stream().anyMatch(AssetChecker.Finding::blocksSaving));
		} else {
			expect("a " + lower + " without WORK only warns: " + withoutWork, !withoutWork.isEmpty() && withoutWork.stream().noneMatch(AssetChecker.Finding::blocksSaving));
		}
		if (label.equals("Canteen")) {
			refused(schema, index, good, "INDUSTRY.IN.WOOD", null);
			refused(schema, index, good, "INDUSTRY.IN.WOOD", "10001");
			refused(schema, index, good, "INDUSTRY.OUT.MEAT.PLAYER", "1");
		}

		// A new one, read back with the game's own reads at the paths the loader uses.
		ModEntry mod = ModCreator.create(mods, game, label + " Creation");
		Path root = mod.root();
		Path modVersion = AssetCreator.versionFolder(root, version);
		Path assets = modVersion.resolve("assets");
		GameAssetIndex modIndex = GameAssetIndex.load(game, modVersion, parser);
		expect("a new " + label + " starts with the game's sprites", AssetForm.startingValues(schema, modIndex).containsKey("SPRITES." + room.sprites().get(0) + ".0.FRAMES"));
		AssetCreator.Created made = AssetCreator.create(root, entry.type(), schema, "TEST_ROOM", good, parser, modIndex);
		expect(label + "'s file has the prefix the game needs", made.dataFile().equals(assets.resolve("init/room/" + room.prefix() + "TEST_ROOM.txt")));
		String mono = Files.readString(assets.resolve("audio/config/mono/" + room.prefix() + "TEST_ROOM.txt"));
		expect("its service sound is written: " + mono, mono.contains("ROOM_SERVICE_" + room.prefix() + "TEST_ROOM: DUMMY"));
		List<AssetChecker.Finding> findings = AssetVanillaCheck.check(schema, modIndex, made.dataFile(), parser);
		expect("the new " + label + " passes the vanilla check: " + findings, findings.isEmpty());
		List<AssetChecker.Finding> textFindings = AssetVanillaCheck.checkText(schema, made.textFile(), parser);
		expect("its text passes too: " + textFindings, textFindings.isEmpty());
		GameParser.Node node = parser.root(made.dataFile());
		GameParser.Node service = node.block("SERVICE");
		expect("the game reads the standing", service.block("STANDING").decimal("CITIZEN", 0, 1000000) == 0.75);
		expect("and its priority", service.block("STANDING").decimalOr("PRIO", 0, 100000, 1) == 3);
		if (label.equals("Canteen")) {
			expect("the game reads the fuel", node.block("INDUSTRY").block("IN").decimal("WOOD", 0, 10000) == 1);
		}
		GameParser.Node text = parser.root(made.textFile());
		expect("the text has the service verb", "Having a bite".equals(text.block("SERVICE").text("VERB")));
		expect("and what subjects say", List.of("We want more.", "Much more.").equals(List.of(text.block("SERVICE").texts("MORE"))));
		expect("and every stat block", text.blocks("STATS").size() == room.stats());
		for (int block : room.namedStats()) {
			expect("and the game can read block " + block, ("Stat " + block).equals(text.blocks("STATS").get(block).text("NAME")));
		}
		expect("a vanilla name is refused", AssetCreator.problemWithName(entry.type(), "NORMAL", modIndex) != null);
		SoundEntries sounds = SoundEntries.load(game, modVersion, parser);
		expect("the mod's sound check finds nothing missing: " + sounds.missingIn(modVersion, parser), sounds.missingIn(modVersion, parser).isEmpty());
		expect("and would report a missing service sound", sounds.missingForRoom(room.prefix() + "NOPE", true).contains("ROOM_SERVICE_" + room.prefix() + "NOPE"));
	}

	/**
	 * A Temple or a Shrine, each checked by hand against its ROOM_ class, its Constructor and RoomService.
	 *
	 * @param namedStats the STATS blocks whose words the game reads
	 * @param inner each class after the package that handles the file, besides the ROOM_ class
	 * @param changes a full class name, a key in it, and what to rename the key to
	 */
	private record SpiritRoom(String label, String prefix, String pack, String roomClass, List<String> tags, List<String> sprites, int items, int stats, int namedSlots,
			List<Integer> namedStats, List<String> inner, boolean work, String[][] changes) {
	}

	private static final List<SpiritRoom> SPIRIT_ROOMS = List.of(
			new SpiritRoom("Temple", "TEMPLE_", "settlement/room/spirit/temple/", "ROOM_TEMPLE",
					List.of("ICON", "RELIGION", "SACRIFICE_TYPE", "SACRIFICE_RESOURCE", "SACRIFICE_TIME", "RESOURCES", "AREA_COSTS", "FLOOR", "FLOOR_PATH", "MINI_COLOR", "MINI_COLOR_PATTERN",
							"WORK", "ITEMS", "SPRITES", "SERVICE", "UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE"),
					List.of("PEDISTAL_BOX", "ALTAR_BOX", "EMBLEM_1X1", "EMBLEM_2X2", "NICHE_A_1X1", "NICHE_B_1X1", "NICHE_C_1X1", "NICHE_D_1X1", "TORCH_1X1", "COFFIN_A_1X1"),
					5, 5, 5, List.of(2, 3, 4), List.of("TempleConstructor", "TempleConstructor$3", "TempleConstructor$4", "TempleConstructor$5"), true,
					new String[][] { { "settlement/room/spirit/temple/ROOM_TEMPLE", "SACRIFICE_TIME", "SACRIFICE_TIMX" }, { "settlement/room/spirit/temple/TempleConstructor", "FLOOR_PATH", "FLOOR_PATX" },
							{ "settlement/room/spirit/temple/TempleConstructor", "COFFIN_A_1X1", "COFFIN_A_1X2" }, { "settlement/room/spirit/temple/TempleConstructor$5", "EMBLEM_1X1", "EMBLEM_1X2" },
							{ "settlement/room/service/module/RoomService", "RADIUS", "RADIUX" } }),
			new SpiritRoom("Shrine", "SHRINE_", "settlement/room/spirit/shrine/", "ROOM_SHRINE",
					List.of("ICON", "RELIGION", "RESOURCES", "AREA_COSTS", "FLOOR", "MINI_COLOR", "MINI_COLOR_PATTERN", "ITEMS", "SPRITES", "SERVICE", "UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE"),
					List.of("ALTAR_FLOOR_TEXTURE", "ALTAR_FLOOR_SCRIBBLE1_TEXTURE", "ALTAR_FLOOR_SCRIBBLE2_TEXTURE", "INNER_BOX", "ALTAR_BOX", "EMBLEM_1X1", "EMBLEM_2X2", "TORCH_1X1"),
					1, 1, 1, List.of(0), List.of("Constructor", "Constructor$1", "Constructor$2", "Constructor$3", "Constructor$4", "Constructor$5", "Constructor$6", "Constructor$SScribbleAltar", "Constructor$SStairs"), false,
					new String[][] { { "settlement/room/spirit/shrine/Constructor", "ALTAR_FLOOR_TEXTURE", "ALTAR_FLOOR_TEXTURX" }, { "settlement/room/spirit/shrine/Constructor$SStairs", "INNER_BOX", "INNER_BOY" },
							{ "settlement/room/service/module/RoomService", "VERB", "VERX" } }));

	private static List<String> spiritRoomClasses(SpiritRoom room) {
		List<String> classes = new java.util.ArrayList<>(FARM_CLASSES);
		for (String gone : List.of("settlement/room/food/farm/ROOM_FARM", "settlement/room/food/farm/Constructor", "settlement/room/industry/module/Industry", "init/type/CLIMATES", "settlement/room/main/BonusExperience$RoomExperienceBonus")) {
			classes.remove(gone);
		}
		classes.add(room.pack() + room.roomClass());
		room.inner().forEach(inner -> classes.add(room.pack() + inner));
		classes.addAll(List.of("settlement/room/service/module/RoomService", "settlement/room/sprite/RoomSpriteImp", "settlement/room/sprite/RoomSprite1x1", "settlement/room/sprite/RoomSprite1xN",
				"settlement/room/sprite/RoomSpriteBoxN", "settlement/room/sprite/RoomSpriteXxX", "init/sprite/game/Sheets", "init/sprite/game/GameSheets", "init/sprite/game/SheetData"));
		if (room.label().equals("Shrine")) {
			classes.add("settlement/room/sprite/RoomSpriteTex");
		}
		return classes;
	}

	private static String frameFor(String sprite) {
		return sprite.endsWith("_BOX") ? "ALTAR: 1" : sprite.endsWith("_TEXTURE") ? "DECOR: 1" : sprite.endsWith("2X2") ? "HUT: 0" : "STORAGE: 6";
	}

	private static Map<String, String> validSpiritRoom(SpiritRoom room) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("ICON", "24->resource->Gem->0");
		values.put("RELIGION", "ATHURI");
		values.put("RESOURCES", "WOOD");
		values.put("AREA_COSTS", "0");
		values.put("MINI_COLOR", "10_20_30");
		if (room.work()) {
			values.put("SACRIFICE_TYPE", "RESOURCE");
			values.put("SACRIFICE_RESOURCE", "STONE");
			values.put("SACRIFICE_TIME", "1");
			values.put("FLOOR_PATH", "STONE_CROSS");
			values.put("WORK.SHIFT_OFFSET", "0.5");
			values.put("WORK.TITLE", "Priest");
			values.put("WORK.VERB", "praying");
		}
		String stats = String.join(",", java.util.Collections.nCopies(room.stats(), "0"));
		for (int i = 0; i < room.items(); i++) {
			values.put("ITEMS." + i + ".COSTS", "1");
			values.put("ITEMS." + i + ".STATS", stats);
			values.put("ITEMS." + i + ".NAME", "Item " + i);
			values.put("ITEMS." + i + ".DESC", "About item " + i);
		}
		for (String sprite : room.sprites()) {
			values.put("SPRITES." + sprite + ".0.FRAMES", frameFor(sprite));
		}
		for (int block : room.namedStats()) {
			values.put("STATS." + block + ".NAME", "Stat " + block);
			values.put("STATS." + block + ".DESC", "About stat " + block);
		}
		values.put("SERVICE.VERB", "Worshipping");
		if (room.work()) {
			values.put("SOUND.ROOM_WORK_", "DUMMY");
		}
		values.put("SOUND.ROOM_CLICK_", "DUMMY");
		values.put("SOUND.ROOM_", "DUMMY");
		values.put("SOUND.ROOM_SERVICE_", "DUMMY");
		values.put("NAME", "Test " + room.label());
		values.put("DESC", "It's for testing.");
		return values;
	}

	/** The tables, the gate, all four vanilla files, the rules and a new asset read back, for a Temple or a Shrine. */
	private static void spiritRoom(SpiritRoom room, List<AssetCatalog.Entry> entries, Path game, Path mods, GameParser parser, GameParser.GameVersion version, GameAssetIndex index, Path temp) throws Exception {
		String label = room.label();
		AssetCatalog.Entry entry = entryNamed(entries, label);
		expect(label + " is available: " + entry.unavailableReason(), entry.available());
		if (!entry.available()) {
			return;
		}
		AssetSchema schema = entry.schema();
		expect(label + "'s files start with its prefix", entry.type().fileName("X").equals(room.prefix() + "X"));
		expect(label + "'s tags: " + schema.dataTags().stream().map(TagSpec::key).toList(), schema.dataTags().stream().map(TagSpec::key).toList().equals(room.tags()));
		for (String unread : List.of("BONUS", "EXPERIENCE_BONUS", "STORAGE", "EMPLOYMENT", "REQUIRES", "SERVICE.SOUND")) {
			expect(label + " doesn't offer " + unread, schema.find(unread) == null);
		}
		expectNested(schema, "RELIGION", TagSpec.Kind.KEY, true, null, null, null);
		expectNested(schema, "SERVICE.RADIUS", TagSpec.Kind.INT, false, "150", 0.0, 50000.0);
		expectNested(schema, "SERVICE.VERB", TagSpec.Kind.TEXT_LINE, true, null, null, null);
		expect(label + " has " + room.items() + " items", schema.tag("ITEMS").shape().fixedCount() == room.items());
		TagSpec stats = schema.textTags().stream().filter(t -> t.key().equals("STATS")).findFirst().orElseThrow();
		expect(label + "'s stat blocks: " + stats.shape().fixedCount() + ", " + stats.shape().namedSlots(), stats.shape().fixedCount() == room.stats() && stats.shape().namedSlots() == room.namedSlots());
		expect(label + "'s sprites", schema.tag("SPRITES").shape().children().stream().map(TagSpec::key).toList().equals(room.sprites()));
		for (String sprite : room.sprites()) {
			String folder = sprite.endsWith("_BOX") ? "sprite/game/box/" : sprite.endsWith("_TEXTURE") ? "sprite/game/texture/" : sprite.endsWith("2X2") ? "sprite/game/2x2/" : "sprite/game/1x1/";
			expect(sprite + " looks in " + folder, schema.find("SPRITES." + sprite + ".FRAMES").sprite().folder().equals(folder));
		}
		expect(label + " looks up its service sound", schema.sounds().stream().anyMatch(slot -> slot.prefix().equals("ROOM_SERVICE_")));
		expect(label + " plays a work sound only if it has workers", schema.sounds().stream().anyMatch(slot -> slot.prefix().equals("ROOM_WORK_")) == room.work());
		expect(label + " starts its sprites from Athuri's", schema.starter().file().equals("init/room/" + room.prefix() + "ATHURI.txt"));
		expect("box and texture sheets have their sizes: " + index.sheetRows("sprite/game/box/", "ALTAR") + " " + index.sheetRows("sprite/game/texture/", "DECOR"),
				index.sheetRows("sprite/game/box/", "ALTAR").validSize() && index.sheetRows("sprite/game/texture/", "DECOR").validSize() && index.sheetRows("sprite/game/texture/", "DECOR").rows() > 0);

		List<String> classes = spiritRoomClasses(room);
		String lower = label.toLowerCase(java.util.Locale.ROOT);
		AssetCatalog.Entry control = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve(lower + "-all.jar"), classes)), label);
		expect("these classes are enough for " + label + ": " + control.unavailableReason(), control.available());
		int at = 0;
		for (String gone : classes) {
			AssetCatalog.Entry without = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve(lower + "-without-" + at++ + ".jar"), classes.stream().filter(n -> !n.equals(gone)).toList())), label);
			expect("without " + gone + ", " + label + " is disabled: " + without.unavailableReason(), !without.available() && without.unavailableReason() != null);
		}
		for (String[] change : room.changes()) {
			AssetCatalog.Entry changed = entryNamed(AssetCatalog.load(patchedJar(game, temp.resolve(lower + "-changed-" + change[1] + ".jar"), classes, change[0], change[1], change[2])), label);
			expect("a changed " + change[1] + " turns " + label + " off: " + changed.unavailableReason(), !changed.available() && changed.unavailableReason() != null);
		}

		// All four vanilla files.
		Path folder = temp.resolve("vanilla-" + lower);
		Files.createDirectories(folder.resolve("data"));
		Files.createDirectories(folder.resolve("text"));
		List<String> known = new java.util.ArrayList<>(schema.textTags().stream().map(TagSpec::key).toList());
		// INFO holds the name. A temple's text RELIGION block isn't read by anything, which a search of the code found.
		known.addAll(List.of("INFO", "RELIGION"));
		int data = 0;
		int texts = 0;
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile())) {
			Enumeration<? extends ZipEntry> files = zip.entries();
			while (files.hasMoreElements()) {
				ZipEntry file = files.nextElement();
				String name = file.getName().substring(file.getName().lastIndexOf('/') + 1);
				boolean isData = file.getName().matches("data/assets/init/room/" + room.prefix() + "[^/]+\\.txt");
				if (!isData && !file.getName().matches("data/assets/text/room/" + room.prefix() + "[^/]+\\.txt")) {
					continue;
				}
				Path copy = folder.resolve(isData ? "data" : "text").resolve(name);
				try (InputStream in = zip.getInputStream(file)) {
					Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
				}
				if (isData) {
					data++;
					List<AssetChecker.Finding> findings = new java.util.ArrayList<>(AssetVanillaCheck.check(schema, index, copy, parser));
					expect(name + " has no errors or warnings: " + findings, findings.isEmpty());
				} else {
					texts++;
					List<String> keys = parser.tags(copy).tags().stream().map(GameParser.TagValue::key).toList();
					expect(name + " text uses only known tags: " + keys, known.containsAll(keys));
					List<AssetChecker.Finding> findings = AssetVanillaCheck.checkText(schema, copy, parser);
					expect(name + " text reads as the schema says: " + findings, findings.isEmpty());
				}
			}
		}
		expect("all 4 vanilla " + lower + "s were checked: " + data, data == 4);
		expect("their text files were checked: " + texts, texts == 4);

		// The rules.
		Map<String, String> good = validSpiritRoom(room);
		expect("a good " + label + " has no problems: " + AssetChecker.check(schema, index, good), AssetChecker.check(schema, index, good).isEmpty());
		for (String key : List.of("ICON", "RELIGION", "MINI_COLOR", "ITEMS.0.COSTS", "NAME", "DESC", "ITEMS.0.NAME", "SPRITES." + room.sprites().get(0) + ".0.FRAMES", "SERVICE.VERB", "SOUND.ROOM_SERVICE_")) {
			refused(schema, index, good, key, null);
		}
		for (int block : room.namedStats()) {
			refused(schema, index, good, "STATS." + block + ".NAME", null);
		}
		refused(schema, index, good, "RELIGION", "NOT_A_RELIGION");
		refused(schema, index, good, "ITEMS.0.STATS", "0,0,0,0,0,0");
		refused(schema, index, good, "SERVICE.RADIUS", "-1");
		clean(schema, index, good, "SERVICE.RADIUS", "50000");
		warned(schema, index, good, "SERVICE.RADIUS", "50001");
		refused(schema, index, good, "SPRITES." + room.sprites().get(0) + ".0.FRAMES", "TORCH: 0,");
		if (!room.work()) {
			// SHRINE_AMINION's item description is blank, and the game reads it as it is. Left empty, it's written blank.
			clean(schema, index, good, "ITEMS.0.DESC", null);
		}
		if (room.work()) {
			clean(schema, index, good, "STATS.0.NAME", null);
			refused(schema, index, good, "SACRIFICE_TYPE", "BLOOD");
			clean(schema, index, good, "SACRIFICE_TYPE", "ANIMAL");
			Map<String, String> human = new LinkedHashMap<>(good);
			human.put("SACRIFICE_TYPE", "HUMAN");
			human.remove("SACRIFICE_RESOURCE");
			expect("a human sacrifice needs no resource: " + AssetChecker.check(schema, index, human), AssetChecker.check(schema, index, human).isEmpty());
			refused(schema, index, good, "SACRIFICE_RESOURCE", null);
			refused(schema, index, good, "SACRIFICE_TIME", "10.5");
			clean(schema, index, good, "SACRIFICE_TIME", "0");
			refused(schema, index, good, "FLOOR_PATH", null);
			refused(schema, index, good, "WORK.TITLE", null);
			Map<String, String> noWork = new LinkedHashMap<>(good);
			noWork.remove("WORK.SHIFT_OFFSET");
			noWork.remove("WORK.TITLE");
			noWork.remove("WORK.VERB");
			List<AssetChecker.Finding> withoutWork = AssetChecker.check(schema, index, noWork);
			expect("a temple without WORK only warns: " + withoutWork, !withoutWork.isEmpty() && withoutWork.stream().noneMatch(AssetChecker.Finding::blocksSaving));
		}

		// A new one, read back with the game's own reads.
		ModEntry mod = ModCreator.create(mods, game, label + " Creation");
		Path root = mod.root();
		Path modVersion = AssetCreator.versionFolder(root, version);
		Path assets = modVersion.resolve("assets");
		GameAssetIndex modIndex = GameAssetIndex.load(game, modVersion, parser);
		expect("a new " + label + " starts with Athuri's sprites", AssetForm.startingValues(schema, modIndex).containsKey("SPRITES." + room.sprites().get(0) + ".0.FRAMES"));
		Map<String, String> creating = new LinkedHashMap<>(good);
		if (!room.work()) {
			creating.remove("ITEMS.0.DESC");
		}
		AssetCreator.Created made = AssetCreator.create(root, entry.type(), schema, "TEST_GOD", creating, parser, modIndex);
		expect(label + "'s file has the prefix the game needs", made.dataFile().equals(assets.resolve("init/room/" + room.prefix() + "TEST_GOD.txt")));
		String mono = Files.readString(assets.resolve("audio/config/mono/" + room.prefix() + "TEST_GOD.txt"));
		expect("its service sound is written: " + mono, mono.contains("ROOM_SERVICE_" + room.prefix() + "TEST_GOD: DUMMY"));
		List<AssetChecker.Finding> findings = AssetVanillaCheck.check(schema, modIndex, made.dataFile(), parser);
		expect("the new " + label + " passes the vanilla check: " + findings, findings.isEmpty());
		List<AssetChecker.Finding> textFindings = AssetVanillaCheck.checkText(schema, made.textFile(), parser);
		expect("its text passes too: " + textFindings, textFindings.isEmpty());
		GameParser.Node node = parser.root(made.dataFile());
		expect("the game reads its religion", "ATHURI".equals(node.value("RELIGION")));
		expect("the SERVICE block the game needs is there, though empty", node.has("SERVICE"));
		GameParser.Node text = parser.root(made.textFile());
		expect("the text has the service verb", "Worshipping".equals(text.block("SERVICE").text("VERB")));
		expect("and every stat block", text.blocks("STATS").size() == room.stats());
		if (!room.work()) {
			expect("an item description left empty is written blank, which the game reads", "".equals(text.blocks("ITEMS").get(0).text("DESC")));
		}
		for (int block : room.namedStats()) {
			expect("and the game can read block " + block, ("Stat " + block).equals(text.blocks("STATS").get(block).text("NAME")));
		}
		expect("a vanilla name is refused", AssetCreator.problemWithName(entry.type(), "ATHURI", modIndex) != null);
		SoundEntries sounds = SoundEntries.load(game, modVersion, parser);
		expect("the mod's sound check finds nothing missing: " + sounds.missingIn(modVersion, parser), sounds.missingIn(modVersion, parser).isEmpty());
		expect("and would report a missing service sound", sounds.missingForRoom(room.prefix() + "NOPE", room.work()).contains("ROOM_SERVICE_" + room.prefix() + "NOPE"));
		expect("but not for a room type nobody checked yet", !sounds.missingForRoom("SCHOOL_NOPE", false).contains("ROOM_SERVICE_SCHOOL_NOPE"));
	}

	/**
	 * What differs between a Fishery, an Orchard and a Hunter. Each was checked by hand against its own ROOM_ class
	 * and Constructor.
	 *
	 * @param outKey where the first output's PLAYER rate is, for a recipe producing MEAT
	 * @param namedStats the STATS blocks whose words the game reads
	 * @param inner each anonymous class that handles the file
	 * @param changes a class's name after the package, a key in it, and what to rename the key to
	 * @param warned a data tag vanilla writes and nothing reads
	 */
	private record FoodRoom(String label, String prefix, String vanilla, String folder, String roomClass, List<String> tags, List<String> sprites, int stats, int namedSlots,
			List<Integer> namedStats, String outKey, List<String> inner, boolean experience, boolean big, String[][] changes, String warned) {
	}

	private static final List<FoodRoom> FOOD_ROOMS = List.of(
			new FoodRoom("Fishery", "FISHERY_", "NORMAL", "fish", "ROOM_FISHERY",
					List.of("ICON", "INDUSTRY", "RESOURCES", "AREA_COSTS", "FLOOR", "MINI_COLOR", "MINI_COLOR_PATTERN", "WORK", "ITEMS", "SPRITES", "BONUS", "EXPERIENCE_BONUS", "UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE"),
					List.of("STORAGE_BOTTOM_1X1", "STORAGE_TOP_1X1", "MISC_TOP_1X1", "CANDLE_1X1", "AUX_EDGE_1X1", "AUX_MID_1X1", "AUX_BIG_2X2", "WORKTABLE_1X1", "MISC_1X1", "EDGE_COMBO", "MISC_GROUND_1X1"),
					5, 2, List.of(0, 1), "INDUSTRY.OUT.MEAT.PLAYER", List.of("$7", "$8", "$9", "$10"), true, true,
					new String[][] { { "ROOM_FISHERY", "FISHERY", "FISHERX" }, { "Constructor", "SPRITES", "SPRITEX" }, { "Constructor", "MISC_TOP_1X1", "MISC_TOP_1X2" }, { "Constructor", "AUX_BIG_2X2", "AUX_BIG_2X3" }, { "Constructor$7", "STORAGE_TOP_1X1", "STORAGE_TOP_1X2" } }, "STORAGE"),
			new FoodRoom("Orchard", "ORCHARD_", "FRUIT", "orchard", "ROOM_ORCHARD",
					List.of("ICON", "INDOORS", "EXTRA_RESOURCE", "EXTRA_RESOURCE_AMOUNT", "DAYS_TILL_GROWTH", "RIPE_AT_PART_OF_YEAR", "INDUSTRY", "RESOURCES", "AREA_COSTS", "FLOOR", "MINI_COLOR", "MINI_COLOR_PATTERN", "WORK", "ITEMS", "SPRITES", "BONUS", "EXPERIENCE_BONUS", "UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE"),
					List.of("FRUIT_1X1", "TREE_1X1", "TREE_2X2", "EDGE_COMBO", "STORAGE_1X1"),
					4, 2, List.of(0, 1), "INDUSTRY.OUT.MEAT.PLAYER", List.of("$6", "$7"), true, true,
					new String[][] { { "ROOM_ORCHARD", "EXTRA_RESOURCE_AMOUNT", "EXTRA_RESOURCE_AMOUNX" }, { "ROOM_ORCHARD$Time", "DAYS_TILL_GROWTH", "DAYS_TILL_GROWTX" }, { "Constructor", "INDOORS", "INDOORX" }, { "Constructor", "TREE_2X2", "TREE_2X3" } }, null),
			new FoodRoom("Hunter", "HUNTER_", "NORMAL", "hunter", "ROOM_HUNTER",
					List.of("ICON", "MAX_EMPLOYED", "INDUSTRIES", "RESOURCES", "AREA_COSTS", "FLOOR", "MINI_COLOR", "MINI_COLOR_PATTERN", "WORK", "ITEMS", "SPRITES", "BONUS", "UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE"),
					List.of("TABLE_COMBO", "NICKNACK_1X1", "STORAGE_1X1"),
					3, 3, List.of(0, 2), "INDUSTRIES.0.INDUSTRY.OUT.MEAT.PLAYER", List.of("$3", "$5"), false, false,
					new String[][] { { "ROOM_HUNTER", "MAX_EMPLOYED", "MAX_EMPLOYEX" }, { "Constructor", "NICKNACK_1X1", "NICKNACK_1X2" }, { "Constructor", "TABLE_COMBO", "TABLE_COMBX" } }, null));

	private static List<String> foodRoomClasses(FoodRoom room) {
		List<String> classes = new java.util.ArrayList<>(FARM_CLASSES);
		classes.remove("settlement/room/food/farm/ROOM_FARM");
		classes.remove("settlement/room/food/farm/Constructor");
		if (!room.experience()) {
			classes.remove("settlement/room/main/BonusExperience$RoomExperienceBonus");
		}
		String pack = "settlement/room/food/" + room.folder() + "/";
		classes.add(pack + room.roomClass());
		classes.add(pack + "Constructor");
		if (room.label().equals("Orchard")) {
			classes.add(pack + "ROOM_ORCHARD$Time");
			classes.add("settlement/tilemap/terrain/TForest$Sprites");
		}
		room.inner().forEach(inner -> classes.add(pack + "Constructor" + inner));
		classes.addAll(List.of("settlement/room/sprite/RoomSpriteImp", "settlement/room/sprite/RoomSprite1x1", "settlement/room/sprite/RoomSprite1xN", "settlement/room/sprite/RoomSpriteCombo",
				"init/sprite/game/Sheets", "init/sprite/game/GameSheets", "init/sprite/game/SheetData"));
		if (room.big()) {
			classes.add("settlement/room/sprite/RoomSpriteXxX");
		}
		return classes;
	}

	private static Map<String, String> validFoodRoom(FoodRoom room) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("ICON", "24->resource->Gem->0");
		values.put("RESOURCES", "WOOD");
		values.put("AREA_COSTS", "0");
		values.put("MINI_COLOR", "10_20_30");
		values.put("WORK.SHIFT_OFFSET", "0.125");
		values.put(room.outKey(), "1");
		if (room.label().equals("Orchard")) {
			values.put("INDOORS", "false");
			values.put("EXTRA_RESOURCE", "WOOD");
			values.put("EXTRA_RESOURCE_AMOUNT", "8");
			values.put("DAYS_TILL_GROWTH", "64");
			values.put("RIPE_AT_PART_OF_YEAR", "0.6");
		}
		if (room.label().equals("Hunter")) {
			values.put("MAX_EMPLOYED", "15");
		}
		String stats = String.join(",", java.util.Collections.nCopies(room.stats(), "0"));
		for (int i = 0; i < 2; i++) {
			values.put("ITEMS." + i + ".COSTS", "1");
			values.put("ITEMS." + i + ".STATS", stats);
			values.put("ITEMS." + i + ".NAME", "Item " + i);
			values.put("ITEMS." + i + ".DESC", "About item " + i);
		}
		for (String sprite : room.sprites()) {
			values.put("SPRITES." + sprite + ".0.FRAMES", sprite.endsWith("COMBO") ? "COMBO_TABLES: 6" : sprite.endsWith("2X2") ? "HUT: 0" : "STORAGE: 6");
		}
		for (int block : room.namedStats()) {
			values.put("STATS." + block + ".NAME", "Stat " + block);
			values.put("STATS." + block + ".DESC", "About stat " + block);
		}
		values.put("SOUND.ROOM_WORK_", "DUMMY");
		values.put("SOUND.ROOM_CLICK_", "DUMMY");
		values.put("SOUND.ROOM_", "DUMMY");
		values.put("NAME", "Test " + room.label());
		values.put("DESC", "It's for testing.");
		values.put("WORK.TITLE", "Tester");
		values.put("WORK.VERB", "testing");
		return values;
	}

	/** The tables, the gate, the vanilla file, the rules and a new asset read back, for one of the three. */
	private static void foodRoom(FoodRoom room, List<AssetCatalog.Entry> entries, Path game, Path mods, GameParser parser, GameParser.GameVersion version, GameAssetIndex index, Path temp) throws Exception {
		String label = room.label();
		AssetCatalog.Entry entry = entryNamed(entries, label);
		expect(label + " is available: " + entry.unavailableReason(), entry.available());
		if (!entry.available()) {
			return;
		}
		AssetSchema schema = entry.schema();
		expect(label + "'s files start with its prefix", entry.type().fileName("X").equals(room.prefix() + "X"));
		expect(label + "'s tags: " + schema.dataTags().stream().map(TagSpec::key).toList(), schema.dataTags().stream().map(TagSpec::key).toList().equals(room.tags()));
		for (String unread : List.of("STORAGE", "WORK.SOUND", "REQUIRES", "EMPLOYMENT")) {
			expect(label + " doesn't offer " + unread, schema.find(unread) == null);
		}
		expect(label + " has exactly 2 items", schema.tag("ITEMS").shape().fixedCount() == 2);
		TagSpec stats = schema.textTags().stream().filter(t -> t.key().equals("STATS")).findFirst().orElseThrow();
		expect(label + "'s stat blocks: " + stats.shape().fixedCount() + ", " + stats.shape().namedSlots(), stats.shape().fixedCount() == room.stats() && stats.shape().namedSlots() == room.namedSlots());
		expect(label + "'s sprites", schema.tag("SPRITES").shape().children().stream().map(TagSpec::key).toList().equals(room.sprites()));
		for (String sprite : room.sprites()) {
			expectNested(schema, "SPRITES." + sprite, TagSpec.Kind.BLOCK_LIST, true, null, null, null);
			expect(sprite + " looks in the right folder", schema.find("SPRITES." + sprite + ".FRAMES").sprite().folder().equals(sprite.endsWith("COMBO") ? "sprite/game/combo/" : sprite.endsWith("2X2") ? "sprite/game/2x2/" : "sprite/game/1x1/"));
		}
		expectNested(schema, room.outKey().replace(".MEAT.PLAYER", ""), TagSpec.Kind.KEYED_ROWS, true, null, null, null);
		expect(label + " starts its sprites from the game's only one", schema.starter().file().equals("init/room/" + room.prefix() + room.vanilla() + ".txt"));
		expect(label + "'s sounds start from it too: " + schema.sounds().stream().map(SoundEntries.Slot::likeKey).toList(), schema.sounds().stream().map(SoundEntries.Slot::likeKey).toList().equals(List.of(
				"ROOM_WORK_" + room.prefix() + room.vanilla(), "ROOM_CLICK_" + room.prefix() + room.vanilla(), "ROOM_" + room.prefix() + room.vanilla())));

		// The gate: every class matters, and so does a changed key.
		List<String> classes = foodRoomClasses(room);
		String lower = label.toLowerCase(java.util.Locale.ROOT);
		AssetCatalog.Entry control = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve(lower + "-all.jar"), classes)), label);
		expect("these classes are enough for " + label + ": " + control.unavailableReason(), control.available());
		int at = 0;
		for (String gone : classes) {
			AssetCatalog.Entry without = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve(lower + "-without-" + at++ + ".jar"), classes.stream().filter(n -> !n.equals(gone)).toList())), label);
			expect("without " + gone + ", " + label + " is disabled: " + without.unavailableReason(), !without.available() && without.unavailableReason() != null);
		}
		String pack = "settlement/room/food/" + room.folder() + "/";
		for (String[] change : room.changes()) {
			AssetCatalog.Entry changed = entryNamed(AssetCatalog.load(patchedJar(game, temp.resolve(lower + "-changed-" + change[1] + ".jar"), classes, pack + change[0], change[1], change[2])), label);
			expect("a changed " + change[1] + " turns " + label + " off: " + changed.unavailableReason(), !changed.available() && changed.unavailableReason() != null);
		}

		// The one vanilla file, with zero errors and only justified warnings.
		Path folder = temp.resolve("vanilla-" + lower);
		Files.createDirectories(folder.resolve("data"));
		Files.createDirectories(folder.resolve("text"));
		String file = room.prefix() + room.vanilla() + ".txt";
		// The text file's BONUS block isn't read (RoomBlueprintIns.java:118-120).
		List<String> known = new java.util.ArrayList<>(schema.textTags().stream().map(TagSpec::key).toList());
		known.addAll(List.of("INFO", "BONUS"));
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile())) {
			Path data = folder.resolve("data").resolve(file);
			Path words = folder.resolve("text").resolve(file);
			try (InputStream in = zip.getInputStream(zip.getEntry("data/assets/init/room/" + file))) {
				Files.copy(in, data, StandardCopyOption.REPLACE_EXISTING);
			}
			try (InputStream in = zip.getInputStream(zip.getEntry("data/assets/text/room/" + file))) {
				Files.copy(in, words, StandardCopyOption.REPLACE_EXISTING);
			}
			List<AssetChecker.Finding> findings = new java.util.ArrayList<>(AssetVanillaCheck.check(schema, index, data, parser));
			if (room.warned() != null) {
				expect(file + " warns that " + room.warned() + " is unread", findings.removeIf(f -> room.warned().equals(f.tag()) && f.severity() == AssetChecker.Severity.WARNING));
			}
			// HUNTER_NORMAL writes three sprites as one block, which RoomSpriteImp also reads (RoomSpriteImp.java:27-37). The check reads it as the first level, as the editor does.
			if (label.equals("Hunter")) {
				expect(file + " has its one-block sprites read, not kept", findings.stream().noneMatch(f -> f.message().contains("one block")));
			}
			expect(file + " has no other errors or warnings: " + findings, findings.isEmpty());
			List<String> keys = parser.tags(words).tags().stream().map(GameParser.TagValue::key).toList();
			expect(file + " text uses only known tags: " + keys, known.containsAll(keys));
			List<AssetChecker.Finding> textFindings = AssetVanillaCheck.checkText(schema, words, parser);
			expect(file + " text reads as the schema says: " + textFindings, textFindings.isEmpty());
		}

		// The rules.
		Map<String, String> good = validFoodRoom(room);
		expect("a good " + label + " has no problems: " + AssetChecker.check(schema, index, good), AssetChecker.check(schema, index, good).isEmpty());
		for (String key : List.of("ICON", "MINI_COLOR", "WORK.SHIFT_OFFSET", room.outKey(), "ITEMS.0.COSTS", "ITEMS.1.STATS", "NAME", "DESC", "WORK.TITLE", "ITEMS.0.NAME", "ITEMS.1.DESC", "SPRITES." + room.sprites().get(0) + ".0.FRAMES", "SOUND.ROOM_WORK_")) {
			refused(schema, index, good, key, null);
		}
		for (int block : room.namedStats()) {
			refused(schema, index, good, "STATS." + block + ".NAME", null);
			refused(schema, index, good, "STATS." + block + ".DESC", null);
		}
		refused(schema, index, good, room.outKey(), "100000.5");
		refused(schema, index, good, "ITEMS.0.COSTS", "1,2");
		refused(schema, index, good, "ITEMS.1.STATS", "0");
		clean(schema, index, good, "FLOOR", null);
		clean(schema, index, good, "DEGRADE_RATE", "0");
		refused(schema, index, good, "DEGRADE_RATE", "1.5");

		// A new one, read back with the game's own reads at the paths the loader uses.
		ModEntry mod = ModCreator.create(mods, game, label + " Creation");
		Path root = mod.root();
		Path modVersion = AssetCreator.versionFolder(root, version);
		Path assets = modVersion.resolve("assets");
		GameAssetIndex modIndex = GameAssetIndex.load(game, modVersion, parser);
		expect("a new " + label + " starts with the game's sprites", AssetForm.startingValues(schema, modIndex).containsKey("SPRITES." + room.sprites().get(0) + ".0.FRAMES"));
		AssetCreator.Created made = AssetCreator.create(root, entry.type(), schema, "TEST_ROOM", good, parser, modIndex);
		expect(label + "'s file has the prefix the game needs", made.dataFile().equals(assets.resolve("init/room/" + room.prefix() + "TEST_ROOM.txt")));
		expect("and its text file", made.textFile().equals(assets.resolve("text/room/" + room.prefix() + "TEST_ROOM.txt")));
		expect("and its sounds", made.soundFiles().size() == 2 && Files.readString(assets.resolve("audio/config/mono/" + room.prefix() + "TEST_ROOM.txt")).contains("ROOM_WORK_" + room.prefix() + "TEST_ROOM: DUMMY"));
		List<AssetChecker.Finding> findings = AssetVanillaCheck.check(schema, modIndex, made.dataFile(), parser);
		expect("the new " + label + " passes the vanilla check: " + findings, findings.isEmpty());
		List<AssetChecker.Finding> textFindings = AssetVanillaCheck.checkText(schema, made.textFile(), parser);
		expect("its text passes too: " + textFindings, textFindings.isEmpty());
		GameParser.Node node = parser.root(made.dataFile());
		GameParser.Node holder = room.label().equals("Hunter") ? node.blocks("INDUSTRIES").get(0) : node;
		expect("the game reads the output", holder.block("INDUSTRY").block("OUT").block("MEAT").decimal("PLAYER", 0, 100000) == 1);
		expect("and an item's lists", node.blocks("ITEMS").get(1).decimals("STATS").length == room.stats());
		GameParser.Node text = parser.root(made.textFile());
		expect("the text has every stat block", text.blocks("STATS").size() == room.stats());
		for (int block : room.namedStats()) {
			expect("and the game can read block " + block, ("Stat " + block).equals(text.blocks("STATS").get(block).text("NAME")));
		}
		expect("a vanilla name is refused", AssetCreator.problemWithName(entry.type(), room.vanilla(), modIndex) != null);
	}

	/** Orchard's and Hunter's own tags. The expected values were checked by hand against ROOM_ORCHARD.java:72,183-184, orchard/Constructor.java:140 and ROOM_HUNTER.java:63. */
	private static void orchardAndHunterLimits(List<AssetCatalog.Entry> entries, GameAssetIndex index) {
		FoodRoom orchardRoom = FOOD_ROOMS.get(1);
		AssetCatalog.Entry orchard = entryNamed(entries, "Orchard");
		if (orchard.available()) {
			AssetSchema schema = orchard.schema();
			Map<String, String> good = validFoodRoom(orchardRoom);
			expectNested(schema, "INDOORS", TagSpec.Kind.BOOL, true, null, null, null);
			expectNested(schema, "DAYS_TILL_GROWTH", TagSpec.Kind.INT, true, null, 8.0, 1024.0);
			expectNested(schema, "RIPE_AT_PART_OF_YEAR", TagSpec.Kind.DECIMAL, true, null, 0.0, 1.0);
			expectNested(schema, "EXTRA_RESOURCE_AMOUNT", TagSpec.Kind.INT, true, null, null, null);
			for (String key : List.of("INDOORS", "EXTRA_RESOURCE", "EXTRA_RESOURCE_AMOUNT", "DAYS_TILL_GROWTH", "RIPE_AT_PART_OF_YEAR")) {
				refused(schema, index, good, key, null);
			}
			refused(schema, index, good, "EXTRA_RESOURCE", "NOT_A_RESOURCE");
			refused(schema, index, good, "DAYS_TILL_GROWTH", "7");
			clean(schema, index, good, "DAYS_TILL_GROWTH", "8");
			// Json.i enforces only its minimum.
			warned(schema, index, good, "DAYS_TILL_GROWTH", "1025");
			refused(schema, index, good, "RIPE_AT_PART_OF_YEAR", "1.5");
			clean(schema, index, good, "RIPE_AT_PART_OF_YEAR", "1");
			// The game's code makes this sheet in three sizes, with no image (TForest.java:607-614,629), and one row.
			clean(schema, index, good, "SPRITES.TREE_2X2.0.FRAMES", "_TREE: 0");
			clean(schema, index, good, "SPRITES.TREE_1X1.0.FRAMES", "_TREE: 0");
			warned(schema, index, good, "SPRITES.TREE_2X2.0.FRAMES", "_TREE: 1");
			warned(schema, index, good, "SPRITES.EDGE_COMBO.0.FRAMES", "_TREE: 0");
			warned(schema, index, good, "SPRITES.TREE_2X2.0.FRAMES", "NOT_A_SHEET: 0");
		}
		FoodRoom hunterRoom = FOOD_ROOMS.get(2);
		AssetCatalog.Entry hunter = entryNamed(entries, "Hunter");
		if (hunter.available()) {
			AssetSchema schema = hunter.schema();
			Map<String, String> good = validFoodRoom(hunterRoom);
			expectNested(schema, "MAX_EMPLOYED", TagSpec.Kind.INT, true, null, 1.0, 10000.0);
			expect("a hunter has no experience bonus", schema.find("EXPERIENCE_BONUS") == null);
			expect("the notes say a hunter isn't a producer", schema.notes().stream().anyMatch(note -> note.contains("no producer")));
			refused(schema, index, good, "MAX_EMPLOYED", null);
			refused(schema, index, good, "MAX_EMPLOYED", "0");
			warned(schema, index, good, "MAX_EMPLOYED", "10001");
			clean(schema, index, good, "STATS.1.NAME", null);
			clean(schema, index, good, "STATS.0.NAME", "");
		}
	}

	private static List<String> refinerClasses() {
		List<String> classes = new java.util.ArrayList<>(FARM_CLASSES);
		classes.remove("settlement/room/food/farm/ROOM_FARM");
		classes.remove("settlement/room/food/farm/Constructor");
		classes.addAll(List.of("settlement/room/industry/refiner/ROOM_REFINER", "settlement/room/industry/refiner/Constructor", "settlement/room/sprite/RoomSpriteImp", "settlement/room/sprite/RoomSprite1x1",
				"settlement/room/sprite/RoomSprite1xN", "settlement/room/sprite/RoomSpriteCombo", "init/sprite/game/Sheets", "init/sprite/game/GameSheets", "init/sprite/game/SheetData"));
		for (int i = 3; i <= 9; i++) {
			classes.add("settlement/room/industry/refiner/Constructor$" + i);
		}
		return classes;
	}

	private static final List<String> REFINER_SPRITES = List.of("MAIN_MACHINE_COMBO", "MAIN_MACHINE_COMBO_TOP", "MAIN_MACHINE_COMBO_TOP_MACHINE", "WORK_1X1", "STORAGE_IN_1X1", "NICKNACK_BOTTOM_1X1", "NICKNACK_TOP_1X1", "MACHINE_1X1", "CONVEYOR_1X1", "STORAGE_1X1");

	private static void refiners(List<AssetCatalog.Entry> entries, Path game, GameAssetIndex index, Path temp, GameParser parser) throws Exception {
		AssetCatalog.Entry refiner = entryNamed(entries, "Refiner");
		expect("Refiner is available: " + refiner.unavailableReason(), refiner.available());
		if (!refiner.available()) {
			return;
		}
		AssetSchema schema = refiner.schema();
		// The expected values were checked by hand against ROOM_REFINER.java, refiner/Constructor.java, Industry.java and Furnisher.java.
		expect("a refiner's files start with REFINER_", refiner.type().fileName("X").equals("REFINER_X"));
		expect("the refiner's tags: " + schema.dataTags().stream().map(TagSpec::key).toList(), schema.dataTags().stream().map(TagSpec::key).toList().equals(List.of("ICON", "STORAGE", "INDUSTRIES", "RESOURCES", "AREA_COSTS", "FLOOR", "MINI_COLOR",
				"MINI_COLOR_PATTERN", "WORK", "ITEMS", "SPRITES", "BONUS", "EXPERIENCE_BONUS", "UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE")));
		expectNested(schema, "STORAGE", TagSpec.Kind.INT, true, null, 8.0, 500.0);
		expect("STORAGE's maximum isn't enforced", !schema.tag("STORAGE").maxEnforced());
		expectNested(schema, "INDUSTRIES", TagSpec.Kind.BLOCK_LIST, true, null, null, null);
		expect("the player decides how many recipes", schema.tag("INDUSTRIES").shape().fixedCount() == 0);
		expectNested(schema, "INDUSTRIES.0.INDUSTRY", TagSpec.Kind.BLOCK, true, null, null, null);
		expectNested(schema, "INDUSTRIES.0.INDUSTRY.OUT", TagSpec.Kind.KEYED_ROWS, true, null, null, null);
		expectNested(schema, "INDUSTRIES.0.INDUSTRY.IN", TagSpec.Kind.KEYED_ROWS, false, "Nothing consumed", null, null);
		expectNested(schema, "INDUSTRIES.0.INDUSTRY.ICON", TagSpec.Kind.ICON, false, "Built from what it produces", null, null);
		expectNested(schema, "FLOOR", TagSpec.Kind.KEY_LIST, false, "No floor of its own", null, null);
		expect("FLOOR isn't described as having amounts: " + schema.ruleText(schema.tag("FLOOR")), schema.ruleText(schema.tag("FLOOR")) == null);
		expect("a refiner has exactly 3 items", schema.tag("ITEMS").shape().fixedCount() == 3);
		expect("each with words in the text file", schema.isTextKey("ITEMS.2.NAME") && !schema.isTextKey("ITEMS.2.COSTS"));
		TagSpec stats = schema.textTags().stream().filter(t -> t.key().equals("STATS")).findFirst().orElseThrow();
		expect("the game names all 3 stats itself", stats.shape().fixedCount() == 3 && stats.shape().namedSlots() == 0);
		expect("every sprite the Constructor reads is there", schema.tag("SPRITES").shape().children().stream().map(TagSpec::key).toList().equals(REFINER_SPRITES));
		for (String sprite : REFINER_SPRITES) {
			String key = "SPRITES." + sprite + ".";
			expectNested(schema, "SPRITES." + sprite, TagSpec.Kind.BLOCK_LIST, true, null, null, null);
			expect(sprite + " looks in the right folder", schema.find(key + "FRAMES").sprite().folder().equals(sprite.endsWith("COMBO") || sprite.endsWith("COMBO_TOP") ? "sprite/game/combo/" : "sprite/game/1x1/"));
			expectNested(schema, key + "COLOR", TagSpec.Kind.COLORS, false, "None, so TINT decides", 0.0, 255.0);
		}
		expect("a refiner starts from a vanilla file's sprites", schema.starter() != null && schema.starter().file().contains("REFINER_") && schema.starter().tags().equals(List.of("SPRITES")));
		expect("a refiner's sounds start from a refiner's", schema.sounds().stream().allMatch(slot -> slot.likeKey().contains("REFINER_")));

		List<String> classes = refinerClasses();
		AssetCatalog.Entry control = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve("refiner-all.jar"), classes)), "Refiner");
		expect("these classes are enough for Refiner: " + control.unavailableReason(), control.available());
		int at = 0;
		for (String gone : classes) {
			AssetCatalog.Entry entry = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve("refiner-without-" + at++ + ".jar"), classes.stream().filter(n -> !n.equals(gone)).toList())), "Refiner");
			expect("without " + gone + ", Refiner is disabled: " + entry.unavailableReason(), !entry.available() && entry.unavailableReason() != null);
		}
		for (String[] change : new String[][] { { "settlement/room/industry/refiner/ROOM_REFINER", "STORAGE", "STORAGX" }, { "settlement/room/industry/refiner/Constructor", "MAIN_MACHINE_COMBO_TOP", "MAIN_MACHINE_COMBO_TOX" },
				{ "settlement/room/industry/refiner/Constructor$6", "NICKNACK_TOP_1X1", "NICKNACK_TOX_1X1" }, { "settlement/room/industry/module/Industry", "INDUSTRIES", "INDUSTRIEX" }, { "init/sprite/UI/Icons", "SCALE", "SCALX" } }) {
			AssetCatalog.Entry changed = entryNamed(AssetCatalog.load(patchedJar(game, temp.resolve("refiner-changed-" + change[1] + ".jar"), classes, change[0], change[1], change[2])), "Refiner");
			expect("a changed " + change[1] + " turns Refiner off: " + changed.unavailableReason(), !changed.available() && changed.unavailableReason() != null);
		}

		expect("a combo sheet's rows are the game's sums: " + index.sheetRows("sprite/game/combo/", "REFINER"), index.sheetRows("sprite/game/combo/", "REFINER").validSize() && index.sheetRows("sprite/game/combo/", "REFINER").rows() > 7);

		Path folder = temp.resolve("vanilla-refiners");
		Files.createDirectories(folder.resolve("data"));
		Files.createDirectories(folder.resolve("text"));
		int data = 0;
		int texts = 0;
		// Vanilla's text files also have a BONUS block with words, which no reviewed read takes from the text file.
		List<String> known = new java.util.ArrayList<>(schema.textTags().stream().map(TagSpec::key).toList());
		known.addAll(List.of("INFO", "BONUS"));
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile())) {
			Enumeration<? extends ZipEntry> files = zip.entries();
			while (files.hasMoreElements()) {
				ZipEntry entry = files.nextElement();
				String name = entry.getName().substring(entry.getName().lastIndexOf('/') + 1);
				boolean isData = entry.getName().matches("data/assets/init/room/REFINER_[^/]+\\.txt");
				if (!isData && !entry.getName().matches("data/assets/text/room/REFINER_[^/]+\\.txt")) {
					continue;
				}
				Path copy = folder.resolve(isData ? "data" : "text").resolve(name);
				try (InputStream in = zip.getInputStream(entry)) {
					Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
				}
				if (isData) {
					data++;
					List<AssetChecker.Finding> findings = AssetVanillaCheck.check(schema, index, copy, parser);
					expect(name + " has no errors or warnings: " + findings, findings.isEmpty());
				} else {
					texts++;
					List<String> keys = parser.tags(copy).tags().stream().map(GameParser.TagValue::key).toList();
					expect(name + " text uses only known tags: " + keys, known.containsAll(keys));
					List<AssetChecker.Finding> findings = AssetVanillaCheck.checkText(schema, copy, parser);
					expect(name + " text reads as the schema says: " + findings, findings.isEmpty());
				}
			}
		}
		expect("all 5 vanilla refiners were checked: " + data, data == 5);
		expect("their text files were checked: " + texts, texts == 5);
	}

	private static List<String> workshopClasses() {
		List<String> classes = new java.util.ArrayList<>(FARM_CLASSES);
		classes.remove("settlement/room/food/farm/ROOM_FARM");
		classes.remove("settlement/room/food/farm/Constructor");
		String c = "settlement/room/industry/workshop/Constructor";
		classes.addAll(List.of("settlement/room/industry/workshop/ROOM_WORKSHOP", c, c + "$STable", "settlement/room/sprite/RoomSpriteImp", "settlement/room/sprite/RoomSprite1x1",
				"settlement/room/sprite/RoomSprite1xN", "settlement/room/sprite/RoomSpriteCombo", "init/sprite/game/Sheets", "init/sprite/game/GameSheets", "init/sprite/game/SheetData"));
		for (String inner : List.of("$3", "$4", "$5", "$6", "$6$1", "$6$2", "$7", "$8", "$8$1", "$9", "$9$1")) {
			classes.add(c + inner);
		}
		return classes;
	}

	private static final List<String> WORKSHOP_SPRITES = List.of("CHAIR_1X1", "TABLE_COMBO", "TABLE_TOP_COMBO", "WORK_BELOW_1X1", "WORK_ABOVE_1X1", "TOOL_1X1", "MISC_BELOW_1X1", "MISC_ABOVE_1X1");

	/** The expected values were checked by hand against ROOM_WORKSHOP.java, workshop/Constructor.java, Industry.java and Furnisher.java. */
	private static void workshops(List<AssetCatalog.Entry> entries, Path game, Path mods, GameParser parser, GameParser.GameVersion version, GameAssetIndex index, Path temp) throws Exception {
		AssetCatalog.Entry workshop = entryNamed(entries, "Workshop");
		expect("Workshop is available: " + workshop.unavailableReason(), workshop.available());
		if (!workshop.available()) {
			return;
		}
		AssetSchema schema = workshop.schema();
		expect("a workshop's files start with WORKSHOP_", workshop.type().fileName("X").equals("WORKSHOP_X"));
		expect("the workshop's tags: " + schema.dataTags().stream().map(TagSpec::key).toList(), schema.dataTags().stream().map(TagSpec::key).toList().equals(List.of("ICON", "INDUSTRIES", "RESOURCES", "AREA_COSTS", "FLOOR", "MINI_COLOR",
				"MINI_COLOR_PATTERN", "WORK", "ITEMS", "SPRITES", "BONUS", "EXPERIENCE_BONUS", "UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE")));
		expect("STORAGE isn't offered", schema.find("STORAGE") == null);
		expectNested(schema, "INDUSTRIES", TagSpec.Kind.BLOCK_LIST, true, null, null, null);
		expect("the player decides how many recipes", schema.tag("INDUSTRIES").shape().fixedCount() == 0);
		expectNested(schema, "INDUSTRIES.0.INDUSTRY", TagSpec.Kind.BLOCK, true, null, null, null);
		expectNested(schema, "INDUSTRIES.0.INDUSTRY.OUT", TagSpec.Kind.KEYED_ROWS, true, null, null, null);
		expectNested(schema, "INDUSTRIES.0.INDUSTRY.IN", TagSpec.Kind.KEYED_ROWS, false, "Nothing consumed", null, null);
		List<TagSpec.Column> outColumns = schema.find("INDUSTRIES.0.INDUSTRY.OUT").shape().columns();
		expect("an output's numbers: " + outColumns, outColumns.stream().map(TagSpec.Column::key).toList().equals(List.of("PLAYER", "AI_RATE", "AI_RECOVERY")));
		expect("player output is required, from 0 to 100000", outColumns.get(0).required() && outColumns.get(0).min() == 0 && outColumns.get(0).max() == 100000);
		expect("the AI rate can be left out", !outColumns.get(1).required() && "As player".equals(outColumns.get(1).absent()));
		List<TagSpec.Column> inColumns = schema.find("INDUSTRIES.0.INDUSTRY.IN").shape().columns();
		expect("an input is one rate, from 0 to 10000", inColumns.size() == 1 && inColumns.get(0).key() == null && inColumns.get(0).required() && inColumns.get(0).min() == 0 && inColumns.get(0).max() == 10000);
		expectNested(schema, "FLOOR", TagSpec.Kind.KEY_LIST, false, "No floor of its own", null, null);
		expectNested(schema, "DEGRADE_RATE", TagSpec.Kind.DECIMAL, false, "0.75", 0.0, 1.0);
		expectNested(schema, "WORK.SHIFT_OFFSET", TagSpec.Kind.DECIMAL, true, null, 0.0, 0.99);
		expectNested(schema, "WORK.NIGHT_SHIFT", TagSpec.Kind.BOOL, false, "false", null, null);
		expectNested(schema, "WORK.FULFILLMENT", TagSpec.Kind.DECIMAL, false, "0.5", -100000.0, 100000.0);
		expectNested(schema, "WORK.ACCIDENTS_PER_YEAR", TagSpec.Kind.DECIMAL, false, "0.0", 0.0, 10000.0);
		expectNested(schema, "WORK.HEALTH_FACTOR", TagSpec.Kind.DECIMAL, false, "1.0", 0.0, 1.0);
		expectNested(schema, "WORK.LARGE_WORKFORCE", TagSpec.Kind.INT, false, "1000", 10.0, 10000.0);
		expectNested(schema, "WORK.TITLE", TagSpec.Kind.TEXT_LINE, true, null, null, null);
		expectNested(schema, "WORK.VERB", TagSpec.Kind.TEXT_LINE, true, null, null, null);
		expectNested(schema, "BONUS.CLIMATE", TagSpec.Kind.DECIMAL_MAP, false, null, 0.0, 2000.0);
		expectNested(schema, "EXPERIENCE_BONUS.MAX_EMPLOYEES", TagSpec.Kind.INT, true, null, 50.0, null);
		expectNested(schema, "EXPERIENCE_BONUS.BONUS", TagSpec.Kind.DECIMAL, true, null, null, null);
		expectNested(schema, "UPGRADES.0.BOOST", TagSpec.Kind.DECIMAL, true, null, null, null);
		expectNested(schema, "UPGRADES.0.AI", TagSpec.Kind.DECIMAL, false, "Half of BOOST", 0.0, 10000.0);
		expectNested(schema, "ENVIRONMENT_EMIT", TagSpec.Kind.KEYED_ROWS, false, null, null, null);
		expectNested(schema, "MINI_COLOR", TagSpec.Kind.COLOR, true, null, 0.0, 255.0);
		expectNested(schema, "MINI_COLOR_PATTERN", TagSpec.Kind.TEXT_ROWS, false, "One colour for the whole room", null, null);
		expectNested(schema, "RESOURCES", TagSpec.Kind.KEY_LIST, false, "Empty", null, null);
		expectNested(schema, "AREA_COSTS", TagSpec.Kind.LINKED_DECIMALS, true, null, null, null);
		expectNested(schema, "ITEMS.0.COSTS", TagSpec.Kind.DECIMALS, true, null, null, null);
		expectNested(schema, "ITEMS.0.STATS", TagSpec.Kind.DECIMALS, true, null, null, null);
		expectNested(schema, "ITEMS.0.NAME", TagSpec.Kind.TEXT_LINE, true, null, null, null);
		expectNested(schema, "ITEMS.0.DESC", TagSpec.Kind.TEXT_LINES, true, null, null, null);
		expect("a workshop has exactly 3 items", schema.tag("ITEMS").shape().fixedCount() == 3);
		expect("each with words in the text file", schema.isTextKey("ITEMS.2.NAME") && !schema.isTextKey("ITEMS.2.COSTS"));
		TagSpec stats = schema.textTags().stream().filter(t -> t.key().equals("STATS")).findFirst().orElseThrow();
		expect("the game names all 3 stats itself", stats.shape().fixedCount() == 3 && stats.shape().namedSlots() == 0);
		expect("every sprite the Constructor reads is there", schema.tag("SPRITES").shape().children().stream().map(TagSpec::key).toList().equals(WORKSHOP_SPRITES));
		for (String sprite : WORKSHOP_SPRITES) {
			String key = "SPRITES." + sprite + ".";
			expectNested(schema, "SPRITES." + sprite, TagSpec.Kind.BLOCK_LIST, true, null, null, null);
			expectNested(schema, key + "FRAMES", TagSpec.Kind.FRAMES, true, null, null, null);
			expect(sprite + " looks in the right folder", schema.find(key + "FRAMES").sprite().folder().equals(sprite.endsWith("COMBO") ? "sprite/game/combo/" : "sprite/game/1x1/"));
			expectNested(schema, key + "FPS", TagSpec.Kind.DECIMAL, false, "0", 0.0, 100000.0);
			expectNested(schema, key + "FPS_INTERVAL", TagSpec.Kind.DECIMAL, false, "1", 0.0, 1.0);
			expectNested(schema, key + "SHADOW_LENGTH", TagSpec.Kind.DECIMAL, false, "0", 0.0, 100.0);
			expectNested(schema, key + "SHADOW_HEIGHT", TagSpec.Kind.DECIMAL, false, "0", 0.0, 100.0);
			expectNested(schema, key + "CIRCULAR", TagSpec.Kind.BOOL, false, "false", null, null);
			expectNested(schema, key + "ROTATES", TagSpec.Kind.BOOL, false, "true", null, null);
			expectNested(schema, key + "TINT", TagSpec.Kind.BOOL, false, "true", null, null);
			expectNested(schema, key + "COLOR", TagSpec.Kind.COLORS, false, "None, so TINT decides", 0.0, 255.0);
		}
		expect("a workshop has no starter", schema.starter() == null);
		expect("its sounds have no shared starting entry", schema.sounds().size() == 3 && schema.sounds().stream().allMatch(slot -> slot.likeKey() == null));
		expect("and they are the room's three lookups", schema.sounds().stream().map(SoundEntries.Slot::valueKey).toList().equals(List.of(SoundEntries.ROOM_WORK.valueKey(), SoundEntries.ROOM_CLICK.valueKey(), SoundEntries.ROOM_AMBIENCE.valueKey())));
		expect("the notes name the unread storage tag", schema.notes().stream().anyMatch(note -> note.contains("STORAGE")));
		for (String unread : List.of("STORAGE", "WORK.SOUND", "REQUIRES", "EMPLOYMENT")) {
			expect(unread + " isn't offered", schema.find(unread) == null);
		}

		List<String> classes = workshopClasses();
		AssetCatalog.Entry control = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve("workshop-all.jar"), classes)), "Workshop");
		expect("these classes are enough for Workshop: " + control.unavailableReason(), control.available());
		int at = 0;
		for (String gone : classes) {
			AssetCatalog.Entry entry = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve("workshop-without-" + at++ + ".jar"), classes.stream().filter(n -> !n.equals(gone)).toList())), "Workshop");
			expect("without " + gone + ", Workshop is disabled: " + entry.unavailableReason(), !entry.available() && entry.unavailableReason() != null);
		}
		// A class whose reads hold only locals has no key to rename. Removing it, above, is what turns the type off.
		for (String[] change : new String[][] {
				{ "settlement/room/industry/workshop/ROOM_WORKSHOP", "WORKSHOP", "WORKSHOX" },
				{ "settlement/room/industry/workshop/Constructor", "SPRITES", "SPRITEX" },
				{ "settlement/room/industry/workshop/Constructor", "CHAIR_1X1", "CHAIR_1X2" },
				{ "settlement/room/industry/workshop/Constructor$STable", "TABLE_COMBO", "TABLE_COMBX" },
				{ "settlement/room/industry/workshop/Constructor$STable", "TABLE_TOP_COMBO", "TABLE_TOP_COMBX" },
				{ "settlement/room/industry/workshop/Constructor$6", "WORK_BELOW_1X1", "WORK_BELOW_1X2" },
				{ "settlement/room/industry/workshop/Constructor$6", "WORK_ABOVE_1X1", "WORK_ABOVE_1X2" },
				{ "settlement/room/industry/workshop/Constructor$8", "TOOL_1X1", "TOOL_1X2" },
				{ "settlement/room/industry/workshop/Constructor$9", "MISC_ABOVE_1X1", "MISC_ABOVE_1X2" },
				{ "settlement/room/industry/module/Industry", "INDUSTRIES", "INDUSTRIEX" },
				{ "init/sprite/UI/Icons", "SCALE", "SCALX" },
				{ "init/sprite/game/SheetData", "SHADOW_LENGTH", "SHADOW_LENGTX" },
				{ "init/sprite/game/GameSheets", "FRAMES", "FRAMEX" } }) {
			AssetCatalog.Entry changed = entryNamed(AssetCatalog.load(patchedJar(game, temp.resolve("workshop-changed-" + change[1] + ".jar"), classes, change[0], change[1], change[2])), "Workshop");
			expect("a changed " + change[1] + " turns Workshop off: " + changed.unavailableReason(), !changed.available() && changed.unavailableReason() != null);
		}

		Path folder = temp.resolve("vanilla-workshops");
		Files.createDirectories(folder.resolve("data"));
		Files.createDirectories(folder.resolve("text"));
		int data = 0;
		int texts = 0;
		// The text file's BONUS block isn't read (RoomBlueprintIns.java:118-120).
		List<String> known = new java.util.ArrayList<>(schema.textTags().stream().map(TagSpec::key).toList());
		known.addAll(List.of("INFO", "BONUS"));
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile())) {
			Enumeration<? extends ZipEntry> files = zip.entries();
			while (files.hasMoreElements()) {
				ZipEntry entry = files.nextElement();
				String name = entry.getName().substring(entry.getName().lastIndexOf('/') + 1);
				boolean isData = entry.getName().matches("data/assets/init/room/WORKSHOP_[^/]+\\.txt");
				if (!isData && !entry.getName().matches("data/assets/text/room/WORKSHOP_[^/]+\\.txt")) {
					continue;
				}
				Path copy = folder.resolve(isData ? "data" : "text").resolve(name);
				try (InputStream in = zip.getInputStream(entry)) {
					Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
				}
				if (isData) {
					data++;
					List<AssetChecker.Finding> findings = new java.util.ArrayList<>(AssetVanillaCheck.check(schema, index, copy, parser));
					// Every vanilla file writes STORAGE, and the only readers are the mine, refiner and woodcutter.
					expect(name + " warns that STORAGE is unread", findings.removeIf(f -> "STORAGE".equals(f.tag()) && f.severity() == AssetChecker.Severity.WARNING));
					expect(name + " has no other errors or warnings: " + findings, findings.isEmpty());
				} else {
					texts++;
					List<String> keys = parser.tags(copy).tags().stream().map(GameParser.TagValue::key).toList();
					expect(name + " text uses only known tags: " + keys, known.containsAll(keys));
					List<AssetChecker.Finding> findings = AssetVanillaCheck.checkText(schema, copy, parser);
					expect(name + " text reads as the schema says: " + findings, findings.isEmpty());
				}
			}
		}
		expect("all 10 vanilla workshops were checked: " + data, data == 10);
		expect("their text files were checked: " + texts, texts == 10);

		workshopRules(schema, index);
		workshopCreation(workshop, mods, game, parser, version);
	}

	private static Map<String, String> validWorkshop() {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("ICON", "24->resource->Gem->0");
		values.put("RESOURCES", "WOOD");
		values.put("AREA_COSTS", "0");
		values.put("FLOOR", "DIRT");
		values.put("MINI_COLOR", "10_20_30");
		values.put("WORK.SHIFT_OFFSET", "0.125");
		values.put("INDUSTRIES.0.INDUSTRY.IN.WOOD", "1");
		values.put("INDUSTRIES.0.INDUSTRY.OUT.FURNITURE.PLAYER", "1");
		for (int i = 0; i < 3; i++) {
			values.put("ITEMS." + i + ".COSTS", "1");
			values.put("ITEMS." + i + ".STATS", "0,1,0");
			values.put("ITEMS." + i + ".NAME", "Item " + i);
			values.put("ITEMS." + i + ".DESC", "About item " + i);
		}
		for (String sprite : WORKSHOP_SPRITES) {
			values.put("SPRITES." + sprite + ".0.FRAMES", sprite.endsWith("COMBO") ? "COMBO_TABLES: 6" : "STORAGE: 6");
		}
		values.put("SOUND.ROOM_WORK_", "DUMMY");
		values.put("SOUND.ROOM_CLICK_", "DUMMY");
		values.put("SOUND.ROOM_", "DUMMY");
		values.put("NAME", "Test Workshop");
		values.put("DESC", "It's for testing.");
		values.put("WORK.TITLE", "Tester");
		values.put("WORK.VERB", "testing");
		return values;
	}

	private static void workshopRules(AssetSchema schema, GameAssetIndex index) {
		Map<String, String> good = validWorkshop();
		expect("a good workshop has no problems: " + AssetChecker.check(schema, index, good), AssetChecker.check(schema, index, good).isEmpty());
		for (String key : List.of("ICON", "MINI_COLOR", "WORK.SHIFT_OFFSET", "INDUSTRIES.0.INDUSTRY.OUT.FURNITURE.PLAYER", "ITEMS.0.COSTS", "ITEMS.2.STATS", "NAME", "DESC", "WORK.TITLE", "ITEMS.0.NAME", "ITEMS.2.DESC", "SPRITES.CHAIR_1X1.0.FRAMES", "SOUND.ROOM_WORK_")) {
			refused(schema, index, good, key, null);
		}
		refused(schema, index, good, "INDUSTRIES.0.INDUSTRY.OUT.FURNITURE.PLAYER", "100000.5");
		clean(schema, index, good, "INDUSTRIES.0.INDUSTRY.OUT.FURNITURE.PLAYER", "100000");
		clean(schema, index, good, "INDUSTRIES.0.INDUSTRY.OUT.FURNITURE.PLAYER", "0");
		refused(schema, index, good, "INDUSTRIES.0.INDUSTRY.IN.WOOD", "10000.5");
		clean(schema, index, good, "FLOOR", null);
		warnsOnly(schema, index, good, "FLOOR", "NOT_A_FLOOR", "leaves that one out");
		clean(schema, index, good, "FLOOR", "DIRT,STONE_BRICK");
		refused(schema, index, good, "WORK.SHIFT_OFFSET", "1");
		clean(schema, index, good, "WORK.SHIFT_OFFSET", "0.99");
		clean(schema, index, good, "DEGRADE_RATE", "0");
		refused(schema, index, good, "DEGRADE_RATE", "1.5");
		// Json.ds(key, size) stops the game on any other length (Json.java:271-277).
		refused(schema, index, good, "ITEMS.0.COSTS", "1,2");
		refused(schema, index, good, "ITEMS.0.COSTS", "");
		refused(schema, index, good, "ITEMS.1.STATS", "0,1");
		refused(schema, index, good, "ITEMS.1.STATS", "0,1,0,0");
		Map<String, String> noResources = new LinkedHashMap<>(good);
		noResources.remove("RESOURCES");
		noResources.remove("AREA_COSTS");
		noResources.put("ITEMS.0.COSTS", "");
		noResources.put("ITEMS.1.COSTS", "");
		noResources.put("ITEMS.2.COSTS", "");
		expect("with no resources an item costs nothing: " + AssetChecker.check(schema, index, noResources), AssetChecker.check(schema, index, noResources).isEmpty());
		expect("a blank list with resources is still to be filled in, not wrong", changed(schema, index, good, "ITEMS.0.COSTS", "").stream().anyMatch(f -> f.tag().equals("ITEMS.0.COSTS") && f.severity() == AssetChecker.Severity.MISSING));
		// The code only proves a crash with 2 levels and every mask too short (RoomUpgrades.java:127-128).
		Map<String, String> oneLevel = new LinkedHashMap<>(good);
		oneLevel.put("RESOURCES", "WOOD,STONE");
		oneLevel.put("AREA_COSTS", "0,0");
		oneLevel.put("ITEMS.0.COSTS", "1,1");
		oneLevel.put("ITEMS.1.COSTS", "1,1");
		oneLevel.put("ITEMS.2.COSTS", "1,1");
		oneLevel.put("UPGRADES.0.RESOURCE_MASK", "1");
		oneLevel.put("UPGRADES.0.BOOST", "0");
		expect("one short upgrade is allowed, because the game clamps it: " + AssetChecker.check(schema, index, oneLevel), AssetChecker.check(schema, index, oneLevel).isEmpty());
		Map<String, String> levels = new LinkedHashMap<>(oneLevel);
		levels.put("UPGRADES.1.RESOURCE_MASK", "1");
		levels.put("UPGRADES.1.BOOST", "1");
		expect("two short masks are refused", AssetChecker.check(schema, index, levels).stream().anyMatch(f -> "UPGRADES.0.RESOURCE_MASK".equals(f.tag()) && f.blocksSaving()));
		levels.put("UPGRADES.1.RESOURCE_MASK", "1,1");
		expect("a shorter level is padded, so one full mask is enough: " + AssetChecker.check(schema, index, levels), AssetChecker.check(schema, index, levels).isEmpty());
	}

	/** A new workshop, read back with the game's own reads at the paths the loader uses. */
	private static void workshopCreation(AssetCatalog.Entry workshop, Path mods, Path game, GameParser parser, GameParser.GameVersion version) throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Workshop Creation");
		Path root = mod.root();
		Path modVersion = AssetCreator.versionFolder(root, version);
		Path assets = modVersion.resolve("assets");
		GameAssetIndex index = GameAssetIndex.load(game, modVersion, parser);
		expect("a new workshop starts with nothing filled in", AssetForm.startingValues(workshop.schema(), index).isEmpty());

		Map<String, String> values = validWorkshop();
		AssetCreator.Created made = AssetCreator.create(root, workshop.type(), workshop.schema(), "TEST_SHOP", values, parser, index);
		expect("the workshop's file has the prefix the game needs", made.dataFile().equals(assets.resolve("init/room/WORKSHOP_TEST_SHOP.txt")));
		expect("and its text file", made.textFile().equals(assets.resolve("text/room/WORKSHOP_TEST_SHOP.txt")));
		expect("and its sounds", made.soundFiles().size() == 2 && Files.readString(assets.resolve("audio/config/mono/WORKSHOP_TEST_SHOP.txt")).contains("ROOM_WORK_WORKSHOP_TEST_SHOP: DUMMY"));
		List<AssetChecker.Finding> findings = AssetVanillaCheck.check(workshop.schema(), index, made.dataFile(), parser);
		expect("the new workshop passes the vanilla check: " + findings, findings.isEmpty());
		List<AssetChecker.Finding> textFindings = AssetVanillaCheck.checkText(workshop.schema(), made.textFile(), parser);
		expect("its text passes too: " + textFindings, textFindings.isEmpty());

		GameParser.Node node = parser.root(made.dataFile());
		expect("the game reads the recipe's output", node.blocks("INDUSTRIES").get(0).block("INDUSTRY").block("OUT").block("FURNITURE").decimal("PLAYER", 0, 100000) == 1);
		expect("and its input", node.blocks("INDUSTRIES").get(0).block("INDUSTRY").block("IN").decimal("WOOD", 0, 10000) == 1);
		expect("and a chair's frames, as written", java.util.Arrays.equals(node.block("SPRITES").blocks("CHAIR_1X1").get(0).values("FRAMES"), new String[] { "STORAGE: 6" }));
		expect("and a table's frames", java.util.Arrays.equals(node.block("SPRITES").blocks("TABLE_COMBO").get(0).values("FRAMES"), new String[] { "COMBO_TABLES: 6" }));
		expect("and an item's lists", java.util.Arrays.equals(node.blocks("ITEMS").get(1).decimals("COSTS"), new double[] { 1 }) && node.blocks("ITEMS").get(2).decimals("STATS").length == 3);
		GameParser.Node text = parser.root(made.textFile());
		expect("the game's text file names the work", "Tester".equals(text.block("WORK").text("TITLE")) && "testing".equals(text.block("WORK").text("VERB")));
		expect("and has the three stat blocks the game requires, with nothing in them", text.blocks("STATS").size() == 3 && !text.blocks("STATS").get(0).has("NAME"));
		expect("and its items", "Item 1".equals(text.blocks("ITEMS").get(1).text("NAME")));

		expect("a vanilla workshop's name is refused", AssetCreator.problemWithName(workshop.type(), "SMITHY", index) != null);
		expect("a new workshop name works", AssetCreator.problemWithName(workshop.type(), "NEW", index) == null);
	}

	private static List<String> pastureClasses() {
		List<String> classes = new java.util.ArrayList<>(FARM_CLASSES);
		classes.remove("settlement/room/food/farm/ROOM_FARM");
		classes.remove("settlement/room/food/farm/Constructor");
		String c = "settlement/room/food/pasture/Constructor";
		classes.addAll(List.of("settlement/room/food/pasture/ROOM_PASTURE", c, c + "$4", c + "$5", c + "$6",
				"settlement/room/food/pasture/ConstructorIndoor", "settlement/room/food/pasture/ConstructorIndoor$SpriteDep",
				"settlement/room/food/pasture/ConstructorOutdoor", "settlement/room/food/pasture/ConstructorOutdoor$3", "settlement/room/food/pasture/ConstructorOutdoor$SpriteDep",
				"settlement/room/sprite/RoomSpriteImp", "settlement/room/sprite/RoomSprite1x1", "settlement/room/sprite/RoomSprite1xN", "settlement/room/sprite/RoomSpriteCombo",
				"settlement/room/sprite/RoomSpriteXxX", "init/sprite/game/Sheets", "init/sprite/game/GameSheets", "init/sprite/game/SheetData"));
		return classes;
	}

	private static final List<String> PASTURE_SPRITES = List.of("FENCE_COMBO", "FENCE_D_COMBO", "AUX_EDGE_1X1", "AUX_MID_1X1", "AUX_BIG_2X2", "STORAGE_1X1", "GATE_TOP_3X3");

	/** The expected values were checked by hand against ROOM_PASTURE.java, pasture/Constructor.java and the two constructors it chooses between. */
	private static void pastures(List<AssetCatalog.Entry> entries, Path game, Path mods, GameParser parser, GameParser.GameVersion version, GameAssetIndex index, Path temp) throws Exception {
		AssetCatalog.Entry pasture = entryNamed(entries, "Pasture");
		expect("Pasture is available: " + pasture.unavailableReason(), pasture.available());
		if (!pasture.available()) {
			return;
		}
		AssetSchema schema = pasture.schema();
		expect("a pasture's files start with PASTURE_", pasture.type().fileName("X").equals("PASTURE_X"));
		expect("the pasture's tags: " + schema.dataTags().stream().map(TagSpec::key).toList(), schema.dataTags().stream().map(TagSpec::key).toList().equals(List.of("ICON", "ANIMAL", "INDOORS", "INDUSTRIES", "RESOURCES", "AREA_COSTS", "FLOOR", "MINI_COLOR",
				"MINI_COLOR_PATTERN", "WORK", "ITEMS", "SPRITES", "BONUS", "EXPERIENCE_BONUS", "UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE")));
		expect("FENCE isn't offered", schema.find("FENCE") == null);
		expect("STORAGE isn't offered", schema.find("STORAGE") == null);
		expect("ANIMAL names an animal file", schema.find("ANIMAL").reference() == TagSpec.Reference.ANIMALS && schema.find("ANIMAL").required());
		expectNested(schema, "INDOORS", TagSpec.Kind.BOOL, false, "false", null, null);
		expectNested(schema, "INDUSTRIES", TagSpec.Kind.BLOCK_LIST, true, null, null, null);
		expect("the player decides how many recipes", schema.tag("INDUSTRIES").shape().fixedCount() == 0);
		expectNested(schema, "INDUSTRIES.0.INDUSTRY.OUT", TagSpec.Kind.KEYED_ROWS, true, null, null, null);
		expectNested(schema, "INDUSTRIES.0.INDUSTRY.IN", TagSpec.Kind.KEYED_ROWS, false, "Nothing consumed", null, null);
		expectNested(schema, "DEGRADE_RATE", TagSpec.Kind.DECIMAL, false, "0.75", 0.0, 1.0);
		expectNested(schema, "WORK.SHIFT_OFFSET", TagSpec.Kind.DECIMAL, true, null, 0.0, 0.99);
		expectNested(schema, "ITEMS.0.STATS", TagSpec.Kind.DECIMALS, true, null, null, null);
		expect("a pasture has exactly 2 items", schema.tag("ITEMS").shape().fixedCount() == 2);
		expect("each with words in the text file", schema.isTextKey("ITEMS.1.NAME") && !schema.isTextKey("ITEMS.1.COSTS"));
		TagSpec stats = schema.textTags().stream().filter(t -> t.key().equals("STATS")).findFirst().orElseThrow();
		expect("all four stat blocks are shown, because the first and the last are named in the file", stats.shape().fixedCount() == 4 && stats.shape().namedSlots() == 4);
		expect("a stat's name isn't required on the tag, so the middle blocks can be empty", !schema.find("STATS.0.NAME").required() && !schema.find("STATS.3.DESC").required());
		expect("every sprite the constructors read is there", schema.tag("SPRITES").shape().children().stream().map(TagSpec::key).toList().equals(PASTURE_SPRITES));
		for (String sprite : PASTURE_SPRITES) {
			String key = "SPRITES." + sprite + ".";
			boolean gate = sprite.equals("GATE_TOP_3X3");
			expectNested(schema, "SPRITES." + sprite, TagSpec.Kind.BLOCK_LIST, !gate, null, null, null);
			expectNested(schema, key + "FRAMES", TagSpec.Kind.FRAMES, true, null, null, null);
			String folder = sprite.endsWith("COMBO") ? "sprite/game/combo/" : sprite.endsWith("2X2") ? "sprite/game/2x2/" : sprite.endsWith("3X3") ? "sprite/game/3x3/" : "sprite/game/1x1/";
			expect(sprite + " looks in the right folder", schema.find(key + "FRAMES").sprite().folder().equals(folder));
			int tile = sprite.endsWith("COMBO") ? 72 : sprite.endsWith("3X3") ? 60 : sprite.endsWith("2X2") ? 44 : 22;
			expect(sprite + " divides by " + tile, schema.find(key + "FRAMES").sprite().width() == tile);
		}
		expect("a pasture has no starter", schema.starter() == null);
		expect("its sounds have no shared starting entry", schema.sounds().size() == 3 && schema.sounds().stream().allMatch(slot -> slot.likeKey() == null));
		expect("and they are the room's three lookups", schema.sounds().stream().map(SoundEntries.Slot::valueKey).toList().equals(List.of(SoundEntries.ROOM_WORK.valueKey(), SoundEntries.ROOM_CLICK.valueKey(), SoundEntries.ROOM_AMBIENCE.valueKey())));
		expect("the notes say the middle stats are named by the game", schema.notes().stream().anyMatch(note -> note.contains("irrigation") && note.contains("efficiency")));
		expect("the notes say degrade rate has no effect", schema.notes().stream().anyMatch(note -> note.contains("never degrades")));
		for (String unread : List.of("FENCE", "STORAGE", "WORK.SOUND", "REQUIRES", "EMPLOYMENT")) {
			expect(unread + " isn't offered", schema.find(unread) == null);
		}

		List<String> classes = pastureClasses();
		AssetCatalog.Entry control = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve("pasture-all.jar"), classes)), "Pasture");
		expect("these classes are enough for Pasture: " + control.unavailableReason(), control.available());
		int at = 0;
		for (String gone : classes) {
			AssetCatalog.Entry entry = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve("pasture-without-" + at++ + ".jar"), classes.stream().filter(n -> !n.equals(gone)).toList())), "Pasture");
			expect("without " + gone + ", Pasture is disabled: " + entry.unavailableReason(), !entry.available() && entry.unavailableReason() != null);
		}
		// A class whose reads hold only locals has no key to rename. Removing it, above, is what turns the type off.
		for (String[] change : new String[][] {
				{ "settlement/room/food/pasture/ROOM_PASTURE", "INDOORS", "INDOORX" },
				{ "settlement/room/food/pasture/Constructor", "FENCE_COMBO", "FENCE_COMBX" },
				{ "settlement/room/food/pasture/Constructor", "AUX_EDGE_1X1", "AUX_EDGE_1X2" },
				{ "settlement/room/food/pasture/Constructor", "AUX_BIG_2X2", "AUX_BIG_2X3" },
				{ "settlement/room/food/pasture/ConstructorIndoor", "SPRITES", "SPRITEX" },
				{ "settlement/room/food/pasture/ConstructorIndoor$SpriteDep", "STORAGE_1X1", "STORAGE_1X2" },
				{ "settlement/room/food/pasture/ConstructorOutdoor", "GATE_TOP_3X3", "GATE_TOP_3X4" },
				{ "settlement/room/food/pasture/ConstructorOutdoor$SpriteDep", "GATE_TOP_3X3", "GATE_TOP_3X4" },
				{ "settlement/room/industry/module/Industry", "INDUSTRIES", "INDUSTRIEX" },
				{ "init/sprite/UI/Icons", "SCALE", "SCALX" },
				{ "init/sprite/game/SheetData", "SHADOW_LENGTH", "SHADOW_LENGTX" },
				{ "init/sprite/game/GameSheets", "FRAMES", "FRAMEX" } }) {
			AssetCatalog.Entry changed = entryNamed(AssetCatalog.load(patchedJar(game, temp.resolve("pasture-changed-" + change[1] + ".jar"), classes, change[0], change[1], change[2])), "Pasture");
			expect("a changed " + change[1] + " turns Pasture off: " + changed.unavailableReason(), !changed.available() && changed.unavailableReason() != null);
		}

		Path folder = temp.resolve("vanilla-pastures");
		Files.createDirectories(folder.resolve("data"));
		Files.createDirectories(folder.resolve("text"));
		int data = 0;
		int texts = 0;
		// The text file's BONUS block isn't read (RoomBlueprintIns.java:118-120). checkText doesn't warn on it, so it's named here.
		List<String> known = new java.util.ArrayList<>(schema.textTags().stream().map(TagSpec::key).toList());
		known.addAll(List.of("INFO", "BONUS"));
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile())) {
			Enumeration<? extends ZipEntry> files = zip.entries();
			while (files.hasMoreElements()) {
				ZipEntry entry = files.nextElement();
				String name = entry.getName().substring(entry.getName().lastIndexOf('/') + 1);
				boolean isData = entry.getName().matches("data/assets/init/room/PASTURE_[^/]+\\.txt");
				if (!isData && !entry.getName().matches("data/assets/text/room/PASTURE_[^/]+\\.txt")) {
					continue;
				}
				Path copy = folder.resolve(isData ? "data" : "text").resolve(name);
				try (InputStream in = zip.getInputStream(entry)) {
					Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
				}
				if (isData) {
					data++;
					List<AssetChecker.Finding> findings = new java.util.ArrayList<>(AssetVanillaCheck.check(schema, index, copy, parser));
					// Four sprites are one block, which RoomSpriteImp also reads (RoomSpriteImp.java:27-37). The check reads it as the first level, as the editor does.
					expect(name + " has its one-block sprites read, not kept", findings.stream().noneMatch(f -> f.message().contains("one block")));
					// FENCE is on five files. Nothing in the pasture loader reads it. PASTURE_GLOBDIEN doesn't have it.
					boolean fence = findings.removeIf(f -> "FENCE".equals(f.tag()) && f.severity() == AssetChecker.Severity.WARNING);
					if (name.equals("PASTURE_GLOBDIEN.txt")) {
						expect(name + " has no FENCE warning", !fence);
					} else {
						expect(name + " warns that FENCE is unread", fence);
					}
					expect(name + " has no other errors or warnings: " + findings, findings.isEmpty());
				} else {
					texts++;
					List<String> keys = parser.tags(copy).tags().stream().map(GameParser.TagValue::key).toList();
					expect(name + " text uses only known tags: " + keys, known.containsAll(keys));
					List<AssetChecker.Finding> findings = AssetVanillaCheck.checkText(schema, copy, parser);
					expect(name + " text reads as the schema says: " + findings, findings.isEmpty());
				}
			}
		}
		expect("all 6 vanilla pastures were checked: " + data, data == 6);
		expect("their text files were checked: " + texts, texts == 6);

		pastureRules(schema, index);
		pastureCreation(pasture, mods, game, parser, version);
	}

	private static String pastureFrames(String sprite) {
		if (sprite.endsWith("COMBO")) {
			return "COMBO_TABLES: 6";
		}
		if (sprite.equals("AUX_BIG_2X2")) {
			return "HUT: 0";
		}
		if (sprite.equals("GATE_TOP_3X3")) {
			return "PASTURE_GATE: 0";
		}
		return "STORAGE: 6";
	}

	private static Map<String, String> validPasture() {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("ICON", "24->resource->Gem->0");
		values.put("ANIMAL", "AUROCH");
		values.put("RESOURCES", "WOOD");
		values.put("AREA_COSTS", "0");
		values.put("FLOOR", "DIRT");
		values.put("MINI_COLOR", "10_20_30");
		values.put("WORK.SHIFT_OFFSET", "0.125");
		values.put("INDUSTRIES.0.INDUSTRY.OUT.MEAT.PLAYER", "1");
		for (int i = 0; i < 2; i++) {
			values.put("ITEMS." + i + ".COSTS", "1");
			values.put("ITEMS." + i + ".STATS", "0,1,0,0");
			values.put("ITEMS." + i + ".NAME", "Item " + i);
			values.put("ITEMS." + i + ".DESC", "About item " + i);
		}
		for (String sprite : PASTURE_SPRITES) {
			values.put("SPRITES." + sprite + ".0.FRAMES", pastureFrames(sprite));
		}
		values.put("SOUND.ROOM_WORK_", "DUMMY");
		values.put("SOUND.ROOM_CLICK_", "DUMMY");
		values.put("SOUND.ROOM_", "DUMMY");
		values.put("NAME", "Test Pasture");
		values.put("DESC", "It's for testing.");
		values.put("WORK.TITLE", "Herder");
		values.put("WORK.VERB", "herding");
		values.put("STATS.0.NAME", "Herders");
		values.put("STATS.0.DESC", "People tending the animals.");
		values.put("STATS.1.NAME", "");
		values.put("STATS.1.DESC", "");
		values.put("STATS.2.NAME", "");
		values.put("STATS.2.DESC", "");
		values.put("STATS.3.NAME", "Fertile ground");
		values.put("STATS.3.DESC", "Ground the animals can use.");
		return values;
	}

	private static Map<String, String> withoutGate(Map<String, String> base) {
		Map<String, String> values = new LinkedHashMap<>(base);
		values.keySet().removeIf(key -> key.startsWith("SPRITES.GATE_TOP_3X3"));
		return values;
	}

	private static void pastureRules(AssetSchema schema, GameAssetIndex index) {
		Map<String, String> good = validPasture();
		expect("a good pasture has no problems: " + AssetChecker.check(schema, index, good), AssetChecker.check(schema, index, good).isEmpty());
		for (String key : List.of("ICON", "ANIMAL", "MINI_COLOR", "WORK.SHIFT_OFFSET", "INDUSTRIES.0.INDUSTRY.OUT.MEAT.PLAYER", "ITEMS.0.COSTS", "ITEMS.1.STATS", "NAME", "DESC", "WORK.TITLE", "ITEMS.0.NAME", "ITEMS.1.DESC", "SPRITES.FENCE_COMBO.0.FRAMES", "SOUND.ROOM_WORK_")) {
			refused(schema, index, good, key, null);
		}
		refused(schema, index, good, "ANIMAL", "NOT_AN_ANIMAL");
		refused(schema, index, good, "ITEMS.0.STATS", "0,1,0");
		refused(schema, index, good, "ITEMS.0.STATS", "0,1,0,0,0");
		clean(schema, index, good, "DEGRADE_RATE", "0");
		refused(schema, index, good, "DEGRADE_RATE", "1.5");
		clean(schema, index, good, "INDOORS", "false");
		clean(schema, index, good, "INDOORS", "true");
		Map<String, String> outdoor = withoutGate(good);
		expect("an outdoor pasture without a gate is refused", AssetChecker.check(schema, index, outdoor).stream().anyMatch(f -> "SPRITES.GATE_TOP_3X3".equals(f.tag()) && f.blocksSaving()));
		Map<String, String> indoor = withoutGate(good);
		indoor.put("INDOORS", "true");
		expect("an indoor pasture without a gate is clean: " + AssetChecker.check(schema, index, indoor), AssetChecker.check(schema, index, indoor).isEmpty());
		refused(schema, index, good, "STATS.0.NAME", null);
		refused(schema, index, good, "STATS.0.DESC", null);
		refused(schema, index, good, "STATS.3.NAME", null);
		refused(schema, index, good, "STATS.3.DESC", null);
		clean(schema, index, good, "STATS.1.NAME", null);
		clean(schema, index, good, "STATS.1.DESC", null);
		clean(schema, index, good, "STATS.2.NAME", null);
		clean(schema, index, good, "STATS.0.NAME", "");
		Map<String, String> three = new LinkedHashMap<>(good);
		three.put("INDUSTRIES.0.INDUSTRY.OUT.LEATHER.PLAYER", "1");
		three.put("INDUSTRIES.0.INDUSTRY.OUT.LIVESTOCK.PLAYER", "1");
		expect("three outputs are allowed: " + AssetChecker.check(schema, index, three), AssetChecker.check(schema, index, three).isEmpty());
		Map<String, String> four = new LinkedHashMap<>(three);
		four.put("INDUSTRIES.0.INDUSTRY.OUT.WOOD.PLAYER", "1");
		expect("a fourth output is refused", AssetChecker.check(schema, index, four).stream().anyMatch(f -> "INDUSTRIES.0.INDUSTRY.OUT".equals(f.tag()) && f.blocksSaving()));
		Map<String, String> input = new LinkedHashMap<>(good);
		input.put("INDUSTRIES.0.INDUSTRY.IN.WOOD", "1");
		expect("an input is refused", AssetChecker.check(schema, index, input).stream().anyMatch(f -> "INDUSTRIES.0.INDUSTRY.IN".equals(f.tag()) && f.blocksSaving()));
		Map<String, String> second = new LinkedHashMap<>(good);
		second.put("INDUSTRIES.1.INDUSTRY.OUT.LEATHER.PLAYER", "1");
		expect("a second recipe with one output is allowed: " + AssetChecker.check(schema, index, second), AssetChecker.check(schema, index, second).isEmpty());
	}

	/** A new pasture, read back with the game's own reads at the paths the loader uses. */
	private static void pastureCreation(AssetCatalog.Entry pasture, Path mods, Path game, GameParser parser, GameParser.GameVersion version) throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Pasture Creation");
		Path root = mod.root();
		Path modVersion = AssetCreator.versionFolder(root, version);
		Path assets = modVersion.resolve("assets");
		GameAssetIndex index = GameAssetIndex.load(game, modVersion, parser);
		expect("a new pasture starts with nothing filled in", AssetForm.startingValues(pasture.schema(), index).isEmpty());

		Map<String, String> values = validPasture();
		values.put("INDOORS", "false");
		AssetCreator.Created made = AssetCreator.create(root, pasture.type(), pasture.schema(), "TESTPEN", values, parser, index);
		expect("the pasture's file has the prefix the game needs", made.dataFile().equals(assets.resolve("init/room/PASTURE_TESTPEN.txt")));
		expect("and its text file", made.textFile().equals(assets.resolve("text/room/PASTURE_TESTPEN.txt")));
		expect("and its sounds", made.soundFiles().size() == 2 && Files.readString(assets.resolve("audio/config/mono/PASTURE_TESTPEN.txt")).contains("ROOM_WORK_PASTURE_TESTPEN: DUMMY"));
		List<AssetChecker.Finding> findings = AssetVanillaCheck.check(pasture.schema(), index, made.dataFile(), parser);
		expect("the new pasture passes the vanilla check: " + findings, findings.isEmpty());
		List<AssetChecker.Finding> textFindings = AssetVanillaCheck.checkText(pasture.schema(), made.textFile(), parser);
		expect("its text passes too: " + textFindings, textFindings.isEmpty());

		GameParser.Node node = parser.root(made.dataFile());
		expect("the game reads the animal", "AUROCH".equals(node.value("ANIMAL")));
		expect("and that it's outdoors", !node.bool("INDOORS"));
		expect("the game reads the recipe's output", node.blocks("INDUSTRIES").get(0).block("INDUSTRY").block("OUT").block("MEAT").decimal("PLAYER", 0, 100000) == 1);
		expect("and the gate's frames, as written", java.util.Arrays.equals(node.block("SPRITES").blocks("GATE_TOP_3X3").get(0).values("FRAMES"), new String[] { "PASTURE_GATE: 0" }));
		expect("and a fence's frames", java.util.Arrays.equals(node.block("SPRITES").blocks("FENCE_COMBO").get(0).values("FRAMES"), new String[] { "COMBO_TABLES: 6" }));
		expect("and an item's stat list", node.blocks("ITEMS").get(0).decimals("STATS").length == 4);
		GameParser.Node text = parser.root(made.textFile());
		expect("the game's text file names the work", "Herder".equals(text.block("WORK").text("TITLE")) && "herding".equals(text.block("WORK").text("VERB")));
		expect("and the workers' name", "Herders".equals(text.blocks("STATS").get(0).text("NAME")) && "People tending the animals.".equals(text.blocks("STATS").get(0).text("DESC")));
		expect("and the fertile area's name", "Fertile ground".equals(text.blocks("STATS").get(3).text("NAME")));
		expect("and a blank name where the game names the stat", "".equals(text.blocks("STATS").get(1).text("NAME")));
		expect("and its items", "Item 1".equals(text.blocks("ITEMS").get(1).text("NAME")));

		expect("a vanilla pasture's name is refused", AssetCreator.problemWithName(pasture.type(), "AUR", index) != null);
		expect("a new pasture name works", AssetCreator.problemWithName(pasture.type(), "NEW", index) == null);
	}

	/** The expected values were checked by hand against ROOM_MINE.java, mine/Constructor.java, Furnisher.java and SheetData.java. */
	private static void mineTable(AssetCatalog.Entry mine) {
		AssetSchema schema = mine.schema();
		expect("a mine's files start with MINE_", mine.type().filePrefix().equals("MINE_") && mine.type().fileName("X").equals("MINE_X"));
		expect("the mine's tags: " + schema.dataTags().stream().map(TagSpec::key).toList(), schema.dataTags().stream().map(TagSpec::key).toList().equals(List.of("ICON", "MINABLE", "FLOOR", "YEILD_WORKER_DAILY", "STORAGE",
				"RESOURCES", "AREA_COSTS", "MINI_COLOR", "MINI_COLOR_PATTERN", "WORK", "ITEMS", "SPRITES", "BONUS", "EXPERIENCE_BONUS", "UPGRADES", "ENVIRONMENT_EMIT", "DEGRADE_RATE")));
		expectNested(schema, "MINABLE", TagSpec.Kind.KEY, true, null, null, null);
		expectNested(schema, "FLOOR", TagSpec.Kind.KEY, true, null, null, null);
		expectNested(schema, "YEILD_WORKER_DAILY", TagSpec.Kind.DECIMAL, true, null, 0.0, 1000.0);
		expectNested(schema, "STORAGE", TagSpec.Kind.INT, true, null, 4.0, 500.0);
		expect("STORAGE's maximum isn't enforced", !schema.tag("STORAGE").maxEnforced());
		expectNested(schema, "DEGRADE_RATE", TagSpec.Kind.DECIMAL, false, "0.75", 0.0, 1.0);
		expectNested(schema, "ITEMS.0.COSTS", TagSpec.Kind.DECIMALS, true, null, null, null);
		expectNested(schema, "ITEMS.0.STATS", TagSpec.Kind.DECIMALS, true, null, null, null);
		expect("a mine has exactly 2 items", schema.tag("ITEMS").shape().fixedCount() == 2);
		expect("each with words in the text file", schema.isTextKey("ITEMS.1.NAME") && !schema.isTextKey("ITEMS.1.COSTS"));
		expect("the text file's stats are a fixed 4", schema.textTags().stream().anyMatch(t -> t.key().equals("STATS") && t.shape().fixedCount() == 4));
		expect("every sprite the Constructor reads is there", schema.tag("SPRITES").shape().children().stream().map(TagSpec::key).toList().equals(MINE_SPRITES));
		for (String sprite : MINE_SPRITES) {
			String key = "SPRITES." + sprite + ".";
			expectNested(schema, "SPRITES." + sprite, TagSpec.Kind.BLOCK_LIST, true, null, null, null);
			expectNested(schema, key + "FRAMES", TagSpec.Kind.FRAMES, true, null, null, null);
			expect(sprite + " looks in the right folder", schema.find(key + "FRAMES").sprite().folder().equals(sprite.endsWith("2X2") ? "sprite/game/2x2/" : "sprite/game/1x1/"));
			expectNested(schema, key + "FPS", TagSpec.Kind.DECIMAL, false, "0", 0.0, 100000.0);
			expectNested(schema, key + "FPS_INTERVAL", TagSpec.Kind.DECIMAL, false, "1", 0.0, 1.0);
			expectNested(schema, key + "SHADOW_LENGTH", TagSpec.Kind.DECIMAL, false, "0", 0.0, 100.0);
			expectNested(schema, key + "SHADOW_HEIGHT", TagSpec.Kind.DECIMAL, false, "0", 0.0, 100.0);
			expectNested(schema, key + "CIRCULAR", TagSpec.Kind.BOOL, false, "false", null, null);
			expectNested(schema, key + "ROTATES", TagSpec.Kind.BOOL, false, "true", null, null);
			expectNested(schema, key + "TINT", TagSpec.Kind.BOOL, false, "true", null, null);
		}
		expect("a mine starts from a vanilla file's sprites", schema.starter() != null && schema.starter().tags().equals(List.of("SPRITES")));
		expect("a mine's sounds start from a mine's", schema.sounds().get(0).likeKey().contains("MINE_") && schema.sounds().get(0).valueKey().equals(SoundEntries.ROOM_WORK.valueKey()));
	}

	/** The expected values were checked by hand against Minable.java:43-63,75-90,109. */
	private static final List<String> MODE_CLASSES = List.of("game/faction/player/PBonusSetting", "menu/ScRandom", "game/boosting/BoostSpecs$ParseValue", "util/info/INFO",
			"game/faction/player/PTitles$IconMaker");

	/** Checked by hand against PBonusSetting.java:54-70,223-237, ScRandom.java:114-118, BoostSpecs.java:205-217, INFO.java:15-27 and PTitles.java:546-552. */
	private static void modeTable(AssetCatalog.Entry mode) {
		AssetSchema schema = mode.schema();
		expect("a mode has no prefix, and its text is in player/mode", mode.type().filePrefix().isEmpty() && "player/mode".equals(mode.type().textFolder()));
		expect("the mode's tags: " + schema.dataTags().stream().map(TagSpec::key).toList(), schema.dataTags().stream().map(TagSpec::key).toList().equals(List.of("ICON_I", "BOOST")));
		expectNested(schema, "ICON_I", TagSpec.Kind.INT, true, null, null, null);
		expectNested(schema, "BOOST", TagSpec.Kind.DECIMAL_MAP, true, null, null, null);
		expect("BOOST isn't a list of names", schema.tag("BOOST").reference() == null);
		expect("the text tags come from INFO", schema.textTags().stream().map(TagSpec::key).toList().equals(List.of("NAME", "NAMES", "DESC", "WIKI")));
		expect("TITLE, which nothing reads, isn't on the form", schema.find("TITLE") == null);
		for (TagSpec tag : schema.dataTags()) {
			expect(tag.key() + " is code-verified", tag.confidence() == TagSpec.Confidence.CODE_VERIFIED);
		}
		expect("the notes say boost names aren't checked", schema.notes().stream().anyMatch(n -> n.contains("Boost names aren't checked")));
	}

	private static void modeGate(Path game, Path temp) throws Exception {
		AssetCatalog.Entry control = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve("mode-all.jar"), MODE_CLASSES)), "Player mode");
		expect("these classes are enough for Player mode: " + control.unavailableReason(), control.available());
		int at = 0;
		for (String gone : MODE_CLASSES) {
			AssetCatalog.Entry entry = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve("mode-without-" + at++ + ".jar"), MODE_CLASSES.stream().filter(n -> !n.equals(gone)).toList())), "Player mode");
			expect("without " + gone + ", Player mode is disabled: " + entry.unavailableReason(), !entry.available() && entry.unavailableReason() != null);
		}
		for (String[] change : new String[][] { { "menu/ScRandom", "ICON_I", "ICON_X" }, { "game/faction/player/PBonusSetting", "setMode", "setModx" },
				{ "game/boosting/BoostSpecs$ParseValue", "Malformed", "Malformex" }, { "util/info/INFO", "DESC", "DESX" } }) {
			AssetCatalog.Entry changed = entryNamed(AssetCatalog.load(patchedJar(game, temp.resolve("mode-changed-" + at++ + ".jar"), MODE_CLASSES, change[0], change[1], change[2])), "Player mode");
			expect("a changed " + change[0] + " (" + change[1] + ") turns Player mode off: " + changed.unavailableReason(), !changed.available() && changed.unavailableReason() != null);
		}
	}

	/** Vanilla is the test. Its only warning is TITLE, which the handbook and every read of a mode file agree nothing uses. */
	private static void vanillaModes(Path game, AssetSchema schema, GameAssetIndex index, Path temp, GameParser parser) throws Exception {
		Path folder = temp.resolve("vanilla-modes");
		Files.createDirectories(folder.resolve("data"));
		Files.createDirectories(folder.resolve("text"));
		int modes = 0;
		int texts = 0;
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName().substring(entry.getName().lastIndexOf('/') + 1);
				String kind = entry.getName().matches("data/assets/init/player/mode/[^/]+\\.txt") ? "data"
						: entry.getName().matches("data/assets/text/player/mode/[^/]+\\.txt") ? "text" : null;
				if (kind == null) {
					continue;
				}
				Path copy = folder.resolve(kind).resolve(name);
				try (InputStream in = zip.getInputStream(entry)) {
					Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
				}
				if (kind.equals("data")) {
					modes++;
					List<AssetChecker.Finding> findings = new ArrayList<>(AssetVanillaCheck.check(schema, index, copy, parser));
					findings.removeIf(f -> "TITLE".equals(f.tag()) && f.severity() == AssetChecker.Severity.WARNING);
					expect(name + " mode has no errors, and no warning but TITLE's: " + findings, findings.isEmpty());
				} else {
					texts++;
					List<AssetChecker.Finding> findings = AssetVanillaCheck.checkText(schema, copy, parser);
					expect(name + " mode text reads as INFO reads it: " + findings, findings.isEmpty());
				}
			}
		}
		expect("all 6 vanilla modes were checked: " + modes, modes == 6);
		expect("their text files were checked: " + texts, texts == 6);
	}

	private static Map<String, String> validMode() {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("ICON_I", "13");
		values.put("BOOST.CIVIC_GOV>MUL", "1.6");
		values.put("BOOST.CIVIC_IMMIGRATION>MUL", "0");
		values.put("NAME", "Test Mode");
		values.put("DESC", "It's for testing.");
		return values;
	}

	private static void modeRules(AssetSchema schema, GameAssetIndex index) {
		Map<String, String> good = validMode();
		expect("a good mode has no problems: " + AssetChecker.check(schema, index, good), AssetChecker.check(schema, index, good).isEmpty());
		for (String key : List.of("ICON_I", "NAME", "DESC")) {
			refused(schema, index, good, key, null);
		}
		// modeIcons is new SPRITE[5*7] (PTitles.java:547), indexed by ICON_I (ScRandom.java:118).
		clean(schema, index, good, "ICON_I", "0");
		clean(schema, index, good, "ICON_I", "34");
		refused(schema, index, good, "ICON_I", "35");
		refused(schema, index, good, "ICON_I", "-1");
		refused(schema, index, good, "ICON_I", "1.5");
		// ParseValue stops the game on a key without >ADD or >MUL (BoostSpecs.java:205-217), and reads the amount with d.
		refused(schema, index, good, "BOOST.CIVIC_GOV", "1");
		refused(schema, index, good, "BOOST.CIVIC_GOV>MUL", "lots");
		clean(schema, index, good, "BOOST.CIVIC_LANDING>ADD", "-2");
		// An unknown boost is logged and skipped (PBonusSetting.java:89-92), and the names come from code, so it isn't reported.
		clean(schema, index, good, "BOOST.NOT_A_REAL_BOOST>ADD", "1");
		// An empty BOOST is fine, as in vanilla's 10_GREAT. The writer still writes the block (see modeCreation).
		Map<String, String> noBoost = new LinkedHashMap<>(good);
		noBoost.keySet().removeIf(key -> key.startsWith("BOOST."));
		expect("a mode with no boosts has no problems: " + AssetChecker.check(schema, index, noBoost), AssetChecker.check(schema, index, noBoost).isEmpty());
	}

	private static void modeCreation(AssetCatalog.Entry entry, ModEntry mod, GameParser parser, Path game) throws Exception {
		AssetSchema schema = entry.schema();
		GameAssetIndex index = GameAssetIndex.load(game, AssetCreator.versionFolder(mod.root(), parser.version()), parser);
		AssetCreator.Created created = AssetCreator.create(mod.root(), entry.type(), schema, "60_TEST_MODE", validMode(), parser, index);
		String folder = created.dataFile().getParent().toString().replace('\\', '/');
		expect("the mode is written to init/player/mode: " + folder, folder.endsWith("/assets/init/player/mode") && created.dataFile().getFileName().toString().equals("60_TEST_MODE.txt"));
		expect("with its text in text/player/mode", created.textFile() != null && created.textFile().getParent().toString().replace('\\', '/').endsWith("/assets/text/player/mode"));
		List<AssetChecker.Finding> data = AssetVanillaCheck.check(schema, index, created.dataFile(), parser);
		expect("the new mode reads back with no findings: " + data, data.isEmpty());
		List<AssetChecker.Finding> text = AssetVanillaCheck.checkText(schema, created.textFile(), parser);
		expect("and so does its text: " + text, text.isEmpty());
		GameParser.Node root = parser.root(created.dataFile());
		expect("the game reads ICON_I and the boosts back", root.has("ICON_I") && root.isBlock("BOOST") && root.block("BOOST").has("CIVIC_GOV>MUL") && root.block("BOOST").has("CIVIC_IMMIGRATION>MUL"));
		// PBonusSetting reads NAME from the top of every mode's text file (PBonusSetting.java:56-59). Written inside INFO,
		// it stopped every new game, whichever mode was chosen.
		GameParser.Node textRoot = parser.root(created.textFile());
		expect("the mode's text is written at the top, not in INFO: " + Files.readString(created.textFile()),
				textRoot.has("NAME") && textRoot.has("DESC") && !textRoot.has("INFO") && textRoot.text("NAME").equals("Test Mode"));
		expect("the schema says so", schema.textAtRoot());

		// A file already wrapped in INFO is reported without blocking, and a save, even of nothing, moves it to the top.
		Files.writeString(created.textFile(), "INFO: {\n\tNAME: \"Test Mode\",\n\tDESC: \"It's for testing.\",\n},\n");
		expect("the wrapped file is refused by the text check", AssetVanillaCheck.checkText(schema, created.textFile(), parser).stream().anyMatch(f -> "NAME".equals(f.tag()) && f.severity() == AssetChecker.Severity.ERROR));
		syx.core.AssetEditing.Location location = syx.core.AssetEditing.locate(mod.root(), parser.version(), created.dataFile(), List.of(entry), index);
		syx.core.AssetEditing.Opened opened = syx.core.AssetEditing.load(location, parser, index);
		GameAssetIndex editing = index.editing(entry.type(), opened.fileName());
		syx.core.AssetForm.Status status = syx.core.AssetForm.status(schema, editing, opened.values(), opened.scope(opened.values(), opened.kept()), null, null, false, opened.misplacedText(), opened.kept(), null);
		expect("opening it warns that NAME is inside INFO, without blocking: " + status.findings(), status.canSave()
				&& status.findings().stream().anyMatch(f -> "NAME".equals(f.tag()) && f.severity() == AssetChecker.Severity.WARNING && f.message().contains("INFO")));
		AssetCreator.Saved saved = AssetCreator.save(opened, opened.values(), opened.values(), false, parser, editing);
		GameParser.Node fixed = parser.root(created.textFile());
		expect("saving it unchanged writes the text at the top: " + saved.written() + " " + Files.readString(created.textFile()),
				fixed.has("NAME") && fixed.has("DESC") && !fixed.has("INFO") && fixed.text("DESC").equals("It's for testing."));
		expect("and the text check is clean again", AssetVanillaCheck.checkText(schema, created.textFile(), parser).isEmpty());

		// Json.json("BOOST") stops the game when the block is missing (PBonusSetting.java:228), so an empty one is still written.
		Map<String, String> noBoost = validMode();
		noBoost.keySet().removeIf(key -> key.startsWith("BOOST."));
		AssetCreator.Created empty = AssetCreator.create(mod.root(), entry.type(), schema, "61_EMPTY_MODE", noBoost, parser, index);
		GameParser.Node emptyRoot = parser.root(empty.dataFile());
		expect("a mode with no boosts still has its BOOST block: " + Files.readString(empty.dataFile()), emptyRoot.isBlock("BOOST"));
		expect("and reads back with no findings", AssetVanillaCheck.check(schema, index, empty.dataFile(), parser).isEmpty());
	}

	private static void minableTable(AssetCatalog.Entry minable) {
		AssetSchema schema = minable.schema();
		expect("a deposit has no prefix and no text file", minable.type().filePrefix().isEmpty() && minable.type().textFolder() == null && schema.textTags().isEmpty());
		expect("the deposit's tags: " + schema.dataTags().stream().map(TagSpec::key).toList(), schema.dataTags().stream().map(TagSpec::key).toList().equals(List.of("RESOURCE", "ON_EVERY_MAP", "COLOR", "MINIMAP_COLOR", "FERTILITY_INCREASE", "SPRITE", "TERRAIN", "OCCURENCE")));
		expectNested(schema, "RESOURCE", TagSpec.Kind.KEY, true, null, null, null);
		expectNested(schema, "ON_EVERY_MAP", TagSpec.Kind.BOOL, true, null, null, null);
		expectNested(schema, "COLOR", TagSpec.Kind.COLOR, true, null, 0.0, 255.0);
		expectNested(schema, "MINIMAP_COLOR", TagSpec.Kind.COLOR, true, null, 0.0, 255.0);
		expectNested(schema, "FERTILITY_INCREASE", TagSpec.Kind.DECIMAL, true, null, -1.0, 1.0);
		expectNested(schema, "SPRITE", TagSpec.Kind.SPRITE, true, null, null, null);
		expectNested(schema, "TERRAIN", TagSpec.Kind.DECIMAL_MAP, false, "0 for every entry", 0.0, 1.0);
		expectNested(schema, "OCCURENCE", TagSpec.Kind.DECIMAL, false, "1", 0.0, 1000.0);
		TagSpec.Sprite image = schema.tag("SPRITE").sprite();
		expect("the deposit's image size is read from the jar: " + image, image.width() == 364 && image.minHeight() == 94 && image.folder().equals("sprite/resource/minable/"));
	}

	private static void mineGate(Path game, Path temp) throws Exception {
		AssetCatalog.Entry control = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve("mine-all.jar"), MINE_CLASSES)), "Mine");
		expect("these classes are enough for Mine: " + control.unavailableReason(), control.available());
		AssetCatalog.Entry minableControl = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve("minable-all.jar"), MINABLE_CLASSES)), "Minable resource");
		expect("these classes are enough for Minable resource: " + minableControl.unavailableReason(), minableControl.available());
		int at = 0;
		for (String gone : MINE_CLASSES) {
			AssetCatalog.Entry entry = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve("mine-without-" + at++ + ".jar"), MINE_CLASSES.stream().filter(n -> !n.equals(gone)).toList())), "Mine");
			expect("without " + gone + ", Mine is disabled: " + entry.unavailableReason(), !entry.available() && entry.unavailableReason() != null);
		}
		for (String gone : MINABLE_CLASSES) {
			AssetCatalog.Entry entry = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve("minable-without-" + at++ + ".jar"), MINABLE_CLASSES.stream().filter(n -> !n.equals(gone)).toList())), "Minable resource");
			expect("without " + gone + ", Minable resource is disabled: " + entry.unavailableReason(), !entry.available() && entry.unavailableReason() != null);
		}
		for (String[] change : new String[][] { { "settlement/room/industry/mine/ROOM_MINE", "STORAGE", "STORAGX" }, { "settlement/room/industry/mine/ROOM_MINE", "YEILD_WORKER_DAILY", "YIELD_WORKER_DAILY" },
				{ "settlement/room/industry/mine/Constructor", "SPRITES", "SPRITEX" }, { "settlement/room/industry/mine/Constructor", "WORK_1X1", "WORK_1X2" },
				{ "init/sprite/game/SheetData", "SHADOW_LENGTH", "SHADOW_LENGTX" }, { "init/sprite/game/GameSheets", "FRAMES", "FRAMEX" } }) {
			AssetCatalog.Entry changed = entryNamed(AssetCatalog.load(patchedJar(game, temp.resolve("mine-changed-" + change[1] + ".jar"), MINE_CLASSES, change[0], change[1], change[2])), "Mine");
			expect("a changed " + change[1] + " turns Mine off: " + changed.unavailableReason(), !changed.available() && changed.unavailableReason() != null);
		}
		AssetCatalog.Entry changed = entryNamed(AssetCatalog.load(patchedJar(game, temp.resolve("minable-changed.jar"), MINABLE_CLASSES, "init/resources/Minable", "FERTILITY_INCREASE", "FERTILITY_INCREASX")), "Minable resource");
		expect("a changed read turns Minable resource off: " + changed.unavailableReason(), !changed.available() && changed.unavailableReason() != null);
	}

	/** Vanilla is the test. Its only warning is SPRITE, which the handbook and a search of the code both say nothing reads. */
	private static void vanillaMines(Path game, AssetSchema mine, AssetSchema minable, GameAssetIndex index, Path temp, GameParser parser) throws Exception {
		Path folder = temp.resolve("vanilla-mines");
		Files.createDirectories(folder.resolve("data"));
		Files.createDirectories(folder.resolve("text"));
		Files.createDirectories(folder.resolve("minable"));
		int mines = 0;
		int texts = 0;
		int minables = 0;
		List<String> known = new java.util.ArrayList<>(mine.textTags().stream().map(TagSpec::key).toList());
		known.add("INFO");
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName().substring(entry.getName().lastIndexOf('/') + 1);
				String kind = entry.getName().matches("data/assets/init/room/MINE_[^/]+\\.txt") ? "data"
						: entry.getName().matches("data/assets/text/room/MINE_[^/]+\\.txt") ? "text"
						: entry.getName().matches("data/assets/init/resource/minable/[^/]+\\.txt") ? "minable" : null;
				if (kind == null) {
					continue;
				}
				Path copy = folder.resolve(kind).resolve(name);
				try (InputStream in = zip.getInputStream(entry)) {
					Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
				}
				switch (kind) {
				case "data" -> {
					mines++;
					List<AssetChecker.Finding> findings = new java.util.ArrayList<>(AssetVanillaCheck.check(mine, index, copy, parser));
					expect(name + " warns that SPRITE is unread", findings.removeIf(f -> "SPRITE".equals(f.tag()) && f.severity() == AssetChecker.Severity.WARNING));
					expect(name + " has no other errors or warnings: " + findings, findings.isEmpty());
					// A block's icons are checked too, so a good result above means something.
					String original = Files.readString(copy);
					Files.writeString(copy, original.replaceFirst("ICON\\s*:\\s*\\{[^}]*\\},?", "ICON: {\n\tBG: 32->BG->9999,\n\tFG: 24->nothing->Here->0,\n},\n"));
					List<AssetChecker.Finding> broken = AssetVanillaCheck.check(mine, index, copy, parser);
					expect(name + ": a position past the end of a block's sheet is noticed: " + broken, broken.stream().anyMatch(f -> "ICON.BG".equals(f.tag()) && f.severity() == AssetChecker.Severity.WARNING));
					expect(name + ": a block's missing sheet is noticed", broken.stream().anyMatch(f -> "ICON.FG".equals(f.tag()) && f.severity() == AssetChecker.Severity.ERROR));
					Files.writeString(copy, original);
				}
				case "text" -> {
					texts++;
					List<String> keys = parser.tags(copy).tags().stream().map(GameParser.TagValue::key).toList();
					expect(name + " text uses only known tags: " + keys, known.containsAll(keys));
					List<AssetChecker.Finding> findings = AssetVanillaCheck.checkText(mine, copy, parser);
					expect(name + " text reads as the schema says: " + findings, findings.isEmpty());
				}
				default -> {
					minables++;
					List<AssetChecker.Finding> findings = AssetVanillaCheck.check(minable, index, copy, parser);
					expect(name + " deposit has no errors or warnings: " + findings, findings.isEmpty());
				}
				}
			}
		}
		expect("all 6 vanilla mines were checked: " + mines, mines == 6);
		expect("their text files were checked: " + texts, texts == 6);
		expect("all 6 vanilla deposits were checked: " + minables, minables == 6);
	}

	private static Map<String, String> validMine(String minable) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("ICON", "24->resource->Gem->0");
		values.put("MINABLE", minable);
		values.put("FLOOR", "MINE");
		values.put("YEILD_WORKER_DAILY", "1.5");
		values.put("STORAGE", "75");
		values.put("RESOURCES", "STONE,METAL");
		values.put("AREA_COSTS", "0,0");
		values.put("MINI_COLOR", "10_20_30");
		values.put("WORK.SHIFT_OFFSET", "0.125");
		for (int i = 0; i < 2; i++) {
			values.put("ITEMS." + i + ".COSTS", "1,2");
			values.put("ITEMS." + i + ".STATS", "0,0,0,0");
			values.put("ITEMS." + i + ".NAME", "Item " + i);
			values.put("ITEMS." + i + ".DESC", "About item " + i);
		}
		for (String sprite : MINE_SPRITES) {
			values.put("SPRITES." + sprite + ".0.FRAMES", sprite.endsWith("2X2") ? "HUT: 0" : "STORAGE: 6");
		}
		values.put("SOUND.ROOM_WORK_", "impact->Mine*");
		values.put("SOUND.ROOM_CLICK_", "DUMMY");
		values.put("SOUND.ROOM_", "DUMMY");
		values.put("NAME", "Test Mine");
		values.put("DESC", "It's for testing.");
		values.put("WORK.TITLE", "Tester");
		values.put("WORK.VERB", "testing");
		for (int i = 0; i < 2; i++) {
			values.put("STATS." + i + ".NAME", "Stat " + i);
			values.put("STATS." + i + ".DESC", "About stat " + i);
		}
		return values;
	}

	private static void warnsOnly(AssetSchema schema, GameAssetIndex index, Map<String, String> base, String key, String value, String mentions) {
		List<AssetChecker.Finding> findings = changed(schema, index, base, key, value);
		expect(key + "=" + value + " warns about " + mentions + " without blocking: " + findings, findings.stream().noneMatch(AssetChecker.Finding::blocksSaving)
				&& findings.stream().anyMatch(f -> f.message().contains(mentions)));
	}

	private static void mineRules(AssetCatalog.Entry mine, GameAssetIndex index) {
		AssetSchema schema = mine.schema();
		Map<String, String> good = validMine("STONE");
		expect("a good mine has no problems: " + AssetChecker.check(schema, index, good), AssetChecker.check(schema, index, good).isEmpty());
		for (String key : List.of("ICON", "MINABLE", "FLOOR", "YEILD_WORKER_DAILY", "STORAGE", "MINI_COLOR", "WORK.SHIFT_OFFSET", "ITEMS.0.COSTS", "ITEMS.1.STATS", "NAME", "DESC", "WORK.TITLE", "STATS.1.DESC", "ITEMS.0.NAME", "ITEMS.1.DESC", "SPRITES.WORK_1X1.0.FRAMES")) {
			refused(schema, index, good, key, null);
		}
		refused(schema, index, good, "MINABLE", "NOT_A_DEPOSIT");
		clean(schema, index, good, "MINABLE", "_STONE");
		refused(schema, index, good, "FLOOR", "NOT_A_FLOOR");
		clean(schema, index, good, "FLOOR", "MINE_MUD");
		refused(schema, index, good, "YEILD_WORKER_DAILY", "1000.5");
		clean(schema, index, good, "YEILD_WORKER_DAILY", "1000");
		clean(schema, index, good, "YEILD_WORKER_DAILY", "0");
		refused(schema, index, good, "STORAGE", "3");
		clean(schema, index, good, "STORAGE", "4");
		clean(schema, index, good, "STORAGE", "500");
		warnsOnly(schema, index, good, "STORAGE", "501", "doesn't enforce");
		// Json.ds(key, size) stops the game on any other length (Json.java:271-277).
		refused(schema, index, good, "ITEMS.0.COSTS", "1");
		refused(schema, index, good, "ITEMS.0.COSTS", "1,2,3");
		refused(schema, index, good, "ITEMS.1.STATS", "0,0,0");
		refused(schema, index, good, "ITEMS.1.STATS", "0,0,0,0,0");
		refused(schema, index, good, "ITEMS.0.STATS", "0,x,0,0");
		Map<String, String> noResources = new LinkedHashMap<>(good);
		noResources.remove("RESOURCES");
		noResources.remove("AREA_COSTS");
		noResources.put("ITEMS.0.COSTS", "");
		noResources.put("ITEMS.1.COSTS", "");
		expect("with no resources an item costs nothing: " + AssetChecker.check(schema, index, noResources), AssetChecker.check(schema, index, noResources).isEmpty());
		expect("a blank list with resources is still to be filled in, not wrong", changed(schema, index, good, "ITEMS.0.COSTS", "").stream().anyMatch(f -> f.tag().equals("ITEMS.0.COSTS") && f.severity() == AssetChecker.Severity.MISSING));
		// A sprite's levels: any number from 1, and more can be added (RoomSpriteImp.java:27-37).
		clean(schema, index, good, "SPRITES.WORK_1X1.1.FRAMES", "STORAGE: 5");
		expect("the game needs a level of each sprite", changed(schema, index, good, "SPRITES.WORK_1X1.0.FRAMES", null).stream().anyMatch(f -> f.severity() == AssetChecker.Severity.MISSING && f.tag().equals("SPRITES.WORK_1X1")));
		refused(schema, index, good, "SPRITES.WORK_1X1.0.FPS", "-1");
		refused(schema, index, good, "SPRITES.WORK_1X1.0.FPS_INTERVAL", "1.5");
		refused(schema, index, good, "SPRITES.WORK_1X1.0.SHADOW_LENGTH", "101");
		refused(schema, index, good, "SPRITES.WORK_1X1.0.CIRCULAR", "maybe");
		refused(schema, index, good, "SPRITES.WORK_1X1.0.COLOR", "1_2");
		clean(schema, index, good, "SPRITES.WORK_1X1.0.COLOR", "1_2_3");
		clean(schema, index, good, "SPRITES.WORK_1X1.0.FPS", "15");
		// Upgrades work as they do for a farm.
		Map<String, String> levels = new LinkedHashMap<>(good);
		levels.put("UPGRADES.0.RESOURCE_MASK", "1");
		levels.put("UPGRADES.0.BOOST", "0");
		levels.put("UPGRADES.1.RESOURCE_MASK", "1");
		levels.put("UPGRADES.1.BOOST", "1");
		expect("upgrades with short masks are refused", AssetChecker.check(schema, index, levels).stream().anyMatch(f -> f.tag().startsWith("UPGRADES") && f.blocksSaving()));
		levels.put("UPGRADES.1.RESOURCE_MASK", "1,1");
		expect("and fine once one covers the resources: " + AssetChecker.check(schema, index, levels), AssetChecker.check(schema, index, levels).isEmpty());
	}

	private static Map<String, String> validMinable(String image) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("RESOURCE", "STONE");
		values.put("ON_EVERY_MAP", "true");
		values.put("COLOR", "255_255_255");
		values.put("MINIMAP_COLOR", "10_20_30");
		values.put("FERTILITY_INCREASE", "-0.5");
		values.put("SPRITE", image);
		values.put("TERRAIN.MOUNTAIN", "0.5");
		return values;
	}

	private static void minableRules(AssetCatalog.Entry minable, Path game, Path mods, GameParser.GameVersion version, GameParser parser) throws Exception {
		AssetSchema schema = minable.schema();
		ModEntry mod = ModCreator.create(mods, game, "Deposit Rules");
		Path assets = AssetCreator.versionFolder(mod.root(), version).resolve("assets");
		Path sprites = assets.resolve("sprite/resource/minable");
		Files.createDirectories(sprites);
		Files.write(sprites.resolve("MyOre.png"), pngHeader(364, 94));
		Files.write(sprites.resolve("Taller.png"), pngHeader(364, 188));
		Files.write(sprites.resolve("TooWide.png"), pngHeader(365, 94));
		Files.write(sprites.resolve("TooShort.png"), pngHeader(364, 93));
		GameAssetIndex index = GameAssetIndex.load(game, assets.getParent(), parser);
		Map<String, String> good = validMinable("MyOre");
		expect("a good deposit has no problems: " + AssetChecker.check(schema, index, good), AssetChecker.check(schema, index, good).isEmpty());
		for (String key : List.of("RESOURCE", "ON_EVERY_MAP", "COLOR", "MINIMAP_COLOR", "FERTILITY_INCREASE", "SPRITE")) {
			refused(schema, index, good, key, null);
		}
		refused(schema, index, good, "RESOURCE", "NOT_A_RESOURCE");
		refused(schema, index, good, "FERTILITY_INCREASE", "-1.5");
		refused(schema, index, good, "FERTILITY_INCREASE", "1.5");
		clean(schema, index, good, "FERTILITY_INCREASE", "1");
		clean(schema, index, good, "SPRITE", "Taller");
		clean(schema, index, good, "SPRITE", "Clay");
		refused(schema, index, good, "SPRITE", "TooWide");
		refused(schema, index, good, "SPRITE", "TooShort");
		refused(schema, index, good, "SPRITE", "NoSuchImage");
		refused(schema, index, good, "OCCURENCE", "1001");
		clean(schema, index, good, "OCCURENCE", "0");
		clean(schema, index, good, "OCCURENCE", "1000");
		refused(schema, index, good, "TERRAIN.MOUNTAIN", "1.5");
		clean(schema, index, good, "TERRAIN.*", "0.5");
		warnsOnly(schema, index, good, "TERRAIN.MOUNTAIN", null, "divides by zero");
		warnsOnly(schema, index, good, "TERRAIN.MOUNTAIN", "0", "divides by zero");
		warnsOnly(schema, index, good, "TERRAIN.SWAMP", "0.5", "ignores this entry");

		// The game stops above 31 files, counting vanilla's 6 (Minable.java:75).
		Path folder = assets.resolve("init/resource/minable");
		Files.createDirectories(folder);
		for (int i = 0; i < 24; i++) {
			Files.writeString(folder.resolve("DEPOSIT" + i + ".txt"), "");
		}
		GameAssetIndex thirty = GameAssetIndex.load(game, assets.getParent(), parser);
		expect("30 deposits leave room for another: " + thirty.options(TagSpec.Reference.MINABLES).size(), thirty.options(TagSpec.Reference.MINABLES).size() == 30 && AssetChecker.check(schema, thirty, good).isEmpty());
		Files.writeString(folder.resolve("DEPOSIT24.txt"), "");
		GameAssetIndex full = GameAssetIndex.load(game, assets.getParent(), parser);
		expect("a 32nd is warned about, without blocking: " + AssetChecker.check(schema, full, good), AssetChecker.check(schema, full, good).stream().anyMatch(f -> f.message().contains("more than 31")) && AssetChecker.check(schema, full, good).stream().noneMatch(AssetChecker.Finding::blocksSaving));
		expect("editing one of the 31 isn't warned about", AssetChecker.check(schema, full.editing(minable.type(), "DEPOSIT0"), good).isEmpty());
	}

	/** The game's own sums for how many rows a sheet has (SheetType.java:70-108,227-296), and what it does with a frame (GameSheets.java:140-196). */
	private static void frameRules(AssetSchema schema, Path game, Path mods, GameParser.GameVersion version) throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Frame Rules");
		Path assets = AssetCreator.versionFolder(mod.root(), version).resolve("assets");
		Path ones = assets.resolve("sprite/game/1x1");
		Path twos = assets.resolve("sprite/game/2x2");
		Files.createDirectories(ones);
		Files.createDirectories(twos);
		Files.write(ones.resolve("Single.png"), pngHeader(44, 22));
		Files.write(ones.resolve("TwoRows.png"), pngHeader(88, 44));
		Files.write(ones.resolve("TallTiles.png"), pngHeader(44, 38));
		Files.write(ones.resolve("BadWidth.png"), pngHeader(46, 22));
		Files.write(ones.resolve("BadHeight.png"), pngHeader(44, 23));
		Files.write(twos.resolve("Big.png"), pngHeader(88, 88));
		Files.write(twos.resolve("BadBig.png"), pngHeader(90, 44));
		GameAssetIndex index = GameAssetIndex.load(game, assets.getParent());
		String one = "sprite/game/1x1/";
		String two = "sprite/game/2x2/";
		expect("a 22 pixel row is one", index.sheetRows(one, "Single").rows() == 1 && index.sheetRows(one, "Single").validSize());
		expect("two of them are two", index.sheetRows(one, "TwoRows").rows() == 2);
		expect("a row can be taller than a tile: " + index.sheetRows(one, "TallTiles"), index.sheetRows(one, "TallTiles").rows() == 2 && index.sheetRows(one, "TallTiles").validSize());
		expect("a width the game refuses", index.sheetRows(one, "BadWidth").exists() && !index.sheetRows(one, "BadWidth").validSize());
		expect("a height the game refuses", !index.sheetRows(one, "BadHeight").validSize());
		expect("2x2 tiles are 44 pixels", index.sheetRows(two, "Big").rows() == 2 && !index.sheetRows(two, "BadBig").validSize());
		expect("a sheet that isn't there", !index.sheetRows(one, "Nothing").exists());
		expect("a mod's sheet is offered, and a shared one isn't: " + index.spriteOptions(one), index.spriteOptions(one).contains("Single") && index.spriteOptions(one).contains("STORAGE") && !index.spriteOptions(one).contains("_OVERLAY"));

		Map<String, String> good = validMine("STONE");
		String key = "SPRITES.WORK_1X1.0.FRAMES";
		clean(schema, index, good, key, "Single: 0");
		clean(schema, index, good, key, "Single:0");
		clean(schema, index, good, key, "-");
		clean(schema, index, good, key, "TwoRows: 1\nSingle: 0\n-");
		clean(schema, index, good, key, "TallTiles: 1");
		refused(schema, index, good, key, "Single");
		refused(schema, index, good, key, "Single: x");
		refused(schema, index, good, key, "a: b: 1");
		refused(schema, index, good, key, "BadWidth: 0");
		refused(schema, index, good, key, "BadHeight: 0");
		warnsOnly(schema, index, good, key, "Nothing: 0", "no image called");
		warnsOnly(schema, index, good, key, "Single: 1", "has 1 row");
		warnsOnly(schema, index, good, key, "TwoRows: 2", "has 2 rows");
		warnsOnly(schema, index, good, key, "Single: -1", "numbered from 0");
		// Vanilla refiners have levels and whole sprites with no frames, so one isn't reported.
		clean(schema, index, good, key, "");
		String big = "SPRITES.AUX_BIG_2X2.0.FRAMES";
		clean(schema, index, good, big, "Big: 1");
		refused(schema, index, good, big, "BadBig: 0");
		warnsOnly(schema, index, good, big, "Single: 0", "no image called");
	}

	/** The whole chain: a new resource's deposit, and a mine of it, with every file through the game's parser. */
	private static void mineCreation(List<AssetCatalog.Entry> entries, AssetCatalog.Entry mine, AssetCatalog.Entry minable, Path mods, Path game, GameParser parser, GameParser.GameVersion version) throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Mine Creation");
		Path root = mod.root();
		Path modVersion = AssetCreator.versionFolder(root, version);
		Path assets = modVersion.resolve("assets");
		GameAssetIndex index = GameAssetIndex.load(game, modVersion, parser);

		Map<String, String> start = AssetEditing.startingValues(mine.schema(), index);
		expect("a new mine starts with every sprite: " + start.size(), MINE_SPRITES.stream().allMatch(sprite -> start.containsKey("SPRITES." + sprite + ".0.FRAMES")));
		expect("and takes nothing else", start.keySet().stream().allMatch(key -> key.startsWith("SPRITES.")));
		expect("a type with no starter starts empty", AssetEditing.startingValues(minable.schema(), index).isEmpty());
		expect("an index that wasn't given the parser has nothing to offer", AssetEditing.startingValues(mine.schema(), GameAssetIndex.load(game, modVersion)).isEmpty());

		Map<String, String> deposit = validMinable("Clay");
		AssetCreator.Created madeDeposit = AssetCreator.create(root, minable.type(), minable.schema(), "TEST_DEPOSIT", deposit, parser, index);
		expect("the deposit's file is in the right place, with no text file", madeDeposit.dataFile().equals(assets.resolve("init/resource/minable/TEST_DEPOSIT.txt")) && madeDeposit.textFile() == null && madeDeposit.soundFiles().isEmpty());
		List<AssetChecker.Finding> depositFindings = AssetVanillaCheck.check(minable.schema(), index, madeDeposit.dataFile(), parser);
		expect("the new deposit passes the vanilla check: " + depositFindings, depositFindings.isEmpty());

		GameAssetIndex after = GameAssetIndex.load(game, modVersion, parser);
		expect("a mine can now name it", after.options(TagSpec.Reference.MINABLES).contains("TEST_DEPOSIT"));
		Map<String, String> values = validMine("TEST_DEPOSIT");
		values.keySet().removeIf(key -> key.startsWith("SPRITES."));
		values.putAll(start);
		values.put("SPRITES.CONVEYOR_1X1.0.COLOR", "1_2_3");
		AssetCreator.Created made = AssetCreator.create(root, mine.type(), mine.schema(), "TEST_MINE", values, parser, after);
		expect("the mine's file has the prefix the game needs", made.dataFile().equals(assets.resolve("init/room/MINE_TEST_MINE.txt")));
		expect("and its text file", made.textFile().equals(assets.resolve("text/room/MINE_TEST_MINE.txt")));
		expect("and its sounds", made.soundFiles().size() == 2 && Files.readString(assets.resolve("audio/config/mono/MINE_TEST_MINE.txt")).contains("ROOM_WORK_MINE_TEST_MINE: impact->Mine*"));
		List<AssetChecker.Finding> findings = AssetVanillaCheck.check(mine.schema(), after, made.dataFile(), parser);
		expect("the new mine passes the vanilla check: " + findings, findings.isEmpty());
		List<AssetChecker.Finding> textFindings = AssetVanillaCheck.checkText(mine.schema(), made.textFile(), parser);
		expect("its text passes too: " + textFindings, textFindings.isEmpty());
		GameParser.Node node = parser.root(made.dataFile());
		expect("the game reads a sprite's levels", node.block("SPRITES").blocks("CONVEYOR_1X1").size() == 3);
		expect("and its frames, as written", java.util.Arrays.equals(node.block("SPRITES").blocks("WORK_1X1").get(0).values("FRAMES"), new String[] { "STORAGE: 5" }));
		expect("and an item's lists", java.util.Arrays.equals(node.blocks("ITEMS").get(1).decimals("COSTS"), new double[] { 1, 2 }));
		expect("the game's text file has its items", parser.root(made.textFile()).blocks("ITEMS").get(1).text("NAME").equals("Item 1"));

		// A mine that costs nothing has empty lists, which the game accepts.
		Map<String, String> free = validMine("TEST_DEPOSIT");
		free.remove("RESOURCES");
		free.remove("AREA_COSTS");
		free.put("ITEMS.0.COSTS", "");
		free.put("ITEMS.1.COSTS", "");
		AssetCreator.Created freeMine = AssetCreator.create(root, mine.type(), mine.schema(), "FREE_MINE", free, parser, after);
		expect("an empty list is written so the game reads it as empty", parser.root(freeMine.dataFile()).blocks("ITEMS").get(0).decimals("COSTS").length == 0);
		expect("and written plainly", Files.readString(freeMine.dataFile()).contains("	COSTS: [],"));

		// The producer check reads what this file says, which clears the warning a new resource gets.
		Path own = assets.resolve("init/resource/OWN_ORE.txt");
		Files.writeString(own, "");
		Files.writeString(madeDeposit.dataFile(), Files.readString(madeDeposit.dataFile()).replace("RESOURCE: STONE", "RESOURCE: OWN_ORE"));
		expect("a deposit and a mine are a producer", ResourceProducers.load(game, modVersion, parser).produces("OWN_ORE"));

		expect("a vanilla mine's name is refused", AssetCreator.problemWithName(mine.type(), "STONE", after) != null);
		expect("a new mine name works", AssetCreator.problemWithName(mine.type(), "NEW", after) == null);
		expect("a vanilla deposit's name is refused", AssetCreator.problemWithName(minable.type(), "STONE", after) != null);
	}

	/** Vanilla is the test: Home's warning must say nothing about the base game. */
	private static void sounds(List<AssetCatalog.Entry> entries, AssetCatalog.Entry farm, Path mods, GameParser parser, GameParser.GameVersion version, Path game, Path temp) throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Sound Test");
		Path root = mod.root();
		Path versionFolder = AssetCreator.versionFolder(root, version);
		Path assets = versionFolder.resolve("assets");
		SoundEntries sounds = SoundEntries.load(game, versionFolder, parser);

		int rooms = 0;
		int animals = 0;
		Path copies = temp.resolve("vanilla-sound-rooms");
		Files.createDirectories(copies);
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile())) {
			Enumeration<? extends ZipEntry> all = zip.entries();
			while (all.hasMoreElements()) {
				ZipEntry entry = all.nextElement();
				String file = entry.getName().substring(entry.getName().lastIndexOf('/') + 1);
				if (entry.getName().matches("data/assets/init/room/[A-Za-z0-9]+_[^/]+\\.txt")) {
					Path copy = copies.resolve(file);
					try (InputStream in = zip.getInputStream(entry)) {
						Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
					}
					GameParser.Node room = parser.root(copy);
					String name = file.substring(0, file.length() - 4);
					List<String> missing = sounds.missingForRoom(name, room.has("WORK") || room.has("EMPLOYMENT"));
					expect("vanilla's " + name + " has its sounds: " + missing, missing.isEmpty());
					rooms++;
				} else if (entry.getName().matches("data/assets/init/animal/[^/]+\\.txt")) {
					String name = file.substring(0, file.length() - 4);
					expect("vanilla's " + name + " has its call", sounds.missingForAnimal(name).isEmpty());
					animals++;
				}
			}
		}
		System.out.println("OBSERVED vanilla rooms and animals checked for sounds: " + rooms + " and " + animals);
		expect("vanilla's rooms were checked: " + rooms, rooms == 81);
		expect("vanilla's animals were checked: " + animals, animals == 9);

		// What's offered comes from the player's own game.
		expect("a farm starts from vanilla's: " + sounds.startingValue(SoundEntries.ROOM_WORK), "impact->Dig*".equals(sounds.startingValue(SoundEntries.ROOM_WORK)));
		expect("and its click", "DUMMY".equals(sounds.startingValue(SoundEntries.ROOM_CLICK)));
		expect("and its ambience", "DUMMY".equals(sounds.startingValue(SoundEntries.ROOM_AMBIENCE)));
		expect("an animal has no default, because vanilla's don't share one", sounds.startingValue(SoundEntries.ANIMAL_CALL) == null);
		List<String> calls = sounds.options(SoundEntries.ANIMAL_CALL);
		expect("vanilla's calls are offered, and no sound first: " + calls, calls.get(0).equals("DUMMY") && calls.containsAll(List.of("animal->Bull*", "animal->Lizard*", "animal->Dino*")));
		List<String> work = sounds.options(SoundEntries.ROOM_WORK);
		expect("work sounds are offered: " + work, work.contains("impact->Dig*") && work.contains("impact->Axe*, impact->HammerSoft*"));
		expect("each kind only offers its own", !work.contains("animal->Bull*") && !calls.contains("impact->Dig*"));

		// A new asset writes its own file.
		GameAssetIndex index = GameAssetIndex.load(game, versionFolder, parser);
		String cropName = index.crops().keySet().iterator().next();
		Map<String, String> values = validFarm(cropName, index.crop(cropName).resource());
		values.put("SOUND.ROOM_WORK_", "impact->Axe*, impact->HammerSoft*");
		AssetCreator.Created made = AssetCreator.create(root, farm.type(), farm.schema(), "LOUD", values, parser, index);
		Path mono = assets.resolve("audio/config/mono/FARM_LOUD.txt");
		Path ambience = assets.resolve("audio/config/ambience/FARM_LOUD.txt");
		expect("each folder gets its own file: " + made.soundFiles(), made.soundFiles().equals(List.of(mono, ambience)) && Files.isRegularFile(mono) && Files.isRegularFile(ambience));
		GameParser.Node node = parser.root(mono);
		expect("several sounds are a list the game reads", java.util.Arrays.equals(node.values("ROOM_WORK_FARM_LOUD"), new String[] { "impact->Axe*", "impact->HammerSoft*" }));
		expect("one sound is a plain value", "DUMMY".equals(node.value("ROOM_CLICK_FARM_LOUD")));
		expect("ambience is in its own folder's file", "DUMMY".equals(parser.root(ambience).value("ROOM_FARM_LOUD")));
		expect("the data file doesn't hold them", !Files.readString(made.dataFile()).contains("SOUND"));
		SoundEntries after = SoundEntries.load(game, versionFolder, parser);
		expect("the new farm has its sounds", after.missingIn(versionFolder, parser).isEmpty());
		expect("and they're offered from then on", after.has(SoundEntries.ROOM_WORK, "FARM_LOUD"));

		// Rules.
		GameAssetIndex withSounds = GameAssetIndex.load(game, versionFolder, parser);
		Map<String, String> good = validFarm(cropName, index.crop(cropName).resource());
		for (String key : List.of("SOUND.ROOM_WORK_", "SOUND.ROOM_CLICK_", "SOUND.ROOM_")) {
			Map<String, String> without = new LinkedHashMap<>(good);
			without.remove(key);
			expect(key + " must be chosen", AssetChecker.check(farm.schema(), withSounds, without).stream().anyMatch(f -> f.blocksSaving() && key.equals(f.tag())));
		}
		Map<String, String> unknown = new LinkedHashMap<>(good);
		unknown.put("SOUND.ROOM_WORK_", "impact->Nothing*");
		List<AssetChecker.Finding> odd = AssetChecker.check(farm.schema(), withSounds, unknown);
		expect("an unknown sound warns, because the game only goes silent: " + odd, odd.size() == 1 && odd.get(0).severity() == AssetChecker.Severity.WARNING);
		AssetCatalog.Entry animal = entryNamed(entries, "Animal");
		Map<String, String> noCall = validAnimal();
		noCall.remove("SOUND.ANIMAL_CALL_");
		expect("an animal's call must be chosen", AssetChecker.check(animal.schema(), withSounds, noCall).stream().anyMatch(f -> f.blocksSaving() && "SOUND.ANIMAL_CALL_".equals(f.tag())));

		// Nothing is overwritten, and a refusal leaves nothing behind.
		ModEntry blocked = ModCreator.create(mods, game, "Sound Blocked");
		Path blockedAssets = AssetCreator.versionFolder(blocked.root(), version).resolve("assets");
		Path existing = blockedAssets.resolve("audio/config/mono/FARM_BLOCKED.txt");
		Files.createDirectories(existing.getParent());
		Files.writeString(existing, "OTHER_ENTRY: DUMMY,\n");
		long before = countFiles(blocked.root());
		GameAssetIndex blockedIndex = GameAssetIndex.load(game, AssetCreator.versionFolder(blocked.root(), version), parser);
		AssetCreator.create(blocked.root(), farm.type(), farm.schema(), "BLOCKED", good, parser, blockedIndex);
		expect("a sound file already there keeps what it holds: " + Files.readString(existing), Files.readString(existing).startsWith("OTHER_ENTRY: DUMMY,\n"));
		expect("and gets the new entries: " + Files.readString(existing), Files.readString(existing).contains("ROOM_WORK_FARM_BLOCKED:"));
		try (Stream<Path> beside = Files.list(existing.getParent())) {
			expect("and no second file is made beside it", beside.count() == 1);
		}
		expect("the other files were added as usual", countFiles(blocked.root()) > before);

		// An entry that's already there isn't repeated, because two would both be added (SoundRaces.java:27-29).
		ModEntry already = ModCreator.create(mods, game, "Sound Already");
		Path alreadyFolder = AssetCreator.versionFolder(already.root(), version);
		Path handMade = alreadyFolder.resolve("assets/audio/config/mono/MyOwn.txt");
		Files.createDirectories(handMade.getParent());
		Files.writeString(handMade, "ROOM_WORK_FARM_HAND: work->Paper*,\n");
		AssetCreator.Created partly = AssetCreator.create(already.root(), farm.type(), farm.schema(), "HAND", good, parser, GameAssetIndex.load(game, alreadyFolder, parser));
		String written = Files.readString(alreadyFolder.resolve("assets/audio/config/mono/FARM_HAND.txt"));
		expect("an existing entry isn't repeated: " + written, !written.contains("ROOM_WORK_") && written.contains("ROOM_CLICK_FARM_HAND") && partly.soundFiles().size() == 2);

		// A mod's own sound files are offered, whichever folder they're in.
		ModEntry modSounds = ModCreator.create(mods, game, "Sound Files");
		Path soundsFolder = AssetCreator.versionFolder(modSounds.root(), version);
		Files.createDirectories(soundsFolder.resolve("assets/audio/mono/work"));
		Files.createDirectories(soundsFolder.resolve("assets/audio/ambience"));
		Files.writeString(soundsFolder.resolve("assets/audio/mono/work/MyDrill.wav"), "");
		Files.writeString(soundsFolder.resolve("assets/audio/mono/Top.wav"), "");
		Files.writeString(soundsFolder.resolve("assets/audio/ambience/MyWind.ogg"), "");
		SoundEntries own = SoundEntries.load(game, soundsFolder, parser);
		expect("a mod's sounds are offered: " + own.options(SoundEntries.ROOM_WORK), own.options(SoundEntries.ROOM_WORK).containsAll(List.of("work->MyDrill", "Top")));
		expect("ambience offers the mod's ambience, not its mono", own.options(SoundEntries.ROOM_AMBIENCE).contains("MyWind") && !own.options(SoundEntries.ROOM_AMBIENCE).contains("Top"));
		expect("vanilla's are still offered", own.options(SoundEntries.ROOM_WORK).contains("impact->Dig*"));

		// The warning, for files made by hand.
		ModEntry warned = ModCreator.create(mods, game, "Sound Warning");
		Path warnedFolder = AssetCreator.versionFolder(warned.root(), version);
		Path warnedRooms = warnedFolder.resolve("assets/init/room");
		Path warnedAnimals = warnedFolder.resolve("assets/init/animal");
		Files.createDirectories(warnedRooms);
		Files.createDirectories(warnedAnimals);
		Files.writeString(warnedRooms.resolve("FARM_NOISY.txt"), "WORK: { SHIFT_OFFSET: 0, },\n");
		Files.writeString(warnedRooms.resolve("STOCKPILE_NOWORK.txt"), "A: 1,\n");
		Files.writeString(warnedRooms.resolve("NOTATYPE_FARM.txt"), "A: 1,\n");
		Files.writeString(warnedRooms.resolve("FARM.txt"), "A: 1,\n");
		Files.writeString(warnedRooms.resolve("FARM_GRAIN.txt"), "WORK: { SHIFT_OFFSET: 0, },\n");
		Files.writeString(warnedAnimals.resolve("HANDBEAST.txt"), "A: 1,\n");
		Files.writeString(warnedAnimals.resolve("AUROCH.txt"), "A: 1,\n");
		List<String> missing = SoundEntries.load(game, warnedFolder, parser).missingIn(warnedFolder, parser);
		System.out.println("OBSERVED missing sounds: " + missing);
		expect("a hand-made room and animal are listed", missing.containsAll(List.of("ROOM_WORK_FARM_NOISY", "ROOM_CLICK_FARM_NOISY", "ROOM_FARM_NOISY", "ANIMAL_CALL_HANDBEAST")));
		expect("a file no room type loads isn't", missing.stream().noneMatch(key -> key.contains("NOTATYPE") || key.endsWith("_FARM")));
		expect("an override of vanilla's isn't", missing.stream().noneMatch(key -> key.contains("FARM_GRAIN") || key.contains("AUROCH")));
		String warning = SoundEntries.warning(missing);
		expect("the warning names them and says it's harmless: " + warning, warning.contains("ROOM_FARM_NOISY") && warning.contains("doesn't affect play"));
		expect("nothing missing gives no warning", SoundEntries.warning(List.of()) == null);

		// Adding them clears it, in a mod's own file or by merging into one of vanilla's names.
		Path own2 = warnedFolder.resolve("assets/audio/config/mono/AA_WORK.txt");
		Files.createDirectories(own2.getParent());
		Files.writeString(own2, "ROOM_WORK_FARM_NOISY: DUMMY,\nROOM_CLICK_FARM_NOISY: DUMMY,\nANIMAL_CALL_HANDBEAST: DUMMY,\n");
		SoundEntries merged = SoundEntries.load(game, warnedFolder, parser);
		expect("a mod's file with one of vanilla's names adds to it", merged.has(SoundEntries.ROOM_WORK, "FARM_NOISY") && merged.has(SoundEntries.ROOM_WORK, "FARM_GRAIN"));
		expect("only the ambience is left: " + merged.missingIn(warnedFolder, parser), merged.missingIn(warnedFolder, parser).stream().noneMatch(key -> key.startsWith("ANIMAL") || key.contains("WORK_FARM_NOISY")));
	}

	private static void expectEveryTagHasEvidence(TagSpec tag) {
		expect(tag.key() + " records evidence: " + tag.evidence(), tag.evidence() != null && tag.evidence().contains(".java:"));
		expect(tag.key() + " records confidence", tag.confidence() == TagSpec.Confidence.CODE_VERIFIED);
		tag.shape().children().forEach(AssetSchemaTest::expectEveryTagHasEvidence);
	}

	private static void expectNested(AssetSchema schema, String key, TagSpec.Kind kind, boolean required, String gameDefault, Double min, Double max) {
		TagSpec tag = schema.find(key);
		expect(key + " was found", tag != null);
		if (tag != null) {
			expect(key + " kind: " + tag.kind(), tag.kind() == kind);
			expect(key + " required: " + tag.required(), tag.required() == required);
			expect(key + " default: " + tag.gameDefault(), java.util.Objects.equals(tag.gameDefault(), gameDefault));
			expect(key + " min: " + tag.min(), java.util.Objects.equals(tag.min(), min));
			expect(key + " max: " + tag.max(), java.util.Objects.equals(tag.max(), max));
		}
	}

	/** The expected values were checked by hand against Growable.java:29-59. */
	private static void cropTable(AssetCatalog.Entry crop) {
		AssetSchema schema = crop.schema();
		expect("the crop's tags: " + schema.dataTags().stream().map(TagSpec::key).toList(),
				schema.dataTags().stream().map(TagSpec::key).toList().equals(List.of("RESOURCE", "SEASONAL_OFFSET", "GROWTH_VALUE", "MINIMAP_COLOR", "CLIMATE_BONUS", "SPRITE")));
		expect("a crop has no text file", schema.textTags().isEmpty() && crop.type().textFolder() == null);
		expectNested(schema, "SEASONAL_OFFSET", TagSpec.Kind.DECIMAL, true, null, 0.0, 1.0);
		expectNested(schema, "GROWTH_VALUE", TagSpec.Kind.DECIMAL, true, null, 0.0, 1.0);
		expectNested(schema, "CLIMATE_BONUS", TagSpec.Kind.DECIMAL_MAP, false, "0 for every entry", 0.0, 10000.0);
		expectNested(schema, "SPRITE.POLLEN", TagSpec.Kind.DECIMAL, true, null, 0.0, 10.0);
		expectNested(schema, "SPRITE.WIND_SWAY", TagSpec.Kind.DECIMAL, false, "1.0", 0.0, 10.0);
		expectNested(schema, "SPRITE.STEM.SHADOW_HEIGHT", TagSpec.Kind.DECIMAL, true, null, 0.0, 32.0);
		expectNested(schema, "SPRITE.GROWTH.SHADOW_LENGTH", TagSpec.Kind.DECIMAL, true, null, 0.0, 32.0);
		expectNested(schema, "SPRITE.STEM.WIND_SWAY", TagSpec.Kind.DECIMAL, false, "0.8", 0.0, 10.0);
		expectNested(schema, "SPRITE.GROWTH.WIND_SWAY", TagSpec.Kind.DECIMAL, false, "1.0", 0.0, 10.0);
		expect("the image's required size was scanned: " + schema.find("SPRITE.SPRITE").sprite(), new TagSpec.Sprite("sprite/resource/growable/", 460, 34).equals(schema.find("SPRITE.SPRITE").sprite()));
		schema.dataTags().forEach(AssetSchemaTest::expectEveryTagHasEvidence);
	}

	/** The expected values were checked by hand against the classes listed in FARM_CLASSES. */
	private static void farmTable(AssetCatalog.Entry farm) {
		AssetSchema schema = farm.schema();
		expect("a farm's files start with FARM_", farm.type().filePrefix().equals("FARM_") && farm.type().fileName("X").equals("FARM_X"));
		expectNested(schema, "GROWABLE", TagSpec.Kind.KEY, true, null, null, null);
		expectNested(schema, "INDOORS", TagSpec.Kind.BOOL, true, null, null, null);
		expectNested(schema, "RESOURCES", TagSpec.Kind.KEY_LIST, false, "Empty", null, null);
		expectNested(schema, "AREA_COSTS", TagSpec.Kind.LINKED_DECIMALS, true, null, null, null);
		expectNested(schema, "MINI_COLOR", TagSpec.Kind.COLOR, true, null, 0.0, 255.0);
		expectNested(schema, "MINI_COLOR_PATTERN", TagSpec.Kind.TEXT_ROWS, false, "One colour for the whole room", null, null);
		expectNested(schema, "INDUSTRY.OUT", TagSpec.Kind.KEYED_ROWS, true, null, null, null);
		expectNested(schema, "INDUSTRY.IN", TagSpec.Kind.KEYED_ROWS, false, "Nothing consumed", null, null);
		expectNested(schema, "WORK.SHIFT_OFFSET", TagSpec.Kind.DECIMAL, true, null, 0.0, 0.99);
		expectNested(schema, "WORK.ACCIDENTS_PER_YEAR", TagSpec.Kind.DECIMAL, false, "0.0", 0.0, 10000.0);
		expectNested(schema, "WORK.HEALTH_FACTOR", TagSpec.Kind.DECIMAL, false, "1.0", 0.0, 1.0);
		expectNested(schema, "WORK.LARGE_WORKFORCE", TagSpec.Kind.INT, false, "1000", 10.0, 10000.0);
		expectNested(schema, "WORK.TITLE", TagSpec.Kind.TEXT_LINE, true, null, null, null);
		expectNested(schema, "BONUS.CLIMATE", TagSpec.Kind.DECIMAL_MAP, false, null, 0.0, 2000.0);
		expectNested(schema, "EXPERIENCE_BONUS.MAX_EMPLOYEES", TagSpec.Kind.INT, true, null, 50.0, null);
		expectNested(schema, "UPGRADES.0.BOOST", TagSpec.Kind.DECIMAL, true, null, null, null);
		expectNested(schema, "UPGRADES.BOOST", TagSpec.Kind.DECIMAL, true, null, null, null);
		expect("the workforce's limit isn't enforced by the game (Json.java:291)", !schema.find("WORK.LARGE_WORKFORCE").maxEnforced());
		expect("the pattern's limit is the code's: " + schema.tag("MINI_COLOR_PATTERN").shape().maxEntries(), schema.tag("MINI_COLOR_PATTERN").shape().maxEntries() == 32);
		expect("at most 4 resources", schema.tag("RESOURCES").shape().maxEntries() == 4);
		TagSpec stats = schema.tag("STATS");
		expect("exactly 4 stat blocks, with 2 named by the file", stats.shape().fixedCount() == 4 && stats.shape().namedSlots() == 2);
		for (String unread : List.of("WORK.SOUND", "WORK.USES_TOOL", "WORK.DEFAULT_RACE_WORKSKILL", "REQUIRES", "EMPLOYMENT", "FULLFILLMENT")) {
			expect(unread + " isn't offered", schema.find(unread) == null);
		}
		expect("WORK is in both files, and both are found", schema.find("WORK.SHIFT_OFFSET") != null && schema.find("WORK.VERB") != null);
		expect("which file a value goes in", !schema.isTextKey("WORK.SHIFT_OFFSET") && schema.isTextKey("WORK.TITLE") && schema.isTextKey("UPGRADES") && !schema.isTextKey("UPGRADES.0.BOOST") && schema.isTextKey("STATS.0.NAME") && !schema.isTextKey("GROWABLE"));
		schema.dataTags().forEach(AssetSchemaTest::expectEveryTagHasEvidence);
		schema.textTags().stream().filter(tag -> tag.kind() == TagSpec.Kind.BLOCK || tag.kind() == TagSpec.Kind.BLOCK_LIST).forEach(AssetSchemaTest::expectEveryTagHasEvidence);
	}

	private static Path patchedJar(Path game, Path target, List<String> classes, String changed, String from, String to) throws IOException {
		try (ZipFile jar = new ZipFile(GameInstall.jarIn(game).toFile());
				ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(target))) {
			for (String name : classes) {
				out.putNextEntry(new ZipEntry(name + ".class"));
				try (InputStream in = jar.getInputStream(jar.getEntry(name + ".class"))) {
					byte[] bytes = in.readAllBytes();
					if (name.equals(changed)) {
						String text = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
						expect("the stand-in really changes " + from, text.contains(from));
						bytes = text.replace(from, to).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
					}
					out.write(bytes);
				}
				out.closeEntry();
			}
		}
		return target;
	}

	/** Each type must turn off, and say why, if what was reviewed has changed. */
	private static void roomGate(Path game, Path temp) throws Exception {
		List<String> cropClasses = new java.util.ArrayList<>(List.of("init/resources/Growable"));
		try (ZipFile jar = new ZipFile(GameInstall.jarIn(game).toFile())) {
			jar.stream().map(ZipEntry::getName).filter(name -> name.startsWith("init/resources/GrowableSprite") && name.endsWith(".class"))
					.map(name -> name.substring(0, name.length() - 6)).forEach(cropClasses::add);
		}
		AssetCatalog.Entry control = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve("crop-all.jar"), cropClasses)), "Crop");
		expect("these classes are enough for Crop: " + control.unavailableReason(), control.available());
		AssetCatalog.Entry farmControl = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve("farm-all.jar"), FARM_CLASSES)), "Farm");
		expect("these classes are enough for Farm: " + farmControl.unavailableReason(), farmControl.available());

		int at = 0;
		for (String gone : FARM_CLASSES) {
			AssetCatalog.Entry entry = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve("farm-without-" + at++ + ".jar"), FARM_CLASSES.stream().filter(n -> !n.equals(gone)).toList())), "Farm");
			expect("without " + gone + ", Farm is disabled: " + entry.unavailableReason(), !entry.available() && entry.unavailableReason() != null);
		}
		AssetCatalog.Entry noCrop = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve("crop-without.jar"), cropClasses.stream().filter(n -> !n.equals("init/resources/Growable")).toList())), "Crop");
		expect("without Growable, Crop is disabled: " + noCrop.unavailableReason(), !noCrop.available() && noCrop.unavailableReason() != null);
		AssetCatalog.Entry noSprite = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve("crop-without-sprite.jar"), List.of("init/resources/Growable"))), "Crop");
		expect("without the sprite class, Crop is disabled: " + noSprite.unavailableReason(), !noSprite.available() && noSprite.unavailableReason() != null);

		// The same classes, with one read's key changed, as a game update might.
		AssetCatalog.Entry changedCrop = entryNamed(AssetCatalog.load(patchedJar(game, temp.resolve("crop-changed.jar"), cropClasses, "init/resources/Growable", "SEASONAL_OFFSET", "SEASONAL_OFFSEX")), "Crop");
		expect("a changed read turns Crop off: " + changedCrop.unavailableReason(), !changedCrop.available() && changedCrop.unavailableReason() != null);
		// A sound's name is built at run time, so the gate checks the class still has its fixed part.
		for (String[] sound : new String[][] { { "settlement/room/main/employment/RoomEmploymentSimple", "ROOM_WORK_", "ROOM_WORX_" }, { "settlement/room/main/RoomBlueprintImp", "ROOM_CLICK_", "ROOM_CLICX_" } }) {
			AssetCatalog.Entry renamed = entryNamed(AssetCatalog.load(patchedJar(game, temp.resolve("farm-sound-" + sound[1] + ".jar"), FARM_CLASSES, sound[0], sound[1], sound[2])), "Farm");
			expect("a renamed sound " + sound[1] + " turns Farm off: " + renamed.unavailableReason(), !renamed.available() && renamed.unavailableReason() != null);
		}
		for (String[] change : new String[][] { { "settlement/room/main/employment/RoomEmploymentSimple", "SHIFT_OFFSET", "SHIFT_OFFSEX" }, { "settlement/room/food/farm/Constructor", "INDOORS", "INDOORX" },
				{ "settlement/room/industry/module/Industry", "AI_RATE", "AI_RATX" }, { "init/type/CLIMATES", "CLIMATE", "CLIMATX" } }) {
			AssetCatalog.Entry changed = entryNamed(AssetCatalog.load(patchedJar(game, temp.resolve("farm-changed-" + change[1] + ".jar"), FARM_CLASSES, change[0], change[1], change[2])), "Farm");
			expect("a changed " + change[1] + " turns Farm off: " + changed.unavailableReason(), !changed.available() && changed.unavailableReason() != null);
		}
	}

	private static void vanillaCrops(Path game, AssetSchema schema, GameAssetIndex index, Path temp, GameParser parser) throws Exception {
		Path folder = temp.resolve("vanilla-crops");
		Files.createDirectories(folder);
		int count = 0;
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (entry.getName().matches("data/assets/init/resource/growable/[^/]+\\.txt")) {
					Path copy = folder.resolve(entry.getName().substring(entry.getName().lastIndexOf('/') + 1));
					try (InputStream in = zip.getInputStream(entry)) {
						Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
					}
					count++;
					List<AssetChecker.Finding> findings = AssetVanillaCheck.check(schema, index, copy, parser);
					expect(copy.getFileName() + " has no errors or warnings: " + findings, findings.isEmpty());
				}
			}
		}
		expect("all 7 vanilla crops were checked: " + count, count == 7);
	}

	private static void vanillaFarms(Path game, AssetSchema schema, GameAssetIndex index, GameAssetIndex withCrops, Path temp, GameParser parser) throws Exception {
		Path folder = temp.resolve("vanilla-farms");
		Path textFolder = temp.resolve("vanilla-farm-text");
		Files.createDirectories(folder);
		Files.createDirectories(textFolder);
		int count = 0;
		int texts = 0;
		// Vanilla's farm text still holds a BONUS block. Nothing reads it (see RoomSchemas), so it's only allowed here.
		List<String> known = new java.util.ArrayList<>(schema.textTags().stream().map(TagSpec::key).toList());
		known.add("INFO");
		known.add("BONUS");
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName().substring(entry.getName().lastIndexOf('/') + 1);
				if (entry.getName().matches("data/assets/init/room/FARM_[^/]+\\.txt")) {
					Path copy = folder.resolve(name);
					try (InputStream in = zip.getInputStream(entry)) {
						Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
					}
					count++;
					List<AssetChecker.Finding> findings = AssetVanillaCheck.check(schema, index, copy, parser);
					expect(name + " has no errors or warnings: " + findings, findings.isEmpty());

					// The harvest is the crop's resource, so this rule mustn't warn about the base game.
					GameParser.Node farm = parser.root(copy);
					String crop = farm.value("GROWABLE");
					String first = farm.block("INDUSTRY").block("OUT").keys().get(0);
					GameAssetIndex.Crop known2 = withCrops.crop(crop);
					expect(name + " names a crop that was read: " + crop, known2 != null);
					expect(name + "'s first output is its crop's resource: " + first, known2 != null && GameAssetIndex.sameKey(known2.resource(), first));
				} else if (entry.getName().matches("data/assets/text/room/FARM_[^/]+\\.txt")) {
					Path copy = textFolder.resolve(name);
					try (InputStream in = zip.getInputStream(entry)) {
						Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
					}
					texts++;
					List<String> keys = parser.tags(copy).tags().stream().map(GameParser.TagValue::key).toList();
					expect(name + " text uses only known tags: " + keys, known.containsAll(keys));
					List<AssetChecker.Finding> findings = AssetVanillaCheck.checkText(schema, copy, parser);
					expect(name + " text reads as the schema says: " + findings, findings.isEmpty());
				}
			}
		}
		expect("all 7 vanilla farms were checked: " + count, count == 7);
		expect("their text files were checked: " + texts, texts == 7);
	}

	private static Map<String, String> validCrop(String image) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("RESOURCE", "STONE");
		values.put("SEASONAL_OFFSET", "0.5");
		values.put("GROWTH_VALUE", "0.25");
		values.put("MINIMAP_COLOR", "10_20_30");
		values.put("SPRITE.SPRITE", image);
		values.put("SPRITE.POLLEN", "1");
		values.put("SPRITE.COLOR_POLLEN", "200_200_0");
		for (String part : List.of("SPRITE.STEM.", "SPRITE.GROWTH.")) {
			values.put(part + "DEAD", "1_1_1");
			values.put(part + "LIVE", "2_2_2");
			values.put(part + "RIPE", "3_3_3");
			values.put(part + "SHADOW_HEIGHT", "1");
			values.put(part + "SHADOW_LENGTH", "2");
		}
		return values;
	}

	private static Map<String, String> validFarm(String crop, String output) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("ICON", "24->resource->Gem->0");
		values.put("GROWABLE", crop);
		values.put("INDOORS", "false");
		values.put("MINI_COLOR", "10_20_30");
		values.put("INDUSTRY.OUT." + output + ".PLAYER", "1");
		values.put("WORK.SHIFT_OFFSET", "0.5");
		values.put("SOUND.ROOM_WORK_", "impact->Dig*");
		values.put("SOUND.ROOM_CLICK_", "DUMMY");
		values.put("SOUND.ROOM_", "DUMMY");
		values.put("NAME", "Test Farm");
		values.put("DESC", "It's for testing.");
		values.put("WORK.TITLE", "Tester");
		values.put("WORK.VERB", "testing");
		for (int i = 0; i < 2; i++) {
			values.put("STATS." + i + ".NAME", "Stat " + i);
			values.put("STATS." + i + ".DESC", "About stat " + i);
		}
		return values;
	}

	private static List<AssetChecker.Finding> changed(AssetSchema schema, GameAssetIndex index, Map<String, String> base, String key, String value) {
		Map<String, String> values = new LinkedHashMap<>(base);
		if (value == null) {
			values.remove(key);
		} else {
			values.put(key, value);
		}
		return AssetChecker.check(schema, index, values);
	}

	private static void clean(AssetSchema schema, GameAssetIndex index, Map<String, String> base, String key, String value) {
		List<AssetChecker.Finding> findings = changed(schema, index, base, key, value);
		expect(key + "=" + value + " has no errors or warnings: " + findings, findings.isEmpty());
	}

	private static void refused(AssetSchema schema, GameAssetIndex index, Map<String, String> base, String key, String value) {
		expect(key + "=" + value + " is refused", changed(schema, index, base, key, value).stream().anyMatch(AssetChecker.Finding::blocksSaving));
	}

	private static void warned(AssetSchema schema, GameAssetIndex index, Map<String, String> base, String key, String value) {
		List<AssetChecker.Finding> findings = changed(schema, index, base, key, value);
		expect(key + "=" + value + " warns but doesn't block: " + findings, !findings.isEmpty() && findings.stream().noneMatch(AssetChecker.Finding::blocksSaving));
	}

	private static void cropRules(AssetSchema schema, GameAssetIndex index) {
		List<String> images = index.spriteOptions("sprite/resource/growable/");
		expect("vanilla's crop images are offered: " + images, !images.isEmpty());
		expect("the folder's _Flower isn't offered, by the existing rule: " + images, images.stream().noneMatch(image -> image.startsWith("_")));
		for (String image : images) {
			GameAssetIndex.ImageSize size = index.spriteSize("sprite/resource/growable/", image);
			expect(image + " has the size the game requires: " + size, size.width() == 460 && size.height() >= 34);
		}
		Map<String, String> good = validCrop(images.get(0));
		expect("a good crop has no problems: " + AssetChecker.check(schema, index, good), AssetChecker.check(schema, index, good).isEmpty());

		for (String key : List.of("RESOURCE", "SEASONAL_OFFSET", "GROWTH_VALUE", "MINIMAP_COLOR", "SPRITE.SPRITE", "SPRITE.POLLEN", "SPRITE.COLOR_POLLEN", "SPRITE.STEM.DEAD", "SPRITE.STEM.SHADOW_HEIGHT", "SPRITE.GROWTH.RIPE", "SPRITE.GROWTH.SHADOW_LENGTH")) {
			refused(schema, index, good, key, null);
		}
		refused(schema, index, good, "SEASONAL_OFFSET", "1.1");
		clean(schema, index, good, "SEASONAL_OFFSET", "1");
		refused(schema, index, good, "SPRITE.POLLEN", "10.5");
		clean(schema, index, good, "SPRITE.POLLEN", "10");
		refused(schema, index, good, "SPRITE.STEM.SHADOW_HEIGHT", "33");
		clean(schema, index, good, "SPRITE.STEM.WIND_SWAY", "10");
		refused(schema, index, good, "SPRITE.STEM.WIND_SWAY", "11");
		refused(schema, index, good, "SPRITE.GROWTH.LIVE", "256_0_0");
		refused(schema, index, good, "RESOURCE", "NOT_A_RESOURCE");
		refused(schema, index, good, "SPRITE.SPRITE", "NotAnImage");
		clean(schema, index, good, "CLIMATE_BONUS.TEMPERATE", "2");
		refused(schema, index, good, "CLIMATE_BONUS.TEMPERATE", "10001");

		// Two crops yielding one resource stops the game loading, whichever way it's spelled.
		GameAssetIndex.Crop grain = index.crops().values().iterator().next();
		List<AssetChecker.Finding> duplicate = changed(schema, index, good, "RESOURCE", grain.resource());
		expect("a resource that's already a crop's is refused: " + duplicate, duplicate.stream().anyMatch(f -> f.blocksSaving() && "RESOURCE".equals(f.tag())));
		String other = grain.resource().startsWith("_") ? grain.resource().substring(1) : "_" + grain.resource();
		expect("also when spelled the other way: " + other, changed(schema, index, good, "RESOURCE", other).stream().anyMatch(AssetChecker.Finding::blocksSaving));
	}

	private static void farmRules(AssetSchema schema, GameAssetIndex index) {
		String crop = index.crops().keySet().iterator().next();
		String output = index.crop(crop).resource();
		Map<String, String> good = validFarm(crop, output);
		String outKey = "INDUSTRY.OUT." + output + ".PLAYER";
		expect("a good farm has no problems: " + AssetChecker.check(schema, index, good), AssetChecker.check(schema, index, good).isEmpty());

		for (String key : List.of("ICON", "GROWABLE", "INDOORS", "MINI_COLOR", "WORK.SHIFT_OFFSET", "NAME", "DESC", "WORK.TITLE", "WORK.VERB", "STATS.0.NAME", "STATS.1.DESC")) {
			refused(schema, index, good, key, null);
		}
		// Empty OUT crashes on load (ROOM_FARM.java:88).
		refused(schema, index, good, outKey, null);
		expect("an output without its rate is refused", changed(schema, index, good, outKey, "").stream().anyMatch(AssetChecker.Finding::blocksSaving));
		refused(schema, index, good, outKey, "100001");
		clean(schema, index, good, outKey, "100000");
		clean(schema, index, good, "INDUSTRY.OUT." + output + ".AI_RATE", "0");
		refused(schema, index, good, "INDUSTRY.OUT." + output + ".AI_RATE", "100001");
		clean(schema, index, good, "INDUSTRY.IN.WOOD", "10000");
		refused(schema, index, good, "INDUSTRY.IN.WOOD", "10001");
		refused(schema, index, good, "INDUSTRY.IN.NOT_A_RESOURCE", "1");
		refused(schema, index, good, "GROWABLE", "NOT_A_CROP");

		// The harvest is the crop's, while the numbers follow OUT.
		Map<String, String> mismatch = new LinkedHashMap<>(good);
		mismatch.remove(outKey);
		String wrong = output.contains("WOOD") ? "STONE" : "WOOD";
		mismatch.put("INDUSTRY.OUT." + wrong + ".PLAYER", "1");
		List<AssetChecker.Finding> differ = AssetChecker.check(schema, index, mismatch);
		expect("an output that isn't the crop's warns: " + differ, differ.size() == 1 && differ.get(0).severity() == AssetChecker.Severity.WARNING && differ.get(0).message().contains(output) && differ.get(0).message().contains(wrong));

		// At most 4 resources, each with an amount.
		Map<String, String> four = new LinkedHashMap<>(good);
		List<String> resources = index.options(TagSpec.Reference.RESOURCES);
		four.put("RESOURCES", String.join(",", resources.subList(0, 4)));
		four.put("AREA_COSTS", "1,1,1,1");
		expect("4 resources are fine: " + AssetChecker.check(schema, index, four), AssetChecker.check(schema, index, four).isEmpty());
		Map<String, String> five = new LinkedHashMap<>(good);
		five.put("RESOURCES", String.join(",", resources.subList(0, 5)));
		five.put("AREA_COSTS", "1,1,1,1,1");
		expect("a 5th resource is refused", AssetChecker.check(schema, index, five).stream().anyMatch(AssetChecker.Finding::blocksSaving));
		Map<String, String> uneven = new LinkedHashMap<>(good);
		uneven.put("RESOURCES", "WOOD,STONE");
		uneven.put("AREA_COSTS", "1");
		expect("a missing amount is refused", AssetChecker.check(schema, index, uneven).stream().anyMatch(AssetChecker.Finding::blocksSaving));

		// The pattern: the code allows 32 rows, inclusive (FurnisherMinimapColor.java:28).
		String row = "10_20_30";
		clean(schema, index, good, "MINI_COLOR_PATTERN", row);
		clean(schema, index, good, "MINI_COLOR_PATTERN", String.join("\n", java.util.Collections.nCopies(32, row)));
		refused(schema, index, good, "MINI_COLOR_PATTERN", String.join("\n", java.util.Collections.nCopies(33, row)));
		refused(schema, index, good, "MINI_COLOR_PATTERN", row + "\n" + row + "x");
		clean(schema, index, good, "MINI_COLOR_PATTERN", "44444444\n99999999\n00000000\n44444444");
		refused(schema, index, good, "MINI_COLOR_PATTERN", "\n");

		// The code only proves a crash with 2 levels and every mask too short (RoomUpgrades.java:127-128).
		Map<String, String> masks = new LinkedHashMap<>(good);
		masks.put("RESOURCES", String.join(",", resources.subList(0, 3)));
		masks.put("AREA_COSTS", "1,1,1");
		masks.put("UPGRADES.0.BOOST", "1");
		masks.put("UPGRADES.0.RESOURCE_MASK", "1");
		expect("one level with a short mask is fine, because the game clamps: " + AssetChecker.check(schema, index, masks), AssetChecker.check(schema, index, masks).isEmpty());
		masks.put("UPGRADES.1.BOOST", "2");
		masks.put("UPGRADES.1.RESOURCE_MASK", "1,1");
		List<AssetChecker.Finding> shortMasks = AssetChecker.check(schema, index, masks);
		expect("two levels with every mask too short are refused: " + shortMasks, shortMasks.stream().anyMatch(f -> f.blocksSaving() && "UPGRADES.0.RESOURCE_MASK".equals(f.tag())));
		masks.put("UPGRADES.1.RESOURCE_MASK", "1,1,1");
		expect("a shorter level is padded, so one full mask is enough: " + AssetChecker.check(schema, index, masks), AssetChecker.check(schema, index, masks).isEmpty());
		masks.put("UPGRADES.1.RESOURCE_MASK", "1,1,1,1");
		expect("a longer mask does no harm: " + AssetChecker.check(schema, index, masks), AssetChecker.check(schema, index, masks).isEmpty());

		clean(schema, index, good, "WORK.SHIFT_OFFSET", "0.99");
		refused(schema, index, good, "WORK.SHIFT_OFFSET", "1");
		refused(schema, index, good, "WORK.HEALTH_FACTOR", "1.5");
		// The game's own check on this limit has no effect (Json.java:291), so it's a warning.
		warned(schema, index, good, "WORK.LARGE_WORKFORCE", "20000");
		refused(schema, index, good, "WORK.LARGE_WORKFORCE", "9");

		// A block's own required tags only count once it's there.
		refused(schema, index, good, "EXPERIENCE_BONUS.BONUS", "1");
		Map<String, String> experience = new LinkedHashMap<>(good);
		experience.put("EXPERIENCE_BONUS.MAX_EMPLOYEES", "100");
		experience.put("EXPERIENCE_BONUS.BONUS", "1.5");
		expect("a whole block is fine: " + AssetChecker.check(schema, index, experience), AssetChecker.check(schema, index, experience).isEmpty());
		clean(schema, index, good, "BONUS.CLIMATE.TEMPERATE", "2000");
		refused(schema, index, good, "BONUS.CLIMATE.TEMPERATE", "2001");

		// A level needs what the game reads from it.
		refused(schema, index, good, "UPGRADES.0.BOOST", "1");
		Map<String, String> level = new LinkedHashMap<>(good);
		level.put("UPGRADES.0.BOOST", "1");
		level.put("UPGRADES.0.RESOURCE_MASK", "1,1");
		expect("a whole level is fine: " + AssetChecker.check(schema, index, level), AssetChecker.check(schema, index, level).isEmpty());
		level.put("UPGRADES.0.RESOURCE_MASK", "1,x");
		expect("a mask has to be numbers", AssetChecker.check(schema, index, level).stream().anyMatch(AssetChecker.Finding::blocksSaving));

		List<String> environments = index.options(TagSpec.Reference.ENVIRONMENTS);
		expect("environments are offered: " + environments, !environments.isEmpty());
		Map<String, String> emit = new LinkedHashMap<>(good);
		emit.put("ENVIRONMENT_EMIT." + environments.get(0) + ".VALUE", "0.5");
		emit.put("ENVIRONMENT_EMIT." + environments.get(0) + ".RADIUS", "0.5");
		expect("an emission is fine: " + AssetChecker.check(schema, index, emit), AssetChecker.check(schema, index, emit).isEmpty());
		emit.remove("ENVIRONMENT_EMIT." + environments.get(0) + ".RADIUS");
		expect("an emission needs both numbers", AssetChecker.check(schema, index, emit).stream().anyMatch(AssetChecker.Finding::blocksSaving));
		Map<String, String> unknownEmit = new LinkedHashMap<>(good);
		unknownEmit.put("ENVIRONMENT_EMIT.NOT_ONE.VALUE", "0.5");
		warned(schema, index, unknownEmit, "ENVIRONMENT_EMIT.NOT_ONE.RADIUS", "0.5");
		expect("floors are offered: ", !index.options(TagSpec.Reference.FLOORS).isEmpty());
		clean(schema, index, good, "FLOOR", index.options(TagSpec.Reference.FLOORS).get(0));
	}

	/** A mod's images are offered with vanilla's, from the same folder. */
	private static void cropSprites(AssetSchema schema, Path mods, GameParser.GameVersion version, Path game) throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Crop Sprites");
		Path sprites = AssetCreator.versionFolder(mod.root(), version).resolve("assets/sprite/resource/growable");
		Files.createDirectories(sprites);
		Files.write(sprites.resolve("MyCrop.png"), pngHeader(460, 34));
		Files.write(sprites.resolve("Taller.png"), pngHeader(460, 68));
		Files.write(sprites.resolve("TooWide.png"), pngHeader(461, 34));
		Files.write(sprites.resolve("TooShort.png"), pngHeader(460, 33));
		GameAssetIndex index = GameAssetIndex.load(game, AssetCreator.versionFolder(mod.root(), version));
		List<String> images = index.spriteOptions("sprite/resource/growable/");
		expect("a mod's crop image is offered: " + images, images.contains("MyCrop"));
		GameAssetIndex vanilla = GameAssetIndex.load(game, AssetCreator.versionFolder(ModCreator.create(mods, game, "No Sprites").root(), version));
		expect("vanilla's are still offered", images.containsAll(vanilla.spriteOptions("sprite/resource/growable/")) && !vanilla.spriteOptions("sprite/resource/growable/").isEmpty());
		Map<String, String> good = validCrop("MyCrop");
		// This index read no crops, so the duplicate rule has nothing to say here.
		clean(schema, index, good, "SPRITE.SPRITE", "MyCrop");
		clean(schema, index, good, "SPRITE.SPRITE", "Taller");
		refused(schema, index, good, "SPRITE.SPRITE", "TooWide");
		refused(schema, index, good, "SPRITE.SPRITE", "TooShort");
	}

	private static void roomNames(AssetType farm, AssetType crop, GameAssetIndex index, GameParser.GameVersion version) {
		String vanillaFarm = index.crops().keySet().iterator().next();
		expect("a new farm name works", AssetCreator.problemWithName(farm, "TESTFARM", index) == null);
		expect("the destination has the prefix: " + AssetCreator.destination(farm, "TESTFARM", version), AssetCreator.destination(farm, "TESTFARM", version).endsWith("assets/init/room/FARM_TESTFARM.txt"));
		expect("a vanilla farm's name is refused", AssetCreator.problemWithName(farm, "GRAIN", index) != null || AssetCreator.problemWithName(farm, "VEG", index) != null);
		expect("a crop's destination has no prefix", AssetCreator.destination(crop, "TESTCROP", version).endsWith("assets/init/resource/growable/TESTCROP.txt"));
		expect("a vanilla crop's name is refused: " + vanillaFarm, AssetCreator.problemWithName(crop, vanillaFarm, index) != null);
	}

	/** The whole chain the in-game test relies on: a new resource, a crop yielding it, and a farm growing that crop. */
	private static void roomCreation(List<AssetCatalog.Entry> entries, AssetCatalog.Entry crop, AssetCatalog.Entry farm, Path mods, GameParser parser, GameParser.GameVersion version, Path game) throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Room Creation");
		Path root = mod.root();
		Path versionFolder = AssetCreator.versionFolder(root, version);
		Path assets = versionFolder.resolve("assets");
		AssetCatalog.Entry resource = entryNamed(entries, "Resource");
		AssetCreator.create(root, resource.type(), resource.schema(), "UNOBTAINIUM", validResource(), parser, GameAssetIndex.load(game, versionFolder, parser));
		expect("the new resource warns that nothing produces it", unproduced(game, versionFolder, parser).equals(List.of("UNOBTAINIUM")));

		GameAssetIndex index = GameAssetIndex.load(game, versionFolder, parser);
		String image = index.spriteOptions("sprite/resource/growable/").get(0);

		// A refusal leaves nothing behind, including when it's only found by the rules across files.
		long before = countFiles(root);
		Map<String, String> badCrop = validCrop(image);
		badCrop.put("RESOURCE", index.crops().values().iterator().next().resource());
		expectRefused(root, crop, "BAD_CROP", badCrop, parser, index, "");
		expect("a refused crop leaves nothing behind", countFiles(root) == before);

		Map<String, String> cropValues = validCrop(image);
		cropValues.put("RESOURCE", "UNOBTAINIUM");
		cropValues.put("SPRITE.WIND_SWAY", "2");
		cropValues.put("SPRITE.STEM.WIND_SWAY", "0.5");
		cropValues.put("CLIMATE_BONUS.TEMPERATE", "1.5");
		AssetCreator.Created madeCrop = AssetCreator.create(root, crop.type(), crop.schema(), "TEST_CROP", cropValues, parser, index);
		expect("the crop's file is in place", madeCrop.dataFile().equals(assets.resolve("init/resource/growable/TEST_CROP.txt")) && Files.isRegularFile(madeCrop.dataFile()));
		expect("a crop writes no text file", madeCrop.textFile() == null && !Files.exists(assets.resolve("text/resource/growable")));
		List<AssetChecker.Finding> cropFindings = AssetVanillaCheck.check(crop.schema(), index, madeCrop.dataFile(), parser);
		expect("the new crop passes the vanilla check: " + cropFindings, cropFindings.isEmpty());
		GameParser.Node cropNode = parser.root(madeCrop.dataFile());
		GameParser.Node sprite = cropNode.block("SPRITE");
		expect("the crop's values read back through the game's parser", "UNOBTAINIUM".equals(cropNode.value("RESOURCE")) && image.equals(sprite.value("SPRITE")) && sprite.decimal("WIND_SWAY", 0, 10) == 2
				&& sprite.block("STEM").decimal("WIND_SWAY", 0, 10) == 0.5 && sprite.block("GROWTH").decimal("SHADOW_LENGTH", 0, 32) == 2);
		expect("a tag that wasn't added isn't written", !sprite.block("GROWTH").has("WIND_SWAY"));
		expect("a crop alone doesn't produce it, because a farm is what's built", !unproduced(game, versionFolder, parser).isEmpty());

		index = GameAssetIndex.load(game, versionFolder, parser);
		expect("the new crop is offered to farms", index.options(TagSpec.Reference.GROWABLES).contains("TEST_CROP"));
		expect("and what it yields is known", "UNOBTAINIUM".equals(index.crop("TEST_CROP").resource()));
		Map<String, String> again = validCrop(image);
		again.put("RESOURCE", "UNOBTAINIUM");
		expect("a second crop for that resource is refused", AssetChecker.check(crop.schema(), index, again).stream().anyMatch(AssetChecker.Finding::blocksSaving));

		before = countFiles(root);
		Map<String, String> badFarm = validFarm("TEST_CROP", "UNOBTAINIUM");
		badFarm.remove("INDUSTRY.OUT.UNOBTAINIUM.PLAYER");
		expectRefused(root, farm, "BAD_FARM", badFarm, parser, index, "");
		expect("a refused farm leaves nothing behind", countFiles(root) == before);

		// Everything a farm can hold, so each shape goes through the game's parser.
		Map<String, String> farmValues = validFarm("TEST_CROP", "UNOBTAINIUM");
		farmValues.put("RESOURCES", "WOOD,STONE");
		farmValues.put("AREA_COSTS", "1,2.5");
		farmValues.put("MINI_COLOR_PATTERN", "10_20_30\n40_50_60");
		farmValues.put("INDUSTRY.OUT.UNOBTAINIUM.AI_RATE", "2");
		farmValues.put("INDUSTRY.OUT.UNOBTAINIUM.AI_RECOVERY", "3");
		farmValues.put("INDUSTRY.IN.WOOD", "0.5");
		farmValues.put("WORK.NIGHT_SHIFT", "true");
		farmValues.put("WORK.LARGE_WORKFORCE", "50");
		farmValues.put("BONUS.CLIMATE.TEMPERATE", "1.5");
		farmValues.put("EXPERIENCE_BONUS.MAX_EMPLOYEES", "100");
		farmValues.put("EXPERIENCE_BONUS.BONUS", "1.5");
		farmValues.put("UPGRADES.0.RESOURCE_MASK", "1,1");
		farmValues.put("UPGRADES.0.BOOST", "1");
		farmValues.put("UPGRADES.1.RESOURCE_MASK", "2,2");
		farmValues.put("UPGRADES.1.BOOST", "2");
		farmValues.put("UPGRADES.1.AI", "3");
		farmValues.put("UPGRADES", "Better\nBest");
		String environment = index.options(TagSpec.Reference.ENVIRONMENTS).get(0);
		farmValues.put("ENVIRONMENT_EMIT." + environment + ".VALUE", "0.5");
		farmValues.put("ENVIRONMENT_EMIT." + environment + ".RADIUS", "0.25");
		List<AssetChecker.Finding> full = AssetChecker.check(farm.schema(), index, farmValues);
		expect("the full farm has no problems: " + full, full.isEmpty());
		AssetCreator.Created madeFarm = AssetCreator.create(root, farm.type(), farm.schema(), "TEST_FARM", farmValues, parser, index);
		expect("the farm's file has the prefix the game needs", madeFarm.dataFile().equals(assets.resolve("init/room/FARM_TEST_FARM.txt")) && Files.isRegularFile(madeFarm.dataFile()));
		expect("and so does its text file", madeFarm.textFile().equals(assets.resolve("text/room/FARM_TEST_FARM.txt")) && Files.isRegularFile(madeFarm.textFile()));
		List<AssetChecker.Finding> farmFindings = AssetVanillaCheck.check(farm.schema(), index, madeFarm.dataFile(), parser);
		expect("the new farm passes the vanilla check: " + farmFindings, farmFindings.isEmpty());
		List<AssetChecker.Finding> textFindings = AssetVanillaCheck.checkText(farm.schema(), madeFarm.textFile(), parser);
		expect("its text passes too: " + textFindings, textFindings.isEmpty());

		GameParser.Node node = parser.root(madeFarm.dataFile());
		GameParser.Node text = parser.root(madeFarm.textFile());
		expect("the output reads back", node.block("INDUSTRY").block("OUT").block("UNOBTAINIUM").decimal("AI_RECOVERY", 0, 100000) == 3);
		expect("a plain input reads back", node.block("INDUSTRY").block("IN").decimal("WOOD", 0, 10000) == 0.5);
		expect("levels read back in order", node.blocks("UPGRADES").size() == 2 && node.blocks("UPGRADES").get(1).decimal("AI", 0, 10000) == 3);
		expect("the pattern reads back", java.util.Arrays.equals(node.texts("MINI_COLOR_PATTERN"), new String[] { "10_20_30", "40_50_60" }));
		expect("the game reads exactly 4 stat blocks", text.blocks("STATS").size() == 4);
		expect("the names are where the game reads them", "Stat 1".equals(text.blocks("STATS").get(1).text("NAME")));
		expect("the game names the other two, so nothing's written there", !text.blocks("STATS").get(2).has("NAME"));
		expect("WORK's text is in the text file", "Tester".equals(text.block("WORK").text("TITLE")) && !text.block("WORK").has("SHIFT_OFFSET"));
		expect("WORK's numbers are in the data file", node.block("WORK").has("SHIFT_OFFSET") && !node.block("WORK").has("TITLE"));
		expect("upgrade names are in the text file", java.util.Arrays.equals(text.texts("UPGRADES"), new String[] { "Better", "Best" }));
		expect("the name is in INFO", "Test Farm".equals(text.block("INFO").text("NAME")));

		// The farm rule compares the first output, so the game's parser must keep a file's order.
		Path order = mods.resolve("order.txt");
		Files.writeString(order, "OUT: { ZZZ: 1, AAA: 1, MMM: 1, },\n");
		expect("the game's parser keeps a file's order", parser.root(order).block("OUT").keys().equals(List.of("ZZZ", "AAA", "MMM")));
		Files.delete(order);

		expect("the toolkit's farm clears the resource warning: " + unproduced(game, versionFolder, parser), unproduced(game, versionFolder, parser).isEmpty());
		Files.delete(madeFarm.dataFile());
		Map<String, String> zero = validFarm("TEST_CROP", "UNOBTAINIUM");
		zero.put("INDUSTRY.OUT.UNOBTAINIUM.PLAYER", "0");
		zero.put("INDUSTRY.OUT.UNOBTAINIUM.AI_RATE", "0");
		Files.delete(madeFarm.textFile());
		index = GameAssetIndex.load(game, versionFolder, parser);
		AssetCreator.create(root, farm.type(), farm.schema(), "TEST_FARM", zero, parser, index);
		expect("a farm with a rate of 0 doesn't clear it", unproduced(game, versionFolder, parser).equals(List.of("UNOBTAINIUM")));

		// Nothing gets overwritten.
		Path existing = assets.resolve("init/room/FARM_TEST_FARM.txt");
		String kept = Files.readString(existing);
		index = GameAssetIndex.load(game, versionFolder, parser);
		expect("a refreshed index refuses the name", AssetCreator.problemWithName(farm.type(), "TEST_FARM", index) != null);
		expectRefused(root, farm, "TEST_FARM", validFarm("TEST_CROP", "UNOBTAINIUM"), parser, index, "");
		expect("the existing farm is untouched", Files.readString(existing).equals(kept));
	}

	private static AssetCatalog.Entry resourceFrom(List<AssetCatalog.Entry> entries) {
		return entries.stream().filter(e -> e.type().label().equals("Resource")).findFirst().orElseThrow();
	}

	private static Path jarWith(Path game, Path target, List<String> classes) throws IOException {
		try (ZipFile jar = new ZipFile(GameInstall.jarIn(game).toFile());
				ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(target))) {
			for (String name : classes) {
				out.putNextEntry(new ZipEntry(name + ".class"));
				try (InputStream in = jar.getInputStream(jar.getEntry(name + ".class"))) {
					in.transferTo(out);
				}
				out.closeEntry();
			}
			// An anonymous sheet is recognised by what it extends, which is read from these classes alone.
		}
		return target;
	}

	/** Vanilla is the test: this includes the 9 files in category 4, above the code's stated 3. */
	private static void vanillaResources(Path game, AssetSchema schema, GameAssetIndex index, Path temp) throws IOException, GameParser.UnavailableException {
		Path folder = temp.resolve("vanilla-resources");
		Files.createDirectories(folder);
		int count = 0;
		int inCategoryFour = 0;
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile());
				GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (entry.getName().matches("data/assets/init/resource/[^/]+\\.txt")) {
					Path copy = folder.resolve(entry.getName().substring(entry.getName().lastIndexOf('/') + 1));
					try (InputStream in = zip.getInputStream(entry)) {
						Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
					}
					count++;
					inCategoryFour += Files.readString(copy).contains("CATEGORY_DEFAULT: 4") ? 1 : 0;
					List<AssetChecker.Finding> findings = AssetVanillaCheck.check(schema, index, copy, parser);
					expect(copy.getFileName() + " has no errors or warnings: " + findings, findings.isEmpty());
				}
			}
		}
		expect("all 42 vanilla resources were checked: " + count, count == 42);
		expect("that includes the ones in category 4: " + inCategoryFour, inCategoryFour == 9);
	}

	/** Vanilla writes these at the top level, and the game accepts that or an INFO block (INFO.java:16-17). */
	private static void resourceText(Path game, AssetSchema schema, Path temp) throws IOException, GameParser.UnavailableException {
		Path folder = temp.resolve("vanilla-resource-text");
		Files.createDirectories(folder);
		int count = 0;
		List<String> known = schema.textTags().stream().map(TagSpec::key).toList();
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile());
				GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (entry.getName().matches("data/assets/text/resource/[^/]+\\.txt")) {
					Path copy = folder.resolve(entry.getName().substring(entry.getName().lastIndexOf('/') + 1));
					try (InputStream in = zip.getInputStream(entry)) {
						Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
					}
					count++;
					List<String> keys = parser.blockKeys(copy, "INFO");
					if (keys == null) {
						keys = parser.tags(copy).tags().stream().map(GameParser.TagValue::key).toList();
					}
					expect(copy.getFileName() + " uses only known tags: " + keys, known.containsAll(keys));
					expect(copy.getFileName() + " has its required tags", keys.contains("NAME") && keys.contains("DESC"));
				}
			}
		}
		System.out.println("OBSERVED vanilla resource text files: " + count);
		expect("vanilla resource text was checked: " + count, count >= 42);
	}

	private static Map<String, String> validResource() {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("DEGRADE_RATE", "0.5");
		values.put("COLOR", "255_255_255");
		values.put("MINIMAP_COLOR", "10_10_10");
		values.put("CATEGORY_DEFAULT", "2");
		values.put("ICON", "24->resource->Gem->0");
		values.put("SPRITE", "Gem");
		values.put("SPRITE_DEBRIS", "Wood");
		values.put("NAME", "Test Good");
		values.put("DESC", "It's for testing.");
		return values;
	}

	private static List<AssetChecker.Finding> resourceFindings(AssetSchema schema, GameAssetIndex index, String key, String value) {
		Map<String, String> values = validResource();
		if (value == null) {
			values.remove(key);
		} else {
			values.put(key, value);
		}
		return AssetChecker.check(schema, index, values);
	}

	private static void resourceRefused(AssetSchema schema, GameAssetIndex index, String key, String value) {
		expect(key + "=" + value + " is refused for a resource", resourceFindings(schema, index, key, value).stream().anyMatch(AssetChecker.Finding::blocksSaving));
	}

	private static void resourceClean(AssetSchema schema, GameAssetIndex index, String key, String value) {
		List<AssetChecker.Finding> findings = resourceFindings(schema, index, key, value);
		expect(key + "=" + value + " has no errors or warnings: " + findings, findings.isEmpty());
	}

	private static void resourceRules(AssetSchema schema, GameAssetIndex index) {
		List<AssetChecker.Finding> all = AssetChecker.check(schema, index, validResource());
		expect("a good resource has no problems: " + all, all.isEmpty());

		// A warning here would be a false alarm on vanilla, which uses 4.
		for (String fine : List.of("0", "3", "4", "5", "9")) {
			resourceClean(schema, index, "CATEGORY_DEFAULT", fine);
		}
		for (String bad : List.of("10", "11", "-1", "1.5", "abc", "")) {
			resourceRefused(schema, index, "CATEGORY_DEFAULT", bad);
		}
		resourceRefused(schema, index, "CATEGORY_DEFAULT", null);

		resourceClean(schema, index, "DEGRADE_RATE", "0");
		resourceClean(schema, index, "DEGRADE_RATE", "1");
		resourceRefused(schema, index, "DEGRADE_RATE", "1.1");
		resourceRefused(schema, index, "DEGRADE_RATE", "-0.1");
		resourceRefused(schema, index, "DEGRADE_RATE", null);
		resourceClean(schema, index, "PRICE_CAP", "1");
		resourceRefused(schema, index, "PRICE_CAP", "1.5");
		resourceClean(schema, index, "PRICE_MUL", "100");
		resourceRefused(schema, index, "PRICE_MUL", "101");
		resourceClean(schema, index, "PRICE_MUL", null);

		for (String tag : List.of("COLOR", "MINIMAP_COLOR")) {
			resourceClean(schema, index, tag, "0_128_255");
			resourceRefused(schema, index, tag, "256_0_0");
			resourceRefused(schema, index, tag, "0_0");
			resourceRefused(schema, index, tag, null);
		}

		resourceClean(schema, index, "ICON", "24->resource->Bread->0");
		resourceRefused(schema, index, "ICON", "24->resource->Nothing->0");
		resourceRefused(schema, index, "SPRITE", null);
		resourceRefused(schema, index, "SPRITE_DEBRIS", null);
		resourceRefused(schema, index, "NAME", null);
		resourceRefused(schema, index, "DESC", null);

		// Each sprite tag has its own folder.
		List<String> sprites = index.spriteOptions("sprite/resource/");
		List<String> debris = index.spriteOptions("sprite/resource/debris/");
		expect("resource sprites are offered: " + sprites, sprites.contains("Gem") && sprites.contains("Wood"));
		expect("subfolders aren't offered", !sprites.contains("debris") && !sprites.contains("growable") && sprites.stream().noneMatch(s -> s.contains("/")));
		expect("debris images are offered: " + debris, debris.equals(List.of("Metal", "Stone", "Wood")));
		expect("animal sprites aren't offered for a resource", !sprites.contains("Auroch"));
		resourceClean(schema, index, "SPRITE", "Gem");
		resourceClean(schema, index, "SPRITE_DEBRIS", "Stone");
		resourceRefused(schema, index, "SPRITE", "Auroch");
		resourceRefused(schema, index, "SPRITE_DEBRIS", "Gem");
		for (String sprite : sprites) {
			GameAssetIndex.ImageSize size = index.spriteSize("sprite/resource/", sprite);
			expect(sprite + " has the size the game requires: " + size, size.width() == 244 && size.height() >= 94);
		}
		for (String image : debris) {
			GameAssetIndex.ImageSize size = index.spriteSize("sprite/resource/debris/", image);
			expect(image + " debris has the size the game requires: " + size, size.width() == 716 && size.height() >= 28);
		}

		AssetType type = new AssetType("Resource", "resource", "resource", null);
		expect("a new resource name works", AssetCreator.problemWithName(type, "NEW_GOOD", index) == null);
		for (String bad : List.of("GEM", "gem", "_WOOD", "_HIDDEN", "BAD NAME")) {
			expect("the resource name '" + bad + "' is refused", AssetCreator.problemWithName(type, bad, index) != null);
		}
	}

	private static void resourceCreation(AssetCatalog.Entry resource, Path mods, GameParser parser, GameParser.GameVersion version, Path game) throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Resource Test");
		Path root = mod.root();
		GameAssetIndex index = GameAssetIndex.load(game, AssetCreator.versionFolder(root, version));
		AssetSchema schema = resource.schema();

		long before = countFiles(root);
		Map<String, String> bad = validResource();
		bad.put("CATEGORY_DEFAULT", "10");
		expectRefused(root, resource, "BAD_GOOD", bad, parser, index, "CATEGORY_DEFAULT");
		expect("a refused resource leaves nothing behind", countFiles(root) == before);

		Map<String, String> values = validResource();
		values.put("CATEGORY_DEFAULT", "4");
		values.put("PRICE_MUL", "2.5");
		values.put("NAMES", "Test Goods");
		AssetCreator.Created created = AssetCreator.create(root, resource.type(), schema, "TEST_GOOD", values, parser, index);
		Path assets = root.resolve("V" + version.major()).resolve("assets");
		expect("the resource's data file is in place", created.dataFile().equals(assets.resolve("init/resource/TEST_GOOD.txt")) && Files.isRegularFile(created.dataFile()));
		expect("the resource's text file is in place", created.textFile().equals(assets.resolve("text/resource/TEST_GOOD.txt")) && Files.isRegularFile(created.textFile()));
		List<AssetChecker.Finding> findings = AssetVanillaCheck.check(schema, index, created.dataFile(), parser);
		expect("the new resource passes the vanilla check: " + findings, findings.isEmpty());
		List<GameParser.ReadResult> reads = parser.read(created.dataFile(), List.of(
				new GameParser.Read(List.of(), "d", List.of("DEGRADE_RATE", 0.0, 1.0)),
				new GameParser.Read(List.of(), "i", List.of("CATEGORY_DEFAULT", 0, 3)),
				new GameParser.Read(List.of(), "i", List.of("CATEGORY_DEFAULT", 0, 10)),
				new GameParser.Read(List.of(), "d", List.of("PRICE_MUL", 0.0, 100.0)),
				new GameParser.Read(List.of(), "value", List.of("SPRITE")),
				new GameParser.Read(List.of(), "value", List.of("SPRITE_DEBRIS")),
				new GameParser.Read(List.of(), "value", List.of("MINIMAP_COLOR"))));
		expect("DEGRADE_RATE reads back", Double.valueOf(0.5).equals(reads.get(0).value()));
		expect("category 4 passes the game's read with its stated 3: " + reads.get(1), Integer.valueOf(4).equals(reads.get(1).value()));
		expect("and the read with 10", Integer.valueOf(4).equals(reads.get(2).value()));
		expect("PRICE_MUL reads back", Double.valueOf(2.5).equals(reads.get(3).value()));
		expect("SPRITE reads back", "Gem".equals(reads.get(4).value()));
		expect("SPRITE_DEBRIS reads back", "Wood".equals(reads.get(5).value()));
		expect("MINIMAP_COLOR reads back", "10_10_10".equals(reads.get(6).value()));
		String file = Files.readString(created.dataFile());
		expect("a price that wasn't added isn't written: " + file, !file.contains("PRICE_CAP"));

		// An animal can name the new resource, which is how the in-game test obtains it.
		GameAssetIndex refreshed = GameAssetIndex.load(game, AssetCreator.versionFolder(root, version));
		expect("the new resource is offered to other files", refreshed.options(TagSpec.Reference.RESOURCES).contains("TEST_GOOD"));
		expect("a refreshed index refuses the resource's name", AssetCreator.problemWithName(resource.type(), "TEST_GOOD", refreshed) != null);
		String beforeOverwrite = file;
		expectRefused(root, resource, "TEST_GOOD", validResource(), parser, index, "");
		expect("the existing resource is untouched", Files.readString(created.dataFile()).equals(beforeOverwrite));
	}

	/** A mod's own images count, and each must have the size its own tag needs. */
	private static void resourceSprites(AssetSchema schema, Path mods, GameParser.GameVersion version, Path game) throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Resource Sprites");
		Path assets = AssetCreator.versionFolder(mod.root(), version).resolve("assets");
		Path sprites = assets.resolve("sprite/resource");
		Path debris = sprites.resolve("debris");
		Files.createDirectories(debris);
		Files.write(sprites.resolve("RightSize.png"), pngHeader(244, 94));
		Files.write(sprites.resolve("Taller.png"), pngHeader(244, 120));
		Files.write(sprites.resolve("TooWide.png"), pngHeader(245, 94));
		Files.write(sprites.resolve("TooShort.png"), pngHeader(244, 93));
		Files.write(sprites.resolve("DebrisShaped.png"), pngHeader(716, 28));
		Files.write(debris.resolve("RightDebris.png"), pngHeader(716, 28));
		Files.write(debris.resolve("ShortDebris.png"), pngHeader(716, 27));
		Files.write(debris.resolve("SpriteShaped.png"), pngHeader(244, 94));
		GameAssetIndex index = GameAssetIndex.load(game, AssetCreator.versionFolder(mod.root(), version));

		expect("a mod's resource sprite is offered", index.spriteOptions("sprite/resource/").contains("RightSize"));
		expect("a mod's debris image is offered", index.spriteOptions("sprite/resource/debris/").contains("RightDebris"));
		expect("a debris image isn't offered as a sprite", !index.spriteOptions("sprite/resource/").contains("RightDebris"));
		expect("a sprite isn't offered as debris", !index.spriteOptions("sprite/resource/debris/").contains("RightSize"));
		resourceClean(schema, index, "SPRITE", "RightSize");
		resourceClean(schema, index, "SPRITE", "Taller");
		resourceRefused(schema, index, "SPRITE", "TooWide");
		resourceRefused(schema, index, "SPRITE", "TooShort");
		resourceRefused(schema, index, "SPRITE", "DebrisShaped");
		resourceClean(schema, index, "SPRITE_DEBRIS", "RightDebris");
		resourceRefused(schema, index, "SPRITE_DEBRIS", "ShortDebris");
		resourceRefused(schema, index, "SPRITE_DEBRIS", "SpriteShaped");
	}

	/** Vanilla is the test: a false alarm here would mean warning about the base game. */
	private static void producers(Path mods, GameParser parser, GameParser.GameVersion version, Path game) throws Exception {
		ModEntry mod = ModCreator.create(mods, game, "Producer Test");
		Path versionFolder = AssetCreator.versionFolder(mod.root(), version);
		Path rooms = versionFolder.resolve("assets/init/room");
		Files.createDirectories(rooms);

		ResourceProducers vanilla = ResourceProducers.load(game, versionFolder, parser);
		int checked = 0;
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				String name = entries.nextElement().getName();
				if (name.matches("data/assets/init/resource/[^/]+\\.txt")) {
					String resource = name.substring(name.lastIndexOf('/') + 1, name.length() - 4);
					expect("vanilla's " + resource + " has a producer", vanilla.produces(resource));
					checked++;
				}
			}
		}
		expect("every vanilla resource was checked: " + checked, checked == 42);
		expect("a leading _ works either way", vanilla.produces("STONE") && vanilla.produces("_STONE") && vanilla.produces("LIVESTOCK"));
		expect("a new resource has no producer", !vanilla.produces("UNOBTAINIUM"));

		// A new resource warns, and each kind of producer clears it, or doesn't.
		Path resources = versionFolder.resolve("assets/init/resource");
		Files.createDirectories(resources);
		Files.writeString(resources.resolve("UNOBTAINIUM.txt"), "DEGRADE_RATE: 0,\n");
		expect("a mod's resource with no producer is listed", unproduced(game, versionFolder, parser).equals(List.of("UNOBTAINIUM")));
		String warning = ResourceProducers.warning(List.of("UNOBTAINIUM"));
		expect("the warning names it and says what's needed: " + warning, warning.contains("UNOBTAINIUM") && warning.contains("crash") && warning.contains("OUT"));
		expect("nothing to warn about gives no warning", ResourceProducers.warning(List.of()) == null);

		producerCase(game, versionFolder, parser, rooms, "WORKSHOP_TEST", simpleRoom("IN: { WOOD: 1, },", "UNOBTAINIUM: 1,"), true, "a workshop");
		producerCase(game, versionFolder, parser, rooms, "REFINER_TEST", simpleRoom("", "UNOBTAINIUM: { PLAYER: 1, AI_RATE: 2, },"), true, "a refiner with its own AI rate");
		producerCase(game, versionFolder, parser, rooms, "PASTURE_TEST", simpleRoom("", "UNOBTAINIUM: { PLAYER: 1, },"), true, "a rate that falls back on PLAYER");
		producerCase(game, versionFolder, parser, rooms, "FARM_TEST", "INDUSTRY: { OUT: { UNOBTAINIUM: 1, }, },\n", true, "a farm's single INDUSTRY");
		producerCase(game, versionFolder, parser, rooms, "WORKSHOP_TEST", simpleRoom("", "UNOBTAINIUM: { PLAYER: 1, AI_RATE: 0, },"), false, "an AI rate of 0");
		producerCase(game, versionFolder, parser, rooms, "WORKSHOP_TEST", simpleRoom("", "UNOBTAINIUM: 0,"), false, "a rate of 0");
		producerCase(game, versionFolder, parser, rooms, "WORKSHOP_TEST", simpleRoom("IN: { UNOBTAINIUM: 1, },", "WOOD: 1,"), false, "only using it as an input");
		producerCase(game, versionFolder, parser, rooms, "WORKSHOP_TEST", simpleRoom("IN: { NOTHING_MAKES_THIS: 1, },", "UNOBTAINIUM: 1,"), false, "an input nothing produces");
		producerCase(game, versionFolder, parser, rooms, "HUNTER_TEST", simpleRoom("", "UNOBTAINIUM: 1,"), false, "a hunter, whose output isn't a recipe");
		producerCase(game, versionFolder, parser, rooms, "MY_ROOM", simpleRoom("", "UNOBTAINIUM: 1,"), false, "a file the game doesn't load as a room");
		producerCase(game, versionFolder, parser, rooms, "WORKSHOPX", simpleRoom("", "UNOBTAINIUM: 1,"), false, "a name that only starts like a type");
		producerCase(game, versionFolder, parser, rooms, "WORKSHOP_TEST", "This isn't valid {{{", false, "a room that can't be read");

		// An input that a mod's own room makes is fine, in either order.
		Files.writeString(rooms.resolve("REFINER_ONE.txt"), simpleRoom("IN: { STEP_TWO: 1, },", "UNOBTAINIUM: 1,"));
		Files.writeString(rooms.resolve("REFINER_TWO.txt"), simpleRoom("IN: { WOOD: 1, },", "STEP_TWO: 1,"));
		expect("a chain of producers clears the warning", unproduced(game, versionFolder, parser).isEmpty());
		Files.delete(rooms.resolve("REFINER_ONE.txt"));
		Files.delete(rooms.resolve("REFINER_TWO.txt"));

		// A mine makes what its minable file names.
		Path minable = versionFolder.resolve("assets/init/resource/minable");
		Files.createDirectories(minable);
		Files.writeString(minable.resolve("DEEP.txt"), "RESOURCE: UNOBTAINIUM,\n");
		producerCase(game, versionFolder, parser, rooms, "MINE_TEST", "MINABLE: DEEP,\nYEILD_WORKER_DAILY: 1,\n", true, "a mine");
		producerCase(game, versionFolder, parser, rooms, "MINE_TEST", "MINABLE: DEEP,\nYEILD_WORKER_DAILY: 0,\n", false, "a mine that yields nothing");
		producerCase(game, versionFolder, parser, rooms, "MINE_TEST", "MINABLE: MISSING,\nYEILD_WORKER_DAILY: 1,\n", false, "a mine of something else");
		Files.delete(minable.resolve("DEEP.txt"));
		Files.delete(minable);

		// A mod's room replaces vanilla's of the same name.
		Files.writeString(rooms.resolve("REFINER_BAKERY.txt"), simpleRoom("", "UNOBTAINIUM: 1,"));
		ResourceProducers replaced = ResourceProducers.load(game, versionFolder, parser);
		expect("a replaced room counts for what it makes now", replaced.produces("UNOBTAINIUM"));
		expect("and not for what it made before, if nothing else makes it: BREAD " + replaced.produces("BREAD"), !replaced.produces("BREAD"));
		Files.delete(rooms.resolve("REFINER_BAKERY.txt"));
		Files.writeString(rooms.resolve("_IgnoreVanilla.txt"), "");
		expect("ignoring vanilla's rooms drops their products", !ResourceProducers.load(game, versionFolder, parser).produces("GEM"));
		Files.delete(rooms.resolve("_IgnoreVanilla.txt"));

		long installFiles = countFiles(game.resolve("base"));
		ResourceProducers.load(game, versionFolder, parser);
		expect("reading leaves the game folder alone", countFiles(game.resolve("base")) == installFiles);
		Files.delete(resources.resolve("UNOBTAINIUM.txt"));
		expect("a mod with no resources has nothing to warn about", unproduced(game, versionFolder, parser).isEmpty());
	}

	private static String simpleRoom(String in, String out) {
		return "INDUSTRIES: [\n\t{\n\t\tINDUSTRY: {\n\t\t\t" + in + "\n\t\t\tOUT: {\n\t\t\t\t" + out + "\n\t\t\t},\n\t\t},\n\t},\n],\n";
	}

	private static List<String> unproduced(Path game, Path versionFolder, GameParser parser) throws IOException {
		return ResourceProducers.load(game, versionFolder, parser).unproducedIn(versionFolder);
	}

	private static void producerCase(Path game, Path versionFolder, GameParser parser, Path rooms, String room, String content, boolean clears, String what) throws IOException {
		Path file = rooms.resolve(room + ".txt");
		Files.writeString(file, content);
		List<String> missing = unproduced(game, versionFolder, parser);
		expect(what + (clears ? " clears" : " doesn't clear") + " the warning: " + missing, missing.isEmpty() == clears);
		Files.delete(file);
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

	/** The keys and the checked values were read off Race.java and the classes it hands the file to. */
	private static void raceTable(AssetSchema schema) {
		List<String> data = schema.dataTags().stream().map(TagSpec::key).toList();
		expect("the race file's tags: " + data, data.equals(List.of(
				"PLAYABLE", "PROPERTIES", "SPRITE_FILE", "HOME", "BIO_FILE", "BIO_FILE_SPECIFIC", "KING_FILE", "WORLD_NAME_FILE",
				"RAID_TEXT_FILE", "RAIDER_NAME_FILE", "TOURIST", "PREFERRED", "POPULATION", "STATS", "RESOURCE", "BOOST",
				"EQUIPMENT_NOT_ENABLED", "EQUIPMENT_ENABLED", "TRAIT", "RESOURCE_GROOMING", "MILITARY_SUPPLY_USE",
				"STATS_ON_SPAWN", "ROOM_FLOOR_OVERRIDE", "ICON_SMALL", "ICON_BIG")));
		expectTag(schema, "PLAYABLE", TagSpec.Kind.BOOL, true, null, null, null, true);
		expectTag(schema, "POPULATION", TagSpec.Kind.BLOCK, false, "MAX 1, growth 0.0001, every climate 0, every terrain 0", null, null, true);
		expectTag(schema, "MILITARY_SUPPLY_USE", TagSpec.Kind.DECIMAL_MAP, false, null, null, null, true);
		expectTag(schema, "TRAIT", TagSpec.Kind.DECIMAL_MAP, false, "0 for every trait", 0.0, 1.0, true);
		// Tags in vanilla files that no reviewed read uses aren't offered, so the vanilla check warns about them, as it does for a mine's SPRITE.
		for (String unread : List.of("TRAITS", "TECH", "PREFERRED.EMPLOYED", "PREFERRED.BUILDING_OVERRIDE", "file.sprite.TYPES.0.COLOR_EXTRA1", "file.sprite.TYPES.0.PORTRAIT_FILE",
				"file.sprite.TYPES.0.SET_APPEARANCE_STATS", "file.bio.HOME_NOBLE", "file.world.DIPLOMACY_GREETING")) {
			expect(unread + " isn't offered", schema.find(unread) == null);
		}
		// RacePopulation reads these with readFill, which leaves every entry at 0 when the block is absent (RMAP.java:211-214,302).
		TagSpec climate = schema.find("POPULATION.CLIMATE");
		expect("a population's climates can be left out", climate != null && !climate.required() && "0 for every climate".equals(climate.gameDefault()));
		TagSpec terrain = schema.find("POPULATION.TERRAIN");
		expect("and its terrains", terrain != null && !terrain.required() && "0 for every terrain".equals(terrain.gameDefault()));
		TagSpec citizen = schema.find("STATS.LOYALTY.CITIZEN");
		expect("a class's standing defaults to 0", citizen != null && "0".equals(citizen.gameDefault()));
		TagSpec prio = schema.find("STATS.LOYALTY.PRIO");
		expect("an absent priority keeps the stat's own (RaceStats.java:62-63)", prio != null && "The stat's own priority".equals(prio.gameDefault()));
		// Json.jsons(key, min, max) refuses as many entries as max (Json.java:228-229): RAppearence asks for 1 to 4 types.
		TagSpec types = schema.find("file.sprite.TYPES");
		expect("a race has at most 3 types", types != null && types.shape().maxEntries() == 3);
		TagSpec furniture = schema.find("file.home.CITIZEN.BED_1x1_TOP.0.RESOURCES");
		expect("every home sheet needs its RESOURCES block (RaceHomeSheet.java:44)", furniture != null && furniture.required());
		expect("the maps people leave alone start folded", "Road preference".equals(schema.foldHeadings().get("PREFERRED.ROAD")) && schema.foldHeadings().keySet().stream().allMatch(key -> schema.find(key) != null));
		List<String> files = schema.companions().stream().map(AssetSchema.Companion::id).toList();
		expect("a race's files: " + files, files.equals(List.of(
				"sprite", "home", "bio", "bioSpecific", "king", "world", "raid", "raiderNames", "tourist", "namesNoble",
				"namesChildFirst", "namesChildSurname", "names0First", "names0Surname", "names1First", "names1Surname",
				"names2First", "names2Surname")));
		expect("the appearance is chosen from the vanilla sprite files", "init/race/sprite".equals(schema.appearanceFolder()));
		TagSpec sleep = schema.tag("file.sprite.SLEEP_FILE");
		expect("a sleep sheet is 164 wide and at least 44 tall", sleep != null && sleep.sprite() != null && sleep.sprite().width() == 164 && sleep.sprite().minHeight() == 44 && !sleep.sprite().exactHeight());
		TagSpec crown = schema.find("file.sprite.CROWN.FILE");
		expect("a missing crown sheet warns and is skipped", crown != null && crown.kind() == TagSpec.Kind.SPRITE && crown.sprite().missingWarns() && crown.sprite().width() == 104 && crown.sprite().minHeight() == 36);
		TagSpec frame = schema.find("file.sprite.TYPES.0.FACE.0.FRAME_RANDOM");
		expect("an absent portrait frame uses its own number", frame != null && frame.gameDefault() == null && Double.valueOf(0).equals(frame.min()) && Double.valueOf(16).equals(frame.max()) && !frame.maxEnforced());
		TagSpec bioTrait = schema.tag("file.bio.TRAIT");
		expect("a biography's traits are lines, and an unknown name isn't a warning", bioTrait != null && bioTrait.kind() == TagSpec.Kind.KEYED_TEXTS && bioTrait.required() && bioTrait.reference() == null);
		expect("text has the race's own lines", schema.tag("POSSESSIVE") != null && schema.tag("ARMY_NAMES") != null && schema.tag("PRONOUN_HE") != null);
	}

	private static List<String> raceClasses() {
		List<String> classes = new ArrayList<>(List.of(
				"init/race/Race", "init/race/RaceInfo", "init/race/RaceInfo$RPronoun", "init/race/RaceInfo$RaceWorldInfo", "init/race/Physics",
				"game/boosting/BoostSpecs", "init/race/RacePopulation", "init/race/RacePreferrence", "world/region/building/RDBuildings",
				"init/race/RaceStats", "init/race/RaceResources", "settlement/stats/util/StatsJson", "settlement/stats/standing/StatStanding$StandingDef",
				"init/race/appearence/RAppearence", "init/race/appearence/RColors", "init/race/appearence/RType", "init/race/appearence/RAddon",
				"init/race/appearence/RPortraitFrame", "init/race/appearence/RCrown", "init/race/appearence/RFloors", "init/race/appearence/RNames",
				"init/race/appearence/RaceSheet", "init/race/appearence/RExtras", "init/race/appearence/RPortrait", "world/map/regions/centre/WorldRaceSheet",
				"init/race/home/RaceHome", "init/race/home/RaceHomeClass", "init/race/home/RaceHomeSheet", "init/sprite/game/SheetData",
				"init/race/bio/Bio", "init/race/bio/BioLine", "init/race/bio/BioLines", "init/race/bio/BioOpinion", "init/race/KingMessages",
				"game/raiding/RaiderTextsRace", "game/tourism/TourismRace", "settlement/stats/standing/StatStanding$StandingDef$StandingData",
				"init/race/appearence/RColors$ColorCollection", "init/race/appearence/RType$RTypeSpec", "init/race/KingMessages$Message", "game/tourism/Text",
				"init/race/RacePreferrence$1", "init/race/RacePreferrence$2", "init/race/RacePreferrence$3", "init/race/RacePreferrence$5",
				"init/race/RacePreferrence$6", "init/race/RacePreferrence$7", "init/race/RacePreferrence$8", "init/race/appearence/RFloors$1",
				"init/race/RaceStats$1", "init/race/RaceStats$2", "world/map/regions/centre/WorldRaceSheet$Town", "world/map/regions/centre/WorldRaceSheet$Village",
				"world/map/regions/centre/WorldRaceSheet$Overlay", "world/map/regions/centre/WorldRaceSheet$Terrain", "world/map/regions/centre/WorldRaceSheet$Farm",
				"world/map/regions/centre/WorldRaceSheet$WallSteriods", "snake2d/util/color/ColorImp", "init/type/TRAITS$2", "init/resources/ResSupplies$1",
				"init/resources/RES_AMOUNT", "init/race/RACES", "init/race/appearence/RaceFrameMaker", "init/race/bio/BioLines$Friend", "init/race/bio/BioLines$Origin",
				"init/race/bio/BioOpinionData", "init/race/bio/BioOpinionData$1", "init/race/bio/BioOpinionData$2", "game/boosting/BoostSpecs$ParseValue",
				"game/tourism/Text$Entry", "world/region/building/RDBuildings$3", "init/value/Lockable", "init/type/TRAITS", "init/resources/ResSupplies"));
		for (int i = 1; i <= 23; i++) {
			classes.add("init/race/bio/BioLines$" + i);
		}
		return classes;
	}

	/** Dropping any reviewed class, or renaming a key it reads, turns Race off. */
	private static void raceGate(Path game, Path temp) throws Exception {
		List<String> classes = raceClasses();
		AssetCatalog.Entry control = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve("race-all.jar"), classes)), "Race");
		expect("these classes are enough for Race: " + control.unavailableReason(), control.available());
		int at = 0;
		for (String gone : classes) {
			AssetCatalog.Entry entry = entryNamed(AssetCatalog.load(jarWith(game, temp.resolve("race-without-" + at++ + ".jar"), classes.stream().filter(n -> !n.equals(gone)).toList())), "Race");
			expect("without " + gone + " Race is off: " + entry.unavailableReason(), !entry.available() && entry.unavailableReason() != null);
		}
		for (String[] change : new String[][] {
				{ "init/race/Race", "PLAYABLE", "PLAYABLX" },
				{ "init/race/bio/BioOpinionData", "STATS_MORE", "STATS_MORX" },
				{ "init/race/appearence/RaceFrameMaker", "FRAMES", "FRAMEX" } }) {
			AssetCatalog.Entry changed = entryNamed(AssetCatalog.load(patchedJar(game, temp.resolve("race-changed-" + change[1] + ".jar"), classes, change[0], change[1], change[2])), "Race");
			expect("renaming " + change[1] + " turns Race off: " + changed.unavailableReason(), !changed.available());
		}
	}

	/** Every vanilla race, its text, and each file it names. A warning has to say the other form is kept. */
	private static void vanillaRaces(Path game, AssetSchema schema, GameAssetIndex index, Path temp, GameParser parser) throws IOException, GameParser.UnavailableException {
		Path folder = temp.resolve("vanilla-races");
		Files.createDirectories(folder);
		int count = 0;
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (!entry.getName().matches("data/assets/init/race/[^/_][^/]*\\.txt")) {
					continue;
				}
				String stem = entry.getName().substring(entry.getName().lastIndexOf('/') + 1).replace(".txt", "");
				Path copy = folder.resolve(stem + ".txt");
				try (InputStream in = zip.getInputStream(entry)) {
					Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
				}
				count++;
				List<AssetChecker.Finding> findings = AssetVanillaCheck.check(schema, index, copy, parser);
				expect(stem + " has no errors, and warns only about tags no reviewed read uses: " + findings, findings.stream().allMatch(AssetSchemaTest::unreadRaceTag));
				expect(stem + " warns that TRAITS isn't read, because the game reads TRAIT (TRAITS.java:44,76)", findings.stream().anyMatch(f -> "TRAITS".equals(f.tag())));
				checkRaceText(zip, schema, folder, stem, parser);
				checkRaceFiles(zip, schema, index, folder, stem, parser);
			}
		}
		expect("all 8 vanilla races were checked: " + count, count == 8);
	}

	private static void checkRaceText(ZipFile zip, AssetSchema schema, Path folder, String stem, GameParser parser) throws IOException {
		ZipEntry entry = zip.getEntry("data/assets/text/race/" + stem + ".txt");
		expect(stem + " has a text file", entry != null);
		if (entry == null) {
			return;
		}
		Path copy = folder.resolve(stem + "-text.txt");
		try (InputStream in = zip.getInputStream(entry)) {
			Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
		}
		List<String> known = schema.textTags().stream().map(TagSpec::key).toList();
		List<String> keys = parser.tags(copy).tags().stream().map(GameParser.TagValue::key).toList();
		expect(stem + " text uses only known tags: " + keys, known.containsAll(keys));
		List<AssetChecker.Finding> findings = AssetVanillaCheck.checkText(schema, copy, parser);
		expect(stem + " text has no errors: " + findings, findings.stream().noneMatch(f -> f.severity() == AssetChecker.Severity.ERROR));
	}

	private static void checkRaceFiles(ZipFile zip, AssetSchema schema, GameAssetIndex index, Path folder, String stem, GameParser parser) throws IOException {
		Path data = folder.resolve(stem + ".txt");
		GameParser.Node root;
		try {
			root = parser.root(data);
		} catch (GameParser.ReadException e) {
			expect(stem + " can be read: " + e.getMessage(), false);
			return;
		}
		Map<String, GameParser.Node> opened = new LinkedHashMap<>();
		opened.put("", root);
		for (AssetSchema.Companion companion : schema.companions()) {
			String named = companionName(opened, companion.nameTag());
			if (named == null || named.isBlank()) {
				expect(stem + " may omit " + companion.id(), !companion.required());
				continue;
			}
			String relative = "data/assets/" + companion.relative(named);
			ZipEntry entry = zip.getEntry(relative);
			if (entry == null) {
				expect(stem + " names " + relative + ", which the game has", false);
				continue;
			}
			Path copy = folder.resolve(companion.id() + "-" + named + ".txt");
			try (InputStream in = zip.getInputStream(entry)) {
				Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
			}
			List<TagSpec> leaves = new ArrayList<>();
			for (TagSpec tag : companion.tags()) {
				leaves.add(tag.withKey(tag.leafName()));
			}
			AssetSchema asFile = new AssetSchema(leaves, List.of(), List.of(), List.of());
			List<AssetChecker.Finding> findings = AssetVanillaCheck.check(asFile, index, copy, parser);
			List<AssetChecker.Finding> bad = findings.stream().filter(f -> f.severity() == AssetChecker.Severity.ERROR
					|| f.severity() == AssetChecker.Severity.WARNING && !keptForm(f.message()) && !unreadRaceTag(f)).toList();
			expect(stem + " " + companion.id() + " " + named + ": " + findings, bad.isEmpty());
			try {
				opened.put(companion.id(), parser.root(copy));
			} catch (GameParser.ReadException e) {
				expect(stem + " " + named + " can be read: " + e.getMessage(), false);
			}
		}
	}

	/** A name tag may be nested, and a nameset's is inside the sprite file. */
	private static String companionName(Map<String, GameParser.Node> opened, String nameTag) {
		try {
			GameParser.Node node;
			String path;
			if (nameTag.startsWith("file.")) {
				String rest = nameTag.substring("file.".length());
				int dot = rest.indexOf('.');
				node = opened.get(rest.substring(0, dot));
				path = rest.substring(dot + 1);
			} else {
				node = opened.get("");
				path = nameTag;
			}
			return node == null ? null : readPath(node, path.split("\\."), 0);
		} catch (GameParser.ReadException e) {
			return null;
		}
	}

	private static String readPath(GameParser.Node node, String[] parts, int at) throws GameParser.ReadException {
		if (at >= parts.length || !node.has(parts[at])) {
			return null;
		}
		if (at == parts.length - 1) {
			return node.value(parts[at]);
		}
		if (at + 1 < parts.length && parts[at + 1].matches("\\d+")) {
			List<GameParser.Node> blocks = node.blocks(parts[at]);
			int index = Integer.parseInt(parts[at + 1]);
			return index < blocks.size() ? readPath(blocks.get(index), parts, at + 2) : null;
		}
		return readPath(node.block(parts[at]), parts, at + 1);
	}

	/**
	 * The top-level tags in vanilla race files that no reviewed read uses, each checked by hand: TRAITS (the
	 * game reads TRAIT), TECH, HOME_NOBLE (its read is commented out, BioLines.java:174) and DIPLOMACY_GREETING.
	 */
	private static boolean unreadRaceTag(AssetChecker.Finding finding) {
		return finding.severity() == AssetChecker.Severity.WARNING && finding.message().contains("doesn't appear to read")
				&& List.of("TRAITS", "TECH", "HOME_NOBLE", "DIPLOMACY_GREETING").contains(finding.tag());
	}

	private static boolean keptForm(String message) {
		return message.contains("stays as written") || message.contains("also accepts") || message.contains("wasn't checked") || message.contains("the game warns and skips");
	}

	private static void raceRules(AssetSchema schema, GameAssetIndex index) {
		Map<String, String> clean = new LinkedHashMap<>();
		clean.put("PREFERRED.FOOD", "*");
		clean.put("PREFERRED.DRINK", "*");
		clean.put("STATS.LOYALTY.CITIZEN", "1");
		clean.put("STATS.LOYALTY.SLAVE", "1");
		String filled = raceRule(schema, index, clean);
		// The other required tags are missing here, so "no new errors" means the same errors as this.
		expect("a filled race has none of the cross-rule errors: " + filled, !filled.contains("favourite") && !filled.contains("standing") && !filled.contains("1 to "));
		expect("food can't be empty", raceRule(schema, index, without(clean, "PREFERRED.FOOD")).contains("favourite food"));
		expect("drink can't be empty", raceRule(schema, index, without(clean, "PREFERRED.DRINK")).contains("favourite drink"));
		String edible = index.options(TagSpec.Reference.EDIBLES).get(0);
		Map<String, String> food = new LinkedHashMap<>(clean);
		food.put("PREFERRED.FOOD", "NO_SUCH_FOOD");
		expect("food with nothing the game has stops loading", raceRule(schema, index, food).contains("favourite food"));
		food.put("PREFERRED.FOOD", edible + ",NO_SUCH_FOOD");
		expect("an unknown food beside a real one is skipped, and only warned about (RMAP.java:181-190)", !raceRule(schema, index, food).contains("food") && raceWarning(schema, index, food).contains("NO_SUCH_FOOD"));
		// RaceStats counts each stat's own base standing too (RaceStats.java:45-46,80-97), and vanilla's _PUNISHMENT gives both classes one.
		Map<String, String> noStanding = without(clean, "STATS.LOYALTY.CITIZEN");
		noStanding.remove("STATS.LOYALTY.SLAVE");
		expect("a race's own STATS needn't give citizens or slaves a standing", raceRule(schema, index, noStanding).equals(filled));
		// But an entry starting with * replaces every stat's standing (StatsJson.java:33-39, RaceStats.java:57-63), so the base no longer counts.
		Map<String, String> everyStat = new LinkedHashMap<>(noStanding);
		everyStat.put("STATS.*.CITIZEN", "0");
		String wiped = raceRule(schema, index, everyStat);
		expect("* with nothing above 0 for citizens or slaves stops loading: " + wiped, wiped.contains("CITIZEN a value above 0") && wiped.contains("SLAVE a value above 0"));
		everyStat.put("STATS.POPULATION_SLAVES_OTHER.CITIZEN", "1");
		String halfWiped = raceRule(schema, index, everyStat);
		expect("one entry giving citizens a value leaves only slaves reported: " + halfWiped, !halfWiped.contains("CITIZEN a value above 0") && halfWiped.contains("SLAVE a value above 0"));
		everyStat.put("STATS.POPULATION_SLAVES_OTHER.SLAVE", "1");
		expect("and with slaves too, nothing is reported", raceRule(schema, index, everyStat).equals(filled));
		Map<String, String> someStats = new LinkedHashMap<>(noStanding);
		someStats.put("STATS.ACCESS*.CITIZEN", "0");
		expect("a wildcard with a prefix doesn't cover every stat, so it isn't reported (vanilla GARTHIMI)", raceRule(schema, index, someStats).equals(filled));
		Map<String, String> standing = new LinkedHashMap<>(clean);
		standing.put("STATS.LOYALTY.CITIZEN", "2000000");
		expect("a standing above 1000000 stops loading (StatStanding.java:337)", raceRule(schema, index, standing).contains("0 to 1000000"));
		standing.put("STATS.LOYALTY.CITIZEN", "1000000");
		expect("and 1000000 itself is allowed", raceRule(schema, index, standing).equals(filled));
		Map<String, String> boost = new LinkedHashMap<>(clean);
		boost.put("BOOST.SOMETHING", "1");
		expect("a boost key has to say add or multiply", raceRule(schema, index, boost).contains(">ADD"));
		Map<String, String> types = new LinkedHashMap<>(clean);
		for (int i = 0; i < 3; i++) {
			types.put("file.sprite.TYPES." + i + ".X", "1");
		}
		expect("3 types are allowed", raceRule(schema, index, types).equals(filled));
		types.put("file.sprite.TYPES.3.X", "1");
		expect("a 4th type is refused, because jsons refuses as many as its maximum (Json.java:228)", raceRule(schema, index, types).contains("1 to 3"));
		Map<String, String> clothes = new LinkedHashMap<>(clean);
		for (int i = 0; i < 15; i++) {
			clothes.put("file.sprite.COLOR_CLOTHES." + i + ".R", "1");
		}
		expect("15 cloth colours are allowed", raceRule(schema, index, clothes).equals(filled));
		clothes.put("file.sprite.COLOR_CLOTHES.15.R", "1");
		expect("a 16th cloth colour is refused", raceRule(schema, index, clothes).contains("1 to 15"));
		List<String> resources = index.options(TagSpec.Reference.RESOURCES);
		Map<String, String> home = new LinkedHashMap<>(clean);
		for (int i = 0; i < 9; i++) {
			home.put("file.home.CITIZEN.BED_1x1_TOP." + i + ".RESOURCES." + resources.get(i), "1");
		}
		expect("a home class can name 8 resources", raceRule(schema, index, home).contains("at most 8"));
		Map<String, String> unknownHome = new LinkedHashMap<>(clean);
		for (int i = 0; i < 8; i++) {
			unknownHome.put("file.home.CITIZEN.BED_1x1_TOP." + i + ".RESOURCES." + resources.get(i), "1");
		}
		unknownHome.put("file.home.CITIZEN.BED_1x1_TOP.8.RESOURCES.NO_SUCH_RESOURCE", "1");
		expect("a resource the game skips doesn't count toward the 8 (RaceHomeClass.java:73-75)", raceRule(schema, index, unknownHome).equals(filled));
		Map<String, String> amount = new LinkedHashMap<>(clean);
		amount.put("file.home.CITIZEN.BED_1x1_TOP.0.RESOURCES." + resources.get(0), "0");
		expect("a home amount below 1 stops loading (RaceHomeClass.java:77)", raceRule(schema, index, amount).contains("lowest amount"));
		amount.put("file.home.CITIZEN.BED_1x1_TOP.0.RESOURCES." + resources.get(0), "1.5");
		expect("and so does one that isn't whole", raceRule(schema, index, amount).contains("whole number"));
		amount.put("file.home.CITIZEN.BED_1x1_TOP.0.RESOURCES." + resources.get(0), "20");
		expect("above 15 only warns, because Json.i doesn't enforce it", raceRule(schema, index, amount).equals(filled) && raceWarning(schema, index, amount).contains("maximum is 15"));
		Map<String, String> addon = new LinkedHashMap<>(clean);
		addon.put("file.sprite.TYPES.0.ADDONS.0.ADDON_INDEX", "8");
		expect("addon index 8 is past the end", raceRule(schema, index, addon).contains("0 to 7"));
		Map<String, String> supply = new LinkedHashMap<>(clean);
		String supplyName = index.options(TagSpec.Reference.SUPPLIES).get(0);
		supply.put("MILITARY_SUPPLY_USE." + supplyName, "-1");
		expect("a supply amount can't be negative", raceRule(schema, index, supply).contains("lowest value the game accepts is 0"));
		supply.remove("MILITARY_SUPPLY_USE." + supplyName);
		supply.put("MILITARY_SUPPLY_USE.NO_SUCH_SUPPLY", "1.5");
		expect("an unknown supply is skipped before its amount is read, so its amount isn't reported", raceRule(schema, index, supply).equals(filled));
		Map<String, String> color = new LinkedHashMap<>(clean);
		color.put("file.sprite.COLORS.SKIN.VALUES.0.R", "1");
		color.put("file.sprite.TYPES.0.COLOR_SKIN", "NO_SUCH");
		expect("a skin colour has to name one in COLORS", raceRule(schema, index, color).contains("COLORS"));
		color.put("file.sprite.TYPES.0.COLOR_SKIN", "HAIR");
		expect("a collection kept as written still counts, as Q_AMEVIA's generated HAIR does",
				AssetChecker.checkNew(schema, index, color, Set.of("file.sprite.COLORS.HAIR.VALUES")).stream().noneMatch(f -> f.message().contains("COLORS")));
		Map<String, String> floors = new LinkedHashMap<>(clean);
		floors.put("ROOM_FLOOR_OVERRIDE.HOME_HOUSE", "NO_SUCH_FLOOR");
		expect("an unknown floor in a room's override stops loading, because readMany refuses it (RMAP.java:147)", raceRule(schema, index, floors).contains("NO_SUCH_FLOOR"));
		Map<String, String> pool = new LinkedHashMap<>(clean);
		pool.put("PREFERRED.POOL.FARM_WHEAT", "1");
		// RacePreferrence only builds an error message for a room that isn't a pool, and drops it (RacePreferrence.java:170-174, Json.java:355-357).
		expect("a room that isn't a pool doesn't block saving", raceRule(schema, index, pool).equals(filled));
		expect("but is warned about", raceWarning(schema, index, pool).contains("FARM_WHEAT"));
		pool.remove("PREFERRED.POOL.FARM_WHEAT");
		pool.put("PREFERRED.POOL.*", "1");
		expect("a wildcard pool is allowed", raceRule(schema, index, pool).equals(filled) && !raceWarning(schema, index, pool).contains("POOL"));
	}

	private static String raceWarning(AssetSchema schema, GameAssetIndex index, Map<String, String> values) {
		StringBuilder text = new StringBuilder();
		for (AssetChecker.Finding finding : AssetChecker.check(schema, index, values)) {
			if (finding.severity() == AssetChecker.Severity.WARNING) {
				text.append(finding.tag()).append(": ").append(finding.message()).append('\n');
			}
		}
		return text.toString();
	}

	private static Map<String, String> without(Map<String, String> values, String key) {
		Map<String, String> copy = new LinkedHashMap<>(values);
		copy.remove(key);
		return copy;
	}

	/**
	 * The game applies STATS entries in order, each replacing a stat's standing (StatsJson.java:31-47, RaceStats.java:57-63).
	 * A * added to a look's STATS was once written last, so it wiped out the entries before it. It now goes first.
	 */
	private static void raceWildcardOrder(AssetCatalog.Entry entry, ModEntry mod, GameParser parser, Path game) throws Exception {
		AssetSchema schema = entry.schema();
		GameAssetIndex index = GameAssetIndex.load(game, AssetCreator.versionFolder(mod.root(), parser.version()), parser);
		String look = "DONDORIAN";
		Map<String, String> values = syx.core.AssetForm.startingValues(schema, index, look, "NF_WILDCARD");
		expect("the look already has POPULATION_SLAVES_OTHER", values.keySet().stream().anyMatch(key -> key.startsWith("STATS.POPULATION_SLAVES_OTHER.")));
		values.put("STATS.*.CITIZEN", "0");
		values.put("STATS.POPULATION_SLAVES_OTHER.CITIZEN", "1");
		values.put("STATS.POPULATION_SLAVES_OTHER.SLAVE", "1");
		AssetCreator.Created created = AssetCreator.create(mod.root(), entry.type(), schema, "NF_WILDCARD", values, look, parser, index);
		String text = Files.readString(created.dataFile());
		int stats = text.indexOf("STATS:");
		int wildcard = text.indexOf("*:", stats);
		int specific = text.indexOf("POPULATION_SLAVES_OTHER:", stats);
		expect("a new * is written before the entries it would otherwise wipe out", stats >= 0 && wildcard > stats && specific > wildcard);
		List<AssetChecker.Finding> errors = AssetVanillaCheck.check(schema, index, created.dataFile(), parser).stream().filter(f -> f.severity() == AssetChecker.Severity.ERROR).toList();
		expect("and the race has no errors: " + errors, errors.isEmpty());

		// A row written fresh, with no look's STATS to merge onto, follows the same order.
		Map<String, Map<String, String>> rows = new LinkedHashMap<>();
		rows.put("LOYALTY", Map.of("CITIZEN", "1"));
		rows.put("*", Map.of("CITIZEN", "0"));
		syx.core.GameTree.Block block = syx.core.GameTree.Block.empty();
		rows.keySet().forEach(row -> block.put(row, syx.core.GameTree.Block.empty()));
		List<String> order = new ArrayList<>(AssetCreator.wildcardsFirst(schema.tag("STATS"), block, List.of("*")).entries().keySet());
		expect("wildcardsFirst puts a new * first: " + order, order.equals(List.of("*", "LOYALTY")));
	}

	private static String raceRule(AssetSchema schema, GameAssetIndex index, Map<String, String> values) {
		StringBuilder text = new StringBuilder();
		for (AssetChecker.Finding finding : AssetChecker.check(schema, index, values)) {
			if (finding.severity() == AssetChecker.Severity.ERROR) {
				text.append(finding.message()).append('\n');
			}
		}
		return text.toString();
	}

	/**
	 * A new race from every look, under its own name. Each file is read back the way its loader reads it,
	 * and what the form can't show in the look's files is still there.
	 */
	private static void raceCreation(AssetCatalog.Entry entry, ModEntry mod, GameParser parser, Path game) throws Exception {
		AssetSchema schema = entry.schema();
		GameAssetIndex index = GameAssetIndex.load(game, AssetCreator.versionFolder(mod.root(), parser.version()), parser);
		List<String> looks = syx.core.AssetForm.appearances(schema, index);
		expect("every vanilla look can start a race: " + looks, looks.size() == 8);
		for (String look : looks) {
			String name = "NF_" + look;
			Map<String, String> values = syx.core.AssetForm.startingValues(schema, index, look, name);
			Set<String> kept = syx.core.AssetForm.startingKept(schema, index, look);
			List<AssetChecker.Finding> blocking = AssetChecker.checkNew(schema, index, syx.core.AssetForm.withFormBlanks(schema, values, true), kept).stream()
					.filter(AssetChecker.Finding::blocksSaving).toList();
			expect(look + ": a new race from it can be saved: " + blocking, blocking.isEmpty());
			if (!blocking.isEmpty()) {
				continue;
			}
			AssetCreator.Created created;
			try {
				created = AssetCreator.create(mod.root(), entry.type(), schema, name, values, look, parser, index);
			} catch (AssetCreator.CreationException e) {
				expect(look + ": a new race from it is written: " + e.getMessage(), false);
				continue;
			}
			List<AssetChecker.Finding> data = AssetVanillaCheck.check(schema, index, created.dataFile(), parser);
			expect(look + ": the new race file has no errors, and the same warnings as vanilla's: " + data, data.stream().allMatch(AssetSchemaTest::unreadRaceTag));
			Map<String, syx.core.GameTree.Block> trees = new LinkedHashMap<>();
			trees.put("", syx.core.GameTree.read(parser.root(created.dataFile())));
			trees.put("text", syx.core.GameTree.read(parser.root(created.textFile())));
			// RaceInfo reads its own lines from the whole text file, and only INFO's tags from an INFO block (RaceInfo.java:49-53, INFO.java:16-25).
			// The toolkit once wrote them inside INFO, and the game stopped loading with "Missing a String with key: POSSESSIVE".
			GameParser.Node textRoot = parser.root(created.textFile());
			for (String key : List.of("POSSESSIVE", "POSSESSIVES", "DESC_LONG")) {
				expect(look + ": the game finds " + key + " at the top of the text file", textRoot.has(key) && textRoot.text(key).equals(values.get(key)));
				expect(look + ": and there's no unread copy of " + key + " inside INFO", !textRoot.isBlock("INFO") || !textRoot.block("INFO").has(key));
			}
			List<AssetChecker.Finding> textFindings = AssetVanillaCheck.checkText(schema, created.textFile(), parser);
			expect(look + ": the new race's text reads as RaceInfo reads it: " + textFindings, textFindings.stream().noneMatch(f -> f.severity() == AssetChecker.Severity.ERROR));
			for (Path file : created.companionFiles()) {
				String folder = file.getParent().toString().replace('\\', '/');
				AssetSchema.Companion companion = schema.companions().stream().filter(c -> folder.endsWith("/assets/" + c.folder())).findFirst().orElseThrow();
				List<TagSpec> leaves = new ArrayList<>();
				for (TagSpec tag : companion.tags()) {
					leaves.add(tag.withKey(tag.leafName()));
				}
				List<AssetChecker.Finding> findings = AssetVanillaCheck.check(new AssetSchema(leaves, List.of(), List.of(), List.of()), index, file, parser);
				expect(look + " " + file.getFileName() + " has no errors: " + findings, findings.stream().noneMatch(f -> f.severity() == AssetChecker.Severity.ERROR));
				// Name files share one folder, so only the others are told apart here.
				if (!companion.id().startsWith("names")) {
					trees.put(companion.id(), syx.core.GameTree.read(parser.root(file)));
				}
			}
			// RAppearence reads jsons("TYPES", 1, 4), and jsons refuses as many as its maximum (Json.java:228-229).
			GameParser.Node sprite = parser.root(created.companionFiles().stream().filter(file -> file.toString().replace('\\', '/').contains("init/race/sprite/")).findFirst().orElseThrow());
			int types = sprite.blocks("TYPES").size();
			expect(look + ": the new race has 1 to 3 types, not " + types, types >= 1 && types <= 3);
			// Every block in a home class opens RESOURCES, even when it's empty (RaceHomeClass.java:68-71, RaceHomeSheet.java:44).
			GameParser.Node home = parser.root(created.companionFiles().stream().filter(file -> file.toString().replace('\\', '/').contains("init/race/home/")).findFirst().orElseThrow());
			for (String clas : List.of("CITIZEN", "NOBLE", "SLAVE")) {
				GameParser.Node block = home.block(clas);
				for (String key : block.keys()) {
					for (GameParser.Node item : block.blocks(key)) {
						expect(look + ": home " + clas + " " + key + " has its RESOURCES block", item.has("RESOURCES"));
					}
				}
			}
			for (String key : kept) {
				expect(look + ": " + key + " is kept as written", keptIn(key, trees));
			}
			// Tags the form doesn't offer stay too, such as TRAITS, and each file keeps every top-level tag its look has.
			AssetEditing.Sources sources = AssetEditing.sources(schema, index, look);
			expect(look + ": the race file keeps its look's tags: " + sources.data().entries().keySet(), trees.get("").entries().keySet().containsAll(sources.data().entries().keySet()));
			sources.companions().forEach((id, source) -> {
				if (trees.containsKey(id)) {
					expect(look + ": " + id + " keeps its look's tags", trees.get(id).entries().keySet().containsAll(source.entries().keySet()));
				}
			});
			// Each type is its look's, and a generated one is a copy of the first, with only its name files changed. CONDITIONS aren't on the form, so this is what shows they're kept.
			if (sources.companions().get("sprite").get("TYPES") instanceof syx.core.GameTree.Blocks lookTypes
					&& trees.get("sprite").get("TYPES") instanceof syx.core.GameTree.Blocks newTypes) {
				for (int i = 0; i < newTypes.items().size(); i++) {
					syx.core.GameTree.Block from = lookTypes.items().get(i < lookTypes.items().size() ? i : 0);
					expect(look + ": type " + i + " is its look's, apart from its names", withoutNames(newTypes.items().get(i)).equals(withoutNames(from)));
				}
			}
		}
		// A level added on the form has no RESOURCES of its own, and the game still opens the block (RaceHomeSheet.java:44).
		Map<String, String> added = syx.core.AssetForm.startingValues(schema, index, "HUMAN", "NF_EXTRA");
		int levels = (int) added.keySet().stream().filter(key -> key.startsWith("file.home.CITIZEN.MAT_1x1.") && key.endsWith(".FRAMES")).count();
		added.put("file.home.CITIZEN.MAT_1x1." + levels + ".FRAMES", "-");
		try {
			AssetCreator.create(mod.root(), entry.type(), schema, "NF_EXTRA", added, "HUMAN", parser, index);
			Path extraHome = AssetCreator.versionFolder(mod.root(), parser.version()).resolve("assets/init/race/home/NF_EXTRA.txt");
			List<GameParser.Node> mats = parser.root(extraHome).block("CITIZEN").blocks("MAT_1x1");
			expect("an added home level is written with an empty RESOURCES block", mats.size() == levels + 1 && mats.get(levels).has("RESOURCES"));
		} catch (AssetCreator.CreationException e) {
			expect("a race with an added home level is written: " + e.getMessage(), false);
		}
		// A raider message is one text over several lines, with a blank line before the signature. It's one message, not four.
		Path raid = AssetCreator.versionFolder(mod.root(), parser.version()).resolve("assets/text/race/raider/message/NF_HUMAN.txt");
		List<String> ends = Files.exists(raid) ? List.of(parser.root(raid).block("FIRST").texts("ENDS")) : List.of();
		expect("a new race's raider messages keep their lines together: " + ends.size(), !ends.isEmpty() && ends.get(0).contains("\n\n") && ends.stream().noneMatch(String::isBlank));
		Path humanSprite = AssetCreator.versionFolder(mod.root(), parser.version()).resolve("assets/init/race/sprite/NF_HUMAN.txt");
		String written = Files.readString(humanSprite);
		expect("the appearance keeps the vanilla sheet names", written.contains("Human") && !written.contains(".png"));
		Path names = AssetCreator.versionFolder(mod.root(), parser.version()).resolve("assets/text/names/nameset");
		expect("name files the look doesn't use were written, up to the third type", Files.exists(names.resolve("NF_ARGONOSH_T2_FIRST.txt")) && Files.exists(names.resolve("NF_ARGONOSH_T2_SURNAME.txt"))
				&& !Files.exists(names.resolve("NF_ARGONOSH_T3_FIRST.txt")));
		try (Stream<Path> walk = Files.walk(AssetCreator.versionFolder(mod.root(), parser.version()))) {
			expect("no image was copied", walk.noneMatch(path -> path.getFileName().toString().endsWith(".png")));
		}
		misplacedRaceText(entry, mod, parser, game);
	}

	/**
	 * A race text file in the layout the toolkit used to write: RaceInfo's own lines inside INFO, where the game
	 * doesn't read them. Opening it must say so as an error on each, and saving with values must move them to the top.
	 */
	private static void misplacedRaceText(AssetCatalog.Entry entry, ModEntry mod, GameParser parser, Path game) throws Exception {
		AssetSchema schema = entry.schema();
		Path assets = AssetCreator.versionFolder(mod.root(), parser.version()).resolve("assets");
		Path data = assets.resolve("init/race/NF_HUMAN.txt");
		Path text = assets.resolve("text/race/NF_HUMAN.txt");
		if (!Files.exists(data) || !Files.exists(text)) {
			expect("NF_HUMAN was written, for the misplaced text test", false);
			return;
		}
		List<String> moved = List.of("POSSESSIVE", "POSSESSIVES", "DESC_LONG", "CHALLENGE");
		syx.core.GameTree.Block tree = syx.core.GameTree.read(parser.root(text));
		syx.core.GameTree.Block info = tree.get("INFO") instanceof syx.core.GameTree.Block block ? block.copy() : syx.core.GameTree.Block.empty();
		Map<String, String> expected = new LinkedHashMap<>();
		GameParser.Node before = parser.root(text);
		for (String key : moved) {
			if (tree.has(key)) {
				expected.put(key, before.text(key));
				info.put(key, tree.get(key));
				tree.remove(key);
			}
		}
		tree.put("INFO", info);
		Files.writeString(text, syx.core.GameTree.write(tree));
		expect("the old layout hides POSSESSIVE from the game's read of the whole file", !parser.root(text).has("POSSESSIVE"));
		List<AssetChecker.Finding> raw = AssetVanillaCheck.checkText(schema, text, parser);
		expect("the text check reports POSSESSIVE as an error: " + raw, raw.stream().anyMatch(f -> "POSSESSIVE".equals(f.tag()) && f.severity() == AssetChecker.Severity.ERROR));

		GameAssetIndex index = GameAssetIndex.load(game, AssetCreator.versionFolder(mod.root(), parser.version()), parser);
		AssetEditing.Location location = AssetEditing.locate(mod.root(), parser.version(), data, List.of(entry), index, parser);
		expect("NF_HUMAN can be opened: " + location.problem(), location.problem() == null);
		if (location.problem() != null) {
			return;
		}
		AssetEditing.Opened opened = AssetEditing.load(location, parser, index);
		expect("opening it finds the lines inside INFO: " + opened.misplacedText(), opened.misplacedText().containsAll(expected.keySet()));
		expect("and doesn't show them as values, because the game doesn't see them", expected.keySet().stream().noneMatch(opened.values()::containsKey));
		GameAssetIndex editing = index.editing(entry.type(), opened.fileName());
		Map<String, String> form = syx.core.AssetForm.withFormBlanks(schema, opened.values(), true);
		syx.core.AssetForm.Status status = syx.core.AssetForm.status(schema, editing, form, opened.scope(form, opened.kept()), null, null, false, opened.misplacedText(), opened.kept(), opened.keptAsWritten());
		for (String key : List.of("POSSESSIVE", "POSSESSIVES", "DESC_LONG")) {
			expect("the form calls misplaced " + key + " an error, and says why: " + status.findings(), status.findings().stream()
					.anyMatch(f -> key.equals(f.tag()) && f.severity() == AssetChecker.Severity.ERROR && f.message().contains("INFO block")));
		}
		expect("and saving is blocked until they're entered", !status.canSave());
		// Without the form's blanks a tag is absent, which the checker calls MISSING, as an agent's values can be.
		syx.core.AssetForm.Status absent = syx.core.AssetForm.status(schema, editing, opened.values(), opened.scope(opened.values(), opened.kept()), null, null, false, opened.misplacedText(), opened.kept(), opened.keptAsWritten());
		expect("absent, POSSESSIVE is still an error, not a value not filled in yet: " + absent.findings(), absent.findings().stream().anyMatch(f -> "POSSESSIVE".equals(f.tag()) && f.severity() == AssetChecker.Severity.ERROR)
				&& absent.findings().stream().noneMatch(f -> "POSSESSIVE".equals(f.tag()) && f.severity() == AssetChecker.Severity.MISSING));

		Map<String, String> values = new LinkedHashMap<>(form);
		values.putAll(expected);
		AssetCreator.save(opened, form, values, false, parser, editing);
		GameParser.Node after = parser.root(text);
		for (Map.Entry<String, String> line : expected.entrySet()) {
			expect("saving writes " + line.getKey() + " at the top", after.has(line.getKey()) && after.text(line.getKey()).equals(line.getValue()));
			expect("and removes its copy inside INFO", !after.block("INFO").has(line.getKey()));
		}
		expect("NAME stays in INFO, where the game reads it", after.block("INFO").has("NAME"));
		List<AssetChecker.Finding> fixed = AssetVanillaCheck.checkText(schema, text, parser);
		expect("the saved text reads as RaceInfo reads it: " + fixed, fixed.stream().noneMatch(f -> f.severity() == AssetChecker.Severity.ERROR));
		AssetEditing.Opened reopened = AssetEditing.load(location, parser, index);
		expect("reopened, nothing is misplaced", reopened.misplacedText().isEmpty());

		// Vanilla Human's home overwrites a frame's colour with one block, which ColorImp.cols reads as one colour, so it's a value the form shows.
		expect("a home overwrite's colour block is shown, not kept: " + reopened.kept(), reopened.values().entrySet().stream()
				.anyMatch(value -> value.getKey().startsWith("file.home.") && value.getKey().endsWith(".OVERWRITE.0.COLOR") && value.getValue().equals("{R: 10, G: 150, B: 10,}"))
				&& reopened.kept().stream().noneMatch(key -> key.contains(".OVERWRITE.")));
		// A kept value is given as the game reads it, so a person and an agent can see what stays. Vanilla Cretonian's colours are generated ranges.
		Map<String, String> fromLook = syx.core.AssetForm.startingKeptAsWritten(schema, index, "CRETONIAN");
		expect("a new race's kept values are given: " + fromLook, fromLook.entrySet().stream().anyMatch(kept -> kept.getKey().startsWith("file.sprite.COLORS.") && kept.getValue().contains("GENERATE")));
		expect("every kept key of the look has its value", fromLook.keySet().equals(syx.core.AssetForm.startingKept(schema, index, "CRETONIAN")));
		Map<String, String> fromOpened = reopened.keptAsWritten();
		expect("and an opened race's, from its own files: " + fromOpened, fromOpened.keySet().equals(reopened.kept()));
		syx.core.AssetForm.Status keptStatus = syx.core.AssetForm.status(schema, editing, values, opened.scope(values, fromLook.keySet()), null, null, false, List.of(), fromLook.keySet(), fromLook);
		expect("the kept callout gives the values: " + keptStatus.callouts(), keptStatus.callouts().stream().anyMatch(callout -> callout.contains("(now {") && callout.contains("GENERATE")));

		// The nine name files share a folder, and each opens the race that names it.
		try (Stream<Path> nameFiles = Files.list(assets.resolve("text/names/nameset"))) {
			List<Path> human = nameFiles.filter(file -> file.getFileName().toString().startsWith("NF_HUMAN_")).toList();
			expect("NF_HUMAN has its name files: " + human, human.size() >= 8);
			for (Path file : human) {
				AssetEditing.Location owner = AssetEditing.locate(mod.root(), parser.version(), file, List.of(entry), index, parser);
				expect(file.getFileName() + " opens NF_HUMAN: " + owner.problem(), owner.problem() == null && "NF_HUMAN".equals(owner.name()));
			}
		}
	}

	private static syx.core.GameTree.Block withoutNames(syx.core.GameTree.Block type) {
		syx.core.GameTree.Block copy = type.copy();
		copy.remove("NAMESET_FILE_FIRST");
		copy.remove("NAMESET_FILE_SURNAME");
		return copy;
	}

	/** Whether a value key's path is in the written files: the data file, or file.<id>. in that companion. */
	private static boolean keptIn(String key, Map<String, syx.core.GameTree.Block> trees) {
		String[] parts = key.split("\\.");
		if (!parts[0].equals("file")) {
			return pathIn(trees.get(""), parts, 0) || pathIn(trees.get("text"), parts, 0)
					|| trees.get("text").get("INFO") instanceof syx.core.GameTree.Block info && pathIn(info, parts, 0);
		}
		return pathIn(trees.get(parts[1]), parts, 2);
	}

	private static boolean pathIn(syx.core.GameTree.Value start, String[] parts, int from) {
		syx.core.GameTree.Value at = start;
		int start2 = from;
		for (int i = start2; i < parts.length && at != null; i++) {
			if (at instanceof syx.core.GameTree.Blocks list && parts[i].matches("\\d+")) {
				int item = Integer.parseInt(parts[i]);
				at = item < list.items().size() ? list.items().get(item) : null;
			} else if (at instanceof syx.core.GameTree.Block block) {
				at = block.get(parts[i]);
			} else {
				at = null;
			}
		}
		return at != null;
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
