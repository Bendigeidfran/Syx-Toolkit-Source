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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Which resources something in the game or a mod can produce. The game looks up the best recipe for
 * every resource without checking there is one, so a resource with none crashes a new game
 * (NPCRes.java:69-70, UIAdvice.java:335). Recipes only come from rooms (Creator.java:60-68), which are
 * read here through the game's own parser. Other enabled mods aren't known here.
 */
public final class ResourceProducers {

	/**
	 * Room files that make recipes, by what their name starts with. The types come from ROOMS.java and
	 * RoomsCreator.java:33. Hunters are left out on purpose: their output is marked as not being a recipe
	 * (ROOM_HUNTER.java:106, Creator.java:64). Canteens and baths are left out too: their recipe has no bonus, so an output
	 * crashes the game rather than producing (ROOM_CANTEEN.java:51, ROOM_BATH.java:61, Creator.java:94). A file that matches no type is never loaded.
	 */
	private static final List<String> PRODUCING_TYPES = List.of("FARM_", "ORCHARD_", "PASTURE_", "REFINER_", "WORKSHOP_", "FISHERY_", "BREEDER_", "NURSERY_",
			"SCHOOL_");
	private static final Set<String> PRODUCING_ROOMS = Set.of("_WOODCUTTER", "_ASYLUM", "_HOSPITAL");
	private static final String MINE = "MINE_";
	private static final String IGNORE_VANILLA = "_IgnoreVanilla.txt";

	private record Recipe(String output, List<String> inputs, double aiRate) {
	}

	private final Set<String> produced;

	private ResourceProducers(Set<String> produced) {
		this.produced = produced;
	}

	/** Reads the game's rooms, with the mod's on top, as the game would (VirtualFolder.java:229). */
	public static ResourceProducers load(Path gameFolder, Path modVersionFolder, GameParser parser) throws IOException {
		Path temp = Files.createTempDirectory("syx-producers");
		try {
			Map<String, Path> rooms = files(gameFolder, modVersionFolder, "init/room", temp.resolve("room"));
			Map<String, Path> minables = files(gameFolder, modVersionFolder, "init/resource/minable", temp.resolve("minable"));
			List<Recipe> recipes = new ArrayList<>();
			rooms.forEach((name, file) -> readRoom(name, file, minables, parser, recipes));
			return new ResourceProducers(resolve(recipes));
		} finally {
			try (Stream<Path> walk = Files.walk(temp)) {
				for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
					Files.deleteIfExists(path);
				}
			}
		}
	}

	/** The files in one folder by name, without .txt. The parser needs real files, so vanilla's are copied out of data.zip. */
	static Map<String, Path> files(Path gameFolder, Path modVersionFolder, String folder, Path temp) throws IOException {
		Map<String, Path> found = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		Path modFolder = modVersionFolder.resolve("assets").resolve(folder);
		if (!Files.isRegularFile(modFolder.resolve(IGNORE_VANILLA), LinkOption.NOFOLLOW_LINKS)) {
			Files.createDirectories(temp);
			String prefix = "data/assets/" + folder + "/";
			try (ZipFile zip = new ZipFile(gameFolder.resolve("base").resolve("data.zip").toFile())) {
				Enumeration<? extends ZipEntry> entries = zip.entries();
				while (entries.hasMoreElements()) {
					ZipEntry entry = entries.nextElement();
					String name = entry.getName();
					if (!entry.isDirectory() && name.startsWith(prefix) && name.endsWith(".txt") && name.indexOf('/', prefix.length()) < 0) {
						Path copy = temp.resolve(name.substring(prefix.length()));
						try (InputStream in = zip.getInputStream(entry)) {
							Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
						}
						found.put(stem(copy), copy);
					}
				}
			}
		}
		for (Path file : txtFiles(modFolder)) {
			found.put(stem(file), file);
		}
		return found;
	}

	static List<Path> txtFiles(Path folder) throws IOException {
		if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
			return List.of();
		}
		try (Stream<Path> list = Files.list(folder)) {
			return list.filter(p -> p.getFileName().toString().endsWith(".txt") && !p.getFileName().toString().equals(IGNORE_VANILLA))
					.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).sorted().toList();
		}
	}

	private static String stem(Path file) {
		String name = file.getFileName().toString();
		return name.substring(0, name.length() - 4);
	}

	/** A room the game can't read stops it loading anyway, so here it just doesn't count as a producer. */
	private static void readRoom(String name, Path file, Map<String, Path> minables, GameParser parser, List<Recipe> recipes) {
		try {
			if (name.startsWith(MINE)) {
				readMine(file, minables, parser, recipes);
			} else if (PRODUCING_ROOMS.contains(name) || PRODUCING_TYPES.stream().anyMatch(name::startsWith)) {
				GameParser.Node room = parser.root(file);
				if (room.has("INDUSTRY")) {
					readIndustry(room, recipes);
				}
				if (room.has("INDUSTRIES")) {
					for (GameParser.Node industry : room.blocks("INDUSTRIES")) {
						readIndustry(industry, recipes);
					}
				}
			}
		} catch (GameParser.ReadException e) {
			// Deliberately ignored, as above.
		}
	}

	/** A mine makes the resource its MINABLE names, at the rate in the room's file (ROOM_MINE.java:46,83, Minable.java:51). */
	private static void readMine(Path file, Map<String, Path> minables, GameParser parser, List<Recipe> recipes) throws GameParser.ReadException {
		GameParser.Node room = parser.root(file);
		Path minable = minables.get(room.value("MINABLE"));
		if (minable == null) {
			minable = minables.get("_" + room.value("MINABLE"));
		}
		if (minable != null) {
			recipes.add(new Recipe(key(parser.root(minable).value("RESOURCE")), List.of(), room.decimal("YEILD_WORKER_DAILY", 0, 1000)));
		}
	}

	/** The same reads as Industry.java:113-139. Each output is one recipe. */
	private static void readIndustry(GameParser.Node holder, List<Recipe> recipes) throws GameParser.ReadException {
		GameParser.Node industry = holder.block("INDUSTRY");
		List<String> inputs = new ArrayList<>();
		if (industry.has("IN")) {
			for (String input : industry.block("IN").keys()) {
				inputs.add(key(input));
			}
		}
		if (industry.has("OUT")) {
			GameParser.Node out = industry.block("OUT");
			for (String output : out.keys()) {
				double rate;
				if (out.isBlock(output)) {
					GameParser.Node rates = out.block(output);
					rate = rates.decimalOr("AI_RATE", 0, 100000, rates.decimal("PLAYER", 0, 100000));
				} else {
					rate = out.decimal(output, 0, 10000);
				}
				recipes.add(new Recipe(key(output), inputs, rate));
			}
		}
	}

	/**
	 * The second crash uses the table in RecipeRatesVanilla.java:39-57, which only accepts a recipe whose
	 * AI rate is above 0 and whose inputs all have a recipe themselves. So this works outwards from
	 * recipes with no inputs, as that table does.
	 */
	private static Set<String> resolve(List<Recipe> recipes) {
		Set<String> produced = new HashSet<>();
		boolean changed = true;
		while (changed) {
			changed = false;
			for (Recipe recipe : recipes) {
				if (recipe.aiRate() > 0 && produced.containsAll(recipe.inputs())) {
					changed |= produced.add(recipe.output());
				}
			}
		}
		return produced;
	}

	/** A leading _ also works without it (KeyMap.java:42-55). */
	private static String key(String name) {
		return name.startsWith("_") ? name.substring(1) : name;
	}

	/** True if a recipe that prevents the crash exists for this resource. */
	public boolean produces(String resource) {
		return produced.contains(key(resource));
	}

	/** The resources in a mod's version folder that nothing produces, in name order. */
	public List<String> unproducedIn(Path modVersionFolder) throws IOException {
		List<String> missing = new ArrayList<>();
		for (Path file : txtFiles(modVersionFolder.resolve("assets").resolve("init").resolve("resource"))) {
			if (!produces(stem(file))) {
				missing.add(stem(file));
			}
		}
		return missing;
	}

	/** What to tell the user about resources that nothing produces, or null if there are none. */
	public static String warning(List<String> resources) {
		if (resources.isEmpty()) {
			return null;
		}
		String names = String.join(", ", resources);
		return "Nothing in the game or this mod produces " + names + ", so starting a new game will crash until something does."
				+ " It needs a room that makes it: an OUT entry in a workshop's, refiner's or farm's INDUSTRY, or a mine (NPCRes.java:70)."
				+ " An animal's RESOURCES doesn't count. If another enabled mod produces it, this doesn't apply.";
	}
}
