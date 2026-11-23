package syx.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import syx.core.GameParser.ReadException;
import syx.core.GameParser.Rgb;
import syx.core.MapSpriteModel.Addon;
import syx.core.MapSpriteModel.Type;
import syx.core.PortraitRenderer.Subject;

/**
 * Draws one moment of a person on the map, the way HSprite, HSprites, HSpriteConst and HCorpseRenderer do. Frames
 * are cut from the sheets and turned into eight directions exactly as the sprite composer does
 * (ComposerSources.Singles, ComposerUtil.paste and pasteRotated), then drawn with the sprite shader's colour doubling
 * and lit against their normals. Sizes here are the sheet's own pixels; the game draws each at 4 times that
 * (C.SCALE).
 */
final class MapSpriteRenderer {

	/** The picture is this many sheet pixels square, so lying sprites and shadows fit. */
	static final int CANVAS = 40;
	/** Where a standing sprite's 24 pixel cell starts. */
	private static final int ORIGIN = 8;
	/** CLAY.off: a lying sprite's 32 pixel cell is moved back by half the difference (HSpriteConst.java:106). */
	private static final int LAY_OFF = (24 - 32) / 2;

	/** HSpriteConst.java:20-38: the rows of the standing sheet, each one pair of frames turned into 8 directions. */
	static final int FEET_NONE = 0;
	static final int FEET_RIGHT = 1;
	static final int FEET_RIGHT2 = 2;
	static final int FEET_LEFT = 3;
	static final int FEET_LEFT2 = 4;
	static final int TUNIC = 5;
	static final int TORSO_STILL = 6;
	static final int TORSO_RIGHT = 7;
	static final int TORSO_RIGHT2 = 8;
	static final int TORSO_RIGHT3 = 9;
	static final int TORSO_LEFT = 10;
	static final int TORSO_LEFT2 = 11;
	static final int TORSO_LEFT3 = 12;
	static final int TORSO_CARRY = 13;
	static final int TORSO_OUT = 14;
	static final int TORSO_OUT2 = 15;
	static final int HEAD = 16;
	static final int SHADOW = 17;
	/** HSpriteConst.java:96-102: the lying sheet's rows. */
	private static final int LAY_PANTS = 0;
	private static final int LAY_TORSO = 1;
	private static final int LAY_ARMS = 2;
	private static final int LAY_HEAD = 3;
	private static final int LAY_SHADOW = 4;

	/**
	 * One layout of ComposerSources.Singles: cells of a size with a 6 pixel margin, so a cell's art starts 6 in.
	 * Pair p is cells 2p and 2p+1, counted across then down.
	 */
	record Grid(int x, int y, int columns, int size) {
		int cellX(int cell) {
			return x + 6 + cell % columns * (size + 6);
		}

		int cellY(int cell) {
			return y + 6 + cell / columns * (size + 6);
		}
	}

	/** RaceSheet.java:22: 18 pairs of 24 pixel cells, two to a row. */
	static final Grid STAND = new Grid(0, 0, 2, 24);
	/** RaceSheet.java:36: beside them, 32 pixel cells four to a row. */
	static final Grid LAY = new Grid(66, 0, 4, 32);
	/** RType.java:48: a skeleton sheet's 32 pixel cells, four to a row. */
	static final Grid SKELETON = new Grid(0, 0, 4, 32);
	/** RAppearence.java:71: a sleep sheet's 32 pixel cells, two to a row. */
	static final Grid SLEEP = new Grid(0, 0, 2, 32);
	/** RExtras.java:27: tools, rows 0 to 5. */
	static final Grid TOOL = new Grid(0, 0, 2, 24);
	/** RExtras.java:35: water, rows 0 to 3, beside the tools. */
	static final Grid WATER = new Grid(66, 0, 2, 24);
	/** RaceSprites.java:31-48: blood in rows 0 to 7 and grit in rows 8 to 15. */
	static final Grid STAIN = new Grid(0, 0, 2, 24);
	/**
	 * RaceSprites.java:50-69: lying blood in rows 0 to 7 beside them. Lying grit restarts at row 0 (:63), so it uses the
	 * same frames as lying blood.
	 */
	static final Grid LAY_STAIN = new Grid(66, 0, 2, 32);

	/** RAddon.java:41-62: an addon's pair of standing cells, and its lying pair beside them. */
	static Grid addonStand(int index) {
		return new Grid(66, 194 + 44 * index, 2, 24);
	}

	static Grid addonLay(int index) {
		return new Grid(132, 194 + 44 * index, 2, 32);
	}

	/** One direction of one frame, as the composer leaves it: RGBA art, and its normal or 0 where none was written. */
	private record Tile(int size, int[] art, int[] normal) {
	}

	private record TileKey(PngCodec.Image image, Grid grid, int pair, int dir) {
	}

	/** Shading of a moment. Colours are as the game holds them, half what a file writes. */
	record Look(Rgb skin, Rgb leg, Rgb clothes, boolean naked, Map<Addon, Rgb> addons) {
	}

	/** RColors' stand-in for a missing colour, which the shader doubles to full (RColors.java:26). */
	private static final Rgb WHITE = PortraitRenderer.WHITE;
	/** HCorpseRenderer.java:24. */
	static final Rgb DECAY = new Rgb(48, 24, 12);
	/** OPACITY.O75, which stains and water are drawn at (HSprite.java:230, HSprites.java:658). */
	private static final double O75 = 0.75;
	/** OPACITY.O99, which a corpse's stains are drawn at (HCorpseRenderer.java:83). */
	private static final double O99 = 254 / 255.0;
	/**
	 * How dark the stand-in shadow is. A soft shadow takes away one of the sun's two passes (Ambient.java:78-84,
	 * ShadowBatch.java:150-164), which on flat ground at noon leaves about two thirds of the light.
	 */
	private static final double SHADOW_ALPHA = 0.35;
	/**
	 * LightShadows.java:60-65 at noon with full shadows (LSettings.java:91): (0.5 + 3 - 3 x 40/50) x 2 = 2.2 screen
	 * pixels per unit of height, straight down the screen, as the sun is at 270 degrees.
	 */
	private static final double SHADOW_LENGTH = 2.2;
	private static final int SCALE = 4;

	private final Map<TileKey, Tile> tiles = new HashMap<>();

	/** Cuts one direction of one pair from a sheet, as ComposerUtil.copy, paste and pasteRotated do. */
	private Tile tile(PngCodec.Image image, Grid grid, int pair, int dir) {
		return tiles.computeIfAbsent(new TileKey(image, grid, pair, dir), key -> {
			int size = grid.size();
			int half = image.width() / 2;
			int cell = pair * 2 + (dir & 1);
			int rotations = (dir >> 1) & 3;
			int sx = grid.cellX(cell);
			int sy = grid.cellY(cell);
			int[] art = new int[size * size];
			int[] normal = new int[size * size];
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					int px = sx + x;
					int py = sy + y;
					if (px >= half || py >= image.height()) {
						continue;
					}
					int c = image.at(px, py);
					if ((c & 0xFF) == 0) {
						continue;
					}
					int nc = image.at(px + half, py);
					int dx = x;
					int dy = y;
					for (int r = 0; r < rotations; r++) {
						int odx = dx;
						dx = size - dy - 1;
						dy = odx;
						int re = (256 - (nc >> 16)) & 0xFF;
						int gr = (nc >> 24) & 0xFF;
						nc &= 0x0000FFFF;
						nc |= gr << 16;
						nc |= re << 24;
					}
					art[dy * size + dx] = c;
					// paste writes a normal with any alpha; pasteRotated only one whose red isn't 0 (ComposerUtil.java:250-253,350-351).
					boolean writes = rotations == 0 ? (nc & 0xFF) != 0 : (nc & 0xFF000000) != 0;
					normal[dy * size + dx] = writes ? nc : 0;
				}
			}
			return new Tile(size, art, normal);
		});
	}

	/** A canvas for one moment, at a scale. */
	static LitCanvas canvas(int scale) {
		return new LitCanvas(CANVAS * scale, CANVAS * scale);
	}

	/** One sprite through SpritePoint_f.txt, as the Race Portrait Sim draws one: the alpha counts twice, and the colour is doubled. */
	private void stamp(LitCanvas canvas, PngCodec.Image image, Grid grid, int pair, int dir, int ox, int oy, Rgb tint, double opacity, int scale) {
		Tile tile = tile(image, grid, pair, dir);
		int size = tile.size();
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				int sample = tile.art()[y * size + x];
				if ((sample & 0xFF) == 0) {
					continue;
				}
				double sampleAlpha = (sample & 0xFF) / 255.0;
				double a = sampleAlpha * sampleAlpha * opacity;
				int normal = tile.normal()[y * size + x];
				double normalAlpha = opacity >= 1 ? (normal & 0xFF) / 255.0 : 0;
				for (int py = 0; py < scale; py++) {
					for (int px = 0; px < scale; px++) {
						int cx = (ox + x) * scale + px;
						int cy = (oy + y) * scale + py;
						canvas.over(cx, cy, PortraitRenderer.channel(sample >>> 24, tint.r()), PortraitRenderer.channel(sample >> 16 & 0xFF, tint.g()), PortraitRenderer.channel(sample >> 8 & 0xFF, tint.b()), a);
						canvas.normal(cx, cy, (normal >>> 24) / 255.0, (normal >> 16 & 0xFF) / 255.0, (normal >> 8 & 0xFF) / 255.0, normalAlpha);
					}
				}
			}
		}
	}

	/**
	 * TILE_SHEET.renderTextured: a texture drawn in another frame's shape. The texture's own pixels are tinted, and the
	 * shape only masks them. Drawn below full opacity, so no normals are written.
	 */
	private void textured(LitCanvas canvas, PngCodec.Image shapeImage, Grid shapeGrid, int shapePair, int dir, PngCodec.Image textureImage, Grid textureGrid, int texturePair, int textureDir, int ox, int oy, Rgb tint, double opacity,
			int scale) {
		Tile shape = tile(shapeImage, shapeGrid, shapePair, dir);
		Tile texture = tile(textureImage, textureGrid, texturePair, textureDir);
		int size = Math.min(shape.size(), texture.size());
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				int mask = shape.art()[y * shape.size() + x];
				int sample = texture.art()[y * texture.size() + x];
				if ((mask & 0xFF) == 0 || (sample & 0xFF) == 0) {
					continue;
				}
				double a = (sample & 0xFF) / 255.0 * ((mask & 0xFF) / 255.0) * opacity;
				for (int py = 0; py < scale; py++) {
					for (int px = 0; px < scale; px++) {
						canvas.over((ox + x) * scale + px, (oy + y) * scale + py, PortraitRenderer.channel(sample >>> 24, tint.r()), PortraitRenderer.channel(sample >> 16 & 0xFF, tint.g()),
								PortraitRenderer.channel(sample >> 8 & 0xFF, tint.b()), a);
					}
				}
			}
		}
	}

	/**
	 * A stand-in for the shadow pass: the frame's shape, darkened and drawn out away from the sun, which at noon is
	 * straight down the screen. How dark it is is approximate.
	 */
	private void shadow(LitCanvas canvas, PngCodec.Image image, Grid grid, int pair, int dir, int ox, int oy, int height, int scale) {
		Tile tile = tile(image, grid, pair, dir);
		int size = tile.size();
		int length = (int) Math.round(height * SHADOW_LENGTH * scale / SCALE);
		boolean[] mask = new boolean[canvas.width * canvas.height];
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				if ((tile.art()[y * size + x] & 0xFF) == 0) {
					continue;
				}
				for (int py = 0; py < scale + length; py++) {
					for (int px = 0; px < scale; px++) {
						int cx = (ox + x) * scale + px;
						int cy = (oy + y) * scale + py;
						if (cx >= 0 && cy >= 0 && cx < canvas.width && cy < canvas.height) {
							mask[cy * canvas.width + cx] = true;
						}
					}
				}
			}
		}
		for (int i = 0; i < mask.length; i++) {
			if (mask[i]) {
				canvas.over(i % canvas.width, i / canvas.width, 0, 0, 0, SHADOW_ALPHA);
			}
		}
	}

	/** Grime and injury: none at 0, else one of 8 rows by amount, and one of 8 frames in it by the person's roll (HSpriteConst.java:82-89). */
	private static int stainRow(double amount) {
		return amount <= 0 ? -1 : (int) (amount * 0x07);
	}

	/**
	 * HSprite.Standing.render (HSprite.java:119-240), and renderWater (:263-312) when swimming.
	 *
	 * @param feet the feet row, used only out of water
	 * @param tool the tool row of the extra sheet, or -1 for none
	 * @param water the water frame, 0 to 3, used only when swimming
	 */
	void standing(LitCanvas canvas, MapSpriteModel model, Type type, Look look, int dir, int torso, int feet, int tool, boolean swimming, int water, double grime, double injury, int ran, int scale) {
		PngCodec.Image sheet = type.sheet();
		if (!swimming) {
			shadow(canvas, sheet, STAND, SHADOW, dir, ORIGIN, ORIGIN, 10, scale);
			stamp(canvas, sheet, STAND, feet, dir, ORIGIN, ORIGIN, look.leg(), 1, scale);
			if (tool >= 0) {
				stamp(canvas, model.extra, TOOL, tool, dir, ORIGIN, ORIGIN, WHITE, 1, scale);
			}
		}
		stamp(canvas, sheet, STAND, torso, dir, ORIGIN, ORIGIN, look.skin(), 1, scale);
		if (!look.naked()) {
			stamp(canvas, sheet, STAND, TUNIC, dir, ORIGIN, ORIGIN, look.clothes(), 1, scale);
		}
		addons(canvas, type, look, true, false, dir, scale);
		stamp(canvas, sheet, STAND, HEAD, dir, ORIGIN, ORIGIN, look.skin(), 1, scale);
		addons(canvas, type, look, false, false, dir, scale);
		if (swimming) {
			textured(canvas, sheet, STAND, torso, dir, model.extra, WATER, water & 3, dir, ORIGIN, ORIGIN, WHITE, O75, scale);
			return;
		}
		int grit = stainRow(grime);
		if (grit >= 0) {
			textured(canvas, sheet, STAND, torso, dir, model.overlays, STAIN, 8 + grit, ran & 7, ORIGIN, ORIGIN, WHITE, O75, scale);
		}
		int blood = stainRow(injury);
		if (blood >= 0) {
			textured(canvas, sheet, STAND, torso, dir, model.overlays, STAIN, blood, ran & 7, ORIGIN, ORIGIN, model.blood, O75, scale);
		}
	}

	private void addons(LitCanvas canvas, Type type, Look look, boolean below, boolean lying, int dir, int scale) {
		for (Addon addon : type.addons()) {
			Rgb color = look.addons().get(addon);
			if (addon.belowHead() == below && color != null) {
				Grid grid = lying ? addonLay(addon.index()) : addonStand(addon.index());
				int origin = lying ? ORIGIN + LAY_OFF : ORIGIN;
				stamp(canvas, type.sheet(), grid, 0, dir, origin, origin, color, 1, scale);
			}
		}
	}

	/** HSprites.LAY.render (HSprites.java:597-667). Sleeping draws this naked, then the blanket (:554-570). */
	void lying(LitCanvas canvas, MapSpriteModel model, Type type, Look look, int dir, double grime, double injury, int ran, int scale) {
		PngCodec.Image sheet = type.sheet();
		int o = ORIGIN + LAY_OFF;
		shadow(canvas, sheet, LAY, LAY_SHADOW, dir, o, o, 5, scale);
		addons(canvas, type, look, true, true, dir, scale);
		stamp(canvas, sheet, LAY, LAY_PANTS, dir, o, o, look.naked() ? look.skin() : look.leg(), 1, scale);
		stamp(canvas, sheet, LAY, LAY_ARMS, dir, o, o, look.skin(), 1, scale);
		stamp(canvas, sheet, LAY, LAY_HEAD, dir, o, o, look.skin(), 1, scale);
		stamp(canvas, sheet, LAY, LAY_TORSO, dir, o, o, look.naked() ? look.skin() : look.clothes(), 1, scale);
		addons(canvas, type, look, false, true, dir, scale);
		int blood = stainRow(injury);
		if (blood >= 0) {
			textured(canvas, sheet, LAY, LAY_SHADOW, dir, model.overlays, LAY_STAIN, blood, ran & 7, o, o, model.blood, O75, scale);
		}
		int grit = stainRow(grime);
		if (grit >= 0) {
			textured(canvas, sheet, LAY, LAY_SHADOW, dir, model.overlays, LAY_STAIN, grit, ran & 7, o, o, WHITE, O75, scale);
		}
	}

	/** The blanket over a sleeper: one of the sleep sheet's first two rows, picked per person (HSprites.java:564-566). */
	void blanket(LitCanvas canvas, MapSpriteModel model, int dir, int row, int scale) {
		int o = ORIGIN + LAY_OFF;
		int rows = model.sleep.height() / 38;
		if (row < rows) {
			stamp(canvas, model.sleep, SLEEP, row, dir, o, o, WHITE, 1, scale);
		}
	}

	/**
	 * HCorpseRenderer.renderCorpse (HCorpseRenderer.java:45-96). Each colour is mixed toward a dark brown by decay. An
	 * addon is drawn mixed, then drawn again unmixed on top (RAddon.java:96-107), so addons don't seem to decay.
	 */
	void corpse(LitCanvas canvas, MapSpriteModel model, Type type, Look look, int dir, double decay, double grime, double injury, int ran, GameParser parser, int scale) throws ReadException {
		PngCodec.Image sheet = type.sheet();
		int o = ORIGIN + LAY_OFF;
		shadow(canvas, sheet, LAY, LAY_SHADOW, dir, o, o, 3, scale);
		stamp(canvas, sheet, LAY, LAY_PANTS, dir, o, o, parser.interpolate(look.leg(), DECAY, decay), 1, scale);
		Rgb skin = parser.interpolate(look.skin(), DECAY, decay);
		stamp(canvas, sheet, LAY, LAY_ARMS, dir, o, o, skin, 1, scale);
		decayedAddons(canvas, type, look, true, dir, decay, parser, scale);
		stamp(canvas, sheet, LAY, LAY_HEAD, dir, o, o, skin, 1, scale);
		stamp(canvas, sheet, LAY, LAY_TORSO, dir, o, o, parser.interpolate(look.clothes(), DECAY, decay), 1, scale);
		decayedAddons(canvas, type, look, false, dir, decay, parser, scale);
		int blood = stainRow(injury);
		if (blood >= 0) {
			textured(canvas, sheet, LAY, LAY_SHADOW, dir, model.overlays, LAY_STAIN, blood, ran & 7, o, o, model.blood, O99, scale);
		}
		int grit = stainRow(grime);
		if (grit >= 0) {
			textured(canvas, sheet, LAY, LAY_SHADOW, dir, model.overlays, LAY_STAIN, grit, ran & 7, o, o, WHITE, O99, scale);
		}
	}

	private void decayedAddons(LitCanvas canvas, Type type, Look look, boolean below, int dir, double decay, GameParser parser, int scale) throws ReadException {
		int o = ORIGIN + LAY_OFF;
		for (Addon addon : type.addons()) {
			Rgb color = look.addons().get(addon);
			if (addon.belowHead() == below && color != null) {
				stamp(canvas, type.sheet(), addonLay(addon.index()), 0, dir, o, o, parser.interpolate(color, DECAY, decay), 1, scale);
				stamp(canvas, type.sheet(), addonLay(addon.index()), 0, dir, o, o, color, 1, scale);
			}
		}
	}

	/**
	 * HCorpseRenderer.renderSkelleton (HCorpseRenderer.java:27-43): one of the first two rows, then one of the next four,
	 * each picked by the corpse's roll, in white.
	 */
	void skeleton(LitCanvas canvas, PngCodec.Image sheet, int dir, int ran, int scale) {
		int o = ORIGIN + LAY_OFF;
		shadow(canvas, sheet, SKELETON, ran & 1, dir, o, o, 2, scale);
		shadow(canvas, sheet, SKELETON, 2 + (ran & 3), dir, o, o, 2, scale);
		stamp(canvas, sheet, SKELETON, ran & 1, dir, o, o, WHITE, 1, scale);
		stamp(canvas, sheet, SKELETON, 2 + (ran & 3), dir, o, o, WHITE, 1, scale);
	}

	/**
	 * The colours a person is drawn in. Skin, legs and addons are picked as if alive, because the map always asks
	 * RColors for the living colour (StatsAppearance.java:124,226, HSprite.java:215,224).
	 *
	 * @param alive the person, with dead false
	 * @param subject the person as they are, for the addons' conditions
	 */
	static Look look(MapSpriteModel model, Type type, Subject alive, Subject subject, PortraitSim.Pins pins, int clothesLevel, boolean naked, boolean enemy, boolean sick, GameParser parser, Set<String> notes) throws ReadException {
		Rgb skin = PortraitRenderer.color(type.skin(), alive, pins, parser, notes);
		if (sick) {
			// colorSkin adds the disease's colour whatever TURNS_SICKLY says (StatsAppearance.java:123-132).
			skin = PortraitRenderer.colorAdd(skin, PortraitRenderer.SICK);
		}
		Rgb leg = PortraitRenderer.color(type.leg(), alive, pins, parser, notes);
		Rgb clothes = clothes(model, subject, clothesLevel, enemy, parser);
		Map<Addon, Rgb> addons = new java.util.LinkedHashMap<>();
		for (Addon addon : type.addons()) {
			if (PortraitRenderer.passes(addon.locks(), subject, notes)) {
				addons.put(addon, addon.color() == null ? WHITE : PortraitRenderer.color(addon.color(), alive, pins, parser, notes));
			}
		}
		return new Look(skin, leg, clothes, naked, addons);
	}

	/** StatsAppearance.java:192-200, each shaded by half. */
	private static final int[][] SLAVE_CLOTHES = { { 114, 67, 36 }, { 119, 67, 36 }, { 114, 72, 36 }, { 114, 67, 41 }, { 119, 72, 36 }, { 114, 72, 41 }, { 119, 67, 41 }, { 119, 72, 41 } };
	/** StatsAppearance.java:204-212. */
	private static final int[][] ENEMY_CLOTHES = { { 20, 20, 20 }, { 25, 20, 20 }, { 20, 25, 20 }, { 20, 20, 25 }, { 25, 25, 20 }, { 20, 25, 25 }, { 25, 20, 25 }, { 30, 20, 20 } };

	/** StatsAppearance.colorClothes (:177-190). Tourists' colours aren't offered. */
	private static Rgb clothes(MapSpriteModel model, Subject subject, int level, boolean enemy, GameParser parser) throws ReadException {
		int[] ints = subject.randomness();
		if (enemy) {
			int[] pick = ENEMY_CLOTHES[PortraitRenderer.random(ints, 0, 3) & 7];
			return new Rgb(pick[0], pick[1], pick[2]);
		}
		if (subject.slave() && !subject.child()) {
			int[] pick = SLAVE_CLOTHES[PortraitRenderer.random(ints, 3, 3) & 7];
			return parser.shade(new Rgb(pick[0], pick[1], pick[2]), 0.5);
		}
		if (subject.noble() && !subject.child()) {
			return new Rgb(84, 0, 127);
		}
		int levels = model.clothes[0].length;
		return model.clothes[(PortraitRenderer.random(ints, 6, 3) & 7) & 0x0F][Math.floorMod(level, levels)];
	}
}
