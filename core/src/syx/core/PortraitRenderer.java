package syx.core;

import java.util.List;
import java.util.Set;

import syx.core.GameParser.ReadException;
import syx.core.GameParser.Rgb;
import syx.core.PortraitModel.ColorSet;
import syx.core.PortraitModel.Frame;
import syx.core.PortraitModel.Lock;
import syx.core.PortraitModel.Overlay;
import syx.core.PortraitModel.Part;
import syx.core.PortraitModel.Type;

/**
 * Draws a portrait the way the game's interface does: the placement and rolls of RPortraitFrame, the colours of
 * RColors, then the sprite shader (SpritePoint_v.txt, SpritePoint_f.txt) and the interface's light (game.time.Gui,
 * LightAmbient_f.txt). Colours are doubled in the shader, and each pixel is lit against the sheet's normal map.
 */
final class PortraitRenderer {

	static final int WIDTH = 40;
	static final int HEIGHT = 64;
	static final int MAX_EQUIPMENT = 5;

	private static final int TILE = 8;
	private static final int FRAME_HEIGHT = 48;
	static final Rgb WHITE = new Rgb(127, 127, 127);
	/** RColors.java:31-32. These two aren't halved, as they aren't read from a file. */
	private static final Rgb GREY = new Rgb(170, 170, 170);
	private static final Rgb DEAD = new Rgb(128, 128, 150);
	/** The game uses the colour of the active disease or event (RColors.java:192-197). This one stands in. */
	static final Rgb SICK = new Rgb(80, 160, 70);

	/** game.time.Gui: an ambient light of 1.3, from direction 220 and tilted 35. Its night cycle is an option, and left out. */
	private static final LitCanvas.Light GUI_LIGHT = LitCanvas.Light.of(1.3, 220, 35);

	/** A face or addon sheet, with each frame's trim worked out when first used. */
	static final class Sheet {
		private final PngCodec.Image image;
		private final int[][] trims;

		Sheet(PngCodec.Image image) {
			this.image = image;
			this.trims = new int[image.width() == 416 ? image.height() / 60 * 4 : 0][];
		}

		int width() {
			return image.width();
		}

		PngCodec.Image image() {
			return image;
		}

		int height() {
			return image.height();
		}

		/** How many frames a face sheet has. */
		int frames() {
			return trims.length;
		}

		/** { first row in the sheet, offset down the frame, rows of 8 pixels }, as RaceFrameMaker.java:142-180 finds them. */
		private int[] trim(int frame) {
			if (trims[frame] != null) {
				return trims[frame];
			}
			int sx = 6 + 52 * (frame % 4);
			int sy = 6 + 60 * (frame / 4);
			int y1 = sy;
			int y2 = y1 + FRAME_HEIGHT;
			while (y1 < sy + FRAME_HEIGHT && !rowHasPixels(sx, y1)) {
				y1++;
			}
			int passes = 0;
			while (passes < FRAME_HEIGHT && !rowHasPixels(sx, y2 - 1)) {
				y2--;
				passes++;
			}
			int offY = y1 - sy;
			int rows = (int) Math.ceil((double) (y2 - y1) / TILE);
			if (y1 + rows * TILE > sy + FRAME_HEIGHT) {
				y1 = sy + FRAME_HEIGHT - rows * TILE;
				offY = y1 - sy;
			}
			trims[frame] = new int[] { y1, offY, Math.max(0, rows) };
			return trims[frame];
		}

		private boolean rowHasPixels(int sx, int y) {
			if (y < 0 || y >= image.height()) {
				return false;
			}
			for (int x = 0; x < WIDTH; x++) {
				if ((image.at(sx + x, y) & 0xFF) != 0) {
					return true;
				}
			}
			return false;
		}
	}

	/** The person a portrait is drawn for. Equipment is 0 to 5. */
	record Subject(int[] randomness, double age, boolean dead, boolean noble, boolean slave, boolean parent, boolean retired, boolean sick, int plate, int leather, double grime, double injury, boolean child) {
	}

	private PortraitRenderer() {
	}

	/** The game's DataRandom.get: bits read across a person's four random ints. */
	static int random(int[] ints, int startBit, int bits) {
		int start = startBit & 127;
		int word = start / 32;
		long value = ints[word] & 0xFFFFFFFFL | (ints[(word + 1) % 4] & 0xFFFFFFFFL) << 32;
		value >>>= start & 31;
		value &= (1 << bits) - 1;
		return (int) value;
	}

	/** Spreads a seed across the four ints. It's not the game's own sequence, so it can't recreate a particular person. */
	static int[] randomness(long seed) {
		int x = (int) seed;
		int[] ints = new int[4];
		for (int i = 0; i < 4; i++) {
			x = x * 1664525 + 1013904223;
			int z = x + (i + 1) * 0x9E3779B9;
			z = (z ^ z >>> 16) * 0x7FEB352D;
			z = (z ^ z >>> 15) * 0x846CA68B;
			ints[i] = z ^ z >>> 16;
		}
		return ints;
	}

	/** What became of one part of the face: which frame it used, whether it drew, and whether that was pinned. */
	record Choice(int frame, boolean shown, boolean pinned) {
	}

	/** Sets the bytes the RANDOM_n stats and colour rolls read. A negative byte leaves the seed's value. */
	static void applyRandom(int[] ints, int[] bytes) {
		for (int i = 0; i < 8 && i < bytes.length; i++) {
			if (bytes[i] >= 0) {
				int bit = 64 + 8 * i;
				int shift = bit & 31;
				ints[bit / 32] = ints[bit / 32] & ~(0xFF << shift) | (bytes[i] & 0xFF) << shift;
			}
		}
	}

	/**
	 * @param choices given one entry per part of the face
	 */
	static int[] render(PortraitModel model, Type type, Subject subject, Overlay overlay, int scale, PortraitSim.Pins pins, GameParser parser, Set<String> notes, List<Choice> choices) throws ReadException {
		LitCanvas canvas = new LitCanvas(WIDTH * scale, HEIGHT * scale);
		for (int i = 0; i < type.parts().size(); i++) {
			choices.add(draw(canvas, type.parts().get(i), i, model, subject, scale, pins, parser, notes));
		}
		if (overlay != null) {
			drawOverlay(canvas, overlay, scale);
		}
		return canvas.lit(GUI_LIGHT);
	}

	/** RPortraitFrame.java:84-131. A pinned part is shown whatever its conditions and roll say, so it can be looked at. */
	private static Choice draw(LitCanvas canvas, Part part, int partIndex, PortraitModel model, Subject subject, int scale, PortraitSim.Pins pins, GameParser parser, Set<String> notes) throws ReadException {
		int count = part.frames().size();
		if (count == 0) {
			return new Choice(0, false, false);
		}
		int ran = random(subject.randomness(), part.random() * 16, 31) & 0x0FF;
		Integer pin = pins.frames().get(partIndex);
		boolean pinned = pin != null;
		int chosen = pinned ? Math.floorMod(pin, count) : ran % count;
		boolean allowed = passes(part, subject, notes);
		boolean shown = pinned || allowed && part.occurrence() > (ran & 0x0F);
		Choice choice = new Choice(chosen, shown, pinned);
		if (!shown) {
			return choice;
		}
		Rgb color = parser.shadeSelf(color(part.color(), subject, pins, parser, notes), 1.2);
		int x = (int) (0 + (part.dx() + (ran / 15.0) * part.dxr()) * scale);
		int y = (int) (scale * 8 + (part.dy() + ((ran >> 4) / 15.0) * part.dyr()) * scale);
		Frame frame = part.frames().get(chosen);
		if (frame.sheet() == null) {
			return choice;
		}
		int[] trim = frame.sheet().trim(frame.index());
		if (trim[2] == 0) {
			return choice;
		}
		int sx = 6 + 52 * (frame.index() % 4);
		stamp(canvas, frame.sheet(), sx, trim[0], WIDTH, trim[2] * TILE, x, y + trim[1] * scale, color, part.opacity(), scale);
		if (part.stains()) {
			stains(canvas, model, frame, sx, trim, x, y + trim[1] * scale, subject, scale);
		}
		return choice;
	}

	/** RaceFrameRaw.java:41-70. Grit first, then blood, in the frame's shape. */
	private static void stains(LitCanvas canvas, PortraitModel model, Frame frame, int sx, int[] trim, int x, int y, Subject subject, int scale) {
		int blood = clampStain(subject.injury());
		int grit = clampStain(subject.grime());
		if (grit >= 0) {
			overlayStain(canvas, model.stains, grit, frame.sheet(), sx, trim, x, y, WHITE, scale);
		}
		if (blood >= 0) {
			overlayStain(canvas, model.stains, 4 + blood, frame.sheet(), sx, trim, x, y, model.blood, scale);
		}
	}

	private static int clampStain(double amount) {
		return Math.max(0, Math.min(4, (int) (amount * 4))) - 1;
	}

	/** The texture is the stain's, and the shape comes from the frame. Opacity is O99 (254), which doesn't touch the normals (SpritePoint_f.txt:25). */
	private static void overlayStain(LitCanvas canvas, Sheet stains, int stainFrame, Sheet sheet, int sx, int[] trim, int x, int y, Rgb tint, int scale) {
		if (stainFrame >= stains.frames()) {
			return;
		}
		int[] stainTrim = stains.trim(stainFrame);
		int stainX = 6 + 52 * (stainFrame % 4);
		int rows = Math.min(trim[2], stainTrim[2]) * TILE;
		for (int row = 0; row < rows; row++) {
			for (int column = 0; column < WIDTH; column++) {
				int mask = sheet.image.at(sx + column, trim[0] + row);
				int sample = stains.image.at(stainX + column, stainTrim[0] + row);
				if ((mask & 0xFF) == 0 || (sample & 0xFF) == 0) {
					continue;
				}
				double a = (sample & 0xFF) / 255.0 * (254 / 255.0) * ((mask & 0xFF) / 255.0);
				for (int dy = 0; dy < scale; dy++) {
					for (int dx = 0; dx < scale; dx++) {
						canvas.over(x + column * scale + dx, y + row * scale + dy, channel(sample >>> 24, tint.r()), channel(sample >> 16 & 0xFF, tint.g()), channel(sample >> 8 & 0xFF, tint.b()), a);
					}
				}
			}
		}
	}

	/** RCrown.java:84-104, and where it's drawn (RaiderPortrait.java:46). The bound colour is the default white. */
	private static void drawOverlay(LitCanvas canvas, Overlay overlay, int scale) {
		int ox = overlay.offX() * (WIDTH * scale) / 48;
		int oy = overlay.offY() * (24 * scale) / 24;
		stamp(canvas, overlay.sheet(), 6, 6 + 36 * overlay.frame(), WIDTH, 24, ox, scale * 8 + oy, WHITE, 255, scale);
	}

	/** One sprite through SpritePoint_f.txt: the alpha counts twice, and a part's colour is doubled and clamped. */
	private static void stamp(LitCanvas canvas, Sheet sheet, int sx, int sy, int width, int height, int x, int y, Rgb tint, int opacity, int scale) {
		int half = sheet.width() / 2;
		for (int row = 0; row < height; row++) {
			for (int column = 0; column < width; column++) {
				int sample = sheet.image.at(sx + column, sy + row);
				if ((sample & 0xFF) == 0) {
					continue;
				}
				double sampleAlpha = (sample & 0xFF) / 255.0;
				double a = sampleAlpha * sampleAlpha * (opacity / 255.0);
				int normal = sheet.image.at(sx + column + half, sy + row);
				double normalAlpha = opacity == 255 ? (normal & 0xFF) / 255.0 : 0;
				for (int dy = 0; dy < scale; dy++) {
					for (int dx = 0; dx < scale; dx++) {
						int px = x + column * scale + dx;
						int py = y + row * scale + dy;
						canvas.over(px, py, channel(sample >>> 24, tint.r()), channel(sample >> 16 & 0xFF, tint.g()), channel(sample >> 8 & 0xFF, tint.b()), a);
						canvas.normal(px, py, (normal >>> 24) / 255.0, (normal >> 16 & 0xFF) / 255.0, (normal >> 8 & 0xFF) / 255.0, normalAlpha);
					}
				}
			}
		}
	}

	/** SpritePoint_v.txt doubles the colour, so 127 is about full brightness. The diffuse target holds no more than 1. */
	static double channel(int sample, int tint) {
		return Math.min(1, sample / 255.0 * (tint / 255.0 * 2.0));
	}

	/** RColors.java:177-201. */
	static Rgb color(ColorSet set, Subject subject, PortraitSim.Pins pins, GameParser parser, Set<String> notes) throws ReadException {
		if (set == null) {
			return WHITE;
		}
		if (set.turnsGray() && subject.retired()) {
			return GREY;
		}
		Rgb color = set.slots()[activeSlot(set, subject, pins, notes)];
		if (set.turnsWhite() && subject.dead()) {
			return parser.interpolate(color, DEAD, 0.3);
		}
		return set.sickly() && subject.sick() ? colorAdd(color, SICK) : color;
	}

	/** Which of the 16 slots this person uses: a pinned one, or the game's pick. */
	static int activeSlot(ColorSet set, Subject subject, PortraitSim.Pins pins, Set<String> notes) {
		Integer pin = pins.colors().get(set.name());
		return Math.floorMod(pin != null ? pin : slot(set, subject, notes), 16);
	}

	private static int slot(ColorSet set, Subject subject, Set<String> notes) {
		if (set.pick() != null) {
			Integer equipment = switch (set.pick()) {
			case "EQUIP_BATTLE_ARMOUR_PLATE" -> subject.plate();
			case "EQUIP_BATTLE_ARMOUR_LEATHER" -> subject.leather();
			default -> null;
			};
			if (equipment != null) {
				return (int) (((double) equipment / MAX_EQUIPMENT) * 15);
			}
			notes.add(set.pick() + " isn't simulated, so " + set.name() + " is picked at random here.");
		}
		return random(subject.randomness(), 64, 31) >> (set.index() & 15) * 4;
	}

	/** RColors.java:206-236. */
	static Rgb colorAdd(Rgb a, Rgb b) {
		return new Rgb(add(a.r(), b.r()), add(a.g(), b.g()), add(a.b(), b.b()));
	}

	private static int add(int a, int b) {
		int result = a;
		if (b > 127) {
			result += b - 127;
		} else {
			result = (int) (result * (b * (1.0 / 127.0)));
		}
		return Math.min(255, result);
	}

	private static boolean passes(Part part, Subject subject, Set<String> notes) {
		return passes(part.locks(), subject, notes);
	}

	/** Lockable.java:115-127: every condition must hold. */
	static boolean passes(List<Lock> locks, Subject subject, Set<String> notes) {
		for (Lock lock : locks) {
			double value = stat(lock.stat(), subject, notes);
			boolean pass = switch (lock.comparator()) {
			case "LESS" -> value < lock.value();
			case "GREATER" -> value > lock.value();
			case "GREATERE" -> value >= lock.value();
			case "EQUAL" -> value == lock.value();
			default -> value != lock.value();
			};
			if (!pass) {
				return false;
			}
		}
		return true;
	}

	/** A stat this can't work out is noted and counts as 0. Stat names are built at run time, so it's never called wrong. */
	private static double stat(String name, Subject subject, Set<String> notes) {
		if (name.matches("RANDOM_[0-7]_[FI]")) {
			int value = random(subject.randomness(), 64 + 8 * (name.charAt(7) - '0'), 31) & 0xFF;
			return name.endsWith("F") ? value / 255.0 : value;
		}
		return switch (name) {
		case "POPULATION_AGE_F" -> subject.age();
		case "AGE_DAYS_I" -> (int) (subject.age() * 1601);
		case "APPEARANCE_DEAD_I" -> subject.dead() ? 1 : 0;
		case "CLASS_NOBLE" -> subject.noble() ? 1 : 0;
		case "CLASS_SLAVE" -> subject.slave() ? 1 : 0;
		case "CLASS_CITIZEN" -> !subject.noble() && !subject.slave() ? 1 : 0;
		case "CLASS_OTHER" -> 0;
		case "TYPE_SLAVE" -> role(subject) == 1 ? 1 : 0;
		case "TYPE_PARENT" -> role(subject) == 2 ? 1 : 0;
		case "TYPE_PARENT_SLAVE" -> role(subject) == 3 ? 1 : 0;
		case "TYPE_CHILD" -> role(subject) == 4 ? 1 : 0;
		case "TYPE_CHILD_SLAVE" -> role(subject) == 5 ? 1 : 0;
		case "TYPE_RETIREE" -> role(subject) == 6 ? 1 : 0;
		case "TYPE_NOBILITY" -> role(subject) == 7 ? 1 : 0;
		case "TYPE_CITIZEN" -> role(subject) == 0 ? 1 : 0;
		case "EQUIP_BATTLE_ARMOUR_PLATE_I" -> subject.plate();
		case "EQUIP_BATTLE_ARMOUR_LEATHER_I" -> subject.leather();
		default -> {
			notes.add(name + " isn't simulated, so it counts as 0 here.");
			yield 0;
		}
		};
	}

	private static int role(Subject subject) {
		if (subject.child()) {
			return subject.slave() ? 5 : 4;
		}
		if (subject.parent()) {
			return subject.slave() ? 3 : 2;
		}
		if (subject.slave()) {
			return 1;
		}
		if (subject.retired()) {
			return 6;
		}
		return subject.noble() ? 7 : 0;
	}
}
