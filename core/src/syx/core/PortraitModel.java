package syx.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import syx.core.GameParser.Node;
import syx.core.GameParser.ReadException;
import syx.core.GameParser.Rgb;

/**
 * A race's portrait as the game loads it. Every value is read with the game's own Json and ColorImp code,
 * in the same order and with the same limits as RColors, RPortrait, RPortraitFrame, RaceFrameMaker and RCrown.
 */
final class PortraitModel {

	/** The game would stop loading. What's wrong is in the message. */
	static final class Stop extends Exception {
		private static final long serialVersionUID = 1L;

		Stop(String message) {
			super(message);
		}
	}

	/** Finds images by the name the game looks them up with. Null means the file doesn't exist. */
	interface Images {
		PortraitRenderer.Sheet face(String name) throws IOException;

		PortraitRenderer.Sheet addon(String name) throws IOException;
	}

	record Lock(String comparator, String stat, double value) {
	}

	/** @param entries for each of the 16 slots, the VALUES entry it comes from, or -1 for a shade the game generates */
	record ColorSet(String name, int index, Rgb[] slots, int[] entries, boolean turnsGray, boolean turnsWhite, boolean sickly, String pick) {
	}

	/**
	 * A sheet of null is the game's stand-in for a file or row it couldn't find, which draws nothing.
	 *
	 * @param index the frame in the sheet
	 * @param file the name as written
	 * @param item which entry of FRAMES this is, counting ones the game skips
	 */
	record Frame(PortraitRenderer.Sheet sheet, int index, String file, int item) {
	}

	record Part(List<Frame> frames, int random, int occurrence, int dx, int dy, int dxr, int dyr, List<Lock> locks, ColorSet color, int opacity, boolean stains) {
	}

	record Type(String label, boolean child, List<Part> parts) {
	}

	/** One crown, helmet or cap: one frame of an addon sheet. */
	record Overlay(PortraitRenderer.Sheet sheet, int frame, int offX, int offY) {
	}

	/** The comparators in init.value.COMPARATOR. */
	private static final Set<String> COMPARATORS = Set.of("LESS", "GREATER", "GREATERE", "EQUAL", "NEQUAL");

	/** What RPortraitFrame's check allows (:39-51). Its FRAME_OCCURENCE is misspelled, and the read beneath it isn't. */
	private static final Set<String> ALLOWED_PART_TAGS = Set.of("FRAMES", "FRAME_RANDOM", "FRAME_OCCURENCE", "OFF_X", "OFF_Y", "OFF_X_RANDOM", "OFF_Y_RANDOM", "CONDITIONS", "COLOR", "OPACITY", "STAINS");

	final Rgb blood;
	final List<ColorSet> colors;
	final List<Type> types;
	final List<Overlay> crowns;
	final List<Overlay> raiders;
	final List<Overlay> mercs;
	final PortraitRenderer.Sheet stains;

	private PortraitModel(Rgb blood, List<ColorSet> colors, List<Type> types, List<Overlay> crowns, List<Overlay> raiders, List<Overlay> mercs, PortraitRenderer.Sheet stains) {
		this.blood = blood;
		this.colors = colors;
		this.types = types;
		this.crowns = crowns;
		this.raiders = raiders;
		this.mercs = mercs;
		this.stains = stains;
	}

	static PortraitModel read(Node root, GameParser parser, Images images, Set<String> warnings) throws ReadException, Stop, IOException {
		Rgb blood = root.color("COLOR_BLOOD");
		Map<String, ColorSet> colors = colors(root.block("COLORS"), parser);
		List<Overlay> crowns = overlays(root, "CROWN", images, warnings);
		List<Overlay> raiders = overlays(root, "RAIDER", images, warnings);
		List<Overlay> mercs = overlays(root, "MERC", images, warnings);
		// RaceFrameMaker's constructor needs this file, and has no fallback (:40,45,98).
		PortraitRenderer.Sheet stains = checked(images.face("_Overlays"), "_Overlays");
		if (stains == null) {
			throw new Stop("The game needs a face sheet called _Overlays, and stops loading without it (RaceFrameMaker.java:98).");
		}
		Map<String, Boolean> warnedFiles = new LinkedHashMap<>();
		Type child = type("Child", true, root.block("CHILD"), colors, images, warnings, warnedFiles);
		List<Node> blocks = root.blocks("TYPES", 1, 4);
		List<Type> types = new ArrayList<>();
		for (int i = 0; i < blocks.size(); i++) {
			types.add(type(blocks.size() == 1 ? "Adult" : "Adult " + (i + 1), false, blocks.get(i), colors, images, warnings, warnedFiles));
		}
		types.add(child);
		return new PortraitModel(blood, List.copyOf(colors.values()), types, crowns, raiders, mercs, stains);
	}

	static Map<String, ColorSet> colors(Node block, GameParser parser) throws ReadException, Stop {
		Map<String, ColorSet> colors = new LinkedHashMap<>();
		int index = 0;
		for (String key : block.keys()) {
			Node json = block.block(key);
			boolean gray = json.boolOr("TURNS_GRAY_WHEN_OLD", false);
			boolean white = json.boolOr("TURNS_WHITE_WHEN_DEAD", false);
			boolean sickly = json.boolOr("TURNS_SICKLY", false);
			String pick = json.has("PICK_BY_STAT") ? json.value("PICK_BY_STAT") : null;
			List<Rgb> listed = json.colors("VALUES");
			// RColors.java:120-125 fills an array of 16, so a 17th colour stops the game.
			if (listed.size() > 16) {
				throw new Stop("COLORS." + key + " has " + listed.size() + " colours, and the game stops loading with more than 16 (RColors.java:120-125).");
			}
			if (listed.isEmpty()) {
				throw new Stop("COLORS." + key + " has no colours, and the game stops loading without one (RColors.java:130,138).");
			}
			colors.put(key, new ColorSet(key, index++, slots(listed, json, parser), entries(listed.size(), json.has("GENERATE_RANDOMIZE")), gray, white, sickly, pick));
		}
		return colors;
	}

	/** Which VALUES entry fills each slot, by the same sums as slots(). */
	private static int[] entries(int listed, boolean generated) {
		int[] entries = new int[16];
		for (int i = 0; i < 16; i++) {
			if (generated) {
				entries[i] = i < listed ? i : -1;
			} else {
				entries[i] = Math.max(0, Math.min(listed * i / 16, listed - 1));
			}
		}
		return entries;
	}

	/** RColors.java:120-141. */
	private static Rgb[] slots(List<Rgb> listed, Node json, GameParser parser) throws ReadException {
		Rgb[] slots = new Rgb[16];
		if (json.has("GENERATE_RANDOMIZE")) {
			double amount = json.decimal("GENERATE_RANDOMIZE");
			for (int i = 0; i < listed.size(); i++) {
				slots[i] = listed.get(i);
			}
			for (int i = listed.size(); i < 16; i++) {
				slots[i] = parser.shade(slots[i % listed.size()], 1.0 - amount * (i / 16.0));
			}
			return slots;
		}
		for (int i = 0; i < 16; i++) {
			slots[i] = listed.get(Math.max(0, Math.min(listed.size() * i / 16, listed.size() - 1)));
		}
		return slots;
	}

	private static Type type(String label, boolean child, Node json, Map<String, ColorSet> colors, Images images, Set<String> warnings, Map<String, Boolean> warnedFiles) throws ReadException, Stop, IOException {
		if (!json.has("FACE")) {
			return new Type(label, child, List.of());
		}
		List<Node> blocks = json.blocks("FACE");
		List<Part> parts = new ArrayList<>();
		for (int i = 0; i < blocks.size(); i++) {
			parts.add(part(blocks.get(i), i, label, colors, images, warnings, warnedFiles));
		}
		return new Type(label, child, parts);
	}

	/** RPortraitFrame.java:59-82. */
	private static Part part(Node json, int index, String label, Map<String, ColorSet> colors, Images images, Set<String> warnings, Map<String, Boolean> warnedFiles) throws ReadException, Stop, IOException {
		String where = label + " part " + (index + 1);
		for (String key : json.keys()) {
			if (key.equals("FRAME_OCCURENCE")) {
				warnings.add(where + " writes FRAME_OCCURENCE. The game only reads FRAME_OCCURRENCE, so this has no effect (RPortraitFrame.java:69).");
			} else if (!ALLOWED_PART_TAGS.contains(key) && !key.equals("FRAME_OCCURRENCE")) {
				warnings.add(where + " has a tag the game doesn't read here: " + key + " (RPortraitFrame.java:61-65).");
			}
		}
		List<Frame> frames = frames(json, where, images, warnings, warnedFiles);
		int random = json.has("FRAME_RANDOM") ? json.integer("FRAME_RANDOM", 0, 16) : index % 16;
		int occurrence = (int) (0x010 * (json.has("FRAME_OCCURRENCE") ? json.decimal("FRAME_OCCURRENCE", 0, 1) : 1.0));
		int dx = json.has("OFF_X") ? json.integer("OFF_X", -40, 40) : 0;
		int dy = json.has("OFF_Y") ? json.integer("OFF_Y", -48, 48) : 0;
		int dxr = json.has("OFF_X_RANDOM") ? json.integer("OFF_X_RANDOM", 0, 40) : 0;
		int dyr = json.has("OFF_Y_RANDOM") ? json.integer("OFF_Y_RANDOM", 0, 48) : 0;
		List<Lock> locks = json.has("CONDITIONS") ? locks(json.block("CONDITIONS"), where) : List.of();
		ColorSet color = null;
		if (json.has("COLOR")) {
			String name = json.value("COLOR");
			color = lookup(colors, name);
			if (color == null) {
				throw new Stop(where + " names the colour " + name + ", which isn't in COLORS. The game stops loading for this (RMAP.java:82). Available: " + String.join(", ", colors.keySet()) + ".");
			}
		}
		int opacity = json.has("OPACITY") ? json.integer("OPACITY", 0, 256) : 255;
		boolean stains = json.has("STAINS") ? json.bool("STAINS") : true;
		return new Part(frames, random, occurrence, dx, dy, dxr, dyr, locks, color, Math.max(0, Math.min(255, opacity)), stains);
	}

	/** A name also finds a key written with a leading _ (KeyMap.java:42-58). */
	static ColorSet lookup(Map<String, ColorSet> colors, String name) {
		return colors.containsKey(name) ? colors.get(name) : colors.get("_" + name);
	}

	/** Lockable.java:115-127. */
	static List<Lock> locks(Node json, String where) throws ReadException, Stop {
		List<Lock> locks = new ArrayList<>();
		for (String comparator : json.keys()) {
			if (!COMPARATORS.contains(comparator)) {
				throw new Stop(where + " has the condition " + comparator + ". The game only has LESS, GREATER, GREATERE, EQUAL and NEQUAL, and stops loading for anything else (Lockable.java:117, RMAP.java:82).");
			}
			Node stats = json.block(comparator);
			for (String stat : stats.keys()) {
				locks.add(new Lock(comparator, stat, stats.decimal(stat)));
			}
		}
		return locks;
	}

	/** RaceFrameMaker.java:51-117. */
	private static List<Frame> frames(Node json, String where, Images images, Set<String> warnings, Map<String, Boolean> warnedFiles) throws ReadException, Stop, IOException {
		List<Frame> frames = new ArrayList<>();
		String[] values = json.values("FRAMES");
		for (int item = 0; item < values.length; item++) {
			String value = values[item];
			String[] parts = value.split(":");
			int row;
			try {
				if (parts.length != 2) {
					throw new NumberFormatException();
				}
				row = Integer.parseInt(parts[1].trim());
			} catch (NumberFormatException e) {
				warnings.add(where + " has a frame the game can't read, so it's skipped: " + value.trim() + ". Frames are written FILE: ROW (RaceFrameMaker.java:59).");
				continue;
			}
			String file = parts[0].trim();
			PortraitRenderer.Sheet sheet = checked(images.face(file), file);
			if (sheet == null) {
				if (warnedFiles.putIfAbsent(file, true) == null) {
					warnings.add("No face sheet called " + file + ". The game draws nothing for that frame (RaceFrameMaker.java:93-97).");
				}
				frames.add(new Frame(null, row, file, item));
			} else if (row < 0 || row >= sheet.frames()) {
				warnings.add(file + " has no row " + row + ": it has rows 0 to " + (sheet.frames() - 1) + ". The game draws nothing for that frame (RaceFrameMaker.java:106-111).");
				frames.add(new Frame(null, row, file, item));
			} else {
				frames.add(new Frame(sheet, row, file, item));
			}
		}
		return frames;
	}

	/** A wrong size stops the game (RaceFrameMaker.java:128-129). */
	private static PortraitRenderer.Sheet checked(PortraitRenderer.Sheet sheet, String file) throws Stop {
		if (sheet != null && (sheet.width() != 416 || sheet.height() < 60)) {
			throw new Stop("The face sheet " + file + " is " + sheet.width() + " by " + sheet.height() + ". It must be 416 wide and at least 60 tall, or the game stops loading (RaceFrameMaker.java:128-129).");
		}
		return sheet;
	}

	/** RCrown.java:32-75. */
	private static List<Overlay> overlays(Node root, String key, Images images, Set<String> warnings) throws ReadException, Stop, IOException {
		if (!root.has(key)) {
			throw new Stop(key + " is missing, and the game stops loading without it (RCrown.java:33-34).");
		}
		List<Overlay> overlays = new ArrayList<>();
		Set<String> warned = new HashSet<>();
		List<Node> blocks = root.isList(key) ? root.blocks(key) : List.of(root.block(key));
		for (Node json : blocks) {
			int offX = json.integer("OFFX", -48, 48);
			int offY = json.integer("OFFY", -48, 48);
			String file = json.value("FILE");
			PortraitRenderer.Sheet sheet = images.addon(file);
			if (sheet == null) {
				if (warned.add(file)) {
					warnings.add(key + " names an addon sheet that doesn't exist: " + file + ". The game skips it (RCrown.java:56-58).");
				}
				continue;
			}
			if (sheet.width() != 104 || sheet.height() < 36) {
				throw new Stop("The addon sheet " + file + " is " + sheet.width() + " by " + sheet.height() + ". It must be 104 wide and at least 36 tall, or the game stops loading (RCrown.java:61).");
			}
			for (int i = 0; i < sheet.height() / 36; i++) {
				overlays.add(new Overlay(sheet, i, offX, offY));
			}
		}
		return overlays;
	}
}
