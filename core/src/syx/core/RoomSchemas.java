package syx.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import syx.core.TagSpec.Column;
import syx.core.TagSpec.Confidence;
import syx.core.TagSpec.Kind;
import syx.core.TagSpec.Reference;
import syx.core.TagSpec.Shape;

/**
 * The tag tables for types whose reads are spread across many classes. Each entry was checked by hand
 * against the game's source, and {@link ReviewedReads} turns the type off if those reads ever change.
 */
final class RoomSchemas {

	static final String FARM_LOADER = "settlement.room.food.farm.ROOM_FARM";
	static final String CROP_LOADER = "init.resources.Growable";
	static final String MINE_LOADER = "settlement.room.industry.mine.ROOM_MINE";
	static final String MINABLE_LOADER = "init.resources.Minable";
	/** The vanilla mine a new one takes its sprites from, because they don't depend on what's mined. */
	private static final String STARTING_MINE = "init/room/MINE_STONE.txt";
	static final String REFINER_LOADER = "settlement.room.industry.refiner.ROOM_REFINER";
	/** The vanilla refiner a new one takes its sprites from. Vanilla's differ in look, not in what they need. */
	private static final String STARTING_REFINER = "init/room/REFINER_SMELTER.txt";
	static final String FISHERY_LOADER = "settlement.room.food.fish.ROOM_FISHERY";
	static final String ORCHARD_LOADER = "settlement.room.food.orchard.ROOM_ORCHARD";
	static final String HUNTER_LOADER = "settlement.room.food.hunter.ROOM_HUNTER";
	/** The only vanilla file of each of these types. Their sprites don't depend on what's produced. */
	private static final String STARTING_FISHERY = "init/room/FISHERY_NORMAL.txt";
	private static final String STARTING_ORCHARD = "init/room/ORCHARD_FRUIT.txt";
	private static final String STARTING_HUNTER = "init/room/HUNTER_NORMAL.txt";
	/** What each passes its Furnisher (fish/Constructor.java:141, orchard/Constructor.java:115, hunter/Constructor.java:77). */
	private static final int FOOD_ROOM_ITEMS = 2;
	private static final int FISHERY_STATS = 5;
	private static final int ORCHARD_STATS = 4;
	private static final int HUNTER_STATS = 3;
	static final String TEMPLE_LOADER = "settlement.room.spirit.temple.ROOM_TEMPLE";
	static final String SHRINE_LOADER = "settlement.room.spirit.shrine.ROOM_SHRINE";
	/** The vanilla temple and shrine new ones take sprites from. The four differ in look, not in what they need. */
	private static final String STARTING_TEMPLE = "init/room/TEMPLE_ATHURI.txt";
	private static final String STARTING_SHRINE = "init/room/SHRINE_ATHURI.txt";
	/** What each passes its Furnisher (TempleConstructor.java:53, shrine/Constructor.java:307). */
	private static final int TEMPLE_ITEMS = 5;
	private static final int TEMPLE_STATS = 5;
	private static final int SHRINE_ITEMS = 1;
	private static final int SHRINE_STATS = 1;
	/** The values ROOM_TEMPLE.java:57-78 accepts. Anything else stops loading. */
	static final List<String> SACRIFICE_TYPES = List.of("RESOURCE", "ANIMAL", "HUMAN");
	static final String TAVERN_LOADER = "settlement.room.service.food.tavern.ROOM_TAVERN";
	static final String EATERY_LOADER = "settlement.room.service.food.eatery.ROOM_EATERY";
	static final String CANTEEN_LOADER = "settlement.room.service.food.canteen.ROOM_CANTEEN";
	/** The only vanilla file of each of these types. */
	private static final String STARTING_TAVERN = "init/room/TAVERN_NORMAL.txt";
	private static final String STARTING_EATERY = "init/room/EATERY_NORMAL.txt";
	private static final String STARTING_CANTEEN = "init/room/CANTEEN_NORMAL.txt";
	/** What each passes its Furnisher (tavern/Constructor.java:44, eatery/Constructor.java:53, canteen/Constructor.java:42). */
	private static final int TAVERN_ITEMS = 3;
	private static final int TAVERN_STATS = 2;
	private static final int EATERY_ITEMS = 1;
	private static final int EATERY_STATS = 2;
	private static final int CANTEEN_ITEMS = 2;
	private static final int CANTEEN_STATS = 3;
	static final String WELL_LOADER = "settlement.room.service.hygine.well.ROOM_WELL";
	static final String BATH_LOADER = "settlement.room.service.hygine.bath.ROOM_BATH";
	static final String LAVATORY_LOADER = "settlement.room.service.lavatory.ROOM_LAVATORY";
	static final String BARBER_LOADER = "settlement.room.service.barber.ROOM_BARBER";
	/** The only vanilla file of each of these types. */
	private static final String STARTING_WELL = "init/room/WELL_NORMAL.txt";
	private static final String STARTING_BATH = "init/room/BATH_NORMAL.txt";
	private static final String STARTING_LAVATORY = "init/room/LAVATORY_NORMAL.txt";
	private static final String STARTING_BARBER = "init/room/BARBER_NORMAL.txt";
	/** What each passes its Furnisher (well/Constructor.java:42, bath/Constructor.java:59, lavatory/Constructor.java:35, barber/Constructor.java:39). */
	private static final int WELL_ITEMS = 1;
	private static final int WELL_STATS = 1;
	private static final int BATH_ITEMS = 2;
	private static final int BATH_STATS = 2;
	private static final int LAVATORY_ITEMS = 2;
	private static final int LAVATORY_STATS = 3;
	private static final int BARBER_ITEMS = 2;
	private static final int BARBER_STATS = 3;
	static final String PHYSICIAN_LOADER = "settlement.room.health.physician.ROOM_PHYSICIAN";
	static final String PLEASURE_LOADER = "settlement.room.service.pleasure.ROOM_PLEASURE";
	static final String MARKET_LOADER = "settlement.room.service.market.ROOM_MARKET";
	/** The only vanilla file of each of these types. */
	private static final String STARTING_PHYSICIAN = "init/room/PHYSICIAN_NORMAL.txt";
	private static final String STARTING_PLEASURE = "init/room/PLEASURE_NORMAL.txt";
	private static final String STARTING_MARKET = "init/room/MARKET_NORMAL.txt";
	/** What each passes its Furnisher (physician/Constructor.java:37, pleasure/Constructor.java:58, market/Constructor.java:52). */
	private static final int PHYSICIAN_ITEMS = 2;
	private static final int PHYSICIAN_STATS = 3;
	private static final int PLEASURE_ITEMS = 3;
	private static final int PLEASURE_STATS = 3;
	private static final int MARKET_ITEMS = 1;
	private static final int MARKET_STATS = 2;
	static final String STAGE_LOADER = "settlement.room.service.stage.ROOM_STAGE";
	static final String SPEAKER_LOADER = "settlement.room.service.speaker.ROOM_SPEAKER";
	static final String FIGHTPIT_LOADER = "settlement.room.service.arena.pit.ROOM_FIGHTPIT";
	static final String ARENA_LOADER = "settlement.room.service.arena.grand.ROOM_ARENA";
	/**
	 * What each room with spectators passes its Furnisher: one item and two stats (StageConstructor.java:31,
	 * SpeakerConstructor.java:32, arena/pit/ArenaConstructor.java:49, arena/grand/ArenaConstructor.java:61).
	 */
	private static final int SPECTATOR_ITEMS = 1;
	private static final int SPECTATOR_STATS = 2;
	static final String COURT_LOADER = "settlement.room.law.court.ROOM_COURT";
	/** What the Court passes its Furnisher (court/Constructor.java:39). */
	private static final int COURT_ITEMS = 2;
	private static final int COURT_STATS = 3;
	static final String PRISON_LOADER = "settlement.room.law.prison.ROOM_PRISON";
	/** What the Prison passes its Furnisher (prison/Constructor.java:50). */
	private static final int PRISON_ITEMS = 1;
	private static final int PRISON_STATS = 2;
	static final String STOCKADE_LOADER = "settlement.room.law.stockade.ROOM_STOCKADE";
	/** What the Stockade passes its Furnisher (stockade/Constructor.java:95). */
	private static final int STOCKADE_ITEMS = 1;
	private static final int STOCKADE_STATS = 2;
	static final String GUARD_LOADER = "settlement.room.law.guard.ROOM_GUARD";
	/** What the Guard passes its Furnisher (guard/Constructor.java:59). */
	private static final int GUARD_ITEMS = 2;
	private static final int GUARD_STATS = 1;
	static final String POLICE_LOADER = "settlement.room.law.police.ROOM_POLICE";
	/** What the Police passes its Furnisher (police/PoliceConstructor.java:43). */
	private static final int POLICE_ITEMS = 5;
	private static final int POLICE_STATS = 2;
	static final String STOCKS_LOADER = "settlement.room.law.stocks.ROOM_STOCKS";
	/** What the Stocks pass their Furnisher (stocks/MConstructor.java:37). */
	private static final int STOCKS_ITEMS = 1;
	private static final int STOCKS_STATS = 1;
	static final String EXECUTION_LOADER = "settlement.room.law.execution.ROOM_EXECTUTION";
	/** What the Execution room passes its Furnisher (execution/Constructor.java:39). */
	private static final int EXECUTION_ITEMS = 4;
	private static final int EXECUTION_STATS = 1;
	static final String MONUMENT_LOADER = "settlement.room.infra.monument.ROOM_MONUMENTS";
	/** The vanilla monument a new one takes its look from. Its two items are a statue and a pillar. */
	private static final String STARTING_MONUMENT = "init/room/MONUMENT_SCULPTURE.txt";
	/** The TYPE that makes ROOM_MONUMENTS build a torch, which reads its file with other code (ROOM_MONUMENTS.java:26-29). */
	static final String TORCH_TYPE = "TORCH";
	/** The keys a monument's SPRITES block makes an item from, one size each (Imp.java:57,91,126). */
	static final List<String> MONUMENT_SIZES = List.of("1x1", "2x2", "3x3");
	/**
	 * A monument's count around each tile is held in 4 bits (ROOM_MONUMENT.java:45, Bitsmap1D.java:88-91), and so is
	 * its amount stat (StatsAccess.java:265), so it can't count past this.
	 */
	static final int MONUMENT_MOST_VALUE = 15;
	/** The comparators a REQUIRES block's keys are read as. Any other stops loading (Lockable.java:117, RMAP.java:69-83). */
	static final List<String> COMPARATORS = List.of("LESS", "GREATER", "GREATERE", "EQUAL", "NEQUAL");
	/** The classes a service's STANDING is given for, in a race's STATS too (StatStanding.java:313-315,336-338). */
	private static final List<String> STANDING_CLASSES = List.of("CITIZEN", "NOBLE", "SLAVE", "OTHER");
	/** The most a class's standing can be (StatStanding.java:337). */
	private static final double MAX_STANDING = 1000000;
	/** The game's files that new assets take starting values from. */
	static final List<String> STARTER_FILES = List.of(STARTING_MINE, STARTING_REFINER, STARTING_FISHERY, STARTING_ORCHARD, STARTING_HUNTER, STARTING_TEMPLE, STARTING_SHRINE,
			STARTING_TAVERN, STARTING_EATERY, STARTING_CANTEEN, STARTING_WELL, STARTING_BATH, STARTING_LAVATORY, STARTING_BARBER,
			STARTING_PHYSICIAN, STARTING_PLEASURE, STARTING_MARKET, STARTING_MONUMENT,
			"init/room/STAGE_NORMAL.txt", "init/room/SPEAKER_NORMAL.txt", "init/room/FIGHTPIT_NORMAL.txt", "init/room/ARENAG_NORMAL.txt",
			"init/room/_COURT.txt", "text/room/_COURT.txt", "init/room/_PRISON.txt", "text/room/_PRISON.txt", "init/room/_STOCKADE.txt", "text/room/_STOCKADE.txt",
			"init/room/_GUARD.txt", "text/room/_GUARD.txt", "init/room/_POLICE.txt", "text/room/_POLICE.txt",
			"init/room/_STOCKS.txt", "text/room/_STOCKS.txt", "init/room/_EXECUTION.txt", "text/room/_EXECUTION.txt");
	private static final String MINABLE_SPRITES = "init/resources/Minable$Util";
	/** The most files the game accepts in the minable folder (Minable.java:75). */
	private static final int MOST_MINABLES = 31;
	private static final String CROP_SPRITE = "init/resources/GrowableSprite";
	private static final Column OUT_PLAYER = new Column("PLAYER", "Player", 0, 100000, true, null);
	private static final Column OUT_AI = new Column("AI_RATE", "AI", 0, 100000, false, "As player");
	private static final Column OUT_RECOVERY = new Column("AI_RECOVERY", "AI recovery", 0, 100000, false, "1");
	private static final Column IN_RATE = new Column(null, "Rate", 0, 10000, true, null);
	private static final Column EMIT_VALUE = new Column("VALUE", "Value", 0, 1, true, null);
	private static final Column EMIT_RADIUS = new Column("RADIUS", "Radius", 0, 1, true, null);
	/** What a mine passes its Furnisher (mine/Constructor.java:103). */
	private static final int MINE_ITEMS = 2;
	private static final int MINE_STATS = 4;
	/** What a refiner passes its Furnisher (refiner/Constructor.java:48). */
	private static final int REFINER_ITEMS = 3;
	private static final int REFINER_STATS = 3;
	static final String WORKSHOP_LOADER = "settlement.room.industry.workshop.ROOM_WORKSHOP";
	/** What a workshop passes its Furnisher (workshop/Constructor.java:49). */
	private static final int WORKSHOP_ITEMS = 3;
	private static final int WORKSHOP_STATS = 3;
	static final String PASTURE_LOADER = "settlement.room.food.pasture.ROOM_PASTURE";
	/** What a pasture passes its Furnisher (pasture/Constructor.java:79). */
	private static final int PASTURE_ITEMS = 2;
	private static final int PASTURE_STATS = 4;

	private RoomSchemas() {
	}

	static boolean handles(String loader) {
		return FARM_LOADER.equals(loader) || CROP_LOADER.equals(loader) || MINE_LOADER.equals(loader) || MINABLE_LOADER.equals(loader) || REFINER_LOADER.equals(loader) || WORKSHOP_LOADER.equals(loader) || PASTURE_LOADER.equals(loader)
				|| FISHERY_LOADER.equals(loader) || ORCHARD_LOADER.equals(loader) || HUNTER_LOADER.equals(loader)
				|| TEMPLE_LOADER.equals(loader) || SHRINE_LOADER.equals(loader)
				|| TAVERN_LOADER.equals(loader) || EATERY_LOADER.equals(loader) || CANTEEN_LOADER.equals(loader)
				|| WELL_LOADER.equals(loader) || BATH_LOADER.equals(loader) || LAVATORY_LOADER.equals(loader) || BARBER_LOADER.equals(loader)
				|| PHYSICIAN_LOADER.equals(loader) || PLEASURE_LOADER.equals(loader) || MARKET_LOADER.equals(loader)
				|| MONUMENT_LOADER.equals(loader)
				|| STAGE_LOADER.equals(loader) || SPEAKER_LOADER.equals(loader) || FIGHTPIT_LOADER.equals(loader) || ARENA_LOADER.equals(loader)
				|| COURT_LOADER.equals(loader) || PRISON_LOADER.equals(loader) || STOCKADE_LOADER.equals(loader) || GUARD_LOADER.equals(loader) || POLICE_LOADER.equals(loader)
				|| STOCKS_LOADER.equals(loader) || EXECUTION_LOADER.equals(loader);
	}

	static AssetSchema schema(String loader, ClassFileScanner scanner, List<String> unresolved) throws ClassFileScanner.ScanException {
		if (CROP_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.crop(), unresolved);
			AssetSchema crop = crop(scanner, unresolved);
			ReviewedReads.checkLimits(crop, ReviewedReads.crop(), unresolved);
			return crop;
		}
		if (MINABLE_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.minable(), unresolved);
			AssetSchema minable = minable(scanner, unresolved);
			ReviewedReads.checkLimits(minable, ReviewedReads.minable(), unresolved);
			return minable;
		}
		if (MINE_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.mine(), unresolved);
			AssetSchema mine = mine();
			ReviewedReads.checkLimits(mine, ReviewedReads.mine(), unresolved);
			return mine;
		}
		if (REFINER_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.refiner(), unresolved);
			AssetSchema refiner = refiner();
			ReviewedReads.checkLimits(refiner, ReviewedReads.refiner(), unresolved);
			return refiner;
		}
		if (WORKSHOP_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.workshop(), unresolved);
			AssetSchema workshop = workshop();
			ReviewedReads.checkLimits(workshop, ReviewedReads.workshop(), unresolved);
			return workshop;
		}
		if (PASTURE_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.pasture(), unresolved);
			AssetSchema pasture = pasture();
			ReviewedReads.checkLimits(pasture, ReviewedReads.pasture(), unresolved);
			return pasture;
		}
		if (FISHERY_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.fishery(), unresolved);
			AssetSchema fishery = fishery();
			ReviewedReads.checkLimits(fishery, ReviewedReads.fishery(), unresolved);
			return fishery;
		}
		if (ORCHARD_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.orchard(), unresolved);
			// A new orchard starts from vanilla's sprites, which name this sheet the game's code makes.
			if (!scanner.mentions("settlement/tilemap/terrain/TForest$Sprites", "_TREE")) {
				unresolved.add("the game no longer makes the _TREE sheet an orchard's sprites use.");
			}
			AssetSchema orchard = orchard();
			ReviewedReads.checkLimits(orchard, ReviewedReads.orchard(), unresolved);
			return orchard;
		}
		if (TEMPLE_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.temple(), unresolved);
			AssetSchema temple = temple();
			ReviewedReads.checkLimits(temple, ReviewedReads.temple(), unresolved);
			return temple;
		}
		if (SHRINE_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.shrine(), unresolved);
			AssetSchema shrine = shrine();
			ReviewedReads.checkLimits(shrine, ReviewedReads.shrine(), unresolved);
			return shrine;
		}
		if (HUNTER_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.hunter(), unresolved);
			AssetSchema hunter = hunter();
			ReviewedReads.checkLimits(hunter, ReviewedReads.hunter(), unresolved);
			return hunter;
		}
		if (TAVERN_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.tavern(), unresolved);
			AssetSchema tavern = tavern();
			ReviewedReads.checkLimits(tavern, ReviewedReads.tavern(), unresolved);
			return tavern;
		}
		if (EATERY_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.eatery(), unresolved);
			AssetSchema eatery = eatery();
			ReviewedReads.checkLimits(eatery, ReviewedReads.eatery(), unresolved);
			return eatery;
		}
		if (CANTEEN_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.canteen(), unresolved);
			AssetSchema canteen = canteen();
			ReviewedReads.checkLimits(canteen, ReviewedReads.canteen(), unresolved);
			return canteen;
		}
		if (WELL_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.well(), unresolved);
			AssetSchema well = well();
			ReviewedReads.checkLimits(well, ReviewedReads.well(), unresolved);
			return well;
		}
		if (BATH_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.bath(), unresolved);
			AssetSchema bath = bath();
			ReviewedReads.checkLimits(bath, ReviewedReads.bath(), unresolved);
			return bath;
		}
		if (LAVATORY_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.lavatory(), unresolved);
			AssetSchema lavatory = lavatory();
			ReviewedReads.checkLimits(lavatory, ReviewedReads.lavatory(), unresolved);
			return lavatory;
		}
		if (BARBER_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.barber(), unresolved);
			AssetSchema barber = barber();
			ReviewedReads.checkLimits(barber, ReviewedReads.barber(), unresolved);
			return barber;
		}
		if (PHYSICIAN_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.physician(), unresolved);
			AssetSchema physician = physician();
			ReviewedReads.checkLimits(physician, ReviewedReads.physician(), unresolved);
			return physician;
		}
		if (PLEASURE_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.pleasure(), unresolved);
			AssetSchema pleasure = pleasure();
			ReviewedReads.checkLimits(pleasure, ReviewedReads.pleasure(), unresolved);
			return pleasure;
		}
		if (MARKET_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.market(), unresolved);
			AssetSchema market = market();
			ReviewedReads.checkLimits(market, ReviewedReads.market(), unresolved);
			return market;
		}
		if (MONUMENT_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.monument(), unresolved);
			AssetSchema monument = monument();
			ReviewedReads.checkLimits(monument, ReviewedReads.monument(), unresolved);
			return monument;
		}
		if (COURT_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.court(), unresolved);
			AssetSchema court = court();
			ReviewedReads.checkLimits(court, ReviewedReads.court(), unresolved);
			return court;
		}
		if (STOCKS_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.stocks(), unresolved);
			AssetSchema stocks = stocks();
			ReviewedReads.checkLimits(stocks, ReviewedReads.stocks(), unresolved);
			return stocks;
		}
		if (EXECUTION_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.execution(), unresolved);
			AssetSchema execution = execution();
			ReviewedReads.checkLimits(execution, ReviewedReads.execution(), unresolved);
			return execution;
		}
		if (GUARD_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.guard(), unresolved);
			AssetSchema guard = guard();
			ReviewedReads.checkLimits(guard, ReviewedReads.guard(), unresolved);
			return guard;
		}
		if (POLICE_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.police(), unresolved);
			AssetSchema police = police();
			ReviewedReads.checkLimits(police, ReviewedReads.police(), unresolved);
			return police;
		}
		if (PRISON_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.prison(), unresolved);
			AssetSchema prison = prison();
			ReviewedReads.checkLimits(prison, ReviewedReads.prison(), unresolved);
			return prison;
		}
		if (STOCKADE_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.stockade(), unresolved);
			AssetSchema stockade = stockade();
			ReviewedReads.checkLimits(stockade, ReviewedReads.stockade(), unresolved);
			return stockade;
		}
		if (STAGE_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.stage(), unresolved);
			AssetSchema stage = stage();
			ReviewedReads.checkLimits(stage, ReviewedReads.stage(), unresolved);
			return stage;
		}
		if (SPEAKER_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.speaker(), unresolved);
			AssetSchema speaker = speaker();
			ReviewedReads.checkLimits(speaker, ReviewedReads.speaker(), unresolved);
			return speaker;
		}
		if (FIGHTPIT_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.fightPit(), unresolved);
			AssetSchema pit = fightPit();
			ReviewedReads.checkLimits(pit, ReviewedReads.fightPit(), unresolved);
			return pit;
		}
		if (ARENA_LOADER.equals(loader)) {
			ReviewedReads.check(scanner, ReviewedReads.grandArena(), unresolved);
			AssetSchema arena = grandArena();
			ReviewedReads.checkLimits(arena, ReviewedReads.grandArena(), unresolved);
			return arena;
		}
		ReviewedReads.check(scanner, ReviewedReads.farm(), unresolved);
		AssetSchema farm = farm();
		ReviewedReads.checkLimits(farm, ReviewedReads.farm(), unresolved);
		return farm;
	}

	private static TagSpec leaf(String key, Kind kind, boolean required, String gameDefault, Double min, Double max, boolean enforced, String evidence) {
		return new TagSpec(key, kind, required, gameDefault, min, max, enforced, null, null, List.of(), evidence, Confidence.CODE_VERIFIED);
	}

	private static TagSpec key(String key, Reference reference, boolean required, String gameDefault, String evidence) {
		return new TagSpec(key, Kind.KEY, required, gameDefault, null, null, true, reference, null, List.of(), evidence, Confidence.CODE_VERIFIED);
	}

	private static TagSpec color(String key, String evidence) {
		return leaf(key, Kind.COLOR, true, null, 0.0, 255.0, true, evidence + ", ColorImp.java:104,120");
	}

	private static TagSpec shaped(String key, Kind kind, boolean required, String gameDefault, Reference reference, String evidence, Shape shape) {
		return new TagSpec(key, kind, required, gameDefault, null, null, true, reference, null, List.of(), evidence, Confidence.CODE_VERIFIED, null, shape);
	}

	private static final String EMPLOYMENT = "RoomEmploymentSimple.java:";

	private static TagSpec icon() {
		return new TagSpec("ICON", Kind.ICON, true, null, null, null, true, Reference.ICONS, null, List.of(), "RoomBlueprintImp.java:80, Icons.java:37", Confidence.CODE_VERIFIED).withIconBlock();
	}

	private static TagSpec resources() {
		return new TagSpec("RESOURCES", Kind.KEY_LIST, false, "Empty", null, null, true, Reference.RESOURCES, null, List.of(), "Furnisher.java:72-74, RMAP.java:122-125,156", Confidence.CODE_VERIFIED, null,
				new Shape(List.of(), List.of(), 4, 0, 0));
	}

	private static TagSpec areaCosts() {
		return new TagSpec("AREA_COSTS", Kind.LINKED_DECIMALS, true, null, null, null, true, null, "RESOURCES", List.of(), "Furnisher.java:75", Confidence.CODE_VERIFIED);
	}

	private static TagSpec miniPattern() {
		return shaped("MINI_COLOR_PATTERN", Kind.TEXT_ROWS, false, "One colour for the whole room", null, "FurnisherMinimapColor.java:22-35", new Shape(List.of(), List.of(), 32, 0, 0));
	}

	private static TagSpec work() {
		return shaped("WORK", Kind.BLOCK, true, null, null, "RoomBlueprintIns.java:66, Tile.java:553", Shape.block(List.of(
				leaf("SHIFT_OFFSET", Kind.DECIMAL, true, null, 0.0, 0.99, true, EMPLOYMENT + "76"),
				leaf("NIGHT_SHIFT", Kind.BOOL, false, "false", null, null, true, EMPLOYMENT + "77"),
				leaf("FULFILLMENT", Kind.DECIMAL, false, "0.5", -100000.0, 100000.0, true, EMPLOYMENT + "79-82"),
				leaf("ACCIDENTS_PER_YEAR", Kind.DECIMAL, false, "0.0", 0.0, 10000.0, true, EMPLOYMENT + "83"),
				leaf("HEALTH_FACTOR", Kind.DECIMAL, false, "1.0", 0.0, 1.0, true, EMPLOYMENT + "84"),
				leaf("LARGE_WORKFORCE", Kind.INT, false, "1000", 10.0, 10000.0, false, EMPLOYMENT + "85"))));
	}

	private static TagSpec bonus() {
		return shaped("BONUS", Kind.BLOCK, false, "1 for every climate", null, "RoomBlueprintIns.java:108-110", Shape.block(List.of(
				new TagSpec("CLIMATE", Kind.DECIMAL_MAP, false, null, 0.0, 2000.0, true, Reference.CLIMATES, null, List.of(), "CLIMATES.java:74-79", Confidence.CODE_VERIFIED))));
	}

	private static TagSpec experienceBonus() {
		return shaped("EXPERIENCE_BONUS", Kind.BLOCK, false, "A bonus of 1 at 1000 employees", null, "BonusExperience.java:155-162", Shape.block(List.of(
				leaf("MAX_EMPLOYEES", Kind.INT, true, null, 50.0, null, true, "BonusExperience.java:160"),
				leaf("BONUS", Kind.DECIMAL, true, null, null, null, true, "BonusExperience.java:161"))));
	}

	private static TagSpec upgrades() {
		return shaped("UPGRADES", Kind.BLOCK_LIST, false, null, null, "RoomUpgrades.java:45-69", new Shape(List.of(
				leaf("RESOURCE_MASK", Kind.DECIMALS, true, null, null, null, true, "RoomUpgrades.java:54"),
				leaf("BOOST", Kind.DECIMAL, true, null, null, null, true, "RoomUpgrades.java:57"),
				leaf("AI", Kind.DECIMAL, false, "Half of BOOST", 0.0, 10000.0, true, "RoomUpgrades.java:62-65")), List.of(), 0, 0, 0));
	}

	private static TagSpec environmentEmit() {
		return shaped("ENVIRONMENT_EMIT", Kind.KEYED_ROWS, false, null, Reference.ENVIRONMENTS, "Furnisher.java:116-126", Shape.rows(List.of(EMIT_VALUE, EMIT_RADIUS)));
	}

	private static TagSpec textWork() {
		return shaped("WORK", Kind.BLOCK, true, null, null, EMPLOYMENT + "73", Shape.block(List.of(
				leaf("TITLE", Kind.TEXT_LINE, true, null, null, null, true, EMPLOYMENT + "74"),
				leaf("VERB", Kind.TEXT_LINE, true, null, null, null, true, EMPLOYMENT + "75"))));
	}

	/** A room's four stats. The first two take their words from the file, and the game names the others (FurnisherStat.java:221,405). */
	/**
	 * A room's STATS text blocks. The game makes one per FurnisherStat, in the order its Constructor creates them,
	 * and reads NAME and DESC only for the first named ones (FurnisherStat.java:57-76). The rest it names itself.
	 */
	private static TagSpec textStats(int blocks, int named, String evidence) {
		return shaped("STATS", Kind.BLOCK_LIST, true, null, null, evidence + ", Furnisher.java:92-94, FurnisherStat.java:62-63,74-75", new Shape(List.of(
				leaf("NAME", Kind.TEXT_LINE, true, null, null, null, true, "FurnisherStat.java:74"),
				leaf("DESC", Kind.TEXT_LINES, true, null, null, null, true, "FurnisherStat.java:75")), List.of(), 0, blocks, named));
	}

	/** One recipe's inputs and outputs, in a farm or a refiner. */
	private static TagSpec industry(String evidence, String outNeededBecause) {
		return shaped("INDUSTRY", Kind.BLOCK, true, null, null, evidence, Shape.block(List.of(
				shaped("OUT", Kind.KEYED_ROWS, true, null, Reference.RESOURCES, "Industry.java:123-138, " + outNeededBecause, Shape.rows(List.of(OUT_PLAYER, OUT_AI, OUT_RECOVERY))),
				shaped("IN", Kind.KEYED_ROWS, false, "Nothing consumed", Reference.RESOURCES, "Industry.java:114-121", Shape.rows(List.of(IN_RATE))),
				new TagSpec("ICON", Kind.ICON, false, "Built from what it produces", null, null, true, Reference.ICONS, null, List.of(), "Industry.java:141-142", Confidence.CODE_VERIFIED).withIconBlock())));
	}

	private static TagSpec textUpgrades() {
		return shaped("UPGRADES", Kind.TEXT_ROWS, false, null, null, "RoomUpgrades.java:81", new Shape(List.of(), List.of(), 0, 0, 0));
	}

	/** A farm's data and text files, from ROOM_FARM and the classes it builds on. */
	private static AssetSchema farm() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(key("GROWABLE", Reference.GROWABLES, true, null, "ROOM_FARM.java:58, RMAP.java:30-36"));
		data.add(leaf("INDOORS", Kind.BOOL, true, null, null, null, true, "Constructor.java:129"));
		data.add(resources());
		data.add(areaCosts());
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		data.add(industry("Industry.java:113, ROOM_FARM.java:65", "ROOM_FARM.java:88"));
		data.add(work());
		data.add(bonus());
		data.add(experienceBonus());
		data.add(upgrades());
		data.add(environmentEmit());
		// Read, but ROOM_FARM.java:117 then sets it to 0.
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0, set by the game for farms", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70, ROOM_FARM.java:117"));
		// Read, but Constructor.java:162-169 clears it.
		data.add(key("FLOOR", Reference.FLOORS, false, "No floor, because farms clear it", "Furnisher.java:76-84, Constructor.java:162-169"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(textWork());
		text.add(textStats(4, 2, "farm Constructor.java:46,79,108-112"));
		text.add(textUpgrades());
		return new AssetSchema(data, text, List.of(),
				List.of("The text file's STATS are four blocks in a fixed order (farm Constructor.java:46,79,110,112): 0 is the soil's fertility and 1 is the farmers needed, both named here. "
						+ "2 is irrigation and 3 is production, which the game names itself (FurnisherStat.java:221,405), so they're written empty and anything in them is never read. Vanilla's \"Yearly Output\" in block 3 has no effect.",
						"Nothing in this file locks the farm. Vanilla's are unlocked through technologies, in the technology files.",
						"REQUIRES isn't offered: its conditions have keys defined by other data files, and vanilla doesn't use it.",
						"What's harvested is the crop's RESOURCE (Tile.java:400), so a farm's first output should be the same resource."),
				RoomSchemas::farmRules, List.of(SoundEntries.ROOM_WORK, SoundEntries.ROOM_CLICK, SoundEntries.ROOM_AMBIENCE));
	}

	/** A sprite's tags (SheetData.java:42-55, GameSheets.java:115). Anything else in the block stops the game loading (SheetData.java:75-102). */
	private static TagSpec spriteEntry(String key, String sheetKind) {
		return spriteEntry(key, sheetKind, true);
	}

	/** A sprite's tags. {@code required} is false for a sprite only one constructor reads, such as a pasture's gate. */
	private static TagSpec spriteEntry(String key, String sheetKind, boolean required) {
		// What each kind of sheet must divide by (SheetType.java:134,222,387,474,524). A 3x3 sheet is size * 16 + 12.
		// GameAssetIndex.sheetRows holds the full rule, which for a texture isn't a square tile.
		int tile = switch (sheetKind) {
		case "1x1" -> 22;
		case "combo" -> 72;
		case "3x3" -> 60;
		case "box" -> 76;
		case "texture" -> 28;
		default -> 44;
		};
		TagSpec.Sprite sheets = new TagSpec.Sprite("sprite/game/" + sheetKind + "/", tile, tile);
		String at = "SheetData.java:";
		return shaped(key, Kind.BLOCK_LIST, required, null, null, "RoomSpriteImp.java:27-37, Constructor.java:117-233", new Shape(List.of(
				new TagSpec("FRAMES", Kind.FRAMES, true, null, null, null, true, null, null, List.of(), "GameSheets.java:115,147-191", Confidence.CODE_VERIFIED, sheets),
				leaf("FPS", Kind.DECIMAL, false, "0", 0.0, 100000.0, true, at + "42"),
				leaf("FPS_INTERVAL", Kind.DECIMAL, false, "1", 0.0, 1.0, true, at + "43"),
				leaf("SHADOW_LENGTH", Kind.DECIMAL, false, "0", 0.0, 100.0, true, at + "45"),
				leaf("SHADOW_HEIGHT", Kind.DECIMAL, false, "0", 0.0, 100.0, true, at + "46"),
				leaf("CIRCULAR", Kind.BOOL, false, "false", null, null, true, at + "47"),
				leaf("ROTATES", Kind.BOOL, false, "true", null, null, true, at + "48"),
				leaf("TINT", Kind.BOOL, false, "true", null, null, true, at + "51"),
				new TagSpec("COLOR", Kind.COLORS, false, "None, so TINT decides", 0.0, 255.0, true, null, null, List.of(), at + "49-50, ColorImp.java:69-116", Confidence.CODE_VERIFIED)), List.of(), 0, 0, 0));
	}

	/** A refiner's data and text files, from ROOM_REFINER and the classes it builds on. */
	private static AssetSchema refiner() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(leaf("STORAGE", Kind.INT, true, null, 8.0, 500.0, false, "ROOM_REFINER.java:37"));
		data.add(shaped("INDUSTRIES", Kind.BLOCK_LIST, true, null, null, "Industry.java:242-248, ROOM_REFINER.java:36", new Shape(List.of(industry("Industry.java:113", "Industry.java:247-248")), List.of(), 0, 0, 0)));
		data.add(resources());
		data.add(areaCosts());
		// A single name works too (Furnisher.java:80), and one in a file is kept as it's written.
		data.add(new TagSpec("FLOOR", Kind.KEY_LIST, false, "No floor of its own", null, null, true, Reference.FLOORS, null, List.of(), "Furnisher.java:76-88,226-229, RMAP.java:168-192", Confidence.CODE_VERIFIED));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		data.add(work());
		data.add(shaped("ITEMS", Kind.BLOCK_LIST, true, null, null, "Furnisher.java:101-104,189-190, Constructor.java:318,373,401", new Shape(List.of(
				leaf("COSTS", Kind.DECIMALS, true, null, null, null, true, "Furnisher.java:189"),
				leaf("STATS", Kind.DECIMALS, true, null, null, null, true, "Furnisher.java:190")), List.of(), 0, REFINER_ITEMS, REFINER_ITEMS)));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, "Constructor.java:71", Shape.block(List.of(
				spriteEntry("MAIN_MACHINE_COMBO", "combo"),
				spriteEntry("MAIN_MACHINE_COMBO_TOP", "combo"),
				spriteEntry("MAIN_MACHINE_COMBO_TOP_MACHINE", "1x1"),
				spriteEntry("WORK_1X1", "1x1"),
				spriteEntry("STORAGE_IN_1X1", "1x1"),
				spriteEntry("NICKNACK_BOTTOM_1X1", "1x1"),
				spriteEntry("NICKNACK_TOP_1X1", "1x1"),
				spriteEntry("MACHINE_1X1", "1x1"),
				spriteEntry("CONVEYOR_1X1", "1x1"),
				spriteEntry("STORAGE_1X1", "1x1")))));
		data.add(bonus());
		data.add(experienceBonus());
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(textWork());
		// All three stats are named by the game (Constructor.java:52,63-64, FurnisherStat.java:175,279,405), so the blocks only have to exist.
		text.add(textStats(REFINER_STATS, 0, "refiner Constructor.java:52,63-64"));
		text.add(shaped("ITEMS", Kind.BLOCK_LIST, true, null, null, "Furnisher.java:102,185-186", new Shape(List.of(
				leaf("NAME", Kind.TEXT_LINE, true, null, null, null, true, "Furnisher.java:185"),
				leaf("DESC", Kind.TEXT_LINES, true, null, null, null, true, "Furnisher.java:186")), List.of(), 0, REFINER_ITEMS, REFINER_ITEMS)));
		text.add(textUpgrades());
		return new AssetSchema(data, text, List.of(),
				List.of("The text file's STATS are three blocks in a fixed order (refiner Constructor.java:52,63-64): workers, efficiency and output. The game names all three itself (FurnisherStat.java:279,175,405), so they're written empty and anything in them is never read.",
						"Each block in INDUSTRIES is a recipe, and needs at least one OUT. The room's storage, and the icon on it, use a recipe's first OUT (Job.java:24, Constructor.java:216).",
						"A new refiner's SPRITES start from the game's own REFINER_SMELTER. Each sprite has one level per upgrade, and a room with more upgrades than levels uses the last. FLOOR works the same way (Furnisher.java:226-229).",
						"Each item's STATS has three numbers, for workers, efficiency and output, in that order. The game works out workers and output from the room, whatever's written for them (Constructor.java:52-69), so the numbers that matter are for efficiency.",
						"REQUIRES isn't offered: its conditions have keys defined by other data files. A sprite's OVERWRITE isn't offered either, and either one in a file stays as it's written."),
				RoomSchemas::refinerRules, List.of(SoundEntries.ROOM_WORK.like("ROOM_WORK_REFINER_SMELTER"), SoundEntries.ROOM_CLICK.like("ROOM_CLICK_REFINER_SMELTER"), SoundEntries.ROOM_AMBIENCE.like("ROOM_REFINER_SMELTER")),
				new AssetSchema.Starter(STARTING_REFINER, List.of("SPRITES")));
	}

	/** A workshop's data and text files, from ROOM_WORKSHOP and the classes it builds on. It has no starter: sprites and sounds are chosen, or copied with Initialise. */
	private static AssetSchema workshop() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(shaped("INDUSTRIES", Kind.BLOCK_LIST, true, null, null, "Industry.java:242-248, ROOM_WORKSHOP.java:36", new Shape(List.of(industry("Industry.java:113", "Industry.java:247-248")), List.of(), 0, 0, 0)));
		data.add(resources());
		data.add(areaCosts());
		// A single name works too (Furnisher.java:80), and one in a file is kept as it's written.
		data.add(new TagSpec("FLOOR", Kind.KEY_LIST, false, "No floor of its own", null, null, true, Reference.FLOORS, null, List.of(), "Furnisher.java:76-88,226-229, RMAP.java:168-192", Confidence.CODE_VERIFIED));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		data.add(work());
		data.add(shaped("ITEMS", Kind.BLOCK_LIST, true, null, null, "Furnisher.java:101-104,189-190, Constructor.java:49,285,395,457", new Shape(List.of(
				leaf("COSTS", Kind.DECIMALS, true, null, null, null, true, "Furnisher.java:189"),
				leaf("STATS", Kind.DECIMALS, true, null, null, null, true, "Furnisher.java:190")), List.of(), 0, WORKSHOP_ITEMS, WORKSHOP_ITEMS)));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, "Constructor.java:72", Shape.block(List.of(
				spriteEntry("CHAIR_1X1", "1x1"),
				spriteEntry("TABLE_COMBO", "combo"),
				spriteEntry("TABLE_TOP_COMBO", "combo"),
				spriteEntry("WORK_BELOW_1X1", "1x1"),
				spriteEntry("WORK_ABOVE_1X1", "1x1"),
				spriteEntry("TOOL_1X1", "1x1"),
				spriteEntry("MISC_BELOW_1X1", "1x1"),
				spriteEntry("MISC_ABOVE_1X1", "1x1")))));
		data.add(bonus());
		data.add(experienceBonus());
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(textWork());
		// All three stats are named by the game (Constructor.java:52,64-65), so the blocks only have to exist.
		text.add(textStats(WORKSHOP_STATS, 0, "workshop Constructor.java:52,64-65"));
		text.add(shaped("ITEMS", Kind.BLOCK_LIST, true, null, null, "Furnisher.java:102,185-186", new Shape(List.of(
				leaf("NAME", Kind.TEXT_LINE, true, null, null, null, true, "Furnisher.java:185"),
				leaf("DESC", Kind.TEXT_LINES, true, null, null, null, true, "Furnisher.java:186")), List.of(), 0, WORKSHOP_ITEMS, WORKSHOP_ITEMS)));
		text.add(textUpgrades());
		return new AssetSchema(data, text, List.of(),
				List.of("STATS are workers, then efficiency, then output. The game names all three, and only the efficiency number is used (Constructor.java:54-68, FurnisherStat.java:185-199).",
						"Sprites and sounds start empty. Choose them, or use Initialise. The first recipe output is what the room stores, and each sprite has one level per upgrade (Job.java:22, Furnisher.java:226-229).",
						"STORAGE isn't read. WORK.SOUND, the text file's BONUS, REQUIRES, EMPLOYMENT and a sprite's OVERWRITE aren't offered, and a file that already has them keeps them (Job.java:17, RoomBlueprintIns.java:66-72)."),
				RoomSchemas::workshopRules, List.of(unsetSound(SoundEntries.ROOM_WORK), unsetSound(SoundEntries.ROOM_CLICK), unsetSound(SoundEntries.ROOM_AMBIENCE)));
	}

	/** A pasture's data and text files. Sprites and sounds start empty, because no one vanilla pasture suits every animal. */
	private static AssetSchema pasture() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(key("ANIMAL", Reference.ANIMALS, true, null, "ROOM_PASTURE.java:126, RMAP.java:30-44, Animals.java:67"));
		data.add(leaf("INDOORS", Kind.BOOL, false, "false", null, null, true, "ROOM_PASTURE.java:130-132"));
		data.add(shaped("INDUSTRIES", Kind.BLOCK_LIST, true, null, null, "Industry.java:242-248, ROOM_PASTURE.java:252", new Shape(List.of(industry("Industry.java:113", "Industry.java:247-248")), List.of(), 0, 0, 0)));
		data.add(resources());
		data.add(areaCosts());
		// A single name works too (Furnisher.java:80), and one in a file is kept as it's written.
		data.add(new TagSpec("FLOOR", Kind.KEY_LIST, false, "No floor of its own", null, null, true, Reference.FLOORS, null, List.of(), "Furnisher.java:76-88,226-229, RMAP.java:168-192", Confidence.CODE_VERIFIED));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		data.add(work());
		data.add(shaped("ITEMS", Kind.BLOCK_LIST, true, null, null, "Furnisher.java:101-104,189-190, Constructor.java:79", new Shape(List.of(
				leaf("COSTS", Kind.DECIMALS, true, null, null, null, true, "Furnisher.java:189"),
				leaf("STATS", Kind.DECIMALS, true, null, null, null, true, "Furnisher.java:190")), List.of(), 0, PASTURE_ITEMS, PASTURE_ITEMS)));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, "Constructor.java:115, ConstructorIndoor.java:36, ConstructorOutdoor.java:56", Shape.block(List.of(
				spriteEntry("FENCE_COMBO", "combo"),
				spriteEntry("FENCE_D_COMBO", "combo"),
				spriteEntry("AUX_EDGE_1X1", "1x1"),
				spriteEntry("AUX_MID_1X1", "1x1"),
				spriteEntry("AUX_BIG_2X2", "2x2"),
				spriteEntry("STORAGE_1X1", "1x1"),
				// Only the outdoor constructor reads this (ConstructorOutdoor.java:60). An indoor file may still have it.
				spriteEntry("GATE_TOP_3X3", "3x3", false)))));
		data.add(bonus());
		data.add(experienceBonus());
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(textWork());
		// namedSlots means the first N blocks. Blocks 0 and 3 are the ones read, so all four are shown and the rule below requires those two.
		text.add(shaped("STATS", Kind.BLOCK_LIST, true, null, null, "Constructor.java:53-85, Furnisher.java:92-94, FurnisherStat.java:69-75", new Shape(List.of(
				leaf("NAME", Kind.TEXT_LINE, false, null, null, null, true, "FurnisherStat.java:74"),
				leaf("DESC", Kind.TEXT_LINES, false, null, null, null, true, "FurnisherStat.java:75")), List.of(), 0, PASTURE_STATS, PASTURE_STATS)));
		text.add(shaped("ITEMS", Kind.BLOCK_LIST, true, null, null, "Furnisher.java:102,185-186", new Shape(List.of(
				leaf("NAME", Kind.TEXT_LINE, true, null, null, null, true, "Furnisher.java:185"),
				leaf("DESC", Kind.TEXT_LINES, true, null, null, null, true, "Furnisher.java:186")), List.of(), 0, PASTURE_ITEMS, PASTURE_ITEMS)));
		text.add(textUpgrades());
		return new AssetSchema(data, text, List.of(),
				List.of("A pasture keeps one animal, named by ANIMAL. The game stops loading if that name isn't an animal file (ROOM_PASTURE.java:126, Animals.java:67).",
						"INDOORS absent or false is outdoors, and that constructor reads the gate. An indoor pasture doesn't. A gate left on an indoor one is not a problem (ROOM_PASTURE.java:130-132, ConstructorOutdoor.java:60).",
						"A recipe can't take an input, and it can have one, two or three outputs. More than three, or any input, stops loading (ROOM_PASTURE.java:255-258).",
						"STATS are four blocks. 0 is the workers and 3 is the fertile area, and the game reads their names. A blank is allowed. 1 is irrigation and 2 is efficiency, which the game names itself (Constructor.java:53-85, FurnisherStat.java:165-175,220).",
						"Each item's STATS is four numbers. Only the third, efficiency, is used. The other three are still required (FurnisherStat.java:185-199).",
						"Storage is 3000 in the code, not a STORAGE tag. Irrigation's 0.5 and 1.075 aren't tags, and how many animals fit comes from the species' mass (ROOM_PASTURE.java:62,128,279).",
						"DEGRADE_RATE is still read, and its default is 0.75, but this room never degrades, so the tag has no effect (ROOM_PASTURE.java:326-327,369-370).",
						"Sprites and sounds start empty. Choose them, or use Initialise. No one vanilla pasture suits every animal, and there is no service sound (ROOM_PASTURE.java:305-307).",
						"FENCE is on five vanilla files and nothing here reads it. WORK.SOUND, the text file's BONUS, REQUIRES, EMPLOYMENT and a sprite's OVERWRITE aren't offered, and a file that already has them keeps them."),
				RoomSchemas::pastureRules, List.of(unsetSound(SoundEntries.ROOM_WORK), unsetSound(SoundEntries.ROOM_CLICK), unsetSound(SoundEntries.ROOM_AMBIENCE)));
	}

	/** The ITEMS block in a data file, with its fixed count. */
	private static TagSpec dataItems(int count, String evidence) {
		return shaped("ITEMS", Kind.BLOCK_LIST, true, null, null, "Furnisher.java:101-104,189-190, " + evidence, new Shape(List.of(
				leaf("COSTS", Kind.DECIMALS, true, null, null, null, true, "Furnisher.java:189"),
				leaf("STATS", Kind.DECIMALS, true, null, null, null, true, "Furnisher.java:190")), List.of(), 0, count, count));
	}

	/** The ITEMS block in a text file, with its fixed count. */
	private static TagSpec textItems(int count) {
		return textItems(count, false);
	}

	/**
	 * The ITEMS block in a text file. {@code blankDesc} is for a type whose vanilla file has an item with DESC: "",
	 * which text() returns as it is (Furnisher.java:186), so left empty it's written blank.
	 */
	private static TagSpec textItems(int count, boolean blankDesc) {
		TagSpec desc = leaf("DESC", Kind.TEXT_LINES, true, null, null, null, true, "Furnisher.java:186");
		return shaped("ITEMS", Kind.BLOCK_LIST, true, null, null, "Furnisher.java:102,185-186", new Shape(List.of(
				leaf("NAME", Kind.TEXT_LINE, true, null, null, null, true, "Furnisher.java:185"),
				blankDesc ? desc.withBlankAllowed() : desc), List.of(), 0, count, count));
	}

	/** FLOOR as most rooms read it. A single name works too (Furnisher.java:80), and one in a file is kept as it's written. */
	private static TagSpec floorList(String gameDefault, String evidence) {
		return new TagSpec("FLOOR", Kind.KEY_LIST, false, gameDefault, null, null, true, Reference.FLOORS, null, List.of(), evidence, Confidence.CODE_VERIFIED);
	}

	/** A fishery's data and text files, from ROOM_FISHERY and the classes it builds on. */
	private static AssetSchema fishery() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(industry("Industry.java:113, ROOM_FISHERY.java:45", "fish/Job.java:32,183"));
		data.add(resources());
		data.add(areaCosts());
		// Read, but fish/Constructor.java:454-466 replaces putFloor without placing it and clears the ground.
		data.add(floorList("No floor, because a fishery clears it", "Furnisher.java:76-88, fish/Constructor.java:454-466"));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		data.add(work());
		data.add(dataItems(FOOD_ROOM_ITEMS, "fish/Constructor.java:141,310,358"));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, "fish/Constructor.java:155", Shape.block(List.of(
				spriteEntry("STORAGE_BOTTOM_1X1", "1x1"),
				spriteEntry("STORAGE_TOP_1X1", "1x1"),
				spriteEntry("MISC_TOP_1X1", "1x1"),
				spriteEntry("CANDLE_1X1", "1x1"),
				spriteEntry("AUX_EDGE_1X1", "1x1"),
				spriteEntry("AUX_MID_1X1", "1x1"),
				spriteEntry("AUX_BIG_2X2", "2x2"),
				spriteEntry("WORKTABLE_1X1", "1x1"),
				spriteEntry("MISC_1X1", "1x1"),
				spriteEntry("EDGE_COMBO", "combo"),
				spriteEntry("MISC_GROUND_1X1", "1x1")))));
		data.add(bonus());
		data.add(experienceBonus());
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(textWork());
		text.add(textStats(FISHERY_STATS, 2, "fish/Constructor.java:57,84,100,117,144"));
		text.add(textItems(FOOD_ROOM_ITEMS));
		text.add(textUpgrades());
		return new AssetSchema(data, text, List.of(),
				List.of("The text file's STATS are five blocks in a fixed order (fish/Constructor.java:57,84,100,117,144): 0 is the workers and 1 is the storage, both named here. "
						+ "2 is efficiency, 3 is deep sea and 4 is production, which the game names itself (FurnisherStat.java:175,405, fish/Constructor.java:117), so anything in them is never read. Vanilla's words in 2 and 4 have no effect.",
						"Each item's STATS is five numbers, in that same order. Only storage and efficiency are used. The game works out the workers and deep sea from the water in the room (fish/Constructor.java:60-77,125-132).",
						"The first OUT is what the room stores and what the boats catch (fish/Job.java:32,183, ROOM_FISHERY.java:125). A fishery has to be placed on shallow water.",
						"A new fishery's SPRITES and sounds start from the game's own FISHERY_NORMAL.",
						"STORAGE is in the vanilla file and nothing reads it: each storage tile holds a fixed amount (fish/Job.java:28). FLOOR is read and has no effect, because the fishery clears the ground (fish/Constructor.java:454-466).",
						"WORK.SOUND, the text file's BONUS, REQUIRES, EMPLOYMENT and a sprite's OVERWRITE aren't offered, and a file that already has them keeps them."),
				RoomSchemas::fisheryRules, List.of(SoundEntries.ROOM_WORK.like("ROOM_WORK_FISHERY_NORMAL"), SoundEntries.ROOM_CLICK.like("ROOM_CLICK_FISHERY_NORMAL"), SoundEntries.ROOM_AMBIENCE.like("ROOM_FISHERY_NORMAL")),
				new AssetSchema.Starter(STARTING_FISHERY, List.of("SPRITES")));
	}

	/** An orchard's data and text files, from ROOM_ORCHARD and the classes it builds on. */
	private static AssetSchema orchard() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(leaf("INDOORS", Kind.BOOL, true, null, null, null, true, "orchard/Constructor.java:140"));
		data.add(key("EXTRA_RESOURCE", Reference.RESOURCES, true, null, "ROOM_ORCHARD.java:72, RMAP.java:33-46"));
		data.add(leaf("EXTRA_RESOURCE_AMOUNT", Kind.INT, true, null, null, null, true, "ROOM_ORCHARD.java:72"));
		data.add(leaf("DAYS_TILL_GROWTH", Kind.INT, true, null, 8.0, 1024.0, false, "ROOM_ORCHARD.java:183"));
		data.add(leaf("RIPE_AT_PART_OF_YEAR", Kind.DECIMAL, true, null, 0.0, 1.0, true, "ROOM_ORCHARD.java:184"));
		data.add(industry("Industry.java:113, ROOM_ORCHARD.java:59", "ROOM_ORCHARD.java:79"));
		data.add(resources());
		data.add(areaCosts());
		data.add(floorList("No floor of its own", "Furnisher.java:76-88,218-223, orchard/Constructor.java:419"));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		data.add(work());
		data.add(dataItems(FOOD_ROOM_ITEMS, "orchard/Constructor.java:115,234,376"));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, "orchard/Constructor.java:143", Shape.block(List.of(
				spriteEntry("FRUIT_1X1", "1x1"),
				spriteEntry("TREE_1X1", "1x1"),
				spriteEntry("TREE_2X2", "2x2"),
				spriteEntry("EDGE_COMBO", "combo"),
				spriteEntry("STORAGE_1X1", "1x1")))));
		data.add(bonus());
		data.add(experienceBonus());
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(textWork());
		text.add(textStats(ORCHARD_STATS, 2, "orchard/Constructor.java:56,78,117-118"));
		text.add(textItems(FOOD_ROOM_ITEMS));
		text.add(textUpgrades());
		return new AssetSchema(data, text, List.of(),
				List.of("The text file's STATS are four blocks in a fixed order (orchard/Constructor.java:56,78,117-118): 0 is the soil's fertility and 1 is the workers, both named here. "
						+ "2 is irrigation and 3 is output, which the game names itself (FurnisherStat.java:221,405), so anything in them is never read. Vanilla's \"Yearly Output\" in block 3 has no effect.",
						"Each item's STATS is four numbers, in that same order. Only workers is used. The game works out fertility from the ground and irrigation from the water (orchard/Constructor.java:64-88, FurnisherStat.java:238-241).",
						"INDOORS true means it must be built indoors and false means outdoors, and an indoor orchard always has full fertility (orchard/Constructor.java:65-66,388-395).",
						"EXTRA_RESOURCE is what a felled tree gives, EXTRA_RESOURCE_AMOUNT how much of it. Storage is 3000 in the code, not a tag (orchard/Constructor.java:98).",
						"A new orchard's SPRITES and sounds start from the game's own ORCHARD_FRUIT.",
						"DEGRADE_RATE is still read, and this room never degrades, so the tag has no effect (orchard/Instance.java:228-230, ROOM_ORCHARD.java:149).",
						"WORK.SOUND, WORK.USES_TOOL, the text file's BONUS, REQUIRES, EMPLOYMENT and a sprite's OVERWRITE aren't offered, and a file that already has them keeps them."),
				RoomSchemas::orchardRules, List.of(SoundEntries.ROOM_WORK.like("ROOM_WORK_ORCHARD_FRUIT"), SoundEntries.ROOM_CLICK.like("ROOM_CLICK_ORCHARD_FRUIT"), SoundEntries.ROOM_AMBIENCE.like("ROOM_ORCHARD_FRUIT")),
				new AssetSchema.Starter(STARTING_ORCHARD, List.of("SPRITES")));
	}

	/** A hunter's data and text files, from ROOM_HUNTER and the classes it builds on. It's the one room here without EXPERIENCE_BONUS, which it never reads. */
	private static AssetSchema hunter() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(leaf("MAX_EMPLOYED", Kind.INT, true, null, 1.0, 10000.0, false, "ROOM_HUNTER.java:63"));
		data.add(shaped("INDUSTRIES", Kind.BLOCK_LIST, true, null, null, "Industry.java:242-248, ROOM_HUNTER.java:104", new Shape(List.of(industry("Industry.java:113", "Industry.java:247-248")), List.of(), 0, 0, 0)));
		data.add(resources());
		data.add(areaCosts());
		data.add(floorList("No floor of its own", "Furnisher.java:76-88,218-223"));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		data.add(work());
		data.add(dataItems(FOOD_ROOM_ITEMS, "hunter/Constructor.java:77,161,204"));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, "hunter/Constructor.java:80", Shape.block(List.of(
				spriteEntry("TABLE_COMBO", "combo"),
				spriteEntry("NICKNACK_1X1", "1x1"),
				spriteEntry("STORAGE_1X1", "1x1")))));
		data.add(bonus());
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(textWork());
		// namedSlots means the first N blocks. Blocks 0 and 2 are the ones read, so all three are shown and the rule requires those two.
		text.add(shaped("STATS", Kind.BLOCK_LIST, true, null, null, "hunter/Constructor.java:35,50,52, Furnisher.java:92-94, FurnisherStat.java:69-75", new Shape(List.of(
				leaf("NAME", Kind.TEXT_LINE, false, null, null, null, true, "FurnisherStat.java:74"),
				leaf("DESC", Kind.TEXT_LINES, false, null, null, null, true, "FurnisherStat.java:75")), List.of(), 0, HUNTER_STATS, HUNTER_STATS)));
		text.add(textItems(FOOD_ROOM_ITEMS));
		text.add(textUpgrades());
		return new AssetSchema(data, text, List.of(),
				List.of("STATS are three blocks. 0 is the workers and 2 is the output, and the game reads their names. A blank is allowed. 1 is efficiency, which the game names itself (hunter/Constructor.java:35,50,52, FurnisherStat.java:175).",
						"Each item's STATS is three numbers, for workers, efficiency and output. The game works out output, so the first two are the ones that matter (hunter/Constructor.java:38-40,52-64, FurnisherStat.java:185-199).",
						"Each block in INDUSTRIES is a recipe, and needs at least one OUT. A hunter's recipes aren't used as a source of a resource (ROOM_HUNTER.java:106, Creator.java:64), so a resource only a hunter makes still counts as having no producer.",
						"MAX_EMPLOYED is how many hunters work at full effect. Over it, each one is less effective (ROOM_HUNTER.java:110-116). The room must be indoors (hunter/Constructor.java:215-217).",
						"A new hunter's SPRITES and sounds start from the game's own HUNTER_NORMAL. A hunter has no EXPERIENCE_BONUS, because it never reads one.",
						"WORK.SOUND, WORK.USES_TOOL, the text file's BONUS, REQUIRES, EMPLOYMENT and a sprite's OVERWRITE aren't offered, and a file that already has them keeps them."),
				RoomSchemas::hunterRules, List.of(SoundEntries.ROOM_WORK.like("ROOM_WORK_HUNTER_NORMAL"), SoundEntries.ROOM_CLICK.like("ROOM_CLICK_HUNTER_NORMAL"), SoundEntries.ROOM_AMBIENCE.like("ROOM_HUNTER_NORMAL")),
				new AssetSchema.Starter(STARTING_HUNTER, List.of("SPRITES")));
	}

	/** A service room's SERVICE block in its data file. Without the block, json() stops loading (RoomService.java:47). */
	private static TagSpec dataService() {
		return shaped("SERVICE", Kind.BLOCK, true, null, null, "RoomService.java:47", Shape.block(List.of(
				leaf("RADIUS", Kind.INT, false, "150", 0.0, 50000.0, false, "RoomService.java:52"))));
	}

	/** A service room's SERVICE block in its text file. VERB is the only tag read (RoomService.java:45). */
	private static TagSpec textService() {
		return shaped("SERVICE", Kind.BLOCK, true, null, null, "RoomService.java:45", Shape.block(List.of(
				leaf("VERB", Kind.TEXT_LINE, true, null, null, null, true, "RoomService.java:45"))));
	}

	/** RELIGION, read with RELIGIONS' own key. An unknown name stops loading (RMAP.java:30-44, RELIGIONS.java:28). */
	private static TagSpec religion(String evidence) {
		return key("RELIGION", Reference.RELIGIONS, true, null, evidence + ", RMAP.java:30-44, RELIGIONS.java:28");
	}

	/** A temple's data and text files, from ROOM_TEMPLE and the classes it builds on. */
	private static AssetSchema temple() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(religion("ROOM_TEMPLE.java:56"));
		data.add(leaf("SACRIFICE_TYPE", Kind.PLAIN, true, null, null, null, true, "ROOM_TEMPLE.java:57-78"));
		// Read only when SACRIFICE_TYPE is RESOURCE (ROOM_TEMPLE.java:59). The rule below asks for it then.
		data.add(key("SACRIFICE_RESOURCE", Reference.RESOURCES, false, null, "ROOM_TEMPLE.java:59, RMAP.java:30-44"));
		data.add(leaf("SACRIFICE_TIME", Kind.DECIMAL, true, null, 0.0, 10.0, true, "ROOM_TEMPLE.java:81"));
		data.add(resources());
		data.add(areaCosts());
		data.add(floorList("No floor of its own", "Furnisher.java:76-88,218-223"));
		data.add(key("FLOOR_PATH", Reference.FLOORS, true, null, "TempleConstructor.java:97,344, RMAP.java:30-44"));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		// Every vanilla temple has WORK, and its priests work through it (TempleJob.java:56,68). Nothing proves a temple without it fails, so it isn't required.
		data.add(shaped("WORK", Kind.BLOCK, false, "No priests work here", null, "RoomBlueprintIns.java:66-72, TempleJob.java:56,68", work().shape()));
		data.add(dataItems(TEMPLE_ITEMS, "TempleConstructor.java:53"));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, "TempleConstructor.java:99", Shape.block(List.of(
				spriteEntry("PEDISTAL_BOX", "box"),
				spriteEntry("ALTAR_BOX", "box"),
				spriteEntry("EMBLEM_1X1", "1x1"),
				spriteEntry("EMBLEM_2X2", "2x2"),
				spriteEntry("NICHE_A_1X1", "1x1"),
				spriteEntry("NICHE_B_1X1", "1x1"),
				spriteEntry("NICHE_C_1X1", "1x1"),
				spriteEntry("NICHE_D_1X1", "1x1"),
				spriteEntry("TORCH_1X1", "1x1"),
				spriteEntry("COFFIN_A_1X1", "1x1")))));
		data.add(dataService());
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(shaped("WORK", Kind.BLOCK, false, null, null, EMPLOYMENT + "73", textWork().shape()));
		// namedSlots means the first N blocks. Blocks 2, 3 and 4 are the ones read, so all five are shown and the rule requires those three.
		text.add(shaped("STATS", Kind.BLOCK_LIST, true, null, null, "TempleConstructor.java:56-73, Furnisher.java:92-94, FurnisherStat.java:57-67,133-136", new Shape(List.of(
				leaf("NAME", Kind.TEXT_LINE, false, null, null, null, true, "FurnisherStat.java:62"),
				leaf("DESC", Kind.TEXT_LINES, false, null, null, null, true, "FurnisherStat.java:63")), List.of(), 0, TEMPLE_STATS, TEMPLE_STATS)));
		text.add(textItems(TEMPLE_ITEMS));
		text.add(textService());
		text.add(textUpgrades());
		return new AssetSchema(data, text, List.of(),
				List.of("RELIGION names a file in init/religion. A temple of a religion the toolkit can't create yet can only name an existing one.",
						"SACRIFICE_TYPE is RESOURCE, ANIMAL or HUMAN, and anything else stops loading. SACRIFICE_RESOURCE is read only for RESOURCE. ANIMAL sacrifices livestock (ROOM_TEMPLE.java:57-78).",
						"STATS are five blocks. 0 is the priests and 1 the worshippers, which the game names itself. 2 is decoration, 3 grandeur and 4 space, and the game reads their names. A blank is allowed (TempleConstructor.java:56-73, FurnisherStat.java:57-67,133-136).",
						"WORK is how priests are employed. Every vanilla temple has it, so leaving it out warns: the temple then has no priests, and whether it still works hasn't been checked in the code or the game.",
						"The text file's RELIGION block, SERVICE's NAME and NAMES, and the data file's SERVICE.SOUND aren't read by anything. A temple reads no BONUS or EXPERIENCE_BONUS. COFFIN_B_1X1 is on every vanilla temple and nothing reads it.",
						"A new temple's SPRITES and sounds start from the game's own TEMPLE_ATHURI, so its emblem is Athuri's until changed. Initialise copies another."),
				RoomSchemas::templeRules, List.of(SoundEntries.ROOM_WORK.like("ROOM_WORK_TEMPLE_ATHURI"), SoundEntries.ROOM_CLICK.like("ROOM_CLICK_TEMPLE_ATHURI"),
						SoundEntries.ROOM_AMBIENCE.like("ROOM_TEMPLE_ATHURI"), SoundEntries.ROOM_SERVICE.like("ROOM_SERVICE_TEMPLE_ATHURI")),
				new AssetSchema.Starter(STARTING_TEMPLE, List.of("SPRITES")));
	}

	/** A shrine's data and text files, from ROOM_SHRINE and the classes it builds on. */
	private static AssetSchema shrine() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(religion("ROOM_SHRINE.java:30"));
		data.add(resources());
		data.add(areaCosts());
		data.add(floorList("No floor of its own", "Furnisher.java:76-88,218-223"));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		data.add(dataItems(SHRINE_ITEMS, "shrine/Constructor.java:307,568"));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, "shrine/Constructor.java:310", Shape.block(List.of(
				spriteEntry("ALTAR_FLOOR_TEXTURE", "texture"),
				spriteEntry("ALTAR_FLOOR_SCRIBBLE1_TEXTURE", "texture"),
				spriteEntry("ALTAR_FLOOR_SCRIBBLE2_TEXTURE", "texture"),
				spriteEntry("INNER_BOX", "box"),
				spriteEntry("ALTAR_BOX", "box"),
				spriteEntry("EMBLEM_1X1", "1x1"),
				spriteEntry("EMBLEM_2X2", "2x2"),
				spriteEntry("TORCH_1X1", "1x1")))));
		data.add(dataService());
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(textStats(SHRINE_STATS, 1, "shrine/Constructor.java:31, FurnisherStat.java:255-259"));
		// Vanilla SHRINE_AMINION's item has DESC: "", which text() returns as it is. So it's required, and a blank works: left empty, it's written blank.
		text.add(shaped("ITEMS", Kind.BLOCK_LIST, true, null, null, "Furnisher.java:102,185-186", new Shape(List.of(
				leaf("NAME", Kind.TEXT_LINE, true, null, null, null, true, "Furnisher.java:185"),
				leaf("DESC", Kind.TEXT_LINES, true, null, null, null, true, "Furnisher.java:186").withBlankAllowed()), List.of(), 0, SHRINE_ITEMS, SHRINE_ITEMS)));
		text.add(textService());
		text.add(textUpgrades());
		return new AssetSchema(data, text, List.of(),
				List.of("RELIGION names a file in init/religion. A shrine of a religion the toolkit can't create yet can only name an existing one.",
						"STATS is one block, the worshippers, and the game reads its name (shrine/Constructor.java:31). Each item's STATS is one number.",
						"A shrine has one item, and the game sets its layouts and costs (shrine/Constructor.java:438-569). No vanilla shrine has WORK, and it isn't offered.",
						"SERVICE's NAME and NAMES in the text file, and SOUND in the data file, aren't read by anything. A shrine reads no BONUS or EXPERIENCE_BONUS.",
						"A new shrine's SPRITES and sounds start from the game's own SHRINE_ATHURI, so its emblem is Athuri's until changed. Initialise copies another."),
				RoomSchemas::shrineRules, List.of(SoundEntries.ROOM_CLICK.like("ROOM_CLICK_SHRINE_ATHURI"), SoundEntries.ROOM_AMBIENCE.like("ROOM_SHRINE_ATHURI"),
						SoundEntries.ROOM_SERVICE.like("ROOM_SERVICE_SHRINE_ATHURI")),
				new AssetSchema.Starter(STARTING_SHRINE, List.of("SPRITES")));
	}

	/**
	 * The SERVICE block of a room subjects get access to. RoomService reads RADIUS, and RoomServiceAccess the rest
	 * (RoomServiceAccess.java:48-52). STANDING is read by StandingDef's one-argument constructor, which doesn't
	 * refuse unknown keys (StatStanding.java:292-328, its check() is commented out).
	 */
	private static TagSpec dataAccessService() {
		return shaped("SERVICE", Kind.BLOCK, true, null, null, "RoomService.java:47, RoomServiceAccess.java:48", Shape.block(List.of(
				leaf("RADIUS", Kind.INT, false, "150", 0.0, 50000.0, false, "RoomService.java:52"),
				shaped("STANDING", Kind.BLOCK, true, null, null, "RoomServiceAccess.java:49, StatStanding.java:292-328", Shape.block(standingChildren())),
				new TagSpec("BOOST", Kind.DECIMAL_MAP, false, null, null, null, true, null, null, List.of(),
						"RoomServiceAccess.java:50-51, BoostSpecs.java:148-191. A key must end in >ADD or >MUL. Which stats can be boosted is decided in code, so this isn't a list.", Confidence.CODE_VERIFIED),
				leaf("USAGE", Kind.DECIMAL, false, "1", 0.0, 1.0, true, "RoomServiceAccess.java:52"))));
	}

	/** What StandingDef's one-argument constructor reads from a STANDING block (StatStanding.java:292-338). */
	private static List<TagSpec> standingChildren() {
		List<TagSpec> standing = new ArrayList<>(List.of(
				leaf("MULTIPLIER", Kind.DECIMAL, false, "1", 0.0, 100000.0, true, "StatStanding.java:305"),
				leaf("EXPONENT", Kind.DECIMAL, false, "1", 0.01, 10.0, true, "StatStanding.java:306"),
				leaf("PRIO", Kind.DECIMAL, false, "1", 0.0, 100000.0, true, "StatStanding.java:307, rounded down to a whole number"),
				leaf("INVERTED", Kind.BOOL, false, "false", null, null, true, "StatStanding.java:308"),
				leaf("CHILD", Kind.BOOL, false, "false", null, null, true, "StatStanding.java:309"),
				leaf("DISMISS", Kind.BOOL, false, "false", null, null, true, "StatStanding.java:310")));
		for (String clas : STANDING_CLASSES) {
			// The key comes from a field, so checkLimits can't match it, and the range is checked in the rule.
			standing.add(leaf(clas, Kind.DECIMAL, false, "0", null, null, true, "StatStanding.java:313-315,336-338, d from 0 to " + (long) MAX_STANDING + ". The range is checked beside the table, because the key is the class's name."));
		}
		return standing;
	}

	/** The SERVICE block in the text file of a room subjects get access to. MORE is read with texts, which needs the key (Json.java:127-133). */
	private static TagSpec textAccessService() {
		return shaped("SERVICE", Kind.BLOCK, true, null, null, "RoomService.java:45, RoomServiceAccess.java:47", Shape.block(List.of(
				leaf("VERB", Kind.TEXT_LINE, true, null, null, null, true, "RoomService.java:45"),
				shaped("MORE", Kind.TEXT_ROWS, true, null, null, "RoomServiceAccess.java:47, Json.java:127-133", new Shape(List.of(), List.of(), 0, 0, 0)))));
	}

	/** WORK in the data file where nothing proves the room needs it, in the form RoomEmployment reads. */
	private static TagSpec optionalWork(String absent, String evidence) {
		return shaped("WORK", Kind.BLOCK, false, absent, null, "RoomBlueprintIns.java:66-72, " + evidence, work().shape());
	}

	/** The text file's WORK for a room whose data WORK is optional. A rule asks for it when the data file has WORK. */
	private static TagSpec optionalTextWork() {
		return shaped("WORK", Kind.BLOCK, false, null, null, EMPLOYMENT + "73", textWork().shape());
	}

	/** STATS in a text file where the blocks the game reads aren't the first ones. A rule asks for the words on those. */
	private static TagSpec textStatsByPosition(int blocks, String evidence) {
		return shaped("STATS", Kind.BLOCK_LIST, true, null, null, evidence + ", Furnisher.java:92-94, FurnisherStat.java:69-75", new Shape(List.of(
				leaf("NAME", Kind.TEXT_LINE, false, null, null, null, true, "FurnisherStat.java:74"),
				leaf("DESC", Kind.TEXT_LINES, false, null, null, null, true, "FurnisherStat.java:75")), List.of(), 0, blocks, blocks));
	}

	private static final String ACCESS_SERVICE_NOTE = "SERVICE.STANDING is how much access to the room matters to each class, in the same form as an entry in a race's STATS. A key in BOOST must end in >ADD or >MUL. USAGE is from 0 to 1 (RoomServiceAccess.java:48-52).";
	private static final String UNOFFERED_NOTE = "WORK.SOUND, WORK.USES_TOOL, the text file's BONUS, REQUIRES, EMPLOYMENT and a sprite's OVERWRITE aren't offered, and a file that already has them keeps them. This room reads no BONUS or EXPERIENCE_BONUS.";
	private static final String SERVICE_SOUND_NOTE = "Vanilla's service sound for this room is an empty list, which isn't offered, so a new one's service sound starts empty: choose DUMMY for none.";

	/** A tavern's data and text files, from ROOM_TAVERN and the classes it builds on. */
	private static AssetSchema tavern() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(resources());
		data.add(areaCosts());
		data.add(floorList("No floor of its own", "Furnisher.java:76-88,218-223"));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		data.add(optionalWork("No tavern keepers work here", "tavern/TavernInstance.java:44-45"));
		data.add(dataItems(TAVERN_ITEMS, "tavern/Constructor.java:44,327,387, FurnisherItemTools.java:148-178"));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, "tavern/Constructor.java:49", Shape.block(List.of(
				spriteEntry("JUG_1X1", "1x1"),
				spriteEntry("JUG_FILL_1X1", "1x1"),
				spriteEntry("TABLE_COMBO", "combo"),
				spriteEntry("STORAGE_1X1", "1x1"),
				spriteEntry("CHAIR_1X1", "1x1"),
				spriteEntry("TABLE_DECOR_1X1", "1x1"),
				spriteEntry("MISC_1X1", "1x1"),
				spriteEntry("MISC_DECOR_1X1", "1x1"),
				spriteEntry("CARPET_COMBO", "combo")))));
		data.add(dataAccessService());
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(optionalTextWork());
		// Block 1 is the one read, so both are shown and the rule requires that one.
		text.add(textStatsByPosition(TAVERN_STATS, "tavern/Constructor.java:46-47"));
		text.add(textItems(TAVERN_ITEMS));
		text.add(textAccessService());
		text.add(textUpgrades());
		return new AssetSchema(data, text, List.of(),
				List.of("STATS are two blocks. 0 is the tables, which the game names itself, and 1 is coziness, whose words the game reads from here. A blank is allowed (tavern/Constructor.java:46-47, FurnisherStat.java:69-75,334).",
						"Each item's STATS is two numbers, for tables and coziness. The three items are tables, knick-knacks and carpets, and the carpet's layouts are made by the game (tavern/Constructor.java:231-389, FurnisherItemTools.java:103-121).",
						"A tavern serves the drinks that are marked to be served, for the THIRST need, and has to be built indoors (ROOM_TAVERN.java:46,54-58, tavern/Constructor.java:440).",
						ACCESS_SERVICE_NOTE,
						"WORK employs the tavern keepers. The vanilla tavern has it, so leaving it out warns: whether a tavern works without it hasn't been checked in the code or the game.",
						"Vanilla's SERVICE.DEFAULT_ACCESS, DEFAULT_VALUE and NEED aren't read by anything. The need is set in the code (ROOM_TAVERN.java:46).",
						"A new tavern's SPRITES and sounds start from the game's own TAVERN_NORMAL. " + SERVICE_SOUND_NOTE,
						UNOFFERED_NOTE),
				RoomSchemas::tavernRules, List.of(SoundEntries.ROOM_WORK.like("ROOM_WORK_TAVERN_NORMAL"), SoundEntries.ROOM_CLICK.like("ROOM_CLICK_TAVERN_NORMAL"),
						SoundEntries.ROOM_AMBIENCE.like("ROOM_TAVERN_NORMAL"), SoundEntries.ROOM_SERVICE),
				new AssetSchema.Starter(STARTING_TAVERN, List.of("SPRITES")));
	}

	/** An eatery's (food stall's) data and text files, from ROOM_EATERY and the classes it builds on. */
	private static AssetSchema eatery() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(resources());
		data.add(areaCosts());
		data.add(floorList("No floor of its own", "Furnisher.java:76-88,218-223"));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		data.add(optionalWork("No food vendors work here", "eatery/EateryInstance.java:51-52,74"));
		data.add(dataItems(EATERY_ITEMS, "eatery/Constructor.java:53,277"));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, "eatery/Constructor.java:58", Shape.block(List.of(
				spriteEntry("CRATE_BOTTOM_A_1X1", "1x1"),
				spriteEntry("CRATE_TOP_1X1", "1x1"),
				spriteEntry("STALL_BOTTOM_1X1", "1x1"),
				spriteEntry("STALL_TOP_1X1", "1x1"),
				spriteEntry("MISC_BOTTOM_1X1", "1x1"),
				spriteEntry("MISC_1X1", "1x1"),
				spriteEntry("CARPET_COMBO", "combo")))));
		data.add(dataAccessService());
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(optionalTextWork());
		// Both stats are named by the game (eatery/Constructor.java:55-56), so the blocks only have to exist.
		text.add(textStats(EATERY_STATS, 0, "eatery/Constructor.java:55-56"));
		text.add(textItems(EATERY_ITEMS));
		text.add(textAccessService());
		text.add(textUpgrades());
		return new AssetSchema(data, text, List.of(),
				List.of("The game calls an eatery a Food Stall.",
						"STATS are two blocks, storage and workers. The game names both itself (eatery/Constructor.java:55-56, FurnisherStat.java:279,334), so anything in them is never read. Vanilla's words have no effect.",
						"Each item's STATS is two numbers, for storage and workers. There is one item, the stall.",
						"An eatery serves the edible resources that are marked to be served, for the HUNGER need, and can be built outdoors (ROOM_EATERY.java:46,55-59, eatery/Constructor.java:288).",
						ACCESS_SERVICE_NOTE,
						"WORK employs the food vendors. The vanilla eatery has it, so leaving it out warns: whether an eatery works without it hasn't been checked in the code or the game.",
						"Vanilla's USAGE at the top of the file isn't read: SERVICE.USAGE is the one that counts. Its CRATE_BOTTOM_B_1X1 sprite isn't read either, because that read is commented out (eatery/Constructor.java:62).",
						"A new eatery's SPRITES and sounds start from the game's own EATERY_NORMAL. " + SERVICE_SOUND_NOTE,
						UNOFFERED_NOTE),
				RoomSchemas::eateryRules, List.of(SoundEntries.ROOM_WORK.like("ROOM_WORK_EATERY_NORMAL"), SoundEntries.ROOM_CLICK.like("ROOM_CLICK_EATERY_NORMAL"),
						SoundEntries.ROOM_AMBIENCE.like("ROOM_EATERY_NORMAL"), SoundEntries.ROOM_SERVICE),
				new AssetSchema.Starter(STARTING_EATERY, List.of("SPRITES")));
	}

	/**
	 * A canteen's (restaurant's) one recipe. The first IN is the fuel its cooks fetch on their first job, so one is
	 * needed (canteen/SWork.java:114,142,166). Its Industry has no bonus (ROOM_CANTEEN.java:51), and any OUT becomes a
	 * recipe whose bonus is read as a game starts (recipe/Creator.java:67-94), so the rule refuses one.
	 */
	private static TagSpec canteenIndustry() {
		return noBonusIndustry("ROOM_CANTEEN.java:51", "canteen/SWork.java:114,142,166");
	}

	/** A recipe built with no bonus, whose first IN is a fuel the room's code uses without checking it's there. */
	private static TagSpec noBonusIndustry(String built, String fuel) {
		return shaped("INDUSTRY", Kind.BLOCK, true, null, null, "Industry.java:113, " + built, Shape.block(List.of(
				shaped("IN", Kind.KEYED_ROWS, true, null, Reference.RESOURCES, "Industry.java:114-121, " + fuel, Shape.rows(List.of(IN_RATE))),
				shaped("OUT", Kind.KEYED_ROWS, false, "Nothing produced", Reference.RESOURCES, "Industry.java:123-138, recipe/Creator.java:67-94", Shape.rows(List.of(OUT_PLAYER, OUT_AI, OUT_RECOVERY))),
				new TagSpec("ICON", Kind.ICON, false, "Built from what it produces", null, null, true, Reference.ICONS, null, List.of(), "Industry.java:141-142", Confidence.CODE_VERIFIED).withIconBlock())));
	}

	/** A canteen's (restaurant's) data and text files, from ROOM_CANTEEN and the classes it builds on. */
	private static AssetSchema canteen() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(resources());
		data.add(areaCosts());
		data.add(floorList("No floor of its own", "Furnisher.java:76-88,218-223"));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		// Without it employment() is null, and ROOM_CANTEEN.java:59 uses it while loading.
		data.add(shaped("WORK", Kind.BLOCK, true, null, null, "RoomBlueprintIns.java:66-72, ROOM_CANTEEN.java:59", work().shape()));
		data.add(dataItems(CANTEEN_ITEMS, "canteen/Constructor.java:42,254,335"));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, "canteen/Constructor.java:48", Shape.block(List.of(
				spriteEntry("PLATE_1X1", "1x1"),
				spriteEntry("TABLE_COMBO", "combo"),
				spriteEntry("OVEN_BENEATH_1X1", "1x1"),
				spriteEntry("OVEN_BENEATH_USED_1X1", "1x1"),
				spriteEntry("OVEN_1X1", "1x1"),
				spriteEntry("TOOLS_1X1", "1x1"),
				spriteEntry("STOOL_1X1", "1x1"),
				spriteEntry("DECOR_1X1", "1x1")))));
		data.add(dataAccessService());
		data.add(canteenIndustry());
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(textWork());
		// Block 2 is the one read, so all three are shown and the rule requires that one.
		text.add(textStatsByPosition(CANTEEN_STATS, "canteen/Constructor.java:44-46"));
		text.add(textItems(CANTEEN_ITEMS));
		text.add(textAccessService());
		text.add(textUpgrades());
		return new AssetSchema(data, text, List.of(),
				List.of("The game calls a canteen a Restaurant.",
						"STATS are three blocks. 0 is the guests and 1 the workers, which the game names itself. 2 is the tables, whose words the game reads from here. A blank is allowed (canteen/Constructor.java:44-46, FurnisherStat.java:69-75,279,334).",
						"Each item's STATS is three numbers, for guests, workers and tables. The two items are the cooking station and the tables.",
						"INDUSTRY's first IN is the fuel the cooks fetch, and a canteen needs one, or its cooks fail on their first job (canteen/SWork.java:114,142,166).",
						"A canteen can't produce anything. Its recipe has no bonus, so an OUT entry makes the game crash as a game starts (ROOM_CANTEEN.java:51, recipe/Creator.java:94). This is from reading the code, and hasn't been run. Vanilla's OUT is empty.",
						"WORK is required: the canteen uses it while loading (ROOM_CANTEEN.java:59). It's for the HUNGER need, and has to be built indoors (ROOM_CANTEEN.java:52, canteen/Constructor.java:346).",
						ACCESS_SERVICE_NOTE,
						"A new canteen's SPRITES and sounds start from the game's own CANTEEN_NORMAL. " + SERVICE_SOUND_NOTE,
						UNOFFERED_NOTE),
				RoomSchemas::canteenRules, List.of(SoundEntries.ROOM_WORK.like("ROOM_WORK_CANTEEN_NORMAL"), SoundEntries.ROOM_CLICK.like("ROOM_CLICK_CANTEEN_NORMAL"),
						SoundEntries.ROOM_AMBIENCE.like("ROOM_CANTEEN_NORMAL"), SoundEntries.ROOM_SERVICE),
				new AssetSchema.Starter(STARTING_CANTEEN, List.of("SPRITES")));
	}

	/**
	 * The SERVICE block of a room that satisfies a need: an access room's, plus NEED, which RoomServiceNeed reads
	 * with NEEDS' own key. An unknown name stops loading (RoomServiceNeed.java:10, NEEDS.java:40-41,68, RMAP.java:30-46).
	 */
	private static TagSpec dataNeedService() {
		List<TagSpec> children = new ArrayList<>();
		children.add(key("NEED", Reference.NEEDS, true, null, "RoomServiceNeed.java:10, NEEDS.java:40-41,68, RMAP.java:30-46"));
		children.addAll(dataAccessService().shape().children());
		return shaped("SERVICE", Kind.BLOCK, true, null, null, "RoomService.java:47, RoomServiceAccess.java:48, RoomServiceNeed.java:10", Shape.block(children));
	}

	private static final String NEED_NOTE = "SERVICE.NEED names a file in init/stats/need, and anything else stops loading (RoomServiceNeed.java:10, RMAP.java:30-46).";
	private static final String NEED_UNREAD_NOTE = "Vanilla's SERVICE.DEFAULT_ACCESS, DEFAULT_VALUE and SOUND aren't read by anything. The service sound is ROOM_SERVICE_ in the sound files.";

	/** A well's data and text files, from ROOM_WELL and the classes it builds on. */
	private static AssetSchema well() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(resources());
		data.add(areaCosts());
		data.add(floorList("No floor of its own", "Furnisher.java:76-88,218-223"));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		data.add(dataItems(WELL_ITEMS, "well/Constructor.java:42,197-218"));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, "well/Constructor.java:45", Shape.block(List.of(
				spriteEntry("STONE_RING_STENCIL_COMBO", "combo"),
				spriteEntry("ROOF_EDGE_1X1", "1x1"),
				spriteEntry("ROOF_MID_1X1", "1x1"),
				spriteEntry("FOUNTAIN_1X1", "1x1"),
				spriteEntry("STONE_RING_COMBO", "combo"),
				spriteEntry("BUCKET_1X1", "1x1")))));
		data.add(dataNeedService());
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(textStats(WELL_STATS, WELL_STATS, "well/Constructor.java:36"));
		text.add(textItems(WELL_ITEMS, true));
		text.add(textAccessService());
		text.add(textUpgrades());
		return new AssetSchema(data, text, List.of(),
				List.of("STATS is one block, the spots, and the game reads its words (well/Constructor.java:36, FurnisherStat.java:57-67). Each item's STATS is one number.",
						"A well has one item, the well, in three sizes made by the game, and can be built outdoors (well/Constructor.java:197-216,228-230).",
						NEED_NOTE,
						ACCESS_SERVICE_NOTE,
						"No vanilla well has WORK, and it isn't offered: nothing in a well gives its workers a job. " + NEED_UNREAD_NOTE,
						"A new well's SPRITES and sounds start from the game's own WELL_NORMAL.",
						"The text file's BONUS, REQUIRES, EMPLOYMENT and a sprite's OVERWRITE aren't offered, and a file that already has them keeps them. A well reads no BONUS or EXPERIENCE_BONUS."),
				RoomSchemas::wellRules, List.of(SoundEntries.ROOM_CLICK.like("ROOM_CLICK_WELL_NORMAL"), SoundEntries.ROOM_AMBIENCE.like("ROOM_WELL_NORMAL"),
						SoundEntries.ROOM_SERVICE.like("ROOM_SERVICE_WELL_NORMAL")),
				new AssetSchema.Starter(STARTING_WELL, List.of("SPRITES")));
	}

	/** A bath's (bathhouse's) data and text files, from ROOM_BATH and the classes it builds on. */
	private static AssetSchema bath() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(resources());
		data.add(areaCosts());
		data.add(floorList("No floor of its own", "Furnisher.java:76-88,218-223"));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		// Without it employment() is null, and ROOM_BATH.java:72 uses it while loading.
		data.add(shaped("WORK", Kind.BLOCK, true, null, null, "RoomBlueprintIns.java:66-72, ROOM_BATH.java:72", work().shape()));
		data.add(dataItems(BATH_ITEMS, "bath/Constructor.java:59,162-266"));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, "bath/Constructor.java:62", Shape.block(List.of(
				spriteEntry("FRAME_COMBO", "combo"),
				spriteEntry("POOL_FLOOR_TEXTURE", "texture"),
				spriteEntry("WORK_1X1", "1x1"),
				spriteEntry("OVEN_1X1", "1x1"),
				spriteEntry("ENTRANCE_1X1", "1x1"),
				spriteEntry("PIPE_1X1", "1x1"),
				spriteEntry("BENCH_1X1", "1x1"),
				spriteEntry("TORCH_1X1", "1x1")))));
		data.add(color("WATER_COLOR", "bath/Constructor.java:67"));
		data.add(leaf("WATER_OPACITY", Kind.DECIMAL, true, null, 0.0, 1.0, true, "bath/Constructor.java:68-69"));
		data.add(dataNeedService());
		data.add(noBonusIndustry("ROOM_BATH.java:61", "bath/BathInstance.java:110,131-133, bath/Oven.java:61-63,103"));
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(leaf("HEATING", Kind.TEXT_LINE, true, null, null, null, true, "ROOM_BATH.java:57"));
		text.add(leaf("HEATING_DESC", Kind.TEXT_LINES, true, null, null, null, true, "ROOM_BATH.java:58"));
		text.add(leaf("HEATING_PROBLEM", Kind.TEXT_LINES, true, null, null, null, true, "ROOM_BATH.java:59"));
		text.add(leaf("WATER_PROBLEM", Kind.TEXT_LINES, true, null, null, null, true, "ROOM_BATH.java:60"));
		text.add(textWork());
		text.add(textStats(BATH_STATS, BATH_STATS, "bath/Constructor.java:43,56"));
		text.add(textItems(BATH_ITEMS));
		text.add(textAccessService());
		text.add(textUpgrades());
		return new AssetSchema(data, text, List.of(),
				List.of("The game calls a bath a Bathhouse.",
						"STATS are two blocks, the baths and relaxation, and the game reads the words of both (bath/Constructor.java:43,56, FurnisherStat.java:57-80).",
						"Each item's STATS is two numbers, for baths and relaxation. The two items are the basin and the benches. A bath has to be built indoors (bath/Constructor.java:277-279).",
						"INDUSTRY's first IN is the fuel that heats the basins, and a bath needs one: the room's heat uses it on every update without checking it's there (bath/BathInstance.java:110,131-133).",
						"A bath can't produce anything. Its recipe has no bonus, so an OUT entry makes the game crash as a game starts (ROOM_BATH.java:61, recipe/Creator.java:94). This is from reading the code, and hasn't been run. Vanilla has no OUT.",
						"WORK is required: the bath uses it while loading (ROOM_BATH.java:72).",
						"WATER_COLOR and WATER_OPACITY are the look of the water in the pools. The opacity is from 0 to 1 (bath/Constructor.java:67-69).",
						NEED_NOTE,
						ACCESS_SERVICE_NOTE,
						NEED_UNREAD_NOTE,
						"A new bath's SPRITES and sounds start from the game's own BATH_NORMAL. " + SERVICE_SOUND_NOTE,
						UNOFFERED_NOTE),
				RoomSchemas::bathRules, List.of(SoundEntries.ROOM_WORK.like("ROOM_WORK_BATH_NORMAL"), SoundEntries.ROOM_CLICK.like("ROOM_CLICK_BATH_NORMAL"),
						SoundEntries.ROOM_AMBIENCE.like("ROOM_BATH_NORMAL"), SoundEntries.ROOM_SERVICE),
				new AssetSchema.Starter(STARTING_BATH, List.of("SPRITES")));
	}

	/** A lavatory's data and text files, from ROOM_LAVATORY and the classes it builds on. */
	private static AssetSchema lavatory() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(resources());
		data.add(areaCosts());
		data.add(floorList("No floor of its own", "Furnisher.java:76-88,218-223"));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		data.add(optionalWork("No plumbers work here", "lavatory/Lavatory.java:115-117,194-199"));
		data.add(dataItems(LAVATORY_ITEMS, "lavatory/Constructor.java:35,162-255"));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, "lavatory/Constructor.java:42", Shape.block(List.of(
				spriteEntry("NICKNACK_1X1", "1x1"),
				spriteEntry("SIT_COMBO", "combo"),
				spriteEntry("SHITHOLE_1X1", "1x1"),
				spriteEntry("SHIT_LID_1X1", "1x1"),
				spriteEntry("SHIT_1X1", "1x1"),
				spriteEntry("SIT_ONTOP_COMBO", "combo"),
				spriteEntry("TABLE_COMBO", "combo"),
				spriteEntry("BASIN_WATER_1X1", "1x1"),
				spriteEntry("BASIN_1X1", "1x1")))));
		data.add(dataNeedService());
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(optionalTextWork());
		// Block 2 is the one read, so all three are shown and the rule requires that one.
		text.add(textStatsByPosition(LAVATORY_STATS, "lavatory/Constructor.java:38-40"));
		text.add(textItems(LAVATORY_ITEMS, true));
		text.add(textAccessService());
		text.add(textUpgrades());
		return new AssetSchema(data, text, List.of(),
				List.of("STATS are three blocks. 0 is the latrines and 1 the workers, which the game names itself. 2 is the basins, whose words the game reads from here. A blank is allowed (lavatory/Constructor.java:38-40, FurnisherStat.java:69-75,279-334).",
						"Each item's STATS is three numbers, for latrines, workers and basins. The two items are the latrines and the basins. A lavatory has to be built indoors (lavatory/Constructor.java:264-266).",
						NEED_NOTE,
						ACCESS_SERVICE_NOTE,
						"WORK employs the plumbers who clean the latrines. Without it the lavatory loads, but each latrine stops serving after four uses, because only a worker cleans it (lavatory/Lavatory.java:115-117,194-199). So leaving it out warns.",
						"Vanilla's DEFAULT_STANDING at the top of the file isn't read by anything: SERVICE.STANDING is the one that counts. " + NEED_UNREAD_NOTE,
						"A new lavatory's SPRITES and sounds start from the game's own LAVATORY_NORMAL.",
						UNOFFERED_NOTE),
				RoomSchemas::lavatoryRules, List.of(SoundEntries.ROOM_WORK.like("ROOM_WORK_LAVATORY_NORMAL"), SoundEntries.ROOM_CLICK.like("ROOM_CLICK_LAVATORY_NORMAL"),
						SoundEntries.ROOM_AMBIENCE.like("ROOM_LAVATORY_NORMAL"), SoundEntries.ROOM_SERVICE.like("ROOM_SERVICE_LAVATORY_NORMAL")),
				new AssetSchema.Starter(STARTING_LAVATORY, List.of("SPRITES")));
	}

	/** A barber's data and text files, from ROOM_BARBER and the classes it builds on. */
	private static AssetSchema barber() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(resources());
		data.add(areaCosts());
		data.add(floorList("No floor of its own", "Furnisher.java:76-88,218-223"));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		// Read with d(key), so there's no range. Each visit takes this many days of work time, in sixteen steps (ROOM_BARBER.java:41, barber/Tile.java:53-54).
		data.add(leaf("WORK_TIME_IN_DAYS", Kind.DECIMAL, true, null, null, null, true, "ROOM_BARBER.java:41, barber/Tile.java:53-54,110-112"));
		data.add(optionalWork("No barbers work here", "barber/Tile.java:115-124,183"));
		data.add(dataItems(BARBER_ITEMS, "barber/Constructor.java:39,139-222, FurnisherItemTools.java:148-178"));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, "barber/Constructor.java:46", Shape.block(List.of(
				spriteEntry("NICKNACK_1X1", "1x1"),
				spriteEntry("TABLE_CENTRE_1X1", "1x1"),
				spriteEntry("TABLE_CENTRE_TOP_1X1", "1x1"),
				spriteEntry("TABLE_COMBO", "combo"),
				spriteEntry("CHAIR_1X1", "1x1"),
				spriteEntry("SEPARATOR_1X1", "1x1"),
				spriteEntry("CARPET_COMBO", "combo")))));
		data.add(dataNeedService());
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(optionalTextWork());
		// Block 2 is the one read, so all three are shown and the rule requires that one.
		text.add(textStatsByPosition(BARBER_STATS, "barber/Constructor.java:42-44"));
		text.add(textItems(BARBER_ITEMS));
		text.add(textAccessService());
		text.add(textUpgrades());
		return new AssetSchema(data, text, List.of(),
				List.of("STATS are three blocks. 0 is the services and 1 the workers, which the game names itself. 2 is the quality, whose words the game reads from here. A blank is allowed (barber/Constructor.java:42-44, FurnisherStat.java:69-75,279-334).",
						"Each item's STATS is three numbers, for services, workers and quality. The two items are the booths and the carpets, and the carpet's layouts are made by the game (barber/Constructor.java:139-222, FurnisherItemTools.java:103-121). A barber has to be built indoors (:231-233).",
						"WORK_TIME_IN_DAYS is how long a barber works on each visit, in days of work time. It has no limit in the code.",
						NEED_NOTE,
						ACCESS_SERVICE_NOTE,
						"WORK employs the barbers. Without it the barber loads but serves nobody: a booth can only be used after a barber has worked at it (barber/Tile.java:115-124,183). So leaving it out warns.",
						NEED_UNREAD_NOTE,
						"A new barber's SPRITES and sounds start from the game's own BARBER_NORMAL.",
						UNOFFERED_NOTE),
				RoomSchemas::barberRules, List.of(SoundEntries.ROOM_WORK.like("ROOM_WORK_BARBER_NORMAL"), SoundEntries.ROOM_CLICK.like("ROOM_CLICK_BARBER_NORMAL"),
						SoundEntries.ROOM_AMBIENCE.like("ROOM_BARBER_NORMAL"), SoundEntries.ROOM_SERVICE.like("ROOM_SERVICE_BARBER_NORMAL")),
				new AssetSchema.Starter(STARTING_BARBER, List.of("SPRITES")));
	}

	/** A physician's data and text files, from ROOM_PHYSICIAN and the classes it builds on. */
	private static AssetSchema physician() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(resources());
		data.add(areaCosts());
		data.add(floorList("No floor of its own", "Furnisher.java:76-88,218-223"));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		data.add(optionalWork("No physicians work here", "physician/Service.java:59,155-158"));
		data.add(dataItems(PHYSICIAN_ITEMS, "physician/Constructor.java:37,147-194"));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, "physician/Constructor.java:44", Shape.block(List.of(
				spriteEntry("SHELF_1X1", "1x1"),
				spriteEntry("SHELF_ONTOP_1X1", "1x1"),
				spriteEntry("BUNK_1X1_TOP", "1x1"),
				spriteEntry("BUNK_1X1_BOTTOM", "1x1"),
				spriteEntry("TABLE_ONTOP_1X1", "1x1"),
				spriteEntry("TABLE_COMBO", "combo"),
				spriteEntry("STORAGE_1X1", "1x1")))));
		data.add(dataNeedService());
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(optionalTextWork());
		// Blocks 0 and 2 are the ones read, so all three are shown and the rule requires those.
		text.add(textStatsByPosition(PHYSICIAN_STATS, "physician/Constructor.java:40-42"));
		text.add(textItems(PHYSICIAN_ITEMS));
		text.add(textAccessService());
		text.add(textUpgrades());
		return new AssetSchema(data, text, List.of(),
				List.of("STATS are three blocks. 0 is the physicians and 2 the quality, whose words the game reads from here. 1 is the services, which the game names itself (physician/Constructor.java:40-42, FurnisherStat.java:57-67,334).",
						"Each item's STATS is three numbers, for physicians, services and quality. The two items are the beds and the workbench with its shelves. A physician has to be built indoors (physician/Constructor.java:147-194,204-206).",
						NEED_NOTE,
						ACCESS_SERVICE_NOTE,
						"WORK employs the physicians. Without it the physician loads but treats nobody: a bed can only be used after a physician has worked at it (physician/Service.java:59,155-158). So leaving it out warns.",
						NEED_UNREAD_NOTE,
						"A new physician's SPRITES and sounds start from the game's own PHYSICIAN_NORMAL. " + SERVICE_SOUND_NOTE,
						UNOFFERED_NOTE),
				RoomSchemas::physicianRules, List.of(SoundEntries.ROOM_WORK.like("ROOM_WORK_PHYSICIAN_NORMAL"), SoundEntries.ROOM_CLICK.like("ROOM_CLICK_PHYSICIAN_NORMAL"),
						SoundEntries.ROOM_AMBIENCE.like("ROOM_PHYSICIAN_NORMAL"), SoundEntries.ROOM_SERVICE),
				new AssetSchema.Starter(STARTING_PHYSICIAN, List.of("SPRITES")));
	}

	/** A pleasure room's (massage parlour's) data and text files, from ROOM_PLEASURE and the classes it builds on. */
	private static AssetSchema pleasure() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(resources());
		data.add(areaCosts());
		data.add(floorList("No floor of its own", "Furnisher.java:76-88,218-223"));
		// Read with value(), so it can't be left out, then looked up with the file passed for the error (RMAP.java:69-83).
		data.add(key("FLOOR2", Reference.FLOORS, true, null, "pleasure/Constructor.java:60,390-396, RMAP.java:69-83"));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		data.add(color("COLOR_PIXEL_BASE", "pleasure/Constructor.java:65"));
		data.add(optionalWork("No masseuses work here", "pleasure/ABed.java:19,93-101,184-189"));
		data.add(dataItems(PLEASURE_ITEMS, "pleasure/Constructor.java:58,249-307, FurnisherItemTools.java:148-178"));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, "pleasure/Constructor.java:62", Shape.block(List.of(
				spriteEntry("BED_HEAD_1X1", "1x1"),
				spriteEntry("BED_TAIL_1X1", "1x1"),
				spriteEntry("TABLE_TOP_1X1", "1x1"),
				spriteEntry("TABLE_COMBO", "combo"),
				spriteEntry("SHELF_1X1", "1x1"),
				spriteEntry("SHELF_TOP_1X1", "1x1"),
				spriteEntry("NICKNACK_1X1", "1x1"),
				spriteEntry("WALLS_COMBO", "combo"),
				spriteEntry("CARPET_COMBO", "combo")))));
		data.add(dataNeedService());
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(optionalTextWork());
		text.add(textStats(PLEASURE_STATS, PLEASURE_STATS, "pleasure/Constructor.java:41-43"));
		text.add(textItems(PLEASURE_ITEMS));
		text.add(textAccessService());
		text.add(textUpgrades());
		return new AssetSchema(data, text, List.of(),
				List.of("The game calls a pleasure room a Massage Parlour.",
						"STATS are three blocks, the clients, coziness and workers, and the game reads the words of all three (pleasure/Constructor.java:41-43, FurnisherStat.java:57-80).",
						"Each item's STATS is three numbers, for clients, coziness and workers. The three items are the booths, the decorations and the carpets, and the carpet's layouts are made by the game (pleasure/Constructor.java:249-307, FurnisherItemTools.java:103-121). A pleasure room has to be built indoors (:327-329).",
						"FLOOR2 is the floor laid under the booths, and FLOOR the rest (pleasure/Constructor.java:390-396). COLOR_PIXEL_BASE is the base colour of the room's random pixels, darkened at random (:65-79).",
						NEED_NOTE,
						ACCESS_SERVICE_NOTE,
						"WORK employs the masseuses. Without it the room loads but serves nobody: a booth starts closed, and only a worker opens it (pleasure/ABed.java:19,93-101,184-189). So leaving it out warns.",
						NEED_UNREAD_NOTE,
						"A new pleasure room's SPRITES and sounds start from the game's own PLEASURE_NORMAL. " + SERVICE_SOUND_NOTE,
						UNOFFERED_NOTE),
				RoomSchemas::pleasureRules, List.of(SoundEntries.ROOM_WORK.like("ROOM_WORK_PLEASURE_NORMAL"), SoundEntries.ROOM_CLICK.like("ROOM_CLICK_PLEASURE_NORMAL"),
						SoundEntries.ROOM_AMBIENCE.like("ROOM_PLEASURE_NORMAL"), SoundEntries.ROOM_SERVICE),
				new AssetSchema.Starter(STARTING_PLEASURE, List.of("SPRITES")));
	}

	/** A market's data and text files, from ROOM_MARKET and the classes it builds on. */
	private static AssetSchema market() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(resources());
		data.add(areaCosts());
		data.add(floorList("No floor of its own", "Furnisher.java:76-88,218-223"));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		data.add(optionalWork("No merchants work here", "market/MarketInstance.java:54-55,73"));
		data.add(dataItems(MARKET_ITEMS, "market/Constructor.java:52,212-267"));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, "market/Constructor.java:57", Shape.block(List.of(
				spriteEntry("CRATE_1X1", "1x1"),
				spriteEntry("STALL_BOTTOM_COMBO", "combo"),
				spriteEntry("STALL_TOP_1X1", "1x1"),
				spriteEntry("MISC_BOTTOM_1X1", "1x1"),
				spriteEntry("MISC_1X1", "1x1"),
				spriteEntry("CARPET_COMBO", "combo")))));
		data.add(dataAccessService());
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(optionalTextWork());
		// Both stats are named by the game (market/Constructor.java:54-55), so the blocks only have to exist.
		text.add(textStats(MARKET_STATS, 0, "market/Constructor.java:54-55"));
		text.add(textItems(MARKET_ITEMS));
		text.add(textAccessService());
		text.add(textUpgrades());
		return new AssetSchema(data, text, List.of(),
				List.of("STATS are two blocks, storage and workers. The game names both itself (market/Constructor.java:54-55, FurnisherStat.java:279,334), so anything in them is never read. Vanilla's words have no effect.",
						"Each item's STATS is two numbers, for storage and workers. There is one item, the stall. A market can be built outdoors (market/Constructor.java:277-279).",
						"A market sells the furnishings and equipment races use, for the SHOPPING need, which is set in the code (ROOM_MARKET.java:49,62-86).",
						ACCESS_SERVICE_NOTE,
						"WORK employs the merchants. The vanilla market has it, so leaving it out warns: whether a market works without it hasn't been checked in the code or the game.",
						"Vanilla's SERVICE.NEED isn't read by anything. The need is set in the code (ROOM_MARKET.java:49).",
						"A new market's SPRITES and sounds start from the game's own MARKET_NORMAL. " + SERVICE_SOUND_NOTE,
						UNOFFERED_NOTE),
				RoomSchemas::marketRules, List.of(SoundEntries.ROOM_WORK.like("ROOM_WORK_MARKET_NORMAL"), SoundEntries.ROOM_CLICK.like("ROOM_CLICK_MARKET_NORMAL"),
						SoundEntries.ROOM_AMBIENCE.like("ROOM_MARKET_NORMAL"), SoundEntries.ROOM_SERVICE),
				new AssetSchema.Starter(STARTING_MARKET, List.of("SPRITES")));
	}

	private static final String SPECTATOR_NOTE = "Spectators gather around the room to watch while it's manned, and WORK is required: the room sets its workers' shift while loading, and stops loading without it.";

	/**
	 * A room with spectators: a need room whose own class sets its employment's shift as it loads, so WORK is
	 * required. Only the sprites, stats, items and notes differ between the four.
	 *
	 * @param vanilla the only vanilla file's name, such as STAGE_NORMAL
	 * @param workEvidence where the room class uses its employment while loading
	 * @param blankDesc true if vanilla's item has DESC: ""
	 */
	private static AssetSchema spectatorRoom(String vanilla, String workEvidence, int items, String itemsEvidence, String spritesEvidence, List<TagSpec> sprites, TagSpec stats,
			boolean blankDesc, List<String> notes, AssetSchema.CrossRule rules) {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(resources());
		data.add(areaCosts());
		data.add(floorList("No floor of its own", "Furnisher.java:76-88,218-223"));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		data.add(shaped("WORK", Kind.BLOCK, true, null, null, "RoomBlueprintIns.java:66-72, " + workEvidence, work().shape()));
		data.add(dataItems(items, itemsEvidence));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, spritesEvidence, Shape.block(sprites)));
		data.add(dataNeedService());
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(textWork());
		text.add(stats);
		text.add(textItems(items, blankDesc));
		text.add(textAccessService());
		text.add(textUpgrades());
		List<String> all = new ArrayList<>(notes);
		all.add(SPECTATOR_NOTE);
		all.add(NEED_NOTE);
		all.add(ACCESS_SERVICE_NOTE);
		all.add(NEED_UNREAD_NOTE);
		all.add("A new one's SPRITES and sounds start from the game's own " + vanilla + ".");
		all.add(UNOFFERED_NOTE);
		return new AssetSchema(data, text, List.of(), all, rules,
				List.of(SoundEntries.ROOM_WORK.like("ROOM_WORK_" + vanilla), SoundEntries.ROOM_CLICK.like("ROOM_CLICK_" + vanilla), SoundEntries.ROOM_AMBIENCE.like("ROOM_" + vanilla),
						SoundEntries.ROOM_SERVICE.like("ROOM_SERVICE_" + vanilla)),
				new AssetSchema.Starter("init/room/" + vanilla + ".txt", List.of("SPRITES")));
	}

	/** A stage's data and text files, from ROOM_STAGE and the classes it builds on. */
	private static AssetSchema stage() {
		return spectatorRoom("STAGE_NORMAL", "ROOM_STAGE.java:45", SPECTATOR_ITEMS, "StageConstructor.java:31,109-151", "StageConstructor.java:37",
				List.of(spriteEntry("A_BOX", "box"), spriteEntry("B_BOX", "box"), spriteEntry("C_BOX", "box")),
				textStats(SPECTATOR_STATS, 1, "StageConstructor.java:34-35"), true,
				List.of("STATS are two blocks. 0 is the actors, whose words the game reads from here, and 1 the spectators, which the game names itself (StageConstructor.java:34-35, FurnisherStat.java:57-67,334).",
						"Each item's STATS is two numbers, for actors and spectators. There is one item, the stage, in three sizes made by the game, and it can be built outdoors (StageConstructor.java:109-151,160-162)."),
				RoomSchemas::spectatorRules);
	}

	/** A speaker's data and text files, from ROOM_SPEAKER and the classes it builds on. */
	private static AssetSchema speaker() {
		return spectatorRoom("SPEAKER_NORMAL", "ROOM_SPEAKER.java:46", SPECTATOR_ITEMS, "SpeakerConstructor.java:32,71-77", "SpeakerConstructor.java:37",
				List.of(spriteEntry("CENTER_COMBO", "combo"), spriteEntry("FRAME_COMBO", "combo")),
				textStats(SPECTATOR_STATS, 0, "SpeakerConstructor.java:34-35"), true,
				List.of("STATS are two blocks, the workers and spectators. The game names both itself (SpeakerConstructor.java:34-35, FurnisherStat.java:279,334), so anything in them is never read. Vanilla's first block is empty.",
						"Each item's STATS is two numbers, for workers and spectators. There is one item, the speaker's platform, and it can be built outdoors (SpeakerConstructor.java:71-77,86-88)."),
				RoomSchemas::spectatorRules);
	}

	/** A fight pit's data and text files, from ROOM_FIGHTPIT and the classes it builds on. */
	private static AssetSchema fightPit() {
		return spectatorRoom("FIGHTPIT_NORMAL", "ROOM_FIGHTPIT.java:69", SPECTATOR_ITEMS, "arena/pit/ArenaConstructor.java:49,254-288", "arena/pit/ArenaConstructor.java:54",
				arenaSprites(false), textStats(SPECTATOR_STATS, 1, "arena/pit/ArenaConstructor.java:52-53"), false,
				List.of("STATS are two blocks. 0 is the gladiators, whose words the game reads from here, and 1 the spectators, which the game names itself (arena/pit/ArenaConstructor.java:52-53, FurnisherStat.java:57-67,334).",
						"Each item's STATS is two numbers, for gladiators and spectators. There is one item, the pit, in two sizes made by the game, and it can be built outdoors (arena/pit/ArenaConstructor.java:254-288,313-315).",
						"Vanilla's TYPE at the top of the file isn't read by anything: only monuments read a TYPE (ROOM_MONUMENTS.java:26-28)."),
				RoomSchemas::spectatorRules);
	}

	/** A grand arena's data and text files, from ROOM_ARENA and the classes it builds on. */
	private static AssetSchema grandArena() {
		return spectatorRoom("ARENAG_NORMAL", "ROOM_ARENA.java:61", SPECTATOR_ITEMS, "arena/grand/ArenaConstructor.java:61,129-133", "arena/grand/ArenaConstructor.java:63-66, CUtil.java:42-160",
				arenaSprites(true), textStats(SPECTATOR_STATS, SPECTATOR_STATS, "arena/grand/ArenaConstructor.java:71-98"), false,
				List.of("The game calls this a Grand Arena, and its files start ARENAG_.",
						"STATS are two blocks, the gladiators and spectators, and the game reads the words of both (arena/grand/ArenaConstructor.java:71-98, FurnisherStat.java:57-67). The game counts both from the arena's shape, so each item's two numbers are not what decides them (:136-176).",
						"There is one item, the entrance, placed on the arena's edge. The rest of the arena is laid out by the game from the area painted (arena/grand/ArenaConstructor.java:107-133). It can be built outdoors (:185-187).",
						"Vanilla's TYPE at the top of the file isn't read by anything: only monuments read a TYPE (ROOM_MONUMENTS.java:26-28)."),
				RoomSchemas::spectatorRules);
	}

	/**
	 * Every room the game builds from one file of its own can only be overridden. A mod's file of that name is read
	 * merged over the game's (Json.java:29-42), so a tag it leaves out keeps the game's value, not the default.
	 */
	private static final String OVERRIDE_NOTE = "The game builds this room from its own %s only, so a mod can't add another. A mod's file of that name is merged over the game's: each top-level tag it writes replaces the game's, and every tag it leaves out keeps the game's value (Json.java:29-42). A new one starts as a copy of the game's files. Its sounds are the game's own entries, which this form doesn't edit.";

	/** The same schema, as an override of the game's own file of this name, under assets/init/room and assets/text/room. */
	private static AssetSchema override(AssetSchema schema, String file) {
		List<String> notes = new ArrayList<>(schema.notes());
		notes.add(0, String.format(OVERRIDE_NOTE, file + ".txt"));
		return new AssetSchema(schema.dataTags(), schema.textTags(), schema.spawnBlocks(), notes, schema.crossRule(), List.of(), null, List.of(), null)
				.withOverride(new AssetSchema.Overridden("init/room/" + file + ".txt", "text/room/" + file + ".txt"));
	}

	/** The Court's data and text files, from ROOM_COURT and the classes it builds on. */
	private static AssetSchema court() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(resources());
		data.add(areaCosts());
		data.add(floorList("No floor of its own", "Furnisher.java:76-88,218-223"));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		data.add(optionalWork("No judges work here", "court/CourtInstance.java:53-54,165-176"));
		data.add(dataItems(COURT_ITEMS, "court/Constructor.java:39,148-217"));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, "court/Constructor.java:42", Shape.block(List.of(
				spriteEntry("TABLE_COMBO", "combo"),
				spriteEntry("CARPET_COMBO", "combo"),
				spriteEntry("TORCH_1X1", "1x1"),
				spriteEntry("GEM_1X1", "1x1"),
				spriteEntry("STAND_COMBO", "combo"),
				spriteEntry("CHAIR_1X1", "1x1"),
				spriteEntry("DECOR_A_1X1", "1x1"),
				spriteEntry("DECOR_B_1X1", "1x1"),
				spriteEntry("BENCH_A_1X1", "1x1"),
				spriteEntry("BENCH_B_1X1", "1x1"),
				spriteEntry("BENCH_C_1X1", "1x1")))));
		data.add(dataNeedService());
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(optionalTextWork());
		text.add(textStats(COURT_STATS, COURT_STATS, "court/Constructor.java:29-31"));
		text.add(textItems(COURT_ITEMS));
		text.add(textAccessService());
		text.add(textUpgrades());
		return override(new AssetSchema(data, text, List.of(),
				List.of("STATS are three blocks, the prisoners, judges and spectators, and the game reads the words of all three (court/Constructor.java:29-31, FurnisherStat.java:57-80).",
						"Each item's STATS is three numbers, for prisoners, judges and spectators. The two items are the judge's bench and the spectators' benches. A court has to be built indoors (court/Constructor.java:148-217,226-229).",
						NEED_NOTE,
						ACCESS_SERVICE_NOTE,
						"WORK employs the judges. The game's court has it, so leaving it out warns: whether a court works without it hasn't been checked in the code or the game.",
						"The game's own WORK spells FULFILLMENT as FULLFILLMENT, which nothing reads, so its fulfillment is the default 0.5 (RoomEmploymentSimple.java:79-82).",
						NEED_UNREAD_NOTE,
						UNOFFERED_NOTE),
				RoomSchemas::courtRules, List.of()), "_COURT");
	}

	/** A prison's or stockade's tags: a room with instances and no service, whose sprites and text differ. */
	private static List<TagSpec> lawData(TagSpec work, int items, String itemsEvidence, String spritesEvidence, List<TagSpec> sprites, TagSpec... extra) {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(resources());
		data.add(areaCosts());
		data.add(floorList("No floor of its own", "Furnisher.java:76-88,218-223"));
		data.addAll(List.of(extra));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		data.add(work);
		data.add(dataItems(items, itemsEvidence));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, spritesEvidence, Shape.block(sprites)));
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));
		return data;
	}

	private static List<TagSpec> lawText(int items, int stats, String statsEvidence) {
		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(optionalTextWork());
		text.add(textStats(stats, stats, statsEvidence));
		text.add(textItems(items));
		text.add(textUpgrades());
		return text;
	}

	/** The Prison's (dungeon's) data and text files, from ROOM_PRISON and the classes it builds on. */
	private static AssetSchema prison() {
		List<TagSpec> data = lawData(optionalWork("No jailers work here", "prison/Food.java:55, prison/Cell.java:36-41"), PRISON_ITEMS, "prison/Constructor.java:50,154-161",
				"prison/Constructor.java:54", List.of(
						spriteEntry("WALLS_COMBO", "combo"),
						spriteEntry("BARS_1X1", "1x1"),
						spriteEntry("LATRINE_EMPTY_1X1", "1x1"),
						spriteEntry("LATRINE_FULL_1X1", "1x1"),
						spriteEntry("FOOD_1X1", "1x1"),
						spriteEntry("MISC_1X1", "1x1"),
						spriteEntry("OPENING_1X1", "1x1"),
						spriteEntry("CANDLE_SHELF_1X1", "1x1")),
				// Read with value(), so it can't be left out, then looked up with the file passed for the error (RMAP.java:69-83).
				key("FLOOR2", Reference.FLOORS, true, null, "prison/Constructor.java:52, RMAP.java:69-83"));
		return override(new AssetSchema(data, lawText(PRISON_ITEMS, PRISON_STATS, "prison/Constructor.java:37-38"), List.of(),
				List.of("The game calls the prison a Dungeon.",
						"STATS are two blocks, the prisoners and guards, and the game reads the words of both (prison/Constructor.java:37-38, FurnisherStat.java:57-80).",
						"Each item's STATS is two numbers, for prisoners and guards. There is one item, the cell. A prison has to be built indoors (prison/Constructor.java:154-161,191-193).",
						"FLOOR2 is a second floor the prison lays, and FLOOR the rest (prison/Constructor.java:52).",
						"WORK employs the jailers. The prison's jobs take their name and sound from it, with no check that it's there (prison/Food.java:55, prison/Cell.java:36-41), so leaving it out warns.",
						UNOFFERED_NOTE),
				RoomSchemas::prisonRules, List.of()), "_PRISON");
	}

	/** The Stockade's data and text files, from ROOM_STOCKADE and the classes it builds on. */
	private static AssetSchema stockade() {
		List<TagSpec> data = lawData(optionalWork("No jailers work here", "stockade/Job.java:112,171"), STOCKADE_ITEMS, "stockade/Constructor.java:95,182-186",
				"stockade/Constructor.java:98", List.of(
						spriteEntry("WALL_1X1", "1x1"),
						spriteEntry("WALL_CORNER_1X1", "1x1"),
						spriteEntry("OPENING_1X1", "1x1"),
						spriteEntry("FOOD_1X1", "1x1"),
						spriteEntry("LATRINE_EMPTY_1X1", "1x1"),
						spriteEntry("LATRINE_FULL_1X1", "1x1"),
						spriteEntry("MISC_1X1", "1x1")));
		return override(new AssetSchema(data, lawText(STOCKADE_ITEMS, STOCKADE_STATS, "stockade/Constructor.java:45-70"), List.of(),
				List.of("STATS are two blocks, the guards and prisoners, and the game reads the words of both (stockade/Constructor.java:45-70, FurnisherStat.java:57-80).",
						"Each item's STATS is two numbers, for guards and prisoners. There is one item, the entrance, placed on the room's edge. A stockade can be built indoors or outdoors (stockade/Constructor.java:182-186,196-204).",
						"WORK employs the jailers. The stockade's jobs take their title and sound from it, with no check that it's there (stockade/Job.java:112,171), so leaving it out warns.",
						"The game's own SPRITES has CANDLE_SHELF_1X1, which the stockade never reads (stockade/Constructor.java:98-186).",
						UNOFFERED_NOTE),
				RoomSchemas::stockadeRules, List.of()), "_STOCKADE");
	}

	/** The Guard's (guardpost's) data and text files, from ROOM_GUARD and the classes it builds on. */
	private static AssetSchema guard() {
		TagSpec employment = shaped("EMPLOYMENT", Kind.BLOCK, false, "No guards work here", null, "RoomBlueprintIns.java:66-71, guard/ROOM_GUARD.java:40", work().shape());
		List<TagSpec> data = lawData(employment, GUARD_ITEMS, "guard/Constructor.java:59,243-300", "guard/Constructor.java:62", List.of(
				spriteEntry("FLOOR_BOX", "box"),
				spriteEntry("CARPET_COMBO", "combo"),
				spriteEntry("TORCH_BOTTOM_COMBO", "combo"),
				spriteEntry("TORCH_COMBO", "combo"),
				spriteEntry("FENCE_COMBO", "combo")));
		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(shaped("EMPLOYMENT", Kind.BLOCK, false, null, null, EMPLOYMENT + "73", textWork().shape()));
		text.add(textStats(GUARD_STATS, GUARD_STATS, "guard/Constructor.java:43"));
		text.add(textItems(GUARD_ITEMS));
		text.add(textUpgrades());
		return override(new AssetSchema(data, text, List.of(),
				List.of("The game calls the guard a Guardpost.",
						"STATS is one block, the guards, and the game reads its words (guard/Constructor.java:43, FurnisherStat.java:57-80).",
						"Each item's STATS is one number, the guards. The two items are the guardpost and the guard station. A guardpost can be built indoors or outdoors (guard/Constructor.java:243-300,446-453).",
						"EMPLOYMENT employs the guards, in the same form as another room's WORK, and its words are the text file's EMPLOYMENT. The game reads it only when there's no WORK (RoomBlueprintIns.java:66-71). The guards are hired through it with no check that it's there (guard/ROOM_GUARD.java:40, RoomEmploymentSimple.java:243-255), so leaving it out warns.",
						"WORK, the text file's BONUS, REQUIRES and a sprite's OVERWRITE aren't offered, and a file that already has them keeps them."),
				RoomSchemas::guardRules, List.of()), "_GUARD");
	}

	/** The Police's (secret police's) data and text files, from ROOM_POLICE and the classes it builds on. */
	private static AssetSchema police() {
		List<TagSpec> data = lawData(optionalWork("No secret police work here", "police/ROOM_POLICE.java:188-207"), POLICE_ITEMS, "police/PoliceConstructor.java:43,180-290",
				"police/PoliceConstructor.java:46", List.of(
						spriteEntry("BED_STRAP_1X1", "1x1"),
						spriteEntry("BED_1X1A", "1x1"),
						spriteEntry("CHAIR_1X1", "1x1"),
						spriteEntry("CAGE_A_1X1", "1x1"),
						spriteEntry("CAGE_A_TOP_1X1", "1x1"),
						spriteEntry("CAGE_B_1X1", "1x1"),
						spriteEntry("CAGE_LATCH_1X1", "1x1"),
						spriteEntry("TABLE_COMBO", "combo"),
						spriteEntry("NICKNACK_1X1", "1x1")));
		data.add(new TagSpec("BOOST", Kind.DECIMAL_MAP, false, "No boosts", null, null, true, null, null, List.of(),
				"police/ROOM_POLICE.java:131-132, BoostSpecs.java:136-191. A key must end in >ADD or >MUL. Which stats can be boosted is decided in code, so this isn't a list.", Confidence.CODE_VERIFIED));
		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(optionalTextWork());
		text.add(textStats(POLICE_STATS, 1, "police/PoliceConstructor.java:31-32"));
		text.add(textItems(POLICE_ITEMS));
		text.add(textUpgrades());
		return override(new AssetSchema(data, text, List.of(),
				List.of("The game calls this the Secret Police.",
						"STATS are two blocks. 0 is the prisoners, whose words the game reads from here, and 1 the efficiency, which the game names itself (police/PoliceConstructor.java:31-32, FurnisherStat.java:57-67,165-177).",
						"Each item's STATS is two numbers, for prisoners and efficiency. The five items are the rack bed, the rack chair, two cages and the auxiliaries. Secret police have to be built indoors (police/PoliceConstructor.java:180-290,379-386).",
						"BOOST is what the secret police add to their subjects, scaled by how many are employed for the population (police/ROOM_POLICE.java:113-132,185-207). A key must end in >ADD or >MUL.",
						"WORK employs the secret police. The room's boosts count its employees with no check that it's there (police/ROOM_POLICE.java:188-207), so leaving it out warns.",
						"The game's own SPRITES has BED_1X1B, which nothing reads: both halves of the bed are BED_1X1A (police/PoliceConstructor.java:46-60).",
						UNOFFERED_NOTE),
				RoomSchemas::policeRules, List.of()), "_POLICE");
	}

	/** The Stocks' data and text files, from ROOM_STOCKS and the classes it builds on. */
	private static AssetSchema stocks() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(resources());
		data.add(areaCosts());
		data.add(floorList("No floor of its own", "Furnisher.java:76-88,218-223"));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		data.add(dataItems(STOCKS_ITEMS, "stocks/MConstructor.java:37,60-80"));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, "stocks/MConstructor.java:43", Shape.block(List.of(
				spriteEntry("BOX", "box"),
				spriteEntry("STOCK_BELOW_1X1", "1x1"),
				spriteEntry("STOCK_TOP_1X1", "1x1")))));
		data.add(dataNeedService());
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		// The one stat is named by the game (stocks/MConstructor.java:39), so the block only has to exist.
		text.add(textStats(STOCKS_STATS, 0, "stocks/MConstructor.java:39"));
		// The game's own item has DESC: "", which the game reads as blank.
		text.add(textItems(STOCKS_ITEMS, true));
		text.add(textAccessService());
		text.add(textUpgrades());
		return override(new AssetSchema(data, text, List.of(),
				List.of("STATS is one block, the spectators, which the game names itself (stocks/MConstructor.java:39, FurnisherStat.java:279), so anything in it is never read.",
						"Each item's STATS is one number, the spectators. There is one item, the stock, and stocks can be built indoors or outdoors (stocks/MConstructor.java:60-80,180-188).",
						NEED_NOTE,
						ACCESS_SERVICE_NOTE,
						"The stocks have no workers, so WORK isn't offered.",
						"The game's own TYPE at the top of the file isn't read by anything: only monuments read a TYPE (ROOM_MONUMENTS.java:26-28). Nor is the text file's SERVICE.LESS: only MORE is read (RoomServiceAccess.java).",
						NEED_UNREAD_NOTE,
						UNOFFERED_NOTE),
				RoomSchemas::stocksRules, List.of()), "_STOCKS");
	}

	/** The Execution room's (scaffold's) data and text files, from ROOM_EXECTUTION and the classes it builds on. */
	private static AssetSchema execution() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(resources());
		data.add(areaCosts());
		data.add(floorList("No floor of its own", "Furnisher.java:76-88,218-223"));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		data.add(dataItems(EXECUTION_ITEMS, "execution/Constructor.java:39"));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, "execution/Constructor.java:42", Shape.block(List.of(
				spriteEntry("GALLOW_BEAM_LEFT_1X1", "1x1"),
				spriteEntry("GALLOW_BEAM_CENTRE_1X1", "1x1"),
				spriteEntry("GALLOW_BOTTOM_1X1", "1x1"),
				spriteEntry("GALLOW_PILLAR_1X1", "1x1"),
				spriteEntry("GALLOW_BOX_1X1", "1x1"),
				spriteEntry("GALLOW_NOOSE_1X1", "1x1"),
				spriteEntry("CHOP_TABLE_1X1", "1x1"),
				spriteEntry("CHOP_MAT_1X1", "1x1"),
				spriteEntry("GIBBET_CAGE_1X1", "1x1"),
				spriteEntry("GIBBET_DOOR_1X1", "1x1"),
				spriteEntry("CROSS_R_1X1", "1x1"),
				spriteEntry("CROSS_L_1X1", "1x1"),
				spriteEntry("CROSS_MID_1X1", "1x1"),
				spriteEntry("CROSS_POLE_1X1", "1x1"),
				spriteEntry("CROSS_C_1X1", "1x1"),
				spriteEntry("PODIUM_BOX", "box")))));
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(textStats(EXECUTION_STATS, EXECUTION_STATS, "execution/Constructor.java:33"));
		text.add(textItems(EXECUTION_ITEMS));
		text.add(textUpgrades());
		return override(new AssetSchema(data, text, List.of(),
				List.of("The game calls this room the Scaffolds.",
						"STATS is one block, the executions, and the game reads its words (execution/Constructor.java:33, FurnisherStat.java:57-80).",
						"Each item's STATS is one number, the executions. The four items are the gallows, the chopping block, the gibbet cage and the cross. Scaffolds can be built indoors or outdoors (execution/Constructor.java:434-442).",
						"The scaffolds have no workers, so WORK isn't offered: the room isn't one the game employs people in (ROOM_EXECTUTION.java:24).",
						"The game's own SERVICE, in both files, isn't read by any of the room's classes, so it warns. Whether code elsewhere reads it by name hasn't been ruled out.",
						"The text file's BONUS, REQUIRES and a sprite's OVERWRITE aren't offered, and a file that already has them keeps them."),
				RoomSchemas::executionRules, List.of()), "_EXECUTION");
	}

	/** The sprites an arena builds. A grand arena also reads CONSTRUCT_1X1 and STAIRS_SINGLE_1X1. */
	private static List<TagSpec> arenaSprites(boolean grand) {
		List<TagSpec> sprites = new ArrayList<>();
		if (grand) {
			sprites.add(spriteEntry("CONSTRUCT_1X1", "1x1"));
		}
		sprites.add(spriteEntry("WALL_1X1", "1x1"));
		sprites.add(spriteEntry("TOWER_1X1", "1x1"));
		sprites.add(spriteEntry("TORCH_1X1", "1x1"));
		sprites.add(spriteEntry("TORCH_TOP_1X1", "1x1"));
		if (grand) {
			sprites.add(spriteEntry("STAIRS_SINGLE_1X1", "1x1"));
		}
		sprites.add(spriteEntry("STAIRS_LEFT_1X1", "1x1"));
		sprites.add(spriteEntry("STAIRS_CENTRE_1X1", "1x1"));
		sprites.add(spriteEntry("STAIRS_RIGHT_1X1", "1x1"));
		sprites.add(spriteEntry("SEAT_BOX", "box"));
		sprites.add(spriteEntry("RIM_BOX", "box"));
		return sprites;
	}

	/** The same sound, with nothing filled in, because no one vanilla entry suits every asset. */
	private static SoundEntries.Slot unsetSound(SoundEntries.Slot slot) {
		return new SoundEntries.Slot(slot.prefix(), slot.folder(), null, slot.readerClass(), slot.evidence());
	}

	/**
	 * REQUIRES, which every room hands to Lockable (RoomBlueprintImp.java:73-74). Each key is a comparator, holding
	 * values the game makes while it runs, each with a number (Lockable.java:99-127). A name the game doesn't have
	 * only warns as the game starts, and leaves the room locked (Lockable.java:145-162), so the names aren't a list.
	 */
	private static TagSpec requires() {
		List<TagSpec> comparators = new ArrayList<>();
		for (String comparator : COMPARATORS) {
			comparators.add(new TagSpec(comparator, Kind.DECIMAL_MAP, false, null, null, null, true, null, null, List.of(),
					"Lockable.java:115-124, COMPARATOR.java:12-80. Each key names a value the game makes while it runs, such as PLAY_MODE_ and a mode's file name, so it isn't a list.",
					Confidence.CODE_VERIFIED));
		}
		return shaped("REQUIRES", Kind.BLOCK, false, "Nothing required", null, "RoomBlueprintImp.java:73-74, Lockable.java:99-127, view/sett/ui/room/construction/SCollection.java:72", Shape.block(comparators));
	}

	/**
	 * A monument's data and text files, from ROOM_MONUMENT, Imp and the classes they build on. A torch is a monument
	 * too, but the game reads its file with Torch instead (ROOM_MONUMENTS.java:26-32), so this type refuses one.
	 */
	private static AssetSchema monument() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(resources());
		data.add(areaCosts());
		data.add(floorList("No floor of its own", "Furnisher.java:76-88,218-223, Imp.java:182-185"));
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		data.add(leaf("SOLID", Kind.BOOL, false, "true", null, null, true, "ROOM_MONUMENT.java:88, Imp.java:83,117,151"));
		// The game passes the file's own count to Furnisher (MConstructor.java:25), so any number works, and the rules match it to SPRITES.
		data.add(shaped("ITEMS", Kind.BLOCK_LIST, true, null, null, "MConstructor.java:25, Furnisher.java:100-104,189-190", new Shape(List.of(
				leaf("COSTS", Kind.DECIMALS, true, null, null, null, true, "Furnisher.java:189"),
				leaf("STATS", Kind.DECIMALS, true, null, null, null, true, "Furnisher.java:190, MConstructor.java:25 gives a monument no stats, so this is []")), List.of(), 0, 0, 0)));
		data.add(shaped("STANDING", Kind.BLOCK, false, "0 for every class", null,
				"ROOM_MONUMENT.java:76, StatStanding.java:228,292-338. Without it the classes are read from the top of the file, where a monument has none", Shape.block(standingChildren())));
		data.add(new TagSpec("FULFILLMENT_BONUS", Kind.DECIMAL_MAP, false, null, null, null, true, null, null, List.of(),
				"ROOM_MONUMENT.java:121, StatsAccess.java:273-287, BoostSpecs.java:148-191. A key must end in >ADD or >MUL. Which stats can be boosted is decided in code, so this isn't a list.",
				Confidence.CODE_VERIFIED));
		data.add(leaf("MAX_VALUE", Kind.INT, true, null, null, null, true, "ROOM_MONUMENT.java:86, SettEnvMonument.java:179-180, StatsAccess.java:319"));
		data.add(shaped("SPRITES", Kind.BLOCK_LIST, true, null, null, "Imp.java:46-163", new Shape(List.of(
				spriteEntry("1x1", "1x1", false),
				spriteEntry("2x2", "2x2", false),
				spriteEntry("3x3", "3x3", false),
				spriteEntry("FLOOR_COMBO", "combo")), List.of(), 0, 0, 0)));
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));
		data.add(requires());

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		// Vanilla MONUMENT_BLOB's item has DESC: "", which text() returns as it is (Furnisher.java:186).
		text.add(shaped("ITEMS", Kind.BLOCK_LIST, true, null, null, "Furnisher.java:102,185-186, Json.java:214-219", new Shape(List.of(
				leaf("NAME", Kind.TEXT_LINE, true, null, null, null, true, "Furnisher.java:185"),
				leaf("DESC", Kind.TEXT_LINES, true, null, null, null, true, "Furnisher.java:186").withBlankAllowed()), List.of(), 0, 0, 0)));
		text.add(shaped("MORE", Kind.TEXT_ROWS, false, "A line the game makes", null, "ROOM_MONUMENT.java:84, Opinion.java:11,69-75", new Shape(List.of(), List.of(), 0, 0, 0)));
		text.add(shaped("LESS", Kind.TEXT_ROWS, false, "A line the game makes", null, "ROOM_MONUMENT.java:84, Opinion.java:12,69-75", new Shape(List.of(), List.of(), 0, 0, 0)));
		text.add(textUpgrades());
		AssetSchema.CrossRule rule = new AssetSchema.CrossRule() {
			@Override
			public void check(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
				monumentRules(values, Set.of(), findings);
			}

			@Override
			public void check(Map<String, String> values, GameAssetIndex index, Set<String> kept, List<AssetChecker.Finding> findings) {
				monumentRules(values, kept, findings);
			}
		};
		return new AssetSchema(data, text, List.of(),
				List.of("Each SPRITES block is one item in the build menu, with a size for each of 1x1, 2x2 and 3x3 it has, and FLOOR_COMBO drawn beneath. "
						+ "The game gives the item at the same place in ITEMS its costs, and in the text file its name, so ITEMS needs at least as many blocks (Imp.java:46-163, Furnisher.java:178-194).",
						"A monument has no stats, so each item's STATS is [] (MConstructor.java:25). It has no workers, recipes, BONUS or EXPERIENCE_BONUS.",
						"STANDING is how much a monument nearby matters to each class, in the same form as an entry in a race's STATS, where a race can change it under the monument's name. "
								+ "Left out, every class is 0. A key in FULFILLMENT_BONUS must end in >ADD or >MUL (ROOM_MONUMENT.java:76,121, StatsAccess.java:271-287).",
						"MAX_VALUE is how many monuments around a tile count in full (SettEnvMonument.java:179-180, StatsAccess.java:319). The game holds the count in 4 bits, so above "
								+ MONUMENT_MOST_VALUE + " it can't reach that, and at 0 it divides by zero.",
						"REQUIRES locks the room in the build menu until every condition holds (SCollection.java:72). Each key is a comparator, LESS, GREATER, GREATERE, EQUAL or NEQUAL, "
								+ "holding names of values with a number each, such as GREATERE: { PLAY_MODE_<mode file>: 1 }. Those names are made by the game while it runs, so the toolkit can't check them: "
								+ "a wrong one warns as the game starts and leaves the room locked (Lockable.java:145-162).",
						"A torch is a monument with TYPE: TORCH, which the game reads with other code (ROOM_MONUMENTS.java:26-29, Torch.java). The toolkit doesn't cover it yet, so TYPE isn't offered and a torch can't be opened here. "
								+ "STANDING_UPGRADE is read, but nothing uses it (StatsAccess.java:305 is commented out), so it isn't offered either.",
						"A new monument's sprites, items and their costs start from the game's own MONUMENT_SCULPTURE: a statue and a pillar, each in three sizes. Its sounds start from that monument's, which are DUMMY."),
				rule, List.of(SoundEntries.ROOM_CLICK.like("ROOM_CLICK_MONUMENT_SCULPTURE"), SoundEntries.ROOM_AMBIENCE.like("ROOM_MONUMENT_SCULPTURE")),
				new AssetSchema.Starter(STARTING_MONUMENT, List.of("RESOURCES", "AREA_COSTS", "ITEMS", "SPRITES")))
				.withOutOfScope(file -> file.get("TYPE") instanceof GameTree.Plain type && TORCH_TYPE.equals(type.value())
						? "is a torch (TYPE: TORCH), which the game reads with other code (ROOM_MONUMENTS.java:26-29). The toolkit doesn't cover torches yet, so it can't be opened here."
						: null);
	}

	/** The highest index plus one of a list's blocks that hold any of these tags, among keys such as ITEMS.2.COSTS. */
	private static int blockCount(Iterable<String> keys, String list, List<String> parts) {
		int count = 0;
		String head = list + ".";
		for (String key : keys) {
			if (!key.startsWith(head)) {
				continue;
			}
			String rest = key.substring(head.length());
			int dot = rest.indexOf('.');
			String index = dot < 0 ? rest : rest.substring(0, dot);
			String inside = dot < 0 ? "" : rest.substring(dot + 1);
			if (index.matches("\\d+") && (parts.isEmpty() || parts.stream().anyMatch(part -> inside.equals(part) || inside.startsWith(part + ".")))) {
				count = Math.max(count, Integer.parseInt(index) + 1);
			}
		}
		return count;
	}

	/**
	 * What a monument's table can't hold. Each SPRITES block calls flush once, which reads the item at its place in
	 * both files' ITEMS (Imp.java:162, Furnisher.java:178-194), and a block that makes no item stops it
	 * (FurnisherItemGroup.java:22-23). A value kept as written counts as there, because it's in the file.
	 */
	private static void monumentRules(Map<String, String> values, Set<String> kept, List<AssetChecker.Finding> findings) {
		int items = blockCount(values.keySet(), "ITEMS", List.of("COSTS", "STATS"));
		String resources = values.getOrDefault("RESOURCES", "");
		int needed = resources.isEmpty() ? 0 : resources.split(",", -1).length;
		for (int i = 0; i < items; i++) {
			itemLength(values, "ITEMS." + i + ".COSTS", needed, "RESOURCES has " + needed + (needed == 1 ? " entry" : " entries"), findings);
			itemLength(values, "ITEMS." + i + ".STATS", 0, "a monument has no stats", findings);
		}
		upgradeMasks(values, findings);

		boolean spritesKept = kept.contains("SPRITES");
		List<String> spriteKeys = new ArrayList<>(values.keySet());
		spriteKeys.addAll(kept);
		int sprites = blockCount(spriteKeys, "SPRITES", List.of());
		if (!spritesKept && sprites > items) {
			findings.add(new AssetChecker.Finding("SPRITES", AssetChecker.Severity.ERROR, "There " + (sprites == 1 ? "is 1 SPRITES block" : "are " + sprites + " SPRITES blocks") + " and "
					+ items + (items == 1 ? " item" : " items") + ". Each block is an item, and the game reads the item at the same place in ITEMS, so it stops loading without one (Furnisher.java:185-190)."));
		}
		for (int i = 0; i < sprites && !spritesKept; i++) {
			String block = "SPRITES." + i + ".";
			boolean sized = MONUMENT_SIZES.stream().anyMatch(size -> spriteKeys.stream().anyMatch(key -> key.equals(block + size) || key.startsWith(block + size + ".")));
			if (!sized) {
				findings.add(new AssetChecker.Finding("SPRITES." + i, AssetChecker.Severity.ERROR,
						"This block needs at least one of 1x1, 2x2 or 3x3. Without one it makes no item, and the game stops loading with \"No items declared\" (Imp.java:57-162, FurnisherItemGroup.java:22-23)."));
			}
		}
		// The text file's words. Only checked when the text file is part of what's checked, as statWords is.
		if (values.containsKey("NAME")) {
			int named = blockCount(values.keySet(), "ITEMS", List.of("NAME", "DESC"));
			if (named < items) {
				findings.add(new AssetChecker.Finding("ITEMS", AssetChecker.Severity.ERROR, "The text file has " + named + (named == 1 ? " item" : " items") + " and the data file " + items
						+ ". The game reads a name and description for each item in the data file, and stops loading with fewer (Furnisher.java:102, Json.java:214-219)."));
			}
		}

		for (Map.Entry<String, String> entry : values.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			if (key.startsWith("FULFILLMENT_BONUS.") && !key.endsWith(">ADD") && !key.endsWith(">MUL")) {
				findings.add(new AssetChecker.Finding(key, AssetChecker.Severity.ERROR, "A boost key has to end in >ADD or >MUL, or the game stops loading (BoostSpecs.java:167-177)."));
			}
			if (STANDING_CLASSES.stream().anyMatch(clas -> key.equals("STANDING." + clas))
					&& value.matches("-?(\\d+\\.?\\d*|\\.\\d+)") && (Double.parseDouble(value) < 0 || Double.parseDouble(value) > MAX_STANDING)) {
				findings.add(new AssetChecker.Finding(key, AssetChecker.Severity.ERROR, "This must be from 0 to " + (long) MAX_STANDING + ", or the game stops loading (StatStanding.java:337)."));
			}
		}
		String most = values.get("MAX_VALUE");
		if (most != null && most.matches("-?\\d+")) {
			long count = Long.parseLong(most);
			if (count < 1) {
				findings.add(new AssetChecker.Finding("MAX_VALUE", AssetChecker.Severity.WARNING,
						"At " + count + " no monument nearby ever counts, and the game divides by it to work out the monument's stat (SettEnvMonument.java:179, StatsAccess.java:319). What that does in a game hasn't been run."));
			} else if (count > MONUMENT_MOST_VALUE) {
				findings.add(new AssetChecker.Finding("MAX_VALUE", AssetChecker.Severity.WARNING,
						"The game holds this count in 4 bits, so it never gets past " + MONUMENT_MOST_VALUE + " and the stat never reaches full. Past that, the count around a tile wraps to 0 "
								+ "(ROOM_MONUMENT.java:45, Bitsmap1D.java:88-91, SettEnvMonument.java:179-180, StatsAccess.java:265,319)."));
			}
		}
	}

	/** A mine's data and text files, from ROOM_MINE and the classes it builds on. */
	private static AssetSchema mine() {
		List<TagSpec> data = new ArrayList<>();
		data.add(icon());
		data.add(key("MINABLE", Reference.MINABLES, true, null, "ROOM_MINE.java:46, RMAP.java:33-46"));
		// Furnisher also takes a list here (:76-84), but Constructor.java:113 reads one name.
		data.add(key("FLOOR", Reference.FLOORS, true, null, "Constructor.java:113, RMAP.java:33-46"));
		data.add(leaf("YEILD_WORKER_DAILY", Kind.DECIMAL, true, null, 0.0, 1000.0, true, "ROOM_MINE.java:83"));
		data.add(leaf("STORAGE", Kind.INT, true, null, 4.0, 500.0, false, "ROOM_MINE.java:100"));
		data.add(resources());
		data.add(areaCosts());
		data.add(color("MINI_COLOR", "Furnisher.java:113"));
		data.add(miniPattern());
		data.add(work());
		data.add(shaped("ITEMS", Kind.BLOCK_LIST, true, null, null, "Furnisher.java:101-104,189-190, Constructor.java:315,363", new Shape(List.of(
				leaf("COSTS", Kind.DECIMALS, true, null, null, null, true, "Furnisher.java:189"),
				leaf("STATS", Kind.DECIMALS, true, null, null, null, true, "Furnisher.java:190")), List.of(), 0, MINE_ITEMS, MINE_ITEMS)));
		data.add(shaped("SPRITES", Kind.BLOCK, true, null, null, "Constructor.java:117", Shape.block(List.of(
				spriteEntry("CONVEYOR_1X1", "1x1"),
				spriteEntry("STORAGE_1X1_TOP", "1x1"),
				spriteEntry("STORAGE_1X1_MID", "1x1"),
				spriteEntry("STORAGE_1X1_END", "1x1"),
				spriteEntry("SLAB_1X1", "1x1"),
				spriteEntry("AUX_1X1", "1x1"),
				spriteEntry("AUX_MEDIUM_A_1X1", "1x1"),
				spriteEntry("AUX_MEDIUM_B_1X1", "1x1"),
				spriteEntry("AUX_BIG_2X2", "2x2"),
				spriteEntry("WORK_1X1", "1x1")))));
		data.add(bonus());
		data.add(experienceBonus());
		data.add(upgrades());
		data.add(environmentEmit());
		data.add(leaf("DEGRADE_RATE", Kind.DECIMAL, false, "0.75", 0.0, 1.0, true, "RoomBlueprintImp.java:67-70"));

		List<TagSpec> text = new ArrayList<>(AssetCatalog.infoTagsFor());
		text.add(textWork());
		text.add(textStats(4, 2, "mine Constructor.java:54,71,95,105"));
		text.add(shaped("ITEMS", Kind.BLOCK_LIST, true, null, null, "Furnisher.java:102,185-186", new Shape(List.of(
				leaf("NAME", Kind.TEXT_LINE, true, null, null, null, true, "Furnisher.java:185"),
				leaf("DESC", Kind.TEXT_LINES, true, null, null, null, true, "Furnisher.java:186")), List.of(), 0, MINE_ITEMS, MINE_ITEMS)));
		text.add(textUpgrades());
		return new AssetSchema(data, text, List.of(),
				List.of("The text file's STATS are four blocks in a fixed order (mine Constructor.java:54,71,95,105): 0 is the workers and 1 is the deposits, both named here. "
						+ "2 is efficiency and 3 is output, which the game names itself (FurnisherStat.java:175,405), so they're written empty and anything in them is never read.",
						"A mine only produces where its MINABLE's deposits are on the map, so new deposits need a Minable resource file too.",
						"A new mine's SPRITES start from the game's own MINE_STONE. Each sprite has one level per upgrade, and a room with more upgrades than levels uses the last.",
						"Each item's STATS has four numbers, for workers, deposits, efficiency and output, in that order. The game works out workers and deposits from the room, whatever's written for them (Constructor.java:54-95), so the numbers that matter are for efficiency.",
						"REQUIRES isn't offered: its conditions have keys defined by other data files. A sprite's OVERWRITE isn't offered either, and either one in a file stays as it's written. Vanilla's SPRITE tag isn't read by the game."),
				RoomSchemas::mineRules, List.of(SoundEntries.ROOM_WORK.like("ROOM_WORK_MINE_STONE"), SoundEntries.ROOM_CLICK.like("ROOM_CLICK_MINE_STONE"), SoundEntries.ROOM_AMBIENCE.like("ROOM_MINE_STONE")),
				new AssetSchema.Starter(STARTING_MINE, List.of("SPRITES")));
	}

	/** A deposit's file, from Minable.java. It has no text file. */
	private static AssetSchema minable(ClassFileScanner scanner, List<String> unresolved) throws ClassFileScanner.ScanException {
		int[] size = minableSheet(scanner, unresolved);
		TagSpec.Sprite image = size == null ? null : new TagSpec.Sprite("sprite/resource/minable/", size[0], size[1]);
		List<TagSpec> data = new ArrayList<>();
		data.add(key("RESOURCE", Reference.RESOURCES, true, null, "Minable.java:50, RMAP.java:33-46"));
		data.add(leaf("ON_EVERY_MAP", Kind.BOOL, true, null, null, null, true, "Minable.java:44"));
		data.add(color("COLOR", "Minable.java:45, ColorImp.java:61"));
		data.add(color("MINIMAP_COLOR", "Minable.java:46"));
		data.add(leaf("FERTILITY_INCREASE", Kind.DECIMAL, true, null, -1.0, 1.0, true, "Minable.java:47"));
		data.add(new TagSpec("SPRITE", Kind.SPRITE, true, null, null, null, true, null, null, List.of(), "Minable.java:80-90,109", Confidence.CODE_VERIFIED, image));
		data.add(new TagSpec("TERRAIN", Kind.DECIMAL_MAP, false, "0 for every entry", 0.0, 1.0, true, Reference.TERRAINS, null, List.of(), "Minable.java:52, RMAP.java:200-219", Confidence.CODE_VERIFIED));
		data.add(leaf("OCCURENCE", Kind.DECIMAL, false, "1", 0.0, 1000.0, true, "Minable.java:62"));
		return new AssetSchema(data, List.of(), List.of(),
				List.of("A deposit has no text file. The game names it after its resource, and a mine reaches it through MINABLE."),
				RoomSchemas::minableRules, List.of());
	}

	/** The size given to the anonymous tile sheet (Minable.java:109). */
	private static int[] minableSheet(ClassFileScanner scanner, List<String> unresolved) throws ClassFileScanner.ScanException {
		ClassFileScanner.ScannedClass sprites = scanner.scan(MINABLE_SPRITES);
		if (sprites != null) {
			for (ClassFileScanner.ScannedMethod method : sprites.methods()) {
				if (method.name().equals("sprite")) {
					return AssetCatalog.sheetSize(method, scanner, "the deposit's image", unresolved);
				}
			}
		}
		unresolved.add("the deposit's image size couldn't be found.");
		return null;
	}

	/** Each item's lists have fixed lengths, read with Json.ds(key, size), which stops the game otherwise (Json.java:271-277, Furnisher.java:189-190). */
	private static void mineRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, MINE_ITEMS, MINE_STATS, "a mine", findings);
	}

	private static void refinerRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, REFINER_ITEMS, REFINER_STATS, "a refiner", findings);
	}

	private static void workshopRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, WORKSHOP_ITEMS, WORKSHOP_STATS, "a workshop", findings);
	}

	private static void pastureRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, PASTURE_ITEMS, PASTURE_STATS, "a pasture", findings);
		pastureRecipes(values, findings);
		pastureGate(values, findings);
		statWords(values, findings, 0, 3);
	}

	private static void fisheryRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, FOOD_ROOM_ITEMS, FISHERY_STATS, "a fishery", findings);
	}

	private static void orchardRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, FOOD_ROOM_ITEMS, ORCHARD_STATS, "an orchard", findings);
	}

	private static void templeRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, TEMPLE_ITEMS, TEMPLE_STATS, "a temple", findings);
		statWords(values, findings, 2, 3, 4);
		String type = values.get("SACRIFICE_TYPE");
		if (type != null && !type.isBlank() && !SACRIFICE_TYPES.contains(type.trim())) {
			findings.add(new AssetChecker.Finding("SACRIFICE_TYPE", AssetChecker.Severity.ERROR,
					"This must be RESOURCE, ANIMAL or HUMAN. The game stops loading for anything else (ROOM_TEMPLE.java:73-77)."));
		}
		if (type != null && type.trim().equals("RESOURCE") && !values.containsKey("SACRIFICE_RESOURCE")) {
			findings.add(new AssetChecker.Finding("SACRIFICE_RESOURCE", AssetChecker.Severity.MISSING,
					"A RESOURCE sacrifice needs the resource. The game stops loading without it (ROOM_TEMPLE.java:59)."));
		}
		optionalWorkRules(values, findings, "Every vanilla temple has WORK, which employs its priests. Without it the temple has none, and whether it still works hasn't been checked.");
	}

	/** For a room whose data WORK is optional: its absence warns, and with it the text file's words are needed. */
	private static void optionalWorkRules(Map<String, String> values, List<AssetChecker.Finding> findings, String absent) {
		boolean hasWork = values.containsKey("WORK.SHIFT_OFFSET");
		if (!hasWork) {
			findings.add(new AssetChecker.Finding("WORK", AssetChecker.Severity.WARNING, absent));
		}
		for (String part : List.of("TITLE", "VERB")) {
			if (hasWork && !values.containsKey("WORK." + part)) {
				findings.add(new AssetChecker.Finding("WORK." + part, AssetChecker.Severity.MISSING,
						"With WORK in the data file, the game reads this from the text file, and stops loading without it (" + EMPLOYMENT + "74-75)."));
			}
		}
	}

	private static void shrineRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, SHRINE_ITEMS, SHRINE_STATS, "a shrine", findings);
	}

	private static void tavernRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, TAVERN_ITEMS, TAVERN_STATS, "a tavern", findings);
		statWords(values, findings, 1);
		optionalWorkRules(values, findings, "The vanilla tavern has WORK, which employs its tavern keepers. Without it the tavern has none, and whether it still works hasn't been checked.");
		accessServiceRules(values, findings);
	}

	private static void eateryRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, EATERY_ITEMS, EATERY_STATS, "an eatery", findings);
		optionalWorkRules(values, findings, "The vanilla eatery has WORK, which employs its food vendors. Without it the eatery has none, and whether it still works hasn't been checked.");
		accessServiceRules(values, findings);
	}

	private static void canteenRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, CANTEEN_ITEMS, CANTEEN_STATS, "a canteen", findings);
		statWords(values, findings, 2);
		accessServiceRules(values, findings);
		if (values.keySet().stream().anyMatch(key -> key.startsWith("INDUSTRY.OUT."))) {
			findings.add(new AssetChecker.Finding("INDUSTRY.OUT", AssetChecker.Severity.ERROR,
					"A canteen can't produce anything. Its recipe has no bonus, so an output makes the game crash as a game starts (ROOM_CANTEEN.java:51, recipe/Creator.java:94)."));
		}
	}

	private static void wellRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, WELL_ITEMS, WELL_STATS, "a well", findings);
		accessServiceRules(values, findings);
	}

	private static void bathRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, BATH_ITEMS, BATH_STATS, "a bath", findings);
		accessServiceRules(values, findings);
		if (values.keySet().stream().anyMatch(key -> key.startsWith("INDUSTRY.OUT."))) {
			findings.add(new AssetChecker.Finding("INDUSTRY.OUT", AssetChecker.Severity.ERROR,
					"A bath can't produce anything. Its recipe has no bonus, so an output makes the game crash as a game starts (ROOM_BATH.java:61, recipe/Creator.java:94)."));
		}
	}

	private static void lavatoryRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, LAVATORY_ITEMS, LAVATORY_STATS, "a lavatory", findings);
		statWords(values, findings, 2);
		optionalWorkRules(values, findings, "Without WORK no plumbers work here. The lavatory loads, but each latrine stops serving after four uses, because only a worker cleans it (lavatory/Lavatory.java:115-117,194-199).");
		accessServiceRules(values, findings);
	}

	private static void barberRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, BARBER_ITEMS, BARBER_STATS, "a barber", findings);
		statWords(values, findings, 2);
		optionalWorkRules(values, findings, "Without WORK no barbers work here. The barber loads, but serves nobody, because a booth can only be used after a barber has worked at it (barber/Tile.java:115-124,183).");
		accessServiceRules(values, findings);
	}

	private static void physicianRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, PHYSICIAN_ITEMS, PHYSICIAN_STATS, "a physician", findings);
		statWords(values, findings, 0, 2);
		optionalWorkRules(values, findings, "Without WORK no physicians work here. The physician loads, but treats nobody, because a bed can only be used after a physician has worked at it (physician/Service.java:59,155-158).");
		accessServiceRules(values, findings);
	}

	private static void pleasureRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, PLEASURE_ITEMS, PLEASURE_STATS, "a pleasure room", findings);
		optionalWorkRules(values, findings, "Without WORK no masseuses work here. The room loads, but serves nobody, because a booth starts closed and only a worker opens it (pleasure/ABed.java:19,93-101,184-189).");
		accessServiceRules(values, findings);
	}

	private static void courtRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, COURT_ITEMS, COURT_STATS, "a court", findings);
		optionalWorkRules(values, findings, "The game's court has WORK, which employs its judges. Without it the court has none, and whether it still works hasn't been checked.");
		accessServiceRules(values, findings);
	}

	private static void stocksRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, STOCKS_ITEMS, STOCKS_STATS, "the stocks", findings);
		accessServiceRules(values, findings);
	}

	private static void executionRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, EXECUTION_ITEMS, EXECUTION_STATS, "the scaffolds", findings);
	}

	private static void guardRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, GUARD_ITEMS, GUARD_STATS, "a guardpost", findings);
		boolean hasEmployment = values.containsKey("EMPLOYMENT.SHIFT_OFFSET");
		if (!hasEmployment) {
			findings.add(new AssetChecker.Finding("EMPLOYMENT", AssetChecker.Severity.WARNING, "Without EMPLOYMENT the guardpost has no guards, and it hires them through EMPLOYMENT with no check that it's there (guard/ROOM_GUARD.java:40, RoomEmploymentSimple.java:243-255), so the game is likely to fail when one is hired."));
		}
		for (String part : List.of("TITLE", "VERB")) {
			if (hasEmployment && !values.containsKey("EMPLOYMENT." + part)) {
				findings.add(new AssetChecker.Finding("EMPLOYMENT." + part, AssetChecker.Severity.MISSING,
						"With EMPLOYMENT in the data file, the game reads this from the text file, and stops loading without it (" + EMPLOYMENT + "74-75)."));
			}
		}
	}

	private static void policeRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, POLICE_ITEMS, POLICE_STATS, "the secret police", findings);
		optionalWorkRules(values, findings, "Without WORK there are no secret police, and the room's boosts count its employees with no check that WORK is there (police/ROOM_POLICE.java:188-207), so the game is likely to fail.");
		for (String key : values.keySet()) {
			if (key.startsWith("BOOST.") && !key.endsWith(">ADD") && !key.endsWith(">MUL")) {
				findings.add(new AssetChecker.Finding(key, AssetChecker.Severity.ERROR, "A boost key has to end in >ADD or >MUL, or the game stops loading (BoostSpecs.java:167-177)."));
			}
		}
	}

	private static void prisonRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, PRISON_ITEMS, PRISON_STATS, "a prison", findings);
		optionalWorkRules(values, findings, "Without WORK the prison has no jailers, and its jobs take their name and sound from WORK with no check that it's there (prison/Food.java:55, prison/Cell.java:36-41), so the game is likely to fail when one is needed.");
	}

	private static void stockadeRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, STOCKADE_ITEMS, STOCKADE_STATS, "a stockade", findings);
		optionalWorkRules(values, findings, "Without WORK the stockade has no jailers, and its jobs take their title and sound from WORK with no check that it's there (stockade/Job.java:112,171), so the game is likely to fail when one is needed.");
	}

	/** A stage, speaker, fight pit or grand arena: one item, two stats, and the service checks. WORK is required by the table. */
	private static void spectatorRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, SPECTATOR_ITEMS, SPECTATOR_STATS, "this room", findings);
		accessServiceRules(values, findings);
	}

	private static void marketRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, MARKET_ITEMS, MARKET_STATS, "a market", findings);
		optionalWorkRules(values, findings, "The vanilla market has WORK, which employs its merchants. Without it the market has none, and whether it still works hasn't been checked.");
		accessServiceRules(values, findings);
	}

	/** The SERVICE block's checks that the table can't hold: the class standings' range, and the form of a boost's key. */
	private static void accessServiceRules(Map<String, String> values, List<AssetChecker.Finding> findings) {
		for (Map.Entry<String, String> entry : values.entrySet()) {
			String key = entry.getKey();
			if (key.startsWith("SERVICE.BOOST.") && !key.endsWith(">ADD") && !key.endsWith(">MUL")) {
				findings.add(new AssetChecker.Finding(key, AssetChecker.Severity.ERROR, "A boost key has to end in >ADD or >MUL, or the game stops loading (BoostSpecs.java:167-177)."));
			}
			if (STANDING_CLASSES.stream().anyMatch(clas -> key.equals("SERVICE.STANDING." + clas))) {
				String value = entry.getValue();
				if (value.matches("-?(\\d+\\.?\\d*|\\.\\d+)") && (Double.parseDouble(value) < 0 || Double.parseDouble(value) > MAX_STANDING)) {
					findings.add(new AssetChecker.Finding(key, AssetChecker.Severity.ERROR, "This must be from 0 to " + (long) MAX_STANDING + ", or the game stops loading (StatStanding.java:337)."));
				}
			}
		}
	}

	private static void hunterRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		itemRules(values, FOOD_ROOM_ITEMS, HUNTER_STATS, "a hunter", findings);
		statWords(values, findings, 0, 2);
	}

	/** One recipe's resource names under OUT or IN. The key is INDUSTRIES.index.INDUSTRY.PART.RESOURCE, then a column. */
	private static void pastureRecipes(Map<String, String> values, List<AssetChecker.Finding> findings) {
		Map<String, Set<String>> outputs = new LinkedHashMap<>();
		Set<String> inputs = new LinkedHashSet<>();
		for (String key : values.keySet()) {
			String output = recipeResource(key, "OUT");
			if (output != null) {
				int dot = output.indexOf('.');
				outputs.computeIfAbsent(output.substring(0, dot), index -> new LinkedHashSet<>()).add(output.substring(dot + 1));
			}
			String input = recipeResource(key, "IN");
			if (input != null) {
				inputs.add(input.substring(0, input.indexOf('.')));
			}
		}
		for (Map.Entry<String, Set<String>> recipe : outputs.entrySet()) {
			if (recipe.getValue().size() > 3) {
				findings.add(new AssetChecker.Finding("INDUSTRIES." + recipe.getKey() + ".INDUSTRY.OUT", AssetChecker.Severity.ERROR,
						"A pasture recipe can have at most 3 outputs, and the game stops loading with more (ROOM_PASTURE.java:255-256)."));
			}
		}
		for (String index : inputs) {
			findings.add(new AssetChecker.Finding("INDUSTRIES." + index + ".INDUSTRY.IN", AssetChecker.Severity.ERROR,
					"A pasture recipe can't take an input, and the game stops loading if it does (ROOM_PASTURE.java:257-258)."));
		}
	}

	/** "0.MEAT" for INDUSTRIES.0.INDUSTRY.OUT.MEAT.PLAYER, or null when the key isn't that recipe part. */
	private static String recipeResource(String key, String part) {
		String head = "INDUSTRIES.";
		String marker = ".INDUSTRY." + part + ".";
		if (!key.startsWith(head)) {
			return null;
		}
		int at = key.indexOf(marker);
		if (at < 0) {
			return null;
		}
		String index = key.substring(head.length(), at);
		String rest = key.substring(at + marker.length());
		int dot = rest.indexOf('.');
		String resource = dot < 0 ? rest : rest.substring(0, dot);
		return index.isEmpty() || resource.isEmpty() ? null : index + "." + resource;
	}

	/** Absent or anything other than true is the outdoor constructor, which stops loading without the gate. */
	private static void pastureGate(Map<String, String> values, List<AssetChecker.Finding> findings) {
		boolean indoors = "true".equals(values.get("INDOORS"));
		boolean gate = values.keySet().stream().anyMatch(key -> key.equals("SPRITES.GATE_TOP_3X3") || key.startsWith("SPRITES.GATE_TOP_3X3."));
		if (!indoors && !gate) {
			findings.add(new AssetChecker.Finding("SPRITES.GATE_TOP_3X3", AssetChecker.Severity.ERROR,
					"An outdoor pasture reads the gate sprite, and the game stops loading without it (ROOM_PASTURE.java:130-132, ConstructorOutdoor.java:60)."));
		}
	}

	/**
	 * For a room whose stats named by the file aren't the first ones. These blocks call text(), which throws when
	 * the key is missing, and a blank string is returned as it is. The others are named by the game and are not
	 * checked here. This runs on a full check, which includes the text file. The vanilla data check does not,
	 * so a data file alone is not asked for these words.
	 */
	private static void statWords(Map<String, String> values, List<AssetChecker.Finding> findings, int... blocks) {
		for (int block : blocks) {
			for (String part : List.of("NAME", "DESC")) {
				String key = "STATS." + block + "." + part;
				if (!values.containsKey(key)) {
					findings.add(new AssetChecker.Finding(key, AssetChecker.Severity.ERROR,
							"The game reads this, and stops loading without it (FurnisherStat.java:74-75)."));
				}
			}
		}
	}

	private static void itemRules(Map<String, String> values, int items, int stats, String room, List<AssetChecker.Finding> findings) {
		String resources = values.getOrDefault("RESOURCES", "");
		int needed = resources.isEmpty() ? 0 : resources.split(",", -1).length;
		for (int i = 0; i < items; i++) {
			itemLength(values, "ITEMS." + i + ".COSTS", needed, "RESOURCES has " + needed + (needed == 1 ? " entry" : " entries"), findings);
			itemLength(values, "ITEMS." + i + ".STATS", stats, room + " has " + stats + " stats", findings);
		}
		upgradeMasks(values, findings);
	}

	private static void itemLength(Map<String, String> values, String key, int needed, String why, List<AssetChecker.Finding> findings) {
		String list = values.get(key);
		if (list != null) {
			int count = list.isEmpty() ? 0 : list.split(",", -1).length;
			if (count == 0 && needed > 0) {
				findings.add(new AssetChecker.Finding(key, AssetChecker.Severity.MISSING, "This needs " + needed + " numbers, because " + why + "."));
			} else if (count != needed) {
				findings.add(new AssetChecker.Finding(key, AssetChecker.Severity.ERROR, "This has " + count + (count == 1 ? " number" : " numbers") + ", and " + why + ", so it needs " + needed + ". The game stops loading otherwise."));
			}
		}
	}

	/**
	 * Warnings only. Scaling by the total divides by zero if nothing's above 0 (Minable.java:53-60), and what
	 * that does on a map hasn't been run. Another mod's _IgnoreVanilla.txt could leave room under the limit.
	 */
	private static void minableRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		boolean anyAbove = values.entrySet().stream().anyMatch(entry -> entry.getKey().startsWith("TERRAIN.") && entry.getValue().matches("\\d*\\.?\\d+") && Double.parseDouble(entry.getValue()) > 0);
		if (!anyAbove) {
			findings.add(new AssetChecker.Finding("TERRAIN", AssetChecker.Severity.WARNING, "With no value above 0, the game divides by zero when it scales these (Minable.java:53-60), so give at least one terrain a value."));
		}
		if (index.options(Reference.MINABLES).size() >= MOST_MINABLES) {
			findings.add(new AssetChecker.Finding(null, AssetChecker.Severity.WARNING, "The game stops loading with more than " + MOST_MINABLES + " deposit files (Minable.java:75), and this would go over."));
		}
	}

	/** A crop's file, from Growable.java. It has no text file. */
	private static AssetSchema crop(ClassFileScanner scanner, List<String> unresolved) throws ClassFileScanner.ScanException {
		int[] size = cropSheet(scanner, unresolved);
		TagSpec.Sprite image = size == null ? null : new TagSpec.Sprite("sprite/resource/growable/", size[0], size[1]);
		List<TagSpec> data = new ArrayList<>();
		data.add(key("RESOURCE", Reference.RESOURCES, true, null, "Growable.java:29, RMAP.java:30-36"));
		data.add(leaf("SEASONAL_OFFSET", Kind.DECIMAL, true, null, 0.0, 1.0, true, "Growable.java:30"));
		data.add(leaf("GROWTH_VALUE", Kind.DECIMAL, true, null, 0.0, 1.0, true, "Growable.java:31"));
		data.add(color("MINIMAP_COLOR", "Growable.java:32"));
		data.add(new TagSpec("CLIMATE_BONUS", Kind.DECIMAL_MAP, false, "0 for every entry", 0.0, 10000.0, true, Reference.CLIMATES, null, List.of(), "Growable.java:34, RMAP.java:200-209", Confidence.CODE_VERIFIED));
		data.add(shaped("SPRITE", Kind.BLOCK, true, null, null, "Growable.java:37", Shape.block(List.of(
				new TagSpec("SPRITE", Kind.SPRITE, true, null, null, null, true, null, null, List.of(), "Growable.java:43, GrowableSprite.java:63", Confidence.CODE_VERIFIED, image),
				leaf("POLLEN", Kind.DECIMAL, true, null, 0.0, 10.0, true, "Growable.java:40"),
				color("COLOR_POLLEN", "Growable.java:45"),
				leaf("WIND_SWAY", Kind.DECIMAL, false, "1.0", 0.0, 10.0, true, "Growable.java:41"),
				plantPart("STEM", "0.8", "Growable.java:47", "GrowableSprite.java:57"),
				plantPart("GROWTH", "1.0", "Growable.java:48", "GrowableSprite.java:58")))));
		return new AssetSchema(data, List.of(), List.of(),
				List.of("A crop has no text file. A farm names it through GROWABLE, and it's the crop's RESOURCE that's harvested (Tile.java:400)."),
				RoomSchemas::cropRules, List.of());
	}

	private static TagSpec plantPart(String key, String swayDefault, String evidence, String swayEvidence) {
		return shaped(key, Kind.BLOCK, true, null, null, evidence, Shape.block(List.of(
				color("DEAD", "Growable.java:57"),
				color("LIVE", "Growable.java:57"),
				color("RIPE", "Growable.java:57"),
				leaf("SHADOW_HEIGHT", Kind.DECIMAL, true, null, 0.0, 32.0, true, "Growable.java:55"),
				leaf("SHADOW_LENGTH", Kind.DECIMAL, true, null, 0.0, 32.0, true, "Growable.java:56"),
				leaf("WIND_SWAY", Kind.DECIMAL, false, swayDefault, 0.0, 10.0, true, "Growable.java:58-59, " + swayEvidence))));
	}

	/** The size given to the crop's anonymous tile sheet (GrowableSprite.java:63). */
	private static int[] cropSheet(ClassFileScanner scanner, List<String> unresolved) throws ClassFileScanner.ScanException {
		ClassFileScanner.ScannedClass sprite = scanner.scan(CROP_SPRITE);
		if (sprite != null) {
			for (ClassFileScanner.ScannedMethod method : sprite.methods()) {
				if (method.name().equals("<init>") && method.descriptor().startsWith("(Ljava/lang/String;")) {
					return AssetCatalog.sheetSize(method, scanner, "the crop's image", unresolved);
				}
			}
		}
		unresolved.add("the crop's image size couldn't be found.");
		return null;
	}

	/** The harvest is the crop's RESOURCE, while the numbers follow OUT (Tile.java:390-400). */
	private static void farmRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		String crop = values.get("GROWABLE");
		GameAssetIndex.Crop info = crop == null ? null : index.crop(crop);
		String prefix = "INDUSTRY.OUT.";
		String first = null;
		for (String key : values.keySet()) {
			if (first == null && key.startsWith(prefix)) {
				int dot = key.indexOf('.', prefix.length());
				first = dot < 0 ? key.substring(prefix.length()) : key.substring(prefix.length(), dot);
			}
		}
		if (info != null && first != null && !GameAssetIndex.sameKey(info.resource(), first)) {
			findings.add(new AssetChecker.Finding("INDUSTRY", AssetChecker.Severity.WARNING,
					"The crop " + crop + " yields " + info.resource() + ", but the first output is " + first + ". Farmers would harvest " + info.resource() + " (Tile.java:400)."));
		}
		upgradeMasks(values, findings);
	}

	/**
	 * Levels shorter than the longest are padded (RoomUpgrades.java:72-80), but drawing an upgrade's icon
	 * reads every resource from the mask without a limit (:127-128), so the longest must cover them. That
	 * only happens with a level to upgrade to. With one level the game clamps (:238), so there's nothing to say.
	 */
	private static void upgradeMasks(Map<String, String> values, List<AssetChecker.Finding> findings) {
		String resources = values.getOrDefault("RESOURCES", "");
		int needed = resources.isEmpty() ? 0 : resources.split(",", -1).length;
		int levels = 0;
		int longest = 0;
		for (Map.Entry<String, String> entry : values.entrySet()) {
			if (entry.getKey().matches("UPGRADES\\.\\d+\\.RESOURCE_MASK")) {
				levels++;
				longest = Math.max(longest, entry.getValue().isEmpty() ? 0 : entry.getValue().split(",", -1).length);
			}
		}
		if (levels > 1 && longest < needed) {
			findings.add(new AssetChecker.Finding("UPGRADES.0.RESOURCE_MASK", AssetChecker.Severity.ERROR,
					"With more than one level, at least one mask needs a number for each of the " + needed + " resources, or the game crashes drawing the upgrade (RoomUpgrades.java:127-128)."));
		}
	}

	/** Two crops can't yield one resource, or the game stops loading (ResGroup.java). */
	private static void cropRules(Map<String, String> values, GameAssetIndex index, List<AssetChecker.Finding> findings) {
		String resource = values.get("RESOURCE");
		if (resource != null) {
			index.crops().forEach((name, crop) -> {
				if (GameAssetIndex.sameKey(crop.resource(), resource)) {
					findings.add(new AssetChecker.Finding("RESOURCE", AssetChecker.Severity.ERROR, "The crop " + name + " already yields " + resource + ", and the game stops loading if two crops yield the same resource."));
				}
			});
		}
	}
}
