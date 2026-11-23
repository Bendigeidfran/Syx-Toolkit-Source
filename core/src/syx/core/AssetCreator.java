package syx.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import syx.core.TagSpec.Kind;

/**
 * Creates a new asset in a mod. It never overwrites anything, and nothing reaches its final place
 * until the game's own parser has read the result back the way a loader would.
 */
public final class AssetCreator {

	/** Says why an asset couldn't be created. Nothing is left behind. */
	public static final class CreationException extends Exception {
		private static final long serialVersionUID = 1L;

		CreationException(String message) {
			super(message);
		}
	}

	/** The files that were created. There's a sound file for each folder with entries to add. */
	public record Created(Path dataFile, Path textFile, List<Path> soundFiles, List<Path> companionFiles) {
		public Created(Path dataFile, Path textFile, List<Path> soundFiles) {
			this(dataFile, textFile, soundFiles, List.of());
		}
	}

	private AssetCreator() {
	}

	/** The folder that the current game version reads from this mod. */
	public static Path versionFolder(Path modRoot, GameParser.GameVersion version) {
		return modRoot.resolve("V" + version.major());
	}

	/** Where the data file will go, relative to the mod's folder. */
	public static String destination(AssetType type, String name, GameParser.GameVersion version) {
		return "V" + version.major() + "/assets/init/" + type.initFolder() + "/" + type.fileName(name) + ".txt";
	}

	/**
	 * Warns if this would make a current version folder for a mod that only has older ones. An exact
	 * match wins (ModInfo.java:49-71), so the game would stop loading the older folder.
	 */
	public static String versionFolderWarning(Path modRoot, GameParser.GameVersion version) {
		if (Files.isDirectory(versionFolder(modRoot, version))) {
			return null;
		}
		List<ModAssets.VersionTree> versions = ModAssets.read(modRoot, version.major()).versions();
		if (versions.isEmpty()) {
			return null;
		}
		return "This mod has no V" + version.major() + " folder yet, so one will be created. The game will then load only that folder, not "
				+ versions.get(0).folderName() + ".";
	}

	/** Returns why a name can't be used, or null if it's fine. */
	public static String problemWithName(AssetType type, String name, GameAssetIndex index) {
		return problemWithName(type, null, name, index);
	}

	public static String problemWithName(AssetType type, AssetSchema schema, String name, GameAssetIndex index) {
		if (type.isOverride()) {
			return problemWithOverride(type, name, index);
		}
		String general = ModCreator.problemWithName(name);
		if (general != null) {
			return general;
		}
		// Our own rule rather than the game's: other punctuation can't be referred to safely in lists.
		if (!name.matches("[A-Za-z0-9_]+")) {
			return "Please use only letters, numbers and _ , so other files can refer to it safely.";
		}
		String file = type.fileName(name);
		if (index.fileExists("init/" + type.initFolder() + "/" + file + ".txt")) {
			return "The game or this mod already has one called " + file + ". A file with that name would change it rather than add a new one.";
		}
		if (type.textFolder() != null && index.fileExists("text/" + type.textFolder() + "/" + file + ".txt")) {
			return "A text file called " + file + " already exists, and won't be overwritten.";
		}
		if ("race".equals(type.initFolder()) && name.startsWith("_")) {
			return "A name starting with _ is skipped by the game, so this race would never load.";
		}
		if (schema != null) {
			for (AssetSchema.Companion companion : schema.companions()) {
				String own = companion.ownStem(name);
				if (own != null && index.fileExists(companion.relative(own))) {
					return "A file called " + own + " already exists in " + companion.folder() + ", and won't be overwritten.";
				}
			}
		}
		return null;
	}

	/** Why an override can't be created under this name, or null. Its name is fixed, and a mod has at most one. */
	private static String problemWithOverride(AssetType type, String name, GameAssetIndex index) {
		String file = type.fileName(type.fixedName());
		if (!type.fixedName().equals(name)) {
			return "A " + type.label() + " is always called " + type.fixedName() + ", because the game only reads " + file + ".txt. A mod can override that file, but can't add another " + type.label() + ".";
		}
		String data = "init/" + type.initFolder() + "/" + file + ".txt";
		if (!index.existsInGame(data)) {
			return "This installation of the game has no " + file + ".txt to override.";
		}
		if (index.inMod(data)) {
			return "This mod already overrides " + file + ".txt. Open it to change it.";
		}
		if (type.textFolder() != null && index.inMod("text/" + type.textFolder() + "/" + file + ".txt")) {
			return "This mod already has a text file called " + file + ", which won't be overwritten. Remove it, or add the data file by hand and open it.";
		}
		return null;
	}

	/**
	 * Creates an override: copies of the game's own files with the form's changes, saved the way an edit is, so what
	 * the form can't show is copied as it's written.
	 */
	private static Created createOverride(Path modRoot, AssetCatalog.Entry entry, Map<String, String> values, GameParser parser, GameAssetIndex index) throws CreationException {
		AssetType type = entry.type();
		GameParser.GameVersion version;
		try {
			version = parser.version();
		} catch (GameParser.UnavailableException e) {
			throw new CreationException(e.getMessage());
		}
		String problem = problemWithOverride(type, type.fixedName(), index);
		if (problem != null) {
			throw new CreationException(problem);
		}
		Path assets = versionFolder(modRoot, version).resolve("assets");
		AssetEditing.Opened opened;
		try {
			opened = AssetEditing.newOverride(entry, assets, index);
		} catch (AssetEditing.OpenException e) {
			throw new CreationException(e.getMessage());
		}
		Saved saved = save(opened, opened.values(), values, false, parser, index);
		return new Created(opened.dataFile(), saved.written().contains(opened.textFile()) ? opened.textFile() : null, List.of());
	}

	/** Creates the data file and its text file, or neither. */
	public static Created create(Path modRoot, AssetType type, AssetSchema schema, String name, Map<String, String> values,
			GameParser parser, GameAssetIndex index) throws CreationException {
		return create(modRoot, type, schema, name, values, null, parser, index);
	}

	/**
	 * Creates an asset. With a look, each file it names starts as a copy of that vanilla file and takes
	 * only what differs from the values read from it, as an edit does, so nothing the form can't show is
	 * lost (AssetEditing.Sources).
	 *
	 * @param appearance the vanilla look a new asset started from, or null
	 */
	public static Created create(Path modRoot, AssetType type, AssetSchema schema, String name, Map<String, String> values, String appearance,
			GameParser parser, GameAssetIndex index) throws CreationException {
		if (type.isOverride()) {
			return createOverride(modRoot, new AssetCatalog.Entry(type, schema, null), values, parser, index);
		}
		AssetEditing.Sources sources = AssetEditing.sources(schema, index, appearance);
		GameParser.GameVersion version;
		try {
			version = parser.version();
		} catch (GameParser.UnavailableException e) {
			throw new CreationException(e.getMessage());
		}
		String nameProblem = problemWithName(type, schema, name, index);
		if (nameProblem != null) {
			throw new CreationException(nameProblem);
		}
		for (AssetChecker.Finding finding : AssetChecker.checkNew(schema, index, values, sources.kept())) {
			if (finding.blocksSaving()) {
				throw new CreationException((finding.tag() == null ? "" : finding.tag() + ": ") + finding.message());
			}
		}

		Path assets = versionFolder(modRoot, version).resolve("assets");
		String file = type.fileName(name);
		Path dataFile = assets.resolve("init").resolve(type.initFolder()).resolve(file + ".txt");
		Path textFile = type.textFolder() == null ? null : assets.resolve("text").resolve(type.textFolder()).resolve(file + ".txt");

		CreatedPaths created = new CreatedPaths();
		Path dataTemp = null;
		Path textTemp = null;
		Map<Path, Path> companionTemps = new LinkedHashMap<>();
		Map<Path, Path> soundTemps = new LinkedHashMap<>();
		Map<Path, byte[]> soundOriginals = new LinkedHashMap<>();
		Map<Path, byte[]> replaced = new LinkedHashMap<>();
		try {
			created.createMissingFolders(dataFile.getParent());
			if (textFile != null) {
				created.createMissingFolders(textFile.getParent());
			}
			// A temp file isn't a .txt, so the game wouldn't list it even if something went badly wrong.
			dataTemp = Files.createTempFile(dataFile.getParent(), name, ".syxtmp");
			Files.write(dataTemp, dataText(schema, values).getBytes(StandardCharsets.UTF_8));
			if (sources.data() != null) {
				mergeOnto(dataTemp, sources.data(), schema.dataTags(), sources.values(), values, parser);
			}
			if (textFile != null) {
				textTemp = Files.createTempFile(textFile.getParent(), name, ".syxtmp");
				// The text file is written from the form. Every tag in a vanilla race's text file is on it (AssetSchemaTest.checkRaceText).
				Files.write(textTemp, textText(schema, values).getBytes(StandardCharsets.UTF_8));
			}

			checkWithGame(parser, dataTemp, dataReads(schema, values), name);
			if (sources.data() != null) {
				verifyMerged(parser, dataTemp, schema.dataTags(), sources.values(), values);
			} else {
				checkNested(parser, dataTemp, schema.dataTags(), values);
			}
			if (textFile != null) {
				checkWithGame(parser, textTemp, textReads(schema, values), name);
				checkNested(parser, textTemp, schema.textTags(), values);
			}
			for (AssetSchema.Companion companion : schema.companions()) {
				String stem = values.get(companion.nameTag());
				boolean body = values.keySet().stream().anyMatch(key -> key.startsWith(companion.prefix()));
				if (stem == null || stem.isBlank() || !companion.required() && !body) {
					continue;
				}
				Path target = assets.resolve(companion.relative(stem));
				if (index.fileExists(companion.relative(stem))) {
					throw new CreationException("A file called " + stem + " already exists in " + companion.folder() + ", and won't be overwritten.");
				}
				created.createMissingFolders(target.getParent());
				Path temp = Files.createTempFile(target.getParent(), name, ".syxtmp");
				companionTemps.put(target, temp);
				Files.write(temp, tagsText(companion.tags(), values).getBytes(StandardCharsets.UTF_8));
				GameTree.Block base = AssetEditing.companionBase(companion, sources, values);
				if (base != null) {
					mergeOnto(temp, base, companion.tags(), sources.values(), values, parser);
					verifyMerged(parser, temp, companion.tags(), sources.values(), values);
				} else {
					checkNested(parser, temp, companion.tags(), values);
				}
			}
			for (Map.Entry<Path, String> sound : soundTexts(schema, file, values, index, assets).entrySet()) {
				Path target = sound.getKey();
				// A file already there may hold other entries, so this one is added after them and nothing is overwritten.
				byte[] original = Files.exists(target) ? Files.readAllBytes(target) : null;
				created.createMissingFolders(target.getParent());
				Path temp = Files.createTempFile(target.getParent(), name, ".syxtmp");
				soundTemps.put(target, temp);
				Files.write(temp, appended(original, sound.getValue()).getBytes(StandardCharsets.UTF_8));
				checkSounds(parser, temp, schema, file, values, index);
				if (original != null) {
					checkEntriesKept(parser, target, temp);
					soundOriginals.put(target, original);
				}
			}

			// Without REPLACE_EXISTING, this refuses to overwrite a file that has just appeared.
			Files.move(dataTemp, dataFile);
			created.add(dataFile);
			dataTemp = null;
			if (textFile != null) {
				Files.move(textTemp, textFile);
				created.add(textFile);
				textTemp = null;
			}
			List<Path> companionFiles = new ArrayList<>(companionTemps.keySet());
			for (Map.Entry<Path, Path> companion : companionTemps.entrySet()) {
				Files.move(companion.getValue(), companion.getKey());
				created.add(companion.getKey());
			}
			companionTemps.clear();
			for (Map.Entry<Path, Path> sound : soundTemps.entrySet()) {
				byte[] original = soundOriginals.get(sound.getKey());
				if (original == null) {
					Files.move(sound.getValue(), sound.getKey());
					created.add(sound.getKey());
				} else {
					SafeWrite.commit(List.of(new SafeWrite.Change(sound.getKey(), sound.getValue(), original)));
					replaced.put(sound.getKey(), original);
				}
			}
			return new Created(dataFile, textFile, List.copyOf(soundTemps.keySet()), companionFiles);
		} catch (FileAlreadyExistsException e) {
			soundTemps.values().forEach(AssetCreator::deleteQuietly);
			companionTemps.values().forEach(AssetCreator::deleteQuietly);
			deleteQuietly(dataTemp);
			deleteQuietly(textTemp);
			restore(replaced);
			created.removeAll();
			throw new CreationException("A file called " + name + " has appeared in the meantime, and hasn't been changed.");
		} catch (IOException e) {
			soundTemps.values().forEach(AssetCreator::deleteQuietly);
			companionTemps.values().forEach(AssetCreator::deleteQuietly);
			deleteQuietly(dataTemp);
			deleteQuietly(textTemp);
			restore(replaced);
			created.removeAll();
			throw new CreationException("The asset couldn't be created: " + e);
		} catch (CreationException e) {
			soundTemps.values().forEach(AssetCreator::deleteQuietly);
			companionTemps.values().forEach(AssetCreator::deleteQuietly);
			deleteQuietly(dataTemp);
			deleteQuietly(textTemp);
			restore(replaced);
			created.removeAll();
			throw e;
		}
	}

	/** Rewrites a new file as its vanilla source with the form's changes taken in, the way an edit is saved. */
	private static void mergeOnto(Path temp, GameTree.Block source, List<TagSpec> tags, Map<String, String> read, Map<String, String> values, GameParser parser)
			throws IOException, CreationException {
		try {
			GameTree.Block fresh = treeOf(parser, temp);
			GameTree.Block merged = source.copy();
			for (TagSpec tag : tags) {
				mergeTag(tag, merged, fresh, read, values);
			}
			if (!merged.equals(fresh)) {
				writeMerged(temp, merged, parser);
			}
		} catch (GameParser.ReadException e) {
			throw new CreationException("The game's parser refused it: " + e.getMessage());
		}
	}

	/**
	 * Reads back what the form changed in a file copied from a look, as a save does. What wasn't changed
	 * is the look's own, in the form it's written in, such as colours written as text (AssetEditing.readList).
	 */
	private static void verifyMerged(GameParser parser, Path temp, List<TagSpec> tags, Map<String, String> read, Map<String, String> values) throws CreationException {
		try {
			GameParser.Node root = parser.root(temp);
			Map<String, String> changed = changedOnly(read, values);
			for (TagSpec tag : tags) {
				verifyTag(root, tag, changed);
			}
		} catch (GameParser.ReadException e) {
			throw new CreationException("The game's parser refused it: " + e.getMessage());
		}
	}

	/** The files that were written. An asset whose files would come out the same writes nothing. */
	public record Saved(List<Path> written) {
	}

	private static Map<String, String> under(String key, Map<String, String> values) {
		Map<String, String> found = new LinkedHashMap<>();
		values.forEach((entry, value) -> {
			if (entry.equals(key) || entry.startsWith(key + ".")) {
				found.put(entry, value);
			}
		});
		return found;
	}

	private static boolean same(String key, Map<String, String> baseline, Map<String, String> values) {
		return under(key, baseline).equals(under(key, values));
	}

	/** How many blocks a list will have, the way the writer counts them. */
	private static int listCount(TagSpec tag, Map<String, String> values) {
		int count = tag.shape().fixedCount();
		if (count == 0) {
			for (String entry : AssetChecker.entriesOf(tag, values).keySet()) {
				count = Math.max(count, Integer.parseInt(entry.substring(0, entry.indexOf('.'))) + 1);
			}
		}
		return count;
	}

	/** Tags the form doesn't offer that saving would lose, because their list is changing length and they can't follow their block. */
	public static List<String> droppedBy(AssetEditing.Opened opened, Map<String, String> baseline, Map<String, String> values) {
		List<String> dropped = new ArrayList<>();
		for (TagSpec tag : opened.schema().dataTags()) {
			collectDropped(tag, opened.dataTree(), baseline, values, dropped);
		}
		if (opened.textTree() != null) {
			for (TagSpec tag : opened.schema().textTags()) {
				collectDropped(tag, opened.textTree(), baseline, values, dropped);
			}
		}
		return dropped;
	}

	private static void collectDropped(TagSpec tag, GameTree.Block parent, Map<String, String> baseline, Map<String, String> values, List<String> dropped) {
		if (same(tag.key(), baseline, values)) {
			return;
		}
		GameTree.Value old = parent.get(tag.leafName());
		if (tag.kind() == Kind.BLOCK && old instanceof GameTree.Block block) {
			for (TagSpec child : tag.nested()) {
				collectDropped(child, block, baseline, values, dropped);
			}
		} else if (tag.kind() == Kind.ICON && old instanceof GameTree.Block block && AssetChecker.entriesOf(tag, values).isEmpty()) {
			// One icon replaces the block, so whatever the form couldn't show goes with it.
			for (String inside : block.entries().keySet()) {
				if (!baseline.containsKey(tag.key() + "." + inside)) {
					dropped.add(tag.key() + "." + inside);
				}
			}
		} else if (tag.kind() == Kind.BLOCK_LIST && old instanceof GameTree.Blocks blocks) {
			if (listCount(tag, values) != blocks.items().size()) {
				dropped.addAll(AssetEditing.unknownIn(tag, blocks));
			} else {
				for (int i = 0; i < blocks.items().size(); i++) {
					for (TagSpec child : tag.nestedAt(i)) {
						collectDropped(child, blocks.items().get(i), baseline, values, dropped);
					}
				}
			}
		}
	}

	/**
	 * Takes one changed tag from the form's version of a file. Anything unchanged keeps the form it was
	 * written in, and anything the form doesn't know about stays, so only what was edited moves.
	 */
	private static void mergeTag(TagSpec tag, GameTree.Block out, GameTree.Block fresh, Map<String, String> baseline, Map<String, String> values) {
		if (same(tag.key(), baseline, values)) {
			return;
		}
		String leaf = tag.leafName();
		GameTree.Value old = out.get(leaf);
		GameTree.Value now = fresh.get(leaf);
		if ((tag.kind() == Kind.BLOCK && old instanceof GameTree.Block && (now == null || now instanceof GameTree.Block))
				|| (tag.kind() == Kind.ICON && old instanceof GameTree.Block && now instanceof GameTree.Block)) {
			GameTree.Block block = (GameTree.Block) old;
			GameTree.Block merged = block.copy();
			GameTree.Block from = now == null ? GameTree.Block.empty() : (GameTree.Block) now;
			for (TagSpec child : tag.nested()) {
				mergeTag(child, merged, from, baseline, values);
			}
			putOrRemove(out, leaf, merged);
		} else if (tag.kind() == Kind.TEXT_BLOCK && old instanceof GameTree.Block block && (now == null || now instanceof GameTree.Block)) {
			GameTree.Block merged = block.copy();
			GameTree.Block from = now == null ? GameTree.Block.empty() : (GameTree.Block) now;
			for (TagSpec.SubKey sub : tag.subKeys()) {
				if (!same(tag.key() + "." + sub.key(), baseline, values)) {
					take(merged, from, sub.key());
				}
			}
			putOrRemove(out, leaf, merged);
		} else if (tag.kind() == Kind.KEYED_BLOCKS && old instanceof GameTree.Block block && (now == null || now instanceof GameTree.Block)) {
			GameTree.Block merged = block.copy();
			GameTree.Block from = now == null ? GameTree.Block.empty() : (GameTree.Block) now;
			Map<String, Map<String, String>> before = AssetChecker.rowsOf(tag, baseline);
			Map<String, Map<String, String>> after = AssetChecker.rowsOf(tag, values);
			before.keySet().stream().filter(row -> !after.containsKey(row)).forEach(merged::remove);
			after.keySet().forEach(row -> {
				if (!after.get(row).equals(before.get(row))) {
					GameTree.Block item = merged.get(row) instanceof GameTree.Block existing ? existing.copy() : GameTree.Block.empty();
					GameTree.Block freshRow = from.get(row) instanceof GameTree.Block written ? written : GameTree.Block.empty();
					for (TagSpec child : tag.shape().children()) {
						mergeTag(child.withKey(tag.key() + "." + row + "." + child.key()), item, freshRow, baseline, values);
					}
					merged.put(row, item);
				}
			});
			putOrRemove(out, leaf, wildcardsFirst(tag, merged, after.keySet().stream().filter(row -> !block.has(row)).toList()));
		} else if (tag.kind() == Kind.KEYED_ROWS && old instanceof GameTree.Block block && (now == null || now instanceof GameTree.Block)) {
			GameTree.Block merged = block.copy();
			GameTree.Block from = now == null ? GameTree.Block.empty() : (GameTree.Block) now;
			Map<String, Map<String, String>> before = AssetChecker.rowsOf(tag, baseline);
			Map<String, Map<String, String>> after = AssetChecker.rowsOf(tag, values);
			before.keySet().stream().filter(row -> !after.containsKey(row)).forEach(merged::remove);
			after.forEach((row, columns) -> {
				if (!columns.equals(before.get(row))) {
					mergeRow(tag, row, merged, from, columns);
				}
			});
			putOrRemove(out, leaf, merged);
		} else if (tag.kind() == Kind.BLOCK_LIST && old instanceof GameTree.Blocks blocks && now instanceof GameTree.Blocks newBlocks
				&& blocks.items().size() == newBlocks.items().size()) {
			List<GameTree.Block> merged = new ArrayList<>();
			for (int i = 0; i < blocks.items().size(); i++) {
				GameTree.Block item = blocks.items().get(i).copy();
				for (TagSpec child : tag.nestedAt(i)) {
					mergeTag(child, item, newBlocks.items().get(i), baseline, values);
				}
				merged.add(item);
			}
			out.put(leaf, new GameTree.Blocks(merged));
		} else {
			take(out, fresh, leaf);
		}
	}

	/** Blocks whose entries the game applies in order, with a * name standing for many entries (StatsJson.java:31-47). */
	private static final Set<String> IN_ORDER_WILDCARDS = Set.of("STATS");

	/**
	 * Puts newly added wildcard rows before the others. The game applies a race's STATS entries in order, and
	 * each one replaces a stat's whole standing (RaceStats.java:57-63), so a wildcard added at the end would
	 * wipe out every specific entry before it. First, it's a default the specific entries refine, as vanilla
	 * GARTHIMI writes ACCESS*. Rows already in the file keep their place, so an order someone chose stays.
	 */
	public static GameTree.Block wildcardsFirst(TagSpec tag, GameTree.Block block, List<String> added) {
		if (!IN_ORDER_WILDCARDS.contains(tag.key())) {
			return block;
		}
		List<String> wildcards = added.stream().filter(row -> row.contains("*") && block.has(row)).toList();
		if (wildcards.isEmpty()) {
			return block;
		}
		GameTree.Block ordered = GameTree.Block.empty();
		wildcards.forEach(row -> ordered.put(row, block.get(row)));
		block.entries().forEach((row, value) -> {
			if (!ordered.has(row)) {
				ordered.put(row, value);
			}
		});
		return ordered;
	}

	/** The rows of a keyed block in the order they're written: new wildcards of an in-order block first, then the rest as given. */
	private static List<String> writtenOrder(TagSpec tag, Map<String, Map<String, String>> rows) {
		if (!IN_ORDER_WILDCARDS.contains(tag.key())) {
			return new ArrayList<>(rows.keySet());
		}
		List<String> order = new ArrayList<>(rows.keySet().stream().filter(row -> row.contains("*")).toList());
		rows.keySet().stream().filter(row -> !row.contains("*")).forEach(order::add);
		return order;
	}

	/** A row's other tags stay. A row written as one number becomes a block, if that's how the form writes it. */
	private static void mergeRow(TagSpec tag, String row, GameTree.Block merged, GameTree.Block from, Map<String, String> columns) {
		if (merged.get(row) instanceof GameTree.Block oldRow && from.get(row) instanceof GameTree.Block newRow) {
			GameTree.Block item = oldRow.copy();
			for (TagSpec.Column column : tag.shape().columns()) {
				if (column.key() != null) {
					if (columns.containsKey(column.key())) {
						item.put(column.key(), newRow.get(column.key()));
					} else {
						item.remove(column.key());
					}
				}
			}
			merged.put(row, item);
		} else {
			take(merged, from, row);
		}
	}

	private static void take(GameTree.Block out, GameTree.Block from, String key) {
		if (from.has(key)) {
			out.put(key, from.get(key));
		} else {
			out.remove(key);
		}
	}

	private static void putOrRemove(GameTree.Block out, String key, GameTree.Block block) {
		if (block.entries().isEmpty()) {
			out.remove(key);
		} else {
			out.put(key, block);
		}
	}

	/**
	 * INFO's tags stay in whichever form the file has them, unless the type needs them at the top. Then an INFO
	 * block is taken apart into the top, which is what the game needs (PBonusSetting.java:56-59).
	 */
	private static GameTree.Block mergeText(AssetSchema schema, GameTree.Block old, GameTree.Block fresh, Map<String, String> baseline, Map<String, String> values) {
		if (schema.textAtRoot() && !old.has("NAME") && old.get("INFO") instanceof GameTree.Block wrapped) {
			GameTree.Block hoisted = GameTree.Block.empty();
			wrapped.entries().forEach(hoisted::put);
			old.entries().forEach((key, value) -> {
				if (!key.equals("INFO") && !hoisted.has(key)) {
					hoisted.put(key, value);
				}
			});
			old = hoisted;
		}
		GameTree.Block out = old.copy();
		boolean atRoot = schema.textAtRoot() || AssetEditing.infoBlock(old) == old && old.has("NAME");
		GameTree.Block info = atRoot ? out : old.get("INFO") instanceof GameTree.Block block ? block.copy() : GameTree.Block.empty();
		// The flat writer puts INFO's tags at the top of the fresh file when the type needs them there.
		GameTree.Block freshInfo = schema.textAtRoot() ? fresh : fresh.get("INFO") instanceof GameTree.Block block ? block : GameTree.Block.empty();
		for (TagSpec tag : schema.textTags()) {
			if (AssetEditing.isInfoTag(tag)) {
				mergeTag(tag, info, freshInfo, baseline, values);
			} else {
				mergeTag(tag, out, fresh, baseline, values);
				// A copy inside INFO is never read (RaceInfo.java:49-53), so once the tag is written at the top it goes.
				if (!atRoot && out.has(tag.leafName())) {
					info.remove(tag.leafName());
				}
			}
		}
		if (!atRoot && (old.has("INFO") || !info.entries().isEmpty())) {
			out.put("INFO", info);
		}
		return out;
	}

	/** Reads what was changed back with the game's own methods, and with the limits its loaders use. */
	private static void verifyTag(GameParser.Node node, TagSpec tag, Map<String, String> changed) throws GameParser.ReadException, CreationException {
		if (under(tag.key(), changed).isEmpty()) {
			return;
		}
		String leaf = tag.leafName();
		switch (tag.kind()) {
		case BLOCK -> {
			if (tag.nested().stream().anyMatch(child -> !under(child.key(), changed).isEmpty())) {
				GameParser.Node block = node.block(leaf);
				for (TagSpec child : tag.nested()) {
					verifyTag(block, child, changed);
				}
			}
		}
		case BLOCK_LIST -> {
			List<GameParser.Node> blocks = null;
			int count = listCount(tag, changed);
			for (int i = 0; i < count; i++) {
				for (TagSpec child : tag.nestedAt(i)) {
					if (!under(child.key(), changed).isEmpty()) {
						blocks = blocks == null ? node.blocks(leaf) : blocks;
						verifyTag(blocks.get(i), child, changed);
					}
				}
			}
		}
		case KEY_LIST, LINKED_DECIMALS -> {
			String value = changed.get(tag.key());
			if (value != null && !value.isEmpty()) {
				expect(tag, split(value), node.values(leaf));
			}
		}
		case DECIMAL_MAP, KEYED_LISTS, KEYED_TEXTS -> readBack(node, tag, changed);
		case TEXT_BLOCK -> {
			GameParser.Node block = node.block(leaf);
			Map<String, String> entries = AssetChecker.entriesOf(tag, changed);
			for (TagSpec.SubKey sub : tag.subKeys()) {
				String value = entries.get(sub.key());
				if (value != null) {
					expect(tag, value, sub.text() ? block.text(sub.key()) : block.value(sub.key()));
				}
			}
		}
		default -> readBack(node, tag, changed);
		}
	}

	private static GameTree.Block treeOf(GameParser parser, Path file) throws GameParser.ReadException {
		return GameTree.read(parser.root(file));
	}

	private static void writeText(Path file, String text) throws IOException {
		Files.write(file, text.getBytes(StandardCharsets.UTF_8));
	}

	private static void refuseIfChanged(Path file, byte[] original) throws CreationException, IOException {
		boolean exists = Files.exists(file);
		boolean unchanged = original == null ? !exists : exists && Arrays.equals(original, Files.readAllBytes(file));
		if (!unchanged) {
			throw new CreationException(file.getFileName() + " has changed since it was opened, so nothing was saved. Please open the asset again, so nothing is lost.");
		}
	}

	/**
	 * Saves an asset that already exists over its own files, and creates any of its files that are
	 * missing and have been asked for. The result must read back as intended, and what the form
	 * doesn't know about must still be there, or nothing changes.
	 *
	 * @param baseline what the form held when it was opened, because only what differs from it is changed
	 * @param acceptDropped whether the user has agreed to lose what {@link #droppedBy} lists
	 */
	public static Saved save(AssetEditing.Opened opened, Map<String, String> baseline, Map<String, String> values, boolean acceptDropped,
			GameParser parser, GameAssetIndex index) throws CreationException {
		AssetSchema schema = opened.schema();
		for (AssetChecker.Finding finding : AssetChecker.check(schema, index, values, opened.scope(values, opened.kept()))) {
			if (finding.blocksSaving()) {
				throw new CreationException((finding.tag() == null ? "" : finding.tag() + ": ") + finding.message());
			}
		}
		List<String> dropped = droppedBy(opened, baseline, values);
		if (!dropped.isEmpty() && !acceptDropped) {
			throw new CreationException("Saving would lose " + String.join(", ", dropped) + ", so nothing was saved.");
		}
		Map<String, String> changed = new LinkedHashMap<>(values);
		changed.entrySet().removeIf(entry -> entry.getValue().equals(baseline.get(entry.getKey())));

		CreatedPaths created = new CreatedPaths();
		List<Path> temps = new ArrayList<>();
		List<SafeWrite.Change> changes = new ArrayList<>();
		String name = opened.name();
		boolean finished = false;
		try {
			refuseIfChanged(opened.dataFile(), opened.dataBytes());
			if (opened.textFile() != null) {
				refuseIfChanged(opened.textFile(), opened.textBytes());
			}
			for (Map.Entry<Path, byte[]> sound : opened.soundFiles().entrySet()) {
				refuseIfChanged(sound.getKey(), sound.getValue());
			}

			AssetEditing.Overlay overlay = opened.overlay();
			if (overlay != null) {
				created.createMissingFolders(opened.dataFile().getParent());
			}
			Path dataTemp = tempBeside(opened.dataFile(), name, temps);
			writeText(dataTemp, dataText(schema, values));
			GameTree.Block fresh = treeOf(parser, dataTemp);
			GameTree.Block merged = opened.dataTree().copy();
			for (TagSpec tag : schema.dataTags()) {
				mergeTag(tag, merged, fresh, baseline, values);
			}
			if (overlay != null) {
				saveOverride(opened, merged, opened.dataTree(), overlay.ownData(), overlay.gameData(), opened.dataFile(), opened.dataBytes(), dataTemp, schema.dataTags(), changed, parser, temps, changes);
			} else if (!merged.equals(opened.dataTree())) {
				writeMerged(dataTemp, merged, parser);
				GameParser.Node root = parser.root(dataTemp);
				for (TagSpec tag : schema.dataTags()) {
					verifyTag(root, tag, changed);
				}
				changes.add(new SafeWrite.Change(opened.dataFile(), dataTemp, opened.dataBytes()));
			}

			if (overlay != null) {
				created.createMissingFolders(opened.textFile().getParent());
				Path textTemp = tempBeside(opened.textFile(), name, temps);
				writeText(textTemp, textText(schema, values));
				GameTree.Block mergedText = mergeText(schema, opened.textTree(), treeOf(parser, textTemp), baseline, values);
				saveOverride(opened, mergedText, opened.textTree(), overlay.ownText(), overlay.gameText(), opened.textFile(), opened.textBytes(), textTemp, schema.textTags(), changed, parser, temps, changes);
			} else if (opened.hasTextFile() || AssetEditing.hasTextValues(schema, values)) {
				created.createMissingFolders(opened.textFile().getParent());
				Path textTemp = tempBeside(opened.textFile(), name, temps);
				writeText(textTemp, textText(schema, values));
				GameTree.Block freshText = treeOf(parser, textTemp);
				GameTree.Block old = opened.hasTextFile() ? opened.textTree() : GameTree.Block.empty();
				GameTree.Block mergedText = mergeText(schema, old, freshText, opened.hasTextFile() ? baseline : Map.of(), values);
				if (!opened.hasTextFile() || !mergedText.equals(old)) {
					if (opened.hasTextFile()) {
						writeMerged(textTemp, mergedText, parser);
					}
					GameParser.Node root = parser.root(textTemp);
					GameParser.Node info = root.has("NAME") ? root : root.has("INFO") ? root.block("INFO") : root;
					for (TagSpec tag : schema.textTags()) {
						verifyTag(AssetEditing.isInfoTag(tag) ? info : root, tag, opened.hasTextFile() ? changed : values);
					}
					changes.add(new SafeWrite.Change(opened.textFile(), textTemp, opened.textBytes()));
				}
			}

			saveSounds(opened, baseline, values, parser, created, temps, changes);
			saveCompanions(opened, baseline, values, parser, created, temps, changes);
			SafeWrite.commit(changes);
			finished = true;
			return new Saved(changes.stream().map(SafeWrite.Change::target).toList());
		} catch (GameParser.ReadException e) {
			throw new CreationException("The game's parser refused it, so nothing was saved: " + e.getMessage());
		} catch (IOException e) {
			throw new CreationException("The asset couldn't be saved, and hasn't been changed: " + e);
		} finally {
			temps.forEach(AssetCreator::deleteQuietly);
			if (!finished) {
				created.removeAll();
			}
		}
	}

	/**
	 * Writes one file of an override. The game reads the mod's file merged over its own (Json.java:29-42), replacing each
	 * top-level tag the mod writes and keeping the game's value for every other (JsonValue.java:323-339). So the file keeps
	 * the tags it already had, gains any that now differ from the game's, and is written only if that changes it. A new
	 * override's own tree is a whole copy of the game's, so its files are written in full.
	 *
	 * @param merged the file the game should end up reading, with the form's changes
	 * @param before what the game read when the asset was opened
	 * @param own the mod's file as it was, or empty if there wasn't one
	 * @param original the mod's file's bytes, or null if it doesn't exist yet
	 */
	private static void saveOverride(AssetEditing.Opened opened, GameTree.Block merged, GameTree.Block before, GameTree.Block own, GameTree.Block game, Path target, byte[] original,
			Path temp, List<TagSpec> tags, Map<String, String> changed, GameParser parser, List<Path> temps, List<SafeWrite.Change> changes)
			throws IOException, GameParser.ReadException, CreationException {
		GameTree.Block written = GameTree.Block.empty();
		merged.entries().forEach((key, value) -> {
			if (own.has(key) || !value.equals(game.get(key))) {
				written.put(key, value);
			}
		});
		if (!AssetEditing.overlaid(game, written).equals(merged)) {
			List<String> lost = game.entries().keySet().stream().filter(key -> !merged.has(key)).toList();
			throw new CreationException(String.join(", ", lost) + " can't be removed from " + target.getFileName() + ": the game keeps the value in its own file whenever this one leaves it out (Json.java:29-42), so nothing was saved. Change the value instead.");
		}
		boolean exists = original != null;
		if (exists ? written.equals(own) : written.entries().isEmpty() && merged.equals(before)) {
			return;
		}
		writeMerged(temp, written, parser);
		// The changed values are checked in what the game will read, which holds them even where they now match the game's.
		Path check = tempBeside(target, opened.name(), temps);
		writeMerged(check, merged, parser);
		GameParser.Node root = parser.root(check);
		boolean text = target.equals(opened.textFile());
		GameParser.Node info = text ? (root.has("NAME") ? root : root.has("INFO") ? root.block("INFO") : root) : root;
		for (TagSpec tag : tags) {
			verifyTag(text && AssetEditing.isInfoTag(tag) ? info : root, tag, changed);
		}
		changes.add(new SafeWrite.Change(target, temp, original));
	}

	private static Path tempBeside(Path file, String name, List<Path> temps) throws IOException {
		// A temp file isn't a .txt, so the game wouldn't list it even if something went badly wrong.
		Path temp = Files.createTempFile(file.getParent(), name, ".syxtmp");
		temps.add(temp);
		return temp;
	}

	/** The game's parser must read exactly what was meant, or this is refused rather than changing what the file does. */
	private static void writeMerged(Path temp, GameTree.Block merged, GameParser parser) throws IOException, GameParser.ReadException, CreationException {
		writeText(temp, GameTree.write(merged));
		if (!treeOf(parser, temp).equals(merged)) {
			throw new CreationException("The game would read the rewritten file differently, so nothing was saved. Text containing quotes or brackets can cause this.");
		}
	}

	/** Adds what's missing and changes what was edited in the asset's own sound files. Every other entry in them stays. */
	private static void saveCompanions(AssetEditing.Opened opened, Map<String, String> baseline, Map<String, String> values, GameParser parser,
			CreatedPaths created, List<Path> temps, List<SafeWrite.Change> changes) throws CreationException, IOException, GameParser.ReadException {
		AssetSchema schema = opened.schema();
		AssetChecker.Scope scope = opened.scope(values, opened.kept());
		for (AssetSchema.Companion companion : schema.companions()) {
			if (!scope.companions().contains(companion.id())) {
				continue;
			}
			String stem = values.get(companion.nameTag());
			if (stem == null || stem.isBlank()) {
				continue;
			}
			AssetEditing.CompanionFile info = opened.companions().get(companion.id());
			boolean own = info != null && info.place() == AssetEditing.FilePlace.OWN && stem.equals(info.stem());
			// A file the game already has must be copied under this asset's name. The same name would merge with it.
			if (info != null && info.place() == AssetEditing.FilePlace.GAME && stem.equals(info.stem())) {
				throw new CreationException(companion.label() + " is the game's " + stem + ". Adding it copies it into this mod under this asset's own name, so nothing was saved.");
			}
			Path target = opened.assets().resolve(companion.relative(stem));
			if (!own && Files.exists(target)) {
				throw new CreationException("A file called " + stem + " already exists in " + companion.folder() + ", and won't be overwritten.");
			}
			if (own) {
				refuseIfChanged(target, info.bytes());
			}
			created.createMissingFolders(target.getParent());
			Path temp = tempBeside(target, opened.name(), temps);
			writeText(temp, tagsText(companion.tags(), values));
			GameTree.Block fresh = treeOf(parser, temp);
			// A copy of the game's file starts as that file, so what the form can't show in it isn't lost.
			boolean copied = !own && info != null && info.place() == AssetEditing.FilePlace.GAME && info.tree() != null;
			GameTree.Block old = (own || copied) && info.tree() != null ? info.tree() : GameTree.Block.empty();
			Map<String, String> from = own ? baseline : copied ? AssetEditing.valuesIn(companion, info.tree()) : Map.of();
			GameTree.Block merged = old.copy();
			for (TagSpec tag : companion.tags()) {
				mergeTag(tag, merged, fresh, from, values);
			}
			if (own && merged.equals(old)) {
				continue;
			}
			if (!merged.equals(fresh)) {
				writeMerged(temp, merged, parser);
			}
			GameParser.Node root = parser.root(temp);
			Map<String, String> check = own ? changedOnly(baseline, values) : values;
			for (TagSpec tag : companion.tags()) {
				verifyTag(root, tag, check);
			}
			changes.add(new SafeWrite.Change(target, temp, own ? info.bytes() : null));
		}
	}

	private static Map<String, String> changedOnly(Map<String, String> baseline, Map<String, String> values) {
		Map<String, String> changed = new LinkedHashMap<>(values);
		changed.entrySet().removeIf(entry -> entry.getValue().equals(baseline.get(entry.getKey())));
		return changed;
	}

	private static void saveSounds(AssetEditing.Opened opened, Map<String, String> baseline, Map<String, String> values, GameParser parser,
			CreatedPaths created, List<Path> temps, List<SafeWrite.Change> changes) throws IOException, GameParser.ReadException, CreationException {
		Map<Path, List<SoundEntries.Slot>> byFile = new LinkedHashMap<>();
		for (SoundEntries.Slot slot : opened.schema().sounds()) {
			String value = values.get(slot.valueKey());
			SoundEntries.State state = opened.sounds().get(slot).state();
			boolean edited = state == SoundEntries.State.OWN_FILE && value != null && !value.equals(baseline.get(slot.valueKey()));
			if (edited || state == SoundEntries.State.MISSING && value != null) {
				byFile.computeIfAbsent(opened.soundFile(slot), file -> new ArrayList<>()).add(slot);
			}
		}
		for (Map.Entry<Path, List<SoundEntries.Slot>> entry : byFile.entrySet()) {
			Path target = entry.getKey();
			byte[] original = opened.soundFiles().get(target);
			GameTree.Block before = original == null ? GameTree.Block.empty() : treeOf(parser, target);
			GameTree.Block expected = before.copy();
			boolean replacing = false;
			StringBuilder additions = new StringBuilder();
			for (SoundEntries.Slot slot : entry.getValue()) {
				String value = values.get(slot.valueKey());
				String[] sounds = split(value);
				String key = slot.key(opened.fileName());
				replacing |= before.has(key);
				expected.put(key, sounds.length == 1 ? new GameTree.Plain(sounds[0]) : new GameTree.Plains(List.of(sounds)));
				additions.append(soundEntry(key, value));
			}
			created.createMissingFolders(target.getParent());
			Path temp = tempBeside(target, opened.name(), temps);
			// Adding to the end leaves what's there exactly as it was, comments included.
			writeText(temp, replacing ? GameTree.write(expected) : appended(original, additions.toString()));
			if (!treeOf(parser, temp).equals(expected)) {
				throw new CreationException("The game would read " + target.getFileName() + " differently from what was chosen, so nothing was saved.");
			}
			changes.add(new SafeWrite.Change(target, temp, original));
		}
	}

	private static String soundEntry(String key, String value) {
		String[] sounds = split(value);
		return sounds.length == 1 ? key + ": " + sounds[0] + ",\n" : key + ": [\n\t" + String.join(",\n\t", sounds) + ",\n],\n";
	}

	private static String appended(byte[] original, String addition) {
		if (original == null) {
			return addition;
		}
		String existing = new String(original, StandardCharsets.UTF_8);
		return existing.isEmpty() || existing.endsWith("\n") ? existing + addition : existing + "\n" + addition;
	}

	/** An existing sound file's entries must all survive having one added. */
	private static void checkEntriesKept(GameParser parser, Path existing, Path temp) throws CreationException {
		try {
			GameTree.Block after = treeOf(parser, temp);
			for (Map.Entry<String, GameTree.Value> entry : treeOf(parser, existing).entries().entrySet()) {
				if (!entry.getValue().equals(after.get(entry.getKey()))) {
					throw new CreationException("Adding to " + existing.getFileName() + " would change its entry " + entry.getKey() + ", so nothing was saved.");
				}
			}
		} catch (GameParser.ReadException e) {
			throw new CreationException("The game's parser refused the sound file: " + e.getMessage());
		}
	}

	private static void restore(Map<Path, byte[]> replaced) {
		replaced.forEach((file, original) -> {
			try {
				Files.write(file, original);
			} catch (IOException e) {
				// Nothing more can be done here, and the caller is already reporting a failure.
			}
		});
	}

	/** Whether an asset's sound goes in its file. An entry the game or mod already has isn't repeated. */
	private static boolean writesSound(SoundEntries.Slot slot, String file, Map<String, String> values, GameAssetIndex index) {
		return values.get(slot.valueKey()) != null && (index.sounds() == null || !index.sounds().has(slot, file));
	}

	/** The text of each sound file, by where it goes. Each is the asset's own, so it never touches another file. */
	private static Map<Path, String> soundTexts(AssetSchema schema, String file, Map<String, String> values, GameAssetIndex index, Path assets) {
		Map<Path, String> texts = new LinkedHashMap<>();
		for (SoundEntries.Slot slot : schema.sounds()) {
			if (writesSound(slot, file, values, index)) {
				texts.merge(assets.resolve(slot.relativeFile(file)), soundEntry(slot.key(file), values.get(slot.valueKey())), String::concat);
			}
		}
		return texts;
	}

	/** The game's parser must read back what was chosen, whether it's one sound or several. */
	private static void checkSounds(GameParser parser, Path temp, AssetSchema schema, String file, Map<String, String> values, GameAssetIndex index) throws CreationException {
		try {
			GameParser.Node node = parser.root(temp);
			for (SoundEntries.Slot slot : schema.sounds()) {
				String key = slot.key(file);
				if (writesSound(slot, file, values, index) && node.has(key)) {
					String[] read = node.isList(key) ? node.values(key) : new String[] { node.value(key) };
					if (!Arrays.equals(read, split(values.get(slot.valueKey())))) {
						throw new CreationException("The game would read " + key + " differently from what was chosen, so nothing was saved.");
					}
				}
			}
		} catch (GameParser.ReadException e) {
			throw new CreationException("The game's parser refused the sound file: " + e.getMessage());
		}
	}

	private static void deleteQuietly(Path temp) {
		if (temp != null) {
			try {
				Files.deleteIfExists(temp);
			} catch (IOException e) {
				// A leftover .syxtmp file is harmless, because the game only lists .txt files.
			}
		}
	}

	private static boolean isNested(TagSpec tag) {
		return switch (tag.kind()) {
		case BLOCK, KEYED_ROWS, KEYED_BLOCKS, BLOCK_LIST, TEXT_ROWS, DECIMALS, KEY -> true;
		default -> false;
		};
	}

	/** True if an icon's values are for a block, rather than for one icon. */
	private static boolean isIconBlock(TagSpec tag, Map<String, String> values) {
		return tag.kind() == Kind.ICON && !AssetChecker.entriesOf(tag, values).isEmpty();
	}

	/** Reads nested tags back with the game's own methods, and with the limits its loaders use. */
	private static void checkNested(GameParser parser, Path file, List<TagSpec> tags, Map<String, String> values) throws CreationException {
		try {
			GameParser.Node root = parser.root(file);
			for (TagSpec tag : tags) {
				if (isNested(tag) || isIconBlock(tag, values)) {
					readBack(root, tag, values);
				}
			}
		} catch (GameParser.ReadException e) {
			throw new CreationException("The game's parser refused it: " + e.getMessage());
		}
	}

	private static void readBack(GameParser.Node node, TagSpec tag, Map<String, String> values) throws GameParser.ReadException, CreationException {
		String leaf = tag.leafName();
		String value = values.get(tag.key());
		switch (tag.kind()) {
		case BLOCK -> {
			if (tag.required() || !AssetChecker.entriesOf(tag, values).isEmpty()) {
				GameParser.Node block = node.block(leaf);
				for (TagSpec child : tag.nested()) {
					readBack(block, child, values);
				}
			}
		}
		case KEYED_BLOCKS -> {
			Map<String, Map<String, String>> rows = AssetChecker.rowsOf(tag, values);
			if (!rows.isEmpty()) {
				GameParser.Node block = node.block(leaf);
				for (String row : rows.keySet()) {
					GameParser.Node entry = block.block(row);
					for (TagSpec child : tag.shape().children()) {
						readBack(entry, child.withKey(tag.key() + "." + row + "." + child.key()), values);
					}
				}
			}
		}
		case KEYED_ROWS -> {
			Map<String, Map<String, String>> rows = AssetChecker.rowsOf(tag, values);
			if (!rows.isEmpty()) {
				GameParser.Node block = node.block(leaf);
				for (Map.Entry<String, Map<String, String>> row : rows.entrySet()) {
					boolean plain = tag.shape().columns().size() == 1 && tag.shape().columns().get(0).key() == null;
					GameParser.Node entry = plain ? null : block.block(row.getKey());
					for (TagSpec.Column column : tag.shape().columns()) {
						String entered = row.getValue().get(column.key() == null ? "" : column.key());
						if (entered != null) {
							double read = plain ? block.decimal(row.getKey(), column.min(), column.max()) : entry.decimal(column.key(), column.min(), column.max());
							expect(tag, Double.parseDouble(entered), read);
						}
					}
				}
			}
		}
		case BLOCK_LIST -> {
			int count = tag.shape().fixedCount();
			int slots = tag.shape().namedSlots();
			if (count == 0) {
				for (String entry : AssetChecker.entriesOf(tag, values).keySet()) {
					count = Math.max(count, Integer.parseInt(entry.substring(0, entry.indexOf('.'))) + 1);
				}
				slots = count;
			}
			if (count > 0) {
				List<GameParser.Node> blocks = node.blocks(leaf);
				if (blocks.size() != count) {
					throw new CreationException("The game would read " + blocks.size() + " blocks in " + tag.key() + ", not " + count + ", so nothing was saved.");
				}
				for (int i = 0; i < slots; i++) {
					for (TagSpec child : tag.nestedAt(i)) {
						readBack(blocks.get(i), child, values);
					}
				}
			}
		}
		case ICON -> {
			if (isIconBlock(tag, values)) {
				GameParser.Node block = node.block(leaf);
				for (TagSpec child : tag.nested()) {
					readBack(block, child, values);
				}
			} else if (value != null) {
				expect(tag, value, node.value(leaf));
			}
		}
		case COLORS -> {
			if (value != null) {
				if (value.startsWith("{")) {
					GameParser.Node block = node.block(leaf);
					String read = "{R: " + block.integer("R", 0, 511) + ", G: " + block.integer("G", 0, 511) + ", B: " + block.integer("B", 0, 511) + ",}";
					expect(tag, value, read);
				} else if (value.contains(",")) {
					expect(tag, split(value), node.values(leaf));
				} else {
					expect(tag, value, node.value(leaf));
				}
			}
		}
		case TEXT_ROWS -> {
			if (value != null) {
				expect(tag, value.split("\n", -1), node.texts(leaf));
			}
		}
		case FRAMES -> {
			if (value != null) {
				expect(tag, value.isBlank() ? new String[0] : Arrays.stream(value.split("\n", -1)).map(String::trim).toArray(String[]::new), node.values(leaf));
			}
		}
		case DECIMALS -> {
			if (value != null) {
				double[] entered = value.isEmpty() ? new double[0] : Arrays.stream(split(value)).mapToDouble(Double::parseDouble).toArray();
				expect(tag, entered, node.decimals(leaf));
			}
		}
		case KEY_LIST -> {
			if (value != null && !value.isEmpty()) {
				expect(tag, split(value), node.values(leaf));
			}
		}
		case DECIMAL_MAP -> {
			Map<String, String> entries = AssetChecker.entriesOf(tag, values);
			if (!entries.isEmpty()) {
				GameParser.Node block = node.block(leaf);
				for (Map.Entry<String, String> entry : entries.entrySet()) {
					double read = tag.min() == null || tag.max() == null ? block.decimal(entry.getKey()) : block.decimal(entry.getKey(), tag.min(), tag.max());
					expect(tag, Double.parseDouble(entry.getValue()), read);
				}
			}
		}
		default -> {
			if (value != null) {
				switch (tag.kind()) {
				case BOOL -> expect(tag, Boolean.valueOf(value), node.bool(leaf));
				case INT -> expect(tag, Integer.valueOf(value), tag.min() == null ? node.integer(leaf) : node.integer(leaf, tag.min().intValue(), tag.max() == null ? Integer.MAX_VALUE : tag.max().intValue()));
				case DECIMAL -> expect(tag, Double.valueOf(value), tag.min() == null ? node.decimal(leaf) : node.decimal(leaf, tag.min(), tag.max()));
				case TEXT_LINE, TEXT_LINES -> expect(tag, value, node.text(leaf));
				default -> expect(tag, value, node.value(leaf));
				}
			}
		}
		}
	}

	private static void expect(TagSpec tag, Object entered, Object read) throws CreationException {
		if (!Objects.deepEquals(entered, read)) {
			throw new CreationException("The game would read " + tag.key() + " differently from what was entered, so nothing was saved.");
		}
	}

	/** The game's parser has the final word. */
	private static void checkWithGame(GameParser parser, Path file, List<ExpectedRead> reads, String name) throws CreationException {
		List<GameParser.Read> plain = new ArrayList<>();
		for (ExpectedRead read : reads) {
			plain.add(read.read());
		}
		List<GameParser.ReadResult> results = parser.read(file, plain);
		for (int i = 0; i < results.size(); i++) {
			GameParser.ReadResult result = results.get(i);
			GameParser.Outcome outcome = result.outcome();
			String tag = result.read().args().isEmpty() ? "" : String.valueOf(result.read().args().get(0));
			if (outcome.dataError() != null) {
				throw new CreationException("The game's parser refused " + tag + ": " + SyntaxChecker.translate(outcome.dataError()).message());
			}
			if (outcome.otherFailure() != null) {
				throw new CreationException("The game's parser couldn't check " + name + ": " + outcome.otherFailure());
			}
			Object expected = reads.get(i).expected();
			if (expected != null && !Objects.deepEquals(expected, result.value())) {
				throw new CreationException("The game would read " + tag + " differently from what was entered, so nothing was saved.");
			}
		}
	}

	private record ExpectedRead(GameParser.Read read, Object expected) {
	}

	private static List<ExpectedRead> dataReads(AssetSchema schema, Map<String, String> values) {
		List<ExpectedRead> reads = new ArrayList<>();
		for (TagSpec tag : schema.dataTags()) {
			String value = values.get(tag.key());
			if (isNested(tag)) {
				continue;
			}
			boolean multiple = tag.kind() == Kind.KEY_LIST || tag.kind() == Kind.LINKED_DECIMALS || tag.kind() == Kind.DECIMAL_MAP || tag.kind() == Kind.KEYED_LISTS || tag.kind() == Kind.KEYED_TEXTS;
			if (value == null && !multiple) {
				continue;
			}
			switch (tag.kind()) {
			case BOOL -> reads.add(root("bool", tag.key(), Boolean.valueOf(value)));
			case INT -> reads.add(new ExpectedRead(new GameParser.Read(List.of(), "i", tag.min() == null ? List.of(tag.key()) : List.of(tag.key(), tag.min().intValue(), tag.max().intValue())), Integer.valueOf(value)));
			case DECIMAL -> reads.add(new ExpectedRead(new GameParser.Read(List.of(), "d", tag.min() == null ? List.of(tag.key()) : List.of(tag.key(), tag.min(), tag.max())), Double.valueOf(value)));
			case COLOR, ICON, SPRITE, PLAIN -> reads.add(root("value", tag.key(), value));
			case KEY_LIST -> {
				if (value != null && !value.isEmpty()) {
					reads.add(new ExpectedRead(new GameParser.Read(List.of(), "values", List.of(tag.key())), split(value)));
				}
			}
			case LINKED_DECIMALS -> {
				String[] amounts = value == null || value.isEmpty() ? new String[0] : split(value);
				double[] numbers = new double[amounts.length];
				for (int i = 0; i < amounts.length; i++) {
					numbers[i] = Double.parseDouble(amounts[i]);
				}
				reads.add(new ExpectedRead(new GameParser.Read(List.of(), "ds", List.of(tag.key(), amounts.length)), numbers));
			}
			case DECIMAL_MAP -> AssetChecker.entriesOf(tag, values).forEach((entry, number) -> reads.add(
					new ExpectedRead(new GameParser.Read(List.of(tag.key()), "d", tag.min() == null || tag.max() == null ? List.of(entry) : List.of(entry, tag.min(), tag.max())), Double.valueOf(number))));
			case KEYED_LISTS -> AssetChecker.entriesOf(tag, values).forEach((entry, names) -> reads.add(
					new ExpectedRead(new GameParser.Read(List.of(tag.key()), "values", List.of(entry)), split(names))));
			case KEYED_TEXTS -> AssetChecker.entriesOf(tag, values).forEach((entry, lines) -> reads.add(
					new ExpectedRead(new GameParser.Read(List.of(tag.key()), "texts", List.of(entry)), lines.split("\n", -1))));
			default -> throw new IllegalStateException("Not a data tag: " + tag.kind());
			}
		}
		return reads;
	}

	private static ExpectedRead root(String method, String key, Object expected) {
		return new ExpectedRead(new GameParser.Read(List.of(), method, List.of(key)), expected);
	}

	private static String[] split(String list) {
		return Arrays.stream(list.split(",", -1)).map(String::trim).toArray(String[]::new);
	}

	private static List<ExpectedRead> textReads(AssetSchema schema, Map<String, String> values) {
		List<ExpectedRead> reads = new ArrayList<>();
		// Read back where the game reads it: in INFO, or at the top for a type whose NAME is read there.
		List<String> infoPath = schema.textAtRoot() ? List.of() : List.of("INFO");
		for (TagSpec tag : schema.textTags()) {
			if (isNested(tag)) {
				continue;
			}
			if (tag.kind() == Kind.TEXT_BLOCK) {
				Map<String, String> entries = AssetChecker.entriesOf(tag, values);
				for (TagSpec.SubKey sub : tag.subKeys()) {
					String value = entries.get(sub.key());
					if (value != null) {
						List<String> path = new ArrayList<>(infoPath);
						path.add(tag.key());
						reads.add(new ExpectedRead(new GameParser.Read(path, sub.text() ? "text" : "value", List.of(sub.key())), value));
					}
				}
			} else if (values.containsKey(tag.key())) {
				List<String> path = AssetEditing.isInfoTag(tag) ? infoPath : List.of();
				reads.add(new ExpectedRead(new GameParser.Read(path, "text", List.of(tag.key())), values.get(tag.key())));
			}
		}
		return reads;
	}

	/** Only what's present is written, so the game's own defaults apply to everything else. */
	static String dataText(AssetSchema schema, Map<String, String> values) {
		return tagsText(schema.dataTags(), values);
	}

	static String tagsText(List<TagSpec> tags, Map<String, String> values) {
		StringBuilder out = new StringBuilder();
		for (TagSpec tag : tags) {
			String value = values.get(tag.key());
			if (isNested(tag) || isIconBlock(tag, values)) {
				writeNested(out, tag, values, "");
				continue;
			}
			switch (tag.kind()) {
			case KEY_LIST -> {
				if (value != null && !value.isEmpty()) {
					out.append(tag.leafName()).append(": ").append(list(value)).append(",\n");
				}
			}
			case LINKED_DECIMALS -> out.append(tag.leafName()).append(": ").append(value == null || value.isEmpty() ? "[]" : list(value)).append(",\n");
			case DECIMAL_MAP -> {
				Map<String, String> entries = AssetChecker.entriesOf(tag, values);
				if (!entries.isEmpty()) {
					out.append(tag.leafName()).append(": {\n");
					entries.forEach((entry, number) -> out.append('\t').append(entry).append(": ").append(number).append(",\n"));
					out.append("},\n");
				} else if (tag.required()) {
					// A map the game opens even when it's empty, such as a play mode's BOOST (PBonusSetting.java:228). Vanilla's 10_GREAT has BOOST: {}.
					out.append(tag.leafName()).append(": {},\n");
				}
			}
			case KEYED_LISTS -> {
				Map<String, String> entries = AssetChecker.entriesOf(tag, values);
				if (!entries.isEmpty()) {
					out.append(tag.leafName()).append(": {\n");
					entries.forEach((entry, names) -> out.append('\t').append(entry).append(": ").append(list(names)).append(",\n"));
					out.append("},\n");
				}
			}
			case KEYED_TEXTS -> writeKeyedTexts(out, tag, values, "", "\t");
			default -> {
				if (value != null) {
					out.append(tag.leafName()).append(": ").append(value).append(",\n");
				}
			}
			}
		}
		return out.toString();
	}

	/** Writes a nested tag and what's in it, leaving out anything that's absent. */
	private static void writeNested(StringBuilder out, TagSpec tag, Map<String, String> values, String indent) {
		String leaf = tag.leafName();
		String value = values.get(tag.key());
		String inner = indent + "\t";
		switch (tag.kind()) {
		case BLOCK -> {
			if (tag.required() || !AssetChecker.entriesOf(tag, values).isEmpty()) {
				out.append(indent).append(leaf).append(": {\n");
				for (TagSpec child : tag.nested()) {
					writeNested(out, child, values, inner);
				}
				out.append(indent).append("},\n");
			}
		}
		case KEYED_BLOCKS -> {
			Map<String, Map<String, String>> rows = AssetChecker.rowsOf(tag, values);
			if (!rows.isEmpty()) {
				out.append(indent).append(leaf).append(": {\n");
				writtenOrder(tag, rows).forEach(row -> {
					out.append(inner).append(row).append(": {\n");
					for (TagSpec child : tag.shape().children()) {
						writeNested(out, child.withKey(tag.key() + "." + row + "." + child.key()), values, inner + "\t");
					}
					out.append(inner).append("},\n");
				});
				out.append(indent).append("},\n");
			}
		}
		case KEYED_ROWS -> {
			Map<String, Map<String, String>> rows = AssetChecker.rowsOf(tag, values);
			if (!rows.isEmpty()) {
				out.append(indent).append(leaf).append(": {\n");
				rows.forEach((row, columns) -> {
					if (columns.containsKey("")) {
						out.append(inner).append(row).append(": ").append(columns.get("")).append(",\n");
					} else {
						out.append(inner).append(row).append(": {\n");
						for (TagSpec.Column column : tag.shape().columns()) {
							if (columns.containsKey(column.key())) {
								out.append(inner).append('\t').append(column.key()).append(": ").append(columns.get(column.key())).append(",\n");
							}
						}
						out.append(inner).append("},\n");
					}
				});
				out.append(indent).append("},\n");
			}
		}
		case BLOCK_LIST -> {
			int count = tag.shape().fixedCount();
			if (count == 0) {
				for (String entry : AssetChecker.entriesOf(tag, values).keySet()) {
					count = Math.max(count, Integer.parseInt(entry.substring(0, entry.indexOf('.'))) + 1);
				}
			}
			if (count > 0) {
				out.append(indent).append(leaf).append(": [\n");
				for (int i = 0; i < count; i++) {
					out.append(inner).append("{\n");
					for (TagSpec child : tag.nestedAt(i)) {
						// Required words that may be blank are written blank when left empty, because text() needs the tag and returns "" as it is.
						// The form can't hold a blank, so this is how one is kept (vanilla SHRINE_AMINION's item has DESC: "").
						boolean blankWords = child.blankAllowed() && (child.kind() == TagSpec.Kind.TEXT_LINE || child.kind() == TagSpec.Kind.TEXT_LINES) && !values.containsKey(child.key());
						if (blankWords) {
							out.append(inner).append('\t').append(child.leafName()).append(": \"\",\n");
							continue;
						}
						writeNested(out, child, values, inner + "\t");
					}
					out.append(inner).append("},\n");
				}
				out.append(indent).append("],\n");
			}
		}
		case ICON -> {
			if (isIconBlock(tag, values)) {
				out.append(indent).append(leaf).append(": {\n");
				for (TagSpec child : tag.nested()) {
					writeNested(out, child, values, inner);
				}
				out.append(indent).append("},\n");
			} else if (value != null) {
				out.append(indent).append(leaf).append(": ").append(value).append(",\n");
			}
		}
		case COLORS -> {
			if (value != null) {
				out.append(indent).append(leaf).append(": ").append(value.contains(",") && !value.startsWith("{") ? list(value) : value).append(",\n");
			}
		}
		case KEY_LIST -> {
			if (value != null && !value.isEmpty()) {
				out.append(indent).append(leaf).append(": ").append(list(value)).append(",\n");
			}
		}
		case TEXT_ROWS -> {
			if (value != null) {
				out.append(indent).append(leaf).append(": [\n");
				for (String row : value.split("\n", -1)) {
					out.append(inner).append(quoted(row)).append(",\n");
				}
				out.append(indent).append("],\n");
			}
		}
		case FRAMES -> {
			if (value != null) {
				if (value.isBlank()) {
					out.append(indent).append(leaf).append(": [],\n");
				} else {
					out.append(indent).append(leaf).append(": [\n");
					for (String frame : value.split("\n", -1)) {
						out.append(inner).append(frame.trim()).append(",\n");
					}
					out.append(indent).append("],\n");
				}
			}
		}
		case DECIMALS -> {
			if (value != null) {
				out.append(indent).append(leaf).append(": ").append(list(value)).append(",\n");
			}
		}
		case DECIMAL_MAP -> {
			Map<String, String> entries = AssetChecker.entriesOf(tag, values);
			if (!entries.isEmpty()) {
				out.append(indent).append(leaf).append(": {\n");
				entries.forEach((entry, number) -> out.append(inner).append(entry).append(": ").append(number).append(",\n"));
				out.append(indent).append("},\n");
			} else if (tag.required()) {
				// A map the game opens even when it's empty, such as each race home sheet's RESOURCES (RaceHomeSheet.java:44).
				out.append(indent).append(leaf).append(": {},\n");
			}
		}
		case KEYED_LISTS -> {
			Map<String, String> entries = AssetChecker.entriesOf(tag, values);
			if (!entries.isEmpty()) {
				out.append(indent).append(leaf).append(": {\n");
				entries.forEach((entry, names) -> out.append(inner).append(entry).append(": ").append(list(names)).append(",\n"));
				out.append(indent).append("},\n");
			}
		}
		case KEYED_TEXTS -> writeKeyedTexts(out, tag, values, indent, inner);
		case TEXT_LINE, TEXT_LINES -> {
			if (value != null) {
				out.append(indent).append(leaf).append(": ").append(quoted(value)).append(",\n");
			}
		}
		default -> {
			if (value != null) {
				out.append(indent).append(leaf).append(": ").append(value).append(",\n");
			}
		}
		}
	}

	private static void writeKeyedTexts(StringBuilder out, TagSpec tag, Map<String, String> values, String indent, String inner) {
		Map<String, String> entries = AssetChecker.entriesOf(tag, values);
		if (!tag.required() && entries.isEmpty()) {
			return;
		}
		out.append(indent).append(tag.leafName()).append(": {\n");
		entries.forEach((entry, lines) -> {
			out.append(inner).append(entry).append(": [\n");
			for (String line : lines.split("\n", -1)) {
				if (!line.isEmpty()) {
					out.append(inner).append('\t').append(quoted(line)).append(",\n");
				}
			}
			out.append(inner).append("],\n");
		});
		out.append(indent).append("},\n");
	}

	private static String list(String value) {
		if (value.isEmpty()) {
			return "[]";
		}
		StringBuilder out = new StringBuilder("[");
		for (String item : split(value)) {
			out.append(item).append(", ");
		}
		return out.append("]").toString().replace(", ]", ",]");
	}

	/**
	 * INFO's own tags go in an INFO block, which the game accepts (INFO.java:16-17). Every other text tag is
	 * written at the top, because its loader reads it from the whole file (RaceInfo.java:49-53).
	 */
	static String textText(AssetSchema schema, Map<String, String> values) {
		// A play mode's NAME is read from the top by more than INFO (PBonusSetting.java:56-59), so its INFO tags go there.
		String indent = schema.textAtRoot() ? "" : "\t";
		StringBuilder out = new StringBuilder(schema.textAtRoot() ? "" : "INFO: {\n");
		for (TagSpec tag : schema.textTags()) {
			if (isNested(tag) || !AssetEditing.isInfoTag(tag)) {
				continue;
			}
			if (tag.kind() == Kind.TEXT_BLOCK) {
				Map<String, String> entries = AssetChecker.entriesOf(tag, values);
				if (!entries.isEmpty()) {
					out.append(indent).append(tag.key()).append(": {\n");
					for (TagSpec.SubKey sub : tag.subKeys()) {
						String value = entries.get(sub.key());
						if (value != null) {
							out.append(indent).append('\t').append(sub.key()).append(": ").append(sub.text() ? quoted(value) : value).append(",\n");
						}
					}
					out.append(indent).append("},\n");
				}
			} else if (values.containsKey(tag.key())) {
				out.append(indent).append(tag.key()).append(": ").append(quoted(values.get(tag.key()))).append(",\n");
			}
		}
		if (!schema.textAtRoot()) {
			out.append("},\n");
		}
		for (TagSpec tag : schema.textTags()) {
			if (isNested(tag)) {
				writeNested(out, tag, values, "");
			} else if (!AssetEditing.isInfoTag(tag) && values.containsKey(tag.key())) {
				out.append(tag.key()).append(": ").append(quoted(values.get(tag.key()))).append(",\n");
			}
		}
		return out.toString();
	}

	private static String quoted(String text) {
		return "\"" + text + "\"";
	}
}
