package syx.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import syx.core.TagSpec.Kind;

/**
 * Checks tag values against the schema. Errors stop a save. A warning is shown but doesn't: it's for
 * things the game would accept, because a false alarm is worse than a missed problem.
 */
public final class AssetChecker {

	/** MISSING blocks a save like ERROR, but a form that simply hasn't been filled in yet isn't shown as wrong. */
	public enum Severity {
		ERROR, MISSING, WARNING
	}

	/** One problem. The tag is null if it's about the asset as a whole. */
	public record Finding(String tag, Severity severity, String message) {
		public boolean blocksSaving() {
			return severity != Severity.WARNING;
		}
	}

	/**
	 * What's being checked when an existing asset is edited.
	 *
	 * @param kept tags in a form the editor can't show, which stay as they are unless they're given a value
	 * @param text whether the text file is being checked, because one that doesn't exist yet may not have been asked for
	 * @param sounds the sounds being edited, by their value's key. The others are in a file this asset doesn't own
	 */
	public record Scope(java.util.Set<String> kept, boolean text, java.util.Set<String> sounds, java.util.Set<String> companions) {
		public Scope(java.util.Set<String> kept, boolean text, java.util.Set<String> sounds) {
			this(kept, text, sounds, java.util.Set.of());
		}
	}

	private static final String WHOLE = "-?\\d+";
	private static final String DECIMAL = "-?(\\d+\\.?\\d*|\\.\\d+)";

	private AssetChecker() {
	}

	/**
	 * An override can't take away a top-level tag the game's own file has: the game merges the two files, and keeps
	 * its own value for every tag the mod's file leaves out (Json.java:29-42, JsonValue.java:323-339). A tag inside a
	 * block can be, because the mod's block replaces the game's whole. INFO's tags are inside INFO.
	 */
	private static void keptByGame(AssetSchema schema, GameAssetIndex index, Map<String, String> values, java.util.Set<String> kept, List<Finding> findings) {
		Map<String, String> game = new LinkedHashMap<>();
		AssetEditing.readAll(schema, index.starter(schema.overrides().dataFile()), index.starter(schema.overrides().textFile()), game, new java.util.HashSet<>());
		List<TagSpec> tags = new ArrayList<>(schema.dataTags());
		schema.textTags().stream().filter(tag -> !AssetEditing.isInfoTag(tag)).forEach(tags::add);
		String file = schema.overrides().dataFile().substring(schema.overrides().dataFile().lastIndexOf('/') + 1);
		for (TagSpec tag : tags) {
			if (filled(tag.key(), game) && !filled(tag.key(), values) && kept.stream().noneMatch(key -> key.equals(tag.key()) || key.startsWith(tag.key() + "."))) {
				findings.add(new Finding(tag.key(), Severity.ERROR, "The game's own " + file + " has " + tag.key() + ", and keeps that value whenever this file leaves it out, so it can't be removed. Change its value instead."));
			}
		}
	}

	private static boolean filled(String key, Map<String, String> values) {
		return values.entrySet().stream().anyMatch(entry -> (entry.getKey().equals(key) || entry.getKey().startsWith(key + ".")) && !entry.getValue().isBlank());
	}

	/** Keeps only the values that belong to a tag or its entries, for working out what's present. */
	static Map<String, String> entriesOf(TagSpec tag, Map<String, String> values) {
		Map<String, String> entries = new LinkedHashMap<>();
		String prefix = tag.key() + ".";
		values.forEach((key, value) -> {
			if (key.startsWith(prefix)) {
				entries.put(key.substring(prefix.length()), value);
			}
		});
		return entries;
	}

	/** Checks every tag. Values use the form described on {@link AssetSchema}. */
	public static List<Finding> check(AssetSchema schema, GameAssetIndex index, Map<String, String> values) {
		return check(schema, index, values, null);
	}

	/** Checks an asset being edited, or a new one if the scope is null. */
	public static List<Finding> check(AssetSchema schema, GameAssetIndex index, Map<String, String> values, Scope scope) {
		return check(schema, index, values, scope, scope == null ? java.util.Set.of() : scope.kept());
	}

	/**
	 * Checks a new asset. One started from a vanilla look is a copy of that look's files, so what they hold
	 * in a form the editor can't show is {@code kept}, as it is when an asset is opened.
	 */
	public static List<Finding> checkNew(AssetSchema schema, GameAssetIndex index, Map<String, String> values, java.util.Set<String> kept) {
		return check(schema, index, values, null, kept);
	}

	private static List<Finding> check(AssetSchema schema, GameAssetIndex index, Map<String, String> values, Scope scope, java.util.Set<String> keptValues) {
		List<Finding> findings = new ArrayList<>();
		for (TagSpec tag : schema.dataTags()) {
			checkTag(tag, index, values, findings);
		}
		if (scope == null || scope.text()) {
			for (TagSpec tag : schema.textTags()) {
				checkTag(tag, index, values, findings);
			}
		}
		for (AssetSchema.Companion companion : schema.companions()) {
			boolean active = scope == null ? companion.required() || hasCompanionValues(companion, values) : scope.companions().contains(companion.id());
			if (active) {
				for (TagSpec tag : companion.tags()) {
					checkTag(tag, index, values, findings);
				}
			} else if (nameIsOnThisForm(companion, scope)) {
				missingCompanion(companion, index, values, findings);
			}
		}
		schema.crossRule().check(values, index, keptValues, findings);
		if (schema.overrides() != null) {
			keptByGame(schema, index, values, keptValues, findings);
		}
		for (SoundEntries.Slot slot : schema.sounds()) {
			if (scope != null && !scope.sounds().contains(slot.valueKey())) {
				continue;
			}
			String sound = values.get(slot.valueKey());
			if (sound == null || sound.isBlank()) {
				findings.add(new Finding(slot.valueKey(), Severity.MISSING, "Choose a sound. " + SoundEntries.NO_SOUND + " means none."));
			} else if (index.sounds() != null && !index.sounds().options(slot).contains(sound)) {
				// The game plays nothing if it can't find a sound, and only says so in developer mode (PathParser.java:95-103).
				findings.add(new Finding(slot.valueKey(), Severity.WARNING, "The game and this mod don't use the sound " + sound + ". If it can't be found, the game plays nothing."));
			}
		}
		for (String block : schema.spawnBlocks()) {
			boolean anyAbove = false;
			for (String value : entriesOf(schema.tag(block), values).values()) {
				anyAbove |= isPositive(value);
			}
			if (!anyAbove) {
				findings.add(new Finding(null, Severity.WARNING, block + " has no value above 0, so this will never appear in the game (Generator.java:57-58)."));
			}
		}
		for (String kept : keptValues) {
			if (values.keySet().stream().noneMatch(key -> isAtOrUnder(key, kept))) {
				findings.removeIf(finding -> finding.tag() != null && isAtOrUnder(finding.tag(), kept));
			}
		}
		return findings;
	}

	private static boolean isAtOrUnder(String key, String tag) {
		return key.equals(tag) || key.startsWith(tag + ".");
	}

	private static boolean hasCompanionValues(AssetSchema.Companion companion, Map<String, String> values) {
		return values.keySet().stream().anyMatch(key -> key.startsWith(companion.prefix()));
	}

	/** A name inside a companion this save isn't rewriting isn't on the form, and the game still reads that file. */
	private static boolean nameIsOnThisForm(AssetSchema.Companion companion, Scope scope) {
		if (scope == null || !companion.nameTag().startsWith("file.")) {
			return true;
		}
		String rest = companion.nameTag().substring("file.".length());
		int dot = rest.indexOf('.');
		String parent = dot < 0 ? rest : rest.substring(0, dot);
		return scope.companions().contains(parent);
	}

	/** A named file the game opens, and which this save is not about to create. */
	private static void missingCompanion(AssetSchema.Companion companion, GameAssetIndex index, Map<String, String> values, List<Finding> findings) {
		String stem = values.get(companion.nameTag());
		if (stem == null || stem.isBlank()) {
			if (companion.required()) {
				findings.add(new Finding(companion.nameTag(), Severity.MISSING, "The game opens " + companion.label() + " from this, and stops loading without it."));
			}
			return;
		}
		if (!index.fileExists(companion.relative(stem))) {
			findings.add(new Finding(companion.nameTag(), Severity.ERROR, "There's no " + companion.label() + " called " + stem + ", and the game stops loading without it."));
		}
	}

	private static void checkKeyedBlocks(TagSpec tag, GameAssetIndex index, Map<String, String> values, List<Finding> findings) {
		Map<String, Map<String, String>> rows = rowsOf(tag, values);
		if (rows.isEmpty() && tag.required()) {
			findings.add(new Finding(tag.key(), Severity.MISSING, "The game needs at least one entry here."));
		}
		int most = tag.shape().maxEntries();
		if (most > 0 && rows.size() > most) {
			error(findings, tag.key(), "The game accepts at most " + most + " entries here.");
		}
		List<String> options = tag.reference() == null ? List.of() : index.options(tag.reference());
		rows.keySet().forEach(row -> {
			if (tag.reference() != null && !"*".equals(row) && options.stream().noneMatch(option -> GameAssetIndex.sameKey(option, row))) {
				error(findings, tag.key(), "The game doesn't have an entry called " + row + ", and stops loading for this.");
			}
			for (TagSpec child : tag.shape().children()) {
				checkTag(child.withKey(tag.key() + "." + row + "." + child.key()), index, values, findings);
			}
		});
	}

	/**
	 * Checks only the data file's tags, for a file that already exists. Whether it will ever appear
	 * isn't asked here, because a vanilla asset may be placed some other way.
	 */
	static List<Finding> checkData(AssetSchema schema, GameAssetIndex index, Map<String, String> values) {
		List<Finding> findings = new ArrayList<>();
		for (TagSpec tag : schema.dataTags()) {
			checkTag(tag, index, values, findings);
		}
		return findings;
	}

	private static boolean isPositive(String value) {
		return value.matches(DECIMAL) && Double.parseDouble(value) > 0;
	}

	private static void checkTag(TagSpec tag, GameAssetIndex index, Map<String, String> values, List<Finding> findings) {
		if (tag.linked()) {
			checkLinked(tag, values, findings);
			return;
		}
		switch (tag.kind()) {
		case DECIMAL_MAP -> checkMap(tag, index, values, findings);
		case KEYED_LISTS -> checkKeyedLists(tag, index, values, findings);
		case KEYED_TEXTS -> {
			// Each entry is lines of text. A stat the game doesn't know is its own concern, and the names aren't a folder.
		}
		case TEXT_BLOCK -> checkBlock(tag, values, findings);
		case KEY_LIST -> checkList(tag, index, values, findings);
		case BLOCK -> {
			if (tag.required() || !entriesOf(tag, values).isEmpty()) {
				for (TagSpec child : tag.nested()) {
					checkTag(child, index, values, findings);
				}
			}
		}
		case KEYED_ROWS -> checkKeyedRows(tag, index, values, findings);
		case KEYED_BLOCKS -> checkKeyedBlocks(tag, index, values, findings);
		case BLOCK_LIST -> checkBlockList(tag, index, values, findings);
		case ICON -> {
			if (!tag.nested().isEmpty() && !entriesOf(tag, values).isEmpty()) {
				checkIconBlock(tag, index, values, findings);
			} else {
				checkSingle(tag, index, values, findings);
			}
		}
		default -> checkSingle(tag, index, values, findings);
		}
	}

	private static void checkSingle(TagSpec tag, GameAssetIndex index, Map<String, String> values, List<Finding> findings) {
		String value = values.get(tag.key());
		if (value == null) {
			// A blank is written for it, which the game accepts (AssetCreator).
			if (tag.required() && !tag.blankAllowed()) {
				findings.add(new Finding(tag.key(), Severity.MISSING, "The game needs this, and stops loading without it."));
			}
		} else {
			checkValue(tag, index, value, findings);
		}
	}

	private static void checkIconBlock(TagSpec tag, GameAssetIndex index, Map<String, String> values, List<Finding> findings) {
		String key = tag.key();
		if (values.containsKey(key)) {
			error(findings, key, "Give either one icon in " + key + ", or a block in " + key + ".BG and the tags beside it, not both.");
		}
		for (TagSpec child : tag.nested()) {
			checkTag(child, index, values, findings);
		}
		if (values.containsKey(key + ".COLOR") && values.containsKey(key + ".FG")) {
			findings.add(new Finding(key + ".COLOR", Severity.WARNING, "The game only uses COLOR on a block with no FG, so this does nothing (Icons.java:104-106)."));
		}
	}

	private static void checkValue(TagSpec tag, GameAssetIndex index, String value, List<Finding> findings) {
		String key = tag.key();
		switch (tag.kind()) {
		case BOOL -> {
			if (!value.equals("true") && !value.equals("false")) {
				error(findings, key, "This must be true or false.");
			}
		}
		case INT -> checkInt(tag, value, findings);
		case DECIMAL -> {
			if (!value.matches(DECIMAL)) {
				error(findings, key, "This must be a number, such as 0.5.");
			} else {
				checkRange(tag, Double.parseDouble(value), findings);
			}
		}
		case COLOR -> checkColor(key, value, findings);
		case COLORS -> {
			if (value.startsWith("{")) {
				checkColor(key, value, findings);
				return;
			}
			int before = findings.size();
			for (String color : value.split(",", -1)) {
				checkColor(key, color.trim(), findings);
				if (findings.size() > before) {
					return;
				}
			}
		}
		case ICON -> checkIcon(key, value, index, findings);
		case SPRITE -> checkSprite(tag, value, index, findings);
		case TEXT_LINE, TEXT_LINES -> {
			String problem = TextRules.problemWith(value, tag.kind() == Kind.TEXT_LINE);
			if (problem != null) {
				error(findings, key, problem);
			} else if (key.equals("NAME") && value.isBlank()) {
				// The game accepts a blank name. It would just be unusable, so this is our own rule.
				findings.add(new Finding(key, Severity.MISSING, "This needs a name."));
			}
		}
		case KEY -> {
			if (index.options(tag.reference()).stream().noneMatch(option -> GameAssetIndex.sameKey(option, value))) {
				error(findings, key, "The game doesn't have an entry called " + value + ", and stops loading for this (RMAP.java:34-46).");
			}
		}
		case TEXT_ROWS -> checkTextRows(tag, value, findings);
		case FRAMES -> checkFrames(tag, value, index, findings);
		case DECIMALS -> {
			// An empty list is valid, and a rule that needs a length says so (Json.java:257-269).
			if (value.isEmpty()) {
				return;
			}
			for (String item : value.split(",", -1)) {
				if (!item.trim().matches(DECIMAL)) {
					error(findings, key, "This needs numbers separated by commas, such as 1,1,0.");
					return;
				}
			}
		}
		case PLAIN -> {
			if (value.isBlank() || value.contains(",") || value.indexOf('\n') >= 0) {
				error(findings, key, "This needs a value, without commas or line breaks.");
			}
		}
		default -> throw new IllegalStateException("Not a single value: " + tag.kind());
		}
	}

	private static void checkTextRows(TagSpec tag, String value, List<Finding> findings) {
		String[] rows = value.split("\n", -1);
		int most = tag.shape().maxEntries();
		if (most > 0 && rows.length > most) {
			error(findings, tag.key(), "This has " + rows.length + " rows, and the game accepts at most " + most + ".");
		}
		// Equal lengths and no empty row are the minimap pattern. Other text rows only have a count limit.
		boolean pattern = tag.key().endsWith("MINI_COLOR_PATTERN");
		for (String row : rows) {
			String problem = TextRules.problemWith(row, true);
			if (problem != null) {
				error(findings, tag.key(), problem);
				return;
			}
			if (pattern && row.isEmpty()) {
				error(findings, tag.key(), "A row can't be empty, or the game crashes drawing the minimap (FurnisherMinimapColor.java:58).");
				return;
			}
			if (pattern && row.length() != rows[0].length()) {
				error(findings, tag.key(), "Every row must be the same length, or the game stops loading.");
				return;
			}
		}
	}

	/**
	 * Sheets the game's code registers under a name, with no image, by folder. Each holds one row. The check
	 * at GameSheets.java:158 finds these first, so a frame naming one is fine. Crops also register sheets
	 * (GrowableSprite.java:407), under a key built at run time, so those still warn. The flower terrain names its
	 * own, _FLOWER, one sheet in each of four kinds (TFlower.java:105, GrowableSprite.java:391-396,404-407).
	 */
	private static final Map<String, java.util.Set<String>> CODE_SHEETS = Map.of(
			"sprite/game/1x1/", java.util.Set.of("_TREE", "_FLOWER", "SKULL_MOUND_1X1", "SEVERED_HEAD_1X1"), // TForest.java:607,629, SpriteSkulls.java:54, SpriteHead.java:33
			"sprite/game/2x2/", java.util.Set.of("_TREE", "_FLOWER", "SKULL_MOUND_2X2"), // TForest.java:611,629, SpriteSkulls.java:82
			"sprite/game/3x3/", java.util.Set.of("_TREE", "_FLOWER", "SKULL_MOUND_3X3"), // TForest.java:614,629, SpriteSkulls.java:111
			"sprite/game/combo/", java.util.Set.of("_FLOWER"));

	/**
	 * The same steps as GameSheets.java:140-196. A malformed frame stops the game. A file or row it can't
	 * find only warns, and the game's code can add sheets of its own, so those are warnings.
	 */
	private static void checkFrames(TagSpec tag, String value, GameAssetIndex index, List<Finding> findings) {
		// Vanilla leaves some levels empty on purpose, and some whole sprites (REFINER_BAKERY's MAIN_MACHINE_COMBO_TOP), so this isn't a problem.
		if (value.isBlank()) {
			return;
		}
		String folder = tag.sprite().folder();
		for (String frame : value.split("\n", -1)) {
			String entry = frame.trim();
			if (entry.equals("-")) {
				continue;
			}
			String[] parts = entry.split(":");
			if (parts.length != 2 || !parts[1].trim().matches("-?\\d{1,9}")) {
				error(findings, tag.key(), "\"" + entry + "\" isn't a frame. Each is written FILE: ROW, or - to draw nothing, and the game stops loading otherwise (GameSheets.java:150,186).");
				return;
			}
			String file = parts[0].trim();
			GameAssetIndex.SheetRows sheet = index.sheetRows(folder, file);
			int row = Integer.parseInt(parts[1].trim());
			if (!sheet.exists() && CODE_SHEETS.getOrDefault(folder, java.util.Set.of()).contains(file)) {
				if (row != 0) {
					findings.add(new Finding(tag.key(), Severity.WARNING, file + " is a sheet the game's own code makes, with 1 row, numbered from 0, so the game warns and skips row " + row + "."));
				}
			} else if (!sheet.exists()) {
				findings.add(new Finding(tag.key(), Severity.WARNING, "There's no image called " + file + " in " + folder + " in the game or this mod, so the game warns and skips this frame."));
			} else if (!sheet.validSize()) {
				error(findings, tag.key(), file + " doesn't have a size the game accepts for this kind of sheet, and it stops loading (SheetType.java:87,159-164,250,295).");
				return;
			} else if (row < 0 || row >= sheet.rows()) {
				String unit = folder.startsWith("sprite/race/face/") && !folder.contains("addon") ? "frame" : "row";
				findings.add(new Finding(tag.key(), Severity.WARNING, file + " has " + sheet.rows() + " " + unit + (sheet.rows() == 1 ? "" : "s") + ", numbered from 0, so the game warns and skips " + unit + " " + row + "."));
			}
		}
	}

	/** The entries of keyed rows, in the order written. Each maps a column, or "" for a plain value, to its value. */
	static Map<String, Map<String, String>> rowsOf(TagSpec tag, Map<String, String> values) {
		Map<String, Map<String, String>> rows = new LinkedHashMap<>();
		entriesOf(tag, values).forEach((entry, value) -> {
			int dot = entry.indexOf('.');
			String row = dot < 0 ? entry : entry.substring(0, dot);
			rows.computeIfAbsent(row, r -> new LinkedHashMap<>()).put(dot < 0 ? "" : entry.substring(dot + 1), value);
		});
		return rows;
	}

	private static void checkKeyedRows(TagSpec tag, GameAssetIndex index, Map<String, String> values, List<Finding> findings) {
		Map<String, Map<String, String>> rows = rowsOf(tag, values);
		if (rows.isEmpty() && tag.required()) {
			findings.add(new Finding(tag.key(), Severity.MISSING, "The game needs at least one entry here."));
		}
		List<String> options = tag.reference() == null ? List.of() : index.options(tag.reference());
		rows.forEach((row, columns) -> {
			if (tag.reference() != null && options.stream().noneMatch(option -> GameAssetIndex.sameKey(option, row))) {
				// Environments are only looked up with a warning (Furnisher.java:119). Resources stop the game (Industry.java:117,126).
				if (tag.reference() == TagSpec.Reference.ENVIRONMENTS) {
					findings.add(new Finding(tag.key(), Severity.WARNING, "The game has no entry called " + row + ", so it ignores this one."));
				} else {
					error(findings, tag.key(), "The game doesn't have an entry called " + row + ", and stops loading for this (RMAP.java:69).");
				}
			}
			for (TagSpec.Column column : tag.shape().columns()) {
				String value = columns.get(column.key() == null ? "" : column.key());
				if (value == null || value.isEmpty()) {
					if (column.required()) {
						findings.add(new Finding(tag.key(), Severity.MISSING, row + " needs a value for " + column.label() + "."));
					}
				} else if (!value.matches(DECIMAL)) {
					error(findings, tag.key(), row + ": " + column.label() + " must be a number, such as 0.5.");
				} else if (Double.parseDouble(value) < column.min() || Double.parseDouble(value) > column.max()) {
					error(findings, tag.key(), row + ": " + column.label() + " must be from " + plain(column.min()) + " to " + plain(column.max()) + ".");
				}
			}
		});
	}

	private static void checkBlockList(TagSpec tag, GameAssetIndex index, Map<String, String> values, List<Finding> findings) {
		int count = tag.shape().fixedCount();
		if (count == 0) {
			for (String entry : entriesOf(tag, values).keySet()) {
				count = Math.max(count, Integer.parseInt(entry.substring(0, entry.indexOf('.'))) + 1);
			}
		} else {
			count = tag.shape().namedSlots();
		}
		// A fixed list's blocks are written whatever's in them, even if the game names them all itself.
		if (count == 0 && tag.required() && tag.shape().fixedCount() == 0) {
			findings.add(new Finding(tag.key(), Severity.MISSING, "The game needs at least one here, and stops loading without it."));
		}
		for (int i = 0; i < count; i++) {
			for (TagSpec child : tag.nestedAt(i)) {
				checkTag(child, index, values, findings);
			}
		}
	}

	private static void checkInt(TagSpec tag, String value, List<Finding> findings) {
		if (!value.matches(WHOLE)) {
			error(findings, tag.key(), "This must be a whole number.");
			return;
		}
		double number;
		try {
			number = Integer.parseInt(value);
		} catch (NumberFormatException e) {
			error(findings, tag.key(), "That number is too large for the game to read.");
			return;
		}
		checkRange(tag, number, findings);
	}

	private static void checkRange(TagSpec tag, double number, List<Finding> findings) {
		if (tag.min() != null && number < tag.min()) {
			error(findings, tag.key(), "The lowest value the game accepts is " + plain(tag.min()) + ".");
		} else if (tag.max() != null && number > tag.max()) {
			if (tag.maxEnforced()) {
				error(findings, tag.key(), "The highest value the game accepts is " + plain(tag.max()) + ".");
			} else {
				findings.add(new Finding(tag.key(), Severity.WARNING, "The game's stated maximum is " + plain(tag.max()) + ", but it doesn't enforce it, so this may behave oddly."));
			}
		}
	}

	private static String plain(double value) {
		return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
	}

	private static void checkColor(String key, String value, List<Finding> findings) {
		if (value.startsWith("{")) {
			java.util.regex.Matcher block = java.util.regex.Pattern.compile("\\{R: (\\d{1,3}), G: (\\d{1,3}), B: (\\d{1,3}),\\}").matcher(value);
			boolean good = block.matches();
			for (int i = 1; good && i <= 3; i++) {
				good = Integer.parseInt(block.group(i)) <= 511;
			}
			if (!good) {
				error(findings, key, "This needs red, green and blue, each from 0 to 511 (ColorImp.java:109-110).");
			}
			return;
		}
		String[] parts = value.split("_", -1);
		boolean good = parts.length == 3;
		for (int i = 0; good && i < 3; i++) {
			good = parts[i].matches("\\d{1,3}") && Integer.parseInt(parts[i]) <= 255;
		}
		if (!good) {
			error(findings, key, "This needs red, green and blue, each from 0 to 255 (ColorImp.java:120).");
		}
	}

	private static void checkIcon(String key, String value, GameAssetIndex index, List<Finding> findings) {
		int split = value.lastIndexOf("->");
		if (value.contains("{") || value.contains("[")) {
			error(findings, key, "This holds one icon path. A block is given in " + key + ".BG, " + key + ".FG and the tags beside them, and a list of blocks can't be written here.");
			return;
		}
		if (split < 0 || !value.substring(split + 2).matches("\\d+")) {
			error(findings, key, "Choose a sheet and a position on it.");
			return;
		}
		String sheet = value.substring(0, split);
		if (!index.options(TagSpec.Reference.ICONS).contains(sheet)) {
			error(findings, key, "There's no icon sheet called " + sheet + " in the game or this mod.");
			return;
		}
		int count = index.iconCount(sheet);
		if (count < 0) {
			error(findings, key, "That sheet doesn't have the size the game requires (IconMaker.java).");
			return;
		}
		int position;
		try {
			position = Integer.parseInt(value.substring(split + 2));
		} catch (NumberFormatException e) {
			position = Integer.MAX_VALUE;
		}
		if (position >= count) {
			findings.add(new Finding(key, Severity.WARNING, "That sheet looks like it has " + count + " icons, numbered from 0, so this may show a placeholder."));
		}
	}

	private static void checkSprite(TagSpec tag, String value, GameAssetIndex index, List<Finding> findings) {
		TagSpec.Sprite rule = tag.sprite();
		if (rule == null) {
			error(findings, tag.key(), "The size this image needs couldn't be read from the game.");
			return;
		}
		GameAssetIndex.ImageSize size = index.spriteSize(rule.folder(), value);
		if (size == null) {
			String message = "There's no image called " + value + " in " + rule.folder() + " in the game or this mod.";
			if (rule.missingWarns()) {
				findings.add(new Finding(tag.key(), Severity.WARNING, message + " The game logs that and skips this one (RCrown.java)."));
			} else {
				error(findings, tag.key(), message);
			}
		} else if (size.width() != rule.width() || (rule.exactHeight() ? size.height() != rule.minHeight() : size.height() < rule.minHeight())) {
			String height = rule.exactHeight() ? "exactly " + rule.minHeight() : "at least " + rule.minHeight();
			error(findings, tag.key(), "That image is " + size.width() + " by " + size.height() + ". The game needs exactly " + rule.width()
					+ " wide and " + height + " tall, or it stops loading (ComposerUtil.java:43-48).");
		}
	}

	private static void checkList(TagSpec tag, GameAssetIndex index, Map<String, String> values, List<Finding> findings) {
		String value = values.get(tag.key());
		if (value == null || value.isEmpty()) {
			return;
		}
		List<String> options = tag.reference() == null ? List.of() : index.options(tag.reference());
		int most = tag.shape().maxEntries();
		if (most > 0 && value.split(",", -1).length > most) {
			error(findings, tag.key(), "The game accepts at most " + most + " entries here, and stops loading with more.");
		}
		if (tag.reference() == null) {
			return;
		}
		for (String item : value.split(",", -1)) {
			String name = item.trim();
			if ("*".equals(name)) {
				continue;
			}
			if (!listed(options, name)) {
				// These lists are read with readManyWarn, which skips a missing name, and only reports it with Debug on (RMAP.java:181-190):
				// a room's floors (Furnisher.java:78) and a race's food and drink (RacePreferrence.java). A race with none left is its own rule.
				if (WARNED_LISTS.contains(tag.reference()) && !name.isEmpty()) {
					findings.add(new Finding(tag.key(), Severity.WARNING, "The game has no " + WARNED_LISTS_NAMES.get(tag.reference()) + " called " + name + ", so it leaves that one out."));
				} else {
					error(findings, tag.key(), "The game doesn't have an entry called " + (name.isEmpty() ? "(blank)" : name) + ", and stops loading for this (RMAP.java:147).");
				}
			}
		}
	}

	/** References whose lists the game reads with readManyWarn, and what each names. */
	private static final java.util.Set<TagSpec.Reference> WARNED_LISTS = java.util.EnumSet.of(TagSpec.Reference.FLOORS, TagSpec.Reference.EDIBLES, TagSpec.Reference.DRINKABLES);
	private static final Map<TagSpec.Reference, String> WARNED_LISTS_NAMES = Map.of(
			TagSpec.Reference.FLOORS, "floor", TagSpec.Reference.EDIBLES, "food", TagSpec.Reference.DRINKABLES, "drink");

	/** A name the list has, a wildcard, or the same name with a leading _ (KeyMap.java:42-55). */
	static boolean listed(List<String> options, String name) {
		if (options.contains(name) || matchesWildcard(name, options)) {
			return true;
		}
		String plain = name.startsWith("_") ? name.substring(1) : name;
		return !plain.equals(name) && (options.contains(plain) || matchesWildcard(plain, options));
	}

	private static void checkKeyedLists(TagSpec tag, GameAssetIndex index, Map<String, String> values, List<Finding> findings) {
		List<String> options = tag.reference() == null ? List.of() : index.options(tag.reference());
		entriesOf(tag, values).forEach((entry, value) -> {
			if (tag.reference() != null && !"*".equals(entry) && !listed(options, entry)) {
				findings.add(new Finding(tag.key(), Severity.WARNING, "The game has no " + tag.key() + " called " + entry + ", so it ignores this entry."));
			}
		});
	}

	/** Wildcards match by what a name starts with (RMAP.java:130-133). */
	private static boolean matchesWildcard(String name, List<String> options) {
		int star = name.indexOf('*');
		return star >= 0 && options.stream().anyMatch(o -> o.startsWith(name.substring(0, star)));
	}

	private static void checkLinked(TagSpec tag, Map<String, String> values, List<Finding> findings) {
		String list = values.getOrDefault(tag.linkedTo(), "");
		String amounts = values.getOrDefault(tag.key(), "");
		int wanted = list.isEmpty() ? 0 : list.split(",", -1).length;
		String[] items = amounts.isEmpty() ? new String[0] : amounts.split(",", -1);
		if (items.length != wanted) {
			error(findings, tag.linkedTo(), "Each entry needs an amount: there are " + wanted + " entries and " + items.length + " amounts (AnimalSpecies.java:80).");
			return;
		}
		for (String item : items) {
			if (!item.trim().matches(DECIMAL)) {
				error(findings, tag.linkedTo(), "Each amount must be a number, such as 0.5.");
				return;
			}
		}
	}

	private static void checkMap(TagSpec tag, GameAssetIndex index, Map<String, String> values, List<Finding> findings) {
		List<String> options = tag.reference() == null ? List.of() : index.options(tag.reference());
		entriesOf(tag, values).forEach((entry, value) -> {
			if (!value.matches(DECIMAL)) {
				error(findings, tag.key(), entry + " must be a number, such as 0.5.");
				return;
			}
			double number = Double.parseDouble(value);
			if ((tag.min() != null && number < tag.min()) || (tag.max() != null && number > tag.max())) {
				error(findings, tag.key(), entry + " must be from " + plain(tag.min()) + " to " + plain(tag.max()) + ".");
			}
			if (tag.reference() != null && !"*".equals(entry) && !listed(options, entry)) {
				// The game only logs this in developer mode and skips the entry (RMAP.java:315-325).
				findings.add(new Finding(tag.key(), Severity.WARNING, "The game has no " + tag.key() + " called " + entry + ", so it ignores this entry."));
			}
		});
	}

	private static void checkBlock(TagSpec tag, Map<String, String> values, List<Finding> findings) {
		Map<String, String> entries = entriesOf(tag, values);
		if (entries.isEmpty()) {
			return;
		}
		for (TagSpec.SubKey sub : tag.subKeys()) {
			String value = entries.get(sub.key());
			if (value == null) {
				if (sub.required()) {
					error(findings, tag.key(), sub.key() + " is needed if this block is present.");
				}
			} else {
				String problem = sub.text() ? TextRules.problemWith(value, !sub.multiLine())
						: value.isBlank() || value.contains(",") || value.indexOf('\n') >= 0 ? "It needs a value, without commas or line breaks." : null;
				if (problem != null) {
					error(findings, tag.key(), sub.key() + ": " + problem);
				}
			}
		}
	}

	private static void error(List<Finding> findings, String key, String message) {
		findings.add(new Finding(key, Severity.ERROR, message));
	}
}
