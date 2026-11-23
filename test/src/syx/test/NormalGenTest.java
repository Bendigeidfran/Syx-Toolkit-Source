package syx.test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import syx.core.NormalGen;
import syx.core.NormalGen.TrimMode;
import syx.core.NormalGen.Tuning;
import syx.core.NormalTuningStore;
import syx.core.PngCodec;

/**
 * Generates normals on images made here, and compares the result with the player's own vanilla sheets. A
 * vanilla sheet is read from the install and only compared in memory, so nothing of the game is kept or written.
 */
public final class NormalGenTest {

	private static final String SHEET = "V71/assets/sprite/race/Folk.png";
	private static final int WHITE = 0xFFFFFFFF;
	/** The average angle a torso may be from vanilla's, in degrees. It's a guard against getting worse. */
	private static final double TORSO_LIMIT = 16;

	private static int passed;
	private static int failed;

	private NormalGenTest() {
	}

	public static void main(String[] args) throws Exception {
		Path root = Files.createTempDirectory("syx-normals");
		try {
			shapes(root.resolve("shapes"));
			frameRules(root.resolve("frames"));
			refusals(root.resolve("refusals"));
			trims(root.resolve("trims"));
			shading(root.resolve("shading"));
			sheetTypes(root.resolve("types"), args.length > 0 ? Path.of(args[0]) : null);
			if (args.length > 0) {
				vanilla(Path.of(args[0]), root.resolve("vanilla"));
				vanillaTypes(Path.of(args[0]), root.resolve("vanillaTypes"));
			} else {
				System.out.println("SKIPPED vanilla comparison: no game folder given.");
			}
		} finally {
			delete(root);
		}
		System.out.println("passed=" + passed + " failed=" + failed);
		System.exit(failed == 0 ? 0 : 1);
	}

	/** Torso frames, in standing row 7, are at x 6 and 36, 24 square. Their normals are 224 to the right. */
	private static void shapes(Path mod) throws Exception {
		BufferedImage image = blank();
		int top = 6 + 30 * 7;
		fill(image, 6 + 5, top + 2, 3, 20, WHITE);
		fill(image, 36 + 8, top + 2, 8, 20, WHITE);
		Path file = write(mod, SHEET, image);
		NormalGen.Changed changed = NormalGen.generate(mod, SHEET, false, null);
		expect("it writes the sheet: " + changed.message(), changed.changed() && changed.frames() == 2);
		PngCodec.Image result = PngCodec.decode(Files.readAllBytes(file));

		int y = top + 10;
		int[] thin = reds(result, 6 + 224 + 5, y, 3);
		expect("a 3-wide bar leans out at both edges and is level on its ridge: " + Arrays.toString(thin), near(thin, 52, 128, 204));
		int[] wide = reds(result, 36 + 224 + 8, y, 8);
		expect("an 8-wide bar leans a little less at each step in, with no flat top, as vanilla's torsos do: " + Arrays.toString(wide), near(wide, 44, 62, 77, 89, 167, 179, 194, 212));
		int topG = result.at(6 + 224 + 6, top + 2) >>> 16 & 0xFF;
		int bottomG = result.at(6 + 224 + 6, top + 21) >>> 16 & 0xFF;
		expect("G points down the screen: top " + topG + ", bottom " + bottomG, topG < 128 && bottomG > 128);

		boolean unit = true;
		boolean neverZero = true;
		for (int i = 0; i < result.rgba().length; i++) {
			int pixel = result.rgba()[i];
			if (i % result.width() >= 224 && (pixel & 0xFF) != 0) {
				double nx = ((pixel >>> 24) - 128) / 127.0;
				double ny = ((pixel >>> 16 & 0xFF) - 128) / 127.0;
				double nz = ((pixel >>> 8 & 0xFF) - 128) / 127.0;
				unit &= Math.abs(nx * nx + ny * ny + nz * nz - 1) < 0.05;
				neverZero &= pixel >>> 24 != 0 && (pixel >>> 16 & 0xFF) != 0;
			}
		}
		expect("every normal is about unit length", unit);
		expect("R and G are never 0, which a rotated frame would lose", neverZero);
	}

	private static void frameRules(Path mod) throws Exception {
		BufferedImage image = blank();
		// Grid lines, a label and art, none of which may change, in both halves.
		for (int y = 0; y < image.getHeight(); y++) {
			image.setRGB(0, y, 0xFF00FF00);
			image.setRGB(224, y, 0xFF00FF00);
			image.setRGB(5, y, 0xFF000084);
			image.setRGB(229, y, 0xFF000084);
		}
		fill(image, 80 + 224, 130, 30, 30, 0xFF112233);
		fill(image, 6, 6 + 30 * 17, 24, 24, WHITE);
		fill(image, 6 + 224, 6 + 30 * 17, 24, 24, 0xFF445566);
		fill(image, 72 + 10, 6 + 10, 4, 4, WHITE);
		fill(image, 72 + 5, 6 + 38 * 2 + 5, 20, 20, WHITE);
		Path file = write(mod, SHEET, image);
		byte[] before = Files.readAllBytes(file);
		PngCodec.Image start = PngCodec.decode(before);
		NormalGen.generate(mod, SHEET, true, null);
		PngCodec.Image after = PngCodec.decode(Files.readAllBytes(file));
		boolean leftSame = true;
		for (int y = 0; y < after.height(); y++) {
			for (int x = 0; x < 224; x++) {
				leftSame &= after.at(x, y) == start.at(x, y);
			}
		}
		expect("the art half is exactly as it was", leftSame);
		expect("grid lines stay", after.at(224, 100) == start.at(224, 100) && after.at(229, 100) == start.at(229, 100));
		expect("and the unread label area", after.at(80 + 224, 130) == start.at(80 + 224, 130));
		expect("a standing shadow is flat", after.at(6 + 224 + 10, 6 + 30 * 17 + 10) == 0x8080FFFF);
		expect("a lying piece follows the whole body in the shadow frame, not only its own art", (after.at(72 + 224 + 6, 6 + 6) & 0xFF) != 0);
		expect("and a frame with no art at all gets none", (after.at(30 + 6, 6 + 30 * 4 + 6) & 0xFF) == 0 && (after.at(36 + 224 + 6, 6 + 30 * 4 + 6) & 0xFF) == 0);
	}

	private static void refusals(Path mod) throws Exception {
		BufferedImage image = blank();
		fill(image, 10, 10, 10, 10, WHITE);
		Path file = write(mod, SHEET, image);
		byte[] original = Files.readAllBytes(file);
		NormalGen.Opened opened = NormalGen.open(mod, SHEET);
		expect("a new sheet says it has no normals: " + opened.status(), opened.listed().supported() && !opened.listed().hasNormals());
		NormalGen.generate(mod, SHEET, false, opened.revision());
		byte[] generated = Files.readAllBytes(file);
		expect("the file changed, and the sheet now has normals", !Arrays.equals(original, generated) && NormalGen.open(mod, SHEET).listed().hasNormals());
		String existing = refused(() -> NormalGen.generate(mod, SHEET, false, null));
		expect("normals aren't replaced unasked: " + existing, existing.contains("already has normals") && Arrays.equals(generated, Files.readAllBytes(file)));
		String stale = refused(() -> NormalGen.generate(mod, SHEET, true, opened.revision()));
		expect("an old revision is refused: " + stale, stale.contains("changed") && Arrays.equals(generated, Files.readAllBytes(file)));
		expect("replacing with the same result writes nothing", !NormalGen.generate(mod, SHEET, true, null).changed());

		BufferedImage small = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
		write(mod, "V71/assets/sprite/race/Small.png", small);
		String size = refused(() -> NormalGen.generate(mod, "V71/assets/sprite/race/Small.png", false, null));
		expect("a wrong size is refused: " + size, size.contains("448") && size.contains("100"));
		write(mod, "V71/assets/sprite/race/extra/Other.png", blank());
		String folder = refused(() -> NormalGen.generate(mod, "V71/assets/sprite/race/extra/Other.png", false, null));
		expect("a subfolder is a different sheet type, however big it is: " + folder, folder.contains("directly"));
		write(mod, "V71/assets/sprite/icon/A.png", blank());
		expect("but it can still be opened to look at", !NormalGen.open(mod, "V71/assets/sprite/icon/A.png").listed().supported());
		refused(() -> NormalGen.generate(mod, "../outside.png", false, null));
		refused(() -> NormalGen.generate(mod, "V71/nothing.png", false, null));

		Files.write(mod.resolve("V71/notes.txt"), new byte[] { 1 });
		Path outside = mod.resolveSibling("elsewhere");
		Files.createDirectories(outside.resolve("race"));
		ImageIO.write(blank(), "png", outside.resolve("race/Linked.png").toFile());
		Path junction = mod.resolve("V71/assets/sprite/linked");
		junction(junction, outside.resolve("race"));
		List<NormalGen.Listed> listed = NormalGen.list(mod);
		expect("only PNG files are listed: " + listed, listed.stream().allMatch(entry -> entry.path().toLowerCase(java.util.Locale.ROOT).endsWith(".png")));
		expect("and a link out of the mod is skipped", listed.stream().noneMatch(entry -> entry.path().contains("Linked")));
		String link = refused(() -> NormalGen.open(mod, "V71/assets/sprite/linked/Linked.png"));
		expect("and can't be reached by name: " + link, link.contains("link"));

		try (Stream<Path> files = Files.walk(mod)) {
			expect("no temporary file is left", files.noneMatch(path -> path.toString().endsWith(".syxtmp")));
		}
	}

	/** Each section's values change only that section, and the store remembers them. */
	private static void shading(Path mod) throws Exception {
		BufferedImage image = blank();
		int torsoTop = 6 + 30 * 7;
		fill(image, 6 + 8, torsoTop + 2, 8, 20, WHITE);
		fill(image, 6 + 8, 6 + 2, 8, 20, WHITE);
		write(mod, SHEET, image);
		int y = torsoTop + 10;
		PngCodec.Image plain = PngCodec.decode(NormalGen.preview(mod, SHEET, Map.of()).png());
		expect("a preview writes nothing", !NormalGen.open(mod, SHEET).listed().hasNormals());

		PngCodec.Image strong = PngCodec.decode(NormalGen.preview(mod, SHEET, Map.of("torso", new Tuning(150, 100))).png());
		expect("strength scales the lean: " + Arrays.toString(reds(strong, 6 + 224 + 8, y, 8)), near(reds(strong, 6 + 224 + 8, y, 8), 128 - 121, 128 - 99, 128 - 76, 128 - 59, 128 + 59, 128 + 76, 128 + 99, 128 + 121));
		expect("and leaves other sections alone", Arrays.equals(reds(strong, 6 + 224 + 8, 6 + 10, 8), reds(plain, 6 + 224 + 8, 6 + 10, 8)));

		PngCodec.Image flat = PngCodec.decode(NormalGen.preview(mod, SHEET, Map.of("torso", new Tuning(0, 100))).png());
		expect("a strength of 0 is flat", flat.at(6 + 224 + 8, y) == 0x8080FFFF);

		PngCodec.Image sharp = PngCodec.decode(NormalGen.preview(mod, SHEET, Map.of("torso", new Tuning(100, 300))).png());
		int[] sharpReds = reds(sharp, 6 + 224 + 8, y, 4);
		int[] plainReds = reds(plain, 6 + 224 + 8, y, 4);
		expect("sharpness leaves the edge and flattens further in: " + Arrays.toString(sharpReds) + " from " + Arrays.toString(plainReds), sharpReds[0] == plainReds[0] && Math.abs(sharpReds[3] - 128) < Math.abs(plainReds[3] - 128));
		PngCodec.Image soft = PngCodec.decode(NormalGen.preview(mod, SHEET, Map.of("torso", new Tuning(100, 40))).png());
		expect("and lower carries the lean further in", Math.abs((soft.at(6 + 224 + 8 + 3, y) >>> 24) - 128) > Math.abs(plainReds[3] - 128));

		PngCodec.Image most = PngCodec.decode(NormalGen.preview(mod, SHEET, Map.of("torso", new Tuning(200, 20))).png());
		boolean valid = true;
		for (int x = 0; x < 8; x++) {
			int pixel = most.at(6 + 224 + 8 + x, y);
			valid &= (pixel >>> 8 & 0xFF) > 128 && pixel >>> 24 != 0;
		}
		expect("the highest values still give usable normals", valid);

		expect("a section that isn't on the sheet is refused", refused(() -> NormalGen.preview(mod, SHEET, Map.of("wings", new Tuning(100, 100)))).contains("wings"));
		expect("and a value out of range", refused(() -> NormalGen.generate(mod, SHEET, Map.of("torso", new Tuning(500, 100)), false, null)).contains("strength"));
		expect("every frame has a section the player can see", NormalGen.sections(SHEET).size() == 21 && NormalGen.sections("V71/assets/sprite/icon/A.png").isEmpty());

		NormalTuningStore store = new NormalTuningStore(mod.resolve("tuning.properties"));
		store.save("Mod", SHEET, Map.of("torso", new Tuning(120, 80), "feet", Tuning.DEFAULT));
		expect("values are kept for that sheet, and a default isn't stored: " + store.load("Mod", SHEET), store.load("Mod", SHEET).equals(Map.of("torso", new Tuning(120, 80))));
		expect("and not for another", store.load("Other", SHEET).isEmpty());
		store.save("Mod", SHEET, Map.of());
		expect("and can be cleared", store.load("Mod", SHEET).isEmpty());
	}

	/** Each type knows its own frames, and normals land in the matching place in the right half of its own size. */
	private static void sheetTypes(Path mod, Path game) throws Exception {
		for (Object[] type : TYPES) {
			String path = "V71/assets/sprite/" + type[0] + "/Item.png";
			int width = (Integer) type[1];
			int height = (Integer) type[2];
			BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < height; y++) {
				image.setRGB(0, y, 0xFF00FF00);
				image.setRGB(width / 2, y, 0xFF00FF00);
			}
			int[] frame = NormalGen.frames(path, width, height).get(0);
			fill(image, frame[0] + 1, frame[1] + 1, frame[2] - 2, frame[2] - 2, WHITE);
			Path file = write(mod, path, image);
			byte[] before = Files.readAllBytes(file);
			boolean building = type[0].equals("settlement/structure");
			if (building) {
				expect("a building needs the game's own sheets", refused(() -> NormalGen.generate(mod, path, false, null)).contains("data.zip"));
				if (game == null) {
					continue;
				}
			}
			NormalGen.Changed changed = NormalGen.generate(game, mod, path, Map.of(), false, null);
			PngCodec.Image result = PngCodec.decode(Files.readAllBytes(file));
			int inFrame = result.at(frame[0] + width / 2 + frame[2] / 2, frame[1] + frame[2] / 2);
			expect(type[0] + ": a frame gets normals in the right place: " + changed.message(), changed.changed() && (inFrame & 0xFF) == 255);
			expect(type[0] + ": grid lines are left alone", result.at(width / 2, 2) == 0x00FF00FF && result.at(0, 2) == 0x00FF00FF && !Arrays.equals(before, Files.readAllBytes(file)));
			if (type.length > 4) {
				expect(type[0] + " starts flat", inFrame == 0x8080FFFF && result.at(frame[0] + width / 2 + 1, frame[1] + frame[2] / 2) == 0x8080FFFF);
			} else if (!building) {
				int edge = result.at(frame[0] + width / 2 + 1, frame[1] + frame[2] / 2);
				expect(type[0] + ": the left edge leans left", edge >>> 24 < 128);
			}
		}
		String wrong = "V71/assets/sprite/animal/_Texture.png";
		write(mod, wrong, new BufferedImage(328, 196, BufferedImage.TYPE_INT_ARGB));
		expect("the animal folder's texture sheet isn't an animal sheet", refused(() -> NormalGen.generate(mod, wrong, false, null)).contains("164"));
		expect("a building has no sharpness", refused(() -> NormalGen.parseTuning("V71/assets/sprite/settlement/structure/Item.png", Map.of("wall", Map.of("sharpness", 150)))).contains("sharpness"));
		expect("but strength is fine", NormalGen.parseTuning("V71/assets/sprite/settlement/structure/Item.png", Map.of("wall", Map.of("strength", 150))).get("wall").strength() == 150);
	}

	private static final Object[][] TYPES = {
			{ "animal", 164, 462, 16.0 },
			{ "race/battle", 132, 36, 12.0 },
			{ "resource", 244, 94, 20.0 },
			{ "resource/debris", 716, 28, 24.0 },
			{ "resource/growable", 460, 34, 25.0 },
			{ "settlement/structure", 576, 372, 20.0 },
			{ "race/skelleton", 316, 120, 22.0 },
			{ "race/sleep", 164, 44, 15.0 },
			{ "race/infant", 352, 22, 22.0 },
			{ "resource/minable", 364, 94, 12.0, "flat" },
			{ "game/1x1", 44, 22, 19.0 },
			{ "game/2x2", 88, 44, 17.0 },
			{ "game/3x3", 120, 60, 29.0 },
			{ "game/box", 152, 76, 7.0, "flat" },
			{ "game/combo", 144, 72, 15.0, "flat" },
			{ "game/texture", 56, 28, 7.0, "flat" } };

	/** Each vanilla sheet of a kind against what's generated from its art. Sheets in another shape are left out. */
	private static void vanillaTypes(Path game, Path temp) throws Exception {
		try (ZipFile zip = new ZipFile(game.resolve("base/data.zip").toFile())) {
			for (Object[] type : TYPES) {
				String prefix = "data/assets/sprite/" + type[0] + "/";
				String path = "V71/assets/sprite/" + type[0] + "/Item.png";
				List<ZipEntry> entries = java.util.Collections.list(zip.entries()).stream().map(e -> (ZipEntry) e).filter(e -> e.getName().startsWith(prefix) && e.getName().endsWith(".png") && e.getName().indexOf('/', prefix.length()) < 0 && e.getName().charAt(prefix.length()) != '_').toList();
				double total = 0;
				int sheets = 0;
				for (ZipEntry entry : entries) {
					byte[] bytes;
					try (var in = zip.getInputStream(entry)) {
						bytes = in.readAllBytes();
					}
					PngCodec.Image original = PngCodec.decode(bytes);
					if (NormalGen.frames(path, original.width(), original.height()).isEmpty()) {
						continue;
					}
					Path mod = temp.resolve(sheets + "-" + type[0].toString().replace('/', '_'));
					Path file = mod.resolve(path);
					Files.createDirectories(file.getParent());
					Files.write(file, bytes);
					NormalGen.generate(game, mod, path, Map.of(), true, null);
					PngCodec.Image made = PngCodec.decode(Files.readAllBytes(file));
					int[][] cells = NormalGen.frames(path, original.width(), original.height()).toArray(new int[0][]);
					double angle = meanAngle(original, made, cells, cells[0][2], original.width() / 2);
					if (!Double.isNaN(angle)) {
						total += angle;
						sheets++;
					}
				}
				double mean = total / sheets;
				System.out.printf("%s: %d vanilla sheets, mean angle from vanilla %.1f degrees%n", type[0], sheets, mean);
				expect(type[0] + " stays close to vanilla: " + mean, sheets > 0 && mean < (Double) type[3]);
			}
		}
	}

	private static void trims(Path mod) throws Exception {
		BufferedImage image = blank();
		fill(image, 6 + 4, 6 + 4, 8, 8, WHITE);
		fill(image, 6 + 224, 6, 24, 24, 0xFF8080FF);
		fill(image, 6 + 224 + 4, 6 + 4, 8, 8, 0xFF2030FF);
		for (int y = 0; y < image.getHeight(); y++) {
			image.setRGB(224, y, 0xFF00FF00);
		}
		fill(image, 80 + 224, 130, 30, 30, 0xFF112233);
		Path file = write(mod, SHEET, image);
		PngCodec.Image start = PngCodec.decode(Files.readAllBytes(file));

		NormalGen.Changed trimmed = NormalGen.trim(mod, SHEET, TrimMode.OUTSIDE_ART, false, null);
		PngCodec.Image after = PngCodec.decode(Files.readAllBytes(file));
		expect("trimming reports the pixels it removed: " + trimmed.message(), trimmed.changed() && trimmed.pixels() == 24 * 24 - 8 * 8);
		expect("normals under art stay", after.at(6 + 224 + 6, 6 + 6) == start.at(6 + 224 + 6, 6 + 6));
		expect("and the rest of the frame is transparent", after.at(6 + 224 + 1, 6 + 1) == 0);
		expect("grid lines, labels and art are untouched", after.at(224, 50) == start.at(224, 50) && after.at(80 + 224, 130) == start.at(80 + 224, 130) && after.at(6 + 6, 6 + 6) == start.at(6 + 6, 6 + 6));
		byte[] once = Files.readAllBytes(file);
		expect("a second trim writes nothing", !NormalGen.trim(mod, SHEET, TrimMode.OUTSIDE_ART, false, null).changed() && Arrays.equals(once, Files.readAllBytes(file)));

		String asked = refused(() -> NormalGen.trim(mod, SHEET, TrimMode.ALL, false, null));
		expect("clearing has to be asked for: " + asked, asked.contains("asked for") && Arrays.equals(once, Files.readAllBytes(file)));
		NormalGen.Changed cleared = NormalGen.trim(mod, SHEET, TrimMode.ALL, true, null);
		PngCodec.Image blank = PngCodec.decode(Files.readAllBytes(file));
		expect("clearing removes every normal: " + cleared.message(), cleared.changed() && blank.at(6 + 224 + 6, 6 + 6) == 0);
		expect("and only those", blank.at(224, 50) == start.at(224, 50) && blank.at(80 + 224, 130) == start.at(80 + 224, 130) && blank.at(6 + 6, 6 + 6) == start.at(6 + 6, 6 + 6));
		byte[] twice = Files.readAllBytes(file);
		expect("clearing again writes nothing", !NormalGen.trim(mod, SHEET, TrimMode.ALL, true, null).changed() && Arrays.equals(twice, Files.readAllBytes(file)));
	}

	/**
	 * Each sheet is copied to a temporary mod with its normals cleared, regenerated, and compared with the
	 * original over the pixels the game draws. Cantor is reported only: it's shaded differently on purpose.
	 */
	private static void vanilla(Path game, Path temp) throws Exception {
		Path data = game.resolve("base/data.zip");
		if (!Files.isRegularFile(data)) {
			System.out.println("SKIPPED vanilla comparison: no data.zip in " + game);
			return;
		}
		try (ZipFile zip = new ZipFile(data.toFile())) {
			for (String race : List.of("Cretonian", "Human", "Dondorian", "Tilapi", "Garthimi", "Amevia", "Argonosh", "Cantor", "ChildHumanoid", "ChildLizard")) {
				ZipEntry entry = zip.getEntry("data/assets/sprite/race/" + race + ".png");
				if (entry == null) {
					System.out.println("SKIPPED " + race + ": not in data.zip");
					continue;
				}
				byte[] bytes;
				try (var in = zip.getInputStream(entry)) {
					bytes = in.readAllBytes();
				}
				Path mod = temp.resolve(race);
				Path file = mod.resolve(SHEET);
				Files.createDirectories(file.getParent());
				Files.write(file, bytes);
				PngCodec.Image original = PngCodec.decode(bytes);
				NormalGen.generate(mod, SHEET, true, null);
				PngCodec.Image made = PngCodec.decode(Files.readAllBytes(file));
				double torso = meanAngle(original, made, standing(5, 15), 24);
				double head = meanAngle(original, made, standing(16, 16), 24);
				double feet = meanAngle(original, made, standing(0, 4), 24);
				double lying = meanAngle(original, made, lyingFrames(), 32);
				System.out.printf("%s: mean angle from vanilla, degrees: torso %.1f, head %.1f, feet %.1f, lying %.1f%n", race, torso, head, feet, lying);
				if (race.equals("Cretonian") || race.equals("Human")) {
					expect(race + "'s torso stays close to vanilla: " + torso, torso < TORSO_LIMIT);
				}
			}
		}
	}

	private static int[][] standing(int firstRow, int lastRow) {
		int[][] cells = new int[(lastRow - firstRow + 1) * 2][];
		int at = 0;
		for (int row = firstRow; row <= lastRow; row++) {
			for (int column = 0; column < 2; column++) {
				cells[at++] = new int[] { 6 + 30 * column, 6 + 30 * row };
			}
		}
		return cells;
	}

	/** The lying pieces and the lying shadow: pairs 0 to 4, and not the unnamed pair after them. */
	private static int[][] lyingFrames() {
		int[][] cells = new int[10][];
		for (int i = 0; i < 10; i++) {
			cells[i] = new int[] { 72 + 38 * (i % 4), 6 + 38 * (i / 4) };
		}
		return cells;
	}

	/**
	 * Mean angle, in degrees, between vanilla's and generated normals, wherever vanilla has art. Only frames are
	 * measured: a sheet's grid lines are opaque in both halves and identical, and counting them flatters the average.
	 */
	private static double meanAngle(PngCodec.Image original, PngCodec.Image made, int[][] cells, int size) {
		return meanAngle(original, made, cells, size, 224);
	}

	private static double meanAngle(PngCodec.Image original, PngCodec.Image made, int[][] cells, int size, int half) {
		double total = 0;
		int count = 0;
		for (int[] cell : cells) {
			for (int y = cell[1]; y < cell[1] + size; y++) {
				for (int x = cell[0]; x < cell[0] + size; x++) {
					if ((original.at(x, y) & 0xFF) == 0 || (original.at(x + half, y) & 0xFF) == 0 || (made.at(x + half, y) & 0xFF) == 0) {
						continue;
					}
					total += angle(original.at(x + half, y), made.at(x + half, y));
					count++;
				}
			}
		}
		return count == 0 ? Double.NaN : total / count;
	}

	private static double angle(int a, int b) {
		double[] first = vector(a);
		double[] second = vector(b);
		double dot = first[0] * second[0] + first[1] * second[1] + first[2] * second[2];
		return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
	}

	private static double[] vector(int pixel) {
		double x = ((pixel >>> 24) - 128) / 127.0;
		double y = ((pixel >>> 16 & 0xFF) - 128) / 127.0;
		double z = ((pixel >>> 8 & 0xFF) - 128) / 127.0;
		double length = Math.max(1e-6, Math.sqrt(x * x + y * y + z * z));
		return new double[] { x / length, y / length, z / length };
	}

	private static int[] reds(PngCodec.Image image, int x, int y, int count) {
		int[] out = new int[count];
		for (int i = 0; i < count; i++) {
			out[i] = image.at(x + i, y) >>> 24;
		}
		return out;
	}

	private static boolean near(int[] actual, int... expected) {
		if (actual.length != expected.length) {
			return false;
		}
		for (int i = 0; i < actual.length; i++) {
			if (Math.abs(actual[i] - expected[i]) > 2) {
				return false;
			}
		}
		return true;
	}

	private static BufferedImage blank() {
		return new BufferedImage(448, 546, BufferedImage.TYPE_INT_ARGB);
	}

	private static void fill(BufferedImage image, int left, int top, int width, int height, int argb) {
		for (int y = top; y < top + height; y++) {
			for (int x = left; x < left + width; x++) {
				image.setRGB(x, y, argb);
			}
		}
	}

	private static Path write(Path mod, String relative, BufferedImage image) throws IOException {
		Path file = mod.resolve(relative);
		Files.createDirectories(file.getParent());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		Files.write(file, out.toByteArray());
		return file;
	}

	private static void junction(Path link, Path target) throws Exception {
		Process process = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString()).redirectErrorStream(true).start();
		process.getInputStream().readAllBytes();
		if (process.waitFor() != 0) {
			throw new IOException("Couldn't make a junction.");
		}
	}

	private interface Action {
		void run() throws Exception;
	}

	private static String refused(Action action) {
		try {
			action.run();
			expect("this should have been refused", false);
			return "";
		} catch (NormalGen.Refused e) {
			passed++;
			return e.getMessage();
		} catch (Exception e) {
			expect("this should have been refused, not " + e, false);
			return "";
		}
	}

	private static void expect(String name, boolean ok) {
		if (ok) {
			passed++;
		} else {
			failed++;
			System.out.println("FAIL: " + name);
		}
	}

	private static void delete(Path root) throws IOException {
		try (Stream<Path> files = Files.walk(root)) {
			for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(file);
			}
		}
	}
}
