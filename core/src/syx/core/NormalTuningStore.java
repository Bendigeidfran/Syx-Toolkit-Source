package syx.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import syx.core.NormalGen.Section;
import syx.core.NormalGen.Tuning;

/**
 * Remembers each sheet's shading values, in the toolkit's own folder, so nothing is added to the mod. The file is
 * read on each call and rewritten on each save, because the app and an agent's server can run at the same time.
 */
public final class NormalTuningStore {

	private final Path file;

	public NormalTuningStore(Path file) {
		this.file = file;
	}

	public static NormalTuningStore beside(ToolkitSettings settings) {
		return new NormalTuningStore(settings.folder().resolve("normal-tuning.properties"));
	}

	/** The values saved for a sheet. A section with its defaults, or with values no longer allowed, isn't included. */
	public Map<String, Tuning> load(String mod, String path) {
		Map<String, Tuning> tuning = new LinkedHashMap<>();
		String saved = read().getProperty(key(mod, path));
		if (saved == null) {
			return tuning;
		}
		List<Section> sections = NormalGen.sections(path);
		for (String entry : saved.split(",")) {
			String[] parts = entry.split("[=:]");
			if (parts.length == 3 && sections.stream().anyMatch(section -> section.id().equals(parts[0]))) {
				try {
					int strength = Integer.parseInt(parts[1]);
					int sharpness = Integer.parseInt(parts[2]);
					if (strength >= NormalGen.MIN_STRENGTH && strength <= NormalGen.MAX_STRENGTH && sharpness >= NormalGen.MIN_SHARPNESS && sharpness <= NormalGen.MAX_SHARPNESS) {
						tuning.put(parts[0], new Tuning(strength, sharpness));
					}
				} catch (NumberFormatException e) {
					// A hand-edited value that can't be read is treated as not set.
				}
			}
		}
		return tuning;
	}

	/** Replaces what's saved for a sheet. Only values that differ from a section's defaults are kept. */
	public void save(String mod, String path, Map<String, Tuning> tuning) throws IOException {
		StringBuilder value = new StringBuilder();
		for (Section section : NormalGen.sections(path)) {
			Tuning given = tuning.get(section.id());
			if (given != null && !given.equals(section.defaults())) {
				value.append(value.length() == 0 ? "" : ",").append(section.id()).append('=').append(given.strength()).append(':').append(given.sharpness());
			}
		}
		Properties values = read();
		if (value.length() == 0) {
			values.remove(key(mod, path));
		} else {
			values.setProperty(key(mod, path), value.toString());
		}
		write(values);
	}

	private static String key(String mod, String path) {
		return mod + "|" + path.replace('\\', '/');
	}

	private Properties read() {
		Properties values = new Properties();
		if (Files.isRegularFile(file)) {
			try (InputStream in = Files.newInputStream(file)) {
				values.load(in);
			} catch (IOException | IllegalArgumentException e) {
				// Unreadable values are treated as none, so sheets simply use their defaults.
				values.clear();
			}
		}
		return values;
	}

	private void write(Properties values) throws IOException {
		Path parent = file.toAbsolutePath().getParent();
		Files.createDirectories(parent);
		Path temp = Files.createTempFile(parent, "normal-tuning", ".tmp");
		try {
			Properties sorted = new Properties();
			new TreeMap<>(values).forEach(sorted::put);
			try (OutputStream out = Files.newOutputStream(temp)) {
				sorted.store(out, "Syx Toolkit normal map shading values");
			}
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(temp);
		}
	}
}
