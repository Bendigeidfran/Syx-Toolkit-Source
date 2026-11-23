package syx.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import syx.core.AssetChecker.Finding;
import syx.core.AssetChecker.Severity;
import syx.core.TagSpec.Confidence;
import syx.core.TagSpec.Kind;

/**
 * A play mode in init/player/mode, with its text in text/player/mode. Its reads are spread over the menu
 * that lists modes (ScRandom), the setting that applies one (PBonusSetting) and the boost parser, so each
 * class is pinned in {@link #reads()}. A difference there leaves the type off.
 */
public final class PlayerSchemas {

	/** The class that applies a mode, used as the type's loader. */
	public static final String MODE_LOADER = "game.faction.player.PBonusSetting";

	/**
	 * How many mode icons there are. ScRandom indexes modeIcons with ICON_I (ScRandom.java:118), and that
	 * array is new SPRITE[5*7] (PTitles.java:547), so 35 or more is out of bounds when the menu is built.
	 * The read itself has no range, so the method that makes the array is pinned by its body hash.
	 */
	static final int MODE_ICONS = 35;
	private static final String ICON_MAKER = "game/faction/player/PTitles$IconMaker";
	private static final String ICON_MAKER_ALL = "()[Lsnake2d/util/sprite/SPRITE;";
	private static final String ICON_MAKER_ALL_HASH = "eb1bb8a04d5860b4f04de1156f21e88b";

	private PlayerSchemas() {
	}

	public static AssetSchema modeSchema(ClassFileScanner scanner, List<String> unresolved) throws ClassFileScanner.ScanException {
		Map<String, List<String>> reads = reads();
		ReviewedReads.check(scanner, reads, unresolved);
		String hash = scanner.bodyHash(ICON_MAKER, "all", ICON_MAKER_ALL);
		if (!ICON_MAKER_ALL_HASH.equals(hash)) {
			unresolved.add(ICON_MAKER + ".all, which makes the mode icons ICON_I picks from, isn't what was reviewed.");
		}
		if (!unresolved.isEmpty()) {
			return null;
		}
		AssetSchema schema = build();
		ReviewedReads.checkLimits(schema, reads, unresolved);
		return unresolved.isEmpty() ? schema : null;
	}

	/** Every read of a mode's files, in code order, as the installed jar has them. */
	static Map<String, List<String>> reads() {
		Map<String, List<String>> reads = new LinkedHashMap<>();
		reads.put("game/faction/player/PBonusSetting", List.of(
				"<init>: <init>(Ljava/nio/file/Path;)V [Returned]",
				"<init>: text(S)S [NAME]",
				"setMode: <init>([Ljava/nio/file/Path;)V [Returned]",
				"setMode: json(S)J [BOOST]",
				"setMode: keys()Lsnake2d/util/sets/LIST; []",
				"setMode: game/boosting/BoostSpecs$ParseValue.<init>(JS)V [Returned Returned]"));
		// The first two reads are the player's own Properties profile, not a mode file.
		reads.put("menu/ScRandom", List.of(
				"<init>: <init>([Ljava/nio/file/Path;)V [Returned]",
				"<init>: has(S)Z [MODE]",
				"<init>: value(S)S [MODE]",
				"<init>: <init>([Ljava/nio/file/Path;)V [Returned]",
				"<init>: <init>([Ljava/nio/file/Path;)V [Returned]",
				"<init>: util/info/INFO.<init>(J)V [New]",
				"<init>: i(S)I [ICON_I]"));
		reads.put("game/boosting/BoostSpecs$ParseValue", List.of(
				"<init>: d(S)D [Local]",
				"<init>: error(SLjava/lang/CharSequence;)V [Malformed value. Must be a string that ends with >ADD (addition), or >MUL (Multiplication) Local]"));
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

	private static AssetSchema build() {
		List<TagSpec> data = List.of(
				new TagSpec("ICON_I", Kind.INT, true, null, null, null, false, null, null, List.of(),
						"ScRandom.java:118, PTitles.java:547. Read with i and no range, then used to pick one of " + MODE_ICONS + " icons, numbered from 0. The range is checked beside the table.",
						Confidence.CODE_VERIFIED),
				new TagSpec("BOOST", Kind.DECIMAL_MAP, true, null, null, null, true, null, null, List.of(),
						"PBonusSetting.java:226-233, BoostSpecs.java:205-217. Read with json, which stops the game when it's missing. A key must end in >ADD or >MUL. Which stats can be boosted is decided in code, so this isn't a list.",
						Confidence.CODE_VERIFIED));
		List<String> notes = List.of(
				"ICON_I picks one of " + MODE_ICONS + " mode icons, numbered 0 to " + (MODE_ICONS - 1) + ". Any other number stops the game when the new-game menu is built (ScRandom.java:118, PTitles.java:547).",
				"Boost names aren't checked, because which stats can be boosted is decided in code. A name the game doesn't know is logged and skipped, with no message in game (PBonusSetting.java:89-92), so check the spelling.",
				"The game reads BOOST when a game starts in this mode, and again each time a save made in it is loaded (PBonusSetting.java:156-160, 223-236). Changing it changes those saves.",
				"Each mode makes the value PLAY_MODE_ followed by its key, which is 1 while that mode is played (PBonusSetting.java:56-67). Titles, levels and events can test it.",
				"Vanilla modes also have TITLE, which no read of a mode file uses. It isn't on the form, and stays as written.");
		AssetSchema.CrossRule rule = PlayerSchemas::modeRules;
		// PBonusSetting reads NAME with text at the top of every mode's text file (PBonusSetting.java:56-59), so it can't
		// be in an INFO block, and INFO then reads the rest from the top too (INFO.java:16-17).
		return new AssetSchema(data, AssetCatalog.infoTagsFor(), List.of(), notes, rule, List.of()).withTextAtRoot();
	}

	private static void modeRules(Map<String, String> values, GameAssetIndex index, List<Finding> findings) {
		// BOOST may be empty, as vanilla's 10_GREAT is. The writer always writes the block, because it's required.
		for (Map.Entry<String, String> entry : values.entrySet()) {
			String key = entry.getKey();
			if (key.startsWith("BOOST.") && !key.endsWith(">ADD") && !key.endsWith(">MUL")) {
				findings.add(new Finding(key, Severity.ERROR, "A boost key has to end in >ADD or >MUL, or the game stops when a game starts in this mode (BoostSpecs.java:205-217)."));
			}
		}
		String icon = values.get("ICON_I");
		if (icon != null && icon.trim().matches("-?\\d+")) {
			long number = Long.parseLong(icon.trim());
			if (number < 0 || number >= MODE_ICONS) {
				findings.add(new Finding("ICON_I", Severity.ERROR, "This must be from 0 to " + (MODE_ICONS - 1) + ". The game has " + MODE_ICONS
						+ " mode icons and stops when the new-game menu is built with any other number (ScRandom.java:118, PTitles.java:547)."));
			}
		}
	}
}
