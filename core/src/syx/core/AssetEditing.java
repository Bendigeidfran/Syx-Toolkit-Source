package syx.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Opens an existing asset for editing. Values come from the game's parser exactly as written, so a
 * value the rules wouldn't accept still reaches the form, which can say what's wrong with it. A value
 * written in a form the editor can't show is listed as kept, and stays in the file as it is.
 */
public final class AssetEditing {

	/** Says why an asset can't be opened. */
	public static final class OpenException extends Exception {
		private static final long serialVersionUID = 1L;

		OpenException(String message) {
			super(message);
		}
	}

	/** Which asset a file belongs to. The problem is why it can't be opened, or null. */
	public record Location(AssetCatalog.Entry entry, String name, Path assets, Path dataFile, Path textFile, String problem) {
		static Location refused(String problem) {
			return new Location(null, null, null, null, null, problem);
		}
	}

	/**
	 * An asset as it was when opened.
	 *
	 * @param assets the mod's assets folder for the current game version
	 * @param values what the form starts with, in the form described on {@link AssetSchema}
	 * @param kept tags whose form the editor can't show
	 * @param dataBytes the data file's content when it was opened
	 * @param textBytes the text file's content when it was opened, or null if there isn't one
	 * @param soundFiles the content of each sound file this asset can use, or null for one that doesn't exist
	 * @param comments whether a file that could be rewritten appears to hold comments
	 */
	/** Where a companion file lives. UNUSED means the asset doesn't name one. */
	public enum FilePlace {
		OWN, GAME, MISSING, UNUSED
	}

	/**
	 * An override's own files and the game's, which the game merges (Json.java:29-42). An override's dataTree and
	 * textTree are the merged result, which is what the form shows and edits, and a save writes back only the
	 * top-level tags the mod's file already had or that now differ from the game's.
	 *
	 * @param ownData the mod's data file as written, or empty if there isn't one yet
	 * @param ownText the mod's text file as written, or empty if there isn't one
	 * @param gameData the game's data file
	 * @param gameText the game's text file, or empty if it couldn't be read
	 */
	public record Overlay(GameTree.Block ownData, GameTree.Block ownText, GameTree.Block gameData, GameTree.Block gameText) {
	}

	/** Top-level tags that make the game merge a file into the game's tag by tag, not replace each tag whole (Json.java:33-36, JsonValue.java:323-339). */
	static final List<String> MERGE_FLAGS = List.of("_JSON_ADD", "_ARRAY_ADD");

	/** The game's file with each top-level tag of the mod's file replacing the game's, as the game merges them without _JSON_ADD (JsonValue.java:323-339). */
	public static GameTree.Block overlaid(GameTree.Block game, GameTree.Block own) {
		GameTree.Block merged = game.copy();
		own.entries().forEach(merged::put);
		return merged;
	}

	/** The game's own files for an override, or why they can't be used. */
	private static Overlay overlay(AssetSchema schema, String file, GameTree.Block ownData, GameTree.Block ownText, GameAssetIndex index) throws OpenException {
		GameTree.Block gameData = index.starter(schema.overrides().dataFile());
		if (gameData == null) {
			throw new OpenException(file + " overrides the game's own " + file + ", which couldn't be read from the game's data.zip, so what the game would make of it can't be shown.");
		}
		for (String flag : MERGE_FLAGS) {
			if (ownData.has(flag) || ownText.has(flag)) {
				throw new OpenException(file + " has " + flag + ", so the game merges it into its own " + file + " inside each tag (Json.java:33-36). The form can't show that yet, so it isn't opened.");
			}
		}
		GameTree.Block gameText = index.starter(schema.overrides().textFile());
		return new Overlay(ownData, ownText, gameData, gameText == null ? GameTree.Block.empty() : gameText);
	}

	/** A companion file as it was when the asset was opened. The tree is null when there is nothing to copy. */
	public record CompanionFile(FilePlace place, String stem, GameTree.Block tree, byte[] bytes) {
	}

	public record Opened(AssetCatalog.Entry entry, String name, Path assets, Path dataFile, Path textFile, Map<String, String> values, Set<String> kept,
			GameTree.Block dataTree, GameTree.Block textTree, byte[] dataBytes, byte[] textBytes, Map<SoundEntries.Slot, SoundEntries.Info> sounds,
			Map<Path, byte[]> soundFiles, boolean comments, Map<String, CompanionFile> companions, Overlay overlay) {

		public AssetSchema schema() {
			return entry.schema();
		}

		/** The file name both of the asset's files use, without .txt. */
		public String fileName() {
			return entry.type().fileName(name);
		}

		public Path soundFile(SoundEntries.Slot slot) {
			return assets.resolve(slot.relativeFile(fileName()));
		}

		public boolean hasTextFile() {
			return textBytes != null;
		}

		public boolean needsTextFile() {
			return entry.type().textFolder() != null;
		}

		/** The sounds a type looks up that have no entry anywhere. */
		public List<SoundEntries.Slot> missingSounds() {
			return schema().sounds().stream().filter(slot -> sounds.get(slot).state() == SoundEntries.State.MISSING).toList();
		}

		/** What's being edited, given what's entered now. A missing file is only checked once it's been asked for. */
		public AssetChecker.Scope scope(Map<String, String> entered, Set<String> stillKept) {
			Set<String> soundKeys = new HashSet<>();
			for (SoundEntries.Slot slot : schema().sounds()) {
				SoundEntries.State state = sounds.get(slot).state();
				if (state == SoundEntries.State.OWN_FILE || state == SoundEntries.State.MISSING && entered.containsKey(slot.valueKey())) {
					soundKeys.add(slot.valueKey());
				}
			}
			Set<String> files = new HashSet<>();
			for (AssetSchema.Companion companion : schema().companions()) {
				CompanionFile info = companions.get(companion.id());
				boolean asked = entered.keySet().stream().anyMatch(key -> key.startsWith(companion.prefix()));
				if (info != null && info.place() == FilePlace.OWN || asked) {
					files.add(companion.id());
				}
			}
			return new AssetChecker.Scope(stillKept, hasTextFile() || hasTextValues(schema(), entered), soundKeys, files);
		}

		/** Each kept value as the game's parser reads it, on one line, by its key. */
		public Map<String, String> keptAsWritten() {
			Map<String, GameTree.Block> trees = new LinkedHashMap<>();
			companions.forEach((id, file) -> {
				if (file.place() == FilePlace.OWN && file.tree() != null) {
					trees.put(id, file.tree());
				}
			});
			return AssetEditing.keptAsWritten(schema(), kept, dataTree, textTree, trees);
		}

		/** Text tags written inside INFO, where the game doesn't read them. The form shows them as absent, as the game sees them. */
		public List<String> misplacedText() {
			return misplacedTextTags(schema(), textTree);
		}

		public List<AssetSchema.Companion> offeredCompanions() {
			List<AssetSchema.Companion> offered = new ArrayList<>();
			for (AssetSchema.Companion companion : schema().companions()) {
				CompanionFile info = companions.get(companion.id());
				if (info != null && info.place() != FilePlace.OWN && info.place() != FilePlace.UNUSED) {
					offered.add(companion);
				}
			}
			return offered;
		}
	}

	private AssetEditing() {
	}

	/** Whether any value belongs in the text file. */
	public static boolean hasTextValues(AssetSchema schema, Map<String, String> values) {
		for (TagSpec tag : schema.textTags()) {
			for (String key : values.keySet()) {
				if ((key.equals(tag.key()) || key.startsWith(tag.key() + ".")) && schema.isTextKey(key)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Works out which asset a file in the mod belongs to, or why it can't be opened. */
	public static Location locate(Path modRoot, GameParser.GameVersion version, Path file, List<AssetCatalog.Entry> entries, GameAssetIndex index) {
		return locate(modRoot, version, file, entries, index, null);
	}

	public static Location locate(Path modRoot, GameParser.GameVersion version, Path file, List<AssetCatalog.Entry> entries, GameAssetIndex index, GameParser parser) {
		Path assets = AssetCreator.versionFolder(modRoot, version).resolve("assets");
		if (!file.startsWith(assets)) {
			return Location.refused(file.startsWith(AssetCreator.versionFolder(modRoot, version)) ? notAnAsset(file)
					: file.getFileName() + " is in a folder for another game version. Only the V" + version.major() + " folder can be edited, because the tags offered come from the current game.");
		}
		Path relative = assets.relativize(file);
		String fileName = file.getFileName().toString();
		if (relative.getNameCount() < 3 || !fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".txt")) {
			return Location.refused(notAnAsset(file));
		}
		String area = relative.getName(0).toString();
		if (!area.equals("init") && !area.equals("text")) {
			return Location.refused(notAnAsset(file));
		}
		String folder = relative.subpath(1, relative.getNameCount() - 1).toString().replace('\\', '/');
		String stem = fileName.substring(0, fileName.length() - 4);
		AssetCatalog.Entry best = null;
		for (AssetCatalog.Entry entry : entries) {
			String entryFolder = area.equals("init") ? entry.type().initFolder() : entry.type().textFolder();
			if (folder.equals(entryFolder) && entry.type().ownsFile(stem)
					&& (best == null || entry.type().isOverride() || entry.type().filePrefix().length() > best.type().filePrefix().length())) {
				best = entry;
			}
		}
		if (best == null) {
			Location companion = locateCompanion(assets, area + "/" + folder, stem, entries, index, parser);
			return companion == null ? Location.refused(notAnAsset(file)) : companion;
		}
		if (!best.available()) {
			return Location.refused(best.type().label() + " files can't be edited yet: " + best.unavailableReason());
		}
		AssetType type = best.type();
		Path dataFile = assets.resolve("init").resolve(type.initFolder()).resolve(stem + ".txt");
		Path textFile = type.textFolder() == null ? null : assets.resolve("text").resolve(type.textFolder()).resolve(stem + ".txt");
		if (!Files.isRegularFile(dataFile, LinkOption.NOFOLLOW_LINKS)) {
			return Location.refused(stem + " has a text file but no data file, so there's no asset to edit.");
		}
		// The game merges files of the same name (Json.java:29-42), so an absent tag here means vanilla's value, not the default.
		// An override is opened merged over the game's file, as the game reads it (load).
		if (!type.isOverride() && index.existsInGame("init/" + type.initFolder() + "/" + stem + ".txt")) {
			return Location.refused(stem + " changes one of the game's own assets, and the game merges the two files, so what it does can't be shown on this form yet.");
		}
		// Without the parser this is found again when the file is opened, in load().
		if (parser != null && best.schema().outOfScope() != null) {
			try {
				String reason = best.schema().outOfScopeReason(tree(parser, dataFile));
				if (reason != null) {
					return Location.refused(stem + " " + reason);
				}
			} catch (GameParser.ReadException e) {
				// The parser's own message is shown when the file is opened.
			}
		}
		return new Location(best, stem.substring(type.filePrefix().length()), assets, dataFile, textFile, null);
	}

	/** A companion file opens the one asset in this mod that names it. */
	private static Location locateCompanion(Path assets, String folder, String stem, List<AssetCatalog.Entry> entries, GameAssetIndex index, GameParser parser) {
		AssetCatalog.Entry type = null;
		// Several companions can share a folder: a race's nine name files are all in text/names/nameset. Any of them may name this file.
		List<AssetSchema.Companion> candidates = new ArrayList<>();
		for (AssetCatalog.Entry entry : entries) {
			if (!entry.available()) {
				continue;
			}
			for (AssetSchema.Companion candidate : entry.schema().companions()) {
				if (candidate.folder().equals(folder)) {
					type = entry;
					candidates.add(candidate);
				}
			}
		}
		if (candidates.isEmpty()) {
			return null;
		}
		if (parser == null) {
			return Location.refused(stem + " belongs to a " + type.type().label() + ", and opening it needs the game's parser.");
		}
		List<String> owners = new ArrayList<>();
		Path dir = assets.resolve("init").resolve(type.type().initFolder());
		if (Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
			try (java.util.stream.Stream<Path> list = Files.list(dir)) {
				for (Path race : list.toList()) {
					String fileName = race.getFileName().toString();
					if (!fileName.endsWith(".txt") || !Files.isRegularFile(race, LinkOption.NOFOLLOW_LINKS)) {
						continue;
					}
					try {
						for (AssetSchema.Companion companion : candidates) {
							if (names(parser, assets, type.schema(), race, companion.nameTag(), stem)) {
								owners.add(fileName.substring(0, fileName.length() - 4));
								break;
							}
						}
					} catch (GameParser.ReadException e) {
						// A race the game can't read isn't an owner we can open.
					}
				}
			} catch (IOException e) {
				return Location.refused(stem + " couldn't be matched to a " + type.type().label() + ": " + e.getMessage());
			}
		}
		if (owners.size() != 1) {
			return Location.refused(owners.isEmpty()
					? stem + " isn't named by a " + type.type().label() + " in this mod, so it can't be opened here."
					: stem + " is named by more than one " + type.type().label() + ", so it can't be opened as one of them.");
		}
		String race = owners.get(0);
		String dataRelative = "init/" + type.type().initFolder() + "/" + type.type().fileName(race) + ".txt";
		if (index.existsInGame(dataRelative)) {
			return Location.refused(race + " changes one of the game's own assets, and the game merges the two files, so what it does can't be shown on this form yet.");
		}
		Path dataFile = assets.resolve(dataRelative);
		Path textFile = type.type().textFolder() == null ? null : assets.resolve("text").resolve(type.type().textFolder()).resolve(type.type().fileName(race) + ".txt");
		return new Location(type, race, assets, dataFile, textFile, null);
	}

	private static boolean names(GameParser parser, Path assets, AssetSchema schema, Path dataFile, String nameTag, String stem) throws GameParser.ReadException {
		return stem.equals(readNamed(parser, assets, schema, parser.root(dataFile), nameTag));
	}

	/** The value of a name tag, following it into a companion file when the tag lives there. */
	private static String readNamed(GameParser parser, Path assets, AssetSchema schema, GameParser.Node node, String nameTag) throws GameParser.ReadException {
		if (!nameTag.startsWith("file.")) {
			return readPath(node, nameTag);
		}
		AssetSchema.Companion holder = schema.companionFor(nameTag);
		if (holder == null) {
			return null;
		}
		String holderStem = readNamed(parser, assets, schema, node, holder.nameTag());
		if (holderStem == null || holderStem.isBlank()) {
			return null;
		}
		Path file = assets.resolve(holder.relative(holderStem));
		if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
			return null;
		}
		return readPath(parser.root(file), nameTag.substring(holder.prefix().length()));
	}

	private static String readPath(GameParser.Node node, String path) throws GameParser.ReadException {
		String[] parts = path.split("\\.");
		GameParser.Node current = node;
		for (int i = 0; i < parts.length - 1; i++) {
			if (i + 1 < parts.length - 1 && parts[i + 1].matches("\\d+") && current.isList(parts[i])) {
				java.util.List<GameParser.Node> blocks = current.blocks(parts[i]);
				int index = Integer.parseInt(parts[i + 1]);
				if (index < 0 || index >= blocks.size()) {
					return null;
				}
				current = blocks.get(index);
				i++;
			} else if (current.isBlock(parts[i])) {
				current = current.block(parts[i]);
			} else {
				return null;
			}
		}
		String leaf = parts[parts.length - 1];
		return current.has(leaf) ? current.value(leaf) : null;
	}

	private static String notAnAsset(Path file) {
		return file.getFileName() + " isn't an asset's data or text file, so it can't be opened here.";
	}

	/** Reads an asset's files with the game's parser. */
	public static Opened load(Location location, GameParser parser, GameAssetIndex index) throws OpenException {
		AssetSchema schema = location.entry().schema();
		String file = location.entry().type().fileName(location.name());
		try {
			byte[] dataBytes = Files.readAllBytes(location.dataFile());
			GameTree.Block dataTree = tree(parser, location.dataFile());
			Overlay overlay = null;
			if (schema.overrides() != null) {
				GameTree.Block ownText = location.textFile() != null && Files.isRegularFile(location.textFile(), LinkOption.NOFOLLOW_LINKS) ? tree(parser, location.textFile()) : GameTree.Block.empty();
				overlay = overlay(schema, file, dataTree, ownText, index);
				dataTree = overlaid(overlay.gameData(), dataTree);
			}
			String outOfScope = schema.outOfScopeReason(dataTree);
			if (outOfScope != null) {
				throw new OpenException(file + " " + outOfScope);
			}
			Map<String, String> values = new LinkedHashMap<>();
			Set<String> kept = new HashSet<>();
			for (TagSpec tag : schema.dataTags()) {
				read(tag, dataTree, values, kept);
			}
			byte[] textBytes = null;
			GameTree.Block textTree = null;
			if (location.textFile() != null && Files.isRegularFile(location.textFile(), LinkOption.NOFOLLOW_LINKS)) {
				textBytes = Files.readAllBytes(location.textFile());
				textTree = tree(parser, location.textFile());
			}
			if (overlay != null) {
				textTree = overlaid(overlay.gameText(), overlay.ownText());
			}
			if (textTree != null) {
				GameTree.Block info = infoBlock(textTree);
				for (TagSpec tag : schema.textTags()) {
					read(tag, isInfoTag(tag) ? info : textTree, values, kept);
				}
			}
			boolean comments = hasComments(dataBytes) || textBytes != null && hasComments(textBytes);
			Path assets = location.assets();
			Map<String, CompanionFile> companions = new LinkedHashMap<>();
			for (AssetSchema.Companion companion : schema.companions()) {
				String stem = values.get(companion.nameTag());
				CompanionFile info = companionFile(companion, stem, assets, index, parser);
				companions.put(companion.id(), info);
				if (info.place() == FilePlace.OWN && info.tree() != null) {
					for (TagSpec tag : companion.tags()) {
						read(tag, info.tree(), values, kept);
					}
					comments |= info.bytes() != null && hasComments(info.bytes());
				}
			}

			Map<SoundEntries.Slot, SoundEntries.Info> sounds = new LinkedHashMap<>();
			Map<Path, byte[]> soundFiles = new LinkedHashMap<>();
			for (SoundEntries.Slot slot : schema.sounds()) {
				SoundEntries.Info info = index.sounds() == null ? SoundEntries.Info.MISSING : index.sounds().info(slot, file, assets);
				sounds.put(slot, info);
				if (info.state() == SoundEntries.State.OWN_FILE) {
					values.put(slot.valueKey(), info.value());
				}
				Path own = assets.resolve(slot.relativeFile(file));
				if (!soundFiles.containsKey(own)) {
					soundFiles.put(own, Files.isRegularFile(own, LinkOption.NOFOLLOW_LINKS) ? Files.readAllBytes(own) : null);
				}
				byte[] bytes = soundFiles.get(own);
				comments |= info.state() == SoundEntries.State.OWN_FILE && bytes != null && hasComments(bytes);
			}
			return new Opened(location.entry(), location.name(), assets, location.dataFile(), location.textFile(), values, kept, dataTree, textTree, dataBytes, textBytes, sounds, soundFiles, comments, companions, overlay);
		} catch (IOException e) {
			throw new OpenException(file + " couldn't be read: " + e.getMessage());
		} catch (GameParser.ReadException e) {
			throw new OpenException("The game's parser refused " + file + ", so it can't be opened: " + e.getMessage());
		}
	}

	/**
	 * A new override as if it had been opened: its own files are whole copies of the game's that aren't written yet, so
	 * saving it writes them with the form's changes. Its sounds are the game's, which the form doesn't edit.
	 */
	static Opened newOverride(AssetCatalog.Entry entry, Path assets, GameAssetIndex index) throws OpenException {
		AssetSchema schema = entry.schema();
		AssetType type = entry.type();
		String file = type.fileName(type.fixedName());
		Overlay game = overlay(schema, file, GameTree.Block.empty(), GameTree.Block.empty(), index);
		Overlay overlay = new Overlay(game.gameData().copy(), game.gameText().copy(), game.gameData(), game.gameText());
		Map<String, String> values = new LinkedHashMap<>();
		Set<String> kept = new HashSet<>();
		readAll(schema, overlay.gameData(), overlay.gameText(), values, kept);
		Path dataFile = assets.resolve("init").resolve(type.initFolder()).resolve(file + ".txt");
		Path textFile = assets.resolve("text").resolve(type.textFolder()).resolve(file + ".txt");
		return new Opened(entry, type.fixedName(), assets, dataFile, textFile, values, kept, overlay.gameData(), overlay.gameText(), null, null, Map.of(), Map.of(), false, Map.of(), overlay);
	}

	/** A new asset's starting values, from the player's own game. Empty if the type has none or the file couldn't be read. */
	public static Map<String, String> startingValues(AssetSchema schema, GameAssetIndex index) {
		return startingValues(schema, index, null, null);
	}

	/**
	 * Starting values, including companion files copied from the chosen appearance. Name tags that would
	 * reuse a shared vanilla file are pointed at the new asset's own name. A text file the appearance
	 * doesn't use is still included when its words can be copied from one the appearance does use.
	 * Opening an existing asset does not come through here, so those files stay as an offer to add.
	 */
	public static Map<String, String> startingValues(AssetSchema schema, GameAssetIndex index, String appearance, String assetName) {
		Map<String, String> values = new LinkedHashMap<>();
		Set<String> kept = new HashSet<>();
		if (schema.overrides() != null) {
			// A new override is a copy of the game's own files, so it starts with every value they hold.
			readAll(schema, index.starter(schema.overrides().dataFile()), index.starter(schema.overrides().textFile()), values, kept);
			return values;
		}
		GameTree.Block file = schema.starter() == null ? null : index.starter(schema.starter().file());
		if (file != null) {
			for (String tag : schema.starter().tags()) {
				read(schema.tag(tag), file, values, kept);
			}
		}
		Sources look = sources(schema, index, appearance);
		values.putAll(look.values());
		if (look != Sources.NONE) {
			if (assetName != null) {
				for (AssetSchema.Companion companion : schema.companions()) {
					String own = companion.ownStem(assetName);
					if (own != null && values.containsKey(companion.nameTag())) {
						values.put(companion.nameTag(), own);
					}
				}
				generateMissingCompanions(schema, assetName, values);
			}
		}
		return values;
	}

	/** Reads every tag the form shows from a data file and a text file. Either can be null. */
	static void readAll(AssetSchema schema, GameTree.Block data, GameTree.Block text, Map<String, String> values, Set<String> kept) {
		if (data != null) {
			for (TagSpec tag : schema.dataTags()) {
				read(tag, data, values, kept);
			}
		}
		if (text != null) {
			GameTree.Block info = infoBlock(text);
			for (TagSpec tag : schema.textTags()) {
				read(tag, isInfoTag(tag) ? info : text, values, kept);
			}
		}
	}

	/** What a new override holds that the form can't show: the game's own files' kept values, which its copy keeps as written. */
	public static Set<String> overrideKept(AssetSchema schema, GameAssetIndex index) {
		Set<String> kept = new HashSet<>();
		if (schema.overrides() != null) {
			readAll(schema, index.starter(schema.overrides().dataFile()), index.starter(schema.overrides().textFile()), new LinkedHashMap<>(), kept);
		}
		return Set.copyOf(kept);
	}

	/**
	 * The vanilla files a look names, and the values the form reads from them before anything is renamed
	 * or generated. A new asset's files start from these trees and take only what the form changed, the
	 * way an edit does, so what the form can't show is copied as it's written rather than lost.
	 *
	 * @param companions each companion's file by id, for the ones the look names
	 * @param kept values those files hold in a form the editor can't show, which a new asset keeps as written
	 */
	public record Sources(GameTree.Block data, GameTree.Block text, Map<String, GameTree.Block> companions, Map<String, String> values, Set<String> kept) {
		public static final Sources NONE = new Sources(null, null, Map.of(), Map.of(), Set.of());
	}

	public static Sources sources(AssetSchema schema, GameAssetIndex index, String appearance) {
		if (appearance == null || schema.appearanceFolder() == null) {
			return Sources.NONE;
		}
		Map<String, String> values = new LinkedHashMap<>();
		Set<String> kept = new HashSet<>();
		String raceFile = schema.appearanceFolder().contains("race/sprite") ? "init/race/" + appearance + ".txt" : schema.appearanceFolder() + "/" + appearance + ".txt";
		GameTree.Block data = index.starter(raceFile);
		if (data != null) {
			for (TagSpec tag : schema.dataTags()) {
				read(tag, data, values, kept);
			}
		}
		GameTree.Block text = index.starter("text/race/" + appearance + ".txt");
		if (text != null) {
			GameTree.Block info = infoBlock(text);
			for (TagSpec tag : schema.textTags()) {
				read(tag, isInfoTag(tag) ? info : text, values, kept);
			}
		}
		Map<String, GameTree.Block> companions = new LinkedHashMap<>();
		for (AssetSchema.Companion companion : schema.companions()) {
			String stem = values.get(companion.nameTag());
			GameTree.Block body = stem == null || stem.isBlank() ? null : index.starter(companion.relative(stem));
			if (body != null) {
				companions.put(companion.id(), body);
				for (TagSpec tag : companion.tags()) {
					read(tag, body, values, kept);
				}
			}
		}
		return new Sources(data, text, companions, values, Set.copyOf(kept));
	}

	/**
	 * A vanilla asset as the form would hold it, read from the player's own data.zip when asked. Nothing is
	 * stored: each file is parsed by the game's parser and its temporary copy deleted.
	 *
	 * @param values the form's values, keyed as open_asset gives them, so they can be given to a new asset
	 * @param keptAsWritten what the form can't show, as the game reads it
	 * @param files the game's files that were read, relative to assets
	 */
	public record Vanilla(String name, Map<String, String> values, Map<String, String> keptAsWritten, List<String> files) {
	}

	/** The game's own assets of a type, by the name a form uses, without the type's file prefix. */
	public static List<String> vanillaNames(AssetCatalog.Entry entry, GameAssetIndex index) {
		AssetType type = entry.type();
		List<String> names = new ArrayList<>();
		if (type.isOverride()) {
			return index.existsInGame("init/" + type.initFolder() + "/" + type.fileName(type.fixedName()) + ".txt") ? List.of(type.fixedName()) : names;
		}
		for (String key : index.gameKeysInFolder("init/" + type.initFolder())) {
			String plain = key.startsWith("_") ? key.substring(1) : key;
			if (plain.startsWith(type.filePrefix()) && plain.length() > type.filePrefix().length()) {
				names.add(plain.substring(type.filePrefix().length()));
			}
		}
		return names;
	}

	/** Reads one vanilla asset, or returns null if the game has no asset of that type and name. */
	public static Vanilla vanilla(AssetCatalog.Entry entry, Path gameFolder, GameAssetIndex index, GameParser parser, String name) throws IOException, GameParser.ReadException {
		AssetSchema schema = entry.schema();
		AssetType type = entry.type();
		String file = type.fileName(name);
		List<String> read = new ArrayList<>();
		GameTree.Block data = gameFile(gameFolder, "init/" + type.initFolder() + "/" + file + ".txt", parser, read);
		if (data == null) {
			return null;
		}
		Map<String, String> values = new LinkedHashMap<>();
		Set<String> kept = new HashSet<>();
		for (TagSpec tag : schema.dataTags()) {
			read(tag, data, values, kept);
		}
		GameTree.Block text = null;
		if (type.textFolder() != null) {
			text = gameFile(gameFolder, "text/" + type.textFolder() + "/" + file + ".txt", parser, read);
			if (text != null) {
				GameTree.Block info = infoBlock(text);
				for (TagSpec tag : schema.textTags()) {
					read(tag, isInfoTag(tag) ? info : text, values, kept);
				}
			}
		}
		// In order, because a race's name files are named by its sprite file.
		Map<String, GameTree.Block> companions = new LinkedHashMap<>();
		for (AssetSchema.Companion companion : schema.companions()) {
			String stem = values.get(companion.nameTag());
			GameTree.Block body = stem == null || stem.isBlank() ? null : gameFile(gameFolder, companion.relative(stem), parser, read);
			if (body != null) {
				companions.put(companion.id(), body);
				for (TagSpec tag : companion.tags()) {
					read(tag, body, values, kept);
				}
			}
		}
		if (index.sounds() != null) {
			for (SoundEntries.Slot slot : schema.sounds()) {
				String sound = index.sounds().gameValue(slot, file);
				if (sound != null) {
					values.put(slot.valueKey(), sound);
				}
			}
		}
		return new Vanilla(name, values, keptAsWritten(schema, kept, data, text, companions), read);
	}

	/** A game file, or the same name with a leading _, which the game also accepts (KeyMap.java:42-55). */
	private static GameTree.Block gameFile(Path gameFolder, String relative, GameParser parser, List<String> read) throws IOException, GameParser.ReadException {
		GameTree.Block found = GameAssetIndex.readGameFile(gameFolder, relative, parser);
		String used = relative;
		if (found == null) {
			int slash = relative.lastIndexOf('/');
			used = relative.substring(0, slash + 1) + "_" + relative.substring(slash + 1);
			found = GameAssetIndex.readGameFile(gameFolder, used, parser);
		}
		if (found != null) {
			read.add(used);
		}
		return found;
	}

	/** What the form reads from one companion's file. */
	static Map<String, String> valuesIn(AssetSchema.Companion companion, GameTree.Block tree) {
		Map<String, String> values = new LinkedHashMap<>();
		for (TagSpec tag : companion.tags()) {
			read(tag, tree, values, new HashSet<>());
		}
		return values;
	}

	/**
	 * The tree a new companion starts from. A race's missing appearance types are copies of the first one
	 * (see {@link #generateMissingCompanions}), so the look's tree gets the same copies, and whatever the
	 * form can't show in that type, such as an addon's CONDITIONS, comes with them.
	 */
	static GameTree.Block companionBase(AssetSchema.Companion companion, Sources sources, Map<String, String> values) {
		GameTree.Block base = sources.companions().get(companion.id());
		if (base == null || !"sprite".equals(companion.id()) || !(base.get("TYPES") instanceof GameTree.Blocks types) || types.items().isEmpty()) {
			return base;
		}
		int wanted = 0;
		while (values.containsKey(companion.prefix() + "TYPES." + wanted + ".SPRITE_FILE")) {
			wanted++;
		}
		if (wanted <= types.items().size()) {
			return base;
		}
		List<GameTree.Block> grown = new ArrayList<>(types.items());
		while (grown.size() < wanted) {
			grown.add(types.items().get(0).copy());
		}
		GameTree.Block copy = base.copy();
		copy.put("TYPES", new GameTree.Blocks(grown));
		return copy;
	}

	/**
	 * Fills companion text the appearance never names, from text it does name. A missing appearance type
	 * is a copy of the first type, so each new name file has a type that points at it. A file with nothing
	 * to copy, such as tourists when the appearance has no tourist block, is left out.
	 */
	private static void generateMissingCompanions(AssetSchema schema, String assetName, Map<String, String> values) {
		int source = -1;
		for (int i = 0; i < RaceSchemas.MAX_TYPES; i++) {
			if (values.containsKey("file.sprite.TYPES." + i + ".SPRITE_FILE")) {
				source = i;
				break;
			}
		}
		boolean names = schema.companions().stream().anyMatch(companion -> {
			String text = values.get(companion.prefix() + "NAMES");
			return text != null && !text.isBlank();
		});
		if (source >= 0 && names) {
			String from = "file.sprite.TYPES." + source + ".";
			for (int i = 0; i < RaceSchemas.MAX_TYPES; i++) {
				if (!values.containsKey("file.sprite.TYPES." + i + ".SPRITE_FILE")) {
					copyPrefix(values, from, "file.sprite.TYPES." + i + ".", true);
				}
			}
		}
		for (AssetSchema.Companion companion : schema.companions()) {
			String own = companion.ownStem(assetName);
			if (own == null) {
				continue;
			}
			boolean body = values.keySet().stream().anyMatch(key -> key.startsWith(companion.prefix()));
			if (own.equals(values.get(companion.nameTag())) && body) {
				continue;
			}
			if (!nameParentReady(companion, values) || (!body && !copyCompanionBody(schema, companion, values))) {
				continue;
			}
			values.put(companion.nameTag(), own);
		}
	}

	/** A name tag inside a block that isn't there would write an incomplete block, so that file waits. */
	private static boolean nameParentReady(AssetSchema.Companion companion, Map<String, String> values) {
		String tag = companion.nameTag();
		if (tag.startsWith("file.sprite.TYPES.")) {
			String rest = tag.substring("file.sprite.TYPES.".length());
			int dot = rest.indexOf('.');
			return dot > 0 && values.containsKey("file.sprite.TYPES." + rest.substring(0, dot) + ".SPRITE_FILE");
		}
		if (tag.startsWith("TOURIST.")) {
			return values.containsKey("TOURIST.OCCURENCE") || values.containsKey("TOURIST.CREDITS");
		}
		return true;
	}

	/** Copies words from a file the appearance already has. False when there is nothing to copy. */
	private static boolean copyCompanionBody(AssetSchema schema, AssetSchema.Companion companion, Map<String, String> values) {
		if ("bioSpecific".equals(companion.id())) {
			return copyPrefix(values, "file.bio.", companion.prefix(), false);
		}
		if (companion.tags().size() != 1 || !companion.tags().get(0).key().endsWith(".NAMES")) {
			return false;
		}
		boolean surname = companion.id().contains("Surname");
		boolean first = companion.id().contains("First");
		String preferred = null;
		String any = null;
		for (AssetSchema.Companion other : schema.companions()) {
			if (other.id().equals(companion.id())) {
				continue;
			}
			String names = values.get(other.prefix() + "NAMES");
			if (names == null || names.isBlank()) {
				continue;
			}
			boolean sameKind = surname && other.id().contains("Surname") || first && other.id().contains("First");
			if (sameKind && (preferred == null || other.id().startsWith("names0"))) {
				preferred = names;
			} else if (any == null) {
				any = names;
			}
		}
		String donor = preferred != null ? preferred : any;
		if (donor == null) {
			return false;
		}
		values.put(companion.prefix() + "NAMES", donor);
		return true;
	}

	/** Copies one file's values under another prefix. Name-file tags are skipped when {@code skipNames} is set. */
	private static boolean copyPrefix(Map<String, String> values, String from, String to, boolean skipNames) {
		Map<String, String> copied = new LinkedHashMap<>();
		values.forEach((key, value) -> {
			if (!key.startsWith(from)) {
				return;
			}
			String dest = to + key.substring(from.length());
			if (skipNames && (dest.endsWith("NAMESET_FILE_FIRST") || dest.endsWith("NAMESET_FILE_SURNAME"))) {
				return;
			}
			copied.putIfAbsent(dest, value);
		});
		values.putAll(copied);
		return !copied.isEmpty();
	}

	private static CompanionFile companionFile(AssetSchema.Companion companion, String stem, Path assets, GameAssetIndex index, GameParser parser) {
		if (stem == null || stem.isBlank()) {
			return new CompanionFile(companion.required() ? FilePlace.MISSING : FilePlace.UNUSED, "", null, null);
		}
		String relative = companion.relative(stem);
		Path path = assets.resolve(relative);
		if (index.ownedByMod(relative) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
			try {
				byte[] bytes = Files.readAllBytes(path);
				return new CompanionFile(FilePlace.OWN, stem, tree(parser, path), bytes);
			} catch (IOException | GameParser.ReadException e) {
				return new CompanionFile(FilePlace.OWN, stem, null, null);
			}
		}
		if (index.fileExists(relative)) {
			return new CompanionFile(FilePlace.GAME, stem, index.starter(relative), null);
		}
		return new CompanionFile(FilePlace.MISSING, stem, null, null);
	}

	/**
	 * Values for adding a companion that currently belongs to the game, or is missing. The name tag is
	 * pointed at a new file, and a game file's tags are copied in. Nothing is written until Save.
	 */
	public static Map<String, String> adoptCompanion(AssetEditing.Opened opened, AssetSchema.Companion companion, GameAssetIndex index) {
		Map<String, String> values = new LinkedHashMap<>();
		Set<String> kept = new HashSet<>();
		CompanionFile info = opened.companions().get(companion.id());
		if (info != null && info.tree() != null) {
			for (TagSpec tag : companion.tags()) {
				read(tag, info.tree(), values, kept);
			}
		}
		String own = companion.ownStem(opened.name());
		if (own != null) {
			values.put(companion.nameTag(), own);
		}
		return values;
	}

	private static GameTree.Block tree(GameParser parser, Path file) throws GameParser.ReadException {
		return GameTree.read(parser.root(file));
	}

	/** A comment runs from ** to the end of the line, and only outside quotes (JsonValue.java:436). */
	static boolean hasComments(byte[] bytes) {
		String content = new String(bytes, StandardCharsets.UTF_8);
		boolean quoted = false;
		for (int i = 0; i < content.length(); i++) {
			char c = content.charAt(i);
			if (c == '"') {
				quoted = !quoted;
			} else if (!quoted && c == '*' && i + 1 < content.length() && content.charAt(i + 1) == '*') {
				return true;
			}
		}
		return false;
	}

	/** These are read from the root if it has NAME, and from an INFO block if not (INFO.java:16-17). */
	static GameTree.Block infoBlock(GameTree.Block root) {
		if (!root.has("NAME") && root.get("INFO") instanceof GameTree.Block info) {
			return info;
		}
		return root;
	}

	/** The only tags INFO.java reads, from the root or from an INFO block (INFO.java:16-25). */
	private static final Set<String> INFO_KEYS = AssetCatalog.infoTagsFor().stream().map(TagSpec::key).collect(java.util.stream.Collectors.toUnmodifiableSet());

	/**
	 * Whether a text tag is one of INFO's, and so can be in an INFO block. A loader's other text reads are on the
	 * whole file: RaceInfo reads POSSESSIVE, POSSESSIVES, DESC_LONG and CHALLENGE with text.text (RaceInfo.java:49-53),
	 * and doesn't find them inside INFO. Deciding this by the tag's kind wrote them there, and the game stopped loading.
	 */
	static boolean isInfoTag(TagSpec tag) {
		return INFO_KEYS.contains(tag.key());
	}

	/**
	 * Each kept value as the game's parser reads it, on one line, by its key. The form can't show these, so this is
	 * how a person and an agent see what stays. Keys under file.ID. are in that companion's file.
	 */
	static Map<String, String> keptAsWritten(AssetSchema schema, Set<String> kept, GameTree.Block data, GameTree.Block text, Map<String, GameTree.Block> companions) {
		Map<String, String> out = new java.util.TreeMap<>();
		for (String key : kept) {
			GameTree.Value value = keptValue(schema, key, data, text, companions);
			if (value != null) {
				out.put(key, GameTree.oneLine(value));
			}
		}
		return out;
	}

	private static GameTree.Value keptValue(AssetSchema schema, String key, GameTree.Block data, GameTree.Block text, Map<String, GameTree.Block> companions) {
		String[] parts = key.split("\\.");
		GameTree.Value at;
		int from = 0;
		if (parts[0].equals("file") && parts.length > 2) {
			at = companions.get(parts[1]);
			from = 2;
		} else if (schema.isTextKey(key)) {
			at = text == null ? null : INFO_KEYS.contains(parts[0]) ? infoBlock(text) : text;
		} else {
			at = data;
		}
		for (int i = from; i < parts.length && at != null; i++) {
			if (at instanceof GameTree.Blocks list && parts[i].matches("\\d+")) {
				int item = Integer.parseInt(parts[i]);
				at = item < list.items().size() ? list.items().get(item) : null;
			} else if (at instanceof GameTree.Block block) {
				at = block.get(parts[i]);
			} else {
				at = null;
			}
		}
		return at;
	}

	/** Text tags that are only inside the file's INFO block, where the game doesn't read them. */
	static List<String> misplacedTextTags(AssetSchema schema, GameTree.Block textTree) {
		if (textTree == null || !(textTree.get("INFO") instanceof GameTree.Block info)) {
			return List.of();
		}
		// A play mode's NAME is read at the top as well as through INFO (PBonusSetting.java:56-59). The form still shows
		// it, because INFO finds it inside the block, so the status warns, and a save moves it to the top.
		if (schema.textAtRoot()) {
			return !textTree.has("NAME") && info.has("NAME") ? List.of("NAME") : List.of();
		}
		List<String> misplaced = new ArrayList<>();
		for (TagSpec tag : schema.textTags()) {
			if (!isInfoTag(tag) && !textTree.has(tag.leafName()) && info.has(tag.leafName())) {
				misplaced.add(tag.key());
			}
		}
		return misplaced;
	}

	/** Turns one tag of a file into the form's values, or marks it kept if the form can't show it. */
	private static void read(TagSpec tag, GameTree.Block parent, Map<String, String> values, Set<String> kept) {
		GameTree.Value value = parent.get(tag.leafName());
		if (value == null) {
			return;
		}
		String key = tag.key();
		switch (tag.kind()) {
		case BOOL -> plain(key, value, "true|false", values, kept);
		case COLOR -> {
			// ColorImp reads either RED_GREEN_BLUE or a block of R, G and B. Both are halved (ColorImp.java:110,136, checked with javap),
			// so the same numbers are the same colour, but the block allows up to 511, so it's kept as a block.
			if (value instanceof GameTree.Block block && block.entries().size() == 3
					&& block.get("R") instanceof GameTree.Plain red && block.get("G") instanceof GameTree.Plain green && block.get("B") instanceof GameTree.Plain blue) {
				values.put(key, "{R: " + red.value() + ", G: " + green.value() + ", B: " + blue.value() + ",}");
			} else {
				plain(key, value, "\\d{1,3}_\\d{1,3}_\\d{1,3}", values, kept);
			}
		}
		case COLORS -> {
			// ColorImp.cols reads one block of R, G and B as one colour (ColorImp.java:72-75), halved as RED_GREEN_BLUE is, so it's
			// shown as the same block a single COLOR is. Vanilla race homes write their overwrite colours this way.
			if (value instanceof GameTree.Block block && block.entries().size() == 3
					&& block.get("R") instanceof GameTree.Plain red && block.get("G") instanceof GameTree.Plain green && block.get("B") instanceof GameTree.Plain blue
					&& red.value().matches("\\d{1,3}") && green.value().matches("\\d{1,3}") && blue.value().matches("\\d{1,3}")) {
				values.put(key, "{R: " + red.value() + ", G: " + green.value() + ", B: " + blue.value() + ",}");
			} else if (value instanceof GameTree.Plains list && !list.items().isEmpty() && list.items().stream().allMatch(item -> item.matches("\\d{1,3}_\\d{1,3}_\\d{1,3}"))) {
				values.put(key, String.join(",", list.items()));
			} else {
				plain(key, value, "\\d{1,3}_\\d{1,3}_\\d{1,3}", values, kept);
			}
		}
		case ICON -> {
			if (value instanceof GameTree.Block block && !tag.nested().isEmpty()) {
				for (TagSpec child : tag.nested()) {
					read(child, block, values, kept);
				}
				// Nothing in it could be shown, so the whole block stays as it's written.
				if (values.keySet().stream().noneMatch(entry -> entry.startsWith(key + "."))) {
					kept.add(key);
				}
			} else {
				plain(key, value, ".+->\\d{1,4}", values, kept);
			}
		}
		case INT, DECIMAL -> {
			if (value instanceof GameTree.Plain number && tag.allowsTyping(number.value())) {
				values.put(key, number.value());
			} else {
				kept.add(key);
			}
		}
		case SPRITE, PLAIN, KEY -> plain(key, value, null, values, kept);
		case TEXT_LINE, TEXT_LINES -> {
			if (value instanceof GameTree.Text text) {
				values.put(key, text.text());
			} else {
				kept.add(key);
			}
		}
		case KEY_LIST, LINKED_DECIMALS, DECIMALS -> {
			if (value instanceof GameTree.Plains list) {
				values.put(key, String.join(",", list.items()));
			} else {
				kept.add(key);
			}
		}
		case FRAMES -> {
			if (value instanceof GameTree.Plains frames) {
				values.put(key, String.join("\n", frames.items()));
			} else {
				kept.add(key);
			}
		}
		case TEXT_ROWS -> {
			if (value instanceof GameTree.Texts rows) {
				values.put(key, String.join("\n", rows.items()));
			} else {
				kept.add(key);
			}
		}
		case DECIMAL_MAP -> readMap(tag, value, values, kept);
		case KEYED_LISTS -> readLists(tag, value, values, kept);
		case KEYED_TEXTS -> readTexts(tag, value, values, kept);
		case TEXT_BLOCK -> readTextBlock(tag, value, values, kept);
		case BLOCK -> {
			if (value instanceof GameTree.Block block) {
				for (TagSpec child : tag.nested()) {
					read(child, block, values, kept);
				}
			} else {
				kept.add(key);
			}
		}
		case KEYED_ROWS -> readRows(tag, value, values, kept);
		case KEYED_BLOCKS -> readKeyed(tag, value, values, kept);
		case BLOCK_LIST -> readList(tag, value, values, kept);
		}
	}

	private static void plain(String key, GameTree.Value value, String pattern, Map<String, String> values, Set<String> kept) {
		if (value instanceof GameTree.Plain plain && (pattern == null || plain.value().matches(pattern))) {
			values.put(key, plain.value());
		} else {
			kept.add(key);
		}
	}

	private static void readTexts(TagSpec tag, GameTree.Value value, Map<String, String> values, Set<String> kept) {
		if (!(value instanceof GameTree.Block block)) {
			kept.add(tag.key());
			return;
		}
		for (Map.Entry<String, GameTree.Value> entry : block.entries().entrySet()) {
			List<String> lines = linesOf(entry.getValue());
			if (lines == null) {
				kept.add(tag.key());
				return;
			}
			values.put(tag.key() + "." + entry.getKey(), String.join("\n", lines));
		}
	}

	private static List<String> linesOf(GameTree.Value value) {
		if (value instanceof GameTree.Texts texts) {
			return texts.items();
		}
		if (value instanceof GameTree.Plains plains) {
			return plains.items();
		}
		return null;
	}

	private static void readLists(TagSpec tag, GameTree.Value value, Map<String, String> values, Set<String> kept) {
		if (!(value instanceof GameTree.Block block)) {
			kept.add(tag.key());
			return;
		}
		for (Map.Entry<String, GameTree.Value> entry : block.entries().entrySet()) {
			if (!(entry.getValue() instanceof GameTree.Plains list)) {
				kept.add(tag.key());
				return;
			}
			values.put(tag.key() + "." + entry.getKey(), String.join(",", list.items()));
		}
	}

	private static void readMap(TagSpec tag, GameTree.Value value, Map<String, String> values, Set<String> kept) {
		if (!(value instanceof GameTree.Block block) || !block.entries().values().stream().allMatch(entry -> entry instanceof GameTree.Plain plain && tag.allowsTyping(plain.value()))) {
			kept.add(tag.key());
			return;
		}
		block.entries().forEach((entry, number) -> values.put(tag.key() + "." + entry, ((GameTree.Plain) number).value()));
	}

	private static void readTextBlock(TagSpec tag, GameTree.Value value, Map<String, String> values, Set<String> kept) {
		if (!(value instanceof GameTree.Block block)) {
			kept.add(tag.key());
			return;
		}
		for (TagSpec.SubKey sub : tag.subKeys()) {
			GameTree.Value entry = block.get(sub.key());
			String full = tag.key() + "." + sub.key();
			if (entry instanceof GameTree.Text text && sub.text()) {
				values.put(full, text.text());
			} else if (entry instanceof GameTree.Plain plain && !sub.text()) {
				values.put(full, plain.value());
			} else if (entry != null) {
				kept.add(full);
			}
		}
	}

	/** A plain number stands for the first column, or is the whole entry if it has no named column (Industry.java:123-138). */
	private static void readRows(TagSpec tag, GameTree.Value value, Map<String, String> values, Set<String> kept) {
		if (!(value instanceof GameTree.Block block)) {
			kept.add(tag.key());
			return;
		}
		TagSpec.Column first = tag.shape().columns().get(0);
		block.entries().forEach((row, content) -> {
			String full = tag.key() + "." + row;
			if (content instanceof GameTree.Plain plain) {
				values.put(first.key() == null ? full : full + "." + first.key(), plain.value());
			} else if (content instanceof GameTree.Block entry && first.key() != null) {
				for (TagSpec.Column column : tag.shape().columns()) {
					if (entry.get(column.key()) instanceof GameTree.Plain number) {
						values.put(full + "." + column.key(), number.value());
					}
				}
			} else {
				kept.add(full);
			}
		});
	}

	private static void readKeyed(TagSpec tag, GameTree.Value value, Map<String, String> values, Set<String> kept) {
		if (!(value instanceof GameTree.Block block)) {
			kept.add(tag.key());
			return;
		}
		block.entries().forEach((entry, content) -> {
			if (content instanceof GameTree.Block child) {
				for (TagSpec field : tag.shape().children()) {
					read(field.withKey(tag.key() + "." + entry + "." + field.key()), child, values, kept);
				}
			} else {
				kept.add(tag.key() + "." + entry);
			}
		});
	}

	private static void readList(TagSpec tag, GameTree.Value value, Map<String, String> values, Set<String> kept) {
		int fixed = tag.shape().fixedCount();
		boolean empty = value instanceof GameTree.Plains none && none.items().isEmpty();
		if (empty && fixed == 0) {
			return;
		}
		if (value instanceof GameTree.Plains colours && !colours.items().isEmpty()
				&& colours.items().stream().allMatch(item -> item.matches("\\d{1,3}_\\d{1,3}_\\d{1,3}"))) {
			// ColorImp.cols also accepts colour strings. They're the same colours, written back as blocks.
			for (int i = 0; i < colours.items().size(); i++) {
				String[] parts = colours.items().get(i).split("_");
				values.put(tag.key() + "." + i + ".R", Integer.toString(Integer.parseInt(parts[0])));
				values.put(tag.key() + "." + i + ".G", Integer.toString(Integer.parseInt(parts[1])));
				values.put(tag.key() + "." + i + ".B", Integer.toString(Integer.parseInt(parts[2])));
			}
			return;
		}
		// RoomSpriteImp reads one block or a list (RoomSpriteImp.java:27-37). One block is the first level.
		if (value instanceof GameTree.Block block && tag.shape().children().stream().anyMatch(child -> child.kind() == TagSpec.Kind.FRAMES)) {
			int before = values.size();
			for (TagSpec child : tag.nestedAt(0)) {
				read(child, block, values, kept);
			}
			if (values.size() == before) {
				kept.add(tag.key());
			}
			return;
		}
		if (!(value instanceof GameTree.Blocks blocks) || fixed > 0 && blocks.items().size() != fixed) {
			kept.add(tag.key());
			return;
		}
		int slots = fixed > 0 ? tag.shape().namedSlots() : blocks.items().size();
		for (int i = 0; i < slots; i++) {
			for (TagSpec child : tag.nestedAt(i)) {
				read(child, blocks.items().get(i), values, kept);
			}
		}
	}

	/** Tags the form doesn't offer, in the blocks of a list that's changing length. They can't follow their block, so saving drops them. */
	static List<String> unknownIn(TagSpec tag, GameTree.Blocks blocks) {
		List<String> found = new ArrayList<>();
		for (int i = 0; i < blocks.items().size(); i++) {
			List<String> known = Arrays.asList(tag.shape().children().stream().map(TagSpec::key).toArray(String[]::new));
			for (String key : blocks.items().get(i).entries().keySet()) {
				if (!known.contains(key)) {
					found.add(tag.key() + "." + i + "." + key);
				}
			}
		}
		return found;
	}
}
