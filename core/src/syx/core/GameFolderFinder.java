package syx.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Looks for the game in its likely locations. Read-only. */
public final class GameFolderFinder {

	private static final String GAME_FOLDER_NAME = "Songs of Syx";
	private static final Path WINDOWS_DEFAULT_STEAM = Paths.get("C:\\Program Files (x86)\\Steam");
	private static final Pattern LIBRARY_PATH = Pattern.compile("\"path\"\\s+\"((?:[^\"\\\\]|\\\\.)*)\"");
	private static final Pattern REGISTRY_STEAM_PATH = Pattern.compile("SteamPath\\s+REG_SZ\\s+(.+)");

	public record Result(Optional<Path> found, List<Path> checked) {
	}

	private GameFolderFinder() {
	}

	public static Result find() {
		String os = System.getProperty("os.name").toUpperCase(Locale.ROOT);
		if (os.contains("WIN")) {
			List<Path> drives = new ArrayList<>();
			for (File root : File.listRoots()) {
				drives.add(root.toPath());
			}
			return search(windowsCandidates(WINDOWS_DEFAULT_STEAM, registrySteamRoot(), drives));
		}
		return search(otherSystemCandidates(os));
	}

	/** Returns the first candidate that's a usable game folder, and every place that was looked at. */
	public static Result search(List<Path> candidates) {
		List<Path> checked = new ArrayList<>();
		for (Path candidate : candidates) {
			checked.add(candidate);
			if (GameInstall.isValid(candidate)) {
				return new Result(Optional.of(candidate), checked);
			}
		}
		return new Result(Optional.empty(), checked);
	}

	/** The search order: the default Steam folder, Steam's own records, then common folders on each drive. */
	public static List<Path> windowsCandidates(Path defaultSteam, Optional<Path> registrySteam, List<Path> drives) {
		Set<Path> candidates = new LinkedHashSet<>();
		List<Path> steamRoots = new ArrayList<>();
		steamRoots.add(defaultSteam);
		registrySteam.ifPresent(steamRoots::add);

		for (Path steam : steamRoots) {
			candidates.add(inLibrary(steam));
		}
		for (Path steam : steamRoots) {
			for (Path library : librariesOf(steam)) {
				candidates.add(inLibrary(library));
			}
		}
		for (Path drive : drives) {
			candidates.add(inLibrary(drive.resolve("SteamLibrary")));
			candidates.add(inLibrary(drive.resolve("Steam")));
			candidates.add(inLibrary(drive.resolve("Program Files (x86)").resolve("Steam")));
		}
		for (Path drive : drives) {
			candidates.add(drive.resolve("Games").resolve(GAME_FOLDER_NAME));
			candidates.add(drive.resolve("GOG Games").resolve(GAME_FOLDER_NAME));
		}
		return new ArrayList<>(candidates);
	}

	/** Reads the library folders from the text of Steam's libraryfolders.vdf. */
	public static List<Path> parseLibraryFolders(String vdf) {
		List<Path> libraries = new ArrayList<>();
		Matcher matcher = LIBRARY_PATH.matcher(vdf);
		while (matcher.find()) {
			libraries.add(Paths.get(matcher.group(1).replace("\\\\", "\\")));
		}
		return libraries;
	}

	private static List<Path> librariesOf(Path steam) {
		try {
			return parseLibraryFolders(Files.readString(steam.resolve("steamapps").resolve("libraryfolders.vdf"), StandardCharsets.UTF_8));
		} catch (IOException | RuntimeException e) {
			// No usable record here. The other locations are still tried.
			return List.of();
		}
	}

	private static Path inLibrary(Path library) {
		return library.resolve("steamapps").resolve("common").resolve(GAME_FOLDER_NAME);
	}

	private static Optional<Path> registrySteamRoot() {
		String systemRoot = System.getenv("SystemRoot");
		String reg = systemRoot == null ? "reg" : systemRoot + "\\System32\\reg.exe";
		try {
			Process process = new ProcessBuilder(reg, "query", "HKCU\\Software\\Valve\\Steam", "/v", "SteamPath")
					.redirectErrorStream(true).start();
			if (!process.waitFor(3, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return Optional.empty();
			}
			String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			Matcher matcher = REGISTRY_STEAM_PATH.matcher(output);
			return matcher.find() ? Optional.of(Paths.get(matcher.group(1).trim())) : Optional.empty();
		} catch (IOException | RuntimeException e) {
			return Optional.empty();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Optional.empty();
		}
	}

	/** From the mod template's pom.xml. Untested: only Windows has been run so far. */
	private static List<Path> otherSystemCandidates(String os) {
		String home = System.getProperty("user.home");
		List<Path> candidates = new ArrayList<>();
		if (os.contains("MAC") || os.contains("DARWIN")) {
			candidates.add(inLibrary(Paths.get(home, "Library", "Application Support", "Steam"))
					.resolve("SongsOfSyxMac.app").resolve("Contents").resolve("Resources"));
		} else {
			candidates.add(inLibrary(Paths.get(home, ".steam", "steam")));
			candidates.add(inLibrary(Paths.get(home, ".local", "share", "Steam")));
		}
		return candidates;
	}
}
