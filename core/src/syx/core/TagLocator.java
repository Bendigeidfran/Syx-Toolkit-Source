package syx.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds where tags and their values sit in a file's text, following the steps of JsonValue.Reader.
 * This only locates text. Whatever uses it must check the keys against the game's parser, and have
 * that parser read any result, so this is never the authority.
 */
final class TagLocator {

	/** Where a tag and its value sit. The end is exclusive. A list item's key is empty. */
	record Located(String key, int keyStart, int start, int end, boolean hasComma) {
	}

	private TagLocator() {
	}

	/** Every tag in a file's text. A layout the game's parser wouldn't accept throws a RuntimeException. */
	static List<Located> scan(String content) {
		return scan(content, 0);
	}

	/** Every tag inside a block value, in positions of the whole text. */
	static List<Located> entries(String content, Located block) {
		return scan(content.substring(block.start() + 1, block.end() - 1), block.start() + 1);
	}

	/** Every item of a list value, in positions of the whole text. */
	static List<Located> items(String content, Located list) {
		Scanner scanner = new Scanner(content.substring(list.start() + 1, list.end() - 1), list.start() + 1);
		List<Located> found = new ArrayList<>();
		while (scanner.nextChar()) {
			found.add(scanner.value("", scanner.position()));
		}
		return found;
	}

	private static List<Located> scan(String content, int base) {
		Scanner scanner = new Scanner(content, base);
		List<Located> found = new ArrayList<>();
		while (scanner.nextChar()) {
			found.add(scanner.next());
		}
		return found;
	}

	/** The range to delete to remove a tag: its whole line if nothing else is on it. A comment beside it is kept. */
	static int[] entryRange(CharSequence text, Located place) {
		int from = place.keyStart();
		int to = place.end() + (place.hasComma() ? 1 : 0);
		int lineStart = from;
		while (lineStart > 0 && isSpace(text.charAt(lineStart - 1))) {
			lineStart--;
		}
		int after = to;
		while (after < text.length() && isSpace(text.charAt(after))) {
			after++;
		}
		boolean startsLine = lineStart == 0 || isLineBreak(text.charAt(lineStart - 1));
		boolean endsLine = after == text.length() || isLineBreak(text.charAt(after));
		if (!startsLine || !endsLine) {
			return new int[] { from, after };
		}
		if (after < text.length() && text.charAt(after) == '\r') {
			after++;
		}
		if (after < text.length() && text.charAt(after) == '\n') {
			after++;
		}
		return new int[] { lineStart, after };
	}

	private static boolean isSpace(char c) {
		return c == ' ' || c == '\t';
	}

	private static boolean isLineBreak(char c) {
		return c == '\n' || c == '\r';
	}

	private static final class Scanner {
		private final String content;
		private final int base;
		private int i;

		Scanner(String content, int base) {
			this.content = content;
			this.base = base;
		}

		int position() {
			return i;
		}

		private boolean isNewline() {
			char c = content.charAt(i);
			if (c == '\r' && i < content.length() - 2 && content.charAt(i + 1) == '\n') {
				i += 2;
				return true;
			}
			if (c == '\r' || c == '\n') {
				i += 1;
				return true;
			}
			return false;
		}

		boolean nextChar() {
			boolean comment = false;
			while (true) {
				if (i >= content.length() - 1) {
					return false;
				}
				if (content.charAt(i) == '*' && i < content.length() - 1 && content.charAt(i + 1) == '*') {
					comment = true;
					i++;
					continue;
				}
				if (isNewline()) {
					comment = false;
					continue;
				}
				char c = content.charAt(i);
				if (comment || c == ' ' || c == '\t') {
					i++;
				} else {
					return true;
				}
			}
		}

		Located next() {
			int keyStart = i;
			int end = content.indexOf(':', i);
			if (end < 0) {
				throw new IllegalStateException("no key");
			}
			String key = content.substring(i, end).trim();
			i = end + 1;
			nextChar();
			return value(key, keyStart);
		}

		Located value(String key, int keyStart) {
			char c = content.charAt(i);
			if (c == '"' || c == '{' || c == '[') {
				return enclosed(key, keyStart, c, c == '"' ? '"' : c == '{' ? '}' : ']');
			}
			return plain(key, keyStart);
		}

		private Located enclosed(String key, int keyStart, char open, char close) {
			int start = i;
			int nesting = 0;
			i++;
			while (true) {
				if (i >= content.length()) {
					throw new IllegalStateException("not closed");
				}
				if (isNewline()) {
					continue;
				}
				char c = content.charAt(i);
				if (c == close) {
					boolean atEnd = i == content.length() - 1;
					if ((atEnd || content.charAt(i + 1) == ',') && nesting == 0) {
						Located located = new Located(key, base + keyStart, base + start, base + i + 1, !atEnd);
						i += 2;
						return located;
					}
					nesting--;
				}
				if (c == open) {
					nesting++;
				}
				i++;
			}
		}

		private Located plain(String key, int keyStart) {
			int start = i;
			while (++i < content.length()) {
				if (isNewline()) {
					throw new IllegalStateException("no comma");
				}
				if (content.charAt(i) == ',') {
					i++;
					return new Located(key, base + keyStart, base + start, base + i - 1, true);
				}
			}
			throw new IllegalStateException("no comma");
		}
	}
}
