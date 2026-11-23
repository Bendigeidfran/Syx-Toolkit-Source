package syx.core.wizard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import syx.core.GameParser;
import syx.core.ModEntry;
import syx.core.PngCodec;
import syx.core.PortraitLayers;
import syx.core.PortraitLayers.Condition;
import syx.core.PortraitLayers.FrameRef;
import syx.core.PortraitLayers.Layer;
import syx.core.PortraitSim;

/**
 * The Portrait Design Wizard: builds a race's portraits as layers of frames picked on face sheets, and its crown,
 * raider and mercenary headwear, without the person writing the sprite file. Every answer is saved at once
 * through {@link PortraitSim.Session#save}, which refuses anything the game couldn't load and checks the result
 * with the game's parser, so this decides nothing about what's valid. Each call reads the file afresh, so a change
 * made in the Race Portrait Sim between questions is what the next question shows.
 *
 * <p>Its questions loop (a portrait's layers are changed one at a time), so where it is is kept as a step id that
 * says which portrait and layer: {@code portrait:0}, {@code layer-frames:0:3}, {@code layer-frames:0:new:Eyes}.
 */
public final class PortraitWizardEngine {

	public static final String ID = "portrait";
	public static final String TITLE = "Portrait Design Wizard";
	public static final String START_PROMPT = "Which race's portraits would you like to design?";

	private static final String OWN_FILE = "own-file";
	private static final String START = "keep-or-empty";
	private static final String SHEETS = "sheets";
	private static final String COLOURS = "colours";
	private static final String DONE = "done";
	private static final int MAX_HISTORY = 200;

	/** The people a layer can be limited to, as the stats the game registers for each type and class (SValues.java:106-127, HTYPES.java). */
	private static final Map<String, Condition> WHO = new LinkedHashMap<>();
	/** Armour, as vanilla's own portraits test it (HUMAN.txt's ADDONS); the sim draws it with its Plate and Leather controls. */
	private static final Map<String, List<Condition>> ARMOUR = new LinkedHashMap<>();

	static {
		WHO.put("Only nobles", new Condition("EQUAL", "CLASS_NOBLE", "1"));
		WHO.put("Only citizens", new Condition("EQUAL", "CLASS_CITIZEN", "1"));
		WHO.put("Only slaves", new Condition("EQUAL", "CLASS_SLAVE", "1"));
		WHO.put("Everyone but slaves", new Condition("NEQUAL", "CLASS_SLAVE", "1"));
		WHO.put("Everyone but nobles", new Condition("NEQUAL", "CLASS_NOBLE", "1"));
		WHO.put("Only retirees", new Condition("EQUAL", "TYPE_RETIREE", "1"));
		WHO.put("Only parents", new Condition("EQUAL", "TYPE_PARENT", "1"));
		WHO.put("Only slave parents", new Condition("EQUAL", "TYPE_PARENT_SLAVE", "1"));
		WHO.put("Only slave children", new Condition("EQUAL", "TYPE_CHILD_SLAVE", "1"));
		ARMOUR.put("Only with plate armour", List.of(new Condition("GREATER", "EQUIP_BATTLE_ARMOUR_PLATE_I", "0")));
		ARMOUR.put("Only without plate armour", List.of(new Condition("EQUAL", "EQUIP_BATTLE_ARMOUR_PLATE_I", "0")));
		ARMOUR.put("Only with leather armour", List.of(new Condition("GREATER", "EQUIP_BATTLE_ARMOUR_LEATHER_I", "0")));
		ARMOUR.put("Only without armour", List.of(new Condition("EQUAL", "EQUIP_BATTLE_ARMOUR_PLATE_I", "0"), new Condition("EQUAL", "EQUIP_BATTLE_ARMOUR_LEATHER_I", "0")));
	}

	private final java.nio.file.Path game;
	private final WizardStore store;

	public PortraitWizardEngine(java.nio.file.Path game, WizardStore store) {
		this.game = game;
		this.store = store;
	}

	/** What's open while a call runs. */
	private record State(PortraitSim.Session sim, ModEntry mod, WizardStore.Session session, PortraitSim.Race race, PortraitSim.Opened opened) {
		String text() {
			return opened.text();
		}

		String name() {
			return session.stem();
		}
	}

	// ---- Starting and moving

	/** The first question: which race. Nothing is kept until it's answered. */
	public WizardQuestion begin(ModEntry mod) throws WizardEngine.Refused, IOException, GameParser.UnavailableException {
		usable();
		try (PortraitSim.Session sim = PortraitSim.open(game, mod)) {
			return raceQuestion(mod, sim, null, null);
		}
	}

	/** Answers the first question with a race's name. A race on the game's file is offered its own copy next. */
	public WizardQuestion start(ModEntry mod, String race) throws WizardEngine.Refused, IOException, GameParser.UnavailableException {
		usable();
		try (PortraitSim.Session sim = PortraitSim.open(game, mod)) {
			PortraitSim.Race found = sim.races().stream().filter(r -> r.name().equalsIgnoreCase(race == null ? "" : race.strip())).findFirst().orElse(null);
			if (found == null) {
				String message = mod.folderName() + " has no race called " + race + ".";
				throw new WizardEngine.Refused(message, raceQuestion(mod, sim, null, message));
			}
			if (!found.editable() && !found.canCopy()) {
				String message = found.name() + "'s portraits can't be designed here: " + found.note();
				throw new WizardEngine.Refused(message, raceQuestion(mod, sim, null, message));
			}
			if (store.find(mod.folderName(), ID, found.name()).isPresent()) {
				return question(mod, found.name());
			}
			String first = found.editable() ? START : OWN_FILE;
			WizardStore.Session session = new WizardStore.Session(mod.folderName(), ID, found.name(), found.name(), null, first, List.of());
			store.put(session);
			return ask(state(sim, mod, session), null);
		}
	}

	public WizardQuestion question(ModEntry mod, String race) throws WizardEngine.Refused, IOException, GameParser.UnavailableException {
		WizardStore.Session session = session(mod, race);
		try (PortraitSim.Session sim = PortraitSim.open(game, mod)) {
			return ask(state(sim, mod, session), null);
		}
	}

	public WizardQuestion skip(ModEntry mod, String race) throws WizardEngine.Refused, IOException, GameParser.UnavailableException {
		WizardStore.Session session = session(mod, race);
		try (PortraitSim.Session sim = PortraitSim.open(game, mod)) {
			State state = state(sim, mod, session);
			WizardQuestion asked = ask(state, null);
			if (!asked.canSkip()) {
				throw new WizardEngine.Refused("This question can't be skipped.", asked);
			}
			return moveTo(state, afterSkip(state), null);
		}
	}

	public WizardQuestion back(ModEntry mod, String race) throws WizardEngine.Refused, IOException, GameParser.UnavailableException {
		WizardStore.Session session = session(mod, race);
		try (PortraitSim.Session sim = PortraitSim.open(game, mod)) {
			State state = state(sim, mod, session);
			List<String> answered = new ArrayList<>(session.answered());
			while (!answered.isEmpty()) {
				String last = answered.remove(answered.size() - 1);
				if (!last.equals(session.step()) && !last.equals(OWN_FILE)) {
					WizardStore.Session moved = session.at(last, answered);
					store.put(moved);
					return ask(state(sim, mod, moved), null);
				}
			}
			throw new WizardEngine.Refused("There's nothing to go back to. What's saved can be changed by answering again, or in the Race Portrait Sim.", ask(state, null));
		}
	}

	public void forget(ModEntry mod, String race) throws WizardEngine.Refused, IOException {
		session(mod, race);
		store.remove(mod.folderName(), ID, race);
	}

	/** The sprite file, for opening the race in the Race Portrait Sim. */
	public String race(ModEntry mod, String race) throws WizardEngine.Refused {
		return session(mod, race).stem();
	}

	/** Answers the question the wizard is on, saves it, and gives the next one. */
	public WizardQuestion answer(ModEntry mod, String race, Map<String, Object> answer) throws WizardEngine.Refused, IOException, GameParser.UnavailableException {
		WizardStore.Session session = session(mod, race);
		try (PortraitSim.Session sim = PortraitSim.open(game, mod)) {
			State state = state(sim, mod, session);
			WizardQuestion asked = ask(state, null);
			String step = currentStep(state);
			String[] parts = step.split(":", 4);
			switch (parts[0]) {
			case OWN_FILE -> {
				PortraitSim.Copied copied;
				try {
					copied = sim.copyToMod(state.name());
				} catch (PortraitSim.Refused e) {
					throw new WizardEngine.Refused(e.getMessage(), asked.refusedWith(e.getMessage(), List.of()));
				}
				return moveTo(state(sim, mod, session), START, "I've given " + state.name() + " its own copy, " + copied.file() + ", and pointed the race at it.");
			}
			case START -> {
				String choice = choice(answer);
				if ("empty".equals(choice)) {
					String text = state.text();
					for (int p = 0; p < PortraitLayers.portraits(text).size(); p++) {
						text = PortraitLayers.clear(text, p);
					}
					save(state, text, asked);
					return moveTo(state, SHEETS, "Every portrait is empty now. Add layers to each.");
				}
				if (!"keep".equals(choice)) {
					throw refuse(asked, "Choose one of the answers.");
				}
				return moveTo(state, SHEETS, null);
			}
			case SHEETS -> {
				return answerSheets(state, asked, answer);
			}
			case COLOURS -> {
				String choice = choice(answer);
				if ("next".equals(choice)) {
					return moveTo(state, portraitStep(0, state), null);
				}
				if ("add".equals(choice)) {
					return moveTo(state, "colour-new", null);
				}
				if (choice != null && choice.startsWith("change:")) {
					return moveTo(state, "colour:" + choice.substring("change:".length()), null);
				}
				throw refuse(asked, "Choose one of the answers.");
			}
			case "colour-new" -> {
				String key = PortraitLayers.keyFrom(text(answer.get("value")));
				if (key == null) {
					throw refuse(asked, "A colour's name needs at least one letter or digit.");
				}
				if (PortraitLayers.colours(state.text()).stream().anyMatch(c -> c.name().equalsIgnoreCase(key))) {
					throw refuse(asked, "There's already a colour called " + key + ".");
				}
				return moveTo(state, "colour:" + key, null);
			}
			case "colour" -> {
				return answerColour(state, asked, parts[1], answer);
			}
			case "portrait" -> {
				return answerLayers(state, asked, Integer.parseInt(parts[1]), choice(answer));
			}
			case "layer-name" -> {
				String typed = text(answer.get("value"));
				String clean = typed == null ? "" : typed.replaceAll("[\\r\\n]+", " ").strip();
				if (clean.isEmpty()) {
					throw refuse(asked, "Give the layer a name, such as Eyes or Back hair.");
				}
				return moveTo(state, "layer-frames:" + parts[1] + ":new:" + clean, null);
			}
			case "layer-frames" -> {
				return answerFrames(state, asked, parts, answer);
			}
			case "layer-tweak" -> {
				return answerTweak(state, asked, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), answer);
			}
			case "headwear" -> {
				return answerHeadwear(state, asked, parts[1], answer);
			}
			case DONE -> {
				store.remove(mod.folderName(), ID, state.name());
				return new WizardQuestion(ID, mod.folderName(), state.name(), state.name(), DONE, "finished", asked.prompt(), asked.help(), asked.evidence(), asked.position(), asked.total(),
						"Finished. " + state.name() + "'s portraits are saved, and the Race Portrait Sim shows them with every control.", List.of(), null, List.of(), List.of(), List.of(), null,
						false, false, asked.history(), asked.portrait(), null);
			}
			default -> throw refuse(asked, "This question can't be answered.");
			}
		}
	}

	// ---- Answers

	private WizardQuestion answerSheets(State state, WizardQuestion asked, Map<String, Object> answer) throws WizardEngine.Refused, IOException {
		Map<String, Object> values = answer.get("values") instanceof Map<?, ?> given ? cast(given) : Map.of();
		List<String> face = names(values.get("face"));
		List<String> addons = names(values.get("headwear"));
		List<String> offeredFace = gameSheets(state, false);
		List<String> offeredAddons = gameSheets(state, true);
		for (String name : face) {
			if (!offeredFace.contains(name)) {
				throw refuse(asked, name + " isn't one of the game's sheets these portraits use.");
			}
		}
		for (String name : addons) {
			if (!offeredAddons.contains(name)) {
				throw refuse(asked, name + " isn't one of the game's addon sheets this race's headwear uses.");
			}
		}
		if (face.isEmpty() && addons.isEmpty()) {
			return moveTo(state, COLOURS, null);
		}
		List<String> copiedFace = new ArrayList<>();
		List<String> copiedAddons = new ArrayList<>();
		String text = state.text();
		List<String> said = new ArrayList<>();
		try {
			for (String name : face) {
				String copy = state.sim().copySheet(false, name, state.name());
				copiedFace.add(copy);
				text = PortraitLayers.renameSheet(text, false, name, copy);
				said.add(name + " as " + copy);
			}
			for (String name : addons) {
				String copy = state.sim().copySheet(true, name, state.name());
				copiedAddons.add(copy);
				text = PortraitLayers.renameSheet(text, true, name, copy);
				said.add(name + " as " + copy);
			}
			save(state, text, asked);
		} catch (PortraitSim.Refused | WizardEngine.Refused | IOException | RuntimeException e) {
			for (String copy : copiedFace) {
				state.sim().removeCopiedSheet(false, copy);
			}
			for (String copy : copiedAddons) {
				state.sim().removeCopiedSheet(true, copy);
			}
			if (e instanceof WizardEngine.Refused refused) {
				throw refused;
			}
			throw refuse(asked, "The sheets couldn't be copied, and nothing was kept: " + e.getMessage());
		}
		return moveTo(state, COLOURS, "I've copied " + String.join(", ", said) + " into your mod, and pointed the portraits at the copies. Repaint them as you like: only this race uses them.");
	}

	private WizardQuestion answerColour(State state, WizardQuestion asked, String name, Map<String, Object> answer) throws WizardEngine.Refused, IOException {
		Map<String, Object> values = answer.get("values") instanceof Map<?, ?> given ? cast(given) : Map.of();
		PortraitLayers.Colour colour = PortraitLayers.colours(state.text()).stream().filter(c -> c.name().equals(name)).findFirst().orElse(null);
		List<PortraitLayers.Rgb> rgb = colour == null || colour.values() == null ? List.of() : colour.values();
		if (values.containsKey("VALUES")) {
			rgb = new ArrayList<>();
			String lines = text(values.get("VALUES"));
			for (String line : lines == null ? new String[0] : lines.split("[\\r\\n,]+")) {
				if (line.isBlank()) {
					continue;
				}
				String[] channels = line.strip().split("_");
				try {
					if (channels.length != 3) {
						throw new NumberFormatException();
					}
					int r = Integer.parseInt(channels[0].strip());
					int g = Integer.parseInt(channels[1].strip());
					int b = Integer.parseInt(channels[2].strip());
					if (r < 0 || g < 0 || b < 0 || r > 255 || g > 255 || b > 255) {
						throw new NumberFormatException();
					}
					rgb.add(new PortraitLayers.Rgb(r, g, b));
				} catch (NumberFormatException e) {
					throw refuse(asked, "Each colour is written R_G_B, from 0 to 255, such as 210_169_151: " + line.strip() + " isn't.");
				}
			}
		} else if (colour != null && colour.values() == null) {
			rgb = null;
		}
		if (rgb != null && rgb.isEmpty()) {
			throw refuse(asked, "A colour needs at least one entry. The game stops loading without one (RColors.java:130,138).");
		}
		if (rgb != null && rgb.size() > 16) {
			throw refuse(asked, "A colour can have at most 16 entries. The game stops loading with more (RColors.java:120-125).");
		}
		Map<String, String> flags = new LinkedHashMap<>();
		for (String flag : List.of("TURNS_GRAY_WHEN_OLD", "TURNS_WHITE_WHEN_DEAD", "TURNS_SICKLY")) {
			if (values.containsKey(flag)) {
				flags.put(flag, values.get(flag) == null ? null : text(values.get(flag)));
			}
		}
		String text = state.text();
		if (rgb == null) {
			// VALUES is written as a range this can't show, so only the flags change.
			text = PortraitLayers.setColourFlags(text, name, flags);
		} else {
			text = PortraitLayers.setColour(text, name, rgb, flags);
		}
		save(state, text, asked);
		return moveTo(state, COLOURS, null);
	}

	private WizardQuestion answerLayers(State state, WizardQuestion asked, int portrait, String choice) throws WizardEngine.Refused, IOException {
		if (choice == null) {
			throw refuse(asked, "Choose one of the answers.");
		}
		if (choice.equals("add")) {
			return moveTo(state, "layer-name:" + portrait, null);
		}
		if (choice.equals("next")) {
			return moveTo(state, after(state, portrait), null);
		}
		String[] parts = choice.split(":");
		List<Layer> layers = PortraitLayers.layers(state.text(), portrait);
		int layer;
		try {
			layer = Integer.parseInt(parts[1]);
		} catch (RuntimeException e) {
			throw refuse(asked, "Choose one of the answers.");
		}
		if (layer < 0 || layer >= layers.size()) {
			throw refuse(asked, "There's no layer " + (layer + 1) + ".");
		}
		String label = label(layers.get(layer));
		switch (parts[0]) {
		case "frames" -> {
			return moveTo(state, "layer-frames:" + portrait + ":" + layer, null);
		}
		case "tweak" -> {
			return moveTo(state, "layer-tweak:" + portrait + ":" + layer, null);
		}
		case "up", "down", "top", "bottom" -> {
			int to = switch (parts[0]) {
			case "up" -> layer + 1;
			case "down" -> layer - 1;
			case "top" -> layers.size() - 1;
			default -> 0;
			};
			if (to == layer && (parts[0].equals("top") || parts[0].equals("bottom"))) {
				throw refuse(asked, label + " is already at the " + parts[0] + ".");
			}
			if (to < 0 || to >= layers.size()) {
				throw refuse(asked, label + " is already at the " + (to < 0 ? "bottom." : "top."));
			}
			save(state, PortraitLayers.move(state.text(), portrait, layer, to), asked);
			return stay(state, label + " is now drawn " + (to > layer ? "over " : "under ") + label(layers.get(to)) + ".");
		}
		case "remove" -> {
			save(state, PortraitLayers.remove(state.text(), portrait, layer), asked);
			return stay(state, label + " is removed.");
		}
		default -> throw refuse(asked, "Choose one of the answers.");
		}
	}

	private WizardQuestion answerFrames(State state, WizardQuestion asked, String[] parts, Map<String, Object> answer) throws WizardEngine.Refused, IOException {
		int portrait = Integer.parseInt(parts[1]);
		List<FrameRef> frames = new ArrayList<>();
		if (!(answer.get("frames") instanceof List<?> given) || given.isEmpty()) {
			throw refuse(asked, "Pick at least one frame. A layer without frames stops the game loading (RaceFrameMaker.java:53).");
		}
		for (Object item : given) {
			FrameRef ref = FrameRef.parse(text(item));
			if (ref == null) {
				throw refuse(asked, "Frames are given as SHEET: INDEX, such as Eyes: 3. " + item + " isn't.");
			}
			PortraitSim.SheetInfo sheet = state.sim().sheet(false, ref.file());
			if (sheet == null) {
				throw refuse(asked, "There's no face sheet called " + ref.file() + ".");
			}
			if (sheet.problem() != null) {
				throw refuse(asked, ref.file() + " can't be used. " + sheet.problem());
			}
			if (ref.index() < 0 || ref.index() >= sheet.frames()) {
				throw refuse(asked, ref.file() + " has frames 0 to " + (sheet.frames() - 1) + ", so there's no " + ref.index() + ".");
			}
			frames.add(ref);
		}
		if (parts[2].equals("new")) {
			String name = parts.length > 3 ? parts[3] : null;
			int count = PortraitLayers.layers(state.text(), portrait).size();
			int at = count;
			if (answer.get("at") != null) {
				try {
					at = (int) Double.parseDouble(text(answer.get("at")).strip());
				} catch (NumberFormatException e) {
					throw refuse(asked, "at is where the layer goes: 0 for the bottom, up to " + count + " for the top.");
				}
				if (at < 0 || at > count) {
					throw refuse(asked, "at is where the layer goes: 0 for the bottom, up to " + count + " for the top.");
				}
			}
			save(state, PortraitLayers.addLayer(state.text(), portrait, at, name, frames), asked);
			String where = at == count ? "on top of the others" : at == 0 ? "at the bottom, behind the others" : "under " + label(PortraitLayers.layers(state.text(), portrait).get(at));
			return moveTo(fresh(state), "layer-tweak:" + portrait + ":" + at, "I've added " + (name == null ? "the layer" : name) + " " + where + ".");
		}
		int layer = Integer.parseInt(parts[2]);
		save(state, PortraitLayers.setFrames(state.text(), portrait, layer, frames), asked);
		return moveTo(state, portraitStep(portrait, state), null);
	}

	private WizardQuestion answerTweak(State state, WizardQuestion asked, int portrait, int index, Map<String, Object> answer) throws WizardEngine.Refused, IOException {
		if (!(answer.get("values") instanceof Map<?, ?> given)) {
			throw refuse(asked, "Give the values that change, by their key.");
		}
		Map<String, Object> values = cast(given);
		List<Layer> layers = PortraitLayers.layers(state.text(), portrait);
		if (index < 0 || index >= layers.size()) {
			throw refuse(asked, "There's no layer " + (index + 1) + ".");
		}
		Layer layer = layers.get(index);
		String text = state.text();
		List<String> unknown = new ArrayList<>();
		boolean conditionsChange = false;
		String who = conditionLabel(layer, true);
		String armour = conditionLabel(layer, false);
		for (Map.Entry<String, Object> item : values.entrySet()) {
			String key = item.getKey();
			String value = item.getValue() == null ? null : text(item.getValue());
			switch (key) {
			case "name" -> text = PortraitLayers.setName(text, portrait, index, value);
			case "COLOR", "FRAME_OCCURRENCE", "OFF_X", "OFF_Y", "OFF_X_RANDOM", "OFF_Y_RANDOM", "OPACITY", "STAINS" -> {
				if (value != null && value.isBlank()) {
					value = null;
				}
				if (value != null && !key.equals("COLOR") && !key.equals("STAINS") && !value.strip().matches("-?(\\d+\\.?\\d*|\\.\\d+)")) {
					throw refuse(asked, "Give a number for " + tweakLabel(key) + ".");
				}
				text = PortraitLayers.setTag(text, portrait, index, key, value == null ? null : value.strip());
			}
			case "link" -> {
				if (value == null) {
					text = PortraitLayers.setTag(text, portrait, index, "FRAME_RANDOM", null);
				} else {
					Layer other = null;
					for (Layer candidate : layers) {
						if (candidate.index() != index && linkLabel(candidate).equals(value)) {
							other = candidate;
						}
					}
					if (other == null) {
						throw refuse(asked, "There's no other layer called " + value + ".");
					}
					text = PortraitLayers.setTag(text, portrait, index, "FRAME_RANDOM", String.valueOf(other.random()));
				}
			}
			case "who" -> {
				if (value != null && !WHO.containsKey(value)) {
					throw refuse(asked, "Choose who has it from the list.");
				}
				who = value;
				conditionsChange = true;
			}
			case "armour" -> {
				if (value != null && !ARMOUR.containsKey(value)) {
					throw refuse(asked, "Choose the armour from the list.");
				}
				armour = value;
				conditionsChange = true;
			}
			default -> unknown.add(key);
			}
		}
		if (!unknown.isEmpty()) {
			throw refuse(asked, "This question has nothing called " + String.join(", ", unknown) + ".");
		}
		if (conditionsChange) {
			List<Condition> conditions = new ArrayList<>();
			if (who != null && WHO.containsKey(who)) {
				conditions.add(WHO.get(who));
			}
			if (armour != null && ARMOUR.containsKey(armour)) {
				conditions.addAll(ARMOUR.get(armour));
			}
			text = PortraitLayers.setConditions(text, portrait, index, conditions);
		}
		save(state, text, asked);
		return moveTo(state, portraitStep(portrait, state), null);
	}

	private WizardQuestion answerHeadwear(State state, WizardQuestion asked, String kind, Map<String, Object> answer) throws WizardEngine.Refused, IOException {
		if (!(answer.get("entries") instanceof List<?> given) || given.isEmpty()) {
			throw refuse(asked, "Give at least one entry. The game stops loading without " + kind + " (RCrown.java:33-34).");
		}
		List<PortraitLayers.Addon> addons = new ArrayList<>();
		for (Object item : given) {
			if (!(item instanceof Map<?, ?> entry)) {
				throw refuse(asked, "Each entry is {file, offx, offy}.");
			}
			String file = text(entry.get("file"));
			String offX = entry.get("offx") == null ? "0" : text(entry.get("offx")).strip();
			String offY = entry.get("offy") == null ? "0" : text(entry.get("offy")).strip();
			if (file == null || file.isBlank()) {
				throw refuse(asked, "Each entry names an addon sheet.");
			}
			PortraitSim.SheetInfo sheet = state.sim().sheet(true, file.strip());
			if (sheet == null) {
				throw refuse(asked, "There's no addon sheet called " + file.strip() + ".");
			}
			if (!offX.matches("-?\\d+") || !offY.matches("-?\\d+")) {
				throw refuse(asked, "Offsets are whole numbers.");
			}
			addons.add(new PortraitLayers.Addon(file.strip(), offX, offY));
		}
		save(state, PortraitLayers.setAddons(state.text(), kind, addons), asked);
		int at = PortraitLayers.ADDON_KINDS.indexOf(kind);
		return moveTo(state, at + 1 < PortraitLayers.ADDON_KINDS.size() ? "headwear:" + PortraitLayers.ADDON_KINDS.get(at + 1) : DONE, null);
	}

	// ---- Saving

	/** Saves the portrait text, or refuses with the game's reason. Nothing changing writes nothing. */
	private void save(State state, String text, WizardQuestion asked) throws WizardEngine.Refused, IOException {
		if (text.equals(state.text())) {
			return;
		}
		java.nio.file.Path file = state.sim().spriteFilePath(state.race().spriteFile());
		if (java.nio.file.Files.exists(file) && !file.toRealPath().startsWith(state.mod().root().toRealPath())) {
			String message = file.getFileName() + " is a link to somewhere outside the mod, so nothing was written through it.";
			throw new WizardEngine.Refused(message, asked.refusedWith(message, List.of()));
		}
		try {
			state.sim().save(state.name(), state.opened().revision(), text);
		} catch (PortraitSim.Refused e) {
			throw new WizardEngine.Refused(e.getMessage(), asked.refusedWith(e.getMessage(), List.of()));
		}
	}

	private State fresh(State state) throws WizardEngine.Refused, IOException {
		return state(state.sim(), state.mod(), state.session());
	}

	private WizardQuestion moveTo(State state, String next, String note) throws WizardEngine.Refused, IOException {
		WizardStore.Session session = state.session();
		List<String> answered = new ArrayList<>(session.answered());
		answered.add(currentStep(state));
		while (answered.size() > MAX_HISTORY) {
			answered.remove(0);
		}
		WizardStore.Session moved = session.at(next, answered);
		store.put(moved);
		return ask(state(state.sim(), state.mod(), moved), note);
	}

	/** Asks the same question again after a change, such as a layer moved. */
	private WizardQuestion stay(State state, String note) throws WizardEngine.Refused, IOException {
		return ask(fresh(state), note);
	}

	private String afterSkip(State state) {
		String step = currentStep(state);
		String[] parts = step.split(":", 4);
		return switch (parts[0]) {
		case START -> SHEETS;
		case SHEETS -> COLOURS;
		case COLOURS -> portraitStep(0, state);
		case "colour", "colour-new" -> COLOURS;
		case "portrait" -> after(state, Integer.parseInt(parts[1]));
		case "layer-name" -> "portrait:" + parts[1];
		case "layer-frames", "layer-tweak" -> "portrait:" + parts[1];
		case "headwear" -> {
			int at = PortraitLayers.ADDON_KINDS.indexOf(parts[1]);
			yield at + 1 < PortraitLayers.ADDON_KINDS.size() ? "headwear:" + PortraitLayers.ADDON_KINDS.get(at + 1) : DONE;
		}
		default -> DONE;
		};
	}

	private String portraitStep(int portrait, State state) {
		return PortraitLayers.portraits(state.text()).isEmpty() ? "headwear:CROWN" : "portrait:" + portrait;
	}

	private String after(State state, int portrait) {
		return portrait + 1 < PortraitLayers.portraits(state.text()).size() ? "portrait:" + (portrait + 1) : "headwear:CROWN";
	}

	// ---- What's open

	private void usable() throws WizardEngine.Refused {
		if (!store.usable()) {
			throw new WizardEngine.Refused(store.problem(), null);
		}
	}

	private WizardStore.Session session(ModEntry mod, String race) throws WizardEngine.Refused {
		usable();
		return store.find(mod.folderName(), ID, race == null ? "" : race)
				.orElseThrow(() -> new WizardEngine.Refused(mod.folderName() + " has no unfinished portrait wizard for " + race + ". list_wizards shows them.", null));
	}

	private State state(PortraitSim.Session sim, ModEntry mod, WizardStore.Session session) throws WizardEngine.Refused, IOException {
		PortraitSim.Opened opened;
		try {
			opened = sim.open(session.stem());
		} catch (PortraitSim.Refused e) {
			throw new WizardEngine.Refused(e.getMessage() + " Forget this wizard to start again.", null);
		}
		PortraitSim.Race race = opened.race();
		if (!race.editable() && !(race.canCopy() && OWN_FILE.equals(session.step()))) {
			throw new WizardEngine.Refused(race.name() + "'s portraits can't be edited here: " + race.note(), null);
		}
		return new State(sim, mod, session, race, opened);
	}

	/** The step the session is on, moved on from one that no longer fits the file, such as a removed layer's. */
	private static String currentStep(State state) {
		String step = state.session().step();
		String[] parts = step.split(":", 4);
		try {
			int portraits = PortraitLayers.portraits(state.text()).size();
			switch (parts[0]) {
			case "portrait", "layer-name" -> {
				if (Integer.parseInt(parts[1]) >= portraits) {
					return "headwear:CROWN";
				}
			}
			case "layer-frames", "layer-tweak" -> {
				int portrait = Integer.parseInt(parts[1]);
				if (portrait >= portraits) {
					return "headwear:CROWN";
				}
				if (!parts[2].equals("new") && Integer.parseInt(parts[2]) >= PortraitLayers.layers(state.text(), portrait).size()) {
					return "portrait:" + portrait;
				}
			}
			default -> {
				// Fine as it is.
			}
			}
		} catch (RuntimeException e) {
			return state.text().isEmpty() ? DONE : COLOURS;
		}
		return step;
	}

	// ---- Questions

	private WizardQuestion raceQuestion(ModEntry mod, PortraitSim.Session sim, String note, String refused) throws IOException {
		List<WizardQuestion.ChoiceView> choices = new ArrayList<>();
		List<String> cant = new ArrayList<>();
		for (PortraitSim.Race race : sim.races()) {
			if (race.editable() || race.canCopy()) {
				choices.add(new WizardQuestion.ChoiceView(race.name(), race.name(), false));
			} else {
				cant.add(race.name() + ": " + race.note());
			}
		}
		String help = choices.isEmpty() ? "There's no race in this mod whose portraits can be designed here. Make one first with the Asset Creation Wizard."
				: "Its portraits are in its sprite file, so a race sharing that file changes too.";
		String said = note;
		if (!cant.isEmpty()) {
			said = (note == null ? "" : note + " ") + "Not offered: " + String.join(" ", cant);
		}
		return new WizardQuestion(ID, mod.folderName(), null, null, "race", "race", START_PROMPT, help, "A race's portraits are in the sprite file its SPRITE_FILE names (RAppearence.java:47).", 1, 1,
				said, choices, null, List.of(), List.of(), List.of(), refused, false, false, List.of(started()), null, null);
	}

	private static WizardQuestion.Exchange started() {
		return new WizardQuestion.Exchange("start", Wizards.START_PROMPT, TITLE, List.of());
	}

	private WizardQuestion ask(State state, String note) throws IOException {
		String step = currentStep(state);
		String[] parts = step.split(":", 4);
		String text = state.text();
		List<String> portraits = PortraitLayers.portraits(text);
		String race = state.name();
		List<WizardQuestion.ChoiceView> choices = new ArrayList<>();
		List<WizardQuestion.FieldView> fields = new ArrayList<>();
		WizardQuestion.PortraitView view = new WizardQuestion.PortraitView(race, -1, portraits, List.of(), null, null, null);
		String kind;
		String prompt;
		String help;
		String evidence;
		boolean canSkip = true;
		List<String> notes = new ArrayList<>();
		if (note != null) {
			notes.add(note);
		}
		switch (parts[0]) {
		case OWN_FILE -> {
			kind = "choice";
			prompt = race + " uses the game's own sprite file. Shall I give it a copy of its own?";
			help = "A race's portraits are in that file, so changing the game's would change every race using it. The copy has a new name, and only " + race + "'s file is pointed at it.";
			evidence = "RAppearence.java:47 opens the file SPRITE_FILE names; a mod's file of the same name is merged into the game's.";
			choices.add(new WizardQuestion.ChoiceView("copy", "Copy it into my mod", false));
			canSkip = false;
		}
		case START -> {
			kind = "choice";
			prompt = "Shall I keep " + race + "'s portraits to change, or start each one empty?";
			help = "Keeping them lets you change their layers one at a time. Empty gives each portrait no layers, which the game draws as no portrait until you add some.";
			evidence = "RPortrait.java:17-20: a type without FACE has an empty portrait.";
			choices.add(new WizardQuestion.ChoiceView("keep", "Keep them", false));
			choices.add(new WizardQuestion.ChoiceView("empty", "Start empty", false));
			if (!state.race().sharedWith().isEmpty()) {
				notes.add("This file is also used by " + String.join(", ", state.race().sharedWith()) + ", so their portraits change too.");
			}
		}
		case SHEETS -> {
			List<String> face = gameSheets(state, false);
			List<String> addons = gameSheets(state, true);
			kind = "fields";
			prompt = "Which of the game's sheets would you like copies of, to repaint for " + race + "?";
			help = "The rest stay the game's, shared with other races, and look as the game's do. A copy has a new name, so only " + race + " uses it.";
			evidence = "The game finds a face sheet in the mod before the game's (RaceFrameMaker.java:93,121), so a copy under the game's name would change every race. Face sheets are 416 wide, addon sheets 104 (RaceFrameMaker.java:128-129, RCrown.java:61).";
			if (!face.isEmpty()) {
				fields.add(field("face", "Face sheets these portraits use", "Each is copied whole, art and normals.", "picks", "set", "", face, null, null, null, null));
			}
			if (!addons.isEmpty()) {
				fields.add(field("headwear", "Crown, raider and mercenary sheets", "", "picks", "set", "", addons, null, null, null, null));
			}
			if (fields.isEmpty()) {
				notes.add("Every sheet these portraits use is already in your mod.");
			}
		}
		case COLOURS -> {
			kind = "choice";
			prompt = "What colours can " + race + "'s layers be drawn in?";
			help = "A layer drawn in a colour gets one of its entries for each person. Change one, add one, or go on to the portraits.";
			evidence = "RColors.java:42-49,99-145 reads COLORS; RPortraitFrame.java:78 tints a layer by the colour it names.";
			for (PortraitLayers.Colour colour : PortraitLayers.colours(text)) {
				choices.add(new WizardQuestion.ChoiceView("change:" + colour.name(), "Change " + colour.name(), false));
			}
			choices.add(new WizardQuestion.ChoiceView("add", "Add a colour", false));
			choices.add(new WizardQuestion.ChoiceView("next", "Go on to the portraits", false));
		}
		case "colour-new" -> {
			kind = "text";
			prompt = "What's the new colour called?";
			help = "Such as SCALES or EYES. It's written in capitals, with _ for spaces.";
			evidence = "COLORS is keyed by name (RColors.java:42-49).";
		}
		case "colour" -> {
			kind = "fields";
			String name = parts[1];
			PortraitLayers.Colour colour = PortraitLayers.colours(text).stream().filter(c -> c.name().equals(name)).findFirst().orElse(null);
			prompt = "What is " + name + "?";
			help = "Up to 16 colours. Each person gets one of them; with fewer than 16, some are shared.";
			evidence = "RColors.java:120-141 spreads VALUES over 16 slots, and stops loading with none or more than 16. RColors.java:99-118 reads the flags.";
			String lines = colour == null ? "" : colour.values() == null ? null : String.join("\n", colour.values().stream().map(PortraitLayers.Rgb::text).toList());
			if (lines == null) {
				fields.add(new WizardQuestion.FieldView("VALUES", "Colours", "Written as a range, which this can't change. Change it in the Race Portrait Sim.", "colours", "kept", null, Map.of(), null, null,
						null, true, null, evidence, "a FROM and TO range", null, null, false, null));
			} else {
				// Needed: the game stops loading without one (RColors.java:130,138), so there's no Remove.
				fields.add(new WizardQuestion.FieldView("VALUES", "Colours", "As a file writes them: about double what the game shows before lighting.", "colours", "required", lines, Map.of(),
						null, null, "1 to 16 colours", true, null, evidence, null, null, null, false, null));
			}
			Map<String, String> plain = colour == null ? Map.of() : colour.plain();
			fields.add(flag("TURNS_GRAY_WHEN_OLD", "Turns grey with age", plain));
			fields.add(flag("TURNS_WHITE_WHEN_DEAD", "Turns pale when dead", plain));
			fields.add(flag("TURNS_SICKLY", "Turns sickly when ill", plain));
		}
		case "portrait" -> {
			int portrait = Integer.parseInt(parts[1]);
			kind = "layers";
			List<Layer> layers = PortraitLayers.layers(text, portrait);
			view = new WizardQuestion.PortraitView(race, portrait, portraits, layerViews(layers), null, null, null);
			prompt = "How is " + race + "'s " + portraits.get(portrait).toLowerCase(Locale.ROOT) + " portrait built?";
			help = layers.isEmpty() ? "It has no layers yet. Add the first: usually the head, then eyes, hair and so on, each drawn over the last."
					: "Layers are drawn from the bottom up, so the last is on top. Change a layer's frames or how it's drawn, move it, or add another.";
			evidence = "RPortrait.java:17-33 draws FACE's layers in order; RPortraitFrame.java:94-123 picks one of a layer's frames for each person.";
			for (Layer layer : layers) {
				String label = label(layer);
				choices.add(new WizardQuestion.ChoiceView("frames:" + layer.index(), "Frames of " + label, false));
				choices.add(new WizardQuestion.ChoiceView("tweak:" + layer.index(), "How " + label + " is drawn", false));
				if (layer.index() + 1 < layers.size()) {
					choices.add(new WizardQuestion.ChoiceView("up:" + layer.index(), "Move " + label + " up", false));
					choices.add(new WizardQuestion.ChoiceView("top:" + layer.index(), "Move " + label + " to the top", false));
				}
				if (layer.index() > 0) {
					choices.add(new WizardQuestion.ChoiceView("down:" + layer.index(), "Move " + label + " down", false));
					choices.add(new WizardQuestion.ChoiceView("bottom:" + layer.index(), "Move " + label + " to the bottom, behind everything", false));
				}
				choices.add(new WizardQuestion.ChoiceView("remove:" + layer.index(), "Remove " + label, false));
			}
			choices.add(new WizardQuestion.ChoiceView("add", "Add a layer", false));
			choices.add(new WizardQuestion.ChoiceView("next", portrait + 1 < portraits.size() ? "Next portrait" : "Go on to headwear", false));
		}
		case "layer-name" -> {
			int portrait = Integer.parseInt(parts[1]);
			kind = "text";
			view = new WizardQuestion.PortraitView(race, portrait, portraits, layerViews(PortraitLayers.layers(text, portrait)), null, null, null);
			prompt = "What's the new layer called?";
			help = "Such as Eyes or Back hair. It's written above the layer in the file, so you can find it later.";
			evidence = "The game skips a line from ** on (JsonValue.java:430-457), as vanilla's own layer names are.";
		}
		case "layer-frames" -> {
			int portrait = Integer.parseInt(parts[1]);
			kind = "frames";
			List<Layer> layers = PortraitLayers.layers(text, portrait);
			boolean isNew = parts[2].equals("new");
			Layer layer = isNew ? null : layers.get(Integer.parseInt(parts[2]));
			List<String> picked = layer == null ? List.of() : layer.frames().stream().map(FrameRef::text).toList();
			List<WizardQuestion.SheetView> sheets = sheetViews(state, false);
			String first = layer != null && !layer.frames().isEmpty() ? layer.frames().get(0).file() : guessSheet(isNew && parts.length > 3 ? parts[3] : null, sheets);
			view = new WizardQuestion.PortraitView(race, portrait, portraits, layerViews(layers), new WizardQuestion.FramesView(picked, first, sheets), null, null);
			String label = isNew ? (parts.length > 3 ? parts[3] : "the new layer") : label(layer);
			prompt = "Which frames can " + label + " be?";
			help = isNew ? "Pick one or more. Each person gets one of them. Choose where it's drawn too: layers are drawn in order, so back hair goes under the head."
					: "Pick one or more. Each person gets one of them, so these are the choices for this layer. Frames with nothing drawn are dimmed.";
			evidence = "RPortraitFrame.java:121: a person gets frame (their random value) modulo how many there are. RaceFrameMaker.java:139-146: 4 frames to a row of 60 pixels, counted across then down. RPortrait.java:33 draws the layers strictly in order, bottom first.";
		}
		case "layer-tweak" -> {
			int portrait = Integer.parseInt(parts[1]);
			int index = Integer.parseInt(parts[2]);
			kind = "fields";
			List<Layer> layers = PortraitLayers.layers(text, portrait);
			Layer layer = layers.get(index);
			view = new WizardQuestion.PortraitView(race, portrait, portraits, layerViews(layers), null, null, null);
			prompt = "How is " + label(layer) + " drawn?";
			help = "Leave what doesn't matter as it is.";
			evidence = "RPortraitFrame.java:59-91 reads each of these, with the defaults shown.";
			fields.addAll(tweakFields(text, layers, layer));
		}
		case "headwear" -> {
			String which = parts[1];
			kind = "addons";
			List<WizardQuestion.AddonView> entries = PortraitLayers.addons(text, which).stream().map(a -> new WizardQuestion.AddonView(a.file(), a.offX(), a.offY())).toList();
			view = new WizardQuestion.PortraitView(race, -1, portraits, List.of(), null, entries, sheetViews(state, true));
			prompt = switch (which) {
			case "CROWN" -> "What does " + race + "'s ruler wear?";
			case "RAIDER" -> "What do " + race + " raiders wear?";
			default -> "What do " + race + " mercenaries wear?";
			};
			help = switch (which) {
			case "CROWN" -> "Only the first row of the first sheet is used, for the ruler's portrait.";
			default -> "Each raider or mercenary gets one row from all of these sheets, by chance.";
			};
			evidence = "RCrown.java:33-68 reads CROWN, RAIDER and MERC, each needed, and makes every 36 pixel row of each sheet a choice. UIRoyalty.java:67 uses the first crown; RaiderPortrait.java:46 and UIMerenaries.java:216 pick at random.";
		}
		default -> {
			kind = "portrait_done";
			prompt = "That's " + race + "'s portraits designed.";
			help = "The Race Portrait Sim shows them on anyone, with every control, and saves to the same file.";
			evidence = "The Race Portrait Sim reads the file as the game does (PortraitModel).";
			canSkip = false;
		}
		}
		boolean canGoBack = state.session().answered().stream().anyMatch(id -> !id.equals(OWN_FILE));
		int position = position(parts[0], parts.length > 1 ? parts[1] : null, portraits.size());
		int total = 6 + portraits.size();
		return new WizardQuestion(ID, state.mod().folderName(), race, race, step, kind, prompt, help, evidence, position, total, notes.isEmpty() ? null : String.join(" ", notes), choices, null,
				fields, List.of(), List.of(), null, canGoBack, canSkip, history(state, step), view, null);
	}

	private static int position(String kind, String which, int portraits) {
		return switch (kind) {
		case OWN_FILE, START -> 1;
		case SHEETS -> 2;
		case COLOURS, "colour", "colour-new" -> 3;
		case "portrait", "layer-name", "layer-frames", "layer-tweak" -> 4 + Integer.parseInt(which);
		case "headwear" -> 4 + portraits + PortraitLayers.ADDON_KINDS.indexOf(which);
		default -> 7 + portraits - 1;
		};
	}

	private List<WizardQuestion.Exchange> history(State state, String now) {
		List<WizardQuestion.Exchange> history = new ArrayList<>();
		history.add(started());
		history.add(new WizardQuestion.Exchange("race", START_PROMPT, state.name(), List.of()));
		List<String> seen = new ArrayList<>();
		for (String id : state.session().answered()) {
			String[] parts = id.split(":", 4);
			String main = switch (parts[0]) {
			case "portrait", "headwear" -> id;
			case SHEETS, COLOURS, START -> parts[0];
			default -> null;
			};
			if (main == null || main.equals(now) || seen.contains(main)) {
				continue;
			}
			seen.add(main);
			String summary = summary(state, main);
			if (summary != null) {
				history.add(new WizardQuestion.Exchange(main, prompt(state, main), summary.length() > 90 ? summary.substring(0, 87) + "..." : summary, List.of()));
			}
		}
		return history;
	}

	private static String prompt(State state, String main) {
		String[] parts = main.split(":");
		return switch (parts[0]) {
		case START -> "Keep " + state.name() + "'s portraits, or start empty?";
		case SHEETS -> "Which of the game's sheets would you like copies of?";
		case COLOURS -> "What colours can the layers be drawn in?";
		case "portrait" -> {
			List<String> portraits = PortraitLayers.portraits(state.text());
			int portrait = Integer.parseInt(parts[1]);
			yield portrait < portraits.size() ? "How is the " + portraits.get(portrait).toLowerCase(Locale.ROOT) + " portrait built?" : "A portrait";
		}
		default -> "What's worn for " + parts[1].toLowerCase(Locale.ROOT) + "?";
		};
	}

	private static String summary(State state, String main) {
		String[] parts = main.split(":");
		String text = state.text();
		return switch (parts[0]) {
		case START -> null;
		case SHEETS -> {
			List<String> copies = PortraitLayers.faceSheets(text).stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(state.name().toLowerCase(Locale.ROOT) + "_")).toList();
			yield copies.isEmpty() ? "None" : String.join(", ", copies);
		}
		case COLOURS -> String.join(", ", PortraitLayers.colours(text).stream().map(PortraitLayers.Colour::name).toList());
		case "portrait" -> {
			int portrait = Integer.parseInt(parts[1]);
			if (portrait >= PortraitLayers.portraits(text).size()) {
				yield null;
			}
			List<Layer> layers = PortraitLayers.layers(text, portrait);
			yield layers.isEmpty() ? "No layers" : String.join(", ", layers.stream().map(PortraitWizardEngine::label).toList());
		}
		default -> String.join(", ", PortraitLayers.addons(text, parts[1]).stream().map(PortraitLayers.Addon::file).toList());
		};
	}

	// ---- Pieces of questions

	/** A layer as a person reads it: its name, or its sheets if it has none. */
	static String label(Layer layer) {
		if (layer.name() != null) {
			return layer.name();
		}
		List<String> files = new ArrayList<>();
		for (FrameRef frame : layer.frames()) {
			if (!files.contains(frame.file())) {
				files.add(frame.file());
			}
		}
		return "layer " + (layer.index() + 1) + (files.isEmpty() ? "" : " (" + String.join(", ", files) + ")");
	}

	/** How a layer is named in "picks the same frame as": its place and name, so two of the same name differ. */
	private static String linkLabel(Layer layer) {
		return (layer.index() + 1) + ". " + label(layer);
	}

	private static List<WizardQuestion.LayerView> layerViews(List<Layer> layers) {
		List<WizardQuestion.LayerView> views = new ArrayList<>();
		for (Layer layer : layers) {
			List<String> about = new ArrayList<>();
			String who = conditionLabel(layer, true);
			String armour = conditionLabel(layer, false);
			if (!layer.conditions().isEmpty() && who == null && armour == null) {
				about.add("only when its conditions hold");
			} else {
				if (who != null) {
					about.add(who.toLowerCase(Locale.ROOT));
				}
				if (armour != null) {
					about.add(armour.toLowerCase(Locale.ROOT));
				}
			}
			if (layer.plain().containsKey("COLOR")) {
				about.add("drawn in " + layer.plain().get("COLOR"));
			}
			if (layer.plain().containsKey("FRAME_OCCURRENCE")) {
				about.add("on a share of " + layer.plain().get("FRAME_OCCURRENCE") + " of people");
			}
			if (!layer.skipped().isEmpty()) {
				about.add(layer.skipped().size() + " frames the game skips");
			}
			views.add(new WizardQuestion.LayerView(layer.index(), layer.name(), layer.frames().stream().map(FrameRef::text).toList(),
					about.isEmpty() ? null : String.join(", ", about)));
		}
		return views;
	}

	/**
	 * The label of the layer's who or armour condition, or null if it has none of that sort. A layer whose conditions
	 * aren't all of the sorts offered here gets null for both, and its tweak fields show them as written.
	 */
	private static String conditionLabel(Layer layer, boolean who) {
		if (!conditionsRecognised(layer)) {
			return null;
		}
		List<Condition> conditions = normalised(layer);
		for (Map.Entry<String, Condition> option : WHO.entrySet()) {
			if (who && conditions.contains(option.getValue())) {
				return option.getKey();
			}
		}
		if (!who) {
			String found = null;
			int size = 0;
			for (Map.Entry<String, List<Condition>> option : ARMOUR.entrySet()) {
				if (conditions.containsAll(option.getValue()) && option.getValue().size() > size) {
					found = option.getKey();
					size = option.getValue().size();
				}
			}
			return found;
		}
		return null;
	}

	private static List<Condition> normalised(Layer layer) {
		List<Condition> list = new ArrayList<>();
		for (Condition condition : layer.conditions()) {
			list.add(new Condition(condition.comparator(), condition.stat(), normalNumber(condition.value())));
		}
		return list;
	}

	/** Whether every condition is one who option and at most one armour option, so the fields can show them. */
	private static boolean conditionsRecognised(Layer layer) {
		List<Condition> rest = normalised(layer);
		int whoCount = 0;
		for (Condition option : WHO.values()) {
			if (rest.remove(option)) {
				whoCount++;
			}
		}
		List<Condition> best = List.of();
		for (List<Condition> option : ARMOUR.values()) {
			if (rest.containsAll(option) && option.size() > best.size()) {
				best = option;
			}
		}
		rest.removeAll(best);
		return rest.isEmpty() && whoCount <= 1;
	}

	private static String normalNumber(String value) {
		try {
			double number = Double.parseDouble(value);
			return number == Math.rint(number) ? String.valueOf((long) number) : value;
		} catch (NumberFormatException e) {
			return value;
		}
	}

	private static String tweakLabel(String key) {
		return switch (key) {
		case "FRAME_OCCURRENCE" -> "how often it's shown";
		case "OFF_X" -> "how far right it's moved";
		case "OFF_Y" -> "how far down it's moved";
		case "OFF_X_RANDOM" -> "how far right it can wander";
		case "OFF_Y_RANDOM" -> "how far down it can wander";
		case "OPACITY" -> "how solid it is";
		default -> key;
		};
	}

	private static List<WizardQuestion.FieldView> tweakFields(String text, List<Layer> layers, Layer layer) {
		List<WizardQuestion.FieldView> fields = new ArrayList<>();
		Map<String, String> plain = layer.plain();
		fields.add(field("name", "Its name", "Written above it in the file.", "text", layer.name() == null ? "add" : "set", layer.name(), null, null, null, null, null));
		List<String> colours = PortraitLayers.colours(text).stream().map(PortraitLayers.Colour::name).toList();
		fields.add(new WizardQuestion.FieldView("COLOR", "Drawn in the colour", "Without one, the frames are drawn in their own colours.", "one", plain.containsKey("COLOR") ? "set" : "add",
				plain.get("COLOR"), Map.of(), null, colours, null, false, "An unknown colour stops the game loading (RMAP.java:82).", "RPortraitFrame.java:78", null, null, null, false, null));
		fields.add(number("FRAME_OCCURRENCE", "Share of people who have it", "From 0 (nobody) to 1 (everyone).", plain, "1", 0.0, 1.0, true, false));
		fields.add(number("OFF_X", "Moved right by (pixels)", "A negative number moves it left.", plain, "0", -40.0, 40.0, false, true));
		fields.add(number("OFF_Y", "Moved down by (pixels)", "A negative number moves it up.", plain, "0", -48.0, 48.0, false, true));
		fields.add(number("OFF_X_RANDOM", "Wanders right by up to (pixels)", "Each person's is moved by a share of this.", plain, "0", 0.0, 40.0, false, true));
		fields.add(number("OFF_Y_RANDOM", "Wanders down by up to (pixels)", "Each person's is moved by a share of this.", plain, "0", 0.0, 48.0, false, true));
		fields.add(number("OPACITY", "How solid it is", "From 0 (invisible) to 255 (solid).", plain, "255", 0.0, 256.0, false, true));
		fields.add(new WizardQuestion.FieldView("STAINS", "Shows dirt and blood", "", "yes_no", plain.containsKey("STAINS") ? "set" : "ghosted", plain.get("STAINS"), Map.of(), "true", null, null, false,
				null, "RPortraitFrame.java:80,127-129", null, null, null, false, null));
		List<String> others = new ArrayList<>();
		String linked = null;
		for (Layer other : layers) {
			if (other.index() != layer.index()) {
				others.add(linkLabel(other));
				if (linked == null && plain.containsKey("FRAME_RANDOM") && other.random() == layer.random()) {
					linked = linkLabel(other);
				}
			}
		}
		fields.add(new WizardQuestion.FieldView("link", "Picks the same frame as", "Two layers that pick together give each person matching frames, such as open and shut eyes.", "one",
				linked == null ? "add" : "set", linked, Map.of(), null, others, null, false, null,
				"RPortraitFrame.java:68,96-121: layers with the same FRAME_RANDOM read the same random value, so their frames match.", null, null, null, false, null));
		boolean recognised = conditionsRecognised(layer);
		String written = layer.conditions().stream().map(c -> c.comparator() + " " + c.stat() + " " + c.value()).reduce((a, b) -> a + ", " + b).orElse("");
		String who = conditionLabel(layer, true);
		String armour = conditionLabel(layer, false);
		fields.add(new WizardQuestion.FieldView("who", "Who has it", "", "one", !recognised ? "kept" : who == null ? "ghosted" : "set", who, Map.of(), "Everyone", new ArrayList<>(WHO.keySet()), null,
				false, null, "SValues.java:106-127 makes TYPE_ and CLASS_ stats of each kind of person; Lockable.java:115-127 tests them.", recognised ? null : written, null, null, false, null));
		fields.add(new WizardQuestion.FieldView("armour", "With armour", "", "one", !recognised ? "kept" : armour == null ? "ghosted" : "set", armour, Map.of(), "With or without", new ArrayList<>(ARMOUR.keySet()),
				null, false, null, "Vanilla's portraits test EQUIP_BATTLE_ARMOUR_PLATE_I and _LEATHER_I the same way (HUMAN.txt).", recognised ? null : written, null, null, false, null));
		return fields;
	}

	private static WizardQuestion.FieldView number(String key, String label, String help, Map<String, String> plain, String defaultText, Double min, Double max, boolean decimal, boolean whole) {
		return new WizardQuestion.FieldView(key, label, help, whole ? "whole_number" : "number", plain.containsKey(key) ? "set" : "ghosted", plain.get(key), Map.of(), defaultText, null,
				min == null ? null : plain(min) + " to " + plain(max) + (decimal ? "" : ", and the game doesn't stop a higher number"), false, null, "RPortraitFrame.java:68-79", null, min, max, decimal, null);
	}

	private static String plain(double value) {
		return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
	}

	private static WizardQuestion.FieldView flag(String key, String label, Map<String, String> plain) {
		return new WizardQuestion.FieldView(key, label, "", "yes_no", plain.containsKey(key) ? "set" : "ghosted", plain.get(key), Map.of(), "false", null, null, false, null, "RColors.java:99-118", null,
				null, null, false, null);
	}

	private static WizardQuestion.FieldView field(String key, String label, String help, String input, String state, String value, List<String> options, String defaultText, Double min, Double max,
			String evidence) {
		return new WizardQuestion.FieldView(key, label, help, input, state, value, Map.of(), defaultText, options, null, false, null, evidence, null, min, max, false, null);
	}

	/** The game's sheets the portraits name that the mod hasn't got, so a copy would be new. */
	private static List<String> gameSheets(State state, boolean addon) throws IOException {
		List<String> names = addon ? PortraitLayers.addonSheets(state.text()) : PortraitLayers.faceSheets(state.text());
		List<String> offered = new ArrayList<>();
		for (String name : names) {
			PortraitSim.SheetInfo info = state.sim().sheet(addon, name);
			if (info != null && info.inGame() && !info.inMod()) {
				offered.add(name);
			}
		}
		return offered;
	}

	private static List<WizardQuestion.SheetView> sheetViews(State state, boolean addon) throws IOException {
		List<WizardQuestion.SheetView> views = new ArrayList<>();
		for (PortraitSim.SheetInfo info : state.sim().sheets(addon)) {
			String source = info.inMod() && info.inGame() ? "mod over game" : info.inMod() ? "mod" : "game";
			views.add(new WizardQuestion.SheetView(info.name(), addon, source, info.width(), info.height(), info.frames(), info.problem()));
		}
		return views;
	}

	/** The sheet whose name is most like the layer's, such as Eyes for "Left eye", or the first. */
	private static String guessSheet(String name, List<WizardQuestion.SheetView> sheets) {
		List<WizardQuestion.SheetView> usable = sheets.stream().filter(sheet -> sheet.problem() == null).toList();
		if (usable.isEmpty()) {
			return null;
		}
		if (name != null) {
			String lower = name.toLowerCase(Locale.ROOT);
			for (WizardQuestion.SheetView sheet : usable) {
				String stem = sheet.name().toLowerCase(Locale.ROOT);
				String singular = stem.endsWith("s") ? stem.substring(0, stem.length() - 1) : stem;
				if (lower.contains(singular) || stem.contains(lower)) {
					return sheet.name();
				}
			}
		}
		return usable.get(0).name();
	}

	// ---- For the screen: pictures, which agents get from preview_face_sheet and preview_portrait

	/** A sheet's pixels, mod first. */
	public PngCodec.Image sheetImage(ModEntry mod, boolean addon, String name) throws IOException, GameParser.UnavailableException {
		try (PortraitSim.Session sim = PortraitSim.open(game, mod)) {
			return sim.sheetImage(addon, name);
		}
	}

	/**
	 * Draws a portrait with a layer's frames as picked but not saved, showing one of them. Nothing is written.
	 *
	 * @param layer the layer, or -1 for a new one on top
	 * @param show which of the frames to show, or -1 to leave it to the person's roll
	 */
	public PortraitSim.Preview preview(ModEntry mod, String race, int portrait, int layer, List<FrameRef> frames, int show) throws IOException, GameParser.UnavailableException {
		return preview(mod, race, portrait, layer, frames, show, Integer.MAX_VALUE);
	}

	/** @param at for a new layer, where it goes in the drawing order, as a frames answer's at: 0 is the bottom */
	public PortraitSim.Preview preview(ModEntry mod, String race, int portrait, int layer, List<FrameRef> frames, int show, int at) throws IOException, GameParser.UnavailableException {
		try (PortraitSim.Session sim = PortraitSim.open(game, mod)) {
			PortraitSim.Opened opened = sim.open(race);
			String text = opened.text();
			int index = layer;
			if (frames != null && !frames.isEmpty()) {
				if (layer < 0) {
					int count = PortraitLayers.layers(text, portrait).size();
					int place = Math.max(0, Math.min(count, at));
					text = PortraitLayers.addLayer(text, portrait, place, null, frames);
					index = place;
				} else {
					text = PortraitLayers.setFrames(text, portrait, layer, frames);
				}
			}
			Map<Integer, Integer> pins = index >= 0 && show >= 0 ? Map.of(index, show) : Map.of();
			PortraitSim.Person person = new PortraitSim.Person(portrait, 1, 0.4, false, false, false, false, false, false, 0, 0, 0, 0, PortraitSim.OverlayChoice.NONE, 4,
					new PortraitSim.Pins(pins, Map.of(), PortraitSim.Pins.NONE.random()));
			return sim.preview(race, text, person);
		} catch (PortraitSim.Refused e) {
			throw new IOException(e.getMessage());
		}
	}

	// ---- Small things

	private static WizardEngine.Refused refuse(WizardQuestion asked, String message) {
		return new WizardEngine.Refused(message, asked.refusedWith(message, List.of()));
	}

	private static String choice(Map<String, Object> answer) {
		return answer.get("choice") instanceof String chosen ? chosen : null;
	}

	private static String text(Object value) {
		if (value instanceof Double number) {
			return number == Math.rint(number) && !Double.isInfinite(number) ? String.valueOf(number.longValue()) : number.toString();
		}
		return value == null ? null : String.valueOf(value);
	}

	private static List<String> names(Object value) {
		List<String> names = new ArrayList<>();
		if (value instanceof List<?> list) {
			for (Object item : list) {
				if (item != null && !String.valueOf(item).isBlank()) {
					names.add(String.valueOf(item).strip());
				}
			}
		} else if (value != null) {
			for (String part : String.valueOf(value).split(",")) {
				if (!part.isBlank()) {
					names.add(part.strip());
				}
			}
		}
		return names;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> cast(Map<?, ?> map) {
		return (Map<String, Object>) map;
	}
}
