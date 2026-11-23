package syx.gui;

/**
 * The toolkit's own version, shown beside its name. Major and minor stay separate, as the game's do,
 * and the phase is a word such as Alpha, Beta or Release.
 */
final class AppVersion {

	static final String PHASE = "Alpha";
	static final int MAJOR = 3;
	static final int MINOR = 0;

	private AppVersion() {
	}

	/** The text beside the app name, such as Alpha v3.0. */
	static String label() {
		return PHASE + " v" + MAJOR + "." + MINOR;
	}
}
