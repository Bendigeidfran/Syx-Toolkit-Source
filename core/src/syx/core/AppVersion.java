package syx.core;

/**
 * The toolkit's own version, the one version every front end shows: the app beside its name, agents as
 * {@code toolkit_version}, the MCP server in its handshake, and the release zip's name. Major and minor stay
 * separate, as the game's do, and the phase is a word such as Alpha, Beta or Release.
 *
 * <p>Every build after a change raises the minor version. The major version only goes up when the user says so.
 */
public final class AppVersion {

	public static final String PHASE = "Alpha";
	public static final int MAJOR = 3;
	public static final int MINOR = 0;

	private AppVersion() {
	}

	/** The number alone, such as 3.0. */
	public static String number() {
		return MAJOR + "." + MINOR;
	}

	/** The text beside the app name, such as Alpha v3.0. */
	public static String label() {
		return PHASE + " v" + number();
	}
}
