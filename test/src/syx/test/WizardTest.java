package syx.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import syx.core.AssetCatalog;
import syx.core.AssetSchema;
import syx.core.GameInstall;
import syx.core.HandbookStore;
import syx.core.JsonText;
import syx.core.ModEntry;
import syx.core.ModLibrary;
import syx.core.Operations;
import syx.core.ToolkitSettings;
import syx.core.wizard.NameForms;
import syx.core.wizard.Phrase;
import syx.core.wizard.Wizard;
import syx.core.wizard.WizardEngine;
import syx.core.wizard.WizardQuestion;
import syx.core.wizard.WizardStore;
import syx.core.wizard.Wizards;

/**
 * The Asset Creation Wizard: a race made from start to finish, each answer saved as it's given, refused
 * answers writing nothing, a wizard continued by a fresh engine, and an agent's answers writing the same
 * files as the engine's.
 */
public final class WizardTest {

	private static int passed;
	private static int failed;
	private static Path game;
	private static Path temp;
	private static Path mods;

	private WizardTest() {
	}

	public static void main(String[] args) throws Exception {
		names();
		if (args.length == 0) {
			System.out.println("SKIPPED the game's part: no game folder given.");
			System.out.println("passed=" + passed + " failed=" + failed);
			System.exit(failed == 0 ? 0 : 1);
			return;
		}
		game = Paths.get(args[0]);
		temp = Files.createTempDirectory("syx-wizard");
		mods = temp.resolve("mods");
		Files.createDirectories(mods);
		System.setProperty(WizardStore.FOLDER_PROPERTY, temp.resolve("Wizards").toString());
		try {
			definitions();
			Operations ops = new Operations(() -> game, mods, new ToolkitSettings(temp.resolve("settings.properties")), handbookStore(), false);
			ok(ops, "create_mod", "name", "Wizard Mod");
			ok(ops, "create_mod", "name", "Agent Mod");
			ModEntry mod = ModLibrary.find(mods, game, "Wizard Mod").orElseThrow();
			WizardEngine engine = new WizardEngine(game, handbookStore(), WizardStore.openDefault());
			journey(engine, mod);
			agent(ops);
			compare(mod.root(), mods.resolve("Agent Mod"));
			noStore(mod);
		} finally {
			System.clearProperty(WizardStore.FOLDER_PROPERTY);
			deleteFolder(temp);
		}
		System.out.println("passed=" + passed + " failed=" + failed);
		System.exit(failed == 0 ? 0 : 1);
	}

	private static void names() {
		expect("a name is put in capitals for its files", "DONDORIAN".equals(NameForms.stem("Dondorian")));
		expect("spaces and symbols become one underscore", "DONDORIAN_ELVES".equals(NameForms.stem("  Dondorian   Elves! ")));
		expect("accents are dropped", "ELVEN".equals(NameForms.stem("Élven")));
		expect("a name of symbols has no file name", NameForms.stem("!!!") == null);
		expect("the name as typed keeps its case", "Dondorian Elves".equals(NameForms.display("  Dondorian   Elves ")));
		expect("a plural is guessed", "Dondorians".equals(NameForms.pluralGuess("Dondorian")) && "Flies".equals(NameForms.pluralGuess("Fly")) && "Bushes".equals(NameForms.pluralGuess("Bush")) && "Cyclops".equals(NameForms.pluralGuess("Cyclops"))
				&& "Ploys".equals(NameForms.pluralGuess("Ploy")) && "Foxes".equals(NameForms.pluralGuess("Fox")));
		Map<String, String> words = Map.of("NAME", "Gnome", "NAMES", "Gnomes", "POSSESSIVE", "Gnomish");
		expect("a prompt is filled from the race's words", "Do Gnomes like Gnomish food, says a Gnome? Gnomish".equals(Phrase.fill("Do {names} like {possessive} food, says a {name}? {possessives}", words, "x")));
		expect("before there are any, the typed name stands in", "How old can a Gnome live? Gnomes".equals(Phrase.fill("How old can a {name} live? {names}", Map.of(), "Gnome")));
	}

	/** Every question names tags the form has, in kinds the wizard can ask, and says what game code backs it. */
	private static void definitions() {
		AssetCatalog.Entry race = AssetCatalog.load(GameInstall.jarIn(game)).stream().filter(entry -> entry.type().label().equals("Race")).findFirst().orElseThrow();
		expect("races are enabled", race.available());
		if (!race.available()) {
			return;
		}
		AssetSchema schema = race.schema();
		for (Wizard.Definition definition : Wizards.all()) {
			expect(definition.id() + " starts with a name and then a look", definition.steps().get(0) instanceof Wizard.NameStep && definition.steps().get(1) instanceof Wizard.LookStep);
			expect(definition.id() + " ends with done", definition.steps().get(definition.steps().size() - 1) instanceof Wizard.DoneStep);
			for (Wizard.Step step : definition.steps()) {
				expect(step.id() + " says what game code backs it", step.evidence() != null && !step.evidence().isBlank());
				expect(step.id() + " is asked in plain words, not tag names", !step.prompt().matches(".*\\b[A-Z]{2,}_[A-Z_]+\\b.*"));
				for (String key : step.keys()) {
					expect(step.id() + "'s " + key + " is a tag the form has", key.startsWith("BOOST.") || schema.find(key) != null);
				}
				if (step instanceof Wizard.FieldsStep fields) {
					for (Wizard.Field field : fields.fields()) {
						expect(field.key() + " is asked in plain words", !field.label().contains(field.key()));
					}
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static void journey(WizardEngine engine, ModEntry mod) throws Exception {
		WizardQuestion first = engine.begin(mod, "race");
		expect("the first question asks for a name", first.kind().equals("name") && first.prompt().contains("called"));
		WizardEngine.Refused taken = refused(() -> engine.start(mod, "race", "Human"));
		expect("a name the game has is refused: " + (taken == null ? null : taken.getMessage()), taken != null && taken.question() != null && taken.question().kind().equals("name"));
		expect("an empty name is refused", refused(() -> engine.start(mod, "race", " ?? ")) != null);

		WizardQuestion look = engine.start(mod, "race", "Dondorian Elves");
		expect("then which race it's like: " + look.kind(), look.kind().equals("look") && look.name().equals("DONDORIAN_ELVES"));
		expect("the looks are named as the game names them", look.choices().stream().anyMatch(choice -> choice.id().equals("HUMAN") && choice.label().equals("Human")));
		Path versionFolder = versionFolder(mod.root());
		Path data = versionFolder.resolve("assets").resolve("init").resolve("race").resolve("DONDORIAN_ELVES.txt");
		expect("nothing is written yet", !Files.exists(data));
		expect("a second wizard of the same name is refused", refused(() -> engine.start(mod, "race", "Dondorian Elves")) != null);

		WizardQuestion again = engine.back(mod, "race", "DONDORIAN_ELVES");
		expect("back from the look forgets it, so the name can change", again.kind().equals("name") && engine.unfinished(mod).isEmpty());
		look = engine.start(mod, "race", "Dondorian Elves");

		WizardQuestion words = engine.answer(mod, "race", "DONDORIAN_ELVES", Map.of("choice", "HUMAN"));
		expect("choosing a look writes the files: " + words.note(), Files.isRegularFile(data));
		expect("and copies its own pictures, renamed", Files.isRegularFile(versionFolder.resolve("assets").resolve("sprite").resolve("race").resolve("Dondorian_elves.png")));
		expect("then asks about its words", words.kind().equals("fields") && words.step().equals("words"));
		expect("starting from the name as typed", value(words, "NAME").equals("Dondorian Elves") && value(words, "POSSESSIVE").equals("Dondorian Elves"));
		expect("and the handbook's words for them are there", words.handbook().size() == 4);
		expect("the look can't be gone back to", words.canGoBack() == false);
		expect("the new race's own files are found, so nothing is reported missing: " + words.findings(), words.findings().isEmpty());
		expect("and a question carries no findings at all", words.findings().isEmpty() && words.refused() == null);
		expect("picking the wizard is the first answer", words.history().get(0).answer().equals("Race"));

		WizardQuestion description = engine.answer(mod, "race", "DONDORIAN_ELVES", Map.of("values", Map.of("NAMES", "Dondorian Elvenfolk", "POSSESSIVE", "Elven")));
		expect("words are saved and the next prompt uses them: " + description.prompt(), description.prompt().equals("Describe Dondorian Elvenfolk."));
		expect("the history shows the answers as the files hold them", description.history().stream().anyMatch(exchange -> exchange.answer().contains("Dondorian Elvenfolk")));
		expect("and going back is now possible", description.canGoBack());

		WizardQuestion playable = engine.answer(mod, "race", "DONDORIAN_ELVES", Map.of("values", Map.of("DESC", "Elves of the deep.")));
		expect("a choice is asked: " + playable.kind(), playable.kind().equals("choice") && playable.choices().stream().anyMatch(choice -> choice.current()));
		WizardQuestion lifespan = engine.answer(mod, "race", "DONDORIAN_ELVES", Map.of("choice", "1"));
		expect("then the lifespan, in years from the look's boost: " + lifespan.number(), lifespan.kind().equals("number") && "80".equals(lifespan.number().value()));
		expect("a lifespan of words is refused", refused(() -> engine.answer(mod, "race", "DONDORIAN_ELVES", Map.of("value", "old"))) != null);
		WizardQuestion growing = engine.answer(mod, "race", "DONDORIAN_ELVES", Map.of("value", "150"));
		Map<String, String> saved = opened(engine, mod);
		expect("the lifespan is saved as the game's multiplier: " + saved.get("BOOST.PHYSICS_DEATH_AGE>MUL"), "1.5".equals(saved.get("BOOST.PHYSICS_DEATH_AGE>MUL")) && "false".equals(saved.get("PLAYABLE")));
		expect("then growing up", growing.step().equals("growing"));

		WizardQuestion body = engine.skip(mod, "race", "DONDORIAN_ELVES");
		expect("a question can be skipped", body.step().equals("body"));
		byte[] before = Files.readAllBytes(data);
		WizardEngine.Refused tooShort = refused(() -> engine.answer(mod, "race", "DONDORIAN_ELVES", Map.of("values", Map.of("PROPERTIES.HEIGHT", "-5"))));
		expect("a value the game refuses is refused, with why: " + (tooShort == null ? null : tooShort.question().findings()), tooShort != null && tooShort.question().findings().stream().anyMatch(problem -> problem.blocksSaving()));
		expect("and nothing was written", java.util.Arrays.equals(before, Files.readAllBytes(data)));
		WizardQuestion food = engine.answer(mod, "race", "DONDORIAN_ELVES", Map.of("values", Map.of("PROPERTIES.WIDTH", "50")));
		expect("a value above a limit the game doesn't enforce is saved: " + opened(engine, mod).get("PROPERTIES.WIDTH"), "50".equals(opened(engine, mod).get("PROPERTIES.WIDTH")));
		expect("with its warning shown on the next", food.step().equals("food"));

		WizardEngine fresh = new WizardEngine(game, handbookStore(), WizardStore.openDefault());
		WizardQuestion resumed = fresh.question(mod, "race", "DONDORIAN_ELVES");
		expect("a fresh engine carries on where it was", resumed.step().equals("food") && resumed.history().size() == 9);
		WizardQuestion homeland = fresh.skip(mod, "race", "DONDORIAN_ELVES");
		WizardQuestion.FieldView climate = homeland.fields().stream().filter(field -> field.key().equals("POPULATION.CLIMATE")).findFirst().orElseThrow();
		expect("climates are amounts by name: " + climate.options(), climate.input().equals("amounts") && climate.options() != null && !climate.options().isEmpty());
		String first0 = climate.options().get(0);
		WizardQuestion speech = fresh.answer(mod, "race", "DONDORIAN_ELVES", Map.of("values", Map.of("POPULATION.CLIMATE." + first0, "0.25")));
		expect("an amount is saved: " + opened(engine, mod).get("POPULATION.CLIMATE." + first0), "0.25".equals(opened(engine, mod).get("POPULATION.CLIMATE." + first0)));
		expect("an amount for a name that isn't one is refused", refused(() -> fresh.answer(mod, "race", "DONDORIAN_ELVES", Map.of("values", Map.of("POPULATION.CLIMATE.NOWHERE", "1")))) != null);
		WizardQuestion back = fresh.back(mod, "race", "DONDORIAN_ELVES");
		expect("back goes to the last question", back.step().equals("homeland") && speech.step().equals("numbers"));
		expect("a number with both ends set offers a slider", climate.hasSlider() && climate.max() == 1.0 && climate.maxEnforced());
		fresh.skip(mod, "race", "DONDORIAN_ELVES");
		WizardQuestion surroundings = fresh.skip(mod, "race", "DONDORIAN_ELVES");
		expect("then what surroundings they like", surroundings.step().equals("surroundings"));
		WizardQuestion.FieldView roads = field(surroundings, "PREFERRED.ROAD");
		expect("only floors that are roads are offered as roads: " + roads.options(), roads.options().contains("STONE1") && !roads.options().contains("GRASS"));
		expect("buildings include the mountain and each structure: " + field(surroundings, "PREFERRED.STRUCTURE").options(),
				field(surroundings, "PREFERRED.STRUCTURE").options().containsAll(List.of("MOUNTAIN", "OUTDOORS", "WOOD", "STONE")));
		WizardQuestion work = fresh.skip(mod, "race", "DONDORIAN_ELVES");
		WizardQuestion.FieldView jobs = field(work, "PREFERRED.WORK");
		expect("jobs keep the names the file has: " + jobs.amounts().keySet(), jobs.amounts().containsKey("_POLICE"));
		expect("and a range as wide as -10000 to 10000 gets no slider", !jobs.hasSlider());
		WizardQuestion others = fresh.answer(mod, "race", "DONDORIAN_ELVES", Map.of("values", WORK));
		expect("a job's amount is saved: " + opened(engine, mod).get("PREFERRED.WORK.MINE_GEM"), "-0.5".equals(opened(engine, mod).get("PREFERRED.WORK.MINE_GEM")) && others.step().equals("others"));
		WizardQuestion law = fresh.skip(mod, "race", "DONDORIAN_ELVES");
		WizardQuestion.FieldView freedom = law.fields().get(0);
		expect("each crime's freedom is asked as an amount per crime: " + freedom.options(), freedom.input().equals("amounts") && "FREEDOM".equals(freedom.column()) && freedom.options().contains("THEFT"));
		expect("with that number's own range", freedom.hasSlider() && freedom.max() == 1.0);
		expect("a crime the game doesn't have is refused", refused(() -> fresh.answer(mod, "race", "DONDORIAN_ELVES", Map.of("values", Map.of("PREFERRED.CRIME.NOTHING.FREEDOM", "1")))) != null);
		WizardQuestion trade = fresh.answer(mod, "race", "DONDORIAN_ELVES", Map.of("values", LAW));
		Map<String, String> lawful = opened(engine, mod);
		expect("a crime's number is saved inside its block: " + lawful.get("PREFERRED.CRIME.THEFT.FREEDOM"), "0.4".equals(lawful.get("PREFERRED.CRIME.THEFT.FREEDOM")) && "0.2".equals(lawful.get("PREFERRED.PUNISHMENT.EXECUTE")));
		expect("then prices", trade.step().equals("trade"));
		WizardQuestion traits = fresh.skip(mod, "race", "DONDORIAN_ELVES");
		String trait = field(traits, "TRAIT").options().get(0);
		WizardQuestion remains = fresh.answer(mod, "race", "DONDORIAN_ELVES", Map.of("values", Map.of("TRAIT." + trait, "0.3")));
		expect("a trait's chance is saved", "0.3".equals(opened(engine, mod).get("TRAIT." + trait)) && remains.step().equals("remains"));
		fresh.skip(mod, "race", "DONDORIAN_ELVES");
		fresh.skip(mod, "race", "DONDORIAN_ELVES");
		WizardQuestion tourists = fresh.skip(mod, "race", "DONDORIAN_ELVES");
		WizardQuestion.FieldView reviews = field(tourists, "TOURIST.TOURIST_TEXT_FILE");
		expect("the tourists' reviews are picked from those there are: " + reviews.options(), tourists.step().equals("tourists") && reviews.input().equals("one") && !reviews.options().isEmpty());
		fresh.skip(mod, "race", "DONDORIAN_ELVES");
		WizardQuestion portraits = fresh.answer(mod, "race", "DONDORIAN_ELVES", Map.of("values", Map.of("HELLO", "Well met\nHail")));
		expect("rows are saved one to a line", "Well met\nHail".equals(opened(engine, mod).get("HELLO")));
		expect("the last question offers the portrait wizard: " + portraits.step(), portraits.step().equals("portraits") && portraits.kind().equals("choice") && portraits.choices().size() == 2);
		WizardQuestion done = fresh.answer(mod, "race", "DONDORIAN_ELVES", Map.of("choice", "1"));
		expect("Later goes on to the end", done.kind().equals("done") && done.handover() == null);
		WizardQuestion finished = fresh.answer(mod, "race", "DONDORIAN_ELVES", Map.of());
		expect("finishing forgets it", finished.kind().equals("finished") && fresh.unfinished(mod).isEmpty());
		expect("and the race is still in the mod", Files.isRegularFile(data));
	}

	/** The same answers through the agent's operations, in another mod. */
	@SuppressWarnings("unchecked")
	private static void agent(Operations ops) {
		Map<String, Object> first = ok(ops, "wizard_question", "mod", "Agent Mod", "wizard", "race");
		expect("an agent gets the name question", "name".equals(first.get("kind")) && String.valueOf(first.get("how_to_answer")).contains("wizard_start"));
		ok(ops, "wizard_start", "mod", "Agent Mod", "wizard", "race", "display_name", "Dondorian Elves");
		Map<String, Object> listed = ok(ops, "list_wizards", "mod", "Agent Mod");
		expect("it's listed as unfinished", ((List<Object>) listed.get("unfinished")).size() == 1);
		answer(ops, Map.of("choice", "HUMAN"));
		answer(ops, Map.of("values", Map.of("NAMES", "Dondorian Elvenfolk", "POSSESSIVE", "Elven")));
		answer(ops, Map.of("values", Map.of("DESC", "Elves of the deep.")));
		answer(ops, Map.of("choice", "1"));
		answer(ops, Map.of("value", "150"));
		ok(ops, "wizard_skip", "mod", "Agent Mod", "wizard", "race", "name", "DONDORIAN_ELVES");
		Operations.Result bad = ops.call("wizard_answer", in("mod", "Agent Mod", "wizard", "race", "name", "DONDORIAN_ELVES", "answer", in("values", in("PROPERTIES.HEIGHT", "-5"))));
		expect("an agent's bad answer is refused with the question: " + bad.payload().keySet(), bad.outcome() == Operations.Outcome.REFUSED && bad.payload().get("question") instanceof Map);
		answer(ops, Map.of("values", Map.of("PROPERTIES.WIDTH", "50")));
		ok(ops, "wizard_skip", "mod", "Agent Mod", "wizard", "race", "name", "DONDORIAN_ELVES");
		Map<String, Object> homeland = ok(ops, "wizard_question", "mod", "Agent Mod", "wizard", "race", "name", "DONDORIAN_ELVES");
		String first0 = null;
		for (Object field : (List<Object>) homeland.get("fields")) {
			Map<String, Object> row = (Map<String, Object>) field;
			if ("POPULATION.CLIMATE".equals(row.get("key"))) {
				first0 = String.valueOf(((List<Object>) row.get("options")).get(0));
			}
		}
		answer(ops, Map.of("values", Map.of("POPULATION.CLIMATE." + first0, "0.25")));
		ok(ops, "wizard_back", "mod", "Agent Mod", "wizard", "race", "name", "DONDORIAN_ELVES");
		skip(ops, 3);
		answer(ops, Map.of("values", WORK));
		skip(ops, 1);
		answer(ops, Map.of("values", LAW));
		Map<String, Object> traits = skip(ops, 1);
		String trait = null;
		for (Object field : (List<Object>) traits.get("fields")) {
			Map<String, Object> row = (Map<String, Object>) field;
			if ("TRAIT".equals(row.get("key"))) {
				trait = String.valueOf(((List<Object>) row.get("options")).get(0));
			}
		}
		answer(ops, Map.of("values", Map.of("TRAIT." + trait, "0.3")));
		skip(ops, 4);
		answer(ops, Map.of("values", Map.of("HELLO", "Well met\nHail")));
		Map<String, Object> end = answer(ops, Map.of("choice", "0"));
		expect("designing the portraits now finishes the race", "finished".equals(end.get("kind")));
		expect("and hands the race to the portrait wizard: " + end.get("next_wizard"), end.get("next_wizard") instanceof Map<?, ?> next && "portrait".equals(next.get("wizard")) && "DONDORIAN_ELVES".equals(next.get("display_name")));
		expect("and the JSON is written", JsonText.write(end).contains("\"finished\""));
		Map<String, Object> listedAfter = ok(ops, "list_wizards", "mod", "Agent Mod");
		expect("the portrait wizard is listed for agents", String.valueOf(listedAfter.get("wizards")).contains("portrait"));
	}

	/** The answers both journeys give, so their files can be compared. */
	private static final Map<String, Object> WORK = Map.of("PREFERRED.WORK.MINE_GEM", "-0.5");
	private static final Map<String, Object> LAW = Map.of("PREFERRED.CRIME.THEFT.FREEDOM", "0.4", "PREFERRED.PUNISHMENT.EXECUTE", "0.2");

	/** Skips questions as an agent, giving the question after the last. */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> skip(Operations ops, int times) {
		Map<String, Object> last = null;
		for (int i = 0; i < times; i++) {
			last = ok(ops, "wizard_skip", "mod", "Agent Mod", "wizard", "race", "name", "DONDORIAN_ELVES");
		}
		return last.get("question") instanceof Map<?, ?> inner ? (Map<String, Object>) inner : last;
	}

	private static WizardQuestion.FieldView field(WizardQuestion question, String key) {
		return question.fields().stream().filter(field -> field.key().equals(key)).findFirst().orElseThrow(() -> new AssertionError("No field " + key + " on " + question.step()));
	}

	private static Map<String, Object> answer(Operations ops, Map<String, Object> answer) {
		return ok(ops, "wizard_answer", "mod", "Agent Mod", "wizard", "race", "name", "DONDORIAN_ELVES", "answer", answer);
	}

	/** The person's and the agent's answers give byte-identical files. */
	private static void compare(Path one, Path two) throws IOException {
		Map<String, byte[]> first = files(one);
		Map<String, byte[]> second = files(two);
		expect("the same files are written: " + first.keySet() + " and " + second.keySet(), first.keySet().equals(second.keySet()));
		for (String file : first.keySet()) {
			expect(file + " is the same either way", second.containsKey(file) && java.util.Arrays.equals(first.get(file), second.get(file)));
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

	/** Outside a packaged build, with nowhere to keep progress, the wizard is refused rather than forgetting answers. */
	private static void noStore(ModEntry mod) {
		WizardEngine nowhere = new WizardEngine(game, handbookStore(), new WizardStore(null));
		WizardEngine.Refused refused = refused(() -> nowhere.begin(mod, "race"));
		expect("without a Wizards folder it says why: " + (refused == null ? null : refused.getMessage()), refused != null && refused.getMessage().contains(".exe"));
	}

	private static Map<String, String> opened(WizardEngine engine, ModEntry mod) throws Exception {
		Operations ops = new Operations(() -> game, mods, new ToolkitSettings(temp.resolve("settings.properties")), handbookStore(), true);
		Operations.Result result = ops.call("open_asset", in("mod", mod.folderName(), "type", "Race", "name", "DONDORIAN_ELVES"));
		@SuppressWarnings("unchecked")
		Map<String, String> values = (Map<String, String>) result.payload().get("values");
		return values == null ? Map.of() : values;
	}

	private static String value(WizardQuestion question, String key) {
		return question.fields().stream().filter(field -> field.key().equals(key)).map(WizardQuestion.FieldView::value).findFirst().orElse("");
	}

	private static Path versionFolder(Path root) throws IOException {
		try (Stream<Path> children = Files.list(root)) {
			return children.filter(child -> Files.isDirectory(child) && child.getFileName().toString().matches("V\\d+")).findFirst().orElse(root.resolve("V71"));
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
		return new HandbookStore(temp == null ? Path.of(System.getProperty("java.io.tmpdir")).resolve("syx-wizard-handbook") : temp.resolve("handbook"),
				etag -> new HandbookStore.Response(404, null, new byte[0]));
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
