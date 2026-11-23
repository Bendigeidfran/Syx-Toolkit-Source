package syx.gui;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

import syx.core.AssetCreator;
import syx.core.GameInstall;
import syx.core.GameParser;
import syx.core.ModAssets;
import syx.core.ModEntry;
import syx.core.Workspace;

/** The home for the selected mod: its name, what can be done with it, and its assets. */
public final class HomeScreen extends JPanel {

	private static final long serialVersionUID = 1L;

	/** Shows a folder to the user. */
	public interface FolderOpener {
		void open(Path folder) throws IOException;
	}

	/** A file in the tree, which shows as its name. */
	private record FileRow(String name, Path path) {
		@Override
		public String toString() {
			return name;
		}
	}

	private final transient Supplier<Path> gameFolder;
	private final transient FolderOpener folderOpener;
	private final transient java.util.function.Consumer<Path> onOpenFile;
	private String openProblem;
	private transient ModEntry mod;
	private final JLabel modName = new JLabel(" ");
	private final JLabel status = new JLabel(" ");
	private final JLabel assetsSummary = new JLabel(" ");
	private final JPanel warnings = new JPanel();
	private String producerWarning;
	private String soundWarning;
	private final JLabel emptyHint = new JLabel("Nothing has been added yet. Add New Asset creates the first file.");
	private final JButton addAsset = new JButton("Add New Asset");
	private final JTree tree = new JTree(new DefaultMutableTreeNode());
	private final JScrollPane scroller;
	private int loadsRunning;

	public HomeScreen(Supplier<Path> gameFolder, FolderOpener folderOpener, Runnable onAddAsset, java.util.function.Consumer<Path> onOpenFile) {
		super(new BorderLayout(0, 16));
		this.gameFolder = gameFolder;
		this.folderOpener = folderOpener;
		this.onOpenFile = onOpenFile;
		setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
		Theme.style(modName, "h2");
		Theme.tone(status, Theme.Tone.HELP);
		Theme.tone(assetsSummary, Theme.Tone.HELP);
		Theme.tone(emptyHint, Theme.Tone.HELP);
		Theme.style(addAsset, "primary");
		addAsset.addActionListener(e -> onAddAsset.run());

		JPanel names = new JPanel();
		names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
		names.add(modName);
		names.add(Box.createVerticalStrut(4));
		names.add(status);
		JPanel header = new JPanel(new BorderLayout());
		header.add(names, BorderLayout.CENTER);
		JPanel addHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		addHolder.add(addAsset);
		header.add(addHolder, BorderLayout.EAST);
		warnings.setLayout(new BorderLayout());
		warnings.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
		warnings.setVisible(false);
		JPanel top = new JPanel(new BorderLayout());
		top.add(header, BorderLayout.NORTH);
		top.add(warnings, BorderLayout.CENTER);

		JLabel assetsTitle = new JLabel("Assets");
		Theme.style(assetsTitle, "h3");
		JPanel assetsHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		assetsHeader.setOpaque(false);
		assetsHeader.add(assetsTitle);
		assetsHeader.add(assetsSummary);
		assetsHeader.add(emptyHint);

		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		tree.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e) {
				if (e.getClickCount() == 2 && javax.swing.SwingUtilities.isLeftMouseButton(e)) {
					javax.swing.tree.TreePath path = tree.getPathForLocation(e.getX(), e.getY());
					if (path != null) {
						openRow(path.getLastPathComponent());
					}
				}
			}
		});
		tree.addKeyListener(new java.awt.event.KeyAdapter() {
			@Override
			public void keyPressed(java.awt.event.KeyEvent e) {
				if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER && tree.getSelectionPath() != null) {
					openRow(tree.getSelectionPath().getLastPathComponent());
				}
			}
		});
		scroller = new JScrollPane(tree);
		scroller.setBorder(BorderFactory.createEmptyBorder());
		scroller.getVerticalScrollBar().setUnitIncrement(16);

		JPanel card = new JPanel(new BorderLayout(0, 10));
		Theme.style(card, "card");
		card.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
		card.add(assetsHeader, BorderLayout.NORTH);
		card.add(scroller, BorderLayout.CENTER);

		add(top, BorderLayout.NORTH);
		add(card, BorderLayout.CENTER);
	}

	private void openRow(Object row) {
		if (row instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof FileRow file) {
			showOpenProblem(null);
			onOpenFile.accept(file.path());
		}
	}

	/** Opens a file in the tree, as double-clicking it would. An asset's files share a name, so this goes by path. */
	public void openFile(Path file) {
		for (int row = 0; row < tree.getRowCount(); row++) {
			Object component = tree.getPathForRow(row).getLastPathComponent();
			if (component instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof FileRow shown && shown.path().equals(file)) {
				openRow(component);
				return;
			}
		}
		throw new IllegalArgumentException("No row for " + file);
	}

	/** Says why a file can't be opened, or clears that if the text is null. */
	public void showOpenProblem(String text) {
		openProblem = text;
		showWarnings(producerWarning, soundWarning);
	}

	public String openProblemText() {
		return openProblem;
	}

	/** Opens a folder in the system's file manager, which is Explorer on Windows. */
	public static void openInFileManager(Path folder) throws IOException {
		if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
			throw new IOException("This system doesn't support opening folders.");
		}
		Desktop.getDesktop().open(folder.toFile());
	}

	/** Clicks Open Folder. The result shows in the status line if it fails. */
	public void openModFolder() {
		if (mod == null) {
			return;
		}
		Path folder = mod.root();
		ModEntry shown = mod;
		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws IOException {
				folderOpener.open(folder);
				return null;
			}

			@Override
			protected void done() {
				try {
					get();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					if (shown == mod) {
						setStatus("The folder couldn't be opened: " + e.getCause().getMessage());
					}
				}
			}
		}.execute();
	}

	/** Shows a mod, and reloads its assets from disk. */
	public void show(ModEntry entry) {
		mod = entry;
		openProblem = null;
		modName.setText(entry.displayName());
		setStatus(entry.status() == null ? " " : entry.status());
		assetsSummary.setText("Loading...");
		emptyHint.setVisible(false);
		setTree(new DefaultMutableTreeNode());
		loadsRunning++;
		Path game = gameFolder.get();
		new SwingWorker<Loaded, Void>() {
			@Override
			protected Loaded doInBackground() {
				// Read again each time, so adding a room or a sound file clears its warning.
				return new Loaded(ModAssets.read(entry.root(), currentMajor(game)), Workspace.producerWarning(entry, game), Workspace.soundWarning(entry, game));
			}

			@Override
			protected void done() {
				loadsRunning--;
				try {
					// Only the latest load counts, if the mod was changed in the meantime.
					if (loadsRunning == 0) {
						Loaded loaded = get();
						showAssets(loaded.listing());
						showWarnings(loaded.producerWarning(), loaded.soundWarning());
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					assetsSummary.setText("The assets couldn't be read: " + e.getCause());
				}
			}
		}.execute();
	}

	private record Loaded(ModAssets.Listing listing, String producerWarning, String soundWarning) {
	}

	private void showWarnings(String producers, String sounds) {
		producerWarning = producers;
		soundWarning = sounds;
		warnings.removeAll();
		if (openProblem != null) {
			warnings.add(WrappedText.callout(openProblem), BorderLayout.NORTH);
		}
		if (producers != null) {
			warnings.add(WrappedText.callout(producers), BorderLayout.CENTER);
		}
		if (sounds != null) {
			warnings.add(WrappedText.callout(sounds), producers == null ? BorderLayout.CENTER : BorderLayout.SOUTH);
		}
		warnings.setVisible(openProblem != null || producers != null || sounds != null);
		warnings.revalidate();
		warnings.repaint();
	}

	/** What Home is warning about, or null. */
	public String producerWarningText() {
		return producerWarning;
	}

	/** What Home is warning about sounds, or null. */
	public String soundWarningText() {
		return soundWarning;
	}

	private static int currentMajor(Path game) {
		if (!GameInstall.isValid(game)) {
			return -1;
		}
		try (GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
			return parser.version().major();
		} catch (GameParser.UnavailableException e) {
			return -1;
		}
	}

	private void setStatus(String text) {
		status.setText(text);
		status.setVisible(!text.isBlank());
	}

	public boolean isLoading() {
		return loadsRunning > 0;
	}

	public String modNameText() {
		return modName.getText();
	}

	public String statusText() {
		return status.getText();
	}

	public boolean isEmptyHintShowing() {
		return emptyHint.isVisible();
	}

	public String assetsSummaryText() {
		return assetsSummary.getText();
	}

	public int treeRowCount() {
		return tree.getRowCount();
	}

	/** The text of every row in the asset tree, from top to bottom. */
	public java.util.List<String> treeRows() {
		java.util.List<String> rows = new java.util.ArrayList<>();
		for (int row = 0; row < tree.getRowCount(); row++) {
			rows.add(String.valueOf(tree.getPathForRow(row).getLastPathComponent()));
		}
		return rows;
	}

	/** Clicks Add New Asset. */
	public void clickAddAsset() {
		addAsset.doClick();
	}

	private void showAssets(ModAssets.Listing listing) {
		DefaultMutableTreeNode root = new DefaultMutableTreeNode();
		for (ModAssets.VersionTree version : listing.versions()) {
			DefaultMutableTreeNode node = new DefaultMutableTreeNode(version.label(), true);
			for (ModAssets.Node child : version.root().children()) {
				node.add(treeNode(child));
			}
			root.add(node);
		}
		setTree(root);
		String summary = listing.versions().isEmpty()
				? "This mod has no version folders yet."
				: listing.files() + (listing.files() == 1 ? " file" : " files") + (listing.files() == 0 ? "" : ". Double-click an asset to edit it");
		if (!listing.unreadable().isEmpty()) {
			summary += ". " + listing.unreadable().size() + " folders couldn't be read, so more may exist.";
		}
		assetsSummary.setText(summary);
		emptyHint.setVisible(listing.files() == 0);
		scroller.getVerticalScrollBar().setValue(0);
		revalidate();
	}

	private static DefaultMutableTreeNode treeNode(ModAssets.Node asset) {
		DefaultMutableTreeNode node = new DefaultMutableTreeNode(asset.folder() ? asset.name() : new FileRow(asset.name(), asset.path()), asset.folder());
		for (ModAssets.Node child : asset.children()) {
			node.add(treeNode(child));
		}
		return node;
	}

	private void setTree(DefaultMutableTreeNode root) {
		// Asking whether a node allows children shows empty folders as folders, not files.
		tree.setModel(new DefaultTreeModel(root, true));
		for (int row = 0; row < tree.getRowCount(); row++) {
			tree.expandRow(row);
		}
	}
}
