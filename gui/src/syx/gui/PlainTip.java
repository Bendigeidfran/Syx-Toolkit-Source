package syx.gui;

import java.util.Locale;

/**
 * Prepares text for a tooltip. Swing renders any tooltip starting with an html tag, which can load remote
 * images, and handbook text is downloaded, so every tooltip that shows outside text goes through here.
 */
final class PlainTip {

	private static final int LINE_LENGTH = 70;

	private PlainTip() {
	}

	/** Wrapped to a readable width, and never read as HTML. Null stays null, which means no tooltip. */
	static String of(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String wrapped = wrap(text.strip());
		// Swing only looks at the very start, so a space there is enough.
		return wrapped.toLowerCase(Locale.ROOT).startsWith("<html") ? " " + wrapped : wrapped;
	}

	private static String wrap(String text) {
		StringBuilder out = new StringBuilder();
		for (String line : text.split("\n", -1)) {
			int length = 0;
			for (String word : line.split(" ")) {
				if (length > 0 && length + word.length() + 1 > LINE_LENGTH) {
					out.append('\n');
					length = 0;
				} else if (length > 0) {
					out.append(' ');
					length++;
				}
				out.append(word);
				length += word.length();
			}
			out.append('\n');
		}
		out.setLength(out.length() - 1);
		return out.toString();
	}
}
