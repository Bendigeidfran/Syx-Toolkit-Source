package syx.gui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.MouseEvent;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.ToolTipManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

import syx.core.Diagnostic;
import syx.core.SyntaxChecker;

/** Displays the core's syntax check. All checking logic lives in {@link SyntaxChecker}. */
public final class SyntaxCheckScreen extends JPanel {

	private static final long serialVersionUID = 1L;

	private final transient Supplier<Path> gameFolder;
	private final JTextField folderField = new JTextField();
	private final JButton browseButton = new JButton("Browse...");
	private final JButton checkButton = new JButton("Check");
	private final JButton cancelButton = new JButton("Cancel");
	private final JProgressBar progress = new JProgressBar();
	private final WrappedText summary = new WrappedText(" ", null);
	private final WrappedText emptyState = new WrappedText(" ", Theme.Tone.HELP);
	private final CardLayout resultViews = new CardLayout();
	private final JPanel resultsPanel = new JPanel();
	private final DefaultTableModel results = new DefaultTableModel(new Object[] { "Severity", "File", "Line", "Message" }, 0) {
		private static final long serialVersionUID = 1L;

		@Override
		public boolean isCellEditable(int row, int column) {
			return false;
		}
	};
	private final transient AtomicBoolean cancelRequested = new AtomicBoolean();
	private transient Consumer<SyntaxChecker.Result> onFinished = r -> {
	};
	private boolean running;

	public SyntaxCheckScreen(Supplier<Path> gameFolder) {
		super(new BorderLayout(0, 12));
		this.gameFolder = gameFolder;
		setBorder(BorderFactory.createEmptyBorder(22, 24, 14, 24));

		JLabel heading = new JLabel("Check Syntax");
		Theme.style(heading, "h2");
		Theme.style(checkButton, "primary");
		browseButton.addActionListener(e -> browse());
		checkButton.addActionListener(e -> startCheck());
		cancelButton.addActionListener(e -> cancelCheck());
		cancelButton.setEnabled(false);

		JPanel folderRow = new JPanel(new BorderLayout(8, 0));
		folderRow.add(new JLabel("Mod folder"), BorderLayout.WEST);
		folderRow.add(folderField, BorderLayout.CENTER);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.add(browseButton);
		buttons.add(cancelButton);
		buttons.add(checkButton);
		folderRow.add(buttons, BorderLayout.EAST);

		// This says what the check doesn't cover, so it stays on screen.
		WrappedText note = new WrappedText(SyntaxChecker.SCOPE_NOTE, Theme.Tone.HELP);

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		heading.setAlignmentX(LEFT_ALIGNMENT);
		folderRow.setAlignmentX(LEFT_ALIGNMENT);
		folderRow.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, folderRow.getPreferredSize().height));
		top.add(heading);
		top.add(javax.swing.Box.createVerticalStrut(12));
		top.add(folderRow);
		top.add(javax.swing.Box.createVerticalStrut(10));
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
		table.setAutoCreateRowSorter(true);
		table.setRowHeight(28);
		table.setShowHorizontalLines(true);
		table.getColumnModel().getColumn(0).setPreferredWidth(110);
		table.getColumnModel().getColumn(0).setMaxWidth(130);
		table.getColumnModel().getColumn(1).setPreferredWidth(280);
		table.getColumnModel().getColumn(2).setPreferredWidth(50);
		table.getColumnModel().getColumn(2).setMaxWidth(70);
		table.getColumnModel().getColumn(3).setPreferredWidth(480);
		table.getColumnModel().getColumn(0).setCellRenderer(new SeverityRenderer());
		// Messages can quote a mod's own text, which must never be read as HTML.
		DefaultTableCellRenderer plain = new DefaultTableCellRenderer();
		plain.putClientProperty("html.disable", Boolean.TRUE);
		for (int column = 1; column < 4; column++) {
			table.getColumnModel().getColumn(column).setCellRenderer(plain);
		}
		JScrollPane tableScroller = new JScrollPane(table);
		tableScroller.setBorder(BorderFactory.createEmptyBorder());

		Theme.tone(emptyState, Theme.Tone.HELP);
		emptyState.setBorder(BorderFactory.createEmptyBorder(12, 8, 8, 8));
		resultsPanel.setLayout(resultViews);
		resultsPanel.setOpaque(false);
		JPanel emptyHolder = new JPanel(new BorderLayout());
		emptyHolder.setOpaque(false);
		emptyHolder.add(emptyState, BorderLayout.NORTH);
		resultsPanel.add(emptyHolder, "empty");
		resultsPanel.add(tableScroller, "table");

		JPanel card = new JPanel(new BorderLayout());
		Theme.style(card, "card");
		card.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		card.add(resultsPanel, BorderLayout.CENTER);

		JPanel bottom = new JPanel(new BorderLayout(0, 6));
		progress.setVisible(false);
		bottom.add(progress, BorderLayout.NORTH);
		bottom.add(summary, BorderLayout.SOUTH);

		add(top, BorderLayout.NORTH);
		add(card, BorderLayout.CENTER);
		add(bottom, BorderLayout.SOUTH);
		showResultsView();
	}

	/** Shows the table if it has anything in it, and otherwise says what happened in words. */
	private void showResultsView() {
		boolean any = results.getRowCount() > 0;
		resultViews.show(resultsPanel, any ? "table" : "empty");
		// With no rows, the result is already in the middle of the screen.
		summary.setVisible(any || running);
		if (!any) {
			String said = summary.getText();
			emptyState.setText(said.isBlank() ? "Press Check to look through this mod's files with the game's own parser. Nothing is changed." : said);
		}
	}

	/** Shows a result's severity as a chip. */
	private static final class SeverityRenderer implements TableCellRenderer {
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused, int row, int column) {
			String text = String.valueOf(value);
			JPanel cell = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
			cell.setBackground(selected ? table.getSelectionBackground() : table.getBackground());
			cell.add(new Chip(text, text.equals("Error") ? Theme.Tone.ERROR : Theme.Tone.WARNING));
			return cell;
		}
	}

	public void setFolder(Path folder) {
		folderField.setText(folder.toString());
	}

	public void setOnFinished(Consumer<SyntaxChecker.Result> onFinished) {
		this.onFinished = onFinished;
	}

	public void startCheck() {
		if (running) {
			return;
		}
		String text = folderField.getText().trim();
		if (text.isEmpty()) {
			summary.setText("Choose a mod folder first.");
			return;
		}
		Path folder;
		try {
			folder = Paths.get(text);
		} catch (InvalidPathException e) {
			summary.setText("That isn't a valid folder path.");
			return;
		}
		setRunning(true);
		results.setRowCount(0);
		progress.setValue(0);
		summary.setText("Checking...");
		showResultsView();
		cancelRequested.set(false);
		Path game = gameFolder.get();

		new SwingWorker<SyntaxChecker.Result, int[]>() {
			private volatile String current = "";

			@Override
			protected SyntaxChecker.Result doInBackground() {
				return SyntaxChecker.check(folder, game, (done, total, file) -> {
					current = file;
					publish(new int[] { done, total });
				}, cancelRequested::get);
			}

			@Override
			protected void process(List<int[]> chunks) {
				int[] latest = chunks.get(chunks.size() - 1);
				progress.setMaximum(Math.max(1, latest[1]));
				progress.setValue(latest[0]);
				if (!cancelRequested.get()) {
					summary.setText("Checking " + current);
				}
			}

			@Override
			protected void done() {
				setRunning(false);
				try {
					show(get());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					summary.setText("The check was interrupted.");
				} catch (ExecutionException e) {
					summary.setText("The check failed unexpectedly: " + e.getCause());
				}
			}
		}.execute();
	}

	public void cancelCheck() {
		cancelRequested.set(true);
		cancelButton.setEnabled(false);
		summary.setText("Cancelling...");
	}

	public int resultCount() {
		return results.getRowCount();
	}

	public String summaryText() {
		return summary.getText();
	}

	private void show(SyntaxChecker.Result result) {
		for (Diagnostic d : result.diagnostics()) {
			results.addRow(new Object[] { label(d.severity()), d.file() == null ? "" : d.file(),
					d.line() == null ? "" : d.line(), d.message() });
		}
		summary.setText(result.summary());
		showResultsView();
		onFinished.accept(result);
	}

	private static String label(Diagnostic.Severity severity) {
		return switch (severity) {
		case ERROR -> "Error";
		case WARNING -> "Warning";
		case REVIEW -> "Review";
		case TOOL_FAILURE -> "Tool problem";
		};
	}

	private void setRunning(boolean value) {
		running = value;
		checkButton.setEnabled(!value);
		browseButton.setEnabled(!value);
		folderField.setEnabled(!value);
		cancelButton.setEnabled(value);
		progress.setVisible(value);
	}

	private void browse() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Choose a mod folder");
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (chooser.showDialog(this, "Use this folder") == JFileChooser.APPROVE_OPTION) {
			setFolder(chooser.getSelectedFile().toPath());
		}
	}
}
