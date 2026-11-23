package syx.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Lists the mods in the mods folder, following the launcher's rules. Read-only. */
public final class ModLibrary {

	private static final String UNSET = "???";
	private static final Pattern VERSION_FOLDER = Pattern.compile("V\\d+");

	/** The mods found, or the reason they couldn't be listed. */
	public record Listing(List<ModEntry> mods, String problem) {
	}

	private ModLibrary() {
	}

	public static Listing list(Path modsFolder, Path gameFolder) {
		if (!Files.isDirectory(modsFolder)) {
			return new Listing(List.of(), null);
		}
		List<Path> folders;
		try (Stream<Path> children = Files.list(modsFolder)) {
			// The launcher skips folders starting with _ (init/paths/VirtualFolder.java).
			folders = children.filter(Files::isDirectory)
					.filter(p -> !p.getFileName().toString().startsWith("_"))
					.toList();
		} catch (IOException e) {
			return new Listing(List.of(), "Couldn't read the mods folder: " + e.getMessage());
		}
		try (GameParser parser = GameParser.open(GameInstall.jarIn(gameFolder))) {
			GameParser.GameVersion version = parser.version();
			List<ModEntry> mods = new ArrayList<>();
			for (Path folder : folders) {
				mods.add(entry(folder, gameFolder, parser, version));
			}
			mods.sort(Comparator.comparing(ModEntry::displayName, String.CASE_INSENSITIVE_ORDER));
			return new Listing(mods, null);
		} catch (GameParser.UnavailableException e) {
			return new Listing(List.of(), e.getMessage());
		}
	}

	/** Reads one mod by its saved folder name, or returns nothing if it's no longer there. */
	public static Optional<ModEntry> find(Path modsFolder, Path gameFolder, String folderName) {
		Path folder = modsFolder.resolve(folderName).normalize();
		if (!modsFolder.normalize().equals(folder.getParent()) || !Files.isDirectory(folder)) {
			return Optional.empty();
		}
		try (GameParser parser = GameParser.open(GameInstall.jarIn(gameFolder))) {
			return Optional.of(entry(folder, gameFolder, parser, parser.version()));
		} catch (GameParser.UnavailableException e) {
			return Optional.of(new ModEntry(folderName, folder, folderName, List.of(), false, e.getMessage()));
		}
	}

	/**
	 * A mod folder in the mods folder. The game follows a link there, such as a junction to a project
	 * folder (VirtualFolder.listFolders and Util.listFiles use Files.list and Files.isDirectory, which follow
	 * links), so the toolkit does too: the mod's root is the link's real target, and the write guards keep
	 * every write inside it. A link into the game install or the Workshop folder is refused, because the
	 * toolkit never writes there.
	 */
	private static ModEntry entry(Path folder, Path gameFolder, GameParser parser, GameParser.GameVersion version) {
		String folderName = folder.getFileName().toString();
		Path real;
		boolean linked;
		try {
			real = folder.toRealPath();
			linked = !real.equals(folder.getParent().toRealPath().resolve(folderName));
		} catch (IOException e) {
			return new ModEntry(folderName, folder, folderName, List.of(), false, "It couldn't be read: " + e.getMessage());
		}
		if (!linked) {
			return read(folderName, folder, parser, version);
		}
		String into = protectedPlace(real, gameFolder);
		ModEntry mod = read(folderName, real, parser, version);
		if (into == null) {
			return mod;
		}
		String refusal = "It's a link into " + into + " (" + real + "), and the toolkit never writes there, so it can't be opened.";
		return new ModEntry(folderName, real, mod.displayName(), mod.versionFolders(), mod.outdated(), mod.problem(), refusal);
	}

	/** Where a real path is, if it's somewhere the toolkit never writes, or null. */
	static String protectedPlace(Path real, Path gameFolder) {
		try {
			if (gameFolder != null && Files.exists(gameFolder) && real.startsWith(gameFolder.toRealPath())) {
				return "the game's install folder";
			}
		} catch (IOException e) {
			// The game folder can't be resolved, so it can't be compared. The Workshop check still applies.
		}
		// Steam keeps Workshop mods in <library>/steamapps/workshop (PATHS.java:513-535 finds them the same way).
		String previous = "";
		for (Path part : real) {
			String name = part.toString();
			if (previous.equalsIgnoreCase("steamapps") && name.equalsIgnoreCase("workshop")) {
				return "the Steam Workshop folder";
			}
			previous = name;
		}
		return null;
	}

	static ModEntry read(Path folder, GameParser parser, GameParser.GameVersion version) {
		return read(folder.getFileName().toString(), folder, parser, version);
	}

	private static ModEntry read(String folderName, Path folder, GameParser parser, GameParser.GameVersion version) {
		List<String> versions = versionFolders(folder);
		String displayName = folderName;
		String problem = null;

		Path info = folder.resolve("_Info.txt");
		if (!Files.isRegularFile(info)) {
			problem = "It has no _Info.txt, so the game can't load it";
		} else {
			GameParser.TextValue name = parser.text(info, "NAME", UNSET);
			if (name.outcome().ok()) {
				if (!UNSET.equals(name.value()) && !name.value().isBlank()) {
					displayName = name.value();
				}
			} else if (name.outcome().dataError() != null) {
				problem = "Its _Info.txt can't be read. " + SyntaxChecker.translate(name.outcome().dataError()).message();
			} else {
				problem = "Its _Info.txt couldn't be checked: " + name.outcome().otherFailure();
			}
		}
		if (problem == null && versions.isEmpty()) {
			problem = "It has no version folder, so the game can't load it";
		}
		boolean outdated = !versions.isEmpty() && !versions.contains("V" + version.major());
		return new ModEntry(folderName, folder, displayName, versions, outdated, problem);
	}

	private static List<String> versionFolders(Path mod) {
		try (Stream<Path> children = Files.list(mod)) {
			return children.filter(Files::isDirectory)
					.map(p -> p.getFileName().toString())
					.filter(name -> VERSION_FOLDER.matcher(name).matches())
					.sorted()
					.toList();
		} catch (IOException e) {
			return List.of();
		}
	}
}
