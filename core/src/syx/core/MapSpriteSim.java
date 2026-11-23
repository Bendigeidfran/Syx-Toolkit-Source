package syx.core;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import syx.core.GameParser.Node;
import syx.core.GameParser.ReadException;
import syx.core.PortraitRenderer.Subject;
import syx.core.PortraitSection.AbsentBlock;
import syx.core.PortraitSim.Refused;
import syx.core.PortraitSim.State;

/**
 * The Map Sprite Sim, for every front end: how a race's people look moving about the map, in each animation and
 * direction the game has. It edits the map tags of the same sprite file the Race Portrait Sim edits, through the same
 * session, so races, locking, saving and Copy to Mod behave the same. Nothing here touches the game's files, and only
 * saving and copying write.
 */
public final class MapSpriteSim {

	/** The map tags of a race's sprite file: what RColors, RAppearence and RType read for the map. */
	static final SpriteFileSection.Spec SPEC = new SpriteFileSection.Spec(List.of("COLOR_BLOOD", "COLORS", "COLOR_CLOTHES", "SPRITE_EXTRA_FILE", "SLEEP_FILE"),
			List.of("SPRITE_FILE", "SPRITE_SKELLETON_FILE", "COLOR_SKIN", "COLOR_LEG", "ADDONS"), "map sprite tag");

	/** How an animation is drawn. */
	public enum Pose {
		STANDING, SWIMMING, LYING, SLEEPING, CORPSE, SKELETON
	}

	/**
	 * One of the game's animations.
	 *
	 * @param id the game's own name for it, such as MOVE
	 * @param torsos the standing sheet's torso row for each step
	 * @param fps steps a second, or 0 if it doesn't move on its own
	 * @param feetFps how fast the feet step, or 0 if they stand still
	 * @param tools the extra sheet's tool row for each step, or empty for none
	 * @param note what the sim leaves out of it, or null
	 */
	public record Animation(String id, String label, Pose pose, List<Integer> torsos, int fps, int feetFps, List<Integer> tools, String note, String evidence) {
	}

	private static final int S = MapSpriteRenderer.TORSO_STILL;
	private static final int R = MapSpriteRenderer.TORSO_RIGHT;
	private static final int R2 = MapSpriteRenderer.TORSO_RIGHT2;
	private static final int R3 = MapSpriteRenderer.TORSO_RIGHT3;
	private static final int L = MapSpriteRenderer.TORSO_LEFT;
	private static final int L2 = MapSpriteRenderer.TORSO_LEFT2;
	private static final int L3 = MapSpriteRenderer.TORSO_LEFT3;
	private static final int C = MapSpriteRenderer.TORSO_CARRY;
	private static final int O = MapSpriteRenderer.TORSO_OUT;
	private static final int O2 = MapSpriteRenderer.TORSO_OUT2;
	/** The feet while moving (HSprite.java:75), at 14 steps a second at full speed (:247). */
	private static final int[] FEET = { MapSpriteRenderer.FEET_NONE, MapSpriteRenderer.FEET_RIGHT, MapSpriteRenderer.FEET_RIGHT2, MapSpriteRenderer.FEET_RIGHT, MapSpriteRenderer.FEET_NONE,
			MapSpriteRenderer.FEET_LEFT, MapSpriteRenderer.FEET_LEFT2, MapSpriteRenderer.FEET_LEFT };
	/** Swimming's torsos, at 5 a second (HSprite.java:76-77,266). */
	private static final List<Integer> SWIM = List.of(S, L, L2, L3, L2, L, S, R, R2, R3, R2, R);
	private static final String WEAPON = "The weapon this animation shows comes from the person's equipment (StatsEquip.renderExtra), and isn't drawn here.";
	private static final String BOW = "The bow comes from the soldier's division (HSprites.java:535-537), and isn't drawn here.";
	private static final String FACING = "In the game this one faces across the way the person moves (getDir uses the perpendicular), so the direction here is the way they face.";

	private static Animation standing(String id, String label, int fps, int feetFps, String note, String evidence, int... torsos) {
		List<Integer> list = new ArrayList<>();
		for (int torso : torsos) {
			list.add(torso);
		}
		return new Animation(id, label, Pose.STANDING, List.copyOf(list), fps, feetFps, List.of(), note, evidence);
	}

	private static Animation tool(String id, String label, int fps, String evidence, int[] torsos, int[] tools) {
		List<Integer> torsoList = new ArrayList<>();
		List<Integer> toolList = new ArrayList<>();
		for (int i = 0; i < torsos.length; i++) {
			torsoList.add(torsos[i]);
			toolList.add(tools[i]);
		}
		return new Animation(id, label, Pose.STANDING, List.copyOf(torsoList), fps, 0, List.copyOf(toolList), null, evidence);
	}

	/** Every animation a person on the map has, in the order HSprites lists them, then swimming, lying and the dead. */
	public static final List<Animation> ANIMATIONS = List.of(
			standing("STAND", "Stand", 0, 0, null, "HSprites.java:39", S),
			standing("MOVE", "Walk", 20, 14, "Weapons a person carries are drawn by their equipment, which isn't simulated.", "HSprites.java:70-106", S, R, R2, R, S, L, L2, L),
			standing("CARRY", "Carry", 0, 0, "The goods carried, or the cart for more than 4, aren't drawn (HSprite.java:185-204).", "HSprites.java:62", C),
			standing("ARMS_OUT", "Arms out", 0, 0, null, "HSprites.java:66", O2),
			standing("THROW", "Throw", 5, 0, null, "HSprites.java:108-118", S, R, R2),
			standing("DRAG", "Drag", 20, 14, FACING, "HSprites.java:120-134", R2),
			tool("TOOL_BACK", "Tool, swing back", 8, "HSprites.java:196-228", new int[] { R2, R2, R2, R2, R2, R, R, S, S }, new int[] { 0, 0, 0, 0, 0, 1, 1, 2, 2 }),
			tool("TOOL_HIT", "Tool, hit", 32, "HSprites.java:230-272", new int[] { S, S, S, S, S, S, S, S, S, S, S, S, S, R, R2 }, new int[] { 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 0 }),
			standing("BOX", "Box", 5, 0, null, "HSprites.java:274-286", L, L2, L3, L2, L, S, R, R2, R3, R2, R),
			standing("STRANGLED", "Strangled", 15, 15, null, "HSprites.java:288-302", L, S, R),
			standing("GRAB", "Grab", 10, 0, null, "HSprites.java:304-315", L2, R2, L2, R2, L2, R2, L2, R2, L2, R2),
			standing("FIST", "Fist", 10, 0, null, "HSprites.java:317-333", S, R, R, R, R, R2, R3, R2, R3, R2, R3, R2, R, R, S),
			standing("ARM_RIGHT", "Arm right", 0, 0, null, "HSprites.java:335", R2),
			standing("ARM_RIGHT2", "Arm right 2", 0, 0, null, "HSprites.java:351", R3),
			standing("ARM_LEFT", "Arm left", 0, 0, null, "HSprites.java:339", L),
			standing("ARM_LEFT2", "Arm left 2", 0, 0, null, "HSprites.java:343", L2),
			standing("ARM_LEFT3", "Arm left 3", 0, 0, null, "HSprites.java:347", L3),
			standing("DANCE", "Dance", 10, 0, null, "HSprites.java:357-366", S, R, R2, R, S, L, L2, L),
			standing("DANCE_EXTRA", "Dance, wide", 10, 0, null, "HSprites.java:368-381", S, R, R2, R3, R2, R, S, L, L2, L3, L2, L),
			standing("WAVE", "Wave", 5, 0, null, "HSprites.java:383-388", S, O, O2, O),
			standing("MOVE_SWORD", "Walk with sword", 20, 14, WEAPON, "HSprites.java:136-162", S, L, L2, L),
			standing("MOVE_SWORD_BACK", "Back away with sword", 20, 14, WEAPON + " " + FACING, "HSprites.java:164-194", S, L, L2, L),
			standing("SWORD_STAND", "Sword, stand", 0, 0, WEAPON, "HSprites.java:390-404", R),
			standing("SWORD_STAB", "Sword, stab", 10, 0, WEAPON, "HSprites.java:428-458", S, R, R2, R2, R2, R, S, S),
			standing("SWORD_OUT", "Sword, draw", 10, 0, WEAPON, "HSprites.java:460-482", S, R, R2, R2),
			standing("SWORD_IN", "Sword, sheathe", 10, 0, WEAPON, "HSprites.java:484-506", R2, R, S, S),
			standing("ARCHER1", "Archer 1", 0, 0, BOW, "HSprites.java:544", S),
			standing("ARCHER2", "Archer 2", 0, 0, BOW, "HSprites.java:545", L),
			standing("ARCHER3", "Archer 3", 0, 0, BOW, "HSprites.java:546", L2),
			standing("ARCHER4", "Archer 4", 0, 0, BOW, "HSprites.java:547", L3),
			new Animation("SWIM", "Swim", Pose.SWIMMING, SWIM, 5, 0, List.of(), null, "HSprite.java:263-312"),
			new Animation("LAY", "Lie down", Pose.LYING, List.of(), 0, 0, List.of(), null, "HSprites.java:594-667"),
			new Animation("SLEEP", "Sleep", Pose.SLEEPING, List.of(), 0, 0, List.of(), null, "HSprites.java:550-570"),
			new Animation("CORPSE", "Corpse", Pose.CORPSE, List.of(), 0, 0, List.of(), null, "HCorpseRenderer.java:45-96"),
			new Animation("SKELETON", "Skeleton", Pose.SKELETON, List.of(), 0, 0, List.of(),
					"The game draws an adult's skeleton from CHILD's SPRITE_SKELLETON_FILE, and a child's from the first type's: ThingsCorpses passes true for an adult (:401), and RAppearence.skelleton gives the child's sheet for true (:157-159). The sim does the same.",
					"HCorpseRenderer.java:27-43"));

	/** DIR.ALL's order (DIR.java:42), which is each frame's place in its 8. */
	public static final List<String> DIRECTIONS = List.of("N", "NE", "E", "SE", "S", "SW", "W", "NW");

	/**
	 * The person to draw. Age, grime, injury and decay are 0 to 1, and armour 0 to 5.
	 *
	 * @param type which of the race's types: its adults in order, then the child
	 * @param animation an animation's id
	 * @param direction 0 to 7, in DIRECTIONS' order
	 * @param clothes how many clothes they have, from 0 to the game's maximum, or -1 for the most
	 * @param enemy an invader, who wears the enemy's dark clothes
	 * @param lit whether to light the sprite against its normals as the sun would
	 */
	public record Person(int type, String animation, int direction, long seed, double age, boolean noble, boolean slave, boolean parent, boolean retired, boolean sick, boolean enemy, boolean naked, int clothes, int plate,
			int leather, double grime, double injury, double decay, int scale, boolean lit, PortraitSim.Pins pins) {
		public static Person standard() {
			return new Person(0, "MOVE", 4, 1, 0.4, false, false, false, false, false, false, false, -1, 0, 0, 0, 0, 0, 4, true, PortraitSim.Pins.NONE);
		}
	}

	/** One step of an animation: which of the preview's frames to show, and for how long. */
	public record Step(int frame, int millis) {
	}

	/**
	 * One addon of the type shown.
	 *
	 * @param item its place in ADDONS
	 * @param index its ADDON_INDEX
	 * @param colour the colour it names, or null for white
	 * @param shown whether its CONDITIONS let this person have it
	 */
	public record AddonRow(int item, int index, boolean belowHead, String colour, boolean shown) {
	}

	/**
	 * The result of drawing.
	 *
	 * @param problem why it's RED, or null
	 * @param line the line of the text the problem is on, or null
	 * @param warnings what the game would only warn about
	 * @param notes where the sim differs from the game, which aren't problems
	 * @param types the race's types, in the order Person.type counts them
	 * @param frames the animation's different pictures, each width by height
	 * @param timeline the order to show the frames in, and for how long, looping; one step for a still pose
	 * @param compass the first moment of the animation in each of the 8 directions, each compassSize square
	 * @param clothesMax the most clothes a person can have, from the game's _CLOTHES file
	 */
	public record Preview(State state, String problem, Integer line, List<String> warnings, List<String> notes, List<String> types, int width, int height, List<int[]> frames, List<Step> timeline, int compassSize,
			List<int[]> compass, List<AbsentBlock> absent, List<AddonRow> addons, int clothesMax, List<Integer> random) {
		/** One frame as a PNG. */
		public byte[] png(int frame) throws IOException {
			return PngCodec.encode(width, height, frames.get(frame));
		}

		/** Every frame side by side, in the order the frames list them. */
		public byte[] stripPng() throws IOException {
			return strip(frames, width, height);
		}

		/** The 8 directions side by side, N first and clockwise. */
		public byte[] compassPng() throws IOException {
			return strip(compass, compassSize, compassSize);
		}

		private static byte[] strip(List<int[]> images, int width, int height) throws IOException {
			if (images.isEmpty()) {
				return null;
			}
			int[] argb = new int[width * images.size() * height];
			for (int i = 0; i < images.size(); i++) {
				int[] image = images.get(i);
				for (int y = 0; y < height; y++) {
					System.arraycopy(image, y * width, argb, y * width * images.size() + i * width, width);
				}
			}
			return PngCodec.encode(width * images.size(), height, argb);
		}
	}

	private static final Pattern KEYED_ERROR = Pattern.compile("Error parsing line: (\\d+), key: (.+?)\\. (.*)", Pattern.DOTALL);
	/** The longest loop worked out, so a slow pair of rates can't make thousands of steps. */
	private static final int MAX_LOOP_MS = 12000;
	private static final int COMPASS_SCALE = 2;
	/**
	 * The sun at noon with the game's default settings (day cycle on, brightness 50, LSettings.java:99,104), from
	 * Ambient.apply (Ambient.java:58-96) and LightShadows (:29-58): at noon the sun is at 270 degrees and tilted 40. Its
	 * strength is (0.55 + 0.75 (1 - 1)^2) x 4.5 x (0.95 + 0.5 x 0.5) = 2.97, and it's registered twice at a quarter
	 * each, (1 - reflect) / 2 with reflect 0.5, for the two shadow depths. A second, unshadowed light of 0.5 comes from
	 * the other side, tilted 50. Weather and the room light indoors aren't included.
	 */
	private static final LitCanvas.Light[] SUN = { LitCanvas.Light.of(2 * 0.25 * 2.97, 270, 40), LitCanvas.Light.of(0.5, 90, 50) };
	private static final LitCanvas.Light[] NO_LIGHT = { LitCanvas.Light.NONE };

	private MapSpriteSim() {
	}

	public static Animation animation(String id) {
		for (Animation animation : ANIMATIONS) {
			if (animation.id().equalsIgnoreCase(id)) {
				return animation;
			}
		}
		return null;
	}

	/** Every block's absent tags with a default in the game code. Text the toolkit can't locate gives an empty list. */
	public static List<AbsentBlock> absent(String text) {
		List<AbsentBlock> blocks = new ArrayList<>();
		try {
			SpriteFileSection.colourBlocks(blocks, text, SpriteFileSection.layout(text));
		} catch (RuntimeException e) {
			return List.of();
		}
		return blocks;
	}

	/** The caller closes it, which closes the game's parser. */
	public static Session open(Path game, ModEntry mod) throws GameParser.UnavailableException, IOException {
		return new Session(PortraitSim.open(game, mod));
	}

	public static final class Session implements AutoCloseable {
		private final PortraitSim.Session base;
		private final MapSpriteRenderer renderer = new MapSpriteRenderer();

		private Session(PortraitSim.Session base) {
			this.base = base;
		}

		@Override
		public void close() {
			base.close();
		}

		/** The same races as the Race Portrait Sim, because both edit the file a race's SPRITE_FILE names. */
		public List<PortraitSim.Race> races() throws IOException {
			return base.races();
		}

		public PortraitSim.Opened open(String name) throws Refused, IOException {
			return base.open(name, SPEC);
		}

		public Path spriteFilePath(String spriteFile) {
			return base.spriteFilePath(spriteFile);
		}

		public Path raceFilePath(String race) {
			return base.raceFilePath(race);
		}

		public PortraitSim.Copied copyToMod(String name) throws Refused, IOException {
			return base.copyToMod(name);
		}

		public boolean imagesChanged() {
			return base.imagesChanged();
		}

		/** Saves the map tags back into the race's file. Returns false if that would change nothing, so nothing was written. */
		public boolean save(String name, String revision, String text) throws Refused, IOException {
			return base.save(name, revision, text, SPEC, section -> {
				Preview preview = preview(name, section, Person.standard());
				return preview.state() == State.RED ? preview.problem() : null;
			});
		}

		/**
		 * Draws a race's people on the map.
		 *
		 * @param text the map tags to use, or null for what's on disk. It's ignored if the race can't be edited.
		 */
		public Preview preview(String name, String text, Person person) throws Refused, IOException {
			PortraitSim.Race race = base.race(name);
			if (race.spriteFile() == null) {
				return red(race.note(), null, List.of());
			}
			Animation animation = animation(person.animation());
			if (animation == null) {
				throw new Refused("There's no animation called " + person.animation() + ". The animations are " + String.join(", ", ANIMATIONS.stream().map(Animation::id).toList()) + ".");
			}
			Set<String> warnings = new LinkedHashSet<>();
			Set<String> notes = new LinkedHashSet<>();
			base.clearUsedImages();
			List<AbsentBlock> absent = List.of();
			try {
				Node root;
				if (race.editable()) {
					String file = PortraitSim.Session.decode(Files.readAllBytes(base.spriteFilePath(race.spriteFile())));
					String section = text == null ? SpriteFileSection.extract(SPEC, file) : text;
					absent = absent(section);
					root = base.sectionRoot(section);
					PortraitSection.Issue issue = SpriteFileSection.problem(SPEC, section, file);
					if (issue != null) {
						return red(issue.message(), issue.line(), absent);
					}
				} else {
					try {
						root = base.layeredRoot(race.spriteFile());
					} catch (ReadException e) {
						if (race.note() != null && !race.canCopy()) {
							return red(race.note(), null, absent);
						}
						throw e;
					}
				}
				Node clothes;
				try {
					clothes = base.layeredInit("stats/equip/civic/_CLOTHES.txt");
				} catch (ReadException e) {
					clothes = null;
				}
				MapSpriteModel model = MapSpriteModel.read(root, clothes, base.parser(), base::raceImage, notes);
				return draw(model, animation, person, warnings, notes, absent);
			} catch (ReadException e) {
				Problem problem = problem(e);
				return red(problem.message(), problem.line(), absent);
			} catch (PortraitModel.Stop e) {
				return red(e.getMessage(), null, absent);
			} catch (CharacterCodingException e) {
				return red("This file isn't saved as UTF-8.", null, absent);
			} catch (RuntimeException e) {
				return red("The toolkit couldn't read this text: " + e, null, absent);
			}
		}

		private Preview draw(MapSpriteModel model, Animation animation, Person person, Set<String> warnings, Set<String> notes, List<AbsentBlock> absent) throws ReadException {
			int index = Math.max(0, Math.min(model.types.size() - 1, person.type()));
			MapSpriteModel.Type type = model.types.get(index);
			int[] randomness = PortraitRenderer.randomness(person.seed());
			PortraitRenderer.applyRandom(randomness, person.pins().random());
			boolean dead = animation.pose() == Pose.CORPSE || animation.pose() == Pose.SKELETON;
			Subject subject = subject(person, randomness, dead, type.child());
			Subject alive = subject(person, randomness, false, type.child());
			int clothesLevel = person.clothes() < 0 ? model.clothesMax : Math.min(person.clothes(), model.clothesMax);
			MapSpriteRenderer.Look look = MapSpriteRenderer.look(model, type, alive, subject, person.pins(), clothesLevel, person.naked(), person.enemy(), person.sick(), base.parser(), notes);
			if (animation.note() != null) {
				notes.add(animation.note());
			}
			if (person.parent() && !type.child() && (animation.pose() == Pose.STANDING || animation.pose() == Pose.SWIMMING || animation.pose() == Pose.LYING)) {
				notes.add("A parent carries their baby on the map (HSprite.java:206-212), which isn't drawn here.");
			}
			notes.add("Lit as at noon with the game's default settings. The shadow is a stand-in, as the game darkens the ground rather than drawing one. Neither has been compared with a screenshot.");
			int scale = Math.max(1, Math.min(8, person.scale()));
			int direction = Math.floorMod(person.direction(), 8);
			LitCanvas.Light[] light = person.lit() ? SUN : NO_LIGHT;
			List<int[]> frames = new ArrayList<>();
			List<Step> timeline = new ArrayList<>();
			Map<String, Integer> seen = new HashMap<>();
			for (Moment moment : moments(animation, randomness)) {
				String key = moment.key();
				Integer frame = seen.get(key);
				if (frame == null) {
					frame = frames.size();
					seen.put(key, frame);
					frames.add(render(model, type, look, animation, moment, direction, subject, randomness, person, scale, light));
				}
				if (!timeline.isEmpty() && timeline.get(timeline.size() - 1).frame() == frame) {
					Step last = timeline.remove(timeline.size() - 1);
					timeline.add(new Step(frame, last.millis() + moment.millis()));
				} else {
					timeline.add(new Step(frame, moment.millis()));
				}
			}
			Moment first = moments(animation, randomness).get(0);
			int compassScale = Math.min(scale, COMPASS_SCALE);
			List<int[]> compass = new ArrayList<>();
			for (int dir = 0; dir < 8; dir++) {
				compass.add(render(model, type, look, animation, first, dir, subject, randomness, person, compassScale, light));
			}
			List<AddonRow> addons = new ArrayList<>();
			for (MapSpriteModel.Addon addon : type.addons()) {
				addons.add(new AddonRow(addon.item(), addon.index(), addon.belowHead(), addon.color() == null ? null : addon.color().name(), look.addons().containsKey(addon)));
			}
			List<Integer> random = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				random.add(PortraitRenderer.random(randomness, 64 + 8 * i, 31) & 0xFF);
			}
			int size = MapSpriteRenderer.CANVAS * scale;
			int compassSize = MapSpriteRenderer.CANVAS * compassScale;
			return new Preview(warnings.isEmpty() ? State.OK : State.WARNING, null, null, List.copyOf(warnings), List.copyOf(notes), model.types.stream().map(MapSpriteModel.Type::label).toList(), size, size, frames,
					timeline, compassSize, compass, absent, addons, model.clothesMax, random);
		}

		private static Subject subject(Person person, int[] randomness, boolean dead, boolean child) {
			return new Subject(randomness, clamp(person.age()), dead, person.noble(), person.slave(), person.parent(), person.retired(), person.sick(), equipment(person.plate()), equipment(person.leather()),
					clamp(person.grime()), clamp(person.injury()), child);
		}

		private int[] render(MapSpriteModel model, MapSpriteModel.Type type, MapSpriteRenderer.Look look, Animation animation, Moment moment, int dir, Subject subject, int[] randomness, Person person, int scale,
				LitCanvas.Light[] light) throws ReadException {
			LitCanvas canvas = MapSpriteRenderer.canvas(scale);
			int ran = PortraitRenderer.random(randomness, 0, 31);
			double grime = subject.grime();
			double injury = subject.injury();
			switch (animation.pose()) {
			case STANDING -> renderer.standing(canvas, model, type, look, dir, moment.torso(), moment.feet(), moment.tool(), false, 0, grime, injury, ran, scale);
			case SWIMMING -> renderer.standing(canvas, model, type, look, dir, moment.torso(), 0, -1, true, moment.water(), grime, injury, ran, scale);
			case LYING -> renderer.lying(canvas, model, type, look, dir, grime, injury, ran, scale);
			case SLEEPING -> {
				// Sleepers are drawn naked under the blanket (HSprites.java:558-560).
				MapSpriteRenderer.Look naked = new MapSpriteRenderer.Look(look.skin(), look.leg(), look.clothes(), true, look.addons());
				renderer.lying(canvas, model, type, naked, dir, grime, injury, ran, scale);
				renderer.blanket(canvas, model, dir, ran >> 3 & 1, scale);
			}
			case CORPSE -> renderer.corpse(canvas, model, type, look, dir, clamp(person.decay()), grime, injury, ran, base.parser(), scale);
			case SKELETON -> {
				PngCodec.Image sheet = type.child() ? model.types.get(0).skeleton() : model.types.get(model.types.size() - 1).skeleton();
				renderer.skeleton(canvas, sheet, dir, ran, scale);
			}
			}
			return canvas.lit(light);
		}

		private static double clamp(double value) {
			return Math.max(0, Math.min(1, value));
		}

		private static int equipment(int value) {
			return Math.max(0, Math.min(PortraitRenderer.MAX_EQUIPMENT, value));
		}

		private static Preview red(String problem, Integer line, List<AbsentBlock> absent) {
			return new Preview(State.RED, problem, line, List.of(), List.of(), List.of(), 0, 0, List.of(), List.of(), 0, List.of(), absent, List.of(), 0, List.of());
		}

		private record Problem(Integer line, String message) {
		}

		private static Problem problem(ReadException e) {
			String data = e.dataError();
			if (data == null) {
				return new Problem(null, "The game's parser failed unexpectedly, which doesn't prove the text is wrong: " + e.getMessage());
			}
			Matcher keyed = KEYED_ERROR.matcher(data);
			if (keyed.matches()) {
				return new Problem(Integer.parseInt(keyed.group(1)) + 1, keyed.group(2) + ": " + keyed.group(3).trim());
			}
			SyntaxChecker.SyntaxProblem translated = SyntaxChecker.translate(data);
			return new Problem(translated.line(), translated.message());
		}
	}

	/**
	 * One stretch of an animation where nothing changes.
	 *
	 * @param feet the feet row, for standing
	 * @param tool the tool row, or -1
	 * @param water the water frame, for swimming
	 */
	record Moment(int torso, int feet, int tool, int water, int millis) {
		String key() {
			return torso + "/" + feet + "/" + tool + "/" + water;
		}
	}

	/**
	 * The animation's moments over one loop, worked out exactly: each part steps at its own rate, and the loop lasts
	 * until every part is back at its start together. Timers start at 0, as a new animation's do.
	 */
	static List<Moment> moments(Animation animation, int[] randomness) {
		Pose pose = animation.pose();
		if (pose != Pose.STANDING && pose != Pose.SWIMMING) {
			return List.of(new Moment(0, 0, -1, 0, 1000));
		}
		List<int[]> channels = new ArrayList<>();
		int length = animation.torsos().size();
		if (animation.fps() > 0 && length > 1) {
			channels.add(new int[] { animation.fps(), length });
		}
		if (pose == Pose.STANDING && animation.feetFps() > 0) {
			channels.add(new int[] { animation.feetFps(), FEET.length });
		}
		if (pose == Pose.SWIMMING) {
			// Water steps with GAME.intervals().get05(), 5 a second, through 4 frames (HSpriteConst.java:92).
			channels.add(new int[] { 5, 4 });
		}
		int swimOffset = PortraitRenderer.random(randomness, 6, 31);
		if (channels.isEmpty()) {
			return List.of(momentAt(animation, 0, 1, swimOffset, 1000));
		}
		long base = 1;
		for (int[] channel : channels) {
			base = lcm(base, channel[0]);
		}
		long loop = 1;
		for (int[] channel : channels) {
			loop = lcm(loop, base / channel[0] * channel[1]);
		}
		long cap = MAX_LOOP_MS * base / 1000;
		loop = Math.min(loop, cap);
		java.util.TreeSet<Long> changes = new java.util.TreeSet<>();
		for (int[] channel : channels) {
			long step = base / channel[0];
			for (long t = 0; t < loop; t += step) {
				changes.add(t);
			}
		}
		changes.add(loop);
		List<Moment> moments = new ArrayList<>();
		Long previous = null;
		for (long t : changes) {
			if (previous != null) {
				long startMs = previous * 1000 / base;
				long endMs = t * 1000 / base;
				moments.add(momentAt(animation, previous, base, swimOffset, (int) Math.max(1, endMs - startMs)));
			}
			previous = t;
		}
		return moments;
	}

	private static Moment momentAt(Animation animation, long time, long base, int swimOffset, int millis) {
		List<Integer> torsos = animation.torsos();
		if (animation.pose() == Pose.SWIMMING) {
			long step = time * 5 / base;
			int tt = (int) ((step + swimOffset) & 0xFF) % SWIM.size();
			return new Moment(SWIM.get(tt), 0, -1, (int) (step & 3), millis);
		}
		int torsoIndex = animation.fps() > 0 ? (int) (time * animation.fps() / base % torsos.size()) : 0;
		int feet = animation.feetFps() > 0 ? FEET[(int) (time * animation.feetFps() / base % FEET.length)] : MapSpriteRenderer.FEET_NONE;
		int tool = animation.tools().isEmpty() ? -1 : animation.tools().get(torsoIndex);
		return new Moment(torsos.get(torsoIndex), feet, tool, 0, millis);
	}

	private static long lcm(long a, long b) {
		return a / gcd(a, b) * b;
	}

	private static long gcd(long a, long b) {
		return b == 0 ? a : gcd(b, a % b);
	}
}
