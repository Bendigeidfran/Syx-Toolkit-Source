package syx.core;

import java.nio.file.Files;
import java.nio.file.Path;

/** Checks that a folder is a usable game install. */
public final class GameInstall {

	public static final String JAR_NAME = "SongsOfSyx.jar";

	private GameInstall() {
	}

	public static Path jarIn(Path gameFolder) {
		return gameFolder.resolve(JAR_NAME);
	}

	public static boolean isValid(Path gameFolder) {
		return problemWith(gameFolder) == null;
	}

	/** Returns what's wrong with the folder, or null if it's usable. */
	public static String problemWith(Path gameFolder) {
		if (gameFolder == null) {
			return "No game folder is set.";
		}
		if (!Files.isDirectory(gameFolder)) {
			return "This folder doesn't exist: " + gameFolder;
		}
		if (!Files.isRegularFile(jarIn(gameFolder))) {
			return "This folder doesn't contain " + JAR_NAME + ": " + gameFolder;
		}
		return null;
	}
}
