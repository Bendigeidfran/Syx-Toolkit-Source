package syx.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import syx.core.HandbookIndex.Section;
import syx.core.HandbookIndex.TagHelp;

/**
 * Compares the handbook with what the jar scan found. Neither side is changed to match: a disagreement
 * is a finding for a person to settle against the game's code.
 */
public final class HandbookCheck {

	/** How serious a difference is. Something missing from the handbook is only a gap. */
	public enum Kind {
		DISAGREES, NOT_DOCUMENTED
	}

	public record Finding(String tag, Kind kind, String message) {
	}

	private HandbookCheck() {
	}

	public static List<Finding> compare(AssetSchema schema, String location, HandbookIndex index) {
		return compare(schema, List.of(location), index);
	}

	/**
	 * Compares with entries merged across locations, the first winning. Only the first is checked for
	 * entries the scan doesn't know, because later ones can describe tags for many types.
	 */
	public static List<Finding> compare(AssetSchema schema, List<String> locations, HandbookIndex index) {
		List<Finding> findings = new ArrayList<>();
		List<String> dataKeys = new ArrayList<>();
		for (TagSpec tag : schema.dataTags()) {
			compareTree(tag, schema, locations, Section.TAGS, index, dataKeys, findings);
		}
		List<String> textKeys = new ArrayList<>();
		for (TagSpec tag : schema.textTags()) {
			compareTree(tag, schema, locations, Section.TEXT_FILE, index, textKeys, findings);
			for (TagSpec.SubKey sub : tag.subKeys()) {
				textKeys.add(tag.key() + "." + sub.key());
			}
		}
		unknownEntries(index, locations.get(0), Section.TAGS, dataKeys, findings);
		unknownEntries(index, locations.get(0), Section.TEXT_FILE, textKeys, findings);
		return findings;
	}

	private static void compareTree(TagSpec tag, AssetSchema schema, List<String> locations, Section section, HandbookIndex index, List<String> keys, List<Finding> findings) {
		keys.add(tag.key());
		HandbookIndex.TagHelp help = null;
		for (String location : locations) {
			help = help != null ? help : index.tag(location, section, tag.key());
		}
		compareTag(tag, help, findings);
		for (TagSpec.Column column : tag.shape().columns()) {
			if (column.key() != null) {
				keys.add(tag.key() + "." + column.key());
			}
		}
		for (TagSpec child : tag.nested()) {
			compareTree(child, schema, locations, section, index, keys, findings);
		}
	}

	private static void unknownEntries(HandbookIndex index, String location, Section section, List<String> known, List<Finding> findings) {
		for (String key : index.headings(location, section)) {
			// A tag the handbook marks as unread is meant to be missing from the scan, so both sides agree.
			if (!known.contains(key) && index.tag(location, section, key).effect() != HandbookIndex.Effect.UNREAD) {
				findings.add(new Finding(key, Kind.DISAGREES, "The handbook has an entry for this, but the game's loader doesn't appear to read it."));
			}
		}
	}

	private static void compareTag(TagSpec tag, TagHelp help, List<Finding> findings) {
		if (help == null) {
			findings.add(new Finding(tag.key(), Kind.NOT_DOCUMENTED, "The handbook has no entry for this tag yet."));
			return;
		}
		// Null means the handbook doesn't say, which is never a disagreement.
		if (help.required() == null) {
			findings.add(new Finding(tag.key(), Kind.NOT_DOCUMENTED, "The handbook doesn't say whether this tag is required."));
		} else if (help.required() != tag.required()) {
			findings.add(new Finding(tag.key(), Kind.DISAGREES, "The handbook says required is " + help.required() + ", and the game's code says " + tag.required() + "."));
		}
		if (tag.kind() == TagSpec.Kind.TEXT_ROWS && tag.shape().maxEntries() > 0 && help.range() != null && !mentions(help.range(), tag.shape().maxEntries())) {
			findings.add(new Finding(tag.key(), Kind.DISAGREES, "The game's code allows " + tag.shape().maxEntries() + " rows, which the handbook's range doesn't mention: " + help.range()));
		}
		if (tag.min() == null && tag.max() == null) {
			return;
		}
		if (help.range() == null) {
			findings.add(new Finding(tag.key(), Kind.NOT_DOCUMENTED, "The handbook doesn't state a range for this tag."));
			return;
		}
		for (Double limit : new Double[] { tag.min(), tag.max() }) {
			if (limit != null && !mentions(help.range(), limit) && !(tag.kind() == TagSpec.Kind.INT && limit.equals(tag.max()) && saysBelow(help.range(), limit))) {
				findings.add(new Finding(tag.key(), Kind.DISAGREES, "The game's code has a limit of " + text(limit) + ", which the handbook's range doesn't mention: " + help.range()));
			}
		}
	}

	private static boolean mentions(String range, double limit) {
		return containsNumber(range, text(limit)) || containsNumber(range, java.math.BigDecimal.valueOf(limit).stripTrailingZeros().toPlainString());
	}

	private static boolean containsNumber(String range, String number) {
		return Pattern.compile("(?<![\\d.])" + Pattern.quote(number) + "(?!\\d|\\.\\d)").matcher(range).find();
	}

	/** For whole numbers, "below 10" states the same limit as a maximum of 9. */
	private static boolean saysBelow(String range, double limit) {
		return Pattern.compile("\\bbelow " + text(limit + 1) + "(?!\\d|\\.\\d)", Pattern.CASE_INSENSITIVE).matcher(range).find();
	}

	private static String text(double value) {
		return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
	}
}
