package syx.core;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * What can be referred to: the files in the game's data.zip plus the current mod. Read-only. Other
 * mods the player has enabled aren't known here.
 */
public final class GameAssetIndex {

	/** An image's size, read from its PNG header. Zero if it isn't a readable PNG. */
	public record ImageSize(int width, int height) {
	}

	private static final String ZIP_ROOT = "data/assets/";

	private final TreeMap<String, ImageSize> images = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	private final TreeSet<String> files = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
	private final TreeSet<String> gameFiles = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
	private final TreeSet<String> modFiles = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
	private final TreeMap<String, Crop> crops = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	private final Map<String, GameTree.Block> starters = new java.util.HashMap<>();

	private SoundEntries sounds;
	/** The floors that are roads, which the parser has to read, or null if it wasn't given. */
	private List<String> roads;

	/** What a crop file says, as the game's parser reads it. */
	public record Crop(String resource, String image) {
	}

	private GameAssetIndex() {
	}

	/** Reads the game's data and the mod's current version folder. */
	public static GameAssetIndex load(Path gameFolder, Path modVersionFolder) throws IOException {
		GameAssetIndex index = new GameAssetIndex();
		index.readZip(gameFolder.resolve("base").resolve("data.zip"));
		Path modAssets = modVersionFolder.resolve("assets");
		if (Files.isDirectory(modAssets, LinkOption.NOFOLLOW_LINKS)) {
			index.readFolder(modAssets);
		}
		return index;
	}

	/** Also reads what each crop yields, which takes the game's parser. */
	public static GameAssetIndex load(Path gameFolder, Path modVersionFolder, GameParser parser) throws IOException {
		GameAssetIndex index = load(gameFolder, modVersionFolder);
		index.sounds = SoundEntries.load(gameFolder, modVersionFolder, parser);
		Path temp = Files.createTempDirectory("syx-crops");
		try {
			ResourceProducers.files(gameFolder, modVersionFolder, "init/resource/growable", temp).forEach((name, file) -> {
				try {
					GameParser.Node crop = parser.root(file);
					index.crops.put(name, new Crop(crop.value("RESOURCE"), crop.block("SPRITE").value("SPRITE")));
				} catch (GameParser.ReadException e) {
					// A crop the game can't read stops it loading anyway, so it isn't listed.
				}
			});
			// A floor is a road when its file has a ROAD block (Floors.java:579), and only roads are in the ROAD map (Floors.java:160-174).
			TreeSet<String> roads = new TreeSet<>();
			ResourceProducers.files(gameFolder, modVersionFolder, "init/settlement/floor", temp.resolve("floors")).forEach((name, file) -> {
				try {
					if (parser.root(file).has("ROAD")) {
						roads.add(name.startsWith("_") ? name.substring(1) : name);
					}
				} catch (GameParser.ReadException e) {
					// A floor the game can't read stops it loading anyway, so it isn't listed.
				}
			});
			index.roads = new ArrayList<>(roads);
			for (String file : RoomSchemas.STARTER_FILES) {
				index.readStarter(gameFolder, file, temp, parser);
			}
			for (String folder : RaceSchemas.STARTER_FOLDERS) {
				index.readStarterFolder(gameFolder, folder, temp, parser);
			}
		} finally {
			try (Stream<Path> walk = Files.walk(temp)) {
				for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
					Files.deleteIfExists(path);
				}
			}
		}
		return index;
	}

	/** The same index for editing an asset, which must not be compared with itself. Only a crop's rules look at other assets of its type. */
	public GameAssetIndex editing(AssetType type, String file) {
		GameAssetIndex copy = new GameAssetIndex();
		copy.images.putAll(images);
		copy.files.addAll(files);
		copy.gameFiles.addAll(gameFiles);
		copy.modFiles.addAll(modFiles);
		copy.crops.putAll(crops);
		copy.starters.putAll(starters);
		copy.sounds = sounds;
		copy.roads = roads;
		if (type.initFolder().equals("resource/growable")) {
			copy.crops.remove(file);
		}
		if (type.initFolder().equals("resource/minable")) {
			copy.files.remove("init/resource/minable/" + file + ".txt");
		}
		return copy;
	}

	/** A file from the player's own game, whole, that new assets take starting values from. Unreadable ones are simply left out. */
	private void readStarter(Path gameFolder, String file, Path temp, GameParser parser) throws IOException {
		try (ZipFile zip = new ZipFile(gameFolder.resolve("base").resolve("data.zip").toFile())) {
			ZipEntry entry = zip.getEntry(ZIP_ROOT + file);
			if (entry != null) {
				Path copy = temp.resolve("starter-" + starters.size() + ".txt");
				try (InputStream in = zip.getInputStream(entry)) {
					Files.copy(in, copy);
				}
				try {
					starters.put(file, GameTree.read(parser.root(copy)));
				} catch (GameParser.ReadException e) {
					// A new asset then starts without these values, and the form asks for them.
				}
			}
		}
	}

	/**
	 * One file from the player's own data.zip, read when asked by the game's parser, or null if the game doesn't
	 * have it. Nothing is kept: the copy the parser needs is deleted before this returns.
	 */
	public static GameTree.Block readGameFile(Path gameFolder, String relative, GameParser parser) throws IOException, GameParser.ReadException {
		try (ZipFile zip = new ZipFile(gameFolder.resolve("base").resolve("data.zip").toFile())) {
			ZipEntry entry = zip.getEntry(ZIP_ROOT + relative);
			if (entry == null) {
				return null;
			}
			Path copy = Files.createTempFile("syx-vanilla", ".txt");
			try {
				try (InputStream in = zip.getInputStream(entry)) {
					Files.copy(in, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				}
				return GameTree.read(parser.root(copy));
			} finally {
				Files.deleteIfExists(copy);
			}
		}
	}

	/** A starter file that was read, or null. */
	public GameTree.Block starter(String file) {
		return starters.get(file);
	}

	/** Reads every text file directly in a folder of the player's own game, for a new asset to start from. */
	private void readStarterFolder(Path gameFolder, String folder, Path temp, GameParser parser) throws IOException {
		String prefix = ZIP_ROOT + (folder.endsWith("/") ? folder : folder + "/");
		try (ZipFile zip = new ZipFile(gameFolder.resolve("base").resolve("data.zip").toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName();
				if (entry.isDirectory() || !name.startsWith(prefix) || !name.endsWith(".txt")) {
					continue;
				}
				String rest = name.substring(prefix.length());
				if (rest.indexOf('/') >= 0) {
					continue;
				}
				readStarter(gameFolder, name.substring(ZIP_ROOT.length()), temp, parser);
			}
		}
	}

	/** The sound entries that were read, or null if this index wasn't given the game's parser. */
	public SoundEntries sounds() {
		return sounds;
	}

	/** The crops that were read, by name. */
	public Map<String, Crop> crops() {
		return crops;
	}

	/** One crop, ignoring a leading _, or null. */
	public Crop crop(String name) {
		Crop found = crops.get(name);
		return found != null ? found : crops.get("_" + name);
	}

	/** A leading _ also works without it (KeyMap.java:42-55). */
	public static boolean sameKey(String one, String two) {
		return plainKey(one).equals(plainKey(two));
	}

	private static String plainKey(String key) {
		return key.startsWith("_") ? key.substring(1) : key;
	}

	private void readZip(Path zipFile) throws IOException {
		try (ZipFile zip = new ZipFile(zipFile.toFile())) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				String name = entry.getName();
				if (!entry.isDirectory() && name.startsWith(ZIP_ROOT)) {
					String relative = name.substring(ZIP_ROOT.length());
					files.add(relative);
					gameFiles.add(relative);
					if (isPng(relative) && isIndexedImage(relative)) {
						try (InputStream in = zip.getInputStream(entry)) {
							images.put(relative, readSize(in));
						}
					}
				}
			}
		}
	}

	private void readFolder(Path assets) throws IOException {
		try (Stream<Path> walk = Files.walk(assets)) {
			for (Path path : walk.toList()) {
				if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
					String relative = assets.relativize(path).toString().replace('\\', '/');
					files.add(relative);
					modFiles.add(relative);
					if (isPng(relative) && isIndexedImage(relative)) {
						try (InputStream in = Files.newInputStream(path)) {
							images.put(relative, readSize(in));
						}
					}
				}
			}
		}
	}

	private static boolean isIndexedImage(String relative) {
		return relative.startsWith("sprite/animal/") || relative.startsWith("sprite/icon/") || relative.startsWith("sprite/resource/") || relative.startsWith("sprite/game/")
				|| relative.startsWith("sprite/race/") || relative.startsWith("sprite/world/centre/");
	}

	private static boolean isPng(String name) {
		return name.toLowerCase(Locale.ROOT).endsWith(".png");
	}

	private static ImageSize readSize(InputStream in) {
		try {
			DataInputStream data = new DataInputStream(in);
			long signature = data.readLong();
			data.readInt();
			int type = data.readInt();
			// The PNG signature, followed by the IHDR chunk.
			if (signature != 0x89504E470D0A1A0AL || type != 0x49484452) {
				return new ImageSize(0, 0);
			}
			return new ImageSize(data.readInt(), data.readInt());
		} catch (IOException e) {
			return new ImageSize(0, 0);
		}
	}

	/** True if a file of this name exists in the game or mod, ignoring case as Windows does. */
	public boolean fileExists(String relative) {
		return files.contains(relative);
	}

	/** True if the game itself has a file of this name, so a mod's file of that name is merged with it. */
	public boolean existsInGame(String relative) {
		return gameFiles.contains(relative);
	}

	/** True if the current mod's version folder has a file of this name, whether or not the game has one too. */
	public boolean inMod(String relative) {
		return modFiles.contains(relative);
	}

	/** True if the current mod has this file. A file the game also has is not this, because the two are merged. */
	public boolean ownedByMod(String relative) {
		return modFiles.contains(relative) && !gameFiles.contains(relative);
	}

	/** The names a tag can refer to, in order. */
	public List<String> options(TagSpec.Reference reference) {
		if (reference == null) {
			return List.of();
		}
		return switch (reference) {
		case RESOURCES -> keysIn("init/resource/");
		case DAMAGES -> keysIn("init/stats/damage/");
		case CLIMATES -> List.of("COLD", "TEMPERATE", "HOT");
		case TERRAINS -> List.of("OCEAN", "WET", "MOUNTAIN", "FOREST", "NONE");
		case ICONS -> iconSheets();
		case GROWABLES -> keysIn("init/resource/growable/");
		case FLOORS -> keysIn("init/settlement/floor/");
		case ENVIRONMENTS -> keysIn("init/settlement/environment/");
		case MINABLES -> keysIn("init/resource/minable/");
		case EDIBLES -> keysIn("init/resource/edible/");
		case DRINKABLES -> keysIn("init/resource/drinkable/");
		case SUPPLIES -> keysIn("init/resource/supply/");
		case TRAITS -> keysIn("init/race/trait/");
		case RACES -> keysIn("init/race/");
		case ROOMS -> keysIn("init/room/");
		case POOL_ROOMS -> keysIn("init/room/").stream().filter(name -> name.startsWith("POOL_")).toList();
		case WORLD_BUILDINGS -> worldBuildings();
		case ANIMALS -> keysIn("init/animal/");
		// RELIGIONS.java:28 maps each file in init/religion by name.
		case RELIGIONS -> keysIn("init/religion/");
		// NEEDS.java:40-41 makes a need of each file in init/stats/need, and :68 maps them by name.
		case NEEDS -> keysIn("init/stats/need/");
		// Without the parser the floors can't be read, so every floor is offered, and a non-road only gets a warning.
		case ROADS -> roads == null ? keysIn("init/settlement/floor/") : roads;
		// BUILDING_PREFS.java:28-47: mountain, outdoors and mud, then a structure per file in init/settlement/structure.
		case STRUCTURES -> {
			TreeSet<String> keys = new TreeSet<>(List.of("MOUNTAIN", "OUTDOORS", "MUD"));
			keys.addAll(keysIn("init/settlement/structure/"));
			yield new ArrayList<>(keys);
		}
		// Fixed in the game's code: CRIMES.java:51-64 and CRIME_PUNISHMENTS.java:54-60. The stocks are keyed NONE.
		case CRIMES -> List.of("WAR", "S_THEFT", "S_MURDER", "S_DISRESPECT", "S_PLEASURE", "FLASHING", "THEFT", "VANDALISM", "MURDER", "DISRESPECT", "PLEASURE", "SPEECH");
		case PUNISHMENTS -> List.of("PARDON", "NONE", "BANISH", "PRISON", "EXECUTE", "HARVEST", "ENSLAVE");
		};
	}

	/**
	 * A world building's key is its category folder, in capitals, then the file name (RDBuilding.java:77).
	 * {@code _CAT} is the category, not a building.
	 */
	private List<String> worldBuildings() {
		TreeSet<String> keys = new TreeSet<>();
		String folder = "init/world/building/";
		for (String file : files) {
			if (!file.startsWith(folder) || !file.endsWith(".txt")) {
				continue;
			}
			String rest = file.substring(folder.length());
			int slash = rest.indexOf('/');
			if (slash < 0 || rest.indexOf('/', slash + 1) >= 0) {
				continue;
			}
			String name = rest.substring(slash + 1, rest.length() - 4);
			if (name.equals("_CAT")) {
				continue;
			}
			keys.add(rest.substring(0, slash).toUpperCase(Locale.ENGLISH) + "_" + name);
		}
		return new ArrayList<>(keys);
	}

	/** File names in one folder, without subfolders. A leading _ also works without it (KeyMap.java:42-55). */
	public List<String> keysInFolder(String folder) {
		return keysIn(folder.endsWith("/") ? folder : folder + "/");
	}

	/** The names of the game's own files directly in a folder, as written, without .txt. */
	public List<String> gameKeysInFolder(String folder) {
		String prefix = folder.endsWith("/") ? folder : folder + "/";
		TreeSet<String> keys = new TreeSet<>();
		for (String file : gameFiles) {
			if (file.startsWith(prefix)) {
				String name = file.substring(prefix.length());
				if (name.indexOf('/') < 0 && name.endsWith(".txt")) {
					keys.add(name.substring(0, name.length() - 4));
				}
			}
		}
		return new ArrayList<>(keys);
	}

	/**
	 * The names of the text files directly in a folder, in the game or the mod, exactly as written and
	 * without .txt. These are what a tag that opens a file by name can give, because the game finds it
	 * with the folder's get or gets, which take the name as it is (VirtualFolder.java:59-95). A name
	 * starting with _ is left out, as it's a note or a shared file rather than one to choose, such as
	 * text/race/tourist/_WIKI. A value that names one is still accepted, and the form shows it.
	 */
	public List<String> fileNames(String folder) {
		String prefix = folder.endsWith("/") ? folder : folder + "/";
		TreeSet<String> names = new TreeSet<>();
		for (String file : files) {
			if (file.startsWith(prefix)) {
				String name = file.substring(prefix.length());
				if (name.indexOf('/') < 0 && name.endsWith(".txt") && !name.startsWith("_")) {
					names.add(name.substring(0, name.length() - 4));
				}
			}
		}
		return new ArrayList<>(names);
	}

	/** File names in one folder, without subfolders. A leading _ also works without it (KeyMap.java:42-55). */
	private List<String> keysIn(String folder) {
		TreeSet<String> keys = new TreeSet<>();
		for (String file : files) {
			if (file.startsWith(folder)) {
				String name = file.substring(folder.length());
				if (name.indexOf('/') < 0 && name.endsWith(".txt")) {
					String key = name.substring(0, name.length() - 4);
					keys.add(key.startsWith("_") ? key.substring(1) : key);
				}
			}
		}
		return new ArrayList<>(keys);
	}

	/**
	 * The images directly in a folder, by name. Subfolders hold other things, and a name starting with _
	 * is shared rather than an asset's own, such as animals' _Texture (Sprites.java:22).
	 */
	public List<String> spriteOptions(String folder) {
		List<String> names = new ArrayList<>();
		for (String image : images.keySet()) {
			if (image.startsWith(folder)) {
				String name = image.substring(folder.length());
				if (name.indexOf('/') < 0 && !name.startsWith("_")) {
					names.add(name.substring(0, name.length() - 4));
				}
			}
		}
		return names;
	}

	/** Images in a folder whose size the game accepts for this tag. */
	public List<String> spriteOptions(TagSpec.Sprite rule) {
		List<String> names = new ArrayList<>();
		for (String name : spriteOptions(rule.folder())) {
			if (rule.folder().startsWith("sprite/game/")) {
				// These sheets are several tiles across, with a normal map beside the picture. sheetRows is that check.
				SheetRows rows = sheetRows(rule.folder(), name);
				if (rows.exists() && rows.validSize()) {
					names.add(name);
				}
				continue;
			}
			ImageSize size = spriteSize(rule.folder(), name);
			boolean tallEnough = rule.exactHeight() ? size != null && size.height() == rule.minHeight() : size != null && size.height() >= rule.minHeight();
			if (size != null && size.width() == rule.width() && tallEnough && size.height() > 0) {
				names.add(name);
			}
		}
		return names;
	}

	/** Icon sheets written the way the tag wants them, such as 32->animal->Auroch. */
	private List<String> iconSheets() {
		List<String> sheets = new ArrayList<>();
		String folder = "sprite/icon/";
		for (String image : images.keySet()) {
			if (image.startsWith(folder)) {
				String path = image.substring(folder.length(), image.length() - 4);
				if (path.matches("(16|24|32)/.+")) {
					sheets.add(path.replace("/", "->"));
				}
			}
		}
		return sheets;
	}

	/** The size of an image in a folder, or null if there's no such file. */
	public ImageSize spriteSize(String folder, String name) {
		return images.get(folder + name + ".png");
	}

	/**
	 * Sheets with this file name, under assets, other than icons. Icons are named the same way and are not
	 * sprite sheets.
	 */
	public List<String> spriteSheets(String stem) {
		String file = stem + ".png";
		List<String> found = new ArrayList<>();
		for (String path : images.keySet()) {
			if (path.startsWith("sprite/") && !path.startsWith("sprite/icon/") && (path.endsWith("/" + file) || path.equals(file))) {
				found.add(path);
			}
		}
		return found;
	}

	/** Records a sheet just copied into the mod, so the open form can name it. */
	public void rememberSprite(Path file, String relative) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			images.put(relative, readSize(in));
		}
		files.add(relative);
		modFiles.add(relative);
	}

	/** How a sprite sheet's size was judged. A row's a frame's second part. */
	public record SheetRows(boolean exists, boolean validSize, int rows) {
	}

	/**
	 * How many rows a sprite sheet has, by the game's own sums. Half the width is the normal map. A 1x1
	 * sheet's rows can be taller than a tile (SheetType.java:227-283,289-296), and others have fixed rows (:70-108).
	 */
	public SheetRows sheetRows(String folder, String name) {
		ImageSize size = spriteSize(folder, name);
		if (size == null) {
			return new SheetRows(false, false, 0);
		}
		int width = size.width() / 2;
		int height = size.height();
		if (width <= 0 || height <= 0) {
			return new SheetRows(true, false, 0);
		}
		if (folder.startsWith("sprite/race/face/") && !folder.contains("addon")) {
			// A face frame's number counts 4 to each 60 pixel row, across then down (RaceFrameMaker.java:139-146), so
			// this is the number of frames: Hair: 61 is on a sheet of 16 rows.
			boolean ok = size.width() == 416 && size.height() >= 60;
			return new SheetRows(true, ok, ok ? size.height() / 60 * 4 : 0);
		}
		if (folder.endsWith("/1x1/")) {
			if (width % 22 != 0) {
				return new SheetRows(true, false, 0);
			}
			for (int tall = 1; tall < 9; tall++) {
				if (height % (16 * tall + 6) == 0) {
					return new SheetRows(true, true, tall * height / (6 + tall * 16));
				}
			}
			return new SheetRows(true, false, 0);
		}
		if (folder.endsWith("/texture/")) {
			// 16 pixels a tile plus 12 across, and rows of 28 (SheetType.java:153-164).
			boolean ok = width >= 12 && (width - 12) % 16 == 0 && height % 28 == 0;
			return ok ? new SheetRows(true, true, height / 28) : new SheetRows(true, false, 0);
		}
		// A box sheet's tiles are 76 square (SheetType.java:524,86).
		int tile = folder.endsWith("/combo/") ? 72 : folder.endsWith("/box/") ? 76 : Character.digit(folder.charAt(folder.length() - 2), 10) * 16 + 12;
		return width % tile == 0 && height % tile == 0 ? new SheetRows(true, true, height / tile) : new SheetRows(true, false, 0);
	}

	/**
	 * How many icons a sheet holds, or -1 if it isn't a valid sheet. Half the image's width is the
	 * normal map, and tiles have a 6 pixel gap (IconMaker.java, sheet).
	 */
	public int iconCount(String sheet) {
		ImageSize size = images.get("sprite/icon/" + sheet.replace("->", "/") + ".png");
		if (size == null || size.width() == 0 || !sheet.matches("(16|24|32)->.+")) {
			return -1;
		}
		int dimension = Integer.parseInt(sheet.substring(0, 2));
		int width = size.width() / 2;
		if ((width - 6) % (dimension + 6) != 0 || (size.height() - 6) % (dimension + 6) != 0) {
			return -1;
		}
		return (width - 6) / (dimension + 6) * ((size.height() - 6) / (dimension + 6));
	}
}
