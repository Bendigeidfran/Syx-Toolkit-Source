package syx.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.ToolTipManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import syx.core.AssetCatalog;
import syx.core.AssetCreator;
import syx.core.AssetForm;
import syx.core.GameAssetIndex;
import syx.core.GameParser;
import syx.core.HandbookStore;
import syx.core.ModEntry;
import syx.core.ResourceProducers;
import syx.core.Workspace;

/** The first step of adding an asset: choose its type and name. All the rules are in the core. */
public final class AddAssetScreen extends JPanel {

	private static final long serialVersionUID = 1L;

	/** What the next step needs. */
	public record Choice(AssetCatalog.Entry entry, String name, GameAssetIndex index, GameParser.GameVersion version, HandbookStore.Loaded handbook, ResourceProducers producers, String appearance) {
		public Choice(AssetCatalog.Entry entry, String name, GameAssetIndex index, GameParser.GameVersion version, HandbookStore.Loaded handbook, ResourceProducers producers) {
			this(entry, name, index, version, handbook, producers, null);
		}
	}

	private record Loaded(List<AssetCatalog.Entry> entries, GameAssetIndex index, GameParser.GameVersion version, String versionWarning, HandbookStore.Loaded handbook, ResourceProducers producers) {
	}

	/** One line of the type list. */
	private sealed interface Row permits TypeRow, GroupRow {
	}

	/** A type, indented if it's inside an open group. */
	private record TypeRow(AssetCatalog.Entry entry, boolean nested) implements Row {
	}

	/** A group's header, which opens and closes the types under it. */
	private record GroupRow(String name, int size, boolean open) implements Row {
	}

	private final transient Supplier<Path> gameFolder;
	private final transient HandbookStore handbookStore;
	private final transient Consumer<Choice> onNext;
	private final JLabel title = new JLabel("Add New Asset");
	private final DefaultListModel<Row> listModel = new DefaultListModel<>();
	private final JList<Row> typeList = new JList<>(listModel) {
		private static final long serialVersionUID = 1L;

		@Override
		public String getToolTipText(MouseEvent event) {
			int index = locationToIndex(event.getPoint());
			if (index < 0 || !(getModel().getElementAt(index) instanceof TypeRow row) || row.entry().available()) {
				return null;
			}
			return PlainTip.of(row.entry().unavailableReason());
		}
	};
	private final transient java.util.Set<String> openGroups = new java.util.HashSet<>();
	private transient List<AssetCatalog.Entry> ordered = List.of();
	private final JTextField nameField = new JTextField();
	private final JLabel appearanceHeading = new JLabel("Appearance");
	private final JComboBox<String> appearance = new JComboBox<>();
	private final WrappedText nameProblem = new WrappedText(" ", Theme.Tone.ERROR);
	private final WrappedText destination = new WrappedText(" ", Theme.Tone.HELP);
	private final WrappedText versionWarning = new WrappedText(" ", Theme.Tone.WARNING);
	private final WrappedText typeHint = new WrappedText(" ", Theme.Tone.WARNING);
	private final WrappedText message = new WrappedText(" ", Theme.Tone.ERROR);
	private final JPanel body = new JPanel();
	private final JPanel typesCard = new JPanel();
	private final JPanel nameCard = new JPanel();
	private final JButton back = new JButton("Back");
	private final JButton next = new JButton("Next");
	private transient Loaded loaded;
	private boolean busy;
	private boolean refreshing;
	private boolean rebuilding;

	public AddAssetScreen(Supplier<Path> gameFolder, HandbookStore handbookStore, Runnable onBack, Consumer<Choice> onNext) {
		super(new BorderLayout(0, 14));
		this.gameFolder = gameFolder;
		this.handbookStore = handbookStore;
		this.onNext = onNext;
		setBorder(BorderFactory.createEmptyBorder(22, 24, 14, 24));
		Theme.style(title, "h2");

		typeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		ToolTipManager.sharedInstance().registerComponent(typeList);
		typeList.setCellRenderer(new TypeRenderer());
		typeList.addListSelectionListener(e -> {
			// An unavailable type can't stay selected, whichever way it was reached.
			if (rebuilding) {
				return;
			}
			AssetCatalog.Entry chosen = chosenEntry();
			if (chosen != null && !chosen.available()) {
				typeHint.setText(chosen.type().label() + " isn't available yet: " + chosen.unavailableReason());
				typeHint.setVisible(true);
				typeList.clearSelection();
			} else if (chosen != null) {
				typeHint.setVisible(false);
			}
			refresh();
		});
		typeList.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent event) {
				int index = typeList.locationToIndex(event.getPoint());
				if (index >= 0 && typeList.getCellBounds(index, index).contains(event.getPoint()) && listModel.get(index) instanceof GroupRow group) {
					setGroupOpen(group.name(), !group.open());
				}
			}
		});
		typeList.addKeyListener(new java.awt.event.KeyAdapter() {
			@Override
			public void keyPressed(java.awt.event.KeyEvent event) {
				if (!(typeList.getSelectedValue() instanceof GroupRow group)) {
					return;
				}
				int key = event.getKeyCode();
				boolean toggle = key == java.awt.event.KeyEvent.VK_ENTER || key == java.awt.event.KeyEvent.VK_SPACE;
				boolean open = toggle ? !group.open() : key == java.awt.event.KeyEvent.VK_RIGHT;
				if (toggle || key == java.awt.event.KeyEvent.VK_RIGHT || key == java.awt.event.KeyEvent.VK_LEFT) {
					setGroupOpen(group.name(), open);
					event.consume();
				}
			}
		});

		nameField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				refresh();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				refresh();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				refresh();
			}
		});

		JLabel typeHeading = new JLabel("Type");
		Theme.style(typeHeading, "h4");
		Theme.tone(typeHeading, Theme.Tone.HEADING);
		JScrollPane typeScroller = new JScrollPane(typeList);
		typeScroller.setBorder(BorderFactory.createEmptyBorder());
		typeScroller.getVerticalScrollBar().setUnitIncrement(16);
		typesCard.setLayout(new BorderLayout(0, 8));
		Theme.style(typesCard, "card");
		typesCard.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
		typesCard.add(typeHeading, BorderLayout.NORTH);
		typesCard.add(typeScroller, BorderLayout.CENTER);

		JLabel nameHeading = new JLabel("Name");
		Theme.style(nameHeading, "h4");
		Theme.tone(nameHeading, Theme.Tone.HEADING);
		nameField.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, nameField.getPreferredSize().height));
		nameField.putClientProperty(com.formdev.flatlaf.FlatClientProperties.PLACEHOLDER_TEXT, "The new file's name, without .txt");
		Theme.style(appearanceHeading, "h4");
		Theme.tone(appearanceHeading, Theme.Tone.HEADING);
		appearance.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, appearance.getPreferredSize().height));
		appearance.addActionListener(e -> refresh());
		appearanceHeading.setVisible(false);
		appearance.setVisible(false);
		JPanel lower = new JPanel();
		lower.setLayout(new BoxLayout(lower, BoxLayout.Y_AXIS));
		Theme.style(lower, "card");
		lower.setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
		for (javax.swing.JComponent item : List.of(nameHeading, nameField, appearanceHeading, appearance, nameProblem, destination, versionWarning, typeHint)) {
			item.setAlignmentX(LEFT_ALIGNMENT);
			lower.add(item);
			lower.add(Box.createVerticalStrut(8));
		}
		nameCard.setLayout(new BorderLayout());
		nameCard.add(lower, BorderLayout.NORTH);

		body.add(typesCard);
		body.add(nameCard);

		back.addActionListener(e -> onBack.run());
		Theme.style(next, "primary");
		next.addActionListener(e -> next());
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.add(back);
		buttons.add(next);
		JPanel footer = new JPanel(new BorderLayout(12, 0));
		footer.add(message, BorderLayout.CENTER);
		footer.add(buttons, BorderLayout.EAST);

		add(title, BorderLayout.NORTH);
		add(body, BorderLayout.CENTER);
		add(footer, BorderLayout.SOUTH);
		setBand(WidthBand.WIDE);
	}

	/** Puts the name beside the list when there's room, and under it when there isn't. */
	void setBand(WidthBand band) {
		boolean stacked = band == WidthBand.NARROW;
		body.setLayout(stacked ? new BoxLayout(body, BoxLayout.Y_AXIS) : new java.awt.GridLayout(1, 2, 14, 0));
		body.revalidate();
	}

	/** Shows each type's name and each group's header, with a heading once the types that can't be used begin. */
	private final class TypeRenderer implements javax.swing.ListCellRenderer<Row> {
		@Override
		public Component getListCellRendererComponent(JList<? extends Row> list, Row item, int index, boolean selected, boolean focused) {
			boolean usable = !(item instanceof TypeRow type) || type.entry().available();
			boolean nested = item instanceof TypeRow type && type.nested();
			JPanel row = new JPanel(new BorderLayout(8, 0));
			row.setBorder(BorderFactory.createEmptyBorder(5, nested ? 28 : 8, 5, 8));
			boolean chosen = selected && usable;
			row.setBackground(chosen ? list.getSelectionBackground() : list.getBackground());
			JLabel name = new JLabel(item instanceof GroupRow group ? (group.open() ? "▾ " : "▸ ") + group.name() + " (" + group.size() + ")" : ((TypeRow) item).entry().type().label());
			name.putClientProperty("html.disable", Boolean.TRUE);
			if (item instanceof GroupRow) {
				Theme.style(name, "semibold");
			}
			if (chosen) {
				name.setForeground(list.getSelectionForeground());
			} else if (!usable) {
				Theme.tone(name, Theme.Tone.GHOST);
			}
			row.add(name, BorderLayout.CENTER);
			if (index != firstUnavailable()) {
				return row;
			}
			JPanel withHeading = new JPanel(new BorderLayout());
			withHeading.setBackground(list.getBackground());
			JLabel heading = new JLabel("Not available yet");
			Theme.style(heading, "semibold");
			Theme.tone(heading, Theme.Tone.HELP);
			heading.setBorder(BorderFactory.createEmptyBorder(10, 8, 4, 8));
			withHeading.add(heading, BorderLayout.NORTH);
			withHeading.add(row, BorderLayout.CENTER);
			return withHeading;
		}
	}

	/** The first ungrouped type that can't be used. A type inside a group keeps its place in the group. */
	private int firstUnavailable() {
		for (int i = 0; i < listModel.size(); i++) {
			if (listModel.get(i) instanceof TypeRow type && !type.nested() && !type.entry().available()) {
				return i;
			}
		}
		return -1;
	}

	/** The type on the selected line, or null if nothing or a group's header is selected. */
	private AssetCatalog.Entry chosenEntry() {
		return typeList.getSelectedValue() instanceof TypeRow type ? type.entry() : null;
	}

	/** Lists the usable types first, each group at its first member's place, and a group's types under it if it's open. */
	private void rebuildRows() {
		List<Row> rows = new ArrayList<>();
		List<String> placed = new ArrayList<>();
		for (AssetCatalog.Entry entry : ordered) {
			String group = entry.type().group();
			if (group == null) {
				rows.add(new TypeRow(entry, false));
			} else if (!placed.contains(group)) {
				placed.add(group);
				List<AssetCatalog.Entry> members = ordered.stream().filter(other -> group.equals(other.type().group())).toList();
				boolean open = openGroups.contains(group);
				rows.add(new GroupRow(group, members.size(), open));
				if (open) {
					members.forEach(member -> rows.add(new TypeRow(member, true)));
				}
			}
		}
		listModel.clear();
		rows.forEach(listModel::addElement);
	}

	/** Opens or closes a group. Closing it with one of its types selected moves the selection to its header. */
	private void setGroupOpen(String name, boolean open) {
		AssetCatalog.Entry kept = chosenEntry();
		boolean wasHeader = typeList.getSelectedValue() instanceof GroupRow;
		if (open) {
			openGroups.add(name);
		} else {
			openGroups.remove(name);
		}
		rebuilding = true;
		try {
			rebuildRows();
			int index = -1;
			for (int i = 0; i < listModel.size() && index < 0; i++) {
				Row row = listModel.get(i);
				boolean isKept = kept != null && row instanceof TypeRow type && type.entry() == kept;
				boolean isHeader = row instanceof GroupRow group && group.name().equals(name) && (wasHeader || kept != null && name.equals(kept.type().group()) && !open);
				if (isKept || isHeader) {
					index = i;
				}
			}
			if (index >= 0) {
				typeList.setSelectedIndex(index);
				typeList.ensureIndexIsVisible(index);
			} else {
				typeList.clearSelection();
			}
		} finally {
			rebuilding = false;
		}
		refresh();
	}

	/** Reads the types from the user's jar, and what can be referred to. */
	public void open(ModEntry entry) {
		loaded = null;
		ordered = List.of();
		openGroups.clear();
		listModel.clear();
		typeHint.setVisible(false);
		nameField.setText("");
		title.setText("Add New Asset to " + entry.displayName());
		Theme.tone(message, Theme.Tone.GHOST);
		message.setText("Reading the game...");
		busy = true;
		refresh();
		Path game = gameFolder.get();
		new SwingWorker<Loaded, Void>() {
			@Override
			protected Loaded doInBackground() throws GameParser.UnavailableException, IOException {
				try (Workspace.Context context = Workspace.open(game, handbookStore, entry)) {
					return new Loaded(context.entries(), context.index(), context.version(), context.versionWarning(), context.handbook(), context.producers());
				}
			}

			@Override
			protected void done() {
				busy = false;
				Theme.tone(message, Theme.Tone.ERROR);
				try {
					loaded = get();
					// The ones that can be used come first, each group staying in the catalog's order.
					List<AssetCatalog.Entry> sorted = new ArrayList<>(loaded.entries().stream().filter(AssetCatalog.Entry::available).toList());
					sorted.addAll(loaded.entries().stream().filter(chosen -> !chosen.available()).toList());
					ordered = List.copyOf(sorted);
					rebuildRows();
					message.setText(" ");
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					message.setText("The game's files couldn't be read: " + e.getCause().getMessage());
				}
				refresh();
			}
		}.execute();
	}

	public boolean isBusy() {
		return busy;
	}

	public boolean isNextEnabled() {
		return next.isEnabled();
	}

	public String messageText() {
		return message.getText();
	}

	public String nameProblemText() {
		return nameProblem.getText();
	}

	/** Why the last type clicked can't be used, if it can't. */
	public String typeHintText() {
		return typeHint.isVisible() ? typeHint.getText() : null;
	}

	public String destinationText() {
		return destination.getText();
	}

	/** The types offered, enabled or not, whether or not their group is open. */
	public List<AssetCatalog.Entry> entries() {
		return ordered;
	}

	public String selectedType() {
		AssetCatalog.Entry chosen = chosenEntry();
		return chosen == null ? null : chosen.type().label();
	}

	/** The lines the list is showing now, with a group's header as its name. */
	public List<String> visibleRows() {
		List<String> rows = new ArrayList<>();
		for (Row row : java.util.Collections.list(listModel.elements())) {
			rows.add(row instanceof GroupRow group ? group.name() : ((TypeRow) row).entry().type().label());
		}
		return rows;
	}

	public boolean isGroupOpen(String name) {
		return openGroups.contains(name);
	}

	/** Opens or closes a group, as clicking its row does. */
	public void toggleGroupForTest(String name) {
		setGroupOpen(name, !isGroupOpen(name));
	}

	/** Selects a group's header, as clicking or arrowing onto it does. */
	public void selectGroup(String name) {
		for (int i = 0; i < listModel.size(); i++) {
			if (listModel.get(i) instanceof GroupRow group && group.name().equals(name)) {
				typeList.setSelectedIndex(i);
			}
		}
	}

	/** Presses a key on the list, as a keyboard user does. */
	public void pressKeyOnList(int keyCode) {
		java.awt.event.KeyEvent event = new java.awt.event.KeyEvent(typeList, java.awt.event.KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, keyCode, java.awt.event.KeyEvent.CHAR_UNDEFINED);
		for (java.awt.event.KeyListener listener : typeList.getKeyListeners()) {
			listener.keyPressed(event);
		}
	}

	public boolean isAppearanceVisible() {
		return appearance.isVisible();
	}

	public List<String> appearanceItems() {
		List<String> items = new ArrayList<>();
		for (int i = 0; i < appearance.getItemCount(); i++) {
			items.add(appearance.getItemAt(i));
		}
		return items;
	}

	public void selectAppearance(String name) {
		appearance.setSelectedItem(name);
	}

	/** Selects a type by its label, opening its group first if it's in one. */
	public void selectType(String label) {
		for (AssetCatalog.Entry entry : ordered) {
			if (entry.type().label().equals(label) && entry.type().group() != null && !openGroups.contains(entry.type().group())) {
				setGroupOpen(entry.type().group(), true);
			}
		}
		for (int i = 0; i < listModel.size(); i++) {
			if (listModel.get(i) instanceof TypeRow type && type.entry().type().label().equals(label)) {
				typeList.setSelectedIndex(i);
			}
		}
	}

	public void setAssetName(String name) {
		nameField.setText(name);
	}

	public void next() {
		AssetCatalog.Entry chosen = chosenEntry();
		if (next.isEnabled() && chosen != null) {
			onNext.accept(new Choice(chosen, nameField.getText(), loaded.index(), loaded.version(), loaded.handbook(), loaded.producers(),
					appearance.isVisible() ? (String) appearance.getSelectedItem() : null));
		}
	}

	private void refresh() {
		if (refreshing) {
			return;
		}
		refreshing = true;
		try {
			refreshNow();
		} finally {
			refreshing = false;
		}
	}

	private void refreshNow() {
		AssetCatalog.Entry chosen = chosenEntry();
		// An override's name is the game's file's, so it's filled in and can't be changed.
		boolean fixed = chosen != null && chosen.type().isOverride();
		if (fixed && !nameField.getText().equals(chosen.type().fixedName())) {
			nameField.setText(chosen.type().fixedName());
		} else if (!fixed && !nameField.isEditable()) {
			nameField.setText("");
		}
		nameField.setEditable(!fixed);
		boolean needsArt = chosen != null && chosen.available() && chosen.schema() != null && chosen.schema().appearanceFolder() != null && loaded != null;
		appearanceHeading.setVisible(needsArt);
		appearance.setVisible(needsArt);
		if (needsArt) {
			java.util.List<String> arts = AssetForm.appearances(chosen.schema(), loaded.index());
			boolean same = appearance.getItemCount() == arts.size();
			for (int i = 0; same && i < arts.size(); i++) {
				same = arts.get(i).equals(appearance.getItemAt(i));
			}
			if (!same) {
				String kept = (String) appearance.getSelectedItem();
				appearance.removeAllItems();
				arts.forEach(appearance::addItem);
				if (kept != null && arts.contains(kept)) {
					appearance.setSelectedItem(kept);
				}
			}
		}
		String problem = null;
		if (chosen != null && loaded != null && !nameField.getText().isEmpty()) {
			problem = AssetCreator.problemWithName(chosen.type(), chosen.schema(), nameField.getText(), loaded.index());
		}
		if (problem == null && needsArt && appearance.getSelectedItem() == null) {
			problem = "Choose an appearance.";
		}
		nameProblem.setText(problem == null ? " " : problem);
		nameProblem.setVisible(problem != null);
		boolean ready = chosen != null && loaded != null && !nameField.getText().isEmpty() && problem == null;
		destination.setText(ready ? "Will be created at: " + AssetCreator.destination(chosen.type(), nameField.getText(), loaded.version()) : " ");
		String warning = loaded == null ? null : loaded.versionWarning();
		versionWarning.setText(warning == null ? " " : warning);
		versionWarning.setVisible(warning != null);
		destination.setVisible(ready);
		next.setEnabled(ready && !busy);
	}
}
