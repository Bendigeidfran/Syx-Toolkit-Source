package syx.core;

import java.util.List;

/**
 * Everything known about one tag, and where that knowledge came from.
 *
 * @param gameDefault what the game uses when the tag is absent, or null if there is no default
 * @param maxEnforced false for Json.i, whose maximum the game states but never checks (Json.java:291)
 * @param subKeys the fixed entries of a {@link Kind#TEXT_BLOCK}
 * @param evidence the game code this rests on, as Class.java:line
 * @param sprite the image rule of a {@link Kind#SPRITE}, or null
 * @param shape what a nested tag holds
 * @param blankAllowed for required text the game reads as it is, so a blank works: left empty, it's written as "" rather than
 *            reported missing. The form can't hold a blank itself, so this is how one is kept.
 */
public record TagSpec(
		String key,
		Kind kind,
		boolean required,
		String gameDefault,
		Double min,
		Double max,
		boolean maxEnforced,
		Reference reference,
		String linkedTo,
		List<SubKey> subKeys,
		String evidence,
		Confidence confidence,
		Sprite sprite,
		Shape shape,
		boolean blankAllowed) {

	public TagSpec(String key, Kind kind, boolean required, String gameDefault, Double min, Double max, boolean maxEnforced, Reference reference, String linkedTo,
			List<SubKey> subKeys, String evidence, Confidence confidence, Sprite sprite, Shape shape) {
		this(key, kind, required, gameDefault, min, max, maxEnforced, reference, linkedTo, subKeys, evidence, confidence, sprite, shape, false);
	}

	/** The same required text, which may be blank. */
	public TagSpec withBlankAllowed() {
		return new TagSpec(key, kind, required, gameDefault, min, max, maxEnforced, reference, linkedTo, subKeys, evidence, confidence, sprite, shape, true);
	}

	public enum Kind {
		BOOL, INT, DECIMAL, PLAIN, TEXT_LINE, TEXT_LINES, COLOR, ICON, SPRITE, KEY_LIST, LINKED_DECIMALS, DECIMAL_MAP, TEXT_BLOCK,
		/** One colour, or a list of them separated by commas (ColorImp.java:87-98). */
		COLORS,
		/** One name from a list of what exists. */
		KEY,
		/** A block with fixed tags inside it. */
		BLOCK,
		/** A block keyed by names from a list, each holding numbers. */
		KEYED_ROWS,
		/** A block for each name, holding the same tags. The name is not one of those tags. */
		KEYED_BLOCKS,
		/** A block for each name, holding a list of names. The name is not one of those names. */
		KEYED_LISTS,
		/** A block for each name, holding lines of text. The name is not one of those lines. */
		KEYED_TEXTS,
		/** A list of blocks with the same tags. */
		BLOCK_LIST,
		/** A list of text, one row to a line. */
		TEXT_ROWS,
		/** A list of numbers. */
		DECIMALS,
		/** A sprite's frames, each FILE: ROW or -, one to a line. {@link Sprite#folder} says where the files are. */
		FRAMES
	}

	/** Where a tag's valid values are listed. */
	public enum Reference {
		RESOURCES, DAMAGES, CLIMATES, TERRAINS, ICONS, GROWABLES, FLOORS, ENVIRONMENTS, MINABLES,
		EDIBLES, DRINKABLES, SUPPLIES, TRAITS, RACES, ROOMS, POOL_ROOMS, WORLD_BUILDINGS, ANIMALS, RELIGIONS, NEEDS,
		/** Floors whose file has a ROAD block. */
		ROADS,
		/** What a race can prefer to be among: mountain, outdoors, mud and each structure. */
		STRUCTURES,
		/** Fixed in the game's code. */
		CRIMES,
		/** Fixed in the game's code. */
		PUNISHMENTS
	}

	/** Only code-verified facts can stop a save. */
	public enum Confidence {
		CODE_VERIFIED, INFERRED
	}

	/** One fixed entry inside a block. */
	public record SubKey(String key, boolean text, boolean multiLine, boolean required) {
	}

	/**
	 * What an image tag can name and the size the game requires.
	 *
	 * @param folder the folder under assets, ending in a slash
	 * @param width the exact width needed
	 * @param minHeight the smallest height accepted, or the only height when {@code exactHeight} is set
	 * @param exactHeight whether a taller sheet is rejected. A sheet constructor treats the height as a minimum (ComposerUtil.java:45)
	 * @param missingWarns whether a missing image is only logged, as a crown sheet is, rather than stopping loading
	 */
	public record Sprite(String folder, int width, int minHeight, boolean exactHeight, boolean missingWarns) {
		public Sprite(String folder, int width, int minHeight, boolean exactHeight) {
			this(folder, width, minHeight, exactHeight, false);
		}

		public Sprite(String folder, int width, int minHeight) {
			this(folder, width, minHeight, false, false);
		}
	}

	/**
	 * One number in a {@link Kind#KEYED_ROWS} entry.
	 *
	 * @param key its tag inside the entry, or null if the entry is just this number
	 * @param absent what the game uses if it's left out, or null if it's needed
	 */
	public record Column(String key, String label, double min, double max, boolean required, String absent) {
	}

	/**
	 * What a nested tag holds.
	 *
	 * @param children the tags inside a block, or inside each block of a list
	 * @param columns the numbers in each entry of keyed rows
	 * @param maxEntries the most entries or rows the game accepts, or 0 for no limit
	 * @param fixedCount how many blocks the game requires, or 0 if the player decides
	 * @param namedSlots how many of a fixed list's blocks hold anything, because the game names the rest
	 */
	public record Shape(List<TagSpec> children, List<Column> columns, int maxEntries, int fixedCount, int namedSlots) {
		public static final Shape NONE = new Shape(List.of(), List.of(), 0, 0, 0);

		static Shape block(List<TagSpec> children) {
			return new Shape(children, List.of(), 0, 0, 0);
		}

		static Shape rows(List<Column> columns) {
			return new Shape(List.of(), columns, 0, 0, 0);
		}

		/** Keyed blocks, with a limit on how many names the game accepts, or 0 for no limit. */
		static Shape keyed(List<TagSpec> children, int maxEntries) {
			return new Shape(children, List.of(), maxEntries, 0, 0);
		}
	}

	/**
	 * What an icon holds when it's written as a block. BG and FG are icon paths, and a block can't hold
	 * another block (Icons.java:101-116). The game only enforces each number's minimum (Json.java:296-302).
	 */
	private static final Shape ICON_BLOCK = Shape.block(List.of(
			new TagSpec("BG", Kind.ICON, true, null, null, null, true, Reference.ICONS, null, List.of(), "Icons.java:103", Confidence.CODE_VERIFIED),
			new TagSpec("FG", Kind.ICON, false, "Nothing on top", null, null, true, Reference.ICONS, null, List.of(), "Icons.java:105-108", Confidence.CODE_VERIFIED),
			new TagSpec("SCALE", Kind.INT, false, "1", 1.0, 8.0, false, null, null, List.of(), "Icons.java:102", Confidence.CODE_VERIFIED),
			new TagSpec("COLOR", Kind.COLOR, false, "255_255_255", null, null, true, null, null, List.of(), "Icons.java:104-106, COLOR.java:15, ColorImp.java:136, which halves what's written", Confidence.CODE_VERIFIED),
			new TagSpec("OFFX", Kind.INT, false, "0", -100.0, 100.0, false, null, null, List.of(), "Icons.java:114", Confidence.CODE_VERIFIED),
			new TagSpec("OFFY", Kind.INT, false, "0", -100.0, 100.0, false, null, null, List.of(), "Icons.java:115", Confidence.CODE_VERIFIED),
			new TagSpec("SHADOW", Kind.INT, false, "0", -100.0, 100.0, false, null, null, List.of(), "Icons.java:116,146,162", Confidence.CODE_VERIFIED)));

	/** The same icon tag, which can also be written as a block (Icons.java:88-90). */
	public TagSpec withIconBlock() {
		return new TagSpec(key, kind, required, gameDefault, min, max, maxEnforced, reference, linkedTo, subKeys, evidence, confidence, sprite, ICON_BLOCK, blankAllowed);
	}

	/** A tag that doesn't name an image. */
	public TagSpec(String key, Kind kind, boolean required, String gameDefault, Double min, Double max, boolean maxEnforced, Reference reference, String linkedTo,
			List<SubKey> subKeys, String evidence, Confidence confidence) {
		this(key, kind, required, gameDefault, min, max, maxEnforced, reference, linkedTo, subKeys, evidence, confidence, null, Shape.NONE);
	}

	/** A tag that isn't nested. */
	public TagSpec(String key, Kind kind, boolean required, String gameDefault, Double min, Double max, boolean maxEnforced, Reference reference, String linkedTo,
			List<SubKey> subKeys, String evidence, Confidence confidence, Sprite sprite) {
		this(key, kind, required, gameDefault, min, max, maxEnforced, reference, linkedTo, subKeys, evidence, confidence, sprite, Shape.NONE);
	}

	/** The same tag under another key, which is how a nested tag gets its full path. */
	public TagSpec withKey(String newKey) {
		return new TagSpec(newKey, kind, required, gameDefault, min, max, maxEnforced, reference, linkedTo, subKeys, evidence, confidence, sprite, shape, blankAllowed);
	}

	/** The tags inside a block, under their full keys, such as WORK.SHIFT_OFFSET. */
	public List<TagSpec> nested() {
		return shape.children().stream().map(child -> child.withKey(key + "." + child.key())).toList();
	}

	/** The tags inside one block of a list, under their full keys, such as UPGRADES.0.BOOST. */
	public List<TagSpec> nestedAt(int index) {
		return shape.children().stream().map(child -> child.withKey(key + "." + index + "." + child.key())).toList();
	}

	/** The last part of the key, which is how it's written in the file. */
	public String leafName() {
		return key.substring(key.lastIndexOf('.') + 1);
	}

	/** True if the editor shows it greyed out with its default when absent. */
	public boolean ghosted() {
		return !required && gameDefault != null;
	}

	/** True if it's only offered from the add list when absent, because it has no default. */
	public boolean addOnly() {
		return !required && gameDefault == null;
	}

	/** True if the tag's value is entered as part of another tag's row. */
	public boolean linked() {
		return linkedTo != null;
	}

	/** Whether what's typed so far could become a valid value, so other characters can be refused. */
	public boolean allowsTyping(String text) {
		return switch (kind) {
		case DECIMALS -> text.matches("[-\\d.,\\s]*");
		case INT -> text.matches("-?\\d*");
		case DECIMAL, DECIMAL_MAP -> text.matches("-?\\d*\\.?\\d*");
		default -> true;
		};
	}

	/** The range as people read it, or null if there isn't one. */
	public String rangeText() {
		if (min == null && max == null) {
			return null;
		}
		String low = min == null ? "any" : number(min);
		String high = max == null ? "any" : number(max);
		return maxEnforced || max == null ? low + " to " + high : low + " to " + high + " (the game doesn't enforce the maximum)";
	}

	private static String number(double value) {
		return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
	}
}
