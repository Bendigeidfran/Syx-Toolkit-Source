package syx.test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import syx.core.GameInstall;
import syx.core.GameParser;
import syx.core.HandbookStore;
import syx.core.MapSpriteSim;
import syx.core.MapSpriteSim.Person;
import syx.core.ModEntry;
import syx.core.Operations;
import syx.core.Operations.Outcome;
import syx.core.PortraitSim;
import syx.core.PortraitSim.State;
import syx.core.ToolkitSettings;

/**
 * The Map Sprite Sim. Drawing is checked against small sheets made here, with single pixels placed where the game's
 * composer reads them, so no game art enters the repo. Vanilla's own files are read from the player's install at run
 * time, and must all draw with no warning.
 */
public final class MapSpriteSimTest {

	private static final String SPRITE_FOLDER = "data/assets/init/race/sprite/";
	private static final int WHITE = 0xFFFFFFFF;
	private static final int FLAT = 0x8080FFFF;

	private static final String FIXTURE = """
			SPRITE_EXTRA_FILE: MapExtra,
			SLEEP_FILE: MapSleep,
			** A comment outside the map tags stays.
			COLOR_BLOOD: 200_10_10,
			COLOR_CLOTHES: [
				{R: 100, G: 50, B: 20,},
			],
			COLORS: {
				SKIN: {
					VALUES: [
						254_254_254,
					],
				},
				LEG: {
					VALUES: [
						100_100_100,
					],
				},
				LEG2: {
					VALUES: [
						50_150_50,
					],
				},
			},
			CROWN: {
				OFFX: 0,
				OFFY: 0,
				FILE: Keep,
			},
			RAIDER: {
				OFFX: 0,
				OFFY: 0,
				FILE: Keep,
			},
			MERC: {
				OFFX: 0,
				OFFY: 0,
				FILE: Keep,
			},
			CHILD: {
				SPRITE_FILE: MapTest,
				SPRITE_SKELLETON_FILE: MapSkelA,
				COLOR_SKIN: SKIN,
				COLOR_LEG: LEG,
				NAMESET_FILE_FIRST: Keep,
			},
			TYPES: [
				{
					OCCURRENCE: 1,
					SPRITE_FILE: MapTest,
					SPRITE_SKELLETON_FILE: MapSkelA,
					COLOR_SKIN: SKIN,
					COLOR_LEG: LEG,
					** a comment inside a block stays
					ADDONS: [
						{
							BELOW_HEAD: false,
							ADDON_INDEX: 1,
							COLOR: SKIN,
							CONDITIONS: {
								EQUAL: {
									CLASS_NOBLE: 1,
								},
							},
						},
					],
					FACE: [
						{
							FRAMES: [
								Keep: 0,
							],
						},
					],
				},
			],
			""";

	private static int passed;
	private static int failed;
	private static Path game;
	private static Path temp;
	private static Path mods;
	private static ModEntry mod;
	private static Path version;
	private static Operations ops;

	private MapSpriteSimTest() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			System.out.println("SKIPPED: no game folder given.");
			System.out.println("passed=0 failed=0");
			return;
		}
		game = Paths.get(args[0]);
		temp = Files.createTempDirectory("syx-mapsprite");
		mods = temp.resolve("mods");
		Files.createDirectories(mods);
		ops = new Operations(() -> game, mods, new ToolkitSettings(temp.resolve("settings.properties")), new HandbookStore(temp.resolve("handbook"), etag -> new HandbookStore.Response(404, null, new byte[0])), false);
		try {
			setup();
			directions();
			colours();
			animations();
			poses();
			problems();
			saving();
			vanillaFiles();
			agents();
		} finally {
			deleteFolder(temp);
		}
		System.out.println("passed=" + passed + " failed=" + failed);
		System.exit(failed == 0 ? 0 : 1);
	}

	private static void setup() throws Exception {
		Operations.Result created = ops.call("create_mod", in("name", "Map Test"));
		expect("the test mod is created: " + created.payload(), created.outcome() == Outcome.OK);
		Path root = mods.resolve("Map Test");
		int major;
		try (GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
			major = parser.version().major();
		}
		version = root.resolve("V" + major);
		mod = new ModEntry("Map Test", root, "Map Test", List.of("V" + major), false, null);
		Files.createDirectories(version.resolve("assets/init/race/sprite"));
		for (String folder : List.of("", "extra", "sleep", "skelleton")) {
			Files.createDirectories(version.resolve("assets/sprite/race/" + folder));
		}
		writeRace("FIXTURE", "FIXTURE");
		writeSprite("FIXTURE", FIXTURE);
		writeSheets();
	}

	private static void writeRace(String race, String sprite) throws IOException {
		Files.writeString(version.resolve("assets/init/race/" + race + ".txt"), "SPRITE_FILE: " + sprite + ",\nOTHER: 1,\n");
	}

	private static void writeSprite(String name, String text) throws IOException {
		Files.writeString(spritePath(name), text);
	}

	private static Path spritePath(String name) {
		return version.resolve("assets/init/race/sprite/" + name + ".txt");
	}

	/** A pixel and its normal, in a sheet whose right half is its normal map. */
	private static void put(BufferedImage image, int x, int y, int rgba, int normal) {
		image.setRGB(x, y, argb(rgba));
		image.setRGB(x + image.getWidth() / 2, y, argb(normal));
	}

	private static int argb(int rgba) {
		return (rgba & 0xFF) << 24 | rgba >>> 8;
	}

	/**
	 * The race sheet: one pixel in the first cell of the still torso's row, one in its diagonal cell, and one in the
	 * tunic row. The child's skeleton sheet has a pixel in every cell a skeleton can use, and the adult's is empty.
	 */
	private static void writeSheets() throws IOException {
		BufferedImage sheet = new BufferedImage(448, 546, BufferedImage.TYPE_INT_ARGB);
		// TORSO_STILL is pair 6: cells 12 and 13, at 6 + 6 x 30 down (ComposerSources.Singles, m = 6).
		put(sheet, 6, 186, WHITE, FLAT);
		put(sheet, 36 + 1, 186, WHITE, FLAT);
		// TUNIC is pair 5, its first cell 30 pixels up from the torso's, with the pixel at (5, 5).
		put(sheet, 6 + 5, 156 + 5, WHITE, FLAT);
		// Addon 1's standing cell (RAddon.java:41-48), at (2, 2).
		put(sheet, 66 + 6 + 2, 194 + 44 + 6 + 2, WHITE, FLAT);
		write(sheet, "MapTest");
		write(new BufferedImage(448, 546, BufferedImage.TYPE_INT_ARGB), "MapBlank");
		write(new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB), "MapSmall");
		BufferedImage skeleton = new BufferedImage(316, 120, BufferedImage.TYPE_INT_ARGB);
		for (int cell = 0; cell < 12; cell += 2) {
			put(skeleton, 6 + cell % 4 * 38, 6 + cell / 4 * 38, WHITE, FLAT);
		}
		ImageIO.write(skeleton, "png", version.resolve("assets/sprite/race/skelleton/MapSkelB.png").toFile());
		ImageIO.write(new BufferedImage(316, 120, BufferedImage.TYPE_INT_ARGB), "png", version.resolve("assets/sprite/race/skelleton/MapSkelA.png").toFile());
		ImageIO.write(new BufferedImage(428, 310, BufferedImage.TYPE_INT_ARGB), "png", version.resolve("assets/sprite/race/extra/MapExtra.png").toFile());
		ImageIO.write(new BufferedImage(164, 44, BufferedImage.TYPE_INT_ARGB), "png", version.resolve("assets/sprite/race/sleep/MapSleep.png").toFile());
	}

	private static void write(BufferedImage image, String name) throws IOException {
		ImageIO.write(image, "png", version.resolve("assets/sprite/race/" + name + ".png").toFile());
	}

	private static Person person(String animation, int direction) {
		return new Person(0, animation, direction, 1, 0.4, false, false, false, false, false, false, false, -1, 0, 0, 0, 0, 0, 1, false, PortraitSim.Pins.NONE);
	}

	private static Person with(Person p, boolean noble, boolean slave, boolean naked, int clothes) {
		return new Person(p.type(), p.animation(), p.direction(), p.seed(), p.age(), noble, slave, p.parent(), p.retired(), p.sick(), p.enemy(), naked, clothes, p.plate(), p.leather(), p.grime(), p.injury(), p.decay(),
				p.scale(), p.lit(), p.pins());
	}

	private static int pixel(MapSpriteSim.Preview preview, int frame, int x, int y) {
		return preview.frames().get(frame)[y * preview.width() + x];
	}

	private static boolean opaque(int argb) {
		return argb >>> 24 != 0;
	}

	/** Each cell is turned a quarter clockwise per step, as ComposerUtil.pasteRotated turns it: (x, y) to (23 - y, x). */
	private static void directions() throws Exception {
		try (MapSpriteSim.Session session = MapSpriteSim.open(game, mod)) {
			int[][] expected = { { 0, 0 }, { 1, 0 }, { 23, 0 }, { 23, 1 }, { 23, 23 }, { 22, 23 }, { 0, 23 }, { 0, 22 } };
			for (int dir = 0; dir < 8; dir++) {
				MapSpriteSim.Preview preview = session.preview("FIXTURE", null, person("STAND", dir));
				expect(MapSpriteSim.DIRECTIONS.get(dir) + " draws: " + preview.problem(), preview.state() == State.OK);
				int x = 8 + expected[dir][0];
				int y = 8 + expected[dir][1];
				int value = pixel(preview, 0, x, y);
				expect(MapSpriteSim.DIRECTIONS.get(dir) + " puts the torso pixel at " + x + "," + y + ": " + Integer.toHexString(value), opaque(value) && (value >> 16 & 0xFF) > 250);
				int count = 0;
				for (int p : preview.frames().get(0)) {
					count += opaque(p) ? 1 : 0;
				}
				// The torso pixel, and the tunic's, which is only in the straight cell; the addon isn't for this person.
				int wanted = dir % 2 == 0 ? 2 : 1;
				expect(MapSpriteSim.DIRECTIONS.get(dir) + " draws " + wanted + " pixels: " + count, count == wanted);
			}
			MapSpriteSim.Preview compass = session.preview("FIXTURE", null, person("STAND", 0));
			expect("the compass has all 8 directions", compass.compass().size() == 8 && compass.compassSize() == 40);
			expect("and its east is the east picture", opaque(compass.compass().get(2)[8 * 40 + 31]));
		}
	}

	private static int[] rgb(int argb) {
		return new int[] { argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF };
	}

	/** Clothes, skin and addons as StatsAppearance and RAddon pick them. Unlit, a colour is drawn doubled, as the shader does. */
	private static void colours() throws Exception {
		try (MapSpriteSim.Session session = MapSpriteSim.open(game, mod)) {
			Person south = person("STAND", 0);
			// Tunic pixel at (5, 5), unturned for north.
			int[] full = rgb(pixel(session.preview("FIXTURE", null, south), 0, 13, 13));
			expect("full clothes are COLOR_CLOTHES' colour: " + java.util.Arrays.toString(full), Math.abs(full[0] - 100) <= 2 && Math.abs(full[1] - 50) <= 2 && Math.abs(full[2] - 20) <= 2);
			int[] none = rgb(pixel(session.preview("FIXTURE", null, with(south, false, false, false, 0)), 0, 13, 13));
			expect("no clothes are less saturated: " + java.util.Arrays.toString(none), !java.util.Arrays.equals(full, none) && none[0] - none[2] < full[0] - full[2]);
			int[] slaveColour = rgb(pixel(session.preview("FIXTURE", null, with(south, false, true, false, -1)), 0, 13, 13));
			expect("a slave wears the slaves' brown: " + java.util.Arrays.toString(slaveColour), !java.util.Arrays.equals(full, slaveColour));
			MapSpriteSim.Preview bare = session.preview("FIXTURE", null, with(south, false, false, true, -1));
			expect("naked hides the tunic", !opaque(pixel(bare, 0, 13, 13)) && opaque(pixel(bare, 0, 8, 8)));
			MapSpriteSim.Preview plain = session.preview("FIXTURE", null, south);
			expect("the addon isn't for a commoner: " + plain.addons(), plain.addons().size() == 1 && !plain.addons().get(0).shown() && !opaque(pixel(plain, 0, 10, 10)));
			MapSpriteSim.Preview noble = session.preview("FIXTURE", null, with(south, true, false, false, -1));
			expect("a noble gets it, in SKIN: " + noble.addons(), noble.addons().get(0).shown() && "SKIN".equals(noble.addons().get(0).colour()) && opaque(pixel(noble, 0, 10, 10)));
			expect("the clothes levels come from the game's _CLOTHES file", plain.clothesMax() >= 1 && plain.clothesMax() <= 15);
		}
	}

	private static int total(MapSpriteSim.Preview preview) {
		return preview.timeline().stream().mapToInt(MapSpriteSim.Step::millis).sum();
	}

	/** A loop lasts until every part is back where it started: torso and feet, or torso and water. */
	private static void animations() throws Exception {
		try (MapSpriteSim.Session session = MapSpriteSim.open(game, mod)) {
			MapSpriteSim.Preview walk = session.preview("FIXTURE", null, person("MOVE", 4));
			// Torso 8 steps at 20 a second (0.4 s) and feet 8 at 14 (4/7 s) meet again after 4 s.
			expect("a walk loops in 4 seconds: " + total(walk), total(walk) == 4000);
			MapSpriteSim.Preview hit = session.preview("FIXTURE", null, person("TOOL_HIT", 4));
			expect("a tool hit is 15 steps at 32 a second: " + total(hit), total(hit) == 468);
			MapSpriteSim.Preview swim = session.preview("FIXTURE", null, person("SWIM", 4));
			expect("swimming is 12 torsos at 5 a second: " + total(swim), total(swim) == 2400);
			MapSpriteSim.Preview stand = session.preview("FIXTURE", null, person("STAND", 4));
			expect("standing is one still frame", stand.timeline().size() == 1 && stand.frames().size() == 1);
			for (MapSpriteSim.Animation animation : MapSpriteSim.ANIMATIONS) {
				MapSpriteSim.Preview each = session.preview("FIXTURE", null, person(animation.id(), 3));
				expect(animation.id() + " draws: " + each.problem(), each.state() == State.OK && !each.timeline().isEmpty());
				boolean framesUsed = each.timeline().stream().allMatch(step -> step.frame() >= 0 && step.frame() < each.frames().size());
				expect(animation.id() + "'s timeline uses its frames", framesUsed);
			}
			try {
				session.preview("FIXTURE", null, person("FLY", 0));
				expect("an unknown animation is refused", false);
			} catch (PortraitSim.Refused e) {
				expect("an unknown animation is refused: " + e.getMessage(), e.getMessage().contains("FLY"));
			}
		}
	}

	/** An adult's skeleton is drawn from CHILD's sheet, as the game's call and RAppearence.skelleton have it. */
	private static void poses() throws Exception {
		writeSprite("FIXTURE", FIXTURE.replace("SPRITE_SKELLETON_FILE: MapSkelA,\n\tCOLOR_SKIN", "SPRITE_SKELLETON_FILE: MapSkelB,\n\tCOLOR_SKIN"));
		try (MapSpriteSim.Session session = MapSpriteSim.open(game, mod)) {
			MapSpriteSim.Preview adult = session.preview("FIXTURE", null, person("SKELETON", 0));
			boolean drawn = java.util.Arrays.stream(adult.frames().get(0)).anyMatch(MapSpriteSimTest::opaque);
			expect("an adult's skeleton uses CHILD's sheet, which has the pixels", drawn);
			Person child = new Person(1, "SKELETON", 0, 1, 0.4, false, false, false, false, false, false, false, -1, 0, 0, 0, 0, 0, 1, false, PortraitSim.Pins.NONE);
			MapSpriteSim.Preview young = session.preview("FIXTURE", null, child);
			boolean empty = java.util.Arrays.stream(young.frames().get(0)).noneMatch(MapSpriteSimTest::opaque);
			expect("a child's uses the first type's, which is empty", empty);
			expect("and a note says so", adult.notes().stream().anyMatch(note -> note.contains("CHILD's SPRITE_SKELLETON_FILE")));
		} finally {
			writeSprite("FIXTURE", FIXTURE);
		}
	}

	private static String red(MapSpriteSim.Session session, String text) throws Exception {
		MapSpriteSim.Preview preview = session.preview("FIXTURE", text, person("STAND", 4));
		return preview.state() == State.RED ? preview.problem() : null;
	}

	/** What stops the game loading is RED, with the reason. Text is drawn unsaved. */
	private static void problems() throws Exception {
		try (MapSpriteSim.Session session = MapSpriteSim.open(game, mod)) {
			String text = session.open("FIXTURE").text();
			expect("the section holds the map tags and not the portrait's", text.contains("ADDONS") && text.contains("COLOR_CLOTHES") && !text.contains("FACE") && !text.contains("CROWN") && !text.contains("OCCURRENCE"));
			String index = red(session, text.replace("ADDON_INDEX: 1,", "ADDON_INDEX: 8,"));
			expect("ADDON_INDEX 8 stops the game: " + index, index != null && index.contains("RAddon.java:33-35"));
			String colour = red(session, text.replaceFirst("COLOR_SKIN: SKIN", "COLOR_SKIN: NOPE"));
			expect("an unknown COLOR_SKIN stops the game: " + colour, colour != null && colour.contains("NOPE"));
			String missing = red(session, text.replaceFirst("SPRITE_FILE: MapTest", "SPRITE_FILE: NoSuchSheet"));
			expect("a missing sheet stops the game: " + missing, missing != null && missing.contains("NoSuchSheet"));
			String small = red(session, text.replaceFirst("SPRITE_FILE: MapTest", "SPRITE_FILE: MapSmall"));
			expect("a sheet of the wrong size stops the game: " + small, small != null && small.contains("448"));
			String face = red(session, text.replace("COLOR_LEG: LEG,\n\t\tADDONS", "COLOR_LEG: LEG,\n\t\tFACE: [],\n\t\tADDONS"));
			expect("a portrait tag can't be edited here: " + face, face != null && face.contains("isn't a map sprite tag"));
			String noClothes = red(session, text.replace("{R: 100, G: 50, B: 20,},", ""));
			expect("COLOR_CLOTHES needs a colour: " + noClothes, noClothes != null);
			String extra = red(session, text.replace("SPRITE_EXTRA_FILE: MapExtra", "SPRITE_EXTRA_FILE: Nope"));
			expect("a missing extra sheet stops the game: " + extra, extra != null && extra.contains("race/extra/Nope"));
			expect("the saved text draws", red(session, null) == null);
		}
	}

	/** Only the map tags that changed are written, and the portrait's and everything else's bytes stay. */
	private static void saving() throws Exception {
		writeSprite("FIXTURE", FIXTURE);
		try (MapSpriteSim.Session session = MapSpriteSim.open(game, mod)) {
			PortraitSim.Opened opened = session.open("FIXTURE");
			expect("saving the same text changes nothing", !session.save("FIXTURE", opened.revision(), opened.text()));
			String edited = opened.text().replace("COLOR_LEG: LEG,\n\t\tADDONS", "COLOR_LEG: LEG2,\n\t\tADDONS").replace("CHILD: {\n", "CHILD: {\n\tADDONS: [\n\t\t{\n\t\t\tBELOW_HEAD: true,\n\t\t\tADDON_INDEX: 2,\n\t\t},\n\t],\n");
			expect("the edit is a change", !edited.equals(opened.text()));
			expect("the edit saves", session.save("FIXTURE", opened.revision(), edited));
			String after = Files.readString(spritePath("FIXTURE"));
			expect("the type's COLOR_LEG changed", after.contains("COLOR_LEG: LEG2,") && after.contains("COLOR_LEG: LEG,"));
			expect("an added tag is inside CHILD: " + after, after.indexOf("ADDON_INDEX: 2") > after.indexOf("CHILD:") && after.indexOf("ADDON_INDEX: 2") < after.indexOf("TYPES:"));
			expect("the portrait's tags and the comments stay", after.contains("Keep: 0,") && after.contains("** A comment outside") && after.contains("** a comment inside a block stays") && after.contains("OCCURRENCE: 1,"));
			try (GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
				expect("the game reads the saved file", parser.parse(spritePath("FIXTURE")).ok());
			}
			try {
				session.save("FIXTURE", opened.revision(), edited);
				expect("an old revision is refused", false);
			} catch (PortraitSim.Refused e) {
				expect("an old revision is refused: " + e.getMessage(), e.getMessage().contains("changed since"));
			}
			PortraitSim.Opened again = session.open("FIXTURE");
			try {
				session.save("FIXTURE", again.revision(), again.text().replace("ADDON_INDEX: 1,", "ADDON_INDEX: 9,"));
				expect("text the game can't load is refused", false);
			} catch (PortraitSim.Refused e) {
				expect("text the game can't load is refused: " + e.getMessage(), e.getMessage().contains("couldn't load"));
			}
			expect("and nothing was written", Files.readString(spritePath("FIXTURE")).equals(after));
		}
		writeSprite("FIXTURE", FIXTURE);
	}

	/** Vanilla is the test of a false alarm: every race, type and animation must draw, with no warning, and a save must change nothing. */
	private static void vanillaFiles() throws Exception {
		List<String> names = new ArrayList<>();
		try (ZipFile zip = new ZipFile(game.resolve("base/data.zip").toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				String name = entries.nextElement().getName();
				if (name.startsWith(SPRITE_FOLDER) && name.endsWith(".txt")) {
					names.add(name);
				}
			}
			expect("vanilla's sprite files are found: " + names.size(), names.size() >= 8);
			for (String name : names) {
				String stem = "V_" + name.substring(SPRITE_FOLDER.length(), name.length() - 4);
				try (InputStream in = zip.getInputStream(zip.getEntry(name))) {
					Files.write(spritePath(stem), in.readAllBytes());
				}
				writeRace(stem, stem);
			}
		}
		Set<String> allWarnings = new LinkedHashSet<>();
		try (MapSpriteSim.Session session = MapSpriteSim.open(game, mod)) {
			for (String name : names) {
				String stem = "V_" + name.substring(SPRITE_FOLDER.length(), name.length() - 4);
				byte[] before = Files.readAllBytes(spritePath(stem));
				PortraitSim.Opened opened = session.open(stem);
				expect(stem + " opens: " + opened.race().note(), opened.race().editable());
				MapSpriteSim.Preview first = session.preview(stem, null, Person.standard());
				expect(stem + " draws: " + first.problem(), first.state() != State.RED);
				for (int type = 0; type < first.types().size(); type++) {
					for (MapSpriteSim.Animation animation : MapSpriteSim.ANIMATIONS) {
						Person each = new Person(type, animation.id(), type + 1, type + 3, 0.5, type == 0, false, false, false, false, false, false, -1, 2, 1, 0.5, 0.5, 0.3, 1, true, PortraitSim.Pins.NONE);
						MapSpriteSim.Preview preview = session.preview(stem, null, each);
						expect(stem + " type " + type + " " + animation.id() + " draws: " + preview.problem(), preview.state() != State.RED);
						allWarnings.addAll(preview.warnings());
						boolean visible = preview.frames().stream().anyMatch(frame -> java.util.Arrays.stream(frame).anyMatch(MapSpriteSimTest::opaque));
						expect(stem + " type " + type + " " + animation.id() + " shows something", visible);
					}
				}
				expect(stem + " saves unchanged", !session.save(stem, opened.revision(), opened.text()));
				expect(stem + " is byte-identical afterwards", java.util.Arrays.equals(before, Files.readAllBytes(spritePath(stem))));
				String edited = withBlood(opened.text());
				expect(stem + " saves an edit", session.save(stem, opened.revision(), edited));
				String after = new String(Files.readAllBytes(spritePath(stem)), StandardCharsets.UTF_8);
				String was = new String(before, StandardCharsets.UTF_8);
				expect(stem + " only changed that value", after.equals(withBlood(was)));
			}
		}
		System.out.println("Warnings on vanilla, each to be justified: " + allWarnings);
		expect("vanilla draws without a warning: " + allWarnings, allWarnings.isEmpty());
	}

	/** The text with COLOR_BLOOD's value replaced, whichever form it's written in. */
	private static String withBlood(String text) {
		int at = text.indexOf("COLOR_BLOOD:") + "COLOR_BLOOD:".length();
		while (text.charAt(at) == ' ') {
			at++;
		}
		int end = text.charAt(at) == '{' ? text.indexOf('}', at) + 1 : text.indexOf(',', at);
		return text.substring(0, at) + "1_2_3" + text.substring(end);
	}

	/** An agent gets what the screen gets: the same pictures, the same refusals. */
	private static void agents() throws Exception {
		writeSprite("FIXTURE", FIXTURE);
		Operations.Result opened = ops.call("open_map_sprite", in("mod", "Map Test", "race", "FIXTURE"));
		expect("open_map_sprite: " + opened.outcome(), opened.outcome() == Outcome.OK);
		String text = (String) opened.payload().get("text");
		expect("it lists the animations and directions", opened.payload().get("animations") instanceof List<?> list && list.size() == MapSpriteSim.ANIMATIONS.size() && opened.payload().get("directions") instanceof List<?>);
		Map<String, Object> personMap = in("animation", "move", "direction", "SE", "seed", 5, "noble", true, "scale", 2);
		Operations.Result preview = ops.call("preview_map_sprite", in("mod", "Map Test", "race", "FIXTURE", "person", personMap));
		expect("preview_map_sprite: " + preview.outcome() + " " + preview.payload().get("problem"), preview.outcome() == Outcome.OK && "ok".equals(preview.payload().get("state")));
		try (MapSpriteSim.Session session = MapSpriteSim.open(game, mod)) {
			Person same = new Person(0, "MOVE", 3, 5, 0.4, true, false, false, false, false, false, false, -1, 0, 0, 0, 0, 0, 2, true, PortraitSim.Pins.NONE);
			MapSpriteSim.Preview direct = session.preview("FIXTURE", null, same);
			expect("an agent's strip is the screen's frames", Base64.getEncoder().encodeToString(direct.stripPng()).equals(preview.payload().get("strip_png_base64")));
			expect("and its compass", Base64.getEncoder().encodeToString(direct.compassPng()).equals(preview.payload().get("compass_png_base64")));
			expect("and its timeline", preview.payload().get("timeline") instanceof List<?> steps && steps.size() == direct.timeline().size());
		}
		Operations.Result badAnimation = ops.call("preview_map_sprite", in("mod", "Map Test", "race", "FIXTURE", "person", in("animation", "fly")));
		expect("an unknown animation is refused: " + badAnimation.payload(), badAnimation.outcome() == Outcome.REFUSED);
		Operations.Result badDirection = ops.call("preview_map_sprite", in("mod", "Map Test", "race", "FIXTURE", "person", in("direction", "UP")));
		expect("an unknown direction is refused: " + badDirection.payload(), badDirection.outcome() == Outcome.REFUSED);
		Operations.Result broken = ops.call("preview_map_sprite", in("mod", "Map Test", "race", "FIXTURE", "text", text.replace("COLOR_SKIN: SKIN", "COLOR_SKIN: NOPE")));
		expect("unsaved text is drawn, and a problem reported: " + broken.payload().get("problem"), "red".equals(broken.payload().get("state")) && String.valueOf(broken.payload().get("problem")).contains("NOPE"));
		Operations.Result recoloured = ops.call("edit_portrait_text", in("text", text, "colour", "LEG", "entry", "0", "rgb", "9_9_9"));
		expect("edit_portrait_text changes a colour in map text: " + recoloured.payload(), recoloured.outcome() == Outcome.OK && String.valueOf(recoloured.payload().get("text")).contains("9_9_9"));
		Operations.Result saved = ops.call("save_map_sprite", in("mod", "Map Test", "race", "FIXTURE", "revision", opened.payload().get("revision"), "text", text.replace("200_10_10", "7_7_7")));
		expect("save_map_sprite: " + saved.payload(), saved.outcome() == Outcome.OK && Boolean.TRUE.equals(saved.payload().get("changed")));
		expect("and it reached the file", Files.readString(spritePath("FIXTURE")).contains("7_7_7"));
		Operations readOnly = new Operations(() -> game, mods, new ToolkitSettings(temp.resolve("settings2.properties")), new HandbookStore(temp.resolve("handbook2"), etag -> new HandbookStore.Response(404, null, new byte[0])), true);
		Operations.Result refusedSave = readOnly.call("save_map_sprite", in("mod", "Map Test", "race", "FIXTURE", "revision", "x", "text", text));
		expect("read-only refuses a save", refusedSave.outcome() == Outcome.REFUSED);
	}

	private static Map<String, Object> in(Object... pairs) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], pairs[i + 1]);
		}
		return map;
	}

	private static void expect(String name, boolean ok) {
		if (ok) {
			passed++;
		} else {
			failed++;
			System.out.println("FAILED: " + name);
		}
	}

	private static void deleteFolder(Path folder) throws IOException {
		try (Stream<Path> files = Files.walk(folder)) {
			for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(file);
			}
		}
	}
}
