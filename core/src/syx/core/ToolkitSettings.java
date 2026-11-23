package syx.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Properties;

/** The toolkit's own settings. Stored apart from the game install and the game's user data folder. */
public final class ToolkitSettings {

	/** Overrides the settings file, so tests never touch real settings. */
	public static final String FILE_OVERRIDE_PROPERTY = "syx.settings";

	private static final String GAME_FOLDER = "gameFolder";
	private static final String CURRENT_MOD = "currentMod";
	private static final String THEME = "theme";

	/** How the app looks. Dark is the default. */
	public enum ThemeChoice {
		DARK, LIGHT
	}

	private final Path file;
	private final Properties values = new Properties();

	public ToolkitSettings(Path file) {
		this.file = file;
		if (Files.isRegularFile(file)) {
			try (InputStream in = Files.newInputStream(file)) {
				values.load(in);
			} catch (IOException | IllegalArgumentException e) {
				// Unreadable settings are treated as no settings, so the user is simply asked again.
				values.clear();
			}
		}
	}

	public static ToolkitSettings openDefault() {
		return new ToolkitSettings(defaultFile());
	}

	public static Path defaultFile() {
		String override = System.getProperty(FILE_OVERRIDE_PROPERTY);
		if (override != null && !override.isBlank()) {
			return Paths.get(override);
		}
		String appData = System.getenv("APPDATA");
		Path base = appData != null && !appData.isBlank()
				? Paths.get(appData)
				: Paths.get(System.getProperty("user.home"), ".config");
		return base.resolve("SyxToolkit").resolve("settings.properties");
	}

	/** The folder these settings are in, which the toolkit's other saved values sit beside. */
	public Path folder() {
		return file.toAbsolutePath().getParent();
	}

	public Optional<Path> gameFolder() {
		String value = values.getProperty(GAME_FOLDER);
		return value == null || value.isBlank() ? Optional.empty() : Optional.of(Paths.get(value));
	}

	public void setGameFolder(Path folder) throws IOException {
		values.setProperty(GAME_FOLDER, folder.toAbsolutePath().toString());
		save();
	}

	/** The folder name of the selected mod, inside the mods folder. */
	public Optional<String> currentMod() {
		String value = values.getProperty(CURRENT_MOD);
		return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
	}

	public void setCurrentMod(String folderName) throws IOException {
		values.setProperty(CURRENT_MOD, folderName);
		save();
	}

	public void clearCurrentMod() throws IOException {
		values.remove(CURRENT_MOD);
		save();
	}

	public ThemeChoice theme() {
		return "light".equals(values.getProperty(THEME)) ? ThemeChoice.LIGHT : ThemeChoice.DARK;
	}

	public void setTheme(ThemeChoice theme) throws IOException {
		values.setProperty(THEME, theme == ThemeChoice.LIGHT ? "light" : "dark");
		save();
	}

	private void save() throws IOException {
		Path parent = file.toAbsolutePath().getParent();
		Files.createDirectories(parent);
		Path temp = Files.createTempFile(parent, "settings", ".tmp");
		try {
			try (OutputStream out = Files.newOutputStream(temp)) {
				values.store(out, "Syx Toolkit settings");
			}
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(temp);
		}
	}
}
