package syx.core;

/** What quoted text can hold and still be read back unchanged (snake2d/util/file/JsonValue.java). */
final class TextRules {

	private TextRules() {
	}

	/** Returns why this text can't be written, or null if it's fine. */
	static String problemWith(String value, boolean singleLine) {
		if (value.contains("\",")) {
			return "The text can't contain a double quote followed by a comma, because the game would end the text there.";
		}
		if (value.contains("\t")) {
			return "The text can't contain tabs, because the game removes them.";
		}
		if (value.contains("%r%")) {
			return "The text can't contain %r%, because the game turns it into a line break.";
		}
		if (singleLine && value.indexOf('\n') >= 0) {
			return "This must fit on one line.";
		}
		if (value.contains("\n\n")) {
			return "The text can't contain blank lines, because the game removes them.";
		}
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if ((c < 32 && c != '\n') || c == 127) {
				return "The text can't contain control characters.";
			}
		}
		return null;
	}
}
