package syx.test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import syx.core.AssetCreator;
import syx.core.GameFolderFinder;
import syx.core.GameInstall;
import syx.core.GameParser;
import syx.core.HandbookIndex;
import syx.core.HandbookStore;
import syx.core.ManifestStore;
import syx.core.ModCreator;
import syx.core.ModInfoField;
import syx.core.SyntaxChecker;
import syx.core.ToolkitSettings;
import syx.gui.AssetEditorScreen;
import syx.gui.MainScreen;
import syx.gui.Theme;
import syx.gui.WidthBand;

/**
 * Drives the real screens without a person, using a temp settings file and a temp mods folder, and
 * saves pictures. It doesn't cover the real folder picker dialog.
 * Usage: GuiSmoke <game folder> <folder to check> <output dir> [real mod to copy and view]
 */
public final class GuiSmoke {

	private static final List<Path> OPENED = new java.util.concurrent.CopyOnWriteArrayList<>();
	private static final List<String> OPENED_LINKS = new java.util.concurrent.CopyOnWriteArrayList<>();
	private static final boolean[] OPEN_FAILS = new boolean[1];
	private static final String[] HANDBOOK_VERSION = new String[1];
	private static HandbookStore handbookStore;
	private static ManifestStore manifestStore;
	private static String args0;
	private static int failed;

	private GuiSmoke() {
	}

	public static void main(String[] args) throws Exception {
		// An open window keeps Java running, so without this a failure here would hang rather than fail.
		Thread.currentThread().setUncaughtExceptionHandler((thread, problem) -> {
			problem.printStackTrace();
			System.out.println("GUI smoke FAILED: " + problem);
			System.exit(1);
		});
		Path game = Paths.get(args[0]);
		args0 = args[0];
		Path checkFolder = Paths.get(args[1]);
		Path outDir = Paths.get(args[2]);
		Files.createDirectories(outDir);
		Path temp = Files.createTempDirectory("syx-smoke");
		Path mods = temp.resolve("songsofsyx/mods");
		Path settingsFile = temp.resolve("settings.properties");
		// A stand-in for the site: nothing is downloaded until a test asks, and then only this fixture.
		handbookStore = new HandbookStore(temp.resolve("handbook"), etag -> {
			if (HANDBOOK_VERSION[0] == null) {
				throw new IOException("offline for this part of the test");
			}
			return new HandbookStore.Response(200, "\"" + HANDBOOK_VERSION[0] + "\"", handbookFixture(HANDBOOK_VERSION[0]).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		});
		GameParser.GameVersion gameVersion;
		try (GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
			gameVersion = parser.version();
		}
		// One Manifests folder in this test's own folder, as beside the .exe, holding a copy of what ships.
		Path shippedManifests = Paths.get("manifests");
		Path manifestFolder = temp.resolve("Manifests");
		Files.createDirectories(manifestFolder);
		if (Files.isDirectory(shippedManifests)) {
			try (java.util.stream.Stream<Path> files = Files.list(shippedManifests)) {
				for (Path file : files.toList()) {
					Files.copy(file, manifestFolder.resolve(file.getFileName()));
				}
			}
		}
		manifestStore = ManifestStore.in(manifestFolder, manifestFolder, temp.resolve("Diff Reports"));
		// The wizard's progress goes beside the .exe, which a test run doesn't have, so it goes in the test's own folder.
		System.setProperty(syx.core.wizard.WizardStore.FOLDER_PROPERTY, temp.resolve("Wizards").toString());

		SwingUtilities.invokeAndWait(() -> {
			Theme.install(ToolkitSettings.ThemeChoice.DARK);
		});
		syx.gui.WizardScreen.setAnimations(false);

		// First run: the automatic search finds the game.
		MainScreen screen = open(settingsFile, mods, new GameFolderFinder.Result(Optional.of(game), List.of(game)), Optional.empty());
		expect("first run asks for the game", screen.shownScreen().equals(MainScreen.FIND_GAME));
		expect("setup steps have no rail", !screen.isRailShowing());
		expect("setup steps name no mod", !screen.isTopBarChangeShowing());
		save(screen, outDir.resolve("1-find-game.png"));
		SwingUtilities.invokeAndWait(screen::checkForGameAutomatically);
		waitFor(() -> screen.shownScreen().equals(MainScreen.FOUND_GAME));
		expect("tells the user where it found the game: " + screen.foundMessageText(), screen.foundMessageText().contains(game.toString()));
		save(screen, outDir.resolve("2-found-game.png"));
		expect("the found folder is saved", new ToolkitSettings(settingsFile).gameFolder().equals(Optional.of(game)));
		SwingUtilities.invokeAndWait(screen::continueAfterFound);
		expect("then shows the options", screen.shownScreen().equals(MainScreen.START));
		save(screen, outDir.resolve("3-start.png"));

		// An empty mods folder offers to create one.
		SwingUtilities.invokeAndWait(screen::showChooseMod);
		waitFor(() -> !screen.chooseScreen().isLoading());
		expect("no mods yet: " + screen.chooseScreen().messageText(), screen.chooseScreen().modCount() == 0);
		save(screen, outDir.resolve("4-no-mods.png"));

		// A bad name is refused on the screen.
		SwingUtilities.invokeAndWait(screen::showCreateMod);
		SwingUtilities.invokeAndWait(() -> {
			screen.createScreen().setModName("_Hidden");
			screen.createScreen().create();
		});
		waitFor(() -> !screen.createScreen().messageText().equals("Creating..."));
		expect("bad name shows why: " + screen.createScreen().messageText(), screen.createScreen().messageText().contains("_"));
		save(screen, outDir.resolve("5-bad-name.png"));
		expect("bad name creates nothing", !Files.exists(mods));

		// Creating a mod selects it.
		SwingUtilities.invokeAndWait(() -> {
			screen.createScreen().setModName("My New Mod");
			screen.createScreen().create();
		});
		waitFor(() -> screen.shownScreen().equals(MainScreen.HOME));
		expect("new mod is the current mod", screen.currentMod().isPresent() && screen.currentMod().get().folderName().equals("My New Mod"));
		expect("current mod is saved", new ToolkitSettings(settingsFile).currentMod().equals(Optional.of("My New Mod")));

		// Home shows the mod and its assets.
		waitFor(() -> !screen.homeScreen().isLoading());
		expect("home shows the name", screen.homeScreen().modNameText().equals("My New Mod"));
		expect("home has the rail", screen.isRailShowing());
		expect("the rail marks where you are: " + screen.railHighlight(), "Assets".equals(screen.railHighlight()));
		expect("the top bar names the mod: " + screen.topBarModName(), screen.topBarModName().equals("My New Mod"));
		expect("the top bar shows the version: " + screen.topBarVersion(), "Alpha v3.0".equals(screen.topBarVersion()));
		expect("the top bar can change the mod", screen.isTopBarChangeShowing());
		expect("an empty mod says what to do next", screen.homeScreen().isEmptyHintShowing());
		expect("home shows the new mod's folders: " + screen.homeScreen().treeRowCount(), screen.homeScreen().treeRowCount() == 6);
		expect("home counts the files: " + screen.homeScreen().assetsSummaryText(), screen.homeScreen().assetsSummaryText().startsWith("0 files"));
		save(screen, outDir.resolve("6-home.png"));

		// With no handbook yet, the editor says so, and doesn't claim the handbook lacks the tag.
		Path modRoot = mods.resolve("My New Mod");
		SwingUtilities.invokeAndWait(screen.homeScreen()::clickAddAsset);
		waitFor(() -> !screen.addAssetScreen().isBusy());
		SwingUtilities.invokeAndWait(() -> {
			screen.addAssetScreen().selectType("Animal");
			screen.addAssetScreen().setAssetName("NO_BOOK");
			screen.addAssetScreen().next();
		});
		expect("no handbook yet says so: " + screen.assetEditorScreen().noteTexts(), screen.assetEditorScreen().noteTexts().stream().anyMatch(n -> n.contains("has been downloaded yet")));
		expect("no handbook yet has no credit", screen.assetEditorScreen().noteTexts().stream().noneMatch(n -> n.contains("CC BY-SA")));
		expect("no handbook yet doesn't blame the handbook: " + screen.assetEditorScreen().descriptionFor("MASS"), screen.assetEditorScreen().descriptionFor("MASS").equals("No handbook description available."));
		save(screen, outDir.resolve("6-asset-editor-no-handbook.png"));
		SwingUtilities.invokeAndWait(screen::showHome);
		waitFor(() -> !screen.homeScreen().isLoading());

		// A copy kept from before a game update is still shown, and marked.
		HANDBOOK_VERSION[0] = "0.1.1";
		expect("an older copy is in the cache", handbookStore.refresh(new GameParser.GameVersion(1, 1)) == HandbookStore.Outcome.UPDATED);
		HANDBOOK_VERSION[0] = null;
		SwingUtilities.invokeAndWait(screen.homeScreen()::clickAddAsset);
		waitFor(() -> !screen.addAssetScreen().isBusy());
		SwingUtilities.invokeAndWait(() -> {
			screen.addAssetScreen().selectType("Animal");
			screen.addAssetScreen().setAssetName("OLD_BOOK");
			screen.addAssetScreen().next();
		});
		expect("a handbook for another version is marked: " + screen.assetEditorScreen().noteTexts(),
				screen.assetEditorScreen().noteTexts().stream().anyMatch(n -> n.contains("0.1.1") && n.contains("out of date")));
		SwingUtilities.invokeAndWait(screen::showHome);
		waitFor(() -> !screen.homeScreen().isLoading());

		// A handbook for this game replaces it, and the note goes.
		HANDBOOK_VERSION[0] = "0." + gameVersion.major() + "." + gameVersion.minor();
		expect("a matching handbook is kept", handbookStore.refresh(gameVersion) == HandbookStore.Outcome.UPDATED);

		// Add New Asset: every type is listed, but only verified ones can be chosen.
		long filesBefore = countFiles(modRoot);
		SwingUtilities.invokeAndWait(screen.homeScreen()::clickAddAsset);
		expect("Add New Asset opens its screen", screen.shownScreen().equals(MainScreen.ADD_ASSET));
		waitFor(() -> !screen.addAssetScreen().isBusy());
		expect("the types are listed: " + screen.addAssetScreen().messageText(), screen.addAssetScreen().entries().size() == 67);
		expect("Next waits for a choice", !screen.addAssetScreen().isNextEnabled());
		expect("the rooms start folded under one row: " + screen.addAssetScreen().visibleRows(), screen.addAssetScreen().visibleRows().contains("Rooms") && !screen.addAssetScreen().visibleRows().contains("Farm") && !screen.addAssetScreen().isGroupOpen("Rooms"));
		SwingUtilities.invokeAndWait(() -> screen.addAssetScreen().selectGroup("Rooms"));
		expect("the Rooms row can't be chosen as a type", screen.addAssetScreen().selectedType() == null && !screen.addAssetScreen().isNextEnabled());
		SwingUtilities.invokeAndWait(() -> screen.addAssetScreen().pressKeyOnList(java.awt.event.KeyEvent.VK_ENTER));
		expect("Enter opens it: " + screen.addAssetScreen().visibleRows(), screen.addAssetScreen().visibleRows().containsAll(List.of("Farm", "Fishery", "Orchard", "Hunter")) && screen.addAssetScreen().isGroupOpen("Rooms"));
		SwingUtilities.invokeAndWait(() -> screen.addAssetScreen().pressKeyOnList(java.awt.event.KeyEvent.VK_LEFT));
		expect("Left closes it", !screen.addAssetScreen().isGroupOpen("Rooms") && !screen.addAssetScreen().visibleRows().contains("Farm"));
		SwingUtilities.invokeAndWait(() -> screen.addAssetScreen().pressKeyOnList(java.awt.event.KeyEvent.VK_RIGHT));
		expect("Right opens it", screen.addAssetScreen().isGroupOpen("Rooms"));
		save(screen, outDir.resolve("6-add-asset-rooms-open.png"));
		SwingUtilities.invokeAndWait(() -> screen.addAssetScreen().selectType("Orchard"));
		expect("a room inside it can be chosen", "Orchard".equals(screen.addAssetScreen().selectedType()));
		SwingUtilities.invokeAndWait(() -> screen.addAssetScreen().toggleGroupForTest("Rooms"));
		expect("closing it moves the selection to its row", screen.addAssetScreen().selectedType() == null && !screen.addAssetScreen().isGroupOpen("Rooms"));
		SwingUtilities.invokeAndWait(() -> screen.addAssetScreen().selectType("Hunter"));
		expect("choosing a room by name opens the group", "Hunter".equals(screen.addAssetScreen().selectedType()) && screen.addAssetScreen().isGroupOpen("Rooms"));
		SwingUtilities.invokeAndWait(() -> screen.addAssetScreen().toggleGroupForTest("Rooms"));
		SwingUtilities.invokeAndWait(() -> screen.addAssetScreen().selectType("Disease"));
		expect("an unverified type can't be chosen", screen.addAssetScreen().selectedType() == null);
		expect("it says why: " + screen.addAssetScreen().typeHintText(), screen.addAssetScreen().typeHintText() != null && screen.addAssetScreen().typeHintText().contains("Disease"));
		expect("the usable types come first", screen.addAssetScreen().entries().get(0).available() && !screen.addAssetScreen().entries().get(screen.addAssetScreen().entries().size() - 1).available());
		SwingUtilities.invokeAndWait(() -> screen.addAssetScreen().selectType("Animal"));
		expect("Animal can be chosen", "Animal".equals(screen.addAssetScreen().selectedType()));
		SwingUtilities.invokeAndWait(() -> screen.addAssetScreen().setAssetName("AUROCH"));
		expect("a vanilla name is refused: " + screen.addAssetScreen().nameProblemText(), screen.addAssetScreen().nameProblemText().contains("already has"));
		expect("a refused name can't continue", !screen.addAssetScreen().isNextEnabled());
		save(screen, outDir.resolve("6-add-asset-bad-name.png"));
		SwingUtilities.invokeAndWait(() -> screen.addAssetScreen().setAssetName("TEST_BEAST"));
		expect("the destination is shown: " + screen.addAssetScreen().destinationText(), screen.addAssetScreen().destinationText().contains("/assets/init/animal/TEST_BEAST.txt"));
		expect("a good name can continue", screen.addAssetScreen().isNextEnabled());
		save(screen, outDir.resolve("6-add-asset.png"));
		SwingUtilities.invokeAndWait(screen.addAssetScreen()::next);
		expect("the editor opens", screen.shownScreen().equals(MainScreen.ASSET_EDITOR));

		// The editor: required tags are empty, tags with defaults are grey, the rest are in the add list.
		AssetEditorScreen editor = screen.assetEditorScreen();
		expect("required tags have rows: " + editor.tagKeys(), editor.tagKeys().containsAll(List.of("MASS", "SPEED", "ICON", "COLOR", "SPRITE", "NAME", "DESC")));
		expect("tags with defaults are grey: " + editor.ghostKeys(), editor.ghostKeys().equals(List.of("RESOURCES", "DAMAGE", "CLIMATE", "TERRAIN", "NAMES")));
		expect("a ghost shows the game's default", editor.ghostValue("CLIMATE").equals("0 for every entry"));
		expect("a ghost can't be edited", !editor.isGhostEditable("CLIMATE"));
		expect("a tag with no default is in the add list: " + editor.addableKeys(), editor.addableKeys().equals(List.of("WIKI")));
		SwingUtilities.invokeAndWait(() -> { });
		SwingUtilities.invokeAndWait(() -> { });
		SwingUtilities.invokeAndWait(() -> { });
		expect("the form opens at the top: " + editor.scrollPosition(), editor.scrollPosition() == 0);
		SwingUtilities.invokeAndWait(() -> editor.setValue("MASS", "1"));
		SwingUtilities.invokeAndWait(() -> editor.setValue("MASS", ""));
		SwingUtilities.invokeAndWait(() -> { });
		expect("typing doesn't move the form: " + editor.scrollPosition(), editor.scrollPosition() == 0);
		SwingUtilities.invokeAndWait(() -> editor.scrollTo(editor.scrollMaximum()));
		save(screen, outDir.resolve("6-asset-editor-bottom.png"));
		SwingUtilities.invokeAndWait(() -> editor.scrollTo(0));
		expect("a matching handbook has no version note: " + editor.noteTexts(), editor.noteTexts().stream().noneMatch(n -> n.contains("out of date")));
		expect("the handbook is credited", editor.noteTexts().stream().anyMatch(n -> n.contains("CC BY-SA")));
		expect("the notes start collapsed", !editor.isNotesOpen());
		SwingUtilities.invokeAndWait(editor::clickNotes);
		expect("clicking the notes heading opens them", editor.isNotesOpen());
		expect("the credit is still there while the notes are open", editor.noteTexts().stream().anyMatch(n -> n.contains("CC BY-SA")));
		SwingUtilities.invokeAndWait(editor::clickNotes);
		expect("clicking the notes heading again closes them", !editor.isNotesOpen());
		expect("a row shows the handbook's words: " + editor.descriptionFor("MASS"), editor.descriptionFor("MASS").equals("How heavy it is:\n\n- Yield."));
		expect("a row shows the example: " + editor.exampleFor("MASS"), "MASS: 110,".equals(editor.exampleFor("MASS")));
		expect("a ghost shows the example: " + editor.exampleFor("CLIMATE"), "CLIMATE: {\n\tCOLD: 0.8,\n},".equals(editor.exampleFor("CLIMATE")));
		expect("no example shows nothing", editor.exampleFor("GRAZES") == null);
		expect("an unconfirmed tag says so", editor.isShowingUnverified("COLOR") && !editor.isShowingUnverified("MASS"));
		expect("a link off the handbook's site isn't offered", editor.linkFor("COLOR") == null);
		expect("a ghost shows the handbook's words: " + editor.descriptionFor("CLIMATE"), editor.descriptionFor("CLIMATE").equals("How common it is."));
		expect("a tag with no heading says so", editor.descriptionFor("SPEED").contains("doesn't describe"));
		expect("a text tag is looked up in the text section: " + editor.descriptionFor("NAME"), editor.descriptionFor("NAME").equals("The singular name."));
		expect("a tag marked as unused says so: " + editor.effectFor("GRAZES"), "No effect in the game".equals(editor.effectFor("GRAZES")));
		expect("a used tag isn't marked", editor.effectFor("MASS") == null);
		expect("Read more goes to the tag: " + editor.linkFor("GRAZES"), editor.linkFor("GRAZES").endsWith("#grazes"));
		SwingUtilities.invokeAndWait(() -> editor.openLink("GRAZES"));
		expect("Read more opens the handbook", OPENED_LINKS.equals(List.of(editor.linkFor("GRAZES"))));
		expect("a tag with no heading has no link", editor.linkFor("SPEED") == null);

		// Details: nothing shows until a tag is chosen, and then everything about it does.
		expect("the details start with a prompt", editor.detailsName() == null);
		expect("the credit shows with nothing selected", editor.noteTexts().stream().anyMatch(n -> n.contains("CC BY-SA")));
		SwingUtilities.invokeAndWait(() -> editor.selectTag("MASS"));
		expect("the details follow a click: " + editor.detailsName(), "MASS".equals(editor.detailsName()));
		expect("the details show the description: " + editor.detailsText(), editor.detailsText().contains("How heavy it is:\n\n- Yield."));
		expect("the details show the example", editor.detailsText().contains("MASS: 110,"));
		expect("the details show the game's rule: " + editor.detailsText(), editor.detailsText().stream().anyMatch(t -> t.contains("1 to 500")));
		expect("the details say where the rule comes from", editor.detailsText().stream().anyMatch(t -> t.startsWith("From ")));
		expect("the details offer Read more", editor.detailsHasReadMore());
		OPENED_LINKS.clear();
		SwingUtilities.invokeAndWait(editor::clickDetailsReadMore);
		expect("Read more in the details opens the tag's page: " + OPENED_LINKS, OPENED_LINKS.equals(List.of(editor.linkFor("MASS"))));
		SwingUtilities.invokeAndWait(() -> editor.simulateFocusOn("DANGER"));
		expect("the details follow focus: " + editor.detailsName(), "DANGER".equals(editor.detailsName()));
		expect("a tag the handbook lacks has no Read more", !editor.detailsHasReadMore());
		SwingUtilities.invokeAndWait(() -> editor.selectTag("CLIMATE"));
		expect("a ghost's details give its default: " + editor.detailsText(), editor.detailsText().stream().anyMatch(t -> t.contains("0 for every entry")));
		expect("a ghost keeps showing its default in the form", editor.ghostValue("CLIMATE").equals("0 for every entry"));
		expect("a hover gives the summary: " + editor.tipFor("MASS"), editor.tipFor("MASS").contains("How heavy") && editor.tipFor("MASS").contains("1 to 500"));
		expect("a ghost's hover gives its default: " + editor.tipFor("CLIMATE"), editor.tipFor("CLIMATE").contains("0 for every entry"));
		SwingUtilities.invokeAndWait(() -> editor.selectTag("PACK"));
		expect("HTML in a description stays literal in the details: " + editor.detailsText(), editor.detailsText().stream().anyMatch(t -> t.startsWith("<html><img")));
		expect("HTML in a summary can't render in a hover: " + editor.tipFor("PACK"), !javax.swing.plaf.basic.BasicHTML.isHTMLString(editor.tipFor("PACK")) && editor.tipFor("PACK").contains("<img"));
		expect("only the selected row is marked: " + editor.highlightedRows(), editor.highlightedRows().equals(List.of("PACK")));
		expect("long hover text is wrapped: " + editor.tipFor("GRAZES"), editor.tipFor("GRAZES").contains("\n") && editor.tipFor("GRAZES").lines().allMatch(l -> l.length() <= 80));
		SwingUtilities.invokeAndWait(() -> { });
		save(screen, outDir.resolve("6-asset-editor-details.png"));
		editor.setDiscardPrompt(question -> true);
		SwingUtilities.invokeAndWait(editor::goToFirstProblem);
		expect("Show goes to the first tag that needs attention: " + editor.selectedTag(), editor.selectedTag() != null && editor.tagKeys().contains(editor.selectedTag()));
		SwingUtilities.invokeAndWait(() -> editor.scrollTo(0));

		// The window's width decides the layout, and nothing is cut off at any of them.
		resize(screen, 1000, 700, WidthBand.MEDIUM);
		expect("a medium window has no details beside the form", !editor.isDetailsDocked());
		expect("its rows open the details instead", editor.isInfoShowing("MASS") && editor.isInfoShowing("CLIMATE"));
		expect("it keeps the rail", screen.isRailShowing() && !screen.isMenuShowing());
		expect("its rows stay on one line", !editor.isStacked("MASS"));
		expect("the credit stays with the details at this width", editor.noteTexts().stream().anyMatch(n -> n.contains("CC BY-SA")));
		expectNothingCutOff(screen, "medium editor");
		SwingUtilities.invokeAndWait(() -> editor.clickInfo("MASS"));
		expect("the info button opens the details", editor.isDetailsPopoverShowing() && "MASS".equals(editor.detailsName()));
		save(screen, outDir.resolve("6-asset-editor-medium.png"));
		saveComponent(editor.detailsComponent(), outDir.resolve("6-asset-editor-popover.png"));
		SwingUtilities.invokeAndWait(editor::hideDetailsPopover);
		resize(screen, 720, 460, WidthBand.NARROW);
		expect("a narrow window has a menu rather than a rail", !screen.isRailShowing() && screen.isMenuShowing());
		expect("the menu has everything the rail has: " + screen.menuItemNames(), screen.menuItemNames().equals(List.of("Assets", "Add New Asset", "Asset Creation Wizard", "Portrait Design Wizard", "Mod Info", "Check Syntax", "Race Portrait Sim", "Map Sprite Sim", "Normal Maps", "Audio Conversion", "Game Version Diff", "Connect Agent", "Open Folder")));
		expect("names sit above fields", editor.isStacked("MASS"));
		expect("the credit stays with the details at this width too", editor.noteTexts().stream().anyMatch(n -> n.contains("CC BY-SA")));
		expectNothingCutOff(screen, "narrow editor");
		save(screen, outDir.resolve("6-asset-editor-narrow.png"));
		SwingUtilities.invokeAndWait(() -> editor.addTag("DAMAGE"));
		expect("a row added at this width fits it too", editor.isStacked("DAMAGE") && editor.isInfoShowing("DAMAGE"));
		SwingUtilities.invokeAndWait(() -> editor.removeTag("DAMAGE"));
		resize(screen, 1200, 760, WidthBand.WIDE);
		expect("a wide window brings the details back", editor.isDetailsDocked() && !editor.isInfoShowing("MASS") && !editor.isStacked("MASS"));
		expect("and the rail", screen.isRailShowing() && !screen.isMenuShowing());
		expectNothingCutOff(screen, "wide editor");

		expect("nothing can be saved yet", !editor.isSaveEnabled());
		expect("it counts what's still needed: " + editor.messageText(), editor.messageText().contains("required tag"));
		expect("an empty form isn't shown as wrong", editor.problemFor("MASS").isBlank());
		expect("nothing has been written yet", countFiles(modRoot) == filesBefore);
		save(screen, outDir.resolve("6-asset-editor-empty.png"));

		// The rail can't lose work without asking.
		List<String> asked = new java.util.ArrayList<>();
		boolean[] answer = new boolean[1];
		editor.setDiscardPrompt(question -> {
			asked.add(question);
			return answer[0];
		});
		expect("the rail marks the editor as adding an asset: " + screen.railHighlight(), "Add New Asset".equals(screen.railHighlight()));
		SwingUtilities.invokeAndWait(() -> screen.clickRail("Assets"));
		expect("an empty form isn't asked about", asked.isEmpty() && screen.shownScreen().equals(MainScreen.HOME));
		waitFor(() -> !screen.homeScreen().isLoading());
		SwingUtilities.invokeAndWait(() -> screen.clickRail("Add New Asset"));
		waitFor(() -> !screen.addAssetScreen().isBusy());
		expect("the rail opens Add New Asset", screen.shownScreen().equals(MainScreen.ADD_ASSET));
		SwingUtilities.invokeAndWait(() -> {
			screen.addAssetScreen().selectType("Animal");
			screen.addAssetScreen().setAssetName("TEST_BEAST");
			screen.addAssetScreen().next();
		});
		SwingUtilities.invokeAndWait(() -> editor.setValue("MASS", "5"));
		SwingUtilities.invokeAndWait(() -> screen.clickRail("Mod Info"));
		expect("leaving with unsaved values asks first: " + asked, asked.size() == 1 && asked.get(0).contains("Discard"));
		expect("saying no stays on the editor", screen.shownScreen().equals(MainScreen.ASSET_EDITOR));
		expect("saying no keeps the value", "5".equals(editor.valueOf("MASS")));
		expect("the highlight stays on the editor: " + screen.railHighlight(), "Add New Asset".equals(screen.railHighlight()));
		SwingUtilities.invokeAndWait(screen::clickTopBarChange);
		expect("the top bar's Change asks as well", asked.size() == 2 && screen.shownScreen().equals(MainScreen.ASSET_EDITOR));
		SwingUtilities.invokeAndWait(() -> editor.setValue("MASS", ""));

		// Switching theme keeps what's on screen, and is remembered.
		expect("dark is the default", new ToolkitSettings(settingsFile).theme() == ToolkitSettings.ThemeChoice.DARK && Theme.current() == ToolkitSettings.ThemeChoice.DARK);
		expect("dark uses the handbook's background", new java.awt.Color(0x1e2029).equals(UIManager.getColor("Panel.background")));
		SwingUtilities.invokeAndWait(() -> editor.setValue("MASS", "123"));
		SwingUtilities.invokeAndWait(() -> editor.scrollTo(editor.scrollMaximum() / 2));
		int scrolledTo = editor.scrollPosition();
		SwingUtilities.invokeAndWait(screen::toggleTheme);
		SwingUtilities.invokeAndWait(() -> { });
		expect("the theme changes", Theme.current() == ToolkitSettings.ThemeChoice.LIGHT);
		expect("light uses the handbook's scheme", java.awt.Color.WHITE.equals(UIManager.getColor("Panel.background")));
		expect("the choice is saved", new ToolkitSettings(settingsFile).theme() == ToolkitSettings.ThemeChoice.LIGHT);
		expect("the button offers the other theme: " + screen.themeButtonText(), screen.themeButtonText().equals("Dark mode"));
		expect("a typed value survives a theme change", "123".equals(editor.valueOf("MASS")));
		expect("the form stays near where it was: " + scrolledTo + " then " + editor.scrollPosition(), scrolledTo > 0 && editor.scrollPosition() > 0);
		SwingUtilities.invokeAndWait(() -> editor.scrollTo(0));
		save(screen, outDir.resolve("6-asset-editor-light.png"));
		SwingUtilities.invokeAndWait(screen::toggleTheme);
		SwingUtilities.invokeAndWait(() -> editor.setValue("MASS", ""));
		SwingUtilities.invokeAndWait(() -> editor.scrollTo(0));
		expect("and back again", Theme.current() == ToolkitSettings.ThemeChoice.DARK);

		SwingUtilities.invokeAndWait(() -> {
			editor.setValue("ICON", "32->animal->Auroch->0");
			editor.setValue("CARAVAN", "true");
			editor.setValue("MASS", "100");
			editor.setValue("SPEED", "4");
			editor.setValue("HEIGHT", "5");
			editor.setValue("COLOR", "200_100_50");
			editor.setValue("PACK", "true");
			editor.setValue("GRAZES", "false");
			editor.setValue("DANGER", "0.5");
			editor.setValue("LIVES_IN_CAVES", "0");
			editor.setValue("SPRITE", "Auroch");
			editor.setValue("NAME", "Test Beast");
			editor.setValue("DESC", "For testing.");
		});
		expect("an animal's call has to be chosen, because vanilla's don't share one: " + editor.messageText(), !editor.isSaveEnabled());
		SwingUtilities.invokeAndWait(() -> editor.setValue("SOUND.ANIMAL_CALL_", "animal->Bull*"));
		expect("with what's required, it can be saved: " + editor.messageText(), editor.isSaveEnabled());

		// Add and remove tags, as in the info editor.
		SwingUtilities.invokeAndWait(() -> editor.addTag("CLIMATE"));
		expect("an added tag stops being grey", !editor.ghostKeys().contains("CLIMATE") && editor.tagKeys().contains("CLIMATE"));
		SwingUtilities.invokeAndWait(() -> editor.setValue("CLIMATE.COLD", "2"));
		expect("an out-of-range value shows why: " + editor.problemFor("CLIMATE"), editor.problemFor("CLIMATE").contains("from 0 to 1"));
		expect("an out-of-range value blocks saving", !editor.isSaveEnabled());
		expect("the footer says why, in case it's out of sight: " + editor.messageText(), editor.messageText().contains("has a problem"));
		save(screen, outDir.resolve("6-asset-editor-invalid.png"));
		SwingUtilities.invokeAndWait(() -> editor.setValue("CLIMATE.COLD", "0.8"));
		expect("fixing it allows saving", editor.isSaveEnabled());
		SwingUtilities.invokeAndWait(() -> editor.setValue("MASS", "abc"));
		expect("letters can't be typed in a number", "100".equals(valueIn(editor, "MASS")));
		SwingUtilities.invokeAndWait(() -> editor.setValue("MASS", "501"));
		expect("above an unenforced maximum warns: " + editor.problemFor("MASS"), editor.problemFor("MASS").contains("doesn't enforce"));
		expect("and doesn't block", editor.isSaveEnabled());
		SwingUtilities.invokeAndWait(() -> editor.setValue("MASS", "100"));
		SwingUtilities.invokeAndWait(() -> editor.addTag("DAMAGE"));
		SwingUtilities.invokeAndWait(() -> editor.removeTag("DAMAGE"));
		expect("a removed tag goes grey again", editor.ghostKeys().contains("DAMAGE") && !editor.tagKeys().contains("DAMAGE"));
		SwingUtilities.invokeAndWait(() -> editor.removeTag("MASS"));
		expect("a required tag can't be removed", editor.tagKeys().contains("MASS"));
		SwingUtilities.invokeAndWait(() -> editor.addTag("WIKI"));
		expect("an added tag leaves the add list", editor.addableKeys().isEmpty());
		expect("a half-filled block is fine until something's in it", editor.isSaveEnabled());
		SwingUtilities.invokeAndWait(() -> editor.setValue("WIKI.NAME", "Test"));
		expect("then its required entries are needed: " + editor.problemFor("WIKI"), !editor.isSaveEnabled() && editor.problemFor("WIKI").contains("needed"));
		SwingUtilities.invokeAndWait(() -> editor.removeTag("WIKI"));
		expect("a tag with no default returns to the add list", editor.addableKeys().equals(List.of("WIKI")));

		SwingUtilities.invokeAndWait(() -> editor.addTag("RESOURCES"));
		SwingUtilities.invokeAndWait(() -> {
			editor.setValue("RESOURCES", "MEAT,LEATHER");
			editor.setValue("RESOURCE_AMOUNT", "0.8");
		});
		expect("a resource with no amount blocks saving: " + editor.problemFor("RESOURCES"), !editor.isSaveEnabled());
		SwingUtilities.invokeAndWait(() -> editor.setValue("RESOURCE_AMOUNT", "0.8,0.5"));
		SwingUtilities.invokeAndWait(() -> editor.addTag("TERRAIN"));
		SwingUtilities.invokeAndWait(() -> editor.setValue("TERRAIN.FOREST", "1"));
		expect("the finished animal can be saved", editor.isSaveEnabled());
		save(screen, outDir.resolve("6-asset-editor-filled.png"));
		expect("still nothing written before Save", countFiles(modRoot) == filesBefore);

		SwingUtilities.invokeAndWait(editor::save);
		waitFor(() -> !editor.isBusy() && screen.shownScreen().equals(MainScreen.HOME) && !screen.homeScreen().isLoading());
		String version;
		try (java.util.stream.Stream<Path> children = Files.list(modRoot)) {
			version = children.map(p -> p.getFileName().toString()).filter(n -> n.matches("V\\d+")).findFirst().orElseThrow();
		}
		Path beast = modRoot.resolve(version).resolve("assets/init/animal/TEST_BEAST.txt");
		Path beastText = modRoot.resolve(version).resolve("assets/text/animal/TEST_BEAST.txt");
		expect("the data file was created", Files.isRegularFile(beast));
		expect("the text file was created", Files.isRegularFile(beastText));
		System.out.println("--- created data file\n" + Files.readString(beast) + "--- created text file\n" + Files.readString(beastText) + "---");
		expect("the tree shows the new file: " + screen.homeScreen().treeRows(), screen.homeScreen().treeRows().contains("TEST_BEAST.txt"));
		save(screen, outDir.resolve("6-home-with-asset.png"));
		SwingUtilities.invokeAndWait(screen::showSyntaxCheck);
		runCheck(screen, null, false);
		expect("the syntax check agrees with the new asset: " + screen.syntaxScreen().summaryText(), screen.syntaxScreen().summaryText().contains("No syntax errors"));
		SwingUtilities.invokeAndWait(screen::showHome);
		waitFor(() -> !screen.homeScreen().isLoading());
		deleteQuietlyForLaterChecks(modRoot.resolve(version).resolve("assets/init/animal"));
		deleteQuietlyForLaterChecks(modRoot.resolve(version).resolve("assets/text/animal"));

		// Modify Info: show the fields, block invalid values, add a field, save.
		SwingUtilities.invokeAndWait(screen::showModifyInfo);
		waitFor(() -> !screen.modifyScreen().isBusy());
		expect("all the file's fields are shown: " + screen.modifyScreen().fieldKeys(), screen.modifyScreen().fieldKeys().size() == 7);
		expect("an absent field with a default is ghosted: " + screen.modifyScreen().ghostKeys(), screen.modifyScreen().ghostKeys().equals(List.of("TEXTURE_CACHE_SIZE")));
		expect("a ghost shows the game's default", screen.modifyScreen().ghostValue("TEXTURE_CACHE_SIZE").equals("4096"));
		expect("a ghost can't be edited", !screen.modifyScreen().isGhostEditable("TEXTURE_CACHE_SIZE"));
		expect("a ghost isn't also in the add list: " + screen.modifyScreen().addableKeys(), screen.modifyScreen().addableKeys().isEmpty());
		expect("nothing to save yet", !screen.modifyScreen().isSaveEnabled());
		save(screen, outDir.resolve("6a-modify-info.png"));

		SwingUtilities.invokeAndWait(() -> screen.modifyScreen().setValue(ModInfoField.NAME, "Renamed Mod"));
		expect("a valid change can be saved", screen.modifyScreen().isSaveEnabled());
		SwingUtilities.invokeAndWait(() -> screen.modifyScreen().setValue(ModInfoField.DESC, "bad\",value"));
		expect("an invalid value shows why: " + screen.modifyScreen().errorFor(ModInfoField.DESC), screen.modifyScreen().errorFor(ModInfoField.DESC).contains("quote"));
		expect("an invalid value blocks saving", !screen.modifyScreen().isSaveEnabled());
		save(screen, outDir.resolve("6b-modify-invalid.png"));
		SwingUtilities.invokeAndWait(() -> screen.modifyScreen().setValue(ModInfoField.DESC, "A good description."));
		expect("fixing it allows saving", screen.modifyScreen().isSaveEnabled());
		SwingUtilities.invokeAndWait(() -> screen.modifyScreen().addField(ModInfoField.TEXTURE_CACHE_SIZE));
		expect("an added field gets a row", screen.modifyScreen().fieldKeys().contains("TEXTURE_CACHE_SIZE"));
		expect("an added field stops being a ghost", screen.modifyScreen().ghostKeys().isEmpty());
		save(screen, outDir.resolve("6c-modify-added.png"));

		// Remove buttons: everything but NAME has one.
		List<String> removable = screen.modifyScreen().removableKeys();
		expect("NAME has no remove button: " + removable, !removable.contains("NAME"));
		expect("every other field has one: " + removable, removable.size() == screen.modifyScreen().fieldKeys().size() - 1);
		SwingUtilities.invokeAndWait(() -> screen.modifyScreen().removeField("NAME"));
		expect("NAME can't be removed", screen.modifyScreen().fieldKeys().contains("NAME"));

		// A removed field is greyed out in place, with its default. The file doesn't change until Save.
		String beforeRemoval = Files.readString(modRoot.resolve("_Info.txt"));
		SwingUtilities.invokeAndWait(() -> screen.modifyScreen().removeField("AUTHOR"));
		SwingUtilities.invokeAndWait(() -> screen.modifyScreen().removeField("GAME_VERSION_MINOR"));
		SwingUtilities.invokeAndWait(() -> screen.modifyScreen().removeField("TEXTURE_CACHE_SIZE"));
		expect("a removed field can't be edited as a field", !screen.modifyScreen().fieldKeys().contains("AUTHOR"));
		expect("removed fields with defaults are greyed out: " + screen.modifyScreen().ghostKeys(), screen.modifyScreen().ghostKeys().equals(List.of("AUTHOR", "TEXTURE_CACHE_SIZE")));
		expect("a grey text field shows the game's default", screen.modifyScreen().ghostValue("AUTHOR").equals("???"));
		expect("a grey size field shows the game's default", screen.modifyScreen().ghostValue("TEXTURE_CACHE_SIZE").equals("4096"));
		expect("a grey field can't be edited", !screen.modifyScreen().isGhostEditable("AUTHOR"));
		expect("a field with no default goes to the add list: " + screen.modifyScreen().addableKeys(), screen.modifyScreen().addableKeys().equals(List.of("GAME_VERSION_MINOR")));
		expect("removal only happens on Save", Files.readString(modRoot.resolve("_Info.txt")).equals(beforeRemoval));
		expect("only one field is marked after removing some: " + screen.modifyScreen().highlightedRows(), screen.modifyScreen().highlightedRows().size() <= 1);
		SwingUtilities.invokeAndWait(() -> screen.modifyScreen().selectField("NAME"));
		expect("a field's details show its help: " + screen.modifyScreen().detailsText(), "NAME".equals(screen.modifyScreen().detailsName()) && screen.modifyScreen().detailsText().size() > 1);
		expect("selecting moves the mark", screen.modifyScreen().highlightedRows().equals(List.of("NAME")));
		expect("a field has hover text: " + screen.modifyScreen().tipFor("NAME"), screen.modifyScreen().tipFor("NAME") != null);
		save(screen, outDir.resolve("6c2-modify-removed.png"));

		// Adding one back restores the value it had.
		SwingUtilities.invokeAndWait(() -> screen.modifyScreen().addField(ModInfoField.GAME_VERSION_MINOR));
		expect("the restored field is back", screen.modifyScreen().fieldKeys().contains("GAME_VERSION_MINOR"));
		expect("its add button goes", screen.modifyScreen().addableKeys().isEmpty());

		// A field added but never saved can be added again.
		SwingUtilities.invokeAndWait(() -> screen.modifyScreen().reAdd("TEXTURE_CACHE_SIZE"));
		expect("only the one removal is left: " + screen.modifyScreen().ghostKeys(), screen.modifyScreen().ghostKeys().equals(List.of("AUTHOR")));

		SwingUtilities.invokeAndWait(screen.modifyScreen()::save);
		waitFor(() -> screen.shownScreen().equals(MainScreen.HOME) && !screen.homeScreen().isLoading());
		expect("home shows the new name: " + screen.homeScreen().modNameText(), screen.homeScreen().modNameText().equals("Renamed Mod"));
		String savedInfo = Files.readString(modRoot.resolve("_Info.txt"));
		expect("the file has the edits: " + savedInfo, savedInfo.contains("NAME: \"Renamed Mod\",") && savedInfo.contains("DESC: \"A good description.\",") && savedInfo.contains("TEXTURE_CACHE_SIZE: 4096,"));
		expect("the removed field is gone: " + savedInfo, !savedInfo.contains("AUTHOR"));
		expect("the restored field is kept: " + savedInfo, savedInfo.contains("GAME_VERSION_MINOR: "));

		// Open Folder asks for the mod's own folder, and a failure is shown.
		// Resource: a second type, through the same screens.
		SwingUtilities.invokeAndWait(screen::showHome);
		waitFor(() -> !screen.homeScreen().isLoading());
		SwingUtilities.invokeAndWait(screen::showAddAsset);
		waitFor(() -> !screen.addAssetScreen().isBusy());
		SwingUtilities.invokeAndWait(() -> {
			screen.addAssetScreen().selectType("Resource");
			screen.addAssetScreen().setAssetName("GEM");
		});
		expect("Resource can be chosen", "Resource".equals(screen.addAssetScreen().selectedType()));
		expect("a vanilla resource name is refused: " + screen.addAssetScreen().nameProblemText(), screen.addAssetScreen().nameProblemText().contains("already has"));
		SwingUtilities.invokeAndWait(() -> screen.addAssetScreen().setAssetName("TEST_GOOD"));
		expect("the resource's destination is shown: " + screen.addAssetScreen().destinationText(), screen.addAssetScreen().destinationText().contains("/assets/init/resource/TEST_GOOD.txt"));
		SwingUtilities.invokeAndWait(screen.addAssetScreen()::next);
		expect("the resource editor opens", screen.shownScreen().equals(MainScreen.ASSET_EDITOR) && editor.titleText().contains("Resource"));
		expect("its required tags have rows: " + editor.tagKeys(), editor.tagKeys().containsAll(List.of("DEGRADE_RATE", "COLOR", "MINIMAP_COLOR", "CATEGORY_DEFAULT", "ICON", "SPRITE", "SPRITE_DEBRIS", "NAME", "DESC")));
		expect("its prices are ghosted with the game's defaults: " + editor.ghostKeys(), editor.ghostKeys().containsAll(List.of("PRICE_CAP", "PRICE_MUL")) && editor.ghostValue("PRICE_CAP").equals("1.0") && editor.ghostValue("PRICE_MUL").equals("1.0"));
		expect("a ghosted price can't be edited", !editor.isGhostEditable("PRICE_MUL"));
		expect("nothing warns about spawning, because a resource doesn't spawn: " + editor.noteTexts(), editor.noteTexts().stream().noneMatch(n -> n.contains("never appear")));
		expect("the limits are noted: " + editor.noteTexts(), editor.noteTexts().stream().anyMatch(n -> n.contains("128")));
		expect("a resource nothing produces is warned about while it's made: " + editor.noteTexts(), editor.noteTexts().stream().anyMatch(n -> n.contains("TEST_GOOD") && n.contains("crash")));
		save(screen, outDir.resolve("6-resource-empty.png"));
		SwingUtilities.invokeAndWait(() -> {
			editor.setValue("DEGRADE_RATE", "0.5");
			editor.setValue("COLOR", "255_255_255");
			editor.setValue("MINIMAP_COLOR", "10_10_10");
			editor.setValue("CATEGORY_DEFAULT", "10");
			editor.setValue("ICON", "24->resource->Gem->0");
			editor.setValue("SPRITE", "Gem");
			editor.setValue("SPRITE_DEBRIS", "Wood");
			editor.setValue("NAME", "Test Good");
			editor.setValue("DESC", "For testing.");
		});
		expect("a category the game can't hold is refused: " + editor.problemFor("CATEGORY_DEFAULT"), !editor.isSaveEnabled() && editor.problemFor("CATEGORY_DEFAULT").contains("9"));
		save(screen, outDir.resolve("6-resource-invalid.png"));
		SwingUtilities.invokeAndWait(() -> editor.setValue("CATEGORY_DEFAULT", "4"));
		expect("vanilla's category 4 gets no problem or warning: " + editor.problemFor("CATEGORY_DEFAULT"), editor.isSaveEnabled() && editor.problemFor("CATEGORY_DEFAULT").isBlank());
		SwingUtilities.invokeAndWait(editor::save);
		waitFor(() -> !editor.isBusy() && screen.shownScreen().equals(MainScreen.HOME) && !screen.homeScreen().isLoading());
		Path good = modRoot.resolve(version).resolve("assets/init/resource/TEST_GOOD.txt");
		expect("the resource was written", Files.isRegularFile(good) && Files.isRegularFile(modRoot.resolve(version).resolve("assets/text/resource/TEST_GOOD.txt")));
		expect("it holds both sprites and no prices: " + Files.readString(good), Files.readString(good).contains("SPRITE_DEBRIS: Wood") && !Files.readString(good).contains("PRICE"));
		expect("the tree shows the new resource", screen.homeScreen().treeRows().contains("TEST_GOOD.txt"));
		expect("the warning doesn't stop it being saved, and Home keeps it: " + screen.homeScreen().producerWarningText(), screen.homeScreen().producerWarningText() != null && screen.homeScreen().producerWarningText().contains("TEST_GOOD"));
		save(screen, outDir.resolve("6-home-producer-warning.png"));
		expectNothingCutOff(screen, "home with the warning");

		// The warning goes once something produces it, and comes back if that goes.
		Path producerRoom = modRoot.resolve(version).resolve("assets/init/room/REFINER_TESTGOOD.txt");
		Files.createDirectories(producerRoom.getParent());
		Files.writeString(producerRoom, "INDUSTRIES: [\n\t{\n\t\tINDUSTRY: {\n\t\t\tOUT: {\n\t\t\t\tTEST_GOOD: 1,\n\t\t\t},\n\t\t},\n\t},\n],\n");
		SwingUtilities.invokeAndWait(screen::showHome);
		waitFor(() -> !screen.homeScreen().isLoading());
		expect("a room that produces it clears the warning: " + screen.homeScreen().producerWarningText(), screen.homeScreen().producerWarningText() == null);
		SwingUtilities.invokeAndWait(screen::showAddAsset);
		waitFor(() -> !screen.addAssetScreen().isBusy());
		SwingUtilities.invokeAndWait(() -> {
			screen.addAssetScreen().selectType("Resource");
			screen.addAssetScreen().setAssetName("OTHER_GOOD");
			screen.addAssetScreen().next();
		});
		expect("a different new resource is still warned about", editor.noteTexts().stream().anyMatch(n -> n.contains("OTHER_GOOD") && n.contains("crash")));
		SwingUtilities.invokeAndWait(screen::showHome);
		waitFor(() -> !screen.homeScreen().isLoading());
		Files.delete(producerRoom);
		SwingUtilities.invokeAndWait(screen::showHome);
		waitFor(() -> !screen.homeScreen().isLoading());
		expect("removing the room brings the warning back", screen.homeScreen().producerWarningText() != null);
		// A crop and a farm, which is the chain that gives the new resource a producer.
		SwingUtilities.invokeAndWait(screen::showAddAsset);
		waitFor(() -> !screen.addAssetScreen().isBusy());
		SwingUtilities.invokeAndWait(() -> {
			screen.addAssetScreen().selectType("Crop");
			screen.addAssetScreen().setAssetName("TEST_CROP");
		});
		expect("a crop's destination is shown: " + screen.addAssetScreen().destinationText(), screen.addAssetScreen().destinationText().contains("/assets/init/resource/growable/TEST_CROP.txt"));
		SwingUtilities.invokeAndWait(screen.addAssetScreen()::next);
		expect("the crop editor opens", screen.shownScreen().equals(MainScreen.ASSET_EDITOR) && editor.titleText().contains("Crop"));
		expect("a crop has no text section", !editor.isTextCardShowing());
		expect("the crop's required tags have rows: " + editor.tagKeys(), editor.tagKeys().containsAll(List.of("RESOURCE", "SEASONAL_OFFSET", "GROWTH_VALUE", "MINIMAP_COLOR", "SPRITE")));
		expect("its climate bonus is ghosted: " + editor.ghostKeys(), editor.ghostKeys().contains("CLIMATE_BONUS"));
		expect("a default inside a block is ghosted with the game's value", "1.0".equals(editor.nestedGhostValue("SPRITE.WIND_SWAY")) && "0.8".equals(editor.nestedGhostValue("SPRITE.STEM.WIND_SWAY")) && "1.0".equals(editor.nestedGhostValue("SPRITE.GROWTH.WIND_SWAY")));
		expect("a tag inside a block has its own row", editor.tipFor("SPRITE.POLLEN") != null);
		SwingUtilities.invokeAndWait(() -> editor.clickRowAction("SPRITE.WIND_SWAY"));
		expect("adding a nested tag takes its grey row away", editor.nestedGhostValue("SPRITE.WIND_SWAY") == null);
		SwingUtilities.invokeAndWait(() -> editor.clickRowAction("SPRITE.WIND_SWAY"));
		expect("removing it brings the grey row back", "1.0".equals(editor.nestedGhostValue("SPRITE.WIND_SWAY")));
		String cropImage = syx.core.GameAssetIndex.load(game, modRoot.resolve(version)).spriteOptions("sprite/resource/growable/").get(0);
		String takenResource = firstCropResource(game, modRoot.resolve(version));
		SwingUtilities.invokeAndWait(() -> {
			editor.setValue("RESOURCE", takenResource);
			editor.setValue("SEASONAL_OFFSET", "0.5");
			editor.setValue("GROWTH_VALUE", "0.25");
			editor.setValue("MINIMAP_COLOR", "10_20_30");
			editor.setValue("SPRITE.SPRITE", cropImage);
			editor.setValue("SPRITE.POLLEN", "1");
			editor.setValue("SPRITE.COLOR_POLLEN", "200_200_0");
			for (String part : List.of("SPRITE.STEM.", "SPRITE.GROWTH.")) {
				editor.setValue(part + "DEAD", "1_1_1");
				editor.setValue(part + "LIVE", "2_2_2");
				editor.setValue(part + "RIPE", "3_3_3");
				editor.setValue(part + "SHADOW_HEIGHT", "1");
				editor.setValue(part + "SHADOW_LENGTH", "2");
			}
		});
		expect("a resource another crop yields is refused: " + editor.problemFor("RESOURCE"), !editor.isSaveEnabled() && editor.problemFor("RESOURCE").contains("already yields"));
		SwingUtilities.invokeAndWait(() -> editor.setValue("RESOURCE", "TEST_GOOD"));
		expect("the new resource is fine, and Save opens: " + editor.messageText(), editor.isSaveEnabled());
		SwingUtilities.invokeAndWait(() -> editor.setValue("SPRITE.POLLEN", "11"));
		expect("a problem in a block shows on that tag's own row: " + editor.problemFor("SPRITE.POLLEN"), !editor.isSaveEnabled() && !editor.problemFor("SPRITE.POLLEN").isBlank());
		SwingUtilities.invokeAndWait(editor::goToFirstProblem);
		expect("Show first finds it inside the block: " + editor.selectedTag(), "SPRITE.POLLEN".equals(editor.selectedTag()));
		expect("its details say what it is: " + editor.detailsName(), editor.detailsName().contains("SPRITE.POLLEN") && editor.detailsText().stream().anyMatch(line -> line.contains("0 to 10")));
		SwingUtilities.invokeAndWait(() -> editor.setValue("SPRITE.POLLEN", "1"));
		saveComponent(editor.formComponent(), outDir.resolve("8-crop-form.png"));
		SwingUtilities.invokeAndWait(editor::save);
		waitFor(() -> !editor.isBusy() && screen.shownScreen().equals(MainScreen.HOME) && !screen.homeScreen().isLoading());
		Path cropFile = modRoot.resolve(version).resolve("assets/init/resource/growable/TEST_CROP.txt");
		expect("the crop was written", Files.isRegularFile(cropFile));
		expect("with no text file", !Files.exists(modRoot.resolve(version).resolve("assets/text/resource/growable")));
		expect("a crop alone isn't a producer: " + screen.homeScreen().producerWarningText(), screen.homeScreen().producerWarningText() != null);

		SwingUtilities.invokeAndWait(screen::showAddAsset);
		waitFor(() -> !screen.addAssetScreen().isBusy());
		SwingUtilities.invokeAndWait(() -> {
			screen.addAssetScreen().selectType("Farm");
			screen.addAssetScreen().setAssetName("GRAIN");
		});
		expect("a vanilla farm's name is refused: " + screen.addAssetScreen().nameProblemText(), screen.addAssetScreen().nameProblemText().contains("FARM_GRAIN"));
		SwingUtilities.invokeAndWait(() -> screen.addAssetScreen().setAssetName("TEST_FARM"));
		expect("the destination has the prefix the game needs: " + screen.addAssetScreen().destinationText(), screen.addAssetScreen().destinationText().contains("/assets/init/room/FARM_TEST_FARM.txt"));
		SwingUtilities.invokeAndWait(screen.addAssetScreen()::next);
		expect("the farm editor opens", screen.shownScreen().equals(MainScreen.ASSET_EDITOR) && editor.titleText().contains("Farm: TEST_FARM"));
		expect("WORK is in both files and has a row for each: " + editor.tagKeys(), editor.tagKeys().containsAll(List.of("WORK", "WORK (text file)", "INDUSTRY", "STATS", "GROWABLE", "INDOORS", "MINI_COLOR", "ICON", "NAME", "DESC")));
		expect("the farm's ghosts: " + editor.ghostKeys(), editor.ghostKeys().containsAll(List.of("RESOURCES", "MINI_COLOR_PATTERN", "BONUS", "EXPERIENCE_BONUS", "DEGRADE_RATE", "FLOOR")));
		expect("a ghost says what the game does", editor.ghostValue("DEGRADE_RATE").contains("0") && editor.ghostValue("FLOOR").contains("No floor"));
		expect("what has no default is add-only, with each file's named apart: " + editor.addableKeys(), editor.addableKeys().containsAll(List.of("UPGRADES", "ENVIRONMENT_EMIT", "UPGRADES (text file)")));
		expect("nothing unread is offered", editor.tagKeys().stream().noneMatch(key -> key.contains("REQUIRES")) && editor.ghostKeys().stream().noneMatch(key -> key.contains("REQUIRES")) && editor.addableKeys().stream().noneMatch(key -> key.contains("REQUIRES")));
		expect("WORK's optional tags are ghosted with the code's values", "1.0".equals(editor.nestedGhostValue("WORK.HEALTH_FACTOR")) && "1000".equals(editor.nestedGhostValue("WORK.LARGE_WORKFORCE")) && "0.0".equals(editor.nestedGhostValue("WORK.ACCIDENTS_PER_YEAR")));
		expect("the save waits for what's required: " + editor.messageText(), !editor.isSaveEnabled() && editor.messageText().contains("required"));
		expect("the farm's sounds start from vanilla's", "impact->Dig*".equals(editor.valueOf("SOUND.ROOM_WORK_")) && "DUMMY".equals(editor.valueOf("SOUND.ROOM_CLICK_")) && "DUMMY".equals(editor.valueOf("SOUND.ROOM_")));
		SwingUtilities.invokeAndWait(() -> editor.selectTag("SOUND.ROOM_WORK_"));
		expect("a sound's details name its entry and file: " + editor.detailsName() + " " + editor.detailsText(), editor.detailsName().equals("ROOM_WORK_FARM_TEST_FARM") && editor.detailsText().stream().anyMatch(line -> line.contains("audio/config/mono/FARM_TEST_FARM.txt")));
		save(screen, outDir.resolve("8-farm-empty.png"));
		SwingUtilities.invokeAndWait(() -> {
			editor.setValue("ICON", "24->resource->Gem->0");
			editor.setValue("GROWABLE", "TEST_CROP");
			editor.setValue("INDOORS", "false");
			editor.setValue("MINI_COLOR", "10_20_30");
			editor.setValue("WORK.SHIFT_OFFSET", "0.5");
			editor.setValue("NAME", "Test Farm");
			editor.setValue("DESC", "For testing.");
			editor.setValue("WORK.TITLE", "Tester");
			editor.setValue("WORK.VERB", "testing");
			for (int i = 0; i < 2; i++) {
				editor.setValue("STATS." + i + ".NAME", "Stat " + i);
				editor.setValue("STATS." + i + ".DESC", "About " + i);
			}
		});
		expect("empty OUT stops the save, and is named: " + editor.messageText(), !editor.isSaveEnabled());
		expect("nothing's written to the text or data file by mistake", "0.5".equals(editor.valueOf("WORK.SHIFT_OFFSET")) && "Tester".equals(editor.valueOf("WORK.TITLE")));
		SwingUtilities.invokeAndWait(() -> editor.setValue("INDUSTRY.OUT.WOOD.PLAYER", "1"));
		expect("an output that isn't the crop's warns, without blocking: " + editor.problemFor("INDUSTRY"), editor.isSaveEnabled() && editor.problemFor("INDUSTRY").contains("TEST_GOOD"));
		boolean[] duplicate = new boolean[1];
		SwingUtilities.invokeAndWait(() -> duplicate[0] = editor.addEntry("INDUSTRY.OUT", "WOOD"));
		expect("the same entry can't be added twice", !duplicate[0]);
		SwingUtilities.invokeAndWait(() -> {
			editor.removeEntries("INDUSTRY.OUT");
			editor.setValue("INDUSTRY.OUT.TEST_GOOD.PLAYER", "1");
			editor.setValue("INDUSTRY.OUT.TEST_GOOD.AI_RATE", "0.5");
			editor.setValue("INDUSTRY.IN.WOOD", "0.5");
		});
		expect("the crop's own output doesn't warn: " + editor.problemFor("INDUSTRY"), editor.problemFor("INDUSTRY").isBlank());
		SwingUtilities.invokeAndWait(() -> {
			editor.addTag("UPGRADES");
			editor.setValue("UPGRADES.0.BOOST", "1");
			editor.setValue("UPGRADES.0.RESOURCE_MASK", "1,1");
			editor.setValue("UPGRADES.1.BOOST", "2");
			editor.setValue("UPGRADES.1.RESOURCE_MASK", "2,2");
			editor.setValue("UPGRADES.2.BOOST", "3");
			editor.setValue("UPGRADES.2.RESOURCE_MASK", "3,3");
			editor.removeLevel("UPGRADES", 1);
		});
		expect("removing a level moves the later ones up", "3".equals(editor.valueOf("UPGRADES.1.BOOST")) && "1".equals(editor.valueOf("UPGRADES.0.BOOST")) && editor.valueOf("UPGRADES.2.BOOST") == null);
		SwingUtilities.invokeAndWait(() -> {
			editor.addTag("UPGRADES (text file)");
			editor.setValue("UPGRADES", "Better\nBest");
		});
		expect("text and data for one tag stay apart", "Better\nBest".equals(editor.valueOf("UPGRADES")) && "3".equals(editor.valueOf("UPGRADES.1.BOOST")));
		SwingUtilities.invokeAndWait(() -> {
			editor.addTag("MINI_COLOR_PATTERN");
			editor.setValue("MINI_COLOR_PATTERN", "1_1_1\n2_2_2\n3_3");
		});
		expect("uneven pattern rows are refused: " + editor.problemFor("MINI_COLOR_PATTERN"), !editor.isSaveEnabled() && editor.problemFor("MINI_COLOR_PATTERN").contains("same length"));
		SwingUtilities.invokeAndWait(() -> editor.setValue("MINI_COLOR_PATTERN", "1_1_1\n2_2_2"));
		SwingUtilities.invokeAndWait(() -> {
			editor.addTag("EXPERIENCE_BONUS");
			editor.setValue("EXPERIENCE_BONUS.MAX_EMPLOYEES", "100");
		});
		expect("a block that's been started needs the rest", !editor.isSaveEnabled());
		SwingUtilities.invokeAndWait(() -> editor.setValue("EXPERIENCE_BONUS.BONUS", "1.5"));
		expect("the whole farm can be saved: " + editor.messageText(), editor.isSaveEnabled());
		saveComponent(editor.formComponent(), outDir.resolve("8-farm-form.png"));
		SwingUtilities.invokeAndWait(editor::save);
		waitFor(() -> !editor.isBusy() && (screen.shownScreen().equals(MainScreen.HOME) && !screen.homeScreen().isLoading() || !editor.messageText().isBlank() && !editor.messageText().startsWith("Checking")));
		expect("the farm saved: " + editor.messageText(), screen.shownScreen().equals(MainScreen.HOME));
		Path farmFile = modRoot.resolve(version).resolve("assets/init/room/FARM_TEST_FARM.txt");
		Path farmText = modRoot.resolve(version).resolve("assets/text/room/FARM_TEST_FARM.txt");
		expect("both of the farm's files were written, with the prefix", Files.isRegularFile(farmFile) && Files.isRegularFile(farmText));
		expect("the data file holds the data: " + Files.readString(farmFile), Files.readString(farmFile).contains("SHIFT_OFFSET: 0.5") && !Files.readString(farmFile).contains("TITLE"));
		expect("the text file holds the text: " + Files.readString(farmText), Files.readString(farmText).contains("TITLE: \"Tester\"") && !Files.readString(farmText).contains("SHIFT_OFFSET") && Files.readString(farmText).contains("STATS: ["));
		expect("the farm clears the warning, through the screens: " + screen.homeScreen().producerWarningText(), screen.homeScreen().producerWarningText() == null);
		Path farmSounds = modRoot.resolve(version).resolve("assets/audio/config/mono/FARM_TEST_FARM.txt");
		Path farmAmbience = modRoot.resolve(version).resolve("assets/audio/config/ambience/FARM_TEST_FARM.txt");
		expect("its sounds were written: " + Files.readString(farmSounds), Files.readString(farmSounds).contains("ROOM_WORK_FARM_TEST_FARM: impact->Dig*") && Files.isRegularFile(farmAmbience));
		expect("so Home says nothing about them: " + screen.homeScreen().soundWarningText(), screen.homeScreen().soundWarningText() == null || !screen.homeScreen().soundWarningText().contains("TEST_FARM"));
		Files.delete(farmSounds);
		SwingUtilities.invokeAndWait(screen::showHome);
		waitFor(() -> !screen.homeScreen().isLoading());
		expect("taking them away makes Home warn: " + screen.homeScreen().soundWarningText(), screen.homeScreen().soundWarningText() != null && screen.homeScreen().soundWarningText().contains("ROOM_WORK_FARM_TEST_FARM"));
		save(screen, outDir.resolve("8-home-sound-warning.png"));
		expectNothingCutOff(screen, "home with both warnings");
		Files.delete(farmAmbience);
		deleteQuietlyForLaterChecks(modRoot.resolve(version).resolve("assets/audio"));
		Files.delete(farmFile);
		Files.delete(farmText);
		deleteQuietlyForLaterChecks(modRoot.resolve(version).resolve("assets/text/room"));
		SwingUtilities.invokeAndWait(screen::showHome);
		waitFor(() -> !screen.homeScreen().isLoading());
		expect("and it comes back with the farm gone", screen.homeScreen().producerWarningText() != null);
		deleteQuietlyForLaterChecks(producerRoom.getParent());
		SwingUtilities.invokeAndWait(screen::showSyntaxCheck);
		runCheck(screen, null, false);
		expect("the syntax check agrees with the new resource: " + screen.syntaxScreen().summaryText(), screen.syntaxScreen().summaryText().contains("No syntax errors"));
		SwingUtilities.invokeAndWait(screen::showHome);
		waitFor(() -> !screen.homeScreen().isLoading());
		deleteQuietlyForLaterChecks(modRoot.resolve(version).resolve("assets/init/resource"));
		deleteQuietlyForLaterChecks(modRoot.resolve(version).resolve("assets/text/resource"));
		SwingUtilities.invokeAndWait(screen::showHome);
		waitFor(() -> !screen.homeScreen().isLoading());
		expect("deleting the resource clears the warning", screen.homeScreen().producerWarningText() == null);

		audioConversion(screen, modRoot, outDir);
		normalMaps(screen, modRoot, outDir);

		racePortraitSim(screen, modRoot, version, outDir);
		mapSpriteSim(screen, modRoot, version, outDir);

		gameVersionDiff(screen, modRoot, version, outDir);

		wizardRace(screen, modRoot, outDir);

		// Every screen, at every width, in both themes.
		for (int theme = 0; theme < 2; theme++) {
			String themeName = Theme.current() == ToolkitSettings.ThemeChoice.DARK ? "dark" : "light";
			int[][] sizes = { { 1200, 760 }, { 1000, 700 }, { 720, 460 } };
			WidthBand[] bands = { WidthBand.WIDE, WidthBand.MEDIUM, WidthBand.NARROW };
			for (int i = 0; i < sizes.length; i++) {
				resize(screen, sizes[i][0], sizes[i][1], bands[i]);
				String tag = themeName + "-" + sizes[i][0];
				SwingUtilities.invokeAndWait(screen::showHome);
				waitFor(() -> !screen.homeScreen().isLoading());
				if (bands[i] == WidthBand.NARROW) {
					SwingUtilities.invokeAndWait(screen::clickMenuButton);
					expect("the menu button opens the menu", screen.menuOpenItems().equals(screen.menuItemNames()));
					saveMenu(screen, outDir.resolve("sweep-" + tag + "-menu.png"));
					SwingUtilities.invokeAndWait(screen::closeMenu);
					SwingUtilities.invokeAndWait(() -> screen.clickMenuItem("Mod Info"));
					expect("the menu goes where it says", screen.shownScreen().equals(MainScreen.MODIFY_INFO));
					waitFor(() -> !screen.modifyScreen().isBusy());
					SwingUtilities.invokeAndWait(() -> screen.clickMenuItem("Assets"));
					waitFor(() -> screen.shownScreen().equals(MainScreen.HOME) && !screen.homeScreen().isLoading());
				}
				expectNothingCutOff(screen, tag + " home");
				SwingUtilities.invokeAndWait(screen::showAudioConversion);
				waitFor(() -> !screen.audioScreen().isBusy());
				expectNothingCutOff(screen, tag + " audio");
				SwingUtilities.invokeAndWait(screen::showNormalMaps);
				waitFor(() -> !screen.normalMapsScreen().isBusy());
				expectNothingCutOff(screen, tag + " normal maps");
				SwingUtilities.invokeAndWait(screen::showRacePortraitSim);
				waitFor(() -> !screen.racePortraitSimScreen().isBusy());
				expectNothingCutOff(screen, tag + " race portrait sim");
				save(screen, outDir.resolve("sweep-" + tag + "-portrait.png"));
				SwingUtilities.invokeAndWait(screen::showMapSpriteSim);
				waitFor(() -> !screen.mapSpriteSimScreen().isBusy());
				expectNothingCutOff(screen, tag + " map sprite sim");
				save(screen, outDir.resolve("sweep-" + tag + "-map-sprite.png"));
				save(screen, outDir.resolve("sweep-" + tag + "-home.png"));
				SwingUtilities.invokeAndWait(screen::showAddAsset);
				waitFor(() -> !screen.addAssetScreen().isBusy());
				SwingUtilities.invokeAndWait(() -> {
					screen.addAssetScreen().selectType("Animal");
					screen.addAssetScreen().setAssetName("SWEEP");
				});
				expectNothingCutOff(screen, tag + " add asset");
				save(screen, outDir.resolve("sweep-" + tag + "-add.png"));
				SwingUtilities.invokeAndWait(screen.addAssetScreen()::next);
				SwingUtilities.invokeAndWait(() -> { });
				expectNothingCutOff(screen, tag + " editor");
				save(screen, outDir.resolve("sweep-" + tag + "-editor.png"));
				SwingUtilities.invokeAndWait(screen::showHome);
				waitFor(() -> !screen.homeScreen().isLoading());
				SwingUtilities.invokeAndWait(screen::showAddAsset);
				waitFor(() -> !screen.addAssetScreen().isBusy());
				SwingUtilities.invokeAndWait(() -> {
					screen.addAssetScreen().selectType("Resource");
					screen.addAssetScreen().setAssetName("SWEEP_GOOD");
					screen.addAssetScreen().next();
				});
				SwingUtilities.invokeAndWait(() -> { });
				expectNothingCutOff(screen, tag + " resource editor");
				save(screen, outDir.resolve("sweep-" + tag + "-resource.png"));
				SwingUtilities.invokeAndWait(screen::showHome);
				waitFor(() -> !screen.homeScreen().isLoading());
				SwingUtilities.invokeAndWait(screen::showAddAsset);
				waitFor(() -> !screen.addAssetScreen().isBusy());
				SwingUtilities.invokeAndWait(() -> {
					screen.addAssetScreen().selectType("Crop");
					screen.addAssetScreen().setAssetName("SWEEP_CROP");
					screen.addAssetScreen().next();
				});
				SwingUtilities.invokeAndWait(() -> { });
				expectNothingCutOff(screen, tag + " crop editor");
				saveComponent(editor.formComponent(), outDir.resolve("sweep-" + tag + "-crop.png"));
				SwingUtilities.invokeAndWait(screen::showHome);
				waitFor(() -> !screen.homeScreen().isLoading());
				SwingUtilities.invokeAndWait(screen::showAddAsset);
				waitFor(() -> !screen.addAssetScreen().isBusy());
				SwingUtilities.invokeAndWait(() -> {
					screen.addAssetScreen().selectType("Farm");
					screen.addAssetScreen().setAssetName("SWEEP_FARM");
					screen.addAssetScreen().next();
					// Filled in, because rows and levels are what could run out of room.
					editor.addTag("UPGRADES");
					editor.addTag("ENVIRONMENT_EMIT");
					editor.addTag("RESOURCES");
					editor.addTag("BONUS");
					editor.setValue("INDUSTRY.OUT.WOOD.PLAYER", "1");
					editor.setValue("INDUSTRY.IN.STONE", "0.5");
					editor.setValue("UPGRADES.0.BOOST", "1");
					editor.setValue("RESOURCES", "WOOD");
					editor.setValue("AREA_COSTS", "1");
				});
				SwingUtilities.invokeAndWait(() -> { });
				expectNothingCutOff(screen, tag + " farm editor");
				saveComponent(editor.formComponent(), outDir.resolve("sweep-" + tag + "-farm.png"));
				editor.setDiscardPrompt(question -> true);
				SwingUtilities.invokeAndWait(screen::showHome);
				waitFor(() -> !screen.homeScreen().isLoading());
				SwingUtilities.invokeAndWait(screen::showModifyInfo);
				waitFor(() -> !screen.modifyScreen().isBusy());
				expectNothingCutOff(screen, tag + " modify info");
				save(screen, outDir.resolve("sweep-" + tag + "-modify.png"));
				SwingUtilities.invokeAndWait(screen::showHome);
				waitFor(() -> !screen.homeScreen().isLoading());
				SwingUtilities.invokeAndWait(screen::showSyntaxCheck);
				expectNothingCutOff(screen, tag + " check syntax");
				save(screen, outDir.resolve("sweep-" + tag + "-syntax.png"));
				SwingUtilities.invokeAndWait(screen::showGameVersionDiff);
				waitFor(() -> !screen.gameVersionDiffScreen().isBusy());
				expectNothingCutOff(screen, tag + " game version diff");
				save(screen, outDir.resolve("sweep-" + tag + "-diff.png"));
				SwingUtilities.invokeAndWait(screen::showWizard);
				waitFor(() -> !screen.wizardScreen().isBusy());
				SwingUtilities.invokeAndWait(() -> screen.wizardScreen().clickContinue(0));
				waitFor(() -> !screen.wizardScreen().isBusy() && screen.wizardScreen().questionKind().equals("fields"));
				SwingUtilities.invokeAndWait(() -> { });
				expectNothingCutOff(screen, tag + " wizard");
				double share = screen.wizardScreen().widestBubbleShare();
				expect("no bubble is wider than its share of the column at " + tag + ": " + share, share <= screen.wizardScreen().bubbleLimit() + 0.001);
				save(screen, outDir.resolve("sweep-" + tag + "-wizard.png"));
				SwingUtilities.invokeAndWait(screen::showHome);
				waitFor(() -> !screen.homeScreen().isLoading());
			}
			SwingUtilities.invokeAndWait(screen::toggleTheme);
		}
		resize(screen, 1200, 760, WidthBand.WIDE);
		wizardPortrait(screen, modRoot, outDir);
		removeWizardFiles(modRoot);

		SwingUtilities.invokeAndWait(screen::clickRailOpenFolder);
		waitFor(() -> !OPENED.isEmpty());
		expect("Open Folder opens the mod's folder: " + OPENED, OPENED.get(0).equals(modRoot));
		OPEN_FAILS[0] = true;
		SwingUtilities.invokeAndWait(screen.homeScreen()::openModFolder);
		waitFor(() -> screen.homeScreen().statusText().contains("couldn't be opened"));
		OPEN_FAILS[0] = false;
		expect("the selected folder is unchanged", new ToolkitSettings(settingsFile).currentMod().equals(Optional.of("My New Mod")));

		// A large real mod's tree scrolls.
		if (args.length > 3) {
			Path big = Paths.get(args[3]);
			copyInto(big, mods.resolve("Big Mod"));
			SwingUtilities.invokeAndWait(screen::showChooseMod);
			waitFor(() -> !screen.chooseScreen().isLoading());
			SwingUtilities.invokeAndWait(() -> {
				screen.chooseScreen().select("Big Mod");
				screen.chooseScreen().chooseSelected();
			});
			waitFor(() -> screen.shownScreen().equals(MainScreen.HOME) && !screen.homeScreen().isLoading());
			System.out.println("big mod: " + screen.homeScreen().assetsSummaryText() + " rows=" + screen.homeScreen().treeRowCount());
			expect("a big mod's tree is fully shown", screen.homeScreen().treeRowCount() > 400);
			save(screen, outDir.resolve("6d-home-big.png"));
			SwingUtilities.invokeAndWait(screen::showModifyInfo);
			waitFor(() -> !screen.modifyScreen().isBusy());
			save(screen, outDir.resolve("6e-modify-real.png"));
			SwingUtilities.invokeAndWait(() -> {
				screen.chooseScreen().refresh("My New Mod");
			});
			waitFor(() -> !screen.chooseScreen().isLoading());
			SwingUtilities.invokeAndWait(() -> screen.chooseScreen().chooseSelected());
			waitFor(() -> screen.shownScreen().equals(MainScreen.HOME) && !screen.homeScreen().isLoading());
		}

		// Check syntax opens on the selected mod.
		SwingUtilities.invokeAndWait(screen::showSyntaxCheck);
		runCheck(screen, null, false);
		System.out.println("new mod check: " + screen.syntaxScreen().summaryText());
		expect("a new mod's info file has no syntax errors: " + screen.syntaxScreen().summaryText(), screen.syntaxScreen().summaryText().contains("Checked 1 of 1") && screen.syntaxScreen().summaryText().contains("No syntax errors"));
		runCheck(screen, checkFolder, false);
		System.out.println("check: " + screen.syntaxScreen().summaryText() + " rows=" + screen.syntaxScreen().resultCount());
		save(screen, outDir.resolve("7-check.png"));

		// A restart goes straight to the mod's home.
		close(screen);
		MainScreen restarted = open(settingsFile, mods, new GameFolderFinder.Result(Optional.empty(), List.of()), Optional.empty());
		expect("a restart opens the mod's home", restarted.shownScreen().equals(MainScreen.HOME));
		expect("a restart remembers the mod", restarted.currentMod().isPresent() && restarted.currentMod().get().folderName().equals("My New Mod"));
		waitFor(() -> !restarted.homeScreen().isLoading());

		editingExisting(restarted, mods.resolve("My New Mod"), game, gameVersion, outDir);
		addingMine(restarted, mods.resolve("My New Mod"), gameVersion, outDir);
		addingRace(restarted, mods.resolve("My New Mod"), game, gameVersion, outDir);
		connectingAnAgent(restarted, outDir);

		// Changing the mod lists it, with the current one selected.
		ModCreator.create(mods, game, "Another Mod");
		SwingUtilities.invokeAndWait(restarted::showChooseMod);
		waitFor(() -> !restarted.chooseScreen().isLoading());
		expect("all mods are listed: " + restarted.chooseScreen().modCount(), restarted.chooseScreen().modCount() >= 2);
		expect("the current mod is selected", restarted.chooseScreen().selectedMod() != null && restarted.chooseScreen().selectedMod().folderName().equals("My New Mod"));
		save(restarted, outDir.resolve("8-choose-mod.png"));

		// The options list can return to the current mod.
		SwingUtilities.invokeAndWait(restarted::showStart);
		expect("the options still show", restarted.shownScreen().equals(MainScreen.START));
		save(restarted, outDir.resolve("8a-start-with-mod.png"));
		close(restarted);

		// A saved mod that has gone is cleared, and the app says so.
		try (java.util.stream.Stream<Path> walk = Files.walk(mods.resolve("My New Mod"))) {
			walk.sorted(java.util.Comparator.reverseOrder()).forEach(GuiSmoke::delete);
		}
		MainScreen afterDelete = open(settingsFile, mods, new GameFolderFinder.Result(Optional.empty(), List.of()), Optional.empty());
		expect("a deleted mod opens the options", afterDelete.shownScreen().equals(MainScreen.START));
		expect("a deleted mod is no longer selected", afterDelete.currentMod().isEmpty());
		expect("a deleted mod is cleared from settings", new ToolkitSettings(settingsFile).currentMod().isEmpty());
		save(afterDelete, outDir.resolve("9-mod-gone.png"));
		close(afterDelete);

		// The automatic search finds nothing, so it falls back on selecting by hand.
		Path settings2 = temp.resolve("settings2.properties");
		MainScreen fallback = open(settings2, mods, new GameFolderFinder.Result(Optional.empty(), List.of(game, game)), Optional.of(game));
		SwingUtilities.invokeAndWait(fallback::checkForGameAutomatically);
		waitFor(() -> fallback.shownScreen().equals(MainScreen.START));
		expect("the fallback's choice is saved", new ToolkitSettings(settings2).gameFolder().equals(Optional.of(game)));
		close(fallback);

		// The automatic search finds nothing, and the user cancels the picker.
		Path settings3 = temp.resolve("settings3.properties");
		MainScreen cancelled = open(settings3, mods, new GameFolderFinder.Result(Optional.empty(), List.of(game, game)), Optional.empty());
		SwingUtilities.invokeAndWait(cancelled::checkForGameAutomatically);
		waitFor(() -> cancelled.findMessageText().contains("wasn't found"));
		expect("cancelling stays on the question", cancelled.shownScreen().equals(MainScreen.FIND_GAME));
		save(cancelled, outDir.resolve("10-not-found.png"));
		close(cancelled);

		// A saved folder that has gone asks again.
		Path settings4 = temp.resolve("settings4.properties");
		new ToolkitSettings(settings4).setGameFolder(temp.resolve("gone"));
		MainScreen gone = open(settings4, mods, new GameFolderFinder.Result(Optional.empty(), List.of()), Optional.empty());
		expect("a saved folder that's gone asks again", gone.shownScreen().equals(MainScreen.FIND_GAME));
		close(gone);

		System.out.println(failed == 0 ? "GUI smoke passed" : "GUI smoke FAILED: " + failed);
		System.exit(failed == 0 ? 0 : 1);
	}

	private static void openFromHome(MainScreen screen, Path file) throws Exception {
		SwingUtilities.invokeAndWait(screen::showHome);
		waitFor(() -> !screen.homeScreen().isLoading());
		SwingUtilities.invokeAndWait(() -> screen.homeScreen().openFile(file));
		waitFor(() -> !screen.isOpeningAsset());
		SwingUtilities.invokeAndWait(() -> { });
	}

	private static int labelsCalled(java.awt.Container in, String text) {
		int found = 0;
		for (java.awt.Component child : in.getComponents()) {
			if (child instanceof javax.swing.JLabel label && text.equals(label.getText())) {
				found++;
			} else if (child instanceof java.awt.Container inner) {
				found += labelsCalled(inner, text);
			}
		}
		return found;
	}

	/** A vanilla refiner's recipes, floors and stats on the form. */
	private static void refinerForm(MainScreen screen, Path assets, Path game, Path outDir) throws Exception {
		Path refiner = assets.resolve("init/room/REFINER_SMOKEBREW.txt");
		Files.createDirectories(refiner.getParent());
		try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(game.resolve("base").resolve("data.zip").toFile());
				java.io.InputStream in = zip.getInputStream(zip.getEntry("data/assets/init/room/REFINER_BREWERY.txt"))) {
			Files.copy(in, refiner);
		}
		openFromHome(screen, refiner);
		AssetEditorScreen editor = screen.assetEditorScreen();
		expect("a refiner opens: " + editor.titleText(), editor.titleText().equals("Edit Refiner: SMOKEBREW"));
		expect("with its floors: " + editor.valueOf("FLOOR"), "DIRT,STONE_BRICK,STONE_MEDIUM_DARK".equals(editor.valueOf("FLOOR")));
		expect("and each of its recipes", editor.valueOf("INDUSTRIES.0.INDUSTRY.OUT.ALCO_BEER.PLAYER") != null && editor.valueOf("INDUSTRIES.1.INDUSTRY.OUT.ALCO_WINE.PLAYER") != null);
		expect("it can be saved as it is", editor.isSaveEnabled());
		expect("only its 4 resources ask for an amount, and its floors don't: " + labelsCalled(editor, "Amount"), labelsCalled(editor, "Amount") == 4);
		save(screen, outDir.resolve("20a-refiner.png"));
		SwingUtilities.invokeAndWait(() -> {
			if (editor.confirmLeave()) {
				screen.showHome();
			}
		});
		waitFor(() -> !screen.homeScreen().isLoading());
		Files.delete(refiner);
	}

	/** Opens an asset from Home's tree, generates what it's missing, and saves over it. */
	private static void editingExisting(MainScreen screen, Path modRoot, Path game, GameParser.GameVersion version, Path outDir) throws Exception {
		Path assets = modRoot.resolve("V" + version.major()).resolve("assets");
		refinerForm(screen, assets, game, outDir);
		Path farm = assets.resolve("init/room/FARM_SMOKEEDIT.txt");
		Path text = assets.resolve("text/room/FARM_SMOKEEDIT.txt");
		Path ambience = assets.resolve("audio/config/ambience/FARM_SMOKEEDIT.txt");
		Path mono = assets.resolve("audio/config/mono/FARM_SMOKEEDIT.txt");
		Files.createDirectories(farm.getParent());
		try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(game.resolve("base").resolve("data.zip").toFile());
				java.io.InputStream in = zip.getInputStream(zip.getEntry("data/assets/init/room/FARM_GRAIN.txt"))) {
			Files.copy(in, farm);
		}
		openFromHome(screen, farm);
		expect("double-clicking an asset opens the editor: " + screen.homeScreen().openProblemText(), screen.shownScreen().equals(MainScreen.ASSET_EDITOR));
		AssetEditorScreen editor = screen.assetEditorScreen();
		expect("it says it's editing: " + editor.titleText(), editor.titleText().equals("Edit Farm: SMOKEEDIT"));
		expect("it's filled in: " + editor.valueOf("GROWABLE"), "GRAIN".equals(editor.valueOf("GROWABLE")));
		expect("nested values are filled in: " + editor.valueOf("WORK.SHIFT_OFFSET"), "0.125".equals(editor.valueOf("WORK.SHIFT_OFFSET")));
		expect("a block ICON is filled in, not kept: " + editor.keptRowKeys(), !editor.keptRowKeys().contains("ICON") && editor.valueOf("ICON.BG") != null && editor.valueOf("ICON") == null);
		expect("what it leaves out is ghosted with its default: " + editor.nestedGhostValue("ICON.SCALE"), "1".equals(editor.nestedGhostValue("ICON.SCALE")));
		SwingUtilities.invokeAndWait(() -> editor.setValue("ICON", "24->resource->Grain->0"));
		expect("changing to one icon leaves only that: " + editor.valueOf("ICON") + " " + editor.valueOf("ICON.BG"), "24->resource->Grain->0".equals(editor.valueOf("ICON")) && editor.valueOf("ICON.BG") == null);
		expect("and shows the sheet's positions: " + editor.iconPositionText("ICON"), editor.iconPositionText("ICON").matches("Position \\(0( to \\d+)?\\)"));
		SwingUtilities.invokeAndWait(() -> editor.setValue("ICON.BG", "32->BG->3"));
		expect("changing back works too: " + editor.valueOf("ICON.BG") + " " + editor.valueOf("ICON"), "32->BG->3".equals(editor.valueOf("ICON.BG")) && editor.valueOf("ICON") == null);
		save(screen, outDir.resolve("21a-icon-block.png"));
		// A list of blocks isn't offered, so it has a kept row.
		SwingUtilities.invokeAndWait(() -> {
			if (editor.confirmLeave()) {
				screen.showHome();
			}
		});
		waitFor(() -> !screen.homeScreen().isLoading());
		String asBlock = Files.readString(farm);
		Files.writeString(farm, asBlock.replaceFirst("ICON\\s*:\\s*\\{[^}]*\\},?", "ICON: [\n\t{\n\t\tBG: 32->BG->3,\n\t},\n],\n"));
		openFromHome(screen, farm);
		expect("a list of icons has a kept row: " + editor.keptRowKeys(), editor.keptRowKeys().contains("ICON"));
		expect("comments are mentioned: " + editor.noteTexts(), editor.noteTexts().stream().anyMatch(note -> note.contains("comments")));
		expect("a missing text file is offered", editor.isGenerateTextOffered());
		expect("missing sounds are offered", editor.isGenerateSoundsOffered());
		expect("text tags aren't asked for yet: " + editor.tagKeys(), !editor.tagKeys().contains("NAME"));
		expect("it can be saved as it is", editor.isSaveEnabled());
		boolean[] asked = new boolean[1];
		editor.setDiscardPrompt(question -> {
			asked[0] = true;
			return false;
		});
		expect("leaving with no changes doesn't ask", editor.confirmLeave() && !asked[0]);

		for (int themeTurn = 0; themeTurn < 2; themeTurn++) {
			int[][] sizes = { { 1200, 760 }, { 1000, 700 }, { 720, 460 } };
			WidthBand[] bands = { WidthBand.WIDE, WidthBand.MEDIUM, WidthBand.NARROW };
			for (int i = 0; i < sizes.length; i++) {
				resize(screen, sizes[i][0], sizes[i][1], bands[i]);
				expectNothingCutOff(screen, "edit form, theme " + themeTurn + ", " + bands[i]);
				saveComponent(editor.formComponent(), outDir.resolve("20-edit-" + themeTurn + "-" + bands[i] + ".png"));
			}
			SwingUtilities.invokeAndWait(screen::toggleTheme);
		}
		resize(screen, 1200, 760, WidthBand.WIDE);

		SwingUtilities.invokeAndWait(editor::clickGenerateText);
		SwingUtilities.invokeAndWait(editor::clickGenerateSounds);
		expect("generating adds the text tags: " + editor.tagKeys(), editor.tagKeys().contains("NAME") && editor.tagKeys().contains("DESC"));
		expect("the offer goes away", !editor.isGenerateTextOffered() && !editor.isGenerateSoundsOffered());
		expect("and adds the sounds", editor.isSoundEditable("SOUND.ROOM_WORK_"));
		expect("a generated file must be filled in first", !editor.isSaveEnabled());
		expect("nothing is written yet", !Files.exists(text) && !Files.exists(mono));
		SwingUtilities.invokeAndWait(() -> { });
		saveComponent(editor.formComponent(), outDir.resolve("21-edit-generated.png"));
		expectNothingCutOff(screen, "edit form with generated sections");
		expect("leaving now asks first", !editor.confirmLeave() && asked[0]);

		SwingUtilities.invokeAndWait(() -> {
			editor.setValue("NAME", "Edited Farm");
			editor.setValue("DESC", "Edited through the form.");
			editor.setValue("WORK.TITLE", "Tester");
			editor.setValue("WORK.VERB", "testing");
			for (int i = 0; i < 2; i++) {
				editor.setValue("STATS." + i + ".NAME", "Stat " + i);
				editor.setValue("STATS." + i + ".DESC", "About stat " + i);
			}
			editor.setValue("SOUND.ROOM_", "DUMMY");
			editor.setValue("SOUND.ROOM_CLICK_", "DUMMY");
			editor.setValue("SOUND.ROOM_WORK_", "DUMMY");
			editor.setValue("INDOORS", "true");
		});
		expect("once filled in, it can be saved: " + editor.messageText(), editor.isSaveEnabled());
		SwingUtilities.invokeAndWait(editor::save);
		waitFor(() -> !editor.isBusy());
		waitFor(() -> screen.shownScreen().equals(MainScreen.HOME));
		waitFor(() -> !screen.homeScreen().isLoading());
		expect("the value is saved over the old one: " + Files.readString(farm), Files.readString(farm).contains("INDOORS: true,"));
		expect("what the form can't show is still there: " + Files.readString(farm), Files.readString(farm).contains("BG: 32->BG->3") && Files.readString(farm).contains("USES_TOOL: true"));
		expect("the text file is written", Files.readString(text).contains("Edited Farm"));
		expect("the sound files are written", Files.readString(mono).contains("ROOM_WORK_FARM_SMOKEEDIT: DUMMY,") && Files.readString(ambience).contains("ROOM_FARM_SMOKEEDIT: DUMMY,"));
		expect("Home's warning clears for it: " + screen.homeScreen().soundWarningText(), screen.homeScreen().soundWarningText() == null || !screen.homeScreen().soundWarningText().contains("SMOKEEDIT"));
		save(screen, outDir.resolve("22-home-after-edit.png"));

		openFromHome(screen, text);
		expect("its text file opens the same asset: " + screen.homeScreen().openProblemText(), screen.shownScreen().equals(MainScreen.ASSET_EDITOR) && editor.titleText().equals("Edit Farm: SMOKEEDIT"));
		expect("nothing is offered now", !editor.isGenerateTextOffered() && !editor.isGenerateSoundsOffered());
		expect("its own sounds are editable", editor.isSoundEditable("SOUND.ROOM_WORK_"));
		expect("the saved value came back", "true".equals(editor.valueOf("INDOORS")) && "Edited Farm".equals(editor.valueOf("NAME")));
		SwingUtilities.invokeAndWait(() -> editor.clickReplaceKept("ICON"));
		expect("replacing a kept value gives the normal row: " + editor.tagKeys(), editor.tagKeys().contains("ICON") && !editor.keptRowKeys().contains("ICON"));
		expect("which then needs a value", !editor.isSaveEnabled());
		editor.setDiscardPrompt(question -> true);
		SwingUtilities.invokeAndWait(() -> {
			if (editor.confirmLeave()) {
				screen.showHome();
			}
		});
		waitFor(() -> !screen.homeScreen().isLoading());

		openFromHome(screen, mono);
		expect("a sound file stays on Home, and says why: " + screen.homeScreen().openProblemText(), screen.shownScreen().equals(MainScreen.HOME) && screen.homeScreen().openProblemText().contains("isn't an asset"));
		save(screen, outDir.resolve("23-home-open-problem.png"));
		for (Path file : List.of(farm, text, mono, ambience)) {
			Files.delete(file);
		}
		SwingUtilities.invokeAndWait(screen::showHome);
		waitFor(() -> !screen.homeScreen().isLoading());
	}

	/** A small handbook in the real file's form. The words are only for this test. */
	private static String handbookFixture(String version) {
		String page = HandbookIndex.SITE + "compendium/animal/";
		return "{\"schema\":1,\"game_version\":\"" + version + "\",\"content_hash\":\"" + version + "\",\"locations\":{\"assets/init/animal/\":{\"tags\":{"
				+ "\"MASS\":{\"description\":\"How heavy it is:\\n\\n- Yield.\",\"example\":\"MASS: 110,\",\"required\":\"Yes\",\"url\":\"" + page + "#mass\"},"
				+ "\"CLIMATE\":{\"description\":\"How common it is.\",\"example\":\"CLIMATE: {\\n\\tCOLD: 0.8,\\n},\",\"required\":\"No\",\"url\":\"" + page + "#climate\"},"
				+ "\"COLOR\":{\"description\":\"Not confirmed.\",\"unverified\":true,\"url\":\"https://example.com/elsewhere\"},"
				+ "\"PACK\":{\"description\":\"<html><img src=http://example.invalid/x.png>Packs.\",\"summary\":\"<html><img src=http://example.invalid/x.png>Packs.\",\"required\":\"Yes\",\"url\":\"" + page + "#pack\"},"
				+ "\"GRAZES\":{\"description\":\"Whether it grazes, which nothing uses, and this description is deliberately long enough that it has to wrap onto more than one line in the editor, so the layout of wrapped text gets looked at too.\",\"required\":\"Yes\",\"effect\":\"None\",\"url\":\"" + page + "#grazes\"}"
				+ "},\"text_tags\":{"
				+ "\"NAME\":{\"description\":\"The singular name.\",\"required\":\"Yes\",\"url\":\"" + page + "#name\"}"
				+ "}}}}";
	}

	/** The rail's normal maps screen: only PNGs, a sheet shown at its real size, and the questions it must ask. */
	private static void normalMaps(MainScreen screen, Path modRoot, Path outDir) throws Exception {
		Path version;
		try (java.util.stream.Stream<Path> children = Files.list(modRoot)) {
			version = children.filter(child -> child.getFileName().toString().matches("V\\d+")).findFirst().orElseThrow();
		}
		Path folder = version.resolve("assets/sprite/race");
		Files.createDirectories(folder);
		java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(448, 546, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		for (int y = 10; y < 26; y++) {
			for (int x = 10; x < 24; x++) {
				image.setRGB(x, y, 0xFFC0A080);
			}
		}
		javax.imageio.ImageIO.write(image, "png", folder.resolve("GuiFolk.png").toFile());
		Path icons = version.resolve("assets/sprite/icon");
		Files.createDirectories(icons);
		javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB), "png", icons.resolve("GuiIcon.png").toFile());
		Files.writeString(icons.resolve("notes.txt"), "not an image");
		try {
			syx.gui.NormalMapsScreen normals = screen.normalMapsScreen();
			SwingUtilities.invokeAndWait(() -> screen.clickRail("Normal Maps"));
			waitFor(() -> !normals.isBusy());
			expect("it's marked in the rail", "Normal Maps".equals(screen.railHighlight()));
			expect("the tree lists only PNGs: " + normals.treeRows(), normals.treeRows().contains("GuiFolk.png") && normals.treeRows().contains("GuiIcon.png") && !normals.treeRows().contains("notes.txt"));
			expect("nothing can be generated before a sheet is chosen", !normals.canGenerate() && !normals.canTrim());
			SwingUtilities.invokeAndWait(() -> normals.select("GuiIcon.png"));
			waitFor(() -> !normals.isBusy());
			expect("another kind of PNG is shown but can't be generated for: " + normals.statusText(), new java.awt.Dimension(32, 32).equals(normals.shownSize()) && !normals.canGenerate() && !normals.canTrim() && normals.statusText().contains("directly"));
			SwingUtilities.invokeAndWait(() -> normals.select("GuiFolk.png"));
			waitFor(() -> !normals.isBusy());
			expect("a race sheet is shown at full size: " + normals.shownSize(), new java.awt.Dimension(448, 546).equals(normals.shownSize()) && normals.canGenerate() && normals.canTrim());
			List<String> asked = new java.util.ArrayList<>();
			SwingUtilities.invokeAndWait(() -> normals.answerQuestions(false, asked));
			SwingUtilities.invokeAndWait(normals::clickGenerate);
			waitFor(() -> !normals.isBusy());
			expect("a sheet with no normals generates without a question: " + normals.statusText(), asked.isEmpty() && normals.statusText().contains("Generated normals"));
			save(screen, outDir.resolve("normal-maps.png"));
			expectNothingCutOff(screen, "normal maps");
			byte[] generated = Files.readAllBytes(folder.resolve("GuiFolk.png"));
			expect("each section has a row: " + normals.sectionLabels(), normals.sectionLabels().contains("Torso") && normals.sectionLabels().contains("Addon 0"));
			int before = normals.shownPixel(224 + 10, 10);
			SwingUtilities.invokeAndWait(() -> normals.setShading("feet", 200, 100));
			waitFor(() -> !normals.isBusy());
			expect("changing a section previews it and writes nothing: " + normals.statusText(), normals.shownPixel(224 + 10, 10) != before && normals.statusText().contains("Preview") && java.util.Arrays.equals(generated, Files.readAllBytes(folder.resolve("GuiFolk.png"))));
			save(screen, outDir.resolve("normal-maps-preview.png"));
			SwingUtilities.invokeAndWait(normals::clickReset);
			waitFor(() -> !normals.isBusy());
			expect("Reset goes back to the defaults", normals.sectionStrength("feet") == 100 && normals.shownPixel(224 + 10, 10) == before);
			SwingUtilities.invokeAndWait(normals::clickGenerate);
			waitFor(() -> !normals.isBusy());
			expect("replacing asks first, and No leaves the file: " + asked, asked.size() == 1 && asked.get(0).contains("already has normals") && java.util.Arrays.equals(generated, Files.readAllBytes(folder.resolve("GuiFolk.png"))));
			SwingUtilities.invokeAndWait(normals::clickClearAll);
			waitFor(() -> !normals.isBusy());
			expect("clearing always asks, and No leaves the file: " + asked, asked.size() == 2 && asked.get(1).contains("Clear every normal") && java.util.Arrays.equals(generated, Files.readAllBytes(folder.resolve("GuiFolk.png"))));
			SwingUtilities.invokeAndWait(() -> normals.answerQuestions(true, asked));
			SwingUtilities.invokeAndWait(normals::clickTrim);
			waitFor(() -> !normals.isBusy());
			expect("trimming doesn't ask: " + normals.statusText(), asked.size() == 2);
			SwingUtilities.invokeAndWait(normals::clickClearAll);
			waitFor(() -> !normals.isBusy());
			expect("Yes clears them: " + normals.statusText(), asked.size() == 3 && normals.statusText().contains("Cleared the normals"));
			SwingUtilities.invokeAndWait(() -> normals.setShading("feet", 60, 100));
			waitFor(() -> !normals.isBusy());
			SwingUtilities.invokeAndWait(normals::clickGenerate);
			waitFor(() -> !normals.isBusy());
			SwingUtilities.invokeAndWait(() -> normals.select("GuiIcon.png"));
			waitFor(() -> !normals.isBusy());
			expect("another kind of PNG has no sections", normals.sectionLabels().isEmpty());
			SwingUtilities.invokeAndWait(() -> normals.select("GuiFolk.png"));
			waitFor(() -> !normals.isBusy());
			expect("a generated sheet keeps its values when opened again: " + normals.sectionStrength("feet"), normals.sectionStrength("feet") == 60);
		} finally {
			deleteQuietlyForLaterChecks(folder);
			deleteQuietlyForLaterChecks(icons);
		}
	}

	/** The rail's audio screen: an empty mod, a ghosted .ogg, and one conversion whose status names where the original went. */
	private static void audioConversion(MainScreen screen, Path modRoot, Path outDir) throws Exception {
		SwingUtilities.invokeAndWait(screen::showAudioConversion);
		waitFor(() -> !screen.audioScreen().isBusy());
		expect("an empty mod says so: " + screen.audioScreen().treeRows(), screen.audioScreen().isEmpty() && !screen.audioScreen().canConvert());
		expect("and it's marked in the rail", "Audio Conversion".equals(screen.railHighlight()));
		Path sounds = modRoot.resolve("sounds");
		Files.createDirectories(sounds);
		writeTone(sounds.resolve("horn.wav"));
		Files.write(sounds.resolve("done.ogg"), new byte[] { 'O', 'g', 'g', 'S' });
		String previous = System.getProperty("jpackage.app-path");
		Path app = modRoot.getParent().resolveSibling("audio-app");
		Files.createDirectories(app);
		try {
			System.setProperty("jpackage.app-path", app.resolve("Syx Toolkit.exe").toString());
			SwingUtilities.invokeAndWait(screen::showAudioConversion);
			waitFor(() -> !screen.audioScreen().isBusy());
			expect("the tree lists both: " + screen.audioScreen().treeRows(), screen.audioScreen().treeRows().contains("horn.wav") && screen.audioScreen().treeRows().contains("done.ogg"));
			expect("the ogg is ghosted and the wav is not", screen.audioScreen().isGhosted("done.ogg") && !screen.audioScreen().isGhosted("horn.wav"));
			SwingUtilities.invokeAndWait(() -> screen.audioScreen().select("done.ogg"));
			expect("an ogg can't be converted: " + screen.audioScreen().statusText(), !screen.audioScreen().canConvert() && screen.audioScreen().statusText().contains("already an .ogg"));
			SwingUtilities.invokeAndWait(() -> screen.audioScreen().select("horn.wav"));
			expect("a wav can", screen.audioScreen().canConvert());
			SwingUtilities.invokeAndWait(screen.audioScreen()::clickConvert);
			waitFor(() -> !screen.audioScreen().isBusy());
			expect("the status gives the path it moved to: " + screen.audioScreen().statusText(), screen.audioScreen().statusText().contains("Converted Audio Files") && screen.audioScreen().statusText().contains("horn.wav"));
			expect("the tree drops the wav and ghosts the new ogg: " + screen.audioScreen().treeRows(), !screen.audioScreen().treeRows().contains("horn.wav") && screen.audioScreen().isGhosted("horn.ogg"));
			save(screen, outDir.resolve("audio-conversion.png"));
			expectNothingCutOff(screen, "audio conversion");
			deleteQuietlyForLaterChecks(sounds);
		} finally {
			if (previous == null) {
				System.clearProperty("jpackage.app-path");
			} else {
				System.setProperty("jpackage.app-path", previous);
			}
		}
	}

	private static void writeTone(Path file) throws Exception {
		javax.sound.sampled.AudioFormat format = new javax.sound.sampled.AudioFormat(22050, 16, 1, true, false);
		byte[] silence = new byte[2205 * 2];
		javax.sound.sampled.AudioSystem.write(new javax.sound.sampled.AudioInputStream(new java.io.ByteArrayInputStream(silence), format, silence.length / 2), javax.sound.sampled.AudioFileFormat.Type.WAVE, file.toFile());
	}

	/** A race on the game's file, Copy to Mod, live text, a quiet outline that clears, and a save. */
	private static void racePortraitSim(MainScreen screen, Path modRoot, String version, Path outDir) throws Exception {
		Path races = modRoot.resolve(version).resolve("assets/init/race");
		Files.createDirectories(races);
		Files.writeString(races.resolve("SIMRACE.txt"), "SPRITE_FILE: HUMAN,\nOTHER: 1,\n");
		SwingUtilities.invokeAndWait(() -> screen.clickRail("Race Portrait Sim"));
		expect("the rail opens the sim", screen.shownScreen().equals(MainScreen.RACE_PORTRAIT_SIM));
		expect("and it's marked in the rail", "Race Portrait Sim".equals(screen.railHighlight()));
		syx.gui.RacePortraitSimScreen sim = screen.racePortraitSimScreen();
		waitFor(() -> !sim.isBusy() && sim.hasPortrait());
		expect("the races are listed: " + sim.raceNames(), sim.raceNames().contains("SIMRACE"));
		expect("a race on the game's file is locked, with Copy to Mod", sim.isEditorLocked() && sim.canCopy() && !sim.canSave());
		expect("its tags are still shown", sim.editorText().contains("COLOR_BLOOD"));
		sim.setCopyPrompt(question -> true);
		SwingUtilities.invokeAndWait(sim::clickCopy);
		waitFor(() -> !sim.isBusy() && !sim.isEditorLocked());
		expect("Copy to Mod makes it editable: " + sim.statusText(), !sim.isEditorLocked() && sim.statusText().contains("Copied"));
		Path own = races.resolve("sprite/SIMRACE.txt");
		expect("and wrote the race's own file", Files.isRegularFile(own));
		String original = sim.editorText();
		byte[] before = Files.readAllBytes(own);
		SwingUtilities.invokeAndWait(() -> sim.typeInEditor(original.replaceFirst("COLOR_BLOOD:", "COLOR_BLOOD ")));
		waitFor(() -> !sim.isBusy() && sim.isRed());
		expect("broken text turns the preview red, and says why: " + sim.reasonText(), sim.isRed() && sim.reasonText().contains("missing its ':'"));
		expect("the last good portrait stays up", sim.hasPortrait());
		expect("Save is blocked", !sim.canSave());
		save(screen, outDir.resolve("race-portrait-sim-red.png"));
		expectNothingCutOff(screen, "race portrait sim, red");
		SwingUtilities.invokeAndWait(() -> sim.typeInEditor(original));
		waitFor(() -> !sim.isBusy() && !sim.isRed());
		expect("a later edit that parses clears it", !sim.isRed() && sim.reasonText().isBlank());
		expect("the file wasn't touched by any of that", java.util.Arrays.equals(before, Files.readAllBytes(own)));
		String edited = original.replaceFirst("(COLOR_BLOOD: )[^,]*,", "$11_2_3,");
		SwingUtilities.invokeAndWait(() -> sim.typeInEditor(edited));
		waitFor(() -> !sim.isBusy() && sim.canSave());
		boolean[] asked = new boolean[1];
		sim.setDiscardPrompt(question -> {
			asked[0] = true;
			return false;
		});
		SwingUtilities.invokeAndWait(() -> screen.clickRail("Assets"));
		expect("leaving with unsaved text asks, and can be refused", asked[0] && screen.shownScreen().equals(MainScreen.RACE_PORTRAIT_SIM));
		SwingUtilities.invokeAndWait(sim::clickSave);
		waitFor(() -> !sim.isBusy() && !sim.canSave());
		expect("Save saved: " + sim.statusText(), sim.statusText().equals("Saved.") && Files.readString(own).contains("1_2_3"));
		int offset = sim.editorText().indexOf("FRAMES");
		SwingUtilities.invokeAndWait(() -> sim.placeCaret(offset));
		expect("the cursor's block lists its absent defaults: " + sim.defaultsListed(), sim.defaultsListed() > 0);
		save(screen, outDir.resolve("race-portrait-sim.png"));
		expectNothingCutOff(screen, "race portrait sim");

		// The colour and piece controls change the text, which then saves like any other edit.
		expect("the colours are listed: " + sim.colourRows().size(), !sim.colourRows().isEmpty());
		expect("the pieces are listed: " + sim.pieceRows().size(), !sim.pieceRows().isEmpty());
		syx.core.PortraitSim.ColourRow skin = sim.colourRows().stream().filter(row -> row.name().equals("SKIN")).findFirst().orElseThrow();
		String beforeColours = sim.editorText();
		SwingUtilities.invokeAndWait(() -> sim.clickSwatch("SKIN", 3));
		waitFor(() -> !sim.isBusy() && sim.colourRows().stream().anyMatch(row -> row.name().equals("SKIN") && row.active() == 3));
		expect("choosing a swatch makes it active and doesn't touch the text", sim.editorText().equals(beforeColours));
		SwingUtilities.invokeAndWait(() -> sim.pickColourForTest("SKIN", 0, 11, 22, 33));
		expect("the picker changes the text straight away", !sim.editorText().equals(beforeColours) && sim.editorText().contains("R: 11") && sim.editorText().contains("G: 22"));
		waitFor(() -> !sim.isBusy() && sim.colourRows().stream().anyMatch(row -> row.name().equals("SKIN") && row.slots().get(0).r() == 10));
		expect("and the swatch follows", sim.colourRows().stream().anyMatch(row -> row.name().equals("SKIN") && row.slots().get(0).r() == 10));
		expect("and it can be saved", sim.canSave());
		SwingUtilities.invokeAndWait(sim::undoInEditor);
		expect("one undo takes the whole colour back", sim.editorText().equals(beforeColours));
		SwingUtilities.invokeAndWait(() -> sim.clickSwatch("SKIN", 0));
		waitFor(() -> !sim.isBusy() && sim.colourRows().stream().anyMatch(row -> row.name().equals("SKIN") && row.active() == 0));
		String[] whileOpen = new String[1];
		driveColourDialog(sim, new java.awt.Color(100, 150, 200), "Cancel", whileOpen);
		expect("the picker's dialog changes the text while it's open: " + whileOpen[0], whileOpen[0] != null && !whileOpen[0].equals(beforeColours));
		expect("and Cancel takes it all back", sim.editorText().equals(beforeColours));
		driveColourDialog(sim, new java.awt.Color(100, 150, 200), "OK", whileOpen);
		expect("OK keeps the colour", !sim.editorText().equals(beforeColours));
		SwingUtilities.invokeAndWait(sim::undoInEditor);
		expect("and one undo removes every step of it", sim.editorText().equals(beforeColours));
		SwingUtilities.invokeAndWait(() -> sim.pickBloodForTest(9, 8, 7));
		expect("blood changes the text", sim.editorText().contains("COLOR_BLOOD: 9_8_7"));
		SwingUtilities.invokeAndWait(sim::undoInEditor);
		waitFor(() -> !sim.isBusy());
		syx.core.PortraitSim.PieceRow piece = sim.pieceRows().stream().filter(row -> row.count() > 1 && row.rows() > 2).findFirst().orElseThrow();
		int startFrame = piece.current();
		SwingUtilities.invokeAndWait(() -> sim.stepPieceForTest(piece.part(), 1));
		waitFor(() -> !sim.isBusy() && sim.pieceRows().stream().anyMatch(row -> row.part() == piece.part() && row.current() != startFrame));
		expect("stepping a piece moves its frame and doesn't touch the text", sim.editorText().equals(beforeColours));
		syx.core.PortraitSim.PieceRow stepped = sim.pieceRows().stream().filter(row -> row.part() == piece.part()).findFirst().orElseThrow();
		int delta = stepped.row() + 1 < stepped.rows() ? 1 : -1;
		SwingUtilities.invokeAndWait(() -> sim.changeRowForTest(piece.part(), delta));
		expect("changing a row changes the text", !sim.editorText().equals(beforeColours));
		waitFor(() -> !sim.isBusy() && sim.pieceRows().stream().anyMatch(row -> row.part() == piece.part() && row.row() == stepped.row() + delta));
		expect("and the piece follows", sim.pieceRows().stream().anyMatch(row -> row.part() == piece.part() && row.row() == stepped.row() + delta && row.current() == stepped.current()));
		for (String tab : List.of("Colours", "Pieces", "Random")) {
			SwingUtilities.invokeAndWait(() -> sim.selectTab(tab));
			SwingUtilities.invokeAndWait(() -> { });
			save(screen, outDir.resolve("race-portrait-sim-" + tab.toLowerCase(java.util.Locale.ROOT) + ".png"));
			expectNothingCutOff(screen, "race portrait sim, " + tab);
		}
		SwingUtilities.invokeAndWait(() -> sim.selectTab("Person"));
		expect("skin was a real row", skin.slots().size() == 16);
		sim.setDiscardPrompt(question -> true);
		deleteQuietlyForLaterChecks(races);
	}

	private static void mapSpriteSim(MainScreen screen, Path modRoot, String version, Path outDir) throws Exception {
		Path races = modRoot.resolve(version).resolve("assets/init/race");
		Files.createDirectories(races);
		Files.writeString(races.resolve("MAPRACE.txt"), "SPRITE_FILE: HUMAN,\nOTHER: 1,\n");
		SwingUtilities.invokeAndWait(() -> screen.clickRail("Map Sprite Sim"));
		expect("the rail opens the map sprite sim", screen.shownScreen().equals(MainScreen.MAP_SPRITE_SIM));
		expect("and it's marked in the rail", "Map Sprite Sim".equals(screen.railHighlight()));
		syx.gui.MapSpriteSimScreen sim = screen.mapSpriteSimScreen();
		waitFor(() -> !sim.isBusy() && sim.hasPreview());
		expect("the races are listed: " + sim.raceNames(), sim.raceNames().contains("MAPRACE"));
		expect("a race on the game's file is locked, with Copy to Mod", sim.isEditorLocked() && sim.canCopy() && !sim.canSave());
		expect("its map tags are shown", sim.editorText().contains("COLOR_CLOTHES") && sim.editorText().contains("ADDONS") && !sim.editorText().contains("FACE"));
		expect("a walk has several frames, and plays: " + sim.frameCount(), sim.frameCount() > 1 && sim.isPlaying());
		SwingUtilities.invokeAndWait(sim::clickPlay);
		expect("Pause stops it", !sim.isPlaying());
		SwingUtilities.invokeAndWait(sim::clickPlay);
		SwingUtilities.invokeAndWait(() -> sim.clickDirection(2));
		waitFor(() -> !sim.isBusy());
		expect("the compass chooses the direction", sim.direction() == 2);
		SwingUtilities.invokeAndWait(() -> sim.selectAnimation("SLEEP"));
		waitFor(() -> !sim.isBusy());
		expect("a still pose has one frame: " + sim.frameCount(), sim.frameCount() == 1);
		expect("each type can be chosen: " + sim.typeCount(), sim.typeCount() >= 2);
		SwingUtilities.invokeAndWait(() -> sim.selectType(sim.typeCount() - 1));
		waitFor(() -> !sim.isBusy());
		expect("the child draws", sim.hasPreview() && !sim.isRed());
		SwingUtilities.invokeAndWait(() -> sim.selectType(0));
		SwingUtilities.invokeAndWait(() -> sim.selectAnimation("MOVE"));
		waitFor(() -> !sim.isBusy());
		expect("the addons are listed: " + sim.addonRows(), !sim.addonRows().isEmpty());
		sim.setCopyPrompt(question -> true);
		SwingUtilities.invokeAndWait(sim::clickCopy);
		waitFor(() -> !sim.isBusy() && !sim.isEditorLocked());
		expect("Copy to Mod makes it editable: " + sim.statusText(), !sim.isEditorLocked() && sim.statusText().contains("Copied"));
		Path own = races.resolve("sprite/MAPRACE.txt");
		expect("and wrote the race's own file", Files.isRegularFile(own));
		String original = sim.editorText();
		byte[] before = Files.readAllBytes(own);
		SwingUtilities.invokeAndWait(() -> sim.typeInEditor(original.replaceFirst("COLOR_SKIN: SKIN", "COLOR_SKIN: NOPE")));
		waitFor(() -> !sim.isBusy() && sim.isRed());
		expect("a colour that isn't in COLORS turns it red, and says why: " + sim.reasonText(), sim.isRed() && sim.reasonText().contains("NOPE"));
		expect("the last good picture stays up", sim.hasPreview());
		expect("Save is blocked", !sim.canSave());
		save(screen, outDir.resolve("map-sprite-sim-red.png"));
		expectNothingCutOff(screen, "map sprite sim, red");
		SwingUtilities.invokeAndWait(() -> sim.typeInEditor(original));
		waitFor(() -> !sim.isBusy() && !sim.isRed());
		expect("a later edit that parses clears it", !sim.isRed() && sim.reasonText().isBlank());
		expect("the file wasn't touched by any of that", java.util.Arrays.equals(before, Files.readAllBytes(own)));
		String edited = original.replaceFirst("(COLOR_BLOOD: )[^,]*,", "$11_2_3,");
		SwingUtilities.invokeAndWait(() -> sim.typeInEditor(edited));
		waitFor(() -> !sim.isBusy() && sim.canSave());
		boolean[] asked = new boolean[1];
		sim.setDiscardPrompt(question -> {
			asked[0] = true;
			return false;
		});
		SwingUtilities.invokeAndWait(() -> screen.clickRail("Assets"));
		expect("leaving with unsaved text asks, and can be refused", asked[0] && screen.shownScreen().equals(MainScreen.MAP_SPRITE_SIM));
		SwingUtilities.invokeAndWait(sim::clickSave);
		waitFor(() -> !sim.isBusy() && !sim.canSave());
		expect("Save saved: " + sim.statusText(), sim.statusText().equals("Saved.") && Files.readString(own).contains("1_2_3"));
		int offset = sim.editorText().indexOf("VALUES");
		SwingUtilities.invokeAndWait(() -> sim.placeCaret(offset));
		expect("the cursor's colour block lists its absent defaults: " + sim.defaultsListed(), sim.defaultsListed() > 0);
		save(screen, outDir.resolve("map-sprite-sim.png"));
		expectNothingCutOff(screen, "map sprite sim");
		for (String tab : List.of("Addons", "Random")) {
			SwingUtilities.invokeAndWait(() -> sim.selectTab(tab));
			SwingUtilities.invokeAndWait(() -> { });
			save(screen, outDir.resolve("map-sprite-sim-" + tab.toLowerCase(java.util.Locale.ROOT) + ".png"));
			expectNothingCutOff(screen, "map sprite sim, " + tab);
		}
		SwingUtilities.invokeAndWait(() -> sim.selectTab("Person"));
		sim.setDiscardPrompt(question -> true);
		deleteQuietlyForLaterChecks(races);
	}

	/** Opens the real colour dialog, moves it through two colours, and presses a button. */
	private static void driveColourDialog(syx.gui.RacePortraitSimScreen sim, java.awt.Color color, String button, String[] whileOpen) throws Exception {
		javax.swing.Timer driver = new javax.swing.Timer(150, event -> {
			for (java.awt.Window window : java.awt.Window.getWindows()) {
				if (window instanceof javax.swing.JDialog dialog && dialog.isShowing()) {
					((javax.swing.Timer) event.getSource()).stop();
					javax.swing.JColorChooser chooser = find(dialog, javax.swing.JColorChooser.class, null);
					chooser.setColor(color.darker());
					chooser.setColor(color);
					whileOpen[0] = sim.editorText();
					find(dialog, javax.swing.JButton.class, button).doClick();
					return;
				}
			}
		});
		driver.start();
		SwingUtilities.invokeAndWait(() -> sim.openPickerForTest("SKIN"));
		driver.stop();
		waitFor(() -> !sim.isBusy());
	}

	private static <T extends java.awt.Component> T find(java.awt.Container in, Class<T> type, String text) {
		for (java.awt.Component child : in.getComponents()) {
			if (type.isInstance(child) && (text == null || child instanceof javax.swing.AbstractButton b && text.equals(b.getText()))) {
				return type.cast(child);
			}
			if (child instanceof java.awt.Container inner) {
				T found = find(inner, type, text);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	/** The rail's item, the two dropdowns and running a diff, on a mod with no code and then one with. */
	private static void gameVersionDiff(MainScreen screen, Path modRoot, String version, Path outDir) throws Exception {
		expect("the rail has Game Version Diff", screen.isRailShowing());
		SwingUtilities.invokeAndWait(() -> screen.clickRail("Game Version Diff"));
		expect("it opens the screen", screen.shownScreen().equals(MainScreen.GAME_VERSION_DIFF));
		expect("and it's marked in the rail: " + screen.railHighlight(), "Game Version Diff".equals(screen.railHighlight()));
		syx.gui.GameVersionDiffScreen diff = screen.gameVersionDiffScreen();
		waitFor(() -> !diff.isBusy());
		expect("it says what the installed game is: " + diff.installedText(), diff.installedText().contains("0."));
		List<String> labels = diff.versionLabels();
		if (labels.size() < 2 || labels.stream().noneMatch(label -> label.equals("0.70.33 (shipped)"))) {
			System.out.println("SKIPPED running a diff here: manifests\\ isn't in this folder.");
			expect("generating is on offer, with no manifest yet: " + diff.installedText(), diff.canGenerate());
			return;
		}
		expect("each dropdown names the version and where it came from: " + labels, labels.contains("0.70.33 (shipped)") && labels.contains("0.71.44 (shipped)"));
		boolean installedIsShipped = diff.installedText().contains("already has");
		expect("generating is only offered if the installed game has no manifest: " + diff.installedText(), diff.canGenerate() != installedIsShipped);
		expect("old starts as the older, new as the newest here: " + diff.selectedOld() + " " + diff.selectedNew(), diff.selectedOld().startsWith("0.70.33") && diff.selectedNew().startsWith("0.71.44"));
		expect("it can run: " + diff.reasonText(), diff.canRun());
		save(screen, outDir.resolve("diff-start.png"));
		SwingUtilities.invokeAndWait(diff::runDiff);
		waitFor(() -> !diff.isBusy());
		expect("a mod with no jar is told so: " + diff.emptyText(), diff.emptyText().contains("no code") && diff.resultCount() == 0);
		expect("and has no report to export", !diff.canExport());
		expect("nothing's been saved, so there's no folder to open", !diff.canOpenReports());
		save(screen, outDir.resolve("diff-no-code.png"));
		String oldId = diff.selectedOld();
		String newId = diff.selectedNew();
		SwingUtilities.invokeAndWait(() -> diff.selectOld(newId));
		expect("two of the same can't run, and it says why: " + diff.reasonText(), !diff.canRun() && diff.reasonText().contains("same version"));
		SwingUtilities.invokeAndWait(() -> {
			diff.selectOld(newId);
			diff.selectNew(oldId);
		});
		expect("the wrong way round can't either: " + diff.reasonText(), !diff.canRun() && diff.reasonText().contains("Swap"));
		save(screen, outDir.resolve("diff-swapped.png"));
		SwingUtilities.invokeAndWait(() -> {
			diff.selectOld(oldId);
			diff.selectNew(newId);
		});

		// A real code mod, read and copied, never changed, if it's on this machine.
		Path steamApps = Paths.get(args0).getParent().getParent();
		Path elohi = steamApps.resolve("workshop/content/1162750/3620581808/V71/script/SongsOfElohi.jar");
		if (!Files.isRegularFile(elohi)) {
			return;
		}
		Path script = modRoot.resolve(version).resolve("script");
		Files.createDirectories(script);
		Path copy = script.resolve("Copied.jar");
		Files.copy(elohi, copy);
		SwingUtilities.invokeAndWait(diff::runDiff);
		waitFor(() -> !diff.isBusy());
		expect("a code mod's findings are listed: " + diff.summaryText(), diff.resultCount() > 0 && diff.summaryText().contains("0 breaks") && diff.summaryText().contains("Old: 0.70.33 (shipped)"));
		expect("the first finding shows in full, not only on hover: " + diff.detailText(), diff.detailText().contains("If your code uses it, the compiled mod still holds the old value until you recompile."));
		expectNothingCutOff(screen, "game version diff results");
		expect("a report can be exported", diff.canExport());
		SwingUtilities.invokeAndWait(diff::export);
		expect("Export Report says where it went: " + diff.exportNoteText(), diff.exportNoteText().startsWith("Saved the report to ") && diff.exportNoteText().contains("Diff Reports") && diff.exportNoteText().contains(" - 0.70.33 to 0.71.44.md"));
		SwingUtilities.invokeAndWait(diff::export);
		expect("exporting it again writes nothing: " + diff.exportNoteText(), diff.exportNoteText().startsWith("This report is already saved"));
		expect("the folder can now be opened", diff.canOpenReports());
		OPENED.clear();
		SwingUtilities.invokeAndWait(diff::openReports);
		waitFor(() -> !OPENED.isEmpty());
		expect("Open Reports Folder opens it: " + OPENED, OPENED.get(0).endsWith("Diff Reports"));
		OPENED.clear();
		for (int[] size : new int[][] { { 1200, 760 }, { 1000, 700 }, { 720, 460 } }) {
			WidthBand band = size[0] == 1200 ? WidthBand.WIDE : size[0] == 1000 ? WidthBand.MEDIUM : WidthBand.NARROW;
			resize(screen, size[0], size[1], band);
			expectNothingCutOff(screen, "game version diff, exported, at " + size[0]);
			save(screen, outDir.resolve("diff-exported-" + size[0] + ".png"));
		}
		resize(screen, 1200, 760, WidthBand.WIDE);
		save(screen, outDir.resolve("diff-results.png"));
		Files.delete(copy);
		Files.delete(script);
		SwingUtilities.invokeAndWait(diff::runDiff);
		waitFor(() -> !diff.isBusy());
		// Generating for real, into this test's own folder, when the installed game has moved past what shipped.
		if (!installedIsShipped) {
			SwingUtilities.invokeAndWait(diff::generate);
			waitFor(() -> !diff.isBusy());
			expect("Generate Manifest says where it saved: " + diff.installedText(), diff.installedText().startsWith("Saved a manifest of ") && diff.installedText().contains(".manifest.json.gz"));
			expect("it's in the dropdowns, marked as generated: " + diff.versionLabels(), diff.versionLabels().stream().anyMatch(label -> label.endsWith("(generated here)")));
			expect("and it can't be generated twice", !diff.canGenerate());
		}
		SwingUtilities.invokeAndWait(screen::showHome);
		waitFor(() -> !screen.homeScreen().isLoading());
	}

	private static MainScreen open(Path settingsFile, Path mods, GameFolderFinder.Result result, Optional<Path> picked) throws Exception {
		MainScreen[] holder = new MainScreen[1];
		SwingUtilities.invokeAndWait(() -> {
			holder[0] = new MainScreen(new ToolkitSettings(settingsFile), mods, () -> result, parent -> picked, folder -> {
				if (OPEN_FAILS[0]) {
					throw new IOException("test failure");
				}
				OPENED.add(folder);
			}, handbookStore, manifestStore, OPENED_LINKS::add);
			holder[0].setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			holder[0].setVisible(true);
			holder[0].start();
		});
		return holder[0];
	}

	/** Lays out a screen that isn't in a window, from the top down. */
	private static void layOut(java.awt.Component component) {
		if (component instanceof java.awt.Container container) {
			container.doLayout();
			for (java.awt.Component child : container.getComponents()) {
				layOut(child);
			}
		}
	}

	/** The screen for connecting an agent. Without a packaged app there's no command line to offer, so the rest uses a stand-in path. */
	/**
	 * The Asset Creation Wizard through the real screen: a race from a name and a look, a question turned over to the
	 * handbook and back, and an answer saved. It's left part way, for the sweep to continue.
	 */
	private static void wizardRace(MainScreen screen, Path modRoot, Path outDir) throws Exception {
		try (java.util.stream.Stream<Path> files = Files.walk(modRoot)) {
			BEFORE_WIZARD.addAll(files.toList());
		}
		syx.gui.WizardScreen wizard = screen.wizardScreen();
		SwingUtilities.invokeAndWait(() -> screen.clickRail("Asset Creation Wizard"));
		expect("the rail opens the wizard", screen.shownScreen().equals(MainScreen.WIZARD));
		expect("and shows where you are", "Asset Creation Wizard".equals(screen.railHighlight()));
		waitFor(() -> !wizard.isBusy());
		expect("it starts by asking what to make: " + wizard.replyLabels(), wizard.questionKind().equals("start") && wizard.replyLabels().contains("Race"));
		save(screen, outDir.resolve("12-wizard-start.png"));
		SwingUtilities.invokeAndWait(() -> wizard.clickReply("Race"));
		waitFor(() -> !wizard.isBusy());
		expect("then asks for a name", wizard.questionKind().equals("name"));
		SwingUtilities.invokeAndWait(() -> wizard.typeAndSend("Human"));
		waitFor(() -> !wizard.isBusy());
		expect("a name the game has is refused, and asked again: " + wizard.lastError(), wizard.questionKind().equals("name") && wizard.lastError() != null);
		SwingUtilities.invokeAndWait(() -> wizard.typeAndSend("Smoke Folk"));
		waitFor(() -> !wizard.isBusy());
		expect("then which race it's like: " + wizard.replyLabels(), wizard.questionKind().equals("look") && wizard.replyLabels().contains("Human"));
		save(screen, outDir.resolve("12-wizard-look.png"));
		SwingUtilities.invokeAndWait(() -> wizard.clickReply("Human"));
		waitFor(() -> !wizard.isBusy());
		expect("choosing it moves on to the race's words: " + wizard.questionStep() + " " + wizard.lastError(), wizard.questionStep().equals("words"));
		Path raceFile;
		try (java.util.stream.Stream<Path> found = Files.walk(modRoot)) {
			raceFile = found.filter(file -> file.getFileName().toString().equals("SMOKE_FOLK.txt") && file.getParent().getFileName().toString().equals("race")
					&& file.getParent().getParent().getFileName().toString().equals("init")).findFirst().orElse(null);
		}
		expect("and wrote the race's files", raceFile != null);
		int last = wizard.flippableCount() - 1;
		expect("the question can be turned over", last >= 0);
		if (last >= 0) {
			String front = wizard.bubbleText(last);
			SwingUtilities.invokeAndWait(() -> wizard.flipBubble(last));
			expect("turned over, it shows the handbook's words: " + wizard.bubbleText(last), wizard.isFlipped(last) && wizard.bubbleText(last).contains("From the handbook"));
			expectNothingCutOff(screen, "wizard with the handbook showing");
			save(screen, outDir.resolve("12-wizard-handbook.png"));
			SwingUtilities.invokeAndWait(() -> wizard.flipBubble(last));
			expect("and turned again, the question", !wizard.isFlipped(last) && wizard.bubbleText(last).equals(front));
		}
		SwingUtilities.invokeAndWait(() -> {
			wizard.setField("NAMES", "Smoke Folkers");
			wizard.clickSave();
		});
		waitFor(() -> !wizard.isBusy());
		expect("an answer is saved and the next asked: " + wizard.questionStep() + " " + wizard.lastError(), wizard.questionStep().equals("description"));
		expect("into the race's text file", raceFile != null && Files.readString(raceFile.getParent().getParent().getParent().resolve("text").resolve("race").resolve("SMOKE_FOLK.txt")).contains("Smoke Folkers"));
		expectNothingCutOff(screen, "wizard");
		double share = wizard.widestBubbleShare();
		expect("no bubble is wider than its share of the column: " + share, share <= wizard.bubbleLimit() + 0.001);
		save(screen, outDir.resolve("12-wizard-fields.png"));
		// On to where a race lives: each climate and land gets a slider, because the game sets both ends.
		for (int i = 0; i < 12 && !wizard.questionStep().equals("homeland"); i++) {
			SwingUtilities.invokeAndWait(wizard::clickSkip);
			waitFor(() -> !wizard.isBusy());
		}
		expect("skipping reaches where a race lives: " + wizard.questionStep(), wizard.questionStep().equals("homeland"));
		expect("with a slider for each amount: " + wizard.sliderCount(), wizard.sliderCount() >= 4);
		expectNothingCutOff(screen, "wizard with sliders");
		save(screen, outDir.resolve("12-wizard-sliders.png"));
		// Jobs are a long list: those with an amount show, and another is added from the list under them.
		for (int i = 0; i < 12 && !wizard.questionStep().equals("work"); i++) {
			SwingUtilities.invokeAndWait(wizard::clickSkip);
			waitFor(() -> !wizard.isBusy());
		}
		expect("skipping reaches which jobs a race enjoys: " + wizard.questionStep(), wizard.questionStep().equals("work"));
		expect("whose range is too wide for sliders: " + wizard.sliderCount(), wizard.sliderCount() == 0);
		SwingUtilities.invokeAndWait(() -> wizard.setField("PREFERRED.WORK.ARENAG_NORMAL", "1.25"));
		expectNothingCutOff(screen, "wizard with a long list");
		save(screen, outDir.resolve("12-wizard-jobs.png"));
		SwingUtilities.invokeAndWait(wizard::clickSave);
		waitFor(() -> !wizard.isBusy());
		expect("a job added from the list is saved: " + wizard.lastError(), wizard.questionStep().equals("others") && Files.readString(raceFile).contains("ARENAG_NORMAL"));
		// The tourists' reviews are one of the review files, picked like an answer.
		for (int i = 0; i < 12 && !wizard.questionStep().equals("tourists"); i++) {
			SwingUtilities.invokeAndWait(wizard::clickSkip);
			waitFor(() -> !wizard.isBusy());
		}
		expect("skipping reaches tourists: " + wizard.questionStep(), wizard.questionStep().equals("tourists"));
		expectNothingCutOff(screen, "wizard tourists");
		save(screen, outDir.resolve("12-wizard-tourists.png"));
		SwingUtilities.invokeAndWait(screen::showHome);
		waitFor(() -> !screen.homeScreen().isLoading());
	}

	/**
	 * The race wizard's last question handing its race to the Portrait Design Wizard, which then adds a layer whose
	 * frames are picked by clicking them on the sheet.
	 */
	private static void wizardPortrait(MainScreen screen, Path modRoot, Path outDir) throws Exception {
		syx.gui.WizardScreen wizard = screen.wizardScreen();
		SwingUtilities.invokeAndWait(() -> screen.clickRail("Asset Creation Wizard"));
		waitFor(() -> !wizard.isBusy());
		expect("the Wizards group is open while a wizard shows", screen.wizardsOpen());
		SwingUtilities.invokeAndWait(screen::clickWizardsHeader);
		expect("its heading folds it", !screen.wizardsOpen());
		SwingUtilities.invokeAndWait(screen::clickWizardsHeader);
		expect("and opens it again", screen.wizardsOpen());
		SwingUtilities.invokeAndWait(() -> wizard.clickContinue(0));
		waitFor(() -> !wizard.isBusy());
		for (int i = 0; i < 30 && !wizard.questionStep().equals("portraits"); i++) {
			SwingUtilities.invokeAndWait(wizard::clickSkip);
			waitFor(() -> !wizard.isBusy());
		}
		expect("the race wizard's last question offers the portraits: " + wizard.replyLabels(), wizard.questionStep().equals("portraits") && wizard.replyLabels().contains("Yes, design them now"));
		save(screen, outDir.resolve("13-portrait-offer.png"));
		SwingUtilities.invokeAndWait(() -> wizard.clickReply("Yes, design them now"));
		waitFor(() -> !wizard.isBusy() && wizard.isPortraitMode() && !"finished".equals(wizard.questionKind()));
		waitFor(() -> !wizard.isBusy());
		expect("yes goes straight to the portrait wizard: " + screen.shownScreen() + " " + wizard.questionStep() + " " + wizard.lastError(),
				screen.shownScreen().equals(MainScreen.PORTRAIT_WIZARD) && "Portrait Design Wizard".equals(screen.railHighlight()));
		if (wizard.questionStep().equals("own-file")) {
			SwingUtilities.invokeAndWait(() -> wizard.clickReply("Copy it into my mod"));
			waitFor(() -> !wizard.isBusy());
		}
		expect("for the race just made: " + wizard.questionStep() + " " + wizard.lastError(), wizard.questionStep().equals("keep-or-empty"));
		SwingUtilities.invokeAndWait(() -> wizard.clickReply("Keep them"));
		waitFor(() -> !wizard.isBusy());
		expect("then which sheets to copy: " + wizard.questionStep(), wizard.questionStep().equals("sheets"));
		save(screen, outDir.resolve("13-portrait-sheets.png"));
		SwingUtilities.invokeAndWait(wizard::clickSkip);
		waitFor(() -> !wizard.isBusy());
		expect("then colours: " + wizard.replyLabels(), wizard.questionStep().equals("colours") && wizard.replyLabels().contains("Add a colour"));
		SwingUtilities.invokeAndWait(() -> wizard.clickReply("Change SKIN"));
		waitFor(() -> !wizard.isBusy());
		expect("a colour is shown as swatches", wizard.questionStep().equals("colour:SKIN"));
		expectNothingCutOff(screen, "portrait wizard colours");
		save(screen, outDir.resolve("13-portrait-colours.png"));
		SwingUtilities.invokeAndWait(wizard::clickSkip);
		waitFor(() -> !wizard.isBusy());
		SwingUtilities.invokeAndWait(() -> wizard.clickReply("Go on to the portraits"));
		waitFor(() -> !wizard.isBusy());
		expect("then the first portrait's layers: " + wizard.questionStep(), wizard.questionStep().equals("portrait:0") && wizard.questionKind().equals("layers"));
		Thread.sleep(800);
		SwingUtilities.invokeAndWait(() -> { });
		expectNothingCutOff(screen, "portrait wizard layers");
		save(screen, outDir.resolve("13-portrait-layers.png"));
		SwingUtilities.invokeAndWait(() -> wizard.clickReply("Add a layer"));
		waitFor(() -> !wizard.isBusy());
		expect("a layer is named first", wizard.questionKind().equals("text"));
		SwingUtilities.invokeAndWait(() -> wizard.typeAndSend("Third eye"));
		waitFor(() -> !wizard.isBusy() && wizard.pickerFrames() > 0);
		expect("then its frames are picked on a sheet: " + wizard.pickerFrames(), wizard.questionKind().equals("frames") && wizard.pickerFrames() > 4);
		SwingUtilities.invokeAndWait(() -> {
			wizard.clickPickerFrame(1);
			wizard.clickPickerFrame(2);
			wizard.clickPickerFrame(3);
			wizard.clickPickerFrame(3);
		});
		expect("clicking picks frames, and again unpicks: " + wizard.pickedFrames(), wizard.pickedFrames().size() == 2);
		SwingUtilities.invokeAndWait(() -> wizard.choosePlace(0));
		Thread.sleep(800);
		SwingUtilities.invokeAndWait(() -> { });
		expectNothingCutOff(screen, "portrait wizard frames");
		save(screen, outDir.resolve("13-portrait-frames.png"));
		SwingUtilities.invokeAndWait(wizard::clickCardSave);
		waitFor(() -> !wizard.isBusy());
		expect("the frames are saved at the bottom, then how it's drawn: " + wizard.questionStep() + " " + wizard.lastError(), wizard.questionStep().equals("layer-tweak:0:0"));
		SwingUtilities.invokeAndWait(() -> {
			wizard.setField("who", "Only nobles");
			wizard.clickSave();
		});
		waitFor(() -> !wizard.isBusy());
		expect("and back to the layers: " + wizard.questionStep() + " " + wizard.lastError(), wizard.questionStep().equals("portrait:0"));
		String sprite;
		try (java.util.stream.Stream<Path> found = Files.walk(modRoot)) {
			Path file = found.filter(path -> path.getParent() != null && path.getParent().getFileName().toString().equals("sprite") && path.toString().replace('\\', '/').contains("init/race/sprite/")).findFirst().orElse(null);
			sprite = file == null ? "" : Files.readString(file);
		}
		expect("the layer is in the race's sprite file with its name", sprite.contains("** Third eye") && sprite.contains("CLASS_NOBLE"));
		resize(screen, 720, 460, WidthBand.NARROW);
		SwingUtilities.invokeAndWait(() -> { });
		expectNothingCutOff(screen, "portrait wizard layers, narrow");
		save(screen, outDir.resolve("13-portrait-layers-narrow.png"));
		resize(screen, 1200, 760, WidthBand.WIDE);
		SwingUtilities.invokeAndWait(screen::showHome);
		waitFor(() -> !screen.homeScreen().isLoading());
	}

	private static final java.util.Set<Path> BEFORE_WIZARD = new java.util.HashSet<>();

	/** Takes away what the wizard wrote, files and folders, so later checks see the mod as it was. */
	private static void removeWizardFiles(Path modRoot) throws IOException {
		List<Path> added;
		try (java.util.stream.Stream<Path> files = Files.walk(modRoot)) {
			added = files.filter(path -> !BEFORE_WIZARD.contains(path)).sorted(java.util.Comparator.reverseOrder()).toList();
		}
		for (Path path : added) {
			Files.deleteIfExists(path);
		}
	}

	private static void connectingAnAgent(MainScreen screen, Path outDir) throws Exception {
		SwingUtilities.invokeAndWait(() -> screen.clickRail("Connect Agent"));
		expect("the rail opens the screen", screen.shownScreen().equals(MainScreen.CONNECT_AGENT));
		expect("the rail shows where you are", "Connect Agent".equals(screen.railHighlight()));
		expect("outside the packaged app it offers nothing that wouldn't work", !screen.connectAgentScreen().isOfferingSetup());
		save(screen, outDir.resolve("11-connect-agent-unpackaged.png"));
		syx.gui.ConnectAgentScreen[] offered = new syx.gui.ConnectAgentScreen[1];
		List<String> copied = new java.util.ArrayList<>();
		SwingUtilities.invokeAndWait(() -> {
			offered[0] = new syx.gui.ConnectAgentScreen(Path.of("C:\\Program Files\\Syx Toolkit\\syx.exe"));
			offered[0].setClipboard(copied::add);
			offered[0].setSize(1000, 1400);
			layOut(offered[0]);
		});
		expect("each client's setup is shown: " + offered[0].shownSetups().size(), offered[0].shownSetups().size() == 7);
		SwingUtilities.invokeAndWait(() -> offered[0].clickCopy(0));
		expect("Copy copies that client's text: " + copied, copied.equals(List.of(offered[0].shownSetups().get(0))));
		expect("the Claude Code command is what's copied", copied.get(0).startsWith("claude mcp add"));
		SwingUtilities.invokeAndWait(() -> offered[0].setReadOnly(true));
		expect("read-only changes what's offered", offered[0].shownSetups().get(0).endsWith("mcp --read-only"));
		SwingUtilities.invokeAndWait(() -> layOut(offered[0]));
		saveComponent(offered[0], outDir.resolve("11-connect-agent.png"));
		SwingUtilities.invokeAndWait(screen::showHome);
		waitFor(() -> !screen.homeScreen().isLoading());
	}

	/** A deposit and a mine through the real screens, with a sprite's rows at each width. */
	/** A race starts from a vanilla look. An empty required field blocks saving, and Add copies a game file onto the form only. */
	private static void addingRace(MainScreen screen, Path modRoot, Path game, GameParser.GameVersion version, Path outDir) throws Exception {
		AssetEditorScreen editor = screen.assetEditorScreen();
		editor.setDiscardPrompt(question -> true);
		SwingUtilities.invokeAndWait(screen::showAddAsset);
		waitFor(() -> !screen.addAssetScreen().isBusy());
		SwingUtilities.invokeAndWait(() -> {
			screen.addAssetScreen().selectType("Race");
			screen.addAssetScreen().setAssetName("");
		});
		expect("a race asks for a look", screen.addAssetScreen().isAppearanceVisible());
		List<String> looks = screen.addAssetScreen().appearanceItems();
		expect("the looks are the vanilla races: " + looks, looks.containsAll(List.of("ARGONOSH", "CANTOR", "CRETONIAN", "DONDORIAN", "GARTHIMI", "HUMAN", "Q_AMEVIA", "TILAPI")));
		expect("a blank name can't continue", !screen.addAssetScreen().isNextEnabled());
		SwingUtilities.invokeAndWait(() -> {
			screen.addAssetScreen().setAssetName("FOLKGUI");
			screen.addAssetScreen().selectAppearance("ARGONOSH");
		});
		expect("a named race with a look can continue", screen.addAssetScreen().isNextEnabled());
		SwingUtilities.invokeAndWait(screen.addAssetScreen()::next);
		waitFor(() -> screen.shownScreen().equals(MainScreen.ASSET_EDITOR) && !editor.isBusy());
		expect("the editor opens on that look", editor.valueOf("SPRITE_FILE") != null && editor.valueOf("SPRITE_FILE").equals("FOLKGUI"));
		expect("a new race generates type 1 first names", "FOLKGUI_T1_FIRST".equals(editor.valueOf("file.sprite.TYPES.1.NAMESET_FILE_FIRST")) && !editor.isFileOffered("names1First"));
		expect("and type 1 surnames", "FOLKGUI_T1_SURNAME".equals(editor.valueOf("file.sprite.TYPES.1.NAMESET_FILE_SURNAME")) && !editor.isFileOffered("names1Surname"));
		expect("and a specific biography", "FOLKGUI".equals(editor.valueOf("BIO_FILE_SPECIFIC")) && !editor.isFileOffered("bioSpecific"));
		expect("road preference starts folded: " + editor.headingText("PREFERRED.ROAD"), !editor.isUnfolded("PREFERRED.ROAD") && editor.headingText("PREFERRED.ROAD").contains("Road preference"));
		expect("a new race has no fourth type, which the game refuses, and can be saved: " + editor.messageText(), editor.valueOf("file.sprite.TYPES.3.SPRITE_FILE") == null && editor.isSaveEnabled());
		SwingUtilities.invokeAndWait(() -> editor.scrollTo(editor.scrollMaximum() / 2));
		int scrolled = editor.scrollPosition();
		String possessive = editor.valueOf("POSSESSIVE");
		SwingUtilities.invokeAndWait(() -> editor.setValue("POSSESSIVE", possessive + "s"));
		SwingUtilities.invokeAndWait(() -> { });
		expect("typing while scrolled down stays there: " + scrolled + " then " + editor.scrollPosition(), scrolled > 0 && editor.scrollPosition() == scrolled);
		SwingUtilities.invokeAndWait(() -> {
			editor.setValue("POSSESSIVE", possessive);
			editor.scrollTo(0);
		});
		SwingUtilities.invokeAndWait(() -> editor.clickHeading("PREFERRED.ROAD"));
		expect("clicking the heading opens it", editor.isUnfolded("PREFERRED.ROAD"));
		SwingUtilities.invokeAndWait(() -> editor.clickHeading("PREFERRED.ROAD"));
		expect("clicking the heading again closes it", !editor.isUnfolded("PREFERRED.ROAD"));
		expect("appearance starts open", editor.isSectionOpen("Appearance"));
		SwingUtilities.invokeAndWait(() -> editor.clickSection("Appearance"));
		expect("clicking the appearance heading closes it", !editor.isSectionOpen("Appearance"));
		SwingUtilities.invokeAndWait(() -> editor.clickSection("Appearance"));
		expect("clicking the appearance heading again opens it", editor.isSectionOpen("Appearance"));
		String playable = editor.valueOf("PLAYABLE");
		SwingUtilities.invokeAndWait(() -> editor.setValue("PLAYABLE", ""));
		expect("an empty required field blocks saving: " + editor.messageText(), !editor.isSaveEnabled());
		SwingUtilities.invokeAndWait(() -> editor.setValue("PLAYABLE", playable == null || playable.isEmpty() ? "true" : playable));
		save(screen, outDir.resolve("22-race-form.png"));
		SwingUtilities.invokeAndWait(() -> {
			if (editor.confirmLeave()) {
				screen.showHome();
			}
		});
		waitFor(() -> screen.shownScreen().equals(MainScreen.HOME) && !screen.homeScreen().isLoading());
		expect("leaving wrote nothing", !Files.exists(AssetCreator.versionFolder(modRoot, version).resolve("assets/init/race/FOLKGUI.txt")));

		Path race = AssetCreator.versionFolder(modRoot, version).resolve("assets/init/race/FOLKCOPY.txt");
		Files.createDirectories(race.getParent());
		try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(game.resolve("base").resolve("data.zip").toFile());
				java.io.InputStream in = zip.getInputStream(zip.getEntry("data/assets/init/race/HUMAN.txt"))) {
			Files.copy(in, race);
		}
		openFromHome(screen, race);
		expect("the copied race opens", screen.shownScreen().equals(MainScreen.ASSET_EDITOR));
		expect("its appearance is still the game's", editor.isFileOffered("sprite"));
		expect("an existing race does not generate type 1 names", editor.valueOf("file.sprite.TYPES.1.NAMESET_FILE_FIRST") == null && editor.valueOf("file.names1First.NAMES") == null);
		SwingUtilities.invokeAndWait(() -> editor.clickAddFile("sprite"));
		expect("Add puts the game's file on the form", editor.valueOf("file.sprite.SLEEP_FILE") != null && !editor.valueOf("file.sprite.SLEEP_FILE").isEmpty());
		expect("and writes nothing yet", !Files.exists(AssetCreator.versionFolder(modRoot, version).resolve("assets/init/race/sprite/FOLKCOPY.txt")));
		save(screen, outDir.resolve("22-race-add.png"));
		SwingUtilities.invokeAndWait(() -> {
			if (editor.confirmLeave()) {
				screen.showHome();
			}
		});
		waitFor(() -> screen.shownScreen().equals(MainScreen.HOME) && !screen.homeScreen().isLoading());
	}

	private static void addingMine(MainScreen screen, Path modRoot, GameParser.GameVersion version, Path outDir) throws Exception {
		AssetEditorScreen editor = screen.assetEditorScreen();
		SwingUtilities.invokeAndWait(screen::showAddAsset);
		waitFor(() -> !screen.addAssetScreen().isBusy());
		SwingUtilities.invokeAndWait(() -> {
			screen.addAssetScreen().selectType("Minable resource");
			screen.addAssetScreen().setAssetName("TEST_DEPOSIT");
		});
		SwingUtilities.invokeAndWait(screen.addAssetScreen()::next);
		expect("the deposit editor opens", screen.shownScreen().equals(MainScreen.ASSET_EDITOR) && editor.titleText().contains("Minable resource: TEST_DEPOSIT"));
		expect("it starts with nothing filled in", editor.currentValues().values().stream().allMatch(String::isEmpty));
		SwingUtilities.invokeAndWait(() -> {
			editor.setValue("RESOURCE", "STONE");
			editor.setValue("ON_EVERY_MAP", "true");
			editor.setValue("COLOR", "255_255_255");
			editor.setValue("MINIMAP_COLOR", "10_20_30");
			editor.setValue("FERTILITY_INCREASE", "0");
			editor.setValue("SPRITE", "Clay");
		});
		expect("with no terrain it warns, and can still be saved: " + editor.problemFor("TERRAIN"), editor.isSaveEnabled() || editor.problemFor("TERRAIN") != null);
		SwingUtilities.invokeAndWait(editor::save);
		waitFor(() -> !editor.isBusy() && screen.shownScreen().equals(MainScreen.HOME) && !screen.homeScreen().isLoading());
		expect("the deposit was written", Files.isRegularFile(AssetCreator.versionFolder(modRoot, version).resolve("assets/init/resource/minable/TEST_DEPOSIT.txt")));

		SwingUtilities.invokeAndWait(screen::showAddAsset);
		waitFor(() -> !screen.addAssetScreen().isBusy());
		SwingUtilities.invokeAndWait(() -> {
			screen.addAssetScreen().selectType("Mine");
			screen.addAssetScreen().setAssetName("TEST_MINE");
		});
		expect("the destination has the prefix the game needs: " + screen.addAssetScreen().destinationText(), screen.addAssetScreen().destinationText().contains("/assets/init/room/MINE_TEST_MINE.txt"));
		SwingUtilities.invokeAndWait(screen.addAssetScreen()::next);
		expect("the mine editor opens", screen.shownScreen().equals(MainScreen.ASSET_EDITOR) && editor.titleText().contains("Mine: TEST_MINE"));
		expect("the sprites start filled in, and folded", !editor.currentValues().getOrDefault("SPRITES.WORK_1X1.0.FRAMES", "").isEmpty() && !editor.isUnfolded("SPRITES.WORK_1X1"));
		expect("the sounds start from a mine's", "impact->Mine*".equals(editor.valueOf("SOUND.ROOM_WORK_")));
		expect("the save waits for what's required: " + editor.messageText(), !editor.isSaveEnabled());
		SwingUtilities.invokeAndWait(() -> {
			editor.clickRowAction("SPRITES.AUX_BIG_2X2");
			editor.setValue("ICON", "24->resource->Gem->0");
			editor.setValue("MINABLE", "TEST_DEPOSIT");
			editor.setValue("FLOOR", "MINE");
			editor.setValue("YEILD_WORKER_DAILY", "2");
			editor.setValue("STORAGE", "100");
			editor.setValue("MINI_COLOR", "80_80_80");
			editor.setValue("WORK.SHIFT_OFFSET", "0.125");
			editor.addTag("RESOURCES");
			editor.setValue("RESOURCES", "STONE");
			editor.setValue("AREA_COSTS", "0");
			editor.setValue("NAME", "Test Mine");
			editor.setValue("DESC", "A mine.");
			editor.setValue("WORK.TITLE", "Miner");
			editor.setValue("WORK.VERB", "Mining");
			for (int i = 0; i < 2; i++) {
				editor.setValue("STATS." + i + ".NAME", "Stat");
				editor.setValue("STATS." + i + ".DESC", "A stat.");
				editor.setValue("ITEMS." + i + ".NAME", "Item");
				editor.setValue("ITEMS." + i + ".DESC", "An item.");
				editor.setValue("ITEMS." + i + ".COSTS", "1");
				editor.setValue("ITEMS." + i + ".STATS", "0,0,0,0");
			}
		});
		expect("a filled-in mine can be saved: " + editor.messageText(), editor.isSaveEnabled());
		for (WidthBand band : WidthBand.values()) {
			resize(screen, band == WidthBand.WIDE ? 1400 : band == WidthBand.MEDIUM ? 950 : 700, 800, band);
			expectNothingCutOff(screen, "the mine's sprites at " + band);
		}
		resize(screen, 1400, 800, WidthBand.WIDE);
		saveComponent(editor.formComponent(), outDir.resolve("10-mine-form.png"));
		SwingUtilities.invokeAndWait(editor::save);
		waitFor(() -> !editor.isBusy() && screen.shownScreen().equals(MainScreen.HOME) && !screen.homeScreen().isLoading());
		Path file = AssetCreator.versionFolder(modRoot, version).resolve("assets/init/room/MINE_TEST_MINE.txt");
		expect("the mine was written", Files.isRegularFile(file));
		expect("with the game's sprites", Files.readString(file).contains("WORK_1X1") && Files.readString(file).contains("MECH_WORK: 0"));
		expect("and its sounds", Files.readString(AssetCreator.versionFolder(modRoot, version).resolve("assets/audio/config/mono/MINE_TEST_MINE.txt")).contains("impact->Mine*"));

		openFromHome(screen, file);
		expect("it opens again for editing", editor.isEditingExisting() && editor.titleText().contains("Edit Mine: TEST_MINE"));
		expect("with its sprites", !editor.currentValues().getOrDefault("SPRITES.WORK_1X1.1.FRAMES", "").isEmpty());
		SwingUtilities.invokeAndWait(screen::showHome);
		waitFor(() -> screen.shownScreen().equals(MainScreen.HOME) && !screen.homeScreen().isLoading());
	}

	private static void close(MainScreen screen) throws Exception {
		SwingUtilities.invokeAndWait(screen::dispose);
	}

	/** Later checks expect an empty new mod, so the test animal is removed again. */
	private static String firstCropResource(Path game, Path versionFolder) throws Exception {
		try (syx.core.GameParser parser = syx.core.GameParser.open(syx.core.GameInstall.jarIn(game))) {
			return syx.core.GameAssetIndex.load(game, versionFolder, parser).crops().values().iterator().next().resource();
		}
	}

	private static void deleteQuietlyForLaterChecks(Path folder) throws IOException {
		try (java.util.stream.Stream<Path> walk = Files.walk(folder)) {
			walk.sorted(java.util.Comparator.reverseOrder()).forEach(GuiSmoke::delete);
		}
	}

	private static String valueIn(AssetEditorScreen editor, String key) throws Exception {
		return editor.valueOf(key);
	}

	private static long countFiles(Path folder) throws IOException {
		try (java.util.stream.Stream<Path> walk = Files.walk(folder)) {
			return walk.count();
		}
	}

	/** Copies a real mod into the temp mods folder, so it's only ever read. */
	private static void copyInto(Path from, Path to) throws IOException {
		try (java.util.stream.Stream<Path> walk = Files.walk(from)) {
			for (Path path : walk.toList()) {
				Files.copy(path, to.resolve(from.relativize(path).toString()));
			}
		}
	}

	private static void delete(Path path) {
		try {
			Files.delete(path);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void waitFor(BooleanSupplier condition) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while (true) {
			boolean[] done = new boolean[1];
			SwingUtilities.invokeAndWait(() -> done[0] = condition.getAsBoolean());
			if (done[0]) {
				return;
			}
			if (System.nanoTime() > deadline) {
				throw new IllegalStateException("Timed out waiting for a screen.");
			}
			Thread.sleep(10);
		}
	}

	private static void runCheck(MainScreen screen, Path folder, boolean cancel) throws Exception {
		CountDownLatch finished = new CountDownLatch(1);
		SwingUtilities.invokeAndWait(() -> {
			screen.syntaxScreen().setOnFinished((SyntaxChecker.Result r) -> finished.countDown());
			if (folder != null) {
				screen.syntaxScreen().setFolder(folder);
			}
			screen.syntaxScreen().startCheck();
			if (cancel) {
				screen.syntaxScreen().cancelCheck();
			}
		});
		if (!finished.await(5, TimeUnit.MINUTES)) {
			throw new IllegalStateException("The check didn't finish.");
		}
		SwingUtilities.invokeAndWait(() -> {
		});
	}

	private static void resize(MainScreen screen, int width, int height, WidthBand band) throws Exception {
		SwingUtilities.invokeAndWait(() -> screen.setSize(width, height));
		waitFor(() -> screen.widthBand() == band);
		SwingUtilities.invokeAndWait(screen::validate);
		SwingUtilities.invokeAndWait(() -> { });
	}

	/** Every visible control must fit inside whatever holds it. Lists and tables can scroll sideways by design. */
	private static void expectNothingCutOff(MainScreen screen, String where) throws Exception {
		List<String> cut = new java.util.ArrayList<>();
		SwingUtilities.invokeAndWait(() -> {
			screen.validate();
			findCutOff(screen.getContentPane(), cut);
			findCutOff(screen.getJMenuBar(), cut);
		});
		expect("nothing is cut off in the " + where + ": " + cut, cut.isEmpty());
	}

	private static void findCutOff(java.awt.Container parent, List<String> cut) {
		for (java.awt.Component child : parent.getComponents()) {
			if (!child.isVisible()) {
				continue;
			}
			if (!(parent instanceof javax.swing.JViewport) && child.getX() + child.getWidth() > parent.getWidth() + 1) {
				cut.add(child.getClass().getSimpleName() + " ends at " + (child.getX() + child.getWidth()) + " in " + parent.getClass().getSimpleName() + " of " + parent.getWidth());
			}
			if (child instanceof java.awt.Container container && !(child instanceof javax.swing.JTree) && !(child instanceof javax.swing.JTable) && !(child instanceof javax.swing.JList)) {
				findCutOff(container, cut);
			}
		}
	}

	private static void saveMenu(MainScreen screen, Path file) throws Exception {
		saveComponent(screen.menuComponent(), file);
	}

	/** For something that shows in its own window, such as a popover, which painting the main window misses. */
	private static void saveComponent(javax.swing.JComponent component, Path file) throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D g = image.createGraphics();
			component.paint(g);
			g.dispose();
			try {
				ImageIO.write(image, "png", new File(file.toString()));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	private static void save(MainScreen screen, Path file) throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			screen.validate();
			BufferedImage image = new BufferedImage(screen.getWidth(), screen.getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D g = image.createGraphics();
			screen.paint(g);
			g.dispose();
			try {
				ImageIO.write(image, "png", new File(file.toString()));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	private static void expect(String name, boolean ok) {
		if (!ok) {
			failed++;
			System.out.println("FAILED: " + name);
		}
	}
}
