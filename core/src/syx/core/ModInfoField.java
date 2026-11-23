package syx.core;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The fields of _Info.txt, with the game's rules (init/paths/ModInfo.java and
 * snake2d/util/file/JsonValue.java).
 */
public enum ModInfoField {

	NAME(Kind.LINE, "The name shown in the launcher's mod list."),
	DESC(Kind.LINES, "Shown when a player hovers over the mod."),
	AUTHOR(Kind.LINE, "Who to credit."),
	VERSION(Kind.LINE, "Your mod's own version. Any scheme is fine."),
	INFO(Kind.LINES, "Anything extra, such as a link to the mod's page."),
	TEXTURE_CACHE_SIZE(Kind.CHOICE, "Only needed for mods with a lot of sprites. The game's default is 4096."),
	GAME_VERSION_MAJOR(Kind.NUMBER, "A record only: the game doesn't read this. The version folder decides."),
	GAME_VERSION_MINOR(Kind.NUMBER, "A record only: the game doesn't read this.");

	public enum Kind {
		LINE, LINES, CHOICE, NUMBER;

		public boolean isText() {
			return this == LINE || this == LINES;
		}
	}

	public static final List<String> TEXTURE_CACHE_SIZES = List.of("4096", "8192", "16384");
	private static final Pattern NUMBER = Pattern.compile("\\d{1,5}");

	private final Kind kind;
	private final String help;

	ModInfoField(Kind kind, String help) {
		this.kind = kind;
		this.help = help;
	}

	public Kind kind() {
		return kind;
	}

	public String help() {
		return help;
	}

	/**
	 * Whether the field can be removed. The game has a fallback for every field (ModInfo.java), so
	 * this is our own rule: without a NAME, the launcher lists the mod as "???".
	 */
	public boolean removable() {
		return this != NAME;
	}

	/**
	 * What the game uses when the field is absent (ModInfo.java:29-35), or null if the game doesn't
	 * read the field, so it has no default. NAME is never absent here, but follows the same rule.
	 */
	public String gameDefault() {
		return switch (this) {
		case NAME, DESC, AUTHOR, VERSION, INFO -> "???";
		case TEXTURE_CACHE_SIZE -> TEXTURE_CACHE_SIZES.get(0);
		case GAME_VERSION_MAJOR, GAME_VERSION_MINOR -> null;
		};
	}

	public static ModInfoField find(String key) {
		for (ModInfoField field : values()) {
			if (field.name().equals(key)) {
				return field;
			}
		}
		return null;
	}

	/** The value a new field starts with. */
	public String defaultValue(String modFolderName, GameParser.GameVersion version) {
		return switch (this) {
		case NAME -> modFolderName;
		case VERSION -> "1.0";
		case TEXTURE_CACHE_SIZE -> TEXTURE_CACHE_SIZES.get(0);
		case GAME_VERSION_MAJOR -> String.valueOf(version.major());
		case GAME_VERSION_MINOR -> String.valueOf(version.minor());
		default -> "";
		};
	}

	/** Returns why this value can't be saved, or null if it's fine. */
	public String problemWith(String value) {
		return switch (kind) {
		case LINE, LINES -> problemWithText(value);
		case CHOICE -> TEXTURE_CACHE_SIZES.contains(value) ? null : "This must be 4096, 8192 or 16384, or the game won't load the mod.";
		case NUMBER -> problemWithNumber(value);
		};
	}

	private String problemWithText(String value) {
		// The game's own rule is only that it's text. A blank name would just be unusable.
		if (this == NAME && value.isBlank()) {
			return "The mod needs a name.";
		}
		return TextRules.problemWith(value, kind == Kind.LINE);
	}

	private static String problemWithNumber(String value) {
		if (!NUMBER.matcher(value).matches() || Integer.parseInt(value) > 65535) {
			return "This must be a whole number from 0 to 65535.";
		}
		return null;
	}
}
