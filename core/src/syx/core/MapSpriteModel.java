package syx.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import syx.core.GameParser.Node;
import syx.core.GameParser.ReadException;
import syx.core.GameParser.Rgb;
import syx.core.PortraitModel.ColorSet;
import syx.core.PortraitModel.Lock;
import syx.core.PortraitModel.Stop;

/**
 * What a race's people look like on the map, as the game loads it. Every value is read with the game's own Json and
 * ColorImp code, in the order and with the limits of RAppearence, RColors, RType, RaceSheet, RAddon, RExtras and
 * RaceSprites. Each image is checked against the size its ITileSheet demands, because a wrong size stops the game
 * (ComposerUtil.java:42-48).
 */
final class MapSpriteModel {

	/** Finds a race sprite by the folder under sprite/ and the name the game looks it up with. Null means neither the mod nor the game has it. */
	interface Images {
		PngCodec.Image get(String folder, String name) throws IOException;
	}

	/**
	 * One addon of a type: a pair of frames on the type's sheet, drawn below or above the head.
	 *
	 * @param index ADDON_INDEX, which row of the sheet's addon area it uses
	 * @param color its colour, or null for the game's white stand-in (RAddon.java:29-32)
	 * @param item its place in ADDONS
	 */
	record Addon(int item, int index, boolean belowHead, ColorSet color, List<Lock> locks) {
	}

	/**
	 * One of a race's types, or its child.
	 *
	 * @param sheet the SPRITE_FILE sheet in sprite/race/
	 * @param skeleton the SPRITE_SKELLETON_FILE sheet in sprite/race/skelleton/
	 */
	record Type(String label, boolean child, String sheetName, PngCodec.Image sheet, String skeletonName, PngCodec.Image skeleton, ColorSet skin, ColorSet leg, List<Addon> addons) {
	}

	/** Sizes each ITileSheet demands: exact width, minimum height. */
	static final int SHEET_WIDTH = 448;
	static final int SHEET_HEIGHT = 546;
	static final int SKELETON_WIDTH = 316;
	static final int SKELETON_HEIGHT = 120;
	static final int EXTRA_WIDTH = 428;
	static final int EXTRA_HEIGHT = 310;
	static final int SLEEP_WIDTH = 164;
	static final int SLEEP_HEIGHT = 44;
	static final int OVERLAYS_WIDTH = 460;
	static final int OVERLAYS_HEIGHT = 486;
	/** Vanilla's _CLOTHES MAX_AMOUNT, used only when the file can't be read, which a note says. */
	private static final int VANILLA_CLOTHES_MAX = 6;

	final Rgb blood;
	final List<ColorSet> colors;
	/** For each of the 16 variations, a colour per clothes level from none to full (RColors.java:245-265). */
	final Rgb[][] clothes;
	final int clothesMax;
	final String extraName;
	final PngCodec.Image extra;
	final String sleepName;
	final PngCodec.Image sleep;
	final PngCodec.Image overlays;
	/** The adults in order, then the child, as the portrait sim counts them. */
	final List<Type> types;

	private MapSpriteModel(Rgb blood, List<ColorSet> colors, Rgb[][] clothes, int clothesMax, String extraName, PngCodec.Image extra, String sleepName, PngCodec.Image sleep, PngCodec.Image overlays, List<Type> types) {
		this.blood = blood;
		this.colors = colors;
		this.clothes = clothes;
		this.clothesMax = clothesMax;
		this.extraName = extraName;
		this.extra = extra;
		this.sleepName = sleepName;
		this.sleep = sleep;
		this.overlays = overlays;
		this.types = types;
	}

	/**
	 * @param clothesData the game's _CLOTHES file as layered, or null if it couldn't be read
	 */
	static MapSpriteModel read(Node root, Node clothesData, GameParser parser, Images images, Set<String> notes) throws ReadException, Stop, IOException {
		int clothesMax = VANILLA_CLOTHES_MAX;
		if (clothesData != null) {
			// Equip.java:73. The clothes stat holds 0 to this many (RColors.java:38).
			clothesMax = clothesData.integer("MAX_AMOUNT", 1, 15);
		} else {
			notes.add("The clothes file init/stats/equip/civic/_CLOTHES.txt couldn't be read, so clothes have " + VANILLA_CLOTHES_MAX + " levels, as in the game's own file.");
		}
		// RColors.java:38-40: clothes, then blood, then COLORS.
		Rgb[][] clothes = clothes(root, clothesMax + 1, parser);
		Rgb blood = root.color("COLOR_BLOOD");
		Map<String, ColorSet> colors = PortraitModel.colors(root.block("COLORS"), parser);
		// RAppearence.java:51-61.
		String extraName = root.value("SPRITE_EXTRA_FILE");
		PngCodec.Image extra = required(images, "race/extra", extraName, "SPRITE_EXTRA_FILE", EXTRA_WIDTH, EXTRA_HEIGHT, "RExtras.java:23");
		String sleepName = root.value("SLEEP_FILE");
		PngCodec.Image sleep = required(images, "race/sleep", sleepName, "SLEEP_FILE", SLEEP_WIDTH, SLEEP_HEIGHT, "RAppearence.java:67");
		// RACES loads the stains once for every race (RaceSprites.java:28).
		PngCodec.Image overlays = required(images, "race/misc", "Overlays", null, OVERLAYS_WIDTH, OVERLAYS_HEIGHT, "RaceSprites.java:28");
		// RAppearence.java:85-138: CHILD first, then TYPES, 1 to 3 of them.
		Type child = type("Child", true, root.block("CHILD"), colors, images);
		List<Node> blocks = root.blocks("TYPES", 1, 4);
		List<Type> types = new ArrayList<>();
		for (int i = 0; i < blocks.size(); i++) {
			types.add(type(blocks.size() == 1 ? "Adult" : "Adult " + (i + 1), false, blocks.get(i), colors, images));
		}
		types.add(child);
		return new MapSpriteModel(blood, List.copyOf(colors.values()), clothes, clothesMax, extraName, extra, sleepName, sleep, overlays, types);
	}

	/** RColors.java:245-265. The full colour is the last level, and each level below it is less saturated. */
	private static Rgb[][] clothes(Node root, int levels, GameParser parser) throws ReadException {
		List<Node> blocks = root.blocks("COLOR_CLOTHES", 1, 16);
		Rgb[] listed = new Rgb[blocks.size()];
		for (int i = 0; i < listed.length; i++) {
			listed[i] = blocks.get(i).color("COLOR");
		}
		Rgb[][] colours = new Rgb[16][levels];
		for (int v = 0; v < 16; v++) {
			colours[v][levels - 1] = listed[v % listed.length];
			for (int s = levels - 2; s >= 0; s--) {
				colours[v][s] = parser.saturated(colours[v][levels - 1], (s + 1.0) / (levels - 1));
			}
		}
		return colours;
	}

	/** RType.java:30-100. */
	private static Type type(String label, boolean child, Node json, Map<String, ColorSet> colors, Images images) throws ReadException, Stop, IOException {
		String sheetName = json.value("SPRITE_FILE");
		PngCodec.Image sheet = required(images, "race", sheetName, label + " SPRITE_FILE", SHEET_WIDTH, SHEET_HEIGHT, "RaceSheet.java:18");
		String skeletonName = json.value("SPRITE_SKELLETON_FILE");
		PngCodec.Image skeleton = required(images, "race/skelleton", skeletonName, label + " SPRITE_SKELLETON_FILE", SKELETON_WIDTH, SKELETON_HEIGHT, "RType.java:45");
		// RTypeSpec reads these with RMAP.read, which stops the game on a name that isn't in COLORS (RType.java:92-93).
		ColorSet skin = colour(colors, json.value("COLOR_SKIN"), label + " COLOR_SKIN");
		ColorSet leg = colour(colors, json.value("COLOR_LEG"), label + " COLOR_LEG");
		List<Addon> addons = new ArrayList<>();
		if (json.has("ADDONS")) {
			List<Node> blocks = json.blocks("ADDONS");
			for (int i = 0; i < blocks.size(); i++) {
				addons.add(addon(blocks.get(i), i, label, colors));
			}
		}
		return new Type(label, child, sheetName, sheet, skeletonName, skeleton, skin, leg, List.copyOf(addons));
	}

	/** RAddon.java:26-65, read in its order: CONDITIONS, COLOR, ADDON_INDEX, then RType reads BELOW_HEAD. */
	private static Addon addon(Node json, int item, String label, Map<String, ColorSet> colors) throws ReadException, Stop {
		String where = label + " addon " + (item + 1);
		List<Lock> locks = json.has("CONDITIONS") ? PortraitModel.locks(json.block("CONDITIONS"), where) : List.of();
		ColorSet color = json.has("COLOR") ? colour(colors, json.value("COLOR"), where + " COLOR") : null;
		int index = json.integer("ADDON_INDEX", 0, 8);
		// Json.i doesn't enforce its maximum, but RAddon keeps the first of each index in an array of 8 (:33-35).
		if (index >= 8) {
			throw new Stop(where + " has ADDON_INDEX " + index + ". The game keeps addons in an array of 8, so anything above 7 stops it loading (RAddon.java:33-35).");
		}
		boolean below = json.bool("BELOW_HEAD");
		return new Addon(item, index, below, color, locks);
	}

	private static ColorSet colour(Map<String, ColorSet> colors, String name, String where) throws Stop {
		ColorSet color = PortraitModel.lookup(colors, name);
		if (color == null) {
			throw new Stop(where + " names the colour " + name + ", which isn't in COLORS. The game stops loading for this (RMAP.java:82). Available: " + String.join(", ", colors.keySet()) + ".");
		}
		return color;
	}

	/**
	 * A sheet the game must find, at the size its ITileSheet demands. A missing one stops the game, because the
	 * folder lookup throws "This resource could not be found" (Normal.java:43, VirtualFolder.java:59-63).
	 *
	 * @param tag the tag that names it, or null for a file the game names itself
	 */
	private static PngCodec.Image required(Images images, String folder, String name, String tag, int width, int minHeight, String evidence) throws IOException, Stop {
		PngCodec.Image image = images.get(folder, name);
		String what = (tag == null ? "The game's " : tag + " names ") + "sprite/" + folder + "/" + name + ".png";
		if (image == null) {
			throw new Stop(what + ", which is neither in this mod nor in the game. The game stops loading without it (" + evidence + ", VirtualFolder.java:59-63).");
		}
		if (image.width() != width || image.height() < minHeight) {
			throw new Stop(what + ", which is " + image.width() + " by " + image.height() + ". It must be " + width + " wide and at least " + minHeight + " tall, or the game stops loading (" + evidence + ", ComposerUtil.java:42-48).");
		}
		return image;
	}
}
