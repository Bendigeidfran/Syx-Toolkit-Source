package syx.gui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

import syx.core.GameInstall;
import syx.core.GameParser;
import syx.core.MapSpriteSim;
import syx.core.ModEntry;
import syx.core.PortraitSection;
import syx.core.PortraitSim;

/**
 * A live, animated preview of how a race's people look on the map, beside the map tags of its sprite file.
 * Everything it shows comes from {@link MapSpriteSim}. Broken text is normal while someone is typing, so it's marked
 * quietly and the last good picture stays.
 */
public final class MapSpriteSimScreen extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final int DEBOUNCE_MS = 150;
	private static final int IMAGE_CHECK_MS = 1000;
	private static final int OUTLINE = 2;
	private static final int REASON_HEIGHT = 38;
	private static final int DEFAULTS_HEIGHT = 92;
	private static final int TAB_HEIGHT = 300;
	/** The compass's places for N, NE, E, SE, S, SW, W and NW, in a 3 by 3 grid read across. */
	private static final int[] COMPASS_CELLS = { 1, 2, 5, 8, 7, 6, 3, 0 };

	/** The animation, played from its timeline, with a thin outline when the text can't be loaded. */
	private static final class Player extends JComponent {
		private static final long serialVersionUID = 1L;
		private transient List<BufferedImage> frames = List.of();
		private transient List<MapSpriteSim.Step> timeline = List.of();
		private int step;
		private boolean problem;
		private final Timer timer = new Timer(100, e -> advance());

		Player() {
			setOpaque(false);
			setAlignmentX(LEFT_ALIGNMENT);
			timer.setRepeats(false);
		}

		void show(MapSpriteSim.Preview preview) {
			List<BufferedImage> images = new ArrayList<>();
			for (int[] argb : preview.frames()) {
				images.add(image(preview.width(), preview.height(), argb));
			}
			boolean sameLoop = timeline.equals(preview.timeline());
			frames = images;
			timeline = preview.timeline();
			if (!sameLoop || step >= timeline.size()) {
				step = 0;
			}
			Dimension size = new Dimension(preview.width() + OUTLINE * 2, preview.height() + OUTLINE * 2);
			setPreferredSize(size);
			setMaximumSize(size);
			setMinimumSize(size);
			revalidate();
			repaint();
			schedule();
		}

		void clear() {
			frames = List.of();
			timeline = List.of();
			timer.stop();
			repaint();
		}

		void setPlaying(boolean playing) {
			if (playing) {
				schedule();
			} else {
				timer.stop();
			}
		}

		boolean playing() {
			return timer.isRunning();
		}

		private void schedule() {
			timer.stop();
			if (timeline.size() > 1 && isShowing()) {
				timer.setInitialDelay(Math.max(15, timeline.get(step).millis()));
				timer.start();
			}
		}

		private void advance() {
			if (timeline.isEmpty()) {
				return;
			}
			step = (step + 1) % timeline.size();
			repaint();
			schedule();
		}

		void setProblem(boolean value) {
			if (problem != value) {
				problem = value;
				repaint();
			}
		}

		boolean hasImage() {
			return !frames.isEmpty();
		}

		int frameCount() {
			return frames.size();
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			try {
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
				if (!frames.isEmpty() && !timeline.isEmpty()) {
					g.drawImage(frames.get(timeline.get(Math.min(step, timeline.size() - 1)).frame()), OUTLINE, OUTLINE, null);
				}
				if (problem) {
					g.setColor(Theme.errorColor());
					g.setStroke(new BasicStroke(OUTLINE));
					g.drawRect(OUTLINE / 2, OUTLINE / 2, getWidth() - OUTLINE, getHeight() - OUTLINE);
				}
			} finally {
				g.dispose();
			}
		}
	}

	private static BufferedImage image(int width, int height, int[] argb) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, width, height, argb, 0, width);
		return image;
	}

	private final transient Supplier<Path> gameFolder;
	private final JLabel heading = new JLabel("Map Sprite Sim");
	private final WrappedText status = new WrappedText(" ", Theme.Tone.HELP);
	private final JComboBox<String> raceBox = new JComboBox<>();
	private final JComboBox<String> typeBox = new JComboBox<>();
	private final JComboBox<String> animationBox = new JComboBox<>(MapSpriteSim.ANIMATIONS.stream().map(MapSpriteSim.Animation::label).toArray(String[]::new));
	private final JComboBox<Integer> scaleBox = new JComboBox<>(new Integer[] { 1, 2, 3, 4, 5, 6, 8 });
	private final JButton playButton = new JButton("Pause");
	private final JButton reroll = new JButton("Re-roll");
	private final JCheckBox lit = new JCheckBox("Sunlight", true);
	private final JSlider age = slider(40);
	private final JSlider clothes = new JSlider(0, 6, 6);
	private final JSlider grime = slider(0);
	private final JSlider injury = slider(0);
	private final JSlider decay = slider(0);
	private final JSlider plate = new JSlider(0, 5, 0);
	private final JSlider leather = new JSlider(0, 5, 0);
	private final JCheckBox noble = new JCheckBox("Noble");
	private final JCheckBox slave = new JCheckBox("Slave");
	private final JCheckBox parent = new JCheckBox("Parent");
	private final JCheckBox retired = new JCheckBox("Retired");
	private final JCheckBox sick = new JCheckBox("Sick");
	private final JCheckBox enemy = new JCheckBox("Enemy");
	private final JCheckBox naked = new JCheckBox("Naked");
	private final Player player = new Player();
	private final JToggleButton[] compass = new JToggleButton[8];
	private final JLabel directionLabel = new JLabel("S", JLabel.CENTER);
	private final WrappedText reason = new WrappedText(" ", Theme.Tone.ERROR);
	private final WrappedText warnings = new WrappedText(" ", Theme.Tone.WARNING);
	private final WrappedText notes = new WrappedText(" ", Theme.Tone.HELP);
	private final WrappedText animationNote = new WrappedText(" ", Theme.Tone.HELP);
	private final JPanel addonsPanel = new JPanel();
	private final JTextArea editor = new JTextArea();
	private final WrappedText editorNote = new WrappedText(" ", Theme.Tone.WARNING);
	private final JLabel fileLabel = new JLabel(" ");
	private final JButton saveButton = new JButton("Save");
	private final JButton copyButton = new JButton("Copy to Mod");
	private final JPanel defaultsList = new JPanel();
	private final JSplitPane split;
	private final JTabbedPane tabs = new JTabbedPane();
	private final JTextField seedField = new JTextField("1", 12);
	private final JSlider[] randomSliders = new JSlider[8];
	private final JLabel[] randomLabels = new JLabel[8];
	private final int[] randomBytes = PortraitSim.Pins.NONE.random().clone();
	private transient List<MapSpriteSim.AddonRow> lastAddons = List.of();
	private boolean syncing;
	private final transient UndoManager undo = new UndoManager();
	private final transient ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "map-sprite-sim");
		thread.setDaemon(true);
		return thread;
	});
	private final transient AtomicInteger running = new AtomicInteger();
	private final Timer debounce = new Timer(DEBOUNCE_MS, e -> startRender());
	private final Timer imageCheck = new Timer(IMAGE_CHECK_MS, e -> checkImages());
	private transient Predicate<String> discardPrompt = question -> JOptionPane.showConfirmDialog(this, question, "Unsaved map sprite tags", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
	private transient Predicate<String> copyPrompt = question -> JOptionPane.showConfirmDialog(this, question, "Copy to Mod", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
	private transient MapSpriteSim.Session session;
	private transient PortraitSim.Opened opened;
	private String baseline = "";
	private String shownRace;
	private String wantedRace;
	private boolean loading;
	private boolean rendering;
	private boolean renderAgain;
	private boolean red;
	private boolean paused;
	private long seed = 1;
	private int direction = 4;

	public MapSpriteSimScreen(Supplier<Path> gameFolder) {
		super(new BorderLayout(0, 12));
		this.gameFolder = gameFolder;
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		Theme.style(heading, "h2");
		debounce.setRepeats(false);
		animationBox.setSelectedIndex(indexOfAnimation("MOVE"));
		split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildPreview(), buildEditor());
		split.setResizeWeight(0.5);
		split.setBorder(BorderFactory.createEmptyBorder());
		split.getLeftComponent().setMinimumSize(new Dimension(280, 140));
		split.getRightComponent().setMinimumSize(new Dimension(240, 140));
		split.getLeftComponent().setPreferredSize(new Dimension(420, 420));
		split.getRightComponent().setPreferredSize(new Dimension(420, 420));
		add(buildTop(), BorderLayout.NORTH);
		add(split, BorderLayout.CENTER);
		wire();
		imageCheck.start();
	}

	private static int indexOfAnimation(String id) {
		for (int i = 0; i < MapSpriteSim.ANIMATIONS.size(); i++) {
			if (MapSpriteSim.ANIMATIONS.get(i).id().equals(id)) {
				return i;
			}
		}
		return 0;
	}

	private static JSlider slider(int value) {
		return new JSlider(0, 100, value);
	}

	private JComponent buildTop() {
		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		heading.setAlignmentX(LEFT_ALIGNMENT);
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.add(new JLabel("Race  "));
		raceBox.setPrototypeDisplayValue("A long race name here");
		row.add(raceBox);
		top.add(heading);
		top.add(Box.createVerticalStrut(8));
		top.add(row);
		top.add(Box.createVerticalStrut(4));
		top.add(status);
		return top;
	}

	private JComponent buildPreview() {
		ScrollablePanel panel = new ScrollablePanel(null, true);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
		int size = 160 + OUTLINE * 2;
		player.setPreferredSize(new Dimension(size, size));
		player.setMaximumSize(player.getPreferredSize());
		player.setMinimumSize(player.getPreferredSize());
		JPanel pictures = new JPanel(new WrapLayout(FlowLayout.LEFT, 12, 6));
		pictures.setAlignmentX(LEFT_ALIGNMENT);
		pictures.add(player);
		pictures.add(buildCompass());
		panel.add(pictures);
		Theme.style(reason, "small");
		JScrollPane reasonScroll = new JScrollPane(reason, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		reasonScroll.setBorder(BorderFactory.createEmptyBorder());
		reasonScroll.setOpaque(false);
		reasonScroll.getViewport().setOpaque(false);
		reasonScroll.setAlignmentX(LEFT_ALIGNMENT);
		reasonScroll.setPreferredSize(new Dimension(100, REASON_HEIGHT));
		reasonScroll.setMinimumSize(new Dimension(0, REASON_HEIGHT));
		reasonScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, REASON_HEIGHT));
		panel.add(reasonScroll);
		panel.add(Box.createVerticalStrut(4));
		tabs.setAlignmentX(LEFT_ALIGNMENT);
		tabs.addTab("Person", tabScroll(buildControls()));
		tabs.addTab("Addons", tabScroll(buildAddons()));
		tabs.addTab("Random", tabScroll(buildRandom()));
		panel.add(tabs);
		panel.add(Box.createVerticalStrut(10));
		Theme.style(animationNote, "small");
		panel.add(animationNote);
		panel.add(Box.createVerticalStrut(6));
		panel.add(warnings);
		panel.add(Box.createVerticalStrut(6));
		panel.add(notes);
		panel.add(Box.createVerticalStrut(6));
		WrappedText limits = new WrappedText("Only this mod and the game are layered, so other enabled mods aren't seen. Re-roll can't recreate a particular person from the game, and Sick uses a stand-in colour.", Theme.Tone.HELP);
		Theme.style(limits, "small");
		panel.add(limits);
		JScrollPane scroll = new JScrollPane(panel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
	}

	/** The first moment in each direction, laid out as a compass. Clicking one shows that direction. */
	private JComponent buildCompass() {
		JPanel grid = new JPanel(new GridLayout(3, 3, 2, 2));
		JComponent[] cells = new JComponent[9];
		for (int dir = 0; dir < 8; dir++) {
			int chosen = dir;
			JToggleButton button = new JToggleButton();
			button.setToolTipText(PlainTip.of("Face " + MapSpriteSim.DIRECTIONS.get(dir)));
			button.getAccessibleContext().setAccessibleName("Face " + MapSpriteSim.DIRECTIONS.get(dir));
			button.setFocusPainted(true);
			button.addActionListener(e -> chooseDirection(chosen));
			compass[dir] = button;
			cells[COMPASS_CELLS[dir]] = button;
		}
		Theme.style(directionLabel, "small");
		Theme.tone(directionLabel, Theme.Tone.HELP);
		cells[4] = directionLabel;
		for (JComponent cell : cells) {
			grid.add(cell);
		}
		compass[direction].setSelected(true);
		return grid;
	}

	private void chooseDirection(int dir) {
		direction = dir;
		for (int i = 0; i < 8; i++) {
			compass[i].setSelected(i == dir);
		}
		directionLabel.setText(MapSpriteSim.DIRECTIONS.get(dir));
		startRender();
	}

	private static JComponent tabScroll(JComponent content) {
		ScrollablePanel holder = new ScrollablePanel(new BorderLayout(), true);
		holder.setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 6));
		holder.add(content, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(holder, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setPreferredSize(new Dimension(100, TAB_HEIGHT));
		return scroll;
	}

	private JComponent buildControls() {
		JPanel grid = new JPanel(new GridBagLayout());
		grid.setAlignmentX(LEFT_ALIGNMENT);
		int y = 0;
		y = addRow(grid, y, "Type", typeBox);
		y = addRow(grid, y, "Animation", animationBox);
		scaleBox.setSelectedItem(4);
		y = addRow(grid, y, "Scale", scaleBox);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		buttons.add(playButton);
		buttons.add(reroll);
		buttons.add(lit);
		y = addRow(grid, y, "", buttons);
		y = addRow(grid, y, "Age", age);
		y = addRow(grid, y, "Clothes", clothes);
		y = addRow(grid, y, "Grime", grime);
		y = addRow(grid, y, "Injury", injury);
		y = addRow(grid, y, "Decay", decay);
		y = addRow(grid, y, "Plate", plate);
		y = addRow(grid, y, "Leather", leather);
		JPanel checks = new JPanel(new GridLayout(0, 3, 6, 2));
		for (JCheckBox box : List.of(noble, slave, parent, retired, sick, enemy, naked)) {
			checks.add(box);
		}
		addRow(grid, y, "", checks);
		return grid;
	}

	private JComponent buildAddons() {
		JPanel holder = new JPanel(new BorderLayout(0, 6));
		WrappedText help = new WrappedText("A type's ADDONS, in order. Each draws a pair of frames from the type's sheet, at its ADDON_INDEX, if its CONDITIONS let this person have it. Change the person to see others.",
				Theme.Tone.HELP);
		Theme.style(help, "small");
		holder.add(help, BorderLayout.NORTH);
		addonsPanel.setLayout(new BoxLayout(addonsPanel, BoxLayout.Y_AXIS));
		holder.add(addonsPanel, BorderLayout.CENTER);
		return holder;
	}

	private JComponent buildRandom() {
		JPanel grid = new JPanel(new GridBagLayout());
		int y = addRow(grid, 0, "Seed", seedField);
		seedField.addActionListener(e -> seedTyped());
		seedField.addFocusListener(new java.awt.event.FocusAdapter() {
			@Override
			public void focusLost(java.awt.event.FocusEvent e) {
				seedTyped();
			}
		});
		for (int i = 0; i < randomSliders.length; i++) {
			int index = i;
			randomSliders[i] = new JSlider(0, 255, 0);
			randomLabels[i] = new JLabel("0.000");
			Theme.style(randomLabels[i], "monospaced");
			randomSliders[i].addChangeListener(e -> {
				if (!syncing) {
					randomBytes[index] = randomSliders[index].getValue();
					startRender();
				}
			});
			JPanel row = new JPanel(new BorderLayout(6, 0));
			row.add(randomSliders[i], BorderLayout.CENTER);
			row.add(randomLabels[i], BorderLayout.EAST);
			y = addRow(grid, y, "RANDOM_" + i, row);
		}
		return grid;
	}

	private void seedTyped() {
		try {
			long value = Long.parseLong(seedField.getText().trim());
			if (value != seed) {
				seed = value;
				java.util.Arrays.fill(randomBytes, -1);
				startRender();
			}
		} catch (NumberFormatException e) {
			seedField.setText(String.valueOf(seed));
		}
	}

	private static int addRow(JPanel grid, int y, String label, JComponent field) {
		GridBagConstraints c = new GridBagConstraints();
		c.gridy = y;
		c.gridx = 0;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(2, 0, 2, 10);
		grid.add(new JLabel(label), c);
		c = new GridBagConstraints();
		c.gridy = y;
		c.gridx = 1;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(2, 0, 2, 0);
		grid.add(field, c);
		return y + 1;
	}

	private JComponent buildEditor() {
		JPanel panel = new JPanel(new BorderLayout(0, 6));
		panel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
		JPanel top = new JPanel(new BorderLayout(8, 0));
		Theme.tone(fileLabel, Theme.Tone.HELP);
		Theme.style(fileLabel, "small");
		top.add(fileLabel, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		Theme.style(saveButton, "primary");
		copyButton.setVisible(false);
		buttons.add(copyButton);
		buttons.add(saveButton);
		top.add(buttons, BorderLayout.EAST);
		JPanel north = new JPanel();
		north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
		top.setAlignmentX(LEFT_ALIGNMENT);
		north.add(top);
		north.add(editorNote);
		editorNote.setVisible(false);
		Theme.style(editor, "monospaced");
		editor.setTabSize(4);
		JScrollPane scroll = new JScrollPane(editor);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		defaultsList.setLayout(new BoxLayout(defaultsList, BoxLayout.Y_AXIS));
		JScrollPane defaults = new JScrollPane(defaultsList, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		defaults.setBorder(BorderFactory.createEmptyBorder());
		defaults.setPreferredSize(new Dimension(100, DEFAULTS_HEIGHT));
		defaults.getVerticalScrollBar().setUnitIncrement(16);
		panel.add(north, BorderLayout.NORTH);
		panel.add(scroll, BorderLayout.CENTER);
		panel.add(defaults, BorderLayout.SOUTH);
		return panel;
	}

	private void wire() {
		raceBox.addActionListener(e -> raceChosen());
		for (JComboBox<?> box : List.of(typeBox, animationBox, scaleBox)) {
			box.addActionListener(e -> {
				if (!loading) {
					startRender();
				}
			});
		}
		for (JSlider slider : List.of(age, clothes, grime, injury, decay, plate, leather)) {
			slider.addChangeListener(e -> startRender());
		}
		for (JCheckBox box : List.of(noble, slave, parent, retired, sick, enemy, naked, lit)) {
			box.addActionListener(e -> startRender());
		}
		playButton.addActionListener(e -> {
			paused = !paused;
			playButton.setText(paused ? "Play" : "Pause");
			player.setPlaying(!paused);
		});
		reroll.addActionListener(e -> {
			seed = System.nanoTime() & 0xFFFFFFFFL;
			seedField.setText(String.valueOf(seed));
			java.util.Arrays.fill(randomBytes, -1);
			startRender();
		});
		saveButton.addActionListener(e -> save());
		copyButton.addActionListener(e -> copy());
		editor.getDocument().addUndoableEditListener(e -> {
			if (!loading) {
				undo.addEdit(e.getEdit());
			}
		});
		editor.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				edited();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				edited();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				edited();
			}
		});
		editor.addCaretListener(e -> SwingUtilities.invokeLater(this::showDefaults));
		int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
		bind("undo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcut), () -> {
			try {
				if (undo.canUndo()) {
					undo.undo();
				}
			} catch (CannotUndoException e) {
				// Nothing to undo.
			}
		});
		bind("redo", KeyStroke.getKeyStroke(KeyEvent.VK_Y, shortcut), () -> {
			try {
				if (undo.canRedo()) {
					undo.redo();
				}
			} catch (CannotRedoException e) {
				// Nothing to redo.
			}
		});
		bind("save", KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcut), this::save);
		addHierarchyListener(e -> {
			player.setPlaying(isShowing() && !paused);
			if (isShowing()) {
				SwingUtilities.invokeLater(this::fitSplit);
			}
		});
	}

	/**
	 * A width change while this screen was hidden lays the split out at no size, which can leave a side below its
	 * minimum once shown. The halves are put back even then.
	 */
	private void fitSplit() {
		boolean horizontal = split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT;
		int left = horizontal ? split.getLeftComponent().getWidth() : split.getLeftComponent().getHeight();
		int right = horizontal ? split.getRightComponent().getWidth() : split.getRightComponent().getHeight();
		int leftMin = horizontal ? split.getLeftComponent().getMinimumSize().width : split.getLeftComponent().getMinimumSize().height;
		int rightMin = horizontal ? split.getRightComponent().getMinimumSize().width : split.getRightComponent().getMinimumSize().height;
		if (split.getWidth() > 0 && (left < leftMin || right < rightMin)) {
			split.setDividerLocation(0.5);
			split.validate();
		}
	}

	private void bind(String name, KeyStroke key, Runnable action) {
		editor.getInputMap().put(key, name);
		editor.getActionMap().put(name, new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				action.run();
			}
		});
	}

	private void edited() {
		if (loading) {
			return;
		}
		status.setText(" ");
		updateSave();
		// Not restarted on each key, so the picture keeps up while someone is still typing.
		if (!debounce.isRunning()) {
			debounce.start();
		}
	}

	/** Opens a mod showing one of its races. */
	public void open(ModEntry value, String race) {
		wantedRace = race;
		open(value);
	}

	public void open(ModEntry value) {
		status.setText(" ");
		Path game = gameFolder.get();
		work(() -> {
			try {
				closeSession();
				if (!GameInstall.isValid(game)) {
					throw new IOException("The game folder isn't set.");
				}
				session = MapSpriteSim.open(game, value);
				List<PortraitSim.Race> races = session.races();
				SwingUtilities.invokeLater(() -> showRaces(races));
			} catch (GameParser.UnavailableException | IOException e) {
				SwingUtilities.invokeLater(() -> status.setText("The game's files couldn't be read: " + e.getMessage()));
			}
		});
	}

	private void closeSession() {
		if (session != null) {
			session.close();
			session = null;
		}
	}

	private void work(Runnable job) {
		running.incrementAndGet();
		worker.execute(() -> {
			try {
				job.run();
			} finally {
				running.decrementAndGet();
			}
		});
	}

	private void showRaces(List<PortraitSim.Race> races) {
		loading = true;
		raceBox.setModel(new DefaultComboBoxModel<>(races.stream().map(PortraitSim.Race::name).toArray(String[]::new)));
		String previous = wantedRace != null ? wantedRace : shownRace;
		wantedRace = null;
		if (races.stream().anyMatch(race -> race.name().equals(previous))) {
			raceBox.setSelectedItem(previous);
		}
		loading = false;
		shownRace = null;
		if (races.isEmpty()) {
			opened = null;
			setEditor("", false);
			player.clear();
			player.setProblem(false);
			reason.setText(" ");
			fileLabel.setText(" ");
			status.setText("This mod has no races.");
			updateSave();
			return;
		}
		loadRace((String) raceBox.getSelectedItem());
	}

	private void raceChosen() {
		if (loading) {
			return;
		}
		String chosen = (String) raceBox.getSelectedItem();
		if (chosen == null || chosen.equals(shownRace)) {
			return;
		}
		if (!confirmLeave()) {
			loading = true;
			raceBox.setSelectedItem(shownRace);
			loading = false;
			return;
		}
		loadRace(chosen);
	}

	private void loadRace(String name) {
		shownRace = name;
		MapSpriteSim.Session current = session;
		if (current == null) {
			return;
		}
		work(() -> {
			try {
				PortraitSim.Opened result = current.open(name);
				SwingUtilities.invokeLater(() -> showOpened(result));
			} catch (PortraitSim.Refused | IOException e) {
				SwingUtilities.invokeLater(() -> status.setText(e.getMessage()));
			}
		});
	}

	private void showOpened(PortraitSim.Opened result) {
		opened = result;
		baseline = result.text();
		loading = true;
		typeBox.setModel(new DefaultComboBoxModel<>());
		loading = false;
		setEditor(result.text(), result.race().editable());
		fileLabel.setText(result.file().isEmpty() ? " " : result.file());
		fileLabel.setToolTipText(result.file().isEmpty() ? null : PlainTip.of(result.file()));
		StringBuilder note = new StringBuilder();
		if (result.race().note() != null) {
			note.append(result.race().note());
		}
		if (!result.race().sharedWith().isEmpty()) {
			note.append(note.length() > 0 ? " " : "").append("This file is shared with ").append(String.join(", ", result.race().sharedWith())).append(", so saving changes them too.");
		}
		note.append(note.length() > 0 ? " " : "").append("The portrait's tags are in this file too, and the Race Portrait Sim edits them.");
		editorNote.setText(note.toString());
		editorNote.setVisible(true);
		copyButton.setVisible(result.race().canCopy());
		status.setText(" ");
		updateSave();
		showDefaults();
		startRender();
	}

	private void setEditor(String text, boolean editable) {
		loading = true;
		editor.setText(text);
		editor.setCaretPosition(0);
		editor.setEditable(editable);
		undo.discardAllEdits();
		loading = false;
	}

	private void updateSave() {
		boolean editable = opened != null && opened.race().editable();
		saveButton.setEnabled(editable && dirty() && !red);
	}

	private boolean dirty() {
		return !editor.getText().equals(baseline);
	}

	private MapSpriteSim.Animation selectedAnimation() {
		return MapSpriteSim.ANIMATIONS.get(Math.max(0, animationBox.getSelectedIndex()));
	}

	private MapSpriteSim.Person person() {
		Object scale = scaleBox.getSelectedItem();
		return new MapSpriteSim.Person(Math.max(0, typeBox.getSelectedIndex()), selectedAnimation().id(), direction, seed, age.getValue() / 100.0, noble.isSelected(), slave.isSelected(), parent.isSelected(), retired.isSelected(),
				sick.isSelected(), enemy.isSelected(), naked.isSelected(), clothes.getValue(), plate.getValue(), leather.getValue(), grime.getValue() / 100.0, injury.getValue() / 100.0, decay.getValue() / 100.0,
				scale == null ? 4 : (Integer) scale, lit.isSelected(), new PortraitSim.Pins(Map.of(), Map.of(), randomBytes.clone()));
	}

	/** One drawing at a time. Whatever changed meanwhile is drawn straight after, so the latest text always wins. */
	private void startRender() {
		MapSpriteSim.Session current = session;
		if (current == null || opened == null || shownRace == null) {
			return;
		}
		if (rendering) {
			renderAgain = true;
			return;
		}
		rendering = true;
		boolean editable = opened.race().editable();
		String text = editable ? editor.getText() : null;
		String race = shownRace;
		MapSpriteSim.Person person = person();
		work(() -> {
			MapSpriteSim.Preview preview = null;
			String failure = null;
			try {
				preview = current.preview(race, text, person);
			} catch (PortraitSim.Refused | IOException | RuntimeException e) {
				failure = e.getMessage() == null ? e.toString() : e.getMessage();
			}
			MapSpriteSim.Preview done = preview;
			String failed = failure;
			SwingUtilities.invokeLater(() -> finishRender(done, failed));
		});
	}

	private void finishRender(MapSpriteSim.Preview preview, String failure) {
		rendering = false;
		MapSpriteSim.Animation animation = selectedAnimation();
		animationNote.setText(animation.label() + " (" + animation.id() + ", " + animation.evidence() + ")" + (animation.fps() > 0 ? ", " + animation.fps() + " steps a second" : ""));
		if (failure != null) {
			showProblem(failure, null);
		} else if (preview.state() == PortraitSim.State.RED) {
			showProblem(preview.problem(), preview.line());
		} else {
			red = false;
			player.setProblem(false);
			reason.setText(" ");
			player.show(preview);
			player.setPlaying(!paused);
			for (int dir = 0; dir < 8 && dir < preview.compass().size(); dir++) {
				compass[dir].setIcon(new ImageIcon(image(preview.compassSize(), preview.compassSize(), preview.compass().get(dir))));
			}
			showTypes(preview.types());
			syncClothes(preview.clothesMax());
			warnings.setText(String.join("\n", preview.warnings()));
			notes.setText(String.join("\n", preview.notes()));
			if (!preview.addons().equals(lastAddons)) {
				showAddons(preview.addons());
			}
			syncRandom(preview.random());
		}
		updateSave();
		if (renderAgain) {
			renderAgain = false;
			startRender();
		}
	}

	private void showAddons(List<MapSpriteSim.AddonRow> rows) {
		lastAddons = rows;
		addonsPanel.removeAll();
		if (rows.isEmpty()) {
			JLabel none = new JLabel("This type has no addons.");
			Theme.tone(none, Theme.Tone.HELP);
			addonsPanel.add(none);
		}
		for (MapSpriteSim.AddonRow row : rows) {
			String text = (row.item() + 1) + ". ADDON_INDEX " + row.index() + ", " + (row.belowHead() ? "below" : "above") + " the head, " + (row.colour() == null ? "white" : row.colour()) + (row.shown() ? "" : ": not for this person");
			JLabel label = new JLabel(text);
			Theme.style(label, "monospaced");
			if (!row.shown()) {
				Theme.tone(label, Theme.Tone.GHOST);
			}
			label.setAlignmentX(LEFT_ALIGNMENT);
			addonsPanel.add(label);
		}
		addonsPanel.revalidate();
		addonsPanel.repaint();
	}

	private void syncClothes(int max) {
		if (clothes.getMaximum() != max) {
			boolean full = clothes.getValue() == clothes.getMaximum();
			clothes.setMaximum(max);
			if (full) {
				clothes.setValue(max);
			}
		}
	}

	private void syncRandom(List<Integer> bytes) {
		syncing = true;
		for (int i = 0; i < randomSliders.length && i < bytes.size(); i++) {
			if (!randomSliders[i].getValueIsAdjusting()) {
				randomSliders[i].setValue(bytes.get(i));
			}
			randomLabels[i].setText(String.format(java.util.Locale.ROOT, "%.3f", bytes.get(i) / 255.0));
		}
		syncing = false;
	}

	/** The last good picture stays up, and only the outline and this one line change. */
	private void showProblem(String problem, Integer line) {
		red = true;
		player.setProblem(true);
		reason.setText(line == null ? problem : "Line " + line + ": " + problem);
	}

	private void showTypes(List<String> types) {
		List<String> current = new ArrayList<>();
		for (int i = 0; i < typeBox.getItemCount(); i++) {
			current.add(typeBox.getItemAt(i));
		}
		if (!current.equals(types)) {
			int selected = Math.max(0, Math.min(types.size() - 1, typeBox.getSelectedIndex()));
			loading = true;
			typeBox.setModel(new DefaultComboBoxModel<>(types.toArray(String[]::new)));
			if (!types.isEmpty()) {
				typeBox.setSelectedIndex(selected);
			}
			loading = false;
		}
	}

	/** A modder saving a sheet in an image editor sees it here without touching anything. */
	private void checkImages() {
		MapSpriteSim.Session current = session;
		if (current == null || opened == null || !isShowing() || running.get() > 0) {
			return;
		}
		work(() -> {
			if (current.imagesChanged()) {
				SwingUtilities.invokeLater(this::startRender);
			}
		});
	}

	/** Absent tags with a default, for the block the cursor is in, each with a button to add it. */
	private void showDefaults() {
		defaultsList.removeAll();
		if (opened != null && opened.race().editable()) {
			List<PortraitSection.AbsentBlock> blocks = MapSpriteSim.absent(editor.getText());
			PortraitSection.AbsentBlock block = PortraitSim.blockAt(blocks, editor.getCaretPosition());
			if (block != null) {
				JLabel title = new JLabel(block.path());
				Theme.style(title, "small");
				Theme.tone(title, Theme.Tone.HELP);
				title.setAlignmentX(LEFT_ALIGNMENT);
				defaultsList.add(title);
				for (PortraitSection.Absent absent : block.absent()) {
					defaultsList.add(defaultRow(block, absent));
				}
			}
		}
		defaultsList.revalidate();
		defaultsList.repaint();
	}

	private JComponent defaultRow(PortraitSection.AbsentBlock block, PortraitSection.Absent absent) {
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		JLabel label = new JLabel(absent.key() + ": " + absent.value());
		Theme.tone(label, Theme.Tone.GHOST);
		Theme.style(label, "monospaced");
		JButton add = new JButton("Add");
		add.addActionListener(e -> {
			int caret = editor.getCaretPosition();
			String before = editor.getText();
			String after = PortraitSim.withDefault(before, block, absent.key(), absent.value());
			int at = block.end() - 1;
			try {
				// An insert rather than new text, so the scroll position and undo history survive.
				editor.getDocument().insertString(at, after.substring(at, at + after.length() - before.length()), null);
			} catch (javax.swing.text.BadLocationException failure) {
				return;
			}
			editor.setCaretPosition(Math.min(caret, editor.getDocument().getLength()));
			showDefaults();
		});
		row.add(label, BorderLayout.CENTER);
		row.add(add, BorderLayout.EAST);
		return row;
	}

	private void save() {
		MapSpriteSim.Session current = session;
		PortraitSim.Opened target = opened;
		if (current == null || target == null || !saveButton.isEnabled()) {
			return;
		}
		String text = editor.getText();
		saveButton.setEnabled(false);
		work(() -> {
			try {
				boolean changed = current.save(target.race().name(), target.revision(), text);
				PortraitSim.Opened reopened = current.open(target.race().name());
				SwingUtilities.invokeLater(() -> {
					opened = reopened;
					baseline = text;
					status.setText(changed ? "Saved." : "There was nothing to change.");
					updateSave();
				});
			} catch (PortraitSim.Refused | IOException e) {
				SwingUtilities.invokeLater(() -> {
					status.setText(e.getMessage());
					updateSave();
				});
			}
		});
	}

	private void copy() {
		MapSpriteSim.Session current = session;
		PortraitSim.Opened target = opened;
		if (current == null || target == null || !target.race().canCopy()) {
			return;
		}
		boolean merged = target.race().note() != null && target.race().note().contains("merges");
		String question = "Give " + target.race().name() + " its own copy of its sprite file, and point the race at it?"
				+ (merged ? " The copy is the game's merged result, so comments in this mod's file won't carry over." : "");
		if (!copyPrompt.test(question)) {
			return;
		}
		String race = target.race().name();
		work(() -> {
			try {
				PortraitSim.Copied copied = current.copyToMod(race);
				PortraitSim.Opened reopened = current.open(race);
				SwingUtilities.invokeLater(() -> {
					showOpened(reopened);
					status.setText("Copied to " + copied.file() + ".");
				});
			} catch (PortraitSim.Refused | IOException e) {
				SwingUtilities.invokeLater(() -> status.setText(e.getMessage()));
			}
		});
	}

	/** Whether it's fine to leave this race or this screen, asking first if something would be lost. */
	public boolean confirmLeave() {
		return !dirty() || discardPrompt.test("Discard your changes to this race's map sprite tags? Its file hasn't been changed.");
	}

	/** Side by side when there's room, stacked when there isn't. */
	public void setBand(WidthBand band) {
		int orientation = band == WidthBand.NARROW ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT;
		if (split.getOrientation() != orientation) {
			split.setOrientation(orientation);
			split.resetToPreferredSizes();
		}
	}

	public boolean isBusy() {
		return running.get() > 0 || rendering || debounce.isRunning();
	}

	public List<String> raceNames() {
		List<String> names = new ArrayList<>();
		for (int i = 0; i < raceBox.getItemCount(); i++) {
			names.add(raceBox.getItemAt(i));
		}
		return names;
	}

	public void selectRace(String name) {
		raceBox.setSelectedItem(name);
	}

	public void selectAnimation(String id) {
		animationBox.setSelectedIndex(indexOfAnimation(id));
	}

	public void selectType(int index) {
		typeBox.setSelectedIndex(index);
	}

	public int typeCount() {
		return typeBox.getItemCount();
	}

	public void clickDirection(int dir) {
		compass[dir].doClick();
	}

	public int direction() {
		return direction;
	}

	public String editorText() {
		return editor.getText();
	}

	public void typeInEditor(String text) {
		editor.setText(text);
	}

	public boolean isRed() {
		return red;
	}

	public String reasonText() {
		return reason.getText();
	}

	public String statusText() {
		return status.getText();
	}

	public boolean hasPreview() {
		return player.hasImage();
	}

	public int frameCount() {
		return player.frameCount();
	}

	public boolean isPlaying() {
		return player.playing();
	}

	public void clickPlay() {
		playButton.doClick();
	}

	public boolean isEditorLocked() {
		return !editor.isEditable();
	}

	public boolean canSave() {
		return saveButton.isEnabled();
	}

	public boolean canCopy() {
		return copyButton.isVisible();
	}

	public void clickSave() {
		saveButton.doClick();
	}

	public void clickCopy() {
		copyButton.doClick();
	}

	public List<MapSpriteSim.AddonRow> addonRows() {
		return lastAddons;
	}

	public int defaultsListed() {
		return Math.max(0, defaultsList.getComponentCount() - 1);
	}

	public void placeCaret(int offset) {
		editor.setCaretPosition(offset);
		showDefaults();
	}

	public void selectTab(String title) {
		tabs.setSelectedIndex(tabs.indexOfTab(title));
	}

	public void setDiscardPrompt(Predicate<String> prompt) {
		discardPrompt = prompt;
	}

	public void setCopyPrompt(Predicate<String> prompt) {
		copyPrompt = prompt;
	}
}
