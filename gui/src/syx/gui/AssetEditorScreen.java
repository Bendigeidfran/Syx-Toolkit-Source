package syx.gui;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
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
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;

import com.formdev.flatlaf.FlatClientProperties;

import syx.core.AssetChecker;
import syx.core.AssetCreator;
import syx.core.AssetEditing;
import syx.core.AssetForm;
import syx.core.AssetSchema;
import syx.core.AssetType;
import syx.core.GameAssetIndex;
import syx.core.GameInstall;
import syx.core.GameParser;
import syx.core.HandbookIndex;
import syx.core.HandbookStore;
import syx.core.ModEntry;
import syx.core.SoundEntries;
import syx.core.SpriteCopy;
import syx.core.TagSpec;

/**
 * Fills in a new asset's tags, laid out like the info editor. Tags come from the schema, and all the
 * rules and the saving are in the core. Nothing is written until Save.
 */
public final class AssetEditorScreen extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final String NO_EFFECT = "No effect in the game";
	private static final String NO_HANDBOOK = AssetForm.NO_HANDBOOK;
	private static final String NO_HANDBOOK_NOTE = "Tag descriptions come from the online handbook, and no copy for your game version has been downloaded yet. The toolkit checks for one each time it starts.";
	private static final String NOT_READ = "Not read by the game";
	private static final String UNVERIFIED = "Unconfirmed";
	private static final String UNVERIFIED_TIP = "Not confirmed by the handbook yet";
	private static final String DEFAULT_NOTE = "It isn't in the file, so the game uses its default:";
	private static final String IN_TEXT = " (text file)";
	private static final String CREDIT = "Tag descriptions are from the Songs of Syx Modding Handbook (CC BY-SA 4.0).";

	/** Shows a handbook page to the user. */
	public interface LinkOpener {
		void open(String url) throws IOException;
	}

	/** One tag's editor. It puts its values in the form described on {@link AssetSchema}. */
	private interface Editor {
		JComponent component();

		void collect(Map<String, String> values);

		void set(String key, String value);
	}

	private final class Row {
		final TagSpec tag;
		final Editor editor;
		FormRow form;

		Row(TagSpec tag) {
			this.tag = tag;
			this.editor = createEditor(tag);
		}
	}

	private final transient Supplier<Path> gameFolder;
	private final transient Runnable onDone;
	private final JLabel title = new JLabel(" ");
	/** Picks a vanilla asset of this type, whose values Initialise puts on the form. */
	private final JComboBox<String> vanillaChoice = new JComboBox<>();
	private final JButton initialiseButton = new JButton("Initialise");
	private final JButton copySprites = new JButton("Copy Vanilla Sprites");
	private final JPanel vanillaRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
	private final JPanel notes = new JPanel();
	private final JPanel requiredPanel = new JPanel();
	private final JPanel optionalPanel = new JPanel();
	private final JPanel textPanel = new JPanel();
	private final JPanel soundPanel = new JPanel();
	private final Box filesStack = Box.createVerticalBox();
	private final transient Map<String, JPanel> filePanels = new LinkedHashMap<>();
	private final transient Map<String, JPanel> fileCards = new LinkedHashMap<>();
	private final transient Map<String, JButton> fileOffers = new LinkedHashMap<>();
	private final JPanel optionalCard;
	private final JPanel textCard;
	private final JPanel soundCard;
	private final DetailsPanel details = new DetailsPanel("Click a tag to see what it does, its rules and an example. Hover over one for a quick summary.");
	private final JButton showProblem = new JButton("Show first");
	private final JPanel addButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
	private final JLabel addTitle = new JLabel("Add a tag");
	private final WrappedText message = new WrappedText(" ", Theme.Tone.HELP);
	private final JButton saveButton = new JButton("Save");
	private final JScrollPane scroller;

	private final transient Map<String, Row> rows = new LinkedHashMap<>();
	private final transient Map<String, FormRow> ghosts = new LinkedHashMap<>();
	private final transient Map<String, FormRow> formRows = new LinkedHashMap<>();
	private final transient List<String> attention = new ArrayList<>();
	private final transient List<String> shownNotes = new ArrayList<>();
	private transient String selectedKey;
	private transient String producerWarning;
	private transient WidthBand band = WidthBand.WIDE;
	private final transient Map<String, JTextField> ghostValues = new LinkedHashMap<>();
	private final transient Map<String, JTextField> nestedGhosts = new LinkedHashMap<>();
	private final transient Map<String, KeyedRowsEditor> keyedEditors = new LinkedHashMap<>();
	/** Opens a folded list, by its key. Each says whether it had to. */
	private final transient Map<String, java.util.function.BooleanSupplier> collapsed = new LinkedHashMap<>();
	private final transient Map<String, JComponent> foldedContent = new LinkedHashMap<>();
	private final transient Map<String, JPanel> sectionBodies = new LinkedHashMap<>();
	private final transient Map<String, Runnable> sectionToggles = new LinkedHashMap<>();
	private static final Set<String> STANDING_SECTIONS = Set.of("Required tags", "Optional tags", "Text file", "Sounds");
	private transient Map<String, String> startingValues = Map.of();
	/** The vanilla look a new asset started from, which its new files are copied from, or null. */
	private transient String appearance;
	private final transient Map<String, JComboBox<String>> soundEditors = new LinkedHashMap<>();
	private final transient Map<String, FormRow> keptRows = new LinkedHashMap<>();
	private transient AssetEditing.Opened opened;
	private transient AddAssetScreen.Choice choice;
	/** What the last Initialise put on a new asset's form, which setUp starts from, or null. */
	private transient AssetForm.VanillaStart vanillaStart;
	/** The core's note on what the last Initialise couldn't copy, or null. */
	private transient String initialisedNote;
	private transient Map<String, String> baseline;
	private transient java.util.Set<String> keptKeys = java.util.Set.of();
	/** The kept values as the game reads them, from the core, so the form shows what stays. */
	private transient Map<String, String> keptWritten = Map.of();
	private transient boolean textActive = true;
	private transient boolean filling;
	private JButton generateText;
	private JButton generateSounds;
	private transient ModEntry mod;
	private transient AssetType type;
	private transient AssetSchema schema;
	private transient GameAssetIndex index;
	private transient String assetName;
	private transient HandbookStore.Loaded handbook = HandbookStore.Loaded.NONE;
	private transient GameParser.GameVersion gameVersion;
	private transient List<String> locations = List.of();
	private final transient LinkOpener linkOpener;
	private final transient Map<String, String> descriptions = new LinkedHashMap<>();
	private final transient Map<String, String> examples = new LinkedHashMap<>();
	private final transient java.util.Set<String> unverifiedKeys = new java.util.HashSet<>();
	private final transient Map<String, String> effects = new LinkedHashMap<>();
	private final transient Map<String, String> links = new LinkedHashMap<>();
	private boolean busy;
	private transient Predicate<String> discardPrompt = question -> JOptionPane.showConfirmDialog(this, question, "Unsaved asset", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;

	public AssetEditorScreen(Supplier<Path> gameFolder, LinkOpener linkOpener, Runnable onDone) {
		super(new BorderLayout());
		this.gameFolder = gameFolder;
		this.linkOpener = linkOpener;
		this.onDone = onDone;
		Theme.style(title, "h2");
		Theme.style(addTitle, "semibold");
		for (JPanel panel : List.of(notes, requiredPanel, optionalPanel, textPanel, soundPanel)) {
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			panel.setOpaque(false);
			panel.setAlignmentX(LEFT_ALIGNMENT);
		}
		optionalCard = sectionCard("Optional tags", optionalPanel);
		addButtons.setOpaque(false);

		JPanel stack = new JPanel();
		stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
		textCard = sectionCard("Text file", textPanel);
		soundCard = sectionCard("Sounds", soundPanel);
		filesStack.setAlignmentX(LEFT_ALIGNMENT);
		vanillaRow.setOpaque(false);
		JLabel vanillaLabel = new JLabel("Vanilla values from");
		vanillaRow.add(vanillaLabel);
		vanillaRow.add(vanillaChoice);
		vanillaRow.add(initialiseButton);
		vanillaRow.add(copySprites);
		copySprites.setVisible(false);
		vanillaChoice.setToolTipText(PlainTip.of("One of the game's own assets of this type, read from your install."));
		initialiseButton.setToolTipText(PlainTip.of("Fills the form with the chosen vanilla asset's values, including its text and sounds. Nothing is saved until you press Save."));
		copySprites.setToolTipText(PlainTip.of("Copies this vanilla asset's own sprite sheets into the mod, renamed for this asset. The images are written now. The form's sprite fields change to the new names, and saving writes those names. Cancelling leaves the images in place."));
		initialiseButton.addActionListener(e -> initialiseFromVanilla());
		copySprites.addActionListener(e -> copyVanillaSprites());
		for (JComponent item : List.of(title, vanillaRow, notes, sectionCard("Required tags", requiredPanel), optionalCard, textCard, filesStack, soundCard, addTitle, addButtons)) {
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
		showProblem.setToolTipText("Go to the first tag that needs attention");
		showProblem.addActionListener(e -> goToFirstProblem());
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.add(showProblem);
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

	/** A titled, rounded group of rows. Clicking the heading folds and unfolds it, and it starts open. */
	private JPanel sectionCard(String heading, JPanel rows) {
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		Theme.style(card, "card");
		card.setBorder(BorderFactory.createEmptyBorder(12, 8, 10, 8));
		boolean[] open = { true };
		JLabel label = new JLabel(sectionMarker(true) + heading);
		Theme.style(label, "h4");
		Theme.tone(label, Theme.Tone.HEADING);
		label.setBorder(BorderFactory.createEmptyBorder(0, 10, 6, 0));
		label.setAlignmentX(LEFT_ALIGNMENT);
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		Runnable toggle = () -> {
			open[0] = !open[0];
			rows.setVisible(open[0]);
			label.setText(sectionMarker(open[0]) + heading);
			revalidate();
			repaint();
		};
		label.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				toggle.run();
			}
		});
		sectionBodies.put(heading, rows);
		sectionToggles.put(heading, toggle);
		rows.setAlignmentX(LEFT_ALIGNMENT);
		card.add(label);
		card.add(rows);
		return card;
	}

	private static String sectionMarker(boolean open) {
		return open ? "\u25BE " : "\u25B8 ";
	}

	public void open(ModEntry entry, AddAssetScreen.Choice choice) {
		vanillaStart = null;
		initialisedNote = null;
		openNew(entry, choice);
	}

	/** A new asset's form, from its starting values, or from {@link #vanillaStart} after Initialise. */
	private void openNew(ModEntry entry, AddAssetScreen.Choice choice) {
		opened = null;
		baseline = null;
		keptKeys = java.util.Set.of();
		keptWritten = Map.of();
		textActive = true;
		filling = true;
		try {
			setUp(entry, choice);
			// A sound's starting value is already selected on its row.
			startingValues.forEach((key, value) -> {
				if (soundEditors.containsKey(key)) {
					return;
				}
				String id = rowIdFor(key);
				// Optional tags stay off the form until they have a value. A chosen appearance already has them.
				if (!rows.containsKey(id)) {
					addTag(id);
				}
				rows.get(id).editor.set(key, value);
			});
		} finally {
			filling = false;
		}
		revalidateRows();
	}

	/** Opens an asset that already exists, filled in from its files. Saving goes over those files. */
	public void openExisting(ModEntry entry, AddAssetScreen.Choice choice, AssetEditing.Opened existing) {
		vanillaStart = null;
		initialisedNote = null;
		opened = existing;
		startingValues = Map.of();
		keptKeys = new java.util.HashSet<>(existing.kept());
		keptWritten = existing.keptAsWritten();
		textActive = existing.hasTextFile();
		filling = true;
		try {
			setUp(entry, choice);
			fillFrom(existing);
		} finally {
			filling = false;
		}
		selectedKey = null;
		formRows.values().forEach(row -> row.setSelectedRow(false));
		details.showHint();
		baseline = values();
		revalidateRows();
	}

	/** Puts a file's values in the form. Anything optional gets its row first, as adding it by hand would. */
	private void fillFrom(AssetEditing.Opened existing) {
		fillValues(existing.values());
	}

	private void fillValues(Map<String, String> values) {
		values.forEach((key, value) -> {
			if (soundEditors.containsKey(key)) {
				choose(soundEditors.get(key), value);
			} else if (!key.startsWith("SOUND.")) {
				AssetSchema.Companion companion = schema.companionFor(key);
				// A companion still offered as Add isn't on the form, so its values have nowhere to go.
				if (companion != null && filePanels.get(companion.id()) != null && fileOffers.containsKey(companion.id()) && !adopted.contains(companion.id())) {
					return;
				}
				String id = rowIdFor(key);
				if (!rows.containsKey(id)) {
					addTag(id);
				}
				rows.get(id).editor.set(key, value);
			}
		});
	}

	/** Companions put on the form since it was set up. */
	private final transient java.util.Set<String> adopted = new java.util.HashSet<>();

	/** Reads the chosen vanilla asset from the player's install, then puts its values on the form. */
	private void initialiseFromVanilla() {
		String from = (String) vanillaChoice.getSelectedItem();
		if (from == null || busy || !confirmInitialise(from)) {
			return;
		}
		Path game = gameFolder.get();
		syx.core.AssetCatalog.Entry entry = choice.entry();
		GameAssetIndex reading = index;
		String name = assetName;
		boolean newAsset = opened == null;
		busy = true;
		initialiseButton.setEnabled(false);
		Theme.tone(message, Theme.Tone.HELP);
		message.setText("Reading " + from + " from the game...");
		new SwingWorker<AssetForm.VanillaStart, Void>() {
			@Override
			protected AssetForm.VanillaStart doInBackground() throws Exception {
				try (GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
					return AssetForm.vanillaStart(entry, game, reading, parser, from, name, newAsset);
				}
			}

			@Override
			protected void done() {
				busy = false;
				initialiseButton.setEnabled(true);
				try {
					applyVanilla(get());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					revalidateRows();
					Theme.tone(message, Theme.Tone.ERROR);
					message.setText("Couldn't read " + from + ": " + e.getCause().getMessage());
				}
			}
		}.execute();
	}

	private boolean confirmInitialise(String from) {
		return discardPrompt.test("Replace everything on this form with vanilla " + from + "'s values? Tags " + from
				+ " doesn't have go back to their defaults. Nothing is saved until you press Save.");
	}

	/** Rebuilds the form from a vanilla asset's values, the way opening it does, so every tag the vanilla asset has gets its row. */
	private void applyVanilla(AssetForm.VanillaStart start) {
		if (start == null) {
			Theme.tone(message, Theme.Tone.ERROR);
			message.setText("The game has no " + type.label() + " by that name.");
			return;
		}
		String chosen = start.from();
		initialisedNote = AssetForm.notCopiedNote(start);
		if (opened == null) {
			vanillaStart = start;
			openNew(mod, choice);
		} else {
			if (start.values().keySet().stream().anyMatch(schema::isTextKey)) {
				textActive = true;
			}
			filling = true;
			try {
				setUp(mod, choice);
				// The same as pressing Add or Generate, so each file the vanilla asset has comes onto the form.
				for (AssetSchema.Companion companion : schema.companions()) {
					if (fileOffers.containsKey(companion.id()) && start.values().keySet().stream().anyMatch(key -> key.startsWith(companion.prefix()))) {
						adopted.add(companion.id());
						fileOffers.get(companion.id()).doClick();
					}
				}
				if (generateSounds != null && start.values().keySet().stream().anyMatch(key -> key.startsWith("SOUND."))) {
					generateSounds.doClick();
				}
				fillValues(start.values());
			} finally {
				filling = false;
			}
			revalidateRows();
		}
		vanillaChoice.setSelectedItem(chosen);
	}

	/** Initialises from a vanilla asset at once, as clicking Initialise does, for tests. */
	public void initialiseNow(String from) throws Exception {
		vanillaChoice.setSelectedItem(from);
		if (!confirmInitialise(from)) {
			return;
		}
		try (GameParser parser = GameParser.open(GameInstall.jarIn(gameFolder.get()))) {
			applyVanilla(AssetForm.vanillaStart(choice.entry(), gameFolder.get(), index, parser, from, assetName, opened == null));
		}
	}

	/** Copies the chosen vanilla asset's own sheets into the mod, then points the form's sprite fields at them. */
	private void copyVanillaSprites() {
		String from = (String) vanillaChoice.getSelectedItem();
		if (from == null || busy) {
			return;
		}
		Path game = gameFolder.get();
		syx.core.AssetCatalog.Entry entry = choice.entry();
		GameAssetIndex reading = index;
		ModEntry writing = mod;
		GameParser.GameVersion version = gameVersion;
		String name = assetName;
		busy = true;
		copySprites.setEnabled(false);
		Theme.tone(message, Theme.Tone.HELP);
		message.setText("Copying " + from + "'s sprites...");
		new SwingWorker<SpriteCopy.Result, Void>() {
			@Override
			protected SpriteCopy.Result doInBackground() throws Exception {
				try (GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
					return SpriteCopy.copy(entry, game, writing, version, reading, parser, from, name);
				}
			}

			@Override
			protected void done() {
				busy = false;
				copySprites.setEnabled(true);
				try {
					applyCopiedSprites(get());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					Throwable cause = e.getCause();
					revalidateRows();
					boolean none = cause instanceof SpriteCopy.Rejected rejected && rejected.why() == SpriteCopy.Why.NONE;
					Theme.tone(message, none ? Theme.Tone.HELP : Theme.Tone.ERROR);
					message.setText(cause.getMessage());
				}
			}
		}.execute();
	}

	/** Points the form's sprite fields at the copies. The images are already in the mod. */
	private void applyCopiedSprites(SpriteCopy.Result result) {
		filling = true;
		try {
			Set<String> bringing = new java.util.HashSet<>();
			for (Map.Entry<String, String> value : result.values().entrySet()) {
				AssetSchema.Companion companion = schema.companionFor(value.getKey());
				if (companion != null && fileOffers.containsKey(companion.id()) && bringing.add(companion.id())) {
					adopted.add(companion.id());
					fileOffers.get(companion.id()).doClick();
				}
				String id = rowIdFor(value.getKey());
				if (!rows.containsKey(id)) {
					addTag(id);
				}
				if (rows.containsKey(id)) {
					rows.get(id).editor.set(value.getKey(), value.getValue());
				}
			}
		} finally {
			filling = false;
		}
		revalidateRows();
		Theme.tone(message, Theme.Tone.HELP);
		int count = result.files().size();
		message.setText("Copied " + count + " sprite" + (count == 1 ? "" : "s") + " into the mod. The form now names them, and saving writes those names. Cancelling leaves the images in place.");
	}

	/** Copies from a vanilla asset at once, as clicking Copy Vanilla Sprites does, for tests. */
	public void copySpritesNow(String from) throws Exception {
		vanillaChoice.setSelectedItem(from);
		try (GameParser parser = GameParser.open(GameInstall.jarIn(gameFolder.get()))) {
			applyCopiedSprites(SpriteCopy.copy(choice.entry(), gameFolder.get(), mod, gameVersion, index, parser, from, assetName));
		}
	}

	/** Whether Copy Vanilla Sprites is on the form, for tests. */
	public boolean copySpritesShown() {
		return copySprites.isVisible() && copySprites.getParent() == vanillaRow;
	}

	/** Whether the vanilla picker and Initialise are on the form, for tests. */
	public boolean vanillaPickerShown() {
		return vanillaRow.isVisible() && vanillaRow.getParent() != null && initialiseButton.getParent() == vanillaRow;
	}

	/** The look a new asset's files will be copied from, or null, for tests. */
	public String currentAppearance() {
		return appearance;
	}

	/** The vanilla assets offered, for tests. */
	public List<String> vanillaChoices() {
		List<String> names = new ArrayList<>();
		for (int i = 0; i < vanillaChoice.getItemCount(); i++) {
			names.add(vanillaChoice.getItemAt(i));
		}
		return names;
	}

	private String rowIdFor(String key) {
		AssetSchema.Companion companion = schema.companionFor(key);
		if (companion != null) {
			String rest = key.substring(companion.prefix().length());
			int nested = rest.indexOf('.');
			return companion.prefix() + (nested < 0 ? rest : rest.substring(0, nested));
		}
		int dot = key.indexOf('.');
		String tagKey = dot < 0 ? key : key.substring(0, dot);
		TagSpec tag = schema.tag(tagKey);
		String id = tag.linked() ? tag.linkedTo() : tagKey;
		return schema.isTextKey(key) && schema.dataTags().stream().anyMatch(other -> other.key().equals(tagKey)) ? tagKey + IN_TEXT : id;
	}

	/** Shows a value the list doesn't offer rather than quietly changing it, so the checker can say what's wrong. */
	private static void choose(JComboBox<String> choice, String value) {
		for (int i = 0; i < choice.getItemCount(); i++) {
			if (value.equals(choice.getItemAt(i))) {
				choice.setSelectedIndex(i);
				return;
			}
		}
		choice.addItem(value);
		choice.setSelectedItem(value);
	}

	private void setUp(ModEntry entry, AddAssetScreen.Choice choice) {
		this.choice = choice;
		adopted.clear();
		mod = entry;
		type = choice.entry().type();
		schema = choice.entry().schema();
		index = choice.index();
		assetName = choice.name();
		handbook = choice.handbook();
		producerWarning = AssetForm.producerWarning(type, assetName, choice.producers());
		gameVersion = choice.version();
		locations = type.handbookLocations();
		nestedGhosts.clear();
		collapsed.clear();
		foldedContent.clear();
		keyedEditors.clear();
		soundEditors.clear();
		descriptions.clear();
		examples.clear();
		unverifiedKeys.clear();
		effects.clear();
		links.clear();
		rows.clear();
		ghosts.clear();
		ghostValues.clear();
		formRows.clear();
		keptRows.clear();
		generateText = null;
		generateSounds = null;
		selectedKey = null;
		requiredPanel.removeAll();
		optionalPanel.removeAll();
		textPanel.removeAll();
		soundPanel.removeAll();
		appearance = opened == null ? (vanillaStart != null && vanillaStart.appearance() != null ? vanillaStart.appearance() : choice.appearance()) : null;
		if (opened == null && vanillaStart != null) {
			// Initialised from a vanilla asset: a look's files are now the ones copied, and what the form can't show in them is kept.
			startingValues = vanillaStart.values();
			keptKeys = new java.util.HashSet<>(vanillaStart.kept());
			keptWritten = vanillaStart.keptAsWritten();
		} else if (opened == null) {
			startingValues = AssetForm.startingValues(schema, index, appearance, assetName);
			// A new asset from a look is a copy of its files, so what the form can't show in them is kept, as when one is opened.
			keptKeys = new java.util.HashSet<>(AssetForm.startingKept(schema, index, appearance));
			keptWritten = AssetForm.startingKeptAsWritten(schema, index, appearance);
		}
		Object wasChosen = vanillaChoice.getSelectedItem();
		vanillaChoice.removeAllItems();
		for (String name : AssetForm.vanillaChoices(choice.entry(), index)) {
			vanillaChoice.addItem(name);
		}
		if (wasChosen != null) {
			vanillaChoice.setSelectedItem(wasChosen);
		}
		vanillaRow.setVisible(vanillaChoice.getItemCount() > 0);
		copySprites.setVisible(schema != null && SpriteCopy.hasSprites(schema) && vanillaChoice.getItemCount() > 0);
		addFileSections();
		details.showHint();
		title.setText((opened == null ? "New " : "Edit ") + type.label() + ": " + assetName);
		for (TagSpec tag : schema.dataTags()) {
			addInitial(tag, panelFor(tag));
		}
		if (textActive) {
			for (TagSpec tag : schema.textTags()) {
				addInitial(tag, panelFor(tag));
			}
		} else if (opened.needsTextFile()) {
			generateText = addGenerate(textPanel, AssetForm.TEXT_FILE_OFFER, "Generate text file", this::generateTextFile);
		}
		optionalCard.setVisible(optionalPanel.getComponentCount() > 0);
		textCard.setVisible(textPanel.getComponentCount() > 0);
		addSounds();
		soundCard.setVisible(soundPanel.getComponentCount() > 0);
		List<String> footerNotes = new ArrayList<>(schema.notes());
		footerNotes.add(handbook.index() != null ? CREDIT : NO_HANDBOOK_NOTE);
		details.setFooter(footerNotes);
		rebuildAddButtons();
		revalidateRows();
		revalidate();
		repaint();
		// Laying out the form and giving it focus can both move it, so this waits until they're done.
		SwingUtilities.invokeLater(() -> {
			validate();
			scroller.getVerticalScrollBar().setValue(0);
			SwingUtilities.invokeLater(() -> scroller.getVerticalScrollBar().setValue(0));
		});
	}

	/** A new asset gets every sound. An existing one shows where each is, and offers to add those that are missing. */
	private void addSounds() {
		if (opened == null) {
			schema.sounds().forEach(slot -> addSoundRow(slot, null));
			return;
		}
		for (SoundEntries.Slot slot : schema.sounds()) {
			SoundEntries.Info info = opened.sounds().get(slot);
			switch (info.state()) {
			case OWN_FILE -> addSoundRow(slot, info.value());
			case GAME, OTHER_MOD_FILE -> addFixedSoundRow(slot, info);
			case MISSING -> {
				// Offered together, below.
			}
			}
		}
		List<String> missing = opened.missingSounds().stream().map(slot -> slot.key(opened.fileName())).toList();
		if (!missing.isEmpty()) {
			generateSounds = addGenerate(soundPanel, AssetForm.soundsOffer(missing),
					missing.size() == 1 ? "Generate sound entry" : "Generate sound entries", this::generateSoundEntries);
		}
	}

	/** Says what's missing, with a button that adds it to the form. Nothing is written until Save. */
	private JButton addGenerate(JPanel host, String why, String label, Runnable action) {
		JPanel offer = new JPanel();
		offer.setLayout(new BoxLayout(offer, BoxLayout.Y_AXIS));
		offer.setOpaque(false);
		offer.setAlignmentX(LEFT_ALIGNMENT);
		offer.setBorder(BorderFactory.createEmptyBorder(0, 10, 6, 10));
		WrappedText note = new WrappedText(why, Theme.Tone.WARNING);
		note.setAlignmentX(LEFT_ALIGNMENT);
		JButton button = new JButton(label);
		button.setToolTipText("Adds this to the form. The file is written when you save.");
		button.setAlignmentX(LEFT_ALIGNMENT);
		button.addActionListener(e -> {
			host.remove(offer);
			WrappedText written = new WrappedText("Written when you save. Cancel leaves nothing behind.", Theme.Tone.HELP);
			written.setAlignmentX(LEFT_ALIGNMENT);
			written.setBorder(BorderFactory.createEmptyBorder(0, 10, 6, 10));
			host.add(written);
			action.run();
			tagsChanged();
		});
		offer.add(note);
		offer.add(Box.createVerticalStrut(6));
		offer.add(button);
		host.add(offer);
		return button;
	}

	private void generateTextFile() {
		generateText = null;
		textActive = true;
		for (TagSpec tag : schema.textTags()) {
			addInitial(tag, textPanel);
		}
	}

	private void generateSoundEntries() {
		generateSounds = null;
		opened.missingSounds().forEach(slot -> addSoundRow(slot, null));
	}

	/** A file's value the form can't show. It stays as written unless the user chooses to replace it. */
	private void addKeptRow(TagSpec tag, JPanel panel, int place) {
		String id = rowId(tag);
		String written = keptWritten.get(tag.key());
		JTextField value = new JTextField(written == null ? "Written in a form this editor can't show, and kept as it is" : "Kept as written: " + written);
		value.setEnabled(false);
		value.setCaretPosition(0);
		FormRow form = new FormRow(id, value, true);
		form.setAction("Replace", () -> replaceKept(tag, id, panel));
		describe(form, id, helpFor(tag.key(), panel), schema.ruleText(tag));
		keptRows.put(id, form);
		panel.add(form, place);
	}

	private void replaceKept(TagSpec tag, String id, JPanel panel) {
		FormRow form = keptRows.remove(id);
		int place = panel.getComponentZOrder(form);
		panel.remove(form);
		keptKeys.remove(tag.key());
		addRow(tag, panel, place);
		tagsChanged();
		select(id);
	}

	private void addInitial(TagSpec tag, JPanel panel) {
		switch (AssetForm.initialState(tag, keptKeys)) {
		case KEPT -> addKeptRow(tag, panel, -1);
		case REQUIRED -> addRow(tag, panel, -1);
		case GHOSTED -> addGhost(tag, panel, -1);
		case ADD_ONLY, LINKED -> {
			// Offered from the add list, or entered in another tag's row.
		}
		}
	}

	private JPanel panelFor(TagSpec tag) {
		AssetSchema.Companion companion = schema.companionFor(tag.key());
		if (companion != null) {
			return filePanels.get(companion.id());
		}
		if (!schema.dataTags().contains(tag)) {
			return textPanel;
		}
		return tag.required() ? requiredPanel : optionalPanel;
	}

	/** One card per companion file, between the text file and the sounds. */
	private void addFileSections() {
		filesStack.removeAll();
		filePanels.clear();
		fileCards.clear();
		fileOffers.clear();
		sectionBodies.keySet().removeIf(key -> !STANDING_SECTIONS.contains(key));
		sectionToggles.keySet().removeIf(key -> !STANDING_SECTIONS.contains(key));
		for (AssetSchema.Companion companion : schema.companions()) {
			JPanel panel = new JPanel();
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			panel.setOpaque(false);
			panel.setAlignmentX(LEFT_ALIGNMENT);
			filePanels.put(companion.id(), panel);
			JPanel card = sectionCard(companion.label(), panel);
			fileCards.put(companion.id(), card);
			filesStack.add(card);
			filesStack.add(Box.createVerticalStrut(12));
			AssetEditing.CompanionFile info = opened == null ? null : opened.companions().get(companion.id());
			boolean active = opened == null
					? companion.required() || startingValues.containsKey(companion.nameTag()) || startingValues.keySet().stream().anyMatch(key -> key.startsWith(companion.prefix()))
					: info != null && info.place() == AssetEditing.FilePlace.OWN;
			if (active) {
				for (TagSpec tag : companion.tags()) {
					addInitial(tag, panel);
				}
			} else if (opened != null && info != null && info.place() != AssetEditing.FilePlace.UNUSED) {
				String verb = info.place() == AssetEditing.FilePlace.GAME ? "Add" : "Generate";
				fileOffers.put(companion.id(), addGenerate(panel, AssetForm.companionOffer(info.place(), info.stem()), verb + " " + companion.label().toLowerCase(), () -> adoptFile(companion)));
			} else if (opened == null && !companion.required()) {
				fileOffers.put(companion.id(), addGenerate(panel, AssetForm.UNUSED_COMPANION_OFFER, "Add " + companion.label().toLowerCase(), () -> adoptFile(companion)));
			}
			card.setVisible(panel.getComponentCount() > 0);
		}
		filesStack.setVisible(fileCards.values().stream().anyMatch(java.awt.Component::isVisible));
	}

	/** Puts a companion on the form. A game file is copied in; nothing is written until Save. */
	private void adoptFile(AssetSchema.Companion companion) {
		Map<String, String> copied = opened == null ? new LinkedHashMap<>() : AssetEditing.adoptCompanion(opened, companion, index);
		if (opened == null) {
			String own = companion.ownStem(assetName);
			if (own != null) {
				copied.put(companion.nameTag(), own);
			}
		}
		JPanel panel = filePanels.get(companion.id());
		panel.removeAll();
		for (TagSpec tag : companion.tags()) {
			addInitial(tag, panel);
		}
		copied.forEach((key, value) -> {
			if (schema.companionFor(key) != null) {
				String id = rowIdFor(key);
				if (rows.containsKey(id)) {
					rows.get(id).editor.set(key, value);
				}
			} else if (rows.containsKey(rowIdFor(key))) {
				rows.get(rowIdFor(key)).editor.set(key, value);
			} else {
				addTag(key);
				rows.get(rowIdFor(key)).editor.set(key, value);
			}
		});
		fileCards.get(companion.id()).setVisible(true);
		filesStack.setVisible(true);
		tagsChanged();
	}

	public boolean isBusy() {
		return busy;
	}

	public boolean isEditingExisting() {
		return opened != null;
	}

	public boolean isGenerateTextOffered() {
		return generateText != null;
	}

	public boolean isGenerateSoundsOffered() {
		return generateSounds != null;
	}

	public void clickGenerateText() {
		generateText.doClick();
	}

	public void clickGenerateSounds() {
		generateSounds.doClick();
	}

	/** Whether Add or Generate is offered for this companion file. */
	public boolean isFileOffered(String id) {
		return fileOffers.containsKey(id);
	}

	public void clickAddFile(String id) {
		fileOffers.get(id).doClick();
	}

	public List<String> keptRowKeys() {
		return List.copyOf(keptRows.keySet());
	}

	public void clickReplaceKept(String key) {
		TagSpec tag = tagFor(key);
		replaceKept(tag, key, panelFor(tag));
	}

	public boolean isSoundEditable(String key) {
		return soundEditors.containsKey(key);
	}

	/** Everything the form would save now. */
	public Map<String, String> currentValues() {
		return values();
	}

	public boolean isSaveEnabled() {
		return saveButton.isEnabled();
	}

	public String messageText() {
		return message.getText();
	}

	public String titleText() {
		return title.getText();
	}

	public List<String> tagKeys() {
		return List.copyOf(rows.keySet());
	}

	public List<String> ghostKeys() {
		return List.copyOf(ghosts.keySet());
	}

	public String ghostValue(String key) {
		return ghostValues.get(key).getText();
	}

	public boolean isGhostEditable(String key) {
		return ghostValues.get(key).isEnabled();
	}

	public List<String> addableKeys() {
		List<String> keys = new ArrayList<>();
		for (java.awt.Component c : addButtons.getComponents()) {
			keys.add(((JButton) c).getText());
		}
		return keys;
	}

	public String problemFor(String key) {
		return formRows.get(key).problemText();
	}

	/** What an icon's row says about its sheet's positions. */
	public String iconPositionText(String key) {
		return ((IconEditor) rows.get(key).editor).positionText();
	}

	/** The value a tag would be saved with, or null if it wouldn't be written. */
	public String valueOf(String key) {
		return values().get(key);
	}

	/** Sets a value in the form described on {@link AssetSchema}, as a person typing would. */
	public void setValue(String key, String value) {
		if (soundEditors.containsKey(key)) {
			choose(soundEditors.get(key), value);
			revalidateRows();
			return;
		}
		rows.get(rowIdFor(key)).editor.set(key, value);
		revalidateRows();
	}

	/** Brings an absent tag in as an editable row, where its grey row was. */
	public void addTag(String key) {
		TagSpec tag = tagFor(key);
		JPanel panel = panelFor(tag);
		int place = -1;
		FormRow ghost = ghosts.remove(key);
		ghostValues.remove(key);
		if (ghost != null) {
			place = panel.getComponentZOrder(ghost);
			panel.remove(ghost);
		}
		addRow(tag, panel, place);
		tagsChanged();
		select(key);
	}

	/** Takes a tag out again. One with a default goes back to being grey. */
	public void removeTag(String key) {
		TagSpec tag = tagFor(key);
		Row row = rows.get(key);
		if (row == null || tag.required()) {
			return;
		}
		rows.remove(key);
		JPanel panel = panelFor(tag);
		int place = panel.getComponentZOrder(row.form);
		panel.remove(row.form);
		formRows.remove(key);
		if (tag.ghosted()) {
			addGhost(tag, panel, place);
		}
		tagsChanged();
		if (formRows.containsKey(key)) {
			select(key);
		} else {
			selectedKey = null;
			formRows.values().forEach(other -> other.setSelectedRow(false));
			details.showHint();
		}
	}

	private void tagsChanged() {
		rebuildAddButtons();
		revalidateRows();
		revalidate();
		repaint();
	}

	private void rebuildAddButtons() {
		addButtons.removeAll();
		List<TagSpec> all = new ArrayList<>(schema.dataTags());
		all.addAll(schema.textTags());
		for (TagSpec tag : all) {
			String id = rowId(tag);
			boolean inMissingFile = !textActive && !schema.dataTags().contains(tag);
			if (tag.addOnly() && !rows.containsKey(id) && !keptRows.containsKey(id) && !inMissingFile) {
				JButton add = new JButton(id);
				add.addActionListener(e -> addTag(id));
				addButtons.add(add);
			}
		}
		addTitle.setVisible(addButtons.getComponentCount() > 0);
	}

	private void addGhost(TagSpec tag, JPanel panel, int place) {
		JTextField value = new JTextField(tag.gameDefault());
		value.setEnabled(false);
		value.setCaretPosition(0);
		String id = rowId(tag);
		FormRow form = new FormRow(id, value, true);
		form.setAction("Add", () -> addTag(id));
		HandbookIndex.TagHelp help = helpFor(tag.key(), panel);
		describe(form, id, help, DEFAULT_NOTE + " " + tag.gameDefault() + ".");
		ghosts.put(id, form);
		ghostValues.put(id, value);
		panel.add(form, place);
	}

	private HandbookIndex.TagHelp helpFor(String key, JPanel panel) {
		AssetSchema.Companion companion = schema.companionFor(key);
		List<String> where = companion == null ? locations : companion.handbookLocations();
		boolean text = panel == null ? schema.isTextKey(key) : panel == textPanel;
		return AssetForm.help(handbook.index(), schema, where, text, key);
	}

	/** A tag in both files gets a row for each, such as WORK. */
	private String rowId(TagSpec tag) {
		boolean inBoth = !schema.dataTags().contains(tag) && schema.dataTags().stream().anyMatch(other -> other.key().equals(tag.key()));
		return inBoth ? tag.key() + IN_TEXT : tag.key();
	}

	private TagSpec tagFor(String id) {
		if (id.endsWith(IN_TEXT)) {
			String key = id.substring(0, id.length() - IN_TEXT.length());
			return schema.textTags().stream().filter(tag -> tag.key().equals(key)).findFirst().orElseThrow();
		}
		return schema.tag(id);
	}

	/** Gives a row its status chips and hover text, and remembers what its details will show. */
	private void describe(FormRow form, String key, HandbookIndex.TagHelp help, String extra) {
		formRows.put(key, form);
		form.setOnSelect(() -> select(key));
		form.setOnInfo(details::showPopover);
		form.setBand(band);
		String text = handbook.index() == null ? NO_HANDBOOK : help == null || help.description().isEmpty() ? AssetForm.withoutHelp(key) : help.description();
		descriptions.put(key, text);
		if (help != null && help.example() != null) {
			examples.put(key, help.example());
		}
		if (help != null && help.url() != null) {
			links.put(key, help.url());
		}
		if (help != null && help.effect() != null) {
			String effect = help.effect() == HandbookIndex.Effect.NONE ? NO_EFFECT : NOT_READ;
			effects.put(key, effect);
			form.addChip(effect, Theme.Tone.WARNING, null);
		}
		if (help != null && help.unverified()) {
			unverifiedKeys.add(key);
			form.addChip(UNVERIFIED, Theme.Tone.WARNING, UNVERIFIED_TIP);
		}
		String summary = help == null || help.summary().isEmpty() ? text : help.summary();
		form.setTip(extra == null ? summary : summary + "\n\n" + extra);
	}

	/** Shows a tag in the details panel, and marks its row. */
	private void select(String key) {
		if (key.equals(selectedKey) && formRows.containsKey(key) && formRows.get(key).isSelectedRow()) {
			return;
		}
		selectedKey = key;
		FormRow form = formRows.get(key);
		formRows.values().forEach(row -> row.setSelectedRow(row == form));
		details.show(detailsFor(key));
	}

	private DetailsPanel.Content detailsFor(String key) {
		SoundEntries.Slot sound = schema.sounds().stream().filter(slot -> slot.valueKey().equals(key)).findFirst().orElse(null);
		if (sound != null) {
			return new DetailsPanel.Content(sound.key(type.fileName(assetName)), List.of(new DetailsPanel.Status("Required", Theme.Tone.HELP)), descriptions.get(key), null,
					soundRule(sound), "From " + sound.evidence(), null, null);
		}
		int dot = key.indexOf('.');
		TagSpec nested = dot < 0 ? null : schema.find(key);
		TagSpec tag = nested != null ? nested : tagFor(dot < 0 ? key : key.substring(0, dot));
		boolean whole = dot < 0 || nested != null;
		List<DetailsPanel.Status> statuses = new ArrayList<>();
		boolean required = whole && tag.required();
		if (!whole) {
			String subKey = key.substring(dot + 1);
			required = tag.subKeys().stream().anyMatch(sub -> sub.key().equals(subKey) && sub.required());
		}
		if (required) {
			statuses.add(new DetailsPanel.Status("Required", Theme.Tone.HELP));
		}
		if (effects.containsKey(key)) {
			statuses.add(new DetailsPanel.Status(effects.get(key), Theme.Tone.WARNING));
		}
		if (unverifiedKeys.contains(key)) {
			statuses.add(new DetailsPanel.Status(UNVERIFIED_TIP, Theme.Tone.WARNING));
		}
		String rule = whole ? schema.ruleText(tag) : null;
		String defaultNote = whole && tag.ghosted() ? DEFAULT_NOTE + " " + tag.gameDefault() + "." : null;
		return new DetailsPanel.Content(key, statuses, descriptions.get(key), examples.get(key), rule,
				rule == null ? null : "From " + tag.evidence(), defaultNote, links.containsKey(key) ? () -> openLink(key) : null);
	}

	/** Selects a tag, as clicking its row would. */
	public void selectTag(String key) {
		select(key);
	}

	/** Goes to the first tag that's missing a value or has a problem. */
	public void goToFirstProblem() {
		for (String key : attention) {
			String shownAs = nearestRow(key);
			if (shownAs != null) {
				formRows.get(shownAs).reveal();
				select(shownAs);
				return;
			}
		}
	}

	/** The rows marked as selected, which is never more than one. */
	public List<String> highlightedRows() {
		return formRows.keySet().stream().filter(key -> formRows.get(key).isSelectedRow()).toList();
	}

	/** The row a key shows on: its own, or the nearest block or row holding it. */
	private String nearestRow(String key) {
		// A block has no value of its own, so a problem with exactly this key is the text file's.
		if (formRows.containsKey(key + IN_TEXT)) {
			return key + IN_TEXT;
		}
		String path = key;
		while (!formRows.containsKey(path)) {
			int dot = path.lastIndexOf('.');
			if (dot < 0) {
				return null;
			}
			path = path.substring(0, dot);
		}
		return path;
	}

	/** Whether a folded list is showing what's in it. */
	public boolean isUnfolded(String key) {
		return foldedContent.containsKey(key) && foldedContent.get(key).isVisible();
	}

	/** The heading of a row, including its fold marker when it has one. */
	public String headingText(String key) {
		return formRows.get(key).headingText();
	}

	/** Clicks a row's heading. A heading that folds the row does that. */
	public void clickHeading(String key) {
		formRows.get(key).clickHeading();
	}

	/** Whether a section card, such as Appearance or Text file, is open. */
	public boolean isSectionOpen(String heading) {
		JPanel body = sectionBodies.get(heading);
		return body != null && body.isVisible();
	}

	/** Clicks a section card's heading, which folds or unfolds it. */
	public void clickSection(String heading) {
		sectionToggles.get(heading).run();
	}

	/** Whether the notes under the tag details are showing. */
	public boolean isNotesOpen() {
		return details.isNotesOpen();
	}

	/** Clicks the notes heading, which folds or unfolds them. */
	public void clickNotes() {
		details.clickNotes();
	}

	/** Clicks the Add or Remove button on a row, including one inside a block. */
	public void clickRowAction(String key) {
		formRows.get(key).clickAction();
	}

	/** Adds an entry to keyed rows, and says whether it stayed. */
	public boolean addEntry(String key, String name) {
		KeyedRowsEditor editor = keyedEditors.get(key);
		return name.equals(editor.addItem(name).name.getSelectedItem());
	}

	public void removeEntries(String key) {
		keyedEditors.get(key).removeAll();
	}

	/** Removes one block from a list, as its Remove button does. */
	public void removeLevel(String key, int level) {
		((BlockListEditor) rows.get(key).editor).removeBlock(level);
	}

	/** The whole form, however far it scrolls, so tests can look at all of it. */
	public JComponent formComponent() {
		return (JComponent) scroller.getViewport().getView();
	}

	/** What a greyed-out tag inside a block shows, or null if it isn't greyed out. */
	public String nestedGhostValue(String key) {
		return nestedGhosts.containsKey(key) ? nestedGhosts.get(key).getText() : null;
	}

	public boolean isTextCardShowing() {
		return textCard.isVisible();
	}

	public String selectedTag() {
		return selectedKey;
	}

	public String detailsName() {
		return details.shownName();
	}

	public List<String> detailsText() {
		return details.shownText();
	}

	public boolean detailsHasReadMore() {
		return details.hasReadMore();
	}

	public void clickDetailsReadMore() {
		details.clickReadMore();
	}

	/** What hovering over a tag's row shows. */
	public String tipFor(String key) {
		return formRows.get(key).tip();
	}

	public boolean isDetailsDocked() {
		return details.getParent() == this;
	}

	/** The details panel itself, so tests can look at it wherever it's showing. */
	public JComponent detailsComponent() {
		return details;
	}

	public boolean isDetailsPopoverShowing() {
		return details.isPopoverShowing();
	}

	public void hideDetailsPopover() {
		details.hidePopover();
	}

	public boolean isInfoShowing(String key) {
		return formRows.get(key).isInfoShowing();
	}

	public boolean isStacked(String key) {
		return formRows.get(key).isStacked();
	}

	/** Clicks a row's info button. */
	public void clickInfo(String key) {
		formRows.get(key).clickInfo();
	}

	/** Acts as if a tag's editor gained focus. */
	public void simulateFocusOn(String key) {
		formRows.get(key).simulateFocus();
	}

	/** Clicks a tag's Read more button. A failure is shown in the footer. */
	public void openLink(String key) {
		String url = links.get(key);
		if (url == null) {
			return;
		}
		try {
			linkOpener.open(url);
		} catch (IOException | RuntimeException e) {
			Theme.tone(message, Theme.Tone.ERROR);
			message.setText("The handbook couldn't be opened: " + e.getMessage());
		}
	}

	/** How far down the form is scrolled. */
	public int scrollPosition() {
		return scroller.getVerticalScrollBar().getValue();
	}

	public void scrollTo(int position) {
		validate();
		scroller.getVerticalScrollBar().setValue(position);
	}

	public int scrollMaximum() {
		return scroller.getVerticalScrollBar().getMaximum();
	}

	public String descriptionFor(String key) {
		return descriptions.get(key);
	}

	public boolean isShowingUnverified(String key) {
		return unverifiedKeys.contains(key);
	}

	public String exampleFor(String key) {
		return examples.get(key);
	}

	public String effectFor(String key) {
		return effects.get(key);
	}

	public String linkFor(String key) {
		return links.get(key);
	}

	/** Every note on the screen, whether at the top of the form or the bottom of the details. */
	public List<String> noteTexts() {
		List<String> texts = new ArrayList<>(shownNotes);
		texts.addAll(details.footerText());
		return texts;
	}

	/** Opens a handbook page in the browser. */
	public static void openInBrowser(String url) throws IOException {
		if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
			throw new IOException("This system doesn't support opening links.");
		}
		Desktop.getDesktop().browse(URI.create(url));
	}

	private void addRow(TagSpec tag, JPanel panel, int place) {
		String id = rowId(tag);
		Row row = new Row(tag);
		rows.put(id, row);
		HandbookIndex.TagHelp help = helpFor(tag.key(), panel);
		row.form = new FormRow(id, row.editor.component(), false);
		switch (tag.kind()) {
		case TEXT_BLOCK, BLOCK, KEYED_ROWS, KEYED_BLOCKS, BLOCK_LIST -> row.form.setAlwaysStacked();
		default -> {
			// These fit on one line.
		}
		}
		if (panel == textPanel && tag.required()) {
			row.form.addChip("Required", Theme.Tone.HELP, null);
		}
		if (!tag.required()) {
			row.form.setAction("Remove", () -> removeTag(id));
		}
		describe(row.form, id, help, schema.ruleText(tag));
		if (schema.foldHeadings().containsKey(tag.key())) {
			foldByHeading(id, row.form, row.editor.component());
		} else if (tag.required() && (tag.kind() == TagSpec.Kind.KEYED_BLOCKS || schema.companionFor(tag.key()) != null && tag.kind() == TagSpec.Kind.BLOCK_LIST)) {
			collapse(id, row.form, row.editor.component());
		}
		panel.add(row.form, place);
	}

	/** Sounds aren't in the asset's own files, so they have their own section. It starts from what vanilla uses, if that suits every asset of the type. */
	private void addSoundRow(SoundEntries.Slot slot, String loaded) {
		JComboBox<String> choice = new JComboBox<>();
		String starting = AssetForm.soundStart(slot, index);
		if (starting == null) {
			choice.addItem("");
		}
		AssetForm.soundOptions(slot, index).forEach(choice::addItem);
		if (starting != null) {
			choice.setSelectedItem(starting);
		}
		if (loaded != null) {
			if (starting == null) {
				choice.removeItemAt(0);
			}
			choose(choice, loaded);
		}
		choice.addActionListener(e -> revalidateRows());
		fullWidth(choice);
		soundEditors.put(slot.valueKey(), choice);
		FormRow form = new FormRow(slot.key(type.fileName(assetName)), choice, false);
		form.setAlwaysStacked();
		form.addChip("Required", Theme.Tone.HELP, null);
		describe(form, slot.valueKey(), null, soundRule(slot));
		soundPanel.add(form);
	}

	/** An entry in a file that isn't this asset's own, so saving doesn't touch it. */
	private void addFixedSoundRow(SoundEntries.Slot slot, SoundEntries.Info info) {
		JTextField value = new JTextField(info.value());
		value.setEnabled(false);
		value.setCaretPosition(0);
		FormRow form = new FormRow(slot.key(type.fileName(assetName)), value, true);
		form.setAlwaysStacked();
		describe(form, slot.valueKey(), null, soundRule(slot));
		soundPanel.add(form);
	}

	private String soundRule(SoundEntries.Slot slot) {
		SoundEntries.Info info = opened == null ? SoundEntries.Info.MISSING : opened.sounds().get(slot);
		if (info.state() == SoundEntries.State.GAME) {
			return "This is the game's own entry, so it can't be changed here.";
		}
		if (info.state() == SoundEntries.State.OTHER_MOD_FILE) {
			return "This entry is in " + info.source() + ", which holds more than this asset, so it can't be changed here.";
		}
		return "Written to " + slot.relativeFile(type.fileName(assetName)) + ". " + SoundEntries.NO_SOUND + " means none. Without an entry the game plays nothing, and can report it when it closes.";
	}

	private static JButton smallButton(String text, Runnable action) {
		JButton button = new JButton(text);
		Theme.style(button, "small");
		button.setMargin(new java.awt.Insets(0, 5, 0, 5));
		button.addActionListener(e -> action.run());
		return button;
	}

	private Editor createEditor(TagSpec tag) {
		return switch (tag.kind()) {
		case BOOL, SPRITE -> choiceEditor(tag.key(), AssetForm.options(tag, schema, index));
		case PLAIN -> {
			// A tag naming one of the asset's files is chosen from the files in that folder.
			List<String> files = AssetForm.options(tag, schema, index);
			yield files == null ? numberOrPlainEditor(tag) : choiceEditor(tag.key(), files);
		}
		case ICON -> new IconEditor(tag);
		case COLOR -> new ColorEditor(tag);
		case KEY_LIST -> new ListEditor(tag);
		case DECIMAL_MAP, KEYED_LISTS -> new MapEditor(tag);
		case KEYED_TEXTS -> new KeyedTextEditor(tag);
		case TEXT_BLOCK -> new BlockEditor(tag);
		case BLOCK -> new NestedBlockEditor(tag);
		case KEYED_ROWS -> new KeyedRowsEditor(tag);
		case KEYED_BLOCKS -> new KeyedBlockEditor(tag);
		case BLOCK_LIST -> new BlockListEditor(tag);
		case KEY -> keyEditor(tag);
		case TEXT_ROWS -> textEditor(tag.key(), true, false);
		case FRAMES -> new FramesEditor(tag);
		case TEXT_LINE -> textEditor(tag.key(), false, true);
		case TEXT_LINES -> textEditor(tag.key(), true, true);
		default -> numberOrPlainEditor(tag);
		};
	}

	/** The core decides what's valid. This only stops characters that can't ever be part of it. */
	private static void restrict(JTextField field, Predicate<String> allowed) {
		((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
			@Override
			public void insertString(FilterBypass fb, int offset, String text, AttributeSet attributes) throws BadLocationException {
				replace(fb, offset, 0, text, attributes);
			}

			@Override
			public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attributes) throws BadLocationException {
				String current = fb.getDocument().getText(0, fb.getDocument().getLength());
				String after = current.substring(0, offset) + (text == null ? "" : text) + current.substring(offset + length);
				if (allowed.test(after)) {
					super.replace(fb, offset, length, text, attributes);
				}
			}
		});
	}

	private void listen(JTextComponent component) {
		component.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				revalidateRows();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				revalidateRows();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				revalidateRows();
			}
		});
	}

	private static <T extends JComponent> T fullWidth(T component) {
		component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
		return component;
	}

	private Editor numberOrPlainEditor(TagSpec tag) {
		JTextField field = new JTextField();
		restrict(field, tag::allowsTyping);
		listen(field);
		if (tag.rangeText() != null) {
			field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, tag.rangeText());
		}
		// An empty list is a value of its own, which the game reads as having no entries.
		return simple(tag.key(), fullWidth(field), tag.kind() == TagSpec.Kind.DECIMALS);
	}

	private Editor textEditor(String key, boolean multiLine, boolean keepBlank) {
		if (multiLine) {
			JTextArea area = new JTextArea(3, 40);
			area.setLineWrap(true);
			area.setWrapStyleWord(true);
			area.setFont(new JTextField().getFont());
			listen(area);
			JScrollPane pane = new JScrollPane(area);
			pane.setMaximumSize(new Dimension(Integer.MAX_VALUE, pane.getPreferredSize().height));
			return new Editor() {
				@Override
				public JComponent component() {
					return pane;
				}

				@Override
				public void collect(Map<String, String> values) {
					if (keepBlank || !area.getText().isEmpty()) {
						values.put(key, area.getText());
					}
				}

				@Override
				public void set(String ignored, String value) {
					area.setText(value);
				}
			};
		}
		JTextField field = new JTextField();
		listen(field);
		return simple(key, fullWidth(field), keepBlank);
	}

	private static Editor simple(String key, JTextField field, boolean keepBlank) {
		return new Editor() {
			@Override
			public JComponent component() {
				return field;
			}

			@Override
			public void collect(Map<String, String> values) {
				if (keepBlank || !field.getText().isEmpty()) {
					values.put(key, field.getText());
				}
			}

			@Override
			public void set(String ignored, String value) {
				field.setText(value);
			}
		};
	}

	/** Crops show which image they use, because that's how a farm gets its look. */
	private Editor keyEditor(TagSpec tag) {
		Editor editor = choiceEditor(tag.key(), AssetForm.options(tag, schema, index));
		if (tag.reference() == TagSpec.Reference.GROWABLES) {
			@SuppressWarnings("unchecked")
			JComboBox<String> choice = (JComboBox<String>) editor.component();
			choice.setRenderer(new javax.swing.DefaultListCellRenderer() {
				private static final long serialVersionUID = 1L;

				@Override
				public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int at, boolean selected, boolean focused) {
					putClientProperty("html.disable", Boolean.TRUE);
					super.getListCellRendererComponent(list, value, at, selected, focused);
					GameAssetIndex.Crop crop = value == null ? null : index.crop(String.valueOf(value));
					if (crop != null) {
						setText(value + "  (image: " + crop.image() + ")");
					}
					return this;
				}
			});
		}
		return editor;
	}

	/** A dropdown that starts blank, because a value is never chosen on the user's behalf. */
	private Editor choiceEditor(String key, List<String> options) {
		JComboBox<String> choice = new JComboBox<>();
		choice.addItem("");
		options.forEach(choice::addItem);
		choice.addActionListener(e -> revalidateRows());
		fullWidth(choice);
		return new Editor() {
			@Override
			public JComponent component() {
				return choice;
			}

			@Override
			public void collect(Map<String, String> values) {
				String selected = String.valueOf(choice.getSelectedItem());
				if (!selected.isEmpty()) {
					values.put(key, selected);
				}
			}

			@Override
			public void set(String ignored, String value) {
				choose(choice, value);
			}
		};
	}

	private final class ColorEditor implements Editor {
		private final JTextField[] parts = { new JTextField(4), new JTextField(4), new JTextField(4) };
		private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		private final String key;
		/** A block is a different colour from the same numbers written as red_green_blue, so it stays a block. */
		private boolean asBlock;

		ColorEditor(TagSpec tag) {
			key = tag.key();
			String[] names = { "Red", "Green", "Blue" };
			for (int i = 0; i < parts.length; i++) {
				restrict(parts[i], text -> text.matches("\\d{0,3}"));
				listen(parts[i]);
				panel.add(new JLabel(names[i]));
				panel.add(parts[i]);
			}
			fullWidth(panel);
		}

		@Override
		public JComponent component() {
			return panel;
		}

		@Override
		public void collect(Map<String, String> values) {
			if (!parts[0].getText().isEmpty() && !parts[1].getText().isEmpty() && !parts[2].getText().isEmpty()) {
				values.put(key, asBlock
						? "{R: " + parts[0].getText() + ", G: " + parts[1].getText() + ", B: " + parts[2].getText() + ",}"
						: parts[0].getText() + "_" + parts[1].getText() + "_" + parts[2].getText());
			}
		}

		@Override
		public void set(String ignored, String value) {
			java.util.regex.Matcher block = java.util.regex.Pattern.compile("\\{R: (\\d{1,3}), G: (\\d{1,3}), B: (\\d{1,3}),\\}").matcher(value.trim());
			if (block.matches()) {
				asBlock = true;
				parts[0].setText(block.group(1));
				parts[1].setText(block.group(2));
				parts[2].setText(block.group(3));
				return;
			}
			asBlock = false;
			String[] given = value.split("_", -1);
			for (int i = 0; i < parts.length; i++) {
				parts[i].setText(i < given.length ? given[i] : "");
			}
		}
	}

	private final class IconEditor implements Editor {
		private static final String ONE_ICON = "One icon";
		private static final String COMBINED = "Two icons combined";
		private final JComboBox<String> sheet = new JComboBox<>();
		private final JTextField position = new JTextField(4);
		private final JLabel positionLabel = new JLabel("Position");
		private final JPanel single = new JPanel(new BorderLayout(6, 0));
		private final JPanel panel = new JPanel();
		private final JComboBox<String> form;
		private final NestedBlockEditor block;
		private final String key;

		IconEditor(TagSpec tag) {
			key = tag.key();
			sheet.addItem("");
			index.options(tag.reference()).forEach(sheet::addItem);
			sheet.addActionListener(e -> {
				showPositions();
				revalidateRows();
			});
			restrict(position, text -> text.matches("\\d{0,4}"));
			listen(position);
			JPanel right = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
			right.add(positionLabel);
			right.add(position);
			single.add(sheet, BorderLayout.CENTER);
			single.add(right, BorderLayout.EAST);
			single.setAlignmentX(LEFT_ALIGNMENT);
			fullWidth(single);
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			panel.setAlignmentX(LEFT_ALIGNMENT);
			if (tag.nested().isEmpty()) {
				form = null;
				block = null;
				panel.add(single);
			} else {
				form = new JComboBox<>(new String[] { ONE_ICON, COMBINED });
				form.setAlignmentX(LEFT_ALIGNMENT);
				fullWidth(form);
				block = new NestedBlockEditor(tag);
				block.component().setVisible(false);
				form.addActionListener(e -> {
					boolean combined = COMBINED.equals(form.getSelectedItem());
					single.setVisible(!combined);
					block.component().setVisible(combined);
					layoutChanged();
				});
				panel.add(form);
				panel.add(single);
				panel.add(block.component());
			}
		}

		/** The sheet's size says which positions it has, which saves finding out from a warning. */
		private void showPositions() {
			String range = AssetForm.iconPositions(String.valueOf(sheet.getSelectedItem()), index);
			positionLabel.setText(range == null ? "Position" : "Position (" + range + ")");
		}

		@Override
		public JComponent component() {
			return panel;
		}

		@Override
		public void collect(Map<String, String> values) {
			if (form != null && COMBINED.equals(form.getSelectedItem())) {
				block.collect(values);
				return;
			}
			String chosen = String.valueOf(sheet.getSelectedItem());
			if (!chosen.isEmpty() && !position.getText().isEmpty()) {
				values.put(key, chosen + "->" + position.getText());
			}
		}

		@Override
		public void set(String tagKey, String value) {
			if (form != null && !tagKey.equals(key)) {
				form.setSelectedItem(COMBINED);
				block.set(tagKey, value);
				return;
			}
			if (form != null) {
				form.setSelectedItem(ONE_ICON);
			}
			int split = value.lastIndexOf("->");
			choose(sheet, split < 0 ? value : value.substring(0, split));
			position.setText(split < 0 ? "" : value.substring(split + 2));
		}

		String positionText() {
			return positionLabel.getText();
		}
	}

	/** Rows of a chosen entry and its amount, which the game keeps in two lists of the same length. */
	private final class ListEditor implements Editor {
		private final class Item {
			final JComboBox<String> entry = new JComboBox<>();
			final JTextField amount = new JTextField(6);
			final JPanel line = new JPanel(new BorderLayout(6, 0));
		}

		private final JPanel panel = new JPanel();
		private final JPanel lines = new JPanel();
		private final List<Item> items = new ArrayList<>();
		private final TagSpec tag;
		private final TagSpec amounts;

		ListEditor(TagSpec tag) {
			this.tag = tag;
			this.amounts = schema.dataTags().stream().filter(t -> tag.key().equals(t.linkedTo())).findFirst().orElse(null);
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
			lines.setAlignmentX(LEFT_ALIGNMENT);
			JButton add = smallButton("Add an entry", this::addItem);
			add.setAlignmentX(LEFT_ALIGNMENT);
			panel.add(lines);
			panel.add(add);
		}

		private Item addItem() {
			Item item = new Item();
			item.entry.addItem("");
			index.options(tag.reference()).forEach(item.entry::addItem);
			item.entry.addActionListener(e -> revalidateRows());
			restrict(item.amount, text -> text.matches("-?\\d*\\.?\\d*"));
			listen(item.amount);
			item.line.setAlignmentX(LEFT_ALIGNMENT);
			JPanel right = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
			if (amounts != null) {
				right.add(new JLabel("Amount"));
				right.add(item.amount);
			}
			right.add(smallButton("Remove", () -> {
				items.remove(item);
				lines.remove(item.line);
				lines.revalidate();
				revalidateRows();
				AssetEditorScreen.this.revalidate();
				AssetEditorScreen.this.repaint();
			}));
			item.line.add(item.entry, BorderLayout.CENTER);
			item.line.add(right, BorderLayout.EAST);
			item.line.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
			fullWidth(item.line);
			items.add(item);
			lines.add(item.line);
			lines.revalidate();
			revalidateRows();
			AssetEditorScreen.this.revalidate();
			AssetEditorScreen.this.repaint();
			return item;
		}

		@Override
		public JComponent component() {
			return panel;
		}

		@Override
		public void collect(Map<String, String> values) {
			List<String> chosen = new ArrayList<>();
			List<String> numbers = new ArrayList<>();
			for (Item item : items) {
				chosen.add(String.valueOf(item.entry.getSelectedItem()));
				numbers.add(item.amount.getText());
			}
			values.put(tag.key(), String.join(",", chosen));
			if (amounts != null) {
				values.put(amounts.key(), String.join(",", numbers));
			}
		}

		@Override
		public void set(String key, String value) {
			String[] given = value.isEmpty() ? new String[0] : value.split(",", -1);
			while (items.size() < given.length) {
				addItem();
			}
			for (int i = 0; i < given.length; i++) {
				if (key.equals(tag.key())) {
					choose(items.get(i).entry, given[i]);
				} else {
					items.get(i).amount.setText(given[i]);
				}
			}
		}
	}

	/** One number for each entry the game knows. A blank one isn't written. */
	/** An object of objects, such as a race's STATS or COLORS. Each entry is a block with fixed tags. */
	private final class KeyedBlockEditor implements Editor {
		private final JPanel panel = new JPanel();
		private final JPanel lines = new JPanel();
		private final Map<String, NestedBlockEditor> entries = new LinkedHashMap<>();
		private final TagSpec tag;
		private final JTextField name = new JTextField();
		private final JComboBox<String> named = new JComboBox<>();

		KeyedBlockEditor(TagSpec tag) {
			this.tag = tag;
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
			lines.setAlignmentX(LEFT_ALIGNMENT);
			JPanel add = new JPanel(new BorderLayout(6, 0));
			add.setAlignmentX(LEFT_ALIGNMENT);
			if (tag.reference() == null) {
				add.add(name, BorderLayout.CENTER);
			} else {
				named.addItem("");
				index.options(tag.reference()).forEach(named::addItem);
				add.add(named, BorderLayout.CENTER);
			}
			add.add(smallButton("Add an entry", this::addNamed), BorderLayout.EAST);
			panel.add(lines);
			panel.add(add);
		}

		private void addNamed() {
			String row = tag.reference() == null ? name.getText().trim() : (String) named.getSelectedItem();
			if (row == null || row.isBlank() || entries.containsKey(row)) {
				return;
			}
			addEntry(row);
			name.setText("");
			named.setSelectedItem("");
		}

		private void addEntry(String row) {
			List<TagSpec> children = tag.shape().children().stream().map(child -> child.withKey(tag.key() + "." + row + "." + child.key())).toList();
			NestedBlockEditor editor = new NestedBlockEditor(children);
			FormRow form = new FormRow(row, editor.component(), false);
			form.setAlwaysStacked();
			form.setAction("Remove", () -> {
				entries.remove(row);
				lines.remove(form);
				layoutChanged();
			});
			entries.put(row, editor);
			lines.add(form);
			layoutChanged();
		}

		@Override
		public JComponent component() {
			return panel;
		}

		@Override
		public void collect(Map<String, String> values) {
			entries.values().forEach(editor -> editor.collect(values));
		}

		@Override
		public void set(String key, String value) {
			String rest = key.substring(tag.key().length() + 1);
			int dot = rest.indexOf('.');
			String row = dot < 0 ? rest : rest.substring(0, dot);
			if (!entries.containsKey(row)) {
				addEntry(row);
			}
			entries.get(row).set(key, value);
		}
	}

	/** One block of text for each name, such as a biography's opinion of a stat. */
	private final class KeyedTextEditor implements Editor {
		private final Map<String, JTextArea> areas = new LinkedHashMap<>();
		private final JPanel panel = new JPanel();
		private final TagSpec tag;

		KeyedTextEditor(TagSpec tag) {
			this.tag = tag;
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			JTextField name = new JTextField();
			JButton add = smallButton("Add", () -> {
				String key = name.getText().trim();
				if (!key.isEmpty() && !areas.containsKey(key)) {
					addArea(key);
					name.setText("");
					layoutChanged();
				}
			});
			JPanel row = new JPanel(new BorderLayout(6, 0));
			row.add(name, BorderLayout.CENTER);
			row.add(add, BorderLayout.EAST);
			row.setAlignmentX(LEFT_ALIGNMENT);
			panel.add(row);
			fullWidth(panel);
		}

		private JTextArea addArea(String option) {
			JTextArea area = new JTextArea(4, 24);
			area.setLineWrap(true);
			area.setWrapStyleWord(true);
			listen(area);
			JPanel cell = new JPanel(new BorderLayout(6, 4));
			cell.add(new JLabel(option), BorderLayout.NORTH);
			cell.add(new JScrollPane(area), BorderLayout.CENTER);
			cell.setAlignmentX(LEFT_ALIGNMENT);
			panel.add(cell);
			areas.put(option, area);
			return area;
		}

		@Override
		public JComponent component() {
			return panel;
		}

		@Override
		public void collect(Map<String, String> values) {
			areas.forEach((entry, area) -> {
				if (!area.getText().isBlank()) {
					values.put(tag.key() + "." + entry, area.getText());
				}
			});
		}

		@Override
		public void set(String key, String value) {
			String entry = key.substring(tag.key().length() + 1);
			(areas.containsKey(entry) ? areas.get(entry) : addArea(entry)).setText(value);
		}
	}

	private final class MapEditor implements Editor {
		private final Map<String, JTextField> fields = new LinkedHashMap<>();
		private final JPanel panel = new JPanel(new java.awt.GridLayout(0, 2, 12, 4));
		private final JPanel wrap = new JPanel();
		private final TagSpec tag;

		MapEditor(TagSpec tag) {
			this.tag = tag;
			if (tag.reference() != null) {
				index.options(tag.reference()).forEach(this::addField);
			} else {
				wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
				JTextField name = new JTextField();
				JButton add = smallButton("Add", () -> {
					String key = name.getText().trim();
					if (!key.isEmpty() && !fields.containsKey(key)) {
						addField(key);
						name.setText("");
						layoutChanged();
					}
				});
				JPanel row = new JPanel(new BorderLayout(6, 0));
				row.add(name, BorderLayout.CENTER);
				row.add(add, BorderLayout.EAST);
				row.setAlignmentX(LEFT_ALIGNMENT);
				panel.setAlignmentX(LEFT_ALIGNMENT);
				wrap.add(row);
				wrap.add(panel);
			}
			fullWidth(panel);
		}

		private JTextField addField(String option) {
			JTextField field = new JTextField(5);
			restrict(field, tag::allowsTyping);
			listen(field);
			JPanel cell = new JPanel(new BorderLayout(6, 0));
			JLabel name = new JLabel(option);
			name.setPreferredSize(new Dimension(80, name.getPreferredSize().height));
			cell.add(name, BorderLayout.WEST);
			cell.add(field, BorderLayout.CENTER);
			panel.add(cell);
			fields.put(option, field);
			return field;
		}

		@Override
		public JComponent component() {
			return tag.reference() == null ? wrap : panel;
		}

		@Override
		public void collect(Map<String, String> values) {
			fields.forEach((entry, field) -> {
				if (!field.getText().isEmpty()) {
					values.put(tag.key() + "." + entry, field.getText());
				}
			});
		}

		@Override
		public void set(String key, String value) {
			// A file can name an entry the game doesn't have, or a wildcard (RMAP.java:130-133).
			String entry = key.substring(tag.key().length() + 1);
			(fields.containsKey(entry) ? fields.get(entry) : addField(entry)).setText(value);
		}
	}

	/** A block with fixed entries, such as a wiki article. */
	private final class BlockEditor implements Editor {
		private final Map<String, Editor> editors = new LinkedHashMap<>();
		private final JPanel panel = new JPanel();

		BlockEditor(TagSpec tag) {
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			for (TagSpec.SubKey sub : tag.subKeys()) {
				String full = tag.key() + "." + sub.key();
				Editor editor = textEditor(full, sub.multiLine(), false);
				FormRow form = new FormRow(sub.key(), editor.component(), false);
				form.setAlwaysStacked();
				if (sub.required()) {
					form.addChip("Required", Theme.Tone.HELP, null);
				}
				describe(form, full, helpFor(full, textPanel), null);
				panel.add(form);
				editors.put(full, editor);
			}
		}

		@Override
		public JComponent component() {
			return panel;
		}

		@Override
		public void collect(Map<String, String> values) {
			editors.values().forEach(editor -> editor.collect(values));
		}

		@Override
		public void set(String key, String value) {
			editors.get(key).set(key, value);
		}
	}

	/** What goes in a nested row. Blank means absent here, because the block's rules decide what's needed. */
	private Editor childEditor(TagSpec child) {
		return switch (child.kind()) {
		case TEXT_LINE -> textEditor(child.key(), false, false);
		case TEXT_LINES -> textEditor(child.key(), true, false);
		default -> createEditor(child);
		};
	}

	/** A row inside a block, with its description and hover text. */
	private FormRow nestedRow(TagSpec child, JComponent editor, boolean ghost) {
		FormRow form = new FormRow(child.leafName(), editor, ghost);
		form.setAlwaysStacked();
		if (!ghost && child.required()) {
			form.addChip("Required", Theme.Tone.HELP, null);
		}
		String extra = ghost ? DEFAULT_NOTE + " " + child.gameDefault() + "." : schema.ruleText(child);
		describe(form, child.key(), helpFor(child.key(), null), extra);
		return form;
	}

	private void layoutChanged() {
		revalidateRows();
		AssetEditorScreen.this.revalidate();
		AssetEditorScreen.this.repaint();
	}

	/** A block with fixed tags. Those with a default show greyed out until they're added. */
	private final class NestedBlockEditor implements Editor {
		private final JPanel panel = new JPanel();
		private final Map<String, Editor> active = new LinkedHashMap<>();
		private final Map<String, FormRow> shown = new LinkedHashMap<>();
		private final Map<String, TagSpec> children = new LinkedHashMap<>();

		NestedBlockEditor(TagSpec tag) {
			this(tag.nested());
		}

		NestedBlockEditor(List<TagSpec> tags) {
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			panel.setAlignmentX(LEFT_ALIGNMENT);
			for (TagSpec child : tags) {
				children.put(child.key(), child);
				if (child.ghosted()) {
					showGhost(child, -1);
				} else {
					showEditor(child, -1);
				}
			}
		}

		private void showGhost(TagSpec child, int place) {
			JTextField value = new JTextField(child.gameDefault());
			value.setEnabled(false);
			value.setCaretPosition(0);
			FormRow form = nestedRow(child, value, true);
			form.setAction("Add", () -> {
				activate(child.key());
				layoutChanged();
			});
			shown.put(child.key(), form);
			nestedGhosts.put(child.key(), value);
			panel.add(form, place);
		}

		private void showEditor(TagSpec child, int place) {
			Editor editor = childEditor(child);
			FormRow form = nestedRow(child, editor.component(), false);
			if (schema.foldHeadings().containsKey(child.key())) {
				foldByHeading(child.key(), form, editor.component());
			} else if (child.kind() == TagSpec.Kind.BLOCK_LIST) {
				collapse(child.key(), form, editor.component());
			}
			if (child.ghosted()) {
				form.setAction("Remove", () -> {
					int at = panel.getComponentZOrder(form);
					panel.remove(form);
					active.remove(child.key());
					showGhost(child, at);
					layoutChanged();
				});
			}
			active.put(child.key(), editor);
			shown.put(child.key(), form);
			panel.add(form, place);
		}

		private void activate(String key) {
			FormRow ghost = shown.get(key);
			int at = panel.getComponentZOrder(ghost);
			panel.remove(ghost);
			nestedGhosts.remove(key);
			showEditor(children.get(key), at);
		}

		@Override
		public JComponent component() {
			return panel;
		}

		@Override
		public void collect(Map<String, String> values) {
			active.values().forEach(editor -> editor.collect(values));
		}

		@Override
		public void set(String key, String value) {
			for (String child : children.keySet()) {
				if (key.equals(child) || key.startsWith(child + ".")) {
					if (!active.containsKey(child)) {
						activate(child);
					}
					active.get(child).set(key, value);
					return;
				}
			}
		}
	}

	/** Folds a row from its heading, leaving its Remove button in place. It starts folded. */
	private void foldByHeading(String key, FormRow form, JComponent content) {
		form.setAlwaysStacked();
		content.setVisible(false);
		foldedContent.put(key, content);
		form.setFolding(schema.foldHeadings().get(key), () -> {
			content.setVisible(!content.isVisible());
			form.setFolded(!content.isVisible());
			layoutChanged();
		});
		collapsed.put(key, () -> {
			if (content.isVisible()) {
				return false;
			}
			content.setVisible(true);
			form.setFolded(false);
			return true;
		});
	}

	/**
	 * A list of blocks inside a block can run to hundreds of rows, such as a room's sprites, so each
	 * starts folded. Anything the checker says about one opens it, so nothing that matters stays hidden.
	 */
	private void collapse(String key, FormRow form, JComponent content) {
		content.setVisible(false);
		foldedContent.put(key, content);
		Runnable[] toggle = new Runnable[1];
		toggle[0] = () -> {
			content.setVisible(!content.isVisible());
			form.setAction(content.isVisible() ? "Hide" : "Show", toggle[0]);
			layoutChanged();
		};
		form.setAction("Show", toggle[0]);
		collapsed.put(key, () -> {
			if (content.isVisible()) {
				return false;
			}
			content.setVisible(true);
			form.setAction("Hide", toggle[0]);
			return true;
		});
	}

	/** A sprite's frames, typed or added from what the game and mod have. */
	private final class FramesEditor implements Editor {
		private final JPanel panel = new JPanel();
		private final JTextArea area = new JTextArea(3, 30);
		private final TagSpec tag;

		FramesEditor(TagSpec tag) {
			this.tag = tag;
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			area.setFont(new JTextField().getFont());
			listen(area);
			JScrollPane pane = new JScrollPane(area);
			pane.setAlignmentX(LEFT_ALIGNMENT);
			pane.setMaximumSize(new Dimension(Integer.MAX_VALUE, pane.getPreferredSize().height));
			panel.add(pane);
			JComboBox<String> sheet = new JComboBox<>();
			sheet.addItem("");
			AssetForm.options(tag, schema, index).forEach(sheet::addItem);
			JTextField row = new JTextField("0", 3);
			restrict(row, text -> text.matches("\\d{0,6}"));
			row.setMaximumSize(row.getPreferredSize());
			JPanel adding = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
			adding.setAlignmentX(LEFT_ALIGNMENT);
			adding.add(sheet);
			adding.add(new JLabel("Row"));
			adding.add(row);
			adding.add(smallButton("Add frame", () -> {
				String file = String.valueOf(sheet.getSelectedItem());
				if (!file.isEmpty() && !row.getText().isEmpty()) {
					area.append((area.getText().isBlank() ? "" : "\n") + file + ": " + row.getText());
				}
			}));
			fullWidth(adding);
			panel.add(adding);
		}

		@Override
		public JComponent component() {
			return panel;
		}

		@Override
		public void collect(Map<String, String> values) {
			// Kept even when blank, so a level with no frames still counts as a level.
			values.put(tag.key(), area.getText().strip());
		}

		@Override
		public void set(String ignored, String value) {
			area.setText(value);
		}
	}

	/** Entries chosen by name, each with its numbers, such as what a room produces. */
	private final class KeyedRowsEditor implements Editor {
		private final class Item {
			final JComboBox<String> name = new JComboBox<>();
			final Map<TagSpec.Column, JTextField> fields = new LinkedHashMap<>();
			final JPanel line = new JPanel(new BorderLayout(6, 0));
		}

		private final JPanel panel = new JPanel();
		private final JPanel lines = new JPanel();
		private final List<Item> items = new ArrayList<>();
		private final TagSpec tag;

		KeyedRowsEditor(TagSpec tag) {
			this.tag = tag;
			keyedEditors.put(tag.key(), this);
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
			lines.setAlignmentX(LEFT_ALIGNMENT);
			JButton add = smallButton("Add an entry", this::addItem);
			add.setAlignmentX(LEFT_ALIGNMENT);
			panel.add(lines);
			panel.add(add);
		}

		private Item addItem(String name) {
			Item item = addItem();
			choose(item.name, name);
			return item;
		}

		private void removeAll() {
			items.clear();
			lines.removeAll();
			layoutChanged();
		}

		private Item addItem() {
			Item item = new Item();
			item.name.addItem("");
			index.options(tag.reference()).forEach(item.name::addItem);
			item.name.addActionListener(e -> {
				// A file can only hold an entry once, and two rows naming it would overwrite each other.
				Object chosen = item.name.getSelectedItem();
				if (!"".equals(chosen) && items.stream().anyMatch(other -> other != item && chosen.equals(other.name.getSelectedItem()))) {
					item.name.setSelectedItem("");
				}
				revalidateRows();
			});
			JPanel right = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
			for (TagSpec.Column column : tag.shape().columns()) {
				JTextField field = new JTextField(column.absent() != null && column.absent().length() > 5 ? 8 : 5);
				restrict(field, text -> text.matches("-?\\d*\\.?\\d*"));
				listen(field);
				if (column.absent() != null) {
					field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, column.absent());
				}
				field.setToolTipText(column.label() + ": " + plainNumber(column.min()) + " to " + plainNumber(column.max()) + (column.absent() == null ? "" : ". If blank: " + column.absent()));
				right.add(new JLabel(column.label()));
				right.add(field);
				item.fields.put(column, field);
			}
			right.add(smallButton("Remove", () -> {
				items.remove(item);
				lines.remove(item.line);
				lines.revalidate();
				layoutChanged();
			}));
			item.line.add(item.name, BorderLayout.CENTER);
			item.line.add(right, BorderLayout.SOUTH);
			item.line.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
			item.line.setAlignmentX(LEFT_ALIGNMENT);
			fullWidth(item.line);
			items.add(item);
			lines.add(item.line);
			lines.revalidate();
			layoutChanged();
			return item;
		}

		@Override
		public JComponent component() {
			return panel;
		}

		@Override
		public void collect(Map<String, String> values) {
			for (Item item : items) {
				String chosen = String.valueOf(item.name.getSelectedItem());
				if (!chosen.isEmpty()) {
					item.fields.forEach((column, field) -> {
						// A needed number that's still blank is kept, so the checker can say it's needed.
						if (!field.getText().isEmpty() || column.required()) {
							values.put(column.key() == null ? tag.key() + "." + chosen : tag.key() + "." + chosen + "." + column.key(), field.getText());
						}
					});
				}
			}
		}

		@Override
		public void set(String key, String value) {
			String rest = key.substring(tag.key().length() + 1);
			int dot = rest.indexOf('.');
			String row = dot < 0 ? rest : rest.substring(0, dot);
			Item item = items.stream().filter(i -> row.equals(i.name.getSelectedItem())).findFirst().orElse(null);
			if (item == null) {
				item = addItem(row);
			}
			String columnKey = dot < 0 ? null : rest.substring(dot + 1);
			item.fields.forEach((column, field) -> {
				if (java.util.Objects.equals(column.key(), columnKey)) {
					field.setText(value);
				}
			});
		}
	}

	private static String plainNumber(double value) {
		return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
	}

	/** A list of blocks with the same tags. A fixed list can't be added to or removed from. */
	private final class BlockListEditor implements Editor {
		private final JPanel panel = new JPanel();
		private final JPanel blocks = new JPanel();
		private final List<NestedBlockEditor> entries = new ArrayList<>();
		private final TagSpec tag;

		BlockListEditor(TagSpec tag) {
			this.tag = tag;
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			blocks.setLayout(new BoxLayout(blocks, BoxLayout.Y_AXIS));
			blocks.setAlignmentX(LEFT_ALIGNMENT);
			panel.add(blocks);
			if (tag.shape().fixedCount() == 0) {
				JButton add = smallButton("Add a " + entryWord().toLowerCase(java.util.Locale.ROOT), () -> {
					addBlock();
					layoutChanged();
				});
				add.setAlignmentX(LEFT_ALIGNMENT);
				panel.add(add);
			} else {
				for (int i = 0; i < tag.shape().fixedCount(); i++) {
					addBlock();
				}
			}
		}

		/** What one block of this list is called on the form. */
		private String entryWord() {
			return tag.leafName().equals("INDUSTRIES") ? "Recipe" : "Level";
		}

		private void addBlock() {
			int i = entries.size();
			boolean fixed = tag.shape().fixedCount() > 0;
			JPanel block = new JPanel();
			block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
			block.setAlignmentX(LEFT_ALIGNMENT);
			block.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
			JPanel heading = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
			heading.setAlignmentX(LEFT_ALIGNMENT);
			JLabel label = new JLabel((fixed ? "Block " : entryWord() + " ") + (i + 1));
			Theme.style(label, "semibold");
			heading.add(label);
			if (!fixed) {
				heading.add(smallButton("Remove", () -> removeBlock(i)));
			}
			fullWidth(heading);
			block.add(heading);
			boolean named = !fixed || i < tag.shape().namedSlots();
			NestedBlockEditor editor = new NestedBlockEditor(named ? tag.nestedAt(i) : List.of());
			entries.add(editor);
			if (named) {
				block.add(editor.component());
			} else {
				block.add(new WrappedText("The game names this one itself, so nothing is needed here.", Theme.Tone.HELP));
			}
			blocks.add(block);
		}

		/** Later blocks move up a place, so they're rebuilt with what was entered in them. */
		private void removeBlock(int gone) {
			Map<String, String> kept = new LinkedHashMap<>();
			collect(kept);
			int count = entries.size();
			entries.clear();
			blocks.removeAll();
			nestedGhosts.keySet().removeIf(key -> key.startsWith(tag.key() + "."));
			for (int i = 0; i < count - 1; i++) {
				addBlock();
			}
			String prefix = tag.key() + ".";
			kept.forEach((key, value) -> {
				String rest = key.substring(prefix.length());
				int at = Integer.parseInt(rest.substring(0, rest.indexOf('.')));
				if (at != gone) {
					int moved = at > gone ? at - 1 : at;
					set(prefix + moved + rest.substring(rest.indexOf('.')), value);
				}
			});
			layoutChanged();
		}

		@Override
		public JComponent component() {
			return panel;
		}

		@Override
		public void collect(Map<String, String> values) {
			entries.forEach(editor -> editor.collect(values));
		}

		@Override
		public void set(String key, String value) {
			String rest = key.substring(tag.key().length() + 1);
			int at = Integer.parseInt(rest.substring(0, rest.indexOf('.')));
			while (entries.size() <= at && tag.shape().fixedCount() == 0) {
				addBlock();
			}
			entries.get(at).set(key, value);
		}
	}

	private Map<String, String> values() {
		Map<String, String> values = new LinkedHashMap<>();
		rows.values().forEach(row -> row.editor.collect(values));
		soundEditors.forEach((key, choice) -> {
			String chosen = String.valueOf(choice.getSelectedItem());
			if (!chosen.isEmpty()) {
				values.put(key, chosen);
			}
		});
		return values;
	}

	/** Shows what the core says about every tag, and allows Save only when nothing blocks it. */
	private void revalidateRows() {
		if (schema == null || filling) {
			return;
		}
		Map<String, String> entered = values();
		AssetForm.Status status = AssetForm.status(schema, index, entered, opened == null ? null : opened.scope(entered, keptKeys), handbook.versionNote(gameVersion), producerWarning,
				opened != null && opened.comments(), opened == null ? java.util.List.of() : opened.misplacedText(), keptKeys, keptWritten);
		formRows.values().forEach(form -> form.setProblem(null, null));
		attention.clear();
		attention.addAll(status.attention());
		List<String> callouts = new ArrayList<>(status.callouts());
		if (initialisedNote != null) {
			callouts.add(initialisedNote);
		}
		// Rebuilding the same notes puts new text areas in the form, and each one asks the form to scroll to it.
		if (!callouts.equals(shownNotes)) {
			shownNotes.clear();
			notes.removeAll();
			callouts.forEach(this::addCallout);
			notes.revalidate();
			notes.repaint();
		}
		boolean unfolded = false;
		for (AssetChecker.Finding finding : status.findings()) {
			if (finding.tag() != null) {
				for (Map.Entry<String, java.util.function.BooleanSupplier> list : collapsed.entrySet()) {
					if (finding.tag().equals(list.getKey()) || finding.tag().startsWith(list.getKey() + ".")) {
						unfolded |= list.getValue().getAsBoolean();
					}
				}
			}
			if (finding.severity() != AssetChecker.Severity.MISSING && finding.tag() != null) {
				String shownAs = nearestRow(finding.tag());
				if (shownAs != null && formRows.get(shownAs).problemText().isBlank()) {
					formRows.get(shownAs).setProblem(finding.message(), finding.severity() == AssetChecker.Severity.ERROR ? Theme.Tone.ERROR : Theme.Tone.WARNING);
				}
			}
		}
		if (!busy) {
			Theme.tone(message, Theme.Tone.HELP);
			// A problem may be scrolled out of sight, so the reason Save is off is always shown here.
			message.setText(status.summary().isEmpty() ? " " : status.summary());
		}
		if (unfolded) {
			revalidate();
			repaint();
		}
		saveButton.setEnabled(status.canSave() && !busy);
		showProblem.setVisible(!attention.isEmpty());
		notes.setVisible(!shownNotes.isEmpty());
	}

	/** A note that stays at the top of the form, because it can change what the file does. */
	private void addCallout(String text) {
		shownNotes.add(text);
		WrappedText note = WrappedText.callout(text);
		if (notes.getComponentCount() > 0) {
			notes.add(Box.createVerticalStrut(6));
		}
		notes.add(note);
	}

	public void save() {
		if (!saveButton.isEnabled()) {
			return;
		}
		Map<String, String> values = values();
		Path game = gameFolder.get();
		Path root = mod.root();
		AssetType savingType = type;
		AssetSchema savingSchema = schema;
		GameAssetIndex savingIndex = index;
		String name = assetName;
		AssetEditing.Opened editing = opened;
		Map<String, String> startedWith = baseline;
		String look = appearance;
		boolean acceptDropped = false;
		if (editing != null) {
			List<String> dropped = AssetCreator.droppedBy(editing, startedWith, values);
			if (!dropped.isEmpty()) {
				acceptDropped = discardPrompt.test("Saving will remove " + String.join(", ", dropped) + ". These aren't on this form, and the number of blocks they're in has changed, so they can't be kept in the right place. Save anyway?");
				if (!acceptDropped) {
					return;
				}
			}
		}
		boolean accepted = acceptDropped;
		busy = true;
		saveButton.setEnabled(false);
		Theme.tone(message, Theme.Tone.HELP);
		message.setText("Checking with the game's parser...");
		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				try (GameParser parser = GameParser.open(GameInstall.jarIn(game))) {
					if (editing == null) {
						AssetCreator.create(root, savingType, savingSchema, name, values, look, parser, savingIndex);
					} else {
						AssetCreator.save(editing, startedWith, values, accepted, parser, savingIndex);
					}
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
					revalidateRows();
					Theme.tone(message, Theme.Tone.ERROR);
					message.setText(e.getCause().getMessage());
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
		if (opened != null) {
			return values().equals(baseline) || discardPrompt.test("Discard your changes to this asset? Its files haven't been changed.");
		}
		// A sound usually starts filled in, so it doesn't count as something entered.
		boolean entered = values().entrySet().stream().anyMatch(value -> !value.getKey().startsWith("SOUND.") && !value.getValue().isEmpty() && !value.getValue().equals(startingValues.get(value.getKey())));
		return !entered || discardPrompt.test("Discard this new asset? Nothing has been saved yet.");
	}

	/** Replaces the question about losing work, so tests can answer it. */
	public void setDiscardPrompt(Predicate<String> prompt) {
		discardPrompt = prompt;
	}
}
