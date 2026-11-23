package syx.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import syx.core.TagLocator.Located;

/**
 * Some of a race's sprite file's tags, cut out as one piece of text and spliced back. Which tags is a {@link Spec}: some
 * at the top of the file, and some inside each TYPES block and CHILD. The text of each value is the file's own, so
 * formatting and comments inside it survive, and everything else in the file stays as it was. The Race Portrait Sim
 * and the Map Sprite Sim each edit their own tags of the same file this way.
 */
final class SpriteFileSection {

	/**
	 * Which tags a section holds.
	 *
	 * @param top the tags at the top of the file, in the order the section lists them
	 * @param inner the tags inside each TYPES block and CHILD, in the order the section lists them
	 * @param noun what a tag of this section is called in messages, such as "portrait tag"
	 */
	record Spec(List<String> top, List<String> inner, String noun) {
		Set<String> allowedTop() {
			Set<String> allowed = new LinkedHashSet<>(top);
			allowed.add(TYPES);
			allowed.add(CHILD);
			return allowed;
		}
	}

	static final String TYPES = "TYPES";
	static final String CHILD = "CHILD";

	/** A block of TYPES, or CHILD, and where its entries are. */
	record Inner(Located block, List<Located> entries) {
		Located entry(String key) {
			return entries.stream().filter(entry -> entry.key().equals(key)).findFirst().orElse(null);
		}
	}

	/** Where a section's tags are in one piece of text. */
	record Layout(String text, Map<String, Located> top, List<Located> topEntries, Located types, List<Inner> typeBlocks, Located child, Inner childBlock) {
	}

	private record Edit(int start, int end, String text) {
	}

	private SpriteFileSection() {
	}

	/** A layout that can't be located throws a RuntimeException. */
	static Layout layout(String text) {
		List<Located> entries = TagLocator.scan(text);
		Map<String, Located> top = new LinkedHashMap<>();
		for (Located entry : entries) {
			top.put(entry.key(), entry);
		}
		Located types = top.get(TYPES);
		List<Inner> blocks = new ArrayList<>();
		if (types != null && text.charAt(types.start()) == '[') {
			for (Located item : TagLocator.items(text, types)) {
				blocks.add(new Inner(item, text.charAt(item.start()) == '{' ? TagLocator.entries(text, item) : List.of()));
			}
		}
		Located child = top.get(CHILD);
		Inner childBlock = child != null && text.charAt(child.start()) == '{' ? new Inner(child, TagLocator.entries(text, child)) : null;
		return new Layout(text, top, entries, types, blocks, child, childBlock);
	}

	static String raw(String text, Located place) {
		return text.substring(place.start(), place.end()).replace("\r\n", "\n").replace('\r', '\n');
	}

	/** The section's text for a file, with \n line ends. A layout that can't be located throws a RuntimeException. */
	static String extract(Spec spec, String file) {
		Layout layout = layout(file);
		StringBuilder out = new StringBuilder();
		for (String key : spec.top()) {
			Located place = layout.top().get(key);
			if (place != null) {
				out.append(key).append(": ").append(raw(file, place)).append(",\n\n");
			}
		}
		if (layout.types() != null) {
			out.append("TYPES: [\n");
			for (Inner block : layout.typeBlocks()) {
				out.append("\t{\n");
				for (String key : spec.inner()) {
					Located place = block.entry(key);
					if (place != null) {
						out.append("\t\t").append(key).append(": ").append(raw(file, place)).append(",\n");
					}
				}
				out.append("\t},\n");
			}
			out.append("],\n\n");
		}
		if (layout.childBlock() != null) {
			out.append("CHILD: {\n");
			for (String key : spec.inner()) {
				Located place = layout.childBlock().entry(key);
				if (place != null) {
					out.append("\t").append(key).append(": ").append(raw(file, place)).append(",\n");
				}
			}
			out.append("},\n");
		}
		return out.toString();
	}

	/** Whether the locator found the same tags as the game's parser, so it's safe to splice into this text. */
	static boolean agrees(String text, GameParser.Node root) throws GameParser.ReadException {
		Layout layout;
		try {
			layout = layout(text);
		} catch (RuntimeException e) {
			return false;
		}
		if (!root.keys().equals(layout.topEntries().stream().map(Located::key).toList())) {
			return false;
		}
		if (layout.types() != null && text.charAt(layout.types().start()) == '[') {
			List<GameParser.Node> blocks = root.blocks(TYPES);
			if (blocks.size() != layout.typeBlocks().size()) {
				return false;
			}
			for (int i = 0; i < blocks.size(); i++) {
				if (!blocks.get(i).keys().equals(layout.typeBlocks().get(i).entries().stream().map(Located::key).toList())) {
					return false;
				}
			}
		}
		return layout.childBlock() == null || root.block(CHILD).keys().equals(layout.childBlock().entries().stream().map(Located::key).toList());
	}

	/** Why this text can't go back into the file, or null. The game's parser has already accepted it. */
	static PortraitSection.Issue problem(Spec spec, String section, String file) {
		Layout text;
		Layout target;
		try {
			text = layout(section);
			target = layout(file);
		} catch (RuntimeException e) {
			return new PortraitSection.Issue("This text is laid out in a way the toolkit can't place back into the file.", null);
		}
		Set<String> allowed = spec.allowedTop();
		for (Located entry : text.topEntries()) {
			if (!allowed.contains(entry.key())) {
				return notSectionTag(spec, section, entry);
			}
		}
		if ((text.types() == null) != (target.types() == null)) {
			return new PortraitSection.Issue(text.types() == null ? "TYPES can't be removed here, because the file holds other tags inside it." : "TYPES can't be added here.", null);
		}
		if (text.types() != null && text.typeBlocks().size() != target.typeBlocks().size()) {
			return new PortraitSection.Issue("TYPES has " + text.typeBlocks().size() + " blocks here and " + target.typeBlocks().size() + " in the file. Types can't be added or removed here, because each holds tags that aren't shown.",
					lineOf(section, text.types().keyStart()));
		}
		if ((text.childBlock() == null) != (target.childBlock() == null)) {
			return new PortraitSection.Issue(text.childBlock() == null ? "CHILD can't be removed here, because the file holds other tags inside it." : "CHILD can't be added here.", null);
		}
		List<Inner> blocks = new ArrayList<>(text.typeBlocks());
		if (text.childBlock() != null) {
			blocks.add(text.childBlock());
		}
		for (Inner block : blocks) {
			for (Located entry : block.entries()) {
				if (!spec.inner().contains(entry.key())) {
					return notSectionTag(spec, section, entry);
				}
			}
		}
		return null;
	}

	/** A key runs up to the next ':' (JsonValue.java:463-468), so one spanning lines means a ':' was left out. */
	private static PortraitSection.Issue notSectionTag(Spec spec, String section, Located entry) {
		int line = lineOf(section, entry.keyStart());
		if (entry.key().contains("\n") || entry.key().contains("\r")) {
			return new PortraitSection.Issue("This line is missing its ':'.", line);
		}
		return new PortraitSection.Issue(entry.key() + " isn't a " + spec.noun() + ", so it can't be edited here.", line);
	}

	static int lineOf(String text, int offset) {
		int line = 1;
		for (int i = 0; i < offset; i++) {
			if (text.charAt(i) == '\n') {
				line++;
			}
		}
		return line;
	}

	/** The file with the section's values in it. Values that didn't change keep their exact bytes. */
	static String apply(Spec spec, String section, String file) throws PortraitSection.Mismatch {
		PortraitSection.Issue problem = problem(spec, section, file);
		if (problem != null) {
			throw new PortraitSection.Mismatch(problem.message());
		}
		Layout text = layout(section);
		Layout target = layout(file);
		String eol = file.contains("\r\n") ? "\r\n" : "\n";
		List<Edit> edits = new ArrayList<>();
		StringBuilder appended = new StringBuilder();
		for (String key : spec.top()) {
			Located now = text.top().get(key);
			Located was = target.top().get(key);
			if (now != null && was != null) {
				replace(edits, section, now, file, was, eol);
			} else if (was != null) {
				int[] range = TagLocator.entryRange(file, was);
				edits.add(new Edit(range[0], range[1], ""));
			} else if (now != null) {
				appended.append(key).append(": ").append(raw(section, now).replace("\n", eol)).append(",").append(eol);
			}
		}
		for (int i = 0; i < target.typeBlocks().size(); i++) {
			inner(spec, edits, section, text.typeBlocks().get(i), file, target.typeBlocks().get(i), eol);
		}
		if (target.childBlock() != null) {
			inner(spec, edits, section, text.childBlock(), file, target.childBlock(), eol);
		}
		edits.sort(Comparator.comparingInt(Edit::start).reversed());
		StringBuilder result = new StringBuilder(file);
		for (Edit edit : edits) {
			result.replace(edit.start(), edit.end(), edit.text());
		}
		if (appended.length() > 0) {
			appendTop(result, target, appended, eol);
		}
		return result.toString();
	}

	private static void appendTop(StringBuilder result, Layout target, StringBuilder appended, String eol) {
		if (!target.topEntries().isEmpty() && !target.topEntries().get(target.topEntries().size() - 1).hasComma()) {
			result.append(',');
		}
		if (result.length() > 0 && result.charAt(result.length() - 1) != '\n') {
			result.append(eol);
		}
		result.append(appended);
	}

	private static void replace(List<Edit> edits, String section, Located now, String file, Located was, String eol) {
		if (!raw(section, now).equals(raw(file, was))) {
			edits.add(new Edit(was.start(), was.end(), raw(section, now).replace("\n", eol)));
		}
	}

	private static void inner(Spec spec, List<Edit> edits, String section, Inner now, String file, Inner was, String eol) {
		List<String> inserted = new ArrayList<>();
		for (String key : spec.inner()) {
			Located nowEntry = now.entry(key);
			Located wasEntry = was.entry(key);
			if (nowEntry != null && wasEntry != null) {
				replace(edits, section, nowEntry, file, wasEntry, eol);
			} else if (wasEntry != null) {
				int[] range = TagLocator.entryRange(file, wasEntry);
				edits.add(new Edit(range[0], range[1], ""));
			} else if (nowEntry != null) {
				inserted.add(key);
			}
		}
		if (inserted.isEmpty()) {
			return;
		}
		if (!was.entries().isEmpty() && !was.entries().get(was.entries().size() - 1).hasComma()) {
			int end = was.entries().get(was.entries().size() - 1).end();
			edits.add(new Edit(end, end, ","));
		}
		int close = was.block().end() - 1;
		String indent = closingIndent(file, close);
		StringBuilder text = new StringBuilder();
		for (String key : inserted) {
			text.append("\t").append(key).append(": ").append(raw(section, now.entry(key)).replace("\n", eol)).append(",").append(eol).append(indent);
		}
		edits.add(new Edit(close, close, text.toString()));
	}

	static String closingIndent(String text, int close) {
		int start = close;
		while (start > 0 && (text.charAt(start - 1) == ' ' || text.charAt(start - 1) == '\t')) {
			start--;
		}
		return start == 0 || text.charAt(start - 1) == '\n' || text.charAt(start - 1) == '\r' ? text.substring(start, close) : "";
	}

	/** The file's tree with the section's tags in place of its own. What a saved file must read back as. */
	static GameTree.Block merged(Spec spec, GameTree.Block file, GameTree.Block section) {
		GameTree.Block result = file.copy();
		for (String key : spec.top()) {
			if (section.has(key)) {
				result.put(key, section.get(key));
			} else {
				result.remove(key);
			}
		}
		if (result.get(TYPES) instanceof GameTree.Blocks types && section.get(TYPES) instanceof GameTree.Blocks given) {
			List<GameTree.Block> blocks = new ArrayList<>();
			for (int i = 0; i < types.items().size(); i++) {
				blocks.add(withInner(spec, types.items().get(i), given.items().get(i)));
			}
			result.put(TYPES, new GameTree.Blocks(blocks));
		}
		if (result.get(CHILD) instanceof GameTree.Block child && section.get(CHILD) instanceof GameTree.Block given) {
			result.put(CHILD, withInner(spec, child, given));
		}
		return result;
	}

	private static GameTree.Block withInner(Spec spec, GameTree.Block block, GameTree.Block section) {
		GameTree.Block result = block.copy();
		for (String key : spec.inner()) {
			if (section.has(key)) {
				result.put(key, section.get(key));
			} else {
				result.remove(key);
			}
		}
		return result;
	}

	/** Adds a block, and the tags with a default it doesn't set, to a list of absent defaults. */
	static void addBlock(List<PortraitSection.AbsentBlock> blocks, String section, String path, Located block, Map<String, String> defaults) {
		Set<String> present = new java.util.HashSet<>();
		for (Located entry : TagLocator.entries(section, block)) {
			present.add(entry.key());
		}
		List<PortraitSection.Absent> absent = new ArrayList<>();
		defaults.forEach((key, value) -> {
			if (!present.contains(key)) {
				absent.add(new PortraitSection.Absent(key, value));
			}
		});
		absent.sort(Comparator.comparing(PortraitSection.Absent::key));
		blocks.add(new PortraitSection.AbsentBlock(path, block.start(), block.end(), absent));
	}

	/** COLORS' blocks and the flags RColors.java:105-107 reads with a fallback of false. */
	static void colourBlocks(List<PortraitSection.AbsentBlock> blocks, String section, Layout layout) {
		Located colors = layout.top().get("COLORS");
		if (colors != null && section.charAt(colors.start()) == '{') {
			for (Located entry : TagLocator.entries(section, colors)) {
				if (section.charAt(entry.start()) == '{') {
					addBlock(blocks, section, "COLORS." + entry.key(), entry, Map.of("TURNS_GRAY_WHEN_OLD", "false", "TURNS_WHITE_WHEN_DEAD", "false", "TURNS_SICKLY", "false"));
				}
			}
		}
	}
}
