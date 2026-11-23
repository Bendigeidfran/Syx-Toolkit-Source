package syx.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import syx.core.AssetChecker.Finding;
import syx.core.AssetChecker.Severity;

/**
 * Checks an existing data file against a schema: the game's parser reads it the way the loader would,
 * and the same value rules the editor uses are applied to what it read. An unknown tag is only ever a
 * warning, because a search that finds nothing doesn't prove a tag is unread.
 */
public final class AssetVanillaCheck {

	private AssetVanillaCheck() {
	}

	public static List<Finding> check(AssetSchema schema, GameAssetIndex index, Path dataFile, GameParser parser) {
		List<Finding> findings = new ArrayList<>();
		GameParser.Tags tags = parser.tags(dataFile);
		if (!tags.outcome().ok()) {
			findings.add(new Finding(null, Severity.ERROR, "The game's parser refused the file: " + tags.outcome()));
			return findings;
		}
		Map<String, GameParser.TagValue> present = new LinkedHashMap<>();
		for (GameParser.TagValue tag : tags.tags()) {
			present.put(tag.key(), tag);
			if (schema.dataTags().stream().noneMatch(t -> t.key().equals(tag.key()))) {
				findings.add(new Finding(tag.key(), Severity.WARNING, "The game code doesn't appear to read this tag."));
			}
		}

		Map<String, String> values = new LinkedHashMap<>();
		for (TagSpec tag : schema.dataTags()) {
			GameParser.TagValue found = present.get(tag.key());
			if (found == null) {
				if (tag.required()) {
					findings.add(new Finding(tag.key(), Severity.ERROR, "The game needs this tag."));
				}
			} else {
				readTag(tag, found, dataFile, parser, values, findings);
			}
		}

		for (Finding finding : AssetChecker.checkData(schema, index, values)) {
			// Whether a required tag is there was decided above, from the file itself.
			if (finding.severity() != Severity.MISSING) {
				findings.add(finding);
			}
		}
		return findings;
	}

	/**
	 * Reads a text file's tags the way the loader would. INFO's own tags can be written two ways, so they aren't
	 * covered here. Every other text tag is read from the top of the file, as RaceInfo does (RaceInfo.java:49-53).
	 */
	public static List<Finding> checkText(AssetSchema schema, Path textFile, GameParser parser) {
		List<Finding> findings = new ArrayList<>();
		try {
			GameParser.Node root = parser.root(textFile);
			// INFO accepts its tags inside an INFO block (INFO.java:16-17), but a play mode's NAME is also read from the
			// top on its own (PBonusSetting.java:56-59), so wrapped, every new game stops, whichever mode is chosen.
			if (schema.textAtRoot() && !root.has("NAME")) {
				findings.add(new Finding("NAME", Severity.ERROR, root.isBlock("INFO")
						? "NAME is inside an INFO block, but the game also reads it from the top of the text file, so every new game stops with \"Missing a String with key: NAME\". Saving moves it to the top."
						: "The game reads NAME from the top of the text file, and it isn't there, so every new game stops."));
			}
			for (TagSpec tag : schema.textTags()) {
				switch (tag.kind()) {
				case BLOCK, BLOCK_LIST, TEXT_ROWS -> readNode(root, tag, new LinkedHashMap<>(), findings);
				case TEXT_LINE, TEXT_LINES -> {
					if (AssetEditing.isInfoTag(tag)) {
						break;
					}
					if (root.has(tag.leafName())) {
						readNode(root, tag, new LinkedHashMap<>(), findings);
					} else if (tag.required()) {
						findings.add(new Finding(tag.key(), Severity.ERROR, "The game reads this from the top of the text file, and it isn't there, so the game stops loading."));
					}
				}
				default -> {
					// INFO's tags.
				}
				}
			}
		} catch (GameParser.ReadException e) {
			findings.add(new Finding(null, Severity.ERROR, "The game's parser refused it: " + e.getMessage()));
		}
		return findings;
	}

	/** Reads one tag with the same Json method its loader uses, so the game decides if it's valid. */
	private static void readTag(TagSpec tag, GameParser.TagValue found, Path file, GameParser parser, Map<String, String> values, List<Finding> findings) {
		String key = tag.key();
		switch (tag.kind()) {
		case BLOCK, KEYED_ROWS, KEYED_BLOCKS, KEYED_LISTS, KEYED_TEXTS, BLOCK_LIST, TEXT_ROWS, DECIMALS, KEY -> {
			try {
				readNode(parser.root(file), tag, values, findings);
			} catch (GameParser.ReadException e) {
				findings.add(new Finding(key, Severity.ERROR, "The game's parser refused it: " + e.getMessage()));
			}
		}
		case BOOL -> values.put(key, String.valueOf(readOne(parser, file, findings, key, List.of(), "bool", List.of(key))));
		case INT -> {
			List<Object> args = tag.min() == null ? List.of(key) : List.of(key, tag.min().intValue(), tag.max().intValue());
			values.put(key, String.valueOf(readOne(parser, file, findings, key, List.of(), "i", args)));
		}
		case DECIMAL -> {
			List<Object> args = tag.min() == null ? List.of(key) : List.of(key, tag.min(), tag.max());
			values.put(key, plainNumber(readOne(parser, file, findings, key, List.of(), "d", args)));
		}
		case KEY_LIST -> {
			// FLOOR is one name when it isn't an array (Furnisher.java:77-83). values() would refuse that.
			if ("FLOOR".equals(key) && found.kind() != GameParser.ValueKind.OTHER) {
				values.put(key, found.value());
			} else {
				Object list = readOne(parser, file, findings, key, List.of(), "values", List.of(key));
				if (list instanceof String[] items) {
					values.put(key, String.join(",", items));
				}
			}
		}
		case LINKED_DECIMALS -> {
			Object list = readOne(parser, file, findings, key, List.of(), "values", List.of(key));
			if (list instanceof String[] items) {
				values.put(key, String.join(",", items));
			}
		}
		case DECIMAL_MAP -> {
			List<String> entries = parser.blockKeys(file, key);
			if (entries == null) {
				findings.add(new Finding(key, Severity.ERROR, "The game's parser needs a block here."));
			} else {
				for (String entry : entries) {
					List<Object> args = tag.min() == null || tag.max() == null ? List.of(entry) : List.of(entry, tag.min(), tag.max());
					values.put(key + "." + entry, plainNumber(readOne(parser, file, findings, key, List.of(key), "d", args)));
				}
			}
		}
		// Icons and colours also have block forms, which plain value() would refuse, so only their text form is checked.
		case COLOR, ICON -> {
			if (found.kind() == GameParser.ValueKind.PLAIN) {
				values.put(key, found.value());
			} else if (tag.kind() == TagSpec.Kind.ICON) {
				// Rooms' icons are often a block that combines several, which the game reads (Icons.java:88-90). A list of them isn't offered, so it isn't checked.
				try {
					GameParser.Node root = parser.root(file);
					if (root.isBlock(key)) {
						readNode(root, tag, values, findings);
					}
				} catch (GameParser.ReadException e) {
					findings.add(new Finding(key, Severity.ERROR, "The game's parser refused it: " + e.getMessage()));
				}
			} else {
				findings.add(new Finding(key, Severity.WARNING, "This is written in a form that wasn't checked."));
			}
		}
		case SPRITE, PLAIN -> values.put(key, String.valueOf(readOne(parser, file, findings, key, List.of(), "value", List.of(key))));
		default -> throw new IllegalStateException("Not a data tag: " + tag.kind());
		}
	}

	/** Reads a nested tag with the game's own methods. A tag inside a block that isn't in the schema is left alone. */
	private static void readNode(GameParser.Node parent, TagSpec tag, Map<String, String> values, List<Finding> findings) throws GameParser.ReadException {
		String leaf = tag.leafName();
		String key = tag.key();
		if (!parent.has(leaf)) {
			if (tag.required()) {
				findings.add(new Finding(key, Severity.ERROR, "The game needs this tag."));
			}
			return;
		}
		switch (tag.kind()) {
		case BLOCK -> {
			GameParser.Node block = parent.block(leaf);
			for (TagSpec child : tag.nested()) {
				readNode(block, child, values, findings);
			}
		}
		case KEYED_BLOCKS -> {
			GameParser.Node block = parent.block(leaf);
			for (String row : block.keys()) {
				if (!block.isBlock(row)) {
					findings.add(new Finding(key, Severity.ERROR, row + " needs to be a block."));
					continue;
				}
				GameParser.Node entry = block.block(row);
				for (TagSpec child : tag.shape().children()) {
					readNode(entry, child.withKey(key + "." + row + "." + child.key()), values, findings);
				}
			}
		}
		case KEYED_ROWS -> {
			GameParser.Node block = parent.block(leaf);
			TagSpec.Column plainColumn = tag.shape().columns().get(0);
			for (String row : block.keys()) {
				if (block.isBlock(row)) {
					GameParser.Node entry = block.block(row);
					for (TagSpec.Column column : tag.shape().columns()) {
						if (column.key() != null && entry.has(column.key())) {
							values.put(key + "." + row + "." + column.key(), plainNumber(entry.decimal(column.key(), column.min(), column.max())));
						}
					}
				} else if (plainColumn.key() == null) {
					values.put(key + "." + row, plainNumber(block.decimal(row, plainColumn.min(), plainColumn.max())));
				} else {
					// A plain rate stands for the first column, and the game gives it its own range (Industry.java:135).
					values.put(key + "." + row + "." + plainColumn.key(), plainNumber(block.decimal(row, 0, 10000)));
				}
			}
		}
		case BLOCK_LIST -> {
			// A sprite is one block or a list, and one block is the first level (RoomSpriteImp.java:27-37), as the editor reads it.
			if (parent.isBlock(leaf) && tag.shape().children().stream().anyMatch(child -> child.kind() == TagSpec.Kind.FRAMES)) {
				GameParser.Node block = parent.block(leaf);
				for (TagSpec child : tag.nestedAt(0)) {
					readNode(block, child, values, findings);
				}
				break;
			}
			// A crown can be one block or a list, and a colour list can be one colour or colour strings. Those stay as written.
			if (parent.isBlock(leaf)) {
				findings.add(new Finding(key, Severity.WARNING, "This is one block, which the game also accepts, so it stays as written."));
				break;
			}
			List<GameParser.Node> blocks;
			try {
				blocks = parent.blocks(leaf);
			} catch (GameParser.ReadException e) {
				findings.add(new Finding(key, Severity.WARNING, "This isn't a list of blocks, so it stays as written."));
				break;
			}
			int fixed = tag.shape().fixedCount();
			if (fixed > 0 && blocks.size() != fixed) {
				findings.add(new Finding(key, Severity.ERROR, "The game needs " + fixed + " blocks here, not " + blocks.size() + "."));
			}
			int slots = fixed > 0 ? Math.min(tag.shape().namedSlots(), blocks.size()) : blocks.size();
			for (int i = 0; i < slots; i++) {
				for (TagSpec child : tag.nestedAt(i)) {
					readNode(blocks.get(i), child, values, findings);
				}
			}
		}
		case KEY_LIST -> values.put(key, String.join(",", parent.values(leaf)));
		case TEXT_ROWS -> values.put(key, String.join("\n", parent.texts(leaf)));
		case FRAMES -> values.put(key, String.join("\n", parent.values(leaf)));
		case DECIMALS -> {
			StringBuilder list = new StringBuilder();
			for (double number : parent.decimals(leaf)) {
				list.append(list.length() == 0 ? "" : ",").append(plainNumber(number));
			}
			values.put(key, list.toString());
		}
		case DECIMAL_MAP -> {
			GameParser.Node block = parent.block(leaf);
			for (String entry : block.keys()) {
				double number = tag.min() == null || tag.max() == null ? block.decimal(entry) : block.decimal(entry, tag.min(), tag.max());
				values.put(key + "." + entry, plainNumber(number));
			}
		}
		case KEYED_LISTS -> {
			GameParser.Node block = parent.block(leaf);
			for (String entry : block.keys()) {
				values.put(key + "." + entry, String.join(",", block.values(entry)));
			}
		}
		case KEYED_TEXTS -> {
			GameParser.Node block = parent.block(leaf);
			for (String entry : block.keys()) {
				values.put(key + "." + entry, String.join("\n", block.texts(entry)));
			}
		}
		case BOOL -> values.put(key, String.valueOf(parent.bool(leaf)));
		case INT -> values.put(key, String.valueOf(tag.min() == null ? parent.integer(leaf) : parent.integer(leaf, tag.min().intValue(), tag.max() == null ? Integer.MAX_VALUE : tag.max().intValue())));
		case DECIMAL -> values.put(key, plainNumber(tag.min() == null ? parent.decimal(leaf) : parent.decimal(leaf, tag.min(), tag.max())));
		case TEXT_LINE, TEXT_LINES -> values.put(key, parent.text(leaf));
		case ICON -> {
			if (!parent.isBlock(leaf)) {
				values.put(key, parent.value(leaf));
			} else if (!tag.nested().isEmpty()) {
				GameParser.Node block = parent.block(leaf);
				for (TagSpec child : tag.nested()) {
					readNode(block, child, values, findings);
				}
			}
		}
		case COLORS -> {
			if (parent.isList(leaf)) {
				try {
					values.put(key, String.join(",", parent.values(leaf)));
				} catch (GameParser.ReadException e) {
					findings.add(new Finding(key, Severity.WARNING, "This is a list of colour blocks, which the game also accepts, so it stays as written."));
				}
			} else if (!parent.isBlock(leaf)) {
				values.put(key, parent.value(leaf));
			}
		}
		default -> values.put(key, parent.value(leaf));
		}
	}

	private static Object readOne(GameParser parser, Path file, List<Finding> findings, String tag, List<String> block, String method, List<Object> args) {
		GameParser.ReadResult result = parser.read(file, List.of(new GameParser.Read(block, method, args))).get(0);
		if (!result.outcome().ok()) {
			findings.add(new Finding(tag, Severity.ERROR, "The game's parser refused it: " + result.outcome()));
		}
		return result.value();
	}

	private static String plainNumber(Object number) {
		return number instanceof Double d ? java.math.BigDecimal.valueOf(d).toPlainString() : String.valueOf(number);
	}
}
