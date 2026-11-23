package syx.core.wizard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import syx.core.AssetCatalog;
import syx.core.AssetChecker;
import syx.core.AssetCreator;
import syx.core.AssetEditing;
import syx.core.AssetForm;
import syx.core.AssetSchema;
import syx.core.GameInstall;
import syx.core.GameParser;
import syx.core.HandbookIndex;
import syx.core.HandbookStore;
import syx.core.ModEntry;
import syx.core.SpriteCopy;
import syx.core.TagSpec;
import syx.core.Workspace;

/**
 * Runs a wizard. Every answer is saved into the asset's files when it's given, through the same create and save
 * as the asset form, so it's checked by the same rules and refused for the same reasons. Each call reads the
 * files afresh, so a change made in the editor between questions is what the next question starts from.
 *
 * <p>The first two answers are special. The name is only remembered, and choosing the look writes the asset's
 * files, as a copy of that look's with the name in it, and copies the look's own images under the new name.
 * Neither can be changed afterwards, because that would mean deleting files, which the toolkit never does.
 */
public final class WizardEngine {

	/** An answer that wasn't taken, with the question to ask again and what's wrong. Nothing was written. */
	public static final class Refused extends Exception {
		private static final long serialVersionUID = 1L;
		private final transient WizardQuestion question;

		Refused(String message, WizardQuestion question) {
			super(message);
			this.question = question;
		}

		/** The question as it should be shown now, or null if there's nothing to ask, such as for an unknown wizard. */
		public WizardQuestion question() {
			return question;
		}
	}

	/** A wizard someone can pick, or why they can't. */
	public record Offer(Wizard.Definition definition, String unavailableReason) {
		public boolean available() {
			return unavailableReason == null;
		}
	}

	private final Path game;
	private final HandbookStore handbook;
	private final WizardStore store;

	public WizardEngine(Path game, HandbookStore handbook, WizardStore store) {
		this.game = game;
		this.handbook = handbook;
		this.store = store;
	}

	public WizardStore store() {
		return store;
	}

	/** The Portrait Design Wizard, which every call here hands its own wizard's calls to. */
	public PortraitWizardEngine portraits() {
		return new PortraitWizardEngine(game, store);
	}

	/** Every wizard, each with why it can't be used, such as its type being turned off after a game update. */
	public List<Offer> offers() {
		List<AssetCatalog.Entry> entries = AssetCatalog.load(GameInstall.jarIn(game));
		List<Offer> offers = new ArrayList<>();
		for (Wizard.Definition definition : Wizards.all()) {
			AssetCatalog.Entry entry = entries.stream().filter(e -> e.type().label().equals(definition.typeLabel())).findFirst().orElse(null);
			String reason = entry == null ? "The toolkit has no " + definition.typeLabel() + " type." : entry.available() ? store.problem() : entry.unavailableReason();
			offers.add(new Offer(definition, reason));
		}
		offers.add(new Offer(Wizards.PORTRAIT, store.problem()));
		return offers;
	}

	/** The mod's unfinished wizards, newest last. */
	public List<WizardStore.Session> unfinished(ModEntry mod) {
		return store.sessions(mod.folderName()).stream().filter(session -> Wizards.find(session.wizard()) != null || Wizards.isPortrait(session.wizard())).toList();
	}

	/** The first question, which only asks for a name, or for the portrait wizard which race. Nothing is kept until it's answered. */
	public WizardQuestion begin(ModEntry mod, String wizardId) throws Refused, IOException, GameParser.UnavailableException {
		if (Wizards.isPortrait(wizardId)) {
			return portraits().begin(mod);
		}
		Wizard.Definition definition = definition(wizardId);
		refuseUnusable(definition);
		return nameQuestion(mod, definition, null, null);
	}

	/** Answers the name question, which starts the wizard. Nothing is written to the mod yet. */
	public WizardQuestion start(ModEntry mod, String wizardId, String typed) throws Refused, IOException, GameParser.UnavailableException {
		if (Wizards.isPortrait(wizardId)) {
			return portraits().start(mod, typed);
		}
		Wizard.Definition definition = definition(wizardId);
		refuseUnusable(definition);
		String display = NameForms.display(typed);
		String stem = NameForms.stem(display);
		if (stem == null) {
			throw new Refused("A name needs at least one letter or digit.", nameQuestion(mod, definition, null, "A name needs at least one letter or digit."));
		}
		if (store.find(mod.folderName(), definition.id(), stem).isPresent()) {
			String message = "You've already started one called " + store.find(mod.folderName(), definition.id(), stem).get().display() + ". Continue it from the list, or choose another name.";
			throw new Refused(message, nameQuestion(mod, definition, null, message));
		}
		try (Workspace.Context context = Workspace.open(game, handbook, mod)) {
			AssetCatalog.Entry entry = entry(context, definition);
			String problem = AssetCreator.problemWithName(entry.type(), entry.schema(), stem, context.index());
			if (problem != null) {
				String message = "That name can't be used: " + problem;
				throw new Refused(message, nameQuestion(mod, definition, null, message));
			}
			Wizard.Step first = definition.steps().get(0);
			List<String> answered = List.of(first.id());
			WizardStore.Session session = new WizardStore.Session(mod.folderName(), definition.id(), stem, display, null, definition.steps().get(1).id(), answered);
			store.put(session);
			return question(new State(context, entry, definition, mod, session, null), null);
		}
	}

	/** The question a wizard is on. */
	public WizardQuestion question(ModEntry mod, String wizardId, String stem) throws Refused, IOException, GameParser.UnavailableException {
		if (Wizards.isPortrait(wizardId)) {
			return portraits().question(mod, stem);
		}
		Wizard.Definition definition = definition(wizardId);
		WizardStore.Session session = session(mod, definition, stem);
		try (Workspace.Context context = Workspace.open(game, handbook, mod)) {
			return question(state(context, definition, mod, session), null);
		}
	}

	/**
	 * Answers the question the wizard is on, saves it, and gives the next one.
	 *
	 * @param answer {@code choice} for a look or a choice, {@code value} for a number, {@code values} for fields
	 *            (only what changes, with a null putting a tag back to absent), and nothing for the end
	 */
	public WizardQuestion answer(ModEntry mod, String wizardId, String stem, Map<String, Object> answer) throws Refused, IOException, GameParser.UnavailableException {
		if (Wizards.isPortrait(wizardId)) {
			return portraits().answer(mod, stem, answer);
		}
		Wizard.Definition definition = definition(wizardId);
		WizardStore.Session session = session(mod, definition, stem);
		try (Workspace.Context context = Workspace.open(game, handbook, mod)) {
			State state = state(context, definition, mod, session);
			Wizard.Step step = current(state).step();
			WizardQuestion asked = question(state, null);
			return switch (step) {
			case Wizard.NameStep name -> throw new Refused("The name is already chosen.", asked);
			case Wizard.LookStep look -> {
				String note = chooseLook(state, asked, answer);
				// The files were just written, so what's in the mod is read again before anything is checked against it.
				try (Workspace.Context fresh = Workspace.open(game, handbook, mod)) {
					yield advance(fresh, definition, mod, session(mod, definition, stem), look, note);
				}
			}
			case Wizard.DoneStep done -> finish(state, asked);
			case Wizard.ChoiceStep choice when choice.id().equals(RaceWizard.PORTRAITS_STEP) && isDesignNow(choice, answer) -> {
				// Designing the portraits now finishes this wizard and hands the race to the portrait wizard.
				changes(state, step, asked, answer);
				String race = state.entry().type().fileName(session.stem());
				yield finish(state, asked).withHandover(new WizardQuestion.Handover(PortraitWizardEngine.ID, race));
			}
			default -> {
				Map<String, String> changes = changes(state, step, asked, answer);
				save(state, changes, asked);
				yield advance(context, definition, mod, session, step, null);
			}
			};
		}
	}

	private static boolean isDesignNow(Wizard.ChoiceStep choice, Map<String, Object> answer) {
		Object chosen = answer.get("choice");
		return "0".equals(chosen) || choice.choices().get(0).label().equals(chosen);
	}

	/** Passes over the question it's on, leaving the asset's values as they are. */
	public WizardQuestion skip(ModEntry mod, String wizardId, String stem) throws Refused, IOException, GameParser.UnavailableException {
		if (Wizards.isPortrait(wizardId)) {
			return portraits().skip(mod, stem);
		}
		Wizard.Definition definition = definition(wizardId);
		WizardStore.Session session = session(mod, definition, stem);
		try (Workspace.Context context = Workspace.open(game, handbook, mod)) {
			State state = state(context, definition, mod, session);
			Wizard.Step step = current(state).step();
			WizardQuestion asked = question(state, null);
			if (!asked.canSkip()) {
				throw new Refused("This question can't be skipped.", asked);
			}
			return advance(context, definition, mod, session, step, null);
		}
	}

	/**
	 * Goes back to the last question answered after the files were made. Going back from choosing the look forgets
	 * the wizard, which has written nothing yet, so a different name can be given.
	 */
	public WizardQuestion back(ModEntry mod, String wizardId, String stem) throws Refused, IOException, GameParser.UnavailableException {
		if (Wizards.isPortrait(wizardId)) {
			return portraits().back(mod, stem);
		}
		Wizard.Definition definition = definition(wizardId);
		WizardStore.Session session = session(mod, definition, stem);
		try (Workspace.Context context = Workspace.open(game, handbook, mod)) {
			State state = state(context, definition, mod, session);
			Wizard.Step step = current(state).step();
			if (step instanceof Wizard.LookStep) {
				store.remove(mod.folderName(), definition.id(), session.stem());
				return nameQuestion(mod, definition, "Nothing was written, so you can choose a different name.", null);
			}
			int lookAt = lookIndex(definition);
			List<String> answered = new ArrayList<>(session.answered());
			while (!answered.isEmpty()) {
				String last = answered.remove(answered.size() - 1);
				if (definition.indexOf(last) > lookAt) {
					WizardStore.Session moved = session.at(last, answered);
					store.put(moved);
					return question(state(context, definition, mod, moved), null);
				}
			}
			throw new Refused("The earlier answers made the files, so they can't be changed here. Everything else can be changed in the editor.", question(state, null));
		}
	}

	/** The asset's data file, for opening it in the editor, whether or not its wizard is finished. */
	public Path assetFile(ModEntry mod, String wizardId, String stem) throws Refused, IOException, GameParser.UnavailableException {
		Wizard.Definition definition = definition(wizardId);
		try (Workspace.Context context = Workspace.open(game, handbook, mod)) {
			return dataFile(context, entry(context, definition), stem);
		}
	}

	/** Forgets a wizard. Whatever it has saved stays in the mod. */
	public void forget(ModEntry mod, String wizardId, String stem) throws Refused, IOException {
		if (Wizards.isPortrait(wizardId)) {
			portraits().forget(mod, stem);
			return;
		}
		Wizard.Definition definition = definition(wizardId);
		session(mod, definition, stem);
		store.remove(mod.folderName(), definition.id(), stem);
	}

	// ---- What's open while a call runs

	private record State(Workspace.Context context, AssetCatalog.Entry entry, Wizard.Definition definition, ModEntry mod, WizardStore.Session session, AssetEditing.Opened opened) {
		AssetSchema schema() {
			return entry.schema();
		}

		Map<String, String> values() {
			return opened == null ? Map.of() : opened.values();
		}

		Set<String> kept() {
			return opened == null ? Set.of() : opened.kept();
		}
	}

	private record Position(int index, Wizard.Step step, List<String> passed) {
	}

	private State state(Workspace.Context context, Wizard.Definition definition, ModEntry mod, WizardStore.Session session) throws Refused {
		AssetCatalog.Entry entry = entry(context, definition);
		AssetEditing.Opened opened = null;
		if (session.look() != null) {
			opened = open(context, entry, mod, session);
		}
		return new State(context, entry, definition, mod, session, opened);
	}

	private AssetEditing.Opened open(Workspace.Context context, AssetCatalog.Entry entry, ModEntry mod, WizardStore.Session session) throws Refused {
		Path file = dataFile(context, entry, session.stem());
		if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
			throw new Refused(session.display() + "'s files aren't in the mod any more, so this wizard can't carry on. Forget it to start again.", null);
		}
		AssetEditing.Location location = AssetEditing.locate(mod.root(), context.version(), file, List.of(entry), context.index());
		if (location.problem() != null) {
			throw new Refused(location.problem(), null);
		}
		try {
			return AssetEditing.load(location, context.parser(), context.index());
		} catch (AssetEditing.OpenException e) {
			throw new Refused(e.getMessage(), null);
		}
	}

	private static Path dataFile(Workspace.Context context, AssetCatalog.Entry entry, String stem) {
		return context.versionFolder().resolve("assets").resolve("init").resolve(entry.type().initFolder()).resolve(entry.type().fileName(stem) + ".txt");
	}

	private static Wizard.Definition definition(String wizardId) throws Refused {
		Wizard.Definition definition = Wizards.find(wizardId);
		if (definition == null) {
			throw new Refused("There's no wizard called " + wizardId + ". list_wizards shows them.", null);
		}
		return definition;
	}

	private void refuseUnusable(Wizard.Definition definition) throws Refused {
		if (!store.usable()) {
			throw new Refused(store.problem(), null);
		}
		for (Offer offer : offers()) {
			if (offer.definition() == Wizards.PORTRAIT) {
				continue;
			}
			if (offer.definition() == definition && !offer.available()) {
				throw new Refused(definition.title() + " can't be used: " + offer.unavailableReason(), null);
			}
		}
	}

	private static AssetCatalog.Entry entry(Workspace.Context context, Wizard.Definition definition) throws Refused {
		for (AssetCatalog.Entry entry : context.entries()) {
			if (entry.type().label().equals(definition.typeLabel())) {
				if (!entry.available()) {
					throw new Refused(definition.title() + " can't be used: " + entry.unavailableReason(), null);
				}
				return entry;
			}
		}
		throw new Refused("The toolkit has no " + definition.typeLabel() + " type.", null);
	}

	private WizardStore.Session session(ModEntry mod, Wizard.Definition definition, String stem) throws Refused {
		if (!store.usable()) {
			throw new Refused(store.problem(), null);
		}
		return store.find(mod.folderName(), definition.id(), stem == null ? "" : stem)
				.orElseThrow(() -> new Refused(mod.folderName() + " has no unfinished " + definition.title().toLowerCase(Locale.ROOT) + " wizard called " + stem + ". list_wizards shows them.", null));
	}

	private static int lookIndex(Wizard.Definition definition) {
		for (int i = 0; i < definition.steps().size(); i++) {
			if (definition.steps().get(i) instanceof Wizard.LookStep) {
				return i;
			}
		}
		return 0;
	}

	/** The step the session is on, passing over any that don't apply now, with a note for each passed for a reason worth saying. */
	private Position current(State state) {
		Wizard.Definition definition = state.definition();
		int index = definition.indexOf(state.session().step());
		if (index < 0) {
			// The definition changed since this was saved: carry on after the last answer it still has.
			index = lookIndex(definition) + 1;
			for (String id : state.session().answered()) {
				index = Math.max(index, definition.indexOf(id) + 1);
			}
		}
		return from(state, index);
	}

	private Position from(State state, int start) {
		List<String> passed = new ArrayList<>();
		WizardFacts facts = new WizardFacts(GameInstall.jarIn(game));
		Wizard.Definition definition = state.definition();
		for (int i = Math.min(start, definition.steps().size() - 1); i < definition.steps().size(); i++) {
			Wizard.Step step = definition.steps().get(i);
			if (step instanceof Wizard.DoneStep) {
				return new Position(i, step, passed);
			}
			if (!step.applies(state.values())) {
				continue;
			}
			if (step instanceof Wizard.NumberStep number && number.converter().problem(facts) != null) {
				passed.add("I've left out \"" + Phrase.fill(number.prompt(), state.values(), state.session().display()) + "\", because " + number.converter().problem(facts));
				continue;
			}
			return new Position(i, step, passed);
		}
		int last = definition.steps().size() - 1;
		return new Position(last, definition.steps().get(last), passed);
	}

	private WizardQuestion advance(Workspace.Context context, Wizard.Definition definition, ModEntry mod, WizardStore.Session session, Wizard.Step answeredStep, String note)
			throws Refused, IOException {
		List<String> answered = new ArrayList<>(session.answered());
		answered.remove(answeredStep.id());
		answered.add(answeredStep.id());
		WizardStore.Session moved = session.at(answeredStep.id(), answered);
		State state = state(context, definition, mod, moved);
		Position next = from(state, definition.indexOf(answeredStep.id()) + 1);
		moved = moved.at(next.step().id(), answered);
		store.put(moved);
		return question(new State(context, state.entry(), definition, mod, moved, state.opened()), note);
	}

	// ---- Answers

	/** Writes the asset's files from the chosen look, and says what was done. */
	private String chooseLook(State state, WizardQuestion asked, Map<String, Object> answer) throws Refused, IOException {
		Workspace.Context context = state.context();
		AssetCatalog.Entry entry = state.entry();
		AssetSchema schema = entry.schema();
		String look = answer.get("choice") instanceof String chosen ? chosen : null;
		List<String> looks = AssetForm.appearances(schema, context.index());
		if (look == null || !looks.contains(look)) {
			throw new Refused("Choose one of the answers.", asked.refusedWith("Choose one of the answers.", List.of()));
		}
		WizardStore.Session session = state.session();
		String stem = session.stem();
		String display = session.display();
		Map<String, String> values = new LinkedHashMap<>(AssetForm.startingValues(schema, context.index(), look, stem));
		values.putAll(state.definition().named().apply(display));
		List<String> copied = List.of();
		String spriteNote;
		try {
			SpriteCopy.Result result = SpriteCopy.copy(entry, game, state.mod(), context.version(), context.index(), context.parser(), look, stem);
			values.putAll(result.values());
			copied = result.files();
			spriteNote = "I copied " + copied.size() + (copied.size() == 1 ? " picture" : " pictures") + " of its own, renamed for yours, so you can repaint them.";
		} catch (SpriteCopy.Rejected e) {
			spriteNote = switch (e.why()) {
			case NONE -> "It has no pictures of its own to copy, so yours uses the game's.";
			case CLASH -> "Pictures with your race's names are already in the mod, so I left them alone, and yours uses the game's until you choose them in the editor.";
			default -> "Its pictures couldn't be copied (" + e.getMessage() + "), so yours uses the game's.";
			};
		} catch (GameParser.ReadException e) {
			spriteNote = "Its pictures couldn't be copied, because the game's parser couldn't read it, so yours uses the game's.";
		}
		values = AssetForm.withFormBlanks(schema, values, true);
		Path assets = context.versionFolder().resolve("assets");
		refuseLinks(state.mod(), dataFile(context, entry, stem), asked);
		if (entry.type().textFolder() != null) {
			refuseLinks(state.mod(), assets.resolve("text").resolve(entry.type().textFolder()).resolve(entry.type().fileName(stem) + ".txt"), asked);
		}
		AssetForm.Status status = AssetForm.status(schema, context.index(), values, null, context.handbook().versionNote(context.version()), null, false, List.of(),
				AssetForm.startingKept(schema, context.index(), look), AssetForm.startingKeptAsWritten(schema, context.index(), look));
		try {
			AssetCreator.create(state.mod().root(), entry.type(), schema, stem, values, look, context.parser(), context.index());
		} catch (AssetCreator.CreationException e) {
			for (String file : copied) {
				Files.deleteIfExists(state.mod().root().resolve(file));
			}
			throw new Refused(e.getMessage(), asked.refusedWith(e.getMessage(), problems(status.findings(), List.of(), true)));
		}
		store.put(session.withLook(look));
		return "I've made " + display + " as a copy of " + lookLabel(schema, context, look) + ". " + spriteNote;
	}

	private WizardQuestion finish(State state, WizardQuestion asked) throws IOException {
		store.remove(state.mod().folderName(), state.definition().id(), state.session().stem());
		return new WizardQuestion(asked.wizard(), asked.mod(), asked.name(), asked.display(), asked.step(), "finished", asked.prompt(), asked.help(), asked.evidence(), asked.position(),
				asked.total(), "Finished. " + asked.display() + " is in your mod, and everything else about it is in the editor.", List.of(), null, List.of(), asked.handbook(), asked.findings(),
				null, false, false, asked.history());
	}

	/** What an answer changes, in the form's values. */
	private Map<String, String> changes(State state, Wizard.Step step, WizardQuestion asked, Map<String, Object> answer) throws Refused {
		Map<String, String> changes = new LinkedHashMap<>();
		switch (step) {
		case Wizard.ChoiceStep choice -> {
			Object chosen = answer.get("choice");
			for (int i = 0; i < choice.choices().size(); i++) {
				Wizard.Choice option = choice.choices().get(i);
				if (String.valueOf(i).equals(chosen) || option.label().equals(chosen)) {
					changes.putAll(option.changes());
					return changes;
				}
			}
			throw new Refused("Choose one of the answers.", asked.refusedWith("Choose one of the answers.", List.of()));
		}
		case Wizard.NumberStep number -> {
			String text = text(answer.get("value"));
			String problem = numberProblem(number, text);
			if (problem != null) {
				throw new Refused(problem, asked.refusedWith(problem, List.of()));
			}
			changes.putAll(number.converter().write(Double.parseDouble(text.strip()), state.values(), new WizardFacts(GameInstall.jarIn(game))));
			return changes;
		}
		case Wizard.FieldsStep fields -> {
			if (!(answer.get("values") instanceof Map<?, ?> given)) {
				throw new Refused("Give the values that change, by their key.", asked.refusedWith("Give the values that change, by their key.", List.of()));
			}
			List<String> unknown = new ArrayList<>();
			for (Map.Entry<?, ?> item : given.entrySet()) {
				String key = String.valueOf(item.getKey());
				WizardQuestion.FieldView field = fieldFor(asked, key);
				if (field == null) {
					unknown.add(key);
					continue;
				}
				changes.put(key, item.getValue() == null ? null : text(item.getValue()));
			}
			if (!unknown.isEmpty()) {
				String message = "This question has nothing called " + String.join(", ", unknown) + ".";
				throw new Refused(message, asked.refusedWith(message, List.of()));
			}
			return changes;
		}
		default -> {
			return changes;
		}
		}
	}

	/** The field a key belongs to: the field itself, or one name's amount in an amounts field. */
	private static WizardQuestion.FieldView fieldFor(WizardQuestion asked, String key) {
		for (WizardQuestion.FieldView field : asked.fields()) {
			if (field.key().equals(key) && !field.input().equals("amounts")) {
				return field;
			}
			String name = field.input().equals("amounts") ? field.nameIn(key) : null;
			// A name it can have, or one the file already has an amount for, such as * for every one.
			if (name != null && (field.options() != null && field.options().stream().anyMatch(name::equalsIgnoreCase) || field.amounts().containsKey(name))) {
				return field;
			}
		}
		return null;
	}

	private static String text(Object value) {
		if (value instanceof Double number) {
			return number == Math.rint(number) && !Double.isInfinite(number) ? String.valueOf(number.longValue()) : number.toString();
		}
		return value == null ? null : String.valueOf(value);
	}

	private static String numberProblem(Wizard.NumberStep step, String text) {
		if (text == null || !text.strip().matches("-?(\\d+\\.?\\d*|\\.\\d+)")) {
			return "Give a number.";
		}
		double value = Double.parseDouble(text.strip());
		if (step.whole() && value != Math.rint(value)) {
			return "Give a whole number.";
		}
		if (value < step.min() || step.max() != null && value > step.max()) {
			return "Give a number from " + plain(step.min()) + (step.max() == null ? " up." : " to " + plain(step.max()) + ".");
		}
		return null;
	}

	/** Saves changes as the asset form would, or refuses with what's wrong. Nothing changing writes nothing. */
	private void save(State state, Map<String, String> changes, WizardQuestion asked) throws Refused, IOException {
		AssetEditing.Opened opened = state.opened();
		if (opened == null) {
			throw new Refused("The asset's files don't exist yet.", asked);
		}
		Map<String, String> baseline = formValues(opened, Map.of());
		Map<String, String> values = formValues(opened, changes);
		if (values.equals(baseline)) {
			return;
		}
		Workspace.Context context = state.context();
		AssetForm.Status status = AssetForm.status(opened.schema(), context.index().editing(opened.entry().type(), opened.fileName()), values, opened.scope(values, opened.kept()),
				context.handbook().versionNote(context.version()), null, opened.comments(), opened.misplacedText(), opened.kept(), opened.keptAsWritten());
		// Only what's about this question's own answers is said. Anything else isn't the person's to fix here.
		List<WizardQuestion.Problem> problems = problems(status.findings(), state.definition().step(asked.step()).keys(), false);
		if (!status.canSave()) {
			String message = problems.stream().anyMatch(WizardQuestion.Problem::blocksSaving) ? "That answer can't be saved." : "That can't be saved yet: " + status.summary();
			throw new Refused(message, asked.refusedWith(message, problems));
		}
		List<String> lost = AssetCreator.droppedBy(opened, baseline, values);
		if (!lost.isEmpty()) {
			String message = "Saving that would lose " + String.join(", ", lost) + ", so nothing was saved. Change it in the editor, which can ask first.";
			throw new Refused(message, asked.refusedWith(message, problems));
		}
		refuseLinks(state.mod(), opened.dataFile(), asked);
		if (opened.textFile() != null) {
			refuseLinks(state.mod(), opened.textFile(), asked);
		}
		try {
			AssetCreator.save(opened, baseline, values, false, context.parser(), context.index().editing(opened.entry().type(), opened.fileName()));
		} catch (AssetCreator.CreationException e) {
			throw new Refused(e.getMessage(), asked.refusedWith(e.getMessage(), problems));
		}
	}

	/** An asset's values as the form holds them, with changes on top. A null removes a value and anything under it. */
	private static Map<String, String> formValues(AssetEditing.Opened opened, Map<String, String> changes) {
		Map<String, String> values = new LinkedHashMap<>(opened.values());
		changes.forEach((key, value) -> {
			if (value == null) {
				values.keySet().removeIf(existing -> existing.equals(key) || existing.startsWith(key + "."));
			}
		});
		changes.forEach((key, value) -> {
			if (value != null) {
				values.put(key, value);
			}
		});
		return AssetForm.withFormBlanks(opened.schema(), values, opened.hasTextFile() || AssetEditing.hasTextValues(opened.schema(), values));
	}

	/** No file this writes is reached through a link, so nothing lands outside the mod. */
	private static void refuseLinks(ModEntry mod, Path target, WizardQuestion asked) throws Refused, IOException {
		Path at = mod.root();
		Path expected = mod.root().toRealPath();
		for (Path part : mod.root().relativize(target.normalize())) {
			at = at.resolve(part);
			expected = expected.resolve(part);
			if (Files.exists(at, LinkOption.NOFOLLOW_LINKS) && !(Files.exists(at) && at.toRealPath().equals(expected))) {
				String message = at + " is a link to somewhere else, so nothing was written through it.";
				throw new Refused(message, asked.refusedWith(message, List.of()));
			}
		}
	}

	// ---- Questions

	private WizardQuestion nameQuestion(ModEntry mod, Wizard.Definition definition, String note, String refused) {
		Wizard.Step step = definition.steps().get(0);
		return new WizardQuestion(definition.id(), mod.folderName(), null, null, step.id(), "name", step.prompt(), step.help(), step.evidence(), 1, definition.steps().size(), note,
				List.of(), null, List.of(), List.of(), List.of(), refused, false, false, List.of(chosen(definition)));
	}

	private WizardQuestion question(State state, String note) {
		Position position = current(state);
		Wizard.Step step = position.step();
		WizardStore.Session session = state.session();
		Map<String, String> values = state.values();
		String display = session.display();
		List<String> notes = new ArrayList<>();
		if (note != null) {
			notes.add(note);
		}
		notes.addAll(position.passed());
		List<WizardQuestion.ChoiceView> choices = new ArrayList<>();
		WizardQuestion.NumberView number = null;
		List<WizardQuestion.FieldView> fields = new ArrayList<>();
		String kind;
		switch (step) {
		case Wizard.NameStep name -> kind = "name";
		case Wizard.LookStep look -> {
			kind = "look";
			for (String id : AssetForm.appearances(state.schema(), state.context().index())) {
				choices.add(new WizardQuestion.ChoiceView(id, lookLabel(state.schema(), state.context(), id), false));
			}
		}
		case Wizard.ChoiceStep choice -> {
			kind = "choice";
			for (int i = 0; i < choice.choices().size(); i++) {
				Wizard.Choice option = choice.choices().get(i);
				choices.add(new WizardQuestion.ChoiceView(String.valueOf(i), Phrase.fill(option.label(), values, display), matches(option.changes(), values)));
			}
		}
		case Wizard.NumberStep asked -> {
			kind = "number";
			number = new WizardQuestion.NumberView(asked.converter().read(values, new WizardFacts(GameInstall.jarIn(game))), asked.unit(), asked.min(), asked.max(), asked.whole());
		}
		case Wizard.FieldsStep asked -> {
			kind = "fields";
			for (Wizard.Field field : asked.fields()) {
				fields.add(fieldView(state, field));
			}
		}
		case Wizard.DoneStep done -> kind = "done";
		}
		// A question carries no findings, by the user's decision on 2026-11-13: the wizard is meant to be streamlined,
		// what a race needs is asked for, and only an answer that can't be saved says what's wrong with it.
		List<WizardQuestion.Problem> problems = List.of();
		boolean canGoBack = step instanceof Wizard.LookStep || hasAnswerAfterLook(state.definition(), session);
		boolean canSkip = step instanceof Wizard.ChoiceStep || step instanceof Wizard.NumberStep || step instanceof Wizard.FieldsStep;
		return new WizardQuestion(state.definition().id(), state.mod().folderName(), session.stem(), display, step.id(), kind, Phrase.fill(step.prompt(), values, display),
				Phrase.fill(step.help(), values, display), step.evidence(), position.index() + 1, state.definition().steps().size(), notes.isEmpty() ? null : String.join(" ", notes),
				choices, number, fields, handbook(state, step), problems, null, canGoBack, canSkip, history(state));
	}

	private static boolean hasAnswerAfterLook(Wizard.Definition definition, WizardStore.Session session) {
		int look = lookIndex(definition);
		return session.answered().stream().anyMatch(id -> definition.indexOf(id) > look);
	}

	private static boolean matches(Map<String, String> changes, Map<String, String> values) {
		for (Map.Entry<String, String> change : changes.entrySet()) {
			String now = values.get(change.getKey());
			if (change.getValue() == null ? now != null : !change.getValue().equals(now)) {
				return false;
			}
		}
		return !changes.isEmpty();
	}

	/** What the game's own text calls a look, such as Human for HUMAN, or the look's name if it has none. */
	private static String lookLabel(AssetSchema schema, Workspace.Context context, String look) {
		String name = AssetEditing.sources(schema, context.index(), look).values().get("NAME");
		return name == null || name.isBlank() ? SpriteCopy.cased(look) : name.strip();
	}

	private WizardQuestion.FieldView fieldView(State state, Wizard.Field field) {
		AssetSchema schema = state.schema();
		TagSpec tag = schema.find(field.key());
		if (tag == null) {
			throw new IllegalStateException("The wizard names a tag the form doesn't have: " + field.key());
		}
		Map<String, String> values = state.values();
		String display = state.session().display();
		// A number inside each named block, such as each crime's FREEDOM, is asked like a map, with that number's own range.
		TagSpec block = null;
		if (field.column() != null) {
			block = tag;
			tag = block.shape().children().stream().filter(child -> child.key().equals(field.column())).findFirst()
					.orElseThrow(() -> new IllegalStateException("The wizard names a column " + field.key() + " doesn't have: " + field.column()));
		}
		List<String> options = field.options() != null ? state.context().index().options(field.options()) : AssetForm.options(block != null ? block : tag, schema, state.context().index());
		String input = block != null ? "amounts" : inputFor(tag, options);
		if (!input.equals("picks") && !input.equals("amounts") && !input.equals("one")) {
			options = null;
		}
		String value = block != null ? null : values.get(field.key());
		Map<String, String> amounts = new LinkedHashMap<>();
		if (input.equals("amounts")) {
			String end = field.column() == null ? "" : "." + field.column();
			values.forEach((key, amount) -> {
				if (key.startsWith(field.key() + ".") && key.endsWith(end)) {
					String name = key.substring(field.key().length() + 1, key.length() - end.length());
					if (!name.isEmpty() && !name.contains(".")) {
						amounts.put(name, amount);
					}
				}
			});
		}
		boolean present = value != null || !amounts.isEmpty();
		String state0;
		String defaultText = tag.gameDefault();
		String keptAsWritten = null;
		if (state.kept().contains(field.key())) {
			state0 = "kept";
			keptAsWritten = state.opened().keptAsWritten().get(field.key());
		} else if (present) {
			state0 = "set";
		} else if (tag.ghosted()) {
			state0 = "ghosted";
		} else {
			// A tag needed inside an optional block that's absent, such as POPULATION.MAX: the block's default stands for it.
			TagSpec parent = parent(schema, field.key());
			boolean parentAbsent = parent != null && values.keySet().stream().noneMatch(key -> key.startsWith(parent.key() + "."));
			if (tag.required() && parentAbsent && !parent.required() && parent.gameDefault() != null) {
				state0 = "ghosted";
				defaultText = parent.gameDefault();
			} else {
				state0 = tag.required() ? "required" : "add";
			}
		}
		return new WizardQuestion.FieldView(field.key(), Phrase.fill(field.label(), values, display), Phrase.fill(field.help(), values, display), input, state0, value, amounts, defaultText,
				options, tag.rangeText(), tag.required(), schema.ruleText(tag), block != null ? block.evidence() + " " + tag.evidence() : tag.evidence(), keptAsWritten, tag.min(), tag.max(),
				tag.maxEnforced(), field.column());
	}

	private static TagSpec parent(AssetSchema schema, String key) {
		int dot = key.lastIndexOf('.');
		return dot < 0 ? null : schema.find(key.substring(0, dot));
	}

	/** How a tag is answered. Only the kinds a wizard can ask are here, and a definition naming another fails its test. */
	static String inputFor(TagSpec tag, List<String> options) {
		return switch (tag.kind()) {
		case INT -> "whole_number";
		case DECIMAL -> "number";
		case BOOL -> "yes_no";
		// A plain value that names a file, such as the tourists' reviews, is one of those files.
		case PLAIN -> options != null && !options.isEmpty() ? "one" : "text";
		case TEXT_LINE -> "text";
		case TEXT_LINES -> "text_lines";
		case TEXT_ROWS -> "rows";
		case KEY_LIST -> "picks";
		case DECIMAL_MAP -> "amounts";
		default -> throw new IllegalStateException("A wizard can't ask for a " + tag.kind() + " tag: " + tag.key());
		};
	}

	/** The handbook's words for each tag a step works on, labelled as the step labels it. */
	private List<WizardQuestion.HandbookEntry> handbook(State state, Wizard.Step step) {
		HandbookIndex index = state.context().handbook().index();
		AssetSchema schema = state.schema();
		Map<String, String> values = state.values();
		String display = state.session() == null ? null : state.session().display();
		List<WizardQuestion.HandbookEntry> entries = new ArrayList<>();
		for (String key : step.keys()) {
			String label = Phrase.fill(step.prompt(), values, display);
			if (step instanceof Wizard.FieldsStep fields) {
				for (Wizard.Field field : fields.fields()) {
					if (field.key().equals(key)) {
						label = Phrase.fill(field.label(), values, display);
					}
				}
			}
			AssetSchema.Companion companion = schema.companionFor(key);
			List<String> locations = companion == null ? state.entry().type().handbookLocations() : companion.handbookLocations();
			HandbookIndex.TagHelp help = AssetForm.help(index, schema, locations, schema.isTextKey(key), key);
			String description = help != null ? help.description() : index == null ? AssetForm.NO_HANDBOOK : AssetForm.withoutHelp(key);
			entries.add(new WizardQuestion.HandbookEntry(label, schema.handbookKey(key), description, help == null ? null : help.example(), help == null ? null : help.url(),
					help != null && help.unverified()));
		}
		return entries;
	}

	/** Picking the wizard is the conversation's first answer. */
	private static WizardQuestion.Exchange chosen(Wizard.Definition definition) {
		return new WizardQuestion.Exchange("start", Wizards.START_PROMPT, definition.title(), List.of());
	}

	private List<WizardQuestion.Exchange> history(State state) {
		List<WizardQuestion.Exchange> history = new ArrayList<>();
		history.add(chosen(state.definition()));
		Wizard.Definition definition = state.definition();
		WizardStore.Session session = state.session();
		Map<String, String> values = state.values();
		for (String id : session.answered()) {
			Wizard.Step step = definition.step(id);
			if (step == null || id.equals(session.step())) {
				continue;
			}
			history.add(new WizardQuestion.Exchange(id, Phrase.fill(step.prompt(), values, session.display()), summary(state, step), handbook(state, step)));
		}
		return history;
	}

	/** An answer as the files hold it now, in a line or two. */
	private String summary(State state, Wizard.Step step) {
		Map<String, String> values = state.values();
		WizardStore.Session session = state.session();
		return switch (step) {
		case Wizard.NameStep name -> session.display();
		case Wizard.LookStep look -> session.look() == null ? "" : lookLabel(state.schema(), state.context(), session.look());
		case Wizard.ChoiceStep choice -> choice.choices().stream().filter(option -> matches(option.changes(), values)).map(option -> Phrase.fill(option.label(), values, session.display()))
				.findFirst().orElse("Something else, set in the editor");
		case Wizard.NumberStep number -> {
			String value = number.converter().read(values, new WizardFacts(GameInstall.jarIn(game)));
			yield value == null ? "Not known" : value + " " + number.unit();
		}
		case Wizard.FieldsStep fields -> {
			List<String> lines = new ArrayList<>();
			for (Wizard.Field field : fields.fields()) {
				WizardQuestion.FieldView view = fieldView(state, field);
				lines.add(view.label() + ": " + shown(view));
			}
			yield String.join("\n", lines);
		}
		case Wizard.DoneStep done -> "";
		};
	}

	private static String shown(WizardQuestion.FieldView view) {
		return switch (view.state()) {
		case "kept" -> view.keptAsWritten() == null ? "as written" : view.keptAsWritten();
		case "ghosted" -> "the game's default (" + view.defaultText() + ")";
		case "add", "required" -> "not set";
		default -> {
			if (view.input().equals("amounts")) {
				List<String> parts = new ArrayList<>();
				view.amounts().forEach((name, amount) -> parts.add(name + " " + amount));
				String all = String.join(", ", parts);
				yield all.length() > 90 ? all.substring(0, 87) + "..." : all;
			}
			if (view.input().equals("yes_no")) {
				yield "true".equals(view.value()) ? "Yes" : "No";
			}
			String value = view.value() == null ? "" : view.value().replace("\n", ", ").strip();
			yield value.length() > 90 ? value.substring(0, 87) + "..." : value;
		}
		};
	}

	/**
	 * What's worth saying with a question: findings about its own tags, and anything blocking a save, since the next
	 * answer couldn't be saved until that's fixed.
	 */
	private static List<WizardQuestion.Problem> problems(List<AssetChecker.Finding> findings, List<String> keys, boolean includeBlocking) {
		List<WizardQuestion.Problem> problems = new ArrayList<>();
		for (AssetChecker.Finding finding : findings) {
			String tag = finding.tag();
			boolean ours = tag != null && keys.stream().anyMatch(key -> tag.equals(key) || tag.startsWith(key + ".") || key.startsWith(tag + "."));
			if (ours || includeBlocking && finding.blocksSaving()) {
				problems.add(new WizardQuestion.Problem(tag, finding.severity().name().toLowerCase(Locale.ROOT), finding.blocksSaving(), finding.message()));
			}
		}
		return problems;
	}

	private static String plain(double value) {
		return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
	}
}
