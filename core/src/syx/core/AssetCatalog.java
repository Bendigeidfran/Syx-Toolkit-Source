package syx.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

import syx.core.ClassFileScanner.Call;
import syx.core.ClassFileScanner.Const;
import syx.core.ClassFileScanner.FieldRef;
import syx.core.ClassFileScanner.Local;
import syx.core.ClassFileScanner.New;
import syx.core.ClassFileScanner.Returned;
import syx.core.ClassFileScanner.ScannedClass;
import syx.core.ClassFileScanner.ScannedMethod;
import syx.core.ClassFileScanner.Value;
import syx.core.TagSpec.Confidence;
import syx.core.TagSpec.Kind;
import syx.core.TagSpec.Reference;
import syx.core.TagSpec.SubKey;

/**
 * The asset types a modder can add, and which of them are ready to use. Tag rules come from scanning
 * the loader in the user's own jar, plus one reviewed rule per shared helper. A type is only enabled
 * when every use of its data file is accounted for, so a partial form is never offered.
 */
public final class AssetCatalog {

	/** A type, with its schema if it's usable, or why it isn't. */
	public record Entry(AssetType type, AssetSchema schema, String unavailableReason) {
		public boolean available() {
			return schema != null;
		}
	}

	public static final String NOT_VERIFIED = "Tags not verified yet";

	private static final String JSON = "snake2d/util/file/Json";
	private static final String ANIMAL_LOADER = "settlement.entity.animal.AnimalSpecies";
	private static final String ANIMAL_CONSTRUCTOR =
			"(Ljava/lang/String;ILsnake2d/util/file/Json;Lsnake2d/util/file/Json;Lsnake2d/util/sets/KeyMap;)V";
	private static final String PATH = "init/paths/PATH";
	private static final String RESOURCE_LOADER = "init.resources.RESOURCE";
	private static final String RESOURCE_CONSTRUCTOR =
			"(Lsnake2d/util/sets/LISTE;Ljava/lang/String;Linit/paths/PATH;Linit/paths/PATH;Linit/paths/PATH;Linit/paths/PATH;Lsnake2d/util/sets/KeyMap;Lsnake2d/util/sets/KeyMap;)V";
	private static final String RESOURCE_SPRITE = "init/resources/Sprite";
	private static final String RESOURCE_LIST = "init/resources/RESOURCES$Data";
	private static final String CATEGORY = "CATEGORY_DEFAULT";
	/** RESOURCES.java:73 has room for this many categories, numbered from 0. */
	private static final int CATEGORY_SLOTS = 10;

	public static final AssetType ANIMAL = new AssetType("Animal", "animal", "animal", ANIMAL_LOADER);

	/** Types where a new file name is loaded. Fixed-name configs and folders with no loader aren't here. */
	private static final List<AssetType> TYPES = List.of(
			ANIMAL,
			new AssetType("Battle division type", "battle/divType", null, null),
			new AssetType("Battle formation", "battle/formation", null, null),
			new AssetType("Disease", "disease", "disease", null),
			new AssetType("Event", "event", "event", null),
			new AssetType("Player level", "player/level", "player/level", null),
			new AssetType("Player mode", "player/mode", "player/mode", PlayerSchemas.MODE_LOADER),
			new AssetType("Player title", "player/titles", "player/titles", null),
			new AssetType("Race", "race", "race", "init.race.Race", "", List.of("assets/init/race/", "assets/text/race/")),
			new AssetType("Race trait", "race/trait", "race/trait", null),
			new AssetType("World camp", "race/worldcamp", "race/worldcamp", null),
			new AssetType("Religion", "religion", "religion", null),
			new AssetType("Resource", "resource", "resource", RESOURCE_LOADER),
			new AssetType("Edible resource", "resource/edible", null, null),
			new AssetType("Drinkable resource", "resource/drinkable", null, null),
			new AssetType("Crop", "resource/growable", null, RoomSchemas.CROP_LOADER),
			new AssetType("Minable resource", "resource/minable", null, RoomSchemas.MINABLE_LOADER),
			new AssetType("Resource supply", "resource/supply", null, null),
			new AssetType("Work equipment", "resource/work", null, null),
			new AssetType("Room", "room", "room", null),
			new AssetType("Farm", "room", "room", RoomSchemas.FARM_LOADER, "FARM_", List.of("assets/init/room/FARM_*", "assets/init/room/")),
			new AssetType("Mine", "room", "room", RoomSchemas.MINE_LOADER, "MINE_", List.of("assets/init/room/MINE_*", "assets/init/room/")),
			new AssetType("Refiner", "room", "room", RoomSchemas.REFINER_LOADER, "REFINER_", List.of("assets/init/room/REFINER_*", "assets/init/room/")),
			new AssetType("Workshop", "room", "room", RoomSchemas.WORKSHOP_LOADER, "WORKSHOP_", List.of("assets/init/room/WORKSHOP_*", "assets/init/room/")),
			new AssetType("Pasture", "room", "room", RoomSchemas.PASTURE_LOADER, "PASTURE_", List.of("assets/init/room/PASTURE_*", "assets/init/room/")),
			new AssetType("Fishery", "room", "room", RoomSchemas.FISHERY_LOADER, "FISHERY_", List.of("assets/init/room/FISHERY_*", "assets/init/room/")),
			new AssetType("Orchard", "room", "room", RoomSchemas.ORCHARD_LOADER, "ORCHARD_", List.of("assets/init/room/ORCHARD_*", "assets/init/room/")),
			new AssetType("Hunter", "room", "room", RoomSchemas.HUNTER_LOADER, "HUNTER_", List.of("assets/init/room/HUNTER_*", "assets/init/room/")),
			new AssetType("Temple", "room", "room", RoomSchemas.TEMPLE_LOADER, "TEMPLE_", List.of("assets/init/room/TEMPLE_*", "assets/init/room/")),
			new AssetType("Shrine", "room", "room", RoomSchemas.SHRINE_LOADER, "SHRINE_", List.of("assets/init/room/SHRINE_*", "assets/init/room/")),
			new AssetType("Tavern", "room", "room", RoomSchemas.TAVERN_LOADER, "TAVERN_", List.of("assets/init/room/TAVERN_*", "assets/init/room/")),
			new AssetType("Eatery", "room", "room", RoomSchemas.EATERY_LOADER, "EATERY_", List.of("assets/init/room/EATERY_*", "assets/init/room/")),
			new AssetType("Canteen", "room", "room", RoomSchemas.CANTEEN_LOADER, "CANTEEN_", List.of("assets/init/room/CANTEEN_*", "assets/init/room/")),
			new AssetType("Well", "room", "room", RoomSchemas.WELL_LOADER, "WELL_", List.of("assets/init/room/WELL_*", "assets/init/room/")),
			new AssetType("Bath", "room", "room", RoomSchemas.BATH_LOADER, "BATH_", List.of("assets/init/room/BATH_*", "assets/init/room/")),
			new AssetType("Lavatory", "room", "room", RoomSchemas.LAVATORY_LOADER, "LAVATORY_", List.of("assets/init/room/LAVATORY_*", "assets/init/room/")),
			new AssetType("Barber", "room", "room", RoomSchemas.BARBER_LOADER, "BARBER_", List.of("assets/init/room/BARBER_*", "assets/init/room/")),
			new AssetType("Physician", "room", "room", RoomSchemas.PHYSICIAN_LOADER, "PHYSICIAN_", List.of("assets/init/room/PHYSICIAN_*", "assets/init/room/")),
			new AssetType("Pleasure", "room", "room", RoomSchemas.PLEASURE_LOADER, "PLEASURE_", List.of("assets/init/room/PLEASURE_*", "assets/init/room/")),
			new AssetType("Market", "room", "room", RoomSchemas.MARKET_LOADER, "MARKET_", List.of("assets/init/room/MARKET_*", "assets/init/room/")),
			new AssetType("Stage", "room", "room", RoomSchemas.STAGE_LOADER, "STAGE_", List.of("assets/init/room/STAGE_*", "assets/init/room/")),
			new AssetType("Speaker", "room", "room", RoomSchemas.SPEAKER_LOADER, "SPEAKER_", List.of("assets/init/room/SPEAKER_*", "assets/init/room/")),
			new AssetType("Fight pit", "room", "room", RoomSchemas.FIGHTPIT_LOADER, "FIGHTPIT_", List.of("assets/init/room/FIGHTPIT_*", "assets/init/room/")),
			new AssetType("Grand arena", "room", "room", RoomSchemas.ARENA_LOADER, "ARENAG_", List.of("assets/init/room/ARENAG_*", "assets/init/room/")),
			AssetType.fixedRoom("Court", RoomSchemas.COURT_LOADER, "_COURT"),
			AssetType.fixedRoom("Prison", RoomSchemas.PRISON_LOADER, "_PRISON"),
			AssetType.fixedRoom("Stockade", RoomSchemas.STOCKADE_LOADER, "_STOCKADE"),
			AssetType.fixedRoom("Guard", RoomSchemas.GUARD_LOADER, "_GUARD"),
			AssetType.fixedRoom("Police", RoomSchemas.POLICE_LOADER, "_POLICE"),
			AssetType.fixedRoom("Stocks", RoomSchemas.STOCKS_LOADER, "_STOCKS"),
			AssetType.fixedRoom("Execution", RoomSchemas.EXECUTION_LOADER, "_EXECUTION"),
			new AssetType("Monument", "room", "room", RoomSchemas.MONUMENT_LOADER, "MONUMENT_", List.of("assets/init/room/MONUMENT_*", "assets/init/room/")),
			new AssetType("Fence", "settlement/fence", "settlement/fence", null),
			new AssetType("Floor", "settlement/floor", "settlement/floor", null),
			new AssetType("Fortification", "settlement/fortification", "settlement/fortification", null),
			new AssetType("Structure", "settlement/structure", "settlement/structure", null),
			new AssetType("Bonus stat", "stats/bonus", null, null),
			new AssetType("Loyalty stat", "stats/loyalty", null, null),
			new AssetType("Damage type", "stats/damage", "stats/damage", null),
			new AssetType("Dynamic boost", "stats/dynamicBoost", null, null),
			new AssetType("Civic equipment", "stats/equip/civic", null, null),
			new AssetType("Battle equipment", "stats/equip/battle", null, null),
			new AssetType("Ranged equipment", "stats/equip/ranged", null, null),
			new AssetType("Need", "stats/need", "stats/need", null),
			new AssetType("Tech tree", "tech", "tech", null),
			new AssetType("World building", "world/building", "world/building", null),
			new AssetType("World point", "world/point", "world/point", null));

	private AssetCatalog() {
	}

	/** Every type, in display order, with what the user's own jar says about it. */
	public static List<Entry> load(Path jar) {
		try (ClassFileScanner scanner = ClassFileScanner.open(jar)) {
			return load(scanner, HelperRules.standard());
		} catch (IOException e) {
			List<Entry> entries = new ArrayList<>();
			for (AssetType type : TYPES) {
				entries.add(new Entry(type, null, "The game's jar couldn't be read: " + e.getMessage()));
			}
			return entries;
		}
	}

	/** Lets a test prove the gate holds when a rule is missing. */
	public static List<Entry> loadWithoutHelper(Path jar, String owner, String name) throws IOException {
		try (ClassFileScanner scanner = ClassFileScanner.open(jar)) {
			return load(scanner, HelperRules.standard().without(owner, name));
		}
	}

	private static List<Entry> load(ClassFileScanner scanner, HelperRules rules) {
		List<Entry> entries = new ArrayList<>();
		for (AssetType type : TYPES) {
			entries.add(type.loader() == null ? new Entry(type, null, NOT_VERIFIED) : entry(type, scanner, rules));
		}
		return entries;
	}

	private static Entry entry(AssetType type, ClassFileScanner scanner, HelperRules rules) {
		try {
			List<String> unresolved = new ArrayList<>();
			AssetSchema schema = RoomSchemas.handles(type.loader()) ? RoomSchemas.schema(type.loader(), scanner, unresolved)
					: "init.race.Race".equals(type.loader()) ? RaceSchemas.schema(scanner, unresolved)
					: PlayerSchemas.MODE_LOADER.equals(type.loader()) ? PlayerSchemas.modeSchema(scanner, unresolved)
					: schema(LOADERS.get(type.loader()), scanner, rules, unresolved);
			if (schema != null && ANIMAL_LOADER.equals(type.loader())) {
				schema = schema.withSounds(List.of(SoundEntries.ANIMAL_CALL));
			}
			// An override's room class names its one file, such as _COURT (court/ROOM_COURT.java:33).
			if (schema != null && type.isOverride() && !scanner.mentions(type.loader().replace('.', '/'), type.fileName(type.fixedName()))) {
				unresolved.add(type.loader() + " no longer builds the room from " + type.fileName(type.fixedName()) + ".");
			}
			if (schema != null) {
				for (SoundEntries.Slot slot : schema.sounds()) {
					if (!scanner.mentions(slot.readerClass(), slot.prefix())) {
						unresolved.add(slot.readerClass() + " no longer looks up the sound " + slot.prefix() + ".");
					}
				}
			}
			if (!unresolved.isEmpty()) {
				return new Entry(type, null, "This game version's loader doesn't match what was reviewed: " + unresolved.get(0));
			}
			return new Entry(type, schema, null);
		} catch (ClassFileScanner.ScanException | RuntimeException e) {
			return new Entry(type, null, "The game's loader couldn't be read: " + e.getMessage());
		}
	}

	/** Where a loader gets a file from: a parameter it's given, or opened by itself from a PATH parameter. */
	private record FileSource(int parameter, boolean opened) {
		static FileSource given(int parameter) {
			return new FileSource(parameter, false);
		}

		static FileSource openedFrom(int pathParameter) {
			return new FileSource(pathParameter, true);
		}
	}

	/** A file found in a constructor, as the variable or the new object that holds it. */
	private record LocatedFile(int slot, int site) {
		boolean is(Value value) {
			return value instanceof Local local && local.slot() == slot || value instanceof New made && made.site() == site;
		}

		boolean usedBy(Call call) {
			return is(call.receiver()) || call.args().stream().anyMatch(this::is);
		}
	}

	private interface SizeFinder {
		int[] find(ScannedClass loader, ScannedMethod constructor, ClassFileScanner scanner, List<String> unresolved) throws ClassFileScanner.ScanException;
	}

	/** A tag naming an image, and where the game's required size for it is found. */
	private record SpriteSource(String key, String folder, SizeFinder finder) {
	}

	/** Anything else that must still hold for a loader's rules to be right. */
	private interface ExtraCheck {
		void check(ClassFileScanner scanner, Map<String, TagSpec> tags, List<String> unresolved) throws ClassFileScanner.ScanException;
	}

	/**
	 * What differs between loaders. Everything else about reading one is shared.
	 *
	 * @param needed tags it must still read, or the type turns off
	 * @param refine what the scan can't tell about a tag, such as which editor it needs
	 * @param spawnBlocks blocks that each need a value above 0 for the asset to ever appear
	 */
	private record LoaderSpec(String className, String constructor, FileSource data, FileSource text, List<String> needed,
			UnaryOperator<TagSpec> refine, List<SpriteSource> sprites, ExtraCheck extra, List<String> spawnBlocks, List<String> notes) {
	}

	private static final LoaderSpec ANIMAL_SPEC = new LoaderSpec(ANIMAL_LOADER, ANIMAL_CONSTRUCTOR,
			FileSource.given(2), FileSource.given(3), List.of("ICON", "SPRITE", "RESOURCES", "RESOURCE_AMOUNT"),
			AssetCatalog::animalRefinement, List.of(new SpriteSource("SPRITE", "sprite/animal/", AssetCatalog::initInConstructor)),
			(scanner, tags, unresolved) -> { }, List.of("CLIMATE", "TERRAIN"),
			List.of("A new animal has no call sound unless a mod defines ANIMAL_CALL_<NAME>. The game then uses silence (SoundRaces.java)."));

	private static final LoaderSpec RESOURCE_SPEC = new LoaderSpec(RESOURCE_LOADER, RESOURCE_CONSTRUCTOR,
			FileSource.openedFrom(2), FileSource.openedFrom(3), List.of("DEGRADE_RATE", "COLOR", "MINIMAP_COLOR", "CATEGORY_DEFAULT", "ICON", "SPRITE", "SPRITE_DEBRIS"),
			AssetCatalog::resourceRefinement,
			List.of(new SpriteSource("SPRITE", "sprite/resource/", AssetCatalog::resourceSheet),
					new SpriteSource("SPRITE_DEBRIS", "sprite/resource/debris/", AssetCatalog::debrisSheet)),
			AssetCatalog::checkCategoryReads, List.of(),
			List.of("The game holds at most 64 resources in one category and 128 in all, counting every enabled mod, and stops loading beyond that (RESOURCES.java:73,104).",
					"Being edible, drinkable, growable or minable comes from a separate file with the same name in that folder (ResGEat.java:14-22)."));

	private static final Map<String, LoaderSpec> LOADERS = Map.of(ANIMAL_LOADER, ANIMAL_SPEC, RESOURCE_LOADER, RESOURCE_SPEC);

	/**
	 * Builds a schema from a loader's constructor. Everything it does with its data and text files must
	 * match a rule, or it's added to unresolved, and the type stays off.
	 */
	private static AssetSchema schema(LoaderSpec spec, ClassFileScanner scanner, HelperRules rules, List<String> unresolved) throws ClassFileScanner.ScanException {
		ScannedClass loader = scanner.scan(spec.className());
		if (loader == null) {
			unresolved.add("the class " + spec.className() + " is missing.");
			return null;
		}
		ScannedMethod constructor = loader.method("<init>", spec.constructor());
		if (constructor == null) {
			unresolved.add("its constructor has changed.");
			return null;
		}
		LocatedFile data = locate(spec.data(), spec.constructor(), constructor, unresolved);
		LocatedFile text = locate(spec.text(), spec.constructor(), constructor, unresolved);
		if (data == null || text == null) {
			return null;
		}
		for (LocatedFile file : List.of(data, text)) {
			if (constructor.reassignedSlots().contains(file.slot())) {
				unresolved.add("it reassigns a file it was given.");
			}
		}
		for (ClassFileScanner.Escape escape : constructor.escapes()) {
			for (LocatedFile file : List.of(data, text)) {
				if (file.slot() >= 0 && escape.slot() == file.slot() || file.site() >= 0 && escape.site() == file.site()) {
					unresolved.add("it stores a file it was given, at " + where(loader, escape.line()) + ".");
				}
			}
		}

		Map<String, TagSpec> tags = new LinkedHashMap<>();
		List<TagSpec> textTags = new ArrayList<>();
		for (Call call : constructor.calls()) {
			boolean onJson = call.owner().equals(JSON);
			// The constructor call that opens one of the files has already been checked.
			if (onJson && call.name().equals("<init>") && (spec.data().opened() && data.is(call.receiver()) || spec.text().opened() && text.is(call.receiver()))) {
				continue;
			}
			boolean usesData = data.usedBy(call);
			boolean usesText = text.usedBy(call);
			if (onJson && !usesData) {
				unresolved.add("it reads a file in a way that wasn't reviewed, at " + where(loader, call.line()) + ".");
			} else if (onJson) {
				add(tags, jsonRead(call, loader, unresolved));
			} else if (usesData) {
				HelperRules.Rule rule = rules.find(call);
				if (rule == null) {
					unresolved.add("it passes its data file to " + call.owner() + "." + call.name() + ", at " + where(loader, call.line()) + ".");
				} else {
					add(tags, rule.tag(call, where(loader, call.line()), scanner, unresolved));
				}
			} else if (usesText) {
				HelperRules.Rule rule = rules.find(call);
				if (rule == null || !rule.isTextRule() || call.conditional()) {
					unresolved.add("it passes its text file to " + call.owner() + "." + call.name() + ", at " + where(loader, call.line()) + ".");
				} else {
					textTags.addAll(HelperRules.infoTags());
				}
			}
		}
		if (textTags.isEmpty()) {
			unresolved.add("it no longer reads a text file the expected way.");
		}
		for (String needed : spec.needed()) {
			if (!tags.containsKey(needed)) {
				unresolved.add("it no longer reads " + needed + ".");
			}
		}
		spec.extra().check(scanner, tags, unresolved);
		for (SpriteSource source : spec.sprites()) {
			int[] size = source.finder().find(loader, constructor, scanner, unresolved);
			TagSpec tag = tags.get(source.key());
			if (size != null && tag != null) {
				tags.put(source.key(), new TagSpec(tag.key(), Kind.SPRITE, tag.required(), tag.gameDefault(), tag.min(), tag.max(), tag.maxEnforced(), null, tag.linkedTo(),
						tag.subKeys(), tag.evidence(), tag.confidence(), new TagSpec.Sprite(source.folder(), size[0], size[1])));
			}
		}

		List<TagSpec> dataTags = new ArrayList<>();
		for (TagSpec tag : tags.values()) {
			dataTags.add(spec.refine().apply(tag));
		}
		return new AssetSchema(dataTags, textTags, spec.spawnBlocks(), spec.notes());
	}

	/** Finds a file in a constructor. One opened by the loader must be opened once, from the expected PATH. */
	private static LocatedFile locate(FileSource source, String descriptor, ScannedMethod constructor, List<String> unresolved) {
		int slot = ClassFileScanner.slotOfParameter(descriptor, source.parameter(), false);
		if (!source.opened()) {
			return new LocatedFile(slot, -1);
		}
		if (constructor.reassignedSlots().contains(slot)) {
			unresolved.add("it reassigns a folder it was given.");
		}
		LocatedFile found = null;
		for (Call call : constructor.calls()) {
			boolean opens = call.owner().equals(JSON) && call.name().equals("<init>") && call.args().size() == 1
					&& call.args().get(0) instanceof Returned from && from.owner().equals(PATH) && from.name().equals("gets")
					&& from.receiver() instanceof Local local && local.slot() == slot;
			if (opens) {
				if (found != null || call.conditional() || !(call.receiver() instanceof New made)) {
					unresolved.add("it opens a file in a way that wasn't reviewed, at line " + call.line() + ".");
					return null;
				}
				found = new LocatedFile(-1, made.site());
			}
		}
		if (found == null) {
			unresolved.add("it no longer opens a file the expected way.");
		}
		return found;
	}

	/** Animal: new IInit(path, width, height), in the constructor (ComposerUtil.java:43-48). */
	private static int[] initInConstructor(ScannedClass loader, ScannedMethod constructor, ClassFileScanner scanner, List<String> unresolved) {
		for (Call call : constructor.calls()) {
			if (call.owner().equals("util/spritecomposer/ComposerThings$IInit") && call.name().equals("<init>")) {
				Number width = number(call.args(), 1);
				Number height = number(call.args(), 2);
				if (width == null || height == null) {
					unresolved.add("its sprite size isn't a fixed number.");
					return null;
				}
				return new int[] { width.intValue(), height.intValue() };
			}
		}
		unresolved.add("its sprite size couldn't be found.");
		return null;
	}

	/** Resource: new Sprite(path), whose constructor makes the sheet (Sprite.java:22). */
	private static int[] resourceSheet(ScannedClass loader, ScannedMethod constructor, ClassFileScanner scanner, List<String> unresolved) throws ClassFileScanner.ScanException {
		boolean made = constructor.calls().stream().anyMatch(c -> c.owner().equals(RESOURCE_SPRITE) && c.name().equals("<init>"));
		ScannedClass sprite = scanner.scan(RESOURCE_SPRITE);
		ScannedMethod method = sprite == null ? null : sprite.method("<init>", "(Ljava/nio/file/Path;)V");
		if (!made || method == null) {
			unresolved.add("it no longer makes its sprite the expected way.");
			return null;
		}
		return sheetSize(method, scanner, "its sprite", unresolved);
	}

	/** Resource: an anonymous tile sheet in the constructor itself (RESOURCE.java:106). */
	private static int[] debrisSheet(ScannedClass loader, ScannedMethod constructor, ClassFileScanner scanner, List<String> unresolved) throws ClassFileScanner.ScanException {
		return sheetSize(constructor, scanner, "its debris sprite", unresolved);
	}

	/** The size given to an anonymous ITileSheet. There must be exactly one that takes a size. */
	static int[] sheetSize(ScannedMethod method, ClassFileScanner scanner, String what, List<String> unresolved) throws ClassFileScanner.ScanException {
		int[] size = null;
		for (Call call : method.calls()) {
			if (call.name().equals("<init>") && call.owner().contains("$") && call.args().size() >= 2) {
				ScannedClass sheet = scanner.scan(call.owner());
				if (sheet != null && "util/spritecomposer/ComposerThings$ITileSheet".equals(sheet.superName())) {
					Number width = number(call.args(), call.args().size() - 2);
					Number height = number(call.args(), call.args().size() - 1);
					if (size != null || width == null || height == null) {
						unresolved.add(what + "'s size isn't a single fixed number.");
						return null;
					}
					size = new int[] { width.intValue(), height.intValue() };
				}
			}
		}
		if (size == null) {
			unresolved.add(what + "'s size couldn't be found.");
		}
		return size;
	}

	/**
	 * RESOURCE states a maximum of 3, but it isn't enforced and vanilla uses 4. The real limit is in
	 * RESOURCES, which puts each file in an array of 10 by this number, so 10 or more crashes on load.
	 * Both reads are checked, because the rule below rests on them.
	 */
	private static void checkCategoryReads(ClassFileScanner scanner, Map<String, TagSpec> tags, List<String> unresolved) throws ClassFileScanner.ScanException {
		TagSpec category = tags.get(CATEGORY);
		if (category == null) {
			return;
		}
		if (!Double.valueOf(0).equals(category.min()) || !Double.valueOf(3).equals(category.max())) {
			unresolved.add("its " + CATEGORY + " limits have changed.");
		}
		ScannedClass list = scanner.scan(RESOURCE_LIST);
		boolean found = false;
		if (list != null) {
			for (ScannedMethod method : list.methods()) {
				if (method.name().equals("<init>")) {
					for (Call call : method.calls()) {
						if (call.owner().equals(JSON) && !call.name().equals("<init>")) {
							boolean expected = call.name().equals("i") && call.descriptor().equals("(Ljava/lang/String;II)I")
									&& call.args().get(0) instanceof Const key && CATEGORY.equals(key.value())
									&& number(call.args(), 1) != null && number(call.args(), 1).intValue() == 0
									&& number(call.args(), 2) != null && number(call.args(), 2).intValue() == CATEGORY_SLOTS;
							if (!expected) {
								unresolved.add("the list of resources reads its files in a way that wasn't reviewed.");
							}
							found |= expected;
						}
					}
				}
			}
		}
		if (!found) {
			unresolved.add("the list of resources no longer reads " + CATEGORY + " the expected way.");
		}
	}

	private static TagSpec resourceRefinement(TagSpec tag) {
		if (!tag.key().equals(CATEGORY)) {
			return tag;
		}
		return new TagSpec(tag.key(), tag.kind(), tag.required(), tag.gameDefault(), tag.min(), (double) (CATEGORY_SLOTS - 1), true, tag.reference(), tag.linkedTo(),
				tag.subKeys(), tag.evidence() + ", RESOURCES.java:73,80-81", tag.confidence());
	}

	private static void add(Map<String, TagSpec> tags, TagSpec tag) {
		if (tag != null) {
			tags.put(tag.key(), tag);
		}
	}

	/** What kind of editor an Animal tag needs. Rules about values are still the scanned ones. */
	private static TagSpec animalRefinement(TagSpec tag) {
		if (!tag.key().equals("RESOURCE_AMOUNT")) {
			return tag;
		}
		return new TagSpec(tag.key(), Kind.LINKED_DECIMALS, tag.required(), tag.gameDefault(), tag.min(), tag.max(), tag.maxEnforced(), tag.reference(), "RESOURCES",
				tag.subKeys(), tag.evidence(), tag.confidence());
	}

	/** The tags INFO reads, shared by everything with a text file. */
	static List<TagSpec> infoTagsFor() {
		return HelperRules.infoTags();
	}

	/** Turns a direct read of the file into a tag, using what the game's Json methods do. */
	private static TagSpec jsonRead(Call call, ScannedClass loader, List<String> unresolved) {
		String at = where(loader, call.line());
		if (call.conditional()) {
			unresolved.add("it only sometimes reads a tag, at " + at + ".");
			return null;
		}
		List<Value> args = call.args();
		if (args.isEmpty() || !(args.get(0) instanceof Const first) || !(first.value() instanceof String key)) {
			unresolved.add("it reads a tag whose name is built while the game runs, at " + at + ".");
			return null;
		}
		Number a = number(args, 1);
		Number b = number(args, 2);
		Number c = number(args, 3);
		switch (call.name() + call.descriptor()) {
		case "bool(Ljava/lang/String;)Z":
			return tag(key, Kind.BOOL, true, null, null, null, true, at);
		case "bool(Ljava/lang/String;Z)Z":
			if (a != null) {
				return tag(key, Kind.BOOL, false, a.intValue() != 0 ? "true" : "false", null, null, true, at);
			}
			break;
		case "i(Ljava/lang/String;)I":
			return tag(key, Kind.INT, true, null, null, null, true, at);
		case "i(Ljava/lang/String;II)I":
			if (a != null && b != null) {
				return tag(key, Kind.INT, true, null, a.doubleValue(), b.doubleValue(), false, at);
			}
			break;
		case "i(Ljava/lang/String;III)I":
			if (a != null && b != null && c != null) {
				return tag(key, Kind.INT, false, String.valueOf(c.intValue()), a.doubleValue(), b.doubleValue(), false, at);
			}
			break;
		case "d(Ljava/lang/String;)D":
			return tag(key, Kind.DECIMAL, true, null, null, null, true, at);
		case "d(Ljava/lang/String;DD)D":
			if (a != null && b != null) {
				return tag(key, Kind.DECIMAL, true, null, a.doubleValue(), b.doubleValue(), true, at);
			}
			break;
		case "dTry(Ljava/lang/String;DDD)D":
			if (a != null && b != null && c != null) {
				return tag(key, Kind.DECIMAL, false, String.valueOf(c.doubleValue()), a.doubleValue(), b.doubleValue(), true, at);
			}
			break;
		case "ds(Ljava/lang/String;I)[D":
			return tag(key, Kind.LINKED_DECIMALS, true, null, null, null, true, at);
		case "value(Ljava/lang/String;)Ljava/lang/String;":
			return tag(key, Kind.PLAIN, true, null, null, null, true, at);
		case "text(Ljava/lang/String;)Ljava/lang/String;":
			return tag(key, Kind.TEXT_LINE, true, null, null, null, true, at);
		default:
			break;
		}
		unresolved.add("it reads " + key + " with " + call.name() + ", which wasn't reviewed, at " + at + ".");
		return null;
	}

	private static TagSpec tag(String key, Kind kind, boolean required, String gameDefault, Double min, Double max, boolean maxEnforced, String at) {
		return new TagSpec(key, kind, required, gameDefault, min, max, maxEnforced, null, null, List.of(), at, Confidence.CODE_VERIFIED);
	}

	private static Number number(List<Value> args, int index) {
		return index < args.size() && args.get(index) instanceof Const c && c.value() instanceof Number n ? n : null;
	}

	private static boolean uses(Call call, int slot) {
		if (call.receiver() instanceof Local local && local.slot() == slot) {
			return true;
		}
		return call.args().stream().anyMatch(a -> a instanceof Local local && local.slot() == slot);
	}

	private static String where(ScannedClass loader, int line) {
		String file = loader.sourceFile() == null ? loader.name() : loader.sourceFile();
		return line < 0 ? file : file + ":" + line;
	}

	/** One reviewed rule for each shared helper that reads tags on a loader's behalf. */
	private static final class HelperRules {

		interface TagMaker {
			TagSpec make(Call call, String at, ClassFileScanner scanner, List<String> unresolved) throws ClassFileScanner.ScanException;
		}

		record Rule(String owner, String name, String descriptor, boolean isTextRule, TagMaker maker) {
			TagSpec tag(Call call, String at, ClassFileScanner scanner, List<String> unresolved) throws ClassFileScanner.ScanException {
				if (call.conditional()) {
					unresolved.add("it only sometimes reads a tag, at " + at + ".");
					return null;
				}
				return maker.make(call, at, scanner, unresolved);
			}
		}

		private record MapRule(String key, Reference reference, String evidence) {
		}

		private final List<Rule> rules;
		private final boolean allowKJson;

		HelperRules(List<Rule> rules, boolean allowKJson) {
			this.rules = rules;
			this.allowKJson = allowKJson;
		}

		Rule find(Call call) {
			for (Rule rule : rules) {
				if (rule.owner().equals(call.owner()) && rule.name().equals(call.name()) && rule.descriptor().equals(call.descriptor())) {
					return rule;
				}
			}
			// A KJson block is an anonymous class in the loader, so it's recognised by what it extends.
			if (allowKJson && call.name().equals("<init>") && call.owner().contains("$") && call.descriptor().contains("Lutil/keymap/RMAP;")) {
				return new Rule(call.owner(), call.name(), call.descriptor(), false, HelperRules::kJson);
			}
			return null;
		}

		HelperRules without(String owner, String name) {
			return new HelperRules(rules.stream().filter(r -> !(r.owner().equals(owner) && r.name().equals(name))).toList(), !owner.equals("KJson"));
		}

		static HelperRules standard() {
			return new HelperRules(List.of(
					new Rule("init/sprite/UI/Icons", "get", "(Lsnake2d/util/file/Json;)Linit/sprite/UI/Icon;", false,
							(c, at, s, u) -> {
								// What an icon holds is read in its own class, so a change there has to turn this type off too.
								ReviewedReads.check(s, ReviewedReads.icons(), u);
								return reviewed("ICON", Kind.ICON, true, null, null, null, Reference.ICONS, at + ", Icons.java:37").withIconBlock();
							}),
					new Rule("snake2d/util/color/ColorImp", "<init>", "(Lsnake2d/util/file/Json;)V", false,
							(c, at, s, u) -> reviewed("COLOR", Kind.COLOR, true, null, 0.0, 255.0, null, at + ", ColorImp.java:61,104,120")),
					new Rule("snake2d/util/color/ColorImp", "<init>", "(Lsnake2d/util/file/Json;Ljava/lang/String;)V", false, HelperRules::namedColor),
					new Rule("util/keymap/RMAPS", "readMany", "(Lsnake2d/util/file/Json;)Lsnake2d/util/sets/LIST;", false, HelperRules::readMany),
					new Rule("util/keymap/RMAP", "readMany", "(Lsnake2d/util/file/Json;)Lsnake2d/util/sets/LIST;", false, HelperRules::readMany),
					new Rule("util/keymap/RMAP", "readFill", "(Lsnake2d/util/file/Json;D)[D", false, HelperRules::readFill),
					new Rule("util/info/INFO", "<init>", "(Lsnake2d/util/file/Json;Lsnake2d/util/misc/ACTION;)V", true, (c, at, s, u) -> null),
					new Rule("util/info/INFO", "<init>", "(Lsnake2d/util/file/Json;)V", true, (c, at, s, u) -> null)), true);
		}

		private static TagSpec reviewed(String key, Kind kind, boolean required, String gameDefault, Double min, Double max, Reference reference, String evidence) {
			return new TagSpec(key, kind, required, gameDefault, min, max, true, reference, null, List.of(), evidence, Confidence.CODE_VERIFIED);
		}

		private static MapRule mapRule(Value source) {
			if (source instanceof Returned r) {
				if (r.owner().equals("init/resources/RESOURCES") && r.name().equals("map")) {
					return new MapRule("RESOURCE", Reference.RESOURCES, "RESOURCES.java:24");
				}
				if (r.owner().equals("init/type/CLIMATES") && r.name().equals("MAP")) {
					return new MapRule("CLIMATE", Reference.CLIMATES, "CLIMATES.java:38");
				}
				if (r.owner().equals("init/type/TERRAINS") && r.name().equals("MAP")) {
					return new MapRule("TERRAIN", Reference.TERRAINS, "TERRAINS.java:40");
				}
			}
			if (source instanceof FieldRef f && f.name().equals("DAMAGE_COLL")) {
				return new MapRule("DAMAGE", Reference.DAMAGES, "BOOSTABLES.java:238");
			}
			return null;
		}

		/** new ColorImp(json, key) reads the same way as the plain form, under the key it's given (ColorImp.java:104-116). */
		private static TagSpec namedColor(Call call, String at, ClassFileScanner scanner, List<String> unresolved) {
			if (call.args().size() != 2 || !(call.args().get(1) instanceof Const key) || !(key.value() instanceof String name)) {
				unresolved.add("it reads a colour whose name is built while the game runs, at " + at + ".");
				return null;
			}
			return reviewed(name, Kind.COLOR, true, null, 0.0, 255.0, null, at + ", ColorImp.java:104,120");
		}

		/** RMAP.readMany(json) reads the map's key plus S, and absent means an empty list (RMAP.java:122-125,156). */
		private static TagSpec readMany(Call call, String at, ClassFileScanner scanner, List<String> unresolved) {
			MapRule map = mapRule(call.receiver());
			if (map == null) {
				unresolved.add("it reads a list from a map that wasn't reviewed, at " + at + ".");
				return null;
			}
			return reviewed(map.key() + "S", Kind.KEY_LIST, false, "Empty", null, null, map.reference(), at + ", RMAP.java:122-125,156, " + map.evidence());
		}

		/** RMAP.readFill(json, max) reads a block of values 0 to max, and absent means zeros (RMAP.java:196-219). */
		private static TagSpec readFill(Call call, String at, ClassFileScanner scanner, List<String> unresolved) {
			MapRule map = mapRule(call.receiver());
			Number max = number(call.args(), 1);
			if (map == null || max == null) {
				unresolved.add("it reads a block in a way that wasn't reviewed, at " + at + ".");
				return null;
			}
			return reviewed(map.key(), Kind.DECIMAL_MAP, false, "0 for every entry", 0.0, max.doubleValue(), map.reference(), at + ", RMAP.java:196-219, " + map.evidence());
		}

		/** A KJson block's range is inside its own process method (RMAP.java:300-330). */
		private static TagSpec kJson(Call call, String at, ClassFileScanner scanner, List<String> unresolved) throws ClassFileScanner.ScanException {
			MapRule map = call.args().stream().map(HelperRules::mapRule).filter(Objects::nonNull).findFirst().orElse(null);
			ScannedClass block = scanner.scan(call.owner());
			if (map == null || block == null || !"util/keymap/RMAP$KJson".equals(block.superName())) {
				unresolved.add("it reads a block in a way that wasn't reviewed, at " + at + ".");
				return null;
			}
			for (ScannedMethod method : block.methods()) {
				if (method.name().equals("process")) {
					for (Call read : method.calls()) {
						if (read.owner().equals(JSON) && read.name().equals("d") && read.descriptor().equals("(Ljava/lang/String;DD)D")
								&& number(read.args(), 1) != null && number(read.args(), 2) != null) {
							return reviewed(map.key(), Kind.DECIMAL_MAP, false, "0 for every entry", number(read.args(), 1).doubleValue(), number(read.args(), 2).doubleValue(),
									map.reference(), at + ", " + block.sourceFile() + ":" + read.line() + ", RMAP.java:300-330, " + map.evidence());
						}
					}
				}
			}
			unresolved.add("its " + map.key() + " block doesn't read values the expected way, at " + at + ".");
			return null;
		}

		/** INFO reads NAME, NAMES, DESC and WIKI, from the root or an INFO block (INFO.java:16-24, WIKI.java:58, ArticleText.java:29-36). */
		static List<TagSpec> infoTags() {
			String evidence = "INFO.java:16-24";
			return List.of(
					new TagSpec("NAME", Kind.TEXT_LINE, true, null, null, null, true, null, null, List.of(), evidence, Confidence.CODE_VERIFIED),
					new TagSpec("NAMES", Kind.TEXT_LINE, false, "Same as NAME", null, null, true, null, null, List.of(), evidence, Confidence.CODE_VERIFIED),
					new TagSpec("DESC", Kind.TEXT_LINES, true, null, null, null, true, null, null, List.of(), evidence, Confidence.CODE_VERIFIED),
					new TagSpec("WIKI", Kind.TEXT_BLOCK, false, null, null, null, true, null, null,
							List.of(new SubKey("NAME", true, false, true),
									new SubKey("CATEGORY", true, false, true),
									new SubKey("LINK_KEY", false, false, false),
									new SubKey("TEXT", true, true, true)),
							"INFO.java:24, WIKI.java:57-61, ArticleText.java:29-36", Confidence.CODE_VERIFIED));
		}
	}
}
