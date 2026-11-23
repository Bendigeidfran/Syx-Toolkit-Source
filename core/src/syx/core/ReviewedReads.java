package syx.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import syx.core.ClassFileScanner.Call;
import syx.core.ClassFileScanner.Const;
import syx.core.ClassFileScanner.ScannedClass;
import syx.core.ClassFileScanner.ScannedMethod;
import syx.core.ClassFileScanner.Value;

/**
 * The gate for loaders whose reads are spread across many classes. Every read of a file, and every call
 * that hands a file onward, was reviewed by hand against the game's source, and is listed here. If the
 * user's jar differs in any way, the type turns off, so a game update can't leave a stale form.
 */
final class ReviewedReads {

	private static final String JSON = "snake2d/util/file/Json";
	private static final String JSON_TYPE = "Lsnake2d/util/file/Json;";

	private ReviewedReads() {
	}

	/** One line per read or hand-off in a class, in code order, without line numbers, which change freely. */
	static List<String> of(ScannedClass scanned) {
		List<String> lines = new ArrayList<>();
		for (ScannedMethod method : scanned.methods()) {
			for (Call call : method.calls()) {
				boolean onJson = call.owner().equals(JSON);
				boolean handsOn = call.descriptor().substring(0, call.descriptor().indexOf(')')).contains(JSON_TYPE);
				if (onJson || handsOn) {
					StringBuilder args = new StringBuilder();
					for (Value value : call.args()) {
						args.append(args.length() == 0 ? "" : " ").append(value instanceof Const constant ? String.valueOf(constant.value()) : value.getClass().getSimpleName());
					}
					lines.add(method.name() + ": " + (onJson ? "" : call.owner() + ".") + call.name() + short_(call.descriptor()) + " [" + args + "]");
				}
			}
		}
		return lines;
	}

	private static String short_(String descriptor) {
		return descriptor.replace("Ljava/lang/String;", "S").replace(JSON_TYPE, "J");
	}

	/** Adds a reason to unresolved for each class that's missing or differs. */
	static void check(ClassFileScanner scanner, Map<String, List<String>> expected, List<String> unresolved) throws ClassFileScanner.ScanException {
		for (Map.Entry<String, List<String>> entry : expected.entrySet()) {
			ScannedClass scanned = scanner.scan(entry.getKey());
			if (scanned == null) {
				unresolved.add("the class " + entry.getKey() + " is missing.");
				continue;
			}
			List<String> found = of(scanned);
			if (!found.equals(entry.getValue())) {
				unresolved.add(entry.getKey() + " reads its file differently from what was reviewed: " + firstDifference(entry.getValue(), found));
			}
		}
	}

	/** Every limit in a tag table must be one the reviewed reads use, so a table can't drift from what was reviewed. */
	static void checkLimits(AssetSchema schema, Map<String, List<String>> reviewed, List<String> unresolved) {
		List<String> all = reviewed.values().stream().flatMap(List::stream).toList();
		for (TagSpec tag : schema.dataTags()) {
			checkLimits(tag, all, unresolved);
		}
		for (TagSpec tag : schema.textTags()) {
			checkLimits(tag, all, unresolved);
		}
		for (AssetSchema.Companion companion : schema.companions()) {
			for (TagSpec tag : companion.tags()) {
				checkLimits(tag, all, unresolved);
			}
		}
	}

	private static void checkLimits(TagSpec tag, List<String> reads, List<String> unresolved) {
		// A nested tag's path isn't what the game passes to Json. The last part is.
		String leaf = tag.leafName();
		String wanted = switch (tag.kind()) {
		case DECIMAL -> tag.min() == null ? null : "[" + leaf + " " + tag.min() + " " + tag.max();
		case INT -> tag.min() == null ? null : "[" + leaf + " " + tag.min().intValue() + " " + (tag.max() == null ? Integer.MAX_VALUE : tag.max().intValue());
		// RMAP.readFill(json, max) reads from 0 (RMAP.java:211-220).
		case DECIMAL_MAP -> {
			if (tag.min() == null || tag.max() == null) {
				yield null;
			}
			// readFill's json argument is a Local, a Returned, or a New, depending on the caller.
			String number = " " + tag.max() + "]";
			if (tag.min() == 0.0 && reads.stream().anyMatch(read -> read.contains("readFill") && read.contains(number))) {
				yield number;
			}
			yield " " + tag.min() + " " + tag.max() + "]";
		}
		case TEXT_ROWS -> tag.shape().maxEntries() == 0 ? null : "[" + leaf + " 1 " + tag.shape().maxEntries() + "]";
		default -> null;
		};
		requireRead(wanted, tag.key(), reads, unresolved);
		for (TagSpec.Column column : tag.shape().columns()) {
			String name = column.key() == null ? "Returned" : column.key();
			requireRead("[" + name + " " + column.min() + " " + column.max(), tag.key() + "." + name, reads, unresolved);
		}
		for (TagSpec child : tag.shape().children()) {
			checkLimits(child, reads, unresolved);
		}
	}

	private static void requireRead(String wanted, String key, List<String> reads, List<String> unresolved) {
		if (wanted != null && reads.stream().noneMatch(read -> read.contains(wanted))) {
			unresolved.add("the limits recorded for " + key + " aren't among the reviewed reads.");
		}
	}

	private static String firstDifference(List<String> expected, List<String> found) {
		for (int i = 0; i < Math.max(expected.size(), found.size()); i++) {
			String wanted = i < expected.size() ? expected.get(i) : "nothing";
			String got = i < found.size() ? found.get(i) : "nothing";
			if (!wanted.equals(got)) {
				return "expected " + wanted + ", found " + got + ".";
			}
		}
		return "the lists differ.";
	}

	/** The class that reads an icon, in either of the forms a tag can have. */
	static Map<String, List<String>> icons() {
		Map<String, List<String>> reads = new LinkedHashMap<>();
		reads.put("init/sprite/UI/Icons", List.of(
				"get: init/sprite/UI/Icons.get(JS)Linit/sprite/UI/Icon; [Local ICON]",
				"get: has(S)Z [ICON]",
				"get: init/sprite/UI/Icons.get(JS)Linit/sprite/UI/Icon; [Local ICON]",
				"get: jsonsIs(S)Z [Local]",
				"get: jsons(SI)[J [Local 1]",
				"get: value(S)S [BG]",
				"get: init/sprite/UI/Icons.get(SJS)Linit/sprite/UI/Icon; [Returned Unknown BG]",
				"get: jsonIs(S)Z [Local]",
				"get: json(S)J [Local]",
				"get: init/sprite/UI/Icons.getJ(J)Linit/sprite/UI/Icon; [Returned]",
				"get: value(S)S [Local]",
				"get: init/sprite/UI/Icons.get(SJS)Linit/sprite/UI/Icon; [Returned Local Local]",
				"getJ: i(SIII)I [SCALE 1 8 1]",
				"getJ: value(S)S [BG]",
				"getJ: init/sprite/UI/Icons.get(SJS)Linit/sprite/UI/Icon; [Returned Local BG]",
				"getJ: has(S)Z [COLOR]",
				"getJ: snake2d/util/color/ColorImp.<init>(JS)V [Local COLOR]",
				"getJ: has(S)Z [FG]",
				"getJ: value(S)S [FG]",
				"getJ: init/sprite/UI/Icons.get(SJS)Linit/sprite/UI/Icon; [Returned Local FG]",
				"getJ: i(SIII)I [OFFX -100 100 0]",
				"getJ: i(SIII)I [OFFY -100 100 0]",
				"getJ: i(SIII)I [SHADOW -100 100 0]",
				"get: errorGet(SLjava/lang/CharSequence;)S [is badly formatted. Needs to contain a path with separation denoted by -> and the final entry being a number indicating which icon to pick of the sheet Local]",
				"get: init/paths/PathParser.error(SJS)V [icons must be either in 16, 24 or 32 folders. Local Local]",
				"get: init/sprite/UI/IconMaker.get(SJS)Linit/sprite/UI/Icon; [Returned Local Local]"));
		return reads;
	}

	/** The classes behind a Farm, with what each does with a file. */
	static Map<String, List<String>> farm() {
		Map<String, List<String>> reads = new LinkedHashMap<>(icons());
		reads.put("settlement/room/food/farm/ROOM_FARM", List.of(
				"<init>: util/keymap/RMAPS.read(J)Lutil/keymap/MAPPED; [Returned]",
				"<init>: settlement/room/food/farm/ROOM_FARM.pushBo(JSZ)Lgame/boosting/Boostable; [Returned FARM 1]",
				"<init>: settlement/room/industry/module/Industry.<init>(Lsettlement/room/main/RoomBlueprintImp;JLgame/boosting/Boostable;)V [Local Returned Returned]",
				"<init>: settlement/room/main/BonusExperience$RoomExperienceBonus.<init>(Lsettlement/room/main/RoomBlueprintImp;JLgame/boosting/Boostable;)V [Local Returned Returned]"));
		reads.put("settlement/room/food/farm/Constructor", List.of(
				"<init>: bool(S)Z [INDOORS]",
				"<init>: init/sprite/game/GameSheets.sheets(Linit/sprite/game/SheetType;SJ)Lsnake2d/util/sets/LIST; [FieldRef _FARM_DIRT null]"));
		reads.put("settlement/room/main/furnisher/Furnisher", List.of(
				"<init>: util/keymap/RMAPS.readMany(J)Lsnake2d/util/sets/LIST; [Returned]",
				"<init>: error(SLjava/lang/CharSequence;)V [Too many resources declared. Max is 4 RESOURCES]",
				"<init>: ds(SI)[D [AREA_COSTS Returned]",
				"<init>: has(S)Z [FieldRef]",
				"<init>: arrayIs(S)Z [FieldRef]",
				"<init>: util/keymap/RMAP.readManyWarn(SJ)Lsnake2d/util/sets/LIST; [FieldRef Returned]",
				"<init>: util/keymap/RMAP.readTry(J)Lutil/keymap/MAPPED; [Returned]",
				"<init>: error(SLjava/lang/CharSequence;)V [no floor named:  FieldRef]",
				"<init>: jsons(SI)[J [STATS Local]",
				"<init>: error(SLjava/lang/CharSequence;)V [Returned STATS]",
				"<init>: jsons(SI)[J [ITEMS Local]",
				"<init>: jsons(SI)[J [ITEMS Local]",
				"<init>: error(SLjava/lang/CharSequence;)V [Returned ITEMS]",
				"<init>: snake2d/util/color/ColorImp.<init>(JS)V [Returned MINI_COLOR]",
				"<init>: settlement/room/main/furnisher/FurnisherMinimapColor.<init>(J)V [Returned]",
				"<init>: has(S)Z [ENVIRONMENT_EMIT]",
				"<init>: json(S)J [ENVIRONMENT_EMIT]",
				"<init>: keys()Lsnake2d/util/sets/LIST; []",
				"<init>: util/keymap/RMAP.getWarn(SJ)Lutil/keymap/MAPPED; [Returned Returned]",
				"<init>: json(S)J [Returned]",
				"<init>: d(SDD)D [VALUE 0.0 1.0]",
				"<init>: d(SDD)D [RADIUS 0.0 1.0]",
				"flush: text(S)S [NAME]",
				"flush: text(S)S [DESC]",
				"flush: ds(SI)[D [COSTS Returned]",
				"flush: ds(SI)[D [STATS Returned]"));
		reads.put("settlement/room/main/RoomBlueprintImp", List.of(
				"<init>: util/info/INFO.<init>(JLsnake2d/util/misc/ACTION;)V [Returned Local]",
				"<init>: has(S)Z [DEGRADE_RATE]",
				"<init>: d(SDD)D [DEGRADE_RATE 0.0 1.0]",
				"<init>: init/value/Lockable.push(J)V [Returned]",
				"icon: init/sprite/UI/Icons.get(J)Linit/sprite/UI/Icon; [Returned]"));
		reads.put("settlement/room/main/RoomBlueprintIns", List.of(
				"<init>: has(S)Z [WORK]",
				"<init>: has(S)Z [EMPLOYMENT]",
				"pushBo: settlement/room/main/RoomBlueprintIns.pushBo(JLjava/lang/CharSequence;Ljava/lang/CharSequence;SZD)Lgame/boosting/Boostable; [Local Local Local Local Local 1.0]",
				"pushBo: has(S)Z [BONUS]",
				"pushBo: json(S)J [BONUS]",
				"pushBo: init/type/CLIMATES.pushBonuses(JLgame/boosting/Boostable;)V [Returned FieldRef]",
				"pushBo: settlement/room/main/RoomBlueprintIns.pushBo(JLjava/lang/CharSequence;Ljava/lang/CharSequence;SZ)Lgame/boosting/Boostable; [Local FieldRef Returned Local Local]"));
		reads.put("settlement/room/main/RoomUpgrades", List.of(
				"<init>: has(S)Z [UPGRADES]",
				"<init>: jsons(SI)[J [UPGRADES 1]",
				"<init>: ds(S)[D [RESOURCE_MASK]",
				"<init>: d(S)D [BOOST]",
				"<init>: has(S)Z [AI]",
				"<init>: d(SDD)D [AI 0.0 10000.0]",
				"<init>: textsTry(S)[S [UPGRADES]"));
		reads.put("settlement/room/main/employment/RoomEmploymentSimple", List.of(
				"<init>: json(S)J [Local]",
				"<init>: json(S)J [Local]",
				"<init>: text(S)S [TITLE]",
				"<init>: text(S)S [VERB]",
				"<init>: d(SDD)D [SHIFT_OFFSET 0.0 0.99]",
				"<init>: has(S)Z [NIGHT_SHIFT]",
				"<init>: bool(S)Z [NIGHT_SHIFT]",
				"<init>: has(S)Z [FULFILLMENT]",
				"<init>: d(SDD)D [FULFILLMENT -100000.0 100000.0]",
				"<init>: dTry(SDDD)D [ACCIDENTS_PER_YEAR 0.0 10000.0 0.0]",
				"<init>: dTry(SDDD)D [HEALTH_FACTOR 0.0 1.0 1.0]",
				"<init>: i(SIII)I [LARGE_WORKFORCE 10 10000 1000]"));
		reads.put("settlement/room/main/furnisher/FurnisherMinimapColor", List.of(
				"<init>: has(S)Z [MINI_COLOR]",
				"<init>: snake2d/util/color/ColorImp.<init>(JS)V [Local MINI_COLOR]",
				"<init>: has(S)Z [MINI_COLOR_PATTERN]",
				"<init>: texts(SII)[S [MINI_COLOR_PATTERN 1 32]",
				"<init>: error(SLjava/lang/CharSequence;)V [the pattern must have the same length of all its strings! MINI_COLOR_PATTERN]"));
		reads.put("settlement/room/industry/module/Industry", List.of(
				"<init>: json(S)J [INDUSTRY]",
				"<init>: has(S)Z [IN]",
				"<init>: json(S)J [IN]",
				"<init>: keys()Lsnake2d/util/sets/LIST; []",
				"<init>: util/keymap/RMAPS.get(SJ)Lutil/keymap/MAPPED; [Returned Returned]",
				"<init>: d(SDD)D [Returned 0.0 10000.0]",
				"<init>: has(S)Z [OUT]",
				"<init>: json(S)J [OUT]",
				"<init>: keys()Lsnake2d/util/sets/LIST; []",
				"<init>: util/keymap/RMAPS.get(SJ)Lutil/keymap/MAPPED; [Returned Returned]",
				"<init>: jsonIs(S)Z [Returned]",
				"<init>: json(S)J [Returned]",
				"<init>: d(SDD)D [PLAYER 0.0 100000.0]",
				"<init>: dTry(SDDD)D [AI_RATE 0.0 100000.0 Returned]",
				"<init>: dTry(SDDD)D [AI_RECOVERY 0.0 100000.0 1.0]",
				"<init>: d(SDD)D [Returned 0.0 10000.0]",
				"<init>: has(S)Z [ICON]",
				"<init>: init/sprite/UI/Icons.get(J)Linit/sprite/UI/Icon; [Returned]",
				"createIndustries: jsons(SI)[J [INDUSTRIES 1]",
				"createIndustries: settlement/room/industry/module/Industry.<init>(Lsettlement/room/main/RoomBlueprintImp;JLgame/boosting/Boostable;)V [Local Unknown Local]",
				"createIndustries: error(SLjava/lang/CharSequence;)V [Returned INDUSTRIES]"));
		reads.put("init/type/CLIMATES", List.of(
				"pushBonuses: has(S)Z [CLIMATE]",
				"pushBonuses: util/keymap/RMAP.readFill([DJDD)V [Unknown Local 0.0 2000.0]"));
		reads.put("settlement/room/main/furnisher/FurnisherStat", List.of(
				"<init>: text(S)S [NAME]",
				"<init>: text(S)S [DESC]",
				"<init>: text(S)S [NAME]",
				"<init>: text(S)S [DESC]"));
		reads.put("settlement/room/main/BonusExperience$RoomExperienceBonus", List.of(
				"<init>: has(S)Z [EXPERIENCE_BONUS]",
				"<init>: json(S)J [EXPERIENCE_BONUS]",
				"<init>: i(SII)I [MAX_EMPLOYEES 50 2147483647]",
				"<init>: d(S)D [BONUS]"));
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
				"pushPush: line(S)I [Returned]"));
		reads.put("util/info/INFO", List.of(
				"<init>: has(S)Z [NAME]",
				"<init>: json(S)J [INFO]",
				"<init>: text(S)S [NAME]",
				"<init>: has(S)Z [NAMES]",
				"<init>: text(S)S [NAMES]",
				"<init>: text(S)S [DESC]",
				"<init>: view/ui/wiki/WIKI.add(J)Lsnake2d/util/misc/ACTION; [Unknown]",
				"<init>: util/info/INFO.<init>(JLsnake2d/util/misc/ACTION;)V [Local null]"));
		return reads;
	}

	/** The classes behind a Mine. It shares a farm's room classes, and draws with sprites where a farm has none. */
	static Map<String, List<String>> mine() {
		Map<String, List<String>> reads = new LinkedHashMap<>(farm());
		reads.remove("settlement/room/food/farm/ROOM_FARM");
		reads.remove("settlement/room/food/farm/Constructor");
		reads.remove("settlement/room/industry/module/Industry");
		reads.put("settlement/room/industry/mine/ROOM_MINE", List.of(
				"<init>: util/keymap/RMAP.read(J)Lutil/keymap/MAPPED; [Returned]",
				"<init>: settlement/room/industry/mine/ROOM_MINE.pushBo(JSZ)Lgame/boosting/Boostable; [Returned MINE 1]",
				"<init>: d(SDD)D [YEILD_WORKER_DAILY 0.0 1000.0]",
				"<init>: i(SII)I [STORAGE 4 500]",
				"<init>: settlement/room/main/BonusExperience$RoomExperienceBonus.<init>(Lsettlement/room/main/RoomBlueprintImp;JLgame/boosting/Boostable;)V [Local Returned Returned]"));
		String c = "settlement/room/industry/mine/Constructor$";
		reads.put("settlement/room/industry/mine/Constructor", List.of(
				"<init>: util/keymap/RMAP.read(J)Lutil/keymap/MAPPED; [Returned]",
				"<init>: json(S)J [SPRITES]",
				"<init>: " + c + "5.<init>(Lsettlement/room/industry/mine/Constructor;JS)V [Local Returned CONVEYOR_1X1]",
				"<init>: " + c + "6.<init>(Lsettlement/room/industry/mine/Constructor;JSZ)V [Local Returned STORAGE_1X1_TOP 1]",
				"<init>: " + c + "7.<init>(Lsettlement/room/industry/mine/Constructor;JSZ)V [Local Returned STORAGE_1X1_MID 0]",
				"<init>: " + c + "8.<init>(Lsettlement/room/industry/mine/Constructor;JSZ)V [Local Returned STORAGE_1X1_END 0]",
				"<init>: " + c + "9.<init>(Lsettlement/room/industry/mine/Constructor;JS)V [Local Returned SLAB_1X1]",
				"<init>: " + c + "11.<init>(Lsettlement/room/industry/mine/Constructor;JS)V [Local Returned AUX_1X1]",
				"<init>: " + c + "12.<init>(Lsettlement/room/industry/mine/Constructor;JSZ)V [Local Returned AUX_MEDIUM_A_1X1 1]",
				"<init>: " + c + "13.<init>(Lsettlement/room/industry/mine/Constructor;JSZ)V [Local Returned AUX_MEDIUM_B_1X1 0]",
				"<init>: " + c + "14.<init>(Lsettlement/room/industry/mine/Constructor;JSI)V [Local Returned AUX_BIG_2X2 2]",
				"<init>: " + c + "15.<init>(Lsettlement/room/industry/mine/Constructor;JS)V [Local Returned WORK_1X1]"));
		String one = "<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local Local]";
		String oneN = "<init>: settlement/room/sprite/RoomSprite1xN.<init>(JSZ)V [Local Local Local]";
		for (int i : new int[] { 5, 9, 11, 15 }) {
			reads.put(c + i, List.of(one));
		}
		for (int i : new int[] { 6, 7, 8, 12, 13 }) {
			reads.put(c + i, List.of(oneN));
		}
		reads.put(c + "14", List.of("<init>: settlement/room/sprite/RoomSpriteXxX.<init>(JSI)V [Local Local Local]"));
		reads.put("settlement/room/sprite/RoomSpriteXxX", List.of("<init>: settlement/room/sprite/RoomSpriteImp.<init>(Linit/sprite/game/SheetType;JS)V [Returned Local Local]"));
		reads.putAll(sprites());
		return reads;
	}

	/** The classes behind a Refiner. It shares a farm's room classes and recipes, and draws with sprites as a mine does. */
	static Map<String, List<String>> refiner() {
		Map<String, List<String>> reads = new LinkedHashMap<>(farm());
		reads.remove("settlement/room/food/farm/ROOM_FARM");
		reads.remove("settlement/room/food/farm/Constructor");
		reads.put("settlement/room/industry/refiner/ROOM_REFINER", List.of(
				"<init>: settlement/room/industry/refiner/ROOM_REFINER.pushBo(JSZ)Lgame/boosting/Boostable; [Returned REFINER 1]",
				"<init>: i(SII)I [STORAGE 8 500]",
				"<init>: settlement/room/main/BonusExperience$RoomExperienceBonus.<init>(Lsettlement/room/main/RoomBlueprintImp;JLgame/boosting/Boostable;)V [Local Returned Returned]"));
		String c = "settlement/room/industry/refiner/Constructor";
		String own = "L" + c + ";";
		String blue = "Lsettlement/room/industry/refiner/ROOM_REFINER;";
		String sprite = "Lsettlement/room/sprite/RoomSprite;";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Returned MAIN_MACHINE_COMBO_TOP]",
				"<init>: " + c + "$3.<init>(" + own + "JSJ" + sprite + ")V [Local Returned MAIN_MACHINE_COMBO Returned New]",
				"<init>: " + c + "$4.<init>(" + own + "JS" + blue + ")V [Local Returned WORK_1X1 Local]",
				"<init>: " + c + "$5.<init>(" + own + "JS" + blue + ")V [Local Returned STORAGE_IN_1X1 Local]",
				"<init>: " + c + "$6.<init>(" + own + "JSJ)V [Local Returned NICKNACK_BOTTOM_1X1 Returned]",
				"<init>: " + c + "$7.<init>(" + own + "JS)V [Local Returned MACHINE_1X1]",
				"<init>: " + c + "$8.<init>(" + own + "JS)V [Local Returned CONVEYOR_1X1]",
				"<init>: " + c + "$9.<init>(" + own + "JSZ" + blue + sprite + ")V [Local Returned STORAGE_1X1 0 Local New]"));
		String one = "<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local Local]";
		reads.put(c + "$3", List.of("<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Local Local]", "<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local MAIN_MACHINE_COMBO_TOP_MACHINE]"));
		reads.put(c + "$4", List.of(one));
		reads.put(c + "$5", List.of(one));
		reads.put(c + "$6", List.of(one, "<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local NICKNACK_TOP_1X1]"));
		reads.put(c + "$7", List.of(one));
		reads.put(c + "$8", List.of(one));
		reads.put(c + "$9", List.of("<init>: settlement/room/sprite/RoomSprite1xN.<init>(JSZ)V [Local Local Local]"));
		reads.put("settlement/room/sprite/RoomSpriteCombo", List.of("<init>: settlement/room/sprite/RoomSpriteImp.<init>(Linit/sprite/game/SheetType;JS)V [FieldRef Local Local]"));
		reads.putAll(sprites());
		return reads;
	}

	/**
	 * The classes behind a Workshop. It shares a farm's room classes and a refiner's recipes, and draws
	 * with its own sprites. The first two anonymous classes are the stat counters, and the file is never
	 * handed to them, so they aren't listed.
	 */
	static Map<String, List<String>> workshop() {
		Map<String, List<String>> reads = new LinkedHashMap<>(farm());
		reads.remove("settlement/room/food/farm/ROOM_FARM");
		reads.remove("settlement/room/food/farm/Constructor");
		reads.put("settlement/room/industry/workshop/ROOM_WORKSHOP", List.of(
				"<init>: settlement/room/industry/workshop/ROOM_WORKSHOP.pushBo(JSZ)Lgame/boosting/Boostable; [Returned WORKSHOP 1]",
				"<init>: settlement/room/main/BonusExperience$RoomExperienceBonus.<init>(Lsettlement/room/main/RoomBlueprintImp;JLgame/boosting/Boostable;)V [Local Returned Returned]"));
		String c = "settlement/room/industry/workshop/Constructor";
		String own = "L" + c + ";";
		String blue = "Lsettlement/room/industry/workshop/ROOM_WORKSHOP;";
		String sprite = "Lsettlement/room/sprite/RoomSprite;";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: " + c + "$3.<init>(" + own + "JS)V [Local Returned CHAIR_1X1]",
				"<init>: " + c + "$4.<init>(" + own + "J" + blue + ")V [Local Returned Local]",
				"<init>: " + c + "$5.<init>(" + own + "J" + blue + ")V [Local Returned Local]",
				// The last argument is the chair, captured because the work spot joins to it (Constructor.java:151).
				"<init>: " + c + "$6.<init>(" + own + "JJ" + blue + sprite + ")V [Local Returned Returned Local New]",
				"<init>: " + c + "$7.<init>(" + own + "J" + blue + ")V [Local Returned Local]",
				"<init>: " + c + "$8.<init>(" + own + "JJ)V [Local Returned Returned]",
				"<init>: " + c + "$9.<init>(" + own + "JSJ)V [Local Returned MISC_BELOW_1X1 Returned]"));
		reads.put(c + "$STable", List.of(
				"<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Local TABLE_COMBO]",
				"<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Local TABLE_TOP_COMBO]"));
		String one = "<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local Local]";
		String table = "<init>: " + c + "$STable.<init>(J)V [Local]";
		reads.put(c + "$3", List.of(one));
		reads.put(c + "$4", List.of(table));
		reads.put(c + "$5", List.of(table));
		reads.put(c + "$6", List.of(
				table,
				"<init>: " + c + "$6$1.<init>(L" + c + "$6;JS" + blue + ")V [Local Local WORK_BELOW_1X1 Local]",
				"<init>: " + c + "$6$2.<init>(L" + c + "$6;JS" + blue + sprite + ")V [Local Local WORK_ABOVE_1X1 Local Local]"));
		reads.put(c + "$6$1", List.of(one));
		reads.put(c + "$6$2", List.of(one));
		reads.put(c + "$7", List.of(table));
		reads.put(c + "$8", List.of(
				table,
				"<init>: " + c + "$8$1.<init>(L" + c + "$8;JS)V [Local Local TOOL_1X1]"));
		reads.put(c + "$8$1", List.of(one));
		reads.put(c + "$9", List.of(
				one,
				"<init>: " + c + "$9$1.<init>(L" + c + "$9;JS)V [Local Local MISC_ABOVE_1X1]"));
		reads.put(c + "$9$1", List.of(one));
		reads.put("settlement/room/sprite/RoomSpriteCombo", List.of("<init>: settlement/room/sprite/RoomSpriteImp.<init>(Linit/sprite/game/SheetType;JS)V [FieldRef Local Local]"));
		reads.putAll(sprites());
		return reads;
	}

	/**
	 * The classes behind a Pasture. It shares a farm's room classes and recipes, and draws with its own
	 * sprites. Which constructor runs depends on INDOORS, and both are in the jar, so both are listed.
	 * JobManager, Gui and PastureInstance never read the file. The empty anonymous classes are boosts,
	 * the irrigation overlay and placability tiles, and the file is never handed to them.
	 */
	static Map<String, List<String>> pasture() {
		Map<String, List<String>> reads = new LinkedHashMap<>(farm());
		reads.remove("settlement/room/food/farm/ROOM_FARM");
		reads.remove("settlement/room/food/farm/Constructor");
		reads.put("settlement/room/food/pasture/ROOM_PASTURE", List.of(
				"<init>: util/keymap/RMAPS.read(J)Lutil/keymap/MAPPED; [Returned]",
				"<init>: bool(SZ)Z [INDOORS 0]",
				"<init>: settlement/room/food/pasture/ROOM_PASTURE.pushBo(JSZ)Lgame/boosting/Boostable; [Returned PASTURE 1]",
				"<init>: error(SLjava/lang/CharSequence;)V [Can't declare more than 3 output in industry! ]",
				"<init>: error(SLjava/lang/CharSequence;)V [Can't declare inputs to a pasture industry ]",
				"<init>: settlement/room/main/BonusExperience$RoomExperienceBonus.<init>(Lsettlement/room/main/RoomBlueprintImp;JLgame/boosting/Boostable;)V [Local Returned Returned]"));
		String c = "settlement/room/food/pasture/Constructor";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Returned FENCE_COMBO]",
				"<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Returned FENCE_D_COMBO]",
				"makeAux: " + c + "$4.<init>(L" + c + ";JSZ)V [Local Local AUX_EDGE_1X1 0]",
				"makeAux: " + c + "$5.<init>(L" + c + ";JSZ)V [Local Local AUX_MID_1X1 1]",
				"makeAux: " + c + "$6.<init>(L" + c + ";JSI)V [Local Local AUX_BIG_2X2 2]"));
		reads.put(c + "$4", List.of("<init>: settlement/room/sprite/RoomSprite1xN.<init>(JSZ)V [Local Local Local]"));
		reads.put(c + "$5", List.of("<init>: settlement/room/sprite/RoomSprite1xN.<init>(JSZ)V [Local Local Local]"));
		reads.put(c + "$6", List.of("<init>: settlement/room/sprite/RoomSpriteXxX.<init>(JSI)V [Local Local Local]"));
		String indoor = "settlement/room/food/pasture/ConstructorIndoor";
		reads.put(indoor, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: " + indoor + "$SpriteDep.<init>(JLsettlement/room/main/job/RoomResStorage;)V [Returned FieldRef]",
				"<init>: " + indoor + "$SpriteDep.<init>(JLsettlement/room/main/job/RoomResStorage;)V [Returned FieldRef]",
				"<init>: " + indoor + "$SpriteDep.<init>(JLsettlement/room/main/job/RoomResStorage;)V [Returned FieldRef]",
				"<init>: " + indoor + ".makeAux(J)V [Returned]"));
		reads.put(indoor + "$SpriteDep", List.of("<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local STORAGE_1X1]"));
		String outdoor = "settlement/room/food/pasture/ConstructorOutdoor";
		reads.put(outdoor, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned STORAGE_1X1]",
				"<init>: " + outdoor + "$3.<init>(L" + outdoor + ";JSILsettlement/room/sprite/RoomSprite;)V [Local Returned GATE_TOP_3X3 3 New]",
				"<init>: " + outdoor + "$SpriteDep.<init>(JLsettlement/room/sprite/RoomSprite;Lsettlement/room/main/job/RoomResStorage;)V [Returned New FieldRef]",
				"<init>: " + outdoor + "$SpriteDep.<init>(JLsettlement/room/sprite/RoomSprite;Lsettlement/room/main/job/RoomResStorage;)V [Returned New FieldRef]",
				"<init>: " + outdoor + "$SpriteDep.<init>(JLsettlement/room/sprite/RoomSprite;Lsettlement/room/main/job/RoomResStorage;)V [Returned New FieldRef]",
				"<init>: " + outdoor + ".makeAux(J)V [Returned]"));
		reads.put(outdoor + "$3", List.of("<init>: settlement/room/sprite/RoomSpriteXxX.<init>(JSI)V [Local Local Local]"));
		reads.put(outdoor + "$SpriteDep", List.of("<init>: settlement/room/sprite/RoomSpriteXxX.<init>(JSI)V [Local GATE_TOP_3X3 3]"));
		reads.put("settlement/room/sprite/RoomSpriteCombo", List.of("<init>: settlement/room/sprite/RoomSpriteImp.<init>(Linit/sprite/game/SheetType;JS)V [FieldRef Local Local]"));
		reads.put("settlement/room/sprite/RoomSpriteXxX", List.of("<init>: settlement/room/sprite/RoomSpriteImp.<init>(Linit/sprite/game/SheetType;JS)V [Returned Local Local]"));
		reads.putAll(sprites());
		return reads;
	}

	/** The classes behind any food room: a farm's room classes and recipes, and sprites as a mine draws them. */
	private static Map<String, List<String>> foodRoom() {
		Map<String, List<String>> reads = new LinkedHashMap<>(farm());
		reads.remove("settlement/room/food/farm/ROOM_FARM");
		reads.remove("settlement/room/food/farm/Constructor");
		reads.put("settlement/room/sprite/RoomSpriteCombo", List.of("<init>: settlement/room/sprite/RoomSpriteImp.<init>(Linit/sprite/game/SheetType;JS)V [FieldRef Local Local]"));
		reads.putAll(sprites());
		return reads;
	}

	private static final String SPRITE_XXX = "settlement/room/sprite/RoomSpriteXxX";
	private static final List<String> SPRITE_XXX_READS = List.of("<init>: settlement/room/sprite/RoomSpriteImp.<init>(Linit/sprite/game/SheetType;JS)V [Returned Local Local]");
	private static final String INDUSTRY_READ = "<init>: settlement/room/industry/module/Industry.<init>(Lsettlement/room/main/RoomBlueprintImp;JLgame/boosting/Boostable;)V [Local Returned Returned]";
	private static final String EXPERIENCE_READ = "<init>: settlement/room/main/BonusExperience$RoomExperienceBonus.<init>(Lsettlement/room/main/RoomBlueprintImp;JLgame/boosting/Boostable;)V [Local Returned Returned]";
	private static final String ONE_BY_ONE = "<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local Local]";

	/** The classes behind a Fishery. Job, FishInstance and the first six anonymous classes, which are stats, never see the file. */
	static Map<String, List<String>> fishery() {
		Map<String, List<String>> reads = foodRoom();
		String room = "settlement/room/food/fish/ROOM_FISHERY";
		reads.put(room, List.of("<init>: " + room + ".pushBo(JSZ)Lgame/boosting/Boostable; [Returned FISHERY 1]", INDUSTRY_READ, EXPERIENCE_READ));
		String c = "settlement/room/food/fish/Constructor";
		String own = "L" + c + ";";
		String blue = "L" + room + ";";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: " + c + "$7.<init>(" + own + "JSJ)V [Local Returned STORAGE_BOTTOM_1X1 Returned]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned MISC_TOP_1X1]",
				"<init>: " + c + "$8.<init>(" + own + "JSLsettlement/room/sprite/RoomSprite1x1;)V [Local Returned CANDLE_1X1 New]",
				"<init>: settlement/room/sprite/RoomSprite1xN.<init>(JSZ)V [Returned AUX_EDGE_1X1 0]",
				"<init>: settlement/room/sprite/RoomSprite1xN.<init>(JSZ)V [Returned AUX_MID_1X1 1]",
				"<init>: settlement/room/sprite/RoomSpriteXxX.<init>(JSI)V [Returned AUX_BIG_2X2 2]",
				"<init>: " + c + "$9.<init>(" + own + "JS" + blue + ")V [Local Returned WORKTABLE_1X1 Local]",
				"<init>: " + c + "$10.<init>(" + own + "JS" + blue + ")V [Local Returned MISC_1X1 Local]",
				"<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Returned EDGE_COMBO]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned MISC_GROUND_1X1]"));
		reads.put(c + "$7", List.of(ONE_BY_ONE, "<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local STORAGE_TOP_1X1]"));
		reads.put(c + "$8", List.of(ONE_BY_ONE));
		reads.put(c + "$9", List.of(ONE_BY_ONE));
		reads.put(c + "$10", List.of(ONE_BY_ONE));
		reads.put(SPRITE_XXX, SPRITE_XXX_READS);
		return reads;
	}

	/** The classes behind an Orchard. Instance, OTile and the stat counters never see the file. */
	static Map<String, List<String>> orchard() {
		Map<String, List<String>> reads = foodRoom();
		String room = "settlement/room/food/orchard/ROOM_ORCHARD";
		reads.put(room, List.of(
				"<init>: " + room + ".pushBo(JSZ)Lgame/boosting/Boostable; [Returned ORCHARD 1]",
				INDUSTRY_READ,
				"<init>: util/keymap/RMAPS.read(SJ)Lutil/keymap/MAPPED; [EXTRA_RESOURCE Returned]",
				"<init>: i(S)I [EXTRA_RESOURCE_AMOUNT]",
				EXPERIENCE_READ));
		reads.put(room + "$Time", List.of(
				"<init>: i(SII)I [DAYS_TILL_GROWTH 8 1024]",
				"<init>: d(SDD)D [RIPE_AT_PART_OF_YEAR 0.0 1.0]"));
		String c = "settlement/room/food/orchard/Constructor";
		String own = "L" + c + ";";
		String one = "Lsettlement/room/sprite/RoomSprite1x1;";
		reads.put(c, List.of(
				"<init>: bool(S)Z [INDOORS]",
				"<init>: json(S)J [SPRITES]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned FRUIT_1X1]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned TREE_1X1]",
				"<init>: " + c + "$6.<init>(" + own + "JSIL" + room + ";" + one + one + ")V [Local Returned TREE_2X2 2 Local New New]",
				"<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Returned EDGE_COMBO]",
				"<init>: " + c + "$7.<init>(" + own + "JS)V [Local Returned STORAGE_1X1]"));
		reads.put(c + "$6", List.of("<init>: settlement/room/sprite/RoomSpriteXxX.<init>(JSI)V [Local Local Local]"));
		reads.put(c + "$7", List.of(ONE_BY_ONE));
		reads.put(SPRITE_XXX, SPRITE_XXX_READS);
		return reads;
	}

	/** The classes behind a Hunter. It reads no EXPERIENCE_BONUS, and Job, Tile and HunterInstance never see the file. */
	static Map<String, List<String>> hunter() {
		Map<String, List<String>> reads = foodRoom();
		reads.remove("settlement/room/main/BonusExperience$RoomExperienceBonus");
		String room = "settlement/room/food/hunter/ROOM_HUNTER";
		reads.put(room, List.of(
				"<init>: " + room + ".pushBo(JSZ)Lgame/boosting/Boostable; [Returned HUNTER 1]",
				"<init>: i(SII)I [MAX_EMPLOYED 1 10000]"));
		String c = "settlement/room/food/hunter/Constructor";
		String own = "L" + c + ";";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: " + c + "$3.<init>(" + own + "JS)V [Local Returned TABLE_COMBO]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned NICKNACK_1X1]",
				"<init>: " + c + "$5.<init>(" + own + "JSLsettlement/room/sprite/RoomSprite;)V [Local Returned STORAGE_1X1 New]"));
		reads.put(c + "$3", List.of("<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Local Local]"));
		reads.put(c + "$5", List.of(ONE_BY_ONE));
		return reads;
	}

	private static final List<String> ROOM_SERVICE_READS = List.of(
			"<init>: json(S)J [SERVICE]",
			"<init>: text(S)S [VERB]",
			"<init>: json(S)J [SERVICE]",
			"<init>: has(S)Z [RADIUS]",
			"<init>: i(SII)I [RADIUS 0 50000]");

	/** The classes behind a religious room: a farm's room classes without recipes, climate bonuses or experience, plus the service it offers. */
	private static Map<String, List<String>> serviceRoom() {
		Map<String, List<String>> reads = new LinkedHashMap<>(farm());
		reads.remove("settlement/room/food/farm/ROOM_FARM");
		reads.remove("settlement/room/food/farm/Constructor");
		reads.remove("settlement/room/industry/module/Industry");
		reads.remove("init/type/CLIMATES");
		reads.remove("settlement/room/main/BonusExperience$RoomExperienceBonus");
		reads.put("settlement/room/service/module/RoomService", ROOM_SERVICE_READS);
		String imp = "<init>: settlement/room/sprite/RoomSpriteImp.<init>(Linit/sprite/game/SheetType;JS)V [FieldRef Local Local]";
		reads.put("settlement/room/sprite/RoomSpriteBoxN", List.of(imp));
		reads.put(SPRITE_XXX, SPRITE_XXX_READS);
		reads.putAll(sprites());
		return reads;
	}

	/** The classes behind a Temple. TempleJob, TempleAltar, TempleInstance, Service and Gui never see the file. */
	static Map<String, List<String>> temple() {
		Map<String, List<String>> reads = serviceRoom();
		reads.put("settlement/room/spirit/temple/ROOM_TEMPLE", List.of(
				"<init>: util/keymap/RMAP.read(J)Lutil/keymap/MAPPED; [Returned]",
				"<init>: value(S)S [SACRIFICE_TYPE]",
				"<init>: util/keymap/RMAPS.read(SJ)Lutil/keymap/MAPPED; [SACRIFICE_RESOURCE Returned]",
				"<init>: value(S)S [SACRIFICE_TYPE]",
				"<init>: error(SLjava/lang/CharSequence;)V [Returned SACRIFICE_TYPE]",
				"<init>: d(SDD)D [SACRIFICE_TIME 0.0 10.0]"));
		String c = "settlement/room/spirit/temple/TempleConstructor";
		String own = "L" + c + ";";
		String oneN = "<init>: settlement/room/sprite/RoomSprite1xN.<init>(JSZ)V [Returned ";
		reads.put(c, List.of(
				"<init>: util/keymap/RMAP.read(SJ)Lutil/keymap/MAPPED; [FLOOR_PATH Returned]",
				"<init>: json(S)J [SPRITES]",
				"<init>: " + c + "$3.<init>(" + own + "JS)V [Local Returned PEDISTAL_BOX]",
				"<init>: " + c + "$4.<init>(" + own + "JSLsettlement/room/sprite/RoomSpriteBoxN;)V [Local Returned ALTAR_BOX New]",
				"<init>: " + c + "$5.<init>(" + own + "Lsettlement/room/sprite/RoomSprite;JLsettlement/room/spirit/temple/ROOM_TEMPLE;)V [Local New Returned Local]",
				"<init>: settlement/room/sprite/RoomSpriteXxX.<init>(JSI)V [Returned EMBLEM_2X2 2]",
				oneN + "NICHE_A_1X1 1]",
				oneN + "NICHE_B_1X1 0]",
				oneN + "NICHE_C_1X1 0]",
				oneN + "NICHE_D_1X1 0]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned TORCH_1X1]",
				oneN + "COFFIN_A_1X1 0]"));
		String box = "<init>: settlement/room/sprite/RoomSpriteBoxN.<init>(JS)V [Local Local]";
		reads.put(c + "$3", List.of(box));
		reads.put(c + "$4", List.of(box));
		reads.put(c + "$5", List.of("<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local EMBLEM_1X1]"));
		return reads;
	}

	/** The classes behind a Shrine. It has no employment of its own, and ShrineInstance and Service never see the file. */
	static Map<String, List<String>> shrine() {
		Map<String, List<String>> reads = serviceRoom();
		reads.put("settlement/room/spirit/shrine/ROOM_SHRINE", List.of("<init>: util/keymap/RMAP.read(J)Lutil/keymap/MAPPED; [Returned]"));
		String c = "settlement/room/spirit/shrine/Constructor";
		String own = "L" + c + ";";
		String sprite = "Lsettlement/room/sprite/RoomSprite;";
		String scribble = "<init>: " + c + "$SScribbleAltar.<init>(IJ" + sprite + ")V [";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: " + c + "$1.<init>(" + own + "JSJ)V [Local Returned ALTAR_FLOOR_TEXTURE Returned]",
				"<init>: " + c + "$SStairs.<init>(IJ)V [1 Returned]",
				"<init>: " + c + "$2.<init>(" + own + "JS" + sprite + sprite + ")V [Local Returned ALTAR_BOX New New]",
				"<init>: " + c + "$3.<init>(" + own + "IJJ)V [Local 1 Returned Returned]",
				scribble + "2 Returned New]",
				"<init>: " + c + "$4.<init>(" + own + "JS)V [Local Returned EMBLEM_1X1]",
				scribble + "1 Returned Returned]",
				scribble + "1 Returned Returned]",
				"<init>: " + c + "$5.<init>(" + own + "IJJ)V [Local 1 Returned Returned]",
				"<init>: " + c + "$6.<init>(" + own + "JSI" + sprite + ")V [Local Returned EMBLEM_2X2 2 Returned]",
				"<init>: " + c + "$SStairs.<init>(IJ)V [2 Returned]",
				scribble + "2 Returned New]"));
		String tex = "<init>: settlement/room/sprite/RoomSpriteTex.<init>(JS)V [Local ";
		String stairs = "<init>: " + c + "$SStairs.<init>(IJ)V [Local Local]";
		String torch = "<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local TORCH_1X1]";
		reads.put(c + "$1", List.of(tex + "Local]", tex + "ALTAR_FLOOR_SCRIBBLE1_TEXTURE]", tex + "ALTAR_FLOOR_SCRIBBLE2_TEXTURE]"));
		reads.put(c + "$2", List.of("<init>: settlement/room/sprite/RoomSpriteBoxN.<init>(JS)V [Local Local]"));
		reads.put(c + "$3", List.of(stairs, torch));
		reads.put(c + "$4", List.of(ONE_BY_ONE));
		reads.put(c + "$5", List.of(stairs, torch));
		reads.put(c + "$6", List.of("<init>: settlement/room/sprite/RoomSpriteXxX.<init>(JSI)V [Local Local Local]"));
		reads.put(c + "$SScribbleAltar", List.of(tex + "ALTAR_FLOOR_TEXTURE]", tex + "ALTAR_FLOOR_SCRIBBLE1_TEXTURE]", tex + "ALTAR_FLOOR_SCRIBBLE2_TEXTURE]"));
		reads.put(c + "$SStairs", List.of("<init>: settlement/room/sprite/RoomSpriteBoxN.<init>(JS)V [Local INNER_BOX]"));
		reads.put("settlement/room/sprite/RoomSpriteTex", List.of("<init>: settlement/room/sprite/RoomSpriteImp.<init>(Linit/sprite/game/SheetType;JS)V [FieldRef Local Local]"));
		return reads;
	}

	/**
	 * The classes behind any room with instances: a farm's room classes without recipes, climate bonuses or
	 * experience, and the sprites. Industry is left out because the rooms built on this never pass it the file.
	 */
	private static Map<String, List<String>> plainRoom() {
		Map<String, List<String>> reads = new LinkedHashMap<>(farm());
		reads.remove("settlement/room/food/farm/ROOM_FARM");
		reads.remove("settlement/room/food/farm/Constructor");
		reads.remove("settlement/room/industry/module/Industry");
		reads.remove("init/type/CLIMATES");
		reads.remove("settlement/room/main/BonusExperience$RoomExperienceBonus");
		reads.put("settlement/room/sprite/RoomSpriteCombo", List.of("<init>: settlement/room/sprite/RoomSpriteImp.<init>(Linit/sprite/game/SheetType;JS)V [FieldRef Local Local]"));
		reads.putAll(sprites());
		return reads;
	}

	/**
	 * The classes behind a room subjects get access to, such as a tavern: a farm's room classes without recipes,
	 * climate bonuses or experience, plus a RoomServiceAccess. That reads SERVICE.STANDING with the same class a
	 * race's STATS uses, and SERVICE.BOOST with the same class as a race's BOOST, so those lists are the race's.
	 */
	private static Map<String, List<String>> accessServiceRoom() {
		Map<String, List<String>> reads = plainRoom();
		reads.put("settlement/room/service/module/RoomService", ROOM_SERVICE_READS);
		reads.put("settlement/room/service/module/RoomServiceAccess", List.of(
				"<init>: json(S)J [SERVICE]",
				"<init>: texts(S)[S [MORE]",
				"<init>: json(S)J [SERVICE]",
				"<init>: json(S)J [STANDING]",
				"<init>: settlement/stats/standing/StatStanding$StandingDef.<init>(J)V [Returned]",
				"<init>: game/boosting/BoostSpecs.read(JLgame/boosting/BValue;)V [Returned FieldRef]",
				"<init>: dTry(SDDD)D [USAGE 0.0 1.0 1.0]"));
		Map<String, List<String>> race = RaceReads.all();
		for (String shared : List.of("game/boosting/BoostSpecs", "settlement/stats/standing/StatStanding$StandingDef", "settlement/stats/standing/StatStanding$StandingDef$StandingData")) {
			reads.put(shared, race.get(shared));
		}
		reads.put("settlement/room/sprite/RoomSpriteCombo", List.of("<init>: settlement/room/sprite/RoomSpriteImp.<init>(Linit/sprite/game/SheetType;JS)V [FieldRef Local Local]"));
		reads.putAll(sprites());
		return reads;
	}

	/** Never handed the file. Listed so the type turns off if it starts reading one. */
	private static final String DISTRIBUTION = "settlement/room/service/food/eatery/RoomDistribution";

	/**
	 * The classes behind a Tavern. The room class hands its file to nothing directly, and RoomDistribution,
	 * TavernInstance and the anonymous service and distribution classes never see it.
	 */
	static Map<String, List<String>> tavern() {
		Map<String, List<String>> reads = accessServiceRoom();
		String room = "settlement/room/service/food/tavern/ROOM_TAVERN";
		reads.put(room, List.of());
		reads.put(DISTRIBUTION, List.of());
		String c = "settlement/room/service/food/tavern/Constructor";
		String own = "L" + c + ";";
		String blue = "L" + room + ";";
		String sprite = "Lsettlement/room/sprite/RoomSprite;";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned JUG_1X1]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned JUG_FILL_1X1]",
				"<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Returned TABLE_COMBO]",
				"<init>: " + c + "$1.<init>(" + own + "JS" + blue + ")V [Local Returned STORAGE_1X1 Local]",
				"<init>: " + c + "$2.<init>(" + own + "JS)V [Local Returned CHAIR_1X1]",
				"<init>: " + c + "$3.<init>(" + own + "JS" + sprite + blue + ")V [Local Returned TABLE_DECOR_1X1 New Local]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned MISC_1X1]",
				"<init>: " + c + "$5.<init>(" + own + sprite + "J)V [Local New Returned]",
				"<init>: settlement/room/main/furnisher/FurnisherItemTools.makeUnder(Lsettlement/room/main/furnisher/Furnisher;JS)Lsettlement/room/main/furnisher/FurnisherItemTile; [Local Returned CARPET_COMBO]"));
		reads.put(c + "$1", List.of(ONE_BY_ONE));
		reads.put(c + "$2", List.of(ONE_BY_ONE));
		reads.put(c + "$3", List.of(ONE_BY_ONE));
		reads.put(c + "$5", List.of("<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local MISC_DECOR_1X1]"));
		reads.putAll(carpetUnder());
		return reads;
	}

	/** The carpet some rooms lay under themselves (FurnisherItemTools.java:103-121). Both overloads are in the class, so both are listed. */
	private static Map<String, List<String>> carpetUnder() {
		Map<String, List<String>> reads = new LinkedHashMap<>();
		String tools = "settlement/room/main/furnisher/FurnisherItemTools";
		String sprite = "Lsettlement/room/sprite/RoomSprite;";
		reads.put(tools, List.of(
				"makeUnder: " + tools + "$2.<init>(JS)V [Local Local]",
				"makeUnder: " + tools + "$3.<init>(JS" + sprite + ")V [Local Local Local]"));
		String combo = "<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Local Local]";
		reads.put(tools + "$2", List.of(combo));
		reads.put(tools + "$3", List.of(combo));
		return reads;
	}

	/** The classes behind an Eatery. RoomDistribution, EateryInstance and the anonymous service and distribution classes never see the file. */
	static Map<String, List<String>> eatery() {
		Map<String, List<String>> reads = accessServiceRoom();
		String room = "settlement/room/service/food/eatery/ROOM_EATERY";
		reads.put(room, List.of());
		reads.put(DISTRIBUTION, List.of());
		String c = "settlement/room/service/food/eatery/Constructor";
		String own = "L" + c + ";";
		String blue = "L" + room + ";";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: " + c + "$1.<init>(" + own + "JSJ" + blue + ")V [Local Returned CRATE_BOTTOM_A_1X1 Returned Local]",
				"<init>: " + c + "$2.<init>(" + own + "JSJ" + blue + ")V [Local Returned STALL_BOTTOM_1X1 Returned Local]",
				"<init>: " + c + "$3.<init>(" + own + "JSJ)V [Local Returned MISC_BOTTOM_1X1 Returned]",
				"<init>: " + c + "$4.<init>(" + own + "JS)V [Local Returned CARPET_COMBO]"));
		reads.put(c + "$1", List.of(ONE_BY_ONE, "<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local CRATE_TOP_1X1]"));
		reads.put(c + "$2", List.of(ONE_BY_ONE, "<init>: " + c + "$2$1.<init>(L" + c + "$2;JS)V [Local Local STALL_TOP_1X1]"));
		reads.put(c + "$2$1", List.of(ONE_BY_ONE));
		reads.put(c + "$3", List.of(ONE_BY_ONE, "<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local MISC_1X1]"));
		reads.put(c + "$4", List.of("<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Local Local]"));
		return reads;
	}

	/**
	 * The classes behind a Canteen. Its one recipe is read by Industry with no bonus (ROOM_CANTEEN.java:51).
	 * SWork, SService, SChair, CanteenInstance and Gui never see the file.
	 */
	static Map<String, List<String>> canteen() {
		Map<String, List<String>> reads = accessServiceRoom();
		reads.put("settlement/room/industry/module/Industry", farm().get("settlement/room/industry/module/Industry"));
		String room = "settlement/room/service/food/canteen/ROOM_CANTEEN";
		reads.put(room, List.of("<init>: settlement/room/industry/module/Industry.<init>(Lsettlement/room/main/RoomBlueprintImp;JLgame/boosting/Boostable;)V [Local Returned null]"));
		String c = "settlement/room/service/food/canteen/Constructor";
		String own = "L" + c + ";";
		String blue = "L" + room + ";";
		String sprite = "Lsettlement/room/sprite/RoomSprite;";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned PLATE_1X1]",
				"<init>: " + c + "$1.<init>(" + own + "JSJ" + blue + ")V [Local Returned TABLE_COMBO Returned Local]",
				"<init>: " + c + "$2.<init>(" + own + "JS" + blue + sprite + ")V [Local Returned TABLE_COMBO Local New]",
				"<init>: " + c + "$3.<init>(" + own + sprite + "J)V [Local New Returned]",
				"<init>: " + c + "$4.<init>(" + own + "JS)V [Local Returned STOOL_1X1]",
				"<init>: " + c + "$5.<init>(" + own + sprite + "J)V [Local New Returned]"));
		String combo = "<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Local Local]";
		String named = "<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local ";
		reads.put(c + "$1", List.of(combo, named + "OVEN_BENEATH_1X1]", named + "OVEN_BENEATH_USED_1X1]", "<init>: " + c + "$1$1.<init>(L" + c + "$1;JS)V [Local Local OVEN_1X1]"));
		reads.put(c + "$1$1", List.of(ONE_BY_ONE));
		reads.put(c + "$2", List.of(combo));
		reads.put(c + "$3", List.of(named + "TOOLS_1X1]"));
		reads.put(c + "$4", List.of(ONE_BY_ONE));
		reads.put(c + "$5", List.of(named + "DECOR_1X1]"));
		return reads;
	}

	/**
	 * The classes behind a room that satisfies a need, such as a well: an access room's, plus RoomServiceNeed,
	 * which reads SERVICE.NEED with NEEDS' own key (RoomServiceNeed.java:10, NEEDS.java:68, RMAP.java:30-46).
	 */
	private static Map<String, List<String>> needServiceRoom() {
		Map<String, List<String>> reads = accessServiceRoom();
		reads.put("settlement/room/service/module/RoomServiceNeed", List.of(
				"<init>: json(S)J [SERVICE]",
				"<init>: util/keymap/RMAP.read(J)Lutil/keymap/MAPPED; [Returned]"));
		return reads;
	}

	/** The classes behind a Well. Its room class, Wash and WellInstance never see the file. */
	static Map<String, List<String>> well() {
		Map<String, List<String>> reads = needServiceRoom();
		reads.put("settlement/room/service/hygine/well/ROOM_WELL", List.of());
		String c = "settlement/room/service/hygine/well/Constructor";
		String own = "L" + c + ";";
		String sprite = "Lsettlement/room/sprite/RoomSprite;";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Returned STONE_RING_STENCIL_COMBO]",
				"<init>: " + c + "$1.<init>(" + own + "JS)V [Local Returned ROOF_EDGE_1X1]",
				"<init>: " + c + "$2.<init>(" + own + "JS)V [Local Returned ROOF_MID_1X1]",
				"<init>: " + c + "$3.<init>(" + own + "JS)V [Local Returned FOUNTAIN_1X1]",
				"<init>: " + c + "$4.<init>(" + own + "JSLsettlement/room/service/hygine/well/ROOM_WELL;Lsettlement/room/sprite/RoomSpriteCombo;" + sprite + sprite + sprite
						+ ")V [Local Returned STONE_RING_COMBO Local New New New New]",
				"<init>: " + c + "$5.<init>(" + own + "JSLsettlement/room/service/hygine/well/ROOM_WELL;)V [Local Returned BUCKET_1X1 Local]"));
		reads.put(c + "$1", List.of(ONE_BY_ONE));
		reads.put(c + "$2", List.of(ONE_BY_ONE));
		reads.put(c + "$3", List.of(ONE_BY_ONE));
		reads.put(c + "$4", List.of("<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Local Local]"));
		reads.put(c + "$5", List.of(ONE_BY_ONE));
		return reads;
	}

	/**
	 * The classes behind a Bath. Its one recipe is read by Industry with no bonus (ROOM_BATH.java:61), and the
	 * pool's frame sprite reads the water's look as it's made (bath/Constructor.java:64-69). Bath, Oven, Crank,
	 * BathInstance and Gui never see the file, and the bench's tail is a copy of its head (:142).
	 */
	static Map<String, List<String>> bath() {
		Map<String, List<String>> reads = needServiceRoom();
		reads.put("settlement/room/industry/module/Industry", farm().get("settlement/room/industry/module/Industry"));
		String room = "settlement/room/service/hygine/bath/ROOM_BATH";
		String industry = "Lsettlement/room/main/RoomBlueprintImp;JLgame/boosting/Boostable;";
		reads.put(room, List.of(
				"<init>: text(S)S [HEATING]",
				"<init>: text(S)S [HEATING_DESC]",
				"<init>: text(S)S [HEATING_PROBLEM]",
				"<init>: text(S)S [WATER_PROBLEM]",
				"<init>: " + room + "$2.<init>(L" + room + ";" + industry + ")V [Local Local Returned null]"));
		reads.put(room + "$2", List.of("<init>: settlement/room/industry/module/Industry.<init>(" + industry + ")V [Local Local Local]"));
		String c = "settlement/room/service/hygine/bath/Constructor";
		String own = "L" + c + ";";
		String sprite = "Lsettlement/room/sprite/RoomSprite;";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: " + c + "$2.<init>(" + own + "JSJLsettlement/room/main/util/RoomInitData;L" + room + ";)V [Local Returned FRAME_COMBO Returned Local Local]",
				"<init>: " + c + "$3.<init>(" + own + "JSL" + room + ";)V [Local Returned WORK_1X1 Local]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned OVEN_1X1]",
				"<init>: " + c + "$4.<init>(" + own + "JS" + sprite + ")V [Local Returned ENTRANCE_1X1 New]",
				"<init>: " + c + "$5.<init>(" + own + "JS" + sprite + ")V [Local Returned PIPE_1X1 New]",
				"<init>: " + c + "$6.<init>(" + own + "JS)V [Local Returned BENCH_1X1]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned TORCH_1X1]"));
		reads.put(c + "$2", List.of(
				"<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Local Local]",
				"<init>: settlement/room/sprite/RoomSpriteTex.<init>(JS)V [Local POOL_FLOOR_TEXTURE]",
				"<init>: snake2d/util/color/ColorImp.<init>(JS)V [Returned WATER_COLOR]",
				"<init>: d(SDD)D [WATER_OPACITY 0.0 1.0]"));
		reads.put(c + "$3", List.of(ONE_BY_ONE));
		reads.put(c + "$4", List.of(ONE_BY_ONE));
		reads.put(c + "$5", List.of(ONE_BY_ONE));
		reads.put(c + "$6", List.of(ONE_BY_ONE));
		reads.put("settlement/room/sprite/RoomSpriteTex", List.of("<init>: settlement/room/sprite/RoomSpriteImp.<init>(Linit/sprite/game/SheetType;JS)V [FieldRef Local Local]"));
		return reads;
	}

	/** The classes behind a Lavatory. Its room class, Lavatory and LavatoryInstance never see the file, and two sprites are copies (Constructor.java:78,112,146). */
	static Map<String, List<String>> lavatory() {
		Map<String, List<String>> reads = needServiceRoom();
		String room = "settlement/room/service/lavatory/ROOM_LAVATORY";
		reads.put(room, List.of());
		String c = "settlement/room/service/lavatory/Constructor";
		String own = "L" + c + ";";
		String sprite = "Lsettlement/room/sprite/RoomSprite;";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned NICKNACK_1X1]",
				"<init>: " + c + "$1.<init>(" + own + "JSJL" + room + ";)V [Local Returned SIT_COMBO Returned Local]",
				"<init>: " + c + "$2.<init>(" + own + sprite + "J" + sprite + ")V [Local New Returned New]",
				"<init>: " + c + "$4.<init>(" + own + "JSJ)V [Local Returned TABLE_COMBO Returned]"));
		String named = "<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local ";
		reads.put(c + "$1", List.of(
				"<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Local Local]",
				"<init>: " + c + "$1$1.<init>(L" + c + "$1;JS)V [Local Local SHITHOLE_1X1]",
				named + "SHIT_LID_1X1]",
				named + "SHIT_1X1]"));
		reads.put(c + "$1$1", List.of(ONE_BY_ONE));
		reads.put(c + "$2", List.of("<init>: " + c + "$2$1.<init>(L" + c + "$2;JS)V [Local Local SIT_ONTOP_COMBO]"));
		reads.put(c + "$2$1", List.of("<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Local Local]"));
		reads.put(c + "$4", List.of(
				"<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Local Local]",
				named + "BASIN_WATER_1X1]",
				"<init>: " + c + "$4$1.<init>(L" + c + "$4;JS)V [Local Local BASIN_1X1]"));
		reads.put(c + "$4$1", List.of(ONE_BY_ONE));
		return reads;
	}

	/** The classes behind a Barber. Tile and Instance never see the file, and the centre table is a copy (Constructor.java:77). */
	static Map<String, List<String>> barber() {
		Map<String, List<String>> reads = needServiceRoom();
		String room = "settlement/room/service/barber/ROOM_BARBER";
		reads.put(room, List.of("<init>: d(S)D [WORK_TIME_IN_DAYS]"));
		String c = "settlement/room/service/barber/Constructor";
		String own = "L" + c + ";";
		String sprite = "Lsettlement/room/sprite/RoomSprite;";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned NICKNACK_1X1]",
				"<init>: " + c + "$1.<init>(" + own + "JS)V [Local Returned TABLE_CENTRE_1X1]",
				"<init>: " + c + "$2.<init>(" + own + "JS)V [Local Returned TABLE_CENTRE_TOP_1X1]",
				"<init>: " + c + "$3.<init>(" + own + "JS" + sprite + ")V [Local Returned TABLE_COMBO New]",
				"<init>: " + c + "$5.<init>(" + own + "JS" + sprite + "L" + room + ";)V [Local Returned CHAIR_1X1 New Local]",
				"<init>: " + c + "$6.<init>(" + own + "JS" + sprite + ")V [Local Returned SEPARATOR_1X1 New]",
				"<init>: settlement/room/main/furnisher/FurnisherItemTools.makeUnder(Lsettlement/room/main/furnisher/Furnisher;JS)Lsettlement/room/main/furnisher/FurnisherItemTile; [Local Returned CARPET_COMBO]"));
		reads.put(c + "$1", List.of(ONE_BY_ONE));
		reads.put(c + "$2", List.of(ONE_BY_ONE));
		reads.put(c + "$3", List.of("<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Local Local]"));
		reads.put(c + "$5", List.of(ONE_BY_ONE));
		reads.put(c + "$6", List.of(ONE_BY_ONE));
		reads.putAll(carpetUnder());
		return reads;
	}

	/** The classes behind a Physician. Its room class, Service, Instance and Gui never see the file. */
	static Map<String, List<String>> physician() {
		Map<String, List<String>> reads = needServiceRoom();
		reads.put("settlement/room/health/physician/ROOM_PHYSICIAN", List.of());
		String c = "settlement/room/health/physician/Constructor";
		String own = "L" + c + ";";
		String sprite = "Lsettlement/room/sprite/RoomSprite;";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: " + c + "$1.<init>(" + own + "JSJ)V [Local Returned SHELF_1X1 Returned]",
				"<init>: settlement/room/sprite/RoomSprite1xN.<init>(JSZ)V [Returned BUNK_1X1_TOP 0]",
				"<init>: settlement/room/sprite/RoomSprite1xN.<init>(JSZ)V [Returned BUNK_1X1_BOTTOM 1]",
				"<init>: " + c + "$2.<init>(" + own + "JS)V [Local Returned TABLE_ONTOP_1X1]",
				"<init>: " + c + "$3.<init>(" + own + "JSLsettlement/room/sprite/RoomSprite1x1;)V [Local Returned TABLE_COMBO New]",
				"<init>: " + c + "$4.<init>(" + own + "JS" + sprite + ")V [Local Returned STORAGE_1X1 New]"));
		reads.put(c + "$1", List.of(ONE_BY_ONE, "<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local SHELF_ONTOP_1X1]"));
		reads.put(c + "$2", List.of(ONE_BY_ONE));
		reads.put(c + "$3", List.of("<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Local Local]"));
		reads.put(c + "$4", List.of(ONE_BY_ONE));
		return reads;
	}

	/**
	 * The classes behind a Pleasure room (massage parlour). FLOOR2 is looked up in the floors with the file
	 * passed for the error (pleasure/Constructor.java:60, RMAP.java:69-83). Its room class, ABed and
	 * PleasureInstance never see the file.
	 */
	static Map<String, List<String>> pleasure() {
		Map<String, List<String>> reads = needServiceRoom();
		reads.put("settlement/room/service/pleasure/ROOM_PLEASURE", List.of());
		String c = "settlement/room/service/pleasure/Constructor";
		String own = "L" + c + ";";
		String sprite = "Lsettlement/room/sprite/RoomSprite;";
		reads.put(c, List.of(
				"<init>: value(S)S [FLOOR2]",
				"<init>: util/keymap/RMAP.get(SJ)Lutil/keymap/MAPPED; [Returned Returned]",
				"<init>: json(S)J [SPRITES]",
				"<init>: snake2d/util/color/ColorImp.<init>(JS)V [Returned COLOR_PIXEL_BASE]",
				"<init>: settlement/room/sprite/RoomSprite1xN.<init>(JSZ)V [Returned BED_HEAD_1X1 0]",
				"<init>: settlement/room/sprite/RoomSprite1xN.<init>(JSZ)V [Returned BED_TAIL_1X1 1]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned TABLE_TOP_1X1]",
				"<init>: " + c + "$1.<init>(" + own + "JS" + sprite + ")V [Local Returned TABLE_COMBO New]",
				"<init>: " + c + "$3.<init>(" + own + "JSJ" + sprite + ")V [Local Returned SHELF_1X1 Returned New]",
				"<init>: " + c + "$4.<init>(" + own + "JS" + sprite + ")V [Local Returned NICKNACK_1X1 New]",
				"<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Returned WALLS_COMBO]",
				"<init>: settlement/room/main/furnisher/FurnisherItemTools.makeUnder(Lsettlement/room/main/furnisher/Furnisher;JS)Lsettlement/room/main/furnisher/FurnisherItemTile; [Local Returned CARPET_COMBO]"));
		reads.put(c + "$1", List.of("<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Local Local]"));
		reads.put(c + "$3", List.of(ONE_BY_ONE, "<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local SHELF_TOP_1X1]"));
		reads.put(c + "$4", List.of(ONE_BY_ONE));
		reads.putAll(carpetUnder());
		return reads;
	}

	/**
	 * The classes behind the Court, a need room built from the game's one _COURT file (court/ROOM_COURT.java:33).
	 * The room class, CourtInstance and Service never see the file.
	 */
	static Map<String, List<String>> court() {
		Map<String, List<String>> reads = needServiceRoom();
		reads.put("settlement/room/law/court/ROOM_COURT", List.of());
		String c = "settlement/room/law/court/Constructor";
		String own = "L" + c + ";";
		String combo = "<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Local Local]";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: " + c + ".station(J)V [Returned]",
				"<init>: " + c + ".bench(J)V [Returned]",
				"station: " + c + "$1.<init>(" + own + "JS)V [Local Local TABLE_COMBO]",
				"station: " + c + "$2.<init>(" + own + "JS)V [Local Local CARPET_COMBO]",
				"station: " + c + "$3.<init>(" + own + "JSJ)V [Local Local TORCH_1X1 Local]",
				"station: " + c + "$4.<init>(" + own + "JS)V [Local Local STAND_COMBO]",
				"station: " + c + "$5.<init>(" + own + "JSLsettlement/room/sprite/RoomSprite;)V [Local Local CHAIR_1X1 New]",
				"station: " + c + "$6.<init>(" + own + "JS)V [Local Local DECOR_A_1X1]",
				"station: " + c + "$7.<init>(" + own + "JS)V [Local Local DECOR_B_1X1]",
				"bench: settlement/room/sprite/RoomSprite1xN.<init>(JSZ)V [Local BENCH_A_1X1 1]",
				"bench: settlement/room/sprite/RoomSprite1xN.<init>(JSZ)V [Local BENCH_B_1X1 0]",
				"bench: settlement/room/sprite/RoomSprite1xN.<init>(JSZ)V [Local BENCH_C_1X1 0]",
				"bench: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local TORCH_1X1]"));
		reads.put(c + "$1", List.of(combo));
		reads.put(c + "$2", List.of(combo));
		reads.put(c + "$3", List.of(ONE_BY_ONE, "<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local GEM_1X1]"));
		reads.put(c + "$4", List.of(combo));
		reads.put(c + "$5", List.of(ONE_BY_ONE));
		reads.put(c + "$6", List.of(ONE_BY_ONE));
		reads.put(c + "$7", List.of(ONE_BY_ONE));
		return reads;
	}

	/**
	 * The classes behind the Prison, built from the game's one _PRISON file (prison/ROOM_PRISON.java:34). Its Industry
	 * is made from the edible resources, not the file (:37-38, Industry.java:82-88). FLOOR2 is read with value() and
	 * looked up with the file passed for the error, as a pleasure room's is.
	 */
	static Map<String, List<String>> prison() {
		Map<String, List<String>> reads = plainRoom();
		reads.put("settlement/room/law/prison/ROOM_PRISON", List.of());
		String c = "settlement/room/law/prison/Constructor";
		String own = "L" + c + ";";
		reads.put(c, List.of(
				"<init>: value(S)S [FLOOR2]",
				"<init>: util/keymap/RMAP.get(SJ)Lutil/keymap/MAPPED; [Returned Returned]",
				"<init>: json(S)J [SPRITES]",
				"<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Returned WALLS_COMBO]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned BARS_1X1]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned LATRINE_EMPTY_1X1]",
				"<init>: " + c + "$1.<init>(" + own + "Lsettlement/room/sprite/RoomSpriteCombo;Lsettlement/room/sprite/RoomSprite1x1;Lsettlement/room/sprite/RoomSprite;J)V [Local New New New Returned]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned FOOD_1X1]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned MISC_1X1]",
				"<init>: " + c + "$3.<init>(" + own + "JS)V [Local Returned OPENING_1X1]",
				"<init>: " + c + "$5.<init>(" + own + "JS)V [Local Returned CANDLE_SHELF_1X1]"));
		reads.put(c + "$1", List.of("<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local LATRINE_FULL_1X1]"));
		reads.put(c + "$3", List.of(ONE_BY_ONE));
		reads.put(c + "$5", List.of(ONE_BY_ONE));
		return reads;
	}

	/** The classes behind the Stockade, built from the game's one _STOCKADE file (stockade/ROOM_STOCKADE.java:35). Job never sees the file. */
	static Map<String, List<String>> stockade() {
		Map<String, List<String>> reads = plainRoom();
		String room = "settlement/room/law/stockade/ROOM_STOCKADE";
		reads.put(room, List.of());
		String c = "settlement/room/law/stockade/Constructor";
		String own = "L" + c + ";";
		String blue = "L" + room + ";";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned WALL_1X1]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned WALL_CORNER_1X1]",
				"<init>: " + c + "$3.<init>(" + own + "JS)V [Local Returned OPENING_1X1]",
				"<init>: " + c + "$5.<init>(" + own + "JS" + blue + ")V [Local Returned FOOD_1X1 Local]",
				"<init>: " + c + "$6.<init>(" + own + "JSJ" + blue + ")V [Local Returned LATRINE_EMPTY_1X1 Returned Local]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned MISC_1X1]"));
		reads.put(c + "$3", List.of(ONE_BY_ONE));
		reads.put(c + "$5", List.of(ONE_BY_ONE));
		reads.put(c + "$6", List.of(ONE_BY_ONE, "<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local LATRINE_FULL_1X1]"));
		return reads;
	}

	/**
	 * The classes behind the Guard, built from the game's one _GUARD file (guard/ROOM_GUARD.java:50). Its employment is
	 * EMPLOYMENT, which RoomBlueprintIns reads when there's no WORK (RoomBlueprintIns.java:66-71). The room class and its
	 * patrols, service and reporter never see the file.
	 */
	static Map<String, List<String>> guard() {
		Map<String, List<String>> reads = plainRoom();
		reads.put("settlement/room/law/guard/ROOM_GUARD", List.of());
		String c = "settlement/room/law/guard/Constructor";
		String own = "L" + c + ";";
		String combo = "<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Local Local]";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: " + c + "$2.<init>(" + own + "JS)V [Local Returned FLOOR_BOX]",
				"<init>: " + c + "$3.<init>(" + own + "JS)V [Local Returned CARPET_COMBO]",
				"<init>: " + c + "$6.<init>(" + own + "Lsettlement/room/sprite/RoomSprite;J)V [Local New Returned]",
				"<init>: " + c + "$7.<init>(" + own + "Lsettlement/room/sprite/RoomSprite;J)V [Local New Returned]"));
		reads.put(c + "$2", List.of("<init>: settlement/room/sprite/RoomSpriteBoxN.<init>(JS)V [Local Local]"));
		reads.put(c + "$3", List.of(combo));
		reads.put(c + "$6", List.of(
				"<init>: " + c + "$6$1.<init>(L" + c + "$6;JS)V [Local Local TORCH_BOTTOM_COMBO]",
				"<init>: " + c + "$6$2.<init>(L" + c + "$6;JS)V [Local Local TORCH_COMBO]"));
		reads.put(c + "$6$1", List.of(combo));
		reads.put(c + "$6$2", List.of(combo));
		reads.put(c + "$7", List.of("<init>: " + c + "$7$1.<init>(L" + c + "$7;JS)V [Local Local FENCE_COMBO]"));
		reads.put(c + "$7$1", List.of(combo));
		reads.put("settlement/room/sprite/RoomSpriteBoxN", List.of("<init>: settlement/room/sprite/RoomSpriteImp.<init>(Linit/sprite/game/SheetType;JS)V [FieldRef Local Local]"));
		return reads;
	}

	/**
	 * The classes behind the Police, built from the game's one _POLICE file (police/ROOM_POLICE.java:48). The room class
	 * reads a BOOST at the top of the file with the same class as a race's BOOST (:131-132), so that list is the race's.
	 */
	static Map<String, List<String>> police() {
		Map<String, List<String>> reads = plainRoom();
		reads.put("settlement/room/law/police/ROOM_POLICE", List.of("<init>: game/boosting/BoostSpecs.read(JLgame/boosting/BValue;)V [Returned New]"));
		reads.put("game/boosting/BoostSpecs", RaceReads.all().get("game/boosting/BoostSpecs"));
		String c = "settlement/room/law/police/PoliceConstructor";
		String own = "L" + c + ";";
		String sprite = "Lsettlement/room/sprite/RoomSprite;";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: " + c + "$1.<init>(" + own + "JS)V [Local Returned BED_STRAP_1X1]",
				"<init>: " + c + "$2.<init>(" + own + "JSZ" + sprite + ")V [Local Returned BED_1X1A 1 New]",
				"<init>: " + c + "$3.<init>(" + own + "JSZ" + sprite + ")V [Local Returned BED_1X1A 0 New]",
				"<init>: " + c + "$4.<init>(" + own + "JS" + sprite + ")V [Local Returned CHAIR_1X1 New]",
				"<init>: " + c + "$5.<init>(" + own + "JSJ)V [Local Returned CAGE_A_1X1 Returned]",
				"<init>: " + c + "$6.<init>(" + own + "JS)V [Local Returned CAGE_B_1X1]",
				"<init>: " + c + "$7.<init>(" + own + "JS)V [Local Returned CAGE_LATCH_1X1]",
				"<init>: " + c + "$8.<init>(" + own + "JSJ)V [Local Returned TABLE_COMBO Returned]"));
		String oneN = "<init>: settlement/room/sprite/RoomSprite1xN.<init>(JSZ)V [Local Local Local]";
		reads.put(c + "$1", List.of(ONE_BY_ONE));
		reads.put(c + "$2", List.of(oneN));
		reads.put(c + "$3", List.of(oneN));
		reads.put(c + "$4", List.of(ONE_BY_ONE));
		reads.put(c + "$5", List.of(ONE_BY_ONE, "<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local CAGE_A_TOP_1X1]"));
		reads.put(c + "$6", List.of(ONE_BY_ONE));
		reads.put(c + "$7", List.of(ONE_BY_ONE));
		reads.put(c + "$8", List.of("<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Local Local]", "<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local NICKNACK_1X1]"));
		return reads;
	}

	/**
	 * The classes behind the Stocks, a need room built from the game's one _STOCKS file (stocks/ROOM_STOCKS.java:42). The
	 * room class, Tile and Instance never see the file.
	 */
	static Map<String, List<String>> stocks() {
		Map<String, List<String>> reads = needServiceRoom();
		String room = "settlement/room/law/stocks/ROOM_STOCKS";
		reads.put(room, List.of());
		String c = "settlement/room/law/stocks/MConstructor";
		String own = "L" + c + ";";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: " + c + "$1.<init>(" + own + "JS)V [Local Returned BOX]",
				"<init>: " + c + "$2.<init>(" + own + "Lsettlement/room/sprite/RoomSprite;JL" + room + ";)V [Local New Returned Local]"));
		reads.put(c + "$1", List.of("<init>: settlement/room/sprite/RoomSpriteBoxN.<init>(JS)V [Local Local]"));
		reads.put(c + "$2", List.of(
				"<init>: " + c + "$2$1.<init>(L" + c + "$2;JS)V [Local Local STOCK_BELOW_1X1]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local STOCK_TOP_1X1]"));
		reads.put(c + "$2$1", List.of(ONE_BY_ONE));
		reads.put("settlement/room/sprite/RoomSpriteBoxN", List.of("<init>: settlement/room/sprite/RoomSpriteImp.<init>(Linit/sprite/game/SheetType;JS)V [FieldRef Local Local]"));
		return reads;
	}

	/**
	 * The classes behind the Execution room (scaffolds), built from the game's one _EXECUTION file
	 * (execution/ROOM_EXECTUTION.java:33). It's a RoomBlueprintImp, so it has no employment, and nothing in it reads a
	 * SERVICE. The room class, ExecutionSingle and ExecutionStation never see the file.
	 */
	static Map<String, List<String>> execution() {
		Map<String, List<String>> reads = plainRoom();
		reads.remove("settlement/room/main/RoomBlueprintIns");
		reads.remove("settlement/room/main/employment/RoomEmploymentSimple");
		reads.put("settlement/room/law/execution/ROOM_EXECTUTION", List.of());
		String c = "settlement/room/law/execution/Constructor";
		String own = "L" + c + ";";
		String sprite = "Lsettlement/room/sprite/RoomSprite;";
		String p = c + "$PSprite.<init>(JS)V [Local ";
		String r = c + "$RSprite.<init>(JS)V [Local ";
		String pedestal = c + "$Pedistal.<init>(J" + sprite + ")V [Local New]";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: " + c + "$Pedistall.<init>(J)V [Returned]",
				"<init>: " + c + ".gallows(J)V [Returned]",
				"<init>: " + c + ".chop(J)V [Returned]",
				"<init>: " + c + ".gibbet(J)V [Returned]",
				"<init>: " + c + ".cross(J)V [Returned]",
				"gallows: " + p + "GALLOW_BEAM_LEFT_1X1]",
				"gallows: " + p + "GALLOW_BEAM_CENTRE_1X1]",
				"gallows: " + p + "GALLOW_BOTTOM_1X1]",
				"gallows: " + p + "GALLOW_PILLAR_1X1]",
				"gallows: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local GALLOW_BOX_1X1]",
				"gallows: " + c + "$1.<init>(" + own + "JS)V [Local Local GALLOW_NOOSE_1X1]",
				"gallows: " + c + "$2.<init>(" + own + "J" + sprite + sprite + sprite + ")V [Local Local New New New]",
				"gallows: " + c + "$3.<init>(" + own + "J" + sprite + sprite + sprite + sprite + ")V [Local Local Returned New New New]",
				"chop: " + r + "CHOP_TABLE_1X1]",
				"chop: " + p + "CHOP_MAT_1X1]",
				"chop: " + pedestal,
				"chop: " + pedestal,
				"gibbet: " + c + "$4.<init>(" + own + "JS)V [Local Local GIBBET_CAGE_1X1]",
				"gibbet: " + c + "$5.<init>(" + own + "JS)V [Local Local GIBBET_DOOR_1X1]",
				"gibbet: " + pedestal,
				"gibbet: " + pedestal,
				"cross: " + r + "CROSS_R_1X1]",
				"cross: " + pedestal,
				"cross: " + r + "CROSS_L_1X1]",
				"cross: " + pedestal,
				"cross: " + r + "CROSS_MID_1X1]",
				"cross: " + pedestal,
				"cross: " + p + "CROSS_POLE_1X1]",
				"cross: " + r + "CROSS_C_1X1]",
				"cross: " + c + "$6.<init>(" + own + "J" + sprite + sprite + ")V [Local Local New New]"));
		reads.put(c + "$1", List.of("<init>: " + c + "$PSprite.<init>(JS)V [Local Local]"));
		reads.put(c + "$2", List.of("<init>: " + c + "$Pedistall.<init>(J)V [Local]"));
		reads.put(c + "$3", List.of("<init>: " + c + "$Pedistall.<init>(J)V [Local]"));
		reads.put(c + "$4", List.of("<init>: " + c + "$RSprite.<init>(JS)V [Local Local]"));
		reads.put(c + "$5", List.of("<init>: " + c + "$PSprite.<init>(JS)V [Local Local]"));
		reads.put(c + "$6", List.of("<init>: " + c + "$Pedistal.<init>(J" + sprite + ")V [Local Local]"));
		reads.put(c + "$PSprite", List.of(ONE_BY_ONE));
		reads.put(c + "$RSprite", List.of(ONE_BY_ONE));
		reads.put(c + "$Pedistal", List.of("<init>: settlement/room/sprite/RoomSpriteBoxN.<init>(JS)V [Local PODIUM_BOX]"));
		reads.put(c + "$Pedistall", List.of("<init>: settlement/room/sprite/RoomSpriteBoxN.<init>(JS)V [Local PODIUM_BOX]"));
		reads.put("settlement/room/sprite/RoomSpriteBoxN", List.of("<init>: settlement/room/sprite/RoomSpriteImp.<init>(Linit/sprite/game/SheetType;JS)V [FieldRef Local Local]"));
		return reads;
	}

	/**
	 * The classes behind a Market. It's an access room with the eatery's RoomDistribution, and its need is set in
	 * the code (ROOM_MARKET.java:49). The room class, RoomDistribution and MarketInstance never see the file.
	 */
	static Map<String, List<String>> market() {
		Map<String, List<String>> reads = accessServiceRoom();
		String room = "settlement/room/service/market/ROOM_MARKET";
		reads.put(room, List.of());
		reads.put(DISTRIBUTION, List.of());
		String c = "settlement/room/service/market/Constructor";
		String own = "L" + c + ";";
		String blue = "L" + room + ";";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: " + c + "$1.<init>(" + own + "JS" + blue + ")V [Local Returned CRATE_1X1 Local]",
				"<init>: " + c + "$2.<init>(" + own + "JSJ" + blue + ")V [Local Returned STALL_BOTTOM_COMBO Returned Local]",
				"<init>: " + c + "$3.<init>(" + own + "JSJ)V [Local Returned MISC_BOTTOM_1X1 Returned]",
				"<init>: " + c + "$4.<init>(" + own + "JS)V [Local Returned CARPET_COMBO]"));
		reads.put(c + "$1", List.of(ONE_BY_ONE));
		reads.put(c + "$2", List.of("<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Local Local]", "<init>: " + c + "$2$1.<init>(L" + c + "$2;JS)V [Local Local STALL_TOP_1X1]"));
		reads.put(c + "$2$1", List.of(ONE_BY_ONE));
		reads.put(c + "$3", List.of(ONE_BY_ONE, "<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local MISC_1X1]"));
		reads.put(c + "$4", List.of("<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Local Local]"));
		return reads;
	}

	/**
	 * The classes behind a room with spectators, such as a stage: a need room's, plus the box sprite. The room's
	 * ROOM_SPECTATOR, which steers the crowd, never sees the file.
	 */
	private static Map<String, List<String>> spectatorRoom(String room) {
		Map<String, List<String>> reads = needServiceRoom();
		reads.put(room, List.of());
		reads.put("settlement/room/service/module/ROOM_SPECTATOR", List.of());
		reads.put("settlement/room/sprite/RoomSpriteBoxN", List.of("<init>: settlement/room/sprite/RoomSpriteImp.<init>(Linit/sprite/game/SheetType;JS)V [FieldRef Local Local]"));
		return reads;
	}

	/** The classes behind a Stage. Its room class, Centre and StageInstance never see the file. */
	static Map<String, List<String>> stage() {
		Map<String, List<String>> reads = spectatorRoom("settlement/room/service/stage/ROOM_STAGE");
		String c = "settlement/room/service/stage/StageConstructor";
		String own = "L" + c + ";";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: " + c + "$1.<init>(" + own + "JS)V [Local Returned A_BOX]",
				"<init>: " + c + "$2.<init>(" + own + "JS)V [Local Returned B_BOX]",
				"<init>: " + c + "$3.<init>(" + own + "JS)V [Local Returned C_BOX]"));
		String box = "<init>: settlement/room/sprite/RoomSpriteBoxN.<init>(JS)V [Local Local]";
		reads.put(c + "$1", List.of(box));
		reads.put(c + "$2", List.of(box));
		reads.put(c + "$3", List.of(box));
		return reads;
	}

	/** The classes behind a Speaker. Its room class, Centre and SpeakerInstance never see the file. */
	static Map<String, List<String>> speaker() {
		Map<String, List<String>> reads = spectatorRoom("settlement/room/service/speaker/ROOM_SPEAKER");
		String c = "settlement/room/service/speaker/SpeakerConstructor";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Returned CENTER_COMBO]",
				"<init>: " + c + "$1.<init>(L" + c + ";JSLsettlement/room/sprite/RoomSprite;)V [Local Returned FRAME_COMBO New]"));
		reads.put(c + "$1", List.of("<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Local Local]"));
		return reads;
	}

	/** The classes behind a Fight Pit. Its room class, Service and ArenaInstance never see the file. */
	static Map<String, List<String>> fightPit() {
		Map<String, List<String>> reads = spectatorRoom("settlement/room/service/arena/pit/ROOM_FIGHTPIT");
		String c = "settlement/room/service/arena/pit/ArenaConstructor";
		String own = "L" + c + ";";
		String stairs = "<init>: " + c + "$SStairs.<init>(" + own + "JS)V [Local Returned ";
		reads.put(c, List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: " + c + "$1.<init>(" + own + "JS)V [Local Returned SEAT_BOX]",
				"<init>: " + c + "$2.<init>(" + own + "JSLsettlement/room/sprite/RoomSprite;)V [Local Returned WALL_1X1 New]",
				"<init>: " + c + "$3.<init>(" + own + "JS)V [Local Returned RIM_BOX]",
				"<init>: " + c + "$4.<init>(" + own + "JS)V [Local Returned TOWER_1X1]",
				"<init>: " + c + "$5.<init>(" + own + "JSJ)V [Local Returned TORCH_1X1 Returned]",
				stairs + "STAIRS_LEFT_1X1]",
				stairs + "STAIRS_CENTRE_1X1]",
				stairs + "STAIRS_RIGHT_1X1]"));
		arenaParts(reads, c);
		return reads;
	}

	/**
	 * The classes behind a Grand Arena. ArenaConstructor hands SPRITES to CUtil, which reads the parts a fight pit
	 * reads itself. Its room class, Service, ArenaInstance and the two stats' anonymous classes never see the file.
	 */
	static Map<String, List<String>> grandArena() {
		Map<String, List<String>> reads = spectatorRoom("settlement/room/service/arena/grand/ROOM_ARENA");
		String pack = "settlement/room/service/arena/grand/";
		reads.put(pack + "ArenaConstructor", List.of(
				"<init>: json(S)J [SPRITES]",
				"<init>: " + pack + "CUtil.<init>(L" + pack + "ArenaConstructor;J)V [Local Returned]",
				"<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Returned CONSTRUCT_1X1]"));
		String c = pack + "CUtil";
		String own = "L" + c + ";";
		String stairs = "<init>: " + c + "$SStairs.<init>(" + own + "JS)V [Local Local ";
		reads.put(c, List.of(
				"<init>: " + c + "$1.<init>(" + own + "JS)V [Local Local SEAT_BOX]",
				"<init>: " + c + "$2.<init>(" + own + "JSLsettlement/room/sprite/RoomSprite;)V [Local Local WALL_1X1 New]",
				"<init>: " + c + "$3.<init>(" + own + "JS)V [Local Local RIM_BOX]",
				"<init>: " + c + "$4.<init>(" + own + "JS)V [Local Local TOWER_1X1]",
				"<init>: " + c + "$5.<init>(" + own + "JSJ)V [Local Local TORCH_1X1 Local]",
				stairs + "STAIRS_SINGLE_1X1]",
				stairs + "STAIRS_LEFT_1X1]",
				stairs + "STAIRS_RIGHT_1X1]",
				stairs + "STAIRS_CENTRE_1X1]"));
		arenaParts(reads, c);
		return reads;
	}

	/** The seat, wall, rim, tower, torch and stair sprites both arenas build in the same anonymous classes. */
	private static void arenaParts(Map<String, List<String>> reads, String c) {
		String box = "<init>: settlement/room/sprite/RoomSpriteBoxN.<init>(JS)V [Local Local]";
		reads.put(c + "$1", List.of(box));
		reads.put(c + "$2", List.of(ONE_BY_ONE));
		reads.put(c + "$3", List.of(box));
		reads.put(c + "$4", List.of(ONE_BY_ONE));
		reads.put(c + "$5", List.of(ONE_BY_ONE, "<init>: " + c + "$5$1.<init>(L" + c + "$5;JS)V [Local Local TORCH_TOP_1X1]"));
		reads.put(c + "$5$1", List.of(ONE_BY_ONE));
		reads.put(c + "$SStairs", List.of(ONE_BY_ONE));
	}

	/** How a room's sprite entries are read, whichever room they're in. */
	/**
	 * The classes behind a monument that isn't a torch (ROOM_MONUMENTS.java:26-32). It's a RoomBlueprintImp with no
	 * employment, recipes, climate bonus or stats, so those farm classes never see the file. The anonymous classes of
	 * ROOM_MONUMENT and Imp itself don't either. Torch and its classes read a torch's file, which this type refuses.
	 * STANDING is read with the class a race's STATS uses, and FULFILLMENT_BONUS with the class behind a race's BOOST.
	 */
	static Map<String, List<String>> monument() {
		Map<String, List<String>> reads = new LinkedHashMap<>(farm());
		for (String gone : List.of("settlement/room/food/farm/ROOM_FARM", "settlement/room/food/farm/Constructor", "settlement/room/industry/module/Industry", "init/type/CLIMATES",
				"settlement/room/main/BonusExperience$RoomExperienceBonus", "settlement/room/main/RoomBlueprintIns", "settlement/room/main/employment/RoomEmploymentSimple",
				"settlement/room/main/furnisher/FurnisherStat")) {
			reads.remove(gone);
		}
		String m = "settlement/room/infra/monument/";
		reads.put(m + "ROOM_MONUMENTS", List.of());
		reads.put(m + "ROOM_MONUMENTS$1", List.of(
				"create: has(S)Z [TYPE]",
				"create: value(S)S [TYPE]"));
		reads.put(m + "ROOM_MONUMENT", List.of(
				"<init>: settlement/stats/standing/StatStanding$StandingDef.<init>(J)V [Returned]",
				"<init>: has(S)Z [STANDING_UPGRADE]",
				"<init>: json(S)J [STANDING_UPGRADE]",
				"<init>: settlement/stats/standing/StatStanding$StandingDef.<init>(J)V [Returned]",
				"<init>: init/race/bio/Opinion.read(J)V [Returned]",
				"<init>: i(S)I [MAX_VALUE]",
				"<init>: bool(SZ)Z [SOLID 1]",
				"<init>: game/boosting/BoostSpecs.read(SJLgame/boosting/BValue;)V [FULFILLMENT_BONUS Returned New]"));
		reads.put(m + "ROOM_MONUMENT$Instance", List.of());
		reads.put(m + "MConstructor", List.of("<init>: jsons(S)[J [ITEMS]"));
		reads.put(m + "Imp", List.of());
		String c = m + "Imp$Constructor";
		String own = "L" + c + ";";
		String sprite = "Lsettlement/room/sprite/RoomSprite;";
		reads.put(c, List.of(
				"<init>: jsons(S)[J [SPRITES]",
				"<init>: " + c + "$1.<init>(" + own + "JS)V [Local Unknown FLOOR_COMBO]",
				"<init>: has(S)Z [1x1]",
				"<init>: " + c + "$2.<init>(" + own + "JS" + sprite + ")V [Local Unknown 1x1 New]",
				"<init>: has(S)Z [2x2]",
				"<init>: " + c + "$3.<init>(" + own + "JSI" + sprite + ")V [Local Unknown 2x2 2 New]",
				"<init>: has(S)Z [3x3]",
				"<init>: " + c + "$4.<init>(" + own + "JSI" + sprite + ")V [Local Unknown 3x3 3 New]"));
		reads.put(c + "$1", List.of("<init>: settlement/room/sprite/RoomSpriteCombo.<init>(JS)V [Local Local]"));
		reads.put(c + "$2", List.of(ONE_BY_ONE));
		reads.put(c + "$3", List.of("<init>: settlement/room/sprite/RoomSpriteXxX.<init>(JSI)V [Local Local Local]"));
		reads.put(c + "$4", List.of("<init>: settlement/room/sprite/RoomSpriteXxX.<init>(JSI)V [Local Local Local]"));
		reads.put("init/race/bio/Opinion", List.of(
				"<init>: init/race/bio/Opinion.get(JJS[Ljava/lang/CharSequence;)[Ljava/lang/CharSequence; [Local Local Local FieldRef]",
				"<init>: init/race/bio/Opinion.get(JJS[Ljava/lang/CharSequence;)[Ljava/lang/CharSequence; [Local Local Local FieldRef]",
				"get: has(S)Z [Local]",
				"get: has(S)Z [Local]",
				"get: texts(S)[S [Local]",
				"read: has(S)Z [MORE]",
				"read: texts(S)[S [MORE]",
				"read: has(S)Z [LESS]",
				"read: texts(S)[S [LESS]"));
		Map<String, List<String>> race = RaceReads.all();
		for (String shared : List.of("game/boosting/BoostSpecs", "settlement/stats/standing/StatStanding$StandingDef", "settlement/stats/standing/StatStanding$StandingDef$StandingData")) {
			reads.put(shared, race.get(shared));
		}
		reads.put("settlement/room/sprite/RoomSpriteCombo", List.of("<init>: settlement/room/sprite/RoomSpriteImp.<init>(Linit/sprite/game/SheetType;JS)V [FieldRef Local Local]"));
		reads.put(SPRITE_XXX, SPRITE_XXX_READS);
		reads.putAll(sprites());
		return reads;
	}

	private static Map<String, List<String>> sprites() {
		Map<String, List<String>> reads = new LinkedHashMap<>();
		reads.put("settlement/room/sprite/RoomSpriteImp", List.of(
				"<init>: jsonsIs(S)Z [Local]",
				"<init>: jsons(SI)[J [Local 1]",
				"<init>: init/sprite/game/Sheets.<init>(Linit/sprite/game/SheetType;J)V [Local Unknown]",
				"<init>: json(S)J [Local]",
				"<init>: init/sprite/game/Sheets.<init>(Linit/sprite/game/SheetType;J)V [Local Returned]"));
		reads.put("settlement/room/sprite/RoomSprite1x1", List.of("<init>: settlement/room/sprite/RoomSpriteImp.<init>(Linit/sprite/game/SheetType;JS)V [FieldRef Local Local]"));
		reads.put("settlement/room/sprite/RoomSprite1xN", List.of("<init>: settlement/room/sprite/RoomSprite1x1.<init>(JS)V [Local Local]"));
		reads.put("init/sprite/game/Sheets", List.of("<init>: init/sprite/game/GameSheets.sheets(Linit/sprite/game/SheetType;J)Lsnake2d/util/sets/LIST; [Local Local]"));
		reads.put("init/sprite/game/GameSheets", List.of(
				"<init>: init/sprite/game/GameSheets.sheets(Linit/sprite/game/SheetType;SJ)Lsnake2d/util/sets/LIST; [Returned _OVERLAY null]",
				"raws: init/sprite/game/SheetType.make(SJ)Lsnake2d/util/sets/LIST; [Local Local]",
				"raw: json(S)J [GAME_TEXTURE]",
				"raw: value(S)S [FILE]",
				"raw: i(S)I [ROW]",
				"raw: init/sprite/game/GameSheets.raw(Linit/sprite/game/SheetType;SIJ)Lsnake2d/util/sprite/TILE_SHEET; [Local Returned Returned Returned]",
				"raw: json(S)J [Local]",
				"raw: value(S)S [FILE]",
				"raw: i(S)I [ROW]",
				"raw: init/sprite/game/GameSheets.raw(Linit/sprite/game/SheetType;SIJ)Lsnake2d/util/sprite/TILE_SHEET; [Local Returned Returned Returned]",
				"raw: init/sprite/game/GameSheets.raws(Linit/sprite/game/SheetType;SJ)Lsnake2d/util/sets/LIST; [Local Local Local]",
				"raw: path()S []",
				"sheets: init/sprite/game/GameSheets.raws(Linit/sprite/game/SheetType;SJ)Lsnake2d/util/sets/LIST; [Local Local Local]",
				"sheets: values(S)[S [FRAMES]",
				"sheets: init/sprite/game/SheetData.<init>(J)V [Local]",
				"sheets: has(S)Z [OVERWRITE]",
				"sheets: jsons(S)[J [OVERWRITE]",
				"sheets: init/sprite/game/SheetData.<init>(Linit/sprite/game/SheetData;J)V [New Unknown]",
				"sheets: error(SLjava/lang/CharSequence;)V [Returned Unknown]",
				"sheets: path()S []",
				"sheets: init/sprite/game/GameSheets.sheets(Linit/sprite/game/SheetType;SJ)Lsnake2d/util/sets/LIST; [Local Returned Local]",
				"sheets: error(SLjava/lang/CharSequence;)V [malformatted Row. Format is FOLDER:FILENAME:ROW Unknown]",
				"sheets: path()S []"));
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
				"test: error(SLjava/lang/CharSequence;)V [Returned Returned]"));
		return reads;
	}

	/** The class behind a deposit. */
	static Map<String, List<String>> minable() {
		Map<String, List<String>> reads = new LinkedHashMap<>();
		reads.put("init/resources/Minable", List.of(
				"<init>: bool(S)Z [ON_EVERY_MAP]",
				"<init>: snake2d/util/color/ColorImp.<init>(J)V [Local]",
				"<init>: snake2d/util/color/ColorImp.<init>(JS)V [Local MINIMAP_COLOR]",
				"<init>: d(SDD)D [FERTILITY_INCREASE -1.0 1.0]",
				"<init>: util/keymap/RMAPS.read(J)Lutil/keymap/MAPPED; [Local]",
				"<init>: util/keymap/RMAP.readFill(JD)[D [Local 1.0]",
				"<init>: dTry(SDDD)D [OCCURENCE 0.0 1000.0 1.0]",
				"make: <init>([Ljava/nio/file/Path;)V [Returned]",
				"make: value(S)S [SPRITE]",
				"make: error(SLjava/lang/CharSequence;)V [Returned Returned]",
				"make: init/resources/Minable.<init>(SILsnake2d/util/sprite/TILE_SHEET;J)V [Unknown Returned Returned New]"));
		reads.put("init/resources/Minable$Util", List.of());
		return reads;
	}

	/** The classes behind a crop. */
	static Map<String, List<String>> crop() {
		Map<String, List<String>> reads = new LinkedHashMap<>();
		reads.put("init/resources/Growable", List.of(
				"<init>: util/keymap/RMAPS.read(J)Lutil/keymap/MAPPED; [Local]",
				"<init>: d(SDD)D [SEASONAL_OFFSET 0.0 1.0]",
				"<init>: d(SDD)D [GROWTH_VALUE 0.0 1.0]",
				"<init>: snake2d/util/color/ColorImp.<init>(JS)V [Local MINIMAP_COLOR]",
				"<init>: util/keymap/RMAP.readFill(S[DJDD)V [CLIMATE_BONUS FieldRef Local 0.0 10000.0]",
				"<init>: json(S)J [SPRITE]",
				"<init>: d(SDD)D [POLLEN 0.0 10.0]",
				"<init>: dTry(SDDD)D [WIND_SWAY 0.0 10.0 1.0]",
				"<init>: value(S)S [SPRITE]",
				"<init>: snake2d/util/color/ColorImp.<init>(JS)V [Returned COLOR_POLLEN]",
				"<init>: json(S)J [STEM]",
				"<init>: init/resources/Growable.set(JLinit/resources/GrowableSprite$Part;)V [Returned FieldRef]",
				"<init>: json(S)J [GROWTH]",
				"<init>: init/resources/Growable.set(JLinit/resources/GrowableSprite$Part;)V [Returned FieldRef]",
				"set: d(SDD)D [SHADOW_HEIGHT 0.0 32.0]",
				"set: d(SDD)D [SHADOW_LENGTH 0.0 32.0]",
				"set: snake2d/util/color/ColorImp.<init>(JS)V [Local DEAD]",
				"set: snake2d/util/color/ColorImp.<init>(JS)V [Local LIVE]",
				"set: snake2d/util/color/ColorImp.<init>(JS)V [Local RIPE]",
				"set: has(S)Z [WIND_SWAY]",
				"set: d(SDD)D [WIND_SWAY 0.0 10.0]",
				"make: <init>([Ljava/nio/file/Path;)V [Returned]",
				"make: init/resources/Growable.<init>(SIJLsnake2d/util/sets/KeyMap;)V [Unknown Returned New New]"));
		return reads;
	}
}
