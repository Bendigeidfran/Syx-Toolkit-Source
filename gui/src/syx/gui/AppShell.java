package syx.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;

import com.formdev.flatlaf.FlatClientProperties;

/** The parts of the window around the current screen: the bar along the top, and the rail down the side. */
final class AppShell extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final int RAIL_WIDTH = 190;

	static final String ASSETS = "Assets";
	static final String ADD_ASSET = "Add New Asset";
	/** The rail's group holding the wizards, which folds open. It isn't a place itself. */
	static final String WIZARDS = "Wizards";
	static final String WIZARD = "Asset Creation Wizard";
	static final String PORTRAIT_WIZARD = "Portrait Design Wizard";
	private static final List<String> WIZARD_ITEMS = List.of(WIZARD, PORTRAIT_WIZARD);
	static final String MOD_INFO = "Mod Info";
	static final String CHECK_SYNTAX = "Check Syntax";
	static final String RACE_PORTRAIT_SIM = "Race Portrait Sim";
	static final String MAP_SPRITE_SIM = "Map Sprite Sim";
	static final String NORMAL_MAPS = "Normal Maps";
	static final String AUDIO_CONVERSION = "Audio Conversion";
	static final String GAME_VERSION_DIFF = "Game Version Diff";
	static final String CONNECT_AGENT = "Connect Agent";
	static final String OPEN_FOLDER = "Open Folder";

	/** What the shell's buttons do. */
	record Actions(Runnable assets, Runnable addAsset, Runnable wizard, Runnable portraitWizard, Runnable modInfo, Runnable checkSyntax, Runnable racePortraitSim, Runnable mapSpriteSim, Runnable normalMaps, Runnable audioConversion, Runnable gameVersionDiff, Runnable connectAgent, Runnable openFolder,
			Runnable changeMod, Runnable openHandbook, Runnable toggleTheme) {
	}

	private final JMenuBar topBar = new JMenuBar();
	private final JPanel rail = new JPanel();
	private final JLabel modName = new JLabel();
	private final JButton menuButton = topBarButton("Menu");
	private JLabel appName;
	private JLabel version;
	private WidthBand band = WidthBand.WIDE;
	private JPopupMenu openMenu;
	private final JButton change = topBarButton("Change");
	private final JButton themeButton = topBarButton(" ");
	private final transient Map<String, JToggleButton> railItems = new LinkedHashMap<>();
	private final JButton openFolder = new JButton(OPEN_FOLDER);
	private final transient ButtonGroup railGroup = new ButtonGroup();
	private final transient Actions actions;

	AppShell(JComponent content, Actions actions) {
		super(new BorderLayout());
		this.actions = actions;
		buildTopBar();
		buildRail();
		add(rail, BorderLayout.WEST);
		add(content, BorderLayout.CENTER);
	}

	JMenuBar topBar() {
		return topBar;
	}

	private void buildTopBar() {
		JLabel appName = new JLabel("Songs of Syx Modding Toolkit");
		JLabel version = new JLabel(syx.core.AppVersion.label());
		Theme.style(appName, "semibold");
		Theme.style(appName, "onTopBar");
		Theme.style(version, "small");
		Theme.style(version, "onTopBar");
		Theme.style(modName, "onTopBar");
		appName.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 6));
		version.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 18));
		modName.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
		change.setToolTipText("Choose a different mod");
		change.addActionListener(e -> actions.changeMod().run());
		JButton handbook = topBarButton("Handbook");
		handbook.setToolTipText("Open the Songs of Syx Modding Handbook in your browser");
		handbook.addActionListener(e -> actions.openHandbook().run());
		themeButton.addActionListener(e -> actions.toggleTheme().run());
		menuButton.setToolTipText("Where to go in this mod");
		menuButton.setVisible(false);
		menuButton.addActionListener(e -> showMenu());
		this.appName = appName;
		this.version = version;
		topBar.add(menuButton);
		topBar.add(appName);
		topBar.add(version);
		topBar.add(modName);
		topBar.add(change);
		// FlatLaf makes this the part of the title bar that drags the window.
		topBar.add(Box.createHorizontalGlue());
		topBar.add(handbook);
		topBar.add(themeButton);
	}

	private static JButton topBarButton(String text) {
		JButton button = new JButton(text);
		button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
		Theme.style(button, "onTopBar");
		button.setFocusable(false);
		return button;
	}

	private void buildRail() {
		rail.setLayout(new BoxLayout(rail, BoxLayout.Y_AXIS));
		Theme.style(rail, "rail");
		rail.setBorder(BorderFactory.createEmptyBorder(14, 10, 14, 10));
		addRailItem(ASSETS, actions.assets());
		addRailItem(ADD_ASSET, actions.addAsset());
		addWizardGroup();
		addRailItem(MOD_INFO, actions.modInfo());
		addRailItem(CHECK_SYNTAX, actions.checkSyntax());
		addRailItem(RACE_PORTRAIT_SIM, actions.racePortraitSim());
		addRailItem(MAP_SPRITE_SIM, actions.mapSpriteSim());
		addRailItem(NORMAL_MAPS, actions.normalMaps());
		addRailItem(AUDIO_CONVERSION, actions.audioConversion());
		addRailItem(GAME_VERSION_DIFF, actions.gameVersionDiff());
		addRailItem(CONNECT_AGENT, actions.connectAgent());
		rail.add(Box.createVerticalGlue());
		railButton(openFolder);
		openFolder.setToolTipText("Open this mod's folder in Explorer");
		openFolder.addActionListener(e -> actions.openFolder().run());
		rail.add(openFolder);
		rail.setVisible(false);
	}

	private void addRailItem(String name, Runnable action) {
		rail.add(railItem(name, action));
		rail.add(Box.createVerticalStrut(4));
	}

	private JToggleButton railItem(String name, Runnable action) {
		JToggleButton item = new JToggleButton(name);
		railButton(item);
		Theme.style(item, "railItem");
		// A move can be refused, and then the highlight must stay on the screen still showing.
		item.addActionListener(e -> {
			action.run();
			highlightShowing();
		});
		railGroup.add(item);
		railItems.put(name, item);
		return item;
	}

	private final JButton wizardsHeader = new JButton();
	private final JPanel wizardItems = new JPanel();
	private boolean wizardsOpen;

	/** Wizards, a heading that folds open to show each wizard, indented under it. */
	private void addWizardGroup() {
		railButton(wizardsHeader);
		Theme.style(wizardsHeader, "railItem");
		wizardsHeader.setToolTipText("Show or hide the wizards");
		wizardsHeader.addActionListener(e -> setWizardsOpen(!wizardsOpen));
		rail.add(wizardsHeader);
		rail.add(Box.createVerticalStrut(4));
		wizardItems.setLayout(new BoxLayout(wizardItems, BoxLayout.Y_AXIS));
		wizardItems.setOpaque(false);
		wizardItems.setAlignmentX(LEFT_ALIGNMENT);
		wizardItems.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
		Runnable[] actionsFor = { actions.wizard(), actions.portraitWizard() };
		for (int i = 0; i < WIZARD_ITEMS.size(); i++) {
			JToggleButton item = railItem(WIZARD_ITEMS.get(i), actionsFor[i]);
			item.setPreferredSize(new Dimension(RAIL_WIDTH - 34, 34));
			wizardItems.add(item);
			wizardItems.add(Box.createVerticalStrut(4));
		}
		rail.add(wizardItems);
		setWizardsOpen(false);
	}

	private void setWizardsOpen(boolean open) {
		wizardsOpen = open;
		wizardItems.setVisible(open);
		wizardsHeader.setText(WIZARDS + (open ? "  ▾" : "  ▸"));
		rail.revalidate();
		rail.repaint();
	}

	boolean areWizardsOpen() {
		return wizardsOpen;
	}

	void clickWizardsHeader() {
		wizardsHeader.doClick();
	}

	private static void railButton(AbstractButton button) {
		button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
		button.setHorizontalAlignment(SwingConstants.LEFT);
		button.setAlignmentX(LEFT_ALIGNMENT);
		// Tab can still reach it, but a click doesn't leave a focus ring behind.
		button.setRequestFocusEnabled(false);
		button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		button.setPreferredSize(new Dimension(RAIL_WIDTH - 20, 34));
	}

	private String showingItem;
	private String showingMod;

	/** Lays the window out for the room it has. A narrow one has a menu rather than a rail. */
	void setBand(WidthBand value) {
		band = value;
		showing(showingItem, showingMod);
	}

	WidthBand band() {
		return band;
	}

	private JPopupMenu buildMenu() {
		JPopupMenu menu = new JPopupMenu();
		Map<String, Runnable> items = menuActions();
		javax.swing.JMenu wizards = new javax.swing.JMenu(WIZARDS);
		items.forEach((name, action) -> {
			JMenuItem item = new JMenuItem(name);
			item.setFont(name.equals(showingItem) ? item.getFont().deriveFont(java.awt.Font.BOLD) : item.getFont());
			item.addActionListener(e -> action.run());
			if (WIZARD_ITEMS.contains(name)) {
				if (wizards.getItemCount() == 0) {
					menu.add(wizards);
				}
				wizards.add(item);
				if (name.equals(showingItem)) {
					wizards.setFont(wizards.getFont().deriveFont(java.awt.Font.BOLD));
				}
			} else {
				menu.add(item);
			}
		});
		return menu;
	}

	private Map<String, Runnable> menuActions() {
		Map<String, Runnable> items = new LinkedHashMap<>();
		items.put(ASSETS, actions.assets());
		items.put(ADD_ASSET, actions.addAsset());
		items.put(WIZARD, actions.wizard());
		items.put(PORTRAIT_WIZARD, actions.portraitWizard());
		items.put(MOD_INFO, actions.modInfo());
		items.put(CHECK_SYNTAX, actions.checkSyntax());
		items.put(RACE_PORTRAIT_SIM, actions.racePortraitSim());
		items.put(MAP_SPRITE_SIM, actions.mapSpriteSim());
		items.put(NORMAL_MAPS, actions.normalMaps());
		items.put(AUDIO_CONVERSION, actions.audioConversion());
		items.put(GAME_VERSION_DIFF, actions.gameVersionDiff());
		items.put(CONNECT_AGENT, actions.connectAgent());
		items.put(OPEN_FOLDER, actions.openFolder());
		return items;
	}

	private void showMenu() {
		openMenu = buildMenu();
		openMenu.show(menuButton, 0, menuButton.getHeight());
	}

	void clickMenuButton() {
		menuButton.doClick();
	}

	void closeMenu() {
		if (openMenu != null) {
			openMenu.setVisible(false);
		}
	}

	/** The menu as it last opened. */
	JComponent menuComponent() {
		return openMenu;
	}

	/** What the open menu lists, which is nothing if it's closed. */
	List<String> openMenuItems() {
		if (openMenu == null || !openMenu.isVisible()) {
			return List.of();
		}
		List<String> names = new java.util.ArrayList<>();
		for (Component item : openMenu.getComponents()) {
			if (item instanceof javax.swing.JMenu group) {
				for (int i = 0; i < group.getItemCount(); i++) {
					names.add(group.getItem(i).getText());
				}
			} else {
				names.add(((JMenuItem) item).getText());
			}
		}
		return names;
	}

	boolean isMenuShowing() {
		return menuButton.isVisible();
	}

	List<String> menuItemNames() {
		return List.copyOf(menuActions().keySet());
	}

	void clickMenuItem(String name) {
		menuActions().get(name).run();
		highlightShowing();
	}

	/** Says what's on screen. The rail and the mod's name only appear once a mod is being worked on. */
	void showing(String railItem, String mod) {
		showingItem = railItem;
		showingMod = mod;
		boolean inMod = railItem != null && mod != null;
		boolean narrow = band == WidthBand.NARROW;
		rail.setVisible(inMod && !narrow);
		menuButton.setVisible(inMod && narrow);
		boolean showTitle = !(inMod && narrow);
		appName.setVisible(showTitle);
		version.setVisible(showTitle);
		modName.setText(inMod ? mod : "");
		modName.setVisible(inMod);
		change.setVisible(inMod);
		highlightShowing();
		revalidate();
		topBar.revalidate();
		topBar.repaint();
	}

	private void highlightShowing() {
		railGroup.clearSelection();
		JToggleButton item = showingItem == null ? null : railItems.get(showingItem);
		if (item != null) {
			item.setSelected(true);
		}
		// A wizard on screen opens its group, so its item is seen highlighted.
		if (showingItem != null && WIZARD_ITEMS.contains(showingItem) && !wizardsOpen) {
			setWizardsOpen(true);
		}
	}

	void setThemeText(String text) {
		themeButton.setText(text);
	}

	boolean isRailShowing() {
		return rail.isVisible();
	}

	String highlightedItem() {
		for (Map.Entry<String, JToggleButton> entry : railItems.entrySet()) {
			if (entry.getValue().isSelected()) {
				return entry.getKey();
			}
		}
		return null;
	}

	List<String> railItemNames() {
		return List.copyOf(railItems.keySet());
	}

	String modNameText() {
		return modName.getText();
	}

	String versionText() {
		return version.getText();
	}

	String themeText() {
		return themeButton.getText();
	}

	boolean isChangeShowing() {
		return change.isVisible();
	}

	void clickRailItem(String name) {
		railItems.get(name).doClick();
	}

	void clickOpenFolder() {
		openFolder.doClick();
	}

	void clickChange() {
		change.doClick();
	}

	Component rail() {
		return rail;
	}
}
