package syx.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every read of a race file, taken from the installed jar and checked against the source. */
final class RaceReads {

	private RaceReads() {
	}

	static Map<String, List<String>> all() {
		Map<String, List<String>> reads = new LinkedHashMap<>();
		reads.put("init/race/Race", List.of(
				"<init>: init/race/RaceInfo.<init>(JJ)V [Local Local]",
				"<init>: bool(S)Z [PLAYABLE]",
				"<init>: init/race/Physics.<init>(J)V [Local]",
				"<init>: game/boosting/BoostSpecs.read(JLgame/boosting/BValue;)V [Local null]",
				"expand: <init>([Ljava/nio/file/Path;)V [Returned]",
				"expand: init/race/RacePopulation.<init>(J)V [New]",
				"expand: init/race/appearence/RAppearence.<init>(Linit/race/Race;JLinit/race/ExpandInit;I)V [Local New Local Returned]",
				"expand: init/race/RacePreferrence.<init>(JLinit/race/Race;)V [New Local]",
				"expand: init/race/KingMessages.make(JLinit/race/ExpandInit;)Linit/race/KingMessages; [New Local]",
				"expand: init/race/RaceStats.<init>(Linit/race/Race;J)V [Local New]",
				"expand: util/keymap/RMAPS.readFill(JD)[D [New 100000.0]",
				"expand: has(S)Z [RESOURCE_GROOMING]",
				"expand: json(S)J [RESOURCE_GROOMING]",
				"expand: init/resources/RES_AMOUNT.make(J)Lsnake2d/util/sets/LIST; [Returned]",
				"expand: value(S)S [HOME]",
				"expand: init/race/bio/Bio.<init>(JLinit/race/Race;)V [New Local]",
				"expand: game/tourism/TourismRace.<init>(JLinit/race/Race;)V [New Local]",
				"expand: has(S)Z [EQUIPMENT_NOT_ENABLED]",
				"expand: util/keymap/RMAP.readMany(SJ)Lsnake2d/util/sets/LIST; [EQUIPMENT_NOT_ENABLED New]",
				"expand: has(S)Z [EQUIPMENT_ENABLED]",
				"expand: util/keymap/RMAP.readMany(SJ)Lsnake2d/util/sets/LIST; [EQUIPMENT_ENABLED New]"
		));
		reads.put("init/race/RaceInfo", List.of(
				"<init>: util/info/INFO.<init>(J)V [Local]",
				"<init>: text(S)S [POSSESSIVE]",
				"<init>: text(S)S [POSSESSIVES]",
				"<init>: text(S)S [DESC_LONG]",
				"<init>: text(SS)S [CHALLENGE ]",
				"<init>: textsTry(S)[S [PROS]",
				"<init>: textsTry(S)[S [CONS]",
				"<init>: init/race/RaceInfo$RPronoun.<init>(SJ)V [PRONOUN_HE Local]",
				"<init>: init/race/RaceInfo$RPronoun.<init>(SJ)V [PRONOUN_HIS Local]",
				"<init>: init/race/RaceInfo$RPronoun.<init>(SJ)V [PRONOUN_HIMSELF Local]",
				"<init>: init/race/RaceInfo$RPronoun.<init>(SJ)V [PRONOUN_HIM Local]",
				"<init>: init/race/RaceInfo$RPronoun.<init>(SJ)V [PRONOUN_CHILD Local]",
				"<init>: texts(SII)[S [ARMY_NAMES 1 255]",
				"<init>: value(S)S [WORLD_NAME_FILE]",
				"<init>: value(S)S [RAID_TEXT_FILE]",
				"<init>: <init>([Ljava/nio/file/Path;)V [Returned]",
				"<init>: game/raiding/RaiderTextsRace.<init>(J)V [New]",
				"<init>: value(S)S [RAIDER_NAME_FILE]",
				"<init>: <init>([Ljava/nio/file/Path;)V [Returned]",
				"<init>: texts(S)[S [NAMES]",
				"<init>: texts(S)[S [HELLO]",
				"<init>: texts(S)[S [GOODBYE]",
				"<init>: texts(S)[S [CURSE]",
				"<init>: texts(S)[S [INSULT]",
				"<init>: texts(S)[S [INSULTING]",
				"<init>: texts(S)[S [LORD]",
				"<init>: texts(S)[S [CITY]",
				"<init>: texts(S)[S [OTHERS]",
				"<init>: texts(S)[S [SELVES]",
				"<init>: texts(S)[S [SELF]",
				"<init>: texts(S)[S [CHILDREN]"
		));
		reads.put("init/race/RaceInfo$RPronoun", List.of(
				"<init>: texts(S)[S [Local]",
				"<init>: texts(S)[S [Returned]"
		));
		reads.put("init/race/RaceInfo$RaceWorldInfo", List.of(
				"<init>: <init>([Ljava/nio/file/Path;)V [Returned]",
				"<init>: texts(SII)[S [INTRO 1 128]",
				"<init>: texts(SII)[S [NAMES 1 512]",
				"<init>: texts(SII)[S [RULER_INTRO 1 128]",
				"<init>: texts(SII)[S [RULER 1 512]"
		));
		reads.put("init/race/Physics", List.of(
				"<init>: json(S)J [PROPERTIES]",
				"<init>: i(SII)I [HEIGHT 0 200]",
				"<init>: i(SII)I [WIDTH 5 15]",
				"<init>: i(S)I [CHILD_DAYS]",
				"<init>: i(S)I [BABY_DAYS]",
				"<init>: bool(S)Z [CORPSE_DECAY]",
				"<init>: bool(S)Z [SLEEPS]",
				"<init>: d(SDD)D [SLAVE_PRICE 0.0 2.147483647E9]",
				"<init>: d(SDD)D [SLAVE_PRICE_RECOVERY 0.0 10.0]",
				"<init>: d(SDD)D [RAID_MERCINARY 0.0 100000.0]"
		));
		reads.put("game/boosting/BoostSpecs", List.of(
				"read: game/boosting/BoostSpecs.read(SJLgame/boosting/BValue;)V [BOOST Local Local]",
				"read: game/boosting/BoostSpecs.read(SJLgame/boosting/BValue;Ljava/lang/CharSequence;)V [Local Local Local null]",
				"read: game/boosting/BoostSpecs.read(SJLgame/boosting/BValue;Ljava/lang/CharSequence;Z[S)V [Local Local Local Local 1 Unknown]",
				"read: has(S)Z [Local]",
				"read: json(S)J [Local]",
				"read: keys()Lsnake2d/util/sets/LIST; []",
				"read: d(S)D [Returned]",
				"read: path()S []",
				"read: line(S)I [Returned]",
				"read: error(SLjava/lang/CharSequence;)V [Only ADD is allowed in this context Returned]",
				"read: error(SLjava/lang/CharSequence;)V [Malformed value. Must be a string that ends with >ADD (addition), or >MUL (Multiplication) Returned]",
				"read: error(SLjava/lang/CharSequence;)V [This key is special in this context and not allowed! Unknown]"
		));
		reads.put("init/race/RacePopulation", List.of(
				"<init>: has(S)Z [POPULATION]",
				"<init>: json(S)J [POPULATION]",
				"<init>: d(SDD)D [MAX 0.0 1.0]",
				"<init>: util/keymap/RMAP.readFill(JD)[D [Returned 1.0]",
				"<init>: util/keymap/RMAP.readFill(JD)[D [Returned 100.0]",
				"<init>: d(SDD)D [GROWTH 1.0E-4 1.0]"
		));
		reads.put("init/race/RacePreferrence", List.of(
				"<init>: json(S)J [PREFERRED]",
				"<init>: util/keymap/RMAPS.readManyWarn(SJ)Lsnake2d/util/sets/LIST; [FOOD Returned]",
				"<init>: error(SLjava/lang/CharSequence;)V [Must have a favorite food! FOOD]",
				"<init>: util/keymap/RMAPS.readManyWarn(SJ)Lsnake2d/util/sets/LIST; [DRINK Returned]",
				"<init>: error(SLjava/lang/CharSequence;)V [Must have a favorite drink! DRINK]",
				"<init>: has(S)Z [CRIME]",
				"<init>: json(S)J [CRIME]",
				"<init>: has(S)Z [FieldRef]",
				"<init>: json(S)J [FieldRef]",
				"<init>: dTry(SDDD)D [FREEDOM 0.0 1.0 FieldRef]",
				"<init>: dTry(SDDD)D [LAW 0.0 1.0 FieldRef]",
				"<init>: has(S)Z [PUNISHMENT]",
				"<init>: json(S)J [PUNISHMENT]",
				"<init>: has(S)Z [FieldRef]",
				"<init>: d(SDD)D [FieldRef 0.0 1.0]",
				"<init>: util/keymap/RMAP.readFill([DJDD)V [FieldRef Returned 0.0 1.0]",
				"<init>: init/race/RacePreferrence$1.<init>(Linit/race/RacePreferrence;Lutil/keymap/RMAP;SJLinit/race/Race;)V [Local FieldRef ROAD Returned Local]",
				"<init>: init/race/RacePreferrence$2.<init>(Linit/race/RacePreferrence;Lutil/keymap/RMAP;SJ)V [Local FieldRef WORK Returned]",
				"<init>: init/race/RacePreferrence$3.<init>(Linit/race/RacePreferrence;Lutil/keymap/RMAP;SJ)V [Local FieldRef POOL Returned]",
				"<init>: init/race/RacePreferrence$5.<init>(Linit/race/RacePreferrence;Lutil/keymap/RMAP;SJ)V [Local Returned RESOURCE_RPICE_MUL Returned]",
				"<init>: init/race/RacePreferrence$6.<init>(Linit/race/RacePreferrence;Lutil/keymap/RMAP;SJ)V [Local Returned RESOURCE_RPICE_CAP Returned]",
				"init: init/race/RacePreferrence$7.<init>(Lutil/keymap/RMAP;SJ[[ZLinit/race/Race;)V [Returned OTHER_RACES_REVERSE FieldRef Unknown Returned]",
				"init: init/race/RacePreferrence$8.<init>(Lutil/keymap/RMAP;SJ[[ZLinit/race/Race;)V [Returned OTHER_RACES FieldRef Unknown Returned]"
		));
		reads.put("world/region/building/RDBuildings", List.of(
				"init: world/region/building/RDBuildings$3.<init>(Lworld/region/building/RDBuildings;Lutil/keymap/RMAP;SJLworld/region/pop/RDRace;)V [Local New WORLD_BUILDING FieldRef Returned]"
		));
		reads.put("init/race/RaceStats", List.of(
				"<init>: init/type/TRAITS.serRaceData(Linit/race/Race;J)V [Local Local]",
				"<init>: init/resources/ResSupplies.setEfficiency(Linit/race/Race;J)V [Local Local]",
				"<init>: init/race/RaceStats$1.<init>(Linit/race/RaceStats;J)V [Local Local]",
				"<init>: has(S)Z [STATS_ON_SPAWN]",
				"<init>: init/race/RaceStats$2.<init>(Linit/race/RaceStats;SJ)V [Local STATS_ON_SPAWN Local]"
		));
		reads.put("init/race/RaceResources", List.of());
		reads.put("settlement/stats/util/StatsJson", List.of(
				"<init>: settlement/stats/util/StatsJson.<init>(SJ)V [STATS Local]",
				"<init>: has(S)Z [Local]",
				"<init>: json(S)J [Local]",
				"<init>: keys()Lsnake2d/util/sets/LIST; []",
				"<init>: settlement/stats/util/StatsJson.doWithTheJson(Lsettlement/stats/stat/STAT;JS)V [Returned Unknown Returned]",
				"<init>: settlement/stats/util/StatsJson.doWithMultiplier(Lsettlement/stats/muls/StatsMultipliers$StatMultiplier;JS)V [Returned Unknown Returned]",
				"<init>: settlement/stats/util/StatsJson.doWithTheJson(Lsettlement/stats/stat/STAT;JS)V [Returned Unknown Returned]",
				"<init>: settlement/stats/util/StatsJson.doWithMultiplier(Lsettlement/stats/muls/StatsMultipliers$StatMultiplier;JS)V [Returned Unknown Returned]",
				"<init>: settlement/stats/util/StatsJson.handleFault(JS)V [Unknown Returned]",
				"handleFault: path()S []",
				"handleFault: line(S)I [Local]"
		));
		reads.put("settlement/stats/standing/StatStanding$StandingDef", List.of(
				"<clinit>: settlement/stats/standing/StatStanding$StandingDef.<init>(J)V [null]",
				"<init>: settlement/stats/standing/StatStanding$StandingDef.check(J)V [Local]",
				"<init>: bool(SZ)Z [INVERTED FieldRef]",
				"<init>: dTry(SDDD)D [MULTIPLIER 0.0 10000.0 FieldRef]",
				"<init>: dTry(SDDD)D [EXPONENT 0.01 10.0 FieldRef]",
				"<init>: dTry(SDDD)D [PRIO 0.0 100000.0 FieldRef]",
				"<init>: bool(SZ)Z [CHILD FieldRef]",
				"<init>: bool(SZ)Z [DISMISS FieldRef]",
				"<init>: has(S)Z [FieldRef]",
				"<init>: settlement/stats/standing/StatStanding$StandingDef$StandingData.<init>(Lsettlement/stats/standing/StatStanding$StandingDef;JLinit/type/HCLASS;)V [Local Local Returned]",
				"check: keys()Lsnake2d/util/sets/LIST; []",
				"check: error(SLjava/lang/CharSequence;)V [Returned Returned]",
				"<init>: has(S)Z [STANDING]",
				"<init>: json(S)J [STANDING]",
				"<init>: has(S)Z [MULTIPLIER]",
				"<init>: d(SDD)D [MULTIPLIER 0.0 100000.0]",
				"<init>: dTry(SDDD)D [EXPONENT 0.01 10.0 1.0]",
				"<init>: dTry(SDDD)D [PRIO 0.0 100000.0 1.0]",
				"<init>: has(S)Z [INVERTED]",
				"<init>: bool(S)Z [INVERTED]",
				"<init>: bool(SZ)Z [CHILD 0]",
				"<init>: has(S)Z [DISMISS]",
				"<init>: bool(S)Z [DISMISS]",
				"<init>: settlement/stats/standing/StatStanding$StandingDef$StandingData.<init>(Lsettlement/stats/standing/StatStanding$StandingDef;JLinit/type/HCLASS;)V [Local Unknown Returned]",
				"NONE: settlement/stats/standing/StatStanding$StandingDef.<init>(J)V [null]"
		));
		reads.put("init/race/appearence/RAppearence", List.of(
				"<init>: init/sprite/UI/Icons.get(JS)Linit/sprite/UI/Icon; [Local ICON_SMALL]",
				"<init>: init/sprite/UI/Icons.get(JS)Linit/sprite/UI/Icon; [Local ICON_BIG]",
				"<init>: init/race/appearence/RFloors.<init>(J)V [Local]",
				"<init>: value(S)S [SPRITE_FILE]",
				"<init>: <init>([Ljava/nio/file/Path;)V [Returned]",
				"<init>: init/race/appearence/RColors.<init>(J)V [New]",
				"<init>: init/race/appearence/RNames.names(SJLsnake2d/util/sets/KeyMap;)Lsnake2d/util/sets/ArrayList; [NAMESET_FILE_NOBLE New FieldRef]",
				"<init>: json(S)J [WORLD]",
				"<init>: world/map/regions/centre/WorldRaceSheet.<init>(J)V [Returned]",
				"<init>: value(S)S [SPRITE_EXTRA_FILE]",
				"<init>: value(S)S [SLEEP_FILE]",
				"<init>: init/race/appearence/RCrown.<init>(Linit/race/ExpandInit;J)V [Local New]",
				"<init>: json(S)J [CHILD]",
				"<init>: init/race/appearence/RType.<init>(Linit/race/appearence/RColors;JLinit/race/appearence/RExtras;Linit/race/ExpandInit;)V [FieldRef Returned FieldRef Local]",
				"<init>: has(S)Z [INFANT_FILE]",
				"<init>: value(S)S [INFANT_FILE]",
				"<init>: jsons(SII)[J [TYPES 1 4]",
				"<init>: init/race/appearence/RType.<init>(Linit/race/appearence/RColors;JLinit/race/appearence/RExtras;Linit/race/ExpandInit;)V [FieldRef Unknown FieldRef Local]"
		));
		reads.put("init/race/appearence/RColors", List.of(
				"<init>: init/race/appearence/RColors.clothes(SJII)[[Lsnake2d/util/color/COLOR; [COLOR_CLOTHES Local Unknown 16]",
				"<init>: snake2d/util/color/ColorImp.<init>(JS)V [Local COLOR_BLOOD]",
				"<init>: json(S)J [COLORS]",
				"<init>: keys()Lsnake2d/util/sets/LIST; []",
				"<init>: keys()Lsnake2d/util/sets/LIST; []",
				"<init>: init/race/appearence/RColors$ColorCollection.<init>(Lsnake2d/util/sets/ArrayList;JS)V [FieldRef Returned Returned]",
				"clothes: jsons(SII)[J [Local 1 Local]",
				"clothes: snake2d/util/color/ColorImp.<init>(J)V [Unknown]",
				"clothes: snake2d/util/color/ColorImp.<init>(J)V [Unknown]"
		));
		reads.put("init/race/appearence/RType", List.of(
				"<init>: value(S)S [SPRITE_FILE]",
				"<init>: value(S)S [SPRITE_SKELLETON_FILE]",
				"<init>: init/race/appearence/RType$RTypeSpec.<init>(Linit/race/appearence/RColors;J)V [Local Local]",
				"<init>: init/race/appearence/RPortrait.<init>(Linit/race/ExpandInit;Linit/race/appearence/RColors;J)V [Local Local Local]",
				"<init>: init/race/appearence/RNames.<init>(JLsnake2d/util/sets/KeyMap;)V [Local FieldRef]",
				"<init>: has(S)Z [ADDONS]",
				"<init>: jsons(S)[J [ADDONS]",
				"<init>: bool(S)Z [BELOW_HEAD]",
				"<init>: init/race/appearence/RAddon.<init>(JLinit/race/appearence/RColors;[Linit/race/appearence/RAddon;)V [Unknown Local Unknown]",
				"<init>: init/race/appearence/RAddon.<init>(JLinit/race/appearence/RColors;[Linit/race/appearence/RAddon;)V [Unknown Local Unknown]"
		));
		reads.put("init/race/appearence/RAddon", List.of(
				"<init>: init/value/Lockable.push(SJ)V [CONDITIONS Local]",
				"<init>: has(S)Z [COLOR]",
				"<init>: util/keymap/RMAP.read(SJ)Lutil/keymap/MAPPED; [COLOR Local]",
				"<init>: i(SII)I [ADDON_INDEX 0 8]"
		));
		reads.put("init/race/appearence/RPortraitFrame", List.of(
				"<init>: keys()Lsnake2d/util/sets/LIST; []",
				"<init>: errorGet(SLjava/lang/CharSequence;)S [Returned Returned]",
				"<init>: init/race/appearence/RaceFrameMaker.read(J)Lsnake2d/util/sets/LIST; [Local]",
				"<init>: has(S)Z [FRAME_RANDOM]",
				"<init>: i(SII)I [FRAME_RANDOM 0 16]",
				"<init>: has(S)Z [FRAME_OCCURRENCE]",
				"<init>: d(SDD)D [FRAME_OCCURRENCE 0.0 1.0]",
				"<init>: has(S)Z [OFF_X]",
				"<init>: i(SII)I [OFF_X -40 40]",
				"<init>: has(S)Z [OFF_Y]",
				"<init>: i(SII)I [OFF_Y -48 48]",
				"<init>: has(S)Z [OFF_X_RANDOM]",
				"<init>: i(SII)I [OFF_X_RANDOM 0 40]",
				"<init>: has(S)Z [OFF_Y_RANDOM]",
				"<init>: i(SII)I [OFF_Y_RANDOM 0 48]",
				"<init>: init/value/Lockable.push(SJ)V [CONDITIONS Local]",
				"<init>: has(S)Z [COLOR]",
				"<init>: value(S)S [COLOR]",
				"<init>: util/keymap/RMAP.get(SJ)Lutil/keymap/MAPPED; [Returned Local]",
				"<init>: has(S)Z [OPACITY]",
				"<init>: i(SII)I [OPACITY 0 256]",
				"<init>: has(S)Z [STAINS]",
				"<init>: bool(S)Z [STAINS]"
		));
		reads.put("init/race/appearence/RCrown", List.of(
				"<init>: init/race/appearence/RCrown.make(Linit/race/ExpandInit;JSLsnake2d/util/sets/ArrayListGrower;)V [Local Local CROWN FieldRef]",
				"<init>: init/race/appearence/RCrown.make(Linit/race/ExpandInit;JSLsnake2d/util/sets/ArrayListGrower;)V [Local Local RAIDER FieldRef]",
				"<init>: init/race/appearence/RCrown.make(Linit/race/ExpandInit;JSLsnake2d/util/sets/ArrayListGrower;)V [Local Local MERC FieldRef]",
				"make: has(S)Z [Local]",
				"make: error(SLjava/lang/CharSequence;)V [Not declared Local]",
				"make: has(S)Z [Local]",
				"make: arrayIs(S)Z [Local]",
				"make: jsons(S)[J [Local]",
				"make: init/race/appearence/RCrown.make(JLinit/race/ExpandInit;Lsnake2d/util/sets/ArrayListGrower;)V [Unknown Local Local]",
				"make: json(S)J [Local]",
				"make: init/race/appearence/RCrown.make(JLinit/race/ExpandInit;Lsnake2d/util/sets/ArrayListGrower;)V [Returned Local Local]",
				"make: i(SII)I [OFFX -48 48]",
				"make: i(SII)I [OFFY -48 48]",
				"make: value(S)S [FILE]",
				"make: errorGet(SLjava/lang/CharSequence;)S [Returned Returned]"
		));
		reads.put("init/race/appearence/RFloors", List.of(
				"<init>: init/race/appearence/RFloors$1.<init>(Linit/race/appearence/RFloors;Lutil/keymap/RMAP;SJ)V [Local FieldRef ROOM_FLOOR_OVERRIDE Local]"
		));
		reads.put("init/race/appearence/RNames", List.of(
				"<init>: init/race/appearence/RNames.names(SJLsnake2d/util/sets/KeyMap;)Lsnake2d/util/sets/ArrayList; [NAMESET_FILE_FIRST Local Local]",
				"<init>: init/race/appearence/RNames.names(SJLsnake2d/util/sets/KeyMap;)Lsnake2d/util/sets/ArrayList; [NAMESET_FILE_SURNAME Local Local]",
				"names: value(S)S [Local]",
				"names: <init>([Ljava/nio/file/Path;)V [Returned]",
				"names: texts(SII)[S [NAMES 1 4095]"
		));
		reads.put("init/race/appearence/RaceSheet", List.of());
		reads.put("init/race/appearence/RExtras", List.of());
		reads.put("init/race/appearence/RPortrait", List.of(
				"<init>: has(S)Z [FACE]",
				"<init>: jsons(S)[J [FACE]",
				"<init>: init/race/appearence/RPortraitFrame.<init>(Linit/race/appearence/RaceFrameMaker;Linit/race/appearence/RColors;JI)V [FieldRef Local Unknown Unknown]"
		));
		reads.put("world/map/regions/centre/WorldRaceSheet", List.of(
				"<init>: world/map/regions/centre/WorldRaceSheet$Town.<init>(Linit/paths/PATH;J)V [Returned Local]",
				"<init>: world/map/regions/centre/WorldRaceSheet$Village.<init>(Linit/paths/PATH;J)V [Returned Local]",
				"<init>: world/map/regions/centre/WorldRaceSheet$WallSteriods.<init>(ISLinit/paths/PATH;J)V [6 WALL Returned Local]",
				"<init>: world/map/regions/centre/WorldRaceSheet$WallSteriods.<init>(ISLinit/paths/PATH;J)V [4 WALL_VILLAGE Returned Local]",
				"<init>: world/map/regions/centre/WorldRaceSheet$Overlay.<init>(Linit/paths/PATH;J)V [Returned Local]",
				"<init>: world/map/regions/centre/WorldRaceSheet$Terrain.<init>(Linit/paths/PATH;J)V [Returned Local]",
				"<init>: world/map/regions/centre/WorldRaceSheet$Farm.<init>(Linit/paths/PATH;J)V [Returned Local]"
		));
		reads.put("init/race/home/RaceHome", List.of(
				"<init>: <init>([Ljava/nio/file/Path;)V [Returned]",
				"<init>: json(S)J [FieldRef]",
				"<init>: init/race/home/RaceHomeClass.<init>(J)V [Returned]",
				"<init>: json(S)J [FieldRef]",
				"<init>: init/race/home/RaceHomeClass.<init>(J)V [Returned]",
				"<init>: json(S)J [FieldRef]",
				"<init>: init/race/home/RaceHomeClass.<init>(J)V [Returned]"
		));
		reads.put("init/race/home/RaceHomeClass", List.of(
				"<init>: keys()Lsnake2d/util/sets/LIST; []",
				"<init>: jsons(S)[J [Returned]",
				"<init>: json(S)J [RESOURCES]",
				"<init>: keys()Lsnake2d/util/sets/LIST; []",
				"<init>: errorGet(SLjava/lang/CharSequence;)S [No resource with this key!  Returned]",
				"<init>: i(SII)I [Returned 1 15]",
				"<init>: error(SLjava/lang/CharSequence;)V [Only 8 distinct resources are allowed ]",
				"<init>: init/race/home/RaceHomeSheet.<init>(Lsnake2d/util/sets/LIST;JSLinit/sprite/game/SheetType;)V [FieldRef Local BED_1x1_TOP FieldRef]",
				"<init>: init/race/home/RaceHomeSheet.<init>(Lsnake2d/util/sets/LIST;JSLinit/sprite/game/SheetType;)V [FieldRef Local BED_1x1_BOTTOM FieldRef]",
				"<init>: init/race/home/RaceHomeSheet.<init>(Lsnake2d/util/sets/LIST;JSLinit/sprite/game/SheetType;)V [FieldRef Local CARPET_COMBO FieldRef]",
				"<init>: init/race/home/RaceHomeSheet.<init>(Lsnake2d/util/sets/LIST;JSLinit/sprite/game/SheetType;)V [FieldRef Local TABLE_COMBO FieldRef]",
				"<init>: init/race/home/RaceHomeSheet.<init>(Lsnake2d/util/sets/LIST;JSLinit/sprite/game/SheetType;)V [FieldRef Local NIGHTSTAND_1x1 FieldRef]",
				"<init>: init/race/home/RaceHomeSheet.<init>(Lsnake2d/util/sets/LIST;JSLinit/sprite/game/SheetType;)V [FieldRef Local STORAGE_1x1 FieldRef]",
				"<init>: init/race/home/RaceHomeSheet.<init>(Lsnake2d/util/sets/LIST;JSLinit/sprite/game/SheetType;)V [FieldRef Local CHAIR_1x1 FieldRef]",
				"<init>: init/race/home/RaceHomeSheet.<init>(Lsnake2d/util/sets/LIST;JSLinit/sprite/game/SheetType;)V [FieldRef Local NICKNACK_A_1x1 FieldRef]",
				"<init>: init/race/home/RaceHomeSheet.<init>(Lsnake2d/util/sets/LIST;JSLinit/sprite/game/SheetType;)V [FieldRef Local NICKNACK_A_ONTOP_1x1 FieldRef]",
				"<init>: init/race/home/RaceHomeSheet.<init>(Lsnake2d/util/sets/LIST;JSLinit/sprite/game/SheetType;)V [FieldRef Local NICKNACK_B_1x1 FieldRef]",
				"<init>: init/race/home/RaceHomeSheet.<init>(Lsnake2d/util/sets/LIST;JSLinit/sprite/game/SheetType;)V [FieldRef Local MAT_1x1 FieldRef]",
				"<init>: init/race/home/RaceHomeSheet.<init>(Lsnake2d/util/sets/LIST;JSLinit/sprite/game/SheetType;)V [FieldRef Local BED_MASTER_2x2 FieldRef]",
				"<init>: init/race/home/RaceHomeSheet.<init>(Lsnake2d/util/sets/LIST;JSLinit/sprite/game/SheetType;)V [FieldRef Local STATUE_2x2 FieldRef]",
				"<init>: has(S)Z [FLOORS]",
				"<init>: jsons(S)[J [FLOORS]",
				"<init>: init/race/home/RaceHomeSheet.addResource(Lsnake2d/util/sets/LIST;JI[[I)V [New Unknown Unknown FieldRef]",
				"<init>: util/keymap/RMAP.read(J)Lutil/keymap/MAPPED; [Unknown]"
		));
		reads.put("init/race/home/RaceHomeSheet", List.of(
				"<init>: has(S)Z [Local]",
				"<init>: jsons(S)[J [Local]",
				"<init>: init/race/home/RaceHomeSheet.addResource(Lsnake2d/util/sets/LIST;JI[[I)V [Local Unknown Unknown FieldRef]",
				"<init>: init/sprite/game/Sheets.<init>(Linit/sprite/game/SheetType;J)V [Local Unknown]",
				"addResource: json(S)J [RESOURCES]",
				"addResource: keys()Lsnake2d/util/sets/LIST; []",
				"addResource: i(SII)I [Returned 0 15]"
		));
		reads.put("init/sprite/game/SheetData", List.of(
				"<init>: init/sprite/game/SheetData.test(J)V [Local]",
				"<init>: dTry(SDDD)D [FPS 0.0 100000.0 0.0]",
				"<init>: dTry(SDDD)D [FPS_INTERVAL 0.0 1.0 1.0]",
				"<init>: dTry(SDDD)D [SHADOW_LENGTH 0.0 100.0 Unknown]",
				"<init>: dTry(SDDD)D [SHADOW_HEIGHT 0.0 100.0 Unknown]",
				"<init>: bool(SZ)Z [CIRCULAR 0]",
				"<init>: bool(SZ)Z [ROTATES 1]",
				"<init>: has(S)Z [COLOR]",
				"<init>: snake2d/util/color/ColorImp.cols(JS)Lsnake2d/util/sets/LIST; [Local COLOR]",
				"<init>: bool(SZ)Z [TINT 1]",
				"<init>: init/sprite/game/SheetData.test(J)V [Local]",
				"<init>: dTry(SDDD)D [FPS 0.0 100000.0 FieldRef]",
				"<init>: dTry(SDDD)D [FPS_INTERVAL 0.0 1.0 Unknown]",
				"<init>: dTry(SDDD)D [SHADOW_LENGTH 0.0 100.0 Unknown]",
				"<init>: dTry(SDDD)D [SHADOW_HEIGHT 0.0 100.0 Unknown]",
				"<init>: bool(SZ)Z [CIRCULAR FieldRef]",
				"<init>: bool(SZ)Z [ROTATES FieldRef]",
				"<init>: has(S)Z [COLOR]",
				"<init>: snake2d/util/color/ColorImp.cols(JS)Lsnake2d/util/sets/LIST; [Local COLOR]",
				"<init>: bool(SZ)Z [TINT 1]",
				"test: keys()Lsnake2d/util/sets/LIST; []",
				"test: error(SLjava/lang/CharSequence;)V [Returned Returned]"
		));
		reads.put("init/race/bio/Bio", List.of(
				"<init>: value(S)S [BIO_FILE]",
				"<init>: <init>(Ljava/nio/file/Path;)V [Returned]",
				"<init>: init/race/bio/BioLines.<init>(J)V [New]",
				"<init>: has(S)Z [BIO_FILE_SPECIFIC]",
				"<init>: value(S)S [BIO_FILE_SPECIFIC]",
				"<init>: <init>(Ljava/nio/file/Path;)V [Returned]",
				"<init>: init/race/bio/BioLines.<init>(Linit/race/bio/BioLines;J)V [Returned New]",
				"<init>: init/race/bio/BioOpinionData.<init>(JJ)V [New Unknown]"
		));
		reads.put("init/race/bio/BioLine", List.of(
				"<init>: has(S)Z [Local]",
				"<init>: init/race/bio/BioLine.strings(JS)[Ljava/lang/CharSequence; [Local Local]",
				"<init>: has(S)Z [Local]",
				"<init>: init/race/bio/BioLine.strings(JS)[Ljava/lang/CharSequence; [Local Local]",
				"strings: texts(S)[S [Local]"
		));
		reads.put("init/race/bio/BioLines", List.of(
				"<init>: init/race/bio/BioLines.<init>(J)V [Local]",
				"<init>: has(S)Z [FieldRef]",
				"<init>: has(S)Z [TRAIT]",
				"<init>: json(S)J [TRAIT]",
				"<init>: has(S)Z [Returned]",
				"<init>: init/race/bio/BioLines$1.<init>(Linit/race/bio/BioLines;Lsnake2d/util/sets/LISTE;JS)V [Local FieldRef Local INFO_OTHER]",
				"<init>: init/race/bio/BioLine.<init>(Lsnake2d/util/sets/LISTE;JS)V [FieldRef Local INFO_GENERAL]",
				"<init>: init/race/bio/BioLine.<init>(Lsnake2d/util/sets/LISTE;JS)V [FieldRef Local INFO_TITLE]",
				"<init>: init/race/bio/BioLine.<init>(Lsnake2d/util/sets/LISTE;JS)V [FieldRef Local INFO_GENERAL2]",
				"<init>: init/race/bio/BioLine.<init>(Lsnake2d/util/sets/LISTE;JS)V [FieldRef Local INFO_GENERAL3]",
				"<init>: init/race/bio/BioLines$2.<init>(Linit/race/bio/BioLines;Lsnake2d/util/sets/LISTE;JS)V [Local FieldRef Local HOME_NONE_WORK]",
				"<init>: init/race/bio/BioLines$3.<init>(Linit/race/bio/BioLines;Lsnake2d/util/sets/LISTE;JS)V [Local FieldRef Local HOME_NONE]",
				"<init>: init/race/bio/BioLines$4.<init>(Linit/race/bio/BioLines;Lsnake2d/util/sets/LISTE;JS)V [Local FieldRef Local HOME_NONE_SEARCH]",
				"<init>: json(S)J [TRAIT]",
				"<init>: init/race/bio/BioLines$5.<init>(Linit/race/bio/BioLines;Lsnake2d/util/sets/LISTE;JS[Ljava/lang/CharSequence;Linit/type/TRAIT;)V [Local FieldRef Returned Returned FieldRef Returned]",
				"<init>: init/race/bio/BioLines$6.<init>(Linit/race/bio/BioLines;Lsnake2d/util/sets/LISTE;JS)V [Local FieldRef Local FRIEND]",
				"<init>: init/race/bio/BioLines$7.<init>(Linit/race/bio/BioLines;Lsnake2d/util/sets/LISTE;JS)V [Local FieldRef Local FRIEND_ENEMY]",
				"<init>: init/race/bio/BioLines$8.<init>(Linit/race/bio/BioLines;Lsnake2d/util/sets/LISTE;JS)V [Local FieldRef Local FRIEND_OTHER]",
				"<init>: init/race/bio/BioLines$Origin.<init>(Lsnake2d/util/sets/LISTE;JSLinit/type/CAUSE_ARRIVE;)V [FieldRef Local ORIGIN_NATIVE Returned]",
				"<init>: init/race/bio/BioLines$Origin.<init>(Lsnake2d/util/sets/LISTE;JSLinit/type/CAUSE_ARRIVE;)V [FieldRef Local ORIGIN_IMMI Returned]",
				"<init>: init/race/bio/BioLines$Origin.<init>(Lsnake2d/util/sets/LISTE;JSLinit/type/CAUSE_ARRIVE;)V [FieldRef Local ORIGIN_FREED Returned]",
				"<init>: init/race/bio/BioLines$Origin.<init>(Lsnake2d/util/sets/LISTE;JSLinit/type/CAUSE_ARRIVE;)V [FieldRef Local ORIGIN_PAROLE Returned]",
				"<init>: init/race/bio/BioLines$Origin.<init>(Lsnake2d/util/sets/LISTE;JSLinit/type/CAUSE_ARRIVE;)V [FieldRef Local ORIGIN_SOLDIER Returned]",
				"<init>: init/race/bio/BioLines$Origin.<init>(Lsnake2d/util/sets/LISTE;JSLinit/type/CAUSE_ARRIVE;)V [FieldRef Local ORIGIN_INSANE Returned]",
				"<init>: init/race/bio/BioLines$9.<init>(Linit/race/bio/BioLines;Lsnake2d/util/sets/LISTE;JS)V [Local FieldRef Local HOME]",
				"<init>: init/race/bio/BioLines$10.<init>(Linit/race/bio/BioLines;Lsnake2d/util/sets/LISTE;JS)V [Local FieldRef Local HOME_ALONE]",
				"<init>: init/race/bio/BioLines$11.<init>(Linit/race/bio/BioLines;Lsnake2d/util/sets/LISTE;JS)V [Local FieldRef Local DIVISION]",
				"<init>: init/race/bio/BioLines$12.<init>(Linit/race/bio/BioLines;Lsnake2d/util/sets/LISTE;JS)V [Local FieldRef Local DIVISION_RECRUIT]",
				"<init>: init/race/bio/BioLines$13.<init>(Linit/race/bio/BioLines;Lsnake2d/util/sets/LISTE;JS)V [Local FieldRef Local DIVISION_NONE]",
				"<init>: init/race/bio/BioLines$14.<init>(Linit/race/bio/BioLines;Lsnake2d/util/sets/LISTE;JS)V [Local FieldRef Local WORK_NOBLE]",
				"<init>: init/race/bio/BioLines$15.<init>(Linit/race/bio/BioLines;Lsnake2d/util/sets/LISTE;JS)V [Local FieldRef Local WORK_EMPLOYED]",
				"<init>: init/race/bio/BioLines$16.<init>(Linit/race/bio/BioLines;Lsnake2d/util/sets/LISTE;JS)V [Local FieldRef Local WORK_UNEMPLOYED]",
				"<init>: init/race/bio/BioLines$17.<init>(Linit/race/bio/BioLines;Lsnake2d/util/sets/LISTE;JS)V [Local FieldRef Local WORK_LEISURE]",
				"<init>: init/race/bio/BioLine.<init>(Lsnake2d/util/sets/LISTE;JS)V [FieldRef Local DREAMS]",
				"<init>: init/race/bio/BioLines$18.<init>(Linit/race/bio/BioLines;Lsnake2d/util/sets/LISTE;JS)V [Local FieldRef Local DREAMS_CHILD]",
				"<init>: init/race/bio/BioLines$19.<init>(Linit/race/bio/BioLines;Lsnake2d/util/sets/LISTE;JS)V [Local FieldRef Local DREAMS_CRIMINAL]",
				"<init>: init/race/bio/BioLines$20.<init>(Linit/race/bio/BioLines;Lsnake2d/util/sets/LISTE;JS)V [Local FieldRef Local DREAMS_ENEMY]",
				"<init>: init/race/bio/BioLines$21.<init>(Linit/race/bio/BioLines;Lsnake2d/util/sets/LISTE;JS)V [Local FieldRef Local DREAMS_RIOTEER]",
				"<init>: init/race/bio/BioLines$22.<init>(Linit/race/bio/BioLines;Lsnake2d/util/sets/LISTE;JS)V [Local FieldRef Local DREAMS_INSANE]",
				"<init>: init/race/bio/BioLines$23.<init>(Linit/race/bio/BioLines;Lsnake2d/util/sets/LISTE;JS)V [Local FieldRef Local DREAMS_SLAVE]"
		));
		// Opinion.java reads MORE and LESS for a stat or a monument, not for a race file. A race only constructs an empty one.
		reads.put("init/race/bio/BioOpinion", List.of());
		reads.put("init/race/KingMessages", List.of(
				"make: value(S)S [KING_FILE]",
				"make: <init>([Ljava/nio/file/Path;)V [Returned]",
				"make: init/race/KingMessages.<init>(J)V [New]",
				"<init>: init/race/KingMessages$Message.<init>(Linit/race/KingMessages;JS)V [Local Local GREETING_GOOD]",
				"<init>: init/race/KingMessages$Message.<init>(Linit/race/KingMessages;JS)V [Local Local GREETING_BAD]",
				"<init>: init/race/KingMessages$Message.<init>(Linit/race/KingMessages;JS)V [Local Local STANCE_UP]",
				"<init>: init/race/KingMessages$Message.<init>(Linit/race/KingMessages;JS)V [Local Local STANCE_DOWN]",
				"<init>: init/race/KingMessages$Message.<init>(Linit/race/KingMessages;JS)V [Local Local STANCE_WARNING]",
				"<init>: init/race/KingMessages$Message.<init>(Linit/race/KingMessages;JS)V [Local Local PEACE_GOOD]",
				"<init>: init/race/KingMessages$Message.<init>(Linit/race/KingMessages;JS)V [Local Local PEACE_BAD]",
				"<init>: init/race/KingMessages$Message.<init>(Linit/race/KingMessages;JS)V [Local Local THREAT_NORMAL]",
				"<init>: init/race/KingMessages$Message.<init>(Linit/race/KingMessages;JS)V [Local Local THREAT_VASSAL]",
				"<init>: init/race/KingMessages$Message.<init>(Linit/race/KingMessages;JS)V [Local Local THREAT_ALLY]",
				"<init>: init/race/KingMessages$Message.<init>(Linit/race/KingMessages;JS)V [Local Local WAR_NORMAL]",
				"<init>: init/race/KingMessages$Message.<init>(Linit/race/KingMessages;JS)V [Local Local WAR_VASSAL]",
				"<init>: init/race/KingMessages$Message.<init>(Linit/race/KingMessages;JS)V [Local Local WAR_ALLY]",
				"<init>: init/race/KingMessages$Message.<init>(Linit/race/KingMessages;JS)V [Local Local WAR_JOIN_NORMAL]",
				"<init>: init/race/KingMessages$Message.<init>(Linit/race/KingMessages;JS)V [Local Local WAR_JOIN_VASSAL]",
				"<init>: init/race/KingMessages$Message.<init>(Linit/race/KingMessages;JS)V [Local Local WAR_JOIN_ALLY]",
				"<init>: texts(S)[S [COALITION_NAME]",
				"<init>: texts(S)[S [WAR_CAUSE_AGRESSION]",
				"<init>: texts(S)[S [WAR_CAUSE_DEFEND]",
				"<init>: texts(S)[S [RUMOUR]",
				"<init>: texts(S)[S [NGO]"
		));
		reads.put("game/raiding/RaiderTextsRace", List.of(
				"<init>: json(S)J [FIRST]",
				"<init>: texts(S)[S [GREETING]",
				"<init>: texts(S)[S [INTROS]",
				"<init>: texts(S)[S [BODIES]",
				"<init>: texts(S)[S [ENDS]",
				"<init>: json(S)J [REPEAT]",
				"<init>: texts(S)[S [GREETING]",
				"<init>: texts(S)[S [INTROS]",
				"<init>: texts(S)[S [BODIES]",
				"<init>: texts(S)[S [ENDS]",
				"<init>: texts(S)[S [REJECTED]",
				"<init>: texts(S)[S [PAYED]",
				"<init>: texts(S)[S [AFTER_RAID]",
				"<init>: texts(S)[S [HELP]",
				"<init>: texts(S)[S [HELP_DESTROYED]",
				"<init>: texts(S)[S [HELP_FIGHT]"
		));
		reads.put("game/tourism/TourismRace", List.of(
				"<clinit>: game/tourism/Text.<init>(J)V [null]",
				"<init>: has(S)Z [TOURIST]",
				"<init>: json(S)J [TOURIST]",
				"<init>: d(SDD)D [OCCURENCE 0.0 100000.0]",
				"<init>: d(SDD)D [CREDITS 0.0 100000.0]",
				"<init>: value(S)S [TOURIST_TEXT_FILE]",
				"<init>: <init>([Ljava/nio/file/Path;)V [Returned]",
				"<init>: game/tourism/Text.<init>(J)V [New]"
		));
		reads.put("settlement/stats/standing/StatStanding$StandingDef$StandingData", List.of(
				"<init>: has(S)Z [FieldRef]",
				"<init>: d(SDD)D [FieldRef 0.0 1000000.0]"
		));
		reads.put("init/race/appearence/RColors$ColorCollection", List.of(
				"<init>: json(S)J [Local]",
				"<init>: bool(SZ)Z [TURNS_GRAY_WHEN_OLD 0]",
				"<init>: bool(SZ)Z [TURNS_WHITE_WHEN_DEAD 0]",
				"<init>: bool(SZ)Z [TURNS_SICKLY 0]",
				"<init>: has(S)Z [PICK_BY_STAT]",
				"<init>: value(S)S [PICK_BY_STAT]",
				"<init>: snake2d/util/color/ColorImp.cols(JS)Lsnake2d/util/sets/LIST; [Returned VALUES]",
				"<init>: has(S)Z [GENERATE_RANDOMIZE]",
				"<init>: d(S)D [GENERATE_RANDOMIZE]"
		));
		reads.put("init/race/appearence/RType$RTypeSpec", List.of(
				"<init>: has(S)Z [OCCURRENCE]",
				"<init>: d(S)D [OCCURRENCE]",
				"<init>: util/keymap/RMAP.read(SJ)Lutil/keymap/MAPPED; [COLOR_SKIN Local]",
				"<init>: util/keymap/RMAP.read(SJ)Lutil/keymap/MAPPED; [COLOR_LEG Local]"
		));
		reads.put("init/race/KingMessages$Message", List.of(
				"<init>: texts(S)[S [Local]"
		));
		reads.put("game/tourism/Text", List.of(
				"<init>: game/tourism/Text$Entry.<init>(IJS)V [0 Local RATING]",
				"<init>: game/tourism/Text$Entry.<init>(IJS)V [1 Local ATTRACTION]",
				"<init>: game/tourism/Text$Entry.<init>(IJS)V [2 Local SERVICE]",
				"<init>: game/tourism/Text$Entry.<init>(IJS)V [3 Local INN]"
		));
		reads.put("init/race/RacePreferrence$1", List.of(
				"<init>: util/keymap/RMAP$KJson.<init>(Lutil/keymap/RMAP;SJ)V [Local Local Local]",
				"process: d(SDD)D [Local 0.0 1.0]",
				"process: init/race/RacePreferrence$1.process(Lsettlement/tilemap/floor/Floors$Floor;JSZ)V [Local Local Local Local]"
		));
		reads.put("init/race/RacePreferrence$2", List.of(
				"<init>: util/keymap/RMAP$KJson.<init>(Lutil/keymap/RMAP;SJ)V [Local Local Local]",
				"process: d(SDD)D [Local -10000.0 10000.0]",
				"process: errorGet(SLjava/lang/CharSequence;)S [Not a workable room Local]",
				"process: init/race/RacePreferrence$2.process(Lsettlement/room/main/RoomBlueprint;JSZ)V [Local Local Local Local]"
		));
		reads.put("init/race/RacePreferrence$3", List.of(
				"<init>: util/keymap/RMAP$KJson.<init>(Lutil/keymap/RMAP;SJ)V [Local Local Local]",
				"process: d(SDD)D [Local 0.0 1.0]",
				"process: errorGet(SLjava/lang/CharSequence;)S [Not a pool! Local]",
				"process: init/race/RacePreferrence$3.process(Lsettlement/room/main/RoomBlueprint;JSZ)V [Local Local Local Local]"
		));
		reads.put("init/race/RacePreferrence$5", List.of(
				"<init>: util/keymap/RMAP$KJson.<init>(Lutil/keymap/RMAP;SJ)V [Local Local Local]",
				"process: d(SDD)D [Local 0.0 100.0]",
				"process: init/race/RacePreferrence$5.process(Linit/resources/RESOURCE;JSZ)V [Local Local Local Local]"
		));
		reads.put("init/race/RacePreferrence$6", List.of(
				"<init>: util/keymap/RMAP$KJson.<init>(Lutil/keymap/RMAP;SJ)V [Local Local Local]",
				"process: d(SDD)D [Local 0.0 1.0]",
				"process: init/race/RacePreferrence$6.process(Linit/resources/RESOURCE;JSZ)V [Local Local Local Local]"
		));
		reads.put("init/race/RacePreferrence$7", List.of(
				"<init>: util/keymap/RMAP$KJson.<init>(Lutil/keymap/RMAP;SJ)V [Local Local Local]",
				"process: d(SDD)D [Local 0.0 1.0]",
				"process: init/race/RacePreferrence$7.process(Linit/race/Race;JSZ)V [Local Local Local Local]"
		));
		reads.put("init/race/RacePreferrence$8", List.of(
				"<init>: util/keymap/RMAP$KJson.<init>(Lutil/keymap/RMAP;SJ)V [Local Local Local]",
				"process: d(SDD)D [Local 0.0 1.0]",
				"process: init/race/RacePreferrence$8.process(Linit/race/Race;JSZ)V [Local Local Local Local]"
		));
		reads.put("init/race/appearence/RFloors$1", List.of(
				"<init>: util/keymap/RMAP$KJson.<init>(Lutil/keymap/RMAP;SJ)V [Local Local Local]",
				"process: util/keymap/RMAP.readMany(SJ)Lsnake2d/util/sets/LIST; [Local Local]",
				"process: init/race/appearence/RFloors$1.process(Lsettlement/room/main/RoomBlueprint;JSZ)V [Local Local Local Local]"
		));
		reads.put("init/race/RaceStats$1", List.of(
				"<init>: settlement/stats/util/StatsJson.<init>(J)V [Local]",
				"doWithTheJson: json(S)J [Local]",
				"doWithTheJson: has(S)Z [PRIO]",
				"doWithTheJson: settlement/stats/standing/StatStanding$StandingDef.<init>(J)V [Returned]"
		));
		reads.put("init/race/RaceStats$2", List.of(
				"<init>: settlement/stats/util/StatsJson.<init>(SJ)V [Local Local]",
				"doWithTheJson: d(S)D [Local]"
		));
		reads.put("world/map/regions/centre/WorldRaceSheet$Town", List.of(
				"<init>: value(S)S [TOWN]",
				"<init>: snake2d/util/color/ColorImp.<init>(JS)V [Local Returned]"
		));
		reads.put("world/map/regions/centre/WorldRaceSheet$Village", List.of(
				"<init>: value(S)S [VILLAGE]",
				"<init>: snake2d/util/color/ColorImp.<init>(JS)V [Local Returned]"
		));
		reads.put("world/map/regions/centre/WorldRaceSheet$Overlay", List.of(
				"<init>: value(S)S [OVERLAY]",
				"<init>: snake2d/util/color/ColorImp.<init>(JS)V [Local Returned]"
		));
		reads.put("world/map/regions/centre/WorldRaceSheet$Terrain", List.of(
				"<init>: value(S)S [TERRAIN]",
				"<init>: snake2d/util/color/ColorImp.<init>(JS)V [Local Returned]"
		));
		reads.put("world/map/regions/centre/WorldRaceSheet$Farm", List.of(
				"<init>: value(S)S [FARM]",
				"<init>: snake2d/util/color/ColorImp.cols(JS)Lsnake2d/util/sets/LIST; [Local Returned]"
		));
		reads.put("world/map/regions/centre/WorldRaceSheet$WallSteriods", List.of(
				"<init>: value(S)S [Local]",
				"<init>: snake2d/util/color/ColorImp.<init>(JS)V [Local Returned]"
		));
		reads.put("snake2d/util/color/ColorImp", List.of(
				"<init>: snake2d/util/color/ColorImp.<init>(JS)V [Local COLOR]",
				"cols: snake2d/util/color/ColorImp.cols(JS)Lsnake2d/util/sets/LIST; [Local COLOR]",
				"cols: has(S)Z [Local]",
				"cols: jsonIs(S)Z [Local]",
				"cols: json(S)J [Local]",
				"cols: has(S)Z [R]",
				"cols: has(S)Z [B]",
				"cols: has(S)Z [G]",
				"cols: snake2d/util/color/ColorImp.<init>(JS)V [Returned Local]",
				"cols: snake2d/util/color/ColorImp.<init>(JS)V [Returned FROM]",
				"cols: snake2d/util/color/ColorImp.<init>(JS)V [Returned TO]",
				"cols: i(SII)I [GENERATE 0 1024]",
				"cols: jsonsIs(S)Z [Local]",
				"cols: jsons(S)[J [Local]",
				"cols: snake2d/util/color/ColorImp.<init>(J)V [Unknown]",
				"cols: arrayIs(S)Z [Local]",
				"cols: values(S)[S [Local]",
				"cols: snake2d/util/color/ColorImp.set(SJ)Lsnake2d/util/color/ColorImp; [Unknown Local]",
				"cols: value(S)S [Local]",
				"cols: snake2d/util/color/ColorImp.set(SJ)Lsnake2d/util/color/ColorImp; [Returned Local]",
				"<init>: has(S)Z [Local]",
				"<init>: jsonIs(S)Z [Local]",
				"<init>: json(S)J [Local]",
				"<init>: has(S)Z [R]",
				"<init>: has(S)Z [G]",
				"<init>: has(S)Z [B]",
				"<init>: i(SII)I [R 0 511]",
				"<init>: i(SII)I [G 0 511]",
				"<init>: i(SII)I [B 0 511]",
				"<init>: value(S)S [Local]",
				"<init>: snake2d/util/color/ColorImp.set(SJ)Lsnake2d/util/color/ColorImp; [Returned Unknown]",
				"set: error(SLjava/lang/CharSequence;)V [Wrong format of color. Should be RED_GREEN_BLUE Local]",
				"set: error(SLjava/lang/CharSequence;)V [Wrong format of color. Should be RED_GREEN_BLUE, where RED, GREEN and BLUE is an integer 0-255. eg 129_12_0 Local]",
				"set: error(SLjava/lang/CharSequence;)V [Wrong format of color. Should be RED_GREEN_BLUE, where RED, GREEN and BLUE is an integer 0-255. eg 129_12_0 Unknown]"
		));
		reads.put("init/type/TRAITS$2", List.of(
				"<init>: util/keymap/RMAP$KJson.<init>(Lutil/keymap/RMAP;J)V [Local Local]",
				"process: d(SDD)D [Local 0.0 1.0]",
				"process: init/type/TRAITS$2.process(Linit/type/TRAIT;JSZ)V [Local Local Local Local]"
		));
		reads.put("init/resources/ResSupplies$1", List.of(
				"<init>: util/keymap/RMAP$KJson.<init>(Lutil/keymap/RMAP;SJ)V [Local Local Local]",
				"process: i(SII)I [Local 0 10000]",
				"process: init/resources/ResSupplies$1.process(Linit/resources/ResSupply;JSZ)V [Local Local Local Local]"
		));
		reads.put("init/resources/RES_AMOUNT", List.of(
				"make: keys()Lsnake2d/util/sets/LIST; []",
				"make: i(S)I [Returned]"
		));
		// The folder loader opens each race file and hands both copies to Race.
		reads.put("init/race/RACES", List.of(
				"<init>: <init>([Ljava/nio/file/Path;)V [Returned]",
				"<init>: <init>([Ljava/nio/file/Path;)V [Returned]",
				"<init>: init/race/Race.<init>(SJJLsnake2d/util/sets/ArrayList;)V [Unknown New New FieldRef]"
		));
		// Portrait FRAMES are read here. RaceFrameMaker.java read() uses values("FRAMES"); a missing sheet only warns.
		reads.put("init/race/appearence/RaceFrameMaker", List.of(
				"<init>: init/race/appearence/RaceFrameMaker.frame(SIJ)Linit/race/appearence/RaceFrameRaw; [_Overlays Unknown null]",
				"<init>: init/race/appearence/RaceFrameMaker.frame(SIJ)Linit/race/appearence/RaceFrameRaw; [_Overlays Unknown null]",
				"read: values(S)[S [FRAMES]",
				"read: init/race/appearence/RaceFrameMaker.read(Lsnake2d/util/sets/ArrayListGrower;SJ)Z [New Unknown Local]",
				"read: init/race/appearence/RaceFrameMaker.frame(SIJ)Linit/race/appearence/RaceFrameRaw; [Returned Returned Local]"
		));
		// Each biography line only hands its file on to BioLine. A new read in one of them turns the type off.
		for (int i = 1; i <= 23; i++) {
			if (i == 5) {
				reads.put("init/race/bio/BioLines$5", List.of(
						"<init>: init/race/bio/BioLine.<init>(Lsnake2d/util/sets/LISTE;JS[Ljava/lang/CharSequence;)V [Local Local Local Local]"));
			} else if (i == 6 || i == 7) {
				reads.put("init/race/bio/BioLines$" + i, List.of(
						"<init>: init/race/bio/BioLines$Friend.<init>(Lsnake2d/util/sets/LISTE;JS)V [Local Local Local]"));
			} else {
				reads.put("init/race/bio/BioLines$" + i, List.of(
						"<init>: init/race/bio/BioLine.<init>(Lsnake2d/util/sets/LISTE;JS)V [Local Local Local]"));
			}
		}
		reads.put("init/race/bio/BioLines$Friend", List.of(
				"<init>: init/race/bio/BioLine.<init>(Lsnake2d/util/sets/LISTE;JS)V [Local Local Local]"));
		reads.put("init/race/bio/BioLines$Origin", List.of(
				"<init>: init/race/bio/BioLine.<init>(Lsnake2d/util/sets/LISTE;JS)V [Local Local Local]"));
		// BioOpinionData.java: titles, FUNNY and NOTHING, then a required STATS_MORE and STATS_LESS.
		reads.put("init/race/bio/BioOpinionData", List.of(
				"<init>: init/race/bio/BioOpinionData.tt(SJJ)[Ljava/lang/CharSequence; [HAPPY Local Local]",
				"<init>: init/race/bio/BioOpinionData.tt(SJJ)[Ljava/lang/CharSequence; [HAPPY_SOSO Local Local]",
				"<init>: init/race/bio/BioOpinionData.tt(SJJ)[Ljava/lang/CharSequence; [HAPPY_NO Local Local]",
				"<init>: init/race/bio/BioOpinionData.tt(SJJ)[Ljava/lang/CharSequence; [FUNNY Local Local]",
				"<init>: init/race/bio/BioOpinionData.tt(SJJ)[Ljava/lang/CharSequence; [NOTHING Local Local]",
				"<init>: json(S)J [STATS_MORE]",
				"<init>: init/race/bio/BioOpinionData$1.<init>(Linit/race/bio/BioOpinionData;J)V [Local Returned]",
				"<init>: json(S)J [STATS_LESS]",
				"<init>: init/race/bio/BioOpinionData$2.<init>(Linit/race/bio/BioOpinionData;J)V [Local Returned]",
				"tt: has(S)Z [Local]",
				"tt: texts(S)[S [Local]",
				"tt: textsTry(S)[S [Local]"
		));
		reads.put("init/race/bio/BioOpinionData$1", List.of(
				"<init>: settlement/stats/util/StatsJson.<init>(J)V [Local]",
				"doWithTheJson: texts(S)[S [Local]",
				"doWithTheJson: texts(S)[S [Local]",
				"doWithTheJson: texts(S)[S [Local]"
		));
		reads.put("init/race/bio/BioOpinionData$2", List.of(
				"<init>: settlement/stats/util/StatsJson.<init>(J)V [Local]",
				"doWithTheJson: texts(S)[S [Local]"
		));
		// A boost value is parsed here. BoostSpecs.java ParseValue reads d and rejects a key that is not >ADD or >MUL.
		reads.put("game/boosting/BoostSpecs$ParseValue", List.of(
				"<init>: d(S)D [Local]",
				"<init>: error(SLjava/lang/CharSequence;)V [Malformed value. Must be a string that ends with >ADD (addition), or >MUL (Multiplication) Local]"
		));
		// Tourist text. Text.java Entry reads BAD, OK and GOOD under each rating.
		reads.put("game/tourism/Text$Entry", List.of(
				"<init>: json(S)J [Local]",
				"<init>: texts(S)[S [BAD]",
				"<init>: texts(S)[S [OK]",
				"<init>: texts(S)[S [GOOD]"
		));
		// WORLD_BUILDING. The key is on RDBuildings.init; this is the value, with no range.
		reads.put("world/region/building/RDBuildings$3", List.of(
				"<init>: util/keymap/RMAP$KJson.<init>(Lutil/keymap/RMAP;SJ)V [Local Local Local]",
				"process: d(S)D [Local]",
				"process: world/region/building/RDBuildings$3.process(Lworld/region/building/RDBuilding;JSZ)V [Local Local Local Local]"
		));
		// Addon and portrait CONDITIONS are handed to Lockable. The same reads as for rooms.
		reads.put("init/value/Lockable", List.of(
				"push: init/value/Lockable.push(SJ)V [REQUIRES Local]",
				"push: has(S)Z [Local]",
				"push: json(S)J [Local]",
				"push: init/value/Lockable.pushPush(J)V [Returned]",
				"pushPush: keys()Lsnake2d/util/sets/LIST; []",
				"pushPush: util/keymap/RMAP.get(SJ)Lutil/keymap/MAPPED; [Returned Local]",
				"pushPush: json(S)J [Returned]",
				"pushPush: keys()Lsnake2d/util/sets/LIST; []",
				"pushPush: d(S)D [Returned]",
				"pushPush: path()S []",
				"pushPush: line(S)I [Returned]"
		));
		// Trait chances on a race. The constructor also loads trait files; the race read is serRaceData.
		reads.put("init/type/TRAITS", List.of(
				"<init>: <init>([Ljava/nio/file/Path;)V [Returned]",
				"<init>: <init>([Ljava/nio/file/Path;)V [Returned]",
				"<init>: init/type/TRAIT.<init>(Lsnake2d/util/sets/LISTE;SJJ)V [New Unknown Unknown New]",
				"<init>: util/keymap/RMAPS.readMany(SJ)Lsnake2d/util/sets/LIST; [DISABLES_OTHERS Unknown]",
				"serRaceData: init/type/TRAITS$2.<init>(Lutil/keymap/RMAP;JLinit/race/Race;)V [FieldRef Local Local]"
		));
		// Military supply use. The key is on setEfficiency; the constructor loads supply files.
		reads.put("init/resources/ResSupplies", List.of(
				"<init>: <init>([Ljava/nio/file/Path;)V [Returned]",
				"<init>: init/resources/ResSupply.<init>(SJLsnake2d/util/sets/LISTE;)V [Unknown New New]",
				"<init>: error(SLjava/lang/CharSequence;)V [Returned Unknown]",
				"setEfficiency: init/resources/ResSupplies$1.<init>(Linit/resources/ResSupplies;Lutil/keymap/RMAP;SJLinit/race/Race;)V [Local FieldRef MILITARY_SUPPLY_USE Local Local]"
		));
		return reads;
	}
}
