package syx.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A whole file as the game's parser reads it, so what a form can't show can be kept when the file is
 * saved. The parser decides what every value is. Nothing here reads a file's text, and whatever is
 * written from a tree must be parsed again and match it, so this is never the authority.
 */
public final class GameTree {

	/** One value in a file, in the forms the game's parser tells apart (JsonValue.java). */
	public sealed interface Value permits Text, Plain, Texts, Plains, Lists, Blocks, Block {
	}

	public record Text(String text) implements Value {
	}

	public record Plain(String value) implements Value {
	}

	public record Texts(List<String> items) implements Value {
	}

	public record Plains(List<String> items) implements Value {
	}

	public record Lists(List<List<String>> items) implements Value {
	}

	public record Blocks(List<Block> items) implements Value {
	}

	/** A block's entries, in file order. The game doesn't depend on the order, so equality ignores it. */
	public record Block(Map<String, Value> entries) implements Value {
		public static Block empty() {
			return new Block(new LinkedHashMap<>());
		}

		public Block copy() {
			return new Block(new LinkedHashMap<>(entries));
		}

		public Value get(String key) {
			return entries.get(key);
		}

		public boolean has(String key) {
			return entries.containsKey(key);
		}

		public void put(String key, Value value) {
			entries.put(key, value);
		}

		public void remove(String key) {
			entries.remove(key);
		}
	}

	private GameTree() {
	}

	/** Reads everything under a node. A list the game could never read as one kind is refused, as the game would. */
	public static Block read(GameParser.Node node) throws GameParser.ReadException {
		Block block = Block.empty();
		for (String key : node.keys()) {
			block.put(key, value(node, key));
		}
		return block;
	}

	private static Value value(GameParser.Node node, String key) throws GameParser.ReadException {
		if (node.isBlock(key)) {
			return read(node.block(key));
		}
		if (node.isText(key)) {
			return new Text(node.text(key));
		}
		if (!node.isList(key)) {
			return new Plain(node.value(key));
		}
		// An array's kind is only known by which read accepts it (JsonValue.java:144-255). An empty one is accepted by all.
		GameParser.ReadException refused;
		try {
			return new Plains(List.of(node.values(key)));
		} catch (GameParser.ReadException e) {
			refused = e;
		}
		try {
			return new Texts(List.of(node.texts(key)));
		} catch (GameParser.ReadException e) {
			refused = e;
		}
		try {
			List<Block> blocks = new ArrayList<>();
			for (GameParser.Node item : node.blocks(key)) {
				blocks.add(read(item));
			}
			return new Blocks(blocks);
		} catch (GameParser.ReadException e) {
			refused = e;
		}
		try {
			List<List<String>> lists = new ArrayList<>();
			for (String[] items : node.lists(key)) {
				lists.add(Arrays.asList(items));
			}
			return new Lists(lists);
		} catch (GameParser.ReadException e) {
			refused = e;
		}
		throw refused;
	}

	/** Writes a tree the way the toolkit writes its own files. */
	public static String write(Block root) {
		StringBuilder out = new StringBuilder();
		writeEntries(out, root, "");
		return out.toString();
	}

	/** A value as the toolkit would write it, on one line, so it can be shown beside its tag. */
	public static String oneLine(Value value) {
		StringBuilder out = new StringBuilder();
		writeValue(out, value, "");
		return out.toString().replaceAll("\\s*\\n\\s*", " ").replace("{ ", "{").replace("[ ", "[").trim();
	}

	private static void writeEntries(StringBuilder out, Block block, String indent) {
		block.entries().forEach((key, value) -> {
			out.append(indent).append(key).append(": ");
			writeValue(out, value, indent);
			out.append(",\n");
		});
	}

	private static void writeValue(StringBuilder out, Value value, String indent) {
		String inner = indent + "\t";
		switch (value) {
		case Text text -> out.append('"').append(lines(text.text(), inner)).append('"');
		case Plain plain -> out.append(plain.value());
		case Plains plains -> out.append(inline(plains.items()));
		case Texts texts -> {
			out.append("[\n");
			texts.items().forEach(item -> out.append(inner).append('"').append(lines(item, inner + "\t")).append("\",\n"));
			out.append(indent).append(']');
		}
		case Lists lists -> {
			out.append("[\n");
			lists.items().forEach(item -> out.append(inner).append(inline(item)).append(",\n"));
			out.append(indent).append(']');
		}
		case Blocks blocks -> {
			out.append("[\n");
			for (Block item : blocks.items()) {
				out.append(inner);
				writeValue(out, item, inner);
				out.append(",\n");
			}
			out.append(indent).append(']');
		}
		case Block block -> {
			out.append("{\n");
			writeEntries(out, block, inner);
			out.append(indent).append('}');
		}
		}
	}

	/**
	 * Indents the lines after the first, as the vanilla files do. The parser drops a text's empty line but
	 * keeps one holding only indentation, and leaves out each line's leading indentation, so a blank line
	 * between paragraphs survives this way and nothing else changes (vanilla's raider messages).
	 */
	private static String lines(String text, String indent) {
		return text.replace("\n", "\n" + indent);
	}

	/** The last item needs its comma too, because the game ignores a list's final character (JsonValue.java:433). */
	private static String inline(List<String> items) {
		if (items.isEmpty()) {
			return "[]";
		}
		return "[" + String.join(", ", items) + ",]";
	}
}
