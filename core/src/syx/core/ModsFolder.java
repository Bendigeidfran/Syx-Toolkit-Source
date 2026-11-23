package syx.core;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * The game loads local mods from one fixed place in its user data folder (init/paths/Util.java and
 * PATHS.java), so this is worked out and never chosen by the user. It can't point at the game
 * install or the Workshop folder.
 */
public final class ModsFolder {

	private ModsFolder() {
	}

	public static Path standard() {
		String os = System.getProperty("os.name").toUpperCase(Locale.ROOT);
		String home = System.getProperty("user.home");
		Path userData;
		if (os.contains("MAC") || os.contains("DARWIN")) {
			userData = Paths.get(home, "Library", "Application Support", "songsofsyx");
		} else if (os.contains("WIN")) {
			String appData = System.getenv("AppData");
			userData = appData != null && !appData.isBlank()
					? Paths.get(appData, "songsofsyx")
					: Paths.get(home + File.separator + "AppData" + File.separator + "Roaming", "songsofsyx");
		} else {
			userData = Paths.get(home, ".local", "share", "songsofsyx");
		}
		return userData.resolve("mods");
	}
}
