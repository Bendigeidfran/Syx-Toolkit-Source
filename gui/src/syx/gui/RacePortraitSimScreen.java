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
import java.util.HashMap;
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
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
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
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;

import syx.core.GameInstall;
import syx.core.GameParser;
import syx.core.ModEntry;
import syx.core.PortraitSection;
import syx.core.PortraitSim;

/**
 * A live preview of a race's portrait beside its portrait tags. Everything it shows comes from {@link PortraitSim}.
 * Broken text is normal while someone is typing, so it's marked quietly and nothing on the screen moves.
 */
public final class RacePortraitSimScreen extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final int DEBOUNCE_MS = 150;
	private static final int IMAGE_CHECK_MS = 1000;
	private static final int OUTLINE = 2;
	private static final int REASON_HEIGHT = 38;
	private static final int DEFAULTS_HEIGHT = 92;
	private static final int TAB_HEIGHT = 300;
	private static final int SWATCH = 20;
	private static final int PIECE_COUNT_WIDTH = 84;
	private static final int PIECE_ROW_WIDTH = 46;

	/** The portrait, with a thin outline when the text can't be loaded. The space for the outline is always there. */
	private static final class Portrait extends JComponent {
		private static final long serialVersionUID = 1L;
		private transient BufferedImage image;
		private boolean problem;

		Portrait() {
			setOpaque(false);
			setAlignmentX(LEFT_ALIGNMENT);
		}

		void show(int width, int height, int[] argb) {
			BufferedImage next = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			next.setRGB(0, 0, width, height, argb, 0, width);
			image = next;
			setPreferredSize(new Dimension(width + OUTLINE * 2, height + OUTLINE * 2));
			setMaximumSize(getPreferredSize());
			setMinimumSize(getPreferredSize());
			revalidate();
			repaint();
		}

		void clear() {
			image = null;
			repaint();
		}

		void setProblem(boolean value) {
			if (problem != value) {
				problem = value;
				repaint();
			}
		}

		boolean hasImage() {
			return image != null;
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			try {
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
				if (image != null) {
					g.drawImage(image, OUTLINE, OUTLINE, null);
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

	private final transient Supplier<Path> gameFolder;
	private final JLabel heading = new JLabel("Race Portrait Sim");
	private final WrappedText status = new WrappedText(" ", Theme.Tone.HELP);
	private final JComboBox<String> raceBox = new JComboBox<>();
	private final JComboBox<String> typeBox = new JComboBox<>();
	private final JComboBox<PortraitSim.OverlayChoice> overlayBox = new JComboBox<>(PortraitSim.OverlayChoice.values());
	private final JComboBox<Integer> scaleBox = new JComboBox<>(new Integer[] { 1, 2, 3, 4, 5, 6, 8 });
	private final JButton reroll = new JButton("Re-roll");
	private final JSlider age = slider(40);
	private final JSlider grime = slider(0);
	private final JSlider injury = slider(0);
	private final JSlider plate = new JSlider(0, 5, 0);
	private final JSlider leather = new JSlider(0, 5, 0);
	private final JCheckBox dead = new JCheckBox("Dead");
	private final JCheckBox noble = new JCheckBox("Noble");
	private final JCheckBox slave = new JCheckBox("Slave");
	private final JCheckBox parent = new JCheckBox("Parent");
	private final JCheckBox retired = new JCheckBox("Retired");
	private final JCheckBox sick = new JCheckBox("Sick");
	private final Portrait portrait = new Portrait();
	private final WrappedText reason = new WrappedText(" ", Theme.Tone.ERROR);
	private final WrappedText warnings = new WrappedText(" ", Theme.Tone.WARNING);
	private final WrappedText notes = new WrappedText(" ", Theme.Tone.HELP);
	private final JTextArea editor = new JTextArea();
	private final WrappedText editorNote = new WrappedText(" ", Theme.Tone.WARNING);
	private final JLabel fileLabel = new JLabel(" ");
	private final JButton saveButton = new JButton("Save");
	private final JButton copyButton = new JButton("Copy to Mod");
	private final JPanel defaultsList = new JPanel();
	private final JSplitPane split;
	private final JTabbedPane tabs = new JTabbedPane();
	private final JPanel coloursPanel = new JPanel();
	private final JPanel piecesPanel = new JPanel();
	private final JButton releaseColours = new JButton("Release pinned colours");
	private final JButton releasePieces = new JButton("Release pinned pieces");
	private final JTextField seedField = new JTextField("1", 12);
	private final JSlider[] randomSliders = new JSlider[8];
	private final JLabel[] randomLabels = new JLabel[8];
	private final int[] randomBytes = PortraitSim.Pins.NONE.random().clone();
	private final transient Map<Integer, Integer> framePins = new HashMap<>();
	private final transient Map<String, Integer> colourPins = new HashMap<>();
	private transient List<PortraitSim.ColourRow> lastColours = List.of();
	private transient PortraitSim.ColourSlot lastBlood;
	private transient List<PortraitSim.PieceRow> lastPieces = List.of();
	private transient CompoundEdit pickerGroup;
	private boolean coloursLocked;
	private boolean piecesLocked;
	private boolean syncing;
	private final transient UndoManager undo = new UndoManager();
	private final transient ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "race-portrait-sim");
		thread.setDaemon(true);
		return thread;
	});
	private final transient AtomicInteger running = new AtomicInteger();
	private final Timer debounce = new Timer(DEBOUNCE_MS, e -> startRender());
	private final Timer imageCheck = new Timer(IMAGE_CHECK_MS, e -> checkImages());
	private transient Predicate<String> discardPrompt = question -> JOptionPane.showConfirmDialog(this, question, "Unsaved portrait", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
	private transient Predicate<String> copyPrompt = question -> JOptionPane.showConfirmDialog(this, question, "Copy to Mod", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
	private transient ModEntry mod;
	private transient PortraitSim.Session session;
	private transient PortraitSim.Opened opened;
	private String baseline = "";
	private String shownRace;
	private boolean loading;
	private boolean rendering;
	private boolean renderAgain;
	private boolean red;
	private long seed = 1;
	private int textChanges;

	public RacePortraitSimScreen(Supplier<Path> gameFolder) {
		super(new BorderLayout(0, 12));
		this.gameFolder = gameFolder;
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		Theme.style(heading, "h2");
		debounce.setRepeats(false);
		split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildPreview(), buildEditor());
		split.setResizeWeight(0.5);
		split.setBorder(BorderFactory.createEmptyBorder());
		split.getLeftComponent().setMinimumSize(new Dimension(280, 140));
		split.getRightComponent().setMinimumSize(new Dimension(240, 140));
		// Equal, so the two halves start as halves whatever their contents would prefer.
		split.getLeftComponent().setPreferredSize(new Dimension(420, 420));
		split.getRightComponent().setPreferredSize(new Dimension(420, 420));
		add(buildTop(), BorderLayout.NORTH);
		add(split, BorderLayout.CENTER);
		wire();
		imageCheck.start();
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
		portrait.setPreferredSize(new Dimension(160 + OUTLINE * 2, 256 + OUTLINE * 2));
		portrait.setMaximumSize(portrait.getPreferredSize());
		portrait.setMinimumSize(portrait.getPreferredSize());
		panel.add(portrait);
		panel.add(Box.createVerticalStrut(4));
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
		tabs.addTab("Colours", tabScroll(coloursPanel));
		tabs.addTab("Pieces", tabScroll(buildPieces()));
		tabs.addTab("Random", tabScroll(buildRandom()));
		panel.add(tabs);
		panel.add(Box.createVerticalStrut(10));
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

	private JComponent buildPieces() {
		JPanel holder = new JPanel(new BorderLayout(0, 6));
		WrappedText help = new WrappedText("The arrows choose which of a piece's frames to look at, which the game rolls per person, so they don't change the text. Row changes which sheet row that frame uses, and does.", Theme.Tone.HELP);
		Theme.style(help, "small");
		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.add(help);
		releasePieces.setAlignmentX(LEFT_ALIGNMENT);
		releasePieces.addActionListener(e -> {
			framePins.clear();
			startRender();
		});
		top.add(Box.createVerticalStrut(4));
		top.add(releasePieces);
		holder.add(top, BorderLayout.NORTH);
		holder.add(piecesPanel, BorderLayout.CENTER);
		piecesPanel.setLayout(new BoxLayout(piecesPanel, BoxLayout.Y_AXIS));
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

	/** A swatch of a colour the race's file holds. Those are data, not part of the theme. */
	private static final class Swatch extends JButton {
		private static final long serialVersionUID = 1L;
		private final java.awt.Color shown;
		private final boolean active;

		Swatch(PortraitSim.ColourSlot slot, boolean active) {
			this.shown = new java.awt.Color(slot.r(), slot.g(), slot.b());
			this.active = active;
			setPreferredSize(new Dimension(SWATCH, SWATCH));
			setMinimumSize(getPreferredSize());
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(true);
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			try {
				g.setColor(shown);
				g.fillRect(0, 0, getWidth(), getHeight());
				if (active) {
					// Black and white together, because it has to show up on whatever colour the swatch is, not on the theme.
					g.setColor(java.awt.Color.BLACK);
					g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
					g.setColor(java.awt.Color.WHITE);
					g.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
					g.setColor(java.awt.Color.BLACK);
					g.drawRect(2, 2, getWidth() - 5, getHeight() - 5);
				}
			} finally {
				g.dispose();
			}
		}
	}

	private void showColours(PortraitSim.Preview preview) {
		boolean locked = !editor.isEditable();
		lastColours = preview.colours();
		lastBlood = preview.blood();
		coloursLocked = locked;
		coloursPanel.removeAll();
		coloursPanel.setLayout(new BoxLayout(coloursPanel, BoxLayout.Y_AXIS));
		releaseColours.setAlignmentX(LEFT_ALIGNMENT);
		coloursPanel.add(releaseColours);
		coloursPanel.add(Box.createVerticalStrut(6));
		for (PortraitSim.ColourRow row : preview.colours()) {
			coloursPanel.add(colourRow(row, locked));
			coloursPanel.add(Box.createVerticalStrut(10));
		}
		if (preview.blood() != null) {
			coloursPanel.add(bloodRow(preview.blood(), locked));
		}
		coloursPanel.revalidate();
		coloursPanel.repaint();
	}

	private JComponent colourRow(PortraitSim.ColourRow row, boolean locked) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setAlignmentX(LEFT_ALIGNMENT);
		JLabel name = new JLabel(row.name());
		Theme.style(name, "semibold");
		name.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(name);
		if (row.note() != null) {
			WrappedText note = new WrappedText(row.note(), Theme.Tone.HELP);
			Theme.style(note, "small");
			panel.add(note);
		}
		JPanel swatches = new JPanel(new GridLayout(2, 8, 2, 2));
		swatches.setAlignmentX(LEFT_ALIGNMENT);
		swatches.setMaximumSize(new Dimension(8 * (SWATCH + 2), 2 * (SWATCH + 2)));
		for (int i = 0; i < row.slots().size(); i++) {
			int slot = i;
			PortraitSim.ColourSlot colour = row.slots().get(i);
			Swatch swatch = new Swatch(colour, i == row.active());
			swatch.setToolTipText(PlainTip.of(row.name() + " slot " + i + ": " + colour.r() + " " + colour.g() + " " + colour.b()));
			swatch.getAccessibleContext().setAccessibleName(row.name() + " slot " + i);
			swatch.addActionListener(e -> chooseSlot(row, slot));
			swatches.add(swatch);
		}
		panel.add(Box.createVerticalStrut(2));
		panel.add(swatches);
		panel.add(Box.createVerticalStrut(3));
		PortraitSim.ColourSlot active = row.slots().get(row.active());
		boolean canChange = !locked && row.editable() && active.entry() >= 0;
		panel.add(colourControls(active, canChange, () -> pickColour(active, (r, g, b) -> setColour(row.name(), active.entry(), r, g, b)), () -> applyEdit(PortraitSim.colourReset(editor.getText(), baseline, row.name(), active.entry()))));
		if (row.editable() && !locked && active.entry() < 0) {
			WrappedText generated = new WrappedText("This shade is generated by GENERATE_RANDOMIZE, so it can't be changed by itself.", Theme.Tone.HELP);
			Theme.style(generated, "small");
			panel.add(generated);
		}
		return panel;
	}

	private JComponent bloodRow(PortraitSim.ColourSlot blood, boolean locked) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setAlignmentX(LEFT_ALIGNMENT);
		JLabel name = new JLabel("Blood");
		Theme.style(name, "semibold");
		name.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(name);
		panel.add(colourControls(blood, !locked, () -> pickColour(blood, this::setBlood), () -> applyEdit(PortraitSim.colourReset(editor.getText(), baseline, null, 0))));
		return panel;
	}

	private JComponent colourControls(PortraitSim.ColourSlot colour, boolean canChange, Runnable change, Runnable reset) {
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		JLabel values = new JLabel(colour.r() + " " + colour.g() + " " + colour.b());
		Theme.style(values, "monospaced");
		JButton changeButton = new JButton("Change...");
		changeButton.setEnabled(canChange);
		changeButton.addActionListener(e -> change.run());
		JButton resetButton = new JButton("Reset");
		resetButton.setEnabled(canChange);
		resetButton.addActionListener(e -> reset.run());
		row.add(values);
		row.add(changeButton);
		row.add(resetButton);
		return row;
	}

	private interface RgbSetter {
		void set(int r, int g, int b);
	}

	/** The portrait and the text follow the picker as it moves. Cancel undoes all of it as one step. */
	private void pickColour(PortraitSim.ColourSlot start, RgbSetter setter) {
		JColorChooser chooser = new JColorChooser(new java.awt.Color(start.r(), start.g(), start.b()));
		chooser.getSelectionModel().addChangeListener(e -> {
			java.awt.Color color = chooser.getColor();
			setter.set(color.getRed(), color.getGreen(), color.getBlue());
		});
		CompoundEdit group = new CompoundEdit();
		boolean[] cancelled = new boolean[1];
		pickerGroup = group;
		JColorChooser.createDialog(this, "Choose a colour", true, chooser, null, e -> cancelled[0] = true).setVisible(true);
		pickerGroup = null;
		group.end();
		if (cancelled[0]) {
			if (group.canUndo()) {
				group.undo();
			}
		} else {
			undo.addEdit(group);
		}
	}

	/** Which slot this person shows is rolled, so choosing one isn't a change to the file. The text moves to where that colour is. */
	private void chooseSlot(PortraitSim.ColourRow row, int slot) {
		colourPins.put(row.name(), slot);
		int entry = row.slots().get(slot).entry();
		showInText(entry < 0 ? null : PortraitSim.colourOffset(editor.getText(), row.name(), entry));
		startRender();
	}

	private void setColour(String colour, int entry, int r, int g, int b) {
		applyEdit(PortraitSim.colourEdit(editor.getText(), colour, entry, r, g, b));
	}

	private void setBlood(int r, int g, int b) {
		applyEdit(PortraitSim.bloodEdit(editor.getText(), r, g, b));
	}

	/** Changes the text in place, so the undo history and the scroll position survive, then shows where. */
	private void applyEdit(PortraitSection.TextEdit edit) {
		if (edit == null) {
			status.setText("That couldn't be changed in the text as it stands. Fix any mistake in it first.");
			return;
		}
		if (edit.text().equals(editor.getText().substring(edit.start(), edit.end()))) {
			return;
		}
		if (pickerGroup != null) {
			editor.replaceRange(edit.text(), edit.start(), edit.end());
		} else {
			CompoundEdit group = new CompoundEdit();
			pickerGroup = group;
			editor.replaceRange(edit.text(), edit.start(), edit.end());
			pickerGroup = null;
			group.end();
			undo.addEdit(group);
		}
		showInText(edit.start());
	}

	private void showInText(Integer offset) {
		if (offset != null && offset <= editor.getDocument().getLength()) {
			editor.setCaretPosition(offset);
		}
	}

	private void showPieces(PortraitSim.Preview preview) {
		lastPieces = preview.pieces();
		boolean locked = !editor.isEditable();
		piecesLocked = locked;
		piecesPanel.removeAll();
		for (int i = 0; i < preview.pieces().size(); i++) {
			JComponent line = pieceRow(preview.pieces().get(i), locked);
			// The name and its buttons are far apart, so every other row is striped to carry the eye across.
			if (i % 2 == 0) {
				Theme.style(line, "zebra");
			}
			piecesPanel.add(line);
		}
		piecesPanel.revalidate();
		piecesPanel.repaint();
	}

	private JComponent pieceRow(PortraitSim.PieceRow row, boolean locked) {
		JPanel line = new JPanel(new GridBagLayout());
		line.setAlignmentX(LEFT_ALIGNMENT);
		line.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 2));
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(1, 0, 1, 4);
		c.anchor = GridBagConstraints.WEST;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		JLabel name = new JLabel(row.label());
		if (!row.shown()) {
			Theme.tone(name, Theme.Tone.GHOST);
		}
		line.add(name, c);
		c.weightx = 0;
		c.fill = GridBagConstraints.NONE;
		line.add(stepButton("‹", row.count() > 1, () -> stepPiece(row, -1)), c);
		line.add(stepButton("›", row.count() > 1, () -> stepPiece(row, 1)), c);
		JLabel count = new JLabel((row.current() + 1) + " / " + row.count() + (row.shown() ? "" : " hidden"));
		Theme.style(count, "small");
		fixWidth(count, PIECE_COUNT_WIDTH);
		line.add(count, c);
		line.add(Box.createHorizontalStrut(8), c);
		JLabel rowLabel = new JLabel("Row " + row.row());
		Theme.style(rowLabel, "small");
		fixWidth(rowLabel, PIECE_ROW_WIDTH);
		line.add(rowLabel, c);
		line.add(stepButton("−", !locked && row.rows() > 0 && row.row() > 0, () -> changeRow(row, -1)), c);
		line.add(stepButton("+", !locked && row.rows() > 0 && row.row() < row.rows() - 1, () -> changeRow(row, 1)), c);
		return line;
	}

	/** Keeps the columns of the piece list in line, whatever each row's numbers are. */
	private static void fixWidth(JLabel label, int width) {
		label.setPreferredSize(new Dimension(Math.max(width, label.getPreferredSize().width), label.getPreferredSize().height));
		label.setMinimumSize(label.getPreferredSize());
	}

	private JButton stepButton(String text, boolean enabled, Runnable action) {
		JButton button = new JButton(text);
		button.setMargin(new Insets(0, 4, 0, 4));
		button.setEnabled(enabled);
		button.addActionListener(e -> action.run());
		return button;
	}

	private void stepPiece(PortraitSim.PieceRow row, int delta) {
		int next = Math.floorMod(row.current() + delta, row.count());
		framePins.put(row.part(), next);
		startRender();
	}

	private void changeRow(PortraitSim.PieceRow row, int delta) {
		framePins.put(row.part(), row.current());
		applyEdit(PortraitSim.frameRowEdit(editor.getText(), Math.max(0, typeBox.getSelectedIndex()), row.part(), row.item(), row.row() + delta));
	}

	private JComponent buildControls() {
		JPanel grid = new JPanel(new GridBagLayout());
		grid.setAlignmentX(LEFT_ALIGNMENT);
		int y = 0;
		y = addRow(grid, y, "Type", typeBox);
		y = addRow(grid, y, "Overlay", overlayBox);
		scaleBox.setSelectedItem(4);
		y = addRow(grid, y, "Scale", scaleBox);
		y = addRow(grid, y, "", reroll);
		y = addRow(grid, y, "Age", age);
		y = addRow(grid, y, "Grime", grime);
		y = addRow(grid, y, "Injury", injury);
		y = addRow(grid, y, "Plate", plate);
		y = addRow(grid, y, "Leather", leather);
		JPanel checks = new JPanel(new GridLayout(0, 3, 6, 2));
		for (JCheckBox box : List.of(dead, noble, slave, parent, retired, sick)) {
			checks.add(box);
		}
		addRow(grid, y, "", checks);
		return grid;
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
		typeBox.addActionListener(e -> {
			if (!loading) {
				// A part's index means something else on another type.
				framePins.clear();
				startRender();
			}
		});
		for (JComboBox<?> box : List.of(overlayBox, scaleBox)) {
			box.addActionListener(e -> {
				if (!loading) {
					startRender();
				}
			});
		}
		releaseColours.addActionListener(e -> {
			colourPins.clear();
			startRender();
		});
		for (JSlider slider : List.of(age, grime, injury, plate, leather)) {
			slider.addChangeListener(e -> startRender());
		}
		for (JCheckBox box : List.of(dead, noble, slave, parent, retired, sick)) {
			box.addActionListener(e -> startRender());
		}
		reroll.addActionListener(e -> {
			seed = System.nanoTime() & 0xFFFFFFFFL;
			seedField.setText(String.valueOf(seed));
			java.util.Arrays.fill(randomBytes, -1);
			startRender();
		});
		saveButton.addActionListener(e -> save());
		copyButton.addActionListener(e -> copy());
		editor.getDocument().addUndoableEditListener(e -> {
			if (loading) {
				return;
			}
			if (pickerGroup != null) {
				pickerGroup.addEdit(e.getEdit());
			} else {
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
		textChanges++;
		status.setText(" ");
		updateSave();
		// Not restarted on each key, so the portrait keeps up while someone is still typing.
		if (!debounce.isRunning()) {
			debounce.start();
		}
	}

	/** Reads the mod's races afresh. */
	/** The race to show once the mod's races are listed, such as the one a wizard just designed. */
	private String wantedRace;

	/** Opens a mod showing one of its races. */
	public void open(ModEntry value, String race) {
		wantedRace = race;
		open(value);
	}

	public void open(ModEntry value) {
		this.mod = value;
		status.setText(" ");
		Path game = gameFolder.get();
		work(() -> {
			try {
				closeSession();
				if (!GameInstall.isValid(game)) {
					throw new IOException("The game folder isn't set.");
				}
				session = PortraitSim.open(game, value);
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
			portrait.clear();
			portrait.setProblem(false);
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
		PortraitSim.Session current = session;
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
		framePins.clear();
		colourPins.clear();
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
		editorNote.setText(note.toString());
		editorNote.setVisible(note.length() > 0);
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

	private PortraitSim.Person person() {
		Object overlay = overlayBox.getSelectedItem();
		Object scale = scaleBox.getSelectedItem();
		return new PortraitSim.Person(Math.max(0, typeBox.getSelectedIndex()), seed, age.getValue() / 100.0, dead.isSelected(), noble.isSelected(), slave.isSelected(), parent.isSelected(), retired.isSelected(), sick.isSelected(),
				plate.getValue(), leather.getValue(), grime.getValue() / 100.0, injury.getValue() / 100.0, overlay == null ? PortraitSim.OverlayChoice.NONE : (PortraitSim.OverlayChoice) overlay, scale == null ? 4 : (Integer) scale,
				new PortraitSim.Pins(Map.copyOf(framePins), Map.copyOf(colourPins), randomBytes.clone()));
	}

	/** One drawing at a time. Whatever changed meanwhile is drawn straight after, so the latest text always wins. */
	private void startRender() {
		PortraitSim.Session current = session;
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
		PortraitSim.Person person = person();
		work(() -> {
			PortraitSim.Preview preview = null;
			String failure = null;
			try {
				preview = current.preview(race, text, person);
			} catch (PortraitSim.Refused | IOException | RuntimeException e) {
				failure = e.getMessage() == null ? e.toString() : e.getMessage();
			}
			PortraitSim.Preview done = preview;
			String failed = failure;
			SwingUtilities.invokeLater(() -> finishRender(done, failed));
		});
	}

	private void finishRender(PortraitSim.Preview preview, String failure) {
		rendering = false;
		if (failure != null) {
			showProblem(failure, null);
		} else if (preview.state() == PortraitSim.State.RED) {
			showProblem(preview.problem(), preview.line());
		} else {
			red = false;
			portrait.setProblem(false);
			reason.setText(" ");
			portrait.show(preview.width(), preview.height(), preview.argb());
			showTypes(preview.types());
			warnings.setText(String.join("\n", preview.warnings()));
			notes.setText(String.join("\n", preview.notes()));
			// Rebuilt only when something shown changed, so a control isn't replaced while it's in use.
			boolean locked = !editor.isEditable();
			if (!preview.colours().equals(lastColours) || !java.util.Objects.equals(preview.blood(), lastBlood) || locked != coloursLocked) {
				showColours(preview);
			}
			if (!preview.pieces().equals(lastPieces) || locked != piecesLocked) {
				showPieces(preview);
			}
			syncRandom(preview.random());
		}
		updateSave();
		if (renderAgain) {
			renderAgain = false;
			startRender();
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

	/** The last good portrait stays up, and only the outline and this one line change. */
	private void showProblem(String problem, Integer line) {
		red = true;
		portrait.setProblem(true);
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
		PortraitSim.Session current = session;
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
			List<PortraitSection.AbsentBlock> blocks = PortraitSim.absent(editor.getText());
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
		PortraitSim.Session current = session;
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
		PortraitSim.Session current = session;
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
		return !dirty() || discardPrompt.test("Discard your changes to this race's portrait? Its file hasn't been changed.");
	}

	/** Side by side when there's room, stacked when there isn't. */
	public void setBand(WidthBand band) {
		int orientation = band == WidthBand.NARROW ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT;
		if (split.getOrientation() != orientation) {
			split.setOrientation(orientation);
			// The old position is measured along the other axis.
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

	public boolean hasPortrait() {
		return portrait.hasImage();
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

	public int defaultsListed() {
		return Math.max(0, defaultsList.getComponentCount() - 1);
	}

	public void placeCaret(int offset) {
		editor.setCaretPosition(offset);
		showDefaults();
	}

	public int textChanges() {
		return textChanges;
	}

	public List<PortraitSim.ColourRow> colourRows() {
		return lastColours;
	}

	public List<PortraitSim.PieceRow> pieceRows() {
		return lastPieces;
	}

	public void selectTab(String title) {
		tabs.setSelectedIndex(tabs.indexOfTab(title));
	}

	public void clickSwatch(String colour, int slot) {
		lastColours.stream().filter(row -> row.name().equals(colour)).findFirst().ifPresent(row -> chooseSlot(row, slot));
	}

	/** What moving the picker does, without the dialog. */
	public void pickColourForTest(String colour, int entry, int r, int g, int b) {
		setColour(colour, entry, r, g, b);
	}

	/** Opens the real picker for a colour's active slot, as its Change button does. */
	public void openPickerForTest(String colour) {
		lastColours.stream().filter(row -> row.name().equals(colour)).findFirst().ifPresent(row -> {
			PortraitSim.ColourSlot active = row.slots().get(row.active());
			pickColour(active, (r, g, b) -> setColour(row.name(), active.entry(), r, g, b));
		});
	}

	public void pickBloodForTest(int r, int g, int b) {
		setBlood(r, g, b);
	}

	public void undoInEditor() {
		if (undo.canUndo()) {
			undo.undo();
		}
	}

	public void stepPieceForTest(int part, int delta) {
		lastPieces.stream().filter(row -> row.part() == part).findFirst().ifPresent(row -> stepPiece(row, delta));
	}

	public void changeRowForTest(int part, int delta) {
		lastPieces.stream().filter(row -> row.part() == part).findFirst().ifPresent(row -> changeRow(row, delta));
	}

	public void setDiscardPrompt(Predicate<String> prompt) {
		discardPrompt = prompt;
	}

	public void setCopyPrompt(Predicate<String> prompt) {
		copyPrompt = prompt;
	}
}
