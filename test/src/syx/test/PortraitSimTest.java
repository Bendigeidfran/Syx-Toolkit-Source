package syx.test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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
import syx.core.ModEntry;
import syx.core.Operations;
import syx.core.Operations.Outcome;
import syx.core.PngCodec;
import syx.core.PortraitSim;
import syx.core.PortraitSim.Person;
import syx.core.PortraitSim.State;
import syx.core.ToolkitSettings;

/**
 * The Race Portrait Sim. Drawing is checked against small sheets made here and sums done by hand, so no game art
 * enters the repo. Vanilla's own files are read from the player's install at run time.
 */
public final class PortraitSimTest {

	private static final String FACE_FOLDER = "data/assets/sprite/race/face/";
	private static final String SPRITE_FOLDER = "data/assets/init/race/sprite/";

	private static final String FIXTURE = """
			SPRITE_EXTRA_FILE: Keep,
			** A comment outside the portrait tags stays.
			COLOR_BLOOD: 200_10_10,
			COLORS: {
				SKIN: {
					VALUES: [
						254_254_254,
					],
				},
			},
			CROWN: {
				OFFX: 0,
				OFFY: 0,
				FILE: TestCrown,
			},
			RAIDER: {
				OFFX: 0,
				OFFY: 0,
				FILE: TestCrown,
			},
			MERC: {
				OFFX: 0,
				OFFY: 0,
				FILE: TestCrown,
			},
			CHILD: {
				SPRITE_FILE: Keep,
				FACE: [
					** a comment inside a value stays
					{
						FRAMES: [
							Test: 0,
						],
						COLOR: SKIN,
					},
				],
			},
			TYPES: [
				{
					SPRITE_FILE: Keep,
					FACE: [
						{
							FRAMES: [
								Test: 0,
							],
							COLOR: SKIN,
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

	private PortraitSimTest() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			System.out.println("SKIPPED: no game folder given.");
			System.out.println("passed=0 failed=0");
			return;
		}
		game = Paths.get(args[0]);
		temp = Files.createTempDirectory("syx-portrait");
		mods = temp.resolve("mods");
		Files.createDirectories(mods);
		ops = new Operations(() -> game, mods, new ToolkitSettings(temp.resolve("settings.properties")), new HandbookStore(temp.resolve("handbook"), etag -> new HandbookStore.Response(404, null, new byte[0])), false);
		try {
			setup();
			pngMatchesImageIo();
			drawing();
			editingAndSaving();
			problems();
			copying();
			controls();
			vanillaFiles();
			agents();
		} finally {
			deleteFolder(temp);
		}
		System.out.println("passed=" + passed + " failed=" + failed);
		System.exit(failed == 0 ? 0 : 1);
	}

	private static void setup() throws Exception {
		Operations.Result created = ops.call("create_mod", in("name", "Portrait Test"));
		expect("the test mod is created: " + created.payload(), created.outcome() == Outcome.OK);
		Path root = mods.resolve("Portrait Test");
		int major;
		try (GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
			major = parser.version().major();
		}
		version = root.resolve("V" + major);
		mod = new ModEntry("Portrait Test", root, "Portrait Test", List.of("V" + major), false, null);
		Files.createDirectories(version.resolve("assets/init/race/sprite"));
		Files.createDirectories(version.resolve("assets/sprite/race/face/addon"));
		writeRace("FIXTURE", "FIXTURE");
		writeSprite("FIXTURE", FIXTURE);
		writeSheets();
	}

	private static void writeRace(String race, String sprite) throws IOException {
		Files.writeString(version.resolve("assets/init/race/" + race + ".txt"), "SPRITE_FILE: " + sprite + ",\nOTHER: 1,\n");
	}

	private static void writeSprite(String name, String text) throws IOException {
		Files.writeString(version.resolve("assets/init/race/sprite/" + name + ".txt"), text);
	}

	private static Path spritePath(String name) {
		return version.resolve("assets/init/race/sprite/" + name + ".txt");
	}

	/** Frame 0 is opaque, frame 1 faces away from the light, frame 2 is half transparent, frame 3 is empty. */
	private static void writeSheets() throws IOException {
		BufferedImage face = new BufferedImage(416, 60, BufferedImage.TYPE_INT_ARGB);
		fill(face, 6, 16, 40, 8, 0xFFFFFFFF, 0xFF8080FF);
		fill(face, 58, 16, 40, 8, 0xFFFFFFFF, 0xFFFFFF00);
		fill(face, 110, 16, 40, 8, 0x80FFFFFF, 0xFF8080FF);
		ImageIO.write(face, "png", version.resolve("assets/sprite/race/face/Test.png").toFile());
		BufferedImage crown = new BufferedImage(104, 36, BufferedImage.TYPE_INT_ARGB);
		for (int x = 0; x < 40; x++) {
			for (int y = 0; y < 24; y++) {
				crown.setRGB(6 + x, 6 + y, 0xFFFFFFFF);
				crown.setRGB(58 + x, 6 + y, 0xFF8080FF);
			}
		}
		ImageIO.write(crown, "png", version.resolve("assets/sprite/race/face/addon/TestCrown.png").toFile());
	}

	private static void fill(BufferedImage image, int x, int y, int width, int height, int diffuse, int normal) {
		for (int dx = 0; dx < width; dx++) {
			for (int dy = 0; dy < height; dy++) {
				image.setRGB(x + dx, y + dy, diffuse);
				image.setRGB(x + dx + 208, y + dy, normal);
			}
		}
	}

	/** The toolkit's decoder has to give the same samples as a mature one, on every sheet the game ships. */
	private static void pngMatchesImageIo() throws Exception {
		int checked = 0;
		int mismatched = 0;
		try (ZipFile zip = new ZipFile(game.resolve("base/data.zip").toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (!entry.getName().startsWith(FACE_FOLDER) || !entry.getName().endsWith(".png")) {
					continue;
				}
				byte[] bytes;
				try (InputStream in = zip.getInputStream(entry)) {
					bytes = in.readAllBytes();
				}
				PngCodec.Image ours = PngCodec.decode(bytes);
				BufferedImage theirs = ImageIO.read(new ByteArrayInputStream(bytes));
				checked++;
				if (ours.width() != theirs.getWidth() || ours.height() != theirs.getHeight()) {
					mismatched++;
					continue;
				}
				for (int y = 0; y < ours.height(); y++) {
					for (int x = 0; x < ours.width(); x++) {
						int a = ours.at(x, y);
						int argb = theirs.getRGB(x, y);
						int b = argb << 8 | argb >>> 24;
						if (a != b && !((a & 0xFF) == 0 && (b & 0xFF) == 0)) {
							mismatched++;
							y = ours.height();
							break;
						}
					}
				}
			}
		}
		expect("every vanilla face sheet was checked: " + checked, checked > 10);
		expect("PngCodec matches ImageIO on them: " + mismatched + " differ", mismatched == 0);
	}

	private static Person person(int type, PortraitSim.OverlayChoice overlay, boolean noble, double grime, int scale) {
		return new Person(type, 1, 0.4, false, noble, false, false, false, false, 0, 0, grime, 0, overlay, scale);
	}

	private static int pixel(PortraitSim.Preview preview, int x, int y) {
		return preview.argb()[y * preview.width() + x];
	}

	private static void expectNear(String name, int actual, int wanted) {
		expect(name + ": " + actual + ", wanted about " + wanted, Math.abs(actual - wanted) <= 1);
	}

	private static String frameFixture(String tags) {
		return FIXTURE.replaceFirst("(?s)TYPES: \\[.*", "TYPES: [\n\t{\n\t\tSPRITE_FILE: Keep,\n\t\tFACE: [\n\t\t\t{\n" + java.util.regex.Matcher.quoteReplacement(tags) + "\t\t\t},\n\t\t],\n\t},\n],\n");
	}

	private static final String FRAME = "\t\t\t\tFRAMES: [\n\t\t\t\t\tTest: %d,\n\t\t\t\t],\n\t\t\t\tCOLOR: SKIN,\n";

	/**
	 * Hand sums. Flat normal: x and y are 2*128/255-1 = 0.00392, z is 1. With the light at direction 220, tilt 35, the dot
	 * is 0.00392*(-0.6275-0.5265) + 0.5736 = 0.5691, and with strength 1.3 that's 0.7398. A full white sample gives 189.
	 * COLOR 254 is halved to 127, then 1.2 makes 152, and the shader's doubling makes 1.19, held at 1 by the target.
	 */
	private static void drawing() throws Exception {
		try (PortraitSim.Session session = PortraitSim.open(game, mod)) {
			PortraitSim.Preview plain = session.preview("FIXTURE", null, person(0, PortraitSim.OverlayChoice.NONE, false, 0, 1));
			expect("it draws: " + plain.problem() + " " + plain.warnings(), plain.state() == State.OK);
			expect("the portrait is 40 by 64", plain.width() == 40 && plain.height() == 64);
			expect("the types list is the adult and the child: " + plain.types(), plain.types().equals(List.of("Adult", "Child")));
			expectNear("alpha at the frame", pixel(plain, 3, 18) >>> 24, 255);
			expectNear("a lit white pixel", pixel(plain, 3, 18) & 0xFF, 189);
			expect("above the frame, 10 rows of nothing are trimmed", pixel(plain, 3, 17) == 0);
			expect("a frame starts 8 pixels down the portrait, plus its trim", (pixel(plain, 3, 18) >>> 24) != 0 && pixel(plain, 3, 26) == 0);

			PortraitSim.Preview doubled = session.preview("FIXTURE", null, person(0, PortraitSim.OverlayChoice.NONE, false, 0, 2));
			expect("scale 2 doubles the size", doubled.width() == 80 && doubled.height() == 128);
			expectNear("and the same pixel is at twice the place", pixel(doubled, 7, 37) & 0xFF, 189);

			writeSprite("FIXTURE", frameFixture(FRAME.formatted(1)));
			PortraitSim.Preview away = session.preview("FIXTURE", null, person(0, PortraitSim.OverlayChoice.NONE, false, 0, 1));
			expect("a pixel facing away from the light stays black but opaque: " + Integer.toHexString(pixel(away, 3, 18)), pixel(away, 3, 18) == 0xFF000000);

			writeSprite("FIXTURE", frameFixture(FRAME.formatted(2)));
			PortraitSim.Preview half = session.preview("FIXTURE", null, person(0, PortraitSim.OverlayChoice.NONE, false, 0, 1));
			expectNear("alpha is the sample's twice over: 128/255 squared", pixel(half, 3, 18) >>> 24, 64);

			writeSprite("FIXTURE", frameFixture(FRAME.formatted(3)));
			PortraitSim.Preview empty = session.preview("FIXTURE", null, person(0, PortraitSim.OverlayChoice.NONE, false, 0, 1));
			expect("an empty frame draws nothing", java.util.Arrays.stream(empty.argb()).allMatch(value -> value == 0));

			writeSprite("FIXTURE", frameFixture(FRAME.formatted(0) + "\t\t\t\tFRAME_OCCURRENCE: 0,\n"));
			PortraitSim.Preview none = session.preview("FIXTURE", null, person(0, PortraitSim.OverlayChoice.NONE, false, 0, 1));
			expect("occurrence 0 never draws", java.util.Arrays.stream(none.argb()).allMatch(value -> value == 0));

			writeSprite("FIXTURE", frameFixture(FRAME.formatted(0) + "\t\t\t\tOFF_X: 5,\n\t\t\t\tOFF_Y: 3,\n"));
			PortraitSim.Preview moved = session.preview("FIXTURE", null, person(0, PortraitSim.OverlayChoice.NONE, false, 0, 1));
			expect("offsets move it", pixel(moved, 4, 21) == 0 && (pixel(moved, 5, 21) >>> 24) != 0);

			writeSprite("FIXTURE", frameFixture(FRAME.formatted(0) + "\t\t\t\tCONDITIONS: {\n\t\t\t\t\tEQUAL: {\n\t\t\t\t\t\tCLASS_NOBLE: 1,\n\t\t\t\t\t},\n\t\t\t\t},\n"));
			PortraitSim.Preview commoner = session.preview("FIXTURE", null, person(0, PortraitSim.OverlayChoice.NONE, false, 0, 1));
			PortraitSim.Preview noble = session.preview("FIXTURE", null, person(0, PortraitSim.OverlayChoice.NONE, true, 0, 1));
			expect("a condition hides a part from someone it doesn't fit", java.util.Arrays.stream(commoner.argb()).allMatch(value -> value == 0));
			expect("and shows it for someone it does", (pixel(noble, 3, 18) >>> 24) != 0);

			writeSprite("FIXTURE", frameFixture(FRAME.formatted(0) + "\t\t\t\tCONDITIONS: {\n\t\t\t\t\tEQUAL: {\n\t\t\t\t\t\tNOT_A_REAL_STAT_I: 0,\n\t\t\t\t\t},\n\t\t\t\t},\n"));
			PortraitSim.Preview unknown = session.preview("FIXTURE", null, person(0, PortraitSim.OverlayChoice.NONE, false, 0, 1));
			expect("a stat it can't model is noted, not called an error: " + unknown.notes(), unknown.state() == State.OK && unknown.notes().stream().anyMatch(note -> note.contains("NOT_A_REAL_STAT_I")));

			writeSprite("FIXTURE", FIXTURE);
			PortraitSim.Preview crowned = session.preview("FIXTURE", null, person(0, PortraitSim.OverlayChoice.CROWN, false, 0, 1));
			expect("the crown starts 8 down, where the portrait does", (pixel(crowned, 3, 8) >>> 24) != 0 && pixel(plain, 3, 8) == 0);
			expectNear("its colour is the default white, doubled", pixel(crowned, 3, 8) & 0xFF, 188);

			PortraitSim.Preview grimy = session.preview("FIXTURE", null, person(0, PortraitSim.OverlayChoice.NONE, false, 0.9, 1));
			expect("grime changes the portrait", !java.util.Arrays.equals(grimy.argb(), plain.argb()));
			PortraitSim.Preview child = session.preview("FIXTURE", null, person(1, PortraitSim.OverlayChoice.NONE, false, 0, 1));
			expect("the child can be drawn", child.state() == State.OK && (pixel(child, 3, 18) >>> 24) != 0);
		}
	}

	private static void editingAndSaving() throws Exception {
		try (PortraitSim.Session session = PortraitSim.open(game, mod)) {
			PortraitSim.Opened opened = session.open("FIXTURE");
			expect("a race's own file can be edited: " + opened.race().note(), opened.race().editable());
			expect("the text is only the portrait tags", opened.text().contains("COLOR_BLOOD") && opened.text().contains("FACE") && !opened.text().contains("SPRITE_EXTRA_FILE") && !opened.text().contains("SPRITE_FILE"));
			expect("comments inside a value are shown", opened.text().contains("a comment inside a value stays"));

			byte[] before = Files.readAllBytes(spritePath("FIXTURE"));
			expect("an unchanged save writes nothing", !session.save("FIXTURE", opened.revision(), opened.text()));
			expect("and the file is byte-identical", java.util.Arrays.equals(before, Files.readAllBytes(spritePath("FIXTURE"))));

			String edited = opened.text().replace("200_10_10", "9_9_9");
			expect("a changed value saves", session.save("FIXTURE", opened.revision(), edited));
			String after = Files.readString(spritePath("FIXTURE"));
			expect("only that value changed", after.equals(FIXTURE.replace("200_10_10", "9_9_9")));

			String revised = session.open("FIXTURE").revision();
			String crlf = FIXTURE.replace("\n", "\r\n");
			Files.writeString(spritePath("FIXTURE"), crlf);
			PortraitSim.Opened windows = session.open("FIXTURE");
			expect("a CRLF file's text is shown with plain line ends", !windows.text().contains("\r"));
			String editedFace = windows.text().replace("Test: 0", "Test: 2");
			expect("a CRLF file saves", session.save("FIXTURE", windows.revision(), editedFace));
			String savedCrlf = Files.readString(spritePath("FIXTURE"));
			expect("and keeps its line ends everywhere", savedCrlf.equals(crlf.replace("Test: 0", "Test: 2")));
			expect("a stale revision is refused", refused(() -> session.save("FIXTURE", revised, edited)).contains("changed"));
			Files.writeString(spritePath("FIXTURE"), FIXTURE);

			PortraitSim.Opened again = session.open("FIXTURE");
			String noFace = again.text().replaceFirst("(?s)CHILD: \\{.*?\\n\\},\\n", "CHILD: {\n},\n");
			expect("removing a FACE saves", session.save("FIXTURE", again.revision(), noFace));
			expect("and leaves the other tags in that block", Files.readString(spritePath("FIXTURE")).contains("SPRITE_FILE: Keep") && !Files.readString(spritePath("FIXTURE")).contains("a comment inside a value stays"));
			PortraitSim.Opened removed = session.open("FIXTURE");
			String restored = removed.text().replace("CHILD: {\n},\n", "CHILD: {\n\tFACE: [\n\t],\n},\n");
			expect("adding a FACE back saves", session.save("FIXTURE", removed.revision(), restored));
			expect("it goes inside CHILD", Files.readString(spritePath("FIXTURE")).matches("(?s).*CHILD: \\{.*FACE: \\[.*\\}.*"));
			Files.writeString(spritePath("FIXTURE"), FIXTURE);

			java.util.List<syx.core.PortraitSection.AbsentBlock> blocks = PortraitSim.absent(session.open("FIXTURE").text());
			expect("absent defaults are listed for each block: " + blocks.size(), blocks.stream().anyMatch(block -> block.path().equals("TYPES.0.FACE.0") && block.absent().stream().anyMatch(absent -> absent.key().equals("OPACITY") && absent.value().equals("255"))));
			expect("a colour's defaults are false", blocks.stream().anyMatch(block -> block.path().equals("COLORS.SKIN") && block.absent().stream().anyMatch(absent -> absent.key().equals("TURNS_SICKLY") && absent.value().equals("false"))));
			expect("nothing invents a default for CONDITIONS", blocks.stream().flatMap(block -> block.absent().stream()).noneMatch(absent -> absent.key().equals("CONDITIONS")));
			String text = session.open("FIXTURE").text();
			syx.core.PortraitSection.AbsentBlock block = blocks.stream().filter(b -> b.path().equals("TYPES.0.FACE.0")).findFirst().orElseThrow();
			String added = PortraitSim.withDefault(text, block, "OPACITY", "255");
			PortraitSim.Preview withOpacity = session.preview("FIXTURE", added, Person.standard());
			expect("a default added by the button parses: " + withOpacity.problem(), withOpacity.state() != State.RED);
			expect("and the block no longer lists it", PortraitSim.absent(added).stream().filter(b -> b.path().equals("TYPES.0.FACE.0")).allMatch(b -> b.absent().stream().noneMatch(a -> a.key().equals("OPACITY"))));
		}
	}

	private interface Saving {
		void run() throws Exception;
	}

	private static String refused(Saving saving) {
		try {
			saving.run();
			return "";
		} catch (PortraitSim.Refused e) {
			return e.getMessage();
		} catch (Exception e) {
			return "unexpected: " + e;
		}
	}

	private static void problems() throws Exception {
		try (PortraitSim.Session session = PortraitSim.open(game, mod)) {
			PortraitSim.Opened opened = session.open("FIXTURE");
			String text = opened.text();
			PortraitSim.Preview broken = session.preview("FIXTURE", text.replace("COLOR_BLOOD: 200_10_10,", "COLOR_BLOOD: 200_10_10"), Person.standard());
			expect("a missing comma is red: " + broken.problem(), broken.state() == State.RED && broken.argb() == null);
			expect("and names a line in the text: " + broken.line(), broken.line() != null && broken.line() == 1);
			PortraitSim.Preview badColour = session.preview("FIXTURE", text.replace("COLOR: SKIN", "COLOR: NOPE"), Person.standard());
			expect("a colour that doesn't exist stops the game: " + badColour.problem(), badColour.state() == State.RED && badColour.problem().contains("NOPE"));
			PortraitSim.Preview noBlood = session.preview("FIXTURE", text.replace("COLOR_BLOOD: 200_10_10,\n", ""), Person.standard());
			expect("a missing required tag is red: " + noBlood.problem(), noBlood.state() == State.RED);
			String extraType = text.replace("TYPES: [\n", "TYPES: [\n\t{\n\t},\n");
			PortraitSim.Preview tooMany = session.preview("FIXTURE", extraType, Person.standard());
			expect("a different number of types is red: " + tooMany.problem(), tooMany.state() == State.RED && tooMany.problem().contains("TYPES"));
			String stranger = text + "\nHELLO: 1,\n";
			PortraitSim.Preview foreign = session.preview("FIXTURE", stranger, Person.standard());
			expect("a tag that isn't a portrait tag is refused: " + foreign.problem(), foreign.state() == State.RED && foreign.problem().contains("HELLO"));
			expect("saving red text is refused", refused(() -> session.save("FIXTURE", opened.revision(), text.replace("COLOR: SKIN", "COLOR: NOPE"))).contains("NOPE"));
			PortraitSim.Preview noColon = session.preview("FIXTURE", text.replace("COLOR_BLOOD: 200_10_10,", "COLOR_BLOOD 200_10_10,"), Person.standard());
			expect("a missing colon is named on its own line: " + noColon.problem() + " " + noColon.line(), noColon.state() == State.RED && noColon.problem().contains("missing its ':'") && noColon.line() != null && noColon.line() == 1);
			PortraitSim.Preview escaping = session.preview("FIXTURE", text.replace("Test: 0", "../../x: 0"), Person.standard());
			expect("a frame can't reach outside the face folder: " + escaping.warnings(), escaping.state() == State.WARNING);
			writeRace("SNEAKY", "../../../evil");
			expect("nor can a SPRITE_FILE, and it isn't offered for editing", !session.open("SNEAKY").race().editable() && session.preview("SNEAKY", null, Person.standard()).state() == State.RED);
			Files.delete(version.resolve("assets/init/race/SNEAKY.txt"));
			PortraitSim.Preview missing = session.preview("FIXTURE", text.replace("Test: 0", "NoSuchSheet: 0"), Person.standard());
			expect("a missing face file only warns, as in the game: " + missing.warnings(), missing.state() == State.WARNING && missing.warnings().stream().anyMatch(warning -> warning.contains("NoSuchSheet")));
			PortraitSim.Preview past = session.preview("FIXTURE", text.replace("Test: 0", "Test: 99"), Person.standard());
			expect("a row out of range only warns: " + past.warnings(), past.state() == State.WARNING);
			BufferedImage wrong = new BufferedImage(100, 60, BufferedImage.TYPE_INT_ARGB);
			ImageIO.write(wrong, "png", version.resolve("assets/sprite/race/face/Wrong.png").toFile());
			PortraitSim.Preview size = session.preview("FIXTURE", text.replace("Test: 0", "Wrong: 0"), Person.standard());
			expect("a sheet of the wrong size stops the game: " + size.problem(), size.state() == State.RED && size.problem().contains("416"));
			Files.delete(version.resolve("assets/sprite/race/face/Wrong.png"));

			BufferedImage changed = ImageIO.read(version.resolve("assets/sprite/race/face/Test.png").toFile());
			session.preview("FIXTURE", null, Person.standard());
			expect("nothing has changed yet", !session.imagesChanged());
			Path sheet = version.resolve("assets/sprite/race/face/Test.png");
			ImageIO.write(changed, "png", sheet.toFile());
			Files.setLastModifiedTime(sheet, java.nio.file.attribute.FileTime.fromMillis(Files.getLastModifiedTime(sheet).toMillis() + 5000));
			expect("a saved sheet is noticed", session.imagesChanged());
		}
	}

	private static void copying() throws Exception {
		writeRace("COPYME", "HUMAN");
		writeRace("TILAPI", "TILAPI");
		writeSprite("TILAPI", "COLOR_BLOOD: 1_2_3,\n");
		writeRace("SHARES", "FIXTURE");
		try (PortraitSim.Session session = PortraitSim.open(game, mod)) {
			Map<String, PortraitSim.Race> races = new LinkedHashMap<>();
			session.races().forEach(race -> races.put(race.name(), race));
			expect("the races are listed: " + races.keySet(), races.keySet().containsAll(List.of("COPYME", "FIXTURE", "SHARES", "TILAPI")));
			expect("a vanilla file can't be edited: " + races.get("COPYME").note(), !races.get("COPYME").editable() && races.get("COPYME").canCopy());
			expect("a same-named file can't either, because the game merges it", !races.get("TILAPI").editable() && races.get("TILAPI").canCopy());
			expect("sharing is reported", races.get("FIXTURE").sharedWith().contains("SHARES"));

			PortraitSim.Opened vanilla = session.open("COPYME");
			expect("a vanilla file's tags are shown", vanilla.text().contains("COLORS"));
			expect("and it draws: " + session.preview("COPYME", null, Person.standard()).problem(), session.preview("COPYME", null, Person.standard()).state() != State.RED);
			expect("saving it is refused", refused(() -> session.save("COPYME", vanilla.revision(), vanilla.text())).contains("Copy"));

			PortraitSim.Copied copied = session.copyToMod("COPYME");
			expect("the copy is named after the race: " + copied.spriteFile(), copied.spriteFile().equals("COPYME"));
			expect("the race now points at it", Files.readString(version.resolve("assets/init/race/COPYME.txt")).equals("SPRITE_FILE: COPYME,\nOTHER: 1,\n"));
			expect("and it can be edited now", session.open("COPYME").race().editable());
			byte[] vanillaBytes;
			try (ZipFile zip = new ZipFile(game.resolve("base/data.zip").toFile()); InputStream in = zip.getInputStream(zip.getEntry(SPRITE_FOLDER + "HUMAN.txt"))) {
				vanillaBytes = in.readAllBytes();
			}
			expect("a vanilla copy is byte for byte", java.util.Arrays.equals(vanillaBytes, Files.readAllBytes(spritePath("COPYME"))));

			writeRace("COPYME2", "HUMAN");
			writeSprite("COPYME2", "COLOR_BLOOD: 1_2_3,\n");
			writeRace("COPYME2", "HUMAN");
			Files.delete(spritePath("COPYME2"));
			Files.writeString(spritePath("COPYME2"), "keep me");
			PortraitSim.Copied taken = session.copyToMod("COPYME2");
			expect("a taken name isn't overwritten: " + taken.spriteFile(), taken.spriteFile().equals("COPYME2_2") && Files.readString(spritePath("COPYME2")).equals("keep me"));
			Files.delete(spritePath("COPYME2"));

			writeRace("HUMAN", "HUMAN");
			PortraitSim.Copied notVanilla = session.copyToMod("HUMAN");
			expect("a vanilla name isn't taken, or the game would merge them: " + notVanilla.spriteFile(), !notVanilla.spriteFile().equals("HUMAN"));
			Files.delete(version.resolve("assets/init/race/HUMAN.txt"));
			Files.delete(spritePath(notVanilla.spriteFile()));

			String merged = "COLOR_BLOOD: 1_2_3,\n_JSON_ADD: 1,\n";
			writeSprite("TILAPI", merged);
			PortraitSim.Copied fromMerged = session.copyToMod("TILAPI");
			String copyText = Files.readString(spritePath(fromMerged.spriteFile()));
			expect("a merged copy holds the merged result: " + fromMerged.spriteFile(), copyText.contains("1_2_3") && copyText.contains("COLORS"));
			PortraitSim.Preview after = session.preview("TILAPI", null, Person.standard());
			expect("and it draws: " + after.problem(), after.state() != State.RED);
			Files.delete(spritePath("TILAPI"));
			Files.delete(version.resolve("assets/init/race/TILAPI.txt"));
			Files.delete(spritePath(fromMerged.spriteFile()));
		}
	}

	private static String applied(String text, syx.core.PortraitSection.TextEdit edit) {
		return text.substring(0, edit.start()) + edit.text() + text.substring(edit.end());
	}

	/** What the colour and piece controls report, what they do to the text, and what they leave alone. */
	private static void controls() throws Exception {
		String fixture = FIXTURE
				.replace("\t\tVALUES: [\n\t\t\t254_254_254,\n\t\t],\n", "\t\tVALUES: [\n\t\t\t{R: 10, G: 20, B: 30,},\n\t\t\t{R: 40, G: 50, B: 60,},\n\t\t],\n")
				.replace("\tSKIN: {", "\tLISTED: {\n\t\tVALUES: [\n\t\t\t40_50_60,\n\t\t\t70_80_90,\n\t\t],\n\t},\n\tRANGE: {\n\t\tVALUES: {\n\t\t\tGENERATE: 4,\n\t\t\tFROM: 0_0_0,\n\t\t\tTO: 200_100_50,\n\t\t},\n\t},\n\tSHADED: {\n\t\tGENERATE_RANDOMIZE: 0.5,\n\t\tVALUES: [\n\t\t\t100_100_100,\n\t\t],\n\t},\n\tSKIN: {")
				.replaceFirst("(?s)(TYPES: \\[.*?)Test: 0,", "$1Test: 0,\n\t\t\t\t\t\tNot a frame,\n\t\t\t\t\t\tTest: 2,");
		writeSprite("FIXTURE", fixture);
		try (PortraitSim.Session session = PortraitSim.open(game, mod)) {
			String text = session.open("FIXTURE").text();
			PortraitSim.Preview preview = session.preview("FIXTURE", text, Person.standard());
			expect("controls: it draws: " + preview.problem(), preview.state() != State.RED);
			PortraitSim.ColourRow skin = preview.colours().stream().filter(row -> row.name().equals("SKIN")).findFirst().orElseThrow();
			expect("each colour has 16 slots", skin.slots().size() == 16);
			expect("a slot shows the colour as a file writes it: " + skin.slots().get(0), skin.slots().get(0).r() == 10 && skin.slots().get(0).g() == 20 && skin.slots().get(0).b() == 30);
			expect("slots spread over the entries", skin.slots().get(0).entry() == 0 && skin.slots().get(8).entry() == 1 && skin.slots().get(15).entry() == 1);
			PortraitSim.ColourRow shaded = preview.colours().stream().filter(row -> row.name().equals("SHADED")).findFirst().orElseThrow();
			expect("a generated shade names no entry", shaded.slots().get(0).entry() == 0 && shaded.slots().get(1).entry() == -1 && shaded.editable());
			PortraitSim.ColourRow range = preview.colours().stream().filter(row -> row.name().equals("RANGE")).findFirst().orElseThrow();
			expect("a FROM and TO range can't be edited by entry, and says so: " + range.note(), !range.editable() && range.note().contains("FROM"));
			expect("the blood colour is reported as written", preview.blood().r() == 200 && preview.blood().g() == 10);

			String block = applied(text, PortraitSim.colourEdit(text, "SKIN", 0, 111, 121, 131));
			expect("a block keeps its form", block.contains("{R: 111, G: 121, B: 131,}") && block.contains("{R: 40, G: 50, B: 60,}"));
			String plain = applied(text, PortraitSim.colourEdit(text, "LISTED", 0, 7, 8, 9));
			expect("a plain value keeps its form", plain.contains("7_8_9") && !plain.contains("40_50_60") && plain.contains("70_80_90"));
			expect("a changed entry reaches the slots that use it", session.preview("FIXTURE", plain, Person.standard()).colours().stream().filter(row -> row.name().equals("LISTED")).findFirst().orElseThrow().slots().get(7).r() == 6);
			expect("an entry past the end is refused", PortraitSim.colourEdit(text, "SKIN", 5, 1, 2, 3) == null);
			expect("a range is refused", PortraitSim.colourEdit(text, "RANGE", 0, 1, 2, 3) == null);
			expect("a colour that isn't there is refused", PortraitSim.colourEdit(text, "NOPE", 0, 1, 2, 3) == null);
			expect("broken text refuses rather than throwing", PortraitSim.colourEdit("COLORS: {", "SKIN", 0, 1, 2, 3) == null);
			String blood = applied(text, PortraitSim.bloodEdit(text, 1, 2, 3));
			expect("the blood colour changes", blood.contains("COLOR_BLOOD: 1_2_3,"));
			String reset = applied(block, PortraitSim.colourReset(block, text, "SKIN", 0));
			expect("Reset puts an entry back exactly", reset.equals(text));
			expect("Reset of blood too", applied(blood, PortraitSim.colourReset(blood, text, null, 0)).equals(text));

			expect("a part's frames are listed: " + preview.pieces(), preview.pieces().size() == 1 && preview.pieces().get(0).count() == 2);
			PortraitSim.PieceRow piece = preview.pieces().get(0);
			expect("the label names the sheet", piece.label().equals("Test"));
			Person second = pinned(Map.of(0, 1), Map.of());
			PortraitSim.Preview pinned = session.preview("FIXTURE", text, second);
			expect("pinning shows that frame: " + pinned.pieces().get(0), pinned.pieces().get(0).current() == 1 && pinned.pieces().get(0).pinned() && pinned.pieces().get(0).row() == 2);
			expect("an entry the game skips still counts in the text", pinned.pieces().get(0).item() == 2);
			String moved = applied(text, PortraitSim.frameRowEdit(text, 0, 0, 2, 1));
			expect("changing a row only changes that number", moved.equals(text.replace("Test: 2,", "Test: 1,")));
			expect("the first entry can be changed too", applied(text, PortraitSim.frameRowEdit(text, 0, 0, 0, 3)).contains("Test: 3,"));
			expect("the child is the last type", applied(text, PortraitSim.frameRowEdit(text, 1, 0, 0, 3)).indexOf("Test: 3,") > text.indexOf("CHILD:"));
			expect("a part that isn't there is refused", PortraitSim.frameRowEdit(text, 0, 9, 0, 1) == null);
			expect("the offset finds the entry", text.startsWith("Test: 2", PortraitSim.frameOffset(text, 0, 0, 2)));
			expect("and a colour's", text.startsWith("70_80_90", PortraitSim.colourOffset(text, "LISTED", 1)));

			writeSprite("FIXTURE", frameFixture(FRAME.formatted(0) + "\t\t\t\tFRAME_OCCURRENCE: 0,\n"));
			String hiddenText = session.open("FIXTURE").text();
			PortraitSim.Preview rolledOut = session.preview("FIXTURE", hiddenText, person(0, PortraitSim.OverlayChoice.NONE, false, 0, 1));
			expect("a hidden part is reported as hidden", !rolledOut.pieces().get(0).shown());
			PortraitSim.Preview shownAnyway = session.preview("FIXTURE", hiddenText, new Person(0, 1, 0.4, false, false, false, false, false, false, 0, 0, 0, 0, PortraitSim.OverlayChoice.NONE, 1, new PortraitSim.Pins(Map.of(0, 0), Map.of(), PortraitSim.Pins.NONE.random())));
			expect("a pin shows it anyway", shownAnyway.pieces().get(0).shown() && (pixel(shownAnyway, 3, 18) >>> 24) != 0);

			writeSprite("FIXTURE", fixture);
			Person slotted = pinned(Map.of(), Map.of("SKIN", 15));
			PortraitSim.Preview slotPreview = session.preview("FIXTURE", text, slotted);
			expect("pinning a slot makes it active", slotPreview.colours().stream().filter(row -> row.name().equals("SKIN")).findFirst().orElseThrow().active() == 15);
			PortraitSim.Preview slotZero = session.preview("FIXTURE", text, pinned(Map.of(), Map.of("SKIN", 0)));
			expect("a pinned slot changes the portrait", !java.util.Arrays.equals(slotPreview.argb(), slotZero.argb()));

			int[] bytes = PortraitSim.Pins.NONE.random().clone();
			bytes[2] = 200;
			Person randomised = new Person(0, 1, 0.4, false, false, false, false, false, false, 0, 0, 0, 0, PortraitSim.OverlayChoice.NONE, 4, new PortraitSim.Pins(Map.of(), Map.of(), bytes));
			expect("a random byte is applied", session.preview("FIXTURE", text, randomised).random().get(2) == 200);
			expect("and the others stay the seed's", session.preview("FIXTURE", text, randomised).random().get(1).equals(preview.random().get(1)));
			expect("pins never reach the file", session.open("FIXTURE").text().equals(text));
		}
		writeSprite("FIXTURE", FIXTURE);
	}

	private static Person pinned(Map<Integer, Integer> frames, Map<String, Integer> colors) {
		return new Person(0, 1, 0.4, false, false, false, false, false, false, 0, 0, 0, 0, PortraitSim.OverlayChoice.NONE, 4, new PortraitSim.Pins(frames, colors, PortraitSim.Pins.NONE.random()));
	}

	/** Vanilla is the test of a false alarm: every file must draw, every warning must be justified, and a save must change nothing. */
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
				byte[] bytes;
				try (InputStream in = zip.getInputStream(zip.getEntry(name))) {
					bytes = in.readAllBytes();
				}
				Files.write(spritePath(stem), bytes);
				writeRace(stem, stem);
			}
		}
		Set<String> allWarnings = new LinkedHashSet<>();
		try (PortraitSim.Session session = PortraitSim.open(game, mod)) {
			for (String name : names) {
				String stem = "V_" + name.substring(SPRITE_FOLDER.length(), name.length() - 4);
				byte[] before = Files.readAllBytes(spritePath(stem));
				PortraitSim.Opened opened = session.open(stem);
				expect(stem + " opens: " + opened.race().note(), opened.race().editable());
				PortraitSim.Preview preview = session.preview(stem, null, Person.standard());
				expect(stem + " draws: " + preview.problem(), preview.state() != State.RED);
				allWarnings.addAll(preview.warnings());
				boolean visible = false;
				for (int type = 0; type < preview.types().size(); type++) {
					for (PortraitSim.OverlayChoice overlay : PortraitSim.OverlayChoice.values()) {
						PortraitSim.Preview each = session.preview(stem, null, new Person(type, type + 7, 0.3, false, type % 2 == 0, false, false, false, false, 0, 0, 0.5, 0.6, overlay, 2));
						expect(stem + " type " + type + " " + overlay + " draws: " + each.problem(), each.state() != State.RED);
						visible |= java.util.Arrays.stream(each.argb()).anyMatch(value -> value >>> 24 != 0);
					}
				}
				expect(stem + " shows something", visible);
				expect(stem + " saves unchanged", !session.save(stem, opened.revision(), opened.text()));
				expect(stem + " is byte-identical afterwards", java.util.Arrays.equals(before, Files.readAllBytes(spritePath(stem))));
				String edited = withBlood(opened.text());
				expect(stem + " saves an edit", session.save(stem, opened.revision(), edited));
				String after = new String(Files.readAllBytes(spritePath(stem)), StandardCharsets.UTF_8);
				String was = new String(before, StandardCharsets.UTF_8);
				expect(stem + " only changed that value", after.equals(withBlood(was)));
				expect(stem + " keeps its comments", count(after, "**") == count(was, "**"));
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

	private static int count(String text, String part) {
		int count = 0;
		for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
			count++;
		}
		return count;
	}

	/** An agent gets what the screen gets: the same drawing, the same refusals. */
	private static void agents() throws Exception {
		writeSprite("FIXTURE", FIXTURE);
		Operations.Result listed = ops.call("list_portrait_races", in("mod", "Portrait Test"));
		expect("list_portrait_races: " + listed.payload(), listed.outcome() == Outcome.OK);
		Operations.Result opened = ops.call("open_portrait", in("mod", "Portrait Test", "race", "FIXTURE"));
		expect("open_portrait: " + opened.outcome(), opened.outcome() == Outcome.OK);
		String text = (String) opened.payload().get("text");
		Map<String, Object> personMap = in("seed", 5, "age", 0.5, "overlay", "crown", "scale", 3);
		Operations.Result preview = ops.call("preview_portrait", in("mod", "Portrait Test", "race", "FIXTURE", "person", personMap));
		expect("preview_portrait: " + preview.outcome(), preview.outcome() == Outcome.OK && "ok".equals(preview.payload().get("state")));
		try (PortraitSim.Session session = PortraitSim.open(game, mod)) {
			Person same = new Person(0, 5, 0.5, false, false, false, false, false, false, 0, 0, 0, 0, PortraitSim.OverlayChoice.CROWN, 3);
			String direct = Base64.getEncoder().encodeToString(session.preview("FIXTURE", null, same).png());
			expect("an agent's picture is the screen's picture", direct.equals(preview.payload().get("png_base64")));
		}
		Operations.Result broken = ops.call("preview_portrait", in("mod", "Portrait Test", "race", "FIXTURE", "text", text.replace("COLOR: SKIN", "COLOR: NOPE")));
		expect("unsaved text is drawn, and a problem reported: " + broken.payload().get("problem"), "red".equals(broken.payload().get("state")) && String.valueOf(broken.payload().get("problem")).contains("NOPE"));
		expect("an agent is told the colours and pieces", preview.payload().get("colours") instanceof List<?> && preview.payload().get("pieces") instanceof List<?>);
		Operations.Result recoloured = ops.call("edit_portrait_text", in("text", text, "blood", true, "rgb", "4_5_6"));
		expect("edit_portrait_text changes the text an agent gave it: " + recoloured.payload(), recoloured.outcome() == Outcome.OK && String.valueOf(recoloured.payload().get("text")).contains("COLOR_BLOOD: 4_5_6,"));
		Operations.Result moved = ops.call("edit_portrait_text", in("text", text, "type", "0", "part", "0", "item", "0", "row", "2"));
		expect("and a frame's row: " + moved.outcome(), moved.outcome() == Outcome.OK && String.valueOf(moved.payload().get("text")).contains("Test: 2,"));
		Operations.Result pinnedFrame = ops.call("preview_portrait", in("mod", "Portrait Test", "race", "FIXTURE", "person", in("colors", in("SKIN", 3), "random", List.of(-1, 9))));
		expect("an agent can pin too: " + pinnedFrame.outcome(), pinnedFrame.outcome() == Outcome.OK && ((List<?>) pinnedFrame.payload().get("random")).get(1).equals(9));
		Operations.Result vanilla = ops.call("preview_portrait", in("mod", "Portrait Test", "race", "SHARES", "person", in("overlay", "nonsense")));
		expect("a bad overlay name is refused: " + vanilla.payload(), vanilla.outcome() == Outcome.REFUSED);
		Operations.Result saved = ops.call("save_portrait", in("mod", "Portrait Test", "race", "FIXTURE", "revision", opened.payload().get("revision"), "text", text.replace("200_10_10", "7_7_7")));
		expect("save_portrait: " + saved.payload(), saved.outcome() == Outcome.OK && Boolean.TRUE.equals(saved.payload().get("changed")));
		expect("and it reached the file", Files.readString(spritePath("FIXTURE")).contains("7_7_7"));
		Operations readOnly = new Operations(() -> game, mods, new ToolkitSettings(temp.resolve("settings2.properties")), new HandbookStore(temp.resolve("handbook2"), etag -> new HandbookStore.Response(404, null, new byte[0])), true);
		Operations.Result refusedSave = readOnly.call("save_portrait", in("mod", "Portrait Test", "race", "FIXTURE", "revision", "x", "text", text));
		expect("read-only refuses a save", refusedSave.outcome() == Outcome.REFUSED);
		Operations.Result refusedCopy = readOnly.call("copy_portrait_to_mod", in("mod", "Portrait Test", "race", "FIXTURE"));
		expect("read-only refuses a copy", refusedCopy.outcome() == Outcome.REFUSED);
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
