package syx.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Generates and removes the normal maps in a mod's sprite sheets. Every composer sheet keeps its normal map in
 * its right half (ComposerUtil.java:61-63,108), so a sheet is only ever edited in place, and only inside the
 * frames its profile names. Grid lines, labels and the art itself are left exactly as they were.
 *
 * <p>The folder decides a sheet's layout, but not its style: vanilla's race sheets are shaded differently from
 * each other. A profile therefore names one target style, measured from vanilla's pixels and recorded here.
 */
public final class NormalGen {

	/** What a frame's normal is made from. */
	enum Role {
		/** A rounded edge around this frame's own art. */
		PIECE,
		/** A rounded edge around the whole body, so that layered pieces don't draw seams across it. */
		WHOLE_BODY,
		/** The shape every vanilla sheet of this kind shares at this position, read from the player's own install. */
		STRUCTURE
	}

	/** One frame in the left half. Its normal is at the same place in the right half. */
	record Frame(int x, int y, int size, Role role, double[][] tilts, String section, int shadowX, int shadowY) {
		static Frame of(int x, int y, int size, double[][] tilts, String section) {
			return new Frame(x, y, size, Role.PIECE, tilts, section, -1, -1);
		}
	}

	/**
	 * One kind of sheet.
	 *
	 * @param path which files are this kind, relative to the mod
	 * @param width the exact width the game requires
	 * @param minHeight the height the game requires at least
	 * @param structureFolder for {@link Role#STRUCTURE} frames, the folder of vanilla sheets in data.zip, or null
	 * @param variable a sheet whose frames depend on its size, or null if they're always the same
	 */
	record Profile(String kind, String path, int width, int minHeight, List<Frame> frames, List<Section> sections, String structureFolder, Variable variable) {
		Profile(String kind, String path, int width, int minHeight, List<Frame> frames, List<Section> sections, String structureFolder) {
			this(kind, path, width, minHeight, frames, sections, structureFolder, null);
		}

		List<Frame> framesFor(int imageWidth, int imageHeight) {
			return variable == null ? frames : variable.frames(imageWidth, imageHeight);
		}
	}

	/** A sheet the game divides up according to its size. */
	interface Variable {
		/** Why the game wouldn't accept this size, or null if it would. */
		String problem(int width, int height);

		List<Frame> frames(int width, int height);
	}

	/**
	 * How one section of a sheet is shaded, as percentages of the sheet's own style.
	 *
	 * @param strength how far the normals lean, where 100 is the style's own lean and 0 is flat
	 * @param sharpness how quickly the lean fades going inwards from the edge, where 100 is the style's own fade.
	 *            Higher keeps the edge and flattens the middle, and lower carries the lean further in
	 */
	public record Tuning(int strength, int sharpness) {
		public static final Tuning DEFAULT = new Tuning(100, 100);
	}

	public static final int MIN_STRENGTH = 0;
	public static final int MAX_STRENGTH = 200;
	public static final int MIN_SHARPNESS = 20;
	public static final int MAX_SHARPNESS = 300;

	/**
	 * A part of a sheet whose shading can be changed on its own.
	 *
	 * @param hasSharpness whether Sharpness means anything here. It doesn't for a shape read from vanilla, which has no fade to bend
	 */
	public record Section(String id, String label, Tuning defaults, boolean hasSharpness) {
		static Section of(String id, String label) {
			return new Section(id, label, Tuning.DEFAULT, true);
		}
	}

	public enum TrimMode {
		/** Only normals beside transparent art, which the game never draws. */
		OUTSIDE_ART,
		/** Every normal in every frame. */
		ALL
	}

	/**
	 * A PNG in the mod.
	 *
	 * @param path relative to the mod, with forward slashes
	 * @param kind what sort of sheet it is, or null if normals can't be generated for it
	 * @param whyNot why they can't, or null if they can
	 * @param hasNormals whether any art in a frame already has a shaped normal beside it
	 */
	public record Listed(String path, String kind, String whyNot, boolean hasNormals) {
		public boolean supported() {
			return kind != null;
		}
	}

	/**
	 * A sheet read from the mod.
	 *
	 * @param argb each pixel as 0xAARRGGBB, row by row from the top
	 * @param status the sentence that describes it, the same for every front end
	 * @param revision identifies these exact bytes, so a change on disk can be noticed
	 */
	public record Opened(Listed listed, int width, int height, int[] argb, byte[] file, String status, String revision) {
	}

	/**
	 * What a generate or trim did.
	 *
	 * @param changed whether the file was rewritten
	 * @param frames how many frames were given normals, or lost them
	 * @param pixels how many pixels of the normal half changed
	 */
	public record Changed(String path, boolean changed, int frames, int pixels, String message) {
	}

	/** Why it won't be done. Nothing was changed. */
	public static final class Refused extends Exception {
		private static final long serialVersionUID = 1L;

		Refused(String message) {
			super(message);
		}
	}

	private static final String PNG = "[^/]+\\.(?i:png)";
	private static final String SPRITES = "V\\d+/assets/sprite/";
	private static final String STRUCTURE_ZIP = "data/assets/sprite/settlement/structure/";
	/**
	 * Tilt by how deep a shape is at its deepest point here (the outer index, less one) and how far a pixel is from
	 * the edge (the inner index, less one), averaged over eight vanilla race sheets. Vanilla's shapes have no flat top:
	 * the tilt falls steadily from the edge to the ridge, so the two sides of a body stay clearly different colours.
	 * Standing torsos and heads are tilted more strongly than everything else. The last value of the deepest row is
	 * an estimate, because only 26 pixels were measured for it and they disagreed with the rest of the row.
	 */
	private static final double[][] STRONG = { { 0.51 }, { 0.60, 0.36 }, { 0.61, 0.48, 0.32 }, { 0.66, 0.52, 0.40, 0.31 }, { 0.76, 0.61, 0.53, 0.42, 0.35 } };
	/** The same for feet, lying bodies and addons. */
	private static final double[][] SOFT = { { 0.43 }, { 0.53, 0.23 }, { 0.55, 0.27, 0.22 }, { 0.54, 0.36, 0.23, 0.09 }, { 0.57, 0.34, 0.23, 0.12, 0.04 } };
	/** Each of these is measured the same way, from all of vanilla's sheets of that kind. */
	private static final double[][] ANIMAL_YOUNG = { { 0.53 }, { 0.59, 0.39 }, { 0.63, 0.41, 0.19 }, { 0.64, 0.46, 0.23, 0.13 }, { 0.59, 0.48, 0.32, 0.17, 0.10 } };
	private static final double[][] ANIMAL = { { 0.30 }, { 0.46, 0.33 }, { 0.50, 0.27, 0.13 }, { 0.51, 0.31, 0.17, 0.09 }, { 0.59, 0.40, 0.26, 0.14, 0.11 }, { 0.54, 0.37, 0.27, 0.22, 0.10 } };
	private static final double[][] WEAPON = { { 0.31 }, { 0.31, 0.05 } };
	private static final double[][] RESOURCE = { { 0.45 }, { 0.49, 0.24 }, { 0.48, 0.21, 0.08 }, { 0.47, 0.15, 0.09, 0.12 }, { 0.46, 0.10, 0.07, 0.10, 0.06 }, { 0.47, 0.11, 0.08, 0.13, 0.09, 0.10 } };
	private static final double[][] DEBRIS = { { 0.53 }, { 0.41, 0.08 } };
	private static final double[][] CROP = { { 0.40 }, { 0.44, 0.22 }, { 0.28, 0.10 } };
	private static final double[][] SKELETON = { { 0.45 }, { 0.43, 0.16 }, { 0.43, 0.44, 0.32 }, { 0.58, 0.41, 0.32, 0.37 }, { 0.61, 0.51, 0.29, 0.35 } };
	/** From one vanilla sheet, the only one there is. */
	private static final double[][] SLEEP = { { 0.66 }, { 0.71, 0.55 }, { 0.66, 0.49, 0.35 }, { 0.62, 0.44, 0.35, 0.29 }, { 0.64, 0.43, 0.34, 0.30, 0.23 }, { 0.61, 0.47, 0.42, 0.32, 0.20, 0.11 } };
	private static final double[][] INFANT = { { 0.59 }, { 0.58, 0.59 }, { 0.50, 0.38, 0.24 }, { 0.62, 0.42, 0.22, 0.12 }, { 0.50, 0.23, 0.21, 0.16, 0.09 }, { 0.48, 0.21, 0.20, 0.21, 0.15 } };
	private static final double[][] TILE_1X1 = { { 0.42 }, { 0.42, 0.18 }, { 0.45, 0.17, 0.11 }, { 0.44, 0.16, 0.12, 0.10 }, { 0.45, 0.17, 0.12, 0.09, 0.07 }, { 0.43, 0.17, 0.11, 0.08, 0.08, 0.07 } };
	private static final double[][] TILE_2X2 = { { 0.39 }, { 0.48, 0.22 }, { 0.57, 0.36, 0.25 }, { 0.41, 0.21, 0.19, 0.08 }, { 0.49, 0.32, 0.21, 0.11, 0.13 }, { 0.42, 0.20, 0.16, 0.12, 0.08, 0.06 } };
	private static final double[][] TILE_3X3 = { { 0.20 }, { 0.31, 0.27 }, { 0.59, 0.45, 0.46 }, { 0.62, 0.44, 0.25, 0.25 }, { 0.61, 0.41, 0.27, 0.20, 0.25 }, { 0.47, 0.36, 0.29, 0.27, 0.25, 0.20 } };
	/** These start at Strength 0, because vanilla's sheets of these kinds match flat as well as any shape. They're here for a player who raises it. */
	private static final double[][] BOX = { { 0.37 }, { 0.28, 0.10 }, { 0.46, 0.06, 0.04 }, { 0.30, 0.14, 0.12, 0.15 }, { 0.35, 0.07, 0.06, 0.12, 0.12 }, { 0.25, 0.04, 0.04, 0.05, 0.07, 0.02 } };
	private static final double[][] COMBO = { { 0.32 }, { 0.24, 0.15 }, { 0.27, 0.12, 0.11 }, { 0.21, 0.09, 0.07, 0.05 }, { 0.21, 0.09, 0.07, 0.05, 0.11 }, { 0.26, 0.11, 0.08, 0.06, 0.10, 0.07 } };
	private static final double[][] TEXTURE = { { 0.13 }, { 0.08, 0.01 }, { 0.08, 0.01, 0.01 }, { 0.09, 0.01, 0.01, 0.02 }, { 0.09, 0.01, 0.02, 0.02, 0.05 }, { 0.09, 0.01, 0.02, 0.02, 0.03, 0.02 } };
	private static final Tuning STARTS_FLAT = new Tuning(0, 100);
	private static final List<Profile> PROFILES = List.of(raceProfile(), animalProfile(), weaponProfile(), resourceProfile(), debrisProfile(), cropProfile(), buildingProfile(),
			skeletonProfile(), sleepProfile(), infantProfile(), minableProfile(), tile1x1Profile(), blockProfile("2x2", 2, TILE_2X2, Tuning.DEFAULT), blockProfile("3x3", 3, TILE_3X3, Tuning.DEFAULT), blockProfile("box", 4, BOX, STARTS_FLAT), comboProfile(), textureProfile());
	private static final Map<Path, Structure> STRUCTURES = new ConcurrentHashMap<>();

	private static final double MAX_TILT = 0.95;
	/** How close to 128 a normal's R and G must be to count as flat. Vanilla's flat areas stay within this. */
	private static final int FLAT_MARGIN = 3;

	private NormalGen() {
	}

	/**
	 * RaceSheet.java:23-26,36-39 and RAddon.java:41-49,58-59 give the frames. HSpriteConst.java:20-38,96-102 says
	 * standing row 17 and lying pair 4 are shadows, and lying pairs 0 to 3 are pieces of one lying body.
	 */
	private static Profile raceProfile() {
		List<Section> sections = new ArrayList<>();
		sections.add(Section.of("feet", "Feet"));
		sections.add(Section.of("tunic", "Tunic"));
		sections.add(Section.of("torso", "Torso"));
		sections.add(Section.of("carry", "Torso, carrying"));
		sections.add(Section.of("torso_out", "Torso, arms out"));
		sections.add(Section.of("head", "Head"));
		// A standing shadow is flat on 4 of the 5 vanilla race sheets measured, so it starts with no lean.
		sections.add(new Section("shadow", "Shadow", new Tuning(0, 100), true));
		String[] lying = { "pants", "torso", "arms", "head", "shadow", "extra" };
		String[] lyingLabels = { "Lying pants", "Lying torso", "Lying arms", "Lying head", "Lying shadow", "Lying, unnamed" };
		for (int i = 0; i < lying.length; i++) {
			sections.add(Section.of("lying_" + lying[i], lyingLabels[i]));
		}
		for (int addon = 0; addon < 8; addon++) {
			sections.add(Section.of("addon_" + addon, "Addon " + addon));
		}
		List<Frame> frames = new ArrayList<>();
		for (int row = 0; row < 18; row++) {
			for (int column = 0; column < 2; column++) {
				frames.add(Frame.of(6 + 30 * column, 6 + 30 * row, 24, row < 5 || row == 17 ? SOFT : STRONG, standingSection(row)));
			}
		}
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 4; column++) {
				int x = 72 + 38 * column;
				int y = 6 + 38 * row;
				int pair = row * 2 + column / 2;
				String section = "lying_" + lying[pair];
				if (pair <= 4) {
					// Unlike a standing shadow, vanilla's lying shadow is shaped like the rest of the lying body.
					frames.add(new Frame(x, y, 32, Role.WHOLE_BODY, SOFT, section, 72 + 38 * (column % 2), 6 + 38 * 2));
				} else {
					frames.add(Frame.of(x, y, 32, SOFT, section));
				}
			}
		}
		for (int addon = 0; addon < 8; addon++) {
			int y = 200 + 44 * addon;
			String section = "addon_" + addon;
			frames.add(Frame.of(72, y, 24, SOFT, section));
			frames.add(Frame.of(102, y, 24, SOFT, section));
			frames.add(Frame.of(138, y, 32, SOFT, section));
			frames.add(Frame.of(176, y, 32, SOFT, section));
		}
		return new Profile("Race sheet (city map)", SPRITES + "race/" + PNG, 448, 546, List.copyOf(frames), List.copyOf(sections), null);
	}

	/** AnimalSpecies.java:113,119-121 gives 2 columns of 12 rows of 32 pixels, and Sprite.java:18-30 names the rows. */
	private static Profile animalProfile() {
		String[] rows = { "baby", "baby", "baby", "standing", "walking", "walking", "laying", "eating", "parts", "parts", "rotten", "bones" };
		List<Frame> frames = new ArrayList<>();
		for (int row = 0; row < rows.length; row++) {
			// Vanilla leans babies and body parts further than the rest.
			double[][] tilts = row < 3 || row == 8 || row == 9 ? ANIMAL_YOUNG : ANIMAL;
			grid(frames, 6, 6 + 38 * row, 2, 1, 32, 38, tilts, rows[row]);
		}
		return new Profile("Animal sheet", SPRITES + "animal/" + PNG, 164, 462, List.copyOf(frames),
				List.of(Section.of("baby", "Baby"), Section.of("standing", "Standing"), Section.of("walking", "Walking"), Section.of("laying", "Laying"), Section.of("eating", "Eating"), Section.of("parts", "Body parts"), Section.of("rotten", "Rotten"), Section.of("bones", "Bones")), null);
	}

	/** EquipBattle.java:184-190 gives one row of 2 frames of 24 pixels. */
	private static Profile weaponProfile() {
		List<Frame> frames = new ArrayList<>();
		grid(frames, 6, 6, 2, 1, 24, 30, WEAPON, "weapon");
		return new Profile("Race weapon sheet", SPRITES + "race/battle/" + PNG, 132, 36, List.copyOf(frames), List.of(Section.of("weapon", "Weapon")), null);
	}

	/**
	 * Sprite.java:27-29 has a column of 16 pixel frames of which the first two are carried, and :47-52 starts a 4 by 4
	 * grid to its right, 28 pixels in. The third frame in the column only gives the resource its colour.
	 */
	private static Profile resourceProfile() {
		List<Frame> frames = new ArrayList<>();
		grid(frames, 6, 6, 1, 2, 16, 22, RESOURCE, "carried");
		grid(frames, 34, 6, 4, 4, 16, 22, RESOURCE, "ground");
		return new Profile("Resource sheet", SPRITES + "resource/" + PNG, 244, 94, List.copyOf(frames), List.of(Section.of("carried", "Carried"), Section.of("ground", "On the ground")), null);
	}

	/** RESOURCE.java:106-111 gives one row of 16 frames of 16 pixels. */
	private static Profile debrisProfile() {
		List<Frame> frames = new ArrayList<>();
		grid(frames, 6, 6, 16, 1, 16, 22, DEBRIS, "debris");
		return new Profile("Resource debris sheet", SPRITES + "resource/debris/" + PNG, 716, 28, List.copyOf(frames), List.of(Section.of("debris", "Debris")), null);
	}

	/** GrowableSprite.java:63-68 gives 2 rows of 16 frames of 8 pixels, and :57-58 says the first row is the growth and the second the trunk. */
	private static Profile cropProfile() {
		List<Frame> frames = new ArrayList<>();
		grid(frames, 6, 6, 16, 1, 8, 14, CROP, "growth");
		grid(frames, 6, 20, 16, 1, 8, 14, CROP, "trunk");
		return new Profile("Crop sheet", SPRITES + "resource/growable/" + PNG, 460, 34, List.copyOf(frames), List.of(Section.of("growth", "Growth"), Section.of("trunk", "Trunk")), null);
	}

	/**
	 * TBuilding.java:114-193. A block is 72 pixels square with a 2 pixel margin, and its 16 tiles are 16 pixels at
	 * 0, 16, 32 and 52 in each direction (ComposerSources.java:37-72). The strips have a 6 pixel margin (:29,400).
	 * Every tile is opaque, so there's no outline to shade. What vanilla shares is the roof shape at each position.
	 */
	private static Profile buildingProfile() {
		List<Frame> frames = new ArrayList<>();
		String[] walls = { "wall", "wall", "diagonal", "diagonal", "broken", "broken" };
		for (int block = 0; block < walls.length; block++) {
			block(frames, 72 * (block % 4), 72 * (block / 4), walls[block]);
		}
		for (int strip = 0; strip < 2; strip++) {
			strip(frames, 144 + 28 * strip, 16, "strip");
		}
		for (int block = 0; block < 4; block++) {
			block(frames, 72 * block, 200, "opening");
		}
		strip(frames, 272, 8, "opening_strip");
		for (int block = 0; block < 2; block++) {
			block(frames, 72 * block, 300, "ceiling");
		}
		List<Section> sections = new ArrayList<>();
		String[][] names = { { "wall", "Walls" }, { "diagonal", "Diagonal walls" }, { "broken", "Broken walls" }, { "strip", "Wall strips" }, { "opening", "Openings" }, { "opening_strip", "Opening strips" }, { "ceiling", "Ceilings" } };
		for (String[] name : names) {
			sections.add(new Section(name[0], name[1], Tuning.DEFAULT, false));
		}
		return new Profile("Building sheet", SPRITES + "settlement/structure/" + PNG, 576, 372, List.copyOf(frames), List.copyOf(sections), STRUCTURE_ZIP);
	}

	/** RType.java:44-51 gives 4 columns of 3 rows of 32 pixels. */
	private static Profile skeletonProfile() {
		List<Frame> frames = new ArrayList<>();
		grid(frames, 6, 6, 4, 3, 32, 38, SKELETON, "skeleton");
		return new Profile("Skeleton sheet", SPRITES + "race/skelleton/" + PNG, 316, 120, List.copyOf(frames), List.of(Section.of("skeleton", "Skeleton")), null);
	}

	/** RAppearence.java:69-76 has 2 columns of 32 pixels, and as many rows as fit at 38 pixels each. */
	private static Profile sleepProfile() {
		Variable rows = new Variable() {
			@Override
			public String problem(int width, int height) {
				return width != 164 || height < 44 ? "A Sleeping sheet must be exactly 164 wide and at least 44 tall, and this one is " + width + " by " + height + "." : null;
			}

			@Override
			public List<Frame> frames(int width, int height) {
				List<Frame> frames = new ArrayList<>();
				grid(frames, 6, 6, 2, height / 38, 32, 38, SLEEP, "sleep");
				return frames;
			}
		};
		return new Profile("Sleeping sheet", SPRITES + "race/sleep/" + PNG, 164, 44, List.of(), List.of(Section.of("sleep", "Sleeping")), null, rows);
	}

	/** RAppearence.java:100-105 uses 8 frames of 16 pixels with a 3 pixel margin (ComposerSources.java:31). */
	private static Profile infantProfile() {
		List<Frame> frames = new ArrayList<>();
		grid(frames, 3, 3, 8, 1, 16, 22, INFANT, "infant");
		return new Profile("Infant sheet", SPRITES + "race/infant/" + PNG, 352, 22, List.copyOf(frames), List.of(Section.of("infant", "Infant")), null);
	}

	/** Minable.java:109-114 gives 2 rows of 8 frames of 16 pixels. */
	private static Profile minableProfile() {
		List<Frame> frames = new ArrayList<>();
		grid(frames, 6, 6, 8, 2, 16, 22, RESOURCE, "minable");
		return new Profile("Deposit sheet", SPRITES + "resource/minable/" + PNG, 364, 94, List.copyOf(frames), List.of(new Section("minable", "Deposit", STARTS_FLAT, true)), null);
	}

	/**
	 * SheetType.java:243-280. Rows of 16 pixel tiles with a 3 pixel margin, 22 apart across. The rows come in groups of
	 * x, each group 16x + 6 tall, for the smallest x from 1 to 8 that divides the height. Inside a group they touch.
	 */
	private static Profile tile1x1Profile() {
		Variable rows = new Variable() {
			private int group(int height) {
				for (int i = 1; i < 9; i++) {
					if (height % (16 * i + 6) == 0) {
						return i;
					}
				}
				return 0;
			}

			@Override
			public String problem(int width, int height) {
				if (width % 2 != 0 || width / 2 < 22 || width / 2 % 22 != 0) {
					return "Half of a 1x1 sheet's width must be a multiple of 22, and this one is " + width + " wide.";
				}
				return height == 0 || group(height) == 0 ? "A 1x1 sheet's height must be a multiple of 16x + 6 for some x from 1 to 8, and this one is " + height + " tall." : null;
			}

			@Override
			public List<Frame> frames(int width, int height) {
				int group = group(height);
				int groupHeight = 6 + group * 16;
				int rowsTotal = group * height / groupHeight;
				int columns = width / 2 / 22;
				List<Frame> frames = new ArrayList<>();
				for (int row = 0; row < rowsTotal; row++) {
					int top = groupHeight * (row / group) + 16 * (row % group);
					grid(frames, 3, top + 3, columns, 1, 16, 22, TILE_1X1, "tiles");
				}
				return frames;
			}
		};
		return new Profile("1x1 room sheet", SPRITES + "game/1x1/" + PNG, 0, 0, List.of(), List.of(Section.of("tiles", "Tiles")), null, rows);
	}

	/** SheetType.java:386-399,523-547. Each block is one piece of art, so its outline goes around the whole block. */
	private static Profile blockProfile(String folder, int tiles, double[][] tilts, Tuning defaults) {
		int inner = tiles * 16;
		int cell = inner + 12;
		Variable grid = new Variable() {
			@Override
			public String problem(int width, int height) {
				return width % 2 != 0 || width / 2 < cell || width / 2 % cell != 0 || height < cell || height % cell != 0 ? "A " + folder + " sheet's height and half its width must each be a multiple of " + cell + ", and this one is " + width + " by " + height + "." : null;
			}

			@Override
			public List<Frame> frames(int width, int height) {
				List<Frame> frames = new ArrayList<>();
				grid(frames, 6, 6, width / 2 / cell, height / cell, inner, cell, tilts, "blocks");
				return frames;
			}
		};
		return new Profile(folder + " room sheet", SPRITES + "game/" + folder + "/" + PNG, 0, 0, List.of(), List.of(new Section("blocks", "Blocks", defaults, true)), null, grid);
	}

	/** SheetType.java:477-486. House blocks of 72 pixels, laid out the way a building's are. */
	private static Profile comboProfile() {
		Variable blocks = new Variable() {
			@Override
			public String problem(int width, int height) {
				return width % 2 != 0 || width / 2 < 72 || width / 2 % 72 != 0 || height < 72 || height % 72 != 0 ? "A combo sheet's height and half its width must each be a multiple of 72, and this one is " + width + " by " + height + "." : null;
			}

			@Override
			public List<Frame> frames(int width, int height) {
				List<Frame> frames = new ArrayList<>();
				int[] offsets = { 0, 16, 32, 52 };
				for (int row = 0; row < height / 72; row++) {
					for (int column = 0; column < width / 2 / 72; column++) {
						for (int y : offsets) {
							for (int x : offsets) {
								frames.add(Frame.of(72 * column + 2 + x, 72 * row + 2 + y, 16, COMBO, "tiles"));
							}
						}
					}
				}
				return frames;
			}
		};
		return new Profile("combo room sheet", SPRITES + "game/combo/" + PNG, 0, 0, List.of(), List.of(new Section("tiles", "Tiles", STARTS_FLAT, true)), null, blocks);
	}

	/** SheetType.java:153-178. Rows 28 pixels apart, of 16 pixel tiles with a 6 pixel margin. */
	private static Profile textureProfile() {
		Variable rows = new Variable() {
			@Override
			public String problem(int width, int height) {
				int half = width / 2;
				return width % 2 != 0 || half <= 12 || (half - 12) % 16 != 0 || height < 28 || height % 28 != 0 ? "Half of a texture sheet's width must be 12 more than a multiple of 16, and its height a multiple of 28. This one is " + width + " by " + height + "." : null;
			}

			@Override
			public List<Frame> frames(int width, int height) {
				List<Frame> frames = new ArrayList<>();
				int columns = (width / 2 - 12) / 16;
				for (int row = 0; row < height / 28; row++) {
					for (int column = 0; column < columns; column++) {
						frames.add(Frame.of(6 + 16 * column, 6 + 28 * row, 16, TEXTURE, "tiles"));
					}
				}
				return frames;
			}
		};
		return new Profile("texture room sheet", SPRITES + "game/texture/" + PNG, 0, 0, List.of(), List.of(new Section("tiles", "Tiles", STARTS_FLAT, true)), null, rows);
	}

	private static void grid(List<Frame> frames, int left, int top, int columns, int rows, int size, int step, double[][] tilts, String section) {
		for (int row = 0; row < rows; row++) {
			for (int column = 0; column < columns; column++) {
				frames.add(Frame.of(left + step * column, top + step * row, size, tilts, section));
			}
		}
	}

	private static void block(List<Frame> frames, int left, int top, String section) {
		int[] offsets = { 0, 16, 32, 52 };
		for (int y : offsets) {
			for (int x : offsets) {
				frames.add(new Frame(left + 2 + x, top + 2 + y, 16, Role.STRUCTURE, null, section, -1, -1));
			}
		}
	}

	private static void strip(List<Frame> frames, int top, int tiles, String section) {
		for (int tile = 0; tile < tiles; tile++) {
			frames.add(new Frame(6 + 16 * tile, top + 6, 16, Role.STRUCTURE, null, section, -1, -1));
		}
	}

	private static Profile profileFor(String relativePath) {
		if (relativePath != null) {
			String path = relativePath.replace('\\', '/');
			for (Profile profile : PROFILES) {
				if (path.matches(profile.path())) {
					return profile;
				}
			}
		}
		return null;
	}

	/** Each frame of a sheet at this path and size, as x, y and size in the left half, or none if the game wouldn't accept it. Its normal is half the sheet's width to the right. */
	public static List<int[]> frames(String relativePath, int width, int height) {
		Profile profile = profileFor(relativePath);
		return profile == null || problemWith(relativePath.replace('\\', '/'), width, height) != null ? List.of() : profile.framesFor(width, height).stream().map(frame -> new int[] { frame.x(), frame.y(), frame.size() }).toList();
	}

	/** The average of vanilla's sheets of this kind at each pixel. A new sheet then has the same roof, under its own art. */
	private record Structure(int width, double[] nx, double[] ny) {
	}

	/** Read from the player's own data.zip when asked, and only held in memory, so none of it is shipped or saved. */
	private static Structure structure(Path game, Profile profile) throws IOException, Refused {
		if (profile.structureFolder() == null) {
			return null;
		}
		if (game == null || !Files.isRegularFile(game.resolve("base/data.zip"))) {
			throw new Refused(profile.kind() + "s are shaded from the game's own sheets, and the game's data.zip couldn't be found.");
		}
		Path key = game.toAbsolutePath().normalize();
		Structure known = STRUCTURES.get(key);
		if (known != null) {
			return known;
		}
		int width = profile.width() / 2;
		double[] sumX = new double[width * profile.minHeight()];
		double[] sumY = new double[sumX.length];
		int[] count = new int[sumX.length];
		int sheets = 0;
		try (ZipFile zip = new ZipFile(game.resolve("base/data.zip").toFile())) {
			for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
				String name = entry.getName();
				if (!name.startsWith(profile.structureFolder()) || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".png") || name.indexOf('/', profile.structureFolder().length()) >= 0) {
					continue;
				}
				PngCodec.Image image;
				try (InputStream in = zip.getInputStream(entry)) {
					image = PngCodec.decode(in.readAllBytes());
				}
				if (image.width() != profile.width() || image.height() < profile.minHeight()) {
					continue;
				}
				sheets++;
				for (Frame frame : profile.frames()) {
					for (int y = frame.y(); y < frame.y() + frame.size(); y++) {
						for (int x = frame.x(); x < frame.x() + frame.size(); x++) {
							int normal = image.at(x + width, y);
							double nx = ((normal >>> 24) - 128) / 127.0;
							double ny = ((normal >>> 16 & 0xFF) - 128) / 127.0;
							if (alpha(image.at(x, y)) != 0 && alpha(normal) != 0 && Math.hypot(nx, ny) <= 1) {
								int at = y * width + x;
								sumX[at] += nx;
								sumY[at] += ny;
								count[at]++;
							}
						}
					}
				}
			}
		}
		if (sheets == 0) {
			throw new Refused("The game's own " + profile.kind().toLowerCase(java.util.Locale.ROOT) + "s couldn't be read.");
		}
		for (int i = 0; i < count.length; i++) {
			sumX[i] /= Math.max(1, count[i]);
			sumY[i] /= Math.max(1, count[i]);
		}
		Structure made = new Structure(width, sumX, sumY);
		STRUCTURES.put(key, made);
		return made;
	}

	private static String standingSection(int row) {
		if (row <= 4) {
			return "feet";
		}
		return switch (row) {
		case 5 -> "tunic";
		case 13 -> "carry";
		case 14, 15 -> "torso_out";
		case 16 -> "head";
		case 17 -> "shadow";
		default -> "torso";
		};
	}

	/** The sections of a sheet at this path whose shading can be changed, or none if it can't be generated for. */
	public static List<Section> sections(String relativePath) {
		Profile profile = profileFor(relativePath);
		return profile == null ? List.of() : profile.sections();
	}

	/**
	 * Reads shading values given by name, such as a tool call's JSON. A value left out takes the section's default.
	 *
	 * @param given by section id, each a map holding strength and sharpness numbers
	 */
	public static Map<String, Tuning> parseTuning(String relativePath, Map<?, ?> given) throws Refused {
		Map<String, Section> known = new LinkedHashMap<>();
		sections(relativePath).forEach(section -> known.put(section.id(), section));
		Map<String, Tuning> tuning = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : given.entrySet()) {
			String id = String.valueOf(entry.getKey());
			Section section = known.get(id);
			if (section == null) {
				throw new Refused("There's no section called " + id + ". The sections are " + String.join(", ", known.keySet()) + ".");
			}
			if (!(entry.getValue() instanceof Map<?, ?> values)) {
				throw new Refused(id + " must give strength, sharpness or both.");
			}
			for (Object key : values.keySet()) {
				if (!"strength".equals(key) && !"sharpness".equals(key)) {
					throw new Refused(id + " has no value called " + key + ".");
				}
			}
			tuning.put(id, new Tuning(number(values, "strength", id, section.defaults().strength()), number(values, "sharpness", id, section.defaults().sharpness())));
		}
		checkTuning(relativePath, tuning);
		return tuning;
	}

	private static int number(Map<?, ?> values, String name, String id, int fallback) throws Refused {
		Object value = values.get(name);
		if (value == null) {
			return fallback;
		}
		if (value instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue())) {
			return number.intValue();
		}
		throw new Refused(id + "'s " + name + " must be a whole number.");
	}

	private static void checkTuning(String relativePath, Map<String, Tuning> tuning) throws Refused {
		List<Section> sections = sections(relativePath);
		for (Map.Entry<String, Tuning> entry : tuning.entrySet()) {
			Section section = sections.stream().filter(candidate -> candidate.id().equals(entry.getKey())).findFirst().orElse(null);
			if (section == null) {
				throw new Refused("There's no section called " + entry.getKey() + ".");
			}
			Tuning value = entry.getValue();
			if (!section.hasSharpness() && value.sharpness() != section.defaults().sharpness()) {
				throw new Refused(entry.getKey() + " has no sharpness to change, because its shape comes from the game's own sheets.");
			}
			if (value.strength() < MIN_STRENGTH || value.strength() > MAX_STRENGTH) {
				throw new Refused(entry.getKey() + "'s strength must be from " + MIN_STRENGTH + " to " + MAX_STRENGTH + ".");
			}
			if (value.sharpness() < MIN_SHARPNESS || value.sharpness() > MAX_SHARPNESS) {
				throw new Refused(entry.getKey() + "'s sharpness must be from " + MIN_SHARPNESS + " to " + MAX_SHARPNESS + ".");
			}
		}
	}

	/** The reason a file at this path and of this size can't be generated for, or null if it can. */
	private static String problemWith(String path, int width, int height) {
		Profile profile = profileFor(path);
		if (profile == null) {
			return "Normals can only be generated so far for sheets directly in these folders of assets/sprite: race, race/battle, race/skelleton, race/sleep, race/infant, animal, resource, resource/debris, resource/growable, resource/minable, settlement/structure, and game/1x1, 2x2, 3x3, box, combo and texture.";
		}
		if (profile.variable() != null) {
			return profile.variable().problem(width, height);
		}
		if (width != profile.width() || height < profile.minHeight()) {
			return "A " + profile.kind() + " must be exactly " + profile.width() + " wide and at least " + profile.minHeight() + " tall, and this one is " + width + " by " + height + ".";
		}
		return null;
	}

	/** Every PNG in the mod's version folders, in path order. A link that leaves the mod is skipped. */
	public static List<Listed> list(Path modRoot) throws IOException {
		List<String> paths = new ArrayList<>();
		if (Files.isDirectory(modRoot, LinkOption.NOFOLLOW_LINKS)) {
			Path real = modRoot.toRealPath();
			try (var children = Files.newDirectoryStream(modRoot)) {
				for (Path child : children) {
					if (child.getFileName().toString().matches("V\\d+") && !leaves(child, real) && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
						walk(child, real.resolve(child.getFileName().toString()), modRoot, paths);
					}
				}
			}
		}
		paths.sort(Comparator.naturalOrder());
		List<Listed> listed = new ArrayList<>();
		for (String path : paths) {
			listed.add(describe(modRoot, path));
		}
		return listed;
	}

	/** Only a sheet in a supported folder is decoded, so a mod with thousands of images still lists quickly. */
	private static Listed describe(Path modRoot, String path) {
		if (profileFor(path) == null) {
			return new Listed(path, null, problemWith(path, 0, 0), false);
		}
		try {
			PngCodec.Image image = PngCodec.decode(Files.readAllBytes(modRoot.resolve(path)));
			return listed(path, image);
		} catch (IOException e) {
			return new Listed(path, null, "This image couldn't be read: " + e.getMessage(), false);
		}
	}

	private static Listed listed(String path, PngCodec.Image image) {
		String problem = problemWith(path, image.width(), image.height());
		if (problem != null) {
			return new Listed(path, null, problem, false);
		}
		Profile profile = profileFor(path);
		return new Listed(path, profile.kind(), null, hasNormals(image, profile));
	}

	private static void walk(Path dir, Path expectedReal, Path modRoot, List<String> found) throws IOException {
		try (var children = Files.newDirectoryStream(dir)) {
			for (Path child : children) {
				if (leaves(child, expectedReal)) {
					continue;
				}
				if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
					walk(child, expectedReal.resolve(child.getFileName().toString()), modRoot, found);
				} else if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS) && child.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
					found.add(modRoot.relativize(child).toString().replace('\\', '/'));
				}
			}
		}
	}

	/** A symbolic link or a junction, which a real-path comparison is what catches on Windows. */
	private static boolean leaves(Path child, Path expectedParent) throws IOException {
		if (!Files.exists(child, LinkOption.NOFOLLOW_LINKS)) {
			return true;
		}
		return !child.toRealPath().equals(expectedParent.resolve(child.getFileName().toString()));
	}

	/** Reads one PNG for showing, whether or not it can be generated for. */
	public static Opened open(Path modRoot, String relativePath) throws IOException, Refused {
		Path file = locate(modRoot, relativePath);
		byte[] bytes = Files.readAllBytes(file);
		PngCodec.Image image = PngCodec.decode(bytes);
		String path = modRoot.relativize(file).toString().replace('\\', '/');
		Listed listed = listed(path, image);
		return new Opened(listed, image.width(), image.height(), toArgb(image.rgba()), bytes, statusOf(listed), revision(bytes));
	}

	private static String statusOf(Listed listed) {
		if (!listed.supported()) {
			return listed.whyNot();
		}
		return listed.kind() + (listed.hasNormals() ? ". This sheet already has normals." : ". This sheet has no normals yet.");
	}

	/**
	 * Writes normals into each frame of a sheet.
	 *
	 * @param replaceExisting must be true to replace normals the sheet already has
	 * @param revision if given, the sheet must still be what {@link #open} read
	 */
	public static Changed generate(Path modRoot, String relativePath, boolean replaceExisting, String revision) throws IOException, Refused {
		return generate(modRoot, relativePath, Map.of(), replaceExisting, revision);
	}

	/**
	 * Writes normals into each frame of a sheet.
	 *
	 * @param tuning the shading of each section, by id. A section left out uses its defaults
	 * @param replaceExisting must be true to replace normals the sheet already has
	 * @param revision if given, the sheet must still be what {@link #open} read
	 */
	public static Changed generate(Path modRoot, String relativePath, Map<String, Tuning> tuning, boolean replaceExisting, String revision) throws IOException, Refused {
		return generate(null, modRoot, relativePath, tuning, replaceExisting, revision);
	}

	/**
	 * @param game the game's folder. Only a sheet shaded from the game's own sheets needs it, and it's only read
	 */
	public static Changed generate(Path game, Path modRoot, String relativePath, Map<String, Tuning> tuning, boolean replaceExisting, String revision) throws IOException, Refused {
		Sheet sheet = sheet(modRoot, relativePath, revision);
		checkTuning(relativePath, tuning);
		if (!replaceExisting && hasNormals(sheet.image(), sheet.profile())) {
			throw new Refused(sheet.name() + " already has normals, so nothing was changed. Replacing them can't be undone, so it has to be asked for.");
		}
		int[] updated = sheet.image().rgba().clone();
		int frames = apply(sheet.image(), sheet.profile(), structure(game, sheet.profile()), tuning, updated);
		int pixels = differences(sheet.image().rgba(), updated);
		String message = pixels == 0 ? sheet.name() + " already has these normals, so nothing was changed." : "Generated normals for " + count(frames, "frame") + " in " + sheet.name() + ".";
		return save(sheet, updated, frames, pixels, message);
	}

	/** What {@link #generate} would make of a sheet with these values, as a PNG and its pixels. Nothing is written. */
	public record Preview(int width, int height, int[] argb, byte[] png) {
	}

	public static Preview preview(Path modRoot, String relativePath, Map<String, Tuning> tuning) throws IOException, Refused {
		return preview(null, modRoot, relativePath, tuning);
	}

	public static Preview preview(Path game, Path modRoot, String relativePath, Map<String, Tuning> tuning) throws IOException, Refused {
		Sheet sheet = sheet(modRoot, relativePath, null);
		checkTuning(relativePath, tuning);
		int[] updated = sheet.image().rgba().clone();
		apply(sheet.image(), sheet.profile(), structure(game, sheet.profile()), tuning, updated);
		int[] argb = toArgb(updated);
		return new Preview(sheet.image().width(), sheet.image().height(), argb, PngCodec.encode(sheet.image().width(), sheet.image().height(), argb));
	}

	private static int apply(PngCodec.Image image, Profile profile, Structure structure, Map<String, Tuning> tuning, int[] updated) {
		int frames = 0;
		for (Frame frame : profile.framesFor(image.width(), image.height())) {
			int[] normals = normals(image, frame, tuningFor(profile, frame, tuning), structure);
			if (applies(normals)) {
				frames++;
			}
			write(updated, image.width(), image.width() / 2, frame, normals);
		}
		return frames;
	}

	private static Tuning tuningFor(Profile profile, Frame frame, Map<String, Tuning> tuning) {
		Tuning given = tuning.get(frame.section());
		if (given != null) {
			return given;
		}
		return profile.sections().stream().filter(section -> section.id().equals(frame.section())).findFirst().orElseThrow().defaults();
	}

	private static int[] toArgb(int[] rgba) {
		int[] argb = new int[rgba.length];
		for (int i = 0; i < argb.length; i++) {
			argb[i] = rgba[i] >>> 8 | rgba[i] << 24;
		}
		return argb;
	}

	/** Removes normals, leaving transparent pixels. */
	public static Changed trim(Path modRoot, String relativePath, TrimMode mode, boolean discardExisting, String revision) throws IOException, Refused {
		Sheet sheet = sheet(modRoot, relativePath, revision);
		if (mode == TrimMode.ALL && !discardExisting) {
			throw new Refused("Clearing every normal can't be undone, so it has to be asked for. Nothing was changed.");
		}
		PngCodec.Image image = sheet.image();
		int[] updated = image.rgba().clone();
		int frames = 0;
		for (Frame frame : sheet.profile().framesFor(image.width(), image.height())) {
			boolean touched = false;
			for (int y = 0; y < frame.size(); y++) {
				for (int x = 0; x < frame.size(); x++) {
					int at = (frame.y() + y) * image.width() + frame.x() + image.width() / 2 + x;
					boolean clear = mode == TrimMode.ALL || alpha(image.at(frame.x() + x, frame.y() + y)) == 0;
					if (clear && alpha(updated[at]) != 0) {
						updated[at] = 0;
						touched = true;
					}
				}
			}
			if (touched) {
				frames++;
			}
		}
		int pixels = differences(image.rgba(), updated);
		String message;
		if (pixels == 0) {
			message = (mode == TrimMode.ALL ? sheet.name() + " has no normals to clear" : sheet.name() + " has no normals outside its art") + ", so nothing was changed.";
		} else if (mode == TrimMode.ALL) {
			message = "Cleared the normals from " + count(frames, "frame") + " in " + sheet.name() + ". Generate new ones so the sheet lights properly.";
		} else {
			message = "Removed " + count(pixels, "normal pixel") + " outside the art in " + sheet.name() + ".";
		}
		return save(sheet, updated, frames, pixels, message);
	}

	private static String count(int number, String noun) {
		return number + " " + noun + (number == 1 ? "" : "s");
	}

	private record Sheet(Path file, String name, byte[] bytes, PngCodec.Image image, Profile profile) {
	}

	private static Sheet sheet(Path modRoot, String relativePath, String revision) throws IOException, Refused {
		Path file = locate(modRoot, relativePath);
		byte[] bytes = Files.readAllBytes(file);
		if (revision != null && !revision.equals(revision(bytes))) {
			throw new Refused(file.getFileName() + " has changed since it was opened, so nothing was changed. Open it again.");
		}
		PngCodec.Image image = PngCodec.decode(bytes);
		String path = modRoot.relativize(file).toString().replace('\\', '/');
		String problem = problemWith(path, image.width(), image.height());
		if (problem != null) {
			throw new Refused(problem + " Nothing was changed.");
		}
		return new Sheet(file, file.getFileName().toString(), bytes, image, profileFor(path));
	}

	private static Path locate(Path modRoot, String relativePath) throws IOException, Refused {
		if (relativePath == null || relativePath.isBlank()) {
			throw new Refused("Name the PNG. It's a path inside the mod.");
		}
		Path relative;
		try {
			relative = Path.of(relativePath);
		} catch (InvalidPathException e) {
			throw new Refused("That isn't a file in this mod.");
		}
		relative = relative.normalize();
		if (relative.isAbsolute() || relative.startsWith("..") || relative.toString().isEmpty()) {
			throw new Refused("That isn't a file in this mod.");
		}
		Path file = modRoot.resolve(relative);
		refuseLinks(modRoot, file);
		if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
			throw new Refused("That file isn't in this mod.");
		}
		if (!file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
			throw new Refused("That isn't a PNG file.");
		}
		return file;
	}

	private static void refuseLinks(Path modRoot, Path target) throws IOException, Refused {
		Path at = modRoot;
		Path expected = modRoot.toRealPath();
		for (Path part : modRoot.relativize(target.normalize())) {
			at = at.resolve(part);
			expected = expected.resolve(part);
			if (Files.exists(at, LinkOption.NOFOLLOW_LINKS) && !(Files.exists(at) && at.toRealPath().equals(expected))) {
				throw new Refused(at + " is a link to somewhere else, so nothing was written through it.");
			}
		}
	}

	private static Changed save(Sheet sheet, int[] updated, int frames, int pixels, String message) throws IOException, Refused {
		String path = sheet.file().toString();
		if (pixels == 0) {
			return new Changed(path, false, frames, 0, message);
		}
		PngCodec.Image image = sheet.image();
		if (changedOutsideFrames(image, sheet.profile(), updated)) {
			throw new IOException("The result changed something outside the sheet's frames, so nothing was written.");
		}
		byte[] encoded = PngCodec.encode(image.width(), image.height(), toArgb(updated));
		if (!Arrays.equals(PngCodec.decode(encoded).rgba(), updated)) {
			throw new IOException("The finished image didn't read back the same, so nothing was written.");
		}
		Path temp = sheet.file().resolveSibling(sheet.file().getFileName() + ".syxtmp");
		try {
			Files.write(temp, encoded);
			if (!Arrays.equals(Files.readAllBytes(sheet.file()), sheet.bytes())) {
				throw new Refused(sheet.name() + " changed while this was running, so nothing was changed.");
			}
			SafeWrite.commit(List.of(new SafeWrite.Change(sheet.file(), temp, sheet.bytes())));
		} finally {
			Files.deleteIfExists(temp);
		}
		return new Changed(path, true, frames, pixels, message);
	}

	private static boolean changedOutsideFrames(PngCodec.Image image, Profile profile, int[] updated) {
		boolean[] inside = new boolean[updated.length];
		int half = image.width() / 2;
		for (Frame frame : profile.framesFor(image.width(), image.height())) {
			for (int y = 0; y < frame.size(); y++) {
				Arrays.fill(inside, (frame.y() + y) * image.width() + frame.x() + half, (frame.y() + y) * image.width() + frame.x() + half + frame.size(), true);
			}
		}
		for (int i = 0; i < updated.length; i++) {
			if (!inside[i] && updated[i] != image.rgba()[i]) {
				return true;
			}
		}
		return false;
	}

	private static int differences(int[] before, int[] after) {
		int count = 0;
		for (int i = 0; i < before.length; i++) {
			if (before[i] != after[i]) {
				count++;
			}
		}
		return count;
	}

	/** Art with a shaped normal beside it. A blank or flat normal is treated as no normal, as nothing would be lost. */
	private static boolean hasNormals(PngCodec.Image image, Profile profile) {
		for (Frame frame : profile.framesFor(image.width(), image.height())) {
			for (int y = 0; y < frame.size(); y++) {
				for (int x = 0; x < frame.size(); x++) {
					int normal = image.at(frame.x() + image.width() / 2 + x, frame.y() + y);
					if (alpha(image.at(frame.x() + x, frame.y() + y)) != 0 && alpha(normal) != 0 && !flat(normal)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean flat(int pixel) {
		return Math.abs((pixel >>> 24) - 128) <= FLAT_MARGIN && Math.abs((pixel >>> 16 & 0xFF) - 128) <= FLAT_MARGIN;
	}

	private static int alpha(int pixel) {
		return pixel & 0xFF;
	}

	private static boolean applies(int[] normals) {
		for (int pixel : normals) {
			if (pixel != 0) {
				return true;
			}
		}
		return false;
	}

	/** A frame's new normals, row by row, with 0 where there should be nothing. */
	private static int[] normals(PngCodec.Image image, Frame frame, Tuning tuning, Structure structure) {
		boolean[][] mask = mask(image, frame);
		int size = frame.size();
		int[] out = new int[size * size];
		if (frame.role() == Role.STRUCTURE) {
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					if (mask[y + 1][x + 1]) {
						int at = (frame.y() + y) * structure.width() + frame.x() + x;
						out[y * size + x] = fromStructure(structure.nx()[at], structure.ny()[at], tuning);
					}
				}
			}
			return out;
		}
		int[][] depth = depths(mask);
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				if (mask[y + 1][x + 1]) {
					out[y * size + x] = shade(depth, x + 1, y + 1, frame.tilts(), tuning);
				}
			}
		}
		return out;
	}

	/** The frame's art, padded by one empty row and column on every side so the border counts as an edge. */
	private static boolean[][] mask(PngCodec.Image image, Frame frame) {
		int size = frame.size();
		boolean[][] mask = new boolean[size + 2][size + 2];
		boolean useShadow = frame.role() == Role.WHOLE_BODY && hasArt(image, frame.shadowX(), frame.shadowY(), size);
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				boolean own = alpha(image.at(frame.x() + x, frame.y() + y)) != 0;
				boolean body = useShadow && alpha(image.at(frame.shadowX() + x, frame.shadowY() + y)) != 0;
				// The art counts too, so a pixel drawn outside the shadow still has a normal to be lit by.
				mask[y + 1][x + 1] = own || body;
			}
		}
		return mask;
	}

	private static boolean hasArt(PngCodec.Image image, int left, int top, int size) {
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				if (alpha(image.at(left + x, top + y)) != 0) {
					return true;
				}
			}
		}
		return false;
	}

	/** How far each pixel is from the shape's edge, by 4-neighbour steps. */
	private static int[][] depths(boolean[][] mask) {
		int[][] depth = new int[mask.length][mask.length];
		boolean changed = true;
		for (int level = 1; changed; level++) {
			changed = false;
			for (int y = 1; y < mask.length - 1; y++) {
				for (int x = 1; x < mask.length - 1; x++) {
					if (mask[y][x] && depth[y][x] == 0 && (touches(mask, depth, x - 1, y, level) || touches(mask, depth, x + 1, y, level) || touches(mask, depth, x, y - 1, level) || touches(mask, depth, x, y + 1, level))) {
						depth[y][x] = level;
						changed = true;
					}
				}
			}
		}
		return depth;
	}

	/** The depth of the ridge a pixel belongs to, found by climbing to the deepest point it can reach. */
	private static int ridge(int[][] depth, int startX, int startY) {
		int x = startX;
		int y = startY;
		while (true) {
			int bestX = x;
			int bestY = y;
			for (int[] step : STEPS) {
				if (depth[y + step[1]][x + step[0]] > depth[bestY][bestX]) {
					bestX = x + step[0];
					bestY = y + step[1];
				}
			}
			if (bestX == x && bestY == y) {
				return depth[y][x];
			}
			x = bestX;
			y = bestY;
		}
	}

	private static final int[][] STEPS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

	private static boolean touches(boolean[][] mask, int[][] depth, int x, int y, int level) {
		return !mask[y][x] || depth[y][x] > 0 && depth[y][x] < level;
	}

	/** G points down the screen (Shader.java:48), so outward is simply away from the shape's middle in image terms. */
	private static int shade(int[][] depth, int x, int y, double[][] tilts, Tuning tuning) {
		int dx = depth[y][x + 1] - depth[y][x - 1];
		int dy = depth[y + 1][x] - depth[y - 1][x];
		double length = Math.hypot(dx, dy);
		double[] row = tilts[Math.min(ridge(depth, x, y), tilts.length) - 1];
		double tilt = length == 0 ? 0 : tuned(row[Math.min(depth[y][x], row.length) - 1], row[0], tuning);
		double nx = length == 0 ? 0 : -dx / length * tilt;
		double ny = length == 0 ? 0 : -dy / length * tilt;
		double nz = Math.sqrt(Math.max(0, 1 - nx * nx - ny * ny));
		return pack(tilted(nx), tilted(ny), Math.min(255, (int) Math.round(128 + 127 * nz)), 255);
	}

	private static int fromStructure(double x, double y, Tuning tuning) {
		double nx = x * tuning.strength() / 100.0;
		double ny = y * tuning.strength() / 100.0;
		double length = Math.hypot(nx, ny);
		if (length > MAX_TILT) {
			nx *= MAX_TILT / length;
			ny *= MAX_TILT / length;
		}
		double nz = Math.sqrt(Math.max(0, 1 - nx * nx - ny * ny));
		return pack(tilted(nx), tilted(ny), Math.min(255, (int) Math.round(128 + 127 * nz)), 255);
	}

	/** Sharpness bends the fade from the edge inwards, leaving the edge alone. A tilt of 1 would leave no Z at all. */
	private static double tuned(double tilt, double edge, Tuning tuning) {
		double shaped = tuning.sharpness() == 100 || edge <= 0 ? tilt : edge * Math.pow(tilt / edge, tuning.sharpness() / 100.0);
		return Math.min(MAX_TILT, shaped * tuning.strength() / 100.0);
	}

	/**
	 * Never 0, because a rotated frame's normal is only written if its R isn't 0, and a G of 0 turns into one
	 * (ComposerUtil.java:340-350).
	 */
	private static int tilted(double value) {
		return Math.max(1, Math.min(255, (int) Math.round(128 + 127 * value)));
	}

	private static void write(int[] pixels, int width, int half, Frame frame, int[] normals) {
		for (int y = 0; y < frame.size(); y++) {
			for (int x = 0; x < frame.size(); x++) {
				int at = (frame.y() + y) * width + frame.x() + half + x;
				int value = normals[y * frame.size() + x];
				if (value != 0 || alpha(pixels[at]) != 0) {
					pixels[at] = value;
				}
			}
		}
	}

	private static int pack(int r, int g, int b, int a) {
		return r << 24 | g << 16 | b << 8 | a;
	}

	private static String revision(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
