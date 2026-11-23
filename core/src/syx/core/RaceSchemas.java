package syx.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import syx.core.AssetChecker.Finding;
import syx.core.AssetChecker.Severity;
import syx.core.AssetSchema.Companion;
import syx.core.AssetSchema.CrossRule;
import syx.core.TagSpec.Confidence;
import syx.core.TagSpec.Kind;
import syx.core.TagSpec.Reference;
import syx.core.TagSpec.Shape;
import syx.core.TagSpec.Sprite;

/**
 * The tags of a race and of every file it names. The list of reads is in {@link RaceReads}, taken from
 * the installed jar. A difference there, or a range that isn't among those reads, leaves the type off.
 */
public final class RaceSchemas {

	/** Folders of text files a new race can copy from, directly under assets, with no subfolders. */
	public static final List<String> STARTER_FOLDERS = List.of(
			"init/race",
			"init/race/sprite",
			"init/race/home",
			"text/race",
			"text/race/bio",
			"text/race/bio/specific",
			"text/race/king",
			"text/race/raider/message",
			"text/race/raider/name",
			"text/race/tourist",
			"text/names/world",
			"text/names/nameset");

	/**
	 * The most appearance types a race can have. RAppearence reads jsons("TYPES", 1, 4), and Json.jsons
	 * refuses as many entries as its maximum (Json.java:228-229, checked against the compiled jar).
	 */
	public static final int MAX_TYPES = 3;

	/** The most cloth colours. RColors reads jsons("COLOR_CLOTHES", 1, 16), with the same rule. */
	static final int MAX_CLOTHES = 15;

	/** The most a class's standing can be in a race's STATS (StatStanding.java:337). */
	private static final double MAX_STANDING = 1000000;

	/** Headings for the race maps a person usually leaves alone. They start folded, and the saved key is unchanged. */
	private static final Map<String, String> FOLDED = Map.ofEntries(
			Map.entry("PREFERRED.ROAD", "Road preference"),
			Map.entry("PREFERRED.WORK", "Work preference"),
			Map.entry("PREFERRED.RESOURCE_RPICE_MUL", "Resource price multiplier"),
			Map.entry("PREFERRED.RESOURCE_RPICE_CAP", "Resource price cap"),
			Map.entry("PREFERRED.OTHER_RACES", "Other races"),
			Map.entry("PREFERRED.OTHER_RACES_REVERSE", "Other races, reversed"),
			Map.entry("PREFERRED.WORLD_BUILDING", "World building"),
			Map.entry("RESOURCE", "Resource"),
			Map.entry("BOOST", "Boost"),
			Map.entry("ROOM_FLOOR_OVERRIDE", "Room floor override"));

	private RaceSchemas() {
	}

	public static AssetSchema schema(ClassFileScanner scanner, List<String> unresolved) throws ClassFileScanner.ScanException {
		Map<String, List<String>> reads = RaceReads.all();
		ReviewedReads.check(scanner, reads, unresolved);
		if (!unresolved.isEmpty()) {
			return null;
		}
		AssetSchema schema = build();
		ReviewedReads.checkLimits(schema, reads, unresolved);
		return unresolved.isEmpty() ? schema : null;
	}

	private static AssetSchema build() {
		List<TagSpec> data = new ArrayList<>();
		data.add(leaf("PLAYABLE", Kind.BOOL, true, null, null, null, true, "Race.java, the constructor"));
		data.add(properties());
		data.add(plain("SPRITE_FILE", true, "RAppearence.java, value SPRITE_FILE"));
		data.add(plain("HOME", true, "Race.java, value HOME"));
		data.add(plain("BIO_FILE", true, "Bio.java, value BIO_FILE"));
		data.add(plain("BIO_FILE_SPECIFIC", false, "Bio.java, has BIO_FILE_SPECIFIC"));
		data.add(plain("KING_FILE", true, "KingMessages.java, value KING_FILE"));
		data.add(plain("WORLD_NAME_FILE", true, "RaceInfo.java, value WORLD_NAME_FILE"));
		data.add(plain("RAID_TEXT_FILE", true, "RaceInfo.java, value RAID_TEXT_FILE"));
		data.add(plain("RAIDER_NAME_FILE", true, "RaceInfo.java, value RAIDER_NAME_FILE"));
		data.add(tourist());
		data.add(preferred());
		data.add(population());
		data.add(stats());
		data.add(map("RESOURCE", false, "0", 0, 100000, Reference.RESOURCES, "Race.java, RESOURCES.map readFill 100000"));
		data.add(map("BOOST", false, null, null, null, null, "BoostSpecs.java. A key must end in >ADD or >MUL. Which stats can be boosted is decided in code, so this isn't a list."));
		data.add(names("EQUIPMENT_NOT_ENABLED", "Race.java, has EQUIPMENT_NOT_ENABLED. Unknown ids are decided in code, so this isn't a list."));
		data.add(names("EQUIPMENT_ENABLED", "Race.java, has EQUIPMENT_ENABLED"));
		data.add(map("TRAIT", false, "0 for every trait", 0, 1, Reference.TRAITS, "TRAITS.java:44,75-80. KJson uses the map's key, TRAIT, and reads each chance with d from 0 to 1."));
		data.add(map("RESOURCE_GROOMING", false, null, null, null, Reference.RESOURCES, "Race.java hands the block to RES_AMOUNT.make, which reads each amount with i and skips an unknown resource."));
		data.add(map("MILITARY_SUPPLY_USE", false, null, null, null, Reference.SUPPLIES, "ResSupplies.java:47-51. Each amount is i from 0 to 10000, and the maximum is not enforced. The bytecode names the supply, not this tag, so the range is checked beside the table."));
		data.add(map("STATS_ON_SPAWN", false, null, null, null, null, "RaceStats.java:126-138. Stat names are decided in code, so this isn't a list. Each value is d with no stated range."));
		data.add(keyedLists("ROOM_FLOOR_OVERRIDE", "RFloors.java:15-22. Each room names a list of floors. A room that isn't one is skipped, and a floor that isn't one is left out."));
		data.add(icon("ICON_SMALL", "RAppearence.java"));
		data.add(icon("ICON_BIG", "RAppearence.java"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(line("POSSESSIVE", true, null, "RaceInfo.java"));
		text.add(line("POSSESSIVES", true, null, "RaceInfo.java"));
		text.add(leaf("DESC_LONG", Kind.TEXT_LINES, true, null, null, null, true, "RaceInfo.java, text. The vanilla lines include line breaks."));
		text.add(line("CHALLENGE", false, "", "RaceInfo.java, text with a blank default"));
		text.add(rows("PROS", false, "No lines", 0, "RaceInfo.java, textsTry"));
		text.add(rows("CONS", false, "No lines", 0, "RaceInfo.java, textsTry"));
		for (String pronoun : List.of("PRONOUN_HE", "PRONOUN_HIS", "PRONOUN_HIMSELF", "PRONOUN_HIM", "PRONOUN_CHILD")) {
			text.add(rows(pronoun, true, null, 0, "RaceInfo.java, RPronoun"));
			text.add(rows(pronoun + "C", true, null, 0, "RPronoun reads the key with C added"));
		}
		text.add(rows("ARMY_NAMES", true, null, 255, "RaceInfo.java, texts 1 to 255"));
		for (String said : List.of("HELLO", "GOODBYE", "CURSE", "INSULT", "INSULTING", "LORD", "CITY", "OTHERS", "SELVES", "SELF", "CHILDREN")) {
			text.add(rows(said, true, null, 0, "RaceInfo.java"));
		}

		List<String> notes = List.of(
				"A new race starts from a vanilla appearance. Its files are written under the new name, and the pictures stay the vanilla sheets those files already name.",
				"Trait chances are read from TRAIT (TRAITS.java:44,75-80). The vanilla races write TRAITS, which none of the game's reads of a race file uses, so their chances appear to have no effect. TRAITS isn't on the form. A new race's files are copies of its look's, so it keeps TRAITS as written, like anything else the form can't show.",
				"The vanilla files also hold tags no reviewed read uses: TECH, PREFERRED.EMPLOYED and PREFERRED.BUILDING_OVERRIDE, in the appearance COLOR_EXTRA1, COLOR_EXTRA2, OCCURRENCE_EXTRA1, OCCURRENCE_EXTRA2, PORTRAIT_FILE and SET_APPEARANCE_STATS, HOME_NOBLE in the biography, whose read is commented out (BioLines.java:174), and DIPLOMACY_GREETING in the world names. They aren't on the form, and stay as written.",
				"A race can have at most " + MAX_TYPES + " appearance types and " + MAX_CLOTHES + " cloth colours (Json.java:228-229).",
				"POPULATION with nothing above 0 means the race never grows. Leaving the block out does that on purpose, as ARGONOSH and CANTOR do.",
				"WORK can't yet tell a workplace from any other room, so a room that exists is not reported. A key that isn't a room, and isn't *, is.",
				"There is no sound file for a race. SoundRaces is the game's own loader.");
		AssetSchema.CrossRule rule = new AssetSchema.CrossRule() {
			@Override
			public void check(Map<String, String> values, GameAssetIndex index, List<Finding> findings) {
				rules(values, index, Set.of(), findings);
			}

			@Override
			public void check(Map<String, String> values, GameAssetIndex index, Set<String> kept, List<Finding> findings) {
				rules(values, index, kept, findings);
			}
		};
		return new AssetSchema(data, text, List.of(), notes, rule, List.of(), null, companions(), "init/race/sprite", FOLDED);
	}

	/*
	 * "Citizens and slaves need a standing above 0" is only checked when it's certain. RaceStats stops loading
	 * when no stat gives the class any standing (RaceStats.java:80-97), and every stat starts from its own base
	 * standing (:45-46, STATS.java:274-277), which a race's file can't show. But an entry whose name starts with
	 * * matches every stat (StatsJson.java:33-39) and replaces its whole standing (RaceStats.java:57-63), so then
	 * no base is left: every stat ends as some entry says, whatever the order. See standingRule.
	 */
	private static void rules(Map<String, String> values, GameAssetIndex index, Set<String> kept, List<Finding> findings) {
		standingRule(values, kept, findings);
		favourite("PREFERRED.FOOD", "food", Reference.EDIBLES, values, index, kept, findings);
		favourite("PREFERRED.DRINK", "drink", Reference.DRINKABLES, values, index, kept, findings);
		List<String> resources = index.options(Reference.RESOURCES);
		for (Map.Entry<String, String> entry : values.entrySet()) {
			String key = entry.getKey();
			if (key.startsWith("BOOST.") && !key.endsWith(">ADD") && !key.endsWith(">MUL")) {
				findings.add(new Finding(key, Severity.ERROR, "A boost key has to end in >ADD or >MUL, or the game stops loading (BoostSpecs.java:167-177)."));
			}
			// An unknown name is skipped before its amount is read (RES_AMOUNT.java:19-21, RMAP.java:302-325), and the map check already warns about it.
			if (key.startsWith("RESOURCE_GROOMING.") && found(resources, key.substring("RESOURCE_GROOMING.".length()))
					|| key.startsWith("MILITARY_SUPPLY_USE.") && AssetChecker.listed(index.options(Reference.SUPPLIES), key.substring("MILITARY_SUPPLY_USE.".length()))) {
				checkWhole(key, entry.getValue(), findings);
			}
			if (key.startsWith("STATS.") && STANDING_CLASSES.stream().anyMatch(clas -> key.endsWith("." + clas))) {
				String value = entry.getValue();
				if (value.matches("-?(\\d+\\.?\\d*|\\.\\d+)") && (Double.parseDouble(value) < 0 || Double.parseDouble(value) > MAX_STANDING)) {
					findings.add(new Finding(key, Severity.ERROR, "This must be from 0 to " + (long) MAX_STANDING + ", or the game stops loading (StatStanding.java:337)."));
				}
			}
			if (key.endsWith(".ADDON_INDEX") && wholeAtLeast(entry.getValue(), 8)) {
				findings.add(new Finding(key, Severity.ERROR, "The game only has indexes 0 to 7. 8 is past the end and stops loading (RAddon.java:33-35, RType.java:73)."));
			}
			if (key.startsWith("ROOM_FLOOR_OVERRIDE.")) {
				for (String floor : entry.getValue().split(",", -1)) {
					String name = floor.trim();
					if (!name.isEmpty() && !AssetChecker.listed(index.options(Reference.FLOORS), name)) {
						findings.add(new Finding(key, Severity.ERROR, "The game has no floor called " + name + ", and stops loading for this (RFloors.java:21, RMAP.java:147)."));
					}
				}
			}
			if (key.startsWith("file.home.") && key.contains(".RESOURCES.")) {
				String resource = key.substring(key.indexOf(".RESOURCES.") + ".RESOURCES.".length());
				if (found(resources, resource)) {
					homeAmount(key, entry.getValue(), findings);
				}
			}
		}
		int types = indexes(values, "file.sprite.TYPES.");
		if (types > MAX_TYPES) {
			findings.add(new Finding("file.sprite.TYPES", Severity.ERROR, "The game accepts 1 to " + MAX_TYPES + " types, and stops loading with more (Json.java:228-229)."));
		}
		int clothes = indexes(values, "file.sprite.COLOR_CLOTHES.");
		if (clothes > MAX_CLOTHES) {
			findings.add(new Finding("file.sprite.COLOR_CLOTHES", Severity.ERROR, "The game accepts 1 to " + MAX_CLOTHES + " cloth colours, and stops loading with more (Json.java:228-229)."));
		}
		for (String clas : List.of("CITIZEN", "NOBLE", "SLAVE")) {
			Set<String> named = new LinkedHashSet<>();
			String prefix = "file.home." + clas + ".";
			for (String key : values.keySet()) {
				int at = key.indexOf(".RESOURCES.");
				if (key.startsWith(prefix) && at > 0) {
					String resource = key.substring(at + ".RESOURCES.".length());
					// The game skips a name it doesn't have, so only known ones count (RaceHomeClass.java:73-75).
					if (found(resources, resource)) {
						named.add(resource);
					}
				}
			}
			if (named.size() > 8) {
				findings.add(new Finding("file.home." + clas, Severity.ERROR, "A home class can name at most 8 different resources, or the game stops loading (RaceHomeClass.java:92-94)."));
			}
		}
		// A collection kept as written, such as one that generates its colours, is still a name the game has.
		String prefix = "file.sprite.COLORS";
		if (kept.contains(prefix)) {
			return;
		}
		Set<String> colors = new LinkedHashSet<>();
		for (String key : concat(values.keySet(), kept)) {
			if (key.startsWith(prefix + ".")) {
				String rest = key.substring(prefix.length() + 1);
				int dot = rest.indexOf('.');
				colors.add(dot < 0 ? rest : rest.substring(0, dot));
			}
		}
		if (!colors.isEmpty()) {
			for (Map.Entry<String, String> entry : values.entrySet()) {
				if (!namesColor(entry.getKey()) || entry.getValue() == null || entry.getValue().isBlank()) {
					continue;
				}
				if (!colors.contains(entry.getValue())) {
					findings.add(new Finding(entry.getKey(), Severity.ERROR, "The game looks this up in COLORS and stops loading if it isn't one of them (RColors.java, RMAP.java:30-46)."));
				}
			}
		}
	}

	private static final List<String> STANDING_CLASSES = List.of("CITIZEN", "NOBLE", "SLAVE", "OTHER");

	/**
	 * With an entry that matches every stat, each stat's standing is whatever its last matching entry says, and a
	 * class an entry leaves out is 0. So if no entry gives citizens, or slaves, a value above 0, none of their
	 * stats can, and the game stops loading (RaceStats.java:95-97). An entry the form can't see might, so a kept
	 * STATS entry skips the rule.
	 */
	private static void standingRule(Map<String, String> values, Set<String> kept, List<Finding> findings) {
		if (kept.stream().anyMatch(key -> key.equals("STATS") || key.startsWith("STATS."))) {
			return;
		}
		boolean everyStat = false;
		Set<String> above = new LinkedHashSet<>();
		for (Map.Entry<String, String> entry : values.entrySet()) {
			if (!entry.getKey().startsWith("STATS.")) {
				continue;
			}
			String rest = entry.getKey().substring("STATS.".length());
			int dot = rest.indexOf('.');
			String row = dot < 0 ? rest : rest.substring(0, dot);
			everyStat |= row.startsWith("*");
			String clas = dot < 0 ? null : rest.substring(dot + 1);
			String value = entry.getValue();
			if (clas != null && value != null && value.trim().matches("-?(\\d+\\.?\\d*|\\.\\d+)") && Double.parseDouble(value.trim()) > 0) {
				above.add(clas);
			}
		}
		if (!everyStat) {
			return;
		}
		for (String clas : List.of("CITIZEN", "SLAVE")) {
			if (!above.contains(clas)) {
				findings.add(new Finding("STATS", Severity.ERROR, "An entry starting with * sets every stat, and no entry gives " + clas
						+ " a value above 0, so no stat gives " + clas.toLowerCase(java.util.Locale.ROOT) + "s any standing and the game stops loading (StatsJson.java:33-39, RaceStats.java:57-63,95-97)."));
			}
		}
	}

	private static List<String> concat(Set<String> first, Set<String> second) {
		List<String> all = new ArrayList<>(first);
		all.addAll(second);
		return all;
	}

	/**
	 * The game needs a favourite food and drink. Unknown names are skipped with a warning, so the game
	 * only stops when none is left (RacePreferrence.java, RMAP.java:168-192).
	 */
	private static void favourite(String key, String what, Reference reference, Map<String, String> values, GameAssetIndex index, Set<String> kept, List<Finding> findings) {
		if (kept.contains(key) || kept.contains("PREFERRED")) {
			return;
		}
		String value = values.get(key);
		if (value == null || value.isBlank()) {
			findings.add(new Finding(key, Severity.ERROR, "The game needs a favourite " + what + ", and * is allowed (RacePreferrence.java)."));
			return;
		}
		List<String> options = index.options(reference);
		for (String name : value.split(",", -1)) {
			String item = name.trim();
			if (item.equals("*") || AssetChecker.listed(options, item)) {
				return;
			}
		}
		findings.add(new Finding(key, Severity.ERROR, "The game has none of these, and stops loading without a favourite " + what + " (RacePreferrence.java)."));
	}

	/** A home sheet's amount is a whole number from 1, read with i, whose maximum of 15 isn't enforced (RaceHomeClass.java:77). */
	private static void homeAmount(String key, String value, List<Finding> findings) {
		if (!value.matches("-?\\d+")) {
			findings.add(new Finding(key, Severity.ERROR, "The game reads this as a whole number, and stops loading otherwise (RaceHomeClass.java:77)."));
		} else if (Long.parseLong(value) < 1) {
			findings.add(new Finding(key, Severity.ERROR, "The lowest amount the game accepts is 1, and it stops loading below that (RaceHomeClass.java:77)."));
		} else if (Long.parseLong(value) > 15) {
			findings.add(new Finding(key, Severity.WARNING, "The game's stated maximum is 15, but it doesn't enforce it, so this may behave oddly (RaceHomeClass.java:77)."));
		}
	}

	/** A name the game finds with tryGet, which has no wildcards (RES_AMOUNT.java:19, RaceHomeClass.java:73). */
	private static boolean found(List<String> options, String name) {
		return !name.contains("*") && AssetChecker.listed(options, name);
	}

	/** Skin, leg and an addon's or portrait's colour are names in this file's COLORS block. */
	private static boolean namesColor(String key) {
		return key.endsWith(".COLOR_SKIN") || key.endsWith(".COLOR_LEG") || key.endsWith(".COLOR") && (key.contains(".ADDONS.") || key.contains(".FACE."));
	}

	private static int indexes(Map<String, String> values, String prefix) {
		Set<String> found = new LinkedHashSet<>();
		for (String key : values.keySet()) {
			if (key.startsWith(prefix)) {
				String rest = key.substring(prefix.length());
				int dot = rest.indexOf('.');
				found.add(dot < 0 ? rest : rest.substring(0, dot));
			}
		}
		return found.size();
	}

	private static List<Companion> companions() {
		List<Companion> files = new ArrayList<>();
		files.add(file("sprite", "Appearance", "init/race/sprite", "SPRITE_FILE", true, "", spriteTags()));
		files.add(file("home", "Home", "init/race/home", "HOME", true, "", homeTags()));
		files.add(file("bio", "Biography", "text/race/bio", "BIO_FILE", true, "", bioTags("No lines", "BioLine.java, absent is no lines")));
		// The same reader as a biography (BioLines.java:22-23), so the handbook's words for one apply to the other.
		files.add(file("bioSpecific", "Specific biography", "text/race/bio/specific", "BIO_FILE_SPECIFIC", false, "",
				bioTags("The biography's own lines", "BioLines.java:27-28, BioOpinionData.java:85-88, absent keeps the biography's own lines"), List.of("assets/text/race/bio/specific/", "assets/text/race/bio/")));
		files.add(file("king", "King", "text/race/king", "KING_FILE", true, "", kingTags()));
		files.add(file("world", "World names", "text/names/world", "WORLD_NAME_FILE", true, "", worldTags()));
		files.add(file("raid", "Raider messages", "text/race/raider/message", "RAID_TEXT_FILE", true, "", raidTags()));
		files.add(file("raiderNames", "Raider names", "text/race/raider/name", "RAIDER_NAME_FILE", true, "", List.of(rows("NAMES", true, null, 0, "RaceInfo.java, the raider name file"))));
		files.add(file("tourist", "Tourists", "text/race/tourist", "TOURIST.TOURIST_TEXT_FILE", false, "", touristText()));
		files.add(file("namesNoble", "Noble names", "text/names/nameset", "file.sprite.NAMESET_FILE_NOBLE", true, "_NOBLE", nameList()));
		files.add(file("namesChildFirst", "Child first names", "text/names/nameset", "file.sprite.CHILD.NAMESET_FILE_FIRST", true, "_CHILD_FIRST", nameList()));
		files.add(file("namesChildSurname", "Child surnames", "text/names/nameset", "file.sprite.CHILD.NAMESET_FILE_SURNAME", true, "_CHILD_SURNAME", nameList()));
		for (int i = 0; i < MAX_TYPES; i++) {
			boolean needed = i == 0;
			files.add(file("names" + i + "First", "Type " + i + " first names", "text/names/nameset", "file.sprite.TYPES." + i + ".NAMESET_FILE_FIRST", needed, "_T" + i + "_FIRST", nameList()));
			files.add(file("names" + i + "Surname", "Type " + i + " surnames", "text/names/nameset", "file.sprite.TYPES." + i + ".NAMESET_FILE_SURNAME", needed, "_T" + i + "_SURNAME", nameList()));
		}
		return files;
	}

	private static Companion file(String id, String label, String folder, String nameTag, boolean required, String suffix, List<TagSpec> tags) {
		return file(id, label, folder, nameTag, required, suffix, tags, List.of("assets/" + folder + "/"));
	}

	private static Companion file(String id, String label, String folder, String nameTag, boolean required, String suffix, List<TagSpec> tags, List<String> handbookLocations) {
		List<TagSpec> prefixed = new ArrayList<>();
		for (TagSpec tag : tags) {
			prefixed.add(tag.withKey("file." + id + "." + tag.key()));
		}
		return new Companion(id, label, folder, nameTag, required, suffix, prefixed, handbookLocations);
	}

	private static List<TagSpec> spriteTags() {
		List<TagSpec> tags = new ArrayList<>();
		tags.add(sheet("SLEEP_FILE", "sprite/race/sleep/", 164, 44, "RAppearence.java"));
		tags.add(sheet("SPRITE_EXTRA_FILE", "sprite/race/extra/", 428, 310, "RExtras.java"));
		tags.add(plain("NAMESET_FILE_NOBLE", true, "RAppearence.java"));
		tags.add(crown("CROWN"));
		tags.add(crown("MERC"));
		tags.add(crownList("RAIDER"));
		tags.add(world());
		tags.add(colorList("COLOR_CLOTHES", true, "RColors.java:245-246, jsons(COLOR_CLOTHES, 1, 16), which allows 1 to " + MAX_CLOTHES + " (Json.java:228-229)"));
		tags.add(new TagSpec("COLOR_BLOOD", Kind.COLOR, true, null, null, null, true, null, null, List.of(), "RColors.java, ColorImp", Confidence.CODE_VERIFIED));
		tags.add(colors());
		tags.add(child());
		tags.add(types());
		return tags;
	}

	private static TagSpec world() {
		return shaped("WORLD", Kind.BLOCK, true, null, null, "RAppearence.java, WorldRaceSheet.java", Shape.block(List.of(
				sheet("TOWN", "sprite/world/centre/town/", 460, 62, "WorldRaceSheet.java"),
				new TagSpec("TOWN_COLOR", Kind.COLOR, true, null, null, null, true, null, null, List.of(), "WorldRaceSheet.java", Confidence.CODE_VERIFIED),
				sheet("VILLAGE", "sprite/world/centre/village/", 460, 34, "WorldRaceSheet.java"),
				new TagSpec("VILLAGE_COLOR", Kind.COLOR, true, null, null, null, true, null, null, List.of(), "WorldRaceSheet.java", Confidence.CODE_VERIFIED),
				sheet("WALL", "sprite/world/centre/wall/", 600, 120, "WorldRaceSheet.java, WallSteriods, IInit"),
				new TagSpec("WALL_COLOR", Kind.COLOR, true, null, null, null, true, null, null, List.of(), "WorldRaceSheet.java, ColorImp with the sheet name plus _COLOR", Confidence.CODE_VERIFIED),
				sheet("WALL_VILLAGE", "sprite/world/centre/wall/", 600, 120, "WorldRaceSheet.java, WallSteriods, IInit"),
				new TagSpec("WALL_VILLAGE_COLOR", Kind.COLOR, true, null, null, null, true, null, null, List.of(), "WorldRaceSheet.java, ColorImp with the sheet name plus _COLOR", Confidence.CODE_VERIFIED),
				sheet("OVERLAY", "sprite/world/centre/overlay/", 236, 34, "WorldRaceSheet.java"),
				new TagSpec("OVERLAY_COLOR", Kind.COLOR, true, null, null, null, true, null, null, List.of(), "WorldRaceSheet.java, ColorImp with the sheet name plus _COLOR", Confidence.CODE_VERIFIED),
				sheet("TERRAIN", "sprite/world/centre/terrain/", 152, 76, "WorldRaceSheet.java"),
				new TagSpec("TERRAIN_COLOR", Kind.COLOR, true, null, null, null, true, null, null, List.of(), "WorldRaceSheet.java, ColorImp with the sheet name plus _COLOR", Confidence.CODE_VERIFIED),
				sheet("FARM", "sprite/world/centre/farm/", 364, 50, "WorldRaceSheet.java"),
				new TagSpec("FARM_COLOR", Kind.COLORS, false, null, null, null, true, null, null, List.of(), "WorldRaceSheet.java", Confidence.CODE_VERIFIED))));
	}

	private static TagSpec colors() {
		return shaped("COLORS", Kind.KEYED_BLOCKS, true, null, null, "RColors.java. The names are this file's own.", Shape.keyed(List.of(
				leaf("TURNS_GRAY_WHEN_OLD", Kind.BOOL, false, "false", null, null, true, "RColors.java"),
				leaf("TURNS_WHITE_WHEN_DEAD", Kind.BOOL, false, "false", null, null, true, "RColors.java"),
				leaf("TURNS_SICKLY", Kind.BOOL, false, "false", null, null, true, "RColors.java"),
				plain("PICK_BY_STAT", false, "RColors.java, has PICK_BY_STAT. Stats are decided in code."),
				colorList("VALUES", true, "RColors.java, ColorImp.cols. A list of colour blocks is edited here. One colour, a FROM/TO/GENERATE block, or a list of colour strings is kept as written."),
				leaf("GENERATE_RANDOMIZE", Kind.DECIMAL, false, null, null, null, true, "RColors.java, d with no range")), 0));
	}

	private static TagSpec child() {
		List<TagSpec> tags = typeParts();
		tags.add(optionalSheet("INFANT_FILE", "sprite/race/infant/", 352, 22, "RAppearence.java, has INFANT_FILE, ITileSheet"));
		return shaped("CHILD", Kind.BLOCK, true, null, null, "RAppearence.java. A child is a type, and it can also name an infant sheet.", Shape.block(tags));
	}

	private static TagSpec types() {
		return shaped("TYPES", Kind.BLOCK_LIST, true, null, null, "RAppearence.java:130, jsons(TYPES, 1, 4), which allows 1 to " + MAX_TYPES + " (Json.java:228-229)", new Shape(typeParts(), List.of(), MAX_TYPES, 0, 0));
	}

	/** What one appearance type holds. The child block holds the same things. */
	private static List<TagSpec> typeParts() {
		List<TagSpec> tags = new ArrayList<>();
		tags.add(sheet("SPRITE_FILE", "sprite/race/", 448, 546, "RaceSheet.java"));
		tags.add(sheet("SPRITE_SKELLETON_FILE", "sprite/race/skelleton/", 316, 120, "RType.java"));
		tags.add(plain("COLOR_SKIN", true, "RType.java. It has to name a collection in COLORS."));
		tags.add(plain("COLOR_LEG", true, "RType.java. It has to name a collection in COLORS."));
		tags.add(plain("NAMESET_FILE_FIRST", true, "RNames.java"));
		tags.add(plain("NAMESET_FILE_SURNAME", true, "RNames.java"));
		tags.add(leaf("OCCURRENCE", Kind.DECIMAL, false, "0.5", null, null, true, "RType.java, absent is 0.5"));
		tags.add(shaped("ADDONS", Kind.BLOCK_LIST, false, null, null, "RType.java, has ADDONS", new Shape(List.of(
				leaf("BELOW_HEAD", Kind.BOOL, true, null, null, null, true, "RType.java"),
				leaf("ADDON_INDEX", Kind.INT, true, null, 0.0, 8.0, false, "RAddon.java. Json.i does not enforce its maximum. The array only has indexes 0 to 7."),
				plain("COLOR", false, "RAddon.java, has COLOR. It names a collection in COLORS."),
				shaped("CONDITIONS", Kind.BLOCK, false, null, null, "RAddon.java, Lockable.push. The entries are the game's lock, and stay as written.", Shape.block(List.of()))), List.of(), 0, 0, 0)));
		tags.add(shaped("FACE", Kind.BLOCK_LIST, false, null, null, "RPortrait.java, has FACE. Absent means no portrait.", new Shape(List.of(
				new TagSpec("FRAMES", Kind.FRAMES, true, null, null, null, true, null, null, List.of(), "RaceFrameMaker.java, values FRAMES", Confidence.CODE_VERIFIED, new Sprite("sprite/race/face/", 416, 60, false)),
				leaf("FRAME_RANDOM", Kind.INT, false, null, 0.0, 16.0, false, "RPortraitFrame.java. Absent uses this frame's own number, modulo 16. Json.i does not enforce its maximum."),
				leaf("FRAME_OCCURRENCE", Kind.DECIMAL, false, "1", 0.0, 1.0, true, "RPortraitFrame.java"),
				leaf("OFF_X", Kind.INT, false, "0", -40.0, 40.0, false, "RPortraitFrame.java. Json.i does not enforce its maximum."),
				leaf("OFF_Y", Kind.INT, false, "0", -48.0, 48.0, false, "RPortraitFrame.java. Json.i does not enforce its maximum."),
				leaf("OFF_X_RANDOM", Kind.INT, false, "0", 0.0, 40.0, false, "RPortraitFrame.java. Json.i does not enforce its maximum."),
				leaf("OFF_Y_RANDOM", Kind.INT, false, "0", 0.0, 48.0, false, "RPortraitFrame.java. Json.i does not enforce its maximum."),
				shaped("CONDITIONS", Kind.BLOCK, false, null, null, "RPortraitFrame.java, Lockable.push. The entries are the game's lock, and stay as written.", Shape.block(List.of())),
				plain("COLOR", false, "RPortraitFrame.java, has COLOR"),
				leaf("OPACITY", Kind.INT, false, "255", 0.0, 256.0, false, "RPortraitFrame.java. Json.i does not enforce its maximum."),
				leaf("STAINS", Kind.BOOL, false, "true", null, null, true, "RPortraitFrame.java")), List.of(), 0, 0, 0)));
		return tags;
	}

	private static TagSpec crown(String key) {
		return shaped(key, Kind.BLOCK, true, null, null, "RCrown.java. Absent stops loading. One block, and a list is also accepted.", Shape.block(crownParts()));
	}

	private static TagSpec crownList(String key) {
		return shaped(key, Kind.BLOCK_LIST, true, null, null, "RCrown.java. Absent stops loading. A list, and one block is also accepted.", new Shape(crownParts(), List.of(), 0, 0, 0));
	}

	private static List<TagSpec> crownParts() {
		return List.of(
				new TagSpec("FILE", Kind.SPRITE, true, null, null, null, true, null, null, List.of(), "RCrown.java. The sheet is 104 wide and at least 36 tall, in rows of 36. A missing sheet is logged and skipped.", Confidence.CODE_VERIFIED, new Sprite("sprite/race/face/addon/", 104, 36, false, true)),
				leaf("OFFX", Kind.INT, true, null, -48.0, 48.0, false, "RCrown.java. Json.i does not enforce its maximum."),
				leaf("OFFY", Kind.INT, true, null, -48.0, 48.0, false, "RCrown.java. Json.i does not enforce its maximum."));
	}

	private static List<TagSpec> homeTags() {
		List<TagSpec> tags = new ArrayList<>();
		for (String clas : List.of("CITIZEN", "NOBLE", "SLAVE")) {
			tags.add(homeClass(clas));
		}
		return tags;
	}

	private static TagSpec homeClass(String key) {
		List<TagSpec> furniture = new ArrayList<>();
		furniture.add(furniture("BED_1x1_TOP", "1x1", 22));
		furniture.add(furniture("BED_1x1_BOTTOM", "1x1", 22));
		furniture.add(furniture("CARPET_COMBO", "combo", 72));
		furniture.add(furniture("CARPET_2x2", "2x2", 44));
		furniture.add(furniture("TABLE_COMBO", "combo", 72));
		furniture.add(furniture("NIGHTSTAND_1x1", "1x1", 22));
		furniture.add(furniture("STORAGE_1x1", "1x1", 22));
		furniture.add(furniture("CHAIR_1x1", "1x1", 22));
		furniture.add(furniture("NICKNACK_A_1x1", "1x1", 22));
		furniture.add(furniture("NICKNACK_A_ONTOP_1x1", "1x1", 22));
		furniture.add(furniture("NICKNACK_B_1x1", "1x1", 22));
		furniture.add(furniture("MAT_1x1", "1x1", 22));
		furniture.add(furniture("BED_MASTER_2x2", "2x2", 44));
		furniture.add(furniture("STATUE_2x2", "2x2", 44));
		furniture.add(shaped("FLOORS", Kind.BLOCK_LIST, false, null, null, "RaceHomeClass.java, has FLOORS", new Shape(List.of(
				map("RESOURCES", true, null, null, null, Reference.RESOURCES, HOME_RESOURCES),
				new TagSpec("FLOOR", Kind.KEY, true, null, null, null, true, Reference.FLOORS, null, List.of(), "RaceHomeClass.java", Confidence.CODE_VERIFIED)), List.of(), 0, 0, 0)));
		return shaped(key, Kind.BLOCK, true, null, null, "RaceHome.java, HCLASSES", Shape.block(furniture));
	}

	private static TagSpec furniture(String key, String kind, int tile) {
		Sprite sheets = new Sprite("sprite/game/" + kind + "/", tile, tile, true);
		return shaped(key, Kind.BLOCK_LIST, false, null, null, "RaceHomeClass.java, SheetData.java", new Shape(List.of(
				new TagSpec("FRAMES", Kind.FRAMES, true, null, null, null, true, null, null, List.of(), "Sheets.java", Confidence.CODE_VERIFIED, sheets),
				leaf("FPS", Kind.DECIMAL, false, "0", 0.0, 100000.0, true, "SheetData.java"),
				leaf("FPS_INTERVAL", Kind.DECIMAL, false, "1", 0.0, 1.0, true, "SheetData.java"),
				leaf("SHADOW_LENGTH", Kind.DECIMAL, false, "0", 0.0, 100.0, true, "SheetData.java"),
				leaf("SHADOW_HEIGHT", Kind.DECIMAL, false, "0", 0.0, 100.0, true, "SheetData.java"),
				leaf("CIRCULAR", Kind.BOOL, false, "false", null, null, true, "SheetData.java"),
				leaf("ROTATES", Kind.BOOL, false, "true", null, null, true, "SheetData.java"),
				leaf("TINT", Kind.BOOL, false, "true", null, null, true, "SheetData.java"),
				new TagSpec("COLOR", Kind.COLORS, false, null, null, null, true, null, null, List.of(), "SheetData.java, ColorImp.cols when COLOR is present", Confidence.CODE_VERIFIED),
				// GameSheets.java:123-130: each OVERWRITE block is read with SheetData(def, json) for the frame of the same number,
				// and anything it leaves out is the entry's own (SheetData.java:57-71). Vanilla homes give each a COLOR block.
				shaped("OVERWRITE", Kind.BLOCK_LIST, false, null, null, "GameSheets.java:123-130, SheetData.java:57-71. Block n changes frame n only.", new Shape(List.of(
						leaf("FPS", Kind.DECIMAL, false, "The entry's own", 0.0, 100000.0, true, "SheetData.java:59"),
						leaf("FPS_INTERVAL", Kind.DECIMAL, false, "The entry's own", 0.0, 1.0, true, "SheetData.java:60"),
						leaf("SHADOW_LENGTH", Kind.DECIMAL, false, "The entry's own", 0.0, 100.0, true, "SheetData.java:61"),
						leaf("SHADOW_HEIGHT", Kind.DECIMAL, false, "The entry's own", 0.0, 100.0, true, "SheetData.java:62"),
						leaf("CIRCULAR", Kind.BOOL, false, "The entry's own", null, null, true, "SheetData.java:63"),
						leaf("ROTATES", Kind.BOOL, false, "The entry's own", null, null, true, "SheetData.java:64"),
						leaf("TINT", Kind.BOOL, false, "true", null, null, true, "SheetData.java:67. Without COLOR, true tints the frame randomly and false keeps the entry's colours."),
						new TagSpec("COLOR", Kind.COLORS, false, null, null, null, true, null, null, List.of(), "SheetData.java:65-66, ColorImp.cols", Confidence.CODE_VERIFIED)),
						List.of(), 0, 0, 0)),
				map("RESOURCES", true, null, null, null, Reference.RESOURCES, HOME_RESOURCES)), List.of(), 0, 0, 0));
	}

	/** Every block in a home class opens RESOURCES, even an empty one. */
	private static final String HOME_RESOURCES = "RaceHomeClass.java:68-77, RaceHomeSheet.java:44. Every entry needs this block, which can be empty. Each amount is a whole number read with i from 1 to 15, and the maximum isn't enforced. A resource the game doesn't have is skipped.";

	private static List<TagSpec> bioTags(String absent, String absentEvidence) {
		List<TagSpec> tags = new ArrayList<>();
		for (String key : List.of("INFO_OTHER", "INFO_GENERAL", "INFO_TITLE", "INFO_GENERAL2", "INFO_GENERAL3",
				"HOME_NONE_WORK", "HOME_NONE", "HOME_NONE_SEARCH", "FRIEND", "FRIEND_ENEMY", "FRIEND_OTHER",
				"ORIGIN_NATIVE", "ORIGIN_IMMI", "ORIGIN_FREED", "ORIGIN_PAROLE", "ORIGIN_SOLDIER", "ORIGIN_INSANE",
				"HOME", "HOME_ALONE", "DIVISION", "DIVISION_RECRUIT", "DIVISION_NONE", "WORK_NOBLE", "WORK_EMPLOYED",
				"WORK_UNEMPLOYED", "WORK_LEISURE", "DREAMS", "DREAMS_CHILD", "DREAMS_CRIMINAL", "DREAMS_ENEMY",
				"DREAMS_RIOTEER", "DREAMS_INSANE", "DREAMS_SLAVE", "HAPPY", "HAPPY_SOSO", "HAPPY_NO", "FUNNY", "NOTHING")) {
			tags.add(rows(key, false, absent, 0, absentEvidence));
		}
		tags.add(new TagSpec("TRAIT", Kind.KEYED_TEXTS, true, null, null, null, true, null, null, List.of(), "BioLines.java:96-108. The block is required. Each trait's name holds lines, and one with none uses that trait's own lines.", Confidence.CODE_VERIFIED));
		tags.add(new TagSpec("STATS_MORE", Kind.KEYED_TEXTS, true, null, null, null, true, null, null, List.of(), "BioOpinionData.java, json STATS_MORE. Each stat names lines of text. Stat names are decided in code.", Confidence.CODE_VERIFIED));
		tags.add(new TagSpec("STATS_LESS", Kind.KEYED_TEXTS, true, null, null, null, true, null, null, List.of(), "BioOpinionData.java, json STATS_LESS. Each stat names lines of text. Stat names are decided in code.", Confidence.CODE_VERIFIED));
		return tags;
	}

	private static List<TagSpec> kingTags() {
		List<TagSpec> tags = new ArrayList<>();
		for (String key : List.of("GREETING_GOOD", "GREETING_BAD", "STANCE_UP", "STANCE_DOWN", "STANCE_WARNING", "PEACE_GOOD", "PEACE_BAD",
				"THREAT_NORMAL", "THREAT_VASSAL", "THREAT_ALLY", "WAR_NORMAL", "WAR_VASSAL", "WAR_ALLY", "WAR_JOIN_NORMAL", "WAR_JOIN_VASSAL", "WAR_JOIN_ALLY",
				"COALITION_NAME", "WAR_CAUSE_AGRESSION", "WAR_CAUSE_DEFEND", "RUMOUR", "NGO")) {
			tags.add(rows(key, true, null, 0, "KingMessages.java"));
		}
		return tags;
	}

	private static List<TagSpec> worldTags() {
		return List.of(
				rows("INTRO", true, null, 128, "RaceWorldInfo.java"),
				rows("NAMES", true, null, 512, "RaceWorldInfo.java"),
				rows("RULER_INTRO", true, null, 128, "RaceWorldInfo.java"),
				rows("RULER", true, null, 512, "RaceWorldInfo.java"));
	}

	private static List<TagSpec> raidTags() {
		List<TagSpec> tags = new ArrayList<>();
		tags.add(shaped("FIRST", Kind.BLOCK, true, null, null, "RaiderTextsRace.java", Shape.block(speech())));
		tags.add(shaped("REPEAT", Kind.BLOCK, true, null, null, "RaiderTextsRace.java", Shape.block(speech())));
		for (String key : List.of("REJECTED", "PAYED", "AFTER_RAID", "HELP", "HELP_DESTROYED", "HELP_FIGHT")) {
			tags.add(rows(key, true, null, 0, "RaiderTextsRace.java"));
		}
		return tags;
	}

	private static List<TagSpec> speech() {
		List<TagSpec> lines = new ArrayList<>();
		for (String key : List.of("GREETING", "INTROS", "BODIES", "ENDS")) {
			lines.add(rows(key, true, null, 0, "RaiderTextsRace.java"));
		}
		return lines;
	}

	private static List<TagSpec> touristText() {
		List<TagSpec> tags = new ArrayList<>();
		for (String key : List.of("RATING", "ATTRACTION", "SERVICE", "INN")) {
			tags.add(shaped(key, Kind.BLOCK, true, null, null, "Text.java", Shape.block(List.of(
					rows("BAD", true, null, 0, "Text.java"),
					rows("OK", true, null, 0, "Text.java"),
					rows("GOOD", true, null, 0, "Text.java")))));
		}
		return tags;
	}

	private static List<TagSpec> nameList() {
		return List.of(rows("NAMES", true, null, 4095, "RNames.java, texts 1 to 4095"));
	}

	private static TagSpec properties() {
		return shaped("PROPERTIES", Kind.BLOCK, true, null, null, "Physics.java", Shape.block(List.of(
				leaf("HEIGHT", Kind.INT, true, null, 0.0, 200.0, false, "Physics.java. Json.i does not enforce its maximum."),
				leaf("WIDTH", Kind.INT, true, null, 5.0, 15.0, false, "Physics.java. Json.i does not enforce its maximum."),
				leaf("CHILD_DAYS", Kind.INT, true, null, null, null, false, "Physics.java, i with no range"),
				leaf("BABY_DAYS", Kind.INT, true, null, null, null, false, "Physics.java, i with no range"),
				leaf("CORPSE_DECAY", Kind.BOOL, true, null, null, null, true, "Physics.java"),
				leaf("SLEEPS", Kind.BOOL, true, null, null, null, true, "Physics.java"),
				leaf("SLAVE_PRICE", Kind.DECIMAL, true, null, 0.0, 2147483647.0, true, "Physics.java, d up to Integer.MAX_VALUE"),
				leaf("SLAVE_PRICE_RECOVERY", Kind.DECIMAL, true, null, 0.0, 10.0, true, "Physics.java"),
				leaf("RAID_MERCINARY", Kind.DECIMAL, true, null, 0.0, 100000.0, true, "Physics.java"))));
	}

	private static TagSpec tourist() {
		return shaped("TOURIST", Kind.BLOCK, false, "No tourists: how often 0, credits 0", null, "TourismRace.java:31-44, has TOURIST. Absent, OCCURENCE and CREDITS are 0.", Shape.block(List.of(
				leaf("OCCURENCE", Kind.DECIMAL, true, null, 0.0, 100000.0, true, "TourismRace.java"),
				leaf("CREDITS", Kind.DECIMAL, true, null, 0.0, 100000.0, true, "TourismRace.java"),
				plain("TOURIST_TEXT_FILE", true, "TourismRace.java"))));
	}

	private static TagSpec preferred() {
		return shaped("PREFERRED", Kind.BLOCK, true, null, null, "RacePreferrence.java", Shape.block(List.of(
				new TagSpec("FOOD", Kind.KEY_LIST, true, null, null, null, true, Reference.EDIBLES, null, List.of(), "RacePreferrence.java. * is allowed.", Confidence.CODE_VERIFIED),
				new TagSpec("DRINK", Kind.KEY_LIST, true, null, null, null, true, Reference.DRINKABLES, null, List.of(), "RacePreferrence.java. * is allowed.", Confidence.CODE_VERIFIED),
				shaped("CRIME", Kind.KEYED_BLOCKS, false, null, null, "RacePreferrence.java. Crime names are decided in code.", Shape.keyed(List.of(
						leaf("FREEDOM", Kind.DECIMAL, false, null, 0.0, 1.0, true, "RacePreferrence.java, dTry"),
						leaf("LAW", Kind.DECIMAL, false, null, 0.0, 1.0, true, "RacePreferrence.java, dTry")), 0)),
				map("PUNISHMENT", false, null, 0, 1, Reference.PUNISHMENTS, "RacePreferrence.java:121-128. The names are fixed in CRIME_PUNISHMENTS.java:54-60, and one the game doesn't have is skipped."),
				map("STRUCTURE", false, null, 0, 1, Reference.STRUCTURES, "RacePreferrence.java:131-134, BUILDING_PREFS readFill. The names are mountain, outdoors, mud and each structure file (BUILDING_PREFS.java:28-47)."),
				map("ROAD", false, null, 0, 1, Reference.ROADS, "RacePreferrence.java:137-143. Only a floor whose file has a ROAD block is a road (Floors.java:160-174,579)."),
				map("WORK", false, null, -10000, 10000, Reference.ROOMS, "RacePreferrence.java. A room that exists is not reported as a non-workplace, because workplaces aren't a folder."),
				map("POOL", false, null, 0, 1, Reference.POOL_ROOMS, "RacePreferrence.java:166-176, RoomsCreator.java:32-33. A pool's file name starts with POOL_. A room that isn't a pool is skipped, because Json.errorGet only builds a message. * is allowed."),
				map("RESOURCE_RPICE_MUL", false, null, 0, 100, Reference.RESOURCES, "RacePreferrence.java. The spelling is the game's."),
				map("RESOURCE_RPICE_CAP", false, null, 0, 1, Reference.RESOURCES, "RacePreferrence.java. The spelling is the game's."),
				map("OTHER_RACES", false, null, 0, 1, Reference.RACES, "RacePreferrence.java"),
				map("OTHER_RACES_REVERSE", false, null, 0, 1, Reference.RACES, "RacePreferrence.java"),
				map("WORLD_BUILDING", false, null, null, null, Reference.WORLD_BUILDINGS, "RDBuildings.java. The value's range isn't in that read."))));
	}

	private static TagSpec population() {
		return shaped("POPULATION", Kind.BLOCK, false, "MAX 1, growth 0.0001, every climate 0, every terrain 0", null, "RacePopulation.java, absent uses those", Shape.block(List.of(
				leaf("MAX", Kind.DECIMAL, true, null, 0.0, 1.0, true, "RacePopulation.java"),
				map("CLIMATE", false, "0 for every climate", 0, 1, Reference.CLIMATES, "RacePopulation.java:30, readFill 1. An absent block leaves every climate at 0 (RMAP.java:211-214,302)."),
				map("TERRAIN", false, "0 for every terrain", 0, 100, Reference.TERRAINS, "RacePopulation.java:35, readFill 100. An absent block leaves every terrain at 0 (RMAP.java:211-214,302)."),
				leaf("GROWTH", Kind.DECIMAL, true, null, 0.0001, 1.0, true, "RacePopulation.java"))));
	}

	private static TagSpec stats() {
		return shaped("STATS", Kind.KEYED_BLOCKS, true, null, null, "StatsJson.java, StandingDef.java. Stat names are decided in code.", Shape.keyed(List.of(
				leaf("MULTIPLIER", Kind.DECIMAL, false, "1", 0.0, 100000.0, true, "StatStanding.java:305"),
				leaf("EXPONENT", Kind.DECIMAL, false, "1", 0.01, 10.0, true, "StatStanding.java:306"),
				leaf("PRIO", Kind.DECIMAL, false, "The stat's own priority", 0.0, 100000.0, true, "StatStanding.java:307. Absent, the race keeps the stat's own priority (RaceStats.java:59-63)."),
				leaf("INVERTED", Kind.BOOL, false, "false", null, null, true, "StatStanding.java:308"),
				leaf("CHILD", Kind.BOOL, false, "false", null, null, true, "StatStanding.java:309"),
				leaf("DISMISS", Kind.BOOL, false, "false", null, null, true, "StatStanding.java:310"),
				standing("CITIZEN"),
				standing("NOBLE"),
				standing("SLAVE"),
				standing("OTHER")), 0));
	}

	/**
	 * A class's standing, keyed by the class's own name. The game reads it with d from 0 to 1000000, which
	 * blocks (StatStanding.java:337). The key comes from a field, so the range is checked in the cross rule.
	 */
	private static TagSpec standing(String clas) {
		return leaf(clas, Kind.DECIMAL, false, "0", null, null, true, "StatStanding.java:336-338, d from 0 to " + (long) MAX_STANDING + ". The range is checked beside the table, because the key is the class's name.");
	}

	private static TagSpec colorList(String key, boolean required, String evidence) {
		return shaped(key, Kind.BLOCK_LIST, required, null, null, evidence, new Shape(List.of(
				leaf("R", Kind.INT, true, null, 0.0, 511.0, false, "ColorImp.java. The game halves it, and Json.i does not enforce the maximum."),
				leaf("G", Kind.INT, true, null, 0.0, 511.0, false, "ColorImp.java"),
				leaf("B", Kind.INT, true, null, 0.0, 511.0, false, "ColorImp.java")), List.of(), 0, 0, 0));
	}

	private static TagSpec sheet(String key, String folder, int width, int height, String evidence) {
		return new TagSpec(key, Kind.SPRITE, true, null, null, null, true, null, null, List.of(), evidence + ". The width must match, and the height is a minimum (ComposerUtil.java:42-48).", Confidence.CODE_VERIFIED, new Sprite(folder, width, height, false));
	}

	/** A sheet the file may leave out. With none, there's no default to show, so it's only offered from the add list. */
	private static TagSpec optionalSheet(String key, String folder, int width, int height, String evidence) {
		return new TagSpec(key, Kind.SPRITE, false, null, null, null, true, null, null, List.of(), evidence + ". The width must match, and the height is a minimum (ComposerUtil.java:42-48).", Confidence.CODE_VERIFIED, new Sprite(folder, width, height, false));
	}

	private static TagSpec icon(String key, String evidence) {
		return new TagSpec(key, Kind.ICON, true, null, null, null, true, Reference.ICONS, null, List.of(), evidence, Confidence.CODE_VERIFIED);
	}

	private static boolean wholeAtLeast(String value, int least) {
		return value.matches("-?\\d+") && Long.parseLong(value) >= least;
	}

	private static void checkWhole(String key, String value, List<Finding> findings) {
		if (!value.matches("-?\\d+")) {
			findings.add(new Finding(key, Severity.ERROR, "The game reads this as a whole number."));
			return;
		}
		long number = Long.parseLong(value);
		if (key.startsWith("MILITARY_SUPPLY_USE.") && number < 0) {
			findings.add(new Finding(key, Severity.ERROR, "The lowest value the game accepts is 0 (ResSupplies.java)."));
		} else if (key.startsWith("MILITARY_SUPPLY_USE.") && number > 10000) {
			findings.add(new Finding(key, Severity.WARNING, "The game's stated maximum is 10000, but it doesn't enforce it, so this may behave oddly (ResSupplies.java)."));
		}
	}

	private static TagSpec keyedLists(String key, String evidence) {
		return new TagSpec(key, Kind.KEYED_LISTS, false, null, null, null, true, Reference.ROOMS, null, List.of(), evidence, Confidence.CODE_VERIFIED);
	}

	private static TagSpec map(String key, boolean required, String absent, Integer min, Integer max, Reference reference, String evidence) {
		Double low = min == null ? null : min.doubleValue();
		Double high = max == null ? null : max.doubleValue();
		return new TagSpec(key, Kind.DECIMAL_MAP, required, absent, low, high, true, reference, null, List.of(), evidence, Confidence.CODE_VERIFIED);
	}

	private static TagSpec names(String key, String evidence) {
		return new TagSpec(key, Kind.KEY_LIST, false, null, null, null, true, null, null, List.of(), evidence, Confidence.CODE_VERIFIED);
	}

	private static TagSpec plain(String key, boolean required, String evidence) {
		return leaf(key, Kind.PLAIN, required, null, null, null, true, evidence);
	}

	private static TagSpec line(String key, boolean required, String absent, String evidence) {
		return leaf(key, Kind.TEXT_LINE, required, absent, null, null, true, evidence);
	}

	private static TagSpec rows(String key, boolean required, String absent, int most, String evidence) {
		return shaped(key, Kind.TEXT_ROWS, required, absent, null, evidence, new Shape(List.of(), List.of(), most, 0, 0));
	}

	private static TagSpec leaf(String key, Kind kind, boolean required, String absent, Double min, Double max, boolean enforced, String evidence) {
		return new TagSpec(key, kind, required, absent, min, max, enforced, null, null, List.of(), evidence, Confidence.CODE_VERIFIED);
	}

	private static TagSpec shaped(String key, Kind kind, boolean required, String absent, Reference reference, String evidence, Shape shape) {
		return new TagSpec(key, kind, required, absent, null, null, true, reference, null, List.of(), evidence, Confidence.CODE_VERIFIED, null, shape);
	}
}
