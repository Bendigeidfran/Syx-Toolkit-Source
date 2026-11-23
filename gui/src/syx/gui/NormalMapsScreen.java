package syx.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

import com.formdev.flatlaf.FlatClientProperties;

import syx.core.ModEntry;
import syx.core.NormalGen;
import syx.core.NormalTuningStore;

/** Lists the current mod's PNGs, shows one at full size, and generates or removes its normals. The work is {@link NormalGen}. */
public final class NormalMapsScreen extends JPanel {

	private static final long serialVersionUID = 1L;

	/** A file in the tree. A folder is just its name. */
	private record SheetRow(String name, String path, boolean supported) {
		@Override
		public String toString() {
			return name;
		}
	}

	private final JLabel heading = new JLabel("Normal Maps");
	private final WrappedText help = new WrappedText("A sheet's normal map is the right half of its PNG. Select a sheet to see it at full size. So far this works on races and their skeletons, sleeping, infant and weapon sheets, animals, resources, debris, crops, deposits, buildings and room sheets, and other PNGs are greyed out. Deposits and some room sheets start flat, because vanilla's are. Change a section's shading to see a preview, and Generate Normals to write it.", Theme.Tone.HELP);
	private final WrappedText shadingHelp = new WrappedText("Strength is how far normals lean, and 0 is flat. Sharpness above 100 keeps the edge and flattens the middle, and below 100 carries the lean further in. On a race sheet, armour is drawn from an addon, and each race's own file says which slot. A building's roof shape comes from the game's own sheets, so it only has Strength.", Theme.Tone.HELP);
	private final JLabel shadingHint = new JLabel("Select a supported sheet to change its shading.");
	private final JButton resetButton = new JButton("Reset");
	private final JPanel sectionRows = new JPanel(new GridBagLayout());
	private final JScrollPane shadingScroller;
	private final transient Map<String, JSpinner[]> spinners = new LinkedHashMap<>();
	private final Timer previewTimer = new Timer(200, e -> preview());
	private final transient NormalTuningStore store;
	private final transient Supplier<Path> gameFolder;
	private transient List<NormalGen.Section> shownSections = List.of();
	private int previewId;
	private boolean previewPending;
	private boolean settingControls;
	private final WrappedText status = new WrappedText(" ", null);
	private final JLabel emptyHint = new JLabel("This mod has no PNG files.");
	private final JButton generateButton = new JButton("Generate Normals");
	private final JButton trimButton = new JButton("Trim Normals");
	private final JButton clearButton = new JButton("Clear All Normals");
	private final JTree tree = new JTree(new DefaultMutableTreeNode());
	private final JScrollPane treeScroller;
	private final JScrollPane imageScroller;
	private final JLabel imageHint = new JLabel("Select a PNG to see it.");
	private final SheetView sheetView = new SheetView();
	private final RowRenderer renderer = new RowRenderer();
	private transient Predicate<String> confirm = this::askUser;
	private transient ModEntry mod;
	private transient NormalGen.Opened opened;
	private int loadId;
	private boolean busy;
	private boolean suppressSelection;

	public NormalMapsScreen(NormalTuningStore store, Supplier<Path> gameFolder) {
		super(new BorderLayout(0, 16));
		this.store = store;
		this.gameFolder = gameFolder;
		previewTimer.setRepeats(false);
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		Theme.style(heading, "h2");
		Theme.tone(emptyHint, Theme.Tone.HELP);
		Theme.tone(imageHint, Theme.Tone.HELP);
		Theme.style(generateButton, "primary");
		generateButton.setToolTipText("Write normals into the selected sheet's frames");
		trimButton.setToolTipText("Remove normals beside transparent art, which the game never draws");
		clearButton.setToolTipText("Remove every normal from the selected sheet's frames");
		generateButton.addActionListener(e -> generate());
		trimButton.addActionListener(e -> trim());
		clearButton.addActionListener(e -> clearAll());

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		buttons.add(generateButton);
		buttons.add(Box.createHorizontalStrut(8));
		buttons.add(trimButton);
		buttons.add(Box.createHorizontalStrut(8));
		buttons.add(clearButton);
		heading.setAlignmentX(LEFT_ALIGNMENT);
		help.setAlignmentX(LEFT_ALIGNMENT);
		status.setAlignmentX(LEFT_ALIGNMENT);
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		top.add(heading);
		top.add(Box.createVerticalStrut(4));
		top.add(help);
		top.add(Box.createVerticalStrut(8));
		top.add(status);
		top.add(Box.createVerticalStrut(8));
		top.add(buttons);

		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		tree.setCellRenderer(renderer);
		tree.addTreeSelectionListener(e -> selected());
		treeScroller = new JScrollPane(tree);
		treeScroller.setBorder(BorderFactory.createEmptyBorder());
		treeScroller.getVerticalScrollBar().setUnitIncrement(16);

		JLabel filesTitle = new JLabel("PNG files");
		Theme.style(filesTitle, "h3");
		JPanel treeBody = new JPanel(new BorderLayout());
		treeBody.setOpaque(false);
		treeBody.add(emptyHint, BorderLayout.NORTH);
		treeBody.add(treeScroller, BorderLayout.CENTER);
		JPanel treeCard = card(filesTitle, treeBody);
		treeCard.setMinimumSize(new Dimension(180, 80));

		JLabel shadingTitle = new JLabel("Shading");
		Theme.style(shadingTitle, "h3");
		resetButton.setToolTipText("Put every section back to its default");
		resetButton.addActionListener(e -> reset());
		JPanel shadingHeader = new JPanel(new BorderLayout());
		shadingHeader.setOpaque(false);
		shadingHeader.add(shadingTitle, BorderLayout.WEST);
		JPanel resetHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		resetHolder.setOpaque(false);
		resetHolder.add(resetButton);
		shadingHeader.add(resetHolder, BorderLayout.EAST);
		sectionRows.setOpaque(false);
		JPanel rowsHolder = new JPanel(new BorderLayout());
		rowsHolder.setOpaque(false);
		rowsHolder.add(sectionRows, BorderLayout.NORTH);
		shadingScroller = new JScrollPane(rowsHolder);
		shadingScroller.setBorder(BorderFactory.createEmptyBorder());
		shadingScroller.getVerticalScrollBar().setUnitIncrement(16);
		Theme.tone(shadingHint, Theme.Tone.HELP);
		JPanel shadingBody = new JPanel(new BorderLayout(0, 8));
		shadingBody.setOpaque(false);
		shadingBody.add(shadingHelp, BorderLayout.NORTH);
		JPanel shadingList = new JPanel(new BorderLayout());
		shadingList.setOpaque(false);
		shadingList.add(shadingHint, BorderLayout.NORTH);
		shadingList.add(shadingScroller, BorderLayout.CENTER);
		shadingBody.add(shadingList, BorderLayout.CENTER);
		JPanel shadingCard = card(shadingHeader, shadingBody);
		shadingCard.setMinimumSize(new Dimension(180, 80));

		JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, treeCard, shadingCard);
		leftSplit.setBorder(BorderFactory.createEmptyBorder());
		leftSplit.setResizeWeight(0.35);
		leftSplit.setMinimumSize(new Dimension(220, 100));
		leftSplit.setPreferredSize(new Dimension(350, 100));

		imageScroller = new JScrollPane(imageHint);
		imageScroller.setBorder(BorderFactory.createEmptyBorder());
		imageScroller.getVerticalScrollBar().setUnitIncrement(16);
		imageScroller.getHorizontalScrollBar().setUnitIncrement(16);
		JLabel imageTitle = new JLabel("Sheet");
		Theme.style(imageTitle, "h3");
		JPanel imageCard = card(imageTitle, imageScroller);
		imageCard.setMinimumSize(new Dimension(200, 100));

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, imageCard);
		split.setBorder(BorderFactory.createEmptyBorder());
		split.setResizeWeight(0);

		add(top, BorderLayout.NORTH);
		add(split, BorderLayout.CENTER);
		showSections(List.of(), Map.of());
		updateButtons();
	}

	/** Builds a row for each section, filled in with these values. */
	private void showSections(List<NormalGen.Section> sections, Map<String, NormalGen.Tuning> values) {
		settingControls = true;
		shownSections = sections;
		sectionRows.removeAll();
		spinners.clear();
		GridBagConstraints c = new GridBagConstraints();
		c.gridy = 0;
		c.insets = new Insets(0, 0, 4, 8);
		c.anchor = GridBagConstraints.WEST;
		if (!sections.isEmpty()) {
			c.gridx = 1;
			sectionRows.add(columnTitle("Strength"), c);
			c.gridx = 2;
			sectionRows.add(columnTitle("Sharpness"), c);
		}
		for (NormalGen.Section section : sections) {
			NormalGen.Tuning value = values.getOrDefault(section.id(), section.defaults());
			c.gridy++;
			c.gridx = 0;
			c.weightx = 1;
			c.fill = GridBagConstraints.HORIZONTAL;
			sectionRows.add(new JLabel(section.label()), c);
			c.weightx = 0;
			c.fill = GridBagConstraints.NONE;
			JSpinner strength = spinner(value.strength(), NormalGen.MIN_STRENGTH, NormalGen.MAX_STRENGTH);
			JSpinner sharpness = section.hasSharpness() ? spinner(value.sharpness(), NormalGen.MIN_SHARPNESS, NormalGen.MAX_SHARPNESS) : null;
			c.gridx = 1;
			sectionRows.add(strength, c);
			if (sharpness != null) {
				c.gridx = 2;
				sectionRows.add(sharpness, c);
			}
			spinners.put(section.id(), new JSpinner[] { strength, sharpness });
		}
		shadingHint.setVisible(sections.isEmpty());
		shadingScroller.setVisible(!sections.isEmpty());
		resetButton.setEnabled(!sections.isEmpty());
		settingControls = false;
		sectionRows.revalidate();
		sectionRows.repaint();
	}

	private static JLabel columnTitle(String text) {
		JLabel label = new JLabel(text);
		Theme.tone(label, Theme.Tone.HELP);
		return label;
	}

	private JSpinner spinner(int value, int minimum, int maximum) {
		JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, minimum, maximum, 5));
		spinner.setPreferredSize(new Dimension(72, spinner.getPreferredSize().height));
		spinner.addChangeListener(e -> shadingChanged());
		return spinner;
	}

	private void shadingChanged() {
		if (!settingControls && opened != null && opened.listed().supported()) {
			previewPending = true;
			previewTimer.restart();
		}
	}

	private Map<String, NormalGen.Tuning> currentShading() {
		Map<String, NormalGen.Tuning> values = new LinkedHashMap<>();
		spinners.forEach((id, pair) -> values.put(id, new NormalGen.Tuning((Integer) pair[0].getValue(), pair[1] == null ? NormalGen.Tuning.DEFAULT.sharpness() : (Integer) pair[1].getValue())));
		return values;
	}

	private void reset() {
		settingControls = true;
		for (NormalGen.Section section : shownSections) {
			JSpinner[] pair = spinners.get(section.id());
			pair[0].setValue(section.defaults().strength());
			if (pair[1] != null) {
				pair[1].setValue(section.defaults().sharpness());
			}
		}
		settingControls = false;
		shadingChanged();
	}

	/** Draws what Generate Normals would write, without writing it. */
	private void preview() {
		if (opened == null || mod == null || !opened.listed().supported()) {
			previewPending = false;
			return;
		}
		int id = ++previewId;
		ModEntry current = mod;
		String path = opened.listed().path();
		Map<String, NormalGen.Tuning> shading = currentShading();
		new SwingWorker<NormalGen.Preview, Void>() {
			@Override
			protected NormalGen.Preview doInBackground() throws Exception {
				return NormalGen.preview(gameFolder.get(), current.root(), path, shading);
			}

			@Override
			protected void done() {
				if (id != previewId) {
					return;
				}
				previewPending = false;
				try {
					NormalGen.Preview preview = get();
					if (opened != null && opened.listed().path().equals(path)) {
						sheetView.show(preview.width(), preview.height(), preview.argb());
						setStatus("Preview. Nothing is written until you click Generate Normals.", Theme.Tone.HELP);
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					setStatus(messageOf(e), Theme.Tone.ERROR);
				}
			}
		}.execute();
	}

	private void cancelPreview() {
		previewTimer.stop();
		previewId++;
		previewPending = false;
	}

	private static JPanel card(JComponent title, JComponent body) {
		JPanel card = new JPanel(new BorderLayout(0, 10));
		Theme.style(card, "card");
		card.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
		card.add(title, BorderLayout.NORTH);
		card.add(body, BorderLayout.CENTER);
		return card;
	}

	/** Reads this mod's PNG files. */
	public void open(ModEntry mod) {
		this.mod = mod;
		if (busy) {
			return;
		}
		setStatus(" ", null);
		show(null);
		busy = true;
		updateButtons();
		new SwingWorker<List<NormalGen.Listed>, Void>() {
			@Override
			protected List<NormalGen.Listed> doInBackground() throws Exception {
				return NormalGen.list(mod.root());
			}

			@Override
			protected void done() {
				busy = false;
				try {
					showFiles(get());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					setStatus("The PNG files couldn't be read.", Theme.Tone.ERROR);
				} catch (ExecutionException e) {
					setStatus(messageOf(e), Theme.Tone.ERROR);
				}
				updateButtons();
			}
		}.execute();
	}

	public boolean isBusy() {
		return busy || previewPending;
	}

	/** One pixel of the image being shown, as 0xAARRGGBB, which is a preview if the shading has been changed. */
	public int shownPixel(int x, int y) {
		return sheetView.pixel(x, y);
	}

	public List<String> sectionLabels() {
		return shownSections.stream().map(NormalGen.Section::label).toList();
	}

	public int sectionStrength(String id) {
		return (Integer) spinners.get(id)[0].getValue();
	}

	/** Changes a section's values, as typing in its boxes would. */
	public void setShading(String id, int strength, int sharpness) {
		spinners.get(id)[0].setValue(strength);
		spinners.get(id)[1].setValue(sharpness);
	}

	public void clickReset() {
		resetButton.doClick();
	}

	public String statusText() {
		return status.getText();
	}

	public boolean isEmpty() {
		return emptyHint.isVisible();
	}

	public boolean canGenerate() {
		return generateButton.isEnabled();
	}

	public boolean canTrim() {
		return trimButton.isEnabled() && clearButton.isEnabled();
	}

	/** The size of the image being shown, or null if there isn't one. */
	public Dimension shownSize() {
		return opened == null ? null : new Dimension(opened.width(), opened.height());
	}

	public void clickGenerate() {
		generateButton.doClick();
	}

	public void clickTrim() {
		trimButton.doClick();
	}

	public void clickClearAll() {
		clearButton.doClick();
	}

	/** Answers the screen's questions, for tests, and collects what was asked. */
	public void answerQuestions(boolean answer, List<String> asked) {
		confirm = question -> {
			asked.add(question);
			return answer;
		};
	}

	public List<String> treeRows() {
		List<String> rows = new ArrayList<>();
		for (int row = 0; row < tree.getRowCount(); row++) {
			rows.add(String.valueOf(tree.getPathForRow(row).getLastPathComponent()));
		}
		return rows;
	}

	/** Selects a file by its name, as clicking its row would. */
	public void select(String name) {
		for (int row = 0; row < tree.getRowCount(); row++) {
			Object last = tree.getPathForRow(row).getLastPathComponent();
			if (last instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof SheetRow sheet && sheet.name().equals(name)) {
				tree.setSelectionPath(tree.getPathForRow(row));
				return;
			}
		}
		throw new IllegalArgumentException("No row for " + name);
	}

	private void selected() {
		if (suppressSelection) {
			return;
		}
		SheetRow row = selectedRow();
		cancelPreview();
		show(null);
		showSections(List.of(), Map.of());
		if (row == null) {
			setStatus(" ", null);
			updateButtons();
			return;
		}
		int id = ++loadId;
		busy = true;
		updateButtons();
		setStatus("Reading " + row.name() + "...", Theme.Tone.HELP);
		ModEntry current = mod;
		new SwingWorker<Selected, Void>() {
			@Override
			protected Selected doInBackground() throws Exception {
				NormalGen.Opened sheet = NormalGen.open(current.root(), row.path());
				return new Selected(sheet, NormalGen.sections(sheet.listed().path()), store.load(current.folderName(), sheet.listed().path()));
			}

			@Override
			protected void done() {
				if (id != loadId) {
					return;
				}
				busy = false;
				try {
					Selected result = get();
					show(result.sheet());
					if (result.sheet().listed().supported()) {
						showSections(result.sections(), result.values());
					}
					setStatus(result.sheet().status(), Theme.Tone.HELP);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					setStatus("That PNG couldn't be read.", Theme.Tone.ERROR);
				} catch (ExecutionException e) {
					setStatus(messageOf(e), Theme.Tone.ERROR);
				}
				updateButtons();
			}
		}.execute();
	}

	private void generate() {
		if (opened == null) {
			return;
		}
		boolean replace = opened.listed().hasNormals();
		if (replace && !confirm.test("This sheet already has normals. Replace them? They can't be got back.")) {
			return;
		}
		String path = opened.listed().path();
		String revision = opened.revision();
		ModEntry current = mod;
		Map<String, NormalGen.Tuning> shading = currentShading();
		run("Generating normals...", () -> {
			NormalGen.Changed changed = NormalGen.generate(gameFolder.get(), current.root(), path, shading, replace, revision);
			store.save(current.folderName(), path, shading);
			return changed;
		});
	}

	private void trim() {
		if (opened == null) {
			return;
		}
		String path = opened.listed().path();
		String revision = opened.revision();
		ModEntry current = mod;
		run("Trimming normals...", () -> NormalGen.trim(current.root(), path, NormalGen.TrimMode.OUTSIDE_ART, false, revision));
	}

	private void clearAll() {
		if (opened == null || !confirm.test("Clear every normal in this sheet? They can't be got back, and the sheet will need new normals to light properly in the game.")) {
			return;
		}
		String path = opened.listed().path();
		String revision = opened.revision();
		ModEntry current = mod;
		run("Clearing normals...", () -> NormalGen.trim(current.root(), path, NormalGen.TrimMode.ALL, true, revision));
	}

	private void run(String progress, Callable<NormalGen.Changed> work) {
		if (busy || opened == null) {
			return;
		}
		cancelPreview();
		busy = true;
		updateButtons();
		setStatus(progress, Theme.Tone.HELP);
		String path = opened.listed().path();
		ModEntry current = mod;
		new SwingWorker<Outcome, Void>() {
			@Override
			protected Outcome doInBackground() throws Exception {
				NormalGen.Changed changed = work.call();
				return new Outcome(changed, NormalGen.open(current.root(), path));
			}

			@Override
			protected void done() {
				busy = false;
				try {
					Outcome outcome = get();
					show(outcome.opened());
					setStatus(outcome.changed().message(), outcome.changed().changed() ? Theme.Tone.SUCCESS : Theme.Tone.HELP);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					setStatus("That was interrupted.", Theme.Tone.ERROR);
				} catch (ExecutionException e) {
					setStatus(messageOf(e), Theme.Tone.ERROR);
				}
				updateButtons();
			}
		}.execute();
	}

	private boolean askUser(String question) {
		return JOptionPane.showConfirmDialog(this, question, "Normal Maps", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
	}

	private static String messageOf(ExecutionException e) {
		Throwable cause = e.getCause() == null ? e : e.getCause();
		return cause.getMessage();
	}

	private void show(NormalGen.Opened sheet) {
		opened = sheet;
		if (sheet == null) {
			imageScroller.setViewportView(imageHint);
		} else {
			sheetView.show(sheet.width(), sheet.height(), sheet.argb());
			imageScroller.setViewportView(sheetView);
		}
		imageScroller.revalidate();
		imageScroller.repaint();
	}

	private void showFiles(List<NormalGen.Listed> files) {
		DefaultMutableTreeNode root = new DefaultMutableTreeNode();
		Map<String, DefaultMutableTreeNode> folders = new HashMap<>();
		folders.put("", root);
		for (NormalGen.Listed file : files) {
			Path path = Path.of(file.path());
			DefaultMutableTreeNode parent = root;
			if (path.getParent() != null) {
				Path walked = Path.of("");
				for (Path part : path.getParent()) {
					walked = walked.resolve(part);
					String key = walked.toString().replace('\\', '/');
					DefaultMutableTreeNode folder = folders.get(key);
					if (folder == null) {
						folder = new DefaultMutableTreeNode(part.toString());
						folders.put(key, folder);
						parent.add(folder);
					}
					parent = folder;
				}
			}
			parent.add(new DefaultMutableTreeNode(new SheetRow(path.getFileName().toString(), file.path(), file.supported()), false));
		}
		suppressSelection = true;
		tree.setModel(new DefaultTreeModel(root, true));
		for (int row = 0; row < tree.getRowCount(); row++) {
			tree.expandRow(row);
		}
		suppressSelection = false;
		emptyHint.setVisible(files.isEmpty());
		treeScroller.setVisible(!files.isEmpty());
		revalidate();
	}

	private void updateButtons() {
		boolean ready = !busy && opened != null && opened.listed().supported();
		generateButton.setEnabled(ready);
		trimButton.setEnabled(ready);
		clearButton.setEnabled(ready);
	}

	private SheetRow selectedRow() {
		Object last = tree.getLastSelectedPathComponent();
		if (last instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof SheetRow row) {
			return row;
		}
		return null;
	}

	private void setStatus(String text, Theme.Tone tone) {
		status.setText(text == null || text.isBlank() ? " " : text);
		if (tone == null) {
			clearTone(status);
		} else {
			Theme.tone(status, tone);
		}
	}

	private static void clearTone(JComponent component) {
		Object style = component.getClientProperty(FlatClientProperties.STYLE_CLASS);
		if (style != null) {
			String kept = java.util.Arrays.stream(style.toString().split(" ")).filter(c -> !c.startsWith("tone")).reduce((a, b) -> a + " " + b).orElse("");
			component.putClientProperty(FlatClientProperties.STYLE_CLASS, kept);
		}
	}

	private record Outcome(NormalGen.Changed changed, NormalGen.Opened opened) {
	}

	private record Selected(NormalGen.Opened sheet, List<NormalGen.Section> sections, Map<String, NormalGen.Tuning> values) {
	}

	/** The image pixel for pixel. Transparent pixels show the card behind them. */
	private static final class SheetView extends JComponent {
		private static final long serialVersionUID = 1L;
		private transient BufferedImage image;

		void show(int width, int height, int[] argb) {
			image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			image.setRGB(0, 0, width, height, argb, 0, width);
			setPreferredSize(new Dimension(width, height));
			revalidate();
			repaint();
		}

		int pixel(int x, int y) {
			return image.getRGB(x, y);
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			if (image != null) {
				Graphics2D g = (Graphics2D) graphics.create();
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
				g.drawImage(image, 0, 0, null);
				g.dispose();
			}
		}
	}

	/** Greys out a PNG that can't be generated for, and leaves a selected row in the selection colour. */
	private static final class RowRenderer extends DefaultTreeCellRenderer {
		private static final long serialVersionUID = 1L;

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean focused) {
			super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focused);
			putClientProperty("html.disable", Boolean.TRUE);
			// The disabled look follows the theme, and a row can still be clicked to see the image.
			if (!selected && value instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof SheetRow sheet && !sheet.supported()) {
				setEnabled(false);
			}
			return this;
		}
	}
}
