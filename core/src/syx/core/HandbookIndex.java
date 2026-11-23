package syx.core;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the tag data the handbook publishes for this toolkit. It's found by the game file's path, such
 * as assets/init/animal/, and page addresses aren't relied on. The handbook only supplies words: a
 * tag's rules still come from the game's code.
 */
public final class HandbookIndex {

	public static final String SITE = "https://bendigeidfran.github.io/Syx-Modding-Handbook/";
	public static final String TAGS_URL = SITE + "toolkit/tags.json";
	private static final String SHARED_WIKI_LOCATION = "assets/init/animal/";
	private static final String SHARED_SPRITE_LOCATION = "assets/sprite/game/";
	private static final String SHARED_ROOM_LOCATION = "assets/init/room/";
	private static final String RACE_SPRITE_LOCATION = "assets/init/race/sprite/";
	private static final String RACE_HOME_LOCATION = "assets/init/race/home/";
	private static final Pattern HOME_CLASS = Pattern.compile("(?:CITIZEN|NOBLE|SLAVE)\\.(.+)");
	private static final Pattern INDUSTRY_ICON = Pattern.compile("(?:.+\\.)?INDUSTRY\\.(ICON\\.[^.]+)");
	/** The version of the file's format that this reader understands. */
	public static final int SCHEMA = 1;

	/** The parts of an entry that describe a file's tags. */
	public enum Section {
		TAGS("tags"), TEXT_FILE("text_tags");

		private final String field;

		Section(String field) {
			this.field = field;
		}
	}

	/** What the handbook says about a tag that isn't active. No value doesn't prove a tag is active. */
	public enum Effect {
		NONE, UNREAD
	}

	/**
	 * What the handbook says about one tag. Null means it doesn't say.
	 *
	 * @param summary a short form of the description, which falls back on its first paragraph. Never null.
	 * @param description plain text, with code in backticks and a blank line between paragraphs. Never null.
	 * @param example the tag as written in a file. Descriptions may refer to it as "below".
	 * @param url the tag's page, which is only ever on the handbook's own site
	 * @param unverified the handbook marks this entry as not confirmed yet
	 * @param required what its Required value says, if that's a plain yes or no
	 * @param range the handbook's words about the tag's range
	 */
	public record TagHelp(String summary, String description, String example, String url, Effect effect, boolean unverified, Boolean required, String range) {
	}

	/** The file isn't in the expected form, so none of it is used. */
	public static class ParseException extends Exception {
		private static final long serialVersionUID = 1L;

		ParseException(String message) {
			super(message);
		}
	}

	/** The file is in a newer form than this toolkit can read. */
	public static final class UnsupportedSchemaException extends ParseException {
		private static final long serialVersionUID = 1L;

		UnsupportedSchemaException(String message) {
			super(message);
		}
	}

	private static final Pattern VERSION = Pattern.compile("v?(?:\\d+\\.)?(\\d+)\\.(\\d+)");
	/**
	 * "Yes, if the block is present", "Yes, for bathhouses" and "No, except for canteens" depend on something else, so
	 * they aren't read as a plain answer. "Yes, for every cause below" is one.
	 */
	private static final Pattern REQUIRED = Pattern.compile("^(Yes|No)\\b(?!,? (?:if|unless|when|except)\\b|,? for (?!every\\b|all\\b))");

	private final Map<String, Map<Section, Map<String, TagHelp>>> locations;
	private final GameParser.GameVersion writtenFor;
	private final String contentHash;

	private HandbookIndex(Map<String, Map<Section, Map<String, TagHelp>>> locations, GameParser.GameVersion writtenFor, String contentHash) {
		this.locations = locations;
		this.writtenFor = writtenFor;
		this.contentHash = contentHash;
	}

	/** Reads the whole file. Anything malformed is a failure, never a partial result. */
	public static HandbookIndex parse(String json) throws ParseException {
		Object root;
		try {
			root = JsonText.parse(json);
		} catch (RuntimeException | StackOverflowError e) {
			throw new ParseException("The handbook's tag data couldn't be read: " + e.getMessage());
		}
		if (!(root instanceof Map<?, ?> map)) {
			throw new ParseException("The handbook's tag data isn't in the expected form.");
		}
		// The format's version comes first, because a newer format may differ in any other way.
		if (!(map.get("schema") instanceof Double schema)) {
			throw new ParseException("The handbook's tag data doesn't say which format it uses.");
		}
		if (schema != SCHEMA) {
			throw new UnsupportedSchemaException("The handbook's tag data uses format " + schema + ", and this toolkit reads format " + SCHEMA + ".");
		}
		if (!(map.get("content_hash") instanceof String hash) || hash.isBlank()) {
			throw new ParseException("The handbook's tag data has no content hash.");
		}
		if (!(map.get("locations") instanceof Map<?, ?> entries)) {
			throw new ParseException("The handbook's tag data has no locations.");
		}
		Map<String, Map<Section, Map<String, TagHelp>>> locations = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : entries.entrySet()) {
			if (!(entry.getValue() instanceof Map<?, ?> location)) {
				throw new ParseException("The handbook's entry for " + entry.getKey() + " isn't in the expected form.");
			}
			Map<Section, Map<String, TagHelp>> sections = new LinkedHashMap<>();
			for (Section section : Section.values()) {
				sections.put(section, readTags(location.get(section.field), section));
			}
			locations.put((String) entry.getKey(), sections);
		}
		return new HandbookIndex(locations, readVersion(map.get("game_version")), hash);
	}

	private static Map<String, TagHelp> readTags(Object value, Section section) throws ParseException {
		Map<String, TagHelp> tags = new LinkedHashMap<>();
		if (value == null) {
			return tags;
		}
		if (!(value instanceof Map<?, ?> entries)) {
			throw new ParseException("The handbook's " + section.field + " aren't in the expected form.");
		}
		for (Map.Entry<?, ?> entry : entries.entrySet()) {
			if (!(entry.getValue() instanceof Map<?, ?> tag)) {
				throw new ParseException("The handbook's entry for " + entry.getKey() + " isn't in the expected form.");
			}
			tags.put((String) entry.getKey(), readTag(tag));
		}
		return tags;
	}

	private static TagHelp readTag(Map<?, ?> tag) throws ParseException {
		String description = text(tag, "description");
		Object unverified = tag.get("unverified");
		if (unverified != null && !(unverified instanceof Boolean)) {
			throw new ParseException("The handbook has an unverified value in an unknown form.");
		}
		String body = description == null ? "" : description;
		String summary = text(tag, "summary");
		int paragraphEnd = body.indexOf("\n\n");
		return new TagHelp(summary != null ? summary : paragraphEnd < 0 ? body : body.substring(0, paragraphEnd), body, text(tag, "example"), safeUrl(text(tag, "url")),
				effect(text(tag, "effect")), Boolean.TRUE.equals(unverified), required(text(tag, "required")), text(tag, "range"));
	}

	/** A value that's absent, null or blank means the handbook doesn't say. */
	private static String text(Map<?, ?> tag, String field) throws ParseException {
		Object value = tag.get(field);
		if (value != null && !(value instanceof String)) {
			throw new ParseException("The handbook has a " + field + " value in an unknown form.");
		}
		return value == null || ((String) value).isBlank() ? null : (String) value;
	}

	/** A value this toolkit doesn't know makes no claim, because no value never proves a tag is active. */
	private static Effect effect(String value) {
		if ("None".equals(value)) {
			return Effect.NONE;
		}
		return "Unread".equals(value) ? Effect.UNREAD : null;
	}

	private static Boolean required(String value) {
		if (value == null) {
			return null;
		}
		Matcher matcher = REQUIRED.matcher(value);
		return matcher.find() ? Boolean.valueOf(matcher.group(1).equals("Yes")) : null;
	}

	/** Links come from a downloaded file and open in the browser, so only the handbook's own pages are kept. */
	private static String safeUrl(String url) {
		if (url == null) {
			return null;
		}
		try {
			return URI.create(url).normalize().toString().startsWith(SITE) ? url : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static GameParser.GameVersion readVersion(Object value) {
		if (!(value instanceof String text)) {
			return null;
		}
		Matcher matcher = VERSION.matcher(text.trim());
		if (!matcher.matches()) {
			return null;
		}
		try {
			return new GameParser.GameVersion(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** True if the file describes at least one game file. */
	public boolean hasLocations() {
		return !locations.isEmpty();
	}

	/** The game version the handbook says it's written for, or null if it can't be read. */
	public GameParser.GameVersion writtenFor() {
		return writtenFor;
	}

	/** Changes only when the content changes, unlike the site's ETag, which can change on every publish. */
	public String contentHash() {
		return contentHash;
	}

	/** What the handbook says about a tag, or null if it has no entry for it. */
	public TagHelp tag(String location, Section section, String key) {
		return tags(location, section).get(key);
	}

	/**
	 * What the handbook says about a tag, for showing to a modder. A WIKI block's inner tags are the same
	 * in every file (INFO.java:24), so the handbook describes them once, and those fall back on that entry.
	 * This only supplies words, so it isn't used when comparing the handbook with the game's code.
	 */
	public TagHelp description(String location, Section section, String key) {
		TagHelp own = tag(location, section, key);
		if (own != null || section != Section.TEXT_FILE || !key.startsWith("WIKI.")) {
			return own;
		}
		return tag(SHARED_WIKI_LOCATION, section, key);
	}

	/** The same, across several locations, with the first that has an entry winning. */
	public TagHelp description(List<String> locations, Section section, String key) {
		for (String location : locations) {
			TagHelp own = tag(location, section, key);
			if (own != null) {
				return own;
			}
		}
		if (section == Section.TAGS) {
			// Every room's sprite entries hold the same tags, which the handbook describes once.
			if (key.matches("SPRITES\\.[^.]+\\.[^.]+")) {
				return tag(SHARED_SPRITE_LOCATION, section, key.substring(key.lastIndexOf('.') + 1));
			}
			// A child is read as one more type (RAppearence.java:92,135). What differs has an exact entry, found above.
			if (key.startsWith("CHILD.") && locations.contains(RACE_SPRITE_LOCATION)) {
				return description(locations, section, "TYPES." + key.substring("CHILD.".length()));
			}
			Matcher home = HOME_CLASS.matcher(key);
			if (home.matches() && locations.contains(RACE_HOME_LOCATION)) {
				return homeTag(home.group(1));
			}
			// A recipe's icon is the same block as a room's (Industry.java:141-142).
			Matcher icon = INDUSTRY_ICON.matcher(key);
			if (icon.matches() && locations.contains(SHARED_ROOM_LOCATION)) {
				return tag(SHARED_ROOM_LOCATION, section, icon.group(1));
			}
		}
		return description(locations.get(0), section, key);
	}

	/**
	 * A home's classes hold the same tags (RaceHome.java:24-26), which the handbook describes once. Its
	 * furniture is read as sprite sheets (RaceHomeSheet.java:38, GameSheets.java:114), with the resources it needs beside them.
	 */
	private TagHelp homeTag(String key) {
		TagHelp own = tag(RACE_HOME_LOCATION, Section.TAGS, key);
		int dot = key.indexOf('.');
		if (own != null || dot < 0) {
			return own;
		}
		String inner = key.substring(dot + 1);
		if (inner.equals("RESOURCES")) {
			return tag(RACE_HOME_LOCATION, Section.TAGS, inner);
		}
		return tag(SHARED_SPRITE_LOCATION, Section.TAGS, inner.equals("OVERWRITE.COLOR") ? "OVERWRITE" : inner);
	}

	/** Every location with an entry. */
	public List<String> locations() {
		return new ArrayList<>(locations.keySet());
	}

	/** Every tag with an entry in a section. */
	public List<String> headings(String location, Section section) {
		return new ArrayList<>(tags(location, section).keySet());
	}

	private Map<String, TagHelp> tags(String location, Section section) {
		Map<Section, Map<String, TagHelp>> sections = locations.get(location);
		return sections == null ? Map.of() : sections.get(section);
	}

	/** Just enough JSON for this file, because java.base has no reader. It never accepts bad input. */
}
