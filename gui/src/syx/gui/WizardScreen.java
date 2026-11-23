package syx.gui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

import com.formdev.flatlaf.FlatClientProperties;

import syx.core.GameAssetIndex;
import syx.core.HandbookStore;
import syx.core.ModEntry;
import syx.core.PortraitLayers;
import syx.core.wizard.Wizard;
import syx.core.wizard.WizardEngine;
import syx.core.wizard.WizardQuestion;
import syx.core.wizard.WizardStore;
import syx.core.wizard.Wizards;

/**
 * The Asset Creation Wizard, as a conversation: the wizard asks, the person answers with a button or by typing,
 * and each answer is saved into the asset's files as it's given. It looks like a chat on a phone: a centred
 * column of bubbles, and the place to answer pinned at the bottom. Clicking one of the wizard's questions turns it
 * over to show the handbook's words for what it's about.
 *
 * <p>Everything it shows comes from {@link WizardQuestion}, which agents get too, and every answer goes through
 * {@link WizardEngine}, so the two can't differ. Nothing here decides what's valid.
 */
public final class WizardScreen extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final int COLUMN = 720;
	/** Up to this many names, an amounts field shows a row for each. Past it, rows are added from a list. */
	private static final int SHORT_LIST = 8;
	private static final int AVATAR = 30;

	private final transient Supplier<Path> gameFolder;
	private final transient HandbookStore handbook;
	private final transient AssetEditorScreen.LinkOpener linkOpener;
	private final transient Consumer<Path> openInEditor;
	private final Messages messages = new Messages();
	private final JScrollPane scroll = new JScrollPane(messages);
	private final JPanel replies = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 6));
	private final JPanel textEntry = new JPanel(new BorderLayout(8, 0));
	private final JTextField entry = new JTextField();
	private final JLabel unit = new JLabel();
	private final JButton send = new JButton("Send");
	private final JButton back = new JButton("Back");
	private final JButton skip = new JButton("Skip");
	private final JButton startOver = new JButton("All wizards");
	private final JLabel progress = new JLabel(" ");
	private final JPanel composer = new JPanel();
	private final transient List<FieldEditor> editors = new ArrayList<>();
	private final transient List<Runnable> continues = new ArrayList<>();
	private transient ModEntry mod;
	private transient WizardQuestion question;
	private transient ChatBubble card;
	private transient ChatBubble typing;
	private WidthBand band = WidthBand.WIDE;
	private boolean busy;
	private String lastError;
	/** What's on screen when no wizard is: the list to start from. */
	private boolean onStart;

	WizardScreen(Supplier<Path> gameFolder, HandbookStore handbook, AssetEditorScreen.LinkOpener linkOpener, Consumer<Path> openInEditor) {
		super(new BorderLayout());
		this.gameFolder = gameFolder;
		this.handbook = handbook;
		this.linkOpener = linkOpener;
		this.openInEditor = openInEditor;
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getViewport().addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				messages.revalidate();
				composer.revalidate();
			}
		});
		add(scroll, BorderLayout.CENTER);
		add(buildComposer(), BorderLayout.SOUTH);
	}

	private JComponent buildComposer() {
		entry.putClientProperty(FlatClientProperties.STYLE, "arc: 999; margin: 9,16,9,16; font: +2");
		entry.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Type your answer");
		entry.addActionListener(e -> sendTyped());
		send.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
		send.putClientProperty(FlatClientProperties.STYLE, "margin: 9,22,9,22; font: +2");
		Theme.style(send, "primary");
		send.addActionListener(e -> sendTyped());
		unit.putClientProperty("html.disable", Boolean.TRUE);
		Theme.tone(unit, Theme.Tone.HELP);
		JPanel ends = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		ends.setOpaque(false);
		ends.add(unit);
		ends.add(send);
		textEntry.setOpaque(false);
		textEntry.add(entry, BorderLayout.CENTER);
		textEntry.add(ends, BorderLayout.EAST);
		replies.setOpaque(false);
		for (JButton button : List.of(back, skip, startOver)) {
			button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
		}
		back.setToolTipText(PlainTip.of("Go back to the last question. The first two can't be changed once the files are made."));
		skip.setToolTipText(PlainTip.of("Leave this as it is and go on."));
		startOver.setToolTipText(PlainTip.of("Leave this wizard where it is and see all of them. It can be continued later."));
		back.addActionListener(e -> backAction());
		skip.addActionListener(e -> skipAction());
		startOver.addActionListener(e -> showStart());
		Theme.style(progress, "small");
		Theme.tone(progress, Theme.Tone.HELP);
		JPanel tools = new JPanel(new BorderLayout());
		tools.setOpaque(false);
		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
		left.setOpaque(false);
		left.add(back);
		left.add(skip);
		left.add(startOver);
		tools.add(left, BorderLayout.WEST);
		tools.add(progress, BorderLayout.EAST);
		composer.setLayout(new BorderLayout(0, 6));
		composer.setOpaque(false);
		composer.add(tools, BorderLayout.NORTH);
		JPanel body = new JPanel(new BorderLayout());
		body.setOpaque(false);
		body.add(replies, BorderLayout.NORTH);
		body.add(textEntry, BorderLayout.SOUTH);
		composer.add(body, BorderLayout.CENTER);
		JPanel holder = new JPanel(new Centred()) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void paintComponent(Graphics g) {
				super.paintComponent(g);
				g.setColor(Color.decode(Theme.palette(Theme.current()).border()));
				g.drawLine(0, 0, getWidth(), 0);
			}
		};
		holder.setBorder(BorderFactory.createEmptyBorder(8, 0, 12, 0));
		holder.add(composer);
		return holder;
	}

	void setBand(WidthBand value) {
		band = value;
		messages.revalidate();
		messages.repaint();
	}

	/** Shows the list of wizards for a mod, with the ones it has unfinished. */
	void open(ModEntry entry) {
		mod = entry;
		portraitMode = false;
		showStart();
	}

	/** Whether this is showing the Portrait Design Wizard, which has its own menu item, rather than the asset wizards. */
	private boolean portraitMode;
	private transient Consumer<String> handOver = race -> {
	};
	private transient Consumer<String> openInSim = race -> {
	};

	/**
	 * @param handOver called when the race wizard hands a race to the portrait wizard, so the menu shows the portrait wizard
	 * @param openInSim opens a race in the Race Portrait Sim
	 */
	void setPortraitLinks(Consumer<String> handOver, Consumer<String> openInSim) {
		this.handOver = handOver;
		this.openInSim = openInSim;
	}

	/** Shows the Portrait Design Wizard for a mod: which race, or straight to a race's questions. */
	void openPortraits(ModEntry entry, String race) {
		mod = entry;
		portraitMode = true;
		onStart = false;
		clearConversation();
		ModEntry forMod = entry;
		if (race == null) {
			run(null, () -> engine().begin(forMod, Wizards.PORTRAIT.id()));
		} else {
			run(null, () -> engine().start(forMod, Wizards.PORTRAIT.id(), race));
		}
	}

	private WizardEngine engine() {
		return new WizardEngine(gameFolder.get(), handbook, WizardStore.openDefault());
	}

	// ---- The start: which wizard, or carry on with one

	private record Start(List<WizardEngine.Offer> offers, List<WizardStore.Session> unfinished) {
	}

	private void showStart() {
		if (mod == null || busy) {
			return;
		}
		if (portraitMode) {
			openPortraits(mod, null);
			return;
		}
		ModEntry forMod = mod;
		question = null;
		onStart = true;
		clearConversation();
		setComposer(null);
		busy = true;
		showTyping();
		new SwingWorker<Start, Void>() {
			@Override
			protected Start doInBackground() {
				WizardEngine engine = engine();
				return new Start(engine.offers(), engine.unfinished(forMod));
			}

			@Override
			protected void done() {
				busy = false;
				hideTyping();
				if (forMod != mod) {
					return;
				}
				try {
					renderStart(get());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					say(Theme.Tone.ERROR, "The wizards couldn't be listed: " + e.getCause().getMessage());
				}
			}
		}.execute();
	}

	private void renderStart(Start start) {
		clearConversation();
		continues.clear();
		ChatBubble hello = wizardBubble(Wizards.START_PROMPT,
				"I'll ask you about it one question at a time, and save each answer into your mod as you go. You can stop at any point and carry on later.", List.of(), List.of(), null);
		addMessage(hello, true);
		replies.removeAll();
		List<String> notes = new ArrayList<>();
		for (WizardEngine.Offer offer : start.offers()) {
			// The portrait wizard has its own menu item, and works on a race that's already made.
			if (offer.definition() == Wizards.PORTRAIT) {
				continue;
			}
			if (offer.available()) {
				replies.add(reply(offer.definition().title(), () -> begin(offer.definition())));
			} else {
				notes.add(offer.definition().title() + " isn't available: " + offer.unavailableReason());
			}
		}
		if (!notes.isEmpty()) {
			addMessage(note(String.join("\n", notes), Theme.Tone.WARNING), false);
		}
		List<WizardStore.Session> assetSessions = start.unfinished().stream().filter(session -> Wizards.find(session.wizard()) != null).toList();
		if (!assetSessions.isEmpty()) {
			ChatBubble unfinished = new ChatBubble(ChatBubble.Side.CARD);
			WrappedText heading = new WrappedText("Carry on where you left off", null);
			Theme.style(heading, "semibold");
			unfinished.addFront(heading);
			for (WizardStore.Session session : assetSessions) {
				Wizard.Definition definition = Wizards.find(session.wizard());
				int at = definition.indexOf(session.step()) + 1;
				unfinished.addFront(new WrappedText(session.display() + " (" + definition.title().toLowerCase(Locale.ROOT) + ", question " + Math.max(at, 1) + " of " + definition.steps().size() + ")",
						Theme.Tone.HELP));
				JPanel buttons = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 0));
				buttons.setOpaque(false);
				Runnable carryOn = () -> resume(session);
				continues.add(carryOn);
				buttons.add(reply("Continue", carryOn));
				JButton forget = new JButton("Forget");
				forget.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
				forget.setToolTipText(PlainTip.of("Stop offering to carry on. What it saved stays in the mod."));
				forget.addActionListener(e -> forget(session));
				buttons.add(forget);
				unfinished.addFront(buttons);
			}
			addMessage(unfinished, false);
		}
		setComposer("replies");
		showProgress(null);
		scrollToEnd();
	}

	private void begin(Wizard.Definition definition) {
		ModEntry forMod = mod;
		run(definition.title(), () -> engine().begin(forMod, definition.id()));
	}

	private void resume(WizardStore.Session session) {
		ModEntry forMod = mod;
		run(null, () -> engine().question(forMod, session.wizard(), session.stem()));
	}

	private void forget(WizardStore.Session session) {
		ModEntry forMod = mod;
		busy = true;
		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				engine().forget(forMod, session.wizard(), session.stem());
				return null;
			}

			@Override
			protected void done() {
				busy = false;
				showStart();
			}
		}.execute();
	}

	// ---- Asking and answering

	/** Runs a call to the engine off the event thread, showing the person's answer and the wizard typing meanwhile. */
	private void run(String echo, Callable<WizardQuestion> call) {
		if (busy) {
			return;
		}
		busy = true;
		lastError = null;
		setControlsEnabled(false);
		if (echo != null && !echo.isBlank()) {
			ChatBubble said = personBubble(echo);
			addMessage(said, false);
			said.fadeIn();
		}
		showTyping();
		ModEntry forMod = mod;
		new SwingWorker<WizardQuestion, Void>() {
			@Override
			protected WizardQuestion doInBackground() throws Exception {
				return call.call();
			}

			@Override
			protected void done() {
				busy = false;
				hideTyping();
				setControlsEnabled(true);
				if (forMod != mod) {
					return;
				}
				try {
					render(get());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					if (e.getCause() instanceof WizardEngine.Refused refused) {
						refused(refused);
					} else {
						lastError = e.getCause().getMessage();
						say(Theme.Tone.ERROR, "Something went wrong, and nothing was saved: " + e.getCause().getMessage());
					}
				}
			}
		}.execute();
	}

	/** Draws the whole conversation from a question: what's been answered, then what's asked now. */
	private void render(WizardQuestion asked) {
		question = asked;
		onStart = false;
		clearConversation();
		editors.clear();
		card = null;
		boolean avatar = true;
		for (WizardQuestion.Exchange exchange : asked.history()) {
			addMessage(wizardBubble(exchange.prompt(), null, exchange.handbook(), List.of(), null), avatar);
			if (!exchange.answer().isBlank()) {
				addMessage(personBubble(exchange.answer()), false);
			}
		}
		List<ChatBubble> fresh = new ArrayList<>();
		if (asked.note() != null) {
			ChatBubble note = note(asked.note(), Theme.Tone.HELP);
			addMessage(note, true);
			fresh.add(note);
		}
		boolean finished = asked.kind().equals("finished");
		ChatBubble now = finished ? wizardBubble("All done.", null, List.of(), List.of(), null)
				: wizardBubble(asked.prompt(), asked.help(), asked.handbook(), asked.findings(), asked.refused());
		addMessage(now, asked.note() == null);
		fresh.add(now);
		framePicker = null;
		card = switch (asked.kind()) {
		case "fields" -> fieldsCard(asked);
		case "layers" -> layersCard(asked);
		case "frames" -> framesCard(asked);
		case "addons" -> addonsCard(asked);
		default -> null;
		};
		if (card != null) {
			addMessage(card, false);
			fresh.add(card);
		}
		replies.removeAll();
		boolean portraits = Wizards.isPortrait(asked.wizard());
		switch (asked.kind()) {
		case "name", "text" -> setComposer("text");
		case "race" -> {
			for (WizardQuestion.ChoiceView choice : asked.choices()) {
				replies.add(reply(choice.label(), () -> startPortraits(choice.id())));
			}
			setComposer("replies");
		}
		case "layers" -> {
			// Each layer's own buttons are on the card; what's for the whole portrait is here.
			for (WizardQuestion.ChoiceView choice : asked.choices()) {
				if (choice.id().equals("add") || choice.id().equals("next")) {
					replies.add(reply(choice.label(), () -> answerChoice(choice)));
				}
			}
			setComposer("replies");
		}
		case "frames", "addons" -> setComposer(null);
		case "portrait_done" -> {
			replies.add(reply("Finish", () -> finish(false)));
			replies.add(reply("Finish and open in the Race Portrait Sim", () -> finish(true)));
			setComposer("replies");
		}
		case "finished" -> {
			if (portraits) {
				replies.add(reply("Open in the Race Portrait Sim", () -> openInSim.accept(asked.name())));
				replies.add(reply("Design another race's portraits", this::showStart));
				setComposer("replies");
				if (openAfterFinish != null) {
					openAfterFinish = null;
					String race = asked.name();
					SwingUtilities.invokeLater(() -> openInSim.accept(race));
				}
			} else if (asked.handover() != null) {
				// The race wizard handing its race to the portrait wizard: carry on there straight away.
				WizardQuestion.Handover next = asked.handover();
				setComposer(null);
				SwingUtilities.invokeLater(() -> {
					handOver.accept(next.name());
					openPortraits(mod, next.name());
				});
			} else {
				replies.add(reply("Open in the editor", this::openAsset));
				replies.add(reply("Make something else", this::showStart));
				setComposer("replies");
				if (openAfterFinish != null) {
					openAfterFinish = null;
					SwingUtilities.invokeLater(this::openAsset);
				}
			}
		}
		case "number" -> {
			entry.setText(asked.number().value() == null ? "" : asked.number().value());
			unit.setText(asked.number().unit());
			setComposer("text");
		}
		case "look", "choice" -> {
			for (WizardQuestion.ChoiceView choice : asked.choices()) {
				JButton button = reply(choice.label(), () -> answerChoice(choice));
				if (choice.current()) {
					Theme.style(button, "primary");
					button.setToolTipText(PlainTip.of("This is what " + asked.display() + " has now."));
				}
				replies.add(button);
			}
			setComposer("replies");
		}
		case "done" -> {
			replies.add(reply("Finish", () -> finish(false)));
			replies.add(reply("Finish and open in the editor", () -> finish(true)));
			setComposer("replies");
		}
		default -> setComposer(null);
		}
		if (!asked.kind().equals("name") && !asked.kind().equals("text")) {
			entry.setText(asked.kind().equals("number") ? entry.getText() : "");
		} else {
			entry.setText("");
			unit.setText("");
		}
		startOver.setText(portraits ? "All races" : "All wizards");
		back.setVisible(asked.canGoBack());
		skip.setVisible(asked.canSkip());
		startOver.setVisible(true);
		showProgress(finished ? null : asked);
		fresh.forEach(ChatBubble::fadeIn);
		scrollToEnd();
		if (asked.kind().equals("name") || asked.kind().equals("number") || asked.kind().equals("text")) {
			SwingUtilities.invokeLater(entry::requestFocusInWindow);
		}
	}

	private void startPortraits(String race) {
		ModEntry forMod = mod;
		run(race, () -> engine().start(forMod, Wizards.PORTRAIT.id(), race));
	}

	/** An answer that wasn't taken. A card of fields keeps what was typed, so nothing has to be entered again. */
	private void refused(WizardEngine.Refused refused) {
		WizardQuestion again = refused.question();
		lastError = refused.getMessage();
		if (again == null) {
			say(Theme.Tone.ERROR, refused.getMessage());
			return;
		}
		if (question != null && card != null && List.of("fields", "frames", "addons").contains(again.kind()) && again.step().equals(question.step())) {
			ChatBubble said = wizardBubble(refused.getMessage(), null, List.of(), again.findings(), null);
			messages.add(said, messages.getComponentCount() - 1);
			said.fadeIn();
			for (FieldEditor editor : editors) {
				editor.showProblems(again.findings());
			}
			messages.revalidate();
			scrollToEnd();
			return;
		}
		render(again);
	}

	private void answerChoice(WizardQuestion.ChoiceView choice) {
		WizardQuestion asked = question;
		ModEntry forMod = mod;
		run(choice.label(), () -> engine().answer(forMod, asked.wizard(), asked.name(), Map.of("choice", choice.id())));
	}

	private void sendTyped() {
		WizardQuestion asked = question;
		String typed = entry.getText();
		if (asked == null || busy || typed.isBlank()) {
			return;
		}
		ModEntry forMod = mod;
		entry.setText("");
		if (asked.kind().equals("name")) {
			run(typed.strip(), () -> engine().start(forMod, asked.wizard(), typed));
		} else if (asked.kind().equals("text")) {
			run(typed.strip(), () -> engine().answer(forMod, asked.wizard(), asked.name(), Map.of("value", typed.strip())));
		} else if (asked.kind().equals("number")) {
			run(typed.strip() + " " + asked.number().unit(), () -> engine().answer(forMod, asked.wizard(), asked.name(), Map.of("value", typed.strip())));
		}
	}

	private void saveFields() {
		WizardQuestion asked = question;
		if (asked == null || busy) {
			return;
		}
		Map<String, Object> values = new LinkedHashMap<>();
		editors.forEach(editor -> editor.collect(values));
		ModEntry forMod = mod;
		run(values.isEmpty() ? "No changes" : null, () -> engine().answer(forMod, asked.wizard(), asked.name(), Map.of("values", values)));
	}

	private void skipAction() {
		WizardQuestion asked = question;
		if (asked == null) {
			return;
		}
		ModEntry forMod = mod;
		run("Skip", () -> engine().skip(forMod, asked.wizard(), asked.name()));
	}

	private void backAction() {
		WizardQuestion asked = question;
		if (asked == null) {
			return;
		}
		ModEntry forMod = mod;
		run(null, () -> engine().back(forMod, asked.wizard(), asked.name()));
	}

	private void finish(boolean open) {
		WizardQuestion asked = question;
		ModEntry forMod = mod;
		openAfterFinish = open ? asked : null;
		run(open ? "Finish and open it" : "Finish", () -> engine().answer(forMod, asked.wizard(), asked.name(), Map.of()));
	}

	private transient WizardQuestion openAfterFinish;

	private void openAsset() {
		WizardQuestion asked = question;
		if (asked == null || asked.name() == null) {
			return;
		}
		ModEntry forMod = mod;
		busy = true;
		new SwingWorker<Path, Void>() {
			@Override
			protected Path doInBackground() throws Exception {
				return engine().assetFile(forMod, asked.wizard(), asked.name());
			}

			@Override
			protected void done() {
				busy = false;
				try {
					openInEditor.accept(get());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					say(Theme.Tone.ERROR, "It couldn't be opened: " + e.getCause().getMessage());
				}
			}
		}.execute();
	}

	// ---- Bubbles

	private ChatBubble wizardBubble(String prompt, String help, List<WizardQuestion.HandbookEntry> entries, List<WizardQuestion.Problem> problems, String refusedText) {
		ChatBubble bubble = new ChatBubble(ChatBubble.Side.WIZARD);
		WrappedText asked = new WrappedText(prompt, null);
		Theme.style(asked, "semibold");
		bubble.addFront(asked);
		if (help != null && !help.isBlank()) {
			bubble.addFront(new WrappedText(help, Theme.Tone.HELP));
		}
		if (refusedText != null) {
			bubble.addFront(new WrappedText(refusedText, Theme.Tone.ERROR));
		}
		for (WizardQuestion.Problem problem : problems) {
			bubble.addFront(new WrappedText((problem.blocksSaving() ? "Must be fixed: " : "Worth knowing: ") + problem.message(), problem.blocksSaving() ? Theme.Tone.ERROR : Theme.Tone.WARNING));
		}
		if (!entries.isEmpty()) {
			WrappedText from = new WrappedText("From the handbook", Theme.Tone.HEADING);
			Theme.style(from, "small");
			bubble.addBack(from);
			for (WizardQuestion.HandbookEntry entry : entries) {
				WrappedText label = new WrappedText(entry.label(), null);
				Theme.style(label, "semibold");
				bubble.addBack(label);
				bubble.addBack(new WrappedText(entry.description(), null));
				if (entry.example() != null && !entry.example().isBlank()) {
					WrappedText example = new WrappedText(entry.example(), null);
					Theme.style(example, "monospaced");
					bubble.addBack(example);
				}
				if (entry.unverified()) {
					bubble.addBack(new WrappedText("The handbook marks this as not confirmed yet.", Theme.Tone.WARNING));
				}
				if (entry.url() != null) {
					JButton more = new JButton("Read more");
					more.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
					Theme.tone(more, Theme.Tone.HEADING);
					more.setToolTipText(PlainTip.of("Open this in the handbook, in your browser."));
					more.addActionListener(e -> {
						try {
							linkOpener.open(entry.url());
						} catch (java.io.IOException | RuntimeException failure) {
							say(Theme.Tone.ERROR, "The handbook couldn't be opened: " + failure.getMessage());
						}
					});
					bubble.addBack(more);
				}
			}
			WrappedText hint = new WrappedText("Click again to go back to the question.", Theme.Tone.GHOST);
			Theme.style(hint, "small");
			bubble.addBack(hint);
			bubble.setOnFlip(() -> {
				messages.revalidate();
				messages.repaint();
			});
		}
		return bubble;
	}

	private static ChatBubble personBubble(String text) {
		ChatBubble bubble = new ChatBubble(ChatBubble.Side.PERSON);
		WrappedText said = new WrappedText(text, null);
		Theme.style(said, "onAccent");
		bubble.addFront(said);
		return bubble;
	}

	private static ChatBubble note(String text, Theme.Tone tone) {
		ChatBubble bubble = new ChatBubble(ChatBubble.Side.WIZARD);
		bubble.addFront(new WrappedText(text, tone));
		return bubble;
	}

	private void say(Theme.Tone tone, String text) {
		ChatBubble bubble = note(text, tone);
		addMessage(bubble, true);
		bubble.fadeIn();
		scrollToEnd();
	}

	private static JButton reply(String text, Runnable action) {
		JButton button = new JButton(text);
		button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
		button.putClientProperty(FlatClientProperties.STYLE, "margin: 9,20,9,20; font: +2");
		button.addActionListener(e -> action.run());
		return button;
	}

	private void addMessage(ChatBubble bubble, boolean avatar) {
		bubble.putClientProperty(Messages.AVATAR_KEY, avatar && bubble.side() == ChatBubble.Side.WIZARD);
		messages.add(bubble);
		messages.revalidate();
		messages.repaint();
	}

	private void clearConversation() {
		messages.removeAll();
		typing = null;
		messages.revalidate();
		messages.repaint();
	}

	private void showTyping() {
		typing = new ChatBubble(ChatBubble.Side.WIZARD);
		typing.addFront(new Dots());
		addMessage(typing, true);
		scrollToEnd();
	}

	private void hideTyping() {
		if (typing != null) {
			messages.remove(typing);
			typing = null;
			messages.revalidate();
			messages.repaint();
		}
	}

	private void setComposer(String kind) {
		replies.setVisible("replies".equals(kind));
		textEntry.setVisible("text".equals(kind));
		composer.revalidate();
		composer.repaint();
	}

	private void setControlsEnabled(boolean enabled) {
		for (Component component : replies.getComponents()) {
			component.setEnabled(enabled);
		}
		entry.setEnabled(enabled);
		send.setEnabled(enabled);
		back.setEnabled(enabled);
		skip.setEnabled(enabled);
		startOver.setEnabled(enabled);
		if (card != null) {
			setEnabledWithin(card, enabled);
		}
	}

	private static void setEnabledWithin(Container container, boolean enabled) {
		for (Component child : container.getComponents()) {
			if (child instanceof AbstractButton || child instanceof javax.swing.text.JTextComponent text && text.isEditable()) {
				child.setEnabled(enabled);
			}
			if (child instanceof Container inner) {
				setEnabledWithin(inner, enabled);
			}
		}
	}

	private void showProgress(WizardQuestion asked) {
		if (asked == null) {
			progress.setText(" ");
			back.setVisible(false);
			skip.setVisible(false);
			startOver.setVisible(!onStart);
			return;
		}
		progress.setText((asked.display() == null ? "" : asked.display() + "  ·  ") + "Question " + asked.position() + " of " + asked.total());
	}

	private void scrollToEnd() {
		SwingUtilities.invokeLater(() -> {
			messages.revalidate();
			SwingUtilities.invokeLater(() -> messages.scrollRectToVisible(new Rectangle(0, Math.max(0, messages.getHeight() - 1), 1, 1)));
		});
	}

	// ---- The card of fields

	private ChatBubble fieldsCard(WizardQuestion asked) {
		ChatBubble bubble = new ChatBubble(ChatBubble.Side.CARD);
		for (WizardQuestion.FieldView view : asked.fields()) {
			FieldEditor editor = new FieldEditor(view, bubble);
			editors.add(editor);
			editor.showProblems(asked.findings());
		}
		JPanel actions = new JPanel(new WrapLayout(FlowLayout.RIGHT, 6, 0));
		actions.setOpaque(false);
		JButton save = new JButton("Save and go on");
		Theme.style(save, "primary");
		save.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
		save.addActionListener(e -> saveFields());
		actions.add(save);
		bubble.addFront(actions);
		return bubble;
	}

	// ---- The portrait wizard's cards

	private transient FramePicker framePicker;
	/** For a new layer's frames, where it's drawn, and the place each choice means. */
	private transient JComboBox<String> placeChooser;
	private transient List<Integer> placeValues = List.of();
	private transient List<PortraitLayers.FrameRef> framePicks = new ArrayList<>();
	private transient Runnable saveCard;
	private final transient Map<String, Runnable> layerButtons = new LinkedHashMap<>();
	private int previewGeneration;

	/** A portrait's layers, top first, each with what can be done to it, and the portrait as it's drawn. */
	private ChatBubble layersCard(WizardQuestion asked) {
		ChatBubble bubble = new ChatBubble(ChatBubble.Side.CARD);
		WizardQuestion.PortraitView view = asked.portrait();
		layerButtons.clear();
		JLabel picture = previewLabel();
		bubble.addFront(picture);
		loadPreview(picture, view.race(), view.portrait(), -1, null, -1);
		if (view.layers().isEmpty()) {
			bubble.addFront(new WrappedText("No layers yet.", Theme.Tone.HELP));
		} else {
			WrappedText order = new WrappedText("From the top down. Each layer is drawn over the ones below it.", Theme.Tone.HELP);
			Theme.style(order, "small");
			bubble.addFront(order);
		}
		List<WizardQuestion.LayerView> layers = new ArrayList<>(view.layers());
		java.util.Collections.reverse(layers);
		for (WizardQuestion.LayerView layer : layers) {
			WrappedText title = new WrappedText((layer.index() + 1) + ". " + (layer.name() != null ? layer.name() : sheetsOf(layer.frames()) + " (no name)"), null);
			Theme.style(title, "semibold");
			bubble.addFront(title);
			String summary = layer.summary() == null ? "" : ". " + layer.summary().substring(0, 1).toUpperCase(Locale.ROOT) + layer.summary().substring(1) + ".";
			WrappedText detail = new WrappedText(framesText(layer.frames()) + summary, Theme.Tone.HELP);
			Theme.style(detail, "small");
			bubble.addFront(detail);
			JPanel buttons = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 0));
			buttons.setOpaque(false);
			for (WizardQuestion.ChoiceView choice : asked.choices()) {
				String[] parts = choice.id().split(":");
				if (parts.length == 2 && parts[1].equals(String.valueOf(layer.index()))) {
					String text = switch (parts[0]) {
					case "frames" -> "Frames";
					case "tweak" -> "How it's drawn";
					case "up" -> "Move up";
					case "down" -> "Move down";
					case "top" -> "To the top";
					case "bottom" -> "To the bottom";
					default -> "Remove";
					};
					JButton button = new JButton(text);
					button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
					Theme.style(button, "small");
					button.getAccessibleContext().setAccessibleName(choice.label());
					Runnable action = () -> answerChoice(choice);
					button.addActionListener(e -> action.run());
					layerButtons.put(choice.id(), action);
					buttons.add(button);
				}
			}
			bubble.addFront(buttons);
		}
		return bubble;
	}

	/** The sheets a layer's frames are on, such as "Skull", for a layer with no name. */
	private static String sheetsOf(List<String> frames) {
		List<String> sheets = new ArrayList<>();
		for (String frame : frames) {
			PortraitLayers.FrameRef ref = PortraitLayers.FrameRef.parse(frame);
			if (ref != null && !sheets.contains(ref.file())) {
				sheets.add(ref.file());
			}
		}
		return sheets.isEmpty() ? "A layer" : String.join(", ", sheets);
	}

	/** Eyes: 1, Eyes: 2, Hair: 3 as "Eyes 1, 2; Hair 3". */
	private static String framesText(List<String> frames) {
		Map<String, List<String>> bySheet = new LinkedHashMap<>();
		for (String frame : frames) {
			PortraitLayers.FrameRef ref = PortraitLayers.FrameRef.parse(frame);
			if (ref != null) {
				bySheet.computeIfAbsent(ref.file(), key -> new ArrayList<>()).add(String.valueOf(ref.index()));
			}
		}
		List<String> parts = new ArrayList<>();
		bySheet.forEach((sheet, indexes) -> parts.add(sheet + " " + String.join(", ", indexes)));
		return parts.isEmpty() ? "No frames" : String.join("; ", parts);
	}

	/** A sheet to pick a layer's frames from, the frames picked so far, and the portrait with the frame under the pointer. */
	private ChatBubble framesCard(WizardQuestion asked) {
		ChatBubble bubble = new ChatBubble(ChatBubble.Side.CARD);
		WizardQuestion.PortraitView view = asked.portrait();
		WizardQuestion.FramesView frames = view.frames();
		framePicks = new ArrayList<>();
		for (String text : frames.picked()) {
			PortraitLayers.FrameRef ref = PortraitLayers.FrameRef.parse(text);
			if (ref != null) {
				framePicks.add(ref);
			}
		}
		String[] parts = asked.step().split(":", 4);
		int layer = parts.length > 2 && !parts[2].equals("new") ? Integer.parseInt(parts[2]) : -1;
		List<WizardQuestion.SheetView> usable = frames.sheets().stream().filter(sheet -> sheet.problem() == null).toList();
		JComboBox<String> sheetBox = new JComboBox<>(usable.stream().map(WizardQuestion.SheetView::name).toArray(String[]::new));
		sheetBox.setRenderer(new javax.swing.DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focused) {
				String name = value == null ? "" : String.valueOf(value);
				String source = usable.stream().filter(sheet -> sheet.name().equals(name)).map(WizardQuestion.SheetView::source).findFirst().orElse("");
				String shown = name + (source.equals("game") ? "" : source.equals("mod") ? "  (your mod)" : "  (your mod's, replacing the game's)");
				Component label = super.getListCellRendererComponent(list, shown, index, selected, focused);
				((JLabel) label).putClientProperty("html.disable", Boolean.TRUE);
				return label;
			}
		});
		sheetBox.getAccessibleContext().setAccessibleName("Sheet");
		if (frames.sheet() != null) {
			sheetBox.setSelectedItem(frames.sheet());
		}
		JPanel top = new JPanel(new BorderLayout(8, 0));
		top.setOpaque(false);
		JLabel sheetLabel = new JLabel("Sheet");
		top.add(sheetLabel, BorderLayout.WEST);
		top.add(sheetBox, BorderLayout.CENTER);
		bubble.addFront(top);
		// A new layer's place in the drawing order. The game draws layers strictly in order, so this is what puts back hair behind the head.
		List<Integer> places = new ArrayList<>();
		List<String> placeNames = new ArrayList<>();
		int count = view.layers().size();
		if (layer < 0) {
			places.add(count);
			placeNames.add("On top of everything");
			for (int i = count - 1; i >= 1; i--) {
				WizardQuestion.LayerView under = view.layers().get(i);
				places.add(i);
				placeNames.add("Under " + (i + 1) + ". " + (under.name() != null ? under.name() : sheetsOf(under.frames())));
			}
			if (count > 0) {
				places.add(0);
				placeNames.add("At the bottom, behind everything");
			}
		}
		JComboBox<String> placeBox = new JComboBox<>(placeNames.toArray(String[]::new));
		placeBox.getAccessibleContext().setAccessibleName("Where it's drawn");
		placeChooser = placeBox;
		placeValues = places;
		if (layer < 0 && count > 0) {
			JPanel placeRow = new JPanel(new BorderLayout(8, 0));
			placeRow.setOpaque(false);
			placeRow.add(new JLabel("Drawn"), BorderLayout.WEST);
			placeRow.add(placeBox, BorderLayout.CENTER);
			bubble.addFront(placeRow);
		}
		java.util.function.IntSupplier place = () -> places.isEmpty() ? Integer.MAX_VALUE : places.get(Math.max(0, placeBox.getSelectedIndex()));

		JLabel picture = previewLabel();
		WrappedText pickedText = new WrappedText(" ", null);
		JPanel side = new JPanel(new BorderLayout(10, 0));
		side.setOpaque(false);
		side.add(picture, BorderLayout.WEST);
		JPanel right = new JPanel();
		right.setLayout(new javax.swing.BoxLayout(right, javax.swing.BoxLayout.Y_AXIS));
		right.setOpaque(false);
		WrappedText how = new WrappedText("Click frames to pick them, in the order you like. The portrait shows the frame under the pointer.", Theme.Tone.HELP);
		Theme.style(how, "small");
		right.add(how);
		right.add(pickedText);
		side.add(right, BorderLayout.CENTER);
		bubble.addFront(side);

		FramePicker picker = new FramePicker();
		framePicker = picker;
		JScrollPane pickerScroll = new JScrollPane(picker) {
			private static final long serialVersionUID = 1L;

			@Override
			public Dimension getPreferredSize() {
				Dimension inner = picker.getPreferredSize();
				return new Dimension(inner.width + 16, Math.min(inner.height + 4, 420));
			}
		};
		pickerScroll.getVerticalScrollBar().setUnitIncrement(30);
		pickerScroll.setBorder(BorderFactory.createEmptyBorder());
		bubble.addFront(pickerScroll);

		Timer previewTimer = new Timer(140, null);
		previewTimer.setRepeats(false);
		int[] hovered = { -1 };
		Runnable showPreview = () -> {
			String sheet = (String) sheetBox.getSelectedItem();
			if (hovered[0] >= 0 && sheet != null) {
				loadPreview(picture, view.race(), view.portrait(), layer, List.of(new PortraitLayers.FrameRef(sheet, hovered[0])), 0, place.getAsInt());
			} else {
				loadPreview(picture, view.race(), view.portrait(), layer, framePicks.isEmpty() ? null : framePicks, framePicks.isEmpty() ? -1 : 0, place.getAsInt());
			}
		};
		previewTimer.addActionListener(e -> showPreview.run());
		placeBox.addActionListener(e -> previewTimer.restart());
		Runnable showPicks = () -> {
			String sheet = (String) sheetBox.getSelectedItem();
			List<Integer> onSheet = new ArrayList<>();
			for (PortraitLayers.FrameRef ref : framePicks) {
				onSheet.add(ref.file().equals(sheet) ? ref.index() : -1);
			}
			picker.setPicked(onSheet);
			pickedText.setText(framePicks.isEmpty() ? "Nothing picked yet." : "Picked: " + framesText(framePicks.stream().map(PortraitLayers.FrameRef::text).toList()));
			bubble.revalidate();
		};
		picker.onToggle(index -> {
			String sheet = (String) sheetBox.getSelectedItem();
			if (sheet == null) {
				return;
			}
			PortraitLayers.FrameRef ref = new PortraitLayers.FrameRef(sheet, index);
			if (!framePicks.remove(ref)) {
				framePicks.add(ref);
			}
			showPicks.run();
			previewTimer.restart();
		});
		picker.onHover(index -> {
			hovered[0] = index;
			previewTimer.restart();
		});
		Runnable loadSheet = () -> {
			String sheet = (String) sheetBox.getSelectedItem();
			ModEntry forMod = mod;
			new SwingWorker<syx.core.PngCodec.Image, Void>() {
				@Override
				protected syx.core.PngCodec.Image doInBackground() throws Exception {
					return sheet == null ? null : engine().portraits().sheetImage(forMod, false, sheet);
				}

				@Override
				protected void done() {
					try {
						picker.show(get(), false, List.of());
						showPicks.run();
						pickerScroll.revalidate();
						messages.revalidate();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					} catch (ExecutionException e) {
						say(Theme.Tone.ERROR, "The sheet couldn't be shown: " + e.getCause().getMessage());
					}
				}
			}.execute();
		};
		sheetBox.addActionListener(e -> loadSheet.run());
		loadSheet.run();
		showPreview.run();

		JPanel actions = new JPanel(new WrapLayout(FlowLayout.RIGHT, 6, 0));
		actions.setOpaque(false);
		JButton clear = new JButton("Clear");
		clear.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
		clear.addActionListener(e -> {
			framePicks.clear();
			showPicks.run();
			previewTimer.restart();
		});
		JButton save = new JButton("Use these frames");
		Theme.style(save, "primary");
		save.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
		saveCard = () -> {
			WizardQuestion current = question;
			ModEntry forMod = mod;
			List<String> chosen = framePicks.stream().map(PortraitLayers.FrameRef::text).toList();
			Map<String, Object> answer = new LinkedHashMap<>();
			answer.put("frames", chosen);
			if (layer < 0 && !places.isEmpty()) {
				answer.put("at", place.getAsInt());
			}
			String said = framesText(chosen) + (layer < 0 && count > 0 ? ", " + String.valueOf(placeBox.getSelectedItem()).toLowerCase(Locale.ROOT) : "");
			run(said, () -> engine().answer(forMod, current.wizard(), current.name(), answer));
		};
		save.addActionListener(e -> saveCard.run());
		actions.add(clear);
		actions.add(save);
		bubble.addFront(actions);
		return bubble;
	}

	/** Crown, raider or mercenary headwear: addon sheets, each with where it's drawn. */
	private ChatBubble addonsCard(WizardQuestion asked) {
		ChatBubble bubble = new ChatBubble(ChatBubble.Side.CARD);
		WizardQuestion.PortraitView view = asked.portrait();
		List<String> sheets = view.addonSheets().stream().filter(sheet -> sheet.problem() == null).map(WizardQuestion.SheetView::name).toList();
		JPanel rows = new JPanel(new GridLayout(0, 1, 0, 6));
		rows.setOpaque(false);
		List<Object[]> entries = new ArrayList<>();
		java.util.function.BiConsumer<String, String[]> addRow = (file, offsets) -> {
			JComboBox<String> sheet = new JComboBox<>(sheets.toArray(String[]::new));
			sheet.setEditable(false);
			if (file != null) {
				if (!sheets.contains(file)) {
					sheet.addItem(file);
				}
				sheet.setSelectedItem(file);
			}
			sheet.getAccessibleContext().setAccessibleName("Addon sheet");
			JTextField offX = new JTextField(offsets[0], 4);
			JTextField offY = new JTextField(offsets[1], 4);
			restrict(offX, "-?\\d*");
			restrict(offY, "-?\\d*");
			offX.getAccessibleContext().setAccessibleName("Moved right by");
			offY.getAccessibleContext().setAccessibleName("Moved down by");
			JLabel image = new JLabel();
			JButton remove = new JButton("Remove");
			remove.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
			JPanel row = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 2));
			row.setOpaque(false);
			row.add(image);
			row.add(sheet);
			row.add(new JLabel("Right"));
			row.add(offX);
			row.add(new JLabel("Down"));
			row.add(offY);
			row.add(remove);
			Object[] entry = { sheet, offX, offY, row };
			entries.add(entry);
			rows.add(row);
			Runnable loadImage = () -> {
				String name = (String) sheet.getSelectedItem();
				ModEntry forMod = mod;
				new SwingWorker<BufferedImage, Void>() {
					@Override
					protected BufferedImage doInBackground() throws Exception {
						syx.core.PngCodec.Image png = name == null ? null : engine().portraits().sheetImage(forMod, true, name);
						if (png == null) {
							return null;
						}
						int width = png.width() / 2;
						int height = Math.min(png.height(), syx.core.PortraitSim.ADDON_CELL_H);
						BufferedImage first = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
						for (int y = 0; y < height; y++) {
							for (int x = 0; x < width; x++) {
								int rgba = png.at(x, y);
								first.setRGB(x, y, (rgba & 0xFF) << 24 | rgba >>> 8);
							}
						}
						return first;
					}

					@Override
					protected void done() {
						try {
							BufferedImage first = get();
							image.setIcon(first == null ? null : new javax.swing.ImageIcon(first.getScaledInstance(first.getWidth() * 2, first.getHeight() * 2, java.awt.Image.SCALE_FAST)));
							image.setToolTipText(PlainTip.of("The first of " + name + "'s rows. Each row is one the game can pick."));
							bubble.revalidate();
							messages.revalidate();
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						} catch (ExecutionException e) {
							image.setIcon(null);
						}
					}
				}.execute();
			};
			sheet.addActionListener(e -> loadImage.run());
			loadImage.run();
			remove.addActionListener(e -> {
				entries.remove(entry);
				rows.remove(row);
				bubble.revalidate();
				messages.revalidate();
				messages.repaint();
			});
		};
		for (WizardQuestion.AddonView addon : view.addons()) {
			addRow.accept(addon.file(), new String[] { addon.offX() == null ? "0" : addon.offX(), addon.offY() == null ? "0" : addon.offY() });
		}
		bubble.addFront(rows);
		JPanel actions = new JPanel(new WrapLayout(FlowLayout.RIGHT, 6, 0));
		actions.setOpaque(false);
		JButton add = new JButton("Add another");
		add.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
		add.addActionListener(e -> {
			addRow.accept(sheets.isEmpty() ? null : sheets.get(0), new String[] { "0", "0" });
			bubble.revalidate();
			messages.revalidate();
			messages.repaint();
		});
		JButton save = new JButton("Save and go on");
		Theme.style(save, "primary");
		save.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
		saveCard = () -> {
			List<Object> given = new ArrayList<>();
			List<String> said = new ArrayList<>();
			for (Object[] entry : entries) {
				String file = String.valueOf(((JComboBox<?>) entry[0]).getSelectedItem());
				String offX = ((JTextField) entry[1]).getText().strip();
				String offY = ((JTextField) entry[2]).getText().strip();
				Map<String, Object> map = new LinkedHashMap<>();
				map.put("file", file);
				map.put("offx", offX.isEmpty() ? "0" : offX);
				map.put("offy", offY.isEmpty() ? "0" : offY);
				given.add(map);
				said.add(file);
			}
			WizardQuestion current = question;
			ModEntry forMod = mod;
			run(said.isEmpty() ? "Nothing" : String.join(", ", said), () -> engine().answer(forMod, current.wizard(), current.name(), Map.of("entries", given)));
		};
		save.addActionListener(e -> saveCard.run());
		actions.add(add);
		actions.add(save);
		bubble.addFront(actions);
		return bubble;
	}

	private static JLabel previewLabel() {
		JLabel picture = new JLabel();
		picture.setHorizontalAlignment(SwingConstants.LEFT);
		// A portrait is 40 by 64 (RPortrait), drawn at 4 times.
		picture.setPreferredSize(new Dimension(40 * 4, 64 * 4));
		picture.getAccessibleContext().setAccessibleName("The portrait");
		return picture;
	}

	/** Draws the portrait off the event thread. Only the newest drawing is shown, so a quick pointer doesn't leave an old one. */
	private void loadPreview(JLabel target, String race, int portrait, int layer, List<PortraitLayers.FrameRef> frames, int show) {
		loadPreview(target, race, portrait, layer, frames, show, Integer.MAX_VALUE);
	}

	private void loadPreview(JLabel target, String race, int portrait, int layer, List<PortraitLayers.FrameRef> frames, int show, int at) {
		ModEntry forMod = mod;
		int generation = ++previewGeneration;
		new SwingWorker<Object, Void>() {
			@Override
			protected Object doInBackground() throws Exception {
				syx.core.PortraitSim.Preview preview = engine().portraits().preview(forMod, race, portrait, layer, frames, show, at);
				if (preview.argb() == null) {
					return preview.problem() == null ? "It can't be drawn." : "It can't be drawn: " + preview.problem();
				}
				BufferedImage image = new BufferedImage(preview.width(), preview.height(), BufferedImage.TYPE_INT_ARGB);
				image.setRGB(0, 0, preview.width(), preview.height(), preview.argb(), 0, preview.width());
				return image;
			}

			@Override
			protected void done() {
				if (generation != previewGeneration) {
					return;
				}
				try {
					Object result = get();
					if (result instanceof BufferedImage image) {
						target.setIcon(new javax.swing.ImageIcon(image));
						target.setText(null);
					} else {
						target.setIcon(null);
						target.putClientProperty("html.disable", Boolean.TRUE);
						target.setText(String.valueOf(result));
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					target.setIcon(null);
					target.setText("It can't be drawn: " + e.getCause().getMessage());
				}
			}
		}.execute();
	}

	/**
	 * One tag on the card. A tag that's absent shows the game's default, greyed and not editable, with Add; a tag
	 * with no default says it isn't set, with Add; a value written in a form this can't show is shown as written,
	 * with Replace. What's sent is only what changed.
	 */
	private final class FieldEditor {
		private final WizardQuestion.FieldView view;
		private final ChatBubble bubble;
		private final WrappedText ghost = new WrappedText(" ", Theme.Tone.GHOST);
		private final WrappedText problem = new WrappedText(" ", Theme.Tone.ERROR);
		private final WrappedText evidence;
		private final JButton action = new JButton();
		private final JComponent editor;
		private final transient Supplier<String> read;
		private final transient Map<String, JTextField> amounts = new LinkedHashMap<>();
		/** Adds a row for a name that isn't showing, as the list under a long list of amounts does. */
		private transient Consumer<String> addAmount = name -> {
			throw new IllegalArgumentException("No amount called " + name);
		};
		/** Whether the tag will be in the file: it was, or Add was pressed, and Remove wasn't. */
		private boolean present;
		/** For colours, sets the swatches from R_G_B lines, as a person picking them would. */
		private transient Consumer<String> coloursSetter;

		FieldEditor(WizardQuestion.FieldView view, ChatBubble bubble) {
			this.view = view;
			this.bubble = bubble;
			present = view.state().equals("set") || view.state().equals("required");
			WrappedText label = new WrappedText(view.label(), null);
			Theme.style(label, "semibold");
			bubble.addFront(label);
			String help = String.join(" ", nonBlank(view.help(), view.rule()));
			if (!help.isBlank()) {
				WrappedText helpText = new WrappedText(help, Theme.Tone.HELP);
				Theme.style(helpText, "small");
				bubble.addFront(helpText);
			}
			evidence = new WrappedText("From the game's code: " + view.evidence(), Theme.Tone.GHOST);
			Theme.style(evidence, "small");
			evidence.setVisible(false);
			Theme.style(ghost, "small");
			bubble.addFront(ghost);
			Reader reader = new Reader();
			editor = buildEditor(reader);
			read = reader.read;
			bubble.addFront(editor);
			bubble.addFront(evidence);
			evidence.setVisible(false);
			action.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
			Theme.style(action, "small");
			action.addActionListener(e -> toggle());
			JPanel actionRow = new JPanel(new WrapLayout(FlowLayout.LEFT, 0, 0));
			actionRow.setOpaque(false);
			actionRow.add(action);
			bubble.addFront(actionRow);
			bubble.addFront(problem);
			problem.setVisible(false);
			showState();
		}

		private JComponent buildEditor(Reader reader) {
			String value = view.value() == null ? "" : view.value();
			return switch (view.input()) {
			case "whole_number", "number" -> {
				JTextField field = new JTextField(value);
				restrict(field, view.input().equals("whole_number") ? "-?\\d*" : "-?\\d*\\.?\\d*");
				if (view.range() != null && !view.hasSlider()) {
					field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, view.range());
				}
				showEvidenceOnFocus(field);
				reader.read = field::getText;
				yield withSlider(field, view);
			}
			case "text" -> {
				JTextField field = new JTextField(value);
				showEvidenceOnFocus(field);
				reader.read =field::getText;
				yield field;
			}
			case "text_lines", "rows" -> {
				JTextArea area = new JTextArea(value, view.input().equals("rows") ? 4 : 3, 30);
				area.setLineWrap(true);
				area.setWrapStyleWord(true);
				area.setFont(new JTextField().getFont());
				showEvidenceOnFocus(area);
				JScrollPane pane = new JScrollPane(area);
				reader.read =() -> area.getText().replaceAll("\\s+$", "");
				yield pane;
			}
			case "colours" -> {
				// Swatches as the file writes them, R_G_B: click one to change it, or add up to 16.
				JPanel swatches = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 4));
				swatches.setOpaque(false);
				List<String> colours = new ArrayList<>();
				for (String line : value.split("\n")) {
					if (!line.isBlank()) {
						colours.add(line.strip());
					}
				}
				Runnable[] rebuild = new Runnable[1];
				rebuild[0] = () -> {
					swatches.removeAll();
					for (int i = 0; i < colours.size(); i++) {
						int at = i;
						JButton swatch = new JButton();
						Color shown = colourOf(colours.get(i));
						swatch.setPreferredSize(new Dimension(30, 30));
						swatch.putClientProperty(FlatClientProperties.STYLE, "background: " + String.format("#%06X", shown.getRGB() & 0xFFFFFF) + "; arc: 8");
						swatch.setToolTipText(PlainTip.of(colours.get(i) + ". Click to change it; right-click to remove it."));
						swatch.getAccessibleContext().setAccessibleName("Colour " + (i + 1) + ", " + colours.get(i));
						swatch.addActionListener(e -> {
							Color picked = javax.swing.JColorChooser.showDialog(swatches, "Colour " + (at + 1), colourOf(colours.get(at)));
							if (picked != null) {
								colours.set(at, picked.getRed() + "_" + picked.getGreen() + "_" + picked.getBlue());
								rebuild[0].run();
							}
						});
						swatch.addMouseListener(new java.awt.event.MouseAdapter() {
							@Override
							public void mousePressed(java.awt.event.MouseEvent e) {
								if (SwingUtilities.isRightMouseButton(e)) {
									colours.remove(at);
									rebuild[0].run();
								}
							}
						});
						swatches.add(swatch);
					}
					JButton add = new JButton("Add a colour");
					add.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
					add.setEnabled(colours.size() < 16);
					add.addActionListener(e -> {
						Color picked = javax.swing.JColorChooser.showDialog(swatches, "New colour", colours.isEmpty() ? Color.GRAY : colourOf(colours.get(colours.size() - 1)));
						if (picked != null) {
							colours.add(picked.getRed() + "_" + picked.getGreen() + "_" + picked.getBlue());
							rebuild[0].run();
						}
					});
					swatches.add(add);
					swatches.revalidate();
					swatches.repaint();
					bubble.revalidate();
					messages.revalidate();
				};
				rebuild[0].run();
				reader.read = () -> String.join("\n", colours);
				coloursSetter = lines -> {
					colours.clear();
					for (String line : lines.split("\n")) {
						if (!line.isBlank()) {
							colours.add(line.strip());
						}
					}
					rebuild[0].run();
				};
				yield swatches;
			}
			case "yes_no" -> {
				JPanel pills = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 0));
				pills.setOpaque(false);
				ButtonGroup group = new ButtonGroup();
				JToggleButton yes = pill("Yes", "true".equals(value));
				JToggleButton no = pill("No", "false".equals(value));
				group.add(yes);
				group.add(no);
				pills.add(yes);
				pills.add(no);
				reader.read =() -> yes.isSelected() ? "true" : no.isSelected() ? "false" : "";
				yield pills;
			}
			case "one" -> {
				List<String> options = new ArrayList<>(view.options() == null ? List.of() : view.options());
				if (!value.isEmpty() && options.stream().noneMatch(value::equalsIgnoreCase)) {
					options.add(value);
				}
				JPanel pills = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 4));
				pills.setOpaque(false);
				ButtonGroup group = new ButtonGroup();
				List<JToggleButton> toggles = new ArrayList<>();
				for (String option : options) {
					JToggleButton toggle = pill(readable(option), option.equalsIgnoreCase(value));
					toggle.putClientProperty("syx.option", option);
					group.add(toggle);
					toggles.add(toggle);
					pills.add(toggle);
				}
				reader.read = () -> toggles.stream().filter(JToggleButton::isSelected).map(toggle -> (String) toggle.getClientProperty("syx.option")).findFirst().orElse("");
				yield pills;
			}
			case "picks" -> {
				JPanel pills = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 4));
				pills.setOpaque(false);
				List<String> chosen = new ArrayList<>();
				for (String part : value.split(",")) {
					if (!part.isBlank()) {
						chosen.add(part.strip());
					}
				}
				List<String> options = new ArrayList<>(view.options() == null ? List.of() : view.options());
				for (String already : chosen) {
					if (options.stream().noneMatch(option -> option.equalsIgnoreCase(already))) {
						options.add(already);
					}
				}
				List<JToggleButton> toggles = new ArrayList<>();
				for (String option : options) {
					JToggleButton toggle = pill(option, chosen.stream().anyMatch(option::equalsIgnoreCase));
					toggle.putClientProperty("syx.option", option);
					toggles.add(toggle);
					pills.add(toggle);
				}
				reader.read =() -> String.join(",", toggles.stream().filter(JToggleButton::isSelected).map(toggle -> (String) toggle.getClientProperty("syx.option")).toList());
				yield pills;
			}
			case "amounts" -> {
				JPanel all = new JPanel(new BorderLayout(0, 6));
				all.setOpaque(false);
				JPanel rows = new JPanel(new GridLayout(0, 1, 0, 4));
				rows.setOpaque(false);
				all.add(rows, BorderLayout.CENTER);
				List<String> options = view.options() == null ? List.of() : view.options();
				// Each option is saved under the name the file already uses for it, such as _GUARD for GUARD.
				Map<String, String> named = new LinkedHashMap<>();
				for (String option : options) {
					String existing = view.amounts().keySet().stream().filter(name -> GameAssetIndex.sameKey(name, option)).findFirst().orElse(option);
					named.put(option, existing);
				}
				List<String> shown = new ArrayList<>();
				// A short list shows every name. A long one, such as every room, shows those with an amount and adds others from a list.
				boolean everyName = options.size() <= SHORT_LIST;
				if (everyName) {
					shown.addAll(named.values());
				}
				for (String name : view.amounts().keySet()) {
					if (!shown.contains(name)) {
						shown.add(name);
					}
				}
				for (String name : shown) {
					rows.add(amountRow(name, view.amounts().getOrDefault(name, ""), view));
				}
				if (!everyName) {
					List<String> addable = new ArrayList<>();
					for (Map.Entry<String, String> option : named.entrySet()) {
						if (!shown.contains(option.getValue())) {
							addable.add(option.getValue());
						}
					}
					JComboBox<String> picker = new JComboBox<>(addable.toArray(String[]::new));
					picker.setRenderer(new javax.swing.DefaultListCellRenderer() {
						@Override
						public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focused) {
							Component shownAs = super.getListCellRendererComponent(list, value == null ? "" : readable(String.valueOf(value)), index, selected, focused);
							((JLabel) shownAs).putClientProperty("html.disable", Boolean.TRUE);
							return shownAs;
						}
					});
					picker.getAccessibleContext().setAccessibleName("Add another to " + view.label());
					JButton add = new JButton("Add");
					addAmount = name -> {
						picker.removeItem(name);
						rows.add(amountRow(name, "", view));
						add.setEnabled(picker.getItemCount() > 0);
						bubble.revalidate();
						messages.revalidate();
						messages.repaint();
					};
					add.addActionListener(e -> {
						Object picked = picker.getSelectedItem();
						if (picked != null) {
							addAmount.accept(String.valueOf(picked));
						}
					});
					add.setEnabled(picker.getItemCount() > 0);
					JPanel adding = new JPanel(new BorderLayout(8, 0));
					adding.setOpaque(false);
					adding.add(picker, BorderLayout.CENTER);
					adding.add(add, BorderLayout.EAST);
					all.add(adding, BorderLayout.SOUTH);
				}
				reader.read = () -> "";
				yield all;
			}
			default -> {
				JTextField field = new JTextField(value);
				reader.read =field::getText;
				yield field;
			}
			};
		}

		/** One name's amount: its name, then the number, with a slider when there's one. Empty leaves it out. */
		private JComponent amountRow(String key, String amount, WizardQuestion.FieldView view) {
			JLabel name = new JLabel(key.contains("*") ? "Every one matching " + key : readable(key));
			name.putClientProperty("html.disable", Boolean.TRUE);
			name.setPreferredSize(new Dimension(120, name.getPreferredSize().height));
			name.setToolTipText(key);
			JTextField field = new JTextField(amount);
			restrict(field, "-?\\d*\\.?\\d*");
			// Empty isn't always 0: an absent race in OTHER_RACES counts as 1, for one. The field's help says what it is.
			field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "not set");
			field.getAccessibleContext().setAccessibleName(view.label() + ": " + readable(key));
			showEvidenceOnFocus(field);
			amounts.put(key, field);
			JPanel row = new JPanel(new BorderLayout(10, 0));
			row.setOpaque(false);
			row.add(name, BorderLayout.WEST);
			JComponent input = withSlider(field, view);
			if (input == field) {
				field.setColumns(7);
				JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
				left.setOpaque(false);
				left.add(field);
				input = left;
			}
			row.add(input, BorderLayout.CENTER);
			return row;
		}

		/**
		 * The text box, with a slider beside it when the game sets both ends. The two follow each other. The slider stops
		 * at the stated maximum; where the game doesn't enforce it, a larger number can still be typed, and the slider
		 * then sits at its end. The core decides what's valid either way.
		 */
		private JComponent withSlider(JTextField field, WizardQuestion.FieldView view) {
			if (!view.hasSlider()) {
				return field;
			}
			boolean whole = view.input().equals("whole_number");
			double min = view.min();
			double max = view.max();
			int steps = whole ? (int) Math.min(100_000, Math.round(max - min)) : 1000;
			javax.swing.JSlider slider = new javax.swing.JSlider(0, steps);
			slider.setOpaque(false);
			slider.getAccessibleContext().setAccessibleName(view.label());
			boolean[] syncing = { false };
			Runnable fromText = () -> {
				if (syncing[0]) {
					return;
				}
				syncing[0] = true;
				try {
					double typed = Double.parseDouble(field.getText().strip());
					slider.setValue((int) Math.round(Math.max(0, Math.min(steps, (typed - min) / (max - min) * steps))));
				} catch (NumberFormatException e) {
					slider.setValue(0);
				} finally {
					syncing[0] = false;
				}
			};
			slider.addChangeListener(e -> {
				if (syncing[0]) {
					return;
				}
				syncing[0] = true;
				field.setText(sliderText(min + slider.getValue() * (max - min) / steps, whole, max - min));
				syncing[0] = false;
			});
			field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
				@Override
				public void insertUpdate(javax.swing.event.DocumentEvent e) {
					fromText.run();
				}

				@Override
				public void removeUpdate(javax.swing.event.DocumentEvent e) {
					fromText.run();
				}

				@Override
				public void changedUpdate(javax.swing.event.DocumentEvent e) {
					fromText.run();
				}
			});
			fromText.run();
			field.setColumns(7);
			JPanel both = new JPanel(new BorderLayout(10, 0));
			both.setOpaque(false);
			both.add(slider, BorderLayout.CENTER);
			both.add(field, BorderLayout.EAST);
			return both;
		}

		private JToggleButton pill(String text, boolean selected) {
			JToggleButton toggle = new JToggleButton(text, selected);
			toggle.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
			toggle.putClientProperty(FlatClientProperties.STYLE, "margin: 3,12,3,12");
			return toggle;
		}

		private void showEvidenceOnFocus(JComponent component) {
			component.addFocusListener(new FocusAdapter() {
				@Override
				public void focusGained(FocusEvent e) {
					evidence.setVisible(view.evidence() != null && !view.evidence().isBlank());
					bubble.revalidate();
					messages.revalidate();
				}

				@Override
				public void focusLost(FocusEvent e) {
					evidence.setVisible(false);
					bubble.revalidate();
					messages.revalidate();
				}
			});
		}

		private void toggle() {
			present = !present;
			showState();
			bubble.revalidate();
			messages.revalidate();
			messages.repaint();
		}

		private void showState() {
			boolean kept = view.state().equals("kept");
			editor.setVisible(present);
			switch (view.state()) {
			case "ghosted" -> ghost.setText("The game's default: " + view.defaultText());
			case "add" -> ghost.setText("Not set.");
			case "kept" -> ghost.setText("Written as: " + view.keptAsWritten() + ". Replacing it writes it the way this form does.");
			default -> ghost.setText(view.defaultText() == null ? "Not set." : "The game's default: " + view.defaultText());
			}
			ghost.setVisible(!present);
			boolean canRemove = !view.required() || view.state().equals("ghosted");
			if (present) {
				action.setText(canRemove && !view.state().equals("required") ? (view.defaultText() != null ? "Use the game's default" : "Remove") : "");
			} else {
				action.setText(kept ? "Replace" : "Add");
			}
			action.setVisible(!action.getText().isEmpty());
		}

		void showProblems(List<WizardQuestion.Problem> problems) {
			List<String> messagesFor = new ArrayList<>();
			for (WizardQuestion.Problem found : problems) {
				if (found.tag() != null && (found.tag().equals(view.key()) || found.tag().startsWith(view.key() + "."))) {
					messagesFor.add(found.message());
				}
			}
			problem.setText(String.join("\n", messagesFor));
			problem.setVisible(!messagesFor.isEmpty());
			Theme.tone(problem, problems.stream().anyMatch(found -> found.blocksSaving() && found.tag() != null && found.tag().startsWith(view.key())) ? Theme.Tone.ERROR : Theme.Tone.WARNING);
		}

		/** Adds what this tag changes, by key. A null puts a tag back to absent. */
		void collect(Map<String, Object> out) {
			boolean was = view.state().equals("set") || view.state().equals("required") || view.state().equals("kept");
			if (view.input().equals("amounts")) {
				if (!present) {
					if (view.state().equals("set") && view.column() == null) {
						out.put(view.key(), null);
					} else if (view.state().equals("set")) {
						// Only this number goes: each crime's LAW stays when its FREEDOM is removed.
						view.amounts().keySet().forEach(name -> out.put(view.amountKey(name), null));
					}
					return;
				}
				for (Map.Entry<String, JTextField> amount : amounts.entrySet()) {
					String now = amount.getValue().getText().strip();
					String before = view.amounts().get(amount.getKey());
					if (now.isEmpty() && before != null) {
						out.put(view.amountKey(amount.getKey()), null);
					} else if (!now.isEmpty() && !now.equals(before)) {
						out.put(view.amountKey(amount.getKey()), now);
					}
				}
				return;
			}
			if (!present) {
				if (was && !view.state().equals("kept")) {
					out.put(view.key(), null);
				}
				return;
			}
			String now = read.get();
			String before = view.value();
			if (now.isEmpty()) {
				if (before != null && !view.required()) {
					out.put(view.key(), null);
				} else if (before != null) {
					out.put(view.key(), "");
				}
				return;
			}
			if (!now.equals(before)) {
				out.put(view.key(), now);
			}
		}
	}

	/** How an editor's value is read, which the editor sets as it's built. */
	private static final class Reader {
		private Supplier<String> read = () -> "";
	}

	private static List<String> nonBlank(String... texts) {
		List<String> kept = new ArrayList<>();
		for (String text : texts) {
			if (text != null && !text.isBlank()) {
				kept.add(text.strip());
			}
		}
		return kept;
	}

	/** A slider's number as text: whole, or with as many places as the range needs, without trailing zeros. */
	private static String sliderText(double value, boolean whole, double range) {
		if (whole) {
			return String.valueOf(Math.round(value));
		}
		int places = range <= 1 ? 3 : range <= 10 ? 2 : range <= 1000 ? 1 : 0;
		java.math.BigDecimal rounded = java.math.BigDecimal.valueOf(value).setScale(places, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
		return rounded.scale() < 0 ? rounded.setScale(0).toPlainString() : rounded.toPlainString();
	}

	/** A colour as a file writes it, R_G_B. Anything else is grey. */
	private static Color colourOf(String text) {
		String[] parts = text.strip().split("_");
		try {
			return new Color(Math.min(255, Integer.parseInt(parts[0])), Math.min(255, Integer.parseInt(parts[1])), Math.min(255, Integer.parseInt(parts[2])));
		} catch (RuntimeException e) {
			return Color.GRAY;
		}
	}

	/** TEMPERATE is Temperate. The key itself is what's saved. */
	private static String readable(String key) {
		String spaced = key.replace('_', ' ').strip();
		return spaced.isEmpty() ? key : spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1).toLowerCase(Locale.ROOT);
	}

	/** The core decides what's valid. This only stops characters that can't ever be part of a number. */
	private static void restrict(JTextField field, String pattern) {
		((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
			@Override
			public void insertString(FilterBypass fb, int offset, String text, AttributeSet attributes) throws BadLocationException {
				replace(fb, offset, 0, text, attributes);
			}

			@Override
			public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attributes) throws BadLocationException {
				String current = fb.getDocument().getText(0, fb.getDocument().getLength());
				String after = current.substring(0, offset) + (text == null ? "" : text) + current.substring(offset + length);
				if (after.matches(pattern)) {
					super.replace(fb, offset, length, text, attributes);
				}
			}
		});
	}

	// ---- Layout

	private int columnWidth(int width) {
		int gutter = 16;
		return Math.max(200, Math.min(COLUMN, width - gutter * 2));
	}

	private double bubbleShare() {
		return band == WidthBand.NARROW ? 0.88 : 0.75;
	}

	/** The conversation: a centred column, the wizard's bubbles on the left with its mark, and the person's on the right. */
	private final class Messages extends JPanel implements Scrollable {
		private static final long serialVersionUID = 1L;
		static final String AVATAR_KEY = "syx.avatar";

		Messages() {
			setLayout(new Conversation());
			setBorder(BorderFactory.createEmptyBorder(18, 0, 18, 0));
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			Theme.Palette palette = Theme.palette(Theme.current());
			int x0 = (getWidth() - columnWidth(getWidth())) / 2;
			for (Component child : getComponents()) {
				if (child instanceof ChatBubble bubble && Boolean.TRUE.equals(bubble.getClientProperty(AVATAR_KEY))) {
					paintAvatar(g2, palette, x0, bubble.getY());
				}
			}
			g2.dispose();
		}

		/** A round mark with a four-pointed star, for the wizard. */
		private void paintAvatar(Graphics2D g2, Theme.Palette palette, int x, int y) {
			int size = AVATAR - 6;
			g2.setColor(Color.decode(palette.accent()));
			g2.fillOval(x, y, size, size);
			double cx = x + size / 2.0;
			double cy = y + size / 2.0;
			double outer = size * 0.34;
			double inner = size * 0.11;
			Path2D star = new Path2D.Double();
			for (int i = 0; i < 8; i++) {
				double angle = Math.PI / 4 * i - Math.PI / 2;
				double radius = i % 2 == 0 ? outer : inner;
				double px = cx + Math.cos(angle) * radius;
				double py = cy + Math.sin(angle) * radius;
				if (i == 0) {
					star.moveTo(px, py);
				} else {
					star.lineTo(px, py);
				}
			}
			star.closePath();
			g2.setColor(Color.decode(palette.surface()));
			g2.fill(star);
			g2.setStroke(new BasicStroke(1f));
		}

		@Override
		public Dimension getPreferredScrollableViewportSize() {
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
			return Math.max(16, visible.height - 16);
		}

		@Override
		public boolean getScrollableTracksViewportWidth() {
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight() {
			return getParent() instanceof JViewport viewport && viewport.getHeight() > getPreferredSize().height;
		}
	}

	/** Sizes each bubble to its words, up to its share of the column, and stacks them. */
	private final class Conversation implements LayoutManager {
		private static final int GAP = 10;

		@Override
		public void addLayoutComponent(String name, Component comp) {
		}

		@Override
		public void removeLayoutComponent(Component comp) {
		}

		@Override
		public Dimension preferredLayoutSize(Container parent) {
			int width = parent.getWidth() > 0 ? parent.getWidth() : (parent.getParent() != null && parent.getParent().getWidth() > 0 ? parent.getParent().getWidth() : 800);
			return new Dimension(width, place(parent, width, false));
		}

		@Override
		public Dimension minimumLayoutSize(Container parent) {
			return new Dimension(0, 0);
		}

		@Override
		public void layoutContainer(Container parent) {
			place(parent, parent.getWidth(), true);
		}

		private int place(Container parent, int width, boolean apply) {
			Insets insets = parent.getInsets();
			int column = columnWidth(width);
			int x0 = (width - column) / 2;
			int y = insets.top;
			for (Component child : parent.getComponents()) {
				if (!(child instanceof ChatBubble bubble)) {
					continue;
				}
				int avatarRoom = bubble.side() == ChatBubble.Side.PERSON ? 0 : AVATAR;
				int max = bubble.side() == ChatBubble.Side.CARD ? column - AVATAR : (int) Math.round((column - avatarRoom) * bubbleShare());
				int bubbleWidth = bubble.side() == ChatBubble.Side.CARD ? max : bubble.naturalWidth(max);
				int height = bubble.heightFor(bubbleWidth);
				int x = bubble.side() == ChatBubble.Side.PERSON ? x0 + column - bubbleWidth : x0 + AVATAR;
				if (apply) {
					bubble.setBounds(x, y, bubbleWidth, height);
				}
				y += height + GAP;
			}
			return y + insets.bottom;
		}
	}

	/** Puts the one child in the centred column, at the height it needs at that width. */
	private final class Centred implements LayoutManager {
		@Override
		public void addLayoutComponent(String name, Component comp) {
		}

		@Override
		public void removeLayoutComponent(Component comp) {
		}

		@Override
		public Dimension preferredLayoutSize(Container parent) {
			int width = parent.getWidth() > 0 ? parent.getWidth() : 800;
			Insets insets = parent.getInsets();
			int column = columnWidth(width);
			int height = 0;
			if (parent.getComponentCount() > 0) {
				Component child = parent.getComponent(0);
				child.setSize(column, Short.MAX_VALUE);
				child.doLayout();
				height = child.getPreferredSize().height;
			}
			return new Dimension(width, height + insets.top + insets.bottom);
		}

		@Override
		public Dimension minimumLayoutSize(Container parent) {
			return preferredLayoutSize(parent);
		}

		@Override
		public void layoutContainer(Container parent) {
			if (parent.getComponentCount() == 0) {
				return;
			}
			Insets insets = parent.getInsets();
			int column = columnWidth(parent.getWidth());
			Component child = parent.getComponent(0);
			child.setBounds((parent.getWidth() - column) / 2, insets.top, column, parent.getHeight() - insets.top - insets.bottom);
		}
	}

	/** Three dots that rise in turn, while the wizard is working. */
	private static final class Dots extends JComponent {
		private static final long serialVersionUID = 1L;
		private int phase;
		private final Timer timer = new Timer(140, e -> {
			phase = (phase + 1) % 12;
			repaint();
		});

		Dots() {
			setPreferredSize(new Dimension(44, 14));
		}

		@Override
		public void addNotify() {
			super.addNotify();
			if (ChatBubble.animate) {
				timer.start();
			}
		}

		@Override
		public void removeNotify() {
			timer.stop();
			super.removeNotify();
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(Color.decode(Theme.palette(Theme.current()).help()));
			for (int i = 0; i < 3; i++) {
				int lift = phase / 4 == i ? 3 : 0;
				g2.fillOval(4 + i * 13, 4 - lift + 2, 8, 8);
			}
			g2.dispose();
		}
	}

	// ---- For tests

	/** Off for tests, so a screenshot is never taken half faded in. */
	public static void setAnimations(boolean on) {
		ChatBubble.animate = on;
	}

	/** Presses Continue on one of the unfinished wizards the start lists. */
	public void clickContinue(int index) {
		continues.get(index).run();
	}

	public boolean isBusy() {
		return busy;
	}

	/** name, look, choice, number, fields, done, finished, or start when the list of wizards is showing. */
	public String questionKind() {
		return onStart ? "start" : question == null ? "" : question.kind();
	}

	public String questionStep() {
		return question == null ? "" : question.step();
	}

	public String lastError() {
		return lastError;
	}

	public List<String> replyLabels() {
		List<String> labels = new ArrayList<>();
		for (Component component : replies.getComponents()) {
			if (component instanceof AbstractButton button) {
				labels.add(button.getText());
			}
		}
		return labels;
	}

	public void clickReply(String label) {
		for (Component component : replies.getComponents()) {
			if (component instanceof AbstractButton button && button.getText().equals(label)) {
				button.doClick();
				return;
			}
		}
		throw new IllegalArgumentException("No reply called " + label + ": " + replyLabels());
	}

	public void typeAndSend(String text) {
		entry.setText(text);
		send.doClick();
	}

	/** Types into a field on the card, pressing Add first if it's absent. For an amount, the key is the field's key, a dot and the name. */
	public void setField(String key, String value) {
		for (FieldEditor editor : editors) {
			String name = editor.view.input().equals("amounts") ? editor.view.nameIn(key) : null;
			if (name != null) {
				if (!editor.present) {
					editor.toggle();
				}
				String shown = editor.amounts.keySet().stream().filter(existing -> GameAssetIndex.sameKey(existing, name)).findFirst().orElse(null);
				if (shown == null) {
					editor.addAmount.accept(name);
					shown = name;
				}
				editor.amounts.get(shown).setText(value);
				return;
			}
			if (editor.view.key().equals(key)) {
				if (!editor.present) {
					editor.toggle();
				}
				if (editor.coloursSetter != null) {
					editor.coloursSetter.accept(value);
				} else if (editor.editor instanceof JTextField field) {
					field.setText(value);
				} else if (editor.editor instanceof JPanel panel && editor.view.input().equals("one")) {
					for (Component child : panel.getComponents()) {
						if (child instanceof JToggleButton toggle && value.equals(toggle.getClientProperty("syx.option"))) {
							toggle.setSelected(true);
							return;
						}
					}
					throw new IllegalArgumentException(key + " has no option " + value);
				} else if (editor.editor instanceof JPanel panel && panel.getComponentCount() > 0 && findField(panel) != null) {
					findField(panel).setText(value);
				} else if (editor.editor instanceof JScrollPane pane && pane.getViewport().getView() instanceof JTextArea area) {
					area.setText(value);
				} else {
					throw new IllegalArgumentException(key + " isn't typed into.");
				}
				return;
			}
		}
		throw new IllegalArgumentException("No field called " + key);
	}

	private static JTextField findField(Container container) {
		for (Component child : container.getComponents()) {
			if (child instanceof JTextField field) {
				return field;
			}
			if (child instanceof Container inner) {
				JTextField found = findField(inner);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	// ---- For the screen test: the portrait wizard's cards

	public boolean isPortraitMode() {
		return portraitMode;
	}

	/** Whether the frame picker is showing a sheet, with how many frames. */
	public int pickerFrames() {
		return framePicker == null ? 0 : framePicker.frameCount();
	}

	/** Clicks a frame on the picker's sheet, as a person would. */
	public void clickPickerFrame(int index) {
		framePicker.clickFrame(index);
	}

	/** Chooses where a new layer is drawn, by its place: 0 is the bottom. */
	public void choosePlace(int at) {
		placeChooser.setSelectedIndex(placeValues.indexOf(at));
	}

	/** The frames picked on the card so far, as SHEET: INDEX. */
	public List<String> pickedFrames() {
		return framePicks.stream().map(PortraitLayers.FrameRef::text).toList();
	}

	/** Presses the frames or headwear card's save button. */
	public void clickCardSave() {
		saveCard.run();
	}

	/** Presses one of a layer's buttons on the layers card, by its answer's id, such as tweak:3. */
	public void clickLayerButton(String id) {
		Runnable action = layerButtons.get(id);
		if (action == null) {
			throw new IllegalArgumentException("No layer button " + id + "; there are " + layerButtons.keySet());
		}
		action.run();
	}

	/** How many sliders the card of fields has. */
	public int sliderCount() {
		return card == null ? 0 : count(card, javax.swing.JSlider.class);
	}

	private static int count(Container container, Class<?> kind) {
		int found = 0;
		for (Component child : container.getComponents()) {
			if (kind.isInstance(child)) {
				found++;
			}
			if (child instanceof Container inner) {
				found += count(inner, kind);
			}
		}
		return found;
	}

	public void clickSave() {
		saveFields();
	}

	public void clickSkip() {
		skip.doClick();
	}

	public void clickBack() {
		back.doClick();
	}

	/** The wizard's bubbles that turn over, oldest first. */
	private List<ChatBubble> flippable() {
		List<ChatBubble> found = new ArrayList<>();
		for (Component component : messages.getComponents()) {
			if (component instanceof ChatBubble bubble && bubble.flippable()) {
				found.add(bubble);
			}
		}
		return found;
	}

	public int flippableCount() {
		return flippable().size();
	}

	/** Turns the bubble over the way pressing Enter on it does. */
	public void flipBubble(int index) {
		ChatBubble bubble = flippable().get(index);
		bubble.dispatchEvent(new java.awt.event.KeyEvent(bubble, java.awt.event.KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, java.awt.event.KeyEvent.VK_ENTER, '\n'));
	}

	public boolean isFlipped(int index) {
		return flippable().get(index).isFlipped();
	}

	/** What the bubble shows now, as text, including its buttons. */
	public String bubbleText(int index) {
		StringBuilder text = new StringBuilder();
		for (Component child : flippable().get(index).getComponents()) {
			if (!child.isVisible()) {
				continue;
			}
			if (child instanceof JTextArea area) {
				text.append(area.getText()).append('\n');
			} else if (child instanceof AbstractButton button) {
				text.append('[').append(button.getText()).append("]\n");
			}
		}
		return text.toString();
	}

	/** The widest any wizard's or person's bubble is, as a share of the column, which should never be above the limit. */
	public double widestBubbleShare() {
		int column = columnWidth(messages.getWidth());
		double widest = 0;
		for (Component component : messages.getComponents()) {
			if (component instanceof ChatBubble bubble && bubble.side() != ChatBubble.Side.CARD) {
				int room = bubble.side() == ChatBubble.Side.PERSON ? column : column - AVATAR;
				widest = Math.max(widest, bubble.getWidth() / (double) room);
			}
		}
		return widest;
	}

	public double bubbleLimit() {
		return bubbleShare();
	}

}
