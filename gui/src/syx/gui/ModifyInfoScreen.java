package syx.gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import syx.core.GameInstall;
import syx.core.GameParser;
import syx.core.ModEntry;
import syx.core.ModInfoField;
import syx.core.ModInfoFile;

/** Edits a mod's _Info.txt. All the rules and the saving are in {@link ModInfoFile}. */
public final class ModifyInfoScreen extends JPanel {

	private static final long serialVersionUID = 1L;

	/** One field's editor. */
	private final class Row {
		final ModInfoField field;
		final JComponent editor;
		FormRow form;

		Row(ModInfoField field, String value) {
			this.field = field;
			this.editor = createEditor(field, value);
		}

		String value() {
			if (editor instanceof JComboBox<?> choice) {
				return String.valueOf(choice.getSelectedItem());
			}
			JTextComponent text = editor instanceof JScrollPane pane
					? (JTextComponent) pane.getViewport().getView()
					: (JTextComponent) editor;
			return text.getText();
		}

		void setValue(String value) {
			if (editor instanceof JComboBox<?> choice) {
				choice.setSelectedItem(value);
			} else {
				(editor instanceof JScrollPane pane ? (JTextComponent) pane.getViewport().getView() : (JTextComponent) editor).setText(value);
			}
		}
	}

	private final transient Supplier<Path> gameFolder;
	private final transient Runnable onDone;
	private final JPanel fieldsPanel = new JPanel();
	private final JPanel addButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
	private final JLabel addTitle = new JLabel("Add a field");
	private final JLabel title = new JLabel(" ");
	private final WrappedText message = new WrappedText(" ", Theme.Tone.ERROR);
	private final DetailsPanel details = new DetailsPanel("Click a field to see what it's for. Hover over one for a quick summary.");
	private final JButton saveButton = new JButton("Save");
	private final JScrollPane scroller;

	private final transient Map<ModInfoField, Row> rows = new LinkedHashMap<>();
	private final transient Map<String, FormRow> unknownRows = new LinkedHashMap<>();
	private final transient Map<String, FormRow> formRows = new LinkedHashMap<>();
	private transient String selectedKey;
	private transient WidthBand band = WidthBand.WIDE;
	private final transient Set<String> removals = new LinkedHashSet<>();
	private final transient Map<String, FormRow> ghosts = new LinkedHashMap<>();
	private final transient Map<String, JTextField> ghostValues = new LinkedHashMap<>();
	private final transient Map<String, String[]> detailTexts = new LinkedHashMap<>();
	private transient ModEntry mod;
	private transient ModInfoFile file;
	private transient GameParser.GameVersion version;
	private boolean busy;
	private transient java.util.function.Predicate<String> discardPrompt = question -> JOptionPane.showConfirmDialog(this, question, "Unsaved changes", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;

	public ModifyInfoScreen(Supplier<Path> gameFolder, Runnable onDone) {
		super(new BorderLayout());
		this.gameFolder = gameFolder;
		this.onDone = onDone;

		Theme.style(title, "h2");
		Theme.style(addTitle, "semibold");
		fieldsPanel.setLayout(new BoxLayout(fieldsPanel, BoxLayout.Y_AXIS));
		fieldsPanel.setOpaque(false);
		fieldsPanel.setAlignmentX(LEFT_ALIGNMENT);
		addButtons.setOpaque(false);

		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		Theme.style(card, "card");
		card.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));
		card.add(fieldsPanel);

		JPanel stack = new JPanel();
		stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
		for (JComponent item : List.of(title, card, addTitle, addButtons)) {
			item.setAlignmentX(LEFT_ALIGNMENT);
			stack.add(item);
			stack.add(Box.createVerticalStrut(12));
		}
		ScrollablePanel content = new ScrollablePanel(new BorderLayout(), true);
		content.setBorder(BorderFactory.createEmptyBorder(22, 24, 12, 20));
		content.add(stack, BorderLayout.NORTH);
		scroller = new JScrollPane(content);
		scroller.setBorder(BorderFactory.createEmptyBorder());
		scroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroller.getVerticalScrollBar().setUnitIncrement(16);

		Theme.style(saveButton, "primary");
		saveButton.addActionListener(e -> save());
		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(e -> cancel());
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.add(cancel);
		buttons.add(saveButton);
		JPanel footer = new JPanel(new BorderLayout(12, 0));
		footer.setBorder(BorderFactory.createEmptyBorder(10, 24, 14, 20));
		footer.add(message, BorderLayout.CENTER);
		footer.add(buttons, BorderLayout.EAST);

		JPanel form = new JPanel(new BorderLayout());
		form.add(scroller, BorderLayout.CENTER);
		form.add(footer, BorderLayout.SOUTH);
		add(form, BorderLayout.CENTER);
		details.dockIn(this);
	}

	/** Lays the screen out for the room the window has. */
	void setBand(WidthBand value) {
		band = value;
		if (band == WidthBand.WIDE) {
			details.dockIn(this);
		} else {
			details.undockFrom(this);
		}
		formRows.values().forEach(row -> row.setBand(band));
		revalidate();
		repaint();
	}

	/** Loads the mod's _Info.txt from disk. */
	public void open(ModEntry entry) {
		mod = entry;
		file = null;
		rows.clear();
		unknownRows.clear();
		removals.clear();
		ghosts.clear();
		ghostValues.clear();
		formRows.clear();
		selectedKey = null;
		details.showHint();
		fieldsPanel.removeAll();
		addButtons.removeAll();
		addTitle.setVisible(false);
		title.setText("Modify Info: " + entry.displayName());
		message.setText("Loading...");
		saveButton.setEnabled(false);
		busy = true;
		Path game = gameFolder.get();
		new SwingWorker<Object[], Void>() {
			@Override
			protected Object[] doInBackground() throws Exception {
				try (GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
					return new Object[] { ModInfoFile.load(entry.root(), parser), parser.version() };
				}
			}

			@Override
			protected void done() {
				busy = false;
				try {
					Object[] loaded = get();
					show((ModInfoFile) loaded[0], (GameParser.GameVersion) loaded[1]);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					// Editing isn't offered if the file can't be safely read.
					message.setText(e.getCause().getMessage());
				}
			}
		}.execute();
	}

	public boolean isBusy() {
		return busy;
	}

	public boolean isSaveEnabled() {
		return saveButton.isEnabled();
	}

	public String messageText() {
		return message.getText();
	}

	public List<String> fieldKeys() {
		return rows.keySet().stream().map(ModInfoField::name).toList();
	}

	public List<String> addableKeys() {
		List<String> keys = new ArrayList<>();
		for (java.awt.Component c : addButtons.getComponents()) {
			keys.add(((JButton) c).getText());
		}
		return keys;
	}

	public String errorFor(ModInfoField field) {
		return rows.get(field).form.problemText();
	}

	public void setValue(ModInfoField field, String value) {
		rows.get(field).setValue(value);
	}

	public void addField(ModInfoField field) {
		int place = takeGhost(field.name());
		// A field removed before saving comes back with the value it had.
		boolean wasInFile = removals.remove(field.name());
		String value = wasInFile && file.existingValue(field) != null
				? file.existingValue(field)
				: field.defaultValue(mod.folderName(), version);
		addRow(new Row(field, value), place);
		fieldsChanged();
		select(field.name());
	}

	/** Brings a removed tag back, where it was and with the value it had. */
	public void reAdd(String key) {
		ModInfoField field = ModInfoField.find(key);
		if (field != null) {
			addField(field);
			return;
		}
		file.fields().stream().filter(f -> f.key().equals(key)).findFirst().ifPresent(unknown -> {
			int place = takeGhost(key);
			removals.remove(key);
			addUnknown(unknown, place);
			fieldsChanged();
			select(key);
		});
	}

	/** Greys out a field in place. The file only changes when Save is pressed. */
	public void removeField(String key) {
		ModInfoField field = ModInfoField.find(key);
		if (field != null && !field.removable()) {
			return;
		}
		Row row = field == null ? null : rows.remove(field);
		FormRow holder = row != null ? row.form : unknownRows.remove(key);
		if (holder == null) {
			return;
		}
		if (file.fields().stream().anyMatch(f -> f.key().equals(key))) {
			removals.add(key);
		}
		int place = fieldsPanel.getComponentZOrder(holder);
		fieldsPanel.remove(holder);
		formRows.remove(key);
		// A field with no default has nothing to show, so it goes to the add list.
		if (field == null || field.gameDefault() != null) {
			addGhost(key, field, place);
		}
		fieldsChanged();
		if (formRows.containsKey(key)) {
			select(key);
		} else {
			selectedKey = null;
			formRows.values().forEach(other -> other.setSelectedRow(false));
			details.showHint();
		}
	}

	/** The tags currently greyed out as removed. */
	public List<String> ghostKeys() {
		return List.copyOf(ghosts.keySet());
	}

	/** What a greyed-out field shows. */
	public String ghostValue(String key) {
		return ghostValues.get(key).getText();
	}

	public boolean isGhostEditable(String key) {
		return ghostValues.get(key).isEnabled();
	}

	/** Removes a tag's grey row, returning where it was, or -1 for the end if it had none. */
	private int takeGhost(String key) {
		FormRow ghost = ghosts.remove(key);
		ghostValues.remove(key);
		if (ghost == null) {
			return -1;
		}
		formRows.remove(key);
		int place = fieldsPanel.getComponentZOrder(ghost);
		fieldsPanel.remove(ghost);
		return place;
	}

	private void addGhost(String key, ModInfoField field, int place) {
		String shown;
		String note;
		if (field == null) {
			shown = file.fields().stream().filter(f -> f.key().equals(key)).findFirst().map(ModInfoFile.Field::value).orElse("");
			note = "Removed. It will be gone from the file when you save.";
		} else {
			shown = field.gameDefault();
			note = removals.contains(key)
					? "Removed. The game will use its default, shown here."
					: "Not in the file. The game uses its default, shown here.";
		}
		JTextField value = new JTextField(shown);
		value.setEnabled(false);
		value.setCaretPosition(0);
		FormRow form = new FormRow(key, value, true);
		if (removals.contains(key)) {
			form.addChip("Removed", Theme.Tone.WARNING, note);
		}
		form.setAction("Add", () -> reAdd(key));
		String help = field == null ? null : field.help();
		form.setTip(help == null ? note : help + "\n\n" + note);
		register(form, key, help, note);
		ghosts.put(key, form);
		ghostValues.put(key, value);
		fieldsPanel.add(form, place);
	}

	private void register(FormRow form, String key, String help, String note) {
		formRows.put(key, form);
		form.setOnSelect(() -> select(key));
		form.setOnInfo(details::showPopover);
		form.setBand(band);
		detailTexts.put(key, new String[] { help, note });
	}

	private void select(String key) {
		selectedKey = key;
		FormRow form = formRows.get(key);
		formRows.values().forEach(row -> row.setSelectedRow(row == form));
		String[] texts = detailTexts.get(key);
		details.show(new DetailsPanel.Content(key, List.of(), texts == null ? null : texts[0], null, null, null, texts == null ? null : texts[1], null));
	}

	/** Selects a field, as clicking its row would. */
	public void selectField(String key) {
		select(key);
	}

	/** The rows marked as selected, which is never more than one. */
	public List<String> highlightedRows() {
		return formRows.keySet().stream().filter(key -> formRows.get(key).isSelectedRow()).toList();
	}

	public String detailsName() {
		return details.shownName();
	}

	public List<String> detailsText() {
		return details.shownText();
	}

	/** What hovering over a field's row shows. */
	public String tipFor(String key) {
		return formRows.get(key).tip();
	}

	/** The tags that have a remove button. */
	public List<String> removableKeys() {
		List<String> keys = new ArrayList<>();
		for (ModInfoField field : rows.keySet()) {
			if (field.removable()) {
				keys.add(field.name());
			}
		}
		keys.addAll(unknownRows.keySet());
		return keys;
	}

	private void fieldsChanged() {
		rebuildAddButtons();
		revalidateRows();
		revalidate();
		repaint();
	}

	public void save() {
		if (!saveButton.isEnabled()) {
			return;
		}
		Map<ModInfoField, String> values = values();
		Set<String> toRemove = new LinkedHashSet<>(removals);
		ModInfoFile target = file;
		Path game = gameFolder.get();
		busy = true;
		saveButton.setEnabled(false);
		message.setText("Saving...");
		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				try (GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
					target.save(values, toRemove, parser);
				}
				return null;
			}

			@Override
			protected void done() {
				busy = false;
				try {
					get();
					message.setText(" ");
					onDone.run();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					message.setText(e.getCause().getMessage());
					revalidateRows();
				}
			}
		}.execute();
	}

	private void cancel() {
		if (confirmLeave()) {
			onDone.run();
		}
	}

	/** Whether it's fine to leave this screen, asking first if something would be lost. */
	public boolean confirmLeave() {
		if (busy) {
			return false;
		}
		return file == null || !file.hasChanges(values(), removals) || discardPrompt.test("Discard your changes to this mod's info?");
	}

	/** Replaces the question about losing work, so tests can answer it. */
	public void setDiscardPrompt(java.util.function.Predicate<String> prompt) {
		discardPrompt = prompt;
	}

	private Map<ModInfoField, String> values() {
		Map<ModInfoField, String> values = new LinkedHashMap<>();
		rows.forEach((field, row) -> values.put(field, row.value()));
		return values;
	}

	private void show(ModInfoFile loaded, GameParser.GameVersion gameVersion) {
		file = loaded;
		version = gameVersion;
		message.setText(" ");
		for (ModInfoFile.Field field : loaded.fields()) {
			if (field.known() == null) {
				addUnknown(field, -1);
			} else {
				addRow(new Row(field.known(), field.value()), -1);
			}
		}
		for (ModInfoField missing : loaded.missingFields()) {
			if (missing.gameDefault() != null) {
				addGhost(missing.name(), missing, -1);
			}
		}
		rebuildAddButtons();
		revalidateRows();
		scroller.getVerticalScrollBar().setValue(0);
		revalidate();
		repaint();
	}

	private void addUnknown(ModInfoFile.Field field, int place) {
		JTextField value = new JTextField(field.value());
		value.setEditable(false);
		value.setCaretPosition(0);
		FormRow form = new FormRow(field.key(), value, false);
		form.addChip("Not used by the game", Theme.Tone.HELP, null);
		form.setAction("Remove", () -> removeField(field.key()));
		String note = "The game doesn't use this, so it's kept as it is.";
		form.setTip(note);
		register(form, field.key(), note, null);
		unknownRows.put(field.key(), form);
		fieldsPanel.add(form, place);
	}

	private void addRow(Row row, int place) {
		rows.put(row.field, row);
		row.form = new FormRow(row.field.name(), row.editor, false);
		if (row.field.removable()) {
			row.form.setAction("Remove", () -> removeField(row.field.name()));
		}
		row.form.setTip(row.field.help());
		String defaultValue = row.field.gameDefault();
		register(row.form, row.field.name(), row.field.help(), defaultValue == null ? null : "The game uses its default: " + defaultValue + ".");
		fieldsPanel.add(row.form, place);
	}

	private JComponent createEditor(ModInfoField field, String value) {
		Runnable changed = this::revalidateRows;
		switch (field.kind()) {
		case CHOICE -> {
			JComboBox<String> choice = new JComboBox<>(ModInfoField.TEXTURE_CACHE_SIZES.toArray(new String[0]));
			if (!ModInfoField.TEXTURE_CACHE_SIZES.contains(value)) {
				// Shows what's in the file, so the error beside it makes sense.
				choice.addItem(value);
			}
			choice.setSelectedItem(value);
			choice.addActionListener(e -> changed.run());
			return choice;
		}
		case LINES -> {
			JTextArea area = new JTextArea(value, 3, 40);
			area.setLineWrap(true);
			area.setWrapStyleWord(true);
			area.setFont(new JTextField().getFont());
			listen(area, changed);
			return new JScrollPane(area);
		}
		default -> {
			JTextField text = new JTextField(value);
			listen(text, changed);
			return text;
		}
		}
	}

	private static void listen(JTextComponent component, Runnable changed) {
		component.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				changed.run();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				changed.run();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				changed.run();
			}
		});
	}

	private void rebuildAddButtons() {
		addButtons.removeAll();
		for (ModInfoField field : ModInfoField.values()) {
			boolean absent = file.missingFields().contains(field) || removals.contains(field.name());
			if (absent && !rows.containsKey(field) && !ghosts.containsKey(field.name())) {
				JButton add = new JButton(field.name());
				add.setToolTipText(field.help());
				add.addActionListener(e -> addField(field));
				addButtons.add(add);
			}
		}
		addTitle.setVisible(addButtons.getComponentCount() > 0);
	}

	/** Checks every field, and allows Save only when something changed and everything's valid. */
	private void revalidateRows() {
		if (file == null) {
			return;
		}
		boolean valid = true;
		for (Row row : rows.values()) {
			String problem = file.problemWith(row.field, row.value());
			row.form.setProblem(problem, Theme.Tone.ERROR);
			valid &= problem == null;
		}
		saveButton.setEnabled(valid && !busy && file.hasChanges(values(), removals));
	}
}
