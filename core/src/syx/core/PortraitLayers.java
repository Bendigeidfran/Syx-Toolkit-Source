package syx.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import syx.core.TagLocator.Located;

/**
 * A race's portraits as layers, read from and written into the portrait text {@link PortraitSim} opens. Each
 * portrait's {@code FACE} is a list of layers drawn bottom first (RPortrait.java:17-33). A layer's name is the
 * {@code **} comment above it, which the game skips (JsonValue.java:430-457), as vanilla's files name theirs.
 *
 * <p>Every change returns new text and touches only what it changes, so the rest keeps its own bytes. This only
 * locates text: what's saved is read back with the game's parser by {@link PortraitSim.Session#save}, which
 * refuses anything the game would read differently or couldn't load.
 */
public final class PortraitLayers {

	/** One frame: a face sheet's name and the frame's index on it, numbered across then down (RaceFrameMaker.java:69-85,139-146). */
	public record FrameRef(String file, int index) {
		public String text() {
			return file + ": " + index;
		}

		/** A FRAMES entry as the game splits it, or null if the game would skip it. */
		public static FrameRef parse(String text) {
			if (text == null) {
				return null;
			}
			String[] parts = text.split(":");
			if (parts.length != 2) {
				return null;
			}
			try {
				String file = parts[0].trim();
				return file.isEmpty() ? null : new FrameRef(file, Integer.parseInt(parts[1].trim()));
			} catch (NumberFormatException e) {
				return null;
			}
		}
	}

	/** One test in a layer's CONDITIONS, as written (Lockable.java:115-127). */
	public record Condition(String comparator, String stat, String value) {
	}

	/**
	 * One layer of a portrait.
	 *
	 * @param index its place in FACE, from the bottom
	 * @param name the comment above it, or null
	 * @param frames the frames the game can read, in order
	 * @param skipped FRAMES entries the game can't read, as written
	 * @param plain each plain tag's value as written, such as COLOR or OFF_X
	 * @param conditions its CONDITIONS, or empty
	 * @param random the person's random slot it picks with: FRAME_RANDOM, or its place in FACE (RPortraitFrame.java:68)
	 */
	public record Layer(int index, String name, List<FrameRef> frames, List<String> skipped, Map<String, String> plain, List<Condition> conditions, int random) {
	}

	/** A crown, helmet or raider's piece: an addon sheet, every row of which can be picked, with where it's drawn (RCrown.java:46-68). */
	public record Addon(String file, String offX, String offY) {
	}

	/** One of a colour's entries as written, R_G_B. */
	public record Rgb(int r, int g, int b) {
		public String text() {
			return r + "_" + g + "_" + b;
		}
	}

	/** A colour of the race: its entries, or null if VALUES isn't a list of plain colours, and its other tags. */
	public record Colour(String name, List<Rgb> values, Map<String, String> plain) {
	}

	public static final List<String> ADDON_KINDS = List.of("CROWN", "RAIDER", "MERC");

	private static final Pattern RGB = Pattern.compile("\\s*(\\d{1,3})_(\\d{1,3})_(\\d{1,3})\\s*");

	private PortraitLayers() {
	}

	// ---- Reading

	/** The portraits, adults in order then the child, labelled as the sim labels them. */
	public static List<String> portraits(String section) {
		List<String> labels = new ArrayList<>();
		int adults = adultBlocks(section).size();
		for (int i = 0; i < adults; i++) {
			labels.add(adults == 1 ? "Adult" : "Adult " + (i + 1));
		}
		if (childBlock(section) != null) {
			labels.add("Child");
		}
		return labels;
	}

	public static List<Layer> layers(String section, int portrait) {
		Located face = face(section, portrait);
		List<Layer> layers = new ArrayList<>();
		if (face == null || section.charAt(face.start()) != '[') {
			return layers;
		}
		List<Located> items = TagLocator.items(section, face);
		for (int i = 0; i < items.size(); i++) {
			Located item = items.get(i);
			if (section.charAt(item.start()) != '{') {
				continue;
			}
			List<FrameRef> frames = new ArrayList<>();
			List<String> skipped = new ArrayList<>();
			Map<String, String> plain = new LinkedHashMap<>();
			List<Condition> conditions = new ArrayList<>();
			for (Located tag : TagLocator.entries(section, item)) {
				char first = section.charAt(tag.start());
				String value = section.substring(tag.start(), tag.end());
				if (tag.key().equals("FRAMES") && first == '[') {
					for (Located entry : TagLocator.items(section, tag)) {
						String written = section.substring(entry.start(), entry.end());
						FrameRef ref = FrameRef.parse(written);
						if (ref == null) {
							skipped.add(written.strip());
						} else {
							frames.add(ref);
						}
					}
				} else if (tag.key().equals("CONDITIONS") && first == '{') {
					for (Located comparator : TagLocator.entries(section, tag)) {
						if (section.charAt(comparator.start()) == '{') {
							for (Located stat : TagLocator.entries(section, comparator)) {
								conditions.add(new Condition(comparator.key(), stat.key(), section.substring(stat.start(), stat.end()).strip()));
							}
						} else {
							conditions.add(new Condition(comparator.key(), "", value.strip()));
						}
					}
				} else if (first != '{' && first != '[') {
					plain.put(tag.key(), value.strip());
				}
			}
			int random = i % 16;
			try {
				if (plain.containsKey("FRAME_RANDOM")) {
					random = Integer.parseInt(plain.get("FRAME_RANDOM"));
				}
			} catch (NumberFormatException e) {
				// The game's own read refuses it, and the preview says so.
			}
			layers.add(new Layer(i, name(section, items, i, face), frames, skipped, plain, conditions, random));
		}
		return layers;
	}

	public static List<Addon> addons(String section, String kind) {
		Located value = top(section, kind);
		List<Addon> addons = new ArrayList<>();
		if (value == null) {
			return addons;
		}
		List<Located> blocks = new ArrayList<>();
		if (section.charAt(value.start()) == '[') {
			blocks.addAll(TagLocator.items(section, value));
		} else {
			blocks.add(value);
		}
		for (Located block : blocks) {
			if (section.charAt(block.start()) != '{') {
				continue;
			}
			Map<String, String> tags = new LinkedHashMap<>();
			for (Located tag : TagLocator.entries(section, block)) {
				tags.put(tag.key(), section.substring(tag.start(), tag.end()).strip());
			}
			addons.add(new Addon(tags.get("FILE"), tags.get("OFFX"), tags.get("OFFY")));
		}
		return addons;
	}

	public static List<Colour> colours(String section) {
		Located colors = top(section, "COLORS");
		List<Colour> colours = new ArrayList<>();
		if (colors == null || section.charAt(colors.start()) != '{') {
			return colours;
		}
		for (Located entry : TagLocator.entries(section, colors)) {
			if (section.charAt(entry.start()) != '{') {
				continue;
			}
			Map<String, String> plain = new LinkedHashMap<>();
			List<Rgb> values = null;
			for (Located tag : TagLocator.entries(section, entry)) {
				char first = section.charAt(tag.start());
				if (tag.key().equals("VALUES")) {
					values = rgbList(section, tag);
				} else if (first != '{' && first != '[') {
					plain.put(tag.key(), section.substring(tag.start(), tag.end()).strip());
				}
			}
			colours.add(new Colour(entry.key(), values, plain));
		}
		return colours;
	}

	/** Plain R_G_B entries and {R, G, B} blocks, or null for any other form, such as a FROM and TO range. */
	private static List<Rgb> rgbList(String section, Located values) {
		if (section.charAt(values.start()) != '[') {
			return null;
		}
		List<Rgb> list = new ArrayList<>();
		for (Located item : TagLocator.items(section, values)) {
			String written = section.substring(item.start(), item.end());
			Matcher plain = RGB.matcher(written);
			if (plain.matches()) {
				list.add(new Rgb(Integer.parseInt(plain.group(1)), Integer.parseInt(plain.group(2)), Integer.parseInt(plain.group(3))));
				continue;
			}
			if (written.startsWith("{")) {
				Map<String, String> channels = new LinkedHashMap<>();
				for (Located tag : TagLocator.entries(section, item)) {
					channels.put(tag.key(), section.substring(tag.start(), tag.end()).strip());
				}
				try {
					if (channels.keySet().equals(java.util.Set.of("R", "G", "B"))) {
						list.add(new Rgb(Integer.parseInt(channels.get("R")), Integer.parseInt(channels.get("G")), Integer.parseInt(channels.get("B"))));
						continue;
					}
				} catch (NumberFormatException e) {
					return null;
				}
			}
			return null;
		}
		return list;
	}

	/** Every face sheet the portraits name, in the order first named. */
	public static List<String> faceSheets(String section) {
		List<String> names = new ArrayList<>();
		for (int p = 0; p < portraits(section).size(); p++) {
			for (Layer layer : layers(section, p)) {
				for (FrameRef frame : layer.frames()) {
					if (!names.contains(frame.file())) {
						names.add(frame.file());
					}
				}
			}
		}
		return names;
	}

	/** Every addon sheet CROWN, RAIDER and MERC name. */
	public static List<String> addonSheets(String section) {
		List<String> names = new ArrayList<>();
		for (String kind : ADDON_KINDS) {
			for (Addon addon : addons(section, kind)) {
				if (addon.file() != null && !names.contains(addon.file())) {
					names.add(addon.file());
				}
			}
		}
		return names;
	}

	// ---- Changing layers

	/** A new layer on top of the others, with its name above it. A portrait with no FACE gets one. */
	public static String addLayer(String section, int portrait, String name, List<FrameRef> frames) {
		return addLayer(section, portrait, Integer.MAX_VALUE, name, frames);
	}

	/**
	 * A new layer at a place in the drawing order: 0 is the bottom, behind everything, and the number of layers is the
	 * top. The game draws strictly in order (RPortrait.java:33), so this is the only thing that puts a layer, such as
	 * back hair, behind the head. The layers it goes under keep the frame each person gets (see {@link #pinRandom}).
	 */
	public static String addLayer(String section, int portrait, int at, String name, List<FrameRef> frames) {
		Located face = face(section, portrait);
		if (face != null && at < TagLocator.items(section, face).size()) {
			int count = TagLocator.items(section, face).size();
			String pinned = pinRandom(section, portrait, Math.max(0, at), count - 1);
			Located pinnedFace = face(pinned, portrait);
			List<Piece> pieces = pieces(pinned, pinnedFace);
			String indent = itemIndent(pinned, pinnedFace, pieces);
			pieces.add(Math.max(0, at), new Piece(null, null, layerText(name, frames, indent)));
			return rebuild(pinned, pinnedFace, pieces, indent);
		}
		if (face == null) {
			Located block = portraitBlock(section, portrait);
			int close = block.end() - 1;
			String indent = closingIndent(section, close) + "\t";
			// The game's reader needs a comma after a block that isn't last in its text (JsonValue's Reader), as TagLocator does.
			String list = "[" + layerText(name, frames, indent + "\t") + ",\n" + indent + "]";
			return insertTag(section, block, "FACE", list);
		}
		List<Piece> pieces = pieces(section, face);
		String indent = itemIndent(section, face, pieces);
		pieces.add(new Piece(null, null, layerText(name, frames, indent)));
		return rebuild(section, face, pieces, indent);
	}

	/** Moves a layer to another place, keeping the frame each person gets (see {@link #pinRandom}). */
	public static String move(String section, int portrait, int from, int to) {
		Located face = face(section, portrait);
		List<Piece> pieces = pieces(section, face);
		if (from < 0 || from >= pieces.size() || to < 0 || to >= pieces.size() || from == to) {
			return section;
		}
		String pinned = pinRandom(section, portrait, Math.min(from, to), Math.max(from, to));
		face = face(pinned, portrait);
		pieces = pieces(pinned, face);
		Piece moved = pieces.remove(from);
		pieces.add(to, moved);
		return rebuild(pinned, face, pieces, itemIndent(pinned, face, pieces));
	}

	/** Removes a layer, keeping the frame each person gets on the layers above it. The last layer removes FACE. */
	public static String remove(String section, int portrait, int layer) {
		Located face = face(section, portrait);
		List<Piece> pieces = pieces(section, face);
		if (layer < 0 || layer >= pieces.size()) {
			return section;
		}
		if (pieces.size() == 1) {
			return clear(section, portrait);
		}
		String pinned = pinRandom(section, portrait, layer + 1, pieces.size() - 1);
		face = face(pinned, portrait);
		pieces = pieces(pinned, face);
		pieces.remove(layer);
		return rebuild(pinned, face, pieces, itemIndent(pinned, face, pieces));
	}

	/** Removes a portrait's FACE, which the game draws as no portrait (RPortrait.java:17-20). */
	public static String clear(String section, int portrait) {
		Located block = portraitBlock(section, portrait);
		for (Located entry : TagLocator.entries(section, block)) {
			if (entry.key().equals("FACE")) {
				int[] range = TagLocator.entryRange(section, entry);
				return section.substring(0, range[0]) + section.substring(range[1]);
			}
		}
		return section;
	}

	/**
	 * Writes FRAME_RANDOM on each layer from {@code first} to {@code last} that doesn't have it, as its place now.
	 * A layer without one picks with its place in FACE (RPortraitFrame.java:68), so moving it would change every
	 * person's frame and break a pair picking together.
	 */
	static String pinRandom(String section, int portrait, int first, int last) {
		String text = section;
		for (Layer layer : layers(section, portrait)) {
			if (layer.index() >= first && layer.index() <= last && !layer.plain().containsKey("FRAME_RANDOM")) {
				text = setTag(text, portrait, layer.index(), "FRAME_RANDOM", String.valueOf(layer.index() % 16));
			}
		}
		return text;
	}

	public static String setFrames(String section, int portrait, int layer, List<FrameRef> frames) {
		Located item = layerItem(section, portrait, layer);
		for (Located tag : TagLocator.entries(section, item)) {
			if (tag.key().equals("FRAMES")) {
				String indent = indentOf(section, tag.keyStart());
				return section.substring(0, tag.start()) + framesText(frames, indent) + section.substring(tag.end());
			}
		}
		String indent = closingIndent(section, item.end() - 1) + "\t";
		return insertTag(section, item, "FRAMES", framesText(frames, indent));
	}

	/** Sets a plain tag on a layer, or removes it for null. */
	public static String setTag(String section, int portrait, int layer, String key, String value) {
		return setIn(section, layerItem(section, portrait, layer), key, value);
	}

	/** Replaces a layer's CONDITIONS, or removes them when there are none. */
	public static String setConditions(String section, int portrait, int layer, List<Condition> conditions) {
		Located item = layerItem(section, portrait, layer);
		Located existing = null;
		for (Located tag : TagLocator.entries(section, item)) {
			if (tag.key().equals("CONDITIONS")) {
				existing = tag;
			}
		}
		if (conditions.isEmpty()) {
			if (existing == null) {
				return section;
			}
			int[] range = TagLocator.entryRange(section, existing);
			return section.substring(0, range[0]) + section.substring(range[1]);
		}
		String indent = existing != null ? indentOf(section, existing.keyStart()) : closingIndent(section, item.end() - 1) + "\t";
		Map<String, List<Condition>> byComparator = new LinkedHashMap<>();
		for (Condition condition : conditions) {
			byComparator.computeIfAbsent(condition.comparator(), key -> new ArrayList<>()).add(condition);
		}
		StringBuilder text = new StringBuilder("{\n");
		byComparator.forEach((comparator, list) -> {
			text.append(indent).append('\t').append(comparator).append(": {\n");
			for (Condition condition : list) {
				text.append(indent).append("\t\t").append(condition.stat()).append(": ").append(condition.value()).append(",\n");
			}
			text.append(indent).append("\t},\n");
		});
		text.append(indent).append('}');
		if (existing != null) {
			return section.substring(0, existing.start()) + text + section.substring(existing.end());
		}
		return insertTag(section, item, "CONDITIONS", text.toString());
	}

	/** Gives a layer a name, as the comment line just above it, replacing the one it has. A blank name removes it. */
	public static String setName(String section, int portrait, int layer, String name) {
		Located face = face(section, portrait);
		List<Piece> pieces = pieces(section, face);
		Piece piece = pieces.get(layer);
		String clean = name == null ? "" : name.replaceAll("[\\r\\n]+", " ").strip();
		String lead = piece.lead();
		int at = lastCommentLine(lead);
		String indent = itemIndent(section, face, pieces);
		String newLead;
		if (at >= 0) {
			int lineEnd = lead.indexOf('\n', at);
			String lineIndent = lead.substring(at, at + leadingSpace(lead, at));
			if (clean.isEmpty()) {
				newLead = lead.substring(0, at) + (lineEnd < 0 ? "" : lead.substring(lineEnd + 1));
			} else {
				newLead = lead.substring(0, at) + lineIndent + "** " + clean + (lineEnd < 0 ? "" : lead.substring(lineEnd));
			}
		} else if (clean.isEmpty()) {
			return section;
		} else {
			int lastBreak = lead.lastIndexOf('\n');
			newLead = lastBreak < 0 ? lead + "\n" + indent + "** " + clean + "\n" + indent : lead.substring(0, lastBreak + 1) + indent + "** " + clean + "\n" + lead.substring(lastBreak + 1);
		}
		int start = layer == 0 ? face.start() + 1 : piece.leadStart();
		return section.substring(0, start) + newLead + section.substring(start + lead.length());
	}

	// ---- Addons and colours

	/** Writes CROWN, RAIDER or MERC as a list of blocks, which RCrown reads as it reads one (RCrown.java:36-43). */
	public static String setAddons(String section, String kind, List<Addon> addons) {
		Located value = top(section, kind);
		String indent = value == null ? "" : indentOf(section, value.keyStart());
		StringBuilder text = new StringBuilder("[\n");
		for (Addon addon : addons) {
			text.append(indent).append("\t{\n");
			text.append(indent).append("\t\tFILE: ").append(addon.file()).append(",\n");
			text.append(indent).append("\t\tOFFX: ").append(addon.offX()).append(",\n");
			text.append(indent).append("\t\tOFFY: ").append(addon.offY()).append(",\n");
			text.append(indent).append("\t},\n");
		}
		text.append(indent).append(']');
		if (value != null) {
			return section.substring(0, value.start()) + text + section.substring(value.end());
		}
		return kind + ": " + text + ",\n" + section;
	}

	/** Sets a colour's entries and flags, adding the colour to COLORS if it isn't there. A null flag is removed. */
	public static String setColour(String section, String name, List<Rgb> values, Map<String, String> flags) {
		Located colors = top(section, "COLORS");
		if (colors == null || section.charAt(colors.start()) != '{') {
			throw new IllegalStateException("COLORS isn't a block.");
		}
		Located entry = null;
		for (Located candidate : TagLocator.entries(section, colors)) {
			if (candidate.key().equals(name)) {
				entry = candidate;
			}
		}
		String text = section;
		if (entry == null) {
			String indent = closingIndent(text, colors.end() - 1) + "\t";
			text = insertTag(text, colors, name, "{\n" + indent + "}");
			entry = find(TagLocator.entries(text, top(text, "COLORS")), name);
		}
		String indent = closingIndent(text, entry.end() - 1) + "\t";
		StringBuilder list = new StringBuilder("[\n");
		for (Rgb rgb : values) {
			list.append(indent).append('\t').append(rgb.text()).append(",\n");
		}
		list.append(indent).append(']');
		Located valuesTag = find(TagLocator.entries(text, entry), "VALUES");
		if (valuesTag != null) {
			text = text.substring(0, valuesTag.start()) + list + text.substring(valuesTag.end());
		} else {
			text = insertTag(text, entry, "VALUES", list.toString());
		}
		return setColourFlags(text, name, flags);
	}

	/** Sets or removes a colour's plain tags, such as TURNS_GRAY_WHEN_OLD, leaving its VALUES as written. */
	public static String setColourFlags(String section, String name, Map<String, String> flags) {
		String text = section;
		for (Map.Entry<String, String> flag : flags.entrySet()) {
			Located again = find(TagLocator.entries(text, top(text, "COLORS")), name);
			if (again == null) {
				throw new IllegalStateException("There's no colour called " + name + ".");
			}
			text = setIn(text, again, flag.getKey(), flag.getValue());
		}
		return text;
	}

	/** Points every frame on a face sheet, or every addon naming an addon sheet, at another sheet. */
	public static String renameSheet(String section, boolean addon, String from, String to) {
		List<int[]> ranges = new ArrayList<>();
		if (addon) {
			for (String kind : ADDON_KINDS) {
				Located value = top(section, kind);
				if (value == null) {
					continue;
				}
				List<Located> blocks = section.charAt(value.start()) == '[' ? TagLocator.items(section, value) : List.of(value);
				for (Located block : blocks) {
					if (section.charAt(block.start()) == '{') {
						Located file = find(TagLocator.entries(section, block), "FILE");
						if (file != null && section.substring(file.start(), file.end()).strip().equals(from)) {
							ranges.add(trimmed(section, file.start(), file.end()));
						}
					}
				}
			}
		} else {
			for (int p = 0; p < portraits(section).size(); p++) {
				Located face = face(section, p);
				if (face == null || section.charAt(face.start()) != '[') {
					continue;
				}
				for (Located item : TagLocator.items(section, face)) {
					if (section.charAt(item.start()) != '{') {
						continue;
					}
					Located frames = find(TagLocator.entries(section, item), "FRAMES");
					if (frames == null || section.charAt(frames.start()) != '[') {
						continue;
					}
					for (Located entry : TagLocator.items(section, frames)) {
						int colon = section.indexOf(':', entry.start());
						if (colon < 0 || colon >= entry.end()) {
							continue;
						}
						int[] file = trimmed(section, entry.start(), colon);
						if (section.substring(file[0], file[1]).equals(from)) {
							ranges.add(file);
						}
					}
				}
			}
		}
		StringBuilder text = new StringBuilder(section);
		ranges.sort((a, b) -> Integer.compare(b[0], a[0]));
		for (int[] range : ranges) {
			text.replace(range[0], range[1], to);
		}
		return text.toString();
	}

	// ---- Where things are

	private static List<Located> topEntries(String section) {
		return TagLocator.scan(section);
	}

	private static Located top(String section, String key) {
		return find(topEntries(section), key);
	}

	private static Located find(List<Located> entries, String key) {
		Located found = null;
		for (Located entry : entries) {
			if (entry.key().equals(key)) {
				found = entry;
			}
		}
		return found;
	}

	private static List<Located> adultBlocks(String section) {
		Located types = top(section, "TYPES");
		if (types == null || section.charAt(types.start()) != '[') {
			return List.of();
		}
		return TagLocator.items(section, types).stream().filter(item -> section.charAt(item.start()) == '{').toList();
	}

	private static Located childBlock(String section) {
		Located child = top(section, "CHILD");
		return child != null && section.charAt(child.start()) == '{' ? child : null;
	}

	private static Located portraitBlock(String section, int portrait) {
		List<Located> adults = adultBlocks(section);
		if (portrait >= 0 && portrait < adults.size()) {
			return adults.get(portrait);
		}
		if (portrait == adults.size() && childBlock(section) != null) {
			return childBlock(section);
		}
		throw new IllegalArgumentException("There's no portrait " + portrait + ".");
	}

	private static Located face(String section, int portrait) {
		Located face = find(TagLocator.entries(section, portraitBlock(section, portrait)), "FACE");
		return face != null && section.charAt(face.start()) == '[' ? face : null;
	}

	private static Located layerItem(String section, int portrait, int layer) {
		Located face = face(section, portrait);
		if (face == null) {
			throw new IllegalArgumentException("This portrait has no layers.");
		}
		List<Located> items = TagLocator.items(section, face);
		if (layer < 0 || layer >= items.size() || section.charAt(items.get(layer).start()) != '{') {
			throw new IllegalArgumentException("There's no layer " + (layer + 1) + ".");
		}
		return items.get(layer);
	}

	/**
	 * A layer's text and the text before it, back to the previous layer's comma: its comments and blank lines.
	 * A layer written by this has no lead yet, only its own text, which starts with its name.
	 */
	private record Piece(String lead, Integer leadAt, String body) {
		int leadStart() {
			return leadAt;
		}
	}

	private static List<Piece> pieces(String section, Located face) {
		List<Piece> pieces = new ArrayList<>();
		int from = face.start() + 1;
		for (Located item : TagLocator.items(section, face)) {
			pieces.add(new Piece(section.substring(from, item.start()), from, section.substring(item.start(), item.end())));
			from = item.end() + (item.hasComma() ? 1 : 0);
		}
		return pieces;
	}

	/** The text after the last layer, up to the list's closing bracket. */
	private static String tail(String section, Located face) {
		List<Located> items = TagLocator.items(section, face);
		int from = items.isEmpty() ? face.start() + 1 : items.get(items.size() - 1).end() + (items.get(items.size() - 1).hasComma() ? 1 : 0);
		return section.substring(from, face.end() - 1);
	}

	private static String rebuild(String section, Located face, List<Piece> pieces, String indent) {
		StringBuilder list = new StringBuilder("[");
		for (Piece piece : pieces) {
			if (piece.lead() == null) {
				list.append(piece.body()).append(',');
			} else {
				list.append(piece.lead()).append(piece.body()).append(',');
			}
		}
		String tail = tail(section, face);
		if (tail.isBlank()) {
			tail = "\n" + indent.substring(0, Math.max(0, indent.length() - 1));
		}
		list.append(tail).append(']');
		return section.substring(0, face.start()) + list + section.substring(face.end());
	}

	/** A new layer's text, from the line break before its name to its closing brace. */
	private static String layerText(String name, List<FrameRef> frames, String indent) {
		StringBuilder text = new StringBuilder("\n");
		String clean = name == null ? "" : name.replaceAll("[\\r\\n]+", " ").strip();
		if (!clean.isEmpty()) {
			text.append(indent).append("** ").append(clean).append('\n');
		}
		text.append(indent).append("{\n");
		text.append(indent).append("\tFRAMES: ").append(framesText(frames, indent + "\t")).append(",\n");
		text.append(indent).append('}');
		return text.toString();
	}

	private static String framesText(List<FrameRef> frames, String indent) {
		StringBuilder text = new StringBuilder("[\n");
		for (FrameRef frame : frames) {
			text.append(indent).append('\t').append(frame.text()).append(",\n");
		}
		return text.append(indent).append(']').toString();
	}

	/** How the layers are indented: as the first one is, or one more than FACE. */
	private static String itemIndent(String section, Located face, List<Piece> pieces) {
		for (Piece piece : pieces) {
			if (piece.lead() != null) {
				int start = piece.leadStart() + piece.lead().length();
				String indent = indentOf(section, start);
				if (section.substring(start - indent.length(), start).equals(indent)) {
					return indent;
				}
			}
		}
		return indentOf(section, face.keyStart()) + "\t";
	}

	/** The name is the last ** comment line in the text before a layer. */
	private static String name(String section, List<Located> items, int index, Located face) {
		int from = index == 0 ? face.start() + 1 : items.get(index - 1).end() + (items.get(index - 1).hasComma() ? 1 : 0);
		String lead = section.substring(from, items.get(index).start());
		int at = lastCommentLine(lead);
		if (at < 0) {
			return null;
		}
		int end = lead.indexOf('\n', at);
		String line = (end < 0 ? lead.substring(at) : lead.substring(at, end)).strip();
		String name = line.replaceFirst("^\\*+", "").strip();
		return name.isEmpty() ? null : name;
	}

	/** Where the last line starting with ** begins in a layer's lead, or -1. */
	private static int lastCommentLine(String lead) {
		int found = -1;
		int lineStart = 0;
		while (lineStart <= lead.length()) {
			int end = lead.indexOf('\n', lineStart);
			String line = end < 0 ? lead.substring(lineStart) : lead.substring(lineStart, end);
			if (line.strip().startsWith("**")) {
				found = lineStart;
			}
			if (end < 0) {
				break;
			}
			lineStart = end + 1;
		}
		return found;
	}

	private static int leadingSpace(String text, int from) {
		int i = from;
		while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
			i++;
		}
		return i - from;
	}

	/** The whitespace at the start of the line holding this offset. */
	private static String indentOf(String text, int offset) {
		int start = offset;
		while (start > 0 && text.charAt(start - 1) != '\n' && text.charAt(start - 1) != '\r') {
			start--;
		}
		int end = start;
		while (end < text.length() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) {
			end++;
		}
		return text.substring(start, end);
	}

	private static String closingIndent(String text, int close) {
		int start = close;
		while (start > 0 && (text.charAt(start - 1) == ' ' || text.charAt(start - 1) == '\t')) {
			start--;
		}
		return start == 0 || text.charAt(start - 1) == '\n' || text.charAt(start - 1) == '\r' ? text.substring(start, close) : indentOf(text, close);
	}

	/** Adds a tag at the end of a block, on its own line, indented one more than the block's closing brace. */
	private static String insertTag(String section, Located block, String key, String value) {
		StringBuilder text = new StringBuilder(section);
		int close = block.end() - 1;
		String indent = closingIndent(section, close);
		boolean onOwnLine = close > 0 && section.substring(lineStart(section, close), close).isBlank();
		String line = "\t" + key + ": " + value + ",\n" + indent;
		if (onOwnLine) {
			text.insert(close, line);
		} else {
			text.insert(close, "\n" + indent + line);
		}
		List<Located> entries = TagLocator.entries(section, block);
		if (!entries.isEmpty() && !entries.get(entries.size() - 1).hasComma()) {
			text.insert(entries.get(entries.size() - 1).end(), ',');
		}
		return text.toString();
	}

	private static int lineStart(String text, int offset) {
		int start = offset;
		while (start > 0 && text.charAt(start - 1) != '\n' && text.charAt(start - 1) != '\r') {
			start--;
		}
		return start;
	}

	private static String setIn(String section, Located block, String key, String value) {
		Located existing = find(TagLocator.entries(section, block), key);
		if (value == null) {
			if (existing == null) {
				return section;
			}
			int[] range = TagLocator.entryRange(section, existing);
			return section.substring(0, range[0]) + section.substring(range[1]);
		}
		if (existing != null) {
			return section.substring(0, existing.start()) + value + section.substring(existing.end());
		}
		return insertTag(section, block, key, value);
	}

	private static int[] trimmed(String text, int start, int end) {
		int s = start;
		int e = end;
		while (s < e && Character.isWhitespace(text.charAt(s))) {
			s++;
		}
		while (e > s && Character.isWhitespace(text.charAt(e - 1))) {
			e--;
		}
		return new int[] { s, e };
	}

	/** A name a person typed, made safe to be a key or a comment: letters, digits and _ only, in capitals. */
	public static String keyFrom(String typed) {
		String key = typed == null ? "" : typed.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_").replaceAll("^_+|_+$", "");
		return key.isEmpty() ? null : key;
	}
}
