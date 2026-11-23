package syx.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import syx.core.GameParser.Node;
import syx.core.GameParser.ReadException;
import syx.core.PortraitModel.Overlay;
import syx.core.PortraitRenderer.Sheet;
import syx.core.PortraitSection.AbsentBlock;

/** The Race Portrait Sim, for every front end. Nothing here touches the game's files, and only saving and copying write. */
public final class PortraitSim {

	public enum State {
		/** Drawn, with nothing to report. */
		OK,
		/** Drawn as the game would, with something to report. */
		WARNING,
		/** The game couldn't load this, so it wasn't drawn. */
		RED
	}

	public enum OverlayChoice {
		NONE, CROWN, RAIDER, MERC;

		@Override
		public String toString() {
			return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
		}
	}

	/** Why something wasn't done. Nothing was changed. */
	public static final class Refused extends Exception {
		private static final long serialVersionUID = 1L;

		Refused(String message) {
			super(message);
		}
	}

	/**
	 * A race in the mod.
	 *
	 * @param spriteFile what its SPRITE_FILE names, or null if that couldn't be read
	 * @param editable whether its portrait tags can be saved here
	 * @param canCopy whether Copy to Mod can give it its own file
	 * @param note why it can't be edited, or null
	 * @param sharedWith other races in the mod using the same file
	 */
	public record Race(String name, String spriteFile, boolean editable, boolean canCopy, String note, List<String> sharedWith) {
	}

	/**
	 * What's shown when a race is opened.
	 *
	 * @param file the sprite file, relative to the mod
	 * @param revision identifies the file as read, and is needed to save
	 */
	public record Opened(Race race, String file, String text, String revision) {
	}

	/**
	 * The person to draw. Equipment is 0 to 5, and age, grime and injury are 0 to 1.
	 *
	 * @param type which of the race's types: its adults in order, then the child
	 */
	public record Person(int type, long seed, double age, boolean dead, boolean noble, boolean slave, boolean parent, boolean retired, boolean sick, int plate, int leather, double grime, double injury, OverlayChoice overlay, int scale, Pins pins) {
		public Person(int type, long seed, double age, boolean dead, boolean noble, boolean slave, boolean parent, boolean retired, boolean sick, int plate, int leather, double grime, double injury, OverlayChoice overlay, int scale) {
			this(type, seed, age, dead, noble, slave, parent, retired, sick, plate, leather, grime, injury, overlay, scale, Pins.NONE);
		}

		public static Person standard() {
			return new Person(0, 1, 0.4, false, false, false, false, false, false, 0, 0, 0, 0, OverlayChoice.NONE, 4);
		}

		public Person withSeed(long value) {
			return new Person(type, value, age, dead, noble, slave, parent, retired, sick, plate, leather, grime, injury, overlay, scale, pins);
		}
	}

	/**
	 * What's held still while looking at a portrait. None of this is in a race's files, because the game rolls it per person.
	 *
	 * @param frames for a part of the face, by its index, which entry of its FRAMES to show. It's shown even if its conditions would hide it.
	 * @param colors for a colour's name, which of its 16 slots to use
	 * @param random the 8 bytes the RANDOM_n stats read, or a negative byte to leave the seed's value
	 */
	public record Pins(Map<Integer, Integer> frames, Map<String, Integer> colors, int[] random) {
		public static final Pins NONE = new Pins(Map.of(), Map.of(), new int[] { -1, -1, -1, -1, -1, -1, -1, -1 });
	}

	/**
	 * One of a colour's 16 slots. Channels are as a file writes them, double what the game holds.
	 *
	 * @param entry the VALUES entry this slot comes from, or -1 for a shade the game generates
	 */
	public record ColourSlot(int r, int g, int b, int entry) {
	}

	/**
	 * A colour of the race and how this person gets it.
	 *
	 * @param editable whether its VALUES can be changed by entry, which a FROM and TO range can't be
	 * @param note how this person's state changes it, or what stops it being edited, or null
	 */
	public record ColourRow(String name, int active, boolean pinned, boolean editable, String note, List<ColourSlot> slots) {
	}

	/**
	 * One part of the face that has frames.
	 *
	 * @param part its index in FACE
	 * @param current which of its frames is showing
	 * @param item that frame's entry in FRAMES
	 * @param row that frame's row in its sheet
	 * @param rows how many rows that sheet has, or 0 if it couldn't be found
	 */
	public record PieceRow(int part, String label, int current, int count, boolean shown, boolean pinned, String file, int item, int row, int rows) {
	}

	/** A change to make to the portrait text. The end is exclusive. */
	public static PortraitSection.TextEdit colourEdit(String text, String colour, int entry, int r, int g, int b) {
		return PortraitSection.colourEdit(text, colour, entry, r, g, b);
	}

	public static PortraitSection.TextEdit bloodEdit(String text, int r, int g, int b) {
		return PortraitSection.bloodEdit(text, r, g, b);
	}

	/** Puts a colour entry back as it is in the saved text. A null colour means the blood colour. */
	public static PortraitSection.TextEdit colourReset(String text, String saved, String colour, int entry) {
		return PortraitSection.colourReset(text, saved, colour, entry);
	}

	public static PortraitSection.TextEdit frameRowEdit(String text, int type, int part, int item, int row) {
		return PortraitSection.frameRowEdit(text, type, part, item, row);
	}

	/** Where a colour entry is in the text, or null. A null colour means the blood colour. */
	public static Integer colourOffset(String text, String colour, int entry) {
		return PortraitSection.colourOffset(text, colour, entry);
	}

	public static Integer frameOffset(String text, int type, int part, int item) {
		return PortraitSection.frameOffset(text, type, part, item);
	}

	/**
	 * The result of drawing.
	 *
	 * @param problem why it's RED, or null
	 * @param line the line of the text the problem is on, or null
	 * @param warnings what the game would only warn about
	 * @param notes where the sim differs from a real person, which aren't problems
	 * @param types the race's types, in the order Person.type counts them
	 * @param argb the portrait's pixels, or null if it's RED
	 * @param absent the tags with a default in the game code that the text doesn't set
	 * @param colours the race's colours, empty if it's RED
	 * @param blood the blood colour as a file writes it, or null if it's RED
	 * @param pieces the parts of the face being drawn that have frames, empty if it's RED
	 * @param random the 8 bytes this person's RANDOM_n stats read, empty if it's RED
	 */
	public record Preview(State state, String problem, Integer line, List<String> warnings, List<String> notes, List<String> types, int width, int height, int[] argb, List<AbsentBlock> absent,
			List<ColourRow> colours, ColourSlot blood, List<PieceRow> pieces, List<Integer> random) {
		/** The portrait as a PNG, or null if it's RED. */
		public byte[] png() throws IOException {
			return argb == null ? null : PngCodec.encode(width, height, argb);
		}
	}

	/** What Copy to Mod did. */
	public record Copied(String spriteFile, String file) {
	}

	/**
	 * A face or addon sheet the game would find.
	 *
	 * @param inMod whether the mod has it. A mod's sheet with the same name as one of the game's replaces it for every race
	 * @param inGame whether the game has one by this name
	 * @param frames how many frames it has: 4 to each 60 pixel row of a face sheet (RaceFrameMaker.java:139-146), one to each
	 *        36 pixel row of an addon sheet (RCrown.java:65-68), or 0 if its size stops the game
	 * @param problem why the game can't use it, or null
	 */
	public record SheetInfo(String name, boolean addon, boolean inMod, boolean inGame, int width, int height, int frames, String problem) {
	}

	/** A face sheet's frame grid: 4 frames of 52 by 60 to a row, each with 40 by 48 of art 6 in (RaceFrameMaker.java:139-146). */
	public static final int FACE_WIDTH = 416;
	public static final int FACE_CELL_W = 52;
	public static final int FACE_CELL_H = 60;
	/** An addon sheet's grid: one frame of 52 by 36 to a row, 40 by 24 of art 6 in (RCrown.java:61-68,82-105). */
	public static final int ADDON_WIDTH = 104;
	public static final int ADDON_CELL_H = 36;

	private static final String ZIP_ROOT = "data/assets/";
	private static final Pattern KEYED_ERROR = Pattern.compile("Error parsing line: (\\d+), key: (.+?)\\. (.*)", Pattern.DOTALL);

	private PortraitSim() {
	}

	/** The caller closes it, which closes the game's parser. */
	public static Session open(Path game, ModEntry mod) throws GameParser.UnavailableException, IOException {
		GameParser parser = GameParser.open(GameInstall.jarIn(game));
		boolean opened = false;
		try {
			Session session = new Session(game, mod, parser);
			opened = true;
			return session;
		} finally {
			if (!opened) {
				parser.close();
			}
		}
	}

	/** Where a frame's cell is on its sheet: x, y, width and height, in the art half. */
	public static int[] frameCell(boolean addon, int index) {
		if (addon) {
			return new int[] { 0, index * ADDON_CELL_H, FACE_CELL_W, ADDON_CELL_H };
		}
		return new int[] { FACE_CELL_W * (index % 4), FACE_CELL_H * (index / 4), FACE_CELL_W, FACE_CELL_H };
	}

	/** How many frames a sheet of this size has, or 0 if its size stops the game. */
	public static int frameCount(boolean addon, int width, int height) {
		if (addon) {
			return width == ADDON_WIDTH && height >= ADDON_CELL_H ? height / ADDON_CELL_H : 0;
		}
		return width == FACE_WIDTH && height >= FACE_CELL_H ? height / FACE_CELL_H * 4 : 0;
	}

	/** Which frames have no art at all, so a picker can say they draw nothing. Only the art half, the left, is looked at. */
	public static List<Boolean> emptyFrames(PngCodec.Image image, boolean addon) {
		List<Boolean> empty = new ArrayList<>();
		int count = frameCount(addon, image.width(), image.height());
		for (int i = 0; i < count; i++) {
			int[] cell = frameCell(addon, i);
			boolean blank = true;
			for (int y = cell[1]; y < cell[1] + cell[3] && blank; y++) {
				for (int x = cell[0]; x < cell[0] + cell[2]; x++) {
					if ((image.at(x, y) & 0xFF) != 0) {
						blank = false;
						break;
					}
				}
			}
			empty.add(blank);
		}
		return empty;
	}

	/**
	 * A sheet's art half as a PNG, with each frame's cell outlined and the picked ones filled at the corners, so an
	 * agent can see what it's choosing. Frames count across then down, from 0.
	 */
	public static byte[] gridPng(PngCodec.Image image, boolean addon, java.util.Collection<Integer> picked) throws IOException {
		int width = image.width() / 2;
		int height = image.height();
		int[] argb = new int[width * height];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int rgba = image.at(x, y);
				argb[y * width + x] = (rgba & 0xFF) << 24 | rgba >>> 8;
			}
		}
		int count = frameCount(addon, image.width(), image.height());
		for (int i = 0; i < count; i++) {
			int[] cell = frameCell(addon, i);
			int colour = picked.contains(i) ? 0xFFFF3B30 : 0x80808080;
			for (int x = cell[0]; x < cell[0] + cell[2] && x < width; x++) {
				argb[cell[1] * width + x] = colour;
				argb[Math.min(height - 1, cell[1] + cell[3] - 1) * width + x] = colour;
			}
			for (int y = cell[1]; y < cell[1] + cell[3] && y < height; y++) {
				argb[y * width + cell[0]] = colour;
				argb[y * width + Math.min(width - 1, cell[0] + cell[2] - 1)] = colour;
			}
		}
		return PngCodec.encode(width, height, argb);
	}

	/** Every block's absent tags with a default, in the text as it stands. */
	public static List<AbsentBlock> absent(String text) {
		return PortraitSection.absent(text);
	}

	/** The block holding this offset of the text, or null. */
	public static AbsentBlock blockAt(List<AbsentBlock> blocks, int offset) {
		return PortraitSection.blockAt(blocks, offset);
	}

	/** The text with a default added to a block, for the person to edit. */
	public static String withDefault(String text, AbsentBlock block, String key, String value) {
		return PortraitSection.withTag(text, block, key, value);
	}

	public static final class Session implements AutoCloseable {
		private record Loaded(String stamp, Sheet sheet) {
		}

		private final Path game;
		private final GameParser parser;
		private final Path version;
		private final Path raceFolder;
		private final Path spriteFolder;
		private final Path faceFolder;
		private final Set<String> vanillaFiles = new HashSet<>();
		private final Map<String, Loaded> sheets = new HashMap<>();
		private final Map<Path, String> usedImages = new HashMap<>();
		private final ModEntry mod;

		private Session(Path game, ModEntry mod, GameParser parser) throws IOException, GameParser.UnavailableException {
			this.game = game;
			this.mod = mod;
			this.parser = parser;
			this.version = AssetCreator.versionFolder(mod.root(), parser.version());
			this.raceFolder = version.resolve("assets").resolve("init").resolve("race");
			this.spriteFolder = raceFolder.resolve("sprite");
			this.faceFolder = version.resolve("assets").resolve("sprite").resolve("race").resolve("face");
			try (ZipFile zip = new ZipFile(zipFile().toFile())) {
				Enumeration<? extends ZipEntry> entries = zip.entries();
				while (entries.hasMoreElements()) {
					vanillaFiles.add(entries.nextElement().getName());
				}
			}
		}

		private Path zipFile() {
			return game.resolve("base").resolve("data.zip");
		}

		private boolean vanillaHas(String relative) {
			return vanillaFiles.contains(ZIP_ROOT + relative);
		}

		private byte[] vanillaBytes(String relative) throws IOException {
			try (ZipFile zip = new ZipFile(zipFile().toFile()); InputStream in = zip.getInputStream(zip.getEntry(ZIP_ROOT + relative))) {
				return in.readAllBytes();
			}
		}

		@Override
		public void close() {
			parser.close();
		}

		private static String nameOf(Path file) {
			String name = file.getFileName().toString();
			return name.substring(0, name.length() - 4);
		}

		private List<String> raceNames() throws IOException {
			if (!Files.isDirectory(raceFolder)) {
				return List.of();
			}
			try (Stream<Path> files = Files.list(raceFolder)) {
				return files.filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
						.filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt") && !file.getFileName().toString().startsWith("_"))
						.map(Session::nameOf).sorted().toList();
			}
		}

		public List<Race> races() throws IOException {
			List<Race> found = new ArrayList<>();
			Map<String, String> files = new HashMap<>();
			List<String> names = raceNames();
			for (String name : names) {
				files.put(name, spriteFileName(name));
			}
			for (String name : names) {
				found.add(describe(name, files));
			}
			return found;
		}

		/** What the game reads as this race's SPRITE_FILE, or null if it can't be read. */
		private String spriteFileName(String name) throws IOException {
			String vanilla = "init/race/" + name + ".txt";
			Path temp = vanillaHas(vanilla) ? tempCopy(vanilla) : null;
			try {
				Path file = raceFolder.resolve(name + ".txt");
				Node root = temp == null ? parser.root(file) : parser.root(List.of(file, temp));
				return root.has("SPRITE_FILE") ? root.value("SPRITE_FILE") : null;
			} catch (ReadException e) {
				return null;
			} finally {
				deleteQuietly(temp);
			}
		}

		private Path tempCopy(String vanilla) throws IOException {
			Path temp = Files.createTempFile("syx-portrait", ".txt");
			Files.write(temp, vanillaBytes(vanilla));
			return temp;
		}

		private Race describe(String name, Map<String, String> files) throws IOException {
			String sprite = files.get(name);
			if (sprite == null) {
				return new Race(name, null, false, false, "This race's file couldn't be read, or names no SPRITE_FILE.", List.of());
			}
			if (!isPlainName(sprite)) {
				return new Race(name, null, false, false, "SPRITE_FILE names " + sprite + ", which isn't a plain file name, so nothing is read or written through it.", List.of());
			}
			List<String> shared = new ArrayList<>();
			files.forEach((other, value) -> {
				if (!other.equals(name) && sprite.equals(value)) {
					shared.add(other);
				}
			});
			shared.sort(null);
			boolean own = Files.isRegularFile(spriteFolder.resolve(sprite + ".txt"), LinkOption.NOFOLLOW_LINKS);
			boolean vanilla = vanillaHas("init/race/sprite/" + sprite + ".txt");
			boolean setsFile = setsSpriteFile(name);
			if (!own && !vanilla) {
				return new Race(name, sprite, false, false, "SPRITE_FILE names " + sprite + ", which is neither in this mod nor in the game.", shared);
			}
			if (own && !vanilla) {
				return new Race(name, sprite, true, false, null, shared);
			}
			String reason = own
					? "This file has the same name as one of the game's, so the game merges the two. Copy it to give this race its own file."
					: "This race uses the game's own " + sprite + " file. Copy it to this mod to edit it.";
			if (!setsFile) {
				reason += " This race's own file doesn't set SPRITE_FILE, so the toolkit can't point it at a copy.";
			}
			return new Race(name, sprite, false, setsFile, reason, shared);
		}

		/** A name from a mod's file must stay inside the folder it's looked up in. */
		private static boolean isPlainName(String name) {
			return !name.isBlank() && !name.contains("..") && name.chars().noneMatch(c -> c == '/' || c == '\\' || c == ':' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|' || c < 32);
		}

		private boolean setsSpriteFile(String name) throws IOException {
			try {
				return parser.root(raceFolder.resolve(name + ".txt")).has("SPRITE_FILE");
			} catch (ReadException e) {
				return false;
			}
		}

		Race race(String name) throws Refused, IOException {
			for (Race race : races()) {
				if (race.name().equals(name)) {
					return race;
				}
			}
			throw new Refused(mod.folderName() + " has no race called " + name + ".");
		}

		/** Where this race's sprite file is, if it has one. */
		public Path spriteFilePath(String spriteFile) {
			return spriteFolder.resolve(spriteFile + ".txt");
		}

		public Path raceFilePath(String race) {
			return raceFolder.resolve(race + ".txt");
		}

		private String relative(Path file) {
			return mod.root().relativize(file).toString().replace('\\', '/');
		}

		public Opened open(String name) throws Refused, IOException {
			return open(name, PortraitSection.SPEC);
		}

		/** A race's tags of one section of its sprite file. */
		Opened open(String name, SpriteFileSection.Spec spec) throws Refused, IOException {
			Race race = race(name);
			if (race.spriteFile() == null) {
				return new Opened(race, "", "", "");
			}
			Path file = spriteFilePath(race.spriteFile());
			boolean own = Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS);
			String vanilla = "init/race/sprite/" + race.spriteFile() + ".txt";
			if (!own && !vanillaHas(vanilla)) {
				return new Opened(race, "", "", "");
			}
			byte[] bytes = own ? Files.readAllBytes(file) : vanillaBytes(vanilla);
			String shown = own ? relative(file) : "the game's " + vanilla.substring("init/".length());
			String text;
			try {
				text = decode(bytes);
			} catch (CharacterCodingException e) {
				return new Opened(locked(race, "This file isn't saved as UTF-8, so editing it here could damage it."), shown, "", revision(bytes));
			}
			String section;
			try {
				section = SpriteFileSection.extract(spec, text);
			} catch (RuntimeException e) {
				return new Opened(locked(race, "This file is laid out in a way the toolkit can't safely edit."), shown, "", revision(bytes));
			}
			if (race.editable() && !agrees(file, text)) {
				return new Opened(locked(race, "This file is laid out in a way the toolkit can't safely edit."), shown, section, revision(bytes));
			}
			return new Opened(race, shown, section, revision(bytes));
		}

		private static Race locked(Race race, String note) {
			return new Race(race.name(), race.spriteFile(), false, false, note, race.sharedWith());
		}

		/** The locator's view of the file must match the game's parser before anything is spliced into it. */
		private boolean agrees(Path file, String text) throws IOException {
			try {
				return PortraitSection.agrees(text, parser.root(file));
			} catch (ReadException e) {
				return true; // The file's own mistake is shown as a red preview, not as a layout problem.
			}
		}

		static String decode(byte[] bytes) throws CharacterCodingException {
			return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
		}

		private static String revision(byte[] bytes) {
			try {
				return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
			} catch (NoSuchAlgorithmException e) {
				throw new IllegalStateException(e);
			}
		}

		/**
		 * Draws a race.
		 *
		 * @param text the portrait tags to use, or null for what's on disk. It's ignored if the race can't be edited.
		 */
		public Preview preview(String name, String text, Person person) throws Refused, IOException {
			Race race = race(name);
			if (race.spriteFile() == null || race.note() != null && !race.canCopy() && !Files.isRegularFile(spriteFilePath(race.spriteFile())) && !vanillaHas("init/race/sprite/" + race.spriteFile() + ".txt")) {
				return red(race.note(), null, List.of());
			}
			Set<String> warnings = new LinkedHashSet<>();
			Set<String> notes = new LinkedHashSet<>();
			usedImages.clear();
			String section = text;
			List<AbsentBlock> absent = List.of();
			try {
				Node root;
				if (race.editable()) {
					byte[] bytes = Files.readAllBytes(spriteFilePath(race.spriteFile()));
					String file = decode(bytes);
					if (section == null) {
						section = PortraitSection.extract(file);
					}
					absent = PortraitSection.absent(section);
					root = sectionRoot(section);
					PortraitSection.Issue issue = PortraitSection.problem(section, file);
					if (issue != null) {
						return red(issue.message(), issue.line(), absent);
					}
				} else {
					root = layeredRoot(race.spriteFile());
				}
				PortraitModel model = PortraitModel.read(root, parser, images(), warnings);
				return draw(model, person, warnings, notes, absent, race.editable() ? section : null);
			} catch (ReadException e) {
				Problem problem = problem(e);
				return red(problem.message(), problem.line(), absent);
			} catch (PortraitModel.Stop e) {
				return red(e.getMessage(), null, absent);
			} catch (CharacterCodingException e) {
				return red("This file isn't saved as UTF-8.", null, absent);
			} catch (RuntimeException e) {
				return red("The toolkit couldn't read this text: " + e, null, absent);
			}
		}

		/** Parsing reads the whole file straight away, so the copy can go as soon as that's done. */
		Node sectionRoot(String section) throws IOException, ReadException {
			Path temp = Files.createTempFile("syx-portrait", ".txt");
			try {
				Files.writeString(temp, section);
				return parser.root(temp);
			} finally {
				deleteQuietly(temp);
			}
		}

		Node layeredRoot(String spriteFile) throws IOException, ReadException {
			Path own = spriteFilePath(spriteFile);
			String vanilla = "init/race/sprite/" + spriteFile + ".txt";
			boolean hasOwn = Files.isRegularFile(own, LinkOption.NOFOLLOW_LINKS);
			if (!vanillaHas(vanilla)) {
				return parser.root(own);
			}
			Path temp = tempCopy(vanilla);
			try {
				return hasOwn ? parser.root(List.of(own, temp)) : parser.root(temp);
			} finally {
				deleteQuietly(temp);
			}
		}

		private Preview draw(PortraitModel model, Person person, Set<String> warnings, Set<String> notes, List<AbsentBlock> absent, String section) throws ReadException {
			int index = Math.max(0, Math.min(model.types.size() - 1, person.type()));
			PortraitModel.Type type = model.types.get(index);
			int[] randomness = PortraitRenderer.randomness(person.seed());
			PortraitRenderer.applyRandom(randomness, person.pins().random());
			PortraitRenderer.Subject subject = new PortraitRenderer.Subject(randomness, clamp(person.age()), person.dead(), person.noble(), person.slave(), person.parent(), person.retired(), person.sick(),
					equipment(person.plate()), equipment(person.leather()), clamp(person.grime()), clamp(person.injury()), type.child());
			Overlay overlay = overlay(model, person.overlay(), randomness, warnings);
			int scale = Math.max(1, Math.min(8, person.scale()));
			List<PortraitRenderer.Choice> choices = new ArrayList<>();
			int[] argb = PortraitRenderer.render(model, type, subject, overlay, scale, person.pins(), parser, notes, choices);
			List<String> types = model.types.stream().map(PortraitModel.Type::label).toList();
			List<Integer> random = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				random.add(PortraitRenderer.random(randomness, 64 + 8 * i, 31) & 0xFF);
			}
			return new Preview(warnings.isEmpty() ? State.OK : State.WARNING, null, null, List.copyOf(warnings), List.copyOf(notes), types, PortraitRenderer.WIDTH * scale, PortraitRenderer.HEIGHT * scale, argb, absent,
					colourRows(model, subject, person.pins(), section), written(model.blood, 0), pieceRows(type, choices), random);
		}

		/** A file writes double what the game holds. A channel a block wrote above 255 is shown as 255. */
		private static ColourSlot written(GameParser.Rgb color, int entry) {
			return new ColourSlot(Math.min(255, color.r() * 2), Math.min(255, color.g() * 2), Math.min(255, color.b() * 2), entry);
		}

		private static List<ColourRow> colourRows(PortraitModel model, PortraitRenderer.Subject subject, Pins pins, String section) {
			List<ColourRow> rows = new ArrayList<>();
			for (PortraitModel.ColorSet set : model.colors) {
				List<ColourSlot> slots = new ArrayList<>();
				for (int i = 0; i < 16; i++) {
					slots.add(written(set.slots()[i], set.entries()[i]));
				}
				boolean editable = section != null && PortraitSection.colourEdit(section, set.name(), 0, 0, 0, 0) != null;
				rows.add(new ColourRow(set.name(), PortraitRenderer.activeSlot(set, subject, pins, new LinkedHashSet<>()), pins.colors().containsKey(set.name()), editable, colourNote(set, subject, section != null && !editable), slots));
			}
			return rows;
		}

		private static String colourNote(PortraitModel.ColorSet set, PortraitRenderer.Subject subject, boolean generated) {
			List<String> notes = new ArrayList<>();
			if (generated) {
				notes.add("VALUES is a range. Change its FROM and TO in the text.");
			}
			if (subject.retired() && set.turnsGray()) {
				notes.add("Retired shows this as grey.");
			}
			if (subject.dead() && set.turnsWhite()) {
				notes.add("Dead mixes this toward white.");
			}
			if (subject.sick() && set.sickly()) {
				notes.add("Sick tints this.");
			}
			return notes.isEmpty() ? null : String.join(" ", notes);
		}

		private static List<PieceRow> pieceRows(PortraitModel.Type type, List<PortraitRenderer.Choice> choices) {
			List<String> labels = new ArrayList<>();
			for (PortraitModel.Part part : type.parts()) {
				labels.add(pieceLabel(part));
			}
			Map<String, Integer> seen = new HashMap<>();
			List<PieceRow> rows = new ArrayList<>();
			for (int i = 0; i < type.parts().size(); i++) {
				PortraitModel.Part part = type.parts().get(i);
				if (part.frames().isEmpty()) {
					continue;
				}
				String label = labels.get(i);
				if (java.util.Collections.frequency(labels, label) > 1) {
					label += " " + seen.merge(label, 1, Integer::sum);
				}
				PortraitRenderer.Choice choice = choices.get(i);
				PortraitModel.Frame frame = part.frames().get(choice.frame());
				rows.add(new PieceRow(i, label, choice.frame(), part.frames().size(), choice.shown(), choice.pinned(), frame.file(), frame.item(), frame.index(), frame.sheet() == null ? 0 : frame.sheet().frames()));
			}
			return rows;
		}

		private static String pieceLabel(PortraitModel.Part part) {
			Set<String> files = new LinkedHashSet<>();
			part.frames().forEach(frame -> files.add(frame.file()));
			return String.join(", ", files);
		}

		private static double clamp(double value) {
			return Math.max(0, Math.min(1, value));
		}

		private static int equipment(int value) {
			return Math.max(0, Math.min(PortraitRenderer.MAX_EQUIPMENT, value));
		}

		/** The crown is always the first (EContext.java:742). Raiders and mercenaries pick by the person's roll (RaiderPortrait.java:46, UIMerenaries.java:216). */
		private static Overlay overlay(PortraitModel model, OverlayChoice choice, int[] randomness, Set<String> warnings) {
			if (choice == OverlayChoice.NONE) {
				return null;
			}
			List<Overlay> list = switch (choice) {
			case CROWN -> model.crowns;
			case RAIDER -> model.raiders;
			default -> model.mercs;
			};
			if (list.isEmpty()) {
				warnings.add(choice + " has no sheet the game can use, so nothing is drawn for it.");
				return null;
			}
			return choice == OverlayChoice.CROWN ? list.get(0) : list.get(PortraitRenderer.random(randomness, 9, 31) % list.size());
		}

		private PortraitModel.Images images() {
			return new PortraitModel.Images() {
				@Override
				public Sheet face(String name) throws IOException {
					return isPlainName(name) ? sheet(name, faceFolder.resolve(name + ".png"), "sprite/race/face/" + name + ".png") : null;
				}

				@Override
				public Sheet addon(String name) throws IOException {
					return isPlainName(name) ? sheet("addon/" + name, faceFolder.resolve("addon").resolve(name + ".png"), "sprite/race/face/addon/" + name + ".png") : null;
				}
			};
		}

		/**
		 * A race sprite as the game finds it, the mod's first, or null if neither has it.
		 *
		 * @param folder under sprite/, such as race or race/extra
		 */
		PngCodec.Image raceImage(String folder, String name) throws IOException {
			if (name == null || !isPlainName(name)) {
				return null;
			}
			Path own = version.resolve("assets").resolve("sprite").resolve(folder.replace('/', java.io.File.separatorChar)).resolve(name + ".png");
			Sheet sheet = sheet("sprite:" + folder + "/" + name, own, "sprite/" + folder + "/" + name + ".png");
			return sheet == null ? null : sheet.image();
		}

		/** An init file as the game reads it, the mod's layered over the game's, or null if neither has it. */
		Node layeredInit(String relative) throws IOException, ReadException {
			Path own = version.resolve("assets").resolve("init").resolve(relative.replace('/', java.io.File.separatorChar));
			boolean hasOwn = Files.isRegularFile(own, LinkOption.NOFOLLOW_LINKS);
			String vanilla = "init/" + relative;
			if (!vanillaHas(vanilla)) {
				return hasOwn ? parser.root(own) : null;
			}
			Path temp = tempCopy(vanilla);
			try {
				return hasOwn ? parser.root(List.of(own, temp)) : parser.root(temp);
			} finally {
				deleteQuietly(temp);
			}
		}

		GameParser parser() {
			return parser;
		}

		/** Forgets which images were used, before a drawing reads them again. */
		void clearUsedImages() {
			usedImages.clear();
		}

		private Sheet sheet(String key, Path own, String vanilla) throws IOException {
			if (own != null && Files.isRegularFile(own, LinkOption.NOFOLLOW_LINKS)) {
				String stamp = stamp(own);
				usedImages.put(own, stamp);
				Loaded loaded = sheets.get(key);
				if (loaded == null || !stamp.equals(loaded.stamp())) {
					loaded = new Loaded(stamp, new Sheet(PngCodec.decode(Files.readAllBytes(own))));
					sheets.put(key, loaded);
				}
				return loaded.sheet();
			}
			if (!vanillaHas(vanilla)) {
				return null;
			}
			Loaded loaded = sheets.get(key);
			if (loaded == null || !"vanilla".equals(loaded.stamp())) {
				loaded = new Loaded("vanilla", new Sheet(PngCodec.decode(vanillaBytes(vanilla))));
				sheets.put(key, loaded);
			}
			return loaded.sheet();
		}

		private static String stamp(Path file) throws IOException {
			return Files.size(file) + "/" + Files.getLastModifiedTime(file).toMillis();
		}

		private Path sheetFolder(boolean addon) {
			return addon ? faceFolder.resolve("addon") : faceFolder;
		}

		private static String sheetZip(boolean addon) {
			return addon ? "sprite/race/face/addon/" : "sprite/race/face/";
		}

		/**
		 * The sheets a portrait can name, from the mod and the game, by name. Names starting with _ are the game's own,
		 * such as _Overlays, and aren't offered.
		 */
		public List<SheetInfo> sheets(boolean addon) throws IOException {
			java.util.TreeMap<String, SheetInfo> found = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			String prefix = ZIP_ROOT + sheetZip(addon);
			Set<String> game = new HashSet<>();
			for (String entry : vanillaFiles) {
				if (entry.startsWith(prefix) && entry.toLowerCase(Locale.ROOT).endsWith(".png") && entry.indexOf('/', prefix.length()) < 0) {
					game.add(entry.substring(prefix.length(), entry.length() - 4));
				}
			}
			Set<String> own = new HashSet<>();
			Path folder = sheetFolder(addon);
			if (Files.isDirectory(folder)) {
				try (Stream<Path> files = Files.list(folder)) {
					files.filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
							.forEach(file -> own.add(nameOf(file)));
				}
			}
			Set<String> all = new java.util.TreeSet<>(game);
			all.addAll(own);
			for (String name : all) {
				if (name.startsWith("_") || !isPlainName(name)) {
					continue;
				}
				int[] size = own.contains(name) ? pngSize(Files.readAllBytes(sheetFolder(addon).resolve(name + ".png"))) : pngSize(vanillaHead(sheetZip(addon) + name + ".png"));
				found.put(name, info(name, addon, own.contains(name), game.contains(name), size));
			}
			return List.copyOf(found.values());
		}

		/** One sheet as the game would find it, mod first, or null if neither has it. */
		public SheetInfo sheet(boolean addon, String name) throws IOException {
			if (name == null || !isPlainName(name)) {
				return null;
			}
			Path own = sheetFolder(addon).resolve(name + ".png");
			boolean inMod = Files.isRegularFile(own, LinkOption.NOFOLLOW_LINKS);
			boolean inGame = vanillaHas(sheetZip(addon) + name + ".png");
			if (!inMod && !inGame) {
				return null;
			}
			return info(name, addon, inMod, inGame, inMod ? pngSize(Files.readAllBytes(own)) : pngSize(vanillaHead(sheetZip(addon) + name + ".png")));
		}

		private static SheetInfo info(String name, boolean addon, boolean inMod, boolean inGame, int[] size) {
			int width = size[0];
			int height = size[1];
			if (addon) {
				boolean ok = width == ADDON_WIDTH && height >= ADDON_CELL_H;
				return new SheetInfo(name, true, inMod, inGame, width, height, ok ? height / ADDON_CELL_H : 0,
						ok ? null : "It's " + width + " by " + height + ". An addon sheet must be 104 wide and at least 36 tall, or the game stops loading (RCrown.java:61).");
			}
			boolean ok = width == FACE_WIDTH && height >= FACE_CELL_H;
			return new SheetInfo(name, false, inMod, inGame, width, height, ok ? height / FACE_CELL_H * 4 : 0,
					ok ? null : "It's " + width + " by " + height + ". A face sheet must be 416 wide and at least 60 tall, or the game stops loading (RaceFrameMaker.java:128-129).");
		}

		/** The sheet's pixels as the game reads them, mod first, or null if neither has it. */
		public PngCodec.Image sheetImage(boolean addon, String name) throws IOException {
			if (name == null || !isPlainName(name)) {
				return null;
			}
			Path own = sheetFolder(addon).resolve(name + ".png");
			if (Files.isRegularFile(own, LinkOption.NOFOLLOW_LINKS)) {
				return PngCodec.decode(Files.readAllBytes(own));
			}
			String vanilla = sheetZip(addon) + name + ".png";
			return vanillaHas(vanilla) ? PngCodec.decode(vanillaBytes(vanilla)) : null;
		}

		/**
		 * Copies a sheet into the mod under a new name, so it can be repainted without changing it for every race. A copy
		 * under the game's own name would replace that sheet for all of them, because the game finds the mod's first.
		 * Nothing is overwritten. The new name is the race's, then the sheet's, and _2 and on if that's taken.
		 *
		 * @return the new sheet's name
		 */
		public String copySheet(boolean addon, String name, String race) throws Refused, IOException {
			SheetInfo info = sheet(addon, name);
			if (info == null) {
				throw new Refused("There's no sheet called " + name + " to copy.");
			}
			byte[] bytes = info.inMod() ? Files.readAllBytes(sheetFolder(addon).resolve(name + ".png")) : vanillaBytes(sheetZip(addon) + name + ".png");
			String base = SpriteCopy.cased(race.replaceAll("[^A-Za-z0-9_]+", "_")) + "_" + name;
			String candidate = base;
			int count = 2;
			while (Files.exists(sheetFolder(addon).resolve(candidate + ".png"), LinkOption.NOFOLLOW_LINKS) || vanillaHas(sheetZip(addon) + candidate + ".png")) {
				candidate = base + "_" + count++;
			}
			Path target = sheetFolder(addon).resolve(candidate + ".png");
			Files.createDirectories(target.getParent());
			if (!target.getParent().toRealPath().startsWith(mod.root().toRealPath())) {
				throw new Refused("The mod's sprite folder is a link to somewhere else, so nothing was written through it.");
			}
			Path temp = Files.createTempFile(target.getParent(), candidate, ".syxtmp");
			boolean done = false;
			try {
				Files.write(temp, bytes);
				SafeWrite.commit(List.of(new SafeWrite.Change(target, temp, null)));
				done = true;
				return candidate;
			} finally {
				if (!done) {
					deleteQuietly(temp);
				}
			}
		}

		/** Removes a sheet this session just copied, when what was to use it couldn't be saved. */
		public void removeCopiedSheet(boolean addon, String name) throws IOException {
			if (isPlainName(name)) {
				Files.deleteIfExists(sheetFolder(addon).resolve(name + ".png"));
			}
		}

		/** The first bytes of a game file, enough for a PNG's size. */
		private byte[] vanillaHead(String relative) throws IOException {
			try (ZipFile zip = new ZipFile(zipFile().toFile()); InputStream in = zip.getInputStream(zip.getEntry(ZIP_ROOT + relative))) {
				return in.readNBytes(32);
			}
		}

		/** Width and height from a PNG's header, or 0 by 0 if it isn't one. */
		private static int[] pngSize(byte[] head) {
			if (head.length < 24 || (head[0] & 0xFF) != 0x89 || head[1] != 'P' || head[2] != 'N' || head[3] != 'G') {
				return new int[] { 0, 0 };
			}
			return new int[] { int32(head, 16), int32(head, 20) };
		}

		private static int int32(byte[] bytes, int at) {
			return (bytes[at] & 0xFF) << 24 | (bytes[at + 1] & 0xFF) << 16 | (bytes[at + 2] & 0xFF) << 8 | bytes[at + 3] & 0xFF;
		}

		/** Whether any of the mod's images used so far has changed on disk. */
		public boolean imagesChanged() {
			for (Map.Entry<Path, String> used : usedImages.entrySet()) {
				try {
					if (!Files.isRegularFile(used.getKey()) || !stamp(used.getKey()).equals(used.getValue())) {
						return true;
					}
				} catch (IOException e) {
					return true;
				}
			}
			return false;
		}

		private static Preview red(String problem, Integer line, List<AbsentBlock> absent) {
			return new Preview(State.RED, problem, line, List.of(), List.of(), List.of(), 0, 0, null, absent, List.of(), null, List.of(), List.of());
		}

		private record Problem(Integer line, String message) {
		}

		private static Problem problem(ReadException e) {
			String data = e.dataError();
			if (data == null) {
				return new Problem(null, "The game's parser failed unexpectedly, which doesn't prove the text is wrong: " + e.getMessage());
			}
			Matcher keyed = KEYED_ERROR.matcher(data);
			if (keyed.matches()) {
				return new Problem(Integer.parseInt(keyed.group(1)) + 1, keyed.group(2) + ": " + keyed.group(3).trim());
			}
			SyntaxChecker.SyntaxProblem translated = SyntaxChecker.translate(data);
			return new Problem(translated.line(), translated.message());
		}

		/** Saves the portrait tags back into the race's file. Returns false if that would change nothing, so nothing was written. */
		public boolean save(String name, String revision, String text) throws Refused, IOException {
			return save(name, revision, text, PortraitSection.SPEC, section -> {
				Preview preview = preview(name, section, Person.standard());
				return preview.state() == State.RED ? preview.problem() : null;
			});
		}

		/** Why the game couldn't load a section's text, or null if it could. */
		interface Loads {
			String problem(String section) throws Refused, IOException;
		}

		/** Saves one section's tags back into the race's file. Returns false if that would change nothing, so nothing was written. */
		boolean save(String name, String revision, String text, SpriteFileSection.Spec spec, Loads loads) throws Refused, IOException {
			Race race = race(name);
			if (!race.editable()) {
				throw new Refused(race.note() != null ? race.note() : "This race's portrait can't be edited.");
			}
			Path target = spriteFilePath(race.spriteFile());
			byte[] bytes = Files.readAllBytes(target);
			if (!revision(bytes).equals(revision)) {
				throw new Refused(target.getFileName() + " has changed since it was opened. Open it again, so nothing is lost.");
			}
			String problem = loads.problem(text);
			if (problem != null) {
				throw new Refused("This can't be saved, because the game couldn't load it. " + problem);
			}
			String file;
			try {
				file = decode(bytes);
			} catch (CharacterCodingException e) {
				throw new Refused("This file isn't saved as UTF-8, so editing it here could damage it.");
			}
			if (!agrees(target, file)) {
				throw new Refused("This file is laid out in a way the toolkit can't safely edit.");
			}
			String updated;
			try {
				updated = SpriteFileSection.apply(spec, text, file);
			} catch (PortraitSection.Mismatch e) {
				throw new Refused(e.getMessage());
			}
			if (updated.equals(file)) {
				return false;
			}
			write(target, bytes, updated, text, spec);
			return true;
		}

		private void write(Path target, byte[] original, String updated, String section, SpriteFileSection.Spec spec) throws Refused, IOException {
			Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".syxtmp");
			boolean moved = false;
			try {
				Files.writeString(temp, updated);
				GameTree.Block expected = SpriteFileSection.merged(spec, tree(parser.root(target)), tree(sectionRoot(section)));
				if (!expected.equals(tree(parser.root(temp)))) {
					throw new Refused("The change was refused, because the game would read the file differently from what's shown here.");
				}
				if (!Arrays.equals(original, Files.readAllBytes(target))) {
					throw new Refused(target.getFileName() + " has changed since it was opened. Open it again, so nothing is lost.");
				}
				SafeWrite.commit(List.of(new SafeWrite.Change(target, temp, original)));
				moved = true;
			} catch (ReadException e) {
				throw new Refused("The change was refused, because the game couldn't read the result. " + e.getMessage());
			} finally {
				if (!moved) {
					deleteQuietly(temp);
				}
			}
		}

		private static GameTree.Block tree(Node node) throws ReadException {
			return GameTree.read(node);
		}

		/** Gives a race its own copy of its sprite file, and points the race at it. Nothing is overwritten. */
		public Copied copyToMod(String name) throws Refused, IOException {
			Race race = race(name);
			if (!race.canCopy()) {
				throw new Refused(race.editable() ? "This race already has its own file." : race.note());
			}
			Path raceFile = raceFilePath(name);
			byte[] raceBytes = Files.readAllBytes(raceFile);
			String raceText;
			try {
				raceText = decode(raceBytes);
			} catch (CharacterCodingException e) {
				throw new Refused("This race's file isn't saved as UTF-8, so editing it here could damage it.");
			}
			String newName = freeName(name);
			Path target = spriteFilePath(newName);
			Files.createDirectories(target.getParent());
			Path spriteTemp = Files.createTempFile(target.getParent(), newName, ".syxtmp");
			Path raceTemp = Files.createTempFile(raceFile.getParent(), name, ".syxtmp");
			boolean done = false;
			try {
				Files.write(spriteTemp, spriteCopy(race.spriteFile()));
				Files.writeString(raceTemp, withSpriteFile(raceText, newName));
				verifyCopy(race.spriteFile(), spriteTemp, raceFile, raceTemp, newName);
				if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
					throw new Refused(target.getFileName() + " has just appeared, so nothing was overwritten.");
				}
				if (!Arrays.equals(raceBytes, Files.readAllBytes(raceFile))) {
					throw new Refused(raceFile.getFileName() + " has changed since it was opened. Open it again, so nothing is lost.");
				}
				SafeWrite.commit(List.of(new SafeWrite.Change(target, spriteTemp, null), new SafeWrite.Change(raceFile, raceTemp, raceBytes)));
				done = true;
				return new Copied(newName, relative(target));
			} catch (ReadException e) {
				throw new Refused("The copy was refused, because the game couldn't read the result. " + e.getMessage());
			} finally {
				if (!done) {
					deleteQuietly(spriteTemp);
					deleteQuietly(raceTemp);
				}
			}
		}

		/** A vanilla file is copied exactly. A merged one comes from the game's merged result, which has no comments to keep. */
		private byte[] spriteCopy(String spriteFile) throws IOException, Refused {
			String vanilla = "init/race/sprite/" + spriteFile + ".txt";
			if (!Files.isRegularFile(spriteFilePath(spriteFile), LinkOption.NOFOLLOW_LINKS)) {
				return vanillaBytes(vanilla);
			}
			try {
				return GameTree.write(tree(layeredRoot(spriteFile))).getBytes(StandardCharsets.UTF_8);
			} catch (ReadException e) {
				throw new Refused("The game couldn't read this race's sprite file, so it wasn't copied. " + e.getMessage());
			}
		}

		private void verifyCopy(String source, Path spriteTemp, Path raceFile, Path raceTemp, String newName) throws IOException, Refused, ReadException {
			if (!tree(layeredRoot(source)).equals(tree(parser.root(spriteTemp)))) {
				throw new Refused("The copy was refused, because the game would read it differently from the original.");
			}
			GameTree.Block race = tree(parser.root(raceFile)).copy();
			race.put("SPRITE_FILE", new GameTree.Plain(newName));
			if (!race.equals(tree(parser.root(raceTemp)))) {
				throw new Refused("The copy was refused, because the game would read this race's file differently.");
			}
		}

		private static String withSpriteFile(String raceText, String newName) throws Refused {
			try {
				for (TagLocator.Located entry : TagLocator.scan(raceText)) {
					if (entry.key().equals("SPRITE_FILE")) {
						return raceText.substring(0, entry.start()) + newName + raceText.substring(entry.end());
					}
				}
			} catch (RuntimeException e) {
				// Reported below.
			}
			throw new Refused("This race's file is laid out in a way the toolkit can't safely edit.");
		}

		/** The race's name, or the next that's in neither the mod nor the game, because a shared name would be merged. */
		private String freeName(String race) {
			String candidate = race;
			int count = 2;
			while (Files.exists(spriteFilePath(candidate), LinkOption.NOFOLLOW_LINKS) || vanillaHas("init/race/sprite/" + candidate + ".txt")) {
				candidate = race + "_" + count++;
			}
			return candidate;
		}

		private static void deleteQuietly(Path file) {
			if (file != null) {
				try {
					Files.deleteIfExists(file);
				} catch (IOException e) {
					// A leftover temporary file does no harm.
				}
			}
		}
	}
}
