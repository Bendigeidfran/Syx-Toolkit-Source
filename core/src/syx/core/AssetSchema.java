package syx.core;

import java.util.List;
import java.util.Map;

/**
 * The tags of one asset type. Values are held as text, one entry per tag, so every front end handles
 * them alike: a list is comma separated, and a block's entries use the key TAG.ENTRY.
 *
 * @param dataTags the tags of assets/init/folder/NAME.txt
 * @param textTags the tags of assets/text/folder/NAME.txt
 * @param spawnBlocks blocks that each need a value above 0 for the asset to ever appear
 * @param notes things the user should know that aren't about one tag
 * @param crossRule what a type requires across its tags and other files
 * @param sounds the sounds this type looks up by name, which are held under SOUND.prefix and go in files of their own
 * @param starter where a new asset's starting values come from, or null if it starts empty
 * @param companions files this asset names, besides its own data and text files
 * @param appearanceFolder the folder of vanilla files a new asset can start from, under assets, or null
 * @param foldHeadings tags that start folded under a heading, by key. Most people leave these alone. The key is what's saved
 * @param textAtRoot whether INFO's tags must be at the top of the text file, never in an INFO block, because something
 *                   besides INFO reads them there. A play mode's NAME is read that way (PBonusSetting.java:56-59)
 * @param outOfScope why a file with this type's prefix is read by different code, which the schema doesn't
 *                   describe, or null if every such file is one. A torch is a monument read another way (ROOM_MONUMENTS.java:26-29)
 * @param overrides the game's own files this asset overrides, or null if it adds an asset of its own
 */
public record AssetSchema(
		List<TagSpec> dataTags,
		List<TagSpec> textTags,
		List<String> spawnBlocks,
		List<String> notes,
		CrossRule crossRule,
		List<SoundEntries.Slot> sounds,
		Starter starter,
		List<Companion> companions,
		String appearanceFolder,
		Map<String, String> foldHeadings,
		boolean textAtRoot,
		OutOfScope outOfScope,
		Overridden overrides) {

	/**
	 * The game's own files of an asset that a mod can only override. The game reads a mod's file of the same
	 * name merged over these (Json.java:29-42): each top-level tag the mod writes replaces the game's whole
	 * tag, and every tag it leaves out keeps the game's value (JsonValue.java:323-339).
	 *
	 * @param dataFile the game's data file, under assets, such as init/room/_COURT.txt
	 * @param textFile the game's text file, under assets
	 */
	public record Overridden(String dataFile, String textFile) {
	}

	public AssetSchema(List<TagSpec> dataTags, List<TagSpec> textTags, List<String> spawnBlocks, List<String> notes, CrossRule crossRule,
			List<SoundEntries.Slot> sounds, Starter starter, List<Companion> companions, String appearanceFolder, Map<String, String> foldHeadings,
			boolean textAtRoot, OutOfScope outOfScope) {
		this(dataTags, textTags, spawnBlocks, notes, crossRule, sounds, starter, companions, appearanceFolder, foldHeadings, textAtRoot, outOfScope, null);
	}

	/** The same schema, for an asset that overrides the game's own files. */
	AssetSchema withOverride(Overridden files) {
		return new AssetSchema(dataTags, textTags, spawnBlocks, notes, crossRule, sounds, starter, companions, appearanceFolder, foldHeadings, textAtRoot, outOfScope, files);
	}

	/** Says why a data file isn't this schema's, from what the game reads in it, or null if it is. */
	public interface OutOfScope {
		String reason(GameTree.Block data);
	}

	public AssetSchema(List<TagSpec> dataTags, List<TagSpec> textTags, List<String> spawnBlocks, List<String> notes, CrossRule crossRule,
			List<SoundEntries.Slot> sounds, Starter starter, List<Companion> companions, String appearanceFolder, Map<String, String> foldHeadings) {
		this(dataTags, textTags, spawnBlocks, notes, crossRule, sounds, starter, companions, appearanceFolder, foldHeadings, false, null);
	}

	/** The same schema, with INFO's tags written at the top of the text file. */
	AssetSchema withTextAtRoot() {
		return new AssetSchema(dataTags, textTags, spawnBlocks, notes, crossRule, sounds, starter, companions, appearanceFolder, foldHeadings, true, outOfScope, overrides);
	}

	/** The same schema, refusing files that the game reads with other code. */
	AssetSchema withOutOfScope(OutOfScope check) {
		return new AssetSchema(dataTags, textTags, spawnBlocks, notes, crossRule, sounds, starter, companions, appearanceFolder, foldHeadings, textAtRoot, check, overrides);
	}

	/** Why this data file isn't one this schema describes, or null if it is. */
	public String outOfScopeReason(GameTree.Block data) {
		return outOfScope == null || data == null ? null : outOfScope.reason(data);
	}

	public AssetSchema(List<TagSpec> dataTags, List<TagSpec> textTags, List<String> spawnBlocks, List<String> notes, CrossRule crossRule,
			List<SoundEntries.Slot> sounds, Starter starter, List<Companion> companions, String appearanceFolder) {
		this(dataTags, textTags, spawnBlocks, notes, crossRule, sounds, starter, companions, appearanceFolder, Map.of());
	}

	/**
	 * A file the asset names. Its values use the prefix {@code file.<id>.}, and the name tag says which file.
	 *
	 * @param folder the folder under assets, such as init/race/sprite
	 * @param nameTag the value key that holds this file's name, without .txt
	 * @param ownSuffix added to the asset's name when a new file is written, so a shared vanilla name is not reused. Null keeps the name tag as it is
	 */
	public record Companion(String id, String label, String folder, String nameTag, boolean required, String ownSuffix, List<TagSpec> tags, List<String> handbookLocations) {
		public String prefix() {
			return "file." + id + ".";
		}

		public String relative(String stem) {
			return folder + "/" + stem + ".txt";
		}

		/** The name a new asset gives this file, or null if it keeps whatever the name tag already says. */
		public String ownStem(String assetName) {
			return ownSuffix == null ? null : assetName + ownSuffix;
		}
	}

	/**
	 * Tags a new asset starts with, read at run time from the player's own game, because no game data
	 * ships with the toolkit. They're starting values rather than defaults, and every one can be changed.
	 *
	 * @param file the game's file, under assets
	 * @param tags the top-level tags to take from it
	 */
	public record Starter(String file, List<String> tags) {
	}

	/** Checks what no single tag can. */
	public interface CrossRule {
		void check(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings);

		/**
		 * The same, knowing which values are kept as written. They're in the file, but not in {@code values},
		 * so a rule that looks for something by name must count them as there.
		 */
		default void check(Map<String, String> values, GameAssetIndex index, java.util.Set<String> kept, List<AssetChecker.Finding> findings) {
			check(values, index, findings);
		}
	}

	public AssetSchema(List<TagSpec> dataTags, List<TagSpec> textTags, List<String> spawnBlocks, List<String> notes) {
		this(dataTags, textTags, spawnBlocks, notes, (values, index, findings) -> { }, List.of(), null, List.of(), null);
	}

	public AssetSchema(List<TagSpec> dataTags, List<TagSpec> textTags, List<String> spawnBlocks, List<String> notes, CrossRule crossRule, List<SoundEntries.Slot> sounds) {
		this(dataTags, textTags, spawnBlocks, notes, crossRule, sounds, null, List.of(), null);
	}

	public AssetSchema(List<TagSpec> dataTags, List<TagSpec> textTags, List<String> spawnBlocks, List<String> notes, CrossRule crossRule, List<SoundEntries.Slot> sounds, Starter starter) {
		this(dataTags, textTags, spawnBlocks, notes, crossRule, sounds, starter, List.of(), null);
	}

	/** The same schema, with the sounds its type looks up. */
	AssetSchema withSounds(List<SoundEntries.Slot> slots) {
		return new AssetSchema(dataTags, textTags, spawnBlocks, notes, crossRule, slots, starter, companions, appearanceFolder, foldHeadings, textAtRoot, outOfScope, overrides);
	}

	public Companion companion(String id) {
		for (Companion companion : companions) {
			if (companion.id().equals(id)) {
				return companion;
			}
		}
		return null;
	}

	/** The companion a value key belongs to, or null. */
	public Companion companionFor(String key) {
		if (key == null || !key.startsWith("file.")) {
			return null;
		}
		int dot = key.indexOf('.', 5);
		String id = dot < 0 ? key.substring(5) : key.substring(5, dot);
		return companion(id);
	}

	/** A rule from the game's code to show with a tag, or null if it has none. These never come from the handbook. */
	public String ruleText(TagSpec tag) {
		if (tag.kind() == TagSpec.Kind.SPRITE) {
			return "Must be exactly " + tag.sprite().width() + " wide and at least " + tag.sprite().minHeight() + " tall, or the game stops loading.";
		}
		if (tag.blankAllowed()) {
			return "The game needs this tag, and accepts it blank. Left empty, it's written as \"\".";
		}
		if (tag.kind() == TagSpec.Kind.FRAMES) {
			return "One to a line, as FILE: ROW from " + tag.sprite().folder() + ", or - to draw nothing. With several, each object picks one at random.";
		}
		boolean hasAmounts = dataTags.stream().anyMatch(other -> tag.key().equals(other.linkedTo()));
		if (tag.kind() == TagSpec.Kind.KEY_LIST && tag.shape().maxEntries() == 0 && hasAmounts) {
			return "Each entry has an amount. An empty amount list is still written if there are none, because the game requires it.";
		}
		if (tag.kind() == TagSpec.Kind.TEXT_ROWS && tag.shape().maxEntries() > 0) {
			return "1 to " + tag.shape().maxEntries() + " rows, all the same length.";
		}
		if (tag.kind() == TagSpec.Kind.KEY_LIST && tag.shape().maxEntries() > 0) {
			return "At most " + tag.shape().maxEntries() + " entries, each with an amount.";
		}
		String range = tag.rangeText();
		return range == null ? null : "Range: " + range + ".";
	}

	/**
	 * Finds a tag by its full key, at any depth, or null. A list's index is left out or given: both work.
	 * A top tag can be in both files, such as WORK, so the data file is tried first and then the text file.
	 */
	public TagSpec find(String fullKey) {
		String[] parts = fullKey.split("\\.");
		TagSpec found = walk(root(dataTags, parts[0]), parts);
		if (found == null) {
			found = walk(root(textTags, parts[0]), parts);
		}
		if (found == null) {
			found = findCompanion(fullKey);
		}
		return found;
	}

	/**
	 * The file a tag names, or null if it names none. A list's index is left out, given or written as #:
	 * all work, as in {@link #find}.
	 */
	public Companion namedBy(String fullKey) {
		String wanted = withoutIndexes(fullKey);
		for (Companion companion : companions) {
			if (withoutIndexes(companion.nameTag()).equals(wanted)) {
				return companion;
			}
		}
		return null;
	}

	private static String withoutIndexes(String key) {
		StringBuilder out = new StringBuilder();
		for (String part : key.split("\\.")) {
			if (part.equals("#") || part.matches("\\d+")) {
				continue;
			}
			if (out.length() > 0) {
				out.append('.');
			}
			out.append(part);
		}
		return out.toString();
	}

	private TagSpec findCompanion(String fullKey) {
		Companion companion = companionFor(fullKey);
		if (companion == null) {
			return null;
		}
		for (TagSpec tag : companion.tags()) {
			if (fullKey.equals(tag.key()) || fullKey.startsWith(tag.key() + ".")) {
				String[] rest = fullKey.substring(tag.key().length()).split("\\.");
				String[] parts = new String[rest.length];
				parts[0] = tag.key();
				System.arraycopy(rest, 1, parts, 1, rest.length - 1);
				return walk(tag, parts);
			}
		}
		return null;
	}

	/** Whether a value belongs in the text file. A block has no value of its own, so an exact match on a tag in both files is the text one. */
	public boolean isTextKey(String fullKey) {
		String[] parts = fullKey.split("\\.");
		TagSpec data = root(dataTags, parts[0]);
		if (root(textTags, parts[0]) == null) {
			return false;
		}
		return data == null || parts.length == 1 || !dataHolds(data, parts);
	}

	/** Kinds whose next part is an entry's name, not one of its tags, so {@link #walk} stops there. */
	private static final java.util.Set<TagSpec.Kind> ENTRY_KINDS = java.util.EnumSet.of(TagSpec.Kind.DECIMAL_MAP, TagSpec.Kind.KEYED_ROWS, TagSpec.Kind.KEYED_LISTS,
			TagSpec.Kind.KEYED_TEXTS, TagSpec.Kind.TEXT_BLOCK);

	/**
	 * Whether a data tag holds this key: the whole path is one of its tags, or the deepest part it reaches takes
	 * entries by name. SERVICE is in both of a service room's files, and SERVICE.BOOST.PHYSICS_HEALTH>MUL is an
	 * entry of the data file's BOOST, which walk() alone would take for a text key.
	 */
	private static boolean dataHolds(TagSpec data, String[] parts) {
		for (int end = parts.length; end >= 1; end--) {
			TagSpec reached = walk(data, java.util.Arrays.copyOf(parts, end));
			if (reached != null) {
				return end == parts.length || ENTRY_KINDS.contains(reached.kind());
			}
		}
		return false;
	}

	/**
	 * The name the handbook gives a tag. It describes each file on its own, and a tag once however many
	 * entries hold it, so file.sprite.TYPES.0.SPRITE_FILE is TYPES.SPRITE_FILE and COLORS.SKIN.VALUES is
	 * COLORS.VALUES. The part after a keyed block is always an entry's name, even one spelled like a tag inside it.
	 */
	public String handbookKey(String fullKey) {
		Companion companion = companionFor(fullKey);
		String prefix = companion == null ? "" : companion.prefix();
		String[] parts = fullKey.substring(prefix.length()).split("\\.");
		TagSpec at = companion != null ? root(companion.tags(), prefix + parts[0]) : root(dataTags, parts[0]) != null ? root(dataTags, parts[0]) : root(textTags, parts[0]);
		StringBuilder key = new StringBuilder(parts[0]);
		boolean inEntry = false;
		for (int i = 1; i < parts.length; i++) {
			if (at != null && at.kind() == TagSpec.Kind.KEYED_BLOCKS && !inEntry) {
				inEntry = true;
				continue;
			}
			// An agent's list of tags writes # where the index or entry name goes.
			if (parts[i].matches("\\d+|#")) {
				continue;
			}
			at = at == null ? null : root(at.shape().children(), parts[i]);
			inEntry = false;
			key.append('.').append(parts[i]);
		}
		return key.toString();
	}

	private static TagSpec root(List<TagSpec> tags, String key) {
		return tags.stream().filter(tag -> tag.key().equals(key)).findFirst().orElse(null);
	}

	private static TagSpec walk(TagSpec start, String[] parts) {
		TagSpec found = start;
		String path = parts[0];
		for (int i = 1; i < parts.length && found != null; i++) {
			path += "." + parts[i];
			TagSpec next = root(found.shape().children(), parts[i]);
			// A list's index, or a keyed block's name, is not one of the tags inside it.
			if (parts[i].matches("\\d+") || next == null && found.kind() == TagSpec.Kind.KEYED_BLOCKS) {
				continue;
			}
			found = next == null ? null : next.withKey(path);
		}
		return found;
	}

	public TagSpec tag(String key) {
		for (TagSpec tag : dataTags) {
			if (tag.key().equals(key)) {
				return tag;
			}
		}
		for (TagSpec tag : textTags) {
			if (tag.key().equals(key)) {
				return tag;
			}
		}
		for (Companion companion : companions) {
			for (TagSpec tag : companion.tags()) {
				if (tag.key().equals(key)) {
					return tag;
				}
			}
		}
		return null;
	}
}
