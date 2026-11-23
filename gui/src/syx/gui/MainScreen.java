package syx.gui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;

import com.formdev.flatlaf.FlatClientProperties;

import syx.core.AssetEditing;
import syx.core.GameFolderFinder;
import syx.core.GameInstall;
import syx.core.GameParser;
import syx.core.HandbookIndex;
import syx.core.HandbookStore;
import syx.core.ManifestStore;
import syx.core.ModEntry;
import syx.core.ModLibrary;
import syx.core.ToolkitSettings;
import syx.core.Workspace;
import syx.core.ToolkitSettings.ThemeChoice;

/** The app window, which moves through the steps one screen at a time. */
public final class MainScreen extends JFrame {

	private static final long serialVersionUID = 1L;

	public static final String FIND_GAME = "findGame";
	public static final String FOUND_GAME = "foundGame";
	public static final String START = "start";
	public static final String CREATE_MOD = "createMod";
	public static final String CHOOSE_MOD = "chooseMod";
	public static final String HOME = "home";
	public static final String ADD_ASSET = "addAsset";
	public static final String ASSET_EDITOR = "assetEditor";
	public static final String MODIFY_INFO = "modifyInfo";
	public static final String SYNTAX = "syntax";
	public static final String RACE_PORTRAIT_SIM = "racePortraitSim";
	public static final String MAP_SPRITE_SIM = "mapSpriteSim";
	public static final String NORMAL_MAPS = "normalMaps";
	public static final String AUDIO_CONVERSION = "audioConversion";
	public static final String GAME_VERSION_DIFF = "gameVersionDiff";
	public static final String CONNECT_AGENT = "connectAgent";
	public static final String WIZARD = "wizard";
	/** The same wizard screen as {@link #WIZARD}, showing the Portrait Design Wizard, with its own menu item. */
	public static final String PORTRAIT_WIZARD = "portrait-wizard";

	private final transient ToolkitSettings settings;
	private final transient Path modsFolder;
	private final transient Supplier<GameFolderFinder.Result> finder;
	private final transient Function<Component, Optional<Path>> folderPicker;
	private final transient HandbookStore handbook;

	private final CardLayout cards = new CardLayout();
	private final JPanel content = new JPanel(cards);
	private final transient AssetEditorScreen.LinkOpener linkOpener;
	private final AppShell shell;
	private final WrappedText findMessage = new WrappedText(" ", null);
	private final WrappedText foundMessage = new WrappedText(" ", null);
	private final WrappedText startNotice = new WrappedText(" ", Theme.Tone.WARNING);
	private final WrappedText currentModLabel = new WrappedText(" ", Theme.Tone.HELP);
	private final JLabel gameFolderLabel = new JLabel(" ");
	private final JButton returnToMod = new JButton(" ");
	private final JButton selectGame = new JButton("Select Songs of Syx folder");
	private final JButton findGameAutomatically = new JButton("Check for Songs of Syx folder automatically");
	private final CreateModScreen createScreen;
	private final ChooseModScreen chooseScreen;
	private final HomeScreen homeScreen;
	private final AddAssetScreen addAssetScreen;
	private final AssetEditorScreen assetEditorScreen;
	private final ConnectAgentScreen connectAgentScreen = new ConnectAgentScreen();
	private final ModifyInfoScreen modifyScreen;
	private final SyntaxCheckScreen syntaxScreen;
	private final RacePortraitSimScreen racePortraitSimScreen;
	private final MapSpriteSimScreen mapSpriteSimScreen;
	private final NormalMapsScreen normalMapsScreen;
	private final AudioConversionScreen audioScreen = new AudioConversionScreen();
	private final GameVersionDiffScreen gameVersionDiffScreen;
	private final WizardScreen wizardScreen;

	private transient Path gameFolder;
	private transient ModEntry currentMod;
	private String shown = "";
	private boolean bandApplied;
	private boolean openingAsset;

	public MainScreen(ToolkitSettings settings, Path modsFolder, Supplier<GameFolderFinder.Result> finder,
			Function<Component, Optional<Path>> folderPicker, HomeScreen.FolderOpener folderOpener,
			HandbookStore handbook, ManifestStore manifests, AssetEditorScreen.LinkOpener linkOpener) {
		super("Songs of Syx Modding Toolkit");
		this.settings = settings;
		this.modsFolder = modsFolder;
		this.finder = finder;
		this.folderPicker = folderPicker;
		this.handbook = handbook;
		this.gameFolder = settings.gameFolder().orElse(null);
		this.createScreen = new CreateModScreen(modsFolder, () -> gameFolder, this::selectMod, this::showStart);
		this.chooseScreen = new ChooseModScreen(modsFolder, () -> gameFolder, this::selectMod, this::showCreateMod, this::showStart);
		this.homeScreen = new HomeScreen(() -> gameFolder, folderOpener, this::showAddAsset, this::openAsset);
		this.addAssetScreen = new AddAssetScreen(() -> gameFolder, handbook, this::showHome, this::showAssetEditor);
		this.assetEditorScreen = new AssetEditorScreen(() -> gameFolder, linkOpener, this::showHome);
		this.modifyScreen = new ModifyInfoScreen(() -> gameFolder, this::showHome);
		this.syntaxScreen = new SyntaxCheckScreen(() -> gameFolder);
		this.racePortraitSimScreen = new RacePortraitSimScreen(() -> gameFolder);
		this.mapSpriteSimScreen = new MapSpriteSimScreen(() -> gameFolder);
		this.normalMapsScreen = new NormalMapsScreen(syx.core.NormalTuningStore.beside(settings), () -> gameFolder);
		this.gameVersionDiffScreen = new GameVersionDiffScreen(() -> gameFolder, manifests, folderOpener);
		this.wizardScreen = new WizardScreen(() -> gameFolder, handbook, linkOpener, this::openFromWizard);
		wizardScreen.setPortraitLinks(this::portraitWizardFor, this::openInPortraitSim);

		content.add(buildFindGame(), FIND_GAME);
		content.add(buildFoundGame(), FOUND_GAME);
		content.add(buildStart(), START);
		content.add(createScreen, CREATE_MOD);
		content.add(chooseScreen, CHOOSE_MOD);
		content.add(homeScreen, HOME);
		content.add(addAssetScreen, ADD_ASSET);
		content.add(assetEditorScreen, ASSET_EDITOR);
		content.add(modifyScreen, MODIFY_INFO);
		content.add(syntaxScreen, SYNTAX);
		content.add(racePortraitSimScreen, RACE_PORTRAIT_SIM);
		content.add(mapSpriteSimScreen, MAP_SPRITE_SIM);
		content.add(normalMapsScreen, NORMAL_MAPS);
		content.add(audioScreen, AUDIO_CONVERSION);
		content.add(gameVersionDiffScreen, GAME_VERSION_DIFF);
		content.add(connectAgentScreen, CONNECT_AGENT);
		content.add(wizardScreen, WIZARD);
		this.linkOpener = linkOpener;
		this.shell = new AppShell(content, new AppShell.Actions(
				() -> move(this::showHome), () -> move(this::showAddAsset), () -> move(this::showWizard), () -> move(this::showPortraitWizard), () -> move(this::showModifyInfo), () -> move(this::showSyntaxCheck), () -> move(this::showRacePortraitSim), () -> move(this::showMapSpriteSim), () -> move(this::showNormalMaps), () -> move(this::showAudioConversion), () -> move(this::showGameVersionDiff), () -> move(this::showConnectAgent),
				homeScreen::openModFolder, () -> move(this::showChooseMod), this::openHandbook, this::toggleTheme));
		content.setFocusable(true);
		setContentPane(shell);
		setJMenuBar(shell.topBar());
		getRootPane().putClientProperty(FlatClientProperties.TITLE_BAR_SHOW_TITLE, false);
		updateThemeButton();
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setMinimumSize(new Dimension(720, 460));
		Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		setSize(Math.min(1200, usable.width), Math.min(760, usable.height));
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				applyWidthBand();
			}
		});
		applyWidthBand();
		setLocationRelativeTo(null);
	}

	/** Tells everything that changes with the window's width how much room there now is. */
	private void applyWidthBand() {
		WidthBand band = WidthBand.of(getWidth());
		if (band != shell.band() || !bandApplied) {
			bandApplied = true;
			shell.setBand(band);
			assetEditorScreen.setBand(band);
			modifyScreen.setBand(band);
			addAssetScreen.setBand(band);
			racePortraitSimScreen.setBand(band);
			mapSpriteSimScreen.setBand(band);
			wizardScreen.setBand(band);
			shell.revalidate();
		}
	}

	public WidthBand widthBand() {
		return shell.band();
	}

	public boolean isMenuShowing() {
		return shell.isMenuShowing();
	}

	public java.util.List<String> menuItemNames() {
		return shell.menuItemNames();
	}

	public void clickMenuItem(String name) {
		shell.clickMenuItem(name);
	}

	public void clickMenuButton() {
		shell.clickMenuButton();
	}

	public void closeMenu() {
		shell.closeMenu();
	}

	public java.util.List<String> menuOpenItems() {
		return shell.openMenuItems();
	}

	/** The menu as it last opened, so tests can look at it. */
	public javax.swing.JComponent menuComponent() {
		return shell.menuComponent();
	}

	private void updateThemeButton() {
		shell.setThemeText(Theme.current() == ThemeChoice.DARK ? "Light mode" : "Dark mode");
	}

	/** Goes somewhere from the rail or top bar, unless that would lose work and the user says no. */
	private void move(Runnable destination) {
		boolean allowed = switch (shown) {
		case ASSET_EDITOR -> assetEditorScreen.confirmLeave();
		case MODIFY_INFO -> modifyScreen.confirmLeave();
		case RACE_PORTRAIT_SIM -> racePortraitSimScreen.confirmLeave();
		case MAP_SPRITE_SIM -> mapSpriteSimScreen.confirmLeave();
		default -> true;
		};
		if (allowed) {
			destination.run();
		}
	}

	private void openHandbook() {
		try {
			linkOpener.open(HandbookIndex.SITE);
		} catch (IOException | RuntimeException e) {
			JOptionPane.showMessageDialog(this, "The handbook couldn't be opened: " + e.getMessage(), "Couldn't open the handbook", JOptionPane.WARNING_MESSAGE);
		}
	}

	/** Switches between dark and light, and remembers the choice. */
	public void toggleTheme() {
		ThemeChoice next = Theme.current() == ThemeChoice.DARK ? ThemeChoice.LIGHT : ThemeChoice.DARK;
		Theme.switchTo(next);
		updateThemeButton();
		try {
			settings.setTheme(next);
		} catch (IOException e) {
			// The theme still changes for now, and this isn't worth interrupting anyone for.
		}
	}

	public String themeButtonText() {
		return shell.themeText();
	}

	public boolean isRailShowing() {
		return shell.isRailShowing();
	}

	public String railHighlight() {
		return shell.highlightedItem();
	}

	public String topBarModName() {
		return shell.modNameText();
	}

	public String topBarVersion() {
		return shell.versionText();
	}

	public boolean isTopBarChangeShowing() {
		return shell.isChangeShowing();
	}

	/** Clicks an item in the rail, by its name. */
	public void clickRail(String name) {
		shell.clickRailItem(name);
	}

	/** Whether the rail's Wizards group is open, showing each wizard. */
	public boolean wizardsOpen() {
		return shell.areWizardsOpen();
	}

	public void clickWizardsHeader() {
		shell.clickWizardsHeader();
	}

	public void clickRailOpenFolder() {
		shell.clickOpenFolder();
	}

	public void clickTopBarChange() {
		shell.clickChange();
	}

	/** Decides the first screen from what's been saved. */
	public void start() {
		String problem = GameInstall.problemWith(gameFolder);
		if (problem == null) {
			refreshHandbook();
			showStart();
			if (currentMod != null) {
				showHome();
			}
		} else if (settings.gameFolder().isPresent()) {
			showFindGame("The saved game folder can't be used. " + problem);
		} else {
			showFindGame("First, the toolkit needs to know where Songs of Syx is installed.");
		}
	}

	public static Optional<Path> pickGameFolder(Component parent) {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Select your Songs of Syx folder");
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		return chooser.showDialog(parent, "Use this folder") == JFileChooser.APPROVE_OPTION
				? Optional.of(chooser.getSelectedFile().toPath())
				: Optional.empty();
	}

	public String shownScreen() {
		return shown;
	}

	public Optional<ModEntry> currentMod() {
		return Optional.ofNullable(currentMod);
	}

	public Path gameFolder() {
		return gameFolder;
	}

	public CreateModScreen createScreen() {
		return createScreen;
	}

	public ChooseModScreen chooseScreen() {
		return chooseScreen;
	}

	public HomeScreen homeScreen() {
		return homeScreen;
	}

	public AddAssetScreen addAssetScreen() {
		return addAssetScreen;
	}

	public AssetEditorScreen assetEditorScreen() {
		return assetEditorScreen;
	}

	public ModifyInfoScreen modifyScreen() {
		return modifyScreen;
	}

	public SyntaxCheckScreen syntaxScreen() {
		return syntaxScreen;
	}

	public String foundMessageText() {
		return foundMessage.getText();
	}

	public String findMessageText() {
		return findMessage.getText();
	}

	public void showFindGame(String message) {
		findMessage.setText(message);
		setFindButtonsEnabled(true);
		show(FIND_GAME);
	}

	public void showStart() {
		updateStart();
		show(START);
	}

	public void showCreateMod() {
		createScreen.reset();
		show(CREATE_MOD);
	}

	public void showChooseMod() {
		chooseScreen.refresh(settings.currentMod().orElse(null));
		show(CHOOSE_MOD);
	}

	/** Shows the current mod's home, re-reading it from disk in case it changed. */
	public void showHome() {
		if (currentMod != null) {
			currentMod = ModLibrary.find(modsFolder, gameFolder, currentMod.folderName()).filter(mod -> mod.refusal() == null).orElse(null);
		}
		if (currentMod == null) {
			showStart();
			return;
		}
		homeScreen.show(currentMod);
		show(HOME);
	}

	public void showAddAsset() {
		if (currentMod != null) {
			addAssetScreen.open(currentMod);
			show(ADD_ASSET);
		}
	}

	/** Moves from choosing a type and name to filling in its tags. */
	private void showAssetEditor(AddAssetScreen.Choice choice) {
		if (currentMod != null) {
			assetEditorScreen.open(currentMod, choice);
			show(ASSET_EDITOR);
		}
	}

	private record OpenedAsset(AssetEditing.Opened asset, AddAssetScreen.Choice choice) {
	}

	/** Opens a file from Home's tree in the editor, or says on Home why it can't be opened. */
	private void openAsset(Path file) {
		ModEntry mod = currentMod;
		Path game = gameFolder;
		if (mod == null || openingAsset) {
			return;
		}
		openingAsset = true;
		new SwingWorker<OpenedAsset, Void>() {
			@Override
			protected OpenedAsset doInBackground() throws Exception {
				try (Workspace.Context context = Workspace.open(game, handbook, mod)) {
					AssetEditing.Location location = AssetEditing.locate(mod.root(), context.version(), file, context.entries(), context.index(), context.parser());
					if (location.problem() != null) {
						throw new IllegalArgumentException(location.problem());
					}
					AssetEditing.Opened asset = AssetEditing.load(location, context.parser(), context.index());
					return new OpenedAsset(asset, new AddAssetScreen.Choice(location.entry(), location.name(), context.index().editing(location.entry().type(), asset.fileName()),
							context.version(), context.handbook(), context.producers()));
				}
			}

			@Override
			protected void done() {
				openingAsset = false;
				try {
					OpenedAsset result = get();
					if (mod == currentMod && shown.equals(HOME)) {
						assetEditorScreen.openExisting(mod, result.choice(), result.asset());
						show(ASSET_EDITOR);
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					homeScreen.showOpenProblem(e.getCause().getMessage());
				}
			}
		}.execute();
	}

	/** True while a file from Home's tree is being read. */
	public boolean isOpeningAsset() {
		return openingAsset;
	}

	public void showModifyInfo() {
		if (currentMod != null) {
			modifyScreen.open(currentMod);
			show(MODIFY_INFO);
		}
	}

	public void showSyntaxCheck() {
		if (currentMod != null) {
			syntaxScreen.setFolder(currentMod.root());
		}
		show(SYNTAX);
	}

	public void showRacePortraitSim() {
		if (currentMod != null) {
			racePortraitSimScreen.open(currentMod);
		}
		show(RACE_PORTRAIT_SIM);
	}

	public RacePortraitSimScreen racePortraitSimScreen() {
		return racePortraitSimScreen;
	}

	public void showMapSpriteSim() {
		if (currentMod != null) {
			mapSpriteSimScreen.open(currentMod);
		}
		show(MAP_SPRITE_SIM);
	}

	public MapSpriteSimScreen mapSpriteSimScreen() {
		return mapSpriteSimScreen;
	}

	public void showNormalMaps() {
		if (currentMod != null) {
			normalMapsScreen.open(currentMod);
		}
		show(NORMAL_MAPS);
	}

	public NormalMapsScreen normalMapsScreen() {
		return normalMapsScreen;
	}

	public void showAudioConversion() {
		if (currentMod != null) {
			audioScreen.open(currentMod);
		}
		show(AUDIO_CONVERSION);
	}

	public AudioConversionScreen audioScreen() {
		return audioScreen;
	}

	public void showGameVersionDiff() {
		gameVersionDiffScreen.open(currentMod);
		show(GAME_VERSION_DIFF);
	}

	public GameVersionDiffScreen gameVersionDiffScreen() {
		return gameVersionDiffScreen;
	}

	public void showConnectAgent() {
		show(CONNECT_AGENT);
	}

	public void showWizard() {
		if (currentMod != null) {
			wizardScreen.open(currentMod);
		}
		show(WIZARD);
	}

	public void showPortraitWizard() {
		if (currentMod != null) {
			wizardScreen.openPortraits(currentMod, null);
		}
		show(PORTRAIT_WIZARD);
	}

	/** The race wizard handing a race it just made to the portrait wizard: the same screen, under the other menu item. */
	private void portraitWizardFor(String race) {
		show(PORTRAIT_WIZARD);
	}

	/** The portrait wizard's Open in the Race Portrait Sim. */
	private void openInPortraitSim(String race) {
		if (currentMod != null) {
			racePortraitSimScreen.open(currentMod, race);
		}
		show(RACE_PORTRAIT_SIM);
	}

	public WizardScreen wizardScreen() {
		return wizardScreen;
	}

	/** The wizard's Open in Editor: the asset's data file, opened as Home's tree would open it. */
	private void openFromWizard(Path file) {
		showHome();
		openAsset(file);
	}

	public ConnectAgentScreen connectAgentScreen() {
		return connectAgentScreen;
	}

	public void continueAfterFound() {
		showStart();
	}

	private void show(String screen) {
		shown = screen;
		cards.show(content, screen.equals(PORTRAIT_WIZARD) ? WIZARD : screen);
		// Otherwise focus jumps to whichever control comes first, and that would select a row or ring a button.
		content.requestFocusInWindow();
		shell.showing(railItemFor(screen), currentMod == null ? null : currentMod.displayName());
	}

	private static String railItemFor(String screen) {
		return switch (screen) {
		case HOME -> AppShell.ASSETS;
		case ADD_ASSET, ASSET_EDITOR -> AppShell.ADD_ASSET;
		case MODIFY_INFO -> AppShell.MOD_INFO;
		case SYNTAX -> AppShell.CHECK_SYNTAX;
		case RACE_PORTRAIT_SIM -> AppShell.RACE_PORTRAIT_SIM;
		case MAP_SPRITE_SIM -> AppShell.MAP_SPRITE_SIM;
		case NORMAL_MAPS -> AppShell.NORMAL_MAPS;
		case AUDIO_CONVERSION -> AppShell.AUDIO_CONVERSION;
		case GAME_VERSION_DIFF -> AppShell.GAME_VERSION_DIFF;
		case CONNECT_AGENT -> AppShell.CONNECT_AGENT;
		case WIZARD -> AppShell.WIZARD;
		case PORTRAIT_WIZARD -> AppShell.PORTRAIT_WIZARD;
		default -> null;
		};
	}

	/** Used by both front ends of the mod screens, once a mod has been created or chosen. */
	private void selectMod(ModEntry mod) {
		currentMod = mod;
		try {
			settings.setCurrentMod(mod.folderName());
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, "This mod will be used for now, but the choice couldn't be saved: " + e.getMessage(),
					"Couldn't save settings", JOptionPane.WARNING_MESSAGE);
		}
		showHome();
	}

	private void updateStart() {
		String notice = " ";
		currentMod = null;
		Optional<String> saved = settings.currentMod();
		if (saved.isPresent()) {
			Optional<ModEntry> found = ModLibrary.find(modsFolder, gameFolder, saved.get());
			currentMod = found.filter(mod -> mod.refusal() == null).orElse(null);
			if (currentMod == null) {
				notice = found.isPresent()
						? "The mod " + saved.get() + " is no longer selected. " + found.get().refusal()
						: "The mod " + saved.get() + " is no longer in the mods folder, so it's no longer selected.";
				try {
					settings.clearCurrentMod();
				} catch (IOException e) {
					notice += " This couldn't be saved: " + e.getMessage();
				}
			}
		}
		startNotice.setText(notice);
		startNotice.setVisible(!notice.isBlank());
		currentModLabel.setText(currentMod == null ? "No mod selected yet." : "Current mod: " + currentMod.displayName());
		returnToMod.setVisible(currentMod != null);
		returnToMod.setText(currentMod == null ? " " : "Return to " + currentMod.displayName());
		gameFolderLabel.setText("Game folder: " + gameFolder);
	}

	public void selectGameFolder() {
		Optional<Path> chosen = folderPicker.apply(this);
		while (chosen.isPresent()) {
			String problem = GameInstall.problemWith(chosen.get());
			if (problem == null) {
				useGameFolder(chosen.get());
				showStart();
				return;
			}
			JOptionPane.showMessageDialog(this, problem, "That isn't the game folder", JOptionPane.WARNING_MESSAGE);
			chosen = folderPicker.apply(this);
		}
	}

	public void checkForGameAutomatically() {
		setFindButtonsEnabled(false);
		findMessage.setText("Checking the likely locations...");
		new SwingWorker<GameFolderFinder.Result, Void>() {
			@Override
			protected GameFolderFinder.Result doInBackground() {
				return finder.get();
			}

			@Override
			protected void done() {
				setFindButtonsEnabled(true);
				try {
					GameFolderFinder.Result result = get();
					if (result.found().isPresent()) {
						useGameFolder(result.found().get());
						foundMessage.setText("Found Songs of Syx at " + result.found().get());
						show(FOUND_GAME);
					} else {
						findMessage.setText("Songs of Syx wasn't found in " + result.checked().size() + " likely locations. Please select its folder.");
						selectGameFolder();
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					findMessage.setText("The search failed, so please select the folder: " + e.getCause());
					selectGameFolder();
				}
			}
		}.execute();
	}

	/** Asks for a newer handbook in the background. Nothing about this ever interrupts the user. */
	private void refreshHandbook() {
		Path game = gameFolder;
		Thread thread = new Thread(() -> {
			try (GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
				handbook.refresh(parser.version());
			} catch (GameParser.UnavailableException | RuntimeException e) {
				// The copy already held stays in use.
			}
		}, "handbook refresh");
		thread.setDaemon(true);
		thread.start();
	}

	private void useGameFolder(Path folder) {
		gameFolder = folder;
		refreshHandbook();
		try {
			settings.setGameFolder(folder);
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, "The folder will be used for now, but it couldn't be saved: " + e.getMessage(),
					"Couldn't save settings", JOptionPane.WARNING_MESSAGE);
		}
	}

	private void setFindButtonsEnabled(boolean enabled) {
		selectGame.setEnabled(enabled);
		findGameAutomatically.setEnabled(enabled);
	}

	private JPanel buildFindGame() {
		Theme.style(selectGame, "primary");
		selectGame.addActionListener(e -> selectGameFolder());
		findGameAutomatically.addActionListener(e -> checkForGameAutomatically());
		return new SetupCard(heading("Find Songs of Syx"), findMessage, selectGame, findGameAutomatically).centred();
	}

	private JPanel buildFoundGame() {
		JButton next = new JButton("Continue");
		Theme.style(next, "primary");
		next.addActionListener(e -> continueAfterFound());
		JButton different = new JButton("Choose a different folder");
		different.addActionListener(e -> selectGameFolder());
		return new SetupCard(heading("Game found"), foundMessage, next, different).centred();
	}

	private JPanel buildStart() {
		JButton create = new JButton("Create New Mod");
		Theme.style(create, "primary");
		create.addActionListener(e -> showCreateMod());
		JButton existing = new JButton("Continue Existing Mod");
		existing.addActionListener(e -> showChooseMod());
		returnToMod.addActionListener(e -> showHome());
		JPanel options = new SetupCard(heading("What would you like to do?"), currentModLabel, create, existing, returnToMod, startNotice).centred();

		JButton change = new JButton("Change...");
		change.addActionListener(e -> showFindGame("Choose the Songs of Syx folder to use."));
		Theme.tone(gameFolderLabel, Theme.Tone.HELP);
		JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		footer.add(gameFolderLabel);
		footer.add(change);

		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
		panel.add(options, BorderLayout.CENTER);
		panel.add(footer, BorderLayout.SOUTH);
		return panel;
	}

	private static JLabel heading(String text) {
		JLabel heading = new JLabel(text);
		Theme.style(heading, "h2");
		return heading;
	}
}
