package syx.core.wizard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import syx.core.JsonText;

/**
 * Where an unfinished wizard is remembered: which question it's on, and the name it was given. The answers
 * themselves are in the asset's files as soon as they're given, so this holds nothing of the mod's.
 *
 * <p>It's in the Wizards folder beside the toolkit's .exe, by the user's decision on 2026-11-13: the toolkit is
 * meant to be portable, and a player finds it there. The file is read on each call and replaced whole on each
 * change, because the app and an agent's server can run at the same time.
 */
public final class WizardStore {

	public static final String FOLDER_NAME = "Wizards";
	/** For tests and runs outside a packaged build, which has no .exe to be beside. */
	public static final String FOLDER_PROPERTY = "syx.wizards";
	static final String FILE_NAME = "progress.json";

	/**
	 * One wizard in progress.
	 *
	 * @param mod the mod's folder name
	 * @param wizard the definition's id
	 * @param stem the asset's name as its files use it
	 * @param display the name as the person typed it
	 * @param look the vanilla asset it started from, or null before that's chosen
	 * @param step the question it's on
	 * @param answered the questions answered or passed, in order, which is what Back goes through
	 */
	public record Session(String mod, String wizard, String stem, String display, String look, String step, List<String> answered) {
		public Session {
			answered = List.copyOf(answered);
		}

		Session at(String nextStep, List<String> nowAnswered) {
			return new Session(mod, wizard, stem, display, look, nextStep, nowAnswered);
		}

		Session withLook(String chosen) {
			return new Session(mod, wizard, stem, display, chosen, step, answered);
		}

		boolean is(String modName, String wizardId, String assetStem) {
			return mod.equals(modName) && wizard.equals(wizardId) && stem.equalsIgnoreCase(assetStem);
		}
	}

	private final Path folder;

	/** @param folder where progress is kept, or null if it can't be kept anywhere */
	public WizardStore(Path folder) {
		this.folder = folder;
	}

	/** Beside the .exe, or where {@link #FOLDER_PROPERTY} says. Outside a packaged build with neither, nothing can be kept. */
	public static WizardStore openDefault() {
		String override = System.getProperty(FOLDER_PROPERTY);
		if (override != null && !override.isBlank()) {
			return new WizardStore(Path.of(override));
		}
		String app = System.getProperty("jpackage.app-path");
		return new WizardStore(app == null ? null : Path.of(app).resolveSibling(FOLDER_NAME));
	}

	public boolean usable() {
		return folder != null;
	}

	public Path folder() {
		return folder;
	}

	/** Why progress can't be kept, or null if it can. */
	public String problem() {
		return folder == null ? "The wizard keeps its progress in a Wizards folder beside the toolkit's .exe, so it only works in the packaged toolkit." : null;
	}

	public List<Session> sessions(String mod) {
		return read().stream().filter(session -> session.mod().equals(mod)).toList();
	}

	public Optional<Session> find(String mod, String wizard, String stem) {
		return read().stream().filter(session -> session.is(mod, wizard, stem)).findFirst();
	}

	/** Adds or replaces the session for the same mod, wizard and asset. */
	public void put(Session session) throws IOException {
		List<Session> all = new ArrayList<>(read());
		all.removeIf(existing -> existing.is(session.mod(), session.wizard(), session.stem()));
		all.add(session);
		write(all);
	}

	public void remove(String mod, String wizard, String stem) throws IOException {
		List<Session> all = new ArrayList<>(read());
		if (all.removeIf(existing -> existing.is(mod, wizard, stem))) {
			write(all);
		}
	}

	private Path file() {
		return folder.resolve(FILE_NAME);
	}

	/** Every session. A file that can't be read, or an entry missing what it needs, counts as none. */
	private List<Session> read() {
		List<Session> sessions = new ArrayList<>();
		if (folder == null || !Files.isRegularFile(file())) {
			return sessions;
		}
		try {
			Object root = JsonText.parse(Files.readString(file(), StandardCharsets.UTF_8));
			if (root instanceof Map<?, ?> map && map.get("sessions") instanceof List<?> list) {
				for (Object item : list) {
					if (item instanceof Map<?, ?> entry) {
						Session session = session(entry);
						if (session != null) {
							sessions.add(session);
						}
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			sessions.clear();
		}
		return sessions;
	}

	private static Session session(Map<?, ?> entry) {
		String mod = text(entry, "mod");
		String wizard = text(entry, "wizard");
		String stem = text(entry, "name");
		String display = text(entry, "display");
		String step = text(entry, "step");
		if (mod == null || wizard == null || stem == null || display == null || step == null) {
			return null;
		}
		List<String> answered = new ArrayList<>();
		if (entry.get("answered") instanceof List<?> list) {
			for (Object item : list) {
				if (item instanceof String id) {
					answered.add(id);
				}
			}
		}
		return new Session(mod, wizard, stem, display, text(entry, "look"), step, answered);
	}

	private static String text(Map<?, ?> entry, String key) {
		return entry.get(key) instanceof String value ? value : null;
	}

	private void write(List<Session> sessions) throws IOException {
		if (folder == null) {
			throw new IOException(problem());
		}
		List<Object> list = new ArrayList<>();
		for (Session session : sessions) {
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("mod", session.mod());
			entry.put("wizard", session.wizard());
			entry.put("name", session.stem());
			entry.put("display", session.display());
			entry.put("look", session.look());
			entry.put("step", session.step());
			entry.put("answered", new ArrayList<Object>(session.answered()));
			list.add(entry);
		}
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("note", "Syx Toolkit's unfinished wizards. The answers are already in each mod's files; this only says which question each is on.");
		root.put("sessions", list);
		Files.createDirectories(folder);
		Path temp = Files.createTempFile(folder, "progress", ".tmp");
		try {
			Files.writeString(temp, JsonText.writePretty(root), StandardCharsets.UTF_8);
			Files.move(temp, file(), StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(temp);
		}
	}
}
