package syx.core.wizard;

import java.text.Normalizer;
import java.util.Locale;

/**
 * The forms a typed name takes in different places. A person types a name once, as they'd write it, and
 * never sees a file name: the game's files use {@link #stem}, which vanilla writes in capitals
 * (DONDORIAN.txt), and its words use the name as typed.
 */
public final class NameForms {

	private NameForms() {
	}

	/** The name as the person wrote it, with the space around it removed. */
	public static String display(String typed) {
		return typed == null ? "" : typed.strip().replaceAll("\\s+", " ");
	}

	/**
	 * The file name for a typed name: capitals, accents dropped, and anything other than a letter, digit or
	 * underscore turned into one underscore. Null if nothing usable is left, such as for a name of symbols.
	 */
	public static String stem(String typed) {
		String plain = Normalizer.normalize(display(typed), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		String stem = plain.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
		return stem.isEmpty() ? null : stem;
	}

	/**
	 * A first guess at the plural, only used as a starting value the person is then asked to check. English
	 * rules only: Dondorian is Dondorians, Ploy is Ploys, Fly is Flies, and a name ending in s is left alone.
	 */
	public static String pluralGuess(String typed) {
		String name = display(typed);
		if (name.isEmpty()) {
			return name;
		}
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("z") || lower.endsWith("ch") || lower.endsWith("sh")) {
			return lower.endsWith("s") ? name : name + "es";
		}
		if (lower.length() > 1 && lower.endsWith("y") && "aeiou".indexOf(lower.charAt(lower.length() - 2)) < 0) {
			return name.substring(0, name.length() - 1) + "ies";
		}
		return name + "s";
	}
}
