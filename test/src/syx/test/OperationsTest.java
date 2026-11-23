package syx.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import syx.core.AgentSetup;
import syx.core.HandbookStore;
import syx.core.JsonText;
import syx.core.Operations;
import syx.core.Operations.Outcome;
import syx.core.ToolkitSettings;
import syx.mcp.Server;

/**
 * Everything a person can do, done by an agent from start to finish, and what must never happen. A new
 * feature in the app has to be added to {@link #FEATURES}, which is how it's stated how agents reach it.
 */
public final class OperationsTest {

	/** Each thing the app lets a person do, and the operations that give an agent the same. */
	private static final Map<String, List<String>> FEATURES = Map.ofEntries(
			Map.entry("Find or choose the game folder", List.of("status", "find_game", "set_game_folder")),
			// Agents only, by the user's decision on 2026-11-06: a player asks their agent to copy or compare a vanilla asset.
			Map.entry("Copy values from a vanilla asset", List.of("read_vanilla_asset")),
			Map.entry("Copy a vanilla asset's sprites", List.of("copy_vanilla_sprites")),
			Map.entry("See the mod list", List.of("list_mods")),
			Map.entry("Create a mod", List.of("create_mod")),
			Map.entry("Edit _Info.txt", List.of("get_mod_info", "set_mod_info")),
			Map.entry("See a mod's assets", List.of("list_assets")),
			Map.entry("See Home's warnings", List.of("mod_warnings")),
			Map.entry("Check syntax", List.of("check_syntax")),
			Map.entry("Choose a type and name", List.of("list_asset_types", "check_asset_name")),
			Map.entry("Read the handbook's words on a tag", List.of("handbook_locations", "handbook_tags", "handbook_tag", "refresh_handbook")),
			Map.entry("See a new asset's form", List.of("new_asset_form", "list_options")),
			Map.entry("Open an existing asset", List.of("open_asset")),
			Map.entry("See validation as values change", List.of("check_asset")),
			Map.entry("Save a new asset", List.of("create_asset")),
			Map.entry("Save an edit, or generate missing files", List.of("save_asset")),
			Map.entry("Check a mod against a game update", List.of("list_manifests", "generate_manifest", "diff_mod", "export_diff_report")),
			Map.entry("Convert audio to ogg", List.of("list_audio_files", "convert_audio")),
			Map.entry("Generate and remove normal maps", List.of("list_sprite_sheets", "preview_sprite_sheet", "list_normal_sections", "preview_normals", "generate_normals", "trim_normals")),
			Map.entry("Race Portrait Sim", List.of("list_portrait_races", "open_portrait", "preview_portrait", "edit_portrait_text", "save_portrait", "copy_portrait_to_mod")),
			Map.entry("Asset Creation Wizard", List.of("list_wizards", "wizard_question", "wizard_start", "wizard_answer", "wizard_skip", "wizard_back", "wizard_forget")),
			Map.entry("Portrait Design Wizard", List.of("preview_face_sheet")),
			Map.entry("Map Sprite Sim", List.of("list_portrait_races", "open_map_sprite", "preview_map_sprite", "edit_portrait_text", "save_map_sprite", "copy_portrait_to_mod")),
			Map.entry("Be told how to work", List.of("agent_guide")));
	private static final Set<String> WRITES = Set.of("set_game_folder", "create_mod", "set_mod_info", "create_asset", "save_asset", "generate_manifest", "export_diff_report", "convert_audio", "copy_vanilla_sprites", "save_portrait", "copy_portrait_to_mod", "save_map_sprite", "generate_normals", "trim_normals",
			"wizard_start", "wizard_answer", "wizard_skip", "wizard_back", "wizard_forget");

	private static int passed;
	private static int failed;
	private static Path game;
	private static Path mods;
	private static Path temp;
	private static Operations ops;
	private static Operations readOnly;

	private OperationsTest() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			System.out.println("SKIPPED: no game folder given.");
			System.out.println("passed=0 failed=0");
			return;
		}
		game = Paths.get(args[0]);
		temp = Files.createTempDirectory("syx-operations");
		mods = temp.resolve("mods");
		Files.createDirectories(mods);
		ToolkitSettings settings = new ToolkitSettings(temp.resolve("settings.properties"));
		settings.setCurrentMod("Whatever The App Has Open");
		ops = new Operations(() -> game, mods, settings, handbookStore(), false);
		readOnly = new Operations(() -> game, mods, settings, handbookStore(), true);
		try {
			registry();
			setup(settings);
			wholeJourney();
			refusals();
			safety();
			readOnlyMode();
			handbook();
			json();
			mcp();
			connecting();
			audioConversion();
			normalMaps();
		} finally {
			deleteFolder(temp);
		}
		System.out.println("passed=" + passed + " failed=" + failed);
		System.exit(failed == 0 ? 0 : 1);
	}

	/** The player's real copy, if there is one, is read and never changed: this store's folder is the test's own. */
	private static HandbookStore handbookStore() {
		byte[] copy = realHandbook();
		return new HandbookStore(temp.resolve("handbook"), etag -> copy == null ? new HandbookStore.Response(404, null, new byte[0]) : new HandbookStore.Response(200, "\"test\"", copy));
	}

	private static Operations withStore(String folder, HandbookStore.Fetcher fetcher) {
		return new Operations(() -> game, mods, new ToolkitSettings(temp.resolve(folder + ".properties")), new HandbookStore(temp.resolve(folder), fetcher), false);
	}

	private static byte[] realHandbook() {
		try {
			Path real = ToolkitSettings.defaultFile().getParent().resolve("handbook").resolve("tags.json");
			return Files.isRegularFile(real) ? Files.readAllBytes(real) : null;
		} catch (IOException e) {
			return null;
		}
	}

	private static Map<String, Object> in(Object... pairs) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], pairs[i + 1]);
		}
		return map;
	}

	private static Map<String, Object> ok(String name, Object... pairs) {
		Operations.Result result = ops.call(name, in(pairs));
		expect(name + " works: " + (result.outcome() == Outcome.OK ? "" : result.payload()), result.outcome() == Outcome.OK);
		return result.payload();
	}

	/**
	 * A player can ask their agent to copy a vanilla asset. Its values come from the player's own install on each
	 * call, in open_asset's tag paths, so they can be given straight to create_asset.
	 */
	@SuppressWarnings("unchecked")
	private static void vanillaAssets(String mod) throws IOException {
		long copiesBefore = vanillaCopies();
		List<String> farms = (List<String>) ok("read_vanilla_asset", "mod", mod, "type", "Farm").get("names");
		expect("the game's farms are listed without their prefix: " + farms, farms != null && farms.contains("GRAIN") && farms.stream().noneMatch(name -> name.startsWith("FARM_")));
		String unknown = refused("a vanilla asset that isn't there", "read_vanilla_asset", "mod", mod, "type", "Farm", "name", "NOPE");
		expect("and the refusal lists them: " + unknown, unknown.contains("GRAIN"));
		refused("a name is a name, not a path", "read_vanilla_asset", "mod", mod, "type", "Farm", "name", "../GRAIN");

		Map<String, Object> grain = ok("read_vanilla_asset", "mod", mod, "type", "Farm", "name", "GRAIN");
		Map<String, String> values = (Map<String, String>) grain.get("values");
		expect("the grain farm's data values are read: " + values, "GRAIN".equals(values.get("GROWABLE")) && "74_119_14".equals(values.get("MINI_COLOR")) && "false".equals(values.get("INDOORS")));
		expect("and its text, by the same paths as open_asset", "Soil".equals(values.get("STATS.0.NAME")));
		expect("and its sounds", values.keySet().stream().anyMatch(key -> key.startsWith("SOUND.")));
		expect("it says which game files were read: " + grain.get("read_from"), ((List<String>) grain.get("read_from")).equals(List.of("init/room/FARM_GRAIN.txt", "text/room/FARM_GRAIN.txt")));
		Map<String, String> under = (Map<String, String>) ok("read_vanilla_asset", "mod", mod, "type", "Farm", "name", "GRAIN", "under", "WORK").get("values");
		expect("under narrows it: " + under.keySet(), !under.isEmpty() && under.keySet().stream().allMatch(key -> key.startsWith("WORK")));

		// Copying it: the values go straight into create_asset, and the new farm reads back the same.
		Map<String, Object> check = ok("check_asset", "mod", mod, "type", "Farm", "name", "GRAINCOPY", "values", values);
		expect("a copy of the grain farm can be saved: " + check, Boolean.TRUE.equals(check.get("can_save")));
		if (Boolean.TRUE.equals(check.get("can_save"))) {
			ok("create_asset", "mod", mod, "type", "Farm", "name", "GRAINCOPY", "values", values);
			Map<String, String> copy = (Map<String, String>) ok("open_asset", "mod", mod, "type", "Farm", "name", "GRAINCOPY").get("values");
			Map<String, String> differs = new java.util.TreeMap<>();
			values.forEach((key, value) -> {
				if (!value.equals(copy.get(key))) {
					differs.put(key, value + " became " + copy.get(key));
				}
			});
			expect("the copy has every vanilla value: " + differs, differs.isEmpty());
		}

		Map<String, Object> human = ok("read_vanilla_asset", "mod", mod, "type", "Race", "name", "HUMAN");
		Map<String, String> humanValues = (Map<String, String>) human.get("values");
		expect("a race's own lines are read from the top of its text file", "Human".equals(humanValues.get("POSSESSIVE")));
		expect("and its companion files", humanValues.keySet().stream().anyMatch(key -> key.startsWith("file.home.")) && ((List<String>) human.get("read_from")).contains("init/race/home/HUMAN.txt"));
		expect("a home overwrite's colour block is a value", humanValues.entrySet().stream().anyMatch(value -> value.getKey().endsWith(".OVERWRITE.0.COLOR") && value.getValue().equals("{R: 10, G: 150, B: 10,}")));
		Map<String, Object> cretonian = ok("read_vanilla_asset", "mod", mod, "type", "Race", "name", "CRETONIAN");
		expect("what the form can't show is given as written", ((Map<String, String>) cretonian.get("kept_as_written")).values().stream().anyMatch(value -> value.contains("GENERATE")));
		expect("and a race is told to use its appearance", String.valueOf(human.get("how_to_use")).contains("appearance HUMAN"));
		// for_name gives what the app's Initialise puts on that asset's form.
		Map<String, Object> forFarm = ok("read_vanilla_asset", "mod", mod, "type", "Farm", "name", "GRAIN", "for_name", "NEWGRAIN");
		expect("a farm's form values are its vanilla values", values.equals(forFarm.get("form_values")) && Boolean.FALSE.equals(forFarm.get("for_existing_asset")));
		Map<String, Object> forFolk = ok("read_vanilla_asset", "mod", mod, "type", "Race", "name", "HUMAN", "for_name", "OPSFOLK");
		Map<String, String> folkValues = (Map<String, String>) forFolk.get("form_values");
		expect("a race's are named after the asset, and copied from the look: " + forFolk.get("appearance"), "OPSFOLK".equals(folkValues.get("BIO_FILE")) && "Human".equals(folkValues.get("POSSESSIVE")) && "HUMAN".equals(forFolk.get("appearance")));
		expect("no copy of a vanilla file is left behind", vanillaCopies() == copiesBefore);

		Map<String, Object> beasts = ok("copy_vanilla_sprites", "mod", mod, "type", "Animal", "name", "TEST_BEAST", "vanilla", "AUROCH");
		List<String> copied = (List<String>) beasts.get("copied");
		expect("an animal's own sheet is copied: " + copied, copied.stream().anyMatch(file -> file.endsWith("sprite/animal/Test_beast.png")));
		expect("and the field names it", "Test_beast".equals(((Map<String, String>) beasts.get("values")).get("SPRITE")));
		String mine = refused("a mine's frames are shared sheets", "copy_vanilla_sprites", "mod", mod, "type", "Mine", "name", "NEWSTONE", "vanilla", "STONE");
		expect("so nothing is copied: " + mine, mine.contains("no sprites named"));
	}

	/** The temporary copies the game's parser reads vanilla files from, which must be gone after each read. */
	private static long vanillaCopies() throws IOException {
		try (java.util.stream.Stream<Path> files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
			return files.filter(file -> file.getFileName().toString().startsWith("syx-vanilla")).count();
		}
	}

	private static String refused(String why, String name, Object... pairs) {
		Operations.Result result = ops.call(name, in(pairs));
		expect(why + ": " + result.outcome() + " " + result.payload(), result.outcome() == Outcome.REFUSED && result.payload().get("refused") != null);
		return String.valueOf(result.payload().get("refused"));
	}

	private static void registry() {
		Set<String> offered = new TreeSet<>();
		ops.all().forEach(operation -> offered.add(operation.name()));
		Set<String> covered = new TreeSet<>();
		FEATURES.values().forEach(covered::addAll);
		expect("every operation belongs to a feature, and every feature has its operations: " + offered + " and " + covered, offered.equals(covered));
		Set<String> writing = new TreeSet<>();
		ops.all().stream().filter(Operations.Operation::writes).forEach(operation -> writing.add(operation.name()));
		expect("the operations that write are exactly these: " + writing, writing.equals(new TreeSet<>(WRITES)));
		expect("only a download reaches outside", ops.all().stream().filter(Operations.Operation::network).map(Operations.Operation::name).toList().equals(List.of("status", "refresh_handbook")));
		for (Operations.Operation operation : ops.all()) {
			expect(operation.name() + " is described", !operation.description().isBlank() && !operation.title().isBlank());
			expect(operation.name() + " has a schema that's closed", Boolean.FALSE.equals(operation.inputSchema().get("additionalProperties")));
		}
	}

	private static void setup(ToolkitSettings settings) throws Exception {
		Map<String, Object> status = ok("status");
		expect("status finds the game: " + status, status.get("game_problem") == null && status.get("game_version") != null);
		expect("and says what the app has open, without being tied to it", "Whatever The App Has Open".equals(status.get("current_mod_in_app")));
		ToolkitSettings fresh = new ToolkitSettings(temp.resolve("second.properties"));
		Operations noGame = new Operations(() -> fresh.gameFolder().orElse(null), mods, fresh, handbookStore(), false);
		Operations.Result without = noGame.call("list_mods", Map.of());
		expect("with no game, a call says so: " + without.payload(), without.outcome() == Outcome.REFUSED && String.valueOf(without.payload().get("refused")).contains("set_game_folder"));
		expect("a folder with no game in it is refused", noGame.call("set_game_folder", in("path", temp.toString())).outcome() == Outcome.REFUSED);
		expect("nothing is saved for it", fresh.gameFolder().isEmpty());
		expect("the real one is saved", noGame.call("set_game_folder", in("path", game.toString())).outcome() == Outcome.OK && game.equals(fresh.gameFolder().orElse(null)));
		expect("find_game reports what it looked at", ok("find_game").containsKey("checked"));
		expect("the guide says to start with status", String.valueOf(ok("agent_guide").get("guide")).contains("status"));
		expect("the guide warns that checks aren't a game run", String.valueOf(ok("agent_guide").get("guide")).contains("Only running it does"));
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> findings(Map<String, Object> check) {
		return (List<Map<String, Object>>) check.get("findings");
	}

	private static boolean mentions(Map<String, Object> check, String tag) {
		return findings(check).stream().anyMatch(finding -> tag.equals(finding.get("tag")));
	}

	/** What the user asked for: a mod with a resource, a deposit and a mine, filled in, corrected, reopened and edited. */
	@SuppressWarnings("unchecked")
	private static void wholeJourney() throws Exception {
		ok("create_mod", "name", "Agent Mod");
		String mod = "Agent Mod";
		List<Map<String, Object>> listed = (List<Map<String, Object>>) ok("list_mods").get("mods");
		expect("the new mod is listed", listed.stream().anyMatch(entry -> mod.equals(entry.get("folder"))));
		refused("a mod isn't overwritten", "create_mod", "name", "Agent Mod");

		ok("set_mod_info", "mod", mod, "values", in("AUTHOR", "An Agent"));
		List<Map<String, Object>> fields = (List<Map<String, Object>>) ok("get_mod_info", "mod", mod).get("fields");
		expect("_Info.txt changed", fields.stream().anyMatch(field -> "AUTHOR".equals(field.get("key")) && "An Agent".equals(field.get("value"))));
		refused("NAME can't be removed", "set_mod_info", "mod", mod, "remove", List.of("NAME"));
		refused("an unknown field is refused", "set_mod_info", "mod", mod, "values", in("NOPE", "1"));

		List<Map<String, Object>> types = (List<Map<String, Object>>) ok("list_asset_types").get("types");
		expect("Mine can be used", types.stream().anyMatch(type -> "Mine".equals(type.get("type")) && Boolean.TRUE.equals(type.get("available"))));
		expect("Disease gives its reason", types.stream().anyMatch(type -> "Disease".equals(type.get("type")) && Boolean.FALSE.equals(type.get("available")) && type.get("why_not") != null));
		expect("agents see the room types' group", types.stream().filter(type -> "Rooms".equals(type.get("group"))).count() == 32 && types.stream().anyMatch(type -> "Fishery".equals(type.get("type")) && "Rooms".equals(type.get("group"))));
		refused("a type that's off can't be used", "new_asset_form", "mod", mod, "type", "Disease", "name", "X");
		String noLook = refused("a race needs an appearance", "new_asset_form", "mod", mod, "type", "Race", "name", "NEWFOLK");
		expect("and the refusal lists them: " + noLook, noLook.contains("HUMAN") && noLook.contains("ARGONOSH"));
		String wrongLook = refused("an appearance is written as listed", "new_asset_form", "mod", mod, "type", "Race", "name", "NEWFOLK", "appearance", "Human");
		expect("and that refusal names it and lists them: " + wrongLook, wrongLook.contains("Human isn't an appearance") && wrongLook.contains("HUMAN"));
		Map<String, Object> raceForm = ok("new_asset_form", "mod", mod, "type", "Race", "name", "NEWFOLK", "appearance", "ARGONOSH");
		List<Map<String, Object>> colorRows = (List<Map<String, Object>>) ok("new_asset_form", "mod", mod, "type", "Race", "name", "NEWFOLK", "appearance", "ARGONOSH", "under", "file.sprite.COLORS").get("tags");
		List<Object> colorKeys = colorRows.stream().map(row -> row.get("key")).toList();
		expect("an agent is told the tags inside a keyed block: " + colorKeys, colorKeys.contains("file.sprite.COLORS.#.VALUES") && colorKeys.contains("file.sprite.COLORS.#.TURNS_SICKLY"));
		Map<String, String> keptLook = (Map<String, String>) ok("new_asset_form", "mod", mod, "type", "Race", "name", "NEWFOLK", "appearance", "CRETONIAN").get("kept_as_written");
		expect("an agent gets a look's kept values: " + keptLook, keptLook != null && keptLook.entrySet().stream().anyMatch(kept -> kept.getKey().startsWith("file.sprite.COLORS.") && kept.getValue().contains("GENERATE")));
		expect("the form lists the vanilla looks",((List<?>) raceForm.get("appearances")).contains("HUMAN") && ((List<?>) raceForm.get("appearances")).contains("ARGONOSH"));
		Map<String, String> raceStart = (Map<String, String>) raceForm.get("starting_values");
		expect("a new race starts from that look, under its own name", "NEWFOLK".equals(raceStart.get("SPRITE_FILE")));
		expect("a new race generates type 1 first names", "NEWFOLK_T1_FIRST".equals(raceStart.get("file.sprite.TYPES.1.NAMESET_FILE_FIRST")) && raceStart.get("file.names1First.NAMES") != null && !raceStart.get("file.names1First.NAMES").isBlank());
		expect("and type 1 surnames", "NEWFOLK_T1_SURNAME".equals(raceStart.get("file.sprite.TYPES.1.NAMESET_FILE_SURNAME")) && raceStart.get("file.names1Surname.NAMES") != null && !raceStart.get("file.names1Surname.NAMES").isBlank());
		expect("and the later types", raceStart.containsKey("file.names2First.NAMES") && raceStart.containsKey("file.names2Surname.NAMES"));
		expect("but no fourth type, which the game refuses (Json.java:228)", raceStart.keySet().stream().noneMatch(key -> key.startsWith("file.sprite.TYPES.3.") || key.startsWith("file.names3")));
		List<?> raceTags = (List<?>) raceForm.get("tags");
		expect("a folded map's heading reaches agents", raceTags.stream().anyMatch(tag -> tag instanceof Map<?, ?> row && "PREFERRED.ROAD".equals(row.get("key")) && "Road preference".equals(row.get("heading"))));
		expect("and a specific biography", "NEWFOLK".equals(raceStart.get("BIO_FILE_SPECIFIC")) && raceStart.keySet().stream().anyMatch(key -> key.startsWith("file.bioSpecific.")));
		List<Map<String, Object>> addable = (List<Map<String, Object>>) raceForm.get("can_add_files");
		expect("only a file with nothing to copy stays an offer: " + addable, addable != null && addable.stream().allMatch(file -> "tourist".equals(file.get("id"))));
		expect("those generated files don't block saving: " + raceForm.get("check"), Boolean.TRUE.equals(((Map<String, Object>) raceForm.get("check")).get("can_save")));
		refused("a new race is a copy of a look, so creating one needs it", "create_asset", "mod", mod, "type", "Race", "name", "NEWFOLK", "values", raceStart);
		refused("and so does checking one", "check_asset", "mod", mod, "type", "Race", "name", "NEWFOLK", "values", raceStart);
		List<String> createdRace = (List<String>) ok("create_asset", "mod", mod, "type", "Race", "name", "NEWFOLK", "appearance", "ARGONOSH", "use_starting_values", true).get("created");
		expect("the type 1 name files were written: " + createdRace, createdRace.stream().anyMatch(file -> file.endsWith("nameset/NEWFOLK_T1_FIRST.txt")) && createdRace.stream().anyMatch(file -> file.endsWith("nameset/NEWFOLK_T1_SURNAME.txt")) && createdRace.stream().anyMatch(file -> file.endsWith("bio/specific/NEWFOLK.txt")));
		// Q_AMEVIA's HAIR makes its colours from a FROM, TO and GENERATE block, which the form keeps as written. Its addons name HAIR.
		Map<String, Object> ameviaForm = ok("new_asset_form", "mod", mod, "type", "Race", "name", "AMEFOLK", "appearance", "Q_AMEVIA");
		expect("a look's kept colours don't block a new race: " + ameviaForm.get("check"), Boolean.TRUE.equals(((Map<String, Object>) ameviaForm.get("check")).get("can_save")));
		List<String> createdAmevia = (List<String>) ok("create_asset", "mod", mod, "type", "Race", "name", "AMEFOLK", "appearance", "Q_AMEVIA", "use_starting_values", true).get("created");
		String ameviaSprite = createdAmevia.stream().filter(file -> file.endsWith("init/race/sprite/AMEFOLK.txt")).findFirst().orElse(null);
		expect("the new race's appearance was written: " + createdAmevia, ameviaSprite != null);
		if (ameviaSprite != null) {
			String written = Files.readString(mods.resolve(mod).resolve(ameviaSprite));
			expect("and it keeps the look's generated colours", written.contains("GENERATE") && written.contains("HAIR"));
		}
		// The game reads POSSESSIVE from the top of a race's text file, never from INFO (RaceInfo.java:49). An agent opening one written the old way is told.
		String raceText = createdRace.stream().filter(file -> file.endsWith("text/race/NEWFOLK.txt")).findFirst().orElse(null);
		expect("the new race's text file was written: " + createdRace, raceText != null);
		if (raceText != null) {
			Path textFile = mods.resolve(mod).resolve(raceText);
			String text = Files.readString(textFile);
			java.util.regex.Matcher line = java.util.regex.Pattern.compile("(?m)^POSSESSIVE: [^\\n]*\\n").matcher(text);
			expect("POSSESSIVE is written at the top of the text file", line.find());
			if (line.find(0)) {
				String moved = text.substring(0, line.start()) + text.substring(line.end());
				Files.writeString(textFile, moved.replaceFirst("INFO: \\{\\n", java.util.regex.Matcher.quoteReplacement("INFO: {\n\t" + line.group())));
				Map<String, Object> misplaced = (Map<String, Object>) ok("open_asset", "mod", mod, "type", "Race", "name", "NEWFOLK").get("check");
				List<Map<String, Object>> found = (List<Map<String, Object>>) misplaced.get("findings");
				expect("an agent is told POSSESSIVE inside INFO isn't read: " + found, found.stream().anyMatch(finding -> "POSSESSIVE".equals(finding.get("tag"))
						&& "ERROR".equals(String.valueOf(finding.get("severity"))) && String.valueOf(finding.get("message")).contains("INFO block")));
				Files.writeString(textFile, text);
			}
		}
		vanillaAssets(mod);
		List<String> pools = (List<String>) ok("list_options", "mod", mod, "type", "Race", "tag", "PREFERRED.POOL").get("options");
		expect("a pool list is only pool rooms: " + pools, !pools.isEmpty() && pools.stream().allMatch(name -> name.startsWith("POOL_")) && pools.stream().noneMatch(name -> name.startsWith("FARM_")));
		// A tag naming one of the race's files is chosen from that file's folder, with names exactly as written.
		List<String> homes = (List<String>) ok("list_options", "mod", mod, "type", "Race", "tag", "HOME").get("options");
		expect("a race's HOME lists the home files: " + homes, homes.contains("HUMAN") && homes.contains("ARGONOSH") && homes.stream().noneMatch(name -> name.endsWith(".txt")));
		List<String> tourists = (List<String>) ok("list_options", "mod", mod, "type", "Race", "tag", "TOURIST.TOURIST_TEXT_FILE").get("options");
		expect("a nested file name lists its folder, without notes starting with _: " + tourists, tourists.contains("NORMAL") && tourists.stream().noneMatch(name -> name.startsWith("_")));
		List<String> firstNames = (List<String>) ok("list_options", "mod", mod, "type", "Race", "tag", "file.sprite.TYPES.#.NAMESET_FILE_FIRST").get("options");
		expect("a name file inside a list lists the name sets: " + firstNames, firstNames.contains("StdFirst"));
		List<String> walls = (List<String>) ok("list_options", "mod", mod, "type", "Race", "tag", "file.sprite.WORLD.WALL").get("options");
		expect("a world wall is chosen from the wall sheets: " + walls, !walls.isEmpty());
		Object specific = ok("list_options", "mod", mod, "type", "Race", "tag", "BIO_FILE_SPECIFIC").get("options");
		expect("a specific biography lists its own folder, not the biographies: " + specific, specific instanceof List<?> list && list.contains("Human") && !list.contains("Normal"));

		Map<String, Object> nameCheck = ok("check_asset_name", "mod", mod, "type", "Mine", "name", "STONE");
		expect("a vanilla name is reported: " + nameCheck, nameCheck.get("problem") != null && Boolean.TRUE.equals(nameCheck.get("has_errors")));
		nameCheck = ok("check_asset_name", "mod", mod, "type", "Mine", "name", "DEEP");
		expect("a new name gets its destination: " + nameCheck, String.valueOf(nameCheck.get("destination")).endsWith("assets/init/room/MINE_DEEP.txt"));

		// A resource, which warns until something produces it.
		ok("create_asset", "mod", mod, "type", "Resource", "name", "OWNORE", "values", resourceValues());
		List<String> warnings = (List<String>) ok("mod_warnings", "mod", mod).get("warnings");
		expect("Home's warning about it reaches the agent: " + warnings, warnings.stream().anyMatch(warning -> warning.contains("OWNORE")));

		// Its deposit, with a mistake to correct.
		Map<String, Object> form = ok("new_asset_form", "mod", mod, "type", "Minable resource", "name", "DEEP");
		expect("a form says what's needed", ((List<Map<String, Object>>) form.get("tags")).stream().anyMatch(tag -> "FERTILITY_INCREASE".equals(tag.get("key")) && Boolean.TRUE.equals(tag.get("required")) && "A number, such as 0.5".equals(tag.get("format"))));
		expect("and what has a default, ghosted", ((List<Map<String, Object>>) form.get("tags")).stream().anyMatch(tag -> "OCCURENCE".equals(tag.get("key")) && "GHOSTED".equals(String.valueOf(tag.get("state"))) && "1".equals(tag.get("default"))));
		Map<String, Object> deposit = in("RESOURCE", "OWNORE", "ON_EVERY_MAP", "true", "COLOR", "255_255_255", "MINIMAP_COLOR", "10_20_30", "FERTILITY_INCREASE", "2", "SPRITE", "Clay");
		Map<String, Object> checked = ok("check_asset", "mod", mod, "type", "Minable resource", "name", "DEEP", "values", deposit);
		expect("check reports the bad value: " + checked, mentions(checked, "FERTILITY_INCREASE") && Boolean.FALSE.equals(checked.get("can_save")));
		expect("and a warning that doesn't block", findings(checked).stream().anyMatch(finding -> "TERRAIN".equals(finding.get("tag")) && Boolean.FALSE.equals(finding.get("blocks_saving"))));
		refused("create refuses it too", "create_asset", "mod", mod, "type", "Minable resource", "name", "DEEP", "values", deposit);
		deposit.put("FERTILITY_INCREASE", -0.5);
		deposit.put("TERRAIN.MOUNTAIN", "0.5");
		checked = ok("check_asset", "mod", mod, "type", "Minable resource", "name", "DEEP", "values", deposit);
		expect("a number works as a value, and nothing's left to fix: " + checked, findings(checked).isEmpty() && Boolean.TRUE.equals(checked.get("can_save")));
		ok("create_asset", "mod", mod, "type", "Minable resource", "name", "DEEP", "values", deposit);

		// The mine, with the form's starting values.
		Map<String, Object> mineForm = ok("new_asset_form", "mod", mod, "type", "Mine", "name", "DEEP", "under", "SPRITES.WORK_1X1");
		expect("a large form can be narrowed: " + ((List<?>) mineForm.get("tags")).size(), ((List<?>) mineForm.get("tags")).size() < 15);
		expect("with its starting values too", ((Map<?, ?>) mineForm.get("starting_values")).keySet().stream().allMatch(key -> String.valueOf(key).startsWith("SPRITES.WORK_1X1")));
		Map<String, Object> full = ok("new_asset_form", "mod", mod, "type", "Mine", "name", "DEEP", "under", "MINABLE", "detail", "full");
		Map<String, Object> minable = ((List<Map<String, Object>>) full.get("tags")).get(0);
		expect("full detail has the evidence and options: " + minable, minable.get("evidence") != null && ((List<String>) minable.get("options")).contains("DEEP"));
		List<String> floors = (List<String>) ok("list_options", "mod", mod, "type", "Mine", "tag", "FLOOR").get("options");
		expect("what a tag can name is listed", floors.contains("MINE"));
		Map<String, Object> frames = ok("list_options", "mod", mod, "type", "Mine", "tag", "SPRITES.WORK_1X1.#.FRAMES");
		expect("a sprite's sheets are listed, with their rows", ((List<String>) frames.get("options")).contains("STORAGE") && ((Map<?, ?>) frames.get("rows")).containsKey("STORAGE"));
		// A face sheet's frames count 4 to a 60 pixel row (RaceFrameMaker.java:139-146): vanilla's Hair: 61 is on Hair, 960 tall.
		Map<String, Object> faces = ok("list_options", "mod", mod, "type", "Race", "tag", "file.sprite.TYPES.#.FACE.#.FRAMES");
		expect("a face sheet counts its frames, not its rows: " + ((Map<?, ?>) faces.get("rows")).get("Hair"), Integer.valueOf(64).equals(((Map<?, ?>) faces.get("rows")).get("Hair")));
		expect("sounds can be listed", ((List<String>) ok("list_options", "mod", mod, "type", "Mine", "tag", "SOUND.ROOM_WORK_").get("options")).contains("impact->Mine*"));

		Map<String, Object> mine = in("ICON", "24->resource->Gem->0", "MINABLE", "DEEP", "FLOOR", "MINE", "YEILD_WORKER_DAILY", "2", "STORAGE", "100", "RESOURCES", "STONE", "AREA_COSTS", "0", "MINI_COLOR", "80_80_80", "WORK.SHIFT_OFFSET", "0.125",
				"ITEMS.0.COSTS", "1", "ITEMS.0.STATS", "0,0,0,0", "ITEMS.1.COSTS", "1", "ITEMS.1.STATS", "0,0,2,0", "NAME", "Deep Mine", "DESC", "A mine.", "WORK.TITLE", "Miner", "WORK.VERB", "Mining");
		for (int i = 0; i < 2; i++) {
			mine.put("STATS." + i + ".NAME", "Stat");
			mine.put("STATS." + i + ".DESC", "A stat.");
			mine.put("ITEMS." + i + ".NAME", "Item");
			mine.put("ITEMS." + i + ".DESC", "An item.");
		}
		Map<String, Object> created = ok("create_asset", "mod", mod, "type", "Mine", "name", "DEEP", "values", mine, "use_starting_values", true);
		expect("the mine and its other files are written: " + created.get("created"), ((List<String>) created.get("created")).stream().anyMatch(file -> file.endsWith("init/room/MINE_DEEP.txt")) && ((List<String>) created.get("created")).stream().anyMatch(file -> file.endsWith("text/room/MINE_DEEP.txt")) && ((List<String>) created.get("created")).stream().anyMatch(file -> file.endsWith("audio/config/mono/MINE_DEEP.txt")));
		warnings = (List<String>) ok("mod_warnings", "mod", mod).get("warnings");
		expect("the warning clears now something produces it: " + warnings, warnings.stream().noneMatch(warning -> warning.contains("OWNORE")));
		refused("an asset isn't overwritten", "create_asset", "mod", mod, "type", "Mine", "name", "DEEP", "values", mine, "use_starting_values", true);

		Map<String, Object> syntax = ok("check_syntax", "mod", mod);
		expect("the whole mod passes the game's parser: " + syntax, Boolean.FALSE.equals(syntax.get("has_errors")) && ((Number) syntax.get("files_checked")).intValue() > 0);

		// Reopen and edit.
		List<Map<String, Object>> versions = (List<Map<String, Object>>) ok("list_assets", "mod", mod).get("versions");
		List<Map<String, Object>> files = (List<Map<String, Object>>) versions.get(0).get("files");
		expect("the mine is listed as something that opens", files.stream().anyMatch(file -> "Mine".equals(file.get("type")) && "DEEP".equals(file.get("name"))));
		expect("a sound file says why it doesn't: " + files, files.stream().anyMatch(file -> String.valueOf(file.get("path")).contains("audio/") && file.get("cannot_open") != null));
		Map<String, Object> opened = ok("open_asset", "mod", mod, "type", "Mine", "name", "DEEP");
		expect("a saved value reads back", "2".equals(((Map<?, ?>) opened.get("values")).get("YEILD_WORKER_DAILY")));
		String revision = (String) opened.get("revision");
		Map<String, Object> saved = ok("save_asset", "mod", mod, "type", "Mine", "name", "DEEP", "revision", revision, "changes", in("YEILD_WORKER_DAILY", "3", "WORK.HEALTH_FACTOR", "0.5"));
		expect("only the data file was written: " + saved.get("written"), ((List<String>) saved.get("written")).size() == 1 && ((List<String>) saved.get("written")).get(0).endsWith("init/room/MINE_DEEP.txt"));
		opened = ok("open_asset", "mod", mod, "type", "Mine", "name", "DEEP");
		expect("the edit is there", "3".equals(((Map<?, ?>) opened.get("values")).get("YEILD_WORKER_DAILY")) && "0.5".equals(((Map<?, ?>) opened.get("values")).get("WORK.HEALTH_FACTOR")));
		saved = ok("save_asset", "mod", mod, "type", "Mine", "name", "DEEP", "revision", opened.get("revision"), "changes", in("WORK.HEALTH_FACTOR", null));
		opened = ok("open_asset", "mod", mod, "type", "Mine", "name", "DEEP");
		expect("a null removes a tag", !((Map<?, ?>) opened.get("values")).containsKey("WORK.HEALTH_FACTOR"));

		iconBlocks(mod, mine);
		refiners(mod);
		generating(mod);
		lostTags(mod);
	}

	/** What the check says about one tag, or null if it says nothing. */
	@SuppressWarnings("unchecked")
	private static String findingOn(Map<String, Object> check, String tag) {
		for (Map<String, Object> finding : (List<Map<String, Object>>) check.get("findings")) {
			if (tag.equals(finding.get("tag"))) {
				return finding.get("severity") + ": " + finding.get("message");
			}
		}
		return null;
	}

	private static Map<String, Object> refinerValues() {
		Map<String, Object> values = in("ICON", "32->REFINER->3", "STORAGE", "25", "RESOURCES", "STONE,WOOD", "AREA_COSTS", "0,0", "FLOOR", "DIRT,STONE_BRICK", "MINI_COLOR", "226_195_38", "WORK.SHIFT_OFFSET", "0.125",
				"INDUSTRIES.0.INDUSTRY.IN.ORE", "1.25", "INDUSTRIES.0.INDUSTRY.OUT.METAL.PLAYER", "0.5", "INDUSTRIES.1.INDUSTRY.IN.COAL", "1", "INDUSTRIES.1.INDUSTRY.OUT.STONE_CUT.PLAYER", "2",
				"NAME", "Own Refiner", "DESC", "A refiner.", "WORK.TITLE", "Smelter", "WORK.VERB", "Smelting");
		for (int i = 0; i < 3; i++) {
			values.put("ITEMS." + i + ".COSTS", "1,1");
			values.put("ITEMS." + i + ".STATS", "0,1,0");
			values.put("ITEMS." + i + ".NAME", "Item");
			values.put("ITEMS." + i + ".DESC", "An item.");
		}
		return values;
	}

	private static Map<String, Object> changedRefiner(String mod, Object... changes) throws Exception {
		Map<String, Object> values = refinerValues();
		for (int i = 0; i < changes.length; i += 2) {
			if (changes[i + 1] == null) {
				values.keySet().removeIf(key -> key.equals(changes[0]) || key.startsWith(changes[0] + "."));
			} else {
				values.put((String) changes[i], changes[i + 1]);
			}
		}
		return ok("check_asset", "mod", mod, "type", "Refiner", "name", "OWN", "values", values, "use_starting_values", true);
	}

	/** What a refiner has that other rooms don't: recipes, a floor list, combo sprites, colour lists and stats the game names itself. */
	@SuppressWarnings("unchecked")
	private static void refiners(String mod) throws Exception {
		Map<String, Object> form = ok("new_asset_form", "mod", mod, "type", "Refiner", "name", "OWN");
		Map<String, String> starting = (Map<String, String>) form.get("starting_values");
		expect("a refiner's sprites start from a vanilla one, colours and all: " + starting.get("SPRITES.MAIN_MACHINE_COMBO.0.COLOR"), "128_128_132,128_120_132,120_128_132,128_128_120".equals(starting.get("SPRITES.MAIN_MACHINE_COMBO.0.COLOR")));
		expect("as they do for sounds", "work->Machine*".equals(starting.get("SOUND.ROOM_WORK_")));
		Map<String, Object> stats = ((List<Map<String, Object>>) form.get("tags")).stream().filter(row -> "STATS".equals(row.get("key"))).findFirst().orElseThrow();
		expect("the form says STATS is 3 blocks with nothing to fill in: " + stats, Integer.valueOf(3).equals(stats.get("block_count")) && Integer.valueOf(0).equals(stats.get("blocks_with_tags")));

		Map<String, Object> check = changedRefiner(mod);
		expect("a refiner with two recipes is valid: " + check.get("findings"), Boolean.TRUE.equals(check.get("can_save")) && ((List<?>) check.get("findings")).isEmpty());

		Map<String, Object> noRecipes = refinerValues();
		noRecipes.keySet().removeIf(key -> key.startsWith("INDUSTRIES."));
		check = ok("check_asset", "mod", mod, "type", "Refiner", "name", "OWN", "values", noRecipes, "use_starting_values", true);
		expect("it needs a recipe: " + findingOn(check, "INDUSTRIES"), String.valueOf(findingOn(check, "INDUSTRIES")).startsWith("MISSING") && Boolean.FALSE.equals(check.get("can_save")));
		Map<String, Object> noOutput = refinerValues();
		noOutput.remove("INDUSTRIES.1.INDUSTRY.OUT.STONE_CUT.PLAYER");
		check = ok("check_asset", "mod", mod, "type", "Refiner", "name", "OWN", "values", noOutput, "use_starting_values", true);
		expect("every recipe needs an output: " + findingOn(check, "INDUSTRIES.1.INDUSTRY.OUT"), String.valueOf(findingOn(check, "INDUSTRIES.1.INDUSTRY.OUT")).startsWith("MISSING"));

		check = changedRefiner(mod, "ITEMS.1.STATS", "1,2,3,4");
		expect("an item has 3 stats: " + findingOn(check, "ITEMS.1.STATS"), String.valueOf(findingOn(check, "ITEMS.1.STATS")).contains("a refiner has 3 stats"));
		check = changedRefiner(mod, "ITEMS.2.COSTS", "1");
		expect("and a cost per resource: " + findingOn(check, "ITEMS.2.COSTS"), String.valueOf(findingOn(check, "ITEMS.2.COSTS")).startsWith("ERROR"));
		check = changedRefiner(mod, "STORAGE", "7");
		expect("the game's minimum for storage is checked: " + findingOn(check, "STORAGE"), String.valueOf(findingOn(check, "STORAGE")).startsWith("ERROR"));
		check = changedRefiner(mod, "STORAGE", "501");
		expect("its maximum only warns, because it isn't enforced: " + findingOn(check, "STORAGE"), String.valueOf(findingOn(check, "STORAGE")).startsWith("WARNING") && Boolean.TRUE.equals(check.get("can_save")));
		check = changedRefiner(mod, "FLOOR", "DIRT,NOT_A_FLOOR");
		expect("an unknown floor only warns, as in the game: " + findingOn(check, "FLOOR"), String.valueOf(findingOn(check, "FLOOR")).startsWith("WARNING") && Boolean.TRUE.equals(check.get("can_save")));
		check = changedRefiner(mod, "FLOOR", "DIRT,");
		expect("a blank floor isn't a name the game can skip: " + findingOn(check, "FLOOR"), String.valueOf(findingOn(check, "FLOOR")).startsWith("ERROR"));
		check = changedRefiner(mod, "INDUSTRIES.0.INDUSTRY.OUT.NOTHING.PLAYER", "1");
		expect("an unknown output stops the game, so it's an error", String.valueOf(findingOn(check, "INDUSTRIES.0.INDUSTRY.OUT")).startsWith("ERROR"));
		check = changedRefiner(mod, "SPRITES.MAIN_MACHINE_COMBO.0.COLOR", "1_2_3,bad");
		expect("each colour in a list is checked: " + findingOn(check, "SPRITES.MAIN_MACHINE_COMBO.0.COLOR"), String.valueOf(findingOn(check, "SPRITES.MAIN_MACHINE_COMBO.0.COLOR")).startsWith("ERROR"));
		check = changedRefiner(mod, "SPRITES.MAIN_MACHINE_COMBO.0.FRAMES", "REFINER: 9999");
		expect("a combo sheet's rows are counted: " + findingOn(check, "SPRITES.MAIN_MACHINE_COMBO.0.FRAMES"), String.valueOf(findingOn(check, "SPRITES.MAIN_MACHINE_COMBO.0.FRAMES")).startsWith("WARNING"));
		check = changedRefiner(mod, "SPRITES.NICKNACK_TOP_1X1.2.FRAMES", "");
		expect("a sprite with no frames isn't a problem, as vanilla has them: " + check.get("findings"), ((List<?>) check.get("findings")).isEmpty());
		Map<String, Object> named = refinerValues();
		named.put("STATS.0.NAME", "Workers");
		refused("the game names a refiner's stats, so there's nowhere to put one", "check_asset", "mod", mod, "type", "Refiner", "name", "OWN", "values", named, "use_starting_values", true);

		ok("create_asset", "mod", mod, "type", "Refiner", "name", "OWN", "values", refinerValues(), "use_starting_values", true);
		Path assets = mods.resolve(mod).resolve("V" + gameMajor()).resolve("assets");
		Path data = assets.resolve("init/room/REFINER_OWN.txt");
		Path text = assets.resolve("text/room/REFINER_OWN.txt");
		String written = Files.readString(data).replace("\r\n", "\n");
		expect("recipes are written in a list of blocks: " + written, written.contains("INDUSTRIES: [\n\t{\n\t\tINDUSTRY: {\n\t\t\tOUT: {\n\t\t\t\tMETAL: {\n\t\t\t\t\tPLAYER: 0.5,\n"));
		expect("a floor list is a list", written.contains("FLOOR: [DIRT, STONE_BRICK,],"));
		expect("a colour list is a list", written.contains("COLOR: [128_128_132, 128_120_132, 120_128_132, 128_128_120,],"));
		String words = Files.readString(text).replace("\r\n", "\n");
		expect("the stats' blocks are there, with nothing in them: " + words, words.contains("STATS: [\n\t{\n\t},\n\t{\n\t},\n\t{\n\t},\n],"));
		Map<String, Object> syntax = ok("check_syntax", "mod", mod);
		expect("the game's parser accepts it: " + syntax, Boolean.FALSE.equals(syntax.get("has_errors")));

		Map<String, Object> opened = ok("open_asset", "mod", mod, "type", "Refiner", "name", "OWN");
		Map<String, Object> values = (Map<String, Object>) opened.get("values");
		expect("it reads back whole: " + opened.get("kept"), ((List<?>) opened.get("kept")).isEmpty() && "2".equals(values.get("INDUSTRIES.1.INDUSTRY.OUT.STONE_CUT.PLAYER")) && "DIRT,STONE_BRICK".equals(values.get("FLOOR")));
		ok("save_asset", "mod", mod, "type", "Refiner", "name", "OWN", "revision", opened.get("revision"), "changes", in("INDUSTRIES.1.INDUSTRY.IN.COAL", "3", "SPRITES.MAIN_MACHINE_COMBO.0.COLOR", "1_2_3"));
		written = Files.readString(data);
		expect("a recipe can be edited, and a list become one colour", written.contains("COAL: 3") && written.contains("COLOR: 1_2_3"));
		opened = ok("open_asset", "mod", mod, "type", "Refiner", "name", "OWN");
		expect("then read back", "1_2_3".equals(((Map<String, Object>) opened.get("values")).get("SPRITES.MAIN_MACHINE_COMBO.0.COLOR")));

		Files.delete(data);
		Files.delete(text);
		Files.deleteIfExists(assets.resolve("audio/config/mono/REFINER_OWN.txt"));
		Files.deleteIfExists(assets.resolve("audio/config/ambience/REFINER_OWN.txt"));
	}

	/** An icon can be one path or a block of two, as on the form. */
	@SuppressWarnings("unchecked")
	private static void iconBlocks(String mod, Map<String, Object> mine) throws Exception {
		List<Map<String, Object>> rows = (List<Map<String, Object>>) ok("new_asset_form", "mod", mod, "type", "Mine", "name", "BLOCKY", "under", "ICON").get("tags");
		Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
		rows.forEach(row -> byKey.put((String) row.get("key"), row));
		expect("a form lists an icon's block tags: " + byKey.keySet(), byKey.keySet().containsAll(List.of("ICON", "ICON.BG", "ICON.FG", "ICON.SCALE", "ICON.COLOR", "ICON.OFFX", "ICON.OFFY", "ICON.SHADOW")));
		expect("saying they're only for a block", "ICON".equals(byKey.get("ICON.BG").get("only_in_block")) && !byKey.get("ICON").containsKey("only_in_block"));
		expect("with the game's defaults: " + byKey.get("ICON.SCALE"), "1".equals(byKey.get("ICON.SCALE").get("default")) && "0".equals(byKey.get("ICON.SHADOW").get("default")) && "255_255_255".equals(byKey.get("ICON.COLOR").get("default")));
		expect("and the top tag says how a block is given: " + byKey.get("ICON").get("format"), String.valueOf(byKey.get("ICON").get("format")).contains("ICON.BG"));

		for (String tag : List.of("ICON", "ICON.BG", "ICON.FG")) {
			Map<String, Object> options = ok("list_options", "mod", mod, "type", "Mine", "tag", tag);
			Map<String, String> positions = (Map<String, String>) options.get("positions");
			expect(tag + " lists sheets and their positions: " + positions, ((List<String>) options.get("options")).contains("24->resource->Gem") && String.valueOf(positions.get("24->resource->Gem")).startsWith("0"));
			expect("and says how they count", String.valueOf(options.get("message")).contains("left to right"));
		}

		Map<String, Object> plain = new LinkedHashMap<>(mine);
		plain.remove("ICON");
		Map<String, Object> block = new LinkedHashMap<>(plain);
		block.put("ICON.BG", "32->BG->4");
		block.put("ICON.FG", "24->resource->Gem->0");
		block.put("ICON.SHADOW", "2");
		Map<String, Object> check = ok("check_asset", "mod", mod, "type", "Mine", "name", "BLOCKY", "values", block, "use_starting_values", true);
		expect("a block is valid: " + check.get("findings"), Boolean.TRUE.equals(check.get("can_save")));

		Map<String, Object> both = new LinkedHashMap<>(block);
		both.put("ICON", "24->resource->Gem->0");
		check = ok("check_asset", "mod", mod, "type", "Mine", "name", "BLOCKY", "values", both, "use_starting_values", true);
		expect("both forms at once are refused: " + findingOn(check, "ICON"), String.valueOf(findingOn(check, "ICON")).contains("not both") && Boolean.FALSE.equals(check.get("can_save")));

		Map<String, Object> noBackground = new LinkedHashMap<>(block);
		noBackground.remove("ICON.BG");
		check = ok("check_asset", "mod", mod, "type", "Mine", "name", "BLOCKY", "values", noBackground, "use_starting_values", true);
		expect("a block needs BG: " + findingOn(check, "ICON.BG"), String.valueOf(findingOn(check, "ICON.BG")).startsWith("MISSING") && Boolean.FALSE.equals(check.get("can_save")));

		Map<String, Object> written = new LinkedHashMap<>(plain);
		written.put("ICON", "{ BG: 32->BG->4, FG: 24->resource->Gem->0, SHADOW: 2, }");
		check = ok("check_asset", "mod", mod, "type", "Mine", "name", "BLOCKY", "values", written, "use_starting_values", true);
		expect("a block copied as one value says what to do: " + findingOn(check, "ICON"), String.valueOf(findingOn(check, "ICON")).contains("ICON.BG"));

		Map<String, Object> small = new LinkedHashMap<>(block);
		small.put("ICON.SCALE", "0");
		check = ok("check_asset", "mod", mod, "type", "Mine", "name", "BLOCKY", "values", small, "use_starting_values", true);
		expect("the game's minimum is checked: " + findingOn(check, "ICON.SCALE"), String.valueOf(findingOn(check, "ICON.SCALE")).startsWith("ERROR"));

		Map<String, Object> colored = new LinkedHashMap<>(block);
		colored.put("ICON.COLOR", "10_20_30");
		check = ok("check_asset", "mod", mod, "type", "Mine", "name", "BLOCKY", "values", colored, "use_starting_values", true);
		expect("COLOR beside FG is pointed out: " + findingOn(check, "ICON.COLOR"), String.valueOf(findingOn(check, "ICON.COLOR")).startsWith("WARNING") && Boolean.TRUE.equals(check.get("can_save")));
		colored.remove("ICON.FG");
		check = ok("check_asset", "mod", mod, "type", "Mine", "name", "BLOCKY", "values", colored, "use_starting_values", true);
		expect("but not without one: " + findingOn(check, "ICON.COLOR"), findingOn(check, "ICON.COLOR") == null);

		Map<String, Object> far = new LinkedHashMap<>(block);
		far.put("ICON.BG", "32->BG->9999");
		check = ok("check_asset", "mod", mod, "type", "Mine", "name", "BLOCKY", "values", far, "use_starting_values", true);
		expect("a position past the end is a warning: " + findingOn(check, "ICON.BG"), String.valueOf(findingOn(check, "ICON.BG")).startsWith("WARNING"));

		for (String wrong : List.of("ICON.NOPE", "ICON.BG.INSIDE", "ICON.0.BG")) {
			Map<String, Object> odd = new LinkedHashMap<>(block);
			odd.put(wrong, "1");
			refused(wrong + " isn't a value", "check_asset", "mod", mod, "type", "Mine", "name", "BLOCKY", "values", odd, "use_starting_values", true);
		}

		ok("create_asset", "mod", mod, "type", "Mine", "name", "BLOCKY", "values", block, "use_starting_values", true);
		Path file = mods.resolve(mod).resolve("V" + gameMajor()).resolve("assets/init/room/MINE_BLOCKY.txt");
		String text = Files.readString(file).replace("\r\n", "\n");
		expect("a new block is written as one, with only what was given: " + text, text.contains("ICON: {\n\tBG: 32->BG->4,\n\tFG: 24->resource->Gem->0,\n\tSHADOW: 2,\n},\n"));
		Map<String, Object> opened = ok("open_asset", "mod", mod, "type", "Mine", "name", "BLOCKY");
		Map<String, Object> values = (Map<String, Object>) opened.get("values");
		expect("it reads back as a block: " + values.get("ICON.BG"), "32->BG->4".equals(values.get("ICON.BG")) && !values.containsKey("ICON"));
		expect("and isn't kept", !((List<String>) opened.get("kept")).contains("ICON"));

		ok("save_asset", "mod", mod, "type", "Mine", "name", "BLOCKY", "revision", opened.get("revision"), "changes", in("ICON", "24->resource->Gem->0", "ICON.BG", null, "ICON.FG", null, "ICON.SHADOW", null));
		text = Files.readString(file);
		expect("a block can become one icon: " + text, text.contains("24->resource->Gem->0") && !text.contains("32->BG->4") && !text.contains("SHADOW: 2"));
		opened = ok("open_asset", "mod", mod, "type", "Mine", "name", "BLOCKY");
		// A removal doesn't take away a new value inside it, whichever comes first.
		ok("save_asset", "mod", mod, "type", "Mine", "name", "BLOCKY", "revision", opened.get("revision"), "changes", in("ICON.BG", "32->BG->4", "ICON", null));
		opened = ok("open_asset", "mod", mod, "type", "Mine", "name", "BLOCKY");
		values = (Map<String, Object>) opened.get("values");
		expect("one icon can become a block: " + values.get("ICON.BG") + " " + values.get("ICON"), "32->BG->4".equals(values.get("ICON.BG")) && !values.containsKey("ICON"));
		Files.delete(file);
		Files.deleteIfExists(file.getParent().getParent().getParent().resolve("text/room/MINE_BLOCKY.txt"));
		Files.deleteIfExists(file.getParent().getParent().getParent().resolve("audio/config/mono/MINE_BLOCKY.txt"));
		Files.deleteIfExists(file.getParent().getParent().getParent().resolve("audio/config/ambience/MINE_BLOCKY.txt"));
	}

	private static Map<String, Object> resourceValues() {
		return in("DEGRADE_RATE", "0.5", "COLOR", "255_255_255", "MINIMAP_COLOR", "10_10_10", "CATEGORY_DEFAULT", "2", "ICON", "24->resource->Gem->0", "SPRITE", "Gem", "SPRITE_DEBRIS", "Wood", "NAME", "Own Ore", "DESC", "Ore.");
	}

	/** Files an asset lacks, which supplying their values creates, as Generate and Save do on the form. */
	@SuppressWarnings("unchecked")
	private static void generating(String mod) throws Exception {
		Path assets = mods.resolve(mod).resolve("V" + gameMajor()).resolve("assets");
		Files.delete(assets.resolve("text/room/MINE_DEEP.txt"));
		Files.delete(assets.resolve("audio/config/mono/MINE_DEEP.txt"));
		Map<String, Object> opened = ok("open_asset", "mod", mod, "type", "Mine", "name", "DEEP");
		Map<String, Object> offers = (Map<String, Object>) opened.get("can_generate");
		expect("a missing text file is offered: " + offers, String.valueOf(offers.get("text_file")).contains("no text file"));
		expect("and missing sounds", String.valueOf(offers.get("sounds")).contains("ROOM_WORK_MINE_DEEP"));
		expect("but neither blocks saving", Boolean.TRUE.equals(((Map<String, Object>) opened.get("check")).get("can_save")));

		Map<String, Object> partly = ok("check_asset", "mod", mod, "type", "Mine", "name", "DEEP", "changes", in("NAME", "Back Again"));
		expect("starting on the text file asks for the rest: " + partly.get("summary"), Boolean.FALSE.equals(partly.get("can_save")) && mentions(partly, "WORK.TITLE"));
		Map<String, Object> text = in("NAME", "Back Again", "DESC", "Again.", "WORK.TITLE", "Miner", "WORK.VERB", "Mining", "SOUND.ROOM_WORK_", "impact->Mine*", "SOUND.ROOM_CLICK_", "DUMMY");
		for (int i = 0; i < 2; i++) {
			text.put("STATS." + i + ".NAME", "Stat");
			text.put("STATS." + i + ".DESC", "A stat.");
			text.put("ITEMS." + i + ".NAME", "Item");
			text.put("ITEMS." + i + ".DESC", "An item.");
		}
		Map<String, Object> saved = ok("save_asset", "mod", mod, "type", "Mine", "name", "DEEP", "revision", opened.get("revision"), "changes", text);
		expect("both are written: " + saved.get("written"), Files.isRegularFile(assets.resolve("text/room/MINE_DEEP.txt")) && Files.readString(assets.resolve("audio/config/mono/MINE_DEEP.txt")).contains("ROOM_WORK_MINE_DEEP"));
	}

	private static String gameMajor() throws Exception {
		return String.valueOf(ops.call("status", Map.of()).payload().get("game_version")).split("\\.")[0];
	}

	/** A tag the form doesn't offer, in a block that's going away, needs naming first. */
	@SuppressWarnings("unchecked")
	private static void lostTags(String mod) throws Exception {
		Path file = mods.resolve(mod).resolve("V" + gameMajor()).resolve("assets/init/room/MINE_DEEP.txt");
		String content = Files.readString(file);
		int at = content.indexOf("WORK_1X1: [");
		int open = content.indexOf('{', at);
		Files.writeString(file, content.substring(0, open + 1) + "\nOVERWRITE: [ { FPS: 1, }, ]," + content.substring(open + 1));
		Map<String, Object> opened = ok("open_asset", "mod", mod, "type", "Mine", "name", "DEEP");
		Map<String, Object> changes = new LinkedHashMap<>();
		for (Object key : ((Map<?, ?>) opened.get("values")).keySet()) {
			if (String.valueOf(key).startsWith("SPRITES.WORK_1X1.1.")) {
				changes.put(String.valueOf(key), null);
			}
		}
		expect("check says what would be lost", ((List<String>) ok("check_asset", "mod", mod, "type", "Mine", "name", "DEEP", "changes", changes).get("would_lose")).equals(List.of("SPRITES.WORK_1X1.0.OVERWRITE")));
		byte[] before = Files.readAllBytes(file);
		String reason = refused("losing a tag has to be accepted", "save_asset", "mod", mod, "type", "Mine", "name", "DEEP", "revision", opened.get("revision"), "changes", changes);
		expect("and it's named: " + reason, reason.contains("SPRITES.WORK_1X1.0.OVERWRITE"));
		refused("naming another tag doesn't accept it", "save_asset", "mod", mod, "type", "Mine", "name", "DEEP", "revision", opened.get("revision"), "changes", changes, "accept_lost_tags", List.of("SOMETHING_ELSE"));
		expect("and the file is untouched", java.util.Arrays.equals(before, Files.readAllBytes(file)));
		ok("save_asset", "mod", mod, "type", "Mine", "name", "DEEP", "revision", opened.get("revision"), "changes", changes, "accept_lost_tags", List.of("SPRITES.WORK_1X1.0.OVERWRITE"));
		expect("named, it goes through", !Files.readString(file).contains("OVERWRITE"));
	}

	private static void refusals() throws Exception {
		String mod = "Agent Mod";
		refused("an unknown operation", "delete_everything");
		refused("an unknown input", "list_mods", "extra", "1");
		refused("a missing input", "get_mod_info");
		refused("the wrong kind of input", "get_mod_info", "mod", List.of("x"));
		refused("an unknown mod", "list_assets", "mod", "Nothing Here");
		refused("an unknown type", "new_asset_form", "mod", mod, "type", "Spaceship", "name", "X");
		refused("an asset that isn't there", "open_asset", "mod", mod, "type", "Mine", "name", "NOTHING");
		refused("a tag that doesn't exist", "check_asset", "mod", mod, "type", "Minable resource", "name", "NEWONE", "values", in("FERTILITY_INCREASEE", "1"));
		refused("a block isn't a value", "check_asset", "mod", mod, "type", "Mine", "name", "NEWONE", "values", in("WORK", "1"));
		refused("a list's tag needs an index", "check_asset", "mod", mod, "type", "Mine", "name", "NEWONE", "values", in("UPGRADES.BOOST", "1"));
		refused("a fixed list has no more blocks", "check_asset", "mod", mod, "type", "Mine", "name", "NEWONE", "values", in("ITEMS.2.COSTS", "1"));
		refused("an unknown row column", "check_asset", "mod", mod, "type", "Farm", "name", "NEWONE", "values", in("INDUSTRY.OUT.GRAIN.NOPE", "1"));
		refused("a value must be text", "check_asset", "mod", mod, "type", "Minable resource", "name", "NEWONE", "values", in("COLOR", List.of("1")));
		refused("new values for an existing asset", "check_asset", "mod", mod, "type", "Mine", "name", "DEEP", "values", in("STORAGE", "9"));
		refused("changes for an asset that isn't there", "check_asset", "mod", mod, "type", "Mine", "name", "NOPE", "changes", in("STORAGE", "9"));

		// A sound in the game's files isn't this asset's to change.
		Path assets = mods.resolve(mod).resolve("V" + gameMajor()).resolve("assets");
		Path overriding = assets.resolve("init/room/MINE_GEM.txt");
		Files.writeString(overriding, "STORAGE: 9,\n");
		String merged = refused("a file named like vanilla's can't be opened", "open_asset", "mod", mod, "type", "Mine", "name", "GEM");
		expect("and it says why: " + merged, merged.contains("merges"));
		Files.delete(overriding);
		Map<String, Object> grain = in("mod", mod, "type", "Mine", "name", "SHARED");
		Path shared = assets.resolve("audio/config/mono/Shared.txt");
		Files.writeString(shared, "ROOM_WORK_MINE_SHARED: DUMMY,\n");
		Path copy = assets.resolve("init/room/MINE_SHARED.txt");
		Files.write(copy, Files.readAllBytes(assets.resolve("init/room/MINE_DEEP.txt")));
		Map<String, Object> opened = ok("open_asset", "mod", mod, "type", "Mine", "name", "SHARED");
		grain.put("revision", opened.get("revision"));
		grain.put("changes", in("SOUND.ROOM_WORK_", "impact->Mine*"));
		expect("a sound in another file is reported as such", String.valueOf(opened.get("sounds")).contains("OTHER_MOD_FILE"));
		String fixed = String.valueOf(ops.call("save_asset", grain).payload().get("refused"));
		expect("and can't be changed from here: " + fixed, fixed.contains("Shared.txt"));
		Files.delete(copy);
		Files.delete(shared);
	}

	private static void expectRefusesPath(String name) {
		Operations.Result result = ops.call("list_assets", in("mod", name));
		expect("the mod name " + name + " is refused: " + result.payload(), result.outcome() == Outcome.REFUSED);
	}

	/** What must never happen, whoever's asking. */
	private static void safety() throws Exception {
		String mod = "Agent Mod";
		for (String name : List.of("..", "../..", "..\\..", "/", "C:\\Windows", "a/b", "a\\b", ".", "", " ")) {
			expectRefusesPath(name);
		}
		Path outside = temp.resolve("outside");
		Files.createDirectories(outside);
		for (String name : List.of("../outside", "..\\outside", outside.toString())) {
			Operations.Result result = ops.call("create_asset", in("mod", name, "type", "Minable resource", "name", "X", "values", in()));
			expect("no write can be aimed at " + name, result.outcome() == Outcome.REFUSED);
		}
		for (String name : List.of("../../../evil", "..\\evil", "a/b", "C:\\evil", "..")) {
			Operations.Result created = ops.call("create_asset", in("mod", mod, "type", "Minable resource", "name", name, "values", goodDeposit()));
			expect("an asset can't be named " + name + ": " + created.payload().get("refused"), created.outcome() == Outcome.REFUSED);
			Operations.Result opened = ops.call("open_asset", in("mod", mod, "type", "Minable resource", "name", name));
			expect("or opened as " + name + ", for that reason: " + opened.payload().get("refused"), opened.outcome() == Outcome.REFUSED && String.valueOf(opened.payload().get("refused")).contains("without a path"));
		}
		for (String name : List.of("../../outsider", "..\\outsider", "a/b", "C:\\x")) {
			expect("a mod can't be created as " + name, ops.call("create_mod", in("name", name)).outcome() == Outcome.REFUSED);
		}
		try (Stream<Path> walk = Files.list(outside)) {
			expect("nothing arrived outside", walk.findAny().isEmpty());
		}

		// A mod folder that's a link is followed, as the game follows it (VirtualFolder.listFolders, Util.listFiles),
		// unless it leads into the game install or the Workshop folder. A link inside a mod isn't followed.
		// A junction needs no privileges, so it's what's made here.
		Path project = temp.resolve("project").resolve("Linked");
		Files.createDirectories(project.resolve("V" + gameMajor()).resolve("assets"));
		Files.writeString(project.resolve("_Info.txt"), "NAME: \"Linked Project\",\n");
		Path link = mods.resolve("Linked");
		if (junction(link, project)) {
			Map<String, Object> listed = linkedEntry("Linked");
			expect("a linked mod is listed as usable: " + listed, listed != null && listed.get("cannot_open") == null && "Linked Project".equals(listed.get("name")));
			ok("list_assets", "mod", "Linked");
			ok("get_mod_info", "mod", "Linked");
			ok("create_asset", "mod", "Linked", "type", "Minable resource", "name", "THROUGH", "values", goodDeposit());
			expect("the write lands in the linked project", Files.isRegularFile(project.resolve("V" + gameMajor()).resolve("assets/init/resource/minable/THROUGH.txt")));
			removeJunction(link);

			Path workshop = temp.resolve("steamapps").resolve("workshop").resolve("content").resolve("1162750").resolve("Subscribed");
			Files.createDirectories(workshop.resolve("V" + gameMajor()).resolve("assets"));
			Files.writeString(workshop.resolve("_Info.txt"), "NAME: \"Subscribed\",\n");
			Path toWorkshop = mods.resolve("ToWorkshop");
			expect("a junction into a Workshop folder is made", junction(toWorkshop, workshop));
			long before = snapshot(workshop);
			Map<String, Object> flagged = linkedEntry("ToWorkshop");
			expect("list_mods says why it can't be used: " + flagged, flagged != null && String.valueOf(flagged.get("cannot_open")).contains("Workshop"));
			String why = refused("a mod linked into the Workshop folder is refused", "list_assets", "mod", "ToWorkshop");
			expect("with the real reason: " + why, why.contains("Workshop") && !why.contains("isn't used"));
			refused("and can't be written to", "create_asset", "mod", "ToWorkshop", "type", "Minable resource", "name", "THROUGH", "values", goodDeposit());
			expect("nothing was written there", snapshot(workshop) == before);
			removeJunction(toWorkshop);
			expect("the junction is gone, not what it pointed at", Files.isRegularFile(workshop.resolve("_Info.txt")) && Files.isRegularFile(project.resolve("_Info.txt")));
			Path version = mods.resolve(mod).resolve("V" + gameMajor()).resolve("assets/init/resource/minable");
			Path moved = version.resolveSibling("minable-moved");
			Files.move(version, moved);
			expect("a junction is made", junction(version, outside));
			Operations.Result through = ops.call("create_asset", in("mod", mod, "type", "Minable resource", "name", "THROUGH", "values", goodDeposit()));
			expect("a write through a link in a mod is refused: " + through.payload().get("refused"), through.outcome() == Outcome.REFUSED && String.valueOf(through.payload().get("refused")).contains("link"));
			removeJunction(version);
			Files.move(moved, version);
			try (Stream<Path> walk = Files.list(outside)) {
				expect("nothing went through either", walk.findAny().isEmpty());
			}
			Operations.Result after = ops.call("check_asset_name", in("mod", mod, "type", "Mine", "name", "FINE"));
			expect("an ordinary mod still works", after.outcome() == Outcome.OK);
		} else {
			System.out.println("OBSERVED junctions can't be made here, so that case wasn't run.");
		}

		// A file changed by someone else.
		Map<String, Object> opened = ok("open_asset", "mod", mod, "type", "Mine", "name", "DEEP");
		Path file = mods.resolve(mod).resolve("V" + gameMajor()).resolve("assets/init/room/MINE_DEEP.txt");
		Files.writeString(file, Files.readString(file) + "\nSOMEONE: 1,\n");
		byte[] theirs = Files.readAllBytes(file);
		String stale = refused("a stale revision is refused", "save_asset", "mod", mod, "type", "Mine", "name", "DEEP", "revision", opened.get("revision"), "changes", in("STORAGE", "7"));
		expect("and says what to do: " + stale, stale.contains("Open it again"));
		expect("their change is still there", java.util.Arrays.equals(theirs, Files.readAllBytes(file)));
		Map<String, Object> again = ok("open_asset", "mod", mod, "type", "Mine", "name", "DEEP");
		// Each of an asset's files counts, not only the data file.
		Path text = mods.resolve(mod).resolve("V" + gameMajor()).resolve("assets/text/room/MINE_DEEP.txt");
		String words = Files.readString(text);
		Files.writeString(text, words + "\nSOMEONE: 1,\n");
		refused("a text file changed since opening is refused", "save_asset", "mod", mod, "type", "Mine", "name", "DEEP", "revision", again.get("revision"), "changes", in("STORAGE", "7"));
		Files.writeString(text, words);
		Path sounds = mods.resolve(mod).resolve("V" + gameMajor()).resolve("assets/audio/config/mono/MINE_DEEP.txt");
		String entries = Files.readString(sounds);
		Files.writeString(sounds, entries + "\nSOMEONE: DUMMY,\n");
		refused("a sound file changed since opening is refused", "save_asset", "mod", mod, "type", "Mine", "name", "DEEP", "revision", again.get("revision"), "changes", in("STORAGE", "7"));
		Files.writeString(sounds, entries);
		ok("save_asset", "mod", mod, "type", "Mine", "name", "DEEP", "revision", again.get("revision"), "changes", in("STORAGE", "7"));
		expect("an unknown tag survives an edit", Files.readString(file).contains("SOMEONE"));
		try (Stream<Path> walk = Files.walk(mods)) {
			expect("no temp files are left", walk.noneMatch(path -> path.toString().endsWith(".syxtmp")));
		}
	}

	private static boolean junction(Path link, Path target) throws IOException, InterruptedException {
		Process process = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
		return process.waitFor() == 0 && Files.exists(link);
	}

	/** Removes the junction, never what it points at. */
	private static void removeJunction(Path link) throws IOException, InterruptedException {
		new ProcessBuilder("cmd", "/c", "rmdir", link.toString()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor();
	}

	private static Map<String, Object> goodDeposit() {
		return in("RESOURCE", "STONE", "ON_EVERY_MAP", "true", "COLOR", "255_255_255", "MINIMAP_COLOR", "10_20_30", "FERTILITY_INCREASE", "0", "SPRITE", "Clay", "TERRAIN.MOUNTAIN", "0.5");
	}

	/** A mod's row in list_mods, by folder name, or null. */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> linkedEntry(String folder) {
		for (Map<String, Object> mod : (List<Map<String, Object>>) ok("list_mods").get("mods")) {
			if (folder.equals(mod.get("folder"))) {
				return mod;
			}
		}
		return null;
	}

	private static long snapshot(Path folder) throws IOException {
		try (Stream<Path> walk = Files.walk(folder)) {
			long total = 0;
			for (Path path : walk.filter(Files::isRegularFile).toList()) {
				total += Files.size(path) + Files.getLastModifiedTime(path).toMillis() + 1;
			}
			return total;
		}
	}

	private static void readOnlyMode() throws Exception {
		long before = snapshot(mods);
		for (String name : WRITES) {
			Operations.Result result = readOnly.call(name, in("mod", "Agent Mod", "type", "Mine", "name", "BLOCKED", "path", game.toString(), "revision", "x", "changes", in(), "values", goodDeposit()));
			expect(name + " is refused when read-only: " + result.payload(), result.outcome() == Outcome.REFUSED && String.valueOf(result.payload().get("refused")).contains("read-only"));
			expect("and isn't listed", readOnly.all().stream().noneMatch(operation -> operation.name().equals(name)));
		}
		expect("reading still works", readOnly.call("list_mods", Map.of()).outcome() == Outcome.OK);
		expect("as does checking", readOnly.call("check_asset", in("mod", "Agent Mod", "type", "Minable resource", "name", "NEWONE", "values", goodDeposit())).outcome() == Outcome.OK);
		expect("nothing changed", before == snapshot(mods));
	}

	@SuppressWarnings("unchecked")
	private static void handbook() throws Exception {
		Operations noCopy = withStore("handbook-none", etag -> new HandbookStore.Response(404, null, new byte[0]));
		Operations.Result before = noCopy.call("handbook_locations", Map.of());
		expect("with no copy, it says so rather than inventing anything: " + before.payload(), before.outcome() == Outcome.REFUSED && String.valueOf(before.payload().get("refused")).contains("refresh_handbook"));
		expect("status says so too", String.valueOf(noCopy.call("status", Map.of()).payload().get("handbook")).contains("available=false"));
		Operations offline = withStore("handbook-offline", etag -> {
			throw new java.io.IOException("No network.");
		});
		String offlineState = String.valueOf(offline.call("status", Map.of()).payload().get("handbook"));
		expect("status still works with no network: " + offlineState, offlineState.contains("update=FAILED") && offlineState.contains("available=false"));
		byte[] real = realHandbook();
		if (real == null) {
			System.out.println("OBSERVED this machine has no handbook copy, so the rest wasn't run.");
			return;
		}
		// An agent has to notice a newer handbook on its own, because the app may not be opened.
		byte[] newer = new String(real, java.nio.charset.StandardCharsets.UTF_8).replaceFirst("\"content_hash\"\\s*:\\s*\"", "\"content_hash\": \"newer").getBytes(java.nio.charset.StandardCharsets.UTF_8);
		int[] asked = new int[1];
		Operations updating = withStore("handbook-updating", etag -> switch (asked[0]++) {
		case 0 -> new HandbookStore.Response(200, "\"first\"", real);
		case 1 -> new HandbookStore.Response(304, "\"first\"", new byte[0]);
		case 2 -> new HandbookStore.Response(200, "\"second\"", newer);
		default -> throw new java.io.IOException("No network.");
		});
		String state = String.valueOf(updating.call("status", Map.of()).payload().get("handbook"));
		expect("status fetches a first copy: " + state, state.contains("update=UPDATED") && state.contains("available=true"));
		state = String.valueOf(updating.call("status", Map.of()).payload().get("handbook"));
		expect("then leaves it if nothing's new: " + state, state.contains("update=NOT_MODIFIED"));
		state = String.valueOf(updating.call("status", Map.of()).payload().get("handbook"));
		expect("and takes a newer one when it appears: " + state, state.contains("update=UPDATED"));
		state = String.valueOf(updating.call("status", Map.of()).payload().get("handbook"));
		expect("and says so if it couldn't check, keeping its copy: " + state, state.contains("update=FAILED") && state.contains("available=true") && state.contains("out of date"));

		Map<String, Object> refreshed = ok("refresh_handbook");
		expect("refresh_handbook also asks: " + refreshed, refreshed.containsKey("outcome") && String.valueOf(refreshed.get("handbook")).contains("available=true"));
		expect("the locations come from the tag data", ((List<String>) ok("handbook_locations").get("locations")).contains("assets/init/room/MINE_*"));
		Map<String, Object> tags = ok("handbook_tags", "type", "Mine");
		expect("a type's tags include its own and shared ones", ((Map<?, ?>) tags.get("tags")).containsKey("MINABLE") && ((Map<?, ?>) tags.get("tags")).containsKey("MINI_COLOR"));
		Map<String, Object> byLocation = ok("handbook_tags", "location", "assets/init/room/MINE_*");
		expect("a location gives only its own", ((Map<?, ?>) byLocation.get("tags")).containsKey("MINABLE") && ((Map<?, ?>) byLocation.get("tags")).size() < ((Map<?, ?>) tags.get("tags")).size());
		Map<String, Object> tag = ok("handbook_tag", "type", "Mine", "tag", "MINABLE");
		expect("a tag has all the handbook says: " + tag.keySet(), Boolean.TRUE.equals(tag.get("found")) && tag.get("description") != null && tag.containsKey("example") && tag.containsKey("url") && tag.containsKey("unverified"));
		expect("the sprite page's words reach a sprite's tags", Boolean.TRUE.equals(ok("handbook_tag", "type", "Mine", "tag", "SPRITES.WORK_1X1.FPS").get("found")));
		expect("text file tags are in their own section", Boolean.TRUE.equals(ok("handbook_tag", "type", "Mine", "tag", "WORK.TITLE", "section", "text").get("found")));
		Map<String, Object> absent = ok("handbook_tag", "type", "Mine", "tag", "NOT_A_TAG");
		expect("a tag it doesn't describe isn't invented", Boolean.FALSE.equals(absent.get("found")));
		refused("both type and location", "handbook_tags", "type", "Mine", "location", "x");
		refused("neither", "handbook_tags");
		refused("a bad section", "handbook_tags", "type", "Mine", "section", "x");
		Map<String, Object> form = ok("new_asset_form", "mod", "Agent Mod", "type", "Mine", "name", "WORDS", "under", "MINABLE", "detail", "full");
		expect("a full form carries the handbook's words", String.valueOf(((List<Map<String, Object>>) form.get("tags")).get(0).get("handbook")).contains("deposit"));
	}

	private static void json() {
		Object parsed = JsonText.parse("{\"a\":[1,2.5,\"x\\n\\u00e9\",null,true],\"b\":{}}");
		expect("JSON reads what it writes", JsonText.parse(JsonText.write(parsed)).equals(parsed) && JsonText.parse(JsonText.writePretty(parsed)).equals(parsed));
		expect("one line, always", !JsonText.write(in("a", List.of(in("b", "line\nbreak\r\u2028x")), "c", in("d", 1.0))).matches("(?s).*[\\n\\r\\u2028].*"));
		expect("a whole number has no point", JsonText.write(3.0).equals("3") && JsonText.write(2.5).equals("2.5"));
		for (String bad : List.of("{", "[1,", "{\"a\"}", "nul", "\"x", "1 2", "[".repeat(100000))) {
			try {
				JsonText.parse(bad);
				expect("should refuse: " + bad.substring(0, Math.min(20, bad.length())), false);
			} catch (IllegalStateException | NumberFormatException e) {
				expect("refused", true);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> talk(Operations operations, String... lines) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new Server(operations, new ByteArrayInputStream((String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8)), out).run();
		List<Map<String, Object>> replies = new ArrayList<>();
		for (String line : out.toString(StandardCharsets.UTF_8).split("\n")) {
			if (!line.isBlank()) {
				replies.add((Map<String, Object>) JsonText.parse(line));
			}
		}
		return replies;
	}

	private static final String META = "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\",\"io.modelcontextprotocol/clientCapabilities\":{}}";

	/** Both kinds of client, by the specification's own examples. */
	@SuppressWarnings("unchecked")
	private static void mcp() throws Exception {
		// The newer revision: no handshake, and every request says its version.
		List<Map<String, Object>> modern = talk(ops, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"server/discover\",\"params\":{" + META + "}}",
				"{\"jsonrpc\":\"2.0\",\"id\":\"two\",\"method\":\"tools/list\",\"params\":{" + META + "}}",
				"{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"status\",\"arguments\":{}," + META + "}}");
		expect("three requests get three replies: " + modern.size(), modern.size() == 3);
		Map<String, Object> discover = (Map<String, Object>) modern.get(0).get("result");
		expect("discover names the version: " + discover, ((List<String>) discover.get("supportedVersions")).contains("2026-07-28") && "complete".equals(discover.get("resultType")));
		expect("and gives the guide", String.valueOf(discover.get("instructions")).contains("status"));
		expect("and who it is", String.valueOf(discover.get("_meta")).contains("syx-toolkit"));
		expect("a reply keeps a string id", "two".equals(modern.get(1).get("id")));
		Map<String, Object> list = (Map<String, Object>) modern.get(1).get("result");
		List<Map<String, Object>> tools = (List<Map<String, Object>>) list.get("tools");
		expect("every operation is a tool", tools.size() == ops.all().size());
		for (Map<String, Object> tool : tools) {
			Map<String, Object> annotations = (Map<String, Object>) tool.get("annotations");
			boolean writes = WRITES.contains(tool.get("name"));
			expect(tool.get("name") + " is marked correctly: " + annotations, Boolean.valueOf(!writes).equals(annotations.get("readOnlyHint")));
			expect(tool.get("name") + " has a valid name and schema", String.valueOf(tool.get("name")).matches("[A-Za-z0-9_.-]{1,128}") && "object".equals(((Map<?, ?>) tool.get("inputSchema")).get("type")));
		}
		expect("save_asset is marked as overwriting", tools.stream().anyMatch(tool -> "save_asset".equals(tool.get("name")) && Boolean.TRUE.equals(((Map<?, ?>) tool.get("annotations")).get("destructiveHint"))));
		expect("create_asset isn't", tools.stream().anyMatch(tool -> "create_asset".equals(tool.get("name")) && Boolean.FALSE.equals(((Map<?, ?>) tool.get("annotations")).get("destructiveHint"))));
		Map<String, Object> call = (Map<String, Object>) modern.get(2).get("result");
		expect("a call returns its result as text: " + call, Boolean.FALSE.equals(call.get("isError")) && "complete".equals(call.get("resultType")) && String.valueOf(((List<Map<String, Object>>) call.get("content")).get(0).get("text")).contains("game_version"));

		// An older client: the handshake first, and no newer fields back.
		List<Map<String, Object>> legacy = talk(ops, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"t\",\"version\":\"1\"}}}",
				"{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",
				"{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"status\",\"arguments\":{}}}",
				"{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"ping\"}");
		expect("the notification gets no reply: " + legacy.size(), legacy.size() == 3);
		Map<String, Object> init = (Map<String, Object>) legacy.get(0).get("result");
		expect("the same version comes back", "2025-06-18".equals(init.get("protocolVersion")) && init.containsKey("capabilities") && init.containsKey("serverInfo") && String.valueOf(init.get("instructions")).contains("status"));
		expect("an older client's call works", Boolean.FALSE.equals(((Map<?, ?>) legacy.get(1).get("result")).get("isError")) && !((Map<?, ?>) legacy.get(1).get("result")).containsKey("resultType"));
		expect("ping works", ((Map<?, ?>) legacy.get(2).get("result")).isEmpty());
		Map<String, Object> newest = (Map<String, Object>) talk(ops, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"1999-01-01\"}}").get(0).get("result");
		expect("an unknown version gets the newest handshake version", "2025-11-25".equals(newest.get("protocolVersion")));

		// What's wrong gets an answer, and the server carries on.
		List<Map<String, Object>> bad = talk(ops, "not json", "{\"jsonrpc\":\"2.0\",\"id\":1}", "[1,2]",
				"{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"tools/list\"}",
				"{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"nothing/here\",\"params\":{" + META + "}}",
				"{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"nope\"," + META + "}}",
				"{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/list\"}",
				"{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/list\",\"params\":{\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"1900-01-01\",\"io.modelcontextprotocol/clientCapabilities\":{}}}}",
				"{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/list\",\"params\":{\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\"}}}",
				"{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"get_mod_info\",\"arguments\":{}," + META + "}}",
				"{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"ping\",\"params\":{" + META + "}}");
		expect("everything gets a reply: " + bad.size(), bad.size() == 11);
		int[] codes = { -32700, -32600, -32600, -32600, -32601, -32602, -32602, -32022, -32602 };
		for (int i = 0; i < codes.length; i++) {
			Map<String, Object> error = (Map<String, Object>) bad.get(i).get("error");
			expect("reply " + i + " has code " + codes[i] + ": " + bad.get(i), error != null && ((Number) error.get("code")).intValue() == codes[i]);
		}
		expect("an unsupported version lists what is", String.valueOf(((Map<?, ?>) bad.get(7).get("error")).get("data")).contains("2026-07-28"));
		Map<String, Object> refused = (Map<String, Object>) bad.get(9).get("result");
		expect("a tool's own refusal is a result with isError, for the agent to act on: " + refused, Boolean.TRUE.equals(refused.get("isError")));
		expect("and it carried on after all that", bad.get(10).get("result") != null);

		// A cancelled request gets no reply.
		List<Map<String, Object>> cancelled = talk(ops, "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\",\"params\":{\"requestId\":9}}",
				"{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\",\"params\":{\"name\":\"status\",\"arguments\":{}," + META + "}}");
		expect("a cancelled request gets no reply: " + cancelled, cancelled.isEmpty());

		// Read-only leaves the write tools off the list, and refuses them if called anyway.
		List<Map<String, Object>> locked = talk(readOnly, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{" + META + "}}",
				"{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"create_mod\",\"arguments\":{\"name\":\"Nope\"}," + META + "}}");
		List<Map<String, Object>> lockedTools = (List<Map<String, Object>>) ((Map<?, ?>) locked.get(0).get("result")).get("tools");
		expect("read-only lists no writes", lockedTools.stream().noneMatch(tool -> WRITES.contains(tool.get("name"))));
		expect("and a write is refused", Boolean.TRUE.equals(((Map<?, ?>) locked.get(1).get("result")).get("isError")) && !Files.exists(mods.resolve("Nope")));
	}

	private static void connecting() {
		Path exe = Path.of("C:\\Program Files\\Syx Toolkit\\syx.exe");
		List<AgentSetup.Client> clients = AgentSetup.clients(exe, false);
		expect("the clients are covered: " + clients.stream().map(AgentSetup.Client::name).toList(), clients.stream().map(AgentSetup.Client::name).toList().containsAll(List.of("Claude Code", "Claude Desktop", "Cursor", "Codex", "VS Code", "Any other MCP client", "Agents without MCP")));
		for (AgentSetup.Client client : clients) {
			expect(client.name() + " has the command: " + client.text(), client.text().contains("Syx Toolkit") && client.text().contains("syx.exe"));
			expect(client.name() + " says where it goes", !client.where().isBlank());
			if (client.text().startsWith("{")) {
				Map<?, ?> config = (Map<?, ?>) JsonText.parse(client.text());
				Map<?, ?> servers = (Map<?, ?>) config.get(client.name().equals("VS Code") ? "servers" : "mcpServers");
				Map<?, ?> server = (Map<?, ?>) servers.get(AgentSetup.SERVER_NAME);
				expect(client.name() + "'s JSON keeps the path whole", exe.toString().equals(server.get("command")) && List.of("mcp").equals(server.get("args")));
			}
		}
		expect("the docs' command for Claude Code", clients.get(0).text().equals("claude mcp add --transport stdio --scope user syx-toolkit -- \"C:\\Program Files\\Syx Toolkit\\syx.exe\" mcp"));
		expect("the docs' command for Codex", clients.get(3).text().equals("codex mcp add syx-toolkit -- \"C:\\Program Files\\Syx Toolkit\\syx.exe\" mcp"));
		expect("read-only is passed on to each", AgentSetup.clients(exe, true).stream().limit(6).allMatch(client -> client.text().contains("--read-only")));
		expect("nothing holds a key", clients.stream().noneMatch(client -> client.text().toLowerCase(java.util.Locale.ROOT).contains("key")));
		expect("an unpackaged app offers none", AgentSetup.launcher() == null);
	}

	private static void deleteFolder(Path folder) throws IOException {
		if (!Files.exists(folder)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(folder)) {
			for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}

	/** The same work as the Normal Maps screen, by name, including what it must ask for first. */
	@SuppressWarnings("unchecked")
	private static void normalMaps() throws Exception {
		ok("create_mod", "name", "Normals");
		Path root = mods.resolve("Normals");
		String version;
		try (Stream<Path> children = Files.list(root)) {
			version = children.map(child -> child.getFileName().toString()).filter(name -> name.matches("V\\d+")).findFirst().orElseThrow();
		}
		String path = version + "/assets/sprite/race/Folk.png";
		Path sheet = root.resolve(path);
		Files.createDirectories(sheet.getParent());
		java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(448, 546, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		for (int y = 10; y < 20; y++) {
			for (int x = 10; x < 20; x++) {
				image.setRGB(x, y, 0xFFFFFFFF);
			}
		}
		javax.imageio.ImageIO.write(image, "png", sheet.toFile());
		List<Map<String, Object>> sheets = (List<Map<String, Object>>) ok("list_sprite_sheets", "mod", "Normals").get("sheets");
		expect("the sheet is listed and can be generated for: " + sheets, sheets.stream().anyMatch(entry -> path.equals(entry.get("path")) && Boolean.TRUE.equals(entry.get("can_generate")) && Boolean.FALSE.equals(entry.get("has_normals"))));
		Map<String, Object> preview = ok("preview_sprite_sheet", "mod", "Normals", "path", path);
		expect("a preview is an image an agent can see: " + preview.keySet(), preview.get("png_base64") != null && "Race sheet (city map)".equals(preview.get("kind")));
		Map<String, Object> made = ok("generate_normals", "mod", "Normals", "path", path, "revision", preview.get("revision"));
		expect("generating says what it did: " + made, Boolean.TRUE.equals(made.get("changed")) && path.equals(made.get("path")));
		byte[] generated = Files.readAllBytes(sheet);
		refused("it won't replace normals unasked", "generate_normals", "mod", "Normals", "path", path);
		refused("an old revision is refused", "generate_normals", "mod", "Normals", "path", path, "replace_existing", true, "revision", preview.get("revision"));
		refused("every normal isn't cleared unasked", "trim_normals", "mod", "Normals", "path", path, "mode", "all");
		refused("a mode must be one of the two", "trim_normals", "mod", "Normals", "path", path, "mode", "some");
		refused("a path can't leave the mod", "generate_normals", "mod", "Normals", "path", "../Other/Folk.png");
		expect("read-only refuses", readOnly.call("generate_normals", in("mod", "Normals", "path", path, "replace_existing", true)).outcome() == Outcome.REFUSED);
		expect("and refused calls left the file as it was", java.util.Arrays.equals(generated, Files.readAllBytes(sheet)));
		Map<String, Object> cleared = ok("trim_normals", "mod", "Normals", "path", path, "mode", "all", "discard_existing", true);
		expect("clearing changes the file: " + cleared, Boolean.TRUE.equals(cleared.get("changed")));
		expect("and the sheet then has no normals", Boolean.FALSE.equals(ok("preview_sprite_sheet", "mod", "Normals", "path", path).get("has_normals")));

		List<Map<String, Object>> sections = (List<Map<String, Object>>) ok("list_normal_sections", "mod", "Normals", "path", path).get("sections");
		expect("the sections start at their defaults: " + sections, sections.stream().anyMatch(entry -> "torso".equals(entry.get("id")) && Integer.valueOf(100).equals(entry.get("strength"))));
		byte[] before = Files.readAllBytes(sheet);
		Map<String, Object> shading = in("feet", in("strength", 50));
		Map<String, Object> previewed = ok("preview_normals", "mod", "Normals", "path", path, "sections", shading);
		expect("a preview is an image and writes nothing", previewed.get("png_base64") != null && java.util.Arrays.equals(before, Files.readAllBytes(sheet)));
		expect("and saves no values", ((List<Map<String, Object>>) ok("list_normal_sections", "mod", "Normals", "path", path).get("sections")).stream().anyMatch(entry -> "feet".equals(entry.get("id")) && Integer.valueOf(100).equals(entry.get("strength"))));
		refused("an unknown section is refused", "preview_normals", "mod", "Normals", "path", path, "sections", in("wings", in("strength", 50)));
		refused("a value out of range is refused", "generate_normals", "mod", "Normals", "path", path, "sections", in("feet", in("strength", 900)));
		ok("generate_normals", "mod", "Normals", "path", path, "sections", shading);
		expect("generating remembers the values", ((List<Map<String, Object>>) ok("list_normal_sections", "mod", "Normals", "path", path).get("sections")).stream().anyMatch(entry -> "feet".equals(entry.get("id")) && Integer.valueOf(50).equals(entry.get("strength"))));
		ok("generate_normals", "mod", "Normals", "path", path, "replace_existing", true, "sections", in("feet", in("strength", 100)));
		expect("and setting a default forgets it", ((List<Map<String, Object>>) ok("list_normal_sections", "mod", "Normals", "path", path).get("sections")).stream().anyMatch(entry -> "feet".equals(entry.get("id")) && Integer.valueOf(100).equals(entry.get("strength"))));
	}

	/** The same conversion the screen runs, through the operations. */
	@SuppressWarnings("unchecked")
	private static void audioConversion() throws Exception {
		String previous = System.getProperty("jpackage.app-path");
		Path app = temp.resolve("audio-app");
		Files.createDirectories(app);
		try {
			System.setProperty("jpackage.app-path", app.resolve("Syx Toolkit.exe").toString());
			ok("create_mod", "name", "Audio Convert");
			Path wav = mods.resolve("Audio Convert").resolve("sounds/horn.wav");
			Files.createDirectories(wav.getParent());
			writeTone(wav);
			List<Map<String, Object>> files = (List<Map<String, Object>>) ok("list_audio_files", "mod", "Audio Convert").get("files");
			expect("the wav is listed: " + files, files.stream().anyMatch(file -> "sounds/horn.wav".equals(file.get("path")) && Boolean.FALSE.equals(file.get("ogg"))));
			Map<String, Object> converted = ok("convert_audio", "mod", "Audio Convert", "path", "sounds/horn.wav");
			expect("it names the ogg and where the original went: " + converted, "sounds/horn.ogg".equals(converted.get("ogg")) && String.valueOf(converted.get("moved_to")).contains("Converted Audio Files") && String.valueOf(converted.get("message")).contains(String.valueOf(converted.get("moved_to"))));
			expect("the original left the mod", !Files.exists(wav) && Files.isRegularFile(mods.resolve("Audio Convert").resolve("sounds/horn.ogg")));
			Path keep = wav.resolveSibling("keep.wav");
			writeTone(keep);
			expect("read-only refuses", readOnly.call("convert_audio", in("mod", "Audio Convert", "path", "sounds/keep.wav")).outcome() == Outcome.REFUSED);
			expect("and leaves the file", Files.isRegularFile(keep));
		} finally {
			if (previous == null) {
				System.clearProperty("jpackage.app-path");
			} else {
				System.setProperty("jpackage.app-path", previous);
			}
		}
	}

	private static void writeTone(Path file) throws Exception {
		javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(22050, 16, 1, true, false);
		byte[] silence = new byte[2205 * 2];
		javax.sound.sampled.AudioSystem.write(new javax.sound.sampled.AudioInputStream(new java.io.ByteArrayInputStream(silence), format, silence.length / 2), javax.sound.sampled.AudioFileFormat.Type.WAVE, file.toFile());
	}

	private static void expect(String name, boolean ok) {
		if (ok) {
			passed++;
		} else {
			failed++;
			System.out.println("FAILED: " + name);
		}
	}
}
