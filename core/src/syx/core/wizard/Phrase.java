package syx.core.wizard;

import java.util.Map;

/**
 * Fills a prompt with the asset's own words: {@code {name}} from NAME, {@code {names}} from NAMES,
 * {@code {possessive}} from POSSESSIVE and {@code {possessives}} from POSSESSIVES. Before the files exist,
 * or while one of these is blank, the name as typed stands in.
 */
public final class Phrase {

	private Phrase() {
	}

	public static String fill(String template, Map<String, String> values, String typedName) {
		if (template == null) {
			return null;
		}
		String name = word(values, "NAME", typedName);
		String names = word(values, "NAMES", NameForms.pluralGuess(name));
		String possessive = word(values, "POSSESSIVE", name);
		String possessives = word(values, "POSSESSIVES", possessive);
		return template.replace("{names}", names).replace("{name}", name).replace("{possessives}", possessives).replace("{possessive}", possessive);
	}

	private static String word(Map<String, String> values, String key, String fallback) {
		String value = values == null ? null : values.get(key);
		return value == null || value.isBlank() ? fallback : value.strip();
	}
}
