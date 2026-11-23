package syx.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import syx.core.HandbookStore;
import syx.core.JsonText;
import syx.core.ModEntry;
import syx.core.ModLibrary;
import syx.core.Operations;
import syx.core.PortraitLayers;
import syx.core.PortraitSim;
import syx.core.ToolkitSettings;
import syx.core.wizard.WizardEngine;
import syx.core.wizard.WizardQuestion;
import syx.core.wizard.WizardStore;

/**
 * The Portrait Design Wizard: a race's portraits built through every question, each answer saved as it's given and
 * read back by the game's parser, refused answers writing nothing, a copied sheet matching the game's bytes, and an
 * agent's answers writing the same files as the engine's.
 */
public final class PortraitWizardTest {

	private static int passed;
	private static int failed;
	private static Path game;
	private static Path temp;
	private static Path mods;

	private static final String RACE = "MOON_FOLK";
	/** The top layer's index in the first portrait at that point in the script: Human's 19, plus Third eye, less the one removed, plus Back hair. */
	private static final String LAST = "19";

	private PortraitWizardTest() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			System.out.println("SKIPPED: no game folder given.");
			System.out.println("passed=0 failed=0");
			return;
		}
		game = Paths.get(args[0]);
		temp = Files.createTempDirectory("syx-portrait-wizard");
		mods = temp.resolve("mods");
		Files.createDirectories(mods);
		System.setProperty(WizardStore.FOLDER_PROPERTY, temp.resolve("Wizards").toString());
		try {
			Operations ops = new Operations(() -> game, mods, new ToolkitSettings(temp.resolve("settings.properties")), handbookStore(), false);
			ok(ops, "create_mod", "name", "Portrait Mod");
			ok(ops, "create_mod", "name", "Portrait Agent Mod");
			ModEntry mod = ModLibrary.find(mods, game, "Portrait Mod").orElseThrow();
			ModEntry agentMod = ModLibrary.find(mods, game, "Portrait Agent Mod").orElseThrow();
			WizardEngine engine = new WizardEngine(game, handbookStore(), WizardStore.openDefault());
			makeRace(engine, mod);
			makeRace(engine, agentMod);
			edits(mod);
			journey(engine, mod);
			agent(ops);
			compare(mod.root(), agentMod.root());
		} finally {
			System.clearProperty(WizardStore.FOLDER_PROPERTY);
			deleteFolder(temp);
		}
		System.out.println("passed=" + passed + " failed=" + failed);
		System.exit(failed == 0 ? 0 : 1);
	}

	/** A race made by the race wizard from Human, as a modder would have one. */
	private static void makeRace(WizardEngine engine, ModEntry mod) throws Exception {
		engine.start(mod, "race", "Moon Folk");
		engine.answer(mod, "race", RACE, Map.of("choice", "HUMAN"));
		engine.forget(mod, "race", RACE);
	}

	/** The text edits on their own, each saved through the sim, which reads the result back with the game's parser. */
	private static void edits(ModEntry mod) throws Exception {
		Path copy = temp.resolve("edits");
		copyFolder(mod.root(), copy);
		ModEntry scratch = new ModEntry(mod.folderName(), copy, mod.displayName(), mod.versionFolders(), mod.outdated(), mod.problem());
		try (PortraitSim.Session sim = PortraitSim.open(game, scratch)) {
			PortraitSim.Race race = sim.races().stream().filter(r -> r.name().equals(RACE)).findFirst().orElseThrow();
			if (!race.editable()) {
				sim.copyToMod(RACE);
			}
			PortraitSim.Opened opened = sim.open(RACE);
			String text = opened.text();
			List<String> portraits = PortraitLayers.portraits(text);
			expect("Human has three adult portraits and a child's: " + portraits, portraits.equals(List.of("Adult 1", "Adult 2", "Adult 3", "Child")));
			List<PortraitLayers.Layer> layers = PortraitLayers.layers(text, 0);
			// A new race's sprite file is written from the game's read of Human's, which has no comments, so no names.
			expect("its layers are read: " + layers.size(), layers.size() > 10 && layers.get(0).name() == null);
			expect("frames are read as the game splits them", layers.get(2).frames().get(0).equals(new PortraitLayers.FrameRef("Chest", 0)));
			expect("a layer's conditions are read", layers.get(0).conditions().equals(List.of(new PortraitLayers.Condition("EQUAL", "TYPE_PARENT", "1"))));

			String added = PortraitLayers.addLayer(text, 0, "Third eye", List.of(new PortraitLayers.FrameRef("Eyes", 1), new PortraitLayers.FrameRef("Eyes", 2)));
			List<PortraitLayers.Layer> after = PortraitLayers.layers(added, 0);
			expect("an added layer is on top with its name", after.size() == layers.size() + 1 && "Third eye".equals(after.get(after.size() - 1).name()) && after.get(after.size() - 1).frames().size() == 2);
			expect("and everything before the portraits keeps its text", added.startsWith(text.substring(0, text.indexOf("TYPES"))));
			opened = saved(sim, opened, added, "adding a layer");

			String under = PortraitLayers.addLayer(text, 0, 0, "Back hair", List.of(new PortraitLayers.FrameRef("Hair", 61)));
			List<PortraitLayers.Layer> underLayers = PortraitLayers.layers(under, 0);
			expect("a layer added at 0 is at the bottom", "Back hair".equals(underLayers.get(0).name()) && underLayers.size() == layers.size() + 1);
			expect("and the layers it went under keep their random slot", underLayers.get(1).random() == 0 && underLayers.get(5).random() == 4);
			String moved = PortraitLayers.move(opened.text(), 0, after.size() - 1, 0);
			List<PortraitLayers.Layer> movedLayers = PortraitLayers.layers(moved, 0);
			expect("a moved layer goes to the bottom with its name", "Third eye".equals(movedLayers.get(0).name()));
			expect("the layers it passed keep the frame each person gets", movedLayers.get(1).random() == 0 && movedLayers.get(2).random() == 1 && "1".equals(movedLayers.get(2).plain().get("FRAME_RANDOM")));
			opened = saved(sim, opened, moved, "moving a layer");

			String tagged = PortraitLayers.setTag(opened.text(), 0, 0, "OFF_Y", "-3");
			tagged = PortraitLayers.setConditions(tagged, 0, 0, List.of(new PortraitLayers.Condition("EQUAL", "CLASS_NOBLE", "1")));
			tagged = PortraitLayers.setName(tagged, 0, 0, "Mind's eye");
			PortraitLayers.Layer first = PortraitLayers.layers(tagged, 0).get(0);
			expect("a tag, conditions and a new name are set", "-3".equals(first.plain().get("OFF_Y")) && first.conditions().size() == 1 && "Mind's eye".equals(first.name()));
			opened = saved(sim, opened, tagged, "setting a layer's tags");

			String removed = PortraitLayers.remove(opened.text(), 0, 0);
			expect("a removed layer is gone", PortraitLayers.layers(removed, 0).size() == layers.size() && PortraitLayers.layers(removed, 0).stream().noneMatch(l -> "Mind's eye".equals(l.name())));
			opened = saved(sim, opened, removed, "removing a layer");

			int child = portraits.size() - 1;
			String cleared = PortraitLayers.clear(opened.text(), child);
			expect("a cleared portrait has no layers", PortraitLayers.layers(cleared, child).isEmpty());
			cleared = PortraitLayers.addLayer(cleared, child, "Head", List.of(new PortraitLayers.FrameRef("Baby", 1)));
			expect("and a layer can be added to it, named", PortraitLayers.layers(cleared, child).size() == 1 && "Head".equals(PortraitLayers.layers(cleared, child).get(0).name()));
			opened = saved(sim, opened, cleared, "clearing and refilling the child's portrait");

			String colour = PortraitLayers.setColour(opened.text(), "SCALES", List.of(new PortraitLayers.Rgb(10, 200, 30)), Map.of("TURNS_SICKLY", "true"));
			expect("a new colour is added", PortraitLayers.colours(colour).stream().anyMatch(c -> c.name().equals("SCALES") && c.values().size() == 1 && "true".equals(c.plain().get("TURNS_SICKLY"))));
			opened = saved(sim, opened, colour, "adding a colour");

			String addons = PortraitLayers.setAddons(opened.text(), "MERC", List.of(new PortraitLayers.Addon("Helm", "0", "-2"), new PortraitLayers.Addon("RaidHat_L", "1", "0")));
			expect("headwear is written as a list", PortraitLayers.addons(addons, "MERC").size() == 2);
			opened = saved(sim, opened, addons, "setting headwear");

			String renamed = PortraitLayers.renameSheet(opened.text(), false, "Eyes", "Moon_folk_Eyes");
			expect("renaming a sheet changes every frame on it", !renamed.matches("(?s).*\\bEyes:.*") && renamed.contains("Moon_folk_Eyes: "));
			String back = PortraitLayers.renameSheet(renamed, false, "Moon_folk_Eyes", "Eyes");
			expect("and renaming it back gives the same text", back.equals(opened.text()));

			PortraitSim.Refused bad = null;
			try {
				sim.save(RACE, opened.revision(), PortraitLayers.setTag(opened.text(), 0, 0, "COLOR", "NOPE"));
			} catch (PortraitSim.Refused e) {
				bad = e;
			}
			expect("a colour that isn't there is refused by the game's read: " + (bad == null ? null : bad.getMessage()), bad != null);
		}
	}

	private static PortraitSim.Opened saved(PortraitSim.Session sim, PortraitSim.Opened opened, String text, String what) throws IOException {
		try {
			sim.save(RACE, opened.revision(), text);
			PortraitSim.Opened again = sim.open(RACE);
			expect(what + " is saved as given", again.text().equals(text));
			PortraitSim.Preview preview = sim.preview(RACE, null, PortraitSim.Person.standard());
			expect(what + " loads as the game would: " + preview.problem(), preview.state() != PortraitSim.State.RED);
			return again;
		} catch (PortraitSim.Refused e) {
			expect(what + " is saved: " + e.getMessage(), false);
			return opened;
		}
	}

	private static void journey(WizardEngine engine, ModEntry mod) throws Exception {
		WizardQuestion first = engine.begin(mod, "portrait");
		expect("the first question asks which race: " + first.kind(), first.kind().equals("race") && first.choices().stream().anyMatch(c -> c.id().equals(RACE)));
		expect("a race that isn't there is refused", refused(() -> engine.start(mod, "portrait", "NOBODY")) != null);
		Path sprite = spriteFile(mod);
		WizardQuestion q = engine.start(mod, "portrait", RACE);
		if (q.step().equals("own-file")) {
			expect("a race on the game's file is offered its own copy", q.choices().size() == 1);
			q = engine.answer(mod, "portrait", RACE, Map.of("choice", "copy"));
			sprite = spriteFile(mod);
		}
		expect("then whether to keep its portraits: " + q.step(), q.step().equals("keep-or-empty"));
		for (Step step : script()) {
			if (step.refused() != null) {
				byte[] before = Files.readAllBytes(spriteFile(mod));
				WizardEngine.Refused no = refused(() -> engine.answer(mod, "portrait", RACE, step.answer()));
				expect(step.refused() + " is refused: " + (no == null ? null : no.getMessage()), no != null && no.question() != null);
				expect(step.refused() + " writes nothing", Arrays.equals(before, Files.readAllBytes(spriteFile(mod))));
				continue;
			}
			q = step.skip() ? engine.skip(mod, "portrait", RACE) : engine.answer(mod, "portrait", RACE, step.answer());
			expect("after " + step.label() + " it asks " + step.expect() + ": " + q.step(), q.step().startsWith(step.expect()));
			if (step.label().equals("copying Eyes")) {
				Path png = versionFolder(mod.root()).resolve("assets/sprite/race/face/Moon_folk_Eyes.png");
				expect("the copied sheet is in the mod", Files.isRegularFile(png));
				expect("and is the game's sheet, byte for byte", Files.isRegularFile(png) && Arrays.equals(Files.readAllBytes(png), gameBytes("data/assets/sprite/race/face/Eyes.png")));
				String text = Files.readString(spriteFile(mod));
				expect("the portraits point at the copy", text.contains("Moon_folk_Eyes: ") && !text.matches("(?s).*[\\s,\\[]Eyes:.*"));
			}
			if (step.label().equals("naming a layer")) {
				expect("the frames question offers sheets: " + q.portrait().frames().sheet(), q.kind().equals("frames") && q.portrait().frames().sheets().size() > 10 && q.portrait().frames().picked().isEmpty());
			}
			if (step.label().equals("picking frames")) {
				expect("a new layer goes on to how it's drawn", q.kind().equals("fields") && q.fields().stream().anyMatch(f -> f.key().equals("who") && f.state().equals("ghosted")));
			}
			if (step.label().equals("picking back hair behind everything") || step.label().equals("and back to the bottom")) {
				List<PortraitLayers.Layer> layers = PortraitLayers.layers(sectionOf(mod), 0);
				expect("back hair is the bottom layer, drawn behind the head (" + step.label() + "): " + layers.get(0).name(), "Back hair".equals(layers.get(0).name()) && layers.get(0).frames().size() == 3);
				expect("and the layers above keep the frame each person gets", layers.stream().skip(1).allMatch(l -> l.plain().containsKey("FRAME_RANDOM")));
			}
			if (step.label().equals("moving it to the top")) {
				List<PortraitLayers.Layer> layers = PortraitLayers.layers(sectionOf(mod), 0);
				expect("to the top puts it last: " + layers.get(layers.size() - 1).name() + " of " + layers.size(), "Back hair".equals(layers.get(layers.size() - 1).name()) && String.valueOf(layers.size() - 1).equals(LAST));
			}
			if (step.label().equals("tweaking it")) {
				List<PortraitLayers.Layer> layers = PortraitLayers.layers(sectionOf(mod), 0);
				PortraitLayers.Layer top = layers.get(layers.size() - 1);
				expect("its colour, place and who has it are saved", "SCALES".equals(top.plain().get("COLOR")) && "-3".equals(top.plain().get("OFF_Y"))
						&& top.conditions().equals(List.of(new PortraitLayers.Condition("EQUAL", "CLASS_NOBLE", "1"))) && "Third eye".equals(top.name()));
				expect("the layers question shows it", q.kind().equals("layers") && q.portrait().layers().stream().anyMatch(l -> "Third eye".equals(l.name()) && l.summary() != null && l.summary().contains("only nobles")));
			}
		}
		expect("the end is reached", q.kind().equals("portrait_done"));
		WizardEngine fresh = new WizardEngine(game, handbookStore(), WizardStore.openDefault());
		WizardQuestion resumed = fresh.question(mod, "portrait", RACE);
		expect("a fresh engine continues where it was", resumed.step().equals(q.step()));
		WizardQuestion back = fresh.back(mod, "portrait", RACE);
		expect("back goes to the last question answered: " + back.step(), back.step().startsWith("headwear:"));
		fresh.skip(mod, "portrait", RACE);
		WizardQuestion finished = fresh.answer(mod, "portrait", RACE, Map.of());
		expect("finishing forgets it", finished.kind().equals("finished") && fresh.unfinished(mod).stream().noneMatch(s -> s.wizard().equals("portrait")));
		try (PortraitSim.Session sim = PortraitSim.open(game, mod)) {
			PortraitSim.Preview preview = sim.preview(RACE, null, PortraitSim.Person.standard());
			expect("the race's portraits load as the game would: " + preview.problem(), preview.state() != PortraitSim.State.RED);
		}
		expect("the sprite file is the one in the mod", sprite.startsWith(mod.root()));
	}

	/** An answer, a skip, or an answer that must be refused, and the question expected after it. */
	private record Step(String label, Map<String, Object> answer, boolean skip, String expect, String refused) {
		static Step answer(String label, Map<String, Object> answer, String expect) {
			return new Step(label, answer, false, expect, null);
		}

		static Step skip(String label, String expect) {
			return new Step(label, Map.of(), true, expect, null);
		}

		static Step refuse(String label, Map<String, Object> answer) {
			return new Step(label, answer, false, null, label);
		}
	}

	/** The same answers for the engine and the agent, from keeping the portraits to the end. */
	private static List<Step> script() {
		List<Step> steps = new ArrayList<>();
		steps.add(Step.answer("keeping the portraits", Map.of("choice", "keep"), "sheets"));
		steps.add(Step.refuse("a sheet the portraits don't use", Map.of("values", Map.of("face", "Nonesuch"))));
		steps.add(Step.answer("copying Eyes", Map.of("values", Map.of("face", "Eyes")), "colours"));
		steps.add(Step.answer("adding a colour", Map.of("choice", "add"), "colour-new"));
		steps.add(Step.answer("naming it", Map.of("value", "Scales"), "colour:SCALES"));
		steps.add(Step.refuse("a colour with no entries", Map.of("values", Map.of("VALUES", ""))));
		steps.add(Step.refuse("a colour that isn't R_G_B", Map.of("values", Map.of("VALUES", "green"))));
		steps.add(Step.answer("giving its entries", Map.of("values", Map.of("VALUES", "10_200_30\n20_180_40", "TURNS_SICKLY", "true")), "colours"));
		steps.add(Step.answer("going on to the portraits", Map.of("choice", "next"), "portrait:0"));
		steps.add(Step.answer("adding a layer", Map.of("choice", "add"), "layer-name:0"));
		steps.add(Step.answer("naming a layer", Map.of("value", "Third eye"), "layer-frames:0:new"));
		steps.add(Step.refuse("no frames", Map.of("frames", List.of())));
		steps.add(Step.refuse("a frame past the sheet's end", Map.of("frames", List.of("Moon_folk_Eyes: 9999"))));
		steps.add(Step.refuse("a sheet that isn't there", Map.of("frames", List.of("Nonesuch: 1"))));
		steps.add(Step.answer("picking frames", Map.of("frames", List.of("Moon_folk_Eyes: 1", "Moon_folk_Eyes: 2")), "layer-tweak:0:"));
		steps.add(Step.refuse("a colour the race hasn't got", Map.of("values", Map.of("COLOR", "NOPE"))));
		steps.add(Step.refuse("someone not on the list", Map.of("values", Map.of("who", "Only kings"))));
		steps.add(Step.answer("tweaking it", Map.of("values", Map.of("COLOR", "SCALES", "OFF_Y", "-3", "who", "Only nobles")), "portrait:0"));
		steps.add(Step.answer("moving a layer down", Map.of("choice", "down:1"), "portrait:0"));
		steps.add(Step.answer("removing a layer", Map.of("choice", "remove:0"), "portrait:0"));
		steps.add(Step.answer("adding back hair", Map.of("choice", "add"), "layer-name:0"));
		steps.add(Step.answer("naming it Back hair", Map.of("value", "Back hair"), "layer-frames:0:new"));
		steps.add(Step.refuse("a place past the top", Map.of("frames", List.of("Hair: 61"), "at", 999)));
		steps.add(Step.answer("picking back hair behind everything", Map.of("frames", List.of("Hair: 61", "Hair: 62", "Hair: 63"), "at", 0), "layer-tweak:0:0"));
		steps.add(Step.skip("leaving how it's drawn", "portrait:0"));
		steps.add(Step.answer("moving it to the top", Map.of("choice", "top:0"), "portrait:0"));
		steps.add(Step.answer("and back to the bottom", Map.of("choice", "bottom:" + LAST), "portrait:0"));
		steps.add(Step.answer("going to the next portrait", Map.of("choice", "next"), "portrait:1"));
		steps.add(Step.skip("skipping the second adult", "portrait:2"));
		steps.add(Step.skip("skipping the third adult", "portrait:3"));
		steps.add(Step.skip("skipping the child", "headwear:CROWN"));
		steps.add(Step.refuse("no headwear", Map.of("entries", List.of())));
		steps.add(Step.answer("choosing the crown", Map.of("entries", List.of(Map.of("file", "Helm", "offx", "0", "offy", "-2"))), "headwear:RAIDER"));
		steps.add(Step.skip("keeping the raiders'", "headwear:MERC"));
		steps.add(Step.skip("keeping the mercenaries'", "done"));
		return steps;
	}

	/** The same answers through the agent's operations, in another mod. */
	@SuppressWarnings("unchecked")
	private static void agent(Operations ops) {
		Map<String, Object> first = ok(ops, "wizard_question", "mod", "Portrait Agent Mod", "wizard", "portrait");
		expect("an agent gets the race question", "race".equals(first.get("kind")) && String.valueOf(first.get("how_to_answer")).contains("wizard_start"));
		Map<String, Object> q = ok(ops, "wizard_start", "mod", "Portrait Agent Mod", "wizard", "portrait", "display_name", RACE);
		if ("own-file".equals(q.get("step"))) {
			q = call(ops, "wizard_answer", Map.of("choice", "copy"));
		}
		for (Step step : script()) {
			if (step.refused() != null) {
				Operations.Result bad = ops.call("wizard_answer", in("mod", "Portrait Agent Mod", "wizard", "portrait", "name", RACE, "answer", step.answer()));
				expect("an agent's " + step.refused() + " is refused with the question", bad.outcome() == Operations.Outcome.REFUSED && bad.payload().get("question") instanceof Map);
				continue;
			}
			q = step.skip() ? call(ops, "wizard_skip", null) : call(ops, "wizard_answer", step.answer());
			expect("an agent's " + step.label() + " leads to " + step.expect(), String.valueOf(q.get("step")).startsWith(step.expect()));
			if (step.label().equals("naming a layer")) {
				Map<String, Object> portrait = (Map<String, Object>) q.get("portrait");
				expect("an agent sees the sheets to pick from", portrait != null && ((List<Object>) portrait.get("sheets")).size() > 10);
				Map<String, Object> sheet = ok(ops, "preview_face_sheet", "mod", "Portrait Agent Mod", "sheet", "Moon_folk_Eyes", "picked", List.of(1, 2));
				expect("and can see a sheet's frames: " + sheet.get("frames"), sheet.get("png_base64") instanceof String png && !png.isEmpty() && ((Number) sheet.get("frames")).intValue() > 4
						&& "mod".equals(sheet.get("source")));
			}
		}
		Operations.Result noSkip = ops.call("wizard_skip", in("mod", "Portrait Agent Mod", "wizard", "portrait", "name", RACE));
		expect("the last question can't be skipped", noSkip.outcome() == Operations.Outcome.REFUSED);
		Map<String, Object> end = call(ops, "wizard_answer", Map.of());
		expect("an agent finishes too", "finished".equals(end.get("kind")));
		expect("and the JSON is written", JsonText.write(end).contains("\"finished\""));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> call(Operations ops, String tool, Map<String, Object> answer) {
		Map<String, Object> args = in("mod", "Portrait Agent Mod", "wizard", "portrait", "name", RACE);
		if (answer != null) {
			args.put("answer", answer);
		}
		Operations.Result result = ops.call(tool, args);
		expect(tool + " works: " + (result.outcome() == Operations.Outcome.OK ? "" : result.payload()), result.outcome() == Operations.Outcome.OK);
		return result.payload();
	}

	// ---- Helpers

	private static String sectionOf(ModEntry mod) throws Exception {
		try (PortraitSim.Session sim = PortraitSim.open(game, mod)) {
			return sim.open(RACE).text();
		}
	}

	private static Path spriteFile(ModEntry mod) throws Exception {
		try (PortraitSim.Session sim = PortraitSim.open(game, mod)) {
			PortraitSim.Race race = sim.races().stream().filter(r -> r.name().equals(RACE)).findFirst().orElseThrow();
			return sim.spriteFilePath(race.spriteFile());
		}
	}

	private static byte[] gameBytes(String entry) throws IOException {
		try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(game.resolve("base").resolve("data.zip").toFile())) {
			return zip.getInputStream(zip.getEntry(entry)).readAllBytes();
		}
	}

	/** The person's and the agent's answers give byte-identical files. */
	private static void compare(Path one, Path two) throws IOException {
		Map<String, byte[]> first = files(one);
		Map<String, byte[]> second = files(two);
		expect("the same files are written: " + first.keySet() + " and " + second.keySet(), first.keySet().equals(second.keySet()));
		for (String file : first.keySet()) {
			expect(file + " is the same either way", second.containsKey(file) && Arrays.equals(first.get(file), second.get(file)));
		}
	}

	private static Map<String, byte[]> files(Path root) throws IOException {
		Map<String, byte[]> files = new TreeMap<>();
		try (Stream<Path> walk = Files.walk(root)) {
			for (Path file : walk.filter(Files::isRegularFile).toList()) {
				String relative = root.relativize(file).toString().replace('\\', '/');
				if (!relative.equals("_Info.txt")) {
					files.put(relative, Files.readAllBytes(file));
				}
			}
		}
		return files;
	}

	private static Path versionFolder(Path root) throws IOException {
		try (Stream<Path> children = Files.list(root)) {
			return children.filter(child -> Files.isDirectory(child) && child.getFileName().toString().matches("V\\d+")).findFirst().orElse(root.resolve("V71"));
		}
	}

	private static void copyFolder(Path from, Path to) throws IOException {
		try (Stream<Path> walk = Files.walk(from)) {
			for (Path path : walk.toList()) {
				Path target = to.resolve(from.relativize(path).toString());
				if (Files.isDirectory(path)) {
					Files.createDirectories(target);
				} else {
					Files.copy(path, target);
				}
			}
		}
	}

	private interface Call {
		Object run() throws Exception;
	}

	private static WizardEngine.Refused refused(Call call) {
		try {
			call.run();
			return null;
		} catch (WizardEngine.Refused e) {
			return e;
		} catch (Exception e) {
			expect("no unexpected failure: " + e, false);
			return null;
		}
	}

	private static HandbookStore handbookStore() {
		return new HandbookStore(temp.resolve("handbook"), etag -> new HandbookStore.Response(404, null, new byte[0]));
	}

	private static Map<String, Object> ok(Operations ops, String name, Object... pairs) {
		Operations.Result result = ops.call(name, in(pairs));
		expect(name + " works: " + (result.outcome() == Operations.Outcome.OK ? "" : result.payload()), result.outcome() == Operations.Outcome.OK);
		return result.payload();
	}

	private static Map<String, Object> in(Object... pairs) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], pairs[i + 1]);
		}
		return map;
	}

	private static void expect(String name, boolean ok) {
		if (ok) {
			passed++;
		} else {
			failed++;
			System.out.println("FAIL " + name);
		}
	}

	private static void deleteFolder(Path folder) throws IOException {
		if (!Files.exists(folder)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(folder)) {
			for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}
}
