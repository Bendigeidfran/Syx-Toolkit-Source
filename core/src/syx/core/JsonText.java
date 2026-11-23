package syx.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain JSON, for the handbook's tag data and for talking to agents. It has nothing to do with the
 * game's data files, which only the game's parser ever reads.
 */
public final class JsonText {

	/** Deeper than anything real, and stops hostile input from overflowing the stack. */
	private static final int MAX_DEPTH = 200;

	private JsonText() {
	}

	/** Objects become maps, arrays lists, and numbers doubles. Throws IllegalStateException or NumberFormatException on bad input. */
	public static Object parse(String text) {
		return new Reader(text).readDocument();
	}

	/** On one line, so it can be a single message in a stream of lines. */
	public static String write(Object value) {
		StringBuilder out = new StringBuilder();
		write(value, out, -1);
		return out.toString();
	}

	/** Indented, for people to read. */
	public static String writePretty(Object value) {
		StringBuilder out = new StringBuilder();
		write(value, out, 0);
		return out.toString();
	}

	private static void write(Object value, StringBuilder out, int depth) {
		if (value instanceof Map<?, ?> map) {
			writeMap(map, out, depth);
		} else if (value instanceof Iterable<?> list) {
			writeList(list, out, depth);
		} else if (value instanceof String || value instanceof Enum<?>) {
			quote(value.toString(), out);
		} else if (value instanceof Double number && number == Math.rint(number) && !Double.isInfinite(number)) {
			out.append(number.longValue());
		} else if (value == null || value instanceof Number || value instanceof Boolean) {
			out.append(value);
		} else {
			quote(value.toString(), out);
		}
	}

	private static void writeMap(Map<?, ?> map, StringBuilder out, int depth) {
		if (map.isEmpty()) {
			out.append("{}");
			return;
		}
		out.append('{');
		boolean first = true;
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (!first) {
				out.append(',');
			}
			first = false;
			newLine(out, depth < 0 ? -1 : depth + 1);
			quote(String.valueOf(entry.getKey()), out);
			out.append(depth < 0 ? ":" : ": ");
			write(entry.getValue(), out, depth < 0 ? -1 : depth + 1);
		}
		newLine(out, depth);
		out.append('}');
	}

	private static void writeList(Iterable<?> list, StringBuilder out, int depth) {
		if (!list.iterator().hasNext()) {
			out.append("[]");
			return;
		}
		out.append('[');
		boolean first = true;
		for (Object item : list) {
			if (!first) {
				out.append(',');
			}
			first = false;
			newLine(out, depth < 0 ? -1 : depth + 1);
			write(item, out, depth < 0 ? -1 : depth + 1);
		}
		newLine(out, depth);
		out.append(']');
	}

	private static void newLine(StringBuilder out, int depth) {
		if (depth >= 0) {
			out.append('\n').append("  ".repeat(depth));
		}
	}

	private static void quote(String text, StringBuilder out) {
		out.append('"');
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (c) {
			case '"' -> out.append("\\\"");
			case '\\' -> out.append("\\\\");
			case '\n' -> out.append("\\n");
			case '\r' -> out.append("\\r");
			case '\t' -> out.append("\\t");
			default -> {
				if (c < 0x20 || c == 0x2028 || c == 0x2029) {
					out.append(String.format("\\u%04x", (int) c));
				} else {
					out.append(c);
				}
			}
			}
		}
		out.append('"');
	}

	private static final class Reader {
		private final String text;
		private int i;
		private int depth;

		Reader(String text) {
			this.text = text;
		}

		Object readDocument() {
			Object value = readValue();
			skipSpace();
			if (i != text.length()) {
				throw new IllegalStateException("extra content at " + i);
			}
			return value;
		}

		private Object readValue() {
			skipSpace();
			if (i >= text.length()) {
				throw new IllegalStateException("the file ended early");
			}
			char c = text.charAt(i);
			return switch (c) {
			case '{' -> nested(this::readObject);
			case '[' -> nested(this::readArray);
			case '"' -> readString();
			case 't' -> readWord("true", Boolean.TRUE);
			case 'f' -> readWord("false", Boolean.FALSE);
			case 'n' -> readWord("null", null);
			default -> readNumber();
			};
		}

		private Object nested(java.util.function.Supplier<Object> read) {
			if (++depth > MAX_DEPTH) {
				throw new IllegalStateException("nested too deeply at " + i);
			}
			Object value = read.get();
			depth--;
			return value;
		}

		private Map<String, Object> readObject() {
			Map<String, Object> map = new LinkedHashMap<>();
			i++;
			skipSpace();
			if (peek() == '}') {
				i++;
				return map;
			}
			while (true) {
				skipSpace();
				if (peek() != '"') {
					throw new IllegalStateException("expected a name at " + i);
				}
				String key = readString();
				skipSpace();
				expect(':');
				map.put(key, readValue());
				skipSpace();
				char c = next();
				if (c == '}') {
					return map;
				}
				if (c != ',') {
					throw new IllegalStateException("expected , or } at " + i);
				}
			}
		}

		private List<Object> readArray() {
			List<Object> list = new ArrayList<>();
			i++;
			skipSpace();
			if (peek() == ']') {
				i++;
				return list;
			}
			while (true) {
				list.add(readValue());
				skipSpace();
				char c = next();
				if (c == ']') {
					return list;
				}
				if (c != ',') {
					throw new IllegalStateException("expected , or ] at " + i);
				}
			}
		}

		private String readString() {
			StringBuilder out = new StringBuilder();
			i++;
			while (true) {
				char c = next();
				if (c == '"') {
					return out.toString();
				}
				if (c < 0x20) {
					throw new IllegalStateException("control character in text at " + i);
				}
				if (c != '\\') {
					out.append(c);
					continue;
				}
				char escape = next();
				switch (escape) {
				case '"', '\\', '/' -> out.append(escape);
				case 'b' -> out.append('\b');
				case 'f' -> out.append('\f');
				case 'n' -> out.append('\n');
				case 'r' -> out.append('\r');
				case 't' -> out.append('\t');
				case 'u' -> {
					if (i + 4 > text.length()) {
						throw new IllegalStateException("the file ended early");
					}
					out.append((char) Integer.parseInt(text.substring(i, i + 4), 16));
					i += 4;
				}
				default -> throw new IllegalStateException("unknown escape at " + i);
				}
			}
		}

		private Object readWord(String word, Object value) {
			if (!text.startsWith(word, i)) {
				throw new IllegalStateException("unknown value at " + i);
			}
			i += word.length();
			return value;
		}

		private Double readNumber() {
			int start = i;
			while (i < text.length() && "+-.eE0123456789".indexOf(text.charAt(i)) >= 0) {
				i++;
			}
			if (start == i) {
				throw new IllegalStateException("unknown value at " + i);
			}
			return Double.valueOf(text.substring(start, i));
		}

		private void skipSpace() {
			while (i < text.length() && " \t\r\n".indexOf(text.charAt(i)) >= 0) {
				i++;
			}
		}

		private char peek() {
			if (i >= text.length()) {
				throw new IllegalStateException("the file ended early");
			}
			return text.charAt(i);
		}

		private char next() {
			char c = peek();
			i++;
			return c;
		}

		private void expect(char wanted) {
			if (next() != wanted) {
				throw new IllegalStateException("expected " + wanted + " at " + i);
			}
		}
	}
}
