package syx.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Creates a new, empty mod that the launcher can load. It never overwrites anything. */
public final class ModCreator {

	public static final int MAX_NAME_LENGTH = 100;

	/** The folders the game reads inside assets (init/paths/PATHS.java). */
	private static final List<String> ASSET_FOLDERS = List.of("init", "text", "sprite", "audio");
	private static final String ILLEGAL_CHARACTERS = "<>:\"/\\|?*";
	private static final Set<String> RESERVED_NAMES = Set.of("CON", "PRN", "AUX", "NUL",
			"COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
			"LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

	/** Says why a mod couldn't be created. Nothing is left behind. */
	public static final class CreationException extends Exception {
		private static final long serialVersionUID = 1L;

		CreationException(String message) {
			super(message);
		}
	}

	private ModCreator() {
	}

	/** Returns why a name can't be used, or null if it's fine. */
	public static String problemWithName(String name) {
		if (name == null || name.isEmpty()) {
			return "Please enter a name.";
		}
		if (name.length() > MAX_NAME_LENGTH) {
			return "That name is too long. Please use " + MAX_NAME_LENGTH + " characters or fewer.";
		}
		if (name.startsWith("_")) {
			return "The name can't start with _, because the game's launcher skips those folders.";
		}
		if (name.charAt(0) == ' ' || name.endsWith(" ") || name.endsWith(".")) {
			return "The name can't start with a space, or end with a space or a full stop.";
		}
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c < 32 || c == 127) {
				return "The name can't contain control characters, such as tabs or line breaks.";
			}
			if (ILLEGAL_CHARACTERS.indexOf(c) >= 0) {
				return "The name can't contain any of these characters: " + ILLEGAL_CHARACTERS;
			}
		}
		int dot = name.indexOf('.');
		String stem = (dot < 0 ? name : name.substring(0, dot)).toUpperCase(Locale.ROOT);
		if (RESERVED_NAMES.contains(stem)) {
			return "Windows reserves the name " + stem + ", so please choose another.";
		}
		return null;
	}

	/** Creates the mod inside the mods folder, using the version of the user's game. */
	public static ModEntry create(Path modsFolder, Path gameFolder, String name) throws CreationException {
		String nameProblem = problemWithName(name);
		if (nameProblem != null) {
			throw new CreationException(nameProblem);
		}
		String installProblem = GameInstall.problemWith(gameFolder);
		if (installProblem != null) {
			throw new CreationException(installProblem);
		}

		try (GameParser parser = GameParser.open(GameInstall.jarIn(gameFolder))) {
			// Read first, so nothing is created if the game's version isn't known.
			GameParser.GameVersion version = parser.version();
			Path root = modsFolder.resolve(name);
			CreatedPaths created = new CreatedPaths();
			try {
				created.createMissingFolders(modsFolder);
				created.createFolder(root);
				Path assets = root.resolve("V" + version.major()).resolve("assets");
				created.createFolder(assets.getParent());
				created.createFolder(assets);
				for (String folder : ASSET_FOLDERS) {
					created.createFolder(assets.resolve(folder));
				}
				Path info = root.resolve("_Info.txt");
				Files.write(info, infoFile(name, version).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
				created.add(info);
				checkInfoFile(parser, info, name);
				return ModLibrary.read(root, parser, version);
			} catch (FileAlreadyExistsException e) {
				created.removeAll();
				throw new CreationException("A mod folder called " + name + " already exists. It hasn't been changed.");
			} catch (IOException | CreationException e) {
				created.removeAll();
				throw new CreationException("The mod couldn't be created: " + e.getMessage());
			}
		} catch (GameParser.UnavailableException e) {
			throw new CreationException(e.getMessage());
		}
	}

	static String infoFile(String name, GameParser.GameVersion version) {
		return "NAME: \"" + name + "\",\n"
				+ "DESC: \"\",\n"
				+ "AUTHOR: \"\",\n"
				+ "VERSION: \"1.0\",\n"
				+ "INFO: \"\",\n"
				+ "GAME_VERSION_MAJOR: " + version.major() + ",\n"
				+ "GAME_VERSION_MINOR: " + version.minor() + ",\n";
	}

	/** The game's own parser must read the file back exactly, or it isn't reported as a success. */
	private static void checkInfoFile(GameParser parser, Path info, String name) throws CreationException {
		GameParser.TextValue read = parser.text(info, "NAME", null);
		if (!read.outcome().ok() || !name.equals(read.value())) {
			throw new CreationException("The game's parser didn't read _Info.txt back correctly.");
		}
	}

}
