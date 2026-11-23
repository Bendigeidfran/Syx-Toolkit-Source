package syx.gui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.ToolTipManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

import syx.core.Diagnostic;
import syx.core.DiffReportFile;
import syx.core.GameManifest;
import syx.core.GameParser;
import syx.core.ManifestStore;
import syx.core.ModCodeDiff;
import syx.core.ModEntry;

/** Shows what a game update means for the current mod. All the logic is in {@link ModCodeDiff} and {@link ManifestStore}. */
public final class GameVersionDiffScreen extends JPanel {

	private static final long serialVersionUID = 1L;

	/** A manifest in a dropdown. */
	private record Choice(ManifestStore.Entry entry) {
		@Override
		public String toString() {
			return entry.label();
		}
	}

	/** What's known when the screen opens. */
	private record Loaded(ManifestStore.Listing listing, ManifestStore.Installed installed, ManifestStore.Defaults defaults) {
	}

	private final transient Supplier<Path> gameFolder;
	private final transient ManifestStore store;
	private final transient HomeScreen.FolderOpener folderOpener;
	private final WrappedText installedText = new WrappedText(" ", null);
	private final JButton generateButton = new JButton("Generate Manifest");
	private final JComboBox<Choice> oldVersion = new JComboBox<>();
	private final JComboBox<Choice> newVersion = new JComboBox<>();
	private final JButton runButton = new JButton("Run Diff");
	private final JButton cancelButton = new JButton("Cancel");
	private final JButton exportButton = new JButton("Export Report");
	private final JButton openReportsButton = new JButton("Open Reports Folder");
	private final WrappedText exportNote = new WrappedText(" ", Theme.Tone.HELP);
	private final WrappedText reason = new WrappedText(" ", Theme.Tone.HELP);
	private final JProgressBar progress = new JProgressBar();
	private final WrappedText summary = new WrappedText(" ", null);
	private final WrappedText bodyNote = new WrappedText(" ", Theme.Tone.HELP);
	private final WrappedText emptyState = new WrappedText(" ", Theme.Tone.HELP);
	private final WrappedText detail = new WrappedText(" ", null);
	private final CardLayout resultViews = new CardLayout();
	private final JPanel resultsPanel = new JPanel();
	private final DefaultTableModel results = new DefaultTableModel(new Object[] { "Group", "Your mod", "Game reference", "What changed" }, 0) {
		private static final long serialVersionUID = 1L;

		@Override
		public boolean isCellEditable(int row, int column) {
			return false;
		}
	};
	private final JTable findings;
	private final transient AtomicBoolean cancelRequested = new AtomicBoolean();
	private transient ModEntry mod;
	private transient ModCodeDiff.Report lastReport;
	/** The mod the last report is about, which the current one may stop being. */
	private transient ModEntry reportMod;
	private boolean busy;
	private boolean canGenerate;

	public GameVersionDiffScreen(Supplier<Path> gameFolder, ManifestStore store, HomeScreen.FolderOpener folderOpener) {
		super(new BorderLayout(0, 10));
		this.gameFolder = gameFolder;
		this.store = store;
		this.folderOpener = folderOpener;
		setBorder(BorderFactory.createEmptyBorder(22, 24, 14, 24));

		JLabel heading = new JLabel("Game Version Diff");
		Theme.style(heading, "h2");
		heading.setAlignmentX(LEFT_ALIGNMENT);
		Theme.style(runButton, "primary");
		generateButton.addActionListener(e -> generate());
		runButton.addActionListener(e -> runDiff());
		cancelButton.addActionListener(e -> cancel());
		cancelButton.setEnabled(false);
		exportButton.addActionListener(e -> export());
		openReportsButton.addActionListener(e -> openReports());
		updateExportState();
		oldVersion.addActionListener(e -> updateRunState());
		newVersion.addActionListener(e -> updateRunState());

		JPanel generateRow = new JPanel(new BorderLayout(12, 0));
		generateRow.add(installedText, BorderLayout.CENTER);
		generateRow.add(generateButton, BorderLayout.EAST);
		generateRow.setAlignmentX(LEFT_ALIGNMENT);

		JPanel versions = new JPanel(new GridBagLayout());
		addVersion(versions, 0, "Old Version", oldVersion);
		addVersion(versions, 2, "New Version", newVersion);
		versions.setAlignmentX(LEFT_ALIGNMENT);

		JPanel runRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		runRow.add(runButton);
		runRow.add(Box.createHorizontalStrut(8));
		runRow.add(cancelButton);
		runRow.add(Box.createHorizontalStrut(8));
		runRow.add(exportButton);
		runRow.add(Box.createHorizontalStrut(8));
		runRow.add(openReportsButton);
		runRow.setAlignmentX(LEFT_ALIGNMENT);
		reason.setAlignmentX(LEFT_ALIGNMENT);

		// This says what a result can't promise, so it stays on screen.
		WrappedText note = new WrappedText(ModCodeDiff.SCOPE_NOTE, Theme.Tone.HELP);
		note.setAlignmentX(LEFT_ALIGNMENT);

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.add(heading);
		top.add(Box.createVerticalStrut(10));
		top.add(generateRow);
		top.add(Box.createVerticalStrut(8));
		top.add(versions);
		top.add(Box.createVerticalStrut(8));
		top.add(runRow);
		top.add(Box.createVerticalStrut(4));
		top.add(reason);
		top.add(Box.createVerticalStrut(4));
		top.add(note);

		JTable table = new JTable(results) {
			private static final long serialVersionUID = 1L;

			@Override
			public String getToolTipText(MouseEvent event) {
				int row = rowAtPoint(event.getPoint());
				int column = columnAtPoint(event.getPoint());
				return row < 0 || column < 0 ? null : PlainTip.of(String.valueOf(getValueAt(row, column)));
			}
		};
		ToolTipManager.sharedInstance().registerComponent(table);
		table.setRowHeight(28);
		table.setShowHorizontalLines(true);
		table.getColumnModel().getColumn(0).setPreferredWidth(90);
		table.getColumnModel().getColumn(0).setMaxWidth(110);
		table.getColumnModel().getColumn(1).setPreferredWidth(240);
		table.getColumnModel().getColumn(2).setPreferredWidth(260);
		table.getColumnModel().getColumn(3).setPreferredWidth(420);
		table.getColumnModel().getColumn(0).setCellRenderer(new GroupRenderer());
		// These names come from a mod's jar, which must never be read as HTML.
		DefaultTableCellRenderer plain = new DefaultTableCellRenderer();
		plain.putClientProperty("html.disable", Boolean.TRUE);
		for (int column = 1; column < 4; column++) {
			table.getColumnModel().getColumn(column).setCellRenderer(plain);
		}
		// A row is cut off at the column's width, and nothing that matters may be available only on hover.
		table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
		table.getSelectionModel().addListSelectionListener(e -> showDetail(table.getSelectedRow()));
		findings = table;
		JScrollPane tableScroller = new JScrollPane(table);
		tableScroller.setBorder(BorderFactory.createEmptyBorder());
		JPanel scroller = new JPanel(new BorderLayout(0, 8));
		scroller.setOpaque(false);
		scroller.add(tableScroller, BorderLayout.CENTER);
		scroller.add(detail, BorderLayout.SOUTH);

		emptyState.setBorder(BorderFactory.createEmptyBorder(12, 8, 8, 8));
		resultsPanel.setLayout(resultViews);
		resultsPanel.setOpaque(false);
		JPanel emptyHolder = new JPanel(new BorderLayout());
		emptyHolder.setOpaque(false);
		emptyHolder.add(emptyState, BorderLayout.NORTH);
		resultsPanel.add(emptyHolder, "empty");
		resultsPanel.add(scroller, "table");

		JPanel card = new JPanel(new BorderLayout());
		Theme.style(card, "card");
		card.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		card.add(resultsPanel, BorderLayout.CENTER);

		JPanel bottom = new JPanel(new BorderLayout(0, 4));
		progress.setVisible(false);
		progress.setIndeterminate(true);
		bottom.add(progress, BorderLayout.NORTH);
		JPanel notes = new JPanel(new BorderLayout(0, 4));
		notes.add(summary, BorderLayout.NORTH);
		notes.add(bodyNote, BorderLayout.CENTER);
		notes.add(exportNote, BorderLayout.SOUTH);
		exportNote.setVisible(false);
		bottom.add(notes, BorderLayout.CENTER);

		add(top, BorderLayout.NORTH);
		add(card, BorderLayout.CENTER);
		add(bottom, BorderLayout.SOUTH);
		showEmpty("Choose two versions, then press Run Diff. Nothing is changed.");
		summary.setVisible(false);
		bodyNote.setVisible(false);
	}

	private static void addVersion(JPanel panel, int column, String name, JComboBox<Choice> box) {
		GridBagConstraints label = new GridBagConstraints();
		label.gridx = column;
		label.insets = new Insets(0, column == 0 ? 0 : 12, 0, 8);
		panel.add(new JLabel(name), label);
		GridBagConstraints field = new GridBagConstraints();
		field.gridx = column + 1;
		field.weightx = 1;
		field.fill = GridBagConstraints.HORIZONTAL;
		// Without this, a long entry would push the other dropdown off a narrow window.
		box.setPreferredSize(new Dimension(120, box.getPreferredSize().height));
		box.setMinimumSize(new Dimension(80, box.getPreferredSize().height));
		panel.add(box, field);
	}

	/** Shows a finding's group as a chip. */
	private static final class GroupRenderer implements TableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused, int row, int column) {
			String text = String.valueOf(value);
			JPanel cell = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
			cell.setBackground(selected ? table.getSelectionBackground() : table.getBackground());
			Theme.Tone tone = switch (text) {
			case "Breaks", "Tool problem" -> Theme.Tone.ERROR;
			case "Silent" -> Theme.Tone.WARNING;
			default -> Theme.Tone.HELP;
			};
			cell.add(new Chip(text, tone));
			return cell;
		}
	}

	/** Opens on this mod, reading what's on hand again in case it changed. */
	public void open(ModEntry current) {
		boolean sameMod = mod != null && current != null && mod.folderName().equals(current.folderName());
		mod = current;
		if (!sameMod) {
			clearResults();
			showEmpty("Choose two versions, then press Run Diff. Nothing is changed.");
		}
		refresh(null);
	}

	private void refresh(String after) {
		setBusy(true);
		Path game = gameFolder.get();
		new SwingWorker<Loaded, Void>() {
			@Override
			protected Loaded doInBackground() throws IOException {
				ManifestStore.Listing listing = store.list();
				ManifestStore.Installed installed = store.installed(game);
				return new Loaded(listing, installed, ManifestStore.defaults(listing.entries(), installed));
			}

			@Override
			protected void done() {
				setBusy(false);
				try {
					fill(get(), after);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					setInstalled("The manifests couldn't be read: " + e.getCause().getMessage(), false);
				}
			}
		}.execute();
	}

	private void fill(Loaded loaded, String after) {
		String keptOld = selectedId(oldVersion);
		String keptNew = selectedId(newVersion);
		oldVersion.removeAllItems();
		newVersion.removeAllItems();
		for (ManifestStore.Entry entry : loaded.listing().entries()) {
			oldVersion.addItem(new Choice(entry));
			newVersion.addItem(new Choice(entry));
		}
		select(oldVersion, keptOld != null ? keptOld : loaded.defaults().oldId());
		select(newVersion, keptNew != null ? keptNew : loaded.defaults().newId());
		ManifestStore.Installed installed = loaded.installed();
		String text;
		boolean can = false;
		if (installed.problem() != null) {
			text = "A manifest of your game can't be made: " + installed.problem();
		} else if (installed.existing() != null) {
			text = "Your game is " + installed.version() + ", which already has a manifest (" + installed.existing().source().label() + ").";
		} else {
			text = "Your game is " + installed.version() + ", which has no manifest yet. It holds names and hashes, not the game's code.";
			can = true;
		}
		if (!loaded.listing().problems().isEmpty()) {
			text += " " + String.join(" ", loaded.listing().problems());
		}
		setInstalled(after == null ? text : after + " " + text, can);
		updateRunState();
	}

	private void setInstalled(String text, boolean can) {
		installedText.setText(text);
		canGenerate = can;
		generateButton.setEnabled(can && !busy);
	}

	private static void select(JComboBox<Choice> box, String id) {
		box.setSelectedIndex(-1);
		for (int i = 0; i < box.getItemCount(); i++) {
			if (box.getItemAt(i).entry().id().equals(id)) {
				box.setSelectedIndex(i);
				return;
			}
		}
	}

	private static String selectedId(JComboBox<Choice> box) {
		Object item = box.getSelectedItem();
		return item == null ? null : ((Choice) item).entry().id();
	}

	/** Says why Run Diff can't be pressed, so it's never just greyed out. */
	private void updateRunState() {
		Object oldItem = oldVersion.getSelectedItem();
		Object newItem = newVersion.getSelectedItem();
		String problem = null;
		if (mod == null) {
			problem = "Choose a mod first.";
		} else if (oldItem == null || newItem == null) {
			problem = "Choose an old and a new version. If you need another, generate one or add a shared manifest.";
		} else {
			problem = ManifestStore.problemWith(((Choice) oldItem).entry(), ((Choice) newItem).entry());
		}
		runButton.setEnabled(problem == null && !busy);
		reason.setText(problem == null ? (mod == null ? " " : "Checks " + mod.displayName() + ".") : problem);
	}

	public void generate() {
		if (busy || !canGenerate) {
			return;
		}
		setBusy(true);
		cancelRequested.set(false);
		Path game = gameFolder.get();
		summary.setVisible(false);
		bodyNote.setVisible(false);
		showEmpty("Reading your game's classes...");
		new SwingWorker<ManifestStore.Generated, Void>() {
			@Override
			protected ManifestStore.Generated doInBackground() throws IOException, GameParser.UnavailableException {
				return store.generate(game, cancelRequested::get);
			}

			@Override
			protected void done() {
				setBusy(false);
				showEmpty("Choose two versions, then press Run Diff. Nothing is changed.");
				String said = null;
				try {
					ManifestStore.Generated generated = get();
					said = (generated.created() ? "Saved a manifest of " : "There's already a manifest of ") + generated.entry().version() + " at " + generated.entry().file() + ".";
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (CancellationException e) {
					said = "That was cancelled, and nothing was written.";
				} catch (ExecutionException e) {
					said = e.getCause() instanceof CancellationException ? "That was cancelled, and nothing was written." : "The manifest couldn't be made: " + e.getCause().getMessage();
				}
				refresh(said);
			}
		}.execute();
	}

	public void runDiff() {
		Object oldItem = oldVersion.getSelectedItem();
		Object newItem = newVersion.getSelectedItem();
		if (busy || mod == null || oldItem == null || newItem == null) {
			return;
		}
		ManifestStore.Entry oldEntry = ((Choice) oldItem).entry();
		ManifestStore.Entry newEntry = ((Choice) newItem).entry();
		ModEntry current = mod;
		reportMod = current;
		setBusy(true);
		cancelRequested.set(false);
		clearResults();
		summary.setVisible(false);
		bodyNote.setVisible(false);
		showEmpty("Comparing " + current.displayName() + " with " + oldEntry.version() + " and " + newEntry.version() + "...");
		new SwingWorker<ModCodeDiff.Report, Void>() {
			@Override
			protected ModCodeDiff.Report doInBackground() throws IOException, GameManifest.ManifestException, ModCodeDiff.DiffRefused {
				return ModCodeDiff.run(current.root(), store.load(oldEntry), oldEntry.source().label(), store.load(newEntry), newEntry.source().label(), cancelRequested::get);
			}

			@Override
			protected void done() {
				setBusy(false);
				try {
					show(get());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (CancellationException e) {
					showEmpty("That was cancelled.");
				} catch (ExecutionException e) {
					showEmpty(e.getCause() instanceof CancellationException ? "That was cancelled." : e.getCause() instanceof ModCodeDiff.DiffRefused ? e.getCause().getMessage() : "The diff couldn't be run: " + e.getCause().getMessage());
				}
			}
		}.execute();
	}

	/** Saves the report on screen, for the versions it compared, whatever the dropdowns say now. */
	public void export() {
		if (busy || lastReport == null || reportMod == null || store.reportsFolder() == null || DiffReportFile.problemWith(lastReport) != null) {
			return;
		}
		try {
			DiffReportFile.Saved saved = DiffReportFile.save(store.reportsFolder(), lastReport, reportMod.folderName(), reportMod.displayName());
			setExportNote((saved.created() ? "Saved the report to " : "This report is already saved at ") + saved.file() + ".");
		} catch (IOException | RuntimeException e) {
			setExportNote("The report couldn't be saved in " + store.reportsFolder() + ": " + e.getMessage());
		}
		updateExportState();
	}

	public void openReports() {
		Path folder = store.reportsFolder();
		if (folder == null || !Files.isDirectory(folder)) {
			return;
		}
		try {
			folderOpener.open(folder);
		} catch (IOException | RuntimeException e) {
			setExportNote("The folder couldn't be opened: " + e.getMessage());
		}
	}

	private void setExportNote(String text) {
		exportNote.setText(text);
		exportNote.setVisible(true);
		revalidate();
	}

	private void updateExportState() {
		boolean canSave = store.reportsFolder() != null;
		exportButton.setEnabled(canSave && !busy && lastReport != null && DiffReportFile.problemWith(lastReport) == null);
		openReportsButton.setEnabled(canSave && Files.isDirectory(store.reportsFolder()));
		String why = canSave ? "Saves this report beside the toolkit's .exe, in " + DiffReportFile.FOLDER_NAME : "Reports can only be saved from the packaged toolkit";
		exportButton.setToolTipText(PlainTip.of(why));
	}

	public boolean canExport() {
		return exportButton.isEnabled();
	}

	public boolean canOpenReports() {
		return openReportsButton.isEnabled();
	}

	public String exportNoteText() {
		return exportNote.isVisible() ? exportNote.getText() : "";
	}

	private void show(ModCodeDiff.Report report) {
		lastReport = report;
		for (Diagnostic finding : report.findings()) {
			String where = finding.file() + (finding.line() == null ? "" : ":" + finding.line());
			String what = finding.suggestion() == null ? finding.message() : finding.message() + " " + finding.suggestion();
			results.addRow(new Object[] { ModCodeDiff.groupOf(finding.severity()), where, finding.tagPath() == null ? "" : finding.tagPath(), what });
		}
		String sources = "Old: " + report.oldVersion() + " (" + report.oldSource() + "). New: " + report.newVersion() + " (" + report.newSource() + ").";
		summary.setText(report.summary() + " " + sources);
		// With no code, the middle of the screen already says so.
		summary.setVisible(report.hasCode());
		bodyNote.setText(report.bodyNote());
		bodyNote.setVisible(report.hasCode());
		updateExportState();
		if (results.getRowCount() > 0) {
			findings.getSelectionModel().setSelectionInterval(0, 0);
			resultViews.show(resultsPanel, "table");
		} else {
			showEmpty(report.hasCode() ? "Nothing in this update was found to affect this mod." : report.summary());
		}
	}

	/** The selected finding in full. */
	private void showDetail(int row) {
		if (row < 0 || row >= results.getRowCount()) {
			detail.setText(" ");
			return;
		}
		String reference = String.valueOf(results.getValueAt(row, 2));
		detail.setText((reference.isEmpty() ? "" : reference + ", in ") + results.getValueAt(row, 1) + ": " + results.getValueAt(row, 3));
	}

	public String detailText() {
		return detail.getText();
	}

	private void clearResults() {
		results.setRowCount(0);
		lastReport = null;
		summary.setVisible(false);
		bodyNote.setVisible(false);
		exportNote.setVisible(false);
		updateExportState();
	}

	private void showEmpty(String text) {
		emptyState.setText(text);
		resultViews.show(resultsPanel, "empty");
	}

	public void cancel() {
		cancelRequested.set(true);
		cancelButton.setEnabled(false);
	}

	private void setBusy(boolean value) {
		busy = value;
		progress.setVisible(value);
		cancelButton.setEnabled(value);
		generateButton.setEnabled(canGenerate && !value);
		oldVersion.setEnabled(!value);
		newVersion.setEnabled(!value);
		if (value) {
			runButton.setEnabled(false);
		} else {
			updateRunState();
		}
		updateExportState();
	}

	public boolean isBusy() {
		return busy;
	}

	public boolean canGenerate() {
		return generateButton.isEnabled();
	}

	public boolean canRun() {
		return runButton.isEnabled();
	}

	public String installedText() {
		return installedText.getText();
	}

	public String reasonText() {
		return reason.getText();
	}

	public String summaryText() {
		return summary.getText();
	}

	public String emptyText() {
		return emptyState.getText();
	}

	public int resultCount() {
		return results.getRowCount();
	}

	public String selectedOld() {
		return selectedId(oldVersion);
	}

	public String selectedNew() {
		return selectedId(newVersion);
	}

	public void selectOld(String id) {
		select(oldVersion, id);
	}

	public void selectNew(String id) {
		select(newVersion, id);
	}

	public List<String> versionLabels() {
		List<String> labels = new java.util.ArrayList<>();
		for (int i = 0; i < oldVersion.getItemCount(); i++) {
			labels.add(oldVersion.getItemAt(i).toString());
		}
		return labels;
	}

	public ModCodeDiff.Report lastReport() {
		return lastReport;
	}
}
