package syx.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The sounds rooms and animals look up by name. A missing one isn't a crash: the game plays nothing
 * (SoundRaces.java:55), and a missing ambience is null, which its only use checks for (RoomInstance.java:141).
 * But the first miss among all race sounds is only reported in developer mode, as is the first among
 * ambiences, and any later one appears in an error box when the game closes (SoundRaces.java:43-53,
 * Ambiances.java:75-85, Errors.java:63-70). Everything offered here is read from the player's own game
 * and mod, because no game data ships with the toolkit. Other enabled mods aren't known here.
 */
public final class SoundEntries {

	/**
	 * One sound an asset looks up.
	 *
	 * @param prefix what the game puts before the asset's name
	 * @param folder the folder under audio/config, and under audio for the sound files
	 * @param likeKey a vanilla entry whose value starts the form, or null if none suits every asset
	 * @param readerClass the class that looks it up, which must still mention the prefix
	 * @param evidence where the game looks it up
	 */
	public record Slot(String prefix, String folder, String likeKey, String readerClass, String evidence) {
		/** Its value's key, in the form described on {@link AssetSchema}. */
		public String valueKey() {
			return "SOUND." + prefix;
		}

		/** The same sound, with its starting value taken from another vanilla entry. */
		public Slot like(String otherKey) {
			return new Slot(prefix, folder, otherKey, readerClass, evidence);
		}

		/** The entry's name in the sound file. */
		public String key(String file) {
			return prefix + file;
		}

		/** Where this asset's own file goes, under assets. */
		public String relativeFile(String file) {
			return "audio/config/" + folder + "/" + file + ".txt";
		}
	}

	public static final Slot ROOM_WORK = new Slot("ROOM_WORK_", "mono", "ROOM_WORK_FARM_GRAIN", "settlement/room/main/employment/RoomEmploymentSimple", "RoomEmploymentSimple.java:78");
	public static final Slot ROOM_CLICK = new Slot("ROOM_CLICK_", "mono", "ROOM_CLICK_FARM_GRAIN", "settlement/room/main/RoomBlueprintImp", "RoomBlueprintImp.java:76");
	public static final Slot ROOM_AMBIENCE = new Slot("ROOM_", "ambience", "ROOM_FARM_GRAIN", "settlement/room/main/RoomBlueprintImp", "RoomBlueprintImp.java:75");
	public static final Slot ROOM_SERVICE = new Slot("ROOM_SERVICE_", "mono", null, "settlement/room/service/module/RoomService", "RoomService.java:48");
	/**
	 * Room types reviewed as building a RoomService, so they look up ROOM_SERVICE_ (ROOM_TEMPLE.java:48, ROOM_SHRINE.java:32,
	 * ROOM_TAVERN.java:46, ROOM_EATERY.java:46, ROOM_CANTEEN.java:52, ROOM_WELL.java:26, ROOM_BATH.java:48, ROOM_LAVATORY.java:28,
	 * ROOM_BARBER.java:32, ROOM_PHYSICIAN.java:31, ROOM_PLEASURE.java:31, ROOM_MARKET.java:49,
	 * ROOM_STAGE.java:36, ROOM_SPEAKER.java:37, ROOM_FIGHTPIT.java:60, ROOM_ARENA.java:49). Others may too, unreviewed.
	 */
	static final Set<String> SERVICE_ROOM_TYPES = Set.of("TEMPLE", "SHRINE", "TAVERN", "EATERY", "CANTEEN", "WELL", "BATH", "LAVATORY", "BARBER", "PHYSICIAN", "PLEASURE", "MARKET",
			"STAGE", "SPEAKER", "FIGHTPIT", "ARENAG");
	public static final Slot ANIMAL_CALL = new Slot("ANIMAL_CALL_", "mono", null, "settlement/entity/animal/AnimalSpecies", "AnimalSpecies.java:107");

	/** Where an asset's entry for a sound is. Only the asset's own file can be edited with it. */
	public enum State {
		OWN_FILE, OTHER_MOD_FILE, GAME, MISSING
	}

	/**
	 * An asset's entry for a sound.
	 *
	 * @param value what it plays, or null if it's missing
	 * @param source the mod's file it's in, under assets, or null if it isn't in one
	 */
	public record Info(State state, String value, String source) {
		public static final Info MISSING = new Info(State.MISSING, null, null);
	}

	/** What the game treats as no sound at all (AudioFactory.java:40). */
	public static final String NO_SOUND = "DUMMY";

	private static final String IGNORE_VANILLA = "_IgnoreVanilla.txt";
	private static final Map<String, String> SOUND_FILE_ENDING = Map.of("mono", ".wav", "ambience", ".ogg");

	private final Map<String, Map<String, String>> entries = new HashMap<>();
	private final Map<String, Map<String, Path>> sources = new HashMap<>();
	private final Map<String, Set<String>> modSounds = new HashMap<>();
	private final Set<String> roomTypes = new HashSet<>();

	private SoundEntries() {
	}

	/**
	 * Reads every entry the game would, with the mod's added. Files of the same name merge in the game
	 * (Json.java:29-42), and other names are added (VirtualFolder.java:138-165), so a mod's entries
	 * count whichever file they're in.
	 */
	public static SoundEntries load(Path gameFolder, Path modVersionFolder, GameParser parser) throws IOException {
		SoundEntries sounds = new SoundEntries();
		Path assets = modVersionFolder.resolve("assets");
		Path temp = Files.createTempDirectory("syx-sounds");
		try (ZipFile zip = new ZipFile(gameFolder.resolve("base").resolve("data.zip").toFile())) {
			for (String folder : SOUND_FILE_ENDING.keySet()) {
				Map<String, String> found = new HashMap<>();
				sounds.entries.put(folder, found);
				Map<String, Path> from = new HashMap<>();
				sounds.sources.put(folder, from);
				Path modFolder = assets.resolve("audio").resolve("config").resolve(folder);
				if (!Files.isRegularFile(modFolder.resolve(IGNORE_VANILLA), LinkOption.NOFOLLOW_LINKS)) {
					readVanilla(zip, folder, temp, parser, found);
				}
				for (Path file : ResourceProducers.txtFiles(modFolder)) {
					readFile(file, parser, found, from);
				}
				sounds.modSounds.put(folder, soundFilesIn(assets.resolve("audio").resolve(folder), SOUND_FILE_ENDING.get(folder)));
			}
			Enumeration<? extends ZipEntry> all = zip.entries();
			while (all.hasMoreElements()) {
				String name = all.nextElement().getName();
				if (name.matches("data/assets/init/room/[A-Za-z0-9]+_[^/]+\\.txt")) {
					String file = name.substring(name.lastIndexOf('/') + 1);
					sounds.roomTypes.add(file.substring(0, file.indexOf('_')));
				}
			}
		} finally {
			try (Stream<Path> walk = Files.walk(temp)) {
				for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
					Files.deleteIfExists(path);
				}
			}
		}
		return sounds;
	}

	private static void readVanilla(ZipFile zip, String folder, Path temp, GameParser parser, Map<String, String> found) throws IOException {
		String prefix = "data/assets/audio/config/" + folder + "/";
		Enumeration<? extends ZipEntry> all = zip.entries();
		while (all.hasMoreElements()) {
			ZipEntry entry = all.nextElement();
			String name = entry.getName();
			if (!entry.isDirectory() && name.startsWith(prefix) && name.endsWith(".txt") && name.indexOf('/', prefix.length()) < 0) {
				Path copy = temp.resolve(folder + "-" + name.substring(prefix.length()));
				try (InputStream in = zip.getInputStream(entry)) {
					Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
				}
				readFile(copy, parser, found, new HashMap<>());
			}
		}
	}

	/** A file the game can't read stops it loading anyway, so it just adds nothing here. */
	private static void readFile(Path file, GameParser parser, Map<String, String> found, Map<String, Path> from) {
		try {
			GameParser.Node node = parser.root(file);
			for (String key : node.keys()) {
				found.put(key, node.isList(key) ? String.join(", ", node.values(key)) : node.value(key));
				from.put(key, file);
			}
		} catch (GameParser.ReadException e) {
			// Deliberately ignored, as above.
		}
	}

	/** Sound files in a mod, written the way an entry refers to them, such as work->MyDrill. */
	private static Set<String> soundFilesIn(Path folder, String ending) throws IOException {
		Set<String> found = new TreeSet<>();
		if (Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
			try (Stream<Path> walk = Files.walk(folder)) {
				for (Path path : walk.toList()) {
					String name = folder.relativize(path).toString().replace('\\', '/');
					if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && name.toLowerCase(Locale.ROOT).endsWith(ending)) {
						found.add(name.substring(0, name.length() - ending.length()).replace("/", "->"));
					}
				}
			}
		}
		return found;
	}

	/** What can be chosen for a sound: no sound, what vanilla and the mod use for this kind, and the mod's own sound files. */
	public List<String> options(Slot slot) {
		Set<String> found = new TreeSet<>();
		entries.get(slot.folder()).forEach((key, value) -> {
			if (key.startsWith(slot.prefix()) && !value.isBlank() && !value.equals(NO_SOUND)) {
				found.add(value);
			}
		});
		found.addAll(modSounds.get(slot.folder()));
		List<String> options = new ArrayList<>();
		options.add(NO_SOUND);
		options.addAll(found);
		return options;
	}

	/** What the form starts from: vanilla's value for a similar asset, or null if nothing suits. */
	public String startingValue(Slot slot) {
		String value = slot.likeKey() == null ? null : entries.get(slot.folder()).get(slot.likeKey());
		return value == null || value.isBlank() ? null : value;
	}

	/** The game's own entry for an asset's sound, or null if it has none or the current mod replaces it. */
	public String gameValue(Slot slot, String file) {
		String key = slot.key(file);
		return sources.get(slot.folder()).containsKey(key) ? null : entries.get(slot.folder()).get(key);
	}

	/** True if the game or mod already has this entry. */
	public boolean has(Slot slot, String file) {
		return entries.get(slot.folder()).containsKey(slot.key(file));
	}

	/** Finds an asset's entry for a sound, given the mod's assets folder. A mod's entry replaces the game's, so it counts first. */
	public Info info(Slot slot, String file, Path assets) {
		String key = slot.key(file);
		String value = entries.get(slot.folder()).get(key);
		if (value == null) {
			return Info.MISSING;
		}
		Path source = sources.get(slot.folder()).get(key);
		if (source == null) {
			return new Info(State.GAME, value, null);
		}
		boolean own = source.toAbsolutePath().normalize().equals(assets.resolve(slot.relativeFile(file)).toAbsolutePath().normalize());
		return new Info(own ? State.OWN_FILE : State.OTHER_MOD_FILE, value, assets.relativize(source).toString().replace('\\', '/'));
	}

	/**
	 * The entries a room looks up. Only a room with employment plays a work sound (RoomBlueprintIns.java:66),
	 * and only a reviewed service room is checked for its service sound.
	 */
	public List<String> missingForRoom(String file, boolean hasWork) {
		List<String> missing = new ArrayList<>();
		List<Slot> slots = new ArrayList<>(hasWork ? List.of(ROOM_WORK, ROOM_CLICK, ROOM_AMBIENCE) : List.of(ROOM_CLICK, ROOM_AMBIENCE));
		int split = file.indexOf('_');
		if (split > 0 && SERVICE_ROOM_TYPES.contains(file.substring(0, split))) {
			slots.add(ROOM_SERVICE);
		}
		for (Slot slot : slots) {
			if (!has(slot, file)) {
				missing.add(slot.key(file));
			}
		}
		return missing;
	}

	public List<String> missingForAnimal(String file) {
		return has(ANIMAL_CALL, file) ? List.of() : List.of(ANIMAL_CALL.key(file));
	}

	/**
	 * The entries a mod's rooms and animals look up and don't have, in name order. A room is only loaded if
	 * its name starts with a room type and _ (RoomsCreator.java:33), so anything else isn't counted.
	 */
	public List<String> missingIn(Path modVersionFolder, GameParser parser) throws IOException {
		Set<String> missing = new TreeSet<>();
		Path init = modVersionFolder.resolve("assets").resolve("init");
		for (Path file : ResourceProducers.txtFiles(init.resolve("animal"))) {
			missing.addAll(missingForAnimal(stem(file)));
		}
		for (Path file : ResourceProducers.txtFiles(init.resolve("room"))) {
			String name = stem(file);
			int split = name.indexOf('_');
			if (split > 0 && roomTypes.contains(name.substring(0, split))) {
				try {
					GameParser.Node room = parser.root(file);
					missing.addAll(missingForRoom(name, room.has("WORK") || room.has("EMPLOYMENT")));
				} catch (GameParser.ReadException e) {
					// A room the game can't read stops it loading, which isn't about its sounds.
				}
			}
		}
		return new ArrayList<>(missing);
	}

	private static String stem(Path file) {
		String name = file.getFileName().toString();
		return name.substring(0, name.length() - 4);
	}

	/** What to tell the user about entries that are missing, or null if there are none. */
	public static String warning(List<String> keys) {
		if (keys.isEmpty()) {
			return null;
		}
		return "These sound entries are missing: " + String.join(", ", keys) + ". The game plays nothing for them, and can list them in an error box when it closes (SoundRaces.java:43-53). This doesn't affect play."
				+ " To add them, list them in a new .txt file: ambience entries, which start ROOM_ without WORK or CLICK, go in audio/config/ambience, and the rest in audio/config/mono."
				+ " For example, " + keys.get(0) + ": " + NO_SOUND + ", means no sound. If another enabled mod has them, this doesn't apply.";
	}
}
