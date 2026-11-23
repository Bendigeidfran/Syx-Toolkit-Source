package syx.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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
import java.util.zip.ZipFile;

import javax.swing.SwingUtilities;

import syx.cli.Main;
import syx.core.AssetCatalog;
import syx.core.AssetCreator;
import syx.core.AssetEditing;
import syx.core.AssetForm;
import syx.core.GameAssetIndex;
import syx.core.GameInstall;
import syx.core.GameParser;
import syx.core.HandbookStore;
import syx.core.JsonText;
import syx.core.ModCreator;
import syx.core.ModEntry;
import syx.core.Operations;
import syx.core.ToolkitSettings;
import syx.gui.AddAssetScreen;
import syx.gui.AssetEditorScreen;
import syx.gui.Theme;
import syx.mcp.Server;

/**
 * Every front end must give identical results. Each available type comes from the catalog, so one that's
 * enabled later is held to this without being added here. Every vanilla asset goes through the real form
 * and the operations, and the first of each type through the command line and MCP server too, which only
 * pass calls on. What's compared: findings, callouts, the summary, whether saving is allowed, and the
 * bytes of what's written.
 */
public final class ParityTest {

	private static int passed;
	private static int failed;
	private static Path game;
	private static Path mods;
	private static Path settingsFile;
	private static GameParser parser;
	private static GameParser.GameVersion version;
	private static Operations operations;
	private static AssetEditorScreen screen;

	private ParityTest() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			System.out.println("SKIPPED: no game folder given.");
			System.out.println("passed=0 failed=0");
			return;
		}
		game = Paths.get(args[0]);
		Path temp = Files.createTempDirectory("syx-parity");
		mods = temp.resolve("mods");
		Files.createDirectories(mods);
		settingsFile = temp.resolve("settings.properties");
		System.setProperty(ToolkitSettings.FILE_OVERRIDE_PROPERTY, settingsFile.toString());
		ToolkitSettings settings = new ToolkitSettings(settingsFile);
		operations = new Operations(() -> game, mods, settings, new HandbookStore(temp.resolve("handbook"), etag -> new HandbookStore.Response(404, null, new byte[0])), false);
		SwingUtilities.invokeAndWait(() -> {
			Theme.install(ToolkitSettings.ThemeChoice.DARK);
			screen = new AssetEditorScreen(() -> game, url -> { }, () -> { });
		});
		try (GameParser opened = GameParser.open(GameInstall.jarIn(game))) {
			parser = opened;
			version = parser.version();
			List<AssetCatalog.Entry> available = AssetCatalog.load(GameInstall.jarIn(game)).stream().filter(AssetCatalog.Entry::available).toList();
			expect("there are types to test", !available.isEmpty());
			ModEntry form = ModCreator.create(mods, game, "Through Form");
			ModEntry agent = ModCreator.create(mods, game, "Through Agent");
			ModEntry cli = ModCreator.create(mods, game, "Through Cli");
			ModEntry mcp = ModCreator.create(mods, game, "Through Mcp");
			for (AssetCatalog.Entry entry : available) {
				newAsset(entry, form, agent, cli, mcp);
				existing(entry, form, agent, cli, mcp);
			}
			blankRows(available, form, agent);
		} finally {
			deleteFolder(temp);
		}
		System.out.println("passed=" + passed + " failed=" + failed);
		System.exit(failed == 0 ? 0 : 1);
	}

	/** Vanilla's files for a type. Types can share a folder, so a file goes to the type with the longest prefix that fits. */
	private static List<String> vanillaFiles(AssetCatalog.Entry entry) throws IOException {
		String start = "data/assets/init/" + entry.type().initFolder() + "/";
		String prefix = entry.type().filePrefix();
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile())) {
			return zip.stream().map(java.util.zip.ZipEntry::getName).filter(name -> name.startsWith(start) && name.endsWith(".txt") && name.indexOf('/', start.length()) < 0)
					.map(name -> name.substring(start.length(), name.length() - 4)).filter(stem -> entry.type().ownsFile(stem)).sorted().toList();
		}
	}

	private static byte[] vanilla(String relative) throws IOException {
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile()); InputStream in = zip.getInputStream(zip.getEntry("data/assets/" + relative))) {
			return in == null ? null : in.readAllBytes();
		}
	}

	private static boolean vanillaHas(String relative) throws IOException {
		try (ZipFile zip = new ZipFile(game.resolve("base").resolve("data.zip").toFile())) {
			return zip.getEntry("data/assets/" + relative) != null;
		}
	}

	private static Path assetsOf(ModEntry mod) {
		return AssetCreator.versionFolder(mod.root(), version).resolve("assets");
	}

	private static GameAssetIndex indexOf(ModEntry mod) throws IOException {
		return GameAssetIndex.load(game, AssetCreator.versionFolder(mod.root(), version), parser);
	}

	/** A copy under a new name in every mod. A crop gets a resource of its own, because two crops can't yield one. */
	private static String copy(AssetCatalog.Entry entry, String stem, int number, ModEntry... targets) throws IOException {
		String prefix = entry.type().filePrefix();
		// An override can only have the game's own name, so its copy is the game's file under that name, replacing any there.
		String name = entry.type().isOverride() ? entry.type().fixedName() : "COPY" + number;
		String file = prefix + name;
		String data = new String(vanilla("init/" + entry.type().initFolder() + "/" + stem + ".txt"), StandardCharsets.UTF_8);
		for (ModEntry mod : targets) {
			Path assets = assetsOf(mod);
			String own = data;
			if (entry.type().initFolder().equals("resource/growable")) {
				String resource = "PARITYRES" + number;
				Files.createDirectories(assets.resolve("init/resource"));
				Files.writeString(assets.resolve("init/resource/" + resource + ".txt"), "");
				own = data.replaceFirst("RESOURCE:\\s*[A-Za-z0-9_]+,", "RESOURCE: " + resource + ",");
			}
			Path target = assets.resolve("init/" + entry.type().initFolder() + "/" + file + ".txt");
			Files.createDirectories(target.getParent());
			Files.write(target, own.getBytes(StandardCharsets.UTF_8));
			String textFolder = entry.type().textFolder();
			if (textFolder != null && vanillaHas("text/" + textFolder + "/" + stem + ".txt")) {
				Path text = assets.resolve("text/" + textFolder + "/" + file + ".txt");
				Files.createDirectories(text.getParent());
				Files.write(text, vanilla("text/" + textFolder + "/" + stem + ".txt"));
			}
		}
		return name;
	}

	private record FormState(List<String> findingsKey, List<String> callouts, String summary, boolean canSave, Map<String, String> values) {
	}

	private static FormState formState() {
		// The notes also hold the footer's text, which isn't part of what's checked. The screen's own list is compared by membership below.
		String summary = screen.messageText();
		return new FormState(List.of(), new ArrayList<>(screen.noteTexts()), summary == null ? "" : summary.trim(), screen.isSaveEnabled(), screen.currentValues());
	}

	@SuppressWarnings("unchecked")
	private static void compare(String what, FormState form, Map<String, Object> check) {
		expect(what + ": the same summary: [" + form.summary() + "] and [" + check.get("summary") + "]", form.summary().equals(String.valueOf(check.get("summary")).trim()));
		expect(what + ": the same answer on saving: " + form.canSave() + " and " + check.get("can_save") + " " + check.get("findings"), form.canSave() == Boolean.TRUE.equals(check.get("can_save")));
		List<String> callouts = (List<String>) check.get("callouts");
		expect(what + ": the form shows every callout: " + callouts, form.callouts().containsAll(callouts));
		List<String> extra = new ArrayList<>(form.callouts());
		extra.removeAll(callouts);
		extra.removeAll(screenFooter());
		expect(what + ": and no others: " + extra, extra.isEmpty());
	}

	private static List<String> footer = List.of();

	private static List<String> screenFooter() {
		return footer;
	}

	private static List<String> footerOf(AssetCatalog.Entry entry) {
		List<String> texts = new ArrayList<>(entry.schema().notes());
		texts.add("Tag descriptions come from the online handbook, and no copy for your game version has been downloaded yet. The toolkit checks for one each time it starts.");
		return texts;
	}

	private static Map<String, String> withoutBlanks(Map<String, String> values) {
		Map<String, String> kept = new TreeMap<>(values);
		kept.values().removeIf(String::isEmpty);
		return kept;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> ok(String name, Map<String, Object> input) {
		Operations.Result result = operations.call(name, input);
		if (result.outcome() != Operations.Outcome.OK) {
			throw new IllegalStateException(name + " " + result.payload());
		}
		return result.payload();
	}

	private static Map<String, Object> input(Object... pairs) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], pairs[i + 1]);
		}
		return map;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> check(Map<String, Object> form) {
		return (Map<String, Object>) form.get("check");
	}

	private static void waitForSave() throws Exception {
		long until = System.currentTimeMillis() + 60000;
		boolean[] busy = { true };
		while (busy[0] && System.currentTimeMillis() < until) {
			SwingUtilities.invokeAndWait(() -> busy[0] = screen.isBusy());
			if (busy[0]) {
				Thread.sleep(10);
			}
		}
		SwingUtilities.invokeAndWait(() -> { });
	}

	/** Every file under a folder, by relative path, so two mods' results can be compared whole. */
	private static Map<String, String> tree(Path folder) throws IOException {
		Map<String, String> files = new TreeMap<>();
		if (Files.isDirectory(folder)) {
			try (Stream<Path> walk = Files.walk(folder)) {
				for (Path path : walk.filter(Files::isRegularFile).toList()) {
					files.put(folder.relativize(path).toString().replace('\\', '/'), new String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1));
				}
			}
		}
		return files;
	}

	private static String difference(Map<String, String> one, Map<String, String> two) {
		List<String> differs = new ArrayList<>();
		one.forEach((key, value) -> {
			if (!value.equals(two.get(key))) {
				differs.add(key);
			}
		});
		two.keySet().stream().filter(key -> !one.containsKey(key)).forEach(differs::add);
		return differs.toString();
	}

	/** Only files with this in their name, for a mod that didn't get everything. */
	private static void sameFiles(String what, ModEntry one, ModEntry two, String named) throws IOException {
		Map<String, String> first = tree(assetsOf(one));
		Map<String, String> second = tree(assetsOf(two));
		first.keySet().removeIf(key -> !key.contains(named));
		second.keySet().removeIf(key -> !key.contains(named));
		expect(what + ": there are files to compare", !first.isEmpty());
		expect(what + ": the same files with the same bytes, as differ: " + difference(first, second), first.equals(second));
	}

	private static void sameFiles(String what, ModEntry one, ModEntry two) throws IOException {
		Map<String, String> first = tree(assetsOf(one));
		Map<String, String> second = tree(assetsOf(two));
		expect(what + ": the same files with the same bytes, as differ: " + difference(first, second), first.equals(second));
	}

	/** A new asset: the form's starting state, a deliberately bad value, then the save. */
	private static void newAsset(AssetCatalog.Entry entry, ModEntry form, ModEntry agent, ModEntry cli, ModEntry mcp) throws Exception {
		String label = entry.type().label();
		String name = entry.type().isOverride() ? entry.type().fixedName() : "PARITY_NEW";
		List<String> files = vanillaFiles(entry);
		expect(label + " has vanilla files to test against", !files.isEmpty());
		footer = footerOf(entry);

		GameAssetIndex index = indexOf(form);
		HandbookStore.Loaded none = HandbookStore.Loaded.NONE;
		syx.core.ResourceProducers producers = syx.core.ResourceProducers.load(game, AssetCreator.versionFolder(form.root(), version), parser);
		String appearance = entry.schema().appearanceFolder() == null ? null : files.get(0);
		SwingUtilities.invokeAndWait(() -> screen.open(form, new AddAssetScreen.Choice(entry, name, index, version, none, producers, appearance)));
		Map<String, Object> formArgs = input("mod", agent.folderName(), "type", label, "name", name);
		if (appearance != null) {
			formArgs.put("appearance", appearance);
		}
		Map<String, Object> fresh = ok("new_asset_form", formArgs);
		// A level with no frames is a real starting value, and the form holds it as a blank.
		@SuppressWarnings("unchecked")
		Map<String, String> starting = withoutBlanks((Map<String, String>) fresh.get("starting_values"));
		Map<String, String> shown = withoutBlanks(screen.currentValues());
		String firstDifference = "";
		for (String key : starting.keySet()) {
			if (!starting.get(key).equals(shown.get(key))) {
				firstDifference = " first " + key + " form=" + shown.get(key) + " agent=" + starting.get(key);
				break;
			}
		}
		if (firstDifference.isEmpty()) {
			for (String key : shown.keySet()) {
				if (!shown.get(key).equals(starting.get(key))) {
					firstDifference = " first " + key + " form=" + shown.get(key) + " agent=" + starting.get(key);
					break;
				}
			}
		}
		expect(label + ": the form starts with the same values: " + difference(shown, starting) + firstDifference, shown.equals(starting));
		compare(label + " new, untouched", formState(), noHandbookNote(check(fresh)));

		// Fill both from a vanilla copy's values, opened through the operations.
		// A race's starting values already include the files it names. A copy of only the race file doesn't.
		Map<String, String> sourceValues;
		if (entry.schema().companions().isEmpty() && !entry.type().isOverride()) {
			// A monument has as many items as its file gives, and a new one starts with the sculpture's two. Values from
			// a monument with fewer would leave the second unnamed, which is rightly refused, so it's filled from that file.
			String from = "Monument".equals(label) ? "MONUMENT_SCULPTURE" : files.get(0);
			String source = copy(entry, from, 9000, agent);
			Map<String, Object> opened = ok("open_asset", input("mod", agent.folderName(), "type", label, "name", source));
			@SuppressWarnings("unchecked")
			Map<String, String> copied = new LinkedHashMap<>((Map<String, String>) opened.get("values"));
			sourceValues = copied;
			deleteAsset(entry, agent, source);
		} else {
			// An override already starts with every value of the game's file, which is what a copy would give.
			sourceValues = new LinkedHashMap<>(starting);
		}
		// A crop must name a resource no other crop yields, and a kept form has no value to take.
		if (entry.type().initFolder().equals("resource/growable")) {
			for (ModEntry mod : List.of(form, agent, cli, mcp)) {
				Files.createDirectories(assetsOf(mod).resolve("init/resource"));
				Files.writeString(assetsOf(mod).resolve("init/resource/PARITYNEWRES.txt"), "");
			}
			sourceValues.put("RESOURCE", "PARITYNEWRES");
		}
		fillRequiredKept(entry, sourceValues);
		// A sound with no starting value has to be chosen on both sides.
		for (syx.core.SoundEntries.Slot slot : entry.schema().sounds()) {
			String start = AssetForm.soundStart(slot, indexOf(form));
			sourceValues.putIfAbsent(slot.valueKey(), start == null ? syx.core.SoundEntries.NO_SOUND : start);
		}
		GameAssetIndex filledIndex = indexOf(form);
		SwingUtilities.invokeAndWait(() -> {
			screen.open(form, new AddAssetScreen.Choice(entry, name, filledIndex, version, none, producers, appearance));
			setAll(entry, sourceValues);
		});
		Map<String, String> values = screen.currentValues();
		expect(label + ": the form takes every value: " + difference(withoutBlanks(sourceValues), withoutBlanks(values)), withoutBlanks(values).entrySet().containsAll(withoutBlanks(sourceValues).entrySet()));
		Map<String, Object> filled = ok("check_asset", withLook(input("mod", agent.folderName(), "type", label, "name", name, "values", new LinkedHashMap<String, Object>(values)), appearance));
		compare(label + " new, filled in", formState(), filled);

		// A value the game refuses.
		String badKey = firstBadKey(entry);
		if (badKey != null) {
			String old = values.get(badKey);
			SwingUtilities.invokeAndWait(() -> screen.setValue(badKey, "300_2_3"));
			Map<String, Object> bad = new LinkedHashMap<>(values);
			bad.put(badKey, "300_2_3");
			Map<String, Object> badCheck = ok("check_asset", withLook(input("mod", agent.folderName(), "type", label, "name", name, "values", bad), appearance));
			compare(label + " new, with a bad value", formState(), badCheck);
			expect(label + ": a bad value stops both", !screen.isSaveEnabled() && Boolean.FALSE.equals(badCheck.get("can_save")));
			Operations.Result refused = operations.call("create_asset", withLook(input("mod", agent.folderName(), "type", label, "name", name, "values", bad), appearance));
			expect(label + ": creating with it is refused, and says why: " + refused.payload().get("refused"), refused.outcome() == Operations.Outcome.REFUSED && refused.payload().get("refused") != null);
			SwingUtilities.invokeAndWait(() -> screen.setValue(badKey, old));
		}

		boolean canSave = screen.isSaveEnabled();
		expect(label + ": a filled-in form can be saved: " + screen.messageText(), canSave);
		SwingUtilities.invokeAndWait(screen::save);
		waitForSave();
		Map<String, Object> asObjects = new LinkedHashMap<>(values);
		if (appearance != null) {
			Operations.Result noLook = operations.call("create_asset", input("mod", agent.folderName(), "type", label, "name", name, "values", asObjects));
			expect(label + ": creating without a look is refused, because the files are copies of it", noLook.outcome() == Operations.Outcome.REFUSED);
		}
		ok("create_asset", withLook(input("mod", agent.folderName(), "type", label, "name", name, "values", asObjects), appearance));
		sameFiles(label + " new, through the operations", form, agent);

		cliCall(cli, "create_asset", withLook(input("type", label, "name", name, "values", asObjects), appearance), 0);
		sameFiles(label + " new, through the command line", form, cli, name);
		mcpCall(mcp, "create_asset", withLook(input("mod", mcp.folderName(), "type", label, "name", name, "values", asObjects), appearance), false);
		sameFiles(label + " new, through MCP", form, mcp, name);
	}

	/** A new asset of a type with looks names the one it started from, as the form does. */
	private static Map<String, Object> withLook(Map<String, Object> args, String appearance) {
		if (appearance != null) {
			args.put("appearance", appearance);
		}
		return args;
	}

	/**
	 * A file can lack a required text tag. The form holds a blank for it, which the game accepts, so the
	 * agent's answer must be the same, and an unrelated edit mustn't write the blank in.
	 */
	@SuppressWarnings("unchecked")
	private static void blankRows(List<AssetCatalog.Entry> available, ModEntry form, ModEntry agent) throws Exception {
		AssetCatalog.Entry entry = available.stream().filter(candidate -> candidate.type().initFolder().equals("animal")).findFirst().orElseThrow();
		footer = footerOf(entry);
		String label = entry.type().label();
		String stem = vanillaFiles(entry).get(0);
		String name = copy(entry, stem, 8000, form, agent);
		for (ModEntry mod : List.of(form, agent)) {
			Files.writeString(assetsOf(mod).resolve("text/" + entry.type().textFolder() + "/" + entry.type().fileName(name) + ".txt"), "INFO: {\n\tNAME: \"Only A Name\",\n},\n");
		}
		Path file = assetsOf(form).resolve("init/" + entry.type().initFolder() + "/" + entry.type().fileName(name) + ".txt");
		GameAssetIndex index = indexOf(form);
		AssetEditing.Opened opened = AssetEditing.load(AssetEditing.locate(form.root(), version, file, List.of(entry), index), parser, index);
		HandbookStore.Loaded none = HandbookStore.Loaded.NONE;
		SwingUtilities.invokeAndWait(() -> screen.openExisting(form, new AddAssetScreen.Choice(entry, name, index.editing(entry.type(), opened.fileName()), version, none, null), opened));
		Map<String, Object> agentForm = ok("open_asset", input("mod", agent.folderName(), "type", label, "name", name));
		compare(label + " missing a text tag", formState(), check(agentForm));
		Map<String, Object> saved = ok("save_asset", input("mod", agent.folderName(), "type", label, "name", name, "revision", agentForm.get("revision"), "changes", input("MASS", "123")));
		expect("an unrelated edit leaves the text file alone: " + saved.get("written"), ((List<String>) saved.get("written")).size() == 1 && !((List<String>) saved.get("written")).get(0).contains("/text/"));
	}

	/** An icon the form can't show has no value to copy, and a new form needs one, so a plain value stands in on both sides. */
	private static void fillRequiredKept(AssetCatalog.Entry entry, Map<String, String> values) {
		for (syx.core.TagSpec tag : entry.schema().dataTags()) {
			if (tag.kind() == syx.core.TagSpec.Kind.ICON && tag.required() && values.keySet().stream().noneMatch(key -> key.equals(tag.key()) || key.startsWith(tag.key() + "."))) {
				values.put(tag.key(), "24->resource->Gem->0");
			}
		}
	}

	private static void setAll(AssetCatalog.Entry entry, Map<String, String> values) {
		values.forEach((key, value) -> {
			String top = key.contains(".") ? key.substring(0, key.indexOf('.')) : key;
			if (key.startsWith("SOUND.")) {
				screen.setValue(key, value);
				return;
			}
			syx.core.TagSpec tag = entry.schema().tag(top);
			String id = top;
			if (entry.schema().isTextKey(key) && entry.schema().dataTags().stream().anyMatch(other -> other.key().equals(top))) {
				id = top + " (text file)";
			} else if (tag != null && tag.linked()) {
				id = tag.linkedTo();
			}
			if (screen.addableKeys().contains(id) || screen.ghostKeys().contains(id)) {
				screen.addTag(id);
			}
			screen.setValue(key, value);
		});
	}

	/** The first colour on a form, because every type's rule about colours is the same and the game refuses this one. */
	private static String firstBadKey(AssetCatalog.Entry entry) {
		for (syx.core.TagSpec tag : entry.schema().dataTags()) {
			if (tag.kind() == syx.core.TagSpec.Kind.COLOR && tag.required()) {
				return tag.key();
			}
		}
		return null;
	}

	private static Map<String, Object> noHandbookNote(Map<String, Object> check) {
		return check;
	}

	private static void deleteAsset(AssetCatalog.Entry entry, ModEntry mod, String name) throws IOException {
		String file = entry.type().fileName(name) + ".txt";
		Files.deleteIfExists(assetsOf(mod).resolve("init/" + entry.type().initFolder() + "/" + file));
		if (entry.type().textFolder() != null) {
			Files.deleteIfExists(assetsOf(mod).resolve("text/" + entry.type().textFolder() + "/" + file));
		}
		Files.deleteIfExists(assetsOf(mod).resolve("init/resource/PARITYRES9000.txt"));
	}

	/** Every vanilla asset, copied: opening, a changed value, a bad value, and the save. */
	@SuppressWarnings("unchecked")
	private static void existing(AssetCatalog.Entry entry, ModEntry form, ModEntry agent, ModEntry cli, ModEntry mcp) throws Exception {
		String label = entry.type().label();
		footer = footerOf(entry);
		int number = 0;
		for (String stem : vanillaFiles(entry)) {
			// Those two only pass calls on, so the first of each type goes through them.
			number++;
			String name = number == 1 ? copy(entry, stem, number, form, agent, cli, mcp) : copy(entry, stem, number, form, agent);
			String what = label + " " + stem;
			Path file = assetsOf(form).resolve("init/" + entry.type().initFolder() + "/" + entry.type().fileName(name) + ".txt");
			GameAssetIndex index = indexOf(form);
			// A torch is a monument the game reads with other code. The editor and the agent both refuse it, and say why.
			String outOfScope = entry.schema().outOfScopeReason(syx.core.GameTree.read(parser.root(file)));
			if (outOfScope != null) {
				String screenProblem = AssetEditing.locate(form.root(), version, file, List.of(entry), index, parser).problem();
				Operations.Result agentOpen = operations.call("open_asset", input("mod", agent.folderName(), "type", label, "name", name));
				expect(what + " is refused on the screen, saying why: " + screenProblem, screenProblem != null && screenProblem.contains(outOfScope));
				expect(what + " is refused for the agent with the same reason: " + agentOpen.payload(),
						agentOpen.outcome() == Operations.Outcome.REFUSED && String.valueOf(agentOpen.payload().get("refused")).contains(outOfScope));
				continue;
			}
			AssetEditing.Location location = AssetEditing.locate(form.root(), version, file, List.of(entry), index);
			expect(what + " can be opened: " + location.problem(), location.problem() == null);
			AssetEditing.Opened opened = AssetEditing.load(location, parser, index);
			GameAssetIndex editing = index.editing(entry.type(), opened.fileName());
			HandbookStore.Loaded none = HandbookStore.Loaded.NONE;
			syx.core.ResourceProducers producers = syx.core.ResourceProducers.load(game, AssetCreator.versionFolder(form.root(), version), parser);
			SwingUtilities.invokeAndWait(() -> screen.openExisting(form, new AddAssetScreen.Choice(entry, name, editing, version, none, producers), opened));

			Map<String, Object> agentForm = ok("open_asset", input("mod", agent.folderName(), "type", label, "name", name));
			Map<String, String> agentValues = (Map<String, String>) agentForm.get("values");
			expect(what + ": the same values: " + difference(withoutBlanks(screen.currentValues()), withoutBlanks(agentValues)), withoutBlanks(screen.currentValues()).equals(withoutBlanks(agentValues)));
			expect(what + ": the same kept tags", new java.util.TreeSet<>(opened.kept()).equals(new java.util.TreeSet<>((List<String>) agentForm.get("kept"))));
			compare(what + " opened", formState(), check(agentForm));

			String revision = (String) agentForm.get("revision");
			String color = firstBadKey(entry);
			Map<String, Object> saved = ok("save_asset", input("mod", agent.folderName(), "type", label, "name", name, "revision", revision, "changes", new LinkedHashMap<String, Object>()));
			expect(what + ": saved unchanged, the agent writes nothing: " + saved.get("written"), ((List<?>) saved.get("written")).isEmpty());
			if ("Race".equals(label)) {
				expect(what + " still points at the game's files", opened.companions().values().stream().noneMatch(companion -> companion.place() == syx.core.AssetEditing.FilePlace.OWN));
				Map<String, Object> generated = (Map<String, Object>) agentForm.get("can_generate");
				expect(what + " offers the game's files to add", generated != null && generated.get("files") instanceof List<?> files && !files.isEmpty());
				expect(what + " wrote no companion file", !Files.exists(assetsOf(form).resolve("init/race/sprite/" + name + ".txt")));
			}

			if (color != null && !opened.kept().contains(color)) {
				SwingUtilities.invokeAndWait(() -> screen.setValue(color, "300_2_3"));
				Map<String, Object> bad = ok("check_asset", input("mod", agent.folderName(), "type", label, "name", name, "changes", input(color, "300_2_3")));
				compare(what + " with a bad value", formState(), bad);

				SwingUtilities.invokeAndWait(() -> screen.setValue(color, "1_2_3"));
				Map<String, Object> good = ok("check_asset", input("mod", agent.folderName(), "type", label, "name", name, "changes", input(color, "1_2_3")));
				compare(what + " with a good value", formState(), good);
				expect(what + " can be saved", screen.isSaveEnabled());
				SwingUtilities.invokeAndWait(screen::save);
				waitForSave();
				ok("save_asset", input("mod", agent.folderName(), "type", label, "name", name, "revision", revision, "changes", input(color, "1_2_3")));
				sameFiles(what + " saved, through the operations", form, agent);

				if (number == 1) {
					Map<String, Object> viaCli = cliCall(cli, "open_asset", input("type", label, "name", name), 0);
					cliCall(cli, "save_asset", input("type", label, "name", name, "revision", viaCli.get("revision"), "changes", input(color, "1_2_3")), 0);
					Map<String, Object> viaMcp = mcpCall(mcp, "open_asset", input("mod", mcp.folderName(), "type", label, "name", name), false);
					mcpCall(mcp, "save_asset", input("mod", mcp.folderName(), "type", label, "name", name, "revision", viaMcp.get("revision"), "changes", input(color, "1_2_3")), false);
				}
			}
			keepForComparison(entry, name, number, form, cli, mcp);
		}
	}

	private static void keepForComparison(AssetCatalog.Entry entry, String name, int number, ModEntry form, ModEntry cli, ModEntry mcp) throws IOException {
		if (number == 1) {
			String file = entry.type().fileName(name) + ".txt";
			for (ModEntry other : List.of(cli, mcp)) {
				Path mine = assetsOf(form).resolve("init/" + entry.type().initFolder() + "/" + file);
				Path theirs = assetsOf(other).resolve("init/" + entry.type().initFolder() + "/" + file);
				expect(entry.type().label() + ": the first copy is the same through " + other.folderName(), Arrays.equals(Files.readAllBytes(mine), Files.readAllBytes(theirs)));
			}
		}
	}

	/** Through the real command, in this process. Its --json output is what's returned. */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> cliCall(ModEntry mod, String command, Map<String, Object> input, int expectedExit) throws Exception {
		List<String> args = new ArrayList<>(List.of("--game", game.toString(), "--mods", mods.toString(), "--json", command, "--mod", mod.folderName()));
		for (Map.Entry<String, Object> entry : input.entrySet()) {
			args.add("--" + entry.getKey());
			args.add(entry.getValue() instanceof String text ? text : JsonText.write(entry.getValue()));
		}
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		int exit = Main.run(args.toArray(new String[0]), new PrintStream(bytes, true, StandardCharsets.UTF_8));
		String output = bytes.toString(StandardCharsets.UTF_8);
		expect(command + " through the command line exits with " + expectedExit + ": " + exit + " " + (exit == expectedExit ? "" : output), exit == expectedExit);
		return (Map<String, Object>) JsonText.parse(output);
	}

	/** Through the real server, in this process, on the newer revision of the protocol. */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> mcpCall(ModEntry mod, String tool, Map<String, Object> arguments, boolean expectError) throws Exception {
		Map<String, Object> meta = input("io.modelcontextprotocol/protocolVersion", "2026-07-28", "io.modelcontextprotocol/clientCapabilities", new LinkedHashMap<String, Object>());
		Map<String, Object> request = input("jsonrpc", "2.0", "id", 1.0, "method", "tools/call", "params", input("name", tool, "arguments", arguments, "_meta", meta));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new Server(operations, new ByteArrayInputStream((JsonText.write(request) + "\n").getBytes(StandardCharsets.UTF_8)), out).run();
		Map<String, Object> reply = (Map<String, Object>) JsonText.parse(out.toString(StandardCharsets.UTF_8).trim());
		Map<String, Object> result = (Map<String, Object>) reply.get("result");
		expect(tool + " through MCP has the expected outcome: " + (result == null ? reply : result.get("isError")), result != null && Boolean.valueOf(expectError).equals(result.get("isError")));
		List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
		return (Map<String, Object>) JsonText.parse((String) content.get(0).get("text"));
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

	private static void expect(String name, boolean ok) {
		if (ok) {
			passed++;
		} else {
			failed++;
			System.out.println("FAILED: " + name);
		}
	}
}
