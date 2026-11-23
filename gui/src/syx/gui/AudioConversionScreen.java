package syx.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

import com.formdev.flatlaf.FlatClientProperties;

import syx.audio.AudioConversion;
import syx.core.ModEntry;

/** Lists the current mod's audio files and converts one. The conversion itself is {@link AudioConversion}. */
public final class AudioConversionScreen extends JPanel {

	private static final long serialVersionUID = 1L;

	/** A file in the tree. A folder is just its name. */
	private record AudioRow(String name, String relativePath, boolean ogg) {
		@Override
		public String toString() {
			return name;
		}
	}

	private final JLabel heading = new JLabel("Audio Conversion");
	private final WrappedText help = new WrappedText("WAV, MP3, AIFF and AU can be converted to an .ogg of the same name. An .ogg file is already in that format, so it is shown greyed out. The original is moved out of the mod.", Theme.Tone.HELP);
	private final WrappedText status = new WrappedText(" ", null);
	private final JLabel emptyHint = new JLabel("This mod has no audio files.");
	private final JButton convertButton = new JButton("Convert to OGG");
	private final JTree tree = new JTree(new DefaultMutableTreeNode());
	private final JScrollPane scroller;
	private final RowRenderer renderer = new RowRenderer();
	private transient ModEntry mod;
	private boolean busy;
	private boolean suppressSelection;

	public AudioConversionScreen() {
		super(new BorderLayout(0, 16));
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		Theme.style(heading, "h2");
		Theme.tone(emptyHint, Theme.Tone.HELP);
		Theme.style(convertButton, "primary");
		convertButton.setEnabled(false);
		convertButton.addActionListener(e -> convert());

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		heading.setAlignmentX(LEFT_ALIGNMENT);
		help.setAlignmentX(LEFT_ALIGNMENT);
		status.setAlignmentX(LEFT_ALIGNMENT);
		top.add(heading);
		top.add(Box.createVerticalStrut(4));
		top.add(help);
		top.add(Box.createVerticalStrut(8));
		top.add(status);

		JLabel title = new JLabel("Audio files");
		Theme.style(title, "h3");
		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setOpaque(false);
		titleRow.add(title, BorderLayout.WEST);
		JPanel buttonHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		buttonHolder.setOpaque(false);
		buttonHolder.add(convertButton);
		titleRow.add(buttonHolder, BorderLayout.EAST);

		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		tree.setCellRenderer(renderer);
		tree.addTreeSelectionListener(this::selected);
		scroller = new JScrollPane(tree);
		scroller.setBorder(BorderFactory.createEmptyBorder());
		scroller.getVerticalScrollBar().setUnitIncrement(16);

		JPanel card = new JPanel(new BorderLayout(0, 10));
		Theme.style(card, "card");
		card.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
		card.add(titleRow, BorderLayout.NORTH);
		JPanel body = new JPanel(new BorderLayout());
		body.setOpaque(false);
		body.add(emptyHint, BorderLayout.NORTH);
		body.add(scroller, BorderLayout.CENTER);
		card.add(body, BorderLayout.CENTER);

		add(top, BorderLayout.NORTH);
		add(card, BorderLayout.CENTER);
	}

	/** Reads this mod's audio files. */
	public void open(ModEntry mod) {
		this.mod = mod;
		if (busy) {
			return;
		}
		setStatus(" ", null);
		reload(null);
	}

	public boolean isBusy() {
		return busy;
	}

	public String statusText() {
		return status.getText();
	}

	public boolean isEmpty() {
		return emptyHint.isVisible();
	}

	public boolean canConvert() {
		return convertButton.isEnabled();
	}

	/** Clicks Convert to OGG. */
	public void clickConvert() {
		convertButton.doClick();
	}

	public List<String> treeRows() {
		List<String> rows = new java.util.ArrayList<>();
		for (int row = 0; row < tree.getRowCount(); row++) {
			rows.add(String.valueOf(tree.getPathForRow(row).getLastPathComponent()));
		}
		return rows;
	}

	/** Whether a file row is drawn ghosted when it isn't selected. */
	public boolean isGhosted(String name) {
		for (int row = 0; row < tree.getRowCount(); row++) {
			Object last = tree.getPathForRow(row).getLastPathComponent();
			if (last instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof AudioRow audio && audio.name().equals(name)) {
				Component rendered = renderer.getTreeCellRendererComponent(tree, node, false, false, true, row, false);
				Object style = ((JComponent) rendered).getClientProperty(FlatClientProperties.STYLE_CLASS);
				return style != null && style.toString().contains("toneGhost");
			}
		}
		return false;
	}

	/** Selects a file by its name, as clicking its row would. */
	public void select(String name) {
		for (int row = 0; row < tree.getRowCount(); row++) {
			Object last = tree.getPathForRow(row).getLastPathComponent();
			if (last instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof AudioRow audio && audio.name().equals(name)) {
				tree.setSelectionPath(tree.getPathForRow(row));
				return;
			}
		}
		throw new IllegalArgumentException("No row for " + name);
	}

	private void selected(TreeSelectionEvent event) {
		if (!suppressSelection) {
			AudioRow row = selectedRow();
			if (row != null && row.ogg()) {
				setStatus(row.name() + " is already an .ogg file.", Theme.Tone.HELP);
			} else if (row != null) {
				setStatus(" ", null);
			}
			updateButton();
		}
	}

	private void convert() {
		AudioRow row = selectedRow();
		if (busy || mod == null || row == null || row.ogg()) {
			return;
		}
		busy = true;
		convertButton.setEnabled(false);
		setStatus("Converting " + row.name() + "...", Theme.Tone.HELP);
		String relativePath = row.relativePath();
		new SwingWorker<Outcome, Void>() {
			@Override
			protected Outcome doInBackground() throws Exception {
				AudioConversion.Converted converted = AudioConversion.convert(mod.root(), mod.folderName(), relativePath);
				return new Outcome(converted, AudioConversion.list(mod.root()));
			}

			@Override
			protected void done() {
				busy = false;
				try {
					Outcome outcome = get();
					showFiles(outcome.files());
					setStatus(outcome.converted().message(), Theme.Tone.SUCCESS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					setStatus("The conversion was interrupted.", Theme.Tone.ERROR);
				} catch (ExecutionException e) {
					Throwable cause = e.getCause() == null ? e : e.getCause();
					setStatus(cause.getMessage(), Theme.Tone.ERROR);
				}
				updateButton();
			}
		}.execute();
	}

	private void reload(String keep) {
		if (mod == null) {
			return;
		}
		busy = true;
		convertButton.setEnabled(false);
		new SwingWorker<List<AudioConversion.Listed>, Void>() {
			@Override
			protected List<AudioConversion.Listed> doInBackground() throws Exception {
				return AudioConversion.list(mod.root());
			}

			@Override
			protected void done() {
				busy = false;
				try {
					showFiles(get());
					if (keep != null) {
						setStatus(keep, Theme.Tone.SUCCESS);
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					setStatus("The audio files couldn't be read.", Theme.Tone.ERROR);
				} catch (ExecutionException e) {
					Throwable cause = e.getCause() == null ? e : e.getCause();
					setStatus(cause.getMessage(), Theme.Tone.ERROR);
				}
				updateButton();
			}
		}.execute();
	}

	private void showFiles(List<AudioConversion.Listed> files) {
		DefaultMutableTreeNode root = new DefaultMutableTreeNode();
		Map<String, DefaultMutableTreeNode> folders = new HashMap<>();
		folders.put("", root);
		for (AudioConversion.Listed file : files) {
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
			parent.add(new DefaultMutableTreeNode(new AudioRow(path.getFileName().toString(), file.path(), file.ogg()), false));
		}
		suppressSelection = true;
		tree.setModel(new DefaultTreeModel(root, true));
		for (int row = 0; row < tree.getRowCount(); row++) {
			tree.expandRow(row);
		}
		suppressSelection = false;
		emptyHint.setVisible(files.isEmpty());
		scroller.setVisible(!files.isEmpty());
		revalidate();
	}

	private void updateButton() {
		AudioRow row = selectedRow();
		convertButton.setEnabled(!busy && row != null && !row.ogg());
	}

	private AudioRow selectedRow() {
		Object last = tree.getLastSelectedPathComponent();
		if (last instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof AudioRow row) {
			return row;
		}
		return null;
	}

	private void setStatus(String text, Theme.Tone tone) {
		status.setText(text == null || text.isBlank() ? " " : text);
		if (tone == null) {
			Object style = status.getClientProperty(FlatClientProperties.STYLE_CLASS);
			if (style != null) {
				String kept = java.util.Arrays.stream(style.toString().split(" ")).filter(c -> !c.startsWith("tone")).reduce((a, b) -> a + " " + b).orElse("");
				status.putClientProperty(FlatClientProperties.STYLE_CLASS, kept);
			}
		} else {
			Theme.tone(status, tone);
		}
	}

	private record Outcome(AudioConversion.Converted converted, List<AudioConversion.Listed> files) {
	}

	/** Ghosts an .ogg row, and leaves a selected row in the selection colour. */
	private static final class RowRenderer extends DefaultTreeCellRenderer {
		private static final long serialVersionUID = 1L;

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean focused) {
			super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focused);
			putClientProperty("html.disable", Boolean.TRUE);
			clearTone();
			if (!selected && value instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof AudioRow audio && audio.ogg()) {
				Theme.tone(this, Theme.Tone.GHOST);
			}
			return this;
		}

		private void clearTone() {
			Object style = getClientProperty(FlatClientProperties.STYLE_CLASS);
			if (style == null) {
				return;
			}
			String kept = java.util.Arrays.stream(style.toString().split(" ")).filter(c -> !c.startsWith("tone")).reduce((a, b) -> a + " " + b).orElse("");
			putClientProperty(FlatClientProperties.STYLE_CLASS, kept);
		}
	}
}
