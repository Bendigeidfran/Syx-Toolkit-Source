package syx.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the asset form shows, as data. The GUI builds its rows and messages from this and agents read
 * it through {@link Operations}, which is what makes the two give the same results.
 */
public final class AssetForm {

	public static final String NO_HANDBOOK = "No handbook description available.";
	public static final String NOT_DESCRIBED = "The handbook doesn't describe this yet.";
	public static final String COLOR_PART = "One part of this colour. The handbook describes the colour as a whole.";
	public static final String COMMENTS_NOTE = "This asset's files appear to contain comments (text after **). A file that changes is rewritten through the game's parser, which doesn't keep them. Every tag and value is kept.";
	public static final String TEXT_FILE_OFFER = "This asset has no text file. The game stops loading without one, unless another enabled mod has it (VirtualFolder.java:92-93).";
	/** Shown when a new asset's chosen appearance doesn't name an optional companion. */
	public static final String UNUSED_COMPANION_OFFER = "The chosen appearance doesn't use this file.";

	/** How a tag starts on the form. */
	public enum RowState {
		/** Written in a form the editor can't show, and left as it is unless it's replaced. */
		KEPT,
		/** Has a row from the start. */
		REQUIRED,
		/** Greyed out with the game's default until it's added. */
		GHOSTED,
		/** Offered from the add list, because it has no default. */
		ADD_ONLY,
		/** Entered as part of another tag's row. */
		LINKED
	}

	/**
	 * What the form says right now.
	 *
	 * @param findings every problem and warning
	 * @param callouts the notes at the top of the form
	 * @param summary why Save is off, or empty if nothing's holding it back
	 * @param canSave whether nothing blocks saving
	 * @param attention the tags that block saving, for going to the first one
	 */
	public record Status(List<AssetChecker.Finding> findings, List<String> callouts, String summary, boolean canSave, List<String> attention) {
	}

	private AssetForm() {
	}

	public static RowState initialState(TagSpec tag, Set<String> kept) {
		if (tag.linked()) {
			return RowState.LINKED;
		}
		if (kept.contains(tag.key())) {
			return RowState.KEPT;
		}
		if (tag.required()) {
			return RowState.REQUIRED;
		}
		return tag.ghosted() ? RowState.GHOSTED : RowState.ADD_ONLY;
	}

	/** The values a new asset starts with: its starter file's, then its sounds'. */
	public static Map<String, String> startingValues(AssetSchema schema, GameAssetIndex index) {
		return startingValues(schema, index, null, null);
	}

	/**
	 * Starting values for a new asset. {@code appearance} chooses which vanilla file to copy from, when the
	 * type has one, and {@code assetName} is the name its own companion files will use.
	 */
	public static Map<String, String> startingValues(AssetSchema schema, GameAssetIndex index, String appearance, String assetName) {
		Map<String, String> values = new LinkedHashMap<>(AssetEditing.startingValues(schema, index, appearance, assetName));
		for (SoundEntries.Slot slot : schema.sounds()) {
			String start = soundStart(slot, index);
			if (start != null) {
				values.put(slot.valueKey(), start);
			}
		}
		return values;
	}

	/**
	 * What a new asset's look holds in a form the editor can't show. Its files are copies of the look's,
	 * so these are kept as written, the same as when an asset is opened.
	 */
	public static Set<String> startingKept(AssetSchema schema, GameAssetIndex index, String appearance) {
		if (schema.overrides() != null) {
			return AssetEditing.overrideKept(schema, index);
		}
		return AssetEditing.sources(schema, index, appearance).kept();
	}

	/** The values of {@link #startingKept}, as the game's parser reads them from the look's files, on one line each. */
	public static Map<String, String> startingKeptAsWritten(AssetSchema schema, GameAssetIndex index, String appearance) {
		if (schema.overrides() != null) {
			return AssetEditing.keptAsWritten(schema, AssetEditing.overrideKept(schema, index), index.starter(schema.overrides().dataFile()),
					index.starter(schema.overrides().textFile()), Map.of());
		}
		AssetEditing.Sources sources = AssetEditing.sources(schema, index, appearance);
		return AssetEditing.keptAsWritten(schema, sources.kept(), sources.data(), sources.text(), sources.companions());
	}

	/**
	 * What Initialise puts on a form from a vanilla asset, read from the player's install when asked.
	 *
	 * @param appearance for a type that starts from a look, the look a new asset's files are now copied from, or null
	 * @param kept and {@code keptAsWritten}, what that look holds that the form can't show, which a new asset keeps
	 * @param notCopied vanilla values the form can't show and that won't be written, as the game reads them
	 */
	public record VanillaStart(String from, Map<String, String> values, String appearance, Set<String> kept, Map<String, String> keptAsWritten, Map<String, String> notCopied) {
	}

	/** The vanilla assets a form can be initialised from: a type's looks if it has them, or its vanilla assets. */
	public static List<String> vanillaChoices(AssetCatalog.Entry entry, GameAssetIndex index) {
		return entry.schema().appearanceFolder() != null ? appearances(entry.schema(), index) : AssetEditing.vanillaNames(entry, index);
	}

	/**
	 * The values of a vanilla asset as a form for {@code assetName} holds them, or null if the game has no such asset.
	 * A type with looks, such as a race, starts from that look as a new asset does, so the files it names are this
	 * asset's own. Anything else is the vanilla asset's values as they are. Nothing is stored.
	 *
	 * @param newAsset whether the form is for a new asset, whose files can then be copied from the look
	 */
	public static VanillaStart vanillaStart(AssetCatalog.Entry entry, Path gameFolder, GameAssetIndex index, GameParser parser, String from, String assetName, boolean newAsset)
			throws java.io.IOException, GameParser.ReadException {
		AssetSchema schema = entry.schema();
		if (schema.appearanceFolder() != null) {
			if (!appearances(schema, index).contains(from)) {
				return null;
			}
			Map<String, String> values = startingValues(schema, index, from, assetName);
			Map<String, String> kept = startingKeptAsWritten(schema, index, from);
			// An existing asset's files aren't copied from the look, so what the form can't show in it isn't brought over.
			return newAsset ? new VanillaStart(from, values, from, startingKept(schema, index, from), kept, Map.of()) : new VanillaStart(from, values, null, Set.of(), Map.of(), kept);
		}
		AssetEditing.Vanilla vanilla = AssetEditing.vanilla(entry, gameFolder, index, parser, from);
		return vanilla == null ? null : new VanillaStart(from, vanilla.values(), null, Set.of(), Map.of(), vanilla.keptAsWritten());
	}

	/** Says which of a vanilla asset's values couldn't be put on the form, or null if every one was. */
	public static String notCopiedNote(VanillaStart start) {
		if (start == null || start.notCopied().isEmpty()) {
			return null;
		}
		List<String> shown = start.notCopied().entrySet().stream().map(entry -> entry.getKey() + " (" + entry.getValue() + ")").toList();
		return "Initialised from vanilla " + start.from() + ". These of its values are written in a form this editor can't show, so they weren't copied: " + String.join("; ", shown)
				+ ". Fill one in to set it.";
	}

	/** The vanilla files a new asset can start from, by name. */
	public static List<String> appearances(AssetSchema schema, GameAssetIndex index) {
		return schema.appearanceFolder() == null ? List.of() : index.keysInFolder(schema.appearanceFolder());
	}

	public static String soundStart(SoundEntries.Slot slot, GameAssetIndex index) {
		return index.sounds() == null ? null : index.sounds().startingValue(slot);
	}

	public static List<String> soundOptions(SoundEntries.Slot slot, GameAssetIndex index) {
		return index.sounds() == null ? List.of(SoundEntries.NO_SOUND) : index.sounds().options(slot);
	}

	/**
	 * What a tag's value can be, or null if it isn't chosen from a list. A tag that names one of the
	 * schema's companion files, such as a race's HOME, can be any file in that companion's folder.
	 */
	public static List<String> options(TagSpec tag, AssetSchema schema, GameAssetIndex index) {
		AssetSchema.Companion named = tag.kind() == TagSpec.Kind.PLAIN && schema != null ? schema.namedBy(tag.key()) : null;
		if (named != null) {
			return index.fileNames(named.folder());
		}
		return switch (tag.kind()) {
		case BOOL -> List.of("true", "false");
		case SPRITE -> tag.sprite() == null ? List.of() : index.spriteOptions(tag.sprite());
		case FRAMES -> tag.sprite() == null ? List.of() : index.spriteOptions(tag.sprite());
		default -> tag.reference() == null ? null : index.options(tag.reference());
		};
	}

	/**
	 * The positions an icon sheet looks like it has, as people read them, or null if it isn't a valid
	 * sheet. The count comes from the image's size, so some of them may be empty.
	 */
	public static String iconPositions(String sheet, GameAssetIndex index) {
		int count = index.iconCount(sheet);
		if (count <= 0) {
			return null;
		}
		return count == 1 ? "0" : "0 to " + (count - 1);
	}

	/** Warns about a new resource that nothing produces. The user may well be about to add the room that does. */
	public static String producerWarning(AssetType type, String name, ResourceProducers producers) {
		boolean checks = type.initFolder().equals("resource") && producers != null && !producers.produces(name);
		return checks ? ResourceProducers.warning(List.of(name)) : null;
	}

	public static String soundsOffer(List<String> missing) {
		return "These sound entries are missing: " + String.join(", ", missing) + ". The game plays nothing for them, and can list them in an error box when it closes (SoundRaces.java:43-53).";
	}

	/**
	 * Why a companion can be added, or null when it's already this asset's or the asset doesn't name one.
	 * The same words are on the form and in an agent's {@code can_generate}.
	 */
	public static String companionOffer(AssetEditing.FilePlace place, String stem) {
		return switch (place) {
		case GAME -> "This is the game's " + stem + ". Adding it copies it into this mod under this asset's own name.";
		case MISSING -> "The game needs this file, and neither the mod nor the game has it.";
		case OWN, UNUSED -> null;
		};
	}

	/** The handbook's words for a tag, found by the name it gives the tag ({@link AssetSchema#handbookKey}). */
	public static HandbookIndex.TagHelp help(HandbookIndex handbook, AssetSchema schema, List<String> locations, boolean inTextFile, String key) {
		if (handbook == null) {
			return null;
		}
		return handbook.description(locations, inTextFile ? HandbookIndex.Section.TEXT_FILE : HandbookIndex.Section.TAGS, schema.handbookKey(key));
	}

	/** What to say about a tag the handbook has no entry for. The red, green and blue of a colour in a list are never given entries of their own. */
	public static String withoutHelp(String key) {
		return key.matches(".*\\.(\\d+|#)\\.[RGB]") ? COLOR_PART : NOT_DESCRIBED;
	}

	/**
	 * Works out the form's messages.
	 *
	 * @param scope what's being edited, or null for a new asset
	 * @param handbookNote the note about the handbook's game version, or null
	 * @param producerWarning the warning from {@link #producerWarning}, or null
	 * @param comments whether the asset's files appear to hold comments
	 * @param misplacedText text tags the file has only inside INFO, from {@link AssetEditing.Opened#misplacedText}
	 * @param kept tags in a form the editor can't show
	 * @param keptAsWritten their values as the game reads them, from {@link AssetEditing.Opened#keptAsWritten} or {@link #startingKeptAsWritten}
	 */
	public static Status status(AssetSchema schema, GameAssetIndex index, Map<String, String> entered, AssetChecker.Scope scope, String handbookNote, String producerWarning, boolean comments,
			List<String> misplacedText, Set<String> kept, Map<String, String> keptAsWritten) {
		List<AssetChecker.Finding> findings = scope == null ? AssetChecker.checkNew(schema, index, entered, kept) : AssetChecker.check(schema, index, entered, scope);
		findings = withMisplacedText(schema, findings, misplacedText, entered);
		List<String> callouts = new ArrayList<>();
		if (handbookNote != null) {
			callouts.add(handbookNote);
		}
		if (producerWarning != null) {
			callouts.add(producerWarning);
		}
		if (comments) {
			callouts.add(COMMENTS_NOTE);
		}
		List<String> keptInside = kept.stream().filter(key -> !hasOwnRow(schema, key) && entered.keySet().stream().noneMatch(value -> value.equals(key) || value.startsWith(key + "."))).sorted().toList();
		if (!keptInside.isEmpty()) {
			List<String> shown = keptInside.stream().map(key -> keptAsWritten != null && keptAsWritten.containsKey(key) ? key + " (now " + keptAsWritten.get(key) + ")" : key).toList();
			callouts.add("These are written in a form this editor can't show, and stay as they are unless you fill them in: " + String.join("; ", shown) + ".");
		}
		int missing = 0;
		int wrong = 0;
		boolean blocked = false;
		List<String> attention = new ArrayList<>();
		for (AssetChecker.Finding finding : findings) {
			blocked |= finding.blocksSaving();
			if (finding.blocksSaving() && finding.tag() != null) {
				attention.add(finding.tag());
			}
			if (finding.severity() == AssetChecker.Severity.MISSING) {
				missing++;
			} else if (finding.tag() == null) {
				callouts.add(finding.message());
			} else if (finding.severity() == AssetChecker.Severity.ERROR) {
				wrong++;
			}
		}
		String still = missing == 0 ? "" : missing + (missing == 1 ? " required tag still needs a value. " : " required tags still need a value. ");
		String bad = wrong == 0 ? "" : wrong + (wrong == 1 ? " value has a problem, shown beside it." : " values have problems, shown beside them.");
		return new Status(findings, callouts, still + bad, !blocked, attention);
	}

	/**
	 * A text tag written only inside INFO isn't read: INFO.java reads only NAME, NAMES, DESC and WIKI from there
	 * (INFO.java:16-25), and the tag's loader reads the whole file. So the game sees it as absent. A required one
	 * stops the game loading, which is an error rather than a value not filled in yet. Once a value is entered,
	 * saving writes it at the top and removes the copy inside INFO.
	 */
	static List<AssetChecker.Finding> withMisplacedText(AssetSchema schema, List<AssetChecker.Finding> findings, List<String> misplaced, Map<String, String> entered) {
		if (misplaced == null || misplaced.isEmpty()) {
			return findings;
		}
		List<AssetChecker.Finding> out = new ArrayList<>();
		for (AssetChecker.Finding finding : findings) {
			if (finding.severity() == AssetChecker.Severity.MISSING && misplaced.contains(finding.tag())) {
				out.add(new AssetChecker.Finding(finding.tag(), AssetChecker.Severity.ERROR, misplacedMessage(schema, finding.tag(), true)));
			} else {
				out.add(finding);
			}
		}
		for (String key : misplaced) {
			TagSpec tag = schema.tag(key);
			String value = entered.get(key);
			boolean reported = out.stream().anyMatch(finding -> key.equals(finding.tag()));
			if (tag != null && (value == null || value.isBlank()) && !reported) {
				out.add(new AssetChecker.Finding(key, tag.required() ? AssetChecker.Severity.ERROR : AssetChecker.Severity.WARNING, misplacedMessage(schema, key, tag.required())));
			} else if (tag != null && schema.textAtRoot() && !reported) {
				// The value is shown, because INFO finds it in the block, and saving writes it at the top, so this doesn't block.
				out.add(new AssetChecker.Finding(key, AssetChecker.Severity.WARNING, "The text file has " + key + " inside an INFO block, but the game also reads it from the top of the file "
						+ "(PBonusSetting.java:56-59), so every new game stops with \"Missing a String with key: " + key + "\". Saving this asset moves it to the top."));
			}
		}
		return out;
	}

	private static String misplacedMessage(AssetSchema schema, String key, boolean required) {
		TagSpec tag = schema.tag(key);
		// Only the evidence's file reference, which comes before any comment on it.
		String where = tag == null || tag.evidence() == null ? "" : " (" + tag.evidence().split(",")[0].trim() + ")";
		String effect = required ? "so the game stops loading with \"Missing a String with key: " + key + "\"" : "so the game uses its default";
		return "This is inside the text file's INFO block, but the game reads it from the top of the file" + where + ", and only NAME, NAMES, DESC and WIKI from INFO (INFO.java:16-25). "
				+ "It isn't found, " + effect + ". Enter it here: saving writes it at the top and removes the copy inside INFO.";
	}

	private static boolean hasOwnRow(AssetSchema schema, String key) {
		TagSpec tag = schema.tag(key);
		return tag != null && !tag.linked();
	}

	/**
	 * Fills in what the form always holds, even when nothing's been typed: a blank for each required text
	 * tag at the top of a file, and for number lists and frames in the blocks that exist. So leaving one
	 * out means the same as leaving it blank on the form, and the two get the same result.
	 *
	 * @param textFile whether the text file's tags are on the form
	 */
	public static Map<String, String> withFormBlanks(AssetSchema schema, Map<String, String> values, boolean textFile) {
		Map<String, String> filled = new LinkedHashMap<>(values);
		for (TagSpec tag : schema.dataTags()) {
			addBlanks(tag, values, filled, false);
		}
		if (textFile) {
			for (TagSpec tag : schema.textTags()) {
				addBlanks(tag, values, filled, true);
			}
		}
		for (AssetSchema.Companion companion : schema.companions()) {
			boolean present = companion.required() || values.keySet().stream().anyMatch(key -> key.startsWith(companion.prefix()));
			if (present) {
				for (TagSpec tag : companion.tags()) {
					addBlanks(tag, values, filled, false);
				}
			}
		}
		return filled;
	}

	private static void addBlanks(TagSpec tag, Map<String, String> values, Map<String, String> filled, boolean topOfText) {
		switch (tag.kind()) {
		case TEXT_LINE, TEXT_LINES -> {
			if (topOfText && tag.required()) {
				filled.putIfAbsent(tag.key(), "");
			}
		}
		case DECIMALS, FRAMES -> {
			if (tag.required()) {
				filled.putIfAbsent(tag.key(), "");
			}
		}
		case BLOCK -> {
			if (tag.required() || !AssetChecker.entriesOf(tag, values).isEmpty()) {
				tag.nested().forEach(child -> addBlanks(child, values, filled, false));
			}
		}
		case BLOCK_LIST -> {
			int count = tag.shape().fixedCount() > 0 ? tag.shape().namedSlots() : 0;
			if (count == 0) {
				for (String entry : AssetChecker.entriesOf(tag, values).keySet()) {
					int dot = entry.indexOf('.');
					if (dot > 0 && entry.substring(0, dot).matches("\\d{1,4}")) {
						count = Math.max(count, Integer.parseInt(entry.substring(0, dot)) + 1);
					}
				}
			}
			for (int i = 0; i < count; i++) {
				tag.nestedAt(i).forEach(child -> addBlanks(child, values, filled, false));
			}
		}
		default -> {
			// Nothing else is held when it's blank.
		}
		}
	}

	/**
	 * Keys the form has nowhere to put. A person can't enter these, but an agent can, and a mistyped tag
	 * must not be quietly dropped. A list needs its index, so UPGRADES.BOOST isn't a value's key.
	 */
	public static List<String> unknownKeys(AssetSchema schema, Iterable<String> keys) {
		List<String> unknown = new ArrayList<>();
		for (String key : keys) {
			if (!holdsValue(schema, key)) {
				unknown.add(key);
			}
		}
		return unknown;
	}

	private static boolean holdsValue(AssetSchema schema, String key) {
		if (schema.sounds().stream().anyMatch(slot -> slot.valueKey().equals(key))) {
			return true;
		}
		String[] parts = key.split("\\.", -1);
		if (leads(schema.dataTags(), parts) || leads(schema.textTags(), parts)) {
			return true;
		}
		AssetSchema.Companion companion = schema.companionFor(key);
		if (companion == null) {
			return false;
		}
		for (TagSpec tag : companion.tags()) {
			if (key.equals(tag.key()) || key.startsWith(tag.key() + ".")) {
				String[] rest = key.substring(tag.key().length()).split("\\.", -1);
				String[] fromTag = new String[rest.length];
				fromTag[0] = tag.leafName();
				System.arraycopy(rest, 1, fromTag, 1, rest.length - 1);
				if (holds(tag.withKey(tag.leafName()), fromTag, 1) || key.equals(tag.key())) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean leads(List<TagSpec> tags, String[] parts) {
		TagSpec tag = tags.stream().filter(candidate -> candidate.key().equals(parts[0])).findFirst().orElse(null);
		return tag != null && holds(tag, parts, 1);
	}

	private static boolean holds(TagSpec tag, String[] parts, int at) {
		switch (tag.kind()) {
		case BLOCK -> {
			return at < parts.length && child(tag, parts[at]) != null && holds(child(tag, parts[at]), parts, at + 1);
		}
		case BLOCK_LIST -> {
			if (at + 1 >= parts.length || !parts[at].matches("\\d{1,4}") || child(tag, parts[at + 1]) == null) {
				return false;
			}
			int limit = tag.shape().fixedCount() > 0 ? tag.shape().namedSlots() : Integer.MAX_VALUE;
			return Integer.parseInt(parts[at]) < limit && holds(child(tag, parts[at + 1]), parts, at + 2);
		}
		case ICON -> {
			return parts.length == at || child(tag, parts[at]) != null && holds(child(tag, parts[at]), parts, at + 1);
		}
		case KEYED_ROWS -> {
			if (at >= parts.length || parts.length > at + 2 || parts[at].isEmpty()) {
				return false;
			}
			String column = parts.length == at + 2 ? parts[at + 1] : null;
			return tag.shape().columns().stream().anyMatch(known -> java.util.Objects.equals(known.key(), column));
		}
		case KEYED_BLOCKS -> {
			if (at >= parts.length || parts[at].isEmpty()) {
				return false;
			}
			TagSpec child = child(tag, parts[at]);
			if (child != null) {
				return holds(child, parts, at + 1);
			}
			return at + 1 < parts.length && child(tag, parts[at + 1]) != null && holds(child(tag, parts[at + 1]), parts, at + 2);
		}
		case DECIMAL_MAP, KEYED_LISTS, KEYED_TEXTS -> {
			return parts.length == at + 1 && !parts[at].isEmpty();
		}
		case TEXT_BLOCK -> {
			return parts.length == at + 1 && tag.subKeys().stream().anyMatch(sub -> sub.key().equals(parts[at]));
		}
		default -> {
			return parts.length == at;
		}
		}
	}

	private static TagSpec child(TagSpec tag, String name) {
		return tag.shape().children().stream().filter(candidate -> candidate.key().equals(name)).findFirst().orElse(null);
	}
}
